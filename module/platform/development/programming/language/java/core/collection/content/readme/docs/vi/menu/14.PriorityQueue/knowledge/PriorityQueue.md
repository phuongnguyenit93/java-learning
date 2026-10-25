<a id="back-to-top"></a>

# PriorityQueue

## Menu
- [Hàng đợi ưu tiên và quan hệ với cấu trúc heap](#priority-queue-001)
- [Thứ tự tự nhiên và Comparator tùy chỉnh](#priority-queue-002)
- [offer, peek, poll và độ phức tạp](#priority-queue-003)
- [remove(Object), contains và chi phí tìm phần tử](#priority-queue-004)
- [Vì sao iterator không duyệt theo thứ tự ưu tiên](#priority-queue-005)
- [Phần tử đồng hạng, tính ổn định và khóa phụ](#priority-queue-006)
- [Rủi ro sửa độ ưu tiên của đối tượng đang nằm trong queue](#priority-queue-007)
- [Bài toán top-K và lựa chọn heap nhỏ hoặc heap lớn](#priority-queue-008)

## <a id="priority-queue-001">Hàng đợi ưu tiên và quan hệ với cấu trúc heap</a>

<details>
<summary>Xem chi tiết</summary>

# Hàng đợi ưu tiên và quan hệ với cấu trúc heap

## 1. Từ “đến trước” sang “cần xử lý trước”

Giả sử ứng dụng đang giữ các công việc chờ xử lý. A được gửi trước, B được gửi sau nhưng B khẩn cấp hơn. Nếu luôn lấy công việc đến trước, A được chọn. Nếu quy tắc là chọn công việc có mức khẩn cấp cao hơn, B phải được chọn dù đến sau.

**Hàng đợi ưu tiên (priority queue)** phục vụ câu hỏi: “Trong các phần tử hiện có, phần tử nào đứng trước theo quy tắc ưu tiên?” Trong Java, `PriorityQueue<E>` là một lớp cài đặt giao diện `Queue<E>` cho nhu cầu đó.

Cần phân biệt ba tầng:

| Tầng | Câu hỏi được giải quyết |
| --- | --- |
| Quy tắc nghiệp vụ | Thế nào là được ưu tiên: hạn chót sớm hơn, chi phí nhỏ hơn, mức khẩn cấp lớn hơn...? |
| Hàng đợi ưu tiên | Thêm phần tử, xem hoặc lấy phần tử đứng đầu theo quy tắc đó |
| Cấu trúc heap | Tổ chức dữ liệu để duy trì phần tử ưu tiên ở vị trí dễ lấy với chi phí cập nhật hợp lý |

Heap là cơ chế tổ chức bên trong, còn hàng đợi ưu tiên là hành vi mà người dùng cấu trúc dữ liệu cần. Một mô hình hàng đợi ưu tiên có thể được xây dựng theo nhiều cách; OpenJDK dùng heap nhị phân cho `PriorityQueue`.

Bài này đi từ ý nghĩa của đầu hàng đợi đến tính chất heap, cách thêm/lấy phần tử, chi phí và ví dụ Java 17+. Mục tiêu là giải thích được **vì sao lấy được phần tử ưu tiên nhanh mà toàn bộ dữ liệu chưa cần được sắp xếp hoàn toàn**.

## 2. Đầu hàng đợi là phần tử nhỏ nhất theo quy tắc so sánh

### 2.1. Nhỏ nhất theo comparator không nhất thiết là số nhỏ nhất

Với cấu hình mặc định cho số nguyên:

```java
PriorityQueue<Integer> queue = new PriorityQueue<>();
queue.offer(5);
queue.offer(1);
queue.offer(4);
```

Đây là đoạn lệnh minh họa đặt trong một phương thức, cần import `java.util.PriorityQueue`. `queue.peek()` trả 1: thứ tự tự nhiên của `Integer` coi 1 đứng trước 4 và 5.

Nếu dùng:

```java
PriorityQueue<Integer> queue =
        new PriorityQueue<>(Comparator.reverseOrder());
```

thì số 5 lại đứng trước 4 và 1 theo comparator đảo chiều. `peek()` trả 5. Cần import thêm `java.util.Comparator`.

Java vẫn lấy phần tử nhỏ nhất **theo thứ tự mà hàng đợi đang sử dụng**. Từ góc nhìn giá trị số ban đầu, trường hợp thứ hai hoạt động như hàng đợi lấy số lớn nhất trước.

Không có quy tắc mặc định rằng trường tên `priority` càng lớn thì càng được xử lý trước. Hãy viết rõ quy ước của ứng dụng rồi xây comparator tương ứng.

### 2.2. FIFO không phải bảo đảm của PriorityQueue

Thêm lần lượt `5, 1, 4` rồi lấy hết bằng `poll()` trên hàng đợi mặc định sẽ nhận `1, 4, 5`, không phải `5, 1, 4`.

Nếu hai phần tử bằng nhau về mức ưu tiên, `PriorityQueue` không tự bảo đảm thứ tự đến trước được lấy trước. Có thể cần tiêu chí phụ như một số thứ tự nhận việc. Hòa ưu tiên cũng không làm phần tử mới bị loại: hàng đợi có thể giữ nhiều phần tử cùng giá trị hoặc cùng mức ưu tiên.

Hợp đồng về phần tử đầu, hòa ưu tiên và thứ tự duyệt nằm trong [tài liệu PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

### 2.3. Hàng đợi không tự thực thi công việc

Lấy một tác vụ từ `PriorityQueue` không làm Java tự tạo luồng hay tự chạy tác vụ đó. Ứng dụng vẫn phải quyết định khi nào gọi `poll()`, chạy công việc ở đâu và xử lý lỗi thế nào.

Nếu công việc ưu tiên cao liên tục được thêm, công việc ưu tiên thấp có thể chờ lâu. Đó là vấn đề chính sách xử lý của ứng dụng; bản thân heap không tự bổ sung cơ chế công bằng hay tăng ưu tiên theo thời gian.

## 3. Heap nhị phân: hình dạng đầy đủ, thứ tự từng nhánh

### 3.1. Hai điều kiện cần giữ

Với heap nhỏ nhất ở gốc (min-heap), hai điều kiện là:

1. **Hình dạng:** cây nhị phân hoàn chỉnh (complete binary tree). Các mức trước mức cuối được lấp đầy; mức cuối được điền từ trái sang phải.
2. **Thứ tự heap:** mỗi nút cha không đứng sau các nút con theo quy tắc so sánh.

Min-heap số nguyên có thể trông như sau:

```text
          2
        /   \
       5     3
      / \   /
     9   6 8
```

Các cạnh đều đúng: 2 ≤ 5, 2 ≤ 3, 5 ≤ 9, 5 ≤ 6, 3 ≤ 8. Vì phép so sánh có tính bắc cầu, gốc 2 không lớn hơn bất kỳ hậu duệ nào, nên 2 là một phần tử nhỏ nhất của toàn heap.

Cây này có hình dạng hoàn chỉnh vì mức cuối có ba vị trí được điền liên tiếp từ trái. Nó không cần có số nút con trái bằng số nút con phải ở mọi nút.

### 3.2. Heap không phải cây tìm kiếm nhị phân

Trong cây minh họa, nút con trái 5 lớn hơn gốc 2. Điều này hoàn toàn hợp lệ với min-heap. Không có điều kiện “mọi giá trị bên trái nhỏ hơn gốc” như cách người học thường nhớ về cây tìm kiếm nhị phân.

Giữa hai nút anh em 5 và 3, heap cũng không yêu cầu 5 phải nhỏ hơn 3. Giữa 6 và 8 ở hai nhánh khác nhau, không có một vị trí cố định nào được quyết định chỉ bởi thứ tự toàn cục của chúng.

Bất biến heap chỉ tạo ra đủ quan hệ để biết gốc là phần tử ưu tiên nhất. Nó không tạo ra đường tìm kiếm một giá trị bất kỳ giống cây tìm kiếm có thứ tự.

### 3.3. Heap không phải mảng đã sắp xếp

Đọc cây trên theo từng mức từ trái sang phải được:

```text
[2, 5, 3, 9, 6, 8]
```

Dãy này là một biểu diễn heap hợp lệ nhưng không tăng dần: 5 đứng trước 3, 9 đứng trước 6 và 8. Không thể kết luận heap sai chỉ vì mảng biểu diễn không được sắp xếp.

Một dãy tăng dần cũng thỏa điều kiện min-heap theo cách đánh chỉ số thông thường, nhưng điều ngược lại không đúng. Heap cho phép nhiều bố trí khác nhau cho cùng một tập phần tử.

Trong bài, các hình và mảng heap là **mô hình thuật toán để giải thích**. Chúng không phải khẳng định về thứ tự `toString()`, `iterator()` hay mảng nội bộ mà một bản JDK cụ thể bắt buộc phải trả.

## 4. Vì sao cây heap thường được lưu trong mảng?

### 4.1. Vị trí cha và con tính được từ chỉ số

Với mảng đánh chỉ số từ 0, nút ở chỉ số `i` có:

| Quan hệ | Công thức |
| --- | --- |
| Cha, khi `i > 0` | `(i - 1) / 2`, lấy phần nguyên |
| Con trái | `2 * i + 1` |
| Con phải | `2 * i + 2` |

Một chỉ số con chỉ tồn tại nếu nhỏ hơn số phần tử `n`.

Với heap minh họa:

```text
Chỉ số:    0  1  2  3  4  5
Giá trị:  [2, 5, 3, 9, 6, 8]
```

- Cha của phần tử 8 tại chỉ số 5 là chỉ số 2, có giá trị 3.
- Con của chỉ số 1 là chỉ số 3 và 4, có giá trị 9 và 6.
- Chỉ số 2 có con trái tại 5, nhưng con phải tại 6 chưa tồn tại.

Do cây được điền liên tiếp theo mức, không cần khoảng trống giữa các nút đang dùng. Cũng không bắt buộc tạo một đối tượng nút có hai tham chiếu con cho mỗi phần tử.

`PriorityQueue` không cung cấp API đọc phần tử theo chỉ số của mảng heap. Công thức trên dùng để hiểu cách cài đặt, không biến hàng đợi thành `List`.

### 4.2. Chiều cao tăng chậm khi số phần tử tăng

Một cây nhị phân hoàn chỉnh có số mức tăng theo logarit của n. Khi thêm hoặc lấy đầu hàng đợi, phần tử cần sửa vị trí thường chỉ đi trên một đường giữa gốc và lá.

Đây là nguồn gốc của chi phí O(log n) cho bước duy trì heap: không phải sắp xếp lại mọi cặp phần tử sau mỗi lần thay đổi.

“Heap” trong cấu trúc dữ liệu này cũng không đồng nghĩa với vùng nhớ heap của JVM. Một đối tượng `PriorityQueue` và các phần tử của nó có thể được cấp phát trong vùng nhớ heap của JVM, nhưng tính chất cha–con đang học là tính chất của thuật toán, không phải quy tắc cấp phát bộ nhớ.

## 5. Thêm phần tử: đi lên để sửa thứ tự

### 5.1. Đặt cuối để giữ hình dạng

Bắt đầu với:

```text
[2, 5, 3, 9, 6, 8]
```

Cần thêm 1. Đặt 1 vào vị trí trống tiếp theo ở cuối mảng:

```text
[2, 5, 3, 9, 6, 8, 1]
```

Hình dạng cây vẫn hoàn chỉnh. Những quan hệ cũ vẫn đúng, nhưng cạnh giữa 3 và 1 đang vi phạm thứ tự min-heap.

### 5.2. Sift up: sửa theo đường đến gốc

So 1 với cha 3; đổi vị trí vì 1 đứng trước 3:

```text
[2, 5, 1, 9, 6, 8, 3]
```

Tiếp tục so 1 với cha 2; đổi vị trí:

```text
[1, 5, 2, 9, 6, 8, 3]
```

1 đã đến gốc. Mỗi bước chỉ cần xét nút đang di chuyển và cha của nó; không cần sắp xếp lại cả hai nhánh. Quá trình này thường được gọi là **đẩy lên (sift up)**.

Nếu thêm một giá trị lớn hơn hoặc bằng cha ngay từ đầu, có thể dừng sớm. Nếu thêm giá trị nhỏ nhất mới, có thể phải đi đến tận gốc.

### 5.3. Mã giả của mô hình

Đây là mã giả minh họa với mảng trừu tượng, không phải mã Java chạy độc lập hoặc mã nguồn được chép từ JDK:

```text
thêm x vào vị trí cuối
i = vị trí của x

trong khi i > 0:
    p = chỉ số cha của i
    nếu compare(heap[i], heap[p]) >= 0:
        dừng
    đổi heap[i] với heap[p]
    i = p
```

Hình thức cài đặt thực tế có thể giữ `x` ở biến tạm và dịch các nút cha xuống thay vì đổi từng cặp. Ý nghĩa duy trì bất biến vẫn như nhau.

## 6. Lấy gốc: dùng phần tử cuối rồi đẩy xuống

### 6.1. Không để một lỗ ở gốc

Tiếp tục với heap sau khi thêm 1:

```text
[1, 5, 2, 9, 6, 8, 3]
```

Khi lấy 1 ra:

1. Giữ 1 làm kết quả cần trả.
2. Đưa phần tử cuối 3 lên gốc.
3. Giảm số phần tử, bỏ vị trí cuối cũ.
4. Sửa quan hệ heap bắt đầu ở gốc.

Trạng thái tạm:

```text
[3, 5, 2, 9, 6, 8]
```

Hình dạng hoàn chỉnh được giữ, nhưng 3 ở gốc lớn hơn con phải 2.

### 6.2. Sift down: chọn con đứng trước

So hai con 5 và 2; chọn 2 vì nó nhỏ hơn theo thứ tự min-heap. Đổi 3 với 2:

```text
[2, 5, 3, 9, 6, 8]
```

Ở vị trí mới, 3 chỉ có con 8; vì 3 ≤ 8 nên dừng. Heap lại hợp lệ và phương thức trả 1 đã giữ trước đó.

Phải chọn con đứng trước trong hai con đang tồn tại. Nếu tùy ý đổi với 5, gốc mới có thể vẫn lớn hơn 2, làm bất biến tiếp tục sai ngay tại gốc.

Quá trình này gọi là **đẩy xuống (sift down)**. Mã giả:

```text
nếu heap rỗng:
    trả kết quả rỗng theo hợp đồng của phương thức

result = phần tử gốc
x = phần tử cuối
bỏ phần tử cuối

nếu vẫn còn phần tử:
    đặt x ở gốc
    i = 0
    trong khi i có con:
        c = chỉ số con đứng trước theo compare
        nếu compare(heap[i], heap[c]) <= 0:
            dừng
        đổi heap[i] với heap[c]
        i = c

trả result
```

Trường hợp chỉ có một phần tử không cần đẩy xuống sau khi lấy nó ra. Trường hợp chỉ có một con thì dùng con đó, không đọc một chỉ số con phải không tồn tại.

Cách lưu mảng, `siftUp`, `siftDown` và thao tác xây heap có thể đối chiếu trong [mã nguồn PriorityQueue của OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/PriorityQueue.java). Khi dùng API, hãy dựa vào hợp đồng công khai thay vì phụ thuộc một bố trí nội bộ cụ thể.

## 7. Từ cơ chế heap sang các phương thức Java

### 7.1. Xem, lấy và xóa theo giá trị là các yêu cầu khác nhau

| Phương thức | Ý nghĩa | Khi rỗng hoặc không tìm thấy |
| --- | --- | --- |
| `offer(e)`, `add(e)` | Thêm e, duy trì hàng đợi ưu tiên | Với phần tử hợp lệ, thêm thành công và trả `true` |
| `peek()` | Xem đầu, không xóa | `null` |
| `element()` | Xem đầu, không xóa | `NoSuchElementException` |
| `poll()` | Lấy/xóa đầu | `null` |
| `remove()` | Lấy/xóa đầu | `NoSuchElementException` |
| `remove(Object)` | Tìm và xóa một phần tử bằng giá trị | `false` nếu không có |
| `contains(Object)` | Kiểm tra có phần tử bằng giá trị | `false` nếu không có |

`PriorityQueue` không cho phần tử `null`, nên `null` từ `peek()` hoặc `poll()` biểu thị rỗng. Đừng nhận kết quả vào kiểu nguyên thủy bằng cách tự động mở hộp mà không xét rỗng: mở hộp một `Integer` bằng `null` sẽ gây `NullPointerException`.

Phân biệt `remove()` không đối số với `remove(Object)` có đối số. Lấy đầu có thể khai thác gốc heap; tìm một giá trị cụ thể phải tìm vị trí trước.

### 7.2. Dung lượng khởi tạo không phải giới hạn số phần tử

`new PriorityQueue<Integer>(2)` không tạo hàng đợi “chứa tối đa hai phần tử”. Con số 2 là dung lượng ban đầu. Hàng đợi tăng dung lượng khi cần; chính sách tăng cụ thể không phải điều nên đưa vào logic ứng dụng.

Lớp được mô tả là không có giới hạn dung lượng cố định. Điều đó không có nghĩa có bộ nhớ vô hạn, và cũng không biến nó thành hàng đợi chặn khi đầy.

### 7.3. Bảng chi phí và lý do

| Thao tác | Chi phí thường dùng khi phân tích | Lý do |
| --- | --- | --- |
| `peek()`, `element()`, `size()` | O(1) | Xem gốc hoặc biến đếm |
| `offer()`, `add()` | O(log n) cho duy trì heap | Đẩy lên theo một đường |
| `poll()`, `remove()` | O(log n) | Đẩy xuống theo một đường |
| `contains(Object)` | O(n) | Heap không chỉ đường tìm một giá trị bất kỳ |
| `remove(Object)` | O(n) | Tìm tuyến tính trước khi sửa heap |
| Lấy hết bằng `poll()` | O(n log n) | Lặp lấy đầu trên kích thước giảm dần |
| Bộ nhớ chứa n phần tử | O(n) | Mảng và các tham chiếu phần tử |

Đây là cách phân tích của lớp được mô tả trong [tài liệu API PriorityQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html). Khi mảng phải tăng dung lượng, riêng lần thêm đó còn có thể tốn O(n) để sao chép các tham chiếu. Không nên diễn đạt rằng mọi lần `offer()` riêng lẻ đều bị chặn ở đúng O(log n) bất kể việc cấp phát; phần đẩy lên là O(log n), còn chi phí tăng mảng được xét khi phân tích cả chuỗi thao tác.

Các mức chi phí này thường giả định một phép so sánh có chi phí O(1). Nếu comparator làm công việc đắt tiền, chi phí đó cũng tham gia tổng thời gian.

### 7.4. Xây từ dữ liệu sẵn có khác thêm từng phần tử

Có thể tạo trực tiếp:

```java
PriorityQueue<Integer> queue =
        new PriorityQueue<>(List.of(9, 2, 6, 1, 5));
```

Đoạn lệnh minh họa cần import `List` và `PriorityQueue`. Hàng đợi được tạo chứa đủ dữ liệu; không có yêu cầu đầu vào phải được sắp xếp trước.

Về thuật toán, xây heap từ dưới lên bằng cách đẩy xuống các nút không phải lá có thể đạt O(n). Không phải mỗi nút đều đi từ gốc xuống độ cao log n: rất nhiều nút nằm gần lá và cần ít hoặc không cần sửa. OpenJDK 17u có bước `heapify` như vậy cho đường khởi tạo phù hợp từ một collection thông thường.

Ngược lại, gọi `offer()` n lần có chặn trên O(n log n) cho các bước duy trì heap. Việc cả hai cách cùng tạo một hàng đợi hợp lệ không buộc chúng phải tạo cùng mảng nội bộ.

Đừng biến nhận xét về một đường cài đặt thành bảo đảm rằng mọi constructor trong mọi bản cài đặt có cùng chi phí, hoặc việc xây heap đã sắp xếp toàn bộ mảng.

## 8. Chương trình chạy: ưu tiên nhỏ nhất và lớn nhất

Lưu thành **PriorityQueueBasicsDemo.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

public class PriorityQueueBasicsDemo {
    private static <E> List<E> drain(PriorityQueue<E> queue) {
        List<E> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            result.add(queue.poll());
        }
        return result;
    }

    public static void main(String[] args) {
        PriorityQueue<Integer> minQueue = new PriorityQueue<>(2);
        for (int value : List.of(5, 1, 4, 2, 3, 1)) {
            minQueue.offer(value);
        }
        System.out.println("size: " + minQueue.size());
        System.out.println("peek: " + minQueue.peek());
        System.out.println("size after peek: " + minQueue.size());
        System.out.println("ordered copy: " + drain(new PriorityQueue<>(minQueue)));
        System.out.println("original size: " + minQueue.size());

        System.out.println("remove value 4: " + minQueue.remove(Integer.valueOf(4)));
        System.out.println("contains 4: " + minQueue.contains(4));
        System.out.println("remove value 99: " + minQueue.remove(Integer.valueOf(99)));
        System.out.println("poll order: " + drain(minQueue));
        System.out.println("empty peek: " + minQueue.peek());
        System.out.println("empty poll: " + minQueue.poll());

        try {
            minQueue.element();
        } catch (NoSuchElementException ex) {
            System.out.println("empty element: " + ex.getClass().getSimpleName());
        }
        try {
            minQueue.remove();
        } catch (NoSuchElementException ex) {
            System.out.println("empty remove: " + ex.getClass().getSimpleName());
        }
        try {
            minQueue.offer(null);
        } catch (NullPointerException ex) {
            System.out.println("null offer: " + ex.getClass().getSimpleName());
        }

        PriorityQueue<Integer> maxQueue =
                new PriorityQueue<>(Comparator.reverseOrder());
        maxQueue.addAll(List.of(5, 1, 4, 2, 3));
        System.out.println("reverse-order peek: " + maxQueue.peek());
        System.out.println("reverse-order poll: " + drain(maxQueue));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PriorityQueueBasicsDemo.java
java PriorityQueueBasicsDemo
```

Stdout chính xác:

```text
size: 6
peek: 1
size after peek: 6
ordered copy: [1, 1, 2, 3, 4, 5]
original size: 6
remove value 4: true
contains 4: false
remove value 99: false
poll order: [1, 1, 2, 3, 5]
empty peek: null
empty poll: null
empty element: NoSuchElementException
empty remove: NoSuchElementException
null offer: NullPointerException
reverse-order peek: 5
reverse-order poll: [5, 4, 3, 2, 1]
```

Các kết quả cần giải thích được:

- Dung lượng ban đầu là 2 nhưng thêm được sáu phần tử. Hai giá trị 1 đều tồn tại.
- `peek()` trả 1 mà không giảm kích thước.
- Bản sao được lấy hết bằng `poll()` cho thứ tự tăng dần; hàng đợi gốc vẫn còn sáu phần tử.
- `remove(Integer.valueOf(4))` xóa giá trị 4, không phải lấy phần tử đầu và không phải xóa chỉ số 4.
- Khi rỗng, hai cặp xem/lấy đầu khác nhau ở cách biểu thị thất bại: `null` hoặc ngoại lệ.
- Comparator đảo chiều làm số 5 đứng đầu và tạo thứ tự lấy 5, 4, 3, 2, 1.

Các chương trình trong bài được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2. Cờ này kiểm tra mã theo ngôn ngữ/API Java 17 và tạo bytecode tương ứng; JVM dùng để chạy ví dụ vẫn là bản đã nêu.

## 9. Các giới hạn bắt nguồn từ bất biến heap

Heap chỉ biết chắc phần tử đứng đầu theo quy tắc so sánh. Do đó, `iterator()` không có nghĩa là `poll()` liên tiếp; muốn xem cả thứ tự mà giữ nguồn, có thể lấy đầu trên bản sao hoặc sắp một danh sách bằng đúng comparator.

Heap cũng không có đường tìm kiếm theo giá trị như cây tìm kiếm nhị phân: `contains(Object)` và `remove(Object)` trước hết cần tìm phần tử theo `equals`. Hai phần tử có `compare(a,b) == 0` vẫn có thể cùng tồn tại; mức ưu tiên hòa không mặc định kéo theo FIFO.

Cuối cùng, thứ tự đã thiết lập không tự được xây lại nếu một trường được comparator đọc bị sửa trực tiếp. Khi thay đổi khóa ưu tiên, ứng dụng cần tổ chức bước gỡ/cập nhật/thêm lại hoặc xây cấu trúc mới có kiểm soát. Các bài chuyên về Comparator, tìm/xóa, iterator, đồng hạng và khóa khả biến phân tích chi tiết từng giới hạn này.
## 10. Khi nào nên chọn hàng đợi ưu tiên?

### 10.1. Hợp với việc liên tục chọn phần tử tốt nhất hiện tại

Các tình huống thường có dạng:

- Mỗi lần chọn sự kiện có thời điểm sớm nhất trong các sự kiện đang chờ.
- Mỗi lần chọn công việc có hạn chót hoặc chi phí thấp nhất theo quy ước.
- Giữ một nhóm ứng viên và thường xuyên lấy ứng viên đứng trước.
- Trong một thuật toán, các phần tử mới xuất hiện xen kẽ với việc lấy phần tử ưu tiên hiện tại.

Điểm chung là dữ liệu thay đổi và ứng dụng cần đầu ưu tiên nhiều lần. Không phải lần nào cũng cần toàn bộ danh sách đã sắp xếp.

Nếu chỉ có một lô dữ liệu cố định và cần xuất tất cả theo thứ tự một lần, sắp xếp danh sách có thể dễ hiểu hơn. Nếu cần lấy theo đúng thứ tự đến, dùng hàng đợi FIFO phù hợp. Nếu cần tìm một giá trị tùy ý hoặc truy vấn khoảng thường xuyên, chỉ tính chất heap chưa đáp ứng những yêu cầu đó một cách hiệu quả.

### 10.2. Giới hạn của ưu tiên một đầu

Min-heap cho phép xem nhỏ nhất nhanh; nó không đồng thời đặt lớn nhất ở một vị trí công khai cố định. Không có `getLast()` để lấy phần tử “ưu tiên thấp nhất” chỉ vì cấu trúc mang tên queue.

Với `Comparator.reverseOrder()`, phần tử lớn nhất theo thứ tự tự nhiên được đưa lên đầu, nhưng khi đó nhỏ nhất theo thứ tự tự nhiên lại không còn là đầu. Cần chọn hướng theo câu hỏi chính của ứng dụng.

### 10.3. Truy cập đồng thời là một yêu cầu riêng

`PriorityQueue` không được đồng bộ hóa. Nếu nhiều luồng cùng truy cập và có luồng sửa dữ liệu, cần cơ chế đồng bộ hoặc cấu trúc đồng thời phù hợp với yêu cầu. Một comparator đúng không làm các thao tác trên hàng đợi trở thành an toàn cho nhiều luồng.

Đây là ranh giới cần nhận biết khi chuyển từ ví dụ thuật toán một luồng sang ứng dụng thực. Không nên suy ra các đặc tính chặn, đánh thức luồng hay xử lý tác vụ từ cấu trúc heap.

## 11. Bảng kiểm khi phân tích một heap

| Câu hỏi | Cách tự kiểm |
| --- | --- |
| Gốc có thật sự ưu tiên nhất? | Kiểm tra quan hệ cha–con dọc từng nhánh, theo comparator hiện hành |
| Mảng heap có tăng dần không? | Không bắt buộc; chỉ cần bất biến heap và hình dạng cây hoàn chỉnh |
| Thêm một giá trị mới sửa thế nào? | Đặt vào cuối, rồi sift-up trên đường đến gốc |
| Lấy gốc sửa thế nào? | Chuyển phần tử cuối lên, rồi sift-down với con được ưu tiên hơn |
| Vì sao thao tác sửa heap thường O(log n)? | Chiều cao heap nhị phân hoàn chỉnh tăng theo logarit số phần tử |
| Vì sao peek thường O(1)? | Phần tử đầu đã được duy trì tại gốc; peek không sắp xếp lại |

Không nên viết kiểm thử yêu cầu một mảng heap nội bộ cụ thể cho mọi JDK. Hãy kiểm tra phần tử đầu, tập phần tử còn lại và thứ tự các kết quả `poll()` khi quy tắc so sánh phân biệt được chúng.
## 12. Bài tập về bất biến heap (có lời giải)

**Bài 1.** `[2, 5, 3, 9, 6, 8]` là min-heap hợp lệ không, và có tăng dần không?

**Lời giải:** hợp lệ vì từng cha không lớn hơn con; không tăng dần vì 5 đứng trước 3. Heap không phải dãy đã sắp.

**Bài 2.** `[2, 5, 1, 9, 6, 8]` vi phạm ở đâu?

**Lời giải:** gốc 2 lớn hơn con phải 1; chỉ cần một cạnh sai là đủ để kết luận bất biến bị phá.

**Bài 3.** Với heap `[2, 5, 3, 9, 6, 8]`, thêm 1 rồi lấy đầu. Mỗi lần sửa xong mảng mô hình là gì?

**Lời giải:** sift-up cho `[1, 5, 2, 9, 6, 8, 3]`; lấy ra 1, thay bằng 3 rồi sift-down cho `[2, 5, 3, 9, 6, 8]`. Đây là trace của mô hình đã chọn, không phải hợp đồng mảng nội bộ.

**Bài 4.** Vì sao biết gốc là nhỏ nhất không đồng nghĩa `contains(8)` chạy trong O(log n)?

**Lời giải:** bất biến cha–con không chỉ ra 8 nằm ở nhánh nào; các nhánh không phân hoạch theo giá trị như cây tìm kiếm. Trường hợp xấu phải kiểm tra tuyến tính.
## 13. Tài liệu đối chiếu

- [PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html): hợp đồng lớp và các phương thức.
- [Comparator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): quy tắc so sánh, đảo chiều và ghép tiêu chí.
- [PriorityQueue trong OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/PriorityQueue.java): đối chiếu cách tổ chức heap nhị phân và các bước duy trì cấu trúc.

Điểm cần giữ khi sử dụng là: comparator quyết định ai đứng trước; heap duy trì đủ quan hệ để đầu ưu tiên ở gốc; thao tác lấy đầu mới lần lượt tạo ra thứ tự ưu tiên. Không thay bước lấy đầu bằng một giả định rằng toàn bộ dữ liệu bên trong đã được sắp xếp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="priority-queue-002">Thứ tự tự nhiên và Comparator tùy chỉnh</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự tự nhiên và Comparator tùy chỉnh

## 1. PriorityQueue chọn phần tử nào để xử lý tiếp?

Giả sử chương trình nhận các số `6, 2, 9, 1, 5`. Nếu dùng hàng đợi FIFO, số 6 đến trước sẽ được lấy trước. Nếu yêu cầu là luôn lấy giá trị nhỏ nhất hiện có, FIFO không mô tả đúng công việc.

`PriorityQueue` duy trì một đầu hàng đợi theo **quy tắc ưu tiên**. Quy tắc ấy có thể là thứ tự tự nhiên của kiểu phần tử hoặc một `Comparator` do bạn cung cấp.

Điểm phải đọc chính xác là: đầu hàng đợi là phần tử **nhỏ nhất theo quy tắc so sánh đang dùng**. Với thứ tự tự nhiên của số nguyên, đó là số nhỏ nhất. Với bộ so sánh đảo, đó lại là số lớn nhất về số học.

Không có một khái niệm “ưu tiên cao” chung cho mọi dữ liệu. Điểm cao có thể cần xử lý trước, nhưng thời hạn sớm thường có giá trị thời gian nhỏ hơn. Bộ so sánh phải biến ý nghĩa nghiệp vụ thành một thứ tự cụ thể.

Bài học đi từ số nguyên đến đối tượng công việc, xử lý trường hợp cùng ưu tiên và kiểm chứng trình tự lấy ra. Các ví dụ dùng API có trong Java 17.

Bạn cần biết cách tạo đối tượng, khai báo kiểu tổng quát và dùng vòng lặp. `Comparable`, `Comparator` và ý nghĩa kết quả so sánh sẽ được giới thiệu trước khi xuất hiện trong chương trình đầy đủ.

## 2. Thứ tự tự nhiên đến từ kiểu phần tử

Thứ tự tự nhiên (natural ordering) được kiểu phần tử cung cấp qua `Comparable<T>`, với phương thức `compareTo`.

Khi đọc `a.compareTo(b)`:

| Kết quả | Ý nghĩa theo thứ tự của kiểu |
| --- | --- |
| Nhỏ hơn 0 | a đứng trước b |
| Bằng 0 | a và b cùng mức về thứ tự |
| Lớn hơn 0 | a đứng sau b |

Chỉ dấu âm, 0 hoặc dương là ý nghĩa chung. Một phép so sánh hợp lệ không bắt buộc luôn trả đúng -1 hoặc 1.

Integer đã có thứ tự tự nhiên theo giá trị số. Vì vậy:

~~~java
PriorityQueue<Integer> numbers = new PriorityQueue<>();
~~~

tạo một hàng đợi có thể chọn số nhỏ nhất làm đầu mà không cần truyền quy tắc riêng. Đây là khai báo minh họa với kiểu `java.util.PriorityQueue`.

Sau khi đưa vào `6, 2, 9, 1, 5`:

~~~text
peek()          → 1, không lấy ra
poll liên tiếp  → 1, 2, 5, 6, 9
~~~

Thứ tự tự nhiên không có nghĩa “thứ tự dữ liệu xuất hiện” hoặc “tự tăng theo mọi trường”. Với một kiểu tự định nghĩa, tác giả kiểu phải quyết định thứ tự qua Comparable nếu muốn cung cấp thứ tự tự nhiên. [Java 17: Comparable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html).

Một record không tự có compareTo chỉ vì nó có các trường số, equals hoặc toString. Khi kiểu không có thứ tự tự nhiên phù hợp, có thể cung cấp một Comparator bên ngoài.

## 3. Comparator mô tả cách ưu tiên riêng của hàng đợi

`Comparator<T>` có phương thức chính:

~~~java
int compare(T left, T right);
~~~

Đây là chữ ký rút gọn để đọc ý nghĩa. Quy tắc nằm ở đối tượng so sánh bên ngoài thay vì nằm trong lớp phần tử.

Với `rule.compare(a, b)`:

~~~text
âm   → a phải được xếp trước b theo rule
0    → rule chưa phân biệt mức của a và b
dương→ a phải được xếp sau b theo rule
~~~

Khi truyền một Comparator khác null vào hàm khởi tạo PriorityQueue, chính quy tắc đó quyết định phần tử nhỏ nhất và đầu hàng đợi. Không có bước tự động dùng thứ tự tự nhiên làm tiêu chí phụ nếu Comparator trả 0.

Bởi vậy, cùng dữ liệu có thể có nhiều hàng đợi phục vụ nhiều mục tiêu:

| Nhu cầu | Quy tắc cần diễn đạt |
| --- | --- |
| Lấy số nhỏ nhất | Thứ tự tự nhiên tăng dần |
| Lấy số lớn nhất | Ngược thứ tự tự nhiên |
| Công việc có mức khẩn lớn hơn đi trước | So sánh mức khẩn theo chiều giảm |
| Cùng mức khẩn thì công việc đến trước đi trước | Ghép số thứ tự đến theo chiều tăng |

Bạn không cần bắt một lớp thực hiện nhiều thứ tự tự nhiên khác nhau cho các màn hình hoặc quy trình khác nhau. Những quy tắc theo từng nhu cầu có thể được giữ ở các Comparator riêng.

## 4. Muốn số lớn nhất ở đầu: đảo quy tắc so sánh

Ví dụ:

~~~java
PriorityQueue<Integer> largestFirst =
        new PriorityQueue<>(Comparator.reverseOrder());
~~~

Với cùng đầu vào `6, 2, 9, 1, 5`:

~~~text
peek()         → 9
poll liên tiếp → 9, 6, 5, 2, 1
~~~

Theo bộ so sánh đảo, số 9 đứng trước số 6, nên 9 là phần tử nhỏ nhất **trong quan hệ do bộ so sánh định nghĩa**. Đây không phải PriorityQueue tự chuyển sang một API “lấy cuối”.

Các thao tác vẫn giống nhau: offer/add thêm, peek xem đầu, poll lấy và xóa đầu. Bạn thay quy tắc ưu tiên lúc tạo đối tượng, không đổi poll thành một phương thức khác.

Phương thức `comparator()` trả bộ so sánh tường minh hoặc null khi dùng thứ tự tự nhiên. Vì vậy:

~~~text
queue.comparator() == null
~~~

không có nghĩa “hàng đợi chưa có thứ tự”. Nó biểu thị không dùng một đối tượng Comparator riêng.

Cũng không suy từ comparator null rằng hàng đợi cho phép thành viên null. Đó là hai câu hỏi khác nhau.

## 5. Chuyển quy tắc nghiệp vụ thành Comparator

Trong bài, mỗi công việc có ba thành phần:

~~~text
id       : tên để nhận biết công việc trong ví dụ
urgency  : mức khẩn; số lớn cần được xử lý trước
sequence : số thứ tự khi công việc được đưa vào; số nhỏ đến trước
~~~

Mô hình:

~~~java
record Job(String id, int urgency, long sequence) {}
~~~

là một kiểu giá trị đơn giản. Nó không triển khai Comparable. Các thành phần dùng cho quy tắc được giữ ổn định sau khi đối tượng được tạo.

Để xử lý mức khẩn cao trước, rồi theo thứ tự đến trong cùng mức, dùng:

~~~java
Comparator<Job> executionOrder =
        Comparator.comparingInt(Job::urgency).reversed()
                .thenComparingLong(Job::sequence);
~~~

Đây là quy tắc được dùng trong chương trình hoàn chỉnh.

Cách đọc từng phần:

1. `Job::urgency` lấy mức khẩn của công việc.
2. `comparingInt` tạo quy tắc so sánh số nguyên theo mức khẩn.
3. `reversed` đảo riêng tiêu chí mức khẩn để số lớn đứng trước.
4. `thenComparingLong` chỉ dùng số thứ tự khi mức khẩn bằng nhau.

Hàng đợi được tạo với:

~~~java
PriorityQueue<Job> scheduled = new PriorityQueue<>(executionOrder);
~~~

Việc Job không có compareTo không gây trở ngại khi Comparator cung cấp so sánh hợp lệ cho các phần tử. Ngược lại, nếu bỏ bộ so sánh mà vẫn dùng Job này, bạn chưa cung cấp con đường tạo thứ tự cho hàng đợi.

## 6. Cùng mức ưu tiên chỉ có thứ tự đến khi đặt khóa phụ

Comparator chỉ đọc `urgency` sẽ trả 0 cho hai Job cùng mức. PriorityQueue vẫn giữ cả hai nhưng không hứa xử lý trước Job được thêm trước. Nếu yêu cầu mức khẩn cao trước và cùng mức thì người đến sớm trước, hãy so `urgency` giảm dần rồi `sequence` tăng dần như `executionOrder` ở mục 5.

Ví dụ `alert(5,1)` và `backup(5,2)` đều đứng trước `report(2,0)`; trong nhóm mức 5, alert đứng trước backup nhờ sequence. Nếu sequence không phân biệt được hai lần gửi, comparator vẫn cho phép hòa. Thứ tự nhận việc và chính sách cấp sequence được phân tích sâu ở bài về phần tử đồng hạng.
## 7. Đặt reversed sai vị trí sẽ đổi cả tiêu chí phụ

So sánh hai biểu thức:

~~~java
Comparator<Job> correctForThisExample =
        Comparator.comparingInt(Job::urgency).reversed()
                .thenComparingLong(Job::sequence);
~~~

và:

~~~java
Comparator<Job> differentRule =
        Comparator.comparingInt(Job::urgency)
                .thenComparingLong(Job::sequence)
                .reversed();
~~~

Biểu thức thứ nhất đảo urgency trước khi ghép sequence. Nó tạo “mức khẩn giảm, số thứ tự tăng”.

Biểu thức thứ hai ghép hai tiêu chí tăng rồi đảo toàn bộ. Nó tạo “mức khẩn giảm, số thứ tự giảm”. Trong nhóm cùng mức, công việc có số đến muộn hơn được xếp trước.

Không thể phát hiện lỗi này bằng mẫu mà mọi công việc đều khác mức khẩn. Hãy có ít nhất một cặp bằng urgency nhưng khác sequence để kiểm chứng ý định.

Các hàm ghép Comparator mô tả thứ tự theo từng tiêu chí. Chỉ khi tiêu chí trước cho kết quả 0 thì tiêu chí sau mới quyết định. Hãy đọc phạm vi của mỗi reversed, không chỉ nhìn thấy từ khóa ấy rồi kết luận “đã là ưu tiên giảm”. [Java 17: Comparator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

## 8. Đồng hạng không làm Queue loại trùng

Với `alpha` và `beta` cùng `urgency = 3`, phép so sánh theo mức khẩn cho 0 nhưng hai record khác nhau vẫn có thể cùng nằm trong PriorityQueue. Thêm lại một phần tử cũng tạo thêm một lần xuất hiện; hàng đợi không có hợp đồng khử trùng.

`contains(Object)` và `remove(Object)` tìm phần tử theo `equals`, không tìm bất kỳ Job nào cùng mức ưu tiên. Chương trình ở mục 10 kiểm chứng kết quả so sánh, phép bằng, kích thước và thao tác tìm một Job khác. Phân biệt rõ `remove()` lấy đầu ưu tiên với `remove(Object)` xóa một giá trị phù hợp.
## 9. Lượt duyệt không phải trình tự lấy đầu

Heap chỉ duy trì quan hệ cha–con đủ để biết phần tử đầu. `iterator()`, for-each, `toArray()` và cách in queue không được bảo đảm theo thứ tự ưu tiên. Muốn xem thứ tự xử lý, dùng `poll()` trên bản sao hoặc sắp một danh sách theo comparator thích hợp. Bài về iterator đi sâu vào các bảo đảm và cách kiểm thử.
## 10. Chương trình đầy đủ dùng Java 17

Lưu thành `PriorityQueueOrderingDemo.java`. Chương trình tránh in trình tự bất kỳ của iterator như một kết quả “đã sắp”. Với các trường hợp bằng ưu tiên chưa có tiêu chí phụ, nó chỉ kiểm tra số lượng và mức khẩn, không giả định thứ tự tên.

Job có toString riêng để kết quả ngắn gọn, theo dạng `id/urgency/sequence`. Thứ tự không được quyết định từ chuỗi hiển thị này; Comparator đọc các trường tương ứng.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public class PriorityQueueOrderingDemo {
    record Job(String id, int urgency, long sequence) {
        @Override
        public String toString() {
            return id + "/" + urgency + "/" + sequence;
        }
    }

    static <E> List<E> drain(Queue<E> queue) {
        List<E> result = new ArrayList<>();
        E next;
        while ((next = queue.poll()) != null) {
            result.add(next);
        }
        return result;
    }

    public static void main(String[] args) {
        List<Integer> input = List.of(6, 2, 9, 1, 5);
        PriorityQueue<Integer> natural = new PriorityQueue<>();
        natural.addAll(input);
        System.out.println("natural comparator is null=" + (natural.comparator() == null));
        System.out.println("natural peek=" + natural.peek());
        System.out.println("natural size after peek=" + natural.size());
        List<Integer> naturalSnapshot = new ArrayList<>(natural);
        naturalSnapshot.sort(natural.comparator());
        System.out.println("natural sorted snapshot=" + naturalSnapshot);
        System.out.println("natural copy drain=" + drain(new PriorityQueue<>(natural)));
        System.out.println("natural original size=" + natural.size());
        System.out.println("natural original poll=" + natural.poll());
        System.out.println("natural original size after poll=" + natural.size());

        PriorityQueue<Integer> reverse =
                new PriorityQueue<>(Comparator.reverseOrder());
        reverse.addAll(input);
        System.out.println("reverse comparator is null=" + (reverse.comparator() == null));
        System.out.println("reverse peek=" + reverse.peek());
        List<Integer> reverseSnapshot = new ArrayList<>(reverse);
        reverseSnapshot.sort(reverse.comparator());
        System.out.println("reverse sorted snapshot=" + reverseSnapshot);
        System.out.println("reverse drain=" + drain(reverse));
        System.out.println("reverse empty peek=" + reverse.peek());
        System.out.println("reverse empty poll=" + reverse.poll());

        Job alpha = new Job("alpha", 3, 0);
        Job beta = new Job("beta", 3, 1);
        Comparator<Job> urgencyOnly = Comparator.comparingInt(Job::urgency);
        PriorityQueue<Job> ties = new PriorityQueue<>(urgencyOnly);
        ties.offer(alpha);
        ties.offer(beta);
        System.out.println("tied comparison=" + urgencyOnly.compare(alpha, beta));
        System.out.println("tied equals=" + alpha.equals(beta));
        System.out.println("tied size=" + ties.size());
        Job probe = new Job("unknown", 3, 99);
        System.out.println("contains equal-priority probe=" + ties.contains(probe));
        System.out.println("remove equal-priority probe=" + ties.remove(probe));
        System.out.println("offer same job again=" + ties.offer(alpha));
        System.out.println("size with repeated job=" + ties.size());
        List<Job> tiedResults = drain(ties);
        System.out.println("tied drain count=" + tiedResults.size());
        System.out.println("all tied urgency=3="
                + tiedResults.stream().allMatch(job -> job.urgency() == 3));

        List<Job> jobs = List.of(
                new Job("report", 2, 0),
                new Job("alert", 5, 1),
                new Job("backup", 5, 2),
                new Job("cleanup", 1, 3));
        Comparator<Job> executionOrder =
                Comparator.comparingInt(Job::urgency).reversed()
                        .thenComparingLong(Job::sequence);
        PriorityQueue<Job> scheduled = new PriorityQueue<>(executionOrder);
        scheduled.addAll(jobs);
        System.out.println("scheduled peek=" + scheduled.peek());
        List<Job> scheduledSnapshot = new ArrayList<>(scheduled);
        scheduledSnapshot.sort(scheduled.comparator());
        System.out.println("scheduled sorted snapshot=" + scheduledSnapshot);
        System.out.println("scheduled copy drain="
                + drain(new PriorityQueue<>(scheduled)));
        System.out.println("scheduled original size=" + scheduled.size());

        PriorityQueue<Job> noRule = new PriorityQueue<>();
        try {
            noRule.offer(new Job("missing", 1, 0));
        } catch (ClassCastException exception) {
            System.out.println("missing natural ordering="
                    + exception.getClass().getSimpleName());
        }

        PriorityQueue<Integer> nullFriendlyRule =
                new PriorityQueue<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        try {
            nullFriendlyRule.offer(null);
        } catch (NullPointerException exception) {
            System.out.println("null despite comparator="
                    + exception.getClass().getSimpleName());
        }
        System.out.println("null queue size=" + nullFriendlyRule.size());

        Comparator<Integer> unsafe = (left, right) -> left - right;
        System.out.println("unsafe edge sign="
                + Integer.signum(unsafe.compare(Integer.MIN_VALUE, Integer.MAX_VALUE)));
        System.out.println("safe edge sign="
                + Integer.signum(Integer.compare(Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 PriorityQueueOrderingDemo.java
java PriorityQueueOrderingDemo
~~~

Dùng JDK 17 trở lên. `--release 17` kiểm tra mã và API theo Java 17; tên tệp khớp lớp public.

Kết quả chuẩn:

~~~text
natural comparator is null=true
natural peek=1
natural size after peek=5
natural sorted snapshot=[1, 2, 5, 6, 9]
natural copy drain=[1, 2, 5, 6, 9]
natural original size=5
natural original poll=1
natural original size after poll=4
reverse comparator is null=false
reverse peek=9
reverse sorted snapshot=[9, 6, 5, 2, 1]
reverse drain=[9, 6, 5, 2, 1]
reverse empty peek=null
reverse empty poll=null
tied comparison=0
tied equals=false
tied size=2
contains equal-priority probe=false
remove equal-priority probe=false
offer same job again=true
size with repeated job=3
tied drain count=3
all tied urgency=3=true
scheduled peek=alert/5/1
scheduled sorted snapshot=[alert/5/1, backup/5/2, report/2/0, cleanup/1/3]
scheduled copy drain=[alert/5/1, backup/5/2, report/2/0, cleanup/1/3]
scheduled original size=4
missing natural ordering=ClassCastException
null despite comparator=NullPointerException
null queue size=0
unsafe edge sign=1
safe edge sign=-1
~~~

## 11. Đọc bằng chứng từ chương trình

### 11.1. Tự nhiên: số nhỏ ở đầu

`natural peek=1` đi cùng `natural size after peek=5` xác nhận việc xem đầu không xóa.

Ảnh chụp được sắp và bản sao được lấy hết đều cho `[1, 2, 5, 6, 9]`. `natural original size=5` cho thấy lấy hết trên bản sao không làm rỗng nguồn.

Sau khi thật sự gọi poll trên nguồn, kết quả là 1 và kích thước nguồn còn 4. Đó là thao tác thay đổi dữ liệu, khác với peek.

### 11.2. Đảo: số lớn ở đầu

`reverse peek=9` và `reverse drain=[9, 6, 5, 2, 1]` xác nhận quy tắc đảo đã đổi thứ tự lấy ra.

Hàng đợi reverse bị hàm drain lấy hết trực tiếp. Vì vậy, hai lời gọi xem/lấy sau đó trả null do rỗng. Không có một phần tử mặc định được tạo ra khi hết dữ liệu.

### 11.3. Cùng mức không bị bỏ

`tied size=2` và `size with repeated job=3` cho thấy hàng đợi giữ đủ cả các giá trị khác nhau cùng mức lẫn một lần đưa lại chính alpha.

Kết quả `tied drain count=3` và `all tied urgency=3=true` kiểm tra điều hợp đồng cho phép khẳng định. Chương trình không in thứ tự tên ở nhóm này, vì Comparator chưa đặt một tiêu chí phân biệt giữa các phần tử cùng mức 3.

### 11.4. Tiêu chí phụ tạo thứ tự mong muốn

`scheduled peek=alert/5/1` là đầu theo “mức khẩn giảm rồi sequence tăng”.

Ảnh chụp được sắp và bản sao được lấy hết cùng cho:

~~~text
alert/5/1, backup/5/2, report/2/0, cleanup/1/3
~~~

Alert đứng trước backup vì cả hai cùng mức 5 nhưng alert có sequence nhỏ hơn. Report không lên trước chỉ vì đến sớm hơn; urgency là tiêu chí chính.

`scheduled original size=4` cho thấy việc tạo ảnh chụp và lấy bản sao không rút dữ liệu khỏi nguồn.

## 12. Xem thứ tự mà không làm rỗng hàng đợi

Có hai cách trong chương trình, với mục đích kiểm chứng tương ứng.

**Cách thứ nhất: chụp thành danh sách rồi sắp.**

~~~java
List<Job> snapshot = new ArrayList<>(scheduled);
snapshot.sort(scheduled.comparator());
~~~

Danh sách ban đầu chưa được xem là có thứ tự ưu tiên. Lời gọi sort mới áp dụng quy tắc của nguồn cho ảnh chụp. Thay đổi trình tự danh sách không thay đổi thành viên của hàng đợi.

Đối với ví dụ số tự nhiên, comparator() trả null. `List.sort(null)` được định nghĩa là dùng thứ tự tự nhiên, nên cách viết tương tự vẫn phù hợp với các phần tử có thứ tự tự nhiên hợp lệ. [Java 17: List.sort](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

**Cách thứ hai: tạo PriorityQueue mới từ PriorityQueue nguồn rồi poll bản sao.**

~~~java
PriorityQueue<Job> copy = new PriorityQueue<>(scheduled);
List<Job> ordered = drain(copy);
~~~

Hàm khởi tạo từ PriorityQueue giữ quy tắc thứ tự của nguồn. Bản sao có cấu trúc thành viên độc lập, nên drain(copy) không lấy mất phần tử của scheduled.

Cả hai cách đều là sao chép nông đối với phần tử: danh sách hoặc hàng đợi mới vẫn có thể giữ cùng tham chiếu đối tượng Job. Trong bài, Job là giá trị record với các trường dùng cho ưu tiên ổn định, nên không có thao tác sửa phần tử gây nhiễu.

Nếu Comparator chưa phân biệt các phần tử cùng mức, một danh sách được sort có thể giữ một thứ tự giữa chúng theo đầu vào ảnh chụp, còn poll bản sao có thể chọn khác trong nhóm. Đừng coi một lần hai kết quả trùng nhau là cam kết ổn định của PriorityQueue.

## 13. Comparator phải tuân thủ hợp đồng thứ tự

Một quy tắc tốt cần nhất quán trong các lần so sánh:

- Đảo hai đối số phải đảo dấu kết quả.
- Quan hệ trước/sau phải bắc cầu.
- Khi hai phần tử so sánh bằng 0, chúng phải có quan hệ cùng dấu với một phần tử thứ ba.

Quy tắc phải áp dụng được cho mọi phần tử mà hàng đợi được phép nhận, không chỉ cho một cặp dữ liệu mẫu.

Đừng so sánh số nguyên bằng cách lấy hiệu nếu có thể tràn:

~~~java
Comparator<Integer> unsafe = (left, right) -> left - right;
~~~

Đây là đoạn minh họa lỗi, không được dùng để tạo hàng đợi trong chương trình.

Với Integer.MIN_VALUE và Integer.MAX_VALUE, phép trừ có thể tràn và tạo dấu ngược ý định. Chương trình in:

~~~text
unsafe edge sign=1
safe edge sign=-1
~~~

Dùng Integer.compare hoặc các hàm comparingInt để diễn đạt so sánh số nguyên thay vì dựa vào phép trừ. Khi đảo chiều, dùng reversed hoặc so sánh đúng chiều các đối số.

Đừng trả 1 chỉ để hai phần tử khác nhau không bị “trùng”. PriorityQueue không cần mẹo đó để giữ nhiều phần tử cùng mức, còn một Comparator vi phạm hợp đồng có thể khiến việc chọn đầu không còn đúng yêu cầu.

## 14. Khóa so sánh cần ổn định khi ở trong Queue

Việc sửa trực tiếp `urgency` của một đối tượng đang nằm trong queue hoặc thay đổi trạng thái ngoài mà comparator đọc không tự gọi sift-up/sift-down. `peek()` có thể vẫn trả đầu theo bố trí cũ. Record `Job` của ví dụ giữ các trường so sánh ổn định; nếu cần đổi ưu tiên, hãy thực hiện bước gỡ mục cũ rồi thêm giá trị mới có kiểm soát, đồng thời xét phần tử trùng và chi phí tìm kiếm.

Bài về thay đổi độ ưu tiên giải thích thêm các cách phục hồi heap và các ràng buộc khi cập nhật liên tục.
## 15. Không có thứ tự tự nhiên và không cho phép null

Có hai lỗi khác nhau được kiểm chứng ở cuối chương trình.

**Không có quy tắc so sánh cho Job.**

~~~java
PriorityQueue<Job> noRule = new PriorityQueue<>();
~~~

Job trong bài không triển khai Comparable. Khi không truyền Comparator, việc đưa Job vào dẫn đến ClassCastException trong ví dụ.

Khai báo PriorityQueue<Job> vẫn có thể biên dịch vì PriorityQueue phải hỗ trợ cả những kiểu không Comparable khi có bộ so sánh bên ngoài. Kiểu tổng quát không tự sinh compareTo cho Job.

**Một Comparator chấp nhận null không làm PriorityQueue chấp nhận null.**

Chương trình tạo một bộ so sánh bọc bằng nullsFirst rồi vẫn nhận NullPointerException khi offer(null). PriorityQueue cấm thành viên null theo hợp đồng của chính lớp, trước khi bạn có thể dùng quy tắc ưu tiên để lưu nó.

Không áp dụng suy luận “Comparator của tôi xử lý được null nên mọi cấu trúc nhận nó đều chứa được null”. Chính sách phần tử thuộc về cả lớp nhận, không chỉ hàm so sánh.

Khi hàng đợi hợp lệ rỗng, peek và poll dùng null làm kết quả không có phần tử. Đó là một vai trò khác với cố chèn null vào làm dữ liệu.

## 16. Những giới hạn cần nhớ khi chọn PriorityQueue

PriorityQueue phù hợp khi cần liên tục thêm và lấy phần tử ở đầu theo một thứ tự ưu tiên. Nó không tự cung cấp một danh sách đã sắp hoàn chỉnh cho mọi lượt duyệt.

API mô tả chi phí O(log n) cho các thao tác thêm/lấy như offer, add, poll và remove không đối số; các phép peek, element và size có thời gian hằng. Tìm hoặc xóa theo một đối tượng cụ thể qua contains/remove(Object) cần thời gian tuyến tính trong cách triển khai này.

Không dùng một phép in hoặc một lần đo nhỏ để suy ra những cam kết lớn hơn. Những con số trên mô tả lớp PriorityQueue, không phải mọi Queue có thể dùng trong Java.

Số dung lượng khởi tạo không phải một giới hạn cố định số công việc được nhận. PriorityQueue tự tăng dung lượng lưu trữ theo nhu cầu; chính sách tăng cụ thể không phải API mà mã ứng dụng nên phụ thuộc.

Đồng thời, lớp không được đồng bộ hóa sẵn để nhiều luồng cùng sửa tự do. Chọn Comparator đúng không thay thế thiết kế chia sẻ dữ liệu. Chương trình bài học dùng một luồng và các giá trị ổn định để kết quả có thể đối chiếu trực tiếp.

Nếu mục tiêu chỉ là lấy một ảnh chụp rồi hiển thị toàn bộ theo thứ tự, một danh sách được sort có thể thể hiện công việc đó rõ hơn. Nếu cần dữ liệu duy nhất theo một định nghĩa bằng nhau, phải bổ sung mô hình duy nhất phù hợp; PriorityQueue không tự loại trùng.

## 17. Những nhầm lẫn thường gặp

| Nhầm lẫn | Cách kiểm tra đúng |
| --- | --- |
| “Priority cao nghĩa là số lớn nhất luôn ra trước” | Xem Comparator; mặc định Integer nhỏ nhất ở đầu |
| “Poll đổi sang lấy cuối khi dùng reverseOrder” | Poll vẫn lấy đầu, quy tắc xác định đầu đã đổi |
| “Comparator trả null nghĩa là chưa có thứ tự” | Comparator() null biểu thị thứ tự tự nhiên |
| “Record tự có compareTo” | Cần Comparable hoặc Comparator thực sự |
| “Cùng mức ưu tiên sẽ FIFO” | Không có cam kết đó nếu chưa thêm tiêu chí thứ tự đến |
| “Compare bằng 0 thì phần tử mới bị bỏ” | PriorityQueue có thể giữ nhiều thành viên cùng mức và cả bản lặp |
| “Contains tìm mọi phần tử có cùng mức” | Nó tìm theo equals, không dùng nhóm ưu tiên làm nhận diện |
| “Iterator/toString thể hiện thứ tự poll” | Không có bảo đảm; kiểm chứng bằng poll hoặc sắp ảnh chụp |
| “Reversed ở đâu trong chuỗi cũng như nhau” | Đảo cuối chuỗi sẽ đảo cả tiêu chí phụ đã ghép |
| “Lấy hiệu hai số là so sánh an toàn” | Có thể tràn; dùng Integer.compare hoặc hàm chuẩn |
| “Sửa urgency trực tiếp là cập nhật hàng đợi” | Hàng đợi không tự theo dõi và đặt lại vị trí |
| “NullsFirst cho phép chèn null vào PriorityQueue” | Lớp vẫn cấm null |
| “Bản sao tạo đối tượng phần tử hoàn toàn mới” | Việc sao chép cấu trúc không tự nhân bản sâu Job |
| “Dung lượng ban đầu là số công việc tối đa” | Đó không phải giới hạn cố định của PriorityQueue |

Mẫu kiểm tra quan trọng nhất nên có một phần tử đến sớm nhưng ưu tiên thấp, hai phần tử cùng ưu tiên và những giá trị số ở biên. Ba nhóm ấy giúp phát hiện nhầm FIFO, thiếu tiêu chí phụ và lỗi so sánh bằng phép trừ.

## 18. Thực hành có lời giải

### Bài 1: thứ tự tự nhiên

Đưa `8, 3, 6, 1` vào PriorityQueue<Integer> mặc định. Peek trả gì? Lấy hết bằng poll theo thứ tự nào?

**Lời giải:** Peek trả 1 và không xóa. Poll lần lượt cho `1, 3, 6, 8`. Số 8 đến trước không làm nó được ưu tiên hơn.

### Bài 2: đảo quy tắc

Dùng cùng dữ liệu với Comparator.reverseOrder. Đầu và trình tự lấy hết là gì?

**Lời giải:** Đầu là 8. Poll cho `8, 6, 3, 1`. Quy tắc đảo khiến số lớn đứng trước, nhưng poll vẫn thực hiện thao tác lấy đầu.

### Bài 3: ưu tiên nghiệp vụ

Job A có urgency 2, sequence 0; Job B có urgency 5, sequence 1. Theo executionOrder trong bài, ai ra trước?

**Lời giải:** B ra trước vì urgency là tiêu chí chính theo chiều giảm. Sequence sớm hơn của A chỉ có tác dụng khi urgency bằng nhau.

### Bài 4: cùng mức

A và B cùng urgency 4, A đến trước B. Comparator chỉ xét urgency. Có thể kiểm thử rằng A chắc chắn được poll trước không?

**Lời giải:** Không theo hợp đồng PriorityQueue. Muốn thứ tự đến được bảo đảm, cần thêm một tiêu chí tương ứng như sequence tăng dần được cấp đúng cho từng lần đưa vào.

### Bài 5: đảo toàn bộ

Bạn ghép urgency tăng rồi sequence tăng, cuối cùng reversed toàn bộ. Trong nhóm cùng urgency, sequence nhỏ hay lớn đứng trước?

**Lời giải:** Sequence lớn đứng trước vì toàn bộ quy tắc đã bị đảo. Điều đó khác với đảo riêng urgency rồi ghép sequence tăng.

### Bài 6: phần tử cùng ưu tiên

Hai Job khác nhau theo equals nhưng Comparator trả 0. Thêm cả hai vào PriorityQueue có bắt buộc chỉ giữ một không?

**Lời giải:** Không. Cả hai có thể được giữ. PriorityQueue không dùng compare bằng 0 để loại thành viên như một tập sắp xếp.

### Bài 7: tìm theo probe cùng mức

Hàng đợi có alpha với urgency 3. Probe có id khác và sequence khác nhưng cũng urgency 3. Contains(probe) có true chỉ vì cùng mức không?

**Lời giải:** Không. Contains dùng equals. Record Job xét các thành phần, nên probe khác những giá trị ấy không được coi là cùng phần tử chỉ vì cùng urgency.

### Bài 8: hiển thị theo ưu tiên mà giữ nguồn

Bạn cần in toàn bộ Job theo executionOrder nhưng không làm rỗng scheduled. Có hai cách nào trong bài?

**Lời giải:** Chụp sang ArrayList rồi sort bằng comparator của scheduled; hoặc tạo PriorityQueue bản sao giữ cùng quy tắc rồi poll bản sao. Chỉ dựa vào iterator hoặc toString của nguồn không bảo đảm thứ tự ưu tiên.

### Bài 9: Job chưa có Comparable

Một record Job không triển khai Comparable được đưa vào PriorityQueue không truyền Comparator. Việc kiểu tổng quát biên dịch được có chứng minh hàng đợi dùng được không?

**Lời giải:** Không. Bạn vẫn phải cung cấp thứ tự tự nhiên hoặc Comparator hợp lệ. Chương trình minh họa ClassCastException khi thiếu cả con đường tự nhiên lẫn bộ so sánh riêng.

### Bài 10: null

Comparator được bọc bằng nullsFirst. Offer(null) vào PriorityQueue có được phép không?

**Lời giải:** Không. PriorityQueue cấm null theo hợp đồng lớp. Khả năng xử lý null của Comparator không thay đổi điều đó.

### Bài 11: thay đổi mức khẩn

Một đối tượng đang nằm trong hàng đợi bị gán urgency mới. Có thể giả định peek sau đó tự phản ánh vị trí mới không?

**Lời giải:** Không. Hàng đợi không tự theo dõi mọi phép gán bên trong. Dùng dữ liệu ổn định và một thao tác cập nhật ưu tiên được thiết kế rõ ràng.

### Bài 12: giá trị biên

Vì sao Comparator<Integer> viết bằng left - right có thể chọn sai đầu với số rất nhỏ và số rất lớn?

**Lời giải:** Phép trừ int có thể tràn, làm dấu kết quả sai. Integer.compare hoặc các hàm so sánh chuẩn diễn đạt thứ tự mà không cần lấy hiệu như vậy.

## 19. Tự kiểm tra khi viết quy tắc mới

Trước khi tạo PriorityQueue, hãy trả lời bằng lời phần tử nào phải ra trước và vì sao. Chuyển câu trả lời thành từng tiêu chí, xác định chiều của từng tiêu chí và cách giải quyết trường hợp bằng nhau.

Tiếp theo, kiểm tra bằng trình tự poll thực sự trên một mẫu có ý nghĩa. Nếu cần giữ nguồn, dùng ảnh chụp đã sắp hoặc bản sao có cùng quy tắc.

Cuối cùng, giữ riêng ba vấn đề: thứ tự ưu tiên, nhận diện phần tử theo equals và sự ổn định của dữ liệu trong thời gian lưu. Một Comparator đúng giúp chọn đầu; nó không tự cung cấp loại trùng, cập nhật ngầm hoặc FIFO cho các trường hợp mà quy tắc chưa phân biệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="priority-queue-003">offer, peek, poll và độ phức tạp</a>

<details>
<summary>Xem chi tiết</summary>

# offer, peek, poll và độ phức tạp của PriorityQueue

## 1. Hàng đợi ưu tiên chọn phần tử tiếp theo theo thứ tự nào?

Một hệ thống đang chờ các công việc có mức ưu tiên khác nhau. Nó cần thêm việc mới, xem việc nào sẽ được chọn tiếp và lấy việc ấy ra để xử lý.

Ba phương thức cốt lõi là:

| Phương thức | Việc thực hiện | Có thay đổi số phần tử không? |
|---|---|---|
| `offer(e)` | Thêm e vào hàng đợi ưu tiên | Tăng một khi thêm thành công |
| `peek()` | Xem phần tử đầu theo thứ tự ưu tiên | Không |
| `poll()` | Lấy và gỡ phần tử đầu theo thứ tự ưu tiên | Giảm một nếu có phần tử |

Khác hàng đợi FIFO, PriorityQueue không mặc định chọn phần tử đến trước. Nó chọn phần tử **nhỏ nhất theo thứ tự đang dùng**. Thứ tự có thể là tự nhiên của kiểu dữ liệu hoặc do Comparator xác định.

Ví dụ, với thứ tự số nguyên tăng dần, thêm `7, 1, 5` thì peek là 1. Nếu nghiệp vụ muốn số lớn được lấy trước, cần một comparator tương ứng; không có khái niệm “số ưu tiên càng lớn luôn được xử lý trước” tự động áp dụng.

[Tài liệu PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html) xác định phần tử đầu, chính sách null, thứ tự và các cận chi phí thông dụng. Bài này nối những quy tắc đó với cơ chế heap để hiểu vì sao offer, peek và poll có chi phí khác nhau.

## 2. Gốc heap giải thích khác biệt giữa ba thao tác

PriorityQueue dùng heap để duy trì phần tử nhỏ nhất theo comparator tại gốc. `offer()` thêm vào vị trí cuối rồi sift-up khi cần; `peek()` đọc ngay gốc; `poll()` gỡ gốc, chuyển phần tử cuối lên rồi sift-down. Vì các đường sửa heap chỉ dài theo chiều cao cây, chúng không phải một lần sắp xếp toàn bộ n phần tử.

Min-heap có thể biểu diễn bằng `[1, 3, 2, 8, 5, 7, 4]`: các cạnh cha–con hợp lệ dù mảng không tăng dần. Mô hình này không ấn định thứ tự iterator hay bố trí nội bộ của JDK. Bài về cấu trúc heap trình bày từng bước sift-up/sift-down và chỉ số cha–con.
## 3. offer: thêm phần tử rồi khôi phục quan hệ heap

Lời gọi:

```java
// Trích đoạn; cần import java.util.PriorityQueue.
PriorityQueue<Integer> values = new PriorityQueue<>();
boolean accepted = values.offer(7);
```

thêm một lần xuất hiện của giá trị 7. Với PriorityQueue chuẩn, khi thao tác hoàn tất thành công, offer trả true.

### 3.1. Thêm vào cuối vùng dữ liệu

Trong mô hình heap dạng mảng, phần tử mới được đặt ở vị trí kế tiếp sau các phần tử hiện có. Vị trí ấy giữ hình dạng cây nhị phân gần đầy (complete binary tree): các tầng trước tầng cuối đều đầy, còn tầng cuối được lấp từ trái sang phải.

Sau đó có thể cần đưa phần tử đi lên để khôi phục quan hệ cha–con, thường gọi là sift-up.

Ví dụ thêm 0 vào heap minh họa:

```text
trước thêm:
[1, 3, 2, 8, 5, 7, 4]

đặt 0 vào vị trí mới:
[1, 3, 2, 8, 5, 7, 4, 0]

0 nhỏ hơn các cha trên đường đi:
0 vượt 8, rồi 3, rồi 1

một kết quả heap hợp lệ:
[0, 1, 2, 3, 5, 7, 4, 8]
```

Thuật toán không sắp xếp lại mọi cặp phần tử. Nó xử lý một đường từ vị trí mới về phía gốc, có số mức theo logarit của kích thước heap.

### 3.2. Phần tử trùng vẫn được thêm

PriorityQueue không phải Set. Thêm 1 hai lần có thể thành công cả hai lần và làm size tăng hai.

So sánh bằng 0 cũng không có nghĩa loại bỏ một phần tử tương đương như TreeSet. Heap có thể giữ nhiều phần tử cùng ưu tiên.

Nếu cần tránh trùng theo một khóa nghiệp vụ, đó là một yêu cầu bổ sung. Không đọc boolean true của offer thành “đã kiểm tra phần tử này chưa tồn tại”.

### 3.3. Offer không có nghĩa mọi lỗi đều thành false

PriorityQueue không nhận phần tử null, kể cả khi comparator nào đó có khả năng so sánh null. Offer null ném NullPointerException.

Phần tử không so sánh được theo thứ tự yêu cầu có thể gây ClassCastException. Một lớp chưa có Comparable cần comparator phù hợp nếu dùng làm phần tử.

PriorityQueue tự tăng sức chứa, nên không dùng false như một tín hiệu “đã chạm giới hạn số việc cấu hình” cho lớp này. Sức chứa ban đầu không phải giới hạn nghiệp vụ và không tạo cơ chế chờ khi đầy.

## 4. peek: đọc phần tử đầu mà không tiêu thụ

Peek trả phần tử ở đầu theo thứ tự ưu tiên nhưng không gỡ nó.

```text
heap có: 1, 3, 2, 8, 5, 7, 4 theo một bố trí hợp lệ
peek() -> 1
size không đổi
peek() lần nữa, nếu không có thay đổi -> vẫn 1
```

Trong triển khai dựa trên mảng của OpenJDK 17, peek đọc vị trí gốc. Không cần sift-up, sift-down hoặc gọi comparator để tìm lại phần tử nhỏ nhất.

### 4.1. Rỗng thì trả null

PriorityQueue cấm phần tử null, nên null trả về từ peek có thể được dùng để biểu đạt không có phần tử.

Nếu muốn việc thiếu phần tử là một lỗi rõ, phương thức element của Queue cũng chỉ xem đầu nhưng ném NoSuchElementException khi rỗng.

Đừng tự động mở hộp trước khi xét trường hợp rỗng:

```java
// Trích đoạn dễ lỗi: poll hoặc peek có thể trả null.
int next = values.peek();
```

Khi rỗng, phép mở hộp null thành int gây NullPointerException ở mã gọi. Có thể giữ Integer và kiểm tra null, hoặc dùng phương thức có chính sách lỗi phù hợp.

### 4.2. Peek không phải bước đã xử lý xong việc

Nếu vòng lặp cứ peek rồi xử lý mà không gỡ phần tử hoặc thay đổi điều kiện dừng, nó có thể xử lý mãi cùng một phần tử.

Nếu ý định là lấy việc tiếp theo để tiêu thụ, poll biểu đạt đúng thao tác hơn.

Tương tự, peek rồi poll là hai lời gọi. Trong mã có cập nhật xen vào giữa, không mặc định kết quả poll luôn là đúng đối tượng vừa xem. Hãy xử lý chính giá trị được lời gọi lấy phần tử trả về, với cơ chế phối hợp phù hợp nếu chia sẻ nhiều luồng.

## 5. poll: gỡ gốc rồi sửa đường đi xuống

Poll vừa trả về vừa gỡ phần tử đầu. Khi rỗng, nó trả null.

Trong mô hình heap mảng, nếu còn nhiều phần tử:

1. Giữ lại giá trị gốc để trả về.
2. Lấy phần tử cuối vùng dữ liệu làm ứng viên lấp gốc.
3. Giảm số phần tử và bỏ tham chiếu ở ô cuối cũ.
4. Đưa ứng viên xuống theo cây để khôi phục quan hệ heap.

Bước đưa xuống thường gọi là sift-down.

### 5.1. Phải chọn con phù hợp

Với min-heap, nếu hai con tồn tại thì cần xem con nào nhỏ hơn. Nếu ứng viên lớn hơn con nhỏ hơn, đưa con ấy lên và tiếp tục xử lý xuống dưới.

Chỉ so sánh với con trái mà bỏ qua con phải có thể phá quan hệ heap.

Từ heap minh họa ban đầu, poll 1 và dùng giá trị cuối 4:

```text
trước poll:
[1, 3, 2, 8, 5, 7, 4]

lấp gốc bằng 4, bỏ ô cuối:
[4, 3, 2, 8, 5, 7]

đưa 2 lên vì 2 là con nhỏ hơn của gốc:
[2, 3, 4, 8, 5, 7]
```

Ứng viên 4 đã phù hợp với con 7 nên dừng. Đây là một quá trình sửa cục bộ, không phải sắp xếp lại toàn bộ dãy.

### 5.2. Rỗng hoặc chỉ có một phần tử

Nếu rỗng, không có gốc để gỡ và poll trả null.

Nếu chỉ có một phần tử, sau khi lấy nó thì hàng đợi trở thành rỗng; không cần một đường sift-down dài. Cận logarit mô tả quy mô tổng quát, không nói mọi lần gọi có đúng một số bước như nhau.

### 5.3. Poll khác remove(Object)

`remove()` không có đối số của Queue cũng lấy và gỡ đầu, nhưng ném ngoại lệ khi rỗng.

`remove(x)` có đối số là một yêu cầu khác: tìm một phần tử bằng x theo equals rồi gỡ một lần xuất hiện. Nó có thể phải quét hàng đợi và có chi phí tuyến tính.

Không suy từ “poll O(log n)” ra “mọi lời gọi có tên remove trên PriorityQueue đều O(log n)”.

## 6. Chương trình 1: offer, peek, poll và phần tử trùng

Lưu tệp `PriorityQueueApiDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityQueueApiDemo {
    public static void main(String[] args) {
        PriorityQueue<Integer> values = new PriorityQueue<>();
        for (int value : new int[] {7, 1, 5, 1, 3}) {
            System.out.println("offer " + value + " = " + values.offer(value));
        }

        System.out.println("size after offers = " + values.size());
        System.out.println("peek one = " + values.peek());
        System.out.println("peek two = " + values.peek());
        System.out.println("size after peeks = " + values.size());
        System.out.println("poll first = " + values.poll());
        System.out.println("size after one poll = " + values.size());

        List<Integer> output = new ArrayList<>();
        Integer next;
        while ((next = values.poll()) != null) {
            output.add(next);
        }
        System.out.println("remaining poll order = " + output);
        System.out.println("empty = " + values.isEmpty());
        System.out.println("empty peek = " + values.peek());
        System.out.println("empty poll = " + values.poll());

        try {
            values.offer(null);
        } catch (NullPointerException ex) {
            System.out.println("offer null = " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 PriorityQueueApiDemo.java
java PriorityQueueApiDemo
```

Kết quả của lần chạy kiểm chứng:

```text
offer 7 = true
offer 1 = true
offer 5 = true
offer 1 = true
offer 3 = true
size after offers = 5
peek one = 1
peek two = 1
size after peeks = 5
poll first = 1
size after one poll = 4
remaining poll order = [1, 3, 5, 7]
empty = true
empty peek = null
empty poll = null
offer null = NullPointerException
```

Các chương trình trong bài dùng ngôn ngữ và API có ở Java 17 trở lên, được biên dịch với `--release 17` và chạy kiểm chứng bằng Azul OpenJDK 24.0.2.

Có năm lần offer thành công, kể cả giá trị 1 xuất hiện hai lần. Size bằng 5 xác nhận đây là hàng đợi lưu các lần xuất hiện, không phải tập hợp loại trùng.

Hai lần peek đều trả 1 và không làm giảm size. Poll đầu tiên gỡ một giá trị 1; lần poll tiếp theo vẫn còn một giá trị 1 khác.

Khi lấy hết, peek và poll cùng trả null. Nhưng lý do không phải chúng làm cùng việc: trên một hàng đợi không rỗng, peek chỉ xem còn poll làm thay đổi dữ liệu.

Mã thu kết quả từ poll vào List để bảo vệ thứ tự. Nó không dựa vào thứ tự iterator của PriorityQueue.

## 7. Độ phức tạp: tách heap, mảng và chi phí so sánh

Javadoc PriorityQueue công bố O(log n) cho các thao tác thêm/lấy cơ bản, O(1) cho peek/element/size, và O(n) cho contains hoặc remove theo đối tượng.

Để hiểu đầy đủ một lần gọi thực tế, cần tách ba loại công việc:

```text
quản lý vùng lưu trữ
    +
sửa quan hệ heap
    +
chi phí mỗi lần so sánh
```

### 7.1. Chi phí sửa heap

Với một phép so sánh có chi phí hằng số:

| Thao tác | Công việc chính | Chi phí phần heap |
|---|---|---|
| offer | Đặt phần tử rồi sift-up | O(log n) |
| peek | Đọc gốc | O(1) |
| poll | Gỡ gốc, lấp bằng phần tử cuối, sift-down | O(log n) |
| size | Đọc số lượng đang giữ | O(1) |

Heap dạng cây nhị phân gần đầy có chiều cao O(log n). Sift-up đi một đường lên; sift-down đi một đường xuống. Mỗi mức dùng số phép so sánh có cận hằng số.

Peek rẻ vì công việc duy trì phần tử đầu đã được trả trong những lần cập nhật trước đó.

### 7.2. Một lần offer cần tăng mảng có thể O(n)

PriorityQueue có mảng nền tự tăng. [Mã nguồn PriorityQueue OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/PriorityQueue.java) cho thấy offer kiểm tra chỗ, tăng mảng khi cần rồi thực hiện sift-up; tăng mảng dùng sao chép các tham chiếu sang mảng mới.

Vì thế cần phân biệt:

- Cận O(log n) cho phần điều chỉnh heap.
- Một lần offer đúng lúc tăng mảng có thêm công việc O(n).
- Trên chuỗi thêm với cơ chế tăng theo tỷ lệ, chi phí tăng mảng được phân bổ khấu hao; phần sửa heap vẫn cho cận tổng quát O(log n) khấu hao mỗi lần thêm.

Đọc dòng độ phức tạp trong Javadoc không nên biến thành kết luận “mọi offer riêng lẻ đều có độ trễ không quá một số bước logarit, kể cả cấp phát”.

Sức chứa ban đầu phù hợp có thể giảm các lần tăng ở giai đoạn đầu, nhưng không thay cận của sift-up và không đặt giới hạn tối đa số phần tử.

### 7.3. Comparator có thể không rẻ

Nếu mỗi phép so sánh tốn C, phần sửa heap có thể được biểu đạt như O(C * log n).

Một comparator xét nhiều trường hoặc chuỗi dài có thể tốn hơn so sánh int. Không đặt truy vấn bên ngoài hoặc tác dụng phụ làm thay đổi dữ liệu ưu tiên vào comparator.

Peek trong mô hình triển khai này không phải trả lại chi phí comparator ở mỗi lần xem gốc; nhưng thay đổi thuộc tính mà comparator sử dụng vẫn có thể làm cấu trúc mất đúng đắn.

### 7.4. Lấy cả n phần tử không phải O(n)

Nếu cần tiêu thụ toàn bộ bằng poll, tổng chi phí tổng quát là O(n log n), vì mỗi lần còn phải duy trì heap cho những phần tử tiếp theo.

Duyệt iterator có thể đi qua n phần tử nhưng không bảo đảm thứ tự ưu tiên. Không so sánh hai cách như thể chúng trả cùng một loại kết quả.

Tương tự, thêm n phần tử bằng n lần offer có cận tổng quát O(n log n) cho sửa heap. Đừng gộp mọi cách khởi tạo từ một collection vào cùng lập luận từng-offer: triển khai có thể dùng một thuật toán xây heap theo lô riêng.

## 8. Chương trình 2: đếm so sánh của từng thao tác

Chương trình sau dùng comparator đếm số lần gọi, nhưng kết quả so sánh vẫn luôn là Integer.compare. Nó chuẩn bị đủ chỗ cho phần tử thêm tiếp để quan sát riêng việc sửa heap.

Lưu tệp `PriorityQueueComparisonCostDemo.java`:

```java
import java.util.Comparator;
import java.util.PriorityQueue;

public class PriorityQueueComparisonCostDemo {
    static final class CountingComparator implements Comparator<Integer> {
        private int calls;

        @Override
        public int compare(Integer left, Integer right) {
            calls++;
            return Integer.compare(left, right);
        }

        void reset() {
            calls = 0;
        }

        int calls() {
            return calls;
        }
    }

    public static void main(String[] args) {
        for (int n : new int[] {7, 31, 127, 511}) {
            CountingComparator order = new CountingComparator();
            PriorityQueue<Integer> values =
                    new PriorityQueue<>(n + 1, order);

            for (int value = 0; value < n; value++) {
                values.offer(value);
            }

            order.reset();
            values.offer(-1);
            int offerComparisons = order.calls();

            order.reset();
            Integer head = values.peek();
            int peekComparisons = order.calls();

            order.reset();
            Integer removed = values.poll();
            int pollComparisons = order.calls();

            System.out.println("n=" + n
                    + ", offerComparisons=" + offerComparisons
                    + ", peekComparisons=" + peekComparisons
                    + ", pollComparisons=" + pollComparisons
                    + ", peek=" + head
                    + ", poll=" + removed
                    + ", next=" + values.peek());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 PriorityQueueComparisonCostDemo.java
java PriorityQueueComparisonCostDemo
```

Kết quả của lần chạy kiểm chứng:

```text
n=7, offerComparisons=3, peekComparisons=0, pollComparisons=4, peek=-1, poll=-1, next=0
n=31, offerComparisons=5, peekComparisons=0, pollComparisons=8, peek=-1, poll=-1, next=0
n=127, offerComparisons=7, peekComparisons=0, pollComparisons=12, peek=-1, poll=-1, next=0
n=511, offerComparisons=9, peekComparisons=0, pollComparisons=16, peek=-1, poll=-1, next=0
```

### 8.1. Vì sao offer dùng nhiều so sánh hơn khi n lớn?

Dữ liệu ban đầu là các số không âm. Offer -1 buộc phần tử mới đi lên về gốc. Khi heap cao hơn, đường đi có thêm mức.

Ở các kích thước đã chọn, lần chạy cho số so sánh offer là 3, 5, 7, 9. Đây là một quan sát về độ dài đường đi, không phải công thức số lần gọi comparator mà API cam kết.

Nếu offer một phần tử đã phù hợp với cha gần vị trí cuối, nó có thể dừng sớm. O(log n) không nói mọi đầu vào đều đi hết chiều cao heap.

### 8.2. Peek không gọi comparator trong ví dụ

Bộ đếm được reset ngay trước peek và kết quả là 0. Phần tử -1 đã ở gốc nhờ offer trước đó, nên peek chỉ đọc nó.

Không suy ra rằng dùng một comparator rất chậm là không quan trọng: offer và poll vẫn cần comparator để tổ chức lại dữ liệu.

### 8.3. Poll sửa đường đi xuống

Poll lấy -1 rồi phải khôi phục đầu tiếp theo là 0. Ở mỗi mức có thể cần so sánh hai con và so sánh ứng viên với con được chọn.

Vì vậy, không nên so số lần gọi của poll với offer rồi mong chúng luôn bằng nhau. Cả hai có cận theo chiều cao heap nhưng làm công việc khác nhau tại mỗi mức.

### 8.4. Đây không phải phép đo thời gian hoặc cấp phát

Constructor nhận n + 1, đủ chỗ cho n phần tử ban đầu và một offer -1. Bộ đếm không đo việc cấp phát, chuyển mảng hoặc số nanosecond.

Nếu một lần offer khác phải tăng mảng, comparator đếm được vẫn không phản ánh toàn bộ công việc ấy. Đây là lý do cần tách phép đếm so sánh khỏi tổng chi phí một lời gọi.

Các con số output đã được kiểm chứng trên môi trường nêu ở chương trình 1. Không đóng đinh chúng thành kiểm tra tương thích giữa các phiên bản JDK.

## 9. Quy tắc so sánh quyết định đầu hàng đợi

Với `Integer` theo thứ tự tự nhiên, `peek()` là số nhỏ nhất hiện tại; dùng `Comparator.reverseOrder()` thì đầu là số lớn nhất theo giá trị số. Heap vẫn chọn phần tử đứng trước nhất theo quy tắc mới.

Nếu hai phần tử so bằng 0, Queue vẫn giữ cả hai và không bảo đảm FIFO giữa chúng. Khi nghiệp vụ cần xử lý theo thứ tự đến trong nhóm đồng hạng, comparator phải ghép thêm một khóa tiếp nhận bất biến. `contains`/`remove(Object)` tìm theo `equals`; đổi trực tiếp trường quyết định ưu tiên không tự làm heap sắp lại. Đây là các điều kiện áp dụng khi diễn giải kết quả của `offer`, `peek` và `poll`.
## 10. Tình huống áp dụng ba thao tác với khóa ưu tiên ghép

Xét các Job `(priority, arrival, name)`: `(5,0,routine)`, `(1,1,urgent-A)`, `(1,2,urgent-B)`, `(3,3,medium)`. Comparator so `priority` tăng rồi `arrival` tăng. Sau khi `offer()` cả bốn, `peek()` trả urgent-A mà không giảm kích thước 4.

Lấy lần lượt bằng `poll()` trả `urgent-A → urgent-B → medium → routine`. Hai urgent cùng priority nhưng được phân biệt bởi arrival; routine đến trước vẫn không được lấy trước do ưu tiên thấp hơn. Nếu chỉ muốn xem trước danh sách, poll một bản sao `new PriorityQueue<>(jobs)` thay vì tiêu thụ hàng đợi gốc.

Ví dụ này bổ sung tình huống cho hai chương trình chạy ở mục 6 và 8. Quy tắc ghép comparator, phần tử đồng hạng và cách xem kết quả theo thứ tự được triển khai đầy đủ trong các bài tương ứng.
## 11. Các mẫu sử dụng và tổng chi phí

### 11.1. Thêm khi có việc mới, lấy khi cần xử lý

Một mẫu một luồng:

```java
// Trích đoạn; Job và process được định nghĩa ở chương trình bao quanh.
PriorityQueue<Job> jobs = new PriorityQueue<>(order);
jobs.offer(newJob);

Job next = jobs.poll();
if (next != null) {
    process(next);
}
```

Giá trị next là công việc đã được gỡ và thực sự dành cho lần xử lý ấy. Không peek một công việc rồi bỏ qua giá trị được poll trả về.

Nếu việc xử lý có thể thất bại, quyền sở hữu, thử lại hoặc khôi phục công việc là chính sách của ứng dụng. Poll chỉ thay đổi collection; nó không tự xác nhận hoàn tất nghiệp vụ.

### 11.2. Chỉ cần biết việc tiếp theo

Dùng peek khi quyết định tiếp theo không muốn lấy việc ra ngay, ví dụ kiểm tra thông tin của đầu hiện tại.

Nhưng không coi một tham chiếu đã peek là một đặt chỗ. Nếu có các cập nhật khác sau đó, đầu có thể thay đổi. Với nhiều luồng, PriorityQueue thông thường không tự an toàn cho truy cập có thay đổi đồng thời.

Một mô hình một luồng sở hữu hàng đợi có giả định khác một hệ thống chia sẻ hàng đợi. Cần giữ điều kiện sử dụng rõ ràng.

### 11.3. Cần toàn bộ thứ tự xử lý

Hai hướng có ý nghĩa khác nhau:

- Poll trên nguồn: nhận thứ tự và tiêu thụ nguồn.
- Tạo bản sao rồi poll: nhận thứ tự nhưng giữ container gốc.
- Sao dữ liệu sang List rồi sort bằng comparator phù hợp: nhận một dãy sắp xếp riêng.

Không dùng for-each trên PriorityQueue như một cách tối ưu thay cho các hướng trên nếu vẫn cần thứ tự ưu tiên.

Tạo và giữ bản sao có chi phí bộ nhớ cho các tham chiếu. Poll toàn bộ có tổng công việc theo heap; sort cũng có chi phí sắp xếp. Không có phương pháp xem tất cả theo thứ tự mà miễn toàn bộ công việc tổ chức dữ liệu.

### 11.4. Nhiều peek không làm giảm size

Nếu ứng dụng có nhiều lần xem đầu xen giữa các lần cập nhật, ưu điểm O(1) của peek có giá trị: heap duy trì đầu khi thêm/lấy, còn việc xem lại không phải quét.

Nhưng một vòng xử lý cần tiến triển. Nếu điều kiện dừng là rỗng mà mã chỉ peek, size không giảm và vòng có thể không bao giờ kết thúc.

## 12. Những độ phức tạp hay bị gộp nhầm

| Nhu cầu hoặc lời gọi | Chi phí/hành vi cần nhớ |
|---|---|
| Xem đầu bằng peek | O(1), không gỡ |
| Lấy đầu bằng poll | O(log n) trong trường hợp tổng quát phần heap |
| Lấy đầu bằng remove() | Cùng vai trò lấy đầu, khác chính sách rỗng |
| Gỡ một đối tượng bằng remove(x) | Có thể O(n) vì phải tìm |
| Kiểm tra contains(x) | O(n) tổng quát, không được lợi như tra cứu trên cây tìm kiếm |
| Thêm bằng offer | Sift-up O(log n); lưu ý tăng mảng |
| Duyệt iterator | Đi qua phần tử nhưng không hứa thứ tự ưu tiên |
| Poll hết n phần tử | O(n log n) tổng quát cho việc tiêu thụ theo ưu tiên |
| Đổi ưu tiên bằng gỡ đối tượng rồi thêm lại | Tính cả bước tìm tuyến tính nếu dùng remove(Object) |

Heap không phải cây tìm kiếm nhị phân. Quan hệ cha–con chỉ đủ để định vị phần tử ưu tiên nhất, không đủ để chọn duy nhất một nhánh khi tìm một giá trị tùy ý.

Đó là lý do contains không tự trở thành O(log n) chỉ vì hình heap có chiều cao logarit.

Khi n thay đổi trong một chuỗi thao tác, phân tích tổng bằng các kích thước tương ứng. Đừng lấy một size ở cuối rồi coi mọi lời gọi trong cả quá trình đã có đúng cùng chi phí.

## 13. Các lỗi sử dụng phổ biến

| Cách hiểu hoặc cách làm | Hậu quả | Cách sửa |
|---|---|---|
| Xem offer như thêm vào một hàng đợi FIFO | Công việc có thể được chọn theo ưu tiên khác thứ tự đến | Xác định comparator và hợp đồng lựa chọn |
| Nghĩ peek đã lấy phần tử | Có thể xử lý lặp cùng đầu | Dùng poll để tiêu thụ |
| Gán kết quả poll vào int ngay | Rỗng có thể gây lỗi mở hộp null | Giữ kiểu tham chiếu và xử lý vắng mặt |
| Nghĩ offer false khi phần tử trùng | PriorityQueue giữ nhiều lần xuất hiện | Quản lý tránh trùng riêng nếu cần |
| Trông chờ FIFO khi cùng ưu tiên | API không bảo đảm ổn định trong ties | Thêm tiêu chí phụ có ý nghĩa |
| Dùng iterator để in thứ tự ưu tiên | Heap không là dãy sắp xếp toàn bộ | Poll, bản sao hoặc sort phù hợp |
| Sửa trực tiếp trường priority đang ở trong queue | Heap không tự cập nhật vị trí | Dùng dữ liệu ổn định và quy trình cập nhật |
| Nghĩ mọi remove đều logarit | remove(Object) cần tìm theo giá trị | Phân biệt chữ ký phương thức |
| Coi mọi offer riêng lẻ đều logarit kể cả cấp phát | Bỏ qua tăng mảng | Tách sửa heap và lưu trữ; hiểu chi phí khấu hao |
| Đặt initial capacity làm giới hạn công việc | Queue vẫn tăng khi cần | Thiết kế giới hạn thực sự ở nơi phù hợp |
| Dùng comparator bằng phép trừ int | Có thể tràn và làm sai thứ tự | Dùng Integer.compare hoặc comparingInt |
| Chia sẻ PriorityQueue mà không phối hợp | Không có hợp đồng an toàn cập nhật đồng thời | Chọn mô hình đồng thời phù hợp |

Ưu tiên thay đổi theo thời gian cũng cần được thiết kế rõ. Một comparator đọc đồng hồ hoặc cấu hình ngoài biến đổi có thể làm quan hệ thứ tự đổi mà heap chưa được cập nhật.

Không cần mọi hệ thống đều có một cơ chế lịch phức tạp, nhưng cần biết thời điểm nào dữ liệu so sánh được giữ ổn định và lúc nào hàng đợi được tổ chức lại.

## 14. Kiểm chứng đúng API và đúng giả định

Một bộ kiểm tra cho mã dùng PriorityQueue nên dựa vào kết quả quan sát được:

1. Offer các giá trị có thứ tự đầu vào khác thứ tự ưu tiên.
2. Peek nhiều lần, kiểm tra size không đổi.
3. Poll một lần, kiểm tra size giảm và đầu tiếp theo đúng.
4. Poll hết, kiểm tra đầy đủ số lượng và thứ tự.
5. Thêm phần tử trùng, xác nhận mọi lần xuất hiện cần giữ còn tồn tại.
6. Kiểm tra rỗng và null đúng chính sách.
7. Kiểm tra comparator đảo nếu nghiệp vụ chọn số lớn trước.
8. Kiểm tra ties theo tiêu chí phụ nếu yêu cầu ổn định.
9. Kiểm tra thay đổi hoặc thay thế công việc theo quy trình cập nhật đã thiết kế.

Không viết kiểm tra rằng iterator phải cho cùng dãy tăng dần với poll. Đó không phải hợp đồng.

Cũng không buộc số lần comparator phải đúng bằng output của chương trình đếm trên mọi phiên bản JDK. Ví dụ đếm giúp giải thích công việc, còn tính tương thích của ứng dụng nên gắn với hành vi cần dùng.

Nếu chỉ thử size hoặc checksum, có thể bỏ qua đảo thứ tự. Nếu chỉ thử bằng một Set, còn có thể bỏ qua cả số lần xuất hiện. Hãy đối chiếu dãy kết quả khi thứ tự là yêu cầu cốt lõi.

Với đối tượng cùng ưu tiên, chỉ khẳng định thứ tự cụ thể khi comparator đã phân biệt đúng theo yêu cầu. Nếu không có tiêu chí phụ, kiểm tra phải chấp nhận các thứ tự hợp lệ trong nhóm tương đương.

## 15. Bài tập áp dụng

### Bài 1: phân biệt xem và lấy

Offer `4, 2, 7`, gọi peek hai lần rồi poll một lần. Size thay đổi thế nào?

**Gợi ý trả lời:** sau offer size là 3; hai peek không đổi size; poll thành công làm size còn 2. Theo natural order, các lần xem và lần lấy đầu đều hướng tới giá trị 2 khi không có thay đổi xen vào.

### Bài 2: phần tử trùng

Offer 1 hai lần. Lần thứ hai có trả false để báo đã tồn tại không?

**Gợi ý trả lời:** không. PriorityQueue không loại trùng; cả hai lần có thể thành công và tạo hai lần xuất hiện.

### Bài 3: đầu theo thứ tự đảo

PriorityQueue dùng reverseOrder, chứa `3, 9, 5`. Peek và poll đầu tiên là gì?

**Gợi ý trả lời:** đều là 9 nếu không có cập nhật xen vào; peek không gỡ, poll gỡ.

### Bài 4: heap cao logarit nhưng contains tuyến tính

Tại sao không thể kết luận contains O(log n) từ chiều cao heap?

**Gợi ý trả lời:** heap không có quy tắc mọi giá trị nhỏ nằm một phía và lớn nằm phía kia như cây tìm kiếm. Với một giá trị tùy ý, có thể phải xem qua nhiều vị trí.

### Bài 5: một offer chậm vì tăng mảng

Điều đó có mâu thuẫn với cách học offer O(log n) không?

**Gợi ý trả lời:** cần tách tầng chi phí. Sift-up logarit, còn một lần tăng mảng có thể tuyến tính; trên chuỗi thao tác có phân tích khấu hao cho lưu trữ.

### Bài 6: cùng ưu tiên, cần đến trước xử lý trước

Hai công việc có cùng priority. Chỉ comparator theo priority có đủ bảo đảm FIFO trong nhóm không?

**Gợi ý trả lời:** không. Cần một tiêu chí phụ biểu đạt thứ tự đến, chẳng hạn arrival nhất quán và phân biệt đúng các công việc.

### Bài 7: xem toàn bộ nhưng giữ nguồn

Có thể poll nguồn để lấy dãy rồi vẫn coi đó là thao tác chỉ xem không?

**Gợi ý trả lời:** không. Poll tiêu thụ nguồn. Có thể poll một bản sao phù hợp hoặc tạo dãy riêng và sắp xếp bằng comparator đúng.

### Bài 8: đổi priority trực tiếp

Một công việc đang nằm trong PriorityQueue được setter đổi mức ưu tiên. Queue có tự sift lại không?

**Gợi ý trả lời:** không. Cần dữ liệu so sánh ổn định hoặc một quy trình cập nhật có kiểm soát; khi dùng remove(Object) để gỡ, phải tính cả chi phí tìm tuyến tính.

Khi chọn giữa offer, peek và poll, hãy xác định rõ đang thêm, chỉ xem hay tiêu thụ. Khi đọc độ phức tạp, hãy nêu rõ phần heap, cấp phát mảng và chi phí comparator đang được tính thế nào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="priority-queue-004">remove(Object), contains và chi phí tìm phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# remove(Object), contains và chi phí tìm phần tử

## 1. Chọn đầu ưu tiên nhanh không đồng nghĩa tìm mọi phần tử đều nhanh

Một PriorityQueue có thể nhanh chóng cho biết công việc nào đứng đầu theo mức ưu tiên. Nhưng nếu câu hỏi là “công việc beta có còn ở trong hàng đợi không?” hoặc “hãy xóa đúng công việc beta”, đó là một loại thao tác khác.

Heap ưu tiên được tổ chức để phục vụ việc chọn đầu. Nó không phải bảng tra cứu theo mọi thuộc tính của công việc và cũng không phải cây tìm kiếm có thể rẽ trái/phải theo một khóa bất kỳ.

Vì vậy, cần tách:

- `peek()`: xem đầu ưu tiên.
- `poll()` hoặc `remove()` không đối số: lấy ra đầu ưu tiên.
- `contains(Object)`: tìm xem có phần tử bằng giá trị được hỏi hay không.
- `remove(Object)`: tìm và xóa một phần tử bằng giá trị được hỏi.

Trong PriorityQueue chuẩn, contains và remove có đối số có chi phí tìm tuyến tính theo số thành viên. Nếu dùng chúng thường xuyên, phần tìm này có thể chiếm nhiều công việc hơn phần duy trì heap.

Bài học giải thích nhận diện bằng equals, bản trùng, các chữ ký dễ nhầm và cách đánh giá chi phí bằng một phép kiểm chứng có đếm thao tác. Chương trình đầy đủ dùng API có trong Java 17.

Bạn cần biết biến, đối tượng, phương thức và vòng lặp. Những quan hệ giữa equals, Comparator và heap sẽ được giới thiệu đúng phạm vi cần dùng.

## 2. Ba câu hỏi khác nhau về một phần tử

Xét các công việc có hai thuộc tính:

~~~text
id       : tên nhận diện trong ví dụ
priority : số ưu tiên; số nhỏ được chọn trước
~~~

Chương trình dùng `record Task(String id, int priority)`. Với record này, phép bằng nhau mặc định xét các thành phần, không chỉ id.

Khi có hai Task, hãy phân biệt:

| Câu hỏi | Cơ chế |
| --- | --- |
| Hai biến có trỏ tới chính cùng đối tượng không? | `==` |
| Hai giá trị Task có bằng nhau không? | `equals` |
| Hai Task có cùng mức trong quy tắc ưu tiên không? | `Comparator.compare(a, b) == 0` |

Nếu Comparator chỉ đọc priority, `Task("alpha", 2)` và `Task("beta", 2)` cho kết quả so sánh 0 nhưng không bằng nhau theo equals. Chúng cùng mức ưu tiên, không phải cùng một giá trị Task.

Ngược lại, `new Task("beta", 2)` là một đối tượng mới nhưng bằng một Task beta/2 đã lưu. Contains có thể tìm được nó dù hai tham chiếu khác nhau.

Không suy “đã có cùng mức ưu tiên” thành “đã có đúng công việc cần tìm”. Cũng không yêu cầu phải giữ chính tham chiếu ban đầu mới có thể tìm hoặc xóa theo giá trị, nếu equals của kiểu đã thể hiện quan hệ phù hợp.

## 3. contains dùng equals, không dùng nhóm ưu tiên

Hợp đồng của `PriorityQueue.contains(o)` kiểm tra có thành viên e mà `o.equals(e)` đúng hay không. Nó không hỏi có phần tử nào mà Comparator so sánh bằng 0 với o.

Ví dụ hàng đợi có beta/2:

~~~text
contains(new Task("beta", 2))   → true
contains(new Task("unknown", 2)) → false
~~~

Giá trị dò unknown/2 cùng priority 2, nhưng id khác nên equals khác. Comparator chỉ xét priority không làm giá trị dò ấy trở thành beta.

Contains chỉ đọc. Nó không xóa thành viên, không đưa phần tử được tìm thấy lên đầu và không làm mới ưu tiên.

Quy tắc equals thuộc về kiểu phần tử. Nếu lớp không ghi đè equals và dùng hành vi nhận diện đối tượng mặc định, một đối tượng mới có các trường nhìn giống nhau chưa chắc được tìm thấy. Nếu kiểu tự định nghĩa equals theo mã, phép tìm sẽ theo quan hệ đó.

Với record Task của bài, hỏi bằng id giống nhưng priority khác cũng không phải hỏi cùng giá trị, vì cả hai thành phần tham gia equals. Nếu nghiệp vụ thật nhận diện công việc chỉ bằng id, cần một mô hình khóa hoặc phép bằng nhau phù hợp, không được suy từ tên trường id. [Java 17: PriorityQueue.contains](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html#contains(java.lang.Object)).

## 4. remove(Object) xóa một phần tử bằng nhau, không nhất thiết là đầu

`remove(o)` có đối số tìm một thành viên bằng o theo equals. Nếu có, nó xóa một lần xuất hiện và trả true. Nếu không có, nó trả false.

Ví dụ hàng đợi chứa:

~~~text
first/1
alpha/2
beta/2
later/9
~~~

Gọi `remove(new Task("beta", 2))` không có nghĩa “bỏ công việc ưu tiên cao nhất”. Nó tìm beta/2 và xóa giá trị ấy; first/1 vẫn có thể đứng đầu.

Một giá trị dò khác có cùng mức ưu tiên nhưng không bằng nhau không được dùng để xóa tùy ý một phần tử trong nhóm:

~~~text
remove(new Task("unknown", 2)) → false
~~~

Lời gọi này không tự chọn alpha/2 hoặc beta/2 để xóa thay.

Nếu có nhiều phần tử bằng nhau, remove(Object) chỉ xóa một lần xuất hiện. PriorityQueue cho phép bản trùng, kể cả việc đưa cùng một đối tượng vào nhiều lần. Không có cơ chế tự loại trùng khi thêm.

Đừng dựa vào remove(Object) để chọn “bản bằng nhau đến sớm nhất” hoặc “đúng tham chiếu này” khi có nhiều đại diện bằng nhau. Hợp đồng mô tả xóa một phần tử phù hợp equals, không cung cấp chính sách lịch sử cho các bản ấy. [Java 17: PriorityQueue.remove(Object)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html#remove(java.lang.Object)).

## 5. remove() và remove(Object) có mục đích, kết quả và chi phí khác nhau

| Chữ ký | Chọn phần tử bằng cách nào? | Kết quả | Khi không có đối tượng phù hợp |
| --- | --- | --- | --- |
| `remove()` | Chọn đầu theo ưu tiên | Phần tử bị lấy ra | Ném NoSuchElementException khi rỗng |
| `poll()` | Chọn đầu theo ưu tiên | Phần tử bị lấy ra | Trả null khi rỗng |
| `remove(Object o)` | Tìm theo equals với o | boolean | Trả false nếu không tìm thấy |
| `contains(Object o)` | Tìm theo equals với o | boolean | Trả false nếu không tìm thấy |

Điểm dễ gây nhầm với số nguyên:

~~~java
PriorityQueue<Integer> numbers =
        new PriorityQueue<>(List.of(5, 1, 3, 2, 4));

boolean removed = numbers.remove(3);
~~~

Đây là đoạn minh họa với các kiểu java.util tương ứng. PriorityQueue không có thao tác remove theo chỉ số. Giá trị int 3 được đóng gói để truyền vào phương thức nhận Object, và mã xóa một phần tử có giá trị 3.

Nó không xóa phần tử ở vị trí thứ ba của một danh sách. Kết quả là boolean, không phải phần tử bị lấy khỏi một chỉ số.

Sau đó `numbers.remove()` không đối số lại có nghĩa khác: lấy đầu nhỏ nhất hiện có. Luôn đọc cả dấu ngoặc và đối số khi đánh giá hành vi hoặc độ phức tạp.

## 6. Vì sao heap không đem lại tìm kiếm tùy ý O(log n)?

Một heap nhỏ nhất chỉ yêu cầu mỗi nút cha không đứng sau các con theo quy tắc ưu tiên. Nó không có quy tắc “mọi giá trị bên trái nhỏ hơn, mọi giá trị bên phải lớn hơn” như một cây tìm kiếm theo khóa.

Mô hình minh họa:

~~~text
        1
      /   \
     4     2
    / \   /
   8   7 3
~~~

Cả hai nhánh có thể chứa những phần tử phù hợp với một phép tìm theo giá trị. So sánh một khóa tìm với một nút không cung cấp cùng quy tắc rẽ nhánh như cây tìm kiếm.

Hơn nữa, contains phải tuân theo equals, trong khi heap được tổ chức bằng Comparator hoặc compareTo. Hai quan hệ không nhất thiết xem cùng một nhóm đối tượng là tương đương.

Vì vậy, PriorityQueue không dùng một phép tìm nhị phân trên toàn bộ dãy lưu trữ để thực hiện contains. Dãy nhìn thấy qua iterator hoặc toArray cũng không được cam kết đã sắp xếp, nên không thể đưa nó thẳng vào thuật toán tìm nhị phân rồi coi kết quả là đúng.

Tổ chức để chọn đầu và tổ chức để tra cứu theo định danh là hai yêu cầu dữ liệu riêng. Một cấu trúc tối ưu cho yêu cầu thứ nhất không tự cung cấp chỉ mục cho yêu cầu thứ hai.

## 7. Quá trình tìm rồi xóa có hai phần chi phí

Trong OpenJDK 17, contains và remove(Object) cùng dùng một bước tìm chỉ số bằng cách kiểm tra equals với các thành viên. Không tìm thấy thì kết thúc; tìm thấy để xóa thì chuyển sang bước sửa heap. [Mã nguồn PriorityQueue, OpenJDK 17](https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/java.base/share/classes/java/util/PriorityQueue.java).

Có thể đọc theo sơ đồ:

~~~text
contains(o)
  → tìm một thành viên bằng o
  → trả có / không

remove(o)
  → tìm một thành viên bằng o
  → nếu không có: trả false
  → nếu có: xóa ở vị trí đã tìm, khôi phục heap, trả true
~~~

Tìm theo giá trị có thể phải xét toàn bộ n thành viên. Sau khi biết chỗ cần xóa, phần tử cuối được dùng để lấp chỗ và quan hệ heap được khôi phục theo hướng cần thiết.

Ví dụ mô hình trước khi xóa 2:

~~~text
        1
      /   \
     2     3
    / \   / \
   4   5 6   7
~~~

Đưa 7 vào chỗ của 2 chưa đủ: 7 đang đứng trước các con 4, 5 sai quan hệ. Cần sửa vị trí để một heap hợp lệ lại được duy trì. Đây là giải thích cơ chế, không khẳng định bố trí nội bộ của mọi hàng đợi phải giống sơ đồ.

Không phải mỗi lần xóa đều sắp xếp lại toàn bộ một danh sách. Bước sửa heap khác với bước tìm thành viên, và chính bước tìm làm cho remove(Object) có chi phí tuyến tính trong trường hợp xấu.

## 8. Đọc đúng bảng độ phức tạp

Với n là số thành viên đang có, mô tả chi phí của PriorityQueue:

| Thao tác | Chi phí theo mô tả của lớp |
| --- | --- |
| `peek` / `element` / `size` | O(1) |
| `offer` / `add` | O(log n) |
| `poll` / `remove()` | O(log n) |
| `contains(Object)` | O(n) |
| `remove(Object)` | O(n) |

Các dòng O(n) không có nghĩa mọi lời gọi luôn phải đi hết n phần tử. Nếu tìm thấy ngay, phần tìm có thể kết thúc sớm. Nếu phần tử vắng mặt hoặc nằm muộn trong vùng tìm, số lần kiểm tra có thể tăng tới n.

Đối với remove(Object), biết đúng vị trí ngay từ đầu vẫn còn bước sửa heap. Đếm chỉ một lần equals ở đầu không chứng minh toàn bộ phép xóa có thời gian hằng.

Trong bước tìm của cách triển khai được đối chiếu, n là size, không phải toàn bộ dung lượng mảng đã cấp phát. Không cần kiểm tra từng ô dự phòng không có thành viên.

Các ký hiệu trên thường tách khỏi độ phức tạp bên trong equals và Comparator. Nếu mỗi equals so sánh nhiều trường dài, chi phí thực tế của bước tìm còn gồm công việc đó. Với chi phí mỗi phép equals là C_eq và mỗi so sánh ưu tiên là C_cmp, có thể hình dung phần tìm/xóa gồm một chặn theo n·C_eq cộng với phần sửa heap theo log(n)·C_cmp.

Đây là cách phân tích số công việc, không phải cam kết một số mili giây cố định trên mọi máy. [Java 17: PriorityQueue, ghi chú chi phí thao tác](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

## 9. Chương trình đầy đủ dùng Java 17

Lưu thành `PriorityQueueSearchDemo.java`. Chương trình có ba nhóm kiểm chứng:

1. Các Task cùng ưu tiên nhưng khác equals, và hai lần xuất hiện của beta.
2. Remove có đối số trên PriorityQueue<Integer>, không phải xóa theo chỉ số.
3. Một kiểu Key đếm số lần equals để quan sát việc tìm.

Key có equals/hashCode theo id. Bộ đếm tĩnh chỉ phục vụ bài thử một luồng; kết quả bằng nhau vẫn được quyết định bằng id. Chương trình không dùng bộ đếm như một phần quy tắc nhận diện.

Các danh sách id được sắp để đối chiếu nội dung. Chúng không được trình bày như thứ tự iterator hoặc thứ tự lấy ra khi có các Task bằng ưu tiên.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityQueueSearchDemo {
    record Task(String id, int priority) {}

    static final class Key {
        static int equalityCalls;
        final int id;

        Key(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            equalityCalls++;
            return other instanceof Key key && id == key.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    static List<String> sortedIds(PriorityQueue<Task> queue) {
        return queue.stream().map(Task::id).sorted().toList();
    }

    static long countEqual(PriorityQueue<Task> queue, Task probe) {
        return queue.stream().filter(probe::equals).count();
    }

    static List<Integer> drainPriorities(PriorityQueue<Task> queue) {
        List<Integer> result = new ArrayList<>();
        Task task;
        while ((task = queue.poll()) != null) {
            result.add(task.priority());
        }
        return result;
    }

    public static void main(String[] args) {
        Comparator<Task> byPriority = Comparator.comparingInt(Task::priority);
        PriorityQueue<Task> tasks = new PriorityQueue<>(byPriority);
        Task beta = new Task("beta", 2);
        tasks.addAll(List.of(
                new Task("alpha", 2),
                beta,
                new Task("later", 9),
                new Task("first", 1),
                beta));
        System.out.println("initial ids=" + sortedIds(tasks));
        System.out.println("initial size=" + tasks.size());
        System.out.println("head priority=" + tasks.peek().priority());

        Task equalProbe = new Task("beta", 2);
        Task samePriority = new Task("unknown", 2);
        System.out.println("equal probe is same reference=" + (equalProbe == beta));
        System.out.println("equal probe equals beta=" + equalProbe.equals(beta));
        System.out.println("contains equal probe=" + tasks.contains(equalProbe));
        System.out.println("same-priority comparison=" + byPriority.compare(samePriority, beta));
        System.out.println("same-priority equals=" + samePriority.equals(beta));
        System.out.println("contains same-priority probe=" + tasks.contains(samePriority));
        System.out.println("remove same-priority probe=" + tasks.remove(samePriority));
        System.out.println("size after missing removal=" + tasks.size());

        System.out.println("beta count before=" + countEqual(tasks, equalProbe));
        System.out.println("remove one beta=" + tasks.remove(equalProbe));
        System.out.println("beta count after one=" + countEqual(tasks, equalProbe));
        System.out.println("size after one=" + tasks.size());
        System.out.println("head priority after one=" + tasks.peek().priority());
        System.out.println("ids after one=" + sortedIds(tasks));
        System.out.println("still contains beta=" + tasks.contains(equalProbe));
        System.out.println("remove second beta=" + tasks.remove(equalProbe));
        System.out.println("remove beta again=" + tasks.remove(equalProbe));
        System.out.println("ids after both=" + sortedIds(tasks));
        System.out.println("remaining priorities poll=" + drainPriorities(tasks));
        System.out.println("contains null=" + tasks.contains(null));
        System.out.println("remove null=" + tasks.remove(null));

        PriorityQueue<Integer> numbers =
                new PriorityQueue<>(List.of(5, 1, 3, 2, 4));
        boolean removedByValue = numbers.remove(3);
        System.out.println("integer remove(3)=" + removedByValue);
        List<Integer> snapshot = new ArrayList<>(numbers);
        snapshot.sort(Comparator.naturalOrder());
        System.out.println("numbers after value removal=" + snapshot);
        System.out.println("contains text 4=" + numbers.contains("4"));
        System.out.println("remove text 4=" + numbers.remove("4"));
        System.out.println("remove() head=" + numbers.remove());
        System.out.println("poll head=" + numbers.poll());

        for (int n : new int[] {8, 16, 32}) {
            PriorityQueue<Key> counted =
                    new PriorityQueue<>(Comparator.comparingInt(key -> key.id));
            for (int id = 0; id < n; id++) {
                counted.offer(new Key(id));
            }

            Key.equalityCalls = 0;
            boolean found = counted.contains(new Key(-1));
            System.out.println("n=" + n + " contains missing=" + found
                    + " equals calls=" + Key.equalityCalls);

            Key.equalityCalls = 0;
            boolean removedMissing = counted.remove(new Key(-1));
            System.out.println("n=" + n + " remove missing=" + removedMissing
                    + " equals calls=" + Key.equalityCalls);

            Key.equalityCalls = 0;
            boolean removedMinimum = counted.remove(new Key(0));
            System.out.println("n=" + n + " remove minimum by value=" + removedMinimum
                    + " equals calls=" + Key.equalityCalls);
            System.out.println("n=" + n + " next minimum=" + counted.peek().id);
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 PriorityQueueSearchDemo.java
java PriorityQueueSearchDemo
~~~

Dùng JDK 17 trở lên. `--release 17` kiểm tra mã và API theo Java 17; tên tệp khớp lớp public.

Kết quả chuẩn:

~~~text
initial ids=[alpha, beta, beta, first, later]
initial size=5
head priority=1
equal probe is same reference=false
equal probe equals beta=true
contains equal probe=true
same-priority comparison=0
same-priority equals=false
contains same-priority probe=false
remove same-priority probe=false
size after missing removal=5
beta count before=2
remove one beta=true
beta count after one=1
size after one=4
head priority after one=1
ids after one=[alpha, beta, first, later]
still contains beta=true
remove second beta=true
remove beta again=false
ids after both=[alpha, first, later]
remaining priorities poll=[1, 2, 9]
contains null=false
remove null=false
integer remove(3)=true
numbers after value removal=[1, 2, 4, 5]
contains text 4=false
remove text 4=false
remove() head=1
poll head=2
n=8 contains missing=false equals calls=8
n=8 remove missing=false equals calls=8
n=8 remove minimum by value=true equals calls=1
n=8 next minimum=1
n=16 contains missing=false equals calls=16
n=16 remove missing=false equals calls=16
n=16 remove minimum by value=true equals calls=1
n=16 next minimum=1
n=32 contains missing=false equals calls=32
n=32 remove missing=false equals calls=32
n=32 remove minimum by value=true equals calls=1
n=32 next minimum=1
~~~

## 10. Đọc kết quả tìm kiếm và nhận diện

### 10.1. Khác tham chiếu nhưng bằng giá trị vẫn tìm thấy

`equal probe is same reference=false` cho biết equalProbe không phải chính đối tượng beta ban đầu.

`equal probe equals beta=true` và `contains equal probe=true` cho thấy equals đủ để tìm thấy một thành viên. Không cần giữ nguyên tham chiếu đã đưa vào hàng đợi nếu kiểu đã có phép bằng nhau phù hợp.

### 10.2. Cùng mức ưu tiên chưa phải cùng phần tử

`same-priority comparison=0` nhưng `same-priority equals=false`. Cả contains và remove với giá trị dò unknown/2 đều trả false.

`size after missing removal=5` xác nhận không có thành viên nào bị xóa thay từ nhóm priority 2. Remove theo giá trị không lựa ngẫu nhiên một phần tử có mức bằng giá trị dò.

### 10.3. Contains không phải thao tác lấy

Đầu có priority 1 trước các phép tìm. Việc đọc contains hoặc thất bại khi xóa giá trị dò không chuyển beta lên đầu và không rút first/1 ra.

Khi cần xử lý phần tử ưu tiên tiếp theo, dùng poll hoặc remove không đối số. Dùng contains để kiểm tra một giá trị không thay thế thao tác lấy đầu.

## 11. Xóa một lần xuất hiện và khôi phục heap

Ban đầu beta xuất hiện hai lần. Chương trình kiểm tra:

~~~text
beta count before=2
remove one beta=true
beta count after one=1
size after one=4
~~~

Kết quả không phải remove “chưa xóa đủ” theo một lỗi triển khai. Hợp đồng chỉ yêu cầu loại một lần xuất hiện phù hợp.

`still contains beta=true` sau lần xóa đầu là đúng vì còn một beta. Lần xóa thứ hai trả true; lần thứ ba mới trả false.

Đầu có priority 1 vẫn còn sau khi xóa beta/2. Điều này giúp phân biệt xóa một đối tượng chỉ định với lấy đầu.

Cuối cùng, `remaining priorities poll=[1, 2, 9]` cho thấy phần còn lại vẫn được lấy theo ưu tiên. Alpha/2 vẫn còn dù beta/2 cùng mức đã bị loại. Kiểm tra này xác nhận ý nghĩa thành viên và trình tự ưu tiên sau thao tác xóa trong ví dụ.

Nếu cần xóa **mọi** phần tử bằng một giá trị, phải chọn một hợp đồng riêng, chẳng hạn một phép loại theo điều kiện phù hợp. Lặp remove có thể đúng về chức năng nhưng mỗi lần có thể phải tìm lại; khi số lần xóa lớn, chi phí lặp không còn chỉ là một lần O(n).

Không dựa vào việc các bản bằng nhau được lưu cùng nhau hoặc được lấy theo thứ tự đến. PriorityQueue không cung cấp những bảo đảm ấy.

## 12. Contains và remove với null hoặc một kiểu giá trị dò khác

PriorityQueue cấm thêm thành viên null. Trong ví dụ, contains(null) và remove(null) đều trả false. Câu “không cho lưu null” không có nghĩa mọi phương thức nhận đối số null đều phải ném lỗi giống offer(null).

Các chữ ký tìm/xóa nhận Object, nên bạn cũng có thể viết:

~~~java
numbers.contains("4");
numbers.remove("4");
~~~

qua PriorityQueue<Integer>. Hai lời gọi trong chương trình trả false vì chuỗi “4” không bằng Integer 4 theo equals của các kiểu đó.

Chúng không cố chèn chuỗi vào hàng đợi, cũng không dùng Comparator tự nhiên để biến chuỗi thành số. Vì vậy, không nên gán chính sách so sánh khi chèn một kiểu sai cho mọi phép hỏi nhận Object.

Điều này không cho phép bỏ qua việc định nghĩa equals đúng. Một kiểu có equals bất thường, không đối xứng hoặc ném lỗi ngoài hợp đồng có thể làm hành vi tìm theo giá trị khó hiểu. Hãy giữ quy tắc bằng nhau là quan hệ hợp lệ và ổn định.

Với Task, record mặc định xét id và priority. Nếu bạn cần hủy theo id mà không biết priority đang được lưu, một Task tự tạo với id đúng nhưng priority đoán khác không tự khớp equals. Cần phương án nhận diện hoặc chỉ mục theo id rõ ràng.

## 13. Đếm thao tác để hiểu chi phí, không lấy một thời gian chạy làm kết luận

Chương trình tạo hàng đợi Key có 8, 16 rồi 32 phần tử. Mỗi Key có một id, còn Comparator sắp theo id. Sau khi tạo xong dữ liệu, bộ đếm equals được đặt về 0 trước mỗi phép thử.

Giá trị dò id -1 không tồn tại. Kết quả:

~~~text
n=8  : contains vắng → 8 lần equals
n=16 : contains vắng → 16 lần equals
n=32 : contains vắng → 32 lần equals
~~~

Remove với cùng giá trị dò vắng cũng cho số lần tương ứng. Nó cần xác nhận không có một thành viên bằng giá trị dò trước khi trả false.

Đây là quan sát ở cách triển khai đang chạy, phù hợp với bước quét được đối chiếu trong OpenJDK 17. Số lần equals chính xác không phải API mà ứng dụng nên buộc mọi phiên bản phải giữ; kết luận quan trọng để chọn cấu trúc là chi phí tìm tuyến tính được mô tả cho lớp.

Bài thử không cần dùng đồng hồ để thấy số kiểm tra tăng cùng số thành viên. Đo thời gian ở các kích thước nhỏ dễ bị ảnh hưởng bởi nhiều yếu tố ngoài phép tìm.

### Vì sao xóa phần tử nhỏ nhất chỉ đếm một equals?

Với giá trị dò id 0, phần tử phù hợp đang ở đầu vùng tìm của ví dụ. Equals khớp ngay nên bộ đếm bằng 1.

Nhưng sau khi xóa, hàng đợi vẫn cần sửa heap để đầu mới là id 1. Bộ đếm không đếm các lời gọi Comparator hoặc các lần di chuyển tham chiếu.

Vì thế:

~~~text
equals calls=1
~~~

không chứng minh toàn bộ remove(Object) có chi phí O(1). Nó chỉ mô tả phần công việc mà thiết bị đo trong ví dụ thật sự đếm.

Cùng nguyên tắc ấy, không thể đo ít lời gọi equals rồi kết luận tất cả thao tác trên PriorityQueue đều nhanh như xem đầu.

## 14. Tránh tìm hai lần khi chỉ cần kết quả xóa

Đoạn ý tưởng:

~~~java
if (queue.contains(probe)) {
    queue.remove(probe);
}
~~~

kiểm tra rồi xóa bằng hai thao tác tìm độc lập. Nó có thể làm lại công việc trong trường hợp phần tử tồn tại.

Nếu mục đích là “thử xóa và biết có xóa được không”, giá trị boolean từ remove đã trả lời:

~~~java
boolean removed = queue.remove(probe);
~~~

Đây là các đoạn minh họa với queue và giá trị dò đã khai báo. Không cần contains trước để được phép gọi remove; không tìm thấy thì remove trả false.

Contains trước cũng không dành riêng thành viên cho lần remove sau. Nếu có thao tác xen vào, trạng thái giữa hai lời gọi có thể thay đổi. PriorityQueue không được đồng bộ hóa sẵn để nhiều luồng tùy ý cùng sửa.

Điều này không có nghĩa một lần remove đã giải quyết mọi vấn đề cạnh tranh. Nó chỉ tránh một phép tìm thừa khi yêu cầu có thể được diễn đạt bằng chính kết quả của thao tác xóa.

Nếu chỉ muốn biết có mặt và hoàn toàn không sửa dữ liệu, contains vẫn là thao tác đúng về chức năng. Vấn đề là chọn đúng mục đích và hiểu chi phí.

## 15. Khi truy vấn theo định danh xuất hiện thường xuyên

Nếu ứng dụng chủ yếu thêm rồi lấy đầu theo ưu tiên, PriorityQueue có thể đáp ứng trực tiếp. Nếu mỗi công việc lại bị tìm hoặc hủy theo id nhiều lần, những lần quét O(n) có thể trở thành phần chi phí cần thiết kế lại.

Một chỉ mục phụ theo khóa có thể giúp hỏi về sự hiện diện theo khóa đó. Nhưng thêm một HashSet không tự làm remove(Object) của PriorityQueue tìm được vị trí nhanh hơn; hàng đợi vẫn không được cung cấp một địa chỉ phần tử từ HashSet.

Cũng phải xét bản trùng. Nếu cùng một giá trị xuất hiện hai lần, xóa một lần chưa làm giá trị ấy vắng mặt. Một tập khóa đơn giản có thể bị cập nhật sai khi bạn loại khóa ngay sau lần poll hoặc remove đầu tiên. Mô hình đếm số lần theo khóa có thể phù hợp hơn nếu sự trùng lặp được phép, cùng quy tắc cập nhật cho mọi đường thêm/lấy/xóa.

Đối với việc hủy, có thể có thiết kế đánh dấu không còn hiệu lực rồi bỏ qua khi phần tử lên đầu. Đó là xóa ở mức logic, không phải loại vật lý ngay khỏi PriorityQueue. Cần hiểu tác động lên dung lượng dữ liệu đang giữ và cách nhận diện những lần đưa lại cùng id.

Nếu cần xóa tùy ý nhanh bằng một địa chỉ hoặc chỉ mục vị trí, phải chọn một cấu trúc có khả năng đó hoặc xây mô hình chỉ mục được duy trì đúng. Không truy cập mảng riêng của PriorityQueue để coi một chỉ số nội bộ là ổn định; sửa heap có thể chuyển các phần tử.

Các lựa chọn này là thay đổi mô hình dữ liệu, không phải đổi tên một lời gọi contains thành remove. Trước khi thêm cấu trúc phụ, hãy xác định rõ khóa, bản trùng, cách cập nhật và tần suất truy vấn thực tế.

## 16. Sự hiện diện không chứng minh thứ tự ưu tiên còn hợp lệ

Một Task có thể được tìm thấy theo equals mà điều đó không chứng minh mọi quan hệ ưu tiên trong hàng đợi đang đúng với ý định.

Ví dụ, nếu dùng đối tượng có trường priority sửa được và đổi nó trực tiếp khi đối tượng còn trong heap, contains có thể vẫn tìm theo equals hoặc định danh. Nhưng PriorityQueue không tự nhận một sự kiện sửa heap chỉ vì trường nội bộ của phần tử đổi.

Hai vấn đề cần tách:

- Quy tắc nhận diện quyết định giá trị dò nào bằng phần tử.
- Quy tắc ưu tiên quyết định phần tử nào phải lên đầu.

Trong bài, Task là record có thành phần được xác lập khi tạo. Không có thao tác gán lại priority trong lúc lưu, nên phép thử tập trung vào ý nghĩa tìm/xóa.

Nếu cần đổi ưu tiên, thiết kế một quy trình cập nhật rõ ràng cho mục dữ liệu cụ thể. Nếu cùng một đối tượng hoặc giá trị được đưa vào nhiều lần, cũng cần hiểu tác động lên các lần xuất hiện ấy.

Không dùng kết quả contains=true như một phép kiểm tra đầy đủ về tính đúng của cấu trúc hoặc thuật toán xử lý. Nó chỉ trả lời sự hiện diện theo hợp đồng của phương thức.

## 17. Những nhầm lẫn thường gặp

| Nhầm lẫn | Điều cần kiểm tra |
| --- | --- |
| “Heap nên mọi tìm kiếm đều O(log n)” | Heap phục vụ chọn đầu; contains/remove(Object) của PriorityQueue tuyến tính |
| “Cùng priority thì contains true” | Contains dùng equals, không chỉ Comparator |
| “Phải cùng tham chiếu mới xóa được” | Một giá trị dò khác tham chiếu vẫn khớp nếu equals đúng |
| “Record có trường id nên mặc định chỉ xét id” | Record Task của bài xét cả id và priority |
| “Remove(Object) xóa hết bản trùng” | Mỗi lần chỉ xóa một thành viên phù hợp |
| “Remove(Object) luôn xóa đầu” | Đó là remove() không đối số; dạng có đối số tìm giá trị |
| “PriorityQueue<Integer>.remove(3) là chỉ số 3” | PriorityQueue không có remove theo chỉ số |
| “Một lần equals tức là toàn bộ phép xóa O(1)” | Còn bước sửa heap và các công việc không được bộ đếm đó đo |
| “Contains trước giúp remove tìm nhanh hơn” | Không có vị trí tìm được truyền qua hai lời gọi |
| “Dãy toArray đã sắp nên tìm nhị phân được ngay” | Dãy không được cam kết theo thứ tự ưu tiên đầy đủ |
| “Null bị cấm nên contains(null) cũng phải ném lỗi” | Chính sách lưu và hành vi tham số tìm kiếm khác nhau |
| “Thêm HashSet là xóa trong heap sẽ O(1)” | Có chỉ mục hiện diện không tự có chỉ mục vị trí của heap |
| “Tập khóa đủ cho mọi trường hợp có bản trùng” | Phải duy trì số lần hoặc mô hình duy nhất phù hợp |
| “Contains đúng nghĩa là heap chắc chắn được cập nhật sau sửa priority” | Sự hiện diện và cấu trúc ưu tiên là hai thuộc tính riêng |

Các câu hỏi nhanh khi xem lại mã là: chữ ký nào được gọi, dùng equals hay Comparator, xóa một lần hay mọi lần, và có tìm lặp lại không cần thiết không?

## 18. Thực hành có lời giải

### Bài 1: cùng ưu tiên nhưng khác giá trị

Hàng đợi có Task("A", 5). Comparator chỉ xét priority. Contains(Task("B", 5)) có true vì hai priority cùng bằng 5 không?

**Lời giải:** Không theo record Task của bài. Hai id khác nhau nên equals khác, dù Comparator trả 0. Contains không dùng nhóm ưu tiên làm định nghĩa của cùng phần tử.

### Bài 2: giá trị dò mới

Hàng đợi có một Task("A", 5). Bạn tạo một đối tượng mới với cùng hai thành phần. Có nhất thiết contains trả false do khác tham chiếu không?

**Lời giải:** Không. Record có equals theo các thành phần, nên giá trị dò mới vẫn bằng giá trị đang lưu và có thể được tìm thấy.

### Bài 3: bản trùng

Một Task bằng x có ba lần xuất hiện. Remove(x) thành công một lần. Contains(x) có bắt buộc false không?

**Lời giải:** Không. Vẫn còn hai lần xuất hiện. Remove(Object) không phải thao tác xóa hết.

### Bài 4: lấy đầu và xóa chỉ định

Hàng đợi có ưu tiên 1, 2, 9. Remove(Object) xóa công việc priority 9. Có bắt buộc đầu priority 1 cũng bị lấy ra không?

**Lời giải:** Không. Dạng có đối số tìm đúng giá trị cần loại. Poll hoặc remove không đối số mới lấy đầu theo ưu tiên.

### Bài 5: số nguyên và chỉ số

PriorityQueue<Integer> chứa 1, 2, 3, 4. Lời gọi remove(3) có nghĩa gì?

**Lời giải:** Xóa một giá trị Integer 3 nếu có và trả boolean. PriorityQueue không cung cấp xóa ở chỉ số 3.

### Bài 6: phần tử vắng

Một PriorityQueue có 10.000 thành viên và giá trị dò không tồn tại. Có thể coi contains chỉ cần khoảng log2(10.000) phép kiểm tra vì đây là heap không?

**Lời giải:** Không. Trong cách triển khai chuẩn được mô tả, contains tìm tuyến tính; trường hợp vắng có thể phải kiểm tra toàn bộ các thành viên.

### Bài 7: bộ đếm equals

Remove một giá trị ở đầu chỉ đếm một equals. Tại sao không được kết luận remove(Object) là O(1)?

**Lời giải:** Bộ đếm chỉ đo equals ở bước tìm. Sau khi xóa vẫn có thể cần các so sánh và di chuyển để sửa heap. Trường hợp đầu cũng không đại diện cho mọi vị trí hoặc giá trị dò vắng.

### Bài 8: tìm hai lần

Mã gọi contains(x), nếu true thì remove(x). Nếu chỉ cần biết xóa được hay không, có cách diễn đạt trực tiếp hơn không?

**Lời giải:** Dùng boolean của chính remove(x). Không tìm thấy trả false; tìm thấy và xóa trả true. Cách này tránh một bước tìm trước không truyền lại vị trí cho remove.

### Bài 9: nhận diện theo id

Task là record gồm id và priority. Bạn chỉ biết id của công việc cần hủy rồi tự chọn một priority khác để tạo giá trị dò. Giá trị dò có chắc khớp không?

**Lời giải:** Không. Equals mặc định của record xét cả hai thành phần. Muốn thao tác theo id cần mô hình khóa, phép bằng nhau hoặc chỉ mục phù hợp với yêu cầu.

### Bài 10: chỉ mục phụ và bản trùng

Bạn thêm một HashSet khóa bên cạnh PriorityQueue. Một khóa xuất hiện hai lần trong hàng đợi. Sau khi poll một lần, xóa ngay khóa khỏi HashSet có đúng không?

**Lời giải:** Không nếu còn một lần xuất hiện khác. Cần quy tắc duy trì tính duy nhất hoặc số lần xuất hiện. Chỉ mục phụ phải phản ánh đúng mọi đường cập nhật.

### Bài 11: kiểu giá trị dò khác

PriorityQueue<Integer> chứa số 4. Contains("4") trong ví dụ có true vì chuỗi có cùng ký tự số không?

**Lời giải:** Không. Chuỗi "4" và Integer 4 không bằng nhau theo equals. Phép hỏi nhận Object không tự phân tích chuỗi thành số hay chèn chuỗi vào hàng đợi.

### Bài 12: tìm thấy sau khi sửa đối tượng

Một đối tượng đang lưu bị thay priority trực tiếp nhưng contains vẫn true. Điều đó có chứng minh peek đang chọn đúng theo priority mới không?

**Lời giải:** Không. Contains chỉ kiểm tra sự hiện diện theo equals. Việc heap có được tổ chức đúng theo các giá trị ưu tiên hiện tại là vấn đề khác.

## 19. Cách áp dụng vào mã thật

Dùng contains khi câu hỏi thật sự là sự hiện diện theo equals và chấp nhận chi phí quét. Dùng remove(Object) khi cần thử xóa một giá trị cụ thể, đọc boolean của nó và nhớ mỗi lần chỉ loại một phần tử bằng nhau.

Khi công việc cần lấy đầu ưu tiên, dùng poll hoặc remove không đối số thay vì tìm lại một đối tượng đã biết ở đầu. Khi truy vấn theo id lặp lại nhiều, đánh giá mô hình chỉ mục riêng thay vì giả định heap đã cung cấp việc đó.

Độ phức tạp phải đi cùng chữ ký chính xác: tìm một giá trị bất kỳ, biết vị trí để sửa heap và chỉ xem đầu là ba loại công việc khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="priority-queue-005">Vì sao iterator không duyệt theo thứ tự ưu tiên</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao iterator không duyệt theo thứ tự ưu tiên

## 1. Hai ý nghĩa khác nhau của “phần tử tiếp theo”

Một ứng dụng giữ công việc trong `PriorityQueue` và cần hiển thị danh sách theo mức ưu tiên. Lập trình viên viết vòng `for`, thấy một phần tử kém ưu tiên xuất hiện trước phần tử tốt hơn và nghi rằng hàng đợi bị hỏng.

Vấn đề thường nằm ở kỳ vọng đối với phép duyệt.

Trong `PriorityQueue`:

- **Phần tử tiếp theo được lấy bằng `poll()`** là đầu hàng đợi theo quy tắc ưu tiên.
- **Phần tử tiếp theo được trả bằng `Iterator.next()`** là phần tử tiếp theo của lượt duyệt; API không cam kết nó có ưu tiên tốt nhất trong số phần tử chưa duyệt.

Iterator không phải một vòng `poll()` được giấu bên trong. Một bên đọc các phần tử; bên kia lấy/xóa đầu và sửa lại cấu trúc để đầu mới tiếp tục đúng.

Sau bài này, người học cần giải thích được nguồn gốc khác biệt đó, biết nhận ra các cách viết không tự sắp xếp, và chọn được cách xem theo ưu tiên với tác động dữ liệu phù hợp.

## 2. PriorityQueue bảo đảm điều gì?

### 2.1. Bảo đảm ở đầu hàng đợi

Theo thứ tự tự nhiên của `Integer`, số nhỏ hơn đứng trước. Nếu hàng đợi chứa 12, 4, 9, 1, 7, 3, 6 thì `peek()` cho 1. Lấy hết bằng `poll()` cho các giá trị theo thứ tự:

```text
1, 3, 4, 6, 7, 9, 12
```

Nếu dùng comparator đảo chiều, giá trị lớn hơn có thể đứng trước. “Ưu tiên trước” luôn phải được hiểu theo comparator hoặc thứ tự tự nhiên đang áp dụng.

`peek()` xem đầu mà không lấy ra. `poll()` lấy đầu và thay đổi hàng đợi. Đây là những phép gắn trực tiếp với quy tắc chọn đầu.

### 2.2. Không có bảo đảm tương tự cho toàn bộ lượt duyệt

Tài liệu của `PriorityQueue` nêu rõ iterator không bảo đảm trả các phần tử theo một thứ tự cụ thể. Spliterator và các cách đọc dựa trên nó cũng không tạo bảo đảm thứ tự ưu tiên. Xem [PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html#iterator()).

Điều này không có nghĩa iterator được phép tùy ý bỏ sót các phần tử hoặc tự thêm phần tử. Trong một lượt đọc bình thường, khi hàng đợi không bị thay đổi, iterator cho phép đi qua các lần xuất hiện đang chứa. Điều không được cam kết ở đây là **thứ tự giữa những lần xuất hiện đó**.

Cũng không được suy ra iterator luôn trả đúng thứ tự thêm vào, luôn tăng dần, hoặc luôn có một bố trí cố định giữa các phiên bản cài đặt.

“Không bảo đảm thứ tự” không đồng nghĩa với “mỗi lần duyệt đều phải ngẫu nhiên”. Một chương trình có thể thấy cùng một thứ tự nhiều lần. Sự lặp lại quan sát được không biến thành hợp đồng của API.

## 3. Nguyên nhân cấu trúc: heap chỉ duy trì thứ tự từng nhánh

### 3.1. Gốc nhỏ nhất chưa làm toàn bộ mảng tăng dần

Một mô hình min-heap hợp lệ:

```text
             1
           /   \
          4     3
         / \   / \
        12  7 9   6
```

Điều kiện quan trọng là mỗi nút cha không lớn hơn các nút con:

```text
1 ≤ 4 và 1 ≤ 3
4 ≤ 12 và 4 ≤ 7
3 ≤ 9 và 3 ≤ 6
```

Nhờ tính bắc cầu của thứ tự, 1 không lớn hơn mọi phần tử dưới nó. Như vậy heap đủ thông tin để biết phần tử nhỏ nhất ở gốc.

Nhưng không có điều kiện bắt buộc rằng con trái 4 phải nhỏ hơn con phải 3. Cũng không có điều kiện buộc lá 7 ở nhánh trái phải đứng trước lá 6 ở nhánh phải theo giá trị.

### 3.2. Đọc theo vị trí lưu trữ không phải đọc theo hạng ưu tiên

Heap nhị phân có thể được biểu diễn bằng mảng theo từng mức:

```text
Chỉ số:    0  1  2   3  4  5  6
Giá trị:  [1, 4, 3, 12, 7, 9, 6]
```

Nếu đọc mảng từ trái sang phải, ngay sau 1 sẽ gặp 4, mặc dù 3 có ưu tiên tốt hơn 4. Đó là một phản ví dụ đủ để thấy:

```text
đúng tính chất heap
    không kéo theo
đúng thứ tự tăng dần của toàn bộ dãy đọc
```

Hình và mảng trên giải thích cơ chế. Mảng heap không phải một API chỉ số công khai của `PriorityQueue`, và bố trí cụ thể không phải đầu ra bắt buộc của iterator.

### 3.3. Một đường đọc không tự chọn tốt nhất giữa mọi nhánh

Để đọc theo ưu tiên, sau khi đã chọn gốc 1, cần quyết định phần tử tốt nhất còn lại giữa các nhánh. Việc đơn giản tăng chỉ số sang phần tử 4 không thực hiện quyết định đó.

Min-heap được thiết kế để cập nhật đầu hiệu quả sau khi thêm/lấy phần tử, thay vì duy trì toàn bộ dữ liệu như một danh sách đã sắp xếp. Nếu bắt iterator tự trả toàn bộ theo ưu tiên, nó phải làm thêm công việc như giữ một cấu trúc phụ hoặc sắp xếp một bản sao. Hợp đồng iterator hiện tại không yêu cầu công việc đó.

Trong OpenJDK 17u, lượt iterator chỉ đọc thông thường đi qua các vị trí của mảng heap. Lớp còn xử lý riêng tình huống xóa qua iterator; vì vậy không nên biến mô tả đường đọc thông thường thành một công thức áp dụng cho mọi lượt duyệt có sửa đổi. Có thể đối chiếu [lớp iterator trong mã nguồn PriorityQueue OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/PriorityQueue.java).

## 4. Vì sao poll lặp lại tạo được thứ tự ưu tiên?

Với heap mô hình ở trên, lấy 1 ra không đơn giản là nhảy qua ô đầu của mảng.

Một cách duy trì heap thực hiện:

1. Giữ 1 làm kết quả.
2. Đưa phần tử cuối 6 lên gốc và bỏ vị trí cuối cũ.
3. So sánh để đẩy 6 xuống vị trí hợp lệ.
4. Đặt phần tử phù hợp ở gốc mới.

Trạng thái tạm và trạng thái sau sửa:

```text
Trước khi lấy:              [1, 4, 3, 12, 7, 9, 6]
Đưa phần tử cuối lên gốc:   [6, 4, 3, 12, 7, 9]
Sửa quan hệ cha–con:        [3, 4, 6, 12, 7, 9]
```

Sau đó, phần tử được chọn cho lần `poll()` tiếp theo là 3. Lấy tiếp lại sửa heap để 4 trở thành đầu.

| Thao tác | Có tìm đầu ưu tiên hiện tại? | Có lấy phần tử ra? | Có duy trì lại heap sau lần lấy? |
| --- | --- | --- | --- |
| `peek()` | Xem đầu hiện có | Không | Không cần vì chỉ đọc |
| `poll()` | Có | Có | Có khi còn phần tử |
| `Iterator.next()` | Không có hợp đồng chọn ưu tiên | Không | Không thực hiện bước lấy đầu |

Chính sự kết hợp “lấy đầu rồi sửa cấu trúc” tạo ra dãy ưu tiên khi lặp `poll()`. Iterator không làm hai bước đó nên không có cùng bảo đảm.

Bảo đảm này giả định quy tắc so sánh và khóa ưu tiên không bị thay đổi tùy ý trong lúc phần tử đang nằm trong hàng đợi. Nếu sửa trực tiếp khóa làm heap không còn phù hợp, không thể dùng kết quả đó để đánh giá quy tắc duyệt bình thường.

## 5. Chương trình quan sát: cùng dữ liệu, hai thứ tự khác nhau

Lưu mã sau vào **PriorityIterationObservation.java**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

public class PriorityIterationObservation {
    private static List<Integer> pollAll(PriorityQueue<Integer> queue) {
        List<Integer> result = new ArrayList<>();
        Integer value;
        while ((value = queue.poll()) != null) {
            result.add(value);
        }
        return result;
    }

    public static void main(String[] args) {
        List<Integer> input = List.of(12, 4, 9, 1, 7, 3, 6);
        PriorityQueue<Integer> queue = new PriorityQueue<>();
        queue.addAll(input);

        System.out.println("insertion order: " + input);
        System.out.println("head before iteration: " + queue.peek());

        List<Integer> visited = new ArrayList<>();
        Iterator<Integer> iterator = queue.iterator();
        while (iterator.hasNext()) {
            visited.add(iterator.next());
        }
        System.out.println("observed iterator order: " + visited);
        System.out.println("size after iteration: " + queue.size());
        System.out.println("head after iteration: " + queue.peek());

        List<Integer> sortedVisited = new ArrayList<>(visited);
        Collections.sort(sortedVisited);
        List<Integer> sortedInput = new ArrayList<>(input);
        Collections.sort(sortedInput);
        System.out.println("same elements: " + sortedVisited.equals(sortedInput));

        PriorityQueue<Integer> copy = new PriorityQueue<>(queue);
        System.out.println("poll order on copy: " + pollAll(copy));
        System.out.println("original size after copy: " + queue.size());
        System.out.println("poll order on original: " + pollAll(queue));
        System.out.println("original size after polling: " + queue.size());

        Iterator<Integer> empty = queue.iterator();
        System.out.println("empty iterator hasNext: " + empty.hasNext());
        try {
            empty.next();
        } catch (NoSuchElementException ex) {
            System.out.println("empty iterator next: " + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 PriorityIterationObservation.java
java PriorityIterationObservation
```

Chương trình đã được biên dịch với `--release 17` và chạy trên OpenJDK 24.0.2. Stdout quan sát được chính xác trong lần kiểm tra:

```text
insertion order: [12, 4, 9, 1, 7, 3, 6]
head before iteration: 1
observed iterator order: [1, 4, 3, 12, 7, 9, 6]
size after iteration: 7
head after iteration: 1
same elements: true
poll order on copy: [1, 3, 4, 6, 7, 9, 12]
original size after copy: 7
poll order on original: [1, 3, 4, 6, 7, 9, 12]
original size after polling: 0
empty iterator hasNext: false
empty iterator next: NoSuchElementException
```

Dòng `observed iterator order` là kết quả quan sát của môi trường đã nêu, **không phải một thứ tự mà mọi Java 17+ phải tạo ra**. Cờ `--release 17` kiểm tra việc dùng ngôn ngữ/API mục tiêu và tạo bytecode tương ứng; nó không đổi JVM chạy ví dụ thành Java 17.

Những điểm có giá trị học tập:

- Thứ tự thêm bắt đầu bằng 12, nhưng đầu là 1.
- Trong kết quả quan sát, iterator trả 4 trước 3 và 12 trước 7. Dãy đó chưa được sắp xếp theo ưu tiên.
- Kích thước và đầu giữ nguyên sau lượt iterator chỉ đọc.
- Sắp xếp bản các phần tử đã đọc cho kết quả bằng sắp xếp đầu vào: iterator vẫn đọc đủ dữ liệu.
- Lấy hết từ bản sao cho thứ tự ưu tiên và không làm giảm kích thước bản gốc.
- Lấy hết từ bản gốc cũng cho thứ tự đó nhưng làm hàng đợi rỗng.
- Iterator rỗng trả `false` từ `hasNext()`; gọi `next()` khi không còn phần tử báo `NoSuchElementException`, không trả `null`.

Hàm `pollAll` dùng `null` làm dấu dừng vì `PriorityQueue` cấm phần tử `null`. Không suy nguyên xi cách dừng này thành quy tắc cho mọi kiểu collection hoặc iterator.

## 6. Những cách viết trông khác nhau nhưng không tự sắp xếp

### 6.1. Enhanced for chỉ che cách gọi iterator

Đoạn sau minh họa một lượt đọc:

```java
// queue là PriorityQueue<Integer> đã có dữ liệu.
for (Integer value : queue) {
    System.out.println(value);
}
```

Enhanced for không chuyển thành thuật toán lấy đầu ưu tiên. Với một đối tượng `Iterable` như hàng đợi, nó dùng iterator để đi qua phần tử.

Vì vậy việc đổi từ vòng `while (iterator.hasNext())` sang `for` không sửa được yêu cầu hiển thị tăng dần.

### 6.2. forEach, toArray và toString cũng chưa đủ

| Cách đọc | Có tự sắp xếp theo ưu tiên không? |
| --- | --- |
| `queue.iterator()` | Không |
| `for (E e : queue)` | Không |
| `queue.forEach(action)` | Không |
| `queue.toArray()` | Không |
| `new ArrayList<>(queue)` | Không |
| `System.out.println(queue)` | Không |
| `queue.stream().toList()` | Không |

“Không” ở bảng nghĩa là không có một bước và một hợp đồng sắp xếp theo ưu tiên. Kết quả vẫn có thể tình cờ trông đúng trên dữ liệu nào đó.

Chuyển dữ liệu sang một kiểu chứa khác không tự bổ sung quy tắc thứ tự. Nếu giao diện cần một danh sách đã xếp, phải có bước xếp hoặc có cách lấy theo ưu tiên rõ ràng.

### 6.3. forEachOrdered không phải tên khác của sorted

Trong stream, `forEachOrdered` giữ thứ tự gặp của stream khi stream có thứ tự gặp được xác định. Nó không tự hỏi comparator của `PriorityQueue` rồi sắp xếp.

Vì nguồn hàng đợi này không cam kết thứ tự gặp theo ưu tiên, gọi:

```java
// Không bổ sung bảo đảm thứ tự ưu tiên:
queue.stream().forEachOrdered(System.out::println);
```

chưa giải quyết yêu cầu. Nếu cần xếp, dùng `sorted(comparator)`. Nếu kết hợp stream song song và cần in theo thứ tự sau bước xếp, dùng thao tác cuối tôn trọng thứ tự đó.

Các ý nghĩa này được mô tả trong [tài liệu Stream Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#forEachOrdered(java.util.function.Consumer)).

## 7. Ba cách đúng để xem theo ưu tiên mà giữ bản gốc

### 7.1. Sao chép PriorityQueue rồi poll bản sao

Một phương thức tiện ích có thể đặt trong lớp của ứng dụng:

```java
static <E> List<E> orderedByPolling(PriorityQueue<E> source) {
    PriorityQueue<E> copy = new PriorityQueue<>(source);
    List<E> result = new ArrayList<>();
    while (!copy.isEmpty()) {
        result.add(copy.poll());
    }
    return result;
}
```

Đây là phương thức minh họa, cần import `List`, `ArrayList` và `PriorityQueue`; chương trình đầy đủ bên dưới có một biến thể tương đương.

Constructor nhận `PriorityQueue` giữ quy tắc so sánh của nguồn. Vòng `poll()` tác động lên bản sao, nên cấu trúc hàng đợi nguồn không bị rút cạn.

Cách này bám sát đúng câu hỏi: “Nếu liên tục lấy đầu, thứ tự sẽ là gì?” Nó đặc biệt dễ hiểu khi muốn xem một vài phần tử ưu tiên đầu tiên thay vì toàn bộ.

### 7.2. Sao chép sang List rồi sort bằng đúng comparator

Nếu đã biết comparator `order` đang dùng:

```java
// Đoạn lệnh minh họa; queue, order và kiểu Task đã được định nghĩa.
List<Task> ordered = new ArrayList<>(queue);
ordered.sort(order);
```

Danh sách riêng cho phép xếp mà không xóa dữ liệu khỏi hàng đợi. Cần giữ comparator của nghiệp vụ như một giá trị có thể dùng lại, thay vì viết một quy tắc tương tự ở nhiều chỗ rồi để chúng lệch nhau.

Với `PriorityQueue<Integer>` dùng thứ tự tự nhiên, có thể dùng:

```java
List<Integer> ordered = new ArrayList<>(queue);
ordered.sort(Comparator.naturalOrder());
```

Hai đoạn trên là những ngữ cảnh riêng; không khai báo cả hai biến `ordered` trong cùng một phạm vi.

### 7.3. Stream có sorted rõ ràng

Với comparator tường minh:

```java
List<Task> ordered = queue.stream()
        .sorted(order)
        .toList();
```

`sorted(order)` mới là bước yêu cầu thứ tự. `toList()` thu kết quả; nó không tự đọc comparator từ hàng đợi.

Nếu chỉ gọi `sorted()`, Java dùng thứ tự tự nhiên. Điều này có thể khác quy tắc của hàng đợi:

```java
// Đoạn lệnh minh họa:
PriorityQueue<Integer> highestFirst =
        new PriorityQueue<>(Comparator.reverseOrder());
highestFirst.addAll(List.of(8, 1, 5));

List<Integer> natural = highestFirst.stream().sorted().toList();
List<Integer> priority = highestFirst.stream()
        .sorted(Comparator.reverseOrder())
        .toList();

// natural  là [1, 5, 8]
// priority là [8, 5, 1]
// highestFirst.peek() vẫn là 8
```

Một kiểu đối tượng không triển khai `Comparable` còn có thể gây `ClassCastException` nếu yêu cầu sắp xếp tự nhiên thay vì cung cấp comparator phù hợp.

### 7.4. Nếu lấy comparator từ queue, phải xét null

`queue.comparator()` trả `null` khi hàng đợi dùng thứ tự tự nhiên. Không đưa thẳng giá trị này vào `stream.sorted(comparator)`: phương thức stream cần comparator không null.

Một cách an toàn, dễ đọc là phân nhánh theo ngữ cảnh kiểu phần tử hoặc giữ comparator tường minh ngay từ lúc tạo hàng đợi. Sao chép `PriorityQueue` rồi `poll()` cũng tránh phải tự viết lại logic chọn thứ tự tự nhiên hay comparator riêng.

Với `List.sort(null)`, hợp đồng lại là dùng thứ tự tự nhiên; xem [List.sort Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)). Không khái quát ý nghĩa `null` giữa các API chỉ vì cùng có thao tác sắp xếp.

## 8. Chương trình chạy: ba cách cho cùng kết quả

Lưu thành **PriorityOrderedViews.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityOrderedViews {
    record Task(String label, int rank, long ticket) {
        @Override
        public String toString() {
            return label + ":" + rank + ":" + ticket;
        }
    }

    private static <E> List<E> pollCopy(PriorityQueue<E> source) {
        PriorityQueue<E> copy = new PriorityQueue<>(source);
        List<E> ordered = new ArrayList<>();
        E value;
        while ((value = copy.poll()) != null) {
            ordered.add(value);
        }
        return ordered;
    }

    public static void main(String[] args) {
        Comparator<Task> order =
                Comparator.comparingInt(Task::rank)
                          .reversed()
                          .thenComparingLong(Task::ticket);
        PriorityQueue<Task> queue = new PriorityQueue<>(order);
        queue.addAll(List.of(
                new Task("report", 2, 0),
                new Task("alert", 9, 1),
                new Task("repair", 9, 2),
                new Task("backup", 4, 3)));

        List<Task> byPolling = pollCopy(queue);
        List<Task> bySorting = new ArrayList<>(queue);
        bySorting.sort(order);
        List<Task> byStream = queue.stream().sorted(order).toList();

        System.out.println("copy plus poll: " + byPolling);
        System.out.println("list plus sort: " + bySorting);
        System.out.println("stream plus sorted: " + byStream);
        System.out.println("all equal: "
                + (byPolling.equals(bySorting) && bySorting.equals(byStream)));
        System.out.println("source size: " + queue.size());
        System.out.println("source head: " + queue.peek());

        PriorityQueue<Integer> duplicates =
                new PriorityQueue<>(List.of(5, 2, 5, 1));
        System.out.println("duplicates preserved: " + pollCopy(duplicates));
        System.out.println("duplicate source size: " + duplicates.size());
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 PriorityOrderedViews.java
java PriorityOrderedViews
```

Stdout chính xác:

```text
copy plus poll: [alert:9:1, repair:9:2, backup:4:3, report:2:0]
list plus sort: [alert:9:1, repair:9:2, backup:4:3, report:2:0]
stream plus sorted: [alert:9:1, repair:9:2, backup:4:3, report:2:0]
all equal: true
source size: 4
source head: alert:9:1
duplicates preserved: [1, 2, 5, 5]
duplicate source size: 4
```

Ở đây quy ước là `rank` lớn hơn đứng trước. Nếu cùng rank, `ticket` nhỏ hơn đứng trước. Vì vậy alert đứng trước repair, rồi backup và report.

Ba cách cùng trả kết quả giống nhau vì cùng dùng một thứ tự đủ phân biệt các tác vụ mẫu:

1. Bản sao PriorityQueue giữ comparator của nguồn rồi lấy đầu.
2. Danh sách riêng được xếp bằng comparator `order`.
3. Stream có `sorted(order)`.

Cả ba chỉ đọc cấu trúc nguồn. Kích thước vẫn là 4 và đầu vẫn là alert. Ví dụ số ở cuối giữ đủ cả hai số 5; không có bước biến dữ liệu thành tập hợp để làm mất lần xuất hiện.

`Stream.toList()` có từ trước Java 17 nên dùng được trong bài. Danh sách kết quả của phương thức đó không hỗ trợ thay đổi cấu trúc; hai danh sách tạo bằng `ArrayList` trong chương trình có khả năng sửa khác. Sự khác biệt này không ảnh hưởng đến phép so sánh nội dung đang thực hiện.

## 9. Hòa ưu tiên: sắp xếp đúng vẫn có thể khác thứ tự danh tính

### 9.1. Comparator bằng 0 chưa tạo quy tắc đến trước

Giả sử comparator chỉ xét mức ưu tiên. A và B có cùng mức 1. Khi lấy đầu, cả hai đều hợp lệ để được chọn trước phần tử mức 2, nhưng không có bảo đảm A phải được lấy trước B chỉ vì A được thêm trước.

Việc sắp xếp một bản danh sách cũng không tự khôi phục lịch sử thêm. Ngay cả một thuật toán sắp xếp ổn định cũng chỉ có thể giữ thứ tự đầu vào của những phần tử hòa; đầu vào được sao chép từ iterator vốn chưa có bảo đảm thứ tự đến.

Không dùng tính ổn định của sort để suy ra tính FIFO cho PriorityQueue.

### 9.2. Tiêu chí phụ phải là dữ liệu thật

Nếu cần FIFO trong từng nhóm ưu tiên, cấp số thứ tự nhận việc rồi ghép vào comparator, như trường `ticket` trong chương trình:

```java
Comparator.comparingInt(Task::rank)
          .reversed()
          .thenComparingLong(Task::ticket)
```

Đây là biểu thức trong ngữ cảnh kiểu `Task` đã định nghĩa. Số thứ tự phải diễn đạt đúng quy tắc của ứng dụng và đủ phân biệt các phần tử cần xếp. Nếu cả rank lẫn ticket vẫn hòa, vấn đề thứ tự giữa những phần tử đó vẫn chưa được giải quyết.

Tính duy nhất của ticket trong ví dụ là do ứng dụng cấp, không phải đặc tính tự có của PriorityQueue.

### 9.3. Đừng dùng TreeSet chỉ để làm một bản in đã xếp

Chuyển sang `TreeSet` có thể làm mất những phần tử mà comparator coi là tương đương. Với dữ liệu số `[5, 2, 5, 1]`, bản danh sách đã xếp giữ `[1, 2, 5, 5]`; một tập hợp chỉ giữ một giá trị 5.

Nếu yêu cầu là xem mọi công việc và mọi lần xuất hiện, chọn cấu trúc kết quả giữ được chúng. “Có thứ tự” không đủ nếu thao tác chuyển đổi còn thay đổi số lượng dữ liệu.

## 10. Chọn cách theo chi phí và tác động dữ liệu

Bảng sau xét n phần tử, comparator có chi phí hằng số, và không có nơi khác sửa dữ liệu trong quá trình đọc:

| Nhu cầu | Cách phù hợp | Tác động bản gốc | Chi phí chính |
| --- | --- | --- | --- |
| Đi qua mọi phần tử, không cần thứ tự | Iterator | Giữ nguyên nếu chỉ đọc | O(n) |
| Chỉ xem phần tử đầu ưu tiên | `peek()` | Giữ nguyên | O(1) |
| Lấy hết theo ưu tiên để xử lý | Lặp `poll()` | Bị rút cạn | O(n log n) |
| Xem toàn bộ theo ưu tiên | Sao chép rồi `poll()` | Giữ nguyên | O(n log n), thêm bộ nhớ |
| Xem toàn bộ theo ưu tiên trong danh sách | Sao chép rồi `sort` | Giữ nguyên | O(n log n), thêm bộ nhớ |
| Xem k phần tử tốt nhất | Sao chép rồi lấy tối đa k lần | Giữ nguyên | Chi phí sao chép cộng các lần lấy |

Với cách cài đặt thông thường của OpenJDK, sao chép một hàng đợi n phần tử cần O(n) thời gian và bộ nhớ; sau đó lấy k phần tử cần O(k log n). Vì thế không gọi toàn bộ cách “sao chép rồi lấy k” là O(k log n) mà bỏ qua bước sao chép.

Một vòng iterator chỉ đọc không cần tạo danh sách chứa toàn bộ n phần tử. Nếu muốn cộng một tổng, đếm hoặc tìm theo điều kiện mà không cần thứ tự, đừng bắt buộc sắp xếp chỉ vì dữ liệu đang ở PriorityQueue.

Ngược lại, một giao diện hiển thị theo ưu tiên phải có bước tạo thứ tự rõ ràng; không nên tối ưu bằng cách xóa mất yêu cầu rồi dùng iterator.

### Chỉ lấy vài phần tử đầu trên bản sao

Đoạn lệnh minh họa, `queue` là `PriorityQueue<Integer>` đã có dữ liệu và `k` là số không âm:

```java
PriorityQueue<Integer> copy = new PriorityQueue<>(queue);
List<Integer> first = new ArrayList<>();

for (int count = 0; count < k && !copy.isEmpty(); count++) {
    first.add(copy.poll());
}
```

Không dùng `queue.stream().limit(k)` rồi cho rằng đó là k phần tử ưu tiên nhất. `limit` không sắp xếp phần nguồn trước khi chọn.

## 11. Iterator chỉ đọc không phải bản chụp độc lập

### 11.1. Không trộn next với poll trên cùng hàng đợi

Ví dụ dễ sai:

```java
// Ví dụ sai về cách thay đổi nguồn trong khi đang duyệt:
Iterator<Integer> iterator = queue.iterator();
while (iterator.hasNext()) {
    System.out.println(iterator.next());
    queue.poll();
}
```

Iterator không có nghĩa “mỗi lần đọc rồi loại đầu thì tự cập nhật thành một lượt ưu tiên”. Việc sửa nguồn ngoài iterator làm lượt duyệt không còn tuân theo cách sử dụng bình thường. Có thể gặp `ConcurrentModificationException`; không dựa vào việc ngoại lệ có xuất hiện ở một lần chạy hay không để coi mã là đúng.

Nếu muốn lấy ưu tiên, dùng vòng `poll()` riêng. Nếu muốn vừa duyệt vừa xóa theo điều kiện, dùng cơ chế xóa được iterator hỗ trợ và hiểu rằng đó vẫn không tạo ra thứ tự ưu tiên.

Theo [hợp đồng Iterator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html#remove()), `remove()` của iterator xóa phần tử được `next()` trả gần nhất; nó không mang ý nghĩa “xóa đầu ưu tiên”. Với iterator hỗ trợ xóa, mỗi lần `next()` thành công cho tối đa một lần xóa tương ứng.

### 11.2. Bản sao cấu trúc vẫn có thể dùng chung đối tượng phần tử

`new PriorityQueue<>(queue)`, `new ArrayList<>(queue)` và stream trong ví dụ giữ các tham chiếu tới đối tượng phần tử. Chúng không tự sao chép sâu các đối tượng đó.

Thêm/xóa vào bản sao không thêm/xóa cấu trúc nguồn. Nhưng sửa một đối tượng thay đổi được đang được cả hai nơi tham chiếu vẫn có thể được quan sát từ cả hai nơi. Đặc biệt, sửa khóa ưu tiên có thể làm giả định thứ tự không còn đúng.

Các Task mẫu dùng trường kiểu nguyên thủy và String trong record để giữ khóa so sánh ổn định.

### 11.3. Sao chép không tự giải quyết truy cập đồng thời

PriorityQueue không an toàn cho việc nhiều luồng cùng truy cập khi có luồng sửa mà thiếu đồng bộ thích hợp. Một dòng tạo bản sao không tự tạo điểm chụp nguyên tử của một nguồn đang bị sửa.

Trong ứng dụng đồng thời, trước hết phải xác định cách có được một lần đọc hợp lệ theo thiết kế đồng bộ hoặc cấu trúc đang dùng. Sau đó mới chọn phương án sắp xếp/hiển thị. Các ví dụ trong bài được chạy trong một luồng với dữ liệu không bị sửa từ bên ngoài.

## 12. Kiểm thử đúng hợp đồng thay vì đóng đinh bố trí heap

### 12.1. Kiểm thử một lượt iterator chỉ đọc

Có thể kiểm tra các thuộc tính:

- Đọc đủ số lần xuất hiện mong đợi.
- Các giá trị và số lượng từng giá trị khớp nguồn.
- Kích thước và đầu không đổi sau khi chỉ đọc.
- Iterator hết phần tử đúng lúc; `next()` khi hết báo ngoại lệ theo hợp đồng.

Không nên viết kiểm thử sản phẩm yêu cầu một dãy iterator cụ thể như `[1, 4, 3, ...]` chỉ vì đã quan sát được dãy đó trên máy. Kiểm thử như vậy trói vào chi tiết bố trí thay vì yêu cầu nghiệp vụ.

Trong ví dụ quan sát, stdout ghi dãy thực tế để minh họa phản ví dụ. Nó không phải đề nghị dùng dãy đó làm một hợp đồng đa phiên bản.

### 12.2. Kiểm thử một danh sách được yêu cầu theo ưu tiên

Khi kiểm thử phương thức tạo danh sách theo ưu tiên, nên kiểm tra đồng thời:

1. Danh sách chứa đủ mọi lần xuất hiện.
2. Mỗi cặp phần tử liên tiếp không đi ngược comparator.
3. Nguồn được bảo toàn nếu hợp đồng là chỉ xem.
4. Quy tắc hòa được xác định nếu nghiệp vụ yêu cầu thứ tự danh tính cụ thể.

Không đổi cả hai phía thành `Set` để kiểm tra “cùng phần tử”, vì cách đó bỏ mất số lần xuất hiện. Sắp xếp các bản danh sách hoặc dùng bảng đếm phù hợp có thể giữ thông tin này.

Nếu comparator chỉ xét một phần dữ liệu và cho nhiều phần tử hòa, không buộc mọi phương án đúng phải trả cùng thứ tự danh tính. Muốn kiểm tra exact một thứ tự như chương trình Task, hãy có tiêu chí phụ đủ phân biệt trước.

## 13. Lỗi phổ biến và cách sửa

| Hiện tượng | Suy luận sai | Cách xử lý |
| --- | --- | --- |
| Iterator trả phần tử 4 trước 3 | Hàng đợi chắc đã hỏng | Kiểm tra đầu và hiểu bất biến heap |
| Vòng for không tăng dần | Đổi sang forEach sẽ sửa được | Thêm bước sort hoặc lấy đầu từ bản sao |
| In queue thấy dãy lộn xộn | toString phải in theo ưu tiên | Xem đó là hiển thị nội dung chưa có hợp đồng xếp |
| toArray vẫn không tăng dần | Chuyển sang mảng tự sắp xếp | Sắp xếp mảng hoặc danh sách theo quy tắc phù hợp |
| forEachOrdered chưa theo ưu tiên | Từ “Ordered” nghĩa là tự dùng comparator | Dùng sorted với comparator tường minh |
| Hàng đợi số lớn trước nhưng stream nhỏ trước | sorted() tự dùng thứ tự của queue | sorted() mặc định dùng thứ tự tự nhiên |
| Hiển thị làm mất công việc chờ | poll trên bản gốc khi chỉ muốn xem | Poll trên bản sao hoặc sort danh sách riêng |
| Hai việc hòa đổi vị trí | Sort ổn định phải khôi phục FIFO | Thêm số thứ tự nhận việc vào comparator |
| Một số việc biến mất sau khi đổi cấu trúc | Dùng TreeSet để xếp mọi lần xuất hiện | Dùng kết quả giữ trùng như List |
| Iterator kết hợp poll gặp lỗi | Tưởng iterator tự đồng bộ với lấy đầu | Chọn riêng lượt đọc hoặc lượt tiêu thụ |

## 14. Bài tập tự kiểm tra có lời giải

### Bài 1: Heap hay danh sách đã xếp?

Một heap có mảng mô hình `[1, 4, 3, 12, 7, 9, 6]`. Vì sao 4 đứng trước 3 vẫn hợp lệ?

**Lời giải:** 4 và 3 là hai con của 1. Min-heap chỉ yêu cầu 1 không lớn hơn mỗi con, không buộc con trái nhỏ hơn con phải. Đọc mảng theo chỉ số vì thế có thể gặp 4 trước 3.

### Bài 2: next và poll

Có hàng đợi số nguyên dùng thứ tự tự nhiên. Một vòng iterator và một vòng poll đều đọc bảy giá trị. Vòng nào phải cho dãy không giảm? Trạng thái hàng đợi sau mỗi vòng?

**Lời giải:** vòng poll cho dãy không giảm và lấy hết phần tử ra. Vòng iterator chỉ đọc giữ nguyên hàng đợi nhưng không có bảo đảm dãy không giảm. Hai vòng đều có thể đọc đủ phần tử mà khác cả thứ tự lẫn tác động dữ liệu.

### Bài 3: Hai phần tử đầu tiên

Muốn xem hai công việc ưu tiên nhất mà giữ nguyên nguồn. Có được dùng `queue.stream().limit(2).toList()` không?

**Lời giải:** cách đó chưa yêu cầu xếp theo ưu tiên, nên không bảo đảm chọn đúng hai phần tử cần xem. Có thể sao chép PriorityQueue rồi poll tối đa hai lần, hoặc sorted theo comparator trước khi limit. Phải xét cả chi phí tạo bản sao hoặc sắp xếp.

### Bài 4: Comparator đảo chiều

Hàng đợi chứa 8, 1, 5 và dùng `Comparator.reverseOrder()`. `queue.stream().sorted().toList()` trả gì? Có giống thứ tự poll không?

**Lời giải:** sorted() dùng thứ tự tự nhiên nên cho `[1, 5, 8]`. Poll theo quy tắc của hàng đợi cho `[8, 5, 1]`. Dùng sorted với comparator đảo chiều nếu muốn cùng thứ tự ưu tiên.

### Bài 5: Hòa ưu tiên và tính ổn định

A và B có cùng ưu tiên. Danh sách được tạo từ iterator rồi dùng sort ổn định. Có chứng minh được A đến trước sẽ đứng trước B không?

**Lời giải:** không. Sort ổn định giữ thứ tự đầu vào giữa các phần tử hòa, nhưng thứ tự iterator không cam kết lịch sử thêm. Cần dữ liệu thứ tự tiếp nhận và tiêu chí phụ phù hợp.

### Bài 6: Giữ các lần xuất hiện

Hàng đợi chứa `[5, 2, 5, 1]` xét theo các giá trị đang có. Hai kết quả `[1, 2, 5]` và `[1, 2, 5, 5]` đều đã xếp. Kết quả nào là bản xem đầy đủ đúng?

**Lời giải:** `[1, 2, 5, 5]`. Bản xem phải giữ cả hai lần xuất hiện của 5. Một kết quả có thứ tự nhưng thiếu dữ liệu vẫn sai yêu cầu.

### Bài 7: Một lần chạy đã xếp

Đầu vào nhỏ khiến iterator tình cờ trả `[1, 2, 3]`. Có thể bỏ bước sắp xếp trong chức năng hiển thị không?

**Lời giải:** không. Một quan sát không tạo ra bảo đảm thứ tự cho các dữ liệu hay bản cài đặt khác. Chức năng cần thứ tự phải thể hiện bằng thao tác có hợp đồng phù hợp.

### Bài 8: comparator null

Hàng đợi dùng thứ tự tự nhiên nên `queue.comparator()` là null. Gọi `queue.stream().sorted(queue.comparator())` có phải cách tổng quát đúng không?

**Lời giải:** không. Biến thể sorted nhận comparator cần đối số không null. Hãy xử lý trường hợp thứ tự tự nhiên rõ ràng hoặc sao chép PriorityQueue rồi lấy đầu trên bản sao.

## 15. Tài liệu đối chiếu

- [PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html): phân biệt hợp đồng đầu hàng đợi với hợp đồng duyệt.
- [Mã nguồn PriorityQueue OpenJDK 17u](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/PriorityQueue.java): xem cách tổ chức heap và đường đọc iterator thông thường.
- [Iterator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Iterator.html): thao tác đọc, trạng thái hết phần tử và quy tắc xóa.
- [Stream Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html): phân biệt sorted, limit và forEachOrdered.

Khi cần thứ tự ưu tiên, hãy chọn thao tác thực sự tạo ra thứ tự đó. Tên PriorityQueue bảo đảm cách chọn đầu; iterator phục vụ việc đi qua nội dung và không tự thực hiện cả một lượt lấy đầu ưu tiên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="priority-queue-006">Phần tử đồng hạng, tính ổn định và khóa phụ</a>

<details>
<summary>Xem chi tiết</summary>

# Phần tử đồng hạng, tính ổn định và khóa phụ

## 1. Bài toán: cùng mức ưu tiên thì ai đi trước?

Một hệ thống nhận ba công việc A, B, C theo đúng thứ tự đó. Cả ba có mức ưu tiên 2. Khi dùng `PriorityQueue`, người viết chương trình có thể mong đợi A rồi B rồi C được lấy ra, vì không có khác biệt về mức ưu tiên.

Nhưng mong đợi ấy chưa có trong quy tắc so sánh.

Nếu comparator chỉ xét mức ưu tiên, nó chỉ nói rằng A, B, C **đồng hạng**. Nó chưa nói phải chọn ai trước trong nhóm đó. `PriorityQueue` không tự bổ sung quy tắc “đến trước thì đi trước” cho những phần tử đồng hạng.

Bài này giải quyết ba câu hỏi:

- Khi nào hai phần tử được coi là đồng hạng?
- Vì sao hàng đợi ưu tiên không mặc nhiên ổn định?
- Làm thế nào dùng khóa phụ để có thứ tự đến trước trong từng nhóm ưu tiên?

Mục tiêu là viết được một quy tắc chọn đầu đầy đủ và kiểm chứng được nó. Các ví dụ dùng Java 17+, chạy trong một luồng và giữ khóa so sánh không đổi khi phần tử đang nằm trong hàng đợi.

## 2. Đồng hạng được xác định bởi phép so sánh

### 2.1. compare bằng 0 có nghĩa gì?

Giả sử một công việc có tên và mức ưu tiên. Quy ước của ví dụ là số ưu tiên nhỏ hơn được làm trước:

```java
// Các khai báo minh họa đặt trong một lớp:
record Job(String name, int priority) {}

Comparator<Job> byPriority = Comparator.comparingInt(Job::priority);
```

Cần import `java.util.Comparator`. Với `Job("A", 2)` và `Job("B", 2)`, comparator trả 0 vì nó chỉ đọc trường `priority`.

Bốn công việc có thể được chia thành nhóm:

| Công việc | Mức ưu tiên | Quan hệ do comparator quyết định |
| --- | --- | --- |
| urgent | 1 | Đứng trước mọi công việc mức 2 |
| A | 2 | Đồng hạng với B và C |
| B | 2 | Đồng hạng với A và C |
| C | 2 | Đồng hạng với A và B |

Trong nhóm mức 2, comparator hiện tại không chọn A, B hay C trước. Thứ tự các nhóm được xác định; thứ tự bên trong nhóm chưa được phân biệt.

Nếu dùng thứ tự tự nhiên qua `Comparable`, quan hệ đồng hạng tương tự đến từ `compareTo(...) == 0`. Nó không bắt buộc phải đến từ một comparator truyền riêng.

### 2.2. Đồng hạng không nhất thiết là bằng nhau theo equals

Hai Job có tên khác nhau nhưng cùng priority có thể thỏa:

```text
compare(A, B) == 0
A.equals(B) == false
```

Record `Job` mặc định xét cả `name` và `priority` trong phép bằng. Comparator ở trên chỉ xét một trường. Hai quan hệ phục vụ hai câu hỏi khác nhau:

| Quan hệ | Câu hỏi |
| --- | --- |
| `compare(a, b) == 0` | Hai phần tử có cùng hạng theo quy tắc thứ tự này không? |
| `a.equals(b)` | Hai đối tượng có bằng nhau theo mô hình dữ liệu không? |
| `a == b` | Hai tham chiếu có trỏ tới cùng đối tượng không? |

Không thay thế ba câu hỏi cho nhau.

Với `PriorityQueue`, đồng hạng không có nghĩa phần tử thứ hai bị loại bỏ. Hàng đợi vẫn có thể chứa nhiều đối tượng cùng hạng, nhiều đối tượng bằng nhau, hoặc nhiều lần xuất hiện của cùng một tham chiếu.

### 2.3. Tìm/xóa theo đối tượng còn dùng phép bằng

`contains(Object)` và `remove(Object)` tìm theo phép bằng của đối tượng. Chúng không dùng “cùng mức ưu tiên” làm định nghĩa của việc tìm thấy.

Nếu hàng đợi có A mức 2, tìm một Job X mức 2 chưa chắc trả `true`. Nếu Job bằng nhau theo cả tên và mức, X khác A nên không khớp.

Hợp đồng của lớp về đồng hạng, giữ phần tử và các thao tác theo đối tượng được mô tả trong [PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

## 3. Tính ổn định: giữ thứ tự tương đối giữa các phần tử đồng hạng

### 3.1. Ổn định là một yêu cầu bổ sung

Trong ngữ cảnh này, mong muốn “ổn định” là: nếu hai lần gửi công việc có cùng mức ưu tiên, lần được tiếp nhận trước phải được lấy trước.

Ví dụ thêm theo thứ tự:

```text
A mức 2 → urgent mức 1 → B mức 2 → C mức 2
```

Một thứ tự lấy đáp ứng ưu tiên và FIFO trong nhóm là:

```text
urgent → A → B → C
```

Đây không phải FIFO toàn hàng đợi: urgent đến sau A nhưng vẫn được làm trước vì mức ưu tiên tốt hơn. Tính đến trước chỉ phân xử những phần tử hòa theo tiêu chí chính.

### 3.2. PriorityQueue không cam kết giữ FIFO trong nhóm

Nếu có nhiều phần tử cùng là nhỏ nhất theo thứ tự đang dùng, đầu hàng đợi có thể là một trong số đó. Không có bảo đảm chọn theo thứ tự chèn.

Một vài dữ liệu có thể vẫn cho kết quả đúng thứ tự đến. Điều đó chỉ cho thấy lần chạy ấy phù hợp, không chứng minh hàng đợi có hợp đồng ổn định.

Cũng không nên đổi phát biểu thành “PriorityQueue luôn đảo thứ tự đồng hạng” hoặc “luôn ngẫu nhiên”. Cả hai đều là những quy tắc không được lớp cam kết.

### 3.3. Vì sao heap có thể thay vị trí các phần tử hòa?

Một mô hình heap với ba công việc cùng mức:

```text
        A:2
       /   \
     B:2   C:2
```

Sau khi lấy A, một cách duy trì heap đưa phần tử cuối C lên gốc:

```text
        C:2
       /
     B:2
```

Vì C và B so sánh bằng 0, tính chất heap vẫn đúng. Heap không có thông tin rằng B được thêm trước C, nên không có điều kiện bắt buộc phải đổi C xuống dưới B.

Một cách xử lý hợp lệ vì thế có thể lấy A, C, B. Đây là minh họa vì sao bất biến heap và ổn định theo lịch sử chèn là hai yêu cầu khác nhau; không phải một lời hứa về bố trí mà mọi JDK phải dùng.

## 4. Chương trình quan sát: đồng hạng vẫn có thể đổi thứ tự

Lưu thành **EqualPriorityObservation.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class EqualPriorityObservation {
    record Job(String name, int priority) {}

    private static List<String> pollNames(PriorityQueue<Job> queue) {
        List<String> names = new ArrayList<>();
        while (!queue.isEmpty()) {
            names.add(queue.poll().name());
        }
        return names;
    }

    public static void main(String[] args) {
        Comparator<Job> byPriority = Comparator.comparingInt(Job::priority);
        Job a = new Job("A", 2);
        Job b = new Job("B", 2);
        Job c = new Job("C", 2);

        System.out.println("compare A and B: " + byPriority.compare(a, b));
        System.out.println("A equals B: " + a.equals(b));

        PriorityQueue<Job> observation = new PriorityQueue<>(byPriority);
        observation.addAll(List.of(a, b, c));
        System.out.println("inserted names: [A, B, C]");
        System.out.println("observed tied poll order: " + pollNames(observation));

        PriorityQueue<Job> lookup = new PriorityQueue<>(byPriority);
        lookup.addAll(List.of(a, b, c));
        System.out.println("size with three ties: " + lookup.size());
        System.out.println("contains equal B: " + lookup.contains(new Job("B", 2)));
        System.out.println("contains same-priority X: " + lookup.contains(new Job("X", 2)));
        System.out.println("remove same-priority X: " + lookup.remove(new Job("X", 2)));
        System.out.println("remove equal B: " + lookup.remove(new Job("B", 2)));
        System.out.println("size after removing B: " + lookup.size());

        PriorityQueue<Job> duplicates = new PriorityQueue<>(byPriority);
        duplicates.offer(a);
        duplicates.offer(a);
        System.out.println("same reference twice size: " + duplicates.size());
        System.out.println("same reference twice names: " + pollNames(duplicates));
    }
}
```

Biên dịch và chạy:

```shell
javac --release 17 -encoding UTF-8 EqualPriorityObservation.java
java EqualPriorityObservation
```

Chương trình đã được biên dịch bằng `--release 17` và chạy trên OpenJDK 24.0.2. Stdout quan sát được:

```text
compare A and B: 0
A equals B: false
inserted names: [A, B, C]
observed tied poll order: [A, C, B]
size with three ties: 3
contains equal B: true
contains same-priority X: false
remove same-priority X: false
remove equal B: true
size after removing B: 2
same reference twice size: 2
same reference twice names: [A, A]
```

Dòng `observed tied poll order` ghi kết quả thực tế trên môi trường đó, không phải thứ tự cố định mà API bảo đảm trên mọi Java 17+. Cờ `--release 17` kiểm tra ngôn ngữ/API mục tiêu và tạo bytecode tương ứng; JVM chạy ví dụ vẫn là bản đã nêu.

Kết quả cần hiểu:

- A và B đồng hạng nhưng không bằng nhau theo record.
- Cả ba phần tử đồng hạng đều được giữ.
- Thứ tự quan sát A, C, B là một phản ví dụ cho giả định FIFO tự động.
- Tạo lại một Job bằng B vẫn tìm/xóa được B.
- Tạo một Job khác tên nhưng cùng priority không làm `contains` hay `remove` coi là có.
- Thêm cùng một tham chiếu hai lần tạo hai lần xuất hiện, không tự loại trùng.

Không dùng kiểm thử yêu cầu chính xác A, C, B để mô tả hợp đồng của PriorityQueue. Khi chưa có tiêu chí phụ, thứ tự danh tính của nhóm đồng hạng chưa được quy định.

## 5. Khóa phụ biến yêu cầu ngầm thành quy tắc tường minh

### 5.1. Từ một khóa thành một bộ khóa

Nếu cần FIFO trong nhóm, cấp cho mỗi lần tiếp nhận một số thứ tự tăng dần:

```text
khóa của một lần gửi = (mức ưu tiên, số thứ tự tiếp nhận)
```

So sánh theo thứ tự từ trái sang phải:

1. Nếu mức ưu tiên khác nhau, dùng mức ưu tiên để chọn.
2. Nếu mức ưu tiên bằng nhau, dùng số thứ tự.
3. Chỉ hòa hoàn toàn nếu mọi tiêu chí đang xét đều hòa.

Ví dụ:

| Lần gửi | Mức ưu tiên | Số thứ tự | Thứ tự lấy mong đợi |
| --- | --- | --- | --- |
| A | 2 | 0 | Sau urgent, trước B |
| urgent | 1 | 1 | Đầu tiên |
| B | 2 | 2 | Sau A, trước C |
| C | 2 | 3 | Cuối cùng trong nhóm mức 2 |

Các khóa `(2,0)`, `(1,1)`, `(2,2)`, `(2,3)` cho thứ tự `(1,1)`, `(2,0)`, `(2,2)`, `(2,3)`.

Khi số thứ tự đủ phân biệt các lần gửi còn trong hàng đợi, hai lần gửi cùng mức không còn đồng hạng theo **comparator hoàn chỉnh** nữa. Heap không cần tự nhớ lịch sử; lịch sử cần thiết đã trở thành một phần của khóa.

### 5.2. Cách ghép bằng thenComparingLong

Một mô hình dữ liệu trực tiếp:

```java
// Các khai báo minh họa đặt trong một lớp:
record QueuedJob(String name, int priority, long receivedOrder) {}

Comparator<QueuedJob> order =
        Comparator.comparingInt(QueuedJob::priority)
                  .thenComparingLong(QueuedJob::receivedOrder);
```

`thenComparingLong` chỉ được dùng khi tiêu chí trước hòa. Không phải cộng hai giá trị thành một điểm tổng.

Chẳng hạn, không viết `priority + receivedOrder` rồi so vì một số thứ tự lớn có thể làm đảo quyết định về mức ưu tiên. Quy tắc “ưu tiên là chính, số thứ tự chỉ phân xử hòa” phải được giữ bằng cách so lần lượt từng khóa.

### 5.3. Đảo chiều đúng tiêu chí

Nếu số ưu tiên lớn hơn được làm trước, nhưng số thứ tự nhỏ hơn vẫn được làm trước khi hòa:

```java
Comparator<QueuedJob> higherPriorityFirst =
        Comparator.comparingInt(QueuedJob::priority)
                  .reversed()
                  .thenComparingLong(QueuedJob::receivedOrder);
```

Nếu viết:

```java
// Khác yêu cầu: đảo cả thứ tự tiếp nhận.
Comparator<QueuedJob> reversedEverything =
        Comparator.comparingInt(QueuedJob::priority)
                  .thenComparingLong(QueuedJob::receivedOrder)
                  .reversed();
```

thì cả tiêu chí phụ cũng bị đảo. Trong nhóm cùng priority, lần có số thứ tự lớn hơn sẽ đi trước.

Quy tắc dấu, tính bắc cầu và ghép tiêu chí được mô tả trong [Comparator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

## 6. Đặt số thứ tự ở công việc hay ở lần gửi?

### 6.1. Một đối tượng có thể được gửi nhiều lần

Nếu gắn một số thứ tự cố định vào chính công việc rồi thêm lại cùng đối tượng, hai lần xuất hiện có thể dùng cùng khóa phụ. Khi đó chúng lại hòa theo comparator hoàn chỉnh.

Câu hỏi cần trả lời là: đang xếp các **công việc** hay xếp các **lần tiếp nhận công việc**?

Đối với FIFO trong nhóm theo lần tiếp nhận, số thứ tự thường thuộc về một bản ghi bao ngoài:

```text
Entry {
    value: công việc,
    sequence: số của lần tiếp nhận này
}
```

Cùng một `value` có thể xuất hiện trong nhiều Entry, mỗi Entry có sequence khác nhau. Cấu trúc dữ liệu không tự loại bỏ các lần gửi lại chỉ vì payload giống nhau.

### 6.2. Thời điểm cấp số là một phần của hợp đồng

Trong ví dụ một luồng, mỗi lời gọi `offer` hợp lệ cấp số kế tiếp ngay khi tiếp nhận công việc. Vì mọi lời gọi diễn ra nối tiếp, số nhỏ hơn tương ứng với lần tiếp nhận trước.

Nếu ứng dụng cấp số từ một danh sách công việc đã bị sắp xếp trước đó, số ấy mô tả thứ tự đang duyệt danh sách, không tự khôi phục lịch sử gốc đã mất.

Nếu dữ liệu được tải lại từ nơi lưu trữ, cần xác định muốn giữ số tiếp nhận cũ hay coi việc tải lại là những lần tiếp nhận mới. Không đặt một tên trường `sequence` rồi mặc nhiên coi nó có mọi ý nghĩa thời gian cần thiết.

## 7. Chương trình chạy: bao công việc bằng khóa thứ tự tiếp nhận

Lưu thành **StablePriorityQueueDemo.java**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

public class StablePriorityQueueDemo {
    record Job(String name, int priority) {}
    private record Entry<E>(E value, long sequence) {}

    static final class StableQueue<E> {
        private final PriorityQueue<Entry<E>> entries;
        private long nextSequence;

        StableQueue(Comparator<? super E> byPriority) {
            Objects.requireNonNull(byPriority);
            entries = new PriorityQueue<>((left, right) -> {
                int result = byPriority.compare(left.value(), right.value());
                return result != 0
                        ? result
                        : Long.compare(left.sequence(), right.sequence());
            });
        }

        void offer(E value) {
            Objects.requireNonNull(value);
            if (nextSequence == Long.MAX_VALUE) {
                throw new IllegalStateException("sequence exhausted");
            }
            entries.offer(new Entry<>(value, nextSequence));
            nextSequence++;
        }

        E peek() {
            Entry<E> head = entries.peek();
            return head == null ? null : head.value();
        }

        E poll() {
            Entry<E> head = entries.poll();
            return head == null ? null : head.value();
        }

        int size() {
            return entries.size();
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    private static List<String> pollNames(StableQueue<Job> queue) {
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            result.add(queue.poll().name());
        }
        return result;
    }

    public static void main(String[] args) {
        StableQueue<Job> queue =
                new StableQueue<>(Comparator.comparingInt(Job::priority));
        queue.offer(new Job("slow-A", 3));
        queue.offer(new Job("urgent-A", 1));
        queue.offer(new Job("normal", 2));
        queue.offer(new Job("urgent-B", 1));
        queue.offer(new Job("slow-B", 3));
        queue.offer(new Job("urgent-C", 1));

        System.out.println("size: " + queue.size());
        System.out.println("head: " + queue.peek().name());
        System.out.println("stable poll names: " + pollNames(queue));

        Job a = new Job("A", 1);
        queue.offer(a);
        queue.offer(new Job("B", 1));
        System.out.println("removed before resubmit: " + queue.poll().name());
        queue.offer(a);
        System.out.println("after resubmit names: " + pollNames(queue));

        queue.offer(a);
        queue.offer(a);
        System.out.println("duplicate payload size: " + queue.size());
        System.out.println("duplicate payload names: " + pollNames(queue));
        System.out.println("empty peek: " + queue.peek());
        System.out.println("empty poll: " + queue.poll());
    }
}
```

Lệnh chạy:

```shell
javac --release 17 -encoding UTF-8 StablePriorityQueueDemo.java
java StablePriorityQueueDemo
```

Stdout chính xác:

```text
size: 6
head: urgent-A
stable poll names: [urgent-A, urgent-B, urgent-C, normal, slow-A, slow-B]
removed before resubmit: A
after resubmit names: [B, A]
duplicate payload size: 2
duplicate payload names: [A, A]
empty peek: null
empty poll: null
```

`StableQueue` trong ví dụ là một lớp nhỏ minh họa chính sách một luồng, không triển khai toàn bộ giao diện `Queue`. Những phương thức nó có đều thể hiện mục tiêu: tiếp nhận, xem/lấy đầu và kiểm tra kích thước.

Phần bao Entry giải quyết việc thứ tự thuộc về **lần gửi**, còn Job giữ dữ liệu công việc. Comparator của Entry trước tiên ủy quyền so mức ưu tiên cho `byPriority`; nếu hòa mới so sequence.

`Objects.requireNonNull` từ chối payload null. Do đó null từ `peek()` hoặc `poll()` biểu thị hàng đợi rỗng, không lẫn với một công việc null đang chờ.

Mã chặn việc tiếp nhận khi số kế tiếp bằng `Long.MAX_VALUE`, trước khi bộ đếm có thể quay vòng. Chính sách này cố ý không sử dụng giá trị cuối đó; nó chọn báo hết dải số thay vì âm thầm tạo khóa thứ tự sai.

## 8. Đọc từng nhóm trong kết quả

Sáu lời gọi đầu tiên cấp số:

| Công việc | Priority | Sequence | Khóa so sánh |
| --- | --- | --- | --- |
| slow-A | 3 | 0 | `(3,0)` |
| urgent-A | 1 | 1 | `(1,1)` |
| normal | 2 | 2 | `(2,2)` |
| urgent-B | 1 | 3 | `(1,3)` |
| slow-B | 3 | 4 | `(3,4)` |
| urgent-C | 1 | 5 | `(1,5)` |

Khi lấy ra:

```text
Nhóm priority 1: urgent-A → urgent-B → urgent-C
Nhóm priority 2: normal
Nhóm priority 3: slow-A → slow-B
```

slow-A được gửi đầu tiên nhưng không đứng đầu toàn hàng đợi, vì priority 3 kém ưu tiên hơn 1 và 2 theo quy ước này. Trong nhóm priority 3, slow-A vẫn đứng trước slow-B.

Đoạn gửi lại A thể hiện một chính sách khác cần nói rõ:

```text
tiếp nhận A → tiếp nhận B → lấy A → tiếp nhận A lần mới
```

Sau đó B phải đi trước lần A mới, nên kết quả là `[B, A]`. Đối tượng A có thể vẫn là cùng tham chiếu, nhưng Entry mới nhận sequence mới.

Lớp không đặt bộ đếm về 0 mỗi khi hàng đợi rỗng. Điều này giúp mô hình tiếp nhận đơn giản, không phải quản lý lại sự trùng số. Nếu một thiết kế khác muốn tái sử dụng số, nó phải bảo đảm không tạo khóa đụng với các lần gửi vẫn còn liên quan.

## 9. Chọn khóa phụ đúng ý nghĩa nghiệp vụ

### 9.1. Một khóa phụ không nhất thiết mang nghĩa FIFO

| Khóa phụ | Chính sách nó có thể diễn đạt | Có tự bảo đảm FIFO không? |
| --- | --- | --- |
| Số thứ tự tiếp nhận tăng dần | Đến trước trong cùng nhóm | Có, nếu cấp số đúng và đủ phân biệt |
| Hạn chót | Việc đến hạn sớm hơn trong cùng nhóm | Không nhất thiết |
| Tên công việc | Thứ tự từ vựng của tên | Không |
| Mã duy nhất | Một thứ tự xác định theo mã | Không nhất thiết |
| Dấu thời gian | Thứ tự theo giá trị thời gian được ghi | Chưa đủ nếu trùng hoặc không theo thứ tự tiếp nhận |

Khóa phụ giúp chọn người thắng khi tiêu chí trước hòa. Việc chọn khóa nào là một quyết định nghiệp vụ, không phải mẹo kỹ thuật có một đáp án cho mọi ứng dụng.

Một mã duy nhất giúp phân biệt hai công việc nhưng không tự nói công việc nào đến trước. Một dấu thời gian có thể trùng nhau giữa nhiều lần gửi; nguồn thời gian cũng có thể có giới hạn độ phân giải hoặc điều chỉnh.

Nếu nghiệp vụ thật sự yêu cầu “ưu tiên, sau đó hạn chót, cuối cùng thứ tự đến”, comparator phải xét đúng ba tiêu chí theo thứ tự ấy. Sequence lúc này chỉ phân xử khi cả ưu tiên và hạn chót đều hòa.

### 9.2. Vẫn có thể hòa ở tiêu chí cuối

Hai Entry có cùng priority và cùng sequence vẫn có thể so sánh bằng 0. Tên gọi “khóa phụ” không tự làm khóa trở thành duy nhất.

Nếu yêu cầu là thứ tự xác định giữa mọi lần gửi còn trong hàng đợi, cần một tiêu chí cuối đủ phân biệt hoặc một chính sách rõ ràng cho phần còn hòa. Ví dụ StableQueue làm điều đó bằng sequence không lặp trong các lời gọi được chấp nhận.

Không dùng số ngẫu nhiên để diễn đạt “đến trước”. Không dùng `System.identityHashCode` như thể nó là lịch sử tiếp nhận hoặc một mã chắc chắn không trùng. Hai giá trị đó không mang hợp đồng FIFO mà ứng dụng cần.

### 9.3. Giữ khóa ổn định khi đang chờ

Nếu priority hoặc sequence được đổi trực tiếp sau khi Entry đã nằm trong heap, vị trí hiện tại có thể không còn phù hợp. Hàng đợi không theo dõi mọi phép gán trường để tự sửa cấu trúc.

Hãy dùng dữ liệu khóa bất biến hoặc cập nhật qua một quy trình lấy/xóa phần tử cũ rồi thêm giá trị mới. Việc đổi ưu tiên còn đặt ra câu hỏi: giữ thứ tự tiếp nhận ban đầu hay cấp số mới? Cả hai có thể hợp lệ trong những nghiệp vụ khác nhau, nhưng phải chọn rõ.

Trong ví dụ, record Entry giữ sequence bất biến; Job chứa String và int cũng không đổi. Một payload khác có trường thay đổi được vẫn cần quy tắc riêng nếu comparator đọc trường ấy.

## 10. Comparator phải là một quy tắc nhất quán

### 10.1. Không tạo khóa phụ bằng phép trừ rồi ép kiểu

Ví dụ cần tránh khi sequence là long:

```java
// Ví dụ sai về cách so sánh:
int result = (int) (left.sequence() - right.sequence());
```

Phép trừ có thể tràn; ép từ long xuống int còn có thể mất thông tin. Khoảng cách lớn không liên quan đến yêu cầu chọn dấu so sánh.

Dùng:

```java
int result = Long.compare(left.sequence(), right.sequence());
```

Với priority kiểu int, dùng `Integer.compare` hoặc `Comparator.comparingInt`. Không viết `a.priority() - b.priority()` nếu muốn quy tắc đúng trên toàn miền int.

### 10.2. Không tính thứ tự phụ dựa trên trạng thái thay đổi mỗi lần gọi

Một comparator phải cho quan hệ nhất quán trong thời gian các khóa đang có hiệu lực. Những cách như “nếu hòa thì tăng bộ đếm ngay trong compare” hoặc “nếu hòa thì chọn ngẫu nhiên” không mã hóa lịch sử tiếp nhận.

Comparator có thể được gọi nhiều lần cho cùng cặp, ở các thời điểm và hướng khác nhau. Tạo sequence trong `offer`, lưu nó vào Entry, rồi chỉ đọc trong `compare`. Không tạo một sequence mới mỗi lần hai phần tử được so.

Nếu comparator không có tính bắc cầu hoặc không đảo dấu phù hợp khi đổi hai đối số, heap không có một thứ tự hợp lệ để duy trì. Thêm nhiều khóa không sửa được một quy tắc so sánh vốn mâu thuẫn.

## 11. Sort ổn định và PriorityQueue ổn định là hai hợp đồng khác nhau

`List.sort` có hợp đồng sắp xếp ổn định: các phần tử comparator coi là hòa giữ thứ tự tương đối của đầu vào. Xem [List.sort Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)).

Nhưng nếu đầu vào danh sách lấy từ iterator của PriorityQueue, thứ tự đó không được cam kết là lịch sử tiếp nhận. Sort ổn định chỉ giữ được thứ tự đầu vào nó nhìn thấy; nó không khôi phục lịch sử đã không được lưu.

Ví dụ:

```text
Lịch sử tiếp nhận:             A, B, C
Danh sách đầu vào một lần đọc: A, C, B
Cả ba có cùng priority
Sort ổn định chỉ theo priority:
                              A, C, B
```

Không có mâu thuẫn: phép sort đã ổn định đối với danh sách đầu vào, còn danh sách ấy không diễn đạt FIFO mong muốn.

Khi dùng khóa phụ sequence, lấy đầu bằng `poll()` cho thứ tự phù hợp. Một danh sách được sort bằng cùng comparator hoàn chỉnh cũng có cùng thứ tự đối với những Entry có khóa đủ phân biệt.

Iterator của PriorityQueue vẫn không tự duyệt theo ưu tiên sau khi đã thêm sequence. Khóa phụ sửa quy tắc chọn đầu, không thay đổi hợp đồng của iterator. Nếu cần xem theo thứ tự lấy, hãy sao chép rồi poll hoặc tạo danh sách và sort bằng đúng comparator.

## 12. Một luồng, nhiều luồng và ý nghĩa “đến trước”

Ví dụ StableQueue dùng bộ đếm long thông thường và PriorityQueue thông thường. Nó được thiết kế cho một luồng. Thay bộ đếm bằng `AtomicLong` không tự làm PriorityQueue trở thành an toàn cho nhiều luồng.

Ngay cả khi dùng một cấu trúc hàng đợi đồng thời thích hợp, cần nói rõ thứ tự nào được ghi nhận. Ví dụ:

```text
Luồng A lấy sequence 10 rồi bị tạm dừng.
Luồng B lấy sequence 11 và thêm vào hàng đợi trước.
Luồng A mới thêm sequence 10 sau đó.
```

Thứ tự cấp số là A trước B; thứ tự hoàn thành thao tác thêm là B trước A. Không được gọi cả hai là cùng một “thứ tự đến” mà không định nghĩa điểm tiếp nhận.

Nếu B đã được lấy ra trước khi A thực sự xuất hiện trong hàng đợi, một sequence nhỏ hơn được thêm sau không thể làm lại phần xử lý đã xảy ra. Quy tắc ưu tiên áp dụng trên các phần tử hiện đang chờ, không sắp xếp lại quá khứ.

`PriorityBlockingQueue` cũng không mặc định bảo đảm thứ tự giữa các phần tử bằng mức; tài liệu của nó gợi ý dùng một khóa phụ khi cần phân xử. Xem [PriorityBlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html).

Thiết kế tiếp nhận đồng thời cần chọn điểm ghi nhận thứ tự và cơ chế đồng bộ phù hợp. Phần này nêu ranh giới của ví dụ; không coi lớp StableQueue một luồng là một bộ lập lịch đồng thời hoàn chỉnh.

## 13. Kiểm thử điều cần bảo đảm

### 13.1. Khi chỉ có comparator ưu tiên

Đừng yêu cầu một thứ tự danh tính cụ thể cho nhóm hòa. Có thể kiểm tra:

- Mức ưu tiên lấy ra không đi ngược quy tắc.
- Không mất hoặc tự sinh thêm lần xuất hiện.
- Những phần tử khác nhóm tuân theo tiêu chí chính.
- Các thao tác tìm/xóa theo đối tượng dùng đúng mô hình bằng nhau.

Thấy A, B, C trên một lần chạy không đủ chứng minh ổn định; thấy A, C, B cũng không có nghĩa dữ liệu bị hỏng.

### 13.2. Khi đã có sequence

Có thể kiểm tra mạnh hơn:

- Trong cùng priority, sequence lấy ra tăng theo chính sách đã chọn.
- Priority vẫn là tiêu chí chính, sequence không lấn át nó.
- Mỗi lần gửi mới nhận khóa phụ phù hợp, kể cả cùng payload.
- Gửi lại một công việc có hành vi đúng với quy tắc giữ số cũ hay cấp số mới.
- Trường hợp tất cả đồng hạng vẫn giữ thứ tự tiếp nhận.
- Dữ liệu gần giới hạn số không làm comparator đổi dấu sai.

Các trường hợp biên hữu ích gồm hàng đợi rỗng, một phần tử, nhiều phần tử cùng mức, nhiều mức đan xen, gửi cùng tham chiếu nhiều lần và lấy ra rồi gửi lại.

Một lớp bao có thể che Entry để mã gọi chỉ nhìn payload; khi kiểm thử tính ổn định, có thể dùng các nhãn riêng cho từng lần gửi để biết chúng có được lấy đúng thứ tự hay không. Payload giống hệt nhau không giúp quan sát danh tính các lần xuất hiện chỉ qua chuỗi in ra.

## 14. Lỗi phổ biến và cách sửa

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Cho rằng cùng ưu tiên tự FIFO | Comparator chưa ghi lịch sử chèn | Thêm khóa tiếp nhận |
| Cho rằng compare bằng 0 sẽ loại phần tử sau | PriorityQueue không phải tập hợp | Phân biệt hòa thứ tự với loại trùng |
| Dùng equals để dự đoán ai đứng đầu | Equals không quyết định thứ tự heap | Đọc comparator hoàn chỉnh |
| Gắn cùng sequence cho hai lần gửi | Hai lần vẫn có thể hòa | Cấp khóa cho lần tiếp nhận |
| Dùng timestamp như khóa chắc chắn duy nhất | Dấu thời gian có thể trùng | Có tiêu chí cuối đủ phân biệt |
| Cộng priority với sequence | Tiêu chí phụ có thể lấn tiêu chí chính | So lần lượt bằng thenComparing |
| Đảo cả comparator ghép | Đảo luôn thứ tự FIFO trong nhóm | Đảo đúng tiêu chí cần đảo |
| Tạo sequence bên trong compare | Kết quả phụ thuộc số lần so sánh | Cấp số khi tiếp nhận và giữ bất biến |
| Ép chênh lệch long sang int | Mất dấu hoặc mất thông tin | Dùng Long.compare |
| Sort ổn định từ iterator để phục hồi FIFO | Lịch sử đến không nằm trong iterator | Lưu sequence ngay từ nguồn |
| Thêm AtomicLong rồi chia sẻ PriorityQueue | Bộ đếm và cấu trúc có yêu cầu đồng bộ riêng | Thiết kế tiếp nhận đồng thời rõ ràng |

## 15. Bài tập tự kiểm tra có lời giải

### Bài 1: Ba quan hệ

A và B là hai record Job khác tên, cùng priority 2. Comparator chỉ xét priority. `compare(A,B)`, `A.equals(B)` và `A == B` có thể lần lượt là gì?

**Lời giải:** 0, false, false. Đồng hạng không có nghĩa bằng toàn bộ dữ liệu hay cùng tham chiếu. PriorityQueue vẫn có thể chứa cả hai.

### Bài 2: Chọn đầu trong nhóm hòa

Thêm A, B, C cùng mức vào PriorityQueue chỉ so priority. Một lần chạy lấy A, C, B. Đây có phải vi phạm API không?

**Lời giải:** không. Cả ba đồng hạng và lớp không cam kết FIFO trong nhóm. Cũng không được biến kết quả này thành một thứ tự cố định cho mọi lần chạy.

### Bài 3: So bộ khóa

Các lần gửi có khóa `A=(2,0)`, `B=(1,1)`, `C=(2,2)`, `D=(1,3)`. Số priority nhỏ hơn đi trước; khi hòa, sequence nhỏ hơn đi trước. Thứ tự lấy là gì?

**Lời giải:** B, D, A, C. Tiêu chí chính chọn nhóm priority 1 trước; trong nhóm ấy 1 trước 3. Sau đó nhóm priority 2 có 0 trước 2.

### Bài 4: Đảo chiều

Cần priority lớn trước và sequence nhỏ trước. Hai cách “đảo priority rồi ghép sequence” và “ghép cả hai rồi đảo” có tương đương không?

**Lời giải:** không. Cách sau đảo cả sequence, nên trong cùng priority sẽ lấy lần có số lớn hơn trước. Phải đảo tiêu chí priority trước khi ghép sequence tăng dần.

### Bài 5: Gửi lại cùng đối tượng

A và B cùng mức, được tiếp nhận theo thứ tự A rồi B. Lấy A ra, sau đó gửi lại A bằng lớp StableQueue của bài. Ai đi trước tiếp theo?

**Lời giải:** B. Lần gửi lại A có Entry và sequence mới. Nếu nghiệp vụ muốn giữ tuổi chờ cũ, đó là một chính sách khác phải được thiết kế riêng.

### Bài 6: Khóa phụ là tên

Dùng `thenComparing(Job::name)` có tạo FIFO trong nhóm không?

**Lời giải:** không. Nó tạo thứ tự theo tên khi priority hòa. A có thể đứng trước B vì tên, dù B đến trước. Nếu muốn FIFO, khóa phụ phải ghi nhận thứ tự tiếp nhận.

### Bài 7: Sort ổn định

Danh sách được đọc từ một PriorityQueue có thứ tự A, C, B; cả ba cùng priority. Sort ổn định chỉ theo priority có bắt buộc trả A, B, C không?

**Lời giải:** không. Nó giữ A, C, B của đầu vào. Tính ổn định của sort không khôi phục lịch sử chèn vào một cấu trúc khác.

### Bài 8: Hai điểm thứ tự trong nhiều luồng

A được cấp số 10 nhưng chưa thêm vào hàng đợi. B được cấp 11 và thêm trước. “A đến trước” có phải phát biểu đủ rõ không?

**Lời giải:** chưa đủ. Phải nói đang lấy thứ tự cấp số, thứ tự bắt đầu hay hoàn thành tiếp nhận, hoặc một điểm đồng bộ cụ thể. Khóa phụ chỉ có ý nghĩa khi ứng dụng định nghĩa nó ghi lại thứ tự nào.

## 16. Tài liệu đối chiếu

- [PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html): hợp đồng hòa ưu tiên và thao tác theo đối tượng.
- [Comparator Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): phép so sánh và ghép tiêu chí.
- [List.sort Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#sort(java.util.Comparator)): hợp đồng ổn định của phép sắp xếp danh sách.
- [PriorityBlockingQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html): giới hạn thứ tự giữa các phần tử cùng mức và ý tưởng phân xử bằng khóa phụ.

Muốn có thứ tự cụ thể trong nhóm đồng hạng, hãy ghi dữ liệu phân xử vào khóa và so sánh nó rõ ràng. Với yêu cầu FIFO theo lần tiếp nhận, sequence là lịch sử được lưu lại để comparator sử dụng, không phải một đặc tính tự phát của heap.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="priority-queue-007">Rủi ro sửa độ ưu tiên của đối tượng đang nằm trong queue</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro sửa độ ưu tiên của đối tượng đang nằm trong queue

## 1. Setter có thể đổi dữ liệu mà không đổi vị trí trong heap

Giả sử một PriorityQueue chọn công việc có số priority nhỏ trước. Nó đang giữ A:10, B:20, C:30, D:40 và E:50. Sau đó, mã gọi sửa chính đối tượng D thành D:1.

Theo giá trị hiện tại, D phải được ưu tiên trước A. Nhưng PriorityQueue không tự nghe mọi setter của phần tử để đưa D lên đầu. Đối tượng vẫn nằm ở vị trí được chọn khi nó còn có priority 40.

Có ba tầng cần giữ riêng:

```text
tham chiếu được queue lưu
        |
        v
đối tượng và dữ liệu hiện tại của nó
        |
        v
quan hệ thứ tự mà comparator đọc

vị trí trong heap đã được tổ chức ở một thời điểm trước
```

Sửa trường của đối tượng thay đổi dữ liệu mà comparator có thể đọc, nhưng không tự thực hiện offer, remove hoặc một thao tác cập nhật heap.

Hậu quả thường là chọn sai công việc theo ưu tiên hiện tại, trong khi size vẫn đúng và đối tượng vẫn tồn tại trong queue.

Bài này giải thích rủi ro ấy, cách nhận biết và những cách cập nhật có thể kiểm soát. Các hợp đồng API liên quan được đối chiếu với [PriorityQueue Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

## 2. Bất biến heap cần đúng trước mỗi thao tác tiếp theo

Với min-heap theo số priority tăng dần, một bất biến cơ bản là:

```text
priority của cha <= priority của mỗi con
```

Một cấu trúc minh họa hợp lệ:

```text
             A:10
            /    \
         B:20    C:30
         /  \
      D:40  E:50
```

Sửa D:40 thành D:1 nhưng không cập nhật cấu trúc:

```text
             A:10
            /    \
         B:20    C:30
         /  \
       D:1  E:50
```

Quan hệ B:20 đứng trước D:1 không còn đúng. Vì D chưa được đưa lên, gốc A:10 cũng không còn là phần tử nhỏ nhất theo các giá trị hiện tại.

Đây là hình minh họa phù hợp với thí nghiệm tiếp theo, không phải hợp đồng về hình dạng nội bộ trên mọi JDK.

### 2.1. Đổi theo cả hai hướng đều có thể gây lỗi

Nếu giảm số priority của một phần tử phía dưới, nó có thể cần đi lên. Nếu tăng số priority của phần tử ở đầu, nó có thể cần đi xuống.

Ví dụ, gốc A:10 đổi thành A:100 có thể vẫn được peek trả về trước B:20, vì thao tác sửa trường không tự thực hiện sift-down.

Nên nói rõ quy ước: trong bài, **số nhỏ hơn được lấy trước**. Cụm “tăng ưu tiên” trong nghiệp vụ đôi khi lại có nghĩa giảm con số; tránh dùng từ mơ hồ khi giải thích thay đổi.

### 2.2. Một lần sửa có thể tình cờ chưa làm sai quan hệ

Không phải mọi giá trị thay đổi đều lập tức cho output sai trong mọi bố trí. Một phần tử có thể vẫn còn nằm trong miền hợp lệ giữa cha và con sau thay đổi nhỏ.

Điều đó không tạo ra một cơ chế tự cập nhật. Chỉ vì một test chưa thấy lỗi, không thể kết luận việc sửa tùy ý trường comparator đọc là an toàn.

Một quy tắc dễ kiểm soát là giữ dữ liệu quyết định thứ tự ổn định trong thời gian phần tử nằm trong queue, và thực hiện thay đổi qua một luồng cập nhật tường minh.

### 2.3. Poll không có nhiệm vụ kiểm tra lại toàn bộ dữ liệu

Poll dựa trên giả định rằng heap đang hợp lệ: lấy gốc, đưa phần tử cuối vào vị trí cần lấp rồi sửa một đường đi xuống.

Nó không quét mọi phần tử để tìm lại min theo dữ liệu vừa bị sửa bên ngoài. Một bất biến đã bị phá ở đường khác có thể tiếp tục sai sau một lần poll.

Cũng không dùng một offer bất kỳ như phép “làm mới toàn bộ”. Offer sửa đường của phần tử mới, không tự rà soát mọi đối tượng đã bị setter đổi.

## 3. Chương trình 1: contains vẫn đúng nhưng đầu ưu tiên sai

Lưu tệp `MutablePriorityFailureDemo.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class MutablePriorityFailureDemo {
    static final class Task {
        private final String id;
        private int priority;

        Task(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        int priority() {
            return priority;
        }

        void changePriority(int newPriority) {
            priority = newPriority;
        }

        @Override
        public String toString() {
            return id + ":" + priority;
        }
    }

    public static void main(String[] args) {
        Comparator<Task> order = Comparator.comparingInt(Task::priority);
        Task a = new Task("A", 10);
        Task b = new Task("B", 20);
        Task c = new Task("C", 30);
        Task d = new Task("D", 40);
        Task e = new Task("E", 50);
        List<Task> input = List.of(a, b, c, d, e);
        PriorityQueue<Task> tasks = new PriorityQueue<>(order);
        tasks.addAll(input);

        System.out.println("head before change = " + tasks.peek());
        d.changePriority(1);

        System.out.println("size after change = " + tasks.size());
        System.out.println("contains changed reference = " + tasks.contains(d));
        System.out.println("minimum by fresh scan = "
                + input.stream().min(order).orElseThrow());
        System.out.println("head after change = " + tasks.peek());
        System.out.println("first poll after change = " + tasks.poll());
        System.out.println("head after one poll = " + tasks.peek());

        List<String> output = new ArrayList<>();
        Task next;
        while ((next = tasks.poll()) != null) {
            output.add(next.toString());
        }
        System.out.println("remaining poll order = " + output);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 MutablePriorityFailureDemo.java
java MutablePriorityFailureDemo
```

Kết quả của lần chạy kiểm chứng:

```text
head before change = A:10
size after change = 5
contains changed reference = true
minimum by fresh scan = D:1
head after change = A:10
first poll after change = A:10
head after one poll = B:20
remaining poll order = [B:20, D:1, C:30, E:50]
```

Đây là thí nghiệm cố ý làm mất bất biến heap. Mã dùng ngôn ngữ và API có ở Java 17 trở lên, được biên dịch với `--release 17` và chạy kiểm chứng bằng Azul OpenJDK 24.0.2. Cơ chế được đối chiếu với mã nguồn OpenJDK 17.

Các dòng chọn phần tử sau thay đổi là quan sát của triển khai đã chạy, không phải một hợp đồng rằng mọi JDK phải sai theo đúng cùng chuỗi ấy.

### 3.1. Size và contains không kiểm tra thứ tự heap

Size vẫn là 5. Contains với đúng tham chiếu d vẫn trả true. Điều này phù hợp với việc queue còn giữ đối tượng ấy.

Nhưng hai câu hỏi khác nhau:

```text
Đối tượng có còn ở trong queue không?
Queue có đang chọn đúng đối tượng ưu tiên nhất không?
```

Fresh scan dùng dữ liệu hiện tại tìm được D:1. Peek của heap cũ vẫn trả A:10. Việc tồn tại thành viên không chứng minh chỉ mục ưu tiên còn đúng.

### 3.2. Một poll chưa sửa hết vấn đề

Sau khi poll A:10, đầu tiếp theo vẫn là B:20 dù D:1 còn chờ. Chuỗi còn lại là B:20, D:1, C:30, E:50, cho thấy không thể dựa vào việc poll một lần để chữa toàn bộ heap.

Trong mô hình minh họa, sau lần poll đầu có thể còn quan hệ:

```text
        B:20
       /    \
     D:1    C:30
     /
   E:50
```

Đường sửa cục bộ đã làm công việc của nó theo giả định cũ, nhưng đầu vẫn không nhỏ hơn mọi phần tử.

### 3.3. Không có ngoại lệ không có nghĩa đúng ưu tiên

Setter không thêm hoặc gỡ một mục của queue. Không có một yêu cầu chung buộc nó phải tăng bộ đếm thay đổi cấu trúc hay ném ConcurrentModificationException.

Chương trình có thể chạy hết, trả đủ các đối tượng, nhưng thứ tự xử lý đã sai. Đây là lỗi của bất biến sắp xếp dữ liệu, không nhất thiết là một lỗi thiếu phần tử hoặc một ngoại lệ truy cập bộ nhớ.

## 4. Vì sao queue không tự chụp hoặc theo dõi priority?

### 4.1. Queue giữ tham chiếu tới phần tử

Nếu đưa một đối tượng khả biến vào PriorityQueue, queue không mặc định clone toàn bộ đối tượng hay lưu sẵn một bản priority bất biến độc lập.

Một tham chiếu khác vẫn có thể gọi setter. Khi comparator được gọi lại, nó thấy dữ liệu mới; nhưng các quan hệ vị trí được tạo trước đó chưa chắc phù hợp dữ liệu mới.

Vì vậy, việc comparator đọc được số mới không đồng nghĩa cấu trúc đã được đặt lại đúng chỗ.

### 4.2. Comparator đúng tại từng thời điểm vẫn chưa đủ

Comparator dùng Integer.compare cho một trường priority có thể hợp lý về dấu và tính bắc cầu tại từng trạng thái dữ liệu.

Vấn đề vòng đời là quan hệ so sánh giữa những phần tử đang nằm trong heap đã thay đổi mà không đi qua thao tác duy trì cấu trúc.

Cần kiểm tra cả hai tầng:

1. Comparator có biểu đạt một quan hệ thứ tự hợp lệ không?
2. Dữ liệu mà quan hệ ấy dựa vào có được giữ ổn định giữa các lần cập nhật queue không?

Sửa một công thức so sánh không tự giải quyết một setter đang thay dữ liệu ngoài luồng quản lý.

### 4.3. final và record chỉ bảo vệ điều chúng thực sự giữ

`final PriorityQueue<Task>` chỉ ngăn gán biến đó sang một queue khác. Nó không làm phần tử Task bất biến.

`final Comparator<Task>` chỉ cố định tham chiếu comparator. Nếu comparator đọc một Map cấu hình có thể đổi, kết quả thứ tự vẫn có thể đổi.

Record cũng không tự bất biến sâu. [Tài liệu Record Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html) mô tả record là bất biến nông: một thành phần tham chiếu có thể trỏ tới đối tượng còn sửa được.

Ví dụ, record chứa một PriorityState khả biến vẫn có thể đổi kết quả so sánh khi PriorityState đổi. Một record chỉ có số nguyên và String bất biến như ví dụ an toàn bên dưới dễ giữ ổn định hơn.

### 4.4. Thay đổi gián tiếp cũng cần được rà soát

Đường làm đổi ưu tiên có thể là:

- Setter trực tiếp của trường priority.
- Đối tượng lồng bên trong mà comparator đọc.
- Một Map ngoài được comparator dùng tra cứu mức hiện tại.
- Một cờ cấu hình làm đảo hoặc đổi tiêu chí so sánh.
- Một quy tắc tính điểm theo trạng thái bên ngoài làm hai công việc đổi tương quan.

Điều cần xem là quan hệ thứ tự có thay đổi không. Không phải mọi dữ liệu bên ngoài đều gây lỗi, nhưng mọi nguồn có thể làm đổi quan hệ cần một cơ chế cập nhật cấu trúc phù hợp.

## 5. contains, equals và comparator có vai trò khác nhau

PriorityQueue dùng comparator hoặc Comparable để tổ chức ưu tiên. Nhưng contains và remove(Object) tìm theo equals.

Trong ví dụ đầu, Task không override equals nên dùng đồng nhất đối tượng mặc định; contains(d) thành công với đúng tham chiếu. Lớp khác có equals theo id sẽ có cách nhận diện khác.

Các vai trò nên được ghi rõ:

| Việc | Quan hệ được dùng |
|---|---|
| Chọn đầu ưu tiên, sift-up/sift-down | Comparator hoặc Comparable |
| contains(x), remove(x) theo đối tượng | Equals |
| So cùng một đối tượng qua hai tham chiếu | Có thể dùng == khi chính thuật toán cần nhận diện tham chiếu |

Không giải thích lỗi PriorityQueue bằng quy tắc ngăn băm của HashSet. PriorityQueue không dùng hashCode để quyết định vị trí ưu tiên hoặc để thực hiện bước tìm tuyến tính của remove(Object).

### 5.1. Remove sau mutation có thể vẫn tìm được

Vì remove(Object) tìm theo equals, thay priority không nhất thiết làm nó mất khả năng tìm mục. Nếu equals hoặc đúng tham chiếu vẫn khớp, lời gọi có thể trả true.

Nhưng việc tìm được để gỡ không chứng minh khoảng thời gian trước đó queue đã trả đúng ưu tiên. Nếu đã có một poll sai xảy ra, gỡ được về sau không hoàn tác quyết định xử lý cũ.

Cách kiểm soát tốt hơn là gỡ khi dữ liệu cũ còn ổn định, xác nhận gỡ thành công, rồi đổi hoặc tạo giá trị mới và thêm lại.

### 5.2. Remove chỉ gỡ một lần xuất hiện bằng nhau

PriorityQueue cho phép phần tử trùng. Nếu cùng một đối tượng được offer nhiều lần, remove một lần không bảo đảm không còn tham chiếu ấy trong queue.

Nếu có nhiều đối tượng khác nhau mà equals xem là bằng nhau, remove(target) cũng không mặc định là một API gỡ theo đúng danh tính tham chiếu của target.

Trước khi dùng remove để cập nhật một công việc, cần một chính sách định danh và số lần xuất hiện rõ. Đừng giả định queue tự bảo đảm mỗi id chỉ có một bản ghi.

### 5.3. Tính chi phí đúng

Remove(Object) có thể cần tìm qua n phần tử nên là O(n) tổng quát, rồi còn duy trì heap quanh vị trí gỡ. Offer tiếp theo có chi phí phần heap O(log n).

Vì thế, cập nhật bằng remove rồi offer không tự có cận O(log n) cho toàn bộ yêu cầu. Nếu cập nhật ưu tiên là thao tác rất thường xuyên ở quy mô lớn, cần đánh giá cấu trúc hoặc mô hình cập nhật khác.

## 6. Cập nhật tường minh: gỡ trước, thay đổi hoặc thay thế rồi thêm

Một luồng cơ bản trong điều kiện đã kiểm soát:

```text
xác định đúng công việc đang chờ
    -> chuẩn bị và kiểm tra dữ liệu mới
    -> gỡ công việc cũ khi thông tin còn ổn định
    -> xác nhận đã gỡ
    -> đổi hoặc tạo giá trị mới
    -> offer giá trị mới
```

### 6.1. Nếu vẫn dùng đối tượng khả biến

Trích đoạn minh họa:

```java
// Trích đoạn trong luồng đơn, với phần tử duy nhất được quản lý.
if (!queue.remove(task)) {
    throw new IllegalStateException("Task is not queued");
}
task.changePriority(newPriority);
queue.offer(task);
```

Đây không phải một hàm cập nhật tổng quát cho mọi hệ thống. Nó giả định:

- Task cần cập nhật được nhận diện đúng.
- Không còn lần xuất hiện khác của cùng khóa khả biến trong queue.
- Không có queue khác đồng thời đang tổ chức theo cùng đối tượng bị sửa.
- Không có cập nhật chen vào giữa các bước.
- Dữ liệu mới và comparator đã đáp ứng hợp đồng cần dùng.

Nếu một đối tượng còn nằm trong nhiều cấu trúc ưu tiên, gỡ khỏi một nơi rồi sửa vẫn có thể làm nơi khác sai.

### 6.2. Thường dễ kiểm soát hơn khi tạo giá trị bất biến mới

Thay vì đổi đối tượng cũ, có thể biểu đạt yêu cầu mới bằng một giá trị khác:

```java
// Trích đoạn khai báo khóa thứ tự ổn định.
record Job(String id, int priority) {
}
```

Gỡ Job cũ rồi offer Job mới. Giá trị cũ không tự biến đổi trong bất kỳ nơi nào còn giữ nó.

Thiết kế này tách được hai việc: trạng thái nghiệp vụ có thể tiếp tục thay đổi, còn mục đã đưa vào heap có một khóa ưu tiên ổn định cho tới khi được thay thế.

Nếu công việc có nhiều dữ liệu mô tả có thể sửa, có thể để mục queue giữ id và bản chụp các trường cần so sánh, còn dữ liệu nghiệp vụ được quản lý ở nơi phù hợp.

### 6.3. Chuỗi cập nhật cần chính sách thất bại

Remove rồi offer là nhiều bước. Ví dụ nhỏ với kiểu số hợp lệ và comparator đơn giản có thể giữ luồng rõ, nhưng API không tự biến chuỗi thành giao dịch.

Nếu nghiệp vụ đòi hỏi không mất công việc khi bước sau thất bại, cần một quy trình chuẩn bị, phục hồi và phối hợp phù hợp. Bỏ qua boolean của remove khiến mã có thể thêm một bản mới dù bản cũ chưa được gỡ đúng.

Không chữa một thất bại tìm bằng cách cứ offer cùng đối tượng đã đổi. Hành động ấy có thể để lại nhiều lần xuất hiện cho cùng một công việc.

## 7. Chương trình 2: thay thế một Job bất biến

Lưu tệp `ImmutablePriorityReplacementDemo.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class ImmutablePriorityReplacementDemo {
    record Job(String id, int priority) {
        @Override
        public String toString() {
            return id + ":" + priority;
        }
    }

    static final Comparator<Job> ORDER =
            Comparator.comparingInt(Job::priority).thenComparing(Job::id);

    static Job replacePriority(
            PriorityQueue<Job> queue, Job oldJob, int newPriority) {
        Job replacement = new Job(oldJob.id(), newPriority);
        if (!queue.remove(oldJob)) {
            throw new IllegalStateException("Old job is not queued");
        }
        queue.offer(replacement);
        return replacement;
    }

    public static void main(String[] args) {
        Job a = new Job("A", 10);
        Job b = new Job("B", 20);
        Job oldC = new Job("C", 30);
        PriorityQueue<Job> queue = new PriorityQueue<>(ORDER);
        queue.addAll(List.of(a, b, oldC));

        Job newC = replacePriority(queue, oldC, 5);
        System.out.println("old immutable value = " + oldC);
        System.out.println("new immutable value = " + newC);
        System.out.println("size after replacement = " + queue.size());
        System.out.println("contains old value = " + queue.contains(oldC));
        System.out.println("contains new value = " + queue.contains(newC));
        System.out.println("head after replacement = " + queue.peek());

        try {
            replacePriority(queue, oldC, 1);
        } catch (IllegalStateException ex) {
            System.out.println("replace missing old value = "
                    + ex.getClass().getSimpleName());
        }
        System.out.println("size after rejected replacement = " + queue.size());

        List<String> output = new ArrayList<>();
        Job next;
        while ((next = queue.poll()) != null) {
            output.add(next.toString());
        }
        System.out.println("safe poll order = " + output);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 ImmutablePriorityReplacementDemo.java
java ImmutablePriorityReplacementDemo
```

Kết quả của lần chạy kiểm chứng:

```text
old immutable value = C:30
new immutable value = C:5
size after replacement = 3
contains old value = false
contains new value = true
head after replacement = C:5
replace missing old value = IllegalStateException
size after rejected replacement = 3
safe poll order = [C:5, A:10, B:20]
```

### 7.1. Giá trị cũ không bị sửa

OldC vẫn là C:30; newC là C:5. Sau thay thế, queue có ba phần tử, không còn giá trị cũ và có giá trị mới.

Đầu C:5 đúng theo comparator vì nó đã được đưa vào bằng offer như một phần tử mới hợp lệ.

Ví dụ có một Job cho mỗi id, không có bản ghi trùng và không có cập nhật đồng thời. Helper kiểm tra gỡ thành công trước khi thêm.

### 7.2. Thử thay một phiên bản cũ bị từ chối

Gọi lại helper với oldC sau khi oldC đã bị thay sẽ không tìm thấy giá trị ấy. Helper ném IllegalStateException và không offer một bản thay thế khác.

Đây là một kiểm tra quan trọng cho cập nhật dựa trên giá trị cũ: yêu cầu lỗi thời không nên âm thầm tạo thêm công việc.

### 7.3. Tính đúng và chi phí là hai câu hỏi riêng

Kết quả poll là C:5, A:10, B:20, phù hợp với ưu tiên của các giá trị mới.

Nhưng bước remove(oldC) vẫn có tìm tuyến tính. Mẫu này ưu tiên sự rõ ràng của vòng đời và phù hợp với điều kiện ví dụ; không được quảng bá như một thao tác cập nhật khóa O(log n) tổng quát.

## 8. Dựng lại heap sau nhiều thay đổi cần được thực hiện có chủ đích

Nếu đã có nhiều trường ưu tiên bị sửa, một hướng phục hồi là xác lập dữ liệu chuẩn rồi dựng một queue mới theo các giá trị ấy.

Trước khi làm, cần bảo đảm dữ liệu dùng để dựng không tiếp tục đổi trong lúc đang đọc. Nếu không, cấu trúc mới có thể được xây từ những quan hệ thứ tự khác nhau tại các thời điểm khác nhau.

Một quy trình rõ ràng:

```text
xác định nguồn dữ liệu chuẩn
    -> dừng hoặc phối hợp các cập nhật liên quan
    -> lấy các bản chụp khóa ưu tiên nhất quán
    -> tạo PriorityQueue mới với comparator đúng
    -> đưa các bản chụp vào bằng thao tác dựng phù hợp
    -> kiểm tra trước khi chuyển quyền sử dụng sang queue mới
```

### 8.1. Copy một PriorityQueue không có hợp đồng sửa heap hỏng

Một cách tưởng như đơn giản:

```java
// Không coi đây là một phép repair cho một heap đã mất bất biến.
PriorityQueue<Task> repaired = new PriorityQueue<>(broken);
```

không có hợp đồng tái đánh giá mọi khóa ưu tiên đã bị sửa.

Trong [mã nguồn PriorityQueue OpenJDK 17](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/util/PriorityQueue.java), đường sao chép từ một đối tượng có đúng lớp PriorityQueue có thể sao dữ liệu nền mà không heapify lại. Tối ưu ấy dựa trên giả định nguồn đã là một heap hợp lệ.

Vì vậy, nếu nguồn đã bị phá bất biến qua setter, chỉ đổi sang một container copy không tự trở thành một quy trình phục hồi đáng tin.

Chi tiết đường tối ưu có thể khác giữa triển khai hoặc kiểu nguồn. Điều nên giữ là không dựa vào constructor copy như một API repair cho trạng thái đã bị sử dụng sai.

### 8.2. Thêm vào queue mới bằng comparator rõ ràng

Một mẫu tổ chức lại theo giá trị hiện tại là:

```java
// Trích đoạn; currentValues là nguồn dữ liệu đã ổn định.
PriorityQueue<Task> rebuilt = new PriorityQueue<>(order);
rebuilt.addAll(currentValues);
```

Với queue mới, các phần tử được thêm theo comparator đã chọn. Nhưng nếu chúng vẫn là cùng các Task khả biến, lần sửa tiếp theo ở nguồn có thể làm rebuilt mất bất biến trở lại.

Tổ chức lại vị trí và đóng băng khóa ưu tiên là hai yêu cầu khác nhau.

### 8.3. Bản chụp khóa giúp tách vòng đời

Có thể dựng bằng một kiểu giá trị ổn định:

```java
// Trích đoạn khai báo; các thành phần nhận diện ưu tiên được giữ ổn định.
record QueueSnapshot(String id, int priority) {
}
```

Khi dựng queue mới, tạo QueueSnapshot từ trạng thái đã xác nhận của từng công việc. Comparator chỉ đọc các thành phần bất biến ấy.

Điều này giữ cấu trúc mới đúng theo **bản chụp đã tạo**. Nó không có nghĩa queue tự đồng bộ với mọi priority mới nhất của đối tượng nghiệp vụ về sau. Nếu nghiệp vụ đổi ưu tiên, vẫn cần một yêu cầu thay thế hoặc một cơ chế phiên bản rõ ràng.

## 9. Chương trình 3: copy trực tiếp, dựng lại chia sẻ và bản chụp

Lưu tệp `PriorityQueueRebuildDemo.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityQueueRebuildDemo {
    static final class MutableTask {
        private final String id;
        private int priority;

        MutableTask(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        int priority() {
            return priority;
        }

        @Override
        public String toString() {
            return id + ":" + priority;
        }
    }

    record Snapshot(String id, int priority) {
        @Override
        public String toString() {
            return id + ":" + priority;
        }
    }

    public static void main(String[] args) {
        Comparator<MutableTask> mutableOrder =
                Comparator.comparingInt(MutableTask::priority);
        MutableTask d = new MutableTask("D", 40);
        PriorityQueue<MutableTask> broken = new PriorityQueue<>(mutableOrder);
        broken.addAll(List.of(
                new MutableTask("A", 10),
                new MutableTask("B", 20),
                new MutableTask("C", 30),
                d,
                new MutableTask("E", 50)));
        d.priority = 1;

        PriorityQueue<MutableTask> directCopy = new PriorityQueue<>(broken);
        PriorityQueue<MutableTask> sharedRebuild =
                new PriorityQueue<>(mutableOrder);
        sharedRebuild.addAll(broken);

        Comparator<Snapshot> snapshotOrder =
                Comparator.comparingInt(Snapshot::priority)
                        .thenComparing(Snapshot::id);
        PriorityQueue<Snapshot> stable = new PriorityQueue<>(snapshotOrder);
        for (MutableTask task : broken) {
            stable.offer(new Snapshot(task.id, task.priority));
        }

        System.out.println("broken head = " + broken.peek());
        System.out.println("direct copy head = " + directCopy.peek());
        System.out.println("shared rebuild head = " + sharedRebuild.peek());
        System.out.println("snapshot rebuild head = " + stable.peek());

        d.priority = 100;
        System.out.println("shared rebuild after later change = "
                + sharedRebuild.peek());
        System.out.println("snapshot head after later change = " + stable.peek());

        List<String> output = new ArrayList<>();
        Snapshot next;
        while ((next = stable.poll()) != null) {
            output.add(next.toString());
        }
        System.out.println("snapshot poll order = " + output);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac --release 17 -encoding UTF-8 PriorityQueueRebuildDemo.java
java PriorityQueueRebuildDemo
```

Kết quả của lần chạy kiểm chứng:

```text
broken head = A:10
direct copy head = A:10
shared rebuild head = D:1
snapshot rebuild head = D:1
shared rebuild after later change = D:100
snapshot head after later change = D:1
snapshot poll order = [D:1, A:10, B:20, C:30, E:50]
```

Phần broken và directCopy là quan sát sau một thay đổi cố ý phá bất biến, được kiểm chứng trên môi trường đã nêu. Không coi một hành vi sai cụ thể là điều mọi triển khai phải tái hiện giống hệt.

### 9.1. Copy trực tiếp vẫn có đầu A:10

D đã đổi thành D:1 nhưng broken.peek vẫn là A:10. DirectCopy cũng có đầu A:10 trong lần chạy.

Điều đó minh họa đường sao chép có thể tin bố trí heap của nguồn. Không có một lời gọi repair tự động chỉ vì constructor đã tạo đối tượng mới.

### 9.2. Dựng lại bằng addAll tổ chức theo các giá trị lúc đó

SharedRebuild được tạo rỗng với mutableOrder rồi thêm các tham chiếu hiện có. Đầu lúc ấy là D:1, phù hợp các giá trị hiện tại.

Nhưng D trong sharedRebuild chính là đối tượng D mà bên ngoài còn giữ. Khi priority của D đổi thành 100, peek của sharedRebuild trả D:100, lại sai so với A:10 đang chờ.

Một lần dựng lại đúng không bảo vệ được các lần sửa tương lai nếu khóa tiếp tục được chia sẻ khả biến.

### 9.3. Snapshot giữ nguyên ưu tiên đã ghi nhận

Stable chứa các Snapshot riêng, nên D trong đó vẫn là D:1 khi đối tượng gốc đổi thành D:100. Chuỗi poll là D:1, A:10, B:20, C:30, E:50.

Điều này chứng minh heap ổn định theo dữ liệu snapshot, không chứng minh rằng nó đã tự áp dụng yêu cầu nghiệp vụ D:100 mới nhất.

Nếu yêu cầu mới là D phải được xử lý theo priority 100, ứng dụng phải thay snapshot hoặc làm phiên bản cũ không còn hợp lệ. Sự độc lập của bản chụp là một công cụ để quản lý thay đổi, không phải thay thế cho việc truyền thay đổi có chủ đích.

### 9.4. Chi phí dựng cần được tính theo cách thực hiện

Trong ví dụ, queue mới có comparator tường minh và nhận các phần tử bằng offer hoặc addAll. Có thể phân tích theo n lần thêm với cận tổng quát O(n log n) cho sửa heap.

Các thuật toán heapify theo lô có thể có chi phí tuyến tính trong những đường dựng phù hợp, nhưng không được suy ra rằng mọi constructor copy hoặc mọi addAll đều là một phép heapify mong muốn.

Khi mục tiêu là phục hồi đúng, trước hết xác định dữ liệu và ngữ nghĩa. Sau đó mới chọn cách dựng hiệu quả có cơ sở.

## 10. Khi cập nhật thường xuyên: tách mục queue khỏi trạng thái nghiệp vụ

Remove(Object) mỗi lần có thể quá tốn kém nếu số việc lớn và đổi ưu tiên liên tục. Có thể cân nhắc một mô hình khác, nhưng vẫn phải giữ khóa heap ổn định.

Một hướng là mục queue bất biến có phiên bản:

```java
// Trích đoạn kiểu dữ liệu, không phải scheduler hoàn chỉnh.
record QueueEntry(String taskId, int priority, long version) {
}
```

Ý tưởng:

1. Mỗi công việc có một phiên bản hiện hành được quản lý ở nguồn chuẩn.
2. Khi đổi ưu tiên, tạo QueueEntry mới với priority và version mới.
3. Comparator chỉ đọc priority cùng các tiêu chí phụ bất biến của entry.
4. Khi lấy entry ra, đối chiếu version với phiên bản hiện hành.
5. Nếu entry đã cũ, bỏ qua; nếu còn hiệu lực, xử lý theo chính sách của ứng dụng.

Cơ chế này không sửa khóa của một entry đang nằm trong heap. Nó thay việc tìm-gỡ ngay bằng việc nhận biết entry lỗi thời ở thời điểm sử dụng.

### 10.1. Đổi đánh đổi, không làm biến mất công việc

Những entry cũ vẫn có thể chiếm chỗ cho tới khi được lấy hoặc dọn. Chi phí heap phụ thuộc tổng số entry đang lưu, gồm cả entry lỗi thời, không chỉ số công việc còn hiệu lực.

Một lần yêu cầu lấy công việc có thể phải bỏ qua nhiều entry cũ. Vì vậy, không khẳng định mỗi lần xử lý nghiệp vụ chắc chắn chỉ có một poll O(log n).

Cần chính sách dọn, hủy, cấp phiên bản và giới hạn lưu trữ phù hợp. Nếu API có peek để xem việc hiệu lực tiếp theo, cũng phải xử lý các đầu đã lỗi thời; chỉ peek thẳng một entry cũ có thể cho quyết định sai.

### 10.2. Nguồn chuẩn và queue cần phối hợp

Thay phiên bản ở Map rồi thêm entry là nhiều bước. Trong hệ thống nhiều luồng, cần thiết kế quan hệ giữa chúng để không công bố trạng thái nửa chừng.

Không sao chép nguyên mô tả ý tưởng thành một cấu trúc đồng thời hoàn chỉnh. Nó là một cách tách khóa bất biến và tính hợp lệ hiện thời, cần được hiện thực theo yêu cầu cụ thể.

Một hướng khác là cấu trúc heap có hỗ trợ cập nhật khóa bằng chỉ mục/handle tường minh. Khi dùng API như vậy, phải đọc hợp đồng riêng; PriorityQueue chuẩn không tự cung cấp khả năng ấy chỉ nhờ có một tham chiếu tới phần tử.

## 11. Cập nhật gián tiếp và nhiều luồng không tự sửa được bằng khóa

### 11.1. Comparator đọc trạng thái bên ngoài

Giả sử comparator không đọc task.priority mà tra `priorities.get(task.id)` trong một Map. Đối tượng Task có thể bất biến nhưng quan hệ thứ tự vẫn đổi khi Map bị cập nhật.

Hoặc một cờ bên ngoài đổi từ “số nhỏ trước” sang “số lớn trước”. Cây heap cũ không được tổ chức lại chỉ vì cờ ấy thay đổi.

Khi rà soát rủi ro, hãy truy theo mọi dữ liệu comparator đọc, không chỉ tìm setter trong lớp phần tử.

### 11.2. Volatile hoặc một trường nguyên tử không thực hiện sift

Một cơ chế làm giá trị priority mới được nhìn thấy không tự di chuyển mục trong heap.

Có thể xảy ra tình huống mọi lần so sánh đều đọc đúng giá trị mới, nhưng bố trí được tạo theo giá trị cũ vẫn sai. Vì vậy, vấn đề hiển thị dữ liệu giữa luồng và vấn đề duy trì cấu trúc cần được giải quyết riêng.

### 11.3. Đồng bộ chỉ có giá trị khi bảo vệ đúng toàn bộ thay đổi

Nếu một luồng giữ khóa khi offer/poll nhưng luồng khác sửa trực tiếp priority mà không tuân thủ cùng quy trình, bất biến vẫn có thể bị phá.

Ngay cả khi tất cả thao tác được chạy tuần tự dưới cùng một khóa, một setter diễn ra lúc phần tử còn ở trong queue vẫn không tự sắp xếp lại heap. Khóa chỉ ngăn cập nhật chen nhau; luồng cập nhật vẫn phải gỡ/thay thế/dựng lại đúng.

Tương tự, đổi sang một collection đồng thời không tự tạo một bộ theo dõi setter cho đối tượng nằm bên trong. Cần giữ ngữ nghĩa cập nhật ưu tiên tường minh.

### 11.4. Một đối tượng trong nhiều queue

Nếu cùng Task nằm trong hai PriorityQueue theo cùng hoặc khác tiêu chí, sửa Task có thể làm cả hai bị ảnh hưởng.

Gỡ khỏi queue thứ nhất trước setter chưa bảo vệ queue thứ hai. Tách mỗi mục queue thành một khóa/bản chụp riêng thường giúp xác định nơi nào phải được cập nhật và lúc nào.

Các snapshot ở nhiều nơi cũng cần cơ chế truyền yêu cầu thay đổi nếu mục tiêu là giữ chúng cùng phản ánh một phiên bản nghiệp vụ. Bất biến không có nghĩa tự đồng bộ dữ liệu.

## 12. Chọn cách xử lý theo tình huống

| Tình huống | Hướng cần cân nhắc | Điểm phải kiểm tra |
|---|---|---|
| Ưu tiên không đổi trong khi chờ | Entry bất biến | Comparator và tiêu chí phụ đều ổn định |
| Thỉnh thoảng đổi một việc ở quy mô vừa | Gỡ giá trị cũ rồi offer giá trị mới | Định danh, phần tử trùng, remove có thành công, chi phí O(n) |
| Nhiều khóa đã bị sửa hoặc cần thay quy tắc xếp hạng | Lấy dữ liệu chuẩn và dựng cấu trúc mới | Bản chụp nhất quán, comparator đúng, không tin copy heap hỏng |
| Cập nhật rất thường xuyên | Entry có phiên bản hoặc cấu trúc hỗ trợ update-key | Entry lỗi thời, bộ nhớ, tổng chi phí và tính nguyên tử |
| Một đối tượng được nhiều nơi lập chỉ mục | Tách khóa riêng theo cấu trúc | Không để setter chung âm thầm đổi mọi chỉ mục |
| Cần xử lý chia sẻ nhiều luồng | Thiết kế cả mô hình cập nhật và phối hợp | Đồng bộ không thay thế việc duy trì heap |

Không phải mọi thay đổi trạng thái của công việc đều làm sai queue. Nếu comparator chỉ đọc priority và id ổn định, đổi một ghi chú không tham gia thứ tự có thể không ảnh hưởng heap.

Nhưng khi id được dùng làm tiêu chí phụ, chính id cũng thuộc dữ liệu cần ổn định. Hãy liệt kê đầy đủ các thành phần so sánh, không chỉ trường mang tên priority.

Một lựa chọn đúng nên mô tả được: ai có quyền thay đổi, thay đổi xảy ra khi phần tử ở trạng thái nào, và cấu trúc được duy trì lại bằng thao tác nào.

## 13. Những cách chữa dễ tạo thêm lỗi

| Cách làm | Điều còn sai | Hướng xử lý |
|---|---|---|
| Setter priority rồi chờ peek tự tìm min | Peek chỉ đọc đầu đã được duy trì | Dùng cập nhật tường minh |
| Poll một lần để “refresh” toàn bộ | Poll sửa một đường, giả định heap đã hợp lệ | Không dùng như phép repair tổng quát |
| Offer lại cùng đối tượng đã sửa | Có thể tạo thêm lần xuất hiện của cùng tham chiếu | Kiểm soát định danh và gỡ đúng trước khi thay |
| Thấy contains true nên kết luận queue đúng | Thành viên và thứ tự là hai câu hỏi khác | Kiểm tra lựa chọn đầu và chuỗi poll |
| Nghĩ remove(Object) sau sửa chắc chắn luôn mất dấu | Tìm theo equals vẫn có thể thành công | Phân biệt tìm thành viên với duy trì heap |
| Gỡ một lần nhưng có nhiều lần xuất hiện | Các tham chiếu còn lại vẫn chịu setter | Quản lý số lần xuất hiện hoặc dùng entry riêng |
| Copy heap hỏng bằng constructor | Có đường copy không heapify lại | Dựng từ dữ liệu chuẩn bằng quy trình rõ |
| Dựng lại bằng cùng đối tượng rồi tiếp tục setter | Heap mới có thể sai lần nữa | Bảo vệ khóa hoặc dùng snapshot bất biến |
| Dùng record chứa một đối tượng priority khả biến | Bất biến chỉ ở tham chiếu trực tiếp | Kiểm tra dữ liệu lồng comparator đọc |
| Dùng synchronized/volatile như phép sắp xếp | Cơ chế bộ nhớ không thực hiện sift | Phối hợp đúng và giữ luồng cập nhật cấu trúc |
| Gọi remove-then-offer là O(log n) | Bước tìm theo đối tượng có thể O(n) | Tính đủ mọi bước |
| Snapshot không đổi nên nghĩ luôn là ưu tiên mới nhất | Snapshot giữ dữ liệu ở thời điểm tạo | Truyền yêu cầu thay thế hoặc phiên bản mới |

Không dùng việc một dữ liệu nhỏ tình cờ cho kết quả đúng để hợp thức hóa một đường sửa không giữ bất biến. Rủi ro thường lộ ra khi phần tử bị đổi nằm sâu hơn, thứ tự cập nhật khác đi hoặc có nhiều đối tượng liên quan.

## 14. Kiểm tra lỗi và giải pháp theo vòng đời

Bài kiểm tra nên theo chuỗi thao tác thực tế, không chỉ kiểm tra comparator trên hai đối tượng tĩnh.

### 14.1. Trước thay đổi

Xác nhận queue ban đầu hợp lệ theo yêu cầu:

- Peek đúng phần tử đầu.
- Dữ liệu priority có các giá trị khác nhau để tránh nhầm với tie.
- Số lượng và định danh của các công việc rõ.
- Comparator có một quy tắc ổn định.

Nếu cần cùng ưu tiên, thêm tình huống riêng theo tiêu chí phụ đã thiết kế. Không dùng một thứ tự tie không được bảo đảm để kết luận có hoặc không có lỗi mutation.

### 14.2. Thay đổi theo các vị trí khác nhau

Nên kiểm tra:

- Một phần tử không ở đầu được đổi thành giá trị phải lên trước.
- Phần tử đầu được đổi thành giá trị phải lùi.
- Nhiều công việc đổi ưu tiên.
- Một đối tượng có nhiều tham chiếu hoặc nhiều lần xuất hiện.
- Dữ liệu so sánh thay qua đối tượng lồng hay cấu hình ngoài.

Các thí nghiệm cố ý setter trong queue giúp tái hiện rủi ro; chúng không phải hành vi sai mà ứng dụng nên dựa vào như một API ổn định.

### 14.3. Kiểm tra giải pháp hợp lệ

Với luồng thay thế hoặc dựng snapshot:

1. Kiểm tra phiên bản cũ được gỡ hoặc vô hiệu hóa đúng.
2. Kiểm tra phiên bản mới có mặt đúng số lần.
3. Kiểm tra peek sau cập nhật.
4. Poll toàn bộ trên dữ liệu thử để đối chiếu thứ tự mong muốn.
5. Kiểm tra yêu cầu cập nhật cũ bị xử lý đúng.
6. Kiểm tra thay đổi nguồn về sau có ảnh hưởng snapshot theo đúng thiết kế không.

Dữ liệu kỳ vọng cần phản ánh mô hình đã chọn. Với snapshot, kết quả đúng theo phiên bản đã chụp; với yêu cầu “luôn mới nhất”, cần cả cơ chế cập nhật phiên bản và loại bỏ dữ liệu cũ.

### 14.4. Đừng chỉ kiểm tra size hoặc contains

Một queue có thể giữ đủ mọi đối tượng mà vẫn chọn sai thứ tự. Size và contains không thay thế kiểm tra đầu và chuỗi xử lý.

Iterator của PriorityQueue cũng không bảo đảm duyệt theo ưu tiên ngay cả khi heap đang đúng. Nếu muốn so dãy xử lý, dùng poll trên một queue thử hợp lệ hoặc một bản dữ liệu được xây đúng.

Không dùng một Set để xác nhận kết quả nếu thứ tự và số lần xuất hiện đều quan trọng.

## 15. Bài tập áp dụng

### Bài 1: sửa một phần tử ở dưới

Queue đang có A:10, B:20, C:30. Sửa C thành C:1 qua setter. Vì sao peek có thể vẫn trả A?

**Gợi ý trả lời:** setter đổi giá trị trong đối tượng nhưng không đưa mục C lên vị trí phù hợp. Gốc cũ vẫn ở đó và peek không quét lại min.

### Bài 2: sửa gốc

Nếu A:10 ở đầu đổi thành A:100, có cần xem lại heap không?

**Gợi ý trả lời:** có. A có thể phải đi xuống; sửa trường không tự sift-down.

### Bài 3: contains vẫn true

Một thử nghiệm contains(task) trả true sau đổi priority. Có chứng minh thứ tự xử lý vẫn đúng không?

**Gợi ý trả lời:** không. Contains kiểm tra tồn tại theo equals; heap chọn đầu theo comparator. Hai yêu cầu khác nhau.

### Bài 4: cùng tham chiếu được thêm hai lần

Gỡ task một lần, sửa priority rồi thêm lại. Có chắc mọi mục trong queue đều hợp lệ không?

**Gợi ý trả lời:** không. Một lần xuất hiện khác của cùng tham chiếu có thể vẫn ở trong queue và chịu setter. Cần quản lý số lần xuất hiện hoặc dùng entry ổn định riêng.

### Bài 5: constructor copy

Queue đã mất bất biến sau mutation. Có nên coi new PriorityQueue<>(broken) là phép sửa chắc chắn không?

**Gợi ý trả lời:** không. Có đường triển khai copy bố trí mà không heapify lại, dựa trên giả định nguồn hợp lệ. Cần dựng có chủ đích từ dữ liệu chuẩn.

### Bài 6: bản chụp giữ D:1

Sau khi tạo snapshot D:1, đối tượng nghiệp vụ đổi thành D:100. Snapshot vẫn trả D:1 có chứng minh nó đã cập nhật đúng ưu tiên mới nhất không?

**Gợi ý trả lời:** không. Nó ổn định theo phiên bản đã chụp. Muốn phản ánh 100 cần một yêu cầu thay thế hoặc cơ chế phiên bản.

### Bài 7: remove rồi offer có logarit không?

Vì offer và các thao tác đầu của heap là O(log n), có thể kết luận cập nhật một đối tượng bằng remove(Object) rồi offer cũng O(log n) không?

**Gợi ý trả lời:** không. Remove(Object) có thể tìm tuyến tính O(n), phải tính cả bước ấy.

### Bài 8: chỉ đổi ghi chú

Comparator không đọc ghi chú và mọi khóa so sánh khác giữ nguyên. Sửa ghi chú có tự làm sai heap không?

**Gợi ý trả lời:** không nhất thiết. Rủi ro đang xét đến từ thay đổi quan hệ so sánh. Cần kiểm tra đúng các dữ liệu comparator thực sự đọc.

Một thiết kế ưu tiên dễ kiểm soát giữ được hai việc cùng lúc: khóa trong heap ổn định, và mọi thay đổi nghiệp vụ đi qua một quy trình cập nhật rõ. Khi đó việc chọn đầu không phụ thuộc vào một setter âm thầm xảy ra ngoài cấu trúc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="priority-queue-008">Bài toán top-K và lựa chọn heap nhỏ hoặc heap lớn</a>

<details>
<summary>Xem chi tiết</summary>

# Bài toán top-K và lựa chọn heap nhỏ hoặc heap lớn

## 1. Top-K là gì và khi nào cần đến?

Top-K là bài toán chọn tối đa K phần tử tốt nhất theo một tiêu chí từ N phần tử đầu vào. “Tốt nhất” có thể là giá trị lớn nhất, thời gian chờ ngắn nhất, điểm đánh giá cao nhất hoặc khoảng cách nhỏ nhất. Trước khi chọn cấu trúc dữ liệu, phải xác định chiều tốt hơn: một số lớn có thể tốt trong bài toán điểm số nhưng kém trong bài toán độ trễ.

Ví dụ, từ dãy:

~~~text
7, 2, 9, 4, 9, 1, 8, 5
~~~

Nếu mỗi lần xuất hiện được tính là một phần tử thì:

| Yêu cầu | Kết quả đã sắp theo thứ tự mong muốn |
| --- | --- |
| Ba phần tử lớn nhất | [9, 9, 8] |
| Ba phần tử nhỏ nhất | [1, 2, 4] |
| Giá trị lớn thứ ba, tính cả số trùng | 8 |
| Ba giá trị lớn nhất khác nhau | [9, 8, 7] |

Đó là các yêu cầu khác nhau. Một hàng đợi ưu tiên không tự quyết định có loại trùng hay không, và top-K theo số lần xuất hiện không đồng nghĩa top-K giá trị phân biệt.

Sắp xếp toàn bộ đầu vào rồi lấy K phần tử đầu là cách dễ hiểu. Tuy nhiên, nếu N rất lớn, K nhỏ, hoặc dữ liệu đến từng phần tử, ta muốn chỉ giữ những ứng viên còn có khả năng nằm trong kết quả. Heap giới hạn kích thước giúp thực hiện điều đó.

Trong bài này, “heap nhỏ” là heap có phần tử nhỏ nhất ở gốc theo thứ tự đang xét; “heap lớn” là heap có phần tử lớn nhất ở gốc theo thứ tự đó. Hai tên này nói về vị trí cực trị, không nói dung lượng heap. Cả hai đều có thể được giới hạn ở K phần tử.

Sau bài học, bạn cần tự trả lời được ba câu hỏi: phần tử nào đang yếu nhất trong nhóm được giữ; phần tử mới có tốt hơn nó không; và đầu heap phải chứa phần tử nào để quyết định thay thế nhanh?

## 2. Liên hệ với PriorityQueue của Java

`PriorityQueue` chọn đầu hàng đợi theo thứ tự tự nhiên hoặc `Comparator` được cung cấp. Đầu hàng đợi là phần tử đứng trước nhất theo thứ tự đó. Với số nguyên, thứ tự tự nhiên đưa số nhỏ nhất ra đầu; đảo thứ tự đưa số lớn nhất ra đầu. Khi các phần tử đồng hạng, hàng đợi không bảo đảm chọn phần tử được thêm sớm hơn. [Tài liệu PriorityQueue, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/PriorityQueue.html).

Hai khai báo cơ bản:

~~~java
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
PriorityQueue<Integer> maxHeap =
        new PriorityQueue<>(Comparator.reverseOrder());
~~~

Đây là hai cách cấu hình cùng một lớp. Java không cung cấp hai lớp tên `MinHeap` và `MaxHeap` cho trường hợp này.

Điều dễ nhầm nhất là chọn heap theo tên kết quả: “tìm lớn nhất thì dùng heap lớn”. Quy tắc đó phù hợp khi bạn giữ toàn bộ dữ liệu và muốn lấy ngay phần tử lớn nhất. Với thuật toán chỉ giữ K ứng viên, thao tác thường xuyên lại là loại ứng viên yếu nhất. Vì vậy, phải chọn đầu heap theo phần tử cần loại.

| Mục tiêu của heap giới hạn K | Phần tử yếu nhất trong nhóm đang giữ | Heap cần dùng cho số nguyên |
| --- | --- | --- |
| Giữ K phần tử lớn nhất | Nhỏ nhất trong nhóm | Heap nhỏ, thứ tự tự nhiên |
| Giữ K phần tử nhỏ nhất | Lớn nhất trong nhóm | Heap lớn, thứ tự đảo |

Hãy đọc hàng thứ nhất thành một câu: “Trong K số lớn nhất đang giữ, số nhỏ nhất là người dễ bị thay thế nhất; đưa nó lên đầu heap”.

## 3. Mô hình ngưỡng: đầu heap là ranh giới chấp nhận

Giả sử cần ba số lớn nhất và heap đang giữ:

~~~text
Các ứng viên: 7, 9, 9
Ngưỡng hiện tại: 7
~~~

Nếu đọc số 8, nó tốt hơn 7, vì thế loại 7 và thêm 8. Nếu đọc số 5, không có lý do loại bất kỳ ứng viên nào đang giữ để nhận 5.

Với heap nhỏ giới hạn K, `peek()` trả ngưỡng nhỏ nhất còn được chấp nhận trong nhóm hiện tại. Nó không nhất thiết là số nhỏ nhất của toàn bộ dữ liệu đã đọc, vì các số quá nhỏ đã bị loại.

Tương tự, khi cần ba số nhỏ nhất:

~~~text
Các ứng viên: 1, 2, 4
Ngưỡng hiện tại: 4
~~~

Số mới bằng 3 sẽ thay 4. Số mới bằng 8 bị bỏ qua. Heap lớn đưa 4 ra đầu để không phải tìm ứng viên yếu nhất bằng cách duyệt cả nhóm.

Trước khi đã nhận đủ K phần tử, `peek()` chỉ là cực trị của nhóm chưa đầy. Sau khi nhóm đủ K phần tử, nó mới đóng vai trò ranh giới của top-K hiện tại. Chẳng hạn, sau một số đầu tiên, không thể dùng đầu heap để kết luận đã có “số lớn thứ ba”.

Trong luồng chỉ có thêm phần tử, với top-K lớn nhất đã đầy, ngưỡng không giảm: mỗi lần thay thế đều nhận một số lớn hơn ngưỡng cũ. Với top-K nhỏ nhất, ngưỡng không tăng. Đây là hệ quả của chính quy tắc chọn, không phải một phép sắp xếp toàn bộ đầu vào.

## 4. Thuật toán giữ K phần tử lớn nhất

Giả sử K > 0 và các giá trị là số nguyên:

~~~text
Tạo heap nhỏ rỗng.
Với mỗi số x:
    Nếu heap chưa đủ K phần tử:
        thêm x.
    Nếu heap đã đủ K phần tử:
        lấy ngưỡng nhỏ nhất bằng peek().
        nếu x > ngưỡng:
            poll() để loại ngưỡng;
            offer(x).
        nếu x <= ngưỡng:
            bỏ qua x.
~~~

Mã cốt lõi, dùng trong chương trình đầy đủ ở phần sau:

~~~java
static void keepLargest(PriorityQueue<Integer> heap, int value, int k) {
    if (heap.size() < k) {
        heap.offer(value);
    } else if (value > heap.peek()) {
        heap.poll();
        heap.offer(value);
    }
}
~~~

Hàm phụ này có tiền điều kiện: `k > 0`, heap dùng thứ tự tự nhiên, không có `null` và trước mỗi lần gọi kích thước không lớn hơn K. Hàm tổng quát ở phần sau kiểm tra K và xử lý K = 0 trước khi lặp; không gọi hàm phụ với K = 0.

Phải kiểm tra chưa đầy trước khi so sánh với `peek()`. Khi heap rỗng, `peek()` trả `null`; nếu tự động mở hộp `Integer` thành `int` trong phép `>`, chương trình sẽ ném `NullPointerException`.

Tại sao dùng `>` thay vì `>=`? Trong bài toán chỉ xếp hạng theo giá trị số, một số bằng ngưỡng không cải thiện các giá trị trong kết quả. Bỏ qua nó khi heap đầy giúp tránh hai thao tác heap không cần thiết. Điều này không loại bỏ tất cả phần tử trùng: các lần xuất hiện bằng nhau vẫn có thể được thêm khi heap chưa đầy hoặc tốt hơn ngưỡng.

Ví dụ `[9, 9, 9, 8]` với K = 2 vẫn cho `[9, 9]`. Nếu cần phân biệt danh tính giữa các bản ghi bằng điểm, phải thêm tiêu chí phụ thay vì suy diễn từ dấu `>`.

### Theo dõi toàn bộ ví dụ

Bảng dưới sắp các phần tử được giữ theo chiều giảm để dễ đọc. Đây là cách trình bày kết quả trung gian, không mô tả thứ tự của mảng nội bộ heap.

| Bước | Số mới | Hành động | Các giá trị được giữ | Đầu heap nhỏ |
| --- | --- | --- | --- | --- |
| 1 | 7 | Chưa đầy, thêm | [7] | 7 |
| 2 | 2 | Chưa đầy, thêm | [7, 2] | 2 |
| 3 | 9 | Chưa đầy, thêm | [9, 7, 2] | 2 |
| 4 | 4 | 4 > 2, thay 2 | [9, 7, 4] | 4 |
| 5 | 9 | 9 > 4, thay 4 | [9, 9, 7] | 7 |
| 6 | 1 | 1 <= 7, bỏ qua | [9, 9, 7] | 7 |
| 7 | 8 | 8 > 7, thay 7 | [9, 9, 8] | 8 |
| 8 | 5 | 5 <= 8, bỏ qua | [9, 9, 8] | 8 |

Điểm quyết định là so với ngưỡng, không so với số đến ngay trước đó và không so với số lớn nhất đang giữ.

Một hình heap nhỏ hợp lệ cho nhóm cuối:

~~~text
       8
      / \
     9   9
~~~

Hai số 9 không cần có thứ tự trước sau. Chỉ cần gốc cho biết ứng viên yếu nhất là 8.

## 5. Đổi sang K phần tử nhỏ nhất

Để giữ K số nhỏ nhất, đổi đồng thời hai yếu tố:

1. Đưa số lớn nhất trong nhóm ra đầu bằng thứ tự đảo.
2. Chỉ nhận số mới nếu nó nhỏ hơn đầu heap.

Mã minh họa độc lập về cơ chế, chưa bao gồm kiểm tra đầu vào:

~~~java
PriorityQueue<Integer> heap =
        new PriorityQueue<>(Comparator.reverseOrder());

for (int value : input) {
    if (heap.size() < k) {
        heap.offer(value);
    } else if (value < heap.peek()) {
        heap.poll();
        heap.offer(value);
    }
}
~~~

Đoạn này giả sử `input` đã có và `k > 0`. Nếu chỉ đảo comparator mà giữ nguyên dấu so sánh của trường hợp K lớn nhất, thuật toán sẽ nhận sai ứng viên.

Với cùng dãy và K = 3, nhóm cuối là `[1, 2, 4]`. Một heap lớn hợp lệ:

~~~text
       4
      / \
     1   2
~~~

Đầu heap bằng 4 là số nhỏ thứ ba trong dữ liệu đã đọc khi đã đủ ba phần tử. Nó không phải số nhỏ nhất; số nhỏ nhất là phần tử tốt nhất, còn đầu heap được đặt ở phần tử yếu nhất để phục vụ thay thế.

Có thể kiểm tra chiều comparator bằng hai giá trị rất đơn giản. Nếu đang tìm số nhỏ nhất nhưng `peek()` của nhóm `[1, 2, 4]` lại là 1, hãy xem lại liệu bạn có đưa đúng phần tử cần loại lên đầu chưa.

## 6. Vì sao thuật toán đúng?

Một bất biến là điều luôn đúng tại ranh giới giữa các vòng lặp. Với top-K lớn nhất, sau khi đọc t phần tử, heap phải:

- Có đúng `min(K, t)` phần tử.
- Giữ một nhóm có các giá trị lớn nhất trong t phần tử đã đọc, tính cả số lần xuất hiện.
- Không có phần tử bị loại nào tốt hơn phần tử yếu nhất đang giữ, khi heap đã đầy.

Chứng minh bằng từng bước:

**Ban đầu:** chưa đọc phần tử nào, heap rỗng. Nhóm lớn nhất kích thước 0 là rỗng, nên bất biến đúng.

**Khi chưa đầy:** số phần tử đã đọc chưa vượt K. Tất cả chúng đều phải thuộc nhóm kết quả, nên thêm phần tử mới là đúng.

**Khi đã đầy và x không lớn hơn ngưỡng:** đã có K phần tử được giữ đều lớn hơn hoặc bằng ngưỡng, và do đó đều lớn hơn hoặc bằng x. Không cần nhận x để có K giá trị lớn nhất. Nếu x bằng ngưỡng, thay hay không thay có thể đổi danh tính lần xuất hiện, nhưng không đổi tập đa hợp các giá trị được chọn.

**Khi đã đầy và x lớn hơn ngưỡng:** x tốt hơn phần tử yếu nhất trong nhóm. Loại một phần tử ở ngưỡng rồi thêm x cho nhóm tốt hơn. Những phần tử đã bị loại trước đây không thể đột nhiên vượt các ứng viên hiện tại chỉ vì có thêm x; các giá trị cũ không thay đổi. Nhóm mới tiếp tục là một lựa chọn top-K hợp lệ.

Khi hết đầu vào, t = N, nên heap giữ `min(K, N)` phần tử lớn nhất. Đổi quan hệ lớn hơn thành nhỏ hơn và đổi loại heap cho chứng minh đối xứng của top-K nhỏ nhất.

Bất biến này mạnh hơn “heap không vượt K”. Một thuật toán có thể giữ đúng K phần tử nhưng chọn sai K phần tử. Ví dụ giữ K số lớn nhất trong heap lớn rồi cứ tràn là `poll()` sẽ loại chính số tốt nhất.

## 7. Hai kiểu cập nhật và lỗi chọn nhầm heap

Có hai kiểu viết phổ biến cho K lớn nhất.

**Kiểu so ngưỡng trước:** khi đầy, chỉ `poll()` rồi `offer()` nếu số mới tốt hơn ngưỡng. Heap không vượt K phần tử ở bất kỳ thời điểm nào trong vòng lặp.

**Kiểu thêm trước, cắt sau:** dùng heap nhỏ, thêm mọi phần tử rồi bỏ phần tử nhỏ nhất nếu kích thước vượt K.

~~~java
PriorityQueue<Integer> heap = new PriorityQueue<>();
for (int value : input) {
    heap.offer(value);
    if (heap.size() > k) {
        heap.poll();
    }
}
~~~

Đây là đoạn minh họa, yêu cầu `k >= 0`. Với K = 0, mỗi số được thêm rồi bị lấy ra ngay; kết quả rỗng nhưng vẫn duyệt hết đầu vào. Với K âm, đoạn này không thể hiện một hợp đồng hợp lệ; cần từ chối K âm trước vòng lặp.

Kiểu thêm trước có thể giữ tạm K + 1 phần tử, vẫn là O(K + 1) bộ nhớ phụ. Nó ngắn và dễ chứng minh: trong K + 1 ứng viên, loại số nhỏ nhất. Kiểu so ngưỡng trước tránh thao tác heap cho ứng viên chắc chắn bị loại, phù hợp khi có nhiều dữ liệu kém hơn ngưỡng. Cả hai đều dùng heap nhỏ cho K lớn nhất.

Xét lỗi dùng heap lớn với kiểu thêm trước, cắt sau:

~~~text
Dữ liệu: 7, 2, 9, 8
K: 3
Thêm đủ bốn số vào heap lớn.
poll() lấy 9 ra.
Còn lại: 8, 7, 2.
Kết quả đúng phải là: 9, 8, 7.
~~~

Lỗi không nằm ở khả năng tìm cực trị của heap lớn; nó tìm đúng số lớn nhất. Lỗi nằm ở việc đem phần tử tốt nhất đi loại. Cách chọn heap chỉ đúng khi gắn với vai trò cụ thể của `poll()`.

Nếu chọn cách giữ toàn bộ N phần tử rồi lấy K lần, K lớn nhất có thể dùng heap lớn. Khi đó `poll()` phục vụ xuất kết quả tốt nhất, không phục vụ cắt bớt nhóm ứng viên. Hai thiết kế có mục đích khác nhau và bộ nhớ khác nhau.

## 8. Tổng quát hóa: thứ tự kết quả và thứ tự của heap

Với dữ liệu đối tượng, viết hai thuật toán gần giống nhau dễ gây đảo dấu sai. Có thể thống nhất bằng một `Comparator` gọi là `bestFirst`:

~~~text
bestFirst.compare(a, b) < 0  nghĩa là a tốt hơn b.
bestFirst.compare(a, b) > 0  nghĩa là a kém hơn b.
bestFirst.compare(a, b) == 0 nghĩa là đồng hạng theo các tiêu chí đã chọn.
~~~

`bestFirst` cũng là thứ tự sắp kết quả đầu ra: phần tử tốt nhất đứng trước.

Để phần tử yếu nhất nằm ở đầu heap, dùng thứ tự đảo của toàn bộ `bestFirst`:

~~~java
PriorityQueue<T> heap = new PriorityQueue<>(bestFirst.reversed());
~~~

Khi heap đầy, phép kiểm tra thống nhất là:

~~~java
if (bestFirst.compare(value, heap.peek()) < 0) {
    heap.poll();
    heap.offer(value);
}
~~~

Một ứng viên tốt hơn đầu heap theo `bestFirst` mới được nhận. Đừng dùng comparator đảo của heap trong biểu thức này rồi giữ nguyên dấu `< 0`; như vậy bạn đã đảo cả ý nghĩa “tốt hơn”.

| Bài toán số nguyên | bestFirst dùng để xếp kết quả | Thứ tự heap = bestFirst.reversed() |
| --- | --- | --- |
| K lớn nhất | `Comparator.reverseOrder()` | Thứ tự tự nhiên, heap nhỏ |
| K nhỏ nhất | `Comparator.naturalOrder()` | Thứ tự đảo, heap lớn |

Phương thức `reversed()` đảo thứ tự; `thenComparing(...)` bổ sung tiêu chí khi các tiêu chí trước chưa phân hạng được. Những phép ghép này giữ mã so sánh dễ đọc hơn so với tự đổi dấu số học. [Tài liệu Comparator, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html).

### Đồng điểm và danh tính bản ghi

Giả sử mỗi ứng viên có `id` và `score`. Yêu cầu: điểm cao hơn đứng trước; nếu bằng điểm, mã có thứ tự chữ nhỏ hơn đứng trước.

~~~java
Comparator<Candidate> bestFirst = Comparator
        .comparingInt(Candidate::score).reversed()
        .thenComparing(Candidate::id);
~~~

Thứ tự kết quả là điểm giảm, mã tăng. Thứ tự heap là đảo của cả chuỗi trên, tức điểm tăng và khi bằng điểm thì mã giảm. Nhờ vậy, trong nhóm điểm 90 gồm A, B, C, bản ghi C là ứng viên yếu nhất khi cần giữ những mã nhỏ hơn.

Với dữ liệu `C:90, B:90, A:90, D:70, E:100` và K = 3, kết quả là `[E:100, A:90, B:90]`.

Nếu chỉ so sánh điểm, A, B và C đồng hạng. Kết quả vẫn có thể đúng theo điểm nhưng danh tính các bản ghi được giữ không có bảo đảm ổn định. Việc bỏ qua ứng viên bằng ngưỡng không biến heap thành một cấu trúc ổn định: khi một điểm cao hơn đến, heap có thể chọn bất kỳ bản ghi nào đang đồng hạng ở đầu để loại.

Nếu cần ưu tiên bản ghi đến sớm khi đồng điểm, gắn số thứ tự đến tăng dần vào từng bản ghi và đưa nó vào tiêu chí phụ. Nếu muốn kết quả độc lập thứ tự nhập, dùng một mã định danh ổn định làm tiêu chí phụ. Hai yêu cầu này không giống nhau.

Các khóa dùng để xếp hạng nên bất biến khi nằm trong heap. Đổi trực tiếp `score` của đối tượng đã được thêm không tự sắp lại heap. Một comparator phụ thuộc vào cấu hình đang thay đổi giữa vòng lặp cũng phá vỡ lập luận về ngưỡng.

### Không dùng phép trừ để đảo thứ tự

Tránh:

~~~java
Comparator<Integer> unsafe = (a, b) -> b - a;
~~~

Ví dụ `b = Integer.MAX_VALUE` và `a = -1` làm phép trừ vượt miền `int`. Kết quả bị tràn có thể mang dấu sai, dẫn đến so sánh trái ý định.

Dùng `Comparator.reverseOrder()`, `Integer.compare(b, a)` hoặc `Comparator.comparingInt(...)`. Khi đảo comparator có sẵn, dùng `reversed()`; không tự viết `-comparator.compare(a, b)` vì giá trị trả về có thể là `Integer.MIN_VALUE`. [Tài liệu Integer.compare, Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html#compare(int,int)).

## 9. Chương trình Java 17+ hoàn chỉnh

Tệp `TopKHeapDemo.java` dưới đây có một hàm tổng quát dùng được với `Iterable`. Nó trả kết quả đã sắp theo `bestFirst`, không sửa cấu trúc đầu vào, không yêu cầu biết trước N và không cấp phát sẵn mảng có K chỗ.

Hợp đồng của `topK`:

- `k < 0`: ném `IllegalArgumentException`.
- `k == 0`: trả danh sách rỗng, không duyệt các phần tử nguồn.
- `k > N`: trả toàn bộ N phần tử theo thứ tự yêu cầu.
- `input` và `bestFirst` phải khác `null`, kể cả khi K = 0.
- Với K > 0, mỗi phần tử được đọc phải khác `null` và comparator phải so sánh được chúng.
- Phần tử trùng được tính theo từng lần xuất hiện; hàm không khử trùng.
- Kết quả là một danh sách giá trị được chọn; không có cam kết về khả năng sửa đổi danh sách trả về. Đặc biệt, nhánh K = 0 dùng `List.of()`.

Đoạn kiểm chứng so sánh kết quả với cách sắp toàn bộ một bản sao rồi lấy tối đa K phần tử. Nó kiểm tra cả hai chiều xếp hạng, dữ liệu trùng, số âm, dãy rỗng, K lớn hơn N và các số ở biên `int`. Đây là kiểm tra tính đúng của kết quả, không phải phép đo tốc độ.

~~~java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;

public class TopKHeapDemo {
    record Candidate(String id, int score) {
        @Override
        public String toString() {
            return id + ":" + score;
        }
    }

    static <T> List<T> topK(
            Iterable<? extends T> input,
            int k,
            Comparator<? super T> bestFirst) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(bestFirst, "bestFirst");
        if (k < 0) {
            throw new IllegalArgumentException("k must be >= 0");
        }
        if (k == 0) {
            return List.of();
        }

        PriorityQueue<T> heap = new PriorityQueue<>(bestFirst.reversed());
        for (T value : input) {
            Objects.requireNonNull(value, "element");
            if (heap.size() < k) {
                heap.offer(value);
            } else if (bestFirst.compare(value, heap.peek()) < 0) {
                heap.poll();
                heap.offer(value);
            }
        }
        List<T> result = new ArrayList<>(heap);
        result.sort(bestFirst);
        return result;
    }

    static void keepLargest(PriorityQueue<Integer> heap, int value, int k) {
        if (heap.size() < k) {
            heap.offer(value);
        } else if (value > heap.peek()) {
            heap.poll();
            heap.offer(value);
        }
    }

    static List<Integer> descendingSnapshot(PriorityQueue<Integer> heap) {
        List<Integer> copy = new ArrayList<>(heap);
        copy.sort(Comparator.reverseOrder());
        return copy;
    }

    static List<Integer> expected(
            List<Integer> input, int k, Comparator<Integer> order) {
        List<Integer> copy = new ArrayList<>(input);
        copy.sort(order);
        return new ArrayList<>(copy.subList(0, Math.min(k, copy.size())));
    }

    static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("top-K mismatch");
        }
    }

    static int selfCheck() {
        Random random = new Random(20261019L);
        int checks = 0;
        for (int test = 0; test < 250; test++) {
            int n = random.nextInt(25);
            int k = random.nextInt(31);
            List<Integer> input = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                input.add(random.nextInt(21) - 10);
            }
            List<Integer> before = new ArrayList<>(input);
            for (Comparator<Integer> order : List.of(
                    Comparator.<Integer>naturalOrder(),
                    Comparator.<Integer>reverseOrder())) {
                check(topK(input, k, order).equals(expected(input, k, order)));
                check(input.equals(before));
                checks++;
            }
        }

        List<Integer> extremes = List.of(
                Integer.MIN_VALUE, 0, Integer.MAX_VALUE, -1, 1);
        for (int k = 0; k <= 6; k++) {
            for (Comparator<Integer> order : List.of(
                    Comparator.<Integer>naturalOrder(),
                    Comparator.<Integer>reverseOrder())) {
                check(topK(extremes, k, order).equals(expected(extremes, k, order)));
                checks++;
            }
        }
        return checks;
    }

    public static void main(String[] args) {
        List<Integer> input = List.of(7, 2, 9, 4, 9, 1, 8, 5);
        List<Integer> before = new ArrayList<>(input);
        PriorityQueue<Integer> heap = new PriorityQueue<>();
        int step = 0;
        for (int value : input) {
            keepLargest(heap, value, 3);
            System.out.println("step=" + (++step)
                    + " value=" + value
                    + " kept=" + descendingSnapshot(heap)
                    + " boundary=" + heap.peek());
        }
        System.out.println("largest 3=" + topK(input, 3, Comparator.reverseOrder()));
        System.out.println("smallest 3=" + topK(input, 3, Comparator.naturalOrder()));
        System.out.println("source unchanged=" + input.equals(before));
        System.out.println("k=0=" + topK(input, 0, Comparator.reverseOrder()));
        System.out.println("k>n=" + topK(List.of(4, 1), 5, Comparator.reverseOrder()));
        System.out.println("empty=" + topK(List.<Integer>of(), 3, Comparator.naturalOrder()));
        System.out.println("duplicates="
                + topK(List.of(9, 9, 9, 8), 2, Comparator.reverseOrder()));

        List<Integer> extremes = List.of(
                Integer.MIN_VALUE, 0, Integer.MAX_VALUE, -1, 1);
        System.out.println("largest extremes="
                + topK(extremes, 2, Comparator.reverseOrder()));
        System.out.println("smallest extremes="
                + topK(extremes, 2, Comparator.naturalOrder()));
        System.out.println("largest 1=" + topK(input, 1, Comparator.reverseOrder()));

        List<Integer> pollOrder = new ArrayList<>();
        PriorityQueue<Integer> copy = new PriorityQueue<>(heap);
        while (!copy.isEmpty()) {
            pollOrder.add(copy.poll());
        }
        System.out.println("min-heap poll order=" + pollOrder);
        System.out.println("original heap size=" + heap.size());

        List<Candidate> candidates = List.of(
                new Candidate("C", 90),
                new Candidate("B", 90),
                new Candidate("A", 90),
                new Candidate("D", 70),
                new Candidate("E", 100));
        Comparator<Candidate> bestFirst = Comparator
                .comparingInt(Candidate::score).reversed()
                .thenComparing(Candidate::id);
        System.out.println("candidates=" + topK(candidates, 3, bestFirst));

        try {
            topK(input, -1, Comparator.naturalOrder());
        } catch (IllegalArgumentException exception) {
            System.out.println("negative k=" + exception.getMessage());
        }
        System.out.println("reference checks=" + selfCheck());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~shell
javac --release 17 -encoding UTF-8 TopKHeapDemo.java
java TopKHeapDemo
~~~

Lệnh `javac --release 17` yêu cầu JDK 17 hoặc mới hơn hỗ trợ phiên bản đích này. Chương trình chỉ dùng API và cú pháp có từ Java 17 trở về trước.

Stdout chính xác:

~~~text
step=1 value=7 kept=[7] boundary=7
step=2 value=2 kept=[7, 2] boundary=2
step=3 value=9 kept=[9, 7, 2] boundary=2
step=4 value=4 kept=[9, 7, 4] boundary=4
step=5 value=9 kept=[9, 9, 7] boundary=7
step=6 value=1 kept=[9, 9, 7] boundary=7
step=7 value=8 kept=[9, 9, 8] boundary=8
step=8 value=5 kept=[9, 9, 8] boundary=8
largest 3=[9, 9, 8]
smallest 3=[1, 2, 4]
source unchanged=true
k=0=[]
k>n=[4, 1]
empty=[]
duplicates=[9, 9]
largest extremes=[2147483647, 1]
smallest extremes=[-2147483648, -1]
largest 1=[9]
min-heap poll order=[8, 9, 9]
original heap size=3
candidates=[E:100, A:90, B:90]
negative k=k must be >= 0
reference checks=514
~~~

### Đọc kết quả để đối chiếu cơ chế

`kept` được tạo bằng cách sao chép heap sang danh sách rồi sắp giảm, nên có thứ tự dễ kiểm tra. Đây là thao tác phục vụ in dấu vết; nó không tham gia quyết định nhận hay loại phần tử.

Tám dòng `step` khớp bảng theo dõi ở trên. Sau bước 5, ngưỡng là 7; bước 6 gặp 1 nên nhóm giữ nguyên; bước 7 gặp 8 nên thay 7. Dòng `largest 3` và `smallest 3` thể hiện cùng hàm tổng quát với hai comparator khác nhau.

`min-heap poll order=[8, 9, 9]` cho thấy một điểm cần phân biệt: heap giữ ba số lớn nhất nhưng lấy ra theo thứ tự tăng. Đó là hệ quả của việc đặt ứng viên yếu nhất ở đầu. Chương trình lấy trên một bản sao, nên `original heap size=3` xác nhận heap ban đầu chưa bị tiêu thụ.

`candidates` kiểm tra tiêu chí phụ bằng mã. `reference checks=514` gồm 250 bộ ngẫu nhiên với hai chiều so sánh, cộng bảy giá trị K từ 0 đến 6 trên bộ dữ liệu biên với hai chiều. Bất kỳ khác biệt nào với kết quả sắp toàn bộ sẽ ném `AssertionError` thay vì in một số kiểm tra thành công.

Các bộ ngẫu nhiên giúp bắt lỗi đảo chiều và xử lý biên, còn chứng minh bằng bất biến giải thích tại sao thuật toán đúng cho dữ liệu ngoài các bộ thử.


## 10. Xuất kết quả: chọn đúng phần tử chưa đồng nghĩa đã có đúng thứ tự

Có hai đầu việc riêng: chọn các phần tử cần giữ và sắp chúng theo thứ tự muốn trình bày. Trong hàm `topK`, heap giải quyết đầu việc thứ nhất; `result.sort(bestFirst)` giải quyết đầu việc thứ hai.

Không được coi `heap.toString()`, vòng `for-each` hoặc `heap.stream().toList()` là danh sách đã xếp hạng. Chúng duyệt các phần tử của heap, không mô phỏng chuỗi `poll()`. Nếu cần thứ tự rõ ràng, hãy sao chép rồi sắp hoặc lấy lần lượt từ heap. Chương trình đã chọn cách sao chép rồi sắp để tránh phụ thuộc vào thứ tự duyệt.

Ba cách xuất thường gặp:

| Cách xuất | Thứ tự thu được | Ảnh hưởng đến heap |
| --- | --- | --- |
| Sao chép sang danh sách rồi `sort(bestFirst)` | Tốt nhất trước | Giữ nguyên heap |
| `poll()` liên tục từ heap giới hạn K | Yếu nhất trước | Tiêu thụ heap |
| Sao chép heap, `poll()` hết rồi đảo danh sách | Tốt nhất trước, trừ thứ tự giữa các phần tử đồng hạng chưa được phân biệt | Giữ nguyên heap gốc |

Ví dụ heap nhỏ chứa ba số lớn nhất `[9, 9, 8]`. Chuỗi `poll()` là `8, 9, 9`. Nếu muốn kết quả giảm dần, đảo chuỗi đó hoặc sắp danh sách sao chép theo thứ tự giảm.

Khi chỉ cần giá trị lớn thứ K, không cần sắp các ứng viên: sau khi đã đọc ít nhất K phần tử và K > 0, `peek()` của heap nhỏ là đáp án. Với giá trị nhỏ thứ K, dùng `peek()` của heap lớn. Nếu dữ liệu chưa đủ K phần tử, không có phần tử thứ K dù heap vẫn có một đầu hợp lệ.

Nếu chỉ cần một nhóm ứng viên mà thứ tự không quan trọng, có thể bỏ bước sắp kết quả để tiết kiệm chi phí. Hãy thể hiện điều đó trong hợp đồng hàm; đừng trả một `List` chưa sắp nhưng mô tả nó là bảng xếp hạng.

Hàm trong bài sao chép các tham chiếu đối tượng, không sao chép sâu đối tượng. Việc không thay đổi cấu trúc đầu vào không bảo vệ được kết quả khỏi các thay đổi sau này trên chính những đối tượng có thể sửa được.

## 11. Phân tích chi phí theo số phần tử thực sự giữ

Gọi N là số phần tử đầu vào và M = min(K, N), với K > 0. Tạm giả sử đọc một phần tử và so sánh hai phần tử có chi phí O(1).

Heap chỉ giữ tối đa M phần tử. Phép so với ngưỡng không cần tìm kiếm cả heap. Việc đưa một phần tử lên hoặc xuống theo đường từ gốc tới lá có số mức tăng theo logarit kích thước heap; có thể đối chiếu các thao tác `siftUp` và `siftDown` trong [mã nguồn PriorityQueue của OpenJDK 17](https://raw.githubusercontent.com/openjdk/jdk/jdk-17%2B35/src/java.base/share/classes/java/util/PriorityQueue.java).

| Công việc trong thuật toán | Chi phí |
| --- | --- |
| Đọc mỗi phần tử và so với ngưỡng khi đã đầy | O(1) cho phần quyết định |
| Thêm hoặc thay thế ứng viên | O(log(M + 1)) theo phân tích heap |
| Duyệt và chọn trên toàn bộ N phần tử | O(N log(M + 1)) ở trường hợp xấu nhất |
| Sao chép M phần tử kết quả | O(M) |
| Sắp M phần tử theo thứ tự đầu ra | O(M log(M + 1)) |
| Bộ nhớ phụ cho heap và danh sách trả về | O(M + 1), không tính nguồn đầu vào |

Dùng `log(M + 1)` để biểu thức vẫn mô tả đúng trường hợp K = 1. Viết “O(N log K)” là cách nói quen thuộc khi `2 <= K <= N`, nhưng thay K = 1 vào rồi kết luận chi phí bằng 0 là sai. Ta vẫn phải đọc N phần tử để tìm cực trị.

Vì M <= N, toàn bộ hàm với bước sắp kết quả có giới hạn O(N log(M + 1)) cho K > 0, nhưng tách chi phí chọn và chi phí xuất giúp hiểu rõ yêu cầu nào đang tiêu tốn công việc. Với K = 0, hàm đã cho trả ngay sau kiểm tra tham chiếu và K; không có lượt duyệt đầu vào.

Kiểu so ngưỡng trước có thể làm ít thao tác heap hơn giới hạn trường hợp xấu nhất. Nếu sau khi heap đầy có rất ít ứng viên tốt hơn ngưỡng, phần lớn phần tử chỉ được đọc rồi so sánh một lần. Nếu R là số lần thay thế sau khi đã đầy, phần lựa chọn có thể được mô tả chi tiết bằng O(N + (M + R) log(M + 1)). Đây là cách tách công việc, không phải lời bảo đảm rằng dữ liệu thực tế luôn khiến R nhỏ.

Các đánh giá trên nói về thuật toán. Mảng nội bộ của hàng đợi đôi lúc phải tăng dung lượng và sao chép khi nhận thêm phần tử; không diễn giải ký hiệu logarit thành lời hứa rằng mọi lời gọi `offer` đều có cùng độ trễ. Khi nhóm đã đầy và cách cập nhật luôn `poll` trước `offer`, số phần tử không tăng thêm so với kích thước đã đạt.

Nếu comparator tính một đại lượng đắt tiền mỗi lần so, chi phí của nó cũng phải được tính. Ví dụ, không nên phân tích lại một chuỗi JSON hay gọi dịch vụ bên ngoài trong mỗi phép so sánh. Tính trước khóa xếp hạng một lần, lưu cùng bản ghi bất biến, rồi so các khóa có chi phí rõ ràng.

Bộ nhớ O(K) là lợi ích của quá trình chọn khi K nhỏ; nó không xóa chi phí của một danh sách N phần tử đã được tải sẵn. Với dữ liệu rất lớn, cần nối hàm với nguồn có thể duyệt từng phần tử thay vì luôn tạo cả `List` đầu vào. Đồng thời, một nguồn vô hạn sẽ không làm hàm trả về: muốn quan sát top-K liên tục, phải duy trì bộ tích lũy và cung cấp thao tác chụp kết quả riêng.

### Chi phí in dấu vết không phải chi phí cốt lõi

Trong chương trình minh chứng, mỗi dòng `step` gọi `descendingSnapshot`, tức sao chép và sắp nhóm đang giữ để in đẹp. Nếu làm việc này cho mọi phần tử của một luồng lớn, nó cộng chi phí khoảng O(N K log(K + 1)) khi K <= N, cùng chi phí xuất văn bản.

Vì vậy, không dùng thời gian chạy của phần in dấu vết để kết luận chi phí chọn top-K. Hàm `topK` chỉ sắp một lần ở cuối. Khi triển khai thực tế, có thể bỏ in từng bước hoặc chỉ chụp ở những thời điểm cần quan sát.

## 12. Hợp đồng biên và các yêu cầu dễ bị đánh đồng

### K bằng 0, âm hoặc lớn hơn N

K = 0 là yêu cầu chọn không phần tử nào. Trả rỗng ngay là một hợp đồng tiện dụng, nhưng nó cũng có nghĩa không đọc và không kiểm tra các phần tử bên trong nguồn. Một nguồn có phần tử `null` sẽ chưa bị phát hiện ở nhánh này; không được mô tả hàm là đã kiểm tra toàn bộ dữ liệu khi K = 0.

K âm không biểu diễn số phần tử cần lấy. Từ chối nó bằng ngoại lệ làm lỗi của nơi gọi lộ rõ. Nếu ứng dụng muốn coi K âm như 0, phải công bố hợp đồng khác thay vì để hành vi phát sinh ngẫu nhiên từ vòng lặp.

K lớn hơn N có hai lựa chọn thiết kế thường gặp: trả tối đa K phần tử, hoặc bắt buộc đủ K và báo lỗi nếu không đủ. Hàm trong bài chọn “tối đa K”, nên `topK([4, 1], 5, giảm)` trả `[4, 1]`. Đối với API “phần tử lớn thứ năm”, cùng dữ liệu phải báo không tồn tại; không được lấy `peek()` rồi gọi 1 là phần tử lớn thứ năm.

Không nên dùng `new PriorityQueue<>(k)` như cách áp giới hạn. Tham số đó là dung lượng ban đầu, không phải số phần tử tối đa. Nó còn không nhận 0 và có thể cấp phát quá lớn nếu K do đầu vào bên ngoài cung cấp. Hàm tổng quát dùng constructor nhận comparator và tự giới hạn số phần tử bằng điều kiện trong vòng lặp.

### Đúng K phần tử và lấy tất cả phần tử đồng hạng ở ngưỡng

Với `[100, 90, 90, 90, 80]` và K = 2:

- “Lấy đúng tối đa hai phần tử” có thể trả `[100, 90]`.
- “Lấy hai mức điểm cao nhất” trả các mức `[100, 90]` nhưng đó là hai giá trị điểm phân biệt.
- “Lấy top hai và tất cả người đồng điểm với người đứng thứ hai” có thể phải trả bốn bản ghi.

Yêu cầu cuối không còn giới hạn kết quả ở K phần tử. Nếu N bản ghi cùng điểm và chính sách yêu cầu giữ tất cả người đồng hạng ở ngưỡng, kích thước kết quả có thể là N. Không hứa bộ nhớ chỉ O(K) cho toàn bộ kết quả của yêu cầu đó.

Có thể cần hai lượt: lượt đầu xác định ngưỡng, lượt sau lấy tất cả bản ghi đạt ngưỡng, nếu nguồn cho phép đọc lại. Nếu chỉ có một lượt và không thể phục hồi dữ liệu đã bỏ, phải thiết kế lưu trữ phù hợp với chính sách đồng hạng ngay từ đầu.

### Top-K giá trị phân biệt

`PriorityQueue` không phải `Set`. Với `[9, 9, 8, 7]`, K = 3, bài toán đếm từng lần xuất hiện trả `[9, 9, 8]`; bài toán giá trị phân biệt trả `[9, 8, 7]`.

Một cách đơn giản cho dữ liệu hữu hạn là khử trùng bằng `HashSet` trước rồi chạy chọn. Nếu có D giá trị phân biệt, bộ nhớ cho bước khử trùng có thể là O(D), nên không được tiếp tục quảng bá cả quy trình là chỉ dùng O(K) bộ nhớ.

Có những thiết kế phối hợp heap với một tập các giá trị đang được giữ để tránh lưu tất cả giá trị từng thấy. Đó là một thuật toán cần duy trì đồng bộ khi thêm và loại ứng viên; không thể đạt được chỉ bằng cách đặt tên biến là “distinct”. Trong phạm vi bài này, hàm chạy được cố ý giữ nguyên từng lần xuất hiện.

### Top-K theo tần suất

Tìm ba số lớn nhất không giống tìm ba số xuất hiện nhiều nhất. Tần suất là thuộc tính của toàn bộ quá trình đếm, không phải độ lớn của từng số.

Một lời giải cho dữ liệu hữu hạn thường có hai giai đoạn: dùng `Map` tính tần suất theo giá trị, sau đó xếp hạng các cặp giá trị–tần suất bằng heap giới hạn K. Bộ nhớ gồm bản đồ tần suất, không chỉ heap. Nếu cập nhật tần suất trực tiếp của đối tượng đã nằm trong heap mà không sửa lại cấu trúc, ngưỡng có thể sai.

### Đổi dữ liệu đã đọc, xóa phần tử và cửa sổ trượt

Bất biến top-K trong bài giả định mỗi giá trị xếp hạng giữ nguyên và dữ liệu chỉ được thêm. Khi điểm cũ thay đổi hoặc một phần tử rời cửa sổ thời gian, những ứng viên đã loại có thể cần quay lại.

Ví dụ K = 2, từng đọc `10, 9, 8`, heap chỉ còn `[10, 9]`. Nếu 10 hết hạn, kết quả đúng là `[9, 8]`, nhưng heap đã bỏ 8 và không tự tái tạo được nó. Không thể giải top-K cửa sổ trượt chính xác chỉ bằng trạng thái của heap top-K thêm mới này.

Cần giữ thêm dữ liệu, chỉ mục hoặc cấu trúc cho các phần tử ngoài nhóm được chọn. Việc đổi sang hàng đợi ưu tiên an toàn luồng cũng không tự giải quyết thiếu dữ liệu hay làm cả chuỗi “kiểm tra ngưỡng → loại → thêm” trở thành một thao tác nguyên tử.

## 13. Chọn cách làm theo yêu cầu thực tế

Heap giới hạn K là một công cụ phù hợp khi chỉ cần một nhóm nhỏ và có thể đọc tuần tự. Nó không mặc định là phương án tốt nhất cho mọi kích thước K.

| Tình huống | Hướng xử lý hợp lý | Điều cần tính thêm |
| --- | --- | --- |
| K nhỏ hơn nhiều so với N, cần một lượt đọc | Heap giới hạn K | Bước sắp K kết quả nếu cần thứ tự |
| K = 1 | Duyệt giữ một cực trị | O(N) thời gian, O(1) trạng thái; không nhất thiết cần heap |
| K gần N và cần toàn bộ kết quả theo thứ tự | Sắp bản sao đầu vào | Heap vẫn đúng nhưng có thể không đem lại lợi ích |
| Đã cần lưu toàn bộ heap để liên tục lấy phần tử tốt nhất | Heap toàn bộ dữ liệu | O(N) bộ nhớ cho heap, mục đích khác với cắt top-K |
| Mảng đã ở bộ nhớ, chỉ cần một lần chọn ngưỡng | Thuật toán phân hoạch chọn hạng có thể phù hợp | Cách phân hoạch, trường hợp xấu, khả năng sửa mảng và sắp phần kết quả |
| Đầu vào đã được sắp theo đúng thứ tự tốt nhất trước | Lấy tối đa K phần tử đầu | Chỉ dừng sớm khi có bảo đảm thứ tự nguồn |

Không nên đọc K phần tử đầu của nguồn chưa sắp rồi dừng. Dù K số ban đầu đều rất lớn, một số tốt hơn vẫn có thể xuất hiện ở cuối. Với dữ liệu không có bảo đảm bổ sung, phải xét toàn bộ nguồn hữu hạn để kết luận top-K chính xác.

Nếu dùng heap toàn bộ dữ liệu bằng cách `offer` từng phần tử, riêng bước dựng theo cách này có giới hạn O(N log(N + 1)), rồi lấy K lần tốn thêm các thao tác logarit. Không nhầm cách dựng đó với một thuật toán dựng heap hàng loạt có phân tích khác. Để quyết định dùng heap giới hạn K, lợi ích rõ nhất là số ứng viên được giữ chỉ khoảng K khi K nhỏ.

Việc chọn theo điểm cao nhất, khoảng cách ngắn nhất hoặc thời gian nhanh nhất có thể dùng cùng hàm tổng quát. Thay đổi `bestFirst` cho đúng ngữ nghĩa, rồi kiểm tra bằng một cặp ví dụ: bản ghi nào tốt hơn phải có kết quả `compare` âm khi đứng ở đối số thứ nhất.

Ví dụ với khoảng cách nguyên đã tính sẵn, khoảng cách nhỏ hơn tốt hơn thì `Comparator.comparingInt(Item::distance)` là thứ tự kết quả. Heap phải dùng thứ tự đảo để đưa khoảng cách xa nhất trong nhóm đang chọn ra đầu. Nếu tự tính bình phương khoảng cách từ tọa độ nguyên, còn phải xử lý tràn số của phép tính khóa trước khi bàn đến heap; comparator tốt không sửa được một khóa đã bị tính sai.

## 14. Lỗi thường gặp và cách tự phát hiện

| Lỗi | Dấu hiệu quan sát được | Cách sửa |
| --- | --- | --- |
| Giữ K lớn nhất bằng heap lớn và tràn là `poll` | Số lớn nhất biến mất | Đặt phần tử cần loại ở đầu: heap nhỏ |
| Đổi loại heap nhưng quên đổi chiều nhận | Ứng viên kém lại được thêm | So sánh theo một `bestFirst` duy nhất |
| So với `peek` khi heap rỗng | Ngoại lệ do mở hộp `null` | Xử lý K = 0 và nhánh chưa đầy trước |
| Luôn nhận mọi số nhưng không cắt bớt | Kết quả vượt K, bộ nhớ tăng theo N | Duy trì bất biến kích thước sau mỗi bước |
| Dùng `contains` để loại trùng tùy tiện | Thay đổi ngữ nghĩa và thêm tìm kiếm tuyến tính | Quyết định rõ đếm lần xuất hiện hay giá trị phân biệt |
| Dùng `remove(value)` để loại ngưỡng | Tìm kiếm không cần thiết và có thể hiểu nhầm overload | Dùng `poll()` để loại đầu heap |
| In trực tiếp heap làm kết quả đã sắp | Nhìn thấy một dãy không đúng thứ tự mong muốn | Sao chép rồi sắp hoặc lấy hết có kiểm soát |
| Gọi `poll` trên heap còn dùng tiếp | Kích thước và trạng thái bị mất | Chụp bản sao trước khi xuất theo cách tiêu thụ |
| Comparator dùng phép trừ số nguyên | Sai ở giá trị biên dù ví dụ nhỏ đúng | Dùng các hàm so sánh an toàn |
| Cho rằng đồng điểm tự giữ thứ tự đến | Danh tính ở ranh giới thay đổi | Thêm khóa phụ đúng chính sách |
| Thay đổi khóa điểm khi đối tượng còn trong heap | `peek` không còn là ngưỡng đúng | Giữ khóa bất biến hoặc thiết kế cập nhật cấu trúc |
| Coi dung lượng ban đầu là giới hạn K | Hàng đợi vẫn nhận quá K phần tử | Áp điều kiện kích thước trong thuật toán |

Một cách kiểm tra nhanh rất hiệu quả là dùng dữ liệu `[7, 2, 9, 8]` với K = 3, rồi thử K = 0, K = 1, K > N, dãy toàn số bằng nhau và dãy có `Integer.MIN_VALUE`/`Integer.MAX_VALUE`. Sau đó đối chiếu kết quả đã sắp với một lời giải đơn giản bằng sắp toàn bộ. Dữ liệu ngẫu nhiên nên là phần bổ sung cho các trường hợp biên có chủ đích.

## 15. Bài tập và lời giải

### Bài 1 — Chọn heap theo phần tử cần loại

Cần giữ năm sản phẩm có điểm đánh giá cao nhất. Điểm cao hơn tốt hơn. Dùng heap nhỏ hay heap lớn nếu chỉ giữ tối đa năm ứng viên?

**Lời giải:** dùng heap nhỏ theo điểm. Trong năm ứng viên, sản phẩm có điểm thấp nhất là ứng viên yếu nhất; đưa nó ra đầu để so với sản phẩm mới. Nếu cần chọn theo điểm rồi mã sản phẩm, phải đảo đúng toàn bộ thứ tự kết quả để làm comparator của heap.

### Bài 2 — Theo dõi K nhỏ nhất

Dữ liệu `[6, 3, 8, 1, 5, 2]`, K = 3. Hãy ghi các giá trị được giữ sau mỗi bước và đầu heap.

**Lời giải:** dùng heap lớn. Trình bày nhóm theo thứ tự tăng cho dễ đọc:

| Số vừa đọc | Nhóm được giữ | Đầu heap lớn |
| --- | --- | --- |
| 6 | [6] | 6 |
| 3 | [3, 6] | 6 |
| 8 | [3, 6, 8] | 8 |
| 1 | [1, 3, 6] | 6 |
| 5 | [1, 3, 5] | 5 |
| 2 | [1, 2, 3] | 3 |

Khi đầy, ứng viên mới phải nhỏ hơn đầu heap mới được nhận. Đáp án cuối là `[1, 2, 3]`.

### Bài 3 — Tìm phản ví dụ cho heap sai chiều

Một chương trình muốn giữ hai số lớn nhất, dùng heap lớn; sau mỗi lần thêm, nếu kích thước vượt hai thì gọi `poll()`. Cho phản ví dụ ngắn.

**Lời giải:** `[1, 2, 3]`. Sau lần thêm thứ ba, heap lớn lấy 3 ra và giữ 1, 2; đáp án đúng phải là 3, 2. Thuật toán đã loại phần tử tốt nhất.

### Bài 4 — Phần tử lớn thứ K

Với heap nhỏ đang giữ ba số lớn nhất của dữ liệu đã đọc, `peek()` bằng 8. Có thể kết luận giá trị lớn thứ ba là 8 không?

**Lời giải:** có, nếu K = 3, đã đọc ít nhất ba phần tử, heap đang giữ đúng bất biến và tính cả các lần xuất hiện trùng. Nếu mới chỉ đọc một hoặc hai phần tử, không có phần tử lớn thứ ba. Nếu bài toán yêu cầu ba giá trị phân biệt, phải kiểm tra một bất biến khác có khử trùng.

### Bài 5 — Trùng giá trị và đồng hạng

Dữ liệu `[10, 10, 9, 8]`, K = 2. So sánh kết quả của top-K lần xuất hiện và top-K giá trị phân biệt.

**Lời giải:** lần xuất hiện cho `[10, 10]`; giá trị phân biệt cho `[10, 9]`. `PriorityQueue` không tự loại trùng. Chỉ thay điều kiện `>` thành `>=` không biến bài toán đầu thành bài toán thứ hai.

### Bài 6 — Tiêu chí phụ ở ranh giới

Dữ liệu `C:90, B:90, A:90, E:100`, K = 2. Yêu cầu điểm giảm, mã tăng. Đáp án là gì và phần tử nào yếu hơn giữa A:90 và B:90?

**Lời giải:** đáp án `[E:100, A:90]`. Giữa hai bản ghi điểm 90, B:90 yếu hơn A:90 vì mã B đứng sau mã A. Nếu chỉ so điểm, chương trình không có căn cứ chọn A thay B theo yêu cầu này.

### Bài 7 — Đảo comparator toàn bộ hay một tiêu chí?

Cho thứ tự kết quả “điểm giảm, mã tăng”. Nếu heap dùng “điểm tăng, mã tăng”, đã đúng chưa?

**Lời giải:** chưa. Trong nhóm cùng điểm, heap này đưa mã nhỏ lên đầu, tức đưa bản ghi tốt hơn ra trước để loại. Heap đúng phải là đảo toàn bộ thứ tự kết quả: điểm tăng, mã giảm. Dùng `bestFirst.reversed()` tránh bỏ sót tiêu chí phụ.

### Bài 8 — Thứ tự lấy ra khỏi heap

Heap nhỏ giữ ba số lớn nhất, các giá trị là 12, 8, 10. `poll()` liên tục trả gì? Cần làm gì để trả danh sách giảm dần mà vẫn giữ heap?

**Lời giải:** chuỗi lấy ra là `8, 10, 12`. Sao chép các phần tử sang danh sách rồi sắp theo thứ tự giảm, hoặc sao chép cả heap, lấy hết bản sao rồi đảo danh sách. Không dùng thứ tự `for-each` như một bảo đảm xếp hạng.

### Bài 9 — K = 0 có đọc phần tử không?

Với hàm `topK` trong bài, gọi K = 0 trên một nguồn hợp lệ nhưng phần tử bên trong có thể chứa `null`. Hàm có phát hiện `null` không?

**Lời giải:** không duyệt nguồn nên không phát hiện phần tử `null` bên trong. Nó vẫn kiểm tra tham chiếu `input` và `bestFirst` trước khi trả rỗng. Nếu nghiệp vụ bắt buộc xác thực toàn bộ nguồn dù không chọn kết quả nào, cần thiết kế một bước xác thực riêng hoặc thay đổi hợp đồng.

### Bài 10 — Chi phí khi K rất nhỏ

N = một triệu, K = 10. Có cần giữ cả một triệu phần tử trong heap không? Nếu chỉ muốn ngưỡng lớn thứ mười thì cần sắp danh sách kết quả không?

**Lời giải:** không cần giữ toàn bộ; heap giới hạn chỉ giữ tối đa mười phần tử, còn bộ nhớ của nguồn phụ thuộc cách đọc. Sau khi đọc đủ và hoàn tất, ngưỡng là `peek()` của heap nhỏ; không cần sắp mười phần tử nếu chỉ cần giá trị ngưỡng. Vẫn phải đọc toàn bộ nguồn chưa sắp để bảo đảm không bỏ sót số tốt hơn.

### Bài 11 — Vì sao không dùng lại trực tiếp cho cửa sổ trượt?

K = 2, đã đọc 10, 9, 8 và chỉ giữ 10, 9. Khi 10 hết hạn, cần kết quả gì và vì sao heap hiện tại chưa đủ?

**Lời giải:** cần 9, 8, nhưng 8 đã bị bỏ. Heap không thể suy ra lại một dữ liệu không còn được lưu. Cần cấu trúc bổ sung cho các phần tử trong cửa sổ và cách xử lý phần tử hết hạn; chỉ xóa 10 khỏi heap không hoàn thành bài toán.

### Bài 12 — Mở rộng phép đối chiếu

Hãy thêm vào chương trình một phép kiểm tra danh sách ứng viên có cùng điểm nhưng mã khác nhau, so kết quả `topK` với bản sao sắp theo `bestFirst` rồi lấy đầu. Nêu thêm một điều không được kết luận từ việc kiểm tra thành công.

**Lời giải:** dùng lại `candidates` và `bestFirst`, tạo `ArrayList<Candidate>`, sắp nó bằng `bestFirst`, lấy `subList(0, Math.min(k, size))` rồi so danh sách kết quả. Lặp K từ 0 đến lớn hơn số ứng viên để kiểm tra cả biên. Thành công không chứng minh comparator của mọi nghiệp vụ đều đúng, không đo được tốc độ, và không cho phép kết luận heap ổn định nếu đã bỏ tiêu chí phụ.

## 16. Tự kiểm tra trước khi áp dụng

Trước khi viết mã, hãy phát biểu bằng lời: “Tôi giữ tối đa K phần tử tốt nhất theo thứ tự nào; phần tử yếu nhất trong nhóm là gì; ứng viên mới phải thỏa điều kiện nào để thay nó”. Nếu ba câu này chưa nhất quán, việc chọn heap theo trực giác rất dễ sai.

Khi rà soát lời giải, kiểm tra riêng bốn điều: nhóm được chọn có đúng giá trị và số lần xuất hiện không; kích thước có đúng hợp đồng K không; thứ tự kết quả có được tạo một cách rõ ràng không; và các trường hợp đồng hạng đã có chính sách phù hợp chưa.

Quy tắc cần nhớ cho heap giới hạn K là đưa **ứng viên yếu nhất đang giữ** lên đầu. Với K số lớn nhất, đó là heap nhỏ; với K số nhỏ nhất, đó là heap lớn. Thứ tự xuất kết quả được xử lý sau khi đã chọn đúng nhóm.



</details>

- [Quay lại đầu trang](#back-to-top)
