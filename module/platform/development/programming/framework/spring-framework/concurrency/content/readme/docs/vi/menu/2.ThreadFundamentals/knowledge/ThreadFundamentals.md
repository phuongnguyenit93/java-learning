<a id="back-to-top"></a>

# Nền tảng luồng

## Menu
- [Runnable, Callable, Executor và ExecutorService](#thread-fundamentals-001)
- [Luồng nền tảng, nhóm luồng và luồng ảo](#thread-fundamentals-002)
- [Future và CompletableFuture biểu diễn công việc chưa hoàn tất](#thread-fundamentals-003)
- [Tranh chấp dữ liệu, tính nguyên tử và khả năng nhìn thấy thay đổi](#thread-fundamentals-004)
- [Quan hệ happens-before khi gửi tác vụ và nhận kết quả](#thread-fundamentals-005)
- [Khóa, biến atomic và cấu trúc dữ liệu đồng thời](#thread-fundamentals-006)
- [Chờ tài nguyên khác với sử dụng CPU](#thread-fundamentals-007)
- [Deadlock, cạn luồng và giới hạn của việc tăng số luồng](#thread-fundamentals-008)

## <a id="thread-fundamentals-001">Runnable, Callable, Executor và ExecutorService</a>

<details>
<summary>Xem chi tiết</summary>

# Runnable, Callable, Executor và ExecutorService

## 1. Bức tranh tổng thể: công việc khác với luồng thực thi

Khi viết chương trình Java, có hai câu hỏi khác nhau:

1. **Cần làm việc gì?** Ví dụ: xử lý một tệp, gọi dịch vụ bên ngoài, tính toán một báo cáo.
2. **Ai thực thi và quản lý việc đó?** Ví dụ: một luồng do ta tạo trực tiếp hoặc một nhóm luồng được tái sử dụng.

`Runnable` và `Callable` mô tả **tác vụ (task)**. `Executor` và `ExecutorService` mô tả **cơ chế thực thi tác vụ**. Tách hai vai trò này giúp tránh tạo `new Thread(...)` cho từng việc, quản lý tài nguyên tập trung, thu nhận kết quả và kết thúc chương trình có kiểm soát.

```text
Mã gọi (caller)
   |
   | tạo Runnable / Callable
   v
Tác vụ: "làm gì?"
   |
   | execute(...) / submit(...)
   v
Executor / ExecutorService: "chạy bằng cách nào?"
   |
   +--> worker thread 1
   +--> worker thread 2      (nếu dùng pool)
   +--> hàng đợi chờ xử lý
              |
              v
          Future<T>          (khi submit)
              |
              v
      kết quả / lỗi / hủy
```

Một tác vụ **không đồng nghĩa** một luồng: nhiều tác vụ có thể chạy lần lượt trên cùng một worker thread; ngược lại, nhiều tác vụ có thể chạy đồng thời trên các worker khác nhau. Và một `Executor` hoàn toàn có thể chạy tác vụ ngay trên luồng gọi, nên tên API không tự bảo đảm chạy bất đồng bộ.

Trong module Spring Framework về concurrency, đây là nền tảng để hiểu các cơ chế cao hơn như `TaskExecutor`, `ThreadPoolTaskExecutor` và `@Async`. Trước hết cần nắm chính xác hợp đồng của các API Java chuẩn.

### Mục tiêu của bài

Sau bài này, bạn có thể chọn đúng loại tác vụ, gửi tác vụ tới bộ thực thi, lấy kết quả hoặc ngoại lệ qua `Future`, xử lý ngắt/hủy, đóng bộ thực thi an toàn, và nhận ra các lỗi dễ gặp khi dùng thread pool.

## 2. Bốn giao diện cốt lõi

| Thành phần | Vai trò | Phương thức cốt lõi | Kết quả và ngoại lệ |
|---|---|---|---|
| `Runnable` | Mô tả việc cần chạy nhưng không trả về giá trị | `void run()` | Không khai báo checked exception từ `run()` |
| `Callable<V>` | Mô tả việc tính toán có kết quả | `V call() throws Exception` | Trả về `V`; có thể ném checked exception |
| `Executor` | Nhận `Runnable` và quyết định cách thực thi | `void execute(Runnable)` | Không trả về `Future`; không bắt buộc tạo luồng mới |
| `ExecutorService` | Mở rộng `Executor` bằng quản lý vòng đời và tác vụ có kết quả | `submit(...)`, `shutdown()`, `awaitTermination(...)` | Trả về `Future` từ `submit`; hỗ trợ hủy, chờ, đóng |

Quan hệ kiểu:

```text
Runnable       Callable<V>
    |               |
    +-- tác vụ -----+

Executor
   ^
   |
ExecutorService   (extends Executor)
   |
   +-- execute(Runnable)
   +-- submit(Runnable | Callable<V>)
   +-- shutdown / shutdownNow / awaitTermination
   +-- invokeAll / invokeAny
```

`ExecutorService` là **giao diện**. `Executors` (số nhiều) là **lớp tiện ích/factory** có các phương thức tạo bộ thực thi, chẳng hạn `Executors.newFixedThreadPool(2)`. Hai tên này rất dễ nhầm.

## 3. Runnable: tác vụ không cần trả về giá trị

`Runnable` là functional interface có một phương thức trừu tượng `run()`. Có thể triển khai bằng lớp thường, lớp ẩn danh hoặc lambda. Dùng khi hành vi tạo ra tác động phụ có chủ đích, như ghi log, cập nhật trạng thái hoặc gửi thông báo, và người gọi không cần một giá trị trả về từ `run()`.

Ví dụ độc lập, chạy được trên Java 17:

**Tệp `RunnableDemo.java`**

```java
public class RunnableDemo {
    public static void main(String[] args) throws InterruptedException {
        Runnable task = () ->
                System.out.println("Tác vụ chạy trên: "
                        + Thread.currentThread().getName());

        Thread worker = new Thread(task, "worker-1");
        worker.start();
        worker.join();

        System.out.println("Main đã đợi xong");
    }
}
```

Biên dịch và chạy:

```bash
javac RunnableDemo.java
java RunnableDemo
```

Kết quả chính xác:

```text
Tác vụ chạy trên: worker-1
Main đã đợi xong
```

Ở đây:

- `task` chỉ chứa hành vi. Tạo đối tượng `Runnable` **chưa** tạo luồng và **chưa** thực thi.
- `new Thread(task, "worker-1")` tạo đối tượng `Thread` gắn với tác vụ.
- `start()` yêu cầu JVM khởi chạy một luồng mới để gọi `run()`. Nếu gọi trực tiếp `task.run()` thì tác vụ chạy **đồng bộ trên luồng hiện tại**.
- `join()` chờ worker kết thúc; nhờ vậy dòng cuối luôn xuất hiện sau dòng do worker in ra. Không nên dùng `Thread.sleep(...)` để đoán khi nào tác vụ xong.

Giới hạn: chữ ký `run()` không có giá trị trả về và không khai báo `throws Exception`. Ta vẫn có thể xử lý checked exception *bên trong* `run()`, và runtime exception vẫn có thể phát sinh. Nếu cần kết quả tính toán hoặc muốn đưa lỗi checked exception tới phía gọi theo hợp đồng tác vụ, `Callable<V>` thích hợp hơn.

## 4. Callable<V>: tác vụ có kết quả và có thể báo lỗi

`Callable<V>` cũng là functional interface, nhưng `call()` trả về `V` và có thể khai báo `throws Exception`:

```java
import java.util.concurrent.Callable;

Callable<Integer> sum = () -> {
    int result = 0;
    for (int i = 1; i <= 5; i++) {
        result += i;
    }
    return result;
};
```

Đoạn trên là **một khai báo để đặt trong phương thức hoặc lớp phù hợp**, chưa phải chương trình độc lập. `Callable<Integer>` biểu diễn phép tính trả về `15`. `Thread` không có constructor nhận trực tiếp `Callable`; cách phổ biến là gửi `Callable` tới `ExecutorService.submit(...)` và lấy `Future<Integer>`.

Tham số kiểu có ý nghĩa: `Callable<String>` cho `Future<String>`; `Callable<Integer>` cho `Future<Integer>`. Bộ thực thi vẫn không biết công việc nghiệp vụ là gì; nó chỉ nhận tác vụ và bố trí chạy.

**Quy tắc chọn nhanh:** Nếu không cần giá trị thì bắt đầu bằng `Runnable`; nếu cần nhận kết quả hoặc biểu diễn lỗi checked exception từ tác vụ, chọn `Callable<V>`. Dù tác vụ là `Runnable`, `submit(Runnable)` vẫn cung cấp `Future<?>` để theo dõi hoàn tất hoặc lỗi.

## 5. Executor: tách nơi gửi công việc khỏi nơi chạy

`Executor` có đúng một phương thức trừu tượng:

```java
void execute(Runnable command);
```

Bên gọi yêu cầu “hãy thực thi tác vụ này”; bộ thực thi quyết định chạy ngay, tạo luồng hay xếp hàng. Hợp đồng `Executor` **không** cam kết tác vụ chạy bất đồng bộ hoặc có thread pool.

Ví dụ bộ thực thi chạy **ngay trên luồng gọi**:

**Tệp `ExecutorDemo.java`**

```java
import java.util.concurrent.Executor;

public class ExecutorDemo {
    public static void main(String[] args) {
        Executor inlineExecutor = Runnable::run;
        inlineExecutor.execute(() ->
                System.out.println("Tên luồng: "
                        + Thread.currentThread().getName()));
    }
}
```

```bash
javac ExecutorDemo.java
java ExecutorDemo
```

Kết quả:

```text
Tên luồng: main
```

Lệnh `Runnable::run` tạo một triển khai `Executor` thực hiện `command.run()` ngay trong luồng gọi. Điều này hữu ích khi minh họa hợp đồng hoặc viết kiểm thử đơn giản, nhưng không đem lại chạy song song. Một `Executor` khác có thể dùng worker pool mà không phải sửa code tạo tác vụ.

Phân biệt `execute` với `submit`:

- `execute(Runnable)` là phương thức của `Executor`; không trả về đối tượng theo dõi công việc.
- `submit(...)` do `ExecutorService` bổ sung; trả về `Future` để lấy kết quả, quan sát lỗi, chờ hoặc yêu cầu hủy.
- Cả hai có thể từ chối tác vụ mới, ví dụ khi bộ thực thi đã đóng; trường hợp phổ biến là `RejectedExecutionException`. Một số cách cấu hình pool còn có chính sách từ chối riêng.

## 6. ExecutorService: gửi việc, nhận Future và quản lý vòng đời

`ExecutorService` thường được hiện thực bằng thread pool, cho phép nhiều tác vụ chia sẻ một số worker. Ví dụ sau dùng factory `Executors.newFixedThreadPool(2)`.

**Tệp `ExecutorServiceDemo.java`**

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExecutorServiceDemo {
    public static void main(String[] args)
            throws InterruptedException, ExecutionException {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Runnable greeting =
                    () -> System.out.println("Runnable hoàn thành");

            Callable<Integer> calculation = () -> {
                int sum = 0;
                for (int i = 1; i <= 5; i++) {
                    sum += i;
                }
                return sum;
            };

            Future<?> greetingDone = executor.submit(greeting);
            Future<Integer> result = executor.submit(calculation);

            greetingDone.get();
            System.out.println("Tổng = " + result.get());
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac ExecutorServiceDemo.java
java ExecutorServiceDemo
```

Kết quả luôn theo thứ tự:

```text
Runnable hoàn thành
Tổng = 15
```

Tác vụ `calculation` có thể chạy hoặc xong trước `greeting` vì pool có hai worker. Tuy nhiên, dòng “Tổng” chỉ được in sau khi `greetingDone.get()` trả về, nên thứ tự **hai dòng đầu ra** ở ví dụ này là xác định.

Ý nghĩa từng bước:

1. `newFixedThreadPool(2)` tạo một pool có tối đa hai worker hoạt động đồng thời; nếu tác vụ đến nhiều hơn khả năng xử lý, các tác vụ khác chờ trong hàng đợi.
2. `submit(greeting)` và `submit(calculation)` xếp lịch thực thi, trả về ngay `Future<?>` và `Future<Integer>`, trừ trường hợp bị từ chối hoặc có lỗi khi gửi.
3. `greetingDone.get()` chờ `Runnable` hoàn thành; nếu thành công, giá trị nhận được từ overload `submit(Runnable)` là `null`.
4. `result.get()` chờ và lấy giá trị `15`; nếu `calculation` ném ngoại lệ, `get()` ném `ExecutionException` thay cho giá trị.
5. `shutdown()` trong `finally` thông báo ngừng nhận việc mới và tiếp tục xử lý các việc đã được chấp nhận. Ở ví dụ này, cả hai việc đã hoàn tất trước khi tới `finally`.

Đừng quên rằng `ExecutorService` quản lý **nhiều tác vụ**, không phải “mỗi `submit` tạo một thread”. Cũng không nên dựa vào tên worker thread để xác định tác vụ nào sẽ chạy trước.

### Các overload submit thường dùng

| Cách gọi | Kiểu trả về | `Future.get()` khi hoàn tất bình thường |
|---|---|---|
| `submit(Runnable task)` | `Future<?>` | `null` |
| `submit(Runnable task, T result)` | `Future<T>` | Giá trị `result` đã cung cấp, không phải return từ `run()` |
| `submit(Callable<T> task)` | `Future<T>` | Giá trị mà `call()` trả về |

`submit(Runnable, result)` hữu ích khi cần đính kèm một kết quả định sẵn sau khi tác vụ không trả về giá trị hoàn thành thành công. Nó không thay thế `Callable` cho phép tính tạo kết quả mới.

## 7. Future: trạng thái, lỗi, chờ và hủy

`Future<T>` là **tay cầm (handle)** đại diện cho kết quả của một tác vụ đã gửi; nó không phải bản thân tác vụ hay một thread.

- `get()` chặn luồng gọi cho đến khi xong, bị hủy hoặc thất bại. Kết quả thành công là `T`; tác vụ thất bại dẫn đến `ExecutionException` (nguyên nhân gốc nằm ở `getCause()`); tác vụ bị hủy dẫn đến `CancellationException`.
- `get(timeout, unit)` chỉ chờ tối đa khoảng thời gian chỉ định; hết thời gian ném `TimeoutException`. **Hết thời gian chờ không tự động hủy tác vụ**.
- `isDone()` đúng khi tác vụ đã hoàn tất bình thường, thất bại **hoặc bị hủy**. Vì vậy “done” không có nghĩa là “thành công”.
- `isCancelled()` báo tác vụ đã được hủy.
- `cancel(false)` cố hủy tác vụ chưa bắt đầu, nhưng không yêu cầu ngắt tác vụ đang chạy. `cancel(true)` có thể yêu cầu ngắt thread đang chạy nếu tác vụ được thực hiện trên thread và việc hủy thành công; không có bảo đảm buộc tác vụ dừng.

Ví dụ dùng thời gian chờ (một đoạn mã đặt trong phương thức có xử lý `InterruptedException` và `ExecutionException`):

```java
try {
    Integer value = future.get(2, TimeUnit.SECONDS);
    System.out.println(value);
} catch (TimeoutException timeout) {
    future.cancel(true);
    System.out.println("Quá hạn, đã yêu cầu hủy tác vụ");
}
```

Ở đây `future` là `Future<Integer>` đã có và cần import `java.util.concurrent.*`. Đây **không phải chương trình độc lập**. Dòng thông báo chỉ xác nhận lời gọi yêu cầu hủy được thực hiện, không chứng minh tác vụ đã dừng; trong chương trình thật cần kiểm tra giá trị boolean từ `cancel(true)` nếu trạng thái đó quan trọng.

### Lỗi từ task đi đâu?

Khi `submit` một `Runnable`/`Callable`, ngoại lệ phát sinh trong tác vụ được giữ để `Future.get()` báo lại qua `ExecutionException`. Vì vậy gọi `submit` rồi bỏ qua `Future` có thể làm lỗi nghiệp vụ không được quan sát ở nơi gọi.

**Tệp `FailureDemo.java`**

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FailureDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<Integer> faultyTask = () -> {
                throw new IllegalStateException("Sai trạng thái");
            };

            Future<Integer> future = executor.submit(faultyTask);
            try {
                System.out.println(future.get());
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                System.out.println(
                        cause.getClass().getSimpleName()
                                + ": " + cause.getMessage());
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac FailureDemo.java
java FailureDemo
```

Kết quả:

```text
IllegalStateException: Sai trạng thái
```

`get()` cũng có thể ném `InterruptedException` khi luồng **đang chờ** bị ngắt; điều này khác với một ngoại lệ ném **bên trong tác vụ** và được bọc trong `ExecutionException`.

Với `execute(Runnable)`, không có `Future` để lấy lỗi. Hành vi truyền hoặc báo runtime exception phụ thuộc cách `Executor` thực thi: bộ thực thi chạy inline có thể ném lỗi về luồng gọi, còn worker thread có thể kết thúc bất thường và đi qua cơ chế `UncaughtExceptionHandler`. Đừng mặc định `execute` và `submit` báo lỗi giống nhau.

### Hủy tác vụ cần hợp tác

Ngắt (`interrupt`) là tín hiệu hợp tác, không phải thao tác giết thread. Tác vụ chặn ở các API có hỗ trợ ngắt, chẳng hạn `Thread.sleep` hoặc `BlockingQueue.take`, thường nhận `InterruptedException`. Một tác vụ xử lý dài nên kiểm tra ngắt định kỳ và dừng hoặc dọn dẹp hợp lý.

Ví dụ **đoạn thân một Runnable**, không phải chương trình độc lập:

```java
Runnable responsiveTask = () -> {
    try {
        for (int i = 0; i < 100; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            Thread.sleep(100);
            // Thực hiện một phần việc ngắn tại đây.
        }
    } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        // Giữ lại tín hiệu ngắt để tầng gọi có thể nhận biết.
    }
};
```

Khi bắt `InterruptedException`, trạng thái ngắt thường đã bị xóa; khôi phục bằng `Thread.currentThread().interrupt()` nếu không thể ném tiếp và chính sách xử lý yêu cầu bảo toàn tín hiệu. Không nuốt ngắt rồi chạy vô hạn. Đồng thời không lạm dụng `Thread.stop()`, vì đây không phải cách quản lý hủy an toàn.

## 8. Đóng ExecutorService đúng cách

Thread pool có thể giữ các worker tồn tại sau khi `main` chạy xong; không đóng pool sở hữu có thể khiến ứng dụng tiếp tục sống hoặc rò rỉ tài nguyên.

- `shutdown()`: ngừng nhận tác vụ mới, cho các tác vụ đã gửi tiếp tục chạy; **không chờ** đến lúc tất cả kết thúc.
- `awaitTermination(timeout, unit)`: chờ sau khi đã bắt đầu đóng; trả `true` nếu đã kết thúc trong thời hạn, `false` nếu hết hạn.
- `shutdownNow()`: cố ngừng các tác vụ đang chạy bằng ngắt thread và trả về danh sách tác vụ đang đợi chưa bắt đầu. Đây là nỗ lực tốt nhất, **không bảo đảm dừng ngay**.
- `isShutdown()`: việc đóng đã bắt đầu; `isTerminated()`: mọi tác vụ đã kết thúc sau khi đóng. Hai trạng thái không đồng nghĩa.

Ví dụ **phương thức tiện ích để đặt trong lớp Java**, dùng khi tác vụ có thể chạy lâu:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

// Phương thức đặt bên trong một class:
static void stopGracefully(ExecutorService executor) {
    executor.shutdown();
    try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Vẫn còn tác vụ chưa kết thúc");
            }
        }
    } catch (InterruptedException interrupted) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

Hàm này bảo vệ luồng gọi trước việc chờ vô hạn và cố dọn dẹp khi thời gian chờ hết. `shutdownNow()` không cưỡng ép tác vụ bất hợp tác; nếu tác vụ bỏ qua mọi ngắt, sau thời hạn vẫn có thể còn hoạt động. Thời hạn 5 giây chỉ là ví dụ, phải chọn theo nghiệp vụ. Đối với Java 17, nên dùng `try/finally` hoặc một cơ chế quản lý vòng đời rõ ràng; đừng giả định `ExecutorService` có thể dùng trực tiếp với `try-with-resources` như các phiên bản JDK mới hơn.

**Quyền sở hữu quan trọng:** Code tạo `ExecutorService` thường có trách nhiệm đóng nó. Nếu ứng dụng Spring tạo và quản lý `ThreadPoolTaskExecutor` như một bean, hãy để vòng đời của container quản lý; không tự ý `shutdown` một pool đang được những thành phần khác dùng chung.

## 9. Chọn loại pool, độ song song và hàng đợi

Một số factory của `Executors` thường gặp:

| Factory | Hành vi tiêu biểu | Điểm cần chú ý |
|---|---|---|
| `newSingleThreadExecutor()` | Một worker chạy từng tác vụ tuần tự | Không nên đặt tác vụ chờ tác vụ khác cùng pool; một tác vụ treo chặn cả hàng |
| `newFixedThreadPool(n)` | Tối đa `n` worker thực thi song song | Hàng đợi mặc định không giới hạn dung lượng theo cấu hình; tải dồn có thể làm bộ nhớ tăng |
| `newCachedThreadPool()` | Tạo thêm worker khi cần, tái sử dụng worker nhàn rỗi | Số worker có thể tăng rất lớn khi tải mạnh; cần cân nhắc giới hạn tài nguyên |

Kích thước pool không phải “càng lớn càng nhanh”. Tác vụ tính toán thuần CPU chịu giới hạn số lõi và chi phí chuyển ngữ cảnh. Tác vụ đợi I/O có thể hưởng lợi từ mức đồng thời cao hơn, nhưng còn giới hạn kết nối, tốc độ dịch vụ ngoài, bộ nhớ và thời gian đáp ứng. Hãy đo hiệu năng với tải thực tế.

`Executors.newFixedThreadPool(n)` thuận tiện cho minh họa, nhưng hàng đợi không có giới hạn cấu hình sẵn nên chưa đủ an toàn cho mọi hệ thống nhận lưu lượng lớn. Trong ứng dụng vận hành, cân nhắc `ThreadPoolExecutor` với `corePoolSize`, `maximumPoolSize`, hàng đợi có dung lượng rõ ràng và chính sách từ chối.

Ví dụ tạo pool **có hàng đợi giới hạn** (đoạn khai báo trong phương thức, cần `import java.util.concurrent.*;`):

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        2, 4,
        30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(100),
        new ThreadPoolExecutor.AbortPolicy()
);
```

Pool trên có tối đa bốn worker, hàng đợi tối đa 100 tác vụ đang chờ, và có thể ném `RejectedExecutionException` khi không thể nhận thêm. Nó vẫn cần `shutdown()` khi không còn dùng. Với `ThreadPoolExecutor`, tác vụ sẽ ưu tiên sử dụng số worker lõi rồi được đưa vào hàng đợi; khi hàng đợi đầy mới cân nhắc tăng worker tới `maximumPoolSize`, nên cấu hình queue ảnh hưởng mạnh đến hành vi mở rộng.

Nếu cần thực thi sau độ trễ hoặc theo chu kỳ, đó là nhiệm vụ của `ScheduledExecutorService` (một nhánh chuyên biệt). Đừng dùng vòng lặp `Thread.sleep` để tự chế bộ lập lịch trong mọi trường hợp.

## 10. Điều phối nhiều tác vụ

Khi có một nhóm tác vụ `Callable<T>`, ngoài `submit` từng cái, `ExecutorService` còn hỗ trợ:

- `invokeAll(collection)`: gửi cả nhóm và chờ tất cả hoàn tất, trả `List<Future<T>>` theo **thứ tự đầu vào**, không phải thứ tự hoàn tất; từng Future có thể giữ lỗi hoặc trạng thái hủy. Có phiên bản với timeout.
- `invokeAny(collection)`: trả kết quả của **một tác vụ hoàn thành thành công**, không nhất thiết là tác vụ được gửi đầu tiên; các tác vụ còn lại chưa xong được yêu cầu hủy. Nếu không có tác vụ thành công, phương thức báo lỗi thích hợp.

Ví dụ độc lập:

**Tệp `InvokeAllDemo.java`**

```java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class InvokeAllDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> 2 * 2,
                    () -> 3 * 3
            );
            List<Future<Integer>> results = executor.invokeAll(tasks);

            int total = 0;
            for (Future<Integer> result : results) {
                total += result.get();
            }
            System.out.println("Tổng = " + total);
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac InvokeAllDemo.java
java InvokeAllDemo
```

Kết quả:

```text
Tổng = 13
```

Một nhóm tác vụ độc lập có thể được phân công chạy song song, nhưng phần gom kết quả vẫn phải xử lý lỗi và thời gian chờ. `Future` phù hợp để chờ/lấy kết quả đơn lẻ; nếu cần ghép chuỗi nhiều bước bất đồng bộ, kết hợp kết quả hoặc xử lý chuỗi sự kiện linh hoạt, có thể tìm hiểu thêm `CompletableFuture` ở bài riêng.

## 11. Đồng bộ dữ liệu và những bẫy khó thấy

### Chia sẻ trạng thái giữa worker không tự an toàn

Pool chỉ điều phối việc chạy, không tự bảo vệ biến dùng chung. Ví dụ nhiều tác vụ cùng thực hiện `count++` trên một `int` có thể mất cập nhật vì phép tăng gồm nhiều thao tác đọc–tính–ghi. Dùng thiết kế tránh chia sẻ dữ liệu có thể thay đổi, hoặc dùng cơ chế phù hợp như `AtomicInteger`, đồng bộ hóa và collection hỗ trợ concurrent.

JDK quy định quan hệ **happens-before** quan trọng đối với `ExecutorService`: hành động trên luồng gửi xảy ra trước `submit` được nhìn thấy bởi tác vụ; hành động của tác vụ xảy ra trước khi phía gọi nhận kết quả qua `Future.get()`. Nhờ đó ta có quan hệ công bố dữ liệu và quan sát kết quả rõ ràng. Tuy nhiên, quy tắc này **không thay thế** cơ chế phối hợp cho nhiều tác vụ đồng thời sửa cùng một biến.

### Đừng chờ chính công việc đang xếp hàng sau mình

Giả sử dùng `newSingleThreadExecutor()`:

1. Tác vụ A đang chiếm worker duy nhất.
2. A lại `submit` tác vụ B vào **cùng pool** và gọi `B.get()` để chờ.
3. B nằm trong hàng đợi, chờ A nhường worker; A lại chờ B ⇒ bế tắc (deadlock).

Mã minh họa trường hợp lỗi, **không chạy nguyên đoạn như một ví dụ hoàn tất**:

```java
ExecutorService single = Executors.newSingleThreadExecutor();
single.submit(() -> {
    Future<Integer> inner = single.submit(() -> 42);
    try {
        System.out.println(inner.get()); // Có thể chờ vô hạn.
    } catch (InterruptedException | ExecutionException e) {
        Thread.currentThread().interrupt(); // Minh họa tối giản; xem lưu ý dưới.
    }
});
```

Đoạn này chỉ minh họa vị trí chờ sai và giả định đã import `java.util.concurrent.*`; cố ý không có lệnh chạy. Trong code thật, chỉ khôi phục trạng thái ngắt khi bắt `InterruptedException`; còn `ExecutionException` phải xử lý theo nguyên nhân lỗi riêng. Cách sửa thiết kế là tránh chặn worker khi đang chờ công việc chỉ chính pool đó mới có thể thực hiện, hoặc tổ chức lại tác vụ để không có phụ thuộc vòng.

### Những sai lầm thường gặp

| Sai lầm | Vì sao nguy hiểm | Hướng sửa |
|---|---|---|
| Gọi `run()` và tưởng đã tạo luồng mới | `run()` chạy như phương thức bình thường | Dùng `Thread.start()` hoặc gửi qua executor phù hợp |
| `submit` rồi bỏ `Future` và không có xử lý lỗi | Lỗi có thể chỉ nằm trong Future | `get()`, ghi nhận hoàn tất/lỗi hoặc dùng cơ chế quan sát rõ ràng |
| `get()` ngay sau mỗi `submit` trong vòng lặp | Có thể biến xử lý dự kiến song song thành tuần tự | Gửi tất cả việc trước, sau đó thu kết quả |
| Cứ gọi `get()` trong worker chờ việc từ cùng pool | Có thể bế tắc khi pool hết worker | Loại bỏ chờ vòng; thiết kế lại phụ thuộc |
| Không gọi `shutdown()` cho pool tự tạo | Worker/tài nguyên tồn tại ngoài ý muốn | Xác định owner, đóng trong `finally` |
| Tin `shutdownNow()` buộc mọi tác vụ dừng | Ngắt chỉ là yêu cầu hợp tác | Viết task đáp ứng interrupt và có timeout hợp lý |
| Dùng nhiều worker cùng sửa `HashMap`/biến chung không bảo vệ | Race condition, dữ liệu sai hoặc lỗi | Tránh shared mutable state; dùng đồng bộ hay concurrent collection |
| Tạo pool mới cho từng request | Tốn thread và tài nguyên, khó kiểm soát tải | Tái sử dụng pool do ứng dụng quản lý |
| Cho rằng `execute` luôn bất đồng bộ | `Executor` có thể chạy inline | Kiểm tra triển khai và hợp đồng thực tế |

**Lưu ý về xử lý ngoại lệ hỗn hợp:** Nếu bắt gộp `InterruptedException` với một ngoại lệ khác, không được gọi `interrupt()` cho cả hai loại một cách máy móc. Hãy tách catch và chỉ khôi phục cờ ngắt khi thực sự gặp `InterruptedException`.

## 12. Liên hệ với Spring Framework

Spring cung cấp `TaskExecutor` để trừu tượng hóa việc thực thi `Runnable`, và `ThreadPoolTaskExecutor` là triển khai phổ biến bọc thread pool có thể cấu hình kích thước, hàng đợi và vòng đời trong container. Các khái niệm Java cơ sở vẫn giữ nguyên:

| Java concurrency | Ý nghĩa khi sang Spring |
|---|---|
| `Runnable` / `Callable` | Phần công việc cần chạy; logic nghiệp vụ nên tách khỏi việc quản lý worker |
| `Executor.execute(Runnable)` | Tương ứng tư duy gửi tác vụ cho `TaskExecutor` |
| `ExecutorService` / thread pool | Cơ sở hiểu `ThreadPoolTaskExecutor`, năng lực xử lý, queue và chính sách từ chối |
| `Future<T>` | Hiểu kết quả hoặc lỗi từ công việc được gửi bất đồng bộ; không nên chặn tùy tiện |
| `shutdown` / vòng đời | Pool do Spring quản lý nên đóng theo lifecycle của application context |

`@Async` hỗ trợ chạy phương thức được cấu hình qua bộ thực thi của Spring, nhưng việc hoạt động phụ thuộc cơ chế proxy và cấu hình async; gọi nội bộ một phương thức `@Async` qua `this` thường không đi qua proxy nên không tạo chuyển luồng như mong đợi. Ngoài ra, chuyển sang worker khác không có nghĩa các giá trị trong `ThreadLocal`, ngữ cảnh request hay transaction trên luồng gọi sẽ tự động đi theo. Các trường hợp cần truyền ngữ cảnh phải được thiết kế rõ ràng.

Trong ứng dụng web, không nên dùng `@Async` như cách vô điều kiện tăng tốc mọi request: công việc vẫn cần CPU, connection, queue, quyền truy cập tài nguyên và xử lý lỗi. Nếu tác vụ cần độ bền qua lần khởi động lại, thread pool nằm trong tiến trình chưa thay thế được hàng đợi tác vụ bền vững.

## 13. Cách chọn công cụ theo yêu cầu

| Yêu cầu thực tế | Lựa chọn ban đầu | Lý do |
|---|---|---|
| Mô tả một thao tác không có kết quả | `Runnable` | Gọn và rõ ý định |
| Tính toán trả về một giá trị hoặc checked exception | `Callable<T>` | `call()` có kiểu trả về và `throws Exception` |
| Chỉ muốn gửi `Runnable`, không cần quản lý kết quả/vòng đời | `Executor` | Hợp đồng tối thiểu, dễ thay thế triển khai |
| Muốn lấy kết quả, chờ hoàn tất hoặc kiểm soát đóng pool | `ExecutorService` + `Future` | Có `submit` và lifecycle |
| Nhiều việc độc lập cần kết quả | `ExecutorService.invokeAll` hoặc nhiều `Future` | Có thể chạy đồng thời, sau đó gom kết quả |
| Ứng dụng Spring cần pool dùng chung | Bean `ThreadPoolTaskExecutor` được cấu hình | Vòng đời tập trung và có giới hạn tài nguyên |

Có thể ghi nhớ chuỗi tư duy: **chọn task → chọn executor → gửi tác vụ → quan sát hoàn tất/lỗi → đóng đúng owner**. Câu hỏi “cần thread mới không?” thường nên đến sau câu hỏi “tác vụ nào và ai chịu trách nhiệm quản lý chúng?”.

## 14. Tự luyện tập và câu hỏi kiểm tra

1. Tạo `Runnable` in tên thread và so sánh hành vi khi gọi trực tiếp `run()` với khi gửi qua `ExecutorService.execute(...)`. Có phải mọi lần dùng `Executor` đều sang thread khác không? Giải thích bằng `ExecutorDemo`.
2. Viết `Callable<Long>` tính tổng từ 1 đến 100 và gửi qua `newSingleThreadExecutor()`. Kiểm tra `Future.get()` trả `5050`; luôn đóng executor ở `finally`.
3. Gửi ba `Callable<Integer>` vào pool hai worker bằng `invokeAll`. Dự đoán thứ tự trả `List<Future<Integer>>` khi tác vụ thứ ba kết thúc nhanh nhất.
4. Thay một `Callable` bằng tác vụ ném `IllegalArgumentException`. Kiểm tra `Future.isDone()` và xử lý nguyên nhân qua `ExecutionException.getCause()`. Vì sao `isDone() == true` không nói rằng tác vụ thành công?
5. Viết một tác vụ ngủ từng đoạn ngắn, gọi `cancel(true)` khi nó đang chạy, rồi quan sát trạng thái. Vì sao chương trình phải xử lý `InterruptedException` và không thể dựa vào `cancel(true)` như một lệnh cưỡng chế?
6. Vẽ timeline của một pool một worker khi A nộp B rồi gọi `B.get()`. Giải thích deadlock và đề xuất cách tách phụ thuộc.

**Đáp án định hướng:** (1) `run()` chạy trên caller, `execute` tùy implementation; (2) tổng bằng 5050; (3) `invokeAll` trả các Future theo thứ tự danh sách đầu vào; (4) `isDone` cũng đúng với lỗi/hủy; (5) ngắt là hợp tác; (6) A giữ worker nên B không có cơ hội chạy. Hãy kiểm tra code bằng `javac`/`java` thay vì dựa vào `Thread.sleep` để đoán thứ tự thực thi.

## 15. Điều cần nhớ

- **`Runnable` và `Callable` mô tả công việc**; `Executor` và `ExecutorService` điều phối việc thực hiện.
- **`Callable<V>` trả giá trị** và có thể ném checked exception; `Runnable.run()` không có hai khả năng đó trong chữ ký.
- **`execute` không trả Future**, còn **`submit` trả Future** để chờ, nhận lỗi, lấy kết quả hoặc hủy.
- **`Future.get()` có thể chặn**, `isDone()` không đồng nghĩa thành công, và `cancel(true)` chỉ yêu cầu hủy/ngắt theo khả năng thực hiện.
- **Pool có vòng đời, hàng đợi và giới hạn tài nguyên**. Người sở hữu pool chịu trách nhiệm đóng nó; Spring có thể sở hữu và quản lý pool thay ứng dụng.
- **Đồng thời không tự động an toàn luồng**: phải thiết kế việc chia sẻ dữ liệu, thời gian chờ và phụ thuộc giữa tác vụ một cách rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-fundamentals-002">Luồng nền tảng, nhóm luồng và luồng ảo</a>

<details>
<summary>Xem chi tiết</summary>

# Luồng nền tảng, nhóm luồng và luồng ảo trong Java (Java 21+)

## Mục tiêu

Sau bài này, bạn có thể:

- Phân biệt **platform thread** (luồng nền tảng), **virtual thread** (luồng ảo) và **`ThreadGroup`** (nhóm luồng), hiểu vì sao nhóm luồng không phải loại luồng thứ ba.
- Tạo, đặt tên, chờ hoàn thành và hủy hợp tác một luồng thông qua `Thread`, `join()`, `interrupt()` hoặc `Future`.
- Giải thích JVM dùng carrier platform threads để thực thi virtual threads và lý do thao tác chờ I/O có thể nhả carrier.
- Dùng `ThreadGroup` đúng phạm vi, không nhầm nó với executor/thread pool hay quota.
- Chọn cách xử lý tải CPU-bound/I/O-bound phù hợp và kiểm tra cách Spring dùng executor.

**Phạm vi phiên bản:** Các ví dụ hoàn chỉnh dùng API **Java 21+**, khi virtual threads trở thành tính năng chính thức theo JEP 444. Pinning do monitor `synchronized` đã được thay đổi từ **Java 24** theo JEP 491; các lưu ý phiên bản được tách riêng ở phần dưới.

## 1. Bản đồ khái niệm: hai loại thread và một cấu trúc nhóm

Một process (tiến trình) JVM có heap chung cho nhiều thread. Mỗi thread có call stack và trạng thái thực thi riêng, nhưng có thể cùng truy cập đối tượng trong heap. Đây là nền tảng của concurrency và cũng là nguồn gốc của race condition, deadlock và nhu cầu đồng bộ hóa.

| Khái niệm | Bản chất | Bên nào lập lịch? | Mục đích |
| --- | --- | --- | --- |
| **Platform thread** | Một đối tượng `java.lang.Thread` gắn với luồng hệ điều hành trong suốt vòng đời | OS lên lịch luồng nền tảng | Chạy các công việc với số lượng luồng OS được kiểm soát |
| **Virtual thread** | Một đối tượng `java.lang.Thread` mà JVM mount lên một platform thread làm carrier khi cần thực thi | JVM lên lịch virtual threads; OS lên lịch carrier | Mở rộng lượng tác vụ đồng thời khi phần lớn thời gian là chờ |
| **`ThreadGroup`** | Đối tượng tổ chức platform threads theo cây nhóm cha–con | **Không lập lịch** | Khả năng nhóm/quan sát mang tính lịch sử; không quản lý task đầy đủ |

**Điểm chính:** Platform thread và virtual thread cùng triển khai giao diện lập trình `Thread`. `ThreadGroup` là đối tượng **gom luồng**, không phải thread, executor, pool hay loại lập lịch thứ ba.

### Vòng đời và trạng thái `Thread.State`

- `NEW`: thread đã được tạo nhưng chưa `start()`.
- `RUNNABLE`: đang chạy hoặc sẵn sàng được chạy theo mô hình trạng thái Java. Không có nghĩa nó chắc chắn đang chiếm CPU ở thời điểm quan sát.
- `BLOCKED`: đang chờ lấy **monitor lock** để vào `synchronized`. Không phải tên chung cho mọi loại chờ I/O.
- `WAITING`: chờ vô thời hạn, ví dụ `join()` không timeout hoặc một số thao tác đợi tín hiệu.
- `TIMED_WAITING`: chờ trong thời hạn, chẳng hạn `Thread.sleep(...)`, `join(timeout)`.
- `TERMINATED`: phương thức thực thi kết thúc. Thread đã kết thúc không thể `start()` lại.

`getState()` trả về snapshot có thể thay đổi ngay lập tức. Không dùng vòng lặp liên tục kiểm tra trạng thái để quyết định luồng nào phải chạy; dùng primitive đồng bộ thích hợp, `Future` hoặc completion notification.

## 2. Platform thread: vòng đời gắn với OS thread

Khi tạo platform thread, JVM dùng một luồng hệ điều hành tương ứng. Hệ điều hành lập lịch chạy, chuyển ngữ cảnh và quản lý stack/tài nguyên của nó. Việc tạo quá nhiều platform threads có thể ngốn bộ nhớ và gặp giới hạn hệ điều hành hoặc JVM. Kích thước và giới hạn chính xác tùy môi trường; không có con số vạn năng.

Ba cách tạo phổ biến:

- `new Thread(runnable, "name")`: cách truyền thống, tạo platform thread.
- `Thread.ofPlatform().name("name").start(runnable)`: builder Java 21 thể hiện rõ ý định.
- `Thread.ofPlatform().factory()`: cung cấp `ThreadFactory` cho thành phần quản lý task.

**`start()` khác `run()`:** `thread.start()` yêu cầu JVM bắt đầu một thread để thực thi `run()`; còn gọi trực tiếp `thread.run()` chỉ chạy phương thức trong **thread hiện tại**. Sau khi gọi `start()`, không thể gọi lại lần thứ hai trên cùng một đối tượng `Thread`.

### Ví dụ 1 — Hai loại thread cùng dùng API `Thread`

Lưu mã vào `ThreadKindsDemo.java`:

~~~java
public class ThreadKindsDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread platform = Thread.ofPlatform()
                .name("platform-demo")
                .start(() -> {
                    Thread current = Thread.currentThread();
                    System.out.println(current.getName()
                            + " | virtual=" + current.isVirtual());
                });
        platform.join();

        Thread virtual = Thread.ofVirtual()
                .name("virtual-demo")
                .start(() -> {
                    Thread current = Thread.currentThread();
                    System.out.println(current.getName()
                            + " | virtual=" + current.isVirtual());
                });
        virtual.join();
    }
}
~~~

Biên dịch và chạy (JDK 21+):

~~~bash
javac ThreadKindsDemo.java
java ThreadKindsDemo
~~~

Kết quả chính xác:

~~~text
platform-demo | virtual=false
virtual-demo | virtual=true
~~~

Ở đây lần `join()` đầu kết thúc trước khi tạo thread thứ hai, nên thứ tự in đã được cố định. Nếu khởi động cả hai rồi mới đợi, các dòng in có thể đảo thứ tự vì lập lịch không xác định.

`join()` đợi **thread cụ thể** kết thúc; `sleep()` chỉ tạm chờ trong thread hiện tại. `sleep()` không tạo quan hệ phối hợp công việc và không tự nhả monitor lock mà thread đang giữ. Đừng dùng `sleep(100)` để “đảm bảo” thread khác đã xong.

### Concurrency và thread safety

Heap chung nghĩa là `counter++` của nhiều thread có thể mất cập nhật, vì đó là chuỗi đọc–tính–ghi, không phải một phép tăng nguyên tử. Dùng `AtomicInteger`, `synchronized`, `Lock` hoặc thiết kế giảm chia sẻ trạng thái theo yêu cầu. `volatile` có tác dụng về visibility và ordering, nhưng không tự biến một chuỗi cập nhật thành nguyên tử. Virtual thread **không chữa được** race condition hay deadlock.

## 3. `ThreadGroup`: cách nhóm luồng kiểu cũ

`java.lang.ThreadGroup` biểu diễn một nhóm có tên và có thể chứa các nhóm con. Các thread group tạo thành cây; nhóm có giới hạn ưu tiên tối đa dành cho các platform threads trong nhóm. API này ra đời từ các phiên bản Java rất sớm, khi việc gom luồng và một số chức năng diagnostic còn dựa trên nhóm. Java SE 21 ghi rõ ứng dụng mới **hiếm khi cần** tạo `ThreadGroup`.

### Ví dụ 2 — Tạo một platform thread trong nhóm

Lưu thành `ThreadGroupDemo.java`:

~~~java
public class ThreadGroupDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadGroup group = new ThreadGroup("workers");

        Thread worker = new Thread(group, () -> {
            Thread current = Thread.currentThread();
            System.out.println(current.getThreadGroup().getName());
        }, "platform-worker");

        worker.start();
        worker.join();
        System.out.println("done");
    }
}
~~~

Chạy:

~~~bash
javac ThreadGroupDemo.java
java ThreadGroupDemo
~~~

Kết quả:

~~~text
workers
done
~~~

Constructor `new Thread(group, runnable, name)` tạo **platform thread** thành viên của `group`. Trong ví dụ, việc đọc tên nhóm được thực hiện khi thread đang sống. Với thread đã kết thúc, `getThreadGroup()` có thể trả về `null`, nên không dùng phương thức này như registry tồn tại mãi.

### Các giới hạn cần nhớ

1. `group.activeCount()` là **ước lượng tức thời** số platform threads còn sống trong nhóm và các nhóm con, **không phải** số task còn xếp hàng hay quota cứng.
2. `group.enumerate(array)` chép các **platform threads** đang sống vào mảng nhưng có race với quá trình thread bắt đầu/kết thúc; nếu mảng nhỏ, kết quả có thể thiếu.
3. `group.interrupt()` là API lịch sử; nó không tự xây dựng cơ chế chờ, timeout, kết quả, hủy có cấu trúc hoặc chính sách xử lý exception.
4. Thread group không phải sandbox hay cơ chế cách ly bảo mật, cũng không điều khiển scheduler của JVM.

Với **virtual thread**, Java runtime cung cấp **một nhóm đặc biệt** khi `getThreadGroup()` được gọi. API tạo virtual thread **không cho gán** `ThreadGroup` tùy chọn; virtual threads **không được tính** bởi `activeCount()`, **không được liệt kê** bởi `enumerate()` và **không bị ngắt** bởi `ThreadGroup.interrupt()`.

Nếu cần nhận task, giới hạn số worker, chờ kết quả và hủy, dùng `ExecutorService`/`Future` hoặc cấu trúc điều phối cấp cao. Nếu cần giới hạn số lời gọi DB/HTTP, đặt giới hạn **ở tài nguyên ấy**, ví dụ connection pool, semaphore hoặc rate limiter. Thread group không thể thay hai vai trò này.

## 4. Virtual thread: nhiều công việc chờ mà không giữ nhiều OS threads

**Virtual threads** được chuẩn hóa từ Java 21 (JEP 444, Project Loom). Chúng đặc biệt hữu ích với mô hình **một task/request một thread**, khi công việc chủ yếu đợi HTTP, JDBC, socket hoặc các thao tác blocking được JVM hỗ trợ.

### Mount, unmount và carrier

Một virtual thread có thể chạy trên carrier platform thread trong một khoảng thời gian, sau đó tạm nhường carrier:

1. Task được khởi tạo dưới dạng virtual thread với đối tượng `Thread` riêng.
2. JVM lên lịch và **mount** virtual thread lên một carrier. OS cấp CPU cho carrier.
3. Khi gặp thao tác blocking có thể unmount, JVM lưu trạng thái tiếp tục của virtual thread và **giải phóng carrier** để nó chạy virtual thread khác.
4. Khi điều kiện chờ đã sẵn sàng, JVM tiếp tục virtual thread, có thể mount lại trên **carrier khác**.

Sơ đồ khái niệm:

~~~text
Virtual A  [CPU] ------- chờ I/O -------- [CPU] --> xong
                   (A unmount)       (A mount lại)
Carrier 1  [A] [Virtual B] [Virtual C] [ ... ]
Carrier 2  [Virtual D] [Virtual E] [ ... ]
~~~

Sơ đồ chỉ diễn đạt sự chia sẻ carrier theo thời gian, không mô phỏng thứ tự lập lịch thực. Bên trong task, `Thread.currentThread()` **vẫn là virtual thread của task**, không phải carrier. Do đó đừng dùng tên carrier làm định danh request.

Số virtual threads có thể lớn hơn rất nhiều số carrier, nhưng chúng **vẫn tốn** bộ nhớ cho task, dữ liệu và stack; hệ thống bên ngoài vẫn có giới hạn file descriptor, kết nối và throughput. Virtual threads chủ yếu giúp **throughput của tải có thời gian chờ**, không trực tiếp giảm latency một truy vấn đơn lẻ hoặc tăng số lõi CPU.

### Tạo virtual thread qua API Java 21

Một số cách thông dụng:

~~~java
Thread direct = Thread.ofVirtual().name("request-1")
        .start(() -> System.out.println(Thread.currentThread().isVirtual()));

Thread simple = Thread.startVirtualThread(() -> {
    System.out.println("a virtual task");
});

ThreadFactory factory = Thread.ofVirtual().name("job-", 0).factory();
~~~

Đoạn này **chỉ minh họa API** (thêm `import java.util.concurrent.ThreadFactory;` vào chương trình chứa nó). Trong chương trình thực tế, cần quản lý kết thúc/hủy và xử lý lỗi của từng task, ví dụ bằng `join()` hoặc `Future`. `Thread.startVirtualThread(runnable)` tạo và bắt đầu virtual thread ngay. Tên của virtual thread có thể rỗng nếu không đặt tường minh.

Virtual threads là **daemon threads**, có độ ưu tiên cố định `Thread.NORM_PRIORITY` và không hỗ trợ cấu hình nhóm tùy chọn. Nếu ứng dụng chỉ còn daemon threads, JVM có thể kết thúc khi một số virtual tasks chưa xong; luôn có chiến lược chờ và quản lý vòng đời.

### Ví dụ 3 — Một virtual thread cho mỗi tác vụ

Lưu thành `VirtualTasksDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualTasksDemo {
    public static void main(String[] args) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> results = new ArrayList<>();

            for (int i = 1; i <= 3; i++) {
                int taskId = i;
                results.add(executor.submit(() -> {
                    Thread.sleep(50); // mô phỏng thời gian chờ
                    return "task-" + taskId
                            + " | virtual=" + Thread.currentThread().isVirtual();
                }));
            }

            for (Future<String> result : results) {
                System.out.println(result.get());
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac VirtualTasksDemo.java
java VirtualTasksDemo
~~~

Kết quả:

~~~text
task-1 | virtual=true
task-2 | virtual=true
task-3 | virtual=true
~~~

`newVirtualThreadPerTaskExecutor()` tạo **một virtual thread mới cho mỗi task**; ba task có thể chạy xen kẽ, nhưng `Future.get()` ở vòng lặp thứ hai lấy kết quả theo thứ tự submit, nên stdout đã nêu là xác định. `Thread.sleep(50)` chỉ mô phỏng chờ, **không phải benchmark I/O**. `try-with-resources` đóng executor và đợi task hoàn tất theo hợp đồng `ExecutorService.close()` của Java 21.

**Không pool virtual threads.** Đích thiết kế là tạo thread mới cho task rồi để nó kết thúc. Pool cố định thường dùng để kiểm soát số OS threads trong mô hình platform threads; pool virtual threads lại giới hạn số task một cách gián tiếp, làm phức tạp mô hình và không thay cho bảo vệ tài nguyên. Để chỉ cho phép 20 truy vấn DB đồng thời, dùng **connection pool/semaphore 20 slots**, không tạo virtual thread pool 20 worker.

## 5. Chọn mô hình theo bản chất công việc

| Tình huống | Hướng lựa chọn | Lý do |
| --- | --- | --- |
| Ít tác vụ tính toán dài, tiêu thụ CPU liên tục | Platform thread executor có độ song song kiểm soát | CPU cores giới hạn mức chạy song song thực |
| Nhiều request độc lập, thời gian chờ blocking I/O lớn | Virtual thread per request/task, cộng giới hạn downstream | Carrier có thể phục vụ task khác trong khi một task chờ |
| Cần nhận kết quả/hủy/timeout nhiều task | `ExecutorService`/`Future` và cơ chế hủy phù hợp | Thread group không sở hữu vòng đời task |
| Dịch vụ dùng event loop / Reactive Streams không chặn | Tiếp tục đánh giá trên mô hình non-blocking hiện tại | Virtual threads không tự tăng hiệu quả của pipeline đã không block |
| Backend chỉ cho 20 kết nối DB | Connection pool tối đa theo năng lực backend | 10.000 virtual threads không tạo thêm 10.000 kết nối hợp lệ |

Phân biệt hai phép đo: **throughput** là số task hoàn tất trong một khoảng thời gian; **latency** là thời gian hoàn tất một task. Virtual threads có thể cải thiện throughput của service khi trước đây nhiều OS threads bị giữ trong lúc chờ, nhưng không bảo đảm tăng tốc từng request. Với CPU-bound, nhiều virtual threads hơn **không tạo thêm CPU**.

Khi đánh giá thực tế, đo CPU, heap, GC, p95/p99 latency, throughput, timeout, tỉ lệ lỗi, số task đang chờ và tình trạng connection pool. Một ví dụ `sleep` trên máy cá nhân chỉ giúp học API, không chứng minh hiệu năng production.

## 6. Interruption, hủy hợp tác và bộ nhớ chia sẻ

`Thread.interrupt()` là **tín hiệu yêu cầu dừng**, không phải lệnh giết thread tức thì. Task phải tự kiểm tra cờ interrupted hoặc đi qua những lời gọi nhận interrupt như `sleep()`, `wait()`, `join()` và nhiều thao tác blocking của thư viện. Đây là quy tắc chung cho cả hai loại thread.

Ví dụ đoạn mã **bên trong một `Runnable`**:

~~~java
try {
    Thread.sleep(1_000);
    // tiếp tục công việc
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt(); // khôi phục cờ interrupt
    return; // thoát khỏi tác vụ một cách có chủ đích
}
~~~

Đây là fragment để giải thích xử lý exception, không phải class Java độc lập. Khi `InterruptedException` được ném, cờ interrupted thường đã được xóa; nếu bắt exception ở tầng này và không thể ném tiếp, khôi phục cờ cho tầng cao hơn. Trong phương thức khai báo `throws InterruptedException`, có thể cho exception lan truyền.

Với `Future`, `cancel(true)` yêu cầu interrupt task đang chạy, không bảo đảm dừng ngay một lời gọi native hay I/O không hỗ trợ interruption. Luôn đóng tài nguyên trong `finally`/`try-with-resources`, cấu hình timeout mạng/DB và thiết kế rollback khi bị hủy.

**Happens-before:** Theo Java Memory Model, hành động trước khi gọi `Thread.start()` có quan hệ happens-before với hành động của thread mới; hành động trong thread đã kết thúc có quan hệ happens-before với thread gọi `join()` và nhận biết kết thúc thành công. Nhưng hai thread chạy đồng thời vẫn cần cơ chế đồng bộ đúng đối với trạng thái chia sẻ, chẳng hạn `AtomicInteger` hoặc lock.

## 7. Các giới hạn và bẫy của virtual threads

### 7.1. Pinning theo phiên bản JDK

Virtual thread có thể bị **pinned** vào carrier: khi nó bị chặn, carrier cũng không rảnh để chạy virtual thread khác. Pinning thường tác động khả năng mở rộng dưới tải; bản thân nó không nhất thiết làm kết quả sai.

- **Java 21–23:** thao tác chặn trong vùng `synchronized` hoặc khi có khung chạy native/foreign function có thể pin carrier. Hạn chế giữ monitor qua lời gọi I/O lâu; có thể tách I/O ra khỏi đoạn khóa hoặc cân nhắc `ReentrantLock` với đo đạc phù hợp.
- **Java 24+:** **JEP 491** cho phép xử lý `synchronized` mà không pin carrier như trước. Pinning vẫn có thể phát sinh ở đường native/foreign và những trường hợp thư viện đặc thù. Không suy luận mọi blocking call chắc chắn unmount.

Không thay hàng loạt `synchronized` bằng `ReentrantLock` khi chưa đo. Dù không pin, **lock vẫn tuần tự hóa** truy cập dữ liệu đang được bảo vệ. Phân tích theo JDK và stack của ứng dụng thực.

### 7.2. `ThreadLocal`, logging context và trạng thái request

`ThreadLocal` và `InheritableThreadLocal` hoạt động trên virtual threads, nhưng mỗi task tạo một thread khác nhau, nên dùng `ThreadLocal` để cache tài nguyên lớn theo thread có thể rất tốn bộ nhớ và không mang lại tái sử dụng như platform thread pool. Với MDC, security context hoặc transaction context, cần hiểu cơ chế truyền/cleanup context của framework; tránh cho rằng context được tự động chia sẻ từ carrier. Khi phải tự quản lý, cleanup trong `finally` và `remove()`.

### 7.3. Bảo vệ downstream

Loại bỏ giới hạn về số OS threads có thể làm xuất hiện giới hạn khác sớm hơn: DB connection pool, upstream API, file descriptor, rate limit hoặc hàng đợi. Thiết kế với **timeout, capacity limit, backpressure và retry có kiểm soát** tại ranh giới tài nguyên. Virtual threads không tự áp dụng bất kỳ quota nào.

### 7.4. Theo dõi và chẩn đoán

- `Thread.currentThread().isVirtual()` giúp nhận biết thread đang chạy.
- `ThreadGroup.activeCount()`, `ThreadGroup.enumerate()` và `Thread.getAllStackTraces()` không cho danh sách đầy đủ các virtual threads; tránh dùng để đếm concurrent virtual tasks.
- Trên JVM có hỗ trợ, `jcmd <pid> Thread.dump_to_file -format=json <file>` có thể xuất thread dump dạng JSON; thay `<pid>` và `<file>` bằng thông số thật.
- JDK Flight Recorder (JFR) có sự kiện `jdk.VirtualThreadPinned` để tìm pinning. Trên Java 21 có thể dùng tham số `-Djdk.tracePinnedThreads=full` khi điều tra; ưu tiên công cụ/chỉ dẫn đúng phiên bản đang dùng.

## 8. Liên hệ đến Spring Framework và Spring Boot

Thread Java xuất hiện trong ứng dụng Spring thông qua request-handling, `@Async`, scheduler, listener và executor mà framework sử dụng.

- Với Spring Framework, `@Async` thực hiện công việc qua **executor được cấu hình/chọn**. Annotation không tự chứng minh rằng tác vụ đang chạy trên virtual thread; kiểm tra executor và dùng `Thread.currentThread().isVirtual()` ở điểm thực thi.
- **Spring Boot 3.2+** có hỗ trợ bật virtual threads với **Java 21+** bằng `spring.threads.virtual.enabled=true`. Các thành phần được Boot tự cấu hình và có hỗ trợ sẽ dùng virtual threads tùy phiên bản, nhưng bean `Executor` tùy chỉnh có thể thay đổi lựa chọn executor.
- Virtual threads là daemon; nếu scheduler/task daemon là thành phần giữ vòng đời ứng dụng, cần kiểm tra khả năng JVM thoát. Spring Boot cung cấp `spring.main.keep-alive=true` cho trường hợp thích hợp.
- JDBC/HTTP blocking có thể hưởng lợi nếu workload nhiều chờ, nhưng số kết nối DB và timeout vẫn là ranh giới cứng. Đối với Reactor/WebFlux, không được chặn event loop bừa bãi; virtual threads không tự thay thế thiết kế reactive.

Ví dụ cấu hình **cho ứng dụng Spring Boot hỗ trợ tính năng** trong `application.properties`:

~~~properties
spring.threads.virtual.enabled=true
~~~

Sau khi cấu hình, đo tải và xác minh executor thực tế, đặc biệt khi ứng dụng có custom executor, task scheduler hoặc web server tùy biến. Thuộc tính hợp lệ không đồng nghĩa mọi lời gọi trong ứng dụng đã tự chuyển qua virtual threads.

## 9. Những lỗi thường gặp

1. **Gọi `run()` rồi tưởng có thread mới.** Cần `start()`.
2. **Dùng `sleep()` để đảm bảo thứ tự tác vụ.** Cần `join()`, `Future.get()` hoặc công cụ đồng bộ; `sleep()` cũng không nhả monitor đang giữ.
3. **Coi `ThreadGroup` là thread pool/concurrency limiter.** Nhóm không quản lý hàng đợi task hay quota.
4. **Dùng `ThreadGroup.enumerate()` để đếm virtual threads.** API này bỏ qua virtual threads.
5. **Tin rằng virtual thread làm CPU-bound code nhanh hơn.** Các lõi CPU và thuật toán vẫn là giới hạn.
6. **Tạo virtual thread pool cố định cho mọi tác vụ.** Nên tạo virtual thread mỗi task và giới hạn tài nguyên downstream trực tiếp.
7. **Bỏ qua interruption và timeout.** Hệ thống có thể giữ tài nguyên hoặc để task chờ quá lâu.
8. **Cho rằng pinning của `synchronized` giống nhau trên Java 21 và Java 24+.** JEP 491 đã thay đổi cơ chế.
9. **Giả định `ThreadLocal` trong task virtual tái sử dụng cache như pool platform threads.** Mỗi virtual task dùng thread khác nhau; cần cân nhắc bộ nhớ và cleanup.

## 10. Tự kiểm tra hiểu bài

**Câu 1:** Vì sao `ThreadGroup` không ngang hàng với platform/virtual thread?  
**Đáp án:** Nó là cấu trúc nhóm platform threads, không phải đối tượng thực thi hay scheduler.

**Câu 2:** Một service có hàng nghìn request chờ HTTP phần lớn thời gian nên xem xét gì?  
**Đáp án:** Virtual threads per request/task có thể phù hợp với blocking I/O được JVM hỗ trợ, kèm timeout, giới hạn upstream và đo tải.

**Câu 3:** `RUNNABLE` có nghĩa chắc chắn đang chiếm CPU không?  
**Đáp án:** Không; Java gộp trạng thái sẵn sàng và chạy, việc quan sát chỉ là snapshot.

**Câu 4:** Tại sao virtual threads không làm DB 20 connections phục vụ đồng thời 10.000 truy vấn?  
**Đáp án:** Virtual threads chỉ làm nhẹ chi phí chờ của JVM; connection pool vẫn có đúng tài nguyên cho 20 truy vấn trong cùng một thời điểm.

**Câu 5:** Đã bật `spring.threads.virtual.enabled` nhưng `@Async` vẫn chạy platform thread, kiểm tra gì?  
**Đáp án:** Phiên bản Spring Boot/JDK, executor mà `@Async` chọn, custom `Executor` bean và điểm kiểm chứng `isVirtual()`.

## Tài liệu tham khảo

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444): thiết kế, runtime, API, scalability.
- [Java SE 21 `Thread` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html): hành vi và vòng đời của hai loại thread.
- [Java SE 21 `ThreadGroup` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadGroup.html): nhóm luồng và các giới hạn virtual thread.
- [Oracle Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): mount/unmount, blocking, pinning, chẩn đoán.
- [JEP 491 — Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491): thay đổi từ Java 24.
- [Spring Boot Reference — Virtual Threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads): hỗ trợ và vòng đời trong Spring Boot.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-fundamentals-003">Future và CompletableFuture biểu diễn công việc chưa hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Future và CompletableFuture: biểu diễn công việc chưa hoàn tất

Trong Java, một lời gọi hàm thông thường trả về kết quả khi hàm đã chạy xong. Với tác vụ chạy ở luồng khác, lúc gửi việc đi ta có thể **chưa có kết quả**. **Future** và **CompletableFuture** cung cấp một đối tượng đại diện cho kết quả sẽ có trong tương lai, bao gồm cả khả năng thất bại hoặc bị hủy.

Bài này thuộc nhóm **Nền tảng luồng** trong Spring Framework - concurrency. Trọng tâm là hiểu trạng thái của công việc, cách đợi hoặc kết hợp kết quả, điều khiển lỗi và lựa chọn executor. Phần cuối nối kiến thức Java với Spring **@Async**.

## 1. Phân biệt tác vụ, luồng và đối tượng kết quả

- **Task (tác vụ)**: phần công việc cần thực hiện, chẳng hạn tính toán, gọi dịch vụ hoặc đọc dữ liệu.
- **Thread (luồng)**: tài nguyên thực thi tác vụ. Một executor quản lý việc xếp lịch và chạy task trên các luồng.
- **Future<T>**: tay cầm đại diện cho kết quả kiểu T của task đã gửi. Nó không phải chính luồng chạy task.
- **CompletionStage<T>**: giao ước mô tả các bước xử lý phụ thuộc vào kết quả trước đó.
- **CompletableFuture<T>**: vừa thực hiện giao ước Future, vừa thực hiện CompletionStage và cho phép chủ động hoàn tất kết quả.

Một đối tượng future có thể đang chờ, đã hoàn tất thành công, đã hoàn tất với lỗi hoặc đã bị hủy. **isDone()** báo đã hoàn tất *theo bất cứ trạng thái nào*; nó không có nghĩa là thành công. Sự hoàn tất của future và sự dừng thực sự của tác vụ nền cũng không phải lúc nào trùng nhau.

Luồng tư duy:

~~~text
Caller                    Executor / worker
  | submit(task) ----------------> task chạy
  | nhận Future<T>           ... có thể chưa xong
  | làm việc khác
  | get()/join() ---> đợi nếu cần ---> nhận T hoặc lỗi
~~~

**Bất đồng bộ** nói về cách điều phối công việc, không tự động đồng nghĩa với “không bao giờ chặn”. Gọi get() hoặc join() trên kết quả chưa sẵn sàng vẫn chặn luồng gọi.

## 2. Future: nhận kết quả từ ExecutorService

**ExecutorService.submit(Callable<T>)** nhận một hàm có giá trị trả về và trả ngay một **Future<T>**. Các thao tác cốt lõi:

| API | Ý nghĩa |
| --- | --- |
| submit(callable) | Gửi task và nhận Future đại diện cho kết quả. |
| isDone() | True nếu đã xong, thất bại hoặc bị hủy. |
| get() | Chờ tới khi xong; trả kết quả hoặc ném ngoại lệ. |
| get(timeout, unit) | Chờ tối đa một khoảng thời gian; có thể ném TimeoutException. |
| cancel(mayInterruptIfRunning) | Yêu cầu hủy; với Future của executor, tham số true có thể phát tín hiệu interrupt. |
| isCancelled() | True nếu future đã được hủy thành công. |

Ví dụ Java 17+ độc lập, lưu thành **FutureDemo.java**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> answer = executor.submit(() -> 6 * 7);

            System.out.println("Đã gửi tác vụ");
            System.out.println("Kết quả: " + answer.get());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac FutureDemo.java
java FutureDemo
~~~

Kết quả chuẩn:

~~~text
Đã gửi tác vụ
Kết quả: 42
~~~

Ở đây phép tính chạy trên executor; get() là điểm đồng bộ hóa. Nếu task chưa xong, luồng main sẽ đợi. Nếu chỉ gửi task rồi lập tức get(), ta ít tận dụng được khả năng làm việc khác trong lúc chờ.

**Ba nhánh kết quả của get():**

1. Task thành công: nhận T.
2. Task ném ngoại lệ: get() ném **ExecutionException**; lỗi gốc thường nằm ở **getCause()**.
3. Luồng gọi bị interrupt: get() ném **InterruptedException**. Ở tầng xử lý chủ động bắt ngoại lệ này, thường phải phục hồi cờ interrupt bằng **Thread.currentThread().interrupt()** rồi dừng hoặc trả lỗi phù hợp. Nếu vượt quá giới hạn ở get(timeout, unit), nhận **TimeoutException**.

**TimeoutException không tự động hủy task**; task vẫn có thể tiếp tục chạy. Với cancel(true), interrupt là yêu cầu hợp tác, không phải cơ chế cưỡng bức dừng mọi công việc, đặc biệt với thao tác không phản ứng interrupt.

**Hạn chế của Future truyền thống:** cung cấp khả năng chờ/hủy/truy vấn, nhưng không có API chuẩn thuận tiện để đăng ký bước xử lý khi hoàn tất, nối nhiều task phụ thuộc hoặc biến đổi kết quả mà không gọi get().

## 3. CompletableFuture: future có thể hoàn tất và nối nhiều bước

**CompletableFuture<T>** bổ sung hai ý tưởng:

1. **Chủ động hoàn tất:** complete(value), completeExceptionally(error) và một số phương thức hoàn tất khác có thể đặt trạng thái của future. Chỉ một lần hoàn tất đầu tiên theo cách thông thường có hiệu lực.
2. **Đăng ký phụ thuộc:** thenApply, thenCompose, thenCombine, handle... tạo các stage tiếp theo. Khi stage trước hoàn tất, stage phụ thuộc có cơ hội chạy theo quy tắc đã đăng ký.

Các cách tạo thường gặp:

| Cách tạo | Cách hiểu |
| --- | --- |
| CompletableFuture.completedFuture(value) | Future đã thành công ngay với value; không tạo task mới. |
| new CompletableFuture<T>() | Future chưa hoàn tất; một bên khác phải gọi complete hoặc completeExceptionally. |
| CompletableFuture.supplyAsync(supplier, executor) | Gửi công việc có kết quả đến executor, trả CompletableFuture<T>. |
| CompletableFuture.runAsync(runnable, executor) | Gửi công việc không có giá trị trả về, trả CompletableFuture<Void>. |

Nếu **supplyAsync/runAsync** không truyền executor, chúng thường dùng **ForkJoinPool.commonPool()** theo chính sách mặc định của CompletableFuture; không nên mặc định sử dụng common pool cho I/O chặn lâu dài trong ứng dụng server. Với **new CompletableFuture<>()**, không có task nào tự được khởi chạy: quên hoàn tất nó có thể khiến người đợi bị treo vô hạn.

## 4. Biến đổi, nối tiếp và ghép nhiều kết quả

| Phương thức | Khi nào dùng | Kiểu kết quả |
| --- | --- | --- |
| thenApply(fn) | Biến đổi kết quả T thành U bằng hàm thông thường. | CompletableFuture<U> |
| thenAccept(action) | Dùng kết quả để tạo tác động phụ, không trả U. | CompletableFuture<Void> |
| thenRun(action) | Chạy bước tiếp theo không cần giá trị trước đó. | CompletableFuture<Void> |
| thenCompose(fn) | Bước sau tự trả một CompletionStage<U>; nối phẳng thay vì lồng future. | CompletableFuture<U> |
| thenCombine(other, fn) | Hai tác vụ độc lập đều hoàn tất thành công rồi mới ghép hai giá trị. | CompletableFuture<V> |
| allOf(f1, f2, ...) | Chờ tất cả future hoàn tất; không tự gom kết quả thành danh sách. | CompletableFuture<Void> |
| anyOf(f1, f2, ...) | Hoàn tất theo future đầu tiên hoàn tất, kể cả trường hợp lỗi. | CompletableFuture<Object> |

**thenApply so với thenCompose:** khi bước tiếp theo trả về một giá trị thường, dùng thenApply; nếu bước đó *đã là bất đồng bộ và trả future*, dùng thenCompose để tránh CompletableFuture<CompletableFuture<U>>. Tương tự, **thenCombine** giải quyết hai nhánh độc lập, còn **thenCompose** biểu diễn quan hệ phụ thuộc (lấy ID trước rồi mới dùng ID để tải hồ sơ).

Ví dụ độc lập, lưu thành **CompletableFutureDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletableFutureDemo {
    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> price =
                    CompletableFuture.supplyAsync(() -> 100, pool);
            CompletableFuture<Integer> fee =
                    CompletableFuture.supplyAsync(() -> 15, pool);

            CompletableFuture<String> bill = price
                    .thenCombine(fee, Integer::sum)
                    .thenApply(total -> "Tổng: " + total);

            System.out.println(bill.join());

            CompletableFuture<String> id =
                    CompletableFuture.completedFuture("u-7");
            CompletableFuture<String> profile = id.thenCompose(userId ->
                    CompletableFuture.supplyAsync(
                            () -> "Hồ sơ " + userId, pool));

            System.out.println(profile.join());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac CompletableFutureDemo.java
java CompletableFutureDemo
~~~

Kết quả chuẩn (hai câu được in theo thứ tự join của main, dù thứ tự chạy giữa worker có thể khác):

~~~text
Tổng: 115
Hồ sơ u-7
~~~

Sơ đồ phụ thuộc:

~~~text
price ──┐
        ├─ thenCombine ─ thenApply ─ bill
fee ────┘

id ─ thenCompose(id -> tải profile bất đồng bộ) ─ profile
~~~

Ví dụ sau là **đoạn mã minh họa trong phương thức**, giả sử a và b có kiểu CompletableFuture<String>:

~~~java
CompletableFuture<Void> ready = CompletableFuture.allOf(a, b);
CompletableFuture<java.util.List<String>> both = ready.thenApply(
        unused -> java.util.List.of(a.join(), b.join()));
~~~

Sau khi allOf hoàn tất thành công, hai giá trị đều sẵn sàng; các lệnh join() ở đây chỉ dùng để lấy giá trị. **allOf không trả về List**. Nếu một nhánh lỗi, allOf cũng hoàn tất với lỗi và thenApply không chạy. Đối với **anyOf**, không nên giả định nhánh thắng luôn thành công hoặc tự hủy các nhánh còn lại.

## 5. Thực thi callback: không có Async và có Async

Dù future mô tả luồng dữ liệu, **nó không đảm bảo mỗi stage chạy trên luồng riêng**:

- **thenApply/thenCompose/whenComplete...** (không có Async): callback có thể chạy trên luồng hoàn tất stage trước hoặc một luồng khác đang thực hiện quá trình hoàn tất. Nếu stage đã xong khi đăng ký, callback có thể chạy ngay trên luồng đăng ký. Không dựa vào tên luồng cụ thể.
- **thenApplyAsync(...), thenComposeAsync(...)**: đưa phần thực thi callback tới executor bất đồng bộ. Phiên bản truyền executor cho phép lựa chọn pool rõ ràng; phiên bản không truyền executor dùng cơ chế mặc định của CompletableFuture.
- **supplyAsync(..., executor)**: chỉ định nơi chạy supplier gốc. Một stage thenApply không có Async gắn sau đó không mặc nhiên được chuyển sang executor mới.

Nếu callback làm việc rất nặng hoặc I/O chặn, hãy chọn executor phù hợp thay vì để nó vô tình chiếm luồng hoàn tất một stage khác. **Bất đồng bộ không đồng nghĩa với song song**: pool một luồng vẫn có thể xử lý nhiều task luân phiên. Với ứng dụng có tải, kích thước pool, độ dài hàng đợi và chính sách từ chối task là yếu tố vận hành quan trọng.

## 6. Lỗi đi theo chuỗi như thế nào?

Khi một stage ném ngoại lệ, stage đó hoàn tất **exceptionally**. Các bước chỉ dành cho thành công (như thenApply) thường bị bỏ qua, còn các bước xử lý lỗi có thể phục hồi hoặc ghi nhận thất bại.

| API | Chạy khi | Tác dụng |
| --- | --- | --- |
| exceptionally(fn) | Khi stage trước lỗi | Chuyển lỗi thành giá trị dự phòng cùng kiểu T. |
| handle((value, error) -> result) | Khi thành công hoặc lỗi | Tạo một giá trị mới U dựa trên cả hai nhánh. |
| whenComplete((value, error) -> ...) | Khi thành công hoặc lỗi | Quan sát/log/đo đạc; thông thường giữ nguyên kết quả hoặc lỗi. |
| completeExceptionally(error) | Khi bên sản xuất chủ động báo lỗi | Hoàn tất một future đang chờ bằng ngoại lệ. |

Ví dụ Java 17+ độc lập, lưu thành **FailureDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;

public class FailureDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> original = new CompletableFuture<>();
        original.completeExceptionally(
                new IllegalStateException("Không đọc được dữ liệu"));

        CompletableFuture<Integer> recovered =
                original.exceptionally(error -> 0);

        System.out.println("Giá trị dự phòng: " + recovered.join());
        System.out.println("Nguồn lỗi: " + original.isCompletedExceptionally());

        String status = original.handle((value, error) ->
                error == null ? "Thành công: " + value : "Thất bại");
        System.out.println(status);
    }
}
~~~

Chạy:

~~~bash
javac FailureDemo.java
java FailureDemo
~~~

Kết quả chuẩn:

~~~text
Giá trị dự phòng: 0
Nguồn lỗi: true
Thất bại
~~~

Chú ý **recovered** là future mới đã phục hồi; **original** vẫn thất bại. **whenComplete** thường dùng để quan sát; không chọn nó thay exceptionally/handle nếu cần trả fallback. Nếu chính callback xử lý lỗi lại ném ngoại lệ, stage mới vẫn có thể thất bại.

**get() so với join():**

- **get()** là API của Future, chặn và khai báo InterruptedException, ExecutionException; có biến thể timeout. Phù hợp khi cần quản lý interrupt tường minh.
- **join()** cũng chờ nhưng dùng CompletionException không kiểm tra cho các lỗi tính toán bất đồng bộ (hoặc CancellationException khi bị hủy); thuận tiện khi nối pipeline.
- Đừng nuốt lỗi bằng cách trả giá trị mặc định cho mọi thất bại nếu điều đó che mất vấn đề nghiệp vụ. Nên ghi nhận/ngữ cảnh hóa nguyên nhân gốc và quyết định có được fallback hay không.

## 7. Timeout, hủy và vòng đời tác vụ

Có ba cơ chế rất dễ nhầm:

1. **future.get(500, MILLISECONDS)** giới hạn *thời gian chờ của người gọi*. Hết 500 ms thì người gọi nhận TimeoutException, nhưng future/task có thể tiếp tục.
2. **future.orTimeout(500, MILLISECONDS)** làm chính CompletableFuture hoàn tất với TimeoutException nếu chưa xong khi hết hạn; phương thức trả lại **cùng đối tượng future**. **completeOnTimeout(fallback, ...)** cũng tác động lên future đó nhưng hoàn tất bình thường bằng fallback.
3. **future.cancel(true)** yêu cầu hủy. Với CompletableFuture chuẩn, tham số **mayInterruptIfRunning không có tác dụng điều khiển interrupt**; hủy được biểu diễn như một dạng hoàn tất đặc biệt với CancellationException. Điều này **không đảm bảo công việc nền đã dừng**.

Sự khác biệt này thiết yếu với các lời gọi mạng hoặc truy vấn có thể chạy rất lâu. Timeout của future là deadline ở tầng điều phối; cần cấu hình timeout/interrupt/cancellation phù hợp ở client I/O hoặc API thực thi thật sự để kiểm soát tài nguyên. Nếu cần dừng công việc, thiết kế task biết kiểm tra interrupt hoặc cơ chế hủy của thư viện đang dùng.

**Đừng tạo future rồi không ai hoàn tất:** một CompletableFuture chưa được complete/completeExceptionally/cancel sẽ khiến join() chờ vô hạn. Tương tự, dùng get()/join() trong một task đang chiếm hết pool để chờ task khác cũng nằm trong chính pool có thể gây **thread starvation hoặc deadlock**. Nên biểu diễn phụ thuộc bằng thenCompose/thenCombine thay vì block chéo giữa các worker.

**Đừng quên đóng executor tự tạo:** với ExecutorService, gọi shutdown() khi không còn gửi task, rồi dùng awaitTermination hoặc chính sách shutdown thích hợp khi ứng dụng cần chờ dọn dẹp. Các ví dụ trên đã đợi kết quả cần dùng trước khi shutdown.

## 8. Liên hệ trực tiếp với Spring Framework

Spring cung cấp **TaskExecutor** và **@Async** để thực thi phương thức bean trên executor. Future/CompletableFuture vẫn là kiểu đối tượng mà phía gọi nhận để theo dõi hoặc kết hợp kết quả; @Async điều khiển **nơi phương thức chạy**, còn CompletableFuture hỗ trợ **cách các kết quả liên kết với nhau**.

Ví dụ dưới đây là **các đoạn cấu hình/service cho ứng dụng Spring có dependency Spring Context**; không phải một chương trình Java độc lập chạy bằng hai lệnh javac/java:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("io-");
        executor.initialize();
        return executor;
    }
}
~~~

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    @Async("ioExecutor")
    public CompletableFuture<String> loadReport(String id) {
        // Giả sử ở đây có phép đọc dữ liệu đồng bộ/tác vụ I/O.
        String content = "report:" + id;
        return CompletableFuture.completedFuture(content);
    }
}
~~~

Một bean khác gọi **reportService.loadReport("r-1")** qua Spring proxy sẽ nhận future đại diện cho việc thực thi bất đồng bộ của lời gọi; nó có thể gắn thêm thenApply/exceptionally thay vì chờ ngay. Trong ví dụ, **completedFuture** được trả từ *bên trong* phương thức đã được @Async đưa sang worker: không cần tự tạo thêm supplyAsync chỉ để bọc kết quả đã tính.

Các ràng buộc khi áp dụng:

- Phải bật hỗ trợ async (ví dụ **@EnableAsync**) và gọi qua Spring bean/proxy. **Tự gọi phương thức @Async bên trong cùng đối tượng** trong cấu hình proxy thông thường không đi qua interceptor, nên không tạo chuyển luồng như mong đợi.
- Chỉ định executor có quản trị; cấu hình pool, hàng đợi, rejection và shutdown cho tải dự kiến. Pool I/O và pool CPU có thể có đặc tính khác nhau.
- Phương thức **@Async** có thể trả **Future** hoặc **CompletableFuture** để người gọi theo dõi lỗi; nếu trả void, lỗi không được chuyển cho caller theo cơ chế future (Spring có AsyncUncaughtExceptionHandler).
- Transaction, SecurityContext, MDC và các trạng thái gắn với ThreadLocal **không tự động được chuyển sang luồng khác trong mọi cấu hình**. Khi cần, dùng cơ chế truyền ngữ cảnh được hỗ trợ, và xác định rõ ranh giới transaction.
- Nếu một web request gọi @Async rồi **join() ngay trên luồng request**, vẫn có đoạn chặn; cần đánh giá cơ chế trả lời bất đồng bộ thật sự ở tầng web thay vì chỉ nhìn annotation.

Một CompletableFuture tự khởi chạy bằng supplyAsync trên executor thuần Java **không tự biến thành** phương thức @Async của Spring. Hai cách có thể bổ sung nhau nhưng không cần lồng async vô ích.

## 9. Những lỗi thiết kế phổ biến và cách nhận biết

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| submit rồi get() ngay | Caller chặn, ít chồng lấp công việc. | Làm việc độc lập trước khi đợi hoặc dùng pipeline. |
| Dùng thenApply với hàm trả future | Tạo future lồng, khó điều phối. | Chọn thenCompose. |
| Tin isDone() đồng nghĩa thành công | Bỏ qua lỗi/hủy. | Kiểm tra get/join, isCompletedExceptionally, isCancelled khi phù hợp. |
| Tin cancel(true) của CompletableFuture dừng I/O | Công việc nền có thể tiếp tục tiêu tốn tài nguyên. | Kết hợp timeout và hủy ở tầng tác vụ/client. |
| Block giữa các task trên pool nhỏ | Cạn worker hoặc deadlock. | Kết hợp stage thay vì join chéo; xem lại cấu hình pool. |
| Dùng commonPool cho nhiều tác vụ I/O lâu | Tranh chấp với các công việc khác. | Truyền executor phù hợp và giới hạn tải. |
| Nuốt exception bằng fallback chung | Ẩn sự cố và dữ liệu sai. | Xử lý có chủ đích, log nguyên nhân và phân biệt lỗi dự phòng. |
| Quên complete một future tự tạo | Người gọi chờ mãi. | Quy định ai sở hữu việc hoàn tất mọi nhánh, kể cả lỗi/hủy. |
| Gọi @Async từ chính bean đó | Có thể không qua Spring proxy. | Gọi qua bean được Spring quản lý ở ranh giới phù hợp. |

## 10. Chọn công cụ theo yêu cầu

- **Chỉ cần gửi một phép tính và cuối cùng chờ kết quả:** ExecutorService + Future thường đủ, đặc biệt khi muốn xử lý interrupt/timeout theo mẫu truyền thống.
- **Cần biến đổi hoặc phối hợp nhiều kết quả bất đồng bộ:** CompletableFuture với thenApply, thenCompose, thenCombine, allOf và xử lý lỗi có thể đọc được hơn.
- **Cần nhiều phương thức service chạy ngoài luồng gọi trong ứng dụng Spring:** TaskExecutor + @Async; chọn kiểu trả về CompletableFuture khi caller cần nối stage.
- **Cần kiểm soát hạn chót và tài nguyên:** deadline, chính sách hủy và timeout thực ở tầng I/O là phần thiết kế riêng; future chỉ biểu diễn và điều phối trạng thái.

## 11. Bài tập tự kiểm tra

1. **Dự đoán:** task đã ném IllegalStateException; isDone() là true hay false? **Đáp án:** true, vì task đã hoàn tất với lỗi; get() sẽ ném ExecutionException.
2. **Chọn phép nối:** bước A trả mã người dùng; bước B gọi API bất đồng bộ để tải hồ sơ theo mã đó. **Đáp án:** thenCompose, vì B trả một stage mới phụ thuộc A.
3. **Hai request độc lập trả giá và phí:** muốn tính tổng khi cả hai thành công. **Đáp án:** thenCombine; nếu muốn đợi nhiều request không dùng trực tiếp giá trị thì allOf.
4. **get(timeout) ném TimeoutException:** task có bị hủy không? **Đáp án:** không tự động.
5. **Gọi cancel(true) trên CompletableFuture:** có chắc worker bị interrupt không? **Đáp án:** không; tham số không chi phối interrupt trong triển khai CompletableFuture chuẩn.
6. **Một @Async gọi từ this.loadReport():** có chắc chạy ở executor không? **Đáp án:** không trong chế độ Spring proxy thông thường, vì self-invocation bỏ qua proxy.

## 12. Ghi nhớ bản chất

**Future** là lời hứa *sẽ có kết quả hoặc lỗi* của công việc đã được gửi; sử dụng trực tiếp chủ yếu bằng kiểm tra trạng thái và chờ. **CompletableFuture** còn là một điểm hoàn tất và một đồ thị phụ thuộc: nó giúp mô tả **khi có A thì làm B**, **khi có cả A và B thì ghép**, **khi lỗi thì phục hồi**, mà không cần chặn tại từng bước. Tuy vậy, **lịch chạy trên executor, giới hạn tài nguyên, timeout I/O và cơ chế hủy thực tế** vẫn phải được thiết kế riêng. Trong Spring, @Async đưa phần thực thi của phương thức qua TaskExecutor; CompletableFuture giúp người gọi kết hợp kết quả đó.

**Tài liệu API nên tra cứu:** Java SE 17 java.util.concurrent.Future, CompletionStage, CompletableFuture; Spring Framework Reference, mục Task Execution and Scheduling và @Async.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-fundamentals-004">Tranh chấp dữ liệu, tính nguyên tử và khả năng nhìn thấy thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Tranh chấp dữ liệu, tính nguyên tử và khả năng nhìn thấy thay đổi

> **Phạm vi:** nền tảng lập trình đa luồng với Java 17+ và cách áp dụng trong ứng dụng Spring Framework. Trọng tâm là trạng thái được nhiều luồng cùng truy cập, điều kiện xảy ra lỗi, các bảo đảm của Java Memory Model (JMM) và lựa chọn công cụ đồng bộ đúng với bất biến cần bảo vệ.

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt **data race**, **race condition**, **atomicity** (tính nguyên tử) và **visibility** (khả năng nhìn thấy thay đổi).
2. Giải thích tại sao `counter++` có thể mất lượt tăng và tại sao thêm `volatile` không khắc phục được điều đó.
3. Dùng quan hệ **happens-before** để lý giải khi nào kết quả ghi của một luồng được bảo đảm hiển thị với luồng khác.
4. Chọn giữa dữ liệu bất biến, `synchronized`, `volatile`, `AtomicInteger`, lock và concurrent collection.
5. Nhận diện trạng thái chia sẻ nguy hiểm trong Spring singleton bean, công việc `@Async` và ứng dụng nhiều instance.

## 1. Trạng thái chia sẻ tạo ra vấn đề như thế nào?

Một **thread** (luồng) là đơn vị thực thi. Hai luồng có thể chạy xen kẽ hoặc song song. Biến cục bộ của một lần gọi phương thức thường thuộc riêng lần gọi đó; ngược lại, trường của một đối tượng được cả hai luồng tham chiếu tới là **shared mutable state** nếu có thể thay đổi.

Ví dụ, hai HTTP request cùng gọi một phương thức trên một Spring service mặc định có scope `singleton`. Chúng có thể thao tác trên **cùng một đối tượng service**. Nếu service lưu `int count` và cả hai request cùng thực hiện `count++`, kết quả phụ thuộc cách các thao tác xen kẽ.

**Data race** theo JMM xảy ra khi có hai truy cập xung đột tới cùng một biến (ít nhất một truy cập là ghi), thuộc các luồng khác nhau, mà không được sắp thứ tự bởi quan hệ happens-before. **Race condition** là khái niệm rộng hơn: kết quả chương trình phụ thuộc vào thời điểm hoặc thứ tự thực thi, đôi khi ngay cả khi từng truy cập dữ liệu đã được đồng bộ. Cách sửa phải dựa trên *bất biến nghiệp vụ* cần bảo vệ, không chỉ nhìn một biến riêng lẻ.

Một ví dụ đơn giản:

~~~java
class UnsafeCounter {
    private int count;

    void increment() {
        count++;
    }

    int get() {
        return count;
    }
}
~~~

Đây là **đoạn minh họa**, chưa có `main`. Cả `increment()` và `get()` truy cập `count` mà không đồng bộ. Lỗi có thể xuất hiện khi nhiều luồng cùng sử dụng một instance.

## 2. Tính nguyên tử: một phép tăng không phải một bước

**Atomicity** nghĩa là thao tác được quan sát như một đơn vị không thể bị chen vào ở giữa, trong phạm vi bảo đảm của thao tác đó. Câu lệnh `count++` về mặt logic gồm ba bước:

1. Đọc giá trị hiện tại.
2. Tính giá trị mới bằng giá trị cũ cộng 1.
3. Ghi giá trị mới.

Giả sử `count = 0`:

| Thời điểm | Luồng A | Luồng B | `count` |
| --- | --- | --- | ---: |
| 1 | Đọc 0 | | 0 |
| 2 | | Đọc 0 | 0 |
| 3 | Ghi 1 | | 1 |
| 4 | | Ghi 1 | 1 |

Hai lần tăng được yêu cầu nhưng kết quả cuối cùng chỉ là **1**. Đây là *lost update*. Thay `int` bằng `volatile int` vẫn không làm toàn bộ chuỗi đọc–sửa–ghi thành nguyên tử.

### Thí nghiệm tái hiện lost update có kiểm soát

Lưu đoạn sau vào tệp `LostUpdateDemo.java`. `CountDownLatch` buộc hai luồng đọc cùng giá trị ban đầu trước khi được phép ghi. Vì thế kết quả của **thí nghiệm này** là xác định; bản thân lỗi race trong chương trình thực tế thường xuất hiện không ổn định.

~~~java
import java.util.concurrent.CountDownLatch;

public class LostUpdateDemo {
    private static int count = 0;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable increment = () -> {
            int snapshot = count;
            bothRead.countDown();

            try {
                allowWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            count = snapshot + 1;
        };

        Thread first = new Thread(increment);
        Thread second = new Thread(increment);
        first.start();
        second.start();

        bothRead.await();
        allowWrite.countDown();
        first.join();
        second.join();

        System.out.println("count = " + count);
    }
}
~~~

Chạy:

~~~bash
javac LostUpdateDemo.java
java LostUpdateDemo
~~~

Kết quả:

~~~text
count = 1
~~~

Hai luồng đọc `0`, sau đó đều ghi `1`. Các latch chỉ tổ chức thứ tự các bước trong **thí nghiệm**; chúng không biến việc cập nhật `count` ở đây thành phép tăng nguyên tử. `join()` bảo đảm luồng `main` chỉ đọc kết quả sau khi cả hai luồng đã kết thúc.

**Điểm phân biệt:** một lần đọc/ghi đơn lẻ của một số kiểu có thể nguyên tử nhưng *chuỗi nhiều thao tác* vẫn không nguyên tử. Theo JLS, việc đọc/ghi `long` hoặc `double` **không volatile** có ngoại lệ lịch sử về tính nguyên tử, nên không nên suy ra mọi phép đọc/ghi primitive đều có bảo đảm giống nhau. Cách viết mã đa luồng an toàn là dựa vào cấu trúc đồng bộ có hợp đồng rõ ràng.

## 3. Visibility: một luồng có thấy giá trị mới không?

**Visibility** trả lời câu hỏi: sau khi luồng A thay đổi dữ liệu, luồng B có được bảo đảm quan sát thay đổi đó không? Trình biên dịch, JIT và CPU có thể dùng thanh ghi, cache và sắp xếp lại thao tác trong giới hạn JMM. Không có quan hệ đồng bộ phù hợp, B có thể quan sát dữ liệu cũ; không được giả định rằng “A đã ghi trước theo đồng hồ” đồng nghĩa với “B chắc chắn đọc được giá trị mới”.

Ví dụ về một cờ dừng:

~~~java
class WorkerLoop implements Runnable {
    private volatile boolean running = true;

    @Override
    public void run() {
        while (running) {
            Thread.onSpinWait();
        }
    }

    void stop() {
        running = false;
    }
}
~~~

Đây là **đoạn minh họa**, không phải ứng dụng độc lập. Với cờ chỉ cần đọc/ghi đơn lẻ, `volatile` tạo bảo đảm hiển thị phù hợp: ghi `running = false` của một luồng *happens-before* các lần đọc `running` về sau trong synchronization order. Nếu bỏ `volatile` và không dùng đồng bộ khác, luồng chạy vòng lặp không có bảo đảm sẽ quan sát lệnh dừng. `Thread.onSpinWait()` chỉ là gợi ý cho CPU, **không phải** công cụ đồng bộ. Trong ứng dụng thực, ưu tiên `interrupt()`, cơ chế chờ có chặn hoặc hủy task thay vì vòng lặp tiêu tốn CPU.

### Atomicity và visibility là hai yêu cầu khác nhau

| Câu hỏi | Ví dụ lỗi | Công cụ thường phù hợp |
| --- | --- | --- |
| **Atomicity**: Cả thao tác có diễn ra không bị chen ngang? | Hai `count++` mất một lần tăng | `synchronized` hoặc `AtomicInteger.incrementAndGet()` |
| **Visibility**: Luồng B có thấy cập nhật của A? | Cờ dừng không được quan sát | `volatile`, lock hoặc quan hệ happens-before khác |
| **Ordering**: Các thao tác có được quan sát theo thứ tự cần thiết? | Đọc trạng thái phụ thuộc trước khi nó được công bố an toàn | Công bố an toàn bằng lock, `volatile`, `final` đúng cách hoặc cấu trúc concurrency |
| **Invariant**: Nhiều trường có luôn nhất quán với nhau? | Trừ tiền và cập nhật sổ cái lệch nhau | Một miền khóa bao trọn thao tác, hoặc transaction/điều phối phù hợp |

`volatile` phù hợp với cờ trạng thái hoặc *một giá trị snapshot độc lập*. Nó **không** làm `x++`, `if (x == 0) x = 1` hoặc hai phép cập nhật ở hai trường thành một giao dịch nguyên tử.

## 4. Java Memory Model và quan hệ happens-before

JMM là hợp đồng quy định những giá trị nào một luồng có thể đọc và các hiệu ứng đồng bộ giữa luồng. Quan hệ **happens-before (HB)** là công cụ chính để suy luận về visibility và thứ tự: nếu hành động A *happens-before* hành động B thì hiệu ứng của A được đảm bảo về mặt hiển thị/thứ tự đối với B theo quy tắc JMM. HB là quan hệ thứ tự logic, **không đơn thuần** là thứ tự thời gian thực.

Những quy tắc cần nhớ:

- **Program order:** trong cùng một luồng, một hành động happens-before các hành động đứng sau nó theo thứ tự chương trình.
- **Monitor:** thao tác mở khóa của `synchronized` happens-before lần khóa tiếp theo trên **chính monitor đó**.
- **Volatile:** một lần ghi vào trường `volatile` happens-before lần đọc tiếp theo của chính trường đó trong synchronization order.
- **Thread.start():** các hành động trước lời gọi `start()` happens-before hành động trong luồng vừa được khởi chạy.
- **Thread.join():** các hành động của luồng đã kết thúc happens-before việc luồng khác trở về thành công từ `join()` đối với luồng ấy.
- **Tính bắc cầu:** nếu A HB B và B HB C thì A HB C. `Future.get()` và nhiều cấu trúc trong `java.util.concurrent` cũng cung cấp các bảo đảm memory consistency được mô tả trong API.

Hình dung thứ tự an toàn của một thông điệp:

~~~text
Luồng A:  data = "ready"  ->  ready = true (volatile write)
                                       |
                                  happens-before
                                       v
Luồng B:                       if (ready) (volatile read)
                                 -> đọc data
~~~

Nếu B quan sát `ready == true` từ thao tác công bố đó, dữ liệu đã ghi trước lần ghi volatile được công bố đúng cho các đọc tiếp theo của B. Khi dùng mẫu này, phải giữ đúng thứ tự ghi dữ liệu rồi mới ghi cờ, và không được tiếp tục sửa dữ liệu chung mà không có giao thức đồng bộ.

**Sự thật quan trọng:** thứ tự xuất hiện của log, thời gian ngủ (`Thread.sleep`), các mức ưu tiên luồng hoặc “máy tôi chạy đúng” **không tạo ra** quan hệ happens-before cho trạng thái chia sẻ. Tránh diễn giải HB thành cam kết scheduler phải thực thi A hoàn toàn trước B.

## 5. Các cách bảo vệ dữ liệu chia sẻ

### 5.1. Ưu tiên không chia sẻ trạng thái có thể thay đổi

Một phương thức chỉ sử dụng tham số, biến cục bộ và các đối tượng bất biến không bị lộ ra ngoài thường dễ cho nhiều request gọi đồng thời. Dùng `record`, trường `final`, defensive copy và giá trị trả về mới khi thích hợp.

`final` giúp ổn định tham chiếu và có quy tắc công bố đặc biệt khi đối tượng được xây dựng đúng, nhưng **không làm bản thân một `ArrayList`/`HashMap` được tham chiếu trở nên bất biến**. Đừng để `this` thoát ra từ constructor trước khi khởi tạo xong.

### 5.2. `synchronized`: vừa loại trừ đồng thời, vừa tạo visibility

~~~java
final class Wallet {
    private long balance;

    Wallet(long openingBalance) {
        this.balance = openingBalance;
    }

    synchronized boolean withdraw(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    synchronized long balance() {
        return balance;
    }
}
~~~

Đây là **định nghĩa lớp để minh họa**, không có `main`. Mọi truy cập vào `balance` được thực hiện qua cùng monitor là đối tượng `Wallet`. Việc *kiểm tra số dư rồi trừ* nằm trong cùng vùng khóa, nên hai luồng không thể đồng thời rút dựa trên một số dư cũ; khi một luồng nhả khóa, luồng lấy khóa tiếp theo nhìn thấy cập nhật. Điều này giải quyết tính nhất quán **của một đối tượng trong một JVM**; nó không tự giải quyết giao dịch CSDL hay nhiều instance dịch vụ.

Lưu ý: khóa của `synchronized` gắn với **đối tượng monitor**. Hai instance `Wallet` khác nhau có hai monitor khác nhau. Tránh khóa trên chuỗi công khai, `this` bị lộ tùy tiện, hoặc đối tượng mà mã bên ngoài có thể tự khóa. Một `private final Object lock = new Object();` thường giúp kiểm soát miền khóa rõ hơn. Giữ vùng khóa vừa đủ để bảo vệ bất biến và tránh I/O chậm trong khóa.

### 5.3. `AtomicInteger`, `AtomicLong` và CAS

Với bộ đếm độc lập, Java cung cấp các phép tăng, cộng và compare-and-set nguyên tử. `AtomicInteger.incrementAndGet()` là **một phép cập nhật nguyên tử**, trái với `count++`.

Lưu đoạn sau vào `AtomicCounterDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        final int threadCount = 4;
        final int incrementsPerThread = 50_000;
        AtomicInteger count = new AtomicInteger();
        Thread[] workers = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    count.incrementAndGet();
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("count = " + count.get());
    }
}
~~~

Chạy:

~~~bash
javac AtomicCounterDemo.java
java AtomicCounterDemo
~~~

Kết quả chính xác:

~~~text
count = 200000
~~~

`incrementAndGet()` thực hiện tăng nguyên tử; `join()` bảo đảm mọi luồng đã hoàn thành trước khi in. Không dựa vào `sleep()` để “chờ đủ lâu”. Một `AtomicInteger` chỉ bảo đảm các thao tác nguyên tử được công bố trên **chính biến đó**; nó không tự bảo vệ bất biến trải trên số dư, lịch sử giao dịch và nhiều đối tượng khác.

**CAS** (compare-and-set) cập nhật giá trị nếu vẫn bằng giá trị mong đợi, giúp nhiều cấu trúc không cần monitor cho từng cập nhật. Với tính toán cập nhật bằng `updateAndGet`, hàm được truyền vào có thể được áp dụng lại khi tranh chấp, vì vậy nên là hàm **không có side effect**. `LongAdder` hữu ích cho bộ đếm bị tranh chấp mạnh, nhưng kết quả `sum()` trong lúc đang cập nhật không phải một snapshot nguyên tử toàn cục.

### 5.4. Lock tường minh và collections đồng thời

`ReentrantLock` dùng khi cần `tryLock`, khóa có timeout, khả năng ngắt khi chờ khóa hoặc nhiều `Condition`. Luôn nhả trong `finally`:

~~~java
lock.lock();
try {
    // Đọc và sửa mọi trạng thái nằm trong cùng bất biến.
} finally {
    lock.unlock();
}
~~~

Đoạn trên là **mẫu cấu trúc**, giả định `lock` đã được khai báo là `ReentrantLock`. Một lock không giúp gì nếu còn đường đọc/ghi cùng dữ liệu đi vòng qua lock đó.

- `ConcurrentHashMap` hỗ trợ nhiều thao tác đồng thời và các thao tác nguyên tử **theo key**, chẳng hạn `compute`, `merge`, `putIfAbsent`. Hai lời gọi độc lập `containsKey` rồi `put` **không** phải một thao tác nguyên tử tổng hợp.
- `CopyOnWriteArrayList` hữu ích khi đọc nhiều và cập nhật ít, vì mỗi lần sửa phải sao chép cấu trúc.
- `Collections.synchronizedList(...)` đồng bộ từng phương thức, nhưng khi duyệt bằng iterator vẫn phải tự đồng bộ quanh quá trình duyệt theo hợp đồng API.
- `BlockingQueue` phù hợp cho mô hình producer–consumer; thao tác đưa/lấy phần tử kết hợp với việc chờ có điều kiện, tránh tự viết vòng lặp polling với cờ volatile.
- `ThreadLocal` lưu giá trị theo luồng, không làm trạng thái chia sẻ bên ngoài an toàn. Với thread pool, luồng được tái sử dụng nên cần dọn `ThreadLocal.remove()` nếu tự quản lý.

Chọn cấu trúc theo **thao tác cần nguyên tử**, không theo tên “concurrent”. Map đồng thời không tự tạo transaction nhất quán trên nhiều key.

## 6. Bảng chọn công cụ cho từng yêu cầu

| Nhu cầu thực tế | Lựa chọn đầu tiên | Giới hạn cần nhớ |
| --- | --- | --- |
| Dữ liệu chỉ đọc sau khi cấu hình | Immutable object, `final`, công bố an toàn | `final` không biến object mutable bên trong thành immutable |
| Cờ hủy hoặc trạng thái độc lập | `volatile boolean` hoặc `AtomicBoolean` | Không thực hiện check-then-act tổng hợp |
| Bộ đếm một instance JVM | `AtomicInteger` / `AtomicLong` | Không đại diện counter toàn hệ thống nhiều node |
| Bộ đếm ghi nhiều, đọc thống kê | `LongAdder` | `sum()` không là snapshot nguyên tử giữa các cập nhật |
| Kiểm tra rồi thay đổi nhiều trường | `synchronized` / `ReentrantLock` | Tất cả đường truy cập phải theo cùng quy tắc khóa |
| Tra cứu/cập nhật theo key | `ConcurrentHashMap.compute` / `merge` | Atomic theo thao tác key, không trên nhiều key |
| Trao đổi task giữa các luồng | `BlockingQueue`, `ExecutorService` | Đóng executor và xử lý cancel/lỗi đúng cách |
| Dữ liệu nhất quán qua nhiều service instance | Transaction/conditional update/lock phân tán phù hợp | Lock trong JVM chỉ bảo vệ một tiến trình |

## 7. Liên hệ trực tiếp với Spring Framework

### 7.1. Singleton bean có thể được nhiều luồng gọi cùng lúc

Trong Spring, scope mặc định `singleton` nghĩa là một instance cho một bean definition trong một Spring container. Nó **không đồng nghĩa thread-safe**. Hai request, tác vụ scheduler hoặc `@Async` có thể dùng cùng một service.

Mẫu dễ lỗi:

~~~java
import org.springframework.stereotype.Service;

@Service
public class UnsafeTicketSequence {
    private long lastId;

    public long nextId() {
        return ++lastId; // có thể mất số khi nhiều request gọi đồng thời
    }
}
~~~

Nếu yêu cầu chỉ là số thứ tự duy nhất **trong một instance JVM**, có thể sửa thành:

~~~java
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class TicketSequence {
    private final AtomicLong lastId = new AtomicLong();

    public long nextId() {
        return lastId.incrementAndGet();
    }
}
~~~

Hai đoạn trên yêu cầu thư viện Spring Framework để biên dịch, **không phải chương trình Java SE độc lập**. Bản sửa chỉ giải quyết đếm trong một đối tượng/JVM. Nếu có nhiều pod/instance, mỗi nơi sẽ có bộ đếm riêng; nếu ID phải duy nhất trên toàn hệ thống, hãy dùng chiến lược ở CSDL hoặc dịch vụ cấp ID phù hợp.

Đối với service xử lý đơn hàng, cách đơn giản hơn thường là **stateless service**: không lưu user hiện tại, request body hay danh sách kết quả từng request vào trường của singleton. Nhận dữ liệu qua tham số, tạo đối tượng cục bộ, trả kết quả. Đổi bean sang `prototype` hoặc `request` chỉ để né race condition không thay thế việc xác định dữ liệu nào thực sự bị chia sẻ, nhất là khi tham chiếu mutable được truyền sang luồng khác.

### 7.2. `@Async` và thread pool không tự đồng bộ dữ liệu

Spring có `TaskExecutor` và hỗ trợ `@Async` (khi đã bật `@EnableAsync`). Một phương thức `@Async` được gọi qua proxy Spring sẽ gửi công việc tới executor phù hợp; nhiều task có thể chạy đồng thời và cùng tham chiếu tới bean singleton. Nếu một phương thức cùng class tự gọi phương thức `@Async` của chính nó theo kiểu mặc định dùng proxy, lời gọi nội bộ không đi qua proxy để kích hoạt async.

Ví dụ **minh họa trong ứng dụng Spring đã cấu hình async**, không phải tệp chạy độc lập:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportJobService {
    private final AtomicInteger finished = new AtomicInteger();

    @Async("applicationExecutor")
    public CompletableFuture<Integer> finishOneReport() {
        int completed = finished.incrementAndGet();
        return CompletableFuture.completedFuture(completed);
    }
}
~~~

`applicationExecutor` phải là tên của một executor bean đã cấu hình; `@EnableAsync` phải được bật. Mỗi invocation qua proxy được lập lịch bất đồng bộ, còn `AtomicInteger` bảo vệ counter nếu nhiều task chạy cùng lúc. Nếu báo cáo cần ghi CSDL, logic phải xử lý transaction/đồng bộ dữ liệu ở tầng lưu trữ; bộ đếm JVM không thay thế điều đó.

**Ba ranh giới cần phân biệt:**

- `@Async` thay đổi cách công việc được lên lịch, **không** biến mọi trường trong bean thành thread-safe.
- `@Transactional` điều phối giao dịch với tài nguyên được transaction manager quản lý, **không** tự khóa trường Java của singleton bean. Transaction context thông thường gắn với luồng; không mặc định được mang sang luồng `@Async`.
- `synchronized` hoặc `AtomicInteger` chỉ phối hợp các luồng chia sẻ **cùng bộ nhớ JVM**. Không dùng chúng để kết luận rằng dữ liệu giữa nhiều pod, server hay process được cập nhật nguyên tử.

### 7.3. Xuất bản cấu hình nhất quán giữa các luồng

Khi cần thay thế một *snapshot* cấu hình nhiều trường, có thể dùng đối tượng bất biến và một tham chiếu `volatile`:

~~~java
record Limits(int maxJobs, int maxRetries) {}

class LimitsHolder {
    private volatile Limits current = new Limits(10, 3);

    Limits snapshot() {
        return current;
    }

    void replace(int maxJobs, int maxRetries) {
        current = new Limits(maxJobs, maxRetries);
    }
}
~~~

Đây là **đoạn minh họa Java 17**, không có `main`. Mỗi lần `snapshot()` nhận một tham chiếu tới toàn bộ bộ giá trị bất biến thay vì đọc riêng lẻ hai trường mutable có thể bị cập nhật lệch thời điểm. Đọc một lần vào biến cục bộ rồi dùng snapshot đó trong toàn bộ nghiệp vụ. Ví dụ này không tự giải quyết trường hợp nhiều writer cần phối hợp kiểm tra rồi ghi; khi đó phải thiết kế cơ chế cập nhật tương ứng.

## 8. Những lỗi suy luận thường gặp

1. **“`volatile int count` làm `count++` thread-safe.”** Sai: volatile có bảo đảm visibility/order, nhưng chuỗi đọc–cộng–ghi vẫn có thể bị chen ngang. Dùng atomic RMW hoặc lock.
2. **“`final Map` không bao giờ đổi.”** Sai: `final` giữ nguyên tham chiếu, không chặn thêm/xóa entry của map.
3. **“Mỗi thao tác `ConcurrentHashMap` an toàn nên hai thao tác ghép lại cũng nguyên tử.”** Sai: phải dùng API atomic phù hợp như `compute` hoặc một giao thức đồng bộ rộng hơn.
4. **“Tôi đã dùng lock lúc ghi; đọc không cần lock.”** Không đủ nếu đường đọc cần nhìn trạng thái nhất quán và không có quan hệ HB khác.
5. **“Thêm `Thread.sleep(100)` là hết race.”** Sai: sleep chỉ ảnh hưởng lịch thực thi, không đặt ra bảo đảm đồng bộ dữ liệu.
6. **“Spring tạo singleton nên tự xử lý race.”** Sai: container quản lý vòng đời và dependency injection; logic mutable của ứng dụng vẫn do tác giả bảo vệ.
7. **“Transaction của database bảo vệ trường Java trong service.”** Sai: hai phạm vi nhất quán khác nhau.
8. **“Kết quả chạy 1.000 lần đều đúng thì mã thread-safe.”** Không đủ: race có thể hiếm, phụ thuộc JVM, CPU và lịch thực thi.
9. **“`AtomicInteger` giúp cập nhật hai biến cùng lúc.”** Sai: atomicity của một biến không bao phủ bất biến trên nhiều biến.
10. **“Thêm khóa vào mọi phương thức là tối ưu.”** Không nhất thiết: khóa quá rộng làm tăng tranh chấp và có thể gây deadlock; ưu tiên bỏ shared mutable state khi có thể.

## 9. Cách phân tích và kiểm thử vấn đề trong dự án

1. **Xác định dữ liệu chia sẻ:** trường `static`, mutable field trong singleton, cache, map, list, session object, tham chiếu được truyền vào task.
2. **Ghi rõ bất biến:** chẳng hạn “số dư không âm” hay “mỗi đơn hàng chỉ xử lý một lần”, thay vì chỉ ghi “không race”.
3. **Liệt kê đường đọc/ghi và luồng thực thi:** HTTP handlers, `@Async`, scheduler, event listener, executor. Xác định phạm vi một JVM hay nhiều instance.
4. **Chứng minh quan hệ HB hoặc tính atomic:** với mỗi cạnh giao tiếp giữa hai luồng, chỉ ra lock nào, volatile nào, atomic operation nào, hoặc API concurrency nào mang bảo đảm.
5. **Tạo bài test cạnh tranh có chủ đích:** dùng `CountDownLatch`/`CyclicBarrier` để bắt đầu đồng thời hoặc ép một interleaving, đo invariant cuối cùng. Thử nhiều vòng và tải đủ lớn; kết quả pass không là chứng minh không có race. Với vấn đề JMM cấp thấp, có thể nghiên cứu công cụ `jcstress`.
6. **Đo và rà soát sau khi sửa:** xác nhận correctness trước, rồi mới đo contention, latency, throughput; xem thread dump khi nghi deadlock hoặc chờ lock kéo dài. Không tối ưu bằng cách tháo khóa nếu chưa có chứng cứ về an toàn.

### Bài tập tự kiểm tra

**Tình huống A:** Một `@Service` singleton có `private int activeRequests`. Trong `begin()` gọi `activeRequests++`, `end()` gọi `activeRequests--`. Điều gì sai?  
**Gợi ý đáp án:** mất cập nhật vì RMW không nguyên tử; `volatile` không chữa. Với yêu cầu chỉ đếm độc lập trong một JVM, có thể dùng `AtomicInteger`, đồng thời phải bảo đảm mọi đường begin/end cân bằng (kể cả lỗi) qua `try/finally`.

**Tình huống B:** Một luồng đổi `stopped = true`, luồng khác vòng lặp kiểm tra `stopped`. Cần bảo đảm gì?  
**Gợi ý đáp án:** visibility/happens-before; `volatile boolean` có thể đủ cho cờ đơn, nhưng nếu đang chờ I/O hoặc blocking queue thì nên dùng cơ chế hủy/interrupt phù hợp.

**Tình huống C:** Hai request đều kiểm tra `balance >= amount` rồi trừ, dùng `AtomicLong` cho balance. Đã chắc chắn đúng chưa?  
**Gợi ý đáp án:** chưa nếu kiểm tra và trừ là hai atomic calls rời; một request có thể trừ theo số dư cũ. Dùng vòng lặp CAS đúng cách hoặc miền khóa bao cả kiểm tra và cập nhật; với tài khoản trong CSDL/multi-instance cần cơ chế transaction/conditional update ở nơi lưu trữ dữ liệu.

**Tình huống D:** Hai bản sao ứng dụng Spring đều có `AtomicLong nextId`. Có bảo đảm ID không trùng không?  
**Gợi ý đáp án:** không; mỗi bản sao có bộ nhớ riêng. Chọn bộ sinh ID/sequence được phối hợp trên toàn hệ thống.

## 10. Ghi nhớ

- **Data race**: truy cập xung đột không được sắp thứ tự HB; **race condition**: kết quả phụ thuộc thứ tự/thời điểm thực thi rộng hơn.
- **Atomicity** bảo vệ một thao tác/bất biến khỏi interleaving; **visibility** bảo đảm quan sát cập nhật; **ordering** giải thích thứ tự hiệu ứng mà các luồng được phép thấy.
- `volatile`: visibility và ordering cho cờ/giá trị độc lập; **không** làm phép cộng hoặc check-then-act nguyên tử.
- `synchronized`: loại trừ đồng thời và HB trên cùng monitor; `AtomicInteger`: phép RMW nguyên tử trên một biến; concurrent collection: bảo đảm phụ thuộc API cụ thể.
- Trong Spring, **singleton ≠ thread-safe**, **`@Async` ≠ synchronization**, và **transaction database ≠ khóa trường Java**. Đề xuất đầu tiên thường là loại bỏ trạng thái mutable dùng chung, sau đó mới chọn cơ chế bảo vệ đúng bất biến.

## Tài liệu tham khảo chính thức

- Java Language Specification, `17 — Threads and Locks, đặc biệt `17.4.5 về happens-before: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- Java SE 17, `java.util.concurrent` và các memory consistency properties: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html
- Java SE 17, `AtomicInteger` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html
- Spring Framework, Bean Scopes: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework, Task Execution and Scheduling / `@Async`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-fundamentals-005">Quan hệ happens-before khi gửi tác vụ và nhận kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ happens-before khi gửi tác vụ và nhận kết quả

Khi một ứng dụng Spring Framework giao công việc cho executor, hai câu hỏi phải được trả lời rõ: **dữ liệu mà luồng gọi đã chuẩn bị có hiển thị đúng với tác vụ không?** Và **sau khi tác vụ hoàn thành, luồng gọi có đọc được kết quả cùng các thay đổi mà tác vụ đã thực hiện không?** Java Memory Model (JMM) trả lời bằng quan hệ `happens-before`. Đây là nền tảng để dùng `Executor`, `ExecutorService`, `Future`, Spring `TaskExecutor` và `@Async` một cách an toàn.

## 1. `happens-before` nghĩa là gì?

`A happens-before B` (viết tắt là `A hb B`) là quan hệ thứ tự bộ nhớ: nếu A ghi dữ liệu và B đọc dữ liệu đó, quan hệ này giúp bảo đảm B nhìn thấy những ghi được công bố từ A theo quy tắc JMM, miễn là không có ghi cạnh tranh khác gây thay đổi giá trị. Nó không có nghĩa hai sự kiện buộc phải chạy trên cùng luồng, cũng không có nghĩa mọi tác vụ trong pool đều chạy tuần tự.

Ba quy tắc cần nhớ:

1. **Thứ tự trong cùng luồng:** hành động đứng trước trong thứ tự chương trình `hb` hành động đứng sau trong luồng ấy.
2. **Cạnh đồng bộ:** thao tác giữa các luồng như `volatile`, khóa `synchronized`, submit qua executor hoặc lấy kết quả từ `Future` có thể thiết lập quan hệ `hb`.
3. **Tính bắc cầu:** nếu `A hb B` và `B hb C` thì `A hb C`.

Quan hệ `hb` giải quyết **thứ tự và khả năng quan sát bộ nhớ**; nó **không tự biến** phép toán `counter++` thành nguyên tử và không bảo vệ đối tượng khỏi những luồng khác đang sửa nó đồng thời.

## 2. Hai cạnh quan trọng nhất trong vòng đời tác vụ

Đặc tả `java.util.concurrent` nêu hai bảo đảm trực tiếp:

- **Trước submit → bên trong tác vụ.** Các hành động của luồng gọi **trước khi** gửi `Runnable` tới `Executor`, hoặc `Runnable`/`Callable` tới `ExecutorService`, `happen-before` việc thực thi tác vụ. Ví dụ, luồng gọi gán `request.id = 7` rồi submit tác vụ đọc `request.id`: nếu không có luồng khác sửa giá trị, tác vụ nhận được dữ liệu đã công bố.
- **Bên trong tác vụ → sau `Future.get()`.** Các hành động của phép tính bất đồng bộ được đại diện bằng `Future` `happen-before` những hành động tiếp theo của luồng đã lấy kết quả bằng `Future.get()`. Bởi vậy, sau khi `get()` trả về bình thường, luồng chờ có thể dùng kết quả và quan sát các ghi của tác vụ trước khi hoàn tất.

```text
Luồng gọi (caller)                         Luồng thực thi (worker)
------------------                         ---------------------
ghi input = 41
       |                                             |
       +--- submit(task) --------------------------> đọc input = 41
                    [cạnh happens-before 1]          tính output = 42
                                                     hoàn tất Future
       <-------------------- get() trả về -----------+
                    [cạnh happens-before 2]
đọc output = 42
```

Từ tính bắc cầu: **ghi trước submit → thao tác trong task → thao tác sau `get()`**. Trái lại, một ghi của caller **sau** submit không tự nhiên `happen-before` lượt đọc của worker. Tương tự, hai task được submit độc lập không mặc nhiên có quan hệ thứ tự với nhau.

Lưu ý: `submit()` thường trả về trước khi tác vụ hoàn thành; việc trả về từ `submit()` **không phải** tín hiệu đã có kết quả. `get()` là điểm chờ hoàn tất và nhận kết quả của `Future` tương ứng.

## 3. Ví dụ Java 17+ chạy độc lập

Ví dụ cố ý dùng hai trường `int` thông thường, không khai báo `volatile`, để chỉ ra **đúng hai ranh giới đồng bộ**. Không có luồng thứ ba truy cập hoặc sửa `SharedState` đồng thời.

Tạo tệp `HappensBeforeTask.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class HappensBeforeTask {
    private static final class SharedState {
        int input;
        int output;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        SharedState state = new SharedState();
        state.input = 41; // Hành động xảy ra trước submit.

        try {
            Future<Integer> future = executor.submit(() -> {
                int observed = state.input; // Thấy 41 nhờ cạnh submit -> task.
                state.output = observed + 1;
                return state.output;
            });

            int result = future.get(); // Chờ task và thiết lập cạnh task -> caller.
            System.out.println("Future: " + result);
            System.out.println("State: " + state.output);
        } finally {
            executor.shutdown();
        }
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac HappensBeforeTask.java
java HappensBeforeTask
```

Kết quả chuẩn:

```text
Future: 42
State: 42
```

**Vì sao kết quả ổn định?** Lệnh `state.input = 41` đứng trước `submit`; task đọc `input` sau ranh giới submit. Task ghi `state.output = 42` trước khi hoàn tất; caller chỉ đọc `output` **sau** `future.get()`. `Future` cũng trả trực tiếp giá trị `42`. Ví dụ này chứng minh bảo đảm của API, không dựa vào `Thread.sleep()`, may mắn về lịch lập lịch hay việc CPU có cache chung. `shutdown()` ở `finally` để giải phóng pool, không thay thế `get()`.

## 4. Điều gì xảy ra nếu đọc quá sớm?

Xem đoạn minh họa sau (giả sử `executor` là một `ExecutorService` đang hoạt động và `state` là đối tượng chỉ chứa trường `int output`):

```java
executor.execute(() -> state.output = 42);
System.out.println(state.output); // Có thể thấy giá trị cũ: chưa chờ task.
```

`execute()` chỉ giao việc, không trả về `Future` để caller chờ. Caller có thể đọc trước khi worker chạy, và với dữ liệu chia sẻ không đồng bộ còn có **data race**. Việc thử vài lần đều in `42` cũng không chứng minh mã đúng.

Khi cần xác nhận task đã hoàn tất, có thể dùng:

```java
Future<?> finished = executor.submit(() -> state.output = 42);
finished.get();
System.out.println(state.output); // 42 nếu không có luồng khác ghi cạnh tranh.
```

Đây là **đoạn trích minh họa**, không phải tệp Java chạy độc lập. Nếu không cần chia sẻ trạng thái, thiết kế dễ hiểu hơn là trả giá trị từ `Callable` rồi đọc trực tiếp qua `Future<Integer>.get()`.

## 5. Áp dụng cho Spring Framework

Spring `TaskExecutor` là abstraction triển khai `java.util.concurrent.Executor`: `execute(Runnable)` có ý nghĩa giao tác vụ tương tự. `AsyncTaskExecutor` bổ sung `submit(...)` để nhận `Future`; `ThreadPoolTaskExecutor` là triển khai phổ biến và dùng pool JDK bên dưới. Do đó các quy tắc xuất bản dữ liệu theo submit và chờ kết quả vẫn là quy tắc JMM của Java. Lưu ý một số executor (như `SyncTaskExecutor`) chạy ngay trên luồng gọi: tên `TaskExecutor` tự nó không hứa luôn có luồng khác.

Trong ứng dụng Spring, nếu cần lấy kết quả, hãy giữ `Future` do `AsyncTaskExecutor.submit` trả về rồi dùng `get()` ở nơi cần giá trị. Ví dụ logic bên trong một Spring bean đã được inject `AsyncTaskExecutor executor`:

```java
int input = 21;
Future<Integer> future = executor.submit(() -> input * 2);
int value = future.get(); // value = 42
```

Đoạn này cần bean `executor` và xử lý `InterruptedException`/`ExecutionException` trong ngữ cảnh ứng dụng; nó là **mảnh mã tích hợp**, không phải chương trình Java độc lập.

### Với `@Async`

Khi `@EnableAsync` đã được bật, một lệnh gọi **đi qua Spring proxy** tới phương thức `@Async` được gửi cho executor cấu hình. Phương thức có thể trả `Future<T>` hoặc `CompletableFuture<T>`. Ví dụ phương thức trong Spring-managed service:

```java
@Async("workExecutor")
public CompletableFuture<Integer> twice(int number) {
    return CompletableFuture.completedFuture(number * 2);
}
```

Giả sử `workExecutor` là bean executor được cấu hình, và một **bean khác** nhận service này qua injection:

```java
CompletableFuture<Integer> future = asyncCalculationService.twice(21);
int result = future.get(); // result = 42
```

Hai đoạn trên là **mảnh mã Spring**, cần import, bean/service, `@EnableAsync`, executor và wiring tương ứng để chạy trong ứng dụng thực tế. Dù phần thân phương thức trả `completedFuture` khi xử lý xong, **Future mà proxy trả cho caller** đại diện cho quá trình async; caller chỉ dùng kết quả sau khi Future đó hoàn tất. Nếu một phương thức gọi `this.twice(...)` bên trong cùng bean trong chế độ proxy mặc định, lời gọi có thể bỏ qua proxy nên **không có hand-off bất đồng bộ như dự tính**.

Với phương thức `@Async void`, caller không có `Future` để chờ hoặc nhận lỗi/kết quả. Nếu cần xác nhận hoàn tất, ưu tiên một kiểu trả về Future phù hợp hoặc dùng cơ chế đồng bộ rõ ràng; đừng đọc trực tiếp một biến chia sẻ rồi coi nó là kết quả tác vụ.

### `CompletableFuture` và callback

`CompletableFuture` là một `Future`: `get()` chờ kết quả; `join()` cũng lấy kết quả sau khi hoàn tất nhưng khác cách báo ngoại lệ (thường bọc lỗi trong `CompletionException` thay vì checked `ExecutionException`). Các stage như `thenApply` chỉ chạy khi stage nguồn có kết quả; tuy nhiên hậu tố `Async` và executor được chỉ định mới quyết định lịch thực thi stage, **không nên suy luận tên luồng từ việc callback tồn tại**.

Khi diễn giải bảo đảm theo tài liệu JDK một cách chính xác, hãy lấy **`Future.get()`** làm mốc đối chiếu rõ nhất cho cạnh *task → consumer*. Nếu xử lý chuỗi CompletableFuture, thiết kế để stage phụ thuộc vào đúng Future, không dựa vào việc hai tác vụ tình cờ chạy chung pool.

## 6. Kết nối nhiều tác vụ bằng tính bắc cầu

Giả sử task A viết một giá trị, task B phải đọc giá trị đó. Chỉ submit A rồi submit B liên tiếp **không đủ** để khẳng định A đã xong trước B; pool nhiều luồng có thể chạy chúng song song.

Nếu caller cần B nhìn thấy ghi của A, một cách trực tiếp là chờ A trước khi gửi B:

```java
Future<?> writer = executor.submit(() -> state.output = 99);
writer.get();
Future<Integer> reader = executor.submit(() -> state.output);
System.out.println(reader.get()); // 99, nếu không có ghi cạnh tranh khác.
```

Chuỗi lý do là: **ghi của A → `writer.get()` trở về → caller submit B → B đọc**. Các cạnh kết hợp bằng tính bắc cầu. Đây cũng là lý do luồng xử lý nhiều giai đoạn nên thể hiện quan hệ phụ thuộc bằng Future/CompletionStage hoặc cơ chế đồng bộ rõ ràng.

Ngược lại, đợi **tất cả** Future sau khi đã submit các task không tự sửa race **giữa chính các task**. Ví dụ hai task cùng làm `counter++` trên `int` thường có thể mất cập nhật; cần `AtomicInteger`, `synchronized`, lock hoặc phân chia dữ liệu hợp lý. `Future.get()` giúp caller nhận **kết quả đã hoàn tất**, không làm các thao tác đồng thời bên trong trở thành atomic.

## 7. Các ranh giới cần phân biệt

| Tình huống | Bảo đảm nên dựa vào | Điều không được suy luận |
| --- | --- | --- |
| Caller ghi dữ liệu, rồi `submit(task)` | Ghi trước submit `hb` hành động trong task | Submit tạo bản sao bất biến của mọi object; ghi sau submit sẽ tự hiện ra |
| `Executor.execute(task)` trả về | Task được gửi để thực thi theo hợp đồng executor | Task đã hoàn tất hoặc caller đã nhận các ghi của nó |
| `Future.get()` trở về bình thường | Tác vụ đã xong, trả kết quả và công bố ghi của tác vụ cho caller | Những task khác cũng xong hoặc mọi thao tác trên shared object đều thread-safe |
| `get(timeout, unit)` ném `TimeoutException` | Chưa lấy được kết quả hoàn tất trong thời hạn chờ | Task đã dừng hay có thể đọc state kết quả ngay |
| `future.isDone()` | Có thể kiểm tra trạng thái hoàn tất của Future | Có thể thay thế tùy ý cho giao thức lấy kết quả và xử lý lỗi |
| `shutdown()` trên `ExecutorService` | Không nhận task mới và cho phép các task đã nộp tiếp tục theo hợp đồng | Phương thức này chờ hết task hoặc là mốc lấy kết quả |
| Hai task cùng pool | Cùng dùng cơ chế thực thi | Một task luôn `hb` task kia chỉ vì thứ tự submit |

Nếu `get()` ném `ExecutionException`, tác vụ đã kết thúc với lỗi nhưng caller **không có kết quả thành công** để sử dụng; cần kiểm tra nguyên nhân. `InterruptedException` cho biết luồng chờ bị ngắt; khi tự bắt ngoại lệ thay vì chuyển tiếp, thường cần khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()`. `CancellationException` cho biết Future bị hủy. Hủy Future hoặc timeout không phải lời cam kết mọi code của task đã dừng ngay.

## 8. Những lỗi thường gặp và cách sửa

1. **Ghi dữ liệu sau khi submit rồi mong task thấy.** Chuẩn bị input hoàn chỉnh **trước** submit; nếu input thay đổi trong khi task chạy, dùng cấu trúc immutable, `volatile`, lock hoặc giao thức đồng bộ phù hợp.
2. **Dùng `Thread.sleep()` để “đợi đủ lâu”.** Ngủ không tạo ra cạnh kết quả như `Future.get()`; nó phụ thuộc tốc độ máy và lịch CPU. Chờ đúng Future hoặc tín hiệu đồng bộ đã định nghĩa.
3. **Đọc shared field ngay khi `execute()` trả về.** Dùng `submit()+get()`, `CompletableFuture` hoặc giao thức đồng bộ khác nếu cần kết quả.
4. **Tưởng `get()` chữa mọi race.** Nó giải quyết điểm công bố kết quả từ một tác vụ tới caller; truy cập ghi/đọc đồng thời bên trong nhiều task vẫn cần bảo vệ riêng.
5. **Chờ `get()` ngay sau mọi lần submit trong một vòng lặp.** Cách đó đúng về đồng bộ nhưng có thể tuần tự hóa luồng gọi và làm mất lợi ích song song. Khi các task độc lập, submit trước rồi lấy kết quả từ các Future sau.
6. **Giả định `@Async` luôn chạy async khi gọi trong cùng bean.** Kiểm tra việc đi qua Spring proxy và kiểu trả về; dùng bean khác gọi qua injection để interceptor xử lý.
7. **Coi `Future.get()` thành công dù timeout, hủy hoặc lỗi.** Phân biệt kết quả bình thường với các nhánh ngoại lệ; chỉ xử lý dữ liệu thành công khi thực sự có kết quả.

## 9. Cách tự kiểm tra thiết kế

Trước khi chia sẻ một đối tượng giữa caller và worker, hãy lần theo bốn điểm: **(1)** ghi input nằm trước lời gọi submit hay không, **(2)** có ai khác vẫn đang sửa input không, **(3)** consumer chờ đúng Future/tín hiệu hoàn tất nào, và **(4)** có nhiều task đồng thời đọc-ghi state đó hay không. Nếu không chỉ ra được đường `happens-before` từ write tới read, chưa nên kết luận mã an toàn chỉ vì chạy thử thấy đúng.

**Tài liệu chuẩn để đối chiếu:**
- [Java SE 17 — `java.util.concurrent`, Memory Consistency Properties](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html)
- [Java SE 17 — `ExecutorService`, Memory consistency effects](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-fundamentals-006">Khóa, biến atomic và cấu trúc dữ liệu đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa, biến atomic và cấu trúc dữ liệu đồng thời trong Java

Trong ứng dụng Spring, nhiều request HTTP, tác vụ `@Async`, `@Scheduled` hoặc luồng nền có thể truy cập cùng một đối tượng. Nếu đối tượng chứa trạng thái thay đổi được, chỉ dùng `HashMap` hay biến `int` thông thường là chưa đủ để đảm bảo kết quả đúng. Bài này tập trung vào cơ chế đồng bộ trong Java (`java.util.concurrent`) và cách chọn chúng khi viết ứng dụng Spring.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích **race condition**, **visibility**, **atomicity**, **happens-before** và phân biệt an toàn dữ liệu với khả năng tiến triển của luồng.
- Chọn giữa `synchronized`, `Lock`, `ReadWriteLock`, `StampedLock`, biến atomic và thiết kế bất biến.
- Dùng đúng `ConcurrentHashMap`, `CopyOnWriteArrayList`, các queue đồng thời và collection được bọc đồng bộ.
- Nhận diện thao tác tổng hợp không atomic, xử lý interruption, tránh deadlock và áp dụng các nguyên tắc trên cho Spring singleton bean.

## 2. Vì sao dữ liệu dùng chung gặp lỗi?

Giả sử hai luồng đồng thời chạy `counter++` với `counter = 0`. Phép tăng gồm ba bước logic: đọc giá trị, cộng 1, ghi lại. Cả hai có thể cùng đọc 0 rồi cùng ghi 1; kết quả cuối là 1 thay vì 2. Đây là **lost update**, một dạng race condition. Khi nhiều luồng cùng đọc/ghi dữ liệu mà không có cơ chế đồng bộ phù hợp, vấn đề còn bao gồm việc thấy dữ liệu cũ hoặc thấy một tổ hợp trạng thái không nhất quán.

Ba thuộc tính cần tách biệt:

| Thuộc tính | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Atomicity** | Thao tác có xuất hiện như một đơn vị không bị xen giữa? | Tăng một bộ đếm bằng `AtomicInteger.incrementAndGet()` |
| **Visibility** | Bản cập nhật ở luồng này có được luồng khác nhìn thấy đúng lúc? | Ghi rồi đọc một trường `volatile` |
| **Ordering / happens-before** | Có ràng buộc thứ tự bộ nhớ để luồng đọc thấy trạng thái đã công bố? | Thả khóa rồi luồng khác lấy lại **cùng khóa** |

Mô hình bộ nhớ Java (Java Memory Model, JMM) quy định quan hệ **happens-before**. Một số quan hệ thường dùng: thao tác `unlock` xảy ra trước `lock` tiếp theo trên cùng khóa; ghi `volatile` xảy ra trước lần đọc sau đó đối với chính biến đó; `Thread.start()` công bố hành động trước khi start cho thread mới; hành động của thread xảy ra trước khi một thread khác `join()` thành công. Các lớp atomic và concurrent collection có bảo đảm bộ nhớ riêng theo API.

**`volatile` không biến `counter++` thành atomic**: nó chủ yếu giải quyết visibility và ordering cho từng lần truy cập. `synchronized`, `Lock` hoặc thao tác atomic thích hợp mới bảo vệ được phép cập nhật tổng hợp. Không dùng `Thread.sleep()` để sửa race condition: độ trễ không tạo ra hợp đồng đồng bộ đáng tin cậy.

Một hệ thống **thread-safe** giữ đúng bất biến dữ liệu dù nhiều luồng thực thi. Tuy nhiên, nó vẫn có thể **deadlock** (các luồng chờ nhau), **starvation** (một luồng liên tục không được phục vụ) hoặc chậm vì tranh chấp khóa. Cần kiểm tra cả correctness lẫn liveness.

## 3. Khóa nội tại: `synchronized`

Mỗi object Java có monitor, dùng được với `synchronized`. Trong vùng khóa, chỉ một luồng sở hữu monitor tại một thời điểm; monitor đồng thời cung cấp quan hệ visibility khi thả và lấy khóa. Các phương thức `synchronized` trên cùng instance dùng chính monitor của instance đó; `static synchronized` dùng monitor của đối tượng `Class`.

Ví dụ một counter có thể dùng cùng khóa cho cả đọc lẫn ghi:

~~~java
public final class SynchronizedCounter {
    private final Object monitor = new Object();
    private int value;

    public void increment() {
        synchronized (monitor) {
            value++;
        }
    }

    public int get() {
        synchronized (monitor) {
            return value;
        }
    }
}
~~~

Đây là một lớp độc lập có thể biên dịch (không chứa `main`); các lời gọi `increment()` và `get()` đều sử dụng cùng đối tượng khóa. Nếu `get()` đọc trực tiếp `value` ở ngoài vùng `synchronized`, giao thức bảo vệ trạng thái bị phá vỡ. Nên dùng khóa `private final`; tránh khóa trên chuỗi literal, đối tượng công khai hoặc đối tượng mà mã bên ngoài cũng có thể khóa.

Monitor **reentrant**: luồng đang giữ khóa có thể vào lại một phương thức khóa cùng monitor mà không tự deadlock. `synchronized` tự thả monitor khi ra khỏi khối, kể cả có exception. Nó phù hợp với vùng tới hạn ngắn và logic đơn giản. Khi cần thử lấy khóa có timeout, nhận interruption khi chờ khóa hoặc dùng nhiều điều kiện chờ, cân nhắc `ReentrantLock`.

## 4. `Lock`, `ReentrantLock` và `Condition`

`java.util.concurrent.locks.Lock` tách thao tác lấy/thả khóa khỏi cú pháp khối `synchronized`. `ReentrantLock` thường dùng nhất. Các thao tác quan trọng:

- `lock()`: chờ đến khi lấy được khóa; bản thân việc chờ kiểu này không phản hồi interruption bằng `InterruptedException`.
- `lockInterruptibly()`: có thể hủy chờ khi thread bị interrupt.
- `tryLock()` hoặc `tryLock(timeout, unit)`: thử lấy khóa ngay hoặc trong một khoảng thời gian; dạng có timeout có thể ném `InterruptedException`.
- `unlock()`: phải gọi **trong `finally` sau khi đã lấy khóa thành công**; gọi khi không sở hữu khóa sẽ lỗi.

Ví dụ đầy đủ sau phối hợp `ReentrantLock` cho bất biến số dư và `AtomicInteger` cho số thao tác hoàn tất. Tệp **LockAtomicDemo.java**, Java 17+:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public final class LockAtomicDemo {
    private static final class Wallet {
        private final ReentrantLock lock = new ReentrantLock();
        private int balance;

        void add(int amount) {
            lock.lock();
            try {
                balance += amount;
            } finally {
                lock.unlock();
            }
        }

        int balanceValue() {
            lock.lock();
            try {
                return balance;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        final int operations = 1_000;
        Wallet wallet = new Wallet();
        AtomicInteger processed = new AtomicInteger();
        CountDownLatch finished = new CountDownLatch(operations);
        ExecutorService pool = Executors.newFixedThreadPool(4);

        try {
            for (int i = 0; i < operations; i++) {
                pool.execute(() -> {
                    try {
                        wallet.add(2);
                        processed.incrementAndGet();
                    } finally {
                        finished.countDown();
                    }
                });
            }

            finished.await();
            System.out.println("processed=" + processed.get());
            System.out.println("balance=" + wallet.balanceValue());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac LockAtomicDemo.java
java LockAtomicDemo
~~~

Stdout:

~~~text
processed=1000
balance=2000
~~~

`finished.await()` đợi 1.000 task gọi `countDown()` trước khi đọc kết quả; do đó kết quả không phụ thuộc thứ tự chạy cụ thể. Trong thực tế phải xử lý lỗi của task, giới hạn hàng đợi executor và có chiến lược shutdown; ví dụ này cố ý tập trung vào tính đúng của hai loại trạng thái.

### `Condition`: chờ một điều kiện của trạng thái

Một `ReentrantLock` có thể tạo một hoặc nhiều `Condition` qua `newCondition()`. Khi gọi `await()`, thread **nhả khóa trong lúc chờ**, rồi phải lấy lại khóa trước khi `await()` trở về. Thread làm thay đổi điều kiện có thể gọi `signal()`/`signalAll()` khi đang giữ khóa tương ứng. Luôn kiểm tra điều kiện bằng vòng `while`, không dùng `if`: có thể xảy ra wakeup bất ngờ hoặc điều kiện đã đổi trước khi thread được chạy.

Mẫu logic (đoạn minh họa nằm trong phương thức đang giữ `lock`, với `notEmpty` là `Condition` và `queue` được bảo vệ bởi khóa đó):

~~~java
while (queue.isEmpty()) {
    notEmpty.await();
}
var item = queue.removeFirst();
~~~

Chỉ dùng mô hình này khi thật sự cần điều kiện tùy biến. Với hàng đợi producer/consumer tiêu chuẩn, `BlockingQueue` đã đóng gói toàn bộ logic chờ và đánh thức, thường đơn giản và ít lỗi hơn.

### Khóa đọc/ghi và optimistic read

- `ReentrantReadWriteLock` có **read lock** cho nhiều reader đồng thời và **write lock** độc quyền. Có ích khi đọc nhiều, ghi ít và vùng đọc tương đối đáng kể. Không mặc định dùng vì overhead và vì một reader cố nâng cấp trực tiếp lên write lock có thể bị kẹt. Tính công bằng có tùy chọn, nhưng có chi phí.
- `StampedLock` hỗ trợ read/write lock và **optimistic read** bằng stamp: đọc dữ liệu, sau đó gọi `validate(stamp)`; nếu không hợp lệ, phải đọc lại dưới read lock. Không được sử dụng tổ hợp dữ liệu đọc thử trước khi validation đạt. Khóa này **không reentrant**, không có `Condition` theo cách `ReentrantLock` cung cấp; cần thận trọng khi cấu trúc dữ liệu phức tạp.
- Khóa công bằng như `new ReentrantLock(true)` có thể giảm hiện tượng chờ bất lợi nhưng thường giảm thông lượng; nó không biến scheduling của hệ điều hành thành hoàn toàn công bằng.

## 5. Biến atomic và Compare-and-Set (CAS)

Các lớp trong `java.util.concurrent.atomic` cung cấp cập nhật atomic trên một biến/trạng thái riêng lẻ mà không cần tự viết `synchronized`. Các lựa chọn phổ biến:

| Lớp / công cụ | Mục đích |
| --- | --- |
| `AtomicInteger`, `AtomicLong` | Đếm, tăng/giảm, cập nhật số với thao tác atomic |
| `AtomicBoolean` | Cờ trạng thái cần chuyển đổi/đọc-ghi atomic |
| `AtomicReference<T>` | Thay một reference atomically, có thể trỏ đến trạng thái bất biến |
| `AtomicStampedReference<T>` | Gắn version/stamp để phát hiện một số dạng **ABA** |
| `LongAdder`, `DoubleAdder` | Tích lũy thống kê dưới mức tranh chấp cập nhật cao |
| `AtomicIntegerArray` và họ atomic array | Cập nhật atomic cho từng phần tử mảng |

Một số API của `AtomicInteger`:

- `get()`, `set(v)`, `getAndSet(v)`: đọc/ghi/đổi giá trị.
- `incrementAndGet()` trả giá trị **sau** khi tăng; `getAndIncrement()` trả giá trị **trước** khi tăng.
- `addAndGet(delta)`, `updateAndGet(f)`, `accumulateAndGet(x, f)`: cập nhật theo phép toán.
- `compareAndSet(expected, newValue)` (CAS): chỉ ghi nếu giá trị hiện tại vẫn bằng expected; kết quả là `true` khi thành công.

CAS tạo cơ chế **optimistic retry**. Khi có tranh chấp, thread đọc lại trạng thái rồi thử lại; phép cập nhật thành công của atomic variable có tính nguyên tử. Ví dụ giữ số chỗ còn trống không âm:

~~~java
AtomicInteger seats = new AtomicInteger(3);
boolean reserved = false;

while (true) {
    int remaining = seats.get();
    if (remaining == 0) {
        break;
    }
    if (seats.compareAndSet(remaining, remaining - 1)) {
        reserved = true;
        break;
    }
}
~~~

Đây là **đoạn mã minh họa**, cần import `java.util.concurrent.atomic.AtomicInteger` và đặt trong phương thức. Nếu CAS thất bại vì thread khác đã đổi giá trị, vòng lặp đọc lại rồi quyết định. Việc trừ chỗ được bảo vệ; còn hành động thanh toán hoặc ghi database là tác vụ khác, cần hợp đồng nhất quán ở tầng phù hợp. Không đặt lời gọi mạng, log có tác dụng phụ hay tạo giao dịch bên trong hàm truyền cho `updateAndGet`: lambda có thể được tính lại khi CAS phải retry.

**ABA** xảy ra khi trạng thái chuyển từ A → B → A: CAS chỉ so sánh A hiện tại với A mong đợi có thể không biết trạng thái đã từng thay đổi. Chỉ cần cơ chế version/stamp khi lịch sử chuyển đổi quan trọng cho thuật toán; đây không phải lỗi của mọi bộ đếm atomic.

**Atomic không đồng nghĩa với một giao dịch nhiều trường.** Hai `AtomicInteger` độc lập chỉ bảo đảm riêng từng phép cập nhật, không tự giữ bất biến như `available + reserved = capacity` trong lúc nhiều thread đọc/ghi. Khi cần nhiều trường thay đổi cùng nhau, dùng chung một khóa, hoặc đóng gói trạng thái bất biến vào một `AtomicReference<State>` và đổi cả reference bằng CAS có retry.

### `AtomicLong` hay `LongAdder`?

`AtomicLong` thuận tiện khi cần giá trị chính xác tại từng thao tác atomic, ví dụ lấy số thứ tự tiếp theo. `LongAdder` phân tán cập nhật vào nhiều ô nội bộ để giảm tranh chấp, thường thích hợp với **metrics, số lần sự kiện**. `LongAdder.sum()` không phải một snapshot atomic khi vẫn có thread cập nhật; khi tất cả cập nhật đã kết thúc, tổng là chính xác. `reset()`/`sumThenReset()` cũng cần xem xét race nếu concurrent update.

## 6. Cấu trúc dữ liệu đồng thời

Một collection thread-safe bảo vệ những thao tác được API cam kết. Điều đó **không** đồng nghĩa chuỗi nhiều lời gọi tự động atomic hoặc mọi lần duyệt đều là snapshot nhất quán.

| Collection / cơ chế | Cách hoạt động và tình huống dùng | Giới hạn cần nhớ |
| --- | --- | --- |
| `ConcurrentHashMap<K,V>` | Map cho nhiều reader/writer, thao tác key-level như `putIfAbsent`, `compute`, `merge` | Không chấp nhận key/value `null`; iterator weakly consistent; thao tác tổng hợp không tự atomic |
| `ConcurrentSkipListMap/Set` | Map/set sắp thứ tự với truy cập đồng thời | Thường tốn chi phí hơn hash-based khi không cần thứ tự |
| `CopyOnWriteArrayList` / `CopyOnWriteArraySet` | Bản sao khi ghi; iterator xem snapshot | Ghi tốn chi phí sao chép, tốn bộ nhớ; hợp với đọc/duyệt nhiều, ghi hiếm |
| `ConcurrentLinkedQueue` / `ConcurrentLinkedDeque` | Queue/deque đồng thời phi chặn, không đợi phần tử | Không cung cấp backpressure và không chặn khi rỗng; `size()` có thể tốn O(n) và biến động |
| `ArrayBlockingQueue` | Queue có sức chứa cố định; `put/take` có thể chờ | Chờ vô hạn nếu không có đối tác; cần shutdown/interruption |
| `LinkedBlockingQueue` | Queue chặn, có thể cấu hình capacity | Nếu không đặt capacity, giới hạn mặc định rất lớn; dễ tích hàng đợi ngoài dự tính |
| `PriorityBlockingQueue` | Lấy phần tử theo ưu tiên | Không bị chặn bởi capacity mặc định; phần tử cùng độ ưu tiên không hứa FIFO |
| `Collections.synchronizedList/Map` | Bọc collection thường để đồng bộ từng phương thức | Phải tự khóa quanh những chuỗi thao tác và lúc duyệt |

### Map an toàn vẫn có thể bị lost update

Đoạn sau **sai** khi nhiều thread cùng tăng một key:

~~~java
ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
counts.put("ok", counts.getOrDefault("ok", 0) + 1);
~~~

Từng lệnh `get` và `put` thread-safe, nhưng **cặp get → put không atomic**. Dùng API gộp cập nhật vào một thao tác:

~~~java
counts.merge("ok", 1, Integer::sum);
~~~

`merge`/`compute` của `ConcurrentHashMap` bảo vệ việc cập nhật cho key liên quan. Giữ mapping/remapping function **ngắn, thuần tính toán**, tránh I/O lâu hoặc tự thay đổi cùng map bên trong hàm. Khi cần bộ đếm tần suất tranh chấp cao, idiom chuẩn là:

~~~java
ConcurrentHashMap<String, LongAdder> frequencies = new ConcurrentHashMap<>();
frequencies.computeIfAbsent("ok", k -> new LongAdder()).increment();
~~~

Ở ví dụ này, `computeIfAbsent` đảm bảo cùng key được gắn một `LongAdder` phù hợp; `increment()` của adder xử lý cập nhật từ nhiều thread. Cần import `java.util.concurrent.ConcurrentHashMap` và `java.util.concurrent.atomic.LongAdder`. Tính nhất quán khi đọc cả map hoặc tổng nhiều key vẫn cần được thiết kế riêng.

### Duyệt collection: snapshot, weak consistency và khóa ngoài

- Iterator của `ConcurrentHashMap` thường **weakly consistent**: không ném `ConcurrentModificationException` theo kiểu fail-fast thông thường, có thể thấy một phần thay đổi gần đây nhưng không hứa snapshot giao dịch.
- Iterator của `CopyOnWriteArrayList` xem snapshot tại lúc tạo iterator; thay đổi sau đó không xuất hiện trong vòng duyệt đang chạy. `Iterator.remove()` không hỗ trợ.
- Với `Collections.synchronizedList(list)`, khi duyệt phải đồng bộ trên **wrapper list**: `synchronized (list) { for (var item : list) { ... } }`. Chỉ synchronized từng phương thức thì vòng duyệt vẫn có thể bị can thiệp.
- Collection bất biến như `List.of(...)` loại bỏ việc thay đổi chính cấu trúc list; nếu phần tử bên trong là object mutable thì bản thân object vẫn có thể cần đồng bộ. `Collections.unmodifiableList` là view không cho sửa qua view, không khiến backing list thành bất biến.

### `BlockingQueue`: producer/consumer và backpressure

`BlockingQueue.put` chờ khi queue đầy, `take` chờ khi rỗng; `offer(value, timeout, unit)`/`poll(timeout, unit)` có thể cho phép timeout. Với producer nhanh hơn consumer, queue **bounded** giúp giới hạn lượng công việc chờ và tạo backpressure. Không nên tăng capacity vô hạn chỉ để giấu vấn đề quá tải.

Ví dụ Java 17+ độc lập, tệp **QueueMapDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class QueueMapDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);
        ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    String event = queue.take();
                    if ("STOP".equals(event)) {
                        break;
                    }
                    counts.computeIfAbsent(event, k -> new LongAdder())
                          .increment();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        queue.put("ok");
        queue.put("ok");
        queue.put("fail");
        queue.put("STOP");
        consumer.join();

        System.out.println("ok=" + counts.get("ok").sum());
        System.out.println("fail=" + counts.get("fail").sum());
    }
}
~~~

Chạy:

~~~bash
javac QueueMapDemo.java
java QueueMapDemo
~~~

Stdout:

~~~text
ok=2
fail=1
~~~

Queue chỉ chứa tối đa hai phần tử tại một thời điểm; `put` có thể đợi consumer lấy bớt. Chuỗi `"STOP"` đóng vai trò **sentinel** để kết thúc consumer trong demo; hệ thống thực phải dùng tín hiệu không thể trùng dữ liệu hợp lệ và, nếu có nhiều consumer, cần chiến lược dừng cho từng consumer. `join()` giúp main đợi consumer xử lý xong trước khi đọc kết quả. Trong ứng dụng thật còn phải xử lý trường hợp producer/consumer lỗi, timeout, cancellation và drain queue khi shutdown.

## 7. Áp dụng vào Spring

Thông thường `@Service`, `@Controller` và nhiều bean khác có scope singleton: nhiều request có thể gọi **cùng instance** đồng thời. Spring tạo và công bố bean an toàn, nhưng những trường **mutable sau khởi tạo** không vì thế mà tự thread-safe. Ví dụ một bộ đếm sự kiện trong bộ nhớ của từng instance ứng dụng:

~~~java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Service;

@Service
public final class EventStats {
    private final ConcurrentHashMap<String, LongAdder> counts =
            new ConcurrentHashMap<>();

    public void record(String event) {
        counts.computeIfAbsent(event, k -> new LongAdder()).increment();
    }

    public long count(String event) {
        LongAdder value = counts.get(event);
        return value == null ? 0L : value.sum();
    }
}
~~~

Đây là **Spring component minh họa**, tệp `EventStats.java`, cần dependency Spring Framework và không có `main`. Bản đồ và các adder xử lý truy cập đồng thời trong **một JVM**; kết quả `count` là tổng quan sát được, không phải snapshot atomic nếu đang cập nhật. Không dùng dữ liệu này như số dư tài khoản, kho hàng hoặc nguồn sự thật cần tồn tại bền vững.

Khi thêm `@Async`, `@Scheduled` hoặc cấu hình `TaskExecutor`/`ThreadPoolTaskExecutor`:

1. Xác định các trường được chia sẻ giữa nhiều lời gọi; ưu tiên biến cục bộ, đối tượng bất biến và truyền state theo tham số. Tránh giữ thông tin riêng từng request trên field của singleton.
2. Nếu phải chia sẻ, mô tả **bất biến** và bảo vệ **mọi điểm truy cập** bằng cùng giao thức: atomic, lock hoặc cấu trúc đồng thời.
3. Thiết lập giới hạn thread/queue phù hợp, xử lý exception, timeout, interruption và đóng tài nguyên đúng vòng đời. Một số kiểu gọi nội bộ trong cùng bean có thể bỏ qua proxy `@Async` mặc định.
4. Không giữ JVM lock trong lúc chờ HTTP, database hoặc tác vụ kéo dài nếu có thể thay bằng thiết kế bất đồng bộ, queue hay giao dịch.

**Ranh giới cực kỳ quan trọng:** `synchronized`, `ReentrantLock` và `ConcurrentHashMap` chỉ đồng bộ trong phạm vi **một tiến trình JVM**. Nếu ứng dụng Spring chạy 3 replica, mỗi replica có trạng thái và khóa riêng. Với dữ liệu nghiệp vụ xuyên tiến trình, cân nhắc transaction của database, optimistic locking bằng version, câu lệnh cập nhật có điều kiện, unique constraint hoặc cơ chế phân phối phù hợp. `@Transactional` xử lý ngữ nghĩa giao dịch tài nguyên như database; nó **không tự khóa** trường Java trong singleton bean. Ngược lại, giữ JVM lock cũng không thay thế được transaction của database.

## 8. Bảng chọn giải pháp theo tình huống

| Yêu cầu thực tế | Khởi đầu nên cân nhắc | Vì sao |
| --- | --- | --- |
| Chỉ tăng một số đếm, cần thao tác đọc/cập nhật atomic | `AtomicInteger` / `AtomicLong` | Đơn giản, không cần tự quản lý khóa |
| Đếm sự kiện rất nhiều luồng, đọc metrics không cần snapshot tức thời | `LongAdder` | Giảm tranh chấp cập nhật |
| Nhiều field phải thay đổi cùng một bất biến | `synchronized` / `ReentrantLock` | Bảo vệ cả vùng cập nhật như một đơn vị |
| Muốn timeout hoặc interruption khi chờ lấy khóa | `ReentrantLock.tryLock` / `lockInterruptibly` | Kiểm soát liveness và hủy chờ |
| Đọc nhiều, ghi ít và thời gian giữ khóa đáng kể | `ReentrantReadWriteLock`; đôi khi `StampedLock` | Có thể tăng song song giữa reader sau khi đo đạc |
| Map nhiều luồng; thao tác theo key | `ConcurrentHashMap.compute/merge` | Cập nhật key atomic theo hợp đồng API |
| Danh sách listener đọc rất nhiều, thay đổi rất ít | `CopyOnWriteArrayList` | Duyệt snapshot, không cần tự khóa iterator |
| Pipeline producer/consumer có giới hạn tồn đọng | `ArrayBlockingQueue` / bounded `LinkedBlockingQueue` | Có backpressure, hỗ trợ chờ |
| Chỉ cần thứ tự và cập nhật đồng thời | `ConcurrentSkipListMap/Set` | Cấu trúc đồng thời có sắp thứ tự |
| Dữ liệu không cần thay đổi sau khởi tạo | Đối tượng bất biến / immutable collection | Giảm hẳn shared mutable state |
| Tồn kho/số dư dùng chung nhiều replica | Database transaction / optimistic version | JVM lock không bao phủ các replica |

Không có cấu trúc nào luôn nhanh nhất. Chi phí phụ thuộc contention, tần suất đọc/ghi, số lượng key, kích thước dữ liệu, độ dài vùng khóa và yêu cầu tính nhất quán. Đo bằng dữ liệu tải gần thực tế trước khi tối ưu.

## 9. Lỗi phổ biến và cách phòng tránh

1. **Dùng `volatile int count` rồi `count++`.** Đây vẫn là read-modify-write không atomic. Thay bằng `AtomicInteger` hoặc đồng bộ phù hợp.
2. **Tưởng `ConcurrentHashMap` khiến mọi chuỗi lệnh an toàn.** Mẫu `if (!map.containsKey(k)) map.put(k,v)` vẫn có race. Dùng `putIfAbsent`, `computeIfAbsent`, `compute` hoặc `merge` theo yêu cầu.
3. **Đọc một phần state dưới khóa, phần khác ngoài khóa.** Điều kiện bất biến nhiều field có thể bị vi phạm. Xác định rõ vùng bảo vệ và cùng sử dụng một khóa.
4. **Quên `unlock()` hoặc giữ khóa khi làm I/O.** Luôn `try/finally`; tách tính toán ngắn trong vùng khóa khỏi mạng/DB nếu có thể.
5. **Lấy nhiều khóa theo thứ tự bất kỳ.** Hai thread có thể khóa A rồi chờ B và khóa B rồi chờ A. Dùng một thứ tự khóa toàn cục, giảm nesting, hoặc `tryLock` với timeout và chiến lược retry có giới hạn.
6. **Bỏ qua `InterruptedException`.** Nếu không thể ném tiếp, thường nên khôi phục cờ bằng `Thread.currentThread().interrupt()` và thoát/cancel logic thích hợp, tránh tiếp tục âm thầm chờ.
7. **Dùng `CopyOnWriteArrayList` cho danh sách cập nhật liên tục.** Mỗi lần ghi sao chép backing array, dễ tốn CPU/bộ nhớ.
8. **Dùng `LongAdder.sum()` để đưa ra quyết định cần snapshot tuyến tính.** `sum()` không atomic trong lúc cập nhật; cân nhắc atomic counter hoặc khóa.
9. **Duyệt `Collections.synchronizedMap/list` mà không khóa ngoài.** Wrapper không tự bảo vệ cả vòng lặp; đồng bộ trên wrapper trong suốt thời gian duyệt.
10. **Cho rằng test chạy một lần không lộ race là code an toàn.** Interleaving phụ thuộc timing; kiểm tra bất biến, dùng barrier/latch cho tình huống tái lập, stress test nhiều lần. Dùng JFR/thread dump để phân tích tranh chấp/deadlock; dùng JMH khi so sánh hiệu năng.
11. **Giữ dữ liệu nghiệp vụ trên singleton field rồi scale ngang.** State tại mỗi replica khác nhau; chọn kho dữ liệu chung hoặc kiến trúc phân vùng phù hợp.
12. **Nghĩ virtual thread loại bỏ race condition.** Virtual thread (Java 21+) vẫn truy cập shared state như thread khác; vẫn cần đúng cơ chế đồng bộ, và nên hạn chế vùng khóa chặn dài.

## 10. Câu hỏi tự kiểm tra

1. Vì sao `volatile` giúp các luồng nhìn thấy thay đổi nhưng không đảm bảo `x++` chính xác?
2. Khi nào một `AtomicReference` tới object state bất biến hợp lý hơn nhiều biến atomic rời rạc?
3. Vì sao `map.get` rồi `map.put` không an toàn dù `map` là `ConcurrentHashMap`? Bạn sẽ chọn `merge` hay `compute`?
4. `LongAdder` phù hợp với metrics, nhưng vì sao không nên dùng `sum()` để quyết định còn đúng một suất đặt chỗ?
5. Điều gì xảy ra nếu `ReentrantLock.unlock()` không nằm trong `finally` hoặc `Condition.await()` chỉ kiểm tra điều kiện bằng `if`?
6. Một Spring service chạy ba replica có thể dùng `synchronized` để bảo đảm không bán quá số lượng hàng trong database không? Vì sao?

## 11. Tài liệu tham khảo chính thức

- [Java SE 17 – java.util.concurrent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html)
- [Java SE 17 – concurrent locks](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/package-summary.html)
- [Java SE 17 – atomic variables](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/package-summary.html)
- [Java SE 17 – ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

**Điểm cần ghi nhớ:** Hãy bắt đầu từ bất biến của dữ liệu và phạm vi chia sẻ (một object, một JVM hay nhiều tiến trình). Chọn công cụ nhỏ nhất đáp ứng atomicity, visibility và liveness; chỉ tối ưu dựa trên contention và nhu cầu quan sát thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-fundamentals-007">Chờ tài nguyên khác với sử dụng CPU</a>

<details>
<summary>Xem chi tiết</summary>

# Chờ tài nguyên khác với sử dụng CPU trong Java và Spring Framework

## 1. Hiểu đúng hai loại thời gian của một luồng

Một luồng (thread) có thể **đang tồn tại, đang giữ chỗ trong một thread pool, nhưng hầu như không tiêu thụ CPU**. Phân biệt điều này là nền tảng để chẩn đoán ứng dụng Java/Spring chậm, cấu hình executor và chọn mô hình đồng thời phù hợp.

- **Sử dụng CPU (CPU work):** luồng được CPU thực thi để tính toán, duyệt dữ liệu, mã hóa, parse JSON, xử lý thuật toán, thực hiện logic nghiệp vụ. Trong khoảng thời gian ấy, luồng cần thời gian xử lý thực sự của một lõi CPU.
- **Chờ tài nguyên (waiting):** luồng chưa thể tiếp tục vì phụ thuộc vào điều kiện bên ngoài: phản hồi mạng/cơ sở dữ liệu, kết nối từ pool, khóa do luồng khác giữ, kết quả từ tác vụ khác, dữ liệu từ hàng đợi hoặc bộ hẹn giờ. Luồng có thể không dùng CPU trong thời gian chờ nhưng vẫn chiếm tài nguyên quản lý, và với blocking platform thread, vẫn chiếm một luồng thực thi của pool.

Ví dụ một yêu cầu HTTP tốn 420 ms:

| Giai đoạn | Thời gian minh họa | Hoạt động chủ yếu |
| --- | ---: | --- |
| Xác thực dữ liệu đầu vào | 15 ms | CPU |
| Chờ lấy kết nối từ connection pool | 80 ms | Chờ |
| Chờ câu SQL và dữ liệu trả về | 270 ms | Chờ |
| Ánh xạ kết quả, tạo JSON | 25 ms | CPU |
| Chờ các tác vụ khác/lịch CPU | 30 ms | Chờ hoặc chưa được chạy |
| **Tổng thời gian phản hồi** | **420 ms** | **CPU thực dùng có thể chỉ khoảng 40 ms** |

Các số trong bảng chỉ để minh họa; thời gian CPU thực còn phụ thuộc driver, kernel, GC và cách đo. Một request chậm **không đồng nghĩa** CPU đang bận. Ngược lại, CPU cao không nhất thiết cho biết luồng đang kẹt mạng.

### Wall-clock time và CPU time

- **Wall-clock time:** thời gian thực trôi qua từ lúc bắt đầu đến lúc kết thúc, đo bằng đồng hồ đơn điệu như `System.nanoTime()` khi cần đo khoảng thời gian.
- **Thread CPU time:** lượng thời gian CPU thực thi mã của luồng (thường gồm user và system CPU time), có thể đo bằng `ThreadMXBean` nếu JVM hỗ trợ.
- **Thời gian chờ/không được chạy:** phần còn lại không thể suy ra chính xác chỉ bằng phép trừ, vì còn có thời gian chờ scheduler, GC/safepoint và sai số đo; muốn phân rã chi tiết cần thêm tracing, JFR hoặc metric.

Với **một luồng** trên một lõi tại một thời điểm, tỷ lệ `CPU time / wall-clock time` là chỉ báo gần đúng: gần 100% gợi ý CPU-bound; rất thấp trong lúc request vẫn lâu gợi ý có chờ hoặc bị mất lượt chạy. Với **toàn tiến trình**, các luồng chạy đồng thời trên nhiều lõi nên tổng CPU time có thể lớn hơn wall-clock time.

## 2. CPU-bound và wait-bound khác nhau ở đâu?

| Tình huống | CPU có bận liên tục? | Luồng Java đồng bộ có bị giữ lại? | Điểm cần kiểm tra |
| --- | --- | --- | --- |
| Nén ảnh, tính hash, vòng lặp xử lý dữ liệu lớn | Thường có | Có, trong lúc tính toán | CPU profile, thuật toán, lõi CPU |
| `Thread.sleep(500)` | Gần như không | Có: luồng ngủ không xử lý việc khác | Tránh dùng sleep để đồng bộ |
| Đọc JDBC/HTTP bằng lời gọi blocking | Thường không trong phần đợi I/O | Có | Latency, timeout, connection pool, downstream |
| Chờ `synchronized` do thread khác giữ | Không trong phần chờ lock | Có | Lock contention, critical section |
| Chờ `BlockingQueue.take()` khi hàng đợi trống | Không | Có | Producer/consumer, tốc độ cấp dữ liệu |
| `CompletableFuture.join()` hoặc `Future.get()` chưa có kết quả | Không trong phần đợi | Có | Dependency, khả năng deadlock/starvation |
| I/O bất đồng bộ thực sự qua event loop | Có CPU khi xử lý callback, không chiếm thread lúc chờ | Thông thường không giữ một thread riêng suốt thời gian I/O | Driver nonblocking, callback, backpressure |

**CPU-bound** là công việc có nút thắt ở tốc độ tính toán: thêm nhiều runnable threads hơn số lõi CPU thường chỉ tăng cạnh tranh và context switch. **Wait-bound/I/O-bound** là công việc có thời gian trì hoãn chủ yếu bởi tài nguyên khác: CPU thấp vẫn có thể có throughput thấp, queue dài và timeout. Một request thực tế thường gồm *cả hai pha*; hãy đo từng pha thay vì gán nhãn cả endpoint chỉ dựa vào tên API.

### Chờ không có nghĩa là miễn phí

Với **platform thread**, mỗi lời gọi blocking vẫn giữ một thread cho đến khi tiếp tục hoặc timeout. Nếu có 200 yêu cầu cùng chờ DB và mô hình xử lý dành một thread cho mỗi yêu cầu, có thể có xấp xỉ 200 worker bị chiếm, dù CPU còn rảnh. Chúng vẫn tạo áp lực lên stack memory, thread pool, connection pool, bộ nhớ request và khả năng tiếp nhận việc mới.

Điều kiện chờ lâu có thể đến từ chính hệ thống: pool kết nối quá nhỏ, tài nguyên DB cạn, mutex tranh chấp, hoặc một luồng giữ khóa quá lâu. Tăng số thread thường **không chữa được** nút thắt này và còn làm tăng tải lên downstream.

## 3. Java Thread.State cho biết điều gì, và không cho biết điều gì?

`Thread.State` là ảnh chụp trạng thái ở tầng Java, **không phải đồng hồ CPU** hay bản phân loại chính xác mọi loại I/O:

| State | Ý nghĩa chính | Ví dụ |
| --- | --- | --- |
| `NEW` | Đã tạo nhưng chưa start | `new Thread(...)` |
| `RUNNABLE` | Đang chạy hoặc đủ điều kiện chạy theo mô hình JVM | Vòng lặp CPU; đôi lúc cả blocking native I/O |
| `BLOCKED` | Đang chờ vào monitor của `synchronized` | Luồng khác đang giữ cùng monitor |
| `WAITING` | Chờ vô hạn đến khi được đánh thức/đủ điều kiện | `Object.wait()`, `Thread.join()`, một số `park()` |
| `TIMED_WAITING` | Chờ có thời hạn | `Thread.sleep(...)`, `wait(timeout)`, `join(timeout)` |
| `TERMINATED` | Đã kết thúc thực thi | Hàm `run()` đã trả về |

Ba điểm dễ hiểu sai:

1. `RUNNABLE` **không chứng minh** thread đang dùng CPU. Luồng có thể chỉ đủ điều kiện được chạy, đang đợi scheduler, hoặc đang ở một lời gọi native I/O mà JVM vẫn báo RUNNABLE.
2. `BLOCKED` trong `Thread.State` nói riêng về chờ **intrinsic monitor** (`synchronized`). Chờ khóa `ReentrantLock`, `Condition` hoặc `LockSupport.park()` có thể xuất hiện là `WAITING/TIMED_WAITING`.
3. `WAITING` chưa cho biết chờ đó hợp lý hay bất thường. Consumer đợi dữ liệu có thể bình thường; một `join()` không bao giờ kết thúc hoặc deadlock thì là lỗi. Cần đọc stack trace, owner của lock và ngữ cảnh nghiệp vụ.

`Thread.sleep()` không nhả monitor đang giữ trong khối `synchronized`; `Object.wait()` thì nhả monitor tương ứng khi chờ và phải gọi khi đang sở hữu monitor. Vì thế ngủ trong critical section có thể khiến nhiều thread khác `BLOCKED` dù thread giữ khóa không dùng CPU.

Khi đọc thread dump, hãy đặt câu hỏi **“đang chờ cái gì, chờ ai, bao lâu và có tiến triển không?”** thay vì kết luận từ một trạng thái đơn lẻ.

## 4. Thực nghiệm Java 21: cùng thời gian trôi qua, CPU khác nhau

Đo hai công việc đều kéo dài khoảng 300 ms: một vòng tính toán liên tục, một lời gọi ngủ. Chương trình độc lập sau chạy được với JDK 21; `ThreadMXBean` lấy CPU time của platform thread đang chạy `main`.

**Tệp `ThreadCostDemo.java`:**

```java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

public class ThreadCostDemo {
    private static final ThreadMXBean MX =
            ManagementFactory.getThreadMXBean();
    private static volatile long sink;

    @FunctionalInterface
    interface Work {
        void run() throws InterruptedException;
    }

    private static void cpuWork() {
        long until = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(300);
        long sum = 0;
        while (System.nanoTime() < until) {
            sum += System.nanoTime() & 1L;
        }
        sink = sum; // Giữ kết quả để JIT không loại bỏ toàn bộ tính toán.
    }

    private static void measure(String name, Work task)
            throws InterruptedException {
        long beforeCpu = MX.getCurrentThreadCpuTime();
        long beforeWall = System.nanoTime();

        task.run();

        long wallNs = System.nanoTime() - beforeWall;
        long cpuNs = MX.getCurrentThreadCpuTime() - beforeCpu;
        System.out.printf(
                "%s: wall=%d ms, cpu=%d ms%n",
                name,
                TimeUnit.NANOSECONDS.toMillis(wallNs),
                TimeUnit.NANOSECONDS.toMillis(cpuNs));
    }

    public static void main(String[] args)
            throws InterruptedException {
        if (!MX.isCurrentThreadCpuTimeSupported()) {
            System.out.println("JVM khong ho tro thread CPU time.");
            return;
        }
        if (!MX.isThreadCpuTimeEnabled()) {
            MX.setThreadCpuTimeEnabled(true);
        }

        measure("CPU work", ThreadCostDemo::cpuWork);
        measure("Sleep", () -> Thread.sleep(300));
    }
}
```

Chạy:

```bash
javac ThreadCostDemo.java
java ThreadCostDemo
```

Kết quả **không cố định**: cả hai dòng thường có `wall` ít nhất xấp xỉ 300 ms; `CPU work` có `cpu` lớn hơn đáng kể `Sleep`, còn `Sleep` chỉ tiêu thụ một lượng CPU rất nhỏ. Nhiễu từ scheduler, JIT, GC và tải của máy làm số thực tế khác nhau. Trên một số JVM, tính năng CPU time không được hỗ trợ/bị vô hiệu hóa hoặc việc bật tính năng bị giới hạn bởi môi trường. Nếu CPU bị tranh chấp nghiêm trọng, `CPU work` có thể có CPU time thấp hơn nhiều so với wall time; đây cũng là lý do không gán nhãn từ một mẫu đơn lẻ.

**Điều cần rút ra:** hai request cùng chậm 300 ms có thể đòi hỏi tài nguyên CPU rất khác nhau. Trước khi tối ưu, phải xác định hệ thống đang *tính* hay *đợi*.

## 5. Luồng request Spring MVC thường đang chờ ở đâu?

Với cách xử lý đồng bộ thường gặp trong Spring MVC:

```text
HTTP request
  -> servlet container worker
  -> controller -> service
  -> chờ lấy DB connection -> gọi JDBC/HTTP blocking
  -> xử lý kết quả bằng CPU -> ghi response
  -> worker có thể nhận request kế tiếp
```

Trong pha chờ JDBC hoặc HTTP blocking, **thread xử lý request bị chiếm nhưng CPU không cần bận liên tục**. Khi lượng request đồng thời vượt khả năng của worker pool, queue sẽ dài hoặc yêu cầu bị từ chối/timeout, dù mức dùng CPU tổng thể còn thấp.

Cần phân biệt các điểm chờ:

- **Chờ connection pool:** request chưa có connection để gửi SQL; xem số kết nối active/idle/pending và giới hạn pool.
- **Chờ database:** đã gửi query nhưng server DB chậm, lock bảng, query thiếu index hoặc mạng có độ trễ.
- **Chờ HTTP downstream:** service khác đang xử lý chậm hoặc timeout quá dài.
- **Chờ đồng bộ:** một lock hoặc `Future.get()` khiến thread bị giữ; đôi lúc có deadlock hoặc thread-pool starvation.
- **Bận CPU trong ứng dụng:** mapping object lớn, serialize JSON, regex phức tạp, nén/mã hóa hoặc thuật toán không hiệu quả.

### `@Async` không tự biến blocking I/O thành nonblocking I/O

Khi một lời gọi được Spring proxy xử lý qua `@Async` và executor được cấu hình, công việc chạy trên worker khác. Thread gọi có thể được giải phóng sớm, nhưng **worker thực hiện blocking JDBC/HTTP vẫn bị chiếm suốt thời gian chờ**.

Nếu thread gọi lập tức `future.get()`/`join()`, nó lại chặn chính mình. Gọi một method `@Async` qua `this.method()` trong cùng bean thường không đi qua Spring proxy nên không có tác dụng bất đồng bộ mặc định. Cần kiểm tra ranh giới proxy, executor thật, queue, cách xử lý exception và cách truyền kết quả.

Với Spring MVC async request như `Callable`/`DeferredResult`, servlet request thread có thể được trả lại container trong thời gian xử lý bất đồng bộ; tuy nhiên một lời gọi I/O blocking được đưa sang executor khác **vẫn chiếm thread của executor đó**. Giải phóng request thread và không dùng thread để chờ I/O là hai tính chất khác nhau.

## 6. Blocking, nonblocking và virtual threads giải quyết những việc khác nhau

### Blocking platform threads

Phong cách tuần tự, dễ đọc: gọi API, đứng chờ kết quả rồi chạy tiếp. Phù hợp khi concurrency vừa phải, thư viện chủ yếu blocking và giới hạn executor/connection pool rõ ràng. Điểm yếu là một số lượng lớn lời gọi I/O chậm sẽ giữ rất nhiều platform threads.

### Nonblocking I/O và Spring WebFlux

Event loop khởi tạo tác vụ I/O rồi xử lý tác vụ khác trong khi chờ dữ liệu; kết quả trở lại qua callback/reactive signal. Mô hình này có thể duy trì nhiều kết nối đồng thời mà không cần một platform thread đứng chờ cho mỗi kết nối, **nếu toàn bộ đường I/O quan trọng đều nonblocking**.

Trong Spring WebFlux, không gọi `Thread.sleep()`, JDBC blocking hoặc `block()` trên event-loop thread. Nếu phải tích hợp thư viện blocking, có thể *chuyển* lời gọi sang scheduler thích hợp:

```java
// Đoạn minh họa bên trong một bean dùng Project Reactor.
Mono<String> loadLegacy() {
    return Mono.fromCallable(() -> {
        Thread.sleep(200); // Mô phỏng API blocking cũ.
        return "data";
    }).subscribeOn(Schedulers.boundedElastic());
}
```

Đoạn trên cần import `reactor.core.publisher.Mono` và `reactor.core.scheduler.Schedulers` trong một class Spring/Reactor; đây là minh họa, **không phải chương trình Java chạy độc lập**. `boundedElastic()` chuyển việc blocking sang nhóm worker có giới hạn; **không làm bản thân `Thread.sleep` trở thành nonblocking**. Nếu có quá nhiều tác vụ, queue và giới hạn concurrency của scheduler vẫn là nút thắt. Không đặt việc CPU nặng kéo dài lên event loop.

### Virtual threads (Java 21+)

Virtual threads cho phép mã có cú pháp blocking tuần tự nhưng khi gặp nhiều thao tác chờ có hỗ trợ, virtual thread có thể được **unmount** khỏi carrier platform thread để carrier chạy việc khác. Điều này giảm chi phí giữ một platform thread cho mỗi thao tác chờ và có lợi cho lượng lớn tác vụ I/O đồng thời.

Tuy nhiên, virtual threads **không làm CPU xử lý nhanh hơn**, không bỏ qua giới hạn DB/HTTP connection pool và không tự sửa timeout hoặc truy vấn chậm. Trên Java 21, một số tình huống như blocking khi đang giữ monitor `synchronized` có thể *pin* virtual thread vào carrier; hãy dùng JFR/diagnostics để phát hiện và đánh giá trường hợp thực tế. Trạng thái/tính khả dụng của CPU-time monitoring cũng có thể khác với platform thread.

Cả ba lựa chọn đều cần giới hạn downstream, timeout, cancellation và đo thực tế; lựa chọn phụ thuộc thư viện I/O, lưu lượng đồng thời, đặc điểm deploy và mô hình lập trình.

## 7. Thread pool: tại sao tăng số thread không phải câu trả lời mặc định?

Với CPU-bound work, số tác vụ thực sự chạy song song thường bị giới hạn bởi **số lõi CPU hiệu dụng**; pool quá lớn tăng scheduling overhead và tranh chấp bộ nhớ/cache.

Với wait-heavy work, nhiều concurrency hơn *có thể* che giấu latency I/O: trong khi task A đợi, task B có thể chạy. Một phép ước lượng kinh điển cho số worker là `N ≈ số lõi × (1 + W/C)`, với `W` là thời gian chờ và `C` là CPU time của mỗi task. Đây chỉ là mô hình tham khảo khi tải ổn định, không phải công thức cấu hình trực tiếp: phải xét CPU quota container, giới hạn kết nối DB, timeout, queue và memory.

Ví dụ hệ thống nhận ổn định 200 request/s, mỗi request có trung bình 250 ms chờ I/O. Theo định luật Little, trung bình riêng pha này có khoảng `200 × 0,25 = 50` request đồng thời đang chờ. Trong mô hình đồng bộ một request giữ một worker suốt pha này, chỉ riêng chờ I/O có thể chiếm khoảng 50 workers. Trong mô hình nonblocking, vẫn có khoảng 50 request đang chờ, nhưng không nhất thiết 50 platform threads phải chờ cùng chúng.

Khi cấu hình `ThreadPoolTaskExecutor`, servlet pool, Reactor scheduler hoặc virtual-thread executor, hãy xem đồng thời:

- **Active threads, queue depth, rejection và saturation:** pool đang thật sự thiếu worker hay task đang mắc kẹt ở dependency?
- **Số kết nối DB/HTTP và latency downstream:** tăng worker có đẩy nhiều yêu cầu hơn vào một dependency vốn đã quá tải không?
- **Timeout và cancellation:** task có cơ chế thoát khi nguồn không phản hồi hay cứ chiếm chỗ vô hạn?
- **Backpressure và concurrency limit:** hệ thống có điều tiết công việc trước khi queue/phân bổ bộ nhớ tăng không?
- **CPU throttling trong container:** mức CPU nhìn từ hệ điều hành host có thể đánh lừa khi container bị giới hạn quota.

Tách biệt pool cho công việc dài/chờ nhiều và công việc nhạy latency khi cần; không dùng một shared executor vô hạn khiến một loại công việc làm nghẽn toàn ứng dụng.

## 8. Cách chẩn đoán khi ứng dụng chậm nhưng CPU thấp

1. **Định nghĩa triệu chứng:** đo throughput, p50/p95/p99 latency, số request đang thực hiện, timeout, error/rejection. CPU thấp chỉ là dấu hiệu, không phải chẩn đoán.
2. **Đo khoảng thời gian từng dependency:** DB connection acquire, SQL execute, HTTP call, lock wait, xử lý CPU và queue executor. Dùng trace/span và metric với cùng correlation id khi có thể.
3. **Xem thread dump theo thời gian:** chạy `jcmd <PID> Thread.print -l` bằng JDK và lấy nhiều mẫu cách nhau một khoảng phù hợp. Tập trung vào stack trace lặp lại, thread giữ lock, `WAITING/BLOCKED`, tên executor; nhớ rằng `RUNNABLE` không chắc đang tốn CPU.
4. **Đo bằng profiler/JFR:** CPU samples giải thích thời gian tính toán; event như `jdk.JavaMonitorEnter`, `jdk.ThreadPark`, `jdk.SocketRead`, `jdk.FileRead` (tùy cấu hình thu thập) giúp thấy một số nguồn chờ. Đối chiếu với metric connection pool, DB và downstream.
5. **Kiểm tra môi trường:** CPU quota/throttling, GC pauses, thread creation, socket timeout, scheduler/connection limits. Tái kiểm tra dưới tải đủ đại diện trước và sau thay đổi.

Một phép đo hữu ích là **độ trễ tăng mà CPU không tăng tương ứng**: ưu tiên điều tra chờ connection, lock, DB, mạng và hàng đợi. Nếu **CPU bão hòa, nhiều threads RUNNABLE và CPU profile tập trung ở cùng hàm**, ưu tiên phân tích thuật toán, work per request, số lõi CPU và mức song song.

Thread dump đơn lẻ có thể bị lệch bởi thời điểm sampling. Cần nhiều mẫu, stack trace và metric theo thời gian trước khi kết luận có deadlock hoặc bottleneck.

## 9. Những lỗi phổ biến cần tránh

- **Nhầm blocking với busy CPU:** `Thread.sleep`/`Future.get` có thể làm request chậm nhưng hầu như không tiêu CPU; busy spin ngược lại liên tục chiếm CPU dù “đang chờ cờ”.
- **Busy-wait thay vì đợi có điều kiện:** `while (!done) { }` vừa đốt CPU vừa có vấn đề visibility nếu `done` không đồng bộ đúng. Dùng `CountDownLatch`, `BlockingQueue`, `Condition`, `CompletableFuture` hoặc cơ chế async phù hợp; nếu dùng cờ giữa luồng phải hiểu `volatile`/happens-before.
- **Dùng `Thread.sleep` để đồng bộ:** ngủ một thời gian không đảm bảo task khác đã xong; nên chờ sự kiện/kết quả thực sự, kèm timeout.
- **Tưởng `@Async` hoặc `CompletableFuture` mặc nhiên nonblocking:** bất đồng bộ ở phía caller không làm lời gọi JDBC/HTTP blocking biến mất; `join/get` vẫn chặn thread gọi.
- **Cho blocking task vào event loop:** một thao tác chờ kéo dài có thể chặn tiến trình xử lý nhiều kết nối khác trên cùng loop.
- **Tăng pool để chữa DB chậm:** dễ tạo thêm queue, lock contention và timeout ở downstream.
- **Thiếu timeout/cancellation và xử lý interrupt:** task mắc kẹt vô hạn có thể làm cạn thread. Khi bắt `InterruptedException` mà không truyền tiếp, phải cân nhắc phục hồi interrupt flag bằng `Thread.currentThread().interrupt()` theo hợp đồng của phương thức.
- **Chỉ nhìn CPU tổng hoặc chỉ nhìn `Thread.State`:** CPU thấp không chứng minh hệ thống khỏe, và RUNNABLE không chứng minh tiêu thụ CPU; cần các phép đo bổ sung.

## 10. Vận dụng khi thiết kế tính năng Spring

Trước một tác vụ mới, trả lời theo thứ tự:

1. **Tác vụ chủ yếu tính toán hay chờ dependency?** Xác định bằng profiler, tracing, dữ liệu tải và tỷ lệ CPU/wall time, không dựa vào tên method.
2. **Trong lúc chờ, thread nào đang bị giữ?** Servlet worker, thread của `@Async` executor, Reactor scheduler, hay virtual thread? Có làm nghẽn tác vụ khác cùng pool không?
3. **Tài nguyên nào thực sự giới hạn throughput?** CPU, DB connection, giới hạn đối tác, lock, memory hay queue?
4. **Có thể bỏ chờ blocking ở đúng tầng I/O không?** Chỉ dùng nonblocking nếu driver/client hỗ trợ; nếu vẫn dùng blocking, chọn bounded executor hoặc virtual threads với giới hạn phù hợp.
5. **Làm sao biết thay đổi đã hiệu quả?** So sánh throughput, latency theo phân vị, CPU time, số workers đang bận, queue, thời gian chờ lock/I/O và tỷ lệ timeout dưới cùng kịch bản tải.

**Thông điệp cốt lõi:** một luồng đang chờ không tương đương một luồng đang tính toán. Giải pháp cho bottleneck CPU là giảm hoặc phân phối tính toán; giải pháp cho chờ tài nguyên là tìm đúng dependency, giới hạn concurrency, thiết lập timeout, chọn mô hình I/O phù hợp và quan sát được thời gian chờ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-fundamentals-008">Deadlock, cạn luồng và giới hạn của việc tăng số luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Deadlock, cạn luồng và giới hạn của việc tăng số luồng

Trong ứng dụng Spring Framework, nhiều yêu cầu HTTP, tác vụ `@Async`, công việc `@Scheduled` và thao tác I/O có thể cùng cần luồng để tiến triển. Khi hệ thống chậm hoặc đứng, tăng số luồng thường là phản xạ đầu tiên, nhưng cách này có thể không giải quyết được vấn đề, thậm chí khiến độ trễ và mức sử dụng tài nguyên tăng thêm.

Bài học này tập trung vào **ba vấn đề khác nhau**: deadlock (các luồng chờ nhau theo vòng), cạn luồng/thread-pool starvation (không còn luồng rảnh để thực hiện công việc cần thiết) và giới hạn của việc mở rộng số luồng. Ví dụ Java dùng Java 17 trở lên; phần cấu hình Spring là ví dụ tích hợp, cần có thư viện Spring trong dự án.

## 1. Luồng, tác vụ và hàng đợi: mô hình cần nắm

**Luồng (thread)** là đơn vị thực thi; **tác vụ (task)** là công việc được giao cho một luồng; **executor/thread pool** nhận tác vụ và quản lý các worker thay vì mỗi tác vụ đều phải tạo một luồng mới.

Trong hệ thống điển hình:

~~~text
Caller (HTTP / scheduler / event)
        |
        v
   TaskExecutor
        |
        +--> worker rảnh ---------> chạy tác vụ ---------> hoàn tất
        |
        +--> hàng đợi (nếu bận) --> chờ đến lượt
        |
        +--> từ chối (khi đã đạt giới hạn theo cấu hình)
~~~

Ba đại lượng phải quan sát đồng thời:

- **Active workers**: số luồng đang bận thực thi tác vụ. Một luồng đang chờ I/O hoặc chờ `Future.get()` vẫn có thể được tính là đang bận.
- **Queue depth và thời gian chờ trong queue**: công việc đã nhận nhưng chưa có worker thực hiện. Hàng đợi tăng liên tục có thể che giấu sự cố cho đến khi độ trễ rất lớn.
- **Throughput, latency và rejection**: tác vụ hoàn tất mỗi giây, thời gian xử lý (đặc biệt p95/p99) và số tác vụ bị từ chối.

**Không đồng nhất số luồng lớn với khả năng xử lý lớn.** Một luồng đang giữ khóa hoặc đợi database chưa tạo ra kết quả, dù nó chiếm chỗ trong pool.

## 2. Phân biệt đúng triệu chứng

| Hiện tượng | Cơ chế cốt lõi | Dấu hiệu thường gặp | Tăng luồng có chữa tận gốc? |
| --- | --- | --- | --- |
| **Deadlock trên khóa Java** | Các luồng giữ khóa và chờ khóa của nhau theo vòng kín | Thread dump chỉ ra chu trình sở hữu/chờ monitor hoặc ownable synchronizer; công việc không tiến triển | **Không**; phải phá vòng phụ thuộc khóa |
| **Thread-pool starvation** | Tất cả worker bận/chờ, trong khi công việc để giải phóng chúng lại nằm trong queue của chính pool đó, hoặc tác vụ quan trọng liên tục không được cấp thời gian | Active chạm ngưỡng; queue đọng; stack nhiều `Future.get()`/`join()` | Tăng pool có thể làm hết triệu chứng trong một tải cụ thể, nhưng phụ thuộc nguy hiểm vẫn tồn tại |
| **Pool saturation / quá tải** | Tốc độ đến vượt tốc độ hoàn thành, hoặc I/O chậm chiếm hết worker | Queue tăng, rejection, latency tăng; có thể không có chu trình deadlock | Đôi khi có ích với I/O-bound, nhưng chỉ tới giới hạn tài nguyên phía sau |
| **Resource starvation** | Luồng chờ connection pool, khóa DB, socket hoặc tài nguyên khác bị giới hạn | Nhiều stack chờ datasource/HTTP client; pool tài nguyên đầy | Tăng worker thường làm nhiều luồng chờ hơn |
| **CPU contention / quá nhiều runnable threads** | Nhiều luồng tranh CPU, cache, lock, lịch chạy | CPU cao, context switch nhiều, throughput không tăng | Thường **không** |
| **Livelock / retry liên tục** | Tác vụ vẫn chạy nhưng liên tục phản ứng/retry mà không đạt tiến triển | CPU hoặc log hoạt động mạnh, kết quả không hoàn tất | Không; phải sửa điều kiện tiến triển và chiến lược retry |

Một luồng ở trạng thái `BLOCKED` hoặc `WAITING` **chưa đủ** để kết luận deadlock. Thread đang đợi việc, ngủ theo lịch, hoặc chờ kết quả hợp lệ cũng có thể ở các trạng thái đó. Cần xem nó chờ **ai/cái gì**, và có vòng phụ thuộc hay không.

## 3. Deadlock là gì và tại sao xảy ra?

Giả sử luồng T1 giữ khóa A rồi cố lấy B, trong khi T2 giữ khóa B rồi cố lấy A:

~~~text
T1: giữ A -----> đợi B (do T2 giữ)
                 ^              |
                 |              v
T2: đợi A <----- giữ B
~~~

Không bên nào có thể tới đoạn nhả khóa đã giữ. Deadlock cổ điển xuất hiện khi đồng thời có bốn điều kiện Coffman:

1. **Mutual exclusion:** tài nguyên/khóa chỉ có một chủ sở hữu tại một thời điểm.
2. **Hold and wait:** luồng giữ ít nhất một tài nguyên trong khi đợi thêm tài nguyên khác.
3. **No preemption:** không thể tự ý thu hồi khóa khỏi luồng đang giữ.
4. **Circular wait:** tồn tại chu trình luồng/tài nguyên chờ nhau.

Phá được **ít nhất một điều kiện** sẽ loại được dạng deadlock tương ứng. Trong Java, cách thực tế nhất thường là **quy định thứ tự lấy khóa thống nhất** và không giữ khóa khi thực hiện thao tác có thể chặn lâu.

### 3.1. Ví dụ Java 17: tạo và phát hiện deadlock có kiểm soát

Tạo tệp `DeadlockDemo.java`:

~~~java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;

public class DeadlockDemo {
    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();
    private static final CountDownLatch READY = new CountDownLatch(2);

    private static void takeLocks(Object first, Object second) {
        synchronized (first) {
            READY.countDown();
            try {
                READY.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (second) {
                System.out.println("Both locks acquired");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(() -> takeLocks(LOCK_A, LOCK_B), "worker-A");
        Thread t2 = new Thread(() -> takeLocks(LOCK_B, LOCK_A), "worker-B");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();

        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        for (int i = 0; i < 100; i++) {
            long[] deadlocked = bean.findMonitorDeadlockedThreads();
            if (deadlocked != null) {
                System.out.println("Deadlock detected: " + deadlocked.length + " threads");
                return;
            }
            Thread.sleep(50);
        }
        System.out.println("No deadlock detected within the check window");
    }
}
~~~

Biên dịch/chạy bằng JDK 17+:

~~~bash
javac --release 17 DeadlockDemo.java
java DeadlockDemo
~~~

Kết quả trên JVM hỗ trợ phát hiện monitor deadlock (ví dụ OpenJDK/HotSpot):

~~~text
Deadlock detected: 2 threads
~~~

`CountDownLatch` buộc cả hai luồng **đã giữ khóa đầu tiên** trước khi cố lấy khóa thứ hai; vì thứ tự đối nghịch, chúng tạo vòng chờ. Ví dụ dùng daemon threads **chỉ để tiến trình minh họa có thể kết thúc** sau khi báo lỗi; daemon không phải biện pháp xử lý deadlock. Dòng `Both locks acquired` không xuất hiện trong kịch bản deadlock. `findMonitorDeadlockedThreads()` phát hiện vòng chờ của monitor do `synchronized` quản lý; `findDeadlockedThreads()` mở rộng tới ownable synchronizers như `ReentrantLock` nếu JVM hỗ trợ.

### 3.2. Khắc phục: luôn lấy khóa theo cùng thứ tự

Nếu hai nhánh đều cần A và B, cả hai phải lấy **A rồi B**, bất kể nhánh nào chạy trước:

~~~java
private static void updateBothLocks() {
    synchronized (LOCK_A) {
        synchronized (LOCK_B) {
            // Vùng cập nhật trạng thái chung phải ngắn và không chứa I/O chặn.
        }
    }
}
~~~

Đây là **đoạn phương pháp minh họa** dùng lại hai biến `LOCK_A` và `LOCK_B` từ lớp trước, không phải chương trình độc lập. Hai luồng có thể phải chờ nhau, nhưng không tạo chu trình A→B và B→A. Trong thiết kế thực tế, còn nên giảm số khóa, rút ngắn critical section, tránh gọi callback/HTTP/DB hoặc chờ `Future` khi đang giữ khóa. Với `ReentrantLock`, `tryLock(timeout, unit)` giúp tránh chờ vô hạn **nếu mọi đường thoát đều nhả các khóa đã lấy trong `finally`**.

**Phân biệt deadlock Java với deadlock database:** hai transaction có thể khóa các hàng SQL theo thứ tự ngược nhau; database thường phát hiện và hủy một transaction. Trường hợp đó cần xem log/SQL/transaction order và chính sách retry phù hợp, không được kết luận chỉ từ `ThreadMXBean`.

## 4. Cạn luồng: khi worker chờ chính công việc cần worker

Một lỗi đặc biệt dễ mắc là **task A đang chạy trong pool nhỏ, lại submit task B vào cùng pool rồi chờ đồng bộ kết quả B**. Nếu mọi worker đều đang làm như A, không worker nào còn rảnh để chạy B. Đây thường được gọi là *thread-starvation deadlock*; nó có thể không xuất hiện trong công cụ phát hiện chu trình monitor deadlock.

### 4.1. Ví dụ Java 17: pool một luồng tự chặn chính mình

Tạo tệp `PoolStarvationDemo.java`:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PoolStarvationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<String> outer = pool.submit(() -> {
                Future<String> inner = pool.submit(() -> "OK");
                return inner.get();
            });
            try {
                System.out.println(outer.get(500, TimeUnit.MILLISECONDS));
            } catch (TimeoutException expected) {
                System.out.println("TIMEOUT: worker waits for a task queued in its own pool");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 PoolStarvationDemo.java
java PoolStarvationDemo
~~~

Kết quả:

~~~text
TIMEOUT: worker waits for a task queued in its own pool
~~~

Diễn biến chính xác: worker duy nhất chạy `outer` → `outer` đưa `inner` vào queue → `outer` gọi `inner.get()`, chiếm worker khi chờ → `inner` không có worker để chạy → luồng main hết thời gian chờ sau 500 ms. `shutdownNow()` ngắt worker nhằm kết thúc chương trình minh họa; việc ngắt này không làm cấu trúc phụ thuộc ban đầu trở nên đúng.

**Sửa tại nguyên nhân:** nếu `inner` chỉ là bước tiếp theo của cùng công việc, gọi logic đó trực tiếp trong `outer`; hoặc tổ chức chuỗi bất đồng bộ không chặn worker trên kết quả của task cùng pool. Tách pool chỉ hợp lý khi đã xác định hai loại công việc có nhu cầu tài nguyên và giới hạn độc lập. Không dùng pool lớn hơn để che vòng phụ thuộc.

### 4.2. Các nguồn cạn luồng khác trong ứng dụng Spring

- Tác vụ `@Async` gọi `get()`/`join()` chờ những tác vụ khác cũng chạy trên executor đã bão hòa.
- Nhiều tác vụ `@Scheduled` chạy lâu hoặc chặn I/O trên một scheduler ít worker khiến lịch chạy kế tiếp bị trì hoãn.
- Luồng request của Spring MVC bị giữ vì gọi HTTP, JDBC hoặc dịch vụ khác không có timeout hữu hạn. Nếu datasource chỉ có 10 connection khả dụng, thêm 100 request threads không tạo ra 100 connection.
- Callback sử dụng chung pool với tác vụ đang chờ callback, gây phụ thuộc kiểu “mọi worker đều bận chờ”.
- Queue không giới hạn nhận tiếp công việc dù năng lực xử lý phía sau thấp: hệ thống chưa từ chối ngay nhưng tăng dần thời gian chờ và bộ nhớ.

Trong Spring WebFlux, **chặn event-loop bằng thao tác blocking** là một lỗi khác: một số ít event-loop threads có thể bị giữ lâu và làm chậm nhiều request. Phải dùng API không chặn hoặc chuyển blocking work sang scheduler phù hợp và có giới hạn; tăng `@Async` pool không tự giải quyết vấn đề trên event loop.

## 5. Vì sao tăng số luồng có giới hạn?

### 5.1. Công việc CPU-bound và I/O-bound

- **CPU-bound:** tính toán chiếm CPU liên tục. Nếu hệ thống có khoảng P lõi CPU thực sự được cấp, cho chạy nhiều hơn P tác vụ CPU-bound cùng lúc thường tăng tranh lịch chạy/context switching mà không tăng tương ứng throughput. Thử nghiệm bắt đầu quanh mức song song CPU hữu dụng rồi đo, thay vì lấy một con số cố định cho mọi máy.
- **I/O-bound:** luồng dành nhiều thời gian chờ mạng/đĩa/database. Có thể cần concurrency lớn hơn số lõi CPU để che độ trễ chờ, nhưng mức tối ưu phụ thuộc giới hạn socket/connection pool, tốc độ backend, timeout và bộ nhớ.
- **Khóa dùng chung:** nếu mọi tác vụ đều phải vào một critical section, thêm worker có thể chỉ làm dài hàng chờ khóa.
- **Phụ thuộc tuần tự:** một giai đoạn bắt buộc chạy nối tiếp tạo giới hạn tốc độ; thêm worker cho các giai đoạn khác không loại bỏ nút thắt đó.

Ví dụ tính nhẩm: endpoint cần 200 ms sử dụng một database connection, còn database pool chỉ có 10 connection. Trong mô hình lý tưởng (không có chi phí khác), tối đa khoảng `10 / 0,2 = 50` lượt truy vấn/giây ở bước này. Tăng executor từ 20 lên 200 worker không làm pool DB vượt giới hạn 10; các worker mới có thể chỉ đứng chờ connection, khiến p95/p99 xấu đi. Đây là phép minh họa giới hạn tài nguyên, **không phải dự báo throughput thực tế**.

### 5.2. Ước lượng concurrency bằng Little's Law

Ở trạng thái ổn định, `L = λ × W`, với L là số công việc trung bình đang ở trong hệ, λ là tốc độ hoàn tất/đến trung bình và W là thời gian trung bình mỗi công việc lưu lại. Nếu throughput ổn định 60 tác vụ/giây và mỗi tác vụ cần trung bình 0,5 giây trong đoạn đang đo, concurrency trung bình xấp xỉ 30.

Phải xác định rõ W đang đo **thời gian chiếm worker** hay **tổng thời gian gồm queue**. Little's Law giúp đối chiếu số liệu, không tự đưa ra kích cỡ pool tối ưu khi tải biến động, đuôi độ trễ dài, I/O có giới hạn hoặc hệ thống chưa ổn định.

### 5.3. Chi phí của thread quá nhiều

Mỗi platform thread cần stack và metadata native, có chi phí lập lịch và chuyển ngữ cảnh; nhiều task đồng thời còn tăng contention trên lock, connection, cache và vùng heap chứa request/queue. Stack có thể là bộ nhớ **được dành chỗ** chứ không phải luôn đã cam kết toàn bộ, nên không nên áp dụng công thức “mỗi thread luôn tốn chính xác 1 MB RAM”.

Nếu tăng thread count từ 32 lên 256 nhưng throughput gần như không tăng trong khi CPU, queue wait, database wait hoặc GC pressure tăng, tín hiệu đó cho thấy cần tìm bottleneck thay vì tiếp tục tăng.

**Java 21+ virtual threads** có thể giảm chi phí giữ hàng loạt luồng chờ I/O so với platform threads trong một số workload, nhưng **không** xóa deadlock, không mở rộng connection pool/database và không biến thao tác CPU-bound thành vô hạn khả năng song song. Các ví dụ runnable trong bài vẫn dùng Java 17.

## 6. ThreadPoolTaskExecutor trong Spring Framework

`ThreadPoolTaskExecutor` bọc `ThreadPoolExecutor` và thường dùng làm executor của `@Async`. Cần hiểu **thứ tự nhận task** với cấu hình core, queue và max:

1. Khi số worker hiện tại dưới `corePoolSize`, executor tạo thêm worker cho task mới.
2. Khi đã đạt core, executor **ưu tiên đưa task vào queue nếu queue còn chỗ**.
3. Chỉ khi queue không nhận thêm được task, executor mới có thể mở thêm worker tới `maxPoolSize`.
4. Nếu cả worker lẫn queue đều tới giới hạn, task bị từ chối theo rejection policy.

Vì vậy, đặt `maxPoolSize = 100` nhưng giữ **queue không giới hạn** không có nghĩa pool sẽ chủ động tăng tới 100. Với mặc định của `ThreadPoolTaskExecutor` (core 1, max và queue rất lớn), phần lớn công việc có thể chỉ luân phiên qua một worker và queue ngày càng dài. Hãy cấu hình rõ giới hạn theo workload.

### 6.1. Ví dụ cấu hình bounded executor cho công việc blocking I/O

Đoạn sau là **mã tích hợp Spring**, yêu cầu Spring Framework và ứng dụng đã scan các bean; không phải chương trình `javac` độc lập:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Configuration
@EnableAsync
class ExecutorConfig {
    @Bean("ioExecutor")
    ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("io-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
class AuditService {
    @Async("ioExecutor")
    public CompletableFuture<String> record(String eventId) {
        // Thay bằng nghiệp vụ thực, có timeout I/O riêng nếu gọi dịch vụ ngoài.
        return CompletableFuture.completedFuture("saved:" + eventId);
    }
}
~~~

Trong ví dụ, từ lúc đầy 4 core workers, executor xếp queue tối đa 32 task đang chờ; khi queue đầy, có thể mở thêm đến 8 workers. Sau khi toàn bộ worker bận và queue đầy, chính sách `AbortPolicy` dẫn đến lỗi từ chối (Spring có thể biểu diễn bằng `TaskRejectedException`). Các số **4/8/32 là ví dụ**, không phải giá trị tối ưu mặc định; cần đo thời gian chạy, độ trễ queue, dung lượng downstream và tải đỉnh rồi chọn.

Điểm cần nhớ:

- `@EnableAsync` kích hoạt xử lý `@Async`; `@Async("ioExecutor")` chọn bean executor có tên tương ứng.
- Với chế độ proxy mặc định, **gọi phương thức `@Async` từ phương thức khác trong cùng instance** (*self-invocation*) sẽ không đi qua proxy, nên không thực hiện async theo cách mong đợi. Lời gọi phải đi qua Spring-managed proxy.
- `@Async` chuyển nơi thực thi công việc sang worker; thao tác blocking trong method vẫn blocking **worker đó**. `CompletableFuture.completedFuture(...)` ở đây chỉ là kiểu kết quả từ method đang chạy trong worker.
- Không gọi `join()`/`get()` vô thời hạn trên tập task con trong chính pool đang xử lý task cha. Dùng composition không chặn hoặc thiết kế lại ranh giới phụ thuộc.
- Từ chối task cần hành vi có chủ đích: trả lỗi phù hợp, retry có backoff ở tầng được phép, kiểm soát tốc độ gửi hoặc ghi vào hàng đợi bền vững nếu nghiệp vụ đòi hỏi. Rejection không nên bị âm thầm nuốt.
- `CallerRunsPolicy` có thể tạo backpressure bằng cách cho **thread gửi task** chạy task, nhưng trên luồng HTTP/event-loop/scheduler có thể gây block ngoài ý muốn; không chọn theo thói quen.
- Gắn timeout/cancellation cho JDBC/HTTP client và các tác vụ chờ được phép ngắt. Timeout `Future.get(timeout)` chỉ giới hạn **thời gian caller chờ**; nó không mặc nhiên dừng I/O trong task đang chạy.
- Nếu một luồng gọi `@Async` nằm trong transaction, không giả định ngữ cảnh transaction thread-bound của caller tự động chuyển sang worker. Thiết kế transaction boundary trong chính công việc async khi cần.

Một `@Scheduled` chạy công việc lâu nên có scheduler/cấu hình concurrency thích hợp và chính sách không chạy chồng ngoài ý muốn. Không gộp mọi loại công việc vào cùng một pool chỉ vì cùng là “background task”.

## 7. Phát hiện sự cố từ bằng chứng

### 7.1. Thread dump và kiểm tra Java deadlock

Khi có dấu hiệu đứng, lấy **nhiều thread dump ở những thời điểm khác nhau** của cùng process để thấy có tiến triển hay không. Trên JDK có công cụ `jcmd` hoặc `jstack`, ví dụ process id là 12345:

~~~bash
jcmd 12345 Thread.print -l
jstack -l 12345
~~~

Thay 12345 bằng PID thực tế; cần quyền truy cập process và JDK tooling phù hợp. Khi đọc dump:

- Với deadlock trên monitor, xem chuỗi “waiting to lock” và “locked” tạo thành vòng; công cụ có thể báo “Found one Java-level deadlock”.
- Với `ReentrantLock`, xem ownable synchronizers và `ThreadMXBean.findDeadlockedThreads()` nếu JVM hỗ trợ.
- Với pool starvation, các worker có thể đang ở `FutureTask.get`/`CompletableFuture.join`, trong khi task chờ vẫn nằm trong executor queue. Kiểu phụ thuộc này **không nhất thiết** được thông báo như Java monitor deadlock.
- Với I/O chậm, nhiều stack có thể đợi socket, datasource hoặc remote API; kết hợp client metrics/log để xác định chỗ chờ.
- Với `WAITING` hay `TIMED_WAITING`, cần giải thích theo stack và trạng thái công việc, không quy tất cả là lỗi.

Có thể lấy thêm thread thông tin từ JMX/`ThreadMXBean`; công cụ kiểm tra lock phục vụ chẩn đoán, không nên gọi liên tục ở tần suất cao trong hot path sản xuất.

### 7.2. Đo executor và tài nguyên phía sau

Khi dùng `ThreadPoolTaskExecutor`, theo dõi `getActiveCount()`, `getPoolSize()`, `getCorePoolSize()`, `getMaxPoolSize()`, `getQueueSize()` (trên phiên bản Spring hỗ trợ), số task hoàn tất và số lần rejection. Song song cần theo dõi:

- Độ dài queue **và tuổi của task lâu nhất**; chỉ nhìn số lượng worker có thể bỏ qua hàng đợi dài.
- Độ trễ end-to-end p50/p95/p99, throughput hoàn tất và timeout/error rate.
- CPU hữu dụng theo quota/container, context switches, GC, heap/native memory và số thread.
- Số connection đang dùng/đang chờ của datasource, HTTP connection pool, hàng đợi hoặc rate limit của dịch vụ downstream.
- Thời gian chờ lock, stack lặp lại, truy vấn DB chậm và transaction đang mở.

**Dấu hiệu dễ nhầm:** `activeCount == maxPoolSize` không tự nó là deadlock; có thể chỉ là tải cao. Ngược lại, `activeCount < maxPoolSize` cũng không chứng minh pool có đủ sức xử lý: queue còn chỗ sẽ khiến executor chưa mở tới max.

### 7.3. Quy trình khoanh vùng theo bằng chứng

1. **Xác nhận triệu chứng:** tách “không task nào hoàn tất”, “hoàn tất chậm” và “nhiều task bị từ chối”; ghi thời điểm và mức tải.
2. **Chụp thread dumps cùng metrics:** nhìn trạng thái worker, stack, lock owner, active/queue/rejection và kết nối phụ thuộc.
3. **Tìm vòng chờ:** nếu thấy lock A→B→A, sửa thứ tự khóa/critical section; nếu thấy worker chờ task con của chính pool, sửa dependency blocking.
4. **Tìm tài nguyên giới hạn:** nếu nhiều worker đợi DB/network, đo capacity và latency tại đó trước khi tăng worker.
5. **Thực hiện một thay đổi có kiểm soát:** ví dụ rút ngắn I/O timeout, đặt queue bounded, sửa code chờ, hoặc điều chỉnh pool; kiểm tra lại throughput, p95/p99, lỗi và tài nguyên.
6. **Thử tải tăng dần và tải kéo dài:** xác nhận hệ thống đạt điểm ổn định, có chiến lược xử lý khi quá tải và hồi phục khi downstream chậm.

## 8. Nguyên tắc thiết kế để tránh tái phát

**Với khóa:** quy định thứ tự lấy lock toàn hệ thống; giảm phạm vi `synchronized`; không gọi hàm lạ, callback, I/O hoặc chờ future khi đang giữ nhiều khóa; cân nhắc bất biến dữ liệu và cấu trúc concurrent sẵn có thay cho khóa thủ công. Khóa phải được nhả đúng trên mọi đường thoát.

**Với task graph:** tránh task cha giữ worker để chờ tác vụ con bắt buộc chạy trong cùng pool; biểu diễn quan hệ bằng luồng xử lý tuần tự hoặc composition. Nếu bắt buộc có bước blocking, dành concurrency budget riêng dựa trên số kết nối và timeout cụ thể.

**Với backpressure:** dùng queue có giới hạn, kiểm soát số task đang xử lý và kết nối downstream. Khi vượt năng lực, từ chối sớm hoặc điều tiết nguồn vào một cách minh bạch. “Nhận mọi việc để xếp hàng” thường chỉ dời lỗi từ rejection sang timeout và tăng dùng bộ nhớ.

**Với timeout và retry:** timeout end-to-end phải bao gồm thời gian queue + thời gian thực thi + các I/O; retry phải có giới hạn, backoff/jitter và phù hợp idempotency. Retry ồ ạt lúc downstream chậm có thể làm cạn worker nhanh hơn.

**Với phân chia pool:** tách CPU-bound khỏi blocking I/O khi khác nhau rõ ràng về tài nguyên; tránh chia pool chỉ vì tên chức năng. Kích thước pool, queue, connection pool và ngưỡng admission phải được thiết kế **cùng nhau**.

## 9. Tình huống tổng hợp trong ứng dụng Spring

Giả sử một dịch vụ Spring MVC dùng `@Async("ioExecutor")` để xuất báo cáo. Mỗi tác vụ cần một DB connection và gọi một API ngoài. Khi tải cao, quan sát thấy:

| Số liệu giả định | Quan sát |
| --- | --- |
| Executor | 8/8 workers bận; queue gần đầy 32/32 |
| Database pool | 10/10 connections đang dùng; có thread đợi lấy connection |
| API ngoài | p99 cao và thỉnh thoảng timeout |
| Đầu ra | Thông lượng không tăng, request bị timeout hoặc task bị reject |

Đây **chưa đủ bằng chứng** để kết luận deadlock. Thread dump không thấy vòng khóa, nhưng thấy nhiều worker đợi I/O. Nếu chỉ nâng `maxPoolSize` từ 8 lên 64, nhiều task hơn có thể đồng thời tranh 10 DB connections và API đang chậm; queue chờ chỉ chuyển từ executor sang connection pool, trong khi độ trễ có thể xấu hơn.

Hướng xử lý dựa trên nguyên nhân là: xác định bước I/O nào chậm; đặt timeout hữu hạn và hủy khi phù hợp; giảm thời gian giữ connection; kiểm soát số task đồng thời vào downstream; bounded queue và phản hồi quá tải; cân nhắc tăng database/HTTP pool **chỉ khi hệ thống phía sau chịu được**. Sau đó thử tải và so sánh throughput, p95/p99, rejection, connection wait. Nếu thread dump thực sự thấy vòng khóa, ưu tiên sửa deadlock đó trước khi tuning pool.

## 10. Tự kiểm tra kiến thức

**Câu 1. Hai thread đều `BLOCKED` có chắc deadlock không?** Không. Cần chứng minh chúng chờ nhau theo vòng (hoặc một chu trình dài hơn). Chờ cùng một lock rồi lần lượt được cấp lock không phải deadlock.

**Câu 2. Tại sao đặt `maxPoolSize=100` nhưng chỉ thấy vài worker?** Với `ThreadPoolExecutor`, sau core, task thường được xếp vào queue trước. Nếu queue rất lớn, nó chưa có lý do mở thêm tới max; phải nhìn `queueCapacity` và các metrics.

**Câu 3. Ví dụ pool một worker chờ `inner.get()` thuộc loại gì?** Thread-pool starvation do task dependency: worker giữ chỗ, task cần chạy để giải phóng worker lại nằm trong chính queue. Trình phát hiện monitor deadlock có thể không nhìn thấy.

**Câu 4. Khi nào thêm worker có thể giúp?** Khi worker chủ yếu chờ I/O, downstream còn năng lực, và concurrency chưa chạm giới hạn CPU, memory, connection, rate limit hay queue. Quyết định phải được xác nhận bằng tải thực tế.

**Câu 5. Virtual threads có giải quyết deadlock hoặc connection pool cạn không?** Không. Chúng thay đổi chi phí mô hình luồng chờ của một số workload, không làm mất vòng khóa và không tăng năng lực database.

**Câu 6. Một task bị timeout khi gọi `Future.get(500, MILLISECONDS)` có tự bị hủy không?** Không. Timeout chỉ chấm dứt lượt chờ của caller. Muốn yêu cầu hủy phải gọi `cancel(...)` hoặc dùng cơ chế timeout/hủy của I/O, và task phải hợp tác với interruption/cancellation.

## 11. Tài liệu tham khảo

- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework — `ThreadPoolTaskExecutor` implementation/API: https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/java/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.java
- Java SE 17 — Monitoring and Management / deadlock diagnosis: https://docs.oracle.com/en/java/javase/17/management/using-jconsole.html
- Java SE — `ThreadMXBean`: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html


</details>

- [Quay lại đầu trang](#back-to-top)
