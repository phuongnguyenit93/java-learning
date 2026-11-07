<a id="back-to-top"></a>

# Future &amp; Scheduling

## Menu
- [Future: get(), isDone(), isCancelled()](#future-and-scheduling-001)
- [get(timeout) và tránh chờ vô hạn](#future-and-scheduling-002)
- [Cancellation và trạng thái của Future](#future-and-scheduling-003)
- [Callable&lt;T&gt; so với Runnable khi cần trả kết quả](#future-and-scheduling-004)
- [ExecutionException và truy nguyên lỗi task](#future-and-scheduling-005)
- [invokeAll() cho nhóm kết quả](#future-and-scheduling-006)
- [invokeAny() cho kết quả thành công đầu tiên](#future-and-scheduling-007)
- [ExecutorCompletionService nhận kết quả theo thứ tự hoàn tất](#future-and-scheduling-008)
- [ScheduledExecutorService cho công việc trì hoãn](#future-and-scheduling-009)
- [scheduleAtFixedRate() và scheduleWithFixedDelay()](#future-and-scheduling-010)
- [Exception trong tác vụ lặp và tác động tới lần chạy sau](#future-and-scheduling-011)
- [Thiết kế stop/shutdown cho lịch chạy định kỳ](#future-and-scheduling-012)
- [FutureTask: một tác vụ Runnable có thể được theo dõi bằng Future](#future-and-scheduling-013)
- [Future.State, resultNow() và exceptionNow() từ Java 21](#future-and-scheduling-014)
- [invokeAll() và invokeAny() có thời hạn: xử lý tác vụ chưa hoàn tất](#future-and-scheduling-015)
- [ExecutorService.close() và try-with-resources từ Java 21: điều kiện chờ kết thúc](#future-and-scheduling-016)
- [ScheduledThreadPoolExecutor.setRemoveOnCancelPolicy() và tác vụ bị hủy trong hàng đợi](#future-and-scheduling-017)
- [Tác vụ định kỳ chạy lâu: fixed rate, fixed delay và việc không chạy chồng chính nó](#future-and-scheduling-018)
- [TimeUnit: chuyển đơn vị, độ chính xác và ngân sách thời gian qua nhiều bước](#future-and-scheduling-019)

## <a id="future-and-scheduling-001">Future: get(), isDone(), isCancelled()</a>

<details>
<summary>Xem chi tiết</summary>

# Future trong Java: get(), isDone(), isCancelled()

## 1. Future đại diện cho điều gì?

Trong Java Concurrency, **java.util.concurrent.Future<V>** là một đối tượng đại diện cho kết quả của một tác vụ bất đồng bộ có thể hoàn thành trong tương lai. Khi gửi Callable<V> hoặc Runnable vào ExecutorService.submit(...), chương trình nhận lại Future để theo dõi trạng thái, đợi kết quả hoặc yêu cầu hủy công việc.

Future không đồng nghĩa với thread. Một Future đại diện cho **một phép tính/tác vụ**; ExecutorService quyết định tác vụ chạy trên worker nào và vào lúc nào. Future cũng không tự động bảo đảm tác vụ chạy thành công, không bị chặn hoặc có thể bị dừng tức thì.

Ba phương thức trọng tâm:

| Phương thức | Ý nghĩa chính | Có thể chờ? |
|---|---|---|
| **V get()** | Đợi tác vụ hoàn tất rồi trả về kết quả, hoặc ném ngoại lệ tương ứng | **Có**, có thể đợi vô thời hạn |
| **boolean isDone()** | Kiểm tra Future đã ở trạng thái hoàn tất hay chưa | **Không** |
| **boolean isCancelled()** | Kiểm tra Future đã được hủy thành công hay chưa | **Không** |

Future còn có **cancel(boolean mayInterruptIfRunning)** để yêu cầu hủy và **get(long timeout, TimeUnit unit)** để giới hạn thời gian đợi. Cần hiểu các phương thức này cùng nhau mới dùng đúng get/isDone/isCancelled.

### Kết quả của submit(...)

~~~java
ExecutorService executor = Executors.newSingleThreadExecutor();
try {
    Future<Integer> result = executor.submit(() -> 6 * 7);
    int answer = result.get(); // 42; có thể đợi nếu task chưa hoàn tất.
} finally {
    executor.shutdown();
}
~~~

Đây là **đoạn minh họa** nằm trong một phương thức có khai báo hoặc xử lý InterruptedException/ExecutionException, chưa phải lớp Java độc lập. Trong ví dụ trên, phần tính toán có thể đã xong trước khi gọi get; tính bất đồng bộ không bắt buộc get phải chờ lâu.

**Lưu ý về Callable và Runnable:**

- **submit(Callable<V>):** Future<V>.get() trả về giá trị V nếu thành công.
- **submit(Runnable):** Future<?>.get() trả về **null** khi Runnable hoàn tất bình thường.
- **submit(Runnable, V result):** get() trả về giá trị result đã cung cấp nếu Runnable hoàn tất bình thường.
- Với Executor.execute(Runnable), kiểu trả về là void: không có Future từ lời gọi execute để dùng get/isDone/isCancelled.

## 2. Hiểu đúng vòng đời và trạng thái Future

Một Future có thể đang chờ trong queue, đang chạy hoặc đã hoàn tất. Khi hoàn tất, kết cục có thể là **thành công**, **thất bại vì ngoại lệ** hoặc **được hủy thành công**.

~~~text
                         submit task
                              |
                     Chưa hoàn tất
                  (queued hoặc running)
                       /      |      \
                      /       |       \
               thành công   thất bại   hủy thành công
                    |          |              |
                 done=true   done=true     done=true
                 cancel=false cancel=false cancel=true
~~~

Bảng hành vi của Future sau khi quan sát trạng thái:

| Tình trạng | isDone() | isCancelled() | get() |
|---|---|---|---|
| Chưa hoàn tất (đang chờ/chạy) | false | false | Có thể chờ đến khi hoàn tất |
| Hoàn tất **thành công** | true | false | Trả về giá trị kết quả |
| Hoàn tất **do ngoại lệ** | true | false | Ném ExecutionException chứa nguyên nhân |
| Hủy **thành công** | true | true | Ném CancellationException |

**Kết luận rất quan trọng:** **isDone() == true không có nghĩa task thành công.** Nó chỉ nói phép tính đã hoàn tất theo hợp đồng Future, bao gồm cả trường hợp lỗi hoặc hủy. isCancelled() == false cũng **không khẳng định thành công**: task có thể chưa xong hoặc đã thất bại.

Những trạng thái trên là ảnh chụp tại **thời điểm kiểm tra**. Task có thể kết thúc ngay sau một lời gọi isDone() trả về false. Đừng ghép nhiều phép kiểm tra thành giả định về một trạng thái bất biến khi các thread khác vẫn hoạt động.

## 3. get(): chờ kết quả và cách xử lý ngoại lệ

Với **Future<V>.get()**, thread **gọi get** có thể bị chặn cho tới khi phép tính hoàn tất; worker thực thi tác vụ là thread khác do executor quản lý. Nếu công việc hoàn tất trước, get lấy kết quả ngay. Một số tình huống:

1. **Hoàn tất bình thường:** get() trả về V; với Runnable không có giá trị thì trả về null.
2. **Task ném lỗi:** get() ném **ExecutionException**; gọi getCause() để truy cập lỗi ban đầu của tác vụ.
3. **Future bị hủy thành công:** get() ném **CancellationException** (ngoại lệ không kiểm tra).
4. **Thread đang chờ bị interrupt:** get() ném **InterruptedException**; việc thread gọi get bị ngắt không mặc nhiên đồng nghĩa Future bị hủy.
5. **Task không kết thúc:** get() không có thời hạn chờ mặc định; nếu không có lối thoát, caller có thể bị chặn vô hạn.

**get(timeout, unit)** chờ tối đa thời lượng yêu cầu và có thể ném **TimeoutException** nếu chưa có kết quả. Timeout **không tự động hủy Future**: tác vụ có thể vẫn chạy và get() ở một lần gọi sau vẫn trả về kết quả thành công.

Hai overload có khai báo checked exceptions khác nhau:

~~~java
V value = future.get(); // InterruptedException, ExecutionException
V value = future.get(2, TimeUnit.SECONDS);
// InterruptedException, ExecutionException, TimeoutException
~~~

Các dòng trên chỉ minh họa chữ ký trong ngữ cảnh Future<V> và cần xử lý/khai báo ngoại lệ. CancellationException vẫn có thể xuất hiện ở cả hai overload.

### Không che giấu lỗi gốc

Nếu task ném IllegalStateException("bad input"), lời gọi get() ở caller ném ExecutionException. Trong catch (ExecutionException ex), **ex.getCause()** mới là nguyên nhân task thất bại. Đừng bắt Exception rồi coi mọi trường hợp là timeout hoặc trả về kết quả mặc định, vì sẽ bỏ qua lỗi xử lý và dữ liệu hỏng.

### Interruption khác cancellation

Khi caller bị interrupt trong lúc đợi get(), get có thể ném InterruptedException. Caller nên **truyền tiếp ngoại lệ** hoặc, nếu xử lý tại chỗ và không truyền tiếp, thường khôi phục cờ interrupt bằng Thread.currentThread().interrupt() để tầng trên biết có yêu cầu ngắt. Việc phục hồi cờ interrupt không tương đương với future.cancel(true), và không đảm bảo task phía worker ngừng.

## 4. Ví dụ Java 17: kiểm tra trước và sau khi hoàn tất

Sử dụng CountDownLatch để điều khiển đúng thời điểm quan sát. Task báo đã bắt đầu rồi chờ tín hiệu; main kiểm tra isDone()/isCancelled() khi task **chắc chắn chưa kết thúc**, sau đó mở khóa và đọc kết quả. Ví dụ này tránh phụ thuộc vào tốc độ CPU để minh họa trạng thái.

Lưu file **FutureLifecycleDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureLifecycleDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> future = executor.submit(() -> {
                started.countDown();
                release.await();
                return "OK";
            });

            started.await();
            System.out.println("before: done=" + future.isDone()
                    + ", cancelled=" + future.isCancelled());

            release.countDown();
            System.out.println("get=" + future.get());
            System.out.println("after: done=" + future.isDone()
                    + ", cancelled=" + future.isCancelled());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac FutureLifecycleDemo.java
java FutureLifecycleDemo
~~~

Stdout xác định:

~~~text
before: done=false, cancelled=false
get=OK
after: done=true, cancelled=false
~~~

Ở điểm in dòng đầu, worker đang đợi latch release nên chưa thể hoàn tất. Sau release.countDown(), get() đồng bộ với kết quả của task; vì get đã trả về bình thường nên isDone true và isCancelled false ở dòng cuối.

**Quan hệ bộ nhớ:** theo hợp đồng Future, các hành động trong phép tính bất đồng bộ **happen-before** các hành động phía thread khác diễn ra sau khi Future.get() tương ứng trả về. Vì vậy get() không chỉ lấy giá trị mà còn cung cấp một mốc đồng bộ quan trọng đối với những ghi dữ liệu do tác vụ thực hiện. Điều này không biến mọi truy cập dữ liệu chia sẻ trước khi get thành thread-safe; khi đọc/ghi đồng thời trước mốc đồng bộ vẫn cần quy tắc đồng bộ phù hợp.

## 5. isDone(): biết đã xong, không biết xong tốt hay xấu

**isDone()** là kiểm tra không blocking. Khi false, tác vụ có thể chưa bắt đầu hoặc vẫn đang chạy. Khi true, phép tính đã hoàn tất bình thường, thất bại hoặc bị hủy.

Cách dùng đúng:

- Theo dõi trạng thái ở giao diện/metric khi cần trạng thái snapshot.
- Chỉ gọi get() khi muốn lấy kết quả hoặc lỗi; nếu isDone() đã true, get() không cần đợi phép tính hoàn tất (nhưng vẫn có thể ném ExecutionException/CancellationException).
- Không dùng vòng lặp while (!future.isDone()) {} để **busy-spin**: nó tiêu tốn CPU không cần thiết. Nếu chỉ muốn chờ, hãy gọi get() hoặc get(timeout,...); nếu muốn phản ứng theo từng task hoàn thành, cân nhắc CompletionService hoặc mô hình completion phù hợp.

**isDone true nhưng get ném lỗi** là hành vi bình thường khi task thất bại. Do đó mô hình if (future.isDone()) { return future.get(); } không phải cách bỏ qua try/catch. Nó chỉ có thể tránh chờ khi phép tính còn pending; lỗi vẫn phải được xử lý.

**Tác vụ chạy lâu không có nghĩa là Future sai.** isDone vẫn false khi task đang xử lý hoặc chờ dependency. Cần đo task duration, queue wait, lock contention và I/O latency trước khi kết luận task treo.

## 6. get(timeout, unit) và lỗi tác vụ: ví dụ kiểm chứng

Ví dụ này tạo một tác vụ chờ latch để kiểm chứng chắc chắn timeout, rồi giải phóng latch để lấy kết quả. Nó cũng submit task cố ý ném lỗi để quan sát ExecutionException. Không dùng sleep để tạo timing mong manh.

Lưu file **FutureTimeoutFailureDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTimeoutFailureDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<Integer> slow = executor.submit(() -> {
                started.countDown();
                release.await();
                return 7;
            });

            started.await();
            try {
                slow.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                System.out.println("timeout, still pending=" + !slow.isDone());
            }

            // Timeout ở caller không hủy task.
            release.countDown();
            System.out.println("slow result=" + slow.get());

            Future<Integer> failed = executor.submit(() -> {
                throw new IllegalStateException("demo failure");
            });

            try {
                failed.get();
            } catch (ExecutionException failure) {
                System.out.println("failure cause="
                        + failure.getCause().getClass().getSimpleName());
            }
            System.out.println("failed isDone=" + failed.isDone());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac FutureTimeoutFailureDemo.java
java FutureTimeoutFailureDemo
~~~

Stdout:

~~~text
timeout, still pending=true
slow result=7
failure cause=IllegalStateException
failed isDone=true
~~~

Đầu tiên, slow được bảo đảm còn đợi latch, nên get với hạn chờ 20 ms ném TimeoutException và isDone vẫn false. Sau khi mở latch, lần get tiếp theo trả về 7. Tác vụ failed ném IllegalStateException; get bọc lỗi trong ExecutionException, nhưng isDone true vì phép tính đã kết thúc.

**Thực tế khi timeout:** tùy nghiệp vụ, caller có thể retry, hủy task, trả về lỗi hoặc tiếp tục đợi; phải chọn rõ. Nếu timeout của một request không còn cần kết quả, cân nhắc future.cancel(true) và cơ chế deadline/cancellation ở cả I/O downstream. Không tự retry nhiều lần trong khi task cũ vẫn đang chạy, vì có thể nhân tải lên hệ thống.

## 7. isCancelled(): kiểm tra kết quả hủy và hiểu cancel(...)

**isCancelled()** chỉ true nếu Future **đã được hủy thành công trước khi hoàn tất bình thường**. Việc người dùng “muốn hủy” không đồng nghĩa isCancelled true; nếu task đã xong trước khi cancel, cancel thường trả về false và isCancelled vẫn false.

Phương thức **cancel(boolean mayInterruptIfRunning)** trả về boolean cho biết yêu cầu hủy thành công theo hợp đồng Future:

- **cancel(false):** cố gắng hủy mà **không gửi interrupt đến thread đang chạy**. Task đang chạy có thể vẫn tiếp tục thực thi phần thân; tuy nhiên Future đã chuyển sang trạng thái canceled, get() sẽ ném CancellationException nếu cancel thành công.
- **cancel(true):** nếu task đang chạy, implementation có thể interrupt worker đang thực thi để yêu cầu task dừng. Interrupt chỉ là tín hiệu hợp tác; code không kiểm tra interrupt hoặc I/O không interruptible có thể còn chạy tiếp.
- **cancel(...) trả false:** việc hủy không thành công, chẳng hạn phép tính đã hoàn tất hoặc Future không thể hủy. Không được tự suy ra kết quả là thành công; kiểm tra và xử lý get nếu cần.
- **isCancelled() == true:** get() ném CancellationException, đồng thời isDone() true. Tuy nhiên điều này **không chứng minh side effect bên ngoài đã dừng, đã được rollback hoặc chưa từng xảy ra**.

**Mối quan hệ quan trọng:** cancellation là trạng thái của Future; termination thực tế của task là một vấn đề khác. Đặc biệt với cancel(false) trên task đang chạy hoặc cancel(true) khi code không đáp ứng interrupt, Future có thể báo canceled trong khi một phần công việc phía sau còn tiếp tục.

### Ví dụ Java 17: hủy khi tác vụ đang đợi

Lưu file **FutureCancellationDemo.java**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureCancellationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);

        try {
            Future<Integer> future = executor.submit(() -> {
                started.countDown();
                blocker.await();
                return 123;
            });

            started.await();
            boolean cancelled = future.cancel(true);

            System.out.println("cancel accepted=" + cancelled);
            System.out.println("done=" + future.isDone()
                    + ", cancelled=" + future.isCancelled());

            try {
                future.get();
            } catch (CancellationException expected) {
                System.out.println("get -> CancellationException");
            }
        } finally {
            blocker.countDown();
            executor.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac FutureCancellationDemo.java
java FutureCancellationDemo
~~~

Stdout:

~~~text
cancel accepted=true
done=true, cancelled=true
get -> CancellationException
~~~

started.await() bảo đảm worker đã vào thân tác vụ, còn blocker.await() chưa được giải phóng. cancel(true) chuyển trạng thái Future sang canceled và gửi yêu cầu interrupt đến worker; CountDownLatch.await() hỗ trợ interruption. Việc finally mở blocker là bước phòng vệ để không giữ worker kẹt nếu một nhánh khác xảy ra.

Nếu task thực hiện các thao tác ghi database, gửi HTTP hoặc ghi file, **hủy Future không tự động hoàn tác tác động đã xảy ra**. Thiết kế công việc có side effect nên cân nhắc idempotency, timeout của I/O, giao dịch và trạng thái hoàn tất nghiệp vụ.

## 8. Dùng get/isDone/isCancelled cùng nhau: mẫu xử lý hợp lý

### Khi caller thực sự cần kết quả

Gọi get(timeout,...) với một deadline phù hợp thường dễ đọc và tránh vòng lặp polling. Bắt riêng:

- **InterruptedException:** caller đang chờ bị ngắt; truyền tiếp hoặc khôi phục cờ interrupt nếu xử lý tại đây.
- **ExecutionException:** task thất bại; phân loại cause và xử lý lỗi nghiệp vụ/kỹ thuật.
- **TimeoutException:** quá hạn chờ; đánh giá có nên cancel hoặc bỏ qua kết quả.
- **CancellationException:** Future đã bị hủy; xử lý như nhánh hủy, không coi là kết quả hợp lệ.

Trong code xử lý InterruptedException tại chỗ:

~~~java
try {
    return future.get(1, TimeUnit.SECONDS);
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Caller interrupted", interrupted);
}
~~~

Đây là **đoạn minh họa trong một phương thức có Future<V> và kiểu trả về V**; còn phải khai báo/xử lý ExecutionException, TimeoutException, CancellationException ở mức phù hợp. Nếu phương thức có thể khai báo throws InterruptedException, truyền tiếp checked exception thường rõ ràng hơn.

### Khi chỉ muốn hiển thị trạng thái

~~~text
if (future.isCancelled()) {
    // Canceled.
} else if (future.isDone()) {
    // Completed, nhưng chưa biết success/failure.
    // Muốn biết kết quả, vẫn phải gọi get() và xử lý ngoại lệ.
} else {
    // Pending: queued hoặc running.
}
~~~

Đây là **pseudocode** minh họa các trạng thái, không phải Java class độc lập. Thứ tự kiểm tra không tạo snapshot nguyên tử: nếu một thread khác hoàn tất/hủy ngay sau lần kiểm tra, trạng thái quan sát tiếp theo có thể thay đổi. Đối với UI hoặc metrics, ghi rõ đây là snapshot.

### Khi nhận nhiều Future

Nếu có danh sách Future và gọi get() lần lượt theo thứ tự submit, một task đầu chậm có thể giữ caller dù task sau đã hoàn thành. Đừng polling liên tục cả danh sách trong một vòng lặp CPU bận. Các lựa chọn trong Java chuẩn gồm:

- **ExecutorCompletionService:** lấy Future theo thứ tự task **hoàn thành**, thay vì thứ tự submit; vẫn phải gọi get và xử lý lỗi.
- **invokeAll(collection):** đợi tập task hoàn thành theo hợp đồng; phiên bản có timeout có thể hủy các task chưa kết thúc khi hết hạn.
- **CompletableFuture:** khi cần composition, callback và chuỗi xử lý kết quả; nó triển khai Future nhưng có thêm API riêng. Với CompletableFuture, join() dùng CompletionException (unchecked) thay cho mô hình checked exceptions của get().

Các lựa chọn trên phục vụ bài toán nhiều tác vụ; để hiểu Future.get/isDone/isCancelled, hãy nắm đúng ngữ nghĩa từng Future trước.

## 9. Future trong ScheduledExecutorService

Future cũng xuất hiện trong lịch chạy Java chuẩn:

- **schedule(Callable/V hoặc Runnable, delay, unit)** trả ScheduledFuture. Trước khi task một lần chạy xong, isDone thường false; sau khi chạy thành công, get trả về kết quả (Runnable thường là null).
- **scheduleAtFixedRate** và **scheduleWithFixedDelay** trả ScheduledFuture đại diện cho chuỗi chạy **định kỳ**. Với chuỗi định kỳ vẫn đang tiếp tục bình thường, Future không được coi hoàn tất sau mỗi lượt nên isDone thường vẫn false.
- **get() trên Future định kỳ đang chạy bình thường có thể chờ mãi**, vì chuỗi chưa chấm dứt. Sau khi hủy thành công, get ném CancellationException. Nếu một lần chạy định kỳ ném ngoại lệ khiến chuỗi bị chặn chạy tiếp, Future có thể hoàn tất theo nhánh lỗi và get ném ExecutionException.
- **cancel(true)** trên ScheduledFuture không đảm bảo callback đang chạy dừng ngay; quy tắc interruption và side effect vẫn áp dụng.

Do đó không nên gọi get() vô thời hạn trên task định kỳ nếu thiết kế mong đợi “lấy kết quả mỗi lượt chạy”. Nếu muốn kết quả từng lượt, cần một cơ chế riêng để xuất sự kiện/kết quả, thay vì trông chờ Future của cả lịch định kỳ.

## 10. Những lỗi phổ biến

| Sai lầm | Hệ quả | Cách xử lý |
|---|---|---|
| Đọc isDone() == true là “thành công” | Bỏ qua nhánh failure/cancel | Gọi get() và xử lý ExecutionException/CancellationException |
| Đọc isCancelled() == false là “đã xong tốt” | Nhầm với queued, running hoặc failed | Kết hợp trạng thái và kết quả get |
| Gọi get() tùy ý trên main thread/UI/thread xử lý request | Có thể block vô thời hạn | Đặt deadline, xem xét kiến trúc async |
| Nghĩ get(timeout) tự ngừng task | Task cũ có thể tiếp tục, gây trùng xử lý khi retry | Quyết định cancel riêng; bảo đảm idempotency khi cần |
| Nghĩ cancel(true) luôn “kill” worker | Side effect hoặc code không interruptible có thể tiếp diễn | Hỗ trợ interruption, timeout I/O, rollback/compensation phù hợp |
| Busy-spin với while (!isDone()) | Đốt CPU và không xử lý lỗi | get/get(timeout), CompletionService hoặc API hoàn tất |
| Nuốt InterruptedException | Mất tín hiệu shutdown/cancel của caller | Truyền tiếp hoặc restore interrupt flag |
| Bỏ qua ExecutionException.getCause() | Ẩn lỗi thực sự xảy ra trong task | Phân loại/ghi log nguyên nhân gốc |
| Chờ get() trên scheduled task định kỳ | Có thể chờ đến lúc hủy/lỗi, không phải mỗi kỳ | Thiết kế cơ chế kết quả riêng cho mỗi lượt |
| Không shutdown ExecutorService | Worker có thể giữ JVM sống hoặc rò rỉ tài nguyên | Quản lý lifecycle, shutdown và chờ kết thúc theo chính sách |

## 11. Checklist ghi nhớ và tự kiểm tra

Sau bài học, bạn nên giải thích được:

1. Vì sao get() có thể chặn caller nhưng isDone()/isCancelled() thì không?
2. Ba kết cục **thành công, lỗi, hủy** ảnh hưởng bảng trạng thái và kết quả get() ra sao?
3. Vì sao isDone() true chưa nói task thành công và isCancelled() false chưa nói task đã hoàn tất?
4. Khi get(timeout,...) ném TimeoutException, tác vụ phía worker có tiếp tục hay không?
5. Khác biệt giữa interrupt thread **đang đợi get** và cancel(true) trên Future là gì?
6. Vì sao cancel(false) hoặc cancel(true) không mặc nhiên bảo đảm tác động bên ngoài dừng hoặc rollback?
7. Vì sao Future của tác vụ lịch **định kỳ** không hoàn tất sau mỗi lần chạy thành công?

**Ghi nhớ cốt lõi:** **get()** là cổng lấy kết quả và lỗi, có thể chờ; **isDone()** chỉ xác nhận phép tính đã kết thúc theo trạng thái Future; **isCancelled()** cho biết phép tính đã được hủy thành công. Cả ba đều phải được hiểu cùng cơ chế cancel, interruption, timeout và vòng đời executor để quản lý công việc bất đồng bộ an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-002">get(timeout) và tránh chờ vô hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Future.get(timeout) và tránh chờ vô hạn trong Java Concurrency

Trong Java Executor Framework, `Future<T>` là một giao diện biểu diễn kết quả của một tác vụ bất đồng bộ. Khi gửi một `Callable<T>` qua `ExecutorService.submit()`, caller nhận một `Future<T>` ngay cả khi task vẫn đang xếp hàng hoặc chạy. Để lấy kết quả, có hai cách quan trọng:

```java
T result = future.get();                         // Có thể chờ vô hạn.
T resultWithLimit = future.get(2, TimeUnit.SECONDS); // Chờ có giới hạn.
```

Đây là trích đoạn minh họa cần một `Future<T> future` và import `TimeUnit`, với exception phải được xử lý. `get()` chờ tới khi Future hoàn tất, bị hủy hoặc thread đợi bị interrupt. Nếu task không bao giờ hoàn tất, thread gọi `get()` có thể bị chặn **vô thời hạn**. `get(timeout, unit)` giới hạn **một lần chờ** và ném `TimeoutException` khi hết ngân sách.

**Điểm cần nhớ ngay:** `Future.get(timeout)` **không tự hủy task** khi timeout. Nó chỉ đưa thread gọi trở về bằng exception; worker có thể tiếp tục chạy, xếp hàng, thực hiện I/O hoặc tạo tác dụng phụ. Để ứng dụng không chờ mãi và không để lại tác vụ vô chủ, cần kết hợp **wait timeout, overall deadline, cancellation, cleanup và quản lý vòng đời executor**.

Bài tập trung vào Java 17+ và các API concurrency của JDK, không phụ thuộc framework.

## 1. Phân biệt trạng thái của Future

Một Future có thể chưa hoàn tất, hoàn tất thành công, thất bại do exception, hoặc bị hủy:

```text
submit(callable)
       |
       v
 Future chưa xong
       |------------------------|
       v                        v
Task trong queue             Task đang chạy
       |                        |
       +-----------+------------+
                   |
          +--------+--------+
          |        |        |
        Success  Failure  Cancelled
          |        |        |
        get T    get ->    get ->
                 Execution  Cancellation
                 Exception  Exception
```

`isDone()` trả `true` cho Future đã **hoàn tất ở bất kỳ trạng thái kết thúc** phù hợp, bao gồm thất bại hoặc hủy. Nó không đồng nghĩa có kết quả thành công. `isCancelled()` phản ánh Future được đánh dấu hủy, chứ không xác nhận worker đã cleanup xong.

`get(timeout, unit)` có thể có các kết quả:

| Kết quả | Ý nghĩa | Hành động phù hợp |
|---|---|---|
| Trả về `T` | Tác vụ thành công trước khi hết thời gian đợi. | Dùng kết quả; giá trị có thể là `null` tùy `Callable`. |
| `TimeoutException` | Thời hạn của **lần chờ** đã hết. | Quyết định có hủy, fallback, tiếp tục theo dõi hay đợi lại không. |
| `ExecutionException` | Task hoàn tất nhưng ném lỗi. | Lấy `getCause()`; báo lỗi nghiệp vụ phù hợp. |
| `InterruptedException` | **Thread đang gọi get** bị interrupt. | Ném tiếp, hoặc khôi phục interrupt và thoát theo contract. |
| `CancellationException` | Future đã bị hủy. | Phân biệt hủy với lỗi task hoặc timeout chờ. |

Ba exception đầu được nêu trong chữ ký phương thức là checked exception; `CancellationException` là unchecked. Một `ExecutionException` bao bọc lỗi do task gây ra, còn `TimeoutException` không khẳng định task đang thất bại: task có thể hoàn tất thành công ngay sau timeout.

## 2. Ví dụ đầu tiên: timeout mà Future vẫn hoàn thành về sau

Để quan sát hành vi chắc chắn, ta giữ worker tại `CountDownLatch.await()`. Main gọi `get(20, MILLISECONDS)` trong lúc latch vẫn đóng, sau đó chủ động mở latch và lấy kết quả từ **cùng Future**.

Tạo file **`FutureTimeoutThenComplete.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTimeoutThenComplete {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<String> future = executor.submit(() -> {
            started.countDown();
            release.await();
            return "completed";
        });

        try {
            started.await();
            try {
                future.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("First get timed out");
            }

            System.out.println("Cancelled: " + future.isCancelled());
            release.countDown();

            String result = future.get(5, TimeUnit.SECONDS);
            System.out.println("Result: " + result);
        } finally {
            release.countDown();
            executor.shutdown();
        }

        boolean stopped = executor.awaitTermination(
                5, TimeUnit.SECONDS);
        System.out.println("Executor terminated: " + stopped);
    }
}
```

Lệnh:

```bash
javac FutureTimeoutThenComplete.java
java FutureTimeoutThenComplete
```

Đầu ra khi không có external interrupt:

```text
First get timed out
Cancelled: false
Result: completed
Executor terminated: true
```

**Vì sao kết quả xác định?** `started.await()` xác nhận worker đã vào callable. Nhưng worker phải chờ `release`, trong khi main chưa mở latch lúc gọi `get(20, ...)`. Vì vậy get đầu tiên phải timeout; nó không hủy task và `isCancelled() == false`. Main mở latch, worker trả `completed`, lần get sau thành công. Cuối cùng shutdown và `awaitTermination()` xác nhận executor thực sự dừng.

`get(timeout)` chỉ giới hạn **lần chờ hiện tại**. Gọi lại get với một timeout mới có nghĩa caller **đặt thêm ngân sách**, điều này có thể đúng nếu có lý do, nhưng không tự tạo thời hạn tổng cho request.

## 3. Timeout chờ không phải timeout chạy task

Giả sử executor đang quá tải:

```text
0 ms      Caller submit task; task ở queue.
200 ms    Caller bắt đầu get(1000, MILLISECONDS).
1200 ms   get() ném TimeoutException, caller không chờ nữa.
1600 ms   Worker mới lấy task khỏi queue và bắt đầu chạy.
2200 ms   Task hoàn thành và có thể đã tạo side effect.
```

Đây là timeline **giả định**, minh họa sự khác biệt giữa thời gian nộp task và thời gian bắt đầu chờ.

| Loại thời hạn | Muốn giới hạn gì? | Công cụ |
|---|---|---|
| Wait timeout | Caller được block trong một lời get bao lâu? | `Future.get(timeout, unit)`. |
| Queue deadline | Task còn hữu ích nếu đợi trong queue bao lâu? | Deadline trong task, queue capacity và cơ chế loại bỏ/hủy. |
| Execution budget | Task được phép chạy tới khi nào? | Cooperative cancellation, kiểm tra deadline, timeout từng thao tác. |
| I/O timeout | Lời gọi socket/database/network chờ tối đa bao lâu? | Timeout của chính I/O API hoặc giao thức dịch vụ. |
| Overall deadline | Toàn bộ công việc end-to-end phải xong trước khi nào? | Một ngân sách chung truyền qua nhiều bước. |

Một `TimeoutException` ở caller **không rollback side effect** mà worker đã tạo, không bảo đảm worker đã trả kết nối về pool và cũng không làm Future chuyển sang canceled.

## 4. Xử lý TimeoutException: tiếp tục, fallback hay cancel?

Khi một lần get quá hạn, có ba chính sách thường gặp:

- **Còn cần kết quả:** giữ Future, tiếp tục làm việc khác, rồi chỉ đợi lại trong ngân sách còn lại hoặc nhận kết quả bất đồng bộ.
- **Không còn cần task:** gọi `future.cancel(true)` nếu caller có quyền hủy và task có đường phản ứng interruption; sau đó quản lý việc kết thúc và cleanup.
- **Cần fallback hoặc trả lỗi timeout:** trả cho caller một kết quả thay thế, nhưng vẫn phải có owner của task đang chạy; không mặc định nó tự biến mất.

`cancel(true)` yêu cầu hủy Future và có thể cố interrupt thread chạy task; nó **không phải lời hứa task sẽ chấm dứt ngay lập tức**. Một task chạy CPU mà không kiểm tra cờ interrupt, hoặc một lời I/O không hỗ trợ interruption, có thể tiếp tục sau khi Future bị đánh dấu canceled.

`cancel(false)` yêu cầu hủy nhưng không yêu cầu interrupt task đang chạy. Cần phân biệt trạng thái Future với tiến triển thực tế của worker: `isDone()` hay `isCancelled()` có thể true khi cleanup phía worker chưa hoàn tất.

Không retry ngay một task có side effect chỉ vì get timeout: task cũ có thể vẫn đang chạy. Hãy thiết kế **idempotency**, unique request ID, transaction hoặc cơ chế phát hiện công việc đã thực hiện nếu một lần chạy trùng gây hại.

## 5. Ví dụ 2: timeout, cancel(true) và chờ cleanup

Ví dụ này cho thấy một `Future` bị hủy không đủ làm bằng chứng cleanup đã xong. Worker sử dụng một blocking call có hỗ trợ interruption và dọn trong `finally`. Main **đợi executor terminated** rồi mới đọc cờ cleanup.

Tạo file **`FutureCancelAndCleanup.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancelAndCleanup {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean cleaned = new AtomicBoolean();

        Future<Integer> future = executor.submit(() -> {
            started.countDown();
            try {
                neverReleased.await();
                return 42;
            } finally {
                cleaned.set(true);
            }
        });

        try {
            started.await();
            try {
                future.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("Wait timed out");
            }

            boolean accepted = future.cancel(true);
            System.out.println("Cancel accepted: " + accepted);
        } finally {
            executor.shutdown();
        }

        boolean terminated = executor.awaitTermination(
                5, TimeUnit.SECONDS);
        System.out.println("Executor terminated: " + terminated);
        System.out.println("Cleanup completed: " + cleaned.get());
        System.out.println("Future cancelled: " + future.isCancelled());
    }
}
```

```bash
javac FutureCancelAndCleanup.java
java FutureCancelAndCleanup
```

Đầu ra trong điều kiện bình thường:

```text
Wait timed out
Cancel accepted: true
Executor terminated: true
Cleanup completed: true
Future cancelled: true
```

Worker đã bắt đầu và phải chờ một latch không bao giờ mở. Lần get đầu vì vậy hết hạn, còn `cancel(true)` có thể interrupt worker đang đợi. `await()` ném `InterruptedException`, nhánh `finally` đặt `cleaned=true`. Chỉ **sau `awaitTermination()` thành công** thì main mới kết luận executor không còn worker đang chạy.

Trong code production, nếu `awaitTermination()` trả `false`, không được báo “đã cleanup xong”. Phải đưa việc còn chạy quá hạn vào một chính sách giám sát và xử lý phù hợp.

## 6. Task failure và hủy khác timeout

Nếu callable ném exception, `future.get()` thường ném `ExecutionException` và `getCause()` chứa nguyên nhân. Với Future canceled, `get()` ném `CancellationException`.

Tạo file **`FutureResultPaths.java`**:

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FutureResultPaths {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> failed = executor.submit(() -> {
                throw new IllegalArgumentException("bad input");
            });

            try {
                failed.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                System.out.println("Failure: "
                        + e.getCause().getMessage());
            }

            CompletableFuture<String> cancelled =
                    new CompletableFuture<>();
            cancelled.cancel(false);
            try {
                cancelled.get(0, TimeUnit.MILLISECONDS);
            } catch (CancellationException e) {
                System.out.println("Cancelled: true");
            }
        } finally {
            executor.shutdown();
        }

        boolean terminated = executor.awaitTermination(
                5, TimeUnit.SECONDS);
        System.out.println("Executor terminated: " + terminated);
    }
}
```

```bash
javac FutureResultPaths.java
java FutureResultPaths
```

Đầu ra:

```text
Failure: bad input
Cancelled: true
Executor terminated: true
```

Đường lỗi của callable đã được tạo rõ ràng và get bọc nó trong `ExecutionException`. `CompletableFuture` thứ hai được cancel trước bất cứ lần chạy nào, nên `get(0, ...)` ném `CancellationException` ngay. Trong một Future chưa hoàn tất, timeout không dương không cung cấp khoảng thời gian chờ có ích; hãy dùng ngân sách dương rõ ràng nếu muốn đợi.

Đừng bắt `Exception` chung rồi trả fallback cho mọi trường hợp: lỗi do task, hủy do caller và timeout chờ là những tình huống có hậu quả nghiệp vụ khác nhau.

## 7. InterruptedException: caller bị ngắt không đồng nghĩa worker bị hủy

Một thread đang chờ `future.get(timeout)` có thể nhận interrupt và bị ném `InterruptedException`. Đây là **interrupt của thread chờ**, không phải bằng chứng worker thực thi Future đã kết thúc.

Mẫu xử lý khi không muốn ném tiếp checked exception:

```java
try {
    return future.get(2, TimeUnit.SECONDS);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Nếu thực sự sở hữu task và contract yêu cầu:
    future.cancel(true);
    throw new IllegalStateException("Wait interrupted", e);
}
```

Đây là trích đoạn trong một method có kiểu trả về phù hợp và cần import các lớp concurrency. Khi bắt `InterruptedException`, cờ interrupt thường đã bị xóa; `Thread.currentThread().interrupt()` khôi phục nó cho tầng gọi. Nếu method **ném tiếp `InterruptedException`**, thông thường không cần tự restore tại tầng này.

Việc hủy Future khi caller bị interrupt là **một lựa chọn có chủ đích**: nếu Future là tác vụ dùng chung và caller không sở hữu nó, hủy có thể ảnh hưởng người khác. Không tự động áp dụng `cancel(true)` cho mọi Future chỉ vì một người đợi không còn quan tâm.

## 8. Overall deadline khi có nhiều Future

Ba lời đợi sau có thể tiêu tốn gần sáu giây:

```java
Result a = first.get(2, TimeUnit.SECONDS);
Result b = second.get(2, TimeUnit.SECONDS);
Result c = third.get(2, TimeUnit.SECONDS);
```

Đây là trích đoạn minh họa; các Future và `Result` được giả định đã khai báo. **Hai giây cho mỗi get** khác với **hai giây cho toàn bộ chuỗi**.

Một helper để chia sẻ deadline:

```java
static <T> T getBeforeDeadline(
        Future<T> future, long deadlineNanos)
        throws InterruptedException, ExecutionException,
               TimeoutException {
    long remaining = deadlineNanos - System.nanoTime();
    if (remaining <= 0L) {
        throw new TimeoutException("Overall deadline expired");
    }
    return future.get(remaining, TimeUnit.NANOSECONDS);
}
```

Đây là method minh họa không độc lập, cần import `Future`, `TimeUnit` và ba exception. Caller tính **một deadline**:

```java
long deadline = System.nanoTime()
        + TimeUnit.SECONDS.toNanos(2);

Result a = getBeforeDeadline(first, deadline);
Result b = getBeforeDeadline(second, deadline);
Result c = getBeforeDeadline(third, deadline);
```

Không reset hai giây ở mỗi lượt. `System.nanoTime()` phù hợp để đo khoảng thời gian và không phụ thuộc điều chỉnh đồng hồ lịch như `currentTimeMillis()`. Với ngân sách quá lớn, cần giới hạn/kiểm tra tràn số; mẫu này giả định duration dương, hữu hạn, đủ nhỏ để phép cộng và so sánh nanoTime không vướng overflow ngoài phạm vi đo.

**Lưu ý:** Một thời hạn end-to-end thường nên bắt đầu từ lúc tiếp nhận yêu cầu, **không** phải sau khi task đã nằm trong queue lâu. Nếu thời gian chờ queue cũng thuộc SLA, tính nó vào ngân sách tổng. Task tự kiểm tra deadline khi bắt đầu cũng hữu ích nếu kết quả quá hạn không còn giá trị.

## 9. Memory consistency và happens-before của Future.get()

`Future` có hợp đồng memory consistency: **các hành động do tính toán bất đồng bộ thực hiện happens-before các hành động trong thread khác xảy ra sau khi lời `Future.get()` tương ứng hoàn tất**. Khi worker xây dựng kết quả rồi hoàn thành Future, thread đợi get thành công có thể quan sát dữ liệu được công bố theo Java Memory Model (JMM) mà không cần tự thêm `volatile` chỉ cho việc chuyển giao kết quả đúng cách.

Ví dụ:

```java
// Trích đoạn: dữ liệu được worker tính xong trước khi Future thành công.
Future<Integer> future = executor.submit(() -> {
    data.result = 42;
    return data.result;
});
int value = future.get();
System.out.println(value);
```

Đoạn này giả định `executor` và `data` đã khai báo, xử lý checked exceptions. `get()` thành công là một **mốc nhận kết quả**, nhưng không biến mọi mutation đồng thời lên `data` từ thread khác thành an toàn.

**Khi `get(timeout)` timeout**, caller **chưa nhận mốc hoàn tất của tác vụ** qua lần get đó. Không được tự suy “mọi ghi của worker đã hoàn tất và hiển thị” chỉ vì vừa hết hạn. Nếu vẫn cần đọc state cuối, hãy đợi một cơ chế hoàn tất được xác nhận, hoặc dùng primitive đồng bộ có hợp đồng riêng như volatile/atomic/lock.

## 10. Với ScheduledFuture, timeout có thêm thời gian trì hoãn trước khi chạy

`ScheduledExecutorService.schedule(task, delay, unit)` trả `ScheduledFuture<T>`. Khi task có delay ban đầu, `get(timeout)` đợi **kết quả cuối cùng** chứ không chỉ đợi thời điểm hẹn:

```java
ScheduledFuture<String> scheduled =
        scheduler.schedule(() -> "done", 3, TimeUnit.SECONDS);
String result = scheduled.get(1, TimeUnit.SECONDS);
```

Đây là trích đoạn, với `scheduler` là `ScheduledExecutorService` đã khai báo. Vì delay ba giây, lần get có ngân sách một giây **thường** sẽ timeout nếu scheduled task chưa chạy; timeout không hủy lịch đã lên. `getDelay(unit)` phản ánh thời gian còn lại tới mốc scheduling theo contract, **không** phải thời gian task sẽ chạy xong.

Với `scheduleAtFixedRate()` hoặc `scheduleWithFixedDelay()`, `ScheduledFuture<?> ` đại diện cho một **chuỗi thực thi định kỳ**. Future của task định kỳ bình thường **không hoàn tất sau tick đầu**, nên gọi `get(timeout)` không phải cách chờ “đã có một lần chạy”. Chuỗi Future thường chỉ đi tới trạng thái kết thúc khi bị hủy hoặc khi việc thực thi chấm dứt bất thường. Nếu cần đợi riêng một tick, dùng `CountDownLatch` hoặc Future chuyên biệt cho sự kiện đó.

## 11. CompletableFuture: orTimeout khác get(timeout)

`CompletableFuture` còn có:

```java
future.orTimeout(1, TimeUnit.SECONDS);
future.completeOnTimeout("fallback", 1, TimeUnit.SECONDS);
```

Hai dòng là **hai chiến lược thay thế**, không nên gọi cả hai lên cùng Future chỉ vì thấy tiện. `orTimeout` có thể làm **chính CompletableFuture đó** hoàn tất ngoại lệ với `TimeoutException`. `completeOnTimeout` có thể hoàn tất Future bằng giá trị dự phòng. Đây là thay đổi **trạng thái Future**, khác với `future.get(timeout)` chỉ làm kết thúc **lần chờ caller**.

Khi một CompletableFuture hoàn tất ngoại lệ vì `orTimeout`, `get()` thường ném `ExecutionException` với cause là `TimeoutException`; `join()` thường ném `CompletionException`. **Các phương thức này không bảo đảm phép tính underlying đã dừng**. Một tác vụ chạy qua `supplyAsync()` có thể tiếp tục làm side effect sau khi CompletableFuture đã timeout. Cleanup và cancellation cần được thiết kế riêng.

## 12. Không để get(timeout) trở thành một điểm nghẽn mới

`get(timeout)` vẫn **block thread gọi** trong khoảng chờ được cho phép. Nếu gọi nó trên event loop, UI thread hoặc nhiều request threads với timeout lớn, hệ thống có thể nghẽn dù từng lời chờ đều hữu hạn.

Nếu xử lý kết quả theo kiểu bất đồng bộ phù hợp, có thể dùng `thenApply()`, `thenCompose()`, `whenComplete()` của `CompletableFuture` thay cho việc block. Nếu phải chờ đồng bộ, hãy:

- Chọn ngân sách phù hợp với SLA và số bước còn lại.
- Cân nhắc bounded executor queue để không tạo backlog vô hạn.
- Dùng timeout riêng cho I/O bên trong task.
- Đảm bảo có đường xử lý quá hạn và cancellation/cleanup.
- Không dùng `while (!future.isDone()) Thread.sleep(...)` như polling mặc định: việc tự ngủ không thay thế exception handling và có thể tăng độ trễ.

Không có timeout nào là “an toàn tuyệt đối” cho mọi workload. Mục tiêu là một giới hạn **có nghĩa trong nghiệp vụ**, không phải luôn đặt một con số rất lớn.

## 13. Shutdown executor và tránh chờ vô thời hạn ở vòng đời

Sau khi hoàn tất sử dụng pool, owner nên ngừng nhận task mới, rồi chờ termination với giới hạn:

```java
executor.shutdown();
if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
    executor.shutdownNow();
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        System.err.println("Executor still running");
    }
}
```

Đây là trích đoạn cần khai báo `executor` và xử lý `InterruptedException`. `shutdown()` cho những task đã được nhận tiếp tục chạy, trong khi `shutdownNow()` **cố** ngắt worker và lấy các task chưa bắt đầu ra khỏi queue. Cả hai không đảm bảo một task không hợp tác sẽ chấm dứt ngay.

`awaitTermination(timeout)` trả `false` khi hết thời gian mà executor vẫn chưa kết thúc. Nếu thread đang shutdown bị interrupt, ứng dụng nên có chính sách riêng để yêu cầu dừng và **khôi phục trạng thái interrupt** thay vì nuốt exception rồi chờ không giới hạn.

`get(timeout)` trên từng Future **không thay thế** việc đóng executor: một tác vụ bị bỏ qua sau timeout vẫn có thể giữ worker sống, làm chương trình không kết thúc hoặc làm tài nguyên không được giải phóng.

## 14. Những lỗi thiết kế thường gặp

| Sai lầm | Hậu quả | Giải pháp |
|---|---|---|
| Dùng `get()` vô hạn trên task có thể treo. | Caller bị block mãi. | Đặt deadline hữu hạn và đường xử lý timeout. |
| Cho rằng `get(timeout)` tự hủy. | Worker có thể còn chạy và làm side effect. | Cancel/cleanup theo ownership nếu cần. |
| Reset toàn bộ timeout cho mỗi get trong chuỗi. | Tổng thời gian chờ vượt SLA. | Dùng một overall deadline. |
| Gộp lỗi task, cancel và timeout thành một ngoại lệ chung. | Retry/báo lỗi sai nguyên nhân. | Phân biệt `ExecutionException`, `CancellationException`, `TimeoutException`. |
| Nuốt `InterruptedException`. | Mất tín hiệu hủy của caller. | Ném tiếp hoặc restore interrupt rồi thoát. |
| Chỉ xem `isDone()` là thành công. | Failed/canceled cũng là done. | Kiểm tra trạng thái, lấy kết quả/exception hợp lệ. |
| Cancel Future rồi coi resource đã đóng. | Cleanup của worker có thể còn chạy. | Xác nhận termination/cleanup riêng. |
| Chỉ dùng get timeout để kiểm soát socket/database. | I/O worker có thể tiếp tục sau khi caller bỏ cuộc. | Đặt I/O timeout và cancellation phù hợp. |
| Retry tác vụ có side effect sau timeout. | Task cũ vẫn chạy, gây thực hiện trùng. | Dùng idempotency/transaction và theo dõi kết quả cũ. |
| Gọi get của periodic ScheduledFuture để chờ một tick. | Future chưa hoàn tất dù tick đã chạy. | Latch hoặc Future riêng cho tick. |
| Dùng `orTimeout` rồi nghĩ computation đã dừng. | Underlying task có thể tiếp tục. | Tách Future completion và cancellation. |
| Không shutdown executor sau khi dùng. | Rò worker và có thể giữ JVM sống. | Owner quản lý lifecycle và awaitTermination có giới hạn. |

## 15. Checklist và câu hỏi tự kiểm tra

Trước khi dùng Future timeout trong thực tế, kiểm tra hợp đồng: **thời điểm bắt đầu budget**, ý nghĩa timeout với caller, task có còn hữu ích sau deadline không, khả năng interruption, owner của worker, cơ chế cleanup và cách truyền nguyên nhân lỗi. Đối với nhiều Future, tính thời gian còn lại từ một mốc chung; đối với task trong queue, đưa thời gian chờ queue vào deadline khi cần. Không retry tác dụng phụ ngoài JVM nếu chưa có chính sách tránh trùng. Theo dõi timeout rate, queue latency, task duration, cancellation và termination failures.

Các câu hỏi quan trọng:

1. **Vì sao `get()` có thể làm thread chờ vô hạn?** Nếu Future không hoàn tất thì lời get không có hạn có thể không trả về.
2. **`get(1, SECONDS)` timeout có tự ngắt worker không?** Không.
3. **`TimeoutException` có nghĩa task ném exception không?** Không; chỉ là caller đợi quá hạn.
4. **`ExecutionException` cho biết gì?** Task đã kết thúc bằng lỗi; `getCause()` cung cấp nguyên nhân.
5. **`CancellationException` khác timeout thế nào?** Future đã bị hủy, không chỉ một lượt chờ quá hạn.
6. **`InterruptedException` trong get là interrupt của ai?** Thread **đang đợi get**, không nhất thiết là worker.
7. **Có thể gọi get lần nữa sau TimeoutException không?** Có; nếu Future chưa bị hủy, nhưng phải quản lý tổng thời gian.
8. **Vì sao `isDone()` không có nghĩa thành công?** Vì lỗi và hủy cũng là kết thúc theo trạng thái Future.
9. **`cancel(true)` có bảo đảm cleanup xong?** Không; cần xác nhận worker termination/cleanup.
10. **Vì sao ba lần `get(2, SECONDS)` có thể đợi gần sáu giây?** Mỗi lời get được cấp hai giây riêng.
11. **Tại sao dùng `System.nanoTime()` cho deadline?** Nó phù hợp đo khoảng, không bị điều chỉnh theo đồng hồ lịch.
12. **`ScheduledFuture.get()` của task định kỳ có trả về sau một tick không?** Không, Future đại diện chuỗi định kỳ.
13. **`orTimeout()` có cùng nghĩa với `get(timeout)` không?** Không; một bên có thể complete Future ngoại lệ, bên kia chỉ kết thúc lời chờ.
14. **Khi get thành công, JMM đảm bảo gì?** Các hành động tính toán bất đồng bộ happens-before các hành động sau get tương ứng; không có nghĩa mọi object mutable ngoài giao thức đều an toàn.

**Ghi nhớ:** `Future.get(timeout)` giúp **giới hạn thời gian chờ một lần của caller**, không tự hủy task. Một hệ thống tránh chờ vô hạn thực sự cần **overall deadline, xử lý đủ các trạng thái kết thúc, cancellation hợp tác, I/O timeout và shutdown/cleanup có giới hạn**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-003">Cancellation và trạng thái của Future</a>

<details>
<summary>Xem chi tiết</summary>

# Cancellation và trạng thái của Future trong Java

> **Trọng tâm:** `Future<V>` là **handle của kết quả một tác vụ bất đồng bộ**, cho phép hỏi đã hoàn tất chưa, chờ kết quả, và **yêu cầu hủy**. Cần phân biệt rõ **kết quả Future** với **thread thực sự đã dừng**: `isDone() == true` có thể do thành công, lỗi hoặc hủy; `cancel(true)` chỉ *cố gắng interrupt* task đang chạy khi implementation hỗ trợ; `get(timeout, unit)` chỉ giới hạn **thời gian đợi của caller**, không tự hủy task. Với `ScheduledFuture`, phải tính cả trạng thái chờ lịch, periodic execution và chính sách xóa task đã hủy.

Bài nằm trong nhóm **Future & Scheduling** của Java concurrency nói chung. Mọi ví dụ độc lập dưới đây dùng Java SE 17+ (`ExecutorService`, `Future`, `FutureTask`, `ScheduledExecutorService` và `CompletableFuture` khi so sánh). Không bàn đến cơ chế của Spring.

## 1. Future đại diện cho điều gì?

Khi một thread gọi `ExecutorService.submit(Callable<V>)`, executor trả về một `Future<V>`. Future có thể được tạo **trước khi worker thực sự chạy**. Nó biểu thị khả năng nhận kết quả/tình trạng hoàn tất trong tương lai, không phải chính thread và không phải một lời hứa “task chắc chắn sẽ thành công”.

~~~text
Thread gọi submit()                Executor/worker
-------------------                ---------------
Future<V> f = submit(task) -----> [ task có thể đang xếp hàng ]
       |                                  |
       | f.isDone() = false                v
       |                              [ task bắt đầu ]
       |                                  |
       |                       +----------+-----------+
       |                       |          |           |
       |                    success      failure    cancellation
       |                       |          |           |
       +---- f.get() <---------+----------+-----------+
              result / ExecutionException / CancellationException
~~~

`Future` không cung cấp một enum trạng thái `NEW/QUEUED/RUNNING/SUCCEEDED` trong interface Java 17. Những tên này chỉ là **mô hình tư duy**, không phải API. Hai method chủ chốt để hỏi trạng thái là `isDone()` và `isCancelled()`; để biết kết quả hoặc nguyên nhân lỗi, caller sử dụng `get()` và xử lý exception.

### Những phương thức nền tảng

| API | Ý nghĩa và điểm cần nhớ |
| --- | --- |
| `boolean isDone()` | `true` nếu Future đã kết thúc theo hợp đồng: **bình thường, thất bại hoặc hủy**. Không có nghĩa task thành công. |
| `boolean isCancelled()` | `true` nếu Future đã bị hủy trước khi hoàn tất bình thường. Không đồng nghĩa thread đã dừng hẳn. |
| `V get()` | Đợi nếu cần; trả kết quả hoặc ném exception tương ứng. Không đặt deadline chờ. |
| `V get(long timeout, TimeUnit unit)` | Đợi tối đa khoảng thời gian chỉ định. `TimeoutException` **không tự hủy** task. |
| `boolean cancel(boolean mayInterruptIfRunning)` | Yêu cầu hủy: nếu chưa chạy thì ngăn chạy; nếu đang chạy thì tham số quyết định có cố interrupt hay không. Kết quả có thể phụ thuộc timing/implementation. |

`Future<V>` là interface, các implementation như `FutureTask`, `ScheduledFuture`, `CompletableFuture` có những khác biệt cụ thể. Không suy diễn mọi implementation phản ứng giống nhau với interruption hoặc cancellation.

## 2. Bảng trạng thái quan sát được qua Future

Hãy phân biệt trạng thái nội bộ giả định của task và **điều có thể kết luận từ API**:

| Tình huống | `isDone()` | `isCancelled()` | `get()` |
| --- | --- | --- | --- |
| Chưa hoàn thành: đang xếp hàng hoặc đang chạy | `false` | Thường `false` | Có thể **block** |
| Hoàn tất bình thường | `true` | `false` | Trả giá trị (có thể `null` với `Runnable`) |
| Hoàn tất do exception trong task | `true` | `false` | Ném `ExecutionException` (lấy nguyên nhân qua `getCause()`) |
| Đã chuyển sang cancelled | `true` | `true` | Ném `CancellationException` |
| Caller đợi quá hạn nhưng task vẫn còn chạy | Có thể vẫn `false` | Có thể vẫn `false` | `get(timeout)` ném `TimeoutException` tại lần gọi đó; các lần gọi sau vẫn có thể thành công |

Bảng là các tình huống điển hình, không phải một “snapshot atomically read” của cả hai flag và kết quả khi task đang hoàn tất cạnh tranh. Nếu đọc `isDone` rồi đọc `isCancelled` ở hai thời điểm, task có thể thay đổi trạng thái giữa chúng. **Đừng viết logic cần phân biệt chính xác nhiều tình huống chỉ bằng polling hai flag.** Khi cần kết quả cuối cùng, dùng `get()`/`get(timeout)` cùng xử lý exception.

### Phân biệt ba đường kết thúc

1. **Normal completion:** computation trả giá trị; `get()` trả đúng giá trị đó.
2. **Exceptional completion:** computation ném exception; với Future từ `submit`, `get()` ném `ExecutionException` bao quanh nguyên nhân gốc.
3. **Cancellation:** Future bị hủy thành công; `get()` ném `CancellationException`, không trả kết quả bình thường, ngay cả khi thân task đã thực hiện một số side effects.

Một exception trong body `Callable` **không** khiến `isCancelled()` thành true. Nó là failure, không phải cancellation. Tương tự, một Future bị cancel không có nghĩa business transaction được rollback.

## 3. Ví dụ 1 — thành công, lỗi và trạng thái sau khi hoàn tất

Tạo tệp `FutureOutcomes.java` (Java 17+):

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureOutcomes {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> successful = pool.submit(() -> 6 * 7);
            Future<Integer> failed = pool.submit(() -> {
                throw new IllegalStateException("broken");
            });

            int value = successful.get(); // Đợi thành công.
            System.out.println("result = " + value);
            System.out.println("success done = " + successful.isDone());
            System.out.println("success cancelled = "
                    + successful.isCancelled());

            try {
                failed.get();
            } catch (ExecutionException ex) {
                System.out.println("failure cause = "
                        + ex.getCause().getClass().getSimpleName());
            }

            System.out.println("failure done = " + failed.isDone());
            System.out.println("failure cancelled = "
                    + failed.isCancelled());
            System.out.println("cancel completed = "
                    + successful.cancel(true));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac FutureOutcomes.java
java FutureOutcomes
~~~

Đầu ra xác định trên đường chạy bình thường:

~~~text
result = 42
success done = true
success cancelled = false
failure cause = IllegalStateException
failure done = true
failure cancelled = false
cancel completed = false
~~~

Tại lúc in, `successful.get()` và `failed.get()` đã xác nhận cả hai Future kết thúc. Một Future thất bại vẫn có `isDone() == true` và `isCancelled() == false`. `cancel(true)` **không thể hủy lại một Future đã hoàn tất bình thường**.

`get()` còn cung cấp quan hệ *happens-before* từ hành động của computation tới hành động sau khi caller nhận completion qua `get()`. Điều đó giúp lấy kết quả công việc an toàn, nhưng **không biến mọi object mutable được chia sẻ ngoài Future thành thread-safe**.

## 4. `cancel(false)` và `cancel(true)` khác nhau thế nào?

`cancel(mayInterruptIfRunning)` là yêu cầu đổi trạng thái Future và, tùy tình huống, ngăn hoặc cố dừng computation.

### Trước khi task chạy

Nếu task vẫn chờ trong executor queue và một cancellation thành công, task tương ứng **không nên được chạy**. Cả `cancel(false)` và `cancel(true)` đều có thể hủy trước khi bắt đầu; tham số interrupt không cần thiết khi chưa có thread thực hiện task.

### Khi task đã bắt đầu

- `cancel(false)`: **không yêu cầu interrupt** worker đang chạy. Một implementation như `FutureTask` có thể chuyển Future sang cancelled trong khi computation **vẫn tiếp tục chạy** cho đến khi tự hoàn tất.
- `cancel(true)`: **cố interrupt** thread đang chạy, khi implementation biết thread đó và có hỗ trợ. Không buộc thread ngừng tức thì; kết quả phụ thuộc code có hợp tác với interruption hay không.
- Với cả hai, nếu Future bị hủy thành công, `isDone()`/`isCancelled()` có thể báo true và `get()` ném `CancellationException` **trước khi thân task thực sự dừng thực thi**.
- Nếu task đã hoàn tất trước `cancel`, cancellation không thay đổi kết quả đã hoàn thành; method thường trả `false`.
- Khi nhiều thread cùng gọi `cancel` trên một Future, kết quả boolean ở mỗi caller có thể khác nhau; sau đó nên kiểm tra `isCancelled()` nếu cần biết final cancellation state.

**Quan trọng:** `cancel` trả `true` là một **kết quả của yêu cầu hủy Future**, không phải bằng chứng mọi side effect của task đã dừng hoặc đã được rollback. Java không có cơ chế an toàn để cưỡng ép dừng một thread bất kỳ tại mọi điểm code.

## 5. Ví dụ 2 — hủy task còn xếp hàng, task không được chạy

Để tránh phụ thuộc scheduler, ta dùng executor **một worker**, chặn worker bằng latch, và hủy task thứ hai khi nó còn chắc chắn trong queue.

Tạo `CancelQueuedFuture.java` (Java 17+):

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CancelQueuedFuture {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean(false);

        try {
            pool.execute(() -> {
                occupied.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            occupied.await();
            Future<?> pending = pool.submit(() -> ran.set(true));

            boolean acceptedCancel = pending.cancel(false);
            System.out.println("cancel accepted = " + acceptedCancel);
            System.out.println("isDone = " + pending.isDone());
            System.out.println("isCancelled = " + pending.isCancelled());

            try {
                pending.get();
            } catch (CancellationException expected) {
                System.out.println("get = CancellationException");
            }
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }

        System.out.println("task ran = " + ran.get());
    }
}
~~~

Chạy:

~~~bash
javac CancelQueuedFuture.java
java CancelQueuedFuture
~~~

Đầu ra khi không bị interruption ngoài:

~~~text
cancel accepted = true
isDone = true
isCancelled = true
get = CancellationException
task ran = false
~~~

Tại lúc `cancel(false)`, worker duy nhất đang đợi `release`; task thứ hai **chưa thể bắt đầu**. Cancellation của FutureTask do `submit` tạo ngăn phần computation tương ứng chạy. Sau khi nhả latch và shutdown/drain executor, cờ `ran` vẫn false.

**Không kết luận:** Future bị cancelled thì wrapper chắc chắn biến mất khỏi queue ngay. `ThreadPoolExecutor` có thể giữ lại cancelled FutureTask trong queue cho tới khi được dequeue hoặc `purge()`. `ScheduledThreadPoolExecutor` có chính sách remove-on-cancel riêng ở phần sau.

## 6. Ví dụ 3 — `cancel(false)` khi task đang chạy: Future done nhưng worker chưa xong

Đây là khác biệt quan trọng nhất giữa **trạng thái Future** và **thực thi thực tế của task**. Với `FutureTask` trong executor chuẩn, một task đang chạy có thể bị đánh dấu cancelled mà không bị interrupt khi gọi `cancel(false)`.

Tạo `CancelRunningWithoutInterrupt.java` (Java 17+):

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CancelRunningWithoutInterrupt {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bodyFinished = new CountDownLatch(1);

        try {
            Future<?> running = pool.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    bodyFinished.countDown();
                }
            });

            started.await();
            boolean cancelled = running.cancel(false);

            System.out.println("cancel(false) = " + cancelled);
            System.out.println("Future done = " + running.isDone());
            System.out.println("Future cancelled = "
                    + running.isCancelled());
            System.out.println("body finished yet = "
                    + (bodyFinished.getCount() == 0));

            try {
                running.get();
            } catch (CancellationException expected) {
                System.out.println("get = CancellationException");
            }

            release.countDown();
            bodyFinished.await();
            System.out.println("body finished later = "
                    + (bodyFinished.getCount() == 0));
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac CancelRunningWithoutInterrupt.java
java CancelRunningWithoutInterrupt
~~~

Đầu ra với `FutureTask` do executor Java SE 17 tạo:

~~~text
cancel(false) = true
Future done = true
Future cancelled = true
body finished yet = false
get = CancellationException
body finished later = true
~~~

`started.await()` đảm bảo task đã bắt đầu và chưa thể đi tới `bodyFinished.countDown()` khi `release` chưa được mở. `cancel(false)` không interrupt; Future chuyển thành cancelled, nhưng worker vẫn chờ latch. Chỉ khi main mở `release`, task mới tới phần `finally` của body.

**Hệ quả thực tiễn:** Không đóng tài nguyên mà body đang dùng **chỉ vì `future.isDone()` thành true sau cancel**, nếu closure thực sự cần đợi worker ngừng. Muốn xác nhận task đã dừng, cần một completion signal riêng từ thân task, trạng thái executor/task lifecycle thích hợp, hoặc một protocol cancellation/cleanup rõ ràng.

## 7. Interruption là cơ chế hợp tác, không phải dừng cưỡng bức

`Thread.interrupt()` đặt trạng thái interrupt của thread hoặc khiến một số blocking methods đáp ứng bằng `InterruptedException`. Nhiều API như `Thread.sleep`, `BlockingQueue.take`, `CountDownLatch.await`, một số `Future.get` có thể thoát khỏi việc chờ khi bị interrupt.

Đối với đoạn tính toán CPU thuần không blocking, việc đặt interrupt flag **không tự buộc vòng lặp dừng**. Task phải **kiểm tra cờ** hoặc có điểm chờ interruptible và quyết định thoát an toàn.

Mẫu xử lý:

~~~java
// Snippet minh họa bên trong Callable/ Runnable.
try {
    while (!Thread.currentThread().isInterrupted()) {
        doOneUnitOfWork();
    }
} finally {
    releaseOwnedResources();
}
~~~

Nếu công việc có hoạt động blocking, cần catch `InterruptedException` phù hợp:

~~~java
// Snippet khi method không muốn lan truyền InterruptedException.
try {
    queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // Khôi phục flag.
    return;                              // Không tiếp tục chờ vô hạn.
}
~~~

**Đừng nuốt interruption** bằng một catch rỗng rồi tiếp tục vô tận. Nếu đang viết `Callable` và cho phép truyền exception, thường có thể lan truyền `InterruptedException` để cấp điều phối biết task không hoàn thành bình thường.

### Điều gì xảy ra khi task bỏ qua interrupt?

`cancel(true)` vẫn có thể khiến Future thành cancelled nhưng task không dừng. Chẳng hạn body chạy một vòng lặp vô hạn không kiểm tra interrupt, gọi native/blocking API không interruptible, hoặc catch `InterruptedException` rồi tiếp tục. Không nên dùng `Thread.stop()` như giải pháp: API đã deprecated và nguy hiểm vì có thể phá invariant.

Phải thiết kế **deadline, stop token, IO timeout, checkpoint và cleanup** cho task của mình thay vì tin cancellation của Future đủ để dừng mọi hoạt động.

## 8. Ví dụ 4 — `cancel(true)` đánh thức task đang chờ latch

Tạo `CancelInterruptibleTask.java` (Java 17+):

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CancelInterruptibleTask {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch interruptionObserved = new CountDownLatch(1);

        try {
            Future<?> future = pool.submit(() -> {
                started.countDown();
                try {
                    neverReleased.await();
                } catch (InterruptedException e) {
                    interruptionObserved.countDown();
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            boolean accepted = future.cancel(true);

            interruptionObserved.await(); // Đợi thân task xác nhận.
            System.out.println("cancel(true) = " + accepted);
            System.out.println("interrupt observed = true");
            System.out.println("isDone = " + future.isDone());
            System.out.println("isCancelled = " + future.isCancelled());

            try {
                future.get();
            } catch (CancellationException expected) {
                System.out.println("get = CancellationException");
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
~~~

Chạy:

~~~bash
javac CancelInterruptibleTask.java
java CancelInterruptibleTask
~~~

Stdout bình thường:

~~~text
cancel(true) = true
interrupt observed = true
isDone = true
isCancelled = true
get = CancellationException
~~~

`started.await()` xác nhận body đã chạy. `future.cancel(true)` khiến executor/FutureTask thử interrupt worker. Dù interrupt đến **trước khi** worker bắt đầu `neverReleased.await()`, cờ interrupt sẽ khiến lần chờ đó ném `InterruptedException`. `interruptionObserved.await()` là tín hiệu từ **chính body** xác nhận nó đã quan sát interrupt, tránh đoán từ thời gian ngủ hoặc chỉ nhìn `isCancelled`.

**Lưu ý:** Trong code thật nên tránh đợi một latch báo interruption **vô hạn** nếu implementation/task có thể không hợp tác; sử dụng timeout và nhánh fail-safe. Demo cố tình chọn task chắc chắn đang/chuẩn bị đợi một primitive interruptible để có output rõ ràng.

## 9. `Future.get(timeout)` không hủy computation

Nếu caller gọi `future.get(100, MILLISECONDS)` và task chưa hoàn tất trước deadline, method ném `TimeoutException`. Future vẫn có thể còn sống và hoàn thành sau đó. Việc caller **không muốn đợi nữa** không đồng nghĩa **task phải dừng**.

### Ví dụ 5 — timeout, rồi task hoàn tất bình thường

Tạo `TimeoutNotCancellation.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimeoutNotCancellation {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> future = pool.submit(() -> {
                started.countDown();
                release.await();
                return "done";
            });

            started.await();
            try {
                future.get(50, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                System.out.println("wait = TimeoutException");
            }

            System.out.println("cancelled = " + future.isCancelled());
            System.out.println("done before release = " + future.isDone());

            release.countDown();
            System.out.println("result = " + future.get());
            System.out.println("done after = " + future.isDone());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac TimeoutNotCancellation.java
java TimeoutNotCancellation
~~~

Stdout:

~~~text
wait = TimeoutException
cancelled = false
done before release = false
result = done
done after = true
~~~

Khi main đang đợi, worker chưa thể trả về vì latch chưa mở; timeout là điều chắc chắn. Sau timeout, `isCancelled()` còn false và `isDone()` false. Khi mở latch, `future.get()` nhận `done` bình thường.

**Phân biệt hai timeout:**
- `get(timeout)`: giới hạn **thời gian caller chờ**.
- Deadline/cancellation của task: logic riêng phải yêu cầu dừng hoặc bỏ việc, thường với `cancel`, interrupt, thời hạn I/O và kiểm tra deadline.

### Khi nào cancel sau timeout?

Một pattern có thể phù hợp:

~~~java
// Snippet minh họa trong caller.
try {
    return future.get(timeout, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    future.cancel(true); // Yêu cầu hủy, không bảo đảm dừng tức khắc.
    throw e;
}
~~~

Nhưng không tự động áp dụng cho mọi nghiệp vụ. Một task ghi database hoặc gửi message có thể đã commit side effect trước lúc caller timeout; cancel không rollback. Nếu caller retry, có nguy cơ xử lý trùng. Dùng idempotency key, deadline, transaction boundaries và trạng thái công việc đáng tin cậy ở tầng hệ thống.

## 10. `InterruptedException` của caller khác cancellation của Future

Một caller đang `future.get()` có thể bị thread khác interrupt. Khi đó `get()` ném `InterruptedException`; đây là tín hiệu **thread đợi bị interrupt**, không chứng minh computation đã bị hủy.

~~~java
// Snippet minh họa, method gọi có thể quyết định policy.
try {
    Object value = future.get();
    consume(value);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Quyết định có cancel Future hay chỉ dừng chờ.
}
~~~

Khi xử lý, phải tự hỏi: caller dừng chờ có đồng nghĩa muốn dừng task không? Đôi lúc task là shared computation, các caller khác vẫn cần kết quả — hủy nó chỉ vì một caller bị interrupt là sai chính sách.

Không dùng `Thread.interrupted()` một cách vô ý trong polling vì method static đó **đọc và xóa interrupt status**. `Thread.currentThread().isInterrupted()` chỉ kiểm tra flag, không xóa. Cần biết mình đang muốn giữ hay consume tín hiệu.

## 11. FutureTask: implementation phổ biến và trạng thái nội bộ

`FutureTask<V>` vừa là `Runnable` vừa là `Future<V>`. `ExecutorService.submit` thường tạo một `RunnableFuture` tương đương để executor chạy và caller theo dõi completion. Nó hỗ trợ `run()`, `cancel`, `get`, `isDone`, `isCancelled`; các trạng thái nội bộ là **chi tiết implementation**, không phải hợp đồng chung của `Future`.

Có thể tạo thủ công:

~~~java
// Snippet API, không độc lập.
FutureTask<Integer> task = new FutureTask<>(() -> 42);
new Thread(task).start();
int result = task.get();
~~~

Cần hiểu rằng `FutureTask.cancel(false)` có thể chuyển state sang cancelled trong khi body đang chạy; body tiếp tục thực thi nếu không bị interrupt. Điều này đã được minh họa ở `CancelRunningWithoutInterrupt`.

### `isDone` không phải acknowledgment hoàn tất mọi side effect

Đặc biệt sau một cancel thành công, `isDone() == true` có thể xảy ra **trước khi computation body thực sự trả về**. Không nên đóng file/socket do body sở hữu hay giả định event đã được ghi xong chỉ dựa vào Future status. Cần một completion signal riêng hoặc phối hợp lifecycle của executor/thread mà chương trình sở hữu.

Với `Callable` ném exception nhưng không bị cancel, Future thường hoàn tất ngoại lệ; `isDone()` true, `isCancelled()` false, `get()` ném `ExecutionException`. Một Future completed normally không thể bị cancel ngược về trước.

## 12. ScheduledFuture: chưa đến hạn, đang chạy và periodic task

`ScheduledExecutorService` hỗ trợ `schedule`, `scheduleAtFixedRate` và `scheduleWithFixedDelay`. Kết quả của các lời gọi này là `ScheduledFuture<?>`, mở rộng `Future` với `getDelay` và `compareTo` trong `Delayed`.

Với task **one-shot**:
- Trước thời điểm scheduled, Future có thể chưa done; `get()` có thể đợi đến khi chạy xong.
- Nếu `cancel` thành công trước thời điểm chạy, task không nên được thực thi.
- Khi task chạy và hoàn tất bình thường/lỗi, các quy tắc `Future.get()` tương ứng áp dụng.

Với **periodic task**:
- Một periodic task thành công mỗi lần chạy thường **không có kết quả cuối cùng** theo mỗi chu kỳ; `get()` không trả bình thường chỉ vì một lần thực thi đã xong.
- Future của periodic task thường ở trạng thái not-done **trong khi lịch chạy vẫn được duy trì**.
- Nếu một lần chạy ném exception, các lần tiếp theo theo hợp đồng scheduler có thể bị suppress; Future có thể kết thúc exceptional và `get()` ném `ExecutionException`.
- Khi cancel periodic Future, các lần lập lịch tương lai bị hủy theo hợp đồng, còn lần đang chạy có thể phải hợp tác với interruption nếu cần dừng ngay.
- Shutdown và continuation của periodic task còn phụ thuộc policy của `ScheduledThreadPoolExecutor` (`continueExistingPeriodicTasksAfterShutdownPolicy`, `executeExistingDelayedTasksAfterShutdownPolicy`).

**Đừng định nghĩa “periodic done” là đã chạy một chu kỳ.** Với nhiệm vụ định kỳ cần shutdown, phải quản lý Future đại diện cho toàn chuỗi thực thi và lifecycle của scheduler.

### Không nhầm `getDelay` với runtime hay guaranteed start

`scheduled.getDelay(TimeUnit.MILLISECONDS)` phản ánh thời gian ước lượng tới thời điểm đủ điều kiện chạy theo clock của scheduler. Nó không đảm bảo task đã chạy ngay khi delay bằng 0: worker còn có thể bận, scheduler đang shutdown hoặc có tài nguyên khác hạn chế.

## 13. Ví dụ 6 — hủy task scheduled trước khi tới hạn

Tạo `CancelScheduledFuture.java` (Java 17+):

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CancelScheduledFuture {
    public static void main(String[] args) throws InterruptedException {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(1);
        scheduler.setRemoveOnCancelPolicy(true);
        AtomicBoolean ran = new AtomicBoolean(false);

        try {
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> ran.set(true), 1, TimeUnit.DAYS);

            boolean cancelled = future.cancel(false);
            System.out.println("cancelled = " + cancelled);
            System.out.println("isDone = " + future.isDone());
            System.out.println("isCancelled = " + future.isCancelled());
            System.out.println("queue entries = "
                    + scheduler.getQueue().size());
            try {
                future.get();
            } catch (CancellationException expected) {
                System.out.println("get = CancellationException");
            }
        } finally {
            scheduler.shutdownNow();
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        }

        System.out.println("task ran = " + ran.get());
    }
}
~~~

Chạy:

~~~bash
javac CancelScheduledFuture.java
java CancelScheduledFuture
~~~

Đầu ra bình thường:

~~~text
cancelled = true
isDone = true
isCancelled = true
queue entries = 0
get = CancellationException
task ran = false
~~~

Task được lên lịch **một ngày sau**, nên hủy ngay lúc vừa schedule xảy ra trước thời điểm chạy. `setRemoveOnCancelPolicy(true)` khiến task bị hủy được loại **ngay** khỏi work queue của `ScheduledThreadPoolExecutor` thay vì tiếp tục giữ lại tới lúc delay đến hạn theo default policy; vì vậy số queue entries sau cancel bằng 0 trong ví dụ.

### Memory retention của task scheduled đã hủy

Theo tài liệu `ScheduledThreadPoolExecutor`, một task delayed được cancel theo mặc định có thể **không được loại khỏi queue ngay** cho đến khi thời điểm đủ điều kiện chạy đến, nhằm cho phép kiểm tra/tracking, nhưng có thể gây retention không cần thiết. `setRemoveOnCancelPolicy(true)` hữu ích khi nhiều task hẹn giờ bị hủy trước thời điểm chạy.

Không nhầm API này với `ThreadPoolExecutor` thông thường: nó là phương thức của `ScheduledThreadPoolExecutor`. Không tự biến mọi scheduler custom thành cùng một implementation.

## 14. CompletableFuture cũng là Future, nhưng hủy có khác biệt

`CompletableFuture<T>` triển khai `Future<T>` và `CompletionStage<T>`. Tuy vậy cancellation của nó không điều khiển worker giống `FutureTask`:

- `CompletableFuture.cancel(mayInterruptIfRunning)` hoàn tất CompletableFuture bằng `CancellationException` nếu chưa completed.
- Tham số **`mayInterruptIfRunning` không có hiệu lực** với chính implementation `CompletableFuture`, vì nó không dùng interrupt để điều khiển xử lý.
- Các dependent stages chưa hoàn tất có thể hoàn thành exceptional với `CompletionException` gây ra bởi cancellation.
- `CompletableFuture.join()` ném unchecked `CompletionException` đối với lỗi thông thường; nếu chính CompletableFuture đó bị cancel, `join()` có thể ném `CancellationException` trực tiếp.
- `isDone()` true nếu completed normally, exceptionally hoặc cancelled; `isCompletedExceptionally()` có thể true cả khi cancelled.

Vì vậy đừng viết code “`future.cancel(true)` luôn interrupt thread đang tính” rồi áp vào cả `FutureTask` lẫn `CompletableFuture`. Với `CompletableFuture.supplyAsync(...)`, việc cancel completion stage **không nhất thiết dừng Supplier đang chạy**.

Ví dụ minh họa đơn giản:

~~~java
// Snippet, có import java.util.concurrent.CompletableFuture.
CompletableFuture<Integer> cf = new CompletableFuture<>();
boolean cancelled = cf.cancel(true);
System.out.println(cf.isCancelled()); // true
// Điều này không chứng minh một thread thực thi bên ngoài bị interrupt.
~~~

Nếu cần stop công việc tính toán thực sự, thiết kế cancellation token, điều kiện dừng, deadline và quan hệ quản lý task riêng, không dựa duy nhất vào trạng thái của completion stage.

## 15. Cancellation và shutdown của ExecutorService không đồng nhất

`ExecutorService` có lifecycle của **executor**, tách biệt với Future của **từng task**.

| Thao tác | Điều thực hiện | Điều không bảo đảm |
| --- | --- | --- |
| `future.cancel(false)` | Yêu cầu hủy Future cụ thể, không interrupt running task | Không ngắt body đang chạy |
| `future.cancel(true)` | Yêu cầu hủy Future và cố interrupt running task | Không cưỡng bức body dừng tức thì |
| `executor.shutdown()` | Ngừng nhận task mới; xử lý task đã nhận theo hợp đồng | Không tự cancel mọi Future và không ép worker kết thúc ngay |
| `executor.shutdownNow()` | Cố interrupt tasks đang thực thi, trả về tasks chưa bắt đầu | Không bảo đảm mọi task dừng hay completed Future được ghi là cancelled |
| `executor.awaitTermination(timeout, unit)` | Đợi executor terminated sau shutdown | Không tự hủy computation còn sống |
| `Future.get(timeout, unit)` | Giới hạn thời gian đợi kết quả của caller | Không cancel, interrupt hoặc cleanup queued task |

Một lỗi khó phát hiện: **`shutdownNow()` trả danh sách `Runnable` chưa bắt đầu**. Nếu một số item tương ứng với Future mà caller đang giữ, cần quyết định rõ cách xử lý: chỉ vì task bị lấy ra khỏi queue không có nghĩa Future ấy tự được completed/cancelled trong mọi implementation. Nếu cần hoàn tất Future của các task chưa chạy, coordinator phải có protocol cancellation/result reporting thích hợp.

Đối với graceful shutdown, mẫu thông thường:

~~~java
// Snippet minh họa, caller có policy xử lý InterruptedException.
executor.shutdown();
if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
    java.util.List<Runnable> notStarted = executor.shutdownNow();
    // Ghi nhận/cancel hoặc chuyển tiếp notStarted theo chính sách.
    executor.awaitTermination(10, TimeUnit.SECONDS);
}
~~~

Một worker đang làm I/O không interruptible có thể không dừng đúng thời gian kỳ vọng. Dừng Future và dừng resources là các thao tác phải được phối hợp.

## 16. `invokeAll` và timeout của nhóm tác vụ

`ExecutorService.invokeAll(tasks)` chờ tất cả task trong nhóm hoàn tất và trả về danh sách Future, nhưng caller vẫn phải kiểm tra `get()` từng Future để phân biệt thành công và lỗi.

`invokeAll(tasks, timeout, unit)` có hợp đồng đáng chú ý: method trả về khi mọi task đã hoàn tất hoặc khi hết timeout; **các task chưa hoàn thành sẽ bị hủy** theo API. Đây là sự khác biệt với `Future.get(timeout)` — thứ **không tự cancel**.

Ví dụ khái niệm:

~~~java
// Snippet minh họa trong method throws InterruptedException.
java.util.List<java.util.concurrent.Callable<String>> tasks = ...;
java.util.List<Future<String>> futures =
        executor.invokeAll(tasks, 2, TimeUnit.SECONDS);

for (Future<String> future : futures) {
    if (future.isCancelled()) {
        // Task không hoàn tất trong hạn hoặc bị hủy vì lý do khác.
    } else {
        // get() để lấy kết quả hoặc bắt ExecutionException.
    }
}
~~~

Đoạn này không độc lập vì biến `tasks`/`executor` chưa khai báo đầy đủ. Cũng như cancellation chung, việc API đánh dấu Future cancelled **không tuyệt đối bảo đảm** mọi body đã dừng/đã rollback side effects.

Khi nhóm task có dependency hoặc cần partial results, thiết kế cách tổng hợp thành công/thất bại/timeout/cancel một cách rõ ràng, tránh coi mỗi Future done là successful.

## 17. Race conditions quanh việc hủy và hoàn tất

Cancellation **chạy cạnh tranh** với task completion:

~~~text
Thread A: cancel(true)  -------+
                               +--- ai cập nhật trạng thái trước?
Worker:   return result -------+
~~~

Một trong các kết quả có thể xảy ra:

- Task completed trước khi cancel có hiệu lực: `cancel` thường trả false; `get` trả kết quả hoặc lỗi đã completed.
- Cancel thắng khi Future còn chưa completed: `isCancelled` true và `get` ném `CancellationException`.
- Task đã bắt đầu side effect, cancel thắng Future state, nhưng body có thể tiếp tục side effect nếu không hợp tác hoặc nếu cancellation tới quá muộn.
- Hai caller đồng thời cancel có thể nhận giá trị boolean khác nhau; nhìn return của một caller không phải cách duy nhất suy ra trạng thái cuối.

**Quy tắc thiết kế:** Cancellation phải **idempotent và có thể quan sát được**, nhưng các side effects của task cần giao thức riêng. Với job gửi email, tạo đơn hàng, trừ tiền hoặc ghi database, `cancel(true)` không phải transaction rollback. Có thể cần idempotency key, stored job status, outbox, acknowledgment sau khi commit, compensating action và cách resume khi restart.

### State machine nghiệp vụ khác với Future state

Một API nghiệp vụ có thể có `PENDING`, `RUNNING`, `COMMITTED`, `CANCEL_REQUESTED`, `CANCELLED`, `FAILED`. Một `Future.isCancelled` không tự chứng minh job đã ở `CANCELLED` ở storage hoặc hệ thống ngoài JVM.

Ví dụ nếu khách hàng nhấn Cancel trong lúc giao dịch đang commit, coordinator phải chọn rõ:
- Không cho commit nếu cancellation thắng trước điểm quyết định.
- Hoặc chấp nhận commit đã hoàn tất và trả trạng thái hoàn thành, dù caller không còn cần kết quả.
- Hoặc ghi trạng thái cần compensation và xử lý bất đồng bộ.
Future API **không quyết định** policy này.

## 18. Task cancellation cooperative: những điểm cần kiểm soát

Một task có thể được hủy an toàn hơn khi:

1. Làm việc theo **đơn vị nhỏ** và thường xuyên kiểm tra `isInterrupted()` hoặc stop token hợp lệ.
2. Sử dụng blocking API có hỗ trợ interruption/timeout.
3. Khi bị interrupt, **thoát** hoặc truyền exception thay vì nuốt tín hiệu.
4. Đặt việc giải phóng tài nguyên vào `finally` hoặc try-with-resources.
5. Tránh đóng tài nguyên dùng chung bởi task khác nếu cancellation chỉ áp dụng cho một task.
6. Không viết vào shared mutable state dở dang; cập nhật các invariant theo bước atomic/transaction rõ.
7. Có deadline cụ thể khi làm I/O; interrupt không luôn hiệu quả với mọi thư viện bên ngoài.
8. Theo dõi được **task body actually stopped** nếu cần cleanup tài nguyên ngoài Future handle.
9. Khi có nhiều consumer của một Future/shared computation, cân nhắc ai **có quyền** cancel.
10. Khi tự retry, kiểm soát idempotency để không tạo duplicate side effect sau timeout.

Mẫu vòng CPU-bound:

~~~java
// Snippet của một task tính toán, không phải chương trình độc lập.
while (!Thread.currentThread().isInterrupted()) {
    if (workQueueIsEmpty()) {
        break;
    }
    processNextUnit();
}
cleanupAfterLoop();
~~~

Trong code thật, việc dequeue/kiểm tra queue phải có concurrency protocol phù hợp; snippet này chỉ nhấn mạnh kiểm tra interrupt flag, không cung cấp một queue implementation.

Đừng dựa vào `Thread.sleep(100)` để chứng minh cancellation được nhận “đúng lúc”; scheduler có thể thay đổi thời điểm. Dùng latch để kiểm soát test hoặc có timeout rõ ràng.

## 19. Kiểm chứng correctness của Future cancellation

Một test có giá trị cần tách **trạng thái Future** khỏi **tiến triển thực của worker**:

| Câu hỏi | Kỹ thuật kiểm chứng |
| --- | --- |
| Task đã bắt đầu chưa? | `CountDownLatch started` giảm ở đầu body |
| Task có chắc còn chờ trong queue? | Một single-worker executor bị giữ bằng latch, task thứ hai được submit |
| `cancel(false)` có interrupt worker không? | Task đang chờ latch và chỉ release sau khi đọc Future status |
| `cancel(true)` được body quan sát? | Latch `interruptionObserved` giảm trong catch `InterruptedException` |
| Caller bị timeout nhưng task có còn sống? | Chỉ release latch **sau** `get(timeout)` ném `TimeoutException` |
| Task đã thực sự dừng chưa? | Latch/callback cuối body, `join` hoặc executor termination phù hợp |
| Lỗi task được giữ trong Future? | `get()` và `ExecutionException.getCause()` |
| Task scheduled đã cancel có còn trong queue? | Kiểm tra remove-on-cancel policy của scheduler cụ thể |

Không dùng một kết quả `isDone() == true` để xác nhận resource cleanup sau cancel. Không kết luận cancellation dừng side effect chỉ vì `Future.get()` ném `CancellationException`.

### Đừng biến demo thành test treo mãi

Các ví dụ dùng latch và giả định không có interruption bên ngoài để có stdout xác định. Với **automated test suite thực tế**, các bước `await()`/`join()` nên có timeout, nhánh fail-safe và cleanup trong `finally`; nếu implementation sai hoặc task không hợp tác, test sẽ không treo vô hạn.

Khi cần đo performance, tách biệt thời gian queue wait, execution, cancellation latency và cleanup latency. Cancelled count cao có thể là dấu hiệu deadline quá ngắn hoặc executor backlog quá lớn.

## 20. Những nhầm lẫn cần tránh

| Nhầm lẫn | Sự thật |
| --- | --- |
| “`isDone()` true nghĩa là thành công.” | Có thể là normal, failure hoặc cancellation |
| “`isCancelled()` true nghĩa worker chắc đã dừng.” | Future state có thể cancelled trong khi task body vẫn chạy |
| “`cancel(false)` không thể hủy task đã bắt đầu.” | Với FutureTask, có thể đánh dấu cancelled mà không interrupt worker |
| “`cancel(true)` giống `Thread.stop()`.” | Chỉ cố interrupt, cần task hợp tác |
| “`cancel(true)` rollback database.” | Không có transaction/compensation tự động |
| “`get(timeout)` timeout sẽ hủy task.” | Nó chỉ giới hạn waiting time của caller |
| “Bắt `InterruptedException` rồi bỏ qua là được.” | Có thể làm task không phản hồi cancellation |
| “Caller `get()` bị interrupt nghĩa Future bị cancelled.” | Hai trạng thái khác nhau |
| “Future lỗi thì `isCancelled()` true.” | Task failure và cancellation khác nhau |
| “Một Future bị cancel sẽ tự mất khỏi queue ngay.” | Có thể còn wrapper queued; scheduler/remove-on-cancel policy khác nhau |
| “`ScheduledFuture.get()` của periodic task trả sau một chu kỳ.” | Periodic task thường không hoàn tất bình thường sau mỗi lần chạy |
| “`CompletableFuture.cancel(true)` interrupt supplier.” | Tham số interrupt không có hiệu lực với CompletableFuture |
| “`shutdownNow()` là cancel chính xác mọi Future.” | Cố interrupt và trả queued tasks, không cam kết mọi Future completed/cancelled |
| “`invokeAll(timeout)` giống `get(timeout)`.” | invokeAll timeout có hợp đồng cancel unfinished tasks |
| “Cancel xong có thể đóng tài nguyên mà body đang dùng.” | Phải phối hợp completion/cleanup thực tế |
| “`Future` có enum RUNNING/QUEUED trong Java 17.” | Interface Java 17 không có API trạng thái chi tiết đó |

## 21. Câu hỏi tự kiểm tra

**Câu 1.** `isDone() == true` và `isCancelled() == false` có chứng minh task thành công không?  
**Đáp án:** Không. Nó có thể đã ném exception; gọi `get()` và xử lý `ExecutionException`.

**Câu 2.** `future.cancel(false)` trả true trong khi FutureTask đang chạy. Body có tự dừng ngay không?  
**Đáp án:** Không nhất thiết; Future có thể cancelled nhưng body vẫn chạy vì không bị interrupt.

**Câu 3.** `future.cancel(true)` được gọi khi task đang block tại `CountDownLatch.await()`. Chuyện gì có thể xảy ra?  
**Đáp án:** Thread được cố interrupt; `await` có thể ném `InterruptedException` để body xử lý. Cần xác nhận body thực sự quan sát và thoát.

**Câu 4.** `get(50, MILLISECONDS)` ném `TimeoutException` thì `isCancelled()` tự chuyển true không?  
**Đáp án:** Không. Timeout chỉ áp dụng lần chờ đó.

**Câu 5.** Task ném `IllegalStateException`. `Future.get()` ném trực tiếp `IllegalStateException` không?  
**Đáp án:** Với Future từ `ExecutorService.submit`, `get()` ném `ExecutionException`; nguyên nhân gốc ở `getCause()`.

**Câu 6.** Future đã trả kết quả bình thường có thể cancel thành công sau đó không?  
**Đáp án:** Không; Future đã completed nên cancellation không thay đổi kết quả.

**Câu 7.** Một `ScheduledFuture` periodic chạy thành công ba lần, `isDone()` có bắt buộc true không?  
**Đáp án:** Không. Periodic Future chưa dừng lịch chạy thì thông thường còn not-done; `get()` không trả chỉ vì một chu kỳ kết thúc.

**Câu 8.** Vì sao nên cân nhắc `setRemoveOnCancelPolicy(true)` trong `ScheduledThreadPoolExecutor`?  
**Đáp án:** Giảm retention của delayed tasks bị cancel còn lưu trong queue đến thời điểm tới hạn theo default policy.

**Câu 9.** `CompletableFuture.cancel(true)` có bắt buộc interrupt thread đang chạy Supplier không?  
**Đáp án:** Không; `mayInterruptIfRunning` không có hiệu lực với implementation CompletableFuture.

**Câu 10.** `invokeAll(tasks, timeout, unit)` và `future.get(timeout, unit)` khác gì ở cancellation?  
**Đáp án:** invokeAll timed hủy những task chưa hoàn thành khi timeout; get timed không tự hủy Future.

**Câu 11.** Nếu caller bị interrupt khi `get()`, task có tự bị hủy không?  
**Đáp án:** Không. Caller phải quyết định có cancel task hay không theo ownership/policy.

**Câu 12.** Sau `cancel(true)`, `isDone()==true`, có được mặc định đóng ngay socket task đang dùng?  
**Đáp án:** Không. Body có thể vẫn đang thực thi; phải có protocol dừng/cleanup thực tế.

## Tài liệu tham khảo chính thức

- [`Future` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get`, `isDone`, `isCancelled`, `cancel`, memory consistency.
- [`FutureTask` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html): runnable future, cancellation của computation.
- [`ExecutorService` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, `invokeAll`, `shutdown`, `shutdownNow`.
- [`ScheduledExecutorService` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay`.
- [`ScheduledFuture` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): Future cho scheduled computation.
- [`ScheduledThreadPoolExecutor` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): remove-on-cancel và shutdown policies.
- [`CompletableFuture` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): cancellation/completion stages và interrupt semantics.
- [`CountDownLatch` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối test xác định.

**Ghi nhớ:** `Future` cho biết trạng thái **kết quả computation**; nó không phải khóa cưỡng bức thread. Muốn cancellation an toàn phải **định nghĩa ownership, cooperative interruption, deadline, side effect policy và tín hiệu body đã thực sự kết thúc**, đặc biệt với scheduling và executor shutdown.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-004">Callable&lt;T&gt; so với Runnable khi cần trả kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Callable<T> so với Runnable khi cần trả kết quả

## 1. Bài toán: tác vụ chạy ở thread khác sẽ trả kết quả như thế nào?

Trong Java Concurrency, một công việc được gửi cho executor thường chạy ở thread khác với thread gửi. Khi chỉ cần **thực hiện hành động** (ghi log, cập nhật trạng thái, gửi thông báo), `Runnable` là một lựa chọn tự nhiên. Khi công việc cần **tính toán và trả về một giá trị**, `Callable<T>` biểu diễn kết quả trực tiếp qua kiểu `T`.

Cả hai đều là **functional interface**, có thể triển khai bằng lambda:

```java
Runnable printMessage = () -> System.out.println("Done");

Callable<Integer> calculate = () -> 21 * 2;
```

Sự khác biệt quan trọng không nằm ở việc “có chạy bất đồng bộ hay không”. Bản thân `Runnable.run()` và `Callable.call()` chỉ là các phương thức thông thường: gọi trực tiếp thì chúng chạy trên **thread hiện tại**. Chúng chỉ chạy theo cơ chế bất đồng bộ khi được giao cho `Thread`, `Executor`, `ExecutorService` hoặc một cơ chế điều phối khác.

Khi giao cho `ExecutorService`, ứng dụng nhận kết quả thông qua một `Future<T>` thay vì nhận trực tiếp tại lời gọi `submit(...)`. `Future` là **đại diện cho kết quả có thể xuất hiện trong tương lai**, cung cấp cách chờ hoàn tất, nhận giá trị, nhận ngoại lệ, kiểm tra trạng thái và yêu cầu hủy.

## 2. So sánh Runnable và Callable<T>

| Đặc điểm | `Runnable` | `Callable<T>` |
| --- | --- | --- |
| Giao diện | `java.lang.Runnable` | `java.util.concurrent.Callable<T>` |
| Phương thức trừu tượng | `void run()` | `T call() throws Exception` |
| Kết quả trả trực tiếp từ phương thức | Không có (`void`) | Có, kiểu `T` |
| Checked exception trong chữ ký | `run()` không khai báo checked exception | `call()` khai báo `throws Exception` |
| Dùng với `Thread` trực tiếp | `new Thread(runnable)` | Không truyền trực tiếp `Callable` vào constructor `Thread` |
| Dùng với `Executor.execute(...)` | Có, nhận `Runnable` | Không nhận `Callable` trực tiếp |
| Dùng với `ExecutorService.submit(...)` | Có, nhận `Future<?>` hoặc `Future<T>` nếu cung cấp kết quả cố định | Có, nhận `Future<T>` |
| Kịch bản điển hình | Task thực hiện hành động, không tạo kết quả logic | Task tính toán, truy vấn, xử lý dữ liệu và trả về kết quả |

Điểm cần nhớ: `Runnable` không đồng nghĩa “fire-and-forget”. Khi gửi `Runnable` bằng `ExecutorService.submit(runnable)`, caller vẫn nhận `Future<?>` để **đợi hoàn tất và quan sát lỗi**; chỉ là không có giá trị nghiệp vụ từ `run()`.

Ngược lại, `Callable<T>` không tự sinh thread hay tự bảo đảm chạy song song. Một `Callable` chạy qua `call()` trực tiếp vẫn là lời gọi đồng bộ.

## 3. API và kiểu kết quả của execute/submit

Hai abstraction thường gặp:

```java
// Fragment minh họa các chữ ký liên quan (không phải chương trình độc lập).
interface Executor {
    void execute(Runnable command);
}

// Một phần API của ExecutorService:
Future<?> submit(Runnable task);
<T> Future<T> submit(Runnable task, T result);
<T> Future<T> submit(Callable<T> task);
```

### 3.1. execute(Runnable): giao việc, không trả Future

`executor.execute(runnable)` trả về `void`. Khi lời gọi thành công, executor đã nhận việc theo chính sách của nó, nhưng **không có handle kết quả** mà API này trả về cho caller. Nó cũng không bảo đảm công việc đã hoàn tất khi `execute` trả về.

Nếu `Runnable.run()` ném unchecked exception trong worker thread, cơ chế xử lý lỗi của thread/`ThreadPoolExecutor` có thể ghi nhận nó thông qua `UncaughtExceptionHandler` hoặc `afterExecute` (tùy cách tùy biến). Lỗi không được chuyển thành `Future.get()` vì không có Future nào được trả về. Nếu executor từ chối task, `RejectedExecutionHandler` có thể ném `RejectedExecutionException` ngay khi gửi việc.

### 3.2. submit(Runnable): có Future, giá trị hoàn thành là null

```java
Future<?> future = executor.submit(
        () -> System.out.println("Side effect finished"));

Object result = future.get(); // null nếu Runnable hoàn tất bình thường
```

`future.get()` đợi tới khi Runnable kết thúc (trừ khi bị interrupt hoặc task bị hủy). Khi thành công, giá trị trả về là `null`; nếu Runnable ném lỗi khi chạy, `get()` ném `ExecutionException` với nguyên nhân ở `getCause()`.

Đừng nhầm `Future<?>` với “không theo dõi được công việc”. Nó theo dõi **vòng đời** dù không chứa kết quả tính toán.

### 3.3. submit(Runnable, result): giá trị cố định khi thành công

```java
Future<String> future = executor.submit(
        () -> performSideEffect(),
        "ACK");
```

Đây là ví dụ fragment: `performSideEffect()` do ứng dụng định nghĩa. Sau khi `Runnable` chạy thành công, `future.get()` trả chuỗi `"ACK"` do caller cung cấp **trước** lúc chạy. Đây không phải giá trị mà `run()` tự tính và trả về. Nếu Runnable ném lỗi, `get()` ném `ExecutionException` chứ không trả `"ACK"`.

Dùng cách này khi bản thân tác vụ không có giá trị cần tính, nhưng caller muốn một Future có kiểu cụ thể và giá trị biểu thị hoàn tất.

### 3.4. submit(Callable<T>): giá trị do chính tác vụ tính toán

```java
Callable<Integer> task = () -> 20 + 22;
Future<Integer> future = executor.submit(task);
Integer answer = future.get(); // 42 khi tính toán thành công
```

`Callable.call()` có thể trả `null` nếu đó là kết quả hợp lệ của nghiệp vụ; kiểu `T` không yêu cầu non-null. Chọn kiểu trả về rõ ràng (`Integer`, `String`, `List<Record>`, một record kết quả...) để caller hiểu chính xác kết quả tính toán.

## 4. Ví dụ Java 17 độc lập: cùng executor, ba cách nhận kết quả

Chương trình sử dụng **single-thread executor** và luôn gọi `get()` trước khi chuyển sang tác vụ tiếp theo, nên thứ tự các dòng in ra được kiểm soát. Ngoại lệ từ `Callable` cũng được minh họa dưới dạng nguyên nhân của `ExecutionException`.

Lưu vào `CallableVsRunnableDemo.java`:

```java
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CallableVsRunnableDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Runnable action = () ->
                    System.out.println("Runnable: side effect");
            Future<?> actionFuture = executor.submit(action);
            System.out.println("Runnable result: " + actionFuture.get());

            Callable<Integer> computation = () -> 20 + 22;
            Future<Integer> computationFuture =
                    executor.submit(computation);
            System.out.println("Callable result: " + computationFuture.get());

            Future<String> fixedResult = executor.submit(
                    () -> System.out.println("Runnable: acknowledged"),
                    "ACK");
            System.out.println("Fixed result: " + fixedResult.get());

            Callable<String> failing = () -> {
                throw new IOException("source unavailable");
            };
            Future<String> failedFuture = executor.submit(failing);
            try {
                System.out.println(failedFuture.get());
            } catch (ExecutionException exception) {
                System.out.println(
                        "Failure cause: "
                        + exception.getCause().getClass().getSimpleName());
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

Chạy:

```bash
javac CallableVsRunnableDemo.java
java CallableVsRunnableDemo
```

Kết quả:

```text
Runnable: side effect
Runnable result: null
Callable result: 42
Runnable: acknowledged
Fixed result: ACK
Failure cause: IOException
```

Giải thích: ở lần gửi thứ nhất, `submit(Runnable)` chỉ cho biết đã hoàn tất, nên `get()` là `null`. Ở lần thứ hai, `Callable<Integer>` thực sự tạo ra `42`. Ở lần thứ ba, `"ACK"` được truyền từ phía caller, không đến từ `Runnable.run()`. Ở lần cuối, `IOException` được giữ làm nguyên nhân của `ExecutionException`; nó không tự xuất hiện như một ngoại lệ của lời gọi `submit`.

Ví dụ dùng `main throws Exception` để giữ mã gọn; trong ứng dụng thực tế, code sử dụng `get()` cần có kế hoạch xử lý `InterruptedException`, `ExecutionException`, timeout và hủy. Đoạn `finally` gửi yêu cầu shutdown và thử chờ tối đa 5 giây; đây không phải bảo đảm mọi task bất hợp tác sẽ dừng tức thì sau `shutdownNow()`.

## 5. Future<T> là cầu nối giữa thread gửi và kết quả

Khi gọi `submit(Callable<T>)`, executor thường biến nhiệm vụ thành một đối tượng kiểu `RunnableFuture<T>` (một ví dụ triển khai là `FutureTask<T>`). Worker có thể chạy wrapper dạng `Runnable`, còn `Future<T>` cho caller các thao tác:

| API | Ngữ nghĩa |
| --- | --- |
| `T get()` | Chờ kết quả; thành công thì trả `T`; có thể bị gián đoạn hoặc ném ngoại lệ nếu task lỗi/hủy |
| `T get(long timeout, TimeUnit unit)` | Chờ tối đa khoảng thời gian chỉ định; có thể ném `TimeoutException` |
| `boolean isDone()` | `true` nếu task đã hoàn tất bình thường, lỗi hoặc bị hủy |
| `boolean isCancelled()` | `true` nếu task đã bị hủy |
| `boolean cancel(boolean mayInterruptIfRunning)` | Cố gắng hủy; giá trị trả về cho biết yêu cầu hủy có thành công theo trạng thái Future hay không |

Một `Future` có thể đại diện cho các trạng thái logic:

```text
SUBMITTED / QUEUED → RUNNING → COMPLETED_SUCCESS
                            └──→ COMPLETED_FAILURE
              └───────────────→ CANCELLED
```

Đây là sơ đồ khái niệm về vòng đời task; các chuyển trạng thái chi tiết còn phụ thuộc loại `Future`. **`isDone() == true` không có nghĩa task thành công**, bởi vì task lỗi hoặc bị hủy cũng là “done”. Cần `get()` hoặc cơ chế ghi nhận kết quả rõ ràng để phân biệt các trường hợp.

### Kết quả, lỗi, hủy: ba đường kết thúc cần phân biệt

```java
// Fragment: future do ExecutorService.submit(callable) trả về.
try {
    String value = future.get(1, TimeUnit.SECONDS);
    System.out.println("Success: " + value);
} catch (java.util.concurrent.TimeoutException timedOut) {
    System.out.println("Still waiting");
} catch (java.util.concurrent.CancellationException cancelled) {
    System.out.println("Cancelled");
} catch (java.util.concurrent.ExecutionException failed) {
    Throwable cause = failed.getCause();
    System.out.println("Task failed: " + cause);
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
}
```

Đây là fragment sử dụng `Future<String> future` đã được khai báo ở nơi khác.

- **`ExecutionException`**: tác vụ đã chạy và kết thúc bằng exception hoặc error; kiểm tra `getCause()` để biết lỗi gốc. `Callable.call()` cho phép checked exception; `Future.get()` không ném thẳng checked exception đó.
- **`CancellationException`** (unchecked): Future đã bị hủy. `get()` không trả một kết quả hợp lệ.
- **`TimeoutException`**: `get(timeout, unit)` đã chờ quá giới hạn; **task thường vẫn đang tiếp tục**. Muốn hủy, caller phải yêu cầu riêng, chẳng hạn `cancel(true)`, và chấp nhận rằng hủy chỉ mang tính hợp tác.
- **`InterruptedException`**: thread đang chờ `get()` bị ngắt; điều này **không tự hủy task phía worker**. Mã ứng dụng thường khôi phục interrupt flag hoặc truyền ngoại lệ lên tầng trên theo hợp đồng của mình.

### Future và khả năng thấy dữ liệu giữa các thread

`Future` còn cung cấp ràng buộc memory consistency: các hành động của tác vụ bất đồng bộ **happen-before** các hành động sau khi thread khác lấy kết quả qua `Future.get()` tương ứng. Vì vậy, `get()` hữu ích như một điểm đồng bộ để đọc dữ liệu mà task đã công bố đúng cách.

Tuy nhiên, `Future.get()` **không** biến mọi trạng thái shared mutable thành thread-safe. Nếu nhiều task cùng sửa một `ArrayList` hoặc biến thông thường trong lúc đang chạy, vẫn cần đồng bộ hóa, collection concurrent hoặc thiết kế không chia sẻ mutable state. Với kết quả đơn giản, ưu tiên **trả kết quả trực tiếp từ `Callable`** thay vì ghi vào biến toàn cục rồi tự chờ.

## 6. Checked exception và lỗi bị “ẩn” khi dùng submit

`Callable<T>.call() throws Exception` cho phép code bất đồng bộ gọi các API có checked exception:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

// Fragment: code đọc file và báo IOException qua Future.get().
Callable<String> readText = () ->
        Files.readString(Path.of("data.txt"));
```

`Files.readString` có thể ném `IOException`; vì `call()` khai báo `throws Exception`, lambda trên hợp lệ. Đối với `Runnable.run()`, caller phải bắt checked exception bên trong và tự xử lý hoặc wrap thành unchecked exception; nó không thể khai báo `throws IOException` khi override `run()`.

Một khác biệt thực tế là cách quan sát lỗi của tác vụ:

- `execute(runnable)`: exception unchecked không được xử lý từ `run()` có thể đi qua cơ chế uncaught exception của worker; caller không có `Future` do lời gọi này trả về.
- `submit(runnable/callable)`: executor thường thực thi wrapper `FutureTask`, wrapper **ghi nhận exception**. Vì thế exception của task không nhất thiết xuất hiện ở log uncaught exception của worker. Caller cần gọi `get()` hoặc có đường ghi nhận lỗi khác.
- `submit` vẫn có thể ném `RejectedExecutionException` **ngay lúc gửi** nếu executor từ chối theo chính sách ném lỗi. Đây là lỗi **admission**, khác `ExecutionException` do bản thân task chạy thất bại.

Lỗi phổ biến: sử dụng `submit` rồi bỏ luôn Future. Nếu task thất bại, ứng dụng có thể không đọc được nguyên nhân và tưởng rằng công việc thành công. Với các tác vụ quan trọng, hãy giữ Future, gom kết quả, hoặc có đường thu thập ngoại lệ và metric được thiết kế rõ.

## 7. Nguy cơ chờ vô hạn và cách dùng timeout

`Future.get()` không có timeout có thể chặn caller mãi nếu task không hoàn tất hoặc Future bị bỏ quên vì một chính sách từ chối im lặng. Trong hệ thống có deadline, `get(timeout, unit)` giúp giới hạn **thời gian caller chờ**, nhưng không đồng nghĩa giới hạn thời gian xử lý trên worker.

Ví dụ fragment:

```java
try {
    Result result = resultFuture.get(500, TimeUnit.MILLISECONDS);
    use(result);
} catch (TimeoutException timedOut) {
    boolean cancellationRequested = resultFuture.cancel(true);
    reportTimeout(cancellationRequested);
}
```

`Result`, `resultFuture`, `use` và `reportTimeout` là các thành phần giả định của ứng dụng; đoạn này minh họa ý tưởng, **không phải chương trình độc lập**. `cancel(true)` thử interrupt thread đang chạy, nhưng Java không thể cưỡng ép dừng một task bỏ qua interrupt hoặc mắc kẹt ở thao tác không có khả năng interrupt. Nếu task gây side effect, hủy cũng không hoàn tác những việc đã làm trước đó.

### Bẫy thread starvation deadlock

Một task chạy trong executor một luồng lại gửi task thứ hai vào **cùng** executor và chờ `get()` của task thứ hai:

```java
// PHẢN VÍ DỤ (không chạy): single-thread executor có thể deadlock.
// Callable<String> outer = () -> {
//     Future<String> inner = sameSingleThreadPool.submit(() -> "ready");
//     return inner.get(); // worker đang chạy outer, inner chưa được chạy
// };
```

Worker duy nhất bận chờ `inner`, còn `inner` nằm trong queue chờ worker rảnh. Cần tránh phụ thuộc đồng bộ kiểu này: tổ chức task thành chuỗi xử lý, dùng executor độc lập và capacity thích hợp khi thật sự cần, hoặc áp dụng composition không chặn. **Callable trả kết quả không loại bỏ nguy cơ deadlock khi caller chờ sai chỗ.**

## 8. Hủy task và interrupt có nghĩa gì?

Ví dụ dùng `Future<String> future = executor.submit(callable)`:

- `future.cancel(false)`: yêu cầu hủy mà không cố interrupt nếu task đã chạy. Future có thể chuyển sang trạng thái canceled, nhưng side effect của task đang thực thi **có thể vẫn tiếp tục**.
- `future.cancel(true)`: nếu task đang chạy, Future có thể gửi interrupt tới thread đang thực thi. Task chỉ dừng kịp thời khi code hoặc các thao tác blocking hợp tác với interrupt.
- `future.get()` sau khi hủy thành công sẽ ném `CancellationException`.
- `future.isCancelled()` là trạng thái của Future; không bảo đảm mọi resource đã được đóng hoặc side effect đã được rollback.

Callable có thể kiểm tra interrupt trong tác vụ lặp:

```java
// Fragment: Runnable hoặc Callable đều có thể kiểm tra interrupt.
while (!Thread.currentThread().isInterrupted()) {
    performOneSmallStep();
}
```

Tác vụ gọi API blocking có thể nhận `InterruptedException`. Không nên nuốt ngoại lệ đó rồi tiếp tục chạy vô hạn; có thể truyền lại từ `Callable.call()`, hoặc khôi phục interrupt flag trong `Runnable.run()` trước khi thoát.

Shutdown executor và cancel Future là hai cơ chế khác nhau. `shutdown()` ngừng nhận việc mới và cho việc đã nhận hoàn tất; `shutdownNow()` cố interrupt worker và trả danh sách các tác vụ đang chờ chưa bắt đầu, **không bảo đảm** tác vụ đang chạy dừng ngay.

## 9. Callable từ Runnable: adapter và FutureTask

Nếu đang có `Runnable` thực hiện hành động nhưng API nhận `Callable<T>`, Java cung cấp adapter:

```java
// Fragment: Runnable đã tồn tại.
Runnable update = () -> System.out.println("Updated");
Callable<String> asCallable =
        java.util.concurrent.Executors.callable(update, "OK");
```

`asCallable.call()` chạy `update.run()` rồi trả `"OK"` nếu hoàn thành bình thường; nếu Runnable ném exception unchecked, exception vẫn được truyền qua lời gọi `call()`. Adapter **không biến side effect thành một kết quả tính toán**.

`FutureTask<V>` triển khai `RunnableFuture<V>`, tức vừa có thể được executor chạy như `Runnable`, vừa có thể được đọc như `Future<V>`:

```java
// Fragment, minh họa hai vai trò của FutureTask.
java.util.concurrent.FutureTask<Integer> task =
        new java.util.concurrent.FutureTask<>(() -> 6 * 7);

executor.execute(task); // execute nhận Runnable.
Integer result = task.get(); // FutureTask đồng thời là Future<Integer>.
```

Không cần tự tạo `FutureTask` trong trường hợp thông thường: `ExecutorService.submit(callable)` đã lo việc tạo Future và lập lịch. `FutureTask` hữu ích khi cần kiểm soát wrapper hoặc tích hợp một API chỉ nhận `Runnable`. Không gọi `run()` thủ công rồi lại gửi cùng task, trừ khi thật sự hiểu semantics về hoàn tất một lần của `FutureTask`.

Một constructor khác là `new FutureTask<>(Runnable, V result)`: về bản chất tương đương ý tưởng `submit(Runnable, result)`, với giá trị `result` được cố định trước.

## 10. Nhiều Callable: invokeAll và invokeAny

`ExecutorService` có hai API làm việc với một tập `Callable`:

| API | Ý nghĩa | Kết quả |
| --- | --- | --- |
| `invokeAll(Collection<? extends Callable<T>>)` | Chờ tất cả hoàn tất | Danh sách `Future<T>` theo thứ tự tương ứng với collection đầu vào; từng `get()` có thể trả giá trị hoặc báo lỗi |
| `invokeAll(tasks, timeout, unit)` | Chờ tối đa một thời hạn chung | Sau khi trở về, các task chưa hoàn tất được yêu cầu hủy |
| `invokeAny(Collection<? extends Callable<T>>)` | Chờ **một** task hoàn tất thành công | Trả giá trị `T` từ một task thành công; không hứa task nào thắng khi nhiều task cạnh tranh |
| `invokeAny(tasks, timeout, unit)` | Tương tự nhưng giới hạn thời gian | Có thể ném `TimeoutException` khi không có kết quả thành công đúng hạn |

`invokeAny` không phải “trả kết quả của task hoàn thành đầu tiên, kể cả khi nó lỗi”: nó tìm **kết quả thành công**; nếu một task thất bại, các task khác vẫn có thể thắng. Khi chọn được kết quả hoặc không còn lý do tiếp tục, API xử lý hủy các tác vụ chưa hoàn tất theo hợp đồng của nó. Không dùng `invokeAny` để giả định thứ tự chạy hay dựa vào tác dụng phụ của tất cả task.

Ví dụ chương trình độc lập `BatchCallableDemo.java`:

```java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BatchCallableDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> 2 * 3,
                    () -> 4 * 5,
                    () -> 6 * 7);
            List<Future<Integer>> futures = executor.invokeAll(tasks);
            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get();
            }
            System.out.println("Total = " + total);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac BatchCallableDemo.java
java BatchCallableDemo
```

Stdout:

```text
Total = 68
```

Dù có hai worker và ba task có thể hoàn thành theo thứ tự khác nhau, `invokeAll` trả Future tương ứng với thứ tự trong collection. Tổng `6 + 20 + 42 = 68` không phụ thuộc thứ tự thực thi.

## 11. Callable trong ScheduledExecutorService

Nhóm **Future & Scheduling** còn có các tác vụ được lên lịch. `ScheduledExecutorService` hỗ trợ:

- `schedule(Runnable command, long delay, TimeUnit unit)` trả `ScheduledFuture<?>`: chạy **một lần** sau độ trễ tối thiểu, không trả kết quả nghiệp vụ.
- `schedule(Callable<V> callable, long delay, TimeUnit unit)` trả `ScheduledFuture<V>`: chạy **một lần** sau độ trễ tối thiểu, cho phép nhận giá trị `V` từ `get()`.
- `scheduleAtFixedRate(...)` và `scheduleWithFixedDelay(...)` nhận `Runnable` để chạy lặp; một tác vụ lặp thành công thường không hoàn tất Future theo cách one-shot. **Không gọi `get()` rồi chờ mỗi chu kỳ trả về một giá trị**: Future của periodic task thường chỉ hoàn tất do hủy hoặc kết thúc bất thường.

Ví dụ một lịch hẹn tính toán `ScheduledCallableDemo.java`:

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledCallableDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        try {
            ScheduledFuture<Integer> future =
                    scheduler.schedule(() -> 7 * 6, 50, TimeUnit.MILLISECONDS);
            System.out.println("Scheduled result = " + future.get());
        } finally {
            scheduler.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ScheduledCallableDemo.java
java ScheduledCallableDemo
```

Stdout:

```text
Scheduled result = 42
```

Lưu ý: `50 ms` là **độ trễ yêu cầu trước khi có thể thực thi**, không phải bảo đảm thời điểm chạy chính xác 50 ms hoặc hoàn tất đúng hạn. Scheduling phụ thuộc tải và khả năng cấp worker. Không cần dùng framework nào để lên lịch Callable kiểu này.

## 12. Khi nào chọn Callable<T>, khi nào giữ Runnable?

**Chọn `Callable<T>` khi:**

1. Giá trị trả về là một phần hợp đồng của tác vụ (tổng tiền, kết quả phân tích, dữ liệu truy vấn, đối tượng đầu ra).
2. Muốn trình biên dịch kiểm tra kiểu trả về của kết quả `Future<T>`, tránh dùng biến toàn cục hoặc holder mutable.
3. Task cần gọi API có checked exception và muốn công bố lỗi thông qua `Future.get()`.
4. Cần dùng `invokeAll`, `invokeAny` hoặc `schedule(Callable, delay, unit)` để nhận kết quả.

**Chọn `Runnable` khi:**

1. Task mang mục đích hành động, không có giá trị tính toán cần trả (cập nhật cache, phát notification, log, cleanup).
2. API yêu cầu `Runnable`, như `new Thread(runnable)`, `Executor.execute(runnable)` hoặc lịch chạy định kỳ.
3. Chỉ cần biết task đã xong/thất bại qua `Future<?>` từ `submit(runnable)`.
4. Cần giá trị thành công cố định, có thể dùng `submit(runnable, result)` thay vì tạo một Callable chỉ để trả cùng một hằng số.

Không nên ép mọi `Runnable` thành `Callable<Void>`. Cách này hợp lệ khi có lý do thiết kế (trả `null`) nhưng làm code dài hơn mà không tự tạo thêm giá trị nghiệp vụ. Nếu task có giá trị thực, `Callable<Result>` thường biểu đạt ý định tốt nhất.

### Bảng quyết định nhanh

| Mục tiêu | Lời gọi ưu tiên | Giá trị caller nhận |
| --- | --- | --- |
| Chỉ giao task và không cần handle | `execute(Runnable)` | Không có Future |
| Giao task và muốn theo dõi hoàn tất/lỗi | `submit(Runnable)` | `Future<?>`, `get() == null` nếu thành công |
| Giao task hành động và muốn hằng số khi thành công | `submit(Runnable, T result)` | `Future<T>` trả giá trị cố định |
| Task tính và trả kết quả kiểu T | `submit(Callable<T>)` | `Future<T>` trả kết quả từ `call()` |
| Nhiều tác vụ cho kết quả | `invokeAll`/`invokeAny` với `Callable<T>` | Danh sách Future hoặc một giá trị thành công |
| Tính toán một lần sau độ trễ | `schedule(Callable<T>, delay, unit)` | `ScheduledFuture<T>` |

## 13. Lỗi thường gặp và cách tránh

| Sai lầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| Nghĩ rằng `Callable.call()` tự chạy thread khác | Đây là một phương thức bình thường nếu gọi trực tiếp | Dùng `ExecutorService.submit(callable)` hoặc scheduler |
| Muốn lấy kết quả từ `Runnable.run()` | `run()` trả `void` | Đổi thành `Callable<Result>` hoặc dùng `submit(runnable, fixedValue)` nếu chỉ cần giá trị cố định |
| Gọi `submit(callable)` rồi chờ trả về giá trị T trực tiếp | `submit` trả về `Future<T>`, không trả T | Dùng `future.get()` ở vị trí được phép block |
| Không giữ Future sau `submit` | Exception của task có thể chỉ được lưu trong Future và không được quan sát | Giữ handle, đọc kết quả, theo dõi và log lỗi |
| Coi `isDone()` là thành công | Hủy và lỗi cũng được xem là done | Gọi `get()`, xử lý `ExecutionException`/`CancellationException` |
| Bắt `ExecutionException` và bỏ qua `getCause()` | Mất nguyên nhân gây lỗi | Dùng nguyên nhân để chẩn đoán hoặc chuyển lỗi có ngữ cảnh |
| Dùng `get()` không timeout trên đường xử lý nhạy độ trễ | Caller có thể chờ lâu hoặc vô hạn | Chọn timeout/deadline, thiết kế callback/composition khi cần |
| Nghĩ rằng `get(timeout)` đã dừng task | Timeout chỉ giới hạn việc **chờ** | Nếu phù hợp, yêu cầu `cancel(true)` và hỗ trợ interrupt |
| Cho worker chờ Future của task queued cùng single-thread pool | Có thể bị thread starvation deadlock | Tổ chức lại phụ thuộc và tránh blocking bên trong worker |
| Dùng `DiscardPolicy` rồi `submit` và `get()` | Task bị bỏ âm thầm, Future có thể không bao giờ hoàn tất | Ưu tiên handler báo lỗi rõ và admission control đúng |
| Dùng `Future.get()` trên lịch chạy định kỳ | Periodic Future không có kết quả mỗi chu kỳ | Báo cáo kết quả mỗi tick qua cơ chế riêng, hủy đúng vòng đời |
| Nhầm `Future.get()` với khóa bảo vệ shared state | Nó tạo quan hệ memory visibility theo hợp đồng, không tránh data race giữa nhiều writer | Dùng thiết kế immutable/đồng bộ khi có chia sẻ dữ liệu |

## 14. Tự kiểm tra

1. **`Runnable` có thể gửi bằng `submit` không?** Có. Caller nhận `Future<?>`; nếu chạy thành công, `get()` trả `null`.
2. **`submit(Runnable, "OK")` khác `submit(Callable<String>)` ở đâu?** Cách đầu trả giá trị cố định do caller cung cấp sau khi Runnable thành công; cách sau trả giá trị thực do `call()` tính.
3. **Một `Callable<Integer>` ném `IOException`; điều gì xảy ra khi `Future.get()`?** `get()` ném `ExecutionException`, `getCause()` là `IOException` tương ứng.
4. **`get(timeout)` ném `TimeoutException` có dừng task không?** Không. Đó chỉ là timeout của thao tác chờ; cần chính sách cancel riêng.
5. **`isDone() == true` đã chứng minh task thành công chưa?** Chưa; task có thể lỗi hoặc bị hủy.
6. **`schedule(Callable<T>, delay, unit)` có trả kết quả không?** Có, thông qua `ScheduledFuture<T>`; độ trễ yêu cầu không bảo đảm thời điểm thực thi tuyệt đối.
7. **Khi nào dùng `execute(Runnable)` thay `submit(Runnable)`?** Khi không cần handle Future và muốn giao việc theo hợp đồng `Executor`; nếu cần đợi hoàn tất hoặc đọc lỗi, `submit` phù hợp hơn.
8. **Vì sao đừng gọi `Future.get()` của một task queued ngay trong worker duy nhất của cùng executor?** Worker sẽ bị chặn trong khi task kia chưa thể được chạy.

## Tài liệu tham khảo

- [Java SE 17: Callable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Callable.html)
- [Java SE 17: Runnable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runnable.html)
- [Java SE 17: ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17: Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17: FutureTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html)
- [Java SE 17: ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-005">ExecutionException và truy nguyên lỗi task</a>

<details>
<summary>Xem chi tiết</summary>

# ExecutionException và truy nguyên lỗi task trong Java Concurrency

## 1. Vì sao task thất bại nhưng thread gọi không nhận exception ngay?

Khi gọi một phương thức Java đồng bộ, exception có thể đi ngược lên call stack của **chính thread gọi**:

```java
String value = parseData(input); // Có thể ném exception ngay tại đây.
```

Nhưng với `ExecutorService`, task chạy ở **worker thread khác**. Thread nộp công việc và worker không chia sẻ call stack. Nếu task ném exception, exception đó **không tự động nhảy sang** thread đã gọi `submit()`.

Java cung cấp `Future` để giữ **trạng thái hoàn thành** và kết quả của tác vụ. Khi thread khác gọi `Future.get()`, API có thể **bọc lỗi mà task đã ném** trong `ExecutionException`, đưa thông tin lỗi trở lại cho caller.

```text
Thread gọi submit                  Worker thread
------------------                 ------------------
executor.submit(callable)  ───────> callable.call()
          │                             │
          │                         throw IOException
          │                             │
          ▼                             ▼
Future<T>                       Future lưu thất bại
          │                             │
future.get()  <────────────────────────┘
          │
          ▼
throw ExecutionException
       └── getCause() -> IOException gốc
```

**ExecutionException là lớp bọc báo task thực thi thất bại**, không phải tên của mọi lỗi concurrency. Nó khác `InterruptedException` khi thread đang chờ bị ngắt, `TimeoutException` khi chờ quá thời hạn, và `CancellationException` khi future đã bị hủy.

Bài học thuộc **Future & Scheduling**, phạm vi **Java concurrency nói chung**, không phụ thuộc framework.

## 2. Ba vai trò: caller, executor và Future

Có thể chia trách nhiệm như sau:

1. **Caller:** tạo/nộp task; giữ `Future`; quyết định khi nào cần đợi và cách xử lý thành công hoặc thất bại.
2. **Executor:** nhận task và sắp xếp worker thực thi; không nhất thiết trả lỗi task trực tiếp tại `submit()`.
3. **Future:** cho phép theo dõi trạng thái hoàn thành, nhận kết quả khi thành công hoặc truy nguyên lỗi khi thất bại/hủy.

`submit(Callable<T>)` trả `Future<T>`. `submit(Runnable)` trả `Future<?>` mà `get()` bình thường cho `null` nếu task hoàn thành thành công. Riêng `submit(Runnable, T result)` có thể trả một giá trị được caller cung cấp sau khi Runnable hoàn thành bình thường.

### Các ngoại lệ quan trọng của Future.get()

| Trạng thái khi gọi `Future.get()` | Hành vi phổ biến |
| --- | --- |
| Task thành công | Trả giá trị (có thể `null` với Runnable) |
| Task ném exception hoặc error không được xử lý | Ném `ExecutionException`, `getCause()` là lỗi được lưu |
| Task/Future đã bị hủy | Ném `CancellationException` (unchecked) |
| Thread **đang chờ** bị interrupt | Ném `InterruptedException` |
| `get(timeout, unit)` hết thời hạn | Ném `TimeoutException` |
| Task vẫn chạy và không có timeout | `get()` có thể đợi vô hạn |

**Không nhầm:** `Future.get()` không tự gửi interrupt chỉ vì caller đợi lâu. `TimeoutException` cũng không tự hủy task: task có thể tiếp tục chạy sau khi caller ngừng đợi.

Lời gọi `get()` có thể ném `InterruptedException` ngay cả khi **task hoàn toàn bình thường**, vì người bị ngắt là thread đang **chờ kết quả**. Đó không phải bằng chứng worker thất bại.

## 3. Ví dụ Java 17+: truy nguyên IOException bên trong ExecutionException

Tạo tệp `ExecutionExceptionBasicsDemo.java`:

```java
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutionExceptionBasicsDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> result = executor.submit(() -> {
                throw new IOException("cannot read report.csv");
            });

            try {
                System.out.println(result.get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                System.out.println("wrapper="
                        + e.getClass().getSimpleName());
                System.out.println("cause="
                        + cause.getClass().getSimpleName());
                System.out.println("message=" + cause.getMessage());
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac ExecutionExceptionBasicsDemo.java
java ExecutionExceptionBasicsDemo
```

Kết quả khi không có yêu cầu interrupt bên ngoài:

```text
wrapper=ExecutionException
cause=IOException
message=cannot read report.csv
```

Diễn giải:

- `submit()` trả `Future<String>`; lambda là `Callable` vì có kiểu kết quả của task và ném `IOException`.
- Worker thực thi `call()` và ném `IOException`.
- Future ghi lại trạng thái thất bại. Caller gọi `result.get()` thì nhận `ExecutionException`.
- `getCause()` trả `IOException` gốc để caller nhận diện vấn đề đọc file.
- `finally` yêu cầu đóng executor dù `get()` ném hoặc xử lý lỗi; `awaitTermination` kiểm tra có kết thúc trong thời hạn hay không.

**Không chỉ xem `e.getMessage()` của wrapper**. Nội dung hữu ích về domain thường nằm ở `e.getCause()` và toàn bộ cause chain. Trong hệ thống thật, vẫn nên giữ stack trace gốc và thông tin task để tìm dòng code đã gây lỗi.

## 4. `getCause()`, cause chain và nguyên nhân gốc

Một lỗi có thể được bọc nhiều tầng:

```text
ExecutionException
  └── IllegalStateException("processing failed")
        └── IllegalArgumentException("invalid orderId")
```

Thông thường:

- **Wrapper:** exception báo lỗi vượt qua ranh giới bất đồng bộ, chẳng hạn `ExecutionException`.
- **Cause trực tiếp:** throwable mà task ném hoặc throwable được một tầng nghiệp vụ bọc lại.
- **Root cause:** lỗi nằm sâu nhất trong cause chain, nếu có một chuỗi `getCause()` liên tục.
- **Suppressed exceptions:** các lỗi khác được gắn bằng `addSuppressed()`, thường thấy khi tài nguyên `close()` cũng thất bại; chúng **không thuộc cause chain tuyến tính**.

### Ví dụ 2: xem từng tầng của chuỗi lỗi

Tạo tệp `ExecutionCauseChainDemo.java`:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutionCauseChainDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Void> task = executor.submit(() -> {
                try {
                    throw new IllegalArgumentException("invalid orderId");
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("processing failed", e);
                }
            });

            try {
                task.get();
            } catch (ExecutionException e) {
                Throwable direct = e.getCause();
                Throwable root = direct.getCause();

                System.out.println("direct="
                        + direct.getClass().getSimpleName());
                System.out.println("direct message=" + direct.getMessage());
                System.out.println("root="
                        + root.getClass().getSimpleName());
                System.out.println("root message=" + root.getMessage());
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

```bash
javac ExecutionCauseChainDemo.java
java ExecutionCauseChainDemo
```

Kết quả khi không bị interrupt ngoài ý muốn:

```text
direct=IllegalStateException
direct message=processing failed
root=IllegalArgumentException
root message=invalid orderId
```

Trong ví dụ, `IllegalStateException` là ngữ cảnh nghiệp vụ **gần nhất** do task ném, còn `IllegalArgumentException` là lỗi thấp hơn được nối bằng constructor `new IllegalStateException(message, cause)`.

**Không phải lúc nào “root cause” cũng đủ để quyết định xử lý.** Có khi tầng cao thêm thông tin quan trọng (mã công việc, bước xử lý, điều kiện retry). Đừng bỏ `IllegalStateException("processing failed")` chỉ vì đã tìm được `IllegalArgumentException`.

### Đi theo chuỗi cause một cách an toàn

Một helper minh họa:

```java
static Throwable deepestCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null
            && current.getCause() != current) {
        current = current.getCause();
    }
    return current;
}
```

Đây là **đoạn phương thức minh họa**, không có `main`. Trong code chẩn đoán tổng quát, có thể cần bảo vệ trước chuỗi cause tạo thành vòng bất thường (ví dụ qua throwable tùy chỉnh), tránh vòng lặp vô hạn. `printStackTrace()` vốn có cơ chế trình bày nguyên nhân và suppressed exceptions theo cấu trúc; thường hiệu quả hơn việc tự ghép chỉ root message.

## 5. Giữ stack trace thay vì chỉ in một dòng message

Khi một worker thất bại, muốn truy nguyên cần biết **loại lỗi, vị trí, ngữ cảnh và đường dẫn lời gọi**. Thao tác hữu ích:

```java
try {
    future.get();
} catch (ExecutionException e) {
    Throwable failure = e.getCause();
    logger.error("Task {} failed", taskId, failure);
}
```

Đây là **pseudocode tích hợp logging**, `future`, `taskId` và `logger` được ứng dụng định nghĩa. Một logger phù hợp có thể lưu toàn bộ stack trace của `failure`; còn truyền `e` nguyên vẹn cũng giữ cause chain.

Với Java thuần, có thể dùng:

```java
failure.printStackTrace(System.err);
```

Nhưng output stack trace phụ thuộc tên tệp, dòng code, lambda, JDK và cách biên dịch, nên **không nên bịa output cố định**. Khi muốn demo chính xác, in `getClass().getSimpleName()` và `getMessage()`; khi debug thực tế, giữ **toàn bộ throwable**, không chỉ chuỗi ký tự.

Các gợi ý log:

- Ghi loại task, task ID hoặc correlation ID, thời điểm nộp/chạy, tên worker khi có ích.
- Ghi wrapper và cause chain, đặc biệt lỗi gốc cùng stack trace.
- Dùng mức độ log tương xứng với loại lỗi dự kiến hay bất thường.
- Không log dữ liệu nhạy cảm (token, mật khẩu, nội dung riêng tư) chỉ để có thêm ngữ cảnh.
- Không tự động retry vì thấy một exception mà chưa xét side effect task đã thực hiện.

## 6. `submit()` khác `execute()` trong cách quan sát exception

`ExecutorService` kế thừa `execute(Runnable)` và bổ sung `submit(...)`.

**`submit()`:** trả `Future`. Trong các executor tiêu chuẩn như `ThreadPoolExecutor`, task thường được bọc trong một `RunnableFuture` (ví dụ `FutureTask`) giữ kết quả hoặc throwable. Nếu Runnable/Callable thất bại, exception được lưu để caller lấy ra qua `Future.get()`; lỗi đó không nhất thiết xuất hiện trực tiếp ở `UncaughtExceptionHandler` của worker.

**`execute()`:** không trả Future. Nếu một Runnable ném exception không bắt, worker thread có thể kết thúc bất thường và exception đi qua cơ chế uncaught exception của thread/runtime, tùy executor. Với `ThreadPoolExecutor`, lỗi từ task được truyền ra khỏi `run()` và worker có thể được thay thế nếu pool còn chạy. Không có `Future.get()` để caller tự lấy lỗi.

Bảng so sánh:

| Tiêu chí | `execute(Runnable)` | `submit(Runnable/Callable)` |
| --- | --- | --- |
| Trả về | `void` | `Future<?>` hoặc `Future<T>` |
| Caller lấy kết quả | Không có Future của lời gọi | `get()` |
| Caller truy exception task | Cần logging, handler hoặc giao thức báo lỗi khác | `get()` ném `ExecutionException` |
| Hủy task riêng | Phải có cơ chế/handle khác | `Future.cancel(...)` |
| Rủi ro khi bỏ qua | Task thất bại không được báo tới caller | Future lưu lỗi nhưng **nếu không gọi `get()` thì caller có thể không biết** |

**Không nên nói “submit nuốt exception” theo nghĩa xóa mất lỗi.** Nó **giữ throwable trong Future** và chuyển cho caller qua `get()`. Nếu code nộp rồi bỏ quên `Future`, lỗi dễ trở nên không được quan sát — đó là vấn đề thiết kế giám sát kết quả.

### Phản ví dụ: nộp rồi bỏ Future

```java
executor.submit(() -> {
    throw new IllegalStateException("failed");
});
// Bỏ qua Future: caller không đọc trạng thái task.
```

Đây là đoạn minh họa. Không nên xem “không có exception ở thread caller” là bằng chứng task chạy thành công.

Một thiết kế tốt giữ `Future` ở tầng chịu trách nhiệm kiểm tra outcome, hoặc đặt đường báo lỗi rõ ràng vào callback/channel/monitoring của ứng dụng.

## 7. Ví dụ 3: nhiều Future, không bỏ sót lỗi một task

Một lỗi thường gặp là nộp hàng chục task rồi chỉ gọi `get()` trên một task. Các Future còn lại có thể thất bại mà không được ghi nhận.

Tạo tệp `MultipleTaskFailuresDemo.java`:

```java
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MultipleTaskFailuresDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> good = executor.submit(() -> 7);
            Future<Integer> bad = executor.submit(() -> {
                throw new IllegalStateException("bad order");
            });

            List<Future<Integer>> futures = List.of(good, bad);
            for (int i = 0; i < futures.size(); i++) {
                try {
                    System.out.println("task " + i
                            + " result=" + futures.get(i).get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    System.out.println("task " + i + " failed="
                            + cause.getClass().getSimpleName()
                            + ": " + cause.getMessage());
                }
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

```bash
javac MultipleTaskFailuresDemo.java
java MultipleTaskFailuresDemo
```

Kết quả khi không bị interrupt ngoài ý muốn:

```text
task 0 result=7
task 1 failed=IllegalStateException: bad order
```

Thứ tự hoàn thành trong worker **không nhất thiết giống thứ tự nộp**, nhưng caller duyệt futures theo index `0` rồi `1`; vì vậy thứ tự dòng log được xác định. `get()` đợi từng Future hoàn thành rồi đọc result/failure.

**Giới hạn của cách duyệt tuần tự:** nếu Future đầu chậm, caller có thể chờ nó dù một Future sau đã thất bại. Khi cần xử lý kết quả **theo thứ tự hoàn thành** chứ không theo thứ tự nộp, có thể dùng `ExecutorCompletionService`.

## 8. `ExecutorCompletionService` để quan sát kết quả theo thứ tự hoàn thành

`ExecutorCompletionService<V>` nhận các `Callable<V>` và đưa Future đã hoàn thành vào một completion queue. Caller gọi `take()` để nhận Future của task **đã kết thúc** (thành công, thất bại hoặc bị hủy) rồi `get()` để phân biệt outcome.

Mẫu minh họa:

```java
ExecutorCompletionService<String> completed =
        new ExecutorCompletionService<>(executor);

completed.submit(() -> fetchA());
completed.submit(() -> fetchB());

for (int i = 0; i < 2; i++) {
    Future<String> future = completed.take();
    try {
        String value = future.get();
        consume(value);
    } catch (ExecutionException e) {
        reportFailure(e.getCause());
    }
}
```

Đây là đoạn minh họa trong ngữ cảnh có import `java.util.concurrent.*` và định nghĩa `executor`, `fetchA`, `fetchB`, `consume` và `reportFailure`. Thứ tự nhận có thể thay đổi giữa các lần chạy, nên không in stdout cố định.

Ưu điểm là một task nhanh không bị việc caller chờ một task chậm **đứng trước trong danh sách** cản việc quan sát. Tuy nhiên, code vẫn phải:

- Biết số lượng task đã nộp hoặc có protocol dừng.
- Xử lý cả `CancellationException` và `InterruptedException` phù hợp.
- Có chính sách cancel các task còn lại nếu lỗi nghiêm trọng.
- Quản lý vòng đời executor.
- Không để completion queue/tập Future tích lũy vô hạn nếu producer nhanh hơn consumer.

`ExecutorCompletionService` giúp **thu thập outcome**, không tự biến exception task thành kết quả thành công.

## 9. TimeoutException, InterruptedException và CancellationException khác ExecutionException

Một trong những sai lầm gây nhầm lẫn chẩn đoán nhất là gom mọi failure của `get()` thành “task throw exception”.

### `TimeoutException`

`future.get(100, TimeUnit.MILLISECONDS)` ném `TimeoutException` nếu thời hạn chờ hết mà Future chưa hoàn tất. Điều đó không có nghĩa task bị lỗi hay đã bị hủy. Task có thể tiếp tục sau timeout.

### `InterruptedException`

Thread **đang đợi `get()`** bị interrupt. Task có thể vẫn chạy bình thường. Nếu không thể propagate, hãy khôi phục cờ và rời workflow ở điểm an toàn. Không nhầm ngắt thread caller với exception gốc do worker ném.

### `CancellationException`

Future đã bị hủy, `get()` ném `CancellationException`. Một task đã bắt đầu có thể vẫn đang cleanup hoặc vẫn chạy nếu không hợp tác với interrupt; `isCancelled()` không tự chứng minh mọi việc dọn dẹp đã hoàn tất.

### `ExecutionException`

Task hoàn thành bất thường vì ném `Throwable` trong quá trình thực thi. `getCause()` chứa lỗi task đã ném (hoặc lỗi được tầng task bọc lại). Không nên xử lý theo kiểu timeout hoặc cancel nếu không có căn cứ.

### Bảng gợi ý phản ứng

| Exception từ `get()` | Ý nghĩa | Hướng xử lý hợp lý |
| --- | --- | --- |
| `ExecutionException` | Task thất bại | Truy nguyên `getCause()`, log stack trace, phân loại retry/fail |
| `TimeoutException` | Chờ kết quả quá lâu | Quyết định chờ thêm, cancel hoặc trả timeout cho caller |
| `InterruptedException` | Caller đang chờ bị ngắt | Propagate hoặc khôi phục interrupt, dọn workflow |
| `CancellationException` | Future bị cancel | Xác nhận trạng thái hủy, xử lý cleanup/outcome nếu cần |

## 10. Ví dụ 4: hết thời gian chờ không đồng nghĩa task thất bại

Tạo tệp `FutureTimeoutDemo.java`:

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
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> task = executor.submit(() -> {
                started.countDown();
                release.await();
                return "OK";
            });

            started.await();
            try {
                task.get(50, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("caller timed out");
            }

            release.countDown();
            System.out.println("later result=" + task.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
```

```bash
javac FutureTimeoutDemo.java
java FutureTimeoutDemo
```

Kết quả khi không có interrupt từ bên ngoài:

```text
caller timed out
later result=OK
```

Lý do kết quả xác định:

- Task gửi `started` rồi đợi `release`, nên **chưa thể hoàn tất** khi caller thử `get(50ms)`.
- Timeout làm `get` ném exception **trong caller**, nhưng **không hủy** task.
- Main mở `release`; task tiếp tục và trả `"OK"`.
- Lần `get()` sau nhận kết quả thành công.

`finally` mở `release` cả khi có exception, tránh worker bị kẹt do caller thoát sớm. `shutdownNow()` trong ví dụ chỉ để dọn executor; trong hệ thống cần graceful shutdown có thể dùng `shutdown()` rồi fallback theo chính sách riêng.

## 11. `Future.get()` có bảo đảm bộ nhớ gì?

`Future` không chỉ lưu kết quả. API Java concurrency có quy tắc memory consistency quan trọng: **các hành động do asynchronous computation thực hiện xảy ra trước (happens-before) các hành động sau khi thread khác lấy kết quả qua `Future.get()`** theo hợp đồng Future.

Ví dụ nếu task ghi một object kết quả trước khi hoàn thành, caller sau khi `get()` trả về thành công có quan hệ bộ nhớ để nhìn thấy những ghi được công bố qua đường này.

Nhưng tránh suy rộng:

- `get()` không tự làm một object mutable tiếp tục bị nhiều thread sửa trở nên thread-safe.
- `get()` không biến hai lần đọc state từ hai phiên bản khác nhau thành một atomic snapshot.
- Nếu task thất bại giữa nhiều cập nhật không có transaction, `ExecutionException` không rollback các cập nhật đã xảy ra.
- Nếu caller không đợi/get và đọc shared state riêng theo đường thiếu đồng bộ, cần giao thức memory visibility khác.

Với các thiết kế mới, ưu tiên tạo immutable result/DTO hoàn chỉnh trong task rồi trả qua Future. Reader không sửa result và không để worker tiếp tục sửa result sau khi trả.

## 12. `CompletableFuture`: get() và join() bọc lỗi khác nhau

`CompletableFuture<T>` vừa là `Future<T>` vừa hỗ trợ composition bất đồng bộ. Hai phương thức chờ phổ biến:

- `future.get()`: khai báo checked exceptions `InterruptedException` và `ExecutionException` (khi computation thất bại).
- `future.join()`: không khai báo checked exception; khi completion thất bại thường ném **`CompletionException`** (unchecked).
- Nếu Future bị hủy, `get()` và `join()` có thể ném `CancellationException` trực tiếp.
- Các callback như `exceptionally`, `handle`, `whenComplete` có thể xử lý hoặc quan sát thất bại mà không gọi `get` tại mỗi tầng.

Lỗi bên trong `CompletionException` cũng thường nằm tại `getCause()`. Trong những chain phức tạp, `CompletionException`/`ExecutionException` có thể được bọc ở nhiều tầng; đừng giả định cause chỉ có đúng một cấp.

### Ví dụ 5: cùng một lỗi qua get và join

Tạo tệp `CompletableFailureDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class CompletableFailureDemo {
    public static void main(String[] args) throws InterruptedException {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        future.completeExceptionally(
                new IllegalArgumentException("bad input"));

        try {
            future.get();
        } catch (ExecutionException e) {
            System.out.println("get wrapper="
                    + e.getClass().getSimpleName());
            System.out.println("get cause="
                    + e.getCause().getClass().getSimpleName());
        }

        try {
            future.join();
        } catch (CompletionException e) {
            System.out.println("join wrapper="
                    + e.getClass().getSimpleName());
            System.out.println("join cause="
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

```bash
javac CompletableFailureDemo.java
java CompletableFailureDemo
```

Kết quả:

```text
get wrapper=ExecutionException
get cause=IllegalArgumentException
join wrapper=CompletionException
join cause=IllegalArgumentException
```

Không có thread race trong ví dụ vì Future đã được hoàn thành exceptionally trước khi gọi hai phương thức. Ví dụ chỉ minh họa **hai loại wrapper** của API chờ khác nhau.

Với các stage asynchronous phức tạp, lỗi có thể được truyền qua `CompletionException` ở giữa chain, và `get()` vẫn bọc theo contract của Future. Cách truy nguyên là xem **cause chain đầy đủ**, không cứng nhắc kỳ vọng một lớp bọc duy nhất.

## 13. Dùng exceptionally, handle, whenComplete đúng mục đích

Khi dùng `CompletableFuture`, có thể xử lý lỗi bằng pipeline thay vì đợi và catch tại mọi nơi.

```java
CompletableFuture<Integer> recovered = operation
        .exceptionally(error -> {
            report(error);
            return 0;
        });
```

Đây là đoạn minh họa, `operation` là `CompletableFuture<Integer>` và `report` do ứng dụng định nghĩa. `exceptionally` có thể **biến một completion thất bại thành kết quả mặc định**. Cách này chỉ phù hợp khi fallback `0` là hợp đồng nghiệp vụ hợp lệ — nếu không, sẽ che lỗi và tạo dữ liệu sai.

`handle((value, error) -> ...)` nhận cả trạng thái thành công và thất bại để tạo một kết quả mới. `whenComplete((value, error) -> ...)` chủ yếu dùng để **quan sát** outcome và thường giữ nguyên kết quả/exception ban đầu nếu callback không tự ném lỗi, không được hiểu là cơ chế recovery mặc định.

### Ví dụ 6: phân loại lỗi rồi fallback có chủ đích

Tạo tệp `CompletableRecoveryDemo.java`:

```java
import java.util.concurrent.CompletableFuture;

public class CompletableRecoveryDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> source = new CompletableFuture<>();
        source.completeExceptionally(
                new IllegalArgumentException("invalid quantity"));

        int result = source.exceptionally(error -> {
            Throwable cause = error.getCause() != null
                    ? error.getCause() : error;
            System.out.println("handled="
                    + cause.getClass().getSimpleName());
            return -1;
        }).join();

        System.out.println("result=" + result);
    }
}
```

```bash
javac CompletableRecoveryDemo.java
java CompletableRecoveryDemo
```

Kết quả:

```text
handled=IllegalArgumentException
result=-1
```

`-1` chỉ là **fallback minh họa**, không phải lời khuyên dùng `-1` cho mọi lỗi. Một hệ thống thật có thể trả một kiểu `Result<T>` chứa success/error, propagate throwable hoặc retry theo chính sách. Quan trọng là đừng gọi recovery một cách mù quáng rồi bỏ qua failure gốc.

### Cẩn thận khi dùng exceptionally với side effect

Callback phục hồi có thể chạy trên thread khác hoặc trong thread gọi completion, tùy cách stage được xây dựng. Nếu callback ghi log, ghi dữ liệu shared hoặc gọi API khác, vẫn cần hiểu thread safety của thao tác đó. `CompletableFuture` không loại trừ yêu cầu đồng bộ trên các object mutable ngoài nó.

## 14. ScheduledExecutorService: lỗi trong tác vụ định kỳ

`ScheduledExecutorService` cũng có `ScheduledFuture<?>`. Với tác vụ được lên lịch một lần, `future.get()` có thể trả kết quả hoặc `ExecutionException` nếu task ném lỗi.

Đối với tác vụ định kỳ bằng `scheduleAtFixedRate` hoặc `scheduleWithFixedDelay`, có điểm khác biệt rất quan trọng:

> Nếu một lần chạy của periodic task ném exception không được xử lý, các lần chạy tiếp theo của **cùng scheduled task** bị ngăn theo hợp đồng `ScheduledThreadPoolExecutor`/`ScheduledExecutorService`. Future của periodic task có thể hoàn thành bất thường.

Vì một periodic task bình thường có thể chạy lặp cho đến khi bị hủy, `get()` trên future của nó thường **không trả về thành công** khi chuỗi lặp vẫn đang hoạt động. Nó có thể hoàn thành bất thường do exception hoặc hủy.

### Ví dụ 7: truy nguyên lỗi periodic task

Tạo tệp `ScheduledTaskFailureDemo.java`:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledTaskFailureDemo {
    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        AtomicInteger runs = new AtomicInteger();

        try {
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                    () -> {
                        int n = runs.incrementAndGet();
                        throw new IllegalStateException("failed at run " + n);
                    },
                    0, 1, TimeUnit.SECONDS);

            try {
                future.get();
            } catch (ExecutionException e) {
                System.out.println("cause="
                        + e.getCause().getClass().getSimpleName());
                System.out.println("message=" + e.getCause().getMessage());
            }

            System.out.println("runs=" + runs.get());
        } finally {
            scheduler.shutdownNow();
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
```

```bash
javac ScheduledTaskFailureDemo.java
java ScheduledTaskFailureDemo
```

Kết quả khi không có interrupt ngoài ý muốn:

```text
cause=IllegalStateException
message=failed at run 1
runs=1
```

Lần chạy đầu tăng `runs` thành `1` rồi ném `IllegalStateException`; periodic future hoàn thành thất bại, các lượt tiếp theo không được tiếp tục. Caller gọi `get()` nhận `ExecutionException`, truy nguyên qua `getCause()`.

**Rủi ro thường gặp:** một scheduled task bị lỗi có thể **ngừng chạy định kỳ** trong khi những thành phần còn lại của ứng dụng vẫn sống. Nếu không giữ `ScheduledFuture` hoặc có đường báo lỗi, hệ thống có thể âm thầm ngừng công việc theo lịch.

Nếu muốn một task tiếp tục sau một lỗi nghiệp vụ có thể phục hồi, task có thể **bắt và xử lý chính lỗi đó** ở ranh giới mỗi lượt. Nhưng tuyệt đối không nuốt mọi `Throwable` vô điều kiện: lỗi nghiêm trọng, trạng thái không hợp lệ hoặc yêu cầu shutdown có thể đòi dừng, báo lỗi hoặc khởi động lại thành phần theo chính sách.

## 15. invokeAll và invokeAny: lỗi nằm ở đâu?

`ExecutorService.invokeAll(tasks)` thường trả về một danh sách `Future` tương ứng cho các callable sau khi chúng hoàn tất theo hợp đồng. **Trả về danh sách không có nghĩa mọi callable thành công**. Cần duyệt và gọi `get()` trên từng Future để kiểm tra lỗi.

```java
List<Future<Integer>> futures = executor.invokeAll(tasks);
for (Future<Integer> future : futures) {
    try {
        consume(future.get());
    } catch (ExecutionException e) {
        reportFailure(e.getCause());
    }
}
```

Đây là đoạn minh họa có import `java.util.concurrent.*` và `java.util.List`; `tasks`, `consume`, `reportFailure` là thành phần của ứng dụng.

Với `invokeAll(timeout, unit)`, các task chưa hoàn thành khi hết hạn có thể bị cancel theo contract; `get()` của chúng có thể ném `CancellationException`, **không nhất thiết là `ExecutionException`**.

`invokeAny(tasks)` có mục tiêu khác: trả kết quả của **một task hoàn thành thành công**. Nếu không có task nào hoàn thành thành công (và không bị các tình huống interrupt/timeout khác chi phối), nó có thể ném `ExecutionException`. Việc một task trong nhóm thất bại **không nhất thiết làm `invokeAny` thất bại** nếu một task khác thành công.

Không nhầm rằng `invokeAll` = “mọi kết quả đều thành công” hoặc `invokeAny` = “kết quả task hoàn thành đầu tiên bất kể thất bại”. Luôn đọc hợp đồng từng API.

## 16. Exception của task có thể là RuntimeException, checked Exception hoặc Error

Một `Callable<T>` khai báo `T call() throws Exception`, nên có thể ném checked exception như `IOException`. Một `Runnable` không được khai báo ném checked exception từ `run()`, nhưng vẫn có thể ném `RuntimeException` hoặc `Error` không bắt.

Khi được nộp qua `submit()` và task thất bại, `ExecutionException.getCause()` có thể là:

- `IOException` do `Callable` ném.
- `IllegalArgumentException`, `IllegalStateException`, `NullPointerException` hoặc một runtime exception khác.
- `Error` như `AssertionError`, `OutOfMemoryError` hoặc những lỗi nghiêm trọng khác.

Không nên viết `catch (Exception e)` rồi mặc định mọi lỗi đều an toàn để retry. `Error` thường báo một vấn đề nghiêm trọng cần xử lý ở mức vận hành hơn là “thử lại vài lần”. `ExecutionException` là checked exception, nhưng **cause của nó là `Throwable`**; code phân loại cần biết rằng nó có thể không phải subclass của `Exception`.

Không bịa quy tắc rằng mọi `Error` bắt buộc làm JVM chết ngay: `FutureTask` có thể ghi lại `Error` như outcome của task. Tuy vậy, việc tiếp tục ứng dụng sau lỗi nghiêm trọng có an toàn hay không tùy nguyên nhân cụ thể và trạng thái JVM.

## 17. Truy nguyên lỗi mà không làm mất ngữ cảnh

Một chiến lược tốt là phân loại dựa trên **cause** và giữ **wrapper/context** trong log hoặc exception của tầng cao:

```java
try {
    return future.get();
} catch (ExecutionException e) {
    Throwable cause = e.getCause();
    throw new TaskFailedException(taskId, cause);
}
```

Đây là đoạn minh họa, `TaskFailedException` và `taskId` do ứng dụng định nghĩa. Một exception mới có cause gốc giúp tầng trên có thêm task ID mà không làm mất đường truy nguyên.

Khi viết wrapper của riêng mình:

```java
throw new IllegalStateException("load config failed", original);
```

là tốt hơn:

```java
throw new IllegalStateException("load config failed");
```

nếu muốn giữ lỗi nguyên nhân. Đoạn thứ hai làm mất cause chain trừ khi có cơ chế khác lưu lại.

Không nên chỉ lấy `cause.getMessage()` rồi xây một exception không có cause. Message không đủ phân biệt các loại lỗi và không giữ stack trace. Với các lỗi đa tầng, giữ cả suppressed exceptions khi ghi log đầy đủ.

### Có nên unwrap rồi ném lại cause?

Có khi API của tầng trên muốn ném lại đúng `IOException` hoặc một exception cụ thể. Nhưng `ExecutionException.getCause()` có kiểu `Throwable`; không thể ném tùy tiện ở method chỉ khai báo checked exceptions hạn chế. Hãy **phân loại** có chủ đích hoặc dùng wrapper domain có cause:

```java
Throwable cause = e.getCause();
if (cause instanceof java.io.IOException io) {
    throw io;
}
throw new IllegalStateException("task failed", cause);
```

Đây là đoạn minh họa bên trong method có thể khai báo `throws IOException`; `e` là `ExecutionException` đã bắt. Pattern matching `instanceof` được hỗ trợ trên Java 17.

Đừng catch mọi `Throwable` rồi biến thành một kết quả “OK” hoặc lặp lại vô hạn. Các lỗi như `OutOfMemoryError` có thể làm quá trình recovery không đáng tin.

## 18. Phân loại lỗi để retry an toàn

Một task thất bại không đồng nghĩa retry luôn đúng. Hãy xem **tính lặp lại được** và mức độ thành công một phần:

| Loại tình huống | Retry ngay có hợp lý? | Cần kiểm tra |
| --- | --- | --- |
| Lỗi mạng tạm thời khi đọc một tài nguyên | Có thể, với giới hạn/backoff | Timeout, idempotency, tài nguyên còn hợp lệ |
| Dữ liệu input sai định dạng | Thường không | Validation, trả lỗi rõ |
| Ghi database thất bại giữa nhiều bước | Chỉ khi có transaction/reconciliation | Commit đã xảy ra phần nào chưa? |
| Task bị cancel | Thường không retry mù quáng | Chủ thể hủy, lý do hủy |
| Caller `get(timeout)` hết hạn | Không suy ra worker thất bại | Task còn chạy? Có nên cancel không? |
| JVM `Error` nghiêm trọng | Thường cần cơ chế vận hành riêng | Trạng thái process/tài nguyên còn an toàn không? |

Ví dụ một task gọi API thanh toán, API đã commit nhưng phản hồi bị mất; worker ném timeout exception và Future trả `ExecutionException`. Nếu retry không có idempotency, có thể thanh toán hai lần. Vì vậy **cause chain là dữ liệu chẩn đoán**, không thay thế kiểm tra trạng thái giao dịch.

Nên áp dụng giới hạn số lần thử, backoff, deadline, circuit breaker khi phù hợp và quy tắc không thực thi side effect hai lần.

## 19. Xử lý InterruptedException khi chờ Future

Nếu thread caller bị interrupt trong `Future.get()`, phương thức có thể ném `InterruptedException` và bỏ chờ. Trong nhiều API interruptible, cờ interrupt bị xóa khi exception được ném; xử lý hợp lệ thường là:

**Truyền lên nếu API cho phép:**

```java
String read(Future<String> future)
        throws InterruptedException, ExecutionException {
    return future.get();
}
```

**Hoặc khôi phục cờ rồi kết thúc đường chờ:**

```java
try {
    String value = future.get();
    consume(value);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
} catch (ExecutionException e) {
    report(e.getCause());
}
```

Đây là hai đoạn minh họa; `consume`, `report` do ứng dụng định nghĩa. `InterruptedException` nói về **thread caller**, không mặc nhiên nói task đã lỗi. Tùy chính sách, caller có thể gọi `future.cancel(true)` để yêu cầu worker dừng, nhưng phải hiểu interrupt worker là best-effort.

**Không nuốt tín hiệu:**

```java
try {
    future.get();
} catch (InterruptedException ignored) {
    // Bỏ qua rồi lặp lại vô hạn: mất khả năng hủy.
}
```

Đây là phản ví dụ. Nếu cần tiếp tục chờ dù bị interrupt, đó phải là quyết định có chủ đích, có deadline và khôi phục cờ khi thoát. Đặc biệt khi ứng dụng shutdown, bỏ qua interrupt làm worker/caller dễ bị treo.

## 20. Hủy Future và cleanup của task

`Future.cancel(true)` có thể yêu cầu ngắt task đang chạy. Sau khi Future được đánh dấu canceled, `future.get()` ném `CancellationException` thay vì `ExecutionException` cho nhánh đó. Tuy vậy:

- Task có thể chưa bắt đầu: cancel ngăn nó chạy.
- Task đang chạy: yêu cầu interrupt được gửi nếu implementation hỗ trợ; task có thể hợp tác và dừng.
- Task bỏ qua interrupt: code có thể tiếp tục chạy sau khi Future đã canceled.
- Task có `finally` giải phóng file/lock/socket: Future canceled **không chứng minh finally đã chạy xong**.

Nếu cần biết cleanup thực tế đã hoàn tất, hãy có tín hiệu riêng như `CountDownLatch` trong `finally` hoặc đợi vòng đời executor theo hợp đồng. `Future.get()` sau cancel có thể ném `CancellationException` ngay, nên không phải một “join cleanup” đáng tin cho task đó.

Điều tương tự xảy ra khi `ExecutorService.shutdownNow()`: nó là yêu cầu dừng best-effort, không biến một tác vụ tùy ý thành trạng thái kết thúc an toàn ngay lập tức.

## 21. Quan sát exception ở thời điểm phù hợp

Có ba thời điểm khác nhau:

1. **Nộp task (`submit`):** task có thể chưa chạy; `submit()` thường chỉ trả Future. Lỗi do *không nhận task* như `RejectedExecutionException` có thể xảy ra ngay tại thời điểm nộp, **không phải một `ExecutionException` được bọc bên trong Future**.
2. **Thực thi task (worker):** task có thể thành công, fail hoặc bị cancel; trạng thái được ghi nhận bởi Future/handler.
3. **Quan sát kết quả (`get`/callback):** caller nhận outcome hoặc exception khi quyết định quan sát.

Điều này giải thích vì sao:

```java
Future<Integer> f = executor.submit(() -> 1 / 0);
// submit() có thể trả Future bình thường.
f.get(); // ExecutionException chứa ArithmeticException.
```

Đây là đoạn minh họa, `executor` đã được tạo và caller xử lý checked exceptions. Nó không có nghĩa exception “xảy ra lúc get” trong worker: lỗi toán học xảy ra lúc task thực thi, còn `get()` là **thời điểm lỗi được báo lại cho caller**.

### Khi nào cần log tại worker?

Với các task fire-and-forget, nếu không ai giữ Future, phải có cơ chế chủ động báo failure: log trong task boundary, callback, supervisor hoặc đường kết quả tập trung. Không log cùng một Throwable nhiều lần ở mọi tầng rồi gây nhiễu. Có thể lựa chọn một tầng sở hữu trách nhiệm báo lỗi, lưu nguyên cause chain và correlation ID.

## 22. Lỗi trong callback cũng là một lỗi task khác

Với `CompletableFuture`, một stage có thể thành công nhưng callback xử lý kết quả lại ném lỗi:

```java
CompletableFuture<Integer> mapped =
        CompletableFuture.completedFuture(3)
                .thenApply(value -> {
                    throw new IllegalStateException("mapping failed");
                });
```

Đây là đoạn minh họa. Future nguồn thành công với giá trị `3`, nhưng stage `mapped` thất bại. Nếu chỉ kiểm tra Future nguồn, bạn sẽ **không thấy lỗi của stage tiếp theo**. Mỗi stage tạo một completion/outcome mới.

Một chuỗi stage có thể có nhiều tầng wrapper, nên hãy ghi:

- Stage nào thất bại, tên bước và key dữ liệu (không chứa bí mật).
- Cause và stack trace tại bước đó.
- Chỗ nào tạo fallback (`exceptionally`) và chỗ nào còn propagate lỗi.
- Completion cuối cùng mà caller thực sự đợi là stage nào.

`whenComplete` được dùng để quan sát outcome nhưng callback cũng có thể ném exception, làm trạng thái downstream khác đi theo hợp đồng. Đừng tự cho callback “chỉ log” là không bao giờ ảnh hưởng kết quả nếu nó có thể ném.

## 23. Các lỗi thường gặp khi xử lý ExecutionException

### Lỗi 1 — Chỉ catch ExecutionException mà bỏ qua nguyên nhân

```java
catch (ExecutionException e) {
    System.out.println("failed");
}
```

Làm mất ngữ cảnh chẩn đoán. Nên xem `e.getCause()` và lưu stack trace phù hợp.

### Lỗi 2 — Bắt rồi trả kết quả mặc định vô điều kiện

Một lỗi dữ liệu nghiêm trọng có thể bị biến thành số `0` hoặc danh sách rỗng mà caller tưởng hợp lệ. Fallback phải theo **hợp đồng nghiệp vụ**, không phải cách né exception.

### Lỗi 3 — Gọi submit rồi quên Future

Future giữ failure; nếu không đọc, caller có thể không thấy lỗi. Cần người chịu trách nhiệm thu thập kết quả hoặc cơ chế supervisor/log.

### Lỗi 4 — Nhầm TimeoutException với ExecutionException

Timeout nói rằng caller đợi quá hạn, **không** chứng minh worker đã thất bại. Nếu cần, kiểm tra `isDone()`, quyết định cancel và truy outcome khi task thực sự hoàn thành.

### Lỗi 5 — Nhầm CancellationException với lỗi gốc của task

Future canceled có ngữ nghĩa riêng; `get()` ném `CancellationException`. Không tự báo rằng worker đã ném một exception nghiệp vụ nào đó.

### Lỗi 6 — Nuốt InterruptedException

Caller bị interrupt khi chờ mà vẫn lặp `get()` vô hạn có thể làm shutdown kẹt. Propagate hoặc khôi phục cờ và thoát ở điểm an toàn.

### Lỗi 7 — Gọi Future.get() trong pool một worker để chờ task con cùng pool

Nếu worker duy nhất của single-thread executor nộp một task con vào chính pool rồi đợi `get()`, task con không có worker để chạy: **thread-starvation deadlock**. ExecutionException không cứu được vòng chờ. Tránh phụ thuộc vòng, dùng kiến trúc task khác hoặc executor có khả năng thực thi tương ứng.

### Lỗi 8 — Tưởng `execute` và `submit` có cùng cơ chế báo exception

`submit` ghi lỗi vào Future; `execute` không trả Future. Nếu dựa vào `UncaughtExceptionHandler` để thấy lỗi của mọi task submit, có thể bỏ sót.

### Lỗi 9 — Log message nhưng không giữ cause

Một câu `e.getMessage()` không đủ để thấy chỗ xảy ra hoặc lỗi bị bọc. Log throwable có stack trace và cause, cân nhắc suppressed exceptions.

### Lỗi 10 — Retry task có side effect mà không kiểm tra commit

Task đã có thể ghi database/gửi message trước khi ném exception. Retry mù quáng dễ tạo tác dụng phụ trùng lặp.

### Lỗi 11 — Không quan sát lỗi của scheduled task lặp lại

Một lần chạy periodic task ném exception có thể khiến các lần sau bị ngăn. Nếu không giữ `ScheduledFuture` hoặc có giám sát, lịch chạy có thể dừng mà không ai nhận ra.

## 24. Checklist truy nguyên khi task bất đồng bộ thất bại

Khi nhận `ExecutionException` hoặc lỗi một stage của `CompletableFuture`, hãy kiểm tra:

1. **Tên task và ID:** task nào, input/correlation nào, worker nào thực thi?
2. **API nộp:** `submit`, `execute`, `schedule`, `invokeAll` hay CompletableFuture stage nào?
3. **API chờ:** `get()`, `get(timeout)`, `join()`, callback? Wrapper có thể khác.
4. **Loại outcome:** task thất bại (`ExecutionException`/`CompletionException`), caller bị interrupt, timeout hay canceled?
5. **Cause trực tiếp:** `getCause()` là kiểu gì? Message gì?
6. **Cause chain:** tầng nào tạo wrapper, tầng nào thêm ngữ cảnh, root cause nào thực sự hữu ích?
7. **Suppressed errors:** có lỗi cleanup thứ cấp không?
8. **Stack trace:** lỗi bắt đầu tại phương thức nào? Đường gọi worker và stage liên quan?
9. **Side effect:** task đã sửa state hoặc resource nào trước khi fail? Có thể retry an toàn không?
10. **Giám sát:** ai giữ Future, ai ghi log failure, có metric theo loại lỗi không?
11. **Lifecycle:** executor/scheduler còn sống, đã shutdown hay task định kỳ ngừng chạy?
12. **Chính sách:** propagate, fallback, retry, cancel các task còn lại hoặc chuyển thành domain error?

Bản chất của `ExecutionException` là chuyển lỗi qua ranh giới bất đồng bộ. **Chẩn đoán đúng** còn cần biết task được nộp khi nào, ở đâu và lỗi đã ảnh hưởng tới trạng thái nào.

## 25. Tự kiểm tra kiến thức

**Câu 1.** Một `Callable` ném `IOException`; thread caller gọi `future.get()`. Loại exception caller nhận thường là gì?

**Đáp án:** `ExecutionException`; `getCause()` là `IOException` do task ném.

**Câu 2.** Nếu chỉ gọi `executor.submit(task)` mà không giữ Future hoặc quan sát outcome, có thể bỏ sót lỗi không?

**Đáp án:** Có. Lỗi được giữ trong Future của task nhưng caller không tự nhận exception tại `submit()`.

**Câu 3.** `future.get(100, MILLISECONDS)` ném `TimeoutException` có nghĩa task chắc chắn đã thất bại không?

**Đáp án:** Không. Task có thể còn đang chạy và hoàn thành sau đó.

**Câu 4.** `future.get()` ném `InterruptedException` là lỗi do task ném hay do thread chờ?

**Đáp án:** Đó là tín hiệu **thread gọi `get()` bị interrupt**. Không tự suy ra task đã thất bại.

**Câu 5.** Khi Future bị hủy, `get()` thường ném exception nào?

**Đáp án:** `CancellationException`, một unchecked exception, không phải `ExecutionException`.

**Câu 6.** `CompletableFuture.get()` và `CompletableFuture.join()` bọc lỗi khác nhau thế nào?

**Đáp án:** `get()` thường ném checked `ExecutionException`, `join()` thường ném unchecked `CompletionException`. Trong cả hai trường hợp, cần xem cause.

**Câu 7.** Vì sao `IllegalStateException("processing failed", cause)` tốt hơn bỏ cause?

**Đáp án:** Giữ chuỗi nguyên nhân để truy ngược lỗi gốc và stack trace, đồng thời thêm ngữ cảnh nghiệp vụ.

**Câu 8.** Nếu `ScheduledFuture` của một periodic task nhận `ExecutionException` vì một lượt chạy ném lỗi, các lượt tiếp theo có tiếp tục bình thường không?

**Đáp án:** Với `scheduleAtFixedRate`/`scheduleWithFixedDelay` tiêu chuẩn, một lượt periodic task thất bại bất thường sẽ ngăn các lượt tiếp theo của chính task đó.

**Câu 9.** `ExecutorService.invokeAll` trả về danh sách Future có chứng minh mọi task thành công không?

**Đáp án:** Không. Cần gọi `get()` trên từng Future để phân biệt thành công, exception hoặc cancellation.

**Câu 10.** `Future.get()` sau khi task thành công có ý nghĩa JMM gì?

**Đáp án:** Hành động của computation trước khi hoàn thành xảy ra trước những hành động của caller sau khi lấy kết quả theo hợp đồng Future; không thay thế việc đồng bộ những sửa đổi shared mutable state xảy ra về sau.

**Câu 11.** Một task đã ghi database nhưng ném exception khi gửi response. Có nên retry lập tức từ đầu không?

**Đáp án:** Không nếu chưa kiểm tra commit/idempotency; retry có thể nhân đôi tác dụng phụ.

**Câu 12.** Tại sao không chỉ log root cause mà bỏ các wrapper tầng nghiệp vụ?

**Đáp án:** Wrapper có thể chứa ngữ cảnh quan trọng: tên bước, task ID, trạng thái xử lý và quy tắc retry. Cần nhìn cả cause chain.

## 26. Tổng kết

`ExecutionException` là **cơ chế báo lại lỗi của công việc bất đồng bộ qua `Future.get()`**. Khi gặp nó, bước đầu tiên là **`getCause()`**, sau đó giữ và đọc **cả cause chain, stack trace, ngữ cảnh task và các side effect có thể đã xảy ra**.

Điều cần phân biệt rõ: **task thất bại** (`ExecutionException`), **caller hết thời gian chờ** (`TimeoutException`), **caller bị ngắt khi chờ** (`InterruptedException`), và **Future bị hủy** (`CancellationException`). Với `CompletableFuture`, `join()` thường dùng `CompletionException`; với scheduled periodic task, một lỗi không xử lý có thể ngăn những lần chạy tiếp theo.

Muốn hệ thống đáng tin cậy, không chỉ nộp task mà còn phải có **chủ sở hữu Future/outcome**, quy tắc log/propagate/retry, timeout/cancel rõ ràng và xử lý lifecycle executor. Ngoại lệ được bọc không làm mất lỗi gốc nếu ta **giữ `getCause()` và quan sát đúng tầng**, nhưng cũng không tự giải quyết giao dịch đã thất bại một phần hoặc tài nguyên chưa được dọn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-006">invokeAll() cho nhóm kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# invokeAll() cho nhóm kết quả trong Java ExecutorService

## 1. Mục tiêu: gửi một nhóm tác vụ và nhận đủ các Future

Khi có nhiều công việc độc lập, ví dụ tính giá của một nhóm sản phẩm hoặc tổng hợp nhiều mảnh dữ liệu, việc tự tạo một thread cho từng việc vừa khó quản lý vừa dễ vượt quá tài nguyên. `ExecutorService` cho phép nộp nhiều `Callable` vào một pool rồi quản lý kết quả bằng `Future`.

**`invokeAll()`** là API dành cho một **nhóm task**: executor gửi các task để thực thi, **chờ đến khi tất cả hoàn tất** (hoặc đến hạn nếu dùng phiên bản timeout), rồi trả về một `List<Future<T>>`. Mỗi `Future` đại diện cho kết quả hoặc trạng thái thất bại/hủy của **một task tương ứng**.

Điểm khác với gọi `submit()` trong một vòng lặp: `invokeAll()` gom bước submit + chờ nhóm vào một lời gọi, có phiên bản áp dụng **một ngân sách timeout chung** cho toàn nhóm. Nhưng:

- `invokeAll()` **không** đồng nghĩa mọi task đều **thành công**.
- `invokeAll()` **không** trả trực tiếp một `List<T>`; nó trả `List<Future<T>>`, caller vẫn phải xử lý `get()` từng phần tử.
- `invokeAll()` **không** cam kết thứ tự **bắt đầu** hay **kết thúc** task.
- Danh sách Future trả về **theo thứ tự tương ứng của các task trong collection được truyền vào theo thứ tự iterator**, không theo thứ tự hoàn tất. Với `List.of(...)`, thứ tự đó là thứ tự các phần tử trong List.
- Nếu dùng `invokeAll(tasks, timeout, unit)`, những task chưa hoàn tất khi hết hạn sẽ được **yêu cầu hủy**; việc chấm dứt thân task phụ thuộc khả năng hợp tác với interrupt.
- Đây là concurrency ở **Java/JDK nói chung**; không phụ thuộc framework.

## 2. API và ý nghĩa hai overload

`ExecutorService` khai báo hai dạng thường dùng:

```java
<T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks)
        throws InterruptedException;

<T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks,
        long timeout,
        TimeUnit unit)
        throws InterruptedException;
```

Đoạn trên là **chữ ký phương thức rút gọn từ API**, không phải một file Java chạy độc lập.

| Dạng | Khi nào trả về bình thường? | Trạng thái Future được trả về |
| --- | --- | --- |
| `invokeAll(tasks)` | Sau khi **tất cả task hoàn tất**, bất kể kết quả thành công hay ném lỗi | Các Future đã hoàn tất; `get()` trả giá trị hoặc báo lỗi/hủy |
| `invokeAll(tasks, timeout, unit)` | Khi tất cả đã hoàn tất **hoặc** ngân sách chờ hết | Các Future đã hoàn tất hoặc bị hủy; task còn dở được yêu cầu hủy |

**Lưu ý về checked exceptions:** hai overload khai báo `InterruptedException` khi thread **đang gọi invokeAll** bị interrupt trong lúc chờ. Phiên bản có timeout **không ném `TimeoutException` cho caller** khi hết hạn; nó trả danh sách `Future` sau khi xử lý cancellation cho những task chưa hoàn tất. Phải đọc `future.isCancelled()` hoặc xử lý `CancellationException` khi lấy kết quả.

Một `Callable<T>` có thể trả `T` hoặc ném `Exception`. Việc một Callable ném lỗi **không làm `invokeAll` lập tức ném exception của Callable**; lỗi được lưu trong Future tương ứng và xuất hiện qua `get()` dưới dạng `ExecutionException`.

### Hình dung vòng đời

```text
List<Callable<T>> input
         |
         v
ExecutorService.invokeAll(...)
         |
         +--> Callable A --> Future A
         +--> Callable B --> Future B
         +--> Callable C --> Future C
         |
         v
Chờ đến khi cả nhóm xong hoặc deadline hết
         |
         v
List<Future<T>> theo thứ tự A, B, C
         |
         +--> get() thành công -> giá trị T
         +--> get() thất bại  -> ExecutionException
         +--> get() bị hủy   -> CancellationException
```

**Thứ tự danh sách là thứ tự ánh xạ task → Future**. Task B có thể hoàn tất trước A, nhưng `futures.get(0)` vẫn tương ứng với task A.

## 3. Ví dụ Java 17+: chạy ba phép tính và tổng hợp kết quả

Tạo file `InvokeAllBasic.java`:

```java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class InvokeAllBasic {
    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> 2 * 2,
                    () -> 3 * 3,
                    () -> 4 * 4
            );

            List<Future<Integer>> futures = pool.invokeAll(tasks);
            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get();
            }

            System.out.println("Future count: " + futures.size());
            System.out.println("Sum: " + total);
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac InvokeAllBasic.java
java InvokeAllBasic
```

Đầu ra:

```text
Future count: 3
Sum: 29
```

Giải thích:

1. `Executors.newFixedThreadPool(3)` dùng tối đa ba worker chạy task đồng thời; số core và scheduling thực tế do JVM/hệ điều hành quyết định.
2. `List.of(...)` giữ thứ tự rõ ràng của ba Callable.
3. `invokeAll(tasks)` chờ cả ba task hoàn tất bình thường; danh sách Future có ba vị trí tương ứng.
4. Vì các task chỉ tính số và không ném lỗi, `future.get()` lần lượt trả 4, 9, 16; tổng luôn là 29, **không phụ thuộc thứ tự task hoàn tất**.
5. `finally` gọi `shutdown()` để executor không tiếp nhận task mới. Với ứng dụng lâu dài, cần quản lý vòng đời executor ở tầng sở hữu, không tạo pool không shutdown cho mỗi lần gọi nghiệp vụ.

Trong ví dụ này `main` không cần `awaitTermination()` để nhìn thấy ba kết quả: `invokeAll` đã đợi các task trong nhóm hoàn tất, và `get()` xác nhận từng kết quả. `shutdown()` vẫn cần thiết để đóng vòng đời worker sau cùng.

## 4. Thứ tự Future khác thứ tự hoàn tất

Ta có thể **cố ý buộc task thứ hai hoàn tất trước task thứ nhất** và quan sát thứ tự List không đổi.

Tạo file `InvokeAllOrdering.java`:

```java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class InvokeAllOrdering {
    public static void main(String[] args) throws Exception {
        CountDownLatch secondFinished = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Callable<String>> tasks = List.of(
                    () -> {
                        secondFinished.await();
                        return "first";
                    },
                    () -> {
                        secondFinished.countDown();
                        return "second";
                    }
            );

            List<Future<String>> results = pool.invokeAll(tasks);
            System.out.println("Index 0: " + results.get(0).get());
            System.out.println("Index 1: " + results.get(1).get());
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac InvokeAllOrdering.java
java InvokeAllOrdering
```

Đầu ra:

```text
Index 0: first
Index 1: second
```

Vì pool có hai worker, task thứ hai có cơ hội chạy và gọi `countDown()`, cho phép task thứ nhất rời `await()`. **Task thứ nhất không thể hoàn tất trước khi task thứ hai gọi `countDown()`**, nhưng không cần đoán chính xác thứ tự các câu lệnh sau tín hiệu; hai giá trị được ánh xạ theo **vị trí đầu vào**. Nếu pool chỉ có **một worker** và task thứ nhất chờ một task thứ hai **đang nằm trong hàng đợi cùng pool**, chương trình có thể tự tạo **thread starvation deadlock**. Vì vậy cần hiểu quan hệ phụ thuộc giữa các task, không chỉ số lượng Future.

**Khi cần xử lý kết quả ngay khi từng task hoàn tất**, đừng dùng thứ tự List như một completion queue. `ExecutorCompletionService` hoặc thiết kế callback/`CompletableFuture` phù hợp hơn.

## 5. Xử lý lỗi từng Callable: invokeAll không phải fail-fast

Một task trong nhóm có thể thất bại nhưng nhóm vẫn có những kết quả hợp lệ khác. `invokeAll` **chờ tất cả task hoàn tất** theo overload không timeout; nó không tự dừng mọi việc khi thấy một Future chứa exception.

Tạo file `InvokeAllFailures.java`:

```java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class InvokeAllFailures {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> tasks = List.of(
                    () -> "ok-A",
                    () -> {
                        throw new IllegalArgumentException("bad input");
                    },
                    () -> "ok-C"
            );

            List<Future<String>> futures = pool.invokeAll(tasks);

            for (int index = 0; index < futures.size(); index++) {
                try {
                    System.out.println(index + ": "
                            + futures.get(index).get());
                } catch (ExecutionException e) {
                    System.out.println(index + ": ERROR "
                            + e.getCause().getClass().getSimpleName());
                }
            }
        } finally {
            pool.shutdown();
        }
    }
}
```

Chạy:

```bash
javac InvokeAllFailures.java
java InvokeAllFailures
```

Đầu ra:

```text
0: ok-A
1: ERROR IllegalArgumentException
2: ok-C
```

Phân tích:

- `invokeAll` trả về một Future cho **mỗi** Callable theo thứ tự List.
- Task ở index 1 ném `IllegalArgumentException`. Khi gọi `get()` của Future đó, caller nhận `ExecutionException`; nguyên nhân là exception gốc.
- Hai task còn lại đã hoàn tất bình thường, vẫn lấy được kết quả.
- Nếu caller chỉ duyệt một phần danh sách, lỗi ở Future không được gọi `get()` có thể bị bỏ sót ở tầng tổng hợp. Vì vậy khi cần báo cáo đầy đủ, phải kiểm tra từng Future.
- Đừng bắt `ExecutionException` quanh `invokeAll(tasks)` vì đây **không phải** checked exception mà lời gọi `invokeAll` khai báo; hãy bắt khi gọi `future.get()`.

**Chính sách xử lý khi có lỗi:** nếu bắt buộc **tất cả phần** phải thành công, có thể gom lỗi rồi đánh dấu toàn batch thất bại; nếu cho phép kết quả từng phần, báo rõ phần thành công và phần thất bại. Đây là quyết định nghiệp vụ, không phải semantics tự động của `invokeAll`.

## 6. Dùng timeout cho **toàn nhóm** và hủy phần chưa hoàn tất

Overload có hạn thời gian:

```java
List<Future<Result>> results =
        pool.invokeAll(tasks, 500, TimeUnit.MILLISECONDS);
```

Đây là snippet; `Result`, `pool` và `tasks` phải được khai báo. Thời gian 500 ms là một **ngân sách chung** cho lời gọi, **không phải 500 ms riêng từng task**. Khi hết thời gian, những task chưa hoàn tất bị **cancel** theo hợp đồng; nó không bảo đảm mã task thực sự ngừng ngay nếu đang trong một đoạn không hợp tác với interrupt.

### Java 17+: tạo timeout có kiểm soát mà không dựa vào sleep

Tạo file `InvokeAllTimeout.java`:

```java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class InvokeAllTimeout {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);

        try {
            List<Callable<String>> tasks = List.of(
                    () -> {
                        gate.await();
                        return "A";
                    },
                    () -> {
                        gate.await();
                        return "B";
                    }
            );

            List<Future<String>> results =
                    pool.invokeAll(tasks, 50, TimeUnit.MILLISECONDS);

            long cancelled = results.stream()
                    .filter(Future::isCancelled)
                    .count();

            System.out.println("Future count: " + results.size());
            System.out.println("Cancelled on timeout: " + cancelled);
        } finally {
            gate.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac InvokeAllTimeout.java
java InvokeAllTimeout
```

Đầu ra khi không có interrupt ngoài:

```text
Future count: 2
Cancelled on timeout: 2
```

**Vì sao hai Future đều bị hủy?** Trong thời gian `invokeAll` đang chờ, `gate` chưa thể được mở bởi main (main còn ở trong chính lời gọi). Task đã chạy thì mắc ở `gate.await()`, task chưa chạy thì vẫn chưa thể tự trả kết quả. Khi timeout, `invokeAll` yêu cầu hủy các Future chưa xong. `gate.countDown()` trong `finally` đảm bảo không cố ý giữ cổng đóng khi chương trình rời phạm vi ví dụ.

Các task bị hủy có thể nhận interrupt tại `await()`, nhưng điều quan trọng của `isCancelled()` là **trạng thái Future**, không phải lời chứng minh rằng mọi thread đã ngừng trước thời điểm phương thức trả về. Trong thực tế còn có side effect, I/O hoặc đoạn CPU không kiểm tra interrupt; chúng cần giao thức hủy và cleanup phù hợp.

**Không dùng `Future.get()` trên các phần tử này với kỳ vọng nhận kết quả bình thường:** Future đã cancelled sẽ ném `CancellationException`. Nếu vẫn cần giá trị thay thế, phải định nghĩa default hoặc retry theo nghiệp vụ.

### Nếu task hoàn tất sát deadline thì sao?

Kết quả có thể nằm ở ranh giới: một task đã hoàn thành trước khi việc cancel được áp dụng có thể có `isCancelled() == false` và chứa giá trị/exception; task chưa xong bị yêu cầu hủy. Đừng suy từ thời gian gần deadline để khẳng định một task cụ thể chắc chắn bị hủy. Chương trình ví dụ dùng latch đóng để loại trừ khả năng task hoàn tất bình thường trước timeout.

### Timeout không phải giới hạn tuyệt đối của toàn bộ workflow

`invokeAll(..., timeout, unit)` giới hạn nỗ lực chờ **nhóm task** theo hợp đồng API. Việc thu thập kết quả, serialize dữ liệu, ghi file hay gọi dịch vụ bên ngoài sau đó có thể tiếp tục tốn thời gian. Scheduling và cancellation cleanup cũng có thể ảnh hưởng độ trễ thực tế. Khi cần deadline cuối-đến-cuối, dùng một ngân sách thời gian chung và truyền **thời gian còn lại** cho từng bước hỗ trợ timeout.

## 7. Interrupt của caller trong lúc invokeAll

`invokeAll` là một **phương thức blocking interruptible**. Nếu thread đang chờ nhóm Future bị interrupt, nó có thể ném `InterruptedException` và các task chưa hoàn tất được yêu cầu hủy theo hợp đồng.

Có hai tầng riêng biệt:

1. **Caller thread**: thread gọi `invokeAll` đang chờ, có thể nhận `InterruptedException`.
2. **Worker threads**: đang chạy `Callable` trong executor, có thể nhận yêu cầu interrupt khi task bị cancel.

`InterruptedException` ở caller **không đồng nghĩa** mọi worker đã dừng hoặc mọi tài nguyên đã rollback. Task đang làm CPU lâu có thể cần kiểm tra `Thread.currentThread().isInterrupted()`; I/O có thể có semantics interrupt/close khác nhau; một số critical section cần kết thúc bước cập nhật rồi mới hợp tác hủy.

### Mẫu xử lý đúng khi caller nhận interrupt

```java
// Snippet: pool và tasks đã được tạo, phương thức bao quanh
// không khai báo throws InterruptedException.
try {
    List<Future<Integer>> futures = pool.invokeAll(tasks);
    consumeResults(futures);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

Đoạn trên là **snippet**, `consumeResults` và biến xung quanh chưa được khai báo. Khi bắt `InterruptedException` mà không truyền tiếp, khôi phục cờ bằng `Thread.currentThread().interrupt()` để tầng gọi có thể biết yêu cầu dừng. Nếu phương thức cho phép `throws InterruptedException`, truyền thẳng ngoại lệ thường đơn giản hơn.

**Không nên** nuốt `InterruptedException` rồi lập tức lặp lại `invokeAll` vô hạn. Điều đó có thể tạo công việc trùng, làm hỏng chính sách shutdown và khiến hệ thống khó hủy.

## 8. Task có phụ thuộc nhau: nguy cơ thread starvation deadlock

`invokeAll` phù hợp nhất với những task có thể chạy độc lập hoặc có giao thức phụ thuộc được thiết kế kỹ. Một lỗi phổ biến là task A đang chạy trong pool nhỏ, rồi **chờ task B cũng phải được chính pool đó chạy**.

Ví dụ nguy hiểm, chỉ mô tả ý tưởng:

```text
Pool có 1 worker:
  Worker nhận task A
  A đợi kết quả task B
  B đang trong queue của cùng pool
  Không worker nào rảnh để thực thi B
```

`invokeAll` ở caller có thể chờ vĩnh viễn nếu không có timeout hoặc cách giải phóng phụ thuộc. Lỗi nằm ở **thiết kế quan hệ chờ**, không phải ở `invokeAll` tự nó.

Cách phòng tránh:

- Không cho task đang chiếm toàn bộ worker chờ một task khác chỉ có thể chạy trên chính pool đó.
- Nếu phải phân pha, tách công việc, dùng dependency graph hoặc `CompletableFuture` mà không chặn worker một cách không cần thiết.
- Chọn pool size và queue policy theo workload, nhưng **tăng số thread không phải bản sửa nền tảng** cho một chu trình phụ thuộc.
- Dùng timeout và log/metrics để phát hiện, không chỉ dựa vào deadlock detector cho monitor: thread starvation deadlock không nhất thiết là monitor deadlock.

Ví dụ `InvokeAllOrdering` dùng **hai worker** nhằm cho phép task thứ hai mở latch của task thứ nhất, tránh tự chặn.

## 9. invokeAll() khác submit(), invokeAny() và CompletionService

| API | Lúc nào caller có thể nhận thông tin? | Thứ tự / kết quả |
| --- | --- | --- |
| `submit(task)` | `submit` thường trả Future ngay; caller chọn lúc `get()` | Một Future cho một task; không chờ ngay tại `submit` |
| `invokeAll(tasks)` | Trả sau khi cả nhóm hoàn tất | List Future theo thứ tự task đầu vào |
| `invokeAll(tasks, timeout, unit)` | Trả khi hoàn tất toàn nhóm hoặc timeout | List Future theo thứ tự đầu vào; phần chưa xong bị cancel |
| `invokeAny(tasks)` | Trả một kết quả thành công theo hợp đồng | Trả **một giá trị T**, không phải List Future; các task khác được yêu cầu hủy |
| `ExecutorCompletionService` | Caller có thể `take()/poll()` từng Future khi chúng hoàn tất | Thứ tự **hoàn tất**, không phải thứ tự nộp |
| `CompletableFuture.allOf(...)` | Một future tổng báo khi tất cả future thành phần hoàn tất | Không tự tạo List kết quả typed; cần truy xuất từng future |

### Khi nào dùng `invokeAny`?

Nếu chỉ cần **một kết quả thành công** từ nhiều nguồn (ví dụ hỏi nhiều máy chủ tương đương và dùng phản hồi hợp lệ đầu tiên), `invokeAny` có thể phù hợp hơn. Nó có thể bỏ qua task thất bại để chờ task khác thành công; nếu không có task nào thành công, sẽ báo lỗi theo hợp đồng. Nó không nhằm tập hợp mọi kết quả.

**Không dùng `invokeAny` để thay thế một batch mà mọi phần đều phải hoàn tất.** Còn nếu yêu cầu là cập nhật giao diện ngay khi từng kết quả sẵn sàng, `ExecutorCompletionService` thường tự nhiên hơn `invokeAll`.

### `CompletableFuture.allOf` và `invokeAll`

`CompletableFuture.allOf(f1, f2, ...)` tạo một `CompletableFuture<Void>` báo hiệu cả nhóm đã hoàn tất. Nó hỗ trợ composition không nhất thiết chặn thread caller cho tới khi gọi `join/get`, nhưng **không tự cung cấp một `List<T>`** và không tự biến nhiều task thành transaction. Mô hình cancellation/timeout của các future thành phần cũng cần xem xét riêng.

`invokeAll` đơn giản hơn khi code đồng bộ muốn nộp một Collection Callable, chờ một lần và gom các Future theo cùng thứ tự.

## 10. Tính đúng đắn dữ liệu và giới hạn của “batch”

`invokeAll` không làm các task chạy theo một transaction, không bảo đảm tất cả dùng chung một snapshot dữ liệu và không tự tạo một lock cho các cấu trúc mutable mà các task cùng truy cập.

Ví dụ nếu mọi Callable cùng `counter++` trên một trường thường, kết quả vẫn có thể sai vì data race/lost update. Pool và Future chỉ quản lý **thực thi và kết quả task**, không thay `AtomicInteger`, `synchronized` hoặc các cơ chế bảo vệ state.

### Memory visibility khi dùng Future

Hợp đồng memory-consistency của `java.util.concurrent` cho `ExecutorService`/`Future` có các quan hệ hữu ích:

- Các hành động của thread **trước khi submit một task** happens-before các hành động trong task theo hợp đồng executor.
- Các hành động của task happens-before những hành động **sau khi caller lấy được kết quả bằng `Future.get()`**.

Vì thế việc đọc kết quả qua `get()` giúp công bố hiệu ứng của task theo giao thức. Nhưng không nên suy rằng **hai task khác nhau** có thứ tự lẫn nhau chỉ vì chúng cùng một lời gọi `invokeAll`. Nếu cùng sửa trạng thái, cần đồng bộ riêng giữa các worker.

### Xử lý side effect khi có timeout/cancel

Một task có thể đã ghi dữ liệu, gửi sự kiện hoặc thực hiện một phần nghiệp vụ **trước khi bị hủy**. `Future.isCancelled()` chỉ mô tả trạng thái Future; nó không xác nhận side effect đã rollback.

Cần làm rõ:

1. Task nào được phép chạy nhiều lần khi retry?
2. Có idempotency key hoặc dấu phiên bản để tránh ghi trùng?
3. Có thể xử lý kết quả từng phần không, hay cần bù trừ?
4. Nếu timeout, có tiếp tục chờ cleanup ở tầng khác không?
5. Task có phản hồi interrupt và giải phóng tài nguyên trong `finally` không?

Nếu nghiệp vụ yêu cầu **all-or-nothing**, phải thiết kế transaction hoặc cơ chế điều phối thích hợp tại nơi sở hữu dữ liệu; `invokeAll` không cung cấp all-or-nothing.

## 11. Pool sizing, queue và nguy cơ quá tải

`invokeAll` có thể nhận rất nhiều task; nếu dùng pool ít worker và queue lớn, một phần lớn task sẽ chờ đến lượt. Cần cân nhắc:

- **`Executors.newFixedThreadPool(n)`** thường dùng queue không giới hạn thực tế; nộp batch rất lớn có thể tiêu thụ bộ nhớ và gây latency tăng.
- **`ThreadPoolExecutor` với bounded queue** cho phép giới hạn backlog nhưng có thể phát sinh `RejectedExecutionException` khi hết khả năng tiếp nhận. Không giả định `invokeAll` luôn có thể chèn trọn batch.
- Khi một task bị chặn lâu, worker tương ứng không tiến triển; nếu nhiều task cũng đang đợi tài nguyên, số worker còn thực thi hữu ích có thể ít hơn pool size.
- Dùng chunking, giới hạn concurrency, rate limiting hoặc backpressure nếu input lớn; không tạo hàng triệu Callable chỉ để chờ toàn bộ trong một lần.
- Quản lý thời gian chờ queue và execution riêng nếu cần phân tích latency; timeout của `invokeAll` tính theo lời gọi, không phải “chỉ tính lúc task bắt đầu chạy”.

**Đừng suy từ số lượng `Future` trả về rằng từng task đã chạy trong worker.** Với timeout/cancellation/rejection, có thể có task bị hủy khi còn trong queue và chưa vào `call()`. Với rejection policy chạy task tại caller, một phần công việc có thể còn chạy trên thread gọi; hiểu đúng policy là quan trọng khi đo thời gian và tổng số task hoàn thành.

## 12. Thiết kế API tổng hợp kết quả rõ ràng

Nếu cần bảo toàn thứ tự và nêu rõ lỗi từng tác vụ, ta có thể tạo một model kết quả cho batch. Ví dụ dưới đây **chạy độc lập**, trả chuỗi thành công hoặc lỗi theo vị trí.

Tạo file `InvokeAllSummary.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class InvokeAllSummary {
    record ItemResult(int index, String outcome) {}

    static List<ItemResult> execute(
            ExecutorService pool,
            List<Callable<String>> tasks) throws InterruptedException {
        List<Future<String>> futures = pool.invokeAll(tasks);
        List<ItemResult> output = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            try {
                output.add(new ItemResult(i, "OK: " + futures.get(i).get()));
            } catch (ExecutionException e) {
                output.add(new ItemResult(i,
                        "ERROR: "
                        + e.getCause().getClass().getSimpleName()));
            }
        }
        return List.copyOf(output);
    }

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> tasks = List.of(
                    () -> "alpha",
                    () -> { throw new IllegalArgumentException("x"); },
                    () -> "gamma"
            );

            List<ItemResult> result = execute(pool, tasks);
            for (ItemResult item : result) {
                System.out.println(item.index() + " " + item.outcome());
            }
        } finally {
            pool.shutdown();
        }
    }
}
```

Chạy:

```bash
javac InvokeAllSummary.java
java InvokeAllSummary
```

Đầu ra:

```text
0 OK: alpha
1 ERROR: IllegalArgumentException
2 OK: gamma
```

Ở đây `record` của Java 17 là một immutable value carrier cho chỉ số và outcome. `execute` chỉ gom danh sách **sau khi tất cả task đã hoàn tất** vì dùng overload không timeout. Nếu chuyển sang phiên bản timeout, cần thêm xử lý `CancellationException` hoặc kiểm tra `future.isCancelled()` trước `get()`; nếu không, task bị cancel có thể khiến phương thức tổng hợp kết thúc bất ngờ.

**Không nên dùng `get()` vô thời hạn trên các Future tùy ý trong một vòng lặp nếu từng Future được tạo bởi `submit` và chưa biết hoàn tất:** nếu task đầu chạy rất lâu, kết quả task sau dù đã xong vẫn bị giữ lại về mặt xử lý. `invokeAll` có chủ đích **đợi toàn nhóm** rồi mới tổng hợp, nên hành vi này phù hợp với batch. Nếu muốn stream kết quả sớm, chuyển sang CompletionService.

## 13. Những hiểu nhầm phổ biến

1. **“`invokeAll()` trả về một List kết quả T.”** Sai: trả `List<Future<T>>`.
2. **“Danh sách Future theo thứ tự task hoàn tất.”** Sai: tương ứng thứ tự task theo iterator đầu vào.
3. **“Một task lỗi khiến `invokeAll` ném `ExecutionException` ngay.”** Sai: thường thấy lỗi qua `future.get()`.
4. **“`invokeAll()` không timeout có fail-fast.”** Sai: chờ tất cả task hoàn tất, kể cả có task thất bại.
5. **“Bản timed sẽ ném `TimeoutException`.”** Sai: timed `invokeAll` trả List Future sau khi cancel task chưa hoàn tất.
6. **“`future.isCancelled()` chứng minh code task đã dừng.”** Sai: task có thể chưa hợp tác với interrupt hoặc còn cleanup.
7. **“Các task trong batch cùng lúc bắt đầu.”** Sai: chúng bị giới hạn bởi worker, queue và scheduling.
8. **“`invokeAll` cho nhóm kết quả thì các task có thể sửa chung ArrayList an toàn.”** Sai: cần bảo vệ shared mutable state riêng.
9. **“Đủ Future nghĩa là đủ kết quả thành công.”** Sai: mỗi Future có thể thành công, thất bại hoặc bị hủy.
10. **“Lấy `Future.get()` xong là phải `shutdownNow()` pool.”** Sai: pool có thể được tái sử dụng; chính owner quản lý lifecycle và shutdown hợp lý.
11. **“Timeout làm mọi side effect của các task đã chạy bị hoàn tác.”** Sai: cancellation không phải transaction.
12. **“Chỉ cần tăng số worker để chữa task A chờ task B cùng pool.”** Không phải lúc nào cũng đúng: cần sửa dependency hoặc giao thức chờ.
13. **“Một Future là một thread riêng.”** Sai: executor quyết định số worker và điều phối nhiều task qua cùng các thread.
14. **“HashSet task vẫn cho thứ tự nghiệp vụ rõ ràng.”** Không nên dựa vào iteration order không được quy định; dùng List khi cần mapping theo index.
15. **“`invokeAll` có thể gọi trên mọi Executor.”** Sai: phương thức này thuộc `ExecutorService`; interface `Executor` cơ bản chỉ có `execute`.

## 14. Checklist trước khi dùng invokeAll trong hệ thống thật

1. **Mục tiêu cần đủ kết quả hay chỉ kết quả đầu tiên?** Đủ kết quả dùng `invokeAll`; một thành công có thể dùng `invokeAny`.
2. **Có cần kết quả đến đâu xử lý đến đó?** Nếu có, cân nhắc `ExecutorCompletionService` hoặc `CompletableFuture`.
3. **Task có độc lập không?** Tránh worker chờ task khác vẫn xếp hàng trong chính pool nhỏ.
4. **Có yêu cầu thứ tự output không?** Dùng `List<Callable<T>>` để xác định mapping index → Future.
5. **Có chính sách lỗi rõ không?** Kiểm tra mọi Future qua `get()`, phân biệt lỗi task và lỗi caller.
6. **Có deadline chung không?** Dùng overload timeout, xử lý cancellation và side effect chưa hoàn tất.
7. **Callable có hợp tác interrupt?** Nếu dùng timeout/cancellation, kiểm tra blocking points, vòng CPU và cleanup trong `finally`.
8. **Có cần all-or-nothing không?** Đây là yêu cầu transaction nghiệp vụ, không phải tính chất của `invokeAll`.
9. **Pool có đủ khả năng nhận batch không?** Đánh giá worker, queue, rejection, backpressure và memory.
10. **Có shared mutable state không?** Thiết kế thread safety của state độc lập với quản lý Future.
11. **Có quản lý executor lifecycle không?** Shutdown khi owner hoàn tất công việc; không bỏ lại các non-daemon worker.
12. **Có kiểm thử thứ tự, thất bại, timeout và interrupt không?** Dùng latch để tạo ranh giới có kiểm soát, tránh kiểm thử timing bằng `sleep` tùy ý.

**Kết luận:** `ExecutorService.invokeAll()` là lựa chọn trực tiếp khi cần **gửi một collection Callable, chờ cả nhóm và nhận List Future theo đúng thứ tự task đầu vào**. Overload có timeout áp dụng **một ngân sách chung** và yêu cầu hủy những task chưa hoàn tất. Hãy kiểm tra từng Future để phân biệt kết quả, lỗi và hủy; quản lý interrupt, pool, quá tải và side effect theo giao thức của ứng dụng, thay vì xem `invokeAll` như một transaction hoặc một cơ chế đảm bảo mọi task đều thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-007">invokeAny() cho kết quả thành công đầu tiên</a>

<details>
<summary>Xem chi tiết</summary>

# invokeAny() cho kết quả thành công đầu tiên trong Java

## 1. Vấn đề và mục tiêu học

Trong một số bài toán concurrency, ta có nhiều cách độc lập để tạo **một kết quả tương đương**, và chỉ cần **một** cách hoàn tất thành công. Ví dụ: truy vấn nhiều replica đọc dữ liệu giống nhau, gọi các nguồn dự phòng, hoặc chạy nhiều thuật toán cho cùng một đầu ra đã chuẩn hóa. Đợi tất cả nguồn hoàn tất có thể tốn thời gian nếu một nguồn lỗi hoặc quá chậm.

`ExecutorService.invokeAny(Collection<? extends Callable<T>> tasks)` thực thi một nhóm `Callable<T>` và **trả trực tiếp kết quả của một task hoàn tất bình thường**. Nó không đợi mọi task thành công. Khi method hoàn tất (bình thường hoặc do exception), các task **chưa hoàn tất** được **yêu cầu hủy**.

~~~java
String response = pool.invokeAny(List.of(
        () -> readReplica("east"),
        () -> readReplica("west"),
        () -> readReplica("backup")
));
~~~

Đây là **đoạn trích minh họa**, trong đó `pool` là một `ExecutorService`, `readReplica` là hàm của ứng dụng; method bao ngoài phải khai báo/handle checked exceptions. Nếu một Callable trả dữ liệu thành công, caller nhận giá trị đó mà không cần `Future.get()` cho từng nguồn.

Sau bài học, bạn sẽ:

- Giải thích đúng `invokeAny`, hai overload, kiểu trả về và checked exceptions.
- Hiểu “thành công” là **không ném exception**, kể cả khi giá trị trả về là `null`.
- Phân biệt task lỗi trước, task thành công trước, tất cả task lỗi, hết timeout và caller bị interrupt.
- Phân biệt `invokeAny` với `invokeAll`, `submit`/`Future.get`, `ExecutorCompletionService` và `CompletableFuture.anyOf`.
- Dùng interruption/cancellation và cleanup đúng cách, không nhầm “đã cancel” với “thread đã chấm dứt”.
- Thiết kế pool đủ capacity, timeout theo deadline, validation kết quả nghiệp vụ, idempotency và giới hạn fan-out.
- Chạy ví dụ Java 17+ dàn dựng thứ tự bằng `CountDownLatch` thay vì phỏng đoán scheduler.

**Phạm vi:** Java SE và Executor Framework, không phụ thuộc bất kỳ framework nào.

## 2. API và các nhánh kết quả

Hai overload chuẩn:

~~~java
<T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException;

<T> T invokeAny(
        Collection<? extends Callable<T>> tasks,
        long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
~~~

| Trường hợp | Hành vi của invokeAny |
|---|---|
| Có task hoàn tất bình thường | Trả kết quả `T` của **một** task thành công |
| Một task ném exception, các task khác có thể thành công | Tiếp tục đợi/cạnh tranh, **không fail ngay vì lỗi đầu tiên** |
| Tất cả task đều không hoàn tất bình thường | Ném `ExecutionException` |
| Timed overload hết hạn trước khi có thành công | Ném `TimeoutException` |
| Thread gọi invokeAny bị interrupt trong khi đợi | Ném `InterruptedException` |
| Collection rỗng | `IllegalArgumentException` |
| Collection hoặc phần tử task cần thực thi là null | `NullPointerException` |
| Executor không thể tiếp nhận các task | Có thể ném `RejectedExecutionException` |

Javadoc cũng quy định **trên cả đường return bình thường lẫn đường thoát bằng exception, các task chưa hoàn tất được hủy**. Hủy không đồng nghĩa thu hồi side effects hay kill thread.

Một điểm rất quan trọng: chữ **success** được hiểu là **completion without throwing exception**, chứ không phải kết quả có status=200 hoặc dữ liệu hợp lệ theo nghiệp vụ. Nếu một Callable `return null`, `return Optional.empty()` hoặc `return Response(503)` mà không ném exception, `invokeAny` có thể chọn nó như một kết quả **thành công kỹ thuật**.

### Kết quả được chọn có thật sự là “nhanh nhất”?

Trong cách nói thông thường, người ta mô tả `invokeAny` là “lấy kết quả thành công đầu tiên”. Nhưng không nên suy rộng thành các cam kết sau:
- Chọn phần tử đầu tiên của `List`.
- Chọn task được **submit** sớm nhất.
- Chọn task có thời gian CPU ngắn nhất.
- Chọn replica có dữ liệu mới nhất/đúng nhất.
- Khởi động **tất cả** task cùng một thời điểm.
- Luôn trả cùng một winner khi chạy lại cùng input.

Scheduler, số workers, queue, thời gian hoàn tất và cách executor quan sát các completions có thể thay đổi winner. Nếu hai kết quả cùng thành công gần như đồng thời, **thứ tự chọn không nên bị kiểm thử bằng một chuỗi cố định**.

## 3. Mô phỏng cạnh tranh giữa các tác vụ

~~~text
Caller: invokeAny([A, B, C])
             |
             +--- A: ném IOException --------------------X (failure)
             |
             +--- B: xử lý rồi return "OK" -----------✓ (success)
             |
             +--- C: đang đợi remote I/O --------------------?
                          |
               B thành công, invokeAny chọn "OK"
                          |
               Task C chưa hoàn tất → yêu cầu cancel
                          |
               Caller có thể nhận "OK" dù C chưa cleanup xong
~~~

**Một failure sớm không phải lý do kết thúc nhóm**, trừ khi tất cả ứng viên đều đã thất bại. Một success đủ điều kiện mới là điểm kết thúc bình thường.

Nhiều nguồn được chạy theo `Callable.call()`:
- `call()` trả về một giá trị ⇒ success.
- `call()` ném exception ⇒ failed task.
- `call()` bị interrupt và ném `InterruptedException` ⇒ task thất bại/chấm dứt qua exception, không phải success.
- Task chưa được schedule hoặc đang chạy khi một task khác thắng ⇒ bị yêu cầu cancel theo API.

Một task có thể đã tạo hiệu ứng bên ngoài (ghi file, gửi request, commit database) trước khi được yêu cầu hủy. Vì thế nên ưu tiên các nguồn **đọc tương đương/idempotent** cho kiểu racing này.

## 4. Ví dụ Java độc lập 1: failure, winner và loser bị interrupt

Ví dụ dùng 3 workers để có khả năng đồng thời xử lý 3 ứng viên. Một ứng viên ném exception, một ứng viên chờ `CountDownLatch` không được mở, và một ứng viên trả `"OK"` **sau khi ứng viên chậm đã bắt đầu**. Kết quả cuối được kiểm soát mà **không dùng sleep**.

Lưu tệp **InvokeAnyFirstSuccessDemo.java**:

~~~java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InvokeAnyFirstSuccessDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);

        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch slowFinished = new CountDownLatch(1);
        CountDownLatch closedGate = new CountDownLatch(1);
        AtomicBoolean slowInterrupted = new AtomicBoolean(false);

        Callable<String> fail = () -> {
            throw new IllegalStateException("replica failed");
        };

        Callable<String> slow = () -> {
            slowStarted.countDown();
            try {
                closedGate.await();
                return "SLOW";
            } catch (InterruptedException e) {
                slowInterrupted.set(true);
                Thread.currentThread().interrupt();
                throw e;
            } finally {
                slowFinished.countDown();
            }
        };

        Callable<String> winner = () -> {
            slowStarted.await();
            return "OK";
        };

        try {
            String result = pool.invokeAny(
                    List.of(fail, slow, winner));

            slowFinished.await();
            System.out.println("result=" + result);
            System.out.println(
                    "slowInterrupted=" + slowInterrupted.get());
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Pool did not terminate");
            }
        }
    }
}
~~~

Biên dịch/chạy với JDK 17+:

~~~bash
javac InvokeAnyFirstSuccessDemo.java
java InvokeAnyFirstSuccessDemo
~~~

Output trong điều kiện bình thường, không có interrupt ngoài:

~~~text
result=OK
slowInterrupted=true
~~~

**Vì sao kết quả ổn định?**

1. Task `fail` ném exception. Nó không được chọn làm winner.
2. Task `slow` báo `slowStarted` rồi chờ `closedGate`. Gate này **không bao giờ mở** trong ví dụ, nên slow không thể tự return `"SLOW"`.
3. Task `winner` chỉ return `"OK"` sau khi `slowStarted.await()` hoàn tất; bảo đảm slow đã khởi động.
4. Khi `"OK"` được chọn, `invokeAny` yêu cầu hủy task slow đang chờ; `CountDownLatch.await()` phản ứng bằng `InterruptedException`, slow set `slowInterrupted=true`.
5. `slowFinished.countDown()` trong `finally` bảo đảm main có thể chờ slow đã đi qua cleanup trước khi đọc cờ.
6. `pool.shutdownNow()` nằm trong finally để bảo vệ cleanup executor nếu có lỗi bất ngờ, không phải nguyên nhân chính tạo kết quả thành công.

Lệnh `slowFinished.await()` là **điểm quan sát bổ sung của chương trình test**. Không nên nghĩ `invokeAny` tự chờ mọi loser hoàn tất cleanup trước khi trả về: nó chỉ yêu cầu cancel khi chưa hoàn tất.

## 5. Nếu tất cả các task đều lỗi?

Ví dụ ta gọi ba nguồn:

~~~java
Callable<String> a = () -> {
    throw new IllegalArgumentException("bad input");
};
Callable<String> b = () -> {
    throw new IllegalStateException("source unavailable");
};
Callable<String> c = () -> {
    throw new java.io.IOException("remote read failed");
};
~~~

Đây là các **đoạn trích** dùng trong method có imports phù hợp. Không task nào có normal result, nên `invokeAny(List.of(a,b,c))` ném `ExecutionException`.

**Không được mặc định một exception nguyên nhân của ExecutionException chứa toàn bộ lỗi của mọi task.** Tùy implementation và thứ tự completion, cause có thể là một trong các failures. Nếu cần báo cáo từng nguồn đã lỗi gì, dùng `invokeAll` rồi đọc các Futures, hoặc dùng `ExecutorCompletionService` để thu thập riêng các outcomes.

### Trường hợp task thành công bằng null

`Callable<String>` có thể `return null` bình thường. Theo contract, đó là success; `invokeAny` có thể return `null`. Tương tự, `Optional.empty()` hay một đối tượng `Response` có trường `ok=false` vẫn được coi là normal completion.

Nếu một kết quả như vậy **không dùng được**, hãy validate **bên trong Callable** trước khi return hoặc tự viết coordinator để chọn theo business predicate. Không giao trách nhiệm validation cho `invokeAny` khi nó không có API nhận predicate.

## 6. Ví dụ Java độc lập 2: mọi task lỗi, nhưng null được coi là success

Lưu tệp **InvokeAnyFailureCasesDemo.java**:

~~~java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InvokeAnyFailureCasesDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> errors = List.of(
                    () -> {
                        throw new IllegalArgumentException("A");
                    },
                    () -> {
                        throw new IllegalStateException("B");
                    }
            );

            boolean allFailed = false;
            try {
                pool.invokeAny(errors);
            } catch (ExecutionException e) {
                allFailed = true;
            }
            System.out.println("allFailed=" + allFailed);

            String result = pool.invokeAny(
                    List.<Callable<String>>of(() -> null));
            System.out.println("nullIsSuccess=" + (result == null));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac InvokeAnyFailureCasesDemo.java
java InvokeAnyFailureCasesDemo
~~~

Output:

~~~text
allFailed=true
nullIsSuccess=true
~~~

Trong phép gọi đầu, hai task đều throw; không có normal completion nên nhận `ExecutionException`. Trong phép gọi thứ hai, Callable return null mà không throw, do đó `invokeAny` trả null. Bài test không in `e.getCause()` vì không nên cam kết thứ tự exception khi nhiều task cùng thất bại.

### Kiểm tra dữ liệu đúng nơi

~~~java
Callable<String> checked = () -> {
    String data = fetchFromReplica();
    if (data == null || data.isBlank()) {
        throw new IllegalStateException("no usable data");
    }
    return data;
};
~~~

Đoạn trích này cần định nghĩa `fetchFromReplica()` ở ứng dụng. Nếu chuỗi rỗng có nghĩa “thất bại”, xác định điều đó trước khi return. Nhưng nếu null thật sự là một kết quả hợp lệ, không nên biến nó thành lỗi chỉ vì đã chọn API; hãy chọn công cụ phối hợp phù hợp với domain.

## 7. Overload có timeout

Cú pháp:

~~~java
String result = executor.invokeAny(
        tasks, 300, TimeUnit.MILLISECONDS);
~~~

Nếu **chưa có một thành công** trước khi hết khoảng chờ, overload timed ném `TimeoutException`. Những task chưa xong được yêu cầu cancel khi method thoát.

Phân biệt ba kết quả:

| Tình huống | Exception/result |
|---|---|
| Có ít nhất một normal result trong hạn | Trả kết quả `T` |
| Tất cả candidates cùng kết thúc bằng exception trước khi hết hạn | `ExecutionException` |
| Hết hạn mà vẫn chưa có normal result | `TimeoutException` |
| Caller bị interrupt trong lúc chờ | `InterruptedException` |

Timeout ở đây là **khoảng chờ của caller cho nhóm tasks**. Nó không làm tác vụ remote I/O đang chạy tự timeout ở đúng mốc đó và không buộc worker release mọi tài nguyên ngay lập tức.

### Ví dụ Java độc lập 3: timed invokeAny hết hạn

Hai tasks chờ latch không bao giờ được đếm xuống. Vì không thể normal-complete, timed call phải hết hạn nếu không bị ngắt bởi tín hiệu ngoài.

Lưu **InvokeAnyTimeoutDemo.java**:

~~~java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InvokeAnyTimeoutDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch closedGate = new CountDownLatch(1);

        Callable<String> blocked = () -> {
            closedGate.await();
            return "UNREACHABLE";
        };

        boolean timedOut = false;
        try {
            try {
                pool.invokeAny(
                        List.of(blocked, blocked),
                        80, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timedOut = true;
            }

            System.out.println("timedOut=" + timedOut);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Pool did not terminate");
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac InvokeAnyTimeoutDemo.java
java InvokeAnyTimeoutDemo
~~~

Output dự kiến:

~~~text
timedOut=true
~~~

Ví dụ không dựa vào lịch chạy chính xác đến 80ms. Hai Callables không thể return nếu latch chưa mở. Khi timed `invokeAny` thoát, các task đang chờ `CountDownLatch.await()` có đường phản ứng với cancellation qua `InterruptedException`.

Nếu thay latch bằng API blocking không hỗ trợ interruption, method vẫn có thể ném timeout ở caller nhưng worker vẫn chiếm resource. Cần timeout/close/cancel riêng theo hợp đồng của từng API.

## 8. Các loại exception cần xử lý tách biệt

Mẫu caller:

~~~java
try {
    return pool.invokeAny(candidates, 500, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    // Chưa có normal result trong deadline.
    logTimeout();
    throw e;
} catch (ExecutionException e) {
    // Không có candidate hoàn tất thành công.
    logAllFailed(e.getCause());
    throw e;
} catch (InterruptedException e) {
    // Thread caller bị yêu cầu hủy.
    Thread.currentThread().interrupt();
    throw e;
}
~~~

Đây là **mã tích hợp**, method bao ngoài phải khai báo hoặc xử lý checked exceptions, và các hàm `logTimeout`, `logAllFailed` do ứng dụng cung cấp. Nếu propagate InterruptedException lên trên, có thể để tầng trên quyết định chính sách, không nhất thiết vừa restore vừa rethrow; việc restore thường cần khi bắt exception mà không ném tiếp.

Các thông điệp ý nghĩa nghiệp vụ:
- `ExecutionException`: **không ứng viên nào thành công**; cause là một thông tin chẩn đoán, không phải tập hợp đủ các exception.
- `TimeoutException`: **deadline của lượt đợi hết** trước success.
- `InterruptedException`: thread **đang gọi** bị interrupt khi chờ; không tự động suy tất cả worker đã dừng.
- `RejectedExecutionException`: executor không nhận một hoặc nhiều tác vụ theo admission policy (shutdown/saturation hoặc không tạo được worker).

Không dùng một `catch(Exception)` rồi trả null trong mọi trường hợp: như vậy làm mất phân biệt giữa dữ liệu null hợp lệ, all-failed và timeout, khiến hệ thống khó hồi phục/chẩn đoán.

## 9. Những task chưa hoàn tất bị hủy ra sao?

Javadoc của `ExecutorService.invokeAny` nêu rằng **upon normal or exceptional return**, các task **chưa completed** được canceled. Với implementation thông dụng, cancellation có thể yêu cầu interrupt worker đang chạy, tương tự `Future.cancel(true)`.

Quan trọng:
- **Cancel request không phải hard stop.** Một CPU-bound loop không kiểm tra interrupt có thể chạy tiếp.
- Một số blocking APIs như `sleep`, `wait`, `BlockingQueue.take`, `CountDownLatch.await` thường phản ứng bằng `InterruptedException`.
- Các I/O/native/third-party APIs không nhất thiết phản ứng nhanh hoặc phản ứng bằng `InterruptedException`; đọc contract cụ thể.
- Tác vụ đã hoàn tất không phải “unfinished”; tác dụng phụ đã commit không được tự rollback.
- Kết quả `invokeAny` đã có không có nghĩa những task thua đã cleanup hoàn toàn.
- Thread pool có thể giữ worker thread tồn tại sau khi một Callable kết thúc; **task finished** khác với **thread terminated**.
- Không nuốt `InterruptedException` rồi lặp vô hạn. Caller yêu cầu hủy mà task không hợp tác có thể làm cạn worker capacity.

Ví dụ anti-pattern:

~~~java
while (true) {
    try {
        queue.take();
    } catch (InterruptedException ignored) {
        // Bỏ tín hiệu hủy, quay lại đợi.
    }
}
~~~

Đây là đoạn minh họa lỗi, không phải mẫu production. Nếu chính sách task là dừng khi interrupt, hãy ném tiếp `InterruptedException` hoặc khôi phục cờ rồi return, kèm cleanup trong `finally`.

## 10. invokeAny không tự đảm bảo mọi task được bắt đầu

Executor có thể có ít workers hơn số Callables. Tasks có thể xếp hàng chờ hoặc được executor submit theo chiến lược khi nó thu thập completions. Ví dụ có 2 nguồn:

- Task A đợi vô hạn một thao tác không có timeout.
- Task B có thể return "OK" ngay khi được chạy.
- Executor chỉ có **một worker** và A chiếm worker trước.

Khi đó B không có worker để chạy, và untimed `invokeAny` có thể **chờ vô thời hạn**. Chỉ vì B “có khả năng trả nhanh” không đủ nếu nó không được schedule.

~~~text
Executor: core=max=1
worker duy nhất -> A đang block
queue -> B đang đợi worker
caller -> invokeAny() đang chờ success
=> Không có success nếu A không thoát để B bắt đầu.
~~~

Đặc biệt nguy hiểm khi task đang chạy trong **chính pool đó** lại gọi `invokeAny` trên pool và chờ các tasks con: worker cha chiếm worker duy nhất, con bị queue, tạo **thread-starvation deadlock**.

Trước khi dùng:
- Pool có đủ worker capacity cho số I/O độc lập mong muốn không?
- Tasks có giữ lock/permit mà các task khác cần để chạy hay không?
- Có dependency cha-con trên cùng pool khiến mọi worker đều chờ nhau không?
- Queue capacity/rejection policy có phù hợp với fan-out không?
- Có timeout, cancellation và deadline cho các nguồn chậm không?

## 11. invokeAny vs invokeAll vs submit

| Công cụ | Return | Khi nào caller kết thúc? | Cancel những task còn dở? |
|---|---|---|---|
| `invokeAny(tasks)` | Một kết quả `T` | Khi có một **normal completion**, hoặc không ai success / bị interrupt | Có, unfinished tasks khi thoát |
| `invokeAny(tasks, timeout, unit)` | Một kết quả `T` | Như trên, hoặc đến timeout | Có, unfinished tasks khi thoát |
| `invokeAll(tasks)` | `List<Future<T>>` | Khi tất cả tasks hoàn tất (success hay exception) | Nếu caller bị interrupt, unfinished tasks bị hủy |
| `invokeAll(tasks, timeout, unit)` | `List<Future<T>>` | Khi tất cả hoàn tất hoặc hết hạn | Unfinished tasks bị hủy khi hết hạn |
| `submit(callable)` | `Future<T>` | Trả Future sau khi submit được nhận | Không tự cancel các tasks khác |

`invokeAll` giữ danh sách Futures theo **thứ tự iterator của collection input**; một Future completed không đồng nghĩa thành công, caller có thể cần `future.get()` và bắt `ExecutionException`.

Với `submit` nhiều lần rồi gọi `futureA.get()` trước, caller có thể kẹt ở A dù B đã thành công; `invokeAny` giải quyết đúng mục tiêu **any-success** mà không bắt người gọi tự quản lý winner. Nếu cần biết **lỗi từng task**, dùng `invokeAll` hoặc các Futures riêng thay vì chỉ một `ExecutionException` tổng quát.

### So với ExecutorCompletionService

`ExecutorCompletionService<T>` cho phép lấy Future theo **thứ tự hoàn tất** thông qua `take()` hoặc `poll()`. Bạn có thể kiểm tra lần lượt kết quả, ghi nhận exception của từng nguồn, chọn theo predicate, hoặc yêu cầu nhiều kết quả. Đổi lại phải tự quản lý danh sách Futures, cancellation, timeout và cleanup. `invokeAny` tiện khi chỉ cần **một normal result**.

### So với CompletableFuture.anyOf

`CompletableFuture.anyOf(a,b,c)` hoàn tất khi một CompletableFuture hoàn tất, **kể cả exceptionally**. Nếu nguồn đầu tiên ném exception, anyOf có thể hoàn thành exceptionally ngay, trong khi `invokeAny` vẫn có thể chờ nguồn khác thành công. anyOf cũng không tự hủy mọi loser theo đúng hợp đồng của `ExecutorService.invokeAny`.

Vì vậy **anyOf không phải bản sao hoàn toàn của invokeAny**. Nếu cần first-success với CompletableFuture, phải thiết kế cơ chế riêng xử lý failure, winner và cancellation.

## 12. Timed invokeAny không đồng nghĩa hard deadline cho toàn hệ thống

Ví dụ:

~~~java
String answer = pool.invokeAny(
        tasks, 250, TimeUnit.MILLISECONDS);
~~~

không hứa:
- Các nguồn remote dừng tức thì ở 250ms.
- Mọi socket/connection đã đóng trước khi caller nhận TimeoutException.
- Side effects của loser đã rollback.
- Scheduler đánh thức caller đúng chính xác ở 250ms theo wall clock.
- Từng task có ngân sách 250ms độc lập.
- Việc trả kết quả sau đó của nguồn đã bị hủy không thể tạo side effect.

Khi nghiệp vụ có deadline tổng 300ms, phải tính thời gian tạo request, queue, remote I/O, xử lý và cleanup. Nếu cần giới hạn remote requests, hãy đặt timeout ở API I/O, không chỉ ở `invokeAny`.

Nếu ứng dụng gọi hàng nghìn lượt invokeAny, mỗi lượt ba replicas, nó có thể **nhân tải downstream lên ba lần**. Đừng dùng hedged racing không giới hạn nếu remote service có quota/concurrency cap. Với nhu cầu chuyên sâu, có thể dùng **delayed hedging** (đợi nguồn chính một thời gian trước khi phát nguồn dự phòng), nhưng `invokeAny` không cung cấp thuật toán delayed hedging sẵn.

## 13. Bài toán nhiều nguồn có side effects

Một nguồn A có thể ghi cơ sở dữ liệu rồi phản hồi chậm; B có thể trả kết quả thành công sớm; sau đó A nhận yêu cầu cancel. Dù caller chỉ nhận B, thao tác ghi của A **có thể đã commit** trước khi bị cancel.

Do đó:
- Ưu tiên tasks **đọc**, pure computation hoặc các attempts idempotent.
- Nếu thao tác ghi bắt buộc, dùng idempotency key và nguồn dữ liệu có protocol chặn duplicate đúng nghiệp vụ.
- Không giả định interruption rollback transaction, email, file, socket hoặc remote request.
- Mọi `Callable` vẫn phải thread-safe với mutable shared objects; `invokeAny` chỉ điều phối completion, không tự khóa business state.
- Nếu nhiều replica có dữ liệu khác nhau, “nguồn trả đầu tiên” không nhất thiết là dữ liệu mới nhất hoặc nhất quán nhất.

Nếu yêu cầu phải chọn version cao nhất, đạt quorum, hoặc ghi nhận mọi kết quả, `invokeAny` **không đủ ngữ nghĩa**; chọn coordination API phù hợp hơn.

## 14. Không nhầm completed, cancelled và termination

Một số trạng thái:

| Sự kiện | Có thể kết luận gì? | Không thể kết luận gì? |
|---|---|---|
| `invokeAny` trả `T` | Có một task normal-complete | Các losers đã cleanup hoàn toàn |
| `TimeoutException` | Không có success trong thời hạn đợi | Mọi worker đã dừng |
| `ExecutionException` | Không task nào success | Tất cả cause chi tiết đã được báo |
| `Future.isCancelled()` | Future có trạng thái cancelled theo contract | Side effects bị đảo ngược |
| `pool.shutdown()` | Pool không nhận task mới theo policy | Mọi task đang chạy đã xong |
| `pool.awaitTermination()` trả true | Pool đã terminated | Các side effects ở hệ thống ngoài đã được rollback |

Nếu cần xác nhận loser đã hoàn tất cleanup, thiết kế latch/handle phù hợp hoặc lifecycle await riêng. Không chặn mãi ở cleanup khi chính operation đã timeout; đặt deadline cho cả phần giải phóng khi hệ thống yêu cầu.

## 15. Quản lý vòng đời executor

Trong Java 17, quản lý `ExecutorService` bằng `try/finally` và shutdown có kiểm soát:

~~~java
ExecutorService pool = Executors.newFixedThreadPool(4);
try {
    String answer = pool.invokeAny(
            candidates, 1, TimeUnit.SECONDS);
    consume(answer);
} finally {
    pool.shutdown();
    try {
        if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                System.err.println("Tasks still running");
            }
        }
    } catch (InterruptedException e) {
        pool.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
~~~

Đây là **đoạn tích hợp**, `candidates` và `consume` do ứng dụng định nghĩa, method phải khai báo hoặc xử lý checked exceptions của `invokeAny`. Nếu pool dùng chung cho nhiều request, **không shutdown sau mỗi request**; owner phải chịu trách nhiệm quản lý toàn bộ lifecycle.

`shutdownNow` **cố gắng** ngắt tasks còn chạy, không kill bắt buộc. Nếu task đang thực hiện I/O không interruptible hoặc nuốt tín hiệu hủy, pool có thể còn worker sau thời gian chờ. Đó là lý do cần theo dõi termination và thiết kế cancellation-aware code.

### Java 21+ và virtual threads

`Executors.newVirtualThreadPerTaskExecutor()` có thể giúp triển khai nhiều tác vụ blocking bằng virtual threads, nhưng không tự hạn chế số request đồng thời tới remote endpoint. Dù executor nào, `invokeAny` vẫn giữ ngữ nghĩa **một normal completion là đủ** và yêu cầu hủy unfinished tasks; việc hủy có hiệu quả hay không vẫn phụ thuộc hoạt động cụ thể. Với Java 17, dùng thread pool nền tảng như ví dụ.

## 16. Lỗi phổ biến khi review code

1. **Nhầm kết quả đầu tiên với task đầu tiên trong List.** Thứ tự collection không bảo đảm winner.
2. **Cho rằng task lỗi sớm làm toàn invokeAny thất bại.** Chỉ khi không còn candidate thành công mới ném `ExecutionException`.
3. **Quên rằng return null cũng là success.** Validation kết quả nghiệp vụ phải do caller/Callable đảm trách.
4. **Cho rằng invokeAny trả Future.** Nó trả trực tiếp `T`.
5. **Cho rằng tất cả Callables chạy song song tức thì.** Pool/queue và chiến lược scheduling quyết định.
6. **Cho rằng invokeAny trả về là mọi loser đã dừng.** Nó yêu cầu hủy nhưng không cưỡng bức thread kết thúc ngay.
7. **Dùng timed invokeAny thay timeout của network client.** Một timeout ở caller không đảm bảo remote I/O đã giải phóng resource.
8. **Lẫn `TimeoutException` với `ExecutionException`.** Hết thời hạn không giống mọi nguồn đã lỗi.
9. **Nuốt `InterruptedException` và tiếp tục.** Worker có thể bỏ qua cancellation.
10. **Dùng các tasks ghi không idempotent.** Losers có thể đã commit.
11. **Dùng pool quá nhỏ với task chờ vô hạn.** Candidate khác có thể không được schedule.
12. **Task cha chờ tasks con trong cùng pool không còn worker rảnh.** Có nguy cơ thread-starvation deadlock.
13. **Dựa vào `ExecutionException.getCause` để suy toàn bộ lỗi nguồn.** Nó không là aggregation đầy đủ.
14. **Sửa collection tasks trong lúc invokeAny chạy.** Javadoc nói kết quả undefined.
15. **Bỏ qua rejection và lifecycle.** Executor đã shutdown có thể không nhận task mới.
16. **Chỉ quan sát throughput mà không đo fan-out.** Racing có thể tăng tải và giảm độ ổn định tổng thể.
17. **Chọn replica thắng dựa trên thời gian nhưng cần dữ liệu mới nhất.** Fastest không đồng nghĩa correct/latest.
18. **Dùng `CompletableFuture.anyOf` như if nó là first-success.** anyOf có thể hoàn tất exceptionally từ nguồn lỗi đầu tiên.
19. **Tuyên bố cancel=true là “kill task”.** Cancellation thường là cơ chế hợp tác dựa trên interrupt.
20. **Không đóng executor mình sở hữu.** Workers/resources có thể bị giữ lâu không cần thiết.

## 17. Checklist trước khi dùng invokeAny

- [ ] Các `Callable<T>` thực sự độc lập và cùng kiểu kết quả không?
- [ ] Một normal result có đủ đáp ứng yêu cầu nghiệp vụ không?
- [ ] Có cần reject `null`/empty/error-status trong Callable không?
- [ ] Collection có không rỗng, không chứa null và không bị mutate trong lúc gọi?
- [ ] Pool có đủ worker để nguồn nhanh có cơ hội chạy không?
- [ ] Có timeout tổng cho lượt gọi khi cần SLA không?
- [ ] Mỗi remote/I/O operation có timeout/cancel phù hợp không?
- [ ] Tasks có phản hồi interrupt và cleanup tài nguyên trong finally không?
- [ ] Các tasks có gây side effects không idempotent không?
- [ ] Có xử lý riêng `ExecutionException`, `TimeoutException` và `InterruptedException` không?
- [ ] Có xử lý tình huống `RejectedExecutionException` không?
- [ ] Ai chịu trách nhiệm shutdown executor; có bị dùng chung không?
- [ ] Có cần thu thập **tất cả lỗi** hoặc chọn theo business predicate khác không?
- [ ] Đã test task thắng với latch/control thay vì dựa vào sleep chưa?
- [ ] Đã đo remote fan-out, queue latency, cancellation effectiveness và tải downstream chưa?

## 18. Bài tập tự kiểm tra

**Câu 1.** Task A ném exception sau 10ms, task B return "OK" sau 20ms. Với executor đủ workers, `invokeAny` nên trả gì?

**Đáp án:** "OK"; failure sớm của A không phải normal result.

**Câu 2.** Hai tasks đều throw, không có normal result. Kết quả?

**Đáp án:** `ExecutionException`.

**Câu 3.** Task A return null, task B return "data". Null có bị bỏ qua không?

**Đáp án:** Không. Null là một normal completion, có thể là winner.

**Câu 4.** Timed invokeAny hết hạn nhưng một worker đang block ở API không interruptible. Worker có chắc đã dừng chưa?

**Đáp án:** Không. Phương thức yêu cầu hủy unfinished tasks nhưng worker có thể tiếp tục; cần timeout/cancel riêng tại tài nguyên.

**Câu 5.** `invokeAny` trả `T` hay `List<Future<T>>`?

**Đáp án:** Trả `T`. `invokeAll` trả `List<Future<T>>`.

**Câu 6.** Caller bị interrupt khi đang đợi invokeAny. Exception gì?

**Đáp án:** `InterruptedException`; unfinished tasks được yêu cầu hủy. Caller cần xử lý interruption theo lifecycle.

**Câu 7.** Tại sao `Executors.newSingleThreadExecutor()` với task đầu block mãi làm invokeAny có thể kẹt?

**Đáp án:** Task tiếp theo có thể chỉ nằm trong queue, không có worker để chạy và tạo success.

**Câu 8.** `Future.get(timeout)` có tự hủy Future khi timeout không?

**Đáp án:** Không. Khác timed invokeAny, Future.get timeout chỉ ảnh hưởng người đợi, cancellation phải gọi riêng nếu muốn.

**Câu 9.** Vì sao không nên dùng invokeAny cho ba tác vụ ghi cùng một giao dịch?

**Đáp án:** Nhiều tasks có thể đã ghi trước khi loser bị hủy, và cancellation không rollback writes. Cần idempotency và transaction protocol.

**Câu 10.** `CompletableFuture.anyOf` với một future fail sớm, một future success muộn có chắc bỏ qua failure sớm không?

**Đáp án:** Không. anyOf có thể hoàn tất exceptionally ngay khi future lỗi đầu tiên hoàn tất; không tương đương first-success của invokeAny.

## 19. Tài liệu chính thức

- **ExecutorService, hai overload invokeAny — Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- **Callable — Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Callable.html
- **Future — Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- **AbstractExecutorService — Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/AbstractExecutorService.html
- **ExecutorCompletionService — Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorCompletionService.html
- **CompletableFuture — Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- **Thread interrupt — Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html

**Điểm cốt lõi:** `invokeAny` lấy **một kết quả từ một Callable hoàn tất bình thường**, bỏ qua failures sớm khi còn cơ hội thành công, và **yêu cầu hủy các tasks chưa hoàn tất** khi trả về hoặc ném exception. Nếu mọi tác vụ thất bại, nhận `ExecutionException`; nếu overload timed hết hạn trước success, nhận `TimeoutException`. Kết quả null vẫn là thành công kỹ thuật. Để ứng dụng đúng, phải thiết kế **business validation, executor capacity, deadline, interruption, idempotency và cleanup**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-008">ExecutorCompletionService nhận kết quả theo thứ tự hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# ExecutorCompletionService nhận kết quả theo thứ tự hoàn tất trong Java

Khi gửi nhiều tác vụ độc lập vào một `ExecutorService`, chúng có thể bắt đầu, kết thúc, thất bại hoặc bị hủy ở những thời điểm khác nhau. Nếu chương trình nhận các `Future` rồi lần lượt gọi `get()` theo **thứ tự submit**, nó có thể bị kẹt ở tác vụ đầu tiên còn chậm, dù những tác vụ submit sau đã hoàn thành. `ExecutorCompletionService<V>` giải quyết chính bài toán **thu thập kết quả theo thứ tự các tác vụ hoàn tất và được đưa vào completion queue**, thay vì thứ tự gửi.

Ví dụ: ba truy vấn A, B, C được gửi theo thứ tự A→B→C, nhưng B xong trước, C xong thứ hai và A xong sau cùng. Với `ExecutorCompletionService`, một consumer có thể lấy kết quả B, C, A và xử lý ngay khi từng kết quả sẵn sàng. Không cần chờ A trước khi xem kết quả B.

Bài Knowledge này thuộc phần **Future & Scheduling trong Java Concurrency nói chung**. Nội dung đi từ mô hình API, so sánh cách dùng Future, ví dụ Java 17+ có stdout xác định, xử lý lỗi/hủy/timeout, đến lựa chọn cấu hình executor và lưu ý vận hành. Những API tích hợp riêng của Spring nằm ngoài phạm vi.

## 1. Bài toán head-of-line blocking khi duyệt Future theo thứ tự submit

Một cách làm thông dụng là submit nhiều tác vụ và lưu các Future trong list:

~~~java
List<Future<String>> futures = new ArrayList<>();
futures.add(executor.submit(taskA));
futures.add(executor.submit(taskB));
futures.add(executor.submit(taskC));

for (Future<String> future : futures) {
    System.out.println(future.get());
}
~~~

Đây là **mảnh minh họa**, giả định `executor` và ba `Callable<String>` đã tồn tại; method chứa nó phải xử lý `InterruptedException`, `ExecutionException`.

Vòng lặp đọc Future theo thứ tự list. Khi `taskA` chậm:

~~~text
Submit:         A --------> B --------> C
Hoàn tất thật:  B --------> C --------> A
get(list):      chờ A................................
                [in A] [in B] [in C]
~~~

Tác vụ B, C có thể đã làm xong nhưng kết quả chưa được tiêu thụ. Đây là **head-of-line blocking ở tầng xử lý kết quả**: caller tự áp đặt thứ tự tiêu thụ không cần thiết. Không phải executor bắt buộc chạy task theo thứ tự đó.

Nếu bài toán cần xử lý kết quả ngay khi từng tác vụ hoàn thành (fan-out/fan-in độc lập), completion service là công cụ thích hợp. Nếu cần kết quả được xuất **đúng vị trí ban đầu** theo request, thứ tự submit có thể lại là yêu cầu hợp lệ và cần sắp xếp ở tầng presentation.

## 2. ExecutorCompletionService là gì?

`java.util.concurrent.ExecutorCompletionService<V>` triển khai interface `CompletionService<V>`. Nó kết hợp:

1. **Một Executor đã có sẵn:** chịu trách nhiệm thực thi các task, định nghĩa số worker, queue, chính sách từ chối, vòng đời và tài nguyên.
2. **Một completion queue:** chứa các `Future<V>` của task đã được xử lý xong theo giao thức completion service.
3. **API submit và nhận kết quả:** `submit(...)` gửi work; `take()` hoặc `poll()` lấy completed Future ra khỏi queue.

Tạo đối tượng:

~~~java
ExecutorService executor = Executors.newFixedThreadPool(3);
CompletionService<String> completion =
        new ExecutorCompletionService<>(executor);
~~~

Đây là mảnh mã minh họa, cần import `ExecutorService`, `Executors`, `CompletionService`, `ExecutorCompletionService`. `ExecutorCompletionService` không tạo thêm pool worker bí mật. Nó sử dụng executor được truyền vào.

### 2.1 Sơ đồ luồng dữ liệu

~~~text
Producer / submitter
       |
       | submit(callable A)
       | submit(callable B)
       | submit(callable C)
       v
+----------------------------+
| Executor / worker threads  |
|    A: chậm                 |
|    B: nhanh                |
|    C: trung bình           |
+----------------------------+
       |
       | mỗi task hoàn tất -> completed Future
       v
+----------------------------+
| Completion queue           |
|  [Future(B)] [Future(C)]   |
|                   ... [A]  |
+----------------------------+
       |
       | take()/poll()
       v
Consumer -> Future.get() -> value hoặc exception
~~~

**Đừng nhầm:** completion queue chứa **Future đã hoàn tất**, không nhất thiết chứa thành công value. Task ném exception vẫn tạo completed Future để consumer lấy; lỗi nghiệp vụ được phát hiện khi gọi `get()`.

Cũng đừng nhầm completion queue với **work queue của ThreadPoolExecutor**. Work queue chứa tác vụ chưa được worker lấy chạy; completion queue chứa các Future đã xong, chờ consumer nhận. Hai queue có vai trò và giới hạn tài nguyên khác nhau.

## 3. API cốt lõi và hợp đồng blocking

| API | Ý nghĩa | Có chờ không? |
| --- | --- | --- |
| `submit(Callable<V>)` | Gửi một task trả value hoặc exception, nhận `Future<V>` dùng để theo dõi/hủy riêng task đó. | Submission bản thân không chờ completion; có thể bị reject bởi executor. |
| `submit(Runnable, V result)` | Gửi Runnable, dùng result định sẵn nếu Runnable hoàn thành bình thường. | Không chờ completion. |
| `take()` | Lấy và loại khỏi completion queue **một Future đã hoàn tất**. | **Chờ vô thời hạn** nếu queue chưa có phần tử, trừ khi bị interrupt. |
| `poll()` | Thử lấy ngay một completed Future. | Không chờ; trả `null` nếu queue đang trống. |
| `poll(timeout, unit)` | Đợi có giới hạn để lấy một completed Future. | Chờ tới khi có phần tử hoặc deadline; trả `null` khi hết hạn. |
| `Future.get()` | Lấy giá trị hoặc exception của Future được lấy ra. | Với completed Future từ queue, bình thường không còn phải chờ task chạy; vẫn cần xử lý lỗi/hủy/interruption. |
| `Future.cancel(true)` | Yêu cầu hủy một task qua Future đã lưu. | Không phải lệnh kill đồng bộ; kết quả/timing còn phụ thuộc task và executor. |

`take()` và timed `poll()` có thể ném `InterruptedException`. `Future.get()` có thể ném `ExecutionException` nếu callable thất bại, `CancellationException` nếu Future bị hủy; phải phân biệt với `InterruptedException` của consumer đang chờ.

### 3.1 Chỉ trả completion, không đảm bảo thứ tự submit

Nếu A xong sau B, thông thường Future của B xuất hiện trong completion queue trước A. Nhưng khi nhiều task hoàn thành gần như đồng thời, **không có hợp đồng thứ tự tổng thể theo nanosecond/dòng log** để người dùng dự đoán tie-break. Thứ tự consumer quan sát là thứ tự các Future được enqueue để lấy.

Đặc biệt, scheduler có thể thay đổi thứ tự chạy giữa các lần thực thi. Một chương trình không được assert cứng “B luôn nhanh hơn A” nếu chỉ dùng `Thread.sleep` để tạo tốc độ giả định.

### 3.2 Không tự biết khi nào toàn bộ nhóm đã xong

`CompletionService` **không có phương thức** kiểu `closeSubmissions()` hoặc `takeAll()` biết chính xác số task của một lô. Consumer phải quản lý:

- Bao nhiêu task đã **submit thành công**;
- Bao nhiêu completion đã nhận;
- Khi nào ngừng submit hoặc chuyển sang lô mới;
- Cách xử lý task bị reject, cancel, timeout hoặc producer ngừng giữa chừng.

Mẫu thông dụng là đếm `n` lần submit rồi gọi `take()` **đúng n lần**, với điều kiện tất cả task được chấp nhận và sẽ thật sự hoàn tất theo contract. Nếu một callable block mãi, `take()` có thể block mãi; cần timeout/cancellation policy.

## 4. Ví dụ Java 17+: chứng minh kết quả nhận theo thứ tự hoàn tất

Để tránh ngẫu nhiên do scheduler, ví dụ dùng `CountDownLatch` làm ba cổng. Main **submit theo A→B→C**, sau đó lần lượt mở cổng B, C, A và nhận **một completion trước khi mở cổng tiếp theo**. Nhờ vậy stdout xác định mà không cần `sleep()`.

Tạo file `CompletionOrderDemo.java`:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class CompletionOrderDemo {
    private static Callable<String> job(
            String name, CountDownLatch started, CountDownLatch gate) {
        return () -> {
            started.countDown();
            gate.await();
            return name;
        };
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CompletionService<String> cs = new ExecutorCompletionService<>(pool);
        CountDownLatch allStarted = new CountDownLatch(3);
        CountDownLatch a = new CountDownLatch(1);
        CountDownLatch b = new CountDownLatch(1);
        CountDownLatch c = new CountDownLatch(1);

        try {
            cs.submit(job("A", allStarted, a));
            cs.submit(job("B", allStarted, b));
            cs.submit(job("C", allStarted, c));
            allStarted.await();

            b.countDown();
            System.out.println("Completed: " + cs.take().get());

            c.countDown();
            System.out.println("Completed: " + cs.take().get());

            a.countDown();
            System.out.println("Completed: " + cs.take().get());
        } finally {
            a.countDown();
            b.countDown();
            c.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac CompletionOrderDemo.java
java CompletionOrderDemo
~~~

Kết quả bình thường, không có interruption bên ngoài:

~~~text
Completed: B
Completed: C
Completed: A
~~~

### 4.1 Vì sao không thể ra thứ tự khác?

- Pool có **ba worker** nên ba task đều có thể đi tới `allStarted.countDown()` rồi chờ cổng riêng.
- `allStarted.await()` của main đợi tới khi cả ba task đã đạt mốc.
- B được phép thoát gate đầu tiên; A và C vẫn bị chặn. `cs.take()` chỉ có thể nhận completion B ở bước này.
- Chỉ **sau khi** main đã lấy B, nó mới mở gate C; A vẫn bị chặn, nên completion kế tiếp là C.
- Tương tự, gate A chỉ mở cuối cùng.
- `cs.take()` trả Future hoàn tất; `get()` trả value A/B/C. `finally` mở tất cả gate khi có lỗi ở main, rồi yêu cầu executor shutdown để tránh để worker chờ vô hạn trong ví dụ.

**`allStarted` và các gate chỉ để thử nghiệm thứ tự:** ứng dụng thực tế thường chạy các tác vụ I/O/CPU độc lập và không chủ động áp thứ tự completion. Lợi ích là consumer có thể xử lý *bất kỳ task nào hoàn tất trước*, không bị ép chờ task đầu trong list.

## 5. Đọc kết quả theo submission order so với completion order

Hai kiểu consumer khác nhau:

### 5.1 Theo submission order

~~~java
List<Future<Result>> futures = new ArrayList<>();
for (Request request : requests) {
    futures.add(executor.submit(() -> call(request)));
}

for (Future<Result> future : futures) {
    consume(future.get());
}
~~~

Đây là **mảnh code minh họa** cần class `Request`/`Result`, `call`, `consume` và import thích hợp. `get()` trên Future đầu tiên chậm sẽ làm consumer chờ, dù Future thứ hai đã hoàn tất.

### 5.2 Theo completion order

~~~java
CompletionService<Result> cs =
        new ExecutorCompletionService<>(executor);
int submitted = 0;

for (Request request : requests) {
    cs.submit(() -> call(request));
    submitted++;
}

for (int i = 0; i < submitted; i++) {
    Future<Result> completed = cs.take();
    consume(completed.get());
}
~~~

Đây cũng là mảnh minh họa. Điểm khác biệt là consumer đợi `cs.take()` lấy **bất kỳ Future đã hoàn tất**; không phụ thuộc request nào được gửi trước.

### 5.3 Khi nào vẫn phải giữ thứ tự đầu vào?

Ví dụ danh sách kết quả hiển thị đúng theo chỉ số dòng yêu cầu. Completion order vẫn có thể dùng cho **xử lý nội bộ**, nhưng phải mang theo ID/index:

~~~java
record IndexedResult(int index, String value) {}

cs.submit(() -> new IndexedResult(index, query()));
~~~

Đây là mảnh code, giả định `cs` có generic type `IndexedResult`, `index` là biến local effectively final cho từng task, `query()` tồn tại. Consumer lưu từng result vào slot tương ứng rồi mới xuất theo order. **Completion order và presentation order là hai quyết định khác nhau**.

## 6. Exception không làm mất một Future đã hoàn tất

Task có thể ném exception, nhưng việc đó không có nghĩa completion service “chỉ trả success”. `submit(Callable)` thường giữ exception trong Future; consumer lấy Future ra rồi gọi `get()` mới biết task thành công hay thất bại.

Tạo file `CompletionFailureDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class CompletionFailureDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CompletionService<String> cs = new ExecutorCompletionService<>(pool);
        CountDownLatch allowSuccess = new CountDownLatch(1);

        try {
            cs.submit(() -> {
                throw new IllegalStateException("source unavailable");
            });
            cs.submit(() -> {
                allowSuccess.await();
                return "OK";
            });

            // Success task vẫn chờ gate: failure phải được nhận trước.
            for (int i = 0; i < 2; i++) {
                Future<String> completed = cs.take();
                try {
                    System.out.println("Success: " + completed.get());
                } catch (ExecutionException e) {
                    System.out.println("Failure: "
                            + e.getCause().getClass().getSimpleName());
                    allowSuccess.countDown();
                }
            }
        } catch (ExecutionException e) {
            throw new AssertionError(e);
        } finally {
            allowSuccess.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Chạy JDK 17+:

~~~bash
javac CompletionFailureDemo.java
java CompletionFailureDemo
~~~

Stdout trong trường hợp bình thường:

~~~text
Failure: IllegalStateException
Success: OK
~~~

**Vì sao?** Task thành công phải đợi `allowSuccess`; task thất bại không cần gate nên Future thất bại xuất hiện trước. Consumer `take()` được Future thất bại, và `get()` ném `ExecutionException` chứa `IllegalStateException` ở `getCause()`. Sau khi bắt failure, main mở gate cho task còn lại.

Lưu ý: đây là mẫu dạy học tập trung vào exception. Trong code production nên có **nhánh xử lý lỗi theo từng task**, không dùng một catch `ExecutionException` bao trùm cả vòng lặp rồi bỏ qua các completion còn lại. Nếu một task có thể mắc kẹt vô hạn, `take()` cũng có thể chờ mãi, cần deadline/cancellation.

### 6.1 Không nhầm exception ở submit và exception ở get

- **RejectedExecutionException** có thể xảy ra ngay tại `submit()` nếu executor không chấp nhận task; khi đó không có một submission thành công tương ứng để consumer chờ.
- **ExecutionException** xảy ra ở `get()` nếu task đã chạy và ném exception.
- **CancellationException** ở `get()` khi Future ở trạng thái cancelled.
- **InterruptedException** ở consumer khi đang chờ `take()`/`get()`; đây là đường cancellation của caller, không phải lỗi của callable.

Bộ đếm số completion cần đợi chỉ nên tăng cho các submission **đã được chấp nhận**. Nếu đếm số request đầu vào nhưng có request bị reject, consumer có thể đợi một “completion chưa từng được đăng ký”.

## 7. take(), poll() và timed poll: chọn theo yêu cầu chờ

`take()` phù hợp khi biết ít nhất một task được chấp nhận và dự kiến sẽ hoàn thành trong thời gian hữu hạn. Nó **block vô hạn** nếu completion queue trống; điều đó có thể đến từ task chậm, task bị kẹt hoặc không có task nào được submit.

`poll()` dùng khi consumer phải làm việc khác ngay nếu chưa có kết quả:

~~~java
Future<String> ready = cs.poll();
if (ready != null) {
    use(ready.get());
}
~~~

Đây là mảnh minh họa, giả định `cs`/`use` có sẵn và method xử lý `InterruptedException`, `ExecutionException`.

`poll(timeout, unit)` hỗ trợ giới hạn thời gian đợi một completion:

~~~java
Future<String> ready = cs.poll(200, TimeUnit.MILLISECONDS);
if (ready == null) {
    onTimeout();
} else {
    use(ready.get());
}
~~~

Đây cũng là mảnh minh họa. Trả `null` là **chưa lấy được completion trong cửa sổ chờ**, không chứng minh mọi task đã chết hoặc executor không hoạt động. Timed poll **không tự cancel** task còn đang chạy.

### 7.1 Timeout của collector khác timeout của từng task

Một consumer có thể đợi tối đa 2 giây cho **cả lô**, trong khi mỗi task có thời gian xử lý khác nhau. Nếu gọi `poll(2, SECONDS)` **lặp lại cho từng kết quả**, tổng thời gian có thể lớn hơn 2 giây nhiều lần.

Hãy chọn một **deadline tổng** và tính thời gian còn lại ở mỗi lần poll. Không reset toàn bộ timeout ở mỗi vòng.

### 7.2 Minh họa deadline tổng

~~~java
long deadline = System.nanoTime() + timeoutNanos;

for (int received = 0; received < submitted; received++) {
    long left = deadline - System.nanoTime();
    if (left <= 0L) {
        break;
    }
    Future<Result> result = cs.poll(left, TimeUnit.NANOSECONDS);
    if (result == null) {
        break;
    }
    handle(result.get());
}
~~~

Đây là mảnh minh họa, giả định timeoutNanos có giá trị hợp lý, `submitted` là số task được chấp nhận và `cs`/`handle` có kiểu đúng. `System.nanoTime()` thích hợp để đo khoảng thời gian trôi qua, không phải timestamp wall-clock. Với deadline gần giới hạn `long`, cần thiết kế tính toán tránh overflow; ở đây chỉ minh họa cách không cấp lại full timeout mỗi lần.

Khi deadline hết, chương trình **phải có chính sách xử lý những task còn lại**: cho tiếp tục chạy và thu dọn sau, yêu cầu hủy, hoặc trả partial results. Chỉ thoát vòng poll mà không biết ai sở hữu những task còn chạy là một lỗi lifecycle.

## 8. First successful result: lấy kết quả hữu ích đầu tiên

Một tình huống thực tế: gửi cùng yêu cầu tới nhiều nguồn dữ liệu, cần **kết quả thành công đầu tiên**, không phải Future submit đầu tiên. `CompletionService` hữu ích vì có thể:

1. Submit tất cả candidate tasks.
2. Nhận completed Future sớm nhất.
3. Nếu `get()` thất bại, tiếp tục đợi một completion khác.
4. Nếu thành công, lưu kết quả rồi **yêu cầu hủy các task còn lại**.
5. Nếu mọi task thất bại, trả một failure được định nghĩa rõ.

**Không được coi “completion đầu tiên” luôn là “success đầu tiên”**. Task lỗi rất nhanh có thể đến trước mọi task thành công.

### 8.1 Java 17+: first-success pattern chạy được

Tạo file `FirstSuccessCompletionDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FirstSuccessCompletionDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CompletionService<Integer> cs = new ExecutorCompletionService<>(pool);
        CountDownLatch blocker = new CountDownLatch(1);
        List<Future<Integer>> submitted = new ArrayList<>();

        try {
            submitted.add(cs.submit(() -> {
                throw new IllegalStateException("source A failed");
            }));
            submitted.add(cs.submit(() -> 42));
            submitted.add(cs.submit(() -> {
                blocker.await();
                return -1;
            }));

            Integer answer = null;
            int failures = 0;
            for (int i = 0; i < submitted.size(); i++) {
                Future<Integer> completed = cs.take();
                try {
                    answer = completed.get();
                    break;
                } catch (ExecutionException e) {
                    failures++;
                }
            }

            if (answer != null) {
                System.out.println("First success: " + answer);
            } else {
                System.out.println("All failed: " + failures);
            }
        } finally {
            for (Future<Integer> future : submitted) {
                future.cancel(true);
            }
            blocker.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac FirstSuccessCompletionDemo.java
java FirstSuccessCompletionDemo
~~~

Stdout bình thường:

~~~text
First success: 42
~~~

Ở đây một task lỗi, một task trả 42 và một task bị block trên latch. Bất kể task lỗi hay task 42 hoàn thành trước, consumer tiếp tục qua error và dừng ngay khi lấy được value 42. `finally` yêu cầu hủy những task chưa xong và mở blocker để không rò thread.

**Giới hạn:** `future.cancel(true)` là yêu cầu hủy; nếu task thật bỏ qua interruption, nó có thể tiếp tục. Với hệ thống thật, cần có deadline tổng để first-success không chờ vô hạn khi không nguồn nào thành công, đồng thời xử lý `CancellationException` nếu candidate bị hủy bởi bên khác.

Nếu “kết quả đầu tiên” có ý nghĩa **first response bao gồm error**, không loop tìm success. Hợp đồng nghiệp vụ phải quyết định.

## 9. Hủy tác vụ và completion queue: những điều dễ hiểu sai

`ExecutorCompletionService.submit` trả một `Future<V>` để caller lưu và chủ động hủy. `Future.cancel(true)` có thể đánh dấu Future cancelled, và khi `get()` trên Future đó sẽ nhận `CancellationException`.

Tuy nhiên cần phân biệt:

- **Future trạng thái cancelled**;
- **callable thực sự đã thôi chạy hay chưa**;
- **Future đã được đặt vào completion queue hay chưa**.

Các trạng thái không nhất thiết diễn ra tức thì cùng thời điểm. Đặc biệt `ExecutorCompletionService` dùng wrapper để enqueue completed task khi wrapper hoàn tất; đừng giả định việc gọi `cancel()` ở một thread lập tức tạo đúng một completion ngay lập tức có sẵn qua `take()` trong mọi race/hệ executor.

Khi caller muốn chấm dứt sớm và không còn tiêu thụ mọi completion, lưu danh sách Future do mình submit để cancel/cleanup, thay vì phụ thuộc vào số phần tử của completion queue để suy ra tất cả user task đã dừng.

### 9.1 Xử lý CancellationException khi get

~~~java
try {
    Result value = completed.get();
    consume(value);
} catch (CancellationException e) {
    recordCancelled();
} catch (ExecutionException e) {
    recordFailure(e.getCause());
}
~~~

Đây là mảnh minh họa trong method đã xử lý `InterruptedException`; `Result` và các helper tự định nghĩa. `CancellationException` là unchecked, **không** bị bọc trong `ExecutionException`. Không nên dùng `catch (Exception)` chung và ghi nhãn mọi tình huống là “task failed”; việc hủy có semantics riêng.

### 9.2 Không chặn consumer bằng công việc xử lý quá lâu

`take()` trả completion theo thứ tự queue, nhưng nếu consumer xử lý mỗi kết quả rất lâu trong cùng thread, các completed Future khác sẽ tồn đọng. Completion service không tự song song hóa **consumer logic**. Nếu cần parse/aggregate nặng, xem xét pipeline khác hoặc executor riêng, nhưng phải kiểm soát backpressure và ordering.

## 10. Completion queue mặc định và rủi ro bounded queue

Constructor thường gặp:

~~~java
CompletionService<Result> cs =
        new ExecutorCompletionService<>(executor);
~~~

Dùng completion queue mặc định kiểu `LinkedBlockingQueue` không có giới hạn thực tế theo số lượng task của một lô nhỏ; cần lưu ý khi submit nhiều task nhanh mà consumer không tiêu thụ.

Có overload nhận queue do caller cung cấp:

~~~java
new ExecutorCompletionService<Result>(executor, completionQueue);
~~~

Nhưng **đừng mặc định truyền một `ArrayBlockingQueue` nhỏ** để “thêm backpressure”. Theo hợp đồng API, completion queue được xem là **unbounded** đối với nhu cầu enqueue completed Future; khi task hoàn tất, service dùng `add` chứ không chờ `put`. Nếu queue do caller cung cấp có capacity hữu hạn và đã đầy, việc thêm completed task có thể thất bại khiến completed Future **không còn lấy được qua completion service** dù task đã chạy xong. Đây là một failure mode quan trọng.

Nếu thật sự cần backpressure, hãy:

- **Giới hạn số task đang được submit/in-flight**, ví dụ dùng `Semaphore` với ownership rõ.
- Tiêu thụ kết quả đủ nhanh hoặc dùng consumer worker phù hợp.
- Cấu hình giới hạn queue đầu vào của `ThreadPoolExecutor` cùng rejection policy có chủ đích, nhưng nhớ đây là **work queue**, khác completion queue.
- Không thay queue của completion service bằng bounded queue một cách tùy tiện.

**Phân biệt hai tầng giới hạn tài nguyên:** work queue giới hạn số task đang chờ được chạy; in-flight limit giới hạn số task chưa được thu hồi completion; completion queue chứa các Future đã xong. Chỉ giới hạn work queue không bảo đảm completed results sẽ được tiêu thụ đủ nhanh.

## 11. Chọn ExecutorService và thread pool

`ExecutorCompletionService` không cung cấp scheduling policy mới. Nó dùng executor được đưa vào:

| Executor | Đặc tính | Phù hợp / hạn chế |
| --- | --- | --- |
| `newFixedThreadPool(n)` | Có tối đa n worker hoạt động; task dư được queue. | Dễ minh họa và kiểm soát concurrency nhưng work queue mặc định có thể tích tụ không giới hạn thực tế. |
| `ThreadPoolExecutor` cấu hình riêng | Có core/max size, work queue, thread factory, rejection policy. | Phù hợp khi cần giới hạn concurrency, queue và saturation policy rõ. |
| `newSingleThreadExecutor()` | Một worker thực thi task tuần tự theo queue. | Completion order thường gắn với execution order của task; không có lợi ích fan-out song song. |
| `newCachedThreadPool()` | Có thể tạo thêm worker theo nhu cầu. | Dễ tăng tài nguyên khi submit rất nhiều blocking tasks; phải giới hạn tải. |
| `newVirtualThreadPerTaskExecutor()` (Java 21+) | Một virtual thread cho mỗi task. | Có thể phù hợp tác vụ chờ I/O, nhưng phải kiểm soát số yêu cầu/tài nguyên bên ngoài; không phải API Java 17. |

Ví dụ Java 17 trong bài dùng fixed pool để tránh phụ thuộc API mới. `newVirtualThreadPerTaskExecutor()` chỉ có ở **Java 21+**; không chạy dòng đó trong ví dụ Java 17.

### 11.1 Bài toán pool starvation

Nếu các task trong cùng một pool nhỏ **chờ completion của những task khác đang nằm chờ được chạy trong chính pool**, có thể xảy ra thread-pool starvation. Ví dụ pool chỉ có một worker, task A submit B vào chính pool rồi gọi `Future.get()` đợi B, trong khi worker đang bị A chiếm. B không có worker để chạy.

`CompletionService` không chữa dependency deadlock/starvation của task. Nó chỉ thu thập completion. Thiết kế đồ thị phụ thuộc và capacity của pool vẫn là trách nhiệm người dùng.

## 12. CompletionService và ExecutorService.invokeAll()

`ExecutorService.invokeAll(collection)` hữu ích khi cần chạy một tập task và đợi **cả tập** hoàn tất hoặc hết timeout, rồi nhận các Future theo **thứ tự iterator đầu vào**. `invokeAny(collection)` hữu ích khi muốn một kết quả thành công sớm và không cần quản lý loop completion thủ công.

| Yêu cầu | API thường phù hợp |
| --- | --- |
| Thu thập kết quả **từng cái ngay khi xong** | `ExecutorCompletionService.take()/poll()` |
| Chạy một tập task rồi chờ kết quả tất cả, trả Future theo thứ tự input | `ExecutorService.invokeAll()` |
| Cần một kết quả thành công sớm | `ExecutorService.invokeAny()` hoặc CompletionService để tùy chỉnh policy |
| Một task với một Future | `ExecutorService.submit()` + `Future.get()` |
| Ghép pipeline bất đồng bộ và transformation | `CompletableFuture` combinators |
| Kết quả cần stream/event-driven nonblocking phức tạp | `CompletableFuture` hoặc framework/reactive API có hợp đồng phù hợp |

`invokeAll` không tự đưa kết quả theo thứ tự hoàn tất chỉ vì các Future có thể đã done. `CompletionService` thuận lợi khi caller cần xử lý **partial results liên tục**.

`invokeAny` có logic hủy những candidate còn lại theo hợp đồng API và trả một kết quả thành công; nếu nhu cầu chính xác như vậy, nó có thể đơn giản hơn việc viết lại toàn bộ thuật toán. CompletionService phù hợp khi cần thêm metrics, xử lý từng lỗi, fallback, partial collection hoặc custom deadline.

### 12.1 So với CompletableFuture

`CompletableFuture.anyOf(...)` hoàn tất khi **bất kỳ future** nào hoàn tất, kể cả exceptional completion; không nhất thiết là *first success*. Muốn xử lý một stream nhiều completion có state, ordering và backpressure thì cần thiết kế pipeline/callback phù hợp.

`CompletionService` cho một API **blocking pull-based**: consumer chủ động `take()`/`poll()`. `CompletableFuture` thiên về callback/composition. Chọn theo nhu cầu orchestration và cách ứng dụng xử lý thread khi chờ.

## 13. Memory consistency và thread safety

Các primitive trong `java.util.concurrent` có các **memory-consistency effects** được tài liệu mô tả. Với `ExecutorService`, những hành động của thread **trước khi submit** task happens-before các hành động trong task; những hành động của task happens-before hành động **sau khi caller nhận kết quả qua `Future.get()`** theo hợp đồng.

Điều này quan trọng khi task tính một result hoặc cập nhật trạng thái trước khi kết thúc. Tuy nhiên:

- `CompletionService.take()` lấy một completed Future; `get()` là cách rõ ràng để thu giá trị/error và nhận hợp đồng memory-consistency của Future.
- Một object mutable được đưa vào task rồi bị **caller sửa tiếp trong lúc task đang chạy** vẫn có thể race. Submit không đóng băng object.
- Một result object mutable được publish rồi bị worker/thread khác sửa không đồng bộ sau completion vẫn cần ownership/synchronization riêng.
- Completion order **không** khiến các task chia sẻ mutable state tự thread-safe.
- Nếu các task update cùng một invariant, cần `synchronized`, atomic aggregate hoặc cấu trúc concurrent theo đúng scope; completion service chỉ quản lý thu kết quả.

### 13.1 Task thuần và snapshot immutable

Một cách đơn giản để giảm race là truyền input immutable/snapshot vào mỗi callable và trả output immutable/result value, rồi aggregate kết quả trong một consumer thread. Nếu kết quả được xử lý trên nhiều consumer thread, cần bảo vệ state tổng hợp theo contract.

## 14. Thu thập nhiều loại kết quả và gắn metadata

Vì completion queue chỉ trả Future, caller có thể không biết ngay kết quả thuộc request nào nếu Callable trả mỗi một giá trị thuần. Nên **gói metadata vào result**:

~~~java
record QueryResult(String sourceId, long elapsedNanos, String value) {}

cs.submit(() -> {
    long start = System.nanoTime();
    String value = fetchFromSource();
    return new QueryResult(
            sourceId, System.nanoTime() - start, value);
});
~~~

Đây là mảnh minh họa, giả định `cs` nhận `QueryResult`, `sourceId` là effectively final, `fetchFromSource` tồn tại. Nhờ metadata, consumer có thể:

- Log nguồn nào xong trước;
- Lưu results theo ID vào map;
- Tính latency từng nguồn;
- Retry có chọn lọc;
- Tổng hợp partial success/failure.

Nếu task lỗi trước khi trả result, metadata trong result không tồn tại. Muốn gắn lỗi với request cụ thể, có thể giữ mapping `Future<?> → RequestId` ở phía submitter (cần map thread-safe nếu collector dùng thread khác), hoặc cho callable trả một `Outcome` chứa success/error thay vì ném exception. **Không biến mọi failure thành success im lặng**; contract cần phân biệt hai trạng thái.

### 14.1 Thứ tự nhận khác thứ tự hiển thị

Consumer có thể xử lý completion B trước A để cập nhật tiến độ, nhưng cuối cùng trả list kết quả theo A, B, C. Tách **order of processing** và **order of presentation** giúp tối ưu phản hồi sớm mà vẫn giữ giao diện/hợp đồng đúng.

## 15. Hạn chế outstanding tasks và thiết kế backpressure

Một hệ thống có hàng triệu request không thể luôn submit tất cả cùng lúc rồi mới bắt đầu lấy completion. Nó có thể giữ quá nhiều Future/callable, lấp đầy work queue hoặc gây áp lực bộ nhớ.

Mô hình **sliding window**:

~~~text
MAX_IN_FLIGHT = 20
submit tối đa 20 task
mỗi khi nhận một completion:
    xử lý outcome
    nếu còn input thì submit thêm 1 task
lặp đến khi:
    hết input và đã nhận hết in-flight completions
~~~

Đây là **mã giả**, không phải Java chạy độc lập. Cần quản lý chính xác biến `inFlight` và các nhánh submit failure/cancellation:

1. Tăng `inFlight` sau khi submit **thành công**.
2. Khi lấy completed Future qua `take/poll` và đã quyết định outcome, giảm `inFlight`.
3. Chỉ submit thêm khi còn input và không vượt giới hạn.
4. Khi cancellation/timeout, gửi yêu cầu hủy các task chưa hoàn tất và quản lý ownership/lifecycle.
5. Nếu producer hoặc consumer có nhiều thread, dùng synchronization để giữ đúng số in-flight.

Không nhất thiết dùng completion queue bounded để kiểm soát outstanding tasks; thường an toàn hơn để **completion queue nhận được mọi completed Future**, còn cửa sổ in-flight được giới hạn **trước khi submit**.

## 16. Interrupt, cancel và shutdown đúng cách

Một collector gọi `take()` có thể bị interrupt. Nếu method có thể khai báo `throws InterruptedException`, thường truyền lên để caller quyết định cancellation. Trong top-level worker `Runnable.run()`, có thể restore interrupted status rồi thoát:

~~~java
try {
    Future<Result> future = cs.take();
    consume(future.get());
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Yêu cầu hủy outstanding tasks và thoát đúng policy.
}
~~~

Đây là mảnh code minh họa; `Result` và helpers đã được giả định. Nếu consumer ngừng sớm, những task vẫn đang chạy phải có **owner** quản lý. Không bỏ hết Future references rồi để executor chạy mãi mà không có shutdown policy.

### 16.1 Mẫu cleanup

~~~java
List<Future<Result>> futures = new ArrayList<>();
try {
    // Submit/collect theo contract của lô.
} finally {
    for (Future<Result> future : futures) {
        if (!future.isDone()) {
            future.cancel(true);
        }
    }
    executor.shutdownNow();
}
~~~

Đây là mảnh code minh họa. `shutdownNow()` là best effort và không đảm bảo mọi task ngừng ngay; nếu executor thuộc một thành phần khác, **không tự shutdown executor dùng chung**. Hãy đặt lifecycle owner rõ ràng: ai tạo pool thì thường chịu trách nhiệm đóng nó, còn thư viện nhận executor từ caller không nên lén shutdown.

Một task bỏ qua interruption hoặc kẹt trên I/O không có timeout có thể không chịu dừng. Cần timeout/resource-specific cancellation và observability phù hợp.

## 17. Lỗi thường gặp và cách khắc phục

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Submit bằng ECS nhưng lưu Future rồi get theo list đầu vào. | Vẫn head-of-line blocking. | Dùng `take()/poll()` trên completion service để thu completion order. |
| Coi completion order luôn là đúng thứ tự theo thời gian thực. | Assertion flaky khi gần đồng thời. | Không dự đoán tie-break; dùng latch trong test cần thứ tự xác định. |
| Coi completed Future là success. | `get()` có thể ném ExecutionException/CancellationException. | Xử lý outcome của từng Future. |
| Gọi take quá số task đã submit thành công. | Có thể block mãi chờ completion không tồn tại. | Đếm accepted submissions; xử lý rejection. |
| Không có deadline khi task có thể kẹt. | Collector block vô hạn. | Timed poll/deadline/cancellation. |
| Gọi timed poll N lần với full timeout. | Deadline tổng bị kéo dài N lần. | Tính remaining time theo monotonic clock. |
| Dùng bounded completionQueue quá nhỏ. | `add` completed Future có thể fail, completion không lấy được. | Giới hạn in-flight trước submit, không chặn completion enqueue. |
| Quên shutdown executor do mình sở hữu. | Worker không-daemon có thể giữ process sống; rò tài nguyên. | Lifecycle trong finally, shutdown/awaitTermination phù hợp. |
| Shutdown executor được caller cung cấp. | Ảnh hưởng các subsystem dùng chung. | Rõ ownership; chỉ hủy task do mình sở hữu. |
| Hủy Future rồi coi worker đã chết tức thì. | Task có thể chưa hợp tác dừng. | Kiểm tra lifecycle, timeout và cleanup policy. |
| Consumer xử lý kết quả quá chậm. | Completion queue và bộ nhớ có thể tăng. | Theo dõi backlog, giới hạn in-flight hoặc tách stage xử lý. |
| Dùng pool một worker rồi task đợi task khác trong cùng pool. | Pool starvation/deadlock. | Sửa dependency graph và cấu hình executor. |
| Các task sửa cùng mutable map/list không đồng bộ. | Data race/invariant lỗi. | Immutable input/output hoặc synchronization thích hợp. |
| Chỉ muốn một kết quả thành công nhưng tự viết loop quá phức tạp. | Nhiều nhánh lỗi/hủy khó kiểm soát. | Xem xét `invokeAny()` khi contract phù hợp. |

## 18. Quy trình áp dụng vào ứng dụng thực tế

Trước khi dùng `ExecutorCompletionService`, hãy trả lời:

1. **Kết quả nào cần trước?** Có cần nhận bất kỳ completion nào ngay khi xong, hay vẫn phải xuất đúng thứ tự input?
2. **Các task có độc lập không?** Nếu task đợi nhau, completion order không giải quyết dependency.
3. **Executor do ai sở hữu?** Có được shutdown khi kết thúc workflow không?
4. **Số task giới hạn bao nhiêu?** Có nguy cơ submit quá nhiều hoặc tạo backlog?
5. **Bao nhiêu completion sẽ thu?** Bộ đếm dựa vào accepted submissions hay input dự kiến?
6. **Cách xử lý failure?** Ném exception, partial success, retry, fail-fast hay first success?
7. **Cancellation của leftover?** Có lưu Future handles để yêu cầu hủy khi timeout/đủ kết quả không?
8. **Deadline ở đâu?** Per task, toàn lô, hay cả hai? Có tính remaining time không?
9. **Dữ liệu được publish ra sao?** Callable nhận input immutable, Future.get công bố result, state tổng hợp có thread safety phù hợp?
10. **Quan sát hiệu năng gì?** Latency từng task, tỉ lệ lỗi, completion queue backlog, in-flight count, pool saturation, shutdown latency?
11. **Xử lý task bị reject?** Không chờ completion cho task chưa được chấp nhận.
12. **Không dùng completion queue làm cơ chế backpressure sai chỗ?** Giới hạn in-flight hoặc work queue đúng tầng.
13. **Có test thứ tự xác định bằng latch?** Không dùng sleep đoán thứ tự kết thúc.

Nếu các yêu cầu trên chỉ là “đợi tất cả xong rồi xử lý chung”, `invokeAll()` hoặc `CompletableFuture.allOf()` có thể gọn hơn. Nếu cần **xử lý dần theo hoàn tất**, ECS thể hiện ý định rất rõ.

## 19. Câu hỏi tự kiểm tra

**1.** Submit A, B, C; B và C xong trước A. Vì sao duyệt list Future theo submit order gây chậm?  
**Đáp:** `get()` trên A block consumer dù kết quả B/C đã hoàn tất; đây là head-of-line blocking ở tầng kết quả.

**2.** `ExecutorCompletionService.take()` trả về value hay Future?  
**Đáp:** Trả một `Future<V>` đã hoàn tất và lấy nó ra khỏi completion queue; cần `get()` để nhận value hoặc exception.

**3.** Task ném exception có được lấy qua take không?  
**Đáp:** Có, Future hoàn tất ngoại lệ vẫn được enqueue; `get()` ném `ExecutionException`.

**4.** `poll()` trả null nghĩa là tất cả task đã thất bại?  
**Đáp:** Không. Chỉ là queue chưa có completed Future tại thời điểm poll.

**5.** `poll(timeout)` hết hạn có tự cancel task còn chạy không?  
**Đáp:** Không. Cần cancellation policy riêng.

**6.** Vì sao completion order không bảo đảm tie-break khi hai task xong rất gần nhau?  
**Đáp:** Nó phản ánh thứ tự enqueue completion theo lịch chạy/runtime; không có đồng hồ tổng thể dùng để ép một thứ tự lịch sử xác định.

**7.** Một batch submit 10 task, 2 task bị reject. Collector nên đợi bao nhiêu completion?  
**Đáp:** Chỉ đợi số **accepted submissions**, không đợi hai task bị reject.

**8.** Có nên truyền `ArrayBlockingQueue(2)` làm completion queue cho 100 task không?  
**Đáp:** Không nếu không thể bảo đảm queue luôn có chỗ khi enqueue: `add` có thể fail và làm mất completed Future khỏi dịch vụ thu kết quả.

**9.** Muốn deadline tổng 5 giây, vì sao không gọi `poll(5s)` 100 lần?  
**Đáp:** Có thể chờ tổng cộng nhiều lần 5 giây; phải tính remaining time từ một deadline chung.

**10.** `Future.cancel(true)` có chắc khiến task dừng ngay không?  
**Đáp:** Không; đây là yêu cầu hủy/interruption, phụ thuộc task hợp tác và API blocking.

**11.** Khi nào `invokeAny()` có thể đơn giản hơn ECS tự viết?  
**Đáp:** Khi chỉ cần một kết quả thành công sớm và không cần custom policy/partial progress.

**12.** Nếu muốn hiển thị kết quả A, B, C theo input order nhưng vẫn xử lý nhanh ngay khi chúng xong, làm sao?  
**Đáp:** Đưa request ID/index vào result, xử lý theo completion order rồi sắp xếp/đặt vào slot theo input order.

**13.** Completion service có tự shutdown executor không?  
**Đáp:** Không. Lifecycle thuộc executor owner; phải quản lý shutdown/cleanup riêng.

**14.** ECS có làm các callable cùng sửa một list thường trở nên thread-safe không?  
**Đáp:** Không. ECS chỉ thu completion; state chia sẻ vẫn cần ownership, synchronization hoặc immutable design.

**15.** Vì sao một pool chỉ có một worker có thể deadlock khi task A đợi Future của task B vừa submit vào chính pool?  
**Đáp:** Worker bị A chiếm trong khi B chưa có worker chạy; ECS không giải quyết dependency starvation ấy.

**16.** Khi nào chọn ECS thay vì lấy Future trực tiếp?  
**Đáp:** Khi cần **thu và xử lý kết quả theo từng task hoàn tất**, không bị khóa vào thứ tự submit.

**Ghi nhớ:** `ExecutorCompletionService` tổ chức **fan-out task qua Executor** và **fan-in completed Future qua completion queue**. `take()/poll()` giúp consumer xử lý task hoàn tất trước, còn `Future.get()` cho value hoặc exception. Correctness vẫn đòi hỏi đếm accepted submissions, deadline/cancel/cleanup, thread-safety của state và giới hạn số in-flight; completion service không thay thế executor lifecycle, transaction hay backpressure policy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-009">ScheduledExecutorService cho công việc trì hoãn</a>

<details>
<summary>Xem chi tiết</summary>

# ScheduledExecutorService cho công việc trì hoãn trong Java

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Giải thích **ScheduledExecutorService** trong Executor Framework và phân biệt **hẹn chạy sau một khoảng trì hoãn** với **chạy định kỳ**.
- Dùng đúng hai overload **schedule(Runnable, delay, unit)** và **schedule(Callable<V>, delay, unit)**; biết vai trò của **ScheduledFuture** đối với kết quả, lỗi, timeout và cancellation.
- Phân biệt **thời điểm task đủ điều kiện chạy** với **thời điểm task thực sự bắt đầu**. Hiểu scheduler của JDK không cam kết độ chính xác thời gian thực (*hard real-time*).
- Chọn **Executors.newSingleThreadScheduledExecutor()**, **Executors.newScheduledThreadPool(n)** hoặc **ScheduledThreadPoolExecutor** với cấu hình phù hợp.
- Xử lý an toàn **shutdown()**, **shutdownNow()**, **Future.cancel()**, **InterruptedException**, task ném exception và tài nguyên liên quan.
- Nhận biết các rủi ro: đặt lịch quá nhiều task, một worker bị block, lịch theo giờ thực/đổi múi giờ, task bị chạy lặp qua retry/restart, và sự phụ thuộc giữa các task trong cùng executor.
- Đọc hiểu sự khác biệt giữa **scheduleAtFixedRate()** và **scheduleWithFixedDelay()** để không nhầm lịch một lần với lịch định kỳ.
- Chạy các ví dụ Java 17+ có đầu ra có thể kiểm chứng mà không dựa vào suy đoán độ dài sleep hay thứ tự scheduling ngẫu nhiên.

Phạm vi bài là **Java concurrency nói chung**, thuộc nhóm **Future & Scheduling**. Bài không bàn API, annotation hoặc cơ chế scheduling riêng của Spring.

## 2. Từ ExecutorService đến ScheduledExecutorService

**ExecutorService** tách việc gửi task khỏi việc tạo/quản lý thread. **ScheduledExecutorService** mở rộng ExecutorService và bổ sung khả năng yêu cầu task trở nên **đủ điều kiện thực thi sau một độ trễ tương đối**, hoặc được thực thi theo lịch lặp.

~~~java
// Đoạn minh họa cú pháp trong một phương thức Java.
ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

try {
    ScheduledFuture<Integer> result =
            scheduler.schedule(() -> 6 * 7, 200, TimeUnit.MILLISECONDS);
    System.out.println(result.get());
} finally {
    scheduler.shutdown();
}
~~~

Cần import các lớp trong java.util.concurrent và khai báo/xử lý checked exceptions của **Future.get()**. Đoạn trên chưa phải một chương trình main độc lập; ví dụ chạy đầy đủ ở phần 5.

Các thành phần:

| Thành phần | Vai trò |
| --- | --- |
| **Runnable** | Task không trả giá trị qua run(); overload schedule(Runnable, ...) vẫn trả Future để theo dõi |
| **Callable<V>** | Task trả V hoặc ném exception |
| **ScheduledExecutorService** | Interface hỗ trợ schedule và periodic execution |
| **ScheduledThreadPoolExecutor** | Triển khai dựa trên pool thread, quản lý queue của các task bị trì hoãn |
| **ScheduledFuture<V>** | Future kết hợp khả năng hỏi khoảng trì hoãn còn lại qua Delayed |
| **TimeUnit** | Đơn vị cho các tham số delay, period, timeout |
| **ExecutorService lifecycle** | shutdown, shutdownNow, awaitTermination, hủy task và dọn tài nguyên |

**Một task đã được schedule chưa chắc đang chạy.** Nó có thể chờ tới hạn, chờ worker rảnh, hoặc đã bị cancel. **ScheduledFuture** giúp quan sát một tác vụ cụ thể nhưng không phải một kho lưu lịch bền vững qua restart JVM.

### Sơ đồ thời gian

~~~text
Thread gửi task           Scheduler                         Worker
---------------           ---------                         ------
schedule(task, 200 ms)
       |                       |
       +---- đăng ký ----------> task nằm trong queue chờ tới hạn
                                |
              200 ms tương đối  +--- task ĐỦ ĐIỀU KIỆN chạy
                                |                |
                                +--- worker rảnh? +----> task.run()
                                |                |
                                |            nếu bận, tiếp tục chờ
       <---- ScheduledFuture ---+
~~~

Lưu ý **“200 ms” không phải lời hứa task chạy chính xác ở mốc 200 ms**. Khi tới hạn, worker còn phụ thuộc scheduler, CPU, tác vụ trước đó và tài nguyên hệ thống.

## 3. Hai overload schedule cho một lần thực thi

### 3.1. Runnable: không có giá trị kết quả

~~~java
// Minh họa; scheduler là ScheduledExecutorService đã tạo.
ScheduledFuture<?> future = scheduler.schedule(
        () -> System.out.println("done"),
        1, TimeUnit.SECONDS
);
~~~

Task in “done” sau khi đủ điều kiện chạy và worker có thể phục vụ. Khi task **hoàn thành bình thường**, **future.get()** trả **null**. Nếu Runnable ném exception, Future.get() ném **ExecutionException** chứa cause. Nếu Future bị cancel, get() ném **CancellationException**.

### 3.2. Callable<V>: có kết quả

~~~java
ScheduledFuture<Integer> future = scheduler.schedule(
        () -> 21 * 2,
        1, TimeUnit.SECONDS
);
int answer = future.get(); // 42 khi task hoàn tất thành công.
~~~

Đoạn trên là minh họa trong phương thức có biến scheduler và checked-exception handling. **get()** đợi tác vụ hoàn thành, không phải đợi chính xác một giây rồi tự trả. Nếu executor quá tải, tổng thời gian từ schedule tới get() hoàn tất có thể **dài hơn delay**.

### 3.3. Delay tính từ thời điểm đăng ký

Các tham số **delay** và **unit** là **thời gian tương đối**, không phải timestamp lịch theo ngày/tháng/giờ. Ví dụ:

~~~java
scheduler.schedule(task, 500, TimeUnit.MILLISECONDS);
scheduler.schedule(task, 2, TimeUnit.SECONDS);
scheduler.schedule(task, 0, TimeUnit.MILLISECONDS);
~~~

Ba dòng trên minh họa các lần đăng ký với Runnable task đã khai báo. **Delay bằng 0 hoặc âm** cho một lần schedule được hiểu là yêu cầu thực thi **ngay khi có thể**; đó không phải lời hứa thực thi đồng bộ ngay trên thread gửi. Scheduler vẫn có thể enqueue.

**Không nhầm với periodic APIs:** **period** của scheduleAtFixedRate và **delay giữa các lần chạy** của scheduleWithFixedDelay phải **dương**; giá trị 0 hoặc âm cho các tham số ấy gây IllegalArgumentException. **initialDelay** của periodic scheduling có thể bằng 0.

## 4. ScheduledFuture, getDelay và quan sát trạng thái

**ScheduledFuture<V>** đồng thời là **Future<V>** và **Delayed**.

Các API đáng chú ý:

| API | Ý nghĩa |
| --- | --- |
| **get()** | Chờ kết thúc và lấy kết quả, có thể ném InterruptedException/ExecutionException/CancellationException |
| **get(timeout, unit)** | Chờ tối đa thời lượng được chỉ định; có thể ném TimeoutException |
| **cancel(false)** | Cố hủy; không yêu cầu ngắt task đang chạy |
| **cancel(true)** | Cố hủy; có thể yêu cầu interrupt thread đang thực thi |
| **isCancelled()** | Future đã bị hủy theo hợp đồng hay chưa |
| **isDone()** | Future đã hoàn tất, kể cả lỗi hoặc hủy, không nhất thiết thành công |
| **getDelay(unit)** | Thời gian ước lượng tới khi task đủ điều kiện thực thi |
| **compareTo(Delayed)** | Thứ tự các task bị trì hoãn trong hợp đồng Delayed |

**getDelay() không phải đồng hồ đếm ngược chính xác tới lúc task bắt đầu.** Khi task đã đến hạn nhưng worker đang bận, **getDelay() có thể bằng 0 hoặc âm** trong khi task vẫn chưa chạy. Sau khi Future hoàn tất, giá trị delay cũng không phải báo cáo thời lượng đã thực thi hay latency.

**get(timeout)** giới hạn thời gian **caller đợi Future**, không thay đổi delay đã đăng ký và **không tự cancel task** khi timeout. Một caller có thể bỏ chờ nhưng worker vẫn chạy sau đó.

### Ba trạng thái cần tách riêng

~~~text
Đã nhận lịch -> chưa đủ hạn -> đủ hạn, đang chờ worker -> đang chạy
                                                           |
                           +----------- hoàn tất ----------+
                           |               |
                           v               v
                        thành công      exception

Hủy có thể xảy ra trước khi bắt đầu hoặc trong lúc task đang chạy.
~~~

**Future.isDone() = true** có thể do thành công, thất bại hoặc hủy. **isCancelled() = true** không chứng minh mọi side effect đã được đảo ngược; cancellation là trạng thái Future và yêu cầu hợp tác với task.

## 5. Ví dụ 1: chạy Callable một lần sau độ trễ

Tệp **DelayedCallableDemo.java**:

~~~java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DelayedCallableDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        try {
            ScheduledFuture<Integer> future = scheduler.schedule(
                    () -> 21 * 2,
                    20, TimeUnit.MILLISECONDS
            );

            System.out.println("value = " + future.get());
            System.out.println("done = " + future.isDone());
            System.out.println("cancelled = " + future.isCancelled());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DelayedCallableDemo.java
java DelayedCallableDemo
~~~

Stdout khi môi trường không gửi interrupt và task hoàn tất bình thường:

~~~text
value = 42
done = true
cancelled = false
~~~

**Lập luận:**

1. Scheduler nhận Callable, đặt trigger tương đối sau 20 ms.
2. **future.get()** chờ đến khi task đã thực thi và trả 42, dù worker có thể bắt đầu sau hạn.
3. Khi get() trả thành công, Future đã **done** và không bị cancel.
4. finally yêu cầu shutdown để worker không giữ chương trình chạy sau khi không còn cần scheduler.

Đừng in một con số “elapsed = 20 ms” như output bắt buộc: runtime, độ phân giải đồng hồ và tải hệ thống có thể làm thời gian đo thực tế khác nhau.

### Runnable có Future trả null khi hoàn tất

Tệp **DelayedRunnableDemo.java**:

~~~java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DelayedRunnableDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean executed = new AtomicBoolean();

        try {
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> executed.set(true),
                    10, TimeUnit.MILLISECONDS
            );

            Object value = future.get();

            System.out.println("runnable result = " + value);
            System.out.println("executed = " + executed.get());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DelayedRunnableDemo.java
java DelayedRunnableDemo
~~~

Stdout:

~~~text
runnable result = null
executed = true
~~~

Task Runnable không có kết quả trả về của run(), nhưng **ScheduledFuture** vẫn cho phép chờ **thời điểm hoàn tất** hoặc nhận lỗi. AtomicBoolean là công cụ quan sát trạng thái minh họa; ngữ nghĩa completion của Future đã đủ để main biết code trước khi run() kết thúc đã xảy ra.

## 6. Chọn scheduler: một worker hay nhiều worker?

Hai factory phổ biến:

~~~java
ScheduledExecutorService one =
        Executors.newSingleThreadScheduledExecutor();

ScheduledExecutorService many =
        Executors.newScheduledThreadPool(4);
~~~

Đoạn minh họa dùng các import từ java.util.concurrent; trong chương trình thật phải shutdown cả hai executor đúng vòng đời.

| Tiêu chí | Single-thread scheduled | Scheduled thread pool N worker |
| --- | --- | --- |
| Số task chạy đồng thời | Tối đa 1 | Tối đa N |
| Khi task đến hạn nhưng worker bận | Chờ task đang chạy kết thúc | Có thể chạy trên worker khác còn rảnh |
| Hai task cùng đủ hạn | Thực thi tuần tự | Có thể thực thi song song |
| Chia sẻ mutable state | Dễ xây một owner nếu tất cả truy cập đều đi qua executor | Cần atomic/lock/confinement phù hợp |
| Blocking task kéo dài | Chặn toàn bộ tác vụ sau | Dùng hết N worker có thể chặn tác vụ sau |
| Queue delayed tasks | Có thể tích lũy rất lớn | Vẫn có thể tích lũy rất lớn |
| Thứ tự task thực sự hoàn tất | Tuần tự trong một worker | Không hứa theo thứ tự submit |

**newSingleThreadScheduledExecutor()** bảo đảm không có hơn một task chạy cùng thời điểm; không nên giả định **cùng một Thread object tồn tại vĩnh viễn**. Nếu worker kết thúc bất thường, executor có thể tạo worker thay thế. ThreadLocal gắn với thread, không gắn với scheduler.

**newScheduledThreadPool(n)** dùng **n core workers** theo nhu cầu. Với **ScheduledThreadPoolExecutor**, queue của các task trì hoãn là **DelayedWorkQueue không có capacity hữu ích để áp dụng backpressure**. Khi tất cả n worker bận, task đến hạn vẫn chờ. Việc đặt maximumPoolSize lớn hơn core **không tạo cơ chế scale tương tự bounded ThreadPoolExecutor**, vì hàng đợi delayed thực dụng không giới hạn.

### Không coi “scheduled” là “giới hạn số job”

Nếu mỗi giây producer đăng ký nhiều task với delay dài, scheduler có thể giữ rất nhiều object task trong queue. Cần **admission control**, giới hạn số lịch, hủy lịch không còn cần thiết, và xem xét **setRemoveOnCancelPolicy(true)** khi dùng ScheduledThreadPoolExecutor để tránh giữ task đã cancel trong queue quá lâu.

## 7. Khi task đến hạn nhưng scheduler chưa có worker

Ví dụ, scheduler chỉ có 1 worker:

~~~text
Thời điểm 0:
  Task A đang chạy và block trên I/O.

Thời điểm 100 ms:
  Task B đủ hạn để chạy.

Thời điểm 500 ms:
  Task A hoàn tất, worker mới nhận B.

Kết quả:
  B được schedule sau 100 ms nhưng có thể thực sự bắt đầu gần 500 ms.
~~~

Đây là sơ đồ minh họa, không phải giới hạn thời gian được JDK bảo đảm.

Các nguyên nhân delay sau hạn:

- Tất cả worker đang thực thi task khác, đặc biệt là blocking I/O dài.
- CPU scheduler/JVM đang bận, stop-the-world GC hoặc mức tải cao.
- Task trước đó giữ khóa hoặc đợi tài nguyên mà task mới cũng cần.
- Queue có quá nhiều task cùng đến hạn.
- Shutdown/cancellation/exception thay đổi trạng thái lifecycle của scheduler.
- Nhiều instance ứng dụng có lịch độc lập cùng tranh chấp tài nguyên chung.

**ScheduledExecutorService là công cụ hẹn thời điểm sớm nhất có thể chạy, không phải scheduler hard real-time.** Nếu nghiệp vụ cần deadline chặt, phải thiết kế theo dõi latency, timeout và xử lý quá hạn riêng.

### Bẫy: một task đợi task khác trên cùng scheduler một worker

Nếu task A chạy trên single scheduler, từ A lại gọi schedule(task B, 0, ...) rồi **B.get()**, A chiếm worker và chờ B. B chưa được chạy vì worker duy nhất đang bận A: **thread-starvation deadlock**.

Không viết luồng phụ thuộc như vậy. Cân nhắc composition bất đồng bộ hoặc mô hình worker khác nếu bài toán cần, và kiểm tra dependency graph trước khi dùng Future.get() trong task.

## 8. Ví dụ 2: hủy task đang nằm trong queue trước khi thực thi

**cancel(false)** phù hợp khi muốn ngăn task chưa bắt đầu, không yêu cầu interrupt task đang chạy. Để ví dụ không phụ thuộc vào việc “kịp cancel trước 100 ms”, ta **cố ý giữ worker duy nhất** bằng một latch, rồi đặt task thứ hai vào queue và cancel ngay khi nó chắc chắn chưa có worker.

Tệp **CancelDelayedTaskDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CancelDelayedTaskDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean();

        try {
            Future<?> blocker = scheduler.submit(() -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            entered.await(); // Worker duy nhất đang bị giữ.

            ScheduledFuture<?> delayed = scheduler.schedule(
                    () -> ran.set(true),
                    0, TimeUnit.MILLISECONDS
            );

            boolean cancelled = delayed.cancel(false);

            release.countDown();
            blocker.get();

            scheduler.shutdown();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);

            System.out.println("cancel accepted = " + cancelled);
            System.out.println("task ran = " + ran.get());
            System.out.println("cancelled = " + delayed.isCancelled());
        } finally {
            release.countDown();
            scheduler.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CancelDelayedTaskDemo.java
java CancelDelayedTaskDemo
~~~

Stdout:

~~~text
cancel accepted = true
task ran = false
cancelled = true
~~~

**Vì sao kết quả được xác định?**

1. entered.await() xác nhận **blocker đã bắt đầu** và đang giữ worker duy nhất.
2. Task delayed có delay=0 nhưng không thể chạy vì worker còn chờ latch.
3. Main gọi cancel(false) khi delayed còn pending, nên hủy thành công.
4. Khi release mở, blocker kết thúc và worker không thực thi task đã hủy.
5. Future bị hủy có isCancelled() true; **delayed.get() nếu gọi sẽ ném CancellationException**.

Cẩn thận trong production: cancel(false) có thể trả false khi task đã hoàn tất hoặc bị hủy trước đó; trả true không có nghĩa side effect từ mọi task khác sẽ bị rollback. Khi task đã bắt đầu, **cancel(false) không yêu cầu interrupt**, và task đang thực thi có thể vẫn tiếp tục chạy.

## 9. cancel(true), interrupt và cleanup

**ScheduledFuture.cancel(true)** cố hủy Future và **có thể interrupt thread đang chạy** nếu task đã bắt đầu. Nó không cưỡng chế dừng code Java theo cách an toàn. Việc dừng phụ thuộc vào task có kiểm tra trạng thái interrupt, dùng blocking API interruptible và dọn tài nguyên hay không.

Mẫu task hợp tác:

~~~java
// Minh họa thân một Runnable, không phải file độc lập.
Runnable job = () -> {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            // Xử lý từng lô ngắn, không giữ lock xuyên thời gian dài.
            processBatch();
        }
    } finally {
        releaseOwnedResources();
    }
};
~~~

Ở đây **processBatch()** và **releaseOwnedResources()** là các phương thức giả định. Nếu processBatch thực hiện blocking API ném InterruptedException, cần xử lý bằng cách propagate hoặc restore interrupt flag rồi thoát theo giao thức.

**Một điểm rất quan trọng:** cancel(true) có thể chuyển Future sang canceled **trước khi code task thực sự kết thúc**. Vì vậy không suy ra “Future canceled” đồng nghĩa side effect đã chấm dứt hoặc resource đã được cleanup. Muốn đợi cleanup, cần giao thức completion riêng nếu việc ấy quan trọng.

Nếu đang giữ Semaphore permit, ReentrantLock hoặc file handle, task phải dùng **try/finally** để giải phóng đúng quyền sở hữu. Không để cleanup phụ thuộc vào task luôn chạy hết đến dòng cuối.

## 10. Ví dụ 3: Future.get(timeout) không hủy task

Ta tạo task **đã bắt đầu nhưng đang đợi một latch chưa mở**, rồi gọi get với timeout. Vì latch chưa được mở, Future chắc chắn **chưa hoàn tất** ở thời điểm chờ; sau timeout, main mở latch và đợi lấy giá trị cuối.

Tệp **ScheduledFutureTimeoutDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ScheduledFutureTimeoutDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            ScheduledFuture<Integer> future = scheduler.schedule(() -> {
                entered.countDown();
                release.await();
                return 42;
            }, 0, TimeUnit.MILLISECONDS);

            entered.await();

            boolean timedOut = false;
            try {
                future.get(10, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                timedOut = true;
            }

            release.countDown();
            int value = future.get();

            System.out.println("wait timed out = " + timedOut);
            System.out.println("final value = " + value);
            System.out.println("cancelled = " + future.isCancelled());
        } finally {
            release.countDown();
            scheduler.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScheduledFutureTimeoutDemo.java
java ScheduledFutureTimeoutDemo
~~~

Stdout:

~~~text
wait timed out = true
final value = 42
cancelled = false
~~~

**Phân tích:**

- Khi main gọi get(10ms), worker đã vào Callable và vẫn phải chờ **release**.
- Vì main **chưa mở release**, Future chưa thể hoàn tất bình thường; get hết timeout và ném TimeoutException.
- Timeout chỉ làm **caller ngừng chờ**, không gọi cancel().
- Main mở release, task trả 42 và Future.get() lần sau nhận kết quả.
- isCancelled() false vì Future chưa từng bị cancel.

Đừng biến 10 ms thành phép đo tốc độ scheduler: ta chỉ dùng nó để minh họa một **đường timeout có thể kiểm chứng**, không suy ra task luôn bắt đầu/hoàn tất trong 10 ms.

## 11. Exception trong scheduled task

Nếu Callable hoặc Runnable ném unchecked exception, **ScheduledFuture.get()** của task chạy một lần ném **ExecutionException**, và **getCause()** cung cấp nguyên nhân. Exception không tự động chạy lại task.

Tệp **ScheduledFailureDemo.java**:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledFailureDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        try {
            ScheduledFuture<Integer> future = scheduler.schedule(
                    () -> {
                        throw new IllegalStateException("invalid state");
                    },
                    0, TimeUnit.MILLISECONDS
            );

            try {
                future.get();
            } catch (ExecutionException ex) {
                System.out.println(
                    "cause = " + ex.getCause().getClass().getSimpleName()
                );
            }

            System.out.println("done = " + future.isDone());
            System.out.println("cancelled = " + future.isCancelled());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScheduledFailureDemo.java
java ScheduledFailureDemo
~~~

Stdout:

~~~text
cause = IllegalStateException
done = true
cancelled = false
~~~

**Khác biệt với rejection:** nếu scheduler đã shutdown hoặc từ chối đăng ký, **schedule()** có thể ném **RejectedExecutionException ngay tại thread gửi**, không trả Future hợp lệ. Nếu đã nhận lịch rồi task ném exception khi chạy, lỗi được quan sát bằng Future.get().

**Đừng bỏ qua Future** của tác vụ quan trọng. Một exception trong tác vụ đã được schedule có thể không xuất hiện như exception ở thread đã gửi task; phải có kênh quan sát lỗi, metrics và chính sách retry phù hợp.

## 12. Lịch một lần và lịch định kỳ khác nhau thế nào?

ScheduledExecutorService còn có hai phương thức chạy lặp:

~~~java
// Minh họa:
scheduler.scheduleAtFixedRate(
        task, 0, 100, TimeUnit.MILLISECONDS
);

scheduler.scheduleWithFixedDelay(
        task, 0, 100, TimeUnit.MILLISECONDS
);
~~~

Các đoạn này minh họa tham số; task là Runnable đã khai báo. Dù tên phương thức đều có schedule, **ý nghĩa lịch lặp khác với schedule() một lần**.

### scheduleAtFixedRate: nhắm tới các mốc theo chu kỳ

Với initialDelay=0, period=100ms, các mốc dự kiến lần lượt là khoảng:

~~~text
t0, t0 + 100ms, t0 + 200ms, t0 + 300ms, ...
~~~

Nếu lần chạy trước mất thời gian dài, lần chạy sau có thể bắt đầu **muộn**. Theo hợp đồng ScheduledThreadPoolExecutor, các lần chạy liên tiếp **của cùng một periodic task không chồng lấn**, dù executor có nhiều worker. Nếu task chạy lâu hơn period, các lần sau có thể nối tiếp nhau mà không có khoảng trống dự kiến.

### scheduleWithFixedDelay: đợi sau khi lần chạy trước kết thúc

Với delay=100ms:

~~~text
Run 1 kết thúc
    |
    +-- chờ 100ms --> Run 2 bắt đầu
                           |
                      Run 2 kết thúc
                           |
                           +-- chờ 100ms --> Run 3 ...
~~~

Fixed-delay thường dễ hiểu cho polling có điều kiện hoặc task chỉ nên bắt đầu lại sau khi lần trước xong một khoảng. Nó không cố bám vào các mốc cố định từ thời điểm ban đầu.

### Bảng so sánh

| Ý định | API | Đặc điểm |
| --- | --- | --- |
| Chạy đúng **một lần** sau khoảng trì hoãn | **schedule(task, delay, unit)** | Future hoàn tất khi task hoàn tất/lỗi/hủy |
| Chạy lặp theo nhịp kể từ mốc ban đầu | **scheduleAtFixedRate** | Mốc lý tưởng theo period; có thể trễ, không overlap cùng một periodic task |
| Chạy lặp sau khoảng nghỉ từ kết thúc lần trước | **scheduleWithFixedDelay** | Khoảng nghỉ giữa hai lần chạy |
| Đợi đúng **một giờ lịch địa phương** mỗi ngày | Cần tính lại thời điểm theo ngày/zone | Không mặc nhiên dùng period=24h vì DST/đổi giờ |
| Công việc cần bền vững qua JVM restart | Cần persistence và giao thức khác | ScheduledExecutorService chỉ giữ lịch trong process |

**Task định kỳ ném exception** sẽ làm các lần chạy tiếp theo **bị suppress** theo hợp đồng, thay vì tự retry mãi. Future của task periodic **không hoàn tất bình thường** chỉ vì đã chạy một lượt: nó đại diện cho cả chuỗi lặp; chỉ trở thành done khi bị hủy hoặc kết thúc bất thường. Không gọi periodicFuture.get() vô thời hạn rồi kỳ vọng nó trả sau tick đầu.

## 13. Ví dụ 4: hủy lịch chạy định kỳ sau ba lần

Tệp **PeriodicStopDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PeriodicStopDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        AtomicInteger runs = new AtomicInteger();
        CountDownLatch threeRuns = new CountDownLatch(3);

        try {
            ScheduledFuture<?> periodic =
                    scheduler.scheduleWithFixedDelay(
                            () -> {
                                runs.incrementAndGet();
                                threeRuns.countDown();
                            },
                            0, 10, TimeUnit.MILLISECONDS
                    );

            threeRuns.await();
            boolean stopped = periodic.cancel(false);

            System.out.println("at least three = " + (runs.get() >= 3));
            System.out.println("cancelled = " + stopped);
            System.out.println("future cancelled = "
                    + periodic.isCancelled());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 PeriodicStopDemo.java
java PeriodicStopDemo
~~~

Stdout:

~~~text
at least three = true
cancelled = true
future cancelled = true
~~~

threeRuns.await() bảo đảm main **không hủy trước khi đạt ba lượt**; trong lúc main chuẩn bị cancel, một lượt kế tiếp *có thể* đã chạy, nên **không in runs == 3 như một hằng số**. Đây là nguyên tắc quan trọng để kiểm thử concurrency: phát biểu **bất biến bảo đảm**, không khẳng định lịch xen kẽ tình cờ.

**Periodic future** không tự kết thúc bình thường. cancel(false) hủy các lần chạy tiếp theo; khi hủy thành công, isCancelled() true. Việc hủy không có nghĩa những side effect của ba lượt trước được hoàn tác.

## 14. Shutdown của scheduler có hai chính sách đặc biệt

**ScheduledThreadPoolExecutor** là triển khai nền tảng của các factory scheduler trong JDK. Khi dùng trực tiếp, nó cung cấp chính sách kiểm soát các task đã được đăng ký trước khi shutdown.

| Chính sách | Giá trị mặc định | Ý nghĩa |
| --- | --- | --- |
| **getExecuteExistingDelayedTasksAfterShutdownPolicy()** | **true** | Task **one-shot đã hẹn trước shutdown** vẫn có thể chạy sau khi shutdown khi đủ hạn |
| **getContinueExistingPeriodicTasksAfterShutdownPolicy()** | **false** | Các lượt chạy periodic tương lai không tiếp tục sau shutdown |
| **getRemoveOnCancelPolicy()** | **false** | Task đã cancel không nhất thiết được gỡ ngay khỏi queue chờ |

Các setter tương ứng:

~~~java
// Minh họa với ScheduledThreadPoolExecutor scheduler đã tạo:
scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
scheduler.setRemoveOnCancelPolicy(true);
~~~

**Không nhầm shutdown với cancel tất cả lịch đã có.** Với mặc định **execute-existing-delayed=true**, nếu bạn schedule một task chạy sau 1 ngày rồi gọi shutdown(), executor có thể giữ tài nguyên chờ task đó tới hạn thay vì terminated ngay.

### shutdown() khác shutdownNow()

- **shutdown()** ngừng nhận lịch mới. Task đã đăng ký sẽ được xử lý theo các policy ở trên; phương thức **không tự block chờ hoàn tất**.
- **shutdownNow()** cố ngắt task đang chạy và trả về các task chưa bắt đầu; không bảo đảm task đang thực thi dừng ngay.
- **awaitTermination(timeout, unit)** chờ executor kết thúc; trả boolean. Nếu false, không được mặc định coi cleanup đã xong.
- Task có thể bị **RejectedExecutionException** khi gọi schedule sau shutdown.
- Nếu cấu hình periodic-continue=true, periodic task có thể tiếp tục và giữ executor không terminated cho đến khi cancel/shutdownNow hoặc thay đổi policy.

### Ví dụ 5: one-shot delayed task vẫn chạy sau shutdown mặc định

Tệp **ScheduledShutdownDemo.java**:

~~~java
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ScheduledShutdownDemo {
    public static void main(String[] args) throws Exception {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(1);

        try {
            ScheduledFuture<Integer> future = scheduler.schedule(
                    () -> 42,
                    20, TimeUnit.MILLISECONDS
            );

            scheduler.shutdown(); // Mặc định vẫn cho delayed one-shot chạy.
            System.out.println("value after shutdown = " + future.get());
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } finally {
            scheduler.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScheduledShutdownDemo.java
java ScheduledShutdownDemo
~~~

Stdout:

~~~text
value after shutdown = 42
~~~

Task được **đăng ký trước shutdown()** và chính sách mặc định cho phép one-shot task tiếp tục chạy sau khi executor ngừng nhận task mới. Vì vậy future.get() vẫn trả 42. Khoảng 20ms là độ trễ tối thiểu để đủ điều kiện, không phải SLA của thực thi.

Nếu đổi thành **setExecuteExistingDelayedTasksAfterShutdownPolicy(false)** trước khi shutdown, những task one-shot chưa đến hạn sẽ bị cancel theo policy thay vì được để lại tới lúc chạy. **Phải quyết định rõ mong muốn của ứng dụng:** khi dừng hệ thống, các lịch cũ nên chạy cho xong, bị hủy, hay được lưu lại ở nơi bền vững để phục hồi?

## 15. Task đã cancel có thể còn trong queue

Đối với **ScheduledThreadPoolExecutor**, task bị cancel **không nhất thiết được loại khỏi queue ngay lập tức** theo mặc định. Một task hẹn trong nhiều giờ, bị cancel sau vài giây, có thể còn được lưu trong delayed queue đến khi tới hạn; số lượng lớn có thể gây giữ bộ nhớ không cần thiết.

Khi tự tạo ScheduledThreadPoolExecutor, có thể cấu hình:

~~~java
// Mã minh họa:
ScheduledThreadPoolExecutor scheduler =
        new ScheduledThreadPoolExecutor(2);
scheduler.setRemoveOnCancelPolicy(true);
~~~

Chính sách này giúp những task bị cancel được gỡ khỏi queue ngay khi hủy theo hợp đồng của executor, giảm retention và thuận tiện quản trị số lượng lịch. Nó **không khiến task đã bắt đầu bị cưỡng chế dừng** và không thay thế việc thiết kế quản lý số lượng lịch.

**Không sử dụng queue.size() làm bộ điều khiển nhận việc theo kiểu check rồi schedule**: giữa lúc kiểm tra và gửi lịch, nhiều thread khác có thể đăng ký; số liệu có thể thay đổi ngay. Nếu phải giới hạn “đang có tối đa K lịch được nhận”, dùng admission control có primitive nguyên tử/permit và hoàn trả permit trên **mọi trạng thái cuối** theo một giao thức được chứng minh.

### Khi nào dùng ScheduledThreadPoolExecutor trực tiếp?

Khi cần các setter policy, thread factory, rejection handler hoặc cấu hình/chẩn đoán cụ thể. Đối với one-shot delay đơn giản, factory **Executors.newSingleThreadScheduledExecutor()** hoặc **newScheduledThreadPool(n)** đủ rõ ràng. Tránh ép kiểu executor trả về từ factory sang ScheduledThreadPoolExecutor: wrapper có thể che triển khai cụ thể; API type của factory không hứa một cast như vậy hợp lệ.

## 16. Hẹn lịch theo thời điểm trên đồng hồ thực

**schedule(delay, unit)** nhận **độ trễ tương đối**. Nếu nghiệp vụ nói “chạy lúc 09:00 theo múi giờ cụ thể”, bạn cần tính khoảng cách giữa **thời điểm hiện tại** và **thời điểm mục tiêu**.

Ví dụ minh họa trong một chương trình Java đã khai báo scheduler:

~~~java
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

// target là một Instant đã được xác định:
long delayMillis = Math.max(
        0L,
        Duration.between(Instant.now(), target).toMillis()
);
scheduler.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
~~~

Đây là **mảnh code minh họa**, không phải file Java có main(): cần khai báo **target**, **task**, **scheduler** và áp dụng đúng import trong class.

### Những yếu tố dễ bỏ sót

- **Instant** là một thời điểm tuyệt đối; **LocalDateTime** không mang múi giờ nên tự nó không xác định duy nhất một instant toàn cầu.
- Nếu người dùng muốn “09:00 theo Asia/Ho_Chi_Minh”, phải dùng **ZoneId** tương ứng và tính **ZonedDateTime** thích hợp.
- Sau khi đã tính độ trễ, scheduler đang giữ **relative delay**. Nếu đồng hồ hệ thống bị chỉnh về trước/sau, scheduler không tự hiểu lại ý nghĩa lịch theo giờ treo trên tường.
- Nếu lịch lặp “09:00 mỗi ngày” phải tính **ngày kế tiếp với zone**, không đơn giản lặp period=24 HOURS trong mọi múi giờ có DST.
- Nếu target đã ở quá khứ, **Math.max(0, ...)** ở trên khiến task được gửi để chạy sớm nhất có thể, nhưng có thể nghiệp vụ muốn **từ chối lịch quá hạn**; phải quyết định theo hợp đồng.
- Duration lớn hoặc conversion có thể overflow; đối với lịch rất xa hoặc phức tạp, cần validate khoảng thời gian và cơ chế persistent scheduler thích hợp.
- Thay đổi timezone/calendar rule và daylight-saving có thể làm giờ địa phương trùng lặp hoặc không tồn tại ở một số vùng. Hãy định nghĩa rõ ứng xử khi gặp các trường hợp này.

### “Delay 24 giờ” khác “cùng giờ ngày mai”

Tại một khu vực có đổi giờ mùa hè, khoảng cách giữa cùng một giờ địa phương ở hai ngày liên tiếp có thể **23 giờ hoặc 25 giờ**. Dùng TimeUnit.DAYS chỉ biểu diễn khoảng tương đối theo đơn vị thời gian; nó không hiểu lịch địa phương.

Đối với lịch theo calendar, cần xây task hẹn lần tới từ **ZonedDateTime**; khi đã chạy xong mới tính mục tiêu tiếp theo, đồng thời xử lý downtime/restart. Đây là nghiệp vụ khác với một ScheduledFuture hẹn chạy một lần.

## 17. Lập lịch trì hoãn không tương đương lưu công việc bền vững

**ScheduledExecutorService giữ lịch trong bộ nhớ JVM**. Nếu process crash, server restart, JVM bị kill, hay hệ thống đổi deployment, các lịch chưa chạy thường **không được khôi phục tự động**.

Nếu một workflow bắt buộc bảo đảm:

- Không mất lịch dù process chết.
- Nhiều JVM không xử lý trùng cùng một lịch.
- Có trạng thái retry và backoff bền vững.
- Task được thực hiện ít nhất một lần hoặc được xác nhận rõ.
- Cần audit, replay, giới hạn số lần thử hoặc dead-letter policy.

...thì phải thiết kế **persistent job store/queue**, idempotency và coordination ở nơi các process cùng truy cập. Một Java executor trong JVM không tự bảo đảm exactly-once execution toàn hệ thống.

### Hẹn gửi thông báo hoặc thanh toán

Ví dụ chương trình schedule task gửi email sau 10 phút. Nếu user hủy trước hạn, gọi future.cancel(false) có thể ngăn task pending. Nhưng nếu email đã bắt đầu gửi, cancel có thể không rút lại email. Nếu JVM restart, future cũ cũng không sống lại.

Với **thao tác tài chính hoặc side effect không thể đảo ngược**, cần lưu trạng thái, idempotency key, xử lý duplicate và giao thức xác nhận; không dựa vào ScheduledFuture như source of truth.

## 18. Timeout, deadline và quyền hủy

**ScheduledExecutorService** quản lý lúc task đủ điều kiện chạy, còn **Future.get(timeout)** quản lý thời gian caller chờ.

| Tình huống | Công cụ | Ý nghĩa |
| --- | --- | --- |
| Muốn task bắt đầu không sớm hơn 5 giây sau đăng ký | schedule(task, 5, SECONDS) | Delay trước khi task đủ điều kiện |
| Caller chỉ có 1 giây chờ kết quả | future.get(1, SECONDS) | Caller có thể timeout, task vẫn chạy |
| Không muốn task pending chạy nữa | future.cancel(false) | Cố hủy task, không yêu cầu interrupt worker |
| Muốn yêu cầu ngắt task đang chạy | future.cancel(true) | Yêu cầu hủy/interrupt hợp tác, không rollback |
| Toàn bộ thao tác có deadline | Tính remaining time cho từng bước | Không cộng nguyên timeout lặp lại cho từng bước |
| Muốn biết scheduler dừng thật chưa | awaitTermination(timeout, unit) | Quan sát lifecycle sau shutdown |

Khi có nhiều lần chờ trong một deadline chung, tính **thời gian còn lại** từ đồng hồ đơn điệu **System.nanoTime()** (với tính toán an toàn với overflow), thay vì cấp lại toàn bộ budget cho mỗi API.

**Không dùng Thread.sleep(delay) thay cho schedule** nếu mục tiêu là “đăng ký việc để worker thực thi sau này” và caller cần làm việc khác: sleep chặn chính thread gọi, không cung cấp ScheduledFuture/cancel/queue semantics tương đương.

## 19. Các lỗi thiết kế thường gặp

| Sai lầm | Vấn đề | Cách sửa |
| --- | --- | --- |
| Nghĩ schedule(delay=100ms) cam kết đúng 100ms sẽ chạy | Task còn phụ thuộc worker và OS scheduler | Diễn đạt là “đủ điều kiện sau delay”; đo lateness |
| Chặn worker duy nhất bằng I/O dài | Các task đến hạn phía sau bị trễ | Không block worker lâu, chọn pool và phân tách workload |
| Dùng schedule() thay timer bền vững qua restart | Lịch bị mất khi JVM chết | Persistent job store nếu cần durability |
| Dùng scheduleAtFixedRate cho công việc chỉ muốn một lần | Task bị lặp không mong muốn | Dùng schedule() |
| Dùng periodic future.get() để đợi tick đầu | get có thể không hoàn tất bình thường | Dùng CountDownLatch/Condition hoặc tín hiệu riêng |
| Tin shutdown() lập tức hủy mọi delayed task | Mặc định one-shot delayed vẫn được chạy | Cấu hình policy theo lifecycle |
| Hủy nhiều lịch rất xa nhưng bỏ mặc queue | Giữ task canceled lâu | setRemoveOnCancelPolicy(true), giới hạn lịch |
| Cancel(true) rồi cho rằng đã rollback I/O | Future hủy không đảo ngược side effect | Hủy hợp tác và idempotency |
| Quên xem ExecutionException của Future | Không nhận biết task đã lỗi | Thu Future và xử lý cause |
| Timeout get() rồi coi task đã dừng | Task vẫn có thể đang chạy | Chỉ cancel theo policy, theo dõi completion thực tế |
| Dùng clock wall-time để đo khoảng chờ | Đồng hồ có thể được điều chỉnh | Dùng nanoTime cho elapsed/deadline |
| Dùng fixed-rate 24h để biểu diễn mỗi ngày 09:00 ở zone có DST | Giờ địa phương có thể dịch | Tính ZonedDateTime của từng ngày |
| Chỉ dùng một worker rồi chờ task con trong chính scheduler đó | Thread-starvation deadlock | Không block đợi task cần cùng worker |
| Đăng ký vô hạn delayed tasks | Queue/memory tăng không kiểm soát | Admission control, giới hạn và telemetry |
| Tự cast factory scheduler sang ScheduledThreadPoolExecutor | Wrapper có thể không có kiểu đó | Tạo ScheduledThreadPoolExecutor trực tiếp nếu cần policy |
| Đặt ThreadLocal làm trạng thái lịch dài hạn | Worker có thể bị thay và context task tái sử dụng | Lưu state với lifecycle rõ, cleanup ThreadLocal |
| Thấy Future done rồi coi nghiệp vụ thành công | Done gồm lỗi/cancellation | Kiểm tra get()/isCancelled() và status business |

## 20. Kiểm thử lịch trì hoãn mà không phụ thuộc timing ngẫu nhiên

Tạo test đúng cho scheduling có hai phần: **hợp đồng thời gian** và **hợp đồng trạng thái**.

**Nên kiểm tra:**

1. **Callable one-shot** trả đúng kết quả qua Future.get().
2. **Runnable one-shot** hoàn tất, Future.get() trả null.
3. **Cancel trước khi task bắt đầu**: giữ worker bằng latch, đặt task vào queue, cancel(false), xác nhận task không chạy.
4. **Timeout của caller**: giữ Callable trên latch rồi dùng Future.get(timeout), xác nhận task vẫn có thể hoàn tất sau khi caller mở latch.
5. **Task ném exception**: Future.get() ném ExecutionException với cause mong đợi.
6. **Periodic scheduling**: dùng CountDownLatch đếm một số tick rồi cancel; không assert một số tick chính xác sau khi main có thể bị scheduler trễ.
7. **Shutdown policies**: schedule trước shutdown, xác minh task delayed chạy/hủy theo policy cấu hình.
8. **Overload**: cố tình giữ tất cả workers bận rồi xem delayed task chỉ chạy khi worker rảnh; kiểm tra memory/backlog nếu gửi nhiều lịch.
9. **Lịch theo calendar**: kiểm tra đổi ngày, múi giờ và trường hợp DST nếu hệ thống cần.
10. **Completion/side effects**: phân biệt “task đã chạy”, “đã retry”, “external operation commit” và “Future canceled”.

**Không nên kiểm tra bằng giả định:** “Thread.sleep(100) xong thì task đã chạy”. Điều đó có thể đúng trên máy này nhưng sai khi hệ thống quá tải. Ưu tiên latch, Future, thông báo trạng thái và invariant. Nếu cần đo thời gian, dùng ngưỡng phù hợp với môi trường và không biến con số timer chính xác thành yêu cầu mà API không hứa.

## 21. Checklist đưa vào ứng dụng

**Thiết kế:**

- [ ] Công việc chạy **một lần** hay **định kỳ**? Đã chọn đúng API?
- [ ] Delay tương đối hay lịch theo giờ địa phương? Đã xác định zone?
- [ ] Có cần kết quả, exception, cancel hoặc deadline? Đã giữ ScheduledFuture?
- [ ] Có yêu cầu lịch sống qua restart/multi-JVM không? Nếu có, có persistent coordinator?
- [ ] Task có thể được thực thi lại không? Có idempotency với external side effect?

**Tải và tài nguyên:**

- [ ] Số worker có phù hợp với CPU-bound/blocking I/O và độ trễ kỳ vọng?
- [ ] Có task dài chiếm worker làm các task sau quá hạn?
- [ ] Số lượng delayed tasks có được giới hạn hoặc đo queue growth?
- [ ] Có xử lý canceled-task retention qua removeOnCancelPolicy khi cần?
- [ ] Có metric scheduled, started, completed, failed, cancelled, overdue, queue size?

**Vòng đời:**

- [ ] Đã quyết định existing delayed tasks có chạy sau shutdown hay bị cancel?
- [ ] Đã quyết định periodic tasks có tiếp tục sau shutdown không?
- [ ] Có shutdown/awaitTermination và fallback phù hợp?
- [ ] Xử lý RejectedExecutionException nếu producer gửi sau shutdown?
- [ ] Nếu caller timeout hoặc Future cancel, task có cleanup hợp tác?
- [ ] Không giữ lock hoặc chiếm worker để đợi task khác phụ thuộc cùng executor?

## 22. Câu hỏi tự kiểm tra

**1. schedule(task, 500, MILLISECONDS) có bảo đảm task bắt đầu chính xác sau 500 ms không?**  
Không. Task **đủ điều kiện** sau delay, còn thời điểm thực sự chạy phụ thuộc worker/scheduler.

**2. ScheduledFuture.getDelay() bằng -100 ms có chắc task đã chạy xong không?**  
Không. Task có thể đã quá hạn nhưng worker chưa rảnh.

**3. Với schedule(Runnable, ...), get() trả giá trị gì khi thành công?**  
**null**, vì Runnable không trả V qua call().

**4. Future.get(timeout) ném TimeoutException có tự hủy lịch không?**  
Không. Caller chỉ ngừng chờ, task có thể vẫn tiếp tục.

**5. cancel(false) và cancel(true) khác nhau ở điểm nào?**  
cancel(false) không yêu cầu interrupt task đang chạy; cancel(true) có thể yêu cầu interrupt, nhưng không ép rollback side effect.

**6. Một periodic task có thể tự chạy hai lần chồng lên nhau trong ScheduledThreadPoolExecutor với bốn worker không?**  
Các lượt **của cùng periodic task** không chồng lấn theo hợp đồng; những periodic task khác nhau có thể chạy đồng thời.

**7. Vì sao periodicFuture.get() có thể chờ mãi?**  
Vì Future đại diện cho cả chuỗi lặp, không hoàn tất bình thường sau một tick. Nó cần bị hủy hoặc kết thúc bất thường để đạt trạng thái cuối.

**8. shutdown() có hủy task one-shot đã được hẹn từ trước nhưng chưa đến hạn theo mặc định không?**  
Không. ScheduledThreadPoolExecutor mặc định cho phép existing delayed one-shot task thực thi sau shutdown.

**9. Dùng scheduleAtFixedRate với period=24 HOURS có hẹn đúng 09:00 giờ địa phương mỗi ngày ở mọi timezone không?**  
Không. Lịch theo giờ địa phương có thể thay đổi offset do DST; phải tính lại thời điểm calendar tương ứng.

**10. Single-thread scheduled executor có thể deadlock khi nào?**  
Khi task đang chạy chiếm worker duy nhất rồi đợi Future của task khác đang chờ trong chính executor đó.

**11. Có thể dựa vào setRemoveOnCancelPolicy(true) để chắc chắn task đã chạy không?**  
Không. Nó chỉ nói về việc gỡ task đã **bị hủy** khỏi queue khi cancel, không chứng minh đã chạy.

**12. ScheduledExecutorService có bảo đảm lịch còn sau restart JVM không?**  
Không. Lịch nằm trong bộ nhớ process; cần persistence/coordination riêng khi nghiệp vụ yêu cầu.

## 23. Tổng kết

**ScheduledExecutorService** là công cụ của JDK giúp **đăng ký công việc chạy sau một độ trễ tương đối** và theo dõi nó bằng **ScheduledFuture**. Phương thức **schedule()** phục vụ tác vụ **một lần**; periodic methods giải quyết một mục tiêu khác là chạy lặp theo **fixed rate** hoặc **fixed delay**.

Điều quan trọng nhất là phân biệt:

- **Đủ hạn chạy** không đồng nghĩa **đã bắt đầu chạy**.
- **Future.get(timeout) hết hạn** không đồng nghĩa **task bị hủy**.
- **Future.cancel()** không đồng nghĩa **mọi side effect đã được hoàn tác**.
- **shutdown()** không nhất thiết **hủy mọi lịch đã đăng ký**.
- **Một scheduler trong JVM** không tương đương **dịch vụ lập lịch bền vững, chính xác giờ lịch và không trùng giữa nhiều process**.

Hãy chọn số worker phù hợp, kiểm soát lượng lịch pending, giữ và quan sát Future, hiểu cancellation/shutdown policies, thiết kế lịch giờ thực với múi giờ rõ ràng và kiểm thử bằng **invariant cùng blocking primitives** thay vì kỳ vọng sleep chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-010">scheduleAtFixedRate() và scheduleWithFixedDelay()</a>

<details>
<summary>Xem chi tiết</summary>

# `scheduleAtFixedRate()` và `scheduleWithFixedDelay()` trong Java

Khi ứng dụng Java cần kiểm tra sức khỏe, làm mới cache, lấy mẫu metric hoặc chạy một công việc định kỳ, `ScheduledExecutorService` cho phép lên lịch thực thi lặp lại mà không phải tự viết vòng `while` cộng `Thread.sleep()`. Hai API phổ biến có bề ngoài gần giống nhau nhưng khác **điểm tính lịch lần chạy tiếp theo**:

- **`scheduleAtFixedRate()`:** mục tiêu chạy ở các mốc thời gian cách đều nhau theo **lịch cố định**, tính từ thời điểm dự kiến của lần chạy đầu. Nếu một lần chạy chậm, lần sau có thể bắt đầu muộn và có thể chạy liên tiếp để bắt kịp lịch; **không có hai lần chạy của cùng periodic task chồng lên nhau**.
- **`scheduleWithFixedDelay()`:** lần sau chỉ được lên lịch sau khi **lần trước kết thúc**, rồi đợi đủ một khoảng delay. Tần suất thực tế phụ thuộc cả thời gian chạy công việc.

Cả hai đều chạy bằng thread pool có lịch của Java SE. **Không có cam kết hard real-time**: thời gian chính xác có thể trễ do scheduler, CPU, queue, garbage collection hoặc task khác. Thêm nữa, một tác vụ định kỳ ném exception không được xử lý sẽ khiến **những lần chạy sau bị ngừng** theo hợp đồng hai API.

Bài thuộc **Future & Scheduling** của **Java concurrency nói chung**, dùng JDK 17+, không dùng annotation, scheduler hoặc runtime riêng của Spring Framework.

## 1. API ở đâu, dùng loại executor nào?

Interface `ScheduledExecutorService` nằm trong `java.util.concurrent` và mở rộng `ExecutorService`. Triển khai thường dùng nhất là `ScheduledThreadPoolExecutor`.

Ví dụ tạo scheduler:

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2);
```

Đây là snippet minh họa, không phải chương trình độc lập. Factory thường tạo một `ScheduledThreadPoolExecutor` với số core thread tương ứng. Hai lựa chọn khác:

```java
ScheduledExecutorService one =
        Executors.newSingleThreadScheduledExecutor();

ScheduledExecutorService several =
        Executors.newScheduledThreadPool(4);
```

`newSingleThreadScheduledExecutor()` cung cấp một scheduler dùng một thread xử lý lần lượt task (và có thể thay worker khi cần); `newScheduledThreadPool(4)` có thể thực thi **các task khác nhau** đồng thời trên tối đa số core worker phù hợp.

Một scheduled executor **không phải một clock timer chính xác tuyệt đối**, và con số core pool không tạo bảo đảm về thời điểm task bắt đầu. Cần thiết kế logic chịu được trễ và quá tải.

## 2. Chữ ký hai phương thức và các tham số

```java
ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command,
        long initialDelay,
        long period,
        TimeUnit unit);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command,
        long initialDelay,
        long delay,
        TimeUnit unit);
```

Đây là khai báo API minh họa, không phải code thêm vào class.

| Tham số | `scheduleAtFixedRate` | `scheduleWithFixedDelay` |
| --- | --- | --- |
| `command` | `Runnable` sẽ chạy nhiều lần | `Runnable` sẽ chạy nhiều lần |
| `initialDelay` | Đợi bao lâu trước mốc lần chạy đầu | Đợi bao lâu trước lần chạy đầu |
| `period` / `delay` | Khoảng cách giữa **các mốc bắt đầu dự kiến** | Khoảng cách giữa **kết thúc lần trước** và **bắt đầu lần sau** |
| `unit` | Đơn vị thời gian của initialDelay và period | Đơn vị thời gian của initialDelay và delay |
| Kết quả | `ScheduledFuture<?>` điều khiển/quan sát lịch | `ScheduledFuture<?>` điều khiển/quan sát lịch |

Đối với cả hai:

- `period` hoặc `delay` phải **dương**; giá trị bằng 0 hoặc âm gây `IllegalArgumentException`.
- `initialDelay` bằng 0 (hoặc âm theo contract của delayed execution) có nghĩa lần đầu được xem là có thể chạy ngay, nhưng vẫn chịu scheduling.
- Nếu command là `null`, contract ném `NullPointerException`.
- Nếu executor không chấp nhận task (ví dụ đã shutdown), có thể gặp `RejectedExecutionException`.
- `Runnable` không trả giá trị nghiệp vụ; `ScheduledFuture<?>` chủ yếu đại diện lifecycle/cancel và exception của task định kỳ, không phải stream kết quả mỗi lần chạy.

## 3. Fixed rate: mốc theo lịch, không theo lúc task kết thúc

Giả sử `initialDelay = 0`, `period = 1 giây`. Các **mốc lý tưởng** lần lượt là:

```text
time (s):  0       1       2       3       4       5
           |-------|-------|-------|-------|-------|
target:    R1      R2      R3      R4      R5      R6
```

Nếu từng lần chạy khoảng 200 ms, scheduler thường có thời gian rảnh giữa các lần. Khi một lần chạy kéo dài 1,6 giây, mốc lần tiếp theo đã đến trước khi lần hiện tại kết thúc:

```text
Mốc lịch:  0       1       2       3       4       5
           |-------|-------|-------|-------|-------|
run R1:    [==============]   (mất khoảng 1,6 s)
run R2:                   [==] (có thể bắt đầu muộn)
run R3:                     [==]
```

Sơ đồ là **minh họa quan hệ thời gian**, không phải output bắt buộc. Java không hứa mốc thực tế cách nhau chính xác 1 giây.

Điều quan trọng:

1. Mục tiêu khoảng cách dựa trên lịch bắt đầu dự kiến, không cộng period sau lúc task hoàn thành.
2. Nếu task bị trễ/overrun, lần sau **được phép bắt đầu muộn**.
3. Các lần chạy có thể gần nhau hoặc liên tiếp khi scheduler đang trễ lịch.
4. **Không chồng lấn các lần thực thi của cùng một periodic task**, ngay cả trong pool có nhiều worker.
5. Các lần liên tiếp có thể chạy trên **những worker thread khác nhau**, và `ScheduledThreadPoolExecutor` bảo đảm hiệu ứng lần trước happens-before lần sau đối với cùng periodic task.

Không nên hiểu "fixed rate" là "bắt buộc cứ 1 giây tạo thêm một bản sao độc lập". `scheduleAtFixedRate` không phải cơ chế tự động nhân bản task khi task cũ chạy chậm.

## 4. Fixed delay: khoảng nghỉ sau khi làm xong

Giả sử `initialDelay = 0`, `delay = 1 giây`; lần thứ nhất chạy 600 ms:

```text
time:      0        0.6        1.6        2.2        3.2
           |--------|----------|----------|----------|
run 1:     [======]
delay:              [----------]
run 2:                         [======]
delay:                                  [----------]
run 3:                                             ...
```

Với `scheduleWithFixedDelay`, lần kế tiếp được lên lịch sau khi **lần hiện tại kết thúc** cộng `delay`. Nếu lần hiện tại mất 2 giây và delay 1 giây, khoảng từ lúc bắt đầu lần này đến lần sau thường xấp xỉ 3 giây cộng trễ scheduling.

Vì vậy:

- Hữu ích cho polling nên nghỉ sau mỗi lần hoàn thành.
- Không cố bắt kịp mốc lịch bị lỡ; hệ thống có khoảng nghỉ được định nghĩa giữa các lần hoàn tất thành công.
- Một lần chạy chậm kéo theo việc lần sau bắt đầu muộn hơn tương ứng.
- Giống fixed rate, **không có hai lần chạy của cùng một periodic task chạy chồng nhau**.

Nếu yêu cầu là "ít nhất 1 giây giữa hai lần gửi network request", fixed delay thường dễ phù hợp hơn fixed rate; nhưng phải còn xét retry, những request khác và deadline của công việc bên trong.

## 5. So sánh ngắn gọn theo trục thời gian

Giả sử task kéo dài 800 ms và khoảng cấu hình 500 ms, không tính scheduler jitter:

| Yếu tố | Fixed rate (period 500 ms) | Fixed delay (delay 500 ms) |
| --- | --- | --- |
| Mốc tính tiếp theo | Mốc bắt đầu theo lịch từ lần đầu | Lúc kết thúc lần trước |
| Khi task chạy 800 ms | Lịch trễ, lần sau có thể bắt đầu gần ngay sau kết thúc | Chờ thêm khoảng 500 ms sau khi kết thúc |
| Khoảng giữa các lần bắt đầu | Có thể gần 800 ms khi task luôn overrun | Xấp xỉ 800 + 500 = 1.300 ms |
| Có catch-up không? | Có thể bắt đầu các lần muộn liên tiếp để đuổi lịch | Không |
| Một periodic task có overlap không? | Không | Không |
| Tần suất gắn với | Mục tiêu cadence của lịch | Chu kỳ làm việc + thời gian nghỉ |

Các số là **ước lượng để học**, không phải thời gian được đảm bảo bởi JVM. Khi hệ thống có scheduler delay lớn, các khoảng quan sát được có thể dài hơn đáng kể.

## 6. Ví dụ Java 17+: cả hai lịch hoàn thành ít nhất ba lần

Ví dụ sau kiểm tra **lifecycle** bằng `CountDownLatch` thay vì đoán khoảng thời gian bằng `Thread.sleep`.

Lưu thành `PeriodicThreeRunsDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PeriodicThreeRunsDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(2);
        CountDownLatch rates = new CountDownLatch(3);
        CountDownLatch delays = new CountDownLatch(3);

        try {
            ScheduledFuture<?> fixedRate =
                    scheduler.scheduleAtFixedRate(
                            rates::countDown,
                            0, 10, TimeUnit.MILLISECONDS);

            ScheduledFuture<?> fixedDelay =
                    scheduler.scheduleWithFixedDelay(
                            delays::countDown,
                            0, 10, TimeUnit.MILLISECONDS);

            boolean rateReached = rates.await(
                    5, TimeUnit.SECONDS);
            boolean delayReached = delays.await(
                    5, TimeUnit.SECONDS);

            fixedRate.cancel(false);
            fixedDelay.cancel(false);

            System.out.println("Rate reached three: " + rateReached);
            System.out.println("Delay reached three: " + delayReached);
            System.out.println("Both canceled: "
                    + (fixedRate.isCancelled()
                            && fixedDelay.isCancelled()));
        } finally {
            scheduler.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac PeriodicThreeRunsDemo.java
java PeriodicThreeRunsDemo
```

**Stdout trên lần chạy bình thường, scheduler nhận được CPU trong 5 giây:**

```text
Rate reached three: true
Delay reached three: true
Both canceled: true
```

Cả hai periodic tasks có thể thực thi hơn ba lần trước khi main cancel vì scheduling của main không tức thời. **Bài cố ý không in số lần thực tế** hay hứa đúng ba lần. `CountDownLatch` chỉ xác minh ít nhất ba invocation đã chạy. `cancel(false)` yêu cầu dừng những lần chạy **tương lai**; không tự interrupt invocation đang chạy.

Dùng `await(timeout)` trong test hạn chế nguy cơ treo vô hạn nếu executor không tiến triển. Trong ứng dụng thật, nếu `await` trả `false` thì phải xử lý sự kiện hết deadline thay vì mặc định thành công.

## 7. Lần chạy của cùng một periodic task không overlap

Có thể nhìn `ScheduledThreadPoolExecutor(4)` và nghĩ bốn thread sẽ chạy song song nhiều lần của **cùng** một công việc cố định nếu period nhỏ. Đó là hiểu nhầm.

Theo hợp đồng `ScheduledThreadPoolExecutor`, successive executions của **một periodic task** không chạy đồng thời. Khi task còn chạy, lần tiếp theo không được phép chồng lên invocation đó, dù pool có worker khác đang rảnh.

Hơn nữa, **hiệu ứng của lần thực thi trước happens-before lần kế tiếp** đối với cùng periodic task. Vì vậy một object task có mutable state riêng mà chỉ được truy cập từ các lần chạy định kỳ **của đúng task đó** có thể dựa vào sequencing này; nhưng nếu state còn được đọc/ghi từ thread khác, vẫn cần đồng bộ/safe publication phù hợp.

Ví dụ Java 17+ xác minh số invocation đồng thời của cùng periodic task:

Lưu thành `NoOverlapDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NoOverlapDemo {
    public static void main(String[] args) throws Exception {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(4);
        CountDownLatch completed = new CountDownLatch(3);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumConcurrent = new AtomicInteger();

        try {
            ScheduledFuture<?> task =
                    scheduler.scheduleAtFixedRate(() -> {
                        int now = active.incrementAndGet();
                        maximumConcurrent.accumulateAndGet(
                                now, Math::max);
                        try {
                            Thread.sleep(60);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            active.decrementAndGet();
                            completed.countDown();
                        }
                    }, 0, 10, TimeUnit.MILLISECONDS);

            boolean reached = completed.await(
                    5, TimeUnit.SECONDS);
            task.cancel(false);

            System.out.println("Three runs observed: " + reached);
            System.out.println("Max concurrent runs: "
                    + maximumConcurrent.get());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
```

```bash
javac NoOverlapDemo.java
java NoOverlapDemo
```

Stdout trên đường chạy bình thường:

```text
Three runs observed: true
Max concurrent runs: 1
```

Task chạy lâu hơn period, nhưng cùng một task không overlap. `active` chỉ được tăng/giảm trong invocation, và `completed.countDown()` sau khi đã giảm. Code dùng Atomic cho phép quan sát metric an toàn nếu thay đổi logic về sau. Nếu main bị scheduler trì hoãn, task có thể thực thi lần thứ tư trước cancel; giá trị **max concurrent** vẫn là 1.

Chú ý hai tác vụ định kỳ **khác nhau** vẫn có thể chạy song song khi pool có đủ worker. Quy tắc không overlap ở đây áp dụng cho những lần chạy của **cùng một ScheduledFuture periodic task**, không phải mọi task trong executor.

## 8. Fixed rate có "bù" các mốc lịch bị lỡ không?

Khi task chạy lâu hơn `period`, các mốc lý tưởng của schedule vẫn tiến. Sau khi một invocation kết thúc, nếu mốc tiếp theo đã ở trong quá khứ, executor có thể cho invocation kế tiếp bắt đầu ngay khi có worker và tài nguyên. Khi tiếp tục trễ, các invocation có thể diễn ra gần nhau.

Nhưng:

- Không có cơ chế chạy **đồng thời nhiều bản sao** của cùng periodic task để bù backlog.
- Không có lời hứa chính xác "mỗi mốc lỡ tương ứng một lần xử lý ở millisecond nào".
- Nếu hệ thống liên tục quá tải, lịch có thể cứ trễ và task gần như chạy liên tục.
- Nếu task throw exception, lịch các lần sau bị ngừng (không tiếp tục bắt kịp).
- Nếu executor shutdown/cancel, quy tắc lifecycle chặn các lần chạy tương lai theo policy tương ứng.

**Một periodic task không phải event log.** Nếu nghiệp vụ phải xử lý tất cả sự kiện xảy ra từng giây kể cả sau khi app bị dừng hoặc restart, một lịch in-memory không cung cấp durability hay exactly-once semantics. Cần dữ liệu mốc/offset/queue hoặc cơ chế persistence riêng.

## 9. Fixed delay giúp có "cool-down" nhưng không bảo đảm tần suất tối thiểu

Với fixed delay 2 giây, sau một invocation hoàn thành bình thường, lần kế tiếp sẽ không được lên lịch bắt đầu **trước khoảng nghỉ đó** theo quy tắc scheduling. Điều này hữu ích khi muốn tránh polling dồn dập.

Tuy nhiên, không có cam kết invocation tiếp theo chắc chắn bắt đầu đúng sau 2 giây; nó có thể bị trễ do scheduler, pool bận hoặc tài nguyên hệ thống. Nếu chính task mất 10 giây, chu kỳ từ lúc bắt đầu lần này tới lần sau sẽ lớn hơn 12 giây.

Do đó, hãy đặt câu hỏi:

- Muốn **cadence cố định** cho việc lấy mẫu? Cân nhắc fixed rate.
- Muốn **nghỉ một khoảng sau khi hoàn tất công việc**? Cân nhắc fixed delay.
- Muốn **một thời điểm cụ thể trên đồng hồ** (ví dụ 02:00 địa phương hằng ngày)? Hai API trên nhận relative delay/period; muốn lịch theo wall clock và timezone thì cần lớp tính lịch khác.
- Muốn **rate limiting toàn bộ request của nhiều task**? Một lịch của từng task riêng không tự là global rate limiter.

## 10. Bảng timeline khi một lần chạy quá chậm

Giả sử mốc bắt đầu đầu là t=0, thời gian chạy mỗi invocation 1,4 giây và cấu hình là 1 giây, bỏ qua scheduler overhead:

```text
Fixed rate, period = 1 s
Lịch mục tiêu: 0, 1, 2, 3, 4, ...
Bắt đầu xấp xỉ: 0, 1.4, 2.8, 4.2, ...
Kết thúc xấp xỉ: 1.4, 2.8, 4.2, ...
=> gần như không có thời gian nghỉ khi liên tục overrun.

Fixed delay, delay = 1 s
Bắt đầu xấp xỉ: 0, 2.4, 4.8, 7.2, ...
Kết thúc xấp xỉ: 1.4, 3.8, 6.2, ...
=> luôn có khoảng nghỉ khoảng 1 s giữa các lần hoàn tất
   và lần bắt đầu tiếp theo.
```

Các số mang tính **lý tưởng hóa để giải thích**, không phải stdout hay deadline runtime đảm bảo.

Với fixed rate, bắt đầu lần thứ hai khoảng t=1,4 s không phải vì period đã đổi thành 1,4 s; đó là do invocation đầu chưa hoàn thành ở mốc t=1. Với fixed delay, mốc lần thứ hai tính **sau** kết thúc invocation đầu nên khoảng t=2,4 s.

## 11. Vì sao không in những khoảng thời gian như output cố định?

Hai lời gọi `System.nanoTime()` đặt trước mỗi invocation có thể giúp **quan sát** khoảng cách bắt đầu nhưng không có kết quả cố định. Nếu in `startDeltaMs` hoặc `currentTimeMillis`, mỗi máy và mỗi lần chạy có thể khác.

Một chương trình đo thực tế nên:

1. Ghi `System.nanoTime()` ngay khi invocation bắt đầu và kết thúc.
2. Tránh in log quá nhiều ngay trong critical path vì logging có thể tạo overhead.
3. Đợi đủ một số invocation bằng latch/atomic counter để lấy mẫu.
4. Tính median/p95 và so với độ dài task/queue contention.
5. Chỉ phát biểu **xu hướng**, không gán output tính theo mili-giây là bất biến.
6. Giữ riêng logic test correctness và benchmark performance; không dùng một con số jitter để kết luận scheduler sai.

`System.nanoTime()` dùng đo **thời lượng trôi qua**, không đại diện ngày giờ thực hoặc timezone. Trong các API scheduling này, các relative delays cũng không đảm bảo đồng bộ với mỗi mốc wall-clock tuyệt đối.

## 12. Exception trong periodic task: lần sau bị ngừng

Hợp đồng của hai phương thức định kỳ đều nói rằng nếu **một invocation ném exception**, các invocation tiếp theo bị **suppressed**. Điều này khác với nhiều ý tưởng cron "lỗi lần này, lần sau vẫn tiếp tục".

Ví dụ:

```java
scheduler.scheduleAtFixedRate(() -> {
    refreshCache(); // Ném RuntimeException nếu refresh thất bại.
}, 0, 1, TimeUnit.MINUTES);
```

Đây là snippet minh họa. Nếu `refreshCache()` ném unchecked exception ra khỏi `Runnable.run()`, lịch đó không tiếp tục ở các phút sau như bình thường.

Điều cần thiết kế:

- Task nào phải **dừng hẳn** khi lỗi để tránh làm hỏng state? Để exception propagate có thể là policy đúng.
- Task nào phải **tiếp tục** sau một lỗi tạm thời? Catch **có chọn lọc**, ghi nhận lỗi, đặt health status và quyết định liệu invocation sau có được phép chạy.
- Nếu exception được catch rồi bỏ qua hoàn toàn, hệ thống có thể tiếp tục lịch trong khi trạng thái dữ liệu vẫn sai. **Đừng dùng catch-all chỉ để "giữ lịch sống"** mà không có policy.
- Nếu task block vĩnh viễn, không có exception và cũng không hoàn tất invocation; scheduler không thể bắt đầu invocation kế của cùng task cho đến khi lần hiện tại kết thúc.

## 13. Ví dụ Java 17+: exception làm periodic future kết thúc

Lưu thành `PeriodicExceptionDemo.java`:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PeriodicExceptionDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        AtomicInteger attempts = new AtomicInteger();

        try {
            ScheduledFuture<?> future =
                    scheduler.scheduleAtFixedRate(() -> {
                        int n = attempts.incrementAndGet();
                        if (n == 2) {
                            throw new IllegalStateException("boom");
                        }
                    }, 0, 10, TimeUnit.MILLISECONDS);

            boolean failed = false;
            String type = "";
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failed = true;
                type = e.getCause().getClass().getSimpleName();
            }

            System.out.println("Failed: " + failed);
            System.out.println("Invocations: " + attempts.get());
            System.out.println("Cause: " + type);
            System.out.println("Future done: " + future.isDone());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
```

```bash
javac PeriodicExceptionDemo.java
java PeriodicExceptionDemo
```

Stdout trên đường chạy bình thường:

```text
Failed: true
Invocations: 2
Cause: IllegalStateException
Future done: true
```

`future.get(5, SECONDS)` đợi future kết thúc exceptional khi invocation thứ hai ném lỗi. Vì exception ngừng schedule, **không có lần thứ ba**. `ExecutionException.getCause()` giúp lấy nguyên nhân. Một invocation thất bại không tự khiến toàn bộ executor shutdown; các task khác của pool có thể tiếp tục theo hợp đồng.

Nếu thay `scheduleAtFixedRate` bằng `scheduleWithFixedDelay` với delay dương, quy tắc suppressed executions khi exception vẫn áp dụng.

## 14. `ScheduledFuture.get()` của periodic task có gì đặc biệt?

Cả hai method trả `ScheduledFuture<?>`. Vì lịch định kỳ chạy lặp lại cho đến khi cancel, exception hoặc shutdown policy kết thúc nó, nên **`future.get()` không trả một result bình thường cho mỗi lần chạy**.

Theo hợp đồng scheduling:

- Nếu periodic task còn được lên lịch liên tục và không kết thúc, `get()` sẽ **chờ** thay vì trả một `List` các kết quả.
- Nếu bị cancel, `get()` ném `CancellationException`.
- Nếu một invocation ném exception không được xử lý, `get()` có thể ném `ExecutionException` với cause tương ứng.
- Một `get(timeout, unit)` có thể ném `TimeoutException` nếu future vẫn chưa done.
- `isDone()` không phải counter của những lần chạy đã hoàn thành; periodic future có thể đã chạy 100 lần mà `isDone() == false`.

Nếu cần **kết quả của từng invocation**, hãy tự báo bằng queue, metric, listener hoặc một cấu trúc result/notification có đồng bộ phù hợp. Đừng gọi `get()` của periodic future sau mỗi lần chạy rồi chờ một kết quả bình thường.

## 15. Ví dụ Java 17+: `get(timeout)` không đếm từng lần lặp

Lưu thành `PeriodicFutureGetDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;

public class PeriodicFutureGetDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        CountDownLatch oneRun = new CountDownLatch(1);

        try {
            ScheduledFuture<?> future =
                    scheduler.scheduleWithFixedDelay(
                            oneRun::countDown,
                            0, 1, TimeUnit.HOURS);

            oneRun.await();

            boolean timedOut = false;
            try {
                future.get(10, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timedOut = true;
            }

            future.cancel(false);

            boolean canceledGet = false;
            try {
                future.get();
            } catch (CancellationException e) {
                canceledGet = true;
            }

            System.out.println("One run completed: "
                    + (oneRun.getCount() == 0));
            System.out.println("Get timed out: " + timedOut);
            System.out.println("Get after cancel: " + canceledGet);
        } finally {
            scheduler.shutdownNow();
        }
    }
}
```

```bash
javac PeriodicFutureGetDemo.java
java PeriodicFutureGetDemo
```

Stdout trên đường chạy bình thường:

```text
One run completed: true
Get timed out: true
Get after cancel: true
```

`oneRun.await()` đợi invocation đầu đến tín hiệu. Periodic future vẫn còn một lượt tiếp theo dự kiến sau **1 giờ**, nên `get(10ms)` timeout, không trả giá trị của lần đầu. Sau `cancel(false)`, `get()` ném `CancellationException`.

Trong trường hợp scheduler bị shutdown hoặc task bị cancel từ nguồn khác trước đó, đường đi có thể khác; ví dụ chỉ có một thread main điều khiển vòng đời pool. `await()` trong demo cố ý không timeout, nhưng invocation đầu không block và pool đang chạy; test harness sản xuất vẫn nên có timeout để tránh treo khi môi trường hỏng.

## 16. Chủ động bắt lỗi: khi nào vẫn nên cho lần sau chạy?

Với một tác vụ polling không quan trọng từng kết quả đơn lẻ, có thể chọn chính sách **log/metric rồi tiếp tục**:

```java
scheduler.scheduleWithFixedDelay(() -> {
    try {
        pollRemoteService();
    } catch (TransientPollingException e) {
        recordFailure(e);
        // Lần kế tiếp vẫn được lên lịch sau delay.
    }
}, 0, 1, TimeUnit.MINUTES);
```

Đây là snippet minh họa với các class/method giả định.

Tuy nhiên:

- Chỉ catch những lỗi **thật sự có thể phục hồi**. Lỗi phá invariant có thể phải dừng schedule.
- Nếu task thực hiện I/O, cần timeout cho **chính I/O**, bởi delay chỉ bắt đầu khi task kết thúc.
- Khi catch `InterruptedException` trong một `Runnable`, thường nên `Thread.currentThread().interrupt()` rồi **thoát theo giao thức hủy**; đừng nuốt interrupt và tiếp tục task như thường.
- Ghi nhận failure rate, consecutive failures và last-success time; đừng chỉ in exception vào console rồi quên.
- Nếu cần retry/backoff, cố định delay 1 phút chưa chắc thích hợp. Có thể quản lý retry bên trong task với deadline hoặc lập lịch **one-shot** theo delay động.

## 17. Cancellation: dừng lịch tương lai khác việc ngắt invocation đang chạy

`ScheduledFuture.cancel(boolean mayInterruptIfRunning)` dùng để yêu cầu hủy periodic task.

- `cancel(false)`: yêu cầu chấm dứt lịch tương lai mà **không yêu cầu interrupt** invocation đang chạy qua tham số này.
- `cancel(true)`: cho phép thử interrupt invocation đang chạy, nếu implementation thực thi phù hợp; không bảo đảm dừng tức thì.
- Khi cancel thành công, `isCancelled()` và `isDone()` phản ánh trạng thái future, **không phải bằng chứng mọi side effect của invocation đã cleanup xong**.
- Task có thể hoàn tất gần thời điểm cancel; xử lý race và idempotency nếu tác vụ đang làm việc bên ngoài.

Mẫu vòng đời cơ bản:

```java
ScheduledFuture<?> handle =
        scheduler.scheduleWithFixedDelay(
                work, 0, 5, TimeUnit.SECONDS);

// Ở nơi quản lý vòng đời phù hợp:
handle.cancel(false);
```

Đây là snippet minh họa, với `scheduler` và `work` đã được khai báo ở nơi khác.

Không dựa vào `future.cancel(false)` để cưỡng bức dừng một HTTP request đã được gửi. Ngay cả `cancel(true)` cũng chỉ là một yêu cầu hợp tác; I/O có thể cần timeout/close riêng.

## 18. Ví dụ Java 17+: `cancel(false)` không hủy công việc đang chạy

Ví dụ dùng latch để tách **đã yêu cầu hủy lịch** khỏi **invocation đã kết thúc**.

Lưu thành `CancelPeriodicDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CancelPeriodicDemo {
    public static void main(String[] args) throws Exception {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();

        try {
            ScheduledFuture<?> future =
                    scheduler.scheduleAtFixedRate(() -> {
                        started.incrementAndGet();
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            finished.countDown();
                        }
                    }, 0, 10, TimeUnit.MILLISECONDS);

            entered.await();
            boolean accepted = future.cancel(false);
            boolean beforeRelease = finished.getCount() == 0;

            release.countDown();
            finished.await();

            System.out.println("Cancel accepted: " + accepted);
            System.out.println("Finished before release: "
                    + beforeRelease);
            System.out.println("Invocations started: "
                    + started.get());
            System.out.println("Future canceled: "
                    + future.isCancelled());
        } finally {
            release.countDown();
            scheduler.shutdownNow();
        }
    }
}
```

```bash
javac CancelPeriodicDemo.java
java CancelPeriodicDemo
```

Stdout trên đường chạy bình thường:

```text
Cancel accepted: true
Finished before release: false
Invocations started: 1
Future canceled: true
```

**Phân tích:**

1. `entered.await()` chắc chắn invocation đầu đã bắt đầu và đang chờ `release`.
2. `cancel(false)` đặt future vào trạng thái canceled; nó **không yêu cầu interrupt** thread đang chờ latch.
3. Ngay trước khi main mở `release`, `finished` chưa về 0, nên invocation chưa hoàn tất; dòng `Finished before release: false` minh họa việc cancel không đồng nghĩa cleanup done.
4. Main mở latch, đợi `finished`, rồi in kết quả. Chỉ một invocation từng được bắt đầu vì những lần tiếp theo đã bị cancel trong khi lần đầu chưa kết thúc.
5. `finally` đảm bảo mở latch nếu gặp lỗi, tránh bỏ mặc worker đợi vô hạn.

Không áp dụng trực tiếp biến `finished` kiểu latch này cho nhiều invocation mà không làm rõ **generation**; một latch count=1 chỉ theo dõi một completion event.

## 19. Tự dừng sau N lần chạy: tránh lưu handle chưa an toàn

Một số ví dụ trên internet viết periodic task tự gọi `future.cancel(false)` thông qua biến `future` gán sau `scheduleAtFixedRate(..., 0, ...)`. Vì lần đầu **có thể chạy trước khi biến được gán**, code đó có race hoặc có nguy cơ `NullPointerException`.

Mẫu an toàn hơn trong chương trình đơn giản là:

1. Tạo task và một `CountDownLatch` đánh dấu N lần đã xảy ra.
2. Main hoặc owner của schedule đợi latch rồi hủy thông qua `ScheduledFuture` đã nhận.
3. Chấp nhận có thể có thêm invocation giữa lúc đạt N và lúc cancel, **trừ khi** task có state machine tự từ chối work vượt N.

Nếu yêu cầu **chính xác N lần side effect**, có thể đặt bộ đếm/claim atomic ngay trong task và không làm side effect ở những invocation vượt N, sau đó owner cancel lịch. Không nhầm "chỉ N side effects" với "scheduler gọi đúng N lần".

Ví dụ logic:

```java
int ordinal = count.incrementAndGet();
if (ordinal > 3) {
    return;
}
performSideEffectFor(ordinal);
```

Đây là snippet minh họa, `count` là `AtomicInteger` và `performSideEffectFor` là method giả định. Nếu external side effect có thể thất bại, cần thêm chính sách hoàn tất/at-least-once/exactly-once riêng; bộ đếm alone không tạo transaction.

## 20. Các task định kỳ khác nhau có thể tranh worker

Quy tắc không-overlap **chỉ áp dụng successive executions của cùng một periodic task**. Trong pool có hai worker:

```java
ScheduledThreadPoolExecutor pool =
        new ScheduledThreadPoolExecutor(2);

pool.scheduleAtFixedRate(this::sampleMetrics,
        0, 1, TimeUnit.SECONDS);

pool.scheduleWithFixedDelay(this::pollResource,
        0, 1, TimeUnit.SECONDS);
```

Đây là snippet minh họa trong class có `sampleMetrics` và `pollResource`.

Hai task trên có thể chạy **đồng thời** và cạnh tranh shared state. Nếu cả hai sửa một `ArrayList` hoặc cùng gửi dữ liệu qua một resource không thread-safe, phải bảo vệ nó hoặc thiết kế ownership riêng. Không có "global lock" chỉ vì cả hai thuộc cùng executor.

Với **một worker**, các task khác nhau được thực thi tuần tự, nhưng một periodic task rất chậm có thể gây **starvation/latency** cho task còn lại. Với nhiều worker, một task bị block lâu sẽ chiếm một worker nhưng task khác có thể chạy nếu còn capacity.

Vì vậy, chọn pool size dựa trên **độ dài task, blocking behavior và số loại periodic work**, không chỉ "mỗi phút có bao nhiêu schedule".

## 21. Ví dụ Java 17+: một worker khiến task khác đợi

Lưu thành `SharedSchedulerContentionDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SharedSchedulerContentionDemo {
    public static void main(String[] args) throws Exception {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        AtomicBoolean releasedBeforeSecond = new AtomicBoolean();

        try {
            pool.schedule(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 0, TimeUnit.MILLISECONDS);

            firstStarted.await();

            pool.schedule(() -> {
                releasedBeforeSecond.set(
                        releaseFirst.getCount() == 0);
                secondFinished.countDown();
            }, 0, TimeUnit.MILLISECONDS);

            boolean secondBeforeRelease =
                    secondFinished.getCount() == 0;

            releaseFirst.countDown();
            secondFinished.await();

            System.out.println("Second ran before release: "
                    + secondBeforeRelease);
            System.out.println("Second saw release: "
                    + releasedBeforeSecond.get());
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }
}
```

```bash
javac SharedSchedulerContentionDemo.java
java SharedSchedulerContentionDemo
```

Stdout trên đường chạy bình thường:

```text
Second ran before release: false
Second saw release: true
```

Vì pool chỉ có **một worker**, task đầu giữ worker trong khi chờ latch; task thứ hai không thể chạy trước khi task đầu rời chỗ chờ. Khi main mở latch, worker xử lý xong task đầu rồi mới có thể thực hiện task thứ hai.

Ví dụ dùng one-shot `schedule` để minh họa **worker contention** rõ ràng, không phụ thuộc period hoặc delay. Nguyên tắc tương tự áp dụng khi một invocation periodic chiếm worker quá lâu. Nếu pool có hai worker, hai task khác nhau có thể chạy cùng lúc và kết quả kiểm tra phải đổi.

## 22. `shutdown()` và tác vụ định kỳ có tiếp tục không?

Với `ScheduledThreadPoolExecutor`, shutdown policy quan trọng:

- **`setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean)`:** quyết định liệu những periodic tasks đã được lập lịch có tiếp tục chạy **sau `shutdown()`** hay không. Mặc định là **`false`**.
- **`setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean)`:** quyết định liệu những one-shot delayed tasks đã được nhận có chạy sau `shutdown()` hay không. Mặc định là **`true`**.
- `shutdownNow()` yêu cầu dừng công việc đang chạy, cố ngăn các task đợi và trả danh sách những task chưa bắt đầu theo hợp đồng; không bảo đảm task đang chạy ngừng ngay.

Đừng gộp **delayed one-shot** với **periodic** khi nói về shutdown. Một scheduler đã shutdown không tiếp nhận task mới, nhưng số task đã được nhận còn có thể chịu policy riêng.

Nếu bật continue-existing-periodic-after-shutdown, periodic tasks có thể tiếp tục được thực hiện, khiến `awaitTermination()` không hoàn tất cho đến khi chúng được cancel hoặc chấm dứt theo điều kiện khác. Phải lưu các `ScheduledFuture` handle và thiết kế lifecycle rõ ràng.

Ví dụ cấu hình minh họa:

```java
ScheduledThreadPoolExecutor pool =
        new ScheduledThreadPoolExecutor(2);

pool.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
```

Đây là các giá trị default nhưng viết tường minh giúp người đọc biết chính sách. Nếu đang quản lý một pool được **chia sẻ**, đừng tự ý đổi policy hoặc shutdown từ một component không sở hữu executor.

## 23. `removeOnCancelPolicy` và queue giữ task đã cancel

`ScheduledThreadPoolExecutor` sử dụng delayed work queue. Một task được schedule rất xa trong tương lai rồi cancel có thể **vẫn còn trong queue** cho đến khi thời điểm delay tới, nếu áp dụng policy mặc định giữ entry.

Với nhiều task bị hủy trước khi tới deadline, điều này có thể khiến queue giữ thêm reference và tăng memory retention dù task sẽ không chạy.

Có thể bật:

```java
ScheduledThreadPoolExecutor pool =
        new ScheduledThreadPoolExecutor(2);
pool.setRemoveOnCancelPolicy(true);
```

Đây là snippet minh họa. `setRemoveOnCancelPolicy(true)` yêu cầu loại bỏ ngay những task bị cancel khỏi work queue khi được cancel, giúp giảm thời gian giữ reference khi có nhiều task delayed/periodic bị hủy. `getRemoveOnCancelPolicy()` cho biết policy hiện hành.

Không suy rằng việc task rời queue chứng minh task đang chạy đã kết thúc. **Queue retention** và **worker execution/cancellation** là hai vấn đề khác nhau.

Tránh trực tiếp `getQueue().remove(...)` hoặc chỉnh queue thay cho việc lưu `ScheduledFuture` handle và dùng `cancel`; thao tác tay có thể phá lifecycle tracking của executor.

## 24. Nếu task chạy lâu: tách scheduling khỏi công việc blocking

Một phương thức định kỳ có thể gọi HTTP, đọc file hoặc chờ downstream service. Nếu thao tác blocking không có timeout:

- Invocation hiện tại có thể kéo dài không giới hạn.
- Không có invocation tiếp theo của **cùng periodic schedule** trước khi lần này kết thúc.
- `scheduleWithFixedDelay` không thể bắt đầu delay sau một invocation chưa hoàn tất.
- `scheduleAtFixedRate` cũng không thể tự spawn invocation khác để vượt qua lần đang block.
- Các task khác có thể bị chậm nếu chung worker pool nhỏ.

Giải pháp phụ thuộc yêu cầu:

1. **Đặt timeout cho I/O** bằng API đúng của client/channel.
2. **Định nghĩa deadline** cho cả nghiệp vụ, thay vì chỉ quy định period/delay của scheduler.
3. Nếu cần một lượt không chồng lấn và có thể chờ, dùng scheduling tuần tự cùng chính sách timeout.
4. Nếu cần concurrency thực sự, tách timer khỏi worker pool và dùng **bounded concurrency / queue / semaphore**, nhưng phải quản lý overlap, backpressure, memory và cancellation.
5. **Không tự submit vô hạn** một task mới mỗi giây khi downstream đang kẹt; backlog có thể tăng không giới hạn.
6. Dùng metric `lastSuccess`, duration, failure count và queue depth để quan sát độ trễ thực tế.

Bản chất: `period` và `delay` quyết định **lịch gọi**, không áp một hard timeout cho thân task.

## 25. Ví dụ một-shot tự lập lịch lại: khi khoảng delay cần thay đổi

`scheduleWithFixedDelay` có **delay cố định**. Nếu cần exponential backoff hoặc lịch theo kết quả lần trước, một phương án là **schedule one-shot task rồi tự lập lịch lượt sau**.

Phác thảo:

```java
void pollOnce() {
    long nextDelayMillis;
    try {
        doPoll();
        nextDelayMillis = 1_000L;
    } catch (TransientException e) {
        nextDelayMillis = 5_000L;
    }

    if (!stopping.get()) {
        scheduler.schedule(this::pollOnce,
                nextDelayMillis, TimeUnit.MILLISECONDS);
    }
}
```

Đây là snippet minh họa, không phải chương trình standalone. `scheduler` là `ScheduledExecutorService`, `stopping` là trạng thái được công bố an toàn, `doPoll`/`TransientException` thuộc ứng dụng.

Những điều phải thiết kế thêm:

- Race giữa `stopping` và `schedule`: shutdown có thể xảy ra ngay sau kiểm tra, gây `RejectedExecutionException`; xử lý có chủ đích.
- Nếu muốn bảo đảm không còn task sau stop, quản lý scheduled handle và cancellation.
- Nếu exception khác thoát ra trước bước reschedule, chuỗi có thể dừng.
- Retry cần giới hạn và jitter phù hợp để tránh nhiều client đồng loạt retry.
- Một-shot scheduling không mang tự động semantics "same periodic task successive executions HB" theo cách periodic API; các task mới vẫn cần publication và đồng bộ đúng với state chia sẻ.

## 26. Fixed rate không phải cron hoặc lịch thời gian thực

Khi dùng:

```java
scheduler.scheduleAtFixedRate(
        this::refresh, 0, 24, TimeUnit.HOURS);
```

đây là snippet minh họa. Nó đặt một chu kỳ **relative 24 giờ**, không tự tương đương "mỗi ngày đúng 08:00 theo timezone địa phương". Giờ thực có daylight saving time, hệ thống có thể restart, và target relative cũng có scheduler delay.

Một lịch calendar cần xử lý:

- Timezone và daylight saving time.
- Lần chạy bị lỡ khi process tắt.
- Duplicate execution khi có nhiều instance JVM.
- Idempotency/transaction semantics.
- Chính sách catch-up hoặc skip missed occurrences.
- Persistence của lịch và state qua restart.

`ScheduledExecutorService` là công cụ chạy task định kỳ **trong một JVM đang sống**, không phải distributed scheduler hay durable job coordinator.

## 27. Đặt tên thread và tách pool theo tác vụ

Nếu scheduler có nhiều loại task, đặt tên worker giúp chẩn đoán:

```java
AtomicInteger sequence = new AtomicInteger();

ThreadFactory factory = runnable -> {
    Thread t = new Thread(runnable);
    t.setName("metrics-scheduler-" + sequence.incrementAndGet());
    return t;
};

ScheduledThreadPoolExecutor pool =
        new ScheduledThreadPoolExecutor(2, factory);
```

Đây là snippet minh họa, cần import `AtomicInteger`, `ThreadFactory` và `ScheduledThreadPoolExecutor`.

Nên cân nhắc pool riêng khi một nhóm task có blocking I/O dài hoặc có yêu cầu latency khác hẳn nhóm còn lại. Tuy nhiên, tạo quá nhiều pool cũng tiêu thụ thread và tài nguyên; cần quan sát workload thực tế.

Nếu pool được dùng chung bởi nhiều component, phải xác định **ai sở hữu shutdown và policy**. Không để một listener tùy ý gọi `shutdownNow()` rồi vô tình ngừng lịch của component khác.

## 28. Thực hành quản lý lifecycle đúng

Một public class sở hữu scheduler nên có chính sách start/stop rõ. Khung:

```java
final class PeriodicService implements AutoCloseable {
    private final ScheduledThreadPoolExecutor scheduler =
            new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> handle;

    synchronized void start() {
        if (handle != null) {
            throw new IllegalStateException("Already started");
        }
        handle = scheduler.scheduleWithFixedDelay(
                this::tick, 0, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        // Thực hiện một bước có timeout/cancellation phù hợp.
    }

    @Override
    public synchronized void close() {
        if (handle != null) {
            handle.cancel(false);
        }
        scheduler.shutdown();
    }
}
```

Đây là class minh họa chưa có `main`; cần import `ScheduledThreadPoolExecutor`, `ScheduledFuture` và `TimeUnit`.

Điểm cần bổ sung trong production:

- Quy định `close()` có idempotent hay không và có chờ task đang chạy hoàn thành không.
- Tránh giữ monitor `this` khi **chờ worker** đang có khả năng gọi synchronized method của cùng object.
- Kiểm tra `scheduler.isShutdown()` hoặc state machine nếu `start` có thể được gọi sau close.
- `cancel(false)` không interrupt một tick đang chạy; nếu cần thời hạn shutdown, phải định nghĩa `awaitTermination(timeout)` và escalation.
- Nếu `tick` throw exception, future sẽ exceptional; cần báo health status thay vì im lặng.
- Nếu `tick` gọi external system, cần policy idempotency/timeout.

`AutoCloseable.close()` chỉ giúp caller biết có nơi đóng, không tự tạo transaction hay đợi cleanup nếu implementation không thực hiện điều đó.

## 29. Cách chọn API theo yêu cầu

| Yêu cầu | Gợi ý |
| --- | --- |
| Lấy mẫu metric định kỳ theo cadence danh nghĩa | `scheduleAtFixedRate` |
| Poll remote endpoint, nghỉ N giây sau mỗi poll | `scheduleWithFixedDelay` |
| Task phải có retry/backoff động | One-shot `schedule` tự lên lịch lại theo kết quả |
| Chỉ chạy một lần sau delay | `schedule` |
| Mỗi 08:00 địa phương và đúng DST | Tính lịch theo calendar/timezone, không chỉ fixed rate 24h |
| Không được overlap một periodic task | Cả hai periodic API đều có bảo đảm không overlap |
| Nhiều task khác nhau cần chạy đồng thời | Pool có đủ worker, và tự bảo vệ shared state |
| Chạy quá lâu phải bị hủy | Tách timeout/cancellation của task ra khỏi period/delay |
| Lỗi tạm thời không được làm lịch mất luôn | Catch/record theo failure policy bên trong task |
| Shutdown phải ngừng periodic jobs | Lưu handles, cancel, shutdown và chọn policies phù hợp |

**Mục tiêu sản phẩm quan trọng hơn tên API.** Fixed rate phù hợp với sampling cadence, nhưng nếu downstream không chịu nổi các lần chạy sát nhau sau overrun, fixed delay hoặc backoff động có thể bền vững hơn.

## 30. Những lỗi phổ biến

**1. "Fixed rate tạo thêm nhiều bản sao chạy chồng nhau khi task chậm."** Sai. Các lần chạy của một periodic task không overlap.

**2. "Fixed delay tính từ thời điểm bắt đầu lần trước."** Sai. Delay tính từ lúc lần trước **kết thúc**.

**3. "Fixed rate chính xác từng mili-giây."** Sai. Scheduler/JVM không cung cấp hard real-time guarantee.

**4. "Fixed rate không bao giờ chạy hai lần liên tiếp sát nhau."** Sai. Sau một overrun, các lần chạy có thể gần nhau khi catch-up lịch.

**5. "Task ném RuntimeException thì lần sau vẫn luôn chạy."** Sai. Các periodic invocations tiếp theo bị suppressed khi exception thoát ra khỏi task.

**6. "`ScheduledFuture.get()` trả kết quả của một lần chạy định kỳ."** Sai. Nó chờ state cuối của periodic future; không phải stream kết quả mỗi invocation.

**7. "`cancel(false)` dừng ngay invocation đang chạy."** Sai. Nó không yêu cầu interrupt invocation đó.

**8. "`cancel(true)` buộc side effect bên ngoài rollback."** Sai. Interruption chỉ là yêu cầu hợp tác.

**9. "Một pool bốn worker cho cùng task chạy song song bốn lần."** Sai. Nhiều worker giúp các task **khác nhau** chạy concurrent, không phá non-overlap của cùng periodic task.

**10. "Cấu hình `period=0` nghĩa chạy nhanh nhất có thể."** Sai. Period/delay phải dương; giá trị không hợp lệ bị từ chối.

**11. "`initialDelay=0` có nghĩa method schedule sẽ đợi command chạy xong."** Sai. Schedule trả handle; command chạy bất đồng bộ khi executor có worker.

**12. "Shutdown mặc định vẫn cho mọi periodic task chạy mãi."** Sai. Default continue-existing-periodic policy của `ScheduledThreadPoolExecutor` là false.

**13. "Đã cancel thì task được loại khỏi queue ngay trong mọi cấu hình."** Không mặc định; xem `setRemoveOnCancelPolicy(true)`.

**14. "Thêm `Thread.sleep(period)` bên trong command sẽ tương đương fixed delay."** Sai. Sleep làm dài thân invocation, không thay contract thời điểm scheduling; có thể phá throughput và làm catch-up của fixed rate.

**15. "Một periodic task in số lần rất nhiều chứng minh không có race."** Không. Shared state và các task khác vẫn cần synchronization theo Java Memory Model.

**16. "`scheduleAtFixedRate` là hệ thống đảm bảo exactly-once qua restart."** Không. Nó chỉ lên lịch in-memory trong executor đang chạy.

## 31. Câu hỏi tự kiểm tra

**Câu 1:** `scheduleAtFixedRate(task, 0, 1, SECONDS)`, mỗi invocation chạy 2 giây. Executor có chạy đồng thời hai invocation của cùng task không?

**Không.** Lần sau có thể bắt đầu muộn và gần sát khi lần trước kết thúc; không overlap cùng periodic task.

**Câu 2:** Với `scheduleWithFixedDelay(task, 0, 1, SECONDS)`, task mất 2 giây. Khoảng bắt đầu các lần liên tiếp khoảng bao lâu khi không có trễ scheduling?

Xấp xỉ **3 giây**: 2 giây làm việc cộng 1 giây nghỉ sau khi hoàn tất.

**Câu 3:** Vì sao `period=0` không thể dùng để chạy "liên tục"?

Vì API yêu cầu `period > 0`, nếu không ném `IllegalArgumentException`. Nếu muốn một worker liên tục xử lý queue, thường nên dùng consumer loop có hủy/lifecycle rõ hơn.

**Câu 4:** `ScheduledFuture.isDone()` false nhưng task đã chạy 1.000 lần, có mâu thuẫn không?

Không. Periodic future còn active; `isDone` không phải số lần hoàn tất.

**Câu 5:** Một invocation ném `IllegalStateException` ra ngoài `Runnable`, chuyện gì xảy ra?

Các invocation định kỳ kế tiếp bị ngừng. Có thể quan sát failure qua `ScheduledFuture.get()` ném `ExecutionException` với cause tương ứng.

**Câu 6:** Khi task làm I/O chậm, nên fixed rate hay fixed delay?

Tùy contract. Fixed delay thường hợp khi muốn nghỉ sau khi request kết thúc; fixed rate hợp khi muốn cadence danh nghĩa nhưng có nguy cơ chạy sát nhau sau overrun. Cả hai vẫn cần timeout cho I/O.

**Câu 7:** Khi một periodic task bị cancel với `false` trong lúc đang chờ latch, nó có tự thức không?

Không. `cancel(false)` không yêu cầu interrupt invocation đang chạy. Cần tín hiệu/lifecycle riêng để kết thúc.

**Câu 8:** Khi `shutdown()`, periodic tasks có tiếp tục mặc định không với `ScheduledThreadPoolExecutor`?

Mặc định **không**; policy `getContinueExistingPeriodicTasksAfterShutdownPolicy()` mặc định false. Delayed one-shot tasks có chính sách khác.

**Câu 9:** `scheduleAtFixedRate` có thay thế cron hằng ngày theo timezone không?

Không. Nó sử dụng chu kỳ tương đối, không tự giải quyết timezone, DST, restart hoặc distributed ownership.

**Câu 10:** Tại sao cần `setRemoveOnCancelPolicy(true)` nếu ứng dụng có nhiều task delayed bị cancel?

Để các entry canceled được loại sớm khỏi delayed queue, giảm thời gian giữ reference và tránh retention không cần thiết.

**Câu 11:** Hai periodic tasks khác nhau trong pool hai worker có được sửa một `HashMap` ordinary chung không?

Chỉ khi có giao thức đồng bộ bảo vệ phù hợp; scheduler không tự làm `HashMap` thread-safe.

**Câu 12:** Muốn điều chỉnh delay sau mỗi lỗi, nên làm gì?

Dùng one-shot scheduling với delay được tính từ trạng thái/lần thất bại, có giới hạn retry và lifecycle rõ; fixed-delay periodic API chỉ nhận một delay cố định.

## 32. Checklist triển khai và vận hành

Trước khi đưa lịch vào production:

1. **Mục tiêu thời gian là gì?** Mốc bắt đầu danh nghĩa hay khoảng nghỉ sau hoàn thành?
2. **Task tối đa chạy bao lâu?** Nếu có I/O, cấu hình timeout/deadline cho chính tác vụ.
3. **Có chấp nhận catch-up sát nhau không?** Nếu không, fixed delay/backoff có thể phù hợp.
4. **Có shared mutable state không?** Chỉ cùng periodic task được non-overlap; task khác vẫn concurrent.
5. **Ai sở hữu ScheduledFuture?** Giữ handle để cancel đúng lịch.
6. **Exception có làm mất schedule không?** Xác định policy recoverable vs fatal, báo health/metric.
7. **Có cần kết quả mỗi invocation không?** Không dựa vào `ScheduledFuture.get()` để lấy stream kết quả.
8. **Khi close, có interrupt invocation đang chạy không?** Chọn `cancel(false/true)` theo hợp đồng.
9. **Có timeout chờ shutdown không?** Dùng `awaitTermination` và escalation phù hợp; không block vô hạn trong monitor.
10. **Task canceled có giữ queue reference lâu không?** Cân nhắc `setRemoveOnCancelPolicy(true)`.
11. **Có cần calendar/DST hoặc durability không?** Đừng dùng fixed rate như một distributed persistent scheduler.
12. **Dữ liệu task bị lỗi hoặc chạy lại có idempotent không?** Cân nhắc kết quả tới muộn, retry và partial side effect.
13. **Pool có đủ worker cho các task khác nhau không?** Một task blocking dài có thể gây trễ task khác.
14. **Có backpressure và resource limit không?** Không submit vô hạn một task mới mỗi tick.
15. **Đo đạc gì?** Scheduled-vs-actual start delay, invocation duration, success/failure count, longest gap, queue depth.
16. **Test có dựa vào sleeping time chính xác không?** Dùng latches và kiểm tra invariant; log timestamp chỉ để quan sát.

## 33. Tổng kết

- **`scheduleAtFixedRate()`** nhắm tới các **mốc bắt đầu cách đều** theo thời gian tương đối; nếu task chậm, lần sau có thể bắt đầu muộn và những lần chạy có thể nối sát nhau.
- **`scheduleWithFixedDelay()`** bắt đầu đợi `delay` **sau khi lần trước kết thúc**; chu kỳ bắt đầu-bắt đầu gồm thời gian task + delay + trễ scheduling.
- **Cả hai không cho những lần chạy của cùng một periodic task overlap**, nhưng các task khác trong pool có thể thực thi đồng thời và cần bảo vệ shared state.
- **Exception thoát khỏi periodic Runnable ngừng các lần chạy sau.** Hãy thiết kế failure policy và theo dõi trạng thái của `ScheduledFuture`.
- **`ScheduledFuture.get()` không trả kết quả của từng tick**; cancel, shutdown và exceptional completion định nghĩa điểm kết thúc lifecycle.
- **Scheduling interval không phải task timeout.** Công việc blocking vẫn cần deadline, cancellation và cleanup riêng.
- **`ScheduledThreadPoolExecutor` có policy shutdown và remove-on-cancel riêng.** Chúng ảnh hưởng lifecycle và memory retention.
- **Hai API này không phải cron calendar, hard-real-time timer hoặc durable distributed job scheduler.** Yêu cầu đúng giờ theo timezone, bù lịch bị lỡ hay exactly-once qua restart cần giải pháp khác.

Lựa chọn đúng nhất xuất phát từ **ý nghĩa nghiệp vụ của khoảng thời gian**: bạn muốn công việc cố gắng xuất hiện theo nhịp đồng hồ, hay muốn một khoảng nghỉ chắc chắn giữa những lượt đã hoàn tất? Khi hiểu rõ ranh giới đó, cộng với exception/cancellation/shutdown contract của Java, việc lập lịch sẽ dễ dự đoán hơn nhiều so với tự kết hợp `while` và `Thread.sleep()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-011">Exception trong tác vụ lặp và tác động tới lần chạy sau</a>

<details>
<summary>Xem chi tiết</summary>

# Exception trong tác vụ lặp và tác động tới lần chạy sau (Java ScheduledExecutorService)

> **Phạm vi:** **Future & Scheduling** trong Java SE 17+, đặc biệt là cách `ScheduledExecutorService.scheduleAtFixedRate()` và `scheduleWithFixedDelay()` xử lý exception phát sinh từ một lần chạy và ảnh hưởng của nó đến toàn bộ chuỗi lần chạy sau. Bài học áp dụng cho **concurrency Java nói chung**; các API scheduler, job annotation hoặc lifecycle riêng của Spring được dành cho chương khác.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt **task chạy một lần** với **task lặp định kỳ**, và phân biệt `scheduleAtFixedRate()` với `scheduleWithFixedDelay()`.
- Phân tích đúng quy tắc API: **một lần thực thi của task định kỳ ném exception không được xử lý → những lần thực thi tiếp theo của chính task đó bị ngăn lại (suppressed)**.
- Biết cách quan sát một periodic task dừng vì lỗi qua `ScheduledFuture.isDone()` và `get()`, không ngộ nhận rằng `get()` của một periodic task vẫn đang hoạt động sẽ trả kết quả thông thường.
- Phân biệt `ExecutionException`, `CancellationException`, `TimeoutException`, `InterruptedException` và `RejectedExecutionException`.
- Giải thích vì sao `ScheduledThreadPoolExecutor.afterExecute(Runnable, Throwable)` thường nhận `Throwable == null` **kể cả khi scheduled command lỗi**.
- Quyết định khi nào **bắt và xử lý exception trong mỗi lần chạy** để cho phép lần tiếp theo, khi nào **để lỗi thoát ra để dừng schedule**, và khi nào cần alert/restart thủ công.
- Xây một chương trình Java 17 chạy được, xác minh bằng `Future.get(timeout)` và `CountDownLatch` thay vì đoán thứ tự thread bằng `Thread.sleep()`.
- Phân tích retry, backoff, shutdown, idempotency, memory visibility, overlap, metrics và những lỗi khiến một tác vụ định kỳ ngừng âm thầm.

**Quy tắc quan trọng nhất:** `scheduleAtFixedRate` / `scheduleWithFixedDelay` không mặc định “thử lại ở chu kỳ sau” khi `Runnable.run()` ném exception. Trên một chuỗi periodic scheduled task, ngoại lệ **thoát khỏi lần chạy** là một sự kiện kết thúc bất thường; các lần tiếp theo của **cùng ScheduledFuture** bị suppressed.

## 2. Vì sao đây là một lỗi vận hành dễ bỏ sót?

Một tác vụ mỗi 10 giây cập nhật dữ liệu trong bộ nhớ hoặc đồng bộ cache có thể chạy tốt hàng giờ. Một lần nó nhận đầu vào không hợp lệ và ném `RuntimeException`:

```java
scheduler.scheduleAtFixedRate(() -> {
    refreshCache(); // Có thể throw RuntimeException.
}, 0, 10, TimeUnit.SECONDS);
```

Đây là **đoạn code minh họa** đặt trong method; cần `scheduler` là `ScheduledExecutorService`, `refreshCache()` đã được định nghĩa và import `java.util.concurrent.TimeUnit`.

Nếu `refreshCache()` ném exception ra khỏi `Runnable`, nhiều người chờ rằng “đến 10 giây sau nó tự chạy lại”. Với `ScheduledExecutorService` chuẩn, **điều ngược lại mới đúng**: task định kỳ đó **không còn được chạy ở các chu kỳ sau**. Tác vụ khác trong cùng scheduler vẫn có thể chạy bình thường, vì trạng thái lỗi thường thuộc về **cụ thể ScheduledFuture** này, không có nghĩa toàn bộ thread pool đã chết.

Hậu quả có thể nghiêm trọng:

- Dữ liệu cache trở nên cũ, nhưng ứng dụng vẫn đáp ứng HTTP request.
- Mỗi ngày một lần đối soát không còn chạy, trong khi health check của process vẫn xanh.
- Monitoring chỉ theo dõi JVM sống/chết mà không theo dõi “lần chạy thành công cuối cùng”, nên không phát hiện tác vụ đã dừng.
- Log lỗi một lần bị bỏ lỡ hoặc exception được giữ trong Future khiến không ai kiểm tra.

Do đó cần thiết kế **error policy + completion monitoring** cho từng periodic task, thay vì trông cậy scheduler tự restart.

## 3. Ôn lại ScheduledExecutorService

`ScheduledExecutorService` mở rộng `ExecutorService` và cung cấp các phương thức để chạy trễ một lần hoặc chạy lặp:

| API | Mục đích | Số lần chạy | Giá trị trả về |
|---|---|---|---|
| `schedule(Runnable, delay, unit)` | Thực thi một Runnable sau khoảng trễ | Một lần | `ScheduledFuture<?>` |
| `schedule(Callable<V>, delay, unit)` | Thực thi Callable có kết quả sau khoảng trễ | Một lần | `ScheduledFuture<V>` |
| `scheduleAtFixedRate(Runnable, initialDelay, period, unit)` | Lặp theo **mốc bắt đầu** theo chu kỳ | Nhiều lần, cho đến khi dừng | `ScheduledFuture<?>` |
| `scheduleWithFixedDelay(Runnable, initialDelay, delay, unit)` | Lặp theo **khoảng nghỉ sau khi lần trước kết thúc** | Nhiều lần, cho đến khi dừng | `ScheduledFuture<?>` |

`ScheduledFuture` mở rộng `Delayed` và `Future`; bạn có thể dùng `cancel()`, `isDone()`, `isCancelled()` và `get()` theo semantics của từng loại tác vụ.

### Ví dụ tạo scheduler

```java
ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2);
```

Đây là đoạn code trong một method, với imports `java.util.concurrent.Executors` và `ScheduledExecutorService`. Lời gọi tạo scheduled executor có số **core threads** là 2; nó không có nghĩa rằng một periodic task sẽ chạy **hai bản đồng thời**.

**Quan trọng về API:** với hai phương thức periodic, `period` và `delay` phải lớn hơn 0; `initialDelay` có thể bằng 0. Một scheduler không phải cron/calendar scheduler: nó tính delay tương đối, không hứa thời điểm wall-clock tuyệt đối hoặc lịch biểu theo timezone.

## 4. Fixed rate và fixed delay: khác nhau ở điểm nào?

### `scheduleAtFixedRate()`

Cú pháp:

```java
ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
        task, initialDelay, period, TimeUnit.MILLISECONDS);
```

Đây là mã minh họa; `scheduler`, `task`, `initialDelay` và `period` đã có trong method.

Các lần bắt đầu **theo lịch danh nghĩa** tại các mốc:

```text
t0 + initialDelay
t0 + initialDelay + period
t0 + initialDelay + 2*period
...
```

Nếu một lần chạy kéo dài **hơn period**, những lần sau có thể **bắt đầu muộn**. Executor không chạy song song hai lần thực thi của **cùng một periodic task** chỉ vì nó bị trễ. Sau khi một lần chậm kết thúc, lần kế tiếp có thể đủ điều kiện chạy gần như ngay mà không cần nghỉ đủ một period nữa.

Ví dụ period 100 ms, lần đầu chạy 260 ms:

```text
Mốc danh nghĩa: 0 ms, 100 ms, 200 ms, 300 ms ...
Thực tế:       chạy #1 [0----------------260]
               chạy #2                    [260--...]
               #3, #4 có thể tiếp tục trễ tùy tải và lịch
```

Sơ đồ minh họa, không phải cam kết start time tuyệt đối. “Fixed rate” không có nghĩa scheduler bỏ việc đang chạy và tạo một bản chạy song song ở mỗi mốc.

### `scheduleWithFixedDelay()`

Cú pháp:

```java
ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
        task, initialDelay, delay, TimeUnit.MILLISECONDS);
```

Đây là mã minh họa với các biến tương ứng đã có.

Lần tiếp theo đủ điều kiện chạy **sau khi lần trước kết thúc + delay**. Nếu task mất 260 ms và delay 100 ms:

```text
run #1:      [0----------------260]
delay:                            [260----360]
run #2:                                        [360--...]
```

Điểm khác biệt là **khoảng nghỉ sau completion**, không phải đuổi theo các mốc fixed-rate.

### So sánh nhanh

| Thuộc tính | Fixed rate | Fixed delay |
|---|---|---|
| Bắt đầu dựa trên | Mốc chu kỳ lý thuyết | Thời gian lần trước hoàn tất |
| Khi task chậm hơn period/delay | Những lần sau có thể khởi động muộn, có thể nối tiếp sát nhau | Sau completion vẫn đợi khoảng delay yêu cầu |
| Hai lần của **cùng periodic task** chạy chồng lên nhau? | Không theo contract của `ScheduledThreadPoolExecutor` | Không theo contract |
| Một lần ném exception thoát khỏi `run()` | **Ngăn các lần tiếp theo** | **Ngăn các lần tiếp theo** |
| `ScheduledFuture.get()` khi đang chạy định kỳ bình thường | Không trả về bình thường | Không trả về bình thường |

Lựa chọn dựa trên semantics: “cứ mỗi khoảng mốc tính từ lịch” hay “nghỉ N đơn vị sau lần chạy trước”. **Quy tắc exception giống nhau cho cả hai**.

## 5. Quy tắc chính thức khi một lần chạy ném exception

Đặc tả `ScheduledExecutorService` nói rõ đối với `scheduleAtFixedRate` và `scheduleWithFixedDelay`:

- Chuỗi periodic executions kéo dài đến khi **Future bị cancel**, executor chấm dứt theo policy, hoặc **một lần thực thi của task ném exception**.
- Khi exception thoát ra, **những lần thực thi kế tiếp bị suppressed**.
- `ScheduledFuture.get()` ném `ExecutionException`, trong đó cause là exception của task.
- `isDone()` chuyển thành **true** sau khi kết thúc bất thường.
- Periodic `get()` **không trả về bình thường** khi task đang lặp. Nó chỉ thoát bằng exception do canceled/failed hoặc điều kiện kết thúc được API quy định.

### Đọc sơ đồ trạng thái

```text
             TASK PERIODIC ĐƯỢC LÊN LỊCH
                        |
                  run #1 / #2 / ...
                   /           \
           run() return        run() throws
           bình thường          throwable
               |                    |
       tính lần chạy sau      Future completed
       theo fixed rate/delay  exceptionally
               |                    |
           chạy tiếp          suppress lần chạy sau
               |
       cho tới cancel/shutdown
```

Điểm quyết định là **exception có thoát khỏi `Runnable.run()` hay không**. Nếu code tự bắt lỗi rồi `run()` trở về bình thường, scheduler có cơ sở tiếp tục lập lịch; việc một xử lý nghiệp vụ thực tế đã thành công hay chưa là điều bạn phải đánh giá riêng.

### Không phải mọi lỗi dừng cả scheduler

Một `ScheduledThreadPoolExecutor` có thể chứa nhiều task periodic độc lập. Một task thất bại thường chỉ khiến chuỗi của **task đó** bị ngừng; các task khác có thể vẫn tiếp tục. Đừng nghĩ “pool vẫn sống, vậy định kỳ A chắc vẫn chạy”.

**Không được dựa vào ứng dụng còn phản hồi request làm bằng chứng tất cả jobs đang chạy.** Theo dõi heartbeat của từng task quan trọng.

## 6. Runnable có thể ném loại exception nào?

`Runnable.run()` không khai báo `throws Exception`, nhưng có thể ném **unchecked exceptions**:

```java
Runnable task = () -> {
    throw new IllegalStateException("unexpected state");
};
```

Đây là đoạn code độc lập bên trong một method. `IllegalStateException` là `RuntimeException` nên không cần `throws`.

Nếu operation có checked exception như `IOException`, lambda Runnable phải **catch hoặc chuyển thành unchecked exception**:

```java
Runnable task = () -> {
    try {
        readFile();
    } catch (IOException e) {
        throw new UncheckedIOException(e);
    }
};
```

Đây là code minh họa; cần imports `java.io.IOException` và `UncheckedIOException`, còn `readFile()` cần được định nghĩa.

Khi bạn wrap bằng `UncheckedIOException` rồi để nó thoát khỏi run(), chuỗi periodic **cũng bị suppressed**. `Future.get()` nhận `ExecutionException` có cause là `UncheckedIOException`; muốn xem nguyên nhân I/O gốc cần đi sâu thêm qua `getCause()` của wrapper đó.

`Error` cũng là Throwable và có thể làm task periodic kết thúc bất thường. **Không nên catch toàn bộ `Throwable` để tiếp tục trong mọi hoàn cảnh**, nhất là những lỗi nghiêm trọng của runtime như `OutOfMemoryError`. Chính sách khôi phục phải dựa trên những lỗi dự kiến có thể xử lý an toàn.

## 7. Ví dụ Java 17 chạy độc lập: dừng, phát hiện và phục hồi

Chương trình sau kiểm tra ba tình huống:

1. `scheduleAtFixedRate`: lần đầu ném `IllegalStateException`, Future chứa lỗi và task chỉ chạy **một** lần.
2. `scheduleWithFixedDelay`: lần đầu ném `IllegalArgumentException`, lần sau cũng bị suppressed.
3. Một task fixed-rate khác **bắt lỗi dự kiến trong chính Runnable**, ghi nhận lỗi rồi trở về bình thường; các lần tiếp theo vẫn được thực thi. `CountDownLatch` xác nhận ít nhất ba lần chạy, sau đó main chủ động `cancel(false)`.

Tạo file **`PeriodicFailureDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PeriodicFailureDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        try {
            AtomicInteger fixedRateRuns = new AtomicInteger();
            ScheduledFuture<?> failedRate = scheduler.scheduleAtFixedRate(() -> {
                fixedRateRuns.incrementAndGet();
                throw new IllegalStateException("failed rate task");
            }, 0, 10, TimeUnit.MILLISECONDS);

            try {
                failedRate.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                System.out.println("rateCause=" + e.getCause().getClass().getSimpleName());
            }
            System.out.println("rateRuns=" + fixedRateRuns.get());
            System.out.println("rateFutureDone=" + failedRate.isDone());

            AtomicInteger fixedDelayRuns = new AtomicInteger();
            ScheduledFuture<?> failedDelay = scheduler.scheduleWithFixedDelay(() -> {
                fixedDelayRuns.incrementAndGet();
                throw new IllegalArgumentException("failed delay task");
            }, 0, 10, TimeUnit.MILLISECONDS);
            try {
                failedDelay.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                System.out.println("delayCause=" + e.getCause().getClass().getSimpleName());
            }
            System.out.println("delayRuns=" + fixedDelayRuns.get());

            AtomicInteger handledFailures = new AtomicInteger();
            AtomicInteger recoveredRuns = new AtomicInteger();
            CountDownLatch threeRuns = new CountDownLatch(3);
            ScheduledFuture<?> recovered = scheduler.scheduleAtFixedRate(() -> {
                int round = recoveredRuns.incrementAndGet();
                try {
                    if (round == 1) {
                        throw new IllegalStateException("transient");
                    }
                } catch (RuntimeException e) {
                    handledFailures.incrementAndGet();
                } finally {
                    threeRuns.countDown();
                }
            }, 0, 10, TimeUnit.MILLISECONDS);

            if (!threeRuns.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("recovery runs timed out");
            }
            recovered.cancel(false);
            System.out.println("recoveredAtLeastThree=" + (recoveredRuns.get() >= 3));
            System.out.println("handledFailures=" + handledFailures.get());
            System.out.println("recoveredCancelled=" + recovered.isCancelled());
        } finally {
            scheduler.shutdownNow();
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("scheduler did not terminate");
            }
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac --release 17 PeriodicFailureDemo.java
java PeriodicFailureDemo
```

**Stdout đã biên dịch và chạy kiểm chứng:**

```text
rateCause=IllegalStateException
rateRuns=1
rateFutureDone=true
delayCause=IllegalArgumentException
delayRuns=1
recoveredAtLeastThree=true
handledFailures=1
recoveredCancelled=true
```

### Phân tích phần fixed rate thất bại

- `fixedRateRuns.incrementAndGet()` được thực thi trước `throw`.
- Lần đầu chạy ném `IllegalStateException` ra khỏi `Runnable.run()`.
- `ScheduledFuture` của chuỗi này chuyển thành **completed exceptionally**; các execution tiếp theo bị suppressed, không có lần #2.
- `failedRate.get(3, SECONDS)` ném `ExecutionException` vì periodic task đã thất bại, và `getCause()` là `IllegalStateException`.
- Khi `get` trả exception, failure đã được ghi nhận; `fixedRateRuns.get() == 1` là một kết luận xác định, không cần dùng sleep để “đợi xem nó có chạy lại không”.
- `failedRate.isDone()` true: nó đã hoàn tất **bất thường**, không phải là “chạy thành công”.

### Phân tích phần fixed delay thất bại

- Lần đầu của `failedDelay` tăng counter rồi ném `IllegalArgumentException`.
- `scheduleWithFixedDelay` có **cùng quy tắc suppression** khi exception thoát khỏi Runnable. `delayRuns=1`.
- `failedDelay.get()` ném `ExecutionException` với cause tương ứng.

### Phân tích phần tiếp tục sau lỗi đã xử lý

- Lần đầu task `recovered` cố tình ném `IllegalStateException`, nhưng `catch (RuntimeException e)` xử lý lỗi trong Runnable.
- `run()` **trở về bình thường**, nên scheduler tiếp tục lên lịch các lần kế tiếp theo fixed rate.
- `threeRuns` được count down trong `finally` mỗi lần, kể cả lần xuất hiện lỗi nhưng đã bắt.
- Main dùng `threeRuns.await(3, SECONDS)` để biết task đã vào ít nhất ba vòng. Không cần mong scheduler vô tình chạy đúng ba vòng trước khi main tiếp tục; vì vậy output dùng `recoveredAtLeastThree=true`, không in một số lần chính xác dễ bị lệch do scheduling.
- `recovered.cancel(false)` hủy những lần chạy tiếp theo; nếu một lần đang chạy, `false` không yêu cầu interrupt lần đang thực thi đó. `isCancelled()` true sau khi cancel thành công trong đường chạy của demo.
- `finally` gọi `shutdownNow()` và `awaitTermination` để không để worker scheduler tồn tại sau khi main xong; trong ứng dụng dài hạn cần shutdown policy phù hợp thay vì luôn shutdownNow.

**Giới hạn mẫu:** đây là bài demo, không mô phỏng retry network/database thật và không khuyến khích bắt mọi `RuntimeException` rồi tiếp tục bất chấp invariant. Nếu một lỗi làm state không còn an toàn, hãy dừng và alert. Ba chuỗi periodic là những task độc lập trong cùng pool; lỗi của một chuỗi không tự động là lỗi của cả pool.

## 8. Vì sao không nên chỉ đợi một khoảng thời gian rồi kiểm tra counter?

Một bài test sai thường dùng:

```java
// Minh họa cách kiểm thử dễ flaky, KHÔNG phải bằng chứng đúng.
scheduler.scheduleAtFixedRate(task, 0, 100, TimeUnit.MILLISECONDS);
Thread.sleep(500);
System.out.println(runs.get());
```

Đây là snippet minh họa, với `scheduler`, `task` và `runs` có sẵn. `Thread.sleep(500)` không hứa scheduler sẽ được CPU đúng lúc, task không block hay hệ thống không pause vì GC. Counter sau một thời gian có thể phụ thuộc scheduler.

Thay vào đó:

- Với **failure terminal**, dùng `ScheduledFuture.get(timeout, unit)` và kiểm tra `ExecutionException.getCause()`.
- Với **progress**, dùng `CountDownLatch`/`Phaser`/counters atomic kèm event completion rõ.
- Với **kết thúc sau cancel**, dùng `Future.isCancelled()`/termination protocol thích hợp; việc cancel Future không luôn bảo đảm action đã dừng nếu đang chạy và cancel(false).
- Với **không xảy ra lần #2**, suy luận từ **hợp đồng suppression** của API và trạng thái Future đã completed exceptionally; nếu cần thêm bằng chứng runtime, kiểm tra count sau khi failure completion hoặc dùng instrumentation.

Các phép thử chịu deadline nên **fail một cách kiểm soát** khi môi trường bị kẹt; tránh treo test mãi ở `get()` không timeout.

## 9. Điều gì xảy ra với ScheduledFuture?

`ScheduledFuture<?>` trả về cho một periodic schedule là một handle của **cả chuỗi lặp**, không phải một Future tách riêng cho từng lần chạy.

### Khi task vẫn đang lặp bình thường

- `isDone()` thường là **false** khi chuỗi còn active.
- `get()` **không trả về giá trị bình thường chỉ vì một iteration đã hoàn thành**.
- `get(timeout, unit)` có thể ném `TimeoutException` nếu schedule vẫn active và chưa được cancel/fail trong thời hạn chờ.
- Không dùng `get()` ngay sau `scheduleAtFixedRate(...)` để “chờ iteration đầu tiên”: nó có thể chờ **mãi** nếu task cứ tiếp tục chạy.

### Khi một iteration ném exception thoát ra

- `isDone() == true` sau completion bất thường.
- `get()` ném `ExecutionException` với cause là throwable gốc của iteration gây lỗi.
- Chuỗi không có lần chạy sau; cần một hành động tạo schedule mới nếu muốn tiếp tục.

### Khi caller hủy lịch

- Nếu `cancel(...)` thành công, `isCancelled() == true` và `isDone() == true`.
- `get()` ném `CancellationException` (unchecked), **không phải `ExecutionException`** của business task.
- `cancel(true)` yêu cầu interrupt tác vụ đang chạy theo semantics của Future; nó **không đảm bảo task lập tức dừng** nếu code bỏ qua interrupt hoặc đang ở I/O không interruptible.
- `cancel(false)` không yêu cầu interrupt tác vụ đang chạy; trạng thái Future canceled không tự chứng minh mọi side effect đã ngừng ngay.

### Phân biệt các exception của get()

| Ngoại lệ | Khi nào thường gặp | Ý nghĩa |
|---|---|---|
| `ExecutionException` | Một iteration ném Throwable thoát ra | Chuỗi kết thúc bất thường; `getCause()` là lỗi gốc |
| `CancellationException` | Future đã được hủy | Chuỗi bị cancel; không mặc định là business failure |
| `TimeoutException` | `get(timeout)` không thấy kết thúc trong thời hạn | Schedule có thể vẫn đang chạy; **get timeout không tự hủy** |
| `InterruptedException` | Thread gọi `get` bị interrupt khi đợi | Caller bị yêu cầu ngắt; cần propagate/khôi phục cờ theo contract |

**Không được coi `isDone()` là dấu hiệu thành công.** Với periodic task, done thường là tín hiệu quan trọng cho **canceled hoặc failed**, không phải “iteration gần nhất thành công”.

## 10. Tại sao exception có thể không xuất hiện trong afterExecute(Throwable)?

Một điểm tinh tế của `ScheduledThreadPoolExecutor`: implementation sử dụng các `ScheduledFuture` để quản lý công việc. JavaDoc của class này nêu rõ: **`afterExecute` luôn nhận tham số `Throwable` thứ hai là null**, ngay cả khi scheduled command chấm dứt bất thường. Muốn biết throwable, đọc outcome qua `Future.get()`.

Ví dụ monitor chưa đủ:

```java
@Override
protected void afterExecute(Runnable task, Throwable error) {
    super.afterExecute(task, error);
    if (error != null) {
        report(error);
    }
}
```

Đây là method minh họa của subclass `ScheduledThreadPoolExecutor`; `report(Throwable)` do ứng dụng định nghĩa. Kiểm tra `error != null` như trên có thể **bỏ sót hoàn toàn lỗi của scheduled tasks**, vì wrapper lưu lỗi trong Future.

**Một cảnh báo khác:** `afterExecute` được gọi sau **từng iteration** của periodic task, nhưng khi task tiếp tục lặp, `ScheduledFuture.isDone()` vẫn **false**. Nếu hook gọi `future.get()` vô điều kiện cho periodic task active, worker có thể **block vô hạn ngay trong afterExecute** và không còn chạy được iterations tiếp theo!

Mẫu an toàn hơn:

```java
@Override
protected void afterExecute(Runnable task, Throwable error) {
    try {
        if (task instanceof Future<?> future && future.isDone()) {
            try {
                future.get();  // Chỉ đọc outcome khi Future đã done.
            } catch (ExecutionException e) {
                reportFailure(e.getCause());
            } catch (CancellationException e) {
                recordCancellation();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    } finally {
        super.afterExecute(task, error);
    }
}
```

Đây là **method minh họa** trong một subclass, với `reportFailure`, `recordCancellation` được cung cấp bên ngoài và imports `Future`, `ExecutionException`, `CancellationException`. Trong code thực, các methods telemetry này phải **nhanh, không block và exception-safe**, vì hook chạy trong worker thread. `future.isDone()` tránh `get()` blocking khi periodic Future còn active.

### Khác biệt với ThreadPoolExecutor thông thường

`ThreadPoolExecutor.afterExecute` khi nhận một direct `Runnable` qua `execute` có thể thấy Throwable thoát ra từ `run()`. Nhưng với `ScheduledThreadPoolExecutor`, **kể cả `execute()` cũng được wrapper thành scheduled future**. Vì vậy không nên chuyển nguyên mẫu “`t != null` là lỗi” từ thread pool thường sang scheduled pool mà không kiểm tra semantics của wrapper.

### Exception của hook không phải exception của business task

Nếu chính `beforeExecute` hoặc `afterExecute` ném exception, behavior của worker hook/lifecycle có thể khác với việc scheduled task `run()` ném exception. Không dùng hook để chứa business logic hoặc các thao tác có thể ném lỗi không được quản lý. Các lỗi đó có thể ảnh hưởng worker/monitoring và làm khó xác định nguyên nhân.

## 11. Phương án A: bắt và xử lý lỗi dự kiến để lịch tiếp tục

Nếu yêu cầu nghiệp vụ là “task phải tiếp tục ở chu kỳ sau dù lần này bị lỗi **có thể phục hồi**”, hãy xử lý lỗi **bên trong `Runnable`** trước khi nó thoát.

```java
Runnable refresh = () -> {
    try {
        refreshFromRemote();
        markSuccess();
    } catch (TransientNetworkException e) {
        recordFailure(e);
        // run() trả về bình thường; scheduler có thể chạy chu kỳ sau.
    }
};
```

Đây là **code minh họa**, không phải chương trình độc lập: `TransientNetworkException` và các method còn lại do ứng dụng định nghĩa. Chỉ bắt exception cụ thể, cho phép **retry ở chu kỳ kế tiếp** khi failure mode đó được xác định là an toàn.

**Không biến ngoại lệ thành “thành công”.** Dù `run()` về bình thường để scheduler tiếp tục, cần log/metric **iteration failed**, không tăng success counter. `ScheduledFuture` không thể cho bạn biết từng iteration từng thất bại đã được catch nếu chính code không báo outcome.

### Mẫu phân loại thất bại

Một policy ví dụ:

- **Transient/retryable:** timeout tạm thời, upstream trả trạng thái quá tải, kết nối ngắn hạn gặp vấn đề. Có thể ghi nhận, bỏ lần này và thử lại ở chu kỳ sau, nếu thao tác idempotent hoặc không gây trạng thái dở dang.
- **Permanent/data validation:** dữ liệu bất hợp lệ, precondition không còn đúng. Có thể cách ly dữ liệu xấu và tiếp tục với những dữ liệu tốt; hoặc dừng lịch nếu lỗi thể hiện invariant bị phá.
- **Programming/invariant failure:** `NullPointerException` không dự kiến, `IllegalStateException` báo state nghiêm trọng sai. Thường cần fail fast và alert hơn là catch rồi tiếp tục vô hạn.
- **JVM/runtime fatal:** `OutOfMemoryError` và các lỗi nghiêm trọng khác. Không cố bắt `Throwable` để “giữ schedule sống” một cách mù quáng.

Đặt **thao tác cleanup** trong `finally` phù hợp để release những tài nguyên thuộc iteration:

```java
Runnable task = () -> {
    Resource resource = openResource();
    try {
        performWork(resource);
    } finally {
        resource.close();
    }
};
```

Đây là **snippet minh họa**, `Resource`, `openResource`, `performWork` do ứng dụng định nghĩa. Với `AutoCloseable`, dùng try-with-resources khi hợp lý. Nếu cleanup cũng ném unchecked exception và thoát khỏi Runnable, **nó có thể làm cả periodic task ngừng**, dù phần main logic vừa chạy thành công.

## 12. Phương án B: để lỗi thoát ra khi cần dừng để tránh làm sai dữ liệu

Không phải lịch lặp nào cũng nên tiếp tục sau failure. Ví dụ job xử lý một batch rồi cập nhật cùng một state phụ thuộc:

- Một exception xảy ra sau khi cập nhật được 50% dữ liệu.
- Lần tiếp theo không biết đang ở batch nào, và chạy tiếp có thể ghi trùng hoặc mất item.
- Tiếp tục im lặng chỉ vì muốn “giữ scheduler sống” có thể gây lỗi lớn hơn.

Trong tình huống đó, để exception thoát khỏi `Runnable` để **suppression** là lựa chọn hợp lý, miễn có **alert và recovery workflow**:

```java
Runnable criticalJob = () -> {
    validateInvariant();   // Nếu không đúng, throw.
    commitOneBatch();      // Nếu lỗi, throw để dừng schedule.
};
```

Đây là snippet minh họa, methods do ứng dụng định nghĩa.

Điều quan trọng:

1. Lỗi bị báo rõ qua `ScheduledFuture.get()`/monitoring, không bị bỏ mặc.
2. Pipeline xác định trạng thái dữ liệu cuối đã được commit hay chưa.
3. Người vận hành hoặc controller có protocol reset/recover.
4. Khi khôi phục, không chỉ gọi `scheduleAtFixedRate` mới một cách vô điều kiện nếu instance cũ vẫn có thể đang chạy hoặc còn side effect.
5. Retry/restart phải **idempotent** hoặc có transactional semantics cần thiết.

**Suppression là behavior của scheduler, không phải một transaction/rollback của nghiệp vụ.** Các side effect đã xảy ra trước exception không được JVM tự hoàn tác.

## 13. Retry bên trong iteration hay thử lại ở chu kỳ sau?

Có hai cấp khác nhau:

### Retry nội bộ trong một iteration

Một execution có thể thử thao tác I/O vài lần, dùng backoff và timeout rồi quyết định return hoặc throw. Ví dụ:

```java
void runWithLimitedRetries() {
    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            callIdempotentRemoteApi();
            return;
        } catch (RetryableFailure e) {
            if (attempt == 3) {
                recordPermanentFailure(e);
                return; // hoặc throw để dừng schedule theo policy
            }
            backoff(attempt);
        }
    }
}
```

Đây là **method minh họa**, `RetryableFailure`, `callIdempotentRemoteApi`, `recordPermanentFailure` và `backoff` cần định nghĩa. Nếu `backoff` dùng sleep, phải xử lý `InterruptedException` đúng cách; deadline tổng thể không được vượt tùy tiện.

Khi retry trong iteration:

- Thời gian chạy iteration có thể dài hơn period/delay.
- `scheduleAtFixedRate` có thể bắt đầu lần sau muộn và chạy sát ngay sau completion để đuổi lịch.
- `scheduleWithFixedDelay` vẫn nghỉ đủ delay sau khi iteration kết thúc.
- Retry quá nhiều có thể giữ worker, tăng tải downstream và làm các task khác bị trễ.
- Một failure sau max attempts cần được ghi nhận, quyết định rõ **return để tiếp tục ở lần sau** hay **throw để suppress**.

### Để chu kỳ kế tiếp là lần retry

Nếu có thể bỏ qua kỳ hiện tại mà không phá invariant, catch lỗi tạm thời, log và return. Scheduler sẽ tự lên lịch lần sau; đây là retry **theo định kỳ**, không phải retry ngay lập tức. Cần bảo đảm input ở lần sau đủ để xử lý dữ liệu của lần thất bại, không tự động mất những work items cần ack.

### Retry không có nghĩa exactly-once

Nếu job gọi một API ngoài JVM và timeout **sau khi bên nhận đã commit**, retry có thể lặp side effect. Cần idempotency keys, transaction/outbox, checkpoint và business reconciliation theo yêu cầu. ScheduledExecutorService không bảo đảm exactly-once của external side effects.

## 14. Exception, interrupt và cancellation cần được phân loại rõ

### `InterruptedException`

Một `Runnable` không thể khai báo checked `InterruptedException`, nên khi gọi API interruptible phải tự xử lý:

```java
Runnable worker = () -> {
    try {
        waitForWork();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // Dừng công việc hoặc báo hủy; không tiếp tục loop vô hạn.
    }
};
```

Đây là code minh họa, `waitForWork()` đã được định nghĩa. Khi interrupt biểu thị shutdown/cancel, **đừng nuốt** rồi coi đó là một failure retryable bình thường. Trong scheduled task, nếu code swallow interrupt và return bình thường nhưng Future không canceled, scheduler có thể còn lập lịch lại theo policy; shutdown path phải được xác định riêng.

Nếu một iteration được cancel `cancel(true)`:

- Cancellation đánh dấu Future canceled theo API.
- Interrupt thường là **yêu cầu** dừng; code phải hợp tác.
- Future canceled không phải bằng chứng external side effects đã được rollback hoặc iteration đã dừng hoàn toàn.

### `RejectedExecutionException`

Lỗi này có thể xảy ra khi bạn cố lên lịch một task mới sau shutdown, hoặc executor không thể nhận task theo policy. Nó xảy ra ở **submission/admission**, không phải exception phát sinh trong một iteration được scheduler thực thi. Theo dõi rejection riêng.

### `TimeoutException` từ Future.get

Một lời gọi:

```java
future.get(2, TimeUnit.SECONDS);
```

Đây là snippet minh họa, `future` là Future. Timeout của `get` nói rằng **caller không đợi thấy completion trong thời gian đó**. Với periodic task đang hoạt động, đây có thể là hành vi hoàn toàn bình thường. `get(timeout)` không tự cancel lịch.

### `Error`

Một `Error` không được bắt bằng `catch(Exception)`. Một Error ném khỏi iteration cũng khiến chuỗi periodic bị suppressed theo behavior của ScheduledExecutorService. Đừng catch Throwable vô điều kiện chỉ để bảo vệ lịch; các lỗi có thể cho thấy JVM hoặc resource đã ở trạng thái không còn đáng tin.

## 15. Task lặp không chạy chồng lên chính nó

Với `ScheduledThreadPoolExecutor`, các lần thực thi tiếp nối của **cùng periodic task** không chạy đồng thời, ngay cả khi một iteration lâu hơn period; các lần chạy sau có thể bắt đầu trễ.

Điều này rất quan trọng khi đọc logs:

```text
rate 100ms, iteration 1 mất 350ms

Task A: [run1------------------------------]
                                                [run2---]
Task B:    [runB]          [runB]
```

Sơ đồ mô tả hai schedules A và B độc lập; **B có thể chạy trong lúc A đang chạy** nếu pool có tài nguyên. Không overlap **giữa các iteration của A** không có nghĩa A không thể chạy song song với schedules khác.

JavaDoc của `ScheduledThreadPoolExecutor` còn cung cấp quan hệ **happens-before giữa effects của các lần thực thi liên tiếp của cùng periodic task**, ngay cả khi mỗi lần chạy trên worker thread khác. Tuy vậy:

- Không suy ra nhiều task định kỳ **khác nhau** được serialize.
- Không suy ra state mutable được dùng đồng thời bởi job A và B trở thành thread-safe.
- Không suy ra mỗi lần sẽ bắt đầu chính xác theo wall clock.
- Một lần lỗi dừng chuỗi vẫn cần monitoring.

Nếu cùng một code được schedule **hai lần riêng biệt**, đó là **hai periodic tasks khác nhau** và có thể chạy song song khi pool có nhiều workers. Đừng dựa vào no-overlap guarantee của một schedule để bảo vệ shared state của hai schedules.

## 16. Đồng hồ, period, drift và tác động của lỗi

`scheduleAtFixedRate` và `scheduleWithFixedDelay` nhận **relative delays**, thường phù hợp với cơ chế scheduling dùng thời gian đơn điệu cho khoảng trễ. Chúng không phải cron theo mốc giờ địa phương.

### Fixed rate nếu task chạy lâu

Nếu execution luôn mất nhiều thời gian hơn period, scheduler không thể chạy **chồng** các execution của cùng task để bù. Những lần sau có thể bắt đầu **muộn**, thời gian chờ thực tế rất nhỏ. Nếu một iteration lỗi trong chuỗi này, những lần sau bị suppressed dù trước đó nhiều deadline đã lỡ.

### Fixed delay nếu task chạy lâu

Khoảng delay giữa **lúc kết thúc** một iteration và lúc bắt đầu lần sau vẫn áp dụng; tổng khoảng cách giữa hai lần bắt đầu xấp xỉ **duration iteration + delay**, cộng overhead/scheduling jitter.

### Lỗi làm mất nhịp như thế nào?

Nếu task bất ngờ ném exception tại vòng thứ 5:

- Không có vòng thứ 6 của **cùng schedule**, không có chính sách “tự chờ thêm period rồi retry”.
- `ScheduledFuture` chuyển sang failed done state.
- Nếu muốn chạy lại, phải **tạo schedule mới** sau khi quyết định cách xử lý trạng thái của vòng thứ 5.
- Không có cơ chế mặc định “chạy bù tất cả các lần đã mất” sau khi bạn tạo schedule mới.
- Với business requiring catch-up, cần mô hình **work items/checkpoints** riêng, không suy từ fixed rate rằng dữ liệu nào bị bỏ lỡ sẽ tự được replay.

## 17. Monitoring: làm sao phát hiện job đã ngừng?

Một scheduler sống không chứng minh mỗi job định kỳ đang thực thi. Tối thiểu, mỗi job quan trọng nên có:

- **Định danh ổn định:** tên job, loại công việc, partition/tenant phù hợp, không log payload nhạy cảm.
- **Last start timestamp** và **last completion timestamp**.
- **Last success timestamp** và **last failure timestamp** tách riêng.
- **Consecutive failures** và tổng failure count, phân loại retryable/fatal.
- **Current ScheduledFuture**: canceled, done hoặc vẫn active.
- **Execution duration**, queue delay / schedule lag nếu cần SLA.
- **Số lần restart** và lý do, tránh restart storm.
- **Age của dữ liệu đầu ra**: job chạy nhưng không tạo tiến triển cũng phải được phát hiện.

Với những trường hợp logic bắt exception rồi return, Future **vẫn có thể active** dù business operation đã thất bại nhiều vòng. Chỉ kiểm tra `future.isDone()` là chưa đủ; phải theo dõi **business success metric** từ code thực thi.

### Probe Future không chặn thread

```java
void inspect(String jobName, ScheduledFuture<?> future) {
    if (!future.isDone()) {
        return; // Periodic task có thể đang hoạt động bình thường.
    }
    try {
        future.get(); // Đã done, không đợi vô hạn.
    } catch (CancellationException e) {
        recordCancelled(jobName);
    } catch (ExecutionException e) {
        recordFailed(jobName, e.getCause());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

Đây là **method minh họa**, với các phương thức `recordCancelled`/`recordFailed` và imports của `java.util.concurrent` do ứng dụng định nghĩa. `Future.get` sau `isDone` phù hợp với `ScheduledFutureTask` chuẩn; nếu dùng custom Future, đánh giá contract riêng.

**Không gọi `future.get()` không timeout trên periodic Future active** trong health-check thread hoặc scheduler worker vì có thể chặn vô thời hạn. Đối với một controller định kỳ, nên lưu các handle Future và kiểm tra trạng thái không blocking.

### Khi job hoạt động nhưng đứng mãi

Nếu task **bị treo trong một iteration** mà không ném exception, Future có thể vẫn `isDone()==false` và không có lần chạy sau (vì iteration hiện tại chưa kết thúc). Cần **heartbeat, duration threshold, timeout của I/O, stack/thread diagnostics** để phân biệt “vẫn active” với “stuck”. `Future.isDone()==false` không đủ để khẳng định scheduler đang tiến triển đúng.

## 18. Điều gì xảy ra nếu tự động restart?

Một watchdog có thể phát hiện Future failed rồi tạo schedule mới, nhưng phải làm cẩn thận:

1. **Xác nhận trạng thái thật:** inspect Future đã done, lấy cause; phân biệt failed/canceled/shutdown.
2. **Kiểm tra chính sách restart:** lỗi là transient hay permanent/invariant corruption? Nếu permanent, cần dừng và alert.
3. **Giới hạn lần thử:** áp dụng backoff, jitter có kiểm soát, threshold/consecutive failure breaker; tránh tạo hàng trăm schedules mới trong một phút.
4. **Không tạo duplicates:** nếu scheduler hiện đã có một schedule khác cho cùng invariant, restart mới có thể tạo hai jobs cùng chạy. Cần ownership/registry và thao tác compare-and-swap trên handle hoặc lifecycle coordinator.
5. **Không tin rằng Future failed = side effect đã rollback:** kiểm tra transaction/checkpoint, idempotency và in-flight state.
6. **Không restart sau shutdown có chủ đích:** `isShutdown()`/policy vận hành phải được xét.
7. **Kiểm soát metrics và cảnh báo:** restart liên tục có thể che lỗi gốc; track số lần, cause và last healthy interval.
8. **Đánh giá race giữa watcher/restart:** nếu nhiều watchdog cùng phát hiện failure, chỉ một bên được tạo replacement hợp lệ.

Một skeleton ở mức ý tưởng:

```text
if periodicFuture completed exceptionally:
    cause = observe future failure
    if cause recoverable and restart budget available:
        reconcile state, then atomically register a new schedule
    else:
        alert and disable job until repaired
```

Đây là **pseudocode**, không phải Java runnable. Không tự động retry với mọi exception.

### Mất dữ liệu và idempotency

Một job đọc 100 records rồi ghi lần lượt 50 records trước khi lỗi. Tạo lại cùng schedule không có nghĩa các records đầu tiên được xử lý đúng một lần. Phải có transaction, checkpoint, idempotency hoặc deduplication để bảo vệ side effects. Scheduler chỉ điều khiển **khi nào chạy code**, không quản lý durability của data pipeline.

## 19. Shutdown policy của ScheduledThreadPoolExecutor

`ScheduledThreadPoolExecutor` có chính sách riêng cho task đã lên lịch khi executor shutdown:

- `setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean)` quyết định các **periodic tasks đã tồn tại** có được tiếp tục sau `shutdown()` hay không (mặc định **false**).
- `setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean)` quyết định các task **delayed one-shot** đã được xếp lịch có chạy sau `shutdown()` hay không (mặc định **true**).
- `setRemoveOnCancelPolicy(boolean)` quyết định có cố loại task đã bị cancel ra khỏi work queue ngay hay không (mặc định thường **false** trong Java 17; kiểm tra JDK version/implementation khi dùng).

Cần hiểu **shutdown** và **exception** là hai nguyên nhân khác nhau khiến periodic task không còn chạy. `shutdownNow()` còn cố ngắt các task đang chạy, trả những task đang chờ và không cam kết mọi side effect đã dừng.

### Vì sao shutdown policy liên quan debugging?

Nếu job không chạy lần tiếp theo sau khi một component gọi `scheduler.shutdown()`, đừng vội kết luận nó đã ném exception. Phải xem `isShutdown/isTerminated`, cancellation và outcome của Future.

Ví dụ:

```java
ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
scheduler.setRemoveOnCancelPolicy(true);
// Sử dụng scheduler rồi chủ động shutdown theo ownership.
```

Đây là đoạn minh họa trong method, cần import `ScheduledThreadPoolExecutor`. `setRemoveOnCancelPolicy(true)` giúp tránh giữ những delayed tasks đã cancel trong queue lâu không cần thiết, nhưng không làm công việc đang chạy dừng ngay lập tức.

**Lifecycle ownership:** ai tạo scheduler thường phải chịu trách nhiệm shutdown nó. Không gọi shutdown pool dùng chung từ một iteration hay một business method không sở hữu lifecycle.

## 20. Một chuỗi periodic khác một tập task one-shot

Nhiều người cố mô phỏng periodic work bằng việc một task tự gọi `scheduler.schedule(this, delay, unit)` ở cuối:

```java
// Mô hình minh họa, bỏ qua các chi tiết lifecycle:
void runAndReschedule() {
    try {
        doWork();
    } finally {
        scheduler.schedule(this::runAndReschedule, 1, TimeUnit.SECONDS);
    }
}
```

Đây là **method minh họa** với `scheduler` và `doWork` ở ngữ cảnh khác, không phải chương trình production. Tự reschedule trong finally ngay cả sau lỗi có thể tạo chuỗi mới **bất chấp failure policy**. Nếu scheduler shutdown, `schedule` có thể reject; nếu có retry ở nơi khác, có thể tạo duplicates; nếu task lỗi nghiêm trọng, tự chạy lại có thể phá invariant.

Một chuỗi `ScheduledFuture` periodic do API quản lý có semantics cancellation/error suppression rõ; **chuỗi one-shot tự reschedule** lại cần tự quản lý toàn bộ:

- Lỗi và quyết định tiếp tục/dừng.
- Giới hạn số lần retry và backoff.
- Future/handle của lần sắp chạy tiếp.
- Race với shutdown/cancel.
- Không tạo nhiều schedules song song.
- Kết quả/metrics của từng iteration.

Đừng đổi từ periodic API sang self-rescheduling chỉ để “né” exception suppression nếu chưa thiết kế đầy đủ lifecycle.

## 21. Kiểm chứng exception mà không làm scheduler “im lặng”

### Log ở đúng tầng

Có ba vị trí hữu ích:

1. **Ngay trong iteration:** bắt lỗi có thể phục hồi, ghi metric/cause, quyết định return hay rethrow.
2. **Ở ScheduledFuture observer:** khi Future done, `get()` để biết terminal failure/cancellation.
3. **Ở worker/executor-level instrumentation:** đo thời gian, số lần chạy, queue, afterExecute, nhưng phải hiểu wrapper Future và điều kiện isDone.

Đừng chỉ dựa vào `Thread.UncaughtExceptionHandler`: với scheduled task được wrapper bằng `ScheduledFutureTask`, throwable có thể được lưu trong Future mà không thoát khỏi worker như một uncaught exception thông thường.

### Log không được nuốt lỗi gốc

Nếu phần logging ném exception mới trong khi bạn xử lý lỗi gốc, outcome của task có thể phản ánh lỗi logging thay vì lỗi nghiệp vụ ban đầu. Hãy khiến instrumentation **exception-safe**, giới hạn thời gian I/O và tránh log dữ liệu bí mật.

### Phân biệt một lần iteration thất bại với chuỗi dừng

Nếu iteration catch và return, metric `iterationFailures++` tăng nhưng `ScheduledFuture.isDone()` vẫn false. Nếu iteration throw ra, Future completed exceptionally và `lastTerminalCause` xuất hiện. Đây là hai loại alert khác nhau: **chuỗi vẫn chạy nhưng kết quả xấu**, so với **chuỗi đã chấm dứt**.

## 22. Trường hợp dễ nhầm liên quan thời gian

### Timeout của get không phải timeout của task

`future.get(100, TimeUnit.MILLISECONDS)` chỉ giới hạn **thời gian người gọi chờ completion**. Nó không áp thời hạn cứng cho nội dung `run()`. Nếu iteration đang block I/O 30 giây, một gọi get timeout 100 ms không làm I/O tự dừng.

Muốn giới hạn một iteration, phải thiết kế timeout ở các I/O operations, cancellation có interrupt support, deadline trong logic hoặc separate coordination. Không chạy periodic task mãi mà không có liveness metrics.

### Fixed rate không “chạy bù” đồng thời

Nếu pool bận, fixed-rate task có thể trễ. Những lần chạy tiếp theo không overlap với lần trước của cùng task. Không giả định 1.000 iteration diễn ra đúng một giây nếu period=1ms trong môi trường tải cao.

### Một lỗi được catch không tự điều chỉnh lịch

Nếu code xử lý một transient exception rồi return bình thường, fixed rate/delay vẫn tiếp tục theo quy tắc timing ban đầu. Nó **không tự tăng backoff** dựa trên số lần lỗi; nếu muốn exponential backoff, phải tự thiết kế một state machine/scheduling strategy phù hợp.

### Lỗi đầu vào lặp lại

Nếu cùng một item xấu xuất hiện ở mỗi iteration và bạn luôn catch rồi return, scheduler sẽ “sống” nhưng công việc có thể thất bại ở mọi lần. Cần quarantine/dead-letter, alert theo consecutive failures, kiểm tra dữ liệu xấu và không để retry storm.

## 23. Java Memory Model và quan hệ happens-before

Một vấn đề độc lập với exception là **memory visibility** giữa các threads.

`ScheduledThreadPoolExecutor` quy định các lần thực thi kế tiếp của **cùng periodic task** không overlap, và effects của lần thực thi trước *happen-before* effects của lần sau, dù chạy trên worker khác. Điều này giúp việc mang state giữa các iteration thuộc **cùng schedule** có cơ sở memory-ordering phù hợp.

Tuy nhiên:

- **Nhiều periodic tasks khác nhau** không tự có cùng thứ tự HB nếu cùng đọc/ghi state.
- Một object shared mutable có thể bị thread khác thay đổi trong lúc job chạy; vẫn cần lock/atomic/immutable state thích hợp.
- `Future.get()` là ranh giới outcome/completion của Future; với periodic task vẫn active, bạn không thể chờ nó bằng `get()` rồi nghĩ một iteration đơn lẻ đã hoàn tất.
- `CountDownLatch`/`AtomicInteger` trong demo cho phép main quan sát tiến triển qua các primitive có memory effects rõ.
- Không dùng `Thread.sleep` làm cơ chế publication/happens-before.

**Đừng nhầm** nguyên tắc no-overlap của cùng periodic task với transaction trên nhiều hệ thống bên ngoài: database/file/network vẫn cần consistency protocol riêng.

## 24. Common pitfalls — lỗi thường gặp và cách sửa

1. **“RuntimeException sẽ bị bỏ qua, scheduler vẫn chạy chu kỳ sau.”** Sai với `scheduleAtFixedRate`/`scheduleWithFixedDelay`: exception thoát ra suppress những lần sau.
2. **“Chỉ fixed rate mới bị dừng; fixed delay thì tự retry.”** Sai: cả hai có cùng suppression rule.
3. **“Future.get() sau scheduleAtFixedRate sẽ trả kết quả của lần chạy đầu.”** Sai: periodic Future active không trả về bình thường.
4. **“isDone() == true nghĩa là iteration cuối thành công.”** Sai: periodic done thường do cancel hoặc lỗi.
5. **“Chỉ cần sau 2 giây counter vẫn bằng 1 là chứng minh không chạy tiếp.”** Test timing có thể flaky; dùng Future failed outcome + contract.
6. **“Bắt Throwable rồi bỏ qua để giữ job chạy là giải pháp an toàn.”** Có thể che JVM fatal error và state corruption.
7. **“Catch exception rồi return là business task đã thành công.”** Sai: cần metrics về failure/success của từng iteration.
8. **“Exception của task định kỳ sẽ luôn hiển thị qua UncaughtExceptionHandler.”** Không; scheduled future wrapper thường lưu lỗi.
9. **“afterExecute Throwable sẽ khác null khi periodic task throw.”** Không với `ScheduledThreadPoolExecutor` chuẩn: sau scheduled wrappers, tham số Throwable luôn null theo JavaDoc.
10. **“Trong afterExecute cứ gọi future.get() là sẽ xem lỗi.”** Sai: nếu periodic task đang active, get có thể block vô hạn. Phải kiểm tra `isDone()`.
11. **“Một schedule có hai workers nên hai iteration cùng job sẽ overlap.”** Không theo contract của ScheduledThreadPoolExecutor.
12. **“Hai schedules khác nhau không overlap vì dùng cùng pool.”** Sai: pool có thể chạy chúng song song nếu đủ workers.
13. **“Future.get(timeout) làm iteration bị cancel khi hết timeout.”** Sai: chỉ caller ngừng chờ.
14. **“cancel(true) đảm bảo external side effect đã dừng/rollback.”** Sai: interrupt cần task hợp tác.
15. **“Scheduler vẫn chạy task khác nên job tôi quan tâm chắc khỏe.”** Sai: failure thuộc từng periodic Future.
16. **“Cứ tự động schedule lại task sau lỗi là an toàn.”** Có thể tạo duplicates/restart storm hoặc chạy lại operation không idempotent.
17. **“Một lần lỗi có nghĩa mọi dữ liệu lần đó được rollback.”** Sai: side effects trước exception có thể đã commit.
18. **“Fixed rate chạy bù tất cả missed ticks cùng lúc.”** Sai: không overlap các iteration của cùng task.
19. **“shutdown() không ảnh hưởng periodic jobs đã xếp lịch.”** Sai với policy mặc định của ScheduledThreadPoolExecutor.
20. **“Đặt Thread.sleep trong Runnable là cách điều phối period.”** Không cần và thường làm tăng drift/block worker; dùng scheduling API theo contract.
21. **“Cần một task mới riêng cho mỗi retry nhưng không cần lưu handle.”** Sai: không có handle sẽ khó cancel, monitor và tránh duplicates.
22. **“Nếu isDone=false thì không thể có lỗi.”** Sai: iteration có thể bắt lỗi rồi return bình thường, hoặc bị treo.
23. **“Lỗi do logging không thể thay đổi trạng thái schedule.”** Sai: logging ném unchecked exception ra ngoài Runnable cũng có thể suppress.
24. **“newScheduledThreadPool(4) có nghĩa một periodic task chạy bốn phiên bản song song.”** Sai: core pool size giới hạn concurrent tasks khác nhau, không nhân bản cùng một schedule.

## 25. Checklist thiết kế production

Khi triển khai một periodic job trong Java SE, cần trả lời:

- **Ý nghĩa công việc:** job có cần chạy đúng từng tick hay chỉ cần định kỳ làm mới trạng thái?
- **Timing:** fixed rate hay fixed delay; period, initialDelay, jitter và deadline thế nào?
- **Failure policy:** ngoại lệ nào được catch rồi tiếp tục, ngoại lệ nào phải dừng/alert?
- **Dữ liệu:** nếu iteration thất bại giữa chừng, state/output có còn nhất quán không?
- **Retry:** giới hạn số lần, backoff, idempotency, replay và checkpoint ra sao?
- **Observability:** có last success, last error, consecutive failures, task duration, lag, Future state?
- **Termination:** có chủ sở hữu scheduler, shutdown policy, deadline và cancellation rõ?
- **Overlap:** cùng logic có được schedule hai lần từ hai components khác nhau không?
- **Concurrency:** những schedules khác có chia sẻ mutable state không?
- **Timeout:** từng I/O operation có timeout, task không block vô hạn?
- **Monitoring:** observer có tránh gọi get blocking trên periodic Future active không?
- **Testing:** dùng latch/Future/timeout, có test exception đầu tiên, exception ở vòng thứ N, cancel, shutdown, restart và retry?
- **Compatibility:** JDK 17+ API có sẵn trên môi trường build/runtime không?

Tất cả các câu trả lời nên biến thành contract được test/giám sát, thay vì chỉ để một exception ném ra rồi hy vọng schedule tiếp tục.

## 26. Bài tập tự luyện

**Bài tập 1 — Fixed rate dừng sau vòng 2:** thay đổi demo để iteration #1 chạy bình thường và #2 ném `IllegalStateException`. Dùng `Future.get(timeout)` xác nhận cause và counter cuối cùng bằng 2. Không dùng `Thread.sleep` để kiểm thử.

**Bài tập 2 — Fixed delay tiếp tục sau một transient failure:** viết một `Runnable` chạy 4 vòng, vòng #2 bắt một `RetryableException` rồi return; dùng `CountDownLatch(4)` xác nhận 4 vòng hoàn thành. Đếm **business successes** riêng với **iteration attempts**.

**Bài tập 3 — Observer Future:** xây một method `inspect(ScheduledFuture<?>)` không blocking; nếu future not done, return; nếu done, phân loại `CancellationException`/`ExecutionException`. Gọi inspect trên một Future còn active và một Future failed.

**Bài tập 4 — Restart budget:** tạo một controller chỉ cho phép restart sau khi future failed và sau delay có backoff. Viết test sao cho hai observer không tạo hai replacement cùng lúc. Không dùng restart khi cause là invariant violation.

**Bài tập 5 — Side effect:** mô phỏng việc ghi 3 records, lỗi sau record #2 rồi retry. Chứng minh tại sao cần idempotency key hoặc commit boundary để tránh xử lý record #1/#2 hai lần.

**Bài tập 6 — Monitoring lỗi bị bắt:** nếu Runnable luôn catch exception rồi return, Future vẫn active. Thiết kế counter `consecutiveFailures` và `lastSuccessNanos` để health check phát hiện job không tạo tiến triển dù schedule vẫn sống.

## 27. Câu hỏi ôn tập và đáp án

**Câu 1.** `scheduleAtFixedRate()` có tự chạy iteration kế tiếp khi iteration trước ném RuntimeException ra ngoài không?  
**Đáp án:** Không. Các lần tiếp theo của cùng periodic schedule bị suppressed; Future completed exceptionally.

**Câu 2.** `scheduleWithFixedDelay()` có ngoại lệ khác fixed rate về failure suppression không?  
**Đáp án:** Không. Nó khác cách tính thời điểm chạy kế tiếp, nhưng có cùng quy tắc exception dừng các lần sau.

**Câu 3.** Làm sao lấy nguyên nhân gốc của periodic task đã thất bại?  
**Đáp án:** Gọi `ScheduledFuture.get()` trên Future đã done; nó ném `ExecutionException`, đọc `getCause()`.

**Câu 4.** Vì sao không gọi `future.get()` vô điều kiện trong `afterExecute`?  
**Đáp án:** Khi periodic task còn active, Future chưa done, get có thể block vô hạn trên worker; phải kiểm tra `future.isDone()`.

**Câu 5.** `afterExecute(r, t)` của `ScheduledThreadPoolExecutor` có luôn nhận Throwable khi scheduled Runnable ném lỗi không?  
**Đáp án:** Không. `ScheduledThreadPoolExecutor` bọc task trong `ScheduledFuture`, JavaDoc ghi `t` luôn null; lỗi đọc qua Future.

**Câu 6.** Khi một scheduled task ném lỗi, mọi scheduled tasks khác trên cùng pool có bị dừng không?  
**Đáp án:** Không mặc định. Chuỗi của task lỗi bị suppressed; các schedules khác có thể tiếp tục chạy nếu executor hoạt động.

**Câu 7.** Vì sao `isDone()` true chưa chứng minh task thành công?  
**Đáp án:** Với periodic task, done có thể do failure hoặc cancellation; phải phân tích outcome.

**Câu 8.** Nếu Runnable catch một transient exception rồi return, scheduler có tiếp tục không?  
**Đáp án:** Có thể tiếp tục theo chu kỳ định nghĩa, vì exception không thoát ra; cần ghi metrics riêng cho iteration failed.

**Câu 9.** Có nên catch toàn bộ Throwable để ngăn schedule dừng không?  
**Đáp án:** Không. Những lỗi nghiêm trọng như OutOfMemoryError/invariant corruption không nên bị che và tiếp tục mù quáng.

**Câu 10.** `Future.get(1, SECONDS)` ném TimeoutException trên periodic task còn active có nghĩa job đã lỗi không?  
**Đáp án:** Không. Nó chỉ nói caller không thấy Future kết thúc trong deadline; periodic Future hoạt động bình thường thường không complete.

**Câu 11.** Nếu fixed-rate task chạy 500ms với period 100ms, scheduler có chạy năm bản của cùng task đồng thời không?  
**Đáp án:** Không. Những lần của cùng periodic task không overlap; các lần sau có thể bắt đầu muộn.

**Câu 12.** `cancel(false)` có ngăn mọi lần future chưa bắt đầu không, và có ép iteration đang chạy dừng ngay không?  
**Đáp án:** Hủy ngăn những lần kế tiếp theo contract; `false` không yêu cầu interrupt iteration đang chạy, nên không hứa dừng ngay lập tức.

**Câu 13.** Sau khi một job failed, tạo schedule mới đã đảm bảo mọi dữ liệu hợp lệ chưa?  
**Đáp án:** Chưa. Cần reconcile/checkpoint/idempotency, kiểm soát duplicates, restart budget và phân tích nguyên nhân.

**Câu 14.** `shutdown()` với ScheduledThreadPoolExecutor có thể làm periodic task ngừng theo default policy không?  
**Đáp án:** Có. Default `continueExistingPeriodicTasksAfterShutdownPolicy` là false.

**Câu 15.** Một job luôn catch lỗi và return nên Future `isDone()==false`; có chắc job khỏe không?  
**Đáp án:** Không. Nó có thể thất bại mỗi iteration nhưng vẫn tiếp tục; cần monitor last success/failure, progress và output.

**Câu 16.** Một iteration không ném lỗi nhưng bị block vô hạn: Future có thể như thế nào?  
**Đáp án:** Có thể vẫn not done, các lần tiếp theo không chạy do iteration hiện tại chưa kết thúc; cần duration/timeout/heartbeat monitoring.

## 28. Tài liệu tham khảo chính thức

- [Java SE 17 — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): hợp đồng của `scheduleAtFixedRate` và `scheduleWithFixedDelay`, exception suppression, periodic Future.get.
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): scheduling, no-overlap, happens-before giữa successive executions, `afterExecute`, shutdown và cancellation policies.
- [Java SE 17 — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): Future + Delayed semantics.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get`, `cancel`, `isDone`, memory consistency.
- [Java SE 17 — Executors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html): `newScheduledThreadPool` và các factory methods.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối kiểm thử/đồng bộ với happens-before.
- [Java SE 17 — ExecutionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html): wrapper của failure từ Future.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model, interrupts và happens-before.

**Kết luận:** đối với các task định kỳ Java `scheduleAtFixedRate()` hoặc `scheduleWithFixedDelay()`, **exception thoát khỏi một lần chạy làm các lần chạy tiếp theo của chính task đó bị suppressed**. `ScheduledFuture` giúp quan sát terminal cause; còn muốn scheduler tiếp tục sau lỗi dự kiến, code phải **catch/record và return bình thường** theo một policy đúng về dữ liệu. Đừng gọi `Future.get()` vô điều kiện trên periodic Future còn active; giám sát business progress, duration và lifecycle tách biệt khỏi trạng thái “pool còn sống”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-012">Thiết kế stop/shutdown cho lịch chạy định kỳ</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế stop/shutdown cho lịch chạy định kỳ trong Java

## 1. Vấn đề: dừng một lịch không đồng nghĩa dừng công việc đang chạy

Trong Java Concurrency, **ScheduledExecutorService** cho phép lập lịch tác vụ một lần hoặc lặp lại theo chu kỳ. Hai phương thức thường gặp là **scheduleAtFixedRate(...)** và **scheduleWithFixedDelay(...)**; cả hai trả về **ScheduledFuture<?>**, đại diện cho lịch chạy định kỳ.

Một thành phần định kỳ như bộ thu thập metric, kiểm tra trạng thái, đồng bộ dữ liệu hoặc tác vụ bảo trì cần được thiết kế vòng đời rõ ràng. Khi ứng dụng dừng, có thể cần:

1. **Không kích hoạt lượt mới** sau khi nhận yêu cầu stop.
2. **Cho lượt đang chạy hoàn tất** nếu còn trong thời hạn cho phép.
3. **Đề nghị ngắt lượt đang chạy** nếu quá thời hạn hoặc cần dừng nhanh.
4. **Chờ tài nguyên thực sự kết thúc** và báo lại trạng thái shutdown.
5. **Đảm bảo khởi động/dừng nhiều lần không tạo lịch trùng** hoặc dùng executor đã shutdown.

Đây là các yêu cầu khác nhau. **ScheduledFuture.cancel(...)** tác động đến lịch của một tác vụ; **executor.shutdown()/shutdownNow()** tác động đến executor; **awaitTermination(...)** đợi executor kết thúc. Muốn bảo đảm an toàn phải phối hợp chúng và thiết kế tác vụ hợp tác với tín hiệu dừng.

Bài học chỉ dùng các API lập lịch và đồng bộ thuộc thư viện chuẩn Java, không gắn với vòng đời của framework.

## 2. Hiểu đúng scheduleAtFixedRate và scheduleWithFixedDelay

| API | Cách xác định thời điểm chạy tiếp | Lưu ý khi dừng |
|---|---|---|
| **scheduleAtFixedRate(task, initialDelay, period, unit)** | Mốc dự kiến: bắt đầu + k × period | Nếu một lần chạy lâu hơn period, lần sau có thể bắt đầu muộn; không chạy chồng các lần của **chính lịch đó** |
| **scheduleWithFixedDelay(task, initialDelay, delay, unit)** | Lần sau bắt đầu sau khi lần trước **kết thúc** rồi chờ delay | Dễ kiểm soát khoảng nghỉ giữa các lượt; cũng không chạy chồng các lần của **chính lịch đó** |

Cả hai phương thức trả về một **ScheduledFuture** của **cả chuỗi định kỳ**, không phải một Future riêng cho từng lần chạy. Future của lịch đang tiếp tục bình thường thường có **isDone() == false** ngay cả khi nhiều lượt đã hoàn tất. Đừng gọi **get()** không thời hạn để “lấy kết quả của lượt vừa chạy”: lời gọi có thể chờ đến khi toàn bộ lịch kết thúc vì bị hủy hoặc do lỗi.

Nếu một lượt chạy định kỳ ném ngoại lệ ra khỏi Runnable, **những lần chạy tiếp theo bị ngăn lại** theo hợp đồng ScheduledExecutorService. Vì thế log, metrics và quy tắc xử lý exception trong Runnable là một phần của thiết kế stop/shutdown: lịch ngừng do lỗi không tương đương với stop có chủ đích.

Đặc biệt, **không có hai lần thực thi của cùng một periodic task chạy đồng thời** trong ScheduledThreadPoolExecutor; tuy nhiên hai task định kỳ **khác nhau** có thể song song nếu executor có nhiều worker. Một lượt chạy quá lâu làm lịch fixed-rate bị trễ, nhưng không tạo lượt thứ hai chồng lên chính lượt đó.

## 3. Bốn cơ chế cần phân biệt

| API | Phạm vi | Tác động chính |
|---|---|---|
| **ScheduledFuture.cancel(false)** | Một lịch/tác vụ | Nếu hủy thành công, ngăn các lần chạy sau, **không yêu cầu interrupt** lượt đang chạy |
| **ScheduledFuture.cancel(true)** | Một lịch/tác vụ | Nếu hủy thành công, có thể yêu cầu **interrupt** thread đang chạy; không bảo đảm task dừng ngay |
| **ExecutorService.shutdown()** | Cả executor | Từ chối tác vụ mới và thực hiện shutdown có trật tự theo chính sách của executor |
| **ExecutorService.shutdownNow()** | Cả executor | Cố gắng dừng tác vụ đang chạy bằng interrupt, ngăn xử lý tác vụ đang đợi và trả danh sách tác vụ chưa khởi chạy |
| **ExecutorService.awaitTermination(timeout, unit)** | Cả executor | Chặn thread gọi để đợi toàn bộ executor kết thúc hoặc hết hạn; trả về boolean |

**shutdown() không phải awaitTermination().** shutdown phát tín hiệu bắt đầu dừng rồi trả về; worker có thể vẫn đang chạy. Gọi **isShutdown()** chỉ cho biết executor đã nhận shutdown. **isTerminated()** mới cho biết nó đã kết thúc tất cả tác vụ sau shutdown. **awaitTermination() == false** nghĩa là đã hết thời hạn đợi mà executor chưa kết thúc; không có nghĩa thao tác shutdown “không được chấp nhận”.

**shutdownNow() cũng không phải “kill thread”.** Java interruption là cơ chế hợp tác. Tác vụ đang tính toán không kiểm tra interrupt, chờ trên I/O không phản hồi interrupt hoặc mắc ở thư viện ngoài vẫn có thể tiếp tục. Không được cam kết mọi task hay side effect đã chấm dứt chỉ vì shutdownNow() đã trả về.

**cancel() và kết quả thực thi:** Future được hủy thành công sẽ báo **isCancelled() == true**, **isDone() == true**, và **get()** ném CancellationException. Tuy vậy một lượt đang chạy có thể vẫn tiếp tục khi đã gọi cancel(false), nên trạng thái Future không phải xác nhận lượt xử lý bên trong đã thực sự thoát.

## 4. Chính sách riêng của ScheduledThreadPoolExecutor

**ScheduledThreadPoolExecutor** là implementation linh hoạt của ScheduledExecutorService. Nó có các chính sách quan trọng trong shutdown:

| Chính sách | Mặc định | Ý nghĩa |
|---|---|---|
| **getContinueExistingPeriodicTasksAfterShutdownPolicy()** | **false** | Các lượt định kỳ hiện có **không tiếp tục** sau shutdown() mặc định |
| **getExecuteExistingDelayedTasksAfterShutdownPolicy()** | **true** | Tác vụ delayed **một lần** đã xếp lịch được tiếp tục sau shutdown() mặc định |
| **getRemoveOnCancelPolicy()** | **false** | Task bị cancel có thể còn trong delay queue đến khi đến hạn theo cách dọn mặc định |

Điều này tạo khác biệt quan trọng: gọi **shutdown()** trên ScheduledThreadPoolExecutor mặc định dừng các chuỗi định kỳ, nhưng **các tác vụ một lần đã đặt lịch rất xa trong tương lai có thể vẫn khiến executor chưa terminate**. Nếu muốn lúc shutdown không chạy các one-shot delayed task chưa tới hạn, chủ sở hữu executor có thể thiết lập:

~~~java
scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
~~~

Khi có nhiều lịch hủy sớm, cân nhắc:

~~~java
scheduler.setRemoveOnCancelPolicy(true);
~~~

Thiết lập này giúp loại bỏ task bị cancel khỏi queue sớm hơn, tránh giữ tham chiếu lâu không cần thiết; không làm cho shutdownNow trở thành cơ chế ngắt cưỡng bức. Hai dòng trên là **mã minh họa** trong phạm vi biến scheduler kiểu ScheduledThreadPoolExecutor, không phải chương trình độc lập.

**Không bật** setContinueExistingPeriodicTasksAfterShutdownPolicy(true) nếu mục tiêu là dừng tất cả lịch định kỳ lúc shutdown. Chính sách true có thể giữ các periodic task tiếp tục thực thi sau shutdown, khiến executor không kết thúc theo cách mong muốn. Nếu dùng true vì một nghiệp vụ đặc biệt, phải có thao tác cancel rõ ràng và kiểm tra termination.

**Phạm vi:** các phương thức điều chỉnh chính sách trên thuộc **ScheduledThreadPoolExecutor**, không phải hợp đồng chung của mọi triển khai ScheduledExecutorService. Nếu được truyền một ScheduledExecutorService bất kỳ, chỉ dựa vào các phương thức của interface hoặc xác định rõ implementation đang sở hữu.

## 5. Trình tự graceful stop khuyến nghị

Với một component **sở hữu riêng** một ScheduledThreadPoolExecutor và một hoặc vài lịch định kỳ, trình tự tiêu biểu là:

1. **Chặn yêu cầu start/schedule mới.** Đánh dấu trạng thái STOPPING với đồng bộ phù hợp để thread khác không tạo thêm lịch trong khi đang dừng.
2. **Lưu handle ScheduledFuture khi bắt đầu**, rồi **cancel(false)** để ngăn lượt mới mà không chủ động interrupt lượt đang chạy.
3. **Gọi shutdown()** lên executor mà component sở hữu. Nếu executor dùng chung, **không tự shutdown** tài nguyên do thành phần khác quản lý: chỉ cancel các lịch thuộc quyền sở hữu của mình.
4. **awaitTermination** với thời gian có hạn, từ một thread điều phối bên ngoài worker đang chạy. Kiểm tra kết quả trả về.
5. Nếu quá hạn và chính sách cho phép, gọi **shutdownNow()** để đề nghị interrupt, sau đó chờ thêm một thời hạn có hạn.
6. **Xử lý InterruptedException** ở thread điều phối: thường gọi shutdownNow() theo chính sách của hệ thống, khôi phục interrupt flag rồi trả/lan truyền trạng thái ngắt.
7. **Đóng tài nguyên phụ thuộc** sau khi đã bảo đảm các task không còn sử dụng chúng, hoặc có quy ước dừng an toàn nếu tác vụ không phản hồi interrupt.

Không đóng HTTP client, file hoặc connection pool **trước** khi lượt đang chạy ngừng sử dụng nếu điều đó khiến tác vụ lỗi giữa chừng. Mặt khác, nếu I/O có thể chờ vô thời hạn, cần timeout ở tầng I/O và cơ chế giải phóng độc lập; chỉ chờ executor chưa đủ.

### Bất biến cần giữ

- Sau khi STOPPING, không một lời gọi **start()** cạnh tranh nào được chấp nhận.
- Với mỗi lịch định kỳ, chỉ có **một** ScheduledFuture handle có hiệu lực trong một vòng đời.
- **stop()** phải idempotent hoặc nêu rõ hợp đồng cho lần gọi lặp lại.
- Một executor đã shutdown **không thể sử dụng để schedule lại**: thường tạo một instance service/executor mới cho vòng đời mới.
- Tránh giữ lock dài trong lúc awaitTermination: task đang chạy có thể cần lock đó để thoát, gây deadlock.
- Không đợi awaitTermination ngay **bên trong chính worker** mà executor đang chờ kết thúc.

## 6. Ví dụ Java 17: stop êm, để lượt đang chạy kết thúc

Ví dụ cố ý dùng **CountDownLatch** để kiểm soát thời điểm: worker bắt đầu một lượt rồi đợi, main gọi **cancel(false)** khi lượt đó chưa xong, mở khóa cho nó hoàn tất, gọi shutdown() và đợi executor kết thúc. Đặt delay giữa các lượt bằng 1 ngày chỉ để ví dụ luôn quan sát **một lượt**; đây không phải chu kỳ được đề xuất cho ứng dụng thực.

Lưu tệp **GracefulPeriodicStopDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GracefulPeriodicStopDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();

        try {
            ScheduledFuture<?> periodic = scheduler.scheduleWithFixedDelay(
                    () -> {
                        runs.incrementAndGet();
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    0, 1, TimeUnit.DAYS
            );

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not start");
            }

            // Lượt hiện tại còn đang đợi release.
            boolean cancelled = periodic.cancel(false);
            release.countDown();
            scheduler.shutdown();
            boolean terminated = scheduler.awaitTermination(5, TimeUnit.SECONDS);

            System.out.println("cancel accepted=" + cancelled);
            System.out.println("runs=" + runs.get());
            System.out.println("future cancelled=" + periodic.isCancelled());
            System.out.println("executor terminated=" + terminated);
        } finally {
            release.countDown();
            scheduler.shutdownNow();
        }
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac GracefulPeriodicStopDemo.java
java GracefulPeriodicStopDemo
~~~

Đầu ra kỳ vọng trong điều kiện máy có khả năng thực thi bình thường và hoàn tất trong thời hạn 5 giây:

~~~text
cancel accepted=true
runs=1
future cancelled=true
executor terminated=true
~~~

**Vì sao kiểm chứng được?** Latch started bảo đảm lượt đầu đã vào thân Runnable, còn latch release khiến lượt đó chưa kết thúc trước khi cancel(false). Cancel thành công chuyển Future sang canceled, nhưng **không chủ động ngắt** worker. Sau release, worker hoàn tất; shutdown và awaitTermination chờ toàn bộ executor ngừng. Trong finally, shutdownNow() là lớp phòng vệ khi đường đi thông thường không hoàn tất; nó không xóa kết quả stop êm đã quan sát.

**Quan trọng:** không dùng Thread.sleep(...) và hy vọng main “kịp cancel” trước worker. Cách đó phụ thuộc timing và dễ tạo ví dụ kết quả không ổn định.

## 7. Ví dụ Java 17: quá hạn graceful shutdown rồi đề nghị interrupt

Nếu một lượt đang chạy không hợp tác kết thúc trong khoảng grace period, chương trình cần có chính sách chuyển cấp. Ví dụ này dùng latch để giữ worker đợi; shutdown() không interrupt worker, nên awaitTermination trong thời hạn ngắn sẽ trả false. Sau đó shutdownNow() gửi interrupt, cho worker thoát khỏi CountDownLatch.await().

Lưu **EscalatingShutdownDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EscalatingShutdownDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocker = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean();

        try {
            ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(
                    () -> {
                        started.countDown();
                        try {
                            blocker.await();
                        } catch (InterruptedException interrupted) {
                            sawInterrupt.set(true);
                            Thread.currentThread().interrupt();
                        }
                    },
                    0, 1, TimeUnit.DAYS
            );

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task did not start");
            }

            periodic.cancel(false);
            scheduler.shutdown();
            boolean graceful = scheduler.awaitTermination(30, TimeUnit.MILLISECONDS);

            if (!graceful) {
                scheduler.shutdownNow();
            }
            boolean terminated = scheduler.awaitTermination(5, TimeUnit.SECONDS);

            System.out.println("graceful=" + graceful);
            System.out.println("interrupt seen=" + sawInterrupt.get());
            System.out.println("terminated=" + terminated);
        } finally {
            blocker.countDown();
            scheduler.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac EscalatingShutdownDemo.java
java EscalatingShutdownDemo
~~~

Trong điều kiện JDK/scheduler đáp ứng và không vượt quá thời hạn 5 giây:

~~~text
graceful=false
interrupt seen=true
terminated=true
~~~

**Giải thích:** cancel(false) dừng lịch nhưng worker đang chờ latch vẫn chưa xong. shutdown() từ chối task mới và bắt đầu dừng executor; grace period 30 ms hết trong khi worker vẫn bị chặn, vì vậy graceful=false. shutdownNow() thử interrupt, khiến CountDownLatch.await() ném InterruptedException. Worker ghi nhận tín hiệu, khôi phục interrupt flag và thoát; executor mới có thể terminate.

Lớp phòng vệ finally đảm bảo không giữ latch mãi nếu có exception. Trong một hệ thống thực, **shutdownNow có thể không khiến task thoát**, nên sau awaitTermination vẫn phải kiểm tra boolean. Nếu terminated=false, cần báo trạng thái stop không hoàn tất, ghi metric/alert, áp timeout cho I/O hoặc thực hiện quy trình phục hồi tương ứng.

## 8. Mẫu quản lý vòng đời có tính idempotent

Trong ứng dụng, yêu cầu stop có thể đến từ nhiều đường đi hoặc cùng lúc với start. Một mẫu đơn giản là **đồng bộ phần chuyển trạng thái**, còn việc chờ task thực thi phải làm **ngoài khóa**. Ví dụ dưới đây tạo executor một lần; stop nhiều lần không gọi schedule lại và không chờ trong synchronized block.

Lưu **PeriodicLifecycleDemo.java**:

~~~java
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PeriodicLifecycleDemo {
    private final ScheduledThreadPoolExecutor scheduler =
            new ScheduledThreadPoolExecutor(1);

    private ScheduledFuture<?> periodic;
    private boolean stopping;

    public synchronized void start() {
        if (stopping || periodic != null) {
            throw new IllegalStateException("Already started or stopping");
        }
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        periodic = scheduler.scheduleWithFixedDelay(
                () -> System.out.println("tick"),
                1, 1, TimeUnit.DAYS
        );
    }

    public boolean stop(long timeout, TimeUnit unit)
            throws InterruptedException {
        synchronized (this) {
            if (!stopping) {
                stopping = true;
                if (periodic != null) {
                    periodic.cancel(false);
                }
                scheduler.shutdown();
            }
        }

        if (scheduler.awaitTermination(timeout, unit)) {
            return true;
        }
        scheduler.shutdownNow();
        return scheduler.awaitTermination(timeout, unit);
    }

    public static void main(String[] args) throws InterruptedException {
        PeriodicLifecycleDemo service = new PeriodicLifecycleDemo();
        service.start();
        boolean stopped = service.stop(5, TimeUnit.SECONDS);
        System.out.println("stopped=" + stopped);
    }
}
~~~

Chạy:

~~~bash
javac PeriodicLifecycleDemo.java
java PeriodicLifecycleDemo
~~~

Đầu ra bình thường:

~~~text
stopped=true
~~~

Chương trình đặt initialDelay một ngày nên không có "tick" trước lúc stop. start() và phần chuyển sang STOPPING đồng bộ trên cùng monitor; việc awaitTermination xảy ra ngoài monitor. Lời gọi stop lần hai không lặp lại bước cancel/shutdown nhưng vẫn có thể chờ executor. **Lưu ý:** nếu hai caller cùng vào stop lúc grace period đang hết, cả hai có thể thử shutdownNow; để có một coordinator duy nhất cho toàn bộ pha chờ, dùng state machine và một completion signal riêng. Ở quy mô lớn, cần xác định rõ ai sở hữu quyền dừng, deadline tuyệt đối, cách thu lỗi và cách báo trạng thái.

Với xử lý InterruptedException trong stop, tầng điều phối thường gọi shutdownNow theo chính sách và **khôi phục interrupt flag** nếu không truyền ngoại lệ lên. Ví dụ trên cố ý khai báo **throws InterruptedException** để bên gọi quyết định; nó không che giấu việc caller bị interrupt.

### Trường hợp executor dùng chung

Nếu executor được chia sẻ giữa nhiều component, **component không sở hữu executor chỉ nên cancel ScheduledFuture của chính mình**. Nếu tự gọi shutdown(), nó có thể làm ngừng lịch và task của các bên khác. Chủ sở hữu executor tập trung có trách nhiệm cuối cùng với shutdown và awaitTermination. Khi service cần bảo đảm không nhận thêm việc, cần đồng bộ stop/start ngay trong service thay vì chỉ trông đợi vào trạng thái của executor.

## 9. Viết Runnable phản hồi tín hiệu dừng

Việc hủy Future hoặc interrupt worker không tự động kết thúc mọi mã Java. Hãy xây dựng công việc theo hướng hợp tác:

- **Kiểm tra trạng thái stop/interrupted** tại các điểm an toàn trước bước xử lý dài hoặc giữa các batch.
- **Dùng timeout I/O có hạn**, vì nhiều lời gọi I/O có thể không phản hồi Thread.interrupt() như mong muốn.
- **Không nuốt InterruptedException** rồi tiếp tục vòng lặp như không có yêu cầu dừng; thường khôi phục interrupt flag và thoát sớm hoặc truyền ngoại lệ.
- **Dọn dẹp trong finally / try-with-resources** cho tài nguyên của riêng lượt chạy; tránh giữ lock trong lúc chờ I/O lâu.
- **Không mở thêm tác vụ vô hạn** vào executor khác khi đã STOPPING; cần cancel/await cả các task con nếu muốn báo stop hoàn tất thực sự.
- **Giữ nghiệp vụ nhất quán:** cancel không rollback gửi HTTP, ghi file, transaction hay publish event đã xảy ra. Với retry, dùng idempotency và/hoặc giao dịch/compensation phù hợp.

Ví dụ pseudo-code cho một lượt có xử lý dữ liệu theo batch:

~~~java
void runBatch() {
    for (Batch batch : inputBatches) {
        if (stopping.get() || Thread.currentThread().isInterrupted()) {
            return;
        }
        processOneBatchWithTimeout(batch);
    }
}
~~~

Đây là **đoạn minh họa, không phải chương trình biên dịch độc lập**: stopping là AtomicBoolean do component sở hữu, inputBatches và processOneBatchWithTimeout là logic nghiệp vụ giả định. Điểm chính là kiểm tra stop tại ranh giới batch, không ngắt dữ liệu đang ghi giữa chừng khi chưa thiết kế được cách phục hồi.

### Nếu task bị lỗi và dừng lịch ngoài ý muốn

Với một Runnable được scheduleAtFixedRate/scheduleWithFixedDelay, exception không được bắt sẽ làm ngừng những lần chạy sau. Có thể chủ động catch, log và xác định lỗi nào được phép retry; tuy nhiên **không nuốt tất cả lỗi rồi chạy vô hạn**. Nên có ngưỡng lỗi liên tiếp, backoff, trạng thái sức khỏe và cách báo lịch đã hỏng. Cần phân biệt rõ: stopped do yêu cầu quản lý, canceled, và failed do lỗi.

## 10. Quyết định thời gian chờ và xử lý quá hạn

Một chính sách shutdown phải trả lời ba câu hỏi: **cho task hiện tại thêm bao lâu**, **khi hết hạn thì làm gì**, và **nếu đã gửi interrupt mà task vẫn chưa thoát thì sao**.

Ví dụ về thứ tự:

~~~text
RUNNING
   |
   | yêu cầu stop
   v
STOPPING: không nhận lịch mới
   |
   | cancel(false) các periodic future, shutdown()
   v
WAIT_GRACEFUL -- awaitTermination -> terminated=true --> STOPPED
   |
   | timeout (false)
   v
ESCALATE: shutdownNow(), log/metrics
   |
   | awaitTermination thêm, có hạn
   +---- terminated=true --> STOPPED
   |
   +---- terminated=false --> STOP_INCOMPLETE
                                (cần cảnh báo/khôi phục)
~~~

Không nên định nghĩa trạng thái **STOPPED** chỉ bằng isShutdown hoặc isCancelled. Nếu worker vẫn còn trong đoạn I/O/blocking, stop chưa hoàn tất theo tiêu chí giải phóng tài nguyên. Hãy định nghĩa mức độ bảo đảm mà service cam kết: “đã từ chối tác vụ mới”, “đã đề nghị hủy”, hay “đã hết worker và có thể giải phóng mọi phụ thuộc”. Các trạng thái này có thể cách nhau đáng kể về thời gian.

**Deadline tổng** cũng khác với hai lần awaitTermination cùng một timeout: nếu mỗi lần chờ 5 giây, tổng thời gian có thể khoảng 10 giây. Khi hệ thống chỉ cho phép 5 giây toàn bộ, phải tính **thời gian còn lại** theo một deadline monotonic (ví dụ System.nanoTime) trước từng lần đợi, tránh vô tình vượt ngân sách thời gian.

Nếu một luồng khác gọi stop lần nữa khi lần đầu đang đợi, cần tránh race về ai là người escalate và ai nhận kết quả. Một coordinator hoặc completion future cho lifecycle thường rõ ràng hơn nhiều caller tự chờ rồi tự can thiệp.

## 11. Kiểm thử hành vi shutdown bằng tình huống có kiểm soát

Đừng chỉ gọi Thread.sleep rồi hy vọng task đã bắt đầu. CountDownLatch, Semaphore và các công cụ đồng bộ giúp tạo các mốc rõ ràng: task **đã vào thân**, **đang bị giữ**, **được phép thoát**, **đã nhận interrupt**. Từ đó có thể kiểm chứng cả trạng thái Future và trạng thái executor.

| Tình huống | Điều cần kiểm chứng |
|---|---|
| Stop trước lượt đầu tiên | Không có lượt mới; executor terminate theo chính sách |
| Stop khi một lượt đang chạy | cancel(false) ngăn lượt tiếp, lượt đang chạy được hoàn tất |
| Stop khi lượt không đáp ứng | Grace period hết, shutdownNow được thử, chưa được báo thành công nếu executor còn worker |
| Gọi stop hai lần | Không schedule lại, không ném lỗi bất ngờ nếu hợp đồng idempotent |
| Race giữa start và stop | Sau STOPPING không có future mới được chấp nhận |
| Task ném exception | Lượt tiếp theo bị suppress; lỗi được báo/ghi nhận |
| Có delayed one-shot task | Kiểm tra policy về tiếp tục thực thi sau shutdown |
| Phụ thuộc I/O không phản hồi interrupt | Deadline và cảnh báo không bị bỏ qua |
| Caller của stop bị interrupt | Bảo toàn/lan truyền tín hiệu, tránh chờ vô hạn |
| Executor dùng chung | Stop một component không shutdown lịch của component khác |

Có thể theo dõi các chỉ số: số periodic task đang active, thời gian mỗi lượt, lần chạy cuối và kết quả, số lần failure, thời điểm stop được yêu cầu, thời gian chờ shutdown, số lần escalation và cờ executor isTerminated(). Với ScheduledThreadPoolExecutor có thể theo dõi thêm **getQueue().size()** và **getActiveCount()**; đừng coi queue size là số lượt chạy định kỳ đã được lưu trữ trong tương lai hoặc là thước đo duy nhất cho sức khỏe hệ thống.

## 12. Những lỗi thiết kế thường gặp

1. **Chỉ gọi cancel(false) rồi coi việc dừng đã xong.** Future canceled nhưng lượt hiện tại có thể vẫn đang xử lý; cần đồng bộ và xác nhận termination nếu sở hữu executor.
2. **Chỉ gọi shutdown() rồi nghĩ mọi thứ kết thúc tức thì.** shutdown không chờ; one-shot delayed có thể còn tồn tại theo policy mặc định.
3. **Dùng shutdownNow() như lệnh kill bắt buộc.** Interrupt là cooperative; phải xử lý khi không đáp ứng.
4. **Gọi awaitTermination ngay trong periodic Runnable của chính executor.** Worker tự chờ executor mà nó phải thoát để executor terminate, dẫn tới deadlock/timeout.
5. **Không lưu ScheduledFuture.** Khó cancel riêng từng lịch hoặc quản lý component chia sẻ executor.
6. **Quên stop/start concurrency.** Hai lệnh start có thể tạo hai lịch trùng; stop cạnh tranh với start có thể để lọt lịch mới.
7. **Bật continueExistingPeriodicTasksAfterShutdownPolicy(true) mà không có cancel.** Executor có thể tiếp tục chạy định kỳ sau shutdown và không terminate.
8. **Coi isDone() của periodic Future là kết quả từng lượt.** Lịch định kỳ vẫn đang chạy bình thường thường chưa done.
9. **Nuốt InterruptedException hoặc đóng connection trước khi task thoát.** Kéo dài shutdown hoặc làm hỏng dữ liệu đang xử lý.
10. **Khởi động lại cùng executor đã shutdown.** schedule(...) bị từ chối; muốn restart nên tạo vòng đời và executor mới.

## 13. Checklist thiết kế trước khi đưa vào sử dụng

- Ai sở hữu ScheduledExecutorService? Có được phép shutdown executor hay chỉ cancel lịch của mình?
- Lịch chạy theo fixed-rate hay fixed-delay? Có khả năng một lượt lâu hơn khoảng cách mong muốn không?
- Có lưu ScheduledFuture của từng lịch? Stop có chặn được mọi schedule mới không?
- Khi stop, cần hoàn tất lượt đang chạy hay được phép interrupt? Bước nào an toàn để ngừng?
- Đã đặt timeout cho database/network/file I/O và quản lý task con chưa?
- shutdown policy đối với **periodic** và **one-shot delayed** đã đúng chưa?
- Có grace period, escalation, deadline tổng và trạng thái STOP_INCOMPLETE nếu không terminate chưa?
- start/stop/stop lặp lại có đồng bộ và tránh deadlock không?
- Có kiểm thử stop giữa lượt, task lỗi, delayed task và I/O không phản hồi interrupt không?
- Metrics/log có phân biệt “đã yêu cầu stop”, “Future canceled” và “executor terminated” không?

**Ghi nhớ:** một stop/shutdown đáng tin cậy cần kiểm soát **lịch tương lai**, **công việc đang chạy** và **vòng đời executor** một cách riêng biệt. Với Java ScheduledExecutorService, kết hợp **cancel(false)** cho graceful stop, **shutdown() + awaitTermination()** để chờ kết thúc, và **shutdownNow()** như bước escalation có giới hạn; luôn chuẩn bị phương án cho task không hợp tác với interrupt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-013">FutureTask: một tác vụ Runnable có thể được theo dõi bằng Future</a>

<details>
<summary>Xem chi tiết</summary>

# FutureTask trong Java: một Runnable có thể được theo dõi bằng Future

## 1. Vấn đề cần giải quyết

Giả sử bạn có một `Runnable` mô tả công việc chạy nền. Bạn có thể truyền nó cho `Thread` hoặc `Executor.execute()`, nhưng một `Runnable` đơn thuần không có sẵn API để hỏi **“xong chưa?”**, đợi hoàn tất, lấy lỗi thực thi hoặc yêu cầu hủy. Trong khi đó, `Future<V>` biểu diễn **kết quả và trạng thái của một phép tính bất đồng bộ** nhưng bản thân interface `Future` không bắt buộc là một tác vụ có thể đưa trực tiếp vào `Thread`.

`java.util.concurrent.FutureTask<V>` nối hai nhu cầu đó:

~~~text
                 Runnable                 Future<V>
                    |                         |
              void run()                get(), cancel(),
                    |                   isDone(), isCancelled()
                    |                         |
                    +---------+---------------+
                              |
                      RunnableFuture<V>
                              |
                         FutureTask<V>
                              |
                Có thể truyền vào Thread,
                Executor.execute() hoặc gọi run()
                và vẫn giữ một handle Future<V>.
~~~

Bài này thuộc mục **Future & Scheduling** trong **Java Advance - concurrency**. Phạm vi tập trung vào cơ chế chuẩn **Java SE 17+**, không có nội dung riêng của Spring vì Spring đã có chương riêng.

Sau bài, bạn có thể:
- Giải thích `FutureTask<V>` triển khai `RunnableFuture<V>`, vừa có `run()` vừa có `get()/cancel()/isDone()`.
- Tạo FutureTask từ **`Callable<V>`** hoặc từ **`Runnable` + giá trị kết quả `V`**.
- Chạy cùng đối tượng FutureTask bằng `new Thread(task).start()` hoặc `executor.execute(task)`, rồi giữ **chính đối tượng đó** làm Future handle.
- Phân biệt giá trị trả về của `FutureTask(Runnable, result)` và giá trị trả về từ `Callable<V>.call()`.
- Hiểu `FutureTask` là **one-shot computation**: nhiều lời gọi `run()` không làm task tự chạy lại từ đầu.
- Đọc đúng `isDone`, `isCancelled`, `get`, `get(timeout)`, `ExecutionException` và `CancellationException`.
- Hiểu `cancel(true)` chỉ yêu cầu interrupt, không cưỡng bức dừng worker hay rollback side effect.
- Tránh các lỗi ownership, bọc FutureTask hai lần qua `submit`, chờ `get` vô hạn, race giữa cancellation và completion.
- Biết `done()`, `runAndReset()` có vai trò gì ở mức mở rộng và vì sao FutureTask **không tự là một scheduler**.

## 2. Nắm chắc bốn interface/lớp liên quan

### 2.1. Runnable — mô tả hành động

~~~java
Runnable work = () -> {
    System.out.println("processing");
};
~~~

`Runnable.run()` trả về `void`. Một `Runnable` thường chỉ mô tả mã để thực thi; nó không có `get()`, không định nghĩa kết quả trả về và không cung cấp trạng thái completion để caller theo dõi. Nếu đưa trực tiếp vào `Thread`, có thể đợi bằng `Thread.join()`, nhưng `join` không tự trả kết quả, không gói exception của `run` thành `ExecutionException` và không phải Future API.

**`Runnable` không bắt buộc chạy trên thread khác.** Gọi `work.run()` là gọi method bình thường trên **thread hiện tại**. Chỉ khi dùng `new Thread(work).start()` hoặc executor thích hợp thì việc chạy mới được lập lịch ở execution context khác.

### 2.2. Callable<V> — mô tả phép tính trả kết quả

~~~java
Callable<Integer> calculation = () -> 6 * 7;
~~~

`Callable<V>.call()` trả một giá trị kiểu `V` và có thể khai báo `throws Exception`. Nhưng `Callable` **không tự triển khai `Runnable`**, nên không thể truyền trực tiếp như một `Runnable` cho constructor `Thread(Runnable)`.

FutureTask có thể nhận `Callable<V>` và tạo một tác vụ vừa chạy được, vừa quan sát được:

~~~java
FutureTask<Integer> task = new FutureTask<>(calculation);
new Thread(task).start();
Integer result = task.get();
~~~

Ba dòng trên là đoạn minh họa trong một method có import thích hợp và xử lý/khai báo `InterruptedException`, `ExecutionException`.

### 2.3. Future<V> — hợp đồng quan sát kết quả

| Phương thức | Mục đích |
| --- | --- |
| `V get()` | Chờ và lấy kết quả; có thể bị interrupt hoặc nhận lỗi của computation |
| `V get(timeout, unit)` | Chờ có hạn; có thể ném `TimeoutException` |
| `boolean cancel(mayInterruptIfRunning)` | Yêu cầu hủy kết quả/computation |
| `boolean isDone()` | Đã hoàn tất, thất bại hoặc cancelled? |
| `boolean isCancelled()` | Future đã chuyển sang trạng thái cancelled? |

Một Future **không nhất thiết** là một Runnable. Không thể suy rằng mọi implementation của Future đều cho bạn `run()` để tự đưa vào Thread.

### 2.4. RunnableFuture<V> và FutureTask<V>

`RunnableFuture<V>` kế thừa **cả `Runnable` và `Future<V>`**. `FutureTask<V>` là implementation concrete phổ biến của interface này.

~~~java
public class FutureTask<V> implements RunnableFuture<V>
~~~

Dòng trên nhằm minh họa ý nghĩa khai báo kiểu, không phải một định nghĩa lại lớp JDK. Khi bạn có:

~~~java
FutureTask<Integer> task = new FutureTask<>(() -> 42);
~~~

thì cùng **một object `task`** đồng thời:
- Có thể chạy qua `task.run()`, `new Thread(task).start()` hoặc `executor.execute(task)`.
- Có thể theo dõi qua `task.isDone()`.
- Có thể lấy kết quả qua `task.get()`.
- Có thể yêu cầu hủy qua `task.cancel(true)`.

Đây là khác biệt chính giữa một Runnable thông thường và một FutureTask.

## 3. Hai constructor quan trọng

| Constructor | Đầu vào | Giá trị `get()` khi thành công | Dùng khi |
| --- | --- | --- | --- |
| `FutureTask(Callable<V> callable)` | Một phép tính trả `V` | Giá trị do `call()` trả | Tính toán tạo kết quả thật |
| `FutureTask(Runnable runnable, V result)` | Hành động `void` và giá trị `result` có sẵn | Giá trị `result` truyền lúc khởi tạo | Cần theo dõi completion của Runnable có sẵn |

### 3.1. Khi dùng Callable

~~~java
Callable<String> call = () -> "READY";
FutureTask<String> task = new FutureTask<>(call);
~~~

Sau khi task thực thi thành công, `get()` trả `"READY"`. Nếu `call()` ném exception, `get()` ném `ExecutionException` chứa lỗi gốc.

### 3.2. Khi dùng Runnable + result

~~~java
Runnable action = () -> saveLocalState();
FutureTask<String> task = new FutureTask<>(action, "SAVED");
~~~

Đây là snippet với `saveLocalState()` đã tồn tại. Khi `action.run()` **hoàn tất bình thường**, `task.get()` trả đúng giá trị `"SAVED"` do caller **cung cấp trước**. `Runnable` không tự sinh ra chuỗi này.

Nếu `run()` ném unchecked exception, FutureTask ghi nhận failure và `get()` ném `ExecutionException`, **không trả `"SAVED"`**. Nếu task bị cancel, `get()` ném `CancellationException`, bất kể `result` đã được truyền vào constructor.

**Có thể truyền `null` làm result** khi chỉ cần biết task thành công hay thất bại:

~~~java
FutureTask<Void> task = new FutureTask<>(runnable, null);
~~~

Sau khi hoàn tất bình thường, `get()` trả `null`. Để phân biệt “task thành công và trả null” với “chưa chạy”, **không dựa vào giá trị null**; dùng trạng thái Future và cách `get()` kết thúc.

## 4. Ví dụ Java 17 độc lập: Runnable được gắn Future

Tạo file **`RunnableFutureTaskDemo.java`**:

~~~java
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

public class RunnableFutureTaskDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger operations = new AtomicInteger();

        Runnable runnable = () -> operations.incrementAndGet();
        FutureTask<String> tracked =
                new FutureTask<>(runnable, "COMPLETED");

        System.out.println("doneBeforeStart=" + tracked.isDone());

        Thread worker = new Thread(tracked, "tracked-worker");
        worker.start();

        String result = tracked.get();
        worker.join();

        System.out.println("result=" + result);
        System.out.println("operations=" + operations.get());
        System.out.println("doneAfterGet=" + tracked.isDone());
        System.out.println("cancelled=" + tracked.isCancelled());
    }
}
~~~

Biên dịch/chạy bằng JDK 17+:

~~~bash
javac RunnableFutureTaskDemo.java
java RunnableFutureTaskDemo
~~~

Stdout:

~~~text
doneBeforeStart=false
result=COMPLETED
operations=1
doneAfterGet=true
cancelled=false
~~~

**Giải thích:**
1. `AtomicInteger operations` bắt đầu bằng 0. `Runnable` tăng biến này đúng một lần khi chạy.
2. Constructor `FutureTask<>(runnable, "COMPLETED")` dùng chuỗi làm **kết quả cố định nếu task thành công**.
3. Trước khi gọi `Thread.start()`, `isDone()==false`: tạo object FutureTask **không tự thực thi** tác vụ.
4. `worker.start()` đưa FutureTask vào một thread. `tracked.get()` chờ computation và trả `"COMPLETED"`.
5. `worker.join()` đợi worker rời run; main mới đọc `operations`. Không dự đoán thứ tự giữa `get` và việc thread thực sự kết thúc toàn bộ vòng đời, nên dùng `join` khi cần biết thread đã thoát.
6. `isDone()==true` và `isCancelled()==false` khi task hoàn tất bình thường.

**Lưu ý về output:** ví dụ không in thread name/timing vì scheduler quyết định lúc nào worker được cấp CPU. Những giá trị in là xác định khi không có interruption hay lỗi ngoài ý muốn.

**Lưu ý về runnable:** `() -> operations.incrementAndGet()` hợp lệ với functional interface trả `void` vì một lời gọi method có thể được dùng làm statement expression; kết quả số của `incrementAndGet()` bị bỏ qua ở phía `Runnable`. FutureTask trả chuỗi `"COMPLETED"` do constructor cung cấp.

## 5. Ví dụ Java 17: Callable và giá trị thực sự được tính

Tạo file **`CallableFutureTaskDemo.java`**:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class CallableFutureTaskDemo {
    public static void main(String[] args) throws Exception {
        Callable<Integer> calculation = () -> 6 * 7;
        FutureTask<Integer> task = new FutureTask<>(calculation);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.execute(task);

            System.out.println("value=" + task.get());
            System.out.println("valueAgain=" + task.get());
            System.out.println("isDone=" + task.isDone());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac CallableFutureTaskDemo.java
java CallableFutureTaskDemo
~~~

Stdout:

~~~text
value=42
valueAgain=42
isDone=true
~~~

Hai lần `get()` **không làm Callable chạy hai lần**. `FutureTask` ghi nhận kết quả của một lần thực thi; các lần `get()` sau completion đọc lại kết quả đã được công bố. Với một computation đã kết thúc, `get()` không cần chạy lại phép tính.

### Vì sao dùng execute(task), không cần submit(task)?

`ExecutorService.execute(Runnable)` nhận một Runnable và trả `void`. Vì `FutureTask` đã **là Runnable** và **là Future**, bạn có thể:

~~~java
FutureTask<Integer> task = new FutureTask<>(() -> 42);
pool.execute(task);
Integer value = task.get();
~~~

Đây là snippet có `pool`, import và xử lý exception phù hợp. Bạn vẫn giữ handle `task` dù `execute` không trả Future.

Đừng vô tình viết:

~~~java
FutureTask<Integer> inner = new FutureTask<>(() -> 42);
Future<?> outer = pool.submit(inner);
~~~

Vì `submit(Runnable)` thường **tạo một Future khác bao quanh Runnable được truyền vào**, nên lúc này có hai handle: `inner` do bạn tạo, `outer` do executor trả về. `outer.get()` theo overload Runnable có thể trả `null`, còn `inner.get()` trả 42. Hủy `outer` không tạo một bảo đảm trừu tượng rằng `inner` đã chuyển sang trạng thái cancelled hoặc mọi side effect bên trong dừng. Mẫu này làm ownership và cancellation khó suy luận.

**Chọn một trong hai lối rõ ràng:**
- Muốn executor tự quản lý Future: `Future<Integer> f = pool.submit(() -> 42);`.
- Muốn **tự sở hữu một FutureTask** và tùy biến trạng thái/hook: tạo `FutureTask<Integer> task` rồi `pool.execute(task)`, dùng trực tiếp `task.get()/cancel()`.

Không cần bọc một FutureTask trong FutureTask khác chỉ vì muốn có Future.

## 6. FutureTask là one-shot: run() nhiều lần không chạy lại tác vụ

Một câu hỏi quan trọng: FutureTask vừa có `run()`, vậy gọi `run()` lần thứ hai có tính lại không?

**Không.** Với `FutureTask.run()` thông thường, chỉ một lần thực thi của computation được chấp nhận. Sau khi task đã hoàn tất, những lần `run()` tiếp theo không chạy lại `Callable`/`Runnable`.

Tạo file **`FutureTaskOneShotDemo.java`**:

~~~java
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

public class FutureTaskOneShotDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger calls = new AtomicInteger();

        FutureTask<Integer> task = new FutureTask<>(() -> {
            return calls.incrementAndGet();
        });

        task.run(); // Thực thi đồng bộ trên main.
        task.run(); // Không thực thi lại.
        task.run(); // Vẫn không thực thi lại.

        System.out.println("result=" + task.get());
        System.out.println("calls=" + calls.get());
        System.out.println("done=" + task.isDone());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac FutureTaskOneShotDemo.java
java FutureTaskOneShotDemo
~~~

Stdout:

~~~text
result=1
calls=1
done=true
~~~

**Hai hiểu lầm cần tránh:**
1. `task.run()` không tạo thread mới. Trong ví dụ này task chạy **trên main thread** và hoàn thành trước khi dòng sau được thực thi.
2. `FutureTask` không phải lịch lặp. Nếu cần thực hiện phép tính nhiều lần như `1s/lần`, sử dụng `ScheduledExecutorService`/`ScheduledFuture` hoặc tạo **mỗi lần một FutureTask mới**, thay vì tái gọi `run()` của FutureTask đã hoàn tất.

Với nhiều thread cùng gọi `run()` trên một FutureTask **chưa hoàn tất**, cơ chế điều phối của FutureTask chỉ cho một runner thực thi body cho computation đó. Không dùng việc gọi run nhiều lần như cách “tăng tốc bằng nhiều worker”: những caller còn lại sẽ không nhận một phép tính riêng.

### So sánh với Runnable thông thường

~~~java
Runnable work = () -> System.out.println("run");
work.run();
work.run(); // In hai lần vì Runnable không tự ngăn thực thi lại.
~~~

Snippet trên chạy trong main sẽ in hai dòng `run`. `Runnable` là một contract mô tả body; `FutureTask` là một **computation có trạng thái hoàn tất** và bộ nhớ kết quả, nên semantics khác.

## 7. Trạng thái của FutureTask và giá trị get()

Bảng sau mô tả những tình huống caller quan sát:

| Sự kiện | `isDone()` | `isCancelled()` | `get()` |
| --- | --- | --- | --- |
| Chưa chạy / đang chạy | `false` (khi chưa có terminal outcome) | `false` | Chờ |
| Hoàn tất bình thường | `true` | `false` | Trả giá trị `V` |
| Callable/Runnable ném exception | `true` | `false` | Ném `ExecutionException` |
| Cancel thành công | `true` | `true` | Ném `CancellationException` |

**Không dùng `isDone()` để kết luận thành công.** Nó cũng đúng khi task lỗi hoặc bị hủy. Nếu muốn biết task chạy thành công và lấy dữ liệu, thường dùng `get()` rồi xử lý exception.

`isCancelled()==false` cũng **không** có nghĩa task chắc chắn thành công; task có thể chưa chạy, đang chạy hoặc kết thúc bằng exception.

### get() và memory visibility

Theo hợp đồng memory consistency của `Future`, những hành động xảy ra trong computation bất đồng bộ **happens-before** những hành động sau `Future.get()` tương ứng của thread khác. Điều này giúp caller nhìn thấy dữ liệu được công bố bởi computation sau khi `get()` hoàn tất theo contract.

Dù vậy:
- Không dùng `get()` như một transaction bảo vệ **mọi shared mutable object** đang bị các thread khác tiếp tục sửa.
- Nếu task trả về một `ArrayList` rồi thread khác còn mutate list, cần thêm ownership/synchronization.
- `Future.get()` không tự làm các thao tác nhiều bước trên database thành atomic.
- Nếu chỉ có `isDone()`, đừng tự thay thế phần reasoning memory consistency của `get()` mà không đọc contract cụ thể.

### Một số thứ không thể suy từ FutureTask

- `isDone()==true` sau `cancel(true)` **không chứng minh** Callable thực sự đã rời stack hoặc xong cleanup.
- `get(timeout)` ném `TimeoutException` **không tự hủy** FutureTask.
- `cancel(true)` không cưỡng bức mọi I/O hoặc CPU code dừng.
- Completion bình thường không có nghĩa một remote transaction bên ngoài chắc chắn được commit, nếu Callable không báo trạng thái business đầy đủ.

## 8. Ví dụ Java 17: timeout và execution exception

Khi cần giới hạn thời gian đợi, `get(timeout, unit)` có thể ném `TimeoutException`. Với lỗi trong Callable, `get()` ném `ExecutionException` và `getCause()` cho biết lỗi gốc.

Tạo file **`FutureTaskOutcomeDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTaskOutcomeDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        FutureTask<Integer> slow = new FutureTask<>(() -> {
            entered.countDown();
            release.await();
            return 7;
        });

        Thread worker = new Thread(slow, "slow-worker");
        worker.start();
        entered.await();

        try {
            slow.get(20, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            System.out.println("timedGet=TimeoutException");
        }

        System.out.println("stillNotCancelled=" + !slow.isCancelled());
        release.countDown();

        System.out.println("resultAfterRelease=" + slow.get());
        worker.join();

        FutureTask<String> failed = new FutureTask<>(() -> {
            throw new IllegalArgumentException("bad input");
        });
        failed.run(); // Chạy trên main, FutureTask giữ exception.

        try {
            failed.get();
        } catch (ExecutionException e) {
            System.out.println("failureCause="
                    + e.getCause().getClass().getSimpleName());
        }

        System.out.println("failedIsDone=" + failed.isDone());
        System.out.println("failedIsCancelled=" + failed.isCancelled());
    }
}
~~~

Chạy:

~~~bash
javac FutureTaskOutcomeDemo.java
java FutureTaskOutcomeDemo
~~~

Stdout khi không có interruption ngoài ý muốn:

~~~text
timedGet=TimeoutException
stillNotCancelled=true
resultAfterRelease=7
failureCause=IllegalArgumentException
failedIsDone=true
failedIsCancelled=false
~~~

**Tại sao kết quả xác định?**
- `slow` báo đã vào Callable rồi chờ trên `release.await()`. Vì main chưa hạ latch, timed `get(20ms)` không thể lấy kết quả và ném `TimeoutException`.
- `get(timeout)` **không gọi `cancel`**, nên `slow.isCancelled()` vẫn false.
- Khi main hạ `release`, Callable trả 7; `slow.get()` sau đó nhận 7.
- FutureTask `failed` thực thi `Callable` ném `IllegalArgumentException`, nhưng `run()` của FutureTask **ghi nhận lỗi**, không ném nguyên lỗi lên main như khi gọi Callable trực tiếp.
- Lỗi được quan sát qua `failed.get()` dưới dạng `ExecutionException`; `getCause()` là `IllegalArgumentException`.
- `failed.isDone()==true` vì đã kết thúc bằng lỗi, còn `isCancelled()==false` vì không có yêu cầu cancel được chấp nhận.

**Đừng dùng timeout như bằng chứng task không có side effect**: nếu caller retry ngay sau `TimeoutException`, task cũ vẫn có thể chạy và thực hiện I/O. Cần idempotency hoặc cancellation policy nếu nghiệp vụ nhạy cảm.

## 9. cancel(false) và cancel(true) trên FutureTask

Constructor khiến FutureTask có một handle hủy, nhưng cancellation không phải kill không điều kiện.

~~~java
boolean accepted = task.cancel(true);
~~~

Ý nghĩa:
- Khi task chưa chạy, cancel thành công làm computation không được thực thi về sau.
- Khi task đang chạy, `FutureTask.cancel(true)` cố interrupt thread runner.
- `cancel(false)` có thể đánh dấu cancelled mà **không interrupt** runner; mã đang chạy có thể tiếp tục tới khi tự xong.
- Khi task đã completed bình thường hoặc lỗi, không thể “đổi ngược” terminal state bằng cancel.
- Khi cancellation thành công, `isCancelled()==true` và `get()` ném `CancellationException`.
- **`isDone()==true` khi cancelled không đồng nghĩa worker đã rời Callable**.

### Interruption mang tính hợp tác

`Thread.interrupt()` đặt cờ interrupt. Những API như `Thread.sleep()`, `Object.wait()`, `BlockingQueue.take()` và `CountDownLatch.await()` có thể ném `InterruptedException`; CPU loop tự tính không kiểm tra cờ có thể tiếp tục. Tùy I/O, việc interrupt có thể không khiến thao tác trả ngay.

Nếu bắt `InterruptedException` ở tầng không thể truyền tiếp, thường khôi phục trạng thái bằng `Thread.currentThread().interrupt()` **và thoát hoặc chuyển trạng thái theo chính sách hủy**. Chỉ khôi phục cờ rồi tiếp tục business logic vô điều kiện sẽ làm cancel không có hiệu lực.

### Một FutureTask bị hủy vẫn có thể chạy code

Nếu Callable bỏ qua interrupt hoặc đang trong cleanup, nó có thể còn làm việc sau khi Future đã cancelled. FutureTask không tự rollback file, database hay HTTP request đã thực hiện. Với side effect, ứng dụng cần thiết kế timeout ở đúng API, idempotency và cơ chế commit/compensation.

## 10. Ví dụ Java 17: yêu cầu hủy và quan sát worker thực sự thoát

Tạo file **`FutureTaskCancelDemo.java`**:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;

public class FutureTaskCancelDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);

        FutureTask<String> task = new FutureTask<>(() -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
                return "normal-result";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "stopped-by-interrupt";
            } finally {
                exited.countDown();
            }
        });

        Thread worker = new Thread(task, "cancel-worker");
        worker.start();
        started.await();

        boolean accepted = task.cancel(true);
        exited.await();
        worker.join();

        System.out.println("cancelAccepted=" + accepted);
        System.out.println("cancelled=" + task.isCancelled());
        System.out.println("done=" + task.isDone());
        System.out.println("workerExited=" + (exited.getCount() == 0));

        try {
            task.get();
        } catch (CancellationException e) {
            System.out.println("get=CancellationException");
        }
    }
}
~~~

Chạy:

~~~bash
javac FutureTaskCancelDemo.java
java FutureTaskCancelDemo
~~~

Stdout trên đường chạy bình thường:

~~~text
cancelAccepted=true
cancelled=true
done=true
workerExited=true
get=CancellationException
~~~

**Giải thích:**
1. `started.await()` đảm bảo body Callable đã bắt đầu; nó chưa thể hoàn thành bình thường trước `cancel` vì đang tiến tới hoặc chờ ở `sleep(60_000)`.
2. `task.cancel(true)` chấp nhận hủy, cố interrupt runner. Nếu interrupt đến **trước** lời gọi `sleep`, `sleep` cũng quan sát interrupt status và ném exception; nếu đến trong sleep, nó thức dậy bằng exception.
3. `finally` hạ latch `exited` và main dùng `worker.join()` để biết worker đã thật sự kết thúc.
4. Trạng thái kết quả của FutureTask vẫn **cancelled**, nên `get()` ném `CancellationException`, **không** trả chuỗi `"stopped-by-interrupt"` mà Callable tạo trong catch.

**Giới hạn:** demo đợi latch không thời hạn để thể hiện sự khác biệt giữa Future cancelled và worker exited. Với task thực tế có thể không hợp tác, **đặt deadline** cho việc chờ cleanup và đưa ra chính sách giám sát nếu worker không thoát.

### Cancel trước khi task được lên lịch

~~~java
FutureTask<String> task = new FutureTask<>(() -> "NEVER");
boolean accepted = task.cancel(false);
task.run(); // Không thực thi Callable vì task đã cancelled.
~~~

Đây là snippet. Với FutureTask mới, cancel thành công trước `run()` khiến body không được chạy; `get()` sẽ ném `CancellationException`. Mặc dù sau đó ta gọi `run()`, FutureTask là one-shot với terminal state.

## 11. Sử dụng FutureTask với ExecutorService cho đúng

### 11.1. Chạy trực tiếp bằng execute

~~~java
ExecutorService pool = Executors.newFixedThreadPool(4);

FutureTask<Integer> task = new FutureTask<>(() -> expensiveCompute());
pool.execute(task);

try {
    Integer answer = task.get();
    use(answer);
} finally {
    pool.shutdown();
}
~~~

Đây là snippet, đã có `expensiveCompute()` và `use(...)`, cần import các class. Trong production cần xử lý exception/cancel, shutdown timeout và ownership. Điểm chính: `execute(task)` nhận đúng **Runnable** là FutureTask, còn `get()` thực hiện trên cùng FutureTask do caller giữ.

### 11.2. Khi không cần FutureTask tự tạo

~~~java
Future<Integer> answer = pool.submit(() -> expensiveCompute());
Integer result = answer.get();
~~~

`ExecutorService.submit(Callable)` **đã trả một Future**, nên phần lớn ứng dụng không phải tự dựng `FutureTask`. Việc tự tạo FutureTask hữu ích khi:
- Phải dùng một nơi chỉ nhận `Runnable` nhưng vẫn cần Future handle.
- Cần chuyển **cùng một object** qua API `execute` rồi quản lý completion ở nơi khác.
- Muốn subclass FutureTask để can thiệp `done()` hoặc tích hợp với một queue/scheduler tùy biến.
- Muốn test/lập luận lifecycle `run/get/cancel` tường minh.

**Không bọc lồng FutureTask qua `submit(Runnable)` chỉ vì quen sử dụng `submit`**. Điều đó tạo thêm Future và thêm lớp cancellation/lifecycle không cần thiết.

### 11.3. Executor có thể từ chối task

`execute(task)` có thể ném `RejectedExecutionException`, chẳng hạn executor đã shutdown hoặc chính sách từ chối của pool kích hoạt. Nếu `FutureTask` của bạn **chưa được chạy hoặc hủy**, gọi `task.get()` sau một lần submit bị từ chối có thể **chờ vô hạn**, vì không worker nào hoàn thành nó.

Mẫu xử lý:

~~~java
FutureTask<Integer> task = new FutureTask<>(() -> 42);
try {
    pool.execute(task);
} catch (RejectedExecutionException e) {
    task.cancel(false);
    throw e;
}
~~~

Snippet giả định `pool` và imports tồn tại. `task.cancel(false)` trong nhánh này khiến những bên đang giữ handle không mắc kẹt ở một Future chưa được lên lịch. Tuy nhiên **đừng tự động làm vậy nếu rejection handler đã thực thi task bằng chính sách caller-runs hoặc một bên khác đang sở hữu task**; cần biết hợp đồng của executor/rejection policy thực tế. Chỉ một lần `execute` đã bị từ chối không chứng minh body chưa hề chạy trong mọi custom executor.

### 11.4. Dùng chung một FutureTask với hai executor?

Đưa **cùng một FutureTask** vào hai nơi không tạo hai computation độc lập. Tính one-shot có thể khiến một bên không làm gì; thứ tự chịu ảnh hưởng scheduler, và ownership/cancellation trở nên rối. Muốn hai lần chạy độc lập, tạo **hai FutureTask** (hoặc hai `Callable` submissions) riêng.

### 11.5. Cần kết quả theo thứ tự hoàn thành?

`FutureTask.get()` đợi **một Future cụ thể**. Nếu bạn có nhiều FutureTask, tuần tự gọi get theo thứ tự submit có thể gây **head-of-line blocking**: task đầu chậm làm chậm việc xử lý kết quả của task sau đã xong.

Khi cần lấy kết quả theo **completion order**, có thể cân nhắc `ExecutorCompletionService<V>` thay vì tự polling `isDone()` của hàng nghìn FutureTask. `ExecutorCompletionService` kết hợp submit công việc và completion queue, nhưng nó không thay đổi điều kiện chung rằng caller phải quản lý lỗi, timeout và hủy các task còn lại.

## 12. FutureTask.done(): hook khi chuyển sang trạng thái terminal

`FutureTask` cung cấp protected method `done()` để subclass override. Nó được gọi khi FutureTask chuyển sang trạng thái hoàn tất (normal, exception hoặc cancellation). Hook này giúp thực hiện một hành động cực ngắn khi completion xảy ra.

### Ví dụ Java 17: subclass và đếm callback

Tạo file **`FutureTaskDoneHookDemo.java`**:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

public class FutureTaskDoneHookDemo {
    static final class TrackedTask<V> extends FutureTask<V> {
        private final AtomicInteger hookCalls = new AtomicInteger();

        TrackedTask(Callable<V> callable) {
            super(callable);
        }

        @Override
        protected void done() {
            hookCalls.incrementAndGet();
        }

        int hookCalls() {
            return hookCalls.get();
        }
    }

    public static void main(String[] args) throws Exception {
        TrackedTask<String> task =
                new TrackedTask<>(() -> "OK");

        task.run();
        task.run(); // Không hoàn thành lần hai.

        System.out.println("result=" + task.get());
        System.out.println("hookCalls=" + task.hookCalls());
        System.out.println("cancelled=" + task.isCancelled());
    }
}
~~~

Chạy:

~~~bash
javac FutureTaskDoneHookDemo.java
java FutureTaskDoneHookDemo
~~~

Stdout:

~~~text
result=OK
hookCalls=1
cancelled=false
~~~

**Điều quan trọng:**
- `done()` **không phải** callback cho mỗi lần `run()` được gọi; FutureTask chỉ có một completion của computation.
- Khi `run()` thực thi đồng bộ trên main, hook cũng chạy trong flow tương ứng. Nếu cancellation thắng, `done()` có thể chạy trong **thread gọi `cancel`**, không nhất thiết worker.
- **Không mặc định `done()` là dấu hiệu worker đã thật sự thoát khi cancel.** Future có thể chuyển sang cancelled, gọi hook, trong khi callable chưa rời stack.
- Tránh I/O lâu, blocking `get()` hoặc lock khó kiểm soát ngay trong `done()`. Một hook block có thể kéo dài luồng hoàn tất/cancel và gây contention, deadlock.
- Trong hook, nếu muốn biết outcome, phải xử lý các trường hợp normal, exception và canceled thay vì gọi `get()` rồi giả định luôn trả giá trị.
- Thời điểm notification và wakeup của waiter có thể khác với thời điểm xử lý hook; đừng dùng thứ tự giữa chúng để tạo invariant không được Javadoc cam kết.

### Có nên dùng done() để publish kết quả?

Một callback completion có thể gửi tín hiệu tới component khác, nhưng nếu kết quả dùng để quyết định giao dịch, cần có một state/commit protocol rõ ràng. `done()` có thể chạy khi computation thất bại hoặc bị hủy; chỉ dựa vào “hook được gọi” không đủ chứng minh success.

## 13. FutureTask không phải ScheduledFuture

Tên FutureTask nằm trong nhóm **Future & Scheduling**, nhưng `FutureTask` chỉ biểu diễn **một phép tính có thể chạy và quan sát trạng thái**. Nó tự nó **không** giữ một mốc giờ để bắt đầu hay chu kỳ lặp.

Để lập lịch sau delay hoặc định kỳ, `ScheduledExecutorService` và `ScheduledFuture` có API thích hợp:

~~~java
ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

ScheduledFuture<?> scheduled = scheduler.schedule(
        () -> System.out.println("run once"),
        1, TimeUnit.SECONDS);
~~~

Đây là snippet, cần imports và shutdown scheduler ở cuối lifecycle. `ScheduledFuture<?>` kết hợp khả năng Future với `Delayed` (thời gian còn lại trước khi chạy). `scheduleAtFixedRate` và `scheduleWithFixedDelay` có semantics periodic theo contract riêng.

**Đừng cố gọi lại `futureTask.run()` mỗi giây để tạo tác vụ định kỳ.** FutureTask thông thường là one-shot. `FutureTask` có protected `runAndReset()` dành cho những trường hợp mở rộng có thể chạy body rồi reset trạng thái **mà không công bố kết quả terminal** nếu thành công, được sử dụng trong những cơ chế tùy biến. Đây là API protected, không phải lệnh bên ngoài “reset một FutureTask đã done”.

### runAndReset() có nên được sử dụng trong ứng dụng?

Thông thường **không**. Nó có semantics hẹp và cần subclass/kiểm soát state đúng:
- Không dùng được để “làm mới” FutureTask đã hoàn tất bình thường.
- Không có ý nghĩa thay `ScheduledExecutorService` cho periodic jobs phổ thông.
- Nếu Callable ném exception hoặc task bị cancel, việc reset/tiếp tục phụ thuộc contract và state.
- Đòi hỏi hiểu kỹ lifecycle, concurrency và override hook; nên ưu tiên abstraction chuẩn thay vì tự viết scheduler bằng protected method.

Lập lịch và theo dõi completion là hai trách nhiệm khác nhau: executor/scheduler quyết định **khi nào** task chạy; FutureTask quản lý **kết quả và trạng thái** của một computation.

## 14. Những lỗi phổ biến khi dùng FutureTask

1. **Tạo FutureTask rồi gọi `get()` ngay nhưng không start/run/execute.** Không có worker nào hoàn tất task, `get()` có thể chờ mãi. Phải thực sự schedule/run hoặc sử dụng timeout.
2. **Tưởng constructor `FutureTask(Runnable, result)` khiến Runnable trả `result`.** Result là giá trị cố định do caller cung cấp; chỉ công bố khi Runnable chạy thành công.
3. **Gọi `task.run()` và tưởng tạo thread mới.** Đó chỉ là method call trên thread hiện tại, trừ khi được Thread/executor gọi.
4. **Gọi `run()` hai lần và mong chạy lại.** FutureTask là one-shot, không phải scheduler.
5. **Dùng `submit(futureTask)` rồi nhầm Future bên ngoài với chính FutureTask.** Có thể xuất hiện hai handle với return/cancel semantics khác nhau; ưu tiên `execute(futureTask)`.
6. **Dùng `isDone()==true` để kết luận task thành công.** Task có thể thất bại hoặc cancelled.
7. **Dùng `isCancelled()==false` để kết luận task đã hoàn thành.** Có thể chưa bắt đầu/đang chạy.
8. **Gọi `get(timeout)` rồi giả định task đã bị cancel khi timeout.** Timed get chỉ kết thúc chờ của caller.
9. **Xem `cancel(true)` như lệnh kill.** Worker có thể bỏ qua interrupt, đang block I/O hoặc tiếp tục cleanup.
10. **Gọi `task.get()` trong callback/lock quan trọng mà không có timeout hoặc dependency graph.** Có thể gây deadlock nếu task cần chính lock đang giữ hoặc executor có quá ít worker.
11. **Dùng một thread pool có một worker, task A chờ `taskB.get()` mà task B cũng nằm sau A trong cùng queue.** B không có cơ hội chạy: thread-starvation deadlock.
12. **Đẩy cùng FutureTask vào nhiều executor vì muốn nhân đôi tính toán.** One-shot khiến không có hai result độc lập; ownership không rõ.
13. **Không xử lý `ExecutionException`.** Lỗi thực thi nằm trong `getCause()`, không nhất thiết xuất hiện ở nơi gọi `execute`.
14. **Không xử lý `CancellationException`.** Đây là trạng thái hợp lệ của một tác vụ bị hủy, không có kết quả để đọc.
15. **Quên restore interrupt status khi đã bắt `InterruptedException` nhưng không propagate.** Caller cao hơn có thể mất tín hiệu hủy.
16. **Dùng `done()` làm nơi thực hiện blocking I/O lâu.** Hook có thể chạy trên worker hoặc canceller, tăng latency và tạo deadlock.
17. **Không cleanup sau `cancel(true)`.** Future cancelled không chứng minh lock/permit/connection đã được trả.
18. **Không xử lý `RejectedExecutionException` từ executor.** FutureTask có thể ở NEW mãi mà không ai chạy.
19. **Giao side effect quan trọng cho task rồi coi Future cancelled là rollback.** Remote system có thể đã commit; cần idempotency hoặc reconciliation.
20. **Không phân biệt `FutureTask` và `CompletableFuture`.** Cả hai expose Future API, nhưng graph completion/cancellation/interrupt semantics khác nhau theo implementation.

## 15. Test FutureTask như thế nào?

Tập trung xác nhận **cả trạng thái Future và hành vi thực thi**.

### 15.1. Các test nên có

| Kịch bản | Assertion |
| --- | --- |
| Chưa chạy | `isDone()==false`, không có side effect |
| Runnable hoàn tất | `get()` trả giá trị constructor; body chạy một lần |
| Callable hoàn tất | `get()` trả đúng giá trị tính được |
| Gọi `run()` nhiều lần | Body vẫn thực thi một lần |
| Callable ném exception | `get()` ném `ExecutionException` với cause gốc |
| Timed get hết hạn | `TimeoutException`, Future có thể hoàn thành sau |
| Cancel trước chạy | Body không chạy, `get()` ném `CancellationException` |
| Cancel khi đang block interruptible | Future cancelled, worker xử lý interrupt và cleanup |
| Cancel khi task bất hợp tác | Future có thể cancelled trong khi body còn chạy |
| Executor từ chối | Không chờ `get()` trên một task không bao giờ được schedule |
| Hook `done()` | Được gọi tại terminal transition, không mặc định có kết quả success |
| Nhiều thread chờ cùng Future | Mọi waiter nhận cùng outcome của computation |

### 15.2. Điều phối race bằng latch

Một cách test đáng tin là dùng `CountDownLatch`:
- `started`: xác nhận worker đã vào body.
- `release`: giữ worker ở vị trí nhất định cho tới khi main kiểm tra một sự kiện.
- `exited`: xác nhận worker đã rời body.
- `Future.get(timeout)`: bảo vệ test khỏi treo không thời hạn khi có bug.

`Thread.sleep(100)` chỉ gợi ý thời gian chờ, **không chứng minh** worker đã bắt đầu hoặc đang bị block ở đoạn code bạn quan tâm. Dùng latch/barrier để tạo quan hệ thứ tự rõ ràng.

### 15.3. Kiểm thử bằng stdout

Đừng in thời gian thực thi hay thứ tự thread nếu không bảo đảm scheduling. Nếu demo có nhiều worker chạy cạnh tranh, output cần dựa trên:
- Giá trị cuối sau `join/get`.
- Số lần side effect được đếm bằng primitive đồng bộ thích hợp.
- Trạng thái logic đã được điều phối.
- Exception loại nào theo contract.
- Chỉ in những kết quả tất định trong điều kiện demo đã nêu.

### 15.4. Vòng đời thread và tài nguyên

Sau khi dùng executor, thường có:

~~~java
pool.shutdown();
try {
    if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
        pool.shutdownNow();
    }
} catch (InterruptedException e) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là snippet có `pool` đã khai báo. `shutdown()` ngừng nhận task mới nhưng cho các task đã submit hoàn thành; `shutdownNow()` chỉ **cố gắng** ngắt các task đang chạy. `awaitTermination` giúp caller quan sát executor thực sự kết thúc; không tự hứa mọi task có thể bị ngắt tức thời.

## 16. Lựa chọn FutureTask hay API nào khác?

| Nhu cầu | Lựa chọn thường rõ ràng |
| --- | --- |
| Chỉ chạy hành động trên thread khác, caller không cần handle | `Runnable` + `Thread` hoặc `Executor.execute` |
| Chạy Callable và nhận Future, không cần tự tạo object | `ExecutorService.submit(Callable)` |
| Có Runnable sẵn, vẫn muốn `get/cancel/isDone` trên cùng object truyền vào execute | **`FutureTask(Runnable, result)`** |
| Muốn tự giữ một Future/Runnable object, custom `done()` | Subclass **`FutureTask`** có kiểm soát |
| Định kỳ theo lịch | `ScheduledExecutorService` / `ScheduledFuture` |
| Xử lý nhiều completion theo thứ tự xong | `ExecutorCompletionService` |
| Pipeline transformation/composition bất đồng bộ | `CompletableFuture`; hiểu khác biệt cancellation |
| Phân tầng cancellation tới nhiều child tasks | Ownership, Future handles, token/deadline và cleanup policy |
| Cần dừng tác vụ ngay lập tức, kể cả I/O không hợp tác | Không có bảo đảm tổng quát bằng `FutureTask.cancel(true)`; phải thiết kế timeout/cancellation ở resource |

**Chọn theo nhu cầu**, không phải độ “cao cấp” của tên class. FutureTask phù hợp khi bạn cần một object **vừa Runnable, vừa Future**. Nếu chỉ cần submit một Callable rồi `get()`, cách ngắn nhất thường là `executor.submit(callable)`.

## 17. Câu hỏi tự kiểm tra

1. `FutureTask<V>` implement interface nào để vừa chạy được vừa theo dõi được? **`RunnableFuture<V>`**, kết hợp `Runnable` và `Future<V>`.
2. `FutureTask<>(runnable, "OK")` lấy giá trị `"OK"` từ đâu? **Từ tham số constructor**, không phải từ `Runnable.run()`.
3. Khi Runnable ném exception, `FutureTask.get()` trả `"OK"` không? **Không**, nó ném `ExecutionException`.
4. Khác biệt giữa `new Thread(task).start()` và `task.run()`? **start tạo/lập lịch thread; run chỉ gọi method trên thread hiện tại**.
5. `task.run()` ba lần có gọi Callable ba lần không? **Không**, FutureTask one-shot.
6. Vì sao `execute(futureTask)` đủ khi caller vẫn giữ object? **FutureTask đã là Runnable và Future**, không cần executor trả handle.
7. Vì sao `submit(futureTask)` có thể gây nhầm? **Submit(Runnable) thường trả một Future bao ngoài**, khác inner FutureTask.
8. `isDone()==true` có chắc nhiệm vụ thành công không? **Không**, có thể exception/cancel.
9. `isCancelled()==false` có chứng minh task đang chạy không? **Không**, có thể chưa bắt đầu hoặc đã hoàn tất**.
10. Khi task throw, lấy lỗi gốc từ đâu? **`ExecutionException.getCause()`**.
11. `get(timeout)` timeout có tự cancel task không? **Không**.
12. `cancel(true)` có bảo đảm thread đã thoát khi trả `true` không? **Không**, có thể còn chạy/cleanup.
13. Sau cancel thành công, `get()` ném gì? **`CancellationException`**.
14. Có nên gọi `get()` trên FutureTask chưa hề được schedule không? **Không nếu không có cơ chế sẽ chạy hoặc timeout**, vì có thể chờ vô hạn.
15. Một FutureTask đã cancel trước `run()` có thực thi Callable không? **Không**, cancellation terminal ngăn body chạy.
16. Vì sao `done()` không nên làm I/O dài? **Hook có thể chạy trong worker/canceller và làm chậm completion/cancellation hoặc deadlock**.
17. `done()` gọi có bảo đảm task đã kết thúc cleanup không? **Không phải với cancellation**, Future có thể terminal trước khi body rời stack.
18. FutureTask có tự chạy định kỳ khi gọi run lặp lại không? **Không**, dùng `ScheduledExecutorService`.
19. Để chờ kết quả theo thứ tự hoàn tất của nhiều task, nên cân nhắc gì? **`ExecutorCompletionService`**.
20. Có cần tự dựng FutureTask khi chỉ muốn `pool.submit(() -> 42)`? **Không bắt buộc**, submit đã trả Future.
21. Khi `execute` bị từ chối, nguy cơ nếu vẫn gọi `task.get()`? **Có thể chờ mãi vì task không được chạy**.
22. `Future.get()` có memory consistency guarantee gì? **Hành động trong computation happens-before hành động sau `get()` tương ứng ở thread khác**.
23. Task bị cancel sau khi đã ghi database có được rollback bằng FutureTask không? **Không**; phải có giao thức nghiệp vụ tại nơi có side effect.
24. Hai thread cùng gọi `run()` trên một FutureTask tạo hai kết quả độc lập không? **Không**, vẫn cùng một computation.
25. Chương trình có thể dùng FutureTask với một `Runnable` vốn không trả kết quả để biết “đã xong chưa” không? **Có**, thông qua `get/isDone` hoặc `FutureTask<Void>` với result null.

## Kết luận

`FutureTask<V>` là một **implementation cụ thể của `RunnableFuture<V>`**. Nó biến một `Runnable` hoặc `Callable<V>` thành **một computation one-shot vừa có thể được thực thi, vừa có thể được theo dõi bằng Future**:

~~~text
FutureTask(Runnable, result) hoặc FutureTask(Callable)
          |
          +---> Thread.start() / Executor.execute() / run()
          |
          +---> isDone() / isCancelled()
          |
          +---> get() / get(timeout) / cancel(true|false)
~~~

Với `Runnable`, kết quả `get()` là giá trị truyền vào constructor **nếu tác vụ hoàn tất bình thường**; với `Callable<V>`, đó là giá trị do `call()` trả. Các lỗi được bọc trong `ExecutionException`, cancellation dẫn tới `CancellationException`, và timed get **không tự hủy computation**.

`FutureTask` giúp quản lý vòng đời kết quả của một tác vụ, nhưng **không tự là scheduler định kỳ, không bắt buộc mọi I/O phản hồi interrupt, và không thay thế giao thức cleanup/commit**. Khi executor đã có `submit(Callable)` và không cần custom lifecycle, bạn có thể dùng Future được trả về trực tiếp thay vì tạo thêm FutureTask.

## Tài liệu tham khảo Java SE 17

- [FutureTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html): constructors, `run`, `done`, `runAndReset`, `get` và cancellation.
- [RunnableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RunnableFuture.html): kết hợp Runnable và Future.
- [Runnable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runnable.html): contract `run`.
- [Callable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Callable.html): phép tính trả kết quả hoặc exception.
- [Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): get, cancel, isDone, memory consistency.
- [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): execute, submit, shutdown.
- [ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): lập lịch delayed và periodic.
- [ExecutorCompletionService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorCompletionService.html): xử lý kết quả theo completion order.
- [CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): completion pipelines và sự khác biệt cancellation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-014">Future.State, resultNow() và exceptionNow() từ Java 21</a>

<details>
<summary>Xem chi tiết</summary>

# Future.State, resultNow() và exceptionNow() trong Java 21+

## 1. Vấn đề: cần đọc trạng thái và kết quả Future mà không chờ

`Future<V>` mô tả kết quả của một phép tính bất đồng bộ. Trước đây, khi cầm một `Future`, ta thường kết hợp `isDone()`, `isCancelled()` và `get()` để phân biệt kết quả thành công, thất bại hoặc bị hủy. Tuy nhiên, `isDone()` chỉ nói tác vụ đã hoàn tất theo nghĩa rộng; nó **không phân biệt hoàn tất thành công với thất bại hay hủy**. `get()` có thể **block** cho đến khi tác vụ hoàn tất và ném các ngoại lệ tương ứng.

Các phương thức mới trên `java.util.concurrent.Future`:

- `Future.State state()`: trả về **trạng thái quan sát được** của phép tính.
- `V resultNow()`: lấy kết quả đã hoàn tất **thành công**, không chờ.
- `Throwable exceptionNow()`: lấy ngoại lệ từ phép tính đã hoàn tất **thất bại**, không chờ.

Trong Java 21, enum `Future.State` có bốn trạng thái: **`RUNNING`, `SUCCESS`, `FAILED`, `CANCELLED`**.

> **Lưu ý về phiên bản:** Bài học dùng **JDK 21 trở lên** theo chủ đề, nhưng ba API `state()`, `resultNow()` và `exceptionNow()` thực tế được giới thiệu từ **Java 19** (trong Javadoc có “Since: 19”). Vì vậy không nên ghi rằng chúng “được thêm lần đầu ở Java 21”. Chúng không có trong interface `Future` của Java 17. Mọi ví dụ biên dịch trực tiếp dưới đây dùng `javac` và `java` của **JDK 21+**, không phải Java 17.

### Mục tiêu

Sau bài học, bạn có thể:

1. Hiểu ngữ nghĩa bốn giá trị `Future.State`, đặc biệt vì sao `RUNNING` **không nhất thiết là đang chạy CPU**.
2. Chỉ gọi `resultNow()` sau khi `state() == SUCCESS` và `exceptionNow()` sau khi `state() == FAILED`.
3. Phân biệt lỗi `IllegalStateException` của truy cập sai trạng thái với `ExecutionException`, `CancellationException`, `InterruptedException` từ `get()`.
4. Biết lúc nào cần **chờ** với `get()`, lúc nào chỉ cần **kiểm tra ngay** với ba API mới.
5. Áp dụng cho `FutureTask`, `CompletableFuture`, task `ExecutorService.submit` và `ScheduledFuture`.
6. Không nhầm `CANCELLED` với bảo đảm worker đã dừng và dọn dẹp xong.
7. Thiết kế theo nguyên tắc safe publication, concurrency cancellation và observability của Java nói chung, không phụ thuộc framework.

## 2. Future truyền thống và vấn đề đặt câu hỏi sai

Một ví dụ Java 17 quen thuộc:

~~~java
// Mẫu Java 17+, không phải chương trình độc lập.
Future<Integer> future = executor.submit(() -> calculate());

if (future.isDone()) {
    try {
        Integer value = future.get();
        use(value);
    } catch (ExecutionException failed) {
        report(failed.getCause());
    } catch (CancellationException cancelled) {
        recordCancellation();
    }
}
~~~

Dù kiểm tra `isDone()` trước, `get()` vẫn buộc caller xử lý nhiều đường kết quả. Nếu `future` chưa xong, `get()` không timeout có thể chờ vô thời hạn; nếu task bị hủy, `isDone()` vẫn trả true; nếu task ném exception, `isDone()` cũng trả true.

Với Java 21+, ta có thể **hỏi trực tiếp outcome** và lấy kết quả tương ứng:

~~~java
// Mẫu Java 21+, không phải chương trình độc lập.
switch (future.state()) {
    case RUNNING -> notePending();
    case SUCCESS -> use(future.resultNow());
    case FAILED -> report(future.exceptionNow());
    case CANCELLED -> recordCancellation();
}
~~~

Mẫu này tách bạch bốn nhánh. Nhưng nó không thay `get()` ở nơi **phải chờ** kết quả; `resultNow()` không được thiết kế để chờ một task đang chạy hoàn tất.

### Vì sao “kiểm tra không chờ” hữu ích?

- Hiển thị dashboard tiến độ: bao nhiêu Future chưa hoàn tất, thành công, lỗi, bị hủy.
- Thu thập kết quả của các task **đã hoàn tất** mà không block thread tổng hợp.
- Gộp lỗi từ các Future đã kết thúc, phân loại nguyên nhân để ghi log hoặc báo cáo.
- Phân loại `ScheduledFuture` đang chờ thời điểm kích hoạt hoặc đã bị hủy.
- Tương tác với `CompletableFuture`/`FutureTask` thông qua hợp đồng `Future` mà không cần suy diễn từ một chuỗi lệnh try/catch `get()`.

**Không nên** polling `state()` liên tục trong một vòng bận chỉ để đợi kết quả; khi mục tiêu là đợi, hãy chọn `get()`/`get(timeout, unit)`, completion callback hoặc cơ chế phối hợp phù hợp.

## 3. Future.State: bốn trạng thái cần nhớ

### 3.1. Enum và ý nghĩa

Trong Java 21:

~~~java
// Tên các hằng enum, không phải chương trình độc lập.
Future.State.RUNNING
Future.State.SUCCESS
Future.State.FAILED
Future.State.CANCELLED
~~~

| State | Ý nghĩa hợp đồng | Kết quả và cách lấy |
| --- | --- | --- |
| `RUNNING` | Phép tính **chưa hoàn tất** | Chưa thể gọi `resultNow()` hoặc `exceptionNow()` để lấy outcome |
| `SUCCESS` | Phép tính hoàn tất **thành công** | `resultNow()` lấy giá trị, kể cả khi giá trị hợp lệ là `null` |
| `FAILED` | Phép tính hoàn tất bằng **exception** | `exceptionNow()` lấy Throwable gốc |
| `CANCELLED` | Phép tính đã được **hủy theo trạng thái Future** | Không có kết quả/ngoại lệ của một lần hoàn thành FAILED để lấy qua hai method Now |

Enum này không có `NEW`, `QUEUED`, `WAITING` hoặc `TERMINATED`. Vì vậy **`RUNNING` chỉ có nghĩa “not completed”**. Một task **chưa được worker nhận**, đang nằm trong queue, đang chờ delay scheduled, hoặc đang thực sự chạy đều có thể xuất hiện là `RUNNING`. Không dùng nó để kết luận thread đã bắt đầu thực thi task.

### 3.2. Chuyển trạng thái về mặt outcome

~~~text
                       +----------------+
                       |    RUNNING     |
                       | (chưa hoàn tất) |
                       +-------+--------+
                               |
             +-----------------+-----------------+
             |                 |                 |
         hoàn thành        ném exception      hủy được chấp nhận
             |                 |                 |
       +-----v-----+     +-----v-----+     +-----v-------+
       |  SUCCESS  |     |  FAILED   |     | CANCELLED   |
       +-----------+     +-----------+     +-------------+
~~~

Đối với một Future thông thường, trạng thái outcome cuối cùng không chuyển trở lại `RUNNING`. `CANCELLED` có thể được quan sát ngay sau khi việc hủy được chấp nhận, **trước khi tất cả mã bên trong worker hoàn tất cleanup**.

Không suy diễn rằng mọi implementation sẽ thông báo hoặc chạy callback theo thứ tự tuyệt đối tương ứng với cách bạn gọi `state()`. Đây là một **snapshot trạng thái**, không phải một sự kiện hàng đợi được lưu vĩnh viễn.

### 3.3. Thời điểm lấy mẫu và tính đồng thời

~~~java
// Mẫu quan sát, không phải chương trình độc lập.
Future.State seen = future.state();

if (seen == Future.State.RUNNING) {
    // Ngay sau đây, worker vẫn có thể đã hoàn tất.
}
~~~

Kết quả `RUNNING` có thể lập tức cũ khi method trở về. Không có khóa nào từ việc gọi `state()` bảo đảm Future vẫn chưa hoàn tất trong khoảng thời gian tiếp theo.

Ngược lại, nếu thấy `SUCCESS`/`FAILED`/`CANCELLED` ở một Future thực thi đúng contract, đó là outcome kết thúc; các phương thức `resultNow()`/`exceptionNow()` trên đúng nhánh phù hợp. Việc ghi nhận những state terminal là hữu ích để lập báo cáo kết quả, nhưng **lifecycle của side effect** bên ngoài vẫn có thể cần kênh xác nhận riêng khi bị hủy.

## 4. resultNow(): đọc kết quả thành công ngay lập tức

### 4.1. Hợp đồng

`V resultNow()` trả kết quả tính toán **không chờ** khi Future đã hoàn tất thành công.

~~~java
// Mẫu Java 21+, không phải chương trình độc lập.
if (future.state() == Future.State.SUCCESS) {
    V result = future.resultNow();
    consume(result);
}
~~~

Khi nào `resultNow()` ném `IllegalStateException`?

- Future chưa hoàn tất (`RUNNING`).
- Future hoàn tất bằng exception (`FAILED`).
- Future bị hủy (`CANCELLED`).

**Quan trọng:** `SUCCESS` không có nghĩa giá trị khác `null`. Ví dụ một `Callable<Void>` hay `Runnable` được `submit` có thể kết thúc thành công với result `null`. `resultNow() == null` không phải tín hiệu thất bại.

### 4.2. Vì sao không dùng resultNow() như get()?

`get()` có mục đích **đợi** task hoàn thành (nếu chưa xong) và truyền outcome hoặc lỗi qua `ExecutionException`, `CancellationException`, `InterruptedException`. `resultNow()` có mục đích **đọc kết quả đã có** và đưa lỗi sử dụng API vào `IllegalStateException` nếu state chưa đúng.

Nếu caller thực sự cần giá trị để tính bước tiếp theo, có thể dùng:

~~~java
// Mẫu đợi kết quả, dùng được từ Java 5; không độc lập.
try {
    Integer answer = future.get(2, TimeUnit.SECONDS);
    process(answer);
} catch (TimeoutException timedOut) {
    // Quyết định deadline/cancel theo nghiệp vụ.
}
~~~

Đoạn này còn cần xử lý hoặc truyền `InterruptedException` và `ExecutionException` theo hợp đồng method. `TimeoutException` nghĩa là chờ đã hết thời gian, **không tự động hủy Future**.

## 5. exceptionNow(): đọc nguyên nhân khi Future đã FAILED

`Throwable exceptionNow()` trả throwable đã làm phép tính kết thúc thất bại, khi state là `FAILED`:

~~~java
// Mẫu Java 21+, không phải chương trình độc lập.
if (future.state() == Future.State.FAILED) {
    Throwable failure = future.exceptionNow();
    report(failure);
}
~~~

Trong Javadoc Java 21, `exceptionNow()` ném `IllegalStateException` nếu task:

- Chưa hoàn tất (`RUNNING`).
- Đã hoàn tất bình thường (`SUCCESS`).
- Đã bị hủy (`CANCELLED`).

`FAILED` là trạng thái hoàn tất bởi exception, không phải “đã bị cancel”. Việc `cancel(true)` gửi interrupt tới worker rồi worker bắt `InterruptedException` **không** bắt buộc biến Future thành `FAILED`; nếu trạng thái Future đã hủy thành công, kết quả thường là `CANCELLED`.

### 5.1. So sánh với Future.get()

Khi task FAILED:

~~~java
// Mẫu Java, không độc lập.
try {
    future.get(); // Có thể chờ nếu chưa xong.
} catch (ExecutionException wrapper) {
    Throwable cause = wrapper.getCause();
    report(cause);
}
~~~

`get()` ném `ExecutionException` bao bọc nguyên nhân. `exceptionNow()` trả trực tiếp `Throwable` nguyên nhân **khi đã FAILED**; caller không cần bóc thêm `ExecutionException` trong nhánh đó.

Không cố gọi cả hai method Now “để xem cái nào không lỗi”. **Đọc `state()` trước rồi chọn đúng method** sẽ rõ nghĩa hơn và tránh dùng exception như luồng điều khiển bình thường.

## 6. Ví dụ 1 — quan sát đủ bốn trạng thái (Java 21+)

Ta tạo bốn `CompletableFuture` có trạng thái được thiết lập có chủ đích. Không cần thread phụ hoặc `sleep`; do đó đầu ra ổn định.

Tạo tệp `FutureStatesDemo.java`:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class FutureStatesDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> pending =
            new CompletableFuture<>();

        CompletableFuture<Integer> success =
            CompletableFuture.completedFuture(42);

        CompletableFuture<Integer> failed =
            CompletableFuture.failedFuture(
                new IllegalArgumentException("bad input")
            );

        CompletableFuture<Integer> cancelled =
            new CompletableFuture<>();
        cancelled.cancel(false);

        System.out.println("pending = " + pending.state());
        System.out.println("success = " + success.state());
        System.out.println("failed = " + failed.state());
        System.out.println("cancelled = " + cancelled.state());

        System.out.println("result = " + success.resultNow());
        System.out.println("failure type = "
            + failed.exceptionNow().getClass().getSimpleName());
        System.out.println("success state constant = "
            + Future.State.SUCCESS);
    }
}
~~~

Biên dịch/chạy bằng **JDK 21+**:

~~~bash
javac FutureStatesDemo.java
java FutureStatesDemo
~~~

Stdout:

~~~text
pending = RUNNING
success = SUCCESS
failed = FAILED
cancelled = CANCELLED
result = 42
failure type = IllegalArgumentException
success state constant = SUCCESS
~~~

**Phân tích chính xác:**

1. `pending` chưa có ai gọi `complete(...)`, nên `state()` là `RUNNING`, dù **không có thread nào thực sự đang chạy nó**.
2. `completedFuture(42)` đã có result từ đầu, nên `SUCCESS` và `resultNow() == 42`.
3. `failedFuture(...)` đã hoàn tất ngoại lệ, nên `FAILED` và `exceptionNow()` trả Throwable gốc.
4. `cancel(false)` trên `CompletableFuture` chưa hoàn tất đặt trạng thái `CANCELLED`. Không có worker nào để ngắt ở ví dụ này.
5. `Future.State` là enum nằm trong interface `Future`, không phải enum của riêng `CompletableFuture`.

Đây là một cách học rất rõ nghĩa vì nó không trộn các trường hợp outcome với tính ngẫu nhiên của scheduler.

## 7. Ví dụ 2 — guard các method Now và IllegalStateException (Java 21+)

Tạo tệp `FutureNowGuardsDemo.java`:

~~~java
import java.util.concurrent.CompletableFuture;

public class FutureNowGuardsDemo {
    public static void main(String[] args) {
        CompletableFuture<String> pending =
            new CompletableFuture<>();
        CompletableFuture<String> ok =
            CompletableFuture.completedFuture(null);
        CompletableFuture<String> bad =
            CompletableFuture.failedFuture(
                new IllegalStateException("business failure")
            );

        try {
            pending.resultNow();
        } catch (IllegalStateException expected) {
            System.out.println("pending resultNow rejected");
        }

        try {
            ok.exceptionNow();
        } catch (IllegalStateException expected) {
            System.out.println("success exceptionNow rejected");
        }

        try {
            bad.resultNow();
        } catch (IllegalStateException expected) {
            System.out.println("failed resultNow rejected");
        }

        System.out.println("null result success = "
            + (ok.state() == java.util.concurrent.Future.State.SUCCESS
                && ok.resultNow() == null));

        System.out.println("failed exception = "
            + bad.exceptionNow().getMessage());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac FutureNowGuardsDemo.java
java FutureNowGuardsDemo
~~~

Stdout:

~~~text
pending resultNow rejected
success exceptionNow rejected
failed resultNow rejected
null result success = true
failed exception = business failure
~~~

**Lưu ý:** Cả exception đại diện thất bại nghiệp vụ và exception do **gọi sai API** đều có thể thuộc kiểu `IllegalStateException`. Trong ví dụ, `bad.exceptionNow()` trả về đối tượng `IllegalStateException("business failure")` từ task; còn lời gọi `bad.resultNow()` tự ném một `IllegalStateException` vì state không phải SUCCESS. Đừng nhập hai lỗi này làm một khi ghi log.

Khi xây dựng API hỗ trợ người dùng, thông thường nên **chuyển trạng thái thành nhánh rõ ràng**, thay vì dùng các exception `resultNow()`/`exceptionNow()` để thăm dò trạng thái.

## 8. Ví dụ 3 — task ExecutorService ở trạng thái RUNNING rồi SUCCESS

Để minh họa `RUNNING` khi task đã được đưa vào executor nhưng chưa hoàn tất, ta điều khiển điểm chờ bằng `CountDownLatch`. Không dùng `Thread.sleep` để phỏng đoán worker đã chạy.

Tạo tệp `ExecutorFutureStateDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutorFutureStateDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<Integer> future = pool.submit(() -> {
                started.countDown();
                release.await();
                return 21;
            });

            started.await();
            System.out.println("before = " + future.state());

            release.countDown();
            System.out.println("get = " + future.get());
            System.out.println("after = " + future.state());
            System.out.println("resultNow = " + future.resultNow());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 21+:

~~~bash
javac ExecutorFutureStateDemo.java
java ExecutorFutureStateDemo
~~~

Stdout khi task không bị interrupt:

~~~text
before = RUNNING
get = 21
after = SUCCESS
resultNow = 21
~~~

**Vì sao xác định được?**

- Worker đếm `started` trước khi chờ `release`; main đợi `started` rồi mới gọi `future.state()`.
- Main **chưa** đếm `release` ở thời điểm in `before`; callable không thể trả về 21 trước khi latch mở. Vì vậy state là RUNNING.
- Main giải phóng worker bằng `release.countDown()`, sau đó `future.get()` đợi cho đến khi tính toán hoàn tất.
- Sau `get()` thành công, `state()` là SUCCESS và `resultNow()` trả 21 ngay.
- `finally` luôn mở latch để tránh làm worker kẹt nếu caller ném exception trước khi release, và quản lý pool lifecycle.

**Giới hạn:** Nếu đã thấy RUNNING, code không nên kết luận “worker hiện đang chiếm một CPU”. Ở ví dụ này worker đang **chờ latch**. Không có enum `QUEUED`/`BLOCKED`/`WAITING` trong `Future.State`.

## 9. Ví dụ 4 — exceptionNow với Future từ executor (Java 21+)

Tạo tệp `ExecutorFailureNowDemo.java`:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutorFailureNowDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> future = pool.submit(() -> {
                throw new IllegalArgumentException("invalid number");
            });

            try {
                future.get();
            } catch (ExecutionException ex) {
                System.out.println("get cause = "
                    + ex.getCause().getClass().getSimpleName());
            }

            System.out.println("state = " + future.state());
            System.out.println("exceptionNow = "
                + future.exceptionNow().getMessage());

            try {
                future.resultNow();
            } catch (IllegalStateException expected) {
                System.out.println("resultNow rejected");
            }
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ExecutorFailureNowDemo.java
java ExecutorFailureNowDemo
~~~

Stdout:

~~~text
get cause = IllegalArgumentException
state = FAILED
exceptionNow = invalid number
resultNow rejected
~~~

`submit(...)` trả Future giữ kết quả của task. `future.get()` khi task FAILED ném `ExecutionException`; nguyên nhân `IllegalArgumentException` được bao trong `getCause()`. Sau đó `future.state()` trả FAILED, còn `exceptionNow()` trả trực tiếp nguyên nhân để xử lý/log.

**Đừng đánh đồng exceptionNow với lời gọi làm task thất bại.** Task đã ném exception trong executor; `exceptionNow()` chỉ **quan sát** một outcome đã được lưu. Gọi `exceptionNow()` không retry, cancel hay khởi chạy lại task.

## 10. CANCELLED: hủy Future không phải dừng mọi side effect ngay

### 10.1. Cancel có thể đến trước hoặc trong thực thi

`Future.cancel(boolean mayInterruptIfRunning)` yêu cầu hủy. Nếu task chưa bắt đầu, việc hủy được chấp nhận có thể khiến task không chạy. Nếu task đã bắt đầu, `mayInterruptIfRunning == true` cho phép implementation thử interrupt thread chạy task khi biết được thread đó.

Nhưng interrupt vẫn là **cơ chế hợp tác**: worker có thể đang ở đoạn tính toán không kiểm tra cờ, giữ tài nguyên, hoặc chạy cleanup. Vì vậy:

- `future.state() == CANCELLED` không bảo đảm worker đã thoát.
- `future.isDone() == true` có thể là trạng thái cancel chứ không phải nghiệp vụ hoàn thành.
- `future.get()` trên Future hủy ném `CancellationException`, không trả kết quả nghiệp vụ.
- `resultNow()` và `exceptionNow()` không trả outcome của task thành công/thất bại trên Future CANCELLED; hai method này ném `IllegalStateException` theo contract.
- Nếu cần biết worker đã đóng file, release resource hoặc ghi lại trạng thái cuối, dùng latch/kênh xác nhận **riêng**.

### 10.2. Ví dụ 5 — CANCELLED và tín hiệu tới worker (Java 21+)

Tạo tệp `FutureCancellationStateDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public class FutureCancellationStateDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        FutureTask<Integer> task = new FutureTask<>(() -> {
            entered.countDown();
            try {
                for (;;) {
                    Thread.sleep(1_000);
                }
            } catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                return 0;
            } finally {
                finished.countDown();
            }
        });

        Thread worker = new Thread(task, "cancellable-worker");
        worker.start();
        entered.await();

        boolean accepted = task.cancel(true);
        finished.await();
        worker.join();

        System.out.println("cancel accepted = " + accepted);
        System.out.println("state = " + task.state());
        System.out.println("worker observed interrupt = "
            + interrupted.get());
        System.out.println("worker finished = " + !worker.isAlive());

        try {
            task.get();
        } catch (CancellationException ex) {
            System.out.println("get throws CancellationException");
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac FutureCancellationStateDemo.java
java FutureCancellationStateDemo
~~~

Stdout khi worker không có interrupt/lỗi ngoài thiết kế:

~~~text
cancel accepted = true
state = CANCELLED
worker observed interrupt = true
worker finished = true
get throws CancellationException
~~~

**Tại sao vẫn CANCELLED nếu task trong catch trả `0`?** `cancel(true)` đã chấp nhận cancel Future trước khi worker kết thúc. Kết quả trả về sau đó **không thay đổi** trạng thái outcome của Future đã bị cancel thành SUCCESS. `get()` trên Future này ném `CancellationException`.

`entered` bảo đảm worker đã vào callable trước khi main hủy. Nếu cancel đến ngay trước khi worker gọi `sleep`, cờ interrupt vẫn khiến sleep phản hồi bằng exception; nếu đến trong khi sleep, lời chờ bị ngắt. `finished` và `worker.join()` xác nhận đường code cuối và thread thật sự đã kết thúc **trước khi** chương trình in kết quả.

Ví dụ này có kênh `finished` vì `CANCELLED` không tự nói tài nguyên đã cleanup. Trong ứng dụng thực, đặt cleanup vào `finally` hoặc try-with-resources, xử lý lỗi cleanup và có xác nhận khi nghiệp vụ thật sự cần.

## 11. ScheduledFuture cũng là Future

`ScheduledExecutorService` có thể trả về `ScheduledFuture<V>`. Interface này vừa mang thông tin delay (`Delayed`) vừa kế thừa `Future<V>`, vì thế dùng được `state()`, `resultNow()` và `exceptionNow()` ở Java 21+.

### 11.1. One-shot scheduled task

~~~java
// Mẫu Java 21+, không phải chương trình độc lập.
ScheduledFuture<String> future =
    scheduler.schedule(() -> "done", 2, TimeUnit.SECONDS);

if (future.state() == Future.State.RUNNING) {
    // Có thể đang đợi lịch hoặc đang thực thi; chưa kết thúc.
}
~~~

**RUNNING không có nghĩa scheduled task đã tới thời điểm chạy.** Một scheduled task đang chờ delay vẫn chưa hoàn tất nên thuộc RUNNING. Để hỏi thời lượng delay còn lại dùng `getDelay(TimeUnit.MILLISECONDS)`, nhưng giá trị này cũng chỉ là quan sát tức thời.

### 11.2. Ví dụ 6 — scheduled task thành công và scheduled task bị hủy (Java 21+)

Tạo tệp `ScheduledFutureStateDemo.java`:

~~~java
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledFutureStateDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

        try {
            ScheduledFuture<String> immediate =
                scheduler.schedule(
                    () -> "ready", 0, TimeUnit.MILLISECONDS
                );

            ScheduledFuture<String> later =
                scheduler.schedule(
                    () -> "too late", 1, TimeUnit.DAYS
                );

            System.out.println("scheduled result = " + immediate.get());
            System.out.println("scheduled state = " + immediate.state());
            System.out.println("resultNow = " + immediate.resultNow());

            System.out.println("later before = " + later.state());
            later.cancel(false);
            System.out.println("later after = " + later.state());
            System.out.println("cancelled = "
                + (later.state() == Future.State.CANCELLED));
        } finally {
            scheduler.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("scheduler still shutting down");
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ScheduledFutureStateDemo.java
java ScheduledFutureStateDemo
~~~

Stdout thông thường với lịch hệ thống ổn định và scheduler dừng trong 5 giây:

~~~text
scheduled result = ready
scheduled state = SUCCESS
resultNow = ready
later before = RUNNING
later after = CANCELLED
cancelled = true
~~~

Lần schedule đầu có delay 0 và callable trả `"ready"`; `get()` chờ hoàn tất rồi `resultNow()` đọc kết quả đã có. Task `later` có delay một ngày nên chưa hoàn tất khi kiểm tra ngay trong ví dụ thông thường: `RUNNING` chỉ mang nghĩa “not completed”. `cancel(false)` hủy task chưa bắt đầu, đổi trạng thái Future thành CANCELLED. Không có kết quả `"too late"` được công bố.

**Lưu ý về tính xác định:** Dòng `later before = RUNNING` dựa trên lịch chạy thông thường, một độ trễ 1 ngày và việc không có thao tác bên ngoài nào sửa đồng hồ/lịch lập lịch cực đoan; đây không phải chứng minh tổng quát rằng delay luôn tồn tại đúng như thời gian đồng hồ tường. Nếu cần bài test trạng thái hoàn toàn xác định, dùng một `CompletableFuture` chưa hoàn tất như ví dụ 1 hoặc một latch chặn callable như ví dụ 3.

### 11.3. Periodic ScheduledFuture

`scheduleAtFixedRate(...)` và `scheduleWithFixedDelay(...)` trả `ScheduledFuture<?>` cho chuỗi thực thi lặp lại:

- Trong lúc tác vụ lặp còn được lịch hóa, Future thường vẫn ở `RUNNING`: các lần chạy riêng hoàn tất **không** đồng nghĩa toàn chuỗi đã SUCCESS.
- Với task lặp vô hạn bình thường, lời gọi `get()` có thể chờ mãi nếu không bị hủy/thất bại hoặc không có chính sách kết thúc khác.
- Nếu một lần chạy ném exception không xử lý, các lần chạy tiếp theo theo hợp đồng scheduled executor bị **suppressed**, Future có thể hoàn tất `FAILED`.
- Khi hủy được chấp nhận, Future chuyển `CANCELLED`; task đang chạy có thể vẫn cần hợp tác dừng/cleanup.

Không diễn giải `Future.State.SUCCESS` sau mỗi tick định kỳ. Nếu cần thống kê thành công/thất bại **mỗi lần chạy**, task cần ghi metric/kết quả ở từng tick bằng cơ chế riêng.

## 12. state(), resultNow() và exceptionNow() có thực sự không block?

Theo hợp đồng API, đây là các phép **quan sát kết quả mà không chờ task hoàn tất**. Với `Future` interface, chúng được cung cấp dưới dạng default method:

- `resultNow()` bản mặc định kiểm tra `isDone()` rồi gọi `get()` khi Future đã hoàn tất.
- `exceptionNow()` bản mặc định kiểm tra `isDone()`/`isCancelled()` và dùng `get()` để lấy exception nếu đã kết thúc.
- `state()` bản mặc định dùng `isDone()`, `isCancelled()` và `get()` để phân loại outcome.

Do đó **implementation đúng hợp đồng phải bảo đảm tính nhất quán của trạng thái hoàn tất với get**. `FutureTask` và `CompletableFuture` có thể có triển khai/đường tối ưu riêng tùy JDK. Không tự tạo `Future` custom có `isDone() == true` nhưng `get()` còn block kéo dài hoặc vĩnh viễn; đó là một contract không nhất quán và có thể làm các phương thức quan sát hoạt động sai kỳ vọng.

Với một Future đã terminal, đọc outcome không đợi tác vụ chạy tiếp. Tuy nhiên, **độ dài thực thi CPU của một lời gọi method** không phải lời hứa deadline realtime tuyệt đối: thời gian còn phụ thuộc scheduler, khóa nội bộ, JIT và việc xử lý giá trị.

### 12.1. Các default method có ý nghĩa tương thích

Vì ba phương thức đã được thêm làm **default methods trên `Future`**, class implement Future cũ vẫn có thể thừa hưởng hành vi mặc định khi chạy trong JDK mới, miễn tuân thủ contract của `isDone`, `isCancelled` và `get`. Nhưng source code gọi `resultNow()` vẫn phải **biên dịch bằng API Java 19+**, ví dụ `javac` JDK 21; với `--release 17`, ba method đó không tồn tại trong API mục tiêu.

Không dùng reflection để “tự bịa” tên method khi target của ứng dụng là Java 17. Nếu cần hỗ trợ Java 17, giữ `isDone/isCancelled/get`, wrapper tương thích hoặc thiết kế API riêng.

## 13. So sánh get(), get(timeout), join() và các phương thức Now

| API | Có thể chờ kết quả? | Thành công | Thất bại | Hủy |
| --- | --- | --- | --- | --- |
| `Future.get()` | **Có** | Trả `V` | `ExecutionException` | `CancellationException` |
| `Future.get(timeout, unit)` | **Có giới hạn** | Trả `V` | `ExecutionException` hoặc timeout | `CancellationException` |
| `Future.state()` | **Không chờ task hoàn tất** | `SUCCESS` | `FAILED` | `CANCELLED` |
| `Future.resultNow()` | **Không** | Trả `V` | `IllegalStateException` | `IllegalStateException` |
| `Future.exceptionNow()` | **Không** | `IllegalStateException` | Trả `Throwable` | `IllegalStateException` |
| `CompletableFuture.join()` | **Có** nếu chưa xong | Trả `V` | `CompletionException` (thường) | `CancellationException` |
| `CompletableFuture.getNow(default)` | **Không** | Trả giá trị (nếu đã xong) | `CompletionException` (thường) | `CancellationException` |

**Chi tiết cần nhớ:**

- `Future.get()` còn có thể ném `InterruptedException` khi **thread đang chờ** bị ngắt.
- `Future.get(timeout, unit)` thêm `TimeoutException`; timeout **không tự cancel**.
- `CompletableFuture.getNow(default)` dùng giá trị fallback khi **chưa hoàn tất**, còn `Future.resultNow()` **ném `IllegalStateException`** khi chưa hoàn tất.
- `Future.resultNow()` thuộc interface Future nên áp dụng cả cho Future do executor/scheduler trả, không riêng CompletableFuture.
- `CompletableFuture.join()` thuộc class CompletableFuture, không phải method của mọi Future.

### 13.1. Vì sao không dùng isDone() && !isCancelled() để kết luận SUCCESS?

~~~java
// SAI: không đủ phân biệt SUCCESS và FAILED.
if (future.isDone() && !future.isCancelled()) {
    Integer value = future.resultNow(); // Có thể IllegalStateException.
}
~~~

Điều kiện này cũng đúng khi Future đã FAILED. Nếu muốn gọi `resultNow()`, dùng `future.state() == Future.State.SUCCESS`; nếu FAILED thì gọi `exceptionNow()`.

### 13.2. Nếu kết quả null thì sao?

`null` có thể là kết quả của một task thành công, chẳng hạn `submit(Runnable)` không gán result cụ thể hoặc một `Callable<String>` trả null. Dùng `state()` thay vì so sánh `resultNow() == null` để quyết định tác vụ lỗi hay thành công.

## 14. Tổng hợp danh sách Future không chờ

Một ứng dụng có tập Future tương ứng nhiều task. Nếu cần **lấy kết quả hiện có mà không block**, có thể lọc state trước.

### Mẫu tổng hợp

~~~java
// Java 21+, đoạn minh họa trong method có List<Future<Integer>>.
List<Integer> successes = futures.stream()
    .filter(f -> f.state() == Future.State.SUCCESS)
    .map(Future::resultNow)
    .toList();

List<Throwable> failures = futures.stream()
    .filter(f -> f.state() == Future.State.FAILED)
    .map(Future::exceptionNow)
    .toList();
~~~

Ở đây `List` và `Future` cần được import. Mẫu không block để chờ các task chưa xong; những Future `RUNNING`/`CANCELLED` bị bỏ qua. Khi một Future đổi từ RUNNING sang SUCCESS ngay sau lúc được kiểm tra, kết quả của nó có thể **không xuất hiện trong lượt quét hiện tại**; lần sau có thể thu nhận.

Nếu yêu cầu “đợi **tất cả** rồi tổng hợp một lần”, đừng quét liên tục như trên. Có thể sử dụng `Future.get()` trên từng task với deadline, hoặc `CompletableFuture.allOf(...)` với semantics exception phù hợp.

### 14.1. Ví dụ 7 — tổng hợp có kết quả ổn định (Java 21+)

Tạo tệp `FutureCollectNowDemo.java`:

~~~java
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class FutureCollectNowDemo {
    public static void main(String[] args) {
        List<Future<Integer>> tasks = List.of(
            CompletableFuture.completedFuture(3),
            CompletableFuture.failedFuture(
                new IllegalArgumentException("broken")
            ),
            CompletableFuture.completedFuture(5),
            new CompletableFuture<>()
        );

        List<Integer> values = tasks.stream()
            .filter(f -> f.state() == Future.State.SUCCESS)
            .map(Future::resultNow)
            .toList();

        List<String> errors = tasks.stream()
            .filter(f -> f.state() == Future.State.FAILED)
            .map(f -> f.exceptionNow().getClass().getSimpleName())
            .toList();

        long unfinished = tasks.stream()
            .filter(f -> f.state() == Future.State.RUNNING)
            .count();

        System.out.println("values = " + values);
        System.out.println("errors = " + errors);
        System.out.println("unfinished = " + unfinished);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac FutureCollectNowDemo.java
java FutureCollectNowDemo
~~~

Stdout:

~~~text
values = [3, 5]
errors = [IllegalArgumentException]
unfinished = 1
~~~

Bốn Future có trạng thái được tạo sẵn, không chạy worker ngoài và không có thread khác cập nhật, nên kết quả lọc và thứ tự list cố định. `Stream.toList()` giữ encounter order của `List` nguồn, còn `Future::resultNow` chỉ gọi với Future đã SUCCESS.

Trong ứng dụng thực, tập Future có thể hoàn thành đồng thời; bảng tổng hợp tức thời không phải snapshot nguyên tử của **toàn bộ** tập task. Muốn bảo đảm mọi task được tính đúng một lần, cần một giao thức thu thập outcome có trạng thái riêng.

## 15. Java Memory Model và tính nhất quán của kết quả

Javadoc `Future` mô tả memory-consistency effect: **các hành động của phép tính bất đồng bộ happen-before những hành động sau lời gọi `Future.get()` tương ứng ở thread khác**. Vì vậy `get()` là một điểm đồng bộ quan trọng khi caller nhận kết quả đã hoàn tất.

Các implementation Future của JDK còn có cơ chế hoàn tất và công bố outcome an toàn. Tuy nhiên, không nên dùng `state()` như một khóa bảo vệ cho **mọi dữ liệu mutable bên ngoài Future**. Một Future SUCCESS không làm các object khác của ứng dụng tự trở thành bất biến, và không thay thế synchronization nếu kết quả chứa tham chiếu tới object tiếp tục bị thread khác sửa.

### 15.1. Phân biệt ba tầng

1. **Task outcome:** Future đã RUNNING/SUCCESS/FAILED/CANCELLED.
2. **Task business data:** dữ liệu kết quả có mutable không, còn được thread nào cập nhật không?
3. **External side effects:** file/network/database có hoàn tất, rollback hoặc cleanup thật sự chưa?

`Future.State` chủ yếu giải quyết tầng thứ nhất. `resultNow()` cho phép lấy kết quả thành công của tầng thứ nhất; nếu giá trị là một mutable object được chia sẻ, tầng thứ hai vẫn cần invariant và ownership. `CANCELLED` cũng không đảm bảo tầng thứ ba đã dừng.

### 15.2. Đừng chờ giả bằng Thread.sleep

~~~java
// SAI về mặt phối hợp, không phải chương trình độc lập.
Thread.sleep(50);
if (future.state() == Future.State.SUCCESS) {
    use(future.resultNow());
}
~~~

Lần chờ 50ms không bảo đảm task đã hoàn tất. Nếu caller cần chờ, `get(timeout, unit)` biểu diễn deadline rõ ràng; nếu caller chỉ muốn xem trạng thái ngay, không cần thêm `sleep`. Hãy dùng latch/barrier trong test khi cần xác định interleaving.

## 16. Những lỗi phổ biến

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Tưởng ba API bắt đầu từ Java 21 | Javadoc ghi `Since: 19` | Ghi rõ **Java 19+**, ví dụ chạy bằng JDK 21+ |
| Tưởng `RUNNING` = thread đang thực thi | RUNNING nghĩa chưa hoàn tất, gồm queued/delayed | Dùng metric executor/thread dump để biết đang thực thi |
| Chỉ thấy `isDone() == true` liền gọi `resultNow()` | FAILED và CANCELLED cũng là done | Kiểm tra `state() == SUCCESS` |
| Gọi `exceptionNow()` khi CANCELLED | CANCELLED không phải FAILED outcome | Ghi nhận cancel riêng |
| Gọi `resultNow()` trên task RUNNING để “chờ ngắn” | Ném `IllegalStateException`, không đợi | Dùng `get(timeout, unit)` nếu cần chờ |
| Dùng `exceptionNow()` như một cách ném exception gốc | Method **trả Throwable**, không throw nó như result | Quyết định report/rethrow theo contract |
| Coi `resultNow() == null` là lỗi | Task thành công có thể trả null | Phân loại qua `state()` |
| Coi `cancel(true)` là worker đã cleanup xong | Future cancellation và worker lifecycle khác nhau | Latch/ack/kênh kết quả riêng |
| Coi mỗi lần periodic task chạy xong là Future SUCCESS | Periodic schedule chưa kết thúc | Thu outcome theo mỗi tick nếu cần |
| Lặp polling `state()` trong spin loop | Tốn CPU và không có hợp đồng deadline | Blocking get, callback hoặc primitive chờ |
| Không đóng scheduled/executor pool | Worker sống lâu, queue còn task | `shutdown`, `awaitTermination`, fallback `shutdownNow` |
| Tin `get(timeout)` tự hủy task khi hết giờ | Timeout chỉ giới hạn chờ của caller | Quyết định `cancel` riêng |
| Bỏ qua InterruptedException của `get()` | Mất tín hiệu hủy của thread đang chờ | Propagate hoặc restore flag theo hợp đồng |
| Bỏ qua nguyên nhân `ExecutionException` | Mất thông tin task FAILED | Đọc `getCause()` hoặc `exceptionNow()` sau FAILED |
| Coi `state()` là snapshot atomic của toàn tập Future | Mỗi Future thay đổi độc lập | Thiết kế aggregation barrier nếu cần |
| Sử dụng `--release 17` với API mới | `Future` Java 17 chưa có ba default method | Biên dịch với target Java 19+ |

## 17. Checklist review khi áp dụng

- Đang biên dịch/chạy với **JDK 21+** hoặc Java 19+ cho ba phương thức mới?
- Có phân biệt rõ `RUNNING` với “đang chạy thực tế” trên worker không?
- `resultNow()` chỉ được gọi khi Future thật sự SUCCESS?
- `exceptionNow()` chỉ được gọi khi Future thật sự FAILED?
- Có xử lý CANCELLED thành outcome riêng thay vì coi đó là exceptionNow?
- Khi cần đợi kết quả, code có dùng `get()` hoặc deadline `get(timeout, unit)` đúng thay vì polling không giới hạn?
- Với Future FAILED, có báo nguyên nhân Throwable thay vì chỉ in `FAILED`?
- Với nullable result, có phân biệt SUCCESS null với thất bại?
- Với `ScheduledFuture`, có hiểu task delayed có thể RUNNING và periodic task không SUCCESS từng tick?
- Có phân biệt Future cancellation state với worker shutdown/cleanup không?
- Có quản lý executor/scheduler `shutdown` và timeout để không rò thread?
- Nếu implementation `Future` là custom, các method `isDone/isCancelled/get` có nhất quán cho default methods hoạt động đúng?
- Có tránh giả định một lượt quét state() cho nhiều Future là một snapshot chung nguyên tử?
- Khi cần visibility của dữ liệu kết quả, có dựa vào `Future.get()` hoặc giao thức đồng bộ thích hợp?
- Special Note của module được tuân thủ bằng việc chỉ nói về Java concurrency và API chuẩn JDK?

## 18. Câu hỏi tự kiểm tra

**1.** `Future.State.RUNNING` có chắc nghĩa là worker đã bắt đầu thực thi task?

**Không.** RUNNING chỉ nghĩa tính toán chưa hoàn tất. Task trong queue hoặc đợi delay cũng có thể RUNNING.

**2.** `isDone()` trả true, có được gọi `resultNow()` không?

**Không thể suy ra**. Future có thể SUCCESS, FAILED hoặc CANCELLED. Chỉ gọi `resultNow()` khi `state() == SUCCESS`.

**3.** `exceptionNow()` trả gì khi task FAILED?

Trả **Throwable gây thất bại** đã được lưu, không chờ. Khác với `get()` ném `ExecutionException` bao bọc nguyên nhân.

**4.** `exceptionNow()` có trả `CancellationException` nếu Future CANCELLED không?

Không theo hợp đồng phương thức này. Với CANCELLED, `exceptionNow()` ném `IllegalStateException` vì Future không phải FAILED. `get()` trên Future bị hủy mới ném `CancellationException`.

**5.** Future SUCCESS có thể trả `null` từ `resultNow()` không?

Có. SUCCESS mô tả outcome, không buộc result là non-null.

**6.** `resultNow()` có chờ task hoàn tất giống `get()` không?

Không. Nếu chưa hoàn tất hoặc kết thúc ở trạng thái không thành công, nó ném `IllegalStateException`.

**7.** Một `ScheduledFuture` hẹn chạy sau một ngày có thể báo RUNNING ngay sau schedule không?

Có. Trong enum Future, RUNNING chỉ có nghĩa “chưa hoàn tất”, không phải đang thực thi CPU.

**8.** Task periodic chạy xong lần đầu. `ScheduledFuture.state()` có bắt buộc SUCCESS không?

Không. Một periodic future thường còn đại diện cho **các lần chạy kế tiếp**; khi tiếp tục được lịch hóa, nó chưa hoàn tất toàn bộ.

**9.** `Future.cancel(true)` đổi trạng thái thành CANCELLED có chứng minh worker đã đóng mọi tài nguyên không?

Không. Cần xác nhận hoàn thành cleanup riêng nếu nghiệp vụ đòi hỏi.

**10.** Ba API được thêm lần đầu ở Java 21 hay Java 19?

**Java 19.** Chúng có sẵn trong Java 21; ví dụ dùng JDK 21+ để khớp môi trường bài học.

**11.** Có thể dùng `Future::resultNow` trong stream mà không kiểm tra state không?

Không an toàn khi stream chứa các Future RUNNING, FAILED hay CANCELLED. Lọc `f.state() == Future.State.SUCCESS` trước.

**12.** Nếu Future FAILED thì `get()` với `exceptionNow()` khác nhau về biểu diễn exception thế nào?

`get()` ném `ExecutionException`, lấy nguyên nhân qua `getCause()`. `exceptionNow()` trả `Throwable` gốc khi Future đã FAILED.

## 19. Tóm tắt và tài liệu chuẩn

**Quy tắc ngắn gọn:**

~~~text
Future.State.RUNNING   -> Chưa có outcome, không gọi method Now
Future.State.SUCCESS   -> future.resultNow()
Future.State.FAILED    -> future.exceptionNow()
Future.State.CANCELLED -> Xử lý hủy riêng; không lấy result/exceptionNow
~~~

`Future.state()` giúp phân loại một outcome không cần chờ. `resultNow()` và `exceptionNow()` giúp đọc đúng kết quả đã hoàn tất mà không cần gọi `get()` để thăm dò. Khi phải **đợi**, dùng `get()` và quản lý deadline/cancellation; khi phải **xác nhận cleanup**, có kênh hoàn tất riêng. Luôn phân biệt Future outcome với trạng thái worker và với side effect ở hệ thống ngoài.

Tài liệu chính thức để đối chiếu hợp đồng và mốc “Since: 19”:

- [Future (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html)
- [Future.State (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.State.html)
- [ScheduledExecutorService (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)

**Ghi nhớ:** Đọc trạng thái **không phải** đợi hoàn tất; SUCCESS/FAILED/CANCELLED là ba outcome khác nhau, còn RUNNING chỉ có nghĩa “chưa xong”. Chọn đúng API theo câu hỏi mà ứng dụng đang cần trả lời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-015">invokeAll() và invokeAny() có thời hạn: xử lý tác vụ chưa hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# invokeAll() và invokeAny() có thời hạn: xử lý tác vụ chưa hoàn tất

## 1. Đặt thời hạn cho một nhóm Callable

`ExecutorService` cho phép giao một tập `Callable<T>` để chạy trên pool. Đôi khi chúng ta cần tất cả kết quả; đôi khi chỉ cần một kết quả thành công từ nhiều phương án tương đương. Trong cả hai trường hợp, một tác vụ chậm hoặc bị kẹt có thể làm thread gọi chờ quá lâu. Hai overload có timeout giúp đặt **ngân sách chờ chung cho cả batch**:

```java
<T> List<Future<T>> invokeAll(
    Collection<? extends Callable<T>> tasks,
    long timeout, TimeUnit unit) throws InterruptedException;

<T> T invokeAny(
    Collection<? extends Callable<T>> tasks,
    long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException;
```

Đây là chữ ký API minh họa, không phải chương trình Java độc lập. Các bản không có timeout cũng tồn tại. Timeout tính cho toàn lời gọi (gồm giao task và chờ), **không phải timeout riêng cho từng task**. Các overload có timeout sử dụng `TimeUnit` như `MILLISECONDS` hoặc `SECONDS`.

Phân biệt hai câu hỏi:

- `invokeAll`: “Đến deadline, mỗi task đã tạo được kết quả, thất bại hay bị hủy?”
- `invokeAny`: “Có **một** task hoàn tất **thành công** trước deadline hay không?”

Bài này chỉ nói về Java Concurrency chuẩn trong `java.util.concurrent`, độc lập với các framework.

## 2. Bảng so sánh hợp đồng quan trọng

| Tình huống | `invokeAll(tasks, timeout, unit)` | `invokeAny(tasks, timeout, unit)` |
| --- | --- | --- |
| Giá trị trả về bình thường | Danh sách `Future<T>`, đúng thứ tự task đầu vào | Một giá trị `T` của một task hoàn thành thành công |
| Khi deadline hết | Trả danh sách, yêu cầu hủy task chưa xong | Ném `TimeoutException` nếu chưa có thành công |
| Task A lỗi, B thành công | Future A báo `ExecutionException` tại `get()`, B có giá trị | Có thể trả giá trị của B |
| Tất cả task đều lỗi | Trả Future của từng task, mỗi `get()` có thể báo lỗi | Ném `ExecutionException` |
| Khi caller bị interrupt | Ném `InterruptedException`; các task chưa xong được yêu cầu hủy | Ném `InterruptedException`; các task chưa xong được yêu cầu hủy |
| Khi đã có một task thành công | Vẫn chờ các task còn lại hoặc hết hạn | Có thể trả kết quả và hủy các task khác |
| Quan sát kết quả một phần | Có, duyệt Future | Không nhận danh sách Future qua API này |

`invokeAll` không đảm bảo mọi task **thành công** trước khi trả về. Nó bảo đảm khi trả về bình thường, **tất cả Future trong danh sách đều `isDone() == true`**: thành công, lỗi hoặc hủy. Khi deadline hết, executor yêu cầu hủy những task chưa hoàn thành.

`invokeAny` chỉ quan tâm một **kết quả thành công**; một task thất bại sớm chưa đủ để kết thúc nếu còn ứng viên. Nếu tất cả thất bại, phương thức ném `ExecutionException`. Nếu chưa có thành công lúc deadline hết, nó ném `TimeoutException`. Khi có kết quả hoặc khi kết thúc bất thường, các task chưa cần thiết được yêu cầu hủy.

Cả hai API đều có thể phát sinh `RejectedExecutionException` do executor không nhận việc, tùy `RejectedExecutionHandler` được cấu hình. Đó là lỗi tiếp nhận công việc, khác lỗi thực thi của `Callable`.

## 3. invokeAll có timeout: điều gì xảy ra với task chưa xong?

Giả sử gọi `invokeAll` với A, B, C trong ngân sách 500 ms:

1. Các `Callable` được chuyển thành công việc để executor thực thi. Pool nhỏ có thể khiến một số task phải chờ queue.
2. `invokeAll` chờ tất cả hoàn thành hoặc hết thời hạn chung.
3. A có thể đã trả giá trị, B có thể đã ném ngoại lệ, còn C đang chạy/chờ khi deadline hết.
4. API yêu cầu `cancel(true)` đối với tác vụ chưa hoàn tất và trả danh sách `Future` tương ứng A, B, C.
5. Caller phân loại từng Future: thành công, thất bại hoặc hủy.

Trình tự trên là ví dụ minh họa; thực tế phụ thuộc lịch chạy. Danh sách Future được trả theo **thứ tự collection đầu vào**, không theo thứ tự hoàn thành. Nếu dữ liệu có ID, giữ một danh sách ID theo cùng thứ tự để ghép đúng kết quả.

| Trạng thái | `isDone()` | `isCancelled()` | `get()` |
| --- | --- | --- | --- |
| Hoàn thành thành công | `true` | `false` | Trả kết quả `T` (có thể là `null`) |
| Hoàn thành với exception | `true` | `false` | Ném `ExecutionException`; lấy lỗi gốc bằng `getCause()` |
| Bị hủy | `true` | `true` | Ném `CancellationException` |

**Hủy Future không đồng nghĩa task đã dừng thực sự**. `cancel(true)` chỉ cố gửi tín hiệu interrupt đến thread đang chạy; code không hợp tác có thể tiếp tục giữ tài nguyên hoặc gây side effect.

### Ví dụ chạy độc lập: các task hết hạn

Lưu thành `TimedInvokeAllDemo.java`. Một `CountDownLatch` không được mở khiến hai task không thể trả kết quả bình thường; ngay cả task chưa được chạy cũng sẽ được hủy khi hết hạn.

```java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TimedInvokeAllDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> { gate.await(); return 1; },
                    () -> { gate.await(); return 2; });
            List<Future<Integer>> futures =
                    pool.invokeAll(tasks, 200, TimeUnit.MILLISECONDS);

            for (int i = 0; i < futures.size(); i++) {
                Future<Integer> f = futures.get(i);
                System.out.println("task " + (i + 1)
                        + " done=" + f.isDone()
                        + " cancelled=" + f.isCancelled());
                try {
                    System.out.println("value=" + f.get());
                } catch (CancellationException expected) {
                    System.out.println("no result: cancelled");
                } catch (java.util.concurrent.ExecutionException failed) {
                    System.out.println("failure=" + failed.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Workers still running");
            }
        }
    }
}
```

Chạy bằng Java 17+:

```bash
javac TimedInvokeAllDemo.java
java TimedInvokeAllDemo
```

Stdout:

```text
task 1 done=true cancelled=true
no result: cancelled
task 2 done=true cancelled=true
no result: cancelled
```

`gate` không bao giờ được `countDown()`, vì vậy task đã chạy sẽ chờ tại `await()` đến khi bị interrupt, còn task chưa bắt đầu vẫn có thể bị hủy. Cả hai Future trở thành `done` và `cancelled`. Trong ví dụ này, `await()` hỗ trợ interruption để worker thoát; đó không phải thuộc tính của mọi thao tác I/O.

## 4. Giữ lại partial results từ invokeAll

Timed `invokeAll` là lựa chọn hữu ích khi muốn giữ những kết quả đã có trước deadline. Đừng vứt cả batch chỉ vì một task không hoàn tất, trừ khi nghiệp vụ bắt buộc mọi kết quả cùng hợp lệ.

Đoạn minh họa sau giả định `futures` là danh sách được trả về bình thường từ timed `invokeAll`, `taskIds` tương ứng đúng thứ tự và các hàm `record...` do ứng dụng cung cấp:

```java
for (int i = 0; i < futures.size(); i++) {
    Future<Result> f = futures.get(i);
    String id = taskIds.get(i);

    if (f.isCancelled()) {
        recordMissing(id);
        continue;
    }

    try {
        Result value = f.get(); // invokeAll return: Future đã done.
        recordSuccess(id, value);
    } catch (java.util.concurrent.ExecutionException failure) {
        recordFailure(id, failure.getCause());
    } catch (java.util.concurrent.CancellationException cancelled) {
        recordMissing(id);
    } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        break;
    }
}
```

Đây là **fragment**, không phải chương trình độc lập vì `Result` và các hàm phụ trợ chưa được khai báo. Mỗi Future trong list sau `invokeAll` bình thường đã `done`, vì vậy `get()` không đợi task chạy tiếp, nhưng vẫn phải xử lý exception theo chữ ký Java. Nếu thread đọc bị interrupt, khôi phục interrupt flag hoặc truyền exception lên caller theo hợp đồng của ứng dụng.

Phân biệt **thất bại thực thi** (`ExecutionException`), **hết hạn/bị hủy** (`CancellationException`), và **kết quả nghiệp vụ không hợp lệ** (Future thành công nhưng dữ liệu không đạt yêu cầu). Có thể biểu diễn mỗi kết quả bằng một record chứa task ID, status, value, error để tránh nhầm vị trí khi tổng hợp.

Nếu caller bị interrupt ngay trong `invokeAll`, phương thức ném `InterruptedException` và **không trả list Future**; đừng giả định có thể truy hồi partial results qua giá trị trả về khi đã ném lỗi.

## 5. invokeAny có timeout: một thành công là đủ

`invokeAny` phù hợp khi nhiều task thực hiện các phương án tương đương và chỉ cần **một** kết quả thành công. Nó không chọn “task đầu tiên trong danh sách”, cũng không trả kết quả lỗi đầu tiên như một thành công.

- Nếu một task trả `T` trước deadline, `invokeAny` có thể trả ngay `T` và yêu cầu hủy các task khác.
- Nếu vài task ném exception, phương thức tiếp tục thử các task khác khi còn khả năng có kết quả thành công và còn thời gian.
- Nếu tất cả đều thất bại, `ExecutionException` thể hiện không có thành công.
- Nếu deadline hết trước khi có thành công, `TimeoutException` được ném.
- API **không trả `Future` của các task thua**. Nếu cần thông tin chi tiết của mọi task, quản lý Futures riêng hoặc dùng `ExecutorCompletionService`.

Không dùng `invokeAny` trên các tác vụ gây side effect không idempotent một cách tùy tiện: nhiều task có thể đã bắt đầu gửi request, ghi file hoặc ghi dữ liệu **trước khi** tìm ra task thắng. Hủy sau khi một task thắng không hoàn tác tác động đã xảy ra.

### Ví dụ chạy độc lập: kết quả thành công và timeout

Lưu thành `TimedInvokeAnyDemo.java`:

```java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimedInvokeAnyDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch neverOpen = new CountDownLatch(1);

        try {
            String winner = pool.invokeAny(
                    List.<Callable<String>>of(
                            () -> { neverOpen.await(); return "never"; },
                            () -> "ready"),
                    5, TimeUnit.SECONDS);
            System.out.println("winner=" + winner);

            try {
                pool.invokeAny(
                        List.<Callable<String>>of(
                                () -> { neverOpen.await(); return "never"; }),
                        200, TimeUnit.MILLISECONDS);
                System.out.println("unexpected success");
            } catch (TimeoutException expected) {
                System.out.println("timed out");
            }
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Workers still running");
            }
        }
    }
}
```

Chạy:

```bash
javac TimedInvokeAnyDemo.java
java TimedInvokeAnyDemo
```

Stdout khi executor được lập lịch trong ngân sách 5 giây:

```text
winner=ready
timed out
```

Task thứ nhất không có đường trả `"never"` bởi latch không bao giờ mở; task thứ hai trả `"ready"`, nên đó là kết quả thành công duy nhất của cuộc đua đầu tiên. Ở cuộc đua thứ hai, không task nào có thể hoàn tất trước hạn, vì vậy `TimeoutException` đúng. Cả hai nhánh đều có xử lý shutdown trong `finally`.

## 6. Timeout không bảo đảm dừng thread hoặc hoàn tác side effect

Cần tách ba thời điểm: **deadline của caller**, **Future chuyển sang canceled**, và **mã task thực sự ngừng thực thi/giải phóng tài nguyên**. Hai thời điểm sau không nhất thiết trùng nhau.

`cancel(true)` cố interrupt worker đang chạy. `CountDownLatch.await()`, `Thread.sleep()` và nhiều API blocking có thể ném `InterruptedException`. Một vòng lặp CPU không kiểm tra interrupt hoặc I/O không hỗ trợ interruption có thể tiếp tục chạy. Future đã canceled có thể khiến `get()` ném `CancellationException` trong khi worker vẫn thực hiện side effect.

Ví dụ fragment `Callable` có kiểm tra interrupt trong xử lý lặp:

```java
Callable<Integer> job = () -> {
    int count = 0;
    for (Item item : items) {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("cancelled");
        }
        process(item); // Cần timeout I/O riêng nếu có.
        count++;
    }
    return count;
};
```

`Item`, `items` và `process` là phần ứng dụng tự định nghĩa. Nếu `process` gọi I/O có timeout quá dài, kiểm tra interrupt ở đầu vòng lặp có thể chưa đủ. Cần cấu hình timeout ở lớp socket/client hoặc cơ chế hủy thực sự hỗ trợ bởi dependency.

`Callable.call()` cho phép ném `InterruptedException`. Nếu `Runnable` bắt ngoại lệ này, hãy thoát theo hợp đồng và thường khôi phục interrupt flag bằng `Thread.currentThread().interrupt()`; không nên nuốt rồi tiếp tục vô hạn.

Resource bên trong task nên có `try/finally` hoặc `try-with-resources`. Tuy nhiên, task **chưa từng bắt đầu** sẽ không thực hiện cleanup trong body. Resource cấp phát trước khi gửi task phải có owner chịu trách nhiệm khác. Cần thiết kế side effect idempotent và cơ chế đối chiếu kết quả nếu công việc đã gửi sang một hệ thống ngoài JVM.

## 7. Deadline tổng và timeout của từng dependency

Timeout của `invokeAll`/`invokeAny` giới hạn **thời gian chờ của caller ở cấp batch**. Nó không tự cấu hình read timeout của socket, query timeout hoặc hạn xử lý của service bên ngoài.

Ví dụ request có deadline 2 giây, nhưng một Callable chờ network response tối đa 60 giây và không phản ứng interrupt. Sau 2 giây, `invokeAny` ném `TimeoutException` hoặc `invokeAll` trả các Future canceled; worker vẫn có thể chiếm thread trong nhiều giây tiếp theo. Khi xảy ra thường xuyên, thread pool bị cạn dù code phía caller nghĩ đã “timeout”.

Thiết kế nên có:
1. Deadline cho toàn request/batch.
2. Timeout cho từng I/O phù hợp với **thời gian còn lại**.
3. Cancellation hợp tác tại ranh giới blocking.
4. Chính sách cho kết quả đến muộn và side effect đã xảy ra.
5. Đo thời gian thực thi, pending queue, rejection, timeout và worker bị kẹt.

Khi nhiều bước xử lý chung một deadline, tránh tự cộng thêm timeout đầy đủ ở từng bước. `System.nanoTime()` hữu ích để đo khoảng thời gian tương đối, thay vì dùng wall clock có thể điều chỉnh; việc tính và truyền ngân sách còn lại cần được chuẩn hóa theo logic ứng dụng.

## 8. Những tình huống ngoại lệ và dọn dẹp

| Sự kiện | API thể hiện | Hành động của caller |
| --- | --- | --- |
| `invokeAll` hết hạn | Danh sách Future, một số canceled | Gom phần thành công, ghi nhận phần timeout |
| `invokeAll` có một task lỗi | `get()` trên Future lỗi ném `ExecutionException` | Xem `getCause()`, không đánh đồng với timeout |
| `invokeAny` hết hạn | `TimeoutException` | Báo deadline, biết rằng tác vụ chưa chắc dừng ngay |
| `invokeAny` không có task thành công | `ExecutionException` | Ghi nhận thất bại, cân nhắc fallback đúng nghiệp vụ |
| Caller bị interrupt | `InterruptedException` | Truyền tiếp hoặc khôi phục interrupt flag và thoát |
| Executor từ chối nhận task | Thường `RejectedExecutionException` theo handler | Admission control/backpressure, xử lý tải |
| Task đã bị cancel nhưng có ghi dữ liệu | Future không rollback dữ liệu | Idempotency key, xác minh trạng thái bên ngoài |

`shutdown()` chỉ ngừng nhận task mới và để task đã nhận hoàn tất; `shutdownNow()` cố interrupt các worker đang chạy và trả về các task còn chờ nhưng không bảo đảm dừng tức thì. Không tạo executor mới cho mỗi batch rồi bỏ quên; quản lý vòng đời pool và cleanup khi ứng dụng kết thúc.

## 9. Khi nào nên chọn một công cụ khác?

`invokeAll` chỉ trả kết quả sau khi tất cả đã xong hoặc deadline đã qua. Nếu muốn xử lý **từng kết quả ngay khi task nào đó hoàn tất**, `ExecutorCompletionService` có `take()` và `poll(timeout, unit)`, nhưng caller phải tự theo dõi Future và hủy task còn lại đúng thời điểm.

Nếu cần điều phối không chặn, `CompletableFuture` là một lựa chọn khác. Tuy nhiên, `CompletableFuture.anyOf(...)` hoàn tất khi một future **bất kỳ** hoàn tất (có thể lỗi), không tương đương semantics “một thành công” của `invokeAny`. `allOf(...)` cũng không tự cung cấp hành vi timed `invokeAll` về hủy task chưa hoàn tất. Phải thiết kế timeout, lỗi và hủy riêng.

Với tác vụ dài hoặc cần tồn tại qua JVM restart, một queue/job store bền vững phù hợp hơn thao tác Future chỉ trong bộ nhớ.

## 10. Lỗi thường gặp và cách tránh

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Tưởng timed `invokeAll` ném `TimeoutException` | Xử lý sai API | Đọc list và phân loại canceled |
| Tưởng mỗi task có nguyên một `timeout` | Batch có deadline bị hiểu sai | Dùng ngân sách chung |
| Coi `isDone() == true` là thành công | Task lỗi hoặc bị hủy vẫn done | Kiểm tra canceled và `get()` |
| Coi `invokeAny` chọn task đầu tiên | Sai mục đích chọn một thành công | Không dựa vào thứ tự đầu vào |
| Dùng `invokeAny` cho nhiều tác vụ ghi không idempotent | Có thể gây side effect nhiều lần | Dùng idempotency, coordination |
| Nghĩ `cancel(true)` cưỡng ép dừng Java code | Worker còn chạy và giữ tài nguyên | Hỗ trợ interrupt và timeout I/O |
| Nuốt `InterruptedException` | Không phản ứng khi bị hủy | Truyền lại hoặc khôi phục flag rồi thoát |
| Bỏ kết quả thành công khi một phần timeout | Mất thông tin có ích | Tổng hợp partial result theo ID |
| Không shutdown executor | Rò thread/tài nguyên | Quản lý lifecycle và shutdown có kiểm soát |
| Dùng queue vô hạn dù batch có timeout | Task vẫn tích tụ và latency cao | Bounded queue và admission control |

## 11. Câu hỏi tự kiểm tra

**1. `invokeAll(tasks, 1, SECONDS)` hết hạn ném `TimeoutException`?** Không. Nó trả về danh sách Future, đồng thời yêu cầu hủy task chưa hoàn tất.

**2. Future của timed `invokeAll` có thể chưa done khi phương thức trả về bình thường?** Không. Mọi Future đều done, nhưng có thể canceled hoặc failed.

**3. `invokeAny` có trả kết quả của task lỗi đầu tiên?** Không. Nó cần một task thành công; khi tất cả thất bại ném `ExecutionException`.

**4. Khi timed `invokeAny` hết hạn, ta có Future list để lấy partial results không?** Không. API chỉ trả giá trị `T` khi thành công hoặc ném exception. Nếu cần chi tiết từng task, sử dụng cơ chế quản lý Future riêng.

**5. `cancel(true)` thành công có chắc worker đã ngừng ghi dữ liệu?** Không. Interrupt mang tính hợp tác; side effect có thể đã xảy ra hoặc vẫn tiếp tục.

**6. Vì sao task I/O nên có timeout riêng?** Vì timeout của batch chỉ giới hạn chờ của caller, không tự đặt timeout cho socket hoặc các tài nguyên ngoài JVM.

**7. Sau khi `invokeAll` trả về, ghép Future với request gốc như thế nào?** Dùng chỉ số trong danh sách: Future được trả theo thứ tự collection đầu vào.

## Tài liệu tham khảo

- [ExecutorService — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Future — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Callable — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Callable.html)
- [CountDownLatch — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [ExecutorCompletionService — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorCompletionService.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-016">ExecutorService.close() và try-with-resources từ Java 21: điều kiện chờ kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# ExecutorService.close() và try-with-resources từ Java 21: điều kiện chờ kết thúc

## 1. Vấn đề: tạo executor xong thì khi nào các task thực sự kết thúc?

Một `ExecutorService` có thể nhận rất nhiều công việc (`Runnable`, `Callable`), rồi phân phối chúng cho worker thread hoặc một cơ chế thực thi khác. Khi một phương thức không còn cần gửi việc mới, ta phải quyết định **khi nào ngừng nhận việc**, **có chờ các task đã gửi chạy xong không**, **có hủy chúng khi chờ quá lâu không** và **khi nào được xem pool đã kết thúc thật sự**.

Trong Java 21, `ExecutorService` đã là một `AutoCloseable` và có phương thức `close()`. Vì thế có thể dùng:

```java
try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
    executor.execute(() -> doWork());
} // close() được gọi tự động khi thoát try.
```

Đây là **snippet**, cần import `ExecutorService`, `Executors` và định nghĩa `doWork()`. Các chương trình hoàn chỉnh ở phần sau có thể biên dịch và chạy bằng JDK 21.

**Điểm cốt lõi:** mặc định, `close()` khởi động một **orderly shutdown** và **chờ tới khi các task đã nhận hoàn tất thực thi và executor terminated**. Nó **không** đơn thuần là đặt một cờ “đã đóng”, cũng **không** giống `shutdownNow()` ngay từ đầu.

**Mốc phiên bản chính xác:** API `ExecutorService.close()` và việc `ExecutorService` mở rộng `AutoCloseable` được bổ sung từ **Java 19**. Java 21 là một phiên bản LTS thường được dùng để áp dụng chính thức mô hình này cùng các tính năng mới như virtual threads. Vì vậy bài tập dùng **JDK 21+**; đoạn `try (ExecutorService executor = ...)` **không biên dịch với JDK 17** nếu static type của resource là `ExecutorService`.

Bài học chỉ nói về **Java concurrency nói chung**, không gắn với Spring hoặc framework khác.

## 2. Những trạng thái cần phân biệt trong vòng đời ExecutorService

| Khái niệm/API | Ý nghĩa |
| --- | --- |
| `execute()` / `submit()` | Gửi một task vào executor |
| `shutdown()` | Yêu cầu ngừng nhận task mới, cho phép task đã nhận tiếp tục chạy |
| `shutdownNow()` | Cố ngừng task đang thực thi (thường bằng interrupt) và không bắt đầu task còn đang chờ; nỗ lực **best effort** |
| `isShutdown()` | Executor đã bắt đầu quá trình shutdown, **không** có nghĩa đã kết thúc |
| `isTerminated()` | Executor đã thực sự kết thúc sau shutdown |
| `awaitTermination(timeout, unit)` | Thread gọi **chờ có hạn** đến khi executor terminated; trả boolean hoặc ném `InterruptedException` |
| `close()` | Tự khởi động orderly shutdown và **chờ kết thúc**; xử lý interrupt theo quy tắc riêng, **không có tham số timeout** |

Mối quan hệ:

```text
        đang nhận task
               |
      shutdown() / close()
               |
               v
     đã shutdown, đang drain
      |                    |
      | task hoàn tất      | nếu close() bị interrupt khi chờ:
      |                    | chuyển sang shutdownNow() kiểu best effort
      v                    v
      executor terminated (không còn task chạy/chờ)
               |
       close() mới trả về bình thường
```

Một lời gọi `shutdown()` **trả về ngay** sau khi đưa ra yêu cầu; task có thể vẫn chạy. Một lời gọi `close()` bình thường **không trở về** cho đến khi executor kết thúc. `awaitTermination()` thì cho phép chỉ **đợi có giới hạn**, nhưng caller phải tự quyết định làm gì nếu hết hạn.

### “Task đã hoàn tất” không nhất thiết là “nghiệp vụ thành công”

Một `Callable` kết thúc bằng exception vẫn là task đã hoàn tất thực thi, có thể khiến `Future.get()` ném `ExecutionException`. `close()` không tự gom những exception đó rồi ném lại cho caller; nó chờ kết thúc **vòng đời thực thi**, không đánh giá thành công nghiệp vụ. Khi cần kết quả/lỗi, hãy quản lý `Future` hoặc một cơ chế báo outcome khác.

## 3. Chính xác thì ExecutorService.close() làm gì?

Theo hợp đồng của **JDK 21**:

1. **Orderly shutdown:** các task đã nộp trước thời điểm shutdown được tiếp tục xử lý theo chính sách executor; từ chối việc nộp mới.
2. **Đợi termination:** thread gọi `close()` chờ cho tới khi executor đã thực sự kết thúc. Đây là điểm khiến `close()` có thể **blocking**.
3. **Nếu thread đang đợi bị interrupt:** `close()` chuyển sang nỗ lực dừng task đang thực thi **như gọi `shutdownNow()`**, và những task vẫn đang nằm trong hàng chờ **không được chạy** theo hợp đồng.
4. **Tiếp tục chờ task đang thực thi dừng:** sau khi yêu cầu dừng, `close()` vẫn chờ các task đang hoạt động kết thúc. Interrupt không khiến phương thức tự bỏ chờ ngay.
5. **Khôi phục interrupt status:** trước khi trở về, cờ interrupt của thread gọi được đặt lại.
6. **Nếu executor đã terminated:** gọi `close()` không còn tác dụng.

**Hệ quả quan trọng:** `close()` không khai báo ném `InterruptedException`. Người gọi **không thể** viết `catch (InterruptedException)` chỉ để bắt từ `close()` nếu trong khối tương ứng không có API nào khác ném checked exception đó. Khi bị interrupt trong giai đoạn chờ, `close()` xử lý theo quy tắc trên và đặt lại cờ trước lúc trả về.

**Đừng hiểu “shutdownNow” là kill mạnh:** việc dừng task đang chạy thường được yêu cầu thông qua `Thread.interrupt()`. Nếu task đang ở I/O không thể bị ngắt hoặc cố tình bỏ qua interrupt, executor có thể **chậm kết thúc hoặc không bao giờ terminated**; `close()` vì vậy cũng có thể chờ rất lâu. API không cung cấp một hard timeout tự động.

### Không có lời hứa “đóng resource là dừng tất cả ngay”

Try-with-resources đảm bảo gọi `close()` khi rời scope, **không** bảo đảm đóng trong vài mili-giây. Nếu task chờ một sự kiện mà chỉ code **sau khối try** mới có thể phát tín hiệu, thread gọi có thể tự mắc kẹt tại `close()`. Phải thiết kế dependency để task có khả năng hoàn tất **trước khi cần đi qua dòng code sau try**.

## 4. Java 21+: chương trình đầu tiên với try-with-resources

Tạo file `CloseWaitDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CloseWaitDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch mayFinish = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.execute(() -> {
                started.countDown();
                try {
                    mayFinish.await();
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            System.out.println("Inside try, completed: " + completed.get());
            mayFinish.countDown();
        }

        System.out.println("After close, completed: " + completed.get());
    }
}
```

Biên dịch/chạy bằng **JDK 21 trở lên**:

```bash
javac CloseWaitDemo.java
java CloseWaitDemo
```

Đầu ra khi không có interrupt từ bên ngoài:

```text
Inside try, completed: 0
After close, completed: 1
```

**Tại sao đầu ra ổn định?**

- Worker báo đã khởi động qua `started.countDown()` nhưng chưa được qua `mayFinish.await()`.
- Main chờ `started.await()` rồi in số task hoàn tất. Cổng vẫn đóng nên bộ đếm chắc chắn là 0.
- Main mở cổng và thoát `try`; `close()` được gọi tự động. Nếu worker chưa tăng bộ đếm, close còn phải chờ worker kết thúc.
- Dòng `After close` được thực thi **sau khi close trả về**, nên khi task hoàn tất bình thường kết quả là 1.

Đây là ví dụ với executor một worker để phân biệt rõ **thoát scope** và **termination**. Trong ứng dụng có nhiều task, close đợi tất cả task đã được nhận theo cơ chế orderly shutdown, không chỉ task cuối cùng được submit.

### Có cần Future.get() trước khi rời try không?

**Không bắt buộc nếu chỉ cần chờ task hoàn tất** theo mô hình `close()`. Nhưng nếu cần **giá trị trả về** hoặc **nguyên nhân thất bại**, vẫn cần `Future.get()` hoặc một cơ chế báo kết quả. `close()` chỉ chờ vòng đời, không trả một danh sách kết quả như `invokeAll()`.

## 5. Phân biệt close() với shutdown(), awaitTermination() và shutdownNow()

| Cách | Có yêu cầu ngừng nhận task mới? | Tự chờ kết thúc? | Có deadline chờ? |
| --- | --- | --- | --- |
| `shutdown()` | Có | Không | Không |
| `shutdownNow()` | Có, đồng thời cố dừng task đang chạy | Không | Không |
| `shutdown(); awaitTermination(timeout, unit)` | Có | Có, **tối đa theo timeout** cho mỗi lần await | Có |
| `close()` | Có | Có, đến khi executor terminated | **Không** |
| `try (ExecutorService e = ...)` | Có khi rời scope | Có qua `close()` | **Không** |

**Không gọi `awaitTermination()` trước `shutdown()` rồi mong executor tự dừng:** `awaitTermination` chỉ chờ termination, không tự yêu cầu shutdown. Nếu executor vẫn nhận việc và chưa có ai shutdown, lời gọi có thể timeout mà không thay đổi trạng thái pool.

**Không suy rằng `shutdownNow()` đã dừng xong:** phương thức này trả về danh sách task **đang chờ chưa bắt đầu** theo hợp đồng và cố tác động đến task đang chạy. Nó không chờ thread thực sự chấm dứt. Muốn kiểm tra kết thúc, dùng `awaitTermination()` hoặc `isTerminated()`.

### Khi nào nên chọn close()?

Phù hợp khi một scope sở hữu riêng executor, số task hữu hạn, task có thể kết thúc, và muốn chắc rằng **không còn task của executor đang chạy khi ra khỏi scope**.

Ví dụ fan-out theo yêu cầu: tạo executor, gửi ba Callable độc lập, thu thập kết quả rồi kết thúc phương thức. Try-with-resources giúp người viết không quên shutdown dù đường xử lý ném exception.

### Khi nào không nên dùng close() trực tiếp?

- Cần một **thời hạn kết thúc hữu hạn** (SLA, graceful shutdown 5 giây, rồi chuyển sang shutdownNow).
- Executor là **tài nguyên dùng chung**, do thành phần khác sở hữu và có các request khác đang dùng; một scope nhỏ không được đóng nó.
- Task có thể **chạy định kỳ vô hạn**, đợi sự kiện không thể phát ra trước khi ra khỏi scope, hoặc làm I/O không có điểm hủy.
- Một đoạn code đang giữ lock mà task cần mới có thể kết thúc; close trong lúc giữ lock có thể tạo deadlock.
- Cần kiểm soát thời điểm/chiến lược stop và quan sát các task chưa thực thi.

Trong các tình huống đó, thiết kế lifecycle riêng với `shutdown`, `awaitTermination` và `shutdownNow` phù hợp hơn; hoặc bảo đảm task kết thúc trước khi scope đóng.

## 6. Java 21+: giá trị và lỗi phải lấy qua Future

Dù mọi task đã dừng, `close()` không biết khái niệm nghiệp vụ “thành công” của task. Ví dụ một task thất bại thì try-with-resources vẫn đóng pool, và caller phải xem Future nếu muốn báo lỗi.

Tạo file `CloseWithFutureDemo.java`:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CloseWithFutureDemo {
    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> ok = executor.submit(() -> 6 * 7);

            Callable<String> failing = () -> {
                throw new IllegalArgumentException("invalid");
            };
            Future<String> bad = executor.submit(failing);

            System.out.println("Value: " + ok.get());

            try {
                System.out.println("Other: " + bad.get());
            } catch (ExecutionException e) {
                System.out.println("Task error: "
                        + e.getCause().getClass().getSimpleName());
            }
        }

        System.out.println("Executor scope ended");
    }
}
```

Biên dịch/chạy:

```bash
javac CloseWithFutureDemo.java
java CloseWithFutureDemo
```

Đầu ra:

```text
Value: 42
Task error: IllegalArgumentException
Executor scope ended
```

- Task thứ nhất trả về 42.
- Task thứ hai ném `IllegalArgumentException`; `Future.get()` của nó ném `ExecutionException` chứa nguyên nhân.
- `close()` được gọi dù có exception ở bên trong scope. Ở đây exception từ `bad.get()` đã được xử lý; nếu không bắt, try-with-resources vẫn gọi close trước khi exception được truyền ra ngoài.
- Không được đọc việc đã đến cuối `close()` như bằng chứng rằng tất cả task đều trả kết quả thành công.

**Đặc biệt:** sau khi một exception thoát khỏi khối `try`, quá trình auto-close diễn ra **trước khi** exception được xử lý ở tầng ngoài. Nếu task còn chạy lâu hoặc kẹt, việc báo lỗi ra ngoài có thể bị trì hoãn vì đang chờ close.

## 7. Java 21+: close() khi caller đã nhận interrupt

Khi thread gọi `close()` đang chờ mà nhận interrupt, default implementation chuyển sang nỗ lực dừng các task theo hướng `shutdownNow()`, tiếp tục đợi các task đang chạy kết thúc rồi khôi phục interrupt status.

Có thể kiểm chứng bằng một task đang chờ latch. Tạo file `InterruptedCloseDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptedCloseDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverOpens = new CountDownLatch(1);
        AtomicBoolean workerSawInterrupt = new AtomicBoolean();

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.execute(() -> {
                started.countDown();
                try {
                    neverOpens.await();
                } catch (InterruptedException e) {
                    workerSawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            // Gửi interrupt cho chính caller trước lúc auto-close.
            Thread.currentThread().interrupt();
        }

        System.out.println("Worker interrupted: "
                + workerSawInterrupt.get());
        System.out.println("Caller flag restored: "
                + Thread.currentThread().isInterrupted());

        // Chỉ xóa cờ để bản demo kết thúc gọn.
        Thread.interrupted();
    }
}
```

Chạy bằng JDK 21+:

```bash
javac InterruptedCloseDemo.java
java InterruptedCloseDemo
```

Đầu ra khi không có tác động khác vào các thread:

```text
Worker interrupted: true
Caller flag restored: true
```

Giải thích:

1. Worker đã chạy và báo `started`, nhưng đang hoặc sắp chờ `neverOpens.await()`.
2. Caller tự đặt interrupt status trước khi ra khỏi scope. Khi `close()` đợi termination, việc chờ thấy interrupt và chuyển sang cơ chế nỗ lực stop giống `shutdownNow()`.
3. Worker nhận interrupt. Dù interrupt tới trước khi `await()` thực sự bắt đầu, lần await tiếp theo cũng phát hiện cờ và ném `InterruptedException`.
4. Worker bắt exception và kết thúc. Close **vẫn đợi** worker kết thúc thực thi, sau đó trả về với cờ caller được khôi phục.
5. Cờ của caller còn true sau close, không phải vì close ném `InterruptedException`.

**Đừng sao chép mẫu cố ý interrupt caller vào logic bình thường:** ví dụ này chỉ làm rõ hợp đồng của close. Trong ứng dụng thật, interrupt thường do yêu cầu hủy/shutdown đến từ một thành phần khác. Nếu task không phản hồi interrupt, close có thể vẫn chờ mãi, vì vậy không thể dùng close như giải pháp timeout.

## 8. Task không hợp tác interrupt có thể khiến close không kết thúc

Một task có thể:

- Lặp tính toán CPU mà **không kiểm tra `isInterrupted()`**.
- Chặn ở một điểm không interruptible, chẳng hạn chờ monitor `synchronized` đang do thread khác giữ.
- Gặp I/O có semantics interrupt riêng mà hệ thống không hỗ trợ dừng nhanh.
- Chờ một latch/condition mà tín hiệu hoàn tất chỉ được gửi **sau khi close trả về**.
- Gọi `Future.get()` trên một task khác trong cùng pool nhỏ gây thread starvation deadlock.

Trong các tình huống này, `close()` không cung cấp lối thoát timeout. Việc caller nhận interrupt chỉ thúc đẩy yêu cầu `shutdownNow()`, **không hứa toàn bộ mã task buộc phải dừng**.

### Mẫu tự deadlock cần tránh

```java
// Snippet NGUY HIỂM, không phải chương trình độc lập.
CountDownLatch mayFinish = new CountDownLatch(1);

try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
    executor.execute(() -> {
        try {
            mayFinish.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    });
}

// Dòng này không tới được nếu close vẫn chờ worker:
mayFinish.countDown();
```

Nếu không có interrupt hay nguồn tín hiệu nào khác, task chờ latch; `close()` chờ task; code mở latch lại nằm **sau close**. Đó là chu trình chờ khiến chương trình có thể treo vô hạn.

**Sửa:** chuyển `mayFinish.countDown()` vào trước lúc thoát scope, hoặc thiết kế producer/timer/cancellation không phụ thuộc vào thread đang bị block ở close.

### Lock giữ trong lúc đóng executor

Nếu caller giữ `synchronized(lock)` rồi thoát try-with-resources bên trong vùng đó, mà một worker cần **cùng lock** để kết thúc, caller chờ close và worker chờ lock. Dù mọi API đều “thread-safe” riêng, tổ hợp vẫn deadlock. Trước khi đóng executor, tránh giữ các khóa/tài nguyên mà task cần để hoàn tất.

## 9. Java 17: cách quản lý vòng đời tương đương về mục tiêu

Trong Java 17, `ExecutorService` **chưa** mở rộng `AutoCloseable` và chưa có `close()`. Mẫu shutdown truyền thống phải viết rõ.

Tạo file `LegacyShutdownDemo.java`, có thể chạy Java 17+:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LegacyShutdownDemo {
    private static void stopWithinBudget(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("Pool has not terminated");
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> answer = pool.submit(() -> 40 + 2);
            System.out.println("Answer: " + answer.get());
        } finally {
            stopWithinBudget(pool);
        }
    }
}
```

Chạy với JDK 17 hoặc mới hơn:

```bash
javac LegacyShutdownDemo.java
java LegacyShutdownDemo
```

Đầu ra khi task hoàn tất bình thường:

```text
Answer: 42
```

So sánh với `close()`:

- `stopWithinBudget` cho pool **2 giây chờ orderly shutdown**, rồi yêu cầu `shutdownNow()` và đợi thêm **2 giây**. Sau đó nó có thể **trả về ngay cả khi pool chưa terminated** (đã ghi cảnh báo).
- `close()` mặc định chờ executor terminated **không giới hạn timeout**, kể cả sau khi chuyển sang shutdownNow vì bị interrupt.
- Ở ví dụ Java 17, xử lý `InterruptedException` của thread caller khởi động shutdownNow rồi **khôi phục cờ**, nhưng không tiếp tục chờ vô hạn.
- Đây là **hai hợp đồng khác nhau**. Dùng try-with-resources vì gọn **không tương đương** với shutdown có deadline nghiệp vụ.

**Một lưu ý riêng về process:** worker thread của các executor thông thường có thể khiến JVM tiếp tục chạy ngay cả khi caller ngừng chờ; việc gọi `shutdownNow()` cũng không bảo đảm worker đã dừng. Thiết kế shutdown cần theo dõi và xử lý task không phản hồi cancellation.

## 10. Virtual threads từ Java 21 và try-with-resources

Java 21 đưa virtual threads thành tính năng chính thức; `Executors.newVirtualThreadPerTaskExecutor()` tạo executor dùng **một virtual thread cho mỗi task**. Executor này cũng là `ExecutorService` nên hưởng lợi từ `close()`/try-with-resources.

Tạo file `VirtualThreadScopeDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadScopeDemo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(() -> 10);
            Future<Integer> second = executor.submit(() -> 20);

            System.out.println("Total: " + (first.get() + second.get()));
        }

        System.out.println("All scoped tasks ended");
    }
}
```

Chạy bằng JDK 21+:

```bash
javac VirtualThreadScopeDemo.java
java VirtualThreadScopeDemo
```

Đầu ra:

```text
Total: 30
All scoped tasks ended
```

Điểm cần hiểu:

- Executor tạo virtual thread cho từng task; không đồng nghĩa 2 task **đồng thời chạy CPU** hoặc phải hoàn tất theo thứ tự submit.
- `Future.get()` lấy kết quả của task, còn `close()` bảo đảm phạm vi executor đã kết thúc trước khi dòng cuối chạy.
- Virtual threads giúp mở rộng mô hình nhiều task blocking trong nhiều tình huống, **không sửa tự động** data race, deadlock, giao dịch hoặc task bỏ qua cancellation.
- Không tạo virtual thread executor ở mỗi tầng nhỏ nếu không hiểu ownership và thời điểm đóng; scope chỉ nên đóng những executor **mà chính scope sở hữu**.

### Vì sao close của virtual-thread-per-task executor đáng lưu ý?

Ví dụ fan-out: trong một hàm, gửi N lời gọi độc lập, chờ kết quả rồi đóng scope. Nếu một task vẫn còn chạy hoặc bị treo, `close()` có thể giữ hàm chưa trả về, dù virtual thread nhẹ hơn platform thread. Điều đó vừa có lợi (không bỏ quên task) vừa là rủi ro (chờ vô hạn) nếu deadline và cancellation chưa được thiết kế.

## 11. Trường hợp ScheduledExecutorService và task định kỳ

`ScheduledExecutorService` cũng là subtype của `ExecutorService`, nhưng task theo lịch đặt thêm câu hỏi: **task nào được phép tiếp tục sau shutdown?**

Với `ScheduledThreadPoolExecutor`, các chính sách như `setContinueExistingPeriodicTasksAfterShutdownPolicy(...)` và `setExecuteExistingDelayedTasksAfterShutdownPolicy(...)` ảnh hưởng task định kỳ/trì hoãn khi shutdown. Các giá trị mặc định được JDK quy định, nhưng khi cấu hình thay đổi, close có thể phải đợi các task còn được phép chạy theo chính sách.

**Không mặc định** rằng rời try-with-resources quanh một scheduler sẽ luôn hoàn tất tức thời. Nếu task delay còn rất lâu hoặc chính sách giữ task định kỳ sau shutdown, chương trình có thể chờ lâu/không kết thúc khi gọi close.

Khi xây dựng scheduler lâu dài, hãy quản lý vòng đời tại owner, hủy `ScheduledFuture` liên quan đúng lúc và xác định deadline shutdown rõ. Đừng đóng một scheduler dùng chung chỉ vì một phương thức đã đăng ký xong một task.

## 12. Exception trong try-with-resources và thứ tự đóng

Try-with-resources tự gọi `close()` khi rời khối **dù kết thúc bình thường, `return` hay exception**. Java đóng nhiều resource theo **thứ tự ngược với thứ tự khai báo**.

Ví dụ cấu trúc:

```java
// Snippet: openResource() trả một AutoCloseable khác.
try (AutoCloseable resource = openResource();
     ExecutorService executor = Executors.newFixedThreadPool(2)) {
    // Gửi task làm việc với resource.
}
// executor.close() chạy trước, sau đó resource.close().
```

Đây là **snippet**, vì `openResource()` chưa được định nghĩa. Việc để executor **được khai báo sau** resource trong trường hợp này làm nó được **close trước** resource, giúp các task còn chạy không truy cập một resource đã bị đóng sớm.

**Nếu đảo thứ tự resource**, resource có thể bị đóng trước khi executor hoàn tất task, gây lỗi sử dụng resource đã đóng. Vì vậy cấu trúc scope không chỉ là cú pháp rút gọn—**thứ tự đóng quyết định correctness** khi task phụ thuộc vào file, stream, connection hoặc resource khác.

Một exception do code trong `try` ném có thể chỉ được chuyển ra ngoài sau khi mọi resource đóng xong; exception từ quá trình đóng có quy tắc suppressed exceptions của Java. `ExecutorService.close()` không ném `InterruptedException` nhưng có thể gặp runtime exception như vấn đề quyền/ngữ cảnh triển khai. Khi quản lý nhiều resource, cần hiểu cả đường lỗi và việc đóng từng thành phần.

### Dùng resource variable đã tạo từ trước

Từ Java 9, try-with-resources có thể dùng một biến resource **final hoặc effectively final** đã được tạo trước đó:

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
try (executor) {
    executor.execute(() -> System.out.println("work"));
}
```

Đoạn này là snippet trong phương thức/class phù hợp và dùng được với **JDK 21+** vì `ExecutorService` lúc đó là `AutoCloseable`. Nếu resource không thuộc quyền sở hữu của phương thức hiện tại (pool dùng chung), **không** đóng nó bằng try-with-resources chỉ vì cú pháp cho phép.

## 13. Điều kiện chờ kết thúc và memory visibility

`close()` chờ kết thúc vòng đời executor, nhưng nếu cần sử dụng kết quả của một task theo hợp đồng chương trình, **`Future.get()` là kênh công bố kết quả rõ ràng**. Hợp đồng của `ExecutorService` mô tả quan hệ memory-consistency quan trọng:

- Các hành động của thread trước khi submit task **happens-before** các hành động tương ứng bên trong task.
- Các hành động trong task **happens-before** những hành động sau khi kết quả được nhận qua `Future.get()`.

Khi task cập nhật shared mutable state, không thể mặc định rằng “dùng ExecutorService rồi close” sẽ làm mọi thao tác cập nhật **nguyên tử** hoặc tự bảo vệ bất biến trong lúc các task đang chạy. Hai task cùng `counter++` trên trường thường vẫn có thể mất cập nhật.

**Thiết kế đúng:** dùng `Future` cho giá trị/exception; dùng `AtomicInteger`, `synchronized`, `Lock` hoặc immutable snapshots khi task cùng truy cập state; dùng `close/awaitTermination` để quản lý **vòng đời**. Đây là ba trách nhiệm có liên quan nhưng không thay thế lẫn nhau.

### isShutdown khác isTerminated

- Sau `shutdown()`, `isShutdown() == true` nhưng `isTerminated()` có thể vẫn false.
- Sau `close()` **trả về bình thường** với default implementation và executor thực sự terminated, `isTerminated() == true`.
- Không cần đo bằng vòng `while (!executor.isTerminated())` busy-spin để chờ pool hoàn tất; dùng `awaitTermination(...)` hoặc close theo hợp đồng thích hợp.

## 14. Những hiểu nhầm thường gặp

1. **“`close()` được thêm vào ExecutorService đúng từ Java 21.”** Chính xác hơn: có **từ Java 19**; thường được học/dùng trong dự án JDK 21+.
2. **“JDK 17 cũng biên dịch try (ExecutorService e = ...).”** Sai: `ExecutorService` JDK17 chưa là `AutoCloseable`.
3. **“`close()` chỉ gọi shutdown và trả về ngay.”** Sai: nó chờ executor terminated.
4. **“`shutdown()` đã đợi mọi task kết thúc.”** Sai: nó chỉ yêu cầu orderly shutdown.
5. **“`shutdownNow()` chắc chắn giết mọi task.”** Sai: chỉ best-effort; task không phản hồi interrupt có thể vẫn chạy.
6. **“`close()` có một timeout mặc định.”** Sai: không có deadline chờ cố định theo API.
7. **“Khi caller bị interrupt, `close()` lập tức ném `InterruptedException`.”** Sai: close cố stop kiểu shutdownNow, tiếp tục chờ và khôi phục cờ trước khi trả về.
8. **“`close()` khẳng định mọi task thành công.”** Sai: cần `Future.get()` để biết outcome.
9. **“Rời scope nghĩa là side effect trong task đều rollback khi có lỗi.”** Sai: executor không phải transaction manager.
10. **“Một pool dùng chung có thể đóng ở bất kỳ nơi nào gọi nó.”** Sai: phải có chủ sở hữu lifecycle rõ.
11. **“Virtual threads khiến close không thể treo.”** Sai: virtual thread có thể chờ vô hạn nếu không có điều kiện tiến triển.
12. **“Task chỉ cần signal ở dòng sau try thì close sẽ cho dòng sau chạy.”** Sai: close chờ task; có thể tạo chu trình chờ.
13. **“Scheduled executor sẽ luôn đóng ngay khi hết khối try.”** Sai: delay/periodic policy có thể khiến chờ đáng kể.
14. **“Một Future hoàn tất nói mọi task trong executor đã dừng.”** Sai: Future chỉ đại diện task tương ứng, còn close/awaitTermination nói về executor.
15. **“close là cách thay thế cho thiết kế graceful shutdown theo SLA.”** Sai: có thể cần shutdown và timed awaitTermination với fallback.

## 15. Checklist áp dụng ExecutorService.close và try-with-resources

1. **JDK đang chạy là phiên bản nào?** Nếu muốn `try (ExecutorService...)` phải có API `AutoCloseable` của ExecutorService, thực tế dùng JDK 21+ theo bài.
2. **Ai sở hữu executor?** Chỉ scope sở hữu được tự đóng; pool dùng chung cần owner có vòng đời dài hơn.
3. **Task có hữu hạn và có thể tiến triển không?** Tránh tác vụ tự chờ tín hiệu chỉ xảy ra sau khi close.
4. **Có cần lấy giá trị và exception không?** Dùng `Future.get()`/kênh kết quả, không trông chờ close báo lỗi task.
5. **Deadline shutdown có giới hạn không?** Nếu cần, dùng `shutdown(); awaitTermination(timeout)` rồi chọn hành động tiếp theo; close không có timeout.
6. **Task có hợp tác với interrupt không?** Cấu trúc shutdownNow khi bị interrupt không phải cơ chế kill mạnh.
7. **Có đang giữ tài nguyên worker cần không?** Không đóng executor khi giữ một lock hay resource tạo vòng chờ.
8. **Nếu có nhiều AutoCloseable, thứ tự đóng có đúng không?** Resource worker sử dụng thường cần tồn tại tới khi executor đã dừng.
9. **Có scheduled/periodic/delayed task không?** Xem chính sách sau shutdown và hủy future khi cần.
10. **Có shared mutable state không?** Đóng executor không chữa data race; đồng bộ các task riêng.
11. **Đã thử đường normal/exception/interrupt chưa?** Dùng latch để tạo lịch tái hiện, kiểm tra trạng thái cờ và cleanup.
12. **Có quan sát lifecycle khi vận hành không?** Theo dõi `isShutdown`, `isTerminated`, thời gian shutdown, task bị kẹt và thread dump/JFR nếu cần.

## 16. Tài liệu chuẩn để đối chiếu

- [JDK 21: ExecutorService API và close()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [JDK 21: Executors API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html)
- [JDK 21: ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)

**Tóm lại:** từ Java 19, `ExecutorService` có `close()` và trở thành `AutoCloseable`; trong các chương trình **Java 21+**, có thể dùng `try-with-resources` để gắn vòng đời executor với một scope. **Close chờ các task đã nhận và executor kết thúc**, không đơn thuần gọi shutdown rồi trả về. Nếu caller bị interrupt khi chờ, close chuyển sang yêu cầu dừng kiểu shutdownNow, **vẫn chờ task đang chạy kết thúc**, rồi khôi phục interrupt status. Hãy chọn close khi cần chờ đến cùng; chọn shutdown/awaitTermination có deadline khi hệ thống có giới hạn thời gian, và quản lý kết quả/lỗi qua Future.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-017">ScheduledThreadPoolExecutor.setRemoveOnCancelPolicy() và tác vụ bị hủy trong hàng đợi</a>

<details>
<summary>Xem chi tiết</summary>

# ScheduledThreadPoolExecutor.setRemoveOnCancelPolicy() và tác vụ bị hủy trong hàng đợi

Trong `java.util.concurrent.ScheduledThreadPoolExecutor`, một tác vụ được lên lịch **chưa đến hạn** có thể bị hủy bằng `ScheduledFuture.cancel(...)`. Hủy không có nghĩa task đang nằm trong **delayed queue** sẽ biến mất khỏi queue ngay: với chính sách mặc định, task bị hủy có thể **tiếp tục nằm trong queue cho đến khi tới thời điểm dự kiến chạy**. Nếu delay rất dài và ứng dụng hủy nhiều tác vụ, queue có thể giữ các task objects — cùng tham chiếu đến những object mà chúng mang theo — lâu hơn mong muốn.

Method **`setRemoveOnCancelPolicy(true)`** giải quyết đúng vấn đề đó: với tác vụ thuộc `ScheduledThreadPoolExecutor` còn nằm trong work queue, một lần hủy được chấp nhận sẽ khiến task **được loại khỏi queue tại thời điểm hủy**, thay vì chờ tới hạn chạy. Chính sách này giúp hạn chế **retention bộ nhớ và queue phình vì canceled tasks**, đặc biệt với timeout, retry, job định kỳ và scheduler tồn tại lâu.

Tuy nhiên, **remove khỏi queue không phải kill thread**, không phải rollback công việc đã bắt đầu, không tự đóng mọi resource, và không giải quyết queue tăng do **những task vẫn hợp lệ**. Bài này giải thích semantics, demo Java 17 chạy được, tác động với `scheduleAtFixedRate`/`scheduleWithFixedDelay`, quan hệ với `purge()` và shutdown, đo đạc và cách tránh hiểu nhầm trong Java concurrency nói chung.

## 1. ScheduledThreadPoolExecutor lưu tác vụ như thế nào?

`ScheduledThreadPoolExecutor` là một `ThreadPoolExecutor` chuyên xử lý các task cần chạy **sau một khoảng delay** hoặc **định kỳ**. Các API thường dùng:

- `schedule(Runnable, delay, unit)`: chạy một lần, **không sớm hơn** khoảng delay chỉ định.
- `schedule(Callable<V>, delay, unit)`: chạy một lần, trả kết quả qua `ScheduledFuture<V>`.
- `scheduleAtFixedRate(task, initialDelay, period, unit)`: cố gắng bắt đầu các lần thực thi theo chu kỳ cố định từ mốc lên lịch, nếu task không bị hủy/thất bại.
- `scheduleWithFixedDelay(task, initialDelay, delay, unit)`: chờ một khoảng delay sau khi **lần thực thi trước kết thúc** rồi mới chạy lần kế tiếp.
- `execute(...)` và `submit(...)` cũng được executor này thực hiện dưới dạng task có **zero delay**, với semantics queue/scheduling tương ứng.

Các task đang đợi được quản lý bằng một dạng **delay queue**, có thể chứa task chưa đến hạn. Chỉ task đủ điều kiện về thời gian mới được lấy ra để worker chạy; `getQueue().size()` vì thế có thể bao gồm những task còn đợi **nhiều giờ hoặc nhiều ngày**.

### Ví dụ hình dung

```text
Time now: 10:00

Task A: run at 10:01  --> delayed queue
Task B: run at 12:00  --> delayed queue
Task C: run tomorrow  --> delayed queue

Caller cancels Task C at 10:00:05
    |
    +-- default remove-on-cancel=false
    |      Task C may remain queued until scheduled time
    |
    +-- remove-on-cancel=true
           Task C is removed from queue promptly on cancellation
```

**Queue chứa object biểu diễn task**, không phải bản sao toàn bộ dữ liệu bên trong. Nhưng object task có thể giữ `Runnable`/`Callable` và các reference liên quan, khiến dữ liệu reachable lâu hơn. `cancel` vẫn ngăn **một task chưa chạy** thực thi sau đó theo contract; vấn đề cần sửa ở đây là **lưu giữ task đã cancel**, không phải “task đã cancel chắc chắn vẫn chạy”.

## 2. Chính sách mặc định và bật remove-on-cancel

API của `ScheduledThreadPoolExecutor`:

```java
ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(2);

executor.setRemoveOnCancelPolicy(true);
boolean enabled = executor.getRemoveOnCancelPolicy();
```

Đây là snippet, cần import `java.util.concurrent.ScheduledThreadPoolExecutor`. Với implementation JDK thông thường, mặc định của `getRemoveOnCancelPolicy()` là **`false`**. Khi bật:

- Các scheduled tasks **bị hủy khi còn trong queue** được gỡ khỏi queue ngay lúc hủy theo hợp đồng chính sách.
- Task đang thực thi trên worker **không thể được gỡ khỏi queue** vì nó đã được lấy ra để chạy. `cancel(true)` có thể **yêu cầu interrupt** nếu phù hợp; có dừng hay không tùy tác vụ.
- Tác vụ đã hoàn thành hoặc đã tự ra khỏi queue không có gì để remove khỏi queue.
- Setting **không tự dọn những task đã bị hủy trước khi setting được bật**; với số task cũ vẫn nằm queue, cân nhắc `purge()`.
- Setting **không** biến `ScheduledThreadPoolExecutor` thành executor có queue bounded, không làm queue các **task chưa bị hủy** tự nhỏ đi.
- Việc gỡ task cho queue giải phóng **một đường reference từ executor**, không bảo đảm object được GC ngay nếu caller, registry, callback hoặc thread khác vẫn giữ reference.

Trong phần lớn ứng dụng **thường xuyên hủy tasks có delay dài**, bật `setRemoveOnCancelPolicy(true)` ngay sau khi tạo executor là lựa chọn thực dụng. Nhưng nó có tradeoff thao tác queue tại thời điểm cancel; nếu workload đặc biệt, đo chi phí cancel/scheduling và memory.

## 3. Ba trạng thái cần phân biệt: hủy, chờ queue, đang chạy

| Khái niệm | Nó trả lời câu hỏi nào? | Không thể kết luận |
| --- | --- | --- |
| `future.isCancelled()` | Future đã nhận trạng thái canceled chưa? | Worker chắc chắn đã dừng, cleanup đã xong |
| `future.isDone()` | Future đã ở trạng thái done (thành công, thất bại, canceled) chưa? | Object chắc chắn đã rời queue |
| `executor.getQueue().contains(task)` hoặc queue size | Task có còn trong work queue tại snapshot đó? | Task còn được phép chạy, hay đang chạy |
| `executor.getActiveCount()` | Số workers đang active theo thống kê gần đúng | Có bao nhiêu canceled tasks còn lưu trong queue |
| `executor.getRemoveOnCancelPolicy()` | Có yêu cầu remove scheduled task khi cancel không? | Các task cũ đã cancel trước đó tự biến mất |
| `executor.isTerminated()` | Executor đã kết thúc hẳn chưa? | Mọi external resource đều tự đóng đúng cách |

**Một task có thể vừa cancelled vừa queued** nếu remove-on-cancel chưa bật. Ngược lại, một task đã bị worker lấy ra thì **không queued**, dù đang chạy hoặc bị yêu cầu hủy.

Không sử dụng `isCancelled()` làm bằng chứng mọi side effect đã dừng. Với `FutureTask`/executor, cancellation có thể được ghi nhận trước khi worker thực sự hoàn tất `finally`. Cần handshake riêng hoặc chờ executor termination theo hợp đồng nếu cần chứng minh cleanup.

## 4. Java 17: so sánh default với `setRemoveOnCancelPolicy(true)`

Thí nghiệm này **không dùng `Thread.sleep` để trông đợi task chưa chạy**. Mỗi task được đặt delay một ngày; ngay sau khi submit, ta hủy trước khi đến hạn. Dùng một executor mới cho từng trường hợp và đọc queue **trước khi shutdown**, vì shutdown là một thao tác lifecycle khác có thể thay đổi queue.

Tạo **`RemoveOnCancelDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RemoveOnCancelDemo {
    static void runCase(boolean removeOnCancel) {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(1);
        pool.setRemoveOnCancelPolicy(removeOnCancel);

        try {
            List<ScheduledFuture<?>> futures = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                futures.add(pool.schedule(
                        () -> System.out.println("unexpected"),
                        1, TimeUnit.DAYS));
            }

            System.out.println("policy=" + removeOnCancel);
            System.out.println("beforeCancel="
                    + pool.getQueue().size());

            for (ScheduledFuture<?> f : futures) {
                if (!f.cancel(false)) {
                    throw new IllegalStateException("Cancel failed");
                }
            }

            System.out.println("afterCancel="
                    + pool.getQueue().size());
            System.out.println("allCancelled="
                    + futures.stream().allMatch(
                            ScheduledFuture::isCancelled));
        } finally {
            pool.shutdownNow();
        }
    }

    public static void main(String[] args) {
        runCase(false);
        runCase(true);
    }
}
```

Biên dịch/chạy:

```bash
javac RemoveOnCancelDemo.java
java RemoveOnCancelDemo
```

**Stdout khi chương trình hoàn thành bình thường:**

```text
policy=false
beforeCancel=3
afterCancel=3
allCancelled=true
policy=true
beforeCancel=3
afterCancel=0
allCancelled=true
```

### Phân tích output

- Cả hai pool nhận ba task, delay một ngày, nên trước khi cancel queue chứa **3 task**.
- Với policy false, `cancel(false)` chuyển Future sang canceled nhưng **những task chưa tới hạn vẫn ở queue** trên đường chạy bình thường, do đó `afterCancel=3`.
- Với policy true, các task bị canceled sẽ **được remove khỏi queue**, nên `afterCancel=0`.
- `allCancelled=true` ở cả hai trường hợp: **cancellation state không đồng nghĩa queue membership**.
- Dòng `unexpected` không được in vì các tác vụ bị hủy trước khi tới hạn một ngày.
- `shutdownNow()` ở `finally` là cleanup executor; ví dụ **không đọc queue sau shutdown** để tránh trộn tác động của shutdown vào đo remove-on-cancel.

Không dùng mẫu này để benchmark tốc độ; delay một ngày chỉ giúp chọn đường thực thi chắc chắn còn queued trong khoảng test bình thường. Trong production, nếu một task rất gần thời điểm chạy, nó có thể bị worker lấy ra **trước khi bạn cancel**. Khi đó số queued thực tế khác vì race, không phải API remove-on-cancel sai.

## 5. Vì sao task canceled còn trong queue có thể làm tăng memory retention?

Giả sử task scheduled sau 24 giờ đóng gói một payload:

```java
byte[] payload = new byte[10_000_000];

ScheduledFuture<?> handle = pool.schedule(() -> {
    process(payload);
}, 24, TimeUnit.HOURS);
```

Đây là **snippet**, `pool` là `ScheduledThreadPoolExecutor`, `process` là method nghiệp vụ. Lambda giữ reference tới payload; executor giữ scheduled task; scheduled task có thể giữ lambda và theo đó giữ payload. Nếu caller gọi:

```java
handle.cancel(false);
```

mà policy false, canceled task **có thể còn nằm trong delayed queue**, khiến một đường reference từ executor vẫn tồn tại tới khi task được gỡ. Với rất nhiều task bị cancel, chi phí reachable objects có thể đáng kể, đặc biệt khi delay dài.

**Tuy nhiên, không được tuyên bố một canceled task luôn giữ trọn payload tới deadline ở mọi triển khai/JDK.** Một Future implementation có thể xóa hoặc thay đổi field references khi cancel; code khác cũng có thể giữ hoặc bỏ references. Điểm chắc chắn về policy là **queue retention đối với canceled task**, còn lượng heap chính xác phải đo bằng heap dump/profiler theo JDK thực tế.

### Không nhầm removal với Garbage Collection

Sau khi `setRemoveOnCancelPolicy(true)`, một task bị gỡ khỏi executor queue:

- Caller vẫn giữ `ScheduledFuture<?>` trong map/list? Object vẫn có thể reachable.
- Một registry giữ lambda hoặc state nghiệp vụ? Dữ liệu vẫn có đường reference khác.
- Task đã chạy và có thread giữ reference? Queue removal không ảnh hưởng phần đã chạy.
- GC chưa diễn ra? Heap usage có thể chưa giảm ngay dù object đã unreachable.

Vì vậy hãy **xem `getQueue().size()` và heap retention như hai tín hiệu khác nhau**. Chính sách đúng chỉ loại bỏ **một nguồn lưu giữ có thể tránh được**.

## 6. `purge()`: dọn task cũ đã canceled mà còn queued

`ThreadPoolExecutor.purge()` duyệt work queue và loại bỏ các `Future` đã bị canceled. Nó tồn tại để hỗ trợ dọn canceled tasks còn chờ khi không bật remove-on-cancel hoặc khi có task cũ đã cancel trước khi policy đổi.

```java
pool.purge();
```

Đây là snippet; `pool` là `ScheduledThreadPoolExecutor`. `purge()` **không cancel tasks mới**, không kill running threads và không phải lời hứa heap được GC. Vì phải xét các phần tử queue, đừng gọi `purge()` liên tục với tần suất cao trong hot path của scheduler rất bận, nếu không đo chi phí.

### Set policy sau khi đã cancel có dọn queue cũ ngay không?

**Không.** Setter thay đổi **hành vi của những lần cancel xảy ra sau khi bật**, không phải một lệnh retroactive scan xóa tất cả task đã canceled.

Ví dụ Java 17 minh họa:

Tạo **`PurgeAfterToggleDemo.java`**:

```java
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PurgeAfterToggleDemo {
    public static void main(String[] args) {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(1);

        try {
            ScheduledFuture<?> old1 =
                    pool.schedule(() -> {}, 1, TimeUnit.DAYS);
            ScheduledFuture<?> old2 =
                    pool.schedule(() -> {}, 1, TimeUnit.DAYS);

            old1.cancel(false);
            old2.cancel(false);
            System.out.println("cancelledBeforeToggle="
                    + pool.getQueue().size());

            pool.setRemoveOnCancelPolicy(true);
            System.out.println("afterToggle="
                    + pool.getQueue().size());

            pool.purge();
            System.out.println("afterPurge="
                    + pool.getQueue().size());

            ScheduledFuture<?> fresh =
                    pool.schedule(() -> {}, 1, TimeUnit.DAYS);
            fresh.cancel(false);
            System.out.println("newCancelAfterToggle="
                    + pool.getQueue().size());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Chạy:

```bash
javac PurgeAfterToggleDemo.java
java PurgeAfterToggleDemo
```

Stdout khi hoàn thành bình thường:

```text
cancelledBeforeToggle=2
afterToggle=2
afterPurge=0
newCancelAfterToggle=0
```

Đây là cách so sánh rõ:

1. Hai task được cancel khi policy mặc định false, nên còn queued.
2. `setRemoveOnCancelPolicy(true)` **không dọn retroactively**.
3. `purge()` gỡ hai `Future` đã canceled.
4. Task mới được cancel **sau khi** bật policy thì được gỡ ngay.

Trong hệ thống lâu sống đã tích lũy nhiều canceled tasks, có thể bật policy mới **và purge một lần** để dọn backlog cũ, miễn là phù hợp phạm vi sử dụng queue và đã đo chi phí.

## 7. Tác vụ một lần so với tác vụ định kỳ

`ScheduledThreadPoolExecutor` xử lý cả **one-shot** và **periodic** scheduled tasks. Cả hai có thể có một task object đang nằm trong queue để chờ một lần chạy tiếp theo. Chính sách remove-on-cancel có ý nghĩa với task **đang queued** khi cancel.

| Kiểu lên lịch | Lần đầu chạy | Các lần tiếp theo | Cancellation |
| --- | --- | --- | --- |
| `schedule(...)` | Sau `delay` | Không có | Nếu chưa chạy và canceled, có thể remove khỏi queue |
| `scheduleAtFixedRate(...)` | Sau `initialDelay` | Theo mốc chu kỳ cố định, không overlap các lần cùng một periodic task | Canceled future sẽ không còn các lần chạy tiếp theo theo contract |
| `scheduleWithFixedDelay(...)` | Sau `initialDelay` | Đợi `delay` sau khi lần chạy trước kết thúc | Canceled future sẽ không còn các lần chạy tiếp theo theo contract |

Với periodic task, **không có nhiều bản sao xếp hàng tương ứng với mỗi chu kỳ tương lai**; sau một lần chạy, nếu còn hợp lệ, nó có thể được đặt lại vào queue cho lần tiếp theo. Nếu task chạy lâu hơn period, `scheduleAtFixedRate` có thể **bị trễ**; các lần thực thi của **cùng một periodic task không overlap** trong `ScheduledThreadPoolExecutor`.

### Java 17: hủy periodic tasks trước lần đầu

Tạo **`PeriodicCancelDemo.java`**:

```java
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PeriodicCancelDemo {
    public static void main(String[] args) {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(1);
        pool.setRemoveOnCancelPolicy(true);

        try {
            ScheduledFuture<?> atRate =
                    pool.scheduleAtFixedRate(
                            () -> System.out.println("unexpected"),
                            1, 1, TimeUnit.DAYS);

            ScheduledFuture<?> withDelay =
                    pool.scheduleWithFixedDelay(
                            () -> System.out.println("unexpected"),
                            1, 1, TimeUnit.DAYS);

            System.out.println("beforeCancel="
                    + pool.getQueue().size());

            atRate.cancel(false);
            withDelay.cancel(false);

            System.out.println("afterCancel="
                    + pool.getQueue().size());
            System.out.println("bothCancelled="
                    + (atRate.isCancelled()
                    && withDelay.isCancelled()));
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac PeriodicCancelDemo.java
java PeriodicCancelDemo
```

Stdout bình thường:

```text
beforeCancel=2
afterCancel=0
bothCancelled=true
```

Ở đây **initialDelay một ngày** bảo đảm task vẫn đang chờ trong bài kiểm tra bình thường. Khi cancel trước lần đầu, queue gỡ cả fixed-rate và fixed-delay tasks. Không dùng kết quả này để suy ra `cancel(true)` có thể ngắt ngay một periodic task **đã bắt đầu** làm việc; khả năng dừng vẫn phụ thuộc interrupt/cancellation và API đang blocking.

### Fixed-rate, fixed-delay có liên quan gì tới retention?

Trong ứng dụng có hàng nghìn periodic timers hoặc retry jobs được tạo rồi cancel trước lần chạy tiếp theo, một canceled scheduled future có thể bị giữ trong queue nếu không remove-on-cancel. Bật policy giảm backlog canceled tasks. Nó **không giảm số periodic jobs còn hợp lệ**, cũng không giảm chi phí việc chạy định kỳ của các jobs chưa cancel.

## 8. `cancel(false)` và `cancel(true)` khác nhau ra sao?

`Future.cancel(mayInterruptIfRunning)` có hai cách yêu cầu hủy:

- `cancel(false)`: nếu task **chưa chạy**, yêu cầu không chạy nó; nếu task đang chạy, không gửi interrupt tới worker qua tham số này.
- `cancel(true)`: nếu task đang chạy và implementation hỗ trợ, có thể yêu cầu interrupt thread thực thi; không có cam kết thread sẽ dừng ngay, và code task cần phản hồi interruption.
- Cả hai có thể đặt Future vào trạng thái canceled khi cancellation được chấp nhận. **Remove-on-cancel policy xử lý queue membership**, không phải quyết định API interrupt đã dừng worker hay chưa.

Ví dụ:

```java
ScheduledFuture<?> future =
        pool.schedule(() -> doWork(), 10, TimeUnit.MINUTES);

boolean accepted = future.cancel(false);
```

Đây là snippet, `pool` là `ScheduledThreadPoolExecutor` và `doWork()` là logic. Nếu chưa tới hạn và cancel thành công, task không còn được phép chạy theo contract. Với remove-on-cancel true, nó cũng được remove khỏi queue. Với policy false, future có thể canceled nhưng vẫn nằm queue tới hạn.

### Điều gì khi task đã bắt đầu?

Nếu worker đã lấy task ra khỏi delay queue, **không còn queued task để remove**. `cancel(true)` gửi yêu cầu interrupt nếu thích hợp; worker phải thoát bằng chính logic của nó. Một task xử lý interrupt đúng sẽ có cơ hội chạy `finally`/`try-with-resources`. Nhưng `future.isCancelled()` chưa chắc là bằng chứng cleanup đã hoàn tất.

**Không dùng `Thread.stop()` để “giúp remove-on-cancel làm việc”.** Cưỡng chế dừng thread có thể làm hỏng invariant. Nếu resource đang chặn I/O không phản hồi interrupt, cần timeout/cancel theo API resource cụ thể.

## 9. `getQueue().size()` không phải số business jobs còn cần chạy

Nếu một pool mặc định có 1.000 canceled delayed futures vẫn chờ trong queue, `getQueue().size()` có thể là 1.000 dù không còn một job nào thật sự cần thực thi. Ngược lại, `getQueue().size()==0` không có nghĩa executor idle: có thể có workers **đang chạy** các tasks đã được lấy khỏi queue.

| Metric | Đo gì? | Không đủ để suy ra |
| --- | --- | --- |
| `pool.getQueue().size()` | Số phần tử queued tại snapshot | Bao nhiêu job còn hợp lệ, bao nhiêu worker active |
| `pool.getActiveCount()` | Ước lượng worker đang thực thi task | Số canceled tasks còn queued |
| `pool.getCompletedTaskCount()` | Ước lượng số tasks pool ghi nhận hoàn tất | Số business requests hoàn tất |
| `future.isCancelled()` | Trạng thái Future | Đã gỡ khỏi queue hoặc cleanup xong |
| `pool.getRemoveOnCancelPolicy()` | Chính sách remove khi cancel | Không có task cũ bị giữ |
| `pool.getQueue().remainingCapacity()` | Với delayed queue của scheduled executor, không phải giới hạn backlog hữu dụng | Scheduler được bounded theo request load |

**Delayed queue của `ScheduledThreadPoolExecutor` về mặt thiết kế là unbounded.** `maximumPoolSize` hầu như không có vai trò tăng số worker hữu ích như một fixed/bounded ThreadPoolExecutor thông thường; core pool size là điều kiện quan trọng hơn. Không cố “giới hạn queue” bằng `maximumPoolSize`, và đừng nhầm `setRemoveOnCancelPolicy` với backpressure.

### Quan sát production

- Số schedule calls/giây.
- Số cancels/giây và tỷ lệ canceled trước khi đến hạn.
- Queue depth theo thời gian; nếu có thể, tách pending valid vs canceled.
- Heap retained size, GC pause, Old Gen growth qua JFR/heap dump.
- Delays dài hay ngắn, burst cancellations, số periodic tasks.
- Số workers đang active, task runtime, scheduling lateness.
- Tốc độ tạo task hợp lệ có vượt năng lực thực thi không.
- Downstream resource usage nếu task chạy API/DB.

**Không gọi `getQueue().size()` là “leak count” một cách tuyệt đối.** Queue có thể chứa valid future tasks. Hãy xác minh `isCancelled()` của phần tử, đo nguồn reference và dùng profiling phù hợp.

## 10. `setRemoveOnCancelPolicy(true)` không phải giải pháp cho mọi memory pressure

Một số nguyên nhân queue/memory tăng **không** được giải quyết chỉ bằng remove-on-cancel:

1. **Task chưa canceled:** tất nhiên còn queue đến khi được thực thi hoặc hủy.
2. **Tasks được schedule quá nhanh:** khối lượng accepted work vẫn tăng ngay cả khi không có canceled tasks.
3. **Future references nằm ở registry/map khác:** queue removal không xóa bản giữ ở nơi khác.
4. **Tasks đã bắt đầu và vẫn blocking:** chúng không còn queued; phải xét task runtime/interrupt/timeout.
5. **Recurring jobs còn active:** chúng tiếp tục schedule lần kế tiếp; chỉ cancel nếu không còn cần chạy.
6. **Task captures lớn:** lambda/closure giữ object lớn dù không cần; cần giới hạn capture.
7. **Periodic task không có giới hạn lifetime:** nếu không cancel và vẫn hợp lệ, nó còn tồn tại lâu theo thiết kế.
8. **Shutdown không đúng:** scheduler còn sống và chấp nhận/giữ jobs; phải quản lý lifecycle.
9. **Caller cứ giữ tất cả ScheduledFuture trong list:** retention còn do application state, không chỉ queue.
10. **Cancellation xảy ra quá trễ:** task đã chạy và tạo các tài nguyên phụ; remove-on-cancel không rollback side effects.

Cần phân biệt **memory leak** (tham chiếu giữ object không còn cần) với **memory retention do cache/queue còn chủ động giữ work**. Hai hiện tượng có thể giống nhau trong heap dump nhưng cần cách xử lý khác.

## 11. Quan hệ với `shutdown()`, `shutdownNow()` và chính sách shutdown

`ScheduledThreadPoolExecutor` còn có các settings khác, **độc lập** với remove-on-cancel:

| Policy | Default phổ biến | Ý nghĩa |
| --- | --- | --- |
| `setRemoveOnCancelPolicy(boolean)` | `false` | Có gỡ canceled scheduled tasks khỏi queue ngay lúc cancel không |
| `setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean)` | `true` | Sau `shutdown()` có tiếp tục chạy one-shot delayed tasks đã lên lịch trước đó không |
| `setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean)` | `false` | Sau `shutdown()` có tiếp tục periodic tasks đã schedule trước đó không |

**Những câu hỏi khác nhau:**

- Cancel một task đang queued → **remove-on-cancel**.
- Executor được shutdown nhẹ nhàng nhưng task delayed chưa tới hạn → **execute-existing-delayed-after-shutdown**.
- Executor shutdown trong khi periodic job đã được đăng ký → **continue-existing-periodic-after-shutdown**.
- Đề nghị dừng tasks ngay lập tức và không nhận thêm → `shutdownNow()`, nhưng chỉ **cố** interrupt những task đang chạy và trả về task chưa được thực thi.

**Lỗi thường gặp:** gọi `shutdown()` rồi mong executor terminate ngay khi nó còn một delayed job sẽ chạy vào ngày mai. Khi policy execute-existing-delayed mặc định true, scheduler vẫn có thể giữ job cần thực thi, nên `awaitTermination` chưa đạt. Nếu muốn shutdown bỏ các delayed jobs, hãy cấu hình đúng lifecycle policy hoặc cancel chúng theo ownership. `setRemoveOnCancelPolicy(true)` tự nó **không có nghĩa bỏ toàn bộ delayed valid tasks sau shutdown**.

### Mẫu lifecycle có ý thức

```java
ScheduledThreadPoolExecutor scheduler =
        new ScheduledThreadPoolExecutor(2);

scheduler.setRemoveOnCancelPolicy(true);
scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
```

Đây là snippet khởi tạo, không phải một chính sách bắt buộc cho mọi ứng dụng. Có những business tasks delayed bắt buộc được thực thi trước khi shutdown hoàn tất, nên việc đặt `setExecuteExistingDelayedTasksAfterShutdownPolicy(false)` có thể làm mất công việc cần làm. **Chọn policy từ contract**, không copy nguyên snippet vào mọi service.

Phương án graceful shutdown:

```java
scheduler.shutdown();
try {
    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            reportStillRunning();
        }
    }
} catch (InterruptedException e) {
    scheduler.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Snippet cần `TimeUnit` và method `reportStillRunning()` của ứng dụng. `shutdownNow()` không bảo đảm mọi task dừng hoặc mọi resource đã đóng; phải báo nếu timeout.

## 12. Tác vụ periodic, lỗi và cancellation

Với một periodic task:

- Nếu body ném exception không được xử lý theo hợp đồng của `scheduleAtFixedRate`/`scheduleWithFixedDelay`, các lần thực thi tiếp theo bị ngăn.
- Nếu một lần thực thi kéo dài hơn period của fixed-rate, những lần kế tiếp có thể chạy trễ nhưng **không overlap với lần trước của cùng một periodic task**.
- Nếu task bị cancel trong khi đang chạy, future có thể chuyển canceled trước khi body thực sự rời worker; `mayInterruptIfRunning` quyết định yêu cầu interrupt.
- Khi periodic task đã hoàn thành một lần và requeue để chờ lần sau, remove-on-cancel true giúp gỡ nó nếu bị cancel **khi đang queued**.
- Nếu cancel và run race sát nhau, `getQueue().size()` là snapshot, không phải lịch sử chắc chắn. Viết test với delay đủ dài hoặc checkpoint rõ.

Không biến một periodic task thành hàng nghìn queued instances chỉ vì nó có period 1 giây trong tương lai: scheduler không tạo trước một bản sao cho mọi tick. Vấn đề retention ở đây là **nhiều futures bị canceled còn queued**, không phải “một job periodic 100 lần tương đương 100 queue nodes”.

## 13. Tránh nhầm cancellation với quét queue thủ công

Một số code sai dùng vòng lặp tự xóa mọi task mà application cho rằng không còn cần:

```java
for (Runnable task : scheduler.getQueue()) {
    if (shouldCancel(task)) {
        scheduler.getQueue().remove(task);
    }
}
```

Đây là **snippet sai về giao thức**. Xóa queue node không tương đương gọi `ScheduledFuture.cancel`. Bạn có thể xóa một task khỏi scheduler nhưng `Future` của caller vẫn không chuyển trạng thái canceled, khiến caller chờ `get()` mãi hoặc đánh giá sai lifecycle. Còn có race với worker và với periodic requeue.

**Hãy cancel qua đúng `ScheduledFuture` handle**, rồi dùng remove-on-cancel policy hoặc `purge()` để xử lý queue retention. Nếu cần owner registry, giữ handle của job và dọn registry khi job kết thúc/hủy, không dùng queue nội bộ như kho dữ liệu nghiệp vụ.

### Cần tự gọi `getQueue().remove(future)` không?

Thường **không cần** khi đã bật remove-on-cancel true. Hơn nữa, các implementation có thể decorate scheduled tasks, khiến type/identity của queued object khác điều bạn dự đoán. Dựa vào public API `cancel` và `setRemoveOnCancelPolicy`, thay vì viết code phụ thuộc nội bộ queue.

## 14. Giữ references và ownership của ScheduledFuture

Khi một request/business job schedule timeout:

```java
ScheduledFuture<?> timeoutTask =
        scheduler.schedule(() -> onTimeout(), 30, TimeUnit.SECONDS);
```

Đây là snippet với `scheduler` đã định nghĩa và callback `onTimeout` thuộc ứng dụng. Khi request kết thúc bình thường trước timeout, phải:

1. Hủy timeout task nếu không cần callback nữa: `timeoutTask.cancel(false)`.
2. Đảm bảo scheduler có policy phù hợp nếu nhiều timeout tương tự bị hủy.
3. Xóa handle khỏi registry/map của ứng dụng nếu registry không còn cần nó.
4. Đảm bảo callback timeout và đường completed bình thường có **giao thức quyết định một lần** (ví dụ CAS state machine) nếu chúng race.
5. Không suy ra `cancel(false)` đã rollback callback nếu callback **đã bắt đầu chạy**.

Một phép cancel thành công không thể giải quyết tình huống callback đã tạo side effect. Để hạn chế double-completion, dùng state như `AtomicReference<State>` hoặc một khóa chung để chuyển `PENDING → TIMED_OUT` hoặc `PENDING → COMPLETED` chỉ một lần, rồi xử lý effect phù hợp. Policy queue chỉ giải quyết **retention**, không quyết định ai thắng business race.

## 15. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| “`cancel` làm task biến mất khỏi queue ngay” | Default có thể giữ canceled task tới delay | Bật `setRemoveOnCancelPolicy(true)` khi cần |
| “Task canceled còn queued nghĩa là vẫn sẽ chạy” | Nhầm queue retention với execution eligibility | Phân biệt Future state và queue membership |
| “`setRemoveOnCancelPolicy(true)` giết running thread” | Task đã chạy không còn queued | Cooperative interrupt/timeout riêng |
| “Bật policy sau cancel sẽ dọn task cũ” | Canceled objects cũ vẫn có thể queued | `purge()` một lần khi phù hợp |
| “`purge()` hủy tác vụ” | Nó chỉ loại canceled futures khỏi queue | Gọi `cancel` trước |
| “remove khỏi queue chắc chắn heap giảm ngay” | Registry/caller/GC vẫn có thể giữ | Xem heap retainers và references |
| “`queue.size()` là số jobs còn cần chạy” | Gồm delayed và canceled tasks | Đo pending valid, canceled, running riêng |
| “`maximumPoolSize` sẽ giới hạn delayed queue” | Queue scheduled executor về cơ bản unbounded | Admission/bounded registry ở tầng trên |
| “`shutdown()` luôn dừng tất cả delayed tasks” | Delayed-after-shutdown default true | Chọn đúng lifecycle policies |
| “`shutdownNow()` bảo đảm worker đã dừng” | Chỉ cố interrupt, có thể không đáp ứng | Đợi termination và cleanup |
| “Periodic schedule sẵn hàng trăm lần tương lai” | Không hiểu requeue định kỳ | Một future được lên lịch lại |
| “`cancel(true)` đã đóng resource” | Future state không đồng nghĩa cleanup | Theo dõi worker completion riêng |
| “Dùng `getQueue().remove` thay cancel” | Future lifecycle có thể sai | Hủy bằng ScheduledFuture handle |
| “Task không cancel thì remove-on-cancel vẫn giúp queue nhỏ” | Policy chỉ tác động canceled tasks | Backpressure, giới hạn số schedules |
| “`execute/submit` không liên quan scheduler” | STPE cũng đóng gói zero-delay tasks | Hiểu queue và wrapper semantics |

## 16. Kiểm thử chính sách đúng cách

Một test tốt nên bao phủ:

1. **Default false**: schedule với delay rất dài, cancel, quan sát queued canceled future.
2. **Enabled true**: cùng workload, cancel, quan sát queue node được gỡ.
3. **Toggle sau khi cancel**: bật true không tự dọn backlog cũ; `purge()` dọn.
4. **Periodic before-first-run**: fixed-rate và fixed-delay canceled futures được gỡ.
5. **Cancel khi task đang chạy**: không nhầm removal với interrupt/termination; dùng latch để điều phối.
6. **Shutdown policy**: kiểm tra one-shot delayed và periodic có tiếp tục sau `shutdown` hay không.
7. **Queue valid tasks**: remove-on-cancel không làm queue các task chưa cancel biến mất.
8. **Memory retention**: đo queue và heap dump/profile, không assert GC chạy trong một mốc thời gian.
9. **Concurrent scheduling/cancellation**: không dựa vào queue snapshot không ổn định để khẳng định race outcome.
10. **Cleanup**: đóng scheduler ở `finally`, dừng tasks theo policy thay vì để thread pool giữ JVM sống ngoài ý muốn.

**Không dùng `Thread.sleep(10)` để đoán task chưa chạy hoặc đã được gỡ.** Ví dụ trong bài chọn delay một ngày và đọc ngay sau cancel, tránh race scheduling ở điểm cần minh họa; test concurrency phức tạp hơn nên dùng `CountDownLatch` hoặc cơ chế điều phối khác.

## 17. Khi nào thực sự nên bật chính sách?

**Rất phù hợp** nếu:

- Scheduler chạy lâu và tạo nhiều **one-shot timeout tasks** mà phần lớn sẽ được hủy khi việc hoàn thành sớm.
- Retry/backoff jobs có delay dài và được cancel khi workflow kết thúc.
- Nhiều periodic tasks được đăng ký rồi dừng trước lần chạy kế tiếp.
- Queue depth và heap retained objects do canceled futures tăng theo thời gian.
- Bạn muốn giảm nhu cầu gọi `purge()` định kỳ chỉ để dọn canceled tasks.

**Không phải phương thuốc chính** nếu:

- Queue tăng vì tasks hợp lệ được schedule nhanh hơn executor xử lý.
- Worker đang chặn I/O, không giải phóng tài nguyên.
- Registry vẫn giữ mọi future đã canceled.
- Task payload quá lớn dù không cancel.
- Chính sách shutdown chưa phù hợp.
- Job phải survive process crash và cần persistent scheduling; `ScheduledThreadPoolExecutor` là scheduler **trong bộ nhớ**, không tự cung cấp durability.

Về hiệu năng, bật remove-on-cancel có chi phí quản lý queue tại thời điểm cancel. Nhưng tradeoff thường đáng cân nhắc khi canceled tasks có delay dài, vì giữ nhiều task sống sẽ tốn heap và làm các hoạt động quản lý queue khó chịu hơn. Cần đo thực tế nếu lượng cancels cực lớn.

## 18. Java 21+ và virtual threads

Virtual threads không thay đổi cơ chế cơ bản của `ScheduledThreadPoolExecutor`: scheduled futures vẫn được quản lý theo queue và cancellation policy của executor. Nếu callback tạo virtual threads hoặc task khác, cần tách **queue của scheduler** với **số công việc đang chạy ở executor khác**.

`newVirtualThreadPerTaskExecutor()` có trên Java 21+ nhưng không thay thế scheduler delayed/periodic chuẩn theo nghĩa cung cấp một `ScheduledExecutorService` với queue/cancel policy giống hệt. Chọn đúng API theo loại tác vụ.

Dù dùng virtual threads, **canceled delayed tasks** vẫn có thể tạo retention nếu còn queued, trong khi memory/threads cho callback còn thuộc hệ thống khác. Mọi demo trong bài chỉ dùng API chạy được bằng Java 17.

## 19. Câu hỏi tự kiểm tra

**1. Mặc định `getRemoveOnCancelPolicy()` là gì?** Thông thường `false` đối với `ScheduledThreadPoolExecutor`. Điều này cho phép canceled scheduled futures còn nằm delayed queue tới thời điểm dự kiến chạy.

**2. Vì sao canceled task còn queued không có nghĩa nó sẽ thực thi?** Trạng thái canceled của `Future` là một chuyện; giữ object trong queue để quản lý deadline/cleanup queue là chuyện khác.

**3. Khi bật `setRemoveOnCancelPolicy(true)`, điều gì thay đổi?** Task bị cancel khi vẫn queued sẽ được gỡ ngay khi cancellation diễn ra, giảm queue retention.

**4. Tác vụ đã chạy được `cancel(true)` có lập tức dừng do policy này không?** Không. Remove-on-cancel chỉ ảnh hưởng queued task; running code cần cooperative interruption/timeout.

**5. Bật policy true sau khi đã cancel 1.000 jobs có tự dọn 1.000 jobs đó?** Không retroactively. Có thể dùng `purge()` để gỡ các futures đã canceled còn queue.

**6. `purge()` có gọi cancel cho valid tasks không?** Không. Nó tìm và loại canceled futures khỏi queue.

**7. `getQueue().size()==0` chứng minh không có task đang chạy không?** Không. Worker có thể đã lấy task ra khỏi queue và vẫn đang chạy.

**8. `getQueue().size()==1.000` chứng minh có 1.000 jobs hợp lệ không?** Không. Queue có thể chứa canceled delayed futures, nhất là khi policy false.

**9. `setRemoveOnCancelPolicy(true)` có làm queue bounded không?** Không. Delayed queue của scheduled executor về cơ bản unbounded; cần admission control để giới hạn valid pending jobs.

**10. `shutdown()` có bỏ mọi delayed one-shot tasks chưa tới hạn không?** Không mặc định; `executeExistingDelayedTasksAfterShutdownPolicy` thường true. Phải cấu hình theo lifecycle contract.

**11. Hủy periodic future khi nó đang chờ lần tiếp theo có được remove ngay không?** Có theo policy true khi task còn queued. Hủy trong lúc task đang chạy là tình huống khác.

**12. Khi queue đã gỡ task, có chắc payload được GC không?** Không. Có thể còn references từ registry/caller; GC không được cam kết chạy ngay.

**13. Có nên tự `getQueue().remove(future)` mà không cancel không?** Không. Xóa queue node không thay thế Future cancellation semantics và dễ gây sai lifecycle.

**14. Nếu bị cancel ở thời điểm gần hạn chạy, queue size có luôn theo một lịch cố định không?** Không. Worker có thể đã lấy task ra trước khi cancel; cần phân biệt race với policy.

**15. Có thể dùng scheduled executor làm durable job scheduler qua JVM crash không?** Không. Đây là scheduler trong process; nếu cần persist/khôi phục jobs, phải thiết kế storage/recovery riêng.

## 20. Checklist cấu hình và vận hành

- Đã xác định scheduler là **ScheduledThreadPoolExecutor** và owner của nó chưa?
- Có đọc/đặt `getRemoveOnCancelPolicy/setRemoveOnCancelPolicy` theo workload không?
- Với scheduler có tỷ lệ cancel cao, đã bật policy **sớm khi khởi tạo** chưa?
- Nếu mới bật trên scheduler cũ, có cần `purge()` để dọn canceled backlog trước đó không?
- Có giữ `ScheduledFuture` handles trong registry quá lâu dù đã cancel không?
- Có phân biệt **Future state**, **queue membership**, **worker executing**, **actual cleanup** không?
- Với periodic jobs, đã hiểu fixed-rate/fixed-delay và cancellation giữa hai lần chạy chưa?
- Có chính sách hủy và timeout I/O riêng cho tasks đã bắt đầu không?
- Queue có tăng vì **valid tasks** hay canceled tasks? Đã xem heap dump/JFR và metrics?
- Đã chọn `executeExistingDelayedTasksAfterShutdownPolicy` và `continueExistingPeriodicTasksAfterShutdownPolicy` theo đúng nghiệp vụ chưa?
- Shutdown có `awaitTermination` và fallback hữu hạn thay vì chỉ gọi một method rồi giả định xong không?
- Khi chạy test có đặt delay đủ dài/handshake để tránh dựa vào scheduler may rủi không?
- Nếu callback có side effects, có state machine/idempotency để xử lý race giữa hoàn thành bình thường và timeout không?

**Kết luận:** `setRemoveOnCancelPolicy(true)` là **công cụ quản lý retention của canceled scheduled tasks** trong `ScheduledThreadPoolExecutor`. Nó giúp **gỡ task còn queued ngay khi hủy**, đặc biệt hữu ích với nhiều tác vụ delay dài hoặc periodic timers bị cancel; mặc định false có thể để các canceled futures ở queue đến lúc đến hạn. Nhưng **cancellation, queue removal, worker interruption, cleanup và GC là những sự kiện khác nhau**. Dùng policy đúng từ khi tạo scheduler, `purge()` khi cần dọn backlog cũ, đo queue và heap theo thời gian, đồng thời thiết kế shutdown/cancellation và giới hạn pending valid work độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-018">Tác vụ định kỳ chạy lâu: fixed rate, fixed delay và việc không chạy chồng chính nó</a>

<details>
<summary>Xem chi tiết</summary>

# Tác vụ định kỳ chạy lâu: fixed rate, fixed delay và việc không chạy chồng chính nó

> **Thông điệp quan trọng:** Trong Java, `ScheduledExecutorService.scheduleAtFixedRate` và `scheduleWithFixedDelay` đều tạo **một chuỗi thực thi tuần tự của cùng một tác vụ định kỳ**. Khi tác vụ chạy lâu hơn chu kỳ, lần chạy tiếp theo của **chính periodic task đó không chồng lên lần trước**. Điểm khác nhau là **fixed rate bám lịch bắt đầu danh nghĩa**, còn **fixed delay chỉ bắt đầu đếm delay sau khi lần chạy trước kết thúc**. Không được suy ra rằng các tác vụ định kỳ *khác nhau* trong pool không thể chạy song song.

Bài thuộc **Future & Scheduling** của **Java concurrency nói chung**, dùng Java SE 17+ và chủ yếu các interface `ScheduledExecutorService`, `ScheduledFuture` cùng implementation `ScheduledThreadPoolExecutor`. Không bàn đến Spring hay annotation scheduling của framework.

## 1. Tác vụ định kỳ và câu hỏi cần giải quyết

Với polling, cập nhật cache, kiểm tra health, dọn dẹp dữ liệu hoặc ghi thống kê, ta muốn chạy một `Runnable` nhiều lần theo lịch. Vấn đề xuất hiện khi **một lần chạy mất nhiều thời gian hơn lịch dự kiến**.

Ví dụ, một tác vụ cần chạy “mỗi 100 ms” nhưng một lượt xử lý tốn 250 ms. Chuyện gì xảy ra với lượt kế tiếp?

- Có sinh thêm một thread mới để chạy chồng lên lượt đang xử lý không?
- Có bỏ những “tick” đã trễ không?
- Lượt tiếp theo được tính từ **mốc lịch ban đầu** hay **lúc lượt trước kết thúc**?
- Nếu pool có 4 worker, 4 lượt của cùng task có được chạy cùng lúc không?
- Nếu một lượt ném exception, lịch định kỳ còn tồn tại không?

Trả lời đúng cần phân biệt **một periodic schedule**, **một lần execution**, và **nhiều scheduled tasks độc lập**.

~~~text
ScheduledExecutorService
  ├── periodic task A: A1 -> A2 -> A3 -> ...   (không tự overlap)
  ├── periodic task B: B1 -> B2 -> B3 -> ...   (không tự overlap)
  └── one-shot task C: C1

A1 có thể đồng thời với B1 hoặc C1 nếu đủ worker,
nhưng A1 không đồng thời với A2 thuộc cùng một schedule.
~~~

Đặc tính **không tự overlap** thuộc về các *successive executions của một periodic task được lập lịch bằng API này*. Nó không phải mutex chung bảo vệ mọi lời gọi của cùng một class hay cùng một `Runnable`.

## 2. Hai API phổ biến

~~~java
// Snippet API minh họa, chưa phải chương trình độc lập.
ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2);

// Mốc bắt đầu lý tưởng: initialDelay, initialDelay+period,
// initialDelay+2*period,... tính từ lịch ban đầu.
ScheduledFuture<?> rateHandle = scheduler.scheduleAtFixedRate(
        task, 0, 100, TimeUnit.MILLISECONDS);

// Đợi delay từ lúc KẾT THÚC execution trước,
// rồi mới đủ điều kiện bắt đầu execution tiếp theo.
ScheduledFuture<?> delayHandle = scheduler.scheduleWithFixedDelay(
        task, 0, 100, TimeUnit.MILLISECONDS);
~~~

Cả hai method nhận một `Runnable`, `initialDelay` và một lượng thời gian `period` hoặc `delay`, trả về một `ScheduledFuture<?>` dùng để quan sát/hủy lịch.

Các ràng buộc đáng nhớ:

- `scheduleAtFixedRate` yêu cầu `period > 0`; `scheduleWithFixedDelay` yêu cầu `delay > 0`, nếu không sẽ ném `IllegalArgumentException`.
- `initialDelay` có thể bằng 0 để chạy ngay khi đủ điều kiện; độ trễ âm ở một số phương thức được xử lý như yêu cầu immediate theo hợp đồng scheduling, nhưng đừng thiết kế lịch nghiệp vụ dựa vào delay âm.
- `TimeUnit` định nghĩa đơn vị; không nhầm mili-giây với giây.
- Các lần thực thi diễn ra trên worker threads do executor quản lý; không dùng `Thread.sleep` trong thread gọi schedule để duy trì lịch.
- `ScheduledFuture.get()` **không trả kết quả bình thường sau mỗi lượt** đối với periodic schedule còn đang lặp. Nó đại diện cho cả chuỗi thực thi định kỳ, không phải một Future riêng cho mỗi tick.

## 3. Fixed rate: mốc bắt đầu dự kiến cách nhau một period

`scheduleAtFixedRate(task, initialDelay, period, unit)` hướng đến lịch:

~~~text
Thời điểm dự kiến của lần k (k từ 0):
    scheduledStart(k) = t0 + initialDelay + k × period
~~~

Ví dụ period 100 ms, thời điểm đủ điều kiện bắt đầu lần lượt là 0, 100, 200, 300 ms (tính tương đối từ t0).

Nếu mỗi lượt chạy **ngắn hơn period**, giữa các lượt có khoảng nghỉ để bám mốc. Nhưng nếu lượt đầu mất **250 ms**, thời điểm danh nghĩa của lượt thứ hai (100 ms) đã qua. Lượt thứ hai có thể **bắt đầu muộn gần như ngay sau khi lượt đầu kết thúc**, tùy worker scheduling; không cần chờ thêm đủ 100 ms tính từ lúc kết thúc.

~~~text
period=100 ms, mỗi lần chạy=250 ms (minh họa lý tưởng)

Mốc fixed-rate:  0    100    200    300    400    500 ...
                 |-----|------|------|------|------|
Chạy A1:         [========= 250 ms =========]
Chạy A2:                                    [==== ...]
                 ^ A2 bắt đầu muộn, không chạy chồng A1
~~~

**Quan trọng:** Đây không có nghĩa scheduler tạo hàng đống `Runnable` riêng cho các mốc bị lỡ rồi chạy chúng đồng thời. Hợp đồng của `ScheduledThreadPoolExecutor` bảo đảm các lần thực thi của **cùng periodic task không overlap**. Khi thời gian đã trễ, những lần chạy tiếp theo có thể bắt đầu liên tiếp với **rất ít hoặc không có thời gian nghỉ** (ngoài chi phí scheduler).

Không có bảo đảm hard real-time về đúng mốc 100 ms: OS, CPU saturation, GC, contention, timer resolution và số worker sẵn có đều có thể khiến lần chạy bắt đầu muộn.

## 4. Fixed delay: tính delay từ khi lượt trước kết thúc

`scheduleWithFixedDelay(task, initialDelay, delay, unit)` có quy tắc:

~~~text
Thời điểm eligible lần đầu: t0 + initialDelay
Lần kế tiếp được phép bắt đầu:
    finish(previous execution) + delay
~~~

Nếu execution mất 250 ms và delay 100 ms, các lần chạy lý tưởng bắt đầu tại 0, 350, 700, 1050 ms.

~~~text
delay=100 ms, mỗi lần chạy=250 ms (minh họa lý tưởng)

Lượt D1:        [========= 250 ms =========]
Nghỉ:                                    [100 ms]
Lượt D2:                                         [========= 250 ms =========]
Nghỉ:                                                                     [100 ms]
Lượt D3:                                                                    ...
~~~

Không phải “fixed delay cố gắng bám 100 ms giữa hai lần bắt đầu”. Nó cam kết **khoảng chờ tối thiểu theo lịch giữa khi kết thúc và lần bắt đầu kế tiếp** (việc được CPU cấp phát có thể khiến khoảng chờ thực tế lớn hơn).

Khi task chậm, tần suất thực tế của fixed delay giảm theo độ dài thực thi. Điều này phù hợp cho:

- Polling cần chờ sau khi request trước kết thúc.
- Cleanup không muốn chạy dồn dập.
- Task có tải I/O cần khoảng cách nghỉ để giảm áp lực downstream.
- Những trường hợp cần tránh hai lần invocation liên tiếp sát nhau khi một invocation đã trễ.

Fixed delay cũng không cho phép cùng periodic task chạy chồng lên chính nó.

## 5. So sánh khi một lượt chạy dài hơn period/delay

| Tiêu chí | `scheduleAtFixedRate` | `scheduleWithFixedDelay` |
| --- | --- | --- |
| Tính lịch từ đâu? | Các mốc bắt đầu danh nghĩa từ `initialDelay` + bội số `period` | Kết thúc lượt trước + `delay` |
| Khi duration ngắn hơn khoảng đặt | Thường có khoảng nghỉ để bám lịch | Nghỉ đúng delay theo cơ chế scheduler |
| Khi duration dài hơn khoảng đặt | Lượt sau có thể bắt đầu muộn, gần như ngay khi lượt trước xong | Vẫn chờ thêm delay sau khi lượt trước xong |
| Có tự overlap cùng periodic task? | **Không** | **Không** |
| Có giữ chính xác mốc thời gian tuyệt đối? | Không, chỉ lập lịch tương đối; có jitter | Không, có jitter |
| Tần suất khi quá tải | Có thể dồn các lượt sát nhau, gây áp lực liên tục | Giảm tự nhiên vì mỗi lượt có khoảng nghỉ |
| Thích hợp khi | Muốn bám cadence định kỳ, ví dụ thu mẫu gần đều | Muốn tránh dồn lần chạy, ví dụ polling và cleanup |
| Nhược điểm cần theo dõi | Trễ tích lũy, chạy liên tiếp không nghỉ | Trôi lịch theo độ dài mỗi execution |

Trong nhiều trường hợp, **fixed rate cũng không “bỏ qua” mỗi mốc trễ theo cách bạn tự kỳ vọng**; nó tiếp tục dựa trên chuỗi mốc danh nghĩa. Nhưng API không hứa một số lượng tick hoàn tất tại thời điểm thực tế cố định. Không dùng `numberOfRuns == elapsed/period` để chứng minh đúng với mọi scheduling/load.

## 6. Ví dụ 1 — mô hình thời gian xác định để hiểu khác biệt

Trước khi viết chương trình có thread và đồng hồ thật, có thể tính toán **mốc thời gian lý tưởng** để phân biệt hai quy tắc. Ví dụ sau không dùng scheduler thực; nó mô phỏng một periodic task **luôn** mất 250 ms và period/delay là 100 ms.

Tạo `PeriodicTimelineModel.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.List;

public class PeriodicTimelineModel {
    public static void main(String[] args) {
        long durationMs = 250;
        long intervalMs = 100;
        int iterations = 4;

        List<Long> rateStarts = new ArrayList<>();
        long nextNominalRate = 0;
        long previousRateFinish = 0;

        for (int i = 0; i < iterations; i++) {
            long actualStart = Math.max(nextNominalRate, previousRateFinish);
            rateStarts.add(actualStart);
            previousRateFinish = actualStart + durationMs;
            nextNominalRate += intervalMs;
        }

        List<Long> delayStarts = new ArrayList<>();
        long nextDelayStart = 0;

        for (int i = 0; i < iterations; i++) {
            delayStarts.add(nextDelayStart);
            long finish = nextDelayStart + durationMs;
            nextDelayStart = finish + intervalMs;
        }

        System.out.println("fixed rate starts (ms) = " + rateStarts);
        System.out.println("fixed delay starts (ms) = " + delayStarts);
    }
}
~~~

Chạy:

~~~bash
javac PeriodicTimelineModel.java
java PeriodicTimelineModel
~~~

Stdout **chính xác**:

~~~text
fixed rate starts (ms) = [0, 250, 500, 750]
fixed delay starts (ms) = [0, 350, 700, 1050]
~~~

**Giải thích:** Fixed rate có các mốc dự kiến 0, 100, 200, 300 ms nhưng mỗi lượt tốn 250 ms, nên lần tiếp theo đủ điều kiện từ lâu khi lượt hiện tại hoàn tất. Fixed delay cộng 100 ms **sau mỗi lần hoàn thành**. Cả hai mô hình đều chỉ thực hiện **một lượt tại một thời điểm**.

Đây là mô hình lý tưởng **không có scheduler jitter, executor queue, GC, preemption hay hệ thống nhiều worker**. Stdout của mô hình có thể xác định hoàn toàn, còn timestamp tuyệt đối trong ứng dụng thật không có cùng bảo đảm.

## 7. Quy tắc “không chạy chồng chính nó” áp dụng tới đâu?

Java API `ScheduledThreadPoolExecutor` nói rõ: các lần thực thi liên tiếp của một task được lập lịch bằng `scheduleAtFixedRate` hoặc `scheduleWithFixedDelay` **không overlap**; hiệu ứng của một lần thực thi *happen-before* hiệu ứng của lần tiếp theo, ngay cả khi các lần thực thi có thể do **những worker thread khác nhau** thực hiện.

Điều này có một số hệ quả:

1. Nếu invocation `A1` của một periodic schedule vẫn chưa kết thúc, invocation `A2` **không thể bắt đầu chồng** lên `A1` do riêng schedule ấy.
2. Việc scheduler có `corePoolSize=4` hay `corePoolSize=32` **không thay đổi** quy tắc ở mục 1.
3. Invocation kế tiếp có thể được thread khác thực hiện, vì vậy không dựa vào `Thread.currentThread().getName()` luôn giữ nguyên.
4. Các lần chạy được điều phối về mặt memory visibility theo quan hệ *happens-before* giữa successive executions.
5. Hai periodic schedule **khác nhau** có thể đồng thời; không có global exclusion giữa mọi task của scheduler.
6. Nếu một ứng dụng **tự lập lịch cùng một Runnable object hai lần**, nó tạo **hai periodic futures độc lập**; các invocation từ hai lịch có thể overlap nhau. Cùng tham chiếu `Runnable` không đồng nghĩa cùng periodic task schedule.
7. Một periodic task tự submit một task con vào pool khác rồi return; **phần việc asynchronous của task con** có thể còn chạy khi invocation kỳ sau bắt đầu. Scheduler chỉ không overlap phần **execution body của chính scheduled Runnable**, không tự theo dõi downstream tasks.

### Phân biệt “không overlap” và “không cần đồng bộ”

Nếu chỉ một periodic schedule truy cập một state không bị thread nào khác dùng, việc các invocation của nó không overlap giúp tránh reentrancy nội bộ. Nhưng nếu:

- Reader bên ngoài đọc cùng mutable state,
- Có scheduled task khác cũng sửa state,
- Handler đồng thời đến từ API khác,
- Task chuyển việc cho asynchronous child task,

thì **race conditions vẫn có thể xảy ra**. Phải thiết kế thread confinement, locks, atomic variables hoặc immutable snapshots theo phạm vi thực.

## 8. Ví dụ 2 — xác nhận một periodic task không tự overlap khi pool có nhiều worker

Để không dùng `Thread.sleep` rồi suy đoán scheduler, dùng các latch kiểm soát một invocation đang giữ trạng thái. Trong khi invocation thứ nhất vẫn bị chặn, task khác vẫn có thể chạy trên worker khác trong cùng pool.

Tạo `PeriodicNoOverlapDemo.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PeriodicNoOverlapDemo {
    public static void main(String[] args) throws InterruptedException {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(4);

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch otherTaskRan = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger runs = new AtomicInteger();

        ScheduledFuture<?> periodic = pool.scheduleAtFixedRate(() -> {
            int nowActive = active.incrementAndGet();
            maximumActive.accumulateAndGet(nowActive, Math::max);
            int invocation = runs.incrementAndGet();

            try {
                if (invocation == 1) {
                    firstEntered.countDown();
                    releaseFirst.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        }, 0, 1, TimeUnit.MILLISECONDS);

        try {
            firstEntered.await();

            pool.schedule(otherTaskRan::countDown,
                    0, TimeUnit.MILLISECONDS);
            otherTaskRan.await();

            System.out.println("runs while first blocked = " + runs.get());
            System.out.println("maximum active = " + maximumActive.get());
            System.out.println("other task completed = true");

            System.out.println("cancelled = " + periodic.cancel(true));
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
~~~

Chạy:

~~~bash
javac PeriodicNoOverlapDemo.java
java PeriodicNoOverlapDemo
~~~

Stdout khi không có interruption bên ngoài:

~~~text
runs while first blocked = 1
maximum active = 1
other task completed = true
cancelled = true
~~~

**Tại sao có thể tin kết quả này?**

- `firstEntered.await()` chứng minh lần đầu đã vào Runnable và đang đợi `releaseFirst`.
- Khi lần đầu chưa return, periodic schedule đó không thể chạy invocation thứ hai, dù pool có 4 worker và period chỉ 1 ms.
- Task **khác** được enqueue với delay 0 vẫn chạy được ở một worker rảnh, chứng minh pool không bị khóa độc quyền toàn cục.
- Main cancel periodic Future khi lần đầu còn bị giữ, sau đó `finally` mới mở latch và dừng pool; không có cơ hội tạo một invocation thứ hai thành công.
- `maximumActive == 1` ở thời điểm kiểm tra là dấu hiệu của thử nghiệm; bảo đảm tổng quát về không overlap đến từ hợp đồng API, không đến từ stress test duy nhất.

**Không giữ latch blocking trong một periodic job production** chỉ để thử nghiệm. Với hệ thống thật, hãy dùng timeout và cảnh báo nếu task không tiến triển; tránh khóa tài nguyên quan trọng khi chờ một thread khác cũng cần tài nguyên ấy.

## 9. Fixed rate gặp overload: xử lý mốc trễ và không “catch up song song”

Giả sử period 100 ms nhưng một execution phải chờ network 2 giây. Trong suốt 2 giây đó, rất nhiều mốc danh nghĩa đã trôi qua. Với fixed rate:

- Không tạo 20 bản sao của **chính periodic Future** chạy trên 20 worker để “bù kịp”.
- Lần chạy kế tiếp có thể bắt đầu muộn khi lần trước kết thúc.
- Những lần sau vẫn gắn với chuỗi mốc ban đầu; nếu còn trễ, có thể bắt đầu lại rất sớm.
- Việc chạy dồn dập sau nghẽn có thể khiến **downstream tiếp tục chịu tải**, không có thời gian nghỉ để phục hồi.
- Không có cam kết rằng toàn bộ số lần tương ứng mốc đã qua sẽ hoàn thành trong một khoảng thời gian giới hạn.

Một task fixed-rate đọc dữ liệu có thể cần chính sách **skip stale work** hoặc **coalesce**: thay vì cố xử lý mọi lần cập nhật đã trễ theo logic scheduler, code tự kiểm tra snapshot hiện tại và chỉ thực hiện việc còn hữu ích. Nếu cần một event stream có độ bền và xử lý từng sự kiện, periodic scheduling không phải queue nghiệp vụ durable.

Không dùng fixed rate với kỳ vọng nó tự giới hạn concurrency. Nó giới hạn **overlap của chính schedule ấy**, không giới hạn **tổng công việc** nếu task gọi `submit` để đẩy thêm nhiệm vụ xuống một executor khác.

## 10. Fixed delay khi tác vụ chạy lâu

Với fixed delay, nếu execution mất 2 giây và delay=100 ms, lần kế tiếp chỉ đủ điều kiện sau khi execution hoàn tất và thêm 100 ms. Điều này:

- Tạo một **khoảng nghỉ thật sự giữa hai invocation**, theo cơ chế scheduling.
- Tránh các invocation được phát liên tiếp với độ nghỉ gần 0 khi một lượt chạy trễ.
- Dễ hiểu hơn cho polling “đợi một lúc rồi thử lại sau khi lần trước xong”.
- Làm tần suất bắt đầu biến động cùng thời lượng xử lý, không giữ nhịp bắt đầu cố định.

Fixed delay **không** đặt deadline cho duration. Nếu một invocation kẹt vĩnh viễn, invocation kế tiếp không chạy; scheduler không tự interrupt invocation chỉ vì đã qua delay. Cần I/O timeout, cancellation, watchdog hoặc policy khác nếu muốn kiểm soát thời gian thực thi.

Nếu muốn “thử sau 100 ms kể từ khi đã nhận response”, fixed delay phù hợp. Nếu muốn “cố lấy mẫu vào những mốc gần mỗi phút theo thời gian tường”, phải phân tích fixed rate, clock alignment, độ trễ và missed sampling riêng.

## 11. Ví dụ 3 — kiểm tra khoảng delay sau khi invocation kết thúc

Ví dụ dưới đây chạy **thật** với scheduler và chỉ in một **mệnh đề về thứ tự được đảm bảo**, không in timestamp ngẫu nhiên. Một lượt làm việc mô phỏng mất khoảng 70 ms; delay giữa các lượt là 20 ms. Sau hai lượt, ta kiểm tra khoảng tính từ gần điểm kết thúc lượt 1 đến lúc bắt đầu lượt 2.

Tạo `FixedDelayGapDemo.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FixedDelayGapDemo {
    public static void main(String[] args) throws InterruptedException {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        AtomicLong firstEnd = new AtomicLong();
        AtomicLong secondStart = new AtomicLong();

        ScheduledFuture<?> future = pool.scheduleWithFixedDelay(() -> {
            int iteration = runs.incrementAndGet();
            long start = System.nanoTime();
            if (iteration == 1) {
                try {
                    // Chỉ mô phỏng công việc chậm, không dùng để
                    // chứng minh vị trí lịch bằng timestamp tuyệt đối.
                    Thread.sleep(70);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                firstEnd.set(System.nanoTime());
            } else if (iteration == 2) {
                secondStart.set(start);
                secondStarted.countDown();
            }
        }, 0, 20, TimeUnit.MILLISECONDS);

        try {
            secondStarted.await();
            long observedGapNs = secondStart.get() - firstEnd.get();
            System.out.println("at least two runs = " + (runs.get() >= 2));
            System.out.println("gap >= 20 ms = "
                    + (observedGapNs >= TimeUnit.MILLISECONDS.toNanos(20)));
        } finally {
            future.cancel(true);
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
~~~

Chạy:

~~~bash
javac FixedDelayGapDemo.java
java FixedDelayGapDemo
~~~

Stdout khi task không bị interrupt ngoài chương trình:

~~~text
at least two runs = true
gap >= 20 ms = true
~~~

Scheduler tính lần đủ điều kiện tiếp theo **sau khi callback lần trước return**; phép `firstEnd.set` nằm ngay trước lúc return nên khoảng đo đến đầu lượt 2 không thể nhỏ hơn delay 20 ms theo semantics và đồng hồ monotonic trên JVM bình thường. Con số elapsed thực tế có thể lớn hơn do scheduler hoặc GC; ví dụ cố tình chỉ in điều kiện đủ/không đủ, không hard-code “90 ms”.

**Giới hạn thử nghiệm:** Một bài benchmark thời gian thực tế cần nhiều mẫu, warmup và môi trường phù hợp. `Thread.sleep(70)` ở đây chỉ tạo duration dài hơn delay để minh họa, không phải kỹ thuật bảo đảm chính xác 70 ms hoặc cách điều phối thread trong production.

## 12. Pool có nhiều worker: hai periodic schedules khác nhau vẫn có thể overlap

Quy tắc không tự overlap **không tạo global mutex** cho scheduler. Một scheduler có từ hai worker trở lên có thể chạy task A và task B đồng thời.

### Ví dụ 4 — hai periodic schedules khác nhau cùng có thể đang chạy

Tạo `DifferentPeriodicTasks.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DifferentPeriodicTasks {
    public static void main(String[] args) throws InterruptedException {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(2);

        CountDownLatch aEntered = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        CountDownLatch bFinished = new CountDownLatch(1);
        AtomicBoolean aRunning = new AtomicBoolean();
        AtomicBoolean bObservedA = new AtomicBoolean();

        ScheduledFuture<?> a = pool.scheduleAtFixedRate(() -> {
            aRunning.set(true);
            aEntered.countDown();
            try {
                releaseA.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                aRunning.set(false);
            }
        }, 0, 1, TimeUnit.DAYS);

        ScheduledFuture<?> b = null;
        try {
            aEntered.await();
            b = pool.scheduleWithFixedDelay(() -> {
                bObservedA.set(aRunning.get());
                bFinished.countDown();
            }, 0, 1, TimeUnit.DAYS);

            bFinished.await();
            System.out.println("B ran while A active = "
                    + bObservedA.get());
        } finally {
            a.cancel(true);
            if (b != null) {
                b.cancel(true);
            }
            releaseA.countDown();
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
~~~

Chạy:

~~~bash
javac DifferentPeriodicTasks.java
java DifferentPeriodicTasks
~~~

Stdout khi bình thường:

~~~text
B ran while A active = true
~~~

Task A đang giữ worker thứ nhất và chờ latch; task B được chạy trên worker thứ hai, đọc cờ A còn active. Hai periodic tasks **độc lập** có thể overlap. Điều không thể xảy ra là lần A thứ hai bắt đầu trong khi lần A thứ nhất chưa kết thúc, vì hai lần đó thuộc cùng lịch `a`.

**Nếu cùng một instance `Runnable` được schedule hai lần**, kết luận cũng tương tự: mỗi lời gọi schedule tạo một periodic job độc lập. Nếu hai job sửa cùng state, cần đồng bộ riêng hoặc không schedule trùng.

## 13. Các lần chạy liên tiếp có quan hệ happens-before

`ScheduledThreadPoolExecutor` có bảo đảm đặc biệt: dù hai lần thực thi kế tiếp của **cùng periodic task** có thể do những thread khác nhau chạy, hiệu ứng của lần trước *happen-before* lần sau. Điều này giúp các thao tác của chính periodic task được quan sát đúng theo thứ tự execution của nó.

~~~text
Periodic schedule A:
    A1 (worker-2) -- completes --> A2 (worker-1)
        |                             ^
        +----------- happens-before --+

Periodic schedule B:
    B1 (worker-3) có thể chạy đồng thời A1;
    không có một HB tổng quát chỉ vì hai task cùng scheduler.
~~~

Không suy ra:

- Field mutable của task tự động an toàn với **reader bên ngoài**.
- Hai periodic tasks khác nhau được serialize với nhau.
- Downstream async tasks được bảo vệ chỉ vì parent task không overlap.
- Side effects mạng/database “commit” theo thứ tự xảy ra trong JVM nếu chúng thuộc hệ thống khác và API chưa đồng bộ trạng thái ấy.
- Hàm `Runnable.run()` luôn được gọi trên cùng worker thread hoặc mang cùng `ThreadLocal` state.

Nếu cần đưa snapshot tới các consumer khác, vẫn cần safe publication/`volatile`, lock, queue, atomic hoặc immutable state theo tình huống. Không sử dụng `ThreadLocal` để lưu state bền vững xuyên các lần invocation nếu lịch có thể chạy trên các worker khác nhau.

## 14. Một lần chạy ném exception: lịch định kỳ bị dừng

Đặc tính quan trọng của `scheduleAtFixedRate` và `scheduleWithFixedDelay`: nếu một execution ném một exception **thoát ra khỏi `Runnable.run()`**, các lần thực thi sau của periodic task đó **bị suppress**. Đây không chỉ là một failed tick; chuỗi periodic execution có thể chấm dứt.

`ScheduledFuture` đại diện cho chuỗi ấy khi đó hoàn tất theo đường lỗi:

- `isDone() == true`, vì chuỗi đã chấm dứt.
- `isCancelled() == false` nếu nó chấm dứt do exception chứ không phải cancellation.
- `future.get()` ném `ExecutionException`, nguyên nhân nằm ở `getCause()`.
- Scheduler có thể vẫn chạy những scheduled tasks khác; **không có nghĩa toàn bộ executor đã shutdown**.

### Ví dụ 5 — lỗi ở lượt thứ hai dừng lịch

Tạo `PeriodicFailureDemo.java` (Java 17+):

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PeriodicFailureDemo {
    public static void main(String[] args) throws InterruptedException {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(1);
        AtomicInteger count = new AtomicInteger();

        try {
            ScheduledFuture<?> future = pool.scheduleAtFixedRate(() -> {
                int n = count.incrementAndGet();
                if (n == 2) {
                    throw new IllegalStateException("boom");
                }
            }, 0, 1, TimeUnit.MILLISECONDS);

            try {
                future.get();
            } catch (ExecutionException ex) {
                System.out.println("cause = "
                        + ex.getCause().getClass().getSimpleName());
            }

            System.out.println("runs = " + count.get());
            System.out.println("isDone = " + future.isDone());
            System.out.println("isCancelled = " + future.isCancelled());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
~~~

Chạy:

~~~bash
javac PeriodicFailureDemo.java
java PeriodicFailureDemo
~~~

Stdout:

~~~text
cause = IllegalStateException
runs = 2
isDone = true
isCancelled = false
~~~

Tại invocation 2, unchecked exception thoát khỏi body, chuỗi chạy định kỳ **không được tiếp tục**. `get()` đang chờ periodic Future sẽ được giải phóng bằng `ExecutionException` (thay vì đợi vô hạn). Đây là một điểm rất dễ bỏ sót nếu chương trình schedule job rồi bỏ qua `ScheduledFuture`.

### Bắt exception hay để lịch dừng?

Phải quyết định theo ngữ nghĩa nghiệp vụ:

- **Fail-fast:** nếu một lỗi làm state không còn hợp lệ, cho exception thoát để dừng schedule, báo lỗi và chờ operator/coordinator.
- **Continue after a recoverable error:** bắt những exception đã hiểu, ghi nhận lỗi, xử lý retry/backoff hợp lệ rồi return bình thường để scheduler cho phép lượt tiếp.
- **Do not swallow everything:** bắt `Throwable` bừa bãi rồi tiếp tục có thể che giấu `Error` nghiêm trọng; log/retry vô hạn không có giới hạn cũng có thể phá hệ thống.
- **Interrupt handling:** nếu `InterruptedException` xuất hiện khi đang xử lý blocking call, cân nhắc restore interrupt flag và thoát đúng policy; đừng coi shutdown như một lỗi cần retry ngay lập tức.

Một wrapper minh họa:

~~~java
// Snippet trong scheduled Runnable; không phải chương trình độc lập.
try {
    pollOnce();
} catch (RecoverableDomainException e) {
    recordFailure(e);
    // Return để lần sau được phép chạy.
}
~~~

Exception do `recordFailure` ném ra vẫn có thể dừng periodic execution. Cần theo dõi cả **lần chạy cuối thành công** và **số lỗi** ở một nơi được kiểm soát, không chỉ kiểm tra executor còn sống hay không.

## 15. ScheduledFuture: trạng thái, get và cancellation

Hai method định kỳ trả về một `ScheduledFuture<?>`. Handle này đại diện cho **toàn bộ chuỗi periodic execution**, không tạo một Future mới cho mỗi lần callback được gọi.

| Thời điểm | `isDone` | `isCancelled` | `get()` |
| --- | --- | --- | --- |
| Đang đợi initial delay | Thường false | false | Chờ |
| Có invocation đang chạy hoặc lịch đang lặp bình thường | false | false | **Tiếp tục chờ** (không trả sau một lần chạy) |
| Một invocation ném exception thoát ra | true | false | `ExecutionException` |
| `cancel` thành công | true | true | `CancellationException` |
| Executor shutdown và periodic schedule bị hủy theo policy | true | true (nếu Future bị cancel) | `CancellationException` |

`getDelay(unit)` phản ánh thời gian còn lại đến khi task đủ điều kiện chạy theo cơ chế scheduler, **không** cho biết duration của callback hoặc một start-time guarantee. Với periodic task đang quá hạn, delay có thể bằng 0 hoặc âm tùy thời điểm/API, nhưng không thể suy ra có nhiều bản sao đang chạy chồng.

### Cancellation không ép callback đang chạy dừng tức thì

- `future.cancel(false)`: yêu cầu ngừng những lần chạy sau, không yêu cầu interrupt invocation hiện tại. Future có thể cancelled trong khi callback đang chạy vẫn tiếp tục làm việc.
- `future.cancel(true)`: yêu cầu ngừng những lần chạy sau và **cố interrupt** callback đang chạy nếu implementation cho phép. Task phải hợp tác với interruption/timeout mới dừng sớm an toàn.
- `isDone()==true` sau cancel không phải bằng chứng mọi side effect đang thực hiện đã kết thúc, không phải transaction rollback.
- `get(timeout)` timeout chỉ giới hạn **lần chờ ở caller**, không tự cancel schedule. Với periodic Future bình thường còn chạy lặp, `get(timeout)` rất dễ timeout vì Future không có kết quả bình thường mỗi chu kỳ.

Nếu muốn biết một lần chạy cụ thể đã xử lý xong, hãy dùng `CountDownLatch`, `CompletableFuture`/acknowledgment theo lần chạy, metrics hoặc state machine riêng. Không lạm dụng `ScheduledFuture.get()` như cơ chế “wait until next tick finishes”.

## 16. Ví dụ 6 — cancel một periodic task trước khi bắt đầu

Tạo `CancelPeriodicDemo.java` (Java 17+):

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CancelPeriodicDemo {
    public static void main(String[] args) throws InterruptedException {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(1);
        pool.setRemoveOnCancelPolicy(true);

        AtomicInteger runs = new AtomicInteger();

        try {
            ScheduledFuture<?> future = pool.scheduleAtFixedRate(
                    runs::incrementAndGet,
                    1, 1, TimeUnit.DAYS);

            System.out.println("cancelled = " + future.cancel(false));
            System.out.println("done = " + future.isDone());
            System.out.println("isCancelled = " + future.isCancelled());
            System.out.println("queued entries = " + pool.getQueue().size());

            try {
                future.get();
            } catch (CancellationException expected) {
                System.out.println("get = CancellationException");
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        System.out.println("runs = " + runs.get());
    }
}
~~~

Chạy:

~~~bash
javac CancelPeriodicDemo.java
java CancelPeriodicDemo
~~~

Stdout:

~~~text
cancelled = true
done = true
isCancelled = true
queued entries = 0
get = CancellationException
runs = 0
~~~

Initial delay là một ngày, nên việc cancel ngay sau schedule diễn ra trước lần chạy đầu. `setRemoveOnCancelPolicy(true)` đảm bảo những delayed task đã cancel không bị giữ lại trong queue lâu không cần thiết theo implementation `ScheduledThreadPoolExecutor`.

**Lưu ý memory retention:** Mặc định `ScheduledThreadPoolExecutor` có thể giữ cancelled delayed tasks trong work queue đến khi tới thời điểm lịch, dù chúng sẽ không được chạy; policy remove-on-cancel giúp giải phóng sớm hơn. Không nhầm nó với method trên `ThreadPoolExecutor` thông thường.

## 17. `ScheduledThreadPoolExecutor` và số worker

`Executors.newScheduledThreadPool(corePoolSize)` tạo scheduler với số core workers được yêu cầu. Khác với `ThreadPoolExecutor` dùng bounded queue tùy ý, implementation `ScheduledThreadPoolExecutor` dùng một delayed work queue **không giới hạn hữu dụng ở mức ứng dụng**; tài liệu lưu ý nó hoạt động như một **fixed-sized pool bằng `corePoolSize`** và việc chỉnh `maximumPoolSize` không có hiệu ứng hữu ích.

Những hệ quả:

- Nhiều delayed tasks hoặc periodic schedules khác nhau có thể cùng đủ điều kiện chạy nhưng phải **đợi worker rảnh**.
- Nếu corePoolSize=1 và task A blocking, task B có thể bị hoãn dù B có deadline sớm.
- Nếu corePoolSize=4, một periodic task A chạy quá lâu vẫn **không tự overlap**; nhưng task B, C, D có thể dùng worker khác.
- Nếu có rất nhiều periodic schedules, việc queue tồn tại không biến số worker thành vô hạn; tổng tải vẫn bị giới hạn bởi CPU, I/O và downstream.
- Các scheduled future/queue entries có thể giữ object graph và tăng memory pressure nếu số lịch đăng ký phát triển không kiểm soát.

**Không tăng `maximumPoolSize` mong sửa backlog** của ScheduledThreadPoolExecutor; hãy đo mức sử dụng core workers, thời gian invocation, số lịch độc lập, chờ queue và downstream saturation. Tăng corePoolSize chỉ thích hợp khi tác vụ độc lập và tài nguyên bên ngoài chịu được concurrency tăng.

### Không giảm core pool tùy tiện về 0

Tài liệu `ScheduledThreadPoolExecutor` khuyến nghị thường không đặt `corePoolSize=0` hoặc bật timeout cho core threads vì có thể dẫn đến scheduler không có thread sẵn sàng xử lý task khi tới hạn. Đây là một tradeoff khác với executor chuyên chạy request bursts.

## 18. Shutdown: policy nào ảnh hưởng task định kỳ?

`ScheduledExecutorService` quản lý task, còn `ScheduledThreadPoolExecutor` có policy đặc biệt cho những lịch còn chờ:

- `setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean)`: quyết định các periodic task đã đăng ký **có được tiếp tục** sau `shutdown()` hay không. Mặc định **false**, nghĩa các periodic tasks hiện hữu không tiếp tục sau shutdown.
- `setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean)`: quyết định one-shot delayed tasks đã nhận **có được chạy** sau `shutdown()` hay không. Mặc định **true**.
- `setRemoveOnCancelPolicy(boolean)`: yêu cầu xóa ngay cancelled tasks khỏi queue theo policy của implementation, tránh giữ chúng tới thời hạn lịch.
- `shutdownNow()`: best effort interrupt các task running và trả những task chưa bắt đầu; không bảo đảm body đang chạy hợp tác dừng tức khắc.
- `awaitTermination(timeout, unit)`: đợi executor terminated sau shutdown; bản thân method không hủy/chạy thêm task.

Ví dụ phối hợp shutdown:

~~~java
// Snippet minh họa trong method có thể ném InterruptedException.
periodicFuture.cancel(true);
scheduler.shutdown();

if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
    scheduler.shutdownNow();
    scheduler.awaitTermination(5, TimeUnit.SECONDS);
}
~~~

Nếu bạn bật `continueExistingPeriodicTasksAfterShutdownPolicy(true)` mà không chủ động hủy lịch, scheduler có thể tiếp tục chạy periodic tasks và không terminated như kỳ vọng. Vì vậy **đừng cấu hình policy rồi quên quản lý handles**.

Khi cần dừng có trật tự, thường:

1. Ngừng đăng ký periodic tasks mới.
2. Chọn rõ task đang chạy được **đợi hoàn tất** hay bị **yêu cầu interrupt**.
3. Cancel các handles theo policy nghiệp vụ.
4. Gọi `shutdown()` và đợi bằng `awaitTermination` với timeout.
5. Dùng `shutdownNow()` cho nhánh escalation nếu cần, rồi ghi nhận trạng thái còn tồn tại.
6. Giải phóng I/O/database resources **sau khi đã xác nhận** task không còn dùng chúng hoặc có protocol đồng bộ shutdown.

Tín hiệu cancel thành công không tự chứng minh body đã dừng; nếu task đang block trong I/O không interruptible, có thể cần deadline/socket timeout hoặc cách dừng khác.

## 19. Khi periodic task tự submit tác vụ con: khả năng overlap gián tiếp

Một design rất dễ gây nhầm:

~~~java
// Snippet, không phải chương trình độc lập.
scheduler.scheduleAtFixedRate(() -> {
    workerPool.submit(this::longOperation);
}, 0, 100, TimeUnit.MILLISECONDS);
~~~

Scheduled Runnable chỉ thực hiện **submit rồi return** trong thời gian ngắn. Vì vậy scheduler **không overlap phần Runnable submit**, nhưng những task `longOperation` đã submit có thể **chạy song song** hoặc **tồn đọng** ở workerPool.

Hậu quả:

- `longOperation` có thể bị chạy đồng thời dù periodic scheduler tuân thủ no-overlap tuyệt đối.
- Nếu workerPool có queue không giới hạn, backlog tăng khi production rate vượt completion rate.
- Hủy `ScheduledFuture` chỉ dừng **những lần submit trong tương lai**, không tự hủy các task con đã được workerPool nhận.
- `shutdown()` scheduler không tự shutdown workerPool nếu ứng dụng tạo hai executor độc lập.
- Nếu task con thay đổi shared state, cần atomicity, idempotency, lock hoặc serial executor phù hợp.

Để tránh overlap nghiệp vụ, có thể **để toàn bộ xử lý trong scheduled Runnable** và dùng fixed delay; hoặc đặt gate như `Semaphore.tryAcquire()`/`AtomicBoolean.compareAndSet(false,true)` quanh toàn bộ asynchronous operation với release đúng một lần trong mọi nhánh. Nếu gate bị release quá sớm (ngay sau `submit`) thì vẫn overlap.

### Mẫu chống overlap với AtomicBoolean (minh họa)

~~~java
// Snippet cấu trúc trong một class có fields tương ứng.
AtomicBoolean inFlight = new AtomicBoolean();

void tick() {
    if (!inFlight.compareAndSet(false, true)) {
        return; // Bỏ lượt khi công việc trước chưa kết thúc.
    }

    try {
        doWorkSynchronously();
    } finally {
        inFlight.set(false);
    }
}
~~~

Mẫu này chỉ hợp nếu công việc hoàn tất ngay trong `doWorkSynchronously()`. Nếu thay bằng `workerPool.submit(...)` mà `finally` luôn release ngay khi submit trả về, gate trở nên vô nghĩa. Với async work, phải release sau khi **thực sự** hoàn tất hoặc fail/cancel, và bảo đảm không leak gate nếu task chưa bao giờ bắt đầu. Đây là bài toán lifecycle/ownership riêng.

## 20. Task có I/O lâu: thiết kế cancellation và timeout

“Không overlap” không giải quyết một job **bị treo vĩnh viễn**. Nếu một invocation chờ network response vô hạn:

- Fixed rate sẽ không tự tạo invocation mới để thay thế invocation đang kẹt.
- Fixed delay cũng không chạy lượt kế tiếp.
- Scheduler pool có thể mất một worker trong thời gian dài.
- Cancel(true) có thể không ngắt ngay một I/O API không interruptible.
- Các tasks độc lập khác vẫn có thể chạy nếu pool còn worker, nhưng tài nguyên chung có thể bị ảnh hưởng.

Checklist:

- Đặt socket/connect/read timeout hoặc API-specific deadline.
- Hỗ trợ interrupt ở các điểm có thể, đừng nuốt `InterruptedException`.
- Đặt try-with-resources/`finally` để dọn dẹp tài nguyên thuộc task.
- Không giữ một lock chung qua I/O quá lâu.
- Theo dõi **start time, duration, last successful run, error count, cancellation latency**.
- Nếu một invocation đã quá hạn, chọn rõ cancel, skip, retry/backoff hay báo lỗi.
- Đảm bảo operation idempotent nếu có khả năng retry sau timeout hoặc process restart.
- Không mặc định cancel là rollback side effect đã commit.

Với periodic callback nặng, nên tách phần “quyết định có cần làm gì” khỏi phần I/O thật, nhưng phải thiết kế lại điều phối work nếu tách thành asynchronous child tasks.

## 21. Fixed rate có tương đương cron hay một đồng hồ tuyệt đối không?

Không. `scheduleAtFixedRate` trong Java SE lập lịch theo **relative delays/periods**; nó không phải một lịch cron với calendar semantics như “00:00 mỗi ngày theo múi giờ X”. Những chủ đề khác:

- Timezone, DST và giờ địa phương nhảy lùi/tiến.
- Leap seconds, đồng bộ NTP, đổi thời gian hệ thống.
- Một lịch “ngày đầu tháng”, “thứ Hai 9 giờ” hay “sau mỗi ngày lịch”.
- Khởi động lại tiến trình, mất lịch khi JVM dừng, chạy lại những lượt bỏ lỡ.
- Có nhiều JVM/instances cùng chạy một job.

`ScheduledExecutorService` **không cung cấp distributed single-leader lock hoặc durable scheduling**. Nếu có nhiều JVM cùng đăng ký periodic job, mỗi JVM có thể chạy job riêng và gây overlap trên cùng dữ liệu ngoài hệ thống. Muốn một lịch business “chỉ chạy một lần trên toàn cluster”, cần protocol coordinator/lease/lock/durable scheduler ở tầng phù hợp; không được suy từ no-overlap **trong một periodic Future**.

Khi muốn bám lịch giờ tường, hãy tính thời điểm kế tiếp từ `ZonedDateTime`/calendar rule phù hợp, chuyển thành delay tương ứng và xử lý clock/holiday/DST theo yêu cầu; không chỉ đặt `period=24 hours` rồi mặc định đó là “mỗi ngày lúc 09:00” mãi mãi.

## 22. Quan sát và kiểm thử hành vi task chạy lâu

### A. Quan sát thực tế

Các metric hữu ích:

| Metric | Ý nghĩa |
| --- | --- |
| Last start / last finish | Biết invocation hiện tại có tiến triển |
| Duration từng lượt, p95/p99 | Nhìn thấy tác vụ nào vượt period/delay |
| Time since last successful finish | Phát hiện job âm thầm dừng vì exception |
| Number of failures | Phân biệt scheduler còn sống với job còn thành công |
| Late start/drift | Đo fixed-rate schedule có trễ so với mốc danh nghĩa |
| Missed business deadlines | Quan trọng hơn số tick trong JVM |
| Pool active threads / queue size | Nhìn thấy contention giữa các scheduled tasks khác nhau |
| Cancellation-to-stop latency | Future cancelled rồi body thực sự dừng sau bao lâu |
| Downstream saturation | Task định kỳ có tạo quá tải DB/API không |

Một periodic Future ở `isDone=false` không chứng minh “job vẫn hoạt động tốt”: nó có thể bị kẹt ở một invocation chưa kết thúc. Cần đo duration/last finish. Ngược lại, `isDone=true` chưa chắc là job thành công; có thể do exception hoặc cancellation.

### B. Dùng latch để test overlap thay vì đoán scheduling

Một test đáng tin nên:

1. Bắt đầu một invocation và giữ nó ở một điểm có thể kiểm soát.
2. Xác nhận invocation đã vào body bằng latch.
3. Dùng một worker khác/điều phối để tạo cơ hội chạy task độc lập.
4. Khi invocation đầu vẫn bị giữ, kiểm tra same-schedule run count không tăng.
5. Nhả latch hoặc cancel trong `finally` để không kẹt test.
6. Đợi executor termination (có timeout) trước khi rời test.
7. Tách xác minh **contract non-overlap** khỏi đo **timing jitter**.

Đừng viết test `Thread.sleep(110)` rồi giả định chắc chắn invocation thứ hai đã chạy hoặc OS phải schedule đúng 110 ms. Lập lịch không cung cấp deadline cứng, stress test chỉ hỗ trợ chứng minh trong điều kiện quan sát chứ không thay tài liệu API.

### C. Khi so sánh fixed rate và fixed delay

Không chỉ đếm số lần chạy trong 1 giây rồi kết luận. Nếu task có thời lượng thay đổi, số invocation phụ thuộc scheduler jitter, GC và resource contention. Hãy đo **thời điểm start/finish** bằng monotonic clock (ví dụ `System.nanoTime()`) và tính:

- Với fixed rate: khoảng cách giữa các start so với period danh nghĩa.
- Với fixed delay: khoảng cách giữa **finish trước** và **start sau** so với delay.
- Với cả hai: liệu có trường hợp cùng periodic Future có hai invocations active không; theo contract là không.
- Lượng công việc mới do mỗi invocation sinh ra, đặc biệt khi có child tasks/worker queues.

## 23. Sai lầm phổ biến

| Sai lầm | Vì sao nguy hiểm / sửa thế nào |
| --- | --- |
| “Fixed rate sẽ chạy chồng cùng task khi bị trễ.” | Không, các successive executions trong cùng periodic schedule không overlap. |
| “Pool có 8 worker nên 8 lần của cùng task chạy đồng thời.” | Pool có thể chạy **tám task khác nhau**, không vi phạm no-overlap từng schedule. |
| “Fixed delay là 100 ms giữa hai thời điểm bắt đầu.” | Delay tính từ **finish lần trước** tới lần sau đủ điều kiện. |
| “Fixed rate luôn chờ đúng period sau khi xong.” | Khi đã trễ, lượt sau có thể bắt đầu rất sớm, gần như ngay sau lần trước. |
| “Mỗi period quá hạn tạo một bản sao task chạy song song.” | Không phải hợp đồng của hai periodic scheduling methods. |
| “Hai lời gọi schedule của cùng Runnable là một task.” | Chúng là hai schedules độc lập, có thể overlap nhau. |
| “Lượt trước chạy trên worker-1 thì lượt sau cũng vậy.” | Implementation có thể dùng worker khác; không dựa vào ThreadLocal xuyên lượt. |
| “No-overlap scheduler cũng bảo vệ task con asynchronous.” | Không; task con có lifecycle riêng và có thể chồng/chờ trong pool khác. |
| “Periodic Future.get() trả về sau một lượt chạy.” | Periodic schedule bình thường không có normal result sau mỗi lần. |
| “Job ném exception nhưng scheduler sẽ tiếp tục chạy lượt sau.” | Uncaught exception làm các lần kế tiếp bị suppress. |
| “Future bị cancel là body đã dừng sạch.” | Cancel không bảo đảm worker đã dừng tức thì hay side effect được rollback. |
| “Tăng maximumPoolSize của ScheduledThreadPoolExecutor.” | Với delayed unbounded queue, max pool setting không hữu ích như nhiều người kỳ vọng. |
| “shutdown() luôn dừng mọi lịch one-shot và periodic như nhau.” | Delayed và periodic tasks có policy tiếp tục khác nhau. |
| “Fixed rate 24h là cron 9:00 mỗi ngày.” | Calendar/DST và relative period là các bài toán khác. |
| “Mỗi JVM đều no-overlap nghĩa toàn cluster không overlap.” | Không có distributed coordination trong ScheduledExecutorService. |
| “Không có exception là job đang khỏe.” | Task có thể bị treo; cần theo dõi last successful finish và duration. |

## 24. Câu hỏi tự kiểm tra

**Câu 1.** Tác vụ fixed-rate có period 100 ms, mỗi execution mất 250 ms. Hai invocation của cùng một `ScheduledFuture` có đồng thời chạy không?  
**Đáp án:** Không. Lượt tiếp theo có thể trễ và bắt đầu sát sau lượt trước, nhưng không overlap.

**Câu 2.** Với mô hình lý tưởng trên, fixed rate và fixed delay 100 ms cho các mốc start nào?  
**Đáp án:** Fixed rate có thể bắt đầu tại 0, 250, 500, 750 ms; fixed delay bắt đầu tại 0, 350, 700, 1050 ms nếu mỗi lượt luôn 250 ms.

**Câu 3.** Một periodic task fixed-delay có duration 250 ms và delay 100 ms. Khoảng cách giữa hai lần **bắt đầu** có ít nhất bằng 100 ms không?  
**Đáp án:** Có, nhưng thường khoảng cách là duration trước + delay + scheduling overhead, xấp xỉ 350 ms trong mô hình lý tưởng.

**Câu 4.** `Executors.newScheduledThreadPool(4)` có cho phép task A và B chạy đồng thời không?  
**Đáp án:** Có thể, nếu là **hai schedules khác nhau** và có worker sẵn có. Không làm A overlap với lần A kế tiếp.

**Câu 5.** Hai lần `scheduleAtFixedRate(sameRunnable, ...)` có tạo một periodic schedule duy nhất không?  
**Đáp án:** Không. Hai Future độc lập có thể gọi cùng Runnable đồng thời.

**Câu 6.** Một execution ném `RuntimeException` thoát khỏi Runnable. Lượt kế tiếp có tự chạy tiếp không?  
**Đáp án:** Không. Các executions tiếp theo bị suppress; Future kết thúc exceptional và `get()` ném `ExecutionException`.

**Câu 7.** Một periodic Future chạy được 10 lần và chưa cancel. `get()` có trả bình thường không?  
**Đáp án:** Không. Periodic Future đại diện toàn lịch, thường chưa hoàn tất bình thường.

**Câu 8.** `cancel(true)` được gọi lúc invocation đang chờ network I/O. Có chắc network call dừng ngay không?  
**Đáp án:** Không. Chỉ cố interrupt, cần I/O timeout và cancellation cooperative.

**Câu 9.** Fixed rate có thể tạo áp lực lớn cho downstream khi một execution bị trễ không?  
**Đáp án:** Có, các invocation sau có thể chạy rất sát nhau; fixed delay chờ thêm sau mỗi finish.

**Câu 10.** Nếu Runnable định kỳ chỉ `submit(longTask)` rồi return, có còn bảo đảm `longTask` không overlap không?  
**Đáp án:** Không. Scheduler chỉ không overlap invocation của Runnable, còn child tasks thuộc pool khác có thể đồng thời.

**Câu 11.** `setContinueExistingPeriodicTasksAfterShutdownPolicy(true)` có thể ảnh hưởng gì?  
**Đáp án:** Periodic tasks hiện hữu có thể tiếp tục sau `shutdown`; cần chủ động cancel để scheduler có thể terminated.

**Câu 12.** Fixed rate period 24 giờ có giống “mỗi ngày 09:00 tại timezone X” không?  
**Đáp án:** Không. Một period tương đối không thay calendar rules/DST.

## Tài liệu tham khảo Java SE

- [`ScheduledExecutorService` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): `scheduleAtFixedRate`/`scheduleWithFixedDelay`, initial delay, period/delay, exception suppression.
- [`ScheduledThreadPoolExecutor` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): successive executions không overlap, happens-before, core pool, remove-on-cancel, shutdown policies.
- [`ScheduledFuture` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): handle cho delayed/periodic job.
- [`Future` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `cancel`, `isDone`, `get`.
- [`Executors` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html): factory `newScheduledThreadPool`.
- [`TimeUnit` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeUnit.html): chuyển đổi và biểu diễn thời gian.
- [`CountDownLatch` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối test xác định.

**Ghi nhớ:** `scheduleAtFixedRate` giữ nhịp **mốc bắt đầu dự kiến**, nên khi bị trễ các lượt có thể nối sát nhau; `scheduleWithFixedDelay` luôn đợi thêm **delay sau khi lượt trước kết thúc**. **Cả hai không tự chạy chồng các lần thực thi của cùng periodic task**, nhưng các schedules khác, các child tasks bất đồng bộ hoặc các JVM khác vẫn có thể cạnh tranh tài nguyên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-and-scheduling-019">TimeUnit: chuyển đơn vị, độ chính xác và ngân sách thời gian qua nhiều bước</a>

<details>
<summary>Xem chi tiết</summary>

# TimeUnit: chuyển đơn vị, độ chính xác và ngân sách thời gian qua nhiều bước

## 1. TimeUnit giải quyết vấn đề gì trong Java Concurrency?

Các API concurrency thường cần biểu diễn **thời gian chờ**, **timeout**, **độ trễ lên lịch** và **thời hạn kết thúc**. Những con số như `1000` không đủ rõ nghĩa: đó là mili giây, micro giây hay nano giây?

Java cung cấp enum `java.util.concurrent.TimeUnit` để truyền một giá trị `long` **cùng đơn vị thời gian**. Ví dụ:

```java
future.get(2, TimeUnit.SECONDS);
latch.await(500, TimeUnit.MILLISECONDS);
lock.tryLock(250, TimeUnit.MICROSECONDS);
executor.awaitTermination(5, TimeUnit.SECONDS);
scheduler.schedule(task, 100, TimeUnit.MILLISECONDS);
```

Các đoạn trên là **minh họa API**; `future`, `latch`, `lock`, `executor`, `scheduler` và `task` được khai báo phù hợp trong ứng dụng. Đọc `2, SECONDS` rõ hơn một số `2000` không được đặt tên đơn vị.

Nhưng chỉ dùng TimeUnit chưa đủ để viết code timeout đúng. Ba nhóm vấn đề phải phân biệt:

1. **Chuyển đơn vị:** `TimeUnit.convert`, `toNanos`, `toMillis` có thể **làm tròn về 0 khi chuyển sang đơn vị thô hơn**, hoặc **bão hòa** nếu kết quả vượt `long`.
2. **Độ chính xác thực tế:** khai báo timeout ở mức nano giây **không bảo đảm thread được đánh thức đúng nano giây đó**; JVM, hệ điều hành, scheduler và tải CPU ảnh hưởng thời điểm thực thi.
3. **Ngân sách thời gian qua nhiều bước:** nếu một workflow có tổng tối đa 1 giây, mỗi bước không được tự dùng lại **đầy đủ 1 giây**. Cần đo thời gian **đã trôi qua** bằng một đồng hồ phù hợp, rồi truyền **phần còn lại** cho bước sau.

Đây là kiến thức của **Future & Scheduling** trong **Java concurrency nói chung**, không phụ thuộc framework.

## 2. Các đơn vị được TimeUnit hỗ trợ

`TimeUnit` là một `enum` với các hằng:

| Hằng số | Viết tắt thường dùng | Quan hệ |
| --- | --- | --- |
| `NANOSECONDS` | ns | 1 nano giây |
| `MICROSECONDS` | µs | 1 µs = 1.000 ns |
| `MILLISECONDS` | ms | 1 ms = 1.000 µs |
| `SECONDS` | s | 1 s = 1.000 ms |
| `MINUTES` | min | 1 phút = 60 s |
| `HOURS` | h | 1 giờ = 60 phút |
| `DAYS` | d | 1 ngày = 24 giờ |

Các đơn vị `DAYS`, `HOURS`, `MINUTES` trong `TimeUnit` là **khoảng thời gian có độ dài cố định**, không tính lịch, múi giờ hay daylight saving time. Ví dụ `DAYS.toHours(1)` luôn là `24`, không phải “số giờ thực tế của một ngày địa phương có thể đổi giờ”.

Để biểu diễn **thời điểm lịch**, dùng `Instant`, `ZonedDateTime` hoặc kiểu `java.time` phù hợp. Để biểu diễn **khoảng chờ/timeout** trong concurrency, `TimeUnit` kết hợp đồng hồ đo thời gian trôi qua là lựa chọn quen thuộc.

## 3. Chuyển đơn vị với `convert` và `toX`

Có hai cách diễn đạt tương đương về mục tiêu:

```java
long millis = TimeUnit.MILLISECONDS.convert(3, TimeUnit.SECONDS);
long same = TimeUnit.SECONDS.toMillis(3);
```

Cả hai cho `3000`.

**Thứ tự tham số của `convert`:**

```java
targetUnit.convert(value, sourceUnit);
```

Điều này có nghĩa **đơn vị nhận lời gọi** là đơn vị đích. Ví dụ:

```java
long seconds = TimeUnit.SECONDS.convert(
        1500, TimeUnit.MILLISECONDS);
```

`seconds == 1`, chứ không phải `1.5`, vì kết quả là `long` nguyên.

Một số hàm tiện ích:

- `TimeUnit.SECONDS.toMillis(5)` → `5000`.
- `TimeUnit.MILLISECONDS.toNanos(2)` → `2_000_000`.
- `TimeUnit.MINUTES.toSeconds(3)` → `180`.
- `TimeUnit.NANOSECONDS.toMillis(999_999)` → `0`.
- `TimeUnit.SECONDS.convert(1_999, TimeUnit.MILLISECONDS)` → `1`.

**Không có đơn vị thập phân trong kết quả `long`.** Nếu cần giữ độ chính xác nano giây, nên giữ giá trị ở đơn vị đủ mịn cho tới lúc gọi API nhận timeout.

### Ví dụ 1: chuyển đơn vị, cắt phần dư và bão hòa

Tạo tệp `TimeUnitConversionDemo.java`:

```java
import java.util.concurrent.TimeUnit;

public class TimeUnitConversionDemo {
    public static void main(String[] args) {
        System.out.println("3s to ms="
                + TimeUnit.SECONDS.toMillis(3));
        System.out.println("1500ms to s="
                + TimeUnit.SECONDS.convert(
                        1500, TimeUnit.MILLISECONDS));
        System.out.println("999999ns to ms="
                + TimeUnit.NANOSECONDS.toMillis(999_999));
        System.out.println("-1500ms to s="
                + TimeUnit.SECONDS.convert(
                        -1500, TimeUnit.MILLISECONDS));
        System.out.println("23h to days="
                + TimeUnit.DAYS.convert(
                        23, TimeUnit.HOURS));
        System.out.println("huge days to nanos="
                + TimeUnit.DAYS.toNanos(Long.MAX_VALUE));
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac TimeUnitConversionDemo.java
java TimeUnitConversionDemo
```

Kết quả:

```text
3s to ms=3000
1500ms to s=1
999999ns to ms=0
-1500ms to s=-1
23h to days=0
huge days to nanos=9223372036854775807
```

Giải thích:

1. `3s` chuyển sang ms chính xác là `3000`.
2. `1500ms` chuyển sang giây nguyên thành `1`, **mất phần 500ms**.
3. `999_999ns` nhỏ hơn 1ms, chuyển sang mili giây thành `0`.
4. `-1500ms` chuyển sang giây nguyên thành `-1`: Java chia số nguyên theo hướng **về 0**, không phải luôn làm tròn xuống âm vô cực.
5. `23h` chưa đủ một ngày cố định 24 giờ, nên `toDays` là `0`.
6. `Long.MAX_VALUE` ngày chuyển sang nano giây vượt phạm vi `long`. `TimeUnit` trả giá trị **bão hòa (saturation)** ở `Long.MAX_VALUE`, không wrap thành số âm.

Không nên lấy kết quả `toMillis(...) == 0` và kết luận “không còn thời gian” nếu ban đầu vẫn còn hàng trăm nghìn nano giây. **Đơn vị quá thô** đã xóa mất phần dư trong phép đổi số nguyên.

## 4. Conversion truncation: trường hợp gây bug timeout

Giả sử một deadline chỉ còn `800_000 ns` = `0.8 ms`.

```java
long remainingNanos = 800_000;
long remainingMillis =
        TimeUnit.NANOSECONDS.toMillis(remainingNanos);
// remainingMillis == 0, dù thời gian còn lại dương.
```

Nếu code kiểm tra `if (remainingMillis <= 0)`, nó có thể từ chối một bước còn khả năng hoàn thành trong `0.8ms`. Nếu gọi API:

```java
future.get(remainingMillis, TimeUnit.MILLISECONDS);
```

thì `get(0, MILLISECONDS)` hoạt động như một phép kiểm tra kết quả ngay, thay vì đợi `0.8ms`.

**Cách giữ độ phân giải:** truyền đúng phần còn lại ở nanoseconds:

```java
long nanos = budget.remainingNanos();
if (nanos <= 0) {
    throw new TimeoutException("budget exhausted");
}
return future.get(nanos, TimeUnit.NANOSECONDS);
```

Đây là **đoạn minh họa** trong method có `budget`, `future`, `TimeoutException` và khai báo checked exceptions phù hợp. Dù truyền nano giây, độ chính xác **thức dậy thực tế** vẫn phụ thuộc scheduler; ưu điểm là không mất độ phân giải **do chính phép chuyển đơn vị**.

### Cẩn thận với negative timeout

Giá trị thời gian âm thường không có ý nghĩa “đợi âm một giây”. Nhiều API timeout hiểu khoảng chờ không dương là không chờ, nhưng hợp đồng mỗi API có thể khác. Không nên dựa vào các giá trị âm truyền vào `Future.get`, `Condition.await` hoặc `Lock.tryLock` như một cơ chế timeout chuẩn chung.

Hãy chuẩn hóa tại biên:

```java
long remaining = Math.max(0L, deadlineNanos - nowNanos);
```

Đây là mã minh họa; phép trừ thô còn phải xét overflow và điểm gốc của `System.nanoTime()`. Phần sau xây dựng cách tính dựa trên **elapsed time** an toàn cho các deadline thực tế.

## 5. Bão hòa số nguyên và giới hạn long

`TimeUnit` dùng `long` và trong những phép chuyển sang đơn vị nhỏ hơn, nó **bão hòa** về `Long.MAX_VALUE` hoặc `Long.MIN_VALUE` khi số học cần vượt giới hạn. Đây là hành vi hữu ích vì không chuyển một timeout khổng lồ thành giá trị âm vô nghĩa.

Ví dụ:

```java
long nanos = TimeUnit.DAYS.toNanos(Long.MAX_VALUE);
// nanos == Long.MAX_VALUE
```

Nhưng cần phân biệt với các phép số học khác:

```java
long nanos = hugeSeconds * 1_000_000_000L;
```

Dòng thứ hai **không tự bão hòa**; phép nhân `long` có thể overflow và wrap. Ưu tiên `TimeUnit.SECONDS.toNanos(hugeSeconds)` nếu mục đích chỉ là chuyển đơn vị.

### So sánh với java.time.Duration

`Duration` mô tả khoảng thời gian theo giây và nano giây với độ phân giải chi tiết. Nó thích hợp ở tầng API cấu hình, deadline, logging hoặc xử lý thời gian chung.

```java
Duration duration = Duration.ofMillis(1500);
long nanos = duration.toNanos();
long millis = duration.toMillis();
```

Đây là đoạn minh họa, cần import `java.time.Duration`. `nanos` là `1_500_000_000` và `millis` là `1500`.

Khác `TimeUnit` có quy tắc saturation ở các phép đổi liên quan, một số phép chuyển `Duration` như `toNanos()` có thể ném `ArithmeticException` nếu giá trị không biểu diễn được trong `long`. **Không giả định hai API có cùng hợp đồng overflow**.

Khi dùng `Duration` để nhập thời hạn người dùng, hãy xác nhận giá trị **không âm**, có nằm trong ngưỡng hệ thống cho phép không và phép đổi cần thiết có overflow hay không.

## 6. TimeUnit không phải đồng hồ

Một câu lệnh:

```java
TimeUnit.MILLISECONDS.sleep(10);
```

yêu cầu thread ngủ **ít nhất theo cơ chế chờ mà JVM/OS hỗ trợ**, không bảo đảm code tiếp tục chính xác ngay ở mốc `10ms`. Sau khi thời gian trôi qua, thread còn phụ thuộc thời điểm scheduler cho chạy lại.

Phân biệt hai khái niệm:

- **Độ phân giải biểu diễn (resolution):** số lượng nhỏ nhất mà kiểu/đơn vị mô tả được; `NANOSECONDS` biểu diễn tới 1ns.
- **Độ chính xác thời gian chạy (accuracy):** sự kiện thực tế xảy ra gần deadline tới đâu; phụ thuộc hệ điều hành, JVM, độ phân giải timer, tải máy, GC pause, contention, CPU scheduling và đặc điểm API.

Chọn `TimeUnit.NANOSECONDS` không có nghĩa thread sẽ thức dậy hay lịch sẽ chạy ở chính xác từng nano giây.

Đặc biệt, `Thread.sleep`, `TimeUnit.sleep` và một số wait có thể bị **interrupt**; các API có thể ném `InterruptedException`. Khi bị interrupt, phải xử lý tín hiệu hủy đúng cách thay vì catch rỗng rồi lặp vô hạn.

### Ba API tiện ích của TimeUnit

`TimeUnit` còn có:

```java
TimeUnit.MILLISECONDS.sleep(20);
TimeUnit.SECONDS.timedJoin(thread, 2);
TimeUnit.MILLISECONDS.timedWait(monitor, 200);
```

Đây là **các đoạn minh họa độc lập**. `thread` phải là `Thread` hợp lệ; với `timedWait`, caller phải đang giữ **monitor của chính `monitor`** giống khi gọi `monitor.wait(...)`, nếu không có thể nhận `IllegalMonitorStateException`. Không dùng `timedWait` thay cho một giao thức condition chuẩn. Điều kiện chờ phải được kiểm tra trong vòng `while` vì spurious wakeups và khả năng điều kiện thay đổi khi thread được đánh thức.

## 7. Đồng hồ đo elapsed time: `System.nanoTime()`

Khi cần tính **ngân sách còn lại**, không nên lấy hai lần `System.currentTimeMillis()` rồi trừ tùy tiện. Đồng hồ wall-clock có thể bị điều chỉnh do NTP, người dùng đổi giờ hoặc thay đổi hệ thống.

`System.nanoTime()` dành cho **đo khoảng thời gian trôi qua**. Giá trị của nó có một điểm gốc tùy ý; **không phải Unix timestamp**, không mang ngày giờ và có thể âm.

Mẫu:

```java
long startedAt = System.nanoTime();
// Thực hiện một bước.
long elapsed = System.nanoTime() - startedAt;
```

`elapsed` là số nano giây đo được từ điểm bắt đầu đến hiện tại. Với các khoảng đo hợp lý nhỏ hơn khoảng `2^63 ns` (xấp xỉ 292 năm), phép trừ này là mẫu Java thường dùng ngay cả khi biểu diễn `long` tràn theo vòng. Không nên dùng một số `nanoTime()` như thời điểm tuyệt đối để lưu database hoặc so sánh giữa các JVM.

### Khác biệt với Instant

- `Instant.now()`: thời điểm trên đồng hồ thực, phù hợp lưu timestamp/log khi sự kiện xảy ra.
- `System.nanoTime()`: đo duration/timeout trong cùng JVM, phù hợp tính elapsed budget.
- `TimeUnit`: **đơn vị** của một khoảng thời gian; không cung cấp một đồng hồ riêng.

Có thể log `Instant.now()` cho con người nhưng dùng `nanoTime()` để quyết định deadline của một retry loop. Không cộng số nanoTime vào epoch millis rồi cho rằng hai đồng hồ cùng hệ quy chiếu.

## 8. Ngân sách chung: không restart timeout cho từng bước

Giả sử một request có **tổng ngân sách 500ms**, gồm ba bước:

1. Chờ lock.
2. Chờ dữ liệu sẵn sàng.
3. Chờ kết quả `Future`.

**Sai:** mỗi bước gọi API với đủ `500ms`:

```java
lock.tryLock(500, TimeUnit.MILLISECONDS);
ready.await(500, TimeUnit.MILLISECONDS);
future.get(500, TimeUnit.MILLISECONDS);
```

Đây là **phản ví dụ về ngân sách**, chưa xét unlock, exceptions và trạng thái return của từng API. Mỗi bước có thể đợi gần `500ms`, khiến tổng latency gần **1500ms** cộng overhead. Càng nhiều bước, deadline end-to-end càng bị kéo dài.

**Đúng ý tưởng:** chỉ có **một start time và một budget**, sau mỗi bước tính lại **remaining time**:

```text
Budget tổng: 500ms
Start                   500ms còn
Bước 1 sử dụng 300ms    200ms còn
Bước 2 sử dụng 150ms     50ms còn
Bước 3 chỉ được dùng tối đa 50ms
Hết 500ms => không khởi tạo một lần chờ 500ms mới.
```

Không chia cố định mỗi bước 166ms nếu không có nhu cầu riêng: một bước hoàn thành nhanh thì bước sau có thể sử dụng phần chưa tiêu. Đây là **end-to-end deadline** chứ không phải timeout tĩnh cho từng phần.

## 9. Một TimeBudget dùng elapsed time và LongSupplier

Để test chính xác logic chia ngân sách mà không dựa vào scheduler, thiết kế một helper nhận **đồng hồ nano giây** qua `LongSupplier`. Trong production sử dụng `System::nanoTime`; trong test dùng đồng hồ giả có thể tiến lên theo ý muốn.

Tạo tệp `TimeBudgetDemo.java`:

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class TimeBudgetDemo {
    static final class Budget {
        private final LongSupplier clock;
        private final long started;
        private final long allowedNanos;

        Budget(long timeout, TimeUnit unit, LongSupplier clock) {
            if (timeout < 0) {
                throw new IllegalArgumentException("negative timeout");
            }
            this.clock = clock;
            this.started = clock.getAsLong();
            this.allowedNanos = unit.toNanos(timeout);
        }

        long remainingNanos() {
            long elapsed = clock.getAsLong() - started;
            if (elapsed <= 0) {
                return allowedNanos;
            }
            return Math.max(0L, allowedNanos - elapsed);
        }

        long remainingMillis() {
            return TimeUnit.NANOSECONDS.toMillis(remainingNanos());
        }
    }

    public static void main(String[] args) {
        AtomicLong fakeNanos = new AtomicLong(0);
        Budget budget = new Budget(
                500, TimeUnit.MILLISECONDS, fakeNanos::get);

        System.out.println("at 0ms=" + budget.remainingMillis());

        fakeNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(300));
        System.out.println("at 300ms=" + budget.remainingMillis());

        fakeNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(150));
        System.out.println("at 450ms=" + budget.remainingMillis());

        fakeNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(60));
        System.out.println("at 510ms=" + budget.remainingMillis());
    }
}
```

```bash
javac TimeBudgetDemo.java
java TimeBudgetDemo
```

Kết quả hoàn toàn xác định:

```text
at 0ms=500
at 300ms=200
at 450ms=50
at 510ms=0
```

Ví dụ **không ngủ** và không dựa vào hiệu năng máy. `AtomicLong` được dùng làm đồng hồ mô phỏng để minh họa chính xác phép tính.

**Đưa vào production:** tạo `new Budget(timeout, unit, System::nanoTime)` và gọi `remainingNanos()` ngay trước mỗi bước chờ. Cần lưu ý `LongSupplier` test phải tiến lên theo đúng đơn vị nano giây, và phép trừ `nanoTime` dựa trên giả định khoảng đo thực tế nhỏ hơn khoảng tràn `long` khổng lồ (khoảng 292 năm).

**Không dùng `remainingMillis()==0` làm điều kiện hết hạn:** nó có thể là `0` dù `remainingNanos() > 0` do truncation. Dùng nanos cho logic hết hạn, chỉ chuyển sang ms khi **hiển thị**.

## 10. Helper TimeBudget có xử lý overflow như thế nào?

Ở ví dụ, `unit.toNanos(timeout)` áp dụng saturation của `TimeUnit` khi số lượng quá lớn. `allowedNanos` có thể là `Long.MAX_VALUE` nếu yêu cầu timeout vượt khả năng biểu diễn nano giây. Với ngân sách hữu hạn thông thường, điều đó không ảnh hưởng.

Để dùng cho hệ thống thật, nên đặt **ngưỡng timeout hợp lý** (chẳng hạn vài giây/phút/giờ tùy nghiệp vụ), thay vì chấp nhận `Long.MAX_VALUE` nano giây và vô tình tạo một phép đợi gần như không giới hạn.

Phép tính:

```java
long elapsed = System.nanoTime() - started;
long remaining = Math.max(0L, allowedNanos - elapsed);
```

được ưa dùng hơn so sánh trực tiếp hai giá trị `deadline` tuyệt đối thiếu kiểm soát. Tuy vậy, nó vẫn có những giả định thời gian thông thường:

- Clock đo elapsed phải ổn định và không tự nhảy theo wall-clock.
- Không dùng helper để xử lý khoảng thời gian hàng thế kỷ vượt phạm vi diễn giải chênh lệch `long`.
- Không trộn giá trị `System.currentTimeMillis()` vào phép trừ `System.nanoTime()`.
- Khi còn đúng `0`, có thể chọn policy fail fast; kết quả của các bước đã hoàn tất không tự biến mất.
- Nếu cần delay dài bất thường, quản lý theo mốc lịch và cơ chế scheduler/renewal phù hợp thay vì giữ một phép chờ một lần cực lớn.

## 11. Truyền remaining budget vào Future.get()

Mẫu quan trọng cho nhiều bước:

```java
long remaining = budget.remainingNanos();
if (remaining <= 0) {
    throw new TimeoutException("request budget exhausted");
}
T result = future.get(remaining, TimeUnit.NANOSECONDS);
```

Đây là **đoạn minh họa** trong method generic có khai báo checked exceptions; `budget` là helper tính thời gian còn lại và `future` là `Future<T>`.

`Future.get(timeout, unit)` có những kết quả cần tách biệt:

- **Thành công:** trả `T`.
- **Task fail:** ném `ExecutionException`; kiểm tra `getCause()`.
- **Future bị hủy:** ném `CancellationException`.
- **Thread caller bị ngắt khi chờ:** ném `InterruptedException`.
- **Không hoàn thành trước timeout chờ:** ném `TimeoutException`.

**Rất quan trọng:** một `TimeoutException` từ `Future.get(timeout)` **không tự hủy task**. Task có thể còn chạy và tiếp tục chiếm CPU/I/O. Nếu policy request muốn dừng task sau deadline, cân nhắc `future.cancel(true)` và hiểu đó chỉ là yêu cầu interrupt hợp tác.

### Ví dụ 3: timeout không tự cancel task

Tạo tệp `FutureWaitTimeoutDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureWaitTimeoutDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> future = executor.submit(() -> {
                started.countDown();
                release.await();
                return "DONE";
            });

            started.await();
            try {
                future.get(30, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("caller timeout");
            }

            System.out.println("cancelled=" + future.isCancelled());
            release.countDown();
            System.out.println("later=" + future.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
```

```bash
javac FutureWaitTimeoutDemo.java
java FutureWaitTimeoutDemo
```

Kết quả khi không có interrupt ngoài ý muốn:

```text
caller timeout
cancelled=false
later=DONE
```

Task đã phát `started` nhưng đang chờ `release` chưa mở; `get(30ms)` phải timeout vì task chưa thể hoàn tất. `isCancelled()` vẫn `false` vì timeout **không gọi cancel**. Sau khi mở latch, task trả kết quả và lần `get()` tiếp theo thành công.

Trong ứng dụng thật, đừng lạm dụng `future.get()` **không timeout** sau một lần timeout tổng nếu SLA yêu cầu không chờ thêm. Ví dụ cố ý làm vậy để chứng minh timeout **không hủy**. Với end-to-end deadline, hãy dừng đợi hoặc dùng `cancel(true)` theo policy.

## 12. Dùng một budget cho Future, CountDownLatch và Lock

Các primitive đồng bộ có chữ ký tương tự nhưng kết quả khác nhau:

```java
future.get(remaining, TimeUnit.NANOSECONDS);           // T hoặc exception
latch.await(remaining, TimeUnit.NANOSECONDS);          // boolean
lock.tryLock(remaining, TimeUnit.NANOSECONDS);         // boolean
condition.await(remaining, TimeUnit.NANOSECONDS);      // boolean
executor.awaitTermination(remaining, TimeUnit.NANOSECONDS); // boolean
```

Đây là các đoạn minh họa, `remaining` phải được **tính mới ngay trước từng lời gọi**, và các biến thuộc đúng loại. **Không** tính remaining một lần rồi truyền lặp cho mọi bước.

Ví dụ flow thực tế:

```text
Clock bắt đầu ở t0; budget = 800ms.

Bước A: latch.await(remainingNanos, NANOSECONDS)
          nếu false: request timeout
          nếu true:  tính remaining mới

Bước B: future.get(remainingNanos, NANOSECONDS)
          nếu TimeoutException: request timeout
          nếu ExecutionException: task thất bại
          nếu success: tính remaining mới

Bước C: lock.tryLock(remainingNanos, NANOSECONDS)
          nếu false: request timeout
          nếu true: xử lý critical section ngắn, unlock finally
```

**Cảnh báo về deadlock:** tránh giữ lock trong lúc chờ một Future/latch nếu task đang chờ **cần chính lock đó** để tiến triển. Có thể chờ điều kiện/dữ liệu trước, chỉ khóa ở cuối để commit ngắn; phải bảo vệ invariant của kết quả.

Khi một bước chặn trả về do **interrupt**, thông điệp đó khác timeout. Với `InterruptedException`, truyền tiếp exception hoặc khôi phục cờ nếu không thể propagate và dừng ở điểm an toàn. Không “reset budget” do interrupt rồi cố chờ thêm toàn bộ timeout.

## 13. Ví dụ 4: end-to-end deadline với hai bước chờ

Chương trình dưới có một budget chung cho **hai latch**. Cả hai được mở sẵn để minh họa nhánh thành công và không dựa vào timing của worker. Phần tính deadline vẫn sử dụng `System.nanoTime()` thực.

Tạo tệp `SharedDeadlineDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SharedDeadlineDemo {
    static long remainingNanos(long startedAt, long budgetNanos) {
        long elapsed = System.nanoTime() - startedAt;
        return Math.max(0L, budgetNanos - elapsed);
    }

    static boolean awaitWithinBudget(
            CountDownLatch first,
            CountDownLatch second,
            long timeout,
            TimeUnit unit) throws InterruptedException {

        long startedAt = System.nanoTime();
        long budgetNanos = unit.toNanos(timeout);

        long remaining = remainingNanos(startedAt, budgetNanos);
        if (remaining <= 0
                || !first.await(remaining, TimeUnit.NANOSECONDS)) {
            return false;
        }

        remaining = remainingNanos(startedAt, budgetNanos);
        if (remaining <= 0
                || !second.await(remaining, TimeUnit.NANOSECONDS)) {
            return false;
        }

        return true;
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch first = new CountDownLatch(0);
        CountDownLatch second = new CountDownLatch(0);

        boolean finished = awaitWithinBudget(
                first, second, 5, TimeUnit.SECONDS);
        System.out.println("finished=" + finished);
    }
}
```

```bash
javac SharedDeadlineDemo.java
java SharedDeadlineDemo
```

Kết quả trên một môi trường chạy bình thường khi hai bước rất ngắn hoàn thành trong năm giây:

```text
finished=true
```

Vì hai latch đã ở `count=0` nên `await` trả về ngay. Nhưng nếu JVM bị dừng/lên lịch chậm đến mức **vượt deadline giữa hai lần kiểm tra**, kết quả có thể là `false` theo đúng policy. Không bịa bảo đảm rằng một chương trình luôn hoàn tất trước một deadline vật lý chỉ vì tác vụ thông thường rất nhanh.

**Ý chính của code:** `startedAt` và `budgetNanos` được tạo **một lần**. Mỗi `await` nhận phần còn lại được tính mới từ cùng `startedAt`, không được thêm trọn vẹn `5 seconds` lần thứ hai.

## 14. Chờ Lock bằng time budget: quyền sở hữu và finally

`ReentrantLock.tryLock(timeout, unit)` chờ trong một khoảng có giới hạn, trả `true` khi **đã sở hữu lock**; trả `false` nếu hết thời hạn chờ mà không chiếm được khóa; có thể ném `InterruptedException` khi bị ngắt.

Mẫu:

```java
long remaining = budget.remainingNanos();
if (remaining <= 0) {
    throw new TimeoutException("no budget for lock");
}

if (!lock.tryLock(remaining, TimeUnit.NANOSECONDS)) {
    throw new TimeoutException("lock unavailable");
}
try {
    updateState();
} finally {
    lock.unlock();
}
```

Đây là đoạn minh họa trong phương thức khai báo throws phù hợp. `unlock()` chỉ được gọi **sau khi tryLock trả true**. Nếu trả false hoặc ném `InterruptedException`, caller chưa lấy được lock qua lời gọi đó, nên không được unlock vô điều kiện.

### Độ trễ critical section cũng tiêu tốn ngân sách

Ngay cả khi chiếm lock thành công ở giây thứ 4.9 trong ngân sách 5 giây, phần xử lý tiếp theo vẫn có thể mất thêm thời gian. `tryLock(timeout)` chỉ giới hạn **thời gian chờ lấy lock**, không đặt timer cưỡng chế chấm dứt code bên trong critical section.

Nếu cần SLA end-to-end nghiêm ngặt, kiểm tra `budget.remainingNanos()` trước phần có thể bắt đầu và chọn điểm hủy an toàn. Nhưng không bỏ dở một cập nhật nhiều trường trong trạng thái hỏng chỉ để đáp ứng deadline; phải thiết kế đơn vị commit/rollback.

## 15. `awaitTermination` cũng phải chia sẻ shutdown budget

Một ứng dụng có thể đóng executor hai giai đoạn:

1. `shutdown()`, đợi graceful.
2. Nếu chưa xong, `shutdownNow()`, đợi thêm.
3. Nếu vẫn chưa xong, báo chưa terminated.

Nếu caller có deadline **tổng là 8 giây**, không nên cứ dùng `awaitTermination(8, SECONDS)` ở cả hai pha; tổng có thể lên tới khoảng 16 giây.

Một mẫu dùng thời gian còn lại:

```java
long started = System.nanoTime();
long totalNanos = TimeUnit.SECONDS.toNanos(8);

executor.shutdown();
long remaining = Math.max(
        0L, totalNanos - (System.nanoTime() - started));

if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
    executor.shutdownNow();

    remaining = Math.max(
            0L, totalNanos - (System.nanoTime() - started));

    boolean terminated = executor.awaitTermination(
            remaining, TimeUnit.NANOSECONDS);
    if (!terminated) {
        reportIncompleteShutdown();
    }
}
```

Đây là **đoạn minh họa** trong method đã xử lý `InterruptedException`, với `executor` và `reportIncompleteShutdown()` do ứng dụng định nghĩa.

**Lưu ý:** `shutdownNow()` là yêu cầu hủy best-effort, thường gửi interrupt worker. Nó không ép tất cả task ngừng ngay lập tức. Khi deadline hết, cần báo trạng thái shutdown chưa hoàn tất, không giả định “awaitTermination đã gọi xong nên worker chắc chắn dừng”.

Nếu thread điều phối shutdown bị interrupt trong lúc await, quy trình có thể gửi `shutdownNow()` và khôi phục cờ interrupt trước khi thoát; đây là quyết định lifecycle riêng, không nên nuốt tín hiệu.

## 16. Độ chính xác: conversion, clock và scheduler là ba tầng khác nhau

Hãy tách ba nguồn sai lệch:

| Tầng | Vấn đề | Ví dụ |
| --- | --- | --- |
| **Số học chuyển đơn vị** | Cắt phần thập phân khi chuyển sang đơn vị thô | 999.999ns → 0ms |
| **Đồng hồ đo elapsed** | Độ phân giải/chi phí lấy mẫu của `nanoTime`, gốc tùy ý | Hai lần gọi rất sát có thể không phân biệt được |
| **Scheduler và runtime** | Thread được lên lịch chậm, GC pause, CPU contention, timer granularity | Timeout 10ms không bảo đảm callback chạy đúng mốc 10ms |

Vì vậy `TimeUnit.NANOSECONDS` là **đơn vị biểu diễn độ trễ/timeout**, không phải lời hứa về độ chính xác nanosecond.

Nếu một task cần đo latency thực tế, dùng `System.nanoTime()` trước/sau và quan sát một phân phối nhiều lần. Không dùng một lần `sleep(10)` rồi khẳng định độ chính xác timer của JVM.

## 17. Sai lầm: dùng sleep để “đợi hoàn thành”

`TimeUnit.MILLISECONDS.sleep(100)` có thể cho task khác cơ hội chạy, nhưng không bảo đảm một tác vụ đã hoàn thành sau 100ms. Nếu cần biết một event đã xảy ra, dùng primitive diễn đạt điều kiện:

- `CountDownLatch.await(timeout, unit)` khi chờ một số sự kiện hoàn thành.
- `Future.get(timeout, unit)` khi đợi outcome của task.
- `Condition.await(timeout, unit)` khi chờ condition trong giao thức lock.
- `ExecutorService.awaitTermination(timeout, unit)` khi chờ toàn executor kết thúc.
- `Thread.join(timeout)` hoặc `TimeUnit.timedJoin` khi chờ một thread kết thúc.

Tương tự, trong test concurrency, không nên `sleep(50)` rồi giả định worker đã tới một dòng cụ thể. Dùng latch hoặc barrier thiết lập precondition; chỉ dùng timeout để **giới hạn test không treo vô hạn**, không thay thế tín hiệu hoàn thành.

## 18. Tính deadline bằng tổng chờ + xử lý + overhead

Một request có thể gồm:

```text
Budget = 1000ms
  chờ lock                    70ms
  query/cache lookup         400ms
  chờ Future khác            450ms
  serialization/logging       90ms
Tổng                        1010ms -> quá hạn
```

Nếu mỗi bước chỉ giới hạn thời gian chờ riêng mà không tính **CPU work, serialization, network processing và overhead**, end-to-end latency vẫn vượt SLA.

Cần tính remaining từ **clock chung** ngay trước mỗi bước và kiểm tra sau những phần xử lý đáng kể. Nhưng ngay cả code tính remaining chính xác cũng không thể cưỡng chế một lời gọi **blocking không hỗ trợ timeout** dừng ở đúng deadline. Với I/O, dùng API có connect/read/request deadline; với task CPU-bound, kiểm tra token hủy hoặc interrupt ở điểm an toàn; với những thao tác cần transaction, đảm bảo commit/rollback nhất quán.

**Deadline là policy điều phối**, không phải cơ chế khiến mọi code Java tự động bị preempt khi hết thời gian.

## 19. Phân biệt timeout, cancellation và interruption

Các khái niệm có liên hệ nhưng khác nhau:

- **Timeout:** một thao tác chờ không hoàn tất trong thời hạn được cho phép; API có thể trả `false`, `null`, hoặc ném `TimeoutException` tùy contract.
- **Cancellation:** yêu cầu công việc không cần tiếp tục; có thể qua `Future.cancel(true)` hoặc token ứng dụng.
- **Interruption:** tín hiệu đến một thread. Nhiều API chặn có thể phản ứng bằng `InterruptedException`; code phải hợp tác.
- **Deadline:** mốc kết thúc ngân sách cho **toàn workflow** hoặc một đơn vị công việc.

Ví dụ:

```java
try {
    String result = future.get(remaining, TimeUnit.NANOSECONDS);
    return result;
} catch (TimeoutException e) {
    future.cancel(true); // Chỉ nếu policy muốn yêu cầu hủy.
    throw e;
}
```

Đây là đoạn minh họa, không có import/context đầy đủ. `cancel(true)` cố yêu cầu interrupt task; **không bảo đảm cleanup xong ngay** hoặc side effect được rollback.

Một `InterruptedException` ở thread caller đang `get()` không mặc định có nghĩa **worker đã lỗi**. Cần xử lý hủy của caller và task theo giao thức riêng; nếu không thể propagate interrupt, nên khôi phục bằng `Thread.currentThread().interrupt()` trước khi rời scope.

## 20. Chuyển ngân sách sang nhiều dịch vụ hoặc process

`System.nanoTime()` có điểm gốc tùy ý trong một JVM. **Không gửi nguyên giá trị nanoTime như một deadline tuyệt đối sang máy khác** rồi yêu cầu máy nhận so sánh với đồng hồ của nó. Chúng không có cùng hệ quy chiếu.

Khi vượt ranh giới process:

1. Có thể gửi **remaining duration** đã được giảm theo elapsed time tại phía gửi.
2. Hoặc gửi **deadline trên wall-clock** với hiểu biết về lệch đồng hồ và contract timeout.
3. Tại phía nhận, dùng ngân sách nhận được để bắt đầu **đồng hồ monotonic cục bộ**.
4. Trừ chi phí truyền và bảo vệ trước độ trễ/clock skew theo yêu cầu hệ thống.
5. Chỉ truyền deadline vào API có hỗ trợ, không giả định một HTTP/database client tự kế thừa TimeUnit của caller.

Đây là nguyên tắc chung của thời hạn phân tán; không cần bất kỳ framework nào để hiểu. Với bài thực thi trong một JVM, dùng một `nanoTime()` tại điểm bắt đầu là cách đơn giản và đáng tin cho elapsed deadline.

## 21. ScheduledExecutorService: delay không phải thời điểm chính xác

`ScheduledExecutorService.schedule(task, delay, unit)` diễn đạt **độ trễ** trước khi một task trở nên đủ điều kiện để thực thi:

```java
scheduler.schedule(
        () -> sendReminder(),
        200,
        TimeUnit.MILLISECONDS);
```

Đây là đoạn minh họa, `scheduler` là `ScheduledExecutorService` và `sendReminder()` được định nghĩa. Nó **không cam kết callback bắt đầu sau chính xác 200.000000ms**; worker có thể bận, CPU bị tranh chấp hoặc JVM bị pause.

`scheduleAtFixedRate` cố duy trì các mốc tương đối theo period, còn `scheduleWithFixedDelay` tính delay sau khi một lần chạy hoàn thành. Cả hai đều chịu thực tế scheduler, độ dài task và exception của periodic task. Không tự suy diễn rằng TimeUnit có thể giúp “lịch chạy tuyệt đối chính xác”.

Nếu cần lên lịch theo giờ lịch (“8h sáng mai tại Hà Nội”), phải tính mốc calendar/wall clock bằng `java.time` trước, rồi chuyển thành delay ở thời điểm lập lịch, có xử lý múi giờ và thay đổi đồng hồ phù hợp. `TimeUnit.DAYS` vẫn chỉ là 24 giờ cố định.

## 22. Time budget có nên cộng trực tiếp vào `System.nanoTime()`?

Một mẫu hay gặp:

```java
long deadline = System.nanoTime() + timeoutNanos;
long remaining = deadline - System.nanoTime();
```

Mẫu này có thể đúng **nếu hiểu rõ overflow và giới hạn khoảng timeout**, nhưng dễ bị dùng sai khi:

- `timeoutNanos` đã được convert bão hòa tới `Long.MAX_VALUE`.
- Code so sánh `System.nanoTime() >= deadline` bằng toán tử số nguyên trực tiếp mà không xử lý wrap.
- Dùng deadline tạo trong JVM này ở JVM khác.
- Deadline được lưu như timestamp wall-clock.
- Hai lần gọi `System.nanoTime()` rất xa nhau hoặc toán học duration vượt khoảng biểu diễn an toàn.

Với bài học chung, **lưu `startedAt` + `allowedNanos`, tính `elapsed = now - startedAt` và `remaining = max(0, allowed - elapsed)`** dễ giải thích hơn và thích hợp cho timeout hợp lý.

Không sử dụng `System.currentTimeMillis()` để giải quyết bài toán “monotonic deadline” chỉ vì con số dễ đọc. Đồng hồ hệ thống có thể nhảy.

## 23. Kiểm tra tính đúng bằng fake clock và latch

### Test phép tính deadline

Dùng fake clock như `TimeBudgetDemo`:

- Tại `0ms`, budget `500ms`.
- Sau `300ms`, còn `200ms`.
- Sau `150ms` nữa, còn `50ms`.
- Sau `60ms` nữa, còn `0ms`.

Không cần sleep. Kết quả không phụ thuộc máy nhanh hay chậm.

### Test timeout của một API chờ

Giữ một `CountDownLatch` đóng, xác nhận `await(30, MILLISECONDS)` trả `false` vì latch không thể mở trước khi timeout. Sau đó mở latch và kiểm tra thành công. Phải xử lý trường hợp thread test bị interrupt.

### Test phân biệt timeout và cancel

Dùng `FutureWaitTimeoutDemo` để xác nhận `get(timeout)` ném `TimeoutException` nhưng `isCancelled()` vẫn `false`; task hoàn tất sau khi được mở latch.

### Tránh flaky test

Không đo “phải đúng chính xác 30ms” bằng `System.currentTimeMillis()` hoặc yêu cầu scheduler bảo đảm cực ngắn. Có thể đặt timeout hợp lý và kiểm tra **trạng thái logic**: đã timeout khi precondition không thể hoàn tất, đã hoàn tất sau latch, không mất interrupt và cleanup executor.

## 24. Những lỗi thường gặp

### 24.1. Đảo thứ tự nguồn/đích của `convert`

`TimeUnit.SECONDS.convert(5000, MILLISECONDS)` là `5` giây, còn `TimeUnit.MILLISECONDS.convert(5, SECONDS)` là `5000` mili giây. Hãy đọc theo công thức **target.convert(value, source)**.

### 24.2. Lấy kết quả cắt phần dư làm deadline

`NANOSECONDS.toMillis(500_000) == 0` không có nghĩa 500.000ns đã hết. Kiểm tra phần còn lại bằng nanos.

### 24.3. Chuyển Duration lớn bằng phép nhân thủ công

`seconds * 1_000_000_000L` có thể overflow. Dùng `TimeUnit.SECONDS.toNanos(seconds)` nếu cần quy tắc saturation; vẫn phải validate timeout hợp lệ cho domain.

### 24.4. Reset timeout ở mỗi bước

`get(1s)` rồi `tryLock(1s)` rồi `await(1s)` có thể ngốn tới 3s. Dùng **một budget** và tính remaining trước từng bước.

### 24.5. Dùng wall-clock cho elapsed timeout

`System.currentTimeMillis()` có thể thay đổi khi hệ thống chỉnh giờ. `System.nanoTime()` phù hợp đo duration trong JVM.

### 24.6. Nhầm độ phân giải nanos với độ chính xác scheduler

Truyền `NANOSECONDS` không đảm bảo thread được đánh thức đúng 1ns. Timeout là ràng buộc trên logic chờ theo API, không phải SLA thực thi tuyệt đối.

### 24.7. Timeout rồi giả định Future đã bị hủy

`Future.get(timeout)` ném `TimeoutException` nhưng task có thể tiếp tục. Cần policy `cancel(true)` riêng nếu muốn yêu cầu ngừng.

### 24.8. Ngủ để chờ điều kiện

`sleep(100)` không chứng minh task/condition đã xong. Dùng `CountDownLatch`, `Future`, `Condition` hoặc primitive tương ứng.

### 24.9. Dùng TimeUnit.DAYS như “ngày lịch”

Một ngày cố định 24 giờ không phải mọi ngày địa phương trong lịch đổi giờ. Dùng `java.time` cho calendar semantics.

### 24.10. Cắt nhỏ thời gian tới millis quá sớm

Nếu một API cho phép `NANOSECONDS`, hãy giữ remaining trong nanos. Khi chuyển sang độ thô hơn, hãy hiểu truncation và không biến một thời lượng dương nhỏ thành “đã hết”.

### 24.11. Không nhả lock khi timeout/exception

Nếu `tryLock` thành công, luôn dùng `finally` để `unlock()`. Nếu thất bại hoặc bị interrupt trước khi lấy lock, không gọi unlock vô điều kiện.

### 24.12. Tin một helper deadline có thể dừng mọi I/O

Một elapsed budget chỉ cung cấp **giá trị remaining**. Nếu API downstream không hỗ trợ timeout/cancellation, deadline không thể tự preempt lời gọi đó.

## 25. Câu hỏi tự kiểm tra

**Câu 1.** `TimeUnit.SECONDS.convert(1_999, TimeUnit.MILLISECONDS)` trả gì?

**Đáp án:** `1`; phần 999ms bị cắt do chuyển sang giây nguyên.

**Câu 2.** `TimeUnit.NANOSECONDS.toMillis(999_999)` bằng 0. Có phải đã hết thời gian?

**Đáp án:** Không nhất thiết; vẫn còn 999.999 nano giây. Cần kiểm tra ở đơn vị đủ mịn.

**Câu 3.** `TimeUnit.DAYS.toNanos(Long.MAX_VALUE)` có wrap số âm không?

**Đáp án:** Không. Phép đổi của `TimeUnit` bão hòa tới `Long.MAX_VALUE` khi vượt giới hạn.

**Câu 4.** Một workflow có budget 500ms, bước A dùng 300ms. Bước B được dùng tối đa bao lâu?

**Đáp án:** Khoảng 200ms còn lại (trừ thêm overhead thực tế) chứ không được reset lại 500ms.

**Câu 5.** Vì sao không dùng `Instant.now()` hoặc `System.currentTimeMillis()` để đo thời gian chờ monotonic?

**Đáp án:** Wall-clock có thể bị chỉnh; `System.nanoTime()` phù hợp đo elapsed duration trong cùng JVM.

**Câu 6.** `future.get(100, MILLISECONDS)` ném TimeoutException. Worker có bị hủy không?

**Đáp án:** Không tự động. Task có thể còn chạy; cancellation là yêu cầu riêng.

**Câu 7.** `TimeUnit.NANOSECONDS.sleep(1)` có bảo đảm code thức dậy chính xác sau 1ns không?

**Đáp án:** Không. Độ phân giải biểu diễn không đồng nghĩa độ chính xác thức dậy thực tế.

**Câu 8.** Nếu `lock.tryLock` trả false, có được gọi `lock.unlock()` không?

**Đáp án:** Không nếu thread chưa sở hữu khóa qua lời gọi ấy. Chỉ unlock sau khi đã acquire thành công.

**Câu 9.** `awaitTermination(timeout)` trả false có nghĩa executor đã dừng chưa?

**Đáp án:** Chưa đạt trạng thái terminated trong thời gian chờ; worker vẫn có thể đang chạy.

**Câu 10.** Có nên gửi giá trị tuyệt đối của `System.nanoTime()` qua mạng để server khác dùng như timestamp deadline không?

**Đáp án:** Không. NanoTime có gốc tùy ý và không phải một mốc có thể so sánh giữa các JVM. Truyền duration remaining hoặc deadline wall-clock có hợp đồng rõ.

**Câu 11.** Khi áp dụng một budget cho cả `CountDownLatch.await` và `Future.get`, vì sao phải tính remaining lại?

**Đáp án:** Vì thời gian chờ latch đã tiêu một phần ngân sách. Nếu dùng lại timeout ban đầu, tổng latency có thể vượt budget.

**Câu 12.** Muốn test đúng logic ngân sách 500ms có cần `Thread.sleep(300)` thật không?

**Đáp án:** Không. Fake clock được inject có thể mô phỏng elapsed time chính xác, tránh flaky test do scheduler.

## 26. Tổng kết

**`TimeUnit` biểu diễn đơn vị thời gian của các phép chuyển và API timeout, nhưng không phải đồng hồ và không tự đảm bảo độ chính xác lập lịch.** Khi chuyển sang đơn vị thô hơn, giá trị `long` được cắt phần dư; khi đổi sang đơn vị nhỏ hơn quá lớn, có thể bão hòa. Đừng đánh đồng `0ms` sau chuyển đổi với `0ns` còn lại.

Để quản lý **ngân sách thời gian qua nhiều bước**, hãy bắt đầu một đồng hồ elapsed bằng `System.nanoTime()`, giữ một budget chung, **tính lại `remainingNanos()` ngay trước từng API chờ**, xử lý riêng timeout/interrupt/cancel, và không reset thời hạn tại mỗi bước. `Future.get`, `CountDownLatch.await`, `Lock.tryLock` và `ExecutorService.awaitTermination` chỉ giới hạn phần chờ của chính lời gọi; các phần CPU/I/O khác vẫn tiêu thời gian chung.

Với Java concurrency, deadline đúng là một **giao thức end-to-end**, không phải một danh sách các lời gọi mỗi lời gọi được cấp một timeout riêng. Kiểm tra bằng fake clock và latch, giữ tính nhất quán khi hủy, và dùng các kiểu `java.time` cho thời điểm lịch thay vì nhầm với thời gian đo elapsed.


</details>

- [Quay lại đầu trang](#back-to-top)
