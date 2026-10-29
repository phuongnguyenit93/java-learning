<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Concurrency, parallelism và asynchronous execution khác nhau thế nào?](#overview-001)
- [Process, platform thread, virtual thread và task](#overview-002)
- [CPU-bound và I/O-bound: chọn cách thực thi](#overview-003)
- [Latency, throughput, responsiveness và utilization](#overview-004)
- [Concurrency trong ứng dụng Java: use case và rủi ro](#overview-005)
- [Shared mutable state, coordination và thread confinement](#overview-006)
- [Safety, liveness, progress và starvation](#overview-007)
- [Race condition, deadlock, livelock: nhận diện ở mức tổng quan](#overview-008)
- [Concurrency overhead: context switch, contention, scheduling](#overview-009)
- [Khi nào nên dùng tuần tự thay vì đa luồng](#overview-010)

## <a id="overview-001">Concurrency, parallelism và asynchronous execution khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

# Concurrency, parallelism và asynchronous execution khác nhau thế nào trong Java?

Ba thuật ngữ này thường đi cùng nhau vì đều liên quan đến việc thực hiện nhiều công việc, nhưng chúng trả lời **ba câu hỏi khác nhau**:

- **Concurrency (xử lý đồng thời theo nghĩa tổ chức):** Hệ thống quản lý tiến độ của nhiều tác vụ trong cùng một khoảng thời gian như thế nào?
- **Parallelism (thực thi song song):** Có thực sự từ hai tác vụ đang chạy **cùng một thời điểm** trên các tài nguyên tính toán khác nhau hay không?
- **Asynchronous execution (thực thi bất đồng bộ):** Sau khi yêu cầu một công việc, phía gọi có thể **tiếp tục** mà chưa cần đợi kết quả ngay hay không?

Một ứng dụng có thể dùng cả ba, chỉ một hoặc hai trong số đó. **Asynchronous không đồng nghĩa non-blocking; concurrency không đồng nghĩa parallelism; nhiều thread không tự động bảo đảm tăng tốc.**

## 1. Concurrency: nhiều tác vụ cùng được quản lý tiến độ

**Concurrency** là khả năng cấu trúc chương trình sao cho nhiều tác vụ có tiến độ chồng lấn trong một khoảng thời gian. Tại một thời điểm quan sát ngắn, mỗi tác vụ có thể đang chạy, chờ CPU, chờ I/O, chờ khóa hoặc đã sẵn sàng chạy.

Trên hệ thống chỉ có một lõi CPU khả dụng, bộ lập lịch có thể lần lượt cho các thread chạy một lát rồi chuyển sang thread khác. Nhìn trên khoảng thời gian dài, cả hai công việc đều tiến triển; tại mỗi thời điểm thực thi CPU, chỉ một công việc chạy.

```text
Thời gian ------->

Một lõi CPU:
CPU:  [A chạy][B chạy][A chạy][B chạy][A chạy]
A:    [chạy]    [chạy]           [chạy]
B:             [chạy]    [chạy]

Hai tác vụ A và B có thời gian tồn tại chồng lấn,
nhưng không có hai đoạn "chạy" trên CPU diễn ra cùng lúc.
```

Sơ đồ là mô hình minh họa việc chia lượt chạy, không phải lịch chạy được Java bảo đảm. Trong Java, nhiều `Thread` hoặc tác vụ được gửi đến `ExecutorService` là những cách tổ chức công việc concurrent. Việc có thực sự chạy đồng thời trên nhiều lõi hay không phụ thuộc số thread hoạt động, số lõi khả dụng và quyết định của scheduler.

Điểm mạnh của concurrency không chỉ là tốc độ: nó giúp chương trình phản hồi các yêu cầu độc lập, xử lý công việc đang chờ và sử dụng tài nguyên linh hoạt. Đổi lại, các tác vụ có thể tranh chấp dữ liệu chung, khóa và tài nguyên; cần quản lý đồng bộ và vòng đời cẩn thận.

## 2. Parallelism: thực sự chạy cùng lúc

**Parallelism** xảy ra khi hai hoặc nhiều phần công việc **đang thực thi đồng thời về thời gian thực**, chẳng hạn trên hai lõi CPU. Đây là thuộc tính của quá trình chạy thực tế, không chỉ của cách viết code.

```text
Thời gian ------->

Hai lõi CPU:
Core 1: [A chạy][A chạy][A chạy]
Core 2: [B chạy][B chạy][B chạy]

Các đoạn chạy của A và B chồng nhau trên hai lõi.
```

Ví dụ: chia một phép tính lớn thành hai phần độc lập rồi giao cho hai worker trên hai lõi CPU. Nếu thực sự cùng chạy, bài toán có parallelism. Trong Java có thể biểu đạt ý định này bằng `ExecutorService` với nhiều worker, `ForkJoinPool`, parallel streams hoặc thuật toán chia để trị.

Tuy nhiên:

- Pool có hai worker **cho phép** hai tác vụ cùng chạy; nó không cam kết chúng luôn được đặt trên hai lõi hay luôn nhanh hơn.
- Tăng số thread không làm tăng số lõi CPU; quá nhiều thread có thể làm tăng chi phí chuyển ngữ cảnh và tranh chấp.
- Một thuật toán không tách được thành phần độc lập, hoặc thường xuyên phải khóa cùng dữ liệu, có thể không hưởng lợi từ parallelism.
- Trên máy nhiều lõi, concurrent tasks có thể cũng parallel. Vì thế parallel execution là một trường hợp thể hiện concurrency về mặt tiến độ, nhưng không phải mọi concurrent execution đều parallel.

## 3. Asynchronous execution: không buộc caller đứng chờ ngay

**Asynchronous execution** nói về **quan hệ thời gian giữa phía gọi và kết quả**. Caller khởi tạo công việc và có thể làm việc khác trước khi nhận kết quả thông qua `Future`, `CompletableFuture`, callback hoặc cơ chế thông báo hoàn tất.

Đối lập về luồng điều khiển:

```text
Synchronous:
Caller: [gọi A........chờ A xong][xử lý kết quả][việc khác]

Asynchronous:
Caller: [khởi động A][việc khác................][dùng kết quả]
Worker:       [thực hiện A......................]
```

Với lời gọi đồng bộ thông thường, phương thức chỉ trả về khi phần việc của nó hoàn thành hoặc thất bại. Với API bất đồng bộ, phương thức thường trả về sớm một đối tượng đại diện cho công việc; kết quả đến sau. Caller vẫn **có thể tự chọn chờ**, chẳng hạn gọi `Future.get()` hoặc `CompletableFuture.join()`. Tại đúng điểm chờ đó, thread gọi có thể bị block.

Bất đồng bộ không chỉ có một mô hình thực thi:

- **Async bằng thread:** Một worker thread làm công việc thay caller. Nếu worker gọi I/O blocking, chính worker vẫn bị block.
- **Async event-driven/non-blocking I/O:** Một event loop có thể đăng ký nhiều hoạt động I/O và xử lý khi chúng sẵn sàng, không cần giữ riêng một thread chờ mỗi yêu cầu. Java NIO `Selector` là một API nền tảng cho hướng này.
- **Async completion chain:** Phần xử lý tiếp theo được mô tả bằng `thenApply`, `thenCompose`, `thenCombine`… để kích hoạt khi điều kiện hoàn tất phù hợp.

Vì vậy, **async không tự chứng minh non-blocking I/O**. Ví dụ `CompletableFuture.supplyAsync(() -> Thread.sleep(...), executor)` trả về cho caller sớm, nhưng worker bị block trong `sleep`. Ngược lại, một thao tác non-blocking cũng không mặc nhiên tạo parallelism trên CPU.

## 4. So sánh theo tiêu chí

| Tiêu chí | Concurrency | Parallelism | Asynchronous execution |
| --- | --- | --- | --- |
| Câu hỏi chính | Nhiều tác vụ có thể cùng tiến triển/được quản lý như thế nào? | Nhiều tác vụ có thực sự chạy cùng lúc không? | Caller có cần đợi kết quả ngay sau khi gọi không? |
| Đối tượng quan sát | Vòng đời và phối hợp giữa các tác vụ | Sự chồng lấn **thực thi** theo thời gian thực | Giao diện gọi, hoàn tất và nhận kết quả |
| Cần nhiều lõi CPU? | Không | Cần tài nguyên thực thi đồng thời để có CPU parallelism thực sự | Không |
| Cần nhiều thread? | Không nhất thiết (có thể dùng event loop) | CPU parallelism thường cần nhiều worker/lõi | Không nhất thiết |
| Mục tiêu hay gặp | Khả năng đáp ứng, tổ chức công việc độc lập | Tăng thông lượng tính toán khi chia việc hiệu quả | Tránh buộc caller chờ, ghép các tác vụ phụ thuộc |
| Rủi ro cần chú ý | Race condition, deadlock, starvation | Overhead, tranh chấp, tải CPU | Callback/chain lỗi, mất kiểm soát vòng đời, vô tình block |

**Ba khái niệm nằm trên các trục khác nhau.** Ví dụ một chương trình nhận nhiều kết nối bằng event loop có concurrency và thao tác I/O bất đồng bộ nhưng vẫn chỉ xử lý callback bằng một thread tại từng thời điểm. Một chương trình dùng hai worker tính toán đồng thời có concurrency và parallelism. Chương trình trả về `CompletableFuture` có tính async ở phía caller, nhưng nếu chỉ dùng một worker thì chưa thể kết luận có CPU parallelism.

## 5. Ví dụ Java 17+: ba khía cạnh xuất hiện trong một chương trình

Chương trình sau gửi **hai tác vụ độc lập** vào pool hai worker, cho thread `main` tiếp tục xử lý trước khi dùng kết quả. `Thread.sleep` chỉ **mô phỏng công việc chờ blocking** để dễ quan sát cấu trúc; nó không đại diện cho non-blocking I/O.

Lưu thành `ExecutionModesDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutionModesDemo {
    private static String load(String id) {
        try {
            Thread.sleep(150); // Giả lập thao tác chờ có blocking
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task interrupted", e);
        }
        return id;
    }

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<String> first =
                    CompletableFuture.supplyAsync(() -> load("A"), pool);
            CompletableFuture<String> second =
                    CompletableFuture.supplyAsync(() -> load("B"), pool);

            System.out.println("Da gui hai tac vu");

            CompletableFuture<String> combined =
                    first.thenCombine(second, (a, b) -> a + " + " + b);

            System.out.println("Main tiep tuc viec khac");
            System.out.println(combined.join());
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ExecutionModesDemo.java
java ExecutionModesDemo
```

**Stdout chính xác** (các worker không in ra màn hình, nên thứ tự ba dòng ổn định):

```text
Da gui hai tac vu
Main tiep tuc viec khac
A + B
```

Phân tích từng phần:

1. **Concurrency:** `first` và `second` đại diện cho hai công việc độc lập có thể cùng ở trạng thái chưa hoàn thành; `main` cũng tiếp tục chạy.
2. **Asynchronous execution:** `supplyAsync` lên lịch tác vụ và trả về `CompletableFuture`. Thread `main` in thông báo tiếp theo mà không đợi hai tác vụ hoàn tất ngay tại lời gọi. `thenCombine` biểu diễn một bước xử lý khi **cả hai** kết quả sẵn sàng.
3. **Parallelism có thể xảy ra:** Pool có hai worker nên hai tác vụ có cơ hội thực thi trùng thời điểm trên các lõi khác nhau. Code không bảo đảm điều này trên mọi môi trường; thời lượng đo được cũng không phải bằng chứng tuyệt đối.
4. **Blocking vẫn có mặt:** Mỗi worker bị block trong `Thread.sleep(150)`. Sau khi in dòng thứ hai, `main` gọi `combined.join()`; nếu kết quả chưa xong, `main` cũng bị block đến lúc hoàn tất.
5. **Không tạo thread vô hạn:** `Executors.newFixedThreadPool(2)` giới hạn hai worker và xếp hàng tác vụ dư. `shutdown()` yêu cầu executor ngừng nhận công việc mới và kết thúc sau các công việc đã gửi; trong ví dụ này `join()` đã đợi cả hai hoàn tất thành công trước đó.

Đừng lấy một lần chạy đo thời gian khoảng 150 ms để kết luận chắc chắn "có parallelism": thời gian khởi động, scheduling, chờ và số lõi khả dụng đều ảnh hưởng kết quả.

## 6. CompletableFuture: async API không đồng nghĩa một thread mới cho mỗi bước

Khi đã có `CompletableFuture<T>`, Java cung cấp các kiểu ghép bước khác nhau:

| API | Ý nghĩa | Điều cần nhớ |
| --- | --- | --- |
| `supplyAsync(supplier, executor)` | Lên lịch tính ra giá trị trên executor được chỉ định | Supplier vẫn có thể blocking |
| `runAsync(runnable, executor)` | Lên lịch tác vụ không tạo giá trị | Kết quả kiểu `CompletableFuture<Void>` |
| `thenApply(fn)` | Biến đổi kết quả khi stage trước thành công | Không tự bảo đảm tạo task/thread riêng; hàm có thể chạy trên thread hoàn tất stage |
| `thenApplyAsync(fn, executor)` | Lên lịch bước biến đổi bất đồng bộ qua executor | Thời điểm chạy phụ thuộc executor |
| `thenCompose(fn)` | Nối một tác vụ async phụ thuộc kết quả trước đó | Tránh cấu trúc lồng `CompletableFuture<CompletableFuture<T>>` |
| `thenCombine(other, fn)` | Kết hợp kết quả hai stage khi cả hai hoàn tất | Thích hợp với các công việc đầu vào độc lập |
| `allOf(futures...)` | Stage hoàn tất khi tất cả stage đã cho hoàn tất | Không tự tạo danh sách kết quả; phải lấy từ từng future |
| `exceptionally(fn)`, `handle(fn)` | Xử lý hoặc biến đổi lỗi | Cần xác định cách phục hồi và giữ ngữ cảnh lỗi |
| `join()` / `get()` | Đợi và lấy giá trị cuối | Đều có thể block; `join()` bọc lỗi bằng `CompletionException`, `get()` dùng `ExecutionException` và có thể ném `InterruptedException` |

Một điểm thường bị hiểu sai: tên `thenApply` **không có hậu tố `Async`** không có nghĩa là nó luôn chạy trên caller thread; ngược lại, tên `thenApplyAsync` cũng không có nghĩa Java tạo một thread hoàn toàn mới. Với các phương thức `Async` không truyền executor, chúng thường sử dụng `ForkJoinPool.commonPool()` theo chính sách thực thi mặc định của `CompletableFuture`; khi cần kiểm soát tài nguyên, hãy cung cấp executor phù hợp.

Nếu B cần kết quả A, chuỗi `A.thenCompose(a -> startB(a))` thể hiện **phụ thuộc tuần tự về dữ liệu**, mặc dù caller vẫn có thể tương tác bất đồng bộ với cả chuỗi. Nếu A và B độc lập, tạo cả hai trước rồi `thenCombine` để tận dụng khả năng chạy chồng lấn; đừng khởi động B chỉ sau `A.join()` nếu không có quan hệ phụ thuộc.

`Future.get()` chỉ đơn giản lấy kết quả, có thể phải đợi; `CompletableFuture` cung cấp thêm cơ chế kết hợp và xử lý hoàn tất mà không buộc caller phải `get()` ở mỗi bước. Tuy nhiên, một hệ thống vẫn cần điểm đồng bộ ở ranh giới sử dụng kết quả, và cần chính sách timeout, lỗi, hủy tác vụ phù hợp.

## 7. Ví dụ riêng về ý định CPU parallelism

Với phép tính có thể chia thành các phần độc lập, parallel stream là một API Java diễn đạt **ý định** phân chia tính toán. Để ví dụ có kết quả tái lập được, dùng phép cộng số nguyên:

Lưu thành `ParallelSumDemo.java`:

```java
import java.util.stream.LongStream;

public class ParallelSumDemo {
    public static void main(String[] args) {
        long total = LongStream.rangeClosed(1, 1_000_000)
                .parallel()
                .sum();

        System.out.println(total);
    }
}
```

```bash
javac ParallelSumDemo.java
java ParallelSumDemo
```

Stdout:

```text
500000500000
```

Kết quả toán học là `1_000_000 × 1_000_001 / 2 = 500_000_500_000`. `parallel()` cho phép stream chia công việc để thực hiện trên nhiều worker, thường sử dụng common fork/join pool. Điều đó không bảo đảm nhanh hơn bản `.sequential()`. Với phép tính đơn giản hoặc dữ liệu nhỏ, overhead chia/ghép công việc có thể chiếm ưu thế. Mã này minh họa cách biểu đạt parallel computation; muốn chứng minh hiệu năng cần benchmark đúng phương pháp, trên dữ liệu thực và môi trường xác định.

Cũng cần phân biệt: phép cộng `sum()` có quy tắc tổng hợp an toàn; không nên dùng `parallelStream().forEach(...)` để sửa một `ArrayList` chung hoặc trông đợi thứ tự phần tử, vì dễ tạo data race hoặc kết quả không ổn định.

## 8. Các tổ hợp thường gặp trong ứng dụng Java

| Tình huống | Concurrency? | Parallelism? | Async đối với caller? |
| --- | --- | --- | --- |
| Hai `Thread` được scheduler luân phiên trên một lõi | Có | Không tại mức CPU đang xét | Tùy cách caller khởi động/chờ |
| Hai tác vụ tính toán đang cùng chạy trên hai lõi | Có | Có | Có thể có hoặc không tùy API |
| Caller dùng `supplyAsync`, nhưng executor chỉ có một worker và task blocking | Có thể tổ chức nhiều task cùng tồn tại | Không có hai task chạy song song trong pool đó | Có, đến khi caller gọi phương thức đợi |
| Một event loop Java NIO xử lý các kết nối sẵn sàng | Có | Không nhất thiết | Có thể có, phụ thuộc API/cách thông báo |
| Caller gọi phương thức đồng bộ, phương thức bên trong tự chia việc trên nhiều CPU rồi đợi xong mới trả về | Có (bên trong) | Có thể có | Không tại giao diện caller |
| Một lời gọi HTTP đồng bộ đơn lẻ chạy trong một thread | Không nhất thiết | Không | Không |

Cột parallelism dựa vào **hành vi thực thi thực tế**, không thể suy ra chắc chắn chỉ từ việc nhìn thấy `parallel()`, số thread hoặc một đối tượng `Future`.

## 9. Chọn cách tiếp cận theo loại công việc

**CPU-bound** (tính toán, phân tích dữ liệu, mã hóa khối dữ liệu): nếu công việc tách được thành các phần đủ lớn và độc lập, hãy cân nhắc `ForkJoinPool`, executor có mức song song phù hợp hoặc parallel streams. Đo hiệu năng thực tế. Giới hạn số task chạy thật sự để tránh nhiều worker hơn khả năng CPU xử lý hiệu quả.

**I/O-bound** (đọc/ghi, kết nối mạng, gọi dịch vụ): concurrency hữu ích vì các tác vụ có thời gian chờ. Có thể dùng pool thực hiện I/O blocking hoặc API I/O không blocking nếu thư viện và kiến trúc phù hợp. Async giúp caller tiếp tục xử lý và kết hợp kết quả; nó không biến một lời gọi blocking thành non-blocking ở worker.

**Cần giao diện phản hồi nhanh:** dùng async để không giữ caller tại điểm chờ ngay lập tức, nhưng phải thiết kế nơi nhận kết quả, kiểm soát số tác vụ đang mở và cách báo lỗi. Nếu chỉ chuyển tác vụ sang worker rồi gọi `join()` ngay lập tức, lợi ích bất đồng bộ đối với caller gần như biến mất.

**Cần dữ liệu theo thứ tự:** xác định quan hệ phụ thuộc. Những bước phụ thuộc phải đợi dữ liệu trước; các bước độc lập có thể gửi đồng thời. Parallelize mù quáng có thể gây sai thứ tự hoặc lỗi dữ liệu.

## 10. Các lỗi nhận thức và kỹ thuật thường gặp

1. **"Concurrent nghĩa là luôn chạy cùng lúc."** Sai: trên một lõi CPU, scheduler có thể cho các tác vụ tiến triển luân phiên. Parallelism mới nói đến khoảng thời gian thực thi thật sự trùng nhau.
2. **"Async nghĩa là non-blocking."** Sai: caller có thể không chờ nhưng worker vẫn block khi đọc file, `sleep` hoặc gọi I/O đồng bộ.
3. **"Dùng `CompletableFuture` thì chắc chắn song song."** Sai: executor một worker có thể thực thi lần lượt; các stage phụ thuộc nhau cũng không chạy đồng thời chỉ vì tên API.
4. **"Nhiều thread luôn nhanh hơn."** Sai: CPU contention, context switching, lock contention, memory bandwidth và overhead chia/ghép có thể làm chậm hơn.
5. **"Gọi `join()` ngay sau mỗi `supplyAsync` là tận dụng tốt concurrency."** Thường sai nếu hai tác vụ độc lập: cách này có thể tuần tự hóa thời điểm bắt đầu công việc. Hãy khởi tạo các tác vụ độc lập trước, rồi mới ghép/đợi kết quả.
6. **"Tác vụ async tự xử lý lỗi."** Sai: lỗi có thể làm future hoàn tất bất thường; phải quan sát thất bại qua `join/get` hoặc stage xử lý lỗi. Không được bỏ qua future quan trọng.
7. **"`shutdown()` là hủy tức thì mọi việc."** Sai: `shutdown()` dừng nhận task mới và cho các task đã gửi hoàn tất; `shutdownNow()` cố gắng ngắt/hủy theo khả năng nhưng cũng không bảo đảm task dừng lập tức. Tác vụ blocking cần thiết kế phản ứng interruption hợp lý.
8. **"Thread-safe tự động khi dùng async."** Sai: `count++` trên biến chia sẻ không trở nên atomic chỉ nhờ chạy trong task. Cần thiết kế immutable data, thread confinement, `AtomicInteger`, lock hoặc cấu trúc concurrent phù hợp theo tình huống.
9. **"Tất cả continuation luôn chạy trên cùng thread."** Sai: một continuation không có hậu tố `Async` có thể chạy trên thread hoàn tất stage trước, hoặc thread gọi nếu stage đã hoàn tất; đừng dựa vào thread identity khi thiết kế logic.
10. **"Cancellation luôn ngắt được công việc đang chạy."** Không có cam kết chung như vậy với `CompletableFuture.cancel(...)`; riêng cơ chế này không dùng interrupt để điều khiển quá trình xử lý. Hãy thiết kế timeout, cờ hủy và đóng tài nguyên theo API cụ thể.

Với Java 21 trở lên, **virtual threads** giúp mô hình thread-per-task thuận tiện hơn cho nhiều tác vụ blocking I/O, nhưng **không tạo thêm lõi CPU** và không làm một phép tính CPU-bound tự nhiên nhanh hơn. Chúng cũng không thay đổi định nghĩa của ba khái niệm trên. Ví dụ chính của bài vẫn dùng API có sẵn trên Java 17.

## 11. Tự kiểm tra cách hiểu

**Tình huống A:** Một thread xử lý nhiều socket bằng cách nhận thông báo I/O sẵn sàng, rồi lần lượt xử lý sự kiện. Có concurrency giữa các kết nối và có thể có asynchronous/non-blocking I/O; không có bằng chứng xử lý callback song song trên CPU.

**Tình huống B:** Hai phép tính độc lập được giao cho hai worker và thật sự chạy cùng lúc trên hai lõi. Đây là concurrency **và** parallelism; giao diện gọi có async hay không phụ thuộc việc caller có phải đợi ngay tại lệnh gọi.

**Tình huống C:** Hàm trả về `CompletableFuture` ngay, nhưng worker đọc dữ liệu bằng phương thức blocking, còn caller tiếp tục xử lý. Đây là async đối với caller; worker vẫn blocking. Chưa đủ thông tin để nói có CPU parallelism.

**Tình huống D:** Caller gọi một hàm đồng bộ tính tổng lớn bằng nhiều worker; hàm đợi tất cả xong mới trả kết quả. Phần tính có thể concurrent và parallel, nhưng **lời gọi bên ngoài vẫn synchronous**.

Khi gặp một API hoặc thiết kế mới, hãy lần lượt hỏi:

1. **Có bao nhiêu tác vụ đang cùng tồn tại và tiến triển?** → xét concurrency.
2. **Có những đoạn tính toán thực sự đang chạy trùng thời gian trên các tài nguyên khác nhau?** → xét parallelism.
3. **Caller có thể làm việc khác trước khi cần lấy kết quả, hay bị buộc chờ tại điểm gọi?** → xét asynchronous execution.

Ba câu hỏi này giúp chọn đúng cơ chế của Java, phân tích bottleneck rõ hơn và tránh suy luận sai từ tên API hoặc số lượng thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-002">Process, platform thread, virtual thread và task</a>

<details>
<summary>Xem chi tiết</summary>

# Process, platform thread, virtual thread và task trong Java

## 1. Bức tranh tổng thể: chương trình chạy bằng những gì?

Khi đọc một ứng dụng Java có nhiều công việc diễn ra đồng thời, cần tách bốn khái niệm:

- **Process (tiến trình)**: một lần thực thi chương trình do hệ điều hành quản lý, có không gian địa chỉ và tài nguyên riêng theo cơ chế cách ly của hệ điều hành. Một lần chạy lệnh `java Main` thông thường tạo một tiến trình chứa JVM.
- **Platform thread (luồng nền tảng)**: một `java.lang.Thread` được JVM gắn với luồng hệ điều hành (OS thread). Hệ điều hành lập lịch cho luồng đó chạy trên các lõi CPU.
- **Virtual thread (luồng ảo)**: `java.lang.Thread` nhẹ, được JVM quản lý và lập lịch lên các platform thread dùng làm **carrier** (luồng mang). Virtual thread là tính năng chính thức từ Java 21; API `Thread.ofVirtual()` và `Executors.newVirtualThreadPerTaskExecutor()` đã ổn định.
- **Task (tác vụ)**: đơn vị công việc cần thực hiện, chẳng hạn “đọc dữ liệu”, “xử lý một yêu cầu” hoặc “tính kết quả”. Task là **công việc**, còn thread là **ngữ cảnh thực thi công việc**. Một `Runnable` hoặc `Callable` mô tả task nhưng không tự chạy khi chỉ mới tạo đối tượng.

Quan hệ thường gặp:

```text
Hệ điều hành
  ├── Process A: JVM
  │     ├── platform thread "main" ──────────> chạy mã Java
  │     ├── platform thread trong thread pool -> chạy lần lượt nhiều task
  │     └── carrier platform threads
  │           ├── virtual thread A ──────────> task A (khi được lập lịch)
  │           ├── virtual thread B ──────────> task B (khi được lập lịch)
  │           └── virtual thread C ──────────> task C (khi được lập lịch)
  └── Process B: chương trình khác
```

Sơ đồ biểu diễn quan hệ logic, không hàm ý mỗi virtual thread có một carrier riêng cố định. Một carrier có thể chạy nhiều virtual thread **lần lượt**; khi đang chạy, một virtual thread được gắn (mounted) lên một carrier.

### So sánh nhanh

| Tiêu chí | Process | Platform thread | Virtual thread | Task |
| --- | --- | --- | --- | --- |
| Bản chất | Đơn vị tiến trình của hệ điều hành | Luồng Java gắn với OS thread | Luồng Java do JVM lập lịch trên carrier | Một công việc hoặc phép tính |
| Ai lập lịch thực thi? | Hệ điều hành quản lý tiến trình/các OS thread | Hệ điều hành lập lịch OS thread | JVM lập lịch virtual thread lên carrier; OS lập lịch carrier | Do người gọi, executor hoặc cơ chế điều phối lựa chọn |
| Bộ nhớ/trạng thái | Không gian địa chỉ và tài nguyên tiến trình, thường được cách ly khỏi tiến trình khác | Stack và trạng thái luồng; chia sẻ heap của process | Stack/trạng thái logic của luồng; chia sẻ heap của process | Dữ liệu đầu vào, trạng thái và kết quả tùy thiết kế |
| Chi phí khi tạo nhiều | Tương đối cao | Stack và tài nguyên OS thread đáng kể | Thường thấp hơn nhiều cho số lượng tác vụ chờ I/O lớn | Phụ thuộc bản thân công việc và dữ liệu |
| Đối tượng/API Java tiêu biểu | `Process`, `ProcessBuilder` để quản lý tiến trình con | `Thread.ofPlatform()` | `Thread.ofVirtual()` | `Runnable`, `Callable`, `Future` |

**Ghi nhớ:** một JVM thường có nhiều thread; nhiều thread trong cùng JVM có thể truy cập chung đối tượng trên heap. Hai process thông thường không thể truy cập trực tiếp cùng heap Java; muốn trao đổi cần cơ chế như socket, pipe, tệp hoặc bộ nhớ chia sẻ được cấu hình riêng. Cách ly process không bảo đảm task bên trong process tự an toàn trước tranh chấp dữ liệu.

## 2. Concurrency khác parallelism

**Concurrency (đồng thời)** nói rằng nhiều công việc cùng tiến triển trong một khoảng thời gian. Chúng có thể thay phiên nhau trên một lõi CPU. **Parallelism (song song)** nói rằng hai hay nhiều công việc thực sự chạy cùng một thời điểm trên các lõi hoặc đơn vị xử lý khác nhau.

Ví dụ, trong khi task A chờ phản hồi mạng, CPU có thể xử lý task B. Đây là concurrency ngay cả khi chỉ có một lõi xử lý mã Java tại một thời điểm. Hai tác vụ tính toán nặng chạy đồng thời trên hai lõi là parallelism.

Thread loại nào cũng cần CPU để chạy mã. Virtual thread **không tạo thêm lõi CPU** và không biến thuật toán tính toán nặng thành nhanh hơn chỉ bằng cách tăng số lượng thread. Điểm mạnh chính của virtual thread là giúp duy trì rất nhiều tác vụ có thời gian chờ I/O theo phong cách lập trình tuần tự mà không cần dành riêng một OS thread cho mỗi tác vụ chờ.

## 3. Process trong ứng dụng Java

Khi chạy `java MyApp`, hệ điều hành khởi tạo tiến trình JVM; bên trong JVM có `main` thread và các thread phục vụ runtime. Mỗi process có PID, tập tài nguyên (ví dụ các file descriptor, socket) và vòng đời riêng. JVM có thể khởi tạo process con bằng `ProcessBuilder`, nhưng **process con khác với một thread mới**: nó có không gian địa chỉ, cơ chế dừng/chờ và giao tiếp riêng.

Ví dụ thực tế:

- Hai lệnh `java MyApp` chạy riêng là hai tiến trình JVM, dù dùng cùng tệp `.class`.
- Trong một JVM, một task đọc socket và task khác tính toán có thể cùng dùng một đối tượng cache; đồng bộ truy cập cache là vấn đề của thread.
- Để một JVM truyền dữ liệu cho JVM khác, cần một kênh IPC hoặc giao thức trao đổi; đặt biến `static` không khiến hai JVM chia sẻ biến.

Process thích hợp khi cần ranh giới cách ly, quyền và vòng đời độc lập. Thread thích hợp khi nhiều công việc thuộc **cùng ứng dụng** cần phối hợp và dùng chung dữ liệu. Đây là hai quyết định kiến trúc ở hai tầng khác nhau.

## 4. Platform thread: ánh xạ tới OS thread

`Thread.ofPlatform().start(runnable)` tạo và khởi chạy một platform thread. Một platform thread gắn với OS thread trong suốt vòng đời; hệ điều hành chịu trách nhiệm phân phối thời gian CPU. Mỗi platform thread cần tài nguyên hệ điều hành và stack, vì vậy tạo hàng chục nghìn platform thread đang tồn tại thường tốn kém hơn nhiều so với tạo cùng số lượng virtual thread.

**Lưu ý về trạng thái:** `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED` là các giá trị `Thread.State` quan sát được qua API, không phải cam kết về chính xác lúc nào CPU đang chạy thread. `RUNNABLE` có thể bao gồm thời gian sẵn sàng chạy. Thread bị chờ khóa monitor, `join` hoặc `sleep` cũng không làm cho task khác tự trở nên an toàn.

Đối với tập công việc nhỏ, tồn tại lâu dài, hoặc tác vụ dùng native API có yêu cầu thread cụ thể, platform thread có thể phù hợp. Khi rất nhiều request đồng thời đều chờ mạng, dùng một platform thread cho mỗi request dễ chạm giới hạn tài nguyên OS thread; vì thế các ứng dụng truyền thống thường dùng **pool** platform thread với số luồng hữu hạn và hàng đợi task.

## 5. Virtual thread: thread Java nhẹ được JVM lập lịch

Virtual thread cũng là `Thread`: có danh tính, có thể gọi `start`, `join`, `interrupt`, quan sát bằng `Thread.currentThread()` và `isVirtual()`. Khác biệt là virtual thread **không sở hữu riêng một OS thread trong toàn bộ vòng đời**.

Trong Java 21, JVM gắn virtual thread lên một carrier platform thread khi nó sẵn sàng chạy. Với nhiều thao tác blocking I/O được JDK hỗ trợ, JVM có thể tháo (unmount) virtual thread khỏi carrier trong lúc chờ, cho carrier chạy virtual thread khác. Khi kết quả I/O sẵn sàng, virtual thread được lên lịch tiếp tục. Cơ chế này giúp mã trông như tuần tự, chẳng hạn `read()` rồi xử lý kết quả, vẫn có thể phục vụ nhiều kết nối đang đợi.

Không phải mọi lời gọi chặn đều giải phóng carrier: có khác biệt giữa phiên bản JDK, thư viện native và loại thao tác. Trong **Java 21**, virtual thread có thể bị **pinning** khi thực hiện thao tác chặn trong vùng `synchronized` hoặc khi chạy mã native/foreign; carrier lúc đó có thể bị giữ lại. Java 24 cải thiện trường hợp monitor pinning theo JEP 491, nhưng vẫn cần xét phiên bản JDK và thư viện cụ thể. Pinning không có nghĩa chương trình luôn sai; nó có thể làm giảm khả năng mở rộng khi xảy ra nhiều thao tác chờ.

Virtual thread đặc biệt thích hợp với tác vụ **I/O-bound** như chờ dữ liệu từ mạng, tệp hoặc hệ thống bên ngoài. Tác vụ **CPU-bound** vẫn cần CPU và nên được giới hạn song song theo tài nguyên xử lý thực có.

### Ví dụ 1 — hai loại thread, cùng thực hiện một kiểu công việc

Tạo tệp `ThreadKindsDemo.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadKindsDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger platformResult = new AtomicInteger();
        AtomicInteger virtualResult = new AtomicInteger();

        Thread platform = Thread.ofPlatform()
                .name("platform-demo")
                .start(() -> platformResult.set(10));

        Thread virtual = Thread.ofVirtual()
                .name("virtual-demo")
                .start(() -> virtualResult.set(20));

        platform.join();
        virtual.join();

        System.out.println("platform: value=" + platformResult.get()
                + ", virtual=" + platform.isVirtual());
        System.out.println("virtual: value=" + virtualResult.get()
                + ", virtual=" + virtual.isVirtual());
    }
}
```

Biên dịch và chạy bằng **JDK 21 trở lên**:

```bash
javac ThreadKindsDemo.java
java ThreadKindsDemo
```

Kết quả:

```text
platform: value=10, virtual=false
virtual: value=20, virtual=true
```

Hai thread được tạo và có thể chạy theo thứ tự bất kỳ. Tuy nhiên, `join()` buộc `main` chờ cả hai hoàn tất trước khi in, nên thứ tự hai dòng kết quả như trên là xác định. `AtomicInteger` cung cấp thao tác cập nhật/đọc an toàn; đồng thời, khi `join` trả về bình thường, các hành động của thread đã kết thúc có quan hệ *happens-before* với các hành động sau `join` của thread đợi. `isVirtual()` phân biệt loại luồng, không suy ra số lượng lõi hoặc mức độ song song thực tế.

**Quan trọng về vòng đời:** virtual thread trong Java 21 là daemon thread và tự nó không giữ JVM sống. Nếu `main` kết thúc mà không có thread không-daemon khác, JVM có thể dừng trước khi virtual thread làm xong. Dùng `join`, `Future.get`, đóng executor đúng cách hoặc cơ chế quản lý vòng đời tương ứng để chờ công việc cần thiết.

## 6. Task là công việc; thread là nơi chạy công việc

Xét hai cách viết:

- `Runnable` mô tả công việc không trả kết quả: `() -> saveRecord()`.
- `Callable<T>` mô tả công việc có kết quả `T` hoặc có thể ném exception: `() -> calculateValue()`.

Việc tạo `Runnable` hay `Callable` **không khởi tạo thread và không tự thực thi**. Người gọi có thể chạy đồng bộ bằng `run()`, tạo một thread để chạy, hoặc gửi vào `ExecutorService`. `Thread.start()` tạo hoạt động đồng thời, còn gọi trực tiếp `runnable.run()` chỉ là lời gọi phương thức thông thường trên thread hiện tại.

`ExecutorService` nhận task, quyết định cách thực thi dựa vào implementation, và thường trả `Future` để quan sát kết quả. Một **pool platform thread** có thể tái sử dụng một số thread cho nhiều task lần lượt. Ngược lại, `newVirtualThreadPerTaskExecutor()` tạo **một virtual thread mới cho mỗi task**, không phải một pool virtual thread dùng lại. Tên “per task” mô tả chính sách thực thi, không có nghĩa task và thread là cùng một khái niệm.

### Ví dụ 2 — thực thi task bằng virtual-thread-per-task executor

Tạo tệp `TaskExecutorDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TaskExecutorDemo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {

            Future<Integer> calculation = executor.submit(() -> {
                Thread.sleep(50);
                return 6 * 7;
            });
            Future<Boolean> typeCheck = executor.submit(
                    () -> Thread.currentThread().isVirtual());

            System.out.println("result=" + calculation.get());
            System.out.println("runsOnVirtualThread=" + typeCheck.get());
        }
    }
}
```

Chạy với JDK 21 trở lên:

```bash
javac TaskExecutorDemo.java
java TaskExecutorDemo
```

Kết quả:

```text
result=42
runsOnVirtualThread=true
```

Hai `submit` gửi hai task độc lập; mỗi task chạy trong virtual thread riêng. `Thread.sleep(50)` mô phỏng thời gian chờ, không chứng minh tăng tốc hiệu năng và không bảo đảm thứ tự các task thực sự bắt đầu. `Future.get()` chờ kết quả (hoặc ném exception nếu task thất bại). `try`-with-resources đóng executor theo vòng đời của `ExecutorService`, chờ các task hoàn tất. Chương trình in theo thứ tự cố định vì gọi `get` rồi in theo thứ tự xác định.

**Không suy rộng:** `CompletableFuture.supplyAsync(...)` không có đối số executor mặc định dùng `ForkJoinPool.commonPool()` (theo cấu hình thông thường), **không mặc nhiên dùng virtual thread**. Muốn task chạy trên virtual thread bằng API đó, phải truyền executor phù hợp.

## 7. Những gì xảy ra khi task chờ, chạy nặng hoặc bị hủy

### 7.1. Blocking I/O và carrier

Một tác vụ mạng kiểu tuần tự có thể thực hiện: gửi yêu cầu → chờ phản hồi → phân tích kết quả. Với platform thread, trong thời gian thao tác chặn, OS thread đó thường đang đợi và chưa thể thực thi task khác trong cùng thread. Với virtual thread trên JDK và thao tác I/O hỗ trợ unmount, JVM có thể trả carrier về bộ lập lịch trong lúc virtual thread chờ.

Chính khả năng tận dụng carrier trong thời gian chờ tạo lợi ích mở rộng số lượng tác vụ I/O đồng thời. Nếu bản thân tác vụ chạy vòng lặp CPU liên tục thì virtual thread vẫn chiếm carrier khi thực thi; nó không tự “nhẹ CPU” hơn platform thread.

### 7.2. Chờ hoàn thành khác với hủy công việc

- `thread.join()` chờ thread kết thúc; `future.get()` chờ task hoàn thành hoặc thất bại.
- `future.get(timeout, unit)` chỉ đặt giới hạn thời gian **chờ kết quả** cho bên gọi. Khi bị `TimeoutException`, task không tự bị hủy.
- `future.cancel(true)` yêu cầu hủy task và thường phát tín hiệu ngắt tới thread đang chạy, nhưng **không bảo đảm task dừng tức thì**.
- `thread.interrupt()` là tín hiệu hợp tác. Mã đang chờ bằng nhiều API như `sleep`/`join` có thể nhận `InterruptedException`; mã chạy lâu cần chủ động kiểm tra trạng thái ngắt và thoát an toàn.

Khi bắt `InterruptedException` mà không thể ném tiếp, cách xử lý thường là khôi phục cờ ngắt qua `Thread.currentThread().interrupt()` rồi kết thúc phần việc hoặc chuyển tín hiệu lên tầng điều phối. Đừng nuốt exception rồi tiếp tục như thể không có yêu cầu dừng.

### 7.3. Vòng đời và tài nguyên ngoài thread

Mỗi task có thể giữ socket, transaction, kết nối, file descriptor hoặc bộ đệm. Dù virtual thread rẻ hơn, **các tài nguyên này không tự rẻ hơn**. Nếu tạo 100.000 virtual thread cùng gọi một cơ sở dữ liệu chỉ hỗ trợ 50 kết nối, nút thắt vẫn nằm ở giới hạn kết nối, khả năng xử lý của hệ thống đích và chính sách timeout.

Giới hạn truy cập tài nguyên bằng connection pool, `Semaphore` hoặc cơ chế backpressure phù hợp. Không “pool virtual thread” chỉ để hạn chế tải: giới hạn ở đúng tài nguyên đang khan hiếm. Luôn quy định thời gian chờ, hủy, retry có kiểm soát và đóng tài nguyên trong `try`-with-resources khi có thể.

## 8. Dùng chung dữ liệu: virtual thread không tự giải quyết race condition

Các thread trong một JVM thường thấy cùng heap. Hai task đồng thời sửa một biến chung mà không có quan hệ đồng bộ hợp lệ có thể gây **data race**, lost update hoặc đọc giá trị không được bảo đảm nhìn thấy theo Java Memory Model. Điều đó xảy ra với cả platform thread lẫn virtual thread.

Ví dụ `counter++` gồm nhiều bước đọc, cộng, ghi; hai thread cùng tăng có thể mất một lần cập nhật. Thay bằng `AtomicInteger.incrementAndGet()` khi chỉ cần bộ đếm đơn giản, hoặc dùng khóa, collection concurrent và thiết kế bất biến/chuyển thông điệp tùy yêu cầu.

Các quan hệ *happens-before* hữu ích gồm việc hoàn tất `Thread.start()` trước các hành động trong thread mới, hành động của thread trước khi một `join` tương ứng trả về, và các bảo đảm của `synchronized`, `volatile`, atomic classes, `Future` theo hợp đồng API. Đừng đánh đồng “đã nhìn thấy giá trị đúng ở một lần chạy” với “chương trình luôn đúng”.

**ThreadLocal:** dữ liệu lưu theo từng thread, kể cả virtual thread. Tạo số lượng virtual thread rất lớn rồi đặt nhiều đối tượng nặng trong `ThreadLocal` vẫn có thể tốn nhiều bộ nhớ và kéo dài vòng đời tài nguyên. Không giả định trạng thái `ThreadLocal` từ task trước sẽ tồn tại ở task tiếp theo trong `newVirtualThreadPerTaskExecutor`: mỗi task nhận một virtual thread mới.

## 9. Chọn đơn vị thực thi cho tình huống thực tế

| Tình huống | Lựa chọn khởi đầu hợp lý | Lý do và điều kiện |
| --- | --- | --- |
| Cần chạy chương trình tách biệt, cách ly tài nguyên/quyền | Process mới | Ranh giới hệ điều hành; phải chủ động giao tiếp, xử lý lỗi và thu hồi tiến trình |
| Vài tác vụ nền tồn tại lâu hoặc có ràng buộc với native OS thread | Platform thread | Phù hợp khi số lượng luồng hạn chế và đặc tính native cần thread riêng |
| Lượng lớn request độc lập, phần lớn thời gian chờ I/O | Virtual thread cho mỗi task | Mã đồng bộ dễ theo dõi, ít OS thread bị giữ khi chờ I/O có hỗ trợ |
| Hàng loạt phép tính CPU-bound | Executor giới hạn concurrency theo CPU | Virtual thread không tăng năng lực CPU; cần chia nhỏ và đo hiệu năng |
| Gọi dịch vụ/DB có quota hay connection pool cố định | Task + giới hạn truy cập tài nguyên | Số virtual thread lớn không thay thế quota, timeout và backpressure |
| Nhiều task cần trả kết quả, phát lỗi hoặc hủy có kiểm soát | `ExecutorService` + `Future` | Tách mô tả task khỏi điều phối và theo dõi vòng đời |

Đừng chọn theo số lượng thread tối đa trong một bản demo. Hãy đo thời gian chờ, CPU, bộ nhớ, số connection, contention, latency và throughput với tải thực tế. Mục tiêu là để nhiều **công việc hữu ích** tiến triển an toàn, chứ không phải tạo nhiều thread nhất.

## 10. Những nhầm lẫn cần loại bỏ

1. **“Một task luôn là một thread.”** Sai. Một thread trong pool có thể chạy hàng nghìn task qua thời gian; một task có thể tự chia thành nhiều tác vụ con. Chỉ với `newVirtualThreadPerTaskExecutor`, mỗi task được executor gán một virtual thread mới.
2. **“Virtual thread không dùng OS thread.”** Sai. Mã Java của virtual thread vẫn chạy trên carrier platform thread; OS vẫn lập lịch carrier lên CPU.
3. **“Virtual thread làm bài toán tính toán nặng chạy nhanh hơn.”** Không có bảo đảm đó. Tác vụ CPU-bound vẫn tranh cùng các lõi CPU, thậm chí tạo quá nhiều task có thể gây overhead.
4. **“Thread.sleep() nghĩa là nhường CPU một cách công bằng.”** `sleep` yêu cầu chờ tối thiểu theo ngữ nghĩa API, không bảo đảm công bằng, thời điểm được chạy lại hoặc thứ tự xử lý.
5. **“Dùng virtual thread thì không cần khóa.”** Sai. Cả hai loại thread chia sẻ dữ liệu và phải tuân theo Java Memory Model.
6. **“Có Future.get(timeout) thì task đã bị dừng khi hết giờ.”** Sai. Timeout khi đợi không phải hủy; cần chính sách cancel, interrupt và cleanup riêng.
7. **“Càng nhiều virtual thread thì càng nhiều kết nối DB.”** Sai. Connection pool và hệ thống đích vẫn quyết định mức truy cập hữu ích.
8. **“Tất cả blocking operations đều unmount virtual thread.”** Sai. Có các trường hợp pinning/native và khác biệt giữa phiên bản JDK; cần quan sát trên môi trường chạy thật.

## 11. Tự kiểm tra hiểu biết

**Tình huống A:** Chạy hai lần `java MyApp`. Mỗi lần tạo 20 virtual thread. Có bao nhiêu process Java và các virtual thread có mặc định chia sẻ biến `static` giữa hai lần chạy không?

**Đáp án:** Thông thường là hai JVM process; mỗi process có không gian heap riêng. Hai nhóm virtual thread trong hai process không chia sẻ trực tiếp cùng biến `static`.

**Tình huống B:** Một ứng dụng có 5.000 công việc chờ HTTP, mỗi công việc chủ yếu đợi phản hồi mạng. Vì sao virtual thread có thể phù hợp hơn 5.000 platform thread?

**Đáp án:** JVM có thể unmount nhiều virtual thread đang chờ I/O, tái sử dụng tập carrier OS thread nhỏ hơn nhiều để chạy những virtual thread sẵn sàng. Kết quả vẫn phụ thuộc thư viện mạng, giới hạn hệ thống đích, socket, bộ nhớ và cấu hình runtime.

**Tình huống C:** Đổi từ platform thread sang virtual thread nhưng phép tính `counter++` trên biến dùng chung vẫn cho kết quả sai. Vì sao?

**Đáp án:** Race condition nằm ở việc cập nhật nhiều bước không được đồng bộ, không nằm ở loại thread. Cần `AtomicInteger`, đồng bộ hoặc cấu trúc dữ liệu an toàn phù hợp.

**Tình huống D:** Một task gọi `Future.get(100, MILLISECONDS)` rồi timeout. Task đang chạy có chắc chắn đã dừng?

**Đáp án:** Không. Timeout chỉ kết thúc việc đợi kết quả của bên gọi; muốn hủy cần thực hiện `cancel` và thiết kế task phản hồi tín hiệu ngắt, đóng tài nguyên đúng cách.

## 12. Tổng hợp mối quan hệ

**Process** đặt ranh giới thực thi và tài nguyên ở cấp hệ điều hành. Trong một JVM process, **platform thread** cung cấp luồng thực thi gắn OS thread, còn **virtual thread** cung cấp luồng Java nhẹ do JVM gắn lên carrier khi cần chạy. **Task** mô tả công việc cần hoàn tất; executor hoặc mã ứng dụng quyết định đặt task vào thread nào và quản lý kết quả ra sao.

Với Java 21+, cách tiếp cận tự nhiên cho nhiều tác vụ chờ I/O là **mỗi task một virtual thread** cùng quản lý vòng đời, timeout và giới hạn tài nguyên. Với tác vụ CPU-bound hoặc ràng buộc OS/native đặc thù, quyết định dựa trên số lõi, chi phí thật và đo đạc. Dù chọn loại thực thi nào, an toàn dữ liệu và cách hủy task vẫn là trách nhiệm thiết kế của chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-003">CPU-bound và I/O-bound: chọn cách thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# CPU-bound và I/O-bound: chọn cách thực thi trong Java

Khi xử lý đồng thời trong Java, câu hỏi đầu tiên cần trả lời là: **tác vụ dùng CPU để tính toán phần lớn thời gian, hay chủ yếu chờ một tài nguyên bên ngoài?** Đặc tính này quyết định số lượng worker, mô hình API, cơ chế giới hạn tải và chỉ số cần đo. Bài này tập trung vào Java nói chung (thread, executor, I/O, virtual thread); các cơ chế riêng của Spring được học ở chương tương ứng.

## 1. Phân biệt CPU-bound, I/O-bound và workload hỗn hợp

**CPU-bound**: phần lớn thời gian hữu ích của tác vụ tiêu tốn cho tính toán trên CPU, ví dụ nén dữ liệu, hashing, xử lý ảnh, thuật toán số học, parse dữ liệu lớn đã nằm trong RAM. Nếu CPU đã bận tối đa, tăng số thread không tăng số phép toán CPU thực thi đồng thời; quá nhiều runnable thread còn tăng chuyển ngữ cảnh và chi phí cache.

**I/O-bound**: tác vụ dành nhiều thời gian chờ thao tác bên ngoài hoàn thành, như đọc/ghi đĩa, truy vấn database, chờ HTTP response, chờ socket. Với blocking I/O và platform thread truyền thống, thread có thể bị giữ trong lúc chờ dù không dùng CPU để tính toán yêu cầu ấy. Cho nhiều tác vụ cùng chờ thường tăng thông lượng, nhưng không thể bỏ qua giới hạn connection, quota, heap và downstream.

**Mixed workload**: các pha có đặc tính khác nhau. Ví dụ nhận ảnh (chờ mạng) → giải mã/resize (CPU) → tải lên storage (chờ mạng). Không nên gán nhãn CPU-bound/I/O-bound một lần cho cả ứng dụng; hãy phân loại **từng pha** và xác định nút nghẽn.

| Dấu hiệu | Khả năng | Cần kiểm chứng bằng |
| --- | --- | --- |
| CPU sát giới hạn, có nhiều runnable thread, tăng pool không cải thiện throughput | CPU-bound | CPU profiler, quota CPU, độ lớn task, contention |
| CPU còn rảnh, request chờ socket/DB/đĩa lâu | I/O-bound | tracing latency, I/O metrics, connection pool |
| CPU thấp nhưng hệ thống chậm | Chờ I/O, khóa, hàng đợi hoặc rate limit | thread dump, lock contention, queue depth |
| CPU cao và nhiều request chậm | Hỗn hợp hoặc quá tải nhiều tài nguyên | đo CPU time, wait time và từng pha |

Cẩn thận với `Thread.State`: `WAITING`/`TIMED_WAITING` không luôn là I/O; thread có thể đợi lock, `Future.get()`, `Thread.sleep()`. `BLOCKED` là chờ monitor lock, không tự đồng nghĩa với blocking network I/O. Không kết luận chỉ từ một thread dump.

## 2. Mô hình thời gian, throughput và concurrency

Gọi `C` là thời gian CPU hữu ích của một tác vụ, `W` là thời gian chờ, và `T ≈ C + W` khi chưa tính thêm queue. `C/T` lớn cho thấy tính toán chi phối; `W/T` lớn cho thấy chờ chi phối. Đây là mô hình suy nghĩ, không phải công thức tự động chọn số thread.

- **Parallelism:** số công việc thực sự được CPU xử lý đồng thời, bị giới hạn bởi năng lực CPU vật lý/ảo và mức CPU được cấp.
- **Concurrency:** số công việc đang tiến triển xen kẽ hoặc cùng lúc, kể cả những công việc đang đợi I/O. Concurrency có thể lớn hơn parallelism nhiều lần.
- **Latency:** thời gian từ lúc nhận một tác vụ tới lúc xong, bao gồm thời gian chờ queue. **Throughput:** số tác vụ hoàn thành mỗi giây.
- **Tail latency:** p95/p99 cho biết các yêu cầu chậm; throughput tăng không có nghĩa latency vẫn tốt.

**Little’s Law** áp dụng khi hệ thống ổn định: `L = λ × R`, trong đó `L` là số tác vụ trung bình trong hệ thống, `λ` là throughput (tác vụ/giây), `R` là thời gian trung bình (giây). Nếu mục tiêu là 100 request/giây và mỗi request mất trung bình 0,2 giây thì trung bình cần khoảng 20 request đồng thời đang ở trong hệ thống. Đây là ước lượng số request in-flight, **không phải mệnh lệnh đặt thread pool = 20**; giới hạn kết nối, độ biến thiên latency, rate limit và queue vẫn quyết định mức an toàn.

Với CPU-bound, định luật Amdahl giải thích phần thuật toán không song song hóa được sẽ giới hạn mức tăng tốc. Tạo nhiều thread không làm tăng số core, không loại bỏ khóa, cache contention hay chi phí phân phối task. Với I/O-bound, concurrency giúp che thời gian chờ nhưng quá nhiều request có thể làm downstream chậm đi và tạo vòng lặp quá tải.

## 3. Chọn mô hình thực thi trong Java

| Tình huống | Lựa chọn thường hợp lý | Điều cần chú ý |
| --- | --- | --- |
| Tính toán nặng trên nhiều tập dữ liệu độc lập | Fixed-size `ExecutorService` gần số CPU khả dụng | Benchmark độ lớn task, contention, memory bandwidth |
| Thuật toán chia để trị, tác vụ con không đồng đều | `ForkJoinPool` nếu thật sự phù hợp | Work-stealing có overhead; tránh blocking dài không quản lý |
| Blocking I/O trên Java 17 | Pool platform thread hữu hạn, bounded queue/admission control | Một thread bị giữ khi đợi; hạn chế thread, memory, connection |
| Nhiều blocking I/O độc lập trên Java 21+ | `Executors.newVirtualThreadPerTaskExecutor()` + giới hạn I/O | Rẻ hơn platform thread khi chờ; không làm CPU nhanh hơn |
| API đã có non-blocking/asynchronous I/O | `HttpClient.sendAsync`, các API NIO/async thích hợp | Callback/completion, error handling, timeout, executor cho pha CPU |
| Vài task rất nhỏ | Chạy tuần tự | Tránh overhead lập lịch và đồng bộ |
| Pipeline I/O → CPU → I/O | Điều tiết riêng mỗi pha | Tránh blocking trên completion/event-loop thread; không làm nghẽn CPU pool |

**Platform thread** truyền thống tương ứng với thread hệ điều hành và tốn tài nguyên hơn khi có rất nhiều yêu cầu chờ. **Virtual thread (ổn định từ Java 21)** được JVM lên lịch trên carrier platform thread; khi đi vào thao tác blocking tương thích, carrier có thể được dùng cho tác vụ khác. Java code vẫn có thể viết tuần tự, nhưng virtual thread không tạo thêm core CPU và không tăng quota của database/API.

**Asynchronous I/O** nghĩa là API trả lại kết quả tương lai hoặc callback để thread gọi có thể làm việc khác trong lúc chờ I/O. `CompletableFuture` tự nó không biến lời gọi blocking thành non-blocking: bọc mã blocking trong `supplyAsync` vẫn cần một thread để chạy. Các tác vụ async không truyền executor thường dùng `ForkJoinPool.commonPool()`; dồn blocking I/O dài vào common pool có thể ảnh hưởng các công việc khác.

## 4. Ví dụ CPU-bound trên Java 17: fixed pool

Ví dụ chạy độc lập sau chia bài toán đếm số nguyên tố từ 2 đến 100 thành bốn đoạn. Dữ liệu nhỏ chỉ để minh họa cấu trúc; **không dùng kết quả thời gian chạy để chứng minh multi-thread nhanh hơn**.

Tạo `CpuBoundExample.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CpuBoundExample {
    private static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int d = 2; (long) d * d <= n; d++) {
            if (n % d == 0) return false;
        }
        return true;
    }

    private static int countPrimes(int from, int to) {
        int count = 0;
        for (int n = from; n <= to; n++) {
            if (isPrime(n)) count++;
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        int workers = Math.min(4, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int start = 2; start <= 100; start += 25) {
                final int from = start;
                final int to = Math.min(start + 24, 100);
                futures.add(pool.submit(() -> countPrimes(from, to)));
            }

            int total = 0;
            for (Future<Integer> f : futures) total += f.get();
            System.out.println("So nguyen to tu 2 den 100: " + total);
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac CpuBoundExample.java
java CpuBoundExample
```

Stdout chính xác:

```text
So nguyen to tu 2 den 100: 25
```

`submit` đưa `Callable` vào executor và trả `Future`; `get()` chờ, trả kết quả hoặc ném `ExecutionException` nếu task lỗi. Vòng cộng kết quả theo danh sách nên kết quả không phụ thuộc thứ tự chạy các worker. `shutdown()` không nhận task mới; trong ví dụ, mọi `get()` đã xong trước khi shutdown. Số worker lấy từ `availableProcessors()` chỉ là điểm bắt đầu: trong container/môi trường chia sẻ, cần xác minh CPU thực được cấp và đo throughput/latency. Với bài toán nhỏ hoặc ít task, tuần tự có thể nhanh hơn vì overhead.

**Lưu ý production:** `Executors.newFixedThreadPool` có queue không giới hạn kích thước trong triển khai chuẩn. Pool cố định **không** tự giới hạn số task đang chờ trong queue. Với nguồn request liên tục, dùng queue hữu hạn, backpressure hoặc admission control.

## 5. Ví dụ I/O-bound trên Java 17: pool platform thread có giới hạn

Để chạy offline và giữ output xác định, mã dùng `Thread.sleep(50)` **mô phỏng thời gian chờ**, không phải thao tác I/O thật và không dùng làm benchmark mạng/đĩa. Trong ứng dụng thực, vị trí này có thể là truy vấn DB hoặc HTTP blocking call đã cấu hình timeout.

Tạo `BlockingWaitExample.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BlockingWaitExample {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                final int id = i;
                futures.add(pool.submit(() -> {
                    Thread.sleep(50); // Chỉ mô phỏng thời gian chờ I/O
                    return "ok-" + id;
                }));
            }

            for (Future<String> f : futures) f.get();
            System.out.println("Da hoan thanh " + futures.size() + " tac vu");
        } finally {
            pool.shutdown();
        }
    }
}
```

```bash
javac BlockingWaitExample.java
java BlockingWaitExample
```

Stdout:

```text
Da hoan thanh 8 tac vu
```

Bốn platform thread xử lý tám tác vụ; khi một thread chờ `sleep`, nó không xử lý task khác trong pool. Tăng số thread có thể cải thiện throughput nếu workload chủ yếu chờ I/O, nhưng bị giới hạn bởi stack memory, OS thread, connection pool, rate limit và downstream. `Future.get()` trong ví dụ không có timeout để minh họa luồng đơn giản; production phải đặt timeout ở client I/O và có thể dùng `get(timeout, unit)` cho deadline phía caller. Timeout của `get` **không tự hủy** thao tác đang chạy.

## 6. Java 21+: virtual thread cho blocking I/O

Virtual thread giúp xử lý nhiều blocking call mà không phải dành một platform thread riêng cho mỗi request chờ. Ví dụ sau chạy với Java 21+, không cần bật preview feature.

Tạo `VirtualThreadWaitExample.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadWaitExample {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                 Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                final int id = i;
                futures.add(executor.submit(() -> {
                    Thread.sleep(50); // Chỉ mô phỏng blocking I/O
                    return "ok-" + id;
                }));
            }

            for (Future<String> f : futures) f.get();
            System.out.println("Da hoan thanh " + futures.size() + " tac vu");
        }
    }
}
```

```bash
javac VirtualThreadWaitExample.java
java VirtualThreadWaitExample
```

Stdout:

```text
Da hoan thanh 20 tac vu
```

`newVirtualThreadPerTaskExecutor()` tạo virtual thread cho từng task. `try` quản lý vòng đời executor, đóng và chờ hoàn thành khi thoát scope. Đây vẫn là mô phỏng wait; không có bằng chứng benchmark I/O thật. Nếu thay `sleep` bằng phép tính CPU nặng, virtual thread **không** cho phép 20 tác vụ cùng tính trên 20 core khi máy chỉ có vài core.

**Không cần pool virtual thread nhỏ để “tiết kiệm thread”.** Thay vào đó phải giới hạn số request tới resource cụ thể: connection pool, semaphore, rate limiting và số pending task. Virtual thread vẫn chiếm heap cho stack/data mỗi tác vụ; có hàng vạn request chờ vẫn có thể gây OOM hoặc quá tải downstream. Trên một số JDK cũ (đặc biệt Java 21–23), blocking khi giữ `synchronized` monitor có thể pin carrier platform thread; các JDK mới đã cải tiến hành vi này. Dùng JFR và profile của runtime/thư viện đang triển khai để kiểm chứng, nhất là khi thư viện thực hiện blocking I/O trong critical section.

## 7. Khi đã có asynchronous I/O

`java.net.http.HttpClient` có từ Java 11. `sendAsync` trả `CompletableFuture<HttpResponse<T>>` để caller không phải chờ đồng bộ tới lúc nhận HTTP response. Đoạn sau là **snippet**, giả sử biến `endpoint` có kiểu `java.net.URI` và trỏ tới URI phù hợp; đoạn này không phải chương trình độc lập và không có stdout cố định:

```java
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofSeconds(3))
        .GET()
        .build();

CompletableFuture<HttpResponse<String>> response =
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

CompletableFuture<Integer> length =
        response.thenApply(r -> r.body().length());
```

Import cần thiết: `java.net.http.HttpClient`, `HttpRequest`, `HttpResponse`, `java.time.Duration` và `java.util.concurrent.CompletableFuture`. `thenApply` biến đổi kết quả sau khi response có sẵn. Nếu bước xử lý body tiêu tốn CPU lớn, dùng `thenApplyAsync(..., cpuExecutor)` để tách sang executor CPU. Gọi `join()`/`get()` ngay trên completion thread hoặc event-loop có thể làm mất lợi ích non-blocking. Cần xử lý lỗi bằng `handle`/`exceptionally`, timeout và việc hủy chuỗi công việc.

**Async hay virtual thread?** Nếu API đã non-blocking và kiến trúc sử dụng pipeline `CompletableFuture` tốt, async hợp lý. Nếu phần lớn thư viện là blocking và chạy Java 21+, virtual thread giúp code tuần tự, `try/catch` và quản lý tài nguyên rõ ràng. Chọn theo API và workload hiện tại; cả hai đều phải kiểm soát tải.

## 8. Giới hạn tải: thread, queue, connection và deadline

Hai thứ phải điều tiết riêng: (1) số công việc **đang hoạt động/chờ tài nguyên**, (2) số công việc **đang xếp hàng chờ được thực thi**. Dùng fixed pool mà queue vô hạn có thể dẫn tới latency tăng rất lâu, memory tăng và cuối cùng quá tải. Với platform thread, `ThreadPoolExecutor` + `ArrayBlockingQueue` hữu hạn và rejection policy có chủ đích giúp giới hạn công việc nhận vào. `CallerRunsPolicy` đôi khi tạo backpressure nhưng có thể chặn caller và kéo theo tắc nghẽn lan truyền; phải kiểm tra đường đi request.

Có thể tham khảo heuristic cho blocking I/O trên platform thread: `N_threads ≈ N_cpu × U_target × (1 + W/C)`, trong đó `U_target` là mức CPU utilization mục tiêu, `W` là thời gian đợi, `C` là thời gian tính CPU. Chỉ dùng như **điểm xuất phát để thử nghiệm**, không phải giới hạn an toàn tuyệt đối vì context switch, lock, connection pool, queue và memory có thể chi phối. Với CPU-bound, bắt đầu với số worker xấp xỉ CPU hiệu dụng rồi đo. Với virtual thread, thay câu hỏi “pool bao nhiêu thread?” bằng “tối đa bao nhiêu I/O call đang dùng resource này?”.

Ví dụ **snippet** giới hạn tối đa 20 lời gọi blocking I/O cùng lúc khi sử dụng virtual thread:

```java
Semaphore permits = new Semaphore(20);

try {
    permits.acquire();
    try {
        // Thực hiện một blocking I/O call có timeout tại đây.
    } finally {
        permits.release();
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Hủy hoặc báo trạng thái phù hợp.
}
```

Cần import `java.util.concurrent.Semaphore`; body I/O do ứng dụng bổ sung. `Semaphore` giới hạn phần thực thi **sau khi lấy permit**, không giới hạn số virtual thread đang đợi permit. Nếu input đến liên tục vô hạn, cần thêm bounded request queue, giới hạn số task chờ, admission control hoặc rate limit tại nguồn.

Ví dụ DB pool chỉ có 30 connection: dù có thể tạo 10.000 virtual thread, không thể có 10.000 truy vấn chiếm connection đồng thời nếu pool giới hạn 30. Khi thấy thời gian đợi lấy connection cao, tối ưu truy vấn, transaction, workload và mức tải trước khi tăng concurrency.

## 9. Timeout, hủy, lỗi và lifecycle

- **Timeout tại I/O client**: cấu hình connect/read/request timeout hoặc deadline tùy API; tránh request chờ vô hạn. `Future.get(timeout, unit)` chỉ giới hạn thời gian caller chờ, không tự dừng task đang chạy.
- **Cancellation**: `Future.cancel(true)` có thể interrupt thread đang chạy, nhưng task phải hợp tác và API I/O có semantics hủy riêng. Không giả định thao tác bên ngoài đã rollback khi Java task bị hủy.
- **Interrupt**: khi bắt `InterruptedException` mà không ném tiếp, thông thường khôi phục bằng `Thread.currentThread().interrupt()` rồi kết thúc/điều phối hủy phù hợp. Không nuốt interrupt.
- **Lỗi từ task**: `Future.get()` bọc lỗi trong `ExecutionException`; `CompletableFuture.join()` có thể bọc trong `CompletionException`. Ghi log nguyên nhân gốc và correlation id, tránh chỉ thấy wrapper.
- **Đóng executor**: dừng nhận task, chờ có deadline và dùng `shutdownNow()` khi chính sách yêu cầu. Quản lý vòng đời executor ở component phù hợp; không tạo platform-thread executor cho từng request rồi bỏ mặc.
- **Dữ liệu dùng chung**: immutable object, ownership, concurrent collection hoặc synchronization thích hợp để tránh data race. Tăng/giảm thread count không giải quyết deadlock, race condition hay lock contention.

## 10. Cách đo và ra quyết định dựa trên số liệu

1. **Xác định mục tiêu**: throughput, p95/p99 latency, CPU cost, memory, tail latency hay lỗi khi downstream chậm? Nắm giới hạn CPU container/VM, connection và rate limit.
2. **Lấy baseline dưới tải đại diện**: requests/sec, latency trung bình và p95/p99, CPU sử dụng, heap/GC, queue depth, active thread, active connection và timeout/errors.
3. **Định vị nút nghẽn**: Java Flight Recorder (JFR), CPU profiler, thread dump (`jcmd <pid> Thread.print`), application tracing và metric I/O. Đo CPU time và wall-clock time của từng pha; CPU thấp có thể do lock, I/O hoặc throttling.
4. **Thử từng chiến lược**: tuần tự so với fixed CPU pool cho tác vụ tính toán; platform-thread bounded pool/virtual thread/async cho I/O. Giữ dữ liệu và downstream tương đương; tăng concurrency có kiểm soát.
5. **Quan sát điểm gãy**: nếu thêm concurrency không tăng throughput mà queue, p99, timeout hoặc memory tăng thì hệ thống đã chạm nút nghẽn. Giảm tải hoặc cải thiện tài nguyên/thuật toán tương ứng.
6. **Kiểm tra sau triển khai**: load spike, downstream slowdown, client disconnect/cancellation, graceful shutdown; đặt cảnh báo trên queue wait, saturation, errors và resource exhaustion.

`Runtime.getRuntime().availableProcessors()` trong JVM hiện đại thường phản ánh cấu hình processor mà runtime thấy, có thể chịu ảnh hưởng cgroup/container; không tự coi đó là bảo đảm hiệu năng. Benchmark CPU-bound phải đủ lớn để chi phí tạo task không chi phối, và đo cả memory/cache contention.

## 11. Những sai lầm phổ biến

| Sai lầm | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Tạo hàng trăm thread cho CPU-bound dù chỉ vài core | Context switch/cache contention, chậm hơn | CPU pool sát năng lực CPU, đo và điều chỉnh |
| Chạy blocking I/O dài trong `ForkJoinPool.commonPool()` tùy tiện | Starvation và khó kiểm soát queue | Dùng executor I/O riêng, virtual thread hoặc async API |
| Tin virtual thread làm thuật toán tính toán nhanh hơn | CPU vẫn nghẽn, quá nhiều runnable task | Giới hạn CPU parallelism, tối ưu thuật toán |
| Tin virtual thread đồng nghĩa downstream vô hạn | Hết DB connections, rate limit, socket/heap | Semaphore, connection cap, queue bound, timeout |
| Dùng `Thread.sleep` như benchmark HTTP/DB | Đo giả lập, bỏ qua downstream và latency đuôi | Benchmark I/O thật trong điều kiện vận hành |
| Chỉ xem throughput trung bình | Bỏ sót p99, queue và lỗi | Theo dõi tail latency, queue, saturation, errors |
| `get()` không deadline, nuốt interrupt | Request treo và shutdown khó | Timeout, cancel và propagation đúng |
| Tạo executor rồi không đóng | Rò rỉ thread/resource | Quản lý vòng đời rõ ràng |

## 12. Bốn tình huống áp dụng

**A. Hash một triệu bản ghi trong RAM.** Chủ yếu CPU-bound: chia thành lô độc lập, dùng số worker gần số CPU hữu dụng, đo kích thước lô và memory pressure. Virtual thread không tăng tốc hash.

**B. Tổng hợp kết quả từ 10 HTTP API.** Chủ yếu chờ I/O: nếu dùng blocking client trên Java 21+, virtual thread + timeout mỗi call + giới hạn đồng thời theo API là một lựa chọn dễ đọc. Nếu API đã hỗ trợ `sendAsync`, pipeline bất đồng bộ cũng phù hợp. Không vượt quota dịch vụ.

**C. Download ảnh → resize → upload.** Download/upload I/O-bound; resize CPU-bound. Điều tiết từng pha và chuyển bước resize sang CPU executor; tránh xử lý ảnh nặng trực tiếp trên event-loop/completion thread. Cân nhắc stream để không lưu cả file lớn trong heap.

**D. Database query với pool 30 connection.** Tăng platform/virtual thread không tạo thêm connection. Nếu query chậm, đo thời gian chờ connection, thời gian SQL, transaction, index và throughput của DB; tăng concurrency chỉ khi DB và pool thực sự chịu được.

## 13. Checklist trước khi chốt thiết kế

- Từng pha là **tính toán**, **chờ I/O**, hay **chờ lock/queue**? Có profile và số đo không?
- Java 17 hay Java 21+? API hỗ trợ non-blocking thật hay chỉ bọc blocking call trong `CompletableFuture`?
- CPU được cấp thực tế là bao nhiêu? Downstream cho phép bao nhiêu connection/request cùng lúc?
- Active tasks và pending queue bị giới hạn thế nào? Khi overload sẽ chờ, từ chối hay giảm tải?
- Có timeout, error propagation, cancellation, xử lý interrupt và đóng executor đúng chưa?
- Thử tải thực tế có cải thiện throughput **và** p95/p99 mà không làm tăng errors, queue, memory hoặc saturation không?

**Nguyên tắc áp dụng:** CPU-bound → giới hạn tính toán song song theo năng lực CPU; I/O-bound → dùng concurrency để che thời gian chờ trong giới hạn tài nguyên; Java 21+ virtual thread giúp code blocking I/O quy mô lớn rõ ràng; API non-blocking hiện hữu có thể tận dụng async. Với nghiệp vụ hỗn hợp, chia giai đoạn và đo đạc từng phần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-004">Latency, throughput, responsiveness và utilization</a>

<details>
<summary>Xem chi tiết</summary>

# Latency, throughput, responsiveness và utilization trong Java concurrency

## Mục tiêu và bối cảnh

Khi đánh giá một chương trình Java chạy nhiều tác vụ đồng thời, “nhanh” có thể mang nhiều nghĩa: một yêu cầu hoàn thành sớm, hệ thống xử lý được nhiều yêu cầu mỗi giây, người dùng thấy ứng dụng phản hồi kịp thời, hay CPU/luồng được sử dụng hiệu quả. Bốn góc nhìn đó lần lượt là **latency**, **throughput**, **responsiveness** và **utilization**. Chúng liên quan với nhau nhưng không thay thế cho nhau.

Sau bài này, bạn có thể:
- Đặt ranh giới và đơn vị đo cho từng chỉ số, phân biệt thời gian chờ hàng đợi với thời gian thực thi.
- Đọc trung vị, p95, p99 và nhận ra vì sao một con số trung bình không mô tả được trải nghiệm xấu nhất của một nhóm người dùng.
- Giải thích vì sao tăng số thread có thể tăng throughput ở tác vụ chờ I/O nhưng không chắc giúp tác vụ tính toán, thậm chí làm latency xấu đi.
- Liên hệ lượng công việc đang xử lý, tốc độ hoàn thành và thời gian phản hồi bằng định luật Little; nhận ra khi hệ thống gần quá tải.
- Tự đo thử các chỉ số trong Java bằng `System.nanoTime()` và `ExecutorService`, rồi diễn giải số liệu đúng giới hạn thí nghiệm.

**Phạm vi:** cơ chế concurrency của Java/JVM nói chung, gồm thread, executor, hàng đợi, CPU và tác vụ chờ I/O. Các cơ chế hoặc cấu hình riêng của Spring thuộc một chương khác.

## 1. Một mô hình thời gian trước khi đo

Giả sử một tác vụ được gửi đến hệ thống tại thời điểm `t0`, bắt đầu được một worker xử lý tại `t1` và hoàn thành tại `t2`.

~~~text
Tác vụ đến/được submit                 Bắt đầu chạy                  Hoàn thành
t0                                    t1                           t2
|-------------- queue wait ------------|--------- execution ---------|
|------------------- latency đo tại ranh giới này ------------------|
~~~

Nếu chỉ xét quãng đường từ lúc submit đến khi kết thúc:
- **Queue wait:** `t1 − t0`. Tác vụ đã được nhận nhưng chưa có worker thực hiện.
- **Execution elapsed time:** `t2 − t1`. Có thể gồm **CPU thực chạy**, chờ khóa, chờ I/O, `sleep`, bị scheduler tạm dừng… Vì vậy “execution elapsed” không đồng nghĩa “CPU time”.
- **Task latency:** `t2 − t0 = queue wait + execution elapsed time`.

Ví dụ tác vụ chờ trong hàng đợi 80 ms rồi mất 40 ms để hoàn tất: latency tính từ lúc submit là **120 ms**, dù đoạn chạy của worker chỉ 40 ms. Nếu đo ở phía người gọi qua mạng, latency còn có thể bao gồm truyền dữ liệu, proxy, serialization và thời gian phía client. Luôn ghi rõ **bắt đầu đo ở đâu, kết thúc đo ở đâu, đối tượng đo là gì**.

Với tác vụ có nhiều giai đoạn (enqueue → gọi dịch vụ khác → đợi lock → tính toán → gửi kết quả), nên đo cả latency toàn phần và thời gian từng giai đoạn. Việc chỉ đặt đồng hồ quanh lời gọi xử lý sau khi đã lấy tác vụ ra khỏi queue có thể che mất nút thắt thật sự.

## 2. Latency: một công việc mất bao lâu?

**Latency** là khoảng thời gian để một đơn vị công việc hoàn thành, tính theo ranh giới đã công bố. Đơn vị thường là ms hoặc µs. Khi nói “latency 100 ms” cần biết đó là latency của lần gọi nào, phía client hay server, có tính hàng đợi không, thành công hay cả lỗi/timeout.

Các thống kê quen thuộc:

| Chỉ số | Ý nghĩa | Điều cần nhớ |
| --- | --- | --- |
| Mean (trung bình) | Tổng thời gian / số mẫu | Dễ bị kéo lên bởi vài yêu cầu rất chậm |
| Median / p50 | Khoảng 50% mẫu có latency không vượt quá giá trị này | Mô tả trải nghiệm điển hình |
| p95 | Khoảng 95% mẫu không vượt quá giá trị này | Thấy rõ hơn phần đuôi chậm |
| p99 | Khoảng 99% mẫu không vượt quá giá trị này | Quan trọng với trải nghiệm hiếm nhưng tệ |
| Max | Mẫu chậm nhất trong tập đã đo | Nhạy với số mẫu và nhiễu |

Ví dụ có 100 yêu cầu, 95 yêu cầu hoàn thành trong khoảng 20–40 ms và 5 yêu cầu mất 800 ms: trung bình có thể trông vẫn chấp nhận được với một số mục tiêu, nhưng p99 sẽ ở vùng khoảng 800 ms. Một giao diện hoặc chuỗi nhiều lời gọi dễ bị ảnh hưởng bởi các trường hợp chậm đột biến này.

**Percentile phụ thuộc tập mẫu và cách tính.** Với mẫu ít, p99 rất kém ổn định; các công cụ dùng nội suy hoặc histogram khác nhau có thể cho số khác nhau. Đừng so hai p99 nếu cửa sổ đo, tập yêu cầu, cách xử lý lỗi hoặc thuật toán percentile khác nhau. Percentile cũng không thể cộng trực tiếp: p95 của bước A cộng p95 của bước B không nhất thiết là p95 của toàn bộ yêu cầu.

Để phân tích latency cao, tách ít nhất: **thời gian nằm trong queue**, **thời gian chờ lock/đồng bộ**, **thời gian chờ I/O**, **thời gian chạy thực tế**. Thời gian tạm dừng do GC, CPU scheduling và khởi động/JIT cũng có thể tạo ra đỉnh latency.

## 3. Throughput: hoàn thành được bao nhiêu việc?

**Throughput** là lượng công việc hoàn thành trong một đơn vị thời gian, ví dụ task/s, request/s, message/s hoặc MB/s:

~~~text
throughput = số công việc hoàn tất trong khoảng đo / độ dài khoảng đo (giây)
~~~

Nếu 600 tác vụ hoàn thành trong 10 giây, throughput quan sát được là 60 task/s. Chọn rõ “hoàn tất” nghĩa là mọi kết quả, chỉ kết quả thành công hay số byte thực truyền. Nên đi kèm **error rate** và **timeout rate**, bởi throughput tính cả yêu cầu thất bại có thể tạo ấn tượng sai về năng lực phục vụ hữu ích.

Phân biệt:
- **Arrival rate / offered load (λ):** số tác vụ đi vào mỗi giây.
- **Completion throughput (X):** số tác vụ kết thúc mỗi giây.
- **Capacity:** tốc độ mà hệ thống có thể duy trì với mục tiêu latency và tỷ lệ lỗi đã chọn, dưới workload và cấu hình xác định.

Ở trạng thái ổn định, không thất thoát tác vụ và hàng đợi không tăng dài hạn, tốc độ vào gần với tốc độ ra. Khi 120 request/s liên tục đi vào nhưng chỉ 90 request/s được xử lý xong, trung bình backlog tăng khoảng **30 request mỗi giây** cho đến khi có cơ chế từ chối, timeout, rớt tác vụ hoặc tải thay đổi. Một khoảng đo ngắn có thể cho throughput cao nhờ xả backlog cũ; nó chưa chứng minh năng lực duy trì.

Đo throughput phải nêu kích thước công việc: 10 tác vụ đọc cache không tương đương 10 truy vấn đĩa hay 10 phép mã hóa nặng. So số task/s của hai hệ thống có input khác nhau thường vô nghĩa.

## 4. Responsiveness: khả năng phản hồi kịp thời

**Responsiveness** mô tả hệ thống phản ứng nhanh và nhất quán như thế nào trước tương tác hoặc tín hiệu mới. Nó liên quan đến latency nhưng bao gồm cả **thời điểm có phản hồi đầu tiên**, sự mượt mà và khả năng tiếp tục phục vụ khi một công việc dài còn chạy.

Ví dụ:
- Một giao diện Java desktop nhận lệnh rồi chạy xử lý nặng trên luồng giao diện: tổng công việc có thể xong sau 2 giây, nhưng cửa sổ bị “đơ” trong cả 2 giây. Đưa công việc sang worker và cập nhật tiến độ giúp giao diện responsive hơn, dù tổng latency hoàn tất không chắc giảm.
- Một dịch vụ xử lý công việc kéo dài có thể nhanh chóng xác nhận “đã nhận” rồi hoàn thành sau. **Time to first acknowledgment** thấp, nhưng **end-to-end completion latency** vẫn dài. Không được đổi tên thời gian xác nhận thành thời gian hoàn tất.
- Khi hủy tác vụ hoặc hệ thống cần shutdown, worker kiểm tra cờ hủy/interrupt đúng cách thường cải thiện responsiveness với thao tác điều khiển, dù throughput của tác vụ thường ngày không đổi.

Responsiveness không có một công thức duy nhất áp dụng cho mọi sản phẩm. Hãy chọn chỉ số đại diện cho lời hứa thực tế: **time to first response**, trễ xử lý sự kiện, thời gian UI không phản hồi, độ trễ hủy tác vụ hoặc tail latency của yêu cầu tương tác. Đừng dùng “có nhiều thread” như bằng chứng rằng hệ thống responsive.

## 5. Utilization: tài nguyên bận đến mức nào?

**Utilization (U)** là tỷ lệ thời gian hoặc năng lực của **một tài nguyên xác định** đang được sử dụng trong cửa sổ đo:

~~~text
U = thời gian tài nguyên bận / thời gian tài nguyên có thể phục vụ
~~~

Ví dụ một CPU core bận chạy tác vụ trong 700 ms của một khoảng 1.000 ms có utilization xấp xỉ **70%** trong khoảng đó. Với máy 4 core, cần nêu cách chuẩn hóa: tiêu thụ trung bình 3 core tương đương khoảng 75% năng lực 4 core; một công cụ có thể hiển thị “300% CPU” khi lấy **100% cho mỗi core**. Cần kiểm tra mẫu số trước khi so số.

Trong Java thường cần phân biệt các tài nguyên sau:

| Tài nguyên đang đo | Chỉ số gần đúng | Không được suy ra |
| --- | --- | --- |
| CPU của tiến trình/hệ thống | Phần trăm CPU hoặc CPU time theo khoảng | 80% CPU không tự cho biết p99 latency |
| Worker thread pool | Số worker đang chạy tác vụ / kích thước pool | Worker đang chờ socket vẫn có thể được tính là active, dù không tiêu CPU |
| Connection pool | Số kết nối đang được mượn / tổng kết nối | Bận kết nối không đồng nghĩa CPU cao |
| Băng thông/đĩa | Tỷ lệ dùng công suất hoặc thời gian thiết bị bận | Thread count không phản ánh trực tiếp tải I/O |
| Lock/critical section | Thời gian giữ lock và số/thời gian chờ | Nhiều thread có thể chỉ làm tăng tranh chấp |

**Thread pool utilization và CPU utilization là hai đại lượng khác nhau.** Với tác vụ chờ I/O, 100% worker bận không nhất thiết là 100% CPU; còn ít worker “active” vẫn có thể có một thread tính toán ngốn trọn một core. `ThreadPoolExecutor.getActiveCount()` và `getQueue().size()` chỉ cung cấp các snapshot xấp xỉ, hữu ích để quan sát xu hướng chứ không phải số đếm tuyến tính hóa tuyệt đối.

Utilization quá thấp có thể là năng lực dư, workload ít, hoặc đang bị chặn bởi khóa/I/O. Utilization cao giúp tận dụng tài nguyên nhưng **không đồng nghĩa hiệu suất tốt**: khi gần bão hòa, hàng đợi có thể tăng mạnh, làm latency và độ biến động xấu đi. Không có một “% CPU lý tưởng” cho mọi hệ thống; cần xét burst, tail latency, độ ưu tiên và mức dự phòng mong muốn.

## 6. Bốn chỉ số ảnh hưởng lẫn nhau như thế nào?

### 6.1. Hàng đợi là mối nối quan trọng

Nếu tốc độ tác vụ đến vượt tốc độ một tài nguyên xử lý trong thời gian đủ dài, hàng đợi tăng. Công việc cũ chiếm chỗ, công việc mới chờ lâu hơn: **latency tăng**, **responsiveness giảm**, còn **throughput có thể đã chạm trần** dù utilization vẫn cao. Tăng thêm công việc hoặc thêm thread trong lúc CPU đã bão hòa thường chỉ làm tranh chấp, chuyển ngữ cảnh và bộ nhớ tệ hơn.

~~~text
Arrival rate tăng
       |
       v
Worker/resource tiến gần giới hạn
       |
       +---- năng lực còn đủ ----> throughput tăng, latency có thể ổn định
       |
       +---- gần/vượt giới hạn --> queue tăng --> p95/p99 latency tăng
                                                 |
                                                 v
                                    timeout, phản hồi chậm, lỗi nhiều
~~~

Nếu có hàng đợi hữu hạn và chính sách từ chối, backlog được giới hạn bằng cách **admission control / backpressure**: phần tải vượt năng lực nhận lỗi hoặc được yêu cầu thử lại. Cách này có thể bảo vệ latency cho tác vụ được nhận, nhưng phải đo riêng tỷ lệ bị từ chối; không được xem throughput thấp do từ chối là hiệu quả tốt.

### 6.2. Định luật Little: số việc trong hệ thống, tốc độ, thời gian

Với **hệ thống ổn định**, cùng ranh giới đo và cùng loại công việc, định luật Little cho:

~~~text
L = λ × W
~~~

- `L`: số công việc trung bình **đang ở trong hệ thống** (chờ + đang chạy).
- `λ`: tốc độ công việc đi qua hệ thống ở trạng thái ổn định, tính bằng công việc/giây.
- `W`: thời gian lưu lại hệ thống trung bình, tính bằng giây.

Ví dụ hệ thống ổn định hoàn thành **120 request/s**, mỗi request có latency trung bình **0,25 s** từ lúc nhận đến lúc xong. Khi đó số request trung bình đang chờ hoặc được thực thi là `L = 120 × 0,25 = 30`.

Dùng công thức đúng cách:
1. Đổi ms ra giây và giữ cùng phạm vi đo; nếu W không tính queue thì L cũng không được tính queue.
2. Sử dụng **trung bình**, không thay W bằng p95/p99 trong công thức.
3. Xem hàng đợi có tăng theo thời gian không. Khi hệ thống liên tục tích backlog, không thể lấy một cửa sổ ngắn rồi coi đó là trạng thái ổn định.
4. Nếu tính cả yêu cầu thất bại, retry hoặc timeout, phải xác định nhất quán khi nào chúng rời hệ thống.

Định luật Little mô tả quan hệ giữa các đại lượng, **không tự chứng minh** tăng concurrency sẽ tăng throughput. Nếu bottleneck là CPU 4 core, thêm vài trăm thread không tạo thêm core.

### 6.3. Utilization từ nhu cầu tài nguyên

Cho một tài nguyên có `m` đơn vị phục vụ tương đương nhau, mỗi công việc tiêu trung bình `D` giây **thời gian bận của một đơn vị tài nguyên**, và tốc độ hoàn tất `X` công việc/giây. Khi cân bằng và workload ổn định, mức utilization trung bình xấp xỉ:

~~~text
U ≈ X × D / m
~~~

Ví dụ tiến trình cần **0,025 CPU-core-second/request**; ở **120 request/s**, tổng nhu cầu là `120 × 0,025 = 3` core bận liên tục. Nếu có 4 core khả dụng cho tiến trình, utilization tương đương khoảng `3/4 = 75%`. Đây là phép tính cho **CPU time**, không được lấy elapsed time 250 ms có chờ mạng rồi thay vào `D` để suy ra CPU utilization.

Công thức gần đúng này giả định định nghĩa CPU capacity/affinity/quota phù hợp và đủ khả năng chạy song song. Đối với thread pool, thời gian bận có thể bao gồm chờ I/O; cần mô hình tài nguyên riêng.

### 6.4. Tăng số thread: khi nào hữu ích?

| Loại công việc | Thêm worker có thể đem lại | Giới hạn hoặc rủi ro |
| --- | --- | --- |
| CPU-bound: mã hóa, tính toán | Tăng parallelism đến khi dùng hết core hữu ích | Vượt core dễ tăng context switching, cache contention và tail latency |
| I/O-bound: chờ file/socket | Cho phép công việc khác chạy trong lúc một worker chờ | Quá nhiều thread/tác vụ có thể làm cạn RAM, connection pool hoặc downstream |
| Tranh chấp lock nặng | Thường không giải quyết bottleneck | Nhiều contender hơn có thể làm queue/latency xấu thêm |
| Workload hỗn hợp | Pool và giới hạn concurrency thích hợp có thể cân bằng | Cần đo riêng queue wait, CPU, I/O và phụ thuộc ngoài |

Từ Java 21, **virtual threads** giúp tạo nhiều tác vụ đồng thời đang chờ theo mô hình thread-per-task với chi phí thread nhỏ hơn trong nhiều tình huống. Chúng **không làm CPU-bound chạy song song vượt năng lực CPU**, và không xóa giới hạn database, socket, lock hay tài nguyên bên ngoài. Ví dụ đo trong phần sau dùng **Java 17+** và platform threads để dễ quan sát.

## 7. Thực hành: đo một batch tác vụ trong Java 17+

Thí nghiệm tạo **12 tác vụ**, mỗi tác vụ `Thread.sleep(120)` để mô phỏng *chờ* trong khoảng 120 ms. Chạy hai lượt với pool 2 và 4 worker; đo thời điểm submit, bắt đầu thực thi, hoàn thành và thời gian toàn batch. Mỗi lần đo dùng đồng hồ đơn điệu `System.nanoTime()`, thích hợp tính **khoảng thời gian** chứ không dùng như timestamp lịch.

Tạo tệp **`ConcurrencyMetricsDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrencyMetricsDemo {
    private record Sample(long queueNs, long runNs, long latencyNs) {}

    private static void experiment(int workers) throws InterruptedException {
        final int taskCount = 12;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Sample> samples = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch completed = new CountDownLatch(taskCount);

        long batchStart = System.nanoTime();
        for (int i = 0; i < taskCount; i++) {
            long submittedAt = System.nanoTime();
            pool.execute(() -> {
                long startedAt = System.nanoTime();
                try {
                    Thread.sleep(120); // Mô phỏng thời gian chờ, không đốt CPU
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    long finishedAt = System.nanoTime();
                    samples.add(new Sample(
                            startedAt - submittedAt,
                            finishedAt - startedAt,
                            finishedAt - submittedAt));
                    completed.countDown();
                }
            });
        }

        completed.await();
        long batchEnd = System.nanoTime();
        pool.shutdown();
        if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
            throw new IllegalStateException("Workers did not terminate");
        }

        List<Long> latencies = samples.stream()
                .map(Sample::latencyNs)
                .sorted()
                .toList();

        int p95Index = (int) Math.ceil(0.95 * latencies.size()) - 1;
        double p95Ms = latencies.get(p95Index) / 1_000_000.0;
        double avgQueueMs = samples.stream()
                .mapToDouble(s -> s.queueNs() / 1_000_000.0)
                .average().orElse(0.0);
        double avgRunMs = samples.stream()
                .mapToDouble(s -> s.runNs() / 1_000_000.0)
                .average().orElse(0.0);
        double throughput = taskCount
                / ((batchEnd - batchStart) / 1_000_000_000.0);

        System.out.printf(Locale.US,
                "workers=%d | throughput=%.1f tasks/s"
                + " | p95=%.1f ms | avgQueue=%.1f ms | avgRun=%.1f ms%n",
                workers, throughput, p95Ms, avgQueueMs, avgRunMs);
    }

    public static void main(String[] args) throws InterruptedException {
        experiment(2);
        experiment(4);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ConcurrencyMetricsDemo.java
java ConcurrencyMetricsDemo
~~~

Chương trình in **hai dòng** theo định dạng:

~~~text
workers=2 | throughput=... tasks/s | p95=... ms | avgQueue=... ms | avgRun=... ms
workers=4 | throughput=... tasks/s | p95=... ms | avgQueue=... ms | avgRun=... ms
~~~

Các dấu `...` ở đây **chỉ vị trí số thực đo**, không phải stdout số cố định. Kết quả chịu ảnh hưởng của máy, scheduler, JIT, độ chính xác timer và độ trễ khi bắt đầu thread.

**Cách diễn giải:** khi chỉ có hai worker, nhiều tác vụ phải đợi các đợt trước, nên `avgQueue` và `p95` thường cao hơn. Bốn worker có thể hoàn thành batch sớm hơn, cho throughput batch cao hơn, bởi các tác vụ chủ yếu đang ngủ/chờ. `avgRun` thường gần 120 ms cộng overhead của scheduler; đó là elapsed time của tác vụ, **không phải 120 ms CPU**.

**Giới hạn của thí nghiệm:**
- Đây là phép đo **burst 12 task**, throughput = 12 / thời gian hoàn thành batch; **không phải** capacity ổn định của dịch vụ hoạt động liên tục.
- `Executors.newFixedThreadPool` dùng hàng đợi không giới hạn về cấu trúc; ví dụ phù hợp để học phép đo trên batch nhỏ, **không nên mặc định áp dụng** cho hệ thống chịu tải bất kỳ.
- `p95` của 12 mẫu là phép thống kê học thuật rất nhiễu. Không dùng để khẳng định SLO hay so benchmark thực tế.
- `Thread.sleep` mô phỏng việc worker không tính toán; để thử CPU-bound, cần workload tính toán đủ lớn và đo CPU time thực.
- Chỉ đo tác vụ đã submit thành công; bài toán production còn cần đo lỗi, từ chối tác vụ, timeout, retry, warm-up và phân phối input.

Một cải tiến cho bài tập: bổ sung pool hữu hạn dùng `ThreadPoolExecutor` với `ArrayBlockingQueue` và `RejectedExecutionHandler` rõ ràng; thử tăng tốc độ submit để quan sát queue wait, throughput thành công và rejection cùng lúc. Đặt giới hạn an toàn, tránh tạo tải không kiểm soát.

## 8. Thu thập số liệu đáng tin trên JVM

### Đo đúng thứ cần đo

- Dùng `System.nanoTime()` để tính duration: `end - start`. Không dùng `System.currentTimeMillis()` cho benchmark thời gian rất ngắn vì đồng hồ wall clock có thể bị điều chỉnh và độ phân giải khác.
- Ghi thời gian **submit/enqueue → start → finish**; nếu có API network, bổ sung dấu thời gian **client send → first response → complete** để không trộn hai phía.
- Theo dõi histogram/distribution latency thay vì chỉ một average; giữ cùng cửa sổ đo, tag loại tác vụ, phiên bản, tải và tỷ lệ lỗi.
- Với `ThreadPoolExecutor`, quan sát `getActiveCount()`, `getPoolSize()`, `getQueue().size()`, số hoàn thành và rejection. Các getter cho trạng thái tức thời/xấp xỉ; lấy mẫu theo chu kỳ sẽ hữu ích hơn một snapshot đơn.
- Theo dõi CPU/GC/lock contention bằng công cụ JVM thích hợp như **Java Flight Recorder (JFR)** và **Java Mission Control (JMC)**; có thể dùng `java.lang.management.ThreadMXBean` để xem CPU time theo thread **nếu JVM hỗ trợ và tính năng được bật**. CPU time, wall time và blocked/waited time phục vụ các câu hỏi khác nhau.
- Gắn throughput, latency, CPU, queue depth, error/timeout rate vào **cùng biểu đồ theo thời gian** để hiểu hướng nhân quả: CPU cao trước khi p99 tăng? Queue tăng khi arrival vượt completion? Timeout tăng sau khi queue dài?

### Thiết kế thí nghiệm tải

1. Định nghĩa workload: kích thước dữ liệu, tỷ lệ CPU/I/O, số loại tác vụ, đường đi xử lý, cấu hình JVM và tài nguyên (core, memory, quota).
2. Warm-up đủ để tránh coi chi phí class loading/JIT ban đầu là steady state; nhưng ghi riêng hiệu năng cold start nếu đó là mục tiêu thực tế.
3. Tăng offered load theo nhiều mức và giữ đủ lâu để thấy hệ thống ổn định hoặc backlog tăng. So sánh throughput **thành công**, p50/p95/p99, CPU, queue và lỗi tại mỗi mức.
4. Phân biệt thử tải **open-loop** (gửi theo tốc độ định trước) với **closed-loop** (chờ phản hồi rồi gửi yêu cầu tiếp). Closed-loop có thể tự giảm tốc khi hệ thống chậm, làm kết quả nhìn đẹp sai lệch. Tránh **coordinated omission**: bộ phát tải không ghi nhận quãng thời gian đáng lẽ yêu cầu phải đến nhưng đã không được gửi vì hệ thống bị chậm.
5. Cố định môi trường và đo nhiều lượt; báo cả biến động. Dữ liệu từ laptop đang chạy nhiều tiến trình không đủ để khẳng định capacity production.

### Một bản ghi kiểm thử hữu ích

~~~text
Workload: 80% I/O wait, 20% CPU work; 4 CPU cores; JVM/config cố định
Offered load: 100 request/s, đo trong 5 phút sau warm-up
Completed success: 98 request/s; errors/timeouts: ghi riêng
Latency boundary: client send -> complete
Latency: mean ..., p50 ..., p95 ..., p99 ... (ms)
CPU utilization: ...% / 4 cores; pool active: .../N; queue: ...
Kết luận: xét xu hướng qua nhiều mức tải, chưa kết luận từ một mẫu
~~~

Các dấu `...` là chỗ cần **điền số đo thật**, không phải số liệu được suy đoán.

## 9. Phân tích tình huống thường gặp

| Quan sát đồng thời | Giả thuyết nên kiểm chứng | Hướng điều tra/điều chỉnh |
| --- | --- | --- |
| Throughput gần như đứng yên, CPU gần bão hòa, queue và p99 tăng | CPU đã là bottleneck | Profile CPU, giảm CPU/request, giảm tải được nhận hoặc bổ sung tài nguyên CPU; tránh tăng thread vô tội vạ |
| CPU thấp, worker active gần hết, queue tăng | Worker kẹt I/O hoặc chờ lock/downstream | Đo blocked/waited time, timeout và tài nguyên phụ thuộc; cân nhắc giới hạn concurrency/pool phù hợp |
| Mean latency ổn nhưng p99 tăng mạnh | Tắc nghẽn theo đợt, GC pause, lock, slow I/O hoặc bất cân bằng công việc | Xem histogram, trace theo giai đoạn, JFR và thời điểm hàng đợi tăng |
| Throughput tăng nhưng nhiều timeout/rejection | Tải vào tăng hoặc cơ chế từ chối bắt đầu hoạt động | Báo throughput thành công và tỷ lệ lỗi; xác định capacity theo SLO |
| Tăng worker giúp task chờ I/O nhưng làm CPU-bound chậm hơn | Thêm concurrency đã vượt tài nguyên hữu ích | Tách workload, giới hạn pool, đo chuyển ngữ cảnh/CPU/queue |
| Trả lời “đã nhận” rất nhanh nhưng kết quả thực rất muộn | First response tốt nhưng completion latency kém | Công bố rõ trạng thái, đo hai mốc và khả năng hủy/theo dõi tiến độ |

Đừng suy kết luận chỉ từ một chỉ số. Ví dụ **CPU thấp + p99 cao** không chứng minh hệ thống còn nhiều năng lực: một lock hoặc downstream chậm có thể là nút cổ chai trong khi CPU gần như rảnh.

## 10. Lỗi tư duy phổ biến và nguyên tắc áp dụng

1. **“Nhiều thread hơn nghĩa là nhanh hơn.”** Chỉ đúng trong những giới hạn tài nguyên và loại workload nhất định; CPU-bound, lock contention và áp lực bộ nhớ có thể tệ hơn.
2. **“Throughput cao nghĩa là latency thấp.”** Một hệ thống có thể hoàn thành rất nhiều request/s nhưng vẫn làm từng request đợi lâu trong queue.
3. **“Mean thấp nên mọi người dùng đều hài lòng.”** Cần xem p95/p99, timeout và ranh giới đo.
4. **“100% utilization luôn tốt.”** Gần bão hòa thường giảm khả năng hấp thụ burst; tail latency có thể tăng rất nhanh.
5. **“Thread active tức là CPU bận.”** Thread có thể chờ I/O, `synchronized`, `Lock` hoặc điều kiện.
6. **“Task latency bằng thời gian xử lý sau dequeue.”** Queue wait cũng là phần người gọi phải chờ nếu bắt đầu đo từ lúc submit.
7. **“Một batch benchmark chứng minh công suất duy trì.”** Burst, warm-up, backlog và cơ chế phát tải có thể làm lệch kết quả.
8. **“Chỉ cần tăng timeout.”** Timeout dài hơn có thể giữ nhiều tác vụ trong hệ thống hơn và làm nghẽn tài nguyên; phải tìm bottleneck và chính sách giới hạn tải.

Khi thiết kế chương trình concurrent trong Java, hãy bắt đầu bằng **mục tiêu có thể đo**: ví dụ “p95 completion latency dưới 200 ms ở 150 task/s, lỗi dưới 0,1%, trong điều kiện 4 core và workload đã mô tả”. Sau đó quan sát cả **latency distribution + successful throughput + responsiveness + utilization/queue/error rate**. Các chỉ số chỉ có nghĩa khi cùng ranh giới, cùng khoảng đo và cùng định nghĩa workload.

## Tự kiểm tra

1. Nếu một request chờ 90 ms trong queue và chạy 30 ms, latency từ lúc submit là bao nhiêu? Vì sao không nên báo 30 ms như toàn bộ latency?
2. Hệ thống ổn định hoàn thành 200 task/s và latency trung bình 150 ms. Theo Little, trung bình có bao nhiêu task đang ở trong hệ thống? (Đáp án: `200 × 0,150 = 30`.)
3. Pool có 100% active worker nhưng CPU tiến trình chỉ 15%. Bạn sẽ kiểm tra những thời gian chờ nào trước khi tăng pool?
4. Tại sao không thể cộng p99 của hai bước xử lý để có chính xác p99 của toàn luồng?
5. Với workload CPU-bound trên 4 core, cần quan sát gì trước khi đổi từ 4 sang 64 worker?

**Ghi nhớ:** latency đo **thời gian cho một công việc**, throughput đo **số công việc hoàn thành theo thời gian**, responsiveness đo **khả năng phản hồi kịp thời**, còn utilization đo **mức bận của tài nguyên cụ thể**. Tối ưu concurrency là quản lý quan hệ giữa các chỉ số, bottleneck và mức tải, không phải tối đa một chỉ số độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-005">Concurrency trong ứng dụng Java: use case và rủi ro</a>

<details>
<summary>Xem chi tiết</summary>

# Concurrency trong ứng dụng Java: use case và rủi ro

## 1. Vì sao ứng dụng Java cần concurrency?

**Concurrency (xử lý đồng thời)** là cách tổ chức nhiều công việc có thể cùng tiến triển trong một khoảng thời gian. Chúng có thể luân phiên trên một lõi CPU hoặc chạy thật sự cùng lúc trên nhiều lõi. **Parallelism (xử lý song song)** là trường hợp các công việc thực sự thực thi đồng thời; **asynchrony (bất đồng bộ)** là cách gọi công việc rồi nhận kết quả ở thời điểm khác, không bắt buộc tạo một luồng riêng hoặc chạy song song.

Ứng dụng thực tế thường xử lý nhiều yêu cầu, thao tác I/O và tác vụ nền. Nếu tất cả phải đợi nhau theo một chuỗi tuần tự, thời gian chờ mạng, đĩa hay cơ sở dữ liệu có thể khiến tài nguyên CPU bị nhàn rỗi. Concurrency cho phép công việc khác tiến triển trong lúc một công việc đang chờ; với bài toán tính toán có thể phân chia độc lập, parallelism còn giúp tận dụng nhiều lõi CPU.

Tuy nhiên, **thêm thread không tự động làm chương trình nhanh hơn**. Mức độ hữu ích phụ thuộc vào loại tác vụ, chi phí phối hợp, giới hạn tài nguyên và khả năng chịu tải của hệ thống phía sau.

### Thuật ngữ cần phân biệt

| Khái niệm | Ý nghĩa | Ví dụ Java |
| --- | --- | --- |
| Task | Đơn vị công việc có đầu vào/kết quả riêng | `Runnable`, `Callable<T>` |
| Thread | Luồng thực thi của JVM, có ngăn xếp và lịch chạy | `Thread` |
| Concurrency | Nhiều task có tiến triển đan xen | Executor chạy các request/task |
| Parallelism | Nhiều task thật sự chạy đồng thời trên các lõi | Xử lý nhiều phần dữ liệu độc lập |
| Asynchrony | Kết quả được xử lý sau khi công việc hoàn tất | `CompletableFuture`, callback |
| Thread safety | Tính đúng đắn khi đối tượng được dùng từ nhiều thread | Trạng thái bất biến, khóa, atomic |

Một công việc chờ I/O và một công việc tính toán là hai loại tải khác nhau. Không nên dùng chung một cấu hình concurrency cho mọi trường hợp.

## 2. Những use case phù hợp

### 2.1. Phục vụ nhiều yêu cầu độc lập

Ứng dụng mạng có thể nhận đồng thời nhiều kết nối, lời gọi hoặc thông điệp. Trong mô hình thread pool, các task xử lý độc lập được chuyển cho một số worker thread; worker hoàn tất công việc này rồi lấy công việc tiếp theo. Mục tiêu là **duy trì độ đáp ứng và thông lượng** khi nhiều người dùng cùng truy cập.

Điều kiện cần: dữ liệu chia sẻ phải an toàn, số task đang chờ phải được giới hạn, và tài nguyên phụ thuộc (kết nối mạng, database, API bên ngoài) phải chịu được tải tương ứng. Một hệ thống nhận 1.000 task không có nghĩa có thể đồng thời mở 1.000 kết nối tới mọi dịch vụ phụ thuộc.

### 2.2. Che thời gian chờ I/O

Đọc tệp, gọi HTTP, truy vấn dữ liệu hoặc đợi thông điệp thường mất thời gian vì I/O. Khi một task đang chờ, worker khác có thể phục vụ công việc khác. Ví dụ một trang tổng hợp thông tin độc lập từ dịch vụ A và B có thể thực hiện hai lời gọi cùng lúc rồi chờ cả hai.

Điều kiện quan trọng: hai lời gọi phải **độc lập về nghiệp vụ**. Nếu B cần kết quả A, khởi chạy B song song có thể sai thứ tự hoặc không mang lợi ích. Concurrency cũng không xóa giới hạn kết nối, rate limit, timeout hoặc dung lượng của dịch vụ đích.

### 2.3. Tách tác vụ nền khỏi đường xử lý chính

Ghi nhật ký có kiểm soát, tạo báo cáo, nén tệp, tạo thumbnail hoặc xử lý một hàng đợi là những ví dụ có thể đưa ra khỏi luồng xử lý tương tác. Mục tiêu là giảm thời gian người dùng phải đợi phần việc không cần hoàn thành ngay.

Cần quyết định rõ: tác vụ có được phép mất khi ứng dụng tắt không; có cần lưu bền vững, retry hay bảo đảm không xử lý hai lần không; khi báo lỗi thì ai theo dõi? Việc chỉ gọi `executor.submit(...)` không tạo ra cơ chế giao hàng bền vững. Đối với tác vụ quan trọng, phải thiết kế độ tin cậy ở mức hệ thống.

### 2.4. Pipeline và xử lý theo lô

Một pipeline có thể gồm nhận dữ liệu → xác thực → biến đổi → ghi kết quả. Các giai đoạn chạy đồng thời với một hàng đợi giới hạn, giúp cân bằng tốc độ giữa nguồn vào và đầu ra. Tác vụ phân tích nhiều tệp độc lập cũng có thể chia thành nhiều phần và gom kết quả.

Cần tránh trường hợp giai đoạn trước sinh dữ liệu nhanh hơn nhiều so với giai đoạn sau. Khi đó hàng đợi tăng, gây tốn RAM và độ trễ; giải pháp là **backpressure** hoặc chặn/từ chối bớt công việc, không phải tiếp tục thêm thread vô hạn.

### 2.5. Tính toán trên nhiều lõi CPU

Bài toán như phân tích tập dữ liệu lớn, mã hóa/biến đổi khối độc lập hoặc tính toán nhiều phần tử có thể chia task để dùng nhiều lõi. `ForkJoinPool`, `parallelStream()` hoặc executor thích hợp có thể hữu ích nếu mỗi task đủ lớn và không tranh chấp trạng thái.

Giới hạn tăng tốc: phần việc không thể song song hóa, chuyển đổi dữ liệu, cache/memory bandwidth, ghép kết quả, lock và context switch. Chia một phép tính rất nhỏ thành hàng nghìn task thường còn chậm hơn chạy tuần tự.

### 2.6. Lập lịch và xử lý sự kiện

`ScheduledExecutorService` phù hợp với công việc định kỳ hoặc trì hoãn trong tiến trình Java. Concurrency giúp xử lý nhiều sự kiện độc lập. Tuy nhiên lịch chạy trong JVM không thay thế lịch bền vững: tiến trình dừng thì lịch/tác vụ trong bộ nhớ cũng mất; tác vụ kéo dài có thể làm sai thời điểm chạy sau đó.

## 3. Khi nào nên chạy tuần tự?

Ưu tiên tuần tự nếu tác vụ nhỏ, phụ thuộc chặt chẽ theo thứ tự, cần một phép biến đổi đơn giản trên dữ liệu nhỏ, hoặc không có điểm chờ/khả năng tách việc đáng kể. Chạy tuần tự giúp giữ reasoning và xử lý lỗi đơn giản. Chỉ tăng concurrency sau khi có nhu cầu về **độ trễ, thông lượng hoặc độ đáp ứng** đo được.

Câu hỏi trước khi song song hóa:

1. Các task có thật sự độc lập và có kết quả hợp nhất rõ ràng không?
2. Tải chủ yếu là CPU hay chờ I/O? Có bao nhiêu tài nguyên hữu hạn phía sau?
3. Trạng thái nào được dùng chung, ai sở hữu, ai được ghi?
4. Có timeout, hủy, giới hạn hàng đợi và hành vi khi quá tải không?
5. Đã có phép đo so với phiên bản tuần tự trong cùng điều kiện chưa?

## 4. Các cơ chế Java thường dùng

| Công cụ | Vai trò | Lưu ý khi áp dụng |
| --- | --- | --- |
| `Thread` | Biểu diễn một luồng thực thi | Không tạo thread mới không giới hạn cho mỗi phần việc |
| `Runnable` / `Callable<V>` | Mô tả task; `Callable` trả kết quả hoặc ném exception | Ưu tiên tách task khỏi chính sách cấp phát thread |
| `ExecutorService` | Chạy và quản lý task qua executor | Phải đóng/shutdown có kiểm soát |
| `Future<V>` | Nhận kết quả, lỗi hoặc yêu cầu hủy | `get()` có thể chặn; cần timeout |
| `CompletableFuture` | Phối hợp kết quả bất đồng bộ | Các biến thể `Async` cần hiểu rõ executor sử dụng |
| `BlockingQueue` | Trao đổi dữ liệu giữa producer/consumer | Dùng capacity hữu hạn cho luồng dữ liệu cần backpressure |
| `synchronized` / `Lock` | Bảo vệ một bất biến chung | Giữ vùng khóa nhỏ, nhất quán thứ tự lấy khóa |
| `volatile` | Bảo đảm visibility và quan hệ thứ tự thích hợp cho một biến | Không biến `count++` thành atomic |
| `AtomicInteger` và họ atomic | Các thao tác atomic cho trạng thái đơn lẻ | Không tự bảo vệ bất biến liên quan nhiều biến |
| `ConcurrentHashMap` | Map hỗ trợ truy cập đồng thời | Chuỗi nhiều thao tác vẫn cần xem xét tính nguyên tử |
| `CountDownLatch` / `Semaphore` | Phối hợp hoàn tất / giới hạn số công việc vào vùng tài nguyên | Không dùng `Thread.sleep` để giả lập đồng bộ hóa |
| `ForkJoinPool` | Work-stealing cho task chia nhỏ, thường hướng CPU | Tránh chặn I/O kéo dài trong workload tính toán |
| Virtual threads (Java 21+) | Phục vụ nhiều task chờ I/O theo mô hình thread-per-task | Không làm CPU nhanh hơn; vẫn giới hạn tài nguyên ngoài JVM |

`ExecutorService`, `Future` và `CompletableFuture` đều dùng được ở Java 17. **Virtual threads là tính năng chính thức từ Java 21**; đoạn mã Java 17 bên dưới không phụ thuộc tính năng này.

## 5. Ví dụ 1 — nhiều task độc lập và gom kết quả an toàn (Java 17+)

Giả sử có ba tác vụ I/O độc lập; `Thread.sleep` chỉ **mô phỏng thời gian chờ** để ví dụ có thể chạy ở mọi máy, không phải cách thực hiện I/O thật.

Tạo tệp `ConcurrentWorkDemo.java`:

~~~java
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentWorkDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        AtomicInteger completed = new AtomicInteger();

        try {
            List<Callable<String>> jobs = List.of(
                () -> {
                    Thread.sleep(80);
                    completed.incrementAndGet();
                    return "A";
                },
                () -> {
                    Thread.sleep(40);
                    completed.incrementAndGet();
                    return "B";
                },
                () -> {
                    Thread.sleep(60);
                    completed.incrementAndGet();
                    return "C";
                }
            );

            List<Future<String>> results = pool.invokeAll(jobs);
            for (Future<String> result : results) {
                System.out.println(result.get());
            }
            System.out.println("Completed: " + completed.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ConcurrentWorkDemo.java
java ConcurrentWorkDemo
~~~

Đầu ra:

~~~text
A
B
C
Completed: 3
~~~

Giải thích:

- `invokeAll` gửi ba task rồi chờ chúng hoàn tất; danh sách `Future` trả về **theo thứ tự task đầu vào**, không theo thứ tự task hoàn thành. Vì vậy luôn in A, B, C nếu các task hoàn tất bình thường.
- `AtomicInteger.incrementAndGet()` tăng đếm nguyên tử; ba task không làm thất lạc lần cập nhật.
- `result.get()` lấy kết quả hoặc phát sinh `ExecutionException` nếu task lỗi. `invokeAll` bản này chờ vô thời hạn nếu task không kết thúc; cần timeout ở trường hợp có rủi ro kẹt.
- `shutdown()` ngừng nhận task mới, cho task đang có hoàn tất; `awaitTermination` chờ có giới hạn. `shutdownNow()` là yêu cầu ngắt/thu hồi task, **không bảo đảm** task bị dừng nếu nó bỏ qua interrupt.

Ví dụ này minh họa concurrency và phối hợp kết quả, chưa chứng minh rằng ứng dụng thực sẽ nhanh hơn. Muốn kiểm chứng, đo latency, throughput và mức tài nguyên với phiên bản tuần tự trên tải tương đương.

## 6. Rủi ro đúng đắn: shared mutable state

### 6.1. Race condition và mất cập nhật

Khi nhiều thread cùng đọc/ghi trạng thái có thể thay đổi mà không đồng bộ, kết quả có thể phụ thuộc thứ tự xen kẽ. Ví dụ thao tác `count++` gồm đọc giá trị cũ, cộng 1, ghi giá trị mới; hai thread có thể cùng đọc một giá trị và một lần cộng bị mất.

~~~java
// MINH HỌA lỗi; không phải chương trình độc lập.
int count = 0;
// Hai thread cùng chạy:
count++; // Không atomic và không bảo đảm thread-safe
~~~

Giải pháp tùy ngữ cảnh: tránh chia sẻ dữ liệu; dùng đối tượng bất biến; giới hạn quyền sở hữu một thread; hoặc bảo vệ cập nhật bằng `synchronized`, `Lock`, các lớp atomic. Không chọn công cụ chỉ theo tốc độ; trước tiên xác định **bất biến** phải luôn đúng (chẳng hạn tổng số dư hai tài khoản không đổi).

### 6.2. Visibility và Java Memory Model

Đôi khi thread A đã ghi nhưng thread B chưa quan sát được như kỳ vọng do tối ưu hóa compiler/JIT, cache và thiếu quan hệ đồng bộ. **Java Memory Model (JMM)** quy định các quan hệ **happens-before** để xác định khi nào hành động của thread này được bảo đảm nhìn thấy ở thread khác.

Các ví dụ quan trọng: unlock của monitor happens-before lần lock tiếp theo trên cùng monitor; một lần ghi `volatile` happens-before lần đọc tiếp theo của cùng biến đó theo thứ tự đồng bộ; các cơ chế executor/`Future` cũng có bảo đảm nhất định về việc công bố kết quả. `Thread.start()` và `Thread.join()` có các bảo đảm thứ tự tương ứng.

~~~java
// MINH HỌA: một cờ dừng đơn giản, chỉ có một biến trạng thái.
private volatile boolean running = true;

// Thread khác gọi:
void stop() {
    running = false;
}

// Worker kiểm tra:
void work() {
    while (running) {
        // Thực hiện công việc ngắn, có thể kiểm tra cờ đều đặn.
    }
}
~~~

`volatile` phù hợp cho cờ trạng thái đơn giản khi không có bất biến nhiều bước; nó **không** bảo đảm cập nhật gộp kiểu `balance = balance - amount` hoặc `count++`. Vòng lặp trống cũng có thể tiêu CPU: hệ thống thật thường dùng cơ chế chờ có thể bị ngắt hoặc tín hiệu phối hợp.

### 6.3. Atomicity chưa đủ cho bất biến nhiều biến

`AtomicInteger` làm một lần tăng atomic nhưng không tự bảo đảm rằng hai biến liên quan luôn nhất quán. Ví dụ chuyển tiền phải bảo vệ đồng thời kiểm tra đủ số dư, trừ tài khoản nguồn và cộng tài khoản đích theo một giao thức nhất quán. Tương tự, thao tác `containsKey` rồi `put` trên concurrent map vẫn có thể bị thread khác xen vào; cân nhắc `putIfAbsent`, `compute` hoặc transaction/lock thích hợp.

### 6.4. Đăng tải đối tượng không an toàn

Đưa tham chiếu tới một đối tượng đang khởi tạo dở cho thread khác, hoặc công bố cấu trúc có thể thay đổi mà thiếu đồng bộ, khiến thread đọc thấy trạng thái chưa đầy đủ. Ưu tiên khởi tạo xong trước khi chia sẻ, trường `final` phù hợp, giá trị bất biến và cấu trúc concurrent; không để `this` thoát khỏi constructor qua callback/thread tự chạy.

## 7. Rủi ro tiến triển: deadlock, livelock, starvation

**Deadlock:** các thread đợi vòng tròn nhau (A giữ khóa 1 cần khóa 2; B giữ khóa 2 cần khóa 1), không thread nào tiến triển. Cách phòng tránh: quy định thứ tự lấy khóa thống nhất, tránh giữ lock khi chờ mạng/I/O, giữ critical section ngắn, sử dụng `tryLock` với timeout khi phù hợp. Timeout giúp phát hiện hoặc phục hồi một số tình huống nhưng không tự sửa thứ tự khóa sai.

**Livelock:** các thread không bị chặn cứng nhưng liên tục nhường/đổi trạng thái cho nhau nên không có công việc hữu ích hoàn tất. Điều chỉnh chiến lược retry với backoff/jitter, đặt giới hạn và xem xét phân phối quyền tiến hành.

**Starvation:** một task liên tục không được cấp CPU/lock/worker vì task khác chiếm tài nguyên. Ví dụ task dài hoặc blocking làm ngập một pool nhỏ; các task khác cứ nằm trong hàng đợi. Cần phân loại workload, giới hạn thời lượng, cân bằng ưu tiên và đo queue wait time.

**Thread-pool deadlock:** task A chiếm worker duy nhất, gửi B vào **cùng pool** rồi gọi `B.get()`; B không có worker để chạy nên A chờ mãi. Tránh chờ một task phụ thuộc trên chính executor đã cạn worker; cân nhắc composition không blocking hoặc thiết kế lại phân rã công việc.

## 8. Rủi ro tài nguyên và khả năng chịu tải

### 8.1. Quá nhiều thread hoặc task

Thread nền tảng tiêu tốn bộ nhớ ngăn xếp và chi phí lập lịch. Khi số thread vượt nhu cầu, context switch và cạnh tranh khóa làm giảm thông lượng. Ngược lại, pool quá nhỏ cho các task chờ I/O có thể khiến hàng đợi tăng dù CPU còn rảnh.

**Queue không giới hạn** là rủi ro riêng: số worker cố định nhưng số task chờ có thể tăng theo tốc độ đầu vào; độ trễ và bộ nhớ phình ra, cuối cùng có thể gây `OutOfMemoryError`. Trong nhiều overload scenario, trả lỗi nhanh có kiểm soát tốt hơn tích lũy việc vô hạn.

### 8.2. Bounded queue và chính sách quá tải

Một cấu hình minh họa (không phải chương trình độc lập):

~~~java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    2, 2,
    0L, TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue<>(8),
    new ThreadPoolExecutor.AbortPolicy()
);
~~~

Cần thêm import từ `java.util.concurrent` khi đưa đoạn trên vào chương trình. Trong trường hợp này, tối đa hai task được thực thi và tối đa tám task nằm chờ trong queue tại cùng thời điểm; khi bão hòa và không thể nhận thêm, `execute` có thể ném `RejectedExecutionException`. Phải thiết kế cách xử lý lỗi, retry có giới hạn hoặc backpressure ở phía gọi. `CallerRunsPolicy` có thể làm thread gửi task trực tiếp chạy task, từ đó gây chậm phía gửi; dùng nó chỉ sau khi hiểu thread gửi đang phục vụ việc gì.

Số worker và queue capacity là **tham số vận hành**, không có một bộ số đúng cho mọi ứng dụng. Đo các chỉ số: worker active, pool size, queue depth, queue wait time, rejection, thời gian task, CPU, bộ nhớ và latency đầu cuối.

### 8.3. Phụ thuộc bên ngoài là nút thắt

Tăng số task gọi một API có rate limit, database connection pool nhỏ hoặc ổ đĩa chậm thường chỉ tăng hàng chờ/timeout. Dùng `Semaphore` hoặc cơ chế tương tự để đặt giới hạn tác vụ đồng thời theo **năng lực của downstream**, đồng thời áp dụng connection timeout, request timeout và retry có backoff phù hợp. Không retry vô hạn trong cùng thread pool khi downstream đã quá tải.

### 8.4. Blocking lẫn vào CPU pool

Pool thiết kế cho tính toán trên CPU có thể bị cạn worker nếu các task bên trong chờ I/O lâu. Khi đó cả task CPU nhỏ cũng bị kẹt hàng đợi. Tách nhóm công việc theo loại tải và đo lường, hoặc chọn kiểu thực thi thích hợp. Với `CompletableFuture.supplyAsync(...)` không truyền executor, mặc định thường dùng `ForkJoinPool.commonPool()`; cần cân nhắc executor riêng cho công việc blocking để tránh gây ảnh hưởng tới các phần code khác.

## 9. Timeout, cancellation, interruption và lỗi

### 9.1. Timeout không đồng nghĩa dừng task

`Future.get(timeout, unit)` chỉ giới hạn **thời gian thread gọi chờ kết quả**. Nếu timeout, tác vụ phía sau vẫn có thể đang chạy. `cancel(true)` yêu cầu interrupt task đang chạy (nếu có); khả năng dừng phụ thuộc vào việc task có kiểm tra/truyền interrupt và API đang dùng có đáp ứng interrupt không.

Một mẫu minh họa xử lý timeout (Java 17+):

~~~java
import java.util.concurrent.*;

public class TimeoutDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<String> future = pool.submit(() -> {
            Thread.sleep(1000);
            return "done";
        });

        try {
            System.out.println(future.get(50, TimeUnit.MILLISECONDS));
        } catch (TimeoutException ex) {
            future.cancel(true);
            System.out.println("Timed out; cancellation requested");
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac TimeoutDemo.java
java TimeoutDemo
~~~

Khi task vẫn đang chờ `Thread.sleep(1000)`, đầu ra thường là:

~~~text
Timed out; cancellation requested
~~~

Đây là kết quả điển hình, **không phải cam kết tuyệt đối của lịch chạy**: sau một khoảng trì hoãn lập lịch bất thường, task có thể đã hoàn tất trước lúc thread gọi thực thi `get`. Thời gian và hành vi hủy trong chương trình thật cần được kiểm chứng. `shutdownNow()` cũng chỉ cố gắng interrupt, không phải thao tác giết thread cưỡng bức.

### 9.2. Xử lý interrupt đúng cách

Khi một thao tác chặn ném `InterruptedException`, thread đang được yêu cầu dừng/chuyển trạng thái. Nếu không thể ném tiếp exception, thường nên khôi phục cờ interrupt bằng `Thread.currentThread().interrupt()` rồi kết thúc công việc an toàn. Không nuốt interrupt và tiếp tục vòng lặp vô hạn.

~~~java
// MINH HỌA bên trong một Runnable, không phải chương trình độc lập.
try {
    queue.take();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
~~~

### 9.3. Lỗi trong task và phạm vi hủy

Với `submit`, exception trong task được giữ trong `Future` và thường được nhìn thấy qua `get()` dưới dạng `ExecutionException`; nếu bỏ qua `Future`, lỗi nghiệp vụ có thể không được xử lý như dự kiến. Với `CompletableFuture` cần quan sát nhánh exceptional và quyết định fallback hay lan truyền lỗi. Khi một nhánh của tập công việc thất bại, xác định các nhánh còn lại có nên bị hủy hay tiếp tục.

Không chỉ cấu hình timeout ở tầng gọi. Phải thống nhất **deadline toàn luồng xử lý** và ngân sách từng bước; ba lần chờ 5 giây nối tiếp có thể tạo độ trễ 15 giây. Retry phải hữu hạn, có điều kiện, và phù hợp với tính idempotent của tác vụ.

### 9.4. Dừng ứng dụng có kiểm soát

Khi ngừng nhận việc, nên chuyển executor sang `shutdown`, chờ các task hiện tại trong một giới hạn, rồi mới `shutdownNow` nếu cần. Cần biết tác vụ chưa hoàn tất có được lưu để chạy lại hay không. Không phụ thuộc vào việc dừng JVM sẽ tự hoàn thành hoặc lưu lại những task chỉ nằm trong bộ nhớ.

## 10. Thread safety, context và công việc phụ thuộc

Không phải mọi đối tượng Java đều an toàn khi được chia sẻ giữa các thread. Xem hợp đồng của API: nhiều kiểu như `String` là bất biến; các collection thông thường như `ArrayList` không bảo đảm thread-safe khi vừa đọc vừa sửa từ nhiều thread. Dùng cấu trúc concurrent hoặc giới hạn quyền truy cập phù hợp.

`ThreadLocal` gắn dữ liệu với **thread cụ thể**, không tự động truyền giá trị sang worker thread khác. Thread pool tái sử dụng worker, nên dữ liệu yêu cầu trước có thể vô tình còn lại nếu quên `remove()`. Với thông tin như correlation ID, user context hay locale, truyền tường minh qua tham số/context object hoặc dùng cơ chế propagation có hợp đồng rõ ràng; tuyệt đối không mặc định context của thread gọi sẽ tự đến task mới.

Tính đúng đắn còn phụ thuộc thứ tự nghiệp vụ: nếu tác vụ B cần tác vụ A hoàn thành thì phải biểu diễn dependency (chờ có giới hạn, ghép `CompletableFuture` hoặc pipeline tuần tự), không dựa vào thứ tự thread được lên lịch. Trong những thao tác có side effect (ghi file, gửi yêu cầu, cập nhật dữ liệu), retry/cancellation có thể tạo xử lý trùng; thiết kế idempotency và phạm vi giao dịch thích hợp.

## 11. Virtual threads trong Java 21+: nên dùng khi nào?

Virtual threads do JVM quản lý với chi phí tạo và đợi thấp hơn platform threads trong nhiều tác vụ blocking I/O. Chúng phù hợp khi muốn viết kiểu mã đồng bộ, dễ đọc, trong khi phải phục vụ số lượng lớn công việc thường xuyên **chờ I/O**.

Ví dụ ngắn dành riêng cho Java 21+ (mã minh họa API, không dành cho Java 17):

~~~java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<String> future = executor.submit(() -> "result");
    System.out.println(future.get());
}
~~~

Nhập `java.util.concurrent.Executors` và `java.util.concurrent.Future` khi dùng đoạn mã trong một lớp hoàn chỉnh. Virtual threads không tăng số lõi, không làm một thuật toán CPU-bound nhanh hơn, và không tăng giới hạn số kết nối database/API. Đừng dùng pool số lượng nhỏ virtual threads như cơ chế chặn truy cập tài nguyên: dùng `Semaphore` hoặc giới hạn tài nguyên cụ thể. Cũng cần đo chi phí của blocking, pinning và mức độ tương thích của thư viện trên phiên bản JVM đang triển khai.

**Lưu ý phạm vi:** Các mẫu concurrency Java ở đây không phụ thuộc Spring; việc cấu hình executor/async trong Spring thuộc chương riêng.

## 12. Thiết kế, thử nghiệm và quan sát

### Thiết kế trước khi code

1. **Xác định mục tiêu đo được:** throughput, p95/p99 latency, số request đồng thời, hoặc thời gian hoàn thành batch.
2. **Phân loại task:** CPU-bound, I/O-bound, có blocking hay không, có thứ tự/phụ thuộc hay không.
3. **Liệt kê trạng thái chia sẻ:** đối tượng nào bất biến, đối tượng nào thuộc một thread, bất biến nào phải giữ bằng khóa/atomic/transaction.
4. **Đặt giới hạn:** worker concurrency, queue capacity, connection limits, deadline, retry budget và chính sách rejection.
5. **Xác định vòng đời:** task thành công, lỗi, timeout, interrupt, hủy, shutdown, restart và khả năng xử lý lại.
6. **Đo trên tải thực tế:** so sánh phiên bản tuần tự/đồng thời, theo dõi tài nguyên phía JVM và dịch vụ phụ thuộc.

### Các phép thử có giá trị

- Tạo nhiều task cập nhật cùng trạng thái và kiểm tra **bất biến** (không chỉ kiểm tra chương trình không ném exception).
- Dùng `CountDownLatch` hoặc barrier để chủ động cho các thread bắt đầu cùng lúc; lặp lại stress test với nhiều lịch xen kẽ.
- Mô phỏng dependency chậm, lỗi, timeout, queue đầy, task bị interrupt, shutdown giữa chừng; xác nhận fallback, số task còn lại và hành vi từ chối.
- Dùng phép đo đủ lâu với warm-up hợp lý của JVM; phân biệt latency, throughput và CPU, tránh suy kết luận hiệu năng chỉ từ một lần chạy.
- Với lỗi khó tái hiện, thu thập thread dump, Java Flight Recorder (JFR), log có correlation ID và các metric pool/queue/lock. Đọc thread state và lock owner để xác định chuỗi chờ.
- Tránh viết test dựa vào `Thread.sleep(100)` với giả định “đủ lâu thì thread khác chắc chắn xong”; scheduler không bảo đảm điều đó. Dùng primitive đồng bộ hoặc chờ có timeout.

### Dấu hiệu cần điều tra

| Hiện tượng | Giả thuyết cần kiểm tra | Hướng chẩn đoán |
| --- | --- | --- |
| Kết quả lúc đúng lúc sai | Race, công bố dữ liệu sai, bất biến không được bảo vệ | Stress test, rà soát shared mutable state/JMM |
| Request treo dù CPU thấp | Deadlock, phụ thuộc nội bộ pool, blocking downstream | Thread dump, call stack, lock/wait graph |
| Latency tăng dần theo tải | Queue không giới hạn, tài nguyên downstream bão hòa | Queue depth, wait time, connection-pool metrics |
| CPU cao nhưng throughput không tăng | Contention, context switch, retry loop/livelock | CPU profiling, JFR, retry counters |
| Task nền mất hoặc không báo lỗi | Future bị bỏ qua, shutdown đột ngột, job chỉ nằm trong RAM | Log lỗi, task lifecycle, thiết kế persistence |
| Dữ liệu request bị lẫn | ThreadLocal không dọn, trạng thái chia sẻ sai | Kiểm tra propagation và `remove()` |

## 13. Sai lầm phổ biến và checklist cuối

- **“Càng nhiều thread càng nhanh”** — sai khi overhead, lock contention và downstream limit chiếm ưu thế.
- **“Dùng volatile là thread-safe”** — sai với cập nhật gộp hoặc bất biến nhiều biến.
- **“Collection concurrent làm toàn bộ luồng nghiệp vụ atomic”** — sai nếu nghiệp vụ gồm nhiều thao tác độc lập.
- **“get(timeout) sẽ giết task”** — sai; chỉ giới hạn thời gian chờ của caller.
- **“Đã submit thì tự động có retry/durability”** — sai; in-memory executor không bảo đảm lưu bền.
- **“Executor tự quản lý vòng đời”** — sai; ứng dụng phải định nghĩa shutdown và quan sát lỗi.
- **“Virtual threads giải quyết mọi bài toán concurrency”** — sai; race, deadlock và giới hạn tài nguyên vẫn tồn tại.

Trước khi đưa concurrency vào production, hãy xác nhận: task độc lập ở mức nghiệp vụ; shared state có cơ chế bảo vệ rõ ràng; queue/worker/downstream được giới hạn; mọi lời gọi chờ có deadline; lỗi và interrupt được xử lý; shutdown có kế hoạch; chỉ số hiệu năng và dấu hiệu nghẽn có thể quan sát được.

**Kết luận học tập:** Concurrency là công cụ cải thiện độ đáp ứng và khả năng sử dụng tài nguyên khi nhiều công việc có thể tiến triển độc lập. Giá trị lớn nhất đến từ việc **chọn đúng use case, xác định đúng giới hạn, và bảo toàn tính đúng đắn dưới mọi thứ tự thực thi**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-006">Shared mutable state, coordination và thread confinement</a>

<details>
<summary>Xem chi tiết</summary>

# Shared mutable state, coordination và thread confinement trong Java

Khi nhiều thread cùng xử lý công việc, vấn đề khó thường không nằm ở việc **tạo thread** mà ở câu hỏi **ai được quyền đọc, sửa và công bố dữ liệu; các thread chờ nhau theo quy tắc nào**. Ba khái niệm trọng tâm của bài là:

- **Shared mutable state:** trạng thái có thể thay đổi và có thể được nhiều thread truy cập, ví dụ bộ đếm dùng chung, số dư, cache, danh sách công việc.
- **Coordination:** cơ chế phối hợp tiến độ và trao đổi tín hiệu giữa các thread hoặc tác vụ, ví dụ đợi kết quả, báo hoàn thành, chuyển việc qua queue, giới hạn số tác vụ.
- **Thread confinement:** thiết kế để một trạng thái mutable chỉ được một thread sở hữu và thao tác tại một thời điểm, qua đó tránh phải đồng bộ trên trạng thái đó.

Ba khái niệm này liên quan nhưng không thay thế nhau: một `CountDownLatch` có thể khiến worker đợi đúng lúc nhưng **không tự làm phép `counter++` thành atomic**; một `synchronized` bảo vệ trạng thái chung nhưng không tự giải quyết cách dừng một pipeline; còn một biến khai báo bên trong phương thức **không thực sự confined** nếu tham chiếu của nó được chuyển cho thread khác.

> Phạm vi: các cơ chế concurrency của **Java nói chung** (Java Memory Model, `java.lang`, `java.util.concurrent`). Không bàn đến cơ chế xử lý concurrency riêng của Spring Framework.

## 1. Nhận diện shared mutable state

Một dữ liệu trở thành **shared** khi cùng một đối tượng hoặc vị trí nhớ có thể được truy cập từ nhiều thread. Nó là **mutable** khi nội dung có thể bị thay đổi sau khi được tạo. Nếu đồng thời thỏa cả hai điều kiện, thiết kế cần quy định cách truy cập.

Ví dụ:

```java
final class Metrics {
    int processed; // Mutable, có thể shared khi Metrics được dùng ở nhiều thread.
}
```

Biến `processed` không tự an toàn chỉ vì nó nằm trong một đối tượng Java. Nếu cùng một instance `Metrics` được nhiều worker truy cập, mỗi worker có thể đọc và ghi trường này.

Cần phân biệt:

| Trường hợp | Có shared mutable state? | Cách nghĩ đúng |
| --- | --- | --- |
| Mỗi task tạo một `StringBuilder` riêng và không chia sẻ nó | Không | Mỗi task sở hữu dữ liệu của mình |
| Nhiều thread đọc một `String` đã được công bố an toàn | Có shared, nhưng immutable | Không cần khóa để bảo vệ thay đổi của `String` vì nó không đổi |
| Nhiều thread cùng tăng `int count` của một instance | Có | Cần đảm bảo atomicity và visibility |
| Nhiều thread cùng ghi một `ArrayList` | Có | `ArrayList` không tự bảo đảm thread safety |
| Một thread cập nhật `HashMap`, thread khác đọc cùng map | Có | Cần publication, visibility và bảo vệ cấu trúc dữ liệu |
| Nhiều thread nhận cùng tham chiếu `AtomicInteger` và gọi `incrementAndGet()` | Có | Phép tăng trên biến atomic được tuyến tính hóa; logic nhiều bước vẫn cần phân tích |

**Vị trí khai báo không tự quyết định quyền sở hữu.** Một trường `static` thường dễ trở thành shared, nhưng field instance cũng shared nếu instance đi qua nhiều thread. Tham chiếu cục bộ như `List<T> local` có thể trỏ tới một list dùng chung; ngược lại, field thuộc một đối tượng chỉ thread duy nhất dùng có thể hoàn toàn confined.

### Ba nhóm lỗi cần tách biệt

1. **Atomicity (tính nguyên tử):** một thao tác logic có thực hiện như một bước không thể bị xen giữa hay không?
2. **Visibility (khả năng quan sát):** khi thread A cập nhật trạng thái, thread B có được bảo đảm nhìn thấy bản cập nhật theo quy tắc cần thiết không?
3. **Ordering (thứ tự quan sát):** các thao tác đọc/ghi có quan hệ thứ tự được Java Memory Model bảo đảm hay không?

Một cơ chế có thể giải quyết một phần nhưng không giải quyết tất cả. Chẳng hạn `volatile int count` hỗ trợ visibility và thứ tự đối với truy cập volatile, nhưng `count++` vẫn gồm nhiều thao tác logic nên chưa atomic.

## 2. Data race và lost update: vì sao `count++` nguy hiểm?

Giả sử hai thread cùng thực hiện `count++`. Thao tác có thể được hiểu ở mức logic như sau:

```text
count = 10

Thread A                         Thread B
đọc count -> 10
                                 đọc count -> 10
tính 10 + 1
                                 tính 10 + 1
ghi count = 11
                                 ghi count = 11

Kết quả: 11, dù đã có hai lần tăng; kết quả mong muốn: 12.
```

Đây là ví dụ **lost update**: các bước đọc, tính và ghi bị xen kẽ. Lịch thực thi trên chỉ là một lịch có thể xảy ra, không phải lịch Java luôn chọn.

```java
final class UnsafeCounter {
    private int count;

    void increment() {
        count++; // Read-modify-write, không atomic.
    }

    int get() {
        return count; // Không có bảo đảm visibility khi đọc từ thread khác.
    }
}
```

Dù thay `int` thành `volatile int`, hai thread vẫn có thể đọc cùng giá trị trước khi cùng ghi; `volatile` không biến chuỗi read-modify-write thành một thao tác atomic.

Nếu nhiều thread truy cập cùng biến, có ít nhất một thao tác ghi, và các truy cập xung đột thiếu quan hệ **happens-before** cần thiết, chương trình có **data race** theo Java Memory Model. Hệ quả không chỉ là mất lần tăng: chương trình cũng có thể đọc trạng thái cũ hoặc quan sát các trường liên quan theo cách không phù hợp với bất biến (invariant) thiết kế.

**Invariant** là điều kiện phải đúng ở các thời điểm có thể quan sát hợp lệ, ví dụ `balance >= 0` hoặc `available + reserved == capacity`. Bảo vệ từng field riêng lẻ chưa chắc bảo vệ được invariant liên quan nhiều field.

## 3. Java Memory Model: thế nào là nhìn thấy dữ liệu an toàn?

Java Memory Model (JMM) định nghĩa các bảo đảm về thứ tự và visibility giữa những hành động trên các thread. Khái niệm then chốt là **happens-before** (HB): nếu hành động X happens-before hành động Y, các hiệu ứng bộ nhớ của X phải được quan sát tại Y theo quy tắc của mô hình bộ nhớ, với điều kiện không bị các ghi hợp lệ khác thay thế.

Các quan hệ quan trọng:

| Quan hệ | Bảo đảm nổi bật |
| --- | --- |
| Thứ tự chương trình trong cùng thread | Hành động trước theo program order happens-before hành động sau trong thread đó |
| `Thread.start()` | Các hành động trước khi gọi `start()` happens-before hành động trong thread vừa khởi động |
| `Thread.join()` thành công | Các hành động của thread được đợi happens-before việc thread gọi tiếp tục sau `join()` |
| Monitor (`synchronized`) | Unlock trên monitor happens-before một lần lock tiếp theo trên **cùng** monitor |
| Truy cập `volatile` | Một volatile write happens-before volatile read tiếp theo theo synchronization order đối với cùng biến |
| `ExecutorService.submit` và `Future.get` | Hành động trước khi gửi task happens-before hành động trong task; hành động task happens-before các hành động sau khi lấy kết quả bằng `Future.get()` |
| `BlockingQueue` | Hành động trước khi đưa một phần tử vào queue happens-before hành động sau khi thread khác lấy phần tử tương ứng ra khỏi queue |
| `CountDownLatch` | Hành động trước `countDown()` happens-before hành động sau `await()` trả về thành công tương ứng |

Ví dụ: dữ liệu được khởi tạo trước `thread.start()` có quan hệ visibility tới thread con. Nếu thread con tính kết quả và thread chính `join()` thành công, thread chính đọc được kết quả đã công bố qua quan hệ HB đó. Đây là một lý do có thể đọc kết quả của thread sau `join` ngay cả khi giá trị không phải `volatile`, miễn là không có thêm truy cập xung đột từ thread khác.

**HB không đồng nghĩa khóa toàn bộ ứng dụng.** Đây là quan hệ về thứ tự/visibility. Một chương trình có thể tạo quan hệ HB qua khóa, volatile, queue, lifecycle của thread hoặc công cụ đồng bộ phù hợp.

### Safe publication

**Safe publication** nghĩa là đưa tham chiếu đối tượng cho thread khác theo cách bảo đảm thread nhận nhìn thấy trạng thái khởi tạo hợp lệ. Những cách thường dùng gồm công bố qua monitor, volatile reference, cấu trúc concurrent, gửi task đúng lúc qua executor hoặc quan hệ thread start.

Trường `final` có quy tắc khởi tạo đặc biệt khi constructor không để tham chiếu `this` thoát ra ngoài; nhưng **final reference không làm đối tượng được tham chiếu trở thành immutable**:

```java
final class Holder {
    final java.util.List<String> names = new java.util.ArrayList<>();
}
```

Không thể đổi tham chiếu `names`, song vẫn có thể gọi `names.add(...)`. Nếu list được nhiều thread sửa, việc trường là `final` không biến `ArrayList` thành thread-safe.

## 4. Chiến lược ưu tiên: giảm chia sẻ mutable state trước khi dùng khóa

Một thứ tự ra quyết định hữu ích:

1. **Đừng chia sẻ** nếu không có nhu cầu: dùng biến cục bộ, đối tượng riêng cho mỗi task, hoặc mỗi worker giữ trạng thái riêng.
2. **Chia sẻ dữ liệu immutable** qua safe publication khi có thể: ví dụ cấu hình dạng `record` chứa các giá trị immutable.
3. **Trao đổi dữ liệu qua queue/message** thay vì cho nhiều worker cùng sửa một collection.
4. **Dùng thao tác atomic** khi invariant nằm gọn trong một biến và API atomic hỗ trợ đúng thao tác.
5. **Dùng lock hoặc cấu trúc đồng bộ bậc cao** khi nhiều bước, nhiều trường phải cùng thỏa invariant.

Không có thứ tự cứng cho mọi kiến trúc. Quyết định phụ thuộc vào yêu cầu về tính đúng, độ trễ, thông lượng, khối lượng công việc và sự dễ hiểu khi bảo trì.

### Immutable snapshot

Một cách thiết kế là xây dựng kết quả trong một thread rồi tạo giá trị immutable để chuyển cho thread khác:

```java
record Report(String name, long total) {}

static Report createReport(String name, long total) {
    return new Report(name, total);
}
```

Ở đây `String`, `long` và `record` không chứa cấu trúc mutable nội bộ. Nhưng record **không tự deep-immutable**: nếu record chứa `List<String>`, nên tạo snapshot như `List.copyOf(input)` và bảo đảm bản thân các phần tử trong list cũng phù hợp với yêu cầu bất biến.

Khi cần thay toàn bộ cấu hình, thường dễ hiểu hơn nếu thay một **immutable snapshot** qua một tham chiếu được công bố an toàn thay vì sửa tuần tự nhiều field của một đối tượng dùng chung.

## 5. Bảo vệ shared state với `synchronized`

`synchronized` dùng **intrinsic monitor lock** của đối tượng. Tại cùng thời điểm, nhiều nhất một thread giữ được cùng monitor; thread khác cần lock đó phải chờ. Việc unlock rồi lock cùng monitor cũng tạo quan hệ happens-before.

Ví dụ độc lập, chạy được với Java 17+:

Lưu thành `SynchronizedCounterDemo.java`:

```java
public class SynchronizedCounterDemo {
    static final class Counter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int get() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        Runnable work = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.increment();
            }
        };

        Thread first = new Thread(work);
        Thread second = new Thread(work);

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println(counter.get());
    }
}
```

Biên dịch và chạy:

```bash
javac SynchronizedCounterDemo.java
java SynchronizedCounterDemo
```

Stdout chính xác:

```text
200000
```

Hai thread cùng tăng **một** counter; `increment()` không bị xen kẽ tại bước `value++` vì cả hai cùng phải giữ monitor của `counter`. `get()` cũng dùng monitor đó để đọc trạng thái nhất quán với các thao tác được bảo vệ. Hai `join()` bảo đảm thread chính đợi cả hai worker hoàn tất trước khi in.

**Lock phải bảo vệ toàn bộ invariant**, không chỉ một thao tác ghi đơn lẻ. Ví dụ trừ số dư cần kiểm tra điều kiện và cập nhật **trong cùng critical section**:

```java
final class Wallet {
    private int balance;

    Wallet(int initialBalance) {
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Negative initial balance");
        }
        balance = initialBalance;
    }

    synchronized boolean withdraw(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Negative amount");
        }
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }

    synchronized int balance() {
        return balance;
    }
}
```

Nếu tách `if (balance >= amount)` và `balance -= amount` thành hai lần khóa riêng, thread khác có thể đổi `balance` giữa hai bước, phá invariant. Khóa phải nhất quán: không được có đường truy cập khác sửa `balance` mà bỏ qua cùng quy tắc bảo vệ.

**Lưu ý:** phương thức `synchronized` trên instance khóa `this`; phương thức `static synchronized` khóa đối tượng `Class`. Hai monitor này khác nhau. Khóa nhầm object thì không mang lại mutual exclusion mong muốn.

## 6. `volatile`, atomic variables, locks: chọn đúng công cụ

| Công cụ | Đặc tính thích hợp | Không tự giải quyết |
| --- | --- | --- |
| `volatile boolean stopped` | Flag được một/nhiều thread ghi và thread khác quan sát, với visibility/ordering | Atomicity của phép đọc-sửa-ghi nhiều bước; tính nhất quán của nhiều field |
| `AtomicInteger.incrementAndGet()` | Tăng một biến nguyên theo phép atomic | Giao dịch cập nhật nhiều đối tượng/field |
| `AtomicReference.compareAndSet(...)` | Thay tham chiếu có điều kiện dựa trên giá trị quan sát | Tính đúng của object mutable bên trong nếu vẫn bị sửa trực tiếp |
| `synchronized` | Bảo vệ critical section và invariant nhiều thao tác dưới cùng monitor | Tự động loại bỏ deadlock hoặc công việc blocking kéo dài |
| `ReentrantLock` | Khóa tường minh, có `tryLock`, tùy chọn interruptible lock và `Condition` | Tự nhả khóa nếu quên `unlock()`; bắt buộc dùng `finally` |
| `ConcurrentHashMap` | Truy cập map concurrent, nhiều thao tác atomic theo key/API cụ thể | Atomicity của chuỗi thao tác nhiều key, hoặc an toàn của value mutable |

### Trường hợp stop flag

```java
final class StopSignal {
    private volatile boolean stopped;

    void requestStop() {
        stopped = true;
    }

    boolean shouldStop() {
        return stopped;
    }
}
```

Các worker có thể đọc `shouldStop()` trong vòng lặp, tuy nhiên nếu worker đang block mãi trong `BlockingQueue.take()` hoặc I/O, **flag một mình không đánh thức được thread**. Cần thêm interruption, timeout hoặc giao thức đóng phù hợp. Một vòng lặp chỉ kiểm tra flag liên tục cũng có thể lãng phí CPU.

### Trường hợp counter đơn biến

```java
import java.util.concurrent.atomic.AtomicInteger;

final class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger();

    int incrementAndGet() {
        return count.incrementAndGet();
    }

    int get() {
        return count.get();
    }
}
```

`AtomicInteger` thích hợp nếu logic là tăng/giảm một giá trị. Nếu điều kiện thành công phụ thuộc vào **hai giá trị liên quan**, chỉ gọi riêng hai atomic operations chưa khiến toàn bộ chuỗi thành một giao dịch.

### Khi dùng `ReentrantLock`

```java
import java.util.concurrent.locks.ReentrantLock;

final class GuardedValue {
    private final ReentrantLock lock = new ReentrantLock();
    private int value;

    void increment() {
        lock.lock();
        try {
            value++;
        } finally {
            lock.unlock();
        }
    }

    int get() {
        lock.lock();
        try {
            return value;
        } finally {
            lock.unlock();
        }
    }
}
```

Mọi đường truy cập tới `value` phải tuân theo cùng khóa. Dùng `finally` để giải phóng khóa kể cả khi có exception. Không giữ lock qua một tác vụ I/O hoặc callback không kiểm soát nếu có thể tránh, vì điều đó tăng thời gian chờ và nguy cơ deadlock.

## 7. Coordination: bảo vệ dữ liệu và phối hợp tiến độ là hai bài toán

**Mutual exclusion** trả lời: *ai được phép vào critical section?*

**Coordination** trả lời: *tác vụ nào cần chờ sự kiện nào, ai báo hoàn tất, công việc và kết quả đi qua kênh nào, khi nào dừng?*

Ví dụ: nhiều worker cập nhật một giá trị bằng `AtomicInteger` đã giải quyết phép tăng atomic, nhưng thread tổng hợp vẫn phải biết **khi nào mọi worker hoàn thành** để xuất báo cáo cuối. Ngược lại, thread tổng hợp có thể dùng `CountDownLatch.await()` để đợi đủ worker, nhưng điều đó không làm các phép tăng `int count++` giữa worker an toàn.

### Nhóm công cụ phối hợp chính

| Công cụ Java | Mục đích | Đặc tính cần nhớ |
| --- | --- | --- |
| `Thread.join()` | Chờ một thread kết thúc | Caller có thể bị block; không nên dùng sleep để đoán thời điểm hoàn thành |
| `Future.get()` | Chờ và lấy kết quả task | Có thể block và ném `InterruptedException`/`ExecutionException`; `get(timeout, unit)` có giới hạn chờ |
| `CountDownLatch` | Đợi số tín hiệu hoàn thành cố định | One-shot; mỗi `countDown()` giảm bộ đếm; không tự reset |
| `CyclicBarrier` | Cho nhóm worker gặp tại cùng điểm trước khi tiếp tục | Có thể tái sử dụng qua các vòng; cần xử lý barrier bị phá |
| `Phaser` | Đồng bộ theo phase, có thể đăng ký/hủy đăng ký participant | Phù hợp vòng nhiều giai đoạn với số participant linh hoạt |
| `Semaphore` | Giới hạn số permit cho tài nguyên/tác vụ | `acquire()` có thể block; phải `release()` đúng cách |
| `BlockingQueue` | Truyền công việc giữa producer và consumer | `put()` có thể chờ khi queue đầy, `take()` có thể chờ khi rỗng |
| `Condition` trên `Lock` | Đợi và thông báo predicate dưới một lock | Luôn kiểm tra điều kiện bằng vòng `while`, không dựa vào một `signal()` đơn lẻ |
| `wait()/notifyAll()` | Cơ chế điều kiện ở mức monitor | Phải giữ monitor, kiểm tra predicate trong vòng `while`, tránh dùng nếu công cụ bậc cao phù hợp hơn |

`Thread.sleep(...)` chỉ làm thread tạm nghỉ; nó **không bảo đảm** worker khác đã xong, dữ liệu đã được công bố an toàn hoặc điều kiện đã đúng. Dùng `join`, future, latch hay queue khi cần quan hệ hoàn thành cụ thể.

## 8. Ví dụ producer–consumer: coordination qua `BlockingQueue`

Một queue giới hạn dung lượng vừa chuyển dữ liệu vừa tạo **backpressure**: producer phải chờ nếu consumer chưa lấy kịp. Điều này giúp tránh việc nhiều worker sửa trực tiếp cùng một danh sách kết quả.

Ví dụ độc lập sau dùng **một** producer, **một** consumer, và số `0` làm tín hiệu kết thúc (đầu vào hợp lệ chỉ gồm số dương). Lưu thành `BlockingQueueDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BlockingQueueDemo {
    public static void main(String[] args) throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> producer = executor.submit(() -> {
                for (int i = 1; i <= 5; i++) {
                    queue.put(i);
                }
                queue.put(0); // Sentinel: kết thúc luồng dữ liệu.
                return null;
            });

            Future<Integer> consumer = executor.submit(() -> {
                int sum = 0;
                while (true) {
                    int item = queue.take();
                    if (item == 0) {
                        return sum;
                    }
                    sum += item;
                }
            });

            producer.get();
            System.out.println("Tong = " + consumer.get());
        } finally {
            executor.shutdown();
        }
    }
}
```

Chạy với Java 17+:

```bash
javac BlockingQueueDemo.java
java BlockingQueueDemo
```

Stdout chính xác:

```text
Tong = 15
```

Điều gì được bảo đảm?

- Producer có thể `put` cùng lúc consumer `take`; queue đảm nhiệm đồng bộ nội bộ nên không cần tự khóa danh sách.
- `ArrayBlockingQueue<>(2)` không giữ quá hai phần tử chờ cùng lúc; `put` có thể block khi đầy, `take` có thể block khi rỗng. Đây là **blocking coordination**, không phải non-blocking I/O.
- Consumer là chủ sở hữu duy nhất của biến `sum`. Không có hai thread cùng sửa biến đó, nên không cần `AtomicInteger` cho `sum`.
- Sentinel `0` chỉ đúng vì dữ liệu thực không chứa `0` và có đúng một consumer. Nếu có nhiều consumer, giao thức kết thúc phải bảo đảm mỗi consumer thoát đúng cách.
- Các thao tác `Future.get()` đợi hoàn tất và công bố kết quả tới caller. `shutdown()` giải phóng vòng đời executor sau các tác vụ đã gửi.

**Điều kiện triển khai thực tế:** cần đường xử lý khi producer thất bại trước khi gửi sentinel, khi consumer thất bại, khi task bị hủy, và khi caller không thể chờ vô hạn. Tùy yêu cầu có thể dùng timeout, cancel, close signal, định nghĩa trạng thái lỗi chung hoặc cơ chế điều phối cao hơn. Ví dụ trên chỉ minh họa luồng thành công.

## 9. Ví dụ coordination khi các worker phải cùng hoàn tất: `CountDownLatch`

`CountDownLatch` hữu ích khi một thread điều phối cần chờ một nhóm công việc độc lập báo xong. Nó **không phải** mutex.

Lưu thành `LatchDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LatchDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(3);
        AtomicInteger total = new AtomicInteger();

        for (int i = 1; i <= 3; i++) {
            final int contribution = i * 10;
            Thread worker = new Thread(() -> {
                try {
                    total.addAndGet(contribution);
                } finally {
                    finished.countDown();
                }
            });
            worker.start();
        }

        finished.await();
        System.out.println("Tong = " + total.get());
    }
}
```

```bash
javac LatchDemo.java
java LatchDemo
```

Stdout chính xác:

```text
Tong = 60
```

Ở đây `AtomicInteger` bảo đảm phép cộng an toàn khi các worker cập nhật `total`; `CountDownLatch` bảo đảm thread `main` chỉ in sau khi cả ba worker đã hoàn tất và gọi `countDown()`. Hai công cụ giải quyết **hai mối quan tâm khác nhau**. Đặt `countDown()` trong `finally` giúp tránh tình huống một worker lỗi ở đoạn xử lý rồi quên giảm latch. Tuy nhiên, ứng dụng thực tế vẫn cần thu thập và báo lỗi worker, vì một latch về 0 không chứng minh tất cả worker thành công.

Nếu nhóm công việc có nhiều vòng và cần chờ nhau ở cuối **mỗi vòng**, cân nhắc `CyclicBarrier` hoặc `Phaser` thay cho việc tái sử dụng một latch đã về 0. Một `CountDownLatch` không reset được.

## 10. Thread confinement: ai sở hữu trạng thái?

**Thread confinement** nghĩa là một đối tượng mutable được truy cập độc quyền bởi một thread trong một giai đoạn sở hữu, không cho phép các thread khác cùng sửa/đọc trạng thái đó không có phối hợp. Vì không có truy cập xung đột, có thể tránh việc đặt lock quanh từng thao tác.

Các kiểu thường gặp:

1. **Stack/task confinement:** Mỗi lần gọi tạo state riêng, không để tham chiếu thoát ra ngoài; đây là lựa chọn đơn giản nhất.
2. **Actor/event-loop ownership:** Một worker sở hữu map/cache/bộ tích lũy và nhận lệnh qua queue; các thread khác gửi message thay vì chạm vào state.
3. **`ThreadLocal<T>`:** Mỗi thread có một giá trị được gắn với thread đó; hữu ích với một số context hoặc buffer, nhưng phải quản lý vòng đời đặc biệt cẩn thận khi tái sử dụng thread.

### Cách đơn giản nhất: state cục bộ theo task

```java
static String formatForOneTask(String id) {
    StringBuilder buffer = new StringBuilder(); // Mỗi lời gọi có builder riêng.
    buffer.append("job-").append(id);
    return buffer.toString(); // Trả String immutable.
}
```

Hai thread có thể cùng chạy phương thức này mà không chia sẻ `buffer`. Không cần `synchronized` hoặc `ThreadLocal`. Đây là lý do **giảm chia sẻ** thường đơn giản hơn việc dùng thêm công cụ đồng bộ.

Nhưng nếu `buffer` được đặt vào một collection chung hoặc chuyển tham chiếu tới thread khác trước khi hoàn tất, state có thể không còn confined. Quy tắc là **không để mutable reference thoát khỏi quyền sở hữu**.

### Khi thật sự cần `ThreadLocal`

`ThreadLocal` lưu một giá trị riêng theo thread, thường có ích khi một API cũ đòi hỏi context theo thread mà không truyền tham số tường minh thuận tiện. Nó không làm đối tượng bên trong `ThreadLocal` tự thread-safe nếu đối tượng đó bị chuyển tham chiếu sang thread khác.

Ví dụ độc lập minh họa cleanup với thread pool tái sử dụng worker. Lưu thành `ThreadLocalDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadLocalDemo {
    private static final ThreadLocal<StringBuilder> BUFFER =
            ThreadLocal.withInitial(StringBuilder::new);

    private static String process(String id) {
        StringBuilder buffer = BUFFER.get();
        try {
            buffer.append("job-").append(id);
            return buffer.toString();
        } finally {
            BUFFER.remove(); // Xóa state gắn với worker sau task.
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> first =
                    executor.submit(() -> process("A"));
            Future<String> second =
                    executor.submit(() -> process("B"));

            System.out.println(first.get());
            System.out.println(second.get());
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac ThreadLocalDemo.java
java ThreadLocalDemo
```

Stdout chính xác:

```text
job-A
job-B
```

Cả hai task chạy trên cùng một worker do `newSingleThreadExecutor()` tái sử dụng thread; `remove()` khiến task sau nhận `StringBuilder` mới thay vì giữ nội dung của task trước. Với state chỉ dùng nội bộ một lời gọi như ví dụ, **biến cục bộ vẫn là lựa chọn ưu tiên**; `ThreadLocal` được dùng ở đây để minh họa chính xác cơ chế và rủi ro vòng đời.

**Các giới hạn quan trọng của `ThreadLocal`:**

- Nó gắn state theo **thread**, không gắn theo **request** hay `CompletableFuture`. Một chuỗi async chạy trên nhiều thread không được tự động mang theo giá trị `ThreadLocal` giữa các stage.
- Thread trong pool có thể sống lâu hơn task; nếu quên `remove()`, task khác trên cùng worker có thể gặp dữ liệu cũ, đồng thời các object được tham chiếu có thể bị giữ trong bộ nhớ lâu hơn mong muốn.
- `ThreadLocal` không phải phương thức bảo mật hay cách đồng bộ state giữa các thread. Nó ngăn chia sẻ qua *đường truy cập ThreadLocal* chứ không ngăn lập trình viên cố tình chia sẻ chính đối tượng mutable.
- Với Java 21+, virtual threads vẫn là thread và có thể dùng `ThreadLocal`, nhưng việc gắn dữ liệu nặng vào số lượng virtual threads rất lớn có thể tốn bộ nhớ. Phần này thuộc Java core; không cần cơ chế riêng của framework.

## 11. Handover ownership: chuyển quyền thay vì chia sẻ tự do

Một thiết kế hiệu quả là chia công việc thành các giai đoạn mà mỗi giai đoạn có **một chủ sở hữu mutable state**:

```text
Nguồn dữ liệu
    |
    v
[Producer - sở hữu state đầu vào]
    |
    | gửi item/snapshot qua BlockingQueue
    v
[Consumer - sở hữu state tích lũy]
    |
    | công bố kết quả immutable qua Future
    v
[Caller - đọc kết quả]
```

Lợi ích:

- Producer không cần lock quanh bộ tích lũy của consumer.
- Queue xác định điểm chuyển giao công việc và cung cấp quy tắc visibility.
- Consumer cập nhật state của nó theo thứ tự nhận message.
- Caller lấy kết quả hoàn tất qua future; không cần đọc một đối tượng đang bị sửa giữa chừng.

Lưu ý: **đưa một tham chiếu mutable vào queue không tự chuyển quyền sở hữu bằng phép màu**. Producer phải ngừng sửa đối tượng sau khi chuyển giao, hoặc gửi một bản sao/immutable snapshot. Nếu producer vẫn sửa chính object đó trong khi consumer đọc thì shared mutable state vẫn tồn tại, dù có queue ở giữa.

Cũng cần xác định ranh giới lỗi và hủy: ai đóng producer, ai gửi tín hiệu kết thúc, ai quyết định khi consumer thất bại và ai chịu trách nhiệm giải phóng tài nguyên.

## 12. Dùng concurrent collections đúng phạm vi bảo đảm

Các collection trong `java.util.concurrent` giúp giảm việc tự viết khóa trên cấu trúc dữ liệu chia sẻ:

- `ConcurrentHashMap`: đọc/cập nhật concurrent trên map. Một số phương thức như `putIfAbsent`, `compute` và `merge` cung cấp thao tác atomic theo chính sách của API. **Không** suy luận hai lệnh `get` rồi `put` là một thao tác atomic.
- `ConcurrentLinkedQueue`: queue concurrent không blocking, không có giới hạn dung lượng mặc định; caller cần giao thức riêng để biết khi nào có dữ liệu và khi nào kết thúc.
- `ArrayBlockingQueue`/`LinkedBlockingQueue`: cung cấp `put/take` chờ và giao tiếp producer–consumer. Với `LinkedBlockingQueue`, cân nhắc cấu hình dung lượng phù hợp để tránh hàng đợi tăng không kiểm soát.
- `CopyOnWriteArrayList`: phù hợp khi đọc nhiều, ghi ít, vì thao tác thay đổi thường tạo bản sao cấu trúc; không tối ưu cho dữ liệu cập nhật liên tục.
- `LongAdder`: có thể hữu ích cho counter thống kê nhiều thread cập nhật; `sum()` trong lúc các thread vẫn cập nhật **không nhất thiết là snapshot atomic**. Nếu phải có một điểm quan sát chính xác và đồng bộ với các cập nhật khác, cần thiết kế phù hợp hơn.

Ví dụ `ConcurrentHashMap<String, Integer>` chỉ bảo vệ thao tác map theo hợp đồng. Nếu value là một `ArrayList` mutable và nhiều thread tự ý gọi `map.get(key).add(...)`, list vẫn có thể bị race. Thread safety của container không tự lan sang nội dung mutable được chứa trong đó.

## 13. Tránh deadlock, starvation và chờ sai điều kiện

### Deadlock khi lấy nhiều lock sai thứ tự

Giả sử thread A giữ lock X rồi chờ lock Y, trong khi thread B giữ Y rồi chờ X:

```text
Thread A: lock X -> chờ Y
Thread B: lock Y -> chờ X
                   |
                 deadlock
```

Cách giảm nguy cơ là quy định **thứ tự lock nhất quán** khi cần lấy nhiều khóa, giảm thời gian giữ khóa, tránh gọi logic không kiểm soát khi đang giữ khóa và dùng `tryLock` có timeout khi yêu cầu hệ thống cho phép. Thứ tự khóa phải áp dụng trên mọi đường code liên quan, không chỉ một phương thức.

**Starvation** xảy ra khi một tác vụ không được cấp tiến độ hợp lý dù các tác vụ khác tiếp tục chạy; không đồng nghĩa deadlock. Fair lock/queue có thể hỗ trợ tính công bằng trong một số trường hợp nhưng không thay thế thiết kế giới hạn tải và quản lý scheduler.

### Chờ điều kiện với monitor

Khi buộc phải dùng `Object.wait/notifyAll`, nguyên tắc là bảo vệ predicate và chờ **trong vòng while dưới cùng monitor**:

```java
final class Gate {
    private boolean open;

    synchronized void awaitOpen() throws InterruptedException {
        while (!open) {
            wait();
        }
    }

    synchronized void open() {
        open = true;
        notifyAll();
    }
}
```

Thread gọi `wait()` phải giữ monitor; thao tác `wait` giải phóng monitor trong khi chờ rồi lấy lại monitor trước khi trả về. Dùng `while` vì có thể thức dậy mà điều kiện vẫn chưa đúng (bao gồm spurious wakeup và thread khác đã thay đổi điều kiện). `notifyAll` thức các waiter phù hợp để kiểm tra lại predicate; nó không tự trao quyền chạy tức thì.

Với producer–consumer hoặc coordination thông thường, `BlockingQueue` và các công cụ bậc cao thường giúp code ít lỗi hơn.

## 14. Cancellation, interruption và quản lý vòng đời

Một thiết kế concurrency đúng không chỉ trả kết quả khi mọi thứ thuận lợi; nó phải xác định **ai có quyền dừng**, **thread đang block được đánh thức thế nào** và **lỗi có truyền về nơi gọi không**.

- `Thread.interrupt()` gửi yêu cầu interruption, không cưỡng bức kết thúc thread. Các thao tác như `sleep`, `wait`, `join`, `BlockingQueue.put/take` có thể phản hồi bằng `InterruptedException`.
- Nếu bắt `InterruptedException` mà không thể xử lý dứt điểm, thường nên kết thúc tác vụ và/hoặc khôi phục interrupt flag bằng `Thread.currentThread().interrupt()`. Không nuốt exception rồi tiếp tục chờ vô hạn.
- `Future.cancel(true)` có thể yêu cầu ngắt task đang chạy, nhưng thành công cuối cùng tùy việc task có hỗ trợ interruption và điểm blocking có đáp ứng không.
- `ExecutorService.shutdown()` không nhận task mới và cho task đã gửi hoàn tất; `shutdownNow()` cố gắng dừng task đang hoạt động và trả các task chờ, nhưng cũng không bảo đảm dừng ngay.
- Khi dùng `Lock` tường minh, đặt `unlock()` trong `finally`. Khi dùng queue, semaphore hoặc latch, lên kế hoạch cleanup và tín hiệu kết thúc trong cả đường lỗi.
- Với `Semaphore`, chỉ `release()` permit mà task thực sự đã `acquire()` thành công. Việc release dư có thể tăng số permit ngoài giới hạn logic.
- Khi báo cáo kết quả, dùng điểm hội tụ rõ ràng (`Future.get`, `join`, latch hoặc signal) và phân biệt **hoàn tất** với **thành công**. Task kết thúc bằng exception vẫn là một kết thúc.

Nếu một request bị hủy trong khi một worker vẫn giữ mutable context, phải bảo đảm context được đóng hoặc dọn theo vòng đời sở hữu; nếu thread được tái sử dụng, hãy kiểm tra những `ThreadLocal` còn tồn tại.

## 15. Chọn thiết kế qua ba bài toán cụ thể

### A. Thống kê số request hoàn tất

**Bài toán:** nhiều worker cùng tăng một số đếm, không có invariant liên quan nhiều biến.

Thiết kế thường phù hợp: `AtomicInteger` hoặc `LongAdder` nếu là thống kê có contention cao và chấp nhận semantics của `sum()`. Không cần khóa lớn quanh toàn bộ xử lý request chỉ để tăng counter. Nếu muốn ảnh chụp thống kê nhất quán tại một mốc, xác định rõ điểm hoàn tất và cách đọc.

### B. Một ví phải bảo đảm số dư không âm

**Bài toán:** cần kiểm tra `balance >= amount` trước khi giảm.

Thiết kế phù hợp: giữ kiểm tra và cập nhật trong **một critical section** (`synchronized` hoặc lock), hoặc dùng vòng CAS thiết kế cẩn thận cho state một biến. `volatile int balance` không đủ, vì phép kiểm tra và trừ là thao tác kết hợp. Nếu chuyển tiền giữa hai ví, cần một thiết kế atomic giữa hai đối tượng và thứ tự khóa tránh deadlock; khóa mỗi ví độc lập rồi cập nhật lần lượt chưa bảo đảm giao dịch tổng thể.

### C. Nhiều nguồn gửi sự kiện, một worker cập nhật trạng thái tổng hợp

**Bài toán:** chỉ một logic tổng hợp cần cập nhật state; producer khác nhau không cần sửa state đó trực tiếp.

Thiết kế phù hợp: dùng `BlockingQueue` có giới hạn; producer gửi event/immutable message, consumer sở hữu map và state mutable của riêng nó. Bảo đảm consumer ngừng đúng cách và thông báo lỗi. Ưu điểm chính là thay đổi **quyền sở hữu dữ liệu**, giúp không phải đặt lock ở từng phép sửa map.

## 16. Những nhầm lẫn thường gặp

1. **"`volatile` giải quyết race condition."** Chỉ đúng với những giao thức cụ thể dựa trên visibility/ordering; `volatile count++` vẫn có thể lost update.
2. **"Có `CountDownLatch` thì dữ liệu worker chia sẻ đã thread-safe."** Latch phối hợp việc đợi và tạo quan hệ HB, không cung cấp mutual exclusion cho các cập nhật concurrent trong lúc chạy.
3. **"Dùng `AtomicInteger` nghĩa là toàn bộ object atomic."** Chỉ những thao tác được định nghĩa trên atomic variable có bảo đảm tương ứng.
4. **"Field `final` biến collection thành immutable."** Nó cố định tham chiếu, không ngăn việc sửa các phần tử trong collection.
5. **"Biến local luôn an toàn."** Biến local chỉ giữ tham chiếu; đối tượng được tham chiếu vẫn có thể bị chia sẻ.
6. **"Concurrent map thì value của map cũng an toàn."** Cần phân tích thread safety của value riêng.
7. **"Một queue bảo đảm không còn data race."** Nếu producer tiếp tục sửa object đã đưa vào queue, data race vẫn có thể xảy ra.
8. **"`ThreadLocal` là dữ liệu theo request."** Nó là dữ liệu theo **thread**; reuse thread và async hop có thể phá giả định đó.
9. **"`Thread.sleep()` là cơ chế đợi worker hoàn thành."** Thời gian ngủ chỉ là khoảng trì hoãn, không chứng minh sự kiện đã xảy ra.
10. **"`notify()` thức một waiter nên có thể bỏ kiểm tra điều kiện."** Waiter vẫn phải kiểm tra predicate trong vòng `while` khi thức dậy và lấy lại monitor.
11. **"Dùng nhiều lock sẽ luôn nhanh hơn một lock."** Tách khóa có thể làm tăng concurrency nhưng cũng tăng rủi ro vi phạm invariant và deadlock.
12. **"`shutdown()` dừng task ngay."** Nó không cưỡng bức chấm dứt task đang chạy; cần thiết kế cancellation phù hợp.

## 17. Checklist khi thiết kế shared state và coordination

Trước khi triển khai, trả lời các câu hỏi theo thứ tự:

1. **State nào mutable?** Ai tạo, ai sở hữu, ai còn giữ tham chiếu?
2. **Có cần chia sẻ không?** Có thể dùng immutable snapshot, state cục bộ hoặc một consumer sở hữu độc quyền không?
3. **Nếu cần chia sẻ, invariant nào phải giữ?** Một biến atomic hay nhiều field cần critical section?
4. **Thread khác nhìn thấy thay đổi bằng cơ chế nào?** Có quan hệ happens-before rõ ràng qua lock, volatile, queue, start/join, future không?
5. **Worker đợi điều kiện gì?** Dùng queue, latch, future, semaphore, barrier hoặc condition phù hợp; tránh đoán bằng sleep.
6. **Hệ thống dừng và báo lỗi thế nào?** Ai gửi tín hiệu kết thúc, ai xử lý interruption, ai hủy và đóng tài nguyên?
7. **Đã kiểm tra ranh giới thread pool chưa?** `ThreadLocal` có cleanup không, dữ liệu task trước có thể lọt sang task sau không?
8. **Đã đo độ trễ và contention chưa?** Hạn chế lock quá rộng và queue không giới hạn, nhưng không đổi tính đúng lấy hiệu năng suy đoán.

Với Java concurrency, điểm xuất phát tốt nhất là **quy định quyền sở hữu trạng thái**, sau đó chọn cơ chế **bảo vệ invariant** và **phối hợp tiến độ** riêng rẽ. Từ đó mới quyết định có cần `synchronized`, atomic variables, concurrent collections, queue hay `ThreadLocal`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-007">Safety, liveness, progress và starvation</a>

<details>
<summary>Xem chi tiết</summary>

# Safety, liveness, progress và starvation trong Java Concurrency

Khi nhiều thread cùng truy cập một trạng thái, chương trình có thể **tạo ra kết quả sai** hoặc **không bao giờ hoàn thành công việc**. Hai loại vấn đề này phải được đánh giá riêng: *safety* hỏi liệu có điều xấu xảy ra hay không; *liveness* hỏi liệu điều tốt cần xảy ra có thể tiếp tục xảy ra hay không. *Progress* mô tả mức bảo đảm tiến triển của thuật toán; *starvation* mô tả tình trạng một thread/tác vụ chờ mãi dù hệ thống vẫn hoạt động.

Bài học áp dụng cho **Java concurrency nói chung**: Java Memory Model, thread, intrinsic lock, `java.util.concurrent`, atomic operation và các thuật toán phối hợp. Những cơ chế tích hợp riêng với Spring nằm ngoài phạm vi.

## 1. Phân biệt bốn khái niệm cốt lõi

| Khái niệm | Câu hỏi cần trả lời | Ví dụ |
| --- | --- | --- |
| **Safety** (tính an toàn) | Có trạng thái/kết quả bị cấm nào có thể xuất hiện không? | Số dư tài khoản không được âm; mỗi công việc chỉ được xử lý một lần. |
| **Liveness** (tính sống) | Với những giả định tiến triển phù hợp, công việc có cơ hội hoàn thành cuối cùng không? | Thread đang đợi có thể được đánh thức; yêu cầu không kẹt vĩnh viễn. |
| **Progress** (bảo đảm tiến triển) | Trong tranh chấp, thuật toán bảo đảm tiến triển cho toàn hệ thống hay cho từng thread ở mức nào? | Lock-free bảo đảm tiến triển ở cấp hệ thống trong mô hình thuật toán; wait-free có giới hạn bước cho mỗi lời gọi. |
| **Starvation** (đói tài nguyên/lượt chạy) | Một tác vụ cụ thể có thể không được phục vụ vô hạn dù tác vụ khác vẫn chạy không? | Thread liên tục thua tranh chấp lock hoặc CAS. |

**Safety** thường được mô tả bằng một *invariant* (bất biến): điều kiện phải đúng trong mọi trạng thái có thể quan sát hợp lệ. Ví dụ, nếu hai thread cùng rút một đơn vị tiền từ tài khoản có số dư `1`, sau khi xử lý hợp lệ phải có tối đa một lần rút thành công và số dư không âm. Chỉ kiểm tra `balance > 0` trước khi trừ chưa đủ nếu bước kiểm tra và trừ có thể bị thread khác chen vào.

**Liveness** là tính chất theo thời gian: một công việc cần có khả năng tiến triển, thường được phát biểu với các giả định như thread tiếp tục được CPU lập lịch, tài nguyên cuối cùng được trả lại và các thread không bị dừng vĩnh viễn. Không có thuật toán Java nào tự bảo đảm hệ điều hành sẽ cấp CPU cho một thread mãi mãi bị bỏ qua.

**Progress** thường là mức bảo đảm được chứng minh ở cấp thuật toán trong một mô hình thực thi. Một chương trình có thể giữ được safety nhưng không có liveness; ví dụ, khóa được giữ đúng cách nên dữ liệu không hỏng, nhưng thread giữ khóa không bao giờ giải phóng. Ngược lại, mọi thread có vẻ chạy rất nhanh cũng không chứng minh safety nếu cập nhật dữ liệu bị race.

**Starvation** là vấn đề của từng tác vụ. Tổng thể hệ thống vẫn đạt throughput cao, nhưng một thread cụ thể không có tiến triển. Vì vậy “có progress toàn hệ thống” không đồng nghĩa “mọi thread đều hoàn thành”.

## 2. Safety: bảo vệ invariant, không chỉ bảo vệ từng biến

### 2.1 Race condition và thao tác ghép

Xét các thao tác:

```java
if (balance.get() > 0) {
    balance.decrementAndGet();
}
```

Ngay cả khi `balance` là `AtomicInteger`, phép đọc, so sánh và giảm là **ba bước riêng**. Hai thread có thể cùng đọc số dư dương rồi cùng giảm. Mỗi lần `decrementAndGet()` là atomic nhưng toàn bộ điều kiện nghiệp vụ “chỉ giảm khi còn tiền” không atomic. Đây là lỗi *check-then-act*.

Có ba vấn đề thường bị trộn lẫn:

- **Atomicity:** thao tác hoặc chuyển trạng thái quan trọng có bị thread khác chen vào ở giữa không?
- **Visibility:** thread khác có nhìn thấy lần ghi mới hay không?
- **Ordering:** JVM/compiler/CPU có thể sắp xếp lại những thao tác nào, và quan hệ *happens-before* nào ràng buộc việc quan sát?

Trong Java Memory Model, `volatile` cung cấp quan hệ visibility/ordering cần thiết cho các lần đọc/ghi tương ứng, nhưng `volatile int count` với `count++` vẫn có thể mất cập nhật vì `++` là read–modify–write. Một khối `synchronized` được dùng nhất quán cho cả đọc/ghi trạng thái liên quan, hoặc một thao tác CAS trên `AtomicInteger`, có thể giữ invariant phù hợp. Đừng dùng hai lock khác nhau cho cùng một invariant rồi giả định đã đồng bộ đúng.

### 2.2 Ví dụ Java 17+: vi phạm safety và sửa bằng CAS

Tạo tệp `SafetyLivenessDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class SafetyLivenessDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger unsafeBalance = new AtomicInteger(1);
        CountDownLatch bothChecked = new CountDownLatch(2);
        CountDownLatch startDecrement = new CountDownLatch(1);

        Runnable unsafeWithdraw = () -> {
            if (unsafeBalance.get() > 0) {
                bothChecked.countDown();
                try {
                    startDecrement.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                unsafeBalance.decrementAndGet();
            } else {
                bothChecked.countDown();
            }
        };

        Thread first = new Thread(unsafeWithdraw, "unsafe-1");
        Thread second = new Thread(unsafeWithdraw, "unsafe-2");
        first.start();
        second.start();
        bothChecked.await();
        startDecrement.countDown();
        first.join();
        second.join();

        AtomicInteger safeBalance = new AtomicInteger(1);
        AtomicInteger successfulWithdrawals = new AtomicInteger();
        Runnable safeWithdraw = () -> {
            while (true) {
                int current = safeBalance.get();
                if (current <= 0) {
                    return;
                }
                if (safeBalance.compareAndSet(current, current - 1)) {
                    successfulWithdrawals.incrementAndGet();
                    return;
                }
            }
        };

        Thread third = new Thread(safeWithdraw, "safe-1");
        Thread fourth = new Thread(safeWithdraw, "safe-2");
        third.start();
        fourth.start();
        third.join();
        fourth.join();

        System.out.println("Unsafe balance: " + unsafeBalance.get());
        System.out.println("Safe balance: " + safeBalance.get());
        System.out.println("Successful safe withdrawals: "
                + successfulWithdrawals.get());
    }
}
```

Biên dịch/chạy:

```bash
javac SafetyLivenessDemo.java
java SafetyLivenessDemo
```

Kết quả:

```text
Unsafe balance: -1
Safe balance: 0
Successful safe withdrawals: 1
```

**Vì sao kết quả đầu tiên xác định?** `CountDownLatch` buộc cả hai thread hoàn tất bước kiểm tra `> 0` trước khi một thread được phép giảm số dư. Do cả hai đọc giá trị ban đầu `1`, mỗi thread giảm đúng một đơn vị: `-1`. Latch chỉ phục vụ tái hiện race; nó không phải cách sửa invariant.

Ở nhánh an toàn, `compareAndSet(current, current - 1)` thực hiện chuyển trạng thái có điều kiện như một thao tác atomic. Nếu thread khác đã thay đổi số dư, CAS thất bại và vòng lặp đọc lại. Từ `1`, chỉ một thread có thể chuyển thành `0`; thread kia phát hiện `0` và dừng. `join()` bảo đảm main thread đợi các thread kết thúc trước khi in kết quả.

Vòng lặp CAS ở đây giải quyết **safety** cho một biến số dư đơn giản. Nó không tự chứng minh rằng một thread cụ thể sẽ thành công sau số bước hữu hạn khi tranh chấp kéo dài. Nếu invariant trải trên nhiều biến/tài nguyên, hãy thiết kế một giao thức đồng bộ chung, dùng lock đúng phạm vi, hoặc dùng cấu trúc dữ liệu có hợp đồng atomic phù hợp. Không thể biến một nghiệp vụ đa bước thành atomic chỉ bằng cách thay mọi trường bằng `AtomicInteger`.

### 2.3 Linearizability và điểm hiệu lực

Một lời gọi đồng thời *linearizable* nếu mỗi thao tác có thể được xem như có hiệu lực tại một điểm duy nhất nằm giữa lúc gọi và lúc trả về, đồng thời tôn trọng thứ tự thực tế của những lời gọi không chồng lấn. Trong ví dụ rút tiền an toàn, CAS thành công là điểm tuyến tính hóa của thao tác giảm số dư.

Linearizability là tiêu chuẩn safety hữu ích cho API concurrent; nó **không bảo đảm liveness hay fairness**. Một cấu trúc có thể trả lời đúng cho mọi lời gọi đã hoàn tất mà vẫn khiến một số lời gọi khác chờ vô hạn.

## 3. Liveness: công việc có tiếp tục tiến triển không?

Những dạng thất bại cần phân biệt:

| Hiện tượng | Điều gì xảy ra? | Dấu hiệu |
| --- | --- | --- |
| **Deadlock** | Một tập thread phụ thuộc vòng tròn vào tài nguyên/sự kiện mà nhau phải giải phóng. | Thread cùng đợi lock mãi, không thread nào trong vòng chờ tiến lên. |
| **Livelock** | Thread vẫn đổi trạng thái/chạy nhưng liên tục phản ứng khiến không hoàn thành việc hữu ích. | CPU hoạt động, số lần retry tăng, số tác vụ thành công không tăng. |
| **Starvation** | Một thread cụ thể liên tục bị bỏ qua trong khi thread khác có thể tiến triển. | Latency của một tác vụ tăng vô hạn; throughput chung có thể vẫn tốt. |
| **Blocking không có giới hạn** | Chờ một điều kiện, I/O hoặc thao tác đồng bộ không có thời hạn/đường thoát được bảo đảm. | Request treo, `await()`, `take()` hoặc lock acquisition kéo dài. |

**Deadlock điển hình:** thread A giữ lock `L1`, cần `L2`; thread B giữ `L2`, cần `L1`. Mỗi thread chờ một lock do đối phương giữ. Safety của dữ liệu được khóa có thể vẫn đúng, nhưng liveness thất bại.

**Livelock điển hình:** hai tác vụ nhường đường cho nhau theo cùng một nhịp và cùng thử lại ngay, không tác vụ nào thực sự đi qua. Một chính sách retry có backoff và jitter phù hợp có thể giảm khả năng lặp đồng bộ; vẫn cần giới hạn/thời hạn và đánh giá logic thực tế.

**Starvation điển hình:** một thread ưu tiên thấp, một yêu cầu nằm cuối hàng đợi không công bằng, hoặc một contender CAS liên tiếp thất bại vì thread khác luôn thắng. Starvation không cần deadlock: các thread khác vẫn hoàn tất.

Cần phân biệt “không có deadlock” với “có liveness”: bỏ vòng chờ lock chưa chứng minh không có starvation, chờ I/O vô hạn hoặc livelock.

## 4. Progress guarantee của thuật toán concurrent

Bốn mức thường gặp được mô tả trong mô hình bộ nhớ chung và với các giả định thực thi tương ứng:

| Mức | Bảo đảm chính | Có thể khiến một thread riêng lẻ chờ mãi? |
| --- | --- | --- |
| **Blocking** | Tiến triển có thể phụ thuộc một thread khác chạy/nhả lock. | Có; thread giữ lock bị dừng có thể chặn mọi contender. |
| **Obstruction-free** | Một thao tác có thể hoàn thành nếu được chạy đủ lâu mà không có tranh chấp từ thao tác khác. | Có. |
| **Lock-free** | Dưới việc thực thi tiếp tục, hệ thống có tiến triển: các thao tác không thể liên tục cản trở nhau mà không có lời gọi nào hoàn tất. | Có; một thread có thể luôn thua. |
| **Wait-free** | Mỗi lời gọi hoàn tất trong số bước của chính nó có giới hạn theo mô hình thuật toán. | Không, theo bảo đảm bước của thuật toán; thời gian thực vẫn phụ thuộc scheduling. |

Mối quan hệ khái niệm trong họ thuật toán không khóa là: **wait-free mạnh hơn lock-free, lock-free mạnh hơn obstruction-free**. `lock-free` không có nghĩa “không dùng bất kỳ cơ chế chờ nào ở hệ điều hành trong mọi tầng triển khai”, và một API có tên `Atomic*` không mặc nhiên chứng minh toàn bộ nghiệp vụ gọi nó là wait-free.

Với CAS retry:

```java
while (true) {
    int oldValue = counter.get();
    int newValue = oldValue + 1;
    if (counter.compareAndSet(oldValue, newValue)) {
        break;
    }
}
```

Nếu một CAS thất bại do giá trị đã đổi thì đã có một thay đổi do contender khác thành công. Đây là nền tảng thường gặp của lập luận tiến triển toàn hệ thống cho vòng lặp CAS đơn giản. Tuy nhiên thread đang chạy vòng lặp có thể thất bại nhiều lần, thậm chí bị starvation. Kết luận về thuật toán hoàn chỉnh còn phụ thuộc giả định, trạng thái chia sẻ, tác vụ trong vòng lặp và hành vi của tầng triển khai. Đừng tuyên bố `wait-free` khi chưa có giới hạn bước cho từng lời gọi.

**Mục tiêu thiết kế:** chọn mức progress theo yêu cầu. Một lock rõ ràng với timeout, phạm vi nhỏ và bất biến dễ chứng minh có thể phù hợp hơn vòng CAS phức tạp. Với yêu cầu độ trễ chặt, cần kiểm chứng cả phân phối latency, thời gian đợi lock và số lần retry, không chỉ throughput.

## 5. Starvation, fairness và chính sách cấp tài nguyên

*Fairness* mô tả cách phân phối cơ hội giữa các contender. Fair lock thường cố gắng ưu tiên những thread đã chờ lâu hơn; *nonfair lock* có thể cho contender mới lấy lock trước (*barging*) để cải thiện throughput trong một số tải.

Java cung cấp lựa chọn fairness cho một số primitive:

- `new ReentrantLock(true)`: dùng chính sách xếp hàng công bằng hơn cho các lần acquire lock thông thường; **không** bảo đảm tuyệt đối thứ tự thread chạy hay thời gian được CPU. `tryLock()` không thời hạn có thể lấy lock ngay cả khi còn thread chờ, kể cả lock fair.
- `new Semaphore(permits, true)`: dùng chính sách công bằng khi cấp permit tại các điểm xếp hàng được định nghĩa; không suy ra công bằng cho mọi hành động bên ngoài primitive.
- `synchronized`: không cung cấp hợp đồng FIFO/fairness để dựa vào khi thiết kế chống starvation.
- `Thread.yield()`, `Thread.setPriority()` và `Thread.sleep()`: không phải cơ chế bảo đảm fairness; lịch chạy tùy JVM/hệ điều hành.

Ví dụ quản lý lock an toàn, minh họa cú pháp Java 17:

```java
import java.util.concurrent.locks.ReentrantLock;

final class Inventory {
    private final ReentrantLock lock = new ReentrantLock(true);
    private int stock = 10;

    public boolean reserveOne() {
        lock.lock();
        try {
            if (stock == 0) {
                return false;
            }
            stock--;
            return true;
        } finally {
            lock.unlock();
        }
    }
}
```

Đây là **mảnh mã minh họa**, không phải chương trình có `main`. Phần `finally` bảo đảm nhả lock ngay cả khi phát sinh exception, giúp tránh giữ lock do sai sót. Cờ `true` tác động chính sách tranh chấp của lock nhưng không làm phương thức này thành wait-free và không bảo đảm latency tối đa. Trong hệ thống thực, cần đo throughput và tail latency trước khi bật fairness vì có thể có chi phí điều phối.

Starvation còn có thể đến từ **thread-pool starvation**: các tác vụ trong pool chờ kết quả của những tác vụ khác cũng được nộp vào chính pool đã kín thread chạy. Dù không có vòng chờ lock, không còn worker rảnh để thực hiện tác vụ được chờ. Hãy tránh cấu trúc chờ phụ thuộc như vậy, hoặc tách tài nguyên thực thi và xác định chính sách capacity/queue phù hợp.

## 6. Giảm nguy cơ deadlock và chờ vô hạn

### 6.1 Thứ tự lấy lock nhất quán

Nếu mọi đoạn mã cần cả `L1` lẫn `L2` đều lấy theo cùng thứ tự `L1` → `L2`, chúng loại bỏ được vòng chờ do **hai lock đó** đảo thứ tự. Quy tắc phải được áp dụng xuyên suốt các call path; phương thức ẩn lấy thêm lock có thể phá hỏng giả định. Tránh giữ lock trong lúc thực hiện I/O mạng, gọi callback không kiểm soát được hoặc chờ `Future.get()` dài hạn.

### 6.2 Chờ có thời hạn và xử lý interruption

Ví dụ thử lấy lock với timeout, đồng thời giữ đúng hợp đồng interruption:

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

final class TimedCriticalSection {
    private final ReentrantLock lock = new ReentrantLock();

    public boolean execute() throws InterruptedException {
        if (!lock.tryLock(200, TimeUnit.MILLISECONDS)) {
            return false;
        }
        try {
            // Thực hiện công việc ngắn, không chờ I/O vô hạn.
            return true;
        } finally {
            lock.unlock();
        }
    }
}
```

Đây cũng là **mảnh mã minh họa**. Timeout của acquire chỉ giới hạn thời gian **chờ lấy lock**, không giới hạn thời gian chạy trong critical section. Phương thức ném `InterruptedException` cho caller quyết định hủy/điều phối. Nếu bắt exception để kết thúc tác vụ, thông thường cần phục hồi cờ interruption bằng `Thread.currentThread().interrupt()` và thoát khỏi công việc. Không nuốt interruption rồi tiếp tục chờ vô hạn.

Dùng `Condition.await()` trong vòng `while` kiểm tra predicate, vì có thể có *spurious wakeup* và điều kiện có thể đã thay đổi sau khi thread tỉnh dậy:

```java
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
    // Tiếp tục khi predicate ready còn đúng dưới lock.
} finally {
    lock.unlock();
}
```

Mảnh trên giả định `lock`, `condition` và `ready` đã được khai báo đúng và mọi thao tác với `ready` đều tuân thủ cùng giao thức đồng bộ. Chỉ gọi `signal()` không tự bảo đảm thread được đánh thức sẽ nhận CPU ngay hoặc hoàn thành kịp hạn.

Với producer/consumer, ưu tiên primitive có hợp đồng rõ như `BlockingQueue` và cân nhắc phiên bản có timeout (`offer(timeout)`, `poll(timeout)`) khi cần ngăn chờ vô hạn. Với `ExecutorService`, quy định rõ việc `shutdown()`, `awaitTermination(timeout)` và `shutdownNow()`; `shutdownNow()` chỉ cố gắng interruption, không ép task không hợp tác dừng ngay.

## 7. Cách kiểm chứng safety và liveness

### Kiểm chứng safety

1. Viết invariant bằng câu khẳng định có thể kiểm tra: “số dư không âm”, “mỗi ID chỉ chuyển trạng thái một lần”, “tổng số permit được cấp không vượt giới hạn”.
2. Xác định phạm vi atomic cần thiết: một biến, nhiều biến, hay cả một chuỗi trạng thái nghiệp vụ.
3. Lập luận mọi interleaving quan trọng có giữ invariant không; đánh dấu điểm tuyến tính hóa nếu API yêu cầu.
4. Dùng test đồng thời có điểm bắt đầu chung (`CountDownLatch`/`CyclicBarrier`) để tăng xác suất race; lặp stress test và kiểm tra trạng thái cuối. Test pass không phải chứng minh không có race.
5. Có thể dùng công cụ kiểm tra interleaving/JVM concurrency phù hợp khi cần bằng chứng sâu hơn; đừng suy từ một lần chạy ổn định rằng code an toàn.

### Kiểm chứng liveness/progress

1. Liệt kê tất cả nơi chờ: lock acquisition, `wait()`/`await()`, queue, `Future.get()`, I/O, `join()`, sleep, và task phụ thuộc lẫn nhau.
2. Với mỗi điểm chờ, xác định ai tạo điều kiện giải phóng, có thể bị chặn bởi chính người đang chờ không, và có timeout/cancellation không.
3. Đo thời gian đợi lock, số lần CAS retry, queue depth, throughput, tỉ lệ timeout và latency p95/p99 theo từng nhóm tác vụ; trung bình tốt có thể che giấu starvation.
4. Khi ứng dụng treo, thu thập thread dump (`jcmd <pid> Thread.dump_to_file ...` khi JVM hỗ trợ, hoặc `jstack <pid>` phù hợp với JDK/JVM). Xem trạng thái `BLOCKED`, `WAITING`, `TIMED_WAITING`, stack trace và quan hệ lock; một trạng thái `WAITING` đơn lẻ chưa chứng minh deadlock.
5. Java có `ThreadMXBean.findDeadlockedThreads()` để hỗ trợ tìm một số deadlock monitor/ownable synchronizer. Không tìm thấy deadlock bằng API này **không chứng minh** không có livelock, starvation, chờ I/O hoặc mọi loại vòng phụ thuộc.

Một thử nghiệm fairness thực tế cần áp dụng tải cạnh tranh đủ lâu và quan sát **mức phục vụ của từng thread**, không chỉ số thao tác mỗi giây toàn hệ thống. Đừng cố chứng minh “không bao giờ starvation” chỉ bằng một benchmark hữu hạn.

## 8. Chọn kỹ thuật theo bài toán

| Nhu cầu | Hướng thiết kế thường phù hợp | Điều vẫn phải kiểm tra |
| --- | --- | --- |
| Bảo vệ nhiều trường cùng một invariant | `synchronized` hoặc `Lock` chung cho mọi đường truy cập | Phạm vi lock, thứ tự lock, exception và contention |
| Một bộ đếm/trạng thái đơn giản cần cập nhật nguyên tử | `AtomicInteger`, `AtomicLong`, CAS phù hợp | Invariant đa bước, retry, khả năng starvation |
| Truyền tác vụ giữa producer và consumer | `BlockingQueue` có capacity phù hợp | Backpressure, shutdown, timeout, cancellation |
| Nhiều thread đọc, ít thread ghi | Cấu trúc concurrent phù hợp hoặc `ReadWriteLock` khi có lợi | Khả năng writer starvation, chi phí lock |
| Tài nguyên giới hạn số lượt truy cập | `Semaphore` và chính sách fairness khi cần | Permit leak, overflow tài nguyên, policy cấp permit |
| Phải hủy công việc khi quá thời gian | API timed wait/lock, interruption hợp tác | Mã bên trong có hợp tác hủy không; tài nguyên có được thu hồi không |

Đánh giá cả correctness lẫn performance. Fairness có thể cải thiện cơ hội phục vụ nhưng không bảo đảm thời hạn tuyệt đối; khóa ít hơn có thể tăng throughput nhưng làm lập luận safety khó hơn. Nếu không có yêu cầu đặc biệt, chọn primitive tiêu chuẩn có hợp đồng rõ và kiểm tra invariant trước khi tối ưu thuật toán.

## 9. Những hiểu lầm phổ biến

- **“Dùng `volatile` thì `count++` thread-safe.”** Sai: visibility không làm phép tăng ghép trở thành atomic.
- **“Mọi trường là `Atomic*` nên nghiệp vụ an toàn.”** Sai: invariant liên quan nhiều thao tác vẫn có thể bị chen vào.
- **“Không thấy race trong test tức là không có race.”** Sai: lịch xen kẽ hiếm có thể chưa xuất hiện.
- **“Không deadlock tức là chương trình có liveness.”** Sai: còn starvation, livelock, chờ I/O, chờ tín hiệu và task-pool starvation.
- **“Lock-free nghĩa là mọi thread đều nhanh.”** Sai: progress toàn hệ thống không bảo đảm một lời gọi riêng lẻ.
- **“Fair lock bảo đảm FIFO theo giờ tường và không có starvation trong mọi tình huống.”** Sai: còn JVM/OS scheduling, điểm tranh chấp, API lấy lock và lỗi logic bên ngoài.
- **“`sleep()` hoặc `yield()` sửa được race.”** Sai: thay đổi khả năng interleaving chứ không tạo atomicity hay quan hệ đồng bộ bắt buộc.
- **“Có timeout cho việc lấy lock thì toàn tác vụ có deadline.”** Sai: critical section và I/O bên trong vẫn có thể treo.

## 10. Tự kiểm tra kiến thức

1. Hai thread cùng gọi `if (atomic.get() > 0) atomic.decrementAndGet()`. Tính chất nào có thể bị vi phạm? **Safety**, vì check-then-act không atomic.
2. Thread A đợi `L2` khi đang giữ `L1`, B đợi `L1` khi đang giữ `L2`. Đó là gì? **Deadlock**, một thất bại liveness do phụ thuộc vòng tròn.
3. CAS loop vẫn có thao tác thành công, nhưng thread T liên tục thất bại. Điều gì có thể đúng? **Hệ thống có progress trong khi T bị starvation**.
4. `new ReentrantLock(true)` có bảo đảm mọi lời gọi kết thúc trong 100 ms không? **Không**: chính sách fairness không phải thời hạn thực thi và không kiểm soát OS scheduling.
5. Muốn chứng minh một thao tác wait-free, cần điều gì? **Giới hạn số bước của chính thao tác** theo các giả định thuật toán, thay vì chỉ thấy các thread khác vẫn hoàn thành.
6. Dữ liệu đúng nhưng một `Future.get()` không timeout chờ mãi. Safety hay liveness gặp vấn đề? **Liveness**, dù safety có thể vẫn được giữ.

**Ghi nhớ:** hãy bắt đầu bằng invariant để đánh giá safety, sau đó vẽ phụ thuộc chờ và chính sách cấp tài nguyên để đánh giá liveness. Phân biệt progress của *toàn hệ thống* với tiến triển của *từng thread*, và luôn kiểm tra khả năng starvation dưới tranh chấp thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-008">Race condition, deadlock, livelock: nhận diện ở mức tổng quan</a>

<details>
<summary>Xem chi tiết</summary>

# Race condition, deadlock và livelock: nhận diện ở mức tổng quan trong Java

Trong lập trình đồng thời (concurrency), nhiều luồng có thể cùng truy cập dữ liệu hoặc tài nguyên. Chương trình có thể chạy rất nhanh mà vẫn cho kết quả sai, hoặc tất cả luồng vẫn tồn tại nhưng công việc không tiến triển. Ba vấn đề nền tảng cần phân biệt là **race condition**, **deadlock** và **livelock**. Bài này nói về Java nói chung (Java 17+), không đi vào cơ chế riêng của Spring.

Mục tiêu sau bài học:
- Giải thích được vấn đề xảy ra ở đâu, vì sao và tác động của mỗi loại.
- Nhìn vào kết quả sai, trạng thái luồng, thread dump và dấu hiệu tiến triển để nhận diện ban đầu.
- Biết cách phòng tránh phù hợp mà không nhầm lẫn giữa tính nguyên tử, khả năng nhìn thấy dữ liệu và tình trạng chờ khóa.

## 1. Bức tranh chung: an toàn dữ liệu và khả năng tiến triển

**Thread safety (an toàn luồng)** liên quan đến việc kết quả và các bất biến (invariant) của dữ liệu được giữ đúng khi nhiều luồng chạy đồng thời. **Liveness (khả năng tiến triển)** liên quan đến việc các thao tác có thể hoàn thành thay vì bị kẹt hoặc lặp vô ích.

- **Race condition:** kết quả phụ thuộc vào thứ tự hoặc thời điểm thực thi các thao tác đồng thời, dẫn đến khả năng vi phạm yêu cầu. Ví dụ hai luồng cùng cập nhật bộ đếm nhưng làm mất một lần tăng.
- **Deadlock:** một tập hợp luồng cùng chờ những điều kiện/tài nguyên mà chính các luồng trong tập hợp đó đang giữ, khiến không luồng nào tiến tiếp được.
- **Livelock:** các luồng vẫn hoạt động và phản ứng với nhau (nhường, hủy, thử lại...), nhưng liên tục lặp mà không đạt được tiến triển hữu ích.

Một thao tác Java trông ngắn gọn không có nghĩa là nguyên tử. Với `count++`, logic bao gồm đọc giá trị, tính giá trị mới và ghi lại. Nếu các bước xen kẽ giữa các luồng mà không có cơ chế đồng bộ đúng, kết quả có thể sai.

## 2. Race condition: kết quả sai do thứ tự xen kẽ

### 2.1 Cách nhận diện

Giả sử `count = 0`, hai luồng đều muốn tăng một lần:

| Bước | Luồng T1 | Luồng T2 |
| --- | --- | --- |
| 1 | Đọc `count = 0` | |
| 2 | | Đọc `count = 0` |
| 3 | Tính `0 + 1`, ghi `count = 1` | |
| 4 | | Tính `0 + 1`, ghi `count = 1` |

Giá trị đúng sau hai lần tăng là **2**, nhưng thực tế là **1**: một cập nhật bị mất (*lost update*). Một lần chạy khác có thể cho kết quả đúng; chính sự phụ thuộc vào lịch thực thi khiến lỗi khó tái hiện.

**Race condition** là khái niệm về tính đúng đắn phụ thuộc thời điểm/thứ tự. **Data race** có nghĩa hẹp hơn trong Java Memory Model: hai thao tác truy cập cùng một biến, ít nhất một thao tác ghi, không được sắp thứ tự bởi quan hệ *happens-before* phù hợp. Data race thường là nguồn gây lỗi, nhưng không nên đánh đồng hai khái niệm. Ngoài cập nhật mất dữ liệu còn có lỗi kiểm tra rồi hành động (*check-then-act*), ví dụ hai luồng đều kiểm tra “chưa tồn tại” rồi cùng chèn một phần tử.

### 2.2 Ví dụ Java 17+ tái hiện cập nhật bị mất

Chương trình dưới đây cố ý cho cả hai luồng đọc bộ đếm trước khi cho phép ghi. `CountDownLatch` làm lịch xen kẽ trở nên có thể dự đoán trong **bài minh họa**; nó không biến bước cập nhật chung thành nguyên tử.

~~~java
// RaceConditionDemo.java
import java.util.concurrent.CountDownLatch;

public class RaceConditionDemo {
    static class Counter {
        int value = 0;
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable increment = () -> {
            int observed = counter.value;
            bothRead.countDown();
            try {
                allowWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            counter.value = observed + 1;
        };

        Thread first = new Thread(increment, "first");
        Thread second = new Thread(increment, "second");
        first.start();
        second.start();

        bothRead.await();
        allowWrite.countDown();
        first.join();
        second.join();

        System.out.println("Expected: 2");
        System.out.println("Actual: " + counter.value);
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac RaceConditionDemo.java
java RaceConditionDemo
~~~

Kết quả:

~~~text
Expected: 2
Actual: 1
~~~

**Vì sao chắc chắn ra 1?** Cả hai luồng đều lưu giá trị `0` vào biến cục bộ `observed` trước khi `bothRead` về 0. Chỉ sau đó, luồng chính mới mở `allowWrite`, nên hai luồng đều ghi `0 + 1`. `join()` đảm bảo luồng chính đợi chúng kết thúc rồi mới in. Trong một ứng dụng bình thường không có hai latch điều phối, lỗi thường xuất hiện không ổn định và phụ thuộc tải/lịch chạy.

### 2.3 Sửa đúng: nguyên tử hóa thao tác hoặc bảo vệ bất biến

Với một bộ đếm độc lập, `AtomicInteger.incrementAndGet()` cung cấp thao tác tăng nguyên tử:

~~~java
// AtomicCounterDemo.java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        Runnable increment = () -> counter.incrementAndGet();

        Thread first = new Thread(increment);
        Thread second = new Thread(increment);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("Actual: " + counter.get());
    }
}
~~~

~~~bash
javac AtomicCounterDemo.java
java AtomicCounterDemo
~~~

Kết quả luôn là:

~~~text
Actual: 2
~~~

Các lựa chọn thường gặp:
- `synchronized` hoặc `Lock`: bảo vệ một đoạn tới hạn (*critical section*) có nhiều thao tác phải nhất quán với nhau.
- `AtomicInteger`, `AtomicLong`, `AtomicReference` và các phép cập nhật nguyên tử: phù hợp với thao tác đơn giản trên trạng thái chia sẻ cụ thể.
- Kiểu dữ liệu bất biến, dữ liệu riêng của mỗi luồng hoặc giảm chia sẻ trạng thái: thường đơn giản và ít rủi ro hơn.
- Collection đồng thời như `ConcurrentHashMap`: giúp các thao tác được hỗ trợ an toàn trong phạm vi bảo đảm của API; chuỗi “đọc rồi kiểm tra rồi ghi” vẫn có thể cần một thao tác gộp nguyên tử như `putIfAbsent` hoặc `compute`.

**Không dùng `volatile` để sửa `count++`.** `volatile` giúp bảo đảm khả năng nhìn thấy giá trị và các ràng buộc thứ tự bộ nhớ nhất định, nhưng không gộp phép đọc–tính–ghi thành một hành động nguyên tử. Tương tự, `Thread.sleep()` chỉ thay đổi thời điểm chạy, không tạo cơ chế bảo vệ dữ liệu.

## 3. Deadlock: các luồng chờ lẫn nhau vô thời hạn

### 3.1 Tình huống điển hình

Có hai khóa độc lập `lockA` và `lockB`:

~~~text
T1: giữ lockA  ──> chờ lockB
                      ▲
                      │
T2: giữ lockB  ──> chờ lockA
        ▲             │
        └─────────────┘
~~~

Ví dụ luồng T1 đi vào `synchronized(lockA)` rồi xin `lockB`, trong khi luồng T2 đi vào `synchronized(lockB)` rồi xin `lockA`. Nếu mỗi luồng đã giữ khóa thứ nhất, cả hai không thể lấy khóa thứ hai. Đây là **deadlock theo thứ tự lấy khóa**.

Đoạn sau là **mã minh họa hai đường thực thi**, không phải một chương trình hoàn chỉnh; giả sử `lockA` và `lockB` là hai đối tượng khóa được chia sẻ:

~~~java
// T1
synchronized (lockA) {
    synchronized (lockB) {
        // Công việc cần cả A và B.
    }
}

// T2: thứ tự ngược lại, có nguy cơ deadlock.
synchronized (lockB) {
    synchronized (lockA) {
        // Công việc cần cả B và A.
    }
}
~~~

Bốn điều kiện cổ điển thường được dùng để phân tích deadlock là: loại trừ lẫn nhau (mutual exclusion), giữ tài nguyên trong khi chờ tài nguyên khác (hold and wait), không bị tước tài nguyên cưỡng bức (no preemption), và chờ vòng tròn (circular wait). Phá một điều kiện phù hợp có thể ngăn deadlock trong thiết kế khóa tương ứng.

### 3.2 Dấu hiệu trong ứng dụng Java

- Một số request hoặc tác vụ dừng vô thời hạn, không có log hoàn thành, dù tiến trình JVM còn sống.
- Các luồng liên quan đợi nhau theo chu trình trong *thread dump*: “T1 giữ A và chờ B; T2 giữ B và chờ A”.
- Với monitor của `synchronized`, luồng chờ lấy monitor thường ở `Thread.State.BLOCKED`. Với `ReentrantLock`, luồng có thể hiển thị `WAITING` hoặc `TIMED_WAITING` do cơ chế park; **chỉ nhìn trạng thái luồng chưa đủ để kết luận**.
- CPU có thể thấp vì nhiều luồng đứng chờ, nhưng CPU thấp không tự nó chứng minh có deadlock.

Lấy thông tin khi có dấu hiệu bị kẹt (cần JDK tools và PID của tiến trình):

~~~bash
jcmd <PID> Thread.print -l
~~~

Trong Java, `ThreadMXBean.findDeadlockedThreads()` có thể tìm vòng chờ liên quan đến monitor và *ownable synchronizer* được JVM quản lý, chẳng hạn `ReentrantLock`. Phương thức có thể trả `null` nếu không tìm thấy; nó không bảo đảm phát hiện mọi kiểu chờ logic, chờ I/O hoặc khóa ở hệ thống ngoài JVM. Một thread dump đơn lẻ cũng không chứng minh được mọi trường hợp treo.

### 3.3 Cách phòng tránh

- **Thống nhất thứ tự lấy khóa** trên mọi đường chạy: nếu phải lấy cả A và B, luôn lấy A trước B. Với nhiều đối tượng động, xác định thứ tự ổn định theo khóa/ID; xử lý cả trường hợp bằng nhau.
- Giữ phạm vi khóa ngắn, tránh gọi tác vụ chặn dài hoặc gọi mã bên ngoài khó kiểm soát trong lúc đang giữ khóa.
- Khi phù hợp, dùng `ReentrantLock.tryLock()` hoặc bản có timeout, giải phóng đúng khóa trong `finally` và có chiến lược retry/hủy rõ ràng. Timeout có thể giúp phục hồi nhưng không tự bảo đảm hệ thống luôn tiến triển.
- Tránh lồng nhiều khóa không cần thiết; xem xét một điểm đồng bộ nhất quán khi bất biến phải bảo vệ thực sự là một khối.

Không có quy tắc “thêm `synchronized` ở mọi nơi” để phòng deadlock: càng giữ nhiều khóa theo các thứ tự khác nhau càng có thể tạo chu trình chờ mới.

## 4. Livelock: liên tục phản ứng nhưng công việc không tiến triển

Hai luồng hoặc hai tác nhân cùng muốn “lịch sự”: nếu tranh chấp, mỗi bên nhường tài nguyên rồi lập tức thử lại. Họ có thể liên tục phát hiện nhau và cùng đổi hướng:

~~~text
Vòng 1: T1 thấy xung đột -> nhường; T2 thấy xung đột -> nhường
Vòng 2: T1 thử lại ngay -> xung đột; T2 thử lại ngay -> xung đột
Vòng 3: cùng nhường, cùng thử lại, ...
Kết quả: có hoạt động, nhưng số thao tác hoàn tất vẫn bằng 0
~~~

Đây là **tình huống minh họa cơ chế**; lịch chạy thực tế có thể phá sự đối xứng, vì thế không thể khẳng định cứ retry là livelock. Livelock cũng có thể xảy ra khi nhiều luồng liên tục hủy/gia hạn thao tác nhằm tránh xung đột hoặc khi hai phía liên tục chuyển trạng thái để đáp ứng nhau.

Dấu hiệu thường gặp:
- Log lặp lại “retry”, “conflict”, “yield”, “rollback” hoặc thay đổi trạng thái mà không có tác vụ hoàn tất.
- Các luồng vẫn chạy hoặc thức dậy thường xuyên; CPU có thể cao, nhưng không bắt buộc.
- Không thấy vòng “giữ khóa rồi chờ khóa” như deadlock; thread dump chỉ ở một thời điểm có thể không đủ. Cần theo dõi **tiến độ** qua nhiều thời điểm: số thao tác hoàn thành, số lần retry và thời gian xử lý.

Biện pháp:
- Retry có **giới hạn số lần hoặc deadline**; khi vượt ngưỡng thì báo lỗi rõ ràng/thực hiện phương án dự phòng.
- Dùng *backoff* với khoảng chờ thay đổi, đôi khi thêm độ ngẫu nhiên (*jitter*) để tránh hai tác nhân hành động đồng bộ. Cách này giảm xác suất, không phải một chứng minh bảo đảm tiến triển.
- Tạo thứ tự ưu tiên, điều phối một phía làm trước hoặc dùng hàng đợi để phá đối xứng.
- Thiết kế điều kiện retry có thể đạt điểm kết thúc; đo cả tốc độ hoàn thành, không chỉ hoạt động CPU.

## 5. So sánh nhanh khi gặp sự cố

| Góc nhìn | Race condition | Deadlock | Livelock |
| --- | --- | --- | --- |
| Vấn đề cốt lõi | Thao tác xen kẽ làm sai dữ liệu/quyết định | Các bên chờ lẫn nhau | Các bên phản ứng và retry mãi mà không hoàn thành |
| Luồng có chạy không? | Thường vẫn chạy và kết thúc | Các luồng tham gia bị chặn/chờ | Có hoạt động hoặc lặp thức dậy |
| Triệu chứng dễ thấy | Kết quả sai, mất cập nhật, lỗi thất thường | Tác vụ treo; có chu trình chờ tài nguyên | Nhiều retry, không tăng số việc hoàn thành |
| Bằng chứng cần tìm | Shared state, bất biến, thứ tự thao tác, đồng bộ | Chủ sở hữu khóa, khóa chờ, chu trình | Log retry, trạng thái thay đổi, throughput qua thời gian |
| Hướng xử lý | Nguyên tử hóa thao tác, bảo vệ bất biến, giảm chia sẻ | Thứ tự khóa cố định, giảm lồng khóa, timeout phù hợp | Phá đối xứng, backoff, giới hạn retry, điều phối |

**Phân biệt thêm với starvation:** một luồng chờ quá lâu vì liên tục bị các luồng khác giành tài nguyên/lịch CPU, trong khi các luồng khác vẫn tiến triển. Starvation không nhất thiết có vòng chờ của deadlock hoặc vòng phản ứng đối xứng của livelock. Một hệ thống cũng có thể mắc nhiều vấn đề đồng thời.

## 6. Quy trình nhận diện thực tế

1. **Xác định triệu chứng và bất biến.** Có dữ liệu sai hay tác vụ không hoàn thành? Ghi rõ kết quả mong đợi và kết quả quan sát được (ví dụ tổng số lần tăng, số yêu cầu hoàn tất).
2. **Khoanh vùng shared state và đường lấy khóa.** Với kết quả sai, tìm chuỗi đọc–sửa–ghi hoặc kiểm tra–hành động, kiểm tra xem toàn bộ chuỗi đã được bảo vệ hay chưa. Với tác vụ treo, truy ra các khóa đang giữ và khóa đang chờ.
3. **Thu thập bằng chứng qua thời gian.** Lấy nhiều thread dump cách nhau một khoảng phù hợp, cùng log, số lượng retry và tốc độ hoàn thành. Một snapshot `RUNNABLE` hoặc `WAITING` chưa đủ xác định livelock/deadlock.
4. **Đối chiếu với mô hình.** Dữ liệu sai do xen kẽ -> nghi race condition. Chu trình chờ tài nguyên -> nghi deadlock. Có hoạt động/retry nhưng không có tiến triển -> nghi livelock. Nếu chỉ một luồng bị bỏ đói trong khi luồng khác hoàn thành -> xem xét starvation.
5. **Sửa đúng nguyên nhân rồi kiểm chứng.** Thêm kiểm thử đồng thời, khẳng định bất biến (assertion), theo dõi latency/throughput và chạy dưới nhiều mức tải. Các bài stress test có thể giúp tái hiện race nhưng một lần chạy thành công không chứng minh chương trình không có race.

### Những nhầm lẫn thường gặp

- “Đã thêm `volatile` nên `counter++` an toàn” — **sai**; visibility không thay thế atomicity.
- “Có `ConcurrentHashMap` nên mọi chuỗi thao tác trên map đều nguyên tử” — **sai**; cần xét hợp đồng của từng phương thức và bất biến xuyên nhiều bước.
- “`sleep` vài mili giây làm hết lỗi” — **không bảo đảm**; chỉ thay đổi lịch chạy.
- “CPU thấp nghĩa là deadlock, CPU cao nghĩa là livelock” — **không đủ**; phải dựa trên vòng chờ hoặc thiếu tiến triển.
- “Thấy `BLOCKED` là có deadlock” — **sai**; chờ khóa tạm thời là bình thường. Cần chứng minh vòng chờ kéo dài.
- “Dùng `tryLock` và retry vô hạn sẽ giải quyết deadlock” — **chưa đủ**; có thể đổi thành livelock hoặc starvation nếu không có chiến lược tiến triển.

## 7. Tự kiểm tra

1. Hai luồng cùng cập nhật `balance` theo mẫu “đọc, tính, ghi”; kết quả có lúc bị mất tiền hoặc cộng thừa. Đây là dấu hiệu gì? **Race condition**; phải kiểm tra nguyên tử của toàn bộ phép cập nhật và bất biến liên quan.
2. T1 giữ khóa A, chờ B; T2 giữ B, chờ A. Đây là gì? **Deadlock**; thống nhất thứ tự khóa hoặc thiết kế lại phạm vi khóa.
3. Hai worker luôn phát hiện xung đột, nhường rồi retry hàng nghìn lần, nhưng không hoàn thành tác vụ. Đây có thể là gì? **Livelock**; cần kiểm tra log/throughput và cách phá đối xứng, giới hạn retry.
4. Một worker chờ rất lâu trong khi những worker khác liên tục hoàn thành. Điều này nhất thiết là deadlock? **Không**; có thể là **starvation**.

**Ghi nhớ:** Race condition đe dọa **tính đúng của kết quả**; deadlock và livelock đe dọa **khả năng tiến triển**, nhưng theo hai cơ chế khác nhau. Chẩn đoán dựa trên bất biến, quan hệ chờ và tiến độ thực tế trước khi chọn công cụ sửa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-009">Concurrency overhead: context switch, contention, scheduling</a>

<details>
<summary>Xem chi tiết</summary>

# Concurrency overhead trong Java: context switch, contention và scheduling

> **Phạm vi:** cơ chế và chi phí của lập trình đồng thời trong Java nói chung (JVM, Java threads, Java concurrency API và hệ điều hành). Nội dung không đi vào phần tích hợp concurrency riêng của Spring.

## 1. Mục tiêu và bức tranh tổng thể

Sau bài này, bạn có thể:
- Giải thích vì sao tăng số lượng thread không đồng nghĩa tăng tốc độ xử lý.
- Phân biệt chi phí **context switch**, **contention** (tranh chấp tài nguyên) và **scheduling** (lập lịch), đồng thời nhận ra chúng thường khuếch đại lẫn nhau.
- Phân biệt thread đang chạy, sẵn sàng chạy, chờ khóa và chờ I/O; đọc đúng các trạng thái Java.
- Đo, kiểm chứng nút thắt rồi chọn cách giảm overhead phù hợp, thay vì tùy tiện tăng pool hoặc đổi cơ chế đồng bộ.

**Concurrency** cho phép nhiều công việc cùng tiến triển theo thời gian; **parallelism** nghĩa là thực sự có nhiều công việc chạy cùng lúc trên các tài nguyên tính toán khác nhau. Ví dụ máy có 2 lõi CPU khả dụng mà chương trình tạo 100 thread chạy tính toán liên tục thì tại một thời điểm chỉ có số luồng tương ứng tài nguyên CPU đang chạy; số còn lại phải đợi lượt. Thêm thread có thể tăng overhead mà không tăng lượng CPU.

Một tác vụ có thể tiêu tốn thời gian vào: thực thi mã hữu ích, đợi được xếp lịch, tranh chấp khóa/tài nguyên, đồng bộ dữ liệu, đợi I/O và các chi phí JVM khác. Các giai đoạn có thể chồng lấn giữa các thread, nên không cộng các con số đo của mọi thread một cách máy móc để suy ra wall-clock latency. Hai thước đo cơ bản:

- **Throughput:** số công việc hoàn thành trên mỗi đơn vị thời gian (ví dụ yêu cầu/giây).
- **Latency:** thời gian một công việc từ lúc đến đến lúc hoàn thành; quan tâm thêm p95/p99, không chỉ trung bình.

Một thay đổi tốt cho throughput có thể làm tail latency xấu đi nếu hàng đợi dài lên hoặc một số thread bị đói CPU.

## 2. Context switch: chuyển CPU giữa các luồng

**Context switch** trong ngữ cảnh này chủ yếu là hệ điều hành ngừng thực thi một thread và tiếp tục một thread khác, thường phải lưu/khôi phục trạng thái thực thi như thanh ghi, stack pointer và thông tin lập lịch. Với **platform thread** trong Java (cách sử dụng thông thường ở Java 17), JVM ánh xạ thread Java đến thread hệ điều hành. Đây là sự kiện ở tầng OS; nó không đồng nghĩa với lệnh gọi `Thread.yield()`, một thao tác synchronized hay mọi lần Java đổi trạng thái thread.

Tình huống thường làm thread nhường hoặc mất CPU:
1. **Tự nguyện (voluntary):** đi vào chờ I/O, chờ điều kiện/khóa, ngủ, hoặc cơ chế chờ khiến OS có thể chạy thread khác.
2. **Không tự nguyện (involuntary / preemption):** hết phần CPU được cấp theo chính sách, bị thread khác ưu tiên hơn, hoặc OS cần phân phối CPU.
3. **Di chuyển CPU (migration):** một thread được chạy ở lõi khác; việc này có thể làm giảm lợi ích của cache đã được làm nóng. Migration và context switch là hai sự kiện khác nhau.

Chi phí của switch không chỉ là số chu kỳ lưu/khôi phục thanh ghi. Sự gián đoạn có thể ảnh hưởng cache và locality; chuyển đổi giữa những không gian địa chỉ khác nhau còn có các hệ quả quản lý bộ nhớ tùy CPU/OS. Chi phí thực tế **không có hằng số nanosecond chung cho mọi máy**; nó phụ thuộc phần cứng, tải, loại thread, cache và đường đi của kernel.

**Ví dụ:** 8 thread CPU-bound tranh nhau 2 lõi, mỗi thread chỉ thực hiện một việc tính toán dài. Lập lịch phải luân phiên quyền chạy; khi tăng lên 80 thread, tổng phần tính toán hữu ích không tự nhiên tăng lên 10 lần. Nhiều thời gian hơn có thể mất vào lập lịch, chuyển ngữ cảnh và mất locality.

Không phải mọi context switch đều có hại: nếu một thread đang chờ I/O, chuyển CPU cho việc khác là cơ chế giữ tài nguyên bận một cách hữu ích. Vấn đề là **switch dư thừa so với lượng công việc có ích**, nhất là khi các task quá nhỏ hoặc số thread chạy được vượt quá năng lực CPU.

### Trạng thái Java không phải đồng hồ CPU của OS

| `Thread.State` | Ý nghĩa chính trong Java | Cách đọc khi chẩn đoán |
|---|---|---|
| `RUNNABLE` | Đang chạy **hoặc** sẵn sàng chạy ở JVM; có thể đang đợi lượt CPU từ OS | Không suy ra thread chắc chắn đang dùng CPU |
| `BLOCKED` | Chờ vào/re-enter **intrinsic monitor** (`synchronized`) | Gợi ý tranh chấp monitor |
| `WAITING` | Đợi vô thời hạn (ví dụ `Object.wait`, `Thread.join`, `LockSupport.park`) | Không đồng nghĩa deadlock |
| `TIMED_WAITING` | Đợi có thời hạn (`sleep`, timed wait/park/join) | Có thể là hành vi chủ đích |
| `NEW` / `TERMINATED` | Chưa chạy / đã xong | Không trực tiếp phản ánh tải hiện tại |

Đặc biệt, một thread đang đợi `ReentrantLock` có thể ở `WAITING` do `LockSupport.park`, **không** ở `BLOCKED`. Muốn kết luận nguyên nhân phải nhìn cả stack trace, lock owner, số liệu và lịch sử theo thời gian. Thread dump là ảnh chụp tại một thời điểm.

## 3. Contention: nhiều thread đòi cùng tài nguyên

**Contention** xuất hiện khi nhiều bên cùng cần một tài nguyên hữu hạn: monitor/lock, biến atomic thường xuyên cập nhật, một queue chung, connection pool, file, bộ nhớ/cache line hoặc thiết bị I/O. Khi tranh chấp, thread có thể:

- **Block/park:** nhường CPU rồi chờ được đánh thức; giảm CPU lãng phí nhưng có chi phí wake-up và được xếp lịch lại.
- **Spin/retry:** liên tục kiểm tra hoặc thử lại (ví dụ vòng CAS thất bại); tránh một số lần park ở trường hợp chờ ngắn nhưng đốt CPU, gây traffic bộ nhớ và có thể làm chậm thread đang giữ tài nguyên.
- **Nối hàng:** độ trễ tăng theo số bên đến và thời gian mỗi bên giữ tài nguyên. Dù từng critical section ngắn, tổng lưu lượng tới hotspot đủ cao vẫn làm tắc nghẽn.

Các kiểu tranh chấp trong Java:

1. **Monitor:** `synchronized` bảo vệ tính nhất quán; nếu nhiều thread cần cùng monitor, phần mã được bảo vệ phải đi lần lượt. `Object.wait()` tạm nhả monitor tương ứng khi đợi; `Thread.sleep()` **không** tự nhả monitor đang giữ.
2. **Explicit lock:** `ReentrantLock`, `ReadWriteLock` hoặc các đồng bộ dựa trên AQS có cơ chế riêng để quản lý người đợi; không giả định chúng luôn nhanh hơn `synchronized`. Chế độ fairness có thể giúp chính sách chờ dễ dự đoán hơn trong một số tải, nhưng thường có trade-off với throughput.
3. **Atomic/CAS:** `AtomicLong.incrementAndGet()` tránh khóa monitor nhưng cập nhật một ô nhớ nóng gây cạnh tranh và retry/cache coherence. “Lock-free” **không** có nghĩa “không contention” hoặc luôn nhanh.
4. **False sharing:** hai giá trị logic độc lập nhưng nằm trên cùng cache line và bị các lõi ghi thường xuyên; cache line vẫn phải trao đổi quyền sở hữu. Đây không phải tranh chấp cùng một khóa. Cần đo thực tế trước khi dùng padding/`@Contended`.
5. **Tài nguyên ngoài CPU:** thread tranh connection pool hoặc hàng đợi cũng có thể tăng latency; tăng số thread không tạo ra thêm database connections.

**Ví dụ nút thắt:** 16 worker cùng vào `synchronized (sharedLock)` để cập nhật một cấu trúc chung. Chỉ một worker chạy critical section tại một thời điểm; worker còn lại có thể bị block hoặc thức dậy rồi lại tranh khóa. Kết quả có cả **contention** lẫn **wake-up/scheduling overhead**.

Giảm contention bằng cách thu nhỏ **critical section**, làm công việc bên ngoài khóa, chia dữ liệu theo partition/key, dùng thread-confined/local state rồi gộp kết quả, hoặc chọn primitive phù hợp với tần suất đọc/ghi. Không tách khóa tùy tiện nếu invariant của dữ liệu yêu cầu thay đổi nhiều trường phải nguyên tử cùng nhau.

## 4. Scheduling: ai được chạy, khi nào và trên CPU nào?

Có ít nhất **hai tầng lập lịch** cần phân biệt:

- **Hệ điều hành:** quyết định platform thread nào được CPU chạy, ưu tiên, preemption và phân phối giữa các lõi. Chi tiết phụ thuộc OS và cấu hình; Java không cam kết một thứ tự thực thi cố định.
- **Ứng dụng / executor:** quyết định task được giao cho thread nào, task nào nằm trong queue, pool có bao nhiêu worker. `ExecutorService`/`ThreadPoolExecutor` quản lý tầng này; task được lấy khỏi queue **không bảo đảm** thread của nó lập tức có CPU.

Lưu ý:
- `Thread.yield()` chỉ là **gợi ý** cho scheduler, không bảo đảm nhường cho ai hoặc tạo fairness. Không dùng nó để sửa race condition.
- `Thread.setPriority()` không phải hợp đồng về latency, thứ tự hay quyền CPU giữa các hệ điều hành.
- CPU-bound workload thường cần lượng worker chạy tích cực xấp xỉ năng lực CPU đo được; tăng hàng chục thread chạy CPU trên ít lõi thường không có lợi. I/O-bound workload có thể cần concurrency lớn hơn do nhiều task chờ, nhưng phải tính giới hạn queue, bộ nhớ, pool kết nối và backpressure.
- Nhiều task cực nhỏ còn chịu chi phí tạo task, enqueue/dequeue, đồng bộ queue và wake-up; batch vừa phải đôi khi hiệu quả hơn task-per-item.
- Work stealing (ví dụ `ForkJoinPool`) hỗ trợ cân bằng task phân chia được; nó không giải quyết contention trên cùng một lock hoặc CPU bị oversubscribe.

### Thực tế của một pool

Giả sử có 4 CPU khả dụng, pool 32 worker và 1.000 task CPU-bound. 32 worker không thể đồng thời thực thi toàn bộ trên 4 CPU. Ngoài chờ lượt CPU, task còn chờ trong queue. Nếu chạy cùng pool với việc blocking dài, những task ngắn có thể bị chậm vì worker bị chiếm. Hãy đo queue wait, queue length, active workers, CPU saturation và p95/p99; chỉ điều chỉnh pool sau khi xác định bottleneck.

**Java 21+ (mở rộng):** virtual thread có thể unmount khỏi carrier thread khi nhiều thao tác blocking được hỗ trợ, giảm nhu cầu giữ một OS thread cho mỗi tác vụ đang chờ. Nhưng chúng vẫn được scheduler phân phối qua số carrier hữu hạn, **không** tạo thêm lõi CPU, không xóa contention trên state chung và không làm CPU-bound code tự động nhanh hơn. Ví dụ bên dưới vẫn chạy Java 17 trở lên và dùng platform thread.

## 5. Ba nguồn overhead liên hệ với nhau thế nào?

Một dòng xử lý thường gặp:

`Nhiều task đến → nhiều worker muốn cùng khóa → worker chờ/park → worker giữ khóa nhả khóa → OS đánh thức/xếp lịch worker kế tiếp → worker tranh khóa lại → kết quả chậm`.

Ở đây:
- **Contention** là nguyên nhân khiến task không thể tiến triển độc lập.
- **Scheduling** quyết định lúc nào thread được tiếp tục.
- **Context switching** có thể là một phần chi phí khi chuyển CPU giữa các thread.

Một trường hợp ngược lại: có 64 worker chạy các phép tính độc lập trên 4 lõi, không dùng khóa chung. Khi đó contention trên application lock có thể rất thấp, nhưng oversubscription và scheduling overhead vẫn cao. Cần xác định đúng hiện tượng thay vì gọi mọi vấn đề concurrency là “lock chậm”.

### Triệu chứng không tự chứng minh nguyên nhân

| Quan sát | Có thể là gì | Cần kiểm tra thêm |
|---|---|---|
| CPU cao, throughput không tăng khi thêm thread | CPU saturation, CAS retry, spin, cache contention | CPU profiling, số core thực dùng, hotspot atomic |
| Nhiều thread `BLOCKED` cùng monitor | Lock contention/critical section dài | Lock owner, stack, JFR monitor events |
| Nhiều thread `WAITING` | Worker nhàn rỗi, đợi queue/condition, hoặc chờ khóa kiểu park | Stack/queue metrics, loại wait, thời lượng |
| CPU thấp nhưng latency cao | Đợi I/O, queue, lock, cạn pool kết nối | Trace thời gian queue/I/O/lock, JFR |
| Context-switch rate tăng mạnh | Oversubscription, wake-up thường xuyên, blocking lặp | Voluntary/involuntary switch, CPU, throughput |
| p99 tăng khi throughput tăng | Hàng đợi/tail contention, GC hoặc các nút thắt khác | Queue wait, duration distribution, GC/JFR |

## 6. Thí nghiệm Java 17+: một điểm cập nhật chung

Ví dụ thực hành so sánh một `AtomicLong` (một điểm cập nhật atomic chung) và `LongAdder` (phân tán cập nhật khi có contention). Hai cách đều cần đếm chính xác sau khi worker kết thúc; kết quả **thời gian không cố định** và không mặc định `LongAdder` phải thắng trong mỗi lần chạy.

Tạo file **`OverheadLab.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class OverheadLab {
    private static final int THREADS = 4;
    private static final int INCREMENTS_PER_THREAD = 100_000;

    @FunctionalInterface
    interface Work { void run(); }

    private static long timeConcurrent(Work work) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[THREADS];

        for (int i = 0; i < THREADS; i++) {
            workers[i] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    work.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "worker-" + i);
            workers[i].start();
        }

        ready.await();
        long begin = System.nanoTime();
        start.countDown();
        for (Thread worker : workers) worker.join();
        return System.nanoTime() - begin;
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicLong atomic = new AtomicLong();
        long atomicNs = timeConcurrent(() -> {
            for (int i = 0; i < INCREMENTS_PER_THREAD; i++) atomic.incrementAndGet();
        });

        LongAdder adder = new LongAdder();
        long adderNs = timeConcurrent(() -> {
            for (int i = 0; i < INCREMENTS_PER_THREAD; i++) adder.increment();
        });

        System.out.println("AtomicLong count=" + atomic.get());
        System.out.println("LongAdder count=" + adder.sum());
        System.out.printf("AtomicLong elapsed=%.2f ms%n", atomicNs / 1_000_000.0);
        System.out.printf("LongAdder elapsed=%.2f ms%n", adderNs / 1_000_000.0);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 OverheadLab.java
java OverheadLab
```

Hai dòng kết quả đếm **chắc chắn** (nếu chương trình hoàn tất bình thường):

```text
AtomicLong count=400000
LongAdder count=400000
```

Hai dòng tiếp theo có dạng `AtomicLong elapsed=... ms` và `LongAdder elapsed=... ms`, với số thay đổi tùy máy/lần chạy. `CountDownLatch` cho các thread bắt đầu công việc gần cùng thời điểm; `join()` bảo đảm phép đếm cuối được đọc sau khi worker hoàn tất. `System.nanoTime()` đo khoảng elapsed, không đo riêng context-switch cost.

Cần hiểu giới hạn của thí nghiệm:

- `AtomicLong` có ngữ nghĩa đọc giá trị atomic trực tiếp; `LongAdder.sum()` **không** cung cấp snapshot nguyên tử khi đang có cập nhật đồng thời. Sau `join()`, dùng `sum()` để đọc tổng cuối là phù hợp.
- `LongAdder` có thêm chi phí và bộ nhớ; ở contention thấp `AtomicLong` có thể nhanh hơn. Cả hai không đại diện trực tiếp cho chi phí monitor `synchronized`.
- Benchmark micro rất dễ lệch vì warm-up/JIT, GC, độ ưu tiên, số lõi, nhiệt/CPU frequency, thứ tự phép đo và nhiễu hệ thống. Chạy `java OverheadLab` một lần **không đủ** để kết luận hiệu năng.
- Để so sánh đáng tin hơn: dùng **JMH** với warm-up, nhiều iteration/fork và môi trường tải kiểm soát; báo cả phân phối kết quả. Đo throughput/latency dưới tải thực tế mới quyết định tối ưu production.

**Bài thử mở rộng:** giữ cùng mã, chạy với `THREADS = 1, 2, 4, 8, 16` và cố định số phép tăng **trên mỗi thread**; khi đó tổng công việc tăng theo số thread, nên **không** được dùng thời gian toàn chương trình để so scalability trên khối lượng công việc cố định. Muốn so cùng lượng việc, chia một tổng phép tăng cố định cho số worker, kiểm tra số dư, warm-up và đo độc lập.

## 7. Quan sát overhead trong JVM và hệ điều hành

### Thread dump: biết đang đợi gì

Khi ứng dụng chạy đủ lâu, dùng PID Java thực tế:

```bash
jcmd <PID> Thread.print
```

Đọc nhóm stack có nhiều thread cùng đợi một monitor/lock, đối chiếu khóa đang được giữ, rồi chụp thêm vài lần cách nhau để phân biệt chờ dài với ảnh chụp tình cờ. `BLOCKED`/`WAITING` chỉ cho biết trạng thái ở thời điểm lấy dump; đừng suy ra tổng thời gian chờ chỉ từ một ảnh.

### ThreadMXBean: số đếm ở tầng Java

`java.lang.management.ManagementFactory.getThreadMXBean()` cho phép đọc dữ liệu thread. Trước khi bật contention monitoring, phải kiểm tra `isThreadContentionMonitoringSupported()`; nếu được hỗ trợ, `setThreadContentionMonitoringEnabled(true)` giúp ghi nhận `ThreadInfo.getBlockedTime()` và `getWaitedTime()` (có overhead đo đạc). `getBlockedCount()` đo số lần đợi **vào monitor**; nó không phải tổng số context switch hay tổng số lần CAS thất bại. Thời gian chờ có thể là `-1` khi monitoring chưa bật hoặc không được hỗ trợ. Không dùng thông tin quản trị thread để điều khiển đồng bộ của chính chương trình.

```java
// Đoạn mã minh họa đặt bên trong một method; không phải chương trình độc lập.
var mx = java.lang.management.ManagementFactory.getThreadMXBean();
if (mx.isThreadContentionMonitoringSupported()
        && !mx.isThreadContentionMonitoringEnabled()) {
    mx.setThreadContentionMonitoringEnabled(true);
}
var info = mx.getThreadInfo(Thread.currentThread().getId());
if (info != null) {
    System.out.println("monitor blocked count = " + info.getBlockedCount());
    System.out.println("monitor blocked time ms = " + info.getBlockedTime());
}
```

### Java Flight Recorder (JFR): tìm điểm chờ có ngữ cảnh

Chạy workload đủ lâu và ghi JFR (JDK 17+):

```bash
java -XX:StartFlightRecording=filename=overhead.jfr,settings=profile,dumponexit=true OverheadLab
jfr summary overhead.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.JavaMonitorWait,jdk.ThreadPark overhead.jfr
```

`jdk.JavaMonitorEnter` gợi ý tranh chấp monitor, `jdk.JavaMonitorWait` là chờ monitor, `jdk.ThreadPark` là parking; kiểm tra duration, stack trace và thread. **Không có event không chứng minh không có chờ:** các mẫu cấu hình JFR có thể có ngưỡng thời gian, tắt một số event, và workload quá ngắn có thể không tạo event hữu ích. Với sản phẩm thực, thiết kế phiên ghi đủ dài và xem bằng JDK Mission Control khi cần phân tích timeline.

### OS-level: đo context switch thật

Trên Linux, có thể dùng công cụ giám sát thread như:

```bash
pidstat -w -t -p <PID> 1
```

Nếu đã cài `sysstat`, theo dõi các chỉ số voluntary/involuntary context switch theo thread; đối chiếu CPU utilization, runnable queue và throughput. Trên hệ điều hành khác, dùng công cụ profiling/counters tương ứng. Chỉ nhìn tổng số context switch trên toàn máy không đủ để quy trách nhiệm cho process Java. Thread CPU time, Java blocked time, OS switch count và wall-clock latency **là các số đo khác nhau**, không thay thế cho nhau.

## 8. Phương pháp tối ưu theo nguyên nhân

**Bước 1 — Tạo baseline có tính so sánh.** Cố định input, tải, cấu hình JVM, số lõi khả dụng; ghi throughput, latency p50/p95/p99, CPU, GC, số active thread, queue length/wait và kết quả correctness. Đo nhiều lần trước khi kết luận.

**Bước 2 — Phân loại bottleneck.**
- **CPU đầy, nhiều runnable, ít chờ:** xem CPU profile, oversubscription, task quá nhỏ, CAS retry hoặc false sharing.
- **Nhiều thread chờ monitor/lock:** dùng JFR/dump để tìm lock owner, critical section và tần suất truy cập.
- **Nhiều thread chờ queue/I/O:** xem slow external calls, connection pool, năng lực downstream và backpressure.
- **Thread tạo/hủy liên tục:** kiểm tra model thực thi, worker reuse, task granularity.

**Bước 3 — Áp dụng thay đổi nhỏ, đo lại.**
- Rút ngắn critical section; chuyển tính toán/I/O ra ngoài lock khi an toàn; giảm lock granularity nếu invariant cho phép.
- Partition/shard state theo key; thread-local aggregation hoặc `LongAdder` cho thống kê cập nhật nhiều mà không cần snapshot nhất quán trong lúc cập nhật.
- Giới hạn runnable workers cho CPU-bound workload; tách pool theo bản chất blocking/CPU khi có căn cứ; dùng queue **có giới hạn** và cơ chế backpressure rõ ràng.
- Tránh thức dậy liên tục để polling; dùng blocking queue/condition với điều kiện đúng thay vì `sleep` và `yield` rải rác.
- Gộp thao tác nhỏ thành batch hợp lý; tránh tạo thread và futures cho từng phép toán quá nhỏ.
- Nếu cần bảo đảm thời gian chờ, đánh giá fairness/starvation có đo đạc. Fairness thường đánh đổi throughput; test cả tail latency.

**Bước 4 — Chứng minh cải thiện và giữ correctness.** Đo lại trên tải đại diện, đồng thời kiểm tra race, lost update, deadlock, starvation, thứ tự quan sát và yêu cầu visibility. Một cách “nhanh hơn” nhưng bỏ đồng bộ cần thiết là lỗi, không phải tối ưu.

## 9. Những nhầm lẫn hay gặp

1. **“Nhiều thread luôn nhanh hơn.”** Sai nếu CPU đã bão hòa, queue/lock bị tắc hoặc overhead chiếm ưu thế.
2. **“RUNNABLE nghĩa là thread đang dùng CPU.”** Sai: có thể đang sẵn sàng nhưng chưa được OS chạy.
3. **“BLOCKED là mọi kiểu chờ lock.”** Sai: Java `BLOCKED` mô tả chờ monitor; park từ các synchronizer khác có trạng thái khác.
4. **“Lock-free nghĩa là không tranh chấp.”** Sai: CAS retries và cache-coherence traffic vẫn có thể tốn nhiều CPU.
5. **“sleep/yield chữa race hoặc bảo đảm fairness.”** Sai: cần thiết kế synchronization, condition và lifecycle đúng.
6. **“CPU thấp thì concurrency không có vấn đề.”** Sai: thread có thể kẹt I/O, queue hoặc lock mà ít tiêu thụ CPU.
7. **“Thấy nhiều WAITING là deadlock.”** Sai: executor worker nhàn rỗi cũng thường WAITING; cần phân tích wait graph và tiến triển thực tế.
8. **“Đo wall-clock bằng nanoTime cho biết context switch.”** Sai: wall-clock gồm cả thời gian chạy, đợi, bị preempt và nhiễu.
9. **“Đổi synchronized sang ReentrantLock/AtomicLong sẽ tự nhanh.”** Sai: tính đúng đắn, fairness, contention pattern và tải mới quyết định.
10. **“Virtual thread xóa chi phí scheduling.”** Sai: vẫn có scheduling, carrier threads và tài nguyên chung.

## 10. Tự kiểm tra

**Tình huống A:** 8 CPU core, 8 thread xử lý phép tính độc lập đạt 100% CPU; nâng lên 64 thread làm p99 xấu đi mà throughput không tăng. **Giải thích:** CPU-bound oversubscription tăng thời gian chờ lượt và có thể tăng context switching/cache disruption. Thử giới hạn active worker quanh CPU khả dụng rồi đo lại.

**Tình huống B:** CPU chưa cao, 40 thread cùng `BLOCKED` trên một monitor, thread giữ monitor làm I/O. **Giải thích:** lock contention do critical section giữ quá lâu. Đưa I/O ra ngoài khóa khi giữ được invariant, hoặc thay kiến trúc chia sẻ dữ liệu rồi dùng JFR/trace xác minh.

**Tình huống C:** Một bộ đếm `AtomicLong` bị tăng từ rất nhiều worker, CPU cao dù không có thread `BLOCKED`. **Giải thích:** hotspot trên một ô nhớ atomic có thể làm CAS/cache coherence tốn chi phí. Cân nhắc `LongAdder` nếu semantics cho phép; kiểm tra độ chính xác và đo under load.

**Tình huống D:** 200 thread đều `WAITING` trong `BlockingQueue.take()` khi queue rỗng. **Giải thích:** đây thường là worker nhàn rỗi đợi công việc; trạng thái WAITING tự nó chưa chứng minh lỗi. Cần nhìn throughput, incoming workload và độ dài queue.

## Tài liệu tham khảo

- [Java SE 17: `Thread.State`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.State.html) — định nghĩa trạng thái Java.
- [Java SE 17: `ThreadInfo`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadInfo.html) — blocked/waited time, owner và monitor.
- [Java SE 17: `ThreadMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html) — kiểm tra/bật contention monitoring.
- [Java SE 17: `LongAdder`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html) — đặc tính thống kê dưới contention.
- [JDK 17: lệnh `jfr`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html) — xem/summarize recording.
- [OpenJDK: Java Flight Recorder](https://dev.java/learn/jvm/monitoring-troubleshooting/jfr/) — cấu hình và phân tích bản ghi.

**Ý chính cần nhớ:** xác định công việc nào thực sự dùng CPU, công việc nào đang đợi resource/queue, rồi đo context switching và contention trong điều kiện tải thực; tăng thread chỉ hữu ích khi có tài nguyên và sự độc lập đủ để chúng tiến triển.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-010">Khi nào nên dùng tuần tự thay vì đa luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào nên dùng tuần tự thay vì đa luồng trong Java?

## 1. Hiểu đúng vấn đề

**Thực thi tuần tự (sequential execution)** là xử lý các bước lần lượt trên một luồng điều khiển: bước trước hoàn tất thì bước tiếp theo mới bắt đầu. **Thực thi đa luồng (multithreading)** cho phép nhiều tác vụ tiến triển trong cùng một khoảng thời gian, có thể thực sự chạy song song trên nhiều lõi CPU hoặc thay phiên nhau khi có tác vụ chờ.

Đa luồng **không mặc định nhanh hơn**. Quyết định phụ thuộc vào kích thước và tính độc lập của công việc, tỷ lệ tính toán so với thời gian chờ I/O, yêu cầu về thứ tự, trạng thái chia sẻ, tài nguyên và hiệu năng thực tế.

Ba khái niệm cần phân biệt:

- **Tuần tự:** một chuỗi công việc được xử lý theo thứ tự; mã thường dễ theo dõi hơn.
- **Đồng thời (concurrency):** nhiều công việc có thể cùng tiến triển, dù chưa chắc đang thực sự chạy cùng lúc.
- **Song song (parallelism):** các phần tính toán thực sự chạy đồng thời trên nhiều lõi hoặc đơn vị thực thi.

Một luồng cũng có thể bị chặn khi gọi I/O. Nhiều luồng cũng có thể không giúp tăng tốc nếu cùng tranh một tài nguyên.

## 2. Khi nào nên ưu tiên tuần tự?

### 2.1. Công việc nhỏ, chi phí mỗi task thấp

Khởi tạo hoặc điều phối nhiều luồng phát sinh chi phí: đưa task vào hàng đợi, chuyển ngữ cảnh, đồng bộ, quản lý kết quả và bộ nhớ. Với vòng lặp nhỏ hoặc phép tính đơn giản, chi phí này có thể lớn hơn thời gian xử lý tác vụ.

Ví dụ, cộng vài trăm số, kiểm tra ít đối tượng hoặc xử lý một cấu hình nhỏ thường nên bắt đầu bằng vòng lặp tuần tự. Không có một mốc số phần tử chung cho mọi chương trình: cùng 1.000 phần tử nhưng mỗi phần tử có thể là một phép cộng hoặc một phép tính đắt đỏ. **Đo theo workload thực**, không quyết định chỉ bằng số phần tử.

### 2.2. Các bước có quan hệ phụ thuộc

Nếu B cần kết quả của A và C cần B, ba bước tạo thành đường phụ thuộc A → B → C. Đưa chúng lên ba luồng không làm đường phụ thuộc biến mất; thay vào đó còn cần cơ chế chờ và chuyển kết quả.

Ví dụ: đọc dữ liệu → kiểm tra tính hợp lệ → tính kết quả → cập nhật một trạng thái dựa trên kết quả. Giữ tuần tự cho phần phụ thuộc giúp thể hiện điều kiện trước/sau rõ ràng. Nếu trong một bước có nhiều nhánh thật sự độc lập, vẫn có thể tách riêng nhánh đó để xử lý đồng thời.

### 2.3. Cần thứ tự chặt chẽ và hành vi dễ dự đoán

Những thao tác cần thứ tự cập nhật, chuỗi chuyển trạng thái, thuật toán có phụ thuộc vị trí hoặc việc ghi vào một tài nguyên phải tuân theo thứ tự thường dễ triển khai tuần tự.

Mã tuần tự có ít lịch thực thi phải xét hơn, thuận tiện để đọc stack trace, tái hiện lỗi và kiểm thử. Tuy nhiên, tuần tự **không tự bảo đảm** thuật toán đúng, transaction an toàn hay dữ liệu bên ngoài không thay đổi.

### 2.4. Có trạng thái mutable dùng chung khó đồng bộ

Nhiều luồng cùng chạy `count++` trên một biến `int` có thể làm mất lượt tăng vì câu lệnh gồm đọc, cộng và ghi; đây không phải một phép nguyên tử. `volatile` giúp bảo đảm một số quy tắc visibility nhưng **không làm `count++` trở nên nguyên tử**.

Với lượng công việc nhỏ và ít yêu cầu throughput, xử lý tuần tự có thể tránh chi phí khóa và rủi ro race. Nếu cần đa luồng, hãy cân nhắc chia nhỏ dữ liệu không chia sẻ, immutable objects, `AtomicInteger`, đồng bộ hoặc collection concurrent thích hợp.

### 2.5. Tài nguyên cuối có thể xử lý từng việc một

Nếu mọi task cùng chờ một khóa, một thiết bị đơn luồng hoặc một tài nguyên có giới hạn chặt, nhiều worker không làm tài nguyên đó nhanh hơn. Hệ thống có thể chỉ thêm hàng đợi, timeout và tiêu thụ bộ nhớ.

Ví dụ, nhiều thao tác cùng cần khóa ghi cho một trạng thái nhỏ: dù chạy trên nhiều luồng, đoạn được bảo vệ vẫn thực thi lần lượt. Tìm **nút thắt thực tế** trước khi tăng số luồng.

### 2.6. Chưa có bằng chứng concurrency giúp đạt mục tiêu

Với job nhỏ, chương trình dòng lệnh, khởi tạo ứng dụng hoặc xử lý ít dữ liệu, giải pháp tuần tự thường là baseline tốt: dễ hiểu, ít lỗi và dễ đo. Hãy giữ cách này nếu nó đã đáp ứng thời gian phản hồi, throughput và mức dùng tài nguyên yêu cầu.

Đây không phải quy tắc “luôn dùng một luồng”. Khi một công việc chờ I/O lâu làm nghẽn công việc khác, hoặc có khối tính toán lớn và độc lập, cần đánh giá các phương án đồng thời.

## 3. Java 17+: ví dụ vòng lặp tuần tự

Tạo tệp `SmallSum.java`:

```java
import java.util.stream.IntStream;

public class SmallSum {
    public static void main(String[] args) {
        int[] values = IntStream.rangeClosed(1, 1_000).toArray();
        long sum = 0;

        for (int value : values) {
            sum += value;
        }

        System.out.println(sum);
    }
}
```

Biên dịch và chạy:

```bash
javac SmallSum.java
java SmallSum
```

Đầu ra:

```text
500500
```

Chương trình cộng 1.000 số nguyên theo thứ tự trong một vòng lặp. Không có yêu cầu nghiệp vụ nào bắt buộc chia thành nhiều task. Có thể thực hiện phép cộng bằng Stream song song trong bài toán khác, nhưng với ví dụ nhỏ này chưa có bằng chứng nó sẽ nhanh hơn. **Đây là minh họa tính đúng đắn, không phải benchmark hiệu năng.**

## 4. Khi nào đa luồng đáng để thử?

| Tình huống | Hướng bắt đầu | Vì sao |
| --- | --- | --- |
| Phép tính đơn giản trên ít phần tử | Tuần tự | Tránh overhead điều phối |
| Các bước phụ thuộc trực tiếp | Tuần tự ở phần phụ thuộc | Luồng khác không xóa phụ thuộc dữ liệu |
| Trạng thái mutable dùng chung, ít tải | Tuần tự hoặc thiết kế lại trạng thái | Giảm race và chi phí đồng bộ |
| Nhiều khối CPU nặng, độc lập, đủ lớn | Thử song song giới hạn | Có thể dùng nhiều lõi CPU |
| Nhiều tác vụ I/O độc lập, thường xuyên chờ | Thử đồng thời giới hạn | Có thể che thời gian chờ |
| Các task cùng tranh một tài nguyên đơn | Đo nút thắt; thường giữ tuần tự | Tài nguyên cuối vẫn là giới hạn |
| Cần đáp ứng công việc khác khi một task chờ | Cân nhắc tách task | Tránh một task làm chặn luồng xử lý |

**CPU-bound:** phần lớn thời gian thực sự dùng CPU. Nếu có nhiều khối độc lập đủ lớn và nhiều lõi, song song có thể hữu ích; nếu số worker quá nhiều so với lõi hữu ích, tranh CPU và chuyển ngữ cảnh có thể gây chậm.

**I/O-bound:** phần lớn thời gian chờ mạng, đĩa hoặc dịch vụ. Đồng thời có thể tận dụng khoảng chờ, nhưng phải giới hạn số kết nối, file descriptor, yêu cầu tới dịch vụ và độ dài hàng đợi.

**Workload hỗn hợp:** cần đo thời gian xử lý và thời gian chờ, CPU, bộ nhớ, connection pool, timeout, throughput và latency. Không chọn số worker chỉ theo một nhãn CPU-bound/I/O-bound.

## 5. Java 17+: so sánh tuần tự với thread pool cho tác vụ chờ

Ví dụ **mô phỏng chờ I/O** bằng `Thread.sleep(120)`. Bốn tác vụ độc lập trả về một chuỗi riêng. Sleep minh họa thời gian chờ có thể chồng lấp, không phải phép đo mạng hoặc ổ đĩa thực.

Tạo tệp `SequentialOrConcurrent.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SequentialOrConcurrent {
    private static String simulateWait(int id) throws InterruptedException {
        Thread.sleep(120);
        return "job-" + id;
    }

    private static List<String> sequential() throws InterruptedException {
        List<String> results = new ArrayList<>();
        for (int id = 1; id <= 4; id++) {
            results.add(simulateWait(id));
        }
        return results;
    }

    private static List<String> concurrent()
            throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int id = 1; id <= 4; id++) {
                final int taskId = id;
                futures.add(pool.submit(() -> simulateWait(taskId)));
            }

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> a = sequential();
        List<String> b = concurrent();
        System.out.println(a);
        System.out.println(b);
        System.out.println("Ket qua tuong duong: " + a.equals(b));
    }
}
```

Biên dịch và chạy:

```bash
javac SequentialOrConcurrent.java
java SequentialOrConcurrent
```

Kết quả xác định khi tất cả tác vụ hoàn tất bình thường:

```text
[job-1, job-2, job-3, job-4]
[job-1, job-2, job-3, job-4]
Ket qua tuong duong: true
```

Bản tuần tự thực hiện bốn khoảng chờ nối tiếp. Thread pool có hai worker cho phép tối đa hai tác vụ tiến triển cùng lúc; các khoảng chờ có thể chồng lên nhau. Danh sách `Future` được duyệt theo **thứ tự gửi task**, do đó kết quả được lắp về đúng thứ tự đó, dù thứ tự hoàn thành thực tế có thể khác. `Future.get()` chờ kết quả task và tạo quan hệ đồng bộ để luồng nhận kết quả nhìn thấy tác dụng của task đã hoàn tất.

Không suy từ ví dụ rằng đa luồng sẽ “nhanh gấp hai”. Thời gian phụ thuộc việc lập lịch, thời gian tạo pool, tải máy và tài nguyên ngoài. Ví dụ đóng pool qua `shutdown()` sau khi đã lấy hết kết quả. Trong ứng dụng dài hạn, cần thêm chiến lược timeout, hủy, xử lý `ExecutionException`/`InterruptedException`, giới hạn hàng đợi, xử lý partial failure và quản lý vòng đời pool.

## 6. Chi phí và rủi ro của đa luồng

**Điều phối:** submit task, xếp hàng, chuyển ngữ cảnh, `Future`, ghép kết quả, đồng bộ. Pool tái sử dụng luồng giúp giảm chi phí tạo luồng liên tục nhưng không xóa toàn bộ overhead.

**Race, visibility, deadlock:** ghi cùng dữ liệu không được bảo vệ có thể mất cập nhật; lock có thể tranh chấp hoặc gây deadlock nếu lấy không nhất quán. Với dữ liệu bất biến, chia sẻ an toàn hơn nhưng vẫn phải xem khả năng tranh tài nguyên bên ngoài.

**Thông lượng và độ trễ:** xử lý được nhiều request/giây không có nghĩa từng request nhanh hơn. Hàng đợi và tranh tài nguyên có thể làm p95/p99 latency xấu đi. Cần đo cả thời gian trung bình, độ trễ đuôi và khả năng chịu tải.

**Tài nguyên:** nhiều platform thread tiêu thụ stack và tài nguyên hệ điều hành; nhiều task cùng lúc cần thêm heap, kết nối, băng thông hoặc file descriptor. Concurrency phải có giới hạn và cơ chế backpressure khi đầu vào nhanh hơn khả năng xử lý.

**Lỗi, timeout và hủy:** khi các task chạy đồng thời, lỗi có thể xuất hiện ở nhiều thời điểm; các task khác có thể vẫn chạy dù một task đã thất bại. Cần định nghĩa hành vi khi timeout, interrupt, cancellation, kết quả một phần và thứ tự áp dụng kết quả.

Java 17 có thể dùng `ExecutorService`, `CompletableFuture` và các primitive concurrent chuẩn của JDK tùy loại công việc. Chọn API nào cũng không thay thế việc chứng minh tính độc lập và đo lợi ích.

## 7. Đo lường trước khi quyết định

1. **Làm baseline tuần tự đúng:** xác định đầu vào, kết quả mong đợi và thời gian thực thi trên workload đại diện.
2. **Phân tích phụ thuộc:** các task có độc lập không, có cập nhật cùng trạng thái, cần thứ tự hay tranh tài nguyên nào không?
3. **Đo điểm nghẽn:** tỷ lệ CPU/I/O wait, lõi CPU hữu ích, memory, số kết nối, hàng đợi, throughput, latency p50/p95/p99 và timeout.
4. **Thử mức đồng thời có giới hạn:** thay đổi số worker từng bước, so sánh kết quả với tuần tự, kiểm tra đúng đắn dưới tải và khả năng hủy/khôi phục.
5. **Cân nhắc tổng chi phí:** nếu chỉ giảm ít thời gian nhưng mã khó debug, quản lý pool và kiểm thử phức tạp đáng kể, giải pháp tuần tự có thể tốt hơn.

Với tính toán song song, **định luật Amdahl** cho một ước lượng giới hạn lý tưởng: nếu tỷ lệ có thể song song hóa là `p` và có `k` đơn vị tính toán, thì:

```text
Speedup <= 1 / ((1 - p) + p / k)
```

Ví dụ `p = 0,8` và `k = 4` cho giới hạn lý tưởng `1 / (0,2 + 0,8 / 4) = 2,5` lần, chưa tính chi phí thêm. Phần tuần tự càng lớn, tăng worker càng ít tác dụng. Công thức này minh họa giới hạn song song tính toán và không trực tiếp dự đoán tốc độ che thời gian chờ I/O.

Không dùng một lần chạy `System.nanoTime()` của ví dụ nhỏ để kết luận microbenchmark JVM: JIT compilation, warm-up, tối ưu hóa, GC và nhiễu môi trường đều ảnh hưởng. Dùng JMH cho microbenchmark Java (warm-up, nhiều lượt đo, đầu vào đại diện) và dùng load test/metrics thật cho hệ thống I/O.

## 8. Ngộ nhận và lỗi phổ biến

- **“Thêm luồng luôn nhanh hơn.”** Sai nếu task quá nhỏ, phụ thuộc, tranh CPU, khóa hoặc tài nguyên cuối.
- **“Tuần tự luôn chậm.”** Sai khi làm ít việc hoặc chi phí chia/ghép task vượt lợi ích.
- **“Có nhiều thread là chạy song song.”** Scheduler có thể xen kẽ; số lõi và nút thắt mới quyết định.
- **“`volatile` làm `count++` an toàn.”** Sai, vì read–modify–write không nguyên tử.
- **“`parallelStream()` là cách tăng tốc mặc định.”** Sai khi việc nhỏ, chia không đều, có side effects hoặc nhiều task cùng tranh pool/tài nguyên.
- **“Sleep demo chứng minh hiệu năng production.”** Sai; `Thread.sleep` chỉ cho thấy khả năng chồng lấp thời gian chờ.
- **“Task không chia sẻ biến nên muốn tạo bao nhiêu thread cũng được.”** Sai, các task vẫn cùng dùng CPU, memory, kết nối và quota dịch vụ.

## 9. Checklist lựa chọn

**Chọn tuần tự trước** khi task nhỏ, các bước phụ thuộc nhau, thứ tự xử lý là ràng buộc chính, trạng thái mutable chia sẻ khó quản lý, tài nguyên cuối thực thi từng việc một hoặc chưa đo được lợi ích rõ ràng của đa luồng.

**Cân nhắc đa luồng giới hạn** khi có nhiều tác vụ đủ lớn và độc lập, có khả năng tận dụng lõi CPU hoặc chồng lấp I/O wait, và đã thiết kế giới hạn worker, cách xử lý lỗi/timeout, kiểm soát tài nguyên và kiểm thử concurrency.

Bắt đầu bằng lời giải tuần tự **đúng và đo được**, xác định điểm nghẽn, rồi chỉ tăng concurrency ở phần độc lập cần tăng tốc. Chọn theo kết quả workload thực tế, không theo số luồng càng nhiều càng tốt.

</details>

- [Quay lại đầu trang](#back-to-top)
