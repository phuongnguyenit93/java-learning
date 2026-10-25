<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Hợp đồng của đối tượng là gì và vì sao mã gọi cần dựa vào hợp đồng](#overview-001)
- [Object là lớp gốc và các phương thức dùng chung](#overview-002)
- [Phân biệt danh tính, giá trị và thứ tự của đối tượng](#overview-003)
- [Mối liên hệ giữa equals(), hashCode(), compareTo() và Comparator](#overview-004)
- [Vai trò của toString(), getClass() và clone()](#overview-005)
- [Phân biệt yêu cầu bắt buộc, quy ước và chi tiết triển khai](#overview-006)
- [Hậu quả khi một lớp phá vỡ hợp đồng trong thư viện chuẩn](#overview-007)
- [Lộ trình học và ví dụ xuyên suốt với đối tượng giá trị](#overview-008)

## <a id="overview-001">Hợp đồng của đối tượng là gì và vì sao mã gọi cần dựa vào hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Hợp đồng của đối tượng là gì và vì sao mã gọi cần dựa vào hợp đồng?

## 1. Hiểu đúng về hợp đồng của đối tượng

Trong lập trình hướng đối tượng, **hợp đồng (contract)** là tập các cam kết về **hành vi có thể quan sát từ bên ngoài** của một đối tượng: người sử dụng được phép gọi gì, cần đáp ứng điều kiện nào, và có thể tin tưởng điều gì sau khi gọi. Hợp đồng có thể được mô tả bằng giao diện công khai, tài liệu API, đặc tả nghiệp vụ, kiểm thử và những quy tắc chung của Java.

Ví dụ, một đối tượng hàng đợi cam kết rằng phần tử được thêm trước sẽ được lấy ra trước (FIFO). Mã gọi có thể dựa vào thứ tự đó mà không cần biết bên trong đối tượng lưu dữ liệu bằng mảng, danh sách liên kết hay cấu trúc nào khác.

**Chữ ký phương thức chưa phải toàn bộ hợp đồng.** Hai lớp cùng có `String dequeue()` đều biên dịch được; nhưng nếu một lớp lấy phần tử cũ nhất và lớp kia lấy phần tử mới nhất thì chúng không thực hiện cùng một hợp đồng. Trình biên dịch giúp kiểm tra kiểu dữ liệu và khả năng truy cập, còn nhiều quy tắc hành vi phải được định nghĩa và kiểm chứng riêng.

Cũng cần phân biệt: “hợp đồng của đối tượng” theo nghĩa rộng là các cam kết hành vi của bất kỳ đối tượng nào; nó không chỉ nói về lớp `java.lang.Object` hay riêng `equals()`/`hashCode()`.

## 2. Một hợp đồng thường chứa những gì?

| Thành phần | Nội dung cần xác định | Ví dụ với hàng đợi |
| --- | --- | --- |
| Thao tác công khai | Đối tượng cho phép thực hiện việc gì? | `enqueue`, `dequeue`, `size` |
| Tiền điều kiện (precondition) | Mã gọi phải bảo đảm điều gì trước khi gọi? | Công việc thêm vào không được `null` hoặc chuỗi trắng |
| Hậu điều kiện (postcondition) | Nếu thao tác thành công, kết quả hoặc trạng thái phải thế nào? | `dequeue` trả về công việc cũ nhất và giảm kích thước đi 1 |
| Bất biến (invariant) | Điều gì luôn đúng ở trạng thái hợp lệ của đối tượng? | `size() >= 0`; các công việc đang chờ tuân theo FIFO |
| Xử lý trường hợp lỗi | Khi đầu vào không hợp lệ hoặc thiếu dữ liệu sẽ xảy ra gì? | Lấy từ hàng đợi rỗng ném `NoSuchElementException` |
| Đặc tính được bảo đảm khác | Khả năng thay đổi, thứ tự, tính nhất quán, luồng, v.v. | Chỉ được xem là thread-safe nếu hợp đồng có cam kết rõ |

Không phải API nào cũng cần ghi từng mục dưới dạng trang trọng. Tuy vậy, **những gì ảnh hưởng đến cách dùng đúng phải đủ rõ để người gọi không phải đoán**.

Ba ranh giới trách nhiệm quan trọng:

- **Người gọi** tuân thủ tiền điều kiện đã công bố: chẳng hạn không truyền công việc trống.
- **Đối tượng thực thi** bảo đảm hậu điều kiện, bất biến và cách báo lỗi đã hứa khi được sử dụng hợp lệ.
- **Tài liệu hợp đồng** phải nêu rõ các trường hợp đặc biệt: giá trị `null`, bộ sưu tập rỗng, thao tác thất bại, giới hạn dữ liệu hoặc khả năng sử dụng đồng thời.

Nếu hợp đồng không hứa điều gì, người gọi không nên suy ra cam kết đó chỉ vì phiên bản hiện tại tình cờ hoạt động như vậy.

## 3. Vì sao mã gọi phải dựa vào hợp đồng?

**Tách nhu cầu sử dụng khỏi cách cài đặt.** Mã gọi chỉ cần biết `dequeue()` trả về công việc cũ nhất. Khi đổi cấu trúc lưu trữ, mã gọi không phải sửa nếu cam kết này vẫn giữ nguyên. Đây là cơ sở của tính đóng gói và thiết kế theo abstraction.

**Cho phép thay thế implementation.** Một đối tượng khác có thể được dùng thay nếu nó thỏa cùng những yêu cầu mà người gọi đang dựa vào. Tính thay thế này là ý tưởng trung tâm của nguyên lý Liskov Substitution: chỉ trùng chữ ký phương thức là chưa đủ; kết quả, điều kiện gọi và hành vi lỗi cũng phải tương thích.

**Giảm sự phụ thuộc dễ vỡ.** Nếu mã gọi đọc trực tiếp trường nội bộ, giả định một lớp luôn dùng `ArrayDeque`, hoặc dựa vào thứ tự không được cam kết, một lần tối ưu nội bộ có thể làm chương trình sai dù API công khai không đổi.

**Giúp xác định lỗi và viết kiểm thử.** Khi đặc tả nói “lấy ra theo FIFO”, ta có thể kiểm thử FIFO trên mọi implementation. Nếu kết quả khác, lỗi nằm ở bên vi phạm hợp đồng; không cần suy đoán dựa trên chi tiết nội bộ.

**Giúp API dễ học và dễ bảo trì.** Người mới sử dụng biết rõ đầu vào hợp lệ, kết quả mong đợi và ngoại lệ cần xử lý. Nhóm phát triển biết những hành vi nào phải giữ khi refactor.

## 4. Ví dụ Java 17+: người gọi chỉ biết hợp đồng hàng đợi

Giả sử hệ thống xử lý công việc theo thứ tự được gửi đến. Ta công bố hợp đồng `TaskQueue`:

1. `enqueue(task)` chỉ chấp nhận chuỗi khác `null` và không trắng; gọi thành công thêm công việc vào cuối hàng.
2. `dequeue()` lấy và xóa công việc cũ nhất; nếu rỗng, ném `NoSuchElementException` và không đổi trạng thái.
3. `size()` cho biết số công việc đang chờ; số này không âm.
4. Ví dụ không cam kết an toàn khi nhiều luồng truy cập đồng thời.

Tạo tệp `ContractDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

public final class ContractDemo {
    interface TaskQueue {
        void enqueue(String task);
        String dequeue();
        int size();
    }

    static final class InMemoryTaskQueue implements TaskQueue {
        private final Deque<String> tasks = new ArrayDeque<>();

        @Override
        public void enqueue(String task) {
            if (task == null || task.isBlank()) {
                throw new IllegalArgumentException("task must not be blank");
            }
            tasks.addLast(task);
        }

        @Override
        public String dequeue() {
            return tasks.removeFirst();
        }

        @Override
        public int size() {
            return tasks.size();
        }
    }

    static void process(TaskQueue queue) {
        queue.enqueue("build");
        queue.enqueue("test");
        System.out.println(queue.dequeue());
        System.out.println(queue.dequeue());
        System.out.println(queue.size());
        try {
            queue.dequeue();
        } catch (NoSuchElementException ex) {
            System.out.println("empty");
        }
    }

    public static void main(String[] args) {
        process(new InMemoryTaskQueue());
    }
}
```

Biên dịch và chạy:

```bash
javac ContractDemo.java
java ContractDemo
```

Kết quả chuẩn:

```text
build
test
0
empty
```

**Giải thích các cam kết được thực hiện:**

- `process` nhận kiểu `TaskQueue`, chỉ gọi các thao tác được công bố. Trong ví dụ, nó nhận một hàng đợi mới, ban đầu rỗng.
- `addLast` và `removeFirst` thực hiện FIFO; mã gọi không phải biết đến `Deque`.
- Kiểm tra đầu vào diễn ra trước khi thêm; `enqueue(null)` hoặc `enqueue("   ")` ném `IllegalArgumentException` và không thêm công việc.
- `removeFirst()` của `ArrayDeque` ném `NoSuchElementException` khi rỗng mà không xóa gì; `process` có thể xử lý trường hợp này theo hợp đồng.
- `size()` đọc đúng số phần tử hiện có. Nếu thay `ArrayDeque` bằng một cấu trúc khác, implementation mới vẫn phải giữ FIFO, điều kiện đầu vào, các ngoại lệ và ý nghĩa của `size`.

Mã phía ngoài sẽ gặp lỗi logic nếu một implementation mới âm thầm chuyển sang LIFO, dù tên phương thức và kiểu trả về không đổi. Đây chính là lý do mã gọi phụ thuộc vào **hành vi đã cam kết**, không phải chỉ vào interface.

## 5. Các hợp đồng quen thuộc ngay trong Java

Java có nhiều ví dụ cho thấy hợp đồng đi xa hơn chữ ký phương thức:

- **`Object.equals(Object)`:** nếu dùng để định nghĩa sự bằng nhau, phương thức phải tuân theo các tính chất phản xạ, đối xứng, bắc cầu, nhất quán (khi thông tin liên quan không đổi), và `x.equals(null)` phải là `false` với `x` khác `null`.
- **`Object.hashCode()`:** hai đối tượng bằng nhau theo `equals` phải có cùng mã băm; hai đối tượng có cùng mã băm không nhất thiết bằng nhau. Vì vậy, `HashMap`/`HashSet` có thể dựa vào cả hai hợp đồng.
- **`Comparable.compareTo`:** bên gọi dùng dấu âm, 0, dương để suy ra thứ tự; implementation phải bảo đảm quy tắc so sánh nhất quán được công bố. Sắp xếp có thể sai hoặc ném ngoại lệ nếu comparator vi phạm hợp đồng.
- **`List.get(index)`:** người gọi dựa vào chỉ số, thứ tự và ngoại lệ cho chỉ số ngoài phạm vi, không phải vào việc danh sách lưu phần tử trong mảng hay nút liên kết.

Các hợp đồng này vẫn có những giới hạn cần đọc trong tài liệu API cụ thể. Ví dụ, `toString()` **không** mặc định bảo đảm chuỗi có định dạng ổn định để máy khác phân tích; nếu cần định dạng trao đổi dữ liệu, hãy dùng một đặc tả định dạng rõ ràng.

## 6. Cách đọc và thiết kế hợp đồng trong thực tế

1. **Nêu hành vi quan sát được.** Với mỗi phương thức, hỏi: đầu vào hợp lệ là gì, kết quả có ý nghĩa gì, và trạng thái nào được phép thay đổi?
2. **Làm rõ tình huống biên.** Đặc tả `null`, giá trị rỗng, số âm, phần tử không tồn tại, tràn số và lỗi; không để người gọi tự đoán.
3. **Ghi hợp đồng ở nơi người dùng API nhìn thấy.** Trong Java, dùng tên kiểu và phương thức rõ ràng, JavaDoc (`@param`, `@return`, `@throws`), interface và tài liệu mô tả bất biến. Annotation kiểm tra null hoặc validation có thể hỗ trợ, nhưng không tự diễn đạt mọi quy tắc hành vi.
4. **Viết kiểm thử theo hợp đồng.** Ví dụ: thêm A rồi B phải lấy A rồi B; lấy từ hàng đợi rỗng phải ném ngoại lệ đã nêu; đầu vào không hợp lệ không làm thay đổi kích thước. Chạy cùng bộ kiểm thử cho mọi implementation.
5. **Kiểm tra khả năng thay thế.** Khi tạo subclass hay implementation mới, đừng tùy tiện đòi hỏi tiền điều kiện mạnh hơn, trả kết quả yếu hơn, hoặc thay quy tắc lỗi mà bên gọi đang phụ thuộc.
6. **Phân biệt bảo đảm với chi tiết cài đặt.** FIFO là hợp đồng trong ví dụ; sử dụng `ArrayDeque`, dung lượng bộ nhớ, thứ tự trường private hay dòng log nội bộ không phải cam kết cho mã gọi.

Một API tốt còn phải nói rõ mức độ an toàn luồng, việc lưu hay sao chép dữ liệu đầu vào, quyền sở hữu tài nguyên và chính sách thay đổi trạng thái **khi những yếu tố đó ảnh hưởng đến người sử dụng**. Không mặc định một object bất biến hoặc thread-safe chỉ vì nó có interface nhỏ.

## 7. Các nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Có cùng interface là thay thế được.” | Còn phải giữ các quy tắc hành vi mà người gọi được quyền trông đợi. |
| “Chạy thử một lần thấy đúng thì đó là hợp đồng.” | Một kết quả quan sát được chưa chứng minh lời bảo đảm lâu dài; hãy dựa vào đặc tả và kiểm thử. |
| “Precondition bị vi phạm nhưng object vẫn phải xử lý thành công.” | Người gọi chịu trách nhiệm với tiền điều kiện; hành vi khi vi phạm cần xem đặc tả cụ thể. |
| “Có `equals()` là đã có toàn bộ object contract.” | `equals`/`hashCode` chỉ là những hợp đồng cụ thể trong số nhiều hợp đồng hành vi. |
| “Chỉ cần trả đúng dữ liệu, ngoại lệ nào cũng được.” | Loại lỗi, thời điểm ném và việc trạng thái có bị thay đổi hay không cũng có thể là phần quan trọng của hợp đồng. |

**Tự kiểm tra:** Nếu thay implementation hàng đợi nhưng `dequeue()` lấy phần tử cuối cùng trước, mã Java có còn biên dịch không? Có thể có. Mã gọi có còn đúng không? Không, vì implementation mới đã vi phạm cam kết FIFO. Nếu thay cấu trúc lưu trữ nhưng mọi cam kết công khai vẫn đúng, mã gọi có cần biết việc thay đổi không? Không.

**Ghi nhớ:** Hợp đồng là ranh giới tin cậy giữa đối tượng và mã sử dụng nó. Đóng gói giúp giấu cách làm; hợp đồng cho biết điều gì vẫn phải đúng bất kể cách làm thay đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-002">Object là lớp gốc và các phương thức dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Java `Object`: lớp gốc và các phương thức dùng chung

## 1. Vì sao mọi lớp Java đều liên quan đến `Object`?

`java.lang.Object` là lớp gốc của hệ phân cấp **class** trong Java. Một lớp không khai báo `extends` sẽ trực tiếp kế thừa `Object`; lớp đã `extends` lớp khác thì kế thừa `Object` gián tiếp qua chuỗi cha. Vì vậy, các đối tượng Java đều có một tập phương thức nền tảng để xác định kiểu khi chạy, so sánh, biểu diễn thành chuỗi và phối hợp giữa các luồng.

```java
class Animal { }                 // ngầm extends Object
class Dog extends Animal { }     // gián tiếp kế thừa Object
```

- `Object x = new Dog();` hợp lệ vì một `Dog` cũng là `Object`. Biến `x` có kiểu khai báo `Object` nhưng đối tượng thực tế vẫn là `Dog`.
- `null` không trỏ tới đối tượng nào; gọi phương thức qua tham chiếu `null` gây `NullPointerException`.
- `int`, `boolean` và các kiểu nguyên thủy khác **không** phải đối tượng. Các lớp bao như `Integer`, `Boolean` mới là đối tượng. Mảng là đối tượng và có thể gán cho biến kiểu `Object`.
- `Object` không định nghĩa `compareTo`, `size` hay các thao tác nghiệp vụ. Những khả năng đó thuộc interface/lớp khác hoặc do ứng dụng định nghĩa.

`java.lang` được import tự động, nên không cần viết `import java.lang.Object;`.

## 2. Danh mục phương thức quan trọng

| Phương thức trong `Object` | Vai trò | Điểm cần nhớ |
| --- | --- | --- |
| `public final Class<?> getClass()` | Lấy kiểu lớp **thực tế lúc chạy** của đối tượng. | `final`: không override được. |
| `public boolean equals(Object obj)` | Kiểm tra hai đối tượng có được xem là bằng nhau hay không. | Mặc định là cùng tham chiếu; nhiều lớp override để so sánh theo giá trị. |
| `public int hashCode()` | Tạo mã băm phục vụ cấu trúc dữ liệu như `HashMap`, `HashSet`. | Phải nhất quán với `equals`; mã băm không phải ID duy nhất. |
| `public String toString()` | Tạo chuỗi mô tả đối tượng. | Nên override để thuận tiện log/debug, không lộ thông tin bí mật. |
| `protected Object clone()` | Cơ chế sao chép đối tượng. | Mặc định sao chép nông; có ràng buộc `Cloneable`; thường nên dùng cách sao chép rõ ràng hơn. |
| `protected void finalize()` | Hook dọn dẹp kiểu cũ trước khi thu gom rác. | Đã deprecated và đang hướng tới loại bỏ; không dựa vào nó để giải phóng tài nguyên. |
| `public final void wait()`, `wait(long)`, `wait(long, int)` | Chờ tín hiệu trên monitor của đối tượng. | Phải sở hữu monitor; `wait` giải phóng monitor đó trong lúc chờ. |
| `public final void notify()`, `notifyAll()` | Đánh thức một hoặc mọi luồng đang `wait` trên cùng monitor. | Không bàn giao khóa ngay; luồng tỉnh phải lấy lại monitor. |

`Object` còn có constructor công khai `Object()`; constructor không phải phương thức được override. Phần lớn ứng dụng gọi các phương thức trong bảng qua lớp con chứ hiếm khi tạo `new Object()` để biểu diễn dữ liệu nghiệp vụ.

## 3. `==`, `equals()` và khái niệm bằng nhau

Với **tham chiếu đối tượng**, `a == b` hỏi hai biến có trỏ đúng **cùng một đối tượng** không. Trong khi đó, `a.equals(b)` hỏi hai đối tượng có **bằng nhau theo quy tắc của lớp** hay không.

Triển khai mặc định của `Object.equals` tương đương kiểm tra `this == obj`. Vì thế, nếu một lớp không override, hai đối tượng khác nhau vẫn không bằng nhau dù các trường có cùng giá trị. Các lớp như `String`, lớp bao số và các `record` có quy tắc so sánh giá trị riêng.

```java
String first = new String("Java");
String second = new String("Java");
System.out.println(first == second);      // false: hai đối tượng khác nhau
System.out.println(first.equals(second)); // true: cùng nội dung
```

Đừng dùng `==` để so sánh **nội dung** của `String` hoặc đối tượng giá trị. Việc một số chuỗi literal có thể được intern không biến `==` thành phép so sánh nội dung.

### Hợp đồng khi override `equals`

Với các tham chiếu khác `null`, quan hệ bằng nhau hợp lệ phải có:

1. **Phản xạ:** `x.equals(x)` là `true`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` cho cùng kết quả.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`.
4. **Nhất quán:** các lần so sánh cho cùng kết quả nếu trạng thái tham gia so sánh không đổi.
5. **So với null:** `x.equals(null)` là `false`.

Override đúng chữ ký `equals(Object)`, dùng `@Override` để trình biên dịch phát hiện sai sót. Viết `equals(MyType)` chỉ **overload**, không thay thế `Object.equals(Object)`.

Khi thiết kế phân cấp lớp có thể kế thừa và bổ sung trạng thái, phải đặc biệt cẩn thận để không phá tính đối xứng/bắc cầu. Với đối tượng giá trị, một lớp `final` bất biến hoặc `record` thường giúp quy tắc đơn giản và an toàn hơn.

## 4. Hợp đồng `hashCode()` đi kèm `equals()`

Quy tắc bắt buộc: **nếu `a.equals(b)` là `true`, thì `a.hashCode() == b.hashCode()` phải là `true`.** Chiều ngược lại không đúng: hai đối tượng không bằng nhau vẫn có thể trùng mã băm (*collision*). Nếu cùng một đối tượng không đổi trạng thái liên quan đến `equals`, mã băm phải ổn định trong các lần gọi trong cùng quá trình chạy theo hợp đồng API.

`HashMap` và `HashSet` dùng `hashCode` để tìm nhóm ứng viên, sau đó dùng `equals` để phân biệt phần tử. Override `equals` nhưng quên `hashCode` có thể làm tập hợp nhận hai phần tử được xem là bằng nhau, hoặc không tìm được khóa tương đương.

- Tính hash từ đúng các thuộc tính mà `equals` sử dụng. Có thể viết `Objects.hash(field1, field2)` cho dễ đọc; lưu ý cách này có thể tạo thêm chi phí so với công thức trực tiếp.
- Đừng coi `hashCode` là mã định danh duy nhất, vị trí bộ nhớ hay giá trị ổn định giữa hai lần chạy JVM.
- Tránh thay đổi các trường tham gia `equals/hashCode` khi đối tượng đang là khóa của `HashMap` hoặc phần tử `HashSet`: sau khi thay đổi, cấu trúc có thể không tìm thấy nó theo mã băm mới.
- Khi cần hash dựa trên **định danh đối tượng** bất kể lớp đã override `hashCode`, có `System.identityHashCode(obj)`; hàm này cũng không bảo đảm duy nhất.

## 5. Ví dụ Java 17+: override đúng ba phương thức phổ biến

Tạo tệp `ObjectDemo.java` với toàn bộ nội dung sau:

```java
import java.util.HashSet;
import java.util.List;

public class ObjectDemo {
    static final class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Point other)) {
                return false;
            }
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }

        @Override
        public String toString() {
            return "Point[x=" + x + ", y=" + y + "]";
        }
    }

    public static void main(String[] args) {
        Point a = new Point(2, 3);
        Point b = new Point(2, 3);

        System.out.println("==: " + (a == b));
        System.out.println("equals: " + a.equals(b));
        System.out.println("same hash: " + (a.hashCode() == b.hashCode()));
        System.out.println("HashSet size: " + new HashSet<>(List.of(a, b)).size());
        System.out.println("toString: " + a);
        System.out.println("runtime class: " + a.getClass().getSimpleName());
    }
}
```

Chạy:

```bash
javac ObjectDemo.java
java ObjectDemo
```

Kết quả:

```text
==: false
equals: true
same hash: true
HashSet size: 1
toString: Point[x=2, y=3]
runtime class: Point
```

**Giải thích:** `a` và `b` được tạo bằng hai lệnh `new`, nên khác tham chiếu. Chúng bằng nhau theo cặp tọa độ `x/y` và có cùng mã băm, vì vậy `HashSet` chỉ giữ một giá trị tương đương. `final` ở lớp và trường khiến ví dụ tránh vấn đề thay đổi trạng thái hay lớp con sửa quy tắc so sánh. Công thức `31 * x + y` có thể trùng hash cho các điểm khác nhau nhưng không vi phạm hợp đồng.

## 6. Hiểu chính xác `getClass()` và `toString()`

### `getClass()`: kiểu tại runtime

```java
Object value = "hello";
System.out.println(value.getClass() == String.class); // true
```

Kiểu khai báo là `Object` nhưng kiểu thực tế là `String`. `getClass()` trả về đối tượng `Class` dùng trong reflection. Đừng nhầm `value.getClass() == SomeClass.class` (đúng **chính xác lớp đó**) với `value instanceof SomeClass` (chấp nhận cả lớp con hoặc lớp hiện thực interface). Với biến `null`, không thể gọi `getClass()`.

### `toString()`: bản mô tả hữu ích cho con người

Mặc định `Object.toString()` tạo chuỗi dạng tên lớp + `@` + biểu diễn thập lục phân của `hashCode()`, chẳng hạn `mypackage.Point@1a2b3c`; phần hex **không nhất thiết** là địa chỉ bộ nhớ hay identity hash nếu lớp đã override `hashCode`.

Override `toString()` để thể hiện các trường cần cho quan sát, ví dụ `Point[x=2, y=3]`. Ghép đối tượng với chuỗi hay gọi `System.out.println(obj)` thường dẫn đến biểu diễn `toString()` (với `null`, cách chuyển đổi chuỗi cho ra `"null"`). Không in mật khẩu, token, dữ liệu nhạy cảm hoặc những cấu trúc có thể đệ quy vô hạn.

## 7. `wait()`, `notify()`, `notifyAll()`: phối hợp qua monitor

Mỗi đối tượng có một monitor/khóa nội tại dùng bởi `synchronized`. Các phương thức `wait`/`notify` của `Object` thao tác với **monitor của chính đối tượng nhận lệnh**. Chúng là cơ chế nền tảng cho việc chờ điều kiện giữa các luồng; đây không phải cách thường dùng cho code ứng dụng mới nếu có thể sử dụng các tiện ích `java.util.concurrent` phù hợp.

- `wait()`: luồng phải đang giữ monitor của đối tượng; khi chờ sẽ **thả monitor đó** để luồng khác có thể thay đổi điều kiện. Khi được đánh thức, luồng phải giành lại monitor trước khi tiếp tục.
- `notify()`: đánh thức một luồng đang chờ trên cùng đối tượng, không có bảo đảm chọn luồng nào.
- `notifyAll()`: đánh thức mọi luồng đang chờ trên đối tượng; chúng vẫn phải lần lượt giành lại khóa và kiểm tra điều kiện.
- Gọi các phương thức này khi chưa sở hữu monitor tương ứng sẽ ném `IllegalMonitorStateException`. Thông thường bọc trong `synchronized (lock)` hoặc phương thức `synchronized` trên chính `lock`.
- Luôn gọi `wait` trong vòng `while` kiểm tra điều kiện vì có thể có *spurious wakeup*, bị đánh thức khi điều kiện chưa đúng hoặc điều kiện đã bị luồng khác thay đổi. Với `wait(long timeout)`, giá trị `0` có nghĩa chờ vô thời hạn; lời gọi có thời hạn cũng cần kiểm tra lại điều kiện.
- `wait` có thể ném `InterruptedException`. Xử lý đúng mục đích: truyền ra ngoài, hoặc khôi phục cờ interrupt nếu bắt lỗi nhưng không thể ném tiếp. `Thread.sleep` **không** tự giải phóng monitor đang giữ.

Ví dụ một hộp thư một ô. Tạo tệp độc lập `MonitorDemo.java`:

```java
public class MonitorDemo {
    static final class Mailbox {
        private String message;

        synchronized void put(String value) throws InterruptedException {
            while (message != null) {
                wait();
            }
            message = value;
            notifyAll();
        }

        synchronized String take() throws InterruptedException {
            while (message == null) {
                wait();
            }
            String value = message;
            message = null;
            notifyAll();
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Mailbox mailbox = new Mailbox();

        Thread consumer = new Thread(() -> {
            try {
                System.out.println("Nhận: " + mailbox.take());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        mailbox.put("hello");
        consumer.join();
    }
}
```

```bash
javac MonitorDemo.java
java MonitorDemo
```

Stdout của lần chạy bình thường:

```text
Nhận: hello
```

Dù producer hay consumer chạy trước, `while` bảo vệ điều kiện `message` và các lời gọi `wait/notifyAll` diễn ra khi giữ monitor của cùng một `Mailbox`. Đây là ví dụ học cơ chế; trong sản phẩm, `BlockingQueue`, `Lock`/`Condition` hoặc các công cụ concurrency cấp cao thường rõ ràng và dễ bảo trì hơn.

## 8. `clone()` và `finalize()`: nên biết giới hạn

### `clone()` thường là sao chép nông

`Object.clone()` có quyền truy cập `protected`; lớp muốn công khai cơ chế clone thường override, gọi `super.clone()` và thực hiện `Cloneable`. Nếu đối tượng không hỗ trợ `Cloneable`, cơ chế clone mặc định báo `CloneNotSupportedException` (mảng có quy tắc clone riêng). Sao chép nông tạo đối tượng mới nhưng các trường tham chiếu vẫn có thể trỏ tới **cùng đối tượng con**.

Ví dụ một đối tượng chứa `List<String>`: nếu clone nông, hai bản có thể cùng dùng một `List`; thay đổi danh sách của bản sao ảnh hưởng bản gốc. Muốn độc lập phải chủ động sao chép các thành phần cần thiết. `Cloneable` cũng không cung cấp sẵn một phương thức `clone()` public trong chính interface.

Trong thiết kế ứng dụng, constructor sao chép, factory hoặc kiểu giá trị bất biến thường diễn đạt ý định tốt hơn `clone()`. Không được mặc định rằng `clone` là deep copy.

### `finalize()` không dành cho quản lý tài nguyên

`finalize()` là cơ chế cũ đã bị deprecate, được định hướng loại bỏ. Không có bảo đảm nó chạy kịp thời hoặc chạy trước khi chương trình kết thúc; lạm dụng còn gây rủi ro hiệu năng và độ tin cậy. **Không sử dụng `finalize()` để đóng file, socket, kết nối hay giải phóng tài nguyên quan trọng.** Thay vào đó, dùng `AutoCloseable` và `try-with-resources`:

```java
try (var reader = java.nio.file.Files.newBufferedReader(path)) {
    // đọc dữ liệu từ reader; reader sẽ được đóng khi rời khối
}
```

Đoạn trên minh họa cú pháp khi `path` đã là một `java.nio.file.Path`; nó không phải chương trình độc lập. Việc thu gom bộ nhớ do GC đảm trách; đóng tài nguyên hệ điều hành là trách nhiệm cần thể hiện rõ trong logic ứng dụng.

## 9. Phân biệt `Object`, `Objects` và `record`

`java.util.Objects` là **lớp tiện ích** (khác `java.lang.Object`) cung cấp các phương thức tĩnh như `Objects.equals(a, b)`, `Objects.hash(...)`, `Objects.requireNonNull(value)` và `Objects.toString(value)`. `Objects.equals(a, b)` an toàn với `null`; còn `a.equals(b)` ném lỗi nếu `a == null`.

Với mô hình dữ liệu đơn giản, `record` (Java 16+) tự sinh `equals`, `hashCode` và `toString` dựa trên các thành phần record:

```java
record Position(int x, int y) {}
```

`new Position(2, 3).equals(new Position(2, 3))` là `true`. Record không cho phép gán lại các component, nhưng nếu component chứa một đối tượng mutable (ví dụ danh sách), chính đối tượng đó vẫn có thể thay đổi; vì vậy hãy cân nhắc bất biến sâu khi dùng record làm khóa băm. Record là lựa chọn hợp lý cho nhiều kiểu dữ liệu giá trị, nhưng không thay thế mọi lớp có hành vi nghiệp vụ.

## 10. Những lỗi phổ biến và checklist áp dụng

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| So sánh nội dung đối tượng bằng `==`. | Nhầm định danh với giá trị. | Dùng `equals` theo hợp đồng lớp; với giá trị có thể `null`, dùng `Objects.equals`. |
| Override `equals` mà không override `hashCode`. | `HashMap`/`HashSet` hành xử không đúng kỳ vọng. | Cập nhật hai phương thức cùng nhau và kiểm thử bằng các đối tượng tương đương. |
| Chỉ viết `equals(MyClass)` mà thiếu `equals(Object)`. | Không override; collection vẫn gọi bản từ `Object`. | Viết đúng chữ ký, luôn thêm `@Override`. |
| Thay đổi trường dùng tính hash của một khóa đã lưu. | Tra cứu hoặc xóa khóa có thể thất bại. | Dùng giá trị bất biến cho khóa hoặc tránh đổi trạng thái liên quan. |
| Coi `hashCode` là ID duy nhất hoặc địa chỉ bộ nhớ. | Nhận diện sai đối tượng khi có collision. | Sử dụng khóa/ID nghiệp vụ rõ ràng. |
| Gọi `wait` ngoài monitor hoặc dùng `if` thay `while`. | `IllegalMonitorStateException` hoặc lỗi tranh chấp. | Đồng bộ đúng đối tượng và kiểm tra lại điều kiện trong vòng lặp. |
| Nghĩ `notify` nhường khóa ngay. | Suy luận sai thứ tự thực thi giữa các luồng. | Hiểu luồng tỉnh phải chờ lấy lại monitor. |
| Cho rằng `clone` sao chép sâu hoặc `finalize` luôn chạy. | Chia sẻ trạng thái ngoài ý muốn hoặc rò rỉ tài nguyên. | Sao chép tường minh; dùng `try-with-resources`. |

**Tự kiểm tra kiến thức:**

1. Vì sao hai đối tượng mới tạo có thể `equals == true` nhưng `== false`?
2. Tại sao đối tượng bằng nhau bắt buộc có cùng hash, nhưng cùng hash chưa chắc bằng nhau?
3. Khi một luồng gọi `lock.wait()`, nó giải phóng khóa nào? Vì sao phải dùng `while`?
4. `getClass()` khác kiểm tra `instanceof` thế nào khi có kế thừa?
5. Vì sao copy constructor hoặc record có thể phù hợp hơn `clone()`/lớp mutable trong mô hình giá trị?

Ghi nhớ: `Object` đặt nền tảng chung cho các lớp Java; khi override, điều quan trọng nhất là **tuân thủ hợp đồng `equals`/`hashCode`, biểu diễn `toString` phù hợp và hiểu đúng monitor khi dùng `wait`/`notify`**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-003">Phân biệt danh tính, giá trị và thứ tự của đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt danh tính, giá trị và thứ tự của đối tượng trong Java

Khi làm việc với một object, ba câu hỏi tưởng giống nhau thực ra có ý nghĩa khác nhau:

1. **Danh tính (identity):** Hai biến có trỏ đến **cùng một object** trong bộ nhớ hay không?
2. **Giá trị (value equality):** Hai object có được xem là **tương đương theo quy tắc của kiểu dữ liệu hoặc nghiệp vụ** hay không?
3. **Thứ tự (ordering):** Object nào đứng trước, đứng sau hoặc ngang hàng **theo tiêu chí sắp xếp**?

Trong Java, ba khái niệm này lần lượt liên quan đến `==`, `equals()`/`hashCode()` và `Comparable`/`Comparator`. Chọn nhầm có thể làm `HashSet`, `HashMap`, `TreeSet`, `TreeMap` hoạt động khác mong đợi.

## 1. Bảng phân biệt

| Khía cạnh | Câu hỏi | Công cụ | Kết quả |
|---|---|---|---|
| Identity | Có phải cùng một object? | `a == b` với hai tham chiếu | `boolean` |
| Equality | Có cùng giá trị theo định nghĩa của kiểu? | `a.equals(b)`, `Objects.equals(a,b)` | `boolean` |
| Băm giá trị | Hai giá trị bằng nhau có cùng mã băm? | `hashCode()` đi cùng `equals()` | `int`, không phải phép thử bằng nhau |
| Thứ tự tự nhiên | Đối tượng nào đứng trước theo mặc định? | `Comparable<T>.compareTo(T)` | Số âm, `0`, số dương |
| Thứ tự tùy chọn | Đối tượng nào đứng trước theo tiêu chí được chọn? | `Comparator<T>.compare(a,b)` | Số âm, `0`, số dương |

**`a == b`, `a.equals(b)` và `compare(a,b) == 0` không mặc nhiên tương đương.** Hai sách có thể là hai instance khác nhau nhưng cùng thông tin; hai sách khác nhau có thể được xếp ngang nhau khi comparator chỉ xét năm xuất bản.

## 2. Danh tính: cùng object hay khác object?

Với **kiểu tham chiếu**, `==` trả `true` khi hai biến trỏ đến cùng instance (hoặc cả hai đều `null`). Phép này không đọc thuộc tính bên trong object.

~~~java
String first = new String("java");
String second = new String("java");
String alias = first;

System.out.println(first == second);      // false
System.out.println(first == alias);       // true
System.out.println(first.equals(second)); // true
~~~

Đây là **đoạn mã minh họa** có thể đặt trong `main`, không phải một file Java độc lập. Hai lần `new String` tạo hai instance; `alias` trỏ đến cùng instance với `first`. `String.equals` so sánh chuỗi ký tự nên hai instance vẫn bằng nhau về nội dung.

Với **kiểu nguyên thủy** (`int`, `boolean`, `char`...), `==` so sánh giá trị nguyên thủy. Đừng dùng kết quả tình cờ của string interning hay bộ nhớ đệm wrapper (`Integer`) để suy ra rằng `==` so sánh nội dung object: các cơ chế này có thể tái sử dụng instance trong một số trường hợp.

Identity hữu ích khi thuật toán thật sự cần biết **cùng đối tượng trong bộ nhớ**, chẳng hạn kiểm tra alias hoặc duyệt đồ thị đối tượng theo identity. `IdentityHashMap` là cấu trúc chuyên biệt dùng identity cho khóa; `HashMap` thông thường dùng equality theo giá trị. `System.identityHashCode(x)` có ích khi chẩn đoán nhưng **không phải ID duy nhất** và không chứng minh hai object giống nhau, vì vẫn có thể xảy ra va chạm hash.

## 3. Giá trị: `equals()` và `hashCode()`

`Object.equals()` mặc định có nghĩa như identity. Các kiểu như `String`, `BigDecimal` và nhiều value object định nghĩa lại equality theo nội dung. Cần quyết định rõ **thuộc tính nào tham gia định nghĩa bằng nhau**:

- **Value object** như tọa độ hoặc một lượng tiền: thường bằng nhau khi mọi thành phần có ý nghĩa đều bằng nhau; tính bất biến giúp sử dụng làm khóa.
- **Entity** như đơn hàng: thường cần phân biệt theo mã định danh ổn định; trường mô tả có thể thay đổi mà entity vẫn là một. Phải cẩn thận với ID chưa gán hoặc có thể đổi.
- **`record`**: Java tự sinh `equals()`/`hashCode()` từ các component. Record **không tự sinh `Comparable`**. Nếu component là array, so sánh component đó vẫn theo equality mặc định của array (identity), không tự so sánh sâu từng phần tử.

Hợp đồng của `equals()` yêu cầu:

1. **Phản xạ:** `x.equals(x)` luôn đúng.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` cho cùng kết quả.
3. **Bắc cầu:** `x.equals(y)` và `y.equals(z)` đúng thì `x.equals(z)` phải đúng.
4. **Nhất quán:** khi dữ liệu dùng để xét equality không thay đổi, các lần gọi có cùng kết quả.
5. **Khác `null`:** với `x` khác `null`, `x.equals(null)` trả `false`.

Nếu `a` có thể `null`, gọi `a.equals(b)` sẽ có nguy cơ `NullPointerException`; dùng `Objects.equals(a,b)` cho so sánh an toàn với `null`. Khi tự triển khai equality, `Objects.equals` có thể hỗ trợ các trường, còn `Arrays.equals`/`Arrays.deepEquals` hữu ích nếu cần so sánh nội dung mảng.

### Vì sao `hashCode()` đi cùng `equals()`?

Quy tắc bắt buộc là: **nếu `x.equals(y)` thì `x.hashCode() == y.hashCode()`.** Chiều ngược lại không bắt buộc: hai object khác nhau vẫn có thể trùng hash. Khi ghi đè `equals()` phải ghi đè `hashCode()` tương ứng. Các collection dựa trên hash sử dụng hash để chọn vùng tra cứu và equality để phân biệt khóa cụ thể.

Nếu object đã nằm trong `HashSet` hoặc làm khóa `HashMap`, việc đổi các trường tham gia equality/hash có thể khiến tra cứu hoặc xóa không tìm được phần tử nữa. Ưu tiên dùng khóa bất biến. Với kế thừa, thiết kế equality cần tránh phá tính đối xứng hoặc bắc cầu.

## 4. Thứ tự: `Comparable` và `Comparator`

Kết quả của phép so sánh thứ tự chỉ cần xét **dấu**:

- **Âm:** vế trái đứng trước vế phải.
- **`0`:** hai vế ngang hàng **theo tiêu chí sắp xếp**.
- **Dương:** vế trái đứng sau vế phải.

Không được giả định kết quả chỉ là `-1`, `0` hoặc `1`.

**`Comparable<T>`** được cài trong kiểu qua `compareTo` khi kiểu có một thứ tự tự nhiên hợp lý. **`Comparator<T>`** là quy tắc bên ngoài, có thể chọn khác nhau tại từng điểm dùng: theo tên, theo tuổi, theo giá... Ví dụ một comparator tăng theo độ dài chuỗi, rồi tăng theo chữ cái nếu bằng độ dài:

~~~java
Comparator<String> byLengthThenText =
        Comparator.comparingInt(String::length)
                  .thenComparing(Comparator.naturalOrder());

System.out.println(byLengthThenText.compare("an", "be") < 0);  // true
System.out.println(byLengthThenText.compare("a", "bbb") < 0); // true
~~~

Đây là đoạn minh họa đặt trong `main` với `import java.util.Comparator;`. Phương thức `thenComparing` giúp **phá hòa** khi tiêu chí đầu bằng nhau. Có thể dùng `reversed()`, `nullsFirst`, `nullsLast`, `comparingInt`, `comparingLong` khi phù hợp. Nếu giá trị có thể `null`, phải chọn chính sách rõ ràng thay vì để comparator tự ném exception ngoài ý muốn.

Hợp đồng thứ tự yêu cầu dấu của `compare(a,b)` đảo so với `compare(b,a)`, quan hệ trước/sau có tính **bắc cầu**, và khi `compare(a,b) == 0`, so sánh `a` và `b` với cùng một `z` phải có cùng dấu. Tránh `return a.score - b.score;` vì phép trừ có thể tràn `int`; dùng `Integer.compare(a.score, b.score)` hoặc `Comparator.comparingInt(...)`.

## 5. Một chương trình Java 17 chứng minh cả ba khái niệm

Trong ví dụ này, `Book` là record có equality theo cả `isbn` và `title`. Thứ tự tự nhiên cũng xét đúng hai thành phần đó, nên **với Book**, `compareTo(...) == 0` nhất quán với `equals()`. `Ticket` dùng comparator ngoài để minh họa một bộ so sánh có thể gom hai object khác nhau vào cùng nhóm thứ tự. Cuối cùng, `BigDecimal` cho thấy ngoại lệ của một thứ tự tự nhiên không nhất quán với `equals()`.

Lưu chương trình đầy đủ dưới tên **`ObjectContractDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class ObjectContractDemo {
    record Book(String isbn, String title) implements Comparable<Book> {
        @Override
        public int compareTo(Book other) {
            return Comparator.comparing(Book::isbn)
                    .thenComparing(Book::title)
                    .compare(this, other);
        }
    }

    record Ticket(String id, int priority) {}

    public static void main(String[] args) {
        Book first = new Book("978-1", "Java");
        Book equalValue = new Book("978-1", "Java");
        Book other = new Book("978-2", "Concurrency");

        System.out.println("Book ==: " + (first == equalValue));
        System.out.println("Book equals: " + first.equals(equalValue));
        System.out.println("Book hash equals: "
                + (first.hashCode() == equalValue.hashCode()));
        System.out.println("Book compare = 0: "
                + (first.compareTo(equalValue) == 0));
        System.out.println("Book first < other: "
                + (first.compareTo(other) < 0));
        System.out.println("HashSet<Book>: "
                + new HashSet<>(List.of(first, equalValue, other)).size());
        System.out.println("TreeSet<Book>: "
                + new TreeSet<>(List.of(first, equalValue, other)).size());

        Ticket a = new Ticket("A", 1);
        Ticket b = new Ticket("B", 1);
        Comparator<Ticket> byPriority =
                Comparator.comparingInt(Ticket::priority);
        TreeSet<Ticket> byPrioritySet = new TreeSet<>(byPriority);
        byPrioritySet.add(a);
        byPrioritySet.add(b);
        System.out.println("Ticket equals: " + a.equals(b));
        System.out.println("Ticket compare = 0: "
                + (byPriority.compare(a, b) == 0));
        System.out.println("TreeSet priority only: " + byPrioritySet.size());

        Comparator<Ticket> byPriorityThenId =
                byPriority.thenComparing(Ticket::id);
        TreeSet<Ticket> distinctTickets = new TreeSet<>(byPriorityThenId);
        distinctTickets.add(a);
        distinctTickets.add(b);
        System.out.println("TreeSet priority + id: "
                + distinctTickets.size());

        BigDecimal x = new BigDecimal("2.0");
        BigDecimal y = new BigDecimal("2.00");
        System.out.println("BigDecimal equals: " + x.equals(y));
        System.out.println("BigDecimal compare = 0: "
                + (x.compareTo(y) == 0));
        System.out.println("HashSet<BigDecimal>: "
                + new HashSet<>(List.of(x, y)).size());
        System.out.println("TreeSet<BigDecimal>: "
                + new TreeSet<>(List.of(x, y)).size());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac ObjectContractDemo.java
java ObjectContractDemo
~~~

Đầu ra:

~~~text
Book ==: false
Book equals: true
Book hash equals: true
Book compare = 0: true
Book first < other: true
HashSet<Book>: 2
TreeSet<Book>: 2
Ticket equals: false
Ticket compare = 0: true
TreeSet priority only: 1
TreeSet priority + id: 2
BigDecimal equals: false
BigDecimal compare = 0: true
HashSet<BigDecimal>: 2
TreeSet<BigDecimal>: 1
~~~

**Phân tích kết quả:**

1. `first` và `equalValue` là hai instance (`== false`) nhưng bằng nhau về giá trị. Record tạo `equals` và `hashCode` tương ứng; `compareTo` của `Book` cũng trả `0`.
2. `HashSet<Book>` dựa vào equality/hash; `TreeSet<Book>` dựa vào natural ordering. Cả hai giữ đúng hai sách phân biệt.
3. Hai `Ticket` có ID khác nhau nên `equals` là `false`, nhưng comparator chỉ nhìn `priority` nên trả `0`. `TreeSet` xem chúng như **một khóa** và chỉ giữ một phần tử. Khi phá hòa thêm `id`, cả hai được giữ lại.
4. `BigDecimal("2.0")` khác `BigDecimal("2.00")` theo `equals`, vì `equals` xét cả **scale** (1 so với 2). Tuy nhiên `compareTo` so giá trị số nên trả `0`. Vì vậy hash set giữ 2, còn tree set giữ 1. **Thứ tự tự nhiên của `BigDecimal` nổi tiếng là không nhất quán với `equals`.**

## 6. `compare == 0` không đồng nghĩa cùng giá trị

Comparator xác định các **nhóm tương đương theo sắp xếp**. Một bộ so sánh chỉ theo điểm thi có thể xem hai sinh viên khác mã là ngang hàng. Điều này không sai khi dùng để sắp xếp một danh sách nhưng ảnh hưởng lớn nếu dùng comparator làm định nghĩa khóa.

| Cấu trúc/thao tác | Quy tắc equality hoặc thứ tự |
|---|---|
| `HashSet`, `HashMap` | `equals()` kết hợp `hashCode()` |
| `TreeSet`, `TreeMap` | `compareTo()` hoặc `Comparator.compare()`; `0` là cùng khóa |
| `List.sort()`, `Collections.sort()`, `Stream.sorted()` | Sắp theo comparator, **không tự loại các phần tử ngang hàng** |
| `List.contains()` | Kiểm tra bằng `equals()` |

Với những object dùng làm khóa trong sorted set/map, **nên** chọn thứ tự nhất quán với equality nếu khả thi: `compare(a,b) == 0` khi và chỉ khi `a.equals(b)`. Nếu cố ý dùng comparator để gom các nhóm theo điểm hoặc mức ưu tiên, hãy mô tả rõ ý nghĩa đó và chấp nhận rằng set/map chỉ giữ một khóa trong mỗi nhóm.

Tính nhất quán phải xét cả những object có **cùng equality nhưng khác dữ liệu dùng để sắp xếp**. Ví dụ nếu equality của entity chỉ xét ID, comparator theo `name` rồi ID chưa chắc nhất quán: hai bản thể có cùng ID nhưng tên khác nhau có thể cho kết quả khác `0`. Khi thiết kế cơ chế khóa, phải quyết định liệu entity có bất biến và theo những trường nào.

Không thay đổi trường tham gia comparator khi object đang nằm trong `TreeSet`/`TreeMap`: vị trí sắp xếp của node đã được xây dựng từ giá trị cũ, khiến tra cứu, xóa hoặc phát hiện trùng khó tin cậy.

## 7. Cách chọn trong tình huống thực tế

| Yêu cầu | Phép/thiết kế phù hợp | Điều cần lưu ý |
|---|---|---|
| Kiểm tra cùng instance | `a == b` | Chỉ là identity với tham chiếu |
| So nội dung chuỗi hoặc value object | `Objects.equals(a,b)` | An toàn khi có `null` |
| Tạo khóa cho hash map/set | `equals` + `hashCode` cùng logic | Trường khóa nên bất biến |
| Sắp theo thứ tự mặc định | `Comparable` | Không phải mọi kiểu đều có natural ordering |
| Sắp theo nhiều tiêu chí chọn lúc chạy | `Comparator`, `thenComparing` | Phá hòa nếu cần trật tự xác định |
| Giữ nhiều phần tử có cùng điểm trong `List` | `List.sort(comparator)` | Không cần phá hòa để giữ phần tử |
| Giữ nhiều phần tử trong `TreeSet` | Comparator phân biệt theo khóa phù hợp | `compare == 0` bị coi là một phần tử |
| So độ lớn `BigDecimal` bỏ qua scale | `compareTo(...) == 0` | `equals` xét scale |
| So nội dung các mảng | `Arrays.equals` hoặc `Arrays.deepEquals` | `array.equals` mặc định so identity |

Quy trình thiết kế: **(1)** xác định entity hay value object; **(2)** định nghĩa trường tham gia equality; **(3)** giữ hợp đồng hash; **(4)** chọn thứ tự tự nhiên và/hoặc comparator; **(5)** kiểm tra xem quan hệ `compare == 0` có phù hợp với các collection định dùng hay không; **(6)** kiểm thử tính đối xứng, bắc cầu và tính ổn định của quy tắc.

## 8. Lỗi phổ biến cần tránh

1. **Dùng `==` để so hai `String` hoặc wrapper:** nhận kết quả phụ thuộc instance hoặc cơ chế cache thay vì nội dung.
2. **Override `equals` nhưng không override `hashCode`:** phá hợp đồng trong hash set/map.
3. **Coi hash trùng là giá trị bằng nhau:** hash collision là hợp lệ, không phải chứng minh equality.
4. **Chỉ so một trường không duy nhất trong comparator của `TreeSet`:** vô tình bỏ mất các phần tử khác nhau.
5. **So số nguyên bằng phép trừ:** overflow có thể đảo sai trật tự.
6. **Comparator không bắc cầu hoặc phụ thuộc dữ liệu đang đổi:** kết quả sắp xếp và cây có thể bất ổn, vi phạm hợp đồng.
7. **Sửa trường equality/hash/order của khóa đã thêm vào set/map:** có thể không tra cứu hoặc xóa được.
8. **Tưởng record luôn so sâu mảng:** component kiểu mảng vẫn có equality mặc định theo tham chiếu.
9. **Coi `compareTo == 0` luôn bằng `equals == true`:** phản ví dụ điển hình là `BigDecimal`.

## 9. Câu hỏi tự kiểm tra

**1.** Hai biến cùng trỏ đến một instance; giá trị của một trường mutable thay đổi. Kết quả `==` giữa hai biến có thay đổi không?  
**Đáp án:** Không. Chúng vẫn trỏ cùng object; equality với object *khác* có thể đổi tùy cách triển khai.

**2.** `a.equals(b) == false` nhưng hai `hashCode()` bằng nhau có hợp lệ không?  
**Đáp án:** Có, vì hash collision được phép. Chiều bắt buộc là equality đúng thì hash phải bằng nhau.

**3.** Hai đơn hàng có ID khác nhau nhưng cùng ngày tạo, comparator chỉ xét ngày và trả `0`. Thêm cả hai vào `TreeSet` thì sao?  
**Đáp án:** Set chỉ giữ một phần tử theo comparator; muốn phân biệt theo ID thì cần tiêu chí bổ sung phù hợp.

**4.** Muốn sắp danh sách nhân viên theo tuổi nhưng vẫn giữ tất cả người cùng tuổi, có bắt buộc phá hòa theo ID không?  
**Đáp án:** Không. Sắp xếp `List` không loại phần tử. Phá hòa cần khi muốn thứ tự xác định giữa các trường hợp cùng tuổi, hoặc khi dùng comparator làm nghĩa của khóa trong sorted set/map.

**Ghi nhớ:** **Identity** trả lời *cùng object?*; **equality** trả lời *cùng giá trị theo quy tắc nào?*; **ordering** trả lời *đứng trước/sau/ngang hàng theo tiêu chí gì?* Thiết kế và kiểm thử từng quan hệ riêng biệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-004">Mối liên hệ giữa equals(), hashCode(), compareTo() và Comparator</a>

<details>
<summary>Xem chi tiết</summary>

# Mối liên hệ giữa `equals()`, `hashCode()`, `compareTo()` và `Comparator` trong Java

Khi làm việc với đối tượng Java, cần phân biệt rõ **bằng nhau về mặt logic**, **mã băm để tra cứu**, và **thứ tự sắp xếp**. Bốn cơ chế trên liên quan nhau nhưng phục vụ các mục đích khác nhau. Nắm được mối liên hệ này giúp tránh lỗi khi dùng `HashSet`, `HashMap`, `TreeSet`, `TreeMap` và khi sắp xếp danh sách.

## 1. Bốn cơ chế trả lời bốn câu hỏi

| Cơ chế | Chữ ký tiêu biểu | Câu hỏi được trả lời | Nơi thường sử dụng |
| --- | --- | --- | --- |
| `equals()` | `boolean equals(Object other)` | Hai đối tượng có **bằng nhau về mặt logic** không? | `HashSet`, `HashMap`, `List.contains()` |
| `hashCode()` | `int hashCode()` | Mã băm dùng để phân nhóm/tra cứu là gì? | `HashSet`, `HashMap` |
| `compareTo()` | `int compareTo(T other)` trong `Comparable<T>` | **Thứ tự tự nhiên** giữa hai phần tử là gì? | `TreeSet`, `TreeMap`, sắp xếp tự nhiên |
| `Comparator<T>` | `int compare(T a, T b)` | **Thứ tự tùy chỉnh** cho một tác vụ là gì? | `List.sort()`, `TreeSet`, `TreeMap`, `PriorityQueue` |

Mọi đối tượng đều thừa hưởng `equals()` và `hashCode()` từ `Object`. `compareTo()` chỉ hiện diện khi lớp triển khai `Comparable<T>`. `Comparator<T>` là một chiến lược so sánh độc lập, có thể tạo nhiều chiến lược cho cùng một lớp.

Ba mệnh đề dễ nhầm:

- `a.equals(b) == true` **bắt buộc kéo theo** `a.hashCode() == b.hashCode()`.
- `a.hashCode() == b.hashCode()` **không kéo theo** `a.equals(b)` vì có thể xảy ra va chạm hash.
- `a.compareTo(b) == 0` hay `comparator.compare(a,b) == 0` chỉ có nghĩa là **tương đương theo phép so sánh đó**; nó không nhất thiết bằng `equals()`.

## 2. Hợp đồng của `equals()` và `hashCode()`

### 2.1. `equals()` định nghĩa sự bằng nhau

Với các đối tượng không null `x`, `y`, `z`, một triển khai `equals()` đúng phải thỏa:

1. **Phản xạ:** `x.equals(x)` luôn `true`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` phải giống nhau.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`.
4. **Nhất quán:** khi trạng thái liên quan không đổi, kết quả so sánh phải ổn định.
5. **So với null:** `x.equals(null)` luôn `false`.

`Object.equals()` mặc định so sánh tham chiếu (`==`). Nếu nghiệp vụ coi hai đối tượng có cùng mã định danh là cùng thực thể, cần ghi đè `equals()` để biểu đạt điều đó.

### 2.2. `hashCode()` phải nhất quán với `equals()`

Hợp đồng quan trọng nhất: **hai đối tượng bằng nhau theo `equals()` phải có cùng mã băm**. Hash của cùng đối tượng phải ổn định khi những thông tin tham gia quan hệ bằng nhau không đổi, trong một lần thực thi.

Hai đối tượng khác nhau được phép có cùng hash; `hashCode()` không phải ID độc nhất. `HashMap`/`HashSet` dùng hash để định vị nhóm tra cứu, rồi dùng phép bằng nhau để phân biệt các khóa liên quan. Vì vậy chỉ kiểm tra `hashCode()` bằng nhau là chưa đủ.

Nếu `equals()` dựa trên `id`, thì `hashCode()` cũng nên dựa trên `id`. Ví dụ, hai `Student` cùng `id` nhưng khác `name` vẫn bằng nhau; tính hash từ cả `id` và `name` sẽ vi phạm hợp đồng. Quy tắc thực hành là **ghi đè `equals()` thì ghi đè cả `hashCode()`**, dùng cùng tập thuộc tính mang ý nghĩa bằng nhau.

Một lưu ý khác: không nên thay đổi thuộc tính tham gia `equals()`/`hashCode()` sau khi đối tượng đã được đưa vào `HashSet` hoặc dùng làm khóa `HashMap`. Nếu hash thay đổi, thao tác tìm hoặc xóa có thể thất bại. Ưu tiên khóa bất biến.

## 3. `Comparable` và `Comparator` quy định thứ tự

### 3.1. Ba nhóm kết quả

Với `a.compareTo(b)` hoặc `comparator.compare(a,b)`:

- Số **âm**: `a` đứng trước `b`.
- Số **0**: `a`, `b` tương đương theo thứ tự đó.
- Số **dương**: `a` đứng sau `b`.

Không được giả định kết quả chỉ là `-1`, `0`, `1`; cần kiểm tra dấu. Phép so sánh phải có tính nhất quán, phản đối xứng về dấu và bắc cầu. Nếu `compare(a,b)==0`, so sánh hai phần tử ấy với phần tử thứ ba phải có cùng dấu. Những quy tắc này giúp thuật toán sắp xếp và cấu trúc cây hoạt động đúng.

`Comparable<T>` nằm trong lớp để cung cấp **một thứ tự tự nhiên**, chẳng hạn sinh viên theo mã. `Comparator<T>` tách riêng chiến lược sắp xếp: theo điểm giảm dần, theo tên tăng dần, rồi theo ID để phân biệt.

Ví dụ (giả sử có kiểu `Student` với phương thức `score()` và `id()` như phần 5):

```java
Comparator<Student> byScore =
        Comparator.comparingInt(Student::score).reversed();

Comparator<Student> byScoreThenId =
        Comparator.comparingInt(Student::score)
                  .reversed()
                  .thenComparing(Student::id);
```

`thenComparing()` chỉ áp dụng tiêu chí phụ khi tiêu chí đầu cho kết quả `0`. Với thuộc tính cho phép null, cân nhắc `Comparator.nullsFirst(...)` hoặc `Comparator.nullsLast(...)` để chỉ rõ chính sách. Hàm so sánh không nên thay đổi theo thời gian hoặc dựa vào trạng thái đang biến đổi trong khi đối tượng nằm trong tập có thứ tự.

**Lỗi phổ biến: so sánh số nguyên bằng phép trừ.**

```java
// Có thể tràn int khi hai giá trị cách nhau quá lớn:
Comparator<Integer> bad = (a, b) -> a - b;

// An toàn hơn:
Comparator<Integer> good = Integer::compare;
```

Với trường số thực, phải xét chính sách xử lý `NaN`, `-0.0`, `+0.0` phù hợp ngữ nghĩa, thay vì tự viết phép trừ rồi ép sang `int`.

### 3.2. Nhất quán với `equals()` là gì?

Một thứ tự được gọi là **nhất quán với `equals()`** nếu với mọi `a`, `b`:

```java
a.compareTo(b) == 0  // khi và chỉ khi
a.equals(b)
```

Đây là **khuyến nghị rất quan trọng**, đặc biệt khi sử dụng `TreeSet`/`TreeMap`, nhưng không phải mọi kiểu `Comparable` bắt buộc tuân theo. `Comparator` dùng cho một ngữ cảnh cụ thể cũng có thể cố ý phân nhóm khác định nghĩa bằng nhau. Khi đó phải hiểu hệ quả đối với collection có thứ tự.

## 4. Quan hệ với các Java collection

| Cấu trúc/thao tác | Dùng cơ chế nào? | Cách xác định trùng lặp |
| --- | --- | --- |
| `HashSet<E>` | `hashCode()` và `equals()` | Theo `equals()` |
| `HashMap<K,V>` | `hashCode()` và `equals()` của khóa | Khóa tương đương theo `equals()` |
| `TreeSet<E>` | `compareTo()` hoặc `Comparator` | Phép so sánh trả `0` |
| `TreeMap<K,V>` | `compareTo()` hoặc `Comparator` của khóa | Phép so sánh khóa trả `0` |
| `List.sort(comparator)` | `Comparator` | Chỉ sắp xếp, **không xóa** phần tử trùng |
| `Collections.sort(list)` | `Comparable` tự nhiên | Chỉ sắp xếp, **không xóa** phần tử trùng |
| `PriorityQueue` | `Comparable`/`Comparator` | Điều khiển thứ tự ưu tiên, **không tự khử trùng** |

`TreeSet` và `TreeMap` dùng so sánh thứ tự làm quan hệ tương đương cho phần tử/khóa. Do đó:

- Nếu `!a.equals(b)` nhưng `compare(a,b)==0`, `HashSet` có thể giữ cả hai, còn `TreeSet` chỉ giữ một.
- Nếu `a.equals(b)` nhưng `compare(a,b)!=0`, `HashSet` chỉ giữ một, còn `TreeSet` có thể giữ cả hai.

Trường hợp thứ hai khiến tính duy nhất trong `TreeSet` không khớp định nghĩa `Set` dựa trên `equals()`; đây là lý do nên dùng phép so sánh nhất quán với `equals()` khi cần ngữ nghĩa tập hợp thông thường. `TreeMap` có rủi ro tương tự với khóa: lần `put()` thứ hai có thể thay giá trị một khóa so sánh bằng `0`, dù `equals()` của hai đối tượng khóa là `false`.

Không dùng `HashSet` nếu yêu cầu chính là sắp xếp; không dùng `TreeSet` chỉ để xếp danh sách theo điểm nếu cần giữ đủ các bản ghi cùng điểm.

## 5. Ví dụ Java 17+ chạy được: định danh theo ID, sắp theo điểm

Giả sử `Student` có `id`, `name`, `score`; quy tắc nghiệp vụ là **cùng `id` nghĩa là cùng sinh viên** dù tên hay điểm khác nhau. Vì thế `equals()`, `hashCode()` và thứ tự tự nhiên cùng dựa trên `id`. Một `Comparator` khác xếp sinh viên theo điểm giảm dần để phục vụ giao diện.

Tạo file **`ObjectContractDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

record Student(String id, String name, int score)
        implements Comparable<Student> {

    Student {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof Student other
                && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public int compareTo(Student other) {
        return id.compareTo(other.id);
    }
}

public class ObjectContractDemo {
    public static void main(String[] args) {
        Student a = new Student("S01", "Lan", 90);
        Student b = new Student("S01", "Lan cập nhật", 75);
        Student c = new Student("S02", "Minh", 95);

        System.out.println("equals: " + a.equals(b));
        System.out.println("same hash: "
                + (a.hashCode() == b.hashCode()));
        System.out.println("compareTo: " + a.compareTo(b));

        System.out.println("HashSet: "
                + new HashSet<>(List.of(a, b, c)).size());
        System.out.println("TreeSet natural: "
                + new TreeSet<>(List.of(a, b, c)).size());

        Comparator<Student> byScoreThenId =
                Comparator.comparingInt(Student::score)
                          .reversed()
                          .thenComparing(Student::id);

        List<Student> sorted =
                new ArrayList<>(List.of(a, b, c));
        sorted.sort(byScoreThenId);
        System.out.println("Sorted: "
                + sorted.stream()
                        .map(s -> s.id() + ":" + s.score())
                        .toList());

        TreeSet<Student> scoreSet = new TreeSet<>(byScoreThenId);
        scoreSet.addAll(List.of(a, b, c));
        System.out.println("TreeSet by score: "
                + scoreSet.size());
    }
}
```

Biên dịch và chạy:

```bash
javac ObjectContractDemo.java
java ObjectContractDemo
```

Kết quả chính xác:

```text
equals: true
same hash: true
compareTo: 0
HashSet: 2
TreeSet natural: 2
Sorted: [S02:95, S01:90, S01:75]
TreeSet by score: 3
```

**Giải thích:**

1. `a` và `b` cùng mã `S01` nên `equals()` đúng, hash bằng nhau và `compareTo()` trả `0`.
2. `HashSet` chỉ giữ hai ID phân biệt; `TreeSet` với thứ tự tự nhiên theo ID cũng giữ hai.
3. `List.sort(byScoreThenId)` sắp `95, 90, 75` nhưng **giữ cả ba** bản ghi.
4. `TreeSet` với comparator theo điểm rồi ID giữ cả ba: `a` và `b` có điểm khác nhau nên comparator không trả `0` dù `equals()` coi chúng bằng nhau. Đây là ví dụ chủ ý về comparator **không nhất quán với `equals()`**.

Record mặc định tự tạo `equals()` và `hashCode()` từ **toàn bộ các component**. Ở đây ghi đè cả hai để thể hiện quy tắc ID là danh tính nghiệp vụ. `compareTo()` cũng dùng ID để thứ tự tự nhiên nhất quán với định nghĩa ấy. Với một *value object* thuần túy, cách đúng có thể là giữ `equals()`/`hashCode()` mặc định của record để xét tất cả thành phần; không phải kiểu nào cũng nên định danh theo ID.

## 6. Trường hợp đặc biệt có thật: `BigDecimal`

`BigDecimal("2.0")` và `BigDecimal("2.00")` có cùng giá trị số học, nên `compareTo()` trả `0`. Nhưng `equals()` còn xét *scale*, nên trả `false`. Đây là ví dụ nổi tiếng cho thứ tự tự nhiên **không nhất quán với `equals()`**.

Tạo file **`BigDecimalDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class BigDecimalDemo {
    public static void main(String[] args) {
        BigDecimal x = new BigDecimal("2.0");
        BigDecimal y = new BigDecimal("2.00");

        System.out.println(x.equals(y));
        System.out.println(x.compareTo(y));
        System.out.println(new HashSet<>(List.of(x, y)).size());
        System.out.println(new TreeSet<>(List.of(x, y)).size());
    }
}
```

Chạy:

```bash
javac BigDecimalDemo.java
java BigDecimalDemo
```

Kết quả:

```text
false
0
2
1
```

`HashSet` coi chúng là hai giá trị theo `equals()`, trong khi `TreeSet` coi chúng tương đương theo thứ tự tự nhiên và chỉ giữ một. Khi xử lý số tiền hoặc lượng đo, cần quyết định ứng dụng đòi hỏi cùng **giá trị số học** hay cùng **giá trị và scale** trước khi chọn cách chuẩn hóa/so sánh.

## 7. Quy trình thiết kế cho một kiểu đối tượng

**Bước 1 — Định nghĩa “bằng nhau”.** Chọn những thuộc tính tạo danh tính logic. Với một thực thể, đó có thể là ID ổn định; với value object, đó thường là các thuộc tính giá trị. Tránh quan hệ bằng nhau phụ thuộc thuộc tính có thể thay đổi khi dùng làm khóa.

**Bước 2 — Triển khai `equals()` và `hashCode()` như một cặp.** Cùng dùng những thuộc tính đã chọn. Có thể nhờ IDE sinh code, dùng `Objects.equals()` để xử lý null và `Objects.hash()` khi cần gộp nhiều trường; chú ý chi phí tạo mảng của `Objects.hash()` trong đường chạy nhạy hiệu năng.

**Bước 3 — Chọn thứ tự tự nhiên nếu có ý nghĩa.** Triển khai `Comparable<T>` khi có một trật tự mặc định rõ ràng; cố gắng giữ `compareTo()==0` tương ứng `equals()`. Không cần ép mọi kiểu triển khai `Comparable`.

**Bước 4 — Dùng `Comparator` cho từng nhu cầu khác nhau.** Ví dụ theo tên, điểm, ngày tạo; kết hợp `thenComparing()` nếu cần tiêu chí phụ. Với tập/cây có thứ tự, xác định rõ comparator quyết định tính duy nhất như thế nào.

**Bước 5 — Chọn đúng cấu trúc dữ liệu.** `HashMap`/`HashSet` cho tra cứu, khử trùng theo quan hệ bằng nhau; `TreeMap`/`TreeSet` khi cần thứ tự, truy vấn dải và quy tắc so sánh phù hợp; `List.sort()` để sắp xếp mà giữ tất cả bản ghi.

## 8. Lỗi thường gặp và checklist kiểm tra

- **Ghi đè `equals()` nhưng không ghi đè `hashCode()`:** hai đối tượng bằng nhau có thể không tìm thấy nhau trong cấu trúc dựa trên hash.
- **Tính hash bằng trường không tham gia `equals()`:** vi phạm hợp đồng khi hai đối tượng bằng nhau mà hash khác nhau.
- **Sửa dữ liệu khóa sau khi đưa vào `HashSet`/`HashMap`:** nguy cơ không tra cứu/xóa được khóa theo trạng thái mới.
- **Nghĩ `compareTo()==0` luôn đồng nghĩa `equals()`:** sai với `BigDecimal` và nhiều comparator tùy chỉnh.
- **Cho rằng `TreeSet` chỉ sắp xếp:** thực tế phép so sánh còn quyết định hai phần tử có được giữ đồng thời hay không.
- **Comparator chỉ xét điểm/tên mà thiếu tiêu chí phân biệt:** các phần tử cùng điểm/tên có thể bị `TreeSet` gộp; cần xét quy tắc nghiệp vụ và tiêu chí phụ.
- **Dùng `a - b` để so sánh `int`:** có nguy cơ tràn số, hãy dùng `Integer.compare(...)` hoặc `Comparator.comparingInt(...)`.
- **Cho `equals()` giữa lớp cha và lớp con sai ngữ nghĩa:** dễ phá đối xứng/bắc cầu; cân nhắc `final`, `record` hoặc thiết kế mô hình giá trị đơn giản và kiểm thử.
- **Nhầm hash với định danh duy nhất:** mã băm được phép va chạm; tính đúng đắn đến từ hợp đồng của hai phương thức.
- **Chỉ thử trên dữ liệu “đẹp”:** hãy thử cùng ID khác thuộc tính, cùng điểm khác ID, trường null nếu hợp lệ, số cực trị và các collection khác nhau.

**Ghi nhớ:** `equals()` định nghĩa bằng nhau; `hashCode()` phải tương thích với định nghĩa đó; `compareTo()` cho thứ tự tự nhiên; `Comparator` cho thứ tự theo ngữ cảnh. Quan hệ thứ tự và quan hệ bằng nhau nên nhất quán khi khả thi, đặc biệt nếu dùng để xác định phần tử duy nhất trong `TreeSet` hoặc khóa trong `TreeMap`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-005">Vai trò của toString(), getClass() và clone()</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của `toString()`, `getClass()` và `clone()` trong Java

## 1. Bức tranh tổng quan

Mọi đối tượng thuộc kiểu tham chiếu trong Java đều kế thừa các phương thức từ `java.lang.Object` (trực tiếp hoặc gián tiếp). Ba phương thức sau giải quyết ba nhu cầu khác nhau:

| Phương thức | Câu hỏi mà phương thức trả lời | Kết quả chính |
| --- | --- | --- |
| `toString()` | “Biểu diễn đối tượng này thành văn bản như thế nào?” | Một `String` mô tả đối tượng, thường phục vụ đọc hiểu, gỡ lỗi và ghi log. |
| `getClass()` | “Đối tượng này thực sự thuộc lớp nào tại thời điểm chạy?” | Đối tượng `Class<?>` biểu diễn lớp runtime chính xác. |
| `clone()` | “Có thể tạo bản sao của đối tượng này bằng cơ chế `Object.clone()` không?” | Một đối tượng mới, với các trường được sao chép ở mức nông; việc gọi hợp lệ phụ thuộc vào giao ước `Cloneable`. |

Ba phương thức không thay thế cho nhau và cũng không phải ba cách kiểm tra đối tượng “bằng nhau”. Đặc biệt, `clone()` không mặc nhiên tạo một bản sao độc lập hoàn toàn. Khi học *object contract*, cần liên hệ chúng với `equals()` và `hashCode()` nhưng phân biệt rõ trách nhiệm.

Chữ ký đáng chú ý của `Object`:

```java
public String toString();
public final native Class<?> getClass();
protected native Object clone() throws CloneNotSupportedException;
```

Đây là **các khai báo minh họa API trong `java.lang.Object`**, không phải ba phương thức để dán độc lập vào một tệp Java. Từ khóa `native` thể hiện phần triển khai của `getClass()` và `clone()` gắn với JVM; người sử dụng chỉ cần nắm giao ước hành vi.

## 2. `toString()`: biểu diễn đối tượng thành chuỗi

### Hành vi mặc định và lý do ghi đè

`Object.toString()` có thể trả về chuỗi theo dạng:

```text
ten.lop.DayDu@chuoiHex
```

Cách biểu diễn mặc định tương ứng với tên lớp từ `getClass().getName()`, ký tự `@` và dạng thập lục phân của giá trị `hashCode()`. **Không** suy luận phần sau `@` là địa chỉ bộ nhớ, mã định danh duy nhất hay `System.identityHashCode()` trong mọi trường hợp: `hashCode()` có thể đã được ghi đè và các giá trị hash có thể trùng nhau.

Ví dụ một lớp mang dữ liệu nên ghi đè để tạo mô tả có ý nghĩa:

```java
final class Product {
    private final String sku;
    private final int price;

    Product(String sku, int price) {
        this.sku = sku;
        this.price = price;
    }

    @Override
    public String toString() {
        return "Product{sku='" + sku + "', price=" + price + "}";
    }
}
```

*Đây là đoạn định nghĩa lớp minh họa; chương trình hoàn chỉnh có `main` nằm ở mục 5.* Với `new Product("P01", 120)`, khi gọi `toString()` sẽ thu được:

```text
Product{sku='P01', price=120}
```

Các tình huống thường gọi `toString()` gồm `System.out.println(product)`, nối chuỗi `"item=" + product`, và nhiều công cụ debug/log khi chúng cần biểu diễn văn bản của đối tượng. Với biến có thể `null`, `String.valueOf(value)` trả về `"null"` thay vì gọi phương thức trên tham chiếu `null` gây `NullPointerException`. Một hệ thống logging có thể dùng cơ chế định dạng riêng hoặc gọi `toString()` trễ, nên hành vi cụ thể phụ thuộc thư viện.

### Thiết kế `toString()` tốt

- Ưu tiên những trường giúp người đọc nhận ra trạng thái hoặc ý nghĩa của đối tượng; giữ định dạng dễ hiểu và nhất quán.
- Không tiết lộ mật khẩu, token, khóa riêng, dữ liệu cá nhân hoặc thông tin nội bộ nhạy cảm khi log.
- Tránh tác dụng phụ, thay đổi trạng thái, truy vấn mạng/cơ sở dữ liệu hay phép tính nặng chỉ để tạo chuỗi. Chú ý quan hệ tham chiếu vòng: gọi `toString()` đệ quy trên cả hai phía có thể dẫn đến `StackOverflowError`.
- Không xem `toString()` như định dạng truyền dữ liệu chính thức. Nếu cần hợp đồng ổn định cho JSON, CSV, giao thức hoặc persistence, hãy dùng serializer/DTO thích hợp.

**Quan hệ với `equals()/hashCode()`:** Hai đối tượng `equals()` nhau không bị bắt buộc phải có chuỗi `toString()` giống nhau. Ngược lại, hai chuỗi `toString()` giống nhau không chứng minh hai đối tượng bằng nhau. `toString()` phục vụ biểu diễn, không định nghĩa bình đẳng.

## 3. `getClass()`: nhận diện kiểu thực tế lúc chạy

`getClass()` là phương thức `public final` của `Object` nên lớp con không ghi đè được. Nó trả về đối tượng `Class` mô tả **đúng lớp runtime** của đối tượng nhận lời gọi, bất kể kiểu khai báo của biến tham chiếu rộng đến đâu.

```java
Object value = "Hello";

System.out.println(value.getClass().getName());       // java.lang.String
System.out.println(value.getClass().getSimpleName()); // String
System.out.println(value.getClass() == String.class); // true
System.out.println(value instanceof CharSequence);   // true
```

*Đây là các câu lệnh minh họa đặt bên trong một phương thức; các chú thích là kết quả dự kiến.* `value` được khai báo kiểu `Object` nhưng đối tượng đang giữ là `String`. Vì vậy `getClass()` cho biết lớp thực của đối tượng, không trả về `Object.class` chỉ vì kiểu của biến là `Object`.

Các phép kiểm tra không đồng nghĩa:

| Cách kiểm tra | Ý nghĩa |
| --- | --- |
| `obj.getClass() == SomeType.class` | Lớp runtime **chính xác** là `SomeType`, không chấp nhận lớp con. |
| `obj instanceof SomeType` | Đối tượng thuộc `SomeType` hoặc một kiểu con/kiểu triển khai tương ứng. Với `obj == null`, kết quả là `false`. |
| `SomeType.class.isAssignableFrom(obj.getClass())` | `SomeType` có thể nhận đối tượng có lớp runtime đó qua quan hệ kiểu; phải bảo đảm `obj` không `null` trước khi gọi `getClass()`. |

Ứng dụng thường gặp: ghi nhận lớp thực khi chẩn đoán lỗi, chọn chiến lược xử lý theo kiểu, làm việc với reflection (xem tên lớp, phương thức, annotation) và hiểu các đối tượng được tạo bởi framework/proxy. Chẳng hạn trong `equals()`, một số lớp value object dùng `getClass()` để yêu cầu hai đối tượng có đúng cùng lớp runtime; lớp khác dùng `instanceof` để cho phép quan hệ kế thừa. Lựa chọn đó ảnh hưởng tính đối xứng và khả năng tương thích với subclass/proxy, nên phải thiết kế theo giao ước bình đẳng của cả lớp, không thay máy móc mọi `instanceof` bằng `getClass()`.

**Lưu ý:**

- Gọi `null.getClass()` không hợp lệ và gây `NullPointerException`; không tồn tại “lớp runtime của null”.
- `getClass()` không cần ép kiểu hay tạo đối tượng `Class` mới mỗi lần. Hai đối tượng có cùng lớp runtime sẽ nhận cùng đối tượng `Class` tương ứng trong ngữ cảnh nạp lớp của chúng.
- `getClass()` chỉ có trên giá trị tham chiếu đối tượng; với kiểu nguyên thủy, có thể lấy class literal như `int.class` thay vì gọi phương thức trên giá trị `int`.
- `getName()`, `getSimpleName()` và `getCanonicalName()` là các cách lấy tên khác nhau; với lớp lồng, anonymous class và array, kết quả có thể khác trực giác. Không mặc định `getSimpleName()` là khóa định danh ổn định.

## 4. `clone()`: cơ chế sao chép nông và hợp đồng `Cloneable`

### Vì sao `clone()` khác hai phương thức trước?

`Object.clone()` có mức truy cập `protected`, nên mã bên ngoài một lớp thường **không thể** gọi `object.clone()` trực tiếp trên mọi đối tượng. Một lớp muốn cung cấp API sao chép thường:

1. Khai báo `implements Cloneable`.
2. Ghi đè `clone()` thành `public` nếu muốn mã gọi bên ngoài có thể sử dụng.
3. Gọi `super.clone()` bên trong phương thức ghi đè.
4. Xử lý `CloneNotSupportedException` nếu cần và quyết định có sao chép sâu dữ liệu tham chiếu hay không.

`Cloneable` là **marker interface**, không định nghĩa phương thức `clone()`. Chỉ việc viết `implements Cloneable` không tự tạo một phương thức `clone()` công khai. Nếu dùng `Object.clone()` trên một đối tượng không triển khai `Cloneable` (ngoại lệ riêng dành cho array), cơ chế đó ném `CloneNotSupportedException`.

Theo cơ chế chuẩn, `super.clone()` tạo đối tượng mới cùng lớp runtime và chép giá trị từng trường:

- Trường nguyên thủy (`int`, `boolean`, …) được chép giá trị.
- Trường tham chiếu được chép **địa chỉ tham chiếu tới cùng đối tượng con**, không tự dựng bản sao của đối tượng con.
- Constructor của đối tượng đích không được gọi như khi viết `new`. Vì vậy, không dựa vào constructor để “sửa” trạng thái sau khi gọi `super.clone()`.

Hình dung với `Customer` giữ tham chiếu `Address`:

```text
Trước khi sao chép:
original ──> Customer A ──address──> Address X

Sau super.clone() (shallow copy):
original ──> Customer A ──address──┐
                                  ├──> Address X
shallow  ──> Customer B ──address──┘

Sau khi tạo bản sao độc lập cho Address:
original ──> Customer A ──address──> Address X
deep     ──> Customer C ──address──> Address Y
```

Tên `deep` trong sơ đồ chỉ bản sao được tạo chủ động cho *đồ thị đối tượng của ví dụ*. Nó không có nghĩa Java cung cấp một thao tác deep clone tổng quát cho mọi đồ thị đối tượng.

### Vì sao cần cân nhắc sao chép sâu?

Nếu `Customer A` và `Customer B` cùng giữ `Address X` thì thay đổi `Address X.city` thông qua một bản sẽ hiển thị ở bản kia. Nếu các thành phần tham chiếu là bất biến (ví dụ `String`), chia sẻ chúng thường an toàn; với thành phần mutable, cần quyết định rõ có muốn chia sẻ hay không. Một `final` trên trường tham chiếu chỉ ngăn gán lại tham chiếu đó, **không** làm đối tượng được tham chiếu trở thành bất biến.

## 5. Ví dụ Java 17+ chạy được: ba phương thức trong cùng một tình huống

Lưu toàn bộ mã bên dưới vào tệp `ObjectMethodsDemo.java`.

```java
public class ObjectMethodsDemo {

    static final class Address {
        String city;

        Address(String city) {
            this.city = city;
        }
    }

    static final class Customer implements Cloneable {
        private final String name;
        private final Address address;

        Customer(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        @Override
        public String toString() {
            return "Customer{name='" + name + "', city='" + address.city + "'}";
        }

        @Override
        public Customer clone() {
            try {
                return (Customer) super.clone();
            } catch (CloneNotSupportedException ex) {
                // Customer triển khai Cloneable nên nhánh này không được mong đợi.
                throw new AssertionError(ex);
            }
        }

        // Tạo bản sao độc lập cho Address mà không dựa vào Object.clone().
        public Customer copyIndependent() {
            return new Customer(name, new Address(address.city));
        }
    }

    public static void main(String[] args) {
        Customer original = new Customer("An", new Address("Hà Nội"));
        Customer shallow = original.clone();
        Customer independent = original.copyIndependent();

        System.out.println(original); // Gọi toString().
        System.out.println(original.getClass().getSimpleName());
        System.out.println(original == shallow);
        System.out.println(original.address == shallow.address);
        System.out.println(original.address == independent.address);

        shallow.address.city = "Đà Nẵng";

        System.out.println(original.address.city);
        System.out.println(independent.address.city);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac ObjectMethodsDemo.java
java ObjectMethodsDemo
```

Đầu ra chính xác:

```text
Customer{name='An', city='Hà Nội'}
Customer
false
true
false
Đà Nẵng
Hà Nội
```

Giải thích tuần tự:

1. `println(original)` dùng `Customer.toString()` nên hiển thị dữ liệu có ý nghĩa thay cho chuỗi mặc định của `Object`.
2. `getClass().getSimpleName()` cho biết lớp runtime của `original` là `Customer`.
3. `original == shallow` là `false`: `clone()` tạo một đối tượng `Customer` mới, không trả về chính đối tượng cũ.
4. `original.address == shallow.address` là `true`: `super.clone()` chỉ chép tham chiếu `address`.
5. `original.address == independent.address` là `false`: `copyIndependent()` đã tạo một `Address` khác bằng `new`.
6. Đổi `shallow.address.city` cũng đổi giá trị quan sát qua `original.address.city` vì hai tham chiếu trỏ tới cùng `Address`. Bản `independent` vẫn lưu “Hà Nội”.

Ở ví dụ này, `name` là `String` bất biến nên có thể chia sẻ tham chiếu an toàn; `Address` mutable nên cần sao chép riêng nếu yêu cầu độc lập. `Customer.address` được khai báo `final` vẫn chia sẻ `Address` sau shallow clone như bình thường.

### Một điểm dễ nhầm: mảng cũng có `clone()`

Array trong Java hỗ trợ `clone()` và bản sao mảng mới có cùng độ dài/kiểu thành phần. Với phần tử tham chiếu, đó vẫn là sao chép nông:

```java
StringBuilder[] a = {new StringBuilder("A")};
StringBuilder[] b = a.clone();

System.out.println(a == b);       // false: hai mảng khác nhau
System.out.println(a[0] == b[0]); // true: cùng phần tử tham chiếu
```

*Đoạn trên là các câu lệnh minh họa bên trong một phương thức.* Chỉnh sửa `b[0].append("!")` cũng khiến `a[0].toString()` trả về `"A!"`. Trong khi đó, thay trực tiếp `b[0] = new StringBuilder("B")` không thay phần tử `a[0]`, vì hai mảng là hai container riêng.

## 6. Khi nào nên dùng cách nào?

| Nhu cầu | Lựa chọn hợp lý | Cảnh báo |
| --- | --- | --- |
| In trạng thái đối tượng cho người đọc hoặc debug | Ghi đè `toString()` | Không đưa dữ liệu bí mật vào chuỗi; không dùng làm định dạng lưu trữ chính thức. |
| Biết chính xác lớp runtime | `obj.getClass()` | Kiểm tra `null` trước; không nhầm với quan hệ đa hình. |
| Kiểm tra đối tượng có thuộc một hệ kiểu | `instanceof` (hoặc `Class.isInstance()` khi biết kiểu lúc chạy) | `getClass() == ...` chỉ nhận một lớp chính xác. |
| Sao chép trạng thái phẳng hoặc dữ liệu bất biến từ lớp đã hỗ trợ `clone()` đúng cách | `clone()` theo giao ước của lớp đó | Vẫn kiểm tra mức độ sao chép và khả năng gọi public. |
| Tạo đối tượng mới có chủ đích, kiểm soát bất biến và thành phần mutable | Copy constructor, factory/copy method hoặc mô hình immutable | Cần xác định rõ trường nào sao chép và trường nào được phép chia sẻ. |

Với API mới, **copy constructor hoặc factory method** thường dễ diễn đạt hơn `Cloneable/clone()`, nhất là khi đối tượng chứa tài nguyên, tham chiếu vòng, trường `final` mutable, invariant cần bảo đảm hoặc quan hệ kế thừa phức tạp. Ví dụ `new Customer(old.name, new Address(old.address.city))` làm chính sách sao chép hiển lộ; với cấu trúc sâu hơn phải tiếp tục sao chép các thành phần mutable liên quan. Không có một thuật toán sao chép sâu mặc định đúng cho mọi loại đối tượng.

## 7. Lỗi thường gặp và cách kiểm tra

**Lỗi 1: Dùng chuỗi `toString()` để so sánh đối tượng.** Hai đối tượng có thể hiển thị giống nhau nhưng khác nhau theo `equals()`; hoặc đối tượng bằng nhau có phần mô tả khác. Hãy dùng `equals()` cho bình đẳng logic được lớp định nghĩa, `==` cho đồng nhất tham chiếu và bảo đảm `hashCode()` tương thích khi ghi đè `equals()`.

**Lỗi 2: Coi phần `@...` trong `Object.toString()` là địa chỉ bộ nhớ.** Nó biểu diễn giá trị hash dạng hex, không phải địa chỉ tin cậy hoặc định danh duy nhất.

**Lỗi 3: Dùng `getClass() == Parent.class` khi muốn chấp nhận lớp con.** Phép so sánh đó loại đối tượng là subclass. Dùng `instanceof Parent` khi thật sự muốn chấp nhận quan hệ kế thừa/triển khai interface.

**Lỗi 4: Gọi `clone()` trên mọi `Object`.** `Object.clone()` là `protected`; `Cloneable` không tự cung cấp method public. Phải xem API cụ thể của lớp, quyền truy cập và điều kiện hỗ trợ.

**Lỗi 5: Tưởng `super.clone()` sao chép luôn các đối tượng lồng.** Hãy kiểm tra từng trường mutable; dùng copy method/copy constructor hoặc sao chép chọn lọc khi cần tính độc lập.

**Lỗi 6: Quên rằng constructor không chạy trong thao tác `super.clone()`.** Khi lớp có invariant, khóa, handle tệp, socket hoặc tài nguyên bên ngoài, sao chép từng trường có thể cho trạng thái nguy hiểm hoặc vô nghĩa; chọn cách tạo mới phù hợp với semantics của lớp.

## 8. Tự kiểm tra kiến thức

**Câu 1.** Biến `Object x = new String("Hi")` trả về gì từ `x.getClass() == String.class`?  
**Đáp án:** `true`, vì `getClass()` nhìn lớp runtime thực tế.

**Câu 2.** Hai đối tượng khác nhau in `toString()` giống hệt nhau có nhất thiết `equals()` nhau không?  
**Đáp án:** Không; chuỗi mô tả không phải giao ước bình đẳng.

**Câu 3.** Sau khi shallow clone một object có trường `List<String> items`, thay đổi `clone.items.add("x")` có thể ảnh hưởng bản gốc không?  
**Đáp án:** Có, nếu cả hai cùng giữ một `List` mutable do chép tham chiếu. Muốn container riêng có thể tạo `new ArrayList<>(items)`, nhưng nếu phần tử là object mutable thì đó vẫn chưa phải sao chép sâu phần tử.

**Câu 4.** Một class `implements Cloneable` nhưng không khai báo `public clone()` thì code bên ngoài có tự gọi `instance.clone()` được không?  
**Đáp án:** Không mặc nhiên; `Cloneable` không khai báo `clone()` và phương thức kế thừa từ `Object` là `protected`.

**Câu 5.** Lớp con cần được chấp nhận khi kiểm tra kiểu: nên dùng `getClass() == Base.class` hay `instanceof Base`?  
**Đáp án:** Thường dùng `instanceof Base` để nhận cả subtype. Nếu cần đúng một lớp runtime (ví dụ một chính sách equality cụ thể), mới cân nhắc `getClass() == Base.class`.

Điểm cần nhớ để vận dụng đúng: `toString()` là **biểu diễn**, `getClass()` là **thông tin kiểu runtime chính xác**, còn `clone()` là **cơ chế sao chép nông có điều kiện và giới hạn truy cập**. Thiết kế sao chép độc lập và định nghĩa bình đẳng là những quyết định riêng của lớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-006">Phân biệt yêu cầu bắt buộc, quy ước và chi tiết triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt yêu cầu bắt buộc, quy ước và chi tiết triển khai trong Java Object Contract

## 1. Đọc một quy tắc Java đúng cấp độ

Khi nói một đối tượng Java có “contract” (hợp đồng hành vi), ta cần phân biệt ba cấp độ:

1. **Yêu cầu bắt buộc (contract/API specification):** Điều kiện mà phương thức hoặc kiểu dữ liệu đã công bố phải tuân thủ để mã khác có thể dựa vào. Vi phạm dẫn đến hành vi sai về mặt ngữ nghĩa dù chương trình vẫn biên dịch.
2. **Quy ước hoặc khuyến nghị (convention/best practice):** Cách viết giúp API dễ hiểu, dễ bảo trì, ít lỗi hoặc hiệu quả hơn. Có trường hợp vi phạm quy ước không làm sai hợp đồng của Java.
3. **Chi tiết triển khai (implementation detail):** Lựa chọn bên trong một class, thư viện hoặc phiên bản JDK. Nó có thể quan sát được ở một môi trường cụ thể nhưng không phải bảo đảm chung để mã ứng dụng phụ thuộc vào.

Trong chủ đề `object-contract`, điểm xuất phát là `java.lang.Object`: mọi class đều kế thừa các phương thức như `equals(Object)`, `hashCode()` và `toString()`. Ba phương thức này có mức ràng buộc khác nhau. Học thuộc một ví dụ triển khai mà không phân biệt cấp độ rất dễ dẫn đến suy luận sai, đặc biệt khi đối tượng được đưa vào `HashMap`, `HashSet` hoặc thư viện bên thứ ba.

Một câu hỏi tự kiểm tra hữu ích là: **“Nếu tôi thay phần hiện thực nhưng giữ nguyên hành vi đã được tài liệu cam kết, chương trình của bên gọi có còn đúng không?”** Nếu có, phần bị thay thường là chi tiết triển khai.

## 2. Các yêu cầu bắt buộc của `equals(Object)`

Khi một class định nghĩa khái niệm “hai giá trị bằng nhau” qua `equals`, quan hệ đó phải có các tính chất sau:

| Tính chất | Yêu cầu | Ví dụ |
| --- | --- | --- |
| Phản xạ (*reflexive*) | Với đối tượng khác `null`, `x.equals(x)` là `true`. | Một `UserId("u1")` bằng chính nó. |
| Đối xứng (*symmetric*) | Với `x` và `y` khác `null`, `x.equals(y)` và `y.equals(x)` phải cho cùng kết quả. | Nếu ID A bằng ID B, chiều ngược lại cũng bằng. |
| Bắc cầu (*transitive*) | Nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`. | Ba đối tượng mô tả cùng ID phải nhất quán. |
| Nhất quán (*consistent*) | Gọi lặp lại cho cùng kết quả khi thông tin dùng để so sánh chưa thay đổi. | Không đổi trường ID thì kết quả không tự đổi. |
| So với `null` | Với `x` khác `null`, `x.equals(null)` là `false`. | Không có đối tượng hợp lệ nào bằng `null`. |

Đây là **hợp đồng hành vi**, không phải gợi ý về cách viết. `Object.equals` mặc định dùng đồng nhất tham chiếu: hai tham chiếu trỏ cùng đối tượng thì bằng nhau. Class có thể override để dùng *value equality* (bằng nhau theo giá trị), miễn là vẫn giữ đủ các tính chất trên.

Phân biệt hai phép so sánh:

- `a == b` kiểm tra hai biến tham chiếu có trỏ cùng đối tượng hay không.
- `a.equals(b)` gọi cách định nghĩa “bằng nhau” của class; có thể giống `==` hoặc so sánh dữ liệu.

**Lưu ý về kế thừa:** Dùng `instanceof` hay `getClass()` trong `equals` là một **quyết định thiết kế**, không có quy tắc chung bắt mọi class chọn một cách. Nếu class mở cho kế thừa, phải xét cả quan hệ đối xứng và bắc cầu giữa đối tượng cha/con. Một class `final` với các trường giá trị bất biến thường giúp định nghĩa equality dễ an toàn hơn.

## 3. Hợp đồng `hashCode()` gắn chặt với `equals()`

`hashCode` có ba điểm quan trọng trong tài liệu Java:

1. Trong cùng một lần chạy ứng dụng, việc gọi `hashCode()` nhiều lần trên một đối tượng phải trả về cùng một số nguyên **nếu thông tin dùng trong `equals` của đối tượng ấy không bị thay đổi**. Không có cam kết mã băm giữ nguyên qua hai lần chạy chương trình khác nhau.
2. **Nếu `a.equals(b) == true` thì bắt buộc `a.hashCode() == b.hashCode()`.** Vì thế, khi override `equals` để định nghĩa equality theo giá trị, thông thường phải override `hashCode` cho phù hợp.
3. Nếu `a.equals(b) == false` thì **không bắt buộc** hai mã băm phải khác nhau. Va chạm hash (*collision*) là hợp lệ.

Dạng logic cần nhớ:

`a.equals(b) ⇒ a.hashCode() == b.hashCode()`

Chiều ngược lại **không đúng nói chung**: cùng mã băm không có nghĩa là hai đối tượng bằng nhau.

Tại sao cần quy tắc này? Cấu trúc dữ liệu dựa trên hash như `HashSet` và `HashMap` sử dụng mã băm để tổ chức việc tìm phần tử rồi dùng so sánh equality để phân biệt khóa phù hợp. Nếu hai khóa bằng nhau nhưng cho mã băm khác nhau, các thao tác thêm/tìm kiếm/xóa có thể cho kết quả sai so với kỳ vọng về khóa bằng nhau.

Việc dùng một thuật toán hash “đẹp” hoặc một công thức `31 * result + fieldHash` là **quy ước triển khai**, không phải yêu cầu bắt buộc của contract. Ngay cả `hashCode() { return 1; }` cũng thỏa điều kiện “bằng nhau thì cùng hash” cho mọi cặp đối tượng, nhưng thường làm suy giảm hiệu năng đáng kể vì va chạm quá nhiều.

## 4. Ví dụ Java 17 chạy được: giữ đúng contract

Ví dụ sau coi hai `UserId` bằng nhau khi có cùng chuỗi mã, bất kể chúng là hai đối tượng được tạo riêng.

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ObjectContractDemo {
    static final class UserId {
        private final String value;

        UserId(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof UserId that)) {
                return false;
            }
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "UserId[" + value + "]";
        }
    }

    public static void main(String[] args) {
        UserId a = new UserId("U-42");
        UserId b = new UserId("U-42");
        Set<UserId> ids = new HashSet<>();
        ids.add(a);

        System.out.println(a == b);
        System.out.println(a.equals(b));
        System.out.println(a.hashCode() == b.hashCode());
        System.out.println(ids.contains(b));
        System.out.println(a);
    }
}
```

Lưu tệp `ObjectContractDemo.java`, sau đó chạy:

```bash
javac ObjectContractDemo.java
java ObjectContractDemo
```

Kết quả chuẩn:

```text
false
true
true
true
UserId[U-42]
```

Giải thích từng dòng:

1. `false`: `a` và `b` được tạo bằng hai lần `new`, nên là hai đối tượng khác nhau.
2. `true`: phương thức `equals` so sánh `value`, không so sánh địa chỉ tham chiếu.
3. `true`: hai `value` bằng nhau nên cùng mã băm.
4. `true`: `HashSet` nhận ra khóa cùng giá trị nhờ cặp `hashCode`/`equals` nhất quán.
5. `UserId[U-42]`: đây là định dạng do class **tự chọn** cho `toString`, không phải mẫu đầu ra Java bắt buộc cho mọi đối tượng.

Class này là `final`, trường `value` là `final` và không có setter nên giá trị dùng trong equality ổn định sau khi khởi tạo. Điều đó thuận lợi khi dùng `UserId` làm khóa của cấu trúc dữ liệu dựa trên hash.

## 5. Cùng một vấn đề nhưng ba cấp độ khác nhau

| Phát biểu | Phân loại | Lý do |
| --- | --- | --- |
| “`equals` phải phản xạ, đối xứng, bắc cầu, nhất quán và trả về `false` khi so với `null`.” | **Bắt buộc** | Là contract đã công bố của `Object.equals`. |
| “Hai đối tượng bằng nhau theo `equals` phải cùng `hashCode`.” | **Bắt buộc** | Là ràng buộc giữa hai phương thức. |
| “Hai đối tượng khác nhau phải có hai mã băm khác nhau.” | **Sai** | Java cho phép hash collision. |
| “Mỗi class đều phải override `equals`, `hashCode` và `toString`.” | **Sai** | Có thể giữ hành vi mặc định của `Object` nếu phù hợp bài toán. |
| “Một class có value equality nên tránh biến đổi các trường dùng để tính equality khi đang làm hash key.” | **Khuyến nghị rất quan trọng** | Giữ hoạt động tra cứu đáng tin cậy; đột biến khóa gây lỗi sử dụng collection. |
| “Dùng `Objects.equals`, `Objects.hash` để viết mã rõ ràng.” | **Quy ước/lựa chọn** | Là tiện ích, không phải cách viết duy nhất đúng contract. |
| “Lấy số 31 để phối hợp các trường khi tính hash.” | **Lựa chọn triển khai** | Phổ biến nhưng không được contract bắt buộc. |
| “Thứ tự duyệt một `HashMap` luôn là thứ tự thêm vào.” | **Sai** | Không có bảo đảm thứ tự duyệt như `LinkedHashMap`. |
| “Giá trị `hashCode` phải giống nhau giữa các lần khởi động JVM.” | **Sai** | Hợp đồng không cam kết ổn định qua các lần chạy. |
| “`toString` nên dễ đọc, hữu ích khi log/debug và không tiết lộ thông tin nhạy cảm.” | **Quy ước thiết kế** | Định dạng cụ thể do class/API quyết định. |
| “Máy ảo bắt buộc tạo identity hash từ địa chỉ RAM của đối tượng.” | **Sai** | Cách sinh identity hash là chi tiết thực thi, không phải cam kết như vậy của Java. |

Điểm mấu chốt: một câu có vẻ “rất thường gặp trong mã Java” chưa chắc là một ràng buộc chính thức. Muốn đánh giá đúng, phải đọc lời cam kết của API, sau đó phân biệt với hướng dẫn thiết kế và thuật toán cụ thể.

## 6. `toString()`: mục đích mô tả và định dạng cụ thể

Tài liệu `Object.toString()` mô tả mục đích là cung cấp biểu diễn ngắn gọn, có ích cho người đọc, và khuyến khích class override để cung cấp thông tin phù hợp. `Object.toString` mặc định tạo chuỗi dạng tên class, dấu `@` và mã băm ở hệ thập lục phân. Tuy nhiên, **không có hợp đồng phổ quát** buộc mọi class override `toString` phải giữ dạng ấy hoặc phải xuất mọi trường theo một định dạng chung.

Ví dụ, cả hai kiểu dưới đây đều có thể là lựa chọn triển khai hợp lý, tùy API:

`UserId[U-42]`

`UserId(value=U-42)`

Bài học thực tiễn:

- Dùng `toString` để chẩn đoán và đọc log, nhưng không coi biểu diễn mặc định là định dạng trao đổi dữ liệu ổn định.
- Không parse đầu ra `toString` của thư viện bên thứ ba để làm quy tắc nghiệp vụ, trừ khi chính thư viện **công bố rõ** định dạng đó là một phần API.
- Tránh đưa mật khẩu, token, khóa bí mật hoặc dữ liệu cá nhân nhạy cảm vào `toString` vì log có thể lưu trữ lâu dài.
- Nếu hệ thống cần biểu diễn máy đọc được, dùng hợp đồng serialization được định nghĩa riêng, chẳng hạn schema JSON rõ ràng.

Một class có thể ghi thành công dữ liệu nghiệp vụ dù không override `toString`. Bởi vậy “phải luôn override `toString`” là kết luận quá mức.

## 7. Trường hợp lỗi: vi phạm contract và vi phạm quy ước

### 7.1. Vi phạm thực sự: equality theo giá trị, hash theo identity

Giả sử class định nghĩa `equals` dựa vào `id`, nhưng lại viết:

```java
@Override
public int hashCode() {
    return System.identityHashCode(this);
}
```

Đây là **đoạn mã minh họa cần đặt trong class**, không phải chương trình độc lập. `System.identityHashCode` cho mã băm theo nhận diện của đối tượng mà không sử dụng cách equality đã override. Hai thể hiện khác nhau có thể có cùng `id` (và vì vậy `equals` trả `true`) nhưng có identity hash khác nhau. Thiết kế này **không bảo đảm** yêu cầu “equals bằng thì hash bằng”. Không thể viện lý do “code chạy được” để coi nó đúng contract.

Cách sửa là tính hash từ cùng các trường xác định equality, chẳng hạn `return id.hashCode();` nếu `id` không phải `null`. Cũng cần đảm bảo mọi trường dùng trong `equals` được tính một cách nhất quán khi xác định `hashCode`; không nhất thiết dùng mọi trường trong hash, nhưng phải giữ điều kiện hai giá trị bằng nhau có hash bằng nhau.

### 7.2. Không vi phạm contract: nhiều giá trị chung một hash

```java
@Override
public int hashCode() {
    return 1;
}
```

Đây cũng chỉ là phương thức minh họa đặt trong class. Nếu `equals` đúng và mọi đối tượng đều trả mã băm `1`, quy tắc tương thích vẫn đúng. Vấn đề ở đây là **hiệu năng**, không phải tính hợp lệ của equality contract. Muốn cải thiện phân bố hash có thể chọn thuật toán khác, miễn giữ ràng buộc bắt buộc.

### 7.3. Lỗi sử dụng: sửa khóa sau khi thêm vào `HashSet`/`HashMap`

Nếu một object thay đổi trường tham gia `equals` và `hashCode` sau khi được dùng làm khóa, vị trí tổ chức tra cứu trước đó có thể không còn phù hợp với mã băm mới. `contains` hoặc `get` có thể không tìm thấy khóa mà lập trình viên tưởng vẫn còn.

Đây là lý do nên dùng đối tượng khóa bất biến (*immutable*) hoặc ít nhất giữ các trường tham gia equality không đổi trong suốt thời gian khóa ở trong collection. Cần phân biệt: tính nhất quán của hash được Java yêu cầu **khi thông tin dùng để so sánh chưa đổi**; Java không cấm mọi object có trạng thái thay đổi.

### 7.4. Lỗi suy luận: nhầm cùng hash với bằng nhau

`a.hashCode() == b.hashCode()` chỉ nói hai kết quả số nguyên trùng nhau. Nó **không chứng minh** `a.equals(b)`. Trong mã nghiệp vụ, không được dùng mã băm như khóa định danh duy nhất hay thay cho phép so sánh giá trị đầy đủ nếu rủi ro trùng mã băm không được xử lý.

## 8. Những ranh giới thường bị nhầm với Object Contract

**`Comparable.compareTo` và `equals`:** `compareTo` thuộc interface `Comparable`, không phải phương thức của `Object`. Nên thiết kế thứ tự tự nhiên nhất quán với `equals` khi có thể, tức `compareTo(x) == 0` tương ứng với equality, đặc biệt khi muốn dùng `TreeSet`/`TreeMap`. Nhưng đây là **khuyến nghị** về tính nhất quán với equals, không phải yêu cầu phổ quát của `Object.equals`. Ví dụ `new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0` nhưng `equals` là `false` vì hai biểu diễn khác scale. Các collection sắp thứ tự có thể xem hai phần tử là cùng khóa dựa vào comparator dù `equals` của chúng khác nhau.

**Mảng trong đối tượng:** Mảng Java không tự so sánh nội dung phần tử bằng `Object.equals`; `Objects.equals(array1, array2)` vẫn sử dụng equality của mảng (thông thường là theo tham chiếu). Nếu equality mong muốn dựa trên nội dung mảng, phải chọn `Arrays.equals`/`Arrays.deepEquals` theo cấu trúc dữ liệu, đồng thời sử dụng cách tính hash tương ứng như `Arrays.hashCode`/`Arrays.deepHashCode`. Đây là yêu cầu nhất quán về **kết quả**, còn API trợ giúp cụ thể là lựa chọn triển khai.

**`record`:** Với Java 17, `record UserId(String value) {}` được trình biên dịch cung cấp sẵn `equals`, `hashCode` và `toString` theo record components. Đó là một cách giảm mã lặp cho kiểu dữ liệu phù hợp, không biến mọi class Java thành record và cũng không bắt class thông thường phải dùng đúng cùng mã nguồn thuật toán.

**Cấu trúc bên trong của collection:** API bảo đảm ngữ nghĩa của các thao tác công khai theo tài liệu. Số bucket, cách phân tán hash, thời điểm resize hoặc cách JVM lưu object có thể thay đổi theo cách triển khai. Không nên suy luận contract của chương trình từ một hình vẽ nội bộ `HashMap` ở một bản JDK cụ thể.

## 9. Cách tự xác định mức độ ràng buộc khi đọc tài liệu

Khi gặp một nhận định về đối tượng Java, hãy đi theo trình tự:

1. **Xác định nơi quy tắc được công bố.** Đó là Javadoc của `Object`, `Comparable`, class thư viện cụ thể hay chỉ là bài blog/đoạn code ví dụ?
2. **Tìm ngôn ngữ quy phạm.** “Must/shall”, tính chất được đặc tả và các điều kiện đi kèm là dấu hiệu hợp đồng; “recommended”, “typically”, “generally” thường là lời khuyên hoặc mô tả thường gặp. Không dựa độc nhất vào một từ mà bỏ qua ngữ cảnh.
3. **Viết phản ví dụ.** Nếu hai đối tượng khác nhau cùng hash, Java có sai không? Không. Nếu hai đối tượng equals nhau mà hash khác, có sai không? Có.
4. **Xem ai có thể thay đổi phần đó.** Nếu thay thuật toán hash, thứ tự bucket hoặc định dạng `toString` mà API vẫn giữ đúng cam kết, đó thường là lựa chọn triển khai. Nếu thay đổi làm `equals` mất đối xứng, đó là vi phạm contract.
5. **Kiểm tra điều kiện sử dụng.** Mã chạy trong Java 17, một thư viện cụ thể, kiểu mutable hay immutable? Điều kiện nào là giả định của ví dụ thay vì quy tắc phổ quát?

### Câu hỏi tự luyện

**Câu 1.** Hai đối tượng cùng hash nhưng `equals` trả `false`. Có vi phạm `Object` contract không?

**Đáp án:** Không. Va chạm hash được phép; chỉ chiều `equals` bằng nhau ⇒ hash bằng nhau là bắt buộc.

**Câu 2.** Một class override `equals` theo mã khách hàng, để nguyên `hashCode` kế thừa từ `Object`. Vì sao cần xem lại?

**Đáp án:** Hai instance khác nhau có thể bằng nhau theo mã khách hàng nhưng hash mặc định không được thiết kế bảo đảm phù hợp equality mới. Đây là nguy cơ vi phạm ràng buộc bắt buộc.

**Câu 3.** Một thư viện đổi định dạng `toString` trong phiên bản mới, nhưng trước đó không công bố định dạng là API. Ứng dụng parse `toString` bị lỗi. Lỗi thiết kế chính nằm ở đâu?

**Đáp án:** Ứng dụng đã phụ thuộc vào định dạng không được cam kết. Nên dùng API lấy dữ liệu có cấu trúc hoặc hợp đồng serialization riêng.

**Câu 4.** Một thuật toán `hashCode` trả hằng số `1` có sai về contract không?

**Đáp án:** Không, nhưng phân bố kém và có thể khiến collection xử lý va chạm nhiều hơn.

## 10. Ghi nhớ

- **Bắt buộc:** các tính chất của `equals`; `equals` bằng nhau thì `hashCode` bằng nhau; hash nhất quán khi dữ liệu dùng trong equality chưa đổi.
- **Quy ước tốt:** equality/hash dựa trên dữ liệu có ý nghĩa, ưu tiên khóa bất biến, `toString` dễ hiểu và an toàn khi log, cân nhắc tính nhất quán giữa ordering và equality.
- **Chi tiết triển khai:** hệ số tính hash, cách kết hợp trường, dữ liệu nội bộ của hash collection, cách JVM sinh identity hash, định dạng `toString` do class tự quyết nếu không có cam kết cụ thể.
- **Không suy luận ngược:** cùng hash không có nghĩa là equals; thứ tự duyệt collection không mặc nhiên là thứ tự chèn; mã chạy thành công không tự chứng minh contract đã đúng.

Tài liệu API gốc để đối chiếu: [`Object` (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html), [`Comparable` (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Comparable.html) và [`HashMap` (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-007">Hậu quả khi một lớp phá vỡ hợp đồng trong thư viện chuẩn</a>

<details>
<summary>Xem chi tiết</summary>

# Hậu quả khi một lớp phá vỡ hợp đồng trong thư viện chuẩn Java

Khi đưa đối tượng vào `HashMap`, `HashSet`, `TreeMap`, `TreeSet` hoặc các thuật toán sắp xếp, thư viện chuẩn **tin vào các hợp đồng** mà lớp công bố qua `equals`, `hashCode`, `Comparable` và `Comparator`. Nếu lớp cài đặt sai, mã thư viện vẫn có thể chạy đúng theo thiết kế nhưng kết quả quan sát được sẽ sai kỳ vọng: không tìm lại được phần tử, lưu các phần tử trùng về mặt logic, mất dữ liệu khi chèn, thứ tự sai hoặc thuật toán sắp xếp báo lỗi.

Mấu chốt là phân biệt ba tình huống: **thật sự vi phạm hợp đồng**, **thay đổi trạng thái khóa sau khi lưu**, và **hai API dùng quy tắc xác định tương đương khác nhau nhưng đều hợp lệ**.

## 1. Những hợp đồng mà thư viện dựa vào

| Thành phần | Quy tắc thiết yếu | Nơi chịu ảnh hưởng |
| --- | --- | --- |
| `Object.equals(Object)` | Phản xạ, đối xứng, bắc cầu, nhất quán khi thông tin so sánh không đổi; `x.equals(null)` là `false` | `List.contains`, `remove`, `HashSet`, khóa `Map`, xử lý trùng lặp |
| `Object.hashCode()` | Nếu `a.equals(b)` thì `a.hashCode() == b.hashCode()`; hash ổn định khi dữ liệu dùng để xác định bằng nhau không đổi | `HashMap`, `HashSet`, `LinkedHashMap`, `LinkedHashSet` |
| `Comparable.compareTo` / `Comparator.compare` | Kết quả so sánh có dấu nhất quán, phản đối xứng về dấu, có tính bắc cầu và nhất quán khi trả về `0` | `TreeSet`, `TreeMap`, `Arrays.sort`, `List.sort`, `Collections.sort` |
| Quy tắc khóa của `Map` | Không thay đổi dữ liệu ảnh hưởng đến `equals`/`hashCode` của khóa trong lúc nó đang là khóa | Tra cứu, xóa, cập nhật mục trong bản đồ |

### Đối với `equals` và `hashCode`

- `x.equals(x)` phải là `true` (phản xạ).
- `x.equals(y)` phải có cùng kết quả với `y.equals(x)` (đối xứng).
- Nếu `x.equals(y)` và `y.equals(z)` đều đúng thì `x.equals(z)` phải đúng (bắc cầu).
- Khi dữ liệu dùng để so sánh không đổi, các lần gọi `equals` phải nhất quán; `equals(null)` phải trả `false`.
- **Chiều bắt buộc** là `equals == true` kéo theo **cùng hash code**. Chiều ngược lại không bắt buộc: hai đối tượng khác nhau có thể có cùng hash code (va chạm hash).

Việc cài `equals` mà bỏ quên `hashCode`, hoặc để hai phương thức dùng tập thuộc tính khác nhau, rất dễ phá vỡ chiều bắt buộc trên. `HashMap` dùng hash để chọn vị trí tìm kiếm và dùng `equals` để xác định khóa tương đương trong phần phù hợp; nó không thể tự sửa một quy tắc so sánh mâu thuẫn.

### Đối với `Comparable`/`Comparator`

Một thứ tự hợp lệ phải đủ nhất quán để các thuật toán sắp xếp và cấu trúc cây hoạt động. Ví dụ, nếu so sánh cho biết `a < b` và `b < c` thì không thể đồng thời cho `a > c`. Nếu `compare(a,b) == 0` thì dấu so sánh của `a` và `b` với một `c` phải nhất quán. Vi phạm có thể gây thứ tự vô lý; trong một số trường hợp `List.sort`/`Arrays.sort` ném `IllegalArgumentException: Comparison method violates its general contract!`, nhưng **không phải mọi dữ liệu đầu vào đều kích hoạt lỗi**.

Việc `compare(a,b) == 0` trong khi `!a.equals(b)` **không tự động vi phạm hợp đồng `Comparator`**. Tuy nhiên, với `SortedSet` và `SortedMap`, nó thay đổi khái niệm “trùng nhau”; nếu cần phù hợp đầy đủ với hợp đồng chung của `Set`/`Map` thì thứ tự nên nhất quán với `equals`, hoặc sự khác biệt phải được hiểu và ghi rõ.

## 2. Hậu quả cụ thể

### 2.1. `HashSet` có thể chứa hai đối tượng vốn phải được coi là bằng nhau

Giả sử `a.equals(b) == true` nhưng `a.hashCode() != b.hashCode()`. Hai phần tử có thể đi vào hai nhóm hash khác nhau. Khi đó `HashSet.add(a)` rồi `add(b)` có thể làm kích thước tăng lên `2`, mặc dù theo định nghĩa `equals` của lớp, chúng là một giá trị logic.

Vấn đề tương tự xuất hiện ở khóa `HashMap`: `get`, `containsKey`, `remove` hoặc `put` dùng một đối tượng tương đương mới có thể không truy cập đúng mục đã lưu. Các lớp thư viện này không có nghĩa vụ dò toàn bộ bảng để bù cho `hashCode` sai.

### 2.2. Khóa mutable có thể trở nên “không tìm thấy” sau khi chèn

Ngay cả khi `equals` và `hashCode` được viết **đúng với trạng thái hiện tại**, thay đổi thuộc tính tham gia hai phương thức sau `map.put(key, value)` vẫn nguy hiểm. Bản đồ đã sắp xếp khóa dựa trên hash tại lúc chèn; sau khi đổi trạng thái, việc tìm với hash mới có thể thất bại. Mục vẫn tồn tại trong bản đồ và vẫn chiếm bộ nhớ, nhưng API tra cứu thông thường có thể không tìm được.

Đây là **vi phạm giả định sử dụng khóa ổn định**, không nhất thiết là vi phạm hợp đồng `hashCode`: hợp đồng cho phép hash thay đổi khi dữ liệu dùng bởi `equals` thay đổi.

### 2.3. `TreeSet`/`TreeMap` có thể làm mất mục theo góc nhìn `equals`

`TreeSet` và `TreeMap` dùng kết quả so sánh (`compareTo` hoặc `Comparator`) để tìm vị trí và quyết định hai khóa có tương đương hay không: `compare(a,b) == 0` được xử lý như một khóa đã có. Vì thế:

- `TreeSet.add` có thể trả `false` cho một phần tử mà `equals` xem là khác.
- `TreeMap.put` với khóa so sánh bằng `0` có thể **thay giá trị của khóa cũ** thay vì thêm mục mới.
- `contains`, `remove` và phạm vi sắp xếp phản ánh quy tắc so sánh, nên có thể khác suy nghĩ dựa trên `equals`.

Không được vội kết luận thư viện làm mất dữ liệu vì lỗi: trước tiên phải kiểm tra chính xác quy tắc bằng nhau mà cấu trúc dữ liệu đang dùng.

### 2.4. Lỗi có thể lan sang tầng nghiệp vụ

Với định danh tài khoản, hàng đợi công việc, cache hoặc bộ lọc chống trùng, lỗi hợp đồng có thể làm dữ liệu trùng không được loại bỏ; truy vấn cache bỏ lỡ; chức năng xóa không xóa được phần tử kỳ vọng; thống kê kích thước sai; hoặc một khóa bị ghi đè theo quy tắc sắp xếp. Đầu ra đôi lúc thay đổi theo thứ tự thao tác, tập dữ liệu và trạng thái đối tượng, nên lỗi khó tái hiện nếu chỉ thử một trường hợp thuận lợi.

## 3. Ví dụ Java 17+: ba hiện tượng khác nhau

Tệp `ContractConsequencesDemo.java` dưới đây chạy độc lập, cố ý chứa hai lớp khóa thiết kế sai hoặc sử dụng sai để quan sát hậu quả. Trường hợp `BigDecimal` ở cuối là **khác biệt được tài liệu Java công bố**, không phải bug của `BigDecimal`.

```java
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ContractConsequencesDemo {
    // SAI: equals so sánh id, hashCode lại phụ thuộc vào hint độc lập.
    static final class BadKey {
        final String id;
        final int hint;

        BadKey(String id, int hint) {
            this.id = id;
            this.hint = hint;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BadKey that && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return hint;
        }
    }

    // Đúng theo trạng thái hiện tại, nhưng không an toàn khi dùng làm khóa rồi sửa id.
    static final class MutableKey {
        String id;

        MutableKey(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableKey that && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    public static void main(String[] args) {
        BadKey a = new BadKey("X", 1);
        BadKey b = new BadKey("X", 2);
        Set<BadKey> brokenSet = new HashSet<>();
        brokenSet.add(a);
        brokenSet.add(b);

        System.out.println(a.equals(b));                   // true
        System.out.println(a.hashCode() == b.hashCode());   // false
        System.out.println(brokenSet.size());               // 2

        MutableKey key = new MutableKey("A");
        Map<MutableKey, String> map = new HashMap<>();
        map.put(key, "stored");
        System.out.println(map.get(key));                   // stored
        key.id = "B";
        System.out.println(map.get(key));                   // null
        System.out.println(map.size());                     // 1

        List<BigDecimal> numbers = List.of(
                new BigDecimal("1.0"),
                new BigDecimal("1.00")
        );
        System.out.println(new HashSet<>(numbers).size());  // 2
        System.out.println(new TreeSet<>(numbers).size());  // 1
        System.out.println(numbers.get(0).equals(numbers.get(1)));   // false
        System.out.println(numbers.get(0).compareTo(numbers.get(1))); // 0
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ContractConsequencesDemo.java
java ContractConsequencesDemo
```

Kết quả chuẩn:

```text
true
false
2
stored
null
1
2
1
false
0
```

Giải thích từng nhóm:

1. Hai `BadKey` có cùng `id` nên `equals` trả `true`, nhưng hash code khác `1` và `2`. `HashSet` lưu được cả hai: lớp `BadKey` đã phá vỡ hợp đồng, không phải `HashSet`.
2. `MutableKey` ban đầu có `id = "A"` nên tìm được chuỗi `stored`. Sau khi đổi thành `"B"`, mã hash của chuỗi đổi; `HashMap.get(key)` không tìm thấy mục cũ, dù `size()` vẫn bằng `1`. Ví dụ dùng chuỗi `"A"`/`"B"` để đầu ra xác định trên `HashMap` của Java chuẩn.
3. `BigDecimal("1.0")` và `BigDecimal("1.00")` khác nhau theo `equals` vì khác scale, nhưng `compareTo` trả `0` do cùng giá trị số. `HashSet` coi là hai phần tử; `TreeSet` coi là một theo thứ tự tự nhiên. Đây là hành vi có chủ đích, cho thấy chọn cấu trúc dữ liệu cũng là chọn tiêu chí tương đương.

## 4. Những kiểu lỗi thường gặp khi tự cài đặt

### Overload nhầm `equals` thay vì override

```java
// SAI: chỉ tạo overload, không ghi đè Object.equals(Object).
public boolean equals(UserId other) {
    return other != null && value.equals(other.value);
}
```

`HashSet`/`HashMap` gọi chữ ký `equals(Object)`. Nếu lớp chỉ có `equals(UserId)`, chúng vẫn có thể dùng phương thức kế thừa từ `Object` (so sánh định danh tham chiếu), khiến hai giá trị cùng mã vẫn bị xem là khác nhau. Dùng `@Override` và khai báo đúng `public boolean equals(Object other)` để trình biên dịch phát hiện lỗi chữ ký.

### Bỏ qua đối xứng hoặc bắc cầu khi kế thừa

Một lớp cha cho phép mọi lớp con cùng `id` bằng nhau, nhưng lớp con lại bắt buộc thêm `region`, có thể tạo `parent.equals(child) == true` trong khi `child.equals(parent) == false`. Thêm nhiều đối tượng còn có thể phá tính bắc cầu. Các cấu trúc dùng `equals` không thể định nghĩa một tập hợp nhất quán khi quan hệ “bằng nhau” tự mâu thuẫn.

Khi thiết kế value object, ưu tiên giá trị bất biến, lớp `final` hoặc mô hình so sánh có chiến lược kế thừa rõ ràng. Tránh sửa `equals` theo cách làm hai phía bất đồng.

### Comparator dùng phép trừ hoặc dùng dữ liệu biến đổi

```java
// Nguy hiểm: a - b có thể tràn số nguyên.
Comparator<Integer> bad = (a, b) -> a - b;

// Đúng và tránh tràn:
Comparator<Integer> good = Integer::compare;
```

Đây là mảnh mã minh họa, không phải chương trình Java độc lập. Phép trừ số nguyên có thể tràn khiến dấu sai và làm thứ tự không nhất quán. Một `Comparator` đọc thuộc tính mutable đang thay đổi trong khi đối tượng ở `TreeSet`/`TreeMap` cũng có thể khiến thao tác tra cứu hoặc thứ tự cây không còn phù hợp với hiện trạng.

### Nhầm `toString` với danh tính đối tượng

`Object.toString()` phục vụ biểu diễn văn bản, gỡ lỗi và logging; nó không thay `equals`/`hashCode`. Hai đối tượng có cùng chuỗi `toString()` không mặc nhiên bằng nhau, và bản thân chuỗi hiển thị không nhất thiết ổn định hoặc duy nhất. Đừng lấy định dạng log làm tiêu chí khóa nếu chưa xác định rõ ngữ nghĩa.

## 5. Cách thiết kế và phòng tránh

1. **Xác định danh tính logic trước khi viết code.** Với `UserId`, có thể chỉ `id` tham gia bằng nhau; với đối tượng miền nghiệp vụ, phải quyết định rõ có đưa phiên bản, trạng thái, loại đối tượng hoặc trường hợp kế thừa vào hay không.
2. **Cài `equals` và `hashCode` cùng một tập thuộc tính.** Cập nhật cả hai khi thay đổi định nghĩa bằng nhau. Dùng `Objects.equals`, `Objects.hash` khi phù hợp; hash code có va chạm vẫn đúng về ngữ nghĩa, dù có thể ảnh hưởng hiệu năng.
3. **Ưu tiên khóa bất biến.** Ví dụ `record UserId(String value) {}` ở Java 17 tự sinh `equals`/`hashCode` theo thành phần `value`. Nhưng record không tự làm cho đối tượng tham chiếu bên trong bất biến: nếu thành phần là mảng hay collection mutable, vẫn cần thiết kế sao cho so sánh và hash ổn định. Với mảng, `equals`/`hashCode` sinh bởi record dùng ngữ nghĩa tham chiếu của mảng, không tự so sánh sâu các phần tử.
4. **Chọn quy tắc sắp xếp có chủ đích.** Khi dùng `TreeSet`/`TreeMap`, kiểm tra ý nghĩa của `compare == 0`. Nếu hai đối tượng khác nhau theo danh tính nghiệp vụ phải cùng tồn tại, comparator cần khóa phân biệt đủ mạnh, ví dụ so sánh thêm mã định danh ổn định. Nếu cố ý gộp theo một giá trị, phải chấp nhận semantics của sorted set/map.
5. **Không thay đổi thuộc tính ảnh hưởng đến thứ tự/khóa sau khi chèn.** Muốn cập nhật, hãy loại bỏ theo trạng thái cũ trước, tạo giá trị mới hoặc cập nhật lúc đối tượng chưa nằm trong collection, rồi chèn lại; với khóa map không thể tra cứu, có thể cần xây dựng lại cấu trúc từ nguồn dữ liệu tin cậy.
6. **Kiểm thử hợp đồng, không chỉ một lệnh `put/get`.** Tạo ít nhất ba đối tượng đại diện cho cùng giá trị, giá trị khác, lớp cha/con hoặc trạng thái khác nếu có. Kiểm tra phản xạ, đối xứng, bắc cầu, `equals(null)`, chiều kéo theo giữa `equals` và `hashCode`, cùng việc tìm/xóa bằng một đối tượng tương đương mới. Với comparator, kiểm tra ba phần tử, thứ tự đảo và trường hợp bằng `0`, đặc biệt các số ở biên.

Một phép thử rất quan trọng: nếu `a.equals(b)` trả `true` thì **bắt buộc** `a.hashCode() == b.hashCode()`. Phép thử chiều ngược lại (`hashCode` bằng nhau suy ra `equals`) là **sai** và không nên viết.

## 6. Checklist chẩn đoán khi collection hoạt động lạ

| Hiện tượng | Kiểm tra đầu tiên | Cách xử lý có định hướng |
| --- | --- | --- |
| `HashSet` giữ hai phần tử tưởng là trùng | `equals` có thật sự override `Object.equals`? Hash của hai đối tượng bằng nhau khi `equals` đúng? | Sửa quan hệ bằng nhau và hàm hash; dựng lại set sau khi sửa |
| `HashMap.get`/`remove` không thấy khóa từng chèn | Khóa có bị sửa trường tham gia `equals`/`hashCode` sau `put`? | Dùng khóa bất biến; khôi phục hoặc dựng lại map từ dữ liệu nguồn |
| `TreeSet` bỏ phần tử hoặc `TreeMap` ghi đè giá trị | `compareTo`/`Comparator.compare` có trả `0` cho hai khóa muốn phân biệt? | Thiết kế comparator thể hiện đúng danh tính cần lưu |
| Kết quả sort bất thường hoặc thỉnh thoảng ném `IllegalArgumentException` | Comparator có bắc cầu, phản đối xứng về dấu và nhất quán khi trả `0`? Có tràn số hoặc trạng thái thay đổi? | Dùng phép so sánh an toàn, khóa sắp xếp ổn định, bổ sung kiểm thử bộ ba |
| `HashSet` và `TreeSet` có kích thước khác nhau | `equals` và thứ tự tự nhiên có cùng định nghĩa tương đương? Ví dụ `BigDecimal` | Xác định nhu cầu nghiệp vụ: so theo scale hay chỉ giá trị số |

**Kết luận:** Collection của Java dựa trên hợp đồng do **lớp phần tử/khóa hoặc comparator** cung cấp. Vi phạm hợp đồng làm lộ lỗi ở thao tác tưởng như vô can (`contains`, `get`, `add`, `remove`, `sort`). Cách phòng ngừa là định nghĩa một quan hệ bằng nhau hợp lệ, sinh hash tương thích, giữ khóa ổn định và hiểu rõ tiêu chí tương đương riêng của collection có thứ tự.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-008">Lộ trình học và ví dụ xuyên suốt với đối tượng giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Lộ trình học Java Object Contract với một đối tượng giá trị xuyên suốt

## 1. Mục tiêu và bối cảnh

Trong Java, mọi lớp thông thường đều kế thừa các hành vi nền tảng từ `java.lang.Object`. Khi một lớp biểu diễn **giá trị** (value object), hai instance khác địa chỉ bộ nhớ có thể mang cùng một ý nghĩa nghiệp vụ. Vì vậy, phải xác định rõ khái niệm “bằng nhau”, cách đưa đối tượng vào cấu trúc băm, cách mô tả đối tượng và (nếu cần) cách sắp xếp.

Bài học dùng duy nhất một tình huống xuyên suốt: `OrderKey` là khóa đơn hàng gồm **mã cửa hàng** (`tenant`) và **số đơn hàng** (`number`). Chẳng hạn `new OrderKey(" shop ", 42)` và `new OrderKey("SHOP", 42)` cùng xác định một đơn hàng sau khi chuẩn hóa mã cửa hàng. `OrderKey` là value object: giá trị các trường quyết định danh tính logic của nó; object không đổi sau khi được tạo.

Sau bài này, người học cần có thể:
- Phân biệt `==` (cùng tham chiếu) với `equals` (bằng nhau về giá trị theo hợp đồng của lớp).
- Phát biểu và kiểm tra hợp đồng `equals`/`hashCode`, giải thích tại sao `HashMap` và `HashSet` phụ thuộc vào cả hai.
- Thiết kế `toString` phục vụ quan sát/gỡ lỗi nhưng không nhầm nó với định dạng lưu trữ ổn định.
- Viết `Comparable`/`Comparator` phù hợp với định nghĩa bằng nhau; hiểu rủi ro khi thứ tự tự nhiên không nhất quán với `equals`.
- Giữ value object bất biến, kiểm tra đầu vào, tránh thay đổi khóa sau khi đưa vào collection.

## 2. Lộ trình đề xuất và tiêu chí hoàn thành

| Chặng | Câu hỏi cần trả lời | Thực hành trên `OrderKey` | Dấu hiệu đã hiểu |
|---|---|---|---|
| 0. Nền tảng | Object, tham chiếu, instance, giá trị, bất biến khác nhau thế nào? | Tạo hai instance riêng có cùng dữ liệu | Giải thích được tại sao `a == b` là `false` |
| 1. Equality | Khi nào hai đơn hàng là “cùng khóa”? | Chọn cặp trường `tenant` và `number` cho `equals` | Kiểm tra đủ 5 tính chất của `equals` |
| 2. Hashing | Vì sao lookup bằng đối tượng khác vẫn tìm được? | Ghi đè `hashCode` đồng bộ với `equals` | `HashSet` loại trùng, `HashMap` tra cứu thành công |
| 3. Representation | Log cho biết gì về đối tượng? | Ghi đè `toString` với thông tin rõ ràng | Đọc log mà không cần biết địa chỉ bộ nhớ |
| 4. Ordering | Sắp xếp các khóa theo quy tắc nào? | So sánh `tenant` rồi `number` với `Comparable` | `TreeSet` không làm mất khóa khác nhau |
| 5. Immutability | Khóa thay đổi sau khi hash sẽ ra sao? | Trường `private final`, chuẩn hóa đầu vào, không setter | Giá trị băm không đổi trong suốt vòng đời |
| 6. Tích hợp và kiểm thử | Hợp đồng hoạt động trong collection thật không? | Chạy chương trình cuối bài và bài tập biến thể | Dự đoán, giải thích đúng toàn bộ output |

**Thứ tự thực hành:** bắt đầu bằng hai tham chiếu khác nhau; thêm equality; ngay sau đó thêm hashing; kiểm tra bằng `HashSet`/`HashMap`; cải thiện cách in đối tượng; cuối cùng mới bổ sung natural ordering và kiểm tra `TreeSet`. Không cần học thuộc mọi phương thức của `Object` cùng lúc.

## 3. Các hợp đồng cần nắm

### 3.1. `==` và `equals`

Với hai biến kiểu tham chiếu, `a == b` hỏi chúng có trỏ tới **cùng một object** hay không. `a.equals(b)` hỏi lớp định nghĩa “bằng nhau” như thế nào. `Object.equals` mặc định tương đương so sánh danh tính tham chiếu; một value object thường cần ghi đè.

Hợp đồng `equals` với các tham chiếu khác `null`:
1. **Phản xạ:** `x.equals(x)` luôn `true`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` cho cùng kết quả.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` đều đúng thì `x.equals(z)` đúng.
4. **Nhất quán:** khi dữ liệu tham gia so sánh không đổi, gọi lại cho cùng kết quả.
5. **Với null:** `x.equals(null)` luôn `false`.

Đối với `OrderKey`, toàn bộ định nghĩa “bằng nhau” là `tenant` chuẩn hóa bằng nhau **và** `number` bằng nhau. Không dùng tên biến, địa chỉ bộ nhớ, `toString` hoặc chỉ một trường để suy đoán sự bằng nhau.

### 3.2. `hashCode`

Nếu `x.equals(y)` là `true` thì **bắt buộc** `x.hashCode() == y.hashCode()`. Chiều ngược lại không đúng: hai object khác nhau vẫn có thể trùng mã băm (collision). Giá trị băm của một object phải nhất quán khi các dữ liệu dùng bởi `equals` không thay đổi.

`HashMap`/`HashSet` dùng mã băm để định hướng tìm bucket và dùng equality để phân biệt khóa/phần tử trong bucket. Ghi đè `equals` mà bỏ `hashCode` khiến hai value object bằng nhau có thể có hash khác nhau, dẫn tới hành vi tìm kiếm/loại trùng sai. Vì thế **luôn ghi đè chúng cùng nhau**, dựa trên cùng bộ thuộc tính. `Objects.hash(tenant, number)` dễ đọc cho ví dụ; code nhạy hiệu năng có thể dùng phép kết hợp hash cụ thể sau khi đo đạc.

### 3.3. `toString`

`toString` của `Object` thường cho chuỗi dạng tên lớp và mã băm dạng hex; đây không phải danh sách thuộc tính nghiệp vụ. Ghi đè `toString` để phục vụ log và debug, ví dụ `OrderKey[tenant=SHOP, number=42]`. Không dùng `toString` làm cơ sở so sánh hay khóa dữ liệu, không coi định dạng này là API serialization. Tránh in thông tin nhạy cảm trong object thực tế.

### 3.4. `Comparable`, `Comparator` và tính nhất quán

`Comparable<T>` đặt **thứ tự tự nhiên** cho một lớp thông qua `compareTo` (âm/0/dương khi nhỏ hơn/bằng về thứ tự/lớn hơn). `Comparator<T>` diễn tả một cách sắp xếp bên ngoài, tiện khi cần nhiều tiêu chí khác nhau.

Với value object dùng trong `TreeSet` hoặc làm khóa `TreeMap`, nên thiết kế:

`a.compareTo(b) == 0` ⇔ `a.equals(b)`.

Các collection có thứ tự dùng kết quả so sánh bằng 0 để xác định phần tử/khóa tương đương theo thứ tự. Nếu quy tắc này không nhất quán với `equals`, `TreeSet` và `HashSet` có thể cho số phần tử khác nhau. Ví dụ quen thuộc: `new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0` nhưng hai số này có `equals` là `false` vì scale khác nhau. Đây là điểm cần cân nhắc khi chọn value object hoặc comparator.

## 4. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`ObjectContractDemo.java`** với nội dung sau. Chương trình dùng một lớp value object `final` lồng bên trong để có thể biên dịch toàn bộ bằng một tệp. Java 17 hỗ trợ pattern matching với `instanceof` được dùng ở đây.

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ObjectContractDemo {
    public static void main(String[] args) {
        OrderKey first = new OrderKey("  shop  ", 42L);
        OrderKey sameValue = new OrderKey("SHOP", 42L);
        OrderKey next = new OrderKey("shop", 43L);

        System.out.println("==: " + (first == sameValue));
        System.out.println("equals: " + first.equals(sameValue));
        System.out.println("hashCode equal: "
                + (first.hashCode() == sameValue.hashCode()));

        Set<OrderKey> unique = new HashSet<>(
                List.of(first, sameValue, next));
        System.out.println("HashSet size: " + unique.size());

        Map<OrderKey, String> status = new HashMap<>();
        status.put(first, "paid");
        System.out.println("HashMap lookup: " + status.get(sameValue));

        Set<OrderKey> sorted = new TreeSet<>(
                List.of(next, sameValue, first));
        System.out.println("TreeSet: " + sorted);
        System.out.println("compareTo == 0: "
                + (first.compareTo(sameValue) == 0));
        System.out.println("toString: " + first);
    }

    public static final class OrderKey implements Comparable<OrderKey> {
        private final String tenant;
        private final long number;

        public OrderKey(String tenant, long number) {
            String normalized = Objects.requireNonNull(tenant, "tenant")
                    .strip()
                    .toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("tenant is blank");
            }
            if (number <= 0) {
                throw new IllegalArgumentException(
                        "number must be positive");
            }
            this.tenant = normalized;
            this.number = number;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof OrderKey that)) {
                return false;
            }
            return number == that.number && tenant.equals(that.tenant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenant, number);
        }

        @Override
        public String toString() {
            return "OrderKey[tenant=" + tenant
                    + ", number=" + number + "]";
        }

        @Override
        public int compareTo(OrderKey other) {
            Objects.requireNonNull(other, "other");
            int byTenant = tenant.compareTo(other.tenant);
            return byTenant != 0
                    ? byTenant
                    : Long.compare(number, other.number);
        }
    }
}
```

Chạy tại thư mục chứa tệp bằng JDK 17 trở lên:

```bash
javac --release 17 ObjectContractDemo.java
java ObjectContractDemo
```

Output dự kiến (thứ tự in của `TreeSet` đã được cố định bởi `compareTo`; không phụ thuộc thứ tự của `HashSet`):

```text
==: false
equals: true
hashCode equal: true
HashSet size: 2
HashMap lookup: paid
TreeSet: [OrderKey[tenant=SHOP, number=42], OrderKey[tenant=SHOP, number=43]]
compareTo == 0: true
toString: OrderKey[tenant=SHOP, number=42]
```

Giải thích theo thứ tự:
- `first` và `sameValue` là hai object khác nhau nên `==` trả về `false`. Constructor đã chuẩn hóa khoảng trắng và chữ hoa bằng `Locale.ROOT`, vì vậy `equals` trả về `true`.
- Khi `equals` là `true`, hai object này buộc có cùng `hashCode`; phần tử trùng được `HashSet` gộp, còn `next` với `number=43` là khóa khác. Kết quả tập hợp có 2 phần tử.
- `HashMap` có thể lấy giá trị đã lưu với `first` bằng khóa `sameValue`, minh họa lookup theo giá trị.
- `TreeSet` dùng `compareTo`: so sánh `tenant` trước, `number` sau. Hai object cùng khóa cho `compareTo` bằng 0, nên tập hợp có một khóa 42 và một khóa 43.
- Chuỗi hiển thị do `toString` chủ động quyết định; không dùng output này để thực hiện logic equality.

### Vì sao ví dụ là một value object hợp lệ?

**Bất biến:** Lớp được khai báo `final`, hai trường `private final`, không có setter. `String` là kiểu bất biến, `long` là primitive nên caller không thể thay đổi dữ liệu bên trong qua tham chiếu rò rỉ. Chuẩn hóa và kiểm tra ngay trong constructor ngăn trạng thái không hợp lệ.

**Bình đẳng có ngữ nghĩa:** `equals` dựa trên đúng hai trường quyết định ý nghĩa của khóa. Nhánh `this == other` là tối ưu nhanh; phép `instanceof OrderKey` an toàn ở đây vì lớp `final` không có subclass gây phá đối xứng.

**Hashing phù hợp:** `hashCode` dùng cùng `tenant`, `number`; cả hai bất biến nên hash ổn định sau khi đưa object vào `HashMap`.

**Sắp xếp phù hợp:** `compareTo` xét cả hai trường, cùng thứ tự ý nghĩa với `equals`; dùng `Long.compare` tránh lỗi khi tự trừ hai số `long` rồi ép về `int`.

**Biên đầu vào rõ ràng:** `null` tenant làm phát sinh `NullPointerException` từ `requireNonNull`; chuỗi rỗng sau `strip` và số đơn không dương bị từ chối bằng `IllegalArgumentException`. Quy tắc chuẩn hóa chữ hoa phải phù hợp miền nghiệp vụ thực tế; ví dụ giả định mã cửa hàng không phân biệt hoa/thường.

## 5. Các lỗi thường gặp và cách phát hiện

| Sai lầm | Hậu quả quan sát được | Cách xử lý |
|---|---|---|
| Chỉ ghi đè `equals` | `HashSet`/`HashMap` có thể không nhận ra hai khóa bằng nhau | Ghi đè `hashCode` với cùng các trường |
| `hashCode` tính thêm trường không có trong `equals` | Hai object bằng nhau có thể có hash khác nhau | Dùng cùng nguồn dữ liệu quyết định equality |
| Khóa có setter làm thay đổi trường tham gia hash | Tra cứu có thể thất bại sau khi đưa khóa vào map/set | Dùng value object bất biến; không thay đổi khóa |
| So sánh chuỗi bằng `==` | So sánh tham chiếu thay vì nội dung | Dùng `String.equals` hoặc `Objects.equals` khi có thể null |
| `compareTo` chỉ xét `tenant`, bỏ `number` | Hai đơn khác số có thể bị `TreeSet` gộp thành một | Đưa toàn bộ trường khóa vào quy tắc so sánh |
| Dùng phép trừ trong `compareTo` số nguyên | Có thể overflow và trả sai dấu | Dùng `Integer.compare`/`Long.compare` |
| Tự động sinh `toString` chứa mật khẩu/token | Dữ liệu nhạy cảm có thể lộ trong log | Chỉ đưa trường cần thiết, che thông tin nhạy cảm |
| Dùng lớp có kế thừa tự do rồi thêm trường equality ở subclass | Có nguy cơ phá đối xứng/bắc cầu | Ưu tiên value object `final` hoặc một thiết kế equality nhất quán |
| So sánh `double`/`BigDecimal` mà chưa chọn chính sách chuẩn hóa | Khác biệt về NaN, -0.0, scale hoặc rounding dễ gây bất ngờ | Định nghĩa miền giá trị, chuẩn hóa và kiểm thử các biên |

**Phân biệt hai mục đích:** Băm để tìm phần tử nhanh và thứ tự để sắp xếp là hai hợp đồng riêng. `hashCode` không cần tăng theo thứ tự; `compareTo` không quyết định bucket băm.

## 6. Tự kiểm thử và mở rộng ví dụ

Sau khi chạy chương trình, thực hiện tuần tự các bài tập sau; luôn dự đoán trước kết quả rồi kiểm tra:
1. Tạo `a = new OrderKey("Shop", 42)`, `b = new OrderKey(" SHOP ", 42)` và `c = new OrderKey("SHOP", 43)`. Kiểm tra `a.equals(b)`, `b.equals(a)`, `a.equals(c)`, `a.equals(null)`, `a.equals(a)`.
2. Tạo `d = new OrderKey("shop", 42)`. Kiểm tra chuỗi `a.equals(b)`, `b.equals(d)`, `a.equals(d)` để quan sát tính bắc cầu; thêm kiểm tra hash khi bằng nhau.
3. Tạm bỏ `hashCode` trong một bản thử **riêng** của lớp rồi quan sát `HashSet`/`HashMap`; trả lại code đúng sau thí nghiệm. Đây là cách thấy lỗi hợp đồng, không phải một triển khai được chấp nhận.
4. Trong bản thử riêng, sửa `compareTo` chỉ so `tenant`, giữ nguyên `equals`. So số phần tử của `HashSet` và `TreeSet` khi thêm khóa 42 và 43; giải thích vì sao kết quả khác nhau.
5. Thử `new OrderKey(null, 1)`, `new OrderKey("   ", 1)`, `new OrderKey("shop", 0)`; xác định lỗi được phát ra và lý do.
6. Viết kiểm thử tự động cho identity, 5 tính chất của `equals`, đồng nhất hash, behavior của `HashMap`/`HashSet`/`TreeSet` và thứ tự so sánh. Các kiểm thử chỉ cần các giá trị mẫu cố định; với lớp phức tạp hơn, dùng nhiều mẫu và property-based testing.

Một bộ test tối thiểu (có thể chuyển sang JUnit) nên có:

| Assertion | Kết quả cần đúng |
|---|---|
| `a != b` và `a.equals(b)` | Hai instance khác nhau nhưng cùng giá trị |
| `a.equals(null) == false` | Không bằng null |
| `a.equals(b) == b.equals(a)` | Đối xứng |
| `a.equals(b) && b.equals(d) && a.equals(d)` | Bắc cầu |
| `a.equals(b)` kéo theo `a.hashCode() == b.hashCode()` | Hợp đồng hash |
| `map.put(a, "ok"); map.get(b)` trả `"ok"` | Tra cứu theo giá trị |
| `a.compareTo(b) == 0` và `a.compareTo(c) < 0` | Thứ tự nhất quán |
| `new HashSet<>(List.of(a,b,c)).size() == 2` | Loại trùng đúng |

## 7. Liên hệ các cơ chế khác của Java và hướng học tiếp

- **`record` (Java 16+)** phù hợp với nhiều value object vì compiler tự cung cấp `equals`, `hashCode`, `toString` theo các component; record không tự cung cấp `Comparable`. Nếu cần chuẩn hóa input, hãy dùng compact constructor và kiểm chứng kết quả equality trên giá trị đã chuẩn hóa.
- **`Objects.equals`, `Objects.hash`, `Objects.requireNonNull`** giúp biểu đạt rõ null handling và các hợp đồng, nhưng không tự chọn đúng thuộc tính nghiệp vụ thay cho người thiết kế.
- **`Arrays.equals`/`Arrays.hashCode`** (hoặc phiên bản `deepEquals`/`deepHashCode` cho cấu trúc lồng thích hợp) cần được cân nhắc nếu value object có mảng: `array.equals` mặc định so tham chiếu.
- **`Object.getClass`** giúp kiểm tra lớp runtime; **`clone`** có hợp đồng riêng và thường không cần thiết với value object bất biến. **`finalize`** đã bị loại khỏi hướng thiết kế Java hiện đại (deprecated for removal), không dùng để quản lý tài nguyên. Các phương thức `wait`/`notify` liên quan phối hợp thread, không phải nền tảng của equality.
- Khi tiến tới lớp entity có danh tính vòng đời, collection mutable, ORM hoặc quan hệ kế thừa phức tạp, hãy xác định lại identity/equality theo quy tắc của mô hình đó; không bê nguyên định nghĩa value object sang mọi trường hợp.

**Tiêu chí hoàn thành bài:** có thể tự xây dựng `OrderKey` từ yêu cầu nghiệp vụ, giải thích từng ghi đè, chạy đúng ví dụ và dự đoán được khác biệt giữa `HashSet`, `HashMap`, `TreeSet` khi một hợp đồng bị vi phạm.

</details>

- [Quay lại đầu trang](#back-to-top)
