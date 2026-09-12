<a id="back-to-top"></a>

# Vấn đề Leak trong lập trình đa luồng

## Menu
- [ Thread Leak và Pool Leak là gì? ](#thread-leak-and-pool-leak)
- [ Pool Leak và Thread Leak khác nhau như thế nào? ](#phan-biet-thread-leak-va-pool-leak)
- [ Tác hại của Thread Leak và Pool Leak ](#tac-hai-cua-thread-leak-va-pool-leak)
- [ Nhận biết và phòng tránh leak ](#nhan-biet-va-phong-tranh-leak)



## <a id="thread-leak-and-pool-leak"> Thread Leak và Pool Leak là gì? </a>
<details>
<summary>Click for details</summary>


### Thread Leak

**Thread Leak** xảy ra khi một thread được tạo ra nhưng không được kết thúc đúng lúc, khiến thread tiếp tục tồn tại trong JVM dù application không còn cần sử dụng nó.

Ví dụ:

```java
public void createThreadLeak() {

    Thread thread = new Thread(() -> {
        while (true) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException ignored) {
            }
        }
    });

    thread.start();
}
```

Nếu method này được gọi nhiều lần:

```text
Call #1 → Thread 1
Call #2 → Thread 2
Call #3 → Thread 3
...
```

Các thread cũ vẫn tiếp tục tồn tại.

Hậu quả có thể là:

* Số lượng thread tăng liên tục.
* Tăng mức sử dụng memory.
* Tăng context switching.
* CPU phải quản lý quá nhiều thread.
* Application chậm dần theo thời gian.
* Có thể dẫn tới:

```text
java.lang.OutOfMemoryError: unable to create native thread
```

---

### Pool Leak

**Pool Leak** xảy ra khi một `ExecutorService` hoặc Thread Pool được tạo ra nhưng không được `shutdown()` đúng cách.

Ví dụ:

```java
public void createPoolLeak() {

    ExecutorService pool =
            Executors.newFixedThreadPool(3);

    for (int i = 0; i < 10; i++) {
        pool.execute(() -> {
            // xử lý task
        });
    }

    // Không gọi pool.shutdown()
}
```

Mỗi lần method được gọi:

```text
Call #1
    ↓
Pool #1
    ├── thread-1
    ├── thread-2
    └── thread-3

Call #2
    ↓
Pool #2
    ├── thread-1
    ├── thread-2
    └── thread-3

Call #3
    ↓
Pool #3
    ├── thread-1
    ├── thread-2
    └── thread-3
```

Nếu các pool không được shutdown thì các worker thread có thể tiếp tục tồn tại trong JVM.

Ví dụ gọi method 100 lần:

```text
100 pools × 3 threads
        ↓
~300 threads
```
### Tổng kết

```text
Thread Leak
= Thread không được kết thúc đúng lifecycle.

Pool Leak
= Thread Pool / Executor không được shutdown đúng lifecycle.

Pool Leak
→ thường kéo theo Thread Leak.
```

Trong production, nếu `ThreadCount` tăng liên tục theo thời gian và không quay lại mức bình thường, đặc biệt xuất hiện ngày càng nhiều thread dạng:

```text
    pool-1-thread-1
    pool-2-thread-1
    pool-3-thread-1
    ...
```

thì đây là dấu hiệu nên kiểm tra xem application có đang liên tục tạo `ExecutorService` mà không `shutdown()` hay không.

---


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="phan-biet-thread-leak-va-pool-leak"> Pool Leak và Thread Leak khác nhau như thế nào? </a>
<details>
<summary>Click for details</summary>


```text
Thread Leak
    ↓
Thread sống ngoài ý muốn
```

Trong khi:

```text
Pool Leak
    ↓
ExecutorService sống ngoài ý muốn
    ↓
Worker threads của pool vẫn sống
    ↓
Có thể dẫn đến Thread Leak
```

Có thể hiểu đơn giản:

> **Thread Leak** là leak ở mức thread.

> **Pool Leak** là leak ở mức resource quản lý một nhóm thread.

Trong nhiều trường hợp:

```text
Pool Leak
    ↓
Thread Leak
```

nhưng không phải mọi Thread Leak đều bắt nguồn từ Thread Pool.

Ví dụ:

```java
new Thread(...).start();
```

cũng có thể gây Thread Leak mà không liên quan đến `ExecutorService`.

---

### Ví dụ Pool Leak phổ biến

Một pattern nguy hiểm trong application:

```java
    public void process() {

        ExecutorService pool =
                Executors.newFixedThreadPool(5);

        pool.execute(...);

        // method return
        // không shutdown pool
    }
```

Sau khi method kết thúc, application có thể không còn reference tới:

```java
pool
```

nhưng các worker thread của executor vẫn tồn tại:

```text
pool-1-thread-1
pool-1-thread-2
pool-1-thread-3
pool-1-thread-4
pool-1-thread-5
```

Nếu method tiếp tục được gọi:

```text
pool-2-thread-*
pool-3-thread-*
pool-4-thread-*
...
```

Số lượng thread sẽ tăng dần theo thời gian.

---

### Cách hạn chế Pool Leak

Không nên tạo Thread Pool mới trong mỗi business method nếu pool có thể được tái sử dụng.

#### Không nên

```java
public void process() {

    ExecutorService pool =
            Executors.newFixedThreadPool(10);

    pool.execute(...);
}
```

#### Nên

Quản lý Thread Pool như một resource có lifecycle rõ ràng:

```java
private final ExecutorService pool =
        Executors.newFixedThreadPool(10);

public void process() {
    pool.execute(...);
}
```

và shutdown khi application không còn sử dụng:

```java
pool.shutdown();
```

Trong Spring Boot, một lựa chọn tốt hơn là sử dụng `ThreadPoolTaskExecutor` dưới dạng Spring Bean để Spring quản lý lifecycle của Thread Pool.

```text
Spring Context
      ↓
ThreadPoolTaskExecutor
      ↓
Service A ─┐
Service B ─┼── submit task
Service C ─┘
      ↓
Shared Thread Pool
```


</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="tac-hai-cua-thread-leak-va-pool-leak"> Tác hại của Thread Leak và Pool Leak </a>
<details>
<summary>Click for details</summary>


## 1. Số lượng thread tăng liên tục

Đây là dấu hiệu dễ thấy nhất.

Ví dụ mỗi lần request tạo một fixed thread pool gồm 3 thread:

```java
Executors.newFixedThreadPool(3);
```

Nếu không shutdown:

```text
1 request      → 3 threads
10 requests    → 30 threads
100 requests   → 300 threads
1.000 requests → 3.000 threads
```

Nếu hệ thống chạy lâu, số thread có thể tăng dần mà không quay về mức bình thường.

Trong Hawtio có thể thấy:

```text
pool-1-thread-1
pool-1-thread-2
pool-1-thread-3

pool-2-thread-1
pool-2-thread-2
pool-2-thread-3

pool-3-thread-1
...
```

Đây là một dấu hiệu rất điển hình của Pool Leak.

---

## 2. Tăng mức sử dụng Memory

Platform thread của Java thường tương ứng với một native OS thread.

Mỗi thread cần tài nguyên như:

```text
Thread
 ├── Stack memory
 ├── Native thread structure
 ├── JVM bookkeeping
 └── OS resource
```

Vì vậy nếu số thread tăng lên hàng trăm hoặc hàng nghìn, lượng memory mà JVM/process sử dụng cũng tăng theo.

Ví dụ:

```text
50 threads
     ↓
bình thường

500 threads
     ↓
memory tăng đáng kể

5.000 threads
     ↓
có thể gây áp lực rất lớn lên native memory
```

Đặc biệt, phần memory này không nhất thiết nằm hoàn toàn trong Java Heap.

Do đó có thể xảy ra trường hợp:

```text
Java Heap vẫn còn nhiều
```

nhưng application vẫn gặp lỗi tạo thread mới.

---

## 3. Có thể gây `OutOfMemoryError: unable to create native thread`

Một hậu quả nghiêm trọng của Thread Leak là JVM không thể tạo thêm native thread.

Ví dụ:

```text
java.lang.OutOfMemoryError:
unable to create native thread
```

Lỗi này không nhất thiết có nghĩa là:

```text
Java Heap hết memory
```

mà có thể là:

```text
JVM / OS không còn đủ tài nguyên
để tạo thêm native thread.
```

Khi đó không chỉ code của chúng ta gặp vấn đề.

Các component khác cũng có thể không tạo được thread:

```text
Tomcat
Kafka
Scheduler
Database Pool
Async Executor
Library nội bộ
```

Kết quả có thể khiến toàn bộ application hoạt động không ổn định.

---

## 4. Tăng Context Switching

Giả sử server có:

```text
8 CPU cores
```

nhưng application có:

```text
2.000 runnable threads
```

CPU không thể thực sự chạy 2.000 thread cùng lúc.

Operating System phải liên tục chuyển đổi giữa các thread:

```text
Thread A
   ↓
Thread B
   ↓
Thread C
   ↓
Thread D
   ↓
...
```

Quá trình này gọi là:

```text
Context Switching
```

Context switching cũng tiêu tốn CPU.

Nếu số thread quá lớn:

```text
CPU time
   ↓
một phần dùng để xử lý business logic
   ↓
một phần lớn bị tiêu tốn cho thread scheduling
```

Kết quả:

```text
CPU tăng
Throughput giảm
Latency tăng
```

---

## 5. Application chậm dần theo thời gian

Thread Leak thường không làm application chết ngay.

Đây mới là điều khiến nó khó phát hiện.

Ví dụ:

```text
09:00 → 50 threads
10:00 → 100 threads
11:00 → 250 threads
12:00 → 500 threads
14:00 → 1.000 threads
```

Ban đầu application vẫn chạy bình thường.

Sau một thời gian:

```text
Response time tăng
CPU tăng
Memory tăng
Request timeout
Scheduler chạy chậm
```

Cuối cùng có thể xảy ra:

```text
Application gần như không phản hồi
```

Đây là loại bug thường chỉ xuất hiện sau khi application chạy lâu hoặc có traffic lớn.

---

## 6. Pool Leak có thể làm Queue tăng theo

Một Pool Leak không chỉ gây vấn đề về worker thread.

Ví dụ:

```java
Executors.newFixedThreadPool(10);
```

`newFixedThreadPool()` sử dụng một queue không giới hạn về mặt cấu hình thực tế:

```text
Incoming Tasks
      ↓
10 worker threads
      ↓
Task xử lý không kịp
      ↓
Queue tăng
```

Nếu task được submit nhanh hơn tốc độ xử lý:

```text
100 tasks
1.000 tasks
10.000 tasks
100.000 tasks
```

queue có thể tiếp tục tăng.

Khi đó application có hai nguồn áp lực:

```text
Thread Pool
+
Task Queue
```

Queue giữ các object `Runnable`, closure và các object mà task reference tới.

Vì vậy memory usage có thể tăng rất mạnh.

---

## 7. `CachedThreadPool` có thể tạo quá nhiều thread

`newCachedThreadPool()` có behavior khác:

```java
Executors.newCachedThreadPool();
```

Pool này có thể tạo thêm thread khi cần.

Ví dụ:

```text
Task đến nhanh
+
Task chạy lâu
      ↓
Pool liên tục tạo worker mới
```

Có thể dẫn tới:

```text
10 threads
100 threads
1.000 threads
...
```

Các worker idle có thể tự terminate sau khoảng 60 giây, nhưng nếu workload tiếp tục tăng thì application vẫn có thể bị quá tải trước khi các thread có cơ hội được thu hồi.

---

## 8. Scheduled Pool Leak có thể làm task bị chạy trùng

Scheduled pool còn nguy hiểm hơn trong một số trường hợp.

Ví dụ:

```java
public void startScheduler() {

    ScheduledExecutorService pool =
            Executors.newScheduledThreadPool(2);

    pool.scheduleAtFixedRate(
            this::healthCheck,
            1,
            5,
            TimeUnit.SECONDS
    );
}
```

Nếu method này bị gọi nhiều lần nhưng không shutdown pool:

```text
Call #1 → Scheduler #1
Call #2 → Scheduler #2
Call #3 → Scheduler #3
```

Sau đó cứ mỗi 5 giây:

```text
Scheduler #1 → healthCheck()
Scheduler #2 → healthCheck()
Scheduler #3 → healthCheck()
```

Thay vì health check chạy một lần:

```text
5s → healthCheck()
```

nó có thể chạy nhiều lần:

```text
5s → healthCheck()
     healthCheck()
     healthCheck()
```

Nếu task là:

```text
Send email
Clean data
Sync database
Generate report
Charge payment
```

thì hậu quả có thể nghiêm trọng hơn nhiều so với chỉ tốn thread.

---

## 9. Pool bị leak nhưng không còn khả năng quản lý

Một vấn đề rất khó chịu là mất reference tới pool.

Ví dụ:

```java
public void process() {

    ExecutorService pool =
            Executors.newFixedThreadPool(3);

    pool.execute(...);

    // không shutdown
}
```

Khi method kết thúc:

```text
local variable "pool"
      ↓
không còn được application giữ lại
```

nhưng executor vẫn còn sống vì worker thread của nó vẫn đang hoạt động/chờ task.

Hawtio vẫn có thể nhìn thấy:

```text
pool-1-thread-1
pool-1-thread-2
pool-1-thread-3
```

nhưng Java không cung cấp API chuẩn:

```java
ExecutorService pool =
    findExecutorByThreadName("pool-1-thread-1");
```

Do đó application không thể dễ dàng lấy lại executor để:

```java
pool.shutdown();
```

Trong tình huống này, nếu pool không thể tự terminate, giải pháp thực tế đôi khi chỉ còn:

```text
restart JVM
```

---

</details>

- [Quay lại đầu trang](#back-to-top)
---
## <a id="nhan-biet-va-phong-tranh-leak"> Nhận biết và phòng tránh leak </a>
<details>
<summary>Click for details</summary>


### Dấu hiệu nhận biết trong Production

Một số dấu hiệu nên kiểm tra:

```text
ThreadCount tăng liên tục
```

hoặc xuất hiện ngày càng nhiều thread:

```text
pool-1-thread-*
pool-2-thread-*
pool-3-thread-*
pool-4-thread-*
...
```

Ngoài ra có thể thấy:

```text
Memory usage tăng dần
CPU tăng bất thường
Context switching cao
API latency tăng
Request timeout
Unable to create native thread
Application chỉ ổn định lại sau restart
```

Khi kiểm tra thread dump, rất nhiều leaked worker có thể nằm ở:

```text
WAITING
```

với stack dạng:

```text
LinkedBlockingQueue.take()
ThreadPoolExecutor.getTask()
ThreadPoolExecutor.runWorker()
```

Điều này thường có nghĩa:

> Worker không làm gì cả nhưng Thread Pool vẫn giữ nó sống để chờ task tiếp theo.

---

### Cách hạn chế Thread Leak và Pool Leak

Không nên tạo Thread Pool mới bên trong business method nếu có thể tái sử dụng:

```java
public void process() {

    ExecutorService pool =
            Executors.newFixedThreadPool(10);

    pool.execute(...);
}
```

Nên quản lý Thread Pool như một resource có lifecycle rõ ràng:

```java
private final ExecutorService pool =
        Executors.newFixedThreadPool(10);
```

và shutdown khi không còn sử dụng:

```java
pool.shutdown();
```

Trong Spring Boot, có thể ưu tiên:

```text
ThreadPoolTaskExecutor
+
Spring Bean
+
Dependency Injection
+
Spring Lifecycle
```

Ví dụ:

```java
@Bean
public ThreadPoolTaskExecutor fileExecutor() {

    ThreadPoolTaskExecutor executor =
            new ThreadPoolTaskExecutor();

    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("file-worker-");

    return executor;
}
```

Nhờ đó:

```text
Service gọi 1 lần
Service gọi 1.000 lần
        ↓
vẫn dùng chung một Thread Pool
```

thay vì:

```text
1 request → 1 pool
1.000 request → 1.000 pool
```

---

</details>

- [Quay lại đầu trang](#back-to-top)