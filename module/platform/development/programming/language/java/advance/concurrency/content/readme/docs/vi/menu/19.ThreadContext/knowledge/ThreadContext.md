<a id="back-to-top"></a>

# Ngữ cảnh luồng

## Menu
- [ThreadLocal: set(), get(), remove()](#thread-context-001)
- [Khởi tạo giá trị với ThreadLocal.withInitial()](#thread-context-002)
- [InheritableThreadLocal và các giới hạn kế thừa](#thread-context-003)
- [Rò rỉ context khi tái sử dụng thread pool](#thread-context-004)
- [Truyền context qua Runnable, Callable và executor](#thread-context-005)
- [Context theo request và ranh giới vòng đời task](#thread-context-006)
- [ThreadLocal khi chạy hàng nghìn virtual threads](#thread-context-007)
- [Scoped Values (preview ở Java 21) để chia sẻ context bất biến](#thread-context-008)
- [Ưu tiên tham số tường minh khi luồng dữ liệu đơn giản](#thread-context-009)
- [Không lưu state nghiệp vụ mutable vào context chung](#thread-context-010)

## <a id="thread-context-001">ThreadLocal: set(), get(), remove()</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadLocal: set(), get(), remove() trong Java

`ThreadLocal<T>` (gói `java.lang`) cho phép **cùng một biến ThreadLocal được các thread truy cập nhưng mỗi thread có một giá trị riêng**. Ba thao tác quan trọng nhất là `set(T value)` để đặt giá trị, `get()` để đọc giá trị và `remove()` để xóa giá trị **của thread hiện tại**. Đây là công cụ quản lý **ngữ cảnh luồng** trong Java concurrency, ví dụ correlation ID của lời gọi đồng bộ; bài này tập trung vào Java chuẩn, không đi vào các tính năng riêng của Spring.

## 1. Vấn đề mà ThreadLocal giải quyết

Giả sử hai thread xử lý hai công việc độc lập và mỗi thread cần mang một mã để log. Nếu lưu mã vào một biến `static String requestId` dùng chung, lệnh ghi ở thread A có thể ghi đè dữ liệu của B. Thêm `volatile` chỉ tăng bảo đảm nhìn thấy giá trị dùng chung, chứ **không tạo ra các giá trị độc lập theo thread**. Nếu mọi hàm đều nhận tham số `requestId` thì rất tường minh; tuy nhiên trong một số chuỗi lời gọi đồng bộ sâu, ThreadLocal giúp truyền ngữ cảnh **ngầm định trên cùng thread**.

```text
               Cùng một đối tượng ThreadLocal<String>
                       /                    \
                  Thread A                 Thread B
               set("A-100")             set("B-200")
                  get() -> A-100           get() -> B-200
```

Bản chất là mỗi thread có ánh xạ từ **đối tượng ThreadLocal** tới **giá trị của thread đó**. ThreadLocal không làm các tác vụ tự động an toàn; nó giúp tránh tranh chấp **vì từng thread đọc/ghi slot của chính mình**.

**Giới hạn:** Hai thread có thể cố ý hoặc vô tình `set()` cùng **một tham chiếu đến đối tượng mutable**. Khi đó dữ liệu bên trong đối tượng vẫn có thể bị truy cập đồng thời; ThreadLocal không tạo bản sao sâu và không thay thế khóa hay các kiểu atomic.

## 2. Khởi tạo và vòng đời

Hai cách khai báo phổ biến:

```java
private static final ThreadLocal<String> CURRENT_ID =
        new ThreadLocal<>();

private static final ThreadLocal<String> CURRENT_ID_WITH_DEFAULT =
        ThreadLocal.withInitial(() -> "UNKNOWN");
```

- Với `new ThreadLocal<>()`, phương thức `initialValue()` mặc định trả về `null`.
- `withInitial(Supplier)` cung cấp giá trị khởi tạo khi **một thread gọi `get()` lần đầu trong khi chưa có ánh xạ**.
- Có thể dùng `private static final` cho tham chiếu ThreadLocal chung của ứng dụng: `static final` giữ ổn định **khóa ThreadLocal**, không làm cho các giá trị của các thread trở thành một giá trị toàn cục.
- Mỗi thread giữ ánh xạ riêng cho đến khi thay bằng `set()`, xóa bằng `remove()` hoặc thread kết thúc (và dữ liệu không còn tham chiếu khác). Với worker thread sống lâu, ánh xạ có thể tồn tại qua nhiều tác vụ.

### `set(T value)`

```java
CURRENT_ID.set("request-1");
CURRENT_ID.set("request-2");
```

Lệnh `set()` sau ghi đè giá trị trước **trong thread đang thực thi**. Nếu thread chưa từng gọi `get()`, `set()` trực tiếp đặt giá trị, không cần gọi supplier khởi tạo. `set(null)` là lời gọi hợp lệ: ánh xạ vẫn có, nhưng giá trị được lưu là `null`.

### `get()`

`get()` trả về giá trị đã đặt qua `set()` hoặc giá trị đã được khởi tạo cho thread. Nếu chưa có ánh xạ, nó gọi `initialValue()` của ThreadLocal, lưu kết quả rồi trả về kết quả. Supplier của `withInitial` thường chỉ chạy khi cần khởi tạo một ánh xạ mới, **không chạy lại ở mỗi lần `get()`**.

Điều kiện `get() == null` không giúp xác định chính xác trạng thái của slot: đó có thể là giá trị khởi tạo mặc định `null`, hoặc giá trị được đặt rõ ràng bởi `set(null)`.

### `remove()`

```java
CURRENT_ID.remove();
```

`remove()` xóa ánh xạ của ThreadLocal **trên thread đang gọi**, không xóa slot của thread khác. Sau đó, `get()` ở thread đó có thể khởi tạo giá trị **lần nữa** theo `initialValue()` hoặc supplier của `withInitial`.

Điểm khác biệt cần nhớ:

| Thao tác | Ánh xạ trên thread hiện tại | Lần `get()` tiếp theo |
| --- | --- | --- |
| `set("A")` | Có, giá trị `"A"` | Trả `"A"` |
| `set(null)` | Có, giá trị `null` | Trả `null`, không khởi tạo lại |
| `remove()` | Không còn ánh xạ | Tạo ánh xạ mới từ `initialValue()` |
| `get()` khi chưa có ánh xạ | Sẽ có ánh xạ sau khi khởi tạo thành công | Trả giá trị đã khởi tạo |

Với `ThreadLocal.withInitial(() -> "UNKNOWN")`, chuỗi `get() -> set(null) -> get() -> remove() -> get()` lần lượt cho `"UNKNOWN"`, `null` rồi `"UNKNOWN"` ở các lời gọi `get()` tương ứng. Nếu supplier ném exception, việc khởi tạo không hoàn thành bình thường; lần đọc sau có thể thử lại.

## 3. Ví dụ chạy được với Java 17+: độc lập theo thread và nguy cơ rò ngữ cảnh

Ví dụ dưới dùng **một worker được tái sử dụng** để cho thấy lỗi thường gặp khi quên `remove()`. Task thứ nhất **cố ý** để lại dữ liệu; task thứ hai không đặt ID mà vẫn thấy ID cũ. Task thứ ba dọn và đọc lại giá trị mặc định. Main thread vẫn giữ một giá trị độc lập.

Tạo tệp `ThreadLocalDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadLocalDemo {
    private static final ThreadLocal<String> CONTEXT =
            ThreadLocal.withInitial(() -> "NONE");

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            System.out.println("main-initial=" + CONTEXT.get());
            CONTEXT.set("MAIN");
            System.out.println("main-set=" + CONTEXT.get());

            Future<String> first = executor.submit(() -> {
                CONTEXT.set("REQUEST-A");
                // Cố ý không remove để mô phỏng một lỗi.
                return CONTEXT.get();
            });
            System.out.println("worker-first=" + first.get());

            Future<String> leaked = executor.submit(CONTEXT::get);
            System.out.println("worker-leaked=" + leaked.get());

            Future<String> cleaned = executor.submit(() -> {
                CONTEXT.remove();
                return CONTEXT.get();
            });
            System.out.println("worker-after-remove=" + cleaned.get());
            System.out.println("main-still=" + CONTEXT.get());
        } finally {
            CONTEXT.remove();
            executor.shutdown();
        }

        System.out.println("main-after-remove=" + CONTEXT.get());
        CONTEXT.remove();
    }
}
```

Biên dịch và chạy:

```bash
javac ThreadLocalDemo.java
java ThreadLocalDemo
```

Kết quả chuẩn:

```text
main-initial=NONE
main-set=MAIN
worker-first=REQUEST-A
worker-leaked=REQUEST-A
worker-after-remove=NONE
main-still=MAIN
main-after-remove=NONE
```

Giải thích theo thứ tự:

1. Main gọi `get()` khi chưa có dữ liệu, nhận `NONE`; sau `set("MAIN")` thì đọc thấy `MAIN`.
2. Worker thứ nhất trong executor nhận `REQUEST-A`. `Future.get()` chờ hoàn thành trước khi tác vụ kế tiếp được submit.
3. `newSingleThreadExecutor()` tái sử dụng cùng worker bình thường, nên tác vụ thứ hai đọc thấy `REQUEST-A` mặc dù nó là công việc mới. Đây là **rò rỉ ngữ cảnh giữa các tác vụ**, không phải trường hợp ThreadLocal tự chia sẻ giá trị giữa hai thread.
4. Tác vụ thứ ba gọi `remove()` rồi `get()`: supplier chạy lại và trả `NONE`.
5. Main vẫn giữ `MAIN`, vì thao tác trên worker không đụng tới slot main. Cuối chương trình main cũng `remove()`, nên lần `get()` kế tiếp lại nhận `NONE`.

Không dùng cách để sót dữ liệu ở task thứ nhất trong mã sản xuất; ví dụ chỉ dùng để **tái hiện và quan sát lỗi**.

## 4. Mẫu đúng: đặt ngữ cảnh, xử lý, dọn trong finally

Khi có một phạm vi đồng bộ rõ ràng, dùng `try/finally`:

```java
private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

static void runWithRequestId(String id) {
    REQUEST_ID.set(id);
    try {
        process();
    } finally {
        REQUEST_ID.remove();
    }
}

static void process() {
    System.out.println("id=" + REQUEST_ID.get());
}
```

Đây là **đoạn mã minh họa phải đặt trong một class**, không phải tệp chương trình có `main` độc lập. Khi `process()` ném exception hoặc có `return`, `finally` vẫn thực hiện dọn slot. Nơi tạo và **sở hữu phạm vi ngữ cảnh** nên chịu trách nhiệm dọn sau khi tất cả code cần ngữ cảnh đã xử lý xong.

Cần thiết kế đặc biệt nếu có **ngữ cảnh lồng nhau**. Ví dụ hàm A `set("outer")`, sau đó hàm B `set("inner")` trên cùng thread và gọi `remove()`; khi trở về A, `"outer"` **không tự phục hồi**. Nếu cần hỗ trợ nesting, hãy lưu giá trị cũ và khôi phục đúng hợp đồng, hoặc truyền context bằng tham số. Không dùng `remove()` ở mọi cấp rồi giả định nó hoạt động như stack.

Một lưu ý tương tự: `remove()` chỉ xóa tham chiếu trong ThreadLocal; nếu giá trị là connection, file, buffer hoặc đối tượng `AutoCloseable`, nó **không tự đóng tài nguyên**. Tài nguyên có vòng đời riêng, phải được đóng đúng nơi sở hữu.

## 5. ThreadLocal theo thread, không theo request hay task

Thread pool thường dùng lại cùng platform thread cho nhiều `Runnable`/`Callable`. Nếu `set()` mà quên `remove()`, dữ liệu cũ có thể bị đọc bởi tác vụ kế tiếp:

```text
Worker-1:
  Task A: set("Alice") -> dùng -> quên remove
  Task B: get() -> "Alice" [sai]
  Task B: remove()
  Task C: get() -> giá trị khởi tạo
```

Hậu quả có thể là correlation ID trong log sai, hành vi dựa vào danh tính/ngữ cảnh cũ, lộ dữ liệu qua công việc khác, hoặc giữ tham chiếu tới đối tượng lớn lâu hơn cần thiết. Vì thế, trong xử lý có executor, phải đặt và dọn ngữ cảnh **theo từng lần xử lý công việc**, kể cả khi task thất bại.

Một thread mới không mặc nhiên nhận giá trị ThreadLocal thường từ thread gửi task. Cụ thể:

```java
ThreadLocal<String> local = new ThreadLocal<>();
local.set("main");
Thread worker = new Thread(() -> {
    System.out.println(local.get()); // null trên thread mới
});
worker.start();
worker.join();
local.remove();
```

Đoạn trên **minh họa trong một phương thức có xử lý `InterruptedException`**, không phải chương trình đứng riêng. Nó in `null` vì `new ThreadLocal<>()` chưa có giá trị ở worker. Tương tự, các tác vụ chuyển qua một executor khác, `CompletableFuture` hoặc callback bất đồng bộ có thể chạy trên thread khác; **không thể giả định context tự đi theo tác vụ**.

Nếu cần truyền context, hãy ưu tiên truyền tham số rõ ràng. Trường hợp có yêu cầu thiết lập context trên worker, cần tạo wrapper đặt giá trị ngay trước phần xử lý, rồi xóa/khôi phục trong `finally`, đồng thời cân nhắc ngữ cảnh lồng nhau.

## 6. Phân biệt với InheritableThreadLocal

`InheritableThreadLocal` là một lớp con của ThreadLocal có cơ chế cho phép thread con nhận giá trị ban đầu từ thread cha **khi thread con được tạo**, theo cấu hình và ngữ nghĩa kế thừa của thread. Điều này không đồng nghĩa với chuyện mỗi task được submit sau này sẽ tự nhận đúng context của thread submit:

- Worker trong thread pool có thể đã được tạo trước khi yêu cầu hiện tại xuất hiện.
- Giá trị được kế thừa khi tạo thread không tự cập nhật theo các lần `set()` mới ở thread cha.
- Truyền cả đối tượng mutable theo tham chiếu có thể tạo thêm vấn đề chia sẻ dữ liệu.
- Với dữ liệu nhạy cảm, cần chủ động kiểm soát có cho phép thread con kế thừa hay không.

Vì thế, `InheritableThreadLocal` không phải giải pháp chung để truyền context theo từng task qua thread pool.

## 7. Bộ nhớ: tại sao remove() quan trọng?

Trên các triển khai OpenJDK thông dụng, cấu trúc nội bộ `ThreadLocalMap` dùng tham chiếu yếu đến **khóa ThreadLocal** và tham chiếu mạnh đến **giá trị**. Nếu đối tượng ThreadLocal không còn được tham chiếu mạnh, khóa có thể bị thu gom, nhưng giá trị trong entry cũ **có thể vẫn được giữ** cho đến khi cấu trúc map dọn entry hoặc thread kết thúc. Đây là một nguy cơ giữ bộ nhớ, không phải tuyên bố rằng quên `remove()` luôn tạo rò rỉ vĩnh viễn.

Nếu `ThreadLocal` là một trường `static final`, khóa còn tồn tại; song các **giá trị per-thread** của worker lâu sống cũng có thể tồn tại quá phạm vi của request/task. `remove()` trên chính thread đang giữ slot làm ngắn vòng đời tham chiếu đó. Nó không xóa slot ở thread khác, cũng không bảo đảm đối tượng bị GC ngay nếu còn tham chiếu ở nơi khác.

Một số thực hành giúp giảm rủi ro:

- Giữ dữ liệu ThreadLocal nhỏ, đúng mục đích (ví dụ chuỗi correlation ID); tránh giữ cả request object, cache lớn hoặc đồ thị đối tượng không cần thiết.
- Đặt `remove()` ở `finally` của ranh giới công việc, thay vì chờ executor shutdown.
- Đừng làm cache đối tượng mutable/đắt tiền theo thread nếu có số lượng thread lớn mà không đo bộ nhớ và lợi ích.
- Dùng profiler/heap dump khi nghi ngờ giữ bộ nhớ; chỉ quan sát tăng RAM chưa đủ để kết luận ThreadLocal là nguyên nhân.

## 8. ThreadLocal trên virtual threads

Java 21 giới thiệu virtual threads như tính năng chính thức. Virtual threads vẫn là `Thread`, nên `ThreadLocal` và ba phương thức `set()`, `get()`, `remove()` vẫn hoạt động. **Giá trị thuộc virtual thread**, không thuộc carrier platform thread; đổi carrier không làm context nhảy sang virtual thread khác.

Mô hình thường gặp là một virtual thread cho mỗi tác vụ. Vì có thể đồng thời có rất nhiều virtual threads, việc dùng ThreadLocal để **cache một đối tượng nặng cho từng thread** có thể tiêu tốn bộ nhớ lớn mà ít cơ hội tái sử dụng. Với ngữ cảnh nhỏ và vòng đời rõ ràng, nó vẫn có thể hữu ích. Luôn xem xét chi phí cấp phát, số lượng task và phương thức truyền context thích hợp với ứng dụng.

Không nên dùng ThreadLocal như cơ chế truyền context "xuyên" mọi bước bất đồng bộ: nếu code chuyển sang thread khác, vẫn cần phân tích ranh giới thực thi.

## 9. So sánh với các lựa chọn khác

| Yêu cầu | Lựa chọn thường phù hợp | Lý do |
| --- | --- | --- |
| Mang ID qua các lời gọi đồng bộ cùng thread | Tham số, hoặc ThreadLocal khi có lý do rõ | Tận dụng context của thread hiện tại; dọn sau công việc |
| Chia sẻ một bộ đếm giữa nhiều thread | `AtomicInteger`, khóa hoặc bộ đếm concurrent | ThreadLocal tạo bộ đếm riêng, không tự tổng hợp |
| Đặt dữ liệu riêng cho nhiều task dùng chung một worker | Truyền tham số hoặc đặt/dọn ThreadLocal từng task | Worker có thể tái sử dụng, nên context cũ có thể sót |
| Chuyển context từ thread submit sang task trên executor | Truyền context rõ ràng | ThreadLocal thường không tự lan truyền |
| Quản lý vòng đời connection, transaction, file | API quản lý tài nguyên chuyên biệt | ThreadLocal chỉ giữ tham chiếu; remove không thay close |
| Giữ cache lớn trong hàng triệu virtual threads | Thường tránh, cân nhắc thiết kế lại | Tạo nhiều bản sao và tăng tải bộ nhớ |

## 10. Những lỗi dễ mắc và checklist kiểm tra

1. **Quên `remove()`:** context của task trước có thể xuất hiện ở task sau trên cùng worker.
2. **Dùng `set(null)` thay `remove()`:** slot vẫn tồn tại; supplier của `withInitial` không được gọi lại.
3. **Cho rằng trường `static final` là một giá trị chung:** thực tế trường đó là **khóa chung** cho nhiều giá trị per-thread.
4. **Cho rằng ThreadLocal bảo vệ đối tượng mutable được chia sẻ:** nhiều thread vẫn có thể cùng giữ một tham chiếu và gây data race.
5. **Dựa vào context tự lan sang executor:** thread đích không mặc nhiên có slot cùng giá trị của thread nguồn.
6. **Gọi `remove()` ở cấp lồng nhau mà không khôi phục:** làm mất ngữ cảnh của người gọi bên ngoài.
7. **Chỉ thử với một task:** rất dễ bỏ qua lỗi vì cần task khác tái sử dụng đúng worker mới quan sát được.
8. **Dùng remove để thay đóng tài nguyên:** tham chiếu bị xóa nhưng tài nguyên không tự giải phóng.

Trước khi dùng ThreadLocal, hãy trả lời: **giá trị thuộc thread nào, ai đặt, ai dọn, thread có được tái sử dụng không, và có ranh giới bất đồng bộ hay ngữ cảnh lồng nhau không?**

Ghi nhớ: `set()` lưu cho **thread hiện tại**, `get()` đọc/khởi tạo cho **thread hiện tại**, `remove()` xóa ánh xạ của **thread hiện tại**. ThreadLocal thuận tiện cho một số ngữ cảnh đồng bộ, nhưng vòng đời theo **thread** đòi hỏi dọn dẹp cẩn thận khi xử lý nhiều tác vụ.

## Tài liệu tham khảo

- [Java SE 21 API: ThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html) — đặc tả `get()`, `set()`, `remove()`, `withInitial()` và `initialValue()`.
- [Java SE 21 API: InheritableThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html) — ngữ nghĩa kế thừa giá trị khi tạo thread con.
- [Oracle Java 21 Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html) — ThreadLocal với virtual threads và lưu ý về cache theo thread.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-002">Khởi tạo giá trị với ThreadLocal.withInitial()</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo giá trị với ThreadLocal.withInitial() trong Java

## 1. ThreadLocal.withInitial() giải quyết vấn đề gì?

`ThreadLocal<T>` là một cơ chế lưu **giá trị gắn với từng thread**. Mỗi thread truy cập cùng một đối tượng `ThreadLocal` nhưng nhìn thấy giá trị riêng của mình. Nó hữu ích khi một thao tác chạy chủ yếu trên một thread và cần trạng thái cục bộ như bộ đệm tạm, bộ đếm, hoặc ngữ cảnh phục vụ một tác vụ. Giá trị không tự truyền sang thread khác và `ThreadLocal` không thay thế cơ chế đồng bộ khi dữ liệu thực sự được chia sẻ.

Khi chỉ tạo:

~~~java
ThreadLocal<StringBuilder> buffer = new ThreadLocal<>();
~~~

lần gọi `buffer.get()` đầu tiên của mỗi thread trả về `null`, trừ khi thread đã `set()` giá trị. Nếu cần mỗi thread tự có một `StringBuilder` riêng khi dùng lần đầu, có thể viết:

~~~java
ThreadLocal<StringBuilder> buffer =
    ThreadLocal.withInitial(StringBuilder::new);
~~~

Bây giờ, **mỗi thread sẽ nhận một `StringBuilder` mới khi chính thread đó gọi `get()` lần đầu**, và tiếp tục lấy lại cùng đối tượng trong các lần `get()` sau cho đến khi thay bằng `set()` hoặc bỏ bằng `remove()`.

`withInitial()` có hai lợi ích: xác định giá trị ban đầu rõ ràng và tránh lặp logic kiểu “nếu `get() == null` thì tạo mới”. Tuy nhiên, giá trị ban đầu **không được tạo ngay lúc gọi `withInitial()`**.

### Mục tiêu sau bài học

- Hiểu chính xác tính lazy và per-thread của hàm khởi tạo.
- Phân biệt `get()`, `set()`, `remove()` và `set(null)`.
- Khởi tạo đúng đối tượng mutable riêng cho từng thread.
- Sử dụng trong `ExecutorService`/thread pool mà không giữ trạng thái của tác vụ trước.
- Biết giới hạn của `ThreadLocal` khi công việc chuyển thread, chạy bất đồng bộ hoặc có vòng đời dài.

Bài viết chỉ dùng Java concurrency chuẩn, độc lập với các framework.

## 2. API, kiểu dữ liệu và thời điểm gọi Supplier

Trong JDK, phương thức tĩnh có dạng:

~~~java
public static <S> ThreadLocal<S> withInitial(
    java.util.function.Supplier<? extends S> supplier)
~~~

`Supplier<? extends S>` là hàm không nhận tham số và trả về một giá trị phù hợp với `S`. Có thể dùng lambda hoặc method reference:

~~~java
ThreadLocal<Integer> sequence = ThreadLocal.withInitial(() -> 0);
ThreadLocal<StringBuilder> builder = ThreadLocal.withInitial(StringBuilder::new);
ThreadLocal<java.util.ArrayList<String>> items =
    ThreadLocal.withInitial(java.util.ArrayList::new);
~~~

Các ví dụ này **khai báo ba ThreadLocal**, chưa buộc thread hiện tại tạo giá trị nào. Lời gọi `get()` trên thread chưa có giá trị mới kích hoạt supplier tương ứng. Nếu dùng `withInitial(null)` thì lời gọi khởi tạo `ThreadLocal` bị từ chối bằng `NullPointerException` vì supplier không được phép là `null`; điều này khác với supplier hợp lệ trả về `null`.

Với một đối tượng `ThreadLocal` cụ thể, trạng thái của **mỗi thread** vận hành như sau:

| Hành động trong thread | Giá trị quan sát được | Supplier có chạy không? |
| --- | --- | --- |
| Mới tạo `ThreadLocal.withInitial(factory)` | Thread chưa có giá trị | Chưa |
| `get()` lần đầu, chưa từng `set()` | Giá trị do `factory.get()` tạo | Có, trong thread gọi `get()` |
| `get()` tiếp theo | Chính giá trị đã lưu | Không |
| `set(custom)` trước `get()` lần đầu | Lấy `custom` khi `get()` | Không |
| `set(custom)` sau `get()` | Thay giá trị hiện tại bằng `custom` | Không |
| `set(null)` | `get()` trả `null` đã lưu | Không khởi tạo lại |
| `remove()` rồi `get()` | Giá trị được tạo lại | Có, ở lần `get()` sau đó |

Hai điều dễ nhầm:

1. **Giá trị riêng theo thread, không riêng theo lần gọi phương thức.** Nếu hai request chạy nối tiếp trên cùng một thread trong pool, cả hai có thể thấy *cùng giá trị cũ* nếu không làm sạch.
2. **Supplier có thể chạy nhiều lần trên cùng một thread.** Mỗi lần `remove()` xoá mapping, lần `get()` kế tiếp có thể gọi supplier lại. Vì vậy không nên xem supplier là bộ khởi tạo “chỉ chạy một lần trong cả ứng dụng”.

Khi supplier trả về `null`, `null` vẫn là một giá trị được ghi nhận cho thread. `get()` tiếp theo không tự gọi supplier lại chỉ vì nhận `null`.

## 3. Ví dụ Java 17 chạy được: lazy initialization, set và remove

Ví dụ tạo một danh sách sự kiện cho từng thread. Một `newSingleThreadExecutor()` cho phép chạy hai tác vụ **trên cùng worker thread**, để thấy vì sao cần `remove()` ở cuối từng tác vụ. `AtomicInteger` chỉ dùng đo số lần supplier được chạy và an toàn nếu demo mở rộng sang nhiều thread.

Tạo tệp `ThreadLocalWithInitialDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadLocalWithInitialDemo {
    private static final AtomicInteger creations = new AtomicInteger();

    private static final ThreadLocal<List<String>> events =
        ThreadLocal.withInitial(() -> {
            creations.incrementAndGet();
            return new ArrayList<>();
        });

    private static String handle(String requestId) {
        List<String> localEvents = events.get();

        try {
            localEvents.add(requestId);
            return String.join(", ", localEvents);
        } finally {
            // Xoá trạng thái tác vụ khỏi thread được tái sử dụng.
            events.remove();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Trước get: " + creations.get());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> first =
                executor.submit(() -> handle("A"));
            System.out.println("Lượt 1: " + first.get());

            Future<String> second =
                executor.submit(() -> handle("B"));
            System.out.println("Lượt 2: " + second.get());
        } finally {
            executor.shutdown();
        }

        System.out.println("Số lần tạo: " + creations.get());

        AtomicInteger initCalls = new AtomicInteger();
        ThreadLocal<String> label = ThreadLocal.withInitial(() -> {
            initCalls.incrementAndGet();
            return "initial";
        });

        label.set("manual"); // set trước get: chưa gọi supplier.
        System.out.println("set trước get: " +
            label.get() + ", init=" + initCalls.get());

        label.remove(); // get tiếp theo sẽ gọi supplier.
        System.out.println("sau remove: " +
            label.get() + ", init=" + initCalls.get());

        label.set(null); // null là giá trị đang được lưu.
        System.out.println("set(null): " +
            (label.get() == null) + ", init=" + initCalls.get());

        label.remove();
    }
}
~~~

Biên dịch, chạy trên JDK 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 ThreadLocalWithInitialDemo.java
java ThreadLocalWithInitialDemo
~~~

Stdout:

~~~text
Trước get: 0
Lượt 1: A
Lượt 2: B
Số lần tạo: 2
set trước get: manual, init=0
sau remove: initial, init=1
set(null): true, init=1
~~~

**Vì sao output có tính xác định?**

- Tạo `events` không gọi supplier, nên dòng đầu có `0`.
- `newSingleThreadExecutor` chạy cả hai nhiệm vụ trên một worker, và `first.get()` hoàn tất trước khi gửi nhiệm vụ thứ hai. Mỗi nhiệm vụ gọi `get()` khi mapping vừa bị `remove()` ở nhiệm vụ trước; supplier chạy hai lần.
- Danh sách của nhiệm vụ B không giữ phần tử A do khối `finally` đã xoá mapping. Nếu bỏ `events.remove()`, dòng thứ hai sẽ thành `Lượt 2: A, B` và tổng số lần tạo chỉ là `1`.
- `label.set("manual")` tạo mapping trước khi `label.get()`, nên supplier của `label` chưa chạy. `remove()` buộc `get()` kế tiếp khởi tạo lại.
- `label.set(null)` lưu `null`; `get()` không khởi tạo thêm. `label.remove()` cuối cùng giải phóng mapping của thread chính.

Với ví dụ này, đối tượng `ArrayList` được dùng trong đúng một worker thread tại từng thời điểm. `ArrayList` không tự trở thành thread-safe nhờ đưa vào `ThreadLocal`; điều kiện an toàn là không chuyển tham chiếu danh sách đó cho thread khác để cùng chỉnh sửa.

## 4. Supplier đúng: tạo đối tượng riêng thay vì trả về đối tượng dùng chung

Mẫu khởi tạo một giá trị mutable nên **tạo mới bên trong supplier**:

~~~java
ThreadLocal<StringBuilder> correct =
    ThreadLocal.withInitial(StringBuilder::new);
~~~

Sai về mục tiêu cô lập dữ liệu:

~~~java
StringBuilder shared = new StringBuilder();
ThreadLocal<StringBuilder> incorrect =
    ThreadLocal.withInitial(() -> shared);
~~~

Trong đoạn sai, mỗi thread có một *mapping* riêng, nhưng tất cả mapping lại giữ **cùng tham chiếu `shared`**. Nhiều thread cùng sửa `shared` sẽ tranh chấp dữ liệu. `ThreadLocal` không sao chép đối tượng cho bạn.

Các kiểu giá trị có thể dùng:

- **Immutable:** Ví dụ mã nhận dạng hoặc cấu hình không thay đổi; cách khởi tạo có thể trả về cùng đối tượng nếu chia sẻ là có chủ đích.
- **Mutable riêng cho thread:** Ví dụ `StringBuilder`, `ArrayList`, parser hoặc bộ xử lý có state; supplier phải tạo instance thích hợp và không để tham chiếu thoát ra ngoài thread.
- **Đối tượng đắt tiền:** Chỉ dùng `ThreadLocal` nếu lợi ích tái sử dụng lớn hơn chi phí mỗi thread giữ một bản, và đã có chính sách dọn tài nguyên.

Chú ý **supplier là một đối tượng hàm có thể được nhiều thread cùng gọi**. Lambda có capture mutable state bên ngoài, ví dụ tăng một `int` nằm trong object dùng chung mà không đồng bộ, vẫn có data race. Trong ví dụ runnable, `AtomicInteger` bảo vệ việc đếm lần tạo; nó không phải cơ chế của `ThreadLocal`.

Tránh tạo `ThreadLocal` mới trong mỗi lần cần lấy trạng thái:

~~~java
// Sai mục đích: mỗi lời gọi tạo một key ThreadLocal khác.
static StringBuilder newUnrelatedBuffer() {
    return ThreadLocal.withInitial(StringBuilder::new).get();
}
~~~

Nếu ý định là truy cập trạng thái có vòng đời của thread, hãy dùng **cùng một key `ThreadLocal`** được giữ trong field (thường `private static final` hoặc instance field có lifecycle rõ ràng). Các thành phần không liên quan không nên dùng chung key chỉ vì cùng kiểu giá trị.

## 5. withInitial() và override initialValue(): tương đương ở ý tưởng nào?

Trước Java 8, một cách phổ biến là subclass ẩn danh:

~~~java
ThreadLocal<StringBuilder> oldStyle = new ThreadLocal<>() {
    @Override
    protected StringBuilder initialValue() {
        return new StringBuilder();
    }
};
~~~

Với Java 8+, cách tương ứng gọn hơn:

~~~java
ThreadLocal<StringBuilder> modern =
    ThreadLocal.withInitial(StringBuilder::new);
~~~

Cả hai có cùng ý tưởng: khi `get()` gặp thread chưa có mapping, gọi logic khởi tạo rồi lưu giá trị để dùng lại trong thread đó. `withInitial` không tạo thread hay lên lịch tác vụ, cũng không đổi nguyên tắc `set`/`remove`.

Dùng `withInitial` khi factory có thể diễn tả rõ bằng `Supplier`. Override `initialValue()` vẫn hợp lệ khi cần subclass hoặc logic riêng, nhưng không cần tạo subclass chỉ để trả về một giá trị.

Nếu factory ném `RuntimeException` trong `get()` đầu tiên, exception xuất hiện tại **lời gọi `get()` đó**; giá trị cho thread chưa được thiết lập thành công. Lần `get()` sau có thể thử factory lại. `Supplier.get()` không khai báo checked exception; nếu khởi tạo có checked exception thì phải chọn cách xử lý/đóng gói rõ ràng, không thể đơn giản ném checked exception từ lambda của `Supplier`.

## 6. Thread pool: khởi tạo theo thread, không theo request

Hãy tưởng tượng một `ExecutorService` có 4 worker nhưng ứng dụng xử lý 10.000 yêu cầu. Nếu dùng:

~~~java
private static final ThreadLocal<String> CURRENT_REQUEST =
    ThreadLocal.withInitial(() -> "unassigned");
~~~

thì mỗi worker có thể khởi tạo giá trị `"unassigned"` ở lần `get()` đầu tiên, sau đó được gán request thực bằng `set()`. Khi worker kết thúc request, **thread không biến mất** mà tiếp tục nhận yêu cầu mới. Vì thế, việc có supplier mặc định **không bảo đảm** trạng thái được đặt lại ở ranh giới request.

Một mẫu xử lý tác vụ cấp Java chuẩn:

~~~java
static String processOne(String requestId) {
    CURRENT_REQUEST.set(requestId);
    try {
        return "Handling " + CURRENT_REQUEST.get();
    } finally {
        CURRENT_REQUEST.remove();
    }
}
~~~

Đây là đoạn minh hoạ được đặt cùng class với field `CURRENT_REQUEST` ở trên. Dùng `finally` để dọn cả khi công việc ném exception hoặc trả về sớm. Không đặt `remove()` chỉ ở nhánh thành công.

Trong trường hợp caller **đã có một ngữ cảnh ngoài đang hoạt động** và muốn tạm thay giá trị trong một lời gọi lồng nhau, `remove()` ngay sau lời gọi trong có thể xoá mất ngữ cảnh ngoài. Có thể lưu và phục hồi giá trị cũ:

~~~java
// Minh hoạ một scope lồng trong scope ngoài đã có CURRENT_REQUEST.
String previous = CURRENT_REQUEST.get();
CURRENT_REQUEST.set("nested-request");
try {
    // Gọi công việc lồng nhau ở đây.
} finally {
    CURRENT_REQUEST.set(previous);
}
~~~

Cách này chủ động gọi `get()` và vì vậy có thể tạo giá trị mặc định nếu thread trước đó chưa có mapping. Muốn phân biệt chính xác trạng thái “chưa hề có mapping” với “đã lưu null”, cần thiết kế scope/ownership riêng; API công khai của `ThreadLocal` không có phương thức kiểm tra sự tồn tại của mapping mà không khởi tạo. Ở **ranh giới tác vụ ngoài cùng**, chủ sở hữu vẫn phải xác định rõ thời điểm `remove()`.

## 7. ThreadLocal không tự lan qua các tác vụ bất đồng bộ

Một lưu ý cốt lõi: `ThreadLocal` đi theo **thread đang chạy**, không đi theo đối tượng request hay chuỗi lời gọi logic.

~~~java
// Minh hoạ: trong một class có field CURRENT_REQUEST.
// executor là ExecutorService đã tạo và được quản lý vòng đời.
CURRENT_REQUEST.set("req-42");

java.util.concurrent.CompletableFuture<String> result =
    java.util.concurrent.CompletableFuture.supplyAsync(
        () -> CURRENT_REQUEST.get(), executor);
~~~

Không thể mong `result` nhận `"req-42"`. Tác vụ bất đồng bộ chạy ở thread của `executor`, nơi mapping `CURRENT_REQUEST` có thể chưa có hoặc còn trạng thái khác nếu code trước đó quản lý sai. Chính sách ngữ cảnh cần được xác định khi bàn giao tác vụ.

Với dữ liệu nhỏ, cách rõ ràng nhất là **truyền tham số**:

~~~java
String requestId = CURRENT_REQUEST.get();
java.util.concurrent.CompletableFuture<String> result =
    java.util.concurrent.CompletableFuture.supplyAsync(
        () -> "Handling " + requestId, executor);
~~~

Nếu thư viện bên dưới bắt buộc đọc `ThreadLocal`, có thể capture giá trị tại thread gọi, rồi `set/remove` trong chính worker:

~~~java
String requestId = CURRENT_REQUEST.get();
java.util.concurrent.CompletableFuture<String> result =
    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
        CURRENT_REQUEST.set(requestId);
        try {
            return "Handling " + CURRENT_REQUEST.get();
        } finally {
            CURRENT_REQUEST.remove();
        }
    }, executor);
~~~

Ba đoạn trên là **minh hoạ độc lập về luồng ngữ cảnh**, giả định `executor` và `CURRENT_REQUEST` đã được khai báo và caller chịu trách nhiệm đóng executor, dọn ngữ cảnh trên thread gọi. Không dùng `ThreadLocal` như cơ chế ẩn để truyền dữ liệu giữa thread.

`InheritableThreadLocal` là API khác dành cho một số tình huống thread con kế thừa giá trị khi tạo thread; nó không làm việc chuyển ngữ cảnh qua thread pool trở nên tự động hay đáng tin cậy. Cũng không nên suy ra các continuation của `CompletableFuture` sẽ luôn chạy ở cùng thread với phần trước: các stage khác nhau có thể chạy trên các thread khác nhau tùy cách hoàn tất và executor.

## 8. Vòng đời, bộ nhớ và hiệu năng

`ThreadLocal` sử dụng cấu trúc lưu giá trị liên quan đến từng thread. Có thể hình dung:

~~~text
Thread A -> ThreadLocal key -> value A
Thread B -> ThreadLocal key -> value B
Thread C -> ThreadLocal key -> value C
~~~

Sơ đồ biểu diễn logic quan sát; chi tiết triển khai là mỗi `Thread` có vùng lưu trữ mapping theo key `ThreadLocal`. Một `ThreadLocal` được khai báo `static final` giữ key tồn tại lâu; nếu thread của pool cũng sống lâu, giá trị lớn có thể bị giữ lâu ngoài ý muốn. Với key không còn tham chiếu mạnh, việc key được quản lý bằng tham chiếu yếu trong `ThreadLocalMap` không bảo đảm value được thu hồi tức thì: value có thể tồn tại tới khi map dọn entry cũ hoặc thread kết thúc. Đừng dựa vào GC thay cho việc `remove()` đúng thời điểm.

Quy tắc thực tế:

- Đừng giữ cả object graph lớn, request body, session hoặc tài nguyên cần đóng trong `ThreadLocal` lâu hơn yêu cầu.
- `remove()` xoá mapping; **nó không tự gọi `close()`** trên value. Nếu value sở hữu file, socket hoặc tài nguyên đóng được, hãy đóng trong `finally` theo lifecycle trước khi bỏ mapping.
- `withInitial(StringBuilder::new)` có thể giúp tái sử dụng bộ đệm trên worker, nhưng nội dung cũ vẫn còn nếu không `setLength(0)` hoặc `remove()`. Giữ bộ đệm quá lớn có thể gây giữ bộ nhớ lâu.
- Mỗi thread cần value riêng có thể làm tăng chi phí theo số thread. Không dùng `ThreadLocal` cho mọi đối tượng chỉ để tránh truyền tham số.
- Khi sử dụng virtual threads trên Java 21+, `ThreadLocal` vẫn có thể hoạt động, nhưng mẫu “giữ cache cho mỗi thread” thường không phù hợp với số lượng virtual thread rất lớn. Cân nhắc thiết kế truyền dữ liệu và vòng đời tác vụ thay vì mặc định giữ state per-thread.

Với giá trị immutable hoặc rẻ và thread-safe, truyền trực tiếp qua tham số thường đơn giản hơn. `ThreadLocal.withInitial` có giá trị khi việc gắn trạng thái vào *thread hiện tại* là quyết định có chủ đích.

## 9. Các lỗi phổ biến và cách kiểm tra

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| `get()` trả `null` dù dùng `withInitial` | Supplier thật sự trả `null` hoặc đã `set(null)` | Xác định contract: giá trị `null` có hợp lệ không; không coi `null` luôn là “chưa khởi tạo” |
| Hai request thấy cùng dữ liệu | Pool tái sử dụng thread; không xoá mapping giữa request | `set` theo đầu tác vụ, `remove` trong `finally` |
| Hai thread cùng sửa một đối tượng | Supplier trả về tham chiếu mutable dùng chung | Tạo instance mới bên trong supplier |
| Supplier không chạy ở lần `get()` đầu như mong đợi | Thread đã `set()` trước `get()` | Kiểm tra trình tự `set/get/remove` |
| Supplier chạy lại | Thread gọi `remove()` hoặc lần `get()` trước khởi tạo lỗi | Hiểu khởi tạo xảy ra theo mapping hiện tại |
| Context biến mất sau `supplyAsync` | Công việc chuyển sang thread khác | Truyền tham số hoặc capture/set/cleanup ở worker |
| Bộ nhớ tăng trên pool dài hạn | Value lớn sống cùng worker hoặc entry cũ chưa được dọn | Giới hạn value, thiết kế lifecycle, `remove()` |
| Dữ liệu bị mất khi lồng scope | Scope bên trong gọi `remove()` làm mất trạng thái scope ngoài | Lưu/phục hồi trạng thái ngoài hoặc thiết kế ownership theo scope |

Tự kiểm chứng bằng cách sửa ví dụ mục 3:

1. Bỏ `events.remove()`, quan sát lượt 2 thấy cả `A` và `B`. Đây là minh chứng trực tiếp cho **state leak** giữa hai tác vụ trên một worker.
2. Khôi phục `remove()`, thử gọi `events.get()` hai lần trong cùng tác vụ và so sánh bằng `==`: cả hai tham chiếu tới cùng danh sách trước khi `remove()`.
3. Đổi supplier thành `() -> null`: `get()` liên tiếp đều trả `null` nhưng mapping không tự khởi tạo lại.
4. Tạo hai thread riêng, mỗi thread lấy `events.get()`. Các thread nhận danh sách khác nhau nếu supplier tạo `new ArrayList<>()` và không chia sẻ tham chiếu ra ngoài.
5. Cho công việc ném exception bên trong `try` của `handle()`; `finally` vẫn chạy và dọn giá trị. Trong môi trường thật, cũng cần kiểm tra nhánh bị huỷ/ngắt theo contract của tác vụ.

## 10. Khi nào nên dùng và điều cần ghi nhớ

Chọn `ThreadLocal.withInitial(factory)` khi một giá trị **cần thuộc về từng thread**, có cách khởi tạo rõ ràng và có vòng đời dọn dẹp xác định. `withInitial` chạy **lười (lazy)** tại `get()` đầu tiên chưa có mapping; `set()` ghi đè, `set(null)` là một giá trị hợp lệ, còn `remove()` xóa mapping để lần `get()` sau khởi tạo lại.

Điều quan trọng nhất trong Java concurrency là nhận ra hai ranh giới khác nhau: **thread** và **tác vụ logic**. Trên thread pool, nhiều tác vụ dùng cùng một thread; với API bất đồng bộ, một tác vụ có thể đi qua nhiều thread. Vì vậy hãy dùng `try/finally` và `remove()` khi sở hữu ngữ cảnh cấp tác vụ, truyền dữ liệu tường minh khi chuyển thread, và không giả định supplier tạo dữ liệu mới cho mỗi request.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-003">InheritableThreadLocal và các giới hạn kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# InheritableThreadLocal và các giới hạn kế thừa trong Java Concurrency

## 1. Vì sao cần hiểu cơ chế này?

`ThreadLocal<T>` gắn một giá trị riêng với **thread hiện tại**. Hai thread dùng chung cùng một biến `ThreadLocal` nhưng mỗi thread có ánh xạ giá trị của riêng nó. Cơ chế này đôi khi được dùng để giữ request ID, correlation ID, tùy chọn ngôn ngữ hoặc một ngữ cảnh xử lý mà nhiều hàm trên cùng thread cần truy cập.

Vấn đề xuất hiện khi một công việc tạo **thread con**: `ThreadLocal` thông thường không tự truyền giá trị của thread tạo sang thread mới. `InheritableThreadLocal<T>` cung cấp cơ chế nhận **giá trị ban đầu** của thread tạo khi thread mới được khởi tạo.

Điều quan trọng nhất: **kế thừa ở đây là thao tác khởi tạo theo thời điểm tạo thread, không phải một luồng đồng bộ hóa context liên tục, không bảo đảm theo từng task và không tự xử lý dữ liệu mutable.** Nếu dùng sai trong thread pool, ứng dụng có thể đọc context cũ hoặc làm lộ dữ liệu giữa các request.

Bài học tập trung vào API và hành vi của **Java concurrency thuần**, gồm platform threads, executor và virtual threads.

### Mục tiêu

Sau bài này, bạn có thể:

- Phân biệt `ThreadLocal` và `InheritableThreadLocal` trên cùng một ví dụ.
- Giải thích chính xác **khi nào**, **từ thread nào**, **giá trị nào** được truyền sang thread mới.
- Dùng `childValue(T parentValue)` để tạo bản sao hoặc biến đổi giá trị ban đầu.
- Nhận ra giới hạn với mutable object, thread pool, executor, luồng tạo gián tiếp và virtual threads.
- Lựa chọn truyền tham số, bọc task có cleanup hoặc tắt kế thừa khi không cần thiết.

## 2. ThreadLocal so với InheritableThreadLocal

Hai lớp đều có `get()`, `set(value)` và `remove()`, nhưng khác cách khởi tạo dữ liệu trong thread con.

| Tiêu chí | `ThreadLocal<T>` | `InheritableThreadLocal<T>` |
|---|---|---|
| Giá trị của thread hiện tại | `get()`/`set()` trên từng thread | Tương tự |
| Khi tạo thread con | Không tự nhận giá trị cha | Mặc định nhận giá trị ban đầu từ cha |
| Khi cha gọi `set()` sau thời điểm tạo con | Không truyền sang con | **Không cập nhật lại** bản sao ánh xạ ở con |
| Khi con gọi `set()` | Chỉ đổi ánh xạ ở con | Chỉ đổi ánh xạ ở con |
| Khi giá trị là object mutable | Mỗi thread giữ tham chiếu riêng nếu được set riêng | Mặc định cha và con có thể giữ **cùng tham chiếu object** |
| Thread pool tái sử dụng worker | Không tự theo context của task | Cũng **không tự theo context của task** |
| Cleanup | `remove()` khi kết thúc phạm vi dùng | `remove()` khi kết thúc phạm vi dùng |

Đừng hiểu “giá trị riêng của thread” là “object luôn được copy”. Ánh xạ `ThreadLocal → value` nằm riêng ở mỗi thread; hai ánh xạ vẫn có thể trỏ tới **chính cùng một object**.

### Ví dụ chạy được: kế thừa tại thời điểm tạo thread

**Tệp `InheritanceTiming.java` (Java 21+):**

```java
public class InheritanceTiming {
    private static final ThreadLocal<String> ORDINARY =
            new ThreadLocal<>();

    private static final InheritableThreadLocal<String> INHERITED =
            new InheritableThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        ORDINARY.set("ordinary-parent");
        INHERITED.set("A");

        try {
            Thread child = Thread.ofPlatform().unstarted(() -> {
                try {
                    System.out.println("child ordinary=" + ORDINARY.get());
                    System.out.println("child inherited=" + INHERITED.get());
                    INHERITED.set("CHILD");
                } finally {
                    ORDINARY.remove();
                    INHERITED.remove();
                }
            });

            // Thread con đã được TẠO; chưa được START.
            INHERITED.set("B");
            child.start();
            child.join();

            System.out.println("parent inherited=" + INHERITED.get());
        } finally {
            ORDINARY.remove();
            INHERITED.remove();
        }
    }
}
```

Biên dịch, chạy:

```bash
javac InheritanceTiming.java
java InheritanceTiming
```

Stdout chính xác:

```text
child ordinary=null
child inherited=A
parent inherited=B
```

Giải thích:

1. Cha `set("A")` vào `INHERITED`.
2. `Thread.ofPlatform().unstarted(...)` **tạo đối tượng thread mới**; lúc đó thread con nhận giá trị ban đầu `"A"`.
3. Cha đổi thành `"B"` **sau khi tạo nhưng trước khi start**. Việc này chỉ đổi ánh xạ ở cha.
4. Con bắt đầu và đọc `INHERITED.get() == "A"`; `ORDINARY.get()` là `null` vì không có giá trị khởi tạo.
5. Con `set("CHILD")` không đổi giá trị của cha. Sau `join()`, cha vẫn đọc `"B"`.

`join()` bảo đảm main chờ con in xong, vì vậy thứ tự stdout này xác định. Nếu dùng `new Thread(runnable)`, thời điểm kế thừa cũng là **tạo thread**, không phải gọi `start()`.

## 3. Cơ chế childValue: sao chép ánh xạ và biến đổi giá trị

`InheritableThreadLocal` ghi đè cách tạo dữ liệu ban đầu cho child thread qua:

```java
protected T childValue(T parentValue)
```

Theo API Java:

- Runtime lấy giá trị tương ứng từ inheritable-thread-local map của **thread đang tạo child thread**.
- `childValue(parentValue)` được gọi **trong thread tạo**, **trước khi child thread bắt đầu chạy**.
- Mặc định implementation trả về **chính `parentValue`**.
- Có thể override để tạo bản sao, biến đổi, lọc bớt hoặc ngăn một phần dữ liệu đi sang child.
- `childValue` không phải hook chạy mỗi khi `get()` ở con, và cũng không chạy sau mỗi `submit` task.

Bởi vậy, thao tác kế thừa là **snapshot các ánh xạ tại thời điểm tạo**. Snapshot này không đồng nghĩa **deep copy mọi object**. Chi phí sao chép bản đồ các mục kế thừa tăng theo số mục hiện có trên thread cha.

### Ví dụ: mutable reference mặc định và bản sao do childValue

**Tệp `MutableInheritance.java` (Java 21+):**

```java
import java.util.ArrayList;
import java.util.List;

public class MutableInheritance {
    private static final InheritableThreadLocal<List<String>> SHARED =
            new InheritableThreadLocal<>();

    private static final InheritableThreadLocal<List<String>> COPIED =
            new InheritableThreadLocal<>() {
                @Override
                protected List<String> childValue(List<String> parentValue) {
                    return parentValue == null
                            ? null
                            : new ArrayList<>(parentValue);
                }
            };

    public static void main(String[] args) throws InterruptedException {
        SHARED.set(new ArrayList<>(List.of("origin")));
        COPIED.set(new ArrayList<>(List.of("origin")));

        try {
            Thread child = Thread.ofPlatform().start(() -> {
                try {
                    SHARED.get().add("child");
                    COPIED.get().add("child");
                } finally {
                    SHARED.remove();
                    COPIED.remove();
                }
            });
            child.join();

            System.out.println("shared parent=" + SHARED.get());
            System.out.println("copied parent=" + COPIED.get());
        } finally {
            SHARED.remove();
            COPIED.remove();
        }
    }
}
```

Chạy:

```bash
javac MutableInheritance.java
java MutableInheritance
```

Stdout:

```text
shared parent=[origin, child]
copied parent=[origin]
```

`SHARED` dùng `childValue` mặc định: hai thread có ánh xạ độc lập nhưng cùng giữ **một `ArrayList`**. Con thêm phần tử nên cha cũng nhìn thấy. `COPIED` tạo `ArrayList` mới cho con, vì thế chỉnh sửa của con không đổi danh sách của cha.

**Điểm cần lưu ý:**

- `new ArrayList<>(parentValue)` là **shallow copy**: sao chép container và các tham chiếu phần tử. Nếu phần tử cũng mutable, parent và child vẫn có thể chia sẻ các object con.
- Nếu nhiều thread cùng sửa object chung mà không đồng bộ hóa, có thể xảy ra data race, mất cập nhật hoặc trạng thái hỏng. `InheritableThreadLocal` **không cung cấp synchronization**.
- Với context là immutable value (ví dụ `String`, record gồm trường immutable, identifier), chia sẻ cùng object thường đơn giản và ít rủi ro hơn.
- Có thể đưa logic kiểm tra hoặc đóng gói dữ liệu vào `childValue`, nhưng không nên thực hiện I/O chậm, side effect phức tạp hoặc thao tác dựa trên child đã start: phương thức chạy trong **thread tạo child**.

## 4. Giới hạn lớn nhất: thread pool không tạo thread mới cho mỗi task

Một request/task có thể được gọi `submit()` từ thread đang giữ context, nhưng **task không tự động trở thành “thread con” của thread submit**. Executor chạy task trên worker do nó quản lý. Với fixed pool, worker thường được tạo một lần rồi chạy nhiều task.

Điều đó dẫn tới hai lỗi:

1. **Stale context:** worker nhận ngữ cảnh lúc nó được tạo; các lần submit sau thay đổi context trên caller nhưng worker vẫn giữ giá trị cũ.
2. **Context leakage:** một task thay giá trị trong worker mà không cleanup; task tiếp theo trên cùng worker có thể nhìn thấy giá trị của task trước.

### Ví dụ thực nghiệm với một worker cố định

**Tệp `PoolInheritanceTrap.java` (Java 21+):**

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PoolInheritanceTrap {
    private static final InheritableThreadLocal<String> CONTEXT =
            new InheritableThreadLocal<>();

    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            CONTEXT.set("request-A");
            System.out.println("first=" +
                    pool.submit(() -> { return CONTEXT.get(); }).get());

            CONTEXT.set("request-B");
            System.out.println("second=" +
                    pool.submit(() -> { return CONTEXT.get(); }).get());

            pool.submit(() -> {
                CONTEXT.set("worker-overwrite");
            }).get();

            System.out.println("third=" +
                    pool.submit(() -> { return CONTEXT.get(); }).get());
        } finally {
            CONTEXT.remove();
        }
    }
}
```

Chạy:

```bash
javac PoolInheritanceTrap.java
java PoolInheritanceTrap
```

Stdout:

```text
first=request-A
second=request-A
third=worker-overwrite
```

Vì `newSingleThreadExecutor()` tạo worker khi task đầu được thực thi, worker nhận `"request-A"` tại lúc tạo. Caller đổi thành `"request-B"` nhưng worker **không được tạo lại**. Task thứ ba đổi context bên trong worker thành `"worker-overwrite"`; task tiếp theo đọc lại đúng giá trị còn sót trong worker.

Ví dụ sử dụng duy nhất một worker và chờ từng `Future.get()` để kết quả xác định. Với pool nhiều worker, mỗi worker có thể được tạo vào thời điểm khác nhau; vì vậy cùng một logic sai có thể tạo lỗi **khó tái hiện**, phụ thuộc thời điểm và lịch chạy.

**Quy tắc quan trọng:** Không giả định `InheritableThreadLocal` sẽ tự lan truyền context của từng request qua `ExecutorService.submit()`, `execute()`, `CompletableFuture.supplyAsync()` hoặc các callback bất đồng bộ. Giá trị phụ thuộc **thread nào thực sự tạo thread thực thi** và vòng đời của worker, không phụ thuộc quan hệ gọi hàm của request.

## 5. Truyền context một cách xác định cho executor

Cách rõ nhất là **truyền context thành tham số**. Một `record` immutable biểu diễn request context khiến phụ thuộc hiện rõ trong API:

```java
record RequestContext(String traceId) {}

static String process(RequestContext context, String payload) {
    return context.traceId() + ":" + payload;
}
```

Đây là đoạn minh họa đặt trong một class; `process` nhận context trực tiếp nên không lệ thuộc thread thực thi.

Nếu phải tương tác với API hiện hữu đọc `ThreadLocal`, có thể **capture context tại thời điểm submit** rồi **bind và cleanup trong thread thực thi**. Đây là lựa chọn chủ động của ứng dụng, không phải phép màu của `InheritableThreadLocal`.

### Ví dụ hoàn chỉnh: capture, restore và cleanup

**Tệp `ExplicitContext.java` (Java 21+):**

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExplicitContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private static <T> Callable<T> withTrace(
            String traceId, Callable<T> action) {
        return () -> {
            String previous = TRACE_ID.get();
            TRACE_ID.set(traceId);
            try {
                return action.call();
            } finally {
                if (previous == null) {
                    TRACE_ID.remove();
                } else {
                    TRACE_ID.set(previous);
                }
            }
        };
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            System.out.println(pool.submit(
                    withTrace("R-1", () -> "task1=" + TRACE_ID.get())).get());

            System.out.println(pool.submit(
                    withTrace("R-2", () -> "task2=" + TRACE_ID.get())).get());

            System.out.println("after=" +
                    pool.submit(() -> { return TRACE_ID.get(); }).get());
        }
    }
}
```

Chạy:

```bash
javac ExplicitContext.java
java ExplicitContext
```

Stdout:

```text
task1=R-1
task2=R-2
after=null
```

`withTrace` là task wrapper: trước khi xử lý nó bind giá trị dành cho task; trong `finally` nó trả lại trạng thái của worker. Với ví dụ này, quy ước `null` nghĩa là chưa có giá trị. Nếu ứng dụng cho phép **giá trị null có ý nghĩa riêng**, thiết kế context và quy tắc phục hồi phải thể hiện rõ trạng thái đó thay vì suy chỉ bằng `get() == null`.

Khi cần truyền **nhiều thuộc tính**, nên gói chúng vào một context immutable thay vì duy trì nhiều `ThreadLocal` riêng lẻ khó snapshot, khó phục hồi. Nếu có yêu cầu cách ly hoặc quyền truy cập dữ liệu, phải xác định chính xác task nào được phép nhận context.

### Đừng bỏ qua trường hợp lỗi

Mẫu wrapper phải dùng `finally` vì `action.call()` có thể ném exception. Với `Callable<T>`, exception tiếp tục được đưa về `Future.get()` qua `ExecutionException`; cleanup vẫn chạy. Khi sao chép context mutable, tạo bản sao phù hợp **trước** khi task dùng để tránh chia sẻ object giữa các task.

Nếu một thread pool xử lý các job từ nhiều user, dữ liệu nhận dạng, token hoặc thông tin quyền truy cập không nên được kế thừa tự động sang job không liên quan. Context truyền theo ranh giới nhiệm vụ phải được **chọn lọc rõ ràng**.

## 6. Virtual threads: vẫn có kế thừa, nhưng cần tiết chế

Virtual threads trở thành tính năng chính thức từ Java 21. Chúng cũng hỗ trợ `ThreadLocal` và `InheritableThreadLocal`. Với `Thread.Builder`, mặc định là **kế thừa inheritable-thread-local values** từ **thread tạo virtual thread**; có thể vô hiệu hóa bằng `inheritInheritableThreadLocals(false)`.

**Tệp `VirtualInheritance.java` (Java 21+):**

```java
public class VirtualInheritance {
    private static final InheritableThreadLocal<String> REQUEST =
            new InheritableThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        REQUEST.set("parent-context");

        try {
            Thread inherited = Thread.ofVirtual().start(() -> {
                try {
                    System.out.println("default=" + REQUEST.get());
                } finally {
                    REQUEST.remove();
                }
            });
            inherited.join();

            Thread isolated = Thread.ofVirtual()
                    .inheritInheritableThreadLocals(false)
                    .start(() -> {
                        try {
                            System.out.println("isolated=" + REQUEST.get());
                        } finally {
                            REQUEST.remove();
                        }
                    });
            isolated.join();
        } finally {
            REQUEST.remove();
        }
    }
}
```

Chạy:

```bash
javac VirtualInheritance.java
java VirtualInheritance
```

Stdout:

```text
default=parent-context
isolated=null
```

Hai thao tác `join()` làm thứ tự output xác định. `isolated` không kế thừa dữ liệu vì builder đã tắt cơ chế này.

### Vì sao cần cân nhắc với virtual threads?

Virtual threads có thể rất nhiều (hàng chục nghìn hoặc hơn, tùy workload), trong khi mỗi virtual thread vẫn có thể giữ các entry thread-local và object được chúng tham chiếu. Nếu context lớn, mỗi child sao chép một map nhiều mục hoặc `childValue` tạo các bản sao mutable lớn, **chi phí bộ nhớ và tạo thread sẽ tăng đáng kể**.

Không dùng `ThreadLocal` như cache tài nguyên lớn “cho mỗi thread” khi số virtual thread có thể rất lớn. Nếu không cần truyền ngữ cảnh khi tạo thread, tắt inheritance có chủ đích. Nếu cần, truyền context nhỏ, immutable và giới hạn vòng đời.

Lưu ý thêm: thread mới kế thừa từ **thread thực sự khởi tạo nó**, không mặc định từ “request gốc” theo cách suy luận của người lập trình. Nếu thread khác chịu trách nhiệm tạo virtual thread, nguồn kế thừa chính là thread tạo đó.

## 7. Giới hạn vòng đời và các ranh giới kế thừa

### 7.1. Không phải quan hệ cha–con sống

Từ ngữ “parent/child thread” chỉ **quan hệ tại thời điểm tạo**. Nó không có nghĩa child tự động đọc những cập nhật mới của parent, không có tính năng đồng bộ giá trị hai chiều, và không có bảo đảm child kết thúc khi parent kết thúc.

Ví dụ với immutable `String`:

```text
T0: parent.set("A")
T1: tạo child       → child nhận giá trị ban đầu "A"
T2: parent.set("B") → parent="B", child="A"
T3: child.set("C")  → parent="B", child="C"
T4: parent.remove() → không tự remove ở child
```

Nếu giá trị là **cùng mutable reference**, thay đổi object được chia sẻ có thể được thread còn lại quan sát theo các quy tắc đồng bộ hóa bộ nhớ Java; tuyệt đối không coi đó là cơ chế truyền context đúng đắn.

### 7.2. Kế thừa qua nhiều thế hệ

Khi thread A tạo B, B nhận các giá trị ban đầu do A để lại (qua `childValue`). Nếu B sau đó tạo C, C nhận từ **B tại thời điểm B tạo C**, không truy ngược về A. Mỗi bước có thể áp dụng `childValue` riêng. Thread không phải được tạo bên trong logic nghiệp vụ vẫn có thể mang context nếu nó được một thread có inheritable values khởi tạo.

### 7.3. remove() không thu hồi giá trị đã kế thừa ở child

`remove()` chỉ loại ánh xạ ở **thread hiện tại**. Khi cha gọi `remove()` sau khi tạo child, giá trị đã kế thừa ở child không tự mất. Khi child sống lâu, dữ liệu có thể tiếp tục được giữ; nếu reference đến object lớn thì object chưa thể được GC chỉ vì cha đã remove.

Ứng dụng cần kiểm soát vòng đời task và cleanup tại **mỗi nơi thật sự bind hoặc giữ context**, đặc biệt với worker dùng lại.

### 7.4. initialValue và childValue khác nhau

- `initialValue()` (kế thừa từ `ThreadLocal`) cung cấp giá trị khởi đầu khi `get()` trên thread hiện tại chưa có entry.
- `childValue(parentValue)` cung cấp **giá trị ban đầu được kế thừa** lúc một child thread được tạo trong trường hợp thread tạo đang có entry tương ứng.

Nếu thread không nhận entry kế thừa, lần `get()` đầu có thể gọi `initialValue()`. Vì vậy, khi thử bật/tắt inheritance, hãy tính đến override `initialValue` hoặc `withInitial`: không nên suy luận rằng `get() == null` là cách nhận biết duy nhất cho “không kế thừa”.

## 8. Khi nào nên dùng, khi nào tránh?

**Có thể phù hợp** khi bạn thực sự tạo một số thread con có quan hệ tác vụ rõ ràng, cần chuyển **giá trị khởi tạo immutable**, không sử dụng worker reuse khó kiểm soát, và đã chủ động quản lý vòng đời.

**Nên tránh dùng làm mặc định** khi:

- Chạy trên fixed/cached thread pool hay chuỗi callback không kiểm soát thread nào thực thi.
- Context chứa object mutable phức tạp, buffer lớn, connection, transaction, tài nguyên phải đóng hoặc reference tới đồ thị object lớn.
- Hệ thống có nhiều request độc lập và có nguy cơ rò rỉ context hoặc nhầm danh tính giữa các task.
- Tạo số lượng rất lớn virtual threads mà inheritance không thực sự cần thiết.
- Muốn “tự động” bảo đảm mọi hàm async nhận đúng dữ liệu của caller: API này không đưa ra hợp đồng như vậy.

Thứ tự lựa chọn thực dụng:

1. **Truyền context thành tham số** hoặc đặt trong object biểu diễn request/job khi có thể.
2. Nếu phải dùng executor với API phụ thuộc thread-local, **capture khi submit + bind khi chạy + restore trong finally**.
3. Dùng `InheritableThreadLocal` khi chính hành vi **khởi tạo child thread** là ranh giới mong muốn và dữ liệu được kiểm soát.
4. Với Java 25+, cân nhắc **`ScopedValue`** cho ngữ cảnh đọc chủ yếu theo phạm vi gọi hàm; lưu ý semantics và yêu cầu phiên bản khác với `InheritableThreadLocal`.

`ScopedValue` là API chính thức trong Java 25 (JEP 506), cung cấp binding theo phạm vi để truyền dữ liệu một chiều qua call stack. Nó không phải cách “bật kế thừa cho mọi thread bất kỳ” và không nên lẫn với `InheritableThreadLocal`. Nếu chương trình phải tương thích Java 21, không thể sử dụng API Java 25 này mà không thay đổi runtime/source baseline.

## 9. Những hiểu nhầm thường gặp

| Nhận định sai | Thực tế |
|---|---|
| “Child đọc giá trị của parent tại lúc `start()`.” | Đọc **bản khởi tạo từ lúc child được tạo**. |
| “`InheritableThreadLocal` làm đồng bộ context cha và con.” | Không có đồng bộ giá trị liên tục hoặc hai chiều. |
| “Con gọi `set()` sẽ cập nhật parent.” | Không; mỗi thread có ánh xạ riêng. |
| “Hai thread luôn giữ hai object độc lập.” | Mặc định `childValue` trả cùng tham chiếu; mutable object có thể bị chia sẻ. |
| “Gửi request mới vào executor sẽ cập nhật worker.” | Không; worker tái sử dụng context cũ nếu không bind/cleanup. |
| “`remove()` ở parent xóa cả child.” | Chỉ xóa tại thread gọi. |
| “Virtual thread không hỗ trợ ThreadLocal.” | Có hỗ trợ; nhưng chi phí và vòng đời phải được cân nhắc. |
| “Muốn tắt inheritance phải dùng API ngoài JDK.” | Java 21 `Thread.Builder.inheritInheritableThreadLocals(false)` hỗ trợ trực tiếp. |
| “Có context nên khỏi cần truyền thông tin phân quyền.” | Context ngầm dễ bị kế thừa nhầm; quyền truy cập phải được xác nhận ở đúng ranh giới nghiệp vụ. |

## 10. Checklist kiểm tra khi review code concurrency

- [ ] Xác định rõ thread **tạo** thread mới, không chỉ thread **gửi task**.
- [ ] Nếu là executor, xác định worker được tạo lúc nào và có tái sử dụng không.
- [ ] Phân biệt `ThreadLocal` thường và `InheritableThreadLocal`; không giả định cùng hành vi.
- [ ] Chỉ kế thừa dữ liệu thực sự cần; ưu tiên immutable hoặc `childValue` sao chép đúng độ sâu cần thiết.
- [ ] Mọi bind có phạm vi rõ và cleanup `finally` tại nơi sử dụng.
- [ ] Không lưu mật khẩu, token, đối tượng request lớn hoặc connection lâu dài trong context ngầm.
- [ ] Có kiểm thử nhiều task liên tiếp trên **cùng một worker** để phát hiện stale context và leakage.
- [ ] Với virtual threads, cân nhắc `inheritInheritableThreadLocals(false)` khi không cần.
- [ ] Xác nhận version JDK nếu dùng `Thread.ofVirtual()` (Java 21+) hoặc `ScopedValue` (Java 25+).

## 11. Tự luyện

**Câu 1.** Parent `set("A")` rồi tạo child bằng `unstarted(...)`, sau đó `set("B")` và mới gọi `start()`. Nếu dùng `InheritableThreadLocal<String>`, child nhận gì? Vì sao?

**Câu 2.** Một `newSingleThreadExecutor()` xử lý task của user A rồi task của user B. Nếu worker kế thừa user A lúc tạo, tại sao task B có thể thấy user A dù caller đã thay context?

**Câu 3.** Vì sao `childValue(parentValue)` trả `new ArrayList<>(parentValue)` có thể vẫn chưa đủ khi danh sách chứa các mutable object?

**Câu 4.** `remove()` ở parent có bảo đảm dữ liệu đã kế thừa không còn tồn tại ở child đang chạy không?

**Câu 5.** Khi cần truyền correlation ID qua executor dùng lại worker, cách nào có lifecycle dễ kiểm soát hơn: dựa vào kế thừa lúc tạo worker, hay capture–bind–finally–restore trong mỗi task?

**Gợi ý đáp án:** (1) `"A"`, vì kế thừa lúc **tạo**; (2) worker được tái sử dụng và không tự cập nhật theo task; (3) shallow copy vẫn chia sẻ phần tử; (4) không; (5) capture–bind–restore theo mỗi task hoặc truyền tham số.

## Tài liệu Java chính thức

- [InheritableThreadLocal (Java SE 21 API)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html): ý nghĩa kế thừa và `childValue`.
- [Thread.Builder (Java SE 21 API)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.Builder.html): tạo thread, thời điểm kế thừa và `inheritInheritableThreadLocals(boolean)`.
- [Executors (Java SE 21 API)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html): executor và worker management.
- [JEP 444 – Virtual Threads](https://openjdk.org/jeps/444): virtual threads và lưu ý về thread-local data.
- [JEP 506 – Scoped Values](https://openjdk.org/jeps/506): API ngữ cảnh theo phạm vi hoàn thiện trong Java 25.

> **Nguyên tắc cốt lõi:** `InheritableThreadLocal` chỉ chuyển **giá trị ban đầu khi tạo thread con**. Khi code đi qua executor, thread pool hoặc context mutable, bạn phải quản lý việc truyền và dọn dẹp dữ liệu theo đúng **vòng đời task**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-004">Rò rỉ context khi tái sử dụng thread pool</a>

<details>
<summary>Xem chi tiết</summary>

# Rò rỉ context khi tái sử dụng thread pool

Trong ứng dụng Java chạy đồng thời, **context của một tác vụ** có thể gồm mã người dùng, tenant, correlation ID, trace ID, locale hoặc trạng thái phục vụ một lần xử lý. Nếu ứng dụng lưu những thông tin này vào `ThreadLocal` nhưng không xóa sau tác vụ, một thread trong pool có thể mang context của **tác vụ trước** sang tác vụ tiếp theo. Đây là lỗi *rò rỉ context* (context leakage), thường dẫn đến log sai, hành vi sai hoặc thậm chí lộ dữ liệu giữa các yêu cầu.

Bài này tập trung vào **Java concurrency thuần**: `ThreadLocal`, `InheritableThreadLocal`, `ExecutorService`, `Future` và cách thiết kế điểm bắt đầu/kết thúc context. Không cần framework để hiểu, tái hiện hay sửa lỗi.

## 1. Context gắn với tác vụ khác với context gắn với thread

*Context của tác vụ* là dữ liệu chỉ có ý nghĩa trong phạm vi một công việc cụ thể, ví dụ:

- `userId` / `tenantId`: ai hoặc đơn vị nào đang được xử lý.
- `requestId` / `traceId`: liên kết log của một chuỗi công việc.
- `locale`: ngôn ngữ yêu cầu.
- Một cờ, cấu hình hoặc metadata tạm thời có vòng đời bằng một tác vụ.

`ThreadLocal<T>` lại lưu **một giá trị cho mỗi thread**, không lưu một giá trị cho mỗi tác vụ. Hai tác vụ chạy trên cùng worker sẽ đọc **cùng slot ThreadLocal của worker đó** nếu tác vụ trước đã `set()` và chưa `remove()`.

Tính chất quan trọng:

- `ThreadLocal.set(value)` ghi giá trị trong **thread đang gọi**.
- `ThreadLocal.get()` đọc giá trị thuộc **thread đang gọi**, không tự biết request/task.
- `ThreadLocal.remove()` xóa giá trị của ThreadLocal đó trên **thread đang gọi**.
- Một ThreadLocal instance có thể được chia sẻ bằng field `static final`; giá trị trong các thread khác nhau vẫn độc lập.
- `ThreadLocal` **không tự truyền context** từ thread gọi `submit()` sang worker của `ExecutorService`.

Điểm dễ sai là giả định “mỗi tác vụ có một thread riêng”. Với thread pool, worker tồn tại lâu hơn task và thường được tái sử dụng.

```text
Thread gọi submit:           submit(A)         submit(B)
                                 |                 |
ExecutorService queue:           A ----->          B
                                 |                 |
Worker-1:                    set user=alice     get user?
                              xử lý A xong        |
                              KHÔNG remove        v
                                               "alice" cũ
```

Tác vụ B có thể không hề nhận `userId` nhưng vẫn đọc được giá trị từ A. Đó là lỗi dù hai tác vụ **không chạy cùng lúc**: chúng dùng chung worker ở hai thời điểm khác nhau.

## 2. Vì sao thread pool làm lỗi khó phát hiện?

`Executors.newFixedThreadPool(n)` giữ các worker để xử lý nhiều công việc theo thời gian; `newSingleThreadExecutor()` là tình huống đơn giản nhất để tái hiện cùng một worker. Với nhiều worker, tác vụ B chỉ thấy context cũ nếu nó được lập lịch lên worker từng chạy A (và slot đó chưa được dọn). Do đó lỗi có thể xuất hiện ngẫu nhiên theo tải và thứ tự scheduling.

Chuỗi sự kiện điển hình:

1. Tác vụ A chạy trên worker W, gọi `USER.set("alice")`.
2. A hoàn thành hoặc ném ngoại lệ nhưng không dọn `USER`.
3. W được trả về trạng thái rảnh, **thread vẫn sống**.
4. Tác vụ B chạy trên W; do một nhánh code bỏ qua `set()`, B gọi `USER.get()`.
5. B nhận `alice` dù đó là context của A.

Nếu B được chuyển sang worker khác, lỗi có thể tạm biến mất. Tăng số worker có thể đổi tần suất nhưng không giải quyết nguyên nhân. Tắt/khởi động lại pool có thể làm dấu hiệu biến mất vì worker cũ chết, nhưng không phải biện pháp sửa.

**Hậu quả cần phân biệt:**

| Loại rò rỉ | Điều thực sự xảy ra | Tác động |
|---|---|---|
| **Rò rỉ giá trị/ngữ cảnh** | Tác vụ sau đọc context của tác vụ trước | Sai log, gắn nhầm tenant, xử lý nhầm dữ liệu |
| **Rò rỉ tham chiếu/bộ nhớ** | Worker sống lâu vẫn giữ đối tượng do ThreadLocal tham chiếu | Heap tăng, đối tượng không được GC như mong đợi |
| **Truyền context thiếu** | Tác vụ khác chạy trên worker chưa được cài context | `get()` trả `null` hoặc mặc định sai |
| **Context bị thay đổi ngoài ý muốn** | Các phần code trong cùng worker chỉnh ThreadLocal mà không khôi phục | Caller sau lời gọi lồng nhau đọc giá trị sai |

Các tình huống trên cùng liên quan thread context nhưng **không đồng nghĩa** nhau. Muốn sửa đúng phải biết context đang bị giữ lại, bị thiếu hay bị ghi đè.

## 3. Ví dụ Java: tái hiện rò rỉ context một cách xác định

Ví dụ dùng executor một worker để ép hai tác vụ lần lượt chạy trên cùng platform thread. Lưu tên tệp **`ContextLeakDemo.java`**:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContextLeakDemo {
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> {
                USER.set("alice");
                System.out.println("A user=" + USER.get());
                // BUG: thiếu USER.remove().
            }).get();

            pool.submit(() -> System.out.println("B user=" + USER.get())).get();
        } finally {
            pool.shutdown();
        }
    }
}
```

Chạy với **JDK 17+**:

```bash
javac ContextLeakDemo.java
java ContextLeakDemo
```

Kết quả:

```text
A user=alice
B user=alice
```

**Giải thích từng bước:**

- `USER` là một đối tượng ThreadLocal dùng chung, nhưng giá trị `alice` được đặt trong worker khi A chạy.
- `Future.get()` đầu tiên chờ A kết thúc trước khi B được gửi. B chắc chắn chạy sau A.
- `newSingleThreadExecutor()` dùng một worker để tuần tự xử lý hai tác vụ trong ví dụ bình thường này; không có lỗi khiến worker bị thay thế.
- B **không hề set USER**, nhưng đọc được giá trị do A để lại. `USER` trên main thread vẫn là một slot khác; điều kiện rò rỉ nằm ở worker.
- Lỗi không cần `data race`, hai threads viết đồng thời hay biến static thông thường. Nó là sai vòng đời của **trạng thái gắn với thread**.

Nếu đổi pool thành nhiều workers, đừng viết bài kiểm tra “B luôn nhận alice” vì scheduling có thể đưa B sang thread khác. Một worker là cách minh họa quyết định được thứ tự và tái sử dụng.

## 4. Sửa căn bản: đóng vòng đời context bằng `try/finally`

Quy tắc chính: **ai cài đặt context phải có trách nhiệm kết thúc phạm vi đó**. Với điểm vào của một tác vụ trên worker, cài context đầu tác vụ và `remove()` trong `finally`. `finally` vẫn chạy khi code xử lý ném ngoại lệ thông thường, nên cleanup không phụ thuộc nhánh thành công.

Lưu thành **`ContextCleanupDemo.java`**:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContextCleanupDemo {
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> {
                USER.set("alice");
                try {
                    System.out.println("A user=" + USER.get());
                } finally {
                    USER.remove();
                }
            }).get();

            pool.submit(() -> System.out.println("B user=" + USER.get())).get();
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac ContextCleanupDemo.java
java ContextCleanupDemo
```

Kết quả:

```text
A user=alice
B user=null
```

B không còn nhìn thấy Alice vì A xóa context trên chính worker mà A chạy. Trên ThreadLocal thông thường, `get()` trả `null` khi chưa có giá trị. Với `ThreadLocal.withInitial(...)`, sau `remove()`, lần `get()` tiếp theo **có thể gọi lại supplier để tạo giá trị mặc định**; vì vậy không được coi mọi `get() == null` là bằng chứng đã dọn sạch mọi loại ThreadLocal.

**Sai lầm thường gặp:**

```java
USER.set("alice");
performTask();
USER.remove();  // Bị bỏ qua khi performTask() ném exception.
```

Đoạn minh họa này không phải chương trình độc lập; `performTask()` là phương thức giả định. Dùng `try/finally`, và khi có nhiều ThreadLocals (USER, TENANT, TRACE), dọn **tất cả** những biến đã cài. `remove()` khác `set(null)`: `remove()` xóa mapping của thread hiện tại; `set(null)` vẫn là một phép gán với giá trị null và có thể ảnh hưởng hành vi `initialValue()` về sau.

**Có nên dọn đầu tác vụ?** Ở ranh giới worker không có ngữ cảnh hợp lệ cần giữ, dọn đầu tác vụ là *defense in depth* hữu ích để loại giá trị sót từ code cũ. Tuy nhiên nó không thay thế cleanup cuối tác vụ; chỉ xóa đầu mỗi job mà không dọn cuối sẽ giữ đối tượng trong khoảng worker rảnh, và những nhánh ngoài wrapper vẫn có thể bị ảnh hưởng.

## 5. Truyền context qua executor đúng cách

Dọn binding trên worker là một phần của giải pháp; **ThreadLocal của caller không tự truyền sang thread thực thi**. Khi cần ngữ cảnh ngầm trên executor, phải **capture một snapshot trước khi submit**, cài binding ở đầu task, sau đó cleanup hoặc restore trong **finally**.

~~~java
// Snippet minh họa: context đã được capture trên caller.
// CURRENT là ThreadLocal không có initializer.
Context previous = CURRENT.get();
CURRENT.set(snapshot);
try {
    work();
} finally {
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
}
~~~

Cách xử lý này đặc biệt quan trọng khi scope có thể lồng nhau: **remove** mọi lúc có thể làm mất binding bên ngoài. Nếu worker không có previous context hợp lệ, kết thúc task phải để lại worker sạch. Cần kiểm thử cả lỗi, hủy và nhiều task trên cùng worker để phát hiện rò rỉ.

Đoạn minh họa giả định **Context**, **CURRENT**, **snapshot**, **work()** thuộc ứng dụng; không phải chương trình Java độc lập. Thiết kế wrapper Runnable/Callable đầy đủ, phân biệt capture time và executor decoration được giảng ở bài **Truyền context qua Runnable, Callable và executor**.

## 6. Ngoại lệ, hủy tác vụ và tác vụ định kỳ

Rò rỉ thường xảy ra ở đường đi hiếm gặp:

- Code `return` sớm trước câu lệnh `remove()`.
- Lời gọi HTTP/database ném exception.
- `Future.get()` nhận `ExecutionException`, nhưng code task đã bỏ qua cleanup.
- Task bị interrupt khi đang blocking.
- Tác vụ hẹn giờ chạy đi chạy lại trên worker cũ.
- Pipeline tạo nhiều `Runnable`/`Callable` qua các lớp wrapper khác nhau và có lớp không dọn context.

Đặt cleanup ngay trong **`finally` của phần chạy trên worker**, không đặt ở caller sau `Future.get()`; caller và worker có hai slot ThreadLocal khác nhau. `ExecutorService.shutdown()` không tự xóa context của từng task trong suốt thời gian pool còn sống. Thread kết thúc thường cho phép các giá trị ThreadLocal được GC nếu không có reference khác, nhưng chờ thread kết thúc không thay thế cleanup.

Với tác vụ định kỳ (`ScheduledExecutorService.scheduleAtFixedRate` hoặc `scheduleWithFixedDelay`), mỗi lần callback được chạy phải bắt đầu trong context mong muốn và kết thúc bằng cleanup. Nếu một callback để lại trace ID, callback kế tiếp có thể ghi log vào trace cũ.

Với `CompletableFuture.supplyAsync` và các stage bất đồng bộ, không giả định chúng chạy trên caller thread hoặc giữ ThreadLocal của caller. Khi không truyền executor, stage async thường dùng `ForkJoinPool.commonPool()` theo hợp đồng Java trong điều kiện thích hợp; việc lựa chọn và tái sử dụng worker cũng làm context ngầm không đáng tin. Muốn truyền context cần ranh giới/cơ chế rõ ràng theo nơi stage thực thi.

## 7. `InheritableThreadLocal` không sửa được lỗi của thread pool

`InheritableThreadLocal<T>` chuyển giá trị khởi tạo từ parent thread sang child thread **lúc child thread được tạo**, không truyền giá trị theo mỗi lần `submit()`. Với pool, workers có thể được tạo sớm rồi xử lý vô số tasks về sau.

Ví dụ về mặt thời gian:

```text
Caller có context tenant=A
    → tạo worker W
    → W có thể kế thừa tenant=A

Caller chuyển context tenant=B
    → submit(B) cho worker W đã có sẵn
    → W KHÔNG tự kế thừa lại tenant=B
    → W có nguy cơ giữ tenant=A nếu chưa dọn
```

Ngoài ra, với mặc định `childValue`, child có thể nhận **cùng tham chiếu đối tượng** như parent. Nếu giá trị là object mutable, hai thread có thể thấy thay đổi qua cùng object. Dùng `InheritableThreadLocal` để “truyền request context tự động” qua executor là sai mô hình.

`ThreadLocal` thường không tự được kế thừa khi tạo thread. Còn `InheritableThreadLocal` vẫn phải có cleanup và kiểm soát thời điểm tạo thread. Đặc biệt không trộn cơ chế inheritance với bảo đảm nghiệp vụ “mỗi task nhận đúng context” nếu không xác minh rõ vòng đời worker.

## 8. Rò rỉ context và rò rỉ bộ nhớ: quan hệ nhưng khác nhau

Một `ThreadLocal` thường có thể được khai báo:

```java
private static final ThreadLocal<LargeContext> CONTEXT = new ThreadLocal<>();
```

Đây là khai báo minh họa; `LargeContext` cần được định nghĩa. Field static giữ `ThreadLocal` sống lâu. Nếu worker pool giữ `LargeContext` (ví dụ có bộ nhớ đệm lớn hoặc đồ thị object chứa request), context có thể sống lâu bằng worker nếu không `remove()`. Không có request nào sử dụng nó nữa cũng không có nghĩa GC thu hồi được.

Trong triển khai OpenJDK, `ThreadLocalMap` gắn với `Thread` dùng khóa tham chiếu yếu đến `ThreadLocal` nhưng giữ **tham chiếu mạnh đến giá trị**. Nếu code tạo nhiều ThreadLocal động rồi bỏ tham chiếu đến chính các key, một số entry với key đã bị GC có thể còn giữ value cho đến khi map thực hiện dọn entry tương ứng hoặc thread kết thúc. Không nên dựa vào GC hay cơ chế dọn nội bộ như cam kết tự động giải phóng đúng lúc. Chi tiết `ThreadLocalMap` thuộc triển khai JDK, không phải lời hứa API cho mọi JVM.

**Phân biệt rõ:**
- Một `ThreadLocal` static còn được tham chiếu và chứa value trên worker **không nhất thiết là memory leak vô hạn**; value vẫn giữ bộ nhớ ngoài ý muốn nếu không được xóa.
- Mỗi request tạo `new ThreadLocal<>()` động và quên dọn trên pool sống lâu có thể tạo áp lực bộ nhớ khó chẩn đoán hơn.
- Context leakage có thể gây lỗi bảo mật ngay cả với giá trị rất nhỏ (ví dụ chuỗi tenant ID), không cần tăng heap.

Do đó, biện pháp trực tiếp là quản lý vòng đời và giảm trạng thái ngầm; heap dump chỉ giúp tìm biểu hiện bộ nhớ, không thay thế kiểm tra logic context.

## 9. Kiểm thử lỗi và kiểm thử bản sửa

Test concurrency phải **đảm bảo tái sử dụng worker**, không dùng `Thread.sleep()` để “hy vọng” task A chạy trước task B. Một kịch bản kiểm thử quyết định được:

1. Tạo `Executors.newSingleThreadExecutor()`.
2. Submit A: cài `ThreadLocal`, chạy code cần kiểm thử.
3. `futureA.get()`: đợi A hoàn tất.
4. Submit B: không cấp context và đọc `ThreadLocal.get()`.
5. `futureB.get()`: xác nhận B thấy `null` (hoặc context mặc định hợp lệ), không thấy giá trị từ A.
6. Thử lại khi A ném exception để kiểm chứng `finally`.
7. Dừng pool trong `finally`; giữ các task được chờ rõ ràng.

Thử bổ sung khi có nhiều context fields (user/tenant/trace), khi request bị interrupt, khi có lời gọi lồng nhau, khi cùng executor chạy tasks được gửi từ hai caller khác nhau và khi context thật sự cần được propagate.

**Các assert quan trọng:** B không đọc dữ liệu của A; mọi nhánh lỗi đều dọn; thông tin truyền sang worker đúng snapshot; cleanup của worker không ảnh hưởng slot caller; thông tin phân quyền được kiểm chứng từ nguồn có thẩm quyền.

### Dấu hiệu khi vận hành

- Log của hai requests khác nhau xuất hiện cùng request ID/trace ID dù không liên quan.
- Một task không có user context lại ghi log hoặc lấy cấu hình theo user trước.
- Giá trị tenant hoặc locale “đổi” không giải thích được sau khi tăng tải.
- Số objects giữ bởi `ThreadLocalMap` của worker tăng trong heap dump.
- Lỗi chỉ xuất hiện trên một số worker và biến mất khi pool được tái tạo.

Khi điều tra, ghi nhận **thread name**, task ID và request ID bằng dữ liệu tối thiểu được phép log; không ghi credential hay dữ liệu nhạy cảm để chẩn đoán. Dùng `jcmd`, heap dump/Java Flight Recorder khi nghi vấn bộ nhớ, và thêm kiểm thử tuần tự trên cùng worker để xác định lỗi logic. Không kết luận từ thread name đơn lẻ rằng một context đã được propagate đúng.

## 10. Lưu ý về virtual threads

Từ Java 21, `Executors.newVirtualThreadPerTaskExecutor()` thường tạo **virtual thread mới cho mỗi task**, vì vậy kịch bản “hai tasks độc lập nối tiếp trên *cùng một virtual thread được pool tái sử dụng*” không xảy ra trong cách dùng đó. Tuy nhiên:

- `ThreadLocal` vẫn gắn với **virtual thread hiện tại** và vẫn cần vòng đời rõ ràng nếu task gọi code lồng nhau.
- ThreadLocal của caller không tự biến thành context của task đang chạy trên thread khác chỉ vì đó là virtual thread.
- Việc giữ dữ liệu lớn trong ThreadLocal của số lượng rất lớn virtual threads vẫn tiêu tốn bộ nhớ.
- Không chuyển sang virtual threads chỉ để che lỗi cleanup của code đang dùng pool, vì cả context lifecycle và cơ chế propagation vẫn cần được thiết kế đúng.

Bài học chính không phụ thuộc loại thread: **context dành cho một task phải được sở hữu và giải phóng theo vòng đời task**.

## 11. Checklist thiết kế để không rò rỉ context

| Câu hỏi | Quy tắc |
|---|---|
| Context thuộc request/task hay thread suốt đời? | Nếu thuộc task, không để giá trị tiếp tục sống trên worker khi task xong |
| Ai cài `ThreadLocal.set`? | Thành phần đó cần cleanup trong `finally` |
| Có task chạy trên thread khác? | Truyền context tường minh hoặc capture/install/cleanup đúng ranh giới |
| Có ngoại lệ, cancellation, timeout? | Kiểm thử đường lỗi và interruption; không bỏ qua `finally` |
| Có gọi lồng nhau tạm thay context? | Khôi phục giá trị của scope ngoài; phân biệt với cleanup ở điểm kết thúc task |
| Có nhiều `ThreadLocal`? | Cleanup tất cả slot đã thay đổi |
| Có `InheritableThreadLocal` trong pool? | Kiểm tra vì inheritance chỉ tại lúc thread được tạo |
| Giá trị context có mutable hoặc nặng? | Ưu tiên snapshot bất biến, không giữ object lớn qua vòng đời worker |
| Context được dùng để xác thực/phân quyền? | Kiểm tra quyền bằng dữ liệu có thẩm quyền; context không thay thế cơ chế authorization |
| Test có dựa vào may rủi scheduler? | Dùng một worker và `Future.get()` để chứng minh leak/cleanup xác định |

**Kết luận kỹ thuật:** `ThreadLocal` cung cấp trạng thái riêng cho *thread*, trong khi `ExecutorService` có thể dùng một *thread* chạy nhiều *task*. Nếu đồng nhất hai vòng đời, context của task A sẽ tồn tại khi task B bắt đầu. Cách khắc phục bền vững là chọn tham số tường minh khi khả thi, hoặc bao mỗi tác vụ bằng thao tác **capture → install → execute → cleanup** với `try/finally`; nơi có scope lồng nhau thì khôi phục context của scope ngoài một cách có chủ đích.

### Tự kiểm tra

1. Vì sao `newSingleThreadExecutor()` là cách hữu ích để tái hiện leak?
2. `ThreadLocal` có truyền `userId` từ thread submit sang worker không?
3. Hai requests chạy tuần tự trên cùng worker có thể bị context leakage mà không có data race không?
4. Vì sao đặt `remove()` sau lời gọi có thể ném exception vẫn chưa an toàn?
5. `InheritableThreadLocal` kế thừa lúc thread được tạo hay lúc mỗi task được submit?
6. Vì sao `remove()` khác `set(null)` khi dùng `withInitial(...)`?
7. Khi nào phải restore previous context thay vì remove?

**Đáp án định hướng:** (1) bảo đảm một worker được tái sử dụng; (2) không; (3) có; (4) exception bỏ qua cleanup; (5) lúc tạo thread; (6) remove làm lần get sau khởi tạo lại từ supplier; (7) lời gọi lồng nhau cần trở lại scope ngoài còn hợp lệ.

### Tài liệu tham khảo

- [Java SE 21 — `ThreadLocal` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html) — `get`, `set`, `remove`, `withInitial` và thời gian sống của giá trị.
- [Java SE 21 — `InheritableThreadLocal` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html) — kế thừa giá trị khi tạo child thread.
- [Java SE 21 — `Executors` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html) — tạo single/fixed thread pools và executor tương ứng.
- [Java SE 21 — `ExecutorService` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html) — quản lý nộp, chờ và kết thúc tác vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-005">Truyền context qua Runnable, Callable và executor</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền context qua Runnable, Callable và executor trong Java

## 1. Bài toán: context đi theo tác vụ hay theo thread?

Trong chương trình đồng thời, **context** là dữ liệu cần mang theo để xử lý đúng một công việc: `requestId` / `traceId`, người khởi tạo, locale, thông tin phân vùng dữ liệu (tenant), thời hạn xử lý, hoặc dữ liệu phục vụ ghi log. Đây là *dữ liệu của tác vụ*, không đương nhiên là dữ liệu của thread chạy tác vụ.

Ví dụ: thread nhận yêu cầu A có `requestId="req-A"` và gọi:

```java
executor.submit(() -> processRequest());
```

`processRequest()` thường chạy trên một thread khác. Nếu nó đọc `ThreadLocal` do thread nhận yêu cầu A thiết lập thì mặc định nó **không thấy giá trị của thread A**. Với executor dùng thread pool, worker được tái sử dụng; giá trị `ThreadLocal` còn sót của một tác vụ trước đó còn có thể khiến tác vụ mới đọc **sai context** nếu không dọn dẹp.

Ba khái niệm cần tách biệt:

| Khái niệm | Trả lời câu hỏi | Tác động |
| --- | --- | --- |
| **Context payload** | Tác vụ cần dữ liệu gì? | Xác định các trường dữ liệu, tính bất biến, quyền truy cập |
| **Context capture** | Chụp dữ liệu ở thời điểm nào? | Thường tại lúc tạo/gửi tác vụ, trước khi chuyển sang thread khác |
| **Context activation/cleanup** | Khi task thực sự chạy, cài context vào thread nào và khôi phục ra sao? | Đảm bảo code bên trong đọc đúng dữ liệu, không rò sang task sau |

**Nguyên tắc ưu tiên:** nếu có thể, truyền một đối tượng context bất biến thành **tham số tường minh** cho hàm/tác vụ. Chỉ cần cơ chế context ngầm (`ThreadLocal`, thư viện logging MDC, ...) khi phải phục vụ các API sâu trong call stack không nhận tham số.

Phạm vi bài này là concurrency chuẩn của Java: `Runnable`, `Callable`, `Executor`, `ExecutorService`, `Future` và `ThreadLocal`. Mỗi thư viện tự quản lý context riêng có thể yêu cầu adapter riêng.

## 2. Runnable, Callable và ExecutorService thay đổi điều gì?

`Runnable` có phương thức `void run()`; nó không trả kết quả và không khai báo checked exception. `Callable<V>` có phương thức `V call() throws Exception`, cho phép trả kết quả hoặc báo lỗi có kiểm soát.

| Cách gửi việc | Giá trị nhận | Quan sát hoàn tất/lỗi |
| --- | --- | --- |
| `Executor.execute(Runnable)` | `void` | Không có `Future`; lỗi phụ thuộc executor/handler |
| `ExecutorService.submit(Runnable)` | `Future<?>` | `get()` trả `null` nếu thành công; lỗi của task được báo qua `ExecutionException` |
| `ExecutorService.submit(Runnable, V result)` | `Future<V>` | `get()` trả giá trị `result` đã cấp khi task thành công |
| `ExecutorService.submit(Callable<V>)` | `Future<V>` | `get()` trả kết quả từ `call()`; lỗi được bọc trong `ExecutionException` |
| `ExecutorService.invokeAll(Collection<Callable<V>>)` | Danh sách `Future<V>` | Mỗi phần tử phải được bọc context trước khi gửi |

Các API trên **chuyển việc sang một ranh giới thực thi bất đồng bộ**. Chúng không tự sao chép một `ThreadLocal` bất kỳ của caller sang worker. `submit()` có thể xếp hàng; task thực thi muộn hơn, lúc context của caller đã bị thay đổi hoặc dọn.

Có hai bảo đảm cần phân biệt:

- Với `ExecutorService`, các hành động xảy ra trước khi gửi task *happen-before* hành động của task; hành động của task *happen-before* lúc thread khác đọc kết quả bằng `Future.get()`.
- **Happens-before** giúp xuất bản dữ liệu đúng cách theo Java Memory Model, nhưng **không có nghĩa JVM chuyển các entry trong `ThreadLocal` sang thread thực thi task**. Đây là hai cơ chế khác nhau.

## 3. Cách đơn giản và an toàn nhất: context là tham số tường minh

Giả sử thông tin mỗi request được đóng gói bằng `record` (Java 16+):

```java
record RequestContext(String requestId, String actor) {}

static String handle(RequestContext context) {
    return context.requestId() + "/" + context.actor();
}
```

Khi tạo tác vụ, đưa context trực tiếp vào lambda:

```java
RequestContext snapshot = new RequestContext("req-42", "alice");
Future<String> future = executor.submit(() -> handle(snapshot));
String answer = future.get();
```

Đây là **đoạn minh họa đặt bên trong phương thức có biến `executor`**, không phải tệp chạy độc lập. `Runnable` cũng áp dụng tương tự:

```java
RequestContext snapshot = new RequestContext("req-43", "bob");
executor.execute(() -> audit(snapshot));
```

Ưu điểm: nguồn dữ liệu hiện ngay trong chữ ký, dễ kiểm thử, không phụ thuộc thread pool, không cần dọn `ThreadLocal`. Với async chain, mỗi stage có thể nhận `snapshot` qua lambda mà không phụ thuộc nơi chạy.

**Lưu ý về “snapshot”:** một `record` chỉ làm tham chiếu tới các trường thành phần bất biến theo nghĩa không gán lại; nếu trường là `Map` / `List` hoặc object mutable, bản thân object ấy vẫn thay đổi được. Hãy copy phòng thủ những trường mutable ở ranh giới tạo task nếu cần ảnh chụp ổn định, ví dụ `Map.copyOf(attributes)`. Tránh truyền đối tượng chứa thông tin có tuổi đời ngắn mà worker giữ sau khi request kết thúc.

## 4. Khi cần context ngầm: ThreadLocal là trạng thái của thread thực thi

`ThreadLocal<T>` có một giá trị độc lập cho **mỗi thread** truy cập nó. Trong cùng một thread, các hàm sâu trong call stack có thể đọc context mà không phải truyền tham số qua mọi tầng.

```java
private static final ThreadLocal<RequestContext> CURRENT =
        new ThreadLocal<>();
```

Lỗi điển hình khi sử dụng pool:

```java
CURRENT.set(new RequestContext("req-101", "alice"));
executor.submit(() -> log(CURRENT.get()));  // worker có thể đọc null
CURRENT.remove();                           // chỉ xóa giá trị của caller
```

Dòng `CURRENT.remove()` của caller **không dọn ThreadLocal của worker**. Nếu trước đây worker từng có context khác, `get()` của worker còn có thể trả giá trị cũ.

Để truyền context qua executor có thread pool, cần **chụp context trên thread gửi**, rồi **cài nó trên thread chạy task**, và **khôi phục đúng trạng thái trước khi trả worker về pool**:

```text
Caller thread                 Worker thread
  CURRENT = context-A
          |
   snapshot = CURRENT.get()
          |
     submit(wrapper) ----->  previous = CURRENT.get()
                             CURRENT.set(snapshot)
                             try { task.run()/call(); }
                             finally {
                                 restore(previous)
                             }
          |
   Caller tự quản lý          Worker sẵn sàng nhận task khác
   vòng đời context
```

Tại thời điểm worker bắt đầu chạy, không nên gọi lại `CURRENT.get()` của worker để “chụp” context của caller: lúc đó đã đứng ở **sai thread**. Cũng không nên giữ reference tới một object mutable trong suốt thời gian chờ hàng đợi rồi gọi đó là snapshot bất biến.

## 5. Hai wrapper đúng: Runnable và Callable

Có thể đóng gói cùng một quy tắc context scope cho cả hai interface. Thiết kế dưới đây giả định `null` là không có context và `CURRENT` là `new ThreadLocal<>()` không định nghĩa giá trị khởi tạo:

**Wrapper cho `Runnable`:**

```java
static Runnable withContext(Runnable task, RequestContext snapshot) {
    return () -> {
        RequestContext previous = CURRENT.get();
        if (snapshot == null) CURRENT.remove();
        else CURRENT.set(snapshot);

        try {
            task.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    };
}
```

**Wrapper cho `Callable<V>`:**

```java
static <V> Callable<V> withContext(
        Callable<V> task, RequestContext snapshot) {
    return () -> {
        RequestContext previous = CURRENT.get();
        if (snapshot == null) CURRENT.remove();
        else CURRENT.set(snapshot);

        try {
            return task.call();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    };
}
```

Đây là **hai đoạn phương thức**, cần đặt trong class có `CURRENT` và import `Runnable` / `Callable` thích hợp. Ví dụ chương trình độc lập ở phần tiếp theo ghép đầy đủ tất cả thành phần.

Tại sao cần từng bước?

1. `previous = CURRENT.get()`: ghi lại context đã hiện diện trên **worker**, có thể là của scope bên ngoài trong một lời gọi lồng nhau.
2. `CURRENT.set(snapshot)` hoặc `remove()`: áp context của task mới; snapshot `null` phải có nghĩa “không có context”, tránh dùng giá trị cũ.
3. `try`: chạy công việc, bất kể trả kết quả hay ném ngoại lệ.
4. `finally`: luôn khôi phục giá trị cũ. Nếu trước đó không có giá trị thì dùng `remove()` để tránh lưu trạng thái thừa suốt đời thread pool.

**Vì sao restore thay vì luôn remove?** Nếu một task gọi một hàm khác cũng kích hoạt context tạm thời trên cùng thread, wrapper bên trong phải trả lại context của wrapper bên ngoài. Còn với pool worker có context rỗng ban đầu, restore sẽ dẫn đến `remove()`.

Không nên dùng chung một `ThreadLocal` cho dữ liệu mutable của nhiều request rồi sửa trực tiếp cùng object. Việc set một object ở hai thread không biến object đó thành thread-safe; concurrency safety của dữ liệu vẫn cần thiết.

## 6. Ví dụ Java 21 hoàn chỉnh, có kết quả xác định

Chương trình sau dùng **một platform worker** (`newFixedThreadPool(1)`) để chứng minh cả hai loại task có thể nhận context riêng, sau đó worker trở về trạng thái không có context. Ví dụ cố ý đợi bằng `Future.get()` trước mỗi lần in để stdout không phụ thuộc thứ tự lập lịch.

Tạo tệp **ContextPropagationDemo.java**:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ContextPropagationDemo {
    record RequestContext(String requestId, String actor) {}

    private static final ThreadLocal<RequestContext> CURRENT =
            new ThreadLocal<>();

    static Runnable withContext(Runnable task, RequestContext snapshot) {
        return () -> {
            RequestContext previous = CURRENT.get();
            if (snapshot == null) CURRENT.remove();
            else CURRENT.set(snapshot);
            try {
                task.run();
            } finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        };
    }

    static <V> Callable<V> withContext(
            Callable<V> task, RequestContext snapshot) {
        return () -> {
            RequestContext previous = CURRENT.get();
            if (snapshot == null) CURRENT.remove();
            else CURRENT.set(snapshot);
            try {
                return task.call();
            } finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        };
    }

    static String describe() {
        RequestContext c = CURRENT.get();
        return c.requestId() + "/" + c.actor();
    }

    public static void main(String[] args) throws Exception {
        RequestContext a = new RequestContext("req-101", "alice");
        RequestContext b = new RequestContext("req-202", "bob");

        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            Future<String> call = executor.submit(
                    withContext(() -> "Callable: " + describe(), a));
            System.out.println(call.get());

            AtomicReference<String> output = new AtomicReference<>();
            Future<?> run = executor.submit(
                    withContext(
                            () -> output.set("Runnable: " + describe()), b));
            run.get();
            System.out.println(output.get());

            Future<Boolean> cleared =
                    executor.submit(() -> CURRENT.get() == null);
            System.out.println("Context cleared: " + cleared.get());
        }
    }
}
```

Biên dịch/chạy bằng **JDK 21+**:

```bash
javac ContextPropagationDemo.java
java ContextPropagationDemo
```

**Stdout chính xác:**

```text
Callable: req-101/alice
Runnable: req-202/bob
Context cleared: true
```

Phân tích:

- `call` là `Callable<String>`. Snapshot `a` được truyền cùng wrapper từ caller; dữ liệu được cài trên worker khi `call()` bắt đầu, rồi được dọn trong `finally`.
- `run` là `Runnable`. Nó ghi chuỗi kết quả vào `AtomicReference`, main đợi `Future.get()` trước khi in. Không cần ép `Runnable` có kiểu trả về.
- Bài dùng pool một worker để chắc chắn tác vụ kiểm tra có cơ hội quan sát **chính worker đã chạy hai task trước đó**; `Context cleared: true` xác nhận cleanup trong trường hợp này.
- `try (ExecutorService ...)` yêu cầu JDK 19+ vì `ExecutorService` hỗ trợ `AutoCloseable` từ Java 19; JDK 21 là lựa chọn ổn định cho chương trình này.
- `Future.get()` vừa chờ hoàn thành vừa cho phép quan sát exception trong task. Nếu một task ném lỗi, `finally` vẫn chạy trước khi `get()` ném `ExecutionException`.

Ví dụ chứng minh cách cài/khôi phục context; nó không được dùng làm benchmark cho throughput hoặc latency của executor.

## 7. Context capture ở đúng thời điểm

Điểm dễ sai nhất là **capture muộn**. So sánh:

**Sai: capture trong task chạy trên worker**:

```java
executor.submit(() -> {
    RequestContext snapshot = CURRENT.get(); // đọc ThreadLocal của worker
    handle(snapshot);
});
```

**Đúng: capture trước khi submit**:

```java
RequestContext snapshot = CURRENT.get(); // trên caller
executor.submit(withContext(() -> handle(CURRENT.get()), snapshot));
```

Hai đoạn trên giả định đã có `executor`, `CURRENT` và `handle(...)` trong scope; chúng minh họa ranh giới capture, không phải tệp biên dịch độc lập.

Những chi tiết tác động đến tính đúng:

- **Queue delay**: task có thể khởi chạy sau vài giây/phút; capture lúc `submit` đem đúng dữ liệu liên quan tới tác vụ ban đầu.
- **Mutable context**: snapshot kiểu tham chiếu vẫn có thể đổi nội dung; hãy tạo object bất biến/copy giá trị liên quan trước khi submit.
- **Cancellation**: nếu task chưa chạy và bị hủy, wrapper chưa kích hoạt context; không có trạng thái worker cần restore. Nếu đang chạy và bị interrupt, `finally` vẫn là nơi cleanup đáng tin.
- **Rejected submission**: `RejectedExecutionException` xảy ra trước khi wrapper thực thi. Context caller phải được quản lý theo scope của caller, không mong chờ cleanup của worker.
- **Nested submission**: nếu task đang có `context-A` rồi gửi tiếp một task mới, cần capture lại `A` ở thời điểm gửi; executor mới không tự kế thừa context của task cha.
- **Timeout**: `future.get(timeout, unit)` timeout chỉ ảnh hưởng caller đợi; bản thân task **không tự dừng**. Cần quyết định hủy/interrupt và tính nhất quán của dữ liệu context.

## 8. Bọc Executor.execute và giới hạn của decorator

Nếu mã có nhiều nơi gọi `execute(Runnable)`, có thể bọc ở cấp `Executor` để mỗi lệnh được capture tự động:

```java
static Executor contextAware(Executor delegate) {
    return command -> {
        RequestContext snapshot = CURRENT.get(); // đang ở caller
        delegate.execute(withContext(command, snapshot));
    };
}
```

Đây là **phương thức minh họa**, cần import `java.util.concurrent.Executor` và dùng cùng `CURRENT`/`withContext` đã định nghĩa. Đặt wrapper khi cấu hình dependency:

```java
Executor decorated = contextAware(underlyingExecutor);
decorated.execute(() -> performWork());
```

Ở đây `snapshot` được lấy **mỗi lần gọi `execute`**, không lấy một lần khi tạo `decorated`. Nếu chụp lúc tạo wrapper, các task của những request khác nhau sẽ cùng dùng context sai.

**Giới hạn rất quan trọng:** `Executor` chỉ có `execute`. Wrapper trên **không tự mang các API** `submit`, `invokeAll`, `schedule` hoặc `CompletableFuture.supplyAsync` của những interface khác. Đừng ép kiểu wrapper thành `ExecutorService`. Có ba lựa chọn:

1. Với `ExecutorService`, bọc trực tiếp từng `Runnable`/`Callable` trước `submit` hoặc trước khi tạo collection cho `invokeAll`.
2. Nếu cần “decorate toàn bộ service”, triển khai một adapter `ExecutorService` hoàn chỉnh, bao phủ mọi đường gửi task, shutdown, exception, scheduling (nếu là `ScheduledExecutorService`). Một wrapper chỉ ghi đè `execute` có thể không bao phủ phương thức chuyên biệt của delegate.
3. Nếu có thể sửa API nghiệp vụ, tiếp tục ưu tiên truyền `RequestContext` như tham số; cách này không cần decorator.

Dùng `CompletableFuture`:

```java
RequestContext snapshot = CURRENT.get();
CompletableFuture<String> result =
        CompletableFuture.supplyAsync(() -> handle(snapshot), executor);
```

Đoạn trên **truyền tường minh** snapshot trong lambda; `supplyAsync` không tự mang `ThreadLocal` của caller. Với chuỗi `thenApplyAsync` / `thenComposeAsync`, cần xem **mỗi stage chạy ở executor nào**, từ đâu được kích hoạt và liệu nó đọc context ngầm không. Không mặc định chain sẽ kế thừa context vì stage trước có giá trị đúng.

## 9. Lỗi phổ biến khi truyền context qua executor

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Cài `ThreadLocal` trên caller rồi tin worker sẽ đọc được | Worker thấy `null` hoặc dữ liệu của task khác | Truyền tham số hoặc capture/activate đúng trên worker |
| Đọc `CURRENT.get()` lần đầu bên trong lambda trên worker | Capture sai thời điểm và thread | Capture **trước submit** |
| `set()` nhưng không `remove()`/restore trong `finally` | Context rò sang task sau, đặc biệt nguy hiểm với trace/user/tenant | Restore giá trị trước đó trong `finally` |
| Luôn `remove()` mà không giữ `previous` | Phá scope bên ngoài khi wrapper lồng nhau | Save/restore trạng thái worker |
| Giữ một `HashMap` mutable làm snapshot giữa các request | Data race hoặc thông tin thay đổi trong lúc chờ | Copy bất biến / giới hạn ownership |
| Bọc `execute` rồi cho rằng đã tự bọc `submit`, `invokeAll`, `schedule` | Đường gửi task khác bỏ qua propagation | Bọc từng API đúng loại hoặc làm adapter đầy đủ |
| Không gọi `Future.get()` khi cần phát hiện lỗi | Exception trong `submit` có thể nằm trong `Future` mà không được báo | Quan sát `Future`, xử lý `ExecutionException` |
| Sử dụng `InheritableThreadLocal` với pool và kỳ vọng mỗi task thừa kế | Giá trị chỉ liên quan lúc tạo child thread, không phải mỗi lần submit task | Propagation có chủ đích tại task boundary |
| Giữ context có secrets/đối tượng nặng qua task kéo dài | Tăng nguy cơ lộ dữ liệu và giữ tham chiếu không cần thiết | Chỉ chuyển các trường tối thiểu, hạn chế lifetime |

### Phân biệt InheritableThreadLocal, virtual threads và ScopedValue

- `InheritableThreadLocal` chuyển giá trị khởi tạo ở **thời điểm tạo thread con** theo quy tắc của Java; worker thread pool đã tạo từ trước không tự nhận giá trị mới của mỗi lần `submit`. Thêm nữa, mutable value được kế thừa bằng tham chiếu vẫn có thể chia sẻ trạng thái ngoài ý muốn.
- Virtual thread vẫn là một `Thread` Java và vẫn hỗ trợ `ThreadLocal`. Mô hình “một virtual thread cho một tác vụ” giảm một số nguy cơ tái sử dụng thread của pool, nhưng **không biến task của một executor bất kỳ thành con logic được tự động truyền mọi context**. Đừng trộn vòng đời thread với ranh giới request.
- `ScopedValue` được hoàn thiện trong **JDK 25** (JEP 506), hữu ích để truyền dữ liệu chỉ đọc theo **dynamic scope**. Với việc gửi task bất kỳ sang executor, vẫn phải xác định rõ cách tạo binding trên thread chạy task; một binding của caller không tự lan sang mọi executor. Đây là lựa chọn bổ sung của Java hiện đại, không phải điều kiện để chạy ví dụ Java 21 ở trên.

Ví dụ JDK 25+ với `ScopedValue` (đoạn minh họa, không độc lập):

```java
static final ScopedValue<RequestContext> CTX =
        ScopedValue.newInstance();

RequestContext snapshot = currentContext;
executor.execute(() ->
        ScopedValue.where(CTX, snapshot).run(() -> performWork()));
```

`ScopedValue.where(...).run(...)` tạo binding chỉ trong thời gian `run`. Quy tắc truy cập và truyền cho child tasks của structured concurrency phải theo tài liệu phiên bản JDK tương ứng.

## 10. Kiểm thử và quan sát đường truyền context

Nên kiểm thử cả **đúng dữ liệu** lẫn **không còn dữ liệu**:

1. Cho hai tác vụ có context khác nhau chạy nối tiếp trên **cùng một worker**; kiểm tra task B không đọc lẫn context A.
2. Cho một task ném exception khi đã activate context; sau khi `Future.get()` báo lỗi, gửi task kiểm tra ThreadLocal rỗng.
3. Với wrapper lồng nhau, xác nhận context phía ngoài được **khôi phục** sau khi scope trong đóng.
4. Với thread pool nhiều worker và task trộn lẫn requestId, thêm test concurrent/stress để phát hiện tham chiếu mutable chia sẻ sai.
5. Với queue delay, thay đổi context trên caller sau `submit` và xác nhận task vẫn dùng snapshot đã capture.
6. Kiểm tra nhánh `RejectedExecutionException`/cancellation, và bảo đảm không giữ context không cần thiết.

Đối với logging, nên gắn `requestId` của context vào bản ghi khi task chạy; mỗi tác vụ phải quan sát đúng ID của chính nó. Nếu dùng MDC của thư viện logging, MDC thường dựa trên thread-local riêng: việc propagate một biến `CURRENT` **không tự động propagate toàn bộ MDC**. Mỗi hệ thống context có cách snapshot, restore và cleanup riêng.

Không nên ghi dữ liệu xác thực nhạy cảm vào log chỉ để debug propagation. Những ID dùng để tương quan có thể được log tùy chính sách; credential/token nên có đường quản lý riêng.

## 11. Chọn chiến lược nào?

| Nhu cầu | Chiến lược đề xuất | Lý do |
| --- | --- | --- |
| Nắm quyền sửa chữ ký hàm nghiệp vụ | `handle(context)`, lambda capture giá trị bất biến | Dễ đọc, dễ test, không có trạng thái ngầm |
| Legacy API sâu cần “current context” | Wrapper `Runnable`/`Callable` với `ThreadLocal`, restore trong `finally` | Tương thích call stack hiện tại, tránh rò context |
| Nhiều lệnh `execute` | Decorate `Executor` với snapshot tại thời điểm từng lần `execute` | Giảm lặp mã ở caller |
| Dùng cả `submit`, `invokeAll`, scheduling | Bọc tác vụ tại từng API hoặc adapter đầy đủ | Không bỏ sót đường chạy |
| Async chain qua nhiều executor | Truyền context tường minh cho từng stage hoặc decorator có coverage xác định | Không dựa vào giả định chain kế thừa ngầm |
| Java 25+ cần context chỉ đọc trong dynamic scope | Cân nhắc `ScopedValue`, vẫn làm rõ task boundary | Scope rõ hơn, tránh mutable thread-local kéo dài |

Bộ câu hỏi tự kiểm tra:

1. **Vì sao đặt `ThreadLocal` trong caller trước `submit` không đủ?** Vì `ThreadLocal` gắn với từng thread và task có thể chạy trên worker khác.
2. **Snapshot nên tạo lúc nào?** Lúc caller xây/gửi task; activation xảy ra sau, trên worker khi task thực thi.
3. **Vì sao restore `previous` thay vì chỉ remove?** Vì wrapper có thể được gọi lồng nhau trên cùng thread; scope bên ngoài phải giữ nguyên context của nó.
4. **Có thể suy ra ThreadLocal được truyền qua `Future.get()` happens-before không?** Không. Happens-before đảm bảo thứ tự hiển thị bộ nhớ, không sao chép state theo thread.
5. **Wrapper `Executor.execute` có tự hỗ trợ `ExecutorService.submit` không?** Không. `Executor` chỉ công bố `execute` và phải kiểm soát riêng các API khác.

## 12. Tài liệu chuẩn

- [Java SE 21 — ExecutorService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, `invokeAll`, `Future` và bảo đảm memory consistency.
- [Java SE 21 — Runnable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Runnable.html) và [Callable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Callable.html): hợp đồng thực thi task.
- [Java SE 21 — ThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html): giá trị theo thread, `get`/`set`/`remove`.
- [Java SE 21 — InheritableThreadLocal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html): giới hạn kế thừa ở thời điểm tạo thread.
- [Java SE 25 — ScopedValue](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html) và [JEP 506](https://openjdk.org/jeps/506): context binding kiểu scope trên Java hiện đại.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-006">Context theo request và ranh giới vòng đời task</a>

<details>
<summary>Xem chi tiết</summary>

# Context theo request và ranh giới vòng đời task trong Java

> **Phạm vi:** Java SE 21 và concurrency của Java nói chung. “Request” nghĩa là một đơn vị xử lý logic (yêu cầu mạng, lệnh CLI, message, hoặc một lượt xử lý job). Các ví dụ không dựa vào Spring hay một framework cụ thể.

## 1. Request context là gì?

**Request context** là bộ thông tin đi cùng một đơn vị công việc logic: `requestId` (đối chiếu log/tracing), `actorId` (người khởi tạo), `tenantId`, locale, deadline, thông tin phân quyền đã được xác minh, v.v. Một request thường đi qua nhiều phương thức và có thể tách thành nhiều tác vụ đồng thời.

~~~text
Request R-42
  │
  ├── Task A: gọi dịch vụ dữ liệu ─── worker/thread 1
  │
  └── Task B: tính kết quả phụ ───── worker/thread 2
                  │
            gom kết quả A và B
                  │
          kết thúc request R-42
~~~

**Request không đồng nghĩa với thread; task cũng không đồng nghĩa với thread.** Thread là tài nguyên thực thi, task là đơn vị công việc được đưa vào lập lịch, còn request là quan hệ nghiệp vụ chứa một hoặc nhiều task. Một worker trong thread pool có thể chạy tuần tự task của nhiều request. Cùng một request có thể chạy trên nhiều thread.

Có hai câu hỏi độc lập nhưng liên quan:

- **Propagation:** dữ liệu ngữ cảnh đi từ bước nhận request sang task/callback nào và bằng cơ chế gì?
- **Lifetime/ownership:** ai sở hữu việc tạo, chờ, kết thúc, hủy và cleanup task; khi request hết hiệu lực, công việc con còn được phép làm gì?

Lỗi propagation có thể khiến task mất `requestId` hoặc nhìn nhầm `actorId` từ request trước. Lỗi lifetime có thể khiến task tiếp tục thao tác với tài nguyên đã đóng hoặc ghi dữ liệu sau khi request đã timeout.

## 2. Phân biệt các phạm vi vòng đời

| Phạm vi | Từ đâu đến đâu? | Ví dụ |
|---|---|---|
| **Request** | Tiếp nhận → thành công, lỗi, timeout hoặc hủy theo quy tắc ứng dụng | R-42 bắt đầu, sinh A/B, tổng hợp, trả kết quả |
| **Task** | Được tạo/gửi thực thi → hoàn tất, lỗi, hoặc thật sự kết thúc sau tín hiệu hủy | `Callable` truyền cho `ExecutorService.submit` |
| **Thread** | Thread được tạo → kết thúc | Worker cố định của `newSingleThreadExecutor` |
| **Context binding** | Gắn ngữ cảnh → tháo/khôi phục binding | `ThreadLocal.set/remove`; dynamic binding của `ScopedValue` |

Ví dụ tái sử dụng worker:

~~~text
Thời gian ─────────────────────────────────────────────────────────▶
Request R1:  [nhận ─ submit task T1 ───── xong]
Task T1:                     [chạy ─ kết thúc]
Worker W:    idle ───────────[  T1  ]── idle ────[  T2  ]── idle
Request R2:                                  [nhận ─ T2 ─ kết thúc]

Nếu T1 để ThreadLocal = R1 mà không remove:
Worker W:                    [R1]─────────────[T2 thấy R1: SAI]
~~~

Thread pool không mặc nhiên xóa ngữ cảnh cho mỗi task. Tương tự, `Future.get()` giúp caller đợi task nhưng không tự định nghĩa mọi quy tắc cleanup, timeout, side effect hay propagation.

## 3. Thiết kế request context có thể chia sẻ an toàn

Một cách tốt là dùng **snapshot bất biến**, chỉ mang những trường mà task thật sự cần:

~~~java
// Minh họa kiểu dữ liệu Java 21, không phải chương trình độc lập.
record RequestContext(
        String requestId,
        String actorId,
        String tenantId
) {}
~~~

`record` tạo các field final, nhưng **không làm deep copy**: nếu một thành phần là `Map`/`List` mutable, những thread giữ cùng tham chiếu vẫn có thể cập nhật nó. Với dữ liệu tập hợp cần bất biến, cân nhắc `Map.copyOf`/`List.copyOf` và kiểm tra phần tử bên trong.

Các nguyên tắc:

1. **Càng nhỏ càng tốt:** giữ định danh và dữ liệu cần thiết; không capture toàn bộ request object, stream đang mở, connection, session có vòng đời riêng, hoặc object graph lớn.
2. **Dữ liệu tin cậy:** actor/tenant dùng để phân quyền phải được xác minh ở biên tin cậy; header tùy ý từ caller không tự trở thành quyền.
3. **Không nhầm context với thread safety:** context immutable có thể chia sẻ; một object mutable đặt trong `ThreadLocal` vẫn có thể bị race nếu nhiều thread cùng tham chiếu.
4. **Có thời hạn rõ ràng:** snapshot tồn tại trong task lâu hơn request không có nghĩa vẫn được phép thực hiện side effect dựa trên quyền hoặc trạng thái đã hết hạn.
5. **Context không thay thế cancellation hay transaction:** có `requestId` không làm timeout lan truyền và không tự rollback bất cứ thao tác nào.

Đối với API do mình kiểm soát, phương án đơn giản và rõ ràng nhất là truyền context bằng tham số:

~~~java
// Minh họa: các phương thức được định nghĩa ở ứng dụng.
Result process(RequestContext ctx) {
    Data data = load(ctx);
    return transform(ctx, data);
}
~~~

Mỗi dependency hiện rõ trong chữ ký; task không phụ thuộc thread nào chạy nó.

## 4. `ThreadLocal` gắn giá trị vào thread, không gắn vào request

`ThreadLocal<T>` cho **mỗi thread** một ô giá trị riêng. Với khai báo:

~~~java
private static final ThreadLocal<RequestContext> CURRENT =
        new ThreadLocal<>();
~~~

Các thao tác cơ bản:

- `CURRENT.set(ctx)`: gán vào thread **đang gọi**.
- `CURRENT.get()`: đọc ở thread **đang gọi**; với `new ThreadLocal<>()`, chưa set thì thường nhận `null`.
- `CURRENT.remove()`: xóa giá trị của thread **đang gọi**, không xóa ở các worker khác.
- `ThreadLocal.withInitial(supplier)`: khởi tạo giá trị riêng cho thread khi cần; không tự gắn với vòng đời request.

Ba giới hạn quan trọng:

**Không tự truyền qua executor.** `executor.submit(task)` không chép ordinary `ThreadLocal` của caller sang worker; một task có thể nhận `null` dù caller đã `set`.

**Có thể rò rỉ qua reuse.** Worker sống lâu hơn một request. Nếu task A quên `remove`, task B chạy trên cùng worker có thể nhìn thấy context A. Đó vừa là lỗi dữ liệu vừa có thể thành lỗi cách ly thông tin.

**Cleanup phải đúng thread và đúng scope.** Gọi `remove()` ở caller sau `submit` không xóa binding trên worker. `finally` phải bao công việc của chính thread đã `set`. Với scope lồng, cần khôi phục giá trị bên ngoài.

## 5. Ví dụ chạy được: task sau đọc nhầm context của task trước

Tạo tệp **`RequestContextDemo.java`**:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RequestContextDemo {
    private record RequestContext(String requestId, String actor) {}

    private static final ThreadLocal<RequestContext> CURRENT =
            new ThreadLocal<>();

    private static String currentId() {
        RequestContext ctx = CURRENT.get();
        return ctx == null ? "none" : ctx.requestId();
    }

    // Chỉ dùng khi wrapper sở hữu binding ở boundary task ngoài cùng.
    private static <T> Callable<T> bound(
            RequestContext context, Callable<T> body) {
        return () -> {
            CURRENT.set(context);
            try {
                return body.call();
            } finally {
                CURRENT.remove();
            }
        };
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService pool =
                     Executors.newSingleThreadExecutor()) {

            // Cố tình SAI: set context nhưng quên remove.
            System.out.println("unsafe-1=" + pool.submit(() -> {
                CURRENT.set(new RequestContext("req-1", "alice"));
                return currentId();
            }).get());

            // Task mới, cùng worker nên thấy giá trị cũ.
            System.out.println("unsafe-2=" +
                    pool.submit(RequestContextDemo::currentId).get());

            // Xóa vết sót trên chính worker để tiếp tục demo.
            pool.submit(() -> {
                CURRENT.remove();
                return null;
            }).get();

            // ĐÚNG ở task boundary: bind rồi cleanup bằng finally.
            System.out.println("safe-3=" + pool.submit(
                    bound(new RequestContext("req-3", "bob"),
                            RequestContextDemo::currentId)).get());

            System.out.println("after-3=" +
                    pool.submit(RequestContextDemo::currentId).get());
        }
    }
}
~~~

Chạy trên **JDK 21**:

~~~bash
javac --release 21 RequestContextDemo.java
java RequestContextDemo
~~~

Output khi không có lỗi:

~~~text
unsafe-1=req-1
unsafe-2=req-1
safe-3=req-3
after-3=none
~~~

`newSingleThreadExecutor` đảm bảo một worker xử lý tuần tự task. Hai lệnh `get()` chờ từng task nên output luôn theo thứ tự đã nêu. Task đầu cố ý để sót `req-1`; task hai đọc lại được giá trị cũ dù không thuộc request đó. Sau khi xóa vết sót, task bọc bằng `bound` gán `req-3` và `remove` trong `finally`. Task kế tiếp thấy `none`.

**`bound` được thiết kế cho task ngoài cùng**, nơi nó có quyền sở hữu binding và cần đảm bảo worker sạch sau task. Nếu đặt wrapper này lồng vào đoạn mã cùng thread có một context hợp lệ ở scope ngoài, `remove` sẽ xóa luôn binding của scope đó. Vì vậy phải phân biệt cleanup boundary ngoài cùng với nested scope.

## 6. Xử lý nested scope đúng cách

Giả sử request đang có context X và một lời gọi con muốn tạm thời xử lý trong context Y trên **cùng thread**. Sau khi lời gọi con thoát, X phải được phục hồi:

~~~java
// Minh họa trong lớp có CURRENT; không phải chương trình độc lập.
RequestContext previous = CURRENT.get();
CURRENT.set(innerContext);
try {
    executeInnerWork();
} finally {
    if (previous == null) {
        CURRENT.remove();
    } else {
        CURRENT.set(previous);
    }
}
~~~

Đây là chính sách **save/restore** cho scope lồng. Nó không thay thế việc làm sạch worker ở boundary ngoài cùng: nếu thread trước đó đã mang dữ liệu rò rỉ, save/restore chỉ đưa dữ liệu rò rỉ trở lại. Tại task boundary, cần chủ động bảo đảm không có context của request khác còn bám sau khi task kết thúc.

`remove` rõ ý nghĩa hơn `set(null)` vì xóa binding thay vì chỉ gán giá trị null. Cũng cần xử lý exception: nếu `executeInnerWork()` ném lỗi, khối `finally` vẫn phục hồi trạng thái.

## 7. Chuyển context qua `ExecutorService` và `CompletableFuture`

**Truyền snapshot tường minh** là giải pháp ưu tiên khi API cho phép:

~~~java
// Mã minh họa: ctx đã được xác minh, executor và work được cung cấp.
RequestContext ctx = currentRequestSnapshot;
Future<Result> future = executor.submit(() -> work(ctx));
Result result = future.get();
~~~

Lambda capture cùng `ctx` bất biến; không đòi thread worker phải kế thừa ambient context. Nếu API cũ chỉ biết đọc `ThreadLocal`, hãy bọc **từng task** bằng wrapper ở mục 5 hoặc wrapper có save/restore phù hợp ownership.

Với `CompletableFuture`:

~~~java
// Minh họa: lookup và format nhận RequestContext rõ ràng.
CompletableFuture<String> pipeline =
        CompletableFuture.supplyAsync(
                () -> lookup(ctx), ioExecutor)
        .thenApply(value -> format(ctx, value));
~~~

Các trường hợp dễ nhầm:

- `supplyAsync(..., executor)` chạy supplier ở executor chỉ định, không tự copy ordinary `ThreadLocal` từ caller.
- `thenApply` không đảm bảo luôn chạy trên đúng worker hoặc thread caller; thread thực thi phụ thuộc trạng thái hoàn tất của stage.
- `thenApplyAsync` không có executor thường chạy trong `ForkJoinPool.commonPool()` theo hợp đồng Java 21, có ngoại lệ khi mức song song mặc định quá thấp.
- `thenCompose`, `whenComplete`, `exceptionally` và callback timeout có thể nằm ở các boundary khác. Callback nào cần context thì phải nhận nó rõ ràng hoặc được bind đúng phạm vi.
- `get()/join()` chỉ chờ hoặc lấy kết quả; chúng không tự đồng bộ tất cả child tasks và không tự dọn bất kỳ `ThreadLocal` nào ở thread thực thi.

Nếu context gắn với quyền truy cập, tránh giả định việc log đúng `requestId` nghĩa là toàn bộ authorization đã đúng; task cần nhận đúng principal/tenant đã xác minh theo chính sách ứng dụng.

## 8. `InheritableThreadLocal` không phải cơ chế propagation theo request

`InheritableThreadLocal` cho phép **child thread nhận giá trị khởi tạo từ parent tại lúc child thread được tạo** (có tùy chọn kiểm soát inheritance). Đây không phải một bản copy mới mỗi khi task được submit vào executor.

~~~text
Request A đặt context=A
  └─ Tạo worker W → W có thể kế thừa A
Request B đặt context=B
  └─ Submit task lên W đã tồn tại → W không tự cập nhật thành B
~~~

Nếu giá trị ban đầu là một object mutable, inheritance mặc định thường truyền cùng reference, không phải deep copy. Có thể override `childValue` để thay đổi giá trị child nhưng vẫn cần quản lý dữ liệu và ownership.

Bởi vậy không dùng `InheritableThreadLocal` như giải pháp mặc định cho request trong thread pools. Tính năng kế thừa cũng không thay thế quy tắc cleanup khi request kết thúc.

## 9. Virtual threads trên JDK 21 và task lifetime

Virtual threads (JEP 444) đã là tính năng ổn định ở Java 21. Chúng vẫn là `java.lang.Thread`; `ThreadLocal` được gắn với **virtual thread**, không gắn với carrier thread trong JVM.

~~~java
// Ví dụ minh họa trong lớp có process(ctx), cần import java.util.concurrent.
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<String> future = executor.submit(() -> process(ctx));
    System.out.println(future.get());
}
~~~

`newVirtualThreadPerTaskExecutor()` tạo virtual thread mới cho mỗi task, nên khác fixed platform-thread pool ở việc không cố tái sử dụng một worker cho các task kế tiếp. Tuy nhiên:

- Ordinary `ThreadLocal` trên caller **không tự động** được truyền vào task mới.
- Virtual thread mới có thể nhận `InheritableThreadLocal` tùy cấu hình khi tạo thread; inheritance vẫn không đồng nghĩa với request lifetime.
- `ThreadLocal` chứa cache/object lớn theo từng virtual thread có thể tốn bộ nhớ đáng kể nếu số virtual thread rất lớn.
- Một task chạy trong virtual thread vẫn có thể sống quá request nếu không có quyết định join/hủy/ownership rõ ràng.
- Chia sẻ mutable object qua nhiều virtual thread vẫn cần thread-safety như thường.

**Thread-per-task** chỉ mô tả cách cấp thread; không tự biến các tác vụ rời rạc thành một cấu trúc cha–con có vòng đời kết thúc cùng request.

## 10. `ScopedValue` trong Java 21: context có phạm vi động (preview)

JDK 21 giới thiệu `ScopedValue` (JEP 446) ở trạng thái **preview**. Thay vì một biến mutable `set/get/remove`, ta bind giá trị cho một **dynamic scope** của lời gọi. Khi scope kết thúc, binding tự phục hồi, kể cả khi có exception. Nó phù hợp thông tin cần truyền một chiều, phần lớn chỉ đọc.

Tạo tệp **`ScopedContextDemo.java`** trên **JDK 21**:

~~~java
public class ScopedContextDemo {
    private static final ScopedValue<String> REQUEST_ID =
            ScopedValue.newInstance();

    public static void main(String[] args) {
        ScopedValue.where(REQUEST_ID, "req-42").run(() -> {
            System.out.println("inside=" + REQUEST_ID.get());

            ScopedValue.where(REQUEST_ID, "req-inner").run(() -> {
                System.out.println("nested=" + REQUEST_ID.get());
            });

            System.out.println("restored=" + REQUEST_ID.get());
        });

        System.out.println("outside=" + REQUEST_ID.isBound());
    }
}
~~~

Chạy với preview của **đúng JDK 21**:

~~~bash
javac --enable-preview --release 21 ScopedContextDemo.java
java --enable-preview ScopedContextDemo
~~~

Output:

~~~text
inside=req-42
nested=req-inner
restored=req-42
outside=false
~~~

Giải thích:

- `ScopedValue.newInstance()` tạo key, ban đầu chưa bind.
- `ScopedValue.where(KEY, value).run(body)` bind giá trị trong đoạn thực thi body và các phương thức đồng bộ gọi bên trong có quyền truy cập key.
- Scope trong có thể rebind tạm; khi thoát, scope ngoài nhận lại `req-42`.
- Thoát outer scope, `isBound()` trả về false. Đọc `get()` khi chưa bind sẽ không trả một requestId hợp lệ.
- `ScopedValue` không tự lan qua **arbitrary executor tasks**. Khi dùng kết hợp `StructuredTaskScope`, binding được kế thừa vào child threads do task scope quản lý theo quy tắc của API.
- Giá trị được bind có thể là reference tới object mutable; chính `ScopedValue` không biến object này thành immutable.

**Lưu ý phiên bản:** cả `ScopedValue` và `StructuredTaskScope` đều còn preview ở JDK 21. API/cú pháp của các JDK mới hơn có thể khác; không biên dịch snippet JDK 21 bằng cách suy đoán từ JDK hiện tại mà thiếu kiểm tra.

## 11. Ranh giới task cha–con: Structured Concurrency (preview ở Java 21)

Một request thường fork hai tác vụ rồi cần cùng kết quả:

~~~text
Request (cha)
  ├─ tạo context bất biến
  ├─ mở task scope
  │   ├─ fork A → thành công hoặc lỗi
  │   ├─ fork B → thành công hoặc lỗi
  │   ├─ join / xử lý lỗi, hủy nếu cần
  │   └─ close, kết thúc tác vụ con
  └─ tổng hợp, kết thúc request
~~~

`StructuredTaskScope` (JEP 453) hướng tới việc ràng buộc vòng đời task con vào scope mà người gọi sở hữu. Trong Java 21, đây là preview API, có các chính sách như `ShutdownOnFailure` và `ShutdownOnSuccess`. Khi tạo task scope trong binding `ScopedValue` phù hợp, các task fork bằng scope đó có thể kế thừa bindings một cách có cấu trúc.

Điểm quan trọng:

1. **Fork không phải hoàn tất.** Caller cần join, đọc kết quả/lỗi và đóng scope.
2. **Hủy chỉ là yêu cầu.** `shutdown` có thể gửi interrupt đến subtasks chưa hoàn thành; mã không hợp tác với interrupt hoặc native I/O có thể tiếp tục. Khi `close`, scope chờ các child threads kết thúc theo hợp đồng API.
3. **Không tự rollback side effect.** Task đã ghi file, gửi message hoặc gọi một dịch vụ ngoài trước khi bị hủy thì cần giao thức bù trừ/idempotency riêng.
4. **Không phải mọi task đều là child request.** Một job được phép tồn tại sau request phải có owner, deadline, retry và cleanup độc lập; không giữ tham chiếu tới object request đã hết vòng đời.

Trong ứng dụng Java 21 không dùng preview, vẫn có thể áp dụng **các nguyên tắc cấu trúc** này qua `ExecutorService` và `Future`: duy trì danh sách future của các tác vụ, đợi đủ kết quả, xử lý lỗi, hủy phần còn lại và đóng executor/resource. Nhưng phải viết chính sách đó một cách tường minh.

## 12. Deadline, timeout và cancellation phải được xử lý riêng

Ví dụ request có deadline 300 ms; A chạy 100 ms, B đang gọi I/O 2 giây:

~~~text
T0  Request R-42 bắt đầu, deadline = T0 + 300 ms
T1  Fork task A và task B
T2  Task A hoàn tất; B vẫn đang đợi
T3  Request timeout; caller gửi tín hiệu cancel cho B
T4  B thực sự dừng và dọn tài nguyên? Phụ thuộc API/code
~~~

Không được đồng nhất ba sự kiện **caller hết chờ**, **cancel được yêu cầu** và **task thực sự đã kết thúc**. Ví dụ `Future.get(timeout, unit)` có thể ném `TimeoutException` nhưng không tự hủy future bên dưới. `Future.cancel(true)` yêu cầu interrupt nếu task đang chạy; không đảm bảo I/O/native call ngừng ngay hoặc side effect chưa xảy ra.

Một thiết kế tốt cần:

- Tính deadline từ entry, truyền deadline hoặc ngân sách còn lại qua API; đặt timeout cụ thể cho I/O để không đợi vô hạn.
- Hủy các task con không còn cần và bảo đảm code hợp tác với interruption khi phù hợp.
- Dùng `try/finally` hoặc `try-with-resources` để giải phóng resource và context binding.
- Chỉ công bố kết quả khi request còn hiệu lực; với side effect không thể hủy hoàn toàn, xác định idempotency hoặc cơ chế bù trừ.
- Nếu chuyển sang job nền, chỉ truyền snapshot tối thiểu và thiết lập lifecycle riêng; không giữ request stream hay transaction đã đóng.

**Context propagation không đồng nghĩa với cancellation propagation**: mang theo `requestId` không khiến task tự hiểu deadline.

## 13. Bảng lựa chọn công cụ

| Cách làm | Phù hợp khi | Hạn chế chính |
|---|---|---|
| **Truyền `RequestContext` như tham số** | API do mình kiểm soát, task/callback qua nhiều thread | Cần chuyển tiếp qua một số tầng phương thức |
| **`ThreadLocal` + bind/cleanup** | API legacy yêu cầu context ambient trên thread hiện tại | Dễ bỏ sót cleanup và rò rỉ khi worker tái sử dụng |
| **`InheritableThreadLocal`** | Thực sự cần truyền giá trị lúc tạo child thread | Không đi theo task submission; reference mutable có thể bị chia sẻ |
| **`ScopedValue` (21 preview)** | Context bất biến, đọc một chiều, scope rõ ràng | Preview; không tự qua arbitrary executor |
| **`StructuredTaskScope` (21 preview)** | Task A/B là child logic của một request, cần join/lỗi/cancel thống nhất | Preview; interrupt không tự dừng mọi tác vụ và không rollback side effect |
| **Virtual thread per task** | Nhiều tác vụ chờ I/O, muốn mô hình blocking đơn giản | Không tự quản lý request context hay quan hệ cha–con |

Phần lớn pipeline có thể bắt đầu bằng **immutable context + truyền tham số tường minh**. Chỉ dùng state ambient khi thư viện/API hoặc cấu trúc lời gọi có lý do cụ thể và ownership được xác định.

## 14. Kiểm thử và các lỗi thường gặp

### Kiểm thử ranh giới

1. **Worker reuse:** dùng `newSingleThreadExecutor`; lần lượt chạy R1 rồi R2, xác minh R2 không thấy context R1.
2. **Đổi thread:** submit từ caller vào worker; kiểm tra trường nào được capture, trường nào không tự propagate.
3. **Nested scope:** bind X, tạm bind Y, cố tình ném exception; sau đó bảo đảm X được phục hồi.
4. **Concurrent tasks:** hai task cùng đọc immutable context; thử thay một field bằng mutable collection để nhận diện shared-state race.
5. **Cancellation:** kiểm tra timeout khi task đang ngủ/chờ I/O; phân biệt thời điểm hủy được yêu cầu và thời điểm tài nguyên thực sự đóng.
6. **Request kết thúc trước:** đảm bảo child task không vô chủ, hoặc được chuyển thành job nền có owner mới.

### Những hiểu lầm cần loại bỏ

| Sai lầm | Cách hiểu đúng |
|---|---|
| “Một request luôn có một thread.” | Request có thể dùng nhiều task/thread; worker có thể xử lý nhiều request. |
| “`ThreadLocal.set` ở thread tiếp nhận sẽ có trên executor worker.” | Ordinary `ThreadLocal` không tự truyền qua `submit`. |
| “`remove()` ở thread cha dọn được context worker.” | Nó chỉ xóa giá trị của thread đang gọi. |
| “`InheritableThreadLocal` theo mỗi task.” | Nó liên quan đến lúc **tạo thread**, không phải mỗi lần submit. |
| “`finally { remove(); }` luôn đúng trong mọi scope.” | Với nested scope phải khôi phục binding ngoài; với task ngoài cùng cần cleanup đầy đủ. |
| “Record chứa Map thì tự thread-safe.” | Record không deep-copy mutable objects. |
| “`thenApply` luôn chạy trên thread cố định.” | Completion stage có thể do thread khác thực thi tùy trạng thái và cách hoàn tất. |
| “Virtual thread tự kế thừa toàn bộ request context.” | Thread-local, inheritance và lifetime vẫn có quy tắc riêng. |
| “ScopedValue tự sang mọi executor.” | Binding theo dynamic scope, child inheritance có cấu trúc qua `StructuredTaskScope`. |
| “`cancel(true)` đã dừng task.” | Đó là yêu cầu hủy/interrupt, phụ thuộc code và I/O thực thi. |
| “Trả response rồi thì mọi task con biến mất.” | Task có thể tiếp tục nếu chưa được join/cancel hoặc giao cho owner riêng. |

## 15. Ghi nhớ

**Context theo request là dữ liệu và quyền sử dụng gắn với vòng đời logic của request, chứ không phải thuộc tính của một OS thread.** Khi task chuyển qua `ExecutorService`, `CompletableFuture` hoặc virtual thread, cần truyền context có chủ đích; khi task kết thúc, cleanup phải đúng thread/scope. Với nhóm task con, cần xác định rõ ai join, xử lý lỗi, cancel và đóng tài nguyên.

Java 21 cung cấp `ThreadLocal` ổn định, virtual threads ổn định và các lựa chọn preview `ScopedValue`/`StructuredTaskScope`. Hãy bắt đầu bằng context bất biến, truyền tường minh, và giới hạn vòng đời task trước khi dùng cơ chế ambient.

## Tài liệu tham khảo

- [Java SE 21 — ThreadLocal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html)
- [Java SE 21 — InheritableThreadLocal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html)
- [Java SE 21 — CompletableFuture API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 446 — Scoped Values (Preview)](https://openjdk.org/jeps/446)
- [Java SE 21 — ScopedValue.Carrier API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ScopedValue.Carrier.html)
- [JEP 453 — Structured Concurrency (Preview)](https://openjdk.org/jeps/453)
- [Java SE 21 — Structured Concurrency](https://docs.oracle.com/en/java/javase/21/core/structured-concurrency.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-007">ThreadLocal khi chạy hàng nghìn virtual threads</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadLocal khi chạy hàng nghìn virtual threads trong Java 21+

`ThreadLocal<T>` cho phép mỗi thread lưu và đọc **giá trị riêng của thread đó**, không phải truyền giá trị qua mọi tham số gọi hàm. Virtual threads từ Java 21 vẫn hỗ trợ `ThreadLocal` như platform threads. Nhưng khi số virtual thread tăng từ vài chục lên hàng nghìn hoặc hàng triệu, những giả định về cache, tuổi thọ và tái sử dụng thread có thể khiến ứng dụng tiêu thụ nhiều bộ nhớ không cần thiết.

Phạm vi bài này là **Java concurrency thuần**, không phụ thuộc Spring. Chúng ta sẽ quan sát context riêng của từng virtual thread, biết cách truyền context qua ranh giới thread, giải phóng binding, nhận diện chi phí bộ nhớ của `withInitial(...)` và lựa chọn thay thế.

## 1. Nguyên lý của ThreadLocal

| API | Hành vi |
|---|---|
| `get()` | Đọc bản sao giá trị của **thread hiện tại**; nếu chưa có, khởi tạo bằng `initialValue()` (mặc định `null`). |
| `set(value)` | Đặt giá trị của **thread hiện tại**; không đặt giá trị cho thread khác. |
| `remove()` | Xóa binding của thread hiện tại; `get()` về sau có thể gọi lại initializer. |
| `ThreadLocal.withInitial(supplier)` | Tạo `ThreadLocal` với supplier khởi tạo **lười**, được gọi riêng trên từng thread khi `get()` lần đầu. |

Biến `static final ThreadLocal<String> TRACE` là một **khóa chung** để tra cứu, nhưng giá trị `TRACE.get()` nằm trong trạng thái từng thread. Nếu thread A gọi `TRACE.set("A")` thì thread B không đương nhiên thấy `"A"`.

Virtual thread vẫn là đối tượng `java.lang.Thread`. Dữ liệu `ThreadLocal` gắn vào **virtual thread đang chạy code**, không gắn vào carrier thread — platform thread tạm thực thi virtual thread.

~~~text
                 static final ThreadLocal<String> TRACE
                                  |
            cùng một khóa, binding riêng theo từng thread
                                  |
         +------------------------+--------------------+
         |                        |                    |
  Virtual Thread V1        Virtual Thread V2      Main Thread
  TRACE = "req-1"          TRACE = "req-2"        TRACE = "caller"
         |
      carrier A
         |
     chờ I/O tương thích -> virtual thread unmount
         |
      carrier B
      TRACE vẫn là "req-1" của V1
~~~

Khi virtual thread unmount khỏi một carrier và resume trên carrier khác, **binding của nó không mất và không biến thành binding của carrier**. Ngược lại, truyền công việc sang **virtual thread khác** có thể làm mất context nếu không truyền rõ ràng.

## 2. Ví dụ runnable: ba virtual thread, ba request ID

Lưu file **`ThreadLocalVirtualDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadLocalVirtualDemo {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private static String nestedCall() {
        return REQUEST_ID.get();
    }

    public static void main(String[] args) throws Exception {
        REQUEST_ID.set("caller");
        try {
            System.out.println("caller=" + REQUEST_ID.get());

            try (ExecutorService executor =
                         Executors.newVirtualThreadPerTaskExecutor()) {

                List<Future<String>> futures = new ArrayList<>();

                for (int id = 1; id <= 3; id++) {
                    final int taskId = id;
                    futures.add(executor.submit(() -> {
                        String before = REQUEST_ID.get();
                        REQUEST_ID.set("req-" + taskId);
                        try {
                            return "task-" + taskId
                                    + " before=" + before
                                    + " nested=" + nestedCall()
                                    + " virtual="
                                    + Thread.currentThread().isVirtual();
                        } finally {
                            REQUEST_ID.remove();
                        }
                    }));
                }

                for (Future<String> future : futures) {
                    System.out.println(future.get());
                }
            }

            System.out.println("caller-after=" + REQUEST_ID.get());
        } finally {
            REQUEST_ID.remove();
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 21+:

~~~bash
javac ThreadLocalVirtualDemo.java
java ThreadLocalVirtualDemo
~~~

Stdout:

~~~text
caller=caller
task-1 before=null nested=req-1 virtual=true
task-2 before=null nested=req-2 virtual=true
task-3 before=null nested=req-3 virtual=true
caller-after=caller
~~~

Điều quan sát được:

1. `main` gán `"caller"` vào `ThreadLocal` của main thread.
2. Mỗi `executor.submit(...)` tạo một **virtual thread mới**. `ThreadLocal` thông thường **không tự kế thừa** giá trị từ thread đã submit, vì vậy `before=null`.
3. Trong từng task, `set("req-N")` tạo binding riêng. `nestedCall()` đọc đúng giá trị đó dù không nhận tham số, vì nó vẫn chạy **trên cùng virtual thread**.
4. `finally { REQUEST_ID.remove(); }` xóa binding của virtual thread, không xóa `"caller"` trên main thread.
5. Các task chạy/hoàn thành theo lịch không xác định; stdout có thứ tự ổn định vì vòng `for` gọi `Future.get()` theo thứ tự danh sách.

**Bài học:** mang context theo call stack **trong một thread** là khả năng của `ThreadLocal`. Muốn đi **sang thread mới**, cần truyền context chủ động.

## 3. Vì sao số lượng virtual thread thay đổi chi phí bộ nhớ?

Trong `Executors.newFixedThreadPool(16)`, nhiều task tái sử dụng 16 platform worker threads. Một `ThreadLocal` khởi tạo cache theo worker có thể tạo khoảng 16 object nếu mỗi worker truy cập. Nhưng `Executors.newVirtualThreadPerTaskExecutor()` tạo **một virtual thread mới trên mỗi task**; các virtual threads không được pool để chạy nhiều task khác nhau. Vì vậy cache theo thread được cấp phát **lặp lại trên từng task**.

Ví dụ một anti-pattern (snippet, không phải chương trình độc lập):

~~~java
static final ThreadLocal<byte[]> PER_THREAD_BUFFER =
        ThreadLocal.withInitial(() -> new byte[64 * 1024]);

void process() {
    byte[] buffer = PER_THREAD_BUFFER.get();
    // Dùng buffer ở đây ...
}
~~~

Nếu **10.000 virtual threads đang sống đồng thời** và mỗi thread đã `get()` buffer 64 KiB, chỉ riêng dữ liệu mảng có kích thước danh nghĩa **625 MiB** (`10_000 × 64 KiB`), chưa tính object header, thread stack, map hoặc object khác. Đây chỉ là phép tính minh họa, **không phải đo peak heap của JVM**. Với task hoàn tất nhanh, GC có thể thu hồi nhiều buffer; với task chờ I/O lâu, rất nhiều buffer có thể còn sống đồng thời.

Phân biệt hai chỉ số:

- **Số lần cấp phát trong toàn bộ thời gian chạy**: tăng theo số virtual thread có gọi `get()` của `ThreadLocal`.
- **Bộ nhớ còn giữ tại một thời điểm**: phụ thuộc bao nhiêu virtual thread còn sống đang tham chiếu buffer, và độ lớn của các object reachable.

`ThreadLocal.withInitial(...)` là **lazy**: chỉ khai báo key không tạo 10.000 object ngay. Nhưng mỗi thread gọi `get()` lần đầu tạo một giá trị mới, kể cả khi thread khác đã có giá trị.

Đặc biệt tránh dùng `ThreadLocal` để cache `SimpleDateFormat`, buffer lớn hoặc connection với kỳ vọng tái sử dụng giữa nhiều task trên **một virtual thread**; điều đó không xảy ra với mô hình thread-per-task. Ưu tiên `DateTimeFormatter` bất biến chia sẻ, quản lý resource theo scope, hoặc pool thật sự cho tài nguyên đắt (ví dụ DB connection pool). Không dùng `ThreadLocal` làm cơ chế giới hạn số request vào downstream.

## 4. Ví dụ runnable: 1.000 task tạo 1.000 buffer

Tạo file **`ThreadLocalAllocationDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadLocalAllocationDemo {
    private static final AtomicInteger CREATED = new AtomicInteger();

    private static final ThreadLocal<byte[]> BUFFER =
            ThreadLocal.withInitial(() -> {
                CREATED.incrementAndGet();
                return new byte[1024];
            });

    public static void main(String[] args) throws Exception {
        int tasks = 1_000;

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        BUFFER.get()[0] = 1;
                    } finally {
                        BUFFER.remove();
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        }

        System.out.println("tasks=" + tasks);
        System.out.println("buffer-initializations=" + CREATED.get());
    }
}
~~~

Chạy:

~~~bash
javac ThreadLocalAllocationDemo.java
java ThreadLocalAllocationDemo
~~~

Stdout:

~~~text
tasks=1000
buffer-initializations=1000
~~~

`CREATED` dùng `AtomicInteger` để đếm chính xác giữa các task đồng thời. Mỗi task được chạy trên virtual thread mới, gọi `BUFFER.get()` một lần, nên supplier được chạy đúng **1.000 lần**. `remove()` dọn binding của từng thread nhưng **không tạo cơ chế tái sử dụng** buffer cho thread khác.

Nếu bỏ `BUFFER.get()`, supplier sẽ không chạy; nếu gọi `remove()` rồi `get()` lần nữa **trên cùng thread**, supplier được gọi lại. Không dùng thời gian chạy của ví dụ nhỏ để kết luận hiệu năng ứng dụng thực tế.

## 5. Phạm vi sống của giá trị và cleanup đúng cách

Một `ThreadLocal<String>` nhỏ chứa trace ID hoặc correlation ID có thể hợp lý khi rất nhiều hàm sâu trong cùng một task cần biết context, nhưng không muốn truyền tham số qua toàn bộ thư viện cũ.

Mẫu task sở hữu binding từ đầu đến cuối:

~~~java
// Snippet minh họa bên trong một Runnable; TRACE đã khai báo.
TRACE.set(traceId);
try {
    handleTask();
} finally {
    TRACE.remove();
}
~~~

Với context lồng nhau, khi code được gọi trong thread **đã có** giá trị, cần tránh xóa nhầm binding của caller:

~~~java
// Snippet: lưu và khôi phục nếu hợp đồng quy định null là "không có".
String previous = TRACE.get();
TRACE.set(traceId);
try {
    handleTask();
} finally {
    if (previous == null) {
        TRACE.remove();
    } else {
        TRACE.set(previous);
    }
}
~~~

Đây chỉ là snippet; `handleTask()` và `TRACE` do code gọi cung cấp. Nếu dùng `ThreadLocal.withInitial(...)`, lệnh `get()` để chụp `previous` có thể tự khởi tạo giá trị; lựa chọn mô hình binding phù hợp với ngữ nghĩa default/absent của ứng dụng.

Dùng `try/finally` để cleanup cả đường đi return, exception, retry, timeout hoặc cancellation. Lợi ích:

- Giải phóng tham chiếu đến dữ liệu (kể cả object graph lớn) ngay khi task không còn cần.
- Đảm bảo một thao tác sau trên **cùng thread** không đọc nhầm context cũ.
- Tương thích an toàn hơn nếu thư viện cũng được dùng trên **platform thread pool**: worker tái sử dụng dễ khiến binding task trước rò sang task tiếp theo nếu quên `remove()`.
- Giữ ranh giới lifecycle của request/task rõ ràng.

**Không khẳng định mọi ThreadLocal trên virtual thread đều rò rỉ vĩnh viễn:** khi virtual thread kết thúc và các giá trị không còn reference khác, chúng có thể được GC thu hồi. Rủi ro chính là **quá nhiều thread đang sống đồng thời giữ object**, hoặc object bị giữ thêm bởi reference ngoài. `remove()` giúp dọn sớm, nhưng không thay thế backpressure, timeout và giới hạn số task outstanding.

`set(null)` không giống `remove()`: `set(null)` đặt một binding có giá trị `null`; `remove()` bỏ binding và lần `get()` sau có thể gọi initializer lại.

## 6. Truyền context qua ranh giới thread

`ThreadLocal` thường **không truyền** giá trị của thread gửi task sang virtual thread. Đây là ví dụ snippet:

~~~java
TRACE.set("trace-A");
try (var executor =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
    var future = executor.submit(() -> TRACE.get());
    // future.get() sẽ là null nếu thread mới chưa đặt TRACE.
}
~~~

Phương án rõ nhất là **capture giá trị bất biến rồi truyền tham số**:

~~~java
// Snippet: traceId và process(String) được khai báo ở nơi gọi.
final String capturedTraceId = traceId;
try (var executor =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
    var future = executor.submit(() -> process(capturedTraceId));
    future.get();
}
~~~

Nếu API kế thừa chỉ đọc `ThreadLocal`, hãy wrap từng task để `set()` **bên trong thread thực thi**, sau đó cleanup bằng `finally`. Nếu wrapper có thể lồng nhau thì phải lưu/khôi phục giá trị trước đó theo hợp đồng của ứng dụng.

Đừng nhầm việc **virtual thread thay carrier** với **công việc chuyển sang virtual thread khác**. Trường hợp đầu không làm đổi `ThreadLocal`. Trường hợp sau cần truyền context rõ ràng. Khi dùng `CompletableFuture`, callbacks, worker pools hoặc executor khác, hãy xác định chính xác đoạn nào chạy trên thread nào trước khi dựa vào context ẩn.

## 7. InheritableThreadLocal kế thừa như thế nào?

`InheritableThreadLocal<T>` khác `ThreadLocal<T>` ở chỗ có thể đưa **giá trị ban đầu** của parent thread vào child thread **khi tạo thread**. Với `Thread.Builder`, hành vi mặc định là kế thừa; có thể tắt bằng `inheritInheritableThreadLocals(false)`.

Tạo file **`InheritableVirtualDemo.java`**:

~~~java
public class InheritableVirtualDemo {
    private static final InheritableThreadLocal<String> CONTEXT =
            new InheritableThreadLocal<>();

    public static void main(String[] args) throws Exception {
        CONTEXT.set("from-parent");
        try {
            Thread inherited = Thread.ofVirtual().start(() ->
                    System.out.println("inherited=" + CONTEXT.get()));
            inherited.join();

            Thread isolated = Thread.ofVirtual()
                    .inheritInheritableThreadLocals(false)
                    .start(() ->
                            System.out.println("isolated=" + CONTEXT.get()));
            isolated.join();
        } finally {
            CONTEXT.remove();
        }
    }
}
~~~

Chạy:

~~~bash
javac InheritableVirtualDemo.java
java InheritableVirtualDemo
~~~

Stdout:

~~~text
inherited=from-parent
isolated=null
~~~

`inherited.join()` đảm bảo dòng đầu được in trước khi tạo và đợi thread thứ hai. Với `inheritInheritableThreadLocals(false)`, child không nhận binding `"from-parent"`. Một child đã tạo **không được tự đồng bộ mọi lần parent đổi giá trị** sau đó.

Cảnh báo quan trọng: `InheritableThreadLocal.childValue(parentValue)` mặc định trả **cùng reference**. Nếu value là một `ArrayList` hoặc một object mutable, parent và child có thể cùng tham chiếu object đó dù mỗi thread có binding riêng. Không có deep copy tự động. Việc tự động kế thừa nhiều binding qua hàng nghìn thread cũng có thể tăng chi phí và mang theo dữ liệu ngoài ý muốn; không chuyển toàn bộ `ThreadLocal` thành `InheritableThreadLocal` để “sửa” mọi vấn đề propagation.

## 8. Thay thế phù hợp với nhu cầu và phiên bản JDK

| Nhu cầu | Lựa chọn | Ưu điểm |
|---|---|---|
| Chỉ vài hàm cần request/trace ID | Tham số hoặc `record RequestContext(...)` bất biến | Dòng dữ liệu rõ, dễ test |
| Thư viện cũ đọc context sâu trong cùng task | `ThreadLocal` nhỏ, cleanup bằng `finally` | Tương thích Java 21 |
| Context chỉ đọc, có lexical scope | `ScopedValue` theo phiên bản JDK | Giới hạn vòng đời binding và giảm chi phí |
| Formatter bất biến dùng lại ở nhiều task | `static final DateTimeFormatter` | Dùng chung an toàn, không cấp phát cache per thread |
| Tài nguyên hữu hạn (DB connection) | Connection pool, `Semaphore`, backpressure | Giới hạn đúng nguồn lực |

`ScopedValue` là **preview API từ JDK 21**, tiếp tục được phát triển trong các preview tiếp theo và **chính thức ở JDK 25** (JEP 506). Cú pháp dưới đây là **snippet dành cho JDK 25+**, không phải chương trình Java 21 chạy mặc định:

~~~java
private static final ScopedValue<String> TRACE = ScopedValue.newInstance();

ScopedValue.where(TRACE, "trace-123").run(() -> {
    System.out.println(TRACE.get());
});
// Bên ngoài run() không còn binding của scope này.
~~~

Trong một ngữ cảnh khai báo hợp lệ, đoạn trên in `trace-123`. `ScopedValue` thích hợp với context **chỉ đọc bên trong một phạm vi gọi hàm**, và có cơ chế kế thừa có kiểm soát với structured concurrency. Không mặc định giả định binding sẽ tự truyền cho **bất kỳ** `executor.submit(...)` nào. Với JDK 21 không dùng preview, ưu tiên truyền tham số hoặc `ThreadLocal` có lifecycle rõ.

## 9. Kiểm tra và tối ưu hệ thống hàng nghìn virtual threads

Trước khi chuyển thư viện sang virtual threads hoặc tối ưu `ThreadLocal`, cần đo những điều thực sự ảnh hưởng tải:

1. Mỗi giá trị `ThreadLocal` giữ **bao nhiêu byte**, và có đang tham chiếu đến buffer/cache/connection/object graph lớn không?
2. Có bao nhiêu **virtual thread đang sống đồng thời**, không chỉ tổng số task xử lý từ lúc ứng dụng khởi động?
3. Supplier của `withInitial(...)` được gọi bao nhiêu lần; có tạo object đắt cho **mỗi task** không?
4. Đường đi thành công, exception, timeout, cancel, retry đã cleanup/restore đúng ngữ cảnh chưa?
5. Có context nào bị mất khi bước qua ranh giới `CompletableFuture`, thread mới hoặc executor khác?
6. DB/network/connection pool có thời hạn và giới hạn rõ; producer có backpressure trước khi submit hàng loạt không?

Dùng JFR, allocation profiler, heap dump, heap usage/GC metrics, số task outstanding và p95/p99 latency dưới tải thực tế. Nếu thấy nhiều byte array/cache object còn sống thông qua virtual-thread context, xác định **ai tạo và ai giữ** giá trị đó trước khi sửa. Thư viện bên thứ ba cũng có thể tạo thread-local cache ngầm.

### Sai lầm thường gặp

| Sai lầm | Hậu quả | Cách sửa |
|---|---|---|
| Tưởng binding theo carrier | Sai về context khi JVM remount | Binding gắn với virtual thread |
| Dùng `withInitial` để cache buffer lớn per thread | Hàng nghìn lần cấp phát và có thể giữ heap lớn | Dùng immutable shared object, cấp phát theo scope |
| Không cleanup context | Giữ data lâu, hoặc rò task trước trên worker pool | `try/finally` và `remove()`/restore |
| Nghĩ `ThreadLocal` tự truyền sang thread mới | Task con đọc `null` | Capture và truyền tham số/wrap task |
| Kế thừa mutable object qua `InheritableThreadLocal` | Parent/child có thể dùng chung object không an toàn | Truyền immutable, kiểm soát inheritance |
| Dùng `set(null)` thay `remove()` | Không tái khởi tạo theo ngữ nghĩa mong đợi | Bỏ binding bằng `remove()` |
| Submit lượng task vô hạn chờ DB | Nhiều virtual thread giữ memory và queue | Backpressure, limit, timeout |
| Dùng API `ScopedValue` JDK 25 trên JDK 21 mặc định | Không biên dịch | Phân biệt preview 21 và finalized 25 |

## 10. Tự kiểm tra

**Câu 1:** Virtual thread chuyển từ carrier A sang B. `ThreadLocal` có đổi thành giá trị của B không? **Không.** Dữ liệu thuộc virtual thread.

**Câu 2:** `ThreadLocal.withInitial(() -> new byte[65536])` được `get()` trên 10.000 virtual threads. Có một buffer dùng chung không? **Không.** Có thể tạo 10.000 buffer riêng; peak heap còn phụ thuộc số virtual thread/buffer sống cùng lúc.

**Câu 3:** Một virtual thread kết thúc nhưng chưa `remove()`. Có luôn rò vĩnh viễn không? **Không.** Giá trị có thể được GC thu hồi khi không còn reference giữ. Cleanup sớm vẫn nên làm.

**Câu 4:** Main thread `TRACE.set("A")` rồi `executor.submit(() -> TRACE.get())`. Task mới có thấy `"A"` không? **Không**, nếu `TRACE` là `ThreadLocal` thường và task chưa tự set.

**Câu 5:** Có nên chuyển mọi context thành `InheritableThreadLocal` không? **Không.** Thừa kế chỉ xảy ra lúc tạo thread và mutable value có thể bị chia sẻ reference.

**Thực hành:** Thử đổi ví dụ allocation thành 100, 1.000 và 10.000 task; quan sát `CREATED`. Thay cache mảng bằng `DateTimeFormatter` dùng chung rồi phân tích khác biệt về số lần khởi tạo. Thêm đường đi exception vào ví dụ request ID để xác nhận `finally` luôn cleanup.

## 11. Ghi nhớ

- `ThreadLocal` hoạt động trên virtual threads Java 21+, và giá trị gắn với **virtual thread**, không gắn với carrier.
- Rất nhiều virtual threads có thể dẫn đến **rất nhiều binding và bản sao object**. Không dùng `ThreadLocal` như cache tái sử dụng giữa nhiều task có virtual thread riêng.
- `withInitial` khởi tạo lười theo từng thread; `remove()` bỏ binding của thread hiện tại. Quản lý scope bằng `try/finally`.
- `ThreadLocal` thông thường không propagate sang thread khác. `InheritableThreadLocal` chỉ thừa kế lúc tạo child thread, cần thận trọng với mutable value.
- Ưu tiên tham số/record bất biến khi có thể; `ScopedValue` được finalized ở JDK 25, còn JDK 21 là preview.
- Đo allocation, heap/GC, số virtual thread đồng thời và tài nguyên downstream trước khi tối ưu.

### Tài liệu chính thức

- Java SE 21 `ThreadLocal` API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html
- Java SE 21 `Thread` và inheritance: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html
- Oracle Java 21 Virtual Threads: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
- JEP 444 — Virtual Threads: https://openjdk.org/jeps/444
- JEP 506 — Scoped Values (JDK 25): https://openjdk.org/jeps/506


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-008">Scoped Values (preview ở Java 21) để chia sẻ context bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Scoped Values (preview ở Java 21) để chia sẻ context bất biến

## 1. Bài toán: truyền ngữ cảnh qua nhiều lời gọi mà không làm bẩn chữ ký hàm

Trong Java, cách rõ ràng nhất để chuyển dữ liệu là truyền tham số:

```java
void handle(RequestContext context) {
    validate(context);
}
```

Tuy nhiên, khi một hàm xử lý đi qua nhiều tầng thư viện, có thể hàng loạt phương thức trung gian phải nhận rồi chuyển tiếp một giá trị mà chính chúng không dùng: mã định danh tác vụ, thông tin người dùng đã xác thực, locale hay thông số truy vết. `ThreadLocal` giải quyết việc truyền ngầm đó nhưng mang các vấn đề về khả năng thay đổi giá trị từ bất cứ callee nào, vòng đời cần `remove()` và chi phí khi có rất nhiều virtual threads.

**`ScopedValue<T>`** cho phép hàm gọi (*caller*) ràng buộc một khóa với một value **trong phạm vi thực thi được giới hạn**, rồi các hàm được gọi trực tiếp hoặc gián tiếp có thể đọc value qua khóa đó. Khi phạm vi kết thúc, binding tự động kết thúc hoặc khôi phục binding bên ngoài. Đây là mô hình truyền ngữ cảnh **một chiều, hướng đọc**: callee chỉ đọc binding đã được caller cung cấp, trừ khi chính callee chủ động mở một scope con với binding khác.

Bài tập trung vào **Java concurrency và ngữ cảnh luồng nói chung**, đặc biệt các tác vụ dùng virtual threads. Cơ chế không phụ thuộc Spring.

### Tình trạng API theo phiên bản Java

| Phiên bản | Trạng thái `ScopedValue` | Hệ quả thực hành |
| --- | --- | --- |
| Java 21 | Preview lần đầu, JEP 446 | Cần `--enable-preview` khi biên dịch và chạy; dùng API đúng tài liệu Java 21. |
| Java 22–24 | Các vòng preview tiếp theo | API có thể thay đổi; cần đọc JavaDoc đúng phiên bản. |
| Java 25 trở lên | API chuẩn, JEP 506 | Không cần bật preview riêng cho `ScopedValue`. |
 
Trong bài, mọi ví dụ được đánh dấu **Java 21** đều áp dụng API preview của JDK 21 và dùng lệnh chạy chính xác. Những đoạn ghi **Java 25+** dùng API chính thức; không trộn tùy tiện các phiên bản trong một ví dụ.

## 2. Cơ chế: khóa, binding và dynamic scope

Giả sử khai báo:

```java
private static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
```

`TRACE_ID` chỉ là **khóa (key)** dùng để tra giá trị. Nó không phải một biến String được cập nhật toàn cục. Khi chạy:

```java
ScopedValue.runWhere(TRACE_ID, "REQ-42", () -> handleRequest());
```

JDK 21 sẽ thực thi `handleRequest()` với `TRACE_ID` được bind thành `"REQ-42"` ở thread hiện tại. Mọi lời gọi sâu hơn trong chuỗi gọi đó có quyền truy cập khóa đều có thể gọi `TRACE_ID.get()`. Khi `runWhere` trả về hoặc ném exception, binding được gỡ và binding trước đó (nếu có) được khôi phục. Không cần `try/finally/remove` cho việc cleanup binding.

Phạm vi này là **dynamic scope**: được xác định bằng các phương thức thực sự chạy bên trong lời gọi ràng buộc, kể cả callback, chứ không đơn giản là các dấu ngoặc của một khối lexical bất kỳ.

Một minh họa đường đi:

```text
Thread hiện tại
  |
  +-- TRACE_ID chưa được bind
  |
  +-- runWhere(TRACE_ID, "REQ-42", () -> service())
  |     |
  |     +-- service()
  |           |
  |           +-- repository()  -> TRACE_ID.get() == "REQ-42"
  |           |
  |           +-- callback()    -> TRACE_ID.get() == "REQ-42"
  |
  +-- kết thúc scope: TRACE_ID trở về trạng thái trước đó
```

**Không đồng nhất binding bất biến với object bất biến.** API không cung cấp thao tác `TRACE_ID.set(...)` để sửa binding hiện có, nhưng nếu bind một `ArrayList` hay object có setter, code nhận được tham chiếu vẫn có thể thay đổi **bên trong object**. Muốn thật sự dùng “context bất biến”, hãy bind `String`, record chứa các giá trị bất biến, hoặc object được thiết kế immutable/defensive copy.

## 3. Những API chính trong Java 21

| API | Tác dụng |
| --- | --- |
| `ScopedValue.newInstance()` | Tạo key mới, ban đầu chưa bind trong mọi thread. |
| `ScopedValue.runWhere(key, value, runnable)` | Bind value và chạy `Runnable` trong phạm vi đó. |
| `ScopedValue.callWhere(key, value, callable)` | Bind value rồi trả về kết quả của `Callable`; có thể lan truyền checked exception. |
| `ScopedValue.getWhere(key, value, supplier)` | Bind value rồi trả về kết quả của `Supplier`. |
| `ScopedValue.where(key, value).run(runnable)` | Tạo `Carrier` của một hoặc nhiều binding và thực thi với chúng. |
| `ScopedValue.where(k1,v1).where(k2,v2).call(callable)` | Bind nhiều key trong một scope. |
| `key.get()` | Lấy value đang bind; ném `NoSuchElementException` nếu chưa bind. |
| `key.isBound()` | Kiểm tra key có binding trong thread hiện tại không. |
| `key.orElse(fallback)` | Lấy binding hoặc fallback nếu chưa bind. |
| `key.orElseThrow(supplier)` | Ném exception được cung cấp nếu key chưa bind. |

Trên Java 21, `ScopedValue` và `ScopedValue.Carrier` đều là **preview API**, phải bật preview cho cả compile và run. `Carrier` là tập mapping bất biến: nối `where(...)` tạo carrier mới; không làm thay đổi carrier đã có.

**Bảo mật và thiết kế API:** chỉ những đoạn code truy cập được đối tượng khóa mới có thể gọi `get()`. Vì vậy, khóa thường được giữ trong trường `private static final` và chỉ mở các helper đọc phù hợp với nhu cầu. Công khai key ra toàn ứng dụng sẽ tăng các vị trí có thể truy cập context.

## 4. Ví dụ đầy đủ Java 21: binding, gọi sâu, rebinding và khôi phục

Lưu dưới tên `ScopedValueDemo.java`:

```java
public class ScopedValueDemo {
    private static final ScopedValue<Context> CONTEXT =
            ScopedValue.newInstance();

    record Context(String traceId, String userId) {}

    static String readInDeepMethod() {
        return CONTEXT.get().traceId();
    }

    static String service() {
        return "trace=" + readInDeepMethod();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Before: " + CONTEXT.isBound());

        String result = ScopedValue.where(
                CONTEXT, new Context("REQ-42", "an")).call(() -> {

            System.out.println("Outer: " + service());

            String nested = ScopedValue.where(
                    CONTEXT, new Context("REQ-99", "an"))
                    .call(ScopedValueDemo::service);

            System.out.println("Inner: " + nested);
            System.out.println("Restored: " + service());
            return "ok";
        });

        System.out.println("Result: " + result);
        System.out.println("After: " + CONTEXT.isBound());
    }
}
```

Dùng **JDK 21** để biên dịch và chạy:

```bash
javac --enable-preview --release 21 -encoding UTF-8 ScopedValueDemo.java
java --enable-preview ScopedValueDemo
```

Biên dịch có thể phát thông báo rằng chương trình dùng preview API; đó là thông báo dự kiến. **Stdout từ chương trình**:

```text
Before: false
Outer: trace=REQ-42
Inner: trace=REQ-99
Restored: trace=REQ-42
Result: ok
After: false
```

Giải thích:

1. Khi chưa vào scope, `CONTEXT.isBound()` là `false`.
2. `Carrier.call(...)` bind record cho thread hiện tại, các hàm `service()` và `readInDeepMethod()` đọc được `REQ-42` dù không có tham số context.
3. Scope lồng nhau **rebind** cùng key thành `REQ-99`; nó không sửa record ngoài.
4. Khi scope con kết thúc, `REQ-42` được khôi phục. Khi scope ngoài kết thúc, key lại không bind.
5. Kết quả của `Callable` được trả về từ `call`. Không có bước xóa thủ công.

Record `Context` dùng các thành phần `String` bất biến; nếu record giữ `List`, `Map` hoặc các object mutable khác, cần cân nhắc `List.copyOf`, `Map.copyOf` hay một dạng defensive copy thích hợp.

## 5. Exception cũng kết thúc scope đúng cách

Khi tác vụ bên trong scope ném exception, cơ chế ràng buộc vẫn được tháo/khôi phục. Ví dụ Java 21, lưu `ScopedValueFailureDemo.java`:

```java
public class ScopedValueFailureDemo {
    private static final ScopedValue<String> TRACE =
            ScopedValue.newInstance();

    public static void main(String[] args) {
        System.out.println("Before: " + TRACE.isBound());

        try {
            ScopedValue.runWhere(TRACE, "REQ-error", () -> {
                System.out.println("During: " + TRACE.get());
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        System.out.println("After: " + TRACE.isBound());
    }
}
```

```bash
javac --enable-preview --release 21 -encoding UTF-8 ScopedValueFailureDemo.java
java --enable-preview ScopedValueFailureDemo
```

```text
Before: false
During: REQ-error
Caught: boom
After: false
```

Việc khôi phục binding được thực hiện bởi cơ chế scope, kể cả lúc exception được ném. Điều đó giải quyết một lỗi thường gặp ở `ThreadLocal`: bỏ quên `remove()` sau một nhánh trả về sớm hoặc exception. Tuy nhiên, **ScopedValue không tự động đóng tài nguyên** mà value trỏ đến. Nếu value chứa kết nối, file hay tài nguyên `AutoCloseable`, cần quản lý việc đóng riêng bằng `try-with-resources` hoặc lifecycle rõ ràng.

## 6. Khả năng nhìn thấy giữa các thread và structured concurrency

Binding của `ScopedValue` theo **thread đang chạy**, không phải một global variable. Một thread khác chạy độc lập sẽ không tự nhiên thấy binding của thread hiện tại.

**Đặc điểm quan trọng:** `ScopedValue` có thể được **kế thừa có cấu trúc** bởi các thread con được tạo bằng `StructuredTaskScope.fork(...)` bên trong scope phù hợp. Cấu trúc parent-child cho phép các child threads cùng chia sẻ **tham chiếu tới context bất biến**, không cần sao chép một ThreadLocalMap đầy đủ cho mỗi child.

Trong **Java 21**, `StructuredTaskScope` cũng là **preview API** (JEP 453). Cần bật `--enable-preview`. Đoạn sau dùng chính kiểu API Java 21; lưu `ScopedValueChildrenDemo.java`:

```java
import java.util.concurrent.StructuredTaskScope;

public class ScopedValueChildrenDemo {
    private static final ScopedValue<String> TRACE =
            ScopedValue.newInstance();

    static String work(String name) {
        return name + "=" + TRACE.get();
    }

    public static void main(String[] args) throws Exception {
        String joined = ScopedValue.where(TRACE, "REQ-7").call(() -> {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                var first = scope.fork(() -> work("first"));
                var second = scope.fork(() -> work("second"));

                scope.join().throwIfFailed();
                return first.get() + ", " + second.get();
            }
        });

        System.out.println(joined);
        System.out.println("Parent bound: " + TRACE.isBound());
    }
}
```

```bash
javac --enable-preview --release 21 -encoding UTF-8 ScopedValueChildrenDemo.java
java --enable-preview ScopedValueChildrenDemo
```

```text
first=REQ-7, second=REQ-7
Parent bound: false
```

Mặc dù hai child có thể hoàn thành theo thứ tự bất kỳ, kết quả cuối ghép `first.get()` trước `second.get()` nên stdout xác định. `scope.join()` chờ các child hoàn tất và `throwIfFailed()` báo lỗi khi có task thất bại. Khi `try-with-resources` kết thúc, phạm vi task được đóng. Hai child đọc được cùng giá trị `REQ-7` từ binding mà scope đã nhận tại thời điểm tạo; key không bị set lại ở từng child.

**Giới hạn cần hiểu:** đây là cơ chế inheritance gắn với *structured task scope*, không có nghĩa mọi cách khởi chạy thread đều được truyền binding. Cụ thể, đừng mặc định `Thread.ofVirtual().start(...)` hoặc một executor bất kỳ tự nhận context từ thread submit task. Nếu cần context trong tác vụ độc lập, bind trực tiếp trong tác vụ đó hoặc tổ chức tác vụ bằng phạm vi có cấu trúc phù hợp.

Khi value là immutable, nhiều child có thể cùng tham chiếu tới nó an toàn theo cách đọc. Nếu value là mutable, việc kế thừa binding **không biến object thành thread-safe**; cần đồng bộ truy cập hoặc chọn object bất biến.

### Vì sao tính có cấu trúc quan trọng?

Nếu thread con tiếp tục chạy sau khi scope của cha kết thúc, ý nghĩa của context trở nên khó xác định. `StructuredTaskScope` ràng buộc vòng đời các task con với block cha, giúp việc truyền ngữ cảnh và dọn binding có điểm bắt đầu/kết thúc rõ ràng. Đừng tách việc truyền binding khỏi thiết kế lifetime và xử lý lỗi của các child tasks.

Lưu ý phiên bản: API `StructuredTaskScope` tiếp tục thay đổi trong những bản Java sau; đoạn trên là **Java 21 preview**, không phải mã bảo đảm biên dịch nguyên xi trên mọi JDK mới hơn.

## 7. So sánh ScopedValue và ThreadLocal

| Tiêu chí | `ScopedValue` | `ThreadLocal` |
| --- | --- | --- |
| Mục đích tốt nhất | Truyền context **hướng đọc**, trong phạm vi rõ ràng | State theo thread cần `get/set/remove` linh hoạt |
| Thiết lập giá trị | Caller bind trong `run/call` | Bất kỳ mã có key có thể `set` |
| Sửa binding tại callee | Không có setter thay binding đang dùng; có thể mở scope con để rebind tạm thời | Có thể `set` rồi ảnh hưởng các lời gọi tiếp theo trong thread |
| Vòng đời | Tự khôi phục khi scope kết thúc | Tiếp tục gắn với thread cho tới khi `remove` hoặc thread kết thúc |
| Đọc ngoài scope | `get` ném `NoSuchElementException`; `isBound/orElse` an toàn | `get` trả `null` hoặc giá trị initial |
| Truyền sang child | Có cơ chế inheritance qua structured task scope | `ThreadLocal` thường không kế thừa; `InheritableThreadLocal` có quy tắc khác |
| Nhiều virtual threads | Phù hợp để đọc context immutable chia sẻ theo scope | Có thể tốn bộ nhớ nếu mỗi virtual thread giữ dữ liệu riêng lớn |
| Tính bất biến của value | **Không tự cưỡng chế deep immutability** | Không cưỡng chế bất biến |
| Mức độ sẵn có | Preview Java 21–24, chính thức từ Java 25 | API ổn định lâu đời |

Đánh giá hiệu năng phải dựa trên workload thật. `ScopedValue` thường ít chi phí hơn trong tình huống truyền context chỉ đọc tới nhiều virtual threads có cấu trúc, nhưng **không nên coi nó là giải pháp tăng tốc tự động** cho mọi thuật toán.

## 8. Thiết kế context bất biến và ranh giới truy cập

Một thiết kế thuận tiện là gom những dữ liệu cùng vòng đời vào record:

```java
import java.util.Map;

record RequestContext(String traceId, String principal,
                      Map<String, String> tags) {
    RequestContext {
        tags = Map.copyOf(tags);
    }
}
```

Đây là **đoạn minh họa kiểu dữ liệu**, không phải chương trình Java độc lập. Việc `Map.copyOf` tạo một map không cho phép sửa thông qua API map; ở đây cả khóa và giá trị đều là `String` nên không có object mutable nằm sâu bên trong. Nếu trường là object mutable khác, record chỉ làm bất biến **tham chiếu**, không làm bất biến object.

Một số nguyên tắc:

- Dữ liệu trong context nên **nhỏ, cần thiết, bất biến**: trace ID, correlation ID, locale, tham số truy vấn đọc-only đã chuẩn hóa.
- Không đưa cả request body, buffer lớn, cache hay kết nối mở vào context nếu không có lý do và vòng đời chính xác.
- Trường khóa `ScopedValue` nên càng ít nơi truy cập càng tốt (thường `private static final`), cung cấp helper đọc nếu cần để giảm sự phụ thuộc ngầm.
- Nếu xử lý chỉ đi qua một hoặc hai hàm, **truyền tham số tường minh** thường dễ hiểu, dễ test hơn.
- Không nhầm việc “không thể set lại binding tại callee” với cơ chế sandbox hay quyền truy cập bảo mật hoàn chỉnh; code có key vẫn có thể đọc value đang bind.

## 9. Các lỗi thường gặp

### Lỗi 1: gọi get ngoài binding

`TRACE.get()` khi key chưa bind sẽ ném `NoSuchElementException`. Hãy tạo scope trước khi gọi hoặc dùng `TRACE.isBound()`, `TRACE.orElse(fallback)` hay `TRACE.orElseThrow(...)` tùy hợp đồng API.

### Lỗi 2: tưởng rằng rebind làm thay đổi giá trị cha

Trong scope lồng nhau, rebind chỉ hiệu lực **cho phạm vi con**. Khi con kết thúc, cha đọc được value cũ. Không dùng rebind để truyền giá trị trả ngược từ callee lên caller; nên return giá trị hoặc dùng kênh kết quả rõ ràng.

### Lỗi 3: bind object mutable rồi coi là bất biến

Hai child threads có thể cùng thấy tham chiếu `ArrayList` hay `HashMap` và sửa đồng thời, dẫn tới data race. Dùng immutable value/defensive copy hoặc đồng bộ nếu thực sự cần chia sẻ mutable state.

### Lỗi 4: giả định thread nào cũng kế thừa context

Binding là per-thread. Cơ chế kế thừa có cấu trúc có các giới hạn cụ thể; một task qua executor độc lập có thể không nhận giá trị. Đừng phụ thuộc context ngầm khi mô hình concurrency không cam kết propagation đó.

### Lỗi 5: dùng cú pháp Java 25 khi đang chạy JDK 21

Java 21 có API preview riêng và cần cờ preview:

```bash
javac --enable-preview --release 21 ScopedValueDemo.java
java --enable-preview ScopedValueDemo
```

Nếu máy chạy JDK 25, không dùng `--release 21` để “giả lập Java 21 preview” từ compiler JDK 25: preview được gắn với phiên bản trình biên dịch đang chạy. Để xác minh API Java 21 phải chạy **JDK 21** thực sự.

### Lỗi 6: lạm dụng context ẩn

Nếu mọi logic dựa vào nhiều khóa bí mật, dữ liệu trở nên khó truy vết và test. Gom các trường liên quan thành một record rõ nghĩa và dành `ScopedValue` cho thông tin thực sự xuyên nhiều tầng gọi.

## 10. Chuyển từ ThreadLocal sang ScopedValue khi phù hợp

Một API kiểu `ThreadLocal` thường xuất hiện như sau:

```java
private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

static void handle(String id) {
    TRACE.set(id);
    try {
        process();
    } finally {
        TRACE.remove();
    }
}
```

Các đoạn trong phần này **là minh họa phương thức**; `process()` do ứng dụng cung cấp. Nếu dữ liệu chỉ để đọc và không có nhánh nào thực sự cần `TRACE.set()` giữa đường, với Java 21 preview có thể viết:

```java
private static final ScopedValue<String> TRACE = ScopedValue.newInstance();

static void handle(String id) {
    ScopedValue.runWhere(TRACE, id, () -> process());
}
```

Lợi ích là caller quyết định binding trong một scope rõ ràng; callee `process()` chỉ đọc `TRACE.get()` và cơ chế tự khôi phục khi khối chạy kết thúc.

**Không thay thế máy móc** nếu code cũ chủ động cập nhật state trong suốt vòng đời thread, dựa vào giá trị sống ngoài scope, có tương tác với thư viện không hỗ trợ, hoặc yêu cầu truyền context qua task bất đồng bộ không có cấu trúc. Cần xác định phạm vi xử lý, ownership và lifecycle trước khi chuyển.

Khi dùng Java 25+, API `ScopedValue.where(KEY, value).run(...)` vẫn sẵn có mà không cần bật preview. Các phương thức tiện ích preview như `runWhere` trong tài liệu Java 21 không nên được coi là API chung ổn định cho mọi JDK.

## 11. Câu hỏi tự kiểm tra

**Hỏi:** `ScopedValue` có khác một biến `static final String` như thế nào?  
**Đáp:** `static final String` là một giá trị dùng chung cố định; `ScopedValue` là một **khóa** có thể nhận binding khác nhau theo thread và scope, tự khôi phục khi kết thúc scope.

**Hỏi:** Scope bên ngoài bind `A`, scope con bind `B`, sau khi scope con kết thúc đọc được gì?  
**Đáp:** `A`; binding con `B` chỉ tồn tại trong scope con.

**Hỏi:** Nếu task ném exception trước khi kết thúc, có cần gọi `remove()` không?  
**Đáp:** Không; scope tự khôi phục binding. Resource chứa trong value vẫn cần được đóng riêng.

**Hỏi:** Vì sao một record không nhất thiết có deep immutability?  
**Đáp:** Record chỉ ngăn gán lại field; field có thể trỏ đến object mutable. Muốn chia sẻ an toàn cần chọn immutable types hoặc defensive copy phù hợp.

**Hỏi:** Dùng `Thread.ofVirtual().start(...)` trong scope cha có tự truyền `ScopedValue` không?  
**Đáp:** Không được mặc định như vậy. Inheritance của scoped values gắn với mô hình structured task scope, không phải mọi API tạo virtual threads.

**Hỏi:** Java 21 phải chạy lệnh gì với đoạn code sử dụng `ScopedValue`?  
**Đáp:** `javac --enable-preview --release 21 ...` và `java --enable-preview ...`, trên **JDK 21**.

**Ghi nhớ:** `ScopedValue` truyền **ngữ cảnh hướng đọc** trong phạm vi thực thi có điểm kết thúc rõ ràng; binding có thể lồng nhau, tự khôi phục và truyền cho thread con khi dùng cấu trúc phù hợp. Để việc chia sẻ thực sự an toàn, bind **context nhỏ và bất biến**, hiểu rõ phiên bản Java cũng như vòng đời tác vụ.

## Tài liệu chính thức

- [OpenJDK JEP 446 — Scoped Values (Preview), Java 21](https://openjdk.org/jeps/446)
- [Oracle Java 21 — ScopedValue API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ScopedValue.html)
- [Oracle Java 21 — ScopedValue.Carrier API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ScopedValue.Carrier.html)
- [OpenJDK JEP 453 — Structured Concurrency (Preview), Java 21](https://openjdk.org/jeps/453)
- [OpenJDK JEP 506 — Scoped Values, Java 25](https://openjdk.org/jeps/506)
- [Oracle Java 25 — ScopedValue API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-009">Ưu tiên tham số tường minh khi luồng dữ liệu đơn giản</a>

<details>
<summary>Xem chi tiết</summary>

# Ưu tiên tham số tường minh khi luồng dữ liệu đơn giản

## 1. Bài toán: dữ liệu ngữ cảnh đi theo tác vụ

Trong chương trình Java chạy đồng thời, một tác vụ thường cần một số dữ liệu đi kèm: `requestId`, `tenantId`, tên người dùng, ngôn ngữ, deadline hoặc thông tin định tuyến. Dữ liệu này gọi chung là **ngữ cảnh thực thi** (*execution context*). Khi chuyển công việc sang một thread khác, chương trình phải quyết định cách đưa ngữ cảnh đến nơi xử lý.

Có hai kiểu thiết kế phổ biến:

- **Tham số tường minh:** hàm cần dữ liệu nào thì nhận dữ liệu đó qua tham số; caller chịu trách nhiệm cung cấp.
- **Ngữ cảnh ngầm theo thread:** hàm gọi `ThreadLocal.get()` hoặc đọc một cơ chế context khác dù chữ ký không cho biết nó phụ thuộc vào dữ liệu gì.

**Nguyên tắc:** khi dữ liệu chỉ đi qua một vài phương thức, hãy **ưu tiên tham số tường minh**. Nó giữ phụ thuộc ở ngay chữ ký phương thức, dễ kiểm thử và không phụ thuộc tác vụ đang chạy trên platform thread, executor worker hay virtual thread.

Ví dụ cách thiết kế dễ đọc:

~~~java
int calculateFee(int amount, boolean premium) {
    return premium ? 0 : amount / 20;
}
~~~

Người gọi thấy rõ hai đầu vào. So với cách dựa vào ngữ cảnh ẩn:

~~~java
int calculateFee(int amount) {
    boolean premium = PREMIUM_CUSTOMER.get();
    return premium ? 0 : amount / 20;
}
~~~

Đây là hai **mảnh phương thức minh họa**, không phải lớp Java độc lập. `PREMIUM_CUSTOMER` ở cách sau được giả định là một `ThreadLocal<Boolean>`; nếu chưa có giá trị, `get()` có thể trả về `null` và unboxing có thể ném `NullPointerException`. Kể cả khi khắc phục null, hợp đồng rằng caller phải thiết lập thread context trước khi gọi vẫn bị che giấu.

Không có quy tắc bắt buộc phải bỏ hoàn toàn `ThreadLocal`. Lời khuyên ở đây áp dụng khi **luồng dữ liệu đơn giản**, tức các giá trị và đường truyền của chúng đủ ngắn, dễ biểu diễn trực tiếp.

## 2. Vì sao tham số rõ ràng thường tốt hơn?

Hãy xét chuỗi xử lý:

~~~text
handleRequest(context, amount)
    -> calculateTotal(amount)
    -> formatResponse(context, total)
~~~

`calculateTotal` chỉ tính số học, còn `formatResponse` dùng mã request và tenant. Mỗi phương thức nhận đúng dữ liệu nó cần, thay vì lặng lẽ đọc trạng thái của thread.

Lợi ích cụ thể:

1. **Phụ thuộc hiện rõ:** nhìn chữ ký là biết một hàm cần `requestId` hay `tenantId`.
2. **Kiểm thử độc lập:** gọi hàm với dữ liệu mẫu, không phải khởi tạo/giải phóng thread context trong các bước thiết lập test.
3. **Dễ truyền qua ranh giới bất đồng bộ:** closure giữ một giá trị và gọi `process(context)` trên thread bất kỳ.
4. **Giảm rò rỉ dữ liệu giữa các yêu cầu:** thread pool có thể tái sử dụng worker, nhưng tham số của lần gọi trước không tự tồn tại trong lần gọi sau.
5. **Dễ refactor:** di chuyển tác vụ giữa thread thường, virtual thread hoặc executor không làm mất điều kiện thiết lập ngữ cảnh ngầm.
6. **Dễ kiểm soát vòng đời:** đối tượng context sống theo tham chiếu mà tác vụ giữ; không còn một slot `ThreadLocal` cần dọn dẹp sau mỗi lần chạy.

Cũng có đánh đổi: khi cùng một thông tin phải truyền qua hàng chục tầng không sử dụng nó, chữ ký có thể trở nên rườm rà. Khi đó mới cân nhắc thiết kế đối tượng ngữ cảnh hoặc cơ chế ngữ cảnh có phạm vi rõ ràng. Việc tránh thêm hai tham số vào hai phương thức không đủ lý do để đưa vào thread-local.

## 3. Ví dụ hoàn chỉnh với Java 17+: ngữ cảnh bất biến

Dùng `record` để biểu diễn giá trị ngữ cảnh gồm các `String` bất biến. Các hàm truyền dữ liệu cần thiết theo tuyến xử lý, không đọc trạng thái của thread.

Lưu vào **`ExplicitContextDemo.java`**:

~~~java
import java.util.Objects;

public class ExplicitContextDemo {
    record RequestContext(String requestId, String tenantId) {
        RequestContext {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(tenantId, "tenantId");
        }
    }

    static int calculateTotal(int amount, int shippingFee) {
        return amount + shippingFee;
    }

    static String formatResult(RequestContext context, int total) {
        return context.requestId() + " / "
                + context.tenantId() + " -> " + total;
    }

    static String handleOrder(
            RequestContext context, int amount, int shippingFee) {
        int total = calculateTotal(amount, shippingFee);
        return formatResult(context, total);
    }

    public static void main(String[] args) {
        RequestContext context = new RequestContext("req-42", "tenant-A");
        System.out.println(handleOrder(context, 150, 15));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ExplicitContextDemo.java
java ExplicitContextDemo
~~~

Stdout chính xác:

~~~text
req-42 / tenant-A -> 165
~~~

Phân tích:

- `handleOrder` và `formatResult` cần ngữ cảnh, vì vậy chữ ký thể hiện nó.
- `calculateTotal` không cần ngữ cảnh, nên **không** nhận tham số context chỉ để chuyển tiếp.
- `RequestContext` có thể được chia sẻ an toàn trong ví dụ vì các trường `String` là bất biến. **Record không bảo đảm bất biến sâu**: nếu thêm `List` hoặc `Map` mutable, nên tạo snapshot hoặc defensive copy.
- Không cần giả định về thread đang chạy; kết quả chỉ phụ thuộc dữ liệu đầu vào và logic của hàm.
- Đối với các giá trị chỉ dùng riêng lẻ, có thể truyền `requestId` hoặc `tenantId` trực tiếp, thay vì luôn gói cả context.

Một test đơn giản có thể gọi `handleOrder(new RequestContext(...), 150, 15)`, so sánh chuỗi trả về và kết thúc. Không cần biết test runner dùng thread nào hoặc có chạy các test song song.

## 4. `ThreadLocal` giải quyết vấn đề khác và có rủi ro riêng

**ThreadLocal** là lựa chọn khi một API legacy cần đọc trạng thái của **thread đang chạy** từ nhiều tầng gọi hàm; nó không tự biến state này thành dữ liệu của một request. Trong worker pool, task A có thể đặt context rồi kết thúc, còn task B chạy trên cùng worker đọc lại dữ liệu cũ nếu không cleanup.

~~~java
// Snippet có ThreadLocal<String> CURRENT và method handle():
CURRENT.set(requestId);
try {
    handle();
} finally {
    CURRENT.remove();
}
~~~

Đoạn trên cần được đặt tại **điểm sở hữu context của tác vụ**, sau đó cần xem có scope lồng nhau phải **restore previous** hay không. Nếu lời gọi logic chuyển qua executor/thread khác, ThreadLocal không tự propagate theo việc submit; vẫn phải capture và bind bên worker hoặc truyền tham số tường minh.

Bài này ưu tiên **truyền trực tiếp biến/record bất biến** vì dependency dễ kiểm tra và không cần cleanup ngầm. Quy tắc set/get/remove, lỗi rò rỉ trên pooled worker và các wrapper propagation đã có bài chuyên sâu riêng.

## 5. Chuyển context sang executor bằng closure

Khi một hàm giao việc sang executor, hãy lấy snapshot ngữ cảnh **tại ranh giới giao việc**, rồi capture nó trong `Runnable` hoặc `Callable`. Tác vụ nhận dữ liệu qua tham số:

~~~text
caller: contextA, contextB
    |
    +-- submit(() -> process(contextA))
    |                    |
    |                    +-- worker bất kỳ
    |
    +-- submit(() -> process(contextB))
                         |
                         +-- worker bất kỳ
~~~

Lưu **`ExplicitExecutorDemo.java`** (Java 17+):

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExplicitExecutorDemo {
    record RequestContext(String requestId, String tenantId) {}

    static String process(RequestContext context, int amount) {
        return context.requestId()
                + " [" + context.tenantId() + "]: " + amount;
    }

    public static void main(String[] args) throws Exception {
        RequestContext first = new RequestContext("req-A", "tenant-X");
        RequestContext second = new RequestContext("req-B", "tenant-Y");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> f1 =
                    executor.submit(() -> process(first, 100));
            Future<String> f2 =
                    executor.submit(() -> process(second, 200));

            System.out.println(f1.get());
            System.out.println(f2.get());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ExplicitExecutorDemo.java
java ExplicitExecutorDemo
~~~

Stdout chính xác:

~~~text
req-A [tenant-X]: 100
req-B [tenant-Y]: 200
~~~

Các task có thể kết thúc ở thứ tự bất kỳ, nhưng chương trình gọi `f1.get()` rồi `f2.get()` nên thứ tự hai dòng in ra được xác định. `Future.get()` cũng giúp caller nhận lỗi task thông qua `ExecutionException`.

`first` và `second` là biến local effectively final, nên lambda được phép capture. Hai record này chứa `String` bất biến. Nếu thay bằng object mutable và sửa nó sau `submit`, các thread có thể thấy thay đổi bất ngờ hoặc xuất hiện data race. **Tham số tường minh giải quyết đường truyền dữ liệu; tính bất biến/đồng bộ giải quyết vấn đề chia sẻ mutable state.**

## 6. Virtual threads (Java 21+) không làm thay đổi quy tắc

Virtual threads do JVM lập lịch trên các carrier threads. Một virtual thread có thể được tạm unmount khi chờ I/O và tiếp tục trên carrier khác. Tham số hàm và các biến của tác vụ đi theo quá trình thực thi bình thường, nên không cần ràng buộc request context với danh tính carrier.

Lưu **`ExplicitVirtualThreadDemo.java`** (JDK 21+):

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExplicitVirtualThreadDemo {
    record RequestContext(String requestId, String tenantId) {}

    static String process(RequestContext context) {
        return context.requestId()
                + "/" + context.tenantId()
                + ": virtual=" + Thread.currentThread().isVirtual();
    }

    public static void main(String[] args) throws Exception {
        RequestContext a = new RequestContext("req-01", "tenant-A");
        RequestContext b = new RequestContext("req-02", "tenant-B");

        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first =
                    executor.submit(() -> process(a));
            Future<String> second =
                    executor.submit(() -> process(b));

            System.out.println(first.get());
            System.out.println(second.get());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ExplicitVirtualThreadDemo.java
java ExplicitVirtualThreadDemo
~~~

Stdout chính xác:

~~~text
req-01/tenant-A: virtual=true
req-02/tenant-B: virtual=true
~~~

`Executors.newVirtualThreadPerTaskExecutor()` tạo một virtual thread mới cho mỗi task. `try-with-resources` đóng executor và hoàn tất quản lý vòng đời. Không có thao tác thiết lập “thread context” ngầm; hàm `process(context)` không quan tâm được thực thi trên virtual thread hay platform thread.

Nếu chỉ có một tác vụ và không cần thu giá trị trả về, có thể dùng API trực tiếp:

~~~java
RequestContext context = new RequestContext("req-03", "tenant-A");
Thread worker = Thread.startVirtualThread(() -> process(context));
worker.join();
~~~

Đây là **mảnh mã** trong phương thức có kiểu `RequestContext` và `process` tương ứng, đồng thời xử lý hoặc khai báo `InterruptedException`. `Thread.join()` chỉ đợi kết thúc; nó không trả về kết quả hay ném lại ngoại lệ không bắt được trong worker. Cần kết quả và lỗi có cấu trúc thì dùng `Callable`/`Future` như ví dụ phía trên.

**Chú ý:** virtual thread vẫn có `ThreadLocal` của chính nó; nó không “đọc ThreadLocal của carrier” theo cách diễn giải tùy tiện. Vấn đề của ngữ cảnh ngầm là nó bám vào một thread hoặc scope riêng và cần quy tắc truyền/ràng buộc đúng khi tác vụ tách nhánh. Tham số tường minh tránh yêu cầu người đọc phải suy đoán quy tắc đó.

## 7. Thiết kế chữ ký: truyền riêng hay gom context?

### 7.1. Dữ liệu ít, sử dụng ở một hàm

Khi phương thức chỉ cần hai giá trị, dùng hai tham số:

~~~java
String formatMessage(String requestId, String localeTag) {
    return "[" + requestId + "] " + localeTag;
}
~~~

Không cần tạo `RequestContext` 20 trường cho thao tác này.

### 7.2. Có một nhóm dữ liệu đi chung và có nghĩa

Khi một nhóm trường cùng mô tả yêu cầu, có thể gom trong object bất biến:

~~~java
record RequestContext(
        String requestId,
        String tenantId,
        java.util.Locale locale) {}
~~~

Không biến object thành **“túi các service và trạng thái”** chứa database connection, HTTP response, logger, config toàn cục và nhiều đối tượng mutable. Như vậy nó chỉ thay một sự phụ thuộc ngầm bằng một sự phụ thuộc rộng khó đánh giá.

### 7.3. Hàm không dùng context

Không nên nhận tham số context chỉ để giữ quy ước nếu hàm thực sự không cần. Cho hàm nhận `amount` và `taxRate` nếu nó chỉ tính thuế. Cách này giữ chữ ký có ý nghĩa, hỗ trợ test và tránh coupling.

### 7.4. Context có dữ liệu mutable

Record `record RequestContext(List<String> roles) {}` không tạo tính bất biến sâu. Nếu các tác vụ cùng nhìn vào list đó và một tác vụ sửa, vẫn có race. Chụp snapshot trước khi submit:

~~~java
List<String> rolesSnapshot = List.copyOf(roles);
executor.submit(() -> checkRoles(rolesSnapshot));
~~~

Đây là **mảnh mã** có `roles`, `executor` và `checkRoles` được định nghĩa. `List.copyOf` tạo danh sách không sửa được; các phần tử bên trong vẫn có thể mutable nếu kiểu dữ liệu cho phép. Khi cần shared mutable state thật, dùng owner rõ ràng, lock, atomic hoặc concurrent collection phù hợp, không giả định “truyền param” tự làm mọi thao tác thread-safe.

## 8. Khi nào mới cân nhắc context ngầm?

Một số nhu cầu xuyên nhiều tầng có thể khiến truyền thủ công trở nên bất tiện: tracing/correlation ID qua thư viện chung, metadata gắn với một scope, hoặc API bên thứ ba chỉ đọc context ngầm. Lúc đó cần đánh giá:

- Ai **thiết lập** và **đóng** context?
- Scope của context là một lời gọi, một task, một request, hay toàn bộ thread?
- Nó có vượt ranh giới executor/child thread không, và cần truyền theo quy tắc nào?
- Có thể lẫn dữ liệu giữa các request không?
- Dữ liệu có bất biến và có thể được chia sẻ giữa các task con không?
- Nếu bị lỗi hoặc cancel giữa chừng, `finally`/đóng scope có được chạy đầy đủ?

Java 25 chuẩn hóa **`ScopedValue`** (JEP 506) cho ngữ cảnh đọc ngầm có phạm vi ràng buộc, phù hợp một số bài toán nhiều tầng. Đây là cơ chế khác `ThreadLocal`: việc thiết lập có phạm vi ràng buộc, và quy tắc truyền sang tác vụ con phụ thuộc cách tạo/quản lý task. Với Java 17/21 hoặc tình huống chỉ qua vài lời gọi, truyền tham số vẫn là lựa chọn đơn giản hơn.

**Lưu ý về bảo mật:** truyền `userId` hoặc `tenantId` đúng cách không tự thực hiện authorization. Các lớp kiểm tra quyền vẫn phải xác thực đầu vào và ràng buộc truy cập tài nguyên theo yêu cầu.

## 9. Tình huống thường gặp và quyết định

| Tình huống | Nên ưu tiên | Vì sao |
| --- | --- | --- |
| Hàm tính phí cần `amount` và `premium` | Hai tham số | Phụ thuộc nhỏ, rõ ràng |
| Hàm format log của một request chỉ cần `requestId` | Truyền `requestId` | Không cần setup/cleanup thread context |
| Vài hàm dùng chung `requestId`, `tenantId`, `locale` | `RequestContext` bất biến | Nhóm dữ liệu có ý nghĩa, không gây chữ ký quá dài |
| Submit hai tác vụ lên executor | Capture context rồi gọi `process(context)` | Không phụ thuộc worker tái sử dụng |
| Một virtual thread xử lý một yêu cầu | Truyền/capture context | Không phụ thuộc carrier, dễ test |
| Framework/library đòi context ngầm nhiều tầng | Cân nhắc cơ chế scoped/thread context | Cần scope, cleanup và propagation rõ ràng |
| Nhiều task cần cùng mutable counter | Atomic/lock hoặc owner riêng | Context propagation không thay concurrency control |
| Công việc có deadline | Truyền deadline và thiết kế timeout/cancel | Chuyển dữ liệu không tự dừng task |

Ở ví dụ thứ hai, đôi khi logger thật sự có cơ chế tự gắn correlation ID; đó là quyết định hạ tầng khác. Bảng trên dành cho **đường truyền dữ liệu đơn giản trong code Java của chính chương trình**.

## 10. Lỗi phổ biến

1. **Ẩn đầu vào của hàm trong `ThreadLocal`** dù caller có thể truyền trực tiếp: tăng độ khó khi test, debug và review.
2. **Quên `remove()` trong executor**: dữ liệu của yêu cầu trước có thể xuất hiện trong tác vụ khác trên cùng worker.
3. **Cho rằng `InheritableThreadLocal` tự truyền qua mọi executor**: worker pool có thể đã được tạo trước khi có request.
4. **Capture đối tượng mutable và sửa sau `submit`**: lambda vẫn giữ cùng tham chiếu, không phải một bản sao bất biến.
5. **Truyền context cho mọi hàm bất kể nó có dùng hay không**: tạo coupling và che phụ thuộc thực tế.
6. **Đưa nhiều service và object lớn vào “Context”**: khiến object thành service locator khó kiểm soát.
7. **Nghĩ `Future.get()` mang thread-local trở lại caller**: `get()` chỉ trả kết quả hoặc báo ngoại lệ task.
8. **Nhầm thread identity với logical request identity**: một request có thể tách thành nhiều task/thread.
9. **Giả định tham số tường minh loại bỏ mọi race**: mutable data vẫn phải được bảo vệ.
10. **Đánh đồng propagation với cancellation**: có deadline trong context không khiến thread tự bị interrupt khi đến hạn.

## 11. Checklist khi review

- [ ] Với đường truyền chỉ vài hàm, đã thử truyền tham số thay cho `ThreadLocal`?
- [ ] Mỗi phương thức có nhận **đúng trường dữ liệu nó sử dụng**?
- [ ] Nếu nhiều trường luôn đi cùng nhau, đã cân nhắc một object context bất biến, vừa đủ?
- [ ] Tại `executor.submit`, `Thread.startVirtualThread` hoặc callback, context có được đưa vào task tường minh?
- [ ] Có object mutable nào bị share giữa task mà chưa có chiến lược đồng bộ?
- [ ] Nếu vẫn dùng `ThreadLocal`, đã có `try/finally` cleanup/restore và test tái sử dụng worker?
- [ ] Có assumption sai rằng các task của một request chạy trên cùng một thread?
- [ ] Output, errors và timeout có đường truyền/vòng đời riêng, thay vì phụ thuộc trạng thái ẩn?
- [ ] Dữ liệu nhận từ context có được kiểm tra quyền khi dùng vào thao tác nhạy cảm?

## 12. Tài liệu chính thức

- [Java SE 17 — `ThreadLocal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): `get`, `set`, `remove`.
- [Java SE 17 — `InheritableThreadLocal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InheritableThreadLocal.html): ngữ nghĩa kế thừa giá trị.
- [Java SE 21 — `Thread`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): `startVirtualThread`, `join` và `isVirtual`.
- [Java SE 21 — `Executors`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html): `newVirtualThreadPerTaskExecutor`.
- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): mô hình concurrency của virtual thread.
- [JEP 506 — Scoped Values](https://openjdk.org/jeps/506): ngữ cảnh có phạm vi ràng buộc từ JDK 25.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-context-010">Không lưu state nghiệp vụ mutable vào context chung</a>

<details>
<summary>Xem chi tiết</summary>

# Không lưu state nghiệp vụ mutable vào context chung

## 1. Vấn đề trong Java concurrency

Một ứng dụng Java có thể xử lý đồng thời nhiều yêu cầu: mỗi yêu cầu có người dùng, đơn hàng, số tiền, quyền thao tác và trạng thái xử lý khác nhau. **State nghiệp vụ mutable** là dữ liệu có thể thay đổi trong quá trình xử lý, chẳng hạn `currentOrder`, `cartItems`, `balance`, `discount` hoặc `currentUser`. Nếu nhiều tác vụ nhìn vào **cùng một đối tượng context có thể sửa**, chúng có thể ghi đè dữ liệu của nhau, khiến thao tác của yêu cầu A dùng nhầm dữ liệu yêu cầu B.

Đây là lỗi **ranh giới sở hữu state**: dữ liệu chỉ thuộc một request/tác vụ lại được đặt vào nơi mà nhiều request cùng đọc và sửa. Lỗi xảy ra với platform threads lẫn virtual threads; thêm `volatile`, `synchronized` hoặc `ConcurrentHashMap` không tự tạo ra sự tách biệt giữa các yêu cầu.

Trong chương **Ngữ cảnh luồng**, cần phân biệt:

- **Execution context**: dữ liệu kèm theo một lần thực thi, thường gồm request ID, correlation ID, actor/user ID, locale, trace context hoặc thông tin quan sát. Nên nhỏ, có phạm vi sống rõ ràng và ưu tiên bất biến.
- **Business state**: dữ liệu quyết định kết quả nghiệp vụ, chẳng hạn các dòng giỏ hàng, tồn kho, trạng thái chuyển tiền, trạng thái xử lý đơn. Nó cần chủ sở hữu, vòng đời và quy tắc cập nhật riêng.
- **Shared state**: dữ liệu nhiều tác vụ thực sự cần dùng chung, như cache được đồng bộ, bộ đếm hệ thống hoặc kho dữ liệu bền vững. Nếu mutable thì phải có giao thức cập nhật đảm bảo các bất biến nghiệp vụ.
- **Thread-local context**: một cách liên kết giá trị với thread hiện tại, không tự đảm bảo đối tượng bên trong bất biến, không tự biến dữ liệu thành tài sản của request, cũng không tự truyền dữ liệu sang mọi thread khác.

Nguyên tắc: **context truyền thông tin nhận diện và quan sát; state nghiệp vụ nằm trong tham số, kết quả, thực thể hoặc nơi lưu trữ được quản lý bằng quy tắc concurrency phù hợp**.

## 2. Thế nào là “context chung” gây rủi ro?

Các mẫu thiết kế dưới đây dễ biến thông tin theo request thành state toàn cục:

```java
// Ví dụ sai: tất cả tác vụ chia sẻ cùng một biến user.
static String currentUserId;

// Ví dụ sai: một map dùng cho mọi request.
static final java.util.Map<String, Object> CONTEXT =
        new java.util.concurrent.ConcurrentHashMap<>();

// Ví dụ sai: singleton chứa "đơn đang xử lý".
final class ProcessingContext {
    String currentOrderId;
    long currentAmount;
}
```

Ba đoạn trên chỉ minh họa anti-pattern, **không phải chương trình độc lập**. `ConcurrentHashMap` có thể bảo vệ tính an toàn của thao tác `put/get` trên chính map, nhưng không ngăn A gọi `put("currentUser", "alice")` rồi B ghi đè `"bob"`. Khi A đọc lần tiếp theo, map hoàn toàn nhất quán về mặt bộ nhớ nhưng giá trị thuộc request B. **Thread-safe container không có nghĩa là request-safe business data.**

Một ví dụ khác tưởng an toàn:

```java
static final ThreadLocal<java.util.Map<String, Object>> CTX =
        ThreadLocal.withInitial(java.util.HashMap::new);
```

Mỗi thread nhận một `HashMap` mặc định riêng, nhưng điều đó chỉ nói về **thread**, không phải request. Platform thread trong executor có thể chạy liên tiếp nhiều request; nếu quên xóa/khôi phục state, request sau nhận dữ liệu trước. Ngược lại, nếu ai đó lấy `CTX.get()` rồi truyền tham chiếu map ấy sang một task khác, hai thread có thể cùng sửa **chính một map**. `ThreadLocal` không sao chép hay tự bảo vệ đối tượng được lưu.

## 3. Ví dụ Java 21+: cùng một context làm hai yêu cầu nhiễm dữ liệu

Chương trình độc lập dưới đây ép thứ tự để lỗi xuất hiện **xác định**, không phụ thuộc may rủi của race condition. Hai virtual threads cùng dùng một map toàn cục. Request Alice ghi `alice` rồi chờ Bob ghi `bob`; khi Alice đọc lại, dữ liệu đã bị ghi đè.

Lưu thành `SharedContextPitfall.java`:

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SharedContextPitfall {
    private static final Map<String, String> SHARED =
            new ConcurrentHashMap<>();

    // Snapshot metadata bất biến của từng request.
    private record RequestContext(String requestId, String userId) {}

    private static String process(RequestContext context) {
        // Không đọc "currentUser" từ một biến toàn cục.
        return context.userId();
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch alicePublished = new CountDownLatch(1);
        CountDownLatch bobPublished = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> aliceWrong = executor.submit(() -> {
                SHARED.put("currentUser", "alice");
                alicePublished.countDown();
                bobPublished.await();
                return SHARED.get("currentUser");
            });

            Future<String> bobWrong = executor.submit(() -> {
                alicePublished.await();
                SHARED.put("currentUser", "bob");
                bobPublished.countDown();
                return SHARED.get("currentUser");
            });

            // Hai kết quả sau phản ánh cùng một context dùng chung.
            System.out.println("shared alice=" + aliceWrong.get());
            System.out.println("shared bob=" + bobWrong.get());

            RequestContext alice = new RequestContext("A", "alice");
            RequestContext bob = new RequestContext("B", "bob");

            Future<String> aliceSafe =
                    executor.submit(() -> process(alice));
            Future<String> bobSafe =
                    executor.submit(() -> process(bob));

            System.out.println("isolated A=" + aliceSafe.get());
            System.out.println("isolated B=" + bobSafe.get());
        }
    }
}
```

Biên dịch và chạy với **JDK 21+**:

```bash
javac SharedContextPitfall.java
java SharedContextPitfall
```

Kết quả chính xác:

```text
shared alice=bob
shared bob=bob
isolated A=alice
isolated B=bob
```

Giải thích:

1. `SHARED` là một `ConcurrentHashMap` đúng chuẩn và từng thao tác `put/get` hợp lệ.
2. Hai request vẫn sai về nghiệp vụ vì cả hai dùng **một khóa “currentUser” có phạm vi toàn process**.
3. `CountDownLatch` buộc Alice chờ đến khi Bob đã ghi; do đó lỗi tái hiện ổn định.
4. Phiên bản đúng tạo **hai `RequestContext` riêng**, được truyền tường minh vào hàm xử lý. Việc thực thi trên virtual threads không làm thay đổi logic.
5. `Future.get()` đợi task hoàn tất và giúp lỗi trong task được báo về caller. `try-with-resources` đóng executor đúng vòng đời.

Đừng diễn giải ví dụ thành “mọi map chia sẻ đều sai”. Một cache hoặc index có thể chia sẻ an toàn khi được thiết kế để dùng chung; vấn đề là lưu **giá trị riêng của một yêu cầu dưới tên toàn cục duy nhất** rồi coi đó là context hiện tại cho tất cả tác vụ.

## 4. ThreadLocal: giá trị theo thread không phải state theo request

`ThreadLocal<T>` giữ một ô giá trị cho **mỗi thread**. Hai thread khác nhau gọi `get()` trên cùng đối tượng `ThreadLocal` sẽ truy cập hai ô riêng. Tuy vậy:

- **Platform-thread pool tái sử dụng thread**: request A kết thúc nhưng thread không chết; nếu không xóa state, request B chạy trên thread ấy có thể đọc giá trị còn sót.
- **Chuyển việc sang thread mới**: `ThreadLocal` thường không tự truyền sang thread được tạo hoặc task trong executor. Phần code chạy ở thread khác có thể thấy `null`/default value.
- **Đối tượng mutable được lưu**: `ThreadLocal` chỉ cô lập *slot* của từng thread; nếu hai slot đều trỏ tới một đối tượng do code tự chia sẻ, đối tượng vẫn có alias và race.
- **Virtual threads**: cũng hỗ trợ `ThreadLocal`, nhưng thường có một virtual thread cho một tác vụ, không nên tạo pool virtual threads. Nhiều virtual threads cùng giữ payload lớn trong thread-local làm tiêu tốn bộ nhớ đáng kể; không dùng thread-local để cache đối tượng đắt tiền trên mỗi virtual thread.

### Ví dụ: request sau đọc nhầm dữ liệu request trước

Lưu file `ThreadLocalCleanupDemo.java`:

```java
import java.util.concurrent.Executors;

public class ThreadLocalCleanupDemo {
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        // Một platform worker tái sử dụng cho ba task.
        try (var executor = Executors.newSingleThreadExecutor()) {
            String first = executor.submit(() -> {
                USER.set("alice");
                return USER.get(); // Cố ý không remove(): minh họa lỗi.
            }).get();

            String leaked = executor.submit(() -> {
                String value = USER.get(); // Nhìn thấy "alice" từ task 1.
                USER.remove();            // Dọn sạch để quan sát task 3.
                return value;
            }).get();

            String cleared = executor.submit(
                    () -> String.valueOf(USER.get())).get();

            System.out.println("task1=" + first);
            System.out.println("task2=" + leaked);
            System.out.println("task3=" + cleared);
        }
    }
}
```

Chạy bằng JDK 21+:

```bash
javac ThreadLocalCleanupDemo.java
java ThreadLocalCleanupDemo
```

```text
task1=alice
task2=alice
task3=null
```

`newSingleThreadExecutor()` đảm bảo các tác vụ được thực thi lần lượt bởi một worker, vì vậy việc request thứ hai nhìn thấy dữ liệu request trước là điều được chủ động minh họa. Ví dụ **cố ý chứa lỗi ở task 1**; tuyệt đối không áp dụng cách quên cleanup này trong code vận hành.

Nếu vì tương thích thư viện bạn buộc phải dùng thread-local để đặt metadata ngắn hạn, hãy khoanh vòng đời:

```java
// Đoạn minh họa trong phương thức xử lý:
USER.set(userId);
try {
    processRequest();
} finally {
    USER.remove();
}
```

Trong ví dụ này, `USER`, `userId` và `processRequest()` phải được định nghĩa bởi ứng dụng; **đây là snippet, không phải chương trình chạy độc lập**. Khi có khả năng đã có binding trước đó trong cùng luồng gọi, việc thay một binding cũ bằng binding mới rồi `remove()` có thể làm mất binding của outer scope; hãy thiết kế người sở hữu context và quy tắc khôi phục giá trị trước đó thật rõ. Cách tốt nhất vẫn là dùng tham số tường minh nếu có thể.

## 5. InheritableThreadLocal không biến context mutable thành bản sao độc lập

`InheritableThreadLocal<T>` có thể truyền **giá trị ban đầu** từ thread cha sang thread con tại thời điểm tạo thread. Mặc định, phương thức `childValue(parentValue)` trả lại **chính tham chiếu** của cha. Ví dụ:

```java
import java.util.ArrayList;
import java.util.List;

InheritableThreadLocal<List<String>> tags = new InheritableThreadLocal<>();
List<String> sharedTags = new ArrayList<>();
tags.set(sharedTags);

Thread child = Thread.ofVirtual().start(() -> {
    tags.get().add("child");
});
child.join();

System.out.println(sharedTags); // [child]
tags.remove();
```

Đoạn trên là **snippet** dùng các câu lệnh trong một phương thức; chạy độc lập cần bọc trong lớp và `main`. Nó cho thấy cha và con cùng giữ tham chiếu `ArrayList`, nên sửa từ con tác động tới đối tượng mà cha nhìn thấy. Ví dụ chủ động dùng `join()` để tránh đọc đồng thời `ArrayList` chưa đồng bộ. Nếu hai thread sửa đồng thời, đó còn là **data race**.

Không dựa vào tính kế thừa để truyền mutable business state; sử dụng thông điệp/bản sao snapshot riêng hoặc tham số bất biến. Khi tạo thread, một số `Thread.Builder` cho phép `inheritInheritableThreadLocals(false)`; tuy nhiên điều đó chỉ tắt kế thừa, **không giải quyết bài toán sở hữu dữ liệu**.

## 6. Record, final, unmodifiable không tự động tạo deep immutability

Cách thuận tiện để mang metadata theo request là tạo một value object bất biến:

```java
record RequestContext(
        String requestId,
        String userId,
        String traceId
) {}
```

`String` bất biến và các trường của `record` là `final`, nên với các thành phần trên context phù hợp để chia sẻ đọc mà không lo một tác vụ đổi trường `userId` sau khi được tạo. Nhưng **record chỉ bất biến ở tham chiếu trường**, không tự sao chép sâu đối tượng bên trong:

```java
record CartContext(java.util.List<String> productIds) {}

var products = new java.util.ArrayList<String>();
products.add("book");
var context = new CartContext(products);
products.add("laptop"); // context.productIds() cũng nhìn thấy laptop.
```

Nếu metadata cần chứa collection, tạo **snapshot** bằng `List.copyOf` / `Map.copyOf` / `Set.copyOf`, đồng thời cân nhắc sao chép sâu các phần tử mutable:

```java
import java.util.List;
import java.util.Objects;

record SafeContext(String requestId, List<String> roles) {
    SafeContext {
        Objects.requireNonNull(requestId);
        roles = List.copyOf(Objects.requireNonNull(roles));
    }
}
```

Đây là đoạn khai báo có thể đặt trong một file Java phù hợp; không có `main` riêng. `List.copyOf` tạo danh sách không thể sửa qua API danh sách và tách khỏi việc thay đổi cấu trúc danh sách đầu vào; ở đây phần tử là `String` bất biến. Với `List<MutableOrder>`, danh sách không sửa được **vẫn có thể chứa các `MutableOrder` bị sửa bên trong**. Không lưu những entity mutable đó trong context chỉ vì đã bọc chúng trong một list “immutable”.

Cũng đừng nhầm `final Map<K,V>` với map bất biến: `final` ngăn gán lại biến/tham chiếu, nhưng không ngăn `map.put(...)`.

## 7. Chọn cách truyền dữ liệu: explicit parameter, ThreadLocal, ScopedValue

| Nhu cầu | Cách phù hợp | Điều cần tránh |
| --- | --- | --- |
| Tính phí đơn hàng theo dữ liệu request | Tham số `OrderInput`/`OrderSnapshot`, kết quả trả về rõ ràng | Cất `currentOrder` mutable vào global/shared context |
| Chuyển request ID/user ID qua nhiều method | Tham số tường minh; có thể dùng `ThreadLocal` hoặc `ScopedValue` cho metadata cần truyền ngầm | Mở rộng context thành “túi” chứa mọi entity nghiệp vụ |
| Gọi tác vụ ở executor khác | Capture context bất biến và đưa vào `Runnable`/`Callable`/tham số | Giả định `ThreadLocal` tự đi theo task |
| Nhiều tác vụ cùng cập nhật tồn kho | Nơi sở hữu state và giao thức cập nhật nguyên tử/transaction thích hợp | `Map<String,Object>` chung với thao tác check-then-act không được bảo vệ |
| Chia sẻ cache đọc | Cache được thiết kế concurrent, key/value an toàn, chính sách hết hạn | Cache chứa đối tượng mutable mà caller tự sửa |
| Logging/correlation trong một call stack | ID ngắn, bất biến, vòng đời rõ ràng | Giữ request body/connection/đối tượng lớn trong thread-local |

### ScopedValue trên Java 25+

`ScopedValue<T>` trở thành API ổn định ở **JDK 25** (được giới thiệu dạng preview từ JDK 21). Nó cung cấp **binding theo phạm vi thực thi**, phù hợp cho dữ liệu chỉ đọc truyền qua nhiều hàm, đặc biệt khi kết hợp với cấu trúc tác vụ con được hỗ trợ. Khác với `ThreadLocal.set` có thể thay đổi binding từ code bất kỳ, code bên trong phạm vi thường chỉ đọc binding hiện tại, rồi binding tự kết thúc khi rời scope.

```java
// Snippet JDK 25+, không phải chương trình độc lập.
record RequestMetadata(String requestId, String actorId) {}

private static final ScopedValue<RequestMetadata> CURRENT =
        ScopedValue.newInstance();

static void execute(RequestMetadata metadata) {
    ScopedValue.where(CURRENT, metadata).run(() -> {
        audit(CURRENT.get().requestId());
    });
}
```

`audit(...)` là hàm do ứng dụng định nghĩa; đây là **snippet** minh họa. Code `ScopedValue` ổn định nói trên không biên dịch trực tiếp trên JDK 21 nếu không dùng đúng preview API và các cờ preview của phiên bản đó.

**Điểm cốt lõi:** binding của `ScopedValue` có vòng đời được kiểm soát, nhưng nó vẫn giữ **tham chiếu tới một object**. Nếu bạn bind một `HashMap`, `ArrayList`, `Order` mutable, các code được phép truy cập có thể vẫn sửa object đó. “Scoped” không đồng nghĩa “deep immutable”. Chỉ gắn metadata bất biến, nhỏ gọn, thực sự phù hợp với luồng thực thi.

Một binding `ScopedValue` cũng không tự động truyền sang **mọi** tác vụ executor độc lập. Khả năng kế thừa ở các tác vụ con có cấu trúc phải tuân theo API/cơ chế task scope của đúng JDK đang chạy. Qua ranh giới thread/process hoặc message queue thông thường, hãy chuyển metadata có chủ đích.

## 8. State nghiệp vụ nên sống ở đâu?

Quyết định theo **chủ sở hữu dữ liệu** và **thời điểm cần cập nhật**, thay vì theo thread hiện đang chạy.

### 8.1. State chỉ cần trong một tác vụ

Sử dụng tham số, biến cục bộ, object sống trong phạm vi tác vụ và kết quả trả về. Ví dụ:

```java
record OrderInput(String orderId, long subtotal, long discount) {}
record OrderResult(String orderId, long payable) {}

static OrderResult calculate(OrderInput input) {
    if (input.subtotal() < 0 || input.discount() < 0 ||
            input.discount() > input.subtotal()) {
        throw new IllegalArgumentException("invalid amount");
    }
    return new OrderResult(
            input.orderId(),
            input.subtotal() - input.discount());
}
```

Đây là đoạn khai báo minh họa, có thể nằm trong một lớp Java thích hợp. Các biến và kết quả thuộc một lời gọi `calculate`, không bị request khác sửa thông qua biến `currentAmount` toàn cục. Ví dụ dùng `long` đơn vị tiền nhỏ nhất đã được quy ước; bài này tập trung vào concurrency, không bàn quy tắc tiền tệ đa loại.

### 8.2. State cần nhiều tác vụ cùng cập nhật

Lúc này **có shared mutable state thật**. Chuyển state vào object sở hữu rõ ràng, kho dữ liệu/transaction, mailbox/actor hoặc cấu trúc concurrent có invariant được bảo vệ. Ví dụ “chỉ còn một sản phẩm” cần thao tác kiểm tra và trừ tồn kho là **một bước logic nguyên tử**: đơn thuần `ConcurrentHashMap.get` rồi `put` không đủ nếu nhiều tác vụ cùng mua.

`AtomicInteger` phù hợp với bộ đếm đơn giản; `ConcurrentHashMap.compute(...)` có thể hỗ trợ cập nhật một key với quy tắc nhất quán phù hợp, nhưng không tự cung cấp transaction xuyên nhiều key/DB. Khi nghiệp vụ trải qua database hoặc hệ thống ngoài, cần cơ chế nhất quán tương ứng ở nơi quản lý dữ liệu. Không đặt một `synchronized` lên “request context” rồi cho rằng đã bảo vệ toàn bộ giao dịch phân tán.

### 8.3. State dùng chung nhưng chỉ đọc

Có thể chia sẻ cấu hình bất biến, danh mục/lookup bất biến, formatter thread-safe (ví dụ `java.time.format.DateTimeFormatter`) hoặc snapshot sau khi xây dựng xong. Phải công bố đối tượng an toàn và không expose reference tới các thành phần mutable để bên dùng sửa.

Trong Java, **an toàn xuất bản (safe publication)** và **bất biến thực sự** là hai vấn đề bổ trợ: một object được publish an toàn nhưng vẫn mutable thì các sửa đổi đồng thời tiếp theo vẫn cần đồng bộ.

## 9. Cách nhận diện và sửa lỗi trong codebase

Khi thấy class dạng `RequestContextHolder`, `UserContext`, `ProcessingContext` hoặc utility static, kiểm tra kỹ:

1. Có trường `static`/singleton nào mang tên `currentUser`, `currentOrder`, `pendingPayment`, `cart`, `amount`, `transactionState` không?
2. Context có chứa `Map<String,Object>`, `List`, entity hoặc DTO mutable được expose trực tiếp qua getter không?
3. Có `ThreadLocal.set(...)` nhưng thiếu `remove()` trong `finally` không? Có code xóa ở nhiều chỗ khiến khó xác định owner không?
4. Có method nhận context nhưng âm thầm sửa nó, khiến thứ tự gọi method làm thay đổi kết quả không?
5. Có tạo child thread, executor task, `CompletableFuture` hoặc virtual thread mới rồi kỳ vọng thấy cùng thread-local mà không truyền dữ liệu không?
6. Có log theo `requestId` để đối chiếu xem một xử lý đã truy cập sai `userId/orderId` của yêu cầu khác không?
7. Có quy tắc nghiệp vụ đòi hỏi cập nhật nguyên tử nhưng implementation chỉ khóa từng thao tác riêng lẻ trên collection không?

Quy trình refactor có kiểm soát:

- **Bước 1:** Phân loại field: metadata bất biến theo request, business data trong request, shared domain state hay resource holder.
- **Bước 2:** Với business data theo request, đưa vào tham số hoặc object riêng cho tác vụ, có quyền sửa và vòng đời rõ ràng; tốt nhất truyền snapshot bất biến giữa các tác vụ.
- **Bước 3:** Với shared domain state, thiết kế owner và atomicity/transaction ở đúng cấp; kiểm tra invariant dưới tải đồng thời.
- **Bước 4:** Với metadata thực sự cần truyền ngầm, giữ phạm vi nhỏ, ràng buộc cleanup/binding; tránh mang theo mutable domain objects.
- **Bước 5:** Thử nhiều yêu cầu với actor và dữ liệu khác nhau chạy đồng thời, tái sử dụng platform pool, có lỗi/timeout/cancel và có nhánh tạo thread mới.

### Kiểm thử quan trọng hơn “cứ chạy nhiều lần”

Việc chạy 1.000 lần có thể vẫn không tái hiện lỗi do timing. Hãy dùng `CountDownLatch`/`CyclicBarrier` để **ép interleaving có chủ đích**, như ví dụ ở mục 3, rồi assert rằng kết quả mỗi request khớp input của nó. Thêm trường hợp exception và cancel để bảo đảm cleanup trong `finally`. Với domain state chung, xác minh invariant cuối cùng: tổng số hàng đã bán không vượt tồn kho, tiền chuyển không mất/nhân đôi, trạng thái không đảo trái phép.

## 10. Những ngộ nhận thường gặp

**“Dùng ConcurrentHashMap là xong.”** Collection thread-safe giữ đúng thao tác container, nhưng không sửa lỗi hai request cùng truy cập khóa `currentUser` hoặc quy tắc check-then-act bị tách rời.

**“Mỗi thread có ThreadLocal nên mỗi request có dữ liệu riêng.”** Sai khi một platform thread chạy nhiều request kế tiếp hoặc code đã chia sẻ reference mutable. `ThreadLocal` cô lập theo thread, không đảm bảo scope nghiệp vụ.

**“InheritableThreadLocal tự copy dữ liệu cho child thread.”** Mặc định nó có thể truyền cùng tham chiếu object. Copy reference không phải deep copy.

**“Record/final đã làm mọi thứ immutable.”** Trường tham chiếu không gán lại được nhưng object/list bên trong vẫn mutable nếu không tạo snapshot phù hợp.

**“ScopedValue sẽ bảo vệ mọi state.”** ScopedValue giới hạn vòng đời của *binding*, không biến object được bind thành immutable và không tạo transaction cho dữ liệu nghiệp vụ.

**“Chuyển sang virtual threads sẽ hết lẫn context.”** Virtual threads giúp mô hình một thread cho một task nhưng không sửa shared static holder, mutable alias hay lỗi ownership. Cũng không nên cache đối tượng lớn trong một `ThreadLocal` cho mỗi virtual thread.

**“Dùng synchronized trên tất cả access là đủ.”** Đồng bộ có thể tránh race ở mức bytecode nhưng vẫn cho request A đọc state của B theo đúng thứ tự khóa; còn có thể tạo contention và giảm throughput. Trước tiên phải thiết kế đúng scope.

**“Context tiện nên truyền cả entity để khỏi thêm parameter.”** Khi một entity mutable được gắn vào ambient context, các hàm ẩn phụ thuộc vào nó, khó test, khó quan sát đường sửa đổi, dễ lộ dữ liệu chéo request. Hãy làm dependency và ownership rõ ràng.

## 11. Checklist thiết kế và vận hành

- [ ] Context giữ **metadata nhỏ và đủ dùng**: request/correlation ID, actor ID, trace ID; không chứa mutable order/cart/payment.
- [ ] Mỗi request/tác vụ có snapshot input riêng; phép xử lý có tham số và kết quả rõ ràng.
- [ ] Không có singleton/static “current business object” cho mọi tác vụ đồng thời.
- [ ] Không expose trực tiếp collection/DTO mutable qua context; đã xét deep immutability.
- [ ] Nếu bắt buộc dùng ThreadLocal, mỗi binding có owner, vòng đời, `finally/remove` hoặc quy tắc restore rõ ràng.
- [ ] Các ranh giới executor/virtual thread/async đã truyền context phù hợp, không giả định thread-local tự theo task.
- [ ] Shared domain state có atomicity/invariant/transaction đúng mức.
- [ ] Có bài test ép interleaving và kiểm tra không lẫn actor, request, order; có test cleanup khi exception.
- [ ] Có metric/log theo request ID để phát hiện bất thường, nhưng không log mật khẩu, token hoặc dữ liệu nhạy cảm không cần thiết.

## Kết luận

**Context của thread là phương tiện giúp code biết mình đang xử lý việc gì; nó không phải kho lưu state nghiệp vụ mutable dùng chung.** Với Java concurrency, phòng tránh nhiễm dữ liệu bằng cách xác định chủ sở hữu từng state, truyền dữ liệu request tường minh hoặc qua metadata bất biến có scope rõ ràng, và dùng cơ chế đồng bộ/transaction khi thực sự cần nhiều tác vụ cập nhật chung một domain state. `ThreadLocal`, `InheritableThreadLocal` và `ScopedValue` đều có phạm vi sử dụng riêng; không cái nào thay thế thiết kế ownership đúng.

## Tài liệu tham khảo

1. Oracle, [ThreadLocal — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html): giá trị theo thread, vòng đời và `remove()`.
2. Oracle, [InheritableThreadLocal — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/InheritableThreadLocal.html): giá trị kế thừa và `childValue`.
3. Oracle, [Virtual Threads — Java SE 21](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): một thread cho mỗi task, cảnh báo về caching trong thread-local.
4. Oracle, [ScopedValue — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html): binding theo scope và giới hạn khi truyền object mutable.
5. OpenJDK, [JEP 506: Scoped Values](https://openjdk.org/jeps/506): ổn định API trong JDK 25.


</details>

- [Quay lại đầu trang](#back-to-top)
