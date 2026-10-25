<a id="back-to-top"></a>

# Ranh giới bất đồng bộ

## Menu
- [Thời điểm trả kết quả đại diện so với thời điểm công việc hoàn tất](#async-boundaries-001)
- [Đo thời gian phần tạo tác vụ và thời gian hoàn thành tác vụ](#async-boundaries-002)
- [Ngoại lệ phát sinh đồng bộ so với lỗi trong callback](#async-boundaries-003)
- [Giữ và khôi phục ngữ cảnh quan sát trong xử lý bất đồng bộ](#async-boundaries-004)
- [Không giữ tài nguyên theo lời gọi ngắn cho công việc kéo dài](#async-boundaries-005)
- [Can thiệp vào luồng sự kiện và nguy cơ phá vỡ áp lực ngược](#async-boundaries-006)
- [Hủy tác vụ, thời gian chờ và thực hiện dọn dẹp](#async-boundaries-007)
- [Xác định rõ điểm kết thúc hành vi được advice bao quanh](#async-boundaries-008)
- [Khi nào nên dùng cơ chế chuyên biệt của mô hình bất đồng bộ](#async-boundaries-009)

## <a id="async-boundaries-001">Thời điểm trả kết quả đại diện so với thời điểm công việc hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Thời điểm trả kết quả đại diện so với thời điểm công việc hoàn tất trong AOP

## 1. Bài toán: phương thức kết thúc trước tác vụ

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP), aspect thường được dùng để ghi log, đo latency, theo dõi lỗi hoặc tạo trace quanh lời gọi phương thức. Nếu phương thức đồng bộ trả về giá trị sau khi xử lý xong, “lời gọi đã trả về” thường gần với “phần việc do phương thức cam kết đã hoàn tất”. Nhưng với **phương thức bất đồng bộ**, hai sự kiện này có thể cách nhau rất xa.

Xét chữ ký:

~~~java
CompletionStage<Report> generateReportAsync();
~~~

Phương thức có thể chỉ tạo/lên lịch tác vụ rồi trả về một `CompletionStage<Report>` ngay lập tức. Báo cáo vẫn đang được tạo trên executor hoặc hệ thống khác. Khi aspect thấy lời gọi trả về bình thường, nó chỉ biết rằng **đối tượng đại diện cho kết quả tương lai đã được trả về**; chưa thể tuyên bố báo cáo đã hoàn tất thành công.

Điểm cần nắm là: **method return** kết thúc đường gọi Java hiện tại; **work completion** được thông báo bởi hợp đồng của đối tượng đại diện hoặc bởi hệ thống thực thi công việc. Một aspect chỉ bao quanh method call không tự động bao quanh toàn bộ vòng đời của tác vụ được khởi chạy sau return.

Mục tiêu của bài học là xác định chính xác hai ranh giới, chọn chỗ ghi log/metric đúng ý nghĩa, xử lý cả lỗi đồng bộ và lỗi bất đồng bộ, đồng thời bảo toàn tính không chặn của API.

## 2. Dòng thời gian và các mốc sự kiện

~~~text
Caller                  Aspect/advice                 Target / worker
  | --- gọi phương thức ---> |                                |
  |                          | ---> gọi target --------------> |
  |                          |        lên lịch công việc       |
  |                          | <--- trả Future/Stage --------- |
  | <--- nhận handle -------- |                                |
  | tiếp tục làm việc khác    |                  công việc tiếp tục
  |                          |                  thành công / lỗi / hủy
  |                          | <--- tín hiệu hoàn tất --------- |
  |                          |       observer chạy             |
~~~

Có thể ký hiệu:

| Mốc | Điều xảy ra | Điều aspect có thể kết luận |
| --- | --- | --- |
| **T0** | Advice bắt đầu bao lời gọi | Lời gọi đang bắt đầu |
| **T1** | Phương thức đích trả đối tượng đại diện | Phương thức đích đã return bình thường |
| **T2** | Advice trả handle cho bên gọi | Bên gọi đã nhận giá trị trả về cuối cùng của lời gọi |
| **T3** | Tác vụ/handle phát trạng thái terminal | Công việc **theo hợp đồng handle** đã kết thúc thành công, thất bại hoặc hủy |
| **T4** | Completion observer được thực thi | Logic ghi nhận hoàn tất đã chạy; có thể trễ so với T3 |

Với tác vụ bất đồng bộ thông thường, T2 xảy ra trước T3. Tuy nhiên, **không có thứ tự tuyệt đối**: một stage có thể đã hoàn tất trước lúc được return, hoặc tác vụ có thể hoàn tất đúng lúc advice đang xử lý. Nếu đăng ký callback khi stage đã hoàn tất, callback có thể chạy ngay trên luồng đăng ký. Vì vậy không nên dựa vào giả định “log return luôn phải xuất hiện trước log completion”.

Khoảng thời gian T0→T2 đo chi phí lời gọi đến lúc trả handle. Khoảng T0→T3 biểu thị thời gian đến tín hiệu hoàn tất của công việc. Khoảng T3→T4 phản ánh thời gian trễ quan sát callback (nếu có). Đặt tên đúng cho ba khoảng thời gian này là điều kiện để metric không gây hiểu nhầm.

## 3. “Kết quả đại diện” khác “kết quả thật”

Với `T read()` đồng bộ, bên gọi nhận `T` khi phương thức return bình thường. Với `CompletionStage<T> readAsync()`, bên gọi nhận một *handle* để quan sát hoặc lấy kết quả về sau; `T` có thể chưa tồn tại.

| Hợp đồng API | Ý nghĩa của method return | Mốc xác nhận công việc xong |
| --- | --- | --- |
| `T method()` đồng bộ | Trả kết quả thật hoặc ném exception | Thường đã có kết quả tại return, trong phạm vi hợp đồng |
| `Future<T> method()` | Trả handle có thể chưa hoàn tất | Future done và outcome được xác định |
| `CompletionStage<T> method()` | Trả stage | Stage hoàn tất bình thường hoặc đặc biệt |
| `void method(Callback cb)` | Lời gọi đăng ký/khởi tạo đã kết thúc | Callback báo trạng thái, nếu hợp đồng có |
| `Flow.Publisher<T> stream()` | Trả đối tượng mô tả luồng | Theo vòng đời subscription, onComplete/onError/cancel |

Đối tượng đại diện không nhất thiết là công việc đang chạy: publisher kiểu cold có thể chưa bắt đầu làm việc cho đến khi có subscriber; một future có thể đã hoàn tất trước khi return.

Cần phân biệt **done** với **success**. `Future.isDone()` có thể là `true` khi tác vụ hoàn tất bình thường, bị hủy, hoặc kết thúc bằng ngoại lệ. Với `CompletableFuture`, `complete(value)` báo hoàn tất bình thường, `completeExceptionally(error)` báo kết thúc bất thường; `cancel(...)` gắn trạng thái hủy, được coi là một dạng hoàn tất đặc biệt. Ghi “success” chỉ dựa trên `isDone()` là sai.

Cũng phải hỏi rõ “công việc nào được đại diện?”. Nếu stage hoàn tất khi job **được đưa vào hàng đợi**, tín hiệu ấy chỉ xác nhận thao tác enqueue. Nó không chứng minh worker đã xử lý xong job. Một aspect không được suy trạng thái của công đoạn bên ngoài từ completion của handle chỉ đại diện cho công đoạn trước.

## 4. Advice AOP nhìn thấy đến đâu?

Các loại advice AOP thường gặp có ý nghĩa tại ranh giới lời gọi:

- **Before**: chạy trước lời gọi đích; chưa biết kết quả tương lai.
- **After returning**: chạy khi phương thức đích return bình thường. Nếu return `CompletionStage<T>`, đối tượng advice thấy là stage, chưa chắc `T` đã được tạo.
- **After throwing**: nhận exception thoát ra ngay trong lời gọi đích. Lỗi xuất hiện qua stage **sau return** không được ném qua stack đã kết thúc nên không tự đi qua nhánh này.
- **After/finally**: chạy tại lúc lời gọi kết thúc dù return hay throw; không mặc nhiên chờ hết các tác vụ nền.
- **Around**: bao lời gọi đích; có thể đo thời gian gọi, quan sát đối tượng trả về và đăng ký listener cho completion trước khi trả handle lại cho bên gọi.

Đặt log `method.returned` tại after returning là hợp lý; đặt `operation.succeeded` tại cùng thời điểm chỉ hợp lý nếu hợp đồng thật sự bảo đảm return nghĩa là tác vụ đã thành công. Với phương thức trả handle bất đồng bộ, điều này thường không đúng.

### Phân biệt exception ném ngay và stage thất bại

~~~java
CompletionStage<String> start(boolean valid) {
    if (!valid) {
        throw new IllegalArgumentException("Invalid argument");
    }
    return CompletableFuture.failedFuture(
            new IllegalStateException("Worker failed"));
}
~~~

Đây là **đoạn minh họa trong một phương thức**, chưa phải tệp Java độc lập; cần khai báo lớp và import. Gọi `start(false)` khiến chính method call ném `IllegalArgumentException`. Gọi `start(true)` lại **return bình thường** một stage đã ở trạng thái thất bại. Cả hai trường hợp đều có lỗi, nhưng lỗi đi qua hai kênh khác nhau: exception đồng bộ và exceptional completion. Một aspect muốn thống kê thất bại đầy đủ phải bao quát cả hai kênh.

Tác vụ có thể thất bại rất sớm, đúng lúc return, hoặc nhiều giây sau. Thời điểm lỗi xảy ra không thay đổi sự thật rằng một stage báo lỗi qua trạng thái hoàn tất của nó.

## 5. Ví dụ Java 17 hoàn chỉnh, đầu ra xác định

Ví dụ sau mô phỏng chức năng quan sát của around advice bằng một hàm Java bao quanh lời gọi. Mục đích là hiểu ranh giới AOP, không yêu cầu bộ máy proxy hoặc weaving bên ngoài. `CompletableFuture` được chủ động hoàn tất trong `main` để đầu ra luôn có thứ tự xác định.

Lưu dưới tên **`AsyncAspectBoundaryDemo.java`**:

~~~java
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class AsyncAspectBoundaryDemo {
    static <T> CompletionStage<T> around(
            String operation,
            Supplier<? extends CompletionStage<T>> targetCall) {

        System.out.println("[enter] " + operation);
        CompletionStage<T> stage;

        try {
            stage = Objects.requireNonNull(targetCall.get(), "stage");
        } catch (RuntimeException | Error error) {
            System.out.println("[throw-now] " + error.getClass().getSimpleName());
            throw error;
        }

        System.out.println("[return-stage] " + operation);

        stage.whenComplete((value, error) -> {
            if (error == null) {
                System.out.println("[complete-ok] " + value);
            } else {
                System.out.println("[complete-error] "
                        + unwrap(error).getClass().getSimpleName());
            }
        });

        // Giữ nguyên stage mà target trả về.
        return stage;
    }

    static Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    public static void main(String[] args) {
        CompletableFuture<String> pending = new CompletableFuture<>();

        CompletionStage<String> stage = around("load", () -> {
            System.out.println("[method] return pending stage");
            return pending;
        });

        System.out.println("[caller] received, done="
                + stage.toCompletableFuture().isDone());

        pending.complete("DATA");
        System.out.println("[caller] result="
                + stage.toCompletableFuture().join());

        CompletableFuture<String> failed = new CompletableFuture<>();
        CompletionStage<String> errorStage = around("send", () -> failed);
        System.out.println("[caller] second stage received");

        failed.completeExceptionally(new IllegalStateException("boom"));

        try {
            errorStage.toCompletableFuture().join();
        } catch (CompletionException error) {
            System.out.println("[caller] caught="
                    + error.getCause().getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17+:

~~~bash
javac AsyncAspectBoundaryDemo.java
java AsyncAspectBoundaryDemo
~~~

Đầu ra:

~~~text
[enter] load
[method] return pending stage
[return-stage] load
[caller] received, done=false
[complete-ok] DATA
[caller] result=DATA
[enter] send
[return-stage] send
[caller] second stage received
[complete-error] IllegalStateException
[caller] caught=IllegalStateException
~~~

**Phân tích:**

1. Khi `around("load", ...)` bắt đầu, advice in `[enter]`. Target trả về `pending` còn chưa hoàn tất, nên advice in `[return-stage]` và bên gọi nhận stage với `isDone()==false`.
2. Chỉ khi `pending.complete("DATA")` chạy, future mới có kết quả cuối; listener `whenComplete` báo `[complete-ok] DATA`.
3. Với `send`, return bình thường diễn ra **trước** `failed.completeExceptionally(...)`. Completion listener thấy lỗi; lúc bên gọi `join()`, nó nhận `CompletionException` có cause là `IllegalStateException`.
4. `whenComplete` tự trả một dependent stage mới, nhưng ví dụ không trả dependent stage đó ra ngoài. Advice trả **stage gốc**, giảm nguy cơ vô tình thay ngữ nghĩa của handle chỉ vì mục đích logging.
5. Trong ví dụ completion do `main` gọi sau khi đã gắn listener, nên callback được kích hoạt ngay trong đường đi completion và output đã nêu đáng tin. Ở chương trình đa luồng, callback có thể chạy trên luồng hoàn tất hoặc lúc đăng ký nếu stage đã xong; không được lấy thứ tự output này làm bất biến chung.

Callback logging thực tế nên tự xử lý lỗi nội bộ và không làm việc nặng. Nếu callback ném exception, dependent stage do `whenComplete` trả về có thể thất bại dù stage gốc vẫn giữ outcome ban đầu.

## 6. Hai loại latency và cách đo

Nếu viết:

~~~java
long start = System.nanoTime();
CompletionStage<?> stage = targetCall.get();
long elapsed = System.nanoTime() - start;
~~~

thì `elapsed` chỉ là **thời gian target trả về stage**. Nó không đo việc xử lý dữ liệu kéo dài bao lâu. Nếu phương thức return sau 2 ms còn worker hoàn tất sau 4 giây, metric `2 ms` không được đặt tên “thời gian hoàn tất công việc”.

Định nghĩa một cách nhất quán:

| Chỉ số | Ranh giới | Ý nghĩa |
| --- | --- | --- |
| *Return/invocation latency* | T0→T2 (hoặc T0→T1, nói rõ lựa chọn) | Bao lâu để nhận handle |
| *Completion latency* | T0→T3 | Bao lâu để công việc được handle xác nhận kết thúc |
| *Observation delay* | T3→T4 | Bao lâu sau completion callback mới chạy |

Có thể quan sát gần đúng latency tới lúc nhận callback:

~~~java
long startedAt = System.nanoTime();
CompletionStage<Result> stage = targetCall.get();

stage.whenComplete((value, failure) -> {
    long observedLatencyNanos = System.nanoTime() - startedAt;
    recordLatency(observedLatencyNanos, failure);
});

return stage;
~~~

Đây là **mã minh họa**: `Result`, `targetCall`, `recordLatency` là thành phần do ứng dụng cung cấp. Đo trong callback cho ta thời gian đến **T4**, có thể chứa độ trễ scheduler sau T3. Muốn đo T3 chính xác phải chụp timestamp ngay tại nguồn phát completion hoặc lấy timestamp được contract cung cấp; `whenComplete` không tự mang timestamp T3.

`System.nanoTime()` thích hợp để đo khoảng thời gian vì là đồng hồ đơn điệu. `System.currentTimeMillis()` phản ánh thời gian lịch, có thể bị điều chỉnh. Nếu bài toán đòi hỏi phân tích sâu, cần chia nhỏ thêm thời gian **chờ queue**, **thực thi thật**, **retry/backoff** và **đợi downstream**; các mốc ấy phải được instrument ở nơi chúng thực sự xảy ra.

Nếu `targetCall.get()` ném exception ngay, cần đo outcome của nhánh đó riêng. Không tạo dữ liệu giả rằng một stage đã được trả khi thực tế lời gọi chưa có handle.

## 7. Theo dõi kết quả bất đồng bộ đúng abstraction

### 7.1. `CompletionStage<T>`

`whenComplete((value, failure) -> ...)` được dùng để quan sát cả completion bình thường và bất thường mà không chặn lời gọi. Nhưng cần hiểu sự khác nhau:

| Thao tác | Ý nghĩa | Rủi ro nếu dùng cho logging |
| --- | --- | --- |
| `whenComplete` | Quan sát outcome và trả dependent stage | Callback lỗi có thể ảnh hưởng dependent stage |
| `handle` | Biến outcome thành giá trị tiếp theo | Có thể đổi kết quả hoặc kênh lỗi |
| `exceptionally` | Xử lý lỗi và tạo giá trị phục hồi | Có thể biến thất bại thành thành công |
| `thenApply` | Biến đổi kết quả khi thành công | Không chạy ở mọi exceptional completion |
| `whenCompleteAsync` | Lập lịch callback trên executor | Có thêm scheduling delay/context propagation |

Nếu aspect chỉ làm telemetry, **tránh trả dependent stage khác khi không cần**; việc thay stage có thể đổi identity, semantics truyền lỗi/hủy và quan hệ dependent completion. Cũng không dùng `exceptionally` để “chỉ log” rồi trả giá trị mặc định, bởi điều đó sẽ đổi hành vi nghiệp vụ.

`whenComplete` không bảo đảm callback chạy trên một luồng cố định. Callback có thể chạy khi đã hoàn tất hoặc trên luồng đang kích hoạt completion. Tính nhẹ, an toàn đồng thời và xử lý lỗi của observer cần được thiết kế rõ.

### 7.2. `Future<T>` thuần

`java.util.concurrent.Future` hỗ trợ `get()`, `isDone()`, `isCancelled()` và `cancel(...)`, nhưng **giao diện này không có completion listener**. Nếu aspect chỉ nhận một `Future` từ return, nó có ba lựa chọn mang tính thiết kế:

- Theo dõi ngay tại task/executor nơi kết quả được tạo.
- Dùng abstraction hỗ trợ callback nếu API cho phép.
- Xây wrapper/bridge có hợp đồng rõ về success/failure/cancel/timeouts nếu thực sự cần.

Không gọi `get()`/`join()` ở trong advice chỉ để chờ log kết quả: lời gọi sẽ bị chặn, có thể gây nghẽn hoặc deadlock. Không vòng lặp bận `isDone()` vì tốn tài nguyên và vẫn phải lấy outcome để phân loại. `Future.get()` có thể ném `ExecutionException` nếu task lỗi, `CancellationException` khi bị hủy, hoặc `InterruptedException` khi luồng chờ bị ngắt — những trạng thái này khác nhau.

### 7.3. Callback-based và fire-and-forget

Với `void requestAsync(Callback cb)`, return của method thường chỉ là “đã đăng ký hoặc khởi chạy”. Completion phải đo ở callback thành công/thất bại theo hợp đồng. Nếu aspect bọc callback, wrapper phải chuyển tiếp đúng số lần, đúng thứ tự, giữ nguyên exception và semantics đồng thời.

Một phương thức `void startJob()` không trả handle, không có callback và không phát tín hiệu completion thì advice tại return **không thể xác định tin cậy** khi nào job nền kết thúc. Nếu yêu cầu theo dõi completion, phải instrument ở nguồn tác vụ hoặc nâng cấp hợp đồng API. Không tự suy “xong việc” từ “đã đưa vào executor”.

### 7.4. Publisher, subscription và terminal event

Với `Flow.Publisher<T>` hoặc publisher tương đương, return publisher có thể chỉ tạo mô tả một luồng dữ liệu. Một publisher kiểu *cold* chưa chạy tác vụ cho đến khi subscriber đăng ký. Vì thế cần phân biệt:

- **Return publisher:** đã có đối tượng đại diện.
- **Subscribe:** tạo/khởi động phiên tiêu thụ.
- **onNext:** phát một phần tử; chưa phải hoàn tất toàn stream.
- **onComplete:** luồng kết thúc bình thường.
- **onError:** luồng kết thúc với lỗi.
- **cancel:** subscriber yêu cầu hủy; không chứng minh mọi tác vụ phụ đã dừng tức thì.

Cùng một publisher có thể nhận nhiều subscription, mỗi subscription là một vòng đời riêng. Metric completion của luồng nên gắn theo **mỗi subscription**, không gom tất cả vào một return publisher. Aspect tự subscribe chỉ để quan sát có thể vô tình kích hoạt thêm một lượt xử lý, tiêu thụ dữ liệu hoặc thay đổi demand/backpressure; đây là thay đổi hành vi.

Một publisher có thể chạy lâu dài mà không phát terminal event. Vì vậy completion latency có thể không có giá trị cuối; chính sách timeout và hủy phải được định nghĩa rõ.

## 8. Bảo toàn semantics khi thêm aspect

**Quan sát** cần tách khỏi **điều khiển công việc**. Những hành động sau có thể làm API thay đổi:

1. Gọi `join()`/`get()` trước khi return: mất tính không chặn, giữ luồng bên gọi.
2. Đổi một handle thành wrapper hoặc dependent stage mà chưa kiểm tra: có thể ảnh hưởng danh tính handle, propagation của lỗi, hủy hoặc deadline.
3. Dùng `handle`/`exceptionally` để tạo kết quả thay thế: có thể nuốt lỗi.
4. Gọi `complete`, `completeExceptionally`, `orTimeout` hoặc `cancel` vào future mà aspect không sở hữu: thao túng trạng thái nghiệp vụ.
5. Tự subscribe publisher để tìm completion: kích hoạt thêm luồng xử lý.
6. Thực hiện logging/metric nặng ngay trong callback: có thể chiếm luồng phát tín hiệu hoàn tất.

Nếu thực sự cần tạo wrapper để instrument, phải kiểm tra đầy đủ success, failure, cancellation, timeout, callback timing, identity và đồng thời của API. Không mặc định hai đối tượng cùng triển khai interface thì có hành vi tương đương.

### Failure, cancellation và timeout

- **Success:** handle được xác nhận hoàn tất bình thường.
- **Failure:** handle được xác nhận hoàn tất do lỗi; đôi khi exception có lớp bao như `CompletionException`.
- **Cancellation:** handle hoặc subscription báo bị hủy/đã nhận hủy theo giao thức, nhưng side effect đã xảy ra không tự rollback.
- **Observation timeout:** hệ thống quan sát chưa nhận completion trong hạn chờ; không suy ra tác vụ thật đã dừng. Việc làm future timeout bằng API thay đổi trạng thái là hành động khác với chỉ ghi nhận quá hạn.

Các metric nên có outcome rõ ràng `success`, `failure`, `cancelled`, `timeout` và `unknown` nếu cần. Chỉ unwrap lớp exception có hợp đồng rõ, giữ cause cho trace và chẩn đoán.

### Truyền correlation context qua luồng

Advice ở T0/T2 thường chạy trên luồng bên gọi; callback hoàn tất có thể chạy trên worker khác hoặc ngay trên luồng đăng ký nếu stage đã done. `ThreadLocal` **không tự lan truyền** qua mọi executor và callback. Nếu muốn nối log cùng request, hãy chụp correlation ID tại thời điểm instrument và truyền theo cơ chế context an toàn của ứng dụng, tránh phụ thuộc vào thread name.

Listener có thể chạy đồng thời với code bên gọi; những cấu trúc lưu metric, trạng thái trace hoặc bản đồ correlation dùng chung phải được đồng bộ đúng. Không tin rằng callback luôn chạy sau dòng code nằm ngay sau return trong tất cả lịch thực thi.

### Điểm cắt AOP và phạm vi can thiệp

AOP có thể thực hiện qua decorator/proxy hoặc weaving ở các thời điểm khác nhau. Nếu chỉ intercept lời gọi đi qua wrapper, lời gọi nội bộ đi vòng qua wrapper sẽ không được bao. Weaving có thể mở rộng điểm cắt theo cấu hình, nhưng **join point method call/execution vẫn có ranh giới với work completion**. Muốn biết completion sau return, phải quan sát completion protocol hoặc điểm kết thúc tác vụ thực tế.

## 9. Chọn mốc quan sát theo tình huống

**Trường hợp A — kiểm tra input, tạo job rồi trả handle:** đo return latency để biết overhead khởi tạo. Theo dõi stage nếu muốn biết job xử lý xong.

**Trường hợp B — tạo báo cáo:** nếu stage chỉ hoàn tất khi báo cáo sẵn sàng, completion stage là mốc đúng để đo SLA tạo báo cáo; return latency là số liệu khác.

**Trường hợp C — enqueue job:** completion của stage enqueue chỉ xác nhận gửi hàng đợi, không xác nhận worker xử lý. Muốn đo worker latency cần tín hiệu từ worker hoặc hệ thống job.

**Trường hợp D — stream dữ liệu lazy:** return publisher chưa bắt đầu thực thi. Theo dõi subscribe→terminal event, và có thể đo first-item latency riêng khi cần.

**Trường hợp E — fire-and-forget không có tín hiệu:** chỉ ghi “đã gửi lệnh/đã lên lịch” tại return; muốn đo completed phải instrument tại nguồn tác vụ.

## 10. Các lỗi phổ biến

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Log “success” tại *after returning* của stage | Chỉ có handle, chưa có outcome thật | Log return riêng, log completion riêng |
| Chỉ đếm lỗi ở *after throwing* | Lỗi phát sinh sau return không đi qua stack cũ | Quan sát exceptional completion |
| Gọi return latency là “thời gian xử lý” | Chỉ đo việc tạo/lấy handle | Tách invocation và completion latency |
| Gọi `join()` trong advice | Biến lời gọi không chặn thành chặn, có thể deadlock | Dùng completion listener |
| Coi `isDone()==true` là success | Done bao gồm failure/cancellation | Phân loại outcome đúng contract |
| Thay stage để thêm logging | Có thể ảnh hưởng identity và propagation | Trả stage gốc nếu hợp lý; wrapper khi được thiết kế |
| Dùng `exceptionally` để log rồi phục hồi mặc định | Vô tình nuốt lỗi | Telemetry tách khỏi biến đổi kết quả |
| Tự subscribe publisher | Có thể khởi chạy thêm tác vụ | Instrument theo subscription thực |
| Dựa vào `ThreadLocal` trên một thread | Callback đổi luồng làm mất context | Truyền correlation context |
| Coi timeout quan sát là task đã dừng | Tác vụ có thể tiếp tục | Phân biệt timeout, cancel và unknown |

## 11. Checklist thiết kế một aspect bất đồng bộ

1. Kiểu trả về là dữ liệu thật, `Future`, `CompletionStage`, callback, publisher hay `void`?
2. Hợp đồng nào xác định completion: return, future done, callback hay terminal event?
3. Completion ấy xác nhận **đúng công việc nào**: enqueue, xử lý thật, hay một chặng downstream?
4. Cần đo return latency hay completion latency; có phải tách queue wait và execution không?
5. Lỗi nào ném ngay, lỗi nào xuất hiện khi handle hoàn tất?
6. Có phân biệt success, failure, cancellation, timeout và trạng thái không rõ không?
7. Advice có chặn, đổi handle, nuốt lỗi, tự hủy hoặc tự subscribe không?
8. Callback có thể chạy ngay, chạy trên luồng khác, hay có nhiều subscription không?
9. Correlation context, metric storage và callback có an toàn đồng thời không?
10. Nếu API không có completion signal, đã thừa nhận giới hạn và chọn điểm instrument phù hợp chưa?

## 12. Kết luận

**Trả về đối tượng đại diện** là một sự kiện của lời gọi phương thức; **công việc hoàn tất** là một sự kiện của vòng đời bất đồng bộ theo đúng hợp đồng handle. Hai sự kiện có thể gần nhau, cách nhau lâu, hoặc completion xảy ra trước khi advice kịp trả handle. Trong AOP, cần đo và log đúng ranh giới, theo dõi lỗi bất đồng bộ ngoài lỗi ném trực tiếp, và giữ nguyên ngữ nghĩa không chặn của API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-boundaries-002">Đo thời gian phần tạo tác vụ và thời gian hoàn thành tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Đo thời gian tạo tác vụ và hoàn thành tác vụ bất đồng bộ bằng AOP

## 1. Vấn đề: một lời gọi, hai thời điểm hoàn tất khác nhau

Khi một phương thức tạo hoặc gửi tác vụ bất đồng bộ, **phương thức có thể trả về trước khi công việc thực sự hoàn thành**. Một advice kiểu *around* chỉ đo từ lúc bắt đầu lời gọi đến khi phương thức trả về sẽ phản ánh thời gian tạo/gửi tác vụ; nó không tự động phản ánh thời gian thực thi bất đồng bộ.

Ví dụ, phương thức createTask() trả về CompletionStage sau khi gửi công việc vào Executor. Luồng gọi tiếp tục xử lý việc khác, còn tác vụ có thể đang chờ trong hàng đợi, đang chạy hoặc đang chờ I/O. Đo một khoảng duy nhất dễ khiến ta kết luận sai rằng công việc “rất nhanh” chỉ vì hàm tạo tác vụ trả về nhanh.

Mục tiêu của phép đo tại ranh giới bất đồng bộ là phân biệt:

- **Thời gian tạo/gửi tác vụ (creation/submission latency):** chi phí đồng bộ của lời gọi tạo tác vụ và trả về đối tượng theo dõi.
- **Thời gian đến khi hoàn tất (end-to-end completion latency):** từ lúc bắt đầu lời gọi đến khi tín hiệu hoàn tất thành công, thất bại hoặc huỷ được quan sát.
- **Thời gian chạy thực tế (execution time):** từ lúc tác vụ *bắt đầu chạy* đến khi phần công việc được đo kết thúc. Không thể suy ra chính xác số này nếu chỉ nhìn bên ngoài lời gọi gửi tác vụ.

Các mốc thời gian cần tách rõ:

~~~text
Luồng gọi:
t0: bắt đầu advice → gọi phương thức (proceed) → t1: phương thức trả về handle
                                                         |
Luồng bất đồng bộ:             chờ hàng đợi → bắt đầu chạy → kết thúc
                                                         |
                               callback quan sát hoàn tất tại tObserved

creation = t1 - t0
completion_observed = tObserved - t0
~~~

**Lưu ý về cách đặt tên:** tObserved là lúc callback quan sát trạng thái hoàn tất, không nhất thiết chính là thời điểm lõi tác vụ kết thúc. Nếu tác vụ hoàn tất trước khi đăng ký callback, callback có thể chạy ngay khi đăng ký; khi đó tObserved muộn hơn thời điểm hoàn tất thật. Tương tự, không được coi khoảng tObserved − t1 là “thời gian CPU của tác vụ”.

Dùng **System.nanoTime()** để lấy chênh lệch thời gian đơn điệu (monotonic). Không dùng System.currentTimeMillis() để đo duration vì đồng hồ lịch có thể bị điều chỉnh. Quy đổi nano giây sang mili giây bằng cách chia cho 1_000_000.0.

## 2. Đặt phép đo vào advice tại ranh giới nào?

Trong AOP, *join point* là vị trí có thể can thiệp (chẳng hạn lời gọi/thực thi phương thức); *pointcut* chọn những vị trí cần đo; *advice* chứa hành vi đo; *weaving* hoặc cơ chế interceptor/proxy gắn hành vi ấy vào luồng gọi. Phạm vi pointcut nên nhắm đúng các API tạo/gửi tác vụ thay vì quét mọi phương thức bất đồng bộ trong hệ thống.

Một thiết kế *around advice* đúng với phương thức trả về CompletionStage đi theo thứ tự sau:

1. Ghi t0 **trước** khi chuyển quyền điều khiển đến phương thức gốc bằng proceed().
2. Nếu proceed() ném ngoại lệ ngay, ghi nhận lỗi **đồng bộ** và thời gian đến khi ném lỗi, sau đó ném tiếp ngoại lệ đó.
3. Nếu proceed() trả về bình thường, ghi t1 và metric **creation**.
4. Gắn callback **không chặn** lên CompletionStage để quan sát thành công/thất bại/huỷ.
5. Trong callback, ghi tObserved và metric **completion_observed**, kèm trạng thái kết thúc.
6. Trả về handle có hợp đồng phù hợp với kiểu trả về của API; tuyệt đối không gọi get() hoặc join() trong advice để chờ hoàn tất.

Đo riêng **creation** và **completion_observed** giúp nhận ra sự khác biệt giữa “gửi việc chậm” và “gửi việc nhanh nhưng đợi kết quả lâu”. Nếu muốn biết hàng đợi tốn bao lâu, phải bổ sung mốc **task-start** tại chính phần việc được thực thi; nếu muốn biết một chuỗi xử lý hoàn tất, cần theo dõi handle của **giai đoạn cuối** mà người gọi thực sự chờ.

### Các đường đi cần tính đúng

| Đường đi | Metric tạo tác vụ | Metric hoàn tất |
| --- | --- | --- |
| Phương thức trả CompletionStage chưa hoàn tất | Ghi ngay tại t1 | Ghi khi callback chạy |
| Stage đã hoàn tất trước khi advice gắn callback | Vẫn ghi tại t1 | Callback có thể chạy ngay trong luồng đăng ký |
| proceed() ném lỗi đồng bộ | Ghi creation-failed; không có handle hợp lệ | Không giả lập một lần hoàn tất bất đồng bộ |
| Stage hoàn tất với ngoại lệ | Ghi creation bình thường | Ghi FAILED, giữ nguyên lỗi gốc |
| Stage bị huỷ | Ghi creation bình thường | Ghi CANCELLED nếu xác định được kiểu huỷ |
| API trả về void/không cung cấp handle | Chỉ đo được việc gửi tại lời gọi | Phải instrument tác vụ bên trong, không suy đoán khi nào xong |

CompletionStage.whenComplete() nhận cả giá trị kết quả lẫn lỗi; nó **không phải thao tác chờ**. Callback có thể chạy ở luồng hoàn tất stage hoặc ngay khi đăng ký nếu stage đã xong. Vì vậy callback nên cực ngắn, không làm I/O chậm hay công việc nặng; việc ghi metric nên thông qua bộ thu thập có chi phí thấp.

## 3. Ví dụ Java 17 chạy độc lập: mô phỏng around advice

Chương trình sau dùng một lớp bọc có đúng vai trò của *around advice*: bao quanh lời gọi createTask(), đo lúc phương thức trả về, rồi gắn bộ quan sát vào CompletionStage. Đây là cách minh hoạ cơ chế AOP bằng Java thuần để có thể biên dịch trực tiếp; trong hệ thống AOP thực, pointcut và weaving có thể gắn cùng logic vào join point mà không phải tự tạo lớp bọc cho từng dịch vụ.

Tạo tệp **AsyncTimingDemo.java**:

~~~java
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncTimingDemo {
    interface TaskService {
        CompletionStage<String> createTask(String id);
    }

    static final class AsyncTaskService implements TaskService {
        private final Executor executor;
        private final CountDownLatch release;

        AsyncTaskService(Executor executor, CountDownLatch release) {
            this.executor = executor;
            this.release = release;
        }

        @Override
        public CompletionStage<String> createTask(String id) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
                return "done:" + id;
            }, executor);
        }
    }

    // Lớp bọc mô phỏng advice "around"; không cần thư viện AOP.
    static final class TimingAspect implements TaskService {
        private final TaskService target;

        TimingAspect(TaskService target) {
            this.target = target;
        }

        @Override
        public CompletionStage<String> createTask(String id) {
            long entered = System.nanoTime();
            CompletionStage<String> stage;
            try {
                stage = target.createTask(id); // tương đương proceed()
            } catch (RuntimeException | Error ex) {
                emit("creation failed", System.nanoTime() - entered,
                        id, "SYNC_ERROR");
                throw ex;
            }

            long returned = System.nanoTime();
            emit("creation", returned - entered, id, "RETURNED");
            if (stage == null) {
                throw new IllegalStateException(
                        "TaskService must return a CompletionStage");
            }

            // Không gọi join()/get(): luồng gọi không bị chặn.
            return stage.whenComplete((value, error) -> {
                long finished = System.nanoTime();
                emit("completion", finished - entered, id,
                        error == null ? "SUCCESS" : "FAILED_OR_CANCELLED");
            });
        }

        private static void emit(
                String phase, long nanos, String id, String state) {
            System.out.printf(Locale.ROOT,
                    "%s id=%s state=%s duration=%.3f ms%n",
                    phase, id, state, nanos / 1_000_000.0);
        }
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        try {
            TaskService service =
                    new TimingAspect(new AsyncTaskService(executor, release));
            CompletionStage<String> result = service.createTask("A");
            System.out.println("caller: tiếp tục công việc khác");
            release.countDown();
            System.out.println(
                    "result: " + result.toCompletableFuture().join());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AsyncTimingDemo.java
java AsyncTimingDemo
~~~

Ví dụ một lần chạy (các thời lượng là **biến thiên**, không phải giá trị cố định):

~~~text
creation id=A state=RETURNED duration=2.478 ms
caller: tiếp tục công việc khác
completion id=A state=SUCCESS duration=25.900 ms
result: done:A
~~~

**Vì sao phép đo đúng ý nghĩa?** CountDownLatch giữ tác vụ chưa thể kết thúc cho đến khi luồng gọi in dòng “caller”. Do đó thời gian creation được in trước, còn thời gian completion xuất hiện sau khi nhả latch. Khoảng completion bao gồm cả thời gian chờ latch; không có cơ sở kết luận rằng thân tác vụ đã chạy CPU trong toàn bộ khoảng đó. Ví dụ này đã được biên dịch với chế độ Java 17 và chạy thành công.

**Vì sao trả về stage.whenComplete()?** Đoạn minh hoạ trả về stage phụ thuộc để người gọi chỉ in kết quả sau khi callback ghi metric chạy xong. Đây là một lựa chọn có chủ đích để quan sát thứ tự trong bài thực hành. Trong advice dùng cho sản phẩm, nếu phải bảo toàn **chính xác đối tượng Future/CompletionStage trả về**, quyền huỷ hoặc kiểu trả về cụ thể như CompletableFuture, hãy đăng ký quan sát bằng stage.whenComplete(...) rồi **trả lại stage gốc**. Một stage phụ thuộc không nhất thiết giữ nguyên identity hay ngữ nghĩa huỷ của stage gốc. Bộ ghi metric cũng phải tránh ném ngoại lệ để không làm thay đổi kết quả nghiệp vụ.

### Nếu lỗi xảy ra ở đâu thì đo ở đó

- **Lỗi đồng bộ:** executor từ chối nhận tác vụ, đối số không hợp lệ hoặc phương thức ném RuntimeException trước khi trả về handle. Advice bắt, ghi creation-failed rồi ném tiếp; không đợi completion.
- **Lỗi bất đồng bộ:** đã trả về CompletionStage nhưng tác vụ thất bại về sau. Callback nhận error; không được nhầm nó với lỗi trong bước tạo tác vụ. Nếu cần phân loại nguyên nhân, hãy bóc các lớp CompletionException theo quy tắc của API và giữ dấu vết lỗi gốc.
- **Huỷ:** CompletionStage là giao diện trừu tượng; huỷ thường đi qua CompletableFuture hoặc API tác vụ cụ thể. Không nên kết luận mọi error đều là lỗi nghiệp vụ. Ví dụ gộp trạng thái FAILED_OR_CANCELLED để tránh gắn nhãn huỷ thiếu kiểm chứng.
- **Kết quả null hợp lệ:** callback có value là null vẫn có thể mang trạng thái SUCCESS nếu error là null. Tránh dùng value == null để phát hiện thất bại.

## 4. Khi API không trả CompletionStage

Không phải tác vụ nào cũng cung cấp CompletionStage. Với **Executor.execute(Runnable)**, bản thân execute() trả về void và thường chỉ đảm bảo đã nhận hoặc từ chối việc gửi. Một around advice đặt quanh execute() chỉ đo việc gửi tác vụ. Để quan sát lúc tác vụ **bắt đầu và kết thúc**, cần bọc Runnable khi gửi:

~~~java
long submitted = System.nanoTime();
Runnable observed = () -> {
    long started = System.nanoTime();
    try {
        originalRunnable.run();
    } finally {
        long finished = System.nanoTime();
        recordQueueDelay(started - submitted);
        recordExecutionTime(finished - started);
        recordTotalTime(finished - submitted);
    }
};
executor.execute(observed);
~~~

Đây là đoạn **minh hoạ**, giả định originalRunnable, executor và các hàm record... được cung cấp từ bên ngoài; nó không phải chương trình Java độc lập. Việc bọc tác vụ phải thực hiện ở đúng đường gửi vào executor, và tách lỗi “bị từ chối ngay khi gửi” khỏi lỗi “xảy ra khi run()”. Nếu hàng đợi huỷ tác vụ trước khi run() được gọi, khối finally này không chạy: muốn đếm chính xác các lần huỷ trước thực thi phải quan sát thêm vòng đời Future/tác vụ của executor.

Với **ExecutorService.submit(Callable)** trả về Future nhưng không phải CompletionStage, không nên mở một luồng chỉ để Future.get() chờ; cách đó tiêu tốn thêm tài nguyên và có thể dẫn đến nghẽn. Có thể bọc Callable hoặc dùng cơ chế theo dõi vòng đời của tác vụ/FutureTask phù hợp. Khi một Future kết thúc, cần phân biệt thành công, lỗi, huỷ và trường hợp đã bị loại khỏi hàng đợi.

Với tác vụ kiểu **fire-and-forget**, nếu chỉ giữ một lời gọi gửi việc và không có handle, AOP bên ngoài không thể suy ra “toàn bộ quá trình hoàn tất” từ lúc phương thức gửi việc trả về. Cần điểm quan sát bên trong tác vụ hoặc tín hiệu kết thúc có tương quan (correlation ID).

## 5. Đo nhiều giai đoạn và ranh giới chuỗi bất đồng bộ

Giả sử API tạo tác vụ A, sau đó kết hợp các bước B và C. Nếu phương thức trả về handle chỉ đại diện cho A, callback của advice sẽ báo A hoàn tất ngay cả khi B và C vẫn đang chạy. Nếu API trả về **stage cuối cùng của chuỗi**, callback đo được thời điểm chuỗi đó kết thúc theo hợp đồng. Các bước tách rời, không nằm trong stage trả về, không tự động thuộc phạm vi đo.

Có ba nhóm mốc thời gian thường cần ghi:

| Khoảng đo | Bắt đầu | Kết thúc | Câu hỏi trả lời |
| --- | --- | --- | --- |
| submission | Vào advice | proceed() trả về | Tạo/gửi công việc có chậm không? |
| queue wait | Gửi tác vụ | Tác vụ thực sự bắt đầu | Việc bị chờ trong hàng đợi bao lâu? |
| execution | Tác vụ bắt đầu | Phần công việc được đo kết thúc | Thân tác vụ chạy trong bao lâu? |
| end-to-end observed | Vào advice | Callback trên handle cam kết kết thúc | Người gọi chờ đến tín hiệu hoàn tất bao lâu? |

Không suy diễn **end-to-end = submission + queue wait + execution** một cách máy móc: các mốc có thể trùng lấn, callback có thể chạy trễ, và chuỗi stage còn có thời gian I/O hoặc các bước bổ sung. Khi phân tích nút thắt hiệu năng, nên ghi các mốc thực tế tại đúng nơi phát sinh.

Nếu một CompletionStage đã kết thúc trước khi advice đăng ký callback, metric completion_observed đo **độ trễ phát hiện** (thời điểm callback được thực thi), không hồi cứu được mốc hoàn thành tuyệt đối của stage từ API này. Muốn chính xác hơn phải instrument nguồn phát tín hiệu hoàn tất.

## 6. Thiết kế AOP đáng tin trong ứng dụng thực

**Chọn pointcut:** nhắm đúng các phương thức chịu trách nhiệm tạo/gửi tác vụ, có hợp đồng trả về xác định. Một pointcut quá rộng có thể đo lặp cùng công việc ở nhiều lớp bọc, tạo số liệu trùng và tăng chi phí. Phân biệt join point “phương thức gửi việc” với join point “thân Runnable/Callable chạy” để tránh gộp hai pha.

**Giữ hợp đồng nghiệp vụ:** lời gọi proceed() chỉ thực hiện **một lần**. Advice phải trả về đúng loại đối tượng mà người gọi mong đợi; không tự chặn chờ, thay đổi exception, nuốt lỗi, đổi luồng thực thi hoặc biến timeout nghiệp vụ thành timeout quan sát. Nếu callback ghi metric gặp lỗi, phần ghi metric không được làm hỏng kết quả tác vụ.

**Giữ ngữ cảnh giữa các luồng:** ID yêu cầu/trace và nhãn metric nên được chụp lại trước khi rời luồng gọi; không giả định ThreadLocal ở luồng callback giống ThreadLocal ở luồng submit. Cẩn thận nếu context dùng tài nguyên cần giải phóng khi hoàn tất hoặc khi bị huỷ.

**Ghi metric có thể vận hành:** dùng histogram/timer cho creation và completion, bộ đếm cho SUCCESS, FAILED, CANCELLED và SYNC_ERROR. Đặt tên nhất quán, chẳng hạn async.task.creation.duration và async.task.completion.duration; nhãn nên có số lượng giá trị hữu hạn như loại tác vụ hoặc endpoint. Không đưa ID request độc nhất, tên người dùng hoặc toàn bộ thông báo ngoại lệ vào label vì sẽ làm bùng nổ số chuỗi thời gian metric.

**Hạn chế observer gây nhiễu:** callback khi hoàn tất có thể chạy ngay trên luồng thực thi tác vụ; logging đồng bộ, ghi file hoặc gửi mạng trong callback làm trễ luồng đó. Khi cần đẩy dữ liệu ra hệ thống thu thập, ưu tiên ghi nhanh vào bộ đệm; nếu dùng callback chuyển executor, phải ghi rõ metric đo thời điểm callback thực thi hay thời điểm tín hiệu hoàn tất.

**Hiểu giới hạn công cụ AOP:** advice chỉ quan sát join point thực sự được cơ chế weaving/interception áp dụng. Những lời gọi nội bộ bỏ qua interceptor hoặc các tác vụ tạo ở nơi khác sẽ không xuất hiện trong số đo trừ khi cơ chế AOP tương ứng bao phủ chúng. Không coi vắng metric là bằng chứng tác vụ không chạy.

## 7. Kiểm thử và lỗi thường gặp

Có thể xác minh phép đo bằng bốn tình huống cụ thể:

1. **Chặn tác vụ bằng latch:** phương thức trả handle nhanh, latch chưa mở thì không được có metric completion; mở latch thì phải có đúng một lần completion.
2. **Ném lỗi ngay khi gửi:** thay executor bằng loại từ chối việc gửi; phải có creation-failed, ngoại lệ được truyền tiếp và không có báo cáo completion giả.
3. **Hoàn tất bất đồng bộ với lỗi:** trả một CompletableFuture được completeExceptionally; phải có creation rồi một completion lỗi, người gọi vẫn nhận được thất bại.
4. **Stage đã hoàn tất hoặc bị huỷ:** callback vẫn phải quan sát kết thúc; trạng thái lỗi/huỷ được phân loại dựa trên dữ liệu thực của API, và không giả định callback sẽ chạy trên luồng worker.

Các lỗi phổ biến cần tránh:

- **Đặt đồng hồ dừng ngay sau proceed() rồi gọi số đó là thời gian chạy tác vụ:** chỉ đo được đường gọi đồng bộ.
- **Dùng join()/get() trong advice:** biến lời gọi bất đồng bộ thành chờ đồng bộ và bóp méo cả hành vi lẫn metric.
- **Coi callback là mốc thực thi CPU:** khoảng end-to-end có cả chờ hàng đợi, I/O, độ trễ callback và các giai đoạn phụ.
- **Dùng currentTimeMillis() cho elapsed:** có thể sai khi đồng hồ hệ thống đổi giờ; dùng nanoTime() để lấy hiệu.
- **Trả về một Future khác mà không xem xét hợp đồng:** có thể thay đổi khả năng huỷ, loại trả về và cách nhận ngoại lệ.
- **Gắn nhiều observer ở nhiều lớp aspect:** dễ đếm cùng một tác vụ nhiều lần.
- **Đếm thành công bằng cách kiểm tra result != null:** kết quả null vẫn có thể thành công.

Một phép đo AOP có ích phải trả lời chính xác **đã đo ranh giới nào**: trả về sau khi gửi công việc, bắt đầu/chạy xong thân tác vụ, hay nhận tín hiệu hoàn tất trên handle được trả về. Đặt tên metric và vị trí advice theo ranh giới đó giúp so sánh số liệu đúng, phát hiện nghẽn ở đúng pha và giữ nguyên bản chất bất đồng bộ của chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-boundaries-003">Ngoại lệ phát sinh đồng bộ so với lỗi trong callback</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ phát sinh đồng bộ so với lỗi trong callback: ranh giới bất đồng bộ trong AOP

## Mục tiêu học tập

Khi áp dụng **Aspect-Oriented Programming (AOP)** để ghi log, đo thời gian, giám sát lỗi hoặc quản lý ngữ cảnh, cần phân biệt hai thời điểm: **lời gọi phương thức kết thúc** và **công việc do lời gọi đó khởi phát kết thúc**. Hai thời điểm thường trùng nhau trong mã đồng bộ nhưng có thể khác nhau khi phương thức trả về một đối tượng đại diện cho kết quả tương lai, đăng ký callback hoặc phát sự kiện.

Sau bài này, bạn có thể:

- Phân biệt ngoại lệ thoát khỏi lời gọi đồng bộ với lỗi phát sinh trong callback, kể cả callback chạy ngay lập tức.
- Xác định chính xác một advice bao quanh lời gọi có thể quan sát lỗi nào và không thể quan sát lỗi nào.
- Thiết kế điểm quan sát bổ sung ở ranh giới callback hoặc kết quả bất đồng bộ, giữ nguyên ngữ nghĩa thành công, lỗi và hủy.
- Dùng Java 17+ kiểm chứng bằng chương trình có kết quả đầu ra xác định.

## 1. Ba khái niệm cần tách bạch

**Aspect** đóng gói một mối quan tâm cắt ngang (cross-cutting concern), ví dụ logging, tracing hoặc đo latency. **Join point** là điểm thực thi được quan sát/can thiệp; **advice** là logic chạy trước, sau hoặc bao quanh join point. Trong bài này, join point được xét chủ yếu là **lời gọi phương thức**. Cơ chế áp dụng advice có thể là weaving, interceptor hay proxy; các cơ chế đó không tự làm thay đổi quy tắc lan truyền ngoại lệ của Java.

**Ngoại lệ đồng bộ** là một ngoại lệ thoát ra theo call stack của lời gọi hiện tại. Chẳng hạn phương thức đích ném IllegalArgumentException trước khi trả về; advice bao quanh gọi phương thức đó có thể dùng try/catch để ghi nhận và ném lại.

**Callback** là hàm được truyền/đăng ký để gọi khi một hoạt động, sự kiện hoặc kết quả xảy ra. Tên gọi “callback” **không bảo đảm bất đồng bộ**: thư viện có thể gọi callback ngay trong cùng call stack, trên một luồng khác, trên event loop, hoặc khi một CompletableFuture đã hoàn thành. Để xác định ai bắt được lỗi, phải xem **callback thực sự được gọi lúc nào, do thành phần nào gọi và hợp đồng xử lý lỗi của thành phần đó**.

Ví dụ minh họa hai dòng thời gian:

~~~text
A. Ngoại lệ đồng bộ
caller -> around advice -> target() -> throw E
                    <- E qua cùng call stack <- caller catch E

B. Callback được kích hoạt sau khi lời gọi đăng ký đã trở về
caller -> around advice -> register(callback) -> return
                    <- advice kết thúc
... sự kiện/kết quả xảy ra ...
dispatcher / completion engine -> callback() -> throw E
                                 -> cơ chế xử lý lỗi riêng
~~~

**Ranh giới bất đồng bộ (asynchronous boundary)** là nơi quyền điều khiển rời khỏi lời gọi hiện tại và công việc tiếp tục thông qua lịch thực thi, callback, hàng đợi, future hoặc completion handler. “Khác call stack” mới là điểm then chốt; “khác thread” là khả năng thường gặp, nhưng không phải điều kiện bắt buộc.

## 2. Ngoại lệ đồng bộ: advice bao quanh nhìn thấy gì?

Giả sử target có dạng:

~~~java
static String parse(String text) {
    if (text == null) {
        throw new IllegalArgumentException("text must not be null");
    }
    return text.trim();
}
~~~

Một around advice về mặt ý tưởng hoạt động như sau:

~~~java
try {
    before();
    String value = parse(input);
    onNormalReturn(value);
    return value;
} catch (RuntimeException failure) {
    onSynchronousFailure(failure);
    throw failure;
} finally {
    onInvocationFinished();
}
~~~

Nếu parse ném lỗi trước khi trả về, lỗi đi trực tiếp qua phần try/catch của advice. Advice có thể quan sát kiểu lỗi, thông điệp, stack trace, nguyên nhân, thời điểm xảy ra và tiếp tục truyền chính ngoại lệ đó cho caller.

Phân biệt các mốc quan sát:

- **Before**: trước khi target được gọi; chưa biết kết quả.
- **After returning**: target đã **trả về bình thường**. Với phương thức trả về CompletionStage, điều này chỉ nói đối tượng stage đã được trả về, chưa khẳng định công việc đã thành công.
- **After throwing**: target **ném ngoại lệ thoát ra khỏi chính lời gọi được bao quanh**. Không mặc nhiên bao gồm lỗi xảy ra sau đó trong callback.
- **After/finally**: lời gọi được bao quanh đã rời khỏi call stack, bất kể cách kết thúc; không mặc nhiên là tác vụ bất đồng bộ đã xong.
- **Around**: có thể quyết định tiếp tục gọi target, quan sát kết quả trả về và, nếu hợp đồng cho phép, gắn thêm completion observer.

Cần xác định rõ phạm vi đo: “thời gian lời gọi phương thức” kết thúc ở return/throw; “thời gian nghiệp vụ hoàn thành” kết thúc ở kết quả cuối cùng của công việc bất đồng bộ.

## 3. Lỗi trong callback: vì sao try/catch bên ngoài không đủ?

Xét lời gọi register(onResult): nếu nó lưu callback rồi trả về, catch bao quanh register chỉ bắt được lỗi của quá trình **đăng ký**. Khi sự kiện phát sinh sau đó, callback được gọi từ một stack khác; một throw tại đây không thể tự động “quay ngược thời gian” để đi vào catch của register đã kết thúc.

Cách lỗi callback được xử lý phụ thuộc vào API:

| Cách thực thi callback | Kết quả khi callback ném ngoại lệ | Advice quanh lời gọi đăng ký có bắt được không? |
| --- | --- | --- |
| Gọi trực tiếp callback.run() ngay trong register và không chặn lỗi | Throw lan qua stack của register | Có |
| Gọi callback sau khi register trả về, trên executor/event loop | Lỗi đi vào nơi gọi callback, uncaught handler, cơ chế báo lỗi hoặc log tùy API | Không |
| Gọi callback trong thenApply/thenAccept của CompletableFuture | Lỗi thường làm **stage phụ thuộc** hoàn thành exceptional | Không, nếu lời gọi đăng ký đã trở về |
| CompletionStage được trả về đã/thêm lúc sau hoàn thành exceptional | Lỗi được lưu trong trạng thái hoàn thành của stage | Catch quanh thao tác trả về stage chỉ thấy lỗi nếu chính thao tác đó throw |
| Callback có tham số error hoặc error channel riêng | Lỗi được chuyển qua giao thức callback; có thể không hề có throw | Phải quan sát error channel đó |

**Ngoại lệ xảy ra trong callback** và **kết quả bất đồng bộ báo thất bại** có quan hệ nhưng không đồng nhất:

1. Công việc phía dưới có thể thất bại và báo qua error callback/failed stage mà không ném lỗi trên luồng của người đăng ký.
2. Công việc có thể thành công nhưng chính callback xử lý kết quả ném lỗi; stage do callback tạo ra có thể thất bại.
3. Thư viện có thể chặn và ghi log exception trong callback, hoặc để exception thoát tới bộ điều phối. Không thể giả định mọi exception đều được đưa vào future.
4. Callback có thể chạy đồng bộ ngay trong lời gọi API. Vì vậy, phải xét **thực tế lời gọi callback**, không chỉ tên “callback”.

Đặc biệt, try/catch đặt quanh một biểu thức trả về future không bắt lỗi hoàn tất future **sau khi** biểu thức đó đã trả về. Dùng cơ chế completion/error của API tương ứng mới quan sát được trạng thái muộn.

## 4. Quan sát hai tầng trong AOP

Một aspect theo dõi tác vụ bất đồng bộ thường cần **hai tầng quan sát có ngữ nghĩa khác nhau**.

**Tầng 1 — invocation boundary:** ghi nhận điểm bắt đầu, dữ liệu vào đã lọc, lỗi ném trước khi trả về, thời điểm phương thức trả về, và đối tượng bất đồng bộ được trả về nếu có. Đây là những gì một advice đồng bộ có thể biết chắc chắn.

**Tầng 2 — completion/callback boundary:** theo dõi callback thực sự chạy hoặc kết quả của future/promise/stream; ghi nhận thành công, thất bại hoặc hủy, thời điểm hoàn thành và ngữ cảnh tương quan. Tầng này cần một hook thực sự đại diện cho sự hoàn tất: callback error/result, completion stage, handler của nguồn phát sự kiện, hoặc bộ giám sát luồng thực thi.

~~~text
                       +--------------------+
request / caller ----> | around invocation  | ----> target()
                       +--------------------+            |
                         | sync throw                     +--> throw ngay -> sync failure
                         | normal return                  |
                         v                                +--> trả CompletionStage
                    invocation ended                                  |
                                                                       v
                                                      completion observer/callback
                                                        | success | error | cancelled
                                                        v
                                                   async outcome recorded
~~~

Nếu target trả về stage, một cách thực hiện chung là quan sát stage đó:

~~~java
CompletionStage<T> stage = invokeTarget(); // vẫn có thể throw ngay tại đây
return stage.whenComplete((value, error) -> {
    // Quan sát sự hoàn thành của stage.
    // Callback quan sát cần hạn chế lỗi phát sinh từ chính nó.
});
~~~

Lưu ý: whenComplete **trả về một stage mới**; lời gọi trên là một lựa chọn thiết kế tác động đến stage trả cho caller. Nếu callback quan sát tự throw, stage mới có thể thất bại dù stage gốc thành công. Nếu stage gốc đã thất bại, lỗi gốc thông thường vẫn được ưu tiên theo hợp đồng của CompletionStage. Vì thế, hãy giữ logic quan sát không ném lỗi, hoặc xử lý nội bộ lỗi của logger/metrics, và kiểm tra hợp đồng của API trước khi decorates kết quả.

Nếu chỉ gọi stage.whenComplete(...) nhưng bỏ qua stage trả về, mọi exception do observer tự ném có thể nằm trong một dependent stage không được ai kiểm tra. Nếu trả stage đã decorate, cần chủ động giữ ngữ nghĩa completion và hiểu sự khác biệt về định danh đối tượng stage. Trong hệ thống có yêu cầu **giữ nguyên chính đối tượng trả về**, có thể gắn observer không thay kết quả trả về, với chính sách giám sát lỗi observer rõ ràng.

## 5. Ví dụ Java 17+ chạy được: so sánh ba đường lỗi

Tạo tệp **AspectBoundaryExample.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class AspectBoundaryExample {
    static <T> T aroundInvocation(String name, Supplier<T> target) {
        System.out.println("BEFORE " + name);
        try {
            T result = target.get();
            System.out.println("RETURN " + name);
            return result;
        } catch (RuntimeException error) {
            System.out.println("SYNC FAIL " + name + ": " + error.getMessage());
            throw error;
        }
    }

    static <T> CompletionStage<T> aroundCompletion(
            String name, Supplier<CompletionStage<T>> target) {
        CompletionStage<T> stage = aroundInvocation(name, target);
        return stage.whenComplete((value, error) -> {
            if (error == null) {
                System.out.println("ASYNC SUCCESS " + name);
            } else {
                System.out.println("ASYNC FAIL " + name + ": " + message(error));
            }
        });
    }

    static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error.getMessage();
    }

    public static void main(String[] args) {
        // (1) Lỗi thoát ngay qua lời gọi được bao quanh.
        try {
            aroundInvocation("parse", () -> {
                throw new IllegalArgumentException("invalid input");
            });
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER SYNC: " + error.getMessage());
        }

        // (2) Lời gọi trả về stage bình thường, nhưng stage thất bại sau đó.
        CompletableFuture<String> pending = new CompletableFuture<>();
        CompletionStage<String> observed = aroundCompletion("fetch", () -> pending);
        pending.completeExceptionally(new IllegalStateException("timeout"));
        try {
            observed.toCompletableFuture().join();
        } catch (CompletionException error) {
            System.out.println("CALLER ASYNC: " + message(error));
        }

        // (3) Dữ liệu đầu vào thành công, nhưng callback xử lý dữ liệu ném lỗi.
        CompletableFuture<String> event = new CompletableFuture<>();
        CompletableFuture<Void> callbackStage = event.thenAccept(value -> {
            throw new IllegalArgumentException("invalid callback");
        });
        event.complete("event");
        System.out.println("EVENT COMPLETE RETURNED");
        try {
            callbackStage.join();
        } catch (CompletionException error) {
            System.out.println("CALLBACK FAIL: " + message(error));
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AspectBoundaryExample.java
java AspectBoundaryExample
~~~

Đầu ra xác định:

~~~text
BEFORE parse
SYNC FAIL parse: invalid input
CALLER SYNC: invalid input
BEFORE fetch
RETURN fetch
ASYNC FAIL fetch: timeout
CALLER ASYNC: timeout
EVENT COMPLETE RETURNED
CALLBACK FAIL: invalid callback
~~~

**Giải thích từng trường hợp:**

1. parse ném IllegalArgumentException trước khi aroundInvocation trả về, vì vậy advice nhìn thấy **SYNC FAIL** và caller cũng bắt được lỗi khi gọi phương thức.
2. fetch trả một CompletableFuture đang pending. Advice đồng bộ in **RETURN fetch**, tức chỉ lời gọi đã kết thúc. Sau đó completeExceptionally làm stage thất bại và completion observer in **ASYNC FAIL fetch**. Khi caller chủ động gọi join, CompletionException mới xuất hiện tại vị trí join.
3. thenAccept đăng ký callback và trả một **dependent stage**. Khi event.complete("event") được gọi, callback throw nhưng CompletableFuture ghi nhận lỗi vào dependent stage. complete trả về bình thường; callbackStage.join() mới ném CompletionException.

Ví dụ cố tình dùng complete/completeExceptionally từ cùng luồng main để thứ tự in luôn xác định. Điều đó chứng minh rằng **lỗi hoàn tất bất đồng bộ là một ranh giới kết quả và quyền điều khiển**, không nhất thiết đòi hỏi hai luồng. Trong hệ thống thực tế, observer có thể chạy trên luồng hoàn tất stage; phiên bản callback “async” có thể chạy với executor riêng. Không dựa vào thứ tự log giữa các luồng nếu chưa có quan hệ đồng bộ hóa.

**Giới hạn minh họa:** aroundInvocation và aroundCompletion là các hàm bao quanh lời gọi để giải thích ngữ nghĩa advice. Chúng không phải bộ weaving AOP đầy đủ. Hàm message chỉ gỡ CompletionException phục vụ in thông điệp; mã sản xuất nên giữ nguyên nguyên nhân, stack trace và thông tin lỗi khi cần.

## 6. Ba cách quản lý lỗi callback

### 6.1 Callback API thuần: chịu trách nhiệm ở nơi thực sự gọi callback

~~~java
static void dispatch(Runnable callback) {
    try {
        callback.run();
    } catch (RuntimeException failure) {
        // Ghi nhận tại callback execution boundary.
        System.err.println("Callback failed: " + failure);
        // Chính sách tiếp theo tùy giao thức: báo lỗi, retry, hoặc chuyển lỗi.
    }
}
~~~

Nếu dispatch chạy sau một lời gọi đăng ký, advice quanh **lời gọi đăng ký** không sở hữu catch này. Với callback do thư viện gọi, người dùng API cần đọc rõ giao thức về exception handler, completion/error callback và cách gắn ngữ cảnh theo sự kiện.

### 6.2 CompletionStage: quan sát chuỗi phụ thuộc

- **whenComplete**: quan sát cả value/error; thông thường giữ kết quả trước đó nếu observer không throw. Thích hợp telemetry nhưng tránh tác dụng phụ có thể thất bại.
- **handle**: nhận cả value/error và **biến đổi** chúng thành kết quả mới; thích hợp khi có chính sách phục hồi hoặc chuyển đổi lỗi rõ ràng.
- **exceptionally**: xử lý riêng nhánh thất bại, thường cung cấp giá trị thay thế; nếu dùng trong aspect chỉ để ghi log mà vô tình trả fallback, nó sẽ **thay đổi ngữ nghĩa lỗi**.
- **thenApply/thenAccept**: chạy xử lý khi stage trước thành công; lỗi ném trong hàm xử lý sẽ làm dependent stage thất bại.
- **join/get**: lấy kết quả theo cách có thể chặn luồng nếu chưa hoàn tất; join có thể ném CompletionException, get có thể ném ExecutionException (và InterruptedException). Đây là điểm quan sát của caller, **không nên chặn luồng chỉ để aspect lấy kết quả**.

Ví dụ về việc **quan sát** khác **phục hồi**:

~~~java
CompletionStage<String> observed = work.whenComplete((value, error) -> {
    recordOutcome(error); // observer cần không ném lỗi
});

CompletionStage<String> recovered = work.exceptionally(error -> {
    return "fallback"; // cố ý đổi kết quả thất bại thành thành công
});
~~~

Hai dòng này có mục đích khác nhau. Việc chuyển lỗi thành fallback phải là quyết định nghiệp vụ, không phải tác dụng phụ của một advice giám sát.

### 6.3 Sự kiện, stream, tác vụ nền và fire-and-forget

Một phương thức trả void sau khi enqueue công việc không có “future kết quả” để aspect suy luận. Cần chủ động gắn điểm quan sát nơi thực thi thật: callback lỗi, subscriber error, task supervisor, queue consumer hay executor hook thích hợp. Nếu callback có thể chạy nhiều lần, không thể xem **lần callback đầu** là tác vụ hoàn thành; phải định nghĩa hợp đồng complete/error/cancel cụ thể. Các callback có thể đồng thời chạy, vì thế số đếm và trạng thái theo dõi phải an toàn khi truy cập đồng thời.

## 7. Các góc cạnh nâng cao dễ bị hiểu sai

**Callback có thể chạy ngay trong thao tác đăng ký.** Một stage đã hoàn thành trước khi gọi whenComplete có thể kích hoạt callback ngay khi đăng ký. Vì thế, không suy luận “callback luôn chạy sau dòng lệnh kế tiếp”. Nếu callback ném lỗi, CompletionStage vẫn có thể chuyển lỗi đó vào dependent stage thay vì ném qua lời gọi đăng ký; hành vi tùy hợp đồng.

**Lỗi của callback không luôn đồng nghĩa lỗi của công việc gốc.** Nếu work đã thành công nhưng thenAccept throw, work vẫn có thể ở trạng thái success trong khi dependent stage thất bại. Aspect phải xác định rõ đang theo dõi **stage gốc, stage trung gian hay toàn bộ pipeline** để tránh ghi nhãn kết quả sai.

**Lỗi trong instrumentation cũng là lỗi.** Logic ghi log, tracing hoặc metrics có thể ném RuntimeException. Với advice đồng bộ, exception đó có thể thay thế lỗi/ngữ nghĩa của target; với observer bất đồng bộ, nó có thể làm dependent stage thất bại. Xử lý lỗi nội bộ phù hợp với chính sách giám sát, hạn chế side effect và không vô ý nuốt lỗi nghiệp vụ.

**Không làm mất loại lỗi ban đầu.** CompletionException và ExecutionException thường là wrapper. Khi cần phân loại/ghi nhận, xem cause; khi truyền cho caller, ưu tiên giữ contract của API và chuỗi nguyên nhân. Với các cơ chế proxy/reflective invocation, lỗi còn có thể bị wrapper bởi cơ chế gọi phản xạ; cần xác định tầng nào đang bắt và unwrapping có chủ đích.

**Cancellation có thể là một trạng thái riêng.** Future hủy có thể biểu diễn bằng CancellationException hoặc kết quả “cancelled”; đừng tự động đếm mọi cancellation là lỗi nghiệp vụ. Nếu hệ thống hỗ trợ hủy, ghi nhận riêng cả lý do, thời điểm và ngữ nghĩa hủy.

**Không đồng nhất context giữa các luồng.** Những thông tin lưu trong ThreadLocal của advice gọi ban đầu không được bảo đảm hiện diện ở executor/callback khác. Hãy chuyển correlation ID/trace context bằng cơ chế propagation phù hợp, thiết lập và dọn dẹp context quanh phần thực thi callback, tránh rò dữ liệu sang tác vụ tiếp theo.

**Độ trễ của lời gọi và độ trễ hoàn tất khác nhau.** Lấy timestamp trước invocation, sau khi trả stage, và khi stage hoàn tất. Ghi hai chỉ số riêng: invocation latency và end-to-end completion latency. Nếu chỉ đo đến return, tác vụ chậm hoặc thất bại sau đó có thể vẫn bị báo “thành công nhanh”.

**Có thể xảy ra race.** Một tác vụ có thể hoàn tất trước khi caller kịp nhận stage. Khi attach observer trên stage đã hoàn tất, observer có thể chạy ngay; trong code đa luồng, thứ tự BEFORE/RETURN/completion logs phải được phân tích theo quan hệ thực thi thật và bảo đảm đồng bộ của API, không dựa vào suy đoán về lịch CPU.

## 8. Cách thiết kế aspect xử lý lỗi đúng ranh giới

1. **Định nghĩa join point và kết quả mong đợi.** Advice đang theo dõi phương thức, callback invocation, một stage hay toàn bộ lifecycle? Khai báo rõ event nào được xem là success, failure, cancellation.
2. **Quan sát lỗi ném ngay.** Dùng catch ở tầng gọi target để ghi nhận ngoại lệ trước khi return, đồng thời bảo toàn hành vi ném lại.
3. **Xem kiểu và hợp đồng kết quả trả về.** Nếu trả CompletionStage, có thể gắn completion observer; nếu đăng ký callback hoặc fire-and-forget, cần hook xử lý tại nơi callback/task được gọi. Không dùng phương thức tên “async” làm bằng chứng duy nhất.
4. **Không đánh tráo trạng thái.** Return thành công của một stage chưa hoàn tất khác với tác vụ thành công; stage gốc thành công nhưng callback thất bại khác với công việc gốc thất bại.
5. **Bảo vệ logic quan sát.** Không để lỗi telemetry thay đổi kết quả nghiệp vụ; không blocking join/get trong advice chỉ để lấy outcome; giữ cancellation và cause chain khi cần.
6. **Truyền ngữ cảnh xuyên ranh giới.** Gắn correlation ID ở entry và khôi phục nó tại callback/completion để kết nối hai tầng log.
7. **Thử các trường hợp độc lập.** Target throw đồng bộ; target trả pending stage rồi fail; callback throw khi xử lý kết quả; callback được gọi ngay; observer tự fail; cancel; hai completion xảy ra gần nhau.

Bảng quyết định nhanh:

| Điều cần quan sát | Điểm gắn advice/hook phù hợp | Sai lầm cần tránh |
| --- | --- | --- |
| Lời gọi ném lỗi trước khi return | Around/after-throwing của lời gọi | Coi mọi exception sau đó là lỗi đồng bộ |
| Lời gọi trả future bình thường | After-returning của lời gọi | Ghi “nghiệp vụ thành công” ngay lúc return |
| Future thất bại/hoàn tất/hủy | Completion observer của future | Blocking toàn bộ pipeline để chờ kết quả |
| Callback ném lỗi khi được gọi | Callback execution boundary hoặc dependent stage | Chỉ catch quanh thao tác register |
| Lỗi callback do framework/runtime xử lý | Error channel/handler theo hợp đồng API | Giả định exception luôn đi tới caller ban đầu |

## 9. Tự kiểm tra

**Câu 1.** Một advice in “RETURN” khi nhận CompletableFuture, 200 ms sau future hoàn thành exceptional. Advice có sai không?

**Đáp án:** “RETURN” đúng nếu nó mô tả **lời gọi đã trả về**; sai nếu nó được diễn giải là **nghiệp vụ đã hoàn tất thành công**. Cần thêm hook completion để ghi lỗi muộn.

**Câu 2.** Đăng ký callback trong try/catch, vì sao lỗi trong callback vẫn có thể không vào catch?

**Đáp án:** Try/catch chỉ có hiệu lực với exception lan qua stack đang thực thi. Nếu callback được gọi sau khi thao tác đăng ký trả về, stack ban đầu đã kết thúc; lỗi thuộc stack và cơ chế xử lý mới. Nếu callback chạy trực tiếp ngay trong stack đăng ký và lỗi không bị chặn, catch vẫn có thể bắt được.

**Câu 3.** CompletableFuture gốc hoàn thành thành công, nhưng thenAccept ném lỗi. Lỗi thuộc đâu?

**Đáp án:** Lỗi thuộc **dependent stage** được tạo bởi thenAccept. Nó không nhất thiết biến stage gốc thành failed. Cần theo dõi đúng stage mà caller dùng làm kết quả cuối cùng.

**Câu 4.** Vì sao không dùng exceptionally với giá trị fallback chỉ để aspect ghi lỗi?

**Đáp án:** Vì exceptionally có thể biến thất bại thành thành công, vô tình đổi hợp đồng. Dùng observer phù hợp, xử lý lỗi của observer riêng và để nguyên kết quả nghiệp vụ.

## Kết luận kiến thức

Một **ngoại lệ đồng bộ** có thể thoát qua cùng call stack mà advice đang bao quanh. **Lỗi trong callback** đi theo thời điểm và cơ chế thực thi của callback; nó có thể lan đồng bộ nếu callback được gọi ngay, được lưu trong một dependent future, hoặc được xử lý bởi một error channel khác. Khi thiết kế AOP, luôn tách **kết thúc lời gọi** khỏi **hoàn tất hoạt động**, rồi đặt điểm giám sát tại đúng ranh giới sở hữu kết quả và lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-boundaries-004">Giữ và khôi phục ngữ cảnh quan sát trong xử lý bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ và khôi phục ngữ cảnh quan sát trong xử lý bất đồng bộ (AOP)

## 1. Mục tiêu và phạm vi

Khi một lời gọi được quan sát bằng **Aspect-Oriented Programming (AOP)** chuyển công việc sang một luồng hoặc tác vụ khác, thông tin dùng để liên kết log, trace và phép đo rất dễ bị mất, dùng nhầm hoặc tồn tại quá lâu. Bài này trình bày cách **chụp (capture), truyền (propagate), gắn (install), khôi phục (restore) và kết thúc (finish)** ngữ cảnh quan sát qua các ranh giới bất đồng bộ.

Sau bài học, bạn có thể:

- Giải thích vì sao advice bao quanh một lời gọi không tự động bao quanh toàn bộ thời gian chạy bất đồng bộ.
- Truyền ngữ cảnh đúng cách qua executor, tác vụ và callback; tránh lẫn ngữ cảnh giữa những request dùng chung luồng.
- Phân biệt **khôi phục trạng thái cục bộ của luồng** với **kết thúc span hoặc observation**.
- Thiết kế advice ghi nhận kết quả khi tác vụ *thực sự hoàn tất*, bao gồm lỗi và hủy.
- Kiểm tra tính đúng đắn và rủi ro về hiệu năng, bảo mật của việc lan truyền ngữ cảnh.

Đây là AOP ở mức khái niệm và kỹ thuật chung. Ví dụ chạy bằng Java 17, sử dụng JDK dynamic proxy làm cơ chế chặn lời gọi; mô hình có thể áp dụng tương tự cho các kiểu weaving/interception khác nếu chúng hỗ trợ join point tương ứng.

## 2. Ngữ cảnh quan sát là gì?

**Ngữ cảnh quan sát (observation context)** là tập dữ liệu đi kèm một đơn vị công việc để các tín hiệu quan sát có thể được liên kết với nhau. Một ngữ cảnh thường chứa:

| Thành phần | Vai trò |
|---|---|
| Trace ID | Liên kết các span thuộc cùng một hành trình xử lý |
| Span ID và parent span ID | Biểu diễn quan hệ cha–con giữa các bước |
| Correlation/request ID | Ghép các dòng log hoặc sự kiện có liên quan |
| Baggage/thuộc tính truyền kèm | Mang một số thông tin cần thiết giữa các ranh giới, có kiểm soát |
| Trạng thái lấy mẫu và các cờ trace | Hỗ trợ quyết định thu thập dữ liệu quan sát |

**Ngữ cảnh** khác với **span**: ngữ cảnh mang danh tính và quan hệ cần thiết để tiếp tục công việc; span là bản ghi một hoạt động có thời điểm bắt đầu/kết thúc, trạng thái, thuộc tính và sự kiện. Một span có thể kết thúc trong khi bản chụp ngữ cảnh vẫn tồn tại ở nơi khác; giữ bản chụp không có nghĩa là span còn chạy.

**Ngữ cảnh** cũng khác với nơi lưu trữ nó. Một `ThreadLocal` hoặc ngữ cảnh log kiểu MDC chỉ là cách đặt dữ liệu vào *luồng hiện tại*; nó không tự gửi dữ liệu sang luồng khác. Trong hệ thống phân tán, sự truyền tiếp thường dùng các trường của giao thức, chẳng hạn `traceparent` và `tracestate` theo W3C Trace Context; đó là ranh giới mạng, khác với việc chuyển task trong cùng JVM.

Không nên mặc định mang theo cả đối tượng request, dữ liệu cá nhân, token hoặc quyền truy cập. Dữ liệu phục vụ quan sát cần tối thiểu, bất biến khi có thể, và có chính sách vòng đời rõ ràng.

## 3. Vì sao advice đồng bộ chưa đủ?

Xét một phương thức:

```java
CompletableFuture<Result> loadAsync(String id);
```

Một `around advice` thông thường thực hiện:

1. Ghi nhận thời điểm bắt đầu.
2. Gọi `proceed()` (hoặc phương thức đích qua interceptor).
3. Nhận ngay một `CompletableFuture<Result>` **chưa hoàn tất**.
4. Chạy `finally` và trả kết quả là future.
5. Sau đó, ở một luồng khác, tác vụ mới thành công hoặc thất bại.

Nếu đóng span ngay tại bước 4, phép đo phản ánh **thời gian lập lịch/trả future**, không phản ánh **thời gian hoàn tất tác vụ**. Nếu chỉ đặt trace ID vào `ThreadLocal` trước bước 2, bước 5 trên một worker thread chưa chắc thấy trace ID. Hai vấn đề độc lập cần xử lý:

- **Vấn đề truyền ngữ cảnh:** công việc ở phía sau ranh giới có đúng trace/span cha hay không?
- **Vấn đề vòng đời quan sát:** hoạt động được kết thúc lúc trả future hay lúc future hoàn tất?

Dòng thời gian:

```text
Luồng gọi:    [advice mở] -- [proceed -> trả future] -- [advice trả] ---- ...
                                             |
Executor:                                     +--> [task chạy] --> [complete]
Callback:                                                        +--> [record/end]
```

Ở lời gọi đồng bộ, `finally` thường có thể kết thúc observation. Ở lời gọi trả về một kết quả bất đồng bộ, advice cần gắn cơ chế hoàn tất thích hợp, đồng thời vẫn dọn sạch ngữ cảnh đã gắn lên luồng gọi ngay khi rời khỏi lời gọi.

## 4. Quy tắc bất biến: capture → install → execute → restore

Tại mỗi ranh giới bất đồng bộ, thực hiện bốn bước:

1. **Capture:** chụp ngữ cảnh của công việc *tại thời điểm đăng ký/gửi việc*, trước khi rời luồng gọi. Một bản chụp bất biến thường an toàn hơn tham chiếu tới đối tượng trạng thái có thể bị sửa.
2. **Install:** trên luồng thực thi, lưu ngữ cảnh đang có ở luồng ấy và gắn bản chụp vào phạm vi chạy tác vụ.
3. **Execute:** chạy công việc, phát log/tạo span con hoặc thu thập sự kiện theo ngữ cảnh vừa gắn.
4. **Restore:** trong `finally`, trả ngữ cảnh trước đó về đúng trạng thái; nếu trước đó không có thì xóa giá trị của luồng.

Bất biến quan trọng:

> Sau khi task hoặc callback kết thúc (thành công hay ném lỗi), luồng thực thi phải có **đúng ngữ cảnh nó có trước lúc install**.

Vì vậy, **restore không có nghĩa là luôn gọi remove**: khi các scope lồng nhau, xóa thẳng sẽ làm mất ngữ cảnh bên ngoài. Ngược lại, chỉ `set` mà không restore sẽ để ngữ cảnh request A rò sang request B trên cùng worker của thread pool.

Đối với một luồng nhận nhiều tác vụ, mỗi lần submit phải có **snapshot riêng**. Chụp một lần khi tạo executor, rồi dùng lại mãi, sẽ làm mọi tác vụ kế tiếp nhận cùng ngữ cảnh cũ.

## 5. Ví dụ Java 17 hoàn chỉnh: proxy AOP và executor có truyền ngữ cảnh

Ví dụ sau dùng:

- `Context`: giá trị quan sát bất biến (trace ID, span ID).
- `install(...)`/`Scope`: lưu–gắn–khôi phục ngữ cảnh, kể cả khi có lỗi.
- `contextAware(...)`: bộ trang trí `Executor`, chụp ngữ cảnh khi submit và gắn lại khi worker chạy.
- `traced(...)`: interceptor dạng dynamic proxy. Nó gắn ngữ cảnh con khi gọi phương thức và dùng `whenComplete` để ghi nhận **lúc future hoàn tất**.

Lưu nguyên mã vào **AsyncObservationDemo.java**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncObservationDemo {
    record Context(String traceId, String spanId) {}

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    @FunctionalInterface
    interface Scope extends AutoCloseable {
        @Override void close();
    }

    static Scope install(Context next) {
        Context previous = CURRENT.get();
        if (next == null) CURRENT.remove();
        else CURRENT.set(next);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    static Executor contextAware(Executor delegate) {
        return command -> {
            Context captured = CURRENT.get(); // chụp lúc submit
            delegate.execute(() -> {
                try (Scope ignored = install(captured)) {
                    command.run();
                }
            });
        };
    }

    interface Job {
        CompletableFuture<String> execute(String text);
    }

    static final class JobImpl implements Job {
        private final Executor executor;

        JobImpl(Executor executor) { this.executor = executor; }

        @Override
        public CompletableFuture<String> execute(String text) {
            return CompletableFuture.supplyAsync(() -> {
                Context context = CURRENT.get();
                String trace = context == null ? "missing" : context.traceId();
                return text.toUpperCase(Locale.ROOT) + " | " + trace;
            }, executor);
        }
    }

    static Job traced(Job target, AtomicReference<String> completed) {
        return (Job) Proxy.newProxyInstance(
            Job.class.getClassLoader(),
            new Class<?>[] { Job.class },
            (proxy, method, args) -> {
                Context parent = CURRENT.get();
                Context child = new Context(
                    parent == null ? "new-trace" : parent.traceId(), "work-1");
                try (Scope ignored = install(child)) {
                    final CompletableFuture<?> future;
                    try {
                        future = (CompletableFuture<?>) method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        completed.set("complete | " + CURRENT.get().traceId() + " | error");
                        throw ex.getCause();
                    }
                    return future.whenComplete((value, error) -> {
                        try (Scope completionScope = install(child)) {
                            completed.set("complete | " + CURRENT.get().traceId()
                                    + " | " + (error == null ? "ok" : "error"));
                        }
                    });
                }
            });
    }

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            AtomicReference<String> completed = new AtomicReference<>();
            Job service = traced(new JobImpl(contextAware(pool)), completed);
            try (Scope requestScope = install(new Context("trace-42", "request-1"))) {
                System.out.println(service.execute("hello").join());
                System.out.println(completed.get());
                System.out.println(CURRENT.get().spanId());
            }
            System.out.println(CURRENT.get() == null);
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 AsyncObservationDemo.java
java AsyncObservationDemo
```

Kết quả chuẩn:

```text
HELLO | trace-42
complete | trace-42 | ok
request-1
true
```

Diễn giải:

- `trace-42` được chụp trong `contextAware` khi `supplyAsync` gửi việc, nên worker đọc đúng trace ID.
- Khi `traced` rời `proceed` và trả future, scope của luồng gọi trở lại `request-1`; nó không giữ `work-1` treo trên luồng gọi trong thời gian chờ.
- Callback `whenComplete` cài lại ngữ cảnh của hoạt động để ghi kết quả rồi khôi phục giá trị vốn có trên luồng callback.
- `join()` chờ future *đã được gắn callback hoàn tất*, nên dòng `complete ...` đã được ghi trước khi in.
- Sau khi đóng `requestScope`, `CURRENT` của main không còn dữ liệu (`true`).

**Giới hạn cố ý của demo:** `work-1` và `new-trace` là mã cố định để dễ theo dõi; trong thực tế cần sinh ID phù hợp và duy nhất. `AtomicReference` mô phỏng bộ ghi trạng thái chứ không phải hệ thống trace thực; một interceptor tổng quát phải xét nhiều kiểu phương thức, lời gọi `Object`, giá trị trả về khác `CompletableFuture`, lỗi trong phép ghi quan sát và các chính sách hủy. Ví dụ này minh họa một phương thức của giao diện `Job`, không phải bộ AOP production.

## 6. Bao phủ các ranh giới bất đồng bộ phổ biến

### 6.1. Executor, Runnable và Callable

Đối với `Executor.execute(Runnable)`, wrapper ở ví dụ là điểm chèn phù hợp: chụp lúc gửi tác vụ, gắn lúc thực thi, khôi phục khi xong. Với `ExecutorService.submit(Callable<T>)`, nguyên tắc tương tự, nhưng wrapper phải trả kết quả và ném lại ngoại lệ từ `call()`:

```java
// Đoạn minh họa đặt trong lớp đã có CURRENT, Context và Scope.
static <T> java.util.concurrent.Callable<T> wrap(
        java.util.concurrent.Callable<T> task) {
    Context captured = CURRENT.get();
    return () -> {
        try (Scope ignored = install(captured)) {
            return task.call();
        }
    };
}
```

Lưu ý: không chỉ bọc phần `submit` ở luồng gọi; việc cần làm là bọc **thân tác vụ** sẽ chạy ở luồng nhận. Khi một task tiếp tục sinh task con, task con phải chụp ngữ cảnh của nơi nó được sinh ra.

### 6.2. CompletableFuture và chuỗi callback

Một chuỗi gồm `supplyAsync`, `thenApply`, `thenCompose`, `handle`, `whenComplete` có thể chạy qua nhiều ranh giới:

- Các phương thức *Async* có executor tường minh có thể dùng executor được trang trí. Không khai báo executor thường dẫn đến việc chạy trên cơ chế thực thi mặc định; không nên giả định có ngữ cảnh đã được gắn.
- Callback **không có hậu tố Async** có thể chạy trên luồng hoàn thành stage trước đó hoặc ngay tại luồng đăng ký khi stage đã hoàn tất. Không thể đoán luồng callback chỉ từ tên phương thức.
- Nếu cần bảo đảm callback nhận ngữ cảnh **tại lúc đăng ký**, hãy chụp và bọc **chính callback**. Đây là điểm khác với chỉ bọc executor, vì người submit thực tế có thể là luồng hoàn thành khác ngữ cảnh.

Ví dụ hàm bọc callback, dùng chung các thành phần của lớp demo:

```java
static <T, R> java.util.function.Function<T, R> wrap(
        java.util.function.Function<T, R> callback) {
    Context captured = CURRENT.get(); // thời điểm đăng ký callback
    return value -> {
        try (Scope ignored = install(captured)) {
            return callback.apply(value);
        }
    };
}

// Ví dụ sử dụng bên trong phạm vi có ngữ cảnh:
// CompletableFuture<String> normalized =
//     source.thenApply(wrap(value -> value.trim()));
```

Nếu callback trả về một `CompletionStage` khác (ví dụ `thenCompose`), gắn ngữ cảnh vào lúc gọi callback **không bảo đảm** công việc sâu bên trong stage mới được truyền tiếp. Các ranh giới do stage đó sử dụng vẫn phải có cơ chế propagation riêng.

### 6.3. Queue, event loop và bộ lập lịch

Với hàng đợi, cơ chế enqueue/dequeue có thể diễn ra trên hai luồng khác nhau. Chụp ngữ cảnh vào một envelope chứa task/message ở lúc enqueue, gắn quanh handler khi dequeue, rồi restore. Tác vụ định kỳ cần một chính sách riêng: kế thừa ngữ cảnh lúc đăng ký lịch hay bắt đầu trace mới cho mỗi lần kích hoạt? Không tự mặc định mang trace của request ban đầu vào một công việc sẽ lặp nhiều giờ.

Trong event loop, thời gian giữ scope phải ngắn và đúng đoạn thực sự chạy. Không giữ `ThreadLocal` của request qua một thao tác `await` giả định; khi event loop xử lý nhiều request xen kẽ, dữ liệu của một request có thể lây sang request khác.

### 6.4. Qua mạng và tiến trình

Truyền ngữ cảnh nội bộ JVM không tự tạo ra header mạng. Tại phía gửi, serialize các trường propagation được cho phép; tại phía nhận, parse và validate chúng, tạo ngữ cảnh cục bộ phù hợp rồi chạy handler trong scope. Trace ID và quan hệ span có thể nối tiếp xuyên tiến trình, nhưng **ngữ cảnh bảo mật/ủy quyền không nên được tin chỉ vì có mặt trong trace header**. Cần giới hạn kích thước, định dạng và mức độ tin cậy của baggage từ bên ngoài.

## 7. Thiết kế advice theo vòng đời bất đồng bộ

Một around advice quan sát thường có hai phạm vi khác nhau:

**Phạm vi gắn ngữ cảnh trên luồng gọi:** bắt đầu trước khi gọi phương thức đích và đóng trong `finally`/try-with-resources, bất kể phương thức trả đồng bộ, trả future hay ném lỗi ngay.

**Vòng đời observation/span:** bắt đầu tại lúc chặn lời gọi; đối với thao tác trả kết quả bất đồng bộ, kết thúc khi completion tương ứng báo thành công, lỗi hoặc hủy theo hợp đồng của kiểu trả về.

Một khung tư duy:

```text
around(joinPoint):
    parent = currentContext()
    span = startChildObservation(parent)
    try with install(span.context):
        try:
            result = proceed()
        catch immediateError:
            recordFailureAndEnd(span, immediateError)
            throw immediateError

        if result is supportedAsyncHandle:
            return observeCompletion(result, span)  // callback kết thúc span
        else:
            recordSuccessAndEnd(span)
            return result
    finally:
        restoreCallerThreadContext()
```

Đây là **pseudocode**, không phải Java hoàn chỉnh. Trong triển khai thực, tránh ghi kết thúc hai lần khi có đua tranh giữa callback hoàn thành, hủy và timeout. Một bộ ghi kết thúc span nên được bảo vệ bằng trạng thái hoàn tất một lần (ví dụ `AtomicBoolean.compareAndSet(false, true)`), nếu nhiều con đường có thể gọi `end`.

**Lưu ý về kiểu trả về:** gắn `whenComplete` sinh ra một stage *mới*; nếu hợp đồng yêu cầu đúng instance future gốc, cách gắn lời quan sát có thể phải khác. Đối với `CompletionStage`, không được giả định mọi implementation có thể ép về `CompletableFuture`. Đối với publisher/stream lười, thời điểm gọi phương thức có thể chỉ tạo mô tả pipeline: quan sát cần theo **subscription, signal, termination và cancel**, tùy hợp đồng, thay vì đóng tại lúc trả publisher.

**Lỗi và hủy:** phân biệt ba tình huống: (1) `proceed` ném lỗi ngay, (2) future hoàn thành exceptionally, (3) future bị cancel. Trong `CompletableFuture`, hủy được biểu diễn bằng hoàn thành ngoại lệ với `CancellationException`; callback nên phân loại rõ trạng thái hủy nếu công cụ quan sát cần một nhãn riêng. `whenComplete` có thể gặp `CompletionException` ở các tầng bao, vì vậy chỉ unwrap khi đã hiểu hợp đồng và vẫn phải giữ nguyên nguyên nhân phục vụ chẩn đoán.

**Lỗi của chính interceptor:** lỗi khi xuất log/metric/trace không nên làm một tính toán nghiệp vụ đang thành công trở thành thất bại ngoài ý muốn. Chọn chính sách rõ ràng (cách ly lỗi telemetry, ghi nhận ở kênh chẩn đoán khác và không che lỗi gốc); luôn restore ngữ cảnh, kể cả khi recorder lỗi.

## 8. Những lỗi thường gặp và cách nhận diện

| Dấu hiệu | Nguyên nhân điển hình | Cách sửa |
|---|---|---|
| Log trong worker thiếu trace ID | Chỉ gắn `ThreadLocal` ở luồng gọi | Chụp lúc submit/đăng ký và cài lại quanh task hoặc callback |
| Request B mang trace ID của A | Không restore/clear ngữ cảnh ở worker tái sử dụng | Lưu ngữ cảnh cũ, restore trong `finally` |
| Span kết thúc quá sớm | Advice đo tới lúc trả future | Quan sát completion của thao tác bất đồng bộ |
| Span không bao giờ kết thúc | Chỉ nghe success, bỏ qua exceptional/cancel | Gắn handler cho mọi trạng thái kết thúc có liên quan |
| Callback đọc sai parent span | Chỉ bọc executor, snapshot được lấy trên luồng hoàn thành | Chụp ngữ cảnh tại lúc đăng ký callback nếu đó là ngữ nghĩa mong muốn |
| Scope ngoài biến mất sau callback lồng nhau | Luôn `remove()` khi đóng scope | Restore giá trị trước lúc install |
| Tăng memory/cost đáng kể | Chụp đối tượng request lớn, gắn thuộc tính quá nhiều | Snapshot tối thiểu, bất biến; giới hạn cardinality và baggage |
| Method nội bộ không được quan sát | Một cơ chế proxy chỉ intercept lời gọi đi **qua proxy** | Xác định rõ phạm vi join point, đường gọi nội bộ và khả năng weaving |

`InheritableThreadLocal` không phải lời giải phổ quát cho thread pool: nó kế thừa trạng thái khi **tạo thread**, không phản ánh ngữ cảnh của từng lần submit, có thể giữ dữ liệu cũ lâu dài. Không dựa vào kế thừa ngầm để thay cho propagation có kiểm soát. Với virtual thread, vẫn cần xét ngữ cảnh tại nơi task được tạo và tất cả callback/luồng chuyển tiếp; khác biệt mô hình thread không tự giải quyết vòng đời span.

## 9. Kiểm thử hành vi quan sát

Nên kiểm thử bằng các tình huống có khả năng bắt lỗi ngữ cảnh, thay vì chỉ kiểm tra output nghiệp vụ:

1. **Luồng worker tái sử dụng:** dùng pool một luồng, lần lượt xử lý request A và B với trace khác nhau; B tuyệt đối không thấy trace của A.
2. **Task lỗi:** body ném ngoại lệ; kiểm tra ngoại lệ gốc vẫn được trả, completion được ghi là lỗi, ngữ cảnh worker được restore.
3. **Callback hoàn thành ngay:** stage đã hoàn tất trước khi đăng ký callback; callback có thể chạy ngay trên luồng đăng ký nhưng phải thấy đúng snapshot.
4. **Scope lồng nhau:** cài parent rồi child; đóng child phải trở lại parent, đóng parent phải trở lại trạng thái ban đầu.
5. **Fan-out/fan-in:** một lời gọi sinh nhiều nhánh song song; mọi nhánh cùng trace nhưng có span ID riêng; mỗi nhánh kết thúc độc lập và không ảnh hưởng nhau.
6. **Hủy và timeout:** xác nhận span/observation có trạng thái đúng theo hợp đồng và không bị end hai lần.
7. **Không có ngữ cảnh ban đầu:** tác vụ không kế thừa ngẫu nhiên dữ liệu từ worker; chính sách tạo root trace hoặc không tạo trace cần rõ ràng.
8. **Lỗi telemetry:** giả lập recorder ném lỗi; ngữ cảnh vẫn được dọn sạch, kết quả nghiệp vụ giữ đúng chính sách đã chọn.

Trong kiểm thử có cạnh tranh, nên dùng `CountDownLatch` hoặc barrier điều khiển thứ tự chạy, tránh dùng `Thread.sleep` làm bằng chứng chắc chắn. Đối chiếu trace ID/span ID và trạng thái scope trực tiếp tại mỗi điểm chặn thay vì chỉ suy từ vị trí log.

## 10. Hiệu năng và quyết định thiết kế

Propagation tạo chi phí capture, phân bổ wrapper, install/restore và thao tác với bộ ghi quan sát. Với tác vụ rất nhỏ, chi phí này có thể thấy rõ; hãy đo bằng workload thực tế trước khi tối ưu. Không truyền mọi thuộc tính và không sinh span con cho mọi phép biến đổi vụn vặt: xác định granularity theo giá trị chẩn đoán.

Cần xác định **ranh giới mà instrumentation sở hữu**: bọc tại executor, tại API đăng ký callback, tại message handler, hoặc phối hợp nhiều nơi. Bọc hai lần cùng một ranh giới có thể tạo scope thừa hoặc span trùng; ngược lại, chỉ bọc lời gọi vào phương thức ban đầu sẽ bỏ sót các continuation phát sinh phía sau.

Một chính sách tốt cần trả lời rõ:

- Snapshot lấy ở thời điểm **gọi**, **submit**, **đăng ký callback**, hay **subscription**?
- Span đại diện thời gian xếp hàng cộng thực thi, chỉ thời gian thực thi, hay toàn bộ kết quả end-to-end?
- Khi parent span đã kết thúc mà child vẫn chạy, quan hệ được biểu diễn thế nào?
- Ai sở hữu việc kết thúc observation, và làm sao bảo đảm kết thúc đúng một lần?
- Ngữ cảnh nào được truyền qua mạng và thuộc tính nào phải loại bỏ vì nhạy cảm hoặc quá nhiều giá trị khác nhau?

## 11. Ghi nhớ

**AOP quan sát một lời gọi không đồng nghĩa với quan sát trọn công việc bất đồng bộ.** Để trace và log đáng tin, phải truyền snapshot qua đúng ranh giới thực thi; để tránh rò ngữ cảnh, phải khôi phục trạng thái cũ của từng luồng trong `finally`; để đo đúng vòng đời, phải chờ tín hiệu hoàn tất thích hợp và ghi nhận success, error hoặc cancel. Ba thao tác **propagate, restore, finish** bổ sung cho nhau, nhưng không thể thay thế nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-boundaries-005">Không giữ tài nguyên theo lời gọi ngắn cho công việc kéo dài</a>

<details>
<summary>Xem chi tiết</summary>

# Ranh giới bất đồng bộ trong AOP: không giữ tài nguyên của lời gọi ngắn cho công việc kéo dài

## 1. Vấn đề cốt lõi

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), một advice có thể bao quanh lời gọi hoặc lần thực thi phương thức để bổ sung logging, đo thời gian, tracing, kiểm tra quyền hoặc quản lý tài nguyên. Với phương thức đồng bộ, thời điểm phương thức kết thúc thường gần với thời điểm công việc kết thúc. Với phương thức bất đồng bộ, hai mốc này tách rời:

1. **Lời gọi ngắn:** phương thức tạo, đăng ký hoặc gửi một công việc rồi trả về rất nhanh (chẳng hạn trả về CompletionStage, Future, một đối tượng phát luồng dữ liệu, hoặc chỉ trả về void).
2. **Công việc kéo dài:** phần xử lý thực tế tiếp tục chạy ở executor, luồng khác, bộ lập lịch hoặc sau một sự kiện bên ngoài; có thể thành công, thất bại, timeout hoặc bị hủy.

**Quy tắc thiết kế:** tài nguyên có vòng đời gắn với lời gọi ngắn phải được đóng khi lời gọi đó kết thúc. Tài nguyên cần cho công việc kéo dài phải được sở hữu, mở và giải phóng theo vòng đời của chính công việc. Không kéo dài một scope chỉ vì muốn advice nhìn thấy kết quả cuối cùng.

Ví dụ tài nguyên của lời gọi ngắn: một scope ngữ cảnh đang gắn với luồng hiện tại, một guard/lock cục bộ, trạng thái đếm thời gian cho invocation, một đối tượng đại diện request chỉ hợp lệ khi lời gọi còn sống. Ví dụ tài nguyên của công việc: kết nối tới hệ thống ngoài, file handle, giao dịch riêng của tác vụ, tài nguyên dành riêng cho từng subscription. Mỗi loại có chủ sở hữu và quy tắc đóng khác nhau; không suy luận thời gian sống từ tên lớp.

## 2. Vì sao around advice thông thường dễ bị hiểu sai?

Giả sử một phương thức khởi động xử lý và lập tức trả về CompletionStage:

~~~java
CompletionStage<Report> generateReportAsync(ReportRequest request);
~~~

Đây là khai báo API minh họa; không phải một chương trình độc lập. Một bộ chặn kiểu around có thể thực hiện:

~~~text
Vào advice
  -> trước proceed(): bắt đầu lời gọi
  -> proceed(): phương thức trả về CompletionStage
  -> sau proceed(): lời gọi đã quay lại, công việc có thể chưa xong
Rời advice
  -> sau đó: CompletionStage mới hoàn thành, thất bại hoặc bị hủy
~~~

Hệ quả đối với các kiểu advice phổ biến:

| Điểm chặn AOP | Điều có thể kết luận | Điều không thể kết luận |
| --- | --- | --- |
| Before | Phương thức sắp được gọi/thực thi | Công việc bất đồng bộ đã bắt đầu hay chắc chắn sẽ hoàn thành |
| After returning | Phương thức đã trả về bình thường một giá trị, có thể là handle của công việc | Handle đó đã thành công |
| After throwing | Phương thức ném lỗi đồng bộ trong lúc được gọi/thực thi | Mọi lỗi sẽ xuất hiện sau đó trong công việc |
| After/finally | Lần thực thi được chặn đã rời khỏi phương thức | Toàn bộ tác vụ bất đồng bộ đã kết thúc |
| Around kết hợp callback hoàn tất | Có thể quan sát mốc trả về lẫn mốc hoàn tất, nếu API hỗ trợ | Callback tự động sở hữu quyền đóng mọi tài nguyên liên quan |

AOP cần xác định đúng **join point** và **vòng đời được quan sát**. Tùy kiểu weaving/proxy/interception, bộ chặn có thể áp dụng tại lời gọi, lần thực thi hoặc một điểm trong chuỗi xử lý; không phải mọi lệnh chạy bất đồng bộ đều tự động đi qua cùng join point.

## 3. Phân biệt bốn vòng đời

**Vòng đời invocation:** bắt đầu ngay khi đi vào join point, kết thúc khi phương thức trả về hoặc ném lỗi đồng bộ. Các thao tác trước/sau proceed(), lock bảo vệ đoạn gọi ngắn và scope gắn với lời gọi thuộc vòng đời này.

**Vòng đời task:** từ khi công việc được lên lịch hoặc bắt đầu thực thi cho tới lúc công việc thực tế dừng. Tài nguyên chỉ dùng trong task nên được cấp phát tại chỗ thực thi và đóng trong finally/try-with-resources của task.

**Vòng đời kết quả bất đồng bộ:** một CompletionStage hoàn tất bằng giá trị hoặc lỗi. Mốc này hữu ích cho metric và trace kết quả. Nhưng stage có thể bị hoàn tất cưỡng bức vì timeout/hủy trong khi mã nền vẫn đang chạy; hoàn tất của handle không luôn chứng minh tài nguyên nền đã được thu hồi.

**Vòng đời ngữ cảnh truyền qua ranh giới:** trace ID, correlation ID, identity tối thiểu hoặc metadata bất biến có thể được chụp tại lúc gửi việc, rồi được tái gắn một cách kiểm soát khi task thực chạy. Không được giả định ThreadLocal, transaction context, request object hay lock của luồng gọi sẽ tự đi theo task.

Đặt câu hỏi cho mỗi tài nguyên: **Ai mở? Ai dùng? Ai đóng? Đóng ở mốc nào kể cả khi có lỗi, hủy hoặc timeout?** Một tài nguyên không có câu trả lời rõ ràng là dấu hiệu cần xem lại ownership.

## 4. Sai lầm: kéo scope invocation qua công việc bất đồng bộ

Một thiết kế dễ mắc lỗi là mở tài nguyên trước proceed(), rồi giữ nó cho đến khi CompletionStage hoàn tất:

~~~text
around:
    resource = openShortLivedResource()
    stage = proceed()
    registerOnCompletion(stage, () -> resource.close())
    return stage
~~~

Đây là giả mã mô tả **phản ví dụ**, không phải một lời khuyên triển khai. Nó có thể gây ra:

- **Giữ tài nguyên quá lâu:** connection, lock, permit hoặc object của request bị giữ cả trong thời gian tác vụ đang đợi hàng đợi hoặc I/O.
- **Sai luồng:** scope đang gắn với thread A được đóng trong callback ở thread B; những abstraction có thread affinity có thể lỗi hoặc làm hỏng trạng thái.
- **Rò rỉ:** task không hoàn tất, không có callback, hoặc đường đi synchronous exception không đóng đúng tài nguyên.
- **Nghẽn hệ thống:** một request ngắn tiêu tốn tài nguyên giới hạn trong suốt công việc nhiều phút, gây cạn pool và tăng tail latency.
- **Sai ranh giới giao dịch:** tài nguyên/giao dịch của invocation bị dùng ngoài phạm vi mà nó được bảo đảm còn hợp lệ.

Chính xác hơn, **không phải mọi tài nguyên đều bắt buộc đóng ngay khi phương thức trả về**: một tài nguyên được thiết kế riêng cho task có thể sống dài. Lỗi nằm ở việc lấy tài nguyên thuộc scope ngắn và mặc nhiên biến nó thành tài nguyên thuộc scope dài.

## 5. Mẫu đúng: tách scope gọi và scope thực thi

Một thiết kế có thể có ba lớp trách nhiệm:

1. **Advice tại invocation:** chụp timestamp và metadata đủ nhỏ, xác định operation, gọi proceed() đúng một lần, xử lý lỗi đồng bộ, giải phóng mọi scope ngắn trước khi return.
2. **Chủ sở hữu task:** khi mã xử lý thực sự chạy, tự lấy tài nguyên cần dùng và đóng trong finally/try-with-resources của chính task; truyền qua ranh giới chỉ dữ liệu cần thiết, có vòng đời độc lập.
3. **Bộ quan sát completion:** nếu kiểu trả về có tín hiệu hoàn tất, đăng ký callback nhẹ để cập nhật outcome và duration. Callback không được giữ lock/connection của invocation và không được nuốt hoặc sửa lỗi nghiệp vụ ngoài ý muốn.

Ví dụ ý tưởng:

~~~text
Caller / advice            Executor / task                  Completion observer
----------------           ----------------                 -------------------
capture immutable context
open invocation scope
try:
    stage = proceed()
finally:
    close invocation scope
return stage                 start task
                             open task resource
                             try: do real work
                             finally: close task resource
                             finish stage ----------------> record outcome/duration
~~~

Ở đây lời gọi ngắn có thể đã kết thúc trước khi task bắt đầu. Nếu task không thể chạy hoặc bị từ chối lên lịch, đường báo lỗi của thao tác submit/proceed cần được xử lý riêng.

## 6. Ví dụ Java 17 chạy được: around-style wrapper quan sát CompletionStage

Ví dụ sau mô phỏng trách nhiệm của around advice bằng một hàm Java thuần để có thể biên dịch và thử nghiệm trực tiếp, không phụ thuộc vào một thư viện weaving cụ thể. Nó minh họa đúng thời điểm đóng tài nguyên của task và đúng thời điểm quan sát completion.

Tạo tệp **AsyncBoundaryDemo.java**:

~~~java
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class AsyncBoundaryDemo {

    static final class TaskResource implements AutoCloseable {
        TaskResource() {
            System.out.println("RESOURCE_OPEN");
        }

        String process() {
            return "done";
        }

        @Override
        public void close() {
            System.out.println("RESOURCE_CLOSE");
        }
    }

    static <T> CompletionStage<T> observeAround(
            Supplier<? extends CompletionStage<T>> proceed) {

        long startedNanos = System.nanoTime();
        final CompletionStage<T> original;

        try {
            // Tương đương proceed(): lời gọi này chỉ trả về handle.
            original = Objects.requireNonNull(proceed.get(), "stage");
        } catch (RuntimeException | Error failure) {
            System.out.println("SYNC_FAILURE");
            throw failure;
        }

        System.out.println("METHOD_RETURNED");

        // Đo kết quả của stage, không kéo TaskResource ra khỏi task.
        return original.whenComplete((value, failure) -> {
            long elapsedNanos = System.nanoTime() - startedNanos;
            // Trong hệ thống thật, đưa elapsedNanos vào metric.
            if (failure == null) {
                System.out.println("ASYNC_SUCCESS");
            } else {
                System.out.println("ASYNC_FAILURE");
            }
        });
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch mayStart = new CountDownLatch(1);

        try {
            CompletionStage<String> result = observeAround(
                    () -> CompletableFuture.supplyAsync(() -> {
                        await(mayStart);
                        // Resource thuộc task: mở khi chạy, đóng trước
                        // khi supplier hoàn thành, kể cả khi process() lỗi.
                        try (TaskResource resource = new TaskResource()) {
                            return resource.process();
                        }
                    }, executor));

            System.out.println("CALLER_CONTINUES");
            mayStart.countDown();

            // join() chỉ để chương trình demo đợi và in kết quả.
            System.out.println(
                    "RESULT=" + result.toCompletableFuture().join());
        } finally {
            mayStart.countDown();
            executor.shutdown();
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac AsyncBoundaryDemo.java
java AsyncBoundaryDemo
~~~

Đầu ra xác định bởi CountDownLatch và chuỗi hoàn tất:

~~~text
METHOD_RETURNED
CALLER_CONTINUES
RESOURCE_OPEN
RESOURCE_CLOSE
ASYNC_SUCCESS
RESULT=done
~~~

**Giải thích:** mayStart khiến công việc chưa thể mở TaskResource khi đoạn gọi ngắn vẫn đang chạy; sau khi người gọi tiếp tục, resource mới được tạo bên trong supplier. Try-with-resources đóng resource trước khi stage của supplier hoàn tất. Callback whenComplete quan sát kết quả của stage; nó không giữ resource nào. Không nên đặt join()/get() vào around advice ở luồng request vì như vậy vô tình biến API bất đồng bộ thành chờ đồng bộ.

Lưu ý khi vận dụng mẫu này:

- whenComplete() tạo **một stage phụ thuộc mới**. Nếu callback quan sát tự ném lỗi, stage trả về có thể bị ảnh hưởng (đặc biệt khi upstream thành công). Vì vậy callback phải nhẹ, an toàn, không ném exception ra ngoài; nếu yêu cầu giữ nguyên identity/semantics của stage gốc, cần lựa chọn kỹ cách đăng ký side effect rồi trả chính stage gốc.
- Nếu upstream đã thất bại, callback nhận Throwable biểu diễn failure; tùy đường đi có thể có CompletionException bọc nguyên nhân. Logging có thể unwrap để phân loại nhưng không được vô tình nuốt lỗi.
- Callback không nhất thiết chạy trên luồng của người gọi và có thể chạy ngay lúc đăng ký nếu stage đã hoàn tất. Không dựa vào ThreadLocal hiện tại; callback chậm có thể làm chậm luồng hoàn tất.
- Stage phụ thuộc trả về bởi whenComplete không tự động truyền hủy ngược đến tác vụ gốc. Chính sách cancellation cần được định nghĩa tại nơi sở hữu công việc.

## 7. Tách quan sát kết quả khỏi sở hữu tài nguyên

**Quan sát** và **sở hữu** là hai trách nhiệm khác nhau. Aspect quan sát completion để ghi metric không đồng nghĩa Aspect phải mở/đóng file, transaction hay network connection của task. Đặt cleanup ở đơn vị sở hữu tài nguyên sẽ giúp xử lý đầy đủ các đường thoát:

~~~java
// Đoạn minh họa đặt BÊN TRONG chính thân task.
try (InputStream input = openInput()) {
    process(input);
} finally {
    releaseTaskPermit();
}
~~~

Đoạn trên chỉ là mảnh mã, cần định nghĩa openInput(), process() và releaseTaskPermit() trong ứng dụng. Nếu releaseTaskPermit() có thể được gọi mà permit chưa được cấp, cần theo dõi quyền sở hữu bằng trạng thái cục bộ; finally không tự sửa lỗi cấp phát thất bại một phần.

Với một scope theo thread:

~~~text
invoke thread:
    attach context for invocation
    try: submit immutable context snapshot + task
    finally: detach invocation context

worker thread:
    attach reconstructed task context
    try: process task
    finally: detach worker context
~~~

Không truyền trực tiếp một scope đang mở để thread khác đóng hộ. Với tác vụ chạy lại hoặc chia nhánh, mỗi nhánh cần ngữ cảnh/scope tương ứng, tránh cùng đóng một đối tượng nhiều lần trừ khi hợp đồng API công khai cho phép.

## 8. Lựa chọn điểm hoàn tất theo kiểu API

| Kiểu API | Mốc kết thúc invocation | Cách quan sát kết quả dài hạn | Rủi ro cần xử lý |
| --- | --- | --- | --- |
| CompletionStage / CompletableFuture | Khi trả về stage | whenComplete hoặc completion hook tương đương | Lỗi callback, dependent stage, cancellation và timeout |
| Future thông thường | Khi trả về Future | Chủ sở hữu task/executor báo kết quả; có thể dùng adapter có quản lý phù hợp | Không có callback completion chuẩn; không chặn advice bằng get() |
| Publisher/lazy stream | Khi trả về đối tượng mô tả stream | Tín hiệu subscribe/onNext/onError/onComplete/cancel tại lifecycle subscription | Có thể chưa chạy cho đến lúc subscribe; nhiều subscription và hủy |
| void submit / fire-and-forget | Sau khi gửi việc | Job ID, event, task wrapper hoặc hệ thống theo dõi riêng | Không có handle để advice suy ra trạng thái cuối |
| Callback-based API | Sau khi đăng ký callback | Wrapper trên callback kết quả, tuân hợp đồng callback | Callback nhiều lần, gọi đồng bộ hay bất đồng bộ, thay đổi thứ tự |
| Chuỗi task phân nhánh | Sau khi gửi nhánh ban đầu | Quan sát stage tổng hợp/join chính thức của nghiệp vụ | Hoàn thành một nhánh không phải toàn bộ workflow hoàn thành |

Với Publisher, **không tự subscribe chỉ để đo**: hành động đó có thể kích hoạt công việc thêm một lần. Công cụ AOP phải hiểu tín hiệu kết thúc hoặc hủy của từng subscription. Với Future đơn giản, cố gọi get() trong advice vừa blocking vừa có thể gây deadlock nếu đang chiếm tài nguyên mà worker cần.

## 9. Các lỗi đồng bộ và bất đồng bộ phải có đường xử lý riêng

Một around advice có thể gặp ít nhất các tình huống sau:

- **proceed() ném trực tiếp:** hoàn trả/lan truyền lỗi đồng bộ và đóng scope invocation ngay tại đó. Không có stage hợp lệ để đăng ký observer.
- **proceed() trả stage đã thất bại:** bản thân lời gọi đã trả bình thường; observer completion nhìn thấy lỗi. Không được coi là thành công chỉ vì after-returning đã chạy.
- **Task thất bại sau khi return:** task đóng resource của mình trong finally; completion observer ghi nhận failure; lỗi nghiệp vụ phải tiếp tục tới người dùng stage theo hợp đồng API.
- **Task không được executor nhận:** việc submit có thể ném RejectedExecutionException ngay trong proceed(), tức lỗi đồng bộ; không tạo ra một workflow tưởng tượng chưa được lên lịch.
- **Task bị hủy/timeout:** biết chính xác tín hiệu nào hoàn tất handle và tín hiệu nào xác nhận mã nền thật sự dừng. Cơ chế hoàn tất exceptionally hoặc timeout trên CompletableFuture không bảo đảm code đang chạy bị ngắt hay đã cleanup.
- **Observer tự lỗi:** bảo vệ đường ghi metric/logging, tránh observer biến một tác vụ thành công thành thất bại giả hoặc che dấu nguyên nhân gốc.

Thường nên ghi riêng các trường trạng thái **INVOCATION_RETURNED**, **SCHEDULED/REJECTED**, **TASK_STARTED**, **COMPLETED/FAILED/CANCELLED**, và **RESOURCE_RELEASED** khi chúng thực sự có ý nghĩa trong kiến trúc. Không ghi trạng thái không thể chứng minh.

## 10. Timeout, cancellation và cleanup: ba sự kiện không đồng nhất

Giả sử một job xử lý I/O kéo dài. Người dùng timeout sau 2 giây, nhưng task nền cần thêm thời gian để ngừng hoặc không hỗ trợ ngắt.

- **Timeout của người chờ** nghĩa là không chờ thêm kết quả; nó không chứng minh I/O đã kết thúc.
- **Cancel của handle** có thể chỉ chuyển trạng thái của đại diện kết quả. Việc có gửi tín hiệu ngắt và có ngừng thực tế hay không tùy API/implementation.
- **Cleanup của tài nguyên thực** phải chạy tại owner/task khi mã xử lý rời phạm vi sử dụng tài nguyên.

Vì vậy không được gắn việc đóng một connection còn đang được task sử dụng vào callback timeout ở bên ngoài mà không có giao thức dừng an toàn. Nên thiết kế cooperative cancellation: task kiểm tra tín hiệu hủy, đặt timeout ở thao tác I/O thích hợp, giải phóng trong finally, rồi phát tín hiệu kết thúc thực sự. Nếu có close sớm để buộc thao tác nền dừng, đó phải là hành vi được API tài nguyên cho phép và được đồng bộ hóa.

Đặc biệt, completion của một dependent stage không nhất thiết đồng nhất với lifecycle của original task; phải đo đúng cấp độ mình đang báo cáo.

## 11. Context propagation qua ranh giới

Một Aspect thường chụp correlation ID hoặc thông tin trace ở điểm vào phương thức. Khi chuyển việc qua executor, chỉ nên chuyển **snapshot dữ liệu bất biến, tối thiểu và được phép sử dụng**. Ở worker, tạo scope mới và đóng nó tại chính worker.

**Không nên:**

- Dựa vào ThreadLocal tự được truyền từ caller sang pool thread.
- Mang đối tượng mutable request/session hoặc một transaction đang mở của caller vào background task.
- Giữ một active tracing scope thuộc thread caller và đóng tại completion callback chạy ở thread khác.
- Lưu dữ liệu nhạy cảm trong nhãn metric có cardinality lớn hoặc thông tin không cần thiết.

**Nên:**

- Chụp trace ID/correlation ID, deadline và thông tin cần thiết theo dạng rõ ràng.
- Phân biệt span của việc submit và span của task nếu hai vòng đời khác nhau; biểu diễn quan hệ cha/con hoặc liên kết theo hệ thống tracing.
- Tái gắn rồi tháo ngữ cảnh bên worker trong try/finally, ngay cả khi executor tái sử dụng thread.
- Truyền deadline như một giá trị; để task tự quản lý timeout và cleanup của nó.

## 12. Thiết kế pointcut và advice ở mức nâng cao

Để áp dụng nguyên tắc một cách nhất quán, cần xác định:

**Phạm vi pointcut.** Chọn đúng các method công khai có hợp đồng async hoặc các điểm submit/execute cần quan sát. Không quét tràn lan mọi phương thức trả về CompletionStage: chúng có thể là wrapper, helper hoặc internal callback, dẫn đến đo trùng và khiến observer kích hoạt lại chính advice.

**Loại giá trị trả về.** Không ép kiểu ngầm mọi kết quả sang CompletionStage. Nếu API có thể trả null, hãy có chính sách rõ ràng (xem null là vi phạm hợp đồng hay xử lý như trả về bình thường). Nếu phương thức trả void hoặc Future đơn giản, chọn kỹ thuật hook thích hợp từ bảng ở trên.

**Chỉ gọi proceed() một lần.** Gọi hai lần có thể submit hai task hoặc gây side effect lặp. Nếu advice chủ ý hỗ trợ retry, đó là chính sách nghiệp vụ riêng, cần bảo đảm idempotency và sở hữu resource cho từng attempt.

**Tính đúng đắn khi nested advice.** Nếu nhiều Aspect cùng gắn callback, định nghĩa rõ thứ tự và mốc thời gian đo; tránh đóng cùng resource hai lần, đếm success lặp hoặc coi hoàn tất của wrapper stage là hoàn tất của task gốc.

**Reentrancy và recursion.** Metric logger hoặc callback có thể gọi phương thức cũng khớp pointcut; cần loại trừ các điểm nội bộ hoặc dùng guard có cleanup chắc chắn. Cẩn thận với guard ThreadLocal khi callback chạy ở thread khác.

**Chi phí thực thi.** Mỗi callback tạo thêm allocation và có thể giữ tham chiếu tới object được capture tới khi stage hoàn tất. Không capture toàn bộ request, resource, đối tượng lớn hoặc closure phụ thuộc scope đã đóng. Giới hạn label metric và kích thước log.

**Tách synchronous duration và end-to-end duration.** Duration của proceed() là thời gian lời gọi trả về; duration của completion là thời gian đến tín hiệu hoàn tất. Nếu task nằm trong queue, chỉ số end-to-end còn bao gồm thời gian chờ; cần thêm dấu mốc task start nếu muốn đo thời gian xử lý thuần.

## 13. Kịch bản kiểm thử cần có

Nên kiểm thử với latch/barrier để điều khiển thời điểm, tránh dùng sleep() mong đợi thread scheduler ngẫu nhiên:

1. **Trả về trước khi task bắt đầu:** lời gọi thoát và scope invocation đã đóng; task chỉ mở tài nguyên của nó sau khi được phép chạy.
2. **Hoàn tất thành công:** observer nhìn thấy đúng một success; task resource đã được đóng; không có blocking ở advice.
3. **Lỗi đồng bộ khi submit:** scope invocation đóng; lỗi được ném tiếp; không tạo callback cho stage không tồn tại.
4. **Lỗi bất đồng bộ:** completion được ghi failure, exception còn quan sát được ở phía caller, task resource đóng qua finally.
5. **Cancel và timeout:** kiểm chứng riêng trạng thái handle, task thực dừng và mốc resource closed; không suy diễn một mốc thay cho mốc khác.
6. **Thread pool tái sử dụng thread:** context của task trước không rò sang task sau; scope attach/detach chạy đúng thread.
7. **Nested/recursive pointcut:** không đo trùng ngoài chủ ý, không gọi proceed() nhiều lần, không có vòng lặp instrumentation.
8. **Observer thất bại:** logic metric/logging không phá hỏng kết quả nghiệp vụ theo chính sách đã chọn.

Một assertion đặc biệt có giá trị: tại thời điểm method đã return nhưng task còn bị giữ bởi latch, **số invocation scope đang mở phải bằng 0**, trong khi task resource có thể chưa mở. Sau khi task thực kết thúc, **số task resource còn mở cũng phải bằng 0**.

## 14. Checklist áp dụng trong thiết kế thực tế

- Xác định ranh giới invocation, task, completion và context thay vì gộp chúng.
- Gắn open/close vào đúng owner; sử dụng finally hoặc try-with-resources tại nơi tài nguyên thật sự được sử dụng.
- Chỉ đưa qua async boundary dữ liệu đủ nhỏ, hợp lệ và độc lập về vòng đời.
- Quan sát completion qua cơ chế do chính loại API cung cấp; không blocking trong advice.
- Giữ nguyên hành vi lan truyền lỗi, cancel và semantics của return value.
- Phân biệt timeout của handle với việc task đã dừng và tài nguyên đã được đóng.
- Kiểm thử đường thành công, lỗi, timeout, cancel, từ chối submit, callback lỗi và thread reuse.

**Nguyên tắc nhớ lâu:** lời gọi trả về chỉ cho biết *lời gọi* đã kết thúc. Công việc dài hạn phải tự sở hữu tài nguyên và tự giải phóng đúng lúc; Aspect có thể quan sát mốc hoàn tất mà không buộc tài nguyên ngắn hạn sống theo nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-boundaries-006">Can thiệp vào luồng sự kiện và nguy cơ phá vỡ áp lực ngược</a>

<details>
<summary>Xem chi tiết</summary>

# Can thiệp vào luồng sự kiện và nguy cơ phá vỡ áp lực ngược trong AOP

## 1. Bài toán và mục tiêu

Aspect-Oriented Programming (AOP) cho phép tách những mối quan tâm cắt ngang như đo thời gian, ghi log, tracing, kiểm soát truy cập và thống kê ra khỏi luồng xử lý nghiệp vụ. Với lời gọi đồng bộ, một advice dạng `around` thường chạy trước/sau một phương thức và quan sát được điểm trả về hoặc ngoại lệ ngay tại đó. Với luồng sự kiện bất đồng bộ, **phương thức trả về không đồng nghĩa với công việc đã kết thúc**: kết quả có thể xuất hiện trên một luồng khác, nhiều lần, hoặc không bao giờ được phát ra nếu có hủy.

Rủi ro quan trọng nhất xuất hiện khi aspect vô tình biến một kênh có kiểm soát nhu cầu thành kênh đẩy dữ liệu quá nhanh, gây tràn hàng đợi, tăng độ trễ, cạn bộ nhớ hoặc làm sai hợp đồng của producer và consumer. Sau bài này, người học cần phân biệt thời điểm lời gọi với vòng đời sự kiện, nhận ra các can thiệp phá vỡ backpressure, và thiết kế một advice theo dõi tín hiệu mà vẫn bảo toàn hành vi của luồng.

## 2. Mô hình tín hiệu và ranh giới bất đồng bộ

Một luồng kiểu Reactive Streams, được Java 9+ phản ánh bằng các interface trong `java.util.concurrent.Flow`, có các vai trò sau:

- **Publisher:** nguồn phát dữ liệu; đăng ký một `Subscriber` qua `subscribe`.
- **Subscriber:** bên nhận dữ liệu; được gọi `onSubscribe`, `onNext`, và cuối cùng là `onError` hoặc `onComplete`.
- **Subscription:** kênh điều khiển giữa subscriber và publisher; `request(n)` thông báo nhu cầu, `cancel()` dừng việc nhận.
- **Demand (nhu cầu):** số phần tử subscriber còn cho phép gửi. Lời gọi `request(n)` có thể cộng dồn; `n` phải dương.

Dòng dữ liệu đi xuôi, còn nhu cầu đi ngược:

~~~mermaid
flowchart LR
    P[Publisher] -->|onNext / onError / onComplete| A[Aspect quan sát tín hiệu]
    A -->|chuyển tiếp tín hiệu| S[Subscriber]
    S -->|request n / cancel| A
    A -->|chuyển tiếp demand / cancel| P
~~~

Hợp đồng cần bảo toàn: trong một `Subscription`, số `onNext` đã phát không được vượt quá tổng nhu cầu hợp lệ đã được yêu cầu, sau khi tính phần tử đã gửi; `cancel` phải được chuyển tiếp; tín hiệu kết thúc chỉ xảy ra một lần và không được phát dữ liệu sau khi kết thúc. `request(n)` là **giới hạn cấp phép**, không phải yêu cầu publisher nhất thiết phải phát đủ `n` phần tử.

Một số hệ thống truyền sự kiện kiểu callback, event bus hoặc listener chỉ hỗ trợ `emit` / `onEvent` mà **không có cơ chế demand**. Khi đó không thể tự tuyên bố là đã bảo toàn backpressure; phải xác định riêng chiến lược hàng đợi, từ chối, làm chậm nguồn, gộp hoặc bỏ sự kiện theo hợp đồng nghiệp vụ.

### Advice thực thi ở thời điểm nào?

Giả sử một phương thức trả về `CompletionStage<T>` hoặc `Flow.Publisher<T>`:

1. `before` hoặc `around` tại **lời gọi phương thức** chỉ chứng kiến thao tác tạo/trả về đối tượng bất đồng bộ; nó chưa biết kết quả cuối.
2. Callback trên `CompletionStage` có thể chạy lúc stage hoàn tất, đôi khi ngay trên thread hoàn tất stage; callback nặng sẽ làm chậm chính luồng xử lý đó.
3. Với `Publisher`, việc nhận đối tượng publisher, gọi `subscribe`, nhận `onSubscribe`, từng `onNext` và tín hiệu cuối là các điểm khác nhau. Có publisher chỉ bắt đầu làm việc sau khi đăng ký; có publisher tạo luồng riêng hoặc phát liên tục.
4. Advice có thể chạy trên thread gọi, thread xử lý sự kiện hoặc thread khác do scheduler/executor quyết định. Không được giả định thread-local context tự đi qua ranh giới này.

Vì thế, đo thời gian từ lúc gọi đến lúc trả về chỉ đo **chi phí tạo tác vụ**. Muốn đo toàn bộ luồng, phải nói rõ đo từ lúc `subscribe` đến tín hiệu cuối, từ lúc `request` đến lúc giao `onNext`, hay từ khi một sự kiện được tạo đến khi xử lý xong.

## 3. Backpressure là gì và vì sao aspect có thể phá vỡ nó?

Đối với một subscriber, có thể hình dung:

`demand_còn_lại = tổng_request_hợp_lệ − số_onNext_đã_giao`

Đây là mô hình tư duy cho một subscription, không phải biến toàn cục dùng chung. Với bộ đệm, phải phân biệt số phần tử đang nằm trong queue với nhu cầu đã được cấp để **giao cho downstream**; buffered event không tự trở thành demand mới.

Một advice dù không sửa dữ liệu vẫn có thể làm sai luồng:

| Can thiệp | Nguy cơ | Cách phù hợp |
| --- | --- | --- |
| Advice chủ động gọi `request(Long.MAX_VALUE)` để “nhìn hết” dữ liệu | Biến nhu cầu hữu hạn thành gần như không giới hạn; upstream có thể tạo quá nhanh | Chỉ chuyển tiếp `request(n)` do downstream đưa ra |
| Advice tự `subscribe` thêm một lần để log | Có thể khởi động tác vụ nguồn lần hai, tạo side effect, tốn tài nguyên, hoặc nhận một bản sao khác | Quan sát subscription thật qua decorator; không tạo subscriber phụ |
| `onNext` ghi log mạng hoặc lưu DB đồng bộ | Chặn thread phát tín hiệu, kéo dài độ trễ, tạo nghẽn trên event loop | Ghi số đếm/timestamp nhẹ; xuất telemetry sang hàng đợi có giới hạn |
| Đưa mọi sự kiện vào queue không giới hạn trong advice | Bộ nhớ tăng dù downstream đang chậm; che khuất backpressure thật | Tránh queue khi không cần; nếu cần, giới hạn sức chứa và xác định chiến lược khi đầy |
| Gọi `request(n + k)` hoặc gọi thêm `request` vì mục đích metrics | Upstream nhận demand lớn hơn mức consumer cho phép | Giữ nguyên giá trị và số lần gọi của downstream |
| Chặn/bỏ một số `onNext` nhưng không điều chỉnh phép đếm demand | Mất cân bằng giữa upstream và downstream, có thể làm upstream chạy mãi để lấp nhu cầu hoặc downstream thiếu dữ liệu | Nếu lọc/biến đổi có trạng thái, dùng toán tử backpressure-aware và chứng minh quy tắc bù demand |
| `onNext` tự gọi lại downstream cùng lúc từ nhiều thread | Phá vỡ tính tuần tự của tín hiệu; race trong subscriber | Giữ callback theo thứ tự, tránh phát thêm tín hiệu ngoài giao thức |
| Nuốt `onError`, quên `cancel` hoặc phát `onComplete` sớm | Hỏng semantics terminal, rò rỉ subscription/tài nguyên | Chuyển tiếp đúng một terminal và hủy khi được yêu cầu |

Ví dụ: consumer xử lý được 100 sự kiện/giây nhưng producer tạo 10.000 sự kiện/giây. Nếu advice tạo hàng đợi vô hạn, số tồn có thể tăng gần 9.900 sự kiện mỗi giây. Sau 60 giây sẽ là khoảng 594.000 sự kiện chưa xử lý (giả sử tốc độ ổn định và chưa có cơ chế điều tiết). Đây là ví dụ về **tích lũy tồn kho**, không phải số liệu benchmark. Giới hạn demand và kích thước buffer giúp hệ thống phản ứng có kiểm soát thay vì giữ tất cả sự kiện.

## 4. Thiết kế aspect an toàn tại ranh giới sự kiện

### 4.1 Quan sát mà không chiếm quyền điều khiển

Khi cần ghi log/tracing ở mức tín hiệu, có thể bọc `Subscriber` và `Subscription`:

- `onSubscribe(original)`: đưa xuống downstream một `Subscription` ủy quyền. `request(n)` phải chuyển **đúng `n`, đúng một lần**, theo thứ tự hợp lệ tới `original.request(n)`. `cancel()` phải chuyển tiếp `original.cancel()`.
- `onNext(item)`: ghi nhận một phép đo ngắn, rồi chuyển **chính item đó** xuống downstream một lần; không bí mật đệm, sao chép thành nhiều lần gửi, tạo subscriber thứ hai hoặc yêu cầu thêm dữ liệu.
- `onError(error)` và `onComplete()`: ghi trạng thái rồi chuyển nguyên tín hiệu tới downstream; không tự thay đổi loại lỗi hay phát tín hiệu tiếp theo.
- Telemetry phải hạn chế ném lỗi. Nếu công cụ metrics/logging thất bại, cơ chế quan sát không nên tự biến thành lỗi của stream; ranh giới bắt lỗi cần được thiết kế rõ ràng.
- Bảo toàn sự tuần tự của callbacks theo giao thức. Không tự đẩy `onNext` lên một executor khác nếu chưa có cơ chế tuần tự hóa, quản lý queue và demand.

Điều này tương ứng với một **decorator/interceptor tại biên subscription**, có thể được cài bằng proxy, weaving bytecode hoặc bọc đối tượng một cách tường minh. Một pointcut ở phương thức tạo publisher thường chưa đủ để thấy từng sự kiện: muốn đo luồng thì advice phải bọc hành vi đăng ký hoặc callback, chứ không chỉ đo thời gian thực thi phương thức.

### 4.2 Ví dụ Java 17+ chạy độc lập: trace tín hiệu mà vẫn tôn trọng request(1)

Mã sau dùng `Flow` và `SubmissionPublisher` của JDK, không phụ thuộc thư viện ngoài. Subscriber chỉ yêu cầu **một phần tử tại một thời điểm**. Lớp `TracingSubscriber` đóng vai trò thành phần quan sát AOP: mọi `request` và `cancel` đều được ủy quyền, không tạo thêm nhu cầu. Ví dụ sử dụng nguồn chỉ có ba phần tử để minh họa hợp đồng tín hiệu; `SubmissionPublisher` **có bộ đệm riêng**, vì vậy `request(1)` giới hạn giao tới subscriber nhưng không đồng nghĩa cấm producer nộp trước nhiều phần tử vào buffer.

Tệp: `BackpressureAspectDemo.java`

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class BackpressureAspectDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);

        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(
                new TracingSubscriber<>(new OneByOneSubscriber(done))
            );

            for (int value = 1; value <= 3; value++) {
                publisher.submit(value);
            }
        }

        if (!done.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Subscriber did not finish");
        }
    }

    static final class TracingSubscriber<T> implements Flow.Subscriber<T> {
        private final Flow.Subscriber<? super T> downstream;

        TracingSubscriber(Flow.Subscriber<? super T> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Flow.Subscription upstream) {
            downstream.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    System.out.println("trace request(" + n + ")");
                    upstream.request(n);
                }

                @Override
                public void cancel() {
                    System.out.println("trace cancel");
                    upstream.cancel();
                }
            });
        }

        @Override
        public void onNext(T item) {
            System.out.println("trace onNext(" + item + ")");
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable error) {
            System.out.println("trace error: " + error);
            downstream.onError(error);
        }

        @Override
        public void onComplete() {
            System.out.println("trace complete");
            downstream.onComplete();
        }
    }

    static final class OneByOneSubscriber implements Flow.Subscriber<Integer> {
        private final CountDownLatch done;
        private Flow.Subscription subscription;

        OneByOneSubscriber(CountDownLatch done) {
            this.done = done;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(Integer value) {
            System.out.println("consume " + value);
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            System.err.println("failed: " + error);
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BackpressureAspectDemo.java
java BackpressureAspectDemo
~~~

Với luồng hoàn tất bình thường, stdout là:

~~~text
trace request(1)
trace onNext(1)
consume 1
trace request(1)
trace onNext(2)
consume 2
trace request(1)
trace onNext(3)
consume 3
trace request(1)
trace complete
~~~

Ở đây người tiêu thụ tự gọi `request(1)` sau mỗi lần xử lý, kể cả sau phần tử cuối vì nó chưa biết publisher sẽ hoàn tất. Trace thấy đúng những lời gọi ấy; không có `request(Long.MAX_VALUE)` do aspect thêm vào. Các tín hiệu của cùng subscription được xử lý tuần tự, nhưng không được suy ra mọi publisher đều dùng chung một thread hoặc có bộ đệm giống `SubmissionPublisher`.

**Giới hạn của mẫu:** Đây là decorator để giải thích nơi đặt advice; chưa phải một runtime weaving hoàn chỉnh. Trong hệ thống thực, phần instrumentation còn cần chính sách xử lý lỗi telemetry, phân bổ tài nguyên và đo tải. Nếu mục đích là đo chính xác tốc độ tạo sự kiện hay kích thước hàng đợi, phải đo thêm ở phía nguồn/buffer; trace `onNext` downstream một mình không cung cấp hai số đó.

### 4.3 Advice trên CompletionStage khác với advice trên Publisher

`CompletionStage<T>` có một kết quả hoàn tất (thành công hoặc thất bại), không có chuỗi `request(n)`/`onNext`. Advice ghi nhận việc trả stage sẽ không thấy thời điểm stage hoàn tất. Có thể gắn callback `whenComplete` để đo terminal, nhưng cần chú ý:

- Callback có thể chạy trên thread hoàn tất stage, hoặc ngay khi đăng ký nếu stage đã xong; tránh I/O chặn hoặc xử lý nặng.
- Telemetry phải tự bắt lỗi và không thay thế kết quả/ngoại lệ của nghiệp vụ. Nếu cần trả về stage cho caller, phải xác định rõ có trả nguyên stage hay stage phụ thuộc mới.
- Hủy `CompletableFuture` không tự bảo đảm hủy mọi tác vụ hoặc I/O gốc; không giả định có backpressure như `Flow.Subscription`.

Khi kết nối stage với stream, cần một toán tử hoặc bridge có hợp đồng cụ thể về cancellation, demand và error. Không thể áp dụng quy tắc của hai cơ chế như thể chúng giống nhau.

## 5. Các can thiệp nâng cao và bất biến cần giữ

### Đưa telemetry sang worker khác

Nếu aspect gửi dữ liệu đo đạc vào queue riêng, queue cũng là một tài nguyên hữu hạn. Cần đặt `capacity`, giới hạn thời gian chờ và chính sách khi đầy: bỏ **bản ghi telemetry** có đếm số bản ghi bị bỏ, lấy mẫu, hoặc chuyển sang bộ đếm tổng hợp. Không được âm thầm bỏ **sự kiện nghiệp vụ** chỉ vì kênh metrics quá tải. Nếu yêu cầu bảo đảm mọi log phải lưu được, chi phí lưu log phải được đưa vào thiết kế throughput và backpressure một cách công khai.

### Thay đổi thứ tự, lập lịch và tốc độ

Một advice tự đổi thread hoặc xếp sự kiện vào executor có thể thay đổi thứ tự, tạo callback chồng lấn, trì hoãn cancellation và làm tăng số event đang bay (`in-flight`). Nếu bắt buộc chèn ranh giới bất đồng bộ, phải thiết kế bộ điều phối có giới hạn, truyền demand ngược, ngừng nhận khi hủy, xử lý lỗi executor và bảo toàn thứ tự theo cam kết của API. Đây là **biến đổi giao thức**, cần kiểm thử như một thành phần xử lý stream thực thụ.

### Filter, retry, fan-out, batching

- **Filter:** bỏ một phần tử vẫn có thể đã tiêu thụ một slot nhu cầu upstream. Muốn đáp ứng nhu cầu downstream đúng nghĩa, toán tử có thể phải yêu cầu bù theo cách an toàn; advice ghi log không nên tự cài logic này.
- **Retry:** đăng ký lại hoặc chạy lại nguồn có thể tạo side effect lần hai. Không retry trong advice nếu chưa xác định tính idempotent, giới hạn số lần, xử lý hủy và demand.
- **Fan-out:** nhiều consumer có tốc độ khác nhau cần chính sách chia sẻ, hàng đợi và xử lý consumer chậm; không thể sao chép một `Subscription` đơn lẻ cho mọi consumer rồi cộng demand tùy tiện.
- **Batching:** gộp `k` phần tử thành một batch làm thay đổi đơn vị demand; phải công bố hợp đồng mới và tính toán lại chuyển đổi nhu cầu giữa hai phía.

### Context, security và lifecycle

Trace ID hoặc thông tin phân quyền thường nằm trong context của lời gọi ban đầu. Qua ranh giới async, `ThreadLocal` có thể mất hoặc lẫn giữa các tác vụ dùng chung thread. Dùng cơ chế truyền context tường minh hoặc scope được quản lý đúng cách; luôn đóng/khôi phục scope tại đúng callback để tránh rò rỉ. Không nên giả định advice đặt trên một phương thức sẽ tự chạy lại trên mọi callback nội bộ.

Đối với lỗi và hủy, ghi nhận riêng ít nhất: `completed`, `failed`, `cancelled`. Nguồn có thể kết thúc sớm khi thiếu dữ liệu, một callback có thể đến gần thời điểm cancellation do cuộc đua đã diễn ra, và không phải mọi callback sau lệnh hủy đều có thể thu hồi tức thời. Đo đạc nên tôn trọng trạng thái thực tế và không giả lập terminal thứ hai.

## 6. Cách áp dụng: quy trình thiết kế một aspect trên event stream

1. **Xác định điểm nối:** advice gắn vào lời gọi tạo publisher, `subscribe`, `Subscription`, từng callback, hay một toán tử xử lý? Vẽ rõ producer, consumer và bất kỳ queue nào.
2. **Ghi hợp đồng tín hiệu:** có `request(n)` hay không, ai nắm quyền `cancel`, có buffer hay không, quy tắc thứ tự và terminal, một hay nhiều subscriber, nóng (`hot`) hay chỉ phát khi đăng ký (`cold`).
3. **Định nghĩa điều gì được quan sát:** số đăng ký, nhu cầu, phần tử đã giao, thời gian xử lý callback, lỗi, hủy hoặc độ sâu queue. Đừng suy diễn số phần tử được sản xuất từ số callback đã thấy.
4. **Giữ một đường điều khiển duy nhất:** truyền `request(n)`/`cancel` nguyên vẹn; không tự `subscribe` để thăm dò hay tăng nhu cầu chỉ để ghi log.
5. **Giữ advice nhẹ và có giới hạn:** thu thập phép đo ngắn, xử lý ngoại lệ telemetry, có chính sách tràn tải cho mọi buffer phụ.
6. **Kiểm thử cả giao thức lẫn áp lực:** nguồn nhanh, consumer chậm, hủy giữa chừng, request không hợp lệ, lỗi ở cả hai phía, hoàn tất ngay, đua giữa các thread và việc đăng ký lại.

### Checklist kiểm thử thực tế

| Tình huống | Kết quả cần quan sát |
| --- | --- |
| Chưa gọi `request` | Không có `onNext` được giao đối với publisher tuân theo demand |
| `request(1)`, nguồn còn dữ liệu | Tối đa một `onNext` trước khi có demand mới |
| `request(2)` rồi `request(3)` | Tổng cấp phép là năm phần tử, không bị nhân đôi do advice |
| Gọi `cancel` | Lệnh hủy tới đúng upstream; không có yêu cầu thêm ngoài ý muốn |
| Nguồn phát `onError` | Downstream nhận đúng tín hiệu lỗi terminal một lần |
| Nguồn phát `onComplete` | Downstream nhận hoàn tất một lần, không thêm `onNext` |
| Log/metrics chậm hoặc hỏng | Có chính sách giới hạn, đường nghiệp vụ giữ hợp đồng đã chọn |
| Subscriber chậm hơn nguồn trong nhiều phút | Buffer và bộ nhớ không tăng vô hạn vì instrumentation |
| Subscribe nhiều lần | Số lần thực thi nguồn phù hợp semantics nguồn; aspect không tự đăng ký thêm |
| Phát hiện race / đổi scheduler | Không phát callback đồng thời trái hợp đồng hoặc tráo thứ tự bất ngờ |

Trong kiểm thử tự động, subscriber điều khiển thủ công demand có ích hơn chỉ đo thời gian chạy. Đối với hệ thống tải cao, hãy theo dõi tốc độ producer/consumer, độ sâu và mức sử dụng buffer, thời gian từ `request` tới `onNext`, độ trễ xử lý, số `cancel` và số telemetry bị bỏ; kết hợp các thước đo mới phân biệt được nút nghẽn ở nguồn, consumer hay chính advice.

## 7. Những nhầm lẫn phổ biến cần tránh

**“Dùng async nghĩa là tự có backpressure.”** Sai: nhiều API bất đồng bộ chỉ trả kết quả về sau; không cung cấp demand. Ngay cả khi downstream yêu cầu từng phần tử, một publisher có buffer vẫn có thể nhận trước nhiều phần tử từ producer.

**“Advice chỉ ghi log thì không ảnh hưởng nghiệp vụ.”** Sai: log đồng bộ có thể chặn thread, bản ghi không giới hạn có thể chiếm bộ nhớ, và một exception phát sinh trong instrumentation có thể thay đổi đường lỗi. Tính xuyên suốt của advice phụ thuộc cả tài nguyên lẫn semantics.

**“Đo thời gian trả về từ phương thức là đo thời gian xử lý stream.”** Sai: lời gọi có thể trả đối tượng publisher rất nhanh trước khi có subscriber. Phải ghi rõ thời điểm bắt đầu/kết thúc phép đo.

**“Cứ gọi request(Long.MAX_VALUE) để không bỏ lỡ sự kiện.”** Sai: thao tác đó xóa bỏ giới hạn demand của consumer và có thể làm producer vượt khả năng xử lý phía sau.

**“Bọc subscriber là luôn an toàn.”** Chỉ an toàn khi wrapper chuyển tiếp đúng các tín hiệu, nhu cầu, hủy, lỗi, thứ tự và ranh giới tài nguyên. Khi wrapper lọc, cache, retry, fan-out hoặc chuyển thread, nó đã trở thành một thành phần xử lý stream cần hợp đồng và kiểm thử riêng.

## 8. Kết luận

Trong AOP, ranh giới bất đồng bộ làm tách biệt **thời điểm gọi phương thức** và **vòng đời phát tín hiệu**. Với stream có backpressure, aspect quan sát cần đứng cạnh giao thức: theo dõi `subscribe`/`onNext`/terminal, ủy quyền `request(n)` và `cancel()` nguyên vẹn, không sinh demand hay queue vô hạn. Mọi biến đổi event, scheduler, buffering hoặc retry phải được thiết kế như một toán tử có kiểm soát nhu cầu. Như vậy mối quan tâm cắt ngang vẫn quan sát được hệ thống mà không phá vỡ khả năng tự điều tiết của luồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-boundaries-007">Hủy tác vụ, thời gian chờ và thực hiện dọn dẹp</a>

<details>
<summary>Xem chi tiết</summary>

# Hủy tác vụ, thời gian chờ và dọn dẹp tại ranh giới bất đồng bộ trong Aspect (AOP)

## 1. Vấn đề mà Aspect cần giải quyết

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), **hủy tác vụ**, **thời gian chờ (timeout)** và **dọn dẹp tài nguyên (cleanup)** là các mối quan tâm cắt ngang: nhiều thao tác nghiệp vụ cần cùng một chính sách nhưng không nên lặp lại mã quản lý thời gian, hủy bỏ và quan sát trạng thái ở từng phương thức.

Điểm khó nằm ở **ranh giới bất đồng bộ**. Một phương thức có thể trả về `Future` hoặc `CompletionStage` gần như ngay lập tức, trong khi công việc thực sự vẫn chạy ở luồng khác. Vì vậy, “phương thức đã trả về” không đồng nghĩa với “tác vụ đã kết thúc”. Một Aspect chỉ bao quanh lời gọi ban đầu có thể bỏ lỡ lỗi, kết quả và tài nguyên được giải phóng sau đó.

Mục tiêu của bài:

- Phân biệt yêu cầu hủy, trạng thái đã hủy, timeout chờ kết quả và việc công việc thực tế đã dừng.
- Xác định Aspect nên gắn chính sách vào **lời gọi**, **đối tượng kết quả bất đồng bộ** và **vòng đời tác vụ** như thế nào.
- Quản lý deadline, truyền tín hiệu hủy và dọn dẹp đúng chủ sở hữu; tránh đóng tài nguyên khi tác vụ còn dùng.
- Nhìn rõ giới hạn của các API Java như `Future` và `CompletableFuture`, kiểm soát race condition và kiểm thử được chính sách.

Bài dùng **AOP tổng quát**, minh họa bằng Java tiêu chuẩn với dynamic proxy/decorator để thể hiện lời khuyên quanh lời gọi (around advice). Các nguyên tắc tương tự áp dụng cho các cơ chế weaving khác nếu cơ chế đó có thể chặn đúng join point.

## 2. Mô hình vòng đời: lời gọi, kết quả, tác vụ

Xét một thao tác `submit()` trả về `Future<Result>`:

~~~text
Luồng gọi                 Aspect                   Worker
    |                       |                        |
    |--- gọi submit() ----->|--- gọi nghiệp vụ ------>|
    |                       |<-- trả Future ----------|
    |<-- nhận Future -------|                        | đang làm việc...
    |                       |                        |
    |--- get()/cancel() ------------------------------|
    |                       |          hoàn tất / lỗi / nhận yêu cầu hủy
    |                       |                        | finally/close
~~~

Có **ba thời điểm** cần tách:

1. **Kết thúc lời gọi đồng bộ**: `submit()` trả về một handle. Advice kiểu “sau khi trả về” tại join point này chỉ quan sát được handle.
2. **Kết thúc logic của handle**: `Future` báo thành công, lỗi hoặc bị hủy; một handle cũng có thể được hoàn tất giả bằng timeout.
3. **Kết thúc thực sự của worker**: không còn dùng tài nguyên; `finally` đã chạy. Đây mới là mốc an toàn để giải phóng những tài nguyên do worker sở hữu.

Các mốc 2 và 3 **không nhất thiết trùng nhau**. Đặc biệt, `cancel(true)` có thể đánh dấu `Future` là cancelled ngay khi worker còn đang xử lý ngắt; `CompletableFuture.orTimeout(...)` có thể hoàn tất đối tượng kết quả trong lúc phép tính nền vẫn tiếp diễn.

Một hợp đồng Aspect hữu ích nên trả lời được: ai sở hữu tác vụ, ai được hủy, deadline bắt đầu từ khi nào, đối tượng nào truyền trạng thái và thành phần nào chịu trách nhiệm cleanup.

## 3. Hủy tác vụ: tín hiệu hợp tác thay vì nút dừng tuyệt đối

### 3.1. Ý nghĩa của các API

| Cơ chế | Nó bảo đảm điều gì? | Điều nó không bảo đảm |
| --- | --- | --- |
| `Future.cancel(false)` | Cố chuyển handle sang trạng thái cancelled; nếu tác vụ chưa chạy, nó có thể bị ngăn không chạy | Không cưỡng bức dừng tác vụ đang chạy |
| `Future.cancel(true)` | Yêu cầu hủy và **có thể interrupt** luồng đang thực hiện với triển khai như `FutureTask` | Không bảo đảm mã nghiệp vụ dừng tức thì; interrupt phải được hợp tác xử lý |
| `Future.isCancelled()` | Trạng thái hủy của handle | Không chứng minh worker đã chạy xong `finally` |
| `Future.get()` sau hủy | Có thể ném `CancellationException` | Không cho biết chắc nguyên nhân là caller hủy hay deadline hết |
| `CompletableFuture.cancel(true)` | Hoàn tất `CompletableFuture` ở trạng thái cancelled | Tham số `mayInterruptIfRunning` **không** làm ngắt phép tính nền theo hợp đồng của `CompletableFuture` |
| `CompletionStage` | Mô tả chuỗi xử lý và kết quả bất đồng bộ | Bản thân interface này không có phương thức `cancel` |

**Interrupt là tín hiệu hợp tác.** Worker nên kiểm tra `Thread.currentThread().isInterrupted()` ở các bước tính toán dài, hoặc phản ứng với `InterruptedException` tại các thao tác chặn có hỗ trợ ngắt. Khi bắt `InterruptedException` nhưng không ném tiếp, thường phải khôi phục cờ bằng `Thread.currentThread().interrupt()` trước khi thoát. Không nuốt ngắt rồi âm thầm chạy tiếp.

Với yêu cầu từ xa hoặc I/O không hỗ trợ interrupt, cancellation có thể cần API riêng để hủy request, đóng kết nối có kiểm soát hoặc truyền cancellation token. Không đồng nhất việc đã ngắt thread với việc server từ xa đã ngừng xử lý.

### 3.2. Quyền sở hữu và lan truyền yêu cầu hủy

Một Aspect chỉ nên hủy tác vụ nền nếu **hợp đồng cho phép nó sở hữu quyền hủy**. Nếu nhiều caller dùng chung một phép tính (single-flight, cache dùng chung, subscription nhiều người), việc một caller timeout không tự động đồng nghĩa được dừng tác vụ cho tất cả.

Phân biệt hai chính sách:

- **Caller-scoped timeout/cancel**: một người gọi ngừng chờ, tách subscriber hoặc trả lỗi riêng; phép tính chung vẫn chạy.
- **Operation-scoped timeout/cancel**: deadline thuộc chính tác vụ; nếu vượt giới hạn, thành phần điều phối có thể yêu cầu hủy và giải phóng tài nguyên khi worker dừng.

Khi chuỗi bất đồng bộ có các tác vụ con, phải định nghĩa hướng lan truyền: hủy cha có hủy con không, một nhánh lỗi có hủy các nhánh còn lại không, hủy một nhánh chia sẻ có ảnh hưởng nhánh khác không. Không giả định mọi thư viện tự truyền cancellation theo cùng cách.

### 3.3. Chuyển trạng thái và race condition

Các sự kiện “hoàn thành”, “hết giờ” và “caller hủy” có thể xảy ra gần như đồng thời. Đặt chính sách là **ai thắng trước thì công bố trạng thái tương ứng**, dùng thao tác nguyên tử hoặc cơ chế hoàn tất được thư viện cung cấp. Aspect không được báo vừa thành công vừa timeout cho cùng một kết quả logic.

Các trường hợp cần tính đến:

- Tác vụ xong trước khi timer bắn: timer kiểm tra trạng thái và không hủy.
- Timer bắn trước hoàn tất: handle có thể cancelled nhưng worker phải đi hết đường thoát và cleanup.
- Hủy trước khi worker bắt đầu: tài nguyên chỉ được cấp phát **bên trong** worker sẽ không tồn tại, do đó không có cleanup ở đường này.
- Hủy lặp lại: hành vi nên idempotent; một tài nguyên không được đóng hai lần do hai luồng xử lý timeout và cancel.
- Kết quả xuất hiện muộn: nếu caller đã timeout, không được ghi đè câu trả lời timeout hoặc tái sử dụng tài nguyên đã đóng; xử lý/loại bỏ kết quả muộn có chủ đích.

Nếu cần phân biệt chính xác lý do kết thúc (deadline, caller, shutdown), lưu lý do riêng bằng trạng thái đồng bộ/atomic; chỉ nhìn `CancellationException` thường không đủ.

## 4. Timeout: phân biệt chờ, hoàn tất logic và dừng thực tế

### 4.1. Timeout của người chờ

`future.get(200, TimeUnit.MILLISECONDS)` giới hạn **thời gian chờ của lời gọi `get`**. Khi ném `TimeoutException`, tác vụ nền **vẫn có thể chạy**. Hệ thống chỉ yêu cầu hủy nếu chính sách chủ động gọi `future.cancel(true)` hoặc cơ chế dừng phù hợp khác.

`CompletableFuture.orTimeout(duration, unit)` đặt thời điểm hoàn tất **exceptionally bằng `TimeoutException` cho chính `CompletableFuture` đó** nếu nó chưa hoàn tất. Nó **không tự ngắt** worker đang chạy. Tương tự, `completeOnTimeout(fallback, ...)` hoàn tất chính đối tượng đó bằng giá trị dự phòng và cũng không dừng công việc nền. Không dùng các API này như bằng chứng tài nguyên đã an toàn để đóng.

### 4.2. Timeout của thao tác và deadline tổng

Timeout tương đối cho từng bước có thể vượt tổng ngân sách. Ví dụ, một yêu cầu có ngân sách 600 ms nhưng ba bước nối tiếp đều được cấp 500 ms có thể kéo dài nhiều hơn 600 ms. Aspect điều phối nên dùng **deadline tổng** rồi tính ngân sách còn lại tại từng ranh giới.

Với đo thời gian trôi qua trong một tiến trình, dùng đồng hồ đơn điệu:

~~~java
long deadlineNanos = System.nanoTime()
        + TimeUnit.MILLISECONDS.toNanos(600);

// Trước mỗi thao tác chặn:
long remainingNanos = deadlineNanos - System.nanoTime();
if (remainingNanos <= 0) {
    throw new TimeoutException("Đã hết ngân sách thời gian");
}
String result = future.get(remainingNanos, TimeUnit.NANOSECONDS);
~~~

Đoạn này minh họa một phần phương thức đang có biến `future` kiểu `Future<String>` và được khai báo `throws InterruptedException, ExecutionException, TimeoutException`. `System.nanoTime()` dùng để đo khoảng thời gian, **không phải** thời gian lịch để ghi log hay truyền trực tiếp qua máy khác. Nếu truyền deadline giữa các dịch vụ, cần thỏa thuận cách biểu diễn và xử lý lệch đồng hồ; thường truyền ngân sách còn lại đã được kiểm soát.

Chính sách timeout cần ghi rõ nó tính từ **lúc caller bắt đầu gọi**, **lúc nhiệm vụ được xếp hàng** hay **lúc worker bắt đầu thực hiện**; ba mốc này cho kết quả rất khác nhau khi executor quá tải. Cũng cần phân biệt thời gian chờ trong queue, thời gian thực thi, timeout I/O và thời gian chờ lấy kết quả.

### 4.3. Timer cũng là tài nguyên

Một Aspect thường dùng `ScheduledExecutorService` để phát tín hiệu hủy khi quá hạn. Timer phải được hủy khi có thể xác nhận công việc hoàn tất; một scheduler nên được dùng chung và có vòng đời rõ ràng, tránh tạo một thread/timer service cho từng request. Với `Future` thuần không có callback hoàn tất, có thể chấp nhận một watchdog ngắn sống đến deadline, hoặc bổ sung lớp giám sát hoàn tất nếu cần thu hồi timer sớm.

Việc timeout không làm biến mất tác vụ nền: khi hủy không có hiệu lực, cần cơ chế giới hạn concurrency, backpressure và quan sát các tác vụ quá hạn đang còn chạy. Nếu không, hệ thống có thể trả timeout rất nhanh nhưng vẫn cạn luồng, kết nối hay bộ nhớ.

## 5. Dọn dẹp tài nguyên đúng vòng đời

### 5.1. Ai tạo thì chịu trách nhiệm đóng

Quy tắc thực dụng:

- **Worker sở hữu** file, socket, cursor, buffer hay resource được tạo để thực thi công việc: đóng bằng `try-with-resources` hoặc `finally` **ngay trong worker**, sau lần sử dụng cuối cùng.
- **Aspect sở hữu** timer, context snapshot, phép đo thời gian, đăng ký callback hoặc permit giới hạn đồng thời: thu hồi những tài nguyên này ở thời điểm phù hợp với vòng đời mà nó quản lý.
- **Caller sở hữu** quyền chờ kết quả: khi caller hết giờ, caller có thể dừng chờ mà không được tự ý đóng resource worker vẫn đang dùng.
- **Tài nguyên dùng chung**: chủ sở hữu quản lý reference count/lease hoặc vòng đời dịch vụ; mỗi invocation không được tùy tiện đóng.

Một anti-pattern nguy hiểm:

~~~java
// SAI nếu tác vụ nền vẫn dùng resource:
CompletableFuture<String> answer = computeUsing(resource)
        .orTimeout(200, TimeUnit.MILLISECONDS);
answer.whenComplete((value, error) -> resource.close());
~~~

Ở đây `orTimeout` có thể đánh dấu `answer` kết thúc trước khi computation ngừng, nên callback `close()` chạy quá sớm. Đây chỉ là **đoạn minh họa lỗi**, không phải một chương trình Java độc lập; cách sửa là để tác vụ thực sở hữu và đóng `resource` trong `finally`, hoặc có tín hiệu **physical completion** tách biệt khỏi kết quả đã timeout.

### 5.2. Callback sau khi hoàn tất: điểm mạnh và giới hạn

- `whenComplete((value, error) -> ...)` phù hợp để đo metric/log cho cả đường thành công lẫn exceptional; không nên mặc định nó được gọi trên một thread cụ thể.
- `whenCompleteAsync(..., executor)` đưa callback sang executor đã chỉ định; cần quản lý executor đó và cân nhắc callback bị trì hoãn hoặc từ chối khi shutdown.
- `thenApply(...)` chỉ chạy trên đường thành công, **không** đủ làm nơi cleanup cho mọi kết thúc.
- Callback có thể ném lỗi. Không để lỗi cleanup/telemetry che mất nguyên nhân nghiệp vụ; xử lý lỗi dọn dẹp riêng khi thích hợp. `try-with-resources` có cơ chế suppressed exceptions giúp giữ dấu vết khi cả thân xử lý và `close()` đều lỗi.

Quan trọng: callback trên một `CompletionStage` chỉ có ý nghĩa đối với **vòng đời logic của stage đó**. Nếu stage được hoàn tất sớm do timeout, callback không chứng minh worker đã thoát.

### 5.3. Nguyên tắc cleanup khi hủy

Trong worker, đặt vùng sử dụng tài nguyên trong `try/finally`; khi nhận interrupt, đi theo đường thoát hợp lệ và để `finally` chạy. Tránh tự tay gọi cleanup từ **cả** nhánh timeout của Aspect **và** `finally` của worker nếu chúng cùng sở hữu một resource. Nếu buộc phải có nhiều đường giải phóng, bảo vệ bằng trạng thái đồng bộ và một hợp đồng `close()` idempotent được thiết kế rõ ràng.

Sau khi một handle chuyển cancelled, đừng giả định mọi side effect đã rollback. Các tác vụ đã gửi email, ghi cơ sở dữ liệu hoặc gọi API từ xa có thể hoàn tất một phần. Cleanup bộ nhớ/connection và **bù trừ nghiệp vụ** là hai việc khác nhau; Aspect không thể tự động đảo ngược mọi side effect.

## 6. Aspect gắn chính sách ở đâu?

Một lời khuyên quanh lời gọi (around advice) có thể:

1. Đọc cấu hình/hợp đồng: deadline, loại tác vụ được phép hủy, chiến lược báo lỗi và chủ sở hữu resource.
2. Gọi phương thức gốc **đúng một lần**, ghi thời điểm bắt đầu và nhận `Future`/`CompletionStage`.
3. Gắn timer, callback quan sát, propagation token hoặc trả một wrapper điều phối; tránh chặn luồng gọi trừ khi API thực sự yêu cầu đồng bộ.
4. Khi hoàn tất/timeout/cancel, báo đúng trạng thái và thu hồi **resource do Aspect sở hữu**.
5. Giữ quyền quản lý cleanup của worker trong worker; xử lý các race giữa kết thúc, timeout và caller cancellation.

Đối với advice chỉ chạy quanh **phương thức trả về handle**, “after returning” thấy handle chứ không nhất thiết thấy giá trị cuối cùng; “after throwing” tại lời gọi không bắt được lỗi phát sinh về sau trong worker. Muốn quan sát kết quả cuối, cần gắn callback hoặc bọc abstraction bất đồng bộ phù hợp.

Khi dùng AOP nâng cao, xác định rõ **join point được weave** (lời gọi dịch vụ, thao tác submit, xử lý callback hay một lớp executor), tránh chặn trùng nhiều tầng gây đặt hai timer hoặc đếm cleanup hai lần. Dù dùng proxy hay weaving, các lời gọi nội bộ không đi qua interception boundary tương ứng có thể không bị advice bắt; đặc tính chính xác phụ thuộc cơ chế AOP.

## 7. Ví dụ chạy được: Aspect áp deadline cho Future bằng JDK dynamic proxy

Ví dụ dưới dùng Java 17+, chỉ thư viện chuẩn. `DeadlineAspect` chặn phương thức trên interface, nhận `Future` rồi đặt watchdog. Khi quá 250 ms, Aspect **yêu cầu** hủy bằng `cancel(true)`. Worker đang ngủ phản ứng với interrupt và luôn thực hiện `finally`.

Tạo file `DeadlineAspectDemo.java`:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeadlineAspectDemo {
    interface JobService {
        Future<String> submit();
    }

    static final class JobServiceImpl implements JobService {
        private final ExecutorService workers;

        JobServiceImpl(ExecutorService workers) {
            this.workers = workers;
        }

        @Override
        public Future<String> submit() {
            return workers.submit(() -> {
                System.out.println("worker: bat dau");
                try {
                    Thread.sleep(1_200);
                    return "OK";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } finally {
                    System.out.println("worker: don dep");
                }
            });
        }
    }

    static final class DeadlineAspect implements InvocationHandler {
        private final JobService target;
        private final ScheduledExecutorService scheduler;

        DeadlineAspect(JobService target, ScheduledExecutorService scheduler) {
            this.target = target;
            this.scheduler = scheduler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            Object returned;
            try {
                returned = method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }

            if (returned instanceof Future<?> future) {
                scheduler.schedule(() -> {
                    if (!future.isDone() && future.cancel(true)) {
                        System.out.println("aspect: yeu cau huy do qua han");
                    }
                }, 250, TimeUnit.MILLISECONDS);
            }
            return returned;
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        try {
            JobService service = (JobService) Proxy.newProxyInstance(
                    JobService.class.getClassLoader(),
                    new Class<?>[] { JobService.class },
                    new DeadlineAspect(new JobServiceImpl(workers), scheduler));

            Future<String> result = service.submit();
            try {
                System.out.println("caller: " + result.get());
            } catch (CancellationException e) {
                System.out.println("caller: future da bi huy");
            }
        } finally {
            workers.shutdown();
            scheduler.shutdown();
            if (!workers.awaitTermination(2, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac DeadlineAspectDemo.java
java DeadlineAspectDemo
~~~

Khi worker kịp bắt đầu và bị ngắt sau deadline, sẽ xuất hiện các thông báo:

~~~text
worker: bat dau
aspect: yeu cau huy do qua han
caller: future da bi huy
worker: don dep
~~~

**Thứ tự hai dòng giữa/cuối có thể khác nhau**: caller có thể nhận `CancellationException` khi worker còn đang vào `finally`, và scheduler có thể in log sau khi `Future` chuyển trạng thái. Nếu worker chưa kịp bắt đầu trước lúc bị hủy, dòng “worker: bat dau” và cleanup trong worker có thể không xuất hiện. Đó là tính chất của concurrency, không phải lỗi cú pháp.

Các giới hạn cố ý của ví dụ:

- JDK proxy chặn được lời gọi qua **interface** `JobService`; nó không tự chặn mọi phương thức hay mọi lời gọi nội bộ.
- Timer đặt deadline từ khi Aspect nhận `Future`. Mốc này có thể khác thời điểm công việc bắt đầu trong queue.
- `cancel(true)` hiệu quả với `Thread.sleep` vì thao tác này phản ứng với interrupt. Worker không hợp tác thì có thể tiếp tục chạy.
- Ở ví dụ tối giản, watchdog còn được xếp lịch đến 250 ms kể cả khi tác vụ xong sớm. Bản production nên quản lý việc hủy timer theo completion thực tế, theo dõi lý do hủy và xử lý lifecycle của executor.
- `result.get()` nhận `CancellationException`, **không phải `TimeoutException`**: đây là chính sách timeout được biểu diễn bằng hành động hủy handle. Nếu API yêu cầu phân biệt “quá hạn” với “người dùng hủy”, cần một lớp trạng thái/kết quả riêng.

## 8. Ví dụ chạy được: CompletableFuture và cancellation token hợp tác

`CompletableFuture.cancel(true)` không ngắt computation nền như ví dụ `FutureTask`. Một lựa chọn khi thiết kế worker thuộc quyền kiểm soát của mình là **cancellation token** dùng chung. Ví dụ dưới giới hạn thời gian chờ phía caller rồi bật token; worker kiểm tra token ở mỗi bước và tự đóng resource sau khi thực sự thoát.

Tạo file `CooperativeCleanupDemo.java`:

~~~java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CooperativeCleanupDemo {
    static final class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("resource: da dong");
        }
    }

    static CompletableFuture<String> start(
            ExecutorService executor, AtomicBoolean cancelled) {
        return CompletableFuture.supplyAsync(() -> {
            try (Resource resource = new Resource()) {
                for (int step = 0; step < 5; step++) {
                    if (cancelled.get()) {
                        throw new CancellationException("Da yeu cau huy");
                    }
                    Thread.sleep(100);
                }
                return "OK";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }, executor).whenComplete((value, error) -> {
            System.out.println("observer: worker da ket thuc");
        });
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        try {
            CompletableFuture<String> result = start(executor, cancelled);
            try {
                System.out.println("caller: " + result.get(180, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                cancelled.set(true);
                System.out.println("caller: het gio, da gui tin hieu huy");
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac CooperativeCleanupDemo.java
java CooperativeCleanupDemo
~~~

Thông thường chương trình in ba thông báo sau (thứ tự dòng phía observer/resource với dòng caller có thể xen kẽ do scheduler luồng):

~~~text
caller: het gio, da gui tin hieu huy
resource: da dong
observer: worker da ket thuc
~~~

Ở đây `get(180, ...)` chỉ giới hạn **chờ**; `AtomicBoolean` mới là tín hiệu worker hợp tác kiểm tra. `Resource` được tạo và đóng bên trong worker; caller không đóng nó vào đúng mốc timeout. Chương trình giữ executor sống đủ lâu để worker đi hết đường dọn dẹp.

Không suy luận rằng token có thể làm dừng ngay một thao tác không kiểm tra token: muốn hủy nhanh các lần I/O dài, phải thiết kế timeout/I/O cancellation riêng. Ở ví dụ này, worker có thể mất tối đa xấp xỉ một bước ngủ nữa trước khi nhận tín hiệu, tùy lịch chạy.

## 9. Xử lý lỗi tại ranh giới bất đồng bộ

Một Aspect cần phân biệt các nhóm lỗi và truyền chúng đúng hợp đồng:

| Tình huống | Điều caller thường thấy | Hướng xử lý |
| --- | --- | --- |
| Lỗi ném ngay khi gọi phương thức | Exception tại join point đồng bộ | Advice đồng bộ xử lý, log rồi truyền tiếp theo hợp đồng |
| Worker lỗi khi thực thi `Callable` | `ExecutionException` khi `Future.get()`; nguyên nhân nằm trong `getCause()` | Quan sát nguyên nhân; đừng quy mọi exception thành timeout |
| `CompletableFuture` hoàn tất lỗi | `CompletionException` qua `join()`, hoặc `ExecutionException` qua `get()` | Giữ chuỗi nguyên nhân và ngữ cảnh bất đồng bộ |
| Caller `get(timeout)` hết giờ | `TimeoutException` tại thao tác chờ | Xác định chỉ ngừng chờ hay yêu cầu hủy operation |
| `Future` được hủy | `CancellationException` khi lấy kết quả | Phân biệt lý do hủy bằng metadata/trạng thái riêng nếu cần |
| Worker nhận interrupt | Có thể là `InterruptedException` hoặc đường thoát tùy code | Khôi phục cờ interrupt khi thích hợp, chạy `finally` |
| Cleanup lỗi | Exception khi đóng resource/callback | Giữ nguyên nhân chính, ghi nhận lỗi cleanup; tránh báo success giả |

Nếu Aspect chuyển lỗi thành fallback, cần nêu rõ fallback **chỉ giải quyết kết quả logic**, chưa chắc đã dừng tác vụ vật lý. Với retry, mỗi lần chạy phải dùng ngân sách deadline còn lại, và không retry trên cancellation hoặc timeout đã hết budget nếu không có chính sách cho phép. Không retry mù các thao tác có side effect không idempotent.

## 10. Các tình huống thiết kế nâng cao

### 10.1. Context qua ranh giới luồng

Thông tin truy vết, correlation ID, định danh người gọi hoặc các giá trị `ThreadLocal` không tự chuyển sang executor khác một cách đáng tin. Nếu Aspect chụp context trước `submit` và phục hồi trong worker, phải:

- Chụp **giá trị cần thiết**, tránh giữ tham chiếu tới dữ liệu mutable không an toàn.
- Thiết lập context ngay khi worker bắt đầu, rồi khôi phục/xóa trong `finally` để không rò sang tác vụ kế tiếp dùng lại thread.
- Truyền ngữ cảnh phù hợp vào callback có thể chạy ở thread khác.
- Không dùng thời điểm caller hết giờ để xóa context mà worker còn cần; phân biệt context caller và context worker.

### 10.2. Bulkhead, concurrency và tài nguyên dùng chung

Nếu Aspect lấy semaphore permit trước khi submit, phải xác định permit được trả khi nào. **Trả permit ngay lúc Future bị đánh dấu cancelled có thể sai** nếu worker vẫn đang chạy và dùng resource cần giới hạn. Với limit số worker thực sự hoạt động, chỉ trả permit khi worker vật lý kết thúc trong `finally`. Với limit số lời gọi caller đang chờ, lifetime có thể khác. Hai loại giới hạn không nên dùng chung một bộ đếm mơ hồ.

Đối với queue đã nhận tác vụ nhưng chưa chạy, hủy trước khi bắt đầu đòi hỏi bộ điều phối thu hồi slot/permit của tác vụ chờ; dựa hoàn toàn vào `finally` của worker sẽ không xử lý đường “chưa từng được thực thi”.

### 10.3. Nested async, fan-out và partial success

Một thao tác có thể gọi nhiều nhánh đồng thời. Khi một nhánh timeout, chính sách có thể chờ các nhánh khác, hủy toàn bộ nhóm, trả kết quả một phần hoặc đợi cleanup toàn bộ trước khi công bố “operation drained”. Aspect cần chọn rõ semantics và deadline tổng; đừng mặc định `allOf`, chuỗi `thenApply` hay việc hủy một `CompletableFuture` tự hủy tất cả phép tính phía dưới.

### 10.4. Kết quả muộn và callback chạy sau hủy

Nếu observer nhận hai tín hiệu theo các đường cạnh tranh (timeout và completion), hãy dùng trạng thái nguyên tử để công bố **một terminal event logic**. Có thể vẫn cần một sự kiện riêng cho “worker thực sự đã thoát” để quản lý resource và metric. Đừng đồng nhất metric “request timeout” với metric “task stopped”.

Nếu một callback vừa ghi kết quả vừa cleanup, bảo đảm xử lý đúng khi timeout đến giữa hai thao tác; xử lý cleanup riêng khỏi publish result và dùng primitive đồng bộ thích hợp cho tài nguyên chia sẻ.

## 11. Checklist thiết kế và kiểm thử

Trước khi áp dụng Aspect timeout/cancel/cleanup cho một API bất đồng bộ, xác nhận:

1. **Đúng phạm vi join point**: chặn lời gọi nào, lời gọi nào bypass proxy/weaving, có intercept trùng không?
2. **Đúng vòng đời**: ghi riêng invocation return, logical completion và physical completion; xác định owner cho từng resource.
3. **Deadline nhất quán**: tính từ mốc nào, ngân sách cha truyền cho con ra sao, đo elapsed time bằng đồng hồ đơn điệu.
4. **Hủy có hợp đồng**: ai được hủy, hủy handle hay worker, interrupt/token/transport có thực sự được hỗ trợ không, các subscriber có chia sẻ công việc không?
5. **Cleanup an toàn**: chạy khi resource hết được sử dụng, xử lý cả thành công/lỗi/hủy, bảo toàn lỗi gốc và tránh double-close.
6. **Quan sát đầy đủ**: phân biệt deadline, cancellation chủ động, worker failure và cleanup failure; cảnh báo worker còn sống sau timeout.
7. **Kiểm thử race**: dùng `CountDownLatch`, `Phaser` hoặc executor kiểm soát được lịch thay vì chỉ `Thread.sleep`; tạo các trường hợp hoàn tất đúng lúc timeout, hủy trước khi chạy, hủy đang chạy, hủy lặp, lỗi tại cleanup, worker phớt lờ interrupt và shutdown đồng thời.
8. **Kiểm thử bảo toàn tài nguyên**: xác minh mỗi resource được thu hồi đúng một lần, không sử dụng sau khi đóng, timer/permit được trả đúng thời điểm và không còn tác vụ mồ côi sau khi caller kết thúc.

Khi một timeout xảy ra, câu hỏi chính không chỉ là “caller đã nhận lỗi chưa?”, mà còn là “**tác vụ vật lý còn đang chạy không, ai giữ tài nguyên, và ai sẽ đưa nó về trạng thái an toàn?**”. Đó là trách nhiệm cốt lõi của một chính sách AOP được thiết kế đúng tại ranh giới bất đồng bộ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-boundaries-008">Xác định rõ điểm kết thúc hành vi được advice bao quanh</a>

<details>
<summary>Xem chi tiết</summary>

# Xác định rõ điểm kết thúc hành vi được advice bao quanh

## 1. Vấn đề cốt lõi: “trả về” và “hoàn tất” là hai sự kiện khác nhau

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), **advice** là hành vi cắt ngang được áp dụng tại một **join point** (điểm thực thi có thể can thiệp), thường được chọn bằng **pointcut**. Với một phương thức đồng bộ, việc phương thức trả về hoặc ném ngoại lệ thường cũng là lúc công việc mà người gọi chờ đã kết thúc. Vì vậy, advice đo thời gian, ghi log hoặc dọn dẹp tài nguyên quanh lời gọi có vẻ tự nhiên.

Với phương thức bất đồng bộ, giả định ấy không còn đúng. Phương thức có thể **trả về ngay một đối tượng đại diện cho kết quả tương lai**, trong khi công việc thực tế vẫn đang chạy hoặc thậm chí chưa bắt đầu. Advice cần xác định mình quan sát sự kiện nào, thay vì gọi chung tất cả là “kết thúc”.

Có ít nhất ba ranh giới cần phân biệt:

1. **Kết thúc lượt thực thi tại join point:** phương thức/đoạn được intercept đã trả về một giá trị hoặc đã ném ngoại lệ đồng bộ. Đây là ranh giới mặc định của advice bao quanh một lời gọi hoặc thực thi phương thức.
2. **Kết thúc thao tác bất đồng bộ theo hợp đồng API:** đối tượng kết quả phát tín hiệu hoàn tất thành công, thất bại hoặc hủy; đối với luồng nhiều phần tử là sự kiện kết thúc của từng lượt tiêu thụ/đăng ký.
3. **Kết thúc toàn bộ tác động phụ và vòng đời tài nguyên:** tác vụ phụ, ghi log nền, gửi thông báo, đóng kết nối, giải phóng tài nguyên… có thể tiếp diễn sau khi kết quả chính hoàn tất. Không được tự đồng nhất ranh giới này với hai ranh giới trên.

**Nguyên tắc:** advice mặc định bao quanh *thực thi join point*; chỉ khi có thiết kế quan sát kết quả bất đồng bộ riêng mới có thể nói advice theo dõi *vòng đời logic của tác vụ*.

## 2. Nhìn trên dòng thời gian

Ví dụ một phương thức bắt đầu công việc nền rồi trả về một đối tượng kiểu CompletionStage:

~~~text
Luồng gọi:
t0  -> advice trước proceed()
t1  -> bắt đầu gọi phương thức đích
t2  -> phương thức đích trả về CompletionStage (có thể chưa hoàn tất)
t3  -> proceed() trả về; advice sau-return/around chạy xong
t4  -> người gọi nhận CompletionStage
          ...
t5  -> công việc bất đồng bộ hoàn tất hoặc thất bại
t6  -> callback theo dõi completion được kích hoạt
~~~

Khoảng **t1–t3** là thời gian thực thi phương thức bị bao quanh. Khoảng **t1–t5** gần hơn với thời gian của *tác vụ logic*, nếu t1 thật sự là lúc tác vụ bắt đầu. Hai khoảng này đo hai thứ khác nhau.

Dòng thời gian trên minh họa trường hợp thường gặp, không phải thứ tự bắt buộc. Một tác vụ rất nhanh có thể hoàn tất **trước khi** phương thức trả về. Khi đó đối tượng được trả về đã hoàn tất sẵn; tuy vậy, việc trả về và việc completion vẫn là hai sự kiện có ý nghĩa độc lập.

### Điểm then chốt về proceed()

Trong around advice, lời gọi **proceed()** chuyển quyền điều khiển sang hành vi được bao quanh và sau đó trả lại điều khiển cho advice theo ngữ nghĩa của cơ chế AOP đang dùng. Nếu hành vi ấy chỉ *khởi tạo* một tác vụ rồi trả về handle, proceed() **không tự chờ** tác vụ đó xong.

Tương tự, advice loại **after returning** thường nhận *giá trị trả về* (ví dụ CompletionStage chưa hoàn tất), còn **after throwing** bắt được ngoại lệ được ném *trong lần thực thi join point*. Chúng không tự động chuyển thành callback nhận kết quả cuối cùng của một tác vụ bất đồng bộ. Advice kiểu **after/finally** cũng kết thúc theo lượt thực thi join point, kể cả khi có công việc nền đang tiếp diễn.

## 3. Phải xác định “kết thúc” theo hợp đồng trả về

| Hình thức API | Khi nào lần gọi phương thức kết thúc? | Khi nào công việc logic có thể coi là kết thúc? | Điều advice cần làm nếu muốn theo dõi đến cùng |
| --- | --- | --- | --- |
| Trả về giá trị đồng bộ | Khi trả về/throw | Thông thường tại cùng ranh giới | Advice thông thường là đủ nếu không có tác vụ phụ |
| CompletionStage hoặc CompletableFuture | Khi trả về đối tượng stage/future | Khi stage đại diện kết quả đã hoàn tất thành công hoặc ngoại lệ | Gắn hành động quan sát completion, xử lý cả lỗi |
| Future cổ điển | Khi trả về Future | Khi Future hoàn tất/hủy theo hợp đồng của nó | Cần cơ chế thông báo/giám sát riêng; Future thông thường không có callback completion |
| API callback | Khi phương thức đăng ký/lên lịch xong và trả về | Khi callback hợp đồng báo kết quả cuối | Bao quanh callback hoặc điểm phát kết quả, không chỉ hàm đăng ký |
| Java Flow.Publisher | Khi trả về Publisher | Theo từng subscription: kết thúc với onComplete, onError hoặc bị cancel; phải phân biệt cancel với terminal signal | Theo dõi vòng đời mỗi subscription và yêu cầu hủy |
| Gửi công việc không trả về handle | Khi enqueue/start xong hoặc khi throw đồng bộ | Không xác định đáng tin từ lời gọi ban đầu | Thiết kế API có handle/tín hiệu hoàn tất hoặc instrument ở điểm chạy thật |

Đối với API phát nhiều kết quả, một lần **trả về Publisher không đồng nghĩa đã chạy**: publisher có thể bắt đầu tạo dữ liệu khi có subscriber (cold), hoặc đã phát dữ liệu trước đó (hot). Một publisher có thể có nhiều subscription với các thời điểm kết thúc khác nhau. Đừng ghi một bản ghi “tác vụ hoàn tất” duy nhất tại lần trả về publisher.

Không nên suy ra vòng đời logic chỉ từ kiểu dữ liệu. Ví dụ, stage A có thể báo “đã gửi yêu cầu đến hàng đợi”, còn stage B báo “nghiệp vụ đã được xử lý”; A và B có tiêu chí completion khác nhau. **Hợp đồng của phương thức** mới xác định stage nào là kết quả cần quan sát.

## 4. Ví dụ Java 17+: chứng minh hai ranh giới là độc lập

Ví dụ sau là một wrapper viết bằng Java thuần **mô phỏng ngữ nghĩa quan sát của around advice**, giúp chạy thử mà không cần trình dệt AOP. Nó cố tình trả về future chưa hoàn tất để thứ tự in ra rõ ràng.

Tạo tệp **AsyncBoundaryDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class AsyncBoundaryDemo {
    static CompletableFuture<String> around(
            Supplier<CompletableFuture<String>> invocation) {
        System.out.println("advice: before");

        // Tương đương gọi hành vi đích/proceed().
        CompletableFuture<String> future = invocation.get();

        // Dòng này chạy ngay sau khi phương thức đích trả về future.
        System.out.println("advice: after return, done=" + future.isDone());

        // Theo dõi lần hoàn tất của future, không chặn người gọi.
        future.whenComplete((value, failure) -> {
            try {
                if (failure == null) {
                    System.out.println("advice: async success=" + value);
                } else {
                    System.out.println("advice: async failure");
                }
            } catch (RuntimeException observerFailure) {
                // Lỗi ở telemetry/log không được làm đổi kết quả nghiệp vụ.
            }
        });

        // Giữ nguyên future của nghiệp vụ.
        return future;
    }

    public static void main(String[] args) {
        CompletableFuture<String> pending = new CompletableFuture<>();

        CompletableFuture<String> returned = around(() -> {
            System.out.println("target: returning a future");
            return pending;
        });

        System.out.println("caller: received future");
        pending.complete("OK");
        System.out.println("caller: value=" + returned.join());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AsyncBoundaryDemo.java
java AsyncBoundaryDemo
~~~

Kết quả:

~~~text
advice: before
target: returning a future
advice: after return, done=false
caller: received future
advice: async success=OK
caller: value=OK
~~~

Ở đây **after return** xuất hiện khi future còn pending. Sự kiện **async success** xảy ra khi hoàn tất future. Việc gọi complete() trong main khiến ví dụ chạy ổn định, không phụ thuộc vào lịch thread. Trong ứng dụng thật, completion thường xảy ra trên thread khác.

Lưu ý: whenComplete tạo ra **một stage phụ thuộc mới**; ví dụ cố ý chỉ dùng nó làm observer và **trả về future gốc**. Callback whenComplete không có hậu tố Async có thể chạy ngay trên thread hoàn tất future hoặc trong những ngữ cảnh thực thi được CompletableFuture cho phép; không được mặc định rằng nó chạy trên một thread riêng.

## 5. Ba kiểu thất bại phải được báo cáo khác nhau

### 5.1. Throw đồng bộ trước khi trả về handle

~~~java
CompletableFuture<String> load() {
    throw new IllegalArgumentException("invalid request");
}
~~~

Không có future nào được trả về. Advice bao quanh lần thực thi có thể thấy ngoại lệ ở nhánh throw đồng bộ; không thể gắn completion callback lên giá trị trả về vì giá trị đó không tồn tại.

### 5.2. Trả về handle rồi hoàn tất lỗi

~~~java
CompletableFuture<String> load() {
    CompletableFuture<String> result = new CompletableFuture<>();
    result.completeExceptionally(
            new IllegalStateException("remote service failed"));
    return result;
}
~~~

Lần gọi phương thức **trả về bình thường** dù kết quả đã đánh dấu thất bại. After-returning advice thấy một future; after-throwing advice của lần gọi này không thấy IllegalStateException như một throw đồng bộ. Observer completion phải kiểm tra nhánh failure. Lỗi có thể được bọc, ví dụ trong CompletionException, tùy cách hoàn tất và cách người gọi lấy kết quả.

### 5.3. Hủy hoặc timeout

Hủy thường được biểu diễn như một trạng thái hoàn tất đặc biệt. Với CompletableFuture, cancel(...) làm future hoàn tất ngoại lệ bằng CancellationException; nó **không đảm bảo dừng công việc đang chạy**. Timeout do tầng nào đặt ra cũng cần được xác định: đó có thể là việc làm future hoàn tất lỗi, hủy việc chờ, hoặc chỉ có một bên quan sát ngừng quan tâm trong khi tác vụ gốc tiếp tục.

Nên phân loại outcome tối thiểu thành **success**, **failure**, **cancel** và **timeout** khi hợp đồng cung cấp đủ tín hiệu để phân biệt. Tránh ghi “success” chỉ vì phương thức đã trả về một future.

## 6. Hai cách thiết kế around advice và đánh đổi

### A. Chỉ quan sát completion; trả về đúng handle ban đầu

Hành vi khái quát:

~~~text
on join-point entry:
    ghi thời điểm bắt đầu
    gọi proceed()
    nếu proceed ném lỗi đồng bộ -> ghi lỗi -> ném lại
    nếu nhận handle:
        gắn observer khi handle completion
        trả về chính handle đó cho người gọi
~~~

Ưu điểm: thường giữ được danh tính đối tượng, cách các bên dùng handle và đường truyền cancellation của đối tượng gốc. Phù hợp cho telemetry/log thuần quan sát.

Giới hạn: việc gắn observer cũng là một tác động có thể nhìn thấy về tài nguyên/lịch chạy; observer chậm hoặc ném lỗi có thể ảnh hưởng hiệu năng hoặc một stage phụ thuộc do nó tạo ra. Mọi thao tác quan sát phải nhanh, an toàn ngoại lệ và không được vô tình giữ tham chiếu lớn quá lâu. Nếu API không cho phép callback, không thể tự tuyên bố đã theo dõi completion.

### B. Trả về một stage đã được biến đổi hoặc bọc lại

Có thể dùng composition để thêm bước cần hoàn tất **trước khi** người gọi nhận trạng thái kết quả cuối cùng, chẳng hạn một bước xử lý kết quả mang ý nghĩa nghiệp vụ.

~~~text
stage gốc -> xử lý bổ sung -> stage mới được trả về
~~~

Khi trả về stage mới, điều này **thay đổi hợp đồng quan sát được**: thời điểm completion, cách lỗi lan truyền, danh tính handle, hành vi hủy, thậm chí kiểu cụ thể của đối tượng trả về có thể khác. Không được bọc lại chỉ để đo số liệu mà chưa kiểm tra hợp đồng. Một phương thức khai báo trả về lớp con cụ thể của Future hoặc CompletableFuture có thể không chấp nhận stage biến đổi thuộc kiểu khác.

**Quy tắc chọn:** nếu chỉ cần số liệu về hoàn tất, ưu tiên quan sát có kiểm soát; nếu cần biến đổi kết quả hoặc ràng buộc bước xử lý bổ sung, thiết kế và kiểm thử hợp đồng của stage trả về như một thay đổi hành vi thực sự.

## 7. Điều gì nằm trong phạm vi được advice bao quanh?

Điểm bắt đầu và kết thúc phải đi cùng **loại join point** và **điều kiện pointcut**:

- Với join point kiểu **method execution**, ranh giới trực tiếp là lượt chạy thân phương thức, kể cả khi thân phương thức chỉ lập lịch một công việc khác.
- Với join point kiểu **method call**, ranh giới là thao tác gọi được instrument ở vị trí gọi, với các ngữ nghĩa cụ thể tùy trình dệt AOP. Nó cũng không tự bao trùm toàn bộ tác vụ được tạo ra.
- Với advice quanh callback, tác vụ con hoặc hàm thực sự thực thi nền, có thể quan sát được *một ranh giới khác*. Nhưng ranh giới đó cần được định nghĩa và chọn rõ; không phải hệ quả tự động của pointcut trên hàm khởi tạo.

Có hai mô hình trách nhiệm thường dùng:

**Mô hình 1 – đo lời gọi:** Tên metric phải phản ánh thời gian tạo/lấy handle, ví dụ invocation duration. Điểm kết thúc là return/throw của join point. Đây là lựa chọn đúng khi mục đích là đo overhead của hàm lập lịch hoặc adapter.

**Mô hình 2 – đo tác vụ logic:** Tên metric phản ánh thời gian từ lúc bắt đầu thao tác đến completion theo hợp đồng, ví dụ operation duration. Advice khởi tạo phép đo lúc bắt đầu và đăng ký observer phù hợp lên handle. Điểm kết thúc là terminal outcome đã xác định, không phải lần return ban đầu.

Một hệ thống có thể dùng cả hai metric, nhưng phải đặt tên và mô tả sao cho không trộn chúng trong cùng một đồ thị.

## 8. Các điểm nâng cao dễ làm sai

### 8.1. Một phương thức có thể khởi tạo nhiều tác vụ

Nếu phương thức khởi tạo ba tác vụ rồi trả về stage tổng hợp, lời gọi kết thúc khi stage tổng hợp được **trả về**. Tác vụ logic kết thúc khi **stage tổng hợp hoàn tất** theo phép tổng hợp đã chọn. Ví dụ allOf hoàn tất khi tất cả các future thành phần đã hoàn tất; nếu có thành phần hoàn tất ngoại lệ, future tổng hợp hoàn tất ngoại lệ theo đặc tả của allOf. Không suy từ completion của một tác vụ con rằng toàn bộ nghiệp vụ đã hoàn tất.

Tác vụ chạy nền không nằm trong stage tổng hợp có thể tiếp tục chạy ngay cả sau khi stage tổng hợp đã complete. Nếu yêu cầu phải chờ tác vụ đó, hợp đồng kết quả cần bao gồm nó.

### 8.2. Completion có thể xảy ra trước hoặc ngay trong lúc đăng ký observer

Không xây thiết kế dựa trên thứ tự giả định “return trước, callback sau”. Với stage đã hoàn tất sẵn, khi đăng ký callback thì callback có thể chạy ngay. Dùng cơ chế đăng ký completion chính thức của API để tránh race giữa kiểm tra trạng thái và đăng ký. Mẫu “nếu chưa done thì mới đăng ký callback” có thể đánh mất kết quả nếu trạng thái đổi giữa hai bước.

### 8.3. Context không tự đi theo thread

Thông tin như correlation ID, trace context, người khởi tạo thao tác hay thời điểm bắt đầu có thể được lưu trong đối tượng context phù hợp. Nếu lưu duy nhất trong ThreadLocal rồi đọc ở completion callback, callback chạy trên thread khác có thể không có giá trị đó. Capture dữ liệu cần thiết ở ranh giới ban đầu và truyền/khôi phục context một cách tường minh theo cơ chế runtime sử dụng. Đừng giả định after-advice và completion callback luôn dùng chung thread.

### 8.4. Không giữ tài nguyên theo lifetime sai

Ví dụ: mở tài nguyên trước proceed(), trả về future rồi đóng trong finally ngay sau proceed(). Nếu công việc nền còn dùng tài nguyên ấy, nó có thể bị đóng quá sớm. Ngược lại, gắn đóng tài nguyên vào completion callback nhưng không xử lý nhánh throw đồng bộ, hủy và timeout có thể gây rò rỉ.

Phải hỏi **ai sở hữu tài nguyên** và **sự kiện nào chứng minh không còn ai dùng nó**. Không buộc dọn dẹp theo completion nếu tác vụ phụ vẫn cần tài nguyên, cũng không kéo dài lifetime chỉ vì đo thời gian.

### 8.5. Chặn chờ bên trong advice có thể làm hỏng thiết kế

Gọi get(), join() hoặc một cơ chế block tương đương ngay trong around advice để “đợi cho xong” có thể làm mất tính bất đồng bộ, tốn thread, gây bế tắc khi runtime cần chính thread đang bị chặn để hoàn tất kết quả, hoặc làm sai timeout kỳ vọng. Theo dõi completion nên dùng cơ chế thông báo/composition phù hợp thay vì ép blocking.

### 8.6. Lỗi của instrumentation phải có chính sách riêng

Lỗi ghi log/metrics không nên tự biến một kết quả nghiệp vụ thành thất bại ngoài ý muốn. Nếu observer ném lỗi, các stage phụ thuộc có thể hoàn tất lỗi dù future nguồn không lỗi. Nếu hệ thống cố ý yêu cầu bước bổ sung là điều kiện bắt buộc, phải mô tả rõ rằng đó là thay đổi hợp đồng, thay vì coi nó chỉ là “log vô hại”.

### 8.7. Một thao tác có thể tạo nhiều ranh giới theo tầng

Ví dụ: enqueue thành công, worker bắt đầu, xử lý chính hoàn tất, dữ liệu được xác nhận bền vững, callback hậu xử lý hoàn tất. Mỗi sự kiện có ý nghĩa khác nhau. Advice tại hàm enqueue chỉ biết chắc enqueue đã được xác nhận theo hợp đồng của hàm đó. Muốn đo “nghiệp vụ bền vững” phải lấy tín hiệu xác nhận thích hợp.

## 9. Cách xác định ranh giới đúng khi thiết kế một aspect

Trước khi viết pointcut và advice, lần lượt trả lời các câu hỏi:

1. **Join point nào được bao quanh?** Một lần gọi, thực thi phương thức, callback hay điểm xử lý nền?
2. **Đầu vào và đầu ra của phương thức là gì?** Giá trị đồng bộ, CompletionStage, Future, Publisher, callback, hay fire-and-forget?
3. **Tín hiệu nào chứng minh mục tiêu đã kết thúc?** Return/throw, completion thành công/thất bại, onComplete/onError, cancel, hay xác nhận từ hệ thống bên ngoài?
4. **Ai phát tín hiệu ấy?** Chính phương thức, executor, một thread khác, subscriber, hoặc tầng lưu trữ?
5. **Có thể có nhiều tín hiệu không?** Nhiều subscription, retry, tác vụ con, lệnh gọi lại, hoặc cùng thao tác được theo dõi ở nhiều aspect?
6. **Ngoại lệ đồng bộ và thất bại bất đồng bộ được ghi nhận thế nào?** Cần hai đường xử lý riêng và đúng một kết quả cuối mỗi thao tác logic.
7. **Quan sát có làm đổi hành vi không?** Kiểm tra việc block, bọc future, lan truyền cancellation, ngoại lệ observer, context và thời gian giữ tài nguyên.
8. **Metric/log thể hiện thời lượng nào?** Invocation duration và end-to-end operation duration phải có nhãn riêng, có điểm bắt đầu và kết thúc được mô tả rõ.

Có thể lập bảng thiết kế ngắn trước khi triển khai:

| Mục tiêu | Start | End | Outcome |
| --- | --- | --- | --- |
| Đo thời gian lời gọi phương thức | Trước proceed() | Ngay sau return hoặc throw đồng bộ | Returned / synchronous failure |
| Đo xử lý async bằng CompletionStage | Trước khi khởi tạo thao tác | Stage hợp đồng hoàn tất | Success / exceptional / cancellation, phân loại thêm timeout nếu biết |
| Theo dõi xử lý Publisher | Tại subscription (hoặc điểm bắt đầu phù hợp) | Theo mỗi subscription: terminal signal hoặc cancel | Complete / error / cancel |
| Theo dõi công việc gửi vào queue | Theo mốc nghiệp vụ được chọn | Acknowledgement tương ứng từ queue/worker | Accepted / processed / durable… đúng theo hợp đồng |

## 10. Các lỗi thường gặp và cách sửa

**Lỗi 1: ghi “hoàn thành tác vụ” trong after-returning advice của một hàm trả về future.** Ở thời điểm đó chỉ biết hàm đã trả về handle. Sửa bằng cách đổi tên sự kiện thành “handle returned” hoặc theo dõi terminal completion của handle.

**Lỗi 2: bắt throw đồng bộ rồi cho rằng đã bao phủ mọi lỗi.** Một future có thể hoàn tất ngoại lệ sau đó. Sửa bằng cách xử lý riêng throw trước return và failure tại completion; cần cơ chế tránh đếm hai lần nếu instrumentation bao phủ nhiều tầng.

**Lỗi 3: dùng finally của around advice để đóng tài nguyên mà async work vẫn sử dụng.** Sửa bằng chính sách sở hữu tài nguyên theo vòng đời thực và xử lý đủ các đường kết thúc.

**Lỗi 4: thêm join() vào advice để có thể log kết quả cuối.** Sửa bằng callback/composition phi chặn; blocking chỉ được dùng khi chính hợp đồng gọi yêu cầu chờ đồng bộ và đã đánh giá ảnh hưởng.

**Lỗi 5: coi cancellation là success hoặc khẳng định đã dừng worker.** Sửa bằng phân loại cancellation riêng và xác nhận ngữ nghĩa hủy thực tế của API.

**Lỗi 6: trả về stage khác mà không xem xét tương thích.** Sửa bằng cách đối chiếu chữ ký, kiểu runtime, danh tính handle, propagation của lỗi/hủy và thời điểm complete.

**Lỗi 7: giả định có một completion duy nhất cho Publisher.** Sửa bằng đơn vị đo theo subscription, vì mỗi subscriber có vòng đời riêng; cancel cũng cần ghi nhận dù không có terminal callback.

## 11. Tình huống tự kiểm tra

**Tình huống A.** Một phương thức trả về CompletableFuture sau 2 ms; sau 500 ms future mới hoàn tất. Around advice chạy proceed() và kết thúc ngay sau khi nhận future. Hỏi hai thời lượng đo được là gì?

**Giải đáp:** advice mặc định đo xấp xỉ 2 ms cho lượt gọi (nếu điểm bắt đầu nằm trước khi khởi tạo); observer completion có thể đo xấp xỉ 500 ms cho thao tác logic. Số cụ thể phụ thuộc mốc start và overhead thực tế, nhưng hai khoảng có bản chất khác nhau.

**Tình huống B.** Phương thức trả về một CompletableFuture đã completeExceptionally. After-returning hay after-throwing của *lời gọi đó* được kích hoạt?

**Giải đáp:** theo ngữ nghĩa thông thường, lời gọi **trả về bình thường** một handle; after-returning nhận handle. Trạng thái lỗi của handle chỉ được nhận biết khi quan sát completion hoặc lấy kết quả.

**Tình huống C.** Advice đăng ký callback sau khi future đã complete. Có bị mất completion không?

**Giải đáp:** không, nếu dùng đúng API CompletionStage/CompletableFuture: callback phụ thuộc có thể được thực thi ngay hoặc theo lịch thực thi của API. Điều nguy hiểm là tự kiểm tra trạng thái rồi bỏ qua đăng ký callback.

**Tình huống D.** Một publisher được trả về nhưng không bao giờ có subscriber. Advice quanh hàm trả về có chứng minh việc xử lý dữ liệu đã kết thúc không?

**Giải đáp:** không. Lần trả về chỉ chứng minh publisher đã được tạo/lấy. Muốn theo dõi việc tiêu thụ phải quan sát subscription và các tín hiệu liên quan theo hợp đồng publisher.

## 12. Kết luận kỹ thuật

Khi xác định điểm kết thúc của hành vi được advice bao quanh, hãy viết rõ **đối tượng quan sát** và **tín hiệu hoàn tất**:

- **Advice theo join point:** kết thúc ở return/throw của lượt thực thi được intercept.
- **Theo dõi tác vụ bất đồng bộ:** kết thúc ở completion/terminal signal/cancellation được hợp đồng API xác định, có xử lý ngoại lệ và ngữ cảnh tương ứng.
- **Theo dõi tài nguyên hoặc tác động phụ:** kết thúc khi chính nghĩa vụ đó thực sự hoàn thành, đôi khi muộn hơn completion của kết quả chính.

Một aspect đúng phải làm rõ ranh giới này trong pointcut, advice, tên sự kiện và hợp đồng quan sát; nếu không, log có thể báo “đã xong” khi công việc thực tế vẫn đang chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-boundaries-009">Khi nào nên dùng cơ chế chuyên biệt của mô hình bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào nên dùng cơ chế chuyên biệt của mô hình bất đồng bộ trong AOP?

## 1. Vấn đề cốt lõi: lời gọi đã kết thúc, công việc có thể vẫn đang chạy

Aspect-Oriented Programming (AOP) tách những mối quan tâm cắt ngang như ghi log, đo thời gian, tracing, metrics, bảo mật và quan sát lỗi khỏi logic nghiệp vụ chính. Advice có thể thực thi trước, sau hoặc bao quanh một join point, thường là lời gọi phương thức. Trong mã đồng bộ, thời điểm phương thức trả về hoặc ném ngoại lệ thường cũng là thời điểm phần công việc được gọi đã kết thúc.

Trong mã bất đồng bộ, **lời gọi phương thức** và **vòng đời công việc** là hai sự kiện khác nhau:

- Phương thức có thể trả về ngay một future, promise, handle, subscription hoặc kết quả đăng ký callback.
- Công việc có thể được xếp hàng, bắt đầu sau đó và chỉ thành công/thất bại khi phương thức gốc đã trả về từ lâu.
- Callback có thể được gọi nhiều lần, được gọi ngay trong bước đăng ký hoặc chạy ở luồng khác.
- Kết quả có thể thất bại, bị hủy hoặc kết thúc từng phần theo một giao thức riêng; exception không nhất thiết thoát qua call stack của lời gọi ban đầu.

Ví dụ một phương thức trả về CompletionStage<String> có thể trả đối tượng stage thành công mà stage đó vẫn đang pending. Một advice kiểu after-returning quan sát được **hành vi trả về của lời gọi**, nhưng chưa thể kết luận nghiệp vụ đã thành công. Một advice kiểu after-throwing quanh lời gọi chỉ bắt exception thoát ra ở thời điểm gọi; lỗi phát sinh khi stage hoàn tất sau đó sẽ cần một cơ chế khác.

~~~text
t0: caller --> advice --> method submit() ----> trả handle/future --> advice kết thúc
                              |
                              +--> công việc được xếp lịch / đăng ký
t1:                         runtime thực thi công việc
t2:                         callback, success, error hoặc cancel
                              |
                              +--> điểm quan sát theo mô hình bất đồng bộ
~~~

**Nguyên tắc:** dùng advice tổng quát để quan sát **invocation boundary**; dùng hook, handler hoặc decorator của mô hình bất đồng bộ khi cần quan sát **completion boundary**, **callback execution**, **subscription lifecycle** hay **task lifecycle**.

## 2. Thế nào là một cơ chế chuyên biệt?

“Cơ chế chuyên biệt” ở đây không đồng nghĩa với việc chọn một thư viện AOP khác. Đó là điểm mở rộng do chính mô hình bất đồng bộ cung cấp để phản ánh đúng trạng thái thực:

| Mô hình | Cơ chế phù hợp | Thông tin có thể quan sát |
| --- | --- | --- |
| Future/CompletionStage | Completion callback như whenComplete, handle, listener khi hoàn tất | Thành công, lỗi hoàn tất, hủy, thời điểm complete |
| Callback một lần | Bao callback nhận kết quả/lỗi, hook của dispatcher | Callback nào được gọi, thời điểm gọi, lỗi dữ liệu hoặc lỗi trong handler |
| Listener nhiều lần | Hook vòng đời đăng ký, từng sự kiện và hủy đăng ký | Sự kiện, lỗi, số lượng callback, tài nguyên còn đăng ký |
| Reactive stream / Publisher | Hook subscribe, onNext, onError, onComplete, cancel theo giao thức stream | Vòng đời từng subscription, tín hiệu lỗi, hoàn tất, hủy, backpressure |
| Executor / queue / background task | Decorator Runnable/Callable/task, completion handle hoặc task supervisor | Thời điểm thực thi, kết quả công việc, lỗi task và hủy |
| Fire-and-forget | Worker-side error handler, event/queue consumer, correlation và trạng thái riêng | Kết quả sau khi lời gọi enqueue đã thành công |
| Coroutine / continuation | Hook completion, cancellation, structured scope và context của mô hình đó | Hoàn tất tác vụ logic qua nhiều lần tạm dừng và tiếp tục |

Bảng chỉ mô tả loại cơ chế; tên API và bảo đảm thực tế tùy môi trường. Trong Java 17, CompletionStage, CompletableFuture và ExecutorService cung cấp các ví dụ cụ thể dễ kiểm chứng mà không cần thêm thư viện.

## 3. Tiêu chí quyết định: dùng cơ chế chuyên biệt khi nào?

### 3.1. Kết quả cuối cùng xuất hiện sau khi lời gọi đã trả về

Hãy dùng hook completion nếu yêu cầu là:

- Đo thời gian **đến khi tác vụ kết thúc**, không chỉ thời gian tạo future.
- Ghi “thành công” sau khi kết quả đã sẵn sàng.
- Ghi nguyên nhân thất bại nếu kết quả chuyển sang trạng thái exceptional.
- Phân loại trạng thái canceled riêng theo hợp đồng.
- Tính metrics như số tác vụ hoàn thành, tỉ lệ lỗi hay latency thực.

Nếu chỉ cần đo chi phí chuẩn bị request, validate input, enqueue task hoặc tạo handle, một advice đồng bộ là đủ và thường đơn giản hơn. Không nên gắn completion observer vào mọi phương thức chỉ vì chữ “async” xuất hiện trong tên.

### 3.2. Lỗi không đi qua call stack ban đầu

Nếu target có thể trả về bình thường nhưng lỗi nghiệp vụ được chuyển qua future, error callback, stream signal hoặc task supervisor, quanh target bằng try/catch chỉ quan sát được lỗi đồng bộ lúc gọi. Khi mục đích là lỗi cuối cùng của công việc, phải theo dõi **error channel thực sự**.

Ngược lại, một callback được gọi ngay trong thao tác đăng ký, không bị dispatcher bắt lỗi, có thể ném exception qua chính lời gọi đăng ký. Không được đồng nhất “callback” với “chạy ở luồng khác”. Chọn điểm quan sát theo **cơ chế gọi callback và hợp đồng truyền lỗi**, không theo tên hàm.

### 3.3. Có nhiều mốc vòng đời hoặc nhiều lần phát dữ liệu

Một stream thường có onSubscribe, zero hoặc nhiều onNext, và một tín hiệu kết thúc onError/onComplete; cancellation có thể do subscriber khởi xướng. Với mô hình như vậy:

- After-returning từ hàm tạo Publisher không tương ứng với subscription hay dữ liệu đã được xử lý.
- Một Publisher có thể được subscribe nhiều lần; mỗi lần đăng ký tạo vòng đời quan sát riêng.
- “Nhận phần tử đầu tiên” không phải là “stream hoàn tất”.
- Lỗi stream phải được quan sát tại error signal; lỗi do observer tự gây ra là một vấn đề khác.
- Cancellation không đồng nghĩa onComplete; phải đọc đúng hợp đồng stream.

Đây là tình huống điển hình cần cơ chế theo stream thay vì một advice chỉ bao quanh hàm trả về Publisher.

### 3.4. Cần truyền ngữ cảnh xuyên ranh giới lịch thực thi

Một aspect có thể đặt correlation ID, trace ID hay ngữ cảnh người gọi vào ThreadLocal ở luồng ban đầu. Khi tác vụ chạy trên executor khác, ThreadLocal này không tự động xuất hiện ở luồng thực thi. Nếu muốn tracing xuyên các callback, cần cơ chế **capture, propagation, restore và cleanup** ở ranh giới thích hợp.

Hook gắn task, callback, completion hoặc subscription cho phép thiết lập lại context khi callback thật sự chạy. Không giữ context vĩnh viễn trên luồng dùng chung, vì thread pool có thể tái sử dụng luồng cho request khác.

### 3.5. Cần xử lý hủy, timeout, retry hoặc tài nguyên theo vòng đời

Một thao tác có thể tạo future rồi bị hủy; kết quả cũng có thể đến muộn sau khi caller đã bỏ cuộc. Nếu advice giải phóng tài nguyên khi phương thức trả về, trong khi callback còn sử dụng tài nguyên, sẽ có lỗi lifecycle. Dùng callback hoàn tất hoặc cơ chế quản lý phạm vi tác vụ khi cleanup thực sự phụ thuộc việc hoàn tất/hủy.

Retry và timeout cũng phải được thiết kế dựa trên **kết quả thực**: retry một lỗi xảy ra sau return không thể đạt được chỉ bằng cách thử gọi lại quanh lời gọi đầu tiên nếu code không chờ hoặc quan sát kết quả. Tuy nhiên không tự động retry chỉ vì một completion callback thấy lỗi; có thể nhân đôi side effect và vi phạm tính idempotent của nghiệp vụ.

## 4. Khi nào advice AOP tổng quát đã đủ?

Cơ chế chuyên biệt không phải lựa chọn mặc định cho mọi join point. Dùng advice đồng bộ nếu mục tiêu là quan sát hoặc can thiệp tại chính lời gọi:

1. Kiểm tra đầu vào hoặc điều kiện truy cập **trước khi** bắt đầu đăng ký công việc.
2. Ghi thời điểm enqueue, loại công việc và request ID ban đầu.
3. Đo latency của việc khởi tạo/schedule.
4. Bắt lỗi phát sinh ngay khi xây dựng future, tạo handler hoặc gửi tác vụ vào queue.
5. Kiểm soát số lượng thao tác đang được đăng ký theo chính sách thực thi phù hợp.
6. Chuẩn hóa metadata đầu vào của lời gọi mà không cần biết công việc kết thúc ra sao.

Nếu nghiệp vụ trả giá trị bình thường và không tạo vòng đời bất đồng bộ riêng, càng không cần một hook theo future hay callback.

Điểm quyết định là **câu hỏi cần trả lời**:

- “Method này có gọi được không?” → advice quanh lời gọi.
- “Việc đăng ký công việc có thành công không?” → advice quanh thao tác đăng ký.
- “Công việc có hoàn tất thành công không?” → completion/error hook.
- “Callback có ném lỗi khi xử lý dữ liệu không?” → bao quanh chính callback hoặc theo dõi dependent stage.
- “Một subscription đã nhận gì và chấm dứt bằng cách nào?” → lifecycle hooks của stream.
- “Tác vụ có thực sự chạy trên executor không?” → execution-side wrapper/hook.

## 5. CompletionStage: một ví dụ điển hình

Giả sử một phương thức trả về CompletionStage<T>. Khi trả về bình thường, advice chỉ biết phương thức đã tạo và trả stage. Để đo kết quả hoàn tất, cần gắn observer:

~~~java
// Đoạn minh họa trong một phương thức generic; không phải chương trình độc lập.
CompletionStage<T> stage = invokeTarget(); // Có thể ném lỗi đồng bộ.
return stage.whenComplete((value, failure) -> {
    if (failure == null) {
        recordCompletedSuccessfully();
    } else {
        recordCompletedExceptionally(failure);
    }
});
~~~

**whenComplete** trả về **một CompletionStage mới**, không phải luôn chính đối tượng ban đầu. Callback quan sát có thể chạy ngay trong thread gọi khi stage đã hoàn tất, hoặc trên thread làm stage hoàn tất. Không giả định luôn dùng một worker thread riêng. Để giữ đúng ngữ nghĩa thành công/thất bại:

- Logic quan sát nên tránh ném exception, vì exception trong observer có thể làm dependent stage thất bại khi stage gốc thành công.
- Không dùng exceptionally để tạo fallback nếu yêu cầu chỉ là metrics: nó có thể biến lỗi thành giá trị thành công.
- Không gọi join/get ngay trong advice để “đợi cho chắc”, vì sẽ làm mất ưu điểm không chặn của pipeline và có thể gây deadlock trong môi trường thiếu worker.
- Không khẳng định stage mới và stage gốc có cùng identity, cancellation propagation, callback timing hoặc cách xử lý completion của tất cả implementation.
- Nếu giao thức bắt buộc giữ nguyên đối tượng trả về, có thể đăng ký observer phụ rồi trả stage gốc, nhưng cần thiết kế rõ cách ghi nhận lỗi của **dependent stage** do observer tạo ra.

Các hàm có tên giống nhau không thay thế được nhau:

| API | Ngữ nghĩa tiêu biểu | Mục đích AOP cần cân nhắc |
| --- | --- | --- |
| whenComplete | Quan sát result/error, tạo dependent stage | Telemetry completion; observer nên không thất bại |
| handle | Biến đổi cả nhánh success/error thành một kết quả mới | Dùng khi chủ ý chuyển đổi kết quả |
| exceptionally | Phục hồi hoặc biến đổi nhánh error | Không dùng để vô tình nuốt lỗi |
| thenApply / thenAccept | Xử lý khi stage trước thành công | Lỗi trong callback làm dependent stage thất bại |
| join / get | Tiêu thụ kết quả, có thể block; báo lỗi khi tiêu thụ | Thích hợp ở ranh giới chờ kết quả có chủ đích, không làm default observer |

Trong CompletableFuture, completion exceptional và callback failure có thể xuất hiện thông qua CompletionException hoặc ExecutionException khi caller đọc kết quả. Cần xem cause để ghi nhận lỗi gốc nhưng vẫn bảo toàn hợp đồng của API.

### 5.1. Bài thực hành Java 17+: invocation so với completion

Tạo file **AsyncAdviceSelectionDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class AsyncAdviceSelectionDemo {
    static <T> CompletionStage<T> observe(
            String operation,
            Supplier<? extends CompletionStage<T>> action) {
        System.out.println("CALL " + operation);

        CompletionStage<T> stage;
        try {
            stage = action.get();
        } catch (RuntimeException failure) {
            System.out.println("INVOCATION ERROR " + operation
                    + ": " + failure.getMessage());
            throw failure;
        }

        System.out.println("RETURN " + operation);

        return stage.whenComplete((value, failure) -> {
            if (failure == null) {
                System.out.println("COMPLETE SUCCESS " + operation);
            } else {
                System.out.println("COMPLETE ERROR " + operation
                        + ": " + rootMessage(failure));
            }
        });
    }

    static String rootMessage(Throwable failure) {
        while (failure instanceof CompletionException
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure.getMessage();
    }

    public static void main(String[] args) {
        CompletableFuture<String> pending = new CompletableFuture<>();
        CompletionStage<String> monitored = observe("fetch", () -> pending);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> pending.completeExceptionally(
                    new IllegalStateException("upstream unavailable")));

            try {
                monitored.toCompletableFuture().join();
            } catch (CompletionException failure) {
                System.out.println("CALLER ERROR: " + rootMessage(failure));
            }
        } finally {
            executor.shutdown();
        }

        CompletableFuture<String> source =
                CompletableFuture.completedFuture("input");
        CompletableFuture<Integer> dependent = source.thenApply(value -> {
            throw new IllegalArgumentException("invalid mapping");
        });

        System.out.println("SOURCE VALUE: " + source.join());

        try {
            dependent.join();
        } catch (CompletionException failure) {
            System.out.println("CALLBACK ERROR: " + rootMessage(failure));
        }
    }
}
~~~

Biên dịch và chạy trên Java 17+:

~~~bash
javac AsyncAdviceSelectionDemo.java
java AsyncAdviceSelectionDemo
~~~

Đầu ra:

~~~text
CALL fetch
RETURN fetch
COMPLETE ERROR fetch: upstream unavailable
CALLER ERROR: upstream unavailable
SOURCE VALUE: input
CALLBACK ERROR: invalid mapping
~~~

**Vì sao thứ tự này xác định?** Hàm observe ghi CALL và RETURN trước khi submit thao tác hoàn tất future vào executor. Lời gọi join trên dependent stage chỉ trả lỗi sau khi completion callback của observer xử lý xong. Sau khi khối đầu tiên kết thúc, main mới chuyển sang ví dụ stage thứ hai. Ví dụ dùng một thread executor riêng để minh họa ranh giới thực thi, nhưng chính trạng thái stage và cơ chế callback mới quyết định ranh giới hoàn tất.

**Ý nghĩa:**

- RETURN fetch là việc **lời gọi tạo/nhận future** đã trả về, không phải fetch thành công.
- COMPLETE ERROR fetch ghi nhận lỗi tại completion boundary.
- CALLER ERROR xuất hiện khi caller tiêu thụ stage bằng join, nơi CompletionException được ném.
- source đã thành công, nhưng callback thenApply ném IllegalArgumentException. Lỗi thuộc **dependent**, không làm source đổi sang trạng thái failed.
- Callback thenApply có thể chạy ngay khi đăng ký, vì source đã hoàn thành. Callback không nhất thiết chạy bất đồng bộ chỉ vì dùng CompletionStage.

Mẫu observe chỉ minh họa một điểm mở rộng tương đương advice. Nó không tự weaving join point. Trong ứng dụng thực, gắn hook thông qua cơ chế interception/weaving hoặc wrapper thích hợp của hệ thống đang dùng.

## 6. Callback API: theo dõi đúng cả kết quả và handler

Với API nhận callback như onSuccess(value) và onFailure(error), lời gọi đăng ký có thể kết thúc trước khi callback được gọi. Một aspect quanh việc đăng ký nên ghi “registered”, còn wrapper tại callback mới biết tác vụ thành công hay thất bại. Đồng thời, cần phân biệt **lỗi của tác vụ** với **lỗi do chính handler của người dùng ném ra**.

Đoạn sau là mã minh họa có thể đặt trong một chương trình Java với giao diện tương ứng:

~~~java
interface ResultCallback<T> {
    void onSuccess(T value);
    void onFailure(Throwable failure);
}

static <T> ResultCallback<T> instrument(
        ResultCallback<T> delegate) {
    return new ResultCallback<>() {
        @Override
        public void onSuccess(T value) {
            recordTaskSuccess();
            try {
                delegate.onSuccess(value);
            } catch (RuntimeException handlerFailure) {
                recordCallbackFailure(handlerFailure);
                throw handlerFailure;
            }
        }

        @Override
        public void onFailure(Throwable failure) {
            recordTaskFailure(failure);
            try {
                delegate.onFailure(failure);
            } catch (RuntimeException handlerFailure) {
                recordCallbackFailure(handlerFailure);
                throw handlerFailure;
            }
        }
    };
}
~~~

Ở đây, recordTaskSuccess và recordTaskFailure ghi **tín hiệu kết quả công việc**; recordCallbackFailure ghi **lỗi thực thi callback delegate**. Các phương thức record... là những hook minh họa, không có định nghĩa độc lập trong đoạn trích. Wrapper ném lại lỗi handler để tôn trọng cơ chế xử lý ngoại lệ sẵn có của dispatcher. Nếu thư viện yêu cầu handler không được throw, cần tuân theo chính sách lỗi của thư viện.

Các điểm cần kiểm tra trước khi bọc callback:

1. Callback có thể gọi không, một hay nhiều lần? Có cả callback terminal riêng không?
2. Có thể gọi callback đồng thời hay tái nhập (reentrant) không?
3. Có error callback riêng không? Exception handler có bị runtime nuốt/log không?
4. API có yêu cầu callback identity để removeListener/unsubscribe không? Nếu có, phải giữ mapping wrapper ↔ callback gốc.
5. Việc gắn wrapper có làm thay đổi ordering, chính sách retry hoặc luồng callback không?
6. Khi callback delegate thất bại, có nên để exception truyền tiếp hay API yêu cầu chuyển thành error signal? Hợp đồng API quyết định.

Nếu callback chỉ đăng ký rồi người gọi không có đối tượng kết quả, không thể tự suy diễn kết quả từ return của register. Nếu callback báo nhiều event, không đếm mỗi onSuccess là “toàn bộ công việc hoàn thành” trừ khi giao thức quy định như vậy.

## 7. Executor và fire-and-forget: quan sát ở nơi task chạy

Giả sử phương thức submitTask chỉ đặt Runnable vào executor:

~~~java
// Ví dụ minh họa; executor và runnable đã được khai báo.
executor.execute(runnable);
return;
~~~

Advice quanh submitTask chỉ thấy thao tác gửi task. Để đo thời gian thực thi, truy ngữ cảnh hay bắt lỗi trong Runnable, cần **decorator của task**:

~~~java
static Runnable observedTask(Runnable delegate) {
    return () -> {
        long startedAt = System.nanoTime();
        try {
            delegate.run();
            recordSuccess(System.nanoTime() - startedAt);
        } catch (RuntimeException failure) {
            recordFailure(failure, System.nanoTime() - startedAt);
            throw failure;
        }
    };
}
~~~

Đây là đoạn minh họa sử dụng các hook recordSuccess/recordFailure do người triển khai cung cấp. Nó đo từ lúc Runnable thực sự được chạy, không tính thời gian task nằm trong queue. Nếu cần end-to-end latency, lưu thêm timestamp tại thời điểm submit rồi kết hợp với timestamp lúc bắt đầu và hoàn tất thực thi.

**Khác biệt giữa execute và submit rất quan trọng:** Với nhiều implementation dựa trên ExecutorService, submit tạo Future ghi nhận exception của task; lỗi đó thường hiển thị khi gọi Future.get. Với execute, exception không bị Future của submit bao lại vì không có Future đó, và cách uncaught exception được xử lý tùy executor/runtime. Không suy ra một chính sách báo lỗi thống nhất cho mọi executor.

Nếu công việc bị từ chối ngay khi submit do executor đã shutdown hoặc quá tải, ngoại lệ có thể phát sinh **đồng bộ tại thao tác submit**; đây là một kết quả riêng, không có nghĩa task đã được chạy hay đã thất bại trong khi chạy. Với task bị hủy trước khi thực thi, wrapper Runnable có thể không bao giờ chạy: phải theo dõi cả cancellation/queue lifecycle nếu yêu cầu đo mọi submission.

## 8. Reactive Publisher: quan sát mỗi subscription thay vì phương thức tạo Publisher

Một phương thức có thể trả Publisher mà chưa thực thi công việc. Với nhiều Publisher, việc thực thi bắt đầu khi subscriber đăng ký. Mỗi subscription có thể có vòng đời riêng, và một Publisher có thể được đăng ký nhiều lần.

~~~text
buildPublisher() -> trả Publisher            (chỉ dựng mô tả pipeline)
                         |
subscriber A --------> subscribe ----> onNext* ----> onComplete/onError
                         |
subscriber B --------> subscribe ----> onNext* ----> onComplete/onError
                         |
cancel signal --------> kết thúc theo chính sách hủy của subscription
~~~

Cơ chế phù hợp ở đây là hook/decorator có hiểu giao thức tín hiệu:

- **onSubscribe:** bắt đầu vòng đời subscription và nhận subscription handle.
- **onNext:** quan sát từng phần tử; ghi số lượng và thời gian xử lý nếu cần, nhưng không biến onNext đầu tiên thành completion.
- **onError:** ghi lỗi terminal của subscription.
- **onComplete:** ghi hoàn tất bình thường.
- **cancel:** ghi việc subscriber chủ động hủy; không tự đồng nhất với success hay failure.
- **request/backpressure:** nếu instrumentation chuyển tiếp các tín hiệu, phải giữ nguyên số lượng/yêu cầu và thứ tự theo hợp đồng, không phát phần tử ngoài nhu cầu.

Không nên quan sát Publisher bằng cách tự subscribe trong advice chỉ để lấy log: thao tác này có thể kích hoạt công việc thêm một lần, tạo side effect, thay đổi tài nguyên hoặc làm sai số lượng subscriber. Hãy dùng operator/hook quan sát được cung cấp bởi mô hình hoặc decorator bảo toàn giao thức. Không blocking để chờ stream kết thúc trong lời gọi xây dựng Publisher.

Nếu Publisher phát nhiều phần tử, completion latency là từ **mốc định nghĩa của subscription** đến terminal signal, không phải từ lúc tạo đối tượng Publisher. Cần phân biệt một stream tồn tại lâu dài không bao giờ tự complete với một tác vụ một lần kết thúc hữu hạn.

## 9. Lỗi và hủy: thiết kế observation contract trước khi triển khai

Một hệ thống giám sát nên định nghĩa chính xác các trạng thái ở từng tầng:

| Tầng | Ví dụ trạng thái | Ý nghĩa |
| --- | --- | --- |
| Invocation | entered, returned, threw | Lời gọi đi vào và thoát ra thế nào |
| Registration/submit | accepted, rejected | Runtime có nhận đăng ký/task không |
| Execution | started, finished | Task thật sự bắt đầu và kết thúc khi nào |
| Completion | succeeded, failed, canceled | Kết quả cuối của operation theo giao thức |
| Callback execution | handler succeeded, handler failed | Chính code xử lý kết quả có chạy an toàn không |
| Subscription | active, completed, errored, canceled | Một subscription riêng đã kết thúc thế nào |

Sự phân tầng tránh các lỗi thống kê như: “Tất cả lời gọi submit đều success” dù phần lớn task bị fail sau đó; hoặc “task thất bại” khi chỉ handler ghi log ném exception.

**Phân loại failure không đơn giản là catch Throwable ở mọi nơi.** Trong Java, Error khác RuntimeException; quyết định quan sát Throwable, có log hay ném lại phải dựa trên contract và tính ổn định của runtime. Không nên tự ý nuốt VirtualMachineError hay biến cancellation thành success. Nếu error được bọc trong CompletionException/ExecutionException, giữ nguyên nguyên nhân và ghi nhận cause chính xác.

**Đo thời gian cần nhiều timestamp nếu nhiều tầng:** submission timestamp, execution start, invocation return, first result, terminal event. Queue latency = execution start − submit; task runtime = terminal − execution start; end-to-end latency = terminal − submit. Không đưa các đại lượng này vào cùng một metric khi chúng đo những hiện tượng khác nhau.

**Đếm event phải tránh đếm hai lần:** Nếu completion observer được gắn nhiều lần, mỗi observer có thể ghi một completion log. Nếu cùng một stage đã được quan sát ở nhiều tầng, phải định nghĩa ranh giới sở hữu metric và dùng operation ID/correlation key để phân biệt.

## 10. Tracing và context propagation trong AOP nâng cao

Trong mô hình đồng bộ, có thể mở và đóng scope tracing quanh một lời gọi. Ở mô hình bất đồng bộ, task có thể thực thi trên luồng khác hoặc callback tiếp tục khi scope ban đầu đã đóng.

Một pipeline thường cần:

1. Tạo/capture một correlation context khi operation được đăng ký.
2. Đính kèm context vào task, callback hoặc metadata được mô hình hỗ trợ.
3. Khi xử lý callback/task, restore context tạm thời.
4. Thực thi logic nghiệp vụ và instrumentation trong context đúng.
5. Cleanup/restore context trước đó trong finally, tránh rò trên thread pool.
6. Ghi event hoàn tất/hủy vào cùng operation ID.

~~~text
thread caller:
  begin trace A -> submit task (capture A) -> return -> close caller scope

thread worker:
  restore A -> run task -> signal completion -> cleanup A

completion observer:
  use captured A / native context -> record outcome -> cleanup
~~~

Đây là ví dụ ý tưởng; trong hệ thống cụ thể, model có thể hỗ trợ propagation sẵn, hoặc cần truyền context tường minh. Không giả định ThreadLocal tự theo một CompletableFuture qua mọi callback. Không để đồng thời nhiều callback dùng chung mutable context không an toàn, và không dùng global variable duy nhất cho nhiều tác vụ.

Khi completion callback được kích hoạt ngay lúc attach observer vì stage đã hoàn tất, xử lý context vẫn phải đúng. Thiết kế không nên phụ thuộc vào giả định callback luôn xảy ra trong một luồng hoặc một thời điểm định trước.

## 11. Những thay đổi hành vi do instrumentation có thể gây ra

Aspect theo dõi là code chạy thật, nên nó cũng có thể làm thay đổi hệ thống:

**Lỗi observer:** Một callback metrics có thể throw. Với CompletionStage, lỗi đó có thể ảnh hưởng dependent stage; với Runnable wrapper, lỗi đó có thể che lỗi ban đầu. Giảm tối đa side effect, áp dụng chính sách xử lý lỗi instrumentation riêng, tránh làm mất stack trace hoặc thay đổi completion contract.

**Identity và kiểu kết quả:** Decorator tạo object mới có thể phá so sánh reference, quy tắc hủy hoặc giao kèo về kiểu cụ thể. CompletionStage.whenComplete không cam kết trả chính stage ban đầu. Khi API consumer cần chính đối tượng gốc, phải kiểm tra cách attach observer mà vẫn trả đúng object và theo dõi lỗi observer tách biệt.

**Cancellation propagation:** Hủy dependent future không luôn có nghĩa hủy tác vụ upstream. Hủy một subscription có ý nghĩa khác với kết thúc nguồn. Không bổ sung propagate cancellation theo trực giác nếu mô hình không cam kết.

**Timing và scheduling:** Callback quan sát có thể thực thi inline và làm chậm thread hoàn tất stage. Chuyển observer sang executor khác có thể giảm blocking nhưng thay đổi thứ tự, điểm hoàn tất và nhu cầu quản lý shutdown. Tránh tác vụ logging nặng trong callback chạy trên event loop.

**Cardinality:** Một callback có thể được gọi nhiều lần, một Publisher có nhiều subscriber, một task có thể retry. Cần metric theo **operation**, **attempt**, **subscription**, hoặc **event** đúng yêu cầu; không gom các đơn vị này một cách tùy tiện.

**Lifecycle resources:** Đóng stream, file, buffer hay connection ở finally của lời gọi tạo tác vụ có thể quá sớm. Nếu tài nguyên thuộc về hoạt động bất đồng bộ, cleanup nên nằm tại completion/error/cancel tương ứng, với bảo đảm idempotent trong race condition.

**AOP join point coverage:** Proxy/interceptor quanh lời gọi không mặc nhiên quan sát mọi callback, task, hoặc lệnh nội bộ. Weaving có thể mở rộng loại join point quan sát được, nhưng cũng không tự hiểu completion semantics của một protocol. Độ sâu instrumentation phải tương ứng hợp đồng runtime.

## 12. Quy trình lựa chọn trong dự án

Dùng checklist dưới đây khi quyết định giữa advice tổng quát và hook chuyên biệt:

| Câu hỏi | Nếu câu trả lời là “có” | Hành động |
| --- | --- | --- |
| Lời gọi kết thúc là đủ để hoàn thành yêu cầu đo/ghi log? | Có | Dùng advice quanh lời gọi, không thêm hook |
| Có future/promise thể hiện outcome đến sau return? | Có | Theo dõi completion/error/cancel của stage |
| API trả void/handle và báo kết quả qua callback? | Có | Bọc callback hoặc dùng dispatcher hook |
| Có nhiều sự kiện, nhiều subscription hoặc stream không kết thúc ngay? | Có | Dùng lifecycle hook theo từng subscription |
| Task đi qua queue/executor? | Có | Instrument submission lẫn execution khi cần |
| Cần tracing qua nhiều luồng/continuation? | Có | Propagate context theo task/callback/continuation |
| Có retry, timeout, cancel hoặc cleanup phức tạp? | Có | Quan sát đúng trạng thái lifecycle và side effect |
| Hook quan sát có thể thay đổi lỗi, object identity hoặc scheduling? | Có | Xem xét contract và lựa chọn decorator an toàn hơn |

Sau đó thực hiện các bước:

1. **Định nghĩa đại lượng:** invocation latency, queue time, execution time, completion latency, error rate hay callback failure? Mỗi đại lượng có mốc bắt đầu/kết thúc rõ ràng.
2. **Xác định đối tượng đang đo:** một lời gọi, một submission, một task, một future, một subscriber hay một toàn bộ pipeline.
3. **Tìm chủ sở hữu tín hiệu kết thúc:** target stack, completion stage, callback, dispatcher, consumer hay subscription.
4. **Xác định nhánh terminal:** success, failure, cancellation, timeout và các tình huống callback tự throw. Không tự suy luận terminal từ “returned”.
5. **Đặt instrumentation ít xâm lấn nhất:** chỉ một hook đủ tin cậy để quan sát kết quả; bổ sung invocation advice nếu thật sự cần đo cả bước đăng ký.
6. **Bảo toàn contract:** không nuốt lỗi, không phát thêm subscription, không chặn event loop, không thay thứ tự callback, không làm sai propagation/hủy.
7. **Xác minh với race thực tế:** hoàn tất trước lúc gắn callback, callback gọi đồng bộ, hoàn tất ngay sau return, hủy trước thực thi, cùng operation có nhiều listener, callback metrics tự throw.

## 13. Những ngộ nhận thường gặp

**“After-returning nghĩa là operation success.”** Chỉ đúng nếu outcome thuộc về chính lời gọi. Future trả về bình thường có thể thất bại sau.

**“Callback luôn chạy ở thread khác.”** Sai. Callback có thể chạy trong thread hiện tại, kể cả khi đăng ký vào stage đã hoàn tất.

**“Around advice với try/catch bắt tất cả lỗi tương lai.”** Sai. Catch chỉ bắt exception lan qua call stack đang được thực thi; future/error signal cần hook đúng kênh.

**“Thêm whenComplete là hoàn toàn không đổi hành vi.”** Không luôn đúng. Nó tạo dependent stage và callback có thể throw, có chi phí thực thi, và có thể chạy trên luồng nhạy cảm về latency.

**“Một stream chỉ cần quan sát lần onNext đầu tiên.”** Sai nếu mục tiêu là outcome cuối. Một stream có thể tiếp tục nhiều phần tử, kết thúc bằng error hoặc bị hủy.

**“Có thể log bằng cách tự subscribe.”** Có thể kích hoạt nguồn thêm lần nữa, làm thay đổi semantics, phụ tải và side effect.

**“Chỉ bọc lời gọi submit là sẽ thấy lỗi khi task chạy.”** Không. Phải theo dõi task execution hoặc future/error channel của task.

**“Cancellation giống lỗi nghiệp vụ.”** Không mặc định. Cancellation có thể là quyết định bình thường của caller hoặc timeout; chính sách phân loại cần ghi rõ.

**“ThreadLocal đi theo lời gọi bất đồng bộ.”** Không được bảo đảm. Context phải được truyền hoặc restore bằng cơ chế phù hợp.

## 14. Bài tập tự đánh giá

**Tình huống 1:** Advice đo 2 ms quanh phương thức tạo CompletionStage; tác vụ mất 700 ms và thất bại. Muốn báo cáo latency thực và lỗi của tác vụ nên làm gì?

**Hướng giải:** Giữ 2 ms cho invocation latency nếu cần, đồng thời gắn completion observer vào stage để đo từ thời điểm khởi phát nghiệp vụ đến terminal event. Quan sát exceptional completion; không coi normal return là thành công của tác vụ.

**Tình huống 2:** API nhận callback mỗi khi sensor phát dữ liệu, có removeListener và không có “hoàn tất” tự động. Có nên ghi success khi callback đầu tiên chạy không?

**Hướng giải:** Không nếu metric biểu diễn thành công toàn bộ subscription. Theo dõi event count, error signal và sự kiện unregister/cancel theo giao thức. Nếu bọc listener, giữ mapping để removeListener vẫn hoạt động.

**Tình huống 3:** Một stage gốc completedFuture("ok"), còn thenApply xử lý nó ném IllegalArgumentException. Stage gốc thất bại không?

**Hướng giải:** Không. Stage phụ thuộc từ thenApply hoàn thành exceptional; stage gốc giữ kết quả "ok". Hook phải gắn đúng stage đại diện cho phạm vi kết quả cần đo.

**Tình huống 4:** Có một advice ghi log kết thúc rồi đóng tài nguyên trước khi task trong executor thực sự chạy. Nên thay đổi ở đâu?

**Hướng giải:** Nếu tài nguyên thuộc vòng đời task, dùng completion/task lifecycle hook để giải phóng sau khi thực thi, lỗi hay hủy theo contract. Đảm bảo cleanup idempotent và không đóng khi operation vẫn dùng tài nguyên.

**Tình huống 5:** Muốn tracing xuyên executor dùng chung nhưng đang ghi correlation ID bằng ThreadLocal. Điều gì còn thiếu?

**Hướng giải:** Capture context tại submit, truyền sang Runnable/Callable hoặc hook của mô hình, restore khi thực thi và cleanup trong finally, đồng thời bảo vệ trước việc worker thread tái sử dụng.

## Kết luận

Hãy chọn **cơ chế chuyên biệt của mô hình bất đồng bộ** khi mục tiêu AOP phụ thuộc vào kết quả hoặc vòng đời diễn ra **sau khi lời gọi phương thức đã trả về**, hoặc khi mô hình có ngữ nghĩa riêng về error, cancellation, subscription, callback và context. Một advice tổng quát vẫn hữu ích để ghi nhận mốc invocation/registration; completion hook, callback wrapper, task decorator hay stream lifecycle hook sẽ bổ sung góc nhìn mà call stack ban đầu không cung cấp. Chất lượng instrumentation được quyết định bởi việc gắn đúng **điểm phát tín hiệu thật**, hiểu rõ hợp đồng API và bảo toàn hành vi nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)
