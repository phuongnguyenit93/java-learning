<a id="back-to-top"></a>

# Ngoại lệ và luồng

## Menu
- [Ngoại lệ chỉ lan truyền trong ngăn xếp của luồng đang thực thi](#thread-exception-handling-001)
- [Try-catch ở luồng tạo không tự bắt lỗi ở luồng con](#thread-exception-handling-002)
- [Thread.UncaughtExceptionHandler](#thread-exception-handling-003)
- [Handler riêng, ThreadGroup và handler mặc định](#thread-exception-handling-004)
- [Khác biệt giữa Executor.execute và submit khi tác vụ thất bại](#thread-exception-handling-005)
- [Future.get và ExecutionException](#thread-exception-handling-006)
- [Tách lỗi của tác vụ khỏi lỗi ở luồng đang chờ](#thread-exception-handling-007)
- [TimeoutException và CancellationException](#thread-exception-handling-008)
- [Lỗi trong tác vụ định kỳ và ảnh hưởng tới các lần chạy tiếp theo](#thread-exception-handling-009)
- [Chuyển thông tin thất bại qua ranh giới luồng bằng kết quả có quản lý](#thread-exception-handling-010)

## <a id="thread-exception-handling-001">Ngoại lệ chỉ lan truyền trong ngăn xếp của luồng đang thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ chỉ lan truyền trong ngăn xếp của luồng đang thực thi (Java)

## 1. Ý nghĩa của nguyên tắc

Trong Java, khi một phương thức ném exception, JVM tìm nơi xử lý bằng cách lần lượt thoát các lời gọi phương thức **trên call stack của luồng đang chạy**. Quá trình đó gọi là *stack unwinding*. Nếu tìm thấy `catch` tương thích, luồng tiếp tục theo logic của handler. Nếu exception đi hết các frame mà vẫn không được xử lý, nó là **uncaught exception của luồng ấy**.

Mỗi `Thread` có ngăn xếp lời gọi riêng. Vì vậy, một exception được ném trong luồng B **không tự động nhảy sang** `try/catch` đang chạy trong luồng A, dù A chính là luồng đã tạo, khởi động hoặc đợi B. Đây là ranh giới nền tảng khi viết API có concurrency.

Từ khóa:

- **Call stack:** các frame phương thức đang thực thi của **một luồng**; mỗi frame chứa địa chỉ quay về và thông tin cần thiết cho lời gọi.
- **Stack unwinding:** JVM rời các frame trong cùng luồng, chạy các đoạn `finally` thích hợp, giải phóng monitor của phương thức/khối `synchronized` khi rời vùng, và tìm handler phù hợp.
- **Uncaught exception:** exception đi ra tới đỉnh luồng mà không có handler ứng dụng xử lý; cơ chế `Thread.UncaughtExceptionHandler` có thể được gọi.
- **Error delivery:** truyền **thông tin** về thất bại từ một tác vụ sang luồng khác bằng đối tượng kết quả, `Future`, `CompletableFuture`, hàng đợi, callback hoặc giao thức thông báo. Đây là hành động có chủ đích; không phải stack unwinding xuyên luồng.

Nguyên tắc áp dụng cho cả **platform thread** lẫn **virtual thread** ở những phiên bản Java hỗ trợ virtual thread: mỗi luồng thực thi có ngữ cảnh ngoại lệ riêng. Cơ chế tổ chức và chờ tác vụ có thể giúp chuyển **kết quả lỗi** về nơi gọi, nhưng không làm hai call stack trở thành một.

## 2. Vì sao `try/catch` của luồng cha không bắt được lỗi của luồng con?

Xét đoạn mã sau (là mảnh code trong một phương thức, với các import cần thiết đã có):

```java
try {
    Thread child = new Thread(() -> {
        throw new IllegalStateException("failed inside child");
    });
    child.start();
    child.join();
} catch (IllegalStateException ex) {
    System.out.println("This catch does not handle the child's exception");
}
```

`Thread.start()` yêu cầu JVM chạy `Thread.run()` trong **luồng mới**. Câu lệnh `throw` thuộc ngăn xếp lời gọi của child; khối `catch` bên ngoài đang ở ngăn xếp của luồng gọi `start()`, nên **không bắt** được exception từ child.

Điều kiện quan trọng:

- `start()` có thể tự ném exception ở **luồng gọi**, chẳng hạn `IllegalThreadStateException` khi cố khởi động một đối tượng `Thread` đã khởi động. Exception như vậy có thể được `catch` tương thích của caller xử lý.
- `join()` có thể ném `InterruptedException` **ở luồng đang đợi** khi luồng đó bị interrupt; đó không phải exception nghiệp vụ của child. Nếu `join()` bị interrupt, khối `catch (InterruptedException)` trong caller có thể bắt được vì exception này phát sinh trong chính caller.
- Với mã ở ví dụ, `join()` trả về sau khi child kết thúc; nó không ném lại `IllegalStateException` đã xảy ra trong child. Nếu không có handler tùy chỉnh, thông thường runtime báo uncaught exception trên `System.err`.
- `Thread.run()` được gọi **trực tiếp** bằng `child.run()` chỉ là lời gọi phương thức bình thường trên **luồng hiện tại**. Khi đó exception có thể lan về `catch` của caller; cũng không tạo một luồng mới.

Cần phân biệt rõ phương thức tạo việc bất đồng bộ với phương thức **chờ và lấy kết quả**. Tạo hoặc đợi một luồng không mặc nhiên mang theo cơ chế chuyển lỗi.

## 3. Sơ đồ ngăn xếp khi ném exception

Ví dụ A gọi `start()` để chạy B. Về mặt khái niệm, ta có hai luồng với hai stack độc lập:

```text
Luồng A (caller)                  Luồng B (worker)
----------------                 ----------------
main()                           Thread.run()
  start() [trả về]                 task()
  join() [đang đợi]                  step1()
                                     step2() -> throw X
  tiếp tục sau join()             unwind step2 -> step1 -> task
                                 -> run -> uncaught handler
```

Exception X được tìm handler ở `step2()`, rồi `step1()`, rồi `task()`, rồi những frame phía trên trong **luồng B**. JVM không tìm handler ở `main()` của A. Sau khi B kết thúc, A có thể tiếp tục sau `join()` mà không biết B đã gặp lỗi, trừ khi hai bên có **kênh báo kết quả**.

Các dấu hiệu dễ gây nhầm:

1. Luồng A đã gọi một phương thức tạo B: điều đó không làm call stack B thành nhánh con về mặt Java exception handling.
2. Bên trong B có gọi một phương thức của object do A tạo: object có thể dùng chung, nhưng stack vẫn riêng.
3. Tên ngoại lệ hoặc stack trace có nhắc class do A gọi: điều đó không làm `catch` của A được kích hoạt.
4. Hai tác vụ chạy chung một `ExecutorService`: mỗi lần thực thi có ngữ cảnh của thread thực hiện; việc chuyển lỗi còn phụ thuộc loại tác vụ và API lấy kết quả.

## 4. Ví dụ Java 17+ có thể biên dịch và chạy

Ví dụ này đặt cạnh nhau bốn tình huống: lời gọi đồng bộ, `Thread` thô, `Future` và `CompletableFuture`. Nó cố tình ném cùng một `IllegalStateException` để phân biệt **nơi exception được ném** với **nơi thông tin lỗi được quan sát**.

Tạo file `ThreadExceptionBoundaryDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadExceptionBoundaryDemo {
    static void startOperation() {
        deeper();
    }

    static void deeper() {
        throw new IllegalStateException("boom");
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            startOperation();
        } catch (IllegalStateException ex) {
            System.out.println("SYNCHRONOUS: " + ex.getClass().getSimpleName());
        }

        Thread worker = new Thread(ThreadExceptionBoundaryDemo::startOperation,
                                   "raw-worker");
        worker.setUncaughtExceptionHandler((thread, error) ->
                System.out.println("UNCAUGHT " + thread.getName() + ": "
                        + error.getClass().getSimpleName()));
        worker.start();
        worker.join();
        System.out.println("AFTER JOIN: main continues");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(
                    ThreadExceptionBoundaryDemo::startOperation);
            try {
                future.get();
            } catch (ExecutionException ex) {
                System.out.println("FUTURE: "
                        + ex.getCause().getClass().getSimpleName());
            }
        } finally {
            executor.shutdown();
        }

        CompletableFuture<Void> stage = CompletableFuture.runAsync(
                ThreadExceptionBoundaryDemo::startOperation);
        try {
            stage.join();
        } catch (CompletionException ex) {
            System.out.println("COMPLETABLE: "
                    + ex.getCause().getClass().getSimpleName());
        }

        System.out.println("MAIN: done");
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn, từ thư mục chứa file:

```bash
javac -encoding UTF-8 ThreadExceptionBoundaryDemo.java
java ThreadExceptionBoundaryDemo
```

Standard output của ví dụ:

```text
SYNCHRONOUS: IllegalStateException
UNCAUGHT raw-worker: IllegalStateException
AFTER JOIN: main continues
FUTURE: IllegalStateException
COMPLETABLE: IllegalStateException
MAIN: done
```

**Điều xảy ra ở từng phần:**

1. `startOperation()` được `main` gọi trực tiếp, nên `IllegalStateException` di chuyển theo chuỗi `deeper() → startOperation() → main()` của chính main thread. `catch` ở `main` bắt được.
2. Một đối tượng `Thread` được khởi động bằng `start()`. Lỗi xảy ra trong worker; do worker không tự xử lý, `UncaughtExceptionHandler` **của worker** in thông tin. `join()` bảo đảm worker đã kết thúc trước khi main in dòng tiếp theo, nhưng không ném lại lỗi.
3. `ExecutorService.submit()` đưa tác vụ vào executor và trả một `Future`. Lỗi thực thi được giữ trong kết quả Future; lời gọi `future.get()` của main ném `ExecutionException` có `getCause()` là `IllegalStateException` ban đầu.
4. `CompletableFuture.runAsync()` tạo một stage bất đồng bộ. Khi tác vụ thất bại, stage hoàn thành với trạng thái exceptional; lời gọi `stage.join()` của main ném `CompletionException` bao lấy nguyên nhân.
5. Chương trình vẫn tới `MAIN: done`. Một thất bại ở worker không mặc nhiên kết thúc tất cả luồng khác hoặc hủy tiến trình.

Ví dụ dùng `setUncaughtExceptionHandler` để output ổn định, không dựa vào in stack trace mặc định trên stderr. `ExecutorService` được `shutdown()` sau khi lấy kết quả; trên Java 17 không giả định rằng `ExecutorService` là tài nguyên `AutoCloseable` cho cú pháp `try-with-resources`.

Lưu ý: `Future.get()` có thể ném thêm `InterruptedException` hoặc `CancellationException` theo trạng thái; `CompletableFuture.join()` có thể ném `CancellationException` nếu future bị hủy. Hai lệnh lấy kết quả được trình bày trong trường hợp tác vụ **thất bại do exception**, không phải trong mọi trạng thái.

## 5. Handler ở đâu trên cùng một call stack?

Một câu `catch` xử lý được exception khi:

- Exception phát sinh trong hoạt động đồng bộ trên **chính luồng hiện tại**.
- Loại exception được ném tương thích với loại trong `catch`, xét theo các quy tắc của Java.
- Không có handler ở frame thấp hơn đã xử lý dứt điểm exception trước đó.

Ví dụ một chuỗi gọi đồng bộ:

```java
static void level1() {
    level2();
}

static void level2() {
    throw new IllegalArgumentException("bad value");
}

// Trong một phương thức khác trên cùng thread:
try {
    level1();
} catch (IllegalArgumentException ex) {
    System.out.println(ex.getMessage());
}
```

Đây là các **mảnh phương thức minh họa**, không phải chương trình độc lập vì vị trí phương thức và mã gọi cần đặt vào lớp phù hợp. `level2()` không cần có `catch` thì handler ở lời gọi `level1()` của cùng thread vẫn bắt được. Nếu `level1()` tạo thread khác và `level2()` chạy trong thread đó, cách diễn giải sẽ thay đổi hoàn toàn.

Trên đường unwinding, các khối `finally` thích hợp vẫn thực thi trong **luồng ném lỗi**; tài nguyên `try-with-resources` được đóng trên luồng đang thực thi đường đó. Các cơ chế cleanup cũng có thể ném thêm lỗi, ví dụ suppressed exceptions, nhưng vẫn không làm exception tự chuyển sang caller của một thread khác.

## 6. Thread thô: `UncaughtExceptionHandler` có vai trò gì?

`Thread.UncaughtExceptionHandler` nhận thông báo khi một luồng chuẩn bị kết thúc do exception không được xử lý. Ví dụ:

```java
Thread worker = new Thread(() -> {
    throw new IllegalArgumentException("invalid task state");
}, "worker-A");

worker.setUncaughtExceptionHandler((thread, failure) -> {
    System.err.println("Worker " + thread.getName()
            + " failed: " + failure.getClass().getSimpleName());
});

worker.start();
```

Đoạn này là **mảnh triển khai** đặt trong phương thức có xử lý vòng đời worker. Handler chạy trong ngữ cảnh xử lý thất bại của worker; nó có thể ghi log, cập nhật trạng thái hoặc phát thông báo qua kênh chung phù hợp. Nó **không phải `catch` của parent thread**, và không tự làm tác vụ bị lỗi chạy tiếp từ nơi vừa ném exception.

Thứ tự chọn handler trong Java bao gồm handler được đặt trực tiếp trên thread hoặc handler của `ThreadGroup`, rồi hành vi mặc định tương ứng. Có thể cấu hình default handler bằng `Thread.setDefaultUncaughtExceptionHandler(...)`, nhưng thiết lập toàn cục đó phải được cân nhắc vì ảnh hưởng đến các luồng không đặt handler riêng. Nếu callback xử lý uncaught exception tự ném exception, JVM thường bỏ qua exception mới từ callback; không nên đặt logic phục hồi phức tạp hay dễ lỗi ở đây.

Khi không có handler tùy chỉnh, JVM thông thường in thông tin uncaught exception ra stderr. Phần chính xác của output có thể phụ thuộc runtime, logging và tên thread, nên không dựa vào nội dung log như một giao thức nhận kết quả. Việc thread chết vì uncaught exception không đồng nghĩa tất cả thread khác phải chết.

**Đáng chú ý:** đối với task chạy qua executor hoặc future, framework có thể **bắt exception để đưa vào đối tượng kết quả**. Vì vậy không được dựa duy nhất vào uncaught handler để phát hiện thất bại của mọi tác vụ bất đồng bộ.

## 7. Phân biệt `Thread.start()`, `Thread.run()` và `Thread.join()`

| API | Chạy ở luồng nào? | Cơ chế lỗi |
| --- | --- | --- |
| `thread.run()` | Luồng **đang gọi** `run()` | Exception có thể lan về `catch` của caller như lời gọi đồng bộ |
| `thread.start()` | Khởi động thực thi `run()` trên **luồng mới** | Lỗi trong `run()` được xử lý theo stack của luồng mới; `start()` không tự ném lại |
| `thread.join()` | Luồng gọi `join()` chờ luồng đích chết | Không truyền ngoại lệ của luồng đích; có thể ném `InterruptedException` ở luồng đang đợi |
| `thread.setUncaughtExceptionHandler(...)` | Đăng ký handler cho luồng đích | Xử lý/quan sát lỗi **không được catch trong đích**, không tự truyền về caller |

Trường hợp `thread.run()` có thể gây nhầm vì cùng một đối tượng `Thread` nhưng thực tế **không có thread mới**. Dùng `run()` trực tiếp thường sai khi mục tiêu là concurrency.

Với `join()`, giá trị quan trọng là **synchronization**: một luồng đã kết thúc thì luồng khác có thể quan sát tác dụng bộ nhớ của nó theo quy tắc *happens-before* khi join thành công. Nhưng *happens-before* về bộ nhớ không đồng nghĩa tự chuyển Throwable qua lại. Nếu muốn nhận lỗi, worker phải đưa kết quả/ngoại lệ vào một kênh chung hoặc dùng abstraction hỗ trợ kết quả như `Future`.

## 8. `ExecutorService`: `execute()` và `submit()` không giống nhau

`ExecutorService` quản lý tập các luồng thực thi task. Hai đường API có khác biệt quan trọng:

### `execute(Runnable)`

`execute` nhận một `Runnable` và **không trả `Future`**. Nếu task ném `RuntimeException` không được bắt, exception có thể đi tới cơ chế xử lý uncaught của luồng worker, tùy executor và cách task được bọc. Với `ThreadPoolExecutor` chuẩn, các trường hợp exception runtime từ task được gửi bằng `execute` có thể làm worker kết thúc đột ngột và kích hoạt xử lý uncaught.

Điều này không cho phép caller viết `try { executor.execute(task); } catch (...)` rồi kỳ vọng bắt exception xảy ra *sau đó* trong worker. `execute` chỉ có thể ném lỗi **đồng bộ lúc nộp task**, chẳng hạn `RejectedExecutionException` nếu executor từ chối công việc.

### `submit(Runnable/Callable)`

`submit` trả `Future`. Exception trong task thường được **ghi vào kết quả Future** thay vì thoát ra uncaught handler của worker. Người gọi phải quan sát kết quả:

```java
Future<Integer> future = executor.submit(() -> {
    throw new IllegalStateException("calculation failed");
});

try {
    Integer value = future.get();
    System.out.println(value);
} catch (ExecutionException ex) {
    Throwable root = ex.getCause();
    System.err.println("Task failed: " + root);
}
```

Đây là **mảnh code trong phương thức** có `ExecutorService executor` đang hoạt động và đã import `Future`, `ExecutionException`. `get()` cũng khai báo `InterruptedException`, nên phương thức bên ngoài phải khai báo/bắt đúng hoặc khôi phục trạng thái interrupt theo hợp đồng.

Một hệ quả thực tế: nếu gọi `submit` nhưng **bỏ kết quả `Future`**, exception của task có thể không được nơi gọi quan sát hoặc ghi log theo mong muốn. Thiết kế nên có nơi chịu trách nhiệm theo dõi từng task/future, nhất là các task làm thay đổi dữ liệu hoặc tài nguyên.

### Future chưa hoàn tất, hủy và timeout

`future.get()` có thể chờ đến khi task hoàn tất. `future.get(timeout, unit)` thêm khả năng ném `TimeoutException` ở luồng gọi **vì chờ quá lâu**; timeout của người đợi không tự đảm bảo task đã dừng. `future.cancel(true)` gửi yêu cầu hủy có thể dùng interrupt, nhưng task cần hợp tác và có thể không dừng ngay; trạng thái công việc đã làm phải được xử lý độc lập.

Khi gặp `ExecutionException`, `getCause()` chỉ ra Throwable khiến tác vụ thất bại. Đừng mặc định cause là loại checked hay unchecked cụ thể; kiểm tra loại và hợp đồng của tác vụ.

## 9. `CompletableFuture`: lỗi được ghi vào stage và lộ ra ở điểm quan sát

`CompletableFuture` biểu diễn kết quả hoặc thất bại của một chuỗi xử lý. Khi hàm bất đồng bộ ném exception, stage liên quan được đánh dấu hoàn thành **exceptionally**. Những điểm quan sát thường dùng:

| Cơ chế | Cách nhận lỗi | Ý nghĩa |
| --- | --- | --- |
| `future.join()` | `CompletionException` với `getCause()` là lỗi nguồn trong trường hợp thông thường | Lấy kết quả đồng bộ, exception dạng unchecked |
| `future.get()` | `ExecutionException` với `getCause()` là nguyên nhân | Giao diện `Future`; còn có `InterruptedException` |
| `future.exceptionally(fn)` | Callback nhận Throwable và có thể tạo giá trị dự phòng | Biến failure thành kết quả nếu có fallback hợp lệ |
| `future.handle((value, error) -> ...)` | Callback thấy thành công hoặc lỗi | Tạo stage kết quả mới theo logic đã chọn |
| `future.whenComplete((value, error) -> ...)` | Callback quan sát kết quả hoặc lỗi | Thường dùng log/metric, không mặc định phục hồi |

Ví dụ mảnh code trong phương thức đã có `CompletableFuture<Integer> future`:

```java
CompletableFuture<Integer> recovered = future.exceptionally(ex -> {
    System.err.println("Async failure: " + ex);
    return -1;
});
```

Đây chỉ là ví dụ biến kết quả lỗi thành `-1`; **chỉ làm như vậy nếu `-1` có nghĩa fallback được hợp đồng cho phép**. Nếu `-1` cũng là dữ liệu hợp lệ thì fallback có thể che mất lỗi. Với tác vụ cập nhật trạng thái hoặc tài chính, việc nuốt lỗi rồi trả mặc định thường rất nguy hiểm.

### Callback có chạy trên luồng main không?

Không được suy luận từ tên `handle`/`whenComplete` rằng callback chạy ở luồng gọi ban đầu. Các continuation không có hậu tố `Async` có thể được thực thi bởi luồng hoàn tất stage trước đó hoặc luồng kích hoạt completion, tùy thời điểm; các phương thức `...Async` có chính sách executor riêng và có thể nhận executor tường minh. Vì vậy:

- Logic callback phải đúng về thread safety.
- Không dựa vào `ThreadLocal` của luồng gọi nếu chưa truyền ngữ cảnh phù hợp.
- Không đặt giả định về thứ tự in log giữa các task đồng thời nếu không có điều kiện đồng bộ.
- Exception mới trong callback có thể khiến **stage tiếp theo** hoàn thành exceptionally; nó không tự trở về một `catch` đã kết thúc trước đó ở main.

## 10. Cách chuyển lỗi từ `Thread` thô sang luồng gọi

Nếu dùng `Thread` thô và muốn caller xác nhận thất bại, có thể dùng kênh kết quả được đồng bộ hóa. Ví dụ dưới đây là **mảnh code trong phương thức khai báo `throws InterruptedException`**, giả sử `doWork()` là phương thức có thể ném `RuntimeException`:

```java
AtomicReference<RuntimeException> failure = new AtomicReference<>();

Thread child = new Thread(() -> {
    try {
        doWork();
    } catch (RuntimeException ex) {
        failure.set(ex);
    }
});

child.start();
child.join();

RuntimeException observed = failure.get();
if (observed != null) {
    throw new IllegalStateException("Child task failed", observed);
}
```

Cần import `java.util.concurrent.atomic.AtomicReference`. `AtomicReference` hỗ trợ chia sẻ kết quả an toàn; thêm vào đó, `join()` thành công tạo quan hệ *happens-before* với tác vụ của child. Điều được chuyển sang caller là **một đối tượng Throwable như dữ liệu**, sau đó caller chủ động ném ngoại lệ mới với `cause` là lỗi của worker.

Đây **không phải** tình huống JVM tự đi ngược stack B sang stack A. Ở thời điểm caller ném `IllegalStateException` mới, JVM unwind **stack caller**; stack trace của cause vẫn giúp chỉ ra nơi lỗi trong worker xuất phát.

Mã chỉ bắt `RuntimeException` theo mục đích minh họa. Với task có checked exception, phải quyết định contract và kênh lưu kiểu ngoại lệ thích hợp. Thông thường không nên `catch (Throwable)` rồi giả định có thể phục hồi mọi `Error`; một lỗi nghiêm trọng có thể không cho phép tiếp tục vận hành an toàn.

Nếu phần mềm đã dùng executor, ưu tiên `Future` hoặc `CompletableFuture` thay vì tự chế kênh lưu lỗi mà dễ bỏ sót hủy, timeout và vòng đời thread.

## 11. Ngoại lệ checked, unchecked và lỗi trong lambda

Trong Java, `Runnable.run()` không khai báo checked exception. Vì vậy lambda truyền cho `new Thread(() -> ...)` không thể đơn giản ném một `IOException` checked ra ngoài nếu chưa xử lý hoặc chuyển đổi nó trong lambda. Có thể:

- Bắt `IOException` bên trong task, báo qua channel hoặc wrapper có `cause`;
- Dùng `Callable<V>` trong executor, vì `Callable.call()` khai báo `throws Exception`, rồi caller quan sát qua `Future.get()`;
- Cấu trúc lại API bất đồng bộ để kết quả hoặc lỗi là giá trị của future/stage.

Ví dụ mảnh code trong phương thức đã có `ExecutorService executor`:

```java
Future<String> fileJob = executor.submit(() -> {
    return java.nio.file.Files.readString(
            java.nio.file.Path.of("config.txt"));
});
```

Ở đây `Callable<String>` có thể ném checked `IOException`; exception đó được giữ làm `cause` của `ExecutionException` khi caller gọi `fileJob.get()`. File không tồn tại hoặc lỗi I/O chỉ phát sinh khi tác vụ chạy; lúc `submit` trả về, caller có thể chưa biết lỗi.

Unchecked exception (`RuntimeException`) cũng không tự “bay qua thread”; khác biệt checked/unchecked thuộc về **kiểm tra kiểu ở compile time và hợp đồng khai báo**, không xóa ranh giới stack.

## 12. `InterruptedException` thuộc về luồng đang chờ

Đây là trường hợp dễ nhầm với “exception từ child truyền về main”:

```java
try {
    child.join();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
```

Mã trên là mảnh trong phương thức phù hợp, đã có `Thread child`; `return` yêu cầu được đặt ở nơi cú pháp Java cho phép. `InterruptedException` xuất hiện vì **luồng đang gọi `join()`** bị interrupt khi đợi, không phải vì child đã ném exception này cho caller. Lời gọi `Thread.currentThread().interrupt()` khôi phục cờ interrupt để các tầng ngoài có thể thấy tín hiệu nếu phương thức không truyền tiếp exception.

Không nên coi interrupt như một “exception gửi từ thread khác”. `interrupt()` đặt tín hiệu yêu cầu hợp tác; những API blocking nhất định sẽ ném `InterruptedException` trong thread bị interrupt, nhưng việc dừng task tùy code và điểm chờ. Không dùng cơ chế interrupt làm đường chuyển lỗi nghiệp vụ chính, hoặc ép worker dừng bằng cơ chế không an toàn.

## 13. Uncaught exception, tiến trình JVM và tài nguyên dùng chung

Một worker chết vì exception không được catch **không bảo đảm JVM tự tắt ngay**. JVM có thể tiếp tục chạy nếu vẫn có những luồng non-daemon đang hoạt động; trạng thái kết thúc tiến trình phụ thuộc các luồng còn lại và môi trường chạy. Đặc biệt:

- Trong server nhiều request, một request handler hoặc worker chết không đồng nghĩa tất cả request khác phải dừng.
- Thất bại của thread không tự hủy các task đang chạy trên thread khác.
- Ngoại lệ trong worker không tự rollback database, file hay mutable state dùng chung.
- `synchronized` monitor tự được nhả khi thoát stack, nhưng dữ liệu đã sửa một phần vẫn có thể ở trạng thái không nhất quán.
- Callback uncaught là nơi **quan sát/ghi nhận** thất bại, không tự bảo đảm khôi phục trạng thái nghiệp vụ.

Điều này đặt ra yêu cầu thiết kế thất bại: nếu một worker đang sở hữu khóa, tài nguyên, transaction hoặc cập nhật nhiều bước, hãy có hợp đồng rõ ràng về cleanup, tính nguyên tử, rollback trong phạm vi tài nguyên, và cách báo trạng thái cho phần còn lại của hệ thống.

Cũng đừng dùng tên thread hoặc thông báo exception làm mã định danh duy nhất của một request: executor có thể tái sử dụng worker cho nhiều task. Dùng correlation ID/task ID tường minh để gắn lỗi với nghiệp vụ cần xử lý.

## 14. Phân biệt lỗi tại thời điểm nộp và tại thời điểm chạy

Một API bất đồng bộ thường có **hai vùng lỗi** khác nhau:

**Vùng 1: ngay khi caller nộp công việc**

Ví dụ `executor.submit(task)` hoặc `executor.execute(task)` có thể thất bại đồng bộ vì executor đã shutdown hoặc vì không nhận thêm task. `RejectedExecutionException` phát sinh trong lời gọi nộp task của caller, nên `try/catch` ngay quanh lời gọi có thể xử lý.

**Vùng 2: khi worker thực thi công việc**

Ví dụ code trong `task` ném `IllegalArgumentException`, `IOException` hay `IllegalStateException`. Caller không bắt được trực tiếp chỉ bằng `try/catch` quanh `submit`. Cần quan sát `Future` hoặc một cơ chế thông báo lỗi tương ứng.

Tương tự, một phương thức `CompletableFuture.supplyAsync(...)` có thể bị lỗi đồng bộ ở giai đoạn tạo/sắp lịch do executor từ chối, còn exception trong supplier sau khi bắt đầu chạy làm stage hoàn thành exceptionally. Việc phân biệt giúp đặt `catch` và cơ chế giám sát đúng nơi.

## 15. Thiết kế đường xử lý lỗi đa luồng có trách nhiệm

Khi viết thư viện hoặc service có task chạy trên thread khác, hãy quyết định ngay từ hợp đồng API:

1. **Ai sở hữu task?** Caller nào phải đợi hoặc nhận kết quả của công việc đó?
2. **Lỗi cần được quan sát bằng gì?** `Future`/`CompletableFuture`, callback, message, log + state machine, hay handler? Log đơn thuần có đủ để caller quyết định không?
3. **Lỗi nào làm tác vụ thất bại?** Checked I/O, lỗi nghiệp vụ, timeout, cancellation và các loại bug được phân loại ra sao?
4. **Tác vụ còn chạy sau timeout/hủy không?** Nếu còn, liệu retry có làm trùng giao dịch hoặc side effect?
5. **Khi một task trong nhóm thất bại, những task khác ra sao?** Tiếp tục, hủy hợp tác, thu thập toàn bộ lỗi, hay fail fast theo hợp đồng?
6. **Trạng thái dùng chung có an toàn không?** Có cần transaction, lock, snapshot, bất biến hoặc idempotency?
7. **Lỗi được ghi ở đâu?** Có ID để ghép log từ worker với kết quả caller hay không?

Một đường triển khai thường rõ ràng là: caller gửi task, giữ reference tới kết quả, chờ/compose ở điểm phù hợp, kiểm tra thành công hoặc lỗi, sau đó thực hiện cleanup và retry theo loại lỗi. Với task dạng fire-and-forget, vẫn cần một nơi định nghĩa **ai nhận báo lỗi, theo dõi hoàn thành và xử lý tác dụng phụ**; “không ai gọi `get()`” không có nghĩa thất bại đã được xử lý.

## 16. Những lỗi hiểu sai thường gặp

| Nhận định sai | Điều đúng |
| --- | --- |
| “Tôi đã bọc `thread.start()` bằng `try/catch` nên bắt được mọi lỗi trong worker.” | `catch` bên caller chỉ bắt lỗi xảy ra **ở caller** khi gọi `start()`; lỗi trong worker đi theo stack worker. |
| “Gọi `join()` thì exception của worker được ném lại.” | `join()` chỉ đợi worker hoàn tất; caller phải có kênh kết quả riêng. |
| “`ExecutorService.submit()` không ghi uncaught log tức là task không lỗi.” | `submit` có thể lưu lỗi trong `Future`. Phải theo dõi `get()` hoặc cơ chế quan sát kết quả phù hợp. |
| “Mọi callback `CompletableFuture` đều chạy ở luồng main.” | Luồng thực thi callback tùy loại continuation, thời điểm hoàn thành và executor. |
| “`CompletionException` là nguyên nhân gốc của lỗi.” | Thông thường đó là **wrapper**; xem `getCause()` và toàn bộ cause chain. |
| “`InterruptedException` từ `join()` là lỗi do thread đích truyền về.” | Đó là lỗi chờ của **thread đang gọi `join()`** khi nó bị interrupt. |
| “Worker ném RuntimeException thì JVM chắc chắn kết thúc.” | Worker có thể kết thúc, các thread khác và tiến trình có thể tiếp tục. |
| “Worker chết thì transaction và biến chia sẻ trở về trước.” | Java exception không tự rollback side effect hoặc mutable state. |
| “Gọi `thread.run()` tương đương `thread.start()`.” | `run()` trực tiếp chạy trong caller; `start()` tạo thực thi trên thread riêng. |
| “Bắt `Throwable` trong mọi worker là cách an toàn để không mất lỗi.” | Bắt cả `Error` có thể che tình trạng nghiêm trọng; cần chính sách lỗi và phục hồi có giới hạn rõ. |

## 17. Cách kiểm thử cơ chế lan truyền

Test concurrency nên xác nhận **nơi lỗi được bắt** và **đường chuyển lỗi**, không chỉ xác nhận log có chữ `Exception`:

- **Đồng bộ:** gọi hàm ném lỗi trực tiếp, dùng `assertThrows` tương ứng và kiểm tra handler trên caller.
- **Thread thô:** đặt handler cho thread; dùng `join()`; xác nhận handler nhận lỗi và `join()` không ném exception nghiệp vụ.
- **Thread.start vs run:** so sánh việc gọi `run()` trực tiếp (cùng stack) với `start()` (stack riêng).
- **Executor.submit:** nộp task ném exception, `future.get()` phải cho `ExecutionException` và đúng `getCause()`.
- **CompletableFuture:** stage thất bại, `join()` cho `CompletionException` và cause phù hợp; `handle`/`exceptionally` tạo kết quả đúng nếu hợp đồng cho phép.
- **Từ chối nộp task:** thử executor đã shutdown và xác nhận lỗi đồng bộ ở caller.
- **Cancel và interrupt:** đảm bảo người đợi xử lý `InterruptedException`; xác minh task có thể dừng hợp tác và các tác dụng phụ được quản lý.
- **Đa luồng thực sự:** dùng `CountDownLatch` hoặc đồng bộ kiểm thử rõ ràng để điều phối thứ tự; tránh phụ thuộc vào `Thread.sleep()` hoặc thời gian tình cờ.

Nếu test cần chờ task, nên có timeout rõ để tránh treo vô hạn. Đặc biệt, kiểm thử ghi nhận lỗi từ uncaught handler cần tránh phụ thuộc vào stdout/stderr có thể xen kẽ giữa thread, hoặc vào tên mặc định của thread tùy runtime.

## 18. Tổng hợp quy tắc làm việc

- Exception **unwind trong một call stack**: handler muốn bắt trực tiếp phải nằm trong luồng đang thực thi đường gọi đó.
- `Thread.start()` tạo một ranh giới bất đồng bộ; `Thread.join()` đợi hoàn thành **nhưng không mang exception trở lại**.
- `Thread.UncaughtExceptionHandler` quan sát lỗi không được xử lý của thread; nó không tự là kênh kết quả cho caller.
- `Future.get()` và `CompletableFuture.get()/join()` cho phép caller **chủ động quan sát thất bại**, thường qua `ExecutionException` hoặc `CompletionException` có `cause`.
- Exception ở worker và tác dụng phụ của worker là hai vấn đề khác nhau: cần mô tả trạng thái sau lỗi, bảo đảm đồng bộ, cleanup, hủy và retry có kiểm soát.

**Ghi nhớ:** *Ngoại lệ không tự vượt qua ranh giới luồng; thông tin về ngoại lệ có thể được chuyển qua một API kết quả hoặc kênh liên lạc rõ ràng.* Hiểu đúng sự khác biệt này giúp thiết kế API đa luồng có đường báo lỗi đáng tin và tránh bỏ sót các tác vụ thất bại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-002">Try-catch ở luồng tạo không tự bắt lỗi ở luồng con</a>

<details>
<summary>Xem chi tiết</summary>

# Try-catch ở luồng tạo không tự bắt lỗi ở luồng con

## 1. Vấn đề: exception đi theo luồng thực thi nào?

Khi một phương thức Java gọi một phương thức khác **trên cùng một thread**, ngoại lệ chưa được bắt có thể lan ngược qua chuỗi lời gọi tới `catch` thích hợp. Nhưng khi code tạo thread khác bằng `Thread.start()`, việc thực thi `run()` diễn ra trên **thread mới với call stack riêng**. Exception phát sinh trong `run()` không “nhảy” ngược sang call stack của thread đã gọi `start()`.

Đây là nguyên tắc quan trọng trong Java Core:

> **`try-catch` bắt exception phát sinh trong luồng thực thi của khối `try`, hoặc được truyền lại qua cơ chế kết quả mà chính luồng đó đọc. Nó không tự bắt lỗi xảy ra bất đồng bộ trong một thread độc lập.**

Xét ý tưởng sau:

~~~java
try {
    Thread worker = new Thread(() -> {
        throw new IllegalStateException("worker failed");
    });
    worker.start();
} catch (IllegalStateException e) {
    System.out.println("caught by creator");
}
~~~

Đây là đoạn minh họa không độc lập, nhưng nội dung cú pháp hợp lệ khi đặt trong một phương thức. `start()` yêu cầu JVM khởi chạy công việc ở thread khác; exception `IllegalStateException` ném từ lambda chạy trong worker. `catch` trên thread tạo **không bắt lỗi này**. Nếu không có `UncaughtExceptionHandler` tùy chỉnh, handler mặc định có thể in stack trace ra luồng lỗi chuẩn; đầu ra cụ thể phụ thuộc môi trường chạy và thiết lập handler.

Tuy vậy, `start()` **có thể tự ném lỗi ngay ở thread gọi**, ví dụ `IllegalThreadStateException` khi cố `start()` cùng một `Thread` lần thứ hai. Lỗi đó thuộc lời gọi `start()` nên có thể được bắt tại thread tạo. Cần phân biệt rõ **lỗi khi yêu cầu tạo/chạy thread** với **lỗi trong công việc mà thread chạy sau đó**.

Sau bài này, bạn sẽ biết cách:
- Giải thích vì sao `catch` ở thread cha không bắt exception của thread con.
- Phân biệt `Thread.start()`, `Thread.run()` và `Thread.join()`.
- Quan sát lỗi của thread bằng `UncaughtExceptionHandler`, hoặc trả lỗi qua `Future`/`CompletableFuture`.
- Xử lý đúng `InterruptedException`, `ExecutionException`, `CompletionException`, cancellation và timeout.
- Thiết kế task có điểm thu nhận lỗi rõ ràng để không bỏ sót thất bại bất đồng bộ.

## 2. Mỗi Java thread có call stack riêng

Hình dung hai luồng:

~~~text
Thread "main"                         Thread "worker"
--------------------------------     --------------------------------
main()                                Thread.run()
  |
  +-- try {                            +-- xử lý task
  |     worker.start()                 |     +-- service.process()
  |     ...                            |           |
  |   } catch (...)                    |           +-- throw
  |                                    |
  +-- chạy tiếp                        +-- không có catch phù hợp
                                        -> UncaughtExceptionHandler
                                        -> thread kết thúc
~~~

Lệnh `start()` tạo ra quan hệ khởi chạy, chứ không phải một lời gọi đồng bộ vào stack của `main`. Khi thread mới gặp exception không được xử lý, JVM tìm handler trên **stack của chính thread đó**. Stack của thread khởi tạo không nằm trong chuỗi gọi này.

Một thread khác có thể quan sát **dữ liệu do thread con ghi** hoặc **một đối tượng kết quả** khi phối hợp đồng bộ đúng. Việc quan sát lỗi cần một trong những cơ chế truyền thông tin đó; quan hệ “tôi tạo ra thread này” tự nó không định nghĩa đường lan truyền exception.

### 2.1. So sánh `run()` và `start()`

~~~java
Runnable task = () -> {
    throw new IllegalStateException("failed");
};

try {
    task.run(); // Gọi đồng bộ trên thread hiện tại.
} catch (IllegalStateException e) {
    System.out.println("caught directly");
}
~~~

Đoạn trên minh họa một `Runnable`, chưa phải file Java có `main`. `run()` chỉ là lời gọi phương thức bình thường: exception lan trên cùng stack nên `catch` nhận được. Nhưng nếu tạo `new Thread(task).start()`, lambda chạy trên thread mới và `catch` quanh `start()` không quan sát exception của lambda.

**Không dùng `run()` thay `start()` chỉ để catch được lỗi**, vì như vậy đã bỏ mục tiêu chạy đồng thời. Hãy chọn cơ chế thu lỗi phù hợp.

## 3. `join()` chờ kết thúc, không chuyển exception của thread con

`Thread.join()` cho phép thread gọi chờ thread đích kết thúc. Nó **không trả đối tượng kết quả** của `run()` và **không ném lại exception chưa bắt** của thread đích.

Có hai nhóm lỗi cần phân biệt:

| Điều xảy ra | Thread nào quan sát trực tiếp? | `join()` có ném lại lỗi công việc không? |
| --- | --- | --- |
| Worker ném `IllegalStateException` và không bắt | Worker, rồi cơ chế uncaught handler | Không |
| Thread đang đợi bị interrupt trong `join()` | Thread đang đợi | `join()` có thể ném `InterruptedException` |
| Worker kết thúc bình thường | Thread tạo có thể thấy sau `join()` | Không có lỗi để ném |
| Worker không bao giờ kết thúc | Thread gọi `join()` vô hạn có thể chờ mãi | Không; cần chính sách chờ/hủy riêng |

Sau khi một lệnh `join()` kết thúc bình thường vì thread đích đã kết thúc, các hành động của thread đích **happens-before** những hành động của thread gọi sau `join()` theo Java Memory Model. Điều đó giúp quan sát state đã được worker ghi theo quy tắc bộ nhớ; nó **không đồng nghĩa nhận exception**. Nếu muốn chuyển lỗi qua state chung, còn cần thiết kế kênh trạng thái và cách xử lý đúng.

Một sai lầm thường gặp là viết:

~~~java
try {
    worker.start();
    worker.join();
} catch (Exception e) {
    // Không thể dựa vào catch này để phát hiện lỗi bên trong worker.
}
~~~

Đây là fragment, giả định `worker` đã khai báo. `Exception` ở đây có thể bắt `InterruptedException` của chính thread đang đợi, hoặc exception khác phát sinh trực tiếp ở thread gọi. Nó không nhận được exception xảy ra trên stack của worker.

## 4. Ví dụ hoàn chỉnh Java 17: ba cơ chế quan sát lỗi

Lưu chương trình sau thành **`ThreadExceptionBoundaryDemo.java`**. Trong cùng một chương trình, nó lần lượt trình diễn:

1. Một `Thread` riêng ném exception; handler của thread nhận lỗi, còn `catch` quanh `start()/join()` không nhận.
2. Một task chạy qua `ExecutorService.submit()`; lỗi được lưu trong `Future` và được báo khi gọi `get()`.
3. Một stage `CompletableFuture` thất bại; caller quan sát lỗi tại `join()` qua `CompletionException`.

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadExceptionBoundaryDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread child = new Thread(() -> {
            throw new IllegalStateException("child crashed");
        }, "child");

        child.setUncaughtExceptionHandler((thread, error) ->
                System.out.println("handler: " + thread.getName()
                        + " -> " + error.getMessage()));

        try {
            child.start();
            child.join();
        } catch (IllegalStateException error) {
            System.out.println("creator catch");
        }
        System.out.println("creator continued; terminated=" + !child.isAlive());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> task = pool.submit(() -> {
                throw new IllegalArgumentException("task failed");
            });
            try {
                System.out.println(task.get());
            } catch (ExecutionException error) {
                System.out.println("future: " + error.getCause().getMessage());
            }

            CompletableFuture<String> stage =
                    CompletableFuture.supplyAsync(() -> {
                        throw new UnsupportedOperationException("stage failed");
                    }, pool);
            try {
                stage.join();
            } catch (CompletionException error) {
                System.out.println("stage: " + error.getCause().getMessage());
            }
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+ từ thư mục chứa file:

~~~bash
javac --release 17 ThreadExceptionBoundaryDemo.java
java ThreadExceptionBoundaryDemo
~~~

Stdout của ví dụ:

~~~text
handler: child -> child crashed
creator continued; terminated=true
future: task failed
stage: stage failed
~~~

### 4.1. Phân tích output và thứ tự

**Dòng 1:** exception trong `child` không có `catch` trong chính thread; JVM gọi `UncaughtExceptionHandler` do ta thiết lập riêng cho nó. Handler in ra `child crashed`. Handler chạy theo cơ chế xử lý exception chưa bắt của thread đang kết thúc; đừng xem đây là kết quả trực tiếp trả từ `Thread.start()`.

**Dòng 2:** thread `main` đã gọi `child.join()` nên chờ con kết thúc, rồi xác nhận `!child.isAlive() == true`. Dòng `creator catch` **không xuất hiện**, vì worker không ném exception qua stack của `main`.

**Dòng 3:** `submit` trả `Future<Integer>`. Task ném `IllegalArgumentException`, `Future.get()` ném `ExecutionException` có `getCause()` là exception gốc. Không cần “bắt lỗi worker” quanh lệnh `submit`.

**Dòng 4:** `CompletableFuture.supplyAsync` hoàn thành ngoại lệ. `join()` ném `CompletionException`, trong đó `getCause()` là `UnsupportedOperationException("stage failed")`.

Các cơ chế đồng bộ trong chương trình làm cho thứ tự dòng in phía trên xác định: `main` đợi `child` hoàn tất trước khi in dòng thứ hai; nó đợi task từ `Future.get()` trước khi tạo stage tiếp theo trên executor một luồng, rồi `join()` chờ stage đó. Ở ví dụ khác, nếu nhiều thread cùng ghi `System.out` mà không có quan hệ thứ tự như vậy, **không nên cam kết thứ tự stdout**.

Trong ứng dụng thực tế, nếu exception được tạo từ nguồn không kiểm soát, vẫn cần kiểm tra kiểu của `getCause()` và xử lý hoặc chuyển tiếp theo hợp đồng; chỉ in `getMessage()` là minh họa tối giản cho đoạn học này. Nếu caller bị interrupt trong `main`, chữ ký `throws InterruptedException` của ví dụ cho phép lan lỗi, thay vì âm thầm bỏ qua.

## 5. Bắt tại chính thread thực thi

Nếu biết cách xử lý lỗi ở bên trong task, đặt `try-catch` **trong lambda hoặc `Runnable.run()`**:

~~~java
Runnable task = () -> {
    try {
        performWork();
    } catch (IllegalArgumentException e) {
        recordFailure(e);
    }
};
new Thread(task).start();
~~~

Đây là **mã minh họa**, phụ thuộc các hàm `performWork()` và `recordFailure(Exception)` chưa định nghĩa. Vì `catch` thuộc code chạy trong worker, nó có thể nhận lỗi đó. Nhưng sau khi bắt, bạn phải xác định rõ điều kiện task được xem là thất bại hay đã phục hồi thành công. Nếu chỉ log exception rồi cho rằng task đã thành công, thông tin thất bại sẽ không đến nơi điều phối.

Với tác vụ xử lý một request, có thể hợp lý khi catch một exception nghiệp vụ, chuyển thành kết quả nghiệp vụ và tiếp tục; nhưng với lỗi bất thường không khôi phục được, có thể cần truyền lại qua kênh kết quả hoặc báo cho bộ điều phối. Tránh `catch (Throwable ignored)` và đừng nuốt `Error` của JVM chỉ để giữ thread tiếp tục chạy.

### 5.1. Checked exception bên trong `Runnable`

Phương thức `Runnable.run()` không khai báo `throws` cho checked exception. Vì vậy một lambda `Runnable` không thể ném trực tiếp `IOException` mà không xử lý hoặc chuyển đổi.

~~~java
Runnable fileTask = () -> {
    try {
        readFile();
    } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException("Reading failed", e);
    }
};
~~~

Đây là fragment minh họa với phương thức `readFile() throws IOException` chưa định nghĩa. Việc bọc bằng `UncheckedIOException` bảo toàn `cause`; nếu task không bắt tiếp, exception unchecked này sẽ đi tới cơ chế xử lý lỗi của task/thread. Chuyển checked thành unchecked là một lựa chọn hợp đồng, không phải cách duy nhất: một `Callable<T>` có `call() throws Exception` có thể phù hợp hơn khi sử dụng `ExecutorService.submit()`.

## 6. `UncaughtExceptionHandler`: nhận lỗi khi thread không bắt được

`Thread.UncaughtExceptionHandler` được gọi khi một thread sắp kết thúc do exception chưa được bắt. Có thể đăng ký riêng:

~~~java
Thread t = new Thread(() -> {
    throw new IllegalArgumentException("bad worker input");
}, "validation-worker");

t.setUncaughtExceptionHandler((thread, failure) -> {
    System.err.println(thread.getName() + ": " + failure);
});

t.start();
~~~

Đoạn này không có `main` độc lập. Handler nhận tham số `Thread` và `Throwable`. Cơ chế này hữu ích cho ghi nhận lỗi của những raw thread hoặc bảo vệ process khỏi việc thất bại biến mất khỏi hệ thống quan sát.

Ngoài handler riêng, Java hỗ trợ `Thread.setDefaultUncaughtExceptionHandler(...)` cho một default handler toàn JVM. Nếu không có handler riêng, chuỗi ủy quyền liên quan `ThreadGroup` và default handler được áp dụng theo quy tắc API. Cần hiểu chính xác môi trường chạy trước khi thay đổi default handler toàn tiến trình: thư viện, framework, máy chủ ứng dụng hoặc runner có thể đang dùng cơ chế ghi nhận riêng.

Những giới hạn thiết kế cần nhớ:
- Handler **không biến exception thành exception trên thread tạo**.
- Handler thường hoạt động lúc thread sắp kết thúc vì lỗi chưa bắt; nó không phải cơ chế “khôi phục và chạy lại từ chỗ throw”.
- Handler không nên thực hiện những công việc phức tạp dễ ném exception khác; nếu handler cũng lỗi thì JVM không bảo đảm sẽ có một handler thứ hai cứu kết quả đó.
- Handler không thay thế hệ thống thu kết quả có kiểu. Nếu caller cần biết task thành công/thất bại và tiếp tục theo nhánh, `Future` hoặc `CompletableFuture` thường thích hợp hơn.
- Với task đã được `ExecutorService.submit()` bọc vào `Future`, exception thường được **lưu trong Future**; không thể kỳ vọng uncaught handler nhận được lỗi chỉ vì callable ném exception.

Trong production, thường nên gắn tên thread, mã tác vụ, correlation ID và dữ liệu ngữ cảnh **không nhạy cảm** cho log, thay vì chỉ gọi `printStackTrace()` tự do từ nhiều nơi.

## 7. `ExecutorService`: khác biệt giữa `execute` và `submit`

Một `ExecutorService` nhận công việc để thực thi bởi các worker được quản lý. Cơ chế xử lý exception phụ thuộc phương thức gửi:

| API | Kết quả trả về | Quan sát thất bại từ mã gọi |
| --- | --- | --- |
| `execute(Runnable)` | `void` | Không có `Future`; cần xử lý bên trong task, cơ chế uncaught logging/giám sát của executor, hoặc cơ chế ứng dụng khác |
| `submit(Runnable)` | `Future<?>` | `get()` có thể ném `ExecutionException` nếu task ném exception |
| `submit(Callable<T>)` | `Future<T>` | `get()` trả giá trị hoặc ném `ExecutionException` |
| `invokeAll(...)` | `List<Future<T>>` | Phải quan sát từng `Future` để xác định task nào thất bại |
| `invokeAny(...)` | Giá trị từ một task hoàn thành thành công | Có thể ném `ExecutionException` nếu không có task thành công, cùng các lỗi điều phối phù hợp |

**`submit` không ném exception từ task vào caller khi gọi submit.** Nó trả một đối tượng mà sau này có thể biểu diễn trạng thái chạy/chưa xong, thành công, thất bại hoặc bị hủy. Nếu gọi `submit` rồi bỏ qua `Future`, task có thể thất bại mà không có nhánh ứng dụng nào xử lý lỗi.

Với `execute`, nếu `Runnable.run()` để lọt unchecked exception, ứng xử của worker và cách ghi nhận phụ thuộc executor/ThreadFactory và implementation. Ở `ThreadPoolExecutor` thông thường, task kết thúc bất thường có thể kích hoạt cơ chế xử lý uncaught; tuy nhiên đừng coi `execute` là “sẽ ném lỗi về thread gọi”, cũng không xây logic nghiệp vụ dựa vào stack trace mặc định.

### 7.1. Xử lý `Future.get()` đúng hợp đồng

~~~java
try {
    String value = future.get();
    use(value);
} catch (java.util.concurrent.ExecutionException e) {
    Throwable taskFailure = e.getCause();
    reportTaskFailure(taskFailure);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Fragment giả định `future`, `use` và `reportTaskFailure` tồn tại, và được đặt trong phương thức có thể `return`. `ExecutionException` biểu thị **task đã hoàn tất bằng exception**; nguyên nhân gốc lấy qua `getCause()`. `InterruptedException` biểu thị **thread đang đợi bị yêu cầu ngắt**, không phải bằng chứng rằng task đã thất bại.

Nếu cần giới hạn thời gian chờ, dùng `future.get(timeout, unit)`; khi quá hạn, phương thức ném `TimeoutException`. **Timeout không tự hủy task.** Caller quyết định tiếp tục chờ, báo trạng thái chưa xác định hay gọi `future.cancel(true)` phù hợp. Hủy với `mayInterruptIfRunning=true` chỉ cố yêu cầu interrupt worker đang chạy; việc dừng có hiệu quả hay không tùy task có hợp tác xử lý interrupt và trạng thái tác vụ. Khi `Future` bị hủy, `get()` có thể ném unchecked `CancellationException`.

### 7.2. Tập hợp nhiều Future

Nếu gọi `invokeAll` hoặc gửi 10 tác vụ bằng `submit`, việc tất cả Future đã ở trạng thái done **không có nghĩa tất cả thành công**. `Future.isDone()` trả true cho cả hoàn tất thành công, thất bại và hủy. Muốn có báo cáo đúng, truy xuất từng kết quả và phân biệt:
- giá trị `get()`: thành công;
- `ExecutionException`: thất bại của task;
- `CancellationException`: tác vụ bị hủy;
- `InterruptedException`: thread điều phối bị interrupt khi đợi.

Khi một task thất bại, hãy quyết định có tiếp tục các task khác, hủy các task còn lại, hay trả báo cáo tổng hợp. Không được suy từ một future thất bại rằng các future đã chạy trước đó không gây side effect.

## 8. `CompletableFuture`: exception đi theo chuỗi stage

`CompletableFuture<T>` vừa đại diện cho kết quả bất đồng bộ, vừa cung cấp các stage biến đổi. Một stage có thể **hoàn thành ngoại lệ** (*complete exceptionally*). Các phép nối stage sẽ truyền thất bại tới stage tiếp theo khi không có cơ chế phục hồi phù hợp.

### 8.1. Cách quan sát lỗi

| API | Khi stage thất bại | Cách sử dụng |
| --- | --- | --- |
| `join()` | Ném unchecked `CompletionException`; trường hợp bị hủy có thể ném `CancellationException` | Tiện khi caller không muốn checked exception |
| `get()` | Ném checked `ExecutionException`; cũng có thể `InterruptedException` | Dùng khi cần giao thức `Future` |
| `get(timeout, unit)` | Giống `get()` và thêm `TimeoutException` | Giới hạn thời gian chờ |
| `exceptionally(fn)` | Biến failure thành giá trị thay thế nếu xử lý thành công | Phục hồi theo chính sách |
| `handle((value, error) -> ...)` | Nhận cả nhánh thành công và lỗi, tạo kết quả mới | Tổng hợp hai nhánh |
| `whenComplete((value, error) -> ...)` | Quan sát kết quả và thực hiện side effect, thường không biến failure thành success | Ghi metric/log/cleanup có kiểm soát |

Ví dụ đoạn biểu thức:

~~~java
CompletableFuture<String> recovered =
        CompletableFuture.<String>supplyAsync(() -> {
            throw new IllegalStateException("remote data failed");
        }).exceptionally(error -> "fallback");
~~~

Đây là fragment, cần import `java.util.concurrent.CompletableFuture` và một phương thức bao quanh. Nếu supplier thất bại, stage `exceptionally` trả `fallback` nên `recovered.join()` có thể trả chuỗi thành công. Nhưng **việc trả fallback là quyết định nghiệp vụ**, không phải lúc nào cũng đúng. Với giao dịch thanh toán, không thể thay lỗi gửi lệnh bằng “success” tùy ý.

Một stage nối bằng `thenApply` có thể bị bỏ qua khi stage trước hoàn thành ngoại lệ; lỗi được truyền trong chuỗi, nên cần xử lý tại điểm quan sát thích hợp. Nếu tạo chuỗi:

~~~java
CompletableFuture<String> result = loadAsync()
        .thenApply(value -> transform(value))
        .whenComplete((value, failure) -> recordOutcome(value, failure));
~~~

thì đây chỉ là fragment minh họa với các hàm chưa khai báo. Nếu `loadAsync` hoặc `transform` thất bại, `whenComplete` có thể nhận lỗi để ghi nhận; nó không mặc nhiên xóa trạng thái thất bại. Nếu chính action của `whenComplete` ném exception thì cách stage kết quả hoàn tất phụ thuộc việc stage gốc đã thành công hay lỗi, theo quy tắc `CompletionStage`.

### 8.2. Không bỏ quên chuỗi bất đồng bộ

~~~java
CompletableFuture.runAsync(() -> saveSomething());
System.out.println("request accepted");
~~~

Fragment giả định `saveSomething`. Lệnh in chỉ thể hiện việc đã lên lịch xử lý, **không phải đã lưu thành công**. Nếu không lưu future để quan sát hoặc không gắn một nhánh xử lý lỗi đáng tin cậy, thất bại có thể trở thành một trạng thái không được ứng dụng xử lý.

Không chỉ bắt exception quanh `runAsync(...)` rồi cho là đã xử lý lỗi bên trong lambda: exception có thể đến sau khi lời gọi `runAsync` trả về. Khi trả future cho caller, cần thể hiện trong API liệu caller phải `join/get`, đăng ký callback, hay hệ thống sẽ quản lý task tới kết thúc.

## 9. `InterruptedException` và hủy task không giống task exception

`InterruptedException` có ý nghĩa riêng trong xử lý concurrency. Khi một thread đang ngủ, chờ `join` hoặc chờ `Future.get()` mà bị interrupt, API chờ có thể ném `InterruptedException`. Điều này phản ánh **thread chờ bị yêu cầu ngắt**, chứ không nói gì chắc chắn về kết quả của thread/task đang được chờ.

Trong mã `Runnable`, mẫu phổ biến:

~~~java
Runnable pollingTask = () -> {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            Thread.sleep(1_000);
            pollOnce();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }
};
~~~

Đây là fragment dùng `pollOnce()` giả định. Khi bị interrupt trong `sleep`, Java ném `InterruptedException` và thường đã xóa interrupt status trong quá trình ném. Gọi lại `Thread.currentThread().interrupt()` giúp tầng trên biết yêu cầu ngắt; sau đó kết thúc task thay vì nuốt lỗi rồi tiếp tục chạy vô hạn. Nếu phương thức của bạn cho phép `throws InterruptedException`, một lựa chọn khác là truyền tiếp lỗi.

Không bao giờ coi `interrupt()` là cơ chế bắt buộc giết thread lập tức. Tác vụ CPU-bound không kiểm tra interrupt, thư viện chặn không hỗ trợ interrupt hoặc thao tác I/O cụ thể có thể tiếp tục. Việc gọi `Future.cancel(true)` cũng chỉ yêu cầu hủy theo hợp đồng Future và khả năng hợp tác của tác vụ, không tự rollback mọi hiệu ứng đã có.

## 10. Truyền lỗi bằng dữ liệu có kiểm soát

Nếu không dùng executor nhưng caller cần lấy lỗi từ một `Thread` tự tạo, có thể dùng một kênh chia sẻ có đồng bộ rõ ràng, chẳng hạn `AtomicReference<Throwable>`, hoặc một `BlockingQueue` chứa kết quả có kiểu. Một mẫu đơn giản:

~~~java
import java.util.concurrent.atomic.AtomicReference;

class CaptureThreadFailure {
    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                throw new IllegalStateException("validation failed");
            } catch (RuntimeException e) {
                failure.set(e);
            }
        });
        worker.start();
        worker.join();

        if (failure.get() != null) {
            System.out.println(failure.get().getMessage());
        }
    }
}
~~~

Nếu lưu thành `CaptureThreadFailure.java`, biên dịch/chạy:

~~~bash
javac --release 17 CaptureThreadFailure.java
java CaptureThreadFailure
~~~

Stdout:

~~~text
validation failed
~~~

Ở đây **worker chủ động bắt và lưu exception** vào `AtomicReference`; main gọi `join()` để chờ, rồi đọc lỗi. `join()` không tự bắt lỗi — nó chỉ tạo điểm phối hợp khiến main biết khi nào task đã hoàn tất. Với mẫu này, exception sẽ không tự chạy tới `UncaughtExceptionHandler` vì đã được catch trong worker. Nếu caller muốn ném lại thì tự quyết định cách chuyển đổi, ví dụ xét loại exception và bọc thành exception phù hợp với hợp đồng của caller.

Trong chương trình phức tạp, nên ưu tiên `Future`/`CompletableFuture` hoặc một abstraction quản lý lifecycle; chúng xử lý trạng thái completion/cancellation thuận tiện hơn việc tự phối hợp nhiều cờ, references và handlers. Với task có giá trị hoặc nhiều loại lỗi nghiệp vụ dự kiến, cũng có thể trả một result type có cấu trúc thay vì ném exception cho các trạng thái bình thường.

## 11. Vị trí catch đúng khi thiết kế ứng dụng đa luồng

Một thiết kế thực tế có các ranh giới khác nhau:

~~~text
Thread A: nhận yêu cầu
    |
    +-- submit/khởi chạy task trên Thread B
    |        |
    |        +-- catch lỗi có thể phục hồi ngay tại B
    |        +-- lỗi khác -> Future/CompletionStage/handler
    |
    +-- A tiếp tục làm việc
    |
    +-- A lấy kết quả/callback khi phù hợp
             |
             +-- success -> xử lý giá trị
             +-- failure -> kiểm tra cause và quyết định
             +-- cancel/timeout -> xử lý trạng thái điều phối
~~~

Chọn cơ chế dựa trên nhu cầu:
- **Task tự chịu trách nhiệm phục hồi:** xử lý bằng `try-catch` ngay trong task; nếu không thể phục hồi, phản ánh trạng thái thất bại.
- **Caller cần biết task thành công hay thất bại:** dùng `Callable` + `Future`, hoặc `CompletableFuture`/`CompletionStage` và quan sát kết quả ở ranh giới điều phối.
- **Raw thread lỗi bất thường:** đăng ký `UncaughtExceptionHandler` để log/giám sát, đồng thời tránh dựa vào nó để truyền kết quả nghiệp vụ.
- **Nhiều task cùng tham gia một công việc:** phải có quy tắc chờ/hủy/tổng hợp kết quả; thread khác đã hoàn thành có thể đã gây side effect trước khi một task thất bại.
- **Task không cần phản hồi cho request hiện tại:** hệ thống vẫn cần một nơi sở hữu lifecycle, ghi nhận exception và cơ chế retry/đối soát nếu có side effect bền vững.

Chú ý **lifecycle**: nếu trả response từ thread nhận yêu cầu trước khi task bất đồng bộ xong, cần phân biệt “đã tiếp nhận” với “đã xử lý thành công”. Exception đến sau đó không thể tự sửa response đã trả; thường phải thông báo trạng thái qua job ID, callback, polling hoặc kênh giao tiếp tương ứng.

### 11.1. Không kết luận mọi task thành công chỉ vì chờ xong

`Thread.join()` thành công, `CountDownLatch.await()` trả về, `Future.isDone() == true`, hoặc mọi task đã hoàn thành đều chỉ nói về một **mốc đồng bộ/trạng thái hoàn tất**. Muốn biết thành công, cần kiểm tra kết quả hoặc kênh lỗi được thiết kế. Tương tự, `ExecutorService.awaitTermination(...)` cho biết executor đã chấm dứt sau shutdown trong thời hạn, chứ không báo rằng mọi task không ném exception.

Đây cũng là chỗ exception và **bảo toàn trạng thái** giao nhau: worker có thể ghi một phần dữ liệu, rồi ném exception. Dù main biết lỗi qua Future, những ghi nhận, HTTP request hay phép thay đổi trước lỗi vẫn có thể đã xảy ra. Thu exception giúp quan sát thất bại; muốn phục hồi phải dùng transaction, bù trừ, idempotency hoặc quy tắc trạng thái phù hợp.

## 12. Lỗi phổ biến và cách sửa

| Anti-pattern | Điều sai | Hướng sửa |
| --- | --- | --- |
| Đặt `try-catch` ngoài `thread.start()` để bắt lỗi worker | Khác call stack | Catch trong worker, handler hoặc kết quả Future |
| Tin `join()` sẽ ném lỗi từ `run()` | `join()` chỉ đợi, có thể bị interrupt | Kết hợp `join` với kênh trạng thái, hoặc dùng Future |
| Gọi `run()` để “bắt được exception” | Chạy đồng bộ, không tạo thread | Dùng `start()` và cơ chế thu lỗi phù hợp |
| `submit()` rồi không lưu/đọc Future | Failure có thể không được caller quan sát | Giữ Future và gọi `get`/gắn quy trình quản lý |
| Catch `ExecutionException` nhưng chỉ in `getMessage()` | Mất ngữ cảnh lỗi gốc | Xem `getCause()`, chuyển đổi/ghi nhận thích hợp |
| Catch `InterruptedException` rồi bỏ qua | Mất tín hiệu hủy khi tầng trên cần biết | Truyền tiếp hoặc restore interrupt status và kết thúc |
| Catch `Throwable` để “không thread nào chết” | Che `Error` và lỗi hỏng trạng thái | Chỉ catch loại cần xử lý, có chính sách thất bại |
| Cho rằng `cancel(true)` chấm dứt mọi tác vụ | Interrupt mang tính hợp tác | Thiết kế timeout/hủy đúng loại task |
| Cho rằng `whenComplete` đã phục hồi lỗi | Callback thường chỉ quan sát | `exceptionally` hoặc `handle` khi thật sự muốn phục hồi |
| Xử lý exception xong nhưng không xử lý partial side effect | Thu được lỗi ≠ rollback | Thiết kế transaction, idempotency và compensation |

### 12.1. “Main không catch nhưng chương trình vẫn tiếp tục” có nghĩa gì?

Một exception không bắt trong worker thông thường làm worker đó kết thúc; **không tự làm JVM thoát lập tức** khi thread khác vẫn chạy. Điều này còn phụ thuộc số lượng và trạng thái daemon/non-daemon thread, chính sách ứng dụng và quá trình shutdown. Ngược lại, một executor có thể thay thread worker sau sự cố tùy implementation, khiến ứng dụng trông vẫn hoạt động dù task cụ thể đã thất bại.

Vì vậy, việc process còn sống, REST server vẫn trả request khác, hoặc log chỉ xuất hiện một stack trace **không chứng minh tác vụ quan trọng đã thành công**. Cần cơ chế theo dõi lỗi/khả năng hoàn thành ở cấp công việc.

## 13. Kiểm thử ngoại lệ ở nhiều thread

Đừng chỉ assert rằng lời gọi `start()` không ném. Tạo những kiểm thử đặc tả:
1. **Raw Thread:** đặt handler riêng, cho worker ném exception, gọi `join()`, assert handler nhận đúng loại và nội dung; assert `catch` quanh `start()/join()` không phải nơi nhận lỗi.
2. **Worker tự catch:** cho worker ghi lỗi vào kênh kết quả; sau `join()` kiểm tra dữ liệu, không phụ thuộc `Thread.sleep` để đoán khi nào xong.
3. **ExecutorService.submit:** task ném exception; `get()` ném `ExecutionException` và `getCause()` là lỗi mong đợi.
4. **CompletableFuture:** stage thất bại; `join()` ném `CompletionException`, `get()` ném `ExecutionException`; `exceptionally/handle` có thể chuyển thành kết quả khác theo chính sách.
5. **Interruption:** interrupt thread đang chờ; bảo đảm code xử lý đúng `InterruptedException` và giữ/công bố tín hiệu hủy theo hợp đồng.
6. **Timeout/cancellation:** kiểm tra task chưa bị xem là thành công chỉ vì caller hết thời gian chờ, và quy tắc hủy phù hợp.
7. **Side effect:** task ghi dữ liệu rồi lỗi; khẳng định trạng thái sau lỗi đúng thiết kế, không giả định thu exception nghĩa là rollback.

Với test nhiều thread, tránh khẳng định chính xác thứ tự log khi không có đồng bộ. Ưu tiên `CountDownLatch`, `Future.get(timeout, unit)`, `join` có thời hạn hoặc công cụ test concurrency phù hợp để điều phối có giới hạn; hạn chế dùng `Thread.sleep` như cách “chờ đại” trong test. Khi test hoàn thành, cần shutdown executor và giải phóng resource thuộc sở hữu test.

## 14. Checklist áp dụng

- [ ] Đã xác định exception phát sinh ở **thread nào** và khối `catch` chạy trên thread nào.
- [ ] Không chờ `catch` quanh `start()` bắt exception trong `run()`.
- [ ] Dùng `join()` đúng vai trò chờ kết thúc; có kênh kết quả riêng nếu cần biết success/failure.
- [ ] Thread độc lập có cơ chế quan sát lỗi không bắt, ví dụ `UncaughtExceptionHandler` thích hợp.
- [ ] Task `submit` được theo dõi thông qua Future; `ExecutionException.getCause()` được xử lý.
- [ ] Stage `CompletableFuture` được quan sát hoặc phục hồi có chủ đích; phân biệt `get` với `join`.
- [ ] `InterruptedException`, timeout và cancellation có chính sách riêng, không nhầm với exception của công việc.
- [ ] Lỗi không bị nuốt và không bị đánh đồng với task thành công.
- [ ] Nếu task có side effect, đã xác định cần rollback, bù trừ hay đối soát khi task thất bại.
- [ ] Có kiểm thử riêng cho exception ở worker, ở thread điều phối, trong callback và khi task bị hủy.

### Bài tập thực hành

Viết một API `loadAll(List<String> ids)` dùng `ExecutorService` để tra cứu nhiều mã song song. Mỗi task có thể trả bản ghi, báo “không tìm thấy” hoặc ném exception I/O. Hãy xác định cách caller phân biệt đủ ba kết quả; cách thu tất cả failure từ các Future mà không bỏ quên tác vụ còn lại; cách hủy khi request timeout; và lý do `try-catch` đặt quanh vòng lặp gọi `submit()` không thay thế được việc quan sát `Future.get()`.

Sau đó thay bằng `CompletableFuture`, thử `handle` để biến mỗi stage thành một kết quả có cấu trúc, rồi kiểm thử trường hợp một stage thất bại trước khi các stage khác hoàn thành. Đừng cam kết thứ tự hoàn thành trừ khi chính thiết kế đã áp dụng đồng bộ hoặc sắp xếp kết quả.

**Điểm cốt lõi:** Một exception lan theo call stack nơi nó phát sinh. Thread tạo và thread con có stack riêng; muốn chuyển thất bại qua ranh giới này, bạn phải **thiết kế kênh quan sát rõ ràng** như handler, Future hoặc CompletionStage.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-003">Thread.UncaughtExceptionHandler</a>

<details>
<summary>Xem chi tiết</summary>

# Thread.UncaughtExceptionHandler: xử lý ngoại lệ không được bắt trong luồng Java

Khi một `Thread` thực thi mã và có một exception hoặc error thoát ra khỏi điểm bắt đầu của luồng mà không được xử lý, luồng sẽ chấm dứt do lỗi. Java cung cấp giao diện `Thread.UncaughtExceptionHandler` để nhận thông báo về **`Throwable` chưa được bắt**, cùng thông tin luồng gặp lỗi. Đây là điểm quan sát thất bại cuối cùng của một luồng, hữu ích cho logging, cảnh báo, ghi nhận sự cố và phối hợp với cơ chế giám sát.

Tuy nhiên, **handler không phải một khối `catch` có thể tiếp tục công việc tại vị trí lỗi**. Khi nó được gọi, thread đang đi đến kết thúc bất thường; muốn phục hồi tác vụ, trả kết quả hay thử lại, ứng dụng phải có cơ chế riêng. Đặc biệt, lỗi trong tác vụ gửi qua `ExecutorService.submit` hoặc trong `CompletableFuture` có thể được **lưu trong đối tượng kết quả** và không đi qua uncaught-exception handler của worker thread.

## 1. Hiểu đúng “uncaught” theo ranh giới của Thread

Ví dụ đơn giản:

```java
Thread worker = new Thread(() -> {
    throw new IllegalStateException("Dữ liệu trạng thái không hợp lệ");
});
worker.start();
```

Đây là đoạn minh họa chưa có `main` độc lập. Một `IllegalStateException` thoát khỏi `Runnable.run()` mà không được `catch` trong worker. JVM chuẩn bị chấm dứt worker do lỗi và gọi handler có hiệu lực cho thread ấy. Nếu không có handler tùy chỉnh, runtime thông thường sẽ in thông tin exception và stack trace ra `System.err` thông qua cơ chế của `ThreadGroup`.

So sánh với đoạn sau:

```java
Thread worker = new Thread(() -> {
    try {
        riskyOperation();
    } catch (IllegalStateException ex) {
        reportAndRecover(ex);
    }
});
```

Đây cũng là đoạn minh họa, với `riskyOperation` và `reportAndRecover` do ứng dụng định nghĩa. Nếu `catch` thực sự xử lý exception và không ném lỗi mới, handler không được gọi, vì exception không còn “uncaught”.

Điều cần phân biệt:

- **Exception được bắt bên trong thread:** mã `catch` có thể xử lý, thay đổi kết quả, thử lại có kiểm soát hoặc quyết định chấm dứt.
- **Exception không được bắt và thoát khỏi thread:** `UncaughtExceptionHandler` được gọi như cơ chế báo cáo cuối; thread không quay lại tiếp tục từ câu lệnh thất bại.
- **Exception được framework/task wrapper bắt rồi lưu lại:** có thể không có exception thoát khỏi worker thread; caller lấy lỗi qua `Future`, `CompletionStage` hoặc callback framework.

Một thread bị lỗi không tự động làm tất cả thread khác dừng. Ứng dụng có thể vẫn hoạt động với trạng thái không còn đúng nếu thread vừa chết là thành phần quan trọng; vì vậy handler có thể chỉ là một phần của hệ thống giám sát.

## 2. Giao diện Thread.UncaughtExceptionHandler

Giao diện lồng trong `java.lang.Thread` có phương thức:

```java
@FunctionalInterface
public interface UncaughtExceptionHandler {
    void uncaughtException(Thread thread, Throwable error);
}
```

Đoạn trên mô tả chữ ký API để học, không định nghĩa lại giao diện JDK trong một chương trình ứng dụng.

Hai tham số mang ý nghĩa:

| Tham số | Ý nghĩa | Dùng để làm gì? |
| --- | --- | --- |
| `Thread thread` | Luồng mà exception không được bắt | Ghi tên luồng, ID hoặc metadata để định vị thành phần lỗi. |
| `Throwable error` | Exception hoặc error thoát khỏi luồng | Ghi loại lỗi, thông điệp, stack trace, cause và suppressed exceptions nếu cần. |

Vì tham số thứ hai là `Throwable`, handler có thể nhận cả `RuntimeException` lẫn `Error` như `AssertionError` hoặc `OutOfMemoryError`. **Không nên cố xử lý mọi `Error` như lỗi nghiệp vụ có thể phục hồi.** Khi nguồn lực JVM đã cạn, ngay cả logging hoặc cấp phát thêm bộ nhớ cũng có thể thất bại.

Có thể cài handler bằng lambda:

```java
Thread.UncaughtExceptionHandler handler = (thread, error) -> {
    System.err.println("Luồng lỗi: " + thread.getName());
    error.printStackTrace(System.err);
};
```

Đây là snippet hợp lệ khi đặt trong phương thức hoặc initializer phù hợp. Trong môi trường production, thường dùng logger và hệ thống quan sát có cấu trúc; không phụ thuộc vào việc in ra console như hợp đồng ứng dụng.

## 3. Đăng ký handler cho từng thread và cho toàn ứng dụng

Java có hai điểm cấu hình chính:

**Per-thread handler:**

```java
Thread thread = new Thread(task, "import-worker");
thread.setUncaughtExceptionHandler((t, e) -> {
    System.err.println(t.getName() + " gặp lỗi: " + e);
});
thread.start();
```

Snippet này giả định `task` đã được định nghĩa. Handler được đặt **trước `start()`** để tránh race giữa lúc thread bắt đầu thất bại và lúc đăng ký handler. Handler gắn cho một đối tượng `Thread` cụ thể.

**Default handler dùng chung:**

```java
Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
    System.err.println("Uncaught trong " + t.getName() + ": " + e);
});
```

Handler mặc định là cấu hình tĩnh ở cấp JVM, có ảnh hưởng rộng tới nhiều luồng sử dụng đường xử lý mặc định. Trong ứng dụng nhiều thư viện/framework, việc thay đổi nó có thể ảnh hưởng đến cơ chế báo lỗi đã tồn tại. Chỉ nên đặt tại ranh giới khởi tạo ứng dụng khi có quyền quản lý cấu hình đó.

Một số điểm API cần nhớ:

- `thread.setUncaughtExceptionHandler(handler)` gắn handler riêng cho thread; có thể đặt `null` để dùng cơ chế fallback.
- `thread.getUncaughtExceptionHandler()` trả về handler được gắn trực tiếp, hoặc `ThreadGroup` tương ứng nếu chưa gắn trực tiếp. **Không nên hiểu** kết quả trả về luôn là chính default handler hiện được đặt.
- `Thread.setDefaultUncaughtExceptionHandler(handler)` đặt default handler; `Thread.getDefaultUncaughtExceptionHandler()` đọc giá trị ấy.
- Khi có `ThreadGroup` custom hoặc môi trường quản lý thread riêng, luồng báo lỗi còn phụ thuộc cách group/framework xử lý.

## 4. Thứ tự chọn handler của JVM

Với một thread đang kết thúc do uncaught exception, thứ tự diễn giải cơ bản:

1. Nếu thread có handler được đặt trực tiếp bằng `setUncaughtExceptionHandler`, JVM gọi handler ấy.
2. Nếu không, thread sử dụng `ThreadGroup` như uncaught-exception handler.
3. Implementation mặc định của `ThreadGroup.uncaughtException` có thể ủy quyền lên group cha; ở group gốc nó có thể chuyển cho default handler nếu có.
4. Nếu không có default handler phù hợp, implementation chuẩn của `ThreadGroup` thường in thông tin lỗi ra `System.err`. Có quy tắc riêng đối với `ThreadDeath`.

Điểm quan trọng: **per-thread handler có độ ưu tiên thực tế cao hơn default handler** theo cơ chế nói trên. Nhưng không nên coi mọi thread đều chắc chắn đến default handler: một `ThreadGroup` ghi đè `uncaughtException` có thể xử lý theo cách khác, và framework có thể chủ động bắt lỗi trước khi exception thoát khỏi thread.

Sơ đồ khái niệm:

```text
Throwable thoát khỏi phần thực thi của Thread
              |
              v
Thread có handler riêng?
       /               \
     Có               Không
     |                  |
     v                  v
Handler riêng       ThreadGroup xử lý
                        |
                 Có thể chuyển lên group cha
                        |
                 Có thể dùng default handler
                        |
               Fallback theo ThreadGroup
```

Handler chỉ được thông báo khi thất bại **thực sự đến ranh giới kết thúc thread**. Câu lệnh `throw` ở bên trong một tác vụ không tự bảo đảm handler được gọi nếu lớp bao bên ngoài đã bắt exception.

## 5. Ví dụ Java 17 hoàn chỉnh: default và per-thread handler

Ví dụ sau tạo tuần tự hai thread. Thread thứ nhất không có handler riêng nên dùng default handler trong môi trường Java chuẩn; thread thứ hai có handler riêng. Cả hai ném exception chưa được bắt. `join()` giúp main chờ từng luồng hoàn tất, nhờ đó thứ tự dòng output có thể dự đoán.

Tạo tệp `UncaughtHandlerDemo.java`:

```java
public class UncaughtHandlerDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                System.out.println(
                    "DEFAULT " + thread.getName()
                        + " " + error.getClass().getSimpleName()
                        + ": " + error.getMessage()
                );
            });

            Thread first = new Thread(() -> {
                throw new IllegalStateException("boom A");
            }, "default-worker");

            first.start();
            first.join();

            Thread second = new Thread(() -> {
                throw new IllegalArgumentException("boom B");
            }, "custom-worker");

            second.setUncaughtExceptionHandler((thread, error) -> {
                System.out.println(
                    "CUSTOM " + thread.getName()
                        + " " + error.getClass().getSimpleName()
                        + ": " + error.getMessage()
                );
            });

            second.start();
            second.join();

            System.out.println("MAIN finished");
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac -encoding UTF-8 UncaughtHandlerDemo.java
java UncaughtHandlerDemo
```

Kết quả chuẩn trong runtime Java thông thường:

```text
DEFAULT default-worker IllegalStateException: boom A
CUSTOM custom-worker IllegalArgumentException: boom B
MAIN finished
```

Phân tích:

- `first` có exception thoát khỏi `Runnable` và không có handler riêng; default handler in dòng bắt đầu bằng `DEFAULT`.
- `second` được gắn handler riêng **trước** khi bắt đầu nên dòng bắt đầu bằng `CUSTOM`. Default handler không xử lý cùng exception này trong luồng xử lý tiêu chuẩn.
- `join()` của từng thread chặn main đến khi thread đó chấm dứt. Trong ví dụ không có race khi main in `MAIN finished` vì từng thread đã hoàn tất.
- `finally` khôi phục default handler trước đó nhằm hạn chế tác động toàn cục nếu chạy ví dụ trong môi trường tích hợp. Một ứng dụng thực không nên đổi handler mặc định liên tục theo từng tác vụ.
- Handler ở đây cố ý in thông tin tối giản, không in full stack trace để output dễ kiểm tra. Khi vận hành thực, stack trace cùng cause/suppressed exception thường hữu ích để chẩn đoán.

`main` khai báo `throws InterruptedException` vì `join()` là thao tác blocking có checked exception đó. `InterruptedException` của main là vấn đề riêng và **không phải** lỗi `IllegalStateException`/`IllegalArgumentException` đã thoát khỏi các worker. Nếu main bị ngắt trong khi chờ, cách xử lý cần tuân thủ chính sách interruption của chương trình.

## 6. Handler được chạy ở đâu và làm được những gì?

Thông thường JVM gọi `uncaughtException(thread, error)` trên luồng đang kết thúc do lỗi; nó không tạo một “luồng cứu hộ” chuyên biệt để xử lý logic của ứng dụng. Do đó, công việc nặng hoặc blocking trong handler có thể làm chậm việc kết thúc thread, giữ tài nguyên hoặc gây deadlock trong hoàn cảnh bất lợi.

Handler hợp lý thường chỉ:

- Ghi nhận thất bại cùng danh tính thread, loại exception và ngữ cảnh an toàn.
- Thông báo cho bộ phận giám sát hoặc coordinator (nếu thiết kế ứng dụng có cơ chế đó).
- Kích hoạt chính sách đánh dấu thành phần không lành mạnh hoặc yêu cầu hệ thống dừng, với cân nhắc an toàn và tính nhất quán.
- Giữ handler tối giản, không phát sinh lỗi phụ và không giả định mọi hệ thống ngoại vi còn hoạt động khi JVM đang thiếu nguồn lực.

Handler **không thể** quay ngược instruction pointer về dòng ném lỗi, không khôi phục stack frame đã unwound và không biến thread chết thành thread đang chạy bình thường. Nếu muốn chạy lại một tác vụ, cần scheduler hoặc supervisor tạo công việc/đối tượng thread khác theo chính sách rõ ràng.

Javadoc của `Thread.UncaughtExceptionHandler` quy định exception do chính `uncaughtException` ném ra sẽ bị JVM **bỏ qua**. Do đó, không nên xây dựng hệ thống theo giả định rằng thất bại của logger/handler sẽ được chuyển tiếp sang một handler dự phòng.

Nếu chính handler ném lỗi:

```java
thread.setUncaughtExceptionHandler((t, e) -> {
    throw new IllegalStateException("handler also failed");
});
```

Đây chỉ là snippet minh họa, với `thread` tồn tại trong ngữ cảnh phù hợp. Lỗi của handler không được coi là phương pháp báo lỗi đáng tin cậy; thiết kế handler đơn giản và bảo đảm có chiến lược vận hành khi logging gặp trục trặc.

## 7. Khác biệt cốt lõi: Thread trực tiếp và ExecutorService.submit

Một nhầm lẫn thường gặp là nghĩ **mọi exception ném trong một tác vụ async sẽ vào `UncaughtExceptionHandler`**.

Xét lời gọi:

```java
Thread direct = new Thread(() -> {
    throw new IllegalStateException("direct failed");
});
direct.start();
```

Exception không được bắt có thể đến handler của `direct`.

Nhưng khi dùng:

```java
Future<?> future = executor.submit(() -> {
    throw new IllegalStateException("task failed");
});
```

Đây là snippet giả định `executor` đã được tạo. Với `ExecutorService.submit` chuẩn, tác vụ thường được bọc trong `FutureTask`. Exception của tác vụ được **bắt và ghi nhận trong `Future`**. Worker của executor có thể tiếp tục xử lý công việc khác; per-thread handler thường **không nhận** lỗi nghiệp vụ này.

Caller cần gọi `future.get()` để quan sát thất bại; với exception ở task, `get()` ném `ExecutionException` và nguyên nhân gốc nằm trong `getCause()`. Nếu caller bỏ qua `Future`, lỗi có thể không được quan sát theo ý định, dù nhiệm vụ thực sự đã thất bại.

### Ví dụ Java 17 hoàn chỉnh với submit và Future

Tạo tệp `FutureFailureDemo.java`:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureFailureDemo {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> result = executor.submit(() -> {
                throw new IllegalStateException("task failed");
            });

            try {
                result.get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                System.out.println(
                    "FUTURE " + cause.getClass().getSimpleName()
                        + ": " + cause.getMessage()
                );
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

Lệnh chạy:

```bash
javac -encoding UTF-8 FutureFailureDemo.java
java FutureFailureDemo
```

Stdout:

```text
FUTURE IllegalStateException: task failed
```

Giải thích:

- `submit` trả một `Future`, trong đó lưu tình trạng hoàn thành và lỗi của task.
- `result.get()` chờ task hoàn thành và ném `ExecutionException` khi task lỗi.
- `ExecutionException.getCause()` chứa `IllegalStateException` gốc; caller có thể quyết định retry, rollback, báo lỗi hoặc dừng workflow.
- `executor.shutdown()` ngừng nhận task mới và cho phép các task đã gửi tiếp tục hoàn thành; `shutdown()` không đồng nghĩa chờ tất cả task xong. Ở ví dụ này, `get()` đã đợi đúng task đó, nên output xác định.
- Không cần `UncaughtExceptionHandler` để nhận lỗi task được `submit` bắt. Nếu muốn logging tự động cho mọi tác vụ, cần thiết kế ở mức task wrapper, kết quả `Future` hoặc cơ chế quan sát của executor.

`result.get()` còn có thể ném `InterruptedException` khi thread chờ bị ngắt; phương thức `main` khai báo checked exception này. Trường hợp task bị hủy, `get()` có thể ném `CancellationException` (unchecked), khác với `ExecutionException` do task thất bại.

## 8. ExecutorService.execute và sự khác nhau với submit

Hai cách gửi một `Runnable` vào executor có thể làm thay đổi cách exception được quan sát:

| Cách thực thi | Cơ chế quan sát lỗi thường gặp | Lưu ý |
| --- | --- | --- |
| `new Thread(task).start()` | Exception thoát khỏi `run()` có thể đến uncaught handler | Thread chấm dứt khi exception thoát ra ngoài. |
| `executor.execute(task)` | Ở `ThreadPoolExecutor` chuẩn, exception unchecked không được bắt trong task có thể thoát khỏi worker và được báo qua handler/logging của worker | Chi tiết phụ thuộc triển khai executor, `ThreadFactory` và wrapper; không dựa duy nhất vào handler. |
| `executor.submit(task)` | Exception được lưu vào `Future`; caller quan sát qua `get()` | Có thể thất lạc tín hiệu lỗi nếu không ai kiểm tra `Future`. |
| `CompletableFuture.supplyAsync(...)` | Thất bại được ghi trong `CompletableFuture` | Quan sát qua `whenComplete`, `handle`, `exceptionally` hoặc `join/get`. |

Một `ThreadPoolExecutor` có thể sử dụng hook `afterExecute(Runnable r, Throwable t)` để quan sát tác vụ; nhưng với `FutureTask` được tạo bởi `submit`, tham số `t` có thể là `null` ngay cả khi task thất bại vì lỗi đã được lưu trong `Future`. Implementation cần kiểm tra `Future` và xử lý `ExecutionException` một cách có chủ đích nếu chọn cách này. Không nên tự gọi `get()` trên một `Future` chưa hoàn thành từ hook trong tình huống có thể gây chờ không cần thiết; với `afterExecute` của task đã hoàn thành, vẫn cần xử lý interruption/cancellation cẩn thận.

Quan trọng hơn, `ExecutorService` là abstraction thực thi tác vụ, không phải một `Thread` do client luôn sở hữu. Worker thread có thể được tái sử dụng, thay thế hoặc quản lý bởi framework; việc gắn handler trực tiếp cho một worker không đồng nghĩa quan sát được mọi lỗi trong các task.

## 9. CompletableFuture và exception bất đồng bộ

`CompletableFuture` là ví dụ rõ ràng về một lỗi được ghi nhận theo **kết quả bất đồng bộ** thay vì thoát khỏi điểm khởi đầu của thread.

```java
CompletableFuture<Integer> stage =
    CompletableFuture.supplyAsync(() -> {
        throw new IllegalArgumentException("bad input");
    });

stage.whenComplete((value, failure) -> {
    if (failure != null) {
        // Quan sát lỗi của stage, không trông chờ handler của worker.
    }
});
```

Đây là snippet minh họa, giả định import `java.util.concurrent.CompletableFuture`. Lỗi có thể được biểu diễn bằng `CompletionException` bọc cause tùy điểm quan sát và hình dạng chuỗi stage. `join()` thường ném `CompletionException` khi stage hoàn thành exceptional; `get()` dùng `ExecutionException` cho thất bại thông thường. Code xử lý nên dựa vào hợp đồng API của stage.

Tránh tạo callback `whenComplete` chỉ để log rồi bỏ qua kết quả mà không kiểm soát hành vi phụ hoặc luồng xử lý tiếp theo. Khi lỗi cần ảnh hưởng quyết định nghiệp vụ, hãy chuyển nó vào pipeline xử lý kết quả theo một chiến lược nhất quán.

## 10. Thread.join không truyền exception của worker về caller

`Thread.join()` chỉ đợi thread kết thúc. Nó **không ném lại exception chưa được bắt của worker** trên thread gọi `join()`.

Ví dụ với một worker tự tạo:

```java
Thread worker = new Thread(() -> {
    throw new IllegalStateException("worker failed");
});
worker.start();
worker.join();

// Đã kết thúc thread, nhưng join không có kiểu kết quả để báo worker thất bại.
```

Snippet trên giả định nằm trong phương thức có xử lý `InterruptedException`. Sau `join()`, việc `worker.isAlive() == false` không cho biết worker thành công hay thất bại. Handler có thể ghi lỗi nhưng caller muốn phân biệt phải có cơ chế trao đổi trạng thái, ví dụ `Future`, `BlockingQueue`, `CountDownLatch` kết hợp nơi giữ kết quả, hoặc một wrapper trả kết quả có cấu trúc.

Nếu caller cần nhận hoặc xử lý thất bại như một kết quả tính toán, dùng `Future` hoặc API composition thường phù hợp hơn dùng global uncaught handler làm đường truyền lỗi.

## 11. Trường hợp đặc biệt: handler được gọi trên luồng “sắp chết”

Cách nghĩ đúng là handler nhận thông tin khi thread không thể tiếp tục đường thực thi cũ. Điều này dẫn đến các quyết định thiết kế:

**Không retry trực tiếp ngay trong handler theo cách tùy tiện.** Nếu thread vừa chết do lỗi bất ngờ, việc chạy lại một tác vụ có side effect có thể làm thao tác lặp, ghi dữ liệu hai lần hoặc rơi vào vòng lỗi vô hạn. Retry cần điều kiện, backoff, idempotency và giới hạn số lần.

**Không dựa vào handler để giải phóng mọi tài nguyên.** Resource được mở trong thread nên được đóng bởi `try-with-resources`/`finally` gần nơi tạo và sở hữu. Handler không biết toàn bộ ownership và có thể không chạy trong mọi kiểu chấm dứt tiến trình. Nó cũng có thể được gọi khi trạng thái chương trình đã bị lỗi nghiêm trọng.

**Không xem handler là nơi sửa trạng thái dùng chung.** Tại thời điểm thất bại, các invariant có thể đã bị phá. Một supervisor có thể đánh dấu thành phần không lành mạnh và dừng pipeline thay vì tự ý tiếp tục.

**Không khóa chéo dễ dẫn đến deadlock.** Nếu lỗi xảy ra trong thread đang giữ monitor/lock hoặc đang tham gia shutdown, handler gọi ngược sang chính subsystem đó có thể treo. Ưu tiên ghi nhận tối giản và truyền sự kiện thất bại cho hệ thống giám sát phù hợp.

## 12. Exception, Error, interruption và shutdown

Một số loại kết thúc công việc có cách xử lý khác nhau:

| Tình huống | Uncaught handler có nhất thiết được gọi? | Hướng xử lý |
| --- | --- | --- |
| `RuntimeException` thoát khỏi `Thread.run()` | Có, theo cơ chế uncaught-exception thông thường của thread | Ghi nhận nguyên nhân và trạng thái thành phần. |
| `Error` thoát khỏi thread | Handler nhận `Throwable`, nhưng việc báo cáo có thể không đáng tin trong lỗi nghiêm trọng | Tránh recovery liều lĩnh, cân nhắc fail-fast. |
| `InterruptedException` được bắt và xử lý đúng | Không, nếu không có exception mới thoát khỏi thread | Tuân thủ chính sách hủy; phục hồi cờ interrupt khi phù hợp. |
| `InterruptedException` bị bọc thành unchecked rồi thoát khỏi `run()` | Có thể có handler vì unchecked exception mới đã uncaught | Giữ cause và chính sách interrupt. |
| `FutureTask` bắt lỗi task được `submit` | Thông thường không | Kiểm tra `Future.get()` và cause. |
| `Thread.stop()` (API đã bị deprecated, không an toàn) | Không nên sử dụng để quản lý lỗi/cleanup | Dùng cooperative cancellation. |
| JVM/OS cưỡng bức kết thúc tiến trình | Không có bảo đảm handler hoặc `finally` sẽ chạy | Dựa vào recovery, supervision và quản lý tài nguyên ngoài tiến trình khi cần. |

`InterruptedException` là checked exception và `Runnable.run()` không khai báo checked exception, vì thế task thường cần bắt nó. Nếu muốn kết thúc sạch, có thể phục hồi interrupt rồi `return`:

```java
@Override
public void run() {
    try {
        blockingOperation();
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
    }
}
```

Đây là snippet minh họa; `blockingOperation` do ứng dụng cung cấp. Trường hợp này không tạo uncaught exception và handler không được gọi. Cách truyền tiếp hay dừng còn phụ thuộc semantics của thread.

`ThreadDeath` là `Error` đặc biệt từng liên quan đến `Thread.stop()`; `ThreadGroup` có hành vi fallback đặc biệt đối với nó. Đừng dựa vào cơ chế cũ này để hủy luồng.

## 13. Gắn handler bằng ThreadFactory trong ứng dụng nhiều worker

Nếu một thành phần thực sự **quản lý việc tạo thread**, có thể cấu hình per-thread handler ở `ThreadFactory`:

```java
ThreadFactory factory = task -> {
    Thread thread = new Thread(task, "service-worker");
    thread.setUncaughtExceptionHandler((t, error) -> {
        System.err.println(t.getName() + " failed: " + error);
    });
    return thread;
};
```

Đây là snippet minh họa (cần import `java.util.concurrent.ThreadFactory`). `ThreadFactory` thường được truyền cho các executor như `Executors.newFixedThreadPool(int, ThreadFactory)` để gắn cấu hình vào worker được tạo.

Nhưng **factory chỉ định handler cho thread**, không khiến exception do `submit` lưu trong `Future` tự nhiên thoát ra ngoài. Đừng nhầm “mọi worker có handler” với “mọi task exception đều được quan sát”.

Thiết kế production thường kết hợp:
- Handler cho thất bại *uncaught* của thread.
- Xử lý kết quả `Future` hoặc pipeline `CompletableFuture` cho task failure.
- Logging/metrics/tracing với định danh tác vụ và xử lý dữ liệu nhạy cảm.
- Chính sách dừng/restart phù hợp tại service/supervisor, không đặt trong handler riêng lẻ.

ThreadFactory còn được dùng để gắn tên dễ nhận diện và các thuộc tính thread theo yêu cầu. Không nên tự ý đặt `Thread.setDaemon(true)` nếu thay đổi semantics shutdown của ứng dụng mà chưa hiểu rõ hậu quả.

## 14. Java 21+ virtual threads và phạm vi kiến thức

Ví dụ ở đây dùng **platform thread**, chạy trên Java 17+. Từ Java 21, virtual threads cũng là `Thread` và có cơ chế xử lý uncaught exception, nhưng việc quản lý lifecycle và task thường dựa nhiều hơn vào structured concurrency hoặc executor. Nếu tác vụ virtual thread được bọc bởi abstraction lưu kết quả, cần phân biệt lỗi của task với uncaught exception theo chính abstraction đó.

Không nên suy luận rằng virtual thread bị lỗi sẽ làm toàn bộ nhóm công việc dừng hoặc tự động truyền exception sang thread cha. Cần đọc hợp đồng cụ thể của API tạo và chờ task đang sử dụng; nhóm tác vụ và cơ chế cancel/join có semantics riêng.

## 15. Một handler vận hành tốt cần ghi nhận điều gì?

Một bản ghi sự cố hữu ích có thể gồm:

- Thời điểm và danh tính tiến trình/service.
- Tên/ID thread, vai trò worker, tác vụ hoặc correlation ID nếu có.
- Loại `Throwable`, message, stack trace, chuỗi cause và suppressed exceptions.
- Mức độ nghiêm trọng hoặc trạng thái thành phần (healthy/degraded/unavailable).
- Thông tin đã được **lọc** để không lộ token, thông tin đăng nhập, dữ liệu cá nhân hoặc nội dung nhạy cảm.

Message exception không phải API dữ liệu ổn định. Đừng dùng so khớp message từ exception không được bắt để quyết định retry. Nếu tác vụ có thể thất bại vì quy tắc nghiệp vụ, nên chuyển thành lỗi có cấu trúc **trước khi** ra đến uncaught handler.

Một handler chỉ in `error.getMessage()` có thể bỏ mất nguyên nhân sâu hơn; ngược lại in toàn bộ exception vào hệ thống log công khai có thể làm lộ dữ liệu. Cần chọn logger và quyền truy cập phù hợp với môi trường.

## 16. Lỗi thường gặp

**Coi handler là `catch` để thread tiếp tục.** Sai vì handler chạy khi thread đang kết thúc do uncaught exception. Logic phục hồi cần đặt trong workflow hoặc supervisor.

**Chỉ đăng ký default handler rồi cho rằng `submit` đã được giám sát.** `submit` có thể bắt exception vào `Future`. Cần kiểm tra kết quả hoặc wrap các tác vụ.

**Dùng `join` như cơ chế phát hiện lỗi.** `join` chỉ chờ hoàn thành; không cho biết success/failure.

**Gắn handler sau `start`.** Thread có thể ném exception trước khi gắn. Đặt handler trước khi bắt đầu hoặc thông qua factory.

**Đặt lại default handler từ từng library.** Đây là cấu hình cấp JVM, có thể xung đột với framework hoặc code ứng dụng. Nếu library cần quan sát lỗi của chính nó, thường nên quản lý thread/task và handler của riêng mình.

**Handler ghi log rồi tự ném exception.** Exception do handler ném ra bị JVM bỏ qua; dễ mất cả báo cáo ban đầu nếu logging chưa hoàn tất.

**Làm công việc blocking hoặc nguy hiểm trong handler.** Handler có thể chạy trong bối cảnh thread đã lỗi và còn liên quan lock; việc kết thúc thread có thể bị chậm hoặc deadlock.

**Gọi `System.exit` vô điều kiện trong handler.** Có thể chấm dứt cả tiến trình vì lỗi của một worker không quan trọng. Chính sách dừng cả service phải do tầng vận hành và criticality quyết định.

**Bắt `Throwable` trong mọi `Runnable` chỉ để ngăn handler.** Có thể che `Error` nghiêm trọng và phá khả năng quan sát lỗi. Chỉ bắt những gì task thực sự có thể xử lý.

**Không quản lý tài nguyên khi lỗi uncaught.** Handler không thay cho `finally` hoặc `try-with-resources`; cleanup phải gắn với resource owner.

## 17. Kiểm thử handler và luồng quan sát lỗi

Các test nên dựa vào hành vi công khai, tránh so khớp format stack trace mặc định giữa JVM:

1. Với một `Thread` trực tiếp ném `IllegalStateException`, gắn per-thread handler thu nhận `Throwable` vào một cấu trúc thread-safe; gọi `start` rồi `join` và xác nhận handler được gọi, đúng thread/name/cause.
2. Với default handler, lưu handler cũ và khôi phục bằng `finally`; tránh làm test chạy song song bị ảnh hưởng bởi cấu hình toàn cục. Nếu runtime hoặc `ThreadGroup` tùy chỉnh có semantics khác, test phải có giả định môi trường rõ ràng.
3. Xác nhận handler per-thread được chọn thay cho default handler trong mô hình thread bình thường.
4. Với `ExecutorService.submit`, tạo task thất bại và xác nhận `Future.get` ném `ExecutionException` có cause đúng; không dùng việc handler có/không log như phép xác nhận duy nhất.
5. Với `CompletableFuture`, xác nhận stage hoàn thành exceptional và callback xử lý nhận đúng trạng thái.
6. Với thread cleanup tài nguyên, xác nhận `try-with-resources` hoặc `finally` thực thi dù task ném exception.
7. Đảm bảo handler không giữ cả tiến trình treo khi logger hoặc thành phần quan sát lỗi thất bại.

Ví dụ unit test thực có thể dùng `AtomicReference<Throwable>` hoặc `BlockingQueue<Throwable>` để ghi nhận exception từ handler mà không phụ thuộc vào console. Hãy đặt timeout phù hợp khi `join` hoặc `Future.get` trong môi trường CI để test không treo vô hạn nếu worker gặp deadlock.

## 18. Tự kiểm tra

**Câu 1:** `Runnable.run` ném `IllegalArgumentException` không ai bắt, và thread có per-thread handler. Handler có thể làm cho `run` chạy tiếp từ dòng sau `throw` không?

**Trả lời:** Không. Handler là nơi nhận thông báo lỗi cuối khi thread đang kết thúc. Muốn tiếp tục hoặc retry, ứng dụng phải có logic riêng và một execution context thích hợp.

**Câu 2:** Cùng một lỗi `IllegalStateException`, vì sao `new Thread(task).start()` có thể gọi handler nhưng `executor.submit(task)` thường không?

**Trả lời:** Với thread trực tiếp, exception có thể thoát khỏi entry point của thread. Với `submit`, task được bọc trong `FutureTask` và lỗi được lưu vào `Future` để caller lấy bằng `get`; worker không nhất thiết có uncaught exception.

**Câu 3:** `join()` trả về bình thường có nghĩa worker thành công?

**Trả lời:** Không. `join()` chờ thread kết thúc và không truyền exception của worker. Cần kênh trạng thái/kết quả riêng.

**Câu 4:** Tại sao một library không nên tự tiện thay default handler?

**Trả lời:** Default handler là cấu hình toàn JVM, có thể ảnh hưởng thread của framework hoặc phần khác của ứng dụng. Library nên quản lý lỗi của chính nó bằng handler/thread factory hoặc task result, và để chủ ứng dụng quyết định default policy.

**Câu 5:** Thread đang ghi file ném uncaught exception. Handler nên trực tiếp đóng mọi file của thread?

**Trả lời:** Không nên dựa vào handler làm owner tài nguyên. Mã sở hữu file phải dùng `try-with-resources`/`finally` để cleanup đúng phạm vi. Handler có nhiệm vụ quan sát thất bại và hỗ trợ xử lý vận hành.

## 19. Ghi nhớ khi thiết kế

`Thread.UncaughtExceptionHandler` trả lời câu hỏi **“điều gì xảy ra khi một Throwable thoát khỏi thread mà không được bắt?”**: runtime chọn handler và thông báo thất bại trước khi thread chấm dứt. Nó **không** thay `catch`, không tự truyền lỗi sang `join`, không thay cơ chế xử lý `Future`/`CompletableFuture` và không làm thread tự hồi phục.

Với thread tự tạo, cài per-thread handler trước `start`, có thể dùng default handler ở cấp ứng dụng để quan sát rộng. Với executor và pipeline bất đồng bộ, ưu tiên quan sát lỗi qua **hợp đồng kết quả của task**. Luôn làm cleanup gần resource owner và dùng supervisor hoặc chính sách service để quyết định ảnh hưởng của một thread thất bại.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-004">Handler riêng, ThreadGroup và handler mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Handler riêng, ThreadGroup và handler mặc định khi luồng Java gặp ngoại lệ chưa được bắt

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Nhận diện tình huống một ngoại lệ thoát khỏi phương thức `run()` của luồng mà không được bắt.
- Giải thích vai trò của `Thread.UncaughtExceptionHandler`, handler riêng của từng `Thread`, `ThreadGroup` và handler mặc định toàn tiến trình.
- Xác định đúng thứ tự xử lý: handler gán riêng cho luồng, handler của nhóm luồng và chuỗi nhóm cha, sau đó đến default handler hoặc cách in lỗi dự phòng.
- Viết handler có thể quan sát lỗi mà không làm gián đoạn hoặc che giấu đường xử lý khác; biết cách gắn handler **trước khi khởi động luồng**.
- Phân biệt ngoại lệ thoát khỏi luồng với ngoại lệ đã được lưu trong `Future` của `ExecutorService.submit()`.
- Kiểm thử hành vi xử lý ngoại lệ một cách xác định mà không phụ thuộc vào thứ tự lập lịch ngẫu nhiên hoặc nội dung stack trace.

## 2. Khi nào uncaught exception handler được gọi?

Mỗi luồng Java có một đường thực thi. Khi một `Throwable` lan truyền ra ngoài đoạn thực thi cao nhất của luồng mà không được code ứng dụng bắt, JVM dùng cơ chế xử lý **uncaught exception** cho luồng đó.

Ví dụ minh họa trong một chương trình có luồng riêng:

~~~java
Thread worker = new Thread(() -> {
    throw new IllegalStateException("Tác vụ không thể tiếp tục");
}, "worker");
worker.start();
~~~

Khối `run()` ném lỗi và không có `catch` bao quanh. Đây là **ngoại lệ không được bắt ở luồng worker**. Nó không tự động nhảy sang một khối `catch` đang bao quanh lời gọi `worker.start()` của luồng cha.

Phân biệt ba hoạt động:

1. **Tạo luồng:** `new Thread(...)` tạo đối tượng, chưa chạy công việc.
2. **Khởi động:** `start()` yêu cầu JVM bắt đầu đường thực thi mới. `start()` bình thường không đợi toàn bộ `run()` hoàn tất.
3. **Thực thi công việc:** nếu exception thoát khỏi `run()`, quá trình xử lý ngoại lệ chưa bắt diễn ra trên đường kết thúc của chính luồng đó.

`Thread.join()` chỉ đợi luồng kết thúc, **không chuyển ngoại lệ của luồng con thành ngoại lệ được ném ở luồng gọi `join()`**. Nếu muốn thu thập kết quả thành công/thất bại để luồng khác xử lý, cần thêm cơ chế truyền kết quả, chẳng hạn `Future`, biến trạng thái được đồng bộ hóa hoặc hệ thống giám sát thích hợp.

### Handler làm gì và không làm gì?

`Thread.UncaughtExceptionHandler` là một interface có một phương thức:

~~~java
@FunctionalInterface
public interface UncaughtExceptionHandler {
    void uncaughtException(Thread t, Throwable e);
}
~~~

Đây là **dạng chữ ký API được rút gọn để học**, không phải lớp cần tự khai báo. JVM gọi phương thức tương ứng khi có ngoại lệ chưa được bắt, truyền vào **luồng gặp lỗi** và **đối tượng lỗi**.

Handler thường dùng để ghi log, lưu thông tin sự cố, gửi tín hiệu tới thành phần quản lý hoặc thống nhất cách báo cáo lỗi nghiêm trọng. Nó **không khôi phục ngăn xếp và không làm công việc của luồng tiếp tục tại dòng sau lệnh ném lỗi**.

Một ngoại lệ có thể được xử lý ngay tại nơi phát sinh nếu code có giải pháp phục hồi phù hợp. Uncaught handler chỉ là điểm quan sát cuối cùng cho lỗi đã thoát khỏi phần xử lý của luồng.

## 3. Ba mức xử lý: từng luồng, ThreadGroup, mặc định

Có ba cơ chế cần phân biệt:

| Cơ chế | Đăng ký/cấu hình | Phạm vi chủ yếu |
| --- | --- | --- |
| Handler riêng của một luồng | `thread.setUncaughtExceptionHandler(handler)` | Chỉ luồng được gán handler |
| Handler qua `ThreadGroup` | `ThreadGroup.uncaughtException(thread, error)` | Luồng trong nhóm không có handler riêng; có thể chuyển tiếp lên nhóm cha |
| Handler mặc định | `Thread.setDefaultUncaughtExceptionHandler(handler)` | Điểm dự phòng toàn JVM qua cơ chế nhóm luồng khi không có handler được chọn ở mức gần hơn |

Các nhóm luồng của JDK cũng thực hiện `Thread.UncaughtExceptionHandler`. Theo hợp đồng `Thread.getUncaughtExceptionHandler()`, nếu luồng đã được gán handler riêng thì trả về handler đó; nếu chưa, nhóm `ThreadGroup` của luồng là handler được dùng.

Điều này giải thích vì sao **default handler không đơn giản là giá trị trả về trực tiếp của `getUncaughtExceptionHandler()`** khi chưa gán handler riêng: thông thường phương thức sẽ trả về đối tượng `ThreadGroup`, và chính logic mặc định của `ThreadGroup` mới tìm tới handler mặc định.

### Sơ đồ ưu tiên

~~~text
Ngoại lệ thoát khỏi run() của Thread T
                    |
                    v
T có handler riêng không?
      | Có                    | Không
      v                       v
Gọi handler riêng       Gọi T.getThreadGroup()
      |                       |
      |                       v
      |               ThreadGroup.uncaughtException(T, e)
      |                       |
      |              +--------+---------------------+
      |              | nhóm override và xử lý     |
      |              |                            |
      |              | logic mặc định của nhóm    |
      |              | -> có nhóm cha: chuyển lên |
      |              | -> tới nhóm gốc:            |
      |              |    kiểm tra default handler |
      |              |    nếu không có: in lỗi     |
      v              v                            v
   Kết thúc xử lý báo cáo; luồng lỗi không tự chạy tiếp
~~~

Sơ đồ mô tả **đường xử lý thường gặp** dựa trên `ThreadGroup` tiêu chuẩn. Một lớp con của `ThreadGroup` có thể ghi đè `uncaughtException()` và tự quyết định có gọi `super.uncaughtException(...)` hay không. Vì thế, handler mặc định **không chắc chắn sẽ được gọi** nếu group tùy biến đã xử lý và không chuyển tiếp.

## 4. Handler riêng của Thread

Đăng ký một handler cho một luồng cụ thể:

~~~java
Thread worker = new Thread(() -> {
    throw new IllegalStateException("Lỗi xử lý");
}, "import-worker");

worker.setUncaughtExceptionHandler((thread, error) -> {
    System.err.println("Luồng lỗi: " + thread.getName());
    System.err.println("Kiểu lỗi: " + error.getClass().getName());
});

worker.start();
~~~

Đây là **đoạn minh họa độc lập trong một phương thức**, có thể đặt trong `main` của một lớp Java. Handler riêng có hai tham số:

- `thread`: luồng mà lỗi không được bắt.
- `error`: chính đối tượng `Throwable` đã thoát ra, có thể chứa cause chain, stack trace và suppressed exceptions.

Khi handler riêng đã được gán, nó được ưu tiên hơn logic `ThreadGroup.uncaughtException()` của cùng luồng. Nếu chỉ cần xử lý lỗi cho một công việc độc lập, đây là lựa chọn dễ kiểm soát nhất.

**Đăng ký trước `start()`.** Nếu đợi tới sau khi khởi động, công việc có thể ném lỗi trước khi handler được gán. Đây là race condition. Trong các hệ thống có `ThreadFactory`, nên gán handler ngay lúc tạo đối tượng luồng, trước khi trả luồng cho executor.

### setUncaughtExceptionHandler(null) nghĩa là gì?

Gọi `thread.setUncaughtExceptionHandler(null)` xóa handler đã gán riêng cho luồng. Khi có lỗi chưa bắt về sau, luồng quay về dùng handler dự phòng của nó, thông thường là `ThreadGroup`. Lời gọi này **không** thiết lập một “handler rỗng” có chức năng nuốt lỗi.

Không nên đặt `null` chỉ để tắt ghi nhận lỗi. Nếu cần thay hành vi báo cáo, hãy chọn handler thích hợp và xem tác động tới giám sát của ứng dụng.

## 5. ThreadGroup vừa gom luồng, vừa tham gia xử lý ngoại lệ

`ThreadGroup` cho phép đặt luồng vào một nhóm. Ngoài các chức năng quản lý nhóm mang tính lịch sử, nó triển khai `Thread.UncaughtExceptionHandler` và đóng vai trò handler dự phòng của các luồng không có handler riêng.

Một nhóm có thể có nhóm cha. Triển khai `ThreadGroup.uncaughtException()` chuẩn xử lý đại ý như sau:

1. Nếu có nhóm cha, gọi `parent.uncaughtException(thread, error)`.
2. Nếu đã tới nhóm gốc, lấy `Thread.getDefaultUncaughtExceptionHandler()`.
3. Nếu default handler không phải `null`, gọi handler đó.
4. Nếu không có default handler, in thông tin ngoại lệ cùng stack trace ra `System.err`, ngoại trừ cách xử lý đặc biệt với `ThreadDeath` của JDK.

Vì vậy, khi dùng `ThreadGroup` tiêu chuẩn không override, ngoại lệ có thể đi qua nhiều nhóm cha trước khi tới handler mặc định. Cơ chế này không tạo ra exception mới; nó chuyển đối tượng lỗi được nhận lên phương thức xử lý tiếp theo.

### Ghi đè uncaughtException trong nhóm

~~~java
class LoggingGroup extends ThreadGroup {
    LoggingGroup(String name) {
        super(name);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        System.err.println("Nhóm bắt gặp lỗi ở " + thread.getName());
        error.printStackTrace(System.err);
    }
}
~~~

Đây là **định nghĩa lớp minh họa**, đặt trong tệp/phạm vi phù hợp. Vì phương thức override không gọi `super.uncaughtException(...)`, luồng thuộc nhóm này và không có handler riêng sẽ được báo cáo tại đây; lỗi **không tự động tiếp tục** tới nhóm cha hoặc default handler.

Nếu muốn bổ sung log của nhóm nhưng vẫn chuyển lên cấp mặc định, có thể gọi `super.uncaughtException(thread, error)`. Khi ấy có khả năng một sự cố bị ghi log hai lần, vì cả nhóm và handler bên trên đều báo cáo. Quy định rõ ai chịu trách nhiệm ghi log cuối cùng để tránh trùng lặp.

**Lưu ý thiết kế:** `ThreadGroup` có nhiều API quản lý luồng không còn được ưu tiên trong code hiện đại. Nếu chỉ cần gắn handler nhất quán cho những luồng do mình tạo, `ThreadFactory` hoặc gắn handler trực tiếp thường dễ bảo trì hơn. Tuy nhiên, cần hiểu cơ chế nhóm vì nó nằm trong đường dự phòng của xử lý ngoại lệ chưa bắt.

## 6. Handler mặc định của JVM

Đăng ký handler toàn cục:

~~~java
Thread.UncaughtExceptionHandler previous =
        Thread.getDefaultUncaughtExceptionHandler();

Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
    System.err.println("Unhandled error in " + thread.getName());
    error.printStackTrace(System.err);
});
~~~

Đây là **đoạn minh họa**, đặt trong ứng dụng có chiến lược quản lý vòng đời handler phù hợp. `setDefaultUncaughtExceptionHandler` là phương thức `static` trên `Thread`.

Handler mặc định hữu ích khi muốn có một điểm báo cáo dự phòng chung. Nó không ghi đè handler riêng đã gán cho từng luồng và không cưỡng ép một `ThreadGroup` tùy biến phải chuyển lỗi lên mặc định.

Một số nguyên tắc khi đăng ký:

- Cấu hình sớm tại điểm khởi tạo ứng dụng để tránh bỏ sót lỗi từ luồng khởi động trước.
- Đăng ký một cách có chủ đích, vì mọi phần của cùng JVM có thể chịu ảnh hưởng của default handler.
- Khi viết ví dụ hoặc kiểm thử, có thể lưu giá trị cũ bằng `getDefaultUncaughtExceptionHandler()` và khôi phục nó sau khi các luồng đã kết thúc.
- Khi nhiều thư viện cùng sửa default handler, giá trị thiết lập sau có thể thay thế giá trị trước. Không dùng cơ chế này như kênh trao đổi trạng thái giữa các thành phần độc lập.
- Handler mặc định không thay thế cơ chế giám sát lỗi của framework, `Future` hoặc ứng dụng web nơi ngoại lệ thường được bắt và chuyển thành kết quả theo cách riêng.

Nếu không có default handler và đường chuẩn của `ThreadGroup` tới điểm dự phòng cuối cùng, JDK thường in dòng dạng `Exception in thread "..."` và stack trace vào `System.err`. Chuỗi cụ thể có thể phụ thuộc runtime; không nên coi đây là API log có định dạng ổn định. Trường hợp `ThreadDeath` có xử lý đặc biệt trong `ThreadGroup` tiêu chuẩn; không dùng `Thread.stop()` để tạo tình huống thử nghiệm vì API này không an toàn và đã bị loại khỏi thực hành hiện đại.

## 7. Ví dụ Java 17+ độc lập: kiểm tra ba mức ưu tiên

Bài chạy độc lập dưới đây tạo ba luồng lần lượt để stdout có thứ tự xác định:

1. Một luồng vừa thuộc nhóm tùy biến vừa có handler riêng: **handler riêng được gọi**.
2. Một luồng thuộc nhóm tùy biến nhưng không có handler riêng: **handler của nhóm được gọi**.
3. Một luồng thuộc nhóm tiêu chuẩn và không có handler riêng: lỗi được chuyển lên **handler mặc định**.

Lưu toàn bộ chương trình vào tệp **HandlerDispatchDemo.java**:

~~~java
public class HandlerDispatchDemo {
    static final class ObservingGroup extends ThreadGroup {
        ObservingGroup(String name) {
            super(name);
        }

        @Override
        public void uncaughtException(Thread thread, Throwable error) {
            System.out.println("GROUP: " + thread.getName()
                    + " -> " + error.getMessage());
        }
    }

    static Thread failingThread(ThreadGroup group, String name, String message) {
        return new Thread(group, () -> {
            throw new IllegalStateException(message);
        }, name);
    }

    public static void main(String[] args) throws InterruptedException {
        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                    System.out.println("DEFAULT: " + thread.getName()
                            + " -> " + error.getMessage()));

            ThreadGroup specialGroup = new ObservingGroup("special");
            Thread local = failingThread(
                    specialGroup, "worker-local", "local-failure");
            local.setUncaughtExceptionHandler((thread, error) ->
                    System.out.println("THREAD: " + thread.getName()
                            + " -> " + error.getMessage()));
            local.start();
            local.join();

            Thread grouped = failingThread(
                    specialGroup, "worker-group", "group-failure");
            grouped.start();
            grouped.join();

            ThreadGroup regularGroup = new ThreadGroup("regular");
            Thread globally = failingThread(
                    regularGroup, "worker-default", "default-failure");
            globally.start();
            globally.join();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 HandlerDispatchDemo.java
java HandlerDispatchDemo
~~~

**Stdout chính xác:**

~~~text
THREAD: worker-local -> local-failure
GROUP: worker-group -> group-failure
DEFAULT: worker-default -> default-failure
~~~

Chương trình đã được thiết kế để không phải dựa vào stack trace chứa tên package hay số dòng bất định. Mỗi handler in cùng một mẫu đơn giản; từng luồng được `join()` trước khi tạo luồng tiếp theo.

### Vì sao từng dòng xuất hiện?

**Dòng 1 — THREAD:** `local` thuộc `ObservingGroup` nhưng đã có handler riêng. Khi lỗi thoát khỏi `run()`, Java gọi handler riêng, nên không gọi `ObservingGroup.uncaughtException()` và cũng không gọi default handler cho lỗi này.

**Dòng 2 — GROUP:** `grouped` không có handler riêng. Handler của nó là nhóm `ObservingGroup`. Phương thức override in dòng `GROUP` rồi trả về, không gọi `super`, nên chuỗi chuyển tiếp kết thúc ngay tại nhóm.

**Dòng 3 — DEFAULT:** `globally` thuộc `ThreadGroup` tiêu chuẩn. Nhóm này dùng logic `uncaughtException` mặc định, chuyển tới các nhóm cha và cuối cùng gọi handler mặc định toàn JVM. Vì vậy hiện dòng `DEFAULT` và không có dòng in stack trace dự phòng.

**Vì sao cần `try/finally` ở main?** Đoạn `finally` khôi phục handler mặc định trước đó để ví dụ không vô tình thay đổi hành vi của những phần code chạy sau. Vì từng luồng đều được `join()`, ba lỗi đã có cơ hội hoàn tất xử lý trước khi khôi phục.

**Vì sao ví dụ không bắt ngoại lệ ở main?** Lỗi được ném từ những luồng khác. `main` chỉ tạo, khởi động và chờ luồng kết thúc. `join()` không ném lại `IllegalStateException` của luồng worker.

## 8. Tự kiểm tra thứ tự ưu tiên

Từ ví dụ trên, có thể thử thay đổi một chi tiết tại một thời điểm:

| Thay đổi | Hành vi dự kiến |
| --- | --- |
| Xóa lời gọi `local.setUncaughtExceptionHandler(...)` | Lỗi của `worker-local` đi tới `ObservingGroup` |
| Giữ handler riêng và thay đổi nhóm | Handler riêng vẫn là cấp ưu tiên cho luồng đó |
| Trong `ObservingGroup.uncaughtException`, gọi `super.uncaughtException(thread, error)` sau khi in | Hệ thống có thể in thêm dòng default do lỗi được chuyển tiếp lên trên |
| Gọi `Thread.setDefaultUncaughtExceptionHandler(null)` và dùng nhóm chuẩn | Nếu không có tầng xử lý riêng, đường cuối cùng của nhóm chuẩn in lỗi vào `System.err` |
| Dùng `thread.setUncaughtExceptionHandler(null)` trước khi start | Luồng không còn handler riêng; nhóm là cấp dự phòng |

Lưu ý: Thay đổi default handler trong một JVM đang chạy nhiều công việc có thể ảnh hưởng các luồng không liên quan. Hãy thử các thay đổi này trong một chương trình hoặc môi trường kiểm thử riêng, không cập nhật cấu hình toàn cục của hệ thống đang phục vụ người dùng.

## 9. Handler không thay thế catch, finally hoặc phục hồi lỗi

Xét công việc có tài nguyên:

~~~java
Thread worker = new Thread(() -> {
    openResource();
    try {
        doWork();
    } finally {
        closeResource();
    }
});
~~~

Đây là **mã minh họa không độc lập**, giả định các phương thức cần thiết tồn tại và chữ ký checked exception cho phép cách gọi đó. Nếu `doWork()` ném một ngoại lệ unchecked chưa được bắt, `finally` vẫn chạy trên đường thoát thông thường, rồi ngoại lệ còn lại mới đi tới uncaught handler.

Nhưng handler:

- Không tự đóng file/socket/lock bị quên giải phóng.
- Không tự rollback trạng thái dữ liệu được cập nhật dở dang.
- Không thể tiếp tục thực thi luồng tại vị trí lỗi.
- Không thay thế các khối `catch` dành cho lỗi dự kiến mà ứng dụng có thể phục hồi.

Đối với tài nguyên `AutoCloseable`, nên ưu tiên `try-with-resources`. Nếu thao tác chính và việc đóng tài nguyên cùng ném lỗi, Java có thể giữ lỗi đóng trong `getSuppressed()` của ngoại lệ chính; handler có thể ghi nhận toàn bộ đối tượng `Throwable` để chẩn đoán. Nếu `finally` tự ném một exception khác, lỗi mới có thể thay thế lỗi đang lan truyền trước đó. Đây là lý do giải phóng tài nguyên cần được thiết kế đúng ngay bên trong công việc.

### Vì sao không bắt mọi lỗi chỉ để coi là thành công?

~~~java
Thread worker = new Thread(() -> {
    try {
        performWork();
    } catch (Exception ignored) {
        // Không báo cáo, không sửa trạng thái, tiếp tục như đã thành công.
    }
});
~~~

Đây là ví dụ **cố ý không nên áp dụng**, với `performWork()` là phương thức minh họa. Lỗi đã bị bắt nên uncaught handler sẽ không biết đến. Nếu hợp đồng công việc thực sự cho phép phục hồi, code ở đây phải xử lý và công bố kết quả chính xác; nếu không, nên cho lỗi lan truyền hoặc chuyển nó thành kết quả thất bại có cấu trúc.

## 10. ExecutorService.submit() có điểm khác quan trọng

Một hiểu nhầm phổ biến: “Đã gán `UncaughtExceptionHandler` cho thread của executor thì mọi exception trong task đều đi tới handler.”

Điều này **không đúng với đường thực thi thông thường của `ExecutorService.submit()`**. `submit()` trả về `Future`. Task được bao bọc bởi cơ chế ghi nhận kết quả; khi task ném exception, `Future` lưu trạng thái thất bại. Lời gọi `Future.get()` sau đó báo `ExecutionException` với cause chứa nguyên nhân task. Exception đã được cơ chế task bắt/ghi nhận, vì thế không thoát ra khỏi `Thread.run()` dưới dạng uncaught exception.

### Ví dụ Java 17+ độc lập với Future

Tạo tệp **FutureFailureDemo.java**:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FutureFailureDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger handlerCalls = new AtomicInteger();

        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "pool-worker");
            thread.setUncaughtExceptionHandler(
                    (t, error) -> handlerCalls.incrementAndGet());
            return thread;
        };

        ExecutorService executor = Executors.newSingleThreadExecutor(factory);
        try {
            Future<?> future = executor.submit(() -> {
                throw new IllegalArgumentException("bad task");
            });

            try {
                future.get();
            } catch (ExecutionException e) {
                System.out.println("FUTURE: "
                        + e.getCause().getClass().getSimpleName()
                        + " -> " + e.getCause().getMessage());
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Executor không kết thúc");
            }
        }

        System.out.println("HANDLER CALLS: " + handlerCalls.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 FutureFailureDemo.java
java FutureFailureDemo
~~~

**Stdout chính xác:**

~~~text
FUTURE: IllegalArgumentException -> bad task
HANDLER CALLS: 0
~~~

Giải thích:

1. `ThreadFactory` tạo thread cho executor và gán handler riêng. Handler chỉ tăng bộ đếm.
2. `submit()` trả về một `Future` cho task ném `IllegalArgumentException`.
3. `future.get()` quan sát thất bại qua `ExecutionException`; `getCause()` là exception gốc của task.
4. Vì task thất bại đã được lưu trong `Future`, handler của thread **không được gọi cho lỗi đó**; bộ đếm là 0.
5. `shutdown()` và `awaitTermination()` bảo đảm tài nguyên executor được quản lý gọn. Chương trình đã xử lý `ExecutionException` của task này để in một kết quả xác định.

### Còn execute() thì sao?

Với `ThreadPoolExecutor.execute(Runnable)`, một `RuntimeException` hoặc `Error` thoát khỏi `Runnable.run()` có thể khiến worker thread kết thúc bất thường; đường xử lý uncaught exception của thread đó có thể được kích hoạt. Tuy nhiên, cách một executor cụ thể xử lý task còn phụ thuộc vào implementation, wrapper và chính loại task đã được giao.

Vì vậy, **không thiết kế hệ thống chỉ dựa vào handler để thu thập mọi lỗi task**. Với `Future`, quan sát kết quả bằng `get()`; với các cơ chế async khác như `CompletableFuture`, dùng các API completion và xử lý exceptional completion phù hợp. Nếu cần ghi nhận thống nhất ở executor, có thể áp dụng chính sách xử lý ở cấp task hoặc tại điểm giám sát được framework cung cấp.

## 11. ThreadFactory: gắn handler cho các thread mới

Khi tự quản lý nhóm worker hoặc dùng `ExecutorService`, `ThreadFactory` giúp áp dụng nhất quán tên luồng và handler:

~~~java
java.util.concurrent.ThreadFactory factory = runnable -> {
    Thread thread = new Thread(runnable, "document-worker");
    thread.setUncaughtExceptionHandler((t, error) -> {
        System.err.println("Worker thất bại: " + t.getName());
        error.printStackTrace(System.err);
    });
    return thread;
};
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. Điểm quan trọng là factory cấu hình thread **trước khi executor hoặc thành phần khác khởi động nó**.

Nếu tạo nhiều thread, nên có chính sách đặt tên phân biệt chúng thay vì tên trùng lặp để dễ đọc log. Handler cần đủ nhẹ, đáng tin cậy và không thực hiện thao tác blocking dài. Việc một thread lỗi không đồng nghĩa toàn executor hoặc cả JVM đã ngừng hoạt động; các cơ chế thay thế worker, trạng thái task và policy shutdown phụ thuộc executor.

## 12. Những ngoại lệ nào có thể đi tới handler?

Phương thức `uncaughtException(Thread, Throwable)` nhận `Throwable`, không chỉ `Exception`. Bởi vậy handler có thể nhìn thấy `RuntimeException`, những unchecked exception khác và cả `Error` đã thoát khỏi luồng.

Không nên diễn giải rằng handler có thể **phục hồi được mọi `Error`**. Các lỗi nghiêm trọng như lỗi tài nguyên JVM có thể khiến cả thao tác ghi log cũng không còn đáng tin cậy. Handler là điểm quan sát tốt nhất có thể, không phải bộ máy tự sửa chữa mọi sự cố.

Các checked exception như `IOException` thường bị buộc bắt hoặc khai báo tại nơi gọi theo quy tắc Java. `Runnable.run()` không khai báo checked exception nên code trực tiếp trong lambda `Runnable` phải xử lý hoặc chuyển đổi chúng trước khi có thể thoát dưới dạng ngoại lệ không được bắt.

Ví dụ minh họa:

~~~java
Runnable task = () -> {
    try {
        readFromFile();
    } catch (java.io.IOException original) {
        throw new IllegalStateException("Không thể đọc dữ liệu", original);
    }
};
~~~

Đoạn này giả định có phương thức `readFromFile()` khai báo `throws IOException`. Nếu `IllegalStateException` thoát khỏi task thực thi trực tiếp trong thread, handler sẽ nhận wrapper đó. `getCause()` của wrapper vẫn giữ `IOException` gốc để chẩn đoán.

Ngoại lệ mà code ứng dụng **đã bắt và xử lý** sẽ không được xem là uncaught; handler không biết về nó nếu code không chủ động báo cáo.

## 13. Handler nên làm những gì?

Một handler tốt thường giới hạn nhiệm vụ ở việc **ghi nhận sự cố đủ chính xác** và cung cấp tín hiệu cho tầng quản lý. Có thể:

- Ghi tên/ID luồng, thời gian và tên tác vụ nếu có.
- Ghi exception object để lưu đầy đủ type, cause, stack trace và suppressed exceptions.
- Tăng một metric hoặc phát sự kiện nội bộ với giới hạn tài nguyên phù hợp.
- Thông báo một bộ quản lý worker rằng một luồng đã kết thúc bất thường.
- Đặt cơ chế báo lỗi thống nhất, ví dụ gửi tới logger của ứng dụng.

Các giới hạn quan trọng:

1. **Không coi handler là chỗ chạy lại task**: không có đủ dữ liệu chắc chắn về tính idempotent, tác dụng phụ đã xảy ra hoặc trạng thái sau lỗi.
2. **Không thực hiện xử lý phức tạp dễ thất bại**: handler có thể được gọi khi hệ thống đang ở trạng thái suy giảm.
3. **Không làm mất thông tin gốc**: khi chuyển lỗi thành sự kiện, cố gắng giữ cause để nội bộ điều tra.
4. **Không tiết lộ thông tin nhạy cảm**: stack trace có thể chứa đường dẫn, tên tài nguyên, message kỹ thuật hoặc thông tin ứng dụng; lưu/hiển thị theo mức quyền thích hợp.
5. **Tránh ghi log trùng**: nếu handler của nhóm và handler mặc định đều in cùng exception, vận hành dễ đếm một sự cố thành hai.
6. **Không dựa vào console cho xử lý nghiệp vụ**: `System.err` không phải hợp đồng giao tiếp ổn định của hệ thống phân tán.

### Nếu handler tự ném ngoại lệ?

Theo hợp đồng của `Thread.UncaughtExceptionHandler`, ngoại lệ do chính phương thức `uncaughtException` ném ra sẽ bị JVM bỏ qua. Không thể kỳ vọng một handler lỗi sẽ kích hoạt vòng báo lỗi thứ hai hay quay lại thực hiện công việc ban đầu.

Vì vậy nên viết handler ngắn, tránh dùng trạng thái chung dễ lỗi và kiểm thử chính handler với trường hợp thông tin đầu vào bất thường. Tùy yêu cầu, có thể bao quanh code báo cáo với cơ chế bảo vệ phù hợp, nhưng không nên tạo vòng gọi handler đệ quy khi bản thân hệ thống ghi log đang lỗi.

## 14. Sự khác nhau giữa xử lý lỗi trong task và xử lý ở handler

| Câu hỏi | Bắt lỗi trong task | Uncaught handler |
| --- | --- | --- |
| Có thể phục hồi rồi tiếp tục công việc? | Có, nếu logic task biết cách phục hồi | Không thể quay lại vị trí đã thoát |
| Có thể chuyển lỗi thành kết quả nghiệp vụ? | Có, theo hợp đồng task | Thường chỉ quan sát/report; cần kênh kết quả khác |
| Có thể giải phóng tài nguyên cục bộ? | Có, qua `finally` hoặc try-with-resources | Quá muộn để dựa vào handler làm cleanup thay cho task |
| Có đủ ngữ cảnh nghiệp vụ? | Thường có | Chỉ được truyền `Thread` và `Throwable`; thông tin khác phải được thiết kế thêm |
| Có nhận lỗi của `submit()` không? | Future/task wrapper lưu exception; caller xử lý qua `get()` | Thường không thấy lỗi task vì đã được wrapper bắt |

Không có một cấp duy nhất phù hợp mọi lỗi. Lỗi dự kiến và có thể phục hồi nên xử lý trong task hoặc trong hợp đồng async tương ứng. Uncaught handler là điểm dự phòng giúp quan sát những lỗi **đã thoát ra ngoài** đường xử lý thông thường.

## 15. Kiểm thử handler và thứ tự xử lý

Bộ kiểm thử nên kiểm tra hành vi **theo cơ chế**, không phụ thuộc vào thời gian chạy hay stack trace của JVM.

| Kịch bản | Kỳ vọng |
| --- | --- |
| Thread có handler riêng và nằm trong nhóm tùy biến | Chỉ handler riêng nhận ngoại lệ, nếu chính handler không chuyển tiếp |
| Thread không có handler riêng, nhóm override `uncaughtException` | Handler của nhóm được gọi |
| Thread dùng nhóm chuẩn, có default handler | Cuối chuỗi nhóm cha, default handler được gọi |
| Nhóm override nhưng không gọi `super` | Default handler không tự động nhận lỗi |
| Nhóm override có gọi `super` | Đường xử lý có thể tới nhóm cha/default |
| Task đã bắt và xử lý exception | Không kích hoạt uncaught handler cho exception đó |
| Task chạy qua `ExecutorService.submit()` | Lỗi lấy qua `Future.get()`, handler của thread không tự động nhận nó |

Trong kiểm thử, có thể dùng `AtomicReference<Throwable>` hoặc một công cụ đồng bộ để ghi nhận exception đã gửi cho handler. Khởi động luồng, gọi `join()`, rồi xác nhận loại exception và **cùng đối tượng** nếu cần.

**Không viết kiểm thử rủi ro treo vô thời hạn** khi task có thể chờ khóa hay chờ I/O. Có thể đặt thời hạn kiểm thử thích hợp và xử lý trạng thái timeout. Không nên khẳng định handler “không bao giờ được gọi” chỉ vì một lần đọc biến đếm quá sớm khi luồng chưa kết thúc.

Với handler mặc định, lưu và khôi phục handler cũ theo vòng đời kiểm thử. Nếu nhiều test chạy song song trong cùng một JVM và đồng thời chỉnh handler mặc định, các test có thể can thiệp nhau; cần cô lập hoặc phối hợp trạng thái toàn cục này.

## 16. Lỗi thường gặp

1. **Bắt ngoại lệ quanh `thread.start()` rồi mong bắt được lỗi ở `run()`:** hai lời gọi chạy trên hai luồng khác nhau.
2. **Gắn handler sau `start()`:** luồng có thể đã ném lỗi trước khi handler được cài.
3. **Tưởng handler mặc định luôn được gọi:** handler riêng hoặc `ThreadGroup` override có thể kết thúc đường báo lỗi trước đó.
4. **Tưởng nhóm chỉ là nhãn phân loại:** `ThreadGroup` cũng là `UncaughtExceptionHandler` dự phòng.
5. **Override group rồi quên quyết định có gọi `super`:** hoặc bỏ mất default logging, hoặc vô tình ghi log trùng.
6. **Cho rằng handler giúp task chạy tiếp:** luồng đã thoát khỏi đường thực thi bị lỗi.
7. **Trông đợi `join()` ném lại ngoại lệ của worker:** `join()` chỉ chờ kết thúc và có thể bị gián đoạn ở luồng đang chờ.
8. **Dùng handler để tìm mọi lỗi từ `submit()`:** exception của task có thể nằm trong `Future`.
9. **Chỉ ghi `error.getMessage()`:** mất type, cause chain và stack trace.
10. **Ghi log toàn bộ thông tin lỗi ra đầu ra công khai:** có nguy cơ lộ chi tiết hạ tầng.
11. **Làm tác vụ nặng trong handler:** dễ phát sinh lỗi mới khi hệ thống đang bất ổn.
12. **Quên khôi phục default handler sau test:** thay đổi hành vi của các test hoặc thành phần chạy sau.
13. **Coi mọi lỗi là khôi phục được:** `Error` và lỗi hệ thống nghiêm trọng có thể không cho phép phục hồi.
14. **Ghi log cùng một lỗi ở nhiều cấp:** gây nhiễu việc đếm và phân tích sự cố.

## 17. Checklist thiết kế

Trước khi vận hành một chương trình dùng nhiều luồng, hãy tự kiểm tra:

- Đã phân biệt **lỗi dự kiến trong task** với **ngoại lệ chưa được bắt thoát khỏi luồng** chưa?
- Những luồng cần xử lý riêng đã được gán handler **trước `start()`** chưa?
- ThreadGroup tùy biến có quy tắc chuyển tiếp lỗi rõ ràng không, và có tránh log trùng không?
- Handler mặc định đã được cấu hình ở điểm khởi tạo thích hợp chưa?
- Nếu code sử dụng `ExecutorService.submit()`, ai chịu trách nhiệm `Future.get()` hoặc xử lý kết quả thất bại?
- Khi lỗi phát sinh, cleanup cục bộ đã được đảm bảo bởi `finally` hoặc `try-with-resources` chưa?
- Handler có đủ ngữ cảnh quan sát mà không tiết lộ thông tin nhạy cảm không?
- Kiểm thử có xác nhận thứ tự ưu tiên, danh tính throwable và đường báo lỗi thực tế không?
- Chính sách khôi phục, thử lại hoặc chấm dứt ứng dụng có được quyết định ở tầng đủ ngữ cảnh hay không?

**Điều cần nhớ:** handler riêng ưu tiên cho từng `Thread`; nếu không có, `ThreadGroup` nhận lỗi và có thể chuyển lên nhóm cha; logic chuẩn ở nhóm gốc mới sử dụng handler mặc định, trước khi rơi về cách in lỗi dự phòng. Uncaught handler giúp **quan sát lỗi cuối đường chạy của luồng**, còn xử lý kết quả, cleanup và phục hồi phải được thiết kế ở đúng tầng có đủ ngữ cảnh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-005">Khác biệt giữa Executor.execute và submit khi tác vụ thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt giữa `Executor.execute()` và `ExecutorService.submit()` khi tác vụ thất bại

Trong lập trình đa luồng Java, một tác vụ có thể được gửi vào bộ thực thi (executor) nhưng lại ném ngoại lệ khi chạy. Cả `execute()` và `submit()` đều có thể dùng để giao một `Runnable` cho executor; điểm khác biệt quan trọng là **đường đi của lỗi và cách chương trình phát hiện lỗi**. Nếu chọn cách gửi tác vụ mà không thiết kế cách nhận lỗi, ứng dụng có thể tiếp tục hoạt động trong khi một công việc quan trọng đã thất bại.

Bài này xét lỗi **xảy ra bên trong tác vụ**. Lỗi khi executor **từ chối nhận tác vụ** được phân biệt riêng ở phần dưới.

## 1. Hai phương thức thuộc những giao diện nào?

- `Executor.execute(Runnable command)` được khai báo trong `java.util.concurrent.Executor`. Phương thức trả về `void`: người gọi không nhận một đối tượng kết quả để chờ hoặc kiểm tra lỗi của tác vụ.
- `ExecutorService.submit(...)` được khai báo trong `java.util.concurrent.ExecutorService` (giao diện mở rộng `Executor`). Các overload thường dùng là `submit(Runnable)`, `submit(Callable<T>)` và `submit(Runnable, T result)`. Phương thức trả về `Future`, là đầu mối để theo dõi kết thúc, nhận kết quả hoặc nhận lỗi.

| Khía cạnh | `execute(Runnable)` | `submit(Runnable/Callable)` |
| --- | --- | --- |
| Giao diện sở hữu | `Executor` | `ExecutorService` |
| Giá trị trả về | `void` | `Future<?>` hoặc `Future<T>` |
| Khi công việc hoàn tất bình thường | Không có kết quả trả về trực tiếp | `Future.get()` trả `null` với `Runnable` thông thường, hoặc giá trị của `Callable<T>` |
| Khi thân tác vụ ném ngoại lệ và không tự bắt | Với `ThreadPoolExecutor` thực thi một `Runnable` trực tiếp, lỗi thường thoát khỏi `run()` của worker, có thể tới `UncaughtExceptionHandler` | Với cơ chế `FutureTask` thường dùng, lỗi được giữ trong `Future`; `get()` ném `ExecutionException` chứa lỗi gốc |
| Người gọi nhận ra thất bại bằng cách nào? | Thiết kế kênh báo lỗi bên trong tác vụ, handler/hook giám sát phù hợp, hoặc cơ chế riêng | Giữ lại `Future` và gọi `get()` (hoặc có cơ chế thu thập/giám sát các `Future`) |
| Có thể kiểm tra đã kết thúc qua đối tượng trả về? | Không | Có, bằng `isDone()`; nhưng `isDone() == true` **không chứng minh thành công** |
| Có thể hủy qua đối tượng trả về? | Không có `Future` do phương thức trả lại | Có, `Future.cancel(...)`, với những giới hạn của cơ chế hủy |

**Điểm mấu chốt:** `submit()` không khiến tác vụ ít lỗi hơn. Nó thay đổi cách biểu diễn và quan sát kết quả của tác vụ. `execute()` cũng không bảo đảm luôn in stack trace: điều này phụ thuộc executor, wrapper và cơ chế xử lý lỗi.

## 2. Đường đi của ngoại lệ

Xét một `Runnable` ném `IllegalStateException` và không tự bắt ngoại lệ.

**Trường hợp thường gặp: `ThreadPoolExecutor.execute(runnable)`**

1. Executor nhận `Runnable` và chạy `run()` bằng một worker.
2. `run()` ném `RuntimeException` hoặc `Error` ra ngoài.
3. Với `Runnable` trực tiếp trên `ThreadPoolExecutor`, worker kết thúc bất thường; lỗi có thể được chuyển tới `Thread.UncaughtExceptionHandler` của worker.
4. Executor có thể tạo worker thay thế để duy trì kích thước pool. Điều này **không có nghĩa toàn bộ thread pool bị hỏng**, cũng không tự động báo lỗi về luồng đã gọi `execute()`.

Nếu worker không có handler tùy chỉnh, bạn thường thấy stack trace do handler mặc định ghi ra luồng lỗi chuẩn. Không nên dựa vào việc “cứ `execute()` là console sẽ hiện lỗi”, bởi các triển khai `Executor` và cách bọc tác vụ có thể khác nhau.

**Trường hợp thường gặp: `ExecutorService.submit(runnable)`**

1. ExecutorService tạo một tác vụ có trạng thái/kết quả dạng `Future` (ví dụ `FutureTask`) rồi lên lịch chạy.
2. Khi thân tác vụ ném ngoại lệ, lớp bao kết quả bắt và lưu ngoại lệ.
3. `Future` chuyển sang trạng thái hoàn tất; một lỗi đã xảy ra vẫn làm `isDone()` trả `true`.
4. Khi người gọi `future.get()`, phương thức ném `ExecutionException`; `getCause()` cho biết ngoại lệ gốc đã khiến tác vụ thất bại.

Vì lỗi được giữ trong `Future`, `UncaughtExceptionHandler` của worker **thường không được gọi** cho lỗi của tác vụ gửi qua `submit()`. Nếu bỏ qua `Future` mà không có kênh giám sát khác, lỗi có thể không xuất hiện trong log.

Lưu ý phạm vi: `Executor` chỉ là hợp đồng thực thi lệnh. Có executor chạy tác vụ ngay trên luồng gọi, và có executor dùng worker riêng. Hành vi worker và stack trace ở trên mô tả `ThreadPoolExecutor` với tác vụ `Runnable` trực tiếp, không phải cam kết cho mọi executor.

## 3. Ví dụ Java 17+ có thể chạy: cùng một lỗi, hai cách quan sát

Tạo tệp **`ExecuteVsSubmitFailure.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ExecuteVsSubmitFailure {
    public static void main(String[] args) throws Exception {
        CountDownLatch handlerFinished = new CountDownLatch(1);

        ThreadFactory factory = task -> {
            Thread worker = new Thread(task, "demo-worker");
            worker.setUncaughtExceptionHandler((thread, failure) -> {
                System.out.printf("execute/handler: %s: %s%n",
                        failure.getClass().getSimpleName(),
                        failure.getMessage());
                handlerFinished.countDown();
            });
            return worker;
        };

        ExecutorService pool = Executors.newSingleThreadExecutor(factory);
        try {
            pool.execute(() -> {
                throw new IllegalStateException("execute failed");
            });

            if (!handlerFinished.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("UncaughtExceptionHandler did not run");
            }

            Future<?> submitted = pool.submit((Runnable) () -> {
                throw new IllegalArgumentException("submit failed");
            });

            try {
                submitted.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                System.out.printf("submit/get: %s: %s%n",
                        cause.getClass().getSimpleName(),
                        cause.getMessage());
            }

            System.out.println("submit/isDone: " + submitted.isDone());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ExecuteVsSubmitFailure.java
java ExecuteVsSubmitFailure
```

Kết quả chuẩn trên cơ chế `ThreadPoolExecutor` do `Executors.newSingleThreadExecutor(...)` tạo:

```text
execute/handler: IllegalStateException: execute failed
submit/get: IllegalArgumentException: submit failed
submit/isDone: true
```

Ví dụ dùng `CountDownLatch` để đợi handler in xong trước khi chuyển sang phần `submit()`, nhờ đó thứ tự ba dòng có thể kiểm tra. Executor một luồng có thể tạo worker mới sau lần lỗi ở `execute()`; phần `submit()` vẫn được chạy. Handler tùy chỉnh đã xử lý việc ghi lỗi của `execute()`, nên không cần trông chờ stack trace mặc định của JVM.

Giải thích từng kết quả:

- Dòng `execute/handler` là thông báo từ `UncaughtExceptionHandler` của luồng thực thi. Người gọi `execute()` không nhận `ExecutionException` hoặc `Future` nào từ lời gọi đó.
- Dòng `submit/get` chỉ xuất hiện khi luồng gọi đọc `submitted.get(...)` và bắt `ExecutionException`. Lỗi ban đầu là `IllegalArgumentException`, lấy bằng `getCause()`.
- Dòng `submit/isDone: true` cho biết tác vụ đã **kết thúc**, dù kết thúc vì ngoại lệ. Đừng dùng `isDone()` như phép kiểm tra thành công.

Lệnh `get(5, TimeUnit.SECONDS)` chỉ giới hạn thời gian chờ của luồng gọi; thời gian chờ hết sẽ ném `TimeoutException`, không tự chứng minh tác vụ đã dừng. Ví dụ có `shutdown()` và `awaitTermination()` để dọn executor khi kết thúc.

## 4. `Runnable`, `Callable` và ngoại lệ checked

`Runnable.run()` không khai báo `throws` cho checked exception. Vì thế một `Runnable` không thể ném trực tiếp checked exception ra khỏi `run()` mà không xử lý hoặc gói nó trong unchecked exception. Ngược lại, `Callable<V>.call()` khai báo `throws Exception`, nên có thể báo checked exception khi tính toán thất bại.

Ví dụ đặt bên trong một phương thức có biến `ExecutorService pool`:

```java
Future<String> result = pool.submit(() -> {
    throw new java.io.IOException("cannot read data");
});

try {
    String value = result.get();
    System.out.println(value);
} catch (ExecutionException exception) {
    Throwable cause = exception.getCause();
    // cause là IOException("cannot read data") của tác vụ.
} catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    // Luồng đang chờ bị ngắt; cần quyết định cách kết thúc/chuyển lỗi.
}
```

Đây là **đoạn mã minh họa bên trong phương thức**, không phải một lớp Java độc lập; cần một `pool` đang hoạt động. `IOException` xảy ra trong worker được lưu làm nguyên nhân của `ExecutionException`. Kiểu trả về `Future<String>` đến từ `Callable<String>`, không phải từ `execute()`.

Với `submit(Runnable)`, nếu tác vụ thành công thì `get()` trả `null`; với `submit(Runnable, fixedResult)`, `get()` trả `fixedResult` khi thành công. Khi tác vụ thất bại, các biến thể đều có thể báo lỗi qua `ExecutionException`.

## 5. Ba loại thất bại cần phân biệt

### 5.1. Tác vụ ném ngoại lệ khi đang chạy

Ví dụ `NullPointerException`, `IllegalArgumentException`, `IllegalStateException` hoặc checked exception từ `Callable`. Với `submit()` và cơ chế `Future` thông thường, `get()` là nơi người đợi nhận `ExecutionException`. Với `execute()` trên worker của `ThreadPoolExecutor` và `Runnable` trực tiếp, lỗi chưa bắt có thể đi tới handler của worker.

`Future.get()` còn có các đường đi **khác** `ExecutionException`: `InterruptedException` khi chính luồng đang chờ bị ngắt; `CancellationException` nếu tác vụ bị hủy; và `TimeoutException` với overload có thời hạn nếu chưa có kết quả kịp lúc. Đừng gộp bốn tình huống thành “tác vụ đã ném exception”.

### 5.2. Executor từ chối nhận tác vụ

Khi executor đã `shutdown()` hoặc hàng đợi/pool quá tải theo chính sách từ chối, `execute()` có thể ném `RejectedExecutionException` **ngay tại thời điểm gửi**. `submit()` cũng có thể ném lỗi này ngay: nhiều triển khai `ExecutorService` bọc công việc thành `FutureTask` rồi gọi cơ chế `execute()` để gửi. Khi bị từ chối, có thể **không có `Future` nào được trả về**.

Ví dụ về cách nhận biết đúng vị trí:

```java
try {
    pool.submit(() -> System.out.println("running"));
} catch (java.util.concurrent.RejectedExecutionException rejected) {
    System.err.println("Executor refused the task: " + rejected);
}
```

Đây là đoạn đặt trong phạm vi đã có `ExecutorService pool`. Việc bắt lỗi từ chối ở **lời gọi gửi** không thay cho kiểm tra kết quả sau này bằng `Future.get()`. Một số `RejectedExecutionHandler` có thể chạy tác vụ ngay ở luồng gọi, hoặc bỏ tác vụ theo chính sách riêng; không được suy ra một quy tắc duy nhất cho mọi chính sách.

### 5.3. Tác vụ tự bắt rồi bỏ qua lỗi

Nếu bên trong `Runnable` có `try/catch` bắt ngoại lệ nhưng chỉ ghi log và trả về bình thường, executor nhìn thấy một tác vụ **hoàn tất bình thường**. Khi ấy:

- `execute()` không còn ngoại lệ chưa bắt để chuyển đến handler;
- `submit()` có `Future.get()` hoàn tất bình thường (`null` đối với `Runnable`), dù công việc nghiệp vụ thực tế có thể chưa thành công.

Muốn tác vụ được ghi nhận là thất bại, cần thiết kế rõ: ném lại ngoại lệ sau khi bổ sung ngữ cảnh, trả kiểu kết quả biểu diễn thành công/thất bại, hoặc truyền sự kiện lỗi tới kênh giám sát. Đừng dùng khối `catch` rỗng để làm “mất” lỗi.

## 6. Cách sử dụng đúng trong ứng dụng

**Chọn `execute()`** khi bạn chỉ muốn gửi một lệnh không cần lấy kết quả qua `Future`, đồng thời đã có chiến lược ghi log/báo thất bại rõ ràng. Ví dụ hệ thống có một `Runnable` tự xử lý lỗi và gửi tín hiệu lỗi tới nơi theo dõi. `execute()` không mang sẵn cơ chế cho người gửi truy vấn kết quả.

**Chọn `submit()`** khi người gửi cần biết việc đã hoàn thành, lấy kết quả, chờ có giới hạn thời gian hoặc yêu cầu hủy. Nhưng phải **giữ và tiêu thụ `Future`** hoặc đưa nó vào cơ chế quan sát tập trung. Gọi `submit()` rồi bỏ qua giá trị trả về thường khiến lỗi của tác vụ khó phát hiện.

Với luồng chờ kết quả, dùng quy tắc xử lý rõ ràng:

1. `ExecutionException`: lấy `getCause()` và xử lý/báo lỗi nghiệp vụ hoặc lỗi kỹ thuật gốc.
2. `InterruptedException`: bảo toàn tín hiệu ngắt bằng `Thread.currentThread().interrupt()` nếu không chuyển tiếp ngoại lệ; sau đó dừng hoặc kết thúc quy trình chờ phù hợp.
3. `CancellationException`: tác vụ đã bị hủy; không coi đó là hoàn tất thành công.
4. `TimeoutException` (khi gọi `get(timeout, unit)`): quyết định có chờ thêm, thử lại hay gọi `cancel(true)`; yêu cầu hủy bằng interrupt không bảo đảm một tác vụ không hợp tác sẽ lập tức dừng.

Đối với nhiều tác vụ, một cách đơn giản là lưu các `Future` vào danh sách và kiểm tra từng kết quả. Trong hệ thống thực tế, có thể xây dựng một đầu mối thu thập kết quả hoặc hoàn tất để tránh bỏ sót lỗi; đừng chỉ dựa vào việc executor còn đang chạy.

**Giám sát ở mức executor:** `ThreadPoolExecutor.afterExecute(Runnable r, Throwable t)` hữu ích khi muốn theo dõi tác vụ, nhưng `t` thường là `null` đối với tác vụ `submit()` đã được bọc trong `FutureTask` vì wrapper đã giữ ngoại lệ. Nếu cài đặt hook, phải xử lý trường hợp `r` là `Future` và đọc kết quả thận trọng (gồm hủy và ngắt), tránh tự làm hỏng worker hoặc chờ sai thời điểm. `UncaughtExceptionHandler` cũng không thay thế được thao tác kiểm tra `Future.get()`.

## 7. Các hiểu lầm và lỗi phổ biến

| Nhận định | Điều chính xác hơn |
| --- | --- |
| “`submit()` nuốt mất exception.” | `Future` thường **giữ** ngoại lệ. Lỗi trở nên khó thấy khi không ai kiểm tra kết quả. |
| “`execute()` luôn ném lỗi về lệnh gọi `execute()`.” | Nếu tác vụ chạy trên worker khác, lỗi xảy ra ở worker; lời gọi gửi có thể đã trả về. Executor chạy trực tiếp trên luồng gọi là trường hợp khác. |
| “`isDone() == true` nghĩa là thành công.” | `isDone()` cũng đúng khi tác vụ thất bại hoặc bị hủy. Cần kiểm tra `get()` hoặc trạng thái phù hợp. |
| “Bọc mọi công việc bằng `submit()` thì lỗi sẽ tự được log.” | Thông thường `submit()` không tự in lỗi của tác vụ ra console. Cần lấy `Future` và có chính sách quan sát. |
| “Một task của pool ném ngoại lệ thì cả pool dừng.” | Với `ThreadPoolExecutor`, worker có thể kết thúc bất thường và pool có thể thay worker; những tác vụ khác không mặc nhiên bị hủy. |
| “Có `UncaughtExceptionHandler` là đủ cho cả hai.” | Wrapper của `submit()` thường đã bắt lỗi, nên handler của worker không nhận lỗi đó. |
| “Bắt `Exception` quanh lời gọi `submit()` là bắt được lỗi của task.” | `try/catch` lúc gửi bắt được lỗi ngay thời điểm gửi (như từ chối). Lỗi sinh ra **sau** khi chạy cần xem `Future`. |

Có một ngoại lệ kỹ thuật cần hiểu đúng: người dùng có thể tự tạo `FutureTask` rồi truyền nó qua `execute()`. Khi ấy chính `FutureTask` sẽ giữ lỗi, dù lời gọi bên ngoài là `execute()`. Vì vậy hành vi lỗi phụ thuộc cả **loại tác vụ đã gửi** và **cách executor thực thi**, không chỉ tên phương thức.

## 8. Tự kiểm tra

**Tình huống A.** Gửi `pool.submit(() -> { throw new IllegalStateException("bad"); });`, bỏ `Future` và tiếp tục xử lý. Liệu người gửi có chắc nhìn thấy lỗi? **Không.** Executor có thể đã giữ lỗi trong `Future` mà không ghi ra console; cần chủ động nhận/quan sát kết quả.

**Tình huống B.** `Future<?> f` đã có `f.isDone() == true` và `f.isCancelled() == false`. Liệu tác vụ chắc thành công? **Không.** Nó vẫn có thể thất bại; `f.get()` sẽ ném `ExecutionException` nếu thất bại.

**Tình huống C.** Sau khi gọi `shutdown()`, cả `execute()` và `submit()` đều có thể bị từ chối. Đây có phải `ExecutionException` sinh ra khi `Future.get()` không? **Không.** Đây là lỗi lúc **gửi công việc** (`RejectedExecutionException`), có thể xảy ra trước khi có `Future`.

Khi thiết kế xử lý lỗi, hãy trả lời hai câu hỏi trước khi chọn API: *Ai chịu trách nhiệm biết tác vụ thành công hay thất bại?* và *Kênh nào chuyển thông tin thất bại tới người đó?* `execute()` phù hợp với lệnh đã có cách giám sát riêng; `submit()` cung cấp một `Future` để người gửi chủ động nhận kết quả và lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-006">Future.get và ExecutionException</a>

<details>
<summary>Xem chi tiết</summary>

# `Future.get()` và `ExecutionException` trong Java

Trong lập trình đồng thời, một tác vụ chạy ở luồng khác có thể hoàn thành thành công, thất bại hoặc bị hủy. `Future<T>` đại diện cho kết quả sẽ có trong tương lai; `get()` là điểm mà luồng gọi **chờ kết quả** và **quan sát lỗi của tác vụ**. Bài này tập trung vào `Future.get()` và `ExecutionException` trong Java 17+, thuộc chủ đề **Ngoại lệ và luồng**.

## 1. Mô hình: lỗi xảy ra ở luồng nào, được nhận ở đâu?

Ví dụ, `ExecutorService.submit(Callable<T>)` gửi tác vụ cho executor. Lời gọi `submit()` thường trả về một `Future<T>` ngay, trong khi tác vụ có thể chưa bắt đầu, đang chạy hoặc đã kết thúc.

```text
Luồng gọi                              Luồng worker
-----------                            ------------
submit(callable) ────────►             chờ lịch chạy
      │                                 chạy call()
      ▼                                 │
Future<T>                               ├─ return T ─► hoàn thành thành công
      │                                 └─ throw X  ─► hoàn thành thất bại
      ▼
future.get()
      ├─ thành công ─► nhận T
      ├─ thất bại ──► ExecutionException (cause = X)
      ├─ đã hủy ────► CancellationException
      └─ bị ngắt lúc chờ ─► InterruptedException
```

Một ngoại lệ phát sinh trong `Callable.call()` **không tự động nhảy sang luồng đã gọi `submit()`**. Executor ghi lại trạng thái thất bại của tác vụ; khi gọi `get()`, bên nhận thấy `ExecutionException` chứa ngoại lệ gốc trong `getCause()`.

Điểm phân biệt quan trọng:

- **Ngoại lệ của tác vụ**: phát sinh trong worker; `get()` ném `ExecutionException`, ngoại lệ gốc nằm trong `cause`.
- **Ngắt luồng đang chờ**: xảy ra ở luồng đang gọi `get()`; `get()` ném trực tiếp `InterruptedException`. Điều này không có nghĩa tác vụ đã thất bại hoặc bị hủy.
- **Hủy Future**: `cancel(...)` thành công khiến `get()` ném trực tiếp `CancellationException` (unchecked), không bọc trong `ExecutionException`.
- **Hết hạn chờ**: chỉ có ở `get(timeout, unit)`; ném `TimeoutException` khi kết quả chưa sẵn sàng trong khoảng chờ. Tác vụ không tự động bị hủy.

## 2. Hai biến thể của `get()` và các ngoại lệ

Các chữ ký liên quan:

```java
T get() throws InterruptedException, ExecutionException;

T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
```

`get()` không có timeout có thể chờ vô thời hạn. `get(timeout, unit)` chờ tối đa thời gian yêu cầu; `TimeoutException` là tín hiệu về **thời gian chờ của caller**, không phải trạng thái hoàn thành cuối cùng của worker.

| Tình huống quan sát | `get()` biểu hiện | Ý nghĩa và xử lý |
|---|---|---|
| `Callable` trả kết quả | Trả `T` | Tác vụ hoàn thành thành công |
| Tác vụ ném `IOException`, `RuntimeException` hoặc `Error` | Ném `ExecutionException` | Xem `getCause()` để biết lỗi thực sự |
| Future đã bị hủy thành công | Ném `CancellationException` | Xử lý nhánh hủy riêng |
| Luồng gọi bị interrupt khi chờ | Ném `InterruptedException` | Dừng/thoát theo chính sách ngắt của caller |
| `get(timeout, unit)` chưa có kết quả đúng hạn | Ném `TimeoutException` | Có thể thử chờ tiếp hoặc chủ động hủy |
| `get()` được gọi lại khi Future đã thành công | Trả lại kết quả | Không chạy lại tác vụ |
| `get()` được gọi lại khi Future đã thất bại | Lại ném `ExecutionException` | Không tự retry tác vụ |

`ExecutionException` là **checked exception**; `InterruptedException` và `TimeoutException` cũng là checked exception. `CancellationException` kế thừa `IllegalStateException` và là **unchecked exception**.

`Future` còn có `isDone()` và `isCancelled()`. `isDone() == true` chỉ nói rằng Future đã kết thúc theo một trong các trạng thái **thành công, lỗi hoặc hủy**; không khẳng định `get()` sẽ trả kết quả thành công. `isCancelled()` cho biết Future đã bị hủy.

## 3. Ví dụ chạy độc lập: kết quả thành công và lỗi từ worker

Tạo tệp `FutureGetDemo.java`:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureGetDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> good = executor.submit(() -> 6 * 7);

            Future<Integer> bad = executor.submit((Callable<Integer>) () -> {
                throw new IllegalArgumentException("mã âm");
            });

            printResult("good", good);
            printResult("bad", bad);
        } finally {
            executor.shutdown();
        }
    }

    private static void printResult(String name, Future<Integer> future)
            throws InterruptedException {
        try {
            System.out.println(name + " = " + future.get());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            System.out.println(name + " lỗi: "
                    + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac FutureGetDemo.java
java FutureGetDemo
```

Kết quả chuẩn:

```text
good = 42
bad lỗi: IllegalArgumentException: mã âm
```

Thứ tự hoàn thành **thực tế** của hai worker có thể khác nhau, nhưng thứ tự in vẫn như trên vì chương trình gọi `printResult("good", ...)` trước. Đối với `bad`, `submit()` không ném ngay `IllegalArgumentException`; câu lệnh `future.get()` làm xuất hiện `ExecutionException` ở luồng `main`. Gọi `getCause()` trả về `IllegalArgumentException` đã phát sinh trong worker. `shutdown()` nằm trong `finally` để yêu cầu executor ngừng nhận tác vụ mới kể cả khi nhánh xử lý kết quả gặp lỗi.

Lưu ý: trên Java 17, `ExecutorService` chưa dùng được trực tiếp với `try (ExecutorService executor = ...)` theo kiểu `AutoCloseable`; mẫu `try/finally` ở trên phù hợp với Java 17.

## 4. Xử lý `ExecutionException`: nhìn vào nguyên nhân gốc

`ExecutionException` mô tả **tác vụ thất bại khi thực thi**; loại lỗi nghiệp vụ hoặc kỹ thuật thật nằm trong `getCause()`. Một `Callable` được phép ném checked exception, ví dụ `IOException`. Caller không bắt `IOException` trực tiếp từ `Future.get()` mà sẽ thấy `ExecutionException` có cause là `IOException`.

Trong một phương thức đang xử lý kết quả Future, có thể phân loại nguyên nhân như sau (đoạn mã đặt **bên trong** nhánh `catch (ExecutionException e)`; không phải chương trình chạy độc lập):

```java
Throwable cause = e.getCause();

if (cause instanceof IllegalArgumentException invalidInput) {
    // Phân loại lỗi dữ liệu/đầu vào.
    System.err.println("Đầu vào không hợp lệ: " + invalidInput.getMessage());
} else if (cause instanceof java.io.IOException ioError) {
    // Lỗi I/O gốc do Callable ném ra.
    System.err.println("Lỗi truy cập I/O: " + ioError.getMessage());
} else {
    // Giữ chuỗi nguyên nhân khi chuyển lên lớp ứng dụng.
    throw new IllegalStateException("Tác vụ bất đồng bộ thất bại", cause);
}
```

Các nguyên tắc:

1. **Giữ cause** khi ghi log hoặc bọc lại: `new IllegalStateException("...", cause)`. Chỉ in `e.getMessage()` có thể làm mất ngữ cảnh ngoại lệ gốc.
2. **Không mặc định** mọi cause là `RuntimeException`; worker có thể ném checked exception hoặc `Error`. Với lỗi hệ thống nghiêm trọng, cân nhắc chính sách xử lý/escalation riêng, đừng âm thầm nuốt lỗi.
3. Chỉ **retry** khi có chiến lược rõ ràng: lấy lại cùng một `Future` không chạy lại tác vụ. Muốn chạy lại phải tạo lần submit mới và xét đến tính idempotent, giới hạn số lần thử, backoff.
4. Không biến mọi lỗi thành giá trị dự phòng nếu việc đó che mất lỗi quan trọng. Ở ranh giới dịch vụ, có thể chuyển cause thành lỗi miền nghiệp vụ nhưng cần giữ nguyên chuỗi nguyên nhân.

`submit(Runnable)` cũng trả về `Future<?>` và lưu thất bại của Runnable để `get()` báo bằng `ExecutionException`; khi Runnable hoàn thành bình thường, `get()` thường trả `null` (hoặc giá trị result được cung cấp qua overload `submit(Runnable, result)`).

**So với `execute(Runnable)`**: `execute` không trả Future để caller gọi `get()`. Cách lỗi được báo/phơi bày từ worker phụ thuộc executor và uncaught-exception handling. Vì vậy không dùng `submit()` rồi bỏ qua Future nếu cần biết tác vụ có thành công hay không.

## 5. Ví dụ chạy độc lập: timeout không đồng nghĩa hủy tác vụ

Tệp `FutureTimeoutDemo.java` sử dụng `CountDownLatch` để bảo đảm tác vụ chưa thể hoàn thành trước khi caller thử chờ. Nhờ vậy, ví dụ không phụ thuộc vào tốc độ máy hay `Thread.sleep(...)`.

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTimeoutDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch gate = new CountDownLatch(1);

        try {
            Future<String> future = executor.submit(() -> {
                gate.await();
                return "hoàn thành";
            });

            try {
                System.out.println(future.get(100, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                System.out.println("Hết hạn chờ");
            }

            System.out.println("Đã xong? " + future.isDone());
            gate.countDown();
            System.out.println("Kết quả sau đó: " + future.get());
        } finally {
            gate.countDown();
            executor.shutdown();
        }
    }
}
```

Chạy:

```bash
javac FutureTimeoutDemo.java
java FutureTimeoutDemo
```

Kết quả:

```text
Hết hạn chờ
Đã xong? false
Kết quả sau đó: hoàn thành
```

`TimeoutException` chỉ chấm dứt lần **đợi** đầu tiên. Future vẫn có thể đang chạy; sau khi mở `gate`, lần `get()` tiếp theo lấy được kết quả. Khi timeout trong ứng dụng thực tế, chọn một trong các cách xử lý có chủ đích: tiếp tục xử lý công việc khác và kiểm tra lại; báo timeout cho caller trong khi worker được phép chạy tiếp; hoặc gọi `cancel(true)` nếu thực sự muốn yêu cầu hủy và tác vụ có hỗ trợ interrupt. Không coi timeout là chứng cứ rằng worker đã dừng.

Không nên dùng `get()` vô hạn trên đường xử lý có yêu cầu latency (chẳng hạn luồng nhận HTTP) nếu không có kế hoạch timeout, giới hạn tài nguyên và xử lý thất bại.

## 6. `InterruptedException`: caller bị ngắt, không phải `ExecutionException`

`get()` là thao tác chờ **có thể bị ngắt**. Nếu luồng **đang gọi `get()`** bị interrupt trong lúc đợi, `get()` ném `InterruptedException`. Đây là sự kiện điều khiển luồng chờ; nó không chứng minh rằng `Callable` của Future đã ném ngoại lệ. Ngược lại, một `InterruptedException` ném **trong worker** mà thoát khỏi `Callable.call()` là một thất bại của tác vụ và sẽ được quan sát dưới dạng cause của `ExecutionException` khi caller gọi `get()`.

Khi phương thức cho phép, cách đơn giản nhất là **truyền tiếp** checked exception:

```java
static <T> T await(Future<T> future)
        throws InterruptedException, ExecutionException {
    return future.get();
}
```

Nếu bắt `InterruptedException` để chuyển thành unchecked exception, thường phải **khôi phục cờ interrupt** của luồng hiện tại để tầng gọi cao hơn không mất tín hiệu:

```java
try {
    return future.get();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Luồng chờ đã bị ngắt", e);
} catch (ExecutionException e) {
    throw new IllegalStateException("Tác vụ thất bại", e.getCause());
}
```

Đoạn trên là phần thân minh họa của phương thức trả về cùng kiểu với kết quả `future.get()`. Khi việc hủy Future phù hợp nghiệp vụ, caller có thể **chủ động** gọi `future.cancel(true)` trong nhánh bị ngắt; `get()` bị interrupt không tự thực hiện hành động này.

Đừng bỏ qua `InterruptedException` bằng `catch (InterruptedException ignored) {}` rồi tiếp tục đợi vô thời hạn. Và đừng nhầm hai chiều interrupt: gọi `future.cancel(true)` thường yêu cầu ngắt **luồng worker** đang chạy tác vụ, còn `Thread.currentThread().interrupt()` tác động lên **luồng caller** hiện tại.

## 7. `cancel(...)`, `CancellationException` và trạng thái hoàn thành

```java
boolean accepted = future.cancel(true);

try {
    Object value = future.get();
} catch (java.util.concurrent.CancellationException cancelled) {
    // Future đã bị hủy; không xử lý như ExecutionException.
}
```

Đây là đoạn sử dụng minh họa, không phải chương trình độc lập. `cancel(true)` thử hủy và, nếu tác vụ đang chạy, cho phép yêu cầu interrupt worker; nó **không bảo đảm** mã worker đang thực thi sẽ dừng ngay, nhất là khi tác vụ phớt lờ interrupt. `cancel(false)` không yêu cầu interrupt worker đang chạy. Giá trị boolean trả về cho biết yêu cầu hủy có thành công theo trạng thái Future hay không; không phải xác nhận worker đã dọn dẹp xong mọi tài nguyên.

Sau khi hủy thành công, `isCancelled()` và `isDone()` đều trả `true`, và `get()` ném `CancellationException`. Tương tự, nếu một tác vụ đã thất bại, `isDone()` vẫn là `true`. Vì vậy phải **đọc kết quả hoặc ngoại lệ từ `get()`** nếu cần phân biệt thành công và lỗi.

Nếu timeout dẫn tới yêu cầu hủy, dùng mẫu có quyết định rõ:

```java
try {
    return future.get(2, java.util.concurrent.TimeUnit.SECONDS);
} catch (java.util.concurrent.TimeoutException timeout) {
    future.cancel(true); // Yêu cầu dừng theo chính sách của ứng dụng.
    throw timeout;
}
```

Đây cũng là đoạn thân phương thức minh họa; phương thức bao ngoài phải khai báo/xử lý `InterruptedException`, `ExecutionException` và `TimeoutException`, cũng như có kiểu trả về phù hợp.

## 8. Những bẫy đồng thời khi dùng `get()`

### 8.1. Deadlock/starvation khi chờ tác vụ cùng executor nhỏ

Một tác vụ chạy trong executor **chỉ có một worker** rồi gửi tác vụ con vào cùng executor và gọi `get()` chờ nó sẽ chiếm worker duy nhất; tác vụ con không có luồng để chạy.

```java
// Minh họa bẫy; KHÔNG chạy đoạn này như một chương trình thực tế.
ExecutorService one = Executors.newSingleThreadExecutor();
Future<String> outer = one.submit(() -> {
    Future<String> inner = one.submit(() -> "inner");
    return inner.get(); // Chờ tác vụ con chưa có worker để chạy.
});
System.out.println(outer.get()); // Có thể chờ vô hạn.
```

Hãy tránh chờ phụ thuộc chéo bên trong pool có tài nguyên hạn chế; thay bằng cấu trúc tác vụ không blocking, tách executor phù hợp hoặc thiết kế luồng xử lý không cần chờ một công việc đang nằm trong cùng hàng đợi.

### 8.2. `isDone()` rồi `get()` không thay thế xử lý ngoại lệ

Ngay cả khi:

```java
if (future.isDone()) {
    // get() sẽ không cần đợi trạng thái hoàn thành,
    // nhưng vẫn có thể ném ExecutionException hoặc CancellationException.
    future.get();
}
```

`isDone()` không cho biết kết quả tốt hay xấu. Nếu có cạnh tranh với thao tác `cancel()`, trạng thái cũng có thể thay đổi giữa lúc kiểm tra và lúc gọi `get()`. Thông thường dùng trực tiếp `get()` trong `try/catch` hoặc phương án timeout sẽ rõ hơn.

### 8.3. Quên lấy kết quả của Future

Với tác vụ đưa vào `submit`, các lỗi được lưu trong Future; nếu không gọi `get()` hoặc không có cơ chế quan sát khác, hệ thống có thể bỏ sót lỗi. Nên gom Future và đọc trạng thái hoàn thành, hoặc dùng một cơ chế xử lý bất đồng bộ có báo lỗi rõ ràng.

### 8.4. Đánh đồng `shutdown()` và `cancel()`

`executor.shutdown()` chỉ từ chối tác vụ mới và cho các tác vụ đã nhận cơ hội hoàn tất. Nó **không** tự hủy tất cả Future và không đợi đến khi toàn bộ worker kết thúc. Khi cần chờ executor đóng, sử dụng `awaitTermination(...)` với timeout và chiến lược interrupt; khi cần yêu cầu dừng mạnh hơn, cân nhắc `shutdownNow()` cùng mã tác vụ có hợp tác xử lý interrupt.

### 8.5. Hiểu sai visibility giữa các luồng

Theo hợp đồng bộ nhớ của `Future`, các hành động của tác vụ bất đồng bộ **happens-before** các hành động theo sau `Future.get()` tương ứng trong luồng khác. Nhờ đó, `get()` là điểm đồng bộ quan trọng khi tiêu thụ kết quả và dữ liệu được công bố bởi tác vụ. Quy tắc này không có nghĩa mọi biến chia sẻ trong toàn bộ ứng dụng tự động thread-safe; các truy cập đồng thời độc lập vẫn cần bảo vệ phù hợp.

## 9. So sánh nhanh với `CompletableFuture`

`CompletableFuture<T>` cũng triển khai `Future<T>`:

| Cách chờ | Khi giai đoạn bất đồng bộ thất bại | Khi thread caller bị interrupt |
|---|---|---|
| `future.get()` | `ExecutionException` chứa cause gốc | Có thể ném `InterruptedException` |
| `future.get(timeout, unit)` | `ExecutionException` chứa cause gốc | Có thể ném `InterruptedException`; thêm `TimeoutException` |
| `completableFuture.join()` | Thường là `CompletionException` (unchecked) chứa cause gốc | Không khai báo `InterruptedException` |

Nếu CompletableFuture bị hủy, cả `get()` và `join()` có thể ném `CancellationException` trực tiếp. `join()` không phải phép thay thế hoàn toàn cho `get()` vì cách báo lỗi và xử lý interrupt khác nhau. Nếu đang phối hợp nhiều tác vụ theo luồng bất đồng bộ, những phép ghép như `thenApply`, `thenCompose`, `handle` và `exceptionally` của CompletableFuture có thể giảm nhu cầu chặn worker bằng `get()`; đây là lựa chọn thiết kế bổ sung, không làm thay đổi hợp đồng lỗi của `Future.get()`.

## 10. Mẫu quyết định xử lý lỗi và checklist

Một luồng xử lý thông dụng có bốn nhánh tách biệt:

```text
Gọi Future.get(timeout, unit)
    ├─ trả kết quả            → dùng kết quả đã hoàn thành
    ├─ ExecutionException    → lấy cause và phân loại lỗi tác vụ
    ├─ InterruptedException  → tôn trọng interrupt của caller; propagate hoặc restore
    ├─ TimeoutException      → quyết định chờ tiếp, báo chậm hay cancel
    └─ CancellationException → xử lý hủy như một trạng thái riêng
```

Trước khi đưa code dùng `Future` vào production, kiểm tra:

- Tác vụ đã được gửi bằng `submit(...)` và Future được giữ lại để quan sát thành công/thất bại.
- Bên đọc xử lý `ExecutionException.getCause()` đúng loại lỗi cần quan tâm, giữ chuỗi nguyên nhân khi chuyển lỗi.
- Caller có chính sách timeout nếu không thể chờ vô hạn; timeout được phân biệt với hủy.
- `InterruptedException` không bị nuốt; propagate hoặc khôi phục interrupt khi cần.
- Không có chờ lồng nhau trong executor dễ làm cạn worker; tác vụ có hỗ trợ hủy/interrupt nếu cần.
- Executor được shutdown; có phương án chờ termination hoặc dọn tài nguyên theo vòng đời dịch vụ.

**Ghi nhớ:** `get()` là điểm nhận kết quả và ngoại lệ của tác vụ bất đồng bộ. `ExecutionException` cho biết **worker đã thất bại** và `getCause()` giải thích lỗi gốc; `InterruptedException`, `TimeoutException` và `CancellationException` mô tả ba tình huống riêng ở phía chờ hoặc trạng thái hủy, cần xử lý riêng theo chính sách ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-007">Tách lỗi của tác vụ khỏi lỗi ở luồng đang chờ</a>

<details>
<summary>Xem chi tiết</summary>

# Tách lỗi của tác vụ khỏi lỗi ở luồng đang chờ

Trong lập trình đa luồng Java, **luồng thực hiện tác vụ** (*worker*) và **luồng chờ kết quả** (*waiting thread/caller*) có thể gặp hai loại thất bại hoàn toàn khác nhau. Một phép tính có thể lỗi trong worker, trong khi caller vẫn chờ bình thường. Ngược lại, caller có thể bị ngắt hoặc hết thời gian chờ dù tác vụ vẫn đang chạy đúng.

Nếu trộn các tình huống này, ứng dụng dễ báo nhầm “tác vụ thất bại” khi chỉ có một yêu cầu chờ đã hết hạn; dễ hủy công việc đang cần thiết hoặc thử lại cùng một giao dịch hai lần. Để thiết kế xử lý exception đúng, phải trả lời riêng:

1. **Tác vụ đã hoàn thành như thế nào?** Thành công, thất bại hay bị hủy?
2. **Lần chờ này kết thúc thế nào?** Nhận được kết quả, bị ngắt, hết hạn chờ hay quan sát một tác vụ thất bại/bị hủy?
3. **Caller có còn quyền hoặc nhu cầu chờ nữa không?** Thất bại của thao tác chờ không mặc nhiên quyết định vòng đời tác vụ.

Bài này tập trung vào cơ chế `Future`, `ExecutorService` và `CompletableFuture` trong Java 17+, cùng cách phân loại, truyền thông tin và xử lý các đường lỗi trong hệ thống thực tế.

## 1. Hai hoạt động, hai ranh giới exception

Xét mô hình:

```text
Luồng caller                         Luồng worker
    |                                      |
    |--- submit(callable) --------------->| nhận tác vụ
    |<-- Future --------------------------|
    |                                      | tính toán / gọi I/O
    |--- future.get()                      |
    |        |                             |
    |        |                             +-- thành công: result
    |        |                             |
    |        |                             +-- thất bại: Throwable
    |        |
    +-- kết thúc chờ: value / ExecutionException
                    / InterruptedException
                    / TimeoutException / CancellationException
```

Một exception được ném trong `Callable.call()` **không tự động được ném trực tiếp trên stack của caller**. Với `ExecutorService.submit`, executor ghi nhận kết quả của tác vụ vào một `Future`; caller gọi `get()` để truy cập kết quả hoặc biết vì sao nó không có kết quả.

Tương tự, exception xảy ra trong `future.get()` không nhất thiết xuất phát từ logic bên trong task. Caller có thể bị ngắt khi đang chờ, hoặc thời hạn chờ do caller chọn có thể hết trước khi task kết thúc.

Ví dụ:

- Worker đọc dữ liệu và ném `IllegalArgumentException("bad input")`. Caller chờ bình thường, nhưng `Future.get()` ném `ExecutionException` có `getCause()` là `IllegalArgumentException`.
- Worker đang đợi I/O, caller chờ bằng `get()` thì bị thread khác gọi `interrupt()`. Chính caller gặp `InterruptedException`; worker có thể vẫn chạy.
- Worker cần 5 giây, caller chỉ cho phép đợi 100 ms bằng `get(100, MILLISECONDS)`. Caller gặp `TimeoutException`; tác vụ không bị hủy chỉ vì lần chờ hết hạn.
- Ai đó đã gọi `future.cancel(...)` thành công. Khi quan sát `Future` đã bị hủy, `get()` ném `CancellationException`.

Cần hiểu **nơi ngoại lệ phát sinh và ý nghĩa vòng đời** trước khi quyết định retry, cancel, rollback hoặc trả lỗi.

## 2. Bốn đường kết thúc quan trọng của Future.get

`Future<V>.get()` có thể trả giá trị hoặc ném exception. Phiên bản có timeout bổ sung `TimeoutException`.

| Kết quả quan sát khi caller chờ | Loại exception tại caller | Ý nghĩa chính | Có chứng minh task tự thất bại? |
| --- | --- | --- | --- |
| Task thành công | Không; `get()` trả `V` | Có kết quả | Không |
| Task kết thúc do exception | `ExecutionException` | Lỗi xảy ra trong quá trình thực thi task; đọc `getCause()` | **Có**, với task đã hoàn thành exceptional |
| Thread gọi `get()` bị ngắt khi chờ | `InterruptedException` | Thao tác chờ của **caller** bị gián đoạn | **Không** |
| Hết hạn chờ của `get(timeout, unit)` | `TimeoutException` | Caller chưa nhận kết quả trong khoảng đợi được cấp | **Không** |
| `Future` đã bị hủy | `CancellationException` (unchecked) | Kết quả không còn được cung cấp vì hủy | Không đồng nghĩa task thất bại theo logic nghiệp vụ |

Có thể thấy `Future.get()` không chỉ “ném exception của worker”. Nó là một API **chờ và quan sát trạng thái**, nên có các exception gắn với cả **kết quả** lẫn **chính hành vi chờ**.

### ExecutionException: lỗi thuộc quá trình chạy task

`ExecutionException` là lớp bao (checked exception) báo rằng phép tính do `Future` đại diện đã kết thúc bất thường. Hãy xem `getCause()` để biết lỗi nền:

```java
try {
    String value = future.get();
} catch (ExecutionException wrapper) {
    Throwable taskFailure = wrapper.getCause();
    // Phân loại theo lỗi có ý nghĩa của task/API.
}
```

Đây là snippet minh họa, giả định `future` là `Future<String>` và phần còn lại của chương trình xử lý `InterruptedException`. Không nên xem chính lớp `ExecutionException` là lỗi nghiệp vụ: nhiều loại thất bại khác nhau đều có thể bị bao trong lớp này.

Nếu `taskFailure` là một exception ứng dụng có mã lỗi ổn định, có thể xử lý có chủ đích. Nếu là bug hoặc `Error` nghiêm trọng, không tùy tiện biến thành lỗi “dữ liệu không hợp lệ”.

### InterruptedException: lỗi thuộc lần chờ của caller

`InterruptedException` trong `Future.get()` thường cho biết **thread đang gọi `get`** đã bị ngắt trong khi chờ. Java cho phép thread khác yêu cầu interruption, ví dụ khi hệ thống shutdown hoặc hủy workflow. Điều đó **không nói rằng worker đã bị ngắt** và cũng không nói rằng worker đã thất bại.

Mẫu xử lý khi không thể truyền `InterruptedException` trực tiếp lên caller:

```java
try {
    return future.get();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    throw new WaitAbortedException("Đã dừng chờ kết quả", interrupted);
}
```

Đoạn này chỉ minh họa, với `future`, kiểu trả về và `WaitAbortedException` được xác định bởi API thực. Việc gọi `Thread.currentThread().interrupt()` phục hồi cờ interrupt mà thao tác blocking đã thường xóa khi ném `InterruptedException`. Nếu phương thức có thể khai báo `throws InterruptedException` và để caller quyết định, có thể truyền tiếp chính checked exception đó.

**Không gọi `future.cancel(true)` vô điều kiện trong catch** nếu caller chỉ bị ngắt nhưng tác vụ là công việc được chia sẻ, hoặc chính sách của ứng dụng muốn tác vụ tiếp tục. Chỉ hủy khi caller thực sự sở hữu quyền hủy và giao kèo vận hành yêu cầu như vậy.

### TimeoutException: hết thời gian chờ, chưa phải timeout của task

`future.get(200, TimeUnit.MILLISECONDS)` giới hạn **thời gian caller chờ để lấy kết quả**. Nếu timeout xảy ra:

- `Future` có thể vẫn đang chạy, đang đợi trong queue hoặc vừa hoàn thành gần thời điểm hết hạn.
- `get(timeout, unit)` **không tự gọi `cancel`**.
- Caller có thể chờ lại sau, kiểm tra `isDone()`, hoặc chọn hủy nếu chính sách đã định.
- Nếu đã quyết định retry công việc mới, cần kiểm tra liệu công việc cũ có còn side effect hoặc có đang xử lý cùng dữ liệu không.

`TimeoutException` cũng không đồng nghĩa với timeout ở tầng socket, query hay dịch vụ bên ngoài. Một task có thể tự ném `SocketTimeoutException`; khi đó `Future.get` báo `ExecutionException` có cause là exception của **worker**, không phải `TimeoutException` của thao tác chờ.

### CancellationException: trạng thái đã hủy

Sau khi `Future.cancel(...)` thành công, `isCancelled()` trả `true` và `get()` ném `CancellationException`. `CancellationException` là unchecked exception.

`cancel(true)` có thể yêu cầu ngắt thread đang chạy, nhưng không bảo đảm code worker sẽ lập tức ngừng: nó phải hợp tác với interruption và có thể đã thực hiện side effect. `cancel(false)` không ngắt task đang chạy; với task chưa bắt đầu có thể ngăn thực thi, còn với task đang chạy `Future` có thể chuyển sang trạng thái hủy theo hợp đồng implementation dù công việc nền vẫn tiếp tục.

Vì thế **trạng thái đã hủy của Future** không chứng minh rằng tác vụ đã dừng mọi hoạt động hoặc rollback xong.

## 3. Ví dụ Java 17 hoàn chỉnh: tách ba nguồn lỗi

Ví dụ sau có ba nhóm kết quả được kiểm soát:

1. Task tự ném `IllegalArgumentException`; caller thấy `ExecutionException` và cause.
2. Chính task ném **`InterruptedException`**; caller vẫn thấy **`ExecutionException`**, vì exception đó xảy ra trong `Callable`.
3. Task thứ ba bị chặn bằng `CountDownLatch`. Lần chờ đầu hết hạn; lần chờ khác bị interruption; cuối cùng task vẫn được giải phóng và trả kết quả thành công.

Ví dụ cố ý dùng latch thay cho `Thread.sleep(...)` để kết quả không phụ thuộc vào thứ tự lập lịch tình cờ giữa các thread.

Tạo tệp `TaskAndWaiterFailuresDemo.java`:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class TaskAndWaiterFailuresDemo {

    private static void printTaskFailure(
            String label, Future<String> future)
            throws InterruptedException {
        try {
            future.get();
            System.out.println(label + ": unexpected success");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            System.out.println(
                label + ": ExecutionException -> "
                    + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage()
            );
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch releaseTask = new CountDownLatch(1);

        try {
            Future<String> failed = executor.submit(
                (Callable<String>) () -> {
                    throw new IllegalArgumentException("bad input");
                }
            );
            printTaskFailure("TASK_ERROR", failed);

            Future<String> taskInterrupted = executor.submit(
                (Callable<String>) () -> {
                    throw new InterruptedException("from task");
                }
            );
            printTaskFailure("TASK_INTERRUPTED", taskInterrupted);

            CountDownLatch taskStarted = new CountDownLatch(1);
            Future<String> pending = executor.submit(
                (Callable<String>) () -> {
                    taskStarted.countDown();
                    releaseTask.await();
                    return "done";
                }
            );

            // Đảm bảo worker đang ở trước điểm trả kết quả;
            // releaseTask chưa được giải phóng.
            taskStarted.await();

            try {
                pending.get(1, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                System.out.println(
                    "WAIT_TIMEOUT: cancelled=" + pending.isCancelled()
                );
            } catch (ExecutionException ex) {
                System.out.println("unexpected task failure");
            }

            AtomicReference<String> waitOutcome =
                    new AtomicReference<>("not observed");

            Thread waiter = new Thread(() -> {
                try {
                    pending.get();
                    waitOutcome.set("unexpected success");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    waitOutcome.set(
                        "InterruptedException; restored="
                            + Thread.currentThread().isInterrupted()
                    );
                } catch (ExecutionException ex) {
                    waitOutcome.set("unexpected task failure");
                }
            }, "waiting-thread");

            waiter.start();
            // Worker chưa được giải phóng nên waiter
            // không thể nhận kết quả thành công trước interrupt.
            waiter.interrupt();
            waiter.join();
            System.out.println("WAIT_INTERRUPTED: " + waitOutcome.get());

            // Caller có thể nhận kết quả sau hai lần chờ thất bại.
            releaseTask.countDown();
            try {
                System.out.println("TASK_RESULT: " + pending.get());
            } catch (ExecutionException ex) {
                System.out.println("unexpected task failure");
            }
        } finally {
            releaseTask.countDown();
            executor.shutdownNow();
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 TaskAndWaiterFailuresDemo.java
java TaskAndWaiterFailuresDemo
```

Stdout:

```text
TASK_ERROR: ExecutionException -> IllegalArgumentException: bad input
TASK_INTERRUPTED: ExecutionException -> InterruptedException: from task
WAIT_TIMEOUT: cancelled=false
WAIT_INTERRUPTED: InterruptedException; restored=true
TASK_RESULT: done
```

Đối chiếu từng tình huống:

- `TASK_ERROR`: exception được ném trong `Callable`, được `Future` lưu giữ. Người gọi nhận `ExecutionException` và phân loại `IllegalArgumentException` qua `getCause()`.
- `TASK_INTERRUPTED`: **worker tự ném `InterruptedException`** trong task. Điều này không làm `future.get()` của main ném `InterruptedException`; main đang chờ bình thường nên vẫn nhận `ExecutionException`. Hai exception có tên giống nhau nhưng phát sinh ở hai luồng khác nhau.
- `WAIT_TIMEOUT`: task thứ ba chưa thể trả kết quả vì `releaseTask` vẫn ở giá trị chưa được giải phóng. Lần chờ bị timeout và `pending.isCancelled()` vẫn `false`.
- `WAIT_INTERRUPTED`: thread `waiting-thread` bị gọi `interrupt()` khi đang chuẩn bị/đang thực hiện `get()`. Nó bắt `InterruptedException`, phục hồi cờ interrupt bằng `Thread.currentThread().interrupt()`; `isInterrupted()` trả `true`. Task không bị hủy.
- `TASK_RESULT`: sau khi `releaseTask.countDown()`, task hoàn thành và trả `"done"`. Cả timeout lẫn interruption của một waiter trước đó đều không làm task bị lỗi.
- `finally` giải phóng latch nếu main rời đoạn xử lý vì lỗi bất ngờ, rồi yêu cầu executor shutdown. `shutdownNow()` chỉ là yêu cầu dừng/chặn công việc; trong ví dụ thành công, các `Future.get()` đã hoàn thành trước khi cleanup.

**Giới hạn của ví dụ:** thread `waiter` bắt đúng các exception liên quan tới `get()`; `join()` của main vẫn có thể bị ngắt bởi cơ chế bên ngoài, và main khai báo `throws InterruptedException`. Trong ứng dụng thực, quản lý executor thường do một component owner chịu trách nhiệm với lifecycle có kiểm soát; không mặc định dùng `shutdownNow()` để kết thúc mọi tác vụ đang chạy.

## 4. Vì sao InterruptedException có thể xuất hiện ở hai vị trí?

Đây là điểm gây nhầm lẫn lớn nhất. Cùng là `InterruptedException`, nhưng ý nghĩa thay đổi theo **nơi ném và nơi bắt**:

### 4.1. InterruptedException ném trong worker

Giả sử `Callable<String>.call()` gọi `BlockingQueue.take()`, bị ngắt và cho `InterruptedException` truyền ra. Tác vụ thất bại; với `FutureTask` của `submit`, nó được ghi là nguyên nhân của một kết thúc exceptional.

Caller chờ qua `Future.get()`:

```java
try {
    future.get();
} catch (ExecutionException ex) {
    if (ex.getCause() instanceof InterruptedException) {
        // Chính task đã bị gián đoạn rồi kết thúc bằng exception.
    }
}
```

Đoạn minh họa giả định có `future`. `ExecutionException` thuộc lớp quan sát kết quả; `InterruptedException` là cause xảy ra trong worker. Caller có thể vẫn chưa từng bị interrupt.

### 4.2. InterruptedException ném bởi get() ở caller

Nếu thread đang chờ bị interrupt, `get()` có thể ném `InterruptedException` ngay ở caller:

```java
try {
    future.get();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Chính việc chờ đã bị ngắt; không suy ra task thất bại.
}
```

Đoạn này cũng chỉ là snippet, giả định xử lý `ExecutionException` được viết ở nơi phù hợp. Caller có thể rời khỏi phép chờ trong khi worker tiếp tục.

Một khác biệt kỹ thuật quan trọng: `Thread.interrupted()` **đọc và xóa** trạng thái interrupt của thread hiện tại; `Thread.currentThread().isInterrupted()` đọc nhưng không xóa. Đừng dùng `Thread.interrupted()` như lệnh “kiểm tra vô hại” trong các luồng điều khiển còn cần biết interrupt.

Trong task, nếu bắt `InterruptedException` nhưng không thể truyền tiếp, có thể phục hồi cờ interrupt rồi kết thúc có kiểm soát. Không biến interruption thành lỗi nghiệp vụ thông thường để thử lại vô hạn.

## 5. Timeout khi chờ khác với timeout xảy ra trong tác vụ

Hai dạng đều có chữ “timeout”, nhưng thuộc hai hợp đồng khác nhau:

**Timeout của lời gọi chờ:**

```java
future.get(500, TimeUnit.MILLISECONDS);
```

Nếu không có kết quả đúng lúc, **chính caller** nhận `TimeoutException`. Task không tự biết rằng caller đã bỏ chờ. Caller có thể chọn tiếp tục chờ, kiểm tra `isDone()` hoặc yêu cầu cancel.

**Timeout bên trong task:**

```java
Callable<String> task = () -> {
    // Một operation có thể ném SocketTimeoutException.
    return fetchRemoteData();
};
```

Đây là snippet giả định `fetchRemoteData()` và import phù hợp. Nếu operation trong worker ném `java.net.SocketTimeoutException` và exception ấy truyền ra `call()`, `Future.get()` ném `ExecutionException` với `getCause()` là `SocketTimeoutException`. Người gọi đã nhận **kết quả thất bại của task**, không chỉ hết giới hạn thời gian của lần chờ.

Điều kiện caller cần xử lý khác nhau:

| Nguồn timeout | Tác vụ có được biết là thất bại? | Quyết định thường gặp |
| --- | --- | --- |
| `Future.get(timeout)` vượt hạn | Chưa; có thể còn chạy hoặc sắp xong | Chờ lại, trả trạng thái pending, hoặc hủy nếu sở hữu quyền và có chính sách. |
| Timeout network trong `Callable` và exception truyền ra | Có, task hoàn thành exceptional | Phân loại lỗi I/O, đánh giá retry/idempotency. |
| Deadline ở tầng service chủ động cancel task | `Future` có thể chuyển sang trạng thái canceled; worker có thể chưa dừng | Báo hủy, phối hợp cleanup và xử lý side effect theo contract. |

Một yêu cầu HTTP bên ngoài trả “request timeout” không chứng minh thao tác phía server đã rollback. Nếu thực hiện lại ngay, có thể tạo dữ liệu trùng. Cần idempotency key hoặc cơ chế đối soát khi thích hợp.

## 6. CancellationException và giới hạn của cancel()

Một `Future` có thể được hủy bằng `cancel(boolean mayInterruptIfRunning)`. Lời gọi trả `boolean` cho biết việc chuyển sang trạng thái hủy có thành công theo `Future` hay không. Sau khi hủy thành công, `isCancelled()` trả `true` và `get()` ném `CancellationException`.

Ví dụ chạy độc lập Java 17, không yêu cầu executor:

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public class CancelledResultDemo {
    public static void main(String[] args) {
        CompletableFuture<String> result = new CompletableFuture<>();
        System.out.println("cancelled: " + result.cancel(false));

        try {
            result.join();
        } catch (CancellationException ex) {
            System.out.println("observed: CancellationException");
        }

        System.out.println("isCancelled: " + result.isCancelled());
    }
}
```

Biên dịch và chạy:

```bash
javac CancelledResultDemo.java
java CancelledResultDemo
```

Stdout:

```text
cancelled: true
observed: CancellationException
isCancelled: true
```

Ở đây `CompletableFuture` được tạo chưa hoàn thành, `cancel(false)` đánh dấu nó hoàn thành exceptional vì cancellation; không có worker nào để ngắt. `join()` ném `CancellationException` trực tiếp đối với trạng thái cancelled của chính future.

**Lưu ý đặc biệt đối với `CompletableFuture`:** tham số `mayInterruptIfRunning` của `CompletableFuture.cancel` không được sử dụng để điều khiển interrupt một task đang chạy theo cách của một `FutureTask`; cancellation được biểu diễn ở trạng thái hoàn thành của `CompletableFuture`. Đừng suy luận từ việc gọi `cancel(true)` rằng một luồng tính toán gắn với nó đã chắc chắn bị ngắt.

Với `FutureTask` hoặc implementation executor cụ thể, `cancel(true)` có thể yêu cầu interrupt thread chạy task. Yêu cầu này có thể không được phản hồi nếu task không hợp tác, đang trong một thao tác không interruptible hoặc đã thực hiện side effect trước khi dừng.

## 7. Vì sao Thread.join() không thể truyền lại lỗi của tác vụ?

`Thread.join()` chỉ chờ thread kết thúc, không mang đối tượng kết quả. Một `Thread` có thể kết thúc bình thường hoặc do uncaught exception, nhưng caller không nhận một `ExecutionException` từ `join()`.

Snippet minh họa:

```java
Thread worker = new Thread(() -> {
    throw new IllegalStateException("failed");
});
worker.start();
worker.join();

// join() trả về khi worker kết thúc; không ném lại "failed".
```

Đoạn code này cần đặt trong phương thức khai báo hoặc xử lý `InterruptedException`. Worker có thể báo uncaught exception qua `Thread.UncaughtExceptionHandler`, nhưng đó là kênh quan sát cuối cùng của **thread**, không phải một `Future` trả kết quả cho caller.

Với một tác vụ cần người gọi xử lý được lỗi và giá trị, ưu tiên abstraction kết quả như `Future`, `CompletionStage` hoặc kênh truyền kết quả có cấu trúc. Đừng dựa vào default uncaught handler để làm logic giao dịch.

## 8. ExecutorService.execute khác submit ở đường đi của exception

`execute(Runnable)` và `submit(Callable/Runnable)` có thể khác cách exception được chuyển tới client.

| API | Exception do task ném | Kênh quan sát |
| --- | --- | --- |
| `new Thread(runnable).start()` | Nếu thoát khỏi `run()`, thread kết thúc bất thường | Uncaught handler của thread/`ThreadGroup`. |
| `ThreadPoolExecutor.execute(runnable)` | Với `Runnable` thông thường và implementation chuẩn, unchecked exception thoát khỏi worker; worker có thể bị thay thế | Handler, log của worker, hoặc hook quản lý executor. |
| `ExecutorService.submit(callable)` | Exception được wrapper task như `FutureTask` bắt và ghi nhận | `Future.get()` nhận `ExecutionException` với cause. |
| `CompletableFuture.supplyAsync(supplier)` | Exception được phản ánh vào trạng thái completed exceptionally | `join/get`, `whenComplete`, `handle`, `exceptionally`. |

Đây là các hành vi thường gặp ở implementation chuẩn; nếu executor/framework sử dụng wrapper, cơ chế xử lý có thể khác. Một số framework bắt task error và gọi callback nội bộ thay vì để exception thoát đến uncaught handler.

Nếu override `ThreadPoolExecutor.afterExecute(Runnable r, Throwable t)` để giám sát, không chỉ kiểm tra `t`: với tác vụ do `submit` tạo thành `FutureTask`, `t` có thể bằng `null` dù task đã thất bại, do lỗi được lưu trong `Future`. Cần hiểu thời điểm hoàn thành, trạng thái hủy và cách truy cập `Future.get()` phù hợp trước khi xây dựng cơ chế giám sát chung.

## 9. CompletableFuture: get, join và callback lỗi

`CompletableFuture` cũng cho thấy khác biệt giữa **trạng thái kết quả** và **cách người gọi quan sát**.

Khi một stage hoàn thành exceptional vì lỗi nghiệp vụ, có hai cách đọc phổ biến:

- `future.get()`: dùng checked `ExecutionException` cho thất bại của stage; người gọi phải quan tâm `InterruptedException`.
- `future.join()`: thường ném unchecked `CompletionException` cho thất bại của stage; không khai báo checked `InterruptedException`. `join()` không trở thành một API chờ có thể ngắt theo cùng cách `get()`.
- Khi stage bị hủy, `join()` có thể ném `CancellationException` trực tiếp; các stage phụ thuộc có thể thể hiện hủy ở dạng exceptional completion và wrapping phù hợp với quan hệ stage.

Một ví dụ Java 17 độc lập dùng `CompletableFuture.failedFuture`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class StageFailureDemo {
    public static void main(String[] args) throws InterruptedException {
        CompletableFuture<String> failed =
                CompletableFuture.failedFuture(
                    new IllegalStateException("bad state")
                );

        try {
            failed.join();
        } catch (CompletionException ex) {
            System.out.println(
                "JOIN: " + ex.getCause().getClass().getSimpleName()
            );
        }

        try {
            failed.get();
        } catch (ExecutionException ex) {
            System.out.println(
                "GET: " + ex.getCause().getClass().getSimpleName()
            );
        }

        String recovered = failed.exceptionally(ex -> "fallback").join();
        System.out.println("RECOVERY: " + recovered);
    }
}
```

Biên dịch và chạy:

```bash
javac StageFailureDemo.java
java StageFailureDemo
```

Stdout:

```text
JOIN: IllegalStateException
GET: IllegalStateException
RECOVERY: fallback
```

`failedFuture` tạo một future **đã hoàn thành exceptional** nên không cần thread thực thi và không có race trong ví dụ. `join` và `get` chỉ là hai cách quan sát cùng một trạng thái đã thất bại.

`exceptionally` tạo một stage mới có thể trả giá trị thay thế. Nó không sửa trực tiếp exception của stage gốc; `failed` vẫn là stage thất bại. Nếu hàm recovery tự ném exception, stage mới cũng có thể hoàn thành exceptional.

### whenComplete và handle

- `whenComplete((value, error) -> ...)` chủ yếu để quan sát giá trị hoặc lỗi và trả một stage phụ thuộc. Nếu callback chạy thành công, stage phụ thuộc giữ trạng thái thành công/thất bại của stage gốc. Nếu callback tự ném, cần đọc quy tắc exceptional completion để hiểu lỗi nào được ưu tiên.
- `handle((value, error) -> result)` có thể **biến cả kết quả thành công lẫn lỗi thành một kết quả mới**, phù hợp khi muốn phân loại và chuyển lỗi ở ranh giới của pipeline.
- `exceptionally(error -> fallback)` xử lý đường lỗi để tạo giá trị thay thế.
- `join()` rất tiện nhưng không tự phân loại lỗi và không có checked `InterruptedException`; với hệ thống muốn cooperative cancellation của caller, chọn cơ chế chờ và giám sát thích hợp.

Không dùng callback chỉ ghi log làm bằng chứng “đã xử lý” nếu luồng nghiệp vụ vẫn cần ra quyết định. Phải quản lý stage trả về và sự thất bại của callback.

## 10. Timeout của CompletableFuture và điểm dễ nhầm

`CompletableFuture` có những API timeout làm thay đổi trạng thái future, khác với `get(timeout)`:

| API | Tác động |
| --- | --- |
| `future.get(timeout, unit)` | Đặt giới hạn chờ cho **một lần gọi**; nếu hết hạn ném `TimeoutException`, tự nó không hoàn thành/hủy future. |
| `future.orTimeout(timeout, unit)` | Sắp đặt để **chính CompletableFuture này** hoàn thành exceptional bằng `TimeoutException` nếu đến hạn mà vẫn chưa hoàn thành. |
| `future.completeOnTimeout(value, timeout, unit)` | Sắp đặt để **chính CompletableFuture này** hoàn thành bình thường bằng giá trị dự phòng nếu hết hạn trước khi nó hoàn thành. |

Các phương thức `orTimeout` và `completeOnTimeout` trả về chính `CompletableFuture` đang được cấu hình; chúng không phải là một bản sao tách biệt của phép tính ban đầu.

Khi `orTimeout` làm future hoàn thành exceptional, gọi `get()` sau đó thường ném `ExecutionException` với cause là `TimeoutException`. Đây là **lỗi đã được ghi vào trạng thái future**. Nó khác với một lời gọi `get(timeout)` tự ném `TimeoutException` ngay tại waiter.

`completeOnTimeout` cũng chỉ thiết lập kết quả của `CompletableFuture` nếu nó vẫn chưa hoàn thành lúc đến hạn; nó **không mặc nhiên ngắt hoặc dừng công việc nền** đang cố hoàn thành future. Nếu công việc cũ có side effect, vẫn phải kiểm soát riêng.

Với pipeline không muốn can thiệp trạng thái shared future chỉ để giới hạn thời gian đợi của một caller, hãy dùng cơ chế chờ có timeout hoặc thiết kế một stage riêng có chính sách timeout rõ ràng, thay vì gọi `orTimeout` tùy tiện lên shared future.

## 11. Biểu diễn lỗi ổn định qua ranh giới async

Trong API async, caller nên phân biệt **lỗi nghiệp vụ của tác vụ** và **lỗi vận hành khi chờ**. Chỉ đưa các loại lỗi cần caller xử lý vào hợp đồng công khai; tránh làm lộ exception nội bộ của executor hoặc dependency.

Ví dụ:

```java
public CompletableFuture<Report> generateReport(ReportRequest request);
```

Đây là chữ ký minh họa; `Report`, `ReportRequest` và class chứa phương thức chưa định nghĩa. Hợp đồng cần nêu:

- Future sẽ hoàn thành bình thường với `Report` trong điều kiện nào?
- Nó hoàn thành exceptional với lỗi nào có ý nghĩa ổn định: validation, không có dữ liệu, mất kết nối, hạn mức?
- Exception gốc của task được giữ trong `cause` như thế nào?
- Ai có quyền cancel và liệu cancel có dừng công việc thực tế không?
- Có giới hạn thời gian ở mức tác vụ hoặc workflow không, và timeout có thể xảy ra sau side effect nào?
- Nếu caller ngừng chờ, kết quả có còn được lưu, sự kiện có được gửi tiếp hay không?

Không nên viết API async mà buộc người dùng bắt kiểu exception của thư viện HTTP/DB nội bộ nếu đó không phải cam kết thiết kế. Cũng không nên biến mọi exception thành `RuntimeException("Failed")` rồi làm mất mã lỗi hoặc nguyên nhân có giá trị điều tra.

Nếu cần trả trạng thái “pending” hoặc “retry later” cho tầng HTTP, kết quả đó phải dựa trên chính sách của workflow; một `TimeoutException` tại waiter không đủ để tuyên bố toàn bộ task đã thất bại.

## 12. Ai sở hữu quyền hủy và vòng đời tác vụ?

Caller có quyền chờ không tự động có quyền **hủy task cho mọi người**. Một `Future` có thể đại diện cho tác vụ độc quyền của một request, hoặc một công việc được nhiều caller cùng quan sát.

Cân nhắc:

1. Task là **độc quyền** hay được **chia sẻ**? Hủy theo timeout của một caller có khiến caller khác mất công việc không?
2. Task đã thực hiện side effect chưa? Có idempotency key hoặc thao tác bù không?
3. `cancel(true)` có được task hỗ trợ bằng interruption không? Task có cleanup trong `finally`/`try-with-resources` không?
4. Nếu caller bị ngắt, chính sách có yêu cầu truyền `InterruptedException`, khôi phục cờ rồi thoát, hay vẫn duy trì task?
5. Khi executor shutdown, ai chờ task kết thúc và giải phóng tài nguyên? Không được để việc một waiter bỏ chờ làm mồ côi tài nguyên.

Tránh kết hợp mù quáng:

```java
catch (TimeoutException ex) {
    future.cancel(true);
    return retrySameOperation();
}
```

Đây là snippet **minh họa một cách làm có thể nguy hiểm**, không phải chương trình độc lập. Nếu task cũ đã ghi một phần dữ liệu hoặc không phản hồi interruption, việc retry ngay có thể gây thao tác trùng. Trước khi hủy/thử lại, phải xác định quyền sở hữu, bảo đảm dừng và tính idempotent của thao tác.

Trong một hệ thống xử lý thanh toán, timeout của request ở tầng web có thể cần trả “đang xử lý” và cung cấp mã tra cứu trạng thái, thay vì khẳng định “thanh toán thất bại”.

## 13. Xử lý exception đúng theo từng lớp

Một ví dụ giả định hệ thống có web request đợi task nhập báo cáo:

- **Task layer** bắt những exception mà nó có thể giải thích hoặc khắc phục; exception còn lại được Future/CompletionStage ghi nhận.
- **Task boundary** dịch lỗi vendor thành mã lỗi ổn định của ứng dụng, bảo toàn cause để chẩn đoán.
- **Waiting/request layer** phân biệt `ExecutionException` (task đã thất bại), `InterruptedException` (caller bị ngắt), `TimeoutException` (lần chờ hết hạn), `CancellationException` (result bị hủy).
- **Workflow owner** quyết định có cancel hay chờ tiếp, theo tính quan trọng của tác vụ và quyền sở hữu.
- **Monitoring** theo dõi task thất bại cũng như waiter timeout/interruption, nhưng không gộp tất cả thành cùng một chỉ số “task failure”.

Mẫu phân loại **minh họa**, không phải chương trình độc lập:

```java
try {
    return future.get(waitDuration, TimeUnit.MILLISECONDS);
} catch (ExecutionException ex) {
    Throwable taskCause = ex.getCause();
    throw translateTaskFailure(taskCause);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new WaitingInterruptedException(ex);
} catch (TimeoutException ex) {
    throw new ResultNotReadyException(ex);
} catch (CancellationException ex) {
    throw new TaskCancelledException(ex);
}
```

Các kiểu `WaitingInterruptedException`, `ResultNotReadyException`, `TaskCancelledException`, hàm `translateTaskFailure`, biến `future` và `waitDuration` là phần giả định của API ứng dụng. Quan trọng là **mỗi nhánh có ý nghĩa khác nhau**, và không gọi `future.cancel(true)` nếu policy không yêu cầu.

Nếu một lỗi `ExecutionException` mang cause `InterruptedException` do **task** thất bại thì đừng nhầm và tự đánh dấu **waiter** bị ngắt. Không gọi `Thread.currentThread().interrupt()` trên waiter chỉ vì cause từ worker là `InterruptedException`: đó là interrupt thuộc bối cảnh của worker, không thuộc thread hiện tại.

## 14. Đừng quên xử lý khi waiter bị interrupt

Interruption là cơ chế hợp tác, không giống việc ném một exception nghiệp vụ thông thường. Khi một phương thức blocking ném `InterruptedException`, trạng thái interrupt của thread hiện tại thường đã được xóa. Hai cách xử lý phù hợp:

**Cách A — truyền tiếp interruption:**

```java
public String waitResult(Future<String> future)
        throws InterruptedException, ExecutionException {
    return future.get();
}
```

Snippet này là một phương thức Java hợp lệ khi nằm trong một class và có import tương ứng. Caller cao hơn quyết định dừng hoặc phục hồi; phương thức không đổi nghĩa lỗi.

**Cách B — đổi loại lỗi nhưng khôi phục cờ:**

```java
try {
    return future.get();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new WaitingInterruptedException(ex);
}
```

Snippet này cần ngữ cảnh class/kiểu như đã nêu; mục đích là giữ tín hiệu interruption cho các tầng sau.

Không swallow `InterruptedException` rồi gọi `get()` lại trong vòng lặp vô hạn. Làm vậy có thể khiến shutdown không hoàn tất hoặc caller bị kẹt lâu hơn chính sách cho phép.

Nếu trong `finally` phải cleanup resource thuộc quyền sở hữu của caller, hãy dọn đúng phạm vi; không tự đóng tài nguyên của worker chỉ vì waiter bị ngắt.

## 15. Kiểm thử các kênh lỗi một cách độc lập

Một bộ test tốt phân biệt được nguồn thất bại ngay cả khi output nhìn tương tự nhau.

| Kịch bản | Kỳ vọng ở caller | Khẳng định về task |
| --- | --- | --- |
| `Callable` trả giá trị | `get()` trả kết quả | Thành công |
| `Callable` ném `IllegalArgumentException` | `ExecutionException`; cause là `IllegalArgumentException` | Hoàn thành thất bại |
| `Callable` ném `InterruptedException` | `ExecutionException`; cause là `InterruptedException` | Hoàn thành thất bại do exception trong task |
| Thread gọi `get()` bị interrupt khi đang chờ | `InterruptedException` tại waiter | Không kết luận task đã thất bại |
| `get(timeout)` hết hạn khi task còn bị chặn | `TimeoutException` | Không bị hủy bởi `get(timeout)` |
| `cancel(...)` thành công | `CancellationException` khi `get()` | Future bị hủy; không suy ra mọi side effect đã dừng |
| `CompletableFuture` exceptional | `get()` thường dùng `ExecutionException`; `join()` thường dùng `CompletionException` | Stage chứa thất bại |
| `CompletableFuture.orTimeout` hoàn thành exceptional | `get()` nhận `ExecutionException` có cause `TimeoutException` | Đây là trạng thái exceptional của future |

Dùng `CountDownLatch`, `Phaser` hoặc synchronization phù hợp để kiểm soát thứ tự test. Không dựa hoàn toàn vào `Thread.sleep(50)` rồi hy vọng tác vụ vẫn đang chạy; test theo thời gian như vậy dễ thất thường khi CI bị quá tải.

Chú ý rằng task có thể đã hoàn thành ngay trước khi caller gọi `interrupt` hoặc `get(timeout)`. Trong test muốn chắc chắn phân biệt lỗi waiter, cần giữ task ở một điểm chưa thể trả kết quả — ví dụ latch chưa được mở — như chương trình ở mục 3.

### Tránh test “đúng exception nhưng sai luồng”

Nếu test cần xác nhận `InterruptedException` của **caller**, hãy điều khiển thread đang gọi `get()` và kiểm tra code catch ở **thread đó**. Không thể kiểm tra bằng cách khiến worker throw `InterruptedException` rồi nghĩ đã thử đường lỗi waiter.

Ngược lại, nếu test cần xác nhận task failure, hãy dùng `Future.get()` và kiểm tra `ExecutionException.getCause()`. Gọi `join()` của `Thread` trực tiếp hoặc chỉ kiểm tra log của `UncaughtExceptionHandler` không thay thế bộ kiểm tra hợp đồng `Future`.

## 16. Lỗi thường gặp

**“`Future.get()` ném `InterruptedException` nên task đã bị ngắt.”** Sai: đó thường là thread gọi `get` bị ngắt. Task có thể tiếp tục chạy và trả kết quả sau.

**“Task ném `InterruptedException` nên caller phải catch `InterruptedException` từ `get`.”** Với `submit(Callable)` thông thường, task failure được bao trong `ExecutionException`, kể cả cause là `InterruptedException`.

**“`TimeoutException` nghĩa là tác vụ thất bại.”** Timeout của lần chờ chỉ nói caller chưa nhận kết quả kịp thời. Không tự hủy hoặc gán lỗi cho task.

**“Hết hạn chờ thì retry ngay.”** Tác vụ cũ có thể vẫn đang ghi dữ liệu. Retry có thể gây trùng side effect; xem xét idempotency và quyền hủy.

**“`cancel(true)` đã ngừng mọi hoạt động.”** Interruption có tính hợp tác; trạng thái canceled không chứng minh worker đã giải phóng xong tài nguyên hay rollback.

**“Bỏ `Future` là lỗi đã được logger ghi.”** Với `submit`, exception có thể được lưu trong Future và không thoát đến uncaught handler; nếu không ai đọc Future thì thất bại có thể bị bỏ qua.

**“Cứ `catch (Exception)` rồi trả một lỗi chung.”** Cách này làm mất phân biệt task failure, waiting interruption, deadline và cancellation. Việc retry hoặc trả trạng thái sai dễ xảy ra.

**“`CompletionException` và `ExecutionException` khác nguyên nhân nghiệp vụ.”** Chúng thường là hai lớp bao tùy API quan sát (`join` hoặc `get`) cho cùng thất bại của một `CompletableFuture`.

**“Lỗi của worker là lỗi interrupt của caller.”** Không khôi phục cờ interrupt của caller dựa trên `ExecutionException.getCause() instanceof InterruptedException` nếu caller chưa bị ngắt.

**“Catch `ExecutionException` là đủ cho `get`.”** Còn có `InterruptedException`; `get(timeout)` có `TimeoutException`; hủy dẫn tới `CancellationException`. Tài liệu và code phải phản ánh đủ đường lỗi mà caller cần xử lý.

## 17. Bài tập và câu hỏi tự kiểm tra

**Bài 1.** Một tác vụ xử lý file có thể mất 10 giây. API HTTP đợi `future.get(2, SECONDS)` rồi nhận `TimeoutException`. Có nên trả về “xử lý thất bại” và gửi lại task ngay?

**Gợi ý đáp án:** Không được kết luận task đã thất bại. Nên có mã công việc/trạng thái pending, xác định chính sách hủy hoặc chờ tiếp, và kiểm soát idempotency trước khi retry.

**Bài 2.** Worker gọi `queue.take()`, bị ngắt và truyền `InterruptedException` ra khỏi `Callable`. Caller gọi `get()` không bị interrupt. Caller bắt exception nào?

**Đáp án:** `ExecutionException`, với `getCause() instanceof InterruptedException`. `InterruptedException` là lỗi xảy ra trong worker, không phải exception trực tiếp của thao tác chờ ở caller.

**Bài 3.** Trong một lần chờ khác, caller đang gọi `get()` thì thread khác gọi `caller.interrupt()`. `Future` của worker có tự động canceled không?

**Đáp án:** Không. `get` của caller có thể ném `InterruptedException`, nhưng Future vẫn có thể chạy và hoàn thành; chỉ cancel khi chủ sở hữu thực hiện chính sách hủy phù hợp.

**Bài 4.** `CompletableFuture.failedFuture(new IllegalStateException("bad"))` được quan sát bằng `join()` và `get()`. Khác biệt?

**Đáp án:** `join` thường ném `CompletionException` chứa cause `IllegalStateException`, còn `get` ném checked `ExecutionException` chứa cause tương ứng. Trạng thái thất bại nền của stage là như nhau.

**Bài 5.** Caller `A` và `B` cùng quan sát một `CompletableFuture` shared. `A` gọi `orTimeout(...)` với deadline ngắn. Điều gì có thể ảnh hưởng tới `B`?

**Đáp án:** `orTimeout` tác động tới chính `CompletableFuture`; nếu deadline thắng, future hoàn thành exceptional cho mọi người quan sát, kể cả `B`. Nếu chỉ muốn giới hạn thời gian chờ riêng của `A`, không nên tùy tiện sửa shared future theo cách này.

## 18. Nguyên tắc ghi nhớ

Hãy phân loại exception theo **chủ thể chịu tác động** trước khi quyết định hành vi:

- **`ExecutionException` + cause:** tác vụ đã hoàn thành thất bại; đọc cause để xử lý lỗi từ worker.
- **`InterruptedException` ném trực tiếp bởi `get()`:** luồng caller bị ngắt khi chờ; truyền tiếp hoặc phục hồi interrupt, không tự kết luận task thất bại.
- **`TimeoutException` trực tiếp từ `get(timeout)`:** thời gian chờ của caller đã hết; task có thể vẫn tiếp tục.
- **`CancellationException`:** kết quả đã bị hủy theo trạng thái Future; việc dừng thực thi nền phụ thuộc API và cooperation.
- **`CompletionException` trong `CompletableFuture.join()`:** một cách bao lỗi khi quan sát trạng thái exceptional của stage; đọc cause thay vì nhầm với lỗi của chính luồng chờ.

Ranh giới rõ ràng giúp caller phản ứng chính xác với lỗi thật sự của task, interruption của chính mình, deadline đã hết hoặc cancellation. Nhờ vậy, ứng dụng có thể giữ trạng thái nhất quán, tránh retry trùng và bảo toàn thông tin nguyên nhân để xử lý, giám sát và chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-008">TimeoutException và CancellationException</a>

<details>
<summary>Xem chi tiết</summary>

# TimeoutException và CancellationException trong Java Concurrency

## 1. Hai tín hiệu rất khác nhau khi làm việc với tác vụ bất đồng bộ

Khi một luồng gọi chờ kết quả từ tác vụ đang chạy ở luồng khác, có ít nhất hai câu hỏi phải tách biệt:

- **Đã chờ quá thời hạn cho phép chưa?** Đây là vấn đề *timeout*: lời gọi chờ không nhận được kết quả trong khoảng thời gian quy định.
- **Kết quả của tác vụ đã bị hủy chưa?** Đây là vấn đề *cancellation*: một đối tượng quản lý tác vụ ghi nhận rằng kết quả không còn được chờ nhận theo đường hoàn tất thông thường.

Trong Java, **java.util.concurrent.TimeoutException** và **java.util.concurrent.CancellationException** biểu diễn hai tình huống này. Chúng thường xuất hiện khi làm việc với **Future**, **ExecutorService** và **CompletableFuture**, nhưng ý nghĩa và cách xử lý không giống nhau.

Một lần gọi **Future.get(timeout, unit)** có thể ném TimeoutException vì chờ quá lâu. **Điều đó không tự động dừng task.** Task có thể tiếp tục chạy, hoàn tất sau đó và trả về kết quả trong một lần get khác. Ngược lại, khi Future đã chuyển sang trạng thái canceled, **Future.get()** sẽ ném CancellationException; ngay cả khi mã của worker còn đang chạy một thời gian do chưa hợp tác với yêu cầu hủy, đối tượng Future đã không còn cung cấp kết quả theo đường thông thường.

Nguyên tắc quan trọng nhất: **timeout là tình trạng của lần chờ; cancellation là trạng thái hủy của tác vụ/kết quả được quản lý**. Một timeout có thể khiến ứng dụng *quyết định* yêu cầu hủy, nhưng việc yêu cầu ấy là một hành động riêng cần được thiết kế rõ ràng.

## 2. Khái niệm và phân loại exception

| Khía cạnh | TimeoutException | CancellationException |
| --- | --- | --- |
| Package | **java.util.concurrent** | **java.util.concurrent** |
| Kiểu | Checked exception, kế thừa **Exception** | Unchecked exception, kế thừa **IllegalStateException** qua **RuntimeException**? Không: nó kế thừa trực tiếp **IllegalStateException**, là một **RuntimeException** |
| Ý nghĩa trong Future | Không nhận được kết quả trong khoảng chờ chỉ định | Future đã bị hủy |
| Ví dụ thường gặp | **future.get(100, MILLISECONDS)** hết thời gian | **future.get()** sau khi Future được hủy |
| Task nền chắc chắn đã dừng? | **Không** | **Không nhất thiết** |
| Tự động gọi **cancel**? | **Không** | Trạng thái hủy thường do lời gọi **cancel** hoặc cơ chế hủy của API |
| Hướng xử lý | Xác định deadline, retry, fallback, hoặc quyết định hủy | Tôn trọng ý định hủy; dừng chờ, cleanup và báo trạng thái phù hợp |

**Lưu ý về phân loại:** CancellationException **trực tiếp extends IllegalStateException**, mà IllegalStateException extends RuntimeException. TimeoutException trực tiếp extends Exception. Vì là checked exception, TimeoutException phải được bắt hoặc khai báo nếu phương thức có thể ném nó qua chữ ký. CancellationException là unchecked, nên Java không bắt buộc khai báo trong **throws**.

Không phải mọi API có chữ “timeout” đều ném TimeoutException. **Thread.sleep** bị interrupt sẽ ném InterruptedException; **Object.wait(timeout)** hết thời gian bình thường chỉ trở về; **CountDownLatch.await(timeout, unit)** trả về **false** nếu hết thời gian; **Lock.tryLock(timeout, unit)** trả về **false** nếu không lấy được lock. Cần đọc hợp đồng của đúng phương thức đang gọi.

## 3. Mô hình trạng thái: deadline, hoàn tất và hủy

Một Future thường được xem dưới các khái niệm:

- **Chưa hoàn tất:** task đang chờ hoặc đang chạy; **isDone()** thường là false.
- **Hoàn tất bình thường:** có kết quả; **get()** trả về kết quả.
- **Hoàn tất có exception:** **get()** ném **ExecutionException** có cause là lỗi task.
- **Đã bị hủy:** **isCancelled()** là true, **isDone()** cũng là true, và **get()** ném **CancellationException**.

**isDone() = true không có nghĩa thành công.** Nó bao gồm hoàn tất bình thường, hoàn tất bất thường và cancellation. Cũng không được suy ra “worker đã thoát” từ **isDone()** của một Future bị hủy, vì cơ chế hủy có thể đánh dấu Future đã canceled trước khi luồng chạy thực sự kết thúc.

Sơ đồ khái niệm:

~~~text
Future chưa hoàn tất
    |                  |
    | get(timeout)     | cancel(...)
    | quá thời hạn     v
    |              Future canceled
    |                  |
    |                  +-- get() -> CancellationException
    |
    +-- TimeoutException cho lượt chờ này
    |
    +-- Task vẫn có thể chạy tiếp
            |
            +-- Thành công -> get() trả kết quả
            |
            +-- Thất bại -> get() ném ExecutionException
~~~

Sơ đồ tập trung vào kết quả quan sát từ **Future**, không phải biểu đồ tất cả trạng thái nội bộ của **Thread** hoặc **FutureTask**. Một lời gọi get hết hạn không chuyển Future sang canceled.

## 4. Ví dụ Java 17+ hoàn chỉnh và chạy được

Chương trình sau tạo một **ExecutorService** với một worker. Tình huống đầu giữ tác vụ ở trạng thái chờ bằng **CountDownLatch**, gây timeout rồi mở latch để lấy kết quả sau đó. Tình huống thứ hai chủ động hủy task bằng **cancel(true)** và kiểm tra worker có quan sát interrupt không.

Dùng latch để điều khiển trình tự giúp không phải dựa vào việc máy tính nhanh hay chậm để quyết định kết quả. Khoảng chờ 30 ms chỉ phục vụ minh họa cho timeout khi latch vẫn đóng, không phải một khuyến nghị timeout cho ứng dụng thực tế.

Lưu vào tệp **TimeoutCancellationDemo.java**:

~~~java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeoutCancellationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch releaseFirst = new CountDownLatch(1);
            Future<String> first = executor.submit(() -> {
                releaseFirst.await();
                return "completed";
            });
            try {
                first.get(30, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("1. First get: TimeoutException");
            }
            System.out.println("2. Cancelled by timeout: " + first.isCancelled());
            releaseFirst.countDown();
            System.out.println("3. Later result: " + first.get());

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch releaseSecond = new CountDownLatch(1);
            CountDownLatch exited = new CountDownLatch(1);
            AtomicBoolean sawInterrupt = new AtomicBoolean(false);
            Future<String> second = executor.submit(() -> {
                started.countDown();
                try {
                    releaseSecond.await();
                    return "completed";
                } catch (InterruptedException e) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                    return "stopped";
                } finally {
                    exited.countDown();
                }
            });

            started.await();
            try {
                second.get(30, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("4. Second get: TimeoutException");
            }
            System.out.println("5. Cancel request accepted: " + second.cancel(true));
            exited.await();
            System.out.println("6. Worker saw interrupt: " + sawInterrupt.get());
            System.out.println("7. Cancelled: " + second.isCancelled());
            System.out.println("8. Done: " + second.isDone());
            try {
                second.get();
            } catch (CancellationException e) {
                System.out.println("9. Get after cancel: CancellationException");
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 TimeoutCancellationDemo.java
java TimeoutCancellationDemo
~~~

Kết quả stdout đã được xác minh bằng biên dịch và chạy chương trình:

~~~text
1. First get: TimeoutException
2. Cancelled by timeout: false
3. Later result: completed
4. Second get: TimeoutException
5. Cancel request accepted: true
6. Worker saw interrupt: true
7. Cancelled: true
8. Done: true
9. Get after cancel: CancellationException
~~~

### Giải thích phần đầu: timeout không hủy task

**releaseFirst** chưa được mở. Task đầu tiên gọi **releaseFirst.await()**, nên chưa thể trả về. Vì vậy **first.get(30, MILLISECONDS)** ném TimeoutException. Sau khi bắt ngoại lệ, **first.isCancelled()** là false: quá thời gian đợi *không yêu cầu hủy*.

Lệnh **releaseFirst.countDown()** cho task được tiếp tục, và **first.get()** sau đó trả về **completed**. Điều này chứng minh một Future đã từng gây TimeoutException cho một lần đợi vẫn có thể cung cấp kết quả bình thường ở lần đợi sau.

### Giải thích phần sau: yêu cầu hủy và interrupt

Task thứ hai đã báo **started** rồi chờ trong **releaseSecond.await()**. Luồng main chờ chắc chắn task đã bắt đầu trước khi gọi get có timeout.

Sau TimeoutException, **second.cancel(true)** yêu cầu hủy và gửi yêu cầu interrupt tới worker đang thực thi. **CountDownLatch.await()** là thao tác chờ có thể bị ngắt; nó ném **InterruptedException**. Worker ghi nhận sự kiện này vào **AtomicBoolean**, khôi phục interrupt flag rồi thoát đường chờ. **exited.await()** đảm bảo main không in trạng thái **sawInterrupt** trước khi worker có cơ hội xử lý interrupt.

Trong tình huống minh họa có điều khiển này, **cancel(true)** trả về true, **isCancelled()** và **isDone()** là true. **second.get()** ném CancellationException thay vì trả về chuỗi **stopped**: khi kết quả Future đã canceled, giá trị do mã Callable trả về sau interrupt không trở lại đường get bình thường.

**Điểm cần phân biệt:** Việc worker thật sự quan sát interrupt được kiểm chứng ở ví dụ nhờ cờ riêng và latch **exited**. Chỉ nhìn vào **isCancelled()** hoặc giá trị true của **cancel(true)** không đủ để khẳng định mọi tác vụ thực tế đã dừng toàn bộ tác dụng phụ.

## 5. Future.get(timeout) có nghĩa chính xác là gì?

Phương thức:

~~~java
// Phương thức của Future<T>, minh họa chữ ký API:
T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
~~~

Yêu cầu luồng gọi chờ tối đa khoảng thời gian được chỉ định để nhận kết quả Future. Theo hợp đồng API, kết quả có thể là:

| Diễn biến | Kết quả từ get(timeout, unit) |
| --- | --- |
| Task hoàn tất bình thường trong thời gian chờ | Trả về kết quả |
| Task hoàn tất bằng exception | Ném **ExecutionException**, có cause là exception từ task |
| Future đã canceled | Ném **CancellationException** (unchecked) |
| Hết thời gian mà kết quả chưa sẵn sàng | Ném **TimeoutException** |
| Luồng đang chờ bị interrupt | Ném **InterruptedException** |

Vì vậy, **catch TimeoutException** là một nhánh điều khiển của lời gọi đợi. Nhánh này không nói task xử lý được bao nhiêu phần trăm công việc, đã ghi dữ liệu bên ngoài hay chưa, hoặc có nên retry hay không.

### Thời hạn của lần chờ và thời hạn nghiệp vụ

Mỗi lần gọi **future.get(1, SECONDS)** có một khoảng chờ riêng. Nếu ứng dụng lặp lại lời gọi 10 lần, nó có thể dành khoảng thời gian chờ lớn hơn 1 giây cho cùng một task. Muốn giới hạn thời gian của cả chuỗi bước nghiệp vụ, hãy dùng **deadline chung** và tính phần thời gian còn lại trước từng bước; không reset timeout vô tình sau mỗi lần retry.

Ví dụ, một request có ngân sách thời gian 500 ms cho cả việc gọi dịch vụ A, lấy kết quả từ Future và ghi phản hồi. Dùng riêng 500 ms cho từng bước sẽ khiến tổng thời gian có thể vượt xa deadline thực tế.

### Timeout và thực thi chậm

Timeout có thể là tín hiệu cần quan sát hiệu năng, tắc nghẽn thread pool, deadlock, giới hạn tài nguyên hoặc một dependency chậm. Một TimeoutException đơn lẻ không tự chứng minh lỗi nghiệp vụ hay treo vĩnh viễn. Cần xem trạng thái executor, tiến độ tác vụ, thời gian đã tiêu tốn và hệ quả nếu task vẫn tiếp tục chạy.

### API khác có thời gian chờ nhưng không ném TimeoutException

- **CountDownLatch.await(timeout, unit)** trả về boolean.
- **Thread.join(millis)** hết thời gian có thể trở về mà thread vẫn sống.
- **BlockingQueue.poll(timeout, unit)** có thể trả về null nếu không có phần tử trong hạn chờ.
- **Semaphore.tryAcquire(timeout, unit)** trả về false khi không lấy được permit.
- **Future.get(timeout, unit)** mới dùng TimeoutException cho hết hạn chờ theo hợp đồng của Future.

Vì vậy không nên viết một catch TimeoutException cho mọi API có tham số timeout mà chưa xem chữ ký phương thức.

## 6. Future.cancel(boolean) và tính hợp tác khi hủy

Giao diện Future cung cấp:

~~~java
// Phương thức trên một Future<?>:
boolean cancel(boolean mayInterruptIfRunning);
~~~

Giá trị trả về biểu thị yêu cầu hủy có chuyển trạng thái Future thành canceled thành công hay không theo hợp đồng triển khai. Thông thường, nếu tác vụ đã hoàn tất bình thường hoặc đã bị hủy trước đó, lời gọi cancel sau sẽ trả về false. Có race giữa hoàn tất và hủy; phải xử lý cả hai kết quả.

Hai cách gọi mang ý nghĩa khác nhau:

- **cancel(false):** yêu cầu hủy kết quả Future mà không đề nghị ngắt luồng đang thực thi. Nếu task đã chạy, mã của nó có thể tiếp tục cho đến khi tự hoàn tất.
- **cancel(true):** cho phép cơ chế thực thi gửi interrupt đến thread đang chạy, khi triển khai hỗ trợ. Đây là **tín hiệu hợp tác**, không phải lệnh buộc hệ điều hành giết thread.

Ngay cả khi **cancel(true)** trả về true, không thể suy ra thao tác I/O, truy vấn database hoặc code bên trong chắc chắn đã dừng ngay. Nếu worker chỉ tính toán trong vòng lặp và không kiểm tra interrupt, nó có thể tiếp tục chạy. Một số thư viện chặn có cơ chế ngắt riêng; những lời gọi khác không phản ứng với interrupt hoặc cần API cancel/close chuyên biệt.

Ví dụ mã minh họa cho một worker có thể hủy:

~~~java
// Minh họa trong Callable<Result> hoặc Runnable có vòng lặp.
while (!Thread.currentThread().isInterrupted()) {
    doOneSmallUnitOfWork();
}
// Dọn dẹp theo hợp đồng rồi kết thúc.
~~~

Khi worker dùng API ném **InterruptedException**:

~~~java
// Minh họa trong thân worker; chưa phải chương trình độc lập.
try {
    blockingOperation();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Hoàn tất cleanup thích hợp rồi thoát.
    return;
}
~~~

Việc khôi phục interrupt flag có ý nghĩa vì những phương thức như **CountDownLatch.await()** khi ném InterruptedException thường xóa trạng thái interrupt trên thread. Tuy nhiên, có trường hợp API muốn **ném tiếp InterruptedException** thay vì tự khôi phục và trả về. Hãy theo hợp đồng tầng gọi và tránh nuốt tín hiệu hủy rồi tiếp tục như bình thường.

## 7. TimeoutException, CancellationException, InterruptedException và ExecutionException

Bốn loại exception sau thường xuất hiện trong cùng một luồng nghiệp vụ nhưng không thể dùng thay cho nhau:

| Exception | Điều gì xảy ra? | Điều nên cân nhắc |
| --- | --- | --- |
| **TimeoutException** | Caller đợi quá lâu | Deadline, fallback, task còn chạy, có cần hủy không |
| **CancellationException** | Future đã canceled | Tôn trọng hủy; cleanup, không chờ kết quả bình thường |
| **InterruptedException** | Thread đang chờ bị yêu cầu interrupt | Khôi phục flag hoặc ném tiếp, dừng/chuyển giao điều khiển |
| **ExecutionException** | Bản thân task hoàn tất bằng lỗi | Kiểm tra **getCause()**, phân loại lỗi nghiệp vụ/hạ tầng |

**InterruptedException** của **future.get(...)** liên quan tới *thread đang chờ* bị interrupt. Nó không có nghĩa task worker tự bị canceled. Một luồng main có thể bị interrupt khi đang chờ Future, trong khi worker vẫn tiếp tục chạy. Nếu nghiệp vụ muốn hủy công việc khi caller bị interrupt, đó là quyết định phải thêm vào chính sách xử lý.

**ExecutionException** cũng khác CancellationException. Nếu Callable ném IllegalArgumentException, Future chưa hẳn bị hủy; **future.get()** thường ném ExecutionException với cause IllegalArgumentException. Không được dùng isCancelled() để phát hiện mọi tác vụ thất bại.

Đừng dựa vào chuỗi message để suy diễn loại lỗi. Hãy bắt đúng exception, kiểm tra **getCause()** khi cần và giữ stack trace, context.

## 8. Mẫu xử lý Future theo chính sách có timeout

Một phương thức ứng dụng thường có thể chọn hủy task khi vượt deadline. Đó là **chính sách ứng dụng**, tách khỏi hành vi mặc định của **get(timeout)**:

~~~java
// Mã minh họa trong một phương thức đã có:
// Future<Result> future; long waitMillis;
// logger và nghiệp vụ xử lý kết quả được định nghĩa ở nơi khác.
try {
    Result value = future.get(waitMillis, TimeUnit.MILLISECONDS);
    useResult(value);
} catch (TimeoutException e) {
    boolean accepted = future.cancel(true);
    recordTimeout(accepted, e);
} catch (CancellationException e) {
    recordCancellation(e);
} catch (ExecutionException e) {
    Throwable taskCause = e.getCause();
    recordTaskFailure(taskCause);
} catch (InterruptedException e) {
    future.cancel(true);
    Thread.currentThread().interrupt();
    recordCallerInterrupted(e);
}
~~~

Đây là đoạn **theo ngữ cảnh**, chưa phải một class Java độc lập. Các hàm **useResult**, **recordTimeout**, **recordCancellation**, **recordTaskFailure** và **recordCallerInterrupted** đại diện cho ứng xử riêng của ứng dụng. Không nên copy đoạn này rồi tưởng rằng gọi **recordTimeout** đã xử lý tác dụng phụ đang chạy dở.

Trong code thật, cần xác định:

1. Hết timeout có luôn hủy hay vẫn giữ task để dùng kết quả sau?
2. Có được phép retry không? Nếu retry, task cũ đã chắc chắn dừng tác dụng phụ chưa?
3. Nếu **cancel** thất bại do task vừa hoàn tất, có cần đọc kết quả cuối không?
4. Nếu caller bị interrupt, nên hủy công việc liên quan, chuyển việc cho luồng khác, hay để job dài hạn chạy tiếp?
5. Có cần ghi nhận trạng thái “timed out but completion unknown” đối với hệ bên ngoài không?

Cũng nên cân nhắc việc ghi log một lần tại ranh giới quan sát lỗi, để không nhân nhiều log trùng nhau qua các tầng.

## 9. Cancellation là trạng thái Future, không phải bằng chứng worker đã ngừng

Đây là bẫy dễ gặp trong thực hành.

Khi Future được đánh dấu canceled, **isDone()** thường là true và **get()** ném CancellationException. Nhưng worker có thể:

- Chưa bắt đầu chạy và được ngăn không chạy nữa.
- Đang bị chặn trong API có khả năng nhận interrupt và sẽ dừng khi API phản ứng.
- Đang tính toán dài nhưng không kiểm tra interrupt.
- Đang gọi hệ thống ngoài không có đường hủy hiệu quả.
- Đã thực hiện một phần side effect, sau đó mới nhận yêu cầu cancel.

Do đó, **isCancelled() == true không phải giấy chứng nhận đã rollback**. Nếu một task đã gửi request thanh toán và bị hủy local Future, bên thanh toán vẫn có thể đã xử lý request. Nếu một task đã ghi một phần file, cancel không tự xóa dữ liệu. Nếu một task đã sửa object chia sẻ, cancel không đảo ngược thay đổi.

Khi cần bảo toàn trạng thái mạnh, tổ chức task để tính toán trên dữ liệu tạm, kiểm tra cancellation ở các ranh giới thích hợp và chỉ công bố kết quả khi chắc chắn còn được phép commit. Với tác vụ có side effect bên ngoài, cần transaction, idempotency, trạng thái công việc hoặc cơ chế phục hồi riêng.

Một tác vụ có thể tự kết thúc bình thường khi nhận interrupt, nhưng Future vẫn đã bị hủy nếu lệnh cancel thắng cuộc đua với hoàn tất. Điều này đã được minh họa bằng giá trị **"stopped"** trong Callable của chương trình chính: giá trị đó không được trả qua get sau khi Future bị canceled.

## 10. Race condition giữa timeout, hoàn tất và cancel

Các thời điểm sau không diễn ra trong một transaction duy nhất:

~~~text
Caller                      Worker
  |                            |
  |--- get(timeout) ---------->|
  |                            | đang tính toán
  |<-- TimeoutException -------|
  |--- cancel(true) ---------->| có thể vừa hoàn tất
  |<-- true hoặc false --------|
~~~

Trong khoảng từ lúc caller nhận TimeoutException đến khi gọi cancel, worker có thể hoàn tất. Khi đó cancel có thể không còn chuyển được trạng thái Future và trả về false. Một nhánh ứng dụng ghi log “đã hủy thành công” ngay khi bắt TimeoutException là không có cơ sở.

Cùng một task có thể có nhiều consumer chờ Future với các timeout khác nhau. Timeout của consumer thứ nhất không tự hủy task cho consumer thứ hai. Nếu consumer thứ nhất chủ động cancel một Future dùng chung, các consumer còn lại có thể bất ngờ nhận CancellationException. Vì vậy quyền hủy nên thuộc về thành phần sở hữu vòng đời task, không tùy tiện đặt ở bất kỳ nơi gọi get nào.

Nếu task đã có side effect, retry ngay sau TimeoutException có thể tạo tác dụng phụ trùng lặp. Ví dụ timeout chờ phản hồi từ một job tạo hóa đơn không chứng minh hóa đơn chưa được tạo. Cần có request id, idempotency key hoặc cơ chế truy vấn trạng thái công việc trước khi retry.

## 11. CompletableFuture: timeout và cancellation có khác Future không?

**CompletableFuture<T>** vừa là một **Future<T>**, vừa hỗ trợ pipeline theo mô hình **CompletionStage**. Một vài API liên quan nhưng có ngữ nghĩa khác:

| API | Hành vi chính |
| --- | --- |
| **cf.get(timeout, unit)** | Đợi tối đa thời gian; có thể ném checked TimeoutException; không tự hủy |
| **cf.get()** | Đợi và trả kết quả, hoặc ném ExecutionException/CancellationException |
| **cf.join()** | Đợi, không khai báo checked InterruptedException/ExecutionException; lỗi hoàn tất bất thường thường bọc qua CompletionException; canceled trực tiếp có thể ném CancellationException |
| **cf.orTimeout(timeout, unit)** | Nếu chưa hoàn tất khi hết hạn, hoàn tất **chính CompletableFuture đó** một cách bất thường với TimeoutException |
| **cf.completeOnTimeout(value, timeout, unit)** | Nếu chưa hoàn tất khi hết hạn, hoàn tất **chính CompletableFuture đó** bình thường với giá trị fallback |
| **cf.cancel(true/false)** | Hoàn tất CompletableFuture theo trạng thái cancellation; tham số **mayInterruptIfRunning** không điều khiển interrupt của quá trình tính toán do CompletableFuture sở hữu |

**Điểm khác biệt quan trọng:** **cf.get(200, MILLISECONDS)** hết hạn ném TimeoutException trực tiếp tại lời gọi chờ. Còn **cf.orTimeout(200, MILLISECONDS)** thiết lập việc *hoàn tất bất thường chính cf* khi hết hạn. Nếu sau đó gọi **cf.get()**, người gọi thường nhận **ExecutionException** có **getCause()** là TimeoutException; với **join()**, người gọi nhận CompletionException có cause là TimeoutException. Hai đường này không nên bị gộp thành cùng một nhánh catch.

Ví dụ minh họa theo ngữ cảnh:

~~~java
// Minh họa phương thức Java 9+; không phải chương trình đầy đủ.
CompletableFuture<String> result = new CompletableFuture<>();
result.orTimeout(200, TimeUnit.MILLISECONDS);

// Nếu không ai hoàn tất result trước hạn, chính result sẽ
// được hoàn tất bất thường với TimeoutException.
~~~

Với **completeOnTimeout("fallback", 200, MILLISECONDS)**, nếu chưa hoàn tất trước hạn, Future có thể hoàn tất bình thường bằng **fallback**, vì vậy consumer không nhất thiết thấy exception. Hãy thiết kế rõ việc fallback là dữ liệu thật hay trạng thái suy giảm chất lượng.

### Timeout CompletableFuture không mặc nhiên giết computation

Các phương thức **orTimeout**, **completeOnTimeout** và **cancel** quyết định trạng thái hoàn tất của CompletableFuture. Chúng **không tự bảo đảm** rằng một supplier đang chạy trong **supplyAsync** đã bị interrupt, dừng xử lý hoặc ngừng tạo side effect. Đặc biệt, tài liệu **CompletableFuture.cancel(boolean)** nêu tham số **mayInterruptIfRunning** không có hiệu lực vì interruption không được dùng để điều khiển xử lý theo cách đó.

Nếu cần hủy computation thực sự, quản lý riêng task bên dưới (ví dụ Future trả từ ExecutorService), token hủy, hoặc API của dependency. Không nên giả định rằng gọi **CompletableFuture.cancel(true)** tương đương gọi **FutureTask.cancel(true)** đối với một worker đang chạy.

### Nhánh xử lý bất thường trong pipeline

**exceptionally**, **handle** và **whenComplete** có thể dùng để quan sát hoặc chuyển đổi kết quả bất thường. Nhưng:

- **exceptionally** có thể tạo fallback và biến pipeline hậu tiếp thành hoàn tất bình thường.
- **handle** nhận cả kết quả và Throwable, có thể chuyển đổi thành giá trị hoặc lỗi khác.
- **whenComplete** thích hợp cho quan sát/side effect, nhưng callback tự ném lỗi cũng cần được xét.
- Một stage phụ thuộc có thể biểu diễn lỗi của stage trước bằng **CompletionException**, nên cần quan sát chuỗi cause thay vì giả định mọi stage đều ném đúng loại exception gốc.

Nếu downstream xử lý cả timeout và cancellation, hãy phân biệt TimeoutException được dùng để hoàn tất bất thường, CancellationException do Future canceled, và những exception nghiệp vụ khác.

## 12. Mối quan hệ với ExecutorService và shutdown

**ExecutorService.submit(...)** thường trả về Future để quan sát kết quả và yêu cầu hủy. **ExecutorService.shutdown()** ngừng nhận task mới nhưng **không tự cancel mọi task đang chạy**. **shutdownNow()** cố gắng dừng tác vụ đang chạy bằng interrupt và trả về danh sách tác vụ chưa được bắt đầu, nhưng không bảo đảm mọi tác vụ đang chạy sẽ dừng ngay.

**Future.cancel(true)** nhắm tới một Future cụ thể; **shutdownNow()** là yêu cầu dừng ở phạm vi executor. Hai thao tác có mục tiêu khác nhau, và cả hai đều cần worker hợp tác đối với interruption.

Trong một ứng dụng có executor sống lâu, đừng tắt executor chung chỉ vì một yêu cầu client timeout. Nếu tạo executor riêng cho một khối nghiệp vụ, cần có chính sách đóng, await termination và quản lý thread để tránh rò rỉ tài nguyên. Nếu task không hợp tác với interrupt, **awaitTermination** cũng có thể hết hạn trong khi một số worker còn sống.

Một số API cấp cao có cơ chế timeout/cancellation riêng, như **ExecutorService.invokeAll(tasks, timeout, unit)**, có thể hủy những task chưa hoàn tất khi hết thời gian theo hợp đồng của nó. Không chuyển quy tắc của API đó sang **Future.get(timeout)**; mỗi phương thức cần đọc đúng JavaDoc.

## 13. Thiết kế deadline và cancellation cho API nghiệp vụ

Một thiết kế rõ ràng nên tách ba quyết định:

**Thời gian chờ của caller:** Người gọi có thể chịu được bao lâu? Đây là timeout hoặc deadline. Một caller HTTP có thể dừng đợi nhưng job nền vẫn cần chạy để hoàn tất xử lý.

**Quyền hủy kết quả/task:** Ai được cancel và khi nào? Nếu nhiều client cùng chờ một Future cache chung, một client timeout không nên mặc nhiên hủy công việc mà client khác còn cần.

**Khả năng dừng side effect:** Worker có thể đáp ứng interruption ở đâu, cần rollback hoặc compensation gì? Một lời gọi mạng bị timeout không khẳng định server chưa commit.

Có thể định nghĩa kết quả nghiệp vụ thành những trạng thái dễ phân biệt:

- **SUCCEEDED:** có dữ liệu và kết quả đã được xác nhận.
- **FAILED:** task hoàn tất với lỗi thật sự, cần giữ cause.
- **TIMED_OUT_WAITING:** caller hết hạn chờ, kết quả cuối có thể chưa biết.
- **CANCEL_REQUESTED:** đã phát yêu cầu cancel nhưng việc dừng thực tế có thể còn đang diễn ra.
- **CANCELED:** Future ở trạng thái canceled; nghiệp vụ vẫn phải xem xét side effect.
- **UNKNOWN_REMOTE_OUTCOME:** phía ngoài có thể đã xử lý nhưng phản hồi không về trước hạn.

Những tên trạng thái này chỉ là gợi ý thiết kế ứng dụng, **không** phải các enum chuẩn có sẵn của JDK.

### Khi nào nên retry?

Retry chỉ an toàn khi hiểu tính chất thao tác:

- Phép tính thuần trong bộ nhớ, không side effect, có thể tạo task mới nếu bị timeout theo chính sách.
- Đọc dữ liệu có thể retry với timeout/backoff hợp lý, nhưng cần tránh làm quá tải dependency chậm.
- Tạo đơn hàng, ghi tiền hoặc phát sự kiện cần idempotency/deduplication nếu retry có thể tạo kết quả trùng.
- Task đã bị canceled ở local nhưng external service chưa xác nhận dừng cần truy vấn trạng thái trước khi khởi chạy lại, nếu API hỗ trợ.

Không nên dùng một catch chung để coi TimeoutException, CancellationException, InterruptedException và ExecutionException là “lỗi mạng tạm thời” rồi retry đồng loạt.

## 14. Cách kiểm thử timeout và cancellation đáng tin

Kiểm thử với **Thread.sleep(100)** và hy vọng kết quả đến đúng thứ tự thường dễ lỗi trên máy CI chậm hoặc khi scheduler thay đổi. Để kiểm thử deterministic, nên dùng **CountDownLatch**, **CyclicBarrier** hoặc công cụ đồng bộ phù hợp nhằm chủ động điều khiển thời điểm task có thể hoàn tất.

Bảng tình huống cần xác minh:

| Kịch bản | Kiểm tra quan trọng |
| --- | --- |
| Future chưa hoàn tất, get có deadline ngắn | TimeoutException; Future chưa tự canceled |
| Sau timeout, cho task hoàn tất | get tiếp theo trả kết quả đúng |
| Cancel trước khi task bắt đầu | Future chuyển canceled nếu hủy thắng race |
| Cancel(true) khi worker đang await | Interrupt được quan sát nếu API hỗ trợ, Future get ném CancellationException |
| Cancel(false) khi worker đang chạy | Không được giả định worker bị interrupt |
| Task ném exception | get ném ExecutionException và cause giữ lỗi gốc |
| Luồng caller bị interrupt trong get | Bắt InterruptedException; xử lý interrupt policy |
| Future đã canceled | isDone và isCancelled đều true; get ném CancellationException |
| CompletableFuture orTimeout hết hạn | Chính CF hoàn tất bất thường; get/join có kiểu bọc phù hợp |
| Hai caller cùng chờ một task | Timeout của một caller không mặc nhiên phá hủy kết quả chung |

Trong ví dụ chính, latch thứ nhất chứng minh task có thể hoàn tất sau một TimeoutException. Latch **started** giúp bảo đảm worker thứ hai đang chạy trước khi main gọi cancel. Latch **exited** bảo đảm main chờ cho đến khi worker thực sự đi hết finally, thay vì nhầm trạng thái Future canceled với việc worker đã dừng.

Không cần cố tình tạo race chỉ bằng timing ngẫu nhiên; nếu muốn kiểm thử race giữa complete và cancel, hãy điều khiển lịch chạy bằng barrier và xác minh các kết quả được hợp đồng cho phép, không hard-code một thứ tự scheduler không được bảo đảm.

## 15. Những lỗi thường gặp

**Hiểu nhầm “timeout nghĩa là task đã dừng”.** Future.get(timeout) chỉ giới hạn thời gian đợi. Nếu không cancel hoặc task chưa tự hoàn tất, worker có thể còn chạy.

**Gọi cancel(true) rồi coi side effect đã rollback.** Interrupt là tín hiệu hợp tác; kết quả từ hệ ngoài có thể đã commit. Cần chính sách dữ liệu/transaction riêng.

**Dùng isDone() như cờ thành công.** isDone còn true với cancellation và lỗi. Cần gọi get hoặc quản lý trạng thái kết quả đầy đủ.

**Nhầm CancellationException với InterruptedException.** CancellationException báo trạng thái hủy của Future; InterruptedException báo thread đang chờ hoặc worker bị interrupt trong API có hỗ trợ.

**Nuốt InterruptedException.** Khi không ném tiếp, thông thường cần khôi phục interrupt flag và kết thúc hoặc chuyển giao chính sách hủy cho tầng trên.

**Bắt TimeoutException rồi retry việc ghi ngay lập tức.** Task cũ có thể vẫn đang chạy hoặc external request đã hoàn tất. Retry không idempotent có thể lặp side effect.

**Cho rằng CompletableFuture.cancel(true) buộc supplier dừng.** CompletableFuture không dùng tham số này để interrupt xử lý theo cách FutureTask thông thường. Phải quản lý computation bên dưới riêng.

**Đặt orTimeout rồi mong get() ném trực tiếp TimeoutException.** Khi CF đã hoàn tất bất thường với TimeoutException, get thường bọc trong ExecutionException; join thường qua CompletionException. Đây là đường khác get(timeout) hết hạn chờ.

**Tạo nhiều deadline độc lập cho cùng request.** Reset timeout sau mỗi lần chờ/retry có thể khiến tổng thời gian vượt ngân sách.

**Tắt executor chia sẻ khi một task chậm.** Quản lý vòng đời executor theo quyền sở hữu; hủy task cụ thể nếu cần và đóng executor vào ranh giới phù hợp.

## 16. Bảng kiểm trước khi xử lý timeout/cancellation

1. Đang gọi API nào: Future.get(timeout), CompletableFuture.orTimeout hay một API trả boolean khi hết hạn?
2. Timeout chỉ dừng chờ hay còn chủ động chuyển trạng thái kết quả?
3. Nếu timeout xảy ra, task vẫn chạy có gây vấn đề nghiệp vụ hoặc tài nguyên không?
4. Ai sở hữu Future và có quyền cancel khi nhiều caller dùng chung?
5. Nếu cancel(true), worker có kiểm tra interrupt hoặc gọi API có thể bị interrupt không?
6. Nếu cancel trả false vì task vừa hoàn tất, có cần lấy và xử lý kết quả cuối không?
7. Có phân biệt **CancellationException**, **InterruptedException** và **ExecutionException.getCause()** không?
8. Deadline có tính cho toàn bộ chuỗi nghiệp vụ, kể cả retry và cleanup, không?
9. Nếu đã có I/O hoặc side effect, làm sao biết outcome và tránh retry trùng?
10. Test có xác minh timeout không tự cancel, cancel không đồng nghĩa worker đã dừng, và các đường lỗi được quan sát đúng không?

**Ghi nhớ:** **TimeoutException** trong **Future.get(timeout)** nói rằng *lượt chờ đã quá hạn*; nó không hủy task. **CancellationException** cho biết *Future đã bị hủy*, nhưng trạng thái Future không tự chứng minh mọi computation và tác dụng phụ đã dừng. Xử lý đúng cần kết hợp deadline rõ ràng, quyền cancel phù hợp, khả năng đáp ứng interrupt của worker, phân biệt các loại exception và cơ chế an toàn trạng thái khi thao tác có side effect.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-009">Lỗi trong tác vụ định kỳ và ảnh hưởng tới các lần chạy tiếp theo</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi trong tác vụ định kỳ và ảnh hưởng tới các lần chạy tiếp theo (Java)

## 1. Tình huống: một lần chạy lỗi, lịch chạy còn tiếp tục không?

Một tác vụ định kỳ (*periodic task*) được cấu hình chạy lặp lại sau một khoảng thời gian, chẳng hạn làm mới cache, dọn dữ liệu tạm, đồng bộ trạng thái hoặc gửi báo cáo. Nếu ở một lần thực thi tác vụ ném exception, điều gì xảy ra với các lần tiếp theo?

Trong Java, câu trả lời **phụ thuộc API lập lịch và việc exception có thoát khỏi thân tác vụ hay không**. Đặc biệt, với `ScheduledExecutorService.scheduleAtFixedRate(...)` và `scheduleWithFixedDelay(...)`, hợp đồng Java quy định:

> Nếu **bất kỳ lần thực thi** tác vụ định kỳ nào gặp exception **không được xử lý và thoát khỏi tác vụ**, các lần thực thi tiếp theo **bị ngăn lại** (*subsequent executions are suppressed*).

Đây là lý do một job đang chạy đều đặn có thể **im lặng ngừng hoạt động sau một lỗi duy nhất**, dù `ScheduledExecutorService` còn sống và các công việc khác vẫn tiếp tục. Caller không tự bắt được lỗi chỉ bằng một `try/catch` bọc lời gọi lập lịch; lỗi phát sinh về sau trên thread của executor.

Bài này làm rõ cơ chế, điểm khác nhau giữa hai kiểu lập lịch, cách quan sát và xử lý exception, khi nào tiếp tục/hủy tác vụ, cũng như các giới hạn về trạng thái, retry và concurrency.

## 2. Phân biệt ba sự kiện riêng biệt

Khi xét một tác vụ định kỳ, cần tách biệt:

1. **Lập lịch thành công:** lệnh `scheduleAtFixedRate` hoặc `scheduleWithFixedDelay` trả về một `ScheduledFuture<?>`. Điều này chỉ xác nhận yêu cầu đã được nhận, **không** bảo đảm mọi lượt tương lai sẽ thành công.
2. **Một lượt chạy thành công hoặc lỗi:** executor thực thi `Runnable.run()` trên một worker thread. Nếu `run()` trả về bình thường, lịch có thể tiếp tục. Nếu một `Throwable` thoát ra khỏi lượt chạy, future của lịch sẽ chuyển sang trạng thái hoàn tất do lỗi theo hợp đồng các phương thức định kỳ và những lần kế tiếp bị ngăn.
3. **Người gọi quan sát kết quả:** `ScheduledFuture.isDone()`, `isCancelled()`, `get()`, log/metric hoặc một kênh báo lỗi riêng mới giúp bộ phận giám sát biết tác vụ đang sống hay đã dừng.

Không nên suy ra lịch còn hoạt động chỉ vì gọi `scheduleAtFixedRate(...)` không ném lỗi ngay lúc tạo lịch. Cũng không nên suy ra toàn bộ scheduler đã chết chỉ vì **một lịch** dừng do exception.

## 3. Hai kiểu lập lịch định kỳ quan trọng

### `scheduleAtFixedRate`: chu kỳ tính theo thời điểm dự kiến

Chữ ký:

```java
ScheduledFuture<?> scheduleAtFixedRate(
    Runnable command,
    long initialDelay,
    long period,
    TimeUnit unit);
```

Đây là **chữ ký API để đọc hiểu**, không phải đoạn mã độc lập. Ví dụ `initialDelay = 0`, `period = 1 SECOND`: lịch dự kiến tại các mốc khoảng `t0, t0+1s, t0+2s, ...`, không phải luôn đợi đúng một giây sau khi lượt trước kết thúc.

Nếu một lượt chạy lâu hơn chu kỳ, lần kế tiếp có thể bắt đầu trễ và có thể chạy gần như ngay sau lượt trước để bắt kịp mốc dự kiến. **Các lần thực thi của cùng một tác vụ định kỳ không chạy chồng lấn nhau**, dù executor có nhiều worker; các tác vụ định kỳ **khác nhau** vẫn có thể chạy song song. Theo hợp đồng của `ScheduledThreadPoolExecutor`, các hiệu ứng của lượt chạy trước *happens-before* lượt chạy tiếp theo của cùng tác vụ định kỳ.

**Khi exception thoát khỏi lượt chạy:** những lần thực thi tiếp theo của **chính lịch đó** sẽ bị ngăn lại, bất kể các mốc định kỳ khác đã tới.

### `scheduleWithFixedDelay`: độ trễ tính sau khi lượt trước kết thúc

Chữ ký:

```java
ScheduledFuture<?> scheduleWithFixedDelay(
    Runnable command,
    long initialDelay,
    long delay,
    TimeUnit unit);
```

Ví dụ công việc chạy trong `300 ms` và `delay = 1 SECOND` thì lần sau được phép bắt đầu sau khoảng một giây kể từ khi lần trước **kết thúc**, tức khoảng `1,3 giây` từ lúc lần trước bắt đầu (bỏ qua trễ lên lịch). Nếu công việc tốn `3 giây`, độ dài giữa hai lần bắt đầu có thể khoảng `4 giây`.

**Khi exception thoát khỏi lượt chạy:** những lần thực thi tiếp theo cũng bị ngăn. Đây là điểm **giống nhau** cốt lõi của cả fixed-rate và fixed-delay.

| Thuộc tính | `scheduleAtFixedRate` | `scheduleWithFixedDelay` |
| --- | --- | --- |
| Thời điểm lần sau | Theo các mốc dự kiến cách nhau `period` | Sau khi lượt trước kết thúc, đợi thêm `delay` |
| Lượt xử lý lâu hơn chu kỳ | Lượt kế tiếp có thể chạy muộn, rồi bắt đầu gần như ngay | Luôn có khoảng chờ `delay` giữa lúc kết thúc và lần bắt đầu kế tiếp |
| Cùng một tác vụ chạy chồng lấn? | Không | Không |
| Exception thoát ra từ một lượt | Ngăn những lần tiếp theo | Ngăn những lần tiếp theo |
| Hợp với | Nhịp lấy mẫu/làm việc theo tần suất tương đối cố định | Polling, dọn dẹp hoặc tác vụ muốn nghỉ sau mỗi lần hoàn tất |

Hai cơ chế trên dùng **thời gian tương đối** và không tự có quy tắc cron, múi giờ hay xử lý giờ mùa hè. Nếu yêu cầu “8 giờ sáng mỗi ngày theo lịch địa phương”, cần thiết kế lịch theo clock/calendar với API thích hợp.

Các tham số `period` và `delay` dành cho tác vụ định kỳ phải **lớn hơn 0**; đầu vào không hợp lệ có thể gây `IllegalArgumentException` ngay trong **luồng gọi lập lịch**, khác với lỗi trong một lần `run()` chạy về sau.

## 4. Vì sao exception lại dừng lịch?

Với `ScheduledThreadPoolExecutor` (triển khai thông dụng của `ScheduledExecutorService`), mỗi lịch định kỳ được quản lý bằng một đối tượng future gắn với trạng thái thực thi. Khi `Runnable` ném exception không được bắt ở bên trong:

- Lượt hiện tại kết thúc bất thường.
- Future của lịch được đánh dấu **hoàn tất do exception** (*exceptional completion*).
- Executor **không đưa cùng lịch đó vào hàng đợi cho lượt tiếp theo**.
- Người gọi có thể lấy nguyên nhân thông qua `ScheduledFuture.get()`; vì lỗi đã làm future hoàn tất, `get()` ném `ExecutionException` có `getCause()` là nguyên nhân của lượt chạy thất bại.

Với lịch định kỳ không kết thúc và đang hoạt động bình thường, `future.get()` **không trả về giá trị thành công sau mỗi lượt**. Nó có thể chờ mãi cho đến khi lịch bị hủy hoặc gặp lỗi (hoặc thao tác chờ bị interrupt). Do đó, tuyệt đối không dùng `get()` không có timeout ở thread quan trọng chỉ để “xem lần vừa chạy có tốt không”.

`Future.isDone() == true` có thể do **lỗi** hoặc **đã hủy**, nên cần kiểm tra `isCancelled()` và nguyên nhân khi thích hợp. `isDone() == false` không đủ để bảo đảm tác vụ đang hoạt động lành mạnh: lịch có thể chưa tới lượt, đang chạy lâu, nghẽn worker, hoặc phụ thuộc bị treo.

## 5. Ví dụ Java 17+ chạy được: một lịch dừng, lịch kia tiếp tục

Chương trình dưới đây tạo scheduler có hai worker và hai tác vụ định kỳ:

- **Tác vụ A:** lần đầu thành công, lần thứ hai ném `IllegalStateException` ra khỏi `Runnable`. Lịch A dừng; `get()` báo `ExecutionException`.
- **Tác vụ B:** lần đầu giả lập lỗi nhưng **bắt và ghi nhận lỗi bên trong**; nó hoàn tất bình thường và tiếp tục ít nhất ba lượt, sau đó main hủy để kết thúc ví dụ.
- **Tác vụ độc lập:** được lập lịch sau lỗi của A để chứng minh scheduler vẫn thực thi việc khác.

Tạo file `PeriodicTaskFailureDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PeriodicTaskFailureDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(2);
        AtomicInteger failedRuns = new AtomicInteger();
        AtomicInteger resilientRuns = new AtomicInteger();
        AtomicInteger handledErrors = new AtomicInteger();
        CountDownLatch thirdRun = new CountDownLatch(1);
        ScheduledFuture<?> resilient = null;

        try {
            ScheduledFuture<?> failed = scheduler.scheduleAtFixedRate(() -> {
                int n = failedRuns.incrementAndGet();
                if (n == 2) {
                    throw new IllegalStateException("second execution failed");
                }
            }, 0, 20, TimeUnit.MILLISECONDS);

            try {
                failed.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                System.out.println("FAILED: "
                        + ex.getCause().getClass().getSimpleName());
            }
            System.out.println("FAILED RUNS: " + failedRuns.get());
            System.out.println("FAILED FUTURE DONE: " + failed.isDone());

            resilient = scheduler.scheduleWithFixedDelay(() -> {
                int n = resilientRuns.incrementAndGet();
                try {
                    if (n == 1) {
                        throw new IllegalArgumentException("recoverable example");
                    }
                } catch (IllegalArgumentException ex) {
                    handledErrors.incrementAndGet();
                } finally {
                    if (n >= 3) {
                        thirdRun.countDown();
                    }
                }
            }, 0, 20, TimeUnit.MILLISECONDS);

            if (!thirdRun.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("resilient task did not run");
            }
            resilient.cancel(false);

            System.out.println("HANDLED ERRORS: " + handledErrors.get());
            System.out.println("RECOVERED AND KEPT RUNNING: "
                    + (resilientRuns.get() >= 3));

            ScheduledFuture<String> independent = scheduler.schedule(
                    () -> "still active", 0, TimeUnit.MILLISECONDS);
            System.out.println("OTHER TASK: "
                    + independent.get(5, TimeUnit.SECONDS));
        } finally {
            if (resilient != null) {
                resilient.cancel(false);
            }
            scheduler.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Scheduler did not terminate in time");
            }
        }
    }
}
```

Lệnh chạy trong thư mục chứa file, với JDK 17 trở lên:

```bash
javac -encoding UTF-8 PeriodicTaskFailureDemo.java
java PeriodicTaskFailureDemo
```

Standard output:

```text
FAILED: IllegalStateException
FAILED RUNS: 2
FAILED FUTURE DONE: true
HANDLED ERRORS: 1
RECOVERED AND KEPT RUNNING: true
OTHER TASK: still active
```

Diễn giải kết quả:

1. `failedRuns` bằng **2** vì exception thoát ra ở lượt thứ hai; executor không lên lịch lượt ba.
2. `failed.get(...)` ném `ExecutionException`. Chương trình lấy `getCause()` để thấy `IllegalStateException` thực tế.
3. `failed.isDone()` trả `true` vì lịch **đã hoàn tất do lỗi**; điều này không có nghĩa tác vụ “thành công”.
4. Lịch `resilient` tự bắt `IllegalArgumentException` rồi kết thúc lượt đầu bình thường. Các lượt tiếp theo vẫn được chạy; `CountDownLatch` giúp ví dụ xác nhận đạt ba lượt mà không phụ thuộc vào `Thread.sleep()`.
5. `resilient.cancel(false)` ngăn lên lịch những lượt sau. Có thể một lượt đã bắt đầu vẫn tiếp tục do `false` không yêu cầu interrupt, nhưng chương trình chỉ kiểm tra điều kiện “đã chạy ít nhất ba lượt”, nên output không phụ thuộc số lần chính xác tại lúc hủy.
6. Tác vụ `independent` vẫn nhận kết quả `still active`, cho thấy lỗi của lịch A **không tự làm scheduler ngừng vận hành**.

`AtomicInteger` giữ bộ đếm an toàn khi đọc từ main và cập nhật trên worker. `CountDownLatch` đồng bộ điều kiện hoàn thành; `get(timeout)` và `await(timeout)` tránh chờ vô hạn trong demo. `shutdownNow()` là yêu cầu dừng và interrupt các worker còn hoạt động; nó không bảo đảm một tác vụ không hợp tác sẽ dừng ngay. Phần `finally` dọn scheduler để chương trình thoát.

Ví dụ chủ động dùng **20 ms** để minh họa nhanh, không coi scheduler là bộ đếm thời gian chính xác đến mili giây. Trên máy quá tải, timeout có thể hết trước khi các tác vụ được chạy; đó là tình huống chờ quá hạn cần xử lý trong ứng dụng thực tế, không làm thay đổi hợp đồng suppression khi task ném lỗi.

## 6. Lỗi thoát ra và lỗi đã được xử lý trong tác vụ khác nhau thế nào?

Điểm quyết định lịch có tiếp tục do lỗi nghiệp vụ hay không là **giá trị trả về của `Runnable.run()`**:

### Exception thoát ra ngoài

```java
Runnable job = () -> {
    loadData();
    saveData(); // Nếu ném RuntimeException không được catch...
};
scheduler.scheduleAtFixedRate(job, 0, 1, TimeUnit.MINUTES);
```

Đây là mảnh minh họa giả định có `ScheduledExecutorService scheduler` và các hàm `loadData()`, `saveData()`. Nếu một exception thoát khỏi `job.run()`, hợp đồng tác vụ định kỳ yêu cầu **ngăn những lần chạy tiếp theo của lịch này**.

### Bắt lỗi dự kiến rồi hoàn tất lượt chạy bình thường

```java
Runnable job = () -> {
    try {
        refreshCache();
    } catch (RecoverableBackendException ex) {
        recordFailure(ex);
        // Lượt này đã thất bại nhưng policy cho phép thử lại ở kỳ sau.
    }
};
scheduler.scheduleWithFixedDelay(job, 0, 30, TimeUnit.SECONDS);
```

Đây cũng là **mảnh minh họa**, với `RecoverableBackendException`, `refreshCache()` và `recordFailure()` là thành phần ứng dụng phải tự định nghĩa. Nếu nhánh `catch` thực thi xong và `run()` trả về bình thường, lịch **có thể tiếp tục** ở kỳ sau. Hành vi đó là quyết định ứng dụng: lỗi tạm thời có thể được ghi nhận và bỏ qua cho lần này, còn lỗi không thể phục hồi nên được báo ra và có thể chủ đích dừng lịch.

Cần đặc biệt chú ý: chính `recordFailure(ex)` cũng có thể ném exception. Nếu logging, metric hoặc callback báo lỗi thất bại và exception mới thoát ra ngoài `run()`, lịch vẫn bị ngăn. Hãy chọn đường giám sát bền vững và xử lý lỗi của đường giám sát theo mức độ quan trọng.

**Không nên bọc toàn bộ tác vụ bằng `catch (Throwable)` rồi bỏ qua mọi lỗi.** `Throwable` gồm `Error` như `OutOfMemoryError`, là tình trạng thường không có cơ sở để tiếp tục sử dụng tài nguyên bình thường. Chỉ bắt loại lỗi mà ứng dụng có chính sách xử lý và khả năng giữ bất biến. Với `InterruptedException`, cần tuân thủ chính sách hủy và trạng thái interrupt riêng.

## 7. Lịch đã lỗi có tự khởi động lại không?

Không. Với `scheduleAtFixedRate` và `scheduleWithFixedDelay`, khi một lượt ném exception thoát ra, **chính đối tượng `ScheduledFuture` của lịch đó chuyển sang trạng thái kết thúc do lỗi**. Executor không tự retry lượt bị lỗi và cũng không tự dựng lịch mới.

Nếu yêu cầu khởi động lại sau một lỗi, ứng dụng phải có chiến lược rõ ràng:

- Cho phép một số lỗi tạm thời **được bắt trong task**, ghi log/metric và để lần chạy theo chu kỳ tiếp theo xử lý lại.
- Nếu cần thay đổi lịch theo số lần thất bại, sử dụng cơ chế **lập lịch lại có điều kiện** (một lần chạy `schedule(...)` rồi chủ động lên lịch lần tiếp) với backoff/jitter thay cho chu kỳ cứng.
- Nếu task đã dừng do lỗi nghiêm trọng, chuyển trạng thái sang `FAILED` và cần hành động vận hành hoặc thay đổi cấu hình; không tự động gọi lịch mới vô hạn.
- Có giám sát tìm những future đã `isDone()` bất thường, tránh tình trạng tác vụ thiết yếu ngừng hoạt động mà không ai biết.

Khi tự lập lịch lại, cần chống trùng: chỉ một scheduler/controller sở hữu lịch, hủy/lưu tham chiếu lịch cũ và tránh việc nhiều callback cùng tạo lịch mới sau một sự cố. Nếu nhiều instance ứng dụng cùng chạy, cần nghĩ đến cơ chế leader election, distributed lock hoặc idempotency phù hợp; `ScheduledExecutorService` tự nó không điều phối các JVM khác nhau.

## 8. Kiểm tra trạng thái và đọc nguyên nhân qua `ScheduledFuture`

Đối tượng `ScheduledFuture<?>` là handle để quản lý và quan sát vòng đời lịch. Một số thuộc tính:

| Lời gọi | Ý nghĩa |
| --- | --- |
| `future.isDone()` | Lịch đã hoàn tất do hủy, lỗi hoặc hình thức kết thúc khác; không đồng nghĩa thành công |
| `future.isCancelled()` | Lịch đã bị hủy |
| `future.cancel(false)` | Yêu cầu hủy những lần chưa chạy; không yêu cầu interrupt lần đang thực thi |
| `future.cancel(true)` | Yêu cầu hủy và **có thể interrupt** lần đang chạy; task phải hợp tác |
| `future.get()` | Với lịch định kỳ khỏe mạnh, thường **không bao giờ trả về bình thường**; khi lỗi ném `ExecutionException`, khi hủy ném `CancellationException` |
| `future.get(timeout, unit)` | Có giới hạn thời gian chờ; `TimeoutException` có thể chỉ phản ánh lịch vẫn đang hoạt động |

Mẫu quan sát *sau khi đã biết future kết thúc*:

```java
if (future.isDone()) {
    if (future.isCancelled()) {
        reportCancelled();
    } else {
        try {
            future.get();
        } catch (ExecutionException ex) {
            reportFailure(ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Đây là mảnh code trong một phương thức có `ScheduledFuture<?> future`, đã import các lớp `java.util.concurrent.*` phù hợp và có các hàm `reportCancelled()`, `reportFailure(Throwable)` do ứng dụng định nghĩa. Cần bảo vệ race do trạng thái hủy và thay đổi kết quả nếu chính hệ thống có nhiều luồng quản lý handle. Nếu `get()` bị hủy sau kiểm tra, nó có thể ném `CancellationException`; ví dụ đã bỏ nhánh đó cho ngắn, còn mã production nên xử lý đầy đủ.

Không nên gọi `future.get()` bên trong **chính một lượt tác vụ định kỳ trên cùng lịch**, vì future chưa kết thúc và có thể tự chờ mình vô thời hạn. Nếu cần xử lý lỗi của từng lượt, hãy ghi nhận nó ngay trong wrapper và dùng hệ thống giám sát riêng.

### `UncaughtExceptionHandler` có nhìn thấy lỗi này không?

Với các task chạy qua `ScheduledThreadPoolExecutor`, executor thường **giữ lỗi vào future**. Vì vậy `Thread.UncaughtExceptionHandler` không phải kênh tin cậy để quan sát mọi thất bại của tác vụ đã lập lịch; lỗi có thể không xuất hiện như một uncaught exception trên worker thread. Đường ổn định là quản lý `ScheduledFuture`, wrapper ghi nhận kết quả từng lượt hoặc kênh giám sát do ứng dụng định nghĩa.

Nếu xây subclass `ScheduledThreadPoolExecutor` và override `afterExecute(Runnable, Throwable)`, lưu ý tham số `Throwable` thứ hai có thể là `null` ngay cả khi task thất bại, do lỗi nằm trong `Future`. Không nên gọi `get()` không điều kiện trên các periodic future chưa `isDone()`; hành động đó có thể block worker của executor.

## 9. Vì sao lỗi một task không đồng nghĩa cả scheduler bị hỏng?

`ScheduledExecutorService` là bộ quản lý **nhiều công việc độc lập**. Một lịch định kỳ lỗi và bị suppression là trạng thái của **handle lịch đó**, không phải mệnh lệnh chung `shutdown()`. Ví dụ có hai job:

- Job A làm mới tỷ giá và gặp một `IllegalStateException` không được bắt;
- Job B dọn dẹp cache bình thường.

Nếu scheduler còn hoạt động và tài nguyên đáp ứng, Job B vẫn được phép thực thi theo lịch của nó. Không được suy luận ngược rằng scheduler hoàn toàn khỏe chỉ vì Job B vẫn chạy: các worker có thể nghẽn, queue tăng hoặc job A đã chết.

Dịch vụ giám sát cần theo dõi **từng lịch** và **pool** riêng. Một số tín hiệu hữu ích:

- Thời điểm gần nhất một lượt bắt đầu/kết thúc;
- Thời điểm thành công cuối cùng;
- Số lỗi liên tiếp, loại lỗi gần nhất và correlation ID;
- `ScheduledFuture.isDone()`, `isCancelled()` cho lịch được kỳ vọng chạy lâu dài;
- Thời lượng một lượt, độ trễ so với lịch dự kiến, worker saturation;
- Số task đang đợi, trạng thái `isShutdown()`/`isTerminated()` của executor.

Không nên đặt tên trạng thái “HEALTHY” chỉ dựa trên việc tiến trình JVM còn sống; một scheduler có thể tồn tại lâu trong khi tất cả periodic job quan trọng đã dừng sau exception.

## 10. So sánh với `java.util.Timer` và `TimerTask`

Java cũ có `java.util.Timer`, hoạt động theo mô hình một thread chạy các `TimerTask` tuần tự trong một Timer. Khi một `TimerTask.run()` ném **unchecked exception** không được xử lý, thread thực thi của `Timer` có thể bị kết thúc; timer không thể tiếp tục lịch như trước và những task khác của **cùng Timer** cũng bị ảnh hưởng.

Điểm này khác đáng kể với `ScheduledThreadPoolExecutor`:

| Trường hợp | `java.util.Timer` | `ScheduledExecutorService` với lịch định kỳ |
| --- | --- | --- |
| Thread thực thi | Một thread cho các task của Timer | Pool gồm một hoặc nhiều worker |
| Lỗi unchecked thoát khỏi một tác vụ | Có thể kết thúc thread Timer, ảnh hưởng toàn bộ các tác vụ cùng Timer | Suppress các lượt sau của **lịch bị lỗi**, không tự dừng các lịch khác |
| Kênh lấy lỗi tác vụ | Không trả handle kiểu `Future` cho từng lượt | `ScheduledFuture` có thể hoàn tất exceptionally |
| Tác vụ lâu | Chặn các task khác trong cùng Timer | Pool có thể cho task khác chạy trên worker khác, tùy kích thước |
| Đề xuất cho mã mới | Thường không phải lựa chọn ưu tiên | Thường phù hợp hơn vì quản lý pool, hủy và future |

Ví dụ `Timer` (mảnh mã trong phương thức; có import `java.util.Timer` và `TimerTask`):

```java
Timer timer = new Timer("legacy-timer", true);
timer.scheduleAtFixedRate(new TimerTask() {
    @Override
    public void run() {
        throw new IllegalStateException("legacy timer task failed");
    }
}, 0L, 1000L);
```

Đây là ví dụ **để phân tích hậu quả**, không nên dùng làm mẫu xử lý lỗi production. Với `Timer`, một exception runtime không được bắt trong task có thể giết thread Timer và khiến những task đã lập lịch sau đó không còn chạy. Nếu sử dụng Timer trong mã cũ, cần xử lý lỗi, quản lý vòng đời và cân nhắc chuyển sang `ScheduledExecutorService` khi phù hợp.

Trong cả hai mô hình, **bắt lỗi có chọn lọc** trong thân tác vụ có thể giúp duy trì việc lập lịch ở lượt sau, nhưng không bảo đảm công việc thật đã hoàn thành đúng.

## 11. Checked exception, unchecked exception và `Error`

`ScheduledExecutorService` nhận `Runnable` cho tác vụ định kỳ. `Runnable.run()` **không khai báo `throws` cho checked exception**. Do đó, phương thức dùng I/O ném `IOException` checked phải xử lý nó trong `run()`:

```java
Runnable job = () -> {
    try {
        refreshFromRemote(); // Có thể ném IOException
    } catch (IOException ex) {
        logTransientFailure(ex);
    }
};
```

Đây là mảnh minh họa cần `java.io.IOException` và các phương thức ứng dụng `refreshFromRemote()`, `logTransientFailure(...)`. Nếu giữ `IOException` trong nhánh `catch` và kết thúc `run()` bình thường, lịch có thể tiếp tục. Nếu cố tình bọc lại bằng `UncheckedIOException` hay `IllegalStateException` **rồi ném ra ngoài**, lịch sẽ bị suppression như các runtime exception khác.

`Error` cũng thuộc `Throwable`. Hợp đồng Java về tác vụ định kỳ dùng ngôn ngữ “execution encounters an exception”; triển khai `ScheduledThreadPoolExecutor` lưu cả thất bại nghiêm trọng từ việc thực thi. Không nên dựa vào việc cố catch mọi `Throwable` để “giữ job còn sống” khi JVM có thể đã thiếu tài nguyên hoặc trạng thái không còn đáng tin.

Tách chính sách:

- **Lỗi có thể phục hồi:** ví dụ một request mạng tạm mất, dữ liệu của lượt này có thể bỏ qua, giữ cache cũ hợp lệ. Ghi nhận rồi kết thúc bình thường nếu đã bảo toàn trạng thái.
- **Lỗi nghiệp vụ/validation:** lỗi cấu hình, không thể giải mã payload, invariant bị vi phạm. Thường phải dừng hoặc cách ly job và cảnh báo, thay vì chạy liên tục làm hỏng dữ liệu.
- **Lỗi hủy/interruption:** có thể cần dừng tác vụ, giải phóng tài nguyên và khôi phục trạng thái interrupt. Không phải mọi `InterruptedException` đều là lỗi tạm thời nên được bỏ qua.
- **Lỗi nghiêm trọng cấp runtime:** báo về cơ chế giám sát hoặc để executor/runtime xử lý theo chính sách vận hành, tránh che bằng vòng lặp retry vô hạn.

## 12. `InterruptedException` và việc hủy lịch

Một tác vụ định kỳ có thể block trên I/O, khóa hoặc các lệnh `sleep`/`wait`. Khi luồng worker bị interrupt, các lời gọi blocking có thể ném `InterruptedException`. Vì `Runnable.run()` không được khai báo checked exception, task cần quyết định xử lý.

Một mảnh xử lý trong `Runnable`:

```java
try {
    waitForWork();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
```

Đây là mảnh code trong phương thức trả về `void`, với `waitForWork()` do ứng dụng định nghĩa. Nó bảo toàn tín hiệu interrupt để code bên ngoài quan sát nếu thích hợp.

**Bẫy quan trọng:** nếu task chỉ bắt `InterruptedException` rồi **return bình thường** trong khi `ScheduledFuture` của lịch **chưa bị hủy**, lịch định kỳ có thể vẫn được lên lịch cho lượt sau. `return` chỉ kết thúc **lượt hiện tại**, không phải lệnh hủy hợp đồng định kỳ. Muốn hủy toàn bộ lịch, cần gọi `future.cancel(...)` hoặc shutdown/cơ chế kiểm soát lifecycle phù hợp.

`cancel(true)` chỉ yêu cầu interrupt nếu đang chạy và là *best effort*. Task làm I/O không phản hồi interrupt có thể vẫn tiếp tục side effect. `cancel(false)` không yêu cầu interrupt công việc đang thực hiện. Cả hai không tự hoàn tác ghi file, update DB hoặc HTTP request đã xảy ra.

Nếu một task được đánh dấu `isCancelled()`, `get()` ném `CancellationException` thay vì trả báo cáo kết quả thành công; cancellation có ngữ nghĩa khác với exceptional completion do lỗi trong `run()`.

## 13. Tác vụ định kỳ có async bên trong: một ranh giới lỗi nữa

Ví dụ một `Runnable` chỉ **khởi tạo** thao tác bất đồng bộ rồi return:

```java
scheduler.scheduleAtFixedRate(() -> {
    CompletableFuture<Void> action = service.refreshAsync();
    action.whenComplete((value, error) -> {
        if (error != null) {
            reportFailure(error);
        }
    });
}, 0, 1, TimeUnit.MINUTES);
```

Đây là mảnh code có `ScheduledExecutorService scheduler`, đối tượng ứng dụng `service` trả `CompletableFuture<Void>` và hàm `reportFailure(Throwable)`.

Nếu `refreshAsync()` trả về stage bình thường rồi lỗi phát sinh **về sau** khi stage đang chạy, lỗi đó **không thoát trực tiếp khỏi lượt `Runnable.run()`** đã return. Vì vậy lịch ngoài có thể **tiếp tục chạy các lượt sau**, trong khi stage trước đó thất bại. Nếu callback `whenComplete` tự ném exception, stage mà nó trả về có thể hoàn tất exceptionally; nó cũng không tự làm exception nhảy ngược vào stack của `Runnable` đã kết thúc.

Một rủi ro khác là **overlap bất đồng bộ**: executor bảo đảm không chạy chồng lấn **các lượt `Runnable.run()`** của cùng lịch, nhưng khi mỗi lượt chỉ phát một future rồi return, các **công việc async do những lượt đó khởi tạo** vẫn có thể chạy chồng lấn trong hệ thống khác.

Để xử lý, chọn hợp đồng rõ ràng:

1. Đặt giới hạn concurrency/in-flight, ví dụ semaphore hoặc state flag và chắc chắn giải phóng ở mọi đường completion.
2. Dùng scheduling của lượt tiếp theo **sau khi stage trước đã hoàn tất** nếu thật sự muốn không overlap.
3. Theo dõi từng future, ghi lỗi, trạng thái và yêu cầu hủy.
4. Nếu buộc phải chờ `join()`/`get()` trong `Runnable`, biết rằng việc chờ có thể block worker, phát sinh exception wrapper, và cần timeout/cancel phù hợp.

Tương tự, exception xảy ra trong callback trên thread khác không tự lan đến `catch` quanh câu `scheduleAtFixedRate` của caller. Phải truyền kết quả qua future hoặc kênh quan sát đã thiết kế.

## 14. Task chạy một lần khác task định kỳ

`scheduler.schedule(Runnable, delay, unit)` hoặc `schedule(Callable, delay, unit)` tạo tác vụ **một lần**. Nếu tác vụ lỗi, future tương ứng hoàn tất exceptionally nhưng không tồn tại “lần chạy tiếp theo của cùng lịch” để bị suppression. Caller có thể `get()` để lấy kết quả hoặc exception.

Ngược lại, `scheduleAtFixedRate` và `scheduleWithFixedDelay` định nghĩa một chuỗi lặp lại; khi có exception thoát ra thì **chuỗi đó kết thúc**. Đừng nhầm việc tự gọi `schedule(...)` liên tục từ callback với hợp đồng của hai API periodic: hệ thống tự lập lịch lại phải quản lý lỗi, hủy và các lịch trùng theo logic riêng.

Tương tự, `executor.execute(Runnable)` không trả future còn `executor.submit(...)` trả `Future`. Đường ghi nhận ngoại lệ của executor có thể khác nhau giữa hai API. Khi phân tích một hệ thống hiện có, trước hết xác định đúng **phương thức đã dùng**, thay vì áp dụng một câu trả lời chung cho “mọi lỗi ở background thread”.

## 15. Sau exception, dữ liệu đã cập nhật có bị rollback không?

Không. Suppression chỉ dừng **lịch chạy tương lai**. Nó không tự hoàn tác những thay đổi đã làm trong lượt bị lỗi.

Ví dụ mảnh mã:

```java
Runnable job = () -> {
    updateDatabase();       // Có thể đã commit
    sendNotification();     // Có thể lỗi sau khi update
};
```

Đây là minh họa có các hàm ứng dụng `updateDatabase()` và `sendNotification()`. Nếu thao tác DB đã commit rồi `sendNotification()` ném exception, lịch định kỳ có thể bị suppression, **nhưng dữ liệu DB đã commit vẫn còn**. Khi vận hành thử lại, một bản ghi có thể đã được xử lý hoặc thông báo có thể đã được gửi dù không nhận ACK.

Đối với job có side effect:

- Dùng transaction đúng phạm vi cho những thay đổi cùng tài nguyên.
- Phân biệt lỗi trước commit, sau commit và kết quả chưa xác định khi timeout.
- Dùng idempotency key/correlation ID để tránh xử lý trùng khi retry hoặc reschedule.
- Với DB và message, cân nhắc outbox nếu nghiệp vụ cần lưu ý định phát sự kiện cùng transaction.
- Dùng trạng thái tiến độ, checkpoint hoặc đối chiếu nghiệp vụ nếu một job xử lý nhiều bản ghi.

Việc “bắt lỗi cho lịch tiếp tục” là lựa chọn về **lịch chạy**, không phải bảo đảm trạng thái nghiệp vụ đã an toàn. Nếu invariant đã bị phá, tiếp tục ở kỳ sau có thể làm lỗi nghiêm trọng hơn.

## 16. Thiết kế wrapper xử lý lỗi có chính sách

Một wrapper hợp lý nên tách đường lỗi có thể tiếp tục với đường lỗi cần dừng:

```java
Runnable guarded = () -> {
    long started = System.nanoTime();
    try {
        performOneSafeIteration();
        recordSuccess(System.nanoTime() - started);
    } catch (TransientDependencyException ex) {
        recordRecoverableFailure(ex);
        // Return bình thường: lịch được phép chạy lượt sau.
    } catch (IllegalStateException ex) {
        recordFatalFailure(ex);
        throw ex; // Cố tình dừng các lượt kế tiếp của lịch này.
    }
};
```

Đây là **mảnh thiết kế**; `TransientDependencyException`, `performOneSafeIteration()` và các phương thức `record...` phải được ứng dụng định nghĩa. Mức phân loại ngoại lệ và lựa chọn dừng/tiếp tục phải dựa trên hợp đồng của job cụ thể.

Đường ghi nhận metric/log cũng cần đủ ổn định. Nếu recordFailure ném exception, nó có thể làm thay đổi quyết định tiếp tục. Trong ứng dụng cần bảo đảm giám sát cao, có thể dùng kênh báo lỗi độc lập, gom lỗi của logging dưới dạng suppressed, hoặc lưu trạng thái thất bại bền vững. Tuy nhiên, không nên viết một `catch` vô hạn, cố bỏ qua mọi lỗi không xác định để duy trì tỷ lệ chạy giả tạo.

### Retry ngay trong một lượt hay chờ kỳ sau?

Có hai cách hợp lý, không tương đương:

- **Đợi kỳ tiếp theo:** khi lỗi có khả năng tạm thời và không cần phản hồi tức thì, ghi nhận lượt thất bại rồi return bình thường. Lượt sau sẽ thử lại theo chính lịch `fixedRate` hoặc `fixedDelay`.
- **Retry trong lượt hiện tại:** thử thêm có giới hạn theo timeout/backoff, rồi quyết định thành công hoặc thất bại. Nếu một lượt chạy dài, lịch fixed-rate có thể bị trễ; lịch fixed-delay cũng bị dời theo thời gian kết thúc lượt.

Đừng phối hợp nhiều vòng retry ở client, service và scheduler mà không có giới hạn tổng. Một dependency tạm mất có thể gây *retry storm* nếu mọi instance cùng đánh thức vào thời điểm giống nhau; jitter và circuit breaker có thể phù hợp với hệ thống quy mô lớn.

## 17. Quan sát và cảnh báo khi job âm thầm ngừng chạy

Sự cố thường gặp là job dừng sau exception nhưng ứng dụng vẫn trả HTTP 200 và JVM vẫn sống. Có thể xây watchdog riêng dựa trên các chỉ số được cập nhật ngay trong từng lượt:

- `lastStartedAt`: lần gần nhất bắt đầu;
- `lastCompletedAt` và `lastSuccessAt`: lần gần nhất hoàn thành/thành công;
- `consecutiveFailures`: số lần lỗi liên tiếp;
- `lastErrorType`, `lastErrorSummary`: loại lỗi và ngữ cảnh không chứa bí mật;
- Trạng thái `ScheduledFuture`: **active**, **cancelled**, **completed exceptionally**;
- Deadline kỳ vọng của lần chạy tiếp theo, cộng với sai số phù hợp và tình trạng scheduler.

Nếu `future.isDone()` cho một lịch **được kỳ vọng còn chạy**, cần phát cảnh báo và xác định nguyên nhân thay vì chỉ tạo lịch mới một cách mù quáng. Nếu `future.get(timeout)` ném `TimeoutException`, cần nhớ rằng một future định kỳ khỏe mạnh cũng chưa hoàn tất, nên timeout của `get()` không đủ kết luận job bị treo.

Tránh giám sát chỉ bằng log stack trace vì task có thể thất bại nhưng ngoại lệ đã được future giữ lại; công việc tự catch lỗi có thể cũng không in gì. Đường giám sát nên là phần rõ ràng của hợp đồng job.

## 18. Vòng đời scheduler: shutdown, cancel và tác động lên lịch

Một scheduler có thể ngừng các kỳ tiếp theo vì các nguyên nhân **khác lỗi trong task**:

- `future.cancel(...)` hủy một lịch cụ thể.
- `scheduler.shutdown()` ngừng nhận task mới, và mặc định với `ScheduledThreadPoolExecutor` các periodic task hiện hữu không tiếp tục sau shutdown (chính sách có thể được thay đổi qua `setContinueExistingPeriodicTasksAfterShutdownPolicy` trên lớp triển khai).
- `shutdownNow()` yêu cầu ngừng càng sớm càng tốt, trả về các task chưa bắt đầu và có thể interrupt worker đang chạy; nó không bảo đảm mọi side effect dừng tức thời.
- Task bị chậm vì pool nghẽn hoặc dependency treo, dù future chưa `isDone()`.
- JVM hoặc process dừng; lịch trong bộ nhớ không tự được lưu bền vững và khôi phục sau restart.

Để chẩn đoán “vì sao job không chạy”, cần phân biệt **exception suppression**, **cancellation**, **executor shutdown**, **quá tải/trễ lịch**, **kẹt công việc**, và **process restart**. Chỉ nhìn một giá trị Boolean không đủ.

Trong mã production dùng `ScheduledThreadPoolExecutor`, có thể cân nhắc `setRemoveOnCancelPolicy(true)` để các task đã hủy được gỡ khỏi hàng đợi nhanh hơn; API này là tùy chọn của lớp triển khai, không phải phương thức trên interface `ScheduledExecutorService`. Không được coi thay đổi đó là xử lý cho task đã **hoàn tất do exception**, vốn khác cancellation.

## 19. Kiểm thử chính xác việc dừng và tiếp tục

Khi viết test, ngoài kiểm tra loại exception, phải xác nhận số lần thực thi **trước và sau lỗi**. Những trường hợp quan trọng:

| Kịch bản | Kiểm tra |
| --- | --- |
| `scheduleAtFixedRate` lỗi lượt hai | `ScheduledFuture.get(timeout)` ném `ExecutionException` đúng `cause`; bộ đếm không tăng sau đó |
| `scheduleWithFixedDelay` lỗi lượt đầu | Lịch dừng; các kỳ tiếp theo không chạy |
| Task catch lỗi tạm thời | Sau một lượt lỗi, các lượt tiếp tục chạy; metric ghi nhận lỗi |
| Exception thoát từ hàm logging/callback | Lịch cũng có thể dừng; người quan sát nhận được lỗi thích hợp |
| Một lịch lỗi, lịch độc lập khỏe | Lịch độc lập vẫn chạy khi scheduler hoạt động |
| `Future` bị hủy | `isCancelled()` đúng; không nhầm với lỗi thực thi |
| Executor bị shutdown | Không lẫn shutdown policy với suppression do exception |
| Một lượt chạy lâu hơn `period` | Không có hai lượt của **cùng lịch** thực thi đồng thời |
| Async stage lỗi sau khi `Runnable` return | Lịch ngoài có thể tiếp tục; lỗi stage phải được thu thập riêng |
| Retry tác vụ có side effect | Không tạo ghi DB, message hoặc yêu cầu thanh toán trùng |

Dùng `CountDownLatch`, `AtomicInteger` và timeout đủ rộng để test những sự kiện cần thiết. Không dùng `Thread.sleep()` như bằng chứng duy nhất “chắc chắn đã xảy ra ba lượt”; thời gian scheduler phụ thuộc tải hệ thống. Khi xác nhận suppression, `future.get(timeout)` trả về bằng exception giúp biết lịch đã kết thúc; sau đó có thể kiểm tra bộ đếm. Tránh để các tác vụ test chạy mãi hoặc gây rò executor sau assertion thất bại: luôn dọn trong `finally`.

Nếu dùng hệ thống lịch khác ngoài Java SE như Quartz, Spring `@Scheduled` hoặc scheduler của ứng dụng, phải đọc hợp đồng của framework và cấu hình error handler tương ứng. Không suy rộng quy tắc suppression của `ScheduledExecutorService` sang mọi framework: có framework bắt lỗi, ghi log và tiếp tục các lần sau theo chính sách riêng.

## 20. Checklist trước khi triển khai tác vụ định kỳ

1. **Chọn đúng kiểu lịch:** fixed-rate nếu cần nhịp theo mốc dự kiến, fixed-delay nếu muốn chờ sau mỗi lần hoàn thành; hiểu rằng cùng một periodic task không overlap ở cấp `Runnable`.
2. **Phân loại lỗi:** lỗi tạm thời nào được catch để tiếp tục, lỗi nghiêm trọng nào phải thoát ra để dừng lịch?
3. **Quan sát kết quả:** giữ `ScheduledFuture` và ghi lại trạng thái/lỗi; không dựa duy nhất vào `UncaughtExceptionHandler` hoặc log mặc định.
4. **Không block vô hạn ở `get()`:** future định kỳ đang hoạt động thường không có kết quả kết thúc; dùng timeout và cơ chế giám sát phù hợp.
5. **Quản lý vòng đời:** cancel, shutdown, interrupt và cleanup theo chính sách; lưu ý cancel không undo tác dụng phụ đã có.
6. **Bảo toàn dữ liệu:** transaction, idempotency, checkpoint và kiểm tra kết quả chưa rõ trước retry.
7. **Kiểm tra job bất đồng bộ:** nếu `Runnable` chỉ khởi tạo future thì xử lý lỗi và overlap của future riêng.
8. **Giám sát từng job:** thời điểm thành công cuối, lỗi liên tiếp, trạng thái future và sức khỏe scheduler.
9. **Test cả hai hành vi:** exception thoát ra **dừng** các kỳ sau; exception được xử lý hợp lệ **cho phép tiếp tục**.
10. **Kiểm tra đúng thư viện:** `Timer`, `ScheduledThreadPoolExecutor`, Spring/Quartz hoặc các scheduler khác có hợp đồng và chính sách lỗi khác nhau.

**Quy tắc cần nhớ:** với `scheduleAtFixedRate` và `scheduleWithFixedDelay`, **một exception thoát khỏi lượt chạy có thể chấm dứt toàn bộ các lần chạy tương lai của lịch đó**. Bắt lỗi bên trong cho phép chạy tiếp khi thật sự an toàn; quan sát `ScheduledFuture` và trạng thái nghiệp vụ giúp tránh thất bại âm thầm. Lịch chạy, trạng thái dữ liệu và khả năng phục hồi cần được thiết kế cùng nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-exception-handling-010">Chuyển thông tin thất bại qua ranh giới luồng bằng kết quả có quản lý</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển thông tin thất bại qua ranh giới luồng bằng kết quả có quản lý

## 1. Vì sao ứng dụng đa luồng cần một kết quả có quản lý?

Khi một phương thức gọi trực tiếp phương thức khác trên **cùng luồng**, ngoại lệ có thể lan theo call stack đến `catch` của người gọi. Nhưng khi công việc được giao sang luồng khác, mỗi luồng có call stack riêng: một exception ném trong worker **không tự động được ném vào `try-catch` đang bao quanh thao tác gửi việc** ở luồng điều phối.

Vì vậy, nếu người gọi cần biết công việc cuối cùng thành công hay thất bại, hệ thống phải có **kênh chuyển thông tin giữa các luồng**. Trong Java Core, hai công cụ thường dùng là:

- **`Future<T>`**: một tay nắm (*handle*) đại diện cho kết quả của công việc chưa chắc hoàn tất. `Future.get()` chờ và thu giá trị hoặc báo thất bại.
- **`CompletableFuture<T>`**: một future có thể hoàn thành từ code và có thể nối thành nhiều giai đoạn bất đồng bộ; cung cấp cách xử lý, biến đổi và tổng hợp cả kết quả lẫn thất bại.

Một kết quả có quản lý thường cần thể hiện ba lớp thông tin riêng:

1. **Trạng thái vòng đời**: đang chờ, đang chạy, đã hoàn thành, hoàn thành lỗi, hay bị hủy.
2. **Kết quả nghiệp vụ**: tìm thấy/không tìm thấy; được chấp nhận/từ chối; các trạng thái mà ứng dụng *dự kiến*.
3. **Lỗi thực thi**: `IOException`, exception bất thường do lập trình, timeout, mất kết nối hoặc lỗi tài nguyên khiến việc tính toán không hoàn tất như kế hoạch.

Không nên đánh đồng “`Future.isDone() == true`” với “đã thành công”; không nên coi mọi exception là một giá trị nghiệp vụ “không tìm thấy”; cũng không nên hiểu “caller đã catch lỗi từ Future” là mọi tác động của worker tự hoàn tác.

Mục tiêu của bài là thiết kế một đường đi rõ ràng: **gửi công việc → nhận handle → hoàn thành thành công hoặc thất bại → quan sát kết quả ở ranh giới luồng → ra quyết định**.

## 2. Ranh giới luồng thay đổi đường đi của exception

Sơ đồ:

~~~text
Luồng điều phối A                         Luồng worker B
--------------------------               --------------------------
try {                                    chạy task
    future = executor.submit(task);  ->       xử lý dữ liệu
    // submit trả về handle                    throw IOException
} catch (...) {                               |
    // lỗi của thao tác submit                 v
}                                          Future ghi nhận failure
           |
           v
future.get()  <-----------------------   task hoàn tất
    |
    +-- trả T
    +-- ExecutionException(cause)
    +-- InterruptedException
    +-- CancellationException
~~~

`submit` nhận việc và có thể trả trước khi worker bắt đầu. `catch` quanh `submit` có thể bắt lỗi phát sinh **ngay tại lệnh gửi việc**, như `RejectedExecutionException` nếu executor từ chối; nó không tự nhận exception phát sinh **sau này trong task**.

Khi dùng `ExecutorService.submit(Callable<T>)`, exception thoát khỏi `call()` thường được ghi lại trong đối tượng `Future`. Người gọi lấy được nó khi gọi `get()`: Java bọc nguyên nhân của task trong `ExecutionException`. Đây chính là cách chuyển lỗi **qua ranh giới luồng bằng một đối tượng có quản lý**, thay vì cố ném ngược qua call stack không còn liên quan.

### 2.1. `Thread.join()` không thay `Future.get()`

`Thread.join()` chỉ đợi thread kết thúc. Nó không có kết quả trả về của `Runnable.run()` và không ném lại exception không được bắt của worker. Khi cần lấy kết quả hoặc lỗi có kiểu, `Future` thường phù hợp hơn một `Thread` tự tạo.

Một thread con có thể được cài `UncaughtExceptionHandler` để ghi nhận lỗi không bắt được, nhưng handler phục vụ cơ chế xử lý lỗi của chính thread đó. Handler không tự gửi giá trị thành công về caller, không cung cấp trạng thái cancellation/timeout như Future và không được xem là một lời hứa “task thành công” hay “task đã rollback”.

## 3. Phân biệt kết quả nghiệp vụ, exception và trạng thái điều phối

Ba nhóm này dẫn tới hành động khác nhau:

| Tình huống | Bản chất | Biểu diễn thường hợp lý | Hành động caller |
| --- | --- | --- | --- |
| Có dữ liệu | Thành công nghiệp vụ | `Found(value)` | Dùng giá trị |
| Mã hợp lệ nhưng không có dữ liệu | Kết cục bình thường được dự kiến | `Absent(id)` hoặc `Optional<T>` bên trong `Future` | Hiển thị không tìm thấy |
| Quy tắc nghiệp vụ từ chối | Kết cục dự kiến với nguyên nhân có kiểu | `Rejected(code, details)` | Yêu cầu sửa dữ liệu/quy trình |
| Lỗi I/O mà API dự kiến báo có cấu trúc | Thất bại thực thi đã được phân loại | `Failed(code, cause)` trong một result type | Retry/ghi nhận/báo lỗi theo code |
| Worker ném exception không xử lý | Thất bại bất thường của tác vụ | `ExecutionException` hoặc completion ngoại lệ | Xem `cause`, không giả thành vắng mặt |
| Caller hết thời gian đợi | Trạng thái điều phối: chưa có kết quả trong hạn | `TimeoutException` từ `get(timeout, ...)` | Xác định tiếp tục chờ/hủy/tra trạng thái |
| Task bị hủy | Trạng thái vòng đời | `CancellationException`, `isCancelled()` | Chấp nhận hủy theo chính sách |
| Luồng đang đợi bị interrupt | Tín hiệu điều phối nhắm vào luồng chờ | `InterruptedException` | Truyền tiếp hoặc khôi phục cờ interrupt |

Bởi vậy, chữ ký `Future<Optional<Product>>` có thể hợp lý khi tìm kiếm có thể trả về rỗng nhưng vẫn muốn exception thật truyền qua `get()`. Nếu có nhiều trạng thái nghiệp vụ, `Future<SearchResult>` với sealed result type thường dễ diễn đạt hơn. Nếu muốn **chuyển lỗi I/O thành giá trị có kiểu**, một nhánh `Failed` có thể lưu mã lỗi ổn định và nguyên nhân; tuy nhiên code phải chuyển đổi **có chủ đích**, không dùng `catch (Throwable)` để nuốt mọi lỗi của JVM.

Quy tắc quan trọng: **`Future<T>` quản lý việc hoàn thành; `T` nói lên dữ liệu thành công hoặc các kết cục nghiệp vụ của thao tác.** Thực hiện phân loại ở đúng tầng giúp caller biết phải làm gì.

## 4. `Callable<T>` và `Future<T>`: nền tảng của kết quả có quản lý

`Runnable.run()` không có giá trị trả về và không khai báo checked exception. `Callable<T>.call()` trả về `T` và có thể khai báo `throws Exception`. Do đó, khi công việc cần trả kết quả hoặc báo lỗi, `Callable` kết hợp `submit` là một lựa chọn tự nhiên.

~~~java
ExecutorService executor = Executors.newSingleThreadExecutor();

try {
    Future<Integer> future = executor.submit(() -> 20 + 22);
    System.out.println(future.get());
} finally {
    executor.shutdown();
}
~~~

Đây là **fragment minh họa**, giả định đã import `ExecutorService`, `Executors`, `Future` và phương thức chứa đoạn mã khai báo hoặc xử lý `InterruptedException`, `ExecutionException`. Khi chạy trong môi trường phù hợp, giá trị từ `get()` là `42`. `submit` chỉ trả handle; `get` mới chờ và thu kết quả. `finally` ở đây có nhiệm vụ gọi `shutdown()` để không tiếp tục nhận việc mới; nếu chương trình yêu cầu chờ toàn bộ task kết thúc thì cần thêm chiến lược chờ và dọn riêng.

### 4.1. Những trạng thái quan trọng của Future

| API | Ý nghĩa chính | Điều không nên suy diễn |
| --- | --- | --- |
| `get()` | Đợi đến khi có kết quả, task lỗi hoặc bị hủy; có thể bị interrupt | Không phải lời hứa task sẽ không bao giờ treo |
| `get(timeout, unit)` | Đợi có giới hạn rồi có thể ném `TimeoutException` | Hết hạn không đồng nghĩa task đã dừng |
| `isDone()` | Task hoàn tất theo trạng thái Future, kể cả lỗi/hủy | `true` không đồng nghĩa thành công |
| `isCancelled()` | Future đã bị hủy | Không bảo đảm mọi side effect đã được hoàn tác |
| `cancel(false)` | Cố hủy; không yêu cầu interrupt nếu đang chạy | Không cam kết chặn task đang thực thi |
| `cancel(true)` | Cố hủy, có thể yêu cầu interrupt worker đang chạy | Không ép tác vụ không hợp tác phải dừng ngay |

`ExecutionException` là **vỏ bọc**, không phải nguyên nhân gốc: `getCause()` thường trả exception được worker ném ra. `InterruptedException` lại thuộc **luồng đang đợi**. Khi nhận nó, hãy chọn một chính sách rõ: truyền tiếp hoặc gọi lại `Thread.currentThread().interrupt()` nếu không thể truyền checked exception lên tầng trên. Không xử lý nó giống như `ExecutionException` của worker.

`get(timeout, unit)` có thể làm caller trả về nhanh, nhưng job vẫn có thể tiếp tục ghi dữ liệu. Nếu timeout dẫn đến retry một thao tác có side effect, hệ thống phải suy xét idempotency hoặc truy vấn trạng thái. Không biến timeout thành “task chắc chắn thất bại và không làm gì”.

### 4.2. Thông tin lỗi cần đi kèm nguyên nhân

Bắt exception để chuyển sang trạng thái có kiểu:

~~~java
Callable<SearchResult> task = () -> {
    try {
        return readFromRepository();
    } catch (IOException e) {
        return new Failed("P01", FailureCode.STORAGE, e);
    }
};
~~~

Đây là **mã minh họa không độc lập**, vì `SearchResult`, `Failed`, `FailureCode` và `readFromRepository` chưa được định nghĩa trong fragment. Chủ ý là phân loại `IOException` thành mã ổn định `STORAGE` và giữ `cause` để chẩn đoán. Tránh bắt mọi `Throwable` rồi trả `Failed("UNKNOWN")`, bởi hành động đó có thể che lỗi nghiêm trọng, làm hỏng trạng thái hoặc khiến caller tưởng một lỗi lập trình là lỗi nghiệp vụ đã được lường trước.

Nếu chính API quyết định rằng lỗi đọc dữ liệu phải được lan qua `ExecutionException`, không cần catch `IOException` trong Callable: cứ để `call() throws Exception` phát sinh lỗi. Cả hai cách đều khả thi; quan trọng là hợp đồng phải **nhất quán và rõ cho caller**.

## 5. Ví dụ Java 17 hoàn chỉnh: quản lý kết quả từ nhiều worker

Chương trình sau dùng một `ExecutorService` hai thread. Bốn tác vụ được gửi cùng lúc:

- `A` trả dữ liệu thành công.
- `N` được tra cứu hợp lệ nhưng không tồn tại.
- `IO` gây `IOException` và được adapter công việc chuyển thành `Failed(STORAGE)`.
- `BUG` mô phỏng invariant bị phá vỡ và được giữ ở kênh exceptional completion của `Future`, không bị giả thành mã “không có dữ liệu”.

Sau đó một `CompletableFuture` minh họa cách **chuyển completion ngoại lệ thành một giá trị `Failed` có kiểu** tại ranh giới `handle`.

Lưu toàn bộ mã bên dưới thành **`ManagedThreadResultDemo.java`**:

~~~java
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ManagedThreadResultDemo {
    sealed interface SearchResult permits Found, Absent, Failed {}

    record Found(String id, String value) implements SearchResult {}
    record Absent(String id) implements SearchResult {}
    record Failed(String id, FailureCode code, Throwable cause)
            implements SearchResult {}

    enum FailureCode { STORAGE, UNEXPECTED }

    static SearchResult lookup(String id) throws IOException {
        return switch (id) {
            case "A" -> new Found(id, "alpha");
            case "N" -> new Absent(id);
            case "IO" -> throw new IOException("storage unavailable");
            case "BUG" -> throw new IllegalStateException("broken invariant");
            default -> new Absent(id);
        };
    }

    static Callable<SearchResult> managed(String id) {
        return () -> {
            try {
                return lookup(id);
            } catch (IOException e) {
                return new Failed(id, FailureCode.STORAGE, e);
            }
        };
    }

    static Throwable unwrapCompletion(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    static String display(SearchResult result) {
        if (result instanceof Found found) {
            return "FOUND " + found.id() + "=" + found.value();
        }
        if (result instanceof Absent absent) {
            return "ABSENT " + absent.id();
        }
        if (result instanceof Failed failed) {
            return "FAILED " + failed.id() + "=" + failed.code();
        }
        throw new IllegalStateException("Unknown result");
    }

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<SearchResult>> futures = new ArrayList<>();
            for (String id : List.of("A", "N", "IO", "BUG")) {
                futures.add(pool.submit(managed(id)));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    System.out.println(display(futures.get(i).get()));
                } catch (ExecutionException e) {
                    System.out.println("UNEXPECTED " + e.getCause()
                            .getClass().getSimpleName());
                }
            }

            CompletableFuture<SearchResult> async =
                    CompletableFuture.<SearchResult>supplyAsync(() -> {
                        throw new IllegalArgumentException("async defect");
                    }, pool).handle((value, error) -> {
                        if (error == null) {
                            return value;
                        }
                        return new Failed("ASYNC", FailureCode.UNEXPECTED,
                                unwrapCompletion(error));
                    });
            System.out.println(display(async.join()));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Dùng JDK 17 trở lên, đứng trong thư mục chứa file:

~~~bash
javac --release 17 ManagedThreadResultDemo.java
java ManagedThreadResultDemo
~~~

**Stdout chính xác:**

~~~text
FOUND A=alpha
ABSENT N
FAILED IO=STORAGE
UNEXPECTED IllegalStateException
FAILED ASYNC=UNEXPECTED
~~~

### 5.1. Giải thích vì sao output có thứ tự cố định

Các task trong nhóm đầu có thể **thực thi và hoàn tất theo thứ tự bất kỳ** trên hai worker. Nhưng vòng lặp duyệt `List<Future<SearchResult>>` theo thứ tự đã gửi và gọi `get()` từng future, vì vậy **thứ tự các dòng in** là A, N, IO, BUG dù thời điểm thực thi thực tế khác nhau. Nếu thay bằng callback in trực tiếp trên từng worker, thứ tự in có thể khác. Sau vòng lặp, chương trình mới tạo stage `CompletableFuture` và đợi `join()` trước khi in dòng cuối.

- `Found A=alpha`: task trả `Found` bình thường; `Future.get()` trả `SearchResult`.
- `Absent N`: tra cứu trả `Absent`, là một **kết cục thành công về mặt thực thi** nhưng không có bản ghi.
- `Failed IO=STORAGE`: task bắt chính xác `IOException`, giữ exception gốc trong `cause` và trả một kết quả đã phân loại. `get()` không ném `ExecutionException` vì Callable đã hoàn thành bằng việc **trả giá trị**.
- `UNEXPECTED IllegalStateException`: `managed` không bắt `IllegalStateException` của `lookup("BUG")`. Future hoàn thành lỗi; `get()` ném `ExecutionException` và caller đọc `getCause()`.
- `Failed ASYNC=UNEXPECTED`: `CompletableFuture.supplyAsync` hoàn thành ngoại lệ, `handle` tạo `Failed` chứa mã lỗi và nguyên nhân đã tháo lớp bọc `CompletionException`. Stage **mới** vì thế hoàn thành bình thường với đối tượng `Failed`.

Ví dụ giữ `Throwable cause` **trong kiểu kết quả nội bộ** để tiện chẩn đoán; khi chuyển thành REST response, log sự kiện hay thông báo người dùng, cần cung cấp **mã lỗi an toàn**, tránh tuần tự hóa stack trace, file path hoặc thông tin nhạy cảm ra ngoài. Mã `STORAGE` và `UNEXPECTED` là minh họa chính sách; ứng dụng thực có thể dùng enum chi tiết hơn, ví dụ `TEMPORARY_UNAVAILABLE`, `PERMISSION_DENIED` hay `NOT_RECOVERABLE` nếu caller thực sự cần phân biệt.

### 5.2. Vì sao không bắt mọi exception để luôn trả `Failed`?

Ta có thể thiết kế một `Result<T,E>` gom tất cả exception ứng dụng vào nhánh lỗi, nhưng phải xét nguyên nhân và ranh giới. Trong ví dụ, lỗi I/O dự kiến được chuyển thành `Failed(STORAGE)`, trong khi `IllegalStateException` mô phỏng invariant bị phá vỡ được giữ là exceptional completion để không bị che giấu.

Ở ranh giới **điều phối công việc** hoặc **giao tiếp ngoài hệ thống**, có thể cần chuyển một số exception chưa xử lý thành kết quả có kiểu để trả lời caller. Khi đó phải phân loại, log và giữ nguyên nhân phù hợp, không lặng lẽ biến thành “không tìm thấy” hoặc “thành công”. Java không bắt buộc mọi kiểu lỗi phải đi theo cùng một kênh.

### 5.3. Vì sao kết quả dùng `sealed interface` và `record`?

`sealed interface SearchResult` liệt kê những kiểu triển khai được cho phép: `Found`, `Absent`, `Failed`. Mỗi `record` mang trường riêng: giá trị khi tìm thấy, khóa không tìm thấy, hoặc mã và nguyên nhân lỗi khi thất bại. Đây là các tính năng ổn định trong Java 17.

`display` dùng `instanceof` pattern matching để đọc dữ liệu của từng loại. `switch` pattern matching trên sealed type là preview ở Java 17, do đó ví dụ không cần bật tính năng preview khi biên dịch. Chữ ký `Future<SearchResult>` cho phép caller xử lý các kết quả có cấu trúc, nhưng **không miễn trừ** `ExecutionException` hoặc cancellation ở lớp Future.

## 6. Chọn nơi chuyển exception thành dữ liệu

Có hai hợp đồng phổ biến:

**Hợp đồng A — lỗi thực thi vẫn là exceptional completion:**

~~~java
Future<String> future = executor.submit(() -> remoteRead());
String value = future.get(); // Có thể ném ExecutionException.
~~~

Đoạn minh họa giả định `executor`, `remoteRead()` và các checked exception đã được khai báo/bắt. Cách này phù hợp khi exception thực thi là một phần kênh lỗi tiêu chuẩn, và tầng gọi có chính sách xử lý thống nhất.

**Hợp đồng B — một số kết cục được chuyển thành `Result` có kiểu:**

~~~java
Future<LoadResult> future = executor.submit(() -> {
    try {
        return new Loaded(remoteRead());
    } catch (IOException e) {
        return new LoadFailed("STORAGE", e);
    }
});
~~~

Fragment giả định `LoadResult`, `Loaded`, `LoadFailed`, `remoteRead` và `executor` đã tồn tại. Ở đây `get()` có thể trả `LoadFailed` bình thường; các exception không được catch vẫn có thể dẫn tới `ExecutionException`.

Cần phân biệt **hoàn thành bình thường của Future** với **thành công nghiệp vụ**: một `Future<Result>` trả về `Failure` không phải exception ở kênh Future, nhưng nghiệp vụ vẫn thất bại và phải được caller xử lý. Nếu monitoring chỉ đếm số task hoàn thành ngoại lệ, nó có thể bỏ qua rất nhiều result mang `Failure`; phải thiết kế metric theo cả hai lớp trạng thái.

Đừng vô ý dùng hai cách biểu diễn **cùng một lỗi** ở những API ngang cấp mà không có quy tắc, vì caller sẽ không biết khi nào đọc nhánh `Failed` và khi nào bắt `ExecutionException`.

## 7. `CompletableFuture`: quản lý cả chuỗi bất đồng bộ

`CompletableFuture<T>` mở rộng ý tưởng handle: thay vì chỉ chờ kết quả cuối, caller có thể định nghĩa các stage kế tiếp.

| API | Ý nghĩa khi upstream thành công | Khi upstream thất bại |
| --- | --- | --- |
| `thenApply(fn)` | Áp dụng chuyển đổi đồng bộ theo cơ chế completion stage | Không gọi `fn`; stage kết quả thất bại |
| `thenCompose(fn)` | Nối stage phụ thuộc khác, tránh lồng `CompletableFuture<CompletableFuture<T>>` | Không gọi `fn`; lỗi được truyền |
| `thenCombine(other, fn)` | Kết hợp giá trị từ hai stage đã hoàn thành bình thường | Nếu một stage thất bại, kết quả có thể hoàn thành ngoại lệ |
| `exceptionally(fn)` | Giữ giá trị gốc | Cho phép tạo giá trị thay thế từ lỗi |
| `handle((value,error) -> result)` | Thấy giá trị và có thể biến đổi | Thấy lỗi và có thể biến đổi thành giá trị |
| `whenComplete((value,error) -> action)` | Thực hiện hành động quan sát | Thấy lỗi; tự nó không phải cơ chế bảo đảm phục hồi |
| `get()` | Trả giá trị khi hoàn thành | Checked `ExecutionException` (hoặc cancellation), có thể bị interrupt |
| `join()` | Trả giá trị khi hoàn thành | Unchecked `CompletionException` (hoặc cancellation) |

Một stage có thể được hoàn thành bởi worker, bởi thread thực hiện completion của stage trước hoặc bởi executor được truyền rõ ràng, tùy API. **Không giả định callback luôn chạy trên thread đã tạo CompletableFuture.** Đối với các biến thể `...Async` không có executor tường minh, Java sử dụng cơ chế executor mặc định được mô tả bởi API; trong hệ thống cần quản lý tài nguyên, nên truyền executor thích hợp và nêu rõ người sở hữu việc shutdown.

### 7.1. `handle` là chuyển đổi có chủ đích, không phải phép chữa lỗi tự động

Ví dụ:

~~~java
CompletableFuture<String> raw = CompletableFuture.supplyAsync(
        () -> {
            throw new IllegalStateException("remote request failed");
        });

CompletableFuture<String> recovered =
        raw.exceptionally(error -> "fallback");
~~~

Fragment có thể đặt trong phương thức đã import `CompletableFuture`. `raw` hoàn thành ngoại lệ; `recovered` là stage mới có thể hoàn thành bình thường với chuỗi `fallback`. Chỉ dùng fallback nếu nghiệp vụ thật sự cho phép. Với dữ liệu quan trọng, chuyển lỗi thành một giá trị giả “thành công” có thể che việc chưa thực hiện được tác vụ.

Một cách khác là chuyển thành result có kiểu:

~~~java
CompletableFuture<Outcome> observed =
        raw.handle((value, error) ->
                error == null
                        ? new Success(value)
                        : new FailedOutcome(error));
~~~

Đây là fragment minh họa, giả định `Outcome`, `Success` và `FailedOutcome` được định nghĩa; các nhánh này cần có chung kiểu phù hợp. `handle` quan sát cả hai trạng thái và tạo kết quả tường minh, thay vì phải phân tích chuỗi thông báo để xem có lỗi hay không.

### 7.2. `whenComplete` dành cho quan sát, cần giữ lỗi

`whenComplete` thường phù hợp để ghi metric, log hoặc cleanup có kiểm soát:

~~~java
CompletableFuture<String> withObservation =
        raw.whenComplete((value, error) -> {
            if (error != null) {
                recordFailure(error);
            }
        });
~~~

Fragment giả định `recordFailure`. Stage `withObservation` vẫn mang kết quả/lỗi theo quy tắc `whenComplete`; callback này **không mặc định biến lỗi thành thành công**. Không dùng `whenComplete` rồi bỏ qua future kết quả nếu caller còn cần bảo đảm thất bại đã được xử lý.

Đặc biệt, callback quan sát có thể ném exception của chính nó. Nếu upstream đã lỗi, hành vi chọn exception nào được truyền sẽ theo hợp đồng CompletionStage; vì vậy callback ghi log/metric nên gọn, không tạo thêm tác động phụ nguy hiểm và không thay thế quy trình xử lý kết quả chính.

### 7.3. Ghép nhiều stage và kết quả tổng hợp

`CompletableFuture.allOf(f1, f2, ...)` trả `CompletableFuture<Void>` hoàn thành sau khi các future thành phần hoàn thành. Nếu có stage thất bại, `allOf` hoàn thành ngoại lệ; nó **không trả danh sách giá trị**, cũng không tự tạo báo cáo tổng hợp đầy đủ của từng future. Nếu cần phân biệt lỗi của từng task, phải quan sát từng future hoặc chuyển từng stage sang một giá trị `Outcome` trước khi tổng hợp.

`CompletableFuture.anyOf(...)` hoàn thành theo một stage thành phần hoàn tất, kể cả hoàn thành ngoại lệ; không đồng nghĩa chọn “stage thành công đầu tiên”. Chỉ dùng khi đó đúng là hợp đồng cần thiết.

Ví dụ phân loại từng stage trước `allOf`:

~~~java
CompletableFuture<Outcome> a = serviceA()
        .handle((value, error) -> classify("A", value, error));
CompletableFuture<Outcome> b = serviceB()
        .handle((value, error) -> classify("B", value, error));

CompletableFuture<Void> all = CompletableFuture.allOf(a, b);
all.join();
Outcome first = a.join();
Outcome second = b.join();
~~~

Đây là **pseudocode gần Java**, các hàm `serviceA`, `serviceB`, `classify` và `Outcome` chưa khai báo, đồng thời kiểu generic cần được thống nhất trong code thật. Ý tưởng là mỗi stage có một kênh trạng thái có kiểu, sau đó tổng hợp sau điểm đồng bộ. Không nên chỉ gọi `all.join()` rồi suy ra tất cả đã thành công về mặt nghiệp vụ.

## 8. Hoàn thành ngoại lệ và bóc lớp nguyên nhân

Trong `CompletableFuture`, các cách kích hoạt/quan sát lỗi gồm:

- Worker hoặc callback ném exception: stage có thể hoàn thành ngoại lệ.
- Code chủ động gọi `completeExceptionally(error)`: thử hoàn thành một CompletableFuture đang chưa được hoàn thành bằng lỗi đã cho.
- `join()`: thông thường báo exceptional completion qua unchecked `CompletionException`; hủy có thể qua `CancellationException`.
- `get()`: báo thất bại qua checked `ExecutionException`; caller còn cần xử lý `InterruptedException`.
- `isCompletedExceptionally()`: kiểm tra future đã hoàn thành ngoại lệ, nhưng không trả nguyên nhân cụ thể.

Một future không thể hoàn thành thành công và thất bại cùng lúc; chỉ một trạng thái terminal thắng cuộc cạnh tranh hoàn thành. `complete(value)` và `completeExceptionally(error)` trả boolean chỉ việc lời gọi có làm future chuyển sang hoàn tất thành công hay không. Không dùng hai lệnh này như cách “ghi đè” kết quả đã hoàn thành.

### 8.1. Không dựa vào đúng một lớp `CompletionException`

Trong callback, đối số lỗi có thể đã được bọc bằng `CompletionException` hoặc kiểu khác tùy cách kết thúc stage. Đừng gọi `error.getCause().getMessage()` vô điều kiện, vì `getCause()` có thể `null`. Khi cần chuẩn hóa exception ở tầng nội bộ, có thể tháo các lớp bọc được biết trước và giữ reference tới lỗi gốc.

~~~java
static Throwable unwrapCompletion(Throwable error) {
    while (error instanceof CompletionException && error.getCause() != null) {
        error = error.getCause();
    }
    return error;
}
~~~

Phương thức minh họa này chính là hàm đã dùng trong `ManagedThreadResultDemo`. Nó chỉ tháo `CompletionException` và tránh mất cause khi cause là `null`. Không nên tháo mọi wrapper theo một quy tắc mù quáng: một exception bọc có thể mang thông tin ngữ cảnh quan trọng. Khi log, thường cần cả thông tin ngữ cảnh của wrapper và chuỗi cause gốc.

### 8.2. Exception gốc và lỗi nghiệp vụ có cấu trúc là hai lớp khác nhau

Một result có kiểu nên chứa **mã lỗi ổn định** cho quyết định của caller. `cause` nên phục vụ phân tích và log nội bộ, không phải API để client phân tích chuỗi `getMessage()`. Ví dụ mã `STORAGE_UNAVAILABLE` cho phép chọn retry theo chính sách; thông điệp chi tiết “connection refused” không phải hợp đồng lâu dài.

Khi nhiều tác vụ lỗi, cần gắn **taskId/correlationId** trước khi tổng hợp để biết lỗi thuộc tác vụ nào; nếu chỉ giữ một `Throwable` cuối cùng, các lỗi trước có thể bị mất. Đối với API bên ngoài, tránh lộ stack trace, đường dẫn file, thông tin người dùng hay token trong result.

## 9. Timeout, interruption và cancellation phải được báo đúng

Các trạng thái này thường bị trộn lẫn với “worker đã ném exception”. Thực tế:

| Tín hiệu | Phát sinh tại | Kết luận đúng |
| --- | --- | --- |
| `ExecutionException` | `Future.get()` của caller | Task hoàn thành bằng exception; xét `getCause()` |
| `CompletionException` | `CompletableFuture.join()` hoặc stage | Stage hoàn thành ngoại lệ; xét cause |
| `TimeoutException` | `Future.get(timeout, unit)` hoặc thao tác chờ giới hạn khác | Caller chưa có kết quả trong thời hạn |
| `InterruptedException` | Thread đang chờ trên `get`, `join` của Thread hay blocking API phù hợp | Luồng chờ bị interrupt; không kết luận worker thất bại |
| `CancellationException` | Gọi lấy kết quả một future đã hủy | Công việc ở trạng thái hủy theo hợp đồng Future |
| `RejectedExecutionException` | Lệnh gửi việc tới executor không chấp nhận | Công việc chưa được chấp nhận tại lời gọi gửi đó |

Mẫu xử lý một `Future` có timeout:

~~~java
try {
    Outcome result = future.get(2, TimeUnit.SECONDS);
    handleResult(result);
} catch (TimeoutException e) {
    reportStillPending();
    future.cancel(true); // Chỉ nếu chính sách cho phép yêu cầu hủy.
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
} catch (ExecutionException e) {
    reportFailure(e.getCause());
} catch (CancellationException e) {
    reportCancelled();
}
~~~

Đây là **fragment minh họa**, giả định `future`, `Outcome`, các hàm xử lý, import tương ứng, và phương thức bên ngoài trả `void`. `cancel(true)` chỉ yêu cầu hủy/có thể interrupt worker theo trạng thái task; task không hợp tác có thể tiếp tục tác động bên ngoài. Nếu caller không sở hữu task, **không tự ý cancel** công việc dùng chung khi chỉ một người gọi hết thời gian chờ.

Một `CompletableFuture.orTimeout(...)` có thể hoàn thành chính CompletableFuture ngoại lệ với `TimeoutException` sau thời hạn, nhưng **không tự chứng minh công việc nguồn đã dừng**. `completeOnTimeout(value, ...)` đặt giá trị dự phòng nếu chưa hoàn tất kịp, cũng không tự hủy hoạt động bên ngoài đang chạy. Vì các phương thức này có thể hoàn thành trên future nhận lệnh, cần suy nghĩ kỹ trước khi áp dụng lên future dùng chung ở nhiều caller.

### 9.1. Xử lý `InterruptedException` đúng cách

Nhiều blocking API xóa cờ interrupt khi ném `InterruptedException`. Nếu bạn bắt và không thể ném tiếp, thông thường gọi `Thread.currentThread().interrupt()` rồi kết thúc/trao quyền cho tầng trên. **Không** làm:

~~~java
try {
    future.get();
} catch (InterruptedException ignored) {
    // Nuốt yêu cầu ngắt, tiếp tục vô hạn.
}
~~~

Fragment này cố tình là anti-pattern và chưa xử lý checked exception khác từ `get()`. Nếu cứ nuốt interrupt, quy trình shutdown/hủy có thể không hoạt động như kỳ vọng.

Nếu task phải dừng khi bị interrupt, code task cũng phải **hợp tác**: kiểm tra `Thread.currentThread().isInterrupted()` ở các vòng lặp dài, xử lý `InterruptedException` từ chờ/sleep phù hợp và không âm thầm tiếp tục làm việc sau yêu cầu hủy.

## 10. Quyền sở hữu executor và kết quả: ai đảm bảo task được quan sát?

Giao việc bất đồng bộ tạo ra trách nhiệm quản lý. Nếu hệ thống dùng `ExecutorService`, cần xác định rõ **ai tạo executor, ai shutdown, ai chờ, ai chịu trách nhiệm xử lý thất bại**.

Một quy trình có thể như sau:

~~~text
Request/Coordinator
    |
    +-- cấp taskId và correlationId
    |
    +-- submit -> nhận Future<T>
    |
    +-- lưu/giữ handle đến khi có người xử lý
    |
    +-- get()/callback/tổng hợp kết quả
    |        |
    |        +-- Success
    |        +-- Business outcome
    |        +-- Execution failure
    |        +-- Cancel/timeout
    |
    +-- ghi nhận trạng thái và phản hồi phù hợp
    |
    +-- release handle và tài nguyên theo chính sách
~~~

Không được **fire-and-forget** một task quan trọng rồi coi như thành công nếu không có cơ chế ghi nhận. Ví dụ:

~~~java
executor.submit(() -> sendNotification(orderId));
return "sent";
~~~

Fragment giả định `executor`, `sendNotification`, `orderId` tồn tại. Nó chỉ chứng minh việc `submit` đã được gọi và không ném ngay; không chứng minh thông báo đã gửi. Một `Future` bị vứt bỏ có thể chứa exception không được đọc. Nếu muốn phản hồi ngay, trạng thái nên là “đã tiếp nhận” hoặc “đang xử lý”, đi kèm job ID và phương thức tra kết quả về sau.

### 10.1. `shutdown()` không giống “đã xong hết”

`ExecutorService.shutdown()` bắt đầu đóng executor theo trình tự: không nhận task mới, các task đã gửi vẫn có thể đang chạy. `shutdownNow()` cố dừng task đang hoạt động và trả danh sách task đang đợi, nhưng không bảo đảm chúng đều đã dừng. `awaitTermination(timeout, unit)` chờ executor chấm dứt trong giới hạn; nó không khẳng định mỗi task thành công.

Java 17 `ExecutorService` chưa có cơ chế `close()` như các phiên bản Java mới hơn; sử dụng `shutdown` và các phương thức chờ/hủy theo vòng đời ứng dụng là mẫu phù hợp. Trong ví dụ chạy độc lập, tất cả future đã được `get/join` trước `shutdown`, nên không còn task nào đang chạy khi tới cuối đường thành công. Trong ứng dụng server, executor thường được sở hữu ở tầng dài hạn và shutdown khi ứng dụng dừng, thay vì tạo/đóng cho từng request.

### 10.2. `Future` không phải storage bền vững

`Future` và `CompletableFuture` là những đối tượng trong bộ nhớ tiến trình JVM. Nếu process chết, object Future biến mất; nó **không phải hàng đợi bền vững** và không cung cấp khả năng phục hồi sau crash. Công việc có hậu quả kinh doanh cần sống qua restart có thể cần trạng thái job lưu DB, hàng đợi bền vững, outbox, mã idempotency và quy trình retry/đối soát tùy yêu cầu.

“Có một handle Future” chỉ giải quyết cách quan sát **khi process và đối tượng còn sống**. Nó không biến nhiều side effect thành một transaction. Task có thể đã ghi file hoặc gửi HTTP request rồi mới hoàn thành lỗi; kể cả caller đã nhận `ExecutionException` hoặc `Failed` thì những tác động trước đó vẫn có thể tồn tại.

## 11. Khi nào chuyển thành Result, khi nào giữ exception?

Một hợp đồng API dễ dùng thường phân nhóm:

- **Trạng thái nghiệp vụ dự kiến:** trả result có kiểu như `Found`, `Absent`, `Rejected` với thông tin đủ để caller xử lý. Những nhánh này có thể là một phần `T` của `Future<T>`.
- **Lỗi kỹ thuật được API có chủ đích phân loại:** chuyển thành `Failed(ErrorCode, cause)` nếu tầng gọi cần quyết định retry, báo lỗi, thống kê hoặc phục hồi dựa trên mã.
- **Lỗi bất thường/invariant hỏng:** có thể để task hoàn thành ngoại lệ và báo điều phối; không tự động biến mọi loại lỗi thành giá trị dữ liệu thông thường.
- **Timeout/cancellation/interruption:** thuộc **hợp đồng điều phối** và cần xử lý tách biệt với các nhánh nghiệp vụ.
- **Hệ thống dừng/crash:** có thể không nhận được kết quả của Future; cần quản lý trạng thái bền vững nếu tính năng yêu cầu.

Tùy API, một kiểu `Result<T, E>` tổng quát cũng có thể hữu ích. `E` nên là enum/record/sealed hierarchy có cấu trúc, tránh một chuỗi thông điệp tự do khiến caller phải `contains("timeout")` hoặc `contains("database")` để quyết định. Không cần đưa `Future<Result<Optional<T>>>` vào mọi chỗ nếu chính nghiệp vụ chỉ có hai trạng thái đơn giản và caller đã có kênh exceptional completion.

### 11.1. Quyết định ở ranh giới lớp

| Tầng | Trách nhiệm | Ví dụ |
| --- | --- | --- |
| Task/worker | Làm công việc, xử lý lỗi có thể khôi phục tại chỗ | Bắt một lỗi I/O cụ thể, có chính sách retry hữu hạn |
| Adapter gửi việc | Tạo task, gắn mã, chọn executor, quản lý handle | `submit(Callable<Result>)` |
| Coordinator | Đợi/tổng hợp/đọc kết quả, phân biệt trạng thái, quyết định hủy | `get()` / callback `handle` |
| API boundary | Ánh xạ trạng thái sang giao diện/HTTP/message, bảo vệ thông tin lỗi | Trả mã lỗi có kiểu, không lộ stack trace |
| Hạ tầng bền vững khi cần | Lưu trạng thái job, retry và chống lặp qua process restart | Database/outbox/job store |

Không nhất thiết mỗi ứng dụng có tất cả các tầng. Điều cần có là một **chủ sở hữu rõ ràng** của kết quả cuối, để thất bại không biến thành một stack trace bị bỏ qua.

## 12. Quan sát nhiều kết quả: đủ lỗi, đúng thứ tự và không rò task

Khi gửi nhiều việc, một lỗi ở một task **không tự hủy những task còn lại**. Nếu các task độc lập, có thể chờ tất cả rồi tổng hợp mọi `Outcome`. Nếu task thất bại khiến kết quả chung vô nghĩa, coordinator có thể hủy task còn lại theo chính sách; cần hiểu mức độ hợp tác của cancellation và những side effect đã xảy ra.

Các điểm quan trọng:
1. **Thứ tự hoàn tất khác thứ tự nộp việc.** Duyệt danh sách Future rồi `get()` theo thứ tự gửi tạo thứ tự đọc có dự đoán được, nhưng có thể chặn trên task đầu dù task sau xong sớm.
2. **Một task lỗi không chứng minh task khác chưa chạy.** Nếu task khác đã gửi email, việc thu lỗi không thu hồi email.
3. **`Future.isDone()` không đủ để phân loại.** Muốn biết kết cục, gọi `get`/đọc result. Nếu future bị hủy thì cần xử lý riêng.
4. **Một stage `allOf` lỗi không cung cấp toàn bộ lỗi theo dạng danh sách.** Muốn báo cáo từng task thì biến từng stage thành `Outcome` hoặc truy vấn từng future.
5. **Thời hạn phải có ý nghĩa ở cấp công việc chung.** Nếu mỗi `get` dùng timeout 5 giây cho 20 task theo thứ tự, thời gian tổng có thể vượt quá deadline mong muốn; thiết kế một deadline tổng thay vì dùng timeout rời rạc khi yêu cầu chung là “kết thúc trong 5 giây”.
6. **Khả năng quan sát:** ghi metric số tác vụ thành công, vắng mặt, thất bại nghiệp vụ, exception, timeout, bị hủy một cách tách biệt.

Nếu ứng dụng phải thu **tất cả** thất bại, không bỏ qua Future còn lại chỉ vì `get` của Future đầu tiên ném `ExecutionException`; hãy tạo báo cáo tổng hợp theo chính sách, đồng thời giới hạn thời gian và tài nguyên hợp lý.

## 13. Lỗi thiết kế thường gặp

### 13.1. `try-catch` chỉ bao quanh `submit`

~~~java
try {
    executor.submit(() -> failingOperation());
} catch (Exception e) {
    logError(e);
}
~~~

Đây là fragment giả định các biến/hàm đã tồn tại. `catch` này có thể nhận lỗi xảy ra trực tiếp khi gửi task, chứ **không tự bắt exception phát sinh sau đó trong worker**. Nếu muốn quan sát task, giữ Future và đọc `get()` hoặc đăng ký stage callback phù hợp.

### 13.2. Bỏ Future và trả thông điệp thành công

~~~java
executor.submit(() -> writeImportantFile());
return true;
~~~

Fragment giả định một phương thức trả boolean và các hàm liên quan. `true` này chỉ là kết luận thiếu chứng cứ: công việc có thể chưa chạy. Trả một job handle/trạng thái accepted; hoặc chờ/nhận kết quả rồi mới xác nhận thực sự thành công.

### 13.3. Trả `Optional.empty()` cho mọi exception

~~~java
try {
    return repository.findById(id);
} catch (RuntimeException ignored) {
    return Optional.empty();
}
~~~

Fragment giả định repository trả Optional. Việc không có bản ghi hoàn toàn khác với việc **không biết kết quả vì truy vấn thất bại**. Cần truyền exceptional completion hoặc một nhánh lỗi có kiểu, giữ cause.

### 13.4. Biến mọi `Failed` thành thành công sau `handle`

Nếu `handle` luôn trả `"ok"` sau một exception, stage kết quả có thể hoàn thành bình thường và metric ở Future chỉ nhìn thấy success. Chính sách recovery phải được định nghĩa; giá trị `Failed(code)` vẫn cần được thống kê là thất bại nghiệp vụ/kỹ thuật.

### 13.5. Bắt `InterruptedException` rồi im lặng

Nuốt interrupt khiến hủy/shutdown khó hoạt động. Truyền tiếp hoặc khôi phục cờ interrupt và kết thúc xử lý theo hợp đồng. `InterruptedException` của caller không thay thế việc kiểm tra task có thật sự dừng hay chưa.

### 13.6. Dùng `getCause()` mà không bảo vệ giả định

Với lỗi từ hệ thống khác nhau, wrapper/cause có thể không có cấu trúc như kỳ vọng. Đừng gọi `cause.getMessage()` khi cause có thể `null`; ghi nhận loại wrapper và fallback phù hợp. Không bịa loại lỗi hoặc coi message text là mã lỗi ổn định.

### 13.7. Không có chính sách kết thúc executor

Một pool tạo ra rồi bị quên có thể giữ thread sống và tiêu thụ tài nguyên. Một executor dùng chung bị shutdown bởi code của một request lại có thể làm hỏng những request khác. Ranh giới sở hữu executor cần được xác định khi thiết kế, không chỉ khi test.

## 14. Kiểm thử đường đi của kết quả qua ranh giới luồng

Một bộ test đáng tin cần kiểm thử **cả kết quả trả về và các trạng thái điều phối**, thay vì chỉ bắt đúng một exception:

| Tình huống | Điều cần kiểm tra |
| --- | --- |
| Callable trả `Found` | `get()` trả giá trị chính xác |
| Callable trả `Absent` | Future hoàn thành bình thường; caller phân loại vắng mặt |
| Callable bắt `IOException` và trả `Failed` | `get()` trả `Failed(STORAGE)`, cause được lưu nội bộ |
| Callable để lọt `IllegalStateException` | `get()` ném `ExecutionException`, cause đúng loại |
| Stage `CompletableFuture` thất bại | `join()` ném `CompletionException` khi chưa phục hồi |
| Stage `handle` chuyển lỗi thành `Failed` | `join()` trả result có kiểu, mã lỗi và cause phù hợp |
| Future chưa xong đến deadline | `get(timeout, ...)` ném `TimeoutException`; task không bị mặc định coi là đã dừng |
| Caller đang chờ bị interrupt | Chính sách phục hồi cờ/lan truyền interrupt đúng |
| Future đã bị hủy | `isCancelled()` đúng, `get()` có thể ném `CancellationException` |
| Nhiều Future có một task lỗi | Không bỏ mất trạng thái/lỗi của các task khác |
| Executor shutdown trong lúc có công việc | Không gửi việc mới; xử lý việc đã chấp nhận theo hợp đồng |
| Worker có side effect rồi lỗi | Kết quả báo thất bại đúng, nhưng không giả định side effect đã rollback |

Để test concurrency ổn định, dùng `CountDownLatch` hoặc future có timeout để điều phối các điểm mốc. Đừng dựa vào `Thread.sleep(100)` rồi đoán task chắc chắn đã hoàn thành. Với kiểm thử lỗi, có thể dùng dependency giả ném exception trước/sau khi ghi state; từ đó khẳng định chính xác điều hệ thống đã làm.

Nếu kiểm thử dùng executor riêng, giải phóng executor sau test; nếu task bị hủy và không hợp tác interrupt, cần thiết kế test có thể kết thúc mà không rò thread. Các bài test về retry hoặc side effect phải kiểm tra tình huống **server đã xử lý nhưng phản hồi bị mất**, không chỉ server từ chối ngay.

## 15. Checklist khi phát triển API bất đồng bộ có quản lý

- [ ] Công việc được gửi qua API rõ ràng; caller biết khi nào chỉ là **accepted** và khi nào là **completed**.
- [ ] Có handle/kênh thông báo kết quả; không mặc định `try-catch` quanh `submit` bắt lỗi worker.
- [ ] Kết quả nghiệp vụ dự kiến có kiểu rõ ràng, không trộn với exceptional completion.
- [ ] Lỗi I/O/technical được phân loại theo hợp đồng; không đổi lỗi thành `Optional.empty()`.
- [ ] `ExecutionException` và `CompletionException` được xử lý qua cause; không suy đoán chỉ từ message.
- [ ] `TimeoutException`, `InterruptedException` và `CancellationException` có chính sách riêng.
- [ ] Callback `handle`/`exceptionally` chỉ chuyển lỗi thành giá trị khi có quyết định recovery rõ ràng.
- [ ] Mọi Future quan trọng được quan sát hoặc có người sở hữu xử lý; không bỏ qua lỗi bị lưu trong Future.
- [ ] Executor có vòng đời và người sở hữu việc shutdown.
- [ ] Thống kê failure bao gồm cả `Result.Failed` lẫn exceptional completion.
- [ ] Side effect đã phát sinh trước thất bại được quản lý bằng transaction/idempotency hoặc quy trình phục hồi khi cần.
- [ ] Kiểm thử thành công, thất bại nghiệp vụ, lỗi bất thường, timeout, hủy và interruption trong môi trường có điều phối.

### Bài tập thực hành

Thiết kế API `loadProfiles(List<String> ids)` dùng `ExecutorService` để xử lý nhiều mã người dùng. Với mỗi mã, thao tác có thể trả: dữ liệu hồ sơ, không tìm thấy, hoặc lỗi I/O có thể thử lại. Một mã khác cố tình gây `IllegalStateException`.

1. Định nghĩa kiểu kết quả cho dữ liệu đã đọc, vắng mặt và thất bại có mã lỗi; quyết định exception nào nên tiếp tục truyền qua `Future.get()`.
2. Gửi bốn task song song rồi tổng hợp mọi kết quả theo thứ tự đầu vào. Không bỏ những Future chưa quan sát chỉ vì một task thất bại.
3. Áp dụng một deadline chung cho toàn bộ công việc; chỉ hủy khi chính sách và quyền sở hữu cho phép.
4. Viết lại dùng `CompletableFuture`; chuyển mỗi stage sang kết quả có kiểu trước khi tổng hợp bằng `allOf`.
5. Tạo test mô phỏng task ghi một phần dữ liệu rồi ném exception. Giải thích vì sao thu được `Failed` hoặc `ExecutionException` vẫn **không tự hoàn tác** tác động trước lỗi.

**Kết luận kỹ thuật:** ranh giới luồng tách rời call stack; để chuyển thất bại, caller cần một giao thức kết quả có quản lý. `Future`/`CompletableFuture` mang trạng thái hoàn thành và lỗi xuyên các luồng, còn kiểu kết quả của ứng dụng làm rõ những kết cục nghiệp vụ. Hai lớp đó phải được xử lý tường minh và nhất quán.

</details>

- [Quay lại đầu trang](#back-to-top)
