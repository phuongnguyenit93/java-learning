# Lập trình bất đồng bộ trong Thread

## <a id="async-thread-basic">Phân phối Luồng (Thread) vs. Điều phối Kết quả (Result)</a>

Nếu "Bộ tứ siêu đẳng" (**CountDownLatch, CyclicBarrier, Semaphore, Phaser**) tập trung vào việc điều phối các luồng (Thread Coordination), thì **Callable, Future và CompletableFuture** tập trung vào việc điều phối kết quả và luồng dữ liệu (Data & Result Coordination).

## 1. Sự tiến hóa của Coordination

Để dễ hình dung, hãy xem bảng so sánh về cách các công cụ này "giao tiếp" với nhau:

| Nhóm công cụ | Mục tiêu chính | Cách "giao tiếp" |
| :--- | :--- | :--- |
| **Synchronizers** | Điều khiển nhịp độ (Flow). | "Đợi tôi ở đây", "Đủ 3 người mới đi". |
| **Future / Callable** | Điều khiển kết quả (Result). | "Làm việc này đi, xong thì đưa kết quả cho tôi". |
| **CompletableFuture** | Điều khiển chuỗi giá trị (Pipeline). | "Làm xong A, lấy kết quả đó làm tiếp B, nếu lỗi thì làm C". |

## 2. Callable & Future: Coordination kiểu "Hứa hẹn"

`Runnable` chỉ biết chạy mà không biết nói (không trả về kết quả). `Callable` ra đời để luồng có thể trả về một giá trị. `Future` chính là "tờ biên lai" để bạn cầm và đợi lấy kết quả đó.

**Tính phối hợp:** Luồng chính đưa việc cho luồng phụ, sau đó luồng chính có thể làm việc khác, rồi cuối cùng mới gọi `future.get()` để lấy kết quả. Hành động `get()` này chính là một điểm phối hợp (Blocking point).

```java
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<String> future = executor.submit(() -> {
        Thread.sleep(2000);
        return "Kết quả từ luồng phụ";
    });

    // Làm việc khác ở luồng Main...
    
    String result = future.get(); // Điểm phối hợp (Blocking)
```

## 3. CompletableFuture: Trùm cuối của Coordination hiện đại

Đây là công cụ mạnh mẽ nhất trong Java hiện nay để phối hợp các tác vụ bất đồng bộ mà không gây tắc nghẽn (Non-blocking). Nó cho phép bạn thiết lập các kịch bản phối hợp cực kỳ phức tạp:

* **Chạy nối tiếp:** Xong A thì làm B (`thenApply`).
* **Chạy song song và đợi cả hai:** Chạy A và B, khi cả hai xong thì tổng hợp kết quả (`thenCombine`).
* **Chạy song song và lấy ông nhanh nhất:** Chạy A và B, ông nào xong trước thì lấy kết quả ông đó (`acceptEither`).

```java
    CompletableFuture.supplyAsync(() -> "Data A")
        .thenCombine(CompletableFuture.supplyAsync(() -> "Data B"), (a, b) -> a + b)
        .thenAccept(result -> System.out.println("Tổng hợp: " + result));
```

### Tại sao lại xếp vào Coordination?
Bản chất của Coordination là **"làm cho các phần rời rạc hoạt động ăn khớp với nhau"**.
1. Nếu không có `Future`, bạn không biết khi nào luồng phụ xong để lấy dữ liệu.
2. Nếu không có `CompletableFuture`, bạn sẽ rơi vào "Callback Hell" khi muốn kết hợp kết quả từ nhiều Service khác nhau (ví dụ: lấy giá từ Service A + lấy hàng từ Service B để tạo Order).

### So sánh sự "Phối hợp" (Coordination): Future vs. CompletableFuture

Sự khác biệt cốt lõi giữa hai công cụ này nằm ở cách chúng ta "lấy" và "truyền" dữ liệu giữa các luồng.

#### Bảng so sánh chi tiết

| Đặc điểm | Future | CompletableFuture |
| :--- | :--- | :--- |
| **Kiểu phối hợp** | **Pull:** Luồng Main phải chủ động gọi `.get()` để kéo dữ liệu về. | **Push:** Khi xong việc, kết quả tự động được đẩy sang bước tiếp theo. |
| **Blocking** | **Có:** Hàm `.get()` làm đứng luồng hiện tại cho đến khi có kết quả. | **Không:** Non-blocking, sử dụng cơ chế Callback hoặc Pipeline. |
| **Ghép nối tác vụ** | **Khó:** Phải dùng nhiều vòng lặp hoặc `if-else` phức tạp để kiểm tra. | **Dễ:** Dùng các hàm hàm xâu chuỗi như `thenApply`, `thenCompose`. |
| **Xử lý lỗi** | Phải dùng `try-catch` bọc quanh lời gọi `.get()`. | Có hàm `.exceptionally()` hoặc `handle()` rất gọn gàng. |

## <a id="thread-and-asynchronous">Phân biệt Thread và Lập trình bất đồng bộ (Asynchronous)</a>

Có một sự nhầm lẫn phổ biến: *"Cứ tạo Thread mới là đang lập trình bất đồng bộ"*. Sự thật là: **Thread là "Cái chân" để chạy, còn Asynchronous là "Cách" chúng ta phối hợp các bước chạy đó.**

## 1. Thread vốn dĩ là "Đồng bộ" (Synchronous) trong mắt người gọi
Khi bạn tạo một `Thread` và gọi `join()` hoặc `future.get()`, luồng chính của bạn sẽ **bị chặn (Blocked)**.

* **Ví dụ:** Bạn cử một nhân viên đi mua cafe (Thread mới). Nhưng bạn đứng im tại chỗ đợi nhân viên đó mang cafe về rồi mới đi làm tiếp.
* **Kết quả:** Tổng thể chương trình có 2 người, nhưng bạn vẫn đang làm việc theo kiểu "đợi chờ" (Blocking).

## 2. Bất đồng bộ (Asynchronous) là "Không chờ đợi" (Non-blocking)
Lập trình bất đồng bộ thực sự là khi bạn giao việc cho Thread khác, và thay vì đứng đợi, bạn đưa cho họ một **"Lời dặn"** (Callback): *"Khi nào xong thì tự ghi vào sổ/gửi email cho tôi, tôi đi làm việc khác đây"*.

### Bảng so sánh tư duy

| Đặc điểm | Threading truyền thống (Multi-thread) | Asynchronous Programming |
| :--- | :--- | :--- |
| **Tư duy** | Chia việc ra cho nhiều người làm. | Làm sao để không ai phải đứng đợi ai. |
| **Điểm nghẽn** | Thường bị kẹt ở các điểm `join()`, `get()`, `wait()`. | Sử dụng `Callback`, `Promise`, `CompletableFuture`. |
| **Tài nguyên** | Tốn RAM để duy trì Stack của từng Thread. | Tối ưu hóa tài nguyên, một Thread có thể xử lý nhiều tác vụ chờ. |

## 3. Ví dụ minh họa sự khác biệt

Hãy xem cách code "nói chuyện" trong hai trường hợp:

### Cách 1: Multi-thread nhưng vẫn là Synchronous (Đợi kết quả)
```java
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<String> future = executor.submit(() -> doLongWork());

    // Luồng Main bị kẹt cứng ở đây để đợi kết quả
    String result = future.get(); 
    System.out.println("Xong: " + result);
```

### Cách 2: Asynchronous thực thụ (Non-blocking)
```java
    CompletableFuture.supplyAsync(() -> doLongWork())
        // "Khi nào xong thì tự làm tiếp việc này, đừng gọi tôi"
        .thenAccept(result -> System.out.println("Xong: " + result));

    // Luồng Main thoải mái làm việc khác, không bị kẹt ở bất kỳ đâu
    doOtherImportantStaff();
```

## 4. Tại sao CompletableFuture lại ở "đẳng cấp" khác?
Bởi vì nó cho phép bạn **Chaining (Xâu chuỗi)**.
Trong Multi-thread truyền thống, việc phối hợp: *"Luồng A xong thì lấy kết quả đưa luồng B, luồng B xong thì báo Main"* là một cơn ác mộng về điều phối (Phaser/Barrier có thể giải quyết nhịp độ, nhưng truyền dữ liệu thì rất cực).

Với `CompletableFuture`, đó chỉ là một dòng lệnh mượt mà.

---

### Tóm lại:
* **Thread:** Là đơn vị thực thi (Worker).
* **Asynchronous:** Là phong cách làm việc (Workflow) mà ở đó không có luồng nào bị lãng phí thời gian để "chờ đợi" luồng khác.

---
## <a id="pipeline-completable-future">CompletableFuture: Từ Điều khiển Luồng đến Chuỗi giá trị (Pipeline)</a>

Cách hiểu về việc "biến nhiều luồng thành một" thực chất là sự **trừu tượng hóa**. `CompletableFuture` (CF) giúp bạn xâu chuỗi nhiều luồng khác nhau thành một quy trình (Pipeline) duy nhất mà không làm mất đi tính chất bất đồng bộ.

## 1. Sự khác biệt giữa Góc nhìn và Vận hành

### Góc nhìn người dùng (Trừu tượng hóa)
Bạn thấy code của mình viết liền mạch từ trên xuống dưới:
`Làm bước A` $\rightarrow$ `Làm bước B` $\rightarrow$ `Làm bước C`.
Nó giống như một dòng chảy duy nhất, giúp bạn thoát khỏi việc quản lý thủ công `start()`, `join()` hay `lock`.

### Thực tế vận hành (Dưới nắp máy)
Thực tế, mỗi bước trong CF có thể được thực hiện bởi các luồng hoàn toàn khác nhau từ một **Thread Pool** (mặc định là `ForkJoinPool`):

```text
    [Thread-1] xử lý Bước A (Tải dữ liệu)
       |
       v -- (A hoàn thành, đẩy kết quả sang bước tiếp theo)
       |
    [Thread-2] xử lý Bước B (Xử lý logic)
       |
       v -- (B hoàn thành, tìm luồng rảnh tiếp theo)
       |
    [Thread-3] xử lý Bước C (Lưu Database)
```

## 2. So sánh: Phối hợp vs. Gom nhóm

| Đặc điểm | Luồng truyền thống (Thread/Lock) | CompletableFuture (Pipeline) |
| :--- | :--- | :--- |
| **Tư duy** | **Thread-centric:** Điều khiển từng "con người". | **Data-centric:** Điều khiển "dòng chảy dữ liệu". |
| **Sự liên kết** | Rời rạc, dùng Latch/Barrier để đợi nhau. | Tự kết nối. Bước sau "đăng ký" nhận dữ liệu bước trước. |
| **Hiệu suất** | Luồng bị "ngủ" (Blocked) khi đợi, lãng phí tài nguyên. | Luồng không ngủ. Nếu chưa có dữ liệu, luồng đi làm việc khác. |

## 3. Ví dụ minh họa: "Dây chuyền sản xuất"

* **Thread thông thường:** Giống như một công nhân phải tự làm từ đầu đến cuối một sản phẩm. Nếu thiếu linh kiện, anh ta ngồi chơi đợi linh kiện đến (**Blocking**).
* **CompletableFuture:** Giống như một **dây chuyền tự động**. Công đoạn 1 xong, nó đẩy sản phẩm sang Công đoạn 2. Người thợ ở Công đoạn 2 không cần biết ai làm Công đoạn 1, chỉ cần thấy đồ vật chạy đến là bắt tay vào làm (**Non-blocking**).

```java
    CompletableFuture.supplyAsync(() -> downloadData()) // Chạy trên luồng A
        .thenApply(data -> processData(data))          // Có thể chạy trên luồng B
        .thenAccept(result -> saveData(result));       // Có thể chạy trên luồng C
```

## 4. Tại sao có cảm giác giống "Một luồng"?

Đó là nhờ tính chất **Composition** (Hợp thành). CF cho phép bạn viết code bất đồng bộ (nhiều luồng) nhưng nhìn lại rất giống code đồng bộ (1 luồng).

> **Đỉnh cao của sự trừu tượng:** Giấu đi sự phức tạp của Thread và hiện ra sự đơn giản của Logic nghiệp vụ.
---
## <a id="completable-future-method">Cẩm nang làm chủ CompletableFuture (CF)</a>

Để nắm trọn bộ công cụ của `CompletableFuture`, hãy chia chúng thành các nhóm dựa trên mục đích sử dụng. Dù có hơn 50 phương thức, bạn chỉ cần nhớ 5 nhóm "vàng" sau đây để xử lý 90% các bài toán bất đồng bộ.

## 1. Nhóm Khởi tạo (Initiation)
Dùng để bắt đầu một tác vụ bất đồng bộ.
* **`supplyAsync(Supplier<U>)`**: Chạy một task có trả về kết quả. (Ví dụ: Lấy giá vé máy bay).
* **`runAsync(Runnable)`**: Chạy một task không trả về kết quả. (Ví dụ: Gửi log vào database).

> **Lưu ý:** Cả hai đều dùng luồng từ `ForkJoinPool.commonPool()` mặc định trừ khi bạn truyền vào một `Executor` riêng.

## 2. Nhóm Chuyển tiếp & Xử lý (Transformation)
Dùng khi bạn muốn lấy kết quả của bước trước để làm việc tiếp theo (Chaining).
* **`thenApply(Function<T, U>)`**: Nhận kết quả từ bước trước, biến đổi nó và trả về kết quả mới. (Tương đương với `map` trong Stream).
* **`thenAccept(Consumer<T>)`**: Nhận kết quả từ bước trước nhưng không trả về gì cả. Thường dùng ở cuối chuỗi.
* **`thenRun(Runnable)`**: Chạy một việc sau khi bước trước xong, nhưng không quan tâm kết quả bước trước là gì.

## 3. Nhóm Kết hợp nhiều CF (Combination)
Đây là phần phối hợp (coordination) mạnh mẽ nhất:
* **`thenCombine(CompletionStage, BiFunction)`**: Đợi cả 2 CF hoàn thành rồi gộp kết quả lại.
* **`thenCompose(Function<T, CompletionStage>)`**: Dùng khi bước tiếp theo cũng trả về một CF. Giúp "làm phẳng" (flatten) các CF lồng nhau. (Tương đương với `flatMap`).
* **`allOf(CF...)`**: Đợi một danh sách rất nhiều CF xong hết mới đi tiếp.
* **`anyOf(CF...)`**: Chỉ cần một trong số các CF xong là đi tiếp ngay.

## 4. Nhóm Hậu tố "Async" (Async Variants)
Hầu hết các phương thức trên đều có phiên bản kết thúc bằng chữ `Async` (ví dụ: `thenApplyAsync`).
* **Không có Async:** Bước tiếp theo thường chạy trên cùng luồng với bước trước hoặc luồng của Main.
* **Có Async:** Ép buộc bước tiếp theo phải được đẩy vào Thread Pool để một luồng khác xử lý, tránh làm nghẽn luồng đang xử lý I/O.

## 5. Nhóm Xử lý lỗi (Exception Handling)
Giúp quy trình không bị "gãy" giữa chừng khi có sự cố.
* **`exceptionally(Function<Throwable, T>)`**: Nếu có lỗi, trả về một giá trị mặc định (Fallback).
* **`handle(BiFunction<T, Throwable, U>)`**: Luôn luôn chạy dù có lỗi hay không, cho phép kiểm tra cả `result` và `exception`.

## Bảng tra cứu nhanh (Cheat Sheet)

| Phương thức | Đầu vào | Trả về | Mục đích chính |
| :--- | :--- | :--- | :--- |
| **`supplyAsync`** | Supplier | `CF<T>` | Khởi tạo task có kết quả. |
| **`thenApply`** | Function | `CF<U>` | Biến đổi kết quả (A -> B). |
| **`thenAccept`** | Consumer | `CF<Void>` | Tiêu thụ kết quả (End of line). |
| **`thenCompose`** | Function | `CF<U>` | Nối chuỗi các task phụ thuộc nhau. |
| **`thenCombine`** | CF + BiFunction | `CF<V>` | Gộp kết quả từ 2 task độc lập. |
| **`exceptionally`** | Function | `CF<T>` | Xử lý lỗi, trả về giá trị cứu cánh. |

## Mẹo cho Project `java-learning`:
Khi viết code, hãy đặt 3 câu hỏi để chọn đúng phương thức:
1. "Tôi có cần kết quả không?" $\rightarrow$ **supply** vs **run**.
2. "Bước sau có cần kết quả bước trước không?" $\rightarrow$ **thenApply** vs **thenRun**.
3. "Tôi có cần luồng mới cho bước sau không?" $\rightarrow$ Thêm hậu tố **Async**.

## <a id="completable-future-with-custom-pool">Kết hợp CompletableFuture với Custom Thread Pool</a>

Trong môi trường Production, việc sử dụng Thread Pool riêng cho `CompletableFuture` (CF) là bắt buộc để đảm bảo tính cô lập và hiệu suất của hệ thống.

## 1. Tại sao phải dùng Thread Pool riêng?

Mặc định, CF dùng `ForkJoinPool.commonPool()`. Tuy nhiên, việc "dùng chung" này tiềm ẩn rủi ro:
* **Tránh nghẽn cổ chai (Resource Isolation):** `commonPool` thường chỉ có số luồng bằng $Số CPU - 1$. Nếu các task I/O (gọi API, Database) chiếm hết luồng này, toàn bộ các tác vụ khác trong hệ thống sẽ bị treo.
* **Kiểm soát kết nối (Throttling):** Sử dụng một `FixedThreadPool` giúp bạn giới hạn số lượng tác vụ chạy đồng thời (ví dụ: tối đa 10 kết nối DB cùng lúc) để tránh làm sập tài nguyên bên ngoài.

## 2. Cách kết hợp CF với Custom Thread Pool

Bạn chỉ cần truyền đối tượng `Executor` vào làm đối số thứ hai trong các phương thức có hậu tố `Async`.

```java
    // 1. Khởi tạo một Thread Pool riêng chuyên cho gọi API
    Executor apiExecutor = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r);
        t.setName("API-Pool-" + t.getId());
        return t;
    });

    // 2. Kết hợp với CompletableFuture
    CompletableFuture.supplyAsync(() -> {
        System.out.println("Đang gọi API trên luồng: " + Thread.currentThread().getName());
        return "Dữ liệu từ API";
    }, apiExecutor) // <--- Truyền Thread Pool vào đây
    .thenApplyAsync(result -> {
        System.out.println("Xử lý trên luồng: " + Thread.currentThread().getName());
        return result.toUpperCase();
    }, apiExecutor); 
```

## 3. Phân biệt thenApply vs thenApplyAsync khi dùng Pool

Việc hiểu cơ chế chuyển giao luồng là cực kỳ quan trọng để tối ưu hóa hiệu suất:

| Phương thức | Luồng thực hiện | Khi nào dùng? |
| :--- | :--- | :--- |
| **`thenApply`** | Chạy trên luồng của task trước đó (nếu đã xong) hoặc luồng của Main. | Khi bước tiếp theo xử lý rất nhẹ, không tốn thời gian. |
| **`thenApplyAsync(..., pool)`** | Ép buộc lấy một luồng mới từ Pool được chỉ định để chạy. | Khi bước tiếp theo là tác vụ nặng hoặc cần giải phóng luồng cũ ngay. |


## <a id="fork-join-pool">Bản chất của "Sự tự động" trong CompletableFuture</a>

Câu hỏi về việc Java có tự tạo luồng mới hay không chạm đúng vào "nội tại" của JVM. Câu trả lời là: **Nó không tạo mới mỗi lần gọi, mà dùng chung một "bể luồng" (Thread Pool) có sẵn.**

## 1. ForkJoinPool.commonPool() là gì?

Nếu bạn không truyền một `Executor`, Java mặc định sử dụng `ForkJoinPool.commonPool()`. Đây là một Thread Pool đặc biệt được tạo sẵn ngay khi ứng dụng khởi động.

* **Số lượng luồng:** Thường bằng $Số lõi CPU - 1$. Ví dụ: Laptop 8 cores sẽ có khoảng 7 luồng.
* **Tính chất:** Đây là **Shared Pool** (dùng chung). Mọi nơi trong code (bao gồm cả `Parallel Stream`) nếu không khai báo Pool riêng đều sẽ tranh giành các "ghế" trong bể bơi chung này.

## 2. Tại sao "Tự động" lại có thể "Nguy hiểm"?

Hãy tưởng tượng kịch bản sau trong hệ thống Backend:
1. **Tác vụ A:** Tính toán nặng, chiếm CPU cao.
2. **Tác vụ B:** Gọi API bên thứ ba (mất 10 giây).

**Vấn đề:** Nếu một vài tác vụ B bị chậm, chúng sẽ chiếm giữ toàn bộ luồng của `commonPool`. Lúc này, tác vụ A (dù quan trọng) cũng không còn luồng để chạy. Hệ thống bị "đứng hình" (Starvation) mặc dù tài nguyên máy tính vẫn đang rảnh.

## 3. Khi nào dùng Mặc định vs. Custom Pool?

| Trường hợp | Dùng mặc định (commonPool) | Dùng Custom Pool |
| :--- | :--- | :--- |
| **Loại tác vụ** | **CPU-Bound:** Tính toán, xử lý dữ liệu memory. | **I/O-Bound:** Gọi DB, API, đọc/ghi File. |
| **Thời gian chạy** | Rất ngắn (miliseconds). | Có thể dài (seconds). |
| **Độ ưu tiên** | Thấp hoặc trung bình. | Cao (Cần đảm bảo tài nguyên riêng). |

## 4. Cách kiểm tra luồng đang thực thi

Bạn có thể sử dụng đoạn mã sau trong project để kiểm chứng cơ chế này:

    ```java
    CompletableFuture.supplyAsync(() -> {
        // Nếu không truyền pool, tên luồng sẽ có dạng: "ForkJoinPool.commonPool-worker-..."
        System.out.println("Luồng đang chạy: " + Thread.currentThread().getName());
        return "Xong";
    });
    ```

## 5. Kết nối với kiến thức TaskDecorator

Đây là điểm cực kỳ quan trọng cho kiến trúc project của bạn:
* `ForkJoinPool.commonPool()` rất khó để cấu hình `TaskDecorator`.
* Nếu bạn muốn truyền **Trace ID** hoặc **Security Context** xuyên suốt các `CompletableFuture`, bạn **bắt buộc** phải dùng Custom Pool (ví dụ: `ThreadPoolTaskExecutor` trong Spring) để gắn Decorator vào.

---

## <a id="async-and-sync-completable-future"> Phân biệt thenApply vs. thenApplyAsync</a>

Sự khác biệt cốt lõi nằm ở câu hỏi: **"Luồng nào sẽ thực thi bước tiếp theo?"** Việc hiểu rõ cơ chế này giúp bạn tránh được những lần chuyển đổi luồng (Context Switch) không cần thiết.

## 1. Phương thức không có hậu tố Async (Ví dụ: `thenApply`)

Khi dùng `thenApply`, Java thực hiện bước tiếp theo một cách "cơ hội" (opportunistic):
* **Trường hợp 1:** Nếu tác vụ trước chưa xong, bước tiếp theo thường chạy trên chính luồng đang thực hiện tác vụ trước đó.
* **Trường hợp 2:** Nếu tác vụ trước đã xong rồi, bước này có thể chạy ngay trên luồng hiện tại (thường là luồng gọi hàm - luồng Main).

> **Đặc điểm:** Tiết kiệm chi phí vì tận dụng luồng sẵn có, giảm thiểu việc chuyển đổi ngữ cảnh.

## 2. Phương thức có hậu tố Async (Ví dụ: `thenApplyAsync`)

Khi dùng bản Async, bạn đưa ra một yêu cầu bắt buộc đối với hệ thống:
* **Cơ chế:** Dù tác vụ trước đã xong hay chưa, bước tiếp theo **luôn luôn** được đẩy vào hàng chờ (Task Queue) để một luồng khác từ Pool lấy ra xử lý.

> **Đặc điểm:** Tăng tính song song nhưng tốn thêm chi phí quản lý và chuyển giao giữa các luồng.

## 3. Bảng so sánh trực quan

| Đặc điểm | `thenApply` (Không Async) | `thenApplyAsync` (Có Async) |
| :--- | :--- | :--- |
| **Luồng thực thi** | Thường là luồng vừa chạy bước trước. | Luồng mới từ Pool (khác luồng trước). |
| **Sự liên tục** | "Làm xong A rồi tiện tay làm luôn B". | "Làm xong A, gửi yêu cầu làm B vào hàng đợi". |
| **Khi nào dùng?** | Task tiếp theo xử lý rất nhanh (format chuỗi). | Task tiếp theo xử lý nặng (I/O, tính toán). |
| **Rủi ro** | Có thể làm nghẽn luồng hiện tại nếu Task sau nặng. | Tốn CPU hơn do chuyển đổi luồng liên tục. |

## 4. Minh họa qua Code

Hãy quan sát sự thay đổi của tên luồng trong ví dụ dưới đây:

```java
    CompletableFuture.supplyAsync(() -> {
        System.out.println("A chạy trên: " + Thread.currentThread().getName());
        return "Kết quả A";
    }, executor)
    .thenApply(res -> {
        // Thường chạy luôn trên luồng vừa chạy A
        System.out.println("B (thenApply) chạy trên: " + Thread.currentThread().getName());
        return res + " + B";
    })
    .thenApplyAsync(res -> {
        // BẮT BUỘC lấy một luồng khác từ pool
        System.out.println("C (thenApplyAsync) chạy trên: " + Thread.currentThread().getName());
        return res + " + C";
    }, executor);
```

## 5. Quy tắc "Vàng" cho Project `java-learning`

* **Dùng bản không Async khi:** Thao tác đơn giản (bọc dữ liệu, format String, logic nhẹ). Giúp ứng dụng chạy nhanh hơn nhờ giảm Context Switch.
* **Dùng bản Async khi:**
    * Bước tiếp theo tốn thời gian (ví dụ: bước 1 lấy ID, bước 2 gọi API lấy chi tiết).
    * Khi muốn tách biệt tài nguyên: Bước 1 dùng `ioExecutor`, bước 2 dùng `cpuExecutor`.

---

> **Tóm lại:** > * Không Async = "Tiện tay làm luôn".
> * Có Async = "Bàn giao cho người khác làm".

--- 

## <a id="sequential-completable-future">Chuyên sâu về Cơ chế Tiếp sức (Sequential) trong CompletableFuture</a>

Sự khác biệt giữa bản **Async** và **không Async** không chỉ là về luồng, mà còn là về chiến thuật "bàn giao gậy" trong một quy trình chạy tiếp sức.

## 1. Bản không Async (Ví dụ: `thenApply`) - "Chạy nối chặng"

Giống như một vận động viên tiếp sức: Người chạy chặng 1 (Task A) sau khi về đích, nếu quy trình cho phép, chính người đó sẽ cầm gậy chạy luôn chặng 2 (Task B).

* **Kết quả:** Task A và Task B thường xuất hiện với cùng một tên Thread (ví dụ: `executor-1`).
* **Lợi ích:** Cực nhanh do không tốn thời gian bàn giao gậy và chuyển đổi ngữ cảnh (Context Switch).

## 2. Bản có Async (Ví dụ: `thenApplyAsync`) - "Bàn giao nghiêm ngặt"

Người chạy chặng 1 về đích bắt buộc phải đặt gậy xuống. Trọng tài (Thread Pool) sẽ điều động một người khác đang đợi trong hàng ngũ ra chạy tiếp chặng 2.

* **Kết quả:** Task A chạy trên `executor-1`, Task B chạy trên `executor-2`.
* **Lợi ích:** Giải phóng người chạy chặng 1 ngay lập tức để họ có thể nhận một chặng 1 mới, giúp hệ thống tăng khả năng tiếp nhận đầu vào.

## 3. Kịch bản "Hack não": Luồng Main tham gia chạy

Có một trường hợp đặc biệt: Nếu Task A đã xong **trước** cả khi bạn kịp gọi lệnh `.thenApply(B)`, thì Task B có thể chạy ngay trên luồng **Main** (luồng đang thực thi dòng code đó).

## 4. Khi nào sự khác biệt này "Cứu mạng" hệ thống?

Hãy xem xét kịch bản thực tế trong Backend:
* **Task A:** Truy vấn Database (1 giây).
* **Task B:** Gọi API bên thứ ba rất chậm (10 giây).

| Chiến thuật | Hệ quả vận hành |
| :--- | :--- |
| **Dùng `thenApply`** | Luồng đang giữ kết nối DB bị "bắt cóc" để đi đợi API 10 giây. Hệ thống hết sạch luồng xử lý DB chỉ vì bận đi đợi API. |
| **Dùng `thenApplyAsync`** | Luồng DB làm xong việc thì quay về Pool nghỉ ngơi. Việc gọi API chậm được đẩy cho một luồng chuyên biệt khác đảm nhận. |

## 5. Quy tắc thực hành (Best Practices)

Để tối ưu cho project `java-learning`, hãy tuân thủ:

    ```java
    // 1. THAO TÁC NHẸ: Dùng bản thường (không Async)
    // Ví dụ: Format chuỗi, bọc kết quả vào DTO
    .thenApply(user -> new UserResponse(user))

    // 2. THAO TÁC NẶNG: Dùng bản Async
    // Ví dụ: Gọi Service khác, I/O, tính toán phức tạp
    .thenApplyAsync(order -> paymentService.process(order), ioExecutor)
    ```

---

> **Tóm lại:** > * **Bản thường:** "Tiện tay làm luôn" - Tiết kiệm thời gian nhưng rủi ro gây nghẽn dây chuyền.
> * **Bản Async:** "Bàn giao chuyên trách" - Tốn phí bàn giao nhưng đảm bảo sự cô lập tài nguyên.