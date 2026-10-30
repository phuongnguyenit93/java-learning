<a id="back-to-top"></a>

# Kết hợp CompletableFuture

## Menu
- [thenApply() biến đổi kết quả và thenCompose() nối công việc phụ thuộc](#completable-future-composition-001)
- [thenCombine() kết hợp hai kết quả độc lập](#completable-future-composition-002)
- [allOf() chờ nhiều công việc và cách thu kết quả có kiểu](#completable-future-composition-003)
- [anyOf() hoàn tất theo một công việc và giới hạn hủy các công việc còn lại](#completable-future-composition-004)
- [thenAccept() và thenRun() cho bước không có kết quả](#completable-future-composition-005)
- [Biến thể Async và lựa chọn bộ thực thi tường minh](#completable-future-composition-006)
- [Các bước không có hậu tố Async có thể chạy trên luồng hoàn tất](#completable-future-composition-007)
- [supplyAsync() không chỉ định executor và nguy cơ dùng chung common pool](#completable-future-composition-008)
- [Tránh gửi tác vụ hai lần khi vừa dùng @Async vừa dùng supplyAsync()](#completable-future-composition-009)
- [Tránh chờ tác vụ con trên chính nhóm luồng đã cạn](#completable-future-composition-010)

## <a id="completable-future-composition-001">thenApply() biến đổi kết quả và thenCompose() nối công việc phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# thenApply() biến đổi kết quả và thenCompose() nối công việc phụ thuộc

> **Module:** Spring Framework – concurrency  
> **Nhóm:** Kết hợp CompletableFuture  
> **Mục tiêu:** Hiểu cách tạo một pipeline phụ thuộc bằng `CompletableFuture`: `thenApply()` biến đổi **giá trị** của một giai đoạn đã hoàn tất, còn `thenCompose()` dùng kết quả để **khởi động hoặc kết nối một giai đoạn bất đồng bộ khác** rồi làm phẳng kết quả. Phân biệt rõ kiểu trả về, thời điểm chạy callback, executor, lỗi và giới hạn vận hành trong ứng dụng Spring.

## 1. Câu trả lời ngắn: map khác flatMap

Hai phương thức cùng chỉ được kích hoạt khi giai đoạn trước **hoàn tất bình thường** và cùng nhận giá trị của giai đoạn đó. Khác biệt nằm ở **giá trị mà hàm callback trả về** và **kiểu của future kế tiếp**:

- `thenApply(fn)`: callback nhận `T`, trả `U`. Kết quả là `CompletableFuture<U>`. Dùng khi đã có giá trị và chỉ cần chuyển nó thành một giá trị mới, ví dụ `User -> user.name()`, `Order -> OrderDto`, `Integer -> String`.
- `thenCompose(fn)`: callback nhận `T`, trả một `CompletionStage<U>`. Kết quả là `CompletableFuture<U>` **được nối/làm phẳng**, chỉ hoàn tất theo kết quả của giai đoạn mà callback trả về. Dùng khi bước kế tiếp **phụ thuộc vào kết quả bước trước** và bản thân bước kế tiếp đã trả về một async stage, ví dụ `userId -> fetchOrdersAsync(userId)`.

Có thể ghi nhớ bằng phép so sánh về hình dạng dữ liệu:

~~~text
CompletableFuture<T>
    .thenApply(T -> U)
          => CompletableFuture<U>

CompletableFuture<T>
    .thenApply(T -> CompletableFuture<U>)
          => CompletableFuture<CompletableFuture<U>>  [LỒNG NHAU]

CompletableFuture<T>
    .thenCompose(T -> CompletionStage<U>)
          => CompletableFuture<U>                     [LÀM PHẲNG]
~~~

`thenCompose` gần với `flatMap` trong cách tổ hợp giá trị bọc; `thenApply` gần với `map`. Đây là cách giải thích bằng tương tự, không phải chữ ký API thực tế giống `Stream` hoặc `Optional`.

## 2. CompletableFuture và CompletionStage biểu diễn điều gì?

`CompletableFuture<T>` của JDK vừa là `Future<T>` (quan sát/đợi kết quả), vừa triển khai `CompletionStage<T>` (đăng ký bước xử lý phụ thuộc).

Một stage có thể ở trạng thái **chưa hoàn tất**, **hoàn tất bình thường với giá trị T** hoặc **hoàn tất ngoại lệ**. Hàm `thenApply`/`thenCompose` tạo ra một stage mới; nó không biến đổi hay thay thế chính đối tượng future nguồn, và cũng không bảo đảm callback chạy trên thread khác.

Ví dụ pipeline:

~~~text
fetchUserAsync("u-17") : CompletableFuture<User>
                  |
                  v  thenApply(user -> user.id())
              CompletableFuture<String>
                  |
                  v  thenCompose(id -> fetchOrdersAsync(id))
              CompletableFuture<List<Order>>
                  |
                  v  thenApply(orders -> toSummary(orders))
              CompletableFuture<Summary>
~~~

Trong sơ đồ, bước lấy đơn hàng cần `id` của user. Vì `fetchOrdersAsync(id)` tự trả về `CompletableFuture<List<Order>>`, dùng `thenCompose` ở đoạn đó sẽ giữ pipeline có kiểu cuối dễ sử dụng, không phải `CompletableFuture<CompletableFuture<List<Order>>>`.

**“Bất đồng bộ” không có nghĩa mọi callback đều chạy bất đồng bộ.** Future có thể đã hoàn tất trước khi gắn callback; callback không có hậu tố `Async` có thể chạy ngay khi đăng ký hoặc trên thread đang hoàn tất stage nguồn. Việc đợi bằng `join()`/`get()` ở caller vẫn là hành động chặn nếu giá trị chưa sẵn sàng.

## 3. Chữ ký kiểu và cách chọn

Trong `CompletableFuture<T>`, các phương thức có dạng tổng quát:

~~~java
<U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn)

<U> CompletableFuture<U> thenCompose(
    Function<? super T, ? extends CompletionStage<U>> fn)
~~~

Đây là **chữ ký API rút gọn**, không phải hai phương thức cần chép vào class Java. `CompletionStage<U>` là interface: callback của `thenCompose` không bắt buộc trả đúng class `CompletableFuture<U>`, chỉ cần trả một stage tương thích.

Bảng lựa chọn nhanh:

| Callback trả về | Dùng | Kiểu kết quả mong muốn |
| --- | --- | --- |
| Giá trị đơn `String`, `int`, `Dto`, `List<Order>` | `thenApply` | `CompletableFuture<U>` |
| `CompletionStage<U>` hoặc `CompletableFuture<U>` | `thenCompose` | `CompletableFuture<U>` đã flatten |
| Cần dùng cả hai future **độc lập** và chờ chúng xong | Thường `thenCombine`/`allOf` | Phối hợp song song, không phụ thuộc đầu ra trước |
| Không cần tạo giá trị mới, chỉ tiêu thụ kết quả | `thenAccept` | `CompletableFuture<Void>` |
| Không cần input của stage trước, chỉ chạy hành động khi xong | `thenRun` | `CompletableFuture<Void>` |

**Không chọn theo kích thước công việc.** “Bước xử lý nặng thì dùng `thenCompose`” là sai. Chọn `thenCompose` vì callback trả một *stage*, còn chuyện chạy trên executor nào được điều khiển bằng cơ chế khác.

## 4. thenApply(): biến đổi một giá trị đồng bộ về mặt callback

Với `CompletableFuture<User>`, ta có thể đổi `User` thành DTO ngay sau khi nhận dữ liệu:

~~~java
CompletableFuture<String> displayName =
    userFuture.thenApply(user -> user.name().trim().toUpperCase());
~~~

Đây là **đoạn ghép vào ứng dụng** giả định đã có `userFuture` kiểu `CompletableFuture<User>` và `User.name()` trả `String`. Khi user future thành công, callback chạy và sinh một chuỗi; future mới hoàn tất với chuỗi đó.

Những điều quan trọng:

1. `thenApply` **không yêu cầu tạo một task mới** trên thread pool. Hàm mapper có thể được thực thi inline theo chính sách completion của `CompletableFuture`.
2. Nếu stage nguồn hoàn tất ngoại lệ, callback mapping thông thường **không chạy**; stage phụ thuộc hoàn tất ngoại lệ.
3. Nếu mapper ném exception, stage mới hoàn tất ngoại lệ, không ném trực tiếp sang luồng caller đang đăng ký một continuation đang đợi.
4. Mapper trả `null` là hợp lệ và stage mới hoàn tất bình thường với `null`; cần xử lý khi nghiệp vụ không cho phép giá trị null.
5. Mapper không tự làm I/O bất đồng bộ. Nếu bên trong nó gọi network/DB theo kiểu blocking, nó vẫn giữ thread chạy callback cho đến khi lời gọi xong.

**Dùng `thenApply` cho biến đổi ngắn, thuần hoặc nhẹ** là lựa chọn thường thuận tiện; khi callback cần CPU nặng hoặc có yêu cầu cách ly tài nguyên, cân nhắc `thenApplyAsync(fn, executor)` trên executor được cấp phát và giới hạn rõ.

## 5. thenCompose(): một bước phụ thuộc trả về stage khác

Giả sử API của hệ thống đã có:

~~~java
CompletableFuture<User> fetchUserAsync(String userId);
CompletableFuture<List<Order>> fetchOrdersAsync(String userId);
~~~

Đây là **hai chữ ký minh họa**, không phải chương trình độc lập; `User` và `Order` là kiểu nghiệp vụ cần định nghĩa.

Khi bước lấy orders cần ID user từ bước trước:

~~~java
CompletableFuture<List<Order>> ordersFuture =
    fetchUserAsync("u-17")
        .thenCompose(user -> fetchOrdersAsync(user.id()));
~~~

Cơ chế:

1. `fetchUserAsync` trả một future. Callback bên trong `thenCompose` **chưa chạy** khi future nguồn chưa hoàn thành bình thường.
2. Khi user sẵn sàng, callback lấy `user.id()` rồi gọi `fetchOrdersAsync`, nhận về **một stage khác**.
3. Future do `thenCompose` trả ra sẽ hoàn tất dựa trên outcome của stage orders, chứ không hoàn tất chỉ vì callback đã trả về object future.
4. Bước kế tiếp `.thenApply(orders -> ...)` sẽ nhận trực tiếp `List<Order>`, không cần bóc thêm một lớp future.

Đây là **nối giai đoạn phụ thuộc**, không phải tự tạo concurrency song song. Việc gọi `fetchOrdersAsync` xảy ra **sau khi** user stage hoàn tất; bất đồng bộ thực tế của việc lấy orders phụ thuộc implementation của `fetchOrdersAsync`.

Nếu callback trả `null` thay vì một `CompletionStage` hợp lệ, stage kết quả sẽ thất bại (thường bằng `NullPointerException`). Không nên dùng `null` để biểu diễn “không có bước kế tiếp”; hãy dùng `CompletableFuture.completedFuture(defaultValue)` hoặc stage hoàn tất phù hợp theo hợp đồng nghiệp vụ.

## 6. Lỗi future lồng nhau khi dùng thenApply cho thao tác async

Một sự nhầm lẫn thường gặp:

~~~java
CompletableFuture<CompletableFuture<List<Order>>> nested =
    fetchUserAsync("u-17")
        .thenApply(user -> fetchOrdersAsync(user.id()));
~~~

Kiểu lồng nhau là **đúng về mặt Java**, nhưng thường bất tiện:

- `nested` có thể hoàn tất bình thường ngay khi callback trả về object `ordersFuture`, **dù orders vẫn chưa tải xong**.
- `nested.get()` trả về **một future nữa**, không trả danh sách orders.
- Lỗi ở **future bên trong** có thể chưa phản ánh như một exceptional completion của future bên ngoài. Nếu code chỉ kiểm tra future ngoài, có thể kết luận sai rằng cả công việc thành công.
- Các đoạn mã sau cần hai lần bóc lớp hoặc tự nối thêm callback, dễ tăng độ phức tạp và rủi ro xử lý lỗi thiếu.

Cách sửa:

~~~java
CompletableFuture<List<Order>> flat =
    fetchUserAsync("u-17")
        .thenCompose(user -> fetchOrdersAsync(user.id()));
~~~

Mọi bước sau nhận trực tiếp `List<Order>`; lỗi phát sinh trong một trong hai giai đoạn sẽ đi theo đường exceptional completion của chuỗi kết quả.

**Chú ý:** `thenCompose` không “hủy” stage nội bộ hay ngăn nó chạy; nó chỉ biểu diễn phụ thuộc và điều phối completion. Tác vụ đang chạy, cơ chế cancel/timeout và tài nguyên downstream vẫn cần thiết kế riêng.

## 7. Ví dụ Java 17+ chạy độc lập

Ví dụ sau minh họa đồng thời biến đổi giá trị, future lồng nhau, nối future phụ thuộc, tạo báo cáo và fallback lỗi. Nó chỉ dùng JDK, không cần Spring để thấy sự khác biệt kiểu và hành vi; khi tích hợp Spring, nguồn future có thể đến từ `@Async` hoặc `AsyncTaskExecutor`.

Tạo file **ComposeDemo.java**:

~~~java
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ComposeDemo {
    static CompletableFuture<String> findOrderAsync(
            String userId, ExecutorService pool) {
        return CompletableFuture.supplyAsync(
            () -> "order-" + userId, pool);
    }

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> user =
                CompletableFuture.supplyAsync(() -> "u-17", pool);

            CompletableFuture<String> transformed =
                user.thenApply(id -> "user=" + id);

            CompletableFuture<CompletableFuture<String>> nested =
                user.thenApply(id -> findOrderAsync(id, pool));

            CompletableFuture<String> flattened =
                user.thenCompose(id -> findOrderAsync(id, pool));

            CompletableFuture<String> report =
                user.thenCompose(id -> findOrderAsync(id, pool))
                    .thenApply(order -> "summary:" +
                        order.toUpperCase(Locale.ROOT));

            System.out.println("apply = " + transformed.join());
            System.out.println("nested = " + nested.join().join());
            System.out.println("compose = " + flattened.join());
            System.out.println("chain = " + report.join());

            CompletableFuture<String> failure =
                CompletableFuture.<String>failedFuture(
                    new IllegalStateException("downstream unavailable"))
                .thenApply(String::toUpperCase);
            System.out.println("recovery = " +
                failure.exceptionally(error -> "fallback").join());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac ComposeDemo.java
java ComposeDemo
~~~

**Stdout chính xác của chương trình**:

~~~text
apply = user=u-17
nested = order-u-17
compose = order-u-17
chain = summary:ORDER-U-17
recovery = fallback
~~~

Ví dụ đã được **biên dịch và thực thi với JDK hỗ trợ Java 17+**; các dòng `System.out.println` có thứ tự xác định vì caller `join()` từng future theo thứ tự mong muốn, bất kể hai worker hoàn tất công việc lúc nào.

Đọc theo kiểu:

- `transformed`: `CompletableFuture<String>` từ một phép chuyển giá trị đơn.
- `nested`: `CompletableFuture<CompletableFuture<String>>`; chỉ để minh họa ta gọi `nested.join().join()` ở **thread main**. Không nên sao chép cách blocking lồng nhau đó vào callback chạy trong pool.
- `flattened`: `CompletableFuture<String>` trực tiếp, chỉ cần một `join()` tại ranh giới cuối của chương trình.
- `report`: `thenCompose` lấy order theo user, `thenApply` biến order thành chuỗi báo cáo.
- `failure`: stage trước hoàn tất ngoại lệ, `thenApply` không thực thi mapper, `exceptionally` trả chuỗi fallback.

Trong ví dụ, `findOrderAsync` mô phỏng việc bất đồng bộ bằng `supplyAsync`, chưa phải một HTTP client thật. Một client HTTP hỗ trợ API bất đồng bộ có thể trả future mà không chiếm worker trong thời gian chờ network; ngược lại, đặt blocking call trong `supplyAsync` vẫn dùng một worker suốt thời gian call.

## 8. Kết hợp với Spring @Async đúng cách

Spring `@Async` có thể trả `CompletableFuture<T>` để caller tiếp tục ghép stage. Điều kiện chính:

1. Bật `@EnableAsync` trong `ApplicationContext` phù hợp.
2. Gọi method `@Async` **qua proxy Spring**; self-invocation `this.method()` trong proxy mode thường bỏ qua interceptor.
3. Có executor phù hợp, tránh để công việc sản phẩm phụ thuộc vào cấu hình concurrency không kiểm soát.
4. Method `@Async` trả `CompletableFuture` đã hoàn tất cục bộ từ **worker** khi công việc trong thân method xong; proxy cung cấp future cho caller để quan sát/nối bước tiếp theo.
5. Đặt các bước async phụ thuộc vào một service được inject, không gọi `join()` trong worker chỉ để đợi một task khác của cùng pool.

Ví dụ **tích hợp Spring Framework**, cần dependency Spring context, được ApplicationContext nạp; không phải tệp Java độc lập chỉ bằng `javac`:

~~~java
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {
    @Bean("ioExecutor")
    ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(64);
        pool.setThreadNamePrefix("io-");
        return pool;
    }
}

@Service
class RemoteLookup {
    @Async("ioExecutor")
    public CompletableFuture<String> fetchUserId(String ref) {
        // Minh họa: thay bằng tác vụ I/O thực tế.
        return CompletableFuture.completedFuture("u-" + ref);
    }

    @Async("ioExecutor")
    public CompletableFuture<String> fetchOrder(String userId) {
        return CompletableFuture.completedFuture("order-" + userId);
    }
}

@Service
class SummaryService {
    private final RemoteLookup remote;

    SummaryService(RemoteLookup remote) {
        this.remote = remote;
    }

    public CompletableFuture<String> summary(String ref) {
        return remote.fetchUserId(ref)
            .thenCompose(remote::fetchOrder)
            .thenApply(order -> "summary:" + order);
    }
}
~~~

Một caller bên ngoài gọi `SummaryService.summary("17")` sẽ nhận `CompletableFuture<String>` có giá trị thành công là **`summary:order-u-17`**. Trong cấu hình này, `RemoteLookup` là **bean khác** so với `SummaryService`, nên `remote::fetchOrder` đi qua proxy `@Async`. Nếu đổi thành `this.fetchOrder(...)` trong cùng instance đang annotate, hiệu lực async có thể khác vì self-invocation.

**Không kết luận rằng `thenCompose` tự thêm một lần submit lên `ioExecutor`.** Lệnh submit ở bước thứ hai có được thực hiện là do lời gọi `remote.fetchOrder` đi qua `@Async` interceptor. `thenCompose` chỉ gắn kết future trả về. Nếu `fetchOrder` là API không async, chỉ dùng `thenCompose` không làm nó trở thành I/O bất đồng bộ.

Có thể tạo future trực tiếp từ `AsyncTaskExecutor.submitCompletable(Callable<T>)` trong Spring Framework 6.0+, hoặc từ các API I/O bất đồng bộ. Để kết hợp, chỉ cần các stage có kiểu tương thích; nguồn future không nhất thiết cùng thư viện hay executor.

## 9. thenApplyAsync / thenComposeAsync khác ở đâu?

Hai phương thức chính có các biến thể `Async`:

~~~java
future.thenApply(x -> transform(x));
future.thenApplyAsync(x -> transform(x));
future.thenApplyAsync(x -> transform(x), executor);

future.thenCompose(x -> fetchAsync(x));
future.thenComposeAsync(x -> fetchAsync(x));
future.thenComposeAsync(x -> fetchAsync(x), executor);
~~~

Đây là **đoạn API tích hợp minh họa**; các hàm `transform`, `fetchAsync`, biến `future`/`executor` cần có trong ứng dụng.

- Không hậu tố `Async`: hành động phụ thuộc có thể chạy ở thread hoàn tất future nguồn hoặc thread khác tham gia completion; nếu nguồn đã hoàn tất trước khi gắn stage, callback có thể chạy ngay trên thread đang đăng ký.
- Hậu tố `Async`, **không truyền executor**: với `CompletableFuture` chuẩn của JDK 17, sử dụng default async executor; thông thường là `ForkJoinPool.commonPool()`, trừ điều kiện parallelism nhỏ hoặc subclass thay đổi default executor. Không mặc định dùng `ThreadPoolTaskExecutor` của Spring đã tạo future nguồn.
- Hậu tố `Async`, **truyền executor**: lịch callback trên executor được chỉ rõ, giúp giới hạn/cách ly CPU hoặc I/O callback theo thiết kế. Nếu executor từ chối task, stage phụ thuộc có thể hoàn tất ngoại lệ do việc lập lịch thất bại.
- Dù có hậu tố `Async`, **`thenApply` vẫn chỉ biến đổi giá trị** và **`thenCompose` vẫn nối một stage khác**. Hai trục khái niệm độc lập: *kiểu kết quả* và *cách lên lịch callback*.

Không mặc định biến mọi stage thành `thenApplyAsync` hay `thenComposeAsync`: liên tục chuyển pool có overhead, làm rối thread context và tạo các giới hạn tải riêng. Biến đổi chuỗi rất nhẹ thường không cần một lần scheduling riêng. Công việc CPU nặng hoặc callback có khả năng chặn cần được phân bổ executor phù hợp.

## 10. Không gọi join/get bên trong callback để “tháo” future

Một anti-pattern:

~~~java
CompletableFuture<List<Order>> result =
    fetchUserAsync("u-17")
        .thenApply(user ->
            fetchOrdersAsync(user.id()).join());
~~~

Mặc dù kiểu trả về lúc này là `CompletableFuture<List<Order>>`, callback phải **chờ đồng bộ** future orders qua `join()`. Nếu nó chạy trên một worker của pool nhỏ mà orders cũng cần pool đó, các worker có thể bị giữ để đợi việc đang xếp hàng, thậm chí dẫn đến **thread starvation/deadlock**. Đường xử lý còn khó quản lý timeout và cancellation.

Cách sửa:

~~~java
CompletableFuture<List<Order>> result =
    fetchUserAsync("u-17")
        .thenCompose(user -> fetchOrdersAsync(user.id()));
~~~

Ở đây callback **trả stage**, không đợi kết quả của stage bên trong. Khi orders hoàn tất, chain tự hoàn tất; thread chạy callback có thể tiếp tục phục vụ tác vụ khác. Caller vẫn có thể dùng `get()`/`join()` tại **ranh giới đồng bộ thật sự**, như chương trình CLI hoặc test, khi nó chấp nhận blocking.

**Ngoại lệ kỹ thuật:** nếu stage bên trong đã hoàn tất, `join()` có thể trả về ngay. Nhưng mã nghiệp vụ không nên dựa vào điều đó như bảo đảm cho mọi lần chạy.

## 11. Lỗi truyền theo chuỗi và vị trí recovery

Nếu giai đoạn nguồn hoàn tất ngoại lệ, `thenApply`/`thenCompose` được gắn vào nó **không chạy callback bình thường**. Stage phụ thuộc sẽ hoàn tất ngoại lệ; lỗi có thể được bọc trong `CompletionException` khi gọi `join()`, hoặc `ExecutionException` khi gọi `get()`.

Ví dụ:

~~~java
CompletableFuture<String> safeReport =
    fetchUserAsync("u-17")
        .thenCompose(user -> fetchOrdersAsync(user.id()))
        .thenApply(orders -> renderReport(orders))
        .exceptionally(error -> "report unavailable");
~~~

Đây là **đoạn tích hợp minh họa**. `exceptionally` trả một giá trị thay thế, nghĩa là chain mới có thể kết thúc **thành công** với `"report unavailable"`. Đặt fallback cuối chuỗi như vậy có thể nuốt cả lỗi lập trình ở `renderReport`, lỗi downstream và cancellation. Cần phân loại lỗi, log correlation ID, gắn metric và chỉ fallback những lỗi mà nghiệp vụ thực sự cho phép.

Phân biệt công cụ:

| API | Khi nào callback chạy | Kết quả |
| --- | --- | --- |
| `thenApply` | Source hoàn tất bình thường | Tạo giá trị mới, lỗi mapper làm stage mới exceptional |
| `thenCompose` | Source hoàn tất bình thường | Chạy bước phụ thuộc trả stage, kết quả đi theo stage đó |
| `exceptionally(fn)` | Source hoàn tất ngoại lệ | Trả giá trị fallback để **khôi phục** stage mới |
| `handle((value,error) -> ...)` | Source thành công hoặc lỗi | Biến cả hai tình huống thành một kết quả do handler quyết định |
| `whenComplete((value,error) -> ...)` | Source thành công hoặc lỗi | Thích hợp ghi nhận side effect/log; thường giữ outcome gốc nếu callback không tự gây lỗi |

Nếu callback của `thenCompose` ném exception **trước khi** trả stage, future ngoài thất bại. Nếu callback trả một stage rồi stage đó thất bại, future ngoài cũng thất bại theo outcome của stage được nối. Đây là một lợi ích lớn của flatten: người dùng chain chỉ cần quan sát một future kết quả.

Đừng giả định `Future.get()` sẽ trả `null` khi có lỗi; nó ném exception. Nếu dùng `exceptionally` để biến lỗi thành kết quả hợp lệ thì phải quy định rõ trạng thái lỗi nghiệp vụ, không vô tình đánh dấu đơn hàng thất bại thành thành công.

## 12. Timeout và cancellation không tự dừng mọi tác vụ bên trong

Các hàm của `CompletableFuture` như `orTimeout` và `completeOnTimeout` hỗ trợ chính sách thời gian:

~~~java
CompletableFuture<Summary> withDeadline =
    reportFuture.orTimeout(2, TimeUnit.SECONDS);

CompletableFuture<Summary> withFallback =
    reportFuture.completeOnTimeout(defaultSummary, 2, TimeUnit.SECONDS);
~~~

Đây là **đoạn API minh họa** (cần import `TimeUnit` và biến nghiệp vụ). Cả hai phương thức thời gian này có thể **hoàn tất chính future được gọi** khi đến hạn. Nếu cùng gọi hai phương thức trên *cùng một instance*, chúng có thể tác động lẫn nhau; ví dụ tách biệt để minh họa, không phải hai timeout độc lập cùng áp lên một future.

- `orTimeout`: hoàn tất future bằng `TimeoutException` nếu chưa hoàn tất đúng hạn.
- `completeOnTimeout`: hoàn tất future bằng giá trị fallback nếu chưa xong đúng hạn.
- Timeout của future **không bảo đảm** tự hủy HTTP request, JDBC query, downstream job hoặc thread đã bắt đầu chạy. Cần timeout/cancel ở client thực, transaction và I/O boundary.
- `cancel(true)` trên `CompletableFuture` của JDK không có cơ chế interrupt tác vụ tính toán bên dưới như một `FutureTask` gắn với worker; nó là một kiểu exceptional completion. Các stage đã khởi động có thể tiếp tục gây side effect.
- Cancellation ở một stage không đồng nghĩa với propagation toàn bộ chain phụ thuộc đã được triển khai. Việc dừng tác vụ, thu hồi tài nguyên và tránh tạo dữ liệu trùng phải thiết kế theo hệ thống thực.

Một pipeline phụ thuộc có thể bị timeout trong khi stage nội bộ vẫn xử lý xong sau đó. Vì vậy dùng idempotency key, trạng thái cuối rõ ràng và metrics để xử lý kết quả đến muộn.

## 13. Hiệu năng, thread pool và context trong Spring

`thenCompose` giúp loại bỏ việc đợi blocking trong callback nhưng **không làm hệ thống có thêm công suất**. Khi ứng dụng dùng Spring `@Async` và nhiều task cùng dùng một `ThreadPoolTaskExecutor`:

1. Chọn `corePoolSize`, `maxPoolSize`, `queueCapacity`, chính sách từ chối và shutdown phù hợp. Queue quá dài có thể che giấu quá tải; queue hữu hạn cần xử lý rejection.
2. Ghi metric queue length, active worker, wait time, task duration, failure và timeout. Một future được tạo ra không có nghĩa là task chắc chắn được nhận/chạy thành công.
3. Callback CPU-heavy nên chạy trên executor chuyên dụng hoặc executor được kiểm soát; tránh biến `commonPool` thành nơi xử lý I/O blocking khối lượng lớn.
4. `ThreadLocal`, MDC, security và request context không tự bảo đảm truyền sang thread hoàn tất future/continuation. Khi cần context propagation, dùng `TaskDecorator` hoặc adapter/context mechanism phù hợp và dọn dữ liệu theo phạm vi.
5. Trong ứng dụng Spring imperative, transaction gắn với thread hiện tại. `thenCompose` **không truyền transaction caller sang worker khác**; mỗi bước ghi DB cần giao dịch đúng ranh giới và không giả định việc của stage trước đã commit trừ khi contract bảo đảm.
6. Bean singleton được nhiều stage cùng gọi phải thread-safe; `thenApply` không bảo vệ một `HashMap` mutable hay biến đếm dùng chung.
7. Nếu task cần sống qua JVM restart hoặc phải chạy đúng ít nhất một lần, `CompletableFuture` chỉ là trạng thái **trong bộ nhớ**, không phải durable queue. Dùng job store, outbox/idempotency, retry/backpressure thích hợp.

Một lưu ý quan trọng về quá tải: khi executor nộp task cho `@Async` bị reject, lời gọi `remote.fetchOrder(id)` có thể ném `TaskRejectedException`. Nếu chuyện này diễn ra bên trong callback `thenCompose`, chain sẽ hoàn tất ngoại lệ. Khi đó không retry nóng, không nộp dồn ngay vào pool bão hòa; sử dụng chính sách retry có giới hạn, backoff và jitter khi nghiệp vụ cho phép.

## 14. Phụ thuộc tuần tự hay độc lập song song?

Đừng dùng `thenCompose` cho hai yêu cầu **thực sự độc lập** chỉ vì cả hai đều bất đồng bộ. Ví dụ hai nguồn dữ liệu được gọi độc lập:

~~~java
CompletableFuture<Customer> customer = fetchCustomerAsync(id);
CompletableFuture<Discount> discount = fetchDiscountAsync(id);

CompletableFuture<Pricing> pricing =
    customer.thenCombine(discount,
        (c, d) -> calculatePricing(c, d));
~~~

Đây là **đoạn tích hợp minh họa**, giả định có API và kiểu `Customer`, `Discount`, `Pricing`. Hai yêu cầu đã có thể chạy song song vì cả hai được khởi tạo trước khi kết hợp; `thenCombine` chờ cả hai hoàn tất bình thường để tính pricing.

So sánh khi bước hai **thực sự cần kết quả bước một**:

~~~java
CompletableFuture<Pricing> pricing =
    fetchCustomerAsync(id)
        .thenCompose(customer ->
            fetchDiscountAsync(customer.tier())
                .thenApply(discount ->
                    calculatePricing(customer, discount)));
~~~

Trong trường hợp thứ hai, bước discount phụ thuộc `customer.tier()`, nên không thể khởi chạy đúng trước khi có customer. `thenCompose` nối request thứ hai, `thenApply` bên trong dùng cả customer đã capture và discount để tạo Pricing.

Phân biệt giúp thiết kế latency tốt hơn:

- **Dependency:** stage B cần output của A → `thenCompose`.
- **Independent:** A và B có thể khởi tạo ngay → `thenCombine`/`allOf`.
- **Synchronous mapping:** từ kết quả thành định dạng/DTO mới → `thenApply`.
- **Side effect không trả giá trị:** `thenAccept`/`thenRun` thích hợp hơn việc viết mapper luôn trả dummy value.

## 15. Các lỗi điển hình và cách sửa

| Hiểu nhầm / anti-pattern | Hậu quả | Hướng sửa |
| --- | --- | --- |
| Dùng `thenApply` cho callback trả `CompletableFuture<U>` nhưng mong `U` | Tạo future lồng nhau | Dùng `thenCompose` |
| Dùng `thenApply(x -> fetchAsync(x).join())` | Chặn callback thread, có thể starvation | Dùng `thenCompose(this::fetchAsync)` |
| Nghĩ `thenCompose` mặc định tạo thread mới | Sai về scheduling và resource | Phân biệt `thenCompose` với `thenComposeAsync`/executor |
| Luôn dùng `thenApplyAsync` không truyền executor trong ứng dụng Spring | Callback có thể chạy trong commonPool, khác pool Spring | Cấp executor tường minh khi cần kiểm soát |
| Bỏ qua future kết quả, chỉ nhìn future nguồn | Không theo dõi lỗi xảy ra ở stage phụ thuộc | Theo dõi **future cuối** của pipeline |
| Gọi `get()` ngay sau mỗi stage | Vô tình tuần tự hóa blocking ở caller | Nối stage, chỉ chờ tại boundary thật |
| Đặt `exceptionally` tổng quát và coi fallback là thành công nghiệp vụ | Nuốt lỗi dữ liệu hoặc lỗi lập trình | Chỉ recover lỗi cho phép; tách status/metric |
| Tin `orTimeout` đã dừng remote I/O | Tác vụ có thể tiếp tục sau timeout | Đặt client I/O timeout và cancel/compensation riêng |
| Gọi `@Async` bằng `this.method()` | Bỏ qua proxy, method chạy đồng bộ | Gọi bean thông qua Spring proxy / cấu trúc service phù hợp |
| Tin `thenCompose` chuyển transaction, MDC, security context | Context phụ thuộc thread và cơ chế propagation | Truyền context đúng quy định; giao dịch riêng |
| Trả `null` từ callback của `thenCompose` | Lỗi completion thay vì bỏ qua stage | Trả một `CompletionStage` hợp lệ |
| Cho nhiều task blocking dùng chung pool quá nhỏ | Queue tích tụ, có thể starvation/rejection | Phân biệt I/O và CPU pool, đo tải, giới hạn queue |

## 16. Kiểm thử để chứng minh pipeline hoạt động đúng

Một bộ kiểm thử tốt cho hai phương thức này không chỉ assert giá trị cuối, mà cần chứng minh cấu trúc và đường lỗi:

1. **Transform:** tạo `CompletableFuture.completedFuture(5)`, gọi `.thenApply(n -> n * 2)`; xác nhận `join() == 10`.
2. **Compose:** cho mapper trả một `CompletableFuture<String>` chưa hoàn tất; xác nhận outer stage sau `thenCompose` **chưa xong**, rồi complete inner và assert result.
3. **Nested type:** viết biến kiểu `CompletableFuture<CompletableFuture<String>>` với `thenApply`; compiler cho thấy chính xác lớp future lồng nhau.
4. **Source failure:** `failedFuture(new IllegalArgumentException(...))` phải khiến mapper thường không chạy; stage phụ thuộc fail.
5. **Inner stage failure:** callback của `thenCompose` trả stage fail; outer stage cũng fail và lỗi phải quan sát được.
6. **Executor:** dùng thread-name prefix hoặc custom executor kiểm chứng `thenApplyAsync(fn, executor)` sử dụng executor chỉ định; không assert một thread name cố định khi API không cam kết.
7. **Spring proxy:** gọi `@Async` qua bean được inject; không dùng `new Service()` hoặc self-invocation trong test kỳ vọng async.
8. **Concurrency:** dùng `CountDownLatch` hoặc test scheduler để tránh test giòn dựa vào `Thread.sleep` và thứ tự chạy không xác định.
9. **Timeout/cancellation:** mô phỏng stage chạy lâu và side effect; xác nhận timeout của future không tự rollback kết quả external.
10. **Saturation:** làm đầy executor có queue hữu hạn, xác nhận rejection được phản ánh ở đường hoàn tất lỗi thay vì bị nuốt.

Một ví dụ có thể kiểm tra riêng logic flatten mà không tạo thread:

~~~java
CompletableFuture<String> inner = new CompletableFuture<>();
CompletableFuture<String> flat =
    CompletableFuture.completedFuture("id")
        .thenCompose(ignored -> inner);

System.out.println(flat.isDone()); // false
inner.complete("ready");
System.out.println(flat.join());   // ready
~~~

Đây là **đoạn code ghép vào phương thức Java** (cần import `CompletableFuture`), không phải một class độc lập. Output của hai lệnh `println` lần lượt là `false` và `ready` vì inner chưa hoàn tất trước khi gọi `inner.complete`.

## 17. Checklist dùng trong code review

- Tôi biết callback trả **giá trị U** hay **CompletionStage<U>** trước khi chọn `thenApply` hoặc `thenCompose`.
- Tôi không vô tình tạo `CompletableFuture<CompletableFuture<U>>` khi muốn kết quả phẳng.
- Tôi không dùng `join()`/`get()` bên trong continuation để ép một công việc bất đồng bộ thành đồng bộ.
- Tôi biết callback không hậu tố `Async` có thể chạy inline hoặc trên thread hoàn tất stage nguồn.
- Tôi dùng `thenApplyAsync/thenComposeAsync` và executor tường minh khi có lý do kiểm soát thread pool.
- Tôi không nhầm `thenCompose` với cách chạy song song những công việc không phụ thuộc nhau.
- Tôi kiểm soát lỗi của cả source stage và stage được nối, và đặt recovery đúng ranh giới nghiệp vụ.
- Tôi có chính sách timeout, cancel, backpressure và retry, không nhầm timeout với việc dừng side effect.
- Tôi xác nhận Spring `@Async` được gọi qua proxy, transaction/context không bị giả định lan truyền.
- Tôi biết ai sở hữu **future cuối** và có nhiệm vụ quan sát trạng thái hoàn tất của toàn pipeline.

**Kết luận:** Dùng `thenApply()` khi chỉ cần biến đổi **giá trị hiện có**; dùng `thenCompose()` khi bước kế tiếp phụ thuộc vào giá trị đó và trả về **một giai đoạn bất đồng bộ**, nhờ đó ta có một future **phẳng** để theo dõi toàn bộ kết quả. Trong Spring, thiết kế đúng còn cần chọn executor, kiểm soát số thread, lỗi, timeout và ranh giới proxy/giao dịch.

## Tài liệu tham khảo chính thức

- Java SE 17: [CompletableFuture Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html) — semantics của `thenApply`, `thenCompose`, async variants, completion, exceptions và cancellation.
- Java SE 17: [CompletionStage Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html) — pipeline phụ thuộc, kiểu callback và quy tắc hoàn tất lỗi.
- Spring Framework: [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — `@EnableAsync`, `@Async`, cách chọn executor và `CompletableFuture` return type.
- Spring Framework: [AsyncTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html) — `submitCompletable` và cách đưa task vào executor theo abstraction Spring.
- Spring Framework: [@Async Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html) — signature, qualifier và ràng buộc khi dùng Spring proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-002">thenCombine() kết hợp hai kết quả độc lập</a>

<details>
<summary>Xem chi tiết</summary>

# thenCombine() kết hợp hai kết quả độc lập

> **Module:** Spring Framework – concurrency  
> **Nhóm kiến thức:** Kết hợp `CompletableFuture`  
> **Trọng tâm:** `thenCombine()` dùng để **kết hợp kết quả của hai tác vụ không phụ thuộc dữ liệu lẫn nhau**, sau khi cả hai `CompletionStage` hoàn thành bình thường. Nó tạo **một stage mới**, không tự khởi chạy hai tác vụ, không đòi hỏi phải đợi tuần tự bằng `get()`/`join()` và không bảo đảm hai việc luôn chạy trên hai thread khác nhau.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích đúng điều kiện chạy `thenCombine`, kiểu tham số hàm `BiFunction` và kiểu trả về `CompletableFuture<V>`.
2. Tạo hai `CompletableFuture` độc lập bằng `supplyAsync` hoặc nhận chúng từ hai API bất đồng bộ rồi kết hợp thành kết quả có kiểu rõ ràng.
3. Phân biệt “độc lập” với “song song”, và phân biệt `thenCombine` với `thenCombineAsync`.
4. Dự đoán cách hoạt động nếu một/both upstream thất bại, bị hủy, không bao giờ hoàn thành hoặc nếu chính hàm kết hợp ném exception.
5. Biết sử dụng `exceptionally`, `handle`, `whenComplete`, timeout và cancellation có chủ đích.
6. Dùng `thenCombine` trong một service Spring phối hợp hai nguồn dữ liệu, không tự chặn request thread và không đánh mất khả năng quan sát lỗi.
7. Biết khi nào nên dùng `thenCompose`, `thenApply`, `allOf` hoặc `applyToEither` thay vì `thenCombine`.
8. Chạy ví dụ Java 17 và ví dụ Spring Context có output xác định, cùng các bài kiểm thử không phụ thuộc `Thread.sleep()`.

## 2. Bài toán: hai kết quả độc lập, một phản hồi tổng hợp

Giả sử endpoint cần trả báo giá gồm:

- **Thông tin sản phẩm**: dịch vụ A trả tên sản phẩm theo mã.
- **Giá hiện tại**: dịch vụ B trả giá của cùng mã.
- **Báo giá cuối**: ghép tên và giá thành một `Quote`.

Hai lời gọi A và B chỉ cần cùng mã sản phẩm, không cần lấy kết quả A để bắt đầu B. Vì vậy, chúng có thể được **khởi động độc lập** và kết quả được kết hợp sau khi cả hai hoàn thành.

Cách dễ viết nhưng có thể tạo chờ tuần tự:

~~~java
String product = productClient.fetchName(id);  // Có thể chặn.
int price = pricingClient.fetchPrice(id);      // Chỉ gọi sau bước trước.
return new Quote(product, price);
~~~

Đây là **pseudocode Java trong service đồng bộ**, giả định hai client đã tồn tại. Nếu mỗi lời gọi mất khoảng 300 ms và 500 ms, trong trường hợp không chồng lấp, tổng thời gian thường khoảng 800 ms cộng overhead.

Nếu hai API đã hỗ trợ bất đồng bộ:

~~~java
CompletableFuture<String> nameFuture = productClient.fetchNameAsync(id);
CompletableFuture<Integer> priceFuture = pricingClient.fetchPriceAsync(id);

CompletableFuture<Quote> quoteFuture =
        nameFuture.thenCombine(priceFuture, Quote::new);
~~~

Đây là **snippet cho ứng dụng có hai async client và record/class Quote**, chưa phải chương trình độc lập. `quoteFuture` chỉ có giá trị sau khi cả hai nguồn hoàn thành bình thường.

Sơ đồ:

~~~text
                              +--> fetchNameAsync(id) ----> "Book" ---+
Request / orchestration ------|                                      | 
                              +--> fetchPriceAsync(id) ----> 50 -----+--> thenCombine
                                                                            |
                                                                            v
                                                                  Quote("Book", 50)
                                                                            |
                                                                            v
                                                                  CompletableFuture<Quote>
~~~

Điểm quan trọng là **không chờ lấy kết quả A rồi mới nộp B**. Tuy nhiên, cấu trúc này chỉ tạo điều kiện để chồng lấp thời gian; nếu backend, connection pool hoặc executor chỉ cho chạy một task tại thời điểm, hai công việc vẫn có thể thực thi nối tiếp.

## 3. Chữ ký API và ý nghĩa từng thành phần

Với `CompletableFuture<T>`:

~~~java
<U, V> CompletableFuture<V> thenCombine(
        CompletionStage<? extends U> other,
        BiFunction<? super T, ? super U, ? extends V> fn);
~~~

Ý nghĩa:

- **`T`:** kiểu kết quả của stage bên trái (`this`).
- **`U`:** kiểu kết quả của stage `other`.
- **`V`:** kiểu kết quả do hàm kết hợp `fn` tạo ra.
- **`other`:** một `CompletionStage` độc lập cần có kết quả.
- **`fn`:** hàm nhận **hai giá trị**, trả **một giá trị**; ví dụ `(name, price) -> new Quote(name, price)`.
- **Giá trị trả về:** một `CompletableFuture<V>` **mới** đại diện cho phép kết hợp.

Javadoc Java nói stage mới thực hiện hàm khi **cả hai stage hoàn thành bình thường** (*both complete normally*). “Hoàn thành bình thường” là completed không có exception/cancellation, không có nghĩa kết quả phải khác `null`.

### Ví dụ nhỏ nhất

~~~java
CompletableFuture<Integer> left =
        CompletableFuture.completedFuture(20);
CompletableFuture<Integer> right =
        CompletableFuture.completedFuture(22);

CompletableFuture<Integer> total =
        left.thenCombine(right, Integer::sum);

System.out.println(total.join());
~~~

Đây là **snippet Java**, đặt trong một phương thức có import `java.util.concurrent.CompletableFuture`. Output:

~~~text
42
~~~

Vì hai input đã hoàn thành, phép cộng rất có thể được thực hiện **ngay khi đăng ký continuation**; đó là hành vi hợp lệ. Đừng suy “CompletableFuture thì mọi callback luôn chạy ở worker”.

### thenCombine không làm những việc nào?

- Không tự gọi `supplyAsync` hoặc tự tạo task cho `left`/`right`.
- Không đảm bảo chạy hàm kết hợp trên một thread riêng.
- Không quyết định executor nào xử lý hai nguồn đã tạo trước đó.
- Không chặn bên gọi để đợi cả hai input hoàn tất giống như hai lệnh `get()`; tuy nhiên, **hàm kết hợp có thể chạy inline ngay khi đăng ký** nếu cả hai đã xong, nên nếu `fn` làm việc nặng/blocking, chính lời gọi đăng ký có thể mất thời gian.
- Không làm failure của hai upstream “biến mất”; stage mới hoàn thành exceptional khi không thể có kết quả kết hợp bình thường.
- Không bảo đảm cancel stage mới sẽ cancel các nguồn đầu vào.

## 4. Quy tắc hoàn thành và thứ tự thực thi

### 4.1. Khi cả hai thành công

~~~text
left:  ---- running ------------> completed("A")
right: ---- running -----> completed(10)

thenCombine(left, right, (a, b) -> a + b)
                                |
                         đủ cả hai giá trị
                                |
                                v
                           completed("A10")
~~~

Hàm `BiFunction` nhận kết quả **theo vị trí tham số**: tham số thứ nhất lấy từ stage gọi `thenCombine`, tham số thứ hai lấy từ `other`. Thứ tự **ai hoàn thành trước** không đảo thứ tự tham số:

~~~java
CompletableFuture<String> name = ...;
CompletableFuture<Integer> price = ...;

CompletableFuture<String> label = name.thenCombine(
        price, (n, p) -> n + " : " + p);
~~~

**Đoạn minh họa:** các future phải được tạo từ trước. Cho dù `price` hoàn thành trước, `n` vẫn là tên và `p` vẫn là giá.

### 4.2. Khi mới có một kết quả

Khi `left` đã completed nhưng `right` chưa completed, `thenCombine` **chưa có hai giá trị** nên `fn` chưa thể chạy. Future kết quả vẫn pending, trừ khi có tình huống exception/cancellation được implementation xử lý theo quy tắc completion.

Một cách kiểm thử chuẩn là tạo hai `new CompletableFuture<>()`, complete từng nguồn một và quan sát `isDone()` của stage mới.

### 4.3. Khi cả hai đã hoàn thành trước khi đăng ký

Nếu cả hai `CompletableFuture.completedFuture(...)` đã hoàn thành, bản **non-Async `thenCombine`** có thể chạy `fn` ngay trong thread gọi `thenCombine`. Hợp đồng không dành riêng một worker thread cho continuation này.

Vì vậy, không nên đặt một phép gọi HTTP blocking hoặc tính toán CPU rất nặng bên trong `thenCombine` non-Async nếu call site cần giữ latency nhỏ. Nếu cần tách tài nguyên cho bước tổng hợp, cân nhắc `thenCombineAsync(..., executor)` có executor riêng và được quản lý.

### 4.4. Nếu hai input “độc lập” nhưng cùng executor một thread

~~~java
ExecutorService single = Executors.newSingleThreadExecutor();

CompletableFuture<Integer> a =
        CompletableFuture.supplyAsync(() -> 20, single);
CompletableFuture<Integer> b =
        CompletableFuture.supplyAsync(() -> 22, single);

CompletableFuture<Integer> result =
        a.thenCombine(b, Integer::sum);
~~~

Đây là **snippet**; cần shutdown `single` trong ứng dụng. Cả hai tác vụ dùng cùng executor một thread nên có thể thực thi lần lượt. Kết quả vẫn là 42, nhưng không nên mô tả chúng như hai CPU job thật sự chạy song song.

**Độc lập** = dữ liệu của tác vụ A không cần kết quả B để bắt đầu, và ngược lại. **Song song** = hệ thống thật sự chạy công việc đồng thời trên nhiều tài nguyên. `thenCombine` yêu cầu tính độc lập logic, không tự cung cấp song song vật lý.

## 5. thenCombine và thenCombineAsync khác gì?

Có ba overload quan trọng:

~~~java
left.thenCombine(right, combiner);

left.thenCombineAsync(right, combiner);

left.thenCombineAsync(right, combiner, customExecutor);
~~~

| Cách dùng | Điều kiện gọi `combiner` | Nơi chạy combiner |
| --- | --- | --- |
| `thenCombine` | Khi hai nguồn completed normally | Có thể inline trên thread hoàn thành một source hoặc thread đăng ký nếu đã xong; không bảo đảm thread riêng |
| `thenCombineAsync` không executor | Cùng điều kiện | Dùng default asynchronous execution facility của stage; với `CompletableFuture` thông dụng là `ForkJoinPool.commonPool()`, có ngoại lệ được Javadoc mô tả |
| `thenCombineAsync(..., executor)` | Cùng điều kiện | Dispatch theo executor được truyền, ví dụ một `ThreadPoolTaskExecutor` do Spring quản lý |

**Async ở đây là đặc tính của bước kết hợp**, không tự chuyển đổi cách thực thi hai upstream. Ví dụ:

~~~java
CompletableFuture<Price> price = pricingService.loadPriceAsync(id);
CompletableFuture<Stock> stock = stockService.loadStockAsync(id);

return price.thenCombineAsync(
        stock,
        (p, s) -> new Offer(p, s),
        aggregationExecutor);
~~~

Đây là **snippet** yêu cầu các class/service `Price`, `Stock`, `Offer` và `aggregationExecutor` đã tồn tại.

### Khi nên chọn loại nào?

- Nếu `combiner` chỉ tạo DTO, cộng vài số hoặc nối hai chuỗi, `thenCombine` thường đủ và tránh thêm một lần schedule.
- Nếu `combiner` thực hiện mapping phức tạp/CPU đáng kể, dùng `thenCombineAsync(..., executor)` với pool có giới hạn là lựa chọn dễ kiểm soát.
- Nếu combiner gọi I/O blocking, tốt hơn nên tách thành **một stage bất đồng bộ rõ ràng** (thường là `thenCompose` hoặc `thenCombine(...).thenCompose(...)`) thay vì vô tình block thread hoàn tất stage.
- Không mặc nhiên dùng common pool cho blocking I/O nặng; thread starvation có thể ảnh hưởng những công việc bất đồng bộ khác trên JVM.
- Một `Executor` được cung cấp tùy biến vẫn có thể chạy inline (ví dụ direct executor), nên không thể bảo đảm “Async overload chắc chắn khác thread” bất chấp executor cụ thể.

### Rejection của executor dùng cho continuation

Nếu `thenCombineAsync` cần schedule hàm kết hợp nhưng executor đã shutdown/bão hòa và từ chối, stage kết quả có thể hoàn thành bất thường do việc điều phối thất bại. Chính sách ở đây nên được kiểm thử theo executor thực tế. **Không chỉ giám sát lỗi của hai nguồn**; hãy giám sát cả lỗi của bước kết hợp và việc schedule nó.

## 6. Ví dụ Java 17 chạy được với hai nguồn độc lập

Chương trình dưới đây tạo **hai worker** để tính thông tin riêng, dùng `CountDownLatch` giữ chúng ở ranh giới trước khi hoàn thành, sau đó:

1. Chứng minh hai tác vụ đã bắt đầu độc lập.
2. Chứng minh `thenCombine` chưa kết thúc khi chưa đủ cả hai kết quả.
3. Nhận kết quả ghép bằng `thenCombine`.
4. Chạy một phép ghép khác bằng `thenCombineAsync(..., aggregationExecutor)` và quan sát đúng thread được cấu hình.
5. Thử một nguồn thất bại rồi khôi phục với `exceptionally`.

### 6.1. Tệp ThenCombineDemo.java

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThenCombineDemo {

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService fetchPool = Executors.newFixedThreadPool(2);
        ExecutorService aggregatePool =
                Executors.newSingleThreadExecutor(
                        r -> new Thread(r, "aggregate-worker"));

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try {
            CompletableFuture<String> name =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        await(release);
                        return "Book";
                    }, fetchPool);

            CompletableFuture<Integer> price =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        await(release);
                        return 50;
                    }, fetchPool);

            CompletableFuture<String> quote =
                    name.thenCombine(price,
                            (n, p) -> n + "-" + p);

            CompletableFuture<String> asyncQuote =
                    name.thenCombineAsync(
                            price,
                            (n, p) -> n + "-" + p
                                    + "@" + Thread.currentThread().getName(),
                            aggregatePool);

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("tasks did not start");
            }

            System.out.println("both started = true");
            System.out.println("quote done before release = "
                    + quote.isDone());

            release.countDown();

            System.out.println("quote = "
                    + quote.get(5, TimeUnit.SECONDS));
            System.out.println("async quote = "
                    + asyncQuote.get(5, TimeUnit.SECONDS));

            CompletableFuture<Integer> broken =
                    CompletableFuture.failedFuture(
                            new IllegalArgumentException("price missing"));
            CompletableFuture<Integer> fee =
                    CompletableFuture.completedFuture(5);

            CompletableFuture<Integer> failed =
                    broken.thenCombine(fee, Integer::sum);

            try {
                failed.join();
            } catch (CompletionException ex) {
                System.out.println("failure cause = "
                        + ex.getCause().getMessage());
            }

            CompletableFuture<Integer> fallback =
                    broken.exceptionally(ex -> 10)
                          .thenCombine(fee, Integer::sum);

            System.out.println("fallback total = " + fallback.join());
        } finally {
            // Dọn cả đường lỗi để không giữ worker mắc kẹt.
            release.countDown();
            fetchPool.shutdown();
            aggregatePool.shutdown();

            if (!fetchPool.awaitTermination(5, TimeUnit.SECONDS)) {
                fetchPool.shutdownNow();
            }
            if (!aggregatePool.awaitTermination(5, TimeUnit.SECONDS)) {
                aggregatePool.shutdownNow();
            }
        }
    }
}
~~~

### 6.2. Biên dịch, chạy và kết quả

~~~bash
javac ThenCombineDemo.java
java ThenCombineDemo
~~~

**Stdout của chương trình khi chạy thành công:**

~~~text
both started = true
quote done before release = false
quote = Book-50
async quote = Book-50@aggregate-worker
failure cause = price missing
fallback total = 15
~~~

### 6.3. Vì sao output xác định?

- `fetchPool` có hai worker để cả hai `supplyAsync` có thể đi tới `started.countDown()` trong ví dụ tải nhẹ.
- Main chỉ vượt `started.await(...)` khi cả hai nguồn báo đã bắt đầu; khi ấy `release` vẫn chưa mở, nên cả hai chưa thể trả `"Book"` và `50`. Do đó `quote.isDone()` ở dòng in bắt buộc là `false`.
- Sau `release.countDown()`, hai tác vụ kết thúc; `quote.get(timeout)` chờ kết quả gộp và in `Book-50`.
- `thenCombineAsync(..., aggregatePool)` chạy bước ghép trên thread do factory đặt tên `aggregate-worker`, không dựa vào tên thread ngẫu nhiên của common pool.
- `failedFuture` là một nguồn đã exceptional, `thenCombine` không gọi `Integer::sum` để tạo kết quả từ nguồn lỗi. `join()` ném `CompletionException`, qua `getCause()` thấy thông điệp `price missing`.
- `exceptionally(ex -> 10)` khôi phục **nguồn broken** thành 10, nên `10 + 5 = 15`.
- Hai pool được shutdown trong `finally` và được chờ có timeout, tránh giữ tiến trình chạy mãi chỉ vì worker chưa kết thúc.

**Giới hạn của demo:** nó điều phối bằng latch để kiểm chứng thứ tự, không mô phỏng thời gian phản hồi mạng. Khi chạy sản phẩm thật, thời điểm hoàn thành phụ thuộc scheduler, I/O, giới hạn connection và lượng tải. Không nên dùng `Thread.sleep` như một cơ chế đồng bộ hóa test.

## 7. Kết quả có kiểu: từ hai nguồn sang DTO/record

Một lợi ích lớn của `thenCombine` là xây **một đối tượng có kiểu** từ hai nguồn không phụ thuộc lẫn nhau:

~~~java
record Product(String id, String name) {}
record Price(long cents, String currency) {}
record ProductView(Product product, Price price) {}

CompletableFuture<Product> productFuture = findProductAsync(id);
CompletableFuture<Price> priceFuture = findPriceAsync(id);

CompletableFuture<ProductView> viewFuture =
        productFuture.thenCombine(priceFuture, ProductView::new);
~~~

Đây là **snippet Java 17**, gồm ba record hợp lệ khi đặt trong phạm vi class/package thích hợp; hai hàm `findProductAsync` và `findPriceAsync` là API giả định, không phải chương trình độc lập.

Ở đây:

- `T = Product`, `U = Price`, `V = ProductView`.
- `thenCombine` nhận `Product` và `Price` thành hai tham số.
- `ProductView::new` là constructor reference có hai tham số tương ứng.
- Future kết quả mang đúng kiểu `CompletableFuture<ProductView>`, không cần ép kiểu hay lưu tạm vào hai biến mutable dùng chung.

Nếu cả hai stage đều hoàn thành bình thường, `ProductView` được tạo đúng một lần cho continuation đó. Tránh thay đổi một shared mutable `ProductView` từ hai callback riêng lẻ vì dễ tạo race condition và trạng thái chưa đầy đủ.

### Nếu nguồn trả null?

`CompletableFuture.completedFuture(null)` là **normal completion**, khác với exceptional completion. Hàm `BiFunction` vẫn có thể được gọi với giá trị `null`. Nếu constructor không cho phép null hoặc business coi null là lỗi, kiểm tra rõ:

~~~java
CompletableFuture<ProductView> viewFuture =
        productFuture.thenCombine(priceFuture, (p, price) -> {
            if (p == null || price == null) {
                throw new IllegalStateException("missing product data");
            }
            return new ProductView(p, price);
        });
~~~

Đây là **snippet tích hợp**. Exception từ `BiFunction` làm **future kết hợp** hoàn thành exceptional; nó không mặc nhiên thay đổi trạng thái của `productFuture` và `priceFuture`.

## 8. Ngoại lệ: khi một hoặc hai upstream thất bại

`thenCombine` chỉ chạy `fn` khi **cả hai stage hoàn thành bình thường**. Nếu một nguồn exceptional, stage kết hợp sẽ không tạo kết quả bình thường từ hai giá trị. Cần hiểu từng đường:

| Tình huống | `fn` có được gọi? | Stage kết hợp |
| --- | --- | --- |
| Cả A, B thành công | Có | Thành công với `V`, trừ khi `fn` ném lỗi |
| A thành công, B thất bại | Không | Hoàn thành exceptional |
| A thất bại, B thành công | Không | Hoàn thành exceptional |
| Cả A, B thất bại | Không | Hoàn thành exceptional; không nên dựa vào thứ tự/cause được chọn khi có nhiều lỗi |
| A, B thành công nhưng `fn` ném exception | Có, rồi ném | Hoàn thành exceptional do chính bước kết hợp |
| Một stage không bao giờ hoàn thành | Không đủ hai kết quả | Có thể tiếp tục pending; thiết kế timeout cho toàn operation |

**Không coi thenCombine là fail-fast có cam kết.** Nếu một source đã lỗi nhưng source còn lại chưa hoàn thành, không nên dựa vào việc stage kết hợp chắc chắn phản hồi lỗi ngay lập tức. Quy tắc/scheduling thực tế phụ thuộc implementation. Hãy áp deadline ở ranh giới API/service thay vì chờ không giới hạn.

### 8.1. Lấy exception bằng get hoặc join

~~~java
CompletableFuture<Integer> broken =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("no price"));
CompletableFuture<Integer> healthy =
        CompletableFuture.completedFuture(5);

CompletableFuture<Integer> total =
        broken.thenCombine(healthy, Integer::sum);

try {
    total.join();
} catch (CompletionException ex) {
    System.out.println(ex.getCause().getMessage());
}
~~~

Đây là **snippet** với import `CompletableFuture` và `CompletionException`. Output:

~~~text
no price
~~~

- `join()` thường ném `CompletionException` (unchecked) cho lỗi bình thường của stage; `get()` thường ném `ExecutionException` (checked), và cả hai cần unwrap để biết nguyên nhân.
- Nếu future bị cancel trực tiếp, `join/get` có thể ném `CancellationException`, nên không ép mọi failure vào một exception class duy nhất.
- Nếu cả hai nguồn cùng thất bại, **đừng dựa vào một thứ tự hoàn thành để chọn cause**. Nếu cần thu thập cả hai lỗi, phải thiết kế wrapper/result hoặc quan sát riêng từng input.

### 8.2. Exception trong BiFunction

~~~java
CompletableFuture<Integer> a =
        CompletableFuture.completedFuture(10);
CompletableFuture<Integer> b =
        CompletableFuture.completedFuture(0);

CompletableFuture<Integer> invalid =
        a.thenCombine(b, (x, y) -> x / y);
~~~

Đây là **snippet**. Hai upstream vẫn hoàn thành bình thường, nhưng continuation chia cho 0 tạo `ArithmeticException` khiến `invalid` exceptional. Đừng chỉ giám sát lỗi khi fetch A/B; phép ghép/mapping/validation cũng có thể thất bại.

## 9. Chọn chiến lược khôi phục lỗi (fallback)

Có hai điểm đặt fallback, và chúng có ngữ nghĩa khác nhau.

### 9.1. Khôi phục từng nguồn trước khi ghép

Nếu giá thiếu thì dùng giá mặc định:

~~~java
CompletableFuture<Integer> safePrice =
        priceFuture.exceptionally(error -> 0);

CompletableFuture<Quote> quote =
        productFuture.thenCombine(safePrice, Quote::new);
~~~

Đây là **snippet**, cần `priceFuture`, `productFuture`, class `Quote`. Chỉ nguồn giá được recover; nếu sản phẩm thất bại thì stage cuối vẫn thất bại. Phù hợp khi nghiệp vụ cho phép degraded response cho **một trường dữ liệu**.

### 9.2. Khôi phục cả kết quả sau khi ghép

~~~java
CompletableFuture<Quote> quote =
        productFuture.thenCombine(priceFuture, Quote::new)
                     .exceptionally(error -> Quote.unavailable());
~~~

Đây là **snippet**, `Quote.unavailable()` là factory method giả định. Nếu **bất kỳ upstream hoặc combiner** thất bại, toàn bộ quote sẽ chuyển sang giá trị dự phòng. Bạn mất khả năng phân biệt giá thiếu với lỗi mapping nếu không log/metric/unwrap trước.

### 9.3. handle và whenComplete khác exceptionally

| API | Khi nào chạy? | Có đổi kết quả? |
| --- | --- | --- |
| `exceptionally(fn)` | Khi stage trước exceptional | Có thể thay lỗi thành fallback `T` |
| `handle((value, error) -> result)` | Khi stage trước thành công hoặc lỗi | Có thể biến đổi cả success lẫn failure sang một `U` mới |
| `whenComplete((value, error) -> ...)` | Khi stage hoàn tất theo cả hai trạng thái | Thường dùng quan sát/log/metrics; **không tự recover** failure chỉ bằng việc ghi log |
| `thenApply(fn)` | Khi stage trước thành công | Biến đổi một kết quả |
| `thenCombine(other, fn)` | Khi hai stage đều thành công | Ghép hai kết quả |

Ví dụ quan sát lỗi:

~~~java
CompletableFuture<Quote> observed =
        productFuture.thenCombine(priceFuture, Quote::new)
                     .whenComplete((value, error) -> {
                         if (error != null) {
                             logger.error("quote aggregation failed", error);
                         }
                     });
~~~

**Snippet tích hợp Spring/logging**, cần `logger`. Nếu `whenComplete` chỉ log, future vẫn có thể hoàn thành exceptional. Nếu callback tự ném lỗi, downstream có thể nhận lỗi từ callback (phụ thuộc trạng thái ban đầu). Đừng lạm dụng logging callback để nuốt failure rồi trả báo giá sai.

### Chọn chính sách theo nghiệp vụ

- Dữ liệu bắt buộc: giữ exceptional completion, trả lỗi phù hợp cho tầng trên.
- Dữ liệu tùy chọn: fallback ở **đúng source** và gắn cờ degraded result nếu API yêu cầu.
- Muốn audit cả hai source: lưu từng kết quả vào kiểu `Result<T>` chứa cả error/value hoặc gắn `handle` riêng trước khi kết hợp.
- Không nên cứ `exceptionally(e -> null)` rồi tiếp tục tạo DTO mà không kiểm tra, vì điều đó che lỗi thành dữ liệu trống khó phát hiện.

## 10. Cancellation, timeout và deadline

### 10.1. Hủy stage mới không đồng nghĩa hủy hai nguồn

~~~java
CompletableFuture<Quote> quote =
        productFuture.thenCombine(priceFuture, Quote::new);

quote.cancel(true);
~~~

Đây là **snippet**. Hủy `quote` không bảo đảm `productFuture` hay `priceFuture` bị cancel tự động. `CompletableFuture.cancel` bản thân nó chủ yếu điều khiển **trạng thái completion**, không phải một cơ chế interrupt đáng tin cậy cho mọi task nền; tham số `mayInterruptIfRunning` không có tác dụng điều khiển interrupt theo cách của `FutureTask`.

Ngược lại, khi một nguồn bị cancel, dependent có thể hoàn thành exceptional, nhưng điều đó không thay thế một chính sách cleanup cho những nguồn còn lại.

### 10.2. Đặt timeout ở ranh giới operation

~~~java
CompletableFuture<Quote> result =
        productFuture.thenCombine(priceFuture, Quote::new)
                     .orTimeout(2, TimeUnit.SECONDS);
~~~

**Snippet**, cần import `TimeUnit`. `orTimeout` (Java 9+) khiến chính `CompletableFuture` nhận timeout completion nếu chưa xong trong thời gian này; nó **không tự dừng** thao tác HTTP/database đang chạy trong hai nguồn. Khi dùng trên một future có nhiều consumer, lưu ý các method như `orTimeout` tác động tới chính đối tượng future được gọi, không nhất thiết tạo bản sao độc lập.

Với `get(2, TimeUnit.SECONDS)`, timeout là **hạn chờ của caller**; việc future có bị complete exceptionally hay không là chuyện khác. Cần hiểu khác biệt giữa timeout thao tác, timeout chờ kết quả và timeout của HTTP client nguồn.

### 10.3. Khi request đã hết hạn

Nếu API không còn cần kết quả:

1. Ghi nhận deadline và cancellation policy ở orchestration layer.
2. Hủy các tác vụ nguồn **nếu** contract của async client hỗ trợ, tránh công việc vô ích.
3. Release permit, connection hoặc subscription theo đúng lifecycle.
4. Phân biệt lỗi timeout với lỗi dữ liệu/validation trong metric và response.
5. Không giả định `future.cancel(true)` chắc chắn interrupt được thread HTTP client đang làm I/O.

Không để `join()` hoặc `get()` không deadline trên request thread khi yêu cầu latency là hữu hạn.

## 11. thenCombine so với thenCompose, thenApply, allOf, applyToEither

| API | Mô hình | Khi nào dùng |
| --- | --- | --- |
| `thenApply(fn)` | `A -> f(A) -> B` | Một kết quả đã có, biến đổi đồng bộ |
| `thenCompose(fn)` | `A -> asyncOperation(A) -> B` | Công việc thứ hai **phụ thuộc kết quả** thứ nhất, cần flatten future |
| `thenCombine(other, fn)` | `A` và `B` độc lập, sau đó `f(A, B) -> C` | Cần **cả hai kết quả** để tổng hợp |
| `thenAcceptBoth(other, action)` | Hai kết quả -> `BiConsumer` | Cần side effect, không trả giá trị tổng hợp (`Void`) |
| `runAfterBoth(other, action)` | Hai stage xong -> `Runnable` | Chỉ cần đợi cả hai, không đọc kết quả |
| `allOf(a,b,c,...)` | Barrier cho nhiều future | Chờ **nhiều nguồn**, trả `CompletableFuture<Void>`, tự lấy giá trị có kiểu sau đó |
| `applyToEither(other, fn)` | Chọn nguồn hoàn thành bình thường trước theo semantics API | Muốn kết quả từ **một trong hai**, không cần cả hai |

### 11.1. thenCompose: phụ thuộc tuần tự về dữ liệu

~~~java
CompletableFuture<Customer> customer = loadCustomerAsync(customerId);

CompletableFuture<Orders> orders =
        customer.thenCompose(c -> loadOrdersAsync(c.id()));
~~~

Đây là **snippet** dùng type/service giả định. `loadOrdersAsync` không thể bắt đầu khi chưa có `c.id()`. Dùng `thenCombine` cho dạng phụ thuộc này là sai về dependency; `thenCompose` loại bỏ dạng lồng `CompletableFuture<CompletableFuture<Orders>>`.

### 11.2. thenCombine: hai nguồn độc lập

~~~java
CompletableFuture<Customer> customer = loadCustomerAsync(customerId);
CompletableFuture<Recommendations> recommendations =
        loadRecommendationsAsync(customerId);

CompletableFuture<HomePage> page =
        customer.thenCombine(recommendations, HomePage::new);
~~~

**Snippet**: cùng có `customerId` nhưng recommendations không cần object `Customer` mới bắt đầu. Hai call có thể được thực hiện chồng lấp.

### 11.3. allOf: nhiều hơn hai nguồn

~~~java
CompletableFuture<Product> product = findProductAsync(id);
CompletableFuture<Price> price = findPriceAsync(id);
CompletableFuture<Stock> stock = findStockAsync(id);

CompletableFuture<ProductPage> page =
        CompletableFuture.allOf(product, price, stock)
                .thenApply(ignored -> new ProductPage(
                        product.join(),
                        price.join(),
                        stock.join()));
~~~

Đây là **snippet**, yêu cầu các type và hàm nghiệp vụ tương ứng. Sau khi `allOf` hoàn thành bình thường, ba `join()` dùng để lấy giá trị đã có nên không phải ba lượt chờ tuần tự tách rời như khi bắt đầu từng call rồi đợi ngay. Nếu bất kỳ nguồn nào lỗi, `allOf` sẽ exceptional, không chạy `thenApply` bình thường.

Khi chỉ có **hai nguồn**, `thenCombine` thường súc tích và giữ type tốt hơn, không cần `join()` trong callback.

### 11.4. Không nhầm with applyToEither

Nếu yêu cầu là “dùng kết quả của server A hoặc B, cái nào có kết quả hợp lệ trước” thì cân nhắc `applyToEither`. Nó không phải API **hợp nhất cả hai**. Với quota, timeout hoặc nguồn thất bại, cần kiểm tra chính xác semantics completion và chọn chiến lược lỗi phù hợp.

## 12. Tích hợp Spring: không để controller tự chạy hai tác vụ nối tiếp

### 12.1. Executor bean và @EnableAsync

Một cấu hình Spring để chạy các lời gọi bất đồng bộ:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(64);
        pool.setThreadNamePrefix("io-");
        return pool; // Spring container gọi initialize/destroy.
    }
}
~~~

Đây là **class cấu hình** trong dự án Spring, chưa có main. `@EnableAsync` cho phép xử lý `@Async`; một `ThreadPoolTaskExecutor` là bean do container quản lý được inject/sử dụng theo tên.

Các con số **chỉ minh họa**, phải chọn theo giới hạn connection, throughput, queue, error budget và độ trễ của downstream. `queueCapacity=64` nghĩa là task có thể phải chờ; nó không bảo đảm hai source của một yêu cầu luôn được chạy cùng lúc nếu pool đang bận.

### 12.2. Hai service độc lập

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    @Async("ioExecutor")
    public CompletableFuture<String> getName(String productId) {
        return CompletableFuture.completedFuture("Book-" + productId);
    }
}
~~~

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
    @Async("ioExecutor")
    public CompletableFuture<Integer> getPrice(String productId) {
        return CompletableFuture.completedFuture(50);
    }
}
~~~

Đây là **hai tệp Java riêng trong một ứng dụng Spring**, yêu cầu import/package/component scanning phù hợp. Trong ví dụ, target method trả một `completedFuture` sau khi hoàn thành phép tính; khi gọi qua **Spring proxy**, Spring async interceptor nộp việc lên executor và caller nhận future đại diện cho invocation bất đồng bộ. Với service thực tế, nội dung method thường là lời gọi I/O blocking hoặc thao tác nghiệp vụ. Không gọi `this.getPrice(...)` bên trong chính bean rồi suy rằng `@Async` sẽ được intercept; self-invocation mặc định bỏ qua proxy.

### 12.3. Service tổng hợp bằng thenCombine

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {
    private final CatalogService catalog;
    private final PricingService pricing;

    public QuoteService(CatalogService catalog, PricingService pricing) {
        this.catalog = catalog;
        this.pricing = pricing;
    }

    public CompletableFuture<String> quote(String productId) {
        CompletableFuture<String> name =
                catalog.getName(productId);
        CompletableFuture<Integer> price =
                pricing.getPrice(productId);

        return name.thenCombine(
                price, (n, p) -> n + " : " + p);
    }
}
~~~

Đây là **service component** trong cùng ứng dụng, không có `main` độc lập. Gọi `catalog.getName` rồi `pricing.getPrice` **không đồng nghĩa đợi task thứ nhất hoàn tất**: hai lời gọi trả future và không `join` trước khi bắt đầu lời gọi thứ hai. Nếu một phép gọi bị từ chối **ngay lúc submit** (ví dụ executor đã shutdown), cần xử lý lỗi đồng bộ ngay tại orchestration layer; nếu một future thất bại sau đó, future kết hợp sẽ phản ánh failure.

### 12.4. Controller trả CompletableFuture

Trong Spring MVC có thể trả một `CompletableFuture` để framework tích hợp xử lý async result:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController {
    private final QuoteService quotes;

    public QuoteController(QuoteService quotes) {
        this.quotes = quotes;
    }

    @GetMapping("/quotes/{id}")
    public CompletableFuture<String> get(@PathVariable String id) {
        return quotes.quote(id);
    }
}
~~~

**Đây là Spring MVC snippet**, cần web starter/dependency, không phải tệp Java 17 chạy độc lập. Tránh ép controller gọi `quote(id).join()` không timeout khi request đang theo mô hình async; việc đó block servlet thread và có thể đánh mất lợi ích chồng lấp/giải phóng tài nguyên. Cần exception mapping phù hợp với contract HTTP và deadline.

### 12.5. Tại sao @Async và thenCombine không thay thế nhau?

- `@Async`/`supplyAsync` quyết định **cách khởi chạy một tác vụ** lên executor.
- `thenCombine` quyết định **dependency kết quả**: kết hợp A và B sau khi cả hai hoàn thành bình thường.
- `thenCombineAsync(..., executor)` thêm lựa chọn **nơi chạy bước ghép**.
- Quản lý transaction/security/MDC không tự xuyên thread. Nếu cần truyền ngữ cảnh, phải dùng cơ chế propagation rõ ràng, không giả định các worker dùng chung `ThreadLocal` của caller.

## 13. Ví dụ Spring Context Java 17 chạy được bằng Maven

Ví dụ kế tiếp **không cần Spring Boot/Web server**: chỉ cần Spring Context, tạo hai bean `@Async` và kết hợp chúng trong một bean orchestration. Đầu ra được xác định bằng kết quả, không phụ thuộc thứ tự hai worker in log.

### 13.1. Cấu trúc dự án

~~~text
spring-combine-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── SpringCombineDemo.java
~~~

Tệp `pom.xml` dùng Java 17 và Spring Framework 6.2.16:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-combine-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.16</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Tệp `src/main/java/demo/SpringCombineDemo.java`:

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class SpringCombineDemo {

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class Config {
        @Bean("ioExecutor")
        ThreadPoolTaskExecutor ioExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(2);
            pool.setMaxPoolSize(2);
            pool.setQueueCapacity(8);
            pool.setThreadNamePrefix("io-");
            return pool;
        }

        @Bean
        Catalog catalog() {
            return new Catalog();
        }

        @Bean
        Pricing pricing() {
            return new Pricing();
        }

        @Bean
        QuoteService quoteService(Catalog catalog, Pricing pricing) {
            return new QuoteService(catalog, pricing);
        }
    }

    static class Catalog {
        @Async("ioExecutor")
        public CompletableFuture<String> name(long id) {
            return CompletableFuture.completedFuture("Book-" + id);
        }
    }

    static class Pricing {
        @Async("ioExecutor")
        public CompletableFuture<Integer> price(long id) {
            return CompletableFuture.completedFuture(50);
        }
    }

    static class QuoteService {
        private final Catalog catalog;
        private final Pricing pricing;

        QuoteService(Catalog catalog, Pricing pricing) {
            this.catalog = catalog;
            this.pricing = pricing;
        }

        CompletableFuture<String> quote(long id) {
            CompletableFuture<String> name = catalog.name(id);
            CompletableFuture<Integer> price = pricing.price(id);
            return name.thenCombine(
                    price, (n, p) -> n + ":" + p);
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            QuoteService service = context.getBean(QuoteService.class);
            String result = service.quote(7)
                                   .get(5, TimeUnit.SECONDS);
            System.out.println("quote = " + result);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SpringCombineDemo
~~~

**Stdout do lớp demo tạo ra** (bỏ qua log Maven/Spring nếu có):

~~~text
quote = Book-7:50
~~~

Giải thích:

1. `@EnableAsync` kích hoạt proxy-based async support.
2. Bean `Catalog` và `Pricing` được gọi qua bean reference được Spring inject, không phải self-invocation.
3. Mỗi method `@Async("ioExecutor")` chạy theo executor được chỉ định và trả future kết quả.
4. `QuoteService.quote` tạo cả hai future rồi đăng ký `thenCombine`; không lấy giá trị `name.get` trước khi tạo `price`.
5. `main` chỉ chờ **tại ranh giới trình diễn** bằng `get(timeout)`, không biến phương thức service thành synchronous. `AnnotationConfigApplicationContext` đóng qua try-with-resources, nên executor bean được Spring hủy theo lifecycle.

**Lưu ý:** chương trình tạo hai task rất ngắn; việc có hai worker không chứng minh chúng thật sự chạy đồng thời trong mỗi lần chạy. Tính đúng đắn của bài là **không có phụ thuộc dữ liệu và hợp nhất sau khi cả hai stage thành công**, không phải việc đo tốc độ của ví dụ quá nhỏ này.

## 14. Những bẫy hiệu năng khi kết hợp future

### 14.1. join quá sớm làm mất sự độc lập

Không nên:

~~~java
String name = catalog.getName(id).join();
int price = pricing.getPrice(id).join();
return CompletableFuture.completedFuture(name + ":" + price);
~~~

Đây là **snippet**. Lời gọi thứ hai chỉ xuất hiện **sau** khi `join()` của future thứ nhất hoàn thành, nên bạn đã biến dependency thành tuần tự và block caller.

Nên:

~~~java
CompletableFuture<String> name = catalog.getName(id);
CompletableFuture<Integer> price = pricing.getPrice(id);

return name.thenCombine(price, (n, p) -> n + ":" + p);
~~~

Hai nguồn được khởi chạy trước khi tổng hợp; caller nhận future và có thể làm việc khác.

### 14.2. Blocking bên trong combiner

~~~java
return a.thenCombine(b, (x, y) -> {
    return externalClient.fetchBlocking(x, y);
});
~~~

**Snippet nguy hiểm**, vì `externalClient.fetchBlocking` giả định chặn. Với `thenCombine` non-Async, chính thread hoàn tất stage cuối có thể bị giữ quá lâu trong combiner. Nếu cần một bước I/O thứ ba phụ thuộc A và B, tạo stage tiếp theo:

~~~java
return a.thenCombine(b, Pair::new)
        .thenCompose(pair ->
                externalClient.fetchAsync(pair.first(), pair.second()));
~~~

Đây là **snippet** với `Pair`/`externalClient` do ứng dụng định nghĩa. `thenCompose` flatten future trả về từ lời gọi I/O kế tiếp thay vì giữ thread của continuation để chờ.

### 14.3. Thread starvation/deadlock do join trong chính pool nhỏ

Với pool chỉ có 1 worker, nếu một task đang chạy trong worker đó gửi thêm hai task về cùng pool rồi gọi `join()` đợi chúng, worker có thể bị chặn và không còn worker nào để thực hiện hai task đã enqueue. Đây có thể là **starvation deadlock** do việc chờ bên trong cùng pool.

Cách tránh: dùng composition không chặn, quản lý pool phù hợp, tách blocking I/O/CPU và tránh thiết kế task trong pool tự đợi các task con ở cùng pool mà không có khả năng thực thi.

### 14.4. Hai nguồn đều dùng chung downstream giới hạn thấp

`thenCombine` không tạo ra “năng lực hệ thống” mới. Nếu A và B đều gọi cùng DB pool 5 connection, số request đồng thời tăng có thể làm latency xấu hơn. Cần giới hạn concurrency, queue capacity, timeout, circuit breaker/bulkhead phù hợp thay vì cứ tăng thread count.

### 14.5. Bỏ Future kết quả

Nếu chỉ viết:

~~~java
nameFuture.thenCombine(priceFuture, (n, p) -> send(n, p));
~~~

**Snippet** với `send` giả định. Nếu `send` thất bại mà bạn bỏ future kết quả, lỗi của continuation có thể bị bỏ sót. Hãy giữ Future, trả về cho caller hoặc gắn observer có ownership rõ ràng. Đừng coi việc gọi `thenCombine` là cam kết side effect đã chạy thành công.

## 15. Kiểm thử không phụ thuộc scheduler

### 15.1. Test thenCombine chỉ chạy sau khi đủ hai kết quả

JUnit 5 snippet (cần imports và assertion tương ứng):

~~~java
CompletableFuture<Integer> left = new CompletableFuture<>();
CompletableFuture<Integer> right = new CompletableFuture<>();

CompletableFuture<Integer> combined =
        left.thenCombine(right, Integer::sum);

left.complete(20);
assertFalse(combined.isDone());

right.complete(22);
assertEquals(42, combined.join());
~~~

Không cần `Thread.sleep`, pool hay network; test kiểm tra trực tiếp **điều kiện về trạng thái** của hai stage.

### 15.2. Test thứ tự hoàn thành đảo ngược

~~~java
CompletableFuture<String> name = new CompletableFuture<>();
CompletableFuture<Integer> price = new CompletableFuture<>();

CompletableFuture<String> quote =
        name.thenCombine(price, (n, p) -> n + ":" + p);

price.complete(50);
assertFalse(quote.isDone());

name.complete("Book");
assertEquals("Book:50", quote.join());
~~~

Đây là JUnit snippet. Thứ tự tham số `(n,p)` không thay đổi khi `price` hoàn thành trước.

### 15.3. Test lỗi upstream và lỗi combiner

~~~java
CompletableFuture<Integer> left = new CompletableFuture<>();
CompletableFuture<Integer> right = new CompletableFuture<>();

CompletableFuture<Integer> sum =
        left.thenCombine(right, Integer::sum);

left.completeExceptionally(
        new IllegalArgumentException("left failed"));
right.complete(2);

CompletionException ex =
        assertThrows(CompletionException.class, sum::join);
assertEquals("left failed", ex.getCause().getMessage());
~~~

Đây là JUnit 5 snippet; cần import các type và `assertThrows/assertEquals`. Với **một nguồn lỗi, một nguồn thành công**, cause kiểm tra được. Với **cả hai nguồn lỗi**, đừng khóa test vào một cause được chọn nếu contract không quy định.

Để kiểm tra combiner exception, cho cả hai thành công và làm `BiFunction` ném `IllegalStateException`; xác minh combined stage exceptional và hai nguồn vẫn successful.

### 15.4. Kiểm thử executor và performance

- Với `thenCombineAsync(..., executor)`, cấp executor có `ThreadFactory` đặt tên kiểm soát được rồi assert tên thread hoặc dùng probe phù hợp; test nên đợi `Future` có timeout.
- Kiểm tra saturation/rejection của executor cấp cho continuation.
- Dùng `CountDownLatch` khi muốn chứng minh hai task đã bắt đầu trước khi mở cổng hoàn tất.
- Đo latency p95/p99, số task active/queued, lỗi upstream và exception từ combiner.
- Kiểm tra cancellation, timeout và graceful shutdown; xác nhận không rò rỉ thread/connection.
- Với Spring `@Async`, test gọi qua bean proxy trong `ApplicationContext`, không gọi trực tiếp một instance `new` rồi khẳng định async support đang hoạt động.

## 16. Checklist triển khai thực tế

- [ ] Hai công việc thật sự **không cần kết quả của nhau** để bắt đầu; nếu cần, chuyển sang `thenCompose`.
- [ ] Hai future đã được tạo/khởi động trước khi chờ kết quả, không `join` ngay sau future thứ nhất.
- [ ] `BiFunction` chỉ ghép/mapping dữ liệu và không block lâu ở default non-Async path.
- [ ] Nếu combiner nặng, đã chọn `thenCombineAsync(..., executor)` với executor có giới hạn và được quản lý.
- [ ] Có chính sách riêng cho lỗi upstream, lỗi trong combiner và lỗi schedule continuation.
- [ ] Fallback được đặt đúng source hoặc ở cả kết quả, không vô ý che lỗi thành giá trị `null`.
- [ ] Có deadline/timeouts cho toàn operation và từng downstream khi phù hợp.
- [ ] Cancellation/cleanup của hai nguồn được thiết kế rõ, không giả định hủy stage cuối tự hủy tất cả.
- [ ] Có chủ sở hữu Future kết hợp; không bỏ kết quả khi lỗi nghiệp vụ phải được ghi nhận.
- [ ] Với Spring: executor bean, `@EnableAsync`/`@Async` và lời gọi qua proxy hoạt động đúng; không self-invocation.
- [ ] Không chờ `get/join` vô hạn trên request thread hay worker thread có thể gây starvation.
- [ ] Kiểm tra connection pool, queue, thread pool, metric latency/error và quá tải khi nhiều request gọi hai nguồn.
- [ ] Test thành công/lỗi/null/timeout/cancel/đảo thứ tự hoàn thành với cơ chế đồng bộ xác định.

## 17. Những ngộ nhận thường gặp

**1. “thenCombine tự chạy hai tác vụ cùng lúc.”** Sai. Nó chỉ đăng ký dependency kết hợp; hai future phải được tạo/khởi chạy bởi API của nguồn.

**2. “Hai future độc lập nghĩa là chắc chắn chạy song song.”** Sai. Có thể dùng một worker, hoặc upstream thực thi đồng bộ; vẫn độc lập về dữ liệu.

**3. “thenCombine chờ bằng cách block thread gọi.”** Không phải như gọi hai `get()`. Nó tạo stage mới. Tuy nhiên `BiFunction` có thể chạy inline khi cả hai đã hoàn thành; tránh blocking combiner.

**4. “thenCombineAsync làm hai API nguồn trở thành async.”** Sai. Nó chỉ quyết định scheduling của bước kết hợp.

**5. “thenCombine luôn chạy trên commonPool.”** Sai. Bản non-Async có thể chạy inline; chỉ overload Async không executor mới sử dụng default async facility của stage.

**6. “B hoàn thành trước A nên tham số đầu tiên là B.”** Sai. Tham số giữ vị trí nguồn `this` và `other`.

**7. “Một nguồn lỗi thì combiner vẫn có thể tạo partial result.”** Không theo default normal-path. Muốn partial, recover input bằng `exceptionally/handle` trước khi ghép.

**8. “Khi một nguồn lỗi, thenCombine luôn fail-fast ngay lập tức.”** Không nên phụ thuộc vào timing này; cần deadline rõ.

**9. “whenComplete đã log lỗi thì future kết hợp tự thành công.”** Sai. Quan sát lỗi không đồng nghĩa recover.

**10. “Hủy future kết hợp sẽ tự hủy cả hai HTTP request.”** Sai nếu không xây explicit cancellation propagation cho async clients.

**11. “orTimeout tự cancel downstream I/O.”** Sai. Timeout hoàn thành future exceptional, còn tác vụ nền có thể tiếp tục.

**12. “thenCombine phù hợp hơn thenCompose cho mọi trường hợp.”** Sai. `thenCompose` dùng cho bước tiếp theo phụ thuộc kết quả trước; `thenCombine` cho hai nguồn độc lập.

**13. “allOf trả danh sách kết quả của từng future.”** Sai. `allOf` trả `CompletableFuture<Void>`, cần truy xuất kết quả từ từng nguồn sau khi barrier hoàn thành.

**14. “Đã có @Async thì không cần @EnableAsync hoặc proxy.”** Sai đối với Spring default async interception.

**15. “Nhiều thread hơn luôn làm thenCombine nhanh hơn.”** Sai khi giới hạn chính là DB, HTTP connection, CPU hoặc downstream; tăng concurrency có thể gây queue/timeout.

**16. “Future combined không cần lưu vì đã đăng ký callback rồi.”** Sai nếu kết quả/exception cần được quan sát hoặc dùng ở API response.

## 18. Câu hỏi tự luyện

**Câu 1:** `CompletableFuture.completedFuture(10).thenCombine(completedFuture(32), Integer::sum)` cho kết quả gì?

**Đáp án:** 42. Hai nguồn đã hoàn thành bình thường và combiner cộng hai giá trị.

**Câu 2:** Bạn cần lấy `userId` trước rồi mới gọi API tải đơn hàng theo `userId`. Dùng `thenCombine` hay `thenCompose`?

**Đáp án:** `thenCompose`; lời gọi tải đơn hàng phụ thuộc vào kết quả lookup user.

**Câu 3:** Giá hoàn thành trước tên sản phẩm. Tham số đầu tiên trong `name.thenCombine(price, (n,p)->...)` là gì?

**Đáp án:** vẫn là tên `n`. Thứ tự tham số do vị trí `this`/`other`, không phải thời điểm kết thúc.

**Câu 4:** Hàm kết hợp ném `IllegalArgumentException`, hai future đầu đã thành công. Future kết hợp sẽ thế nào?

**Đáp án:** completed exceptionally; hai future nguồn không mặc nhiên bị chuyển thành failed.

**Câu 5:** Vì sao `thenCombineAsync(right, fn, executor)` chưa chắc làm API A và B chạy song song?

**Đáp án:** executor chỉ dùng cho **fn**; hai nguồn vẫn do cơ chế riêng của chúng khởi chạy và có thể cùng chạy trên pool một worker.

**Câu 6:** Một input bị cancel; gọi `combined.join()` có chắc trả giá trị bình thường không?

**Đáp án:** không. Dependent có thể exceptional do cancellation của nguồn; cần xử lý riêng và hiểu rằng hủy stage cuối không tự hủy nguồn còn lại.

**Câu 7:** Có nên gọi `nameFuture.join()` trước khi tạo `priceFuture` trên request thread?

**Đáp án:** không, nếu hai nguồn độc lập và yêu cầu khai thác concurrency. Hãy tạo cả hai future trước rồi combine.

**Câu 8:** Hai upstream đều chậm; `thenCombine` có giới hạn chờ mặc định không?

**Đáp án:** không có deadline nghiệp vụ tự động. Thiết kế timeout ở async client/source và whole-operation boundary.

## 19. Tóm tắt cốt lõi

**Công thức:** khởi chạy hai nguồn độc lập để có `CompletableFuture<A>` và `CompletableFuture<B>`, sau đó dùng `a.thenCombine(b, (x, y) -> combine(x, y))` để tạo `CompletableFuture<C>`. Hàm kết hợp chạy khi cả hai nguồn **thành công**. Bản non-Async không đòi hỏi thread riêng; bản Async chọn default facility hoặc executor được truyền.

Dùng `thenCompose` khi lời gọi thứ hai **phụ thuộc** giá trị thứ nhất; dùng `allOf` khi cần barrier cho nhiều nguồn. Với Spring, kết hợp `@Async`/async client và `ThreadPoolTaskExecutor` cấu hình có giới hạn để điều phối nguồn, rồi trả future kết hợp cho tầng trên. Đừng bỏ quên lỗi, timeout, cancellation, context propagation và chi phí chờ/queue khi vận hành.

## Tài liệu tham khảo chính thức

- Java SE 17 — `CompletableFuture` (thenCombine, thenCombineAsync, exceptionally, cancel): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- Java SE 17 — `CompletionStage`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html
- Java SE 17 — `ExecutorService`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17 — `CountDownLatch`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- Spring Framework 6.2 — Task Execution and Scheduling / @Async: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework 6.2 — `ThreadPoolTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework — Spring MVC async requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-003">allOf() chờ nhiều công việc và cách thu kết quả có kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# allOf() chờ nhiều công việc và cách thu kết quả có kiểu

## 1. Vì sao cần allOf()?

Trong một ứng dụng Spring, một API tổng hợp có thể phải lấy giá, tồn kho và trạng thái vận chuyển từ ba nguồn độc lập. Nếu từng nguồn được gọi qua `@Async` hoặc `CompletableFuture.supplyAsync`, ta có ba `CompletableFuture<T>` có thể hoàn thành ở những thời điểm khác nhau. Yêu cầu nghiệp vụ là **chờ đủ các nguồn cần thiết rồi kết hợp kết quả**, không phải lấy “kết quả nào xong trước”.

Java cung cấp:

~~~java
public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs)
~~~

Khi dùng `CompletableFuture.allOf(a, b, c)`, bạn nhận một **barrier Future**: nó hoàn tất khi **tất cả** các Future đầu vào đã hoàn tất (thành công hoặc lỗi). Nếu tất cả thành công, Future tổng hợp hoàn tất bình thường với giá trị `null` do kiểu trả về là `Void`; **nó không chứa danh sách kết quả**. Nếu có ít nhất một Future lỗi, Future tổng hợp hoàn tất exceptionally.

Vì vậy thiết kế thường có hai bước:

1. **Ghép tín hiệu hoàn tất:** `CompletableFuture.allOf(...)`.
2. **Thu giá trị đúng kiểu:** sau khi barrier **thành công**, đọc từng Future đầu vào bằng `join()` và biến đổi thành `List<T>` hoặc một record/DTO cụ thể.

Ví dụ khái niệm:

~~~java
CompletableFuture<User> userFuture = loadUser();
CompletableFuture<Balance> balanceFuture = loadBalance();

CompletableFuture<AccountView> combined =
        CompletableFuture.allOf(userFuture, balanceFuture)
                .thenApply(ignored ->
                        new AccountView(
                                userFuture.join(),
                                balanceFuture.join()));
~~~

Đây là đoạn minh họa nằm trong service, **không phải chương trình độc lập**: `User`, `Balance`, `AccountView`, `loadUser` và `loadBalance` là kiểu/method do dự án định nghĩa. Bản chất của ví dụ: kết quả có kiểu `CompletableFuture<AccountView>`, không bị biến thành `Object[]` hay mất generic type.

**Nhớ nhanh:** `allOf` **chờ đủ**; `thenApply` **thu kết quả**; `join` **đọc từng giá trị đã hoàn tất**. Không có bước nào trong ba bước này tự tạo worker thread.

## 2. Hợp đồng chính xác của CompletableFuture.allOf

### 2.1. API và kết quả

| Tình huống | `CompletableFuture.allOf(...)` | Điều cần nhớ |
| --- | --- | --- |
| Hai Future chưa xong | Future tổng hợp chưa xong | Không được coi là kết quả hoàn chỉnh |
| Một Future xong, một Future chưa xong | Vẫn chưa xong | `allOf` là barrier chờ **tất cả** |
| Tất cả thành công | Hoàn tất bình thường, giá trị `Void/null` | Đọc đầu vào để lấy giá trị cụ thể |
| Ít nhất một Future hoàn tất exceptionally | Future tổng hợp hoàn tất exceptionally sau khi các đầu vào hoàn tất | Xử lý exception; không chạy nhánh `thenApply` chỉ-dành-cho-thành-công |
| Danh sách rỗng | Future tổng hợp đã hoàn tất với `null` | Hàm `sequence` có thể trả danh sách rỗng |
| Một phần tử `null` trong mảng đầu vào | Ném `NullPointerException` lúc gọi | Kiểm tra danh sách đầu vào |
| Một Future không bao giờ hoàn tất | Barrier có thể chờ vô hạn | Deadline, rejection rõ ràng và theo dõi trạng thái job |

`allOf` không giới hạn thời gian chờ, không tự hủy tác vụ đang chạy, không tự tạo thread pool, không ngăn tác vụ nhận quá tải và **không bảo đảm partial results** được trả về nếu một phần tử lỗi.

Với nhiều lỗi, đừng dựa vào việc `join()` của barrier sẽ báo lỗi nào đầu tiên theo thứ tự thời gian. Khi cần báo cáo từng nguồn lỗi, hãy xử lý từng Future bằng `handle` trước khi gom.

### 2.2. allOf khác anyOf và thenCombine

| Công cụ | Khi nào stage tổng hợp có thể xong? | Kết quả tổng hợp |
| --- | --- | --- |
| `allOf(a,b,c)` | Sau khi cả ba đã hoàn tất | `CompletableFuture<Void>`; không tự lấy các giá trị |
| `anyOf(a,b,c)` | Khi **một** Future bất kỳ xong | `CompletableFuture<Object>`; có thể khó bảo đảm kiểu |
| `a.thenCombine(b, fn)` | Khi cả hai thành công | Kiểu giá trị do `fn` trả về, thích hợp cho hai nguồn |
| `a.thenCompose(fn)` | Khi a xong thành công rồi cần Future tiếp theo | Dùng cho công việc **phụ thuộc tuần tự**, không phải barrier |
| `allOf(...).thenApply(fn)` | Khi mọi Future đầu vào thành công | Kiểu giá trị do `fn` tạo, dùng cho n nguồn |

`anyOf` với đầu vào rỗng trả một Future chưa hoàn tất, còn `allOf` với đầu vào rỗng trả Future đã hoàn tất. Đó là khác biệt quan trọng khi xử lý danh sách động.

### 2.3. allOf không tự khởi chạy các tác vụ

Giả sử:

~~~java
CompletableFuture<String> a =
        CompletableFuture.supplyAsync(() -> "A", executor);
CompletableFuture<String> b =
        CompletableFuture.supplyAsync(() -> "B", executor);
CompletableFuture<Void> barrier = CompletableFuture.allOf(a, b);
~~~

`supplyAsync` mới là thao tác **gửi công việc** tới `executor`; `allOf` chỉ theo dõi sự hoàn tất. Nếu executor là `SyncTaskExecutor`, từng supplier có thể chạy trên caller thread; nếu là một pool bất đồng bộ, công việc được xử lý trên worker của pool. Tham số `Executor` và cấu hình pool mới quyết định execution model, không phải tên `allOf`.

Nếu không truyền `Executor` cho `supplyAsync`, Java thường sử dụng `ForkJoinPool.commonPool()`. Trong dự án Spring cần tách pool báo cáo, JDBC và tác vụ ngắn, hãy truyền executor tường minh hoặc gọi các phương thức `@Async("beanName")` qua Spring proxy; không mặc định rằng mọi CompletableFuture đều chạy trong pool của Boot.

## 3. Pattern chuẩn: thu List<T> theo thứ tự đầu vào

Một danh sách `List<CompletableFuture<T>>` có thể được chuyển thành **một** `CompletableFuture<List<T>>` mà không cần cast từ `Object`.

~~~java
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class FutureCollections {
    private FutureCollections() { }

    public static <T> CompletableFuture<List<T>> sequence(
            List<CompletableFuture<T>> input) {
        List<CompletableFuture<T>> futures = List.copyOf(input);

        CompletableFuture<?>[] array =
                futures.toArray(new CompletableFuture<?>[0]);

        return CompletableFuture.allOf(array)
                .thenApply(ignored ->
                        futures.stream()
                                .map(CompletableFuture::join)
                                .toList());
    }
}
~~~

Đây là một class Java 17 hợp lệ, có thể đặt trong file `FutureCollections.java`. Trong một dự án dùng Java 8–15, `Stream.toList()` chưa có; có thể thay bằng collector phù hợp. `List.copyOf(input)` chụp danh sách các **tham chiếu Future** tại thời điểm gọi, tránh caller sửa cấu trúc danh sách sau khi xây barrier. Nó không sao chép hoặc đóng băng các kết quả `T` có thể mutable. `List.copyOf` không chấp nhận phần tử `null` và bản thân `allOf` cũng từ chối phần tử null.

**Các đặc tính quan trọng:**

- Khi `input` có `CompletableFuture<Report>`, kiểu trả về là `CompletableFuture<List<Report>>`. Compiler giúp kiểm tra kiểu; không ép kiểu `List<Object>`.
- `thenApply` **chỉ chạy khi barrier hoàn tất bình thường**. Nếu một task thất bại, stage trả về cũng exceptional; không đọc vòng lặp và vô tình biến lỗi thành thành công.
- Khi chạy callback `thenApply` sau barrier thành công, từng Future con đã hoàn tất; `join()` lúc này **không chờ thêm** trong điều kiện các Future giữ nguyên terminal state.
- Danh sách kết quả giữ **thứ tự của `input`**, không phải thứ tự task hoàn tất. `Stream.toList()` trả một list không sửa được qua API list.
- `input` rỗng tạo kết quả `CompletableFuture<List<T>>` đã hoàn tất với list rỗng.

Nếu cần kiểm soát thứ tự theo deadline, mức ưu tiên hoặc độ hoàn tất, phải có logic sắp xếp riêng. `allOf` không cung cấp “results in completion order”.

### Sai lầm: trả List<Object> hoặc cast toàn bộ

~~~java
// Anti-pattern minh họa, không phải API để khuyến khích sử dụng.
CompletableFuture<?>[] array = ...;
CompletableFuture<Void> barrier = CompletableFuture.allOf(array);
// Không có hàm barrier.getResults().
// barrier.join() trả null (Void), không thể ép thành List<Report>.
~~~

Một đoạn code dùng `CompletableFuture<?>[]` ở bước tạo barrier **không làm mất kiểu** của danh sách `List<CompletableFuture<Report>>` ban đầu. Giữ danh sách có generic type để thu kết quả ở bước sau. Tránh raw `CompletableFuture[]` và cast không kiểm tra khi không cần.

## 4. Ví dụ Java 17 độc lập với kết quả xác định

Ví dụ sau minh họa ba trường hợp không phụ thuộc tốc độ CPU hoặc lập lịch thread:

1. `allOf` **chưa hoàn tất** khi một trong hai tác vụ vẫn pending, dù tác vụ còn lại đã xong.
2. Thu dữ liệu cùng kiểu thành `List<String>`; thu dữ liệu **khác kiểu** thành record `Summary`.
3. Một Future lỗi làm barrier lỗi; khi muốn giữ thành công từng phần thì chuyển mỗi Future thành `Outcome<T>` trước khi gom.

Lưu thành **`FanInDemo.java`**:

~~~java
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class FanInDemo {
    record Summary(String sku, int stock) { }

    record Outcome<T>(String source, T value, String error) { }

    static <T> CompletableFuture<List<T>> sequence(
            List<CompletableFuture<T>> input) {
        List<CompletableFuture<T>> snapshot = List.copyOf(input);
        CompletableFuture<?>[] array =
                snapshot.toArray(new CompletableFuture<?>[0]);

        return CompletableFuture.allOf(array)
                .thenApply(ignored ->
                        snapshot.stream()
                                .map(CompletableFuture::join)
                                .toList());
    }

    public static void main(String[] args) {
        CompletableFuture<String> first = new CompletableFuture<>();
        CompletableFuture<String> second = new CompletableFuture<>();
        CompletableFuture<List<String>> together =
                sequence(List.of(first, second));

        second.complete("B");
        System.out.println("WAIT_AFTER_B=" + together.isDone());

        first.complete("A");
        System.out.println("TYPED_VALUES=" + together.join());

        CompletableFuture<String> sku =
                CompletableFuture.completedFuture("P-7");
        CompletableFuture<Integer> stock =
                CompletableFuture.completedFuture(4);

        CompletableFuture<Summary> summary =
                CompletableFuture.allOf(sku, stock)
                        .thenApply(ignored ->
                                new Summary(sku.join(), stock.join()));
        System.out.println("SUMMARY=" + summary.join());

        CompletableFuture<String> ok =
                CompletableFuture.completedFuture("OK");
        CompletableFuture<String> failed =
                CompletableFuture.failedFuture(
                        new IllegalStateException("unavailable"));

        CompletableFuture<Void> all =
                CompletableFuture.allOf(ok, failed);

        try {
            all.join();
        } catch (CompletionException ex) {
            System.out.println("ALL_ERROR="
                    + ex.getCause().getClass().getSimpleName());
        }

        CompletableFuture<Outcome<String>> safeOk =
                ok.handle((value, error) ->
                        new Outcome<>("first", value,
                                error == null ? null
                                        : error.getClass().getSimpleName()));

        CompletableFuture<Outcome<String>> safeFailed =
                failed.handle((value, error) ->
                        new Outcome<>("second", value,
                                error == null ? null
                                        : error.getClass().getSimpleName()));

        System.out.println("PARTIAL="
                + sequence(List.of(safeOk, safeFailed)).join());

        System.out.println("EMPTY="
                + sequence(List.<CompletableFuture<String>>of()).join());
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac --release 17 FanInDemo.java
java FanInDemo
~~~

Stdout do chương trình in:

~~~text
WAIT_AFTER_B=false
TYPED_VALUES=[A, B]
SUMMARY=Summary[sku=P-7, stock=4]
ALL_ERROR=IllegalStateException
PARTIAL=[Outcome[source=first, value=OK, error=null], Outcome[source=second, value=null, error=IllegalStateException]]
EMPTY=[]
~~~

**Tại sao xác định?** Không có worker chạy ngẫu nhiên trong ví dụ; hai Future được `complete` thủ công theo thứ tự đã định. Khi mới `second.complete("B")`, `first` chưa xong nên `together.isDone()` phải là `false`. Sau `first.complete("A")`, barrier thành công và kết quả vẫn theo `List.of(first, second)`, nên là `[A, B]` dù B hoàn tất trước.

Phần `Summary` cho thấy cách thu hai loại kết quả `String` và `Integer` thành một **record có kiểu**, không phải `List<Object>`. Phần `ALL_ERROR` cho thấy mặc định `allOf` không trả kết quả thành công một phần. Cuối cùng `handle` biến từng Future thành một `Outcome<String>` **đều hoàn tất bình thường**, cho phép `sequence` thu cả trạng thái thành công và lỗi. Cách làm này có chủ đích: nếu cần toàn bộ công việc đều thành công thì **không** nuốt lỗi; nếu báo cáo chấp nhận thiếu một nguồn thì model hóa lỗi rõ ràng.

## 5. Khác kiểu: tạo DTO thay vì cố gom List<T>

`allOf` nhận `CompletableFuture<?>...` nên có thể chờ những Future mang kiểu khác nhau. Tuy nhiên không có một `T` duy nhất để biểu diễn chính xác cả `User`, `Money` và `Inventory` trong `List<T>`. Khi từng nguồn mang ý nghĩa khác nhau, tạo DTO:

~~~java
// Mã minh họa trong một service; các record được dự án định nghĩa.
CompletableFuture<User> user = findUser(userId);
CompletableFuture<Money> balance = findBalance(userId);
CompletableFuture<Inventory> inventory = findInventory(userId);

CompletableFuture<Dashboard> dashboard =
        CompletableFuture.allOf(user, balance, inventory)
                .thenApply(ignored ->
                        new Dashboard(
                                user.join(),
                                balance.join(),
                                inventory.join()));
~~~

Từng `join()` trả đúng kiểu ban đầu; constructor của `Dashboard` kiểm tra tương thích ở compile time. Điều này dễ đọc hơn:

~~~java
// Anti-pattern: làm mất type-safety và dựa vào vị trí số nguyên.
List<Object> result = List.of(
        user.join(), balance.join(), inventory.join());
User u = (User) result.get(0);
~~~

Trong ví dụ đúng, kết quả là `CompletableFuture<Dashboard>` để caller có thể `thenApply`/`thenCompose`/`handle`, chứ không buộc caller gọi `get()` đồng bộ ngay lập tức. Nếu có một Future đầu vào bị lỗi, `thenApply` tạo DTO **không chạy**; nhận lỗi bằng completion stage hoặc `handle` ở cấp phù hợp.

Với đúng **hai** Future, `thenCombine` có thể diễn đạt ngắn hơn:

~~~java
CompletableFuture<DashboardPart> part =
        user.thenCombine(balance, DashboardPart::new);
~~~

`thenCombine` phù hợp khi chỉ kết hợp hai nguồn và cả hai phải thành công; với ba nguồn trở lên, `allOf` cộng DTO giúp code đối xứng, hoặc có thể chuỗi `thenCombine` nếu cấu trúc phức tạp.

## 6. Tích hợp Spring Boot @Async: ví dụ runnable

Một chương trình thuần Java có thể dùng `CompletableFuture.supplyAsync`; trong Spring, ta thường có service gọi API hoặc DB trên executor riêng và aggregator chịu trách nhiệm ghép kết quả. Ví dụ dưới đây dùng **Java 17 + Maven + Spring Boot 3.3.5**; không yêu cầu web server hoặc DB.

Bộ ví dụ gồm hai phương thức async độc lập trả **hai kiểu record khác nhau**. `CatalogAggregator.fetch` gom cả hai bằng `allOf(...).thenApply(...)`; `ApplicationRunner` in DTO cuối cùng để kiểm thử.

### 6.1. Cấu trúc thư mục

~~~text
spring-allof-demo/
├── pom.xml
└── src/main/java/com/example/fanin/
    ├── FanInApplication.java
    ├── CatalogClient.java
    └── CatalogAggregator.java
~~~

### 6.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>spring-allof-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 6.3. FanInApplication.java

~~~java
package com.example.fanin;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
public class FanInApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(FanInApplication.class, args)) {
            // Context tự đóng sau khi ApplicationRunner hoàn tất.
        }
    }

    @Bean("catalogExecutor")
    ThreadPoolTaskExecutor catalogExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(10);
        pool.setThreadNamePrefix("catalog-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        return pool;
    }

    @Bean
    ApplicationRunner demonstration(CatalogAggregator aggregator) {
        return args -> {
            CatalogAggregator.Snapshot result =
                    aggregator.fetch("P-7").get(5, TimeUnit.SECONDS);
            System.out.println("SNAPSHOT=" + result);
        };
    }
}
~~~

`ApplicationRunner` cố ý gọi `get(5, SECONDS)` để có stdout xác định rồi đóng context. Cách này **chặn startup thread** trong demo; không bắt buộc đối với một web controller muốn bất đồng bộ thật sự. Hai worker core trong bean chỉ là cấu hình mẫu, không phải mặc định của mọi Spring app.

### 6.4. CatalogClient.java

~~~java
package com.example.fanin;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CatalogClient {
    public record Price(String sku, int amount) { }
    public record Stock(String sku, int available) { }

    @Async("catalogExecutor")
    public CompletableFuture<Price> price(String sku) {
        // Giả lập kết quả tra giá, thay bằng HTTP/DB thật khi triển khai.
        return CompletableFuture.completedFuture(new Price(sku, 120));
    }

    @Async("catalogExecutor")
    public CompletableFuture<Stock> stock(String sku) {
        return CompletableFuture.completedFuture(new Stock(sku, 7));
    }
}
~~~

`CatalogClient` là Spring-managed bean được `CatalogAggregator` gọi **qua proxy**, nên hai method được interceptor `@Async` gửi vào `catalogExecutor`. Phương thức implementation trả một `CompletableFuture.completedFuture` chứa kết quả vừa tính: đó là kiểu return hợp lệ của target method, **không** làm mất cơ chế async của proxy bên ngoài. Cần lưu ý method nhỏ như demo không nhất thiết đáng gửi sang worker trong production; mục tiêu chỉ minh họa fan-out và fan-in.

### 6.5. CatalogAggregator.java

~~~java
package com.example.fanin;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class CatalogAggregator {
    public record Snapshot(
            String sku, int amount, int available) { }

    private final CatalogClient client;

    public CatalogAggregator(CatalogClient client) {
        this.client = client;
    }

    public CompletableFuture<Snapshot> fetch(String sku) {
        CompletableFuture<CatalogClient.Price> price =
                client.price(sku);
        CompletableFuture<CatalogClient.Stock> stock =
                client.stock(sku);

        return CompletableFuture.allOf(price, stock)
                .thenApply(ignored -> new Snapshot(
                        sku,
                        price.join().amount(),
                        stock.join().available()));
    }
}
~~~

Điểm chính:

- Lấy **hai handle Future trước**, không gọi `join()` ngay sau mỗi lần dispatch.
- Gộp bằng `allOf(price, stock)` để quan sát **cả hai** đã hoàn thành.
- Trong `thenApply`, đọc kết quả đúng kiểu `Price` và `Stock` để tạo `Snapshot`; đó là callback đồng bộ không-blocking theo dữ liệu đã có.
- `fetch` trả `CompletableFuture<Snapshot>` để caller chọn cách đợi/compose, thay vì chặn thread ngay trong aggregator.
- `@Async` nằm trên phương thức của bean **khác** so với aggregator; không có self-invocation.

### 6.6. Chạy và kết quả

Từ thư mục chứa `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log Spring/Maven, dòng do chương trình in là:

~~~text
SNAPSHOT=Snapshot[sku=P-7, amount=120, available=7]
~~~

Đầu ra DTO không phụ thuộc thứ tự worker hoàn thành, vì `allOf` đợi đủ và record được dựng theo đúng field name trong code. Dù pool có hai core workers, **không bảo đảm** hai method chắc chắn được CPU thực thi đồng thời; queue, thread scheduling và tốc độ công việc quyết định độ chồng lấp thực tế. Bài này kiểm tra correctness của ghép kết quả, không đo benchmark về concurrency.

## 7. Thu tất cả thành công hay chấp nhận thành công một phần?

### 7.1. All-or-nothing: giữ nguyên ngoại lệ

Với một báo cáo đòi hỏi **tất cả** nguồn chính xác, giữ chính sách mặc định:

~~~java
// Snippet trong service: price và stock là CompletableFuture có kiểu.
CompletableFuture<Snapshot> allRequired =
        CompletableFuture.allOf(price, stock)
                .thenApply(ignored -> new Snapshot(
                        price.join(), stock.join()));
~~~

`Snapshot` ở đây là record minh họa có hai field tương ứng kiểu đầu vào, không phải record `Snapshot` trong ví dụ Spring ở mục 6. Nếu `price` lỗi, `thenApply` không tạo snapshot. Gắn `whenComplete((value, error) -> ...)` để log/metric, hoặc `handle` ở tầng API để chuyển lỗi thành status. Trong `@Async` Spring, lỗi nghiệp vụ trong worker có thể được chuyển thành exceptional completion; lỗi **từ chối gửi việc** khi executor đầy có thể xảy ra ngay ở lời gọi proxy (ví dụ `TaskRejectedException`), vì vậy cả fan-out phase cũng cần `try/catch` nếu cần phản hồi 503 thay vì 500 ngẫu nhiên.

### 7.2. Partial success: bình thường hóa từng kết quả trước allOf

Nếu nghiệp vụ vẫn cho phép tổng hợp khi một nhà cung cấp lỗi, hãy **biến mỗi Future thành một kết quả có trạng thái tường minh** trước khi gửi vào `allOf`.

~~~java
// Trích đoạn minh họa; record Outcome<T> do dự án định nghĩa.
record Outcome<T>(String source, T value, Throwable error) { }

CompletableFuture<Outcome<Price>> safePrice =
        price.handle((value, error) ->
                new Outcome<>("price", value, error));

CompletableFuture<Outcome<Stock>> safeStock =
        stock.handle((value, error) ->
                new Outcome<>("stock", value, error));

CompletableFuture<PartialView> partial =
        CompletableFuture.allOf(safePrice, safeStock)
                .thenApply(ignored ->
                        new PartialView(
                                safePrice.join(),
                                safeStock.join()));
~~~

`Price`, `Stock` và `PartialView` trong snippet là model riêng của dự án; tên record `Outcome` chỉ áp dụng cho snippet. Vì `handle` chạy cho cả success/failure và trả về `Outcome` bình thường, barrier mới có thể thành công và callback nhìn thấy **từng lỗi**. Nếu handler ném exception thì Future `safePrice` vẫn có thể exceptional, do vậy handler cần ngắn, không bị lỗi ngoài ý muốn.

Với hệ thống thực, nên chuyển `Throwable` thành mã lỗi đã phân loại thay vì đưa stack trace/exception chứa dữ liệu nhạy cảm thẳng ra HTTP response. Giữ log gốc trên server, còn client nhận `source`, `status`, `message` phù hợp.

### 7.3. Fail-fast khác với đợi đủ rồi báo lỗi

Nếu một nguồn thất bại sớm còn một nguồn khác bị treo, `allOf` có thể chưa hoàn tất vì nó là barrier **chờ mọi Future terminal**. Vì vậy không được dựa vào `allOf` như cơ chế “fail-fast” để trả lỗi lập tức. Muốn kết thúc sớm khi gặp lỗi, cần thiết kế bổ sung: deadline chung, tín hiệu thất bại riêng hoặc thư viện/logic phối hợp có cancellation và quy tắc nhận trạng thái cụ thể.

Nếu nhiều nguồn thất bại, `allOf` không đưa ra danh sách các exception. Trong mô hình partial success, `handle` trên từng nguồn như trên giúp quan sát đầy đủ. Trong mô hình all-or-nothing, có thể log lỗi riêng tại từng Future trước khi tạo barrier, nhưng tránh log cùng một lỗi trùng lặp nhiều lần.

## 8. Timeout, hủy và bảo vệ khỏi chờ vô hạn

### 8.1. allOf không có timeout mặc định

Nếu một Future bị mất do `DiscardPolicy`/`DiscardOldestPolicy` âm thầm bỏ task, bị kẹt I/O hoặc không ai gọi `complete`, barrier cũng có thể pending mãi. `allOf` không nhận thời hạn đợi trong constructor.

Người gọi đồng bộ có thể dùng:

~~~java
// Snippet: combined là CompletableFuture<Snapshot>.
try {
    Snapshot view = combined.get(3, TimeUnit.SECONDS);
    send(view);
} catch (TimeoutException ex) {
    recordDeadlineExceeded();
} catch (ExecutionException ex) {
    recordFailure(ex.getCause());
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Interrupted while waiting", ex);
}
~~~

`send`, `recordDeadlineExceeded`, `recordFailure` ở đây là method nghiệp vụ giả định, không phải ví dụ độc lập. `get(timeout)` giới hạn **thời gian chờ của caller**; không làm `combined` tự hoàn tất và không tự dừng các task con.

Với `CompletableFuture`, có thể làm stage tổng hợp hoàn tất exceptionally khi quá hạn:

~~~java
// Snippet: future tổng hợp do ứng dụng tạo và quản lý.
CompletableFuture<Snapshot> bounded =
        combined.orTimeout(3, TimeUnit.SECONDS);
~~~

`orTimeout` trả **cùng chính** `CompletableFuture` được gọi, không tạo một bản copy độc lập. Sau timeout, `join()` có thể ném `CompletionException` chứa `TimeoutException`. Nhưng các Future con và công việc I/O thực tế vẫn có thể tiếp tục; timeout không thay thế HTTP/DB socket timeout, cancellation, idempotency hoặc quản lý worker lifecycle.

### 8.2. Cancellation của aggregate không phải cancellation của mọi child

~~~java
CompletableFuture<Void> all = CompletableFuture.allOf(a, b, c);
all.cancel(true);
~~~

`cancel(true)` trên `CompletableFuture` làm chính `all` hoàn tất như một cancellation (nếu chưa terminal). Nó **không tự interrupt** các task upstream và không cam kết `a`, `b`, `c` đều bị hủy. Trong implementation của `CompletableFuture`, tham số `mayInterruptIfRunning` không điều khiển trực tiếp việc interrupt task. Muốn quản lý hủy, cần lưu riêng các Future con và thực hiện hủy/cancel request theo hợp đồng executor, đồng thời xử lý việc task vẫn có thể hoàn tất muộn.

Khi task gây side effect DB/HTTP, sau timeout/hủy caller có thể retry và task cũ vẫn ghi dữ liệu. Dùng idempotency key, conditional update và trạng thái job để tránh duplicate. Với công việc lâu dài phải sống qua restart, in-memory `CompletableFuture` không thay thế DB/message queue bền vững.

### 8.3. Không tạo fan-out vô hạn

Với hàng ngàn Future cùng gửi vào pool và queue quá lớn, thời gian chờ của từng task có thể vượt deadline dù worker còn hoạt động. `allOf` không tự điều tiết số task đang in-flight. Áp dụng:

- Pool và queue có giới hạn; xử lý `TaskRejectedException` ngay khi submit.
- Semaphore/bulkhead hoặc chia danh sách thành các nhóm có kích thước tối đa; lưu ý batching tuần tự có thể giảm song song nhưng giúp ổn định tải.
- Timeout tổng và timeout riêng cho từng lời gọi bên ngoài; deadline truyền qua service nếu cần.
- Metric số task đã gửi, active, queued, failed, timeout, rejected và `allOf` đang pending.
- Không blocking `join()/get()` bên trong chính pool nhỏ đang cần chạy các task còn lại; có thể gây starvation/deadlock nếu tất cả worker chờ công việc vẫn nằm trong queue.

Đối với CPU-bound workload, tăng pool vô hạn không tăng throughput tùy ý; với I/O-bound workload phải xét số connection, quota và latency bên ngoài, không chỉ thread count.

## 9. Hiểu đúng thenApply, thenApplyAsync và thread thực thi

~~~java
CompletableFuture<Result> aggregate =
        CompletableFuture.allOf(a, b)
                .thenApply(ignored -> combine(a.join(), b.join()));
~~~

`thenApply` không tự hẹn chạy trên một executor riêng. Theo chính sách `CompletableFuture`, callback **có thể chạy trên thread vừa hoàn tất stage cuối**, hoặc thread tham gia completion khác; nếu barrier đã hoàn tất khi đăng ký, callback cũng có thể chạy ngay trên caller. Vì vậy callback nên **nhẹ**, chỉ tạo record/map hoặc tính toán rẻ.

Nếu bước tổng hợp tốn CPU hoặc ghi file lớn, có thể chọn:

~~~java
CompletableFuture<Result> aggregate =
        CompletableFuture.allOf(a, b)
                .thenApplyAsync(
                        ignored -> combine(a.join(), b.join()),
                        aggregationExecutor);
~~~

`aggregationExecutor` ở đây là `Executor` mà ứng dụng đã khởi tạo và quản lý; snippet chưa phải chương trình độc lập. `thenApplyAsync` gửi callback sang executor được truyền. Cũng cần quản lý rejection: nếu executor tổng hợp không nhận được task, stage có thể exceptional. Đừng dùng `thenApplyAsync` chỉ để đọc hai giá trị rồi tạo record nếu chi phí scheduling lớn hơn công việc.

Một nguy cơ khác là kết hợp kiểu sai:

~~~java
// Anti-pattern: join ngay khi submit, làm tuần tự hóa cả đường gọi.
Result aResult = loadA().join();
Result bResult = loadB().join();
return CompletableFuture.completedFuture(combine(aResult, bResult));
~~~

Đoạn này khiến caller đợi A xong **trước khi** gửi B (trong trường hợp `loadA` mới dispatch lúc gọi), mất lợi thế chạy song song. Hãy **khởi tạo toàn bộ futures trước**, rồi dùng barrier để thu kết quả. Với công việc phụ thuộc tuần tự, `thenCompose` mới là lựa chọn đúng, không cố fan-out các bước chưa có dữ liệu.

## 10. Các lỗi phổ biến

| Triệu chứng / hiểu nhầm | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `allOf(...).join()` trả `null` | Kiểu kết quả của barrier là `Void` | Giữ các Future đầu vào và đọc từng `join()` sau barrier |
| Thu kết quả thành `Object[]` và cast liên tục | Mất generic type ở tầng ứng dụng | `sequence(List<CompletableFuture<T>>)` hoặc record/DTO |
| Một nguồn thất bại, không nhận được kết quả còn lại | `allOf` exceptional, `thenApply` không chạy | Chọn all-or-nothing hoặc `handle` từng Future trước khi gom |
| Kết quả không theo thứ tự hoàn tất | Pattern `sequence` thu theo thứ tự danh sách đầu vào | Sắp xếp theo ID/priority/time nếu cần |
| Một task treo, allOf cũng treo | Không có deadline hoặc task bị silently discarded | Timeout, rejection có tín hiệu, job state và monitoring |
| Timeout tổng nhưng worker vẫn ghi DB | Timeout không cancel upstream task | Cancellation contract, I/O timeout, idempotency |
| Dùng `@Async` mà vẫn chạy inline | Thiếu `@EnableAsync`, self-invocation hoặc dùng `SyncTaskExecutor` | Gọi qua Spring proxy, xác nhận executor |
| Callback tổng hợp làm chậm worker | `thenApply` chạy trên completion thread | Giữ callback nhỏ hoặc dùng `thenApplyAsync` với pool riêng |
| API HTTP vẫn chậm | Controller gọi `join()` ngay sau khi fan-out | Trả async type hoặc job ID/202 theo contract |
| Vượt tải hàng ngàn task | Không giới hạn fan-out và queue | Bound concurrency, pool/queue, backpressure |
| Test thỉnh thoảng sai | Dựa vào `Thread.sleep` để đo thời gian hoàn tất | Dùng `CountDownLatch`, Future thủ công và assert trạng thái |
| Nuốt lỗi bằng `exceptionally` trả dữ liệu giả | Error handling che giấu dữ liệu thiếu | Kết quả partial phải chứa trạng thái nguồn, không báo thành công giả |

### Checklist khi review đoạn dùng allOf

1. Danh sách Future có kiểu cụ thể và không có phần tử `null`?
2. Có **dispatch tất cả** trước khi chờ, không vô tình gọi `join()` tuần tự?
3. `allOf` có được dùng như barrier, không nhầm nó chứa `List<T>`?
4. Code thu kết quả nằm trong `thenApply`/`thenCompose` sau khi barrier thành công, không block ở request thread khi chưa cần?
5. Nếu một Future lỗi, contract của API muốn fail toàn bộ hay trả partial result?
6. Có timeout, lỗi rejection, hủy và cơ chế báo task pending quá lâu?
7. Có giới hạn đồng thời/queue, không fan-out vô hạn hoặc block worker trên các Future cùng pool?
8. Trong Spring, lời gọi `@Async` đi qua proxy từ bean khác, executor có tên đúng và được quản lý vòng đời?
9. Output DTO giữ đúng các field/types, không dựa vào thứ tự hoàn tất ngẫu nhiên?
10. Có test cho input rỗng, nhiều kết quả, một lỗi, nhiều lỗi, timeout, một Future pending và overload?

## 11. Bài tập tự kiểm tra

1. Tại sao `CompletableFuture.allOf(a, b)` trả `CompletableFuture<Void>` chứ không phải `CompletableFuture<List<T>>`? Dữ liệu ở đâu?
2. Hai Future kiểu `CompletableFuture<String>` hoàn tất theo thứ tự B rồi A. Hàm `sequence(List.of(A,B))` trả thứ tự nào? Vì sao?
3. Nếu ba Future có các kiểu `User`, `Balance` và `Stock`, làm thế nào trả `CompletableFuture<Dashboard>` mà không dùng `Object[]`?
4. Vì sao `allOf(...).thenApply(...)` không chạy callback thành công khi một Future con thất bại? Cần làm gì nếu muốn báo cáo cả dữ liệu thành công lẫn lỗi?
5. Nếu một Future báo lỗi nhưng Future còn lại không bao giờ hoàn tất, barrier có thể trả lỗi ngay hay vẫn chờ? Cơ chế nào giải quyết thời hạn?
6. `get(2, SECONDS)`, `orTimeout(2, SECONDS)` và `cancel(true)` khác nhau thế nào? Chúng có dừng DB write không?
7. Khi dùng Spring `@Async`, tại sao controller không nên gọi `join()` ngay lập tức nếu API muốn trả lời nhanh?
8. `thenApply` có bảo đảm chạy trên một pool riêng không? Khi nào chọn `thenApplyAsync`?
9. Nếu 5.000 yêu cầu cùng fan-out 10 tác vụ, `allOf` có tự giới hạn độ đồng thời không? Cần lớp bảo vệ nào?

**Ghi nhớ:** `allOf` giải quyết bài toán **chờ đủ**, không giải quyết bài toán **thu dữ liệu có kiểu** hoặc điều tiết concurrency. Giữ các `CompletableFuture<T>` ban đầu, ghép tín hiệu bằng `allOf`, đọc từng kết quả sau khi tất cả thành công và dựng `List<T>`/DTO rõ ràng. Với lỗi từng phần, dùng `handle` trước khi gom; với production, bổ sung deadline, backpressure và theo dõi task.

## Tài liệu chính thức

- [Java SE 17 — CompletableFuture (allOf, anyOf, thenApply, handle, orTimeout)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — CompletionStage](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html)
- [Spring Framework 6.2 — Task Execution, @Async và Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-004">anyOf() hoàn tất theo một công việc và giới hạn hủy các công việc còn lại</a>

<details>
<summary>Xem chi tiết</summary>

# anyOf() hoàn tất theo một công việc và giới hạn hủy các công việc còn lại

## 1. Mục tiêu: “có kết quả đầu tiên” không có nghĩa “mọi việc khác dừng”

Trong Java 17, `CompletableFuture.anyOf(...)` là công cụ kết hợp nhiều `CompletableFuture`: **future tổng hợp hoàn tất khi một trong các future đầu vào hoàn tất**, nhận kết quả hoặc trạng thái ngoại lệ tương ứng. Nó hữu ích khi muốn lấy phản hồi sớm nhất từ vài nguồn độc lập, chạy các phép tra cứu song song hoặc mô phỏng chiến lược *hedged requests*.

Tuy nhiên, có hai sự kiện rất khác nhau:

1. **Future tổng hợp đã hoàn tất:** caller có thể đọc kết quả đầu tiên, hoặc nhận lỗi của task hoàn tất trước.
2. **Công việc còn lại đã dừng:** worker thread, HTTP request, truy vấn DB hoặc side effect của các task còn lại không còn chạy nữa.

`anyOf()` **chỉ bảo đảm điều thứ nhất**. Nó không tự hủy, ngắt thread hoặc giải phóng kết nối của các công việc chưa thắng. Kể cả khi ta gọi `cancel(true)` trên một `CompletableFuture`, Java không bảo đảm công việc thực tế phía sau future bị ngừng.

**Ví dụ cần hình dung:** ứng dụng gửi yêu cầu đọc dữ liệu tới ba replica. Replica A phản hồi trong 100 ms; B và C còn đang xử lý. `anyOf(A, B, C)` cho phép caller dùng kết quả của A, **nhưng B và C vẫn có thể tiếp tục tiêu thụ CPU, thread, JDBC/HTTP connections và quota dịch vụ**. Nếu các request có side effect, cả ba có thể đã tác động tới hệ thống trước khi nhận kết quả đầu tiên; “chỉ trả một kết quả” không biến chúng thành “chỉ thực hiện một lần”.

Bài này đi từ hợp đồng API, luồng thời gian, ví dụ Java 17 chạy độc lập, cơ chế hủy thực tế và phương án xử lý trong Spring `@Async`.

## 2. Hợp đồng chính xác của CompletableFuture.anyOf()

Khai báo:

```java
public static CompletableFuture<Object> anyOf(
        CompletableFuture<?>... cfs)
```

Giả sử có ba future A, B và C:

```text
A ---- hoàn tất thành công ("A") -------|
                                        |
B ----------- đang chạy ----------------+--> anyOf(A, B, C) = "A"
                                        |
C -------------------- đang chạy -------|

Sau khi anyOf đã hoàn tất:
- Caller có thể tiếp tục với kết quả "A".
- B và C không tự chuyển thành cancelled.
- B và C có thể tiếp tục chạy và gây side effect.
```

Các quy tắc cần nhớ:

- **“Bất kỳ future nào hoàn tất”** bao gồm **hoàn tất thành công, hoàn tất ngoại lệ và hủy**. `anyOf` không mặc định “đợi future thành công đầu tiên”.
- Kết quả có kiểu **`CompletableFuture<Object>`**, do các future nguồn có thể trả các kiểu khác nhau. Không tự ép `Object` thành `String` nếu các đầu vào có kiểu không đồng nhất; dùng kiểu kết quả thống nhất hoặc wrapper/record gắn nhãn nguồn.
- Nếu một đầu vào hoàn tất ngoại lệ trước, future `anyOf` cũng hoàn tất ngoại lệ, thông thường thể hiện qua `CompletionException` khi gọi `join()`. Những đầu vào khác vẫn có thể thành công sau đó.
- Nếu **không truyền future nào**, `anyOf()` trả về một `CompletableFuture<Object>` **chưa hoàn tất**, không phải future hoàn tất với `null`. Gọi `join()` trên nó có thể chờ vô hạn nếu không ai hoàn thành thủ công.
- Nếu danh sách chứa future `null`, API báo `NullPointerException`; xác minh danh sách đầu vào.
- Nếu nhiều future đã hoàn tất hoặc hoàn tất gần như đồng thời, **không dựa vào thứ tự mảng để quyết định chắc chắn ai thắng**. Kết quả cuộc đua phụ thuộc thứ tự completion được quan sát.
- `anyOf` không tự tạo worker thread. Nó **kết hợp các future đã có**; nguồn có thực thi song song hay không phụ thuộc cách tạo các future, executor và các dependency thực tế.

### Phân biệt với các API gần giống

| API | Khi nào future tổng hợp hoàn tất? | Có tự hủy việc còn lại không? |
| --- | --- | --- |
| `anyOf(a, b, ...)` | Một đầu vào bất kỳ hoàn tất (kể cả lỗi) | **Không** |
| `allOf(a, b, ...)` | Tất cả đầu vào hoàn tất; nếu có lỗi sẽ hoàn tất ngoại lệ | **Không** |
| `applyToEither(other, fn)` | Áp dụng hàm cho một kết quả hoàn tất **bình thường** theo hợp đồng CompletionStage; lỗi cạnh tranh cần kiểm thử theo semantics thực tế | **Không** |
| `thenCompose(fn)` | Ghép bước tiếp theo phụ thuộc kết quả một future | **Không** |
| `orTimeout(timeout, unit)` | Hoàn tất ngoại lệ khi tới hạn nếu future được gọi vẫn chưa xong | **Không bảo đảm dừng computation phía sau** |
| `completeOnTimeout(value, timeout, unit)` | Gán giá trị dự phòng khi đến hạn nếu chưa xong | **Không bảo đảm dừng computation phía sau** |

Đừng xem `applyToEither` là sự thay thế không cần suy nghĩ cho bài toán **“kết quả thành công đầu tiên, bất kể các nhánh lỗi trước đó”**. Nếu nghiệp vụ cần bỏ qua lỗi của từng replica và chỉ thất bại khi **tất cả** replica thất bại, hãy viết một coordinator rõ ràng với chính sách kết quả/ngoại lệ cụ thể.

## 3. Vì sao future hoàn tất không làm worker bên dưới ngừng lại?

Một `CompletableFuture<T>` trước hết đại diện cho **trạng thái của kết quả trong tương lai**: chưa hoàn thành, hoàn thành với giá trị, hoàn thành ngoại lệ hoặc bị hủy. Nó không nhất thiết là một handle sở hữu khả năng điều khiển thread đang chạy.

Ví dụ:

```java
CompletableFuture<String> slow =
        CompletableFuture.supplyAsync(() -> callSlowService(), executor);

CompletableFuture<String> fast =
        CompletableFuture.supplyAsync(() -> callFastService(), executor);

CompletableFuture<Object> first =
        CompletableFuture.anyOf(fast, slow);

Object result = first.join();
```

Đây là đoạn minh họa trong phương thức có sẵn `executor` cùng các hàm nghiệp vụ `callSlowService` và `callFastService`; không phải chương trình độc lập.

Sau `first.join()`:

- Chỉ có **`first` đã hoàn tất** theo điều kiện “một đầu vào đã xong”; `slow.isDone()` có thể vẫn bằng `false`.
- Executor có thể vẫn đang giữ worker của `slow`.
- HTTP client có thể vẫn duy trì request tới remote host.
- JDBC query của nhánh chậm có thể vẫn đang chạy.
- Future chậm có thể hoàn thành sau và cập nhật cache, ghi log, gửi event hoặc tạo tác động ngoài ý muốn.
- `first` hoàn tất **không gọi** `slow.cancel(...)` hay `fast.cancel(...)` thay chúng ta.

Có ba tầng cần phân biệt:

```text
TẦNG 1: Result/Future state
   isDone(), complete(), completeExceptionally(), cancel()
          |
          v
TẦNG 2: Execution task
   Runnable/Callable, worker thread, CompletableFuture supplier
          |
          v
TẦNG 3: External work
   JDBC query, HTTP request, file I/O, remote side effect

Chuyển trạng thái ở tầng 1
  KHÔNG tự chứng minh tầng 2 và tầng 3 đã dừng.
```

Vì vậy chính sách “lấy kết quả đầu tiên và bỏ qua các nhánh còn lại” phải là **quyết định riêng**: có chấp nhận chúng chạy tiếp không, có yêu cầu dừng hợp tác không, có handle cho phép hủy thật không, và có idempotency cho tác dụng phụ không?

## 4. Ví dụ Java 17 chạy độc lập: chứng minh giới hạn hủy

Ví dụ dưới có ba thí nghiệm độc lập trong cùng một chương trình:

1. Một nhánh chậm **đang chạy trên executor**, nhánh nhanh được hoàn tất có kiểm soát. `anyOf` trả về kết quả nhanh, không tự hủy nhánh chậm.
2. Gọi `cancel(true)` trên `CompletableFuture` của nhánh chậm trong khi supplier vẫn chờ. Future chuyển thành **cancelled**, nhưng computation vẫn có thể tiếp tục sau khi được cho phép.
3. Một nhánh hoàn tất **ngoại lệ** trước; `anyOf` hoàn tất theo lỗi đó, không đợi nhánh khác thành công và cũng không hủy nhánh khác.

Dùng `CountDownLatch` để tránh phụ thuộc vào timing của `Thread.sleep`. Tạo tệp **`AnyOfCancellationDemo.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnyOfCancellationDemo {
    private static void awaitOrFail(CountDownLatch latch, String stage) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout at " + stage);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted at " + stage, ex);
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch slowFinished = new CountDownLatch(1);
        AtomicBoolean sideEffect = new AtomicBoolean(false);

        try {
            CompletableFuture<String> slow =
                    CompletableFuture.supplyAsync(() -> {
                        slowStarted.countDown();
                        try {
                            // Supplier thật vẫn đang giữ một worker.
                            awaitOrFail(releaseSlow, "releaseSlow");
                            sideEffect.set(true);
                            return "SLOW";
                        } finally {
                            slowFinished.countDown();
                        }
                    }, pool);

            // Chắc chắn supplier chậm đã vào worker trước khi tiếp tục.
            awaitOrFail(slowStarted, "slowStarted");

            // Điều khiển hoàn tất một future khác: không phụ thuộc CPU.
            CompletableFuture<String> quick = new CompletableFuture<>();
            CompletableFuture<Object> first =
                    CompletableFuture.anyOf(quick, slow);

            quick.complete("QUICK");
            System.out.println("winner=" + first.join());
            System.out.println("slowDoneAtWinner=" + slow.isDone());
            System.out.println(
                    "slowAutoCancelled=" + slow.isCancelled());

            // cancel() thay đổi trạng thái CompletableFuture, không bảo đảm
            // ngắt worker đang chạy supplyAsync.
            boolean cancelled = slow.cancel(true);
            System.out.println("manualCancel=" + cancelled);
            System.out.println("slowCancelledNow=" + slow.isCancelled());

            // Dù future đã cancelled, supplier vẫn còn đợi releaseSlow.
            releaseSlow.countDown();
            awaitOrFail(slowFinished, "slowFinished");
            System.out.println(
                    "sideEffectAfterCancel=" + sideEffect.get());

            // Một future hoàn tất lỗi trước: anyOf đi theo lỗi đó.
            CompletableFuture<String> failed = new CompletableFuture<>();
            CompletableFuture<String> other = new CompletableFuture<>();
            CompletableFuture<Object> firstError =
                    CompletableFuture.anyOf(failed, other);

            failed.completeExceptionally(
                    new IllegalStateException("remote failed"));
            try {
                firstError.join();
            } catch (CompletionException ex) {
                System.out.println(
                        "firstError=" + ex.getCause().getClass().getSimpleName());
            }

            System.out.println(
                    "otherAutoCancelled=" + other.isCancelled());
            other.complete("RECOVERED");
            System.out.println("otherResult=" + other.join());

            // Mảng rỗng KHÔNG đồng nghĩa kết quả null đã hoàn tất.
            CompletableFuture<Object> empty = CompletableFuture.anyOf();
            System.out.println("emptyIsDone=" + empty.isDone());
        } finally {
            // Dọn dẹp kể cả khi một assertion hoặc bước khác thất bại.
            releaseSlow.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool did not terminate");
            }
        }
    }
}
```

Chạy bằng **JDK 17+** tại thư mục chứa file:

```bash
javac AnyOfCancellationDemo.java
java AnyOfCancellationDemo
```

**Standard output trong đường chạy thành công** (không tính thông báo của JVM khi máy bị gián đoạn bất thường):

```text
winner=QUICK
slowDoneAtWinner=false
slowAutoCancelled=false
manualCancel=true
slowCancelledNow=true
sideEffectAfterCancel=true
firstError=IllegalStateException
otherAutoCancelled=false
otherResult=RECOVERED
emptyIsDone=false
```

### Phân tích từng phép đo

- `slowStarted` xác nhận supplier đã chạy trên worker và đang đợi `releaseSlow`. Do main chưa mở latch, `slow.isDone()` vẫn bằng `false`.
- `quick.complete("QUICK")` là điểm hoàn tất đầu tiên có kiểm soát, khiến `first.join()` trả `QUICK`. Không có logic hủy nhánh chậm nào bên trong `anyOf`.
- `slow.cancel(true)` chuyển **trạng thái của CompletableFuture** sang cancelled, nên `slowCancelledNow=true`. Theo Java 17 Javadoc, tham số `mayInterruptIfRunning` **không có tác dụng trong triển khai CompletableFuture** vì nó không dùng interrupt để điều khiển processing.
- Khi main mở `releaseSlow`, supplier vẫn đang chạy, thực hiện `sideEffect.set(true)` rồi kết thúc. Future `slow` **vẫn là cancelled**; completion result thật `"SLOW"` không thay thế trạng thái hủy đã thiết lập.
- `failed.completeExceptionally(...)` hoàn tất một nhánh trước. `firstError.join()` ném `CompletionException` với cause là `IllegalStateException`. Future `other` không tự hủy và vẫn có thể hoàn tất với `RECOVERED`.
- `anyOf()` không đối số trả một future chưa được hoàn tất: `emptyIsDone=false`. Không gọi `empty.join()` trong demo vì nó có thể chờ vô hạn.

**Giới hạn của thí nghiệm:** `quick` ở đây được hoàn tất thủ công để thứ tự sự kiện có thể chứng minh. Trong ứng dụng thật, cả fast và slow có thể là các tác vụ của executor hoặc các request HTTP chạy bất đồng bộ; khi đó nhánh thắng phụ thuộc vào thứ tự hoàn tất thực tế. Thí nghiệm vẫn chứng minh được rằng kết quả tổng hợp và vòng đời worker **không phải cùng một thứ**.

## 5. cancel(true) trên CompletableFuture thực sự có ý nghĩa gì?

Javadoc Java 17 nêu rõ: với `CompletableFuture.cancel(boolean mayInterruptIfRunning)`, việc hủy có hiệu lực như một dạng **hoàn tất ngoại lệ** với `CancellationException`. Tham số `mayInterruptIfRunning` không khiến CompletableFuture tự gửi interrupt tới thread đang tính toán.

| Hành động | Điều có thể xác nhận | Điều **không** được bảo đảm |
| --- | --- | --- |
| `anyOf(f1, f2)` hoàn tất | Một đầu vào đã hoàn tất và future tổng hợp có kết quả/lỗi | f2 dừng, bị hủy, giải phóng connection |
| `combined.cancel(true)` | Future tổng hợp chuyển sang trạng thái cancelled nếu chưa hoàn tất | Tất cả đầu vào bị hủy hoặc mọi task nguồn bị dừng |
| `f2.cancel(true)` | f2 có thể hoàn tất với `CancellationException`; `isCancelled()` phản ánh trạng thái | Supplier do `supplyAsync` khởi chạy sẽ bị interrupt/dừng |
| `future.isDone()` | Future không còn ở trạng thái chưa hoàn tất | Remote operation đã thực sự ngừng |
| `future.isCompletedExceptionally()` | Future thất bại/cancel/hoàn tất ngoại lệ | Worker chưa từng gây side effect |
| `Future<?> handle.cancel(true)` từ `ExecutorService.submit` | **Yêu cầu** hủy task, có thể interrupt worker nếu đang chạy | Task chắc chắn dừng ngay; I/O bắt buộc bị hủy |

### Hủy “future tổng hợp” cũng không tự hủy “nguồn”

```java
CompletableFuture<String> a = new CompletableFuture<>();
CompletableFuture<String> b = new CompletableFuture<>();
CompletableFuture<Object> combined = CompletableFuture.anyOf(a, b);

combined.cancel(true);

System.out.println("aggregateCancelled=" + combined.isCancelled());
System.out.println("aCancelled=" + a.isCancelled());
System.out.println("bCancelled=" + b.isCancelled());
```

Đây là **snippet độc lập trong method** (cần import `CompletableFuture`). Với hai đầu vào chưa hoàn tất, ba dòng kết quả tương ứng là `true`, `false`, `false`. Việc chuyển trạng thái của **aggregate** không tự động truyền hủy ngược về hai đầu vào.

### Hủy nhánh thua không đồng nghĩa việc thực thi dừng

Giả sử A đã thắng. Ta tự gọi `b.cancel(true)`. Nếu future B được hoàn tất bởi một supplier đang chạy, biến trạng thái B có thể chuyển sang cancelled, nhưng supplier, DB query hoặc HTTP call vẫn tiếp tục. Supplier sau đó có thể gọi `complete("B")` hoặc trả kết quả, nhưng kết quả ấy không thể thay thế trạng thái cancelled đã có; tác dụng phụ thực tế có thể vẫn xảy ra.

**Hệ quả:** một chương trình in `b.isCancelled() == true` **không chứng minh** nó đã tiết kiệm thread, kết nối hoặc chi phí remote request. Phải đo thread/task/HTTP client hoặc cơ chế cancellation của dependency.

## 6. Khi thật sự muốn dừng công việc: cancellation phải hợp tác

Việc dừng thực thi phụ thuộc vào **loại work** và **handle kiểm soát nó**.

### 6.1. Runnable/Callable với Future của ExecutorService

Nếu tự sở hữu task từ `ExecutorService.submit(Callable)`, kết quả nhận được là `Future<T>` có quan hệ trực tiếp hơn với task của executor. Gọi `cancel(true)` có thể cố ngắt worker thread nếu task đang chạy.

```java
Future<?> handle = executor.submit(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        performSmallChunk();  // Mỗi phần việc nên hữu hạn và kiểm tra hủy.
    }
});

// Khi không cần task nữa:
handle.cancel(true);
```

Đây là snippet minh họa với `executor`, `performSmallChunk()` và import có sẵn; không phải chương trình độc lập. Nếu mã đã bị chặn trong phương thức có hỗ trợ interrupt, `InterruptedException` cần được xử lý đúng và thường nên khôi phục cờ interrupt khi không thể ném tiếp:

```java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
```

Lưu ý: không phải mọi API I/O hoặc native library đều phản ứng với interrupt giống nhau. Ngay cả `Future.cancel(true)` cũng là **yêu cầu hủy** chứ không phải giao kèo “mọi side effect bị rollback”.

### 6.2. Cờ dừng hợp tác

Một `AtomicBoolean stopRequested` hoặc cancellation token có thể được task kiểm tra giữa các bước:

```java
AtomicBoolean stopRequested = new AtomicBoolean(false);

CompletableFuture<String> task =
        CompletableFuture.supplyAsync(() -> {
            while (!stopRequested.get()) {
                doBoundedStep();
            }
            return "stopped";
        }, executor);

// Khi đã có kết quả cần dùng từ nhánh khác:
stopRequested.set(true);
```

Đây là snippet minh họa; `doBoundedStep()` và `executor` cần định nghĩa. Dùng cờ riêng theo task/operation; không dùng một cờ dừng toàn cục vô tình hủy công việc của request khác. Nếu một bước bên trong bị kẹt trong HTTP/JDBC không có timeout, kiểm tra cờ ở vòng sau cũng không giúp dừng ngay: cần deadline và cancellation tại client tương ứng.

**Quy tắc:** cancel token chỉ hữu ích nếu computation **thường xuyên quan sát token**, từng bước đủ ngắn, tài nguyên được đóng đúng cách và không tiếp tục gây side effect sau khi đã nhận tín hiệu dừng.

### 6.3. HTTP request, JDBC và tác vụ remote

- Với HTTP client bất đồng bộ, xem API hủy request/đóng response/connection hoặc cancel subscription theo client cụ thể. Không chỉ hủy `CompletableFuture` rồi tự nhận đã ngắt kết nối.
- Với JDBC, cần transaction/query timeout, cancellation API của driver (ví dụ `Statement.cancel()` nếu được hỗ trợ) và quản lý transaction/connection đúng vòng đời. Không giả định Java interrupt tự rollback câu SQL phía database.
- Với WebClient/Reactor, cancellation của `Mono`/`Flux` được đưa qua lifecycle subscription; nếu sử dụng `toFuture()`, phải xác minh semantics của phiên bản adapter và client, không suy rộng từ `CompletableFuture.cancel` thuần.
- Với lời gọi gRPC, message listener hoặc workflow engine, sử dụng API cancellation riêng của chúng. Một task đã đến server bên kia vẫn có thể hoàn thành dù caller không đợi kết quả nữa.
- Với dữ liệu có side effect, một thao tác đã commit **không thể bị “uncommit”** chỉ vì future bị hủy. Cần idempotency, transaction đúng phạm vi hoặc compensating action.

## 7. Khi chỉ cần kết quả nhanh nhất, có nên hủy các nhánh khác?

Không có đáp án duy nhất; hãy dựa trên chi phí và quy tắc nghiệp vụ.

**Có thể cho chạy tiếp** nếu mỗi nhánh là công việc nhẹ, sẽ hoàn tất gần như ngay, tiếp tục chạy không gây side effect nguy hiểm, và chi phí hủy còn cao hơn chi phí thực hiện. Ví dụ các phép tính ngắn dùng CPU có giới hạn, không cần cố gắng ngắt giữa chừng.

**Nên tìm cách dừng/cancel** nếu nhánh thua là HTTP request tốn quota, truy vấn DB dài hoặc tác vụ đòi nhiều worker/connection. Dùng API cancellation thật của client và đặt timeout; **đo** xem tài nguyên có được trả lại hay không.

**Không nên chạy đua nhiều bản sao** đối với thao tác ghi dữ liệu không idempotent. Ví dụ cùng gửi yêu cầu “thu tiền” tới hai endpoint rồi lấy phản hồi nhanh nhất có thể khiến **hai giao dịch được thực hiện** dù chỉ một future được hiển thị. Kết quả “first wins” không bảo vệ exactly-once.

Một kế hoạch thực tế:

1. Xác định mỗi nhánh có read-only/idempotent hay có side effect.
2. Giới hạn số bản sao được phép chạy (bulkhead, executor, connection pool).
3. Đặt **deadline** cho cả cuộc đua và timeout riêng từng dependency.
4. Khi có kết quả phù hợp, báo hiệu dừng cho nhánh thua qua **cơ chế riêng** (cờ hợp tác, cancellation handle, client cancellation).
5. Đợi/quan sát việc cleanup nếu nghiệp vụ đòi hỏi, nhưng không làm request đồng bộ chờ vô hạn chỉ để “hủy cho xong”.
6. Log và đo cancellation thực sự, task in-flight, active connections và chi phí request thừa.

## 8. anyOf() lấy completion đầu tiên, không nhất thiết lấy thành công đầu tiên

Giả sử có ba nguồn:

```text
t=50ms   A hoàn tất ngoại lệ: TimeoutException
t=90ms   B hoàn tất thành công: "DATA-B"
t=120ms  C hoàn tất thành công: "DATA-C"
```

`anyOf(A, B, C)` hoàn tất ngoại lệ từ A ở 50 ms. Nó **không chờ** B tới 90 ms và cũng không tự retry. Đây là hành vi đúng theo contract, nhưng có thể **không đáp ứng nghiệp vụ** “chỉ cần một replica trả thành công”.

Nếu muốn “thành công đầu tiên; chỉ thất bại khi tất cả cùng lỗi”, cần một coordinator khác. Ví dụ utility Java 17 tổng quát:

```java
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public static <T> CompletableFuture<T> firstSuccessful(
        List<? extends CompletableFuture<? extends T>> inputs) {

    if (inputs.isEmpty()) {
        return CompletableFuture.failedFuture(
                new IllegalArgumentException("No candidate futures"));
    }

    CompletableFuture<T> answer = new CompletableFuture<>();
    AtomicInteger failuresRemaining = new AtomicInteger(inputs.size());

    for (CompletableFuture<? extends T> candidate : inputs) {
        candidate.whenComplete((value, error) -> {
            if (error == null) {
                answer.complete(value); // Một thành công đủ để thắng.
            } else if (failuresRemaining.decrementAndGet() == 0) {
                // Đến đây mọi candidate đều đã thất bại.
                answer.completeExceptionally(error);
            }
        });
    }
    return answer;
}
```

**Đây là một phương thức utility**, cần đặt vào class và import đầy đủ; không phải chương trình chạy độc lập. Code dùng callback `whenComplete`; tương tự `anyOf`, nó **không tự hủy những nguồn còn lại**. Nếu tất cả thất bại, ví dụ chỉ giữ lỗi của callback thất bại cuối cùng; hệ thống sản xuất có thể cần gom nguyên nhân, mã lỗi, deadline và thông tin từng replica để debug. Nếu nhiều thành công xảy ra cùng lúc, future kết quả chỉ hoàn tất thành công một lần; caller không nên suy diễn chính xác tác vụ vật lý nào chạm đích trước chỉ dựa vào thứ tự callback.

**Quan trọng:** phương thức này khác `anyOf` về **ngữ nghĩa chọn thành công**, không giải quyết hủy công việc. Cơ chế cleanup/stop task thua vẫn là vấn đề riêng.

### Gắn danh tính nguồn để tránh mất ngữ cảnh

Vì `anyOf` trả `Object`, nhiều service có cùng kiểu kết quả sẽ khó biết “ai thắng”. Có thể chuẩn hóa kết quả thành record:

```java
record Result(String source, String payload) {}

CompletableFuture<Result> a =
        sourceAFuture.thenApply(value -> new Result("A", value));
CompletableFuture<Result> b =
        sourceBFuture.thenApply(value -> new Result("B", value));

CompletableFuture<Object> raced = CompletableFuture.anyOf(a, b);
Result winner = (Result) raced.join();
```

Đây là snippet với `sourceAFuture`, `sourceBFuture` đã được tạo và đều trả `String`. Ép kiểu `Result` chỉ an toàn vì **cả hai input** được chuẩn hóa thành cùng một kiểu; nếu có thêm future trả kiểu khác vào `anyOf`, phải điều chỉnh mô hình.

## 9. Timeout và cancellation: hai vòng đời khác nhau

`CompletableFuture.orTimeout(...)` và `completeOnTimeout(...)` thường bị hiểu sai khi kết hợp với `anyOf`.

```java
CompletableFuture<Object> first =
        CompletableFuture.anyOf(remoteA, remoteB)
                .orTimeout(800, TimeUnit.MILLISECONDS);
```

Đây là snippet trong môi trường có hai future `remoteA`, `remoteB` và import `TimeUnit`. Nếu 800 ms trôi qua trước khi bất kỳ nhánh nào xong, future tổng hợp sẽ hoàn tất ngoại lệ với timeout theo API. **Điều đó không tự dừng** task nguồn, HTTP call hoặc DB query. Cũng cần lưu ý `orTimeout` tác động lên **chính future được gọi** (ở đây là aggregate), không phải tự tạo một tầng cancellation riêng bao trùm mọi nguồn.

Tương tự, `completeOnTimeout(defaultValue, ...)` có thể trả giá trị dự phòng khi đến hạn nhưng không tự giải phóng các task nền. Không đặt timeout ở ba lớp khác nhau và mặc định kết quả chỉ tiêu tốn đúng một khoảng timeout: thời gian xếp hàng executor, đợi connection, HTTP connect, response và retry có thể cộng dồn.

**Thiết kế deadline hợp lý:**

- Deadline của toàn request (ví dụ 1 giây).
- Timeout/giới hạn acquire của pool và client.
- Timeout của từng cuộc gọi remote.
- Điều kiện dừng cho các nhánh thua.
- Hạn mức concurrency và số lần retry.
- Quy trình trả connection/permit/transaction khi task lỗi hoặc bị hủy.

Nếu một nhánh không hỗ trợ hủy, **đừng quảng bá cancellation như một bảo đảm**; có thể chọn trả kết quả sớm rồi theo dõi nhánh ấy để cleanup khi thực sự xong.

## 10. Ví dụ tích hợp Spring @Async: đúng executor, đúng proxy, đúng giới hạn

Trong Spring Boot/Spring Framework, `@Async` có thể trả `CompletableFuture<T>`. Spring proxy submit phần thân phương thức tới executor; caller kết hợp các future bằng `anyOf`.

Đoạn cấu hình sau là **code tích hợp Spring**, không phải chương trình `javac` chỉ có JDK:

**`AsyncConfig.java`:**

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    @Bean("lookupExecutor")
    public ThreadPoolTaskExecutor lookupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("lookup-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

**`LookupService.java`:**

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LookupService {
    @Async("lookupExecutor")
    public CompletableFuture<String> lookupPrimary(String key) {
        // Ví dụ rút gọn: thay bằng I/O thật với timeout/cancellation.
        return CompletableFuture.completedFuture("primary:" + key);
    }

    @Async("lookupExecutor")
    public CompletableFuture<String> lookupReplica(String key) {
        return CompletableFuture.completedFuture("replica:" + key);
    }
}
```

**`LookupCoordinator.java`:**

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class LookupCoordinator {
    private final LookupService lookupService;

    public LookupCoordinator(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    public CompletableFuture<Object> fastest(String key) {
        CompletableFuture<String> first =
                lookupService.lookupPrimary(key);
        CompletableFuture<String> second =
                lookupService.lookupReplica(key);

        // Trả completion đầu tiên; KHÔNG tự hủy lời gọi còn lại.
        return CompletableFuture.anyOf(first, second);
    }
}
```

Những class này minh họa đường đi:

```text
Controller / caller
       |
       v
LookupCoordinator (Spring bean)
       |
       +-- gọi LookupService proxy.lookupPrimary() --> lookupExecutor task #1
       |
       +-- gọi LookupService proxy.lookupReplica() --> lookupExecutor task #2
       |
       +-- CompletableFuture.anyOf(task #1, task #2)
                           |
                           v
                    kết quả/lỗi đầu tiên
                           |
                           +--> tác vụ còn lại vẫn có thể chạy
```

**Lưu ý tích hợp:**

- `@EnableAsync` phải được bật; phương thức `@Async` phải được gọi qua **Spring-managed proxy**. `this.lookupPrimary(...)` bên trong cùng bean ở chế độ proxy mặc định có thể **bypass** interceptor và chạy ngay trên thread hiện tại.
- `@Async("lookupExecutor")` chọn đúng executor. Nếu không chỉ rõ tên, việc chọn executor tùy cấu hình Framework/Boot và bean thực tế.
- Hai method trả kết quả ngay trong ví dụ, vì vậy không dùng code này để khẳng định nhánh nào chắc chắn thắng hoặc kiểm tra tải thực. Trong hệ thống thật, thứ tự hoàn thành phụ thuộc scheduler, connection pool và remote latency.
- `ThreadPoolTaskExecutor` dùng core/max/queue và rejection policy riêng. Nếu pool/queue đầy, việc submit một nhánh có thể ném `TaskRejectedException` **trực tiếp ở caller**, trước khi phương thức coordinator trả aggregate; phải xử lý nếu hệ thống cho phép quá tải.
- Thay `CompletableFuture.completedFuture(...)` trong target bằng HTTP/JDBC thực cần timeout và chính sách cancel của dependency. `@Async` không biến tác vụ ngoại vi thành có thể hủy chắc chắn.
- `@Transactional` gắn với ranh giới proxy/transaction tại thread thực thi; transaction của caller không tự được chuyển sang worker khi thực sự dùng `@Async`.
- Một executor bean trong mỗi JVM chỉ giới hạn tải **cục bộ**, không đặt quota toàn cluster nếu có nhiều pod.

### Cẩn thận khi trả kết quả sớm khỏi controller

Nếu REST endpoint nhận future từ `fastest()` và trả response theo nhánh đầu tiên, các task thua có thể tiếp tục chạy sau khi response đã gửi. Hãy đo active tasks, thời gian phản hồi và chi phí downstream. Nếu cần “không tiếp tục làm việc sau khi client ngắt kết nối”, cần gắn cancellation từ web layer xuống HTTP client/executor bằng cơ chế hỗ trợ thực tế, không chỉ nhìn `anyOf`.

## 11. Kiểm soát chi phí và tài nguyên khi chạy đua nhiều tác vụ

Một lời gọi `anyOf` có thể tăng độ trễ được cảm nhận của **một request**, nhưng cũng tăng tải cho các nguồn.

Ví dụ một request đồng thời gọi ba replica, mỗi replica tốn một connection và 1 đơn vị quota:

```text
Một request:
   1 aggregate future
   3 remote calls đã nộp
   1 kết quả được dùng
   tối đa 2 calls thua có thể vẫn đang chạy

100 requests đồng thời:
   tối đa 300 remote calls được nộp,
   không phải chỉ 100, trừ khi admission control giới hạn.
```

Không đồng nhất “số aggregate đang chờ” với “số task/HTTP call thực tế”. Cần xét:

| Rủi ro | Kiểm soát phù hợp |
| --- | --- |
| Tạo nhiều bản sao cho mỗi request | Chỉ hedge khi cần; dùng ngưỡng độ trễ trước khi khởi tạo replica phụ |
| Bão hòa executor | Bounded `ThreadPoolTaskExecutor`, hàng đợi hữu hạn, rejection handling |
| Cạn JDBC/HTTP connection pool | Giới hạn connection/active calls phù hợp, deadline ở client |
| API đối tác có rate/concurrency quota | Rate limiter và bulkhead riêng theo dependency |
| Task thua tiếp tục gây side effect | Chỉ dùng cho đọc/idempotent, cancellation/compensation và idempotency key |
| Caller rời đi mà work còn chạy | Theo dõi lifecycle, cancellation propagation, cleanup trong `finally` |
| Retry storm khi một nhánh lỗi | Backoff/jitter, retry budget và circuit breaker |
| Nhiều replica cùng chậm | Deadline tổng; không tạo thêm replica vô hạn khi đã quá tải |

**Công thức tư duy:** nếu mỗi request có tối đa `k` nhánh, tải *submission* tối đa có thể gần `k × số request`. Mức in-flight thực tế còn phụ thuộc tốc độ hoàn thành, queue, client pool, giới hạn executor và thời điểm hedge. Cần đo thực tế, không giả định mọi nhánh thua được cắt bỏ khi `anyOf` hoàn tất.

## 12. Kiểm thử đúng cả kết quả lẫn vòng đời công việc

Một test “`assertEquals("A", anyOf.join())`” **chưa** chứng minh nhánh B được dừng. Hãy kiểm tra ở hai nhóm:

**Nhóm kết quả:**

1. Một nhánh thành công trước: aggregate trả đúng giá trị, có thể có kiểu `Object` cần chuẩn hóa.
2. Một nhánh lỗi trước: aggregate hoàn tất lỗi, không tự chờ nhánh sau thành công.
3. Một nhánh bị cancel trước: aggregate hoàn tất theo trạng thái ngoại lệ/cancellation của nhánh đó.
4. Các nhánh xong gần đồng thời: không phụ thuộc kết quả cố định nếu không kiểm soát thứ tự.
5. Không có input: aggregate chưa hoàn tất; không vô ý gọi `join()` gây chờ vô hạn.

**Nhóm vòng đời:**

1. Khi aggregate vừa xong, một supplier chậm đã thật sự bắt đầu chưa? Dùng `CountDownLatch`.
2. Supplier chậm có còn giữ worker/thread/connection không?
3. Sau khi gọi `CompletableFuture.cancel(true)`, supplier có tiếp tục chạy không? Ví dụ mục 4 chứng minh có thể tiếp tục.
4. Khi áp dụng cờ hợp tác hoặc client cancel, task có quan sát tín hiệu, đóng tài nguyên và ngừng side effect mới không?
5. Có exception hoặc task bị bỏ quên từ nhánh thua không? Có metric/log kết quả cho những task ấy không?
6. Với cancellation trong lúc đang ghi DB/remote, trạng thái nghiệp vụ được giữ nhất quán và retry idempotent chưa?
7. Khi executor shutdown, mọi task/connection có được cleanup đúng thời hạn không?

**Kỹ thuật test:** dùng latch hoặc barrier để tạo thứ tự xác định; không dùng duy nhất `Thread.sleep(100)` và kết luận chắc chắn “A luôn nhanh hơn B”. Dùng timeout cho `await`, `Future.get(timeout)` và shutdown để tránh bài test treo. Phân biệt exception **sinh ra trong task** với `TaskRejectedException` **khi submit**.

### Trực quan hóa các trạng thái khi debug

```text
anyOf result         : DONE (QUICK)
slow CompletableFuture: CANCELLED
slow supplier/worker  : STILL RUNNING
remote side effect    : POSSIBLY ALREADY COMMITTED

Các dòng này CÓ THỂ cùng đúng tại một thời điểm.
Không dùng isCancelled() để kết luận "worker đã dừng".
```

## 13. Những hiểu lầm thường gặp

### “anyOf trả lời rồi nên các task còn lại tự động bị hủy”

**Sai.** `anyOf` chỉ hoàn tất future tổng hợp; nguồn chưa thắng vẫn có thể chạy và hoàn thành. Nếu muốn dừng, phải chủ động dùng cơ chế riêng của task/client.

### “Gọi cancel(true) là chắc chắn ngắt thread đang chạy supplyAsync”

**Sai đối với `CompletableFuture.cancel`.** Java 17 Javadoc nêu tham số `mayInterruptIfRunning` không dùng để điều khiển xử lý trong implementation này. Task supplier vẫn có thể chạy tiếp, như ví dụ có latch.

### “isDone=true nghĩa là đã thực hiện xong công việc”

**Không nhất thiết.** Future bị hủy/đặt timeout có thể `isDone=true` trong khi underlying computation chưa dừng. Cần metric/task handle hoặc xác nhận cleanup thật.

### “anyOf chỉ hoàn tất khi có thành công đầu tiên”

**Sai.** Completion ngoại lệ hoặc cancellation cũng có thể kết thúc `anyOf` trước khi nhánh khác trả thành công. Nếu cần “first successful”, hãy xây coordinator có chính sách bỏ qua lỗi rõ ràng.

### “anyOf không đối số sẽ trả null ngay”

**Sai.** Nó trả future **chưa hoàn tất**. Ngược lại, `allOf()` không đối số tạo future hoàn tất với `null`; hai API không đối xứng ở trường hợp rỗng.

### “Đã gọi future.cancel thì request thanh toán đã gửi sẽ rollback”

**Sai.** Giao dịch/HTTP side effect đã tới hệ thống khác không được đảo ngược bằng việc đổi trạng thái future. Cần idempotency, transaction, trạng thái nghiệp vụ hoặc compensation có thiết kế.

### “Càng nhiều nhánh race thì càng tốt”

Không nhất thiết. Nhiều bản sao tăng tổng CPU, socket, thread, quota và có thể tự làm downstream chậm hơn. Cần bounded concurrency, cân đối tail latency với tài nguyên.

### “@Async luôn bảo đảm tác vụ được chạy song song”

Không hoàn toàn. Cần gọi qua Spring proxy, cấu hình executor có capacity, xử lý rejection và kiểm tra thứ tự scheduling. Cùng một executor chỉ có một worker vẫn xử lý tác vụ nối tiếp dù các phương thức gắn `@Async`.

### “Đặt orTimeout là đủ để hoàn tất cleanup”

Không. Timeout thay trạng thái future, không tự xác nhận supplier/HTTP request đã ngừng. Timeout của client, cancellation propagation và resource release là những vấn đề riêng.

## 14. Checklist thiết kế và tài liệu đối chiếu

Trước khi dùng `anyOf` cho một feature Spring:

1. **Mục đích là fastest-completion hay first-success?** Nếu muốn first-success, tự định nghĩa xử lý từng lỗi.
2. **Mọi nhánh có thể chạy đồng thời không?** Xác minh executor, queue và HTTP connection pool.
3. **Mỗi nhánh có side effect không?** Tránh hedge tác vụ ghi không idempotent.
4. **Ai sở hữu cancellation?** `CompletableFuture`, executor `Future`, HTTP client, reactive subscription, DB statement hay workflow controller?
5. **Hủy có dừng underlying work thật không?** Không chỉ nhìn `isCancelled()`; kiểm thử.
6. **Khi nào resource được giải phóng?** Dùng `finally`, timeout, đóng response/statement, đóng/giải phóng permit.
7. **Đường lỗi đầu tiên có phù hợp nghiệp vụ không?** `anyOf` không bỏ qua exception của nhánh thua/chưa thắng.
8. **Có số lượng nhánh tối đa và quota không?** Kiểm soát chi phí nhân bản tác vụ.
9. **Có deadline tổng và chính sách retry đúng không?** Tránh chờ vô hạn và retry storm.
10. **Có metric/log riêng cho nhánh thua không?** Đo completed/cancelled/actually-stopped, active tasks, remote calls và side effects.

**Tài liệu chính thức:**

- [Java SE 17 – CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `anyOf`, `allOf`, `cancel`, `orTimeout`, `join`, `whenComplete`.
- [Java SE 17 – Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): nghĩa của `cancel(boolean)` ở interface và nghĩa khác nhau theo implementation.
- [Java SE 17 – ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, `Future` và shutdown.
- [Java SE 17 – CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): test có điểm đồng bộ.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`, `TaskExecutor`, lời gọi qua proxy và task scheduling.
- [Spring Framework – Asynchronous Return Types](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async-return-types): trả `CompletableFuture` trong phương thức `@Async`.

**Ghi nhớ cuối cùng:** `anyOf` là **bộ chọn một trạng thái hoàn tất**, không phải **bộ quản lý vòng đời các công việc còn lại**. Nếu muốn tiết kiệm tài nguyên hoặc dừng nhánh thua, cần có cancellation handle/cooperative stop/client timeout đúng tầng, và phải kiểm tra việc thực thi thật đã dừng — không chỉ rằng một future có `isCancelled() == true`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-005">thenAccept() và thenRun() cho bước không có kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# thenAccept() và thenRun() cho bước không có kết quả

## 1. Bối cảnh và mục tiêu

Khi kết hợp các tác vụ với `CompletableFuture`, không phải bước xử lý nào cũng sinh ra kết quả mới. Có lúc cần đọc kết quả của bước trước để ghi audit, in log hoặc gửi thông báo; có lúc chỉ cần làm một hành động sau khi công việc trước hoàn thành. Hai phương thức dành cho những bước như vậy là **`thenAccept()` và `thenRun()`**.

- `thenAccept(Consumer<? super T>)`: **nhận giá trị T** từ giai đoạn trước; callback thực hiện hành động có kiểu trả về `void`.
- `thenRun(Runnable)`: **không nhận kết quả T** của giai đoạn trước; callback chỉ chạy sau khi giai đoạn trước hoàn thành bình thường.
- Cả hai trả về `CompletableFuture<Void>` nếu gọi trên `CompletableFuture<T>`. Stage mới vẫn là một future để **theo dõi hành động đã xong hay bị lỗi**.

Sau bài học, bạn có thể giải thích đúng `Void` trong pipeline; chọn callback theo nhu cầu dữ liệu; kiểm soát thứ tự giữa các bước; hiểu failure/cancellation và non-Async/Async variants; sử dụng executor do Spring quản lý khi nối vào kết quả `@Async`; và viết test không dựa vào timing tình cờ.

**Trọng tâm:** những bước **không tạo kết quả mới** trong chuỗi `CompletableFuture`. Việc chọn `thenAccept` hoặc `thenRun` không tự bảo đảm side effect được ghi bền vững, không tạo transaction, không khắc phục race condition hay giải phóng request thread nếu caller vẫn `join()` ngay.

## 2. Cơ chế CompletionStage và kiểu Void

Một `CompletableFuture<T>` có thể:

1. Hoàn thành bình thường với kết quả `T`.
2. Hoàn thành bất thường với exception/cancellation.
3. Chưa hoàn thành; callbacks đang chờ điều kiện kích hoạt.

Khi đăng ký continuation, API tạo **dependent stage**. Giai đoạn phụ thuộc chỉ hoàn thành bình thường khi giai đoạn trước thành công và callback chạy xong không ném lỗi. Callback bị bỏ qua nếu tiền nhiệm đã hoàn thành bất thường.

```text
CompletableFuture<T> source
       |
       +------ success(T) ------+
       |                        |
       |                thenAccept(T -> action)
       |                    returns
       |                CompletableFuture<Void>
       |                        |
       |                thenRun(() -> signal)
       |                    returns
       |                CompletableFuture<Void>
       |
       +------ exceptional completion
                                |
                        callback normal-only bị bỏ qua
                        dependent stage kết thúc bất thường
```

Trong Java, `void` là từ khóa cho method không có giá trị trả về. `java.lang.Void` là một kiểu tham chiếu có thể dùng làm generic type argument. Với `CompletableFuture<Void>`, kết quả khi hoàn tất **bình thường** là `null`. Nó không có nghĩa rằng future bị bỏ qua hoặc không thể thất bại.

| API | Tham số callback | Nhận giá trị của source? | Tạo giá trị mới? | Kiểu kết quả stage mới |
| --- | --- | --- | --- | --- |
| `thenApply(fn)` | `Function<T, U>` | Có | Có, `U` | `CompletableFuture<U>` |
| `thenAccept(action)` | `Consumer<T>` | Có | Không | `CompletableFuture<Void>` |
| `thenRun(action)` | `Runnable` | Không | Không | `CompletableFuture<Void>` |
| `thenCompose(fn)` | `Function<T, CompletionStage<U>>` | Có | Ghép với future khác | `CompletableFuture<U>` |
| `whenComplete(action)` | `BiConsumer<T, Throwable>` | Giá trị và lỗi | Không nhằm tạo giá trị mới | Stage có cùng kiểu `T` |
| `handle(fn)` | `BiFunction<T, Throwable, U>` | Giá trị và lỗi | Có, `U` | `CompletableFuture<U>` |

Đây là các kiểu khi method được gọi trên `CompletableFuture`; interface `CompletionStage` khai báo tương ứng với kiểu kết quả là `CompletionStage`.

**Cách nhớ:** *apply* tạo giá trị, *accept* tiêu thụ giá trị, *run* không cần giá trị. Khi một operation phụ phải trả một future khác, *compose* mới là cách nối future đó vào luồng kết quả.

## 3. thenAccept(): nhận kết quả và thực hiện hành động

Chữ ký:

```java
CompletableFuture<Void> thenAccept(Consumer<? super T> action)
```

Ví dụ một future truy xuất kết quả đơn hàng:

```java
CompletableFuture<OrderSummary> source = loadOrder(orderId);
CompletableFuture<Void> audited =
        source.thenAccept(order -> auditService.record(order.id()));
```

Đây là **fragment trong project**: `OrderSummary`, `loadOrder` và `auditService` phải có trong ứng dụng. Callback nhận `OrderSummary` nên có thể truy cập ID, giá trị, trạng thái... Khi `auditService.record` trả về bình thường, `audited` mới được đánh dấu hoàn thành. Nếu audit ném lỗi, `audited` hoàn thành bất thường nhưng source có thể vẫn thành công.

Không hiểu `thenAccept` như một toán tử biến đổi dữ liệu. Callback có hợp đồng `Consumer.accept(T)` trả `void`, nên không thể trả một giá trị kiểu `String`/`OrderId` để làm kết quả của giai đoạn tiếp theo.

**Ví dụ cố ý sai kiểu, không biên dịch:**

```java
source.thenAccept(order -> {
    return order.id();
});
```

Nếu cần ID để truyền tiếp, dùng `thenApply(order -> order.id())`. Nếu chỉ cần ghi log ID, dùng `thenAccept(order -> System.out.println(order.id()))`.

### 3.1. Ví dụ Java 17+ hoàn chỉnh

Tệp `ThenAcceptThenRunDemo.java`:

```java
import java.util.concurrent.CompletableFuture;

public class ThenAcceptThenRunDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> source =
                CompletableFuture.completedFuture(21);

        CompletableFuture<Void> accepted =
                source.thenAccept(value ->
                        System.out.println("received=" + value));

        CompletableFuture<Void> finished =
                accepted.thenRun(() ->
                        System.out.println("audit-done"));

        finished.join();

        System.out.println("source=" + source.join());
        System.out.println("terminal=" + finished.join());
    }
}
```

Chạy bằng Java 17+:

```bash
javac ThenAcceptThenRunDemo.java
java ThenAcceptThenRunDemo
```

Kết quả:

```text
received=21
audit-done
source=21
terminal=null
```

Diễn giải:

1. Source đã có giá trị 21.
2. `thenAccept` nhận giá trị này và in dòng đầu.
3. `thenRun` được nối trên **`accepted`**, không phải đăng ký như nhánh độc lập của source, nên chỉ chạy sau khi `thenAccept` hoàn tất bình thường.
4. `join()` chờ future cuối; kết quả `null` là bình thường đối với `Void`.
5. `source.join()` vẫn trả 21: callback không lấy đi hay xóa kết quả của source.

Ví dụ này không tạo executor; mục đích là chứng minh **luồng dữ liệu và dependency**, không phải đo tốc độ chạy đồng thời.

### 3.2. Cách viết method reference

```java
CompletableFuture<String> source =
        CompletableFuture.completedFuture("ORDER-7");

CompletableFuture<Void> printed =
        source.thenAccept(System.out::println);
printed.join();
```

Đây là fragment Java 17+ và in `ORDER-7`. Method reference `System.out::println` ở đây đóng vai trò một `Consumer<String>`; kết quả source được truyền tới `println`. Nếu dùng `thenRun(() -> System.out.println("done"))` thì chỉ in thông điệp cố định, không được truyền `ORDER-7`.

## 4. thenRun(): chỉ cần điều kiện hoàn thành, không nhận T

Chữ ký:

```java
CompletableFuture<Void> thenRun(Runnable action)
```

`thenRun` nhận `Runnable` không có tham số. Hành động chỉ chạy sau khi stage trước hoàn thành **bình thường**. Dù source có kết quả `String`, `Integer` hay `OrderSummary`, `Runnable` không nhận giá trị đó qua API.

Tình huống đúng:

```java
CompletableFuture<Invoice> source = invoiceService.load(invoiceId);
CompletableFuture<Void> signaled =
        source.thenRun(() -> completionSignal.countDown());
```

Đây là fragment cần biến và service tương ứng. Nếu chỉ cần phát tín hiệu khi invoice được tải xong thành công, không phải đọc `Invoice`, `thenRun` phù hợp.

Nếu hành động **phải dùng invoice được tải từ source**, `thenAccept(invoice -> ...)` thường rõ ràng hơn. Không lưu invoice vào một field singleton chỉ để callback `thenRun` có dữ liệu, vì cách đó tạo shared mutable state dễ race.

### 4.1. thenRun vẫn có thể capture biến ngoài

```java
String requestId = "REQ-9";
CompletableFuture<Void> done =
        source.thenRun(() -> System.out.println(requestId));
```

Đây là **variable capture** của biến local effectively final, không phải source chuyển kết quả cho `thenRun`. Nếu biến capture trỏ tới object mutable, object đó vẫn có thể được chia sẻ giữa nhiều thread.

### 4.2. Ví dụ Java 17+: hành động không đọc giá trị

Tệp `ThenRunSignalDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ThenRunSignalDemo {
    public static void main(String[] args) {
        AtomicInteger signals = new AtomicInteger();

        CompletableFuture<String> source =
                CompletableFuture.completedFuture("READY");
        CompletableFuture<Void> signaled =
                source.thenRun(() -> signals.incrementAndGet());

        signaled.join();

        System.out.println("signals=" + signals.get());
        System.out.println("source=" + source.join());
        System.out.println("result=" + signaled.join());
    }
}
```

```bash
javac ThenRunSignalDemo.java
java ThenRunSignalDemo
```

Kết quả:

```text
signals=1
source=READY
result=null
```

`thenRun` chỉ quan tâm source thành công, không nhận chuỗi `READY`. Dùng `AtomicInteger` giúp biến đếm có thao tác cập nhật thread-safe khi ví dụ được chuyển sang callback Async. Trong ví dụ đơn giản không có cạnh tranh đồng thời.

## 5. Hai nhánh độc lập không đồng nghĩa với chuỗi tuần tự

Một nhầm lẫn quan trọng:

```java
source.thenAccept(order -> audit(order));
source.thenRun(() -> sendSignal());
```

Đây là hai **dependent stages riêng**, cùng gắn với `source`. Chúng chỉ phụ thuộc vào source, **không phụ thuộc lẫn nhau**. Vì thế không có bảo đảm `audit` chạy xong trước `sendSignal`. Nếu source đã complete sẵn, bạn có thể thấy hai callback chạy theo thứ tự đăng ký trong một lần test; đó không phải hợp đồng cho mọi tình huống.

Nếu cần `sendSignal` sau audit thành công, phải nối:

```java
CompletableFuture<Void> workflow =
        source.thenAccept(order -> audit(order))
              .thenRun(() -> sendSignal());

workflow.join();
```

Nếu audit lỗi, stage sau audit hoàn thành bất thường và `sendSignal` bị bỏ qua. Đó là hành vi mong muốn khi signal chỉ được phát sau audit thành công.

### 5.1. Khi hai nhánh thật sự độc lập

Tệp `TwoBranchesDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class TwoBranchesDemo {
    public static void main(String[] args) {
        AtomicInteger actions = new AtomicInteger();
        CompletableFuture<String> source =
                CompletableFuture.completedFuture("INVOICE-1");

        CompletableFuture<Void> audit =
                source.thenAccept(value -> actions.incrementAndGet());
        CompletableFuture<Void> notify =
                source.thenRun(() -> actions.incrementAndGet());

        CompletableFuture.allOf(audit, notify).join();

        System.out.println("actions=" + actions.get());
        System.out.println("source=" + source.join());
    }
}
```

```bash
javac TwoBranchesDemo.java
java TwoBranchesDemo
```

Kết quả:

```text
actions=2
source=INVOICE-1
```

`CompletableFuture.allOf(...)` tạo một stage `Void` để chờ cả hai nhánh. Ví dụ **không** in thứ tự từng callback vì giữa hai nhánh không có thứ tự được bảo đảm. Khi các nhánh chạy đồng thời, dùng một counter thread-safe như `AtomicInteger` tốt hơn cập nhật `int` chung không có đồng bộ.

### 5.2. Vì sao không được bỏ tham chiếu đến future của callback?

**Phản mẫu:**

```java
source.thenAccept(this::sendEmail);
return source;
```

Nếu gửi email lỗi, lỗi nằm trên **dependent stage** do `thenAccept` tạo ra; caller chỉ giữ `source` có thể chỉ biết source thành công. Đối với side effect bắt buộc:

```java
return source.thenAccept(this::sendEmail);
```

Câu trên là fragment phù hợp với phương thức trả `CompletableFuture<Void>`. Quan trọng là caller/service phải thực sự quan sát future đó; nếu chỉ tạo rồi bỏ, bạn không có hợp đồng kết quả đáng tin cậy.

## 6. Non-Async và Async: callback chạy trên thread nào?

**Không có hậu tố Async không có nghĩa luôn chạy trong main/caller thread.** Với `CompletableFuture`, hành động `thenAccept`/`thenRun` có thể chạy bởi thread vừa hoàn thành stage trước hoặc bởi một thread tham gia quá trình completion. Nếu stage đã complete trước khi callback được đăng ký, action có thể chạy ngay trong lúc đăng ký. API không cung cấp một thread identity cố định.

Biến thể có hậu tố:

| API | Nhận T? | Cơ chế chạy |
| --- | --- | --- |
| `thenAccept(action)` | Có | Không chỉ rõ executor |
| `thenAcceptAsync(action)` | Có | Default asynchronous execution facility |
| `thenAcceptAsync(action, executor)` | Có | Executor được truyền |
| `thenRun(action)` | Không | Không chỉ rõ executor |
| `thenRunAsync(action)` | Không | Default asynchronous execution facility |
| `thenRunAsync(action, executor)` | Không | Executor được truyền |

Với `CompletableFuture` tiêu chuẩn Java, Async variants **không truyền executor** thường dựa trên `ForkJoinPool.commonPool()`; có điều kiện fallback nếu common pool không đủ mức parallelism. Chúng **không mặc nhiên sử dụng** pool của Spring `@Async`.

Đặc biệt trong Spring, nếu callback gửi HTTP blocking, truy vấn DB hoặc ghi log rất chậm, hãy dùng executor có giới hạn thích hợp thay vì mặc nhiên chiếm thread hoàn tất source hoặc common pool.

### 6.1. Ví dụ Java 17+: chỉ định executor

Tệp `NamedExecutorDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NamedExecutorDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor =
                Executors.newSingleThreadExecutor(
                        task -> new Thread(task, "audit-worker"));

        try {
            CompletableFuture<String> source = new CompletableFuture<>();

            CompletableFuture<Void> done =
                    source.thenAcceptAsync(value -> {
                        System.out.println("value=" + value);
                        System.out.println("accept-thread="
                                + Thread.currentThread().getName());
                    }, executor).thenRunAsync(() ->
                        System.out.println("run-thread="
                                + Thread.currentThread().getName()),
                        executor);

            source.complete("A-17");
            done.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac NamedExecutorDemo.java
java NamedExecutorDemo
```

Kết quả:

```text
value=A-17
accept-thread=audit-worker
run-thread=audit-worker
```

Giải thích: source hoàn thành bằng `complete("A-17")`, callback đầu được nộp vào executor có thread name xác định; callback sau chỉ được nộp khi dependent stage của callback đầu đã thành công. `get(5, TimeUnit.SECONDS)` đặt giới hạn chờ cho caller. `shutdown()` thuộc trách nhiệm của code tạo executor; trong Spring, executor nên là bean được container quản lý thay vì tạo một pool mới trong từng request.

**Không nên suy ra mọi overload `thenAcceptAsync` chắc chắn tạo một platform thread mới:** nếu cung cấp một `Executor` thực thi inline hoặc virtual-thread strategy, nơi chạy phụ thuộc vào executor đó. Điều API cung cấp là **cơ chế gửi tác vụ tới execution facility**.

### 6.2. Non-Async callback có thể làm worker nguồn chậm thêm

Nếu một worker hoàn thành `loadFromDb` rồi tự chạy `thenAccept` có blocking I/O vài giây, thread này có thể bị giữ lâu hơn dự tính. Tách `thenAcceptAsync(..., auditExecutor)` giúp quyết định tài nguyên cho callback nhưng không biến một dịch vụ chậm thành nhanh hơn năng lực thực. Vẫn phải có timeout, queue capacity, rate limit, backpressure và shutdown policy.

## 7. Exceptional completion: khi callback bị bỏ qua

Cả `thenAccept` lẫn `thenRun` chỉ chạy khi **tiền nhiệm hoàn thành bình thường**. Nếu stage trước thất bại/cancel, callback bị bỏ qua và dependent stage hoàn thành bất thường.

Nếu callback tự ném exception, dependent stage thất bại, trong khi original stage có thể vẫn thành công. Vì vậy một workflow có thể gồm **source success + audit failure**: đó không phải một mâu thuẫn, mà là hai stages khác nhau.

### 7.1. Ví dụ Java 17+: lỗi nguồn và phục hồi

Tệp `RecoveryDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecoveryDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> source = new CompletableFuture<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        CompletableFuture<Void> skipped =
                source.thenAccept(value -> invoked.set(true))
                      .thenRun(() -> System.out.println("unexpected"));

        source.completeExceptionally(
                new IllegalStateException("service unavailable"));

        try {
            skipped.join();
        } catch (CompletionException ex) {
            System.out.println("cause="
                    + ex.getCause().getClass().getSimpleName());
        }

        System.out.println("accept-called=" + invoked.get());

        CompletableFuture<Void> recovered =
                source.exceptionally(error -> 0)
                      .thenAccept(value ->
                              System.out.println("fallback=" + value));
        recovered.join();
    }
}
```

```bash
javac RecoveryDemo.java
java RecoveryDemo
```

Kết quả:

```text
cause=IllegalStateException
accept-called=false
fallback=0
```

Nhánh đầu bỏ qua cả hai callbacks do source lỗi. `exceptionally(error -> 0)` tạo stage mới thành công với fallback 0, nhờ đó `thenAccept` ở nhánh thứ hai được chạy. Giá trị fallback phải được chọn dựa trên nghiệp vụ; không nên thay lỗi thanh toán bằng 0 hoặc coi lỗi là thành công nếu hợp đồng không cho phép.

### 7.2. Ví dụ Java 17+: lỗi ở callback không thay đổi source

Tệp `CallbackFailureDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class CallbackFailureDemo {
    public static void main(String[] args) {
        CompletableFuture<String> source =
                CompletableFuture.completedFuture("ORDER-9");

        CompletableFuture<Void> audited =
                source.thenAccept(value -> {
                    throw new IllegalArgumentException("audit failed");
                });

        try {
            audited.join();
        } catch (CompletionException ex) {
            System.out.println("audit-error="
                    + ex.getCause().getClass().getSimpleName());
        }

        System.out.println("source=" + source.join());
    }
}
```

```bash
javac CallbackFailureDemo.java
java CallbackFailureDemo
```

Kết quả:

```text
audit-error=IllegalArgumentException
source=ORDER-9
```

`join()` thường ném `CompletionException` nếu stage hoàn thành bất thường; `get()` dùng checked exception `ExecutionException` để bọc lỗi. Không chỉ catch exception quanh **đoạn đăng ký callback**, vì với async continuation, lỗi có thể phát sinh về sau và nằm trong dependent future.

### 7.3. Phân biệt với whenComplete và handle

- `whenComplete((value, error) -> ...)` chạy khi stage trước **thành công hoặc lỗi**, thích hợp để ghi nhận cả hai đường; nó có quy tắc giữ outcome gốc và xử lý exception của chính callback.
- `handle((value, error) -> result)` cũng chạy khi có success/error, nhưng trả **một kết quả U mới** nên dùng được cho recovery/chuyển đổi.
- `exceptionally(error -> fallback)` phục hồi lỗi bằng fallback phù hợp.
- `thenAccept` và `thenRun` không chạy trên exceptional completion; **không dùng chúng như finally/cleanup cho mọi outcome**.

Ví dụ fragment:

```java
CompletableFuture<Void> done =
        source.whenComplete((value, error) -> {
            if (error != null) {
                reportFailure(error);
            }
        }).thenRun(() -> markSucceeded());
```

`markSucceeded` ở cuối vẫn chỉ chạy sau normal completion. `whenComplete` giúp quan sát lỗi, **không tự chữa** lỗi đó. Nếu bạn muốn cleanup luôn thực hiện, dùng một cleanup callback/primitive có semantics đúng và kiểm soát exception của cleanup.

## 8. Kết hợp với Spring @Async

Một method `@Async` của Spring có thể trả `CompletableFuture<T>` khi được gọi **qua Spring-managed proxy** và async processing đã bật (ví dụ `@EnableAsync`). Caller có thể gắn `thenAccept`/`thenRun` vào future nhận được.

**Snippet Spring tích hợp (không biên dịch đơn lẻ bằng javac):**

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

record InvoiceSummary(String invoiceId, int lineCount) {}

@Service
public class InvoiceQueryService {
    @Async("queryExecutor")
    public CompletableFuture<InvoiceSummary> load(String id) {
        return CompletableFuture.completedFuture(
                new InvoiceSummary(id, 3));
    }
}
```

Trong một bean khác, inject `InvoiceQueryService`, `AuditService` và executor:

```java
public CompletableFuture<Void> auditInvoice(String id) {
    return queryService.load(id)
            .thenAcceptAsync(summary ->
                    auditService.record(
                            summary.invoiceId(),
                            summary.lineCount()),
                    auditExecutor)
            .thenRun(() -> auditService.markCompleted(id));
}
```

Fragment trên giả định `queryService`, `auditService`, `auditExecutor` là dependency và đã được khai báo/inject. Stage cuối biểu diễn chuỗi: lấy summary thành công → audit thành công → đánh dấu hoàn tất.

**Chú ý:** `thenRun` ở cuối là non-Async, nên callback `markCompleted` có thể chạy trên thread hoàn thành audit. Nếu nó cũng là I/O blocking, nên cân nhắc `thenRunAsync(..., auditExecutor)` hoặc một executor khác thích hợp. `thenAcceptAsync` không truyền executor sẽ thường dùng default Java async facility, **không mặc nhiên là `queryExecutor` của Spring**.

### 8.1. Thread boundary và transaction

`@Async` có thể đã đổi thread, sau đó `thenAcceptAsync` có thể đổi sang executor khác. Spring transaction context, request-scoped object, MDC/trace context và security context gắn thread **không tự động đi theo** chuỗi future. Nếu audit cần transaction, mở/định nghĩa transaction tại boundary chạy audit thông qua cơ chế Spring phù hợp; ưu tiên truyền immutable ID/DTO thay vì managed JPA entity và `EntityManager` từ caller.

Nếu callback gọi API có side effect cần hoàn tất ngay trong transaction của request, phải xem lại thiết kế; đừng coi continuation bất đồng bộ là thành phần tự động của transaction ở caller.

### 8.2. Pool và quá tải

`thenAcceptAsync(action, executor)` có thể thất bại nếu executor đang shutdown hoặc từ chối task do quá tải. Khi side effect quan trọng, phải quan sát dependent stage, đặt admission control/queue hữu hạn và phản hồi lỗi theo SLA; không bỏ future rồi hy vọng callback chắc chắn chạy.

Spring `ThreadPoolTaskExecutor` cung cấp cách quản lý pool qua bean. Đừng tạo `Executors.newFixedThreadPool(...)` bên trong mỗi request để chạy callback; resource đó không tự do Spring đóng nếu chỉ là local object.

### 8.3. API trả kết quả và side effect có bắt buộc không?

Nếu caller cần phản hồi chỉ sau khi audit thành công, trả/await future **cuối** thay vì source. Nếu gửi email có thể hoàn tất sau HTTP response nhưng không được mất khi restart, lưu công việc bằng **durable queue/outbox/job store**, trả job ID/trạng thái theo hợp đồng; continuation trong RAM không thay thế durability.

## 9. Giữ giá trị nguồn nếu pipeline còn cần T

Sau `thenAccept`, stage mới là `Void`. Nếu cần tiếp tục xử lý `T`, đừng cố truyền giá trị từ `thenRun`:

### 9.1. Giữ T bằng thenApply

```java
CompletableFuture<Order> withAudit =
        source.thenApply(order -> {
            audit(order);
            return order;
        });
```

Fragment này giữ kiểu `Order` của stage mới và biến audit thành điều kiện thành công. Nếu audit lỗi, `withAudit` lỗi.

### 9.2. Quan sát outcome bằng whenComplete

```java
CompletableFuture<Order> observed =
        source.whenComplete((order, error) -> {
            if (error == null) {
                audit(order);
            } else {
                logFailure(error);
            }
        });
```

`whenComplete` giữ kiểu kết quả ban đầu. Tuy nhiên, callback này chạy **cả trên exceptional path**, nên cần ghi rõ điều kiện, tránh side effect không mong muốn; nếu callback ném lỗi, dependent stage có quy tắc exceptional completion riêng.

### 9.3. Nếu hành động phụ trả future: dùng thenCompose

**Dễ sai:**

```java
source.thenAccept(order -> auditAsync(order));
```

Nếu `auditAsync(order)` trả `CompletableFuture<Void>`, callback `thenAccept` chỉ gọi hàm và bỏ future con; stage `Void` do `thenAccept` trả về **không đợi** future đó hoàn tất.

**Đúng nếu cần đợi audit:**

```java
CompletableFuture<Void> done =
        source.thenCompose(order -> auditAsync(order));
```

`thenCompose` nối dependent stage vào future được trả về từ `auditAsync`, cho phép caller theo dõi lỗi và completion của nó. Quy tắc này đặc biệt quan trọng khi service Spring phụ trả `CompletableFuture`.

## 10. Kiểm thử thứ tự bằng CountDownLatch

Dùng `Thread.sleep(50)` để dự đoán thời điểm callback thực thi thường khiến test flaky. Thay vào đó, dùng latch giữ callback đầu và xác minh callback sau chưa thể hoàn tất.

Tệp `OrderedContinuationDemo.java` (Java 17+):

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OrderedContinuationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            CompletableFuture<String> source = new CompletableFuture<>();

            CompletableFuture<Void> done =
                    source.thenAcceptAsync(value -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                    }, executor).thenRun(() ->
                            System.out.println("next-step-ran"));

            source.complete("ok");

            if (!entered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("callback not started");
            }

            System.out.println("before-release=" + done.isDone());
            release.countDown();
            done.get(5, TimeUnit.SECONDS);
            System.out.println("after-release=" + done.isDone());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
```

```bash
javac OrderedContinuationDemo.java
java OrderedContinuationDemo
```

Kết quả khi hoàn tất bình thường:

```text
before-release=false
next-step-ran
after-release=true
```

Đây là kiểm tra quan hệ phụ thuộc, **không phải** kiểm tra thread name. Source hoàn thành nhưng callback đầu đang đợi latch, nên `done` chưa thể hoàn thành. Khi release, callback đầu kết thúc, `thenRun` tiếp tục, future cuối mới hoàn tất. Chương trình đặt timeout để phát hiện lỗi thay vì treo vô hạn.

Trong JUnit, có thể kiểm tra `assertFalse(done.isDone())` trước khi release; `assertNull(done.get(timeout, unit))` sau khi release; và `assertThrows(CompletionException.class, failed::join)` ở test lỗi. Các assertion này là fragment cần JUnit/import phù hợp.

## 11. An toàn khi thao tác dữ liệu và side effect

### 11.1. Callback có thể chia sẻ object mutable

`thenAccept` nhận kết quả T từ source, **không tự clone** đối tượng đó. Nếu hai callback Async cùng sửa một `ArrayList` hoặc một field của singleton Spring bean, có thể xảy ra race condition.

**Phản mẫu:**

```java
List<String> shared = new ArrayList<>();
source.thenAcceptAsync(value -> shared.add("A"));
source.thenRunAsync(() -> shared.add("B"));
```

Đây là fragment. Hai task truy cập `ArrayList` mutable đồng thời không có đồng bộ. Hướng sửa là xác định ownership, tạo immutable snapshot, dùng cấu trúc đồng thời/lock phù hợp hoặc tuần tự hóa các thay đổi. Nối thứ tự chỉ điều phối những stage trong chuỗi đó; thread khác ngoài chuỗi vẫn có thể sửa cùng state.

### 11.2. Idempotency và retry

Một callback ghi DB/gửi email có thể được chạy lại nếu cả workflow bị retry. Nếu ghi dữ liệu thành công nhưng future bị đánh dấu lỗi trước khi caller nhận acknowledgment, retry có thể gây **tác động lặp**. `thenAccept` không bảo đảm exactly-once; cần idempotency key, unique constraint/deduplication hoặc transactional outbox tùy nghiệp vụ.

### 11.3. Timeout, cancel và backpressure

- `get(timeout, unit)` chỉ giới hạn thời gian **caller đợi**; không tự hủy I/O của callback.
- `orTimeout(...)` khiến stage hết hạn chuyển sang exceptional completion; normal-only callbacks sau đó bị bỏ qua nếu timeout thắng race hoàn tất.
- `completeOnTimeout(value, ...)` có thể tạo fallback normal completion, nên `thenAccept` sau đó có thể chạy với fallback.
- Hủy dependent future không có nghĩa mọi upstream work hay HTTP/DB client tự bị hủy.
- Với callback Async, đặt giới hạn queue, connection, deadline và rejection; không tạo nhiều stage rồi để RAM/worker tích tụ vô hạn.

### 11.4. Quan sát đúng stage

Theo dõi **source completion** và **terminal callback completion** riêng: một source trả kết quả đúng không chứng minh audit hay notification thành công. Các metric hữu ích: queue wait, task duration, completed/failed/rejected/cancelled, timeout, độ trễ p95/p99, kết nối downstream và số công việc pending.

## 12. Lỗi phổ biến và cách giải quyết

| Lỗi | Nguyên nhân | Cách giải quyết |
| --- | --- | --- |
| Dùng `thenRun` nhưng cần kết quả T | `Runnable` không được truyền T | Dùng `thenAccept` |
| Dùng `thenAccept` rồi muốn trả U | `Consumer` không trả giá trị | Dùng `thenApply` |
| Nghĩ `CompletableFuture<Void>` không cần chờ | Stage vẫn có completion và lỗi | Giữ/return terminal future |
| Đăng ký hai nhánh rồi nghĩ có thứ tự | Chỉ cùng phụ thuộc source | Nối `thenAccept(...).thenRun(...)` |
| Nghĩ non-Async luôn ở caller thread | Continuation có thể chạy trong thread completion | Không dựa vào identity; chỉ định executor khi cần |
| Nghĩ Async continuation dùng mặc định Spring pool | Không truyền executor thường dùng common pool | Truyền Spring-managed Executor |
| Source lỗi nhưng mong thenRun chạy cleanup | Normal-only callback bị bỏ qua | Dùng `whenComplete` hoặc cleanup phù hợp |
| Callback lỗi nhưng chỉ kiểm tra source | Lỗi nằm trên dependent stage | Quan sát terminal future |
| thenAccept gọi hàm trả future rồi bỏ đi | Không đợi tác vụ phụ | Dùng `thenCompose` |
| Dùng callback in-memory cho job không được mất | JVM restart làm mất stage đang chờ | Durable queue/outbox |
| Callback sửa shared mutable list/bean field | Data race và mất cập nhật | Immutable/ownership/đồng bộ |
| Đợi `join` ngay trong request rồi nghĩ không block | `join` vẫn chờ kết quả | Thiết kế async response/deadline phù hợp |

## 13. Bài tập thực hành và tiêu chí hoàn thành

**Bài 1 — Chọn method.** Cho `CompletableFuture<OrderSummary> future`: (a) in tổng tiền nhưng không trả kết quả mới; (b) phát tín hiệu sau khi hoàn thành mà không đọc summary; (c) lấy ID làm kết quả mới. Chọn lần lượt `thenAccept`, `thenRun`, `thenApply` và giải thích kiểu trả về.

**Bài 2 — Kiểm tra lỗi.** Tạo source thành công, `thenAccept` cố ý ném `IllegalStateException`, rồi nối `thenRun`. Chứng minh callback thứ hai bị bỏ qua, dependent future lỗi nhưng source gốc vẫn thành công.

**Bài 3 — Hai nhánh và chuỗi.** So sánh `source.thenAccept(audit)` và `source.thenRun(signal)` đăng ký độc lập với `source.thenAccept(audit).thenRun(signal)`. Dùng latch tạo thứ tự kiểm thử, không dùng `sleep`.

**Bài 4 — Executor trong Spring.** Đặt `@Async("queryExecutor")` trên service trả `CompletableFuture<Summary>`. Nối `thenAcceptAsync(..., auditExecutor)`. Xác minh `auditExecutor` là Spring-managed bean, log tên thread, đo queue/rejection và timeout.

**Bài 5 — Future con.** Viết `auditAsync` trả `CompletableFuture<Void>`. Quan sát khác biệt giữa `thenAccept(x -> auditAsync(x))` và `thenCompose(x -> auditAsync(x))` khi cần bảo đảm audit đã xong.

**Bài 6 — Độ tin cậy nghiệp vụ.** Thiết kế luồng gửi thông báo sau giao dịch. Phân biệt best-effort continuation với yêu cầu durable delivery; mô tả idempotency, outbox/job ID và cách trả trạng thái cho caller.

### Checklist

- [ ] Tôi nói đúng `thenAccept` nhận T, `thenRun` không nhận T.
- [ ] Tôi hiểu `CompletableFuture<Void>` vẫn có completion, lỗi và khả năng chờ.
- [ ] Tôi phân biệt `thenApply`, `thenCompose`, `whenComplete` và `exceptionally`.
- [ ] Tôi hiểu hai nhánh độc lập không có thứ tự mặc định.
- [ ] Tôi không nhầm non-Async với “luôn trên main thread”.
- [ ] Tôi biết chọn executor rõ cho `thenAcceptAsync`/`thenRunAsync`.
- [ ] Tôi biết các callback normal-only bị bỏ qua khi source lỗi/cancel.
- [ ] Tôi quan sát lỗi ở **dependent stage**, không chỉ ở source.
- [ ] Tôi có thể test ordering bằng latch và timeout.
- [ ] Tôi hiểu Spring `@Async` pool không tự áp dụng cho mọi Java continuation.
- [ ] Tôi biết khi nào side effect cần transaction, idempotency và lưu bền vững.

**Kết luận:** `thenAccept()` **tiêu thụ kết quả T nhưng không tạo giá trị mới**; `thenRun()` **không nhận T và chỉ đợi thành công để chạy hành động**. Cả hai đều tạo `CompletableFuture<Void>` có thể nối chuỗi và quan sát. Trong Spring, phải kiểm soát đúng dependent stage, executor và side effect để một bước “không trả kết quả” không biến thành một bước “không ai chịu trách nhiệm”.

## 14. Tài liệu chính thức

- Java SE 17 — [CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `thenAccept`/`thenRun` và các biến thể Async.
- Java SE 17 — [CompletionStage](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html): semantics của Function, Consumer, Runnable và exceptional completion.
- Java SE 17 — [Executor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html): hợp đồng gửi tác vụ và nơi thực thi.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`, `TaskExecutor` và cấu hình nguồn thực thi.

Khi chạy trong môi trường thật, đối chiếu phiên bản JDK/Spring của dự án và đo thread/queue/exception thực tế thay vì suy đoán từ tên phương thức.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-006">Biến thể Async và lựa chọn bộ thực thi tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Biến thể Async và lựa chọn bộ thực thi tường minh trong CompletableFuture

**CompletableFuture** cho phép nối nhiều giai đoạn bất đồng bộ thành một pipeline: tạo dữ liệu, chuyển đổi kết quả, gọi thêm dịch vụ, kết hợp nhiều nhánh, xử lý lỗi và trả kết quả cuối. Điểm quan trọng trong **Spring Framework - concurrency** không chỉ là viết một phương thức có đuôi **Async**, mà còn phải trả lời: **giai đoạn nào chạy ở đâu, executor nào nhận việc, trường hợp nào chạy ngay trên thread hiện tại và việc lựa chọn đó tác động thế nào tới tài nguyên của ứng dụng?**

Bài học nằm trong nhóm **Kết hợp CompletableFuture**, với đề tài **Biến thể Async và lựa chọn bộ thực thi tường minh**. Trọng tâm là so sánh các biến thể không có Async và có Async, chỉ định Executor cho từng bước, hiểu cơ chế mặc định của JDK, phối hợp với Spring **@Async** và **ThreadPoolTaskExecutor**, tránh nghẽn pool, xử lý lỗi quá tải và xây dựng kiểm thử Java 17 có đầu ra xác định.

Các nguyên tắc đầu tiên:

- **thenApply** không có Async: tác vụ phụ thuộc có thể chạy ngay trên thread hoàn tất stage trước hoặc trong một ngữ cảnh gọi completion thích hợp; không có cam kết một worker riêng.
- **thenApplyAsync(fn)**: đăng ký một bước được thực thi bất đồng bộ bằng **default asynchronous execution facility** của CompletableFuture; với CompletableFuture chuẩn, thường là **ForkJoinPool.commonPool()**, không mặc nhiên là executor Spring.
- **thenApplyAsync(fn, executor)**: yêu cầu executor **được cung cấp tường minh** thực thi bước callback đó.
- **thenComposeAsync(fn, executor)**: executor được dùng để chạy **hàm fn tạo ra CompletionStage tiếp theo**, không mặc nhiên áp đặt executor cho toàn bộ những giai đoạn nằm bên trong stage mà fn trả về.
- **@Async("beanName")** của Spring và **CompletableFuture.*Async(..., executor)** của JDK là **hai cơ chế chọn executor riêng biệt**. Cấu hình một bên không tự gán executor mặc định cho mọi bước ở bên kia.

## 1. CompletableFuture có ba kiểu lựa chọn thực thi thường gặp

Tưởng tượng công việc gồm: tải dữ liệu từ hệ thống bên ngoài, chuẩn hóa kết quả rồi tính điểm.

~~~text
Request thread
    |
    +-- supplyAsync(load, ioExecutor)
                  |
                  | hoàn tất dữ liệu
                  v
         thenApplyAsync(transform, cpuExecutor)
                  |
                  v
         thenComposeAsync(nextStageFactory, cpuExecutor)
                  |
                  +-- hàm nextStageFactory trả về một CompletionStage
                      của thao tác ở executor khác nếu được chỉ định
                  |
                  v
              kết quả cuối
~~~

Với cùng một tác vụ chuyển đổi, ba lời gọi dưới đây có ý nghĩa khác nhau:

~~~java
CompletableFuture<Integer> source = /* một future hợp lệ */;

CompletableFuture<Integer> a =
        source.thenApply(n -> n * 2);

CompletableFuture<Integer> b =
        source.thenApplyAsync(n -> n * 2);

CompletableFuture<Integer> c =
        source.thenApplyAsync(n -> n * 2, cpuExecutor);
~~~

Đây là **đoạn mã minh họa trong một phương thức có source và cpuExecutor**, không phải chương trình Java độc lập.

| Cách viết | Vị trí callback được thực thi | Điều không được tự suy ra |
| --- | --- | --- |
| **thenApply(fn)** | Theo cơ chế non-async của CompletionStage: có thể là thread hoàn tất stage trước, hoặc caller tham gia hoàn tất/đăng ký trong trường hợp phù hợp | Không chắc chạy trong một executor mới |
| **thenApplyAsync(fn)** | Default async facility của CompletableFuture; thường là commonPool đối với CompletableFuture chuẩn | Không chắc chạy trong ThreadPoolTaskExecutor của Spring |
| **thenApplyAsync(fn, executor)** | Executor truyền vào nhận việc thực thi fn | Không chắc là một thread mới khác caller trong mọi custom Executor |

Phần cuối đặc biệt quan trọng: từ **Async** có nghĩa là callback được **giao cho cơ chế thực thi bất đồng bộ đã chọn**. Nếu bạn chủ động truyền một **Executor chạy trực tiếp** (Runnable::run) hoặc dùng một rejection policy kiểu **CallerRunsPolicy** khi pool đầy, callback vẫn có thể thực hiện ngay trên luồng submit. Do đó, **Async không phải một lời hứa rằng callback luôn chạy trên một OS thread khác** trong mọi cấu hình executor.

## 2. Non-async: tối ưu một bước nhỏ nhưng phải biết nó có thể chạy ở đâu

Các biến thể không có hậu tố Async, như **thenApply**, **thenAccept**, **thenRun**, **thenCompose**, **thenCombine**, **whenComplete** và **handle**, không tự yêu cầu một executor riêng cho callback.

Theo hợp đồng của CompletableFuture, hành động thuộc dependent completion ở biến thể non-async **có thể được thực thi bởi thread hoàn tất future hiện tại hoặc bởi một caller khác đang thực hiện một phương thức completion**.

Hai tình huống thường gặp:

**Trường hợp A — stage đã hoàn thành khi đăng ký callback:**

~~~java
Thread caller = Thread.currentThread();

boolean onCaller = CompletableFuture.completedFuture(10)
        .thenApply(n -> Thread.currentThread() == caller)
        .join();

System.out.println(onCaller);
~~~

Đây là **đoạn thân phương thức Java 17**. Với future đã hoàn tất và thenApply chuẩn trong ví dụ này, callback được chạy trực tiếp trong luồng đang đăng ký, nên in **true**. Không nên dựa vào hiện tượng này để coi **mọi** thenApply đều chạy trên caller.

**Trường hợp B — stage chưa hoàn thành:** callback được đăng ký trước, rồi một worker làm stage nguồn hoàn tất. Callback non-async thường có thể chạy trên thread làm nguồn hoàn tất. Nếu callback này làm I/O chặn lâu hoặc xử lý CPU nặng, nó có thể kéo dài thời gian một worker đang được dùng cho nhiệm vụ trước.

Quy tắc chọn:

- Biến đổi **rất nhẹ, không chặn, không giữ lock lâu**, như đổi trường DTO hoặc kiểm tra điều kiện đơn giản: non-async thường hợp lý.
- Tác vụ **CPU nặng, có blocking I/O, cần cô lập loại workload hoặc cần phân định tài nguyên**: xem xét Async với Executor tường minh.
- Nếu kết quả phải chạy trên một thread có affinity đặc biệt (UI/event loop), **không dựa vào thenApply non-async** để bảo đảm affinity; dùng cơ chế dispatch phù hợp với framework.

**Non-async không phải synchronous toàn bộ pipeline.** Nếu stage nguồn hoàn tất sau một phút, thenApply vẫn chỉ kích hoạt sau khi nguồn xong. Từ non-async ở đây mô tả **cách chạy callback phụ thuộc**, không nói rằng mọi nguồn chạy trong caller.

## 3. Async không chỉ là dùng ForkJoinPool.commonPool một cách vô thức

Khi dùng **CompletableFuture.supplyAsync(supplier)** hoặc **thenApplyAsync(fn)** mà **không truyền Executor**, CompletableFuture chuẩn dùng cơ chế async mặc định, thường là **ForkJoinPool.commonPool()**. Theo Javadoc Java 17, nếu commonPool không hỗ trợ mức song song tối thiểu là hai thì mặc định có thể dùng **thread mới cho từng task** thay thế. Có thể có subclass CompletableFuture tùy biến **defaultExecutor()** cho các phương thức instance.

Vì vậy, câu “mọi phương thức Async mặc định đều luôn chạy trên commonPool” là cách nói quá tuyệt đối. Đối với mã ứng dụng phổ thông, hãy hiểu **commonPool là mặc định thường gặp**, nhưng kiểm tra cấu hình/runtime nếu hành vi khác có ý nghĩa vận hành.

**Tại sao cần chú ý commonPool?**

- Đây là tài nguyên **chia sẻ của tiến trình Java**, không phải pool riêng của một Spring service.
- Một pipeline chạy nhiều blocking I/O trong commonPool có thể cạnh tranh với những tác vụ khác trong cùng JVM.
- Kích thước và chính sách của commonPool không tự động khớp với **CPU-bound**, **I/O-bound**, connection pool hay mục tiêu latency của ứng dụng.
- Việc một phương thức Spring dùng **@Async("myExecutor")** không tự động khiến những **thenApplyAsync() không tham số executor** bên trong cũng chạy trên myExecutor.
- Dùng Async không truyền executor làm khó việc phân loại workload và tracing nếu có nhiều subsystem cùng sử dụng commonPool.

**Không cần cấm commonPool tuyệt đối.** Nó có thể hữu ích cho tác vụ nhỏ, CPU-bound, dùng có chủ đích. Nhưng với backend có yêu cầu vận hành rõ, **chọn Executor tường minh** ở các giai đoạn cần kiểm soát thường giúp reasoning và đo đạc dễ hơn.

## 4. Nhóm API: những biến thể Async cần nhận diện

### 4.1 Tạo tác vụ gốc

| Phương thức | Mục đích | Biến thể truyền Executor |
| --- | --- | --- |
| **CompletableFuture.supplyAsync(supplier)** | Thực thi Supplier để tạo giá trị kết quả | **supplyAsync(supplier, executor)** |
| **CompletableFuture.runAsync(runnable)** | Chạy Runnable, future trả kiểu Void | **runAsync(runnable, executor)** |
| **future.completeAsync(supplier)** | Thử hoàn tất future bằng kết quả Supplier bất đồng bộ | **completeAsync(supplier, executor)** |

**runAsync** là lựa chọn khi task **không trả giá trị**, nhưng nó vẫn tạo CompletableFuture<Void> để theo dõi hoàn thành và lỗi. **supplyAsync** phù hợp khi task trả giá trị. **completeAsync** hoạt động trên một future có thể đã tồn tại và có cạnh tranh về việc ai hoàn tất nó; không phải cách thay thế máy móc cho supplyAsync.

### 4.2 Một stage nguồn, xử lý giá trị thành công

| Non-async | Async mặc định | Async với Executor | Ý nghĩa |
| --- | --- | --- | --- |
| **thenApply(fn)** | **thenApplyAsync(fn)** | **thenApplyAsync(fn, ex)** | T → U, trả một giá trị |
| **thenAccept(consumer)** | **thenAcceptAsync(consumer)** | **thenAcceptAsync(consumer, ex)** | Dùng T, không tạo giá trị mới |
| **thenRun(runnable)** | **thenRunAsync(runnable)** | **thenRunAsync(runnable, ex)** | Chạy sau thành công, không nhận T |
| **thenCompose(fn)** | **thenComposeAsync(fn)** | **thenComposeAsync(fn, ex)** | T → CompletionStage<U>, ghép phẳng kết quả |

**thenApply** biến T thành U. Nếu fn **trả CompletableFuture<U>**, kết quả trở thành **CompletableFuture<CompletableFuture<U>>**; khi đó thường cần **thenCompose** để tránh tương lai lồng nhau.

### 4.3 Kết hợp hai stage

| Non-async | Async với Executor | Khi nào kích hoạt |
| --- | --- | --- |
| **thenCombine(other, fn)** | **thenCombineAsync(other, fn, ex)** | Cả hai stage hoàn thành bình thường, lấy hai giá trị để tính U |
| **thenAcceptBoth(other, consumer)** | **thenAcceptBothAsync(other, consumer, ex)** | Cả hai hoàn thành bình thường, dùng hai kết quả |
| **runAfterBoth(other, action)** | **runAfterBothAsync(other, action, ex)** | Cả hai hoàn thành bình thường, chạy không cần giá trị |
| **applyToEither(other, fn)** | **applyToEitherAsync(other, fn, ex)** | Khi một stage hoàn thành bình thường đủ điều kiện |
| **acceptEither(other, consumer)** | **acceptEitherAsync(other, consumer, ex)** | Dùng kết quả thành công từ một stage |
| **runAfterEither(other, action)** | **runAfterEitherAsync(other, action, ex)** | Chạy sau một stage thành công theo hợp đồng |

Những API trên cũng có biến thể **Async không truyền Executor**; bảng chỉ hiển thị dạng ba tham số ở những phương thức kết hợp hai nguồn để nhấn mạnh lựa chọn executor.

Khi hai nhánh có thứ tự hoàn tất phụ thuộc scheduler, **không suy ra nhánh nào luôn thắng**. Với các phương thức **Either**, phải xem semantics hoàn thành bình thường/ngoại lệ theo Javadoc cụ thể, không coi chúng tự động là cơ chế lấy “kết quả thành công nhanh nhất” trong mọi trường hợp lỗi.

### 4.4 Xử lý kết quả và lỗi

| Non-async | Async với Executor | Công dụng |
| --- | --- | --- |
| **whenComplete(action)** | **whenCompleteAsync(action, ex)** | Quan sát cả thành công/lỗi; thông thường không đổi kết quả gốc |
| **handle(fn)** | **handleAsync(fn, ex)** | Có thể biến thành công hoặc lỗi thành kết quả U |
| **exceptionally(fn)** | **exceptionallyAsync(fn, ex)** | Phục hồi từ ngoại lệ bằng giá trị T |
| **exceptionallyCompose(fn)** | **exceptionallyComposeAsync(fn, ex)** | Phục hồi bằng một CompletionStage<T> khác |

Các biến thể **exceptionallyAsync** và **exceptionallyComposeAsync** có sẵn trên **Java 17**. Không nhầm chúng với **handleAsync**: handle nhận cả giá trị lẫn lỗi, trong khi exceptionally chỉ chạy khi stage phía trước hoàn thành ngoại lệ.

**Lưu ý:** khiComplete không phải cơ chế recover mặc định; nếu stage nguồn đã lỗi, khiComplete thường giữ trạng thái lỗi sau khi callback quan sát, trừ các trường hợp đặc biệt theo hợp đồng lỗi callback. Khi cần phục hồi, chọn handle/exceptionally phù hợp.

## 5. Executor được chọn ở mức nào trong một pipeline?

Quan sát chuỗi:

~~~java
CompletableFuture<String> result =
        CompletableFuture.supplyAsync(this::load, ioExecutor)
            .thenApplyAsync(this::normalize, cpuExecutor)
            .thenComposeAsync(this::enrichAsync, cpuExecutor)
            .thenApply(this::format);
~~~

Đây là **đoạn minh họa trong một class có load/normalize/enrichAsync/format, ioExecutor và cpuExecutor**; không phải chương trình có thể biên dịch độc lập.

Thứ tự phân định:

1. **supplyAsync(..., ioExecutor)**: tác vụ **load** chạy qua ioExecutor.
2. **thenApplyAsync(..., cpuExecutor)**: callback **normalize** chạy qua cpuExecutor.
3. **thenComposeAsync(..., cpuExecutor)**: callback **enrichAsync** được dispatch qua cpuExecutor. Nếu enrichAsync tự trả một future tạo bằng **supplyAsync(..., networkExecutor)**, phần việc của future đó chạy tại **networkExecutor**.
4. **thenApply(this::format)**: không chọn executor riêng. Callback format có thể chạy trên thread hoàn tất giai đoạn trước, hoặc caller khác theo semantics non-async.

Không có “tính kế thừa executor vĩnh viễn” từ stage đầu. Dùng **supplyAsync(..., ioExecutor)** không có nghĩa mọi **thenApplyAsync(...)** phía sau mặc định dùng ioExecutor. Muốn bảo đảm từng bước quan trọng chạy trong pool nào thì **truyền Executor ở chính lời gọi của bước đó**.

### Dấu hiệu sai khi review

~~~java
CompletableFuture.supplyAsync(() -> fetch(), ioExecutor)
        .thenApplyAsync(data -> heavyCompute(data));
~~~

**heavyCompute** không mặc nhiên chạy trong ioExecutor; callback Async không có đối số Executor dùng default facility. Với workload CPU được cô lập, sửa thành:

~~~java
CompletableFuture.supplyAsync(() -> fetch(), ioExecutor)
        .thenApplyAsync(data -> heavyCompute(data), cpuExecutor);
~~~

Hai đoạn code là **minh họa**, giả sử fetch/heavyCompute và executor đã được khai báo. Điểm sửa không phải là “thêm Async”, mà là **đưa đúng executor vào đúng ranh giới**.

## 6. Java 17 chạy độc lập: mỗi biến thể dùng đúng executor

Ví dụ này **không phụ thuộc Spring**. Nó tạo hai executor một luồng có tên rõ ràng, rồi lần lượt thực thi **supplyAsync**, **thenApplyAsync**, **thenComposeAsync** và một nhánh nested future. Nó không khẳng định rằng Async luôn tạo thread mới; ở đây **chúng ta chủ động chọn hai ExecutorService** có worker riêng.

Tạo tệp **AsyncVariantsDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncVariantsDemo {
    private static ExecutorService namedSingleThread(String name) {
        return Executors.newSingleThreadExecutor(
                task -> new Thread(task, name));
    }

    public static void main(String[] args) throws Exception {
        Thread caller = Thread.currentThread();

        ExecutorService io = namedSingleThread("io-worker");
        ExecutorService cpu = namedSingleThread("cpu-worker");

        AtomicReference<String> sourceThread = new AtomicReference<>();
        AtomicReference<String> mapThread = new AtomicReference<>();
        AtomicReference<String> composeThread = new AtomicReference<>();
        AtomicReference<String> nestedThread = new AtomicReference<>();
        AtomicReference<String> finalThread = new AtomicReference<>();

        try {
            CompletableFuture<Integer> source =
                    CompletableFuture.supplyAsync(() -> {
                        sourceThread.set(Thread.currentThread().getName());
                        return 10;
                    }, io);

            CompletableFuture<String> pipeline = source
                    .thenApplyAsync(value -> {
                        mapThread.set(Thread.currentThread().getName());
                        return value * 2;
                    }, cpu)
                    .thenComposeAsync(value -> {
                        composeThread.set(Thread.currentThread().getName());
                        return CompletableFuture.supplyAsync(() -> {
                            nestedThread.set(
                                    Thread.currentThread().getName());
                            return value + 5;
                        }, io);
                    }, cpu)
                    .thenApplyAsync(value -> {
                        finalThread.set(Thread.currentThread().getName());
                        return "value=" + value;
                    }, cpu);

            String result = pipeline.get(5, TimeUnit.SECONDS);

            boolean nonAsyncOnCaller =
                    CompletableFuture.completedFuture(7)
                            .thenApply(x -> Thread.currentThread() == caller)
                            .join();

            System.out.println("Non-async trên caller: "
                    + nonAsyncOnCaller);
            System.out.println("supplyAsync: " + sourceThread.get());
            System.out.println("thenApplyAsync: " + mapThread.get());
            System.out.println("thenComposeAsync: " + composeThread.get());
            System.out.println("nested supplyAsync: " + nestedThread.get());
            System.out.println("final thenApplyAsync: " + finalThread.get());
            System.out.println("result: " + result);
        } finally {
            io.shutdown();
            cpu.shutdown();
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
            if (!cpu.awaitTermination(5, TimeUnit.SECONDS)) {
                cpu.shutdownNow();
            }
        }
    }
}
~~~

Lệnh biên dịch và chạy (JDK 17+):

~~~bash
javac -encoding UTF-8 AsyncVariantsDemo.java
java AsyncVariantsDemo
~~~

**Stdout của chương trình**, nếu hai executor bình thường và task hoàn tất trong thời hạn test:

~~~text
Non-async trên caller: true
supplyAsync: io-worker
thenApplyAsync: cpu-worker
thenComposeAsync: cpu-worker
nested supplyAsync: io-worker
final thenApplyAsync: cpu-worker
result: value=25
~~~

Phân tích từng kết quả:

- **sourceThread** là io-worker vì supplier gốc được giao **io**.
- **mapThread** là cpu-worker vì biến thể **thenApplyAsync** chỉ định **cpu**.
- **composeThread** là cpu-worker vì chính **hàm tạo CompletionStage** được dispatch tới **cpu**.
- **nestedThread** quay lại io-worker vì **hàm trả về một CompletableFuture mới** thông qua **supplyAsync(..., io)**. Executor của thenComposeAsync **không điều khiển công việc bên trong nested future**.
- **finalThread** là cpu-worker do last thenApplyAsync chỉ định cpu.
- **result** bằng value=25 vì 10 × 2 + 5.
- Phép thử **completedFuture(7).thenApply(...)** chạy callback ngay khi đăng ký trên caller của ví dụ, cho kết quả **true**. Điều đó không phải một cam kết cho mọi future chưa hoàn tất.

Chương trình chỉ in sau khi đã **get(timeout)** kết quả cuối nên không phụ thuộc thứ tự log giữa worker. Những tên worker được **ThreadFactory** cấu hình tường minh. Các AtomicReference được đọc sau khi future hoàn tất, bảo đảm có thể thấy giá trị đã được ghi trong những stage tương ứng.

**Giới hạn đo lường:** ví dụ chứng minh **giai đoạn nào được chuyển sang executor nào trong cấu hình cụ thể**, không phải benchmark throughput. Executor single-thread trong ví dụ nhằm dễ giải thích và kiểm chứng; không nên bê nguyên giới hạn 1 worker vào production mà không đánh giá workload.

## 7. thenApplyAsync khác thenComposeAsync thế nào?

Xét một hàm gọi dịch vụ trả về một future:

~~~java
CompletableFuture<User> loadUserAsync(String id) {
    return CompletableFuture.supplyAsync(
            () -> queryUser(id), ioExecutor);
}
~~~

Đây là **đoạn method minh họa** với User, queryUser và ioExecutor được định nghĩa ở ứng dụng. Khi muốn load user sau khi đã lấy id:

~~~java
CompletableFuture<String> idFuture = /* stage nguồn */;

// Tạo một future lồng trong future:
CompletableFuture<CompletableFuture<User>> nested =
        idFuture.thenApplyAsync(
                id -> loadUserAsync(id),
                cpuExecutor);

// Ghép phẳng để nhận một future của User:
CompletableFuture<User> flat =
        idFuture.thenComposeAsync(
                id -> loadUserAsync(id),
                cpuExecutor);
~~~

**thenApplyAsync** chấp nhận hàm **T → U**. Nếu U là CompletableFuture<User>, kết quả là **CompletableFuture<CompletableFuture<User>>**.

**thenComposeAsync** chấp nhận hàm **T → CompletionStage<U>**, rồi tạo một stage phụ thuộc phản ánh **kết quả eventual** của stage được hàm trả về. Nhờ vậy ta nhận **CompletableFuture<User>** thay vì tương lai lồng nhau.

Trong mã này **cpuExecutor chạy hàm gọi loadUserAsync**. Nhưng chính loadUserAsync bên trong tạo tác vụ qua **supplyAsync(..., ioExecutor)** nên queryUser được thực thi trên **ioExecutor**. Đây là nơi nhiều người nhầm giữa **executor của callback tạo future** và **executor của công việc mà future mô tả**.

Một cách viết khác có thể hợp lý nếu hàm tạo future chỉ thực hiện thao tác rất nhẹ:

~~~java
CompletableFuture<User> flat =
        idFuture.thenCompose(id -> loadUserAsync(id));
~~~

Ở đây việc tạo/đăng ký future không được dispatch riêng, nhưng queryUser **vẫn có thể chạy ở ioExecutor** vì loadUserAsync tự dùng supplyAsync với ioExecutor. Nếu hàm thenCompose chỉ khởi tạo future và không blocking, cách này có thể tránh thêm một lần chuyển thread không cần thiết.

**Tóm lại:** chọn **thenApply(Async)** khi biến đổi giá trị, chọn **thenCompose(Async)** khi hàm trả về một **CompletionStage**. Quyết định **Async hay non-async** cho phần hàm callback là một quyết định khác với **Apply hay Compose**.

## 8. thenCombineAsync: kết hợp hai công việc độc lập

Giả sử cần tính một mức giá từ hai nguồn độc lập:

~~~java
CompletableFuture<Integer> baseFuture =
        CompletableFuture.supplyAsync(
                () -> loadBasePrice(), ioExecutor);

CompletableFuture<Integer> feeFuture =
        CompletableFuture.supplyAsync(
                () -> loadFee(), ioExecutor);

CompletableFuture<Integer> totalFuture =
        baseFuture.thenCombineAsync(
                feeFuture,
                (base, fee) -> base + fee,
                cpuExecutor);
~~~

Đây là **đoạn minh họa trong ứng dụng** đã định nghĩa loadBasePrice/loadFee và executors. Hai future nguồn có thể cùng tiến triển, phụ thuộc khả năng executor và tài nguyên downstream. Hàm cộng chỉ được thực thi sau khi cả hai stage hoàn thành bình thường, trên **cpuExecutor**.

Nếu chỉ cần cộng hai số, callback thực tế rất nhẹ; **thenCombine** không Async cũng có thể đủ. Chỉ định executor sẽ có giá trị khi phần kết hợp thực sự **tốn CPU**, yêu cầu **thread affinity** hoặc cần tách workload.

### allOf và anyOf không phải các overload Async với Executor

**CompletableFuture.allOf(f1, f2, ...)** tạo một future tổng hợp hoàn tất khi các future đầu vào đã hoàn tất; nó **không trả danh sách các giá trị**. Bạn vẫn đọc từng result từ những future gốc sau khi điều kiện hoàn tất được bảo đảm.

~~~java
CompletableFuture<Void> all =
        CompletableFuture.allOf(baseFuture, feeFuture);

CompletableFuture<Integer> total = all.thenApplyAsync(
        ignored -> baseFuture.join() + feeFuture.join(),
        cpuExecutor);
~~~

Đây là **đoạn minh họa** với hai future trả Integer. Khi all hoàn tất **bình thường**, các future thành viên đã hoàn tất bình thường nên join trong callback không phải đợi chúng tiếp tục chạy. Nếu một future đầu vào lỗi, all cũng có thể hoàn tất ngoại lệ và thenApplyAsync không chạy; caller phải xử lý nhánh lỗi riêng.

**allOf** bản thân **không có tham số Executor**. Nếu muốn chạy bước hậu xử lý ở pool cụ thể, chọn **thenApplyAsync/thenRunAsync/... với Executor** trên future tổng hợp. **anyOf** trả một tương lai có kiểu giá trị **Object** và hoàn tất theo future thành viên hoàn tất trước; nó cũng không phải cú pháp để cấu hình executor chạy mọi nhánh đầu vào.

Đừng nghĩ rằng **allOf** tự mở một pool hoặc tự chạy lại các tác vụ. Nó chỉ **kết hợp completion** của những future đã có.

## 9. Biến thể Async của bước cuối và xử lý lỗi

### thenAcceptAsync và thenRunAsync

~~~java
CompletableFuture<Order> orderFuture = /* future hợp lệ */;

CompletableFuture<Void> audit =
        orderFuture.thenAcceptAsync(
                order -> auditOrder(order),
                auditExecutor);

CompletableFuture<Void> cleanup =
        audit.thenRunAsync(
                () -> markAuditComplete(),
                auditExecutor);
~~~

**thenAcceptAsync** nhận Order và trả stage Void. **thenRunAsync** không nhận Order/result đầu vào, chỉ chạy sau khi upstream thành công. Hai bước được yêu cầu chạy qua **auditExecutor**, với điều kiện executor nhận việc.

**Lưu ý:** một hành động side effect như audit phải có quy tắc idempotency và lỗi rõ ràng. Nếu audit có tính bắt buộc, đừng coi một callback in-memory đã đăng ký là xác nhận công việc được lưu bền vững.

### whenCompleteAsync và handleAsync

~~~java
CompletableFuture<String> observed =
        sourceFuture.whenCompleteAsync(
                (value, error) -> logOutcome(value, error),
                logExecutor);

CompletableFuture<String> recovered =
        sourceFuture.handleAsync(
                (value, error) -> error == null
                        ? value
                        : "fallback",
                recoveryExecutor);
~~~

Đây là **đoạn minh họa** với sourceFuture, logOutcome và executors đã tồn tại. **whenCompleteAsync** phù hợp để quan sát/log và thường giữ kết quả hoặc lỗi gốc; **handleAsync** trả một giá trị mới nên có thể chuyển một exceptional completion thành kết quả bình thường, nếu logic cho phép.

### exceptionallyAsync và exceptionallyComposeAsync

Java 17 có các API:

~~~java
CompletableFuture<String> recoveredValue =
        sourceFuture.exceptionallyAsync(
                error -> "fallback",
                recoveryExecutor);

CompletableFuture<String> recoveredStage =
        sourceFuture.exceptionallyComposeAsync(
                error -> fetchFromBackupAsync(),
                recoveryExecutor);
~~~

**exceptionallyAsync** tạo giá trị phục hồi khi có lỗi. **exceptionallyComposeAsync** gọi hàm sinh một **CompletionStage<String>** khác và ghép kết quả của stage đó. Trong ví dụ thứ hai, **recoveryExecutor** được dùng để chạy hàm tạo stage backup; future được hàm **fetchFromBackupAsync()** trả về vẫn tuân theo executor/cơ chế riêng của nó.

Đừng dùng fallback chung để che mọi lỗi nghiệp vụ. Ví dụ, lỗi phân quyền hoặc request không hợp lệ thường không nên biến thành một dữ liệu hợp lệ giả. Phân loại exception/cause trước khi quyết định phục hồi hoặc retry.

**Phân biệt lỗi scheduler và lỗi task:** một task có thể được executor **từ chối trước khi chạy**; hoặc đã chạy rồi gặp exception. Cách lỗi xuất hiện với CompletableFuture phụ thuộc chính API và **thời điểm** bị reject; cần xử lý cả đường submit/đăng ký và future exceptional completion, không mặc định mọi lỗi đều đợi được bằng exceptionally.

## 10. Chọn Executor nào? Phân biệt CPU-bound, I/O-bound và downstream

Một ứng dụng có thể chọn nhiều executor, thay vì dồn mọi giai đoạn vào một pool:

| Workload | Ví dụ | Hướng chọn executor |
| --- | --- | --- |
| CPU-bound | tính điểm lớn, chuyển đổi dữ liệu nặng, nén | Worker pool hữu hạn theo khả năng CPU; tránh oversubscribe quá mạnh |
| Blocking I/O | HTTP client đồng bộ, driver DB blocking, file access | Worker riêng có giới hạn đồng thời phù hợp connection pool và timeout |
| Log/metrics nhẹ | thống kê, ghi log không chặn quá lâu | Thường không cần chuyển executor cho từng phép toán; chọn hệ thống telemetry phù hợp |
| Tác vụ bắt buộc lưu/đảm bảo | job thanh toán, gửi email quan trọng | Durable queue/job store hoặc outbox, không tin chỉ Future trong RAM |
| Logic phối hợp future | nối stage, ghép kết quả, chọn fallback | Non-async nếu thật sự nhẹ; Async có executor nếu cần cô lập/affinity |

Không có quy tắc “I/O pool luôn phải có N thread” đúng cho mọi hệ thống. Nếu database chỉ cho 10 connection đồng thời, cho 1.000 blocking DB tasks chạy trên 1.000 platform threads có thể làm tăng chờ và bộ nhớ mà không tăng throughput. **CPU pool cũng không tự tăng năng lực tính toán bằng cách tăng vô hạn số worker**.

### ThreadPoolTaskExecutor của Spring

**ThreadPoolTaskExecutor** có thể được dùng như một **Executor** chuẩn để truyền vào **CompletableFuture.*Async(..., executor)**. Nó có các thông số:

- **corePoolSize**: số worker cốt lõi.
- **maxPoolSize**: số worker tối đa.
- **queueCapacity**: dung lượng lưu task chờ; **0** chọn SynchronousQueue / handoff trực tiếp.
- **threadNamePrefix**: tên worker cho logging/debug.
- **RejectedExecutionHandler**: xử lý khi không thể nhận thêm tác vụ.
- Các tùy chọn **shutdown/lifecycle** cần phù hợp yêu cầu của ứng dụng.

Một queue quá lớn có thể tích tụ backlog và khiến maxPoolSize ít khi có cơ hội tăng. Một queue bằng 0 có thể làm pool mở rộng nhanh hơn đến max nhưng cũng reject sớm hơn. Chọn thông số theo lượng tải, SLA, deadline và resource downstream; không lấy giá trị ví dụ trong bài làm cấu hình production mặc định.

## 11. Spring @Async và CompletableFuture: hai tầng dispatch khác nhau

Spring **@Async** cho phép **Spring AOP/interceptor** chuyển việc thực thi method vào một executor. Một phương thức có **@Async("ioExecutor")** dùng executor theo tên bean/qualifier được chỉ định để chạy **phần thân method** qua cơ chế Spring. Điều đó **không thay đổi mặc định của CompletableFuture.thenApplyAsync(...)** ở các giai đoạn downstream.

Sơ đồ:

~~~text
Caller thread
   |
   +-- Spring proxy: @Async("ioExecutor")
   |          |
   |          +-- ioExecutor chạy body of service.load()
   |                       |
   |                       +-- trả kết quả CompletableFuture
   |
   +-- nhận CompletableFuture từ proxy
              |
              +-- thenApplyAsync(fn, cpuExecutor)
              |          |
              |          +-- CPU executor chạy fn
              |
              +-- thenApplyAsync(fn)  [không truyền executor]
                         |
                         +-- default async facility của CF
                             (thường là commonPool, không phải ioExecutor)
~~~

Với **@Async** chuẩn, một method cần return type hỗ trợ như **void**, **Future** hoặc **CompletableFuture** theo hợp đồng Spring. Nếu trong body method đã được Spring dispatch tới ioExecutor và tính toán đã xong, có thể trả **CompletableFuture.completedFuture(value)**. Caller sẽ nhận future do Spring proxy cung cấp để theo dõi phần thực thi bất đồng bộ.

**Không nhất thiết gọi supplyAsync thêm bên trong @Async method.** Nếu viết @Async("ioExecutor") rồi trong body lại **supplyAsync(..., cpuExecutor)** mà không có chủ đích, bạn tạo thêm một tầng scheduling, có thể làm khó việc truyền context, tính thời gian, quan sát lỗi và shutdown.

### Cấu hình hai executor Spring, chọn tường minh cho từng công đoạn

Ví dụ ứng dụng Spring dưới đây dùng:

- Bean **ioExecutor** cho body của **@Async**.
- Bean **cpuExecutor** làm tham số của **thenApplyAsync**.
- Hai pool chỉ có một worker để kiểm chứng đường chạy, **không phải kích cỡ nên dùng ở production**.

Cấu trúc thư mục Maven:

~~~text
spring-explicit-executor/
├── pom.xml
└── src/main/java/example/SpringAsyncSelectionDemo.java
~~~

**pom.xml** (Spring Framework 6.2.12 và Java 17):

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-explicit-executor</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.12</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>example.SpringAsyncSelectionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/example/SpringAsyncSelectionDemo.java**:

~~~java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class SpringAsyncSelectionDemo {
    private static final AtomicReference<String> ioThread =
            new AtomicReference<>();
    private static final AtomicReference<String> cpuThread =
            new AtomicReference<>();

    @Configuration
    @EnableAsync
    public static class Config {
        @Bean("ioExecutor")
        public ThreadPoolTaskExecutor ioExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(10);
            pool.setThreadNamePrefix("io-");
            return pool;
        }

        @Bean("cpuExecutor")
        public ThreadPoolTaskExecutor cpuExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(10);
            pool.setThreadNamePrefix("cpu-");
            return pool;
        }

        @Bean
        public ReportWorker reportWorker() {
            return new ReportWorker();
        }

        @Bean
        public ReportPipeline reportPipeline(
                ReportWorker worker,
                @Qualifier("cpuExecutor") Executor cpuExecutor) {
            return new ReportPipeline(worker, cpuExecutor);
        }
    }

    public static class ReportWorker {
        @Async("ioExecutor")
        public CompletableFuture<Integer> load(int value) {
            ioThread.set(Thread.currentThread().getName());
            return CompletableFuture.completedFuture(value + 1);
        }
    }

    public static class ReportPipeline {
        private final ReportWorker worker;
        private final Executor cpuExecutor;

        public ReportPipeline(ReportWorker worker, Executor cpuExecutor) {
            this.worker = worker;
            this.cpuExecutor = cpuExecutor;
        }

        public CompletableFuture<Integer> run(int value) {
            return worker.load(value)
                    .thenApplyAsync(loaded -> {
                        cpuThread.set(Thread.currentThread().getName());
                        return loaded * 2;
                    }, cpuExecutor);
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            int result = context.getBean(ReportPipeline.class)
                    .run(10)
                    .get(5, TimeUnit.SECONDS);

            System.out.println("ioExecutor đúng: "
                    + ioThread.get().startsWith("io-"));
            System.out.println("cpuExecutor đúng: "
                    + cpuThread.get().startsWith("cpu-"));
            System.out.println("Kết quả: " + result);
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java
~~~

**Stdout của chương trình** (không tính log của Maven/Spring, phụ thuộc môi trường):

~~~text
ioExecutor đúng: true
cpuExecutor đúng: true
Kết quả: 22
~~~

Giải thích:

1. **@EnableAsync** bật việc phát hiện **@Async**. Bean ReportWorker do Spring quản lý, method load là **public**, không final và được **ReportPipeline** (bean khác) gọi qua dependency Spring inject, cho phép async proxy hoạt động.
2. **@Async("ioExecutor")** yêu cầu Spring đưa **body của load** lên pool ioExecutor. Body ghi lại tên worker, tính 10 + 1 = 11 rồi trả **completedFuture(11)**; interceptor tạo future phù hợp cho caller.
3. ReportPipeline đăng ký **thenApplyAsync(..., cpuExecutor)**, nên phép nhân 11 × 2 được xử lý qua pool cpuExecutor.
4. **get(timeout)** được gọi ở main để chờ hoàn tất, sau đó mới in; vì vậy log kết quả không phụ thuộc thứ tự thực thi worker.
5. Hai tên thread đều xuất phát từ **setThreadNamePrefix**, và code kiểm tra tiền tố thay vì đoán số thứ tự worker.
6. Các bean ThreadPoolTaskExecutor được Spring quản lý và khởi tạo trong context; khi context đóng, vòng đời executor được container điều phối. Trong chương trình thuần Java phải đóng ExecutorService chủ động.

Đây là **ví dụ kỹ thuật tự chứa trong một dự án Maven có dependencies phù hợp**. Nó minh họa hai tầng chọn executor độc lập, không phải bộ khung production hoàn chỉnh về bảo mật, timeout, metrics và xử lý lỗi.

## 12. Chọn executor qua @Async, qualifier và AsyncConfigurer

Có nhiều mức lựa chọn trong Spring:

**A. Chỉ định trực tiếp ở method:**

~~~java
@Async("ioExecutor")
public CompletableFuture<Data> read() {
    return CompletableFuture.completedFuture(loadData());
}
~~~

Đây là **đoạn minh họa method** trong bean Spring. Chuỗi **ioExecutor** phải khớp bean name hoặc qualifier mà Spring có thể resolve thành một Executor/TaskExecutor.

**B. Chỉ định ở class:**

~~~java
@Async("ioExecutor")
public class ExternalApiWorker {
    public CompletableFuture<String> call() {
        return CompletableFuture.completedFuture("ok");
    }
}
~~~

Đây là **đoạn minh họa class** (còn cần đăng ký bean), không phải full application. Method-level **@Async("otherExecutor")** có thể **ghi đè qualifier ở class-level** theo cơ chế Spring.

**C. Cấu hình mặc định của async support:**

Có thể dùng **AsyncConfigurer** trong Spring để cung cấp executor mặc định cho những method **@Async không có qualifier**, hoặc để cấu hình xử lý ngoại lệ cho async void. Tên bean tự chọn theo quy tắc framework cũng có thể ảnh hưởng lựa chọn nếu không định rõ. Không nên trộn các cách này rồi suy ra executor thực tế chỉ từ tên annotation.

**D. Chỉ định ở từng callback CompletableFuture:**

~~~java
return worker.load()
        .thenApplyAsync(this::transform, cpuExecutor)
        .thenComposeAsync(this::saveAsync, ioExecutor);
~~~

Đây là **đoạn minh họa method có các dependency**; chính đối số Executor cho từng Async callback mới quyết định nơi callback tương ứng được dispatch. Qualifier **@Async("ioExecutor")** trên worker.load **không thay thế** hai đối số đó.

**Kết luận tổ chức:** chọn executor của method Spring bằng **@Async("...")**, chọn executor của callback CompletionStage bằng **biến thể Async có đối số Executor**, và kiểm tra bean names/qualifiers bằng integration test.

## 13. Executor tường minh không tự truyền ThreadLocal, MDC và security context

Nhiều dữ liệu trong Spring gắn với thread đang xử lý request, ví dụ:

- **MDC** dùng trong logging.
- **SecurityContext** theo chiến lược giữ context hiện tại.
- **RequestContextHolder** và request attributes.
- **Transaction/persistence context** liên kết với transaction của thread.
- Các ThreadLocal ứng dụng tự viết.

Khi chuyển từ **request thread** sang **ioExecutor** rồi tiếp tục sang **cpuExecutor**, ngữ cảnh đó **không mặc nhiên đi theo mọi bước**. Việc dùng tên executor chính xác giúp kiểm soát tài nguyên, nhưng không phải tự động kích hoạt context propagation.

Ví dụ lỗi dễ gặp:

~~~java
CompletableFuture<String> future = CompletableFuture
        .supplyAsync(() -> callRemote(), ioExecutor)
        .thenApplyAsync(value -> {
            // Đừng tự giả định MDC, Principal hay transaction
            // từ HTTP request thread hiện có ở đây.
            return process(value);
        }, cpuExecutor);
~~~

Đây là **đoạn minh họa**, không phải đoạn chạy độc lập.

Thiết kế phù hợp:

1. **Dữ liệu nghiệp vụ**: tạo immutable snapshot từ input cần thiết trước khi gửi task.
2. **Logging/tracing**: sử dụng cơ chế context propagation chính thức của stack Spring/Micrometer hoặc **TaskDecorator** được cấu hình đúng; phải làm sạch/khôi phục context sau callback khi worker được tái sử dụng.
3. **Bảo mật**: cân nhắc các wrapper/delegating executor của Spring Security hoặc xác thực/ủy quyền lại tại nơi thực hiện thao tác quan trọng. Không chia sẻ bừa toàn bộ principal/token.
4. **Transaction**: transaction ở request thread không tự mở rộng qua Async stage. Mở transaction phù hợp với thao tác ở worker, thường qua service bean giao dịch đúng proxy.
5. **Đổi executor nhiều lần**: nếu pipeline có nhiều ranh giới io → cpu → io, phải xét propagation và cleanup ở **từng ranh giới**, không chỉ stage đầu.

**Không dùng field mutable của singleton bean để giả lập request context xuyên luồng.** Điều đó khiến nhiều request có thể ghi đè thông tin của nhau.

## 14. Quá tải và rejection ở các giai đoạn Async

Khi gọi Async với Executor tường minh, stage phụ thuộc có thể **không được lên lịch** nếu executor đã shutdown hoặc bão hòa. Ví dụ **ThreadPoolTaskExecutor** có hàng đợi hữu hạn và worker giới hạn có thể ném **TaskRejectedException** theo hợp đồng Spring; JDK **ThreadPoolExecutor** thường biểu hiện từ chối qua **RejectedExecutionException**.

Phân biệt:

| Vấn đề | Xảy ra ở đâu | Cách xử lý |
| --- | --- | --- |
| Không chấp nhận task | Lúc submit/schedule một stage | Bắt lỗi có thể xảy ra ở đường gọi; quan sát exceptional completion khi API đó báo qua future; xác định retry/overload |
| Lỗi trong supplier/function | Sau khi stage được nhận và chạy | Future tương ứng hoàn tất exceptional; dùng handle/exceptionally/whenComplete |
| Task bị chờ lâu trong queue | Sau khi được nhận nhưng chưa chạy | Deadline/timeout, metric queue wait, giới hạn backlog |
| Bị block do join/get | Thread gọi join/get | Tránh blocking trong worker có năng lực hữu hạn; thiết kế compose |
| Callback bị chạy ở caller | Direct Executor hoặc CallerRunsPolicy | Không được giả định Async luôn giữ caller rảnh |

**Một điểm rất dễ nhầm:** nếu gọi **thenApplyAsync(fn, executor)** khi stage nguồn **đã hoàn tất**, implementation có thể cố schedule callback **ngay trong lời gọi đăng ký**. Nếu executor không tiếp nhận, kết quả có thể xuất hiện khác với trường hợp stage nguồn **chỉ hoàn tất sau đó** và lời từ chối xảy ra khi future đang kích hoạt dependent action. Không nên khẳng định mọi rejection đều luôn xảy ra trong một Future hay luôn ném ngay tại cùng một dòng trong mọi implementation. Viết test cho executor và API thực tế, xử lý cả submit boundary lẫn exceptional completion.

**Với @Async**, việc Spring proxy gửi method vào executor còn là một ranh giới riêng; **TaskRejectedException** có thể được caller nhận **trước khi một CompletableFuture được trả về**. Nếu task đã được nhận nhưng body lỗi, future là nơi theo dõi exceptional completion.

### Rejection policy không phải cách “để hết lỗi”

- **AbortPolicy**: từ chối bằng ngoại lệ, giúp quan sát saturation.
- **CallerRunsPolicy**: khi chưa shutdown, có thể chạy task trên thread gửi, làm các hàm Async thực thi ở caller trong trường hợp quá tải.
- **DiscardPolicy/DiscardOldestPolicy**: có nguy cơ mất công việc hoặc tạo Future không bao giờ hoàn tất nếu không quản lý trạng thái task bị bỏ.

Một backend cần xác định rõ liệu task có được phép mất hay không. Nếu không được mất, executor nội bộ trong RAM **không thay thế durable job queue/outbox**; phải có trạng thái tiếp nhận bền vững và retry/idempotency phù hợp.

## 15. Tránh deadlock và starvation khi dùng cùng một pool

Một lỗi phổ biến: gửi một task vào **executor chỉ có một worker**, rồi **ngay bên trong task đó** lại gửi task phụ vào **cùng executor** và **join()** để đợi:

~~~java
ExecutorService oneWorker = Executors.newSingleThreadExecutor();

CompletableFuture<Integer> result =
        CompletableFuture.supplyAsync(() -> {
            CompletableFuture<Integer> nested =
                    CompletableFuture.supplyAsync(() -> 42, oneWorker);
            return nested.join(); // Rủi ro deadlock!
        }, oneWorker);
~~~

Đây là **đoạn minh họa nguy hiểm**, không phải chương trình nên chạy: nếu worker thứ nhất đang chờ nested.join(), nested task nằm trong hàng đợi và không có worker thứ hai để xử lý. Đừng chạy ví dụ này mà không có cơ chế hủy/timeout bên ngoài; nó có thể treo.

Thay bằng composition **không block trong worker**:

~~~java
CompletableFuture<Integer> result =
        CompletableFuture.supplyAsync(() -> 10, oneWorker)
                .thenComposeAsync(
                        n -> CompletableFuture.supplyAsync(
                                () -> n + 32, oneWorker),
                        oneWorker);
~~~

Đây là **đoạn minh họa**. thenComposeAsync chạy hàm tạo future mới trong executor, nhưng nó **không join đợi future bên trong ngay khi đang giữ worker**; worker có thể nhả quyền chạy để task nested được thực hiện sau. Với một worker, hai task có thể lần lượt hoàn thành mà không tự giữ worker để chờ nhau.

Dù vậy, **không phải cứ thêm thenCompose là mọi hệ thống không thể deadlock**. Vẫn cần xem lock, database connection, transaction, lô task khác, queue và exception. Mấu chốt là **không block một worker trên công việc bắt buộc phải lấy cùng worker đó để tiến triển**.

### Join/get thuộc về tầng nào?

- **get(timeout)** trên thread phối hợp ở biên có thể hữu ích để chờ kết quả có deadline.
- **join()** bên trong task chạy trên pool nhỏ dễ tăng khả năng bão hòa và làm mất lợi ích của pipeline bất đồng bộ.
- **thenCompose** chuyển mối quan hệ “chờ stage phụ” thành **composition** mà không cần worker chủ động đợi trong lời gọi join.
- Nếu bắt buộc block vì API ngoài chỉ đồng bộ, phân bổ pool phù hợp và đặt timeout rõ ràng, không giả vờ nó là nonblocking.

## 16. Timeouts, delayedExecutor và lựa chọn executor

Java 17 có các tiện ích:

- **orTimeout(timeout, unit)**: nếu future chưa hoàn thành trước deadline thì làm future đó hoàn thành ngoại lệ TimeoutException theo hợp đồng.
- **completeOnTimeout(value, timeout, unit)**: làm future hoàn thành bằng giá trị fallback nếu quá hạn.
- **delayedExecutor(delay, unit, executor)**: tạo một Executor adapter trì hoãn thời điểm **giao task cho executor được chỉ định**.
- **CompletableFuture.copy()** và **minimalCompletionStage()**: hỗ trợ kiểm soát cách bên ngoài truy cập/hoàn tất stage trong API thiết kế thư viện; không phải lựa chọn thread cho callback.

**orTimeout** và **completeOnTimeout** không có tham số Executor riêng để chọn nơi chạy code nghiệp vụ; chúng tác động trạng thái completion và không đồng nghĩa việc task nguồn bị interrupt/hủy ngay khi timeout xảy ra.

Ví dụ trì hoãn một tác vụ:

~~~java
Executor delayed = CompletableFuture.delayedExecutor(
        200, TimeUnit.MILLISECONDS, ioExecutor);

CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> "ok", delayed);
~~~

Đây là **đoạn code minh họa** với ioExecutor, TimeUnit và Executor đã import. Executor adapter trì hoãn rồi chuyển task tới **ioExecutor**. Nó **không nói rằng callback sau future tự kế thừa ioExecutor**. Nếu sau đó cần transform trong cpuExecutor, viết **thenApplyAsync(fn, cpuExecutor)**.

**Deadline nghiệp vụ** có thể bao gồm thời gian queue wait + execution + retry. Một CompletableFuture timeout sau 2 giây không tự giải phóng DB connection đang bị blocking I/O kéo dài; tài nguyên I/O cần timeout/cancellation riêng.

## 17. Kiểm thử việc chọn executor một cách xác định

Không nên chứng minh Async bằng cách đo **Thread.sleep(50)** rồi nhìn thứ tự in log. Hãy thiết kế test theo điều bạn cần xác minh:

**Kiểm thử phân định pool:**

1. Tạo hai ExecutorService có ThreadFactory đặt tên rõ hoặc lưu **Thread identity** trong AtomicReference.
2. Chạy **supplyAsync(..., ioExecutor)** và **thenApplyAsync(..., cpuExecutor)**.
3. Chờ future cuối với **get(timeout)**.
4. Assert supplier/callback đã chạy trên thread/executor được cấu hình.
5. Đóng pool trong finally để test không rò rỉ worker.

**Kiểm thử non-async:**

1. Tạo completedFuture đã hoàn tất.
2. Gọi thenApply và đối chiếu Thread.currentThread() tại callback với caller.
3. Để kiểm thử khả năng non-async chạy khi stage hoàn tất sau, dùng **CountDownLatch** điều phối, không đoán thứ tự thực thi.
4. Không biến một trường hợp kiểm thử thành “quy luật mọi non-async luôn chạy trên caller”.

**Kiểm thử @Async và proxy:**

1. Dùng **ApplicationContext**, không **new ReportWorker()** trực tiếp.
2. Bật **@EnableAsync**; gọi method **qua Spring proxy từ bean khác**.
3. Tránh self-invocation **this.load()**, method private/final không phù hợp với kiểu proxy.
4. Đo thread chạy body và thread chạy bước **thenApplyAsync(..., cpuExecutor)** tách biệt.
5. Kiểm thử khi executor bão hòa/shutdown, phân biệt **TaskRejectedException tại submit** và future exceptional completion.

**Kiểm thử overload:**

- Cấu hình pool có worker/queue hữu hạn.
- Giữ task đang chạy bằng **CountDownLatch**, chờ xác nhận các worker đã bắt đầu.
- Submit thêm task tới lúc bão hòa, assert hành vi Abort/CallerRuns theo cấu hình.
- Luôn release latch/cleanup trong finally.
- Theo dõi số task rejected, queue wait, pool active và tác động response latency.

## 18. Những lỗi hiểu nhầm thường gặp

**“Async luôn tạo thread mới.”** Sai. CompletableFuture.*Async gọi một Executor; executor có thể tái sử dụng worker, chạy trực tiếp hoặc CallerRuns khi quá tải.

**“thenApply và thenApplyAsync chỉ khác về tên, kết quả như nhau nên không quan trọng.”** Sai. Khác biệt ở lựa chọn thread và tác động latency/saturation/context, dù giá trị cuối có thể giống.

**“supplyAsync(..., ioExecutor) buộc mọi thenApplyAsync sau đó dùng ioExecutor.”** Sai. Mỗi Async callback không truyền executor sử dụng default asynchronous facility; phải truyền executor ở từng ranh giới muốn kiểm soát.

**“thenComposeAsync(..., cpuExecutor) chạy tất cả future mà hàm trả về trong cpuExecutor.”** Sai. cpuExecutor chạy hàm callback tạo CompletionStage, còn future trả về có thể dùng executor riêng.

**“@Async("ioExecutor") khiến CompletableFuture.thenApplyAsync() tự dùng ioExecutor.”** Sai. Spring @Async và JDK CompletableFuture có cơ chế cấu hình khác nhau.

**“thenComposeAsync luôn tốt hơn thenCompose.”** Sai. Nếu hàm tạo future rất nhẹ và không block, thenCompose non-async có thể tránh scheduling bổ sung. Đánh giá nhu cầu thực sự.

**“thenApplyAsync làm phép tính CPU nặng nhanh hơn nếu tăng số thread vô hạn.”** Sai. CPU vẫn có giới hạn core/cache và overhead scheduling; cần đo throughput/latency.

**“allOf sẽ tự chạy mọi input future trên executor truyền vào.”** Sai. allOf chỉ kết hợp completion; các input futures phải được khởi tạo/chạy theo cơ chế riêng.

**“join/get bên trong worker pool chỉ là cách viết gọn, không ảnh hưởng concurrency.”** Sai. Blocking có thể gây starvation/deadlock, nhất là khi task phụ cần cùng một pool.

**“@Async luôn trả về ngay mà caller không bao giờ chặn.”** Không chắc. CallerRunsPolicy hoặc lỗi ở submit có thể ảnh hưởng; sau đó caller cũng có thể tự gọi join/get để chờ.

**“Exception trong pipeline và rejection ở submit có thể xử lý bằng đúng một exceptionally.”** Không nên giả định. Rejection có thể xảy ra trước khi một future được tạo/trả về hoặc khi scheduling dependent callback; phải kiểm tra API thực tế.

**“orTimeout tự hủy task gốc và giải phóng mọi I/O resource.”** Sai. Nó thay đổi completion semantics của future; việc ngắt/cancel/timeout operation cần thiết kế riêng.

**“Executor tường minh có nghĩa MDC, SecurityContext và transaction luôn đi theo.”** Sai. Context propagation là vấn đề khác, phải có cơ chế chuyên dụng và cleanup.

**“Future trả về 202 Accepted nghĩa là job đã lưu bền vững.”** Sai. CompletableFuture không phải durable queue và không bảo đảm sống qua restart.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Gọi **thenApplyAsync(fn)** không truyền Executor trong một CompletableFuture chuẩn. Có chắc dùng ThreadPoolTaskExecutor của Spring không?

**Đáp án:** Không. Nó dùng default asynchronous execution facility của CompletableFuture, thường là ForkJoinPool.commonPool().

**Câu 2.** Gọi **thenApplyAsync(fn, cpuExecutor)** sau **supplyAsync(supplier, ioExecutor)**. Supplier và fn có bắt buộc dùng cùng executor không?

**Đáp án:** Không. Supplier được gửi tới ioExecutor; fn được gửi tới cpuExecutor.

**Câu 3.** Một **thenApply(fn)** non-async đứng sau future chưa hoàn tất. Có chắc fn chạy trên caller tạo chuỗi không?

**Đáp án:** Không. fn có thể chạy trên thread hoàn tất upstream hoặc caller khác liên quan completion; không có thread affinity mặc định.

**Câu 4.** Hàm tạo stage trong **thenComposeAsync(fn, cpuExecutor)** trả về **supplyAsync(..., ioExecutor)**. Công việc nested chạy ở đâu?

**Đáp án:** fn chạy qua cpuExecutor; phần supplier của future được trả về chạy qua ioExecutor.

**Câu 5.** Nếu fn trả về CompletableFuture<User>, nên dùng **thenApply** hay **thenCompose** để có **CompletableFuture<User>**?

**Đáp án:** thenCompose; thenApply sẽ tạo future lồng nhau.

**Câu 6.** Có thể dùng **thenCombineAsync** để tổng hợp hai future độc lập trong một executor riêng không?

**Đáp án:** Có. Truyền (other, function, executor), callback kết hợp được scheduling qua executor khi hai nguồn hoàn tất bình thường.

**Câu 7.** **allOf(f1, f2)** có chấp nhận tham số executor để di chuyển mọi f1/f2 vào pool mới không?

**Đáp án:** Không. allOf chỉ tạo stage tổng hợp; muốn hậu xử lý ở pool nào, nối thêm callback Async với Executor đó.

**Câu 8.** **@Async("ioExecutor")** trên Spring service có tự điều khiển **thenApplyAsync(fn)** ở phía caller không?

**Đáp án:** Không. Caller cần gọi thenApplyAsync(fn, ioExecutor) hoặc một executor khác nếu muốn lựa chọn rõ.

**Câu 9.** Nếu một worker duy nhất gọi nested Future.join() mà nested task cũng nằm chờ trong pool có một worker, nguy cơ là gì?

**Đáp án:** Starvation/deadlock vì worker duy nhất đang chờ công việc cần chính worker đó để chạy.

**Câu 10.** Nếu pipeline chỉ có transform rất nhẹ, không chặn và không cần affinity, có nhất thiết dùng Async ở mọi bước không?

**Đáp án:** Không. Non-async có thể giảm chi phí scheduling, với điều kiện hiểu callback có thể chạy trên thread hoàn tất upstream.

**Câu 11.** **whenCompleteAsync** và **handleAsync** khác nhau ở đâu?

**Đáp án:** whenComplete chủ yếu quan sát outcome và thường giữ outcome cũ, handle có thể tính một kết quả mới từ thành công hoặc ngoại lệ.

**Câu 12.** Nếu **@Async** bị executor từ chối ngay lúc gọi proxy, một handler **exceptionally** gắn vào future có chắc nhận được lỗi không?

**Đáp án:** Không. Có trường hợp lời gọi chưa trả Future để gắn handler; cần xử lý ở boundary submit và ở future nếu đã được trả.

**Câu 13.** **orTimeout** có bảo đảm task đang gọi HTTP client sẽ lập tức dừng không?

**Đáp án:** Không. Timeout ở future không tự đảm bảo cancel/hủy I/O. Cần timeout/cancellation của chính client và policy cleanup.

## 20. Checklist thiết kế pipeline trong ứng dụng Spring

- [ ] Tách rõ **nguồn dữ liệu** và các **dependent stages**.
- [ ] Với mỗi stage, xác định nó **CPU-bound, blocking I/O hay callback rất nhẹ**.
- [ ] Chọn **non-async** cho callback nhẹ không đòi hỏi thread affinity khi phù hợp.
- [ ] Dùng **Async(..., executor)** ở mỗi ranh giới thật sự muốn chọn pool.
- [ ] Không giả định executor của stage nguồn được thừa kế vô hạn.
- [ ] Phân biệt **thenApply** với **thenCompose**, và executor của callback với executor của future mà callback trả về.
- [ ] Khi dùng **@Async("...")**, xác minh bean name/qualifier, **@EnableAsync** và lời gọi đi qua Spring proxy.
- [ ] Xử lý riêng **rejection tại scheduling/submit** và lỗi task qua future.
- [ ] Không gọi **join/get** chờ nested task trong cùng pool hữu hạn nếu có thể dùng composition.
- [ ] Cấu hình **pool, queue, max threads, timeout** theo downstream và SLA.
- [ ] Truyền **immutable snapshot** của dữ liệu request cần thiết; không chia sẻ mutable field singleton giữa các request.
- [ ] Propagate/cleanup **MDC, SecurityContext, tracing** đúng cách ở các ranh giới thread.
- [ ] Test thread identity hoặc tên worker được đặt tường minh, không chỉ kiểm tra giá trị cuối.
- [ ] Dùng get(timeout), release latch và shutdown/awaitTermination để test không bị treo.
- [ ] Nếu công việc không được phép mất khi restart, thiết kế **durable job queue/outbox**, không dựa chỉ vào CompletableFuture.

## Kết luận

**Biến thể Async** của CompletableFuture cho bạn kiểm soát **nơi và cách chạy callback**, còn lựa chọn **Executor tường minh** cho phép phân chia CPU, blocking I/O, giới hạn concurrency và quản lý quá tải một cách có chủ đích. Các phương thức non-async không tự chọn worker riêng; các phương thức Async không truyền executor thường dùng default facility của CompletableFuture, **không mặc nhiên là pool Spring**.

Trong hệ thống Spring, **@Async("executorName")** điều khiển executor của **method body được proxy dispatch**, trong khi **thenApplyAsync/thenComposeAsync/thenCombineAsync(..., executor)** điều khiển executor của **từng dependent callback**. Để tránh “async nhưng chạy sai pool”, hãy **đặt Executor đúng ở từng ranh giới cần kiểm soát**, phân biệt nested stage, tránh blocking làm nghẽn worker, quản lý context và test bằng thread identity hoặc executor được đặt tên tường minh.

**Tài liệu chính thức nên đọc:**

- Java SE 17: [CompletableFuture Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html) và [CompletionStage](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html).
- Java SE 17: [Executor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html), [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html), [ForkJoinPool](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html).
- Spring Framework: [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html), [@Async Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html), [ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-007">Các bước không có hậu tố Async có thể chạy trên luồng hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Các bước không có hậu tố Async có thể chạy trên luồng hoàn tất

Trong Java 17+ và Spring Framework, `CompletableFuture` cho phép ghép nhiều bước xử lý bằng `thenApply`, `thenCompose`, `thenAccept`, `whenComplete`, `handle`, `thenCombine`... Một hiểu lầm rất phổ biến là: “Đã dùng `CompletableFuture` nên mọi callback đều chạy trên một thread nền”, hoặc “callback nối sau một `@Async` sẽ luôn dùng đúng executor đã khai báo tại `@Async`”.

**Không đúng.** Theo chính sách thực thi của `CompletableFuture`, hành động của các phương thức **không có hậu tố `Async`** có thể được thực hiện bởi **thread làm cho future trước đó hoàn tất**, hoặc một thread khác tham gia thao tác hoàn tất. Nếu stage đã hoàn tất khi ta đăng ký callback, callback có thể chạy **ngay trên thread đang đăng ký**. Vì vậy, callback không có hậu tố `Async` **không có bảo đảm chuyển luồng**.

Đây là kiến thức nền tảng trong nhóm **Kết hợp CompletableFuture** của module **Spring Framework – concurrency**: cần biết callback có thể chạy ở đâu trước khi đặt vào đó truy vấn chặn luồng, tính toán nặng, logic transaction, logging context hoặc code phụ thuộc thread.

## 1. Hình dung một chuỗi CompletableFuture

```text
Thread "request"
     |
     | đăng ký callback
     v
CompletableFuture<String> source
     |
     +-- thenApply(transform) ------------> stage B
     |             ^
     |             | có thể chạy trên thread hoàn tất source
     |
     +-- thenApplyAsync(transform, pool) --> stage C
                   |
                   | xếp lịch callback qua executor đã chỉ định
                   v
              pool worker
```

Hai điểm cần phân biệt:

1. **Stage có thể chưa hoàn thành** dù phương thức đăng ký callback như `thenApply(...)` đã trả về một `CompletableFuture` mới.
2. **Luồng chạy callback** không nhất thiết là luồng gọi phương thức đăng ký; với phương thức không `Async`, luồng đó phụ thuộc vào thời điểm và cơ chế hoàn tất.

“Không có `Async`” **không đồng nghĩa** “luôn chạy trên main thread”, “luôn chạy đồng bộ từ đầu đến cuối” hoặc “luôn chạy trên thread mà producer được khởi tạo”. Nó có nghĩa **không yêu cầu executor riêng để chuyển callback** theo chính sách non-async.

### Những thành phần cần hiểu

| Thành phần | Vai trò |
| --- | --- |
| `CompletableFuture<T>` | Biểu diễn kết quả có thể hoàn thành trong tương lai; có thể hoàn tất bình thường, ngoại lệ hoặc bị hủy |
| `CompletionStage<T>` | API ghép các bước phụ thuộc vào kết quả tương lai |
| Upstream stage | Stage đầu vào mà một callback chờ |
| Dependent stage | Stage mới do `thenApply`/`thenCompose`/... trả về |
| Completion thread | Thread thực hiện hành động hoàn tất upstream, ví dụ gọi `complete(...)` hoặc kết thúc async computation |
| Registration thread | Thread gọi `thenApply(...)` để gắn một callback |
| Executor | Đối tượng nhận task để lên lịch thực thi, ví dụ `ThreadPoolTaskExecutor` hoặc `ExecutorService` |

Một stage mới cũng có thể là upstream cho các stage phía sau; mỗi điểm nối có quy tắc thực thi riêng.

## 2. Chính sách chính thức: non-Async không có thread affinity

Javadoc `CompletableFuture` trong Java SE 17 nêu chính sách cho các hành động của dependent completion **non-async**: chúng *may be performed by the thread that completes the current CompletableFuture, or by any other caller of a completion method*. Đây là chính sách **có thể**, không phải lời hứa cố định thread nào sẽ chạy.

Có ba tình huống nên nhớ:

### Tình huống A — Callback được đăng ký khi upstream chưa hoàn tất

```java
CompletableFuture<String> source = new CompletableFuture<>();

CompletableFuture<String> next =
        source.thenApply(String::toUpperCase);

source.complete("hello");
```

Ở cách triển khai phổ biến của JDK 17, nếu callback đã đăng ký trước `complete` và không có thao tác hoàn tất cạnh tranh, thread gọi `source.complete("hello")` có thể thực thi ngay `String::toUpperCase` trước khi hoàn tất việc xử lý các dependent completions.

**Không nên dựa vào tên thread để coi đây là hợp đồng.** Nếu nhiều thread tham gia hoàn tất hoặc có race giữa đăng ký và hoàn tất, luồng thực tế có thể khác. Điểm được bảo đảm về mặt ý nghĩa là **stage phụ thuộc phải tính đúng kết quả**, không phải thread identity.

### Tình huống B — Upstream đã hoàn tất trước khi callback được đăng ký

```java
CompletableFuture<String> ready =
        CompletableFuture.completedFuture("hello");

CompletableFuture<String> next =
        ready.thenApply(String::toUpperCase);
```

Với implementation JDK 17 thông thường, callback `thenApply` có thể được chạy **ngay lúc gọi `thenApply` trên registration thread**, bởi kết quả đầu vào đã sẵn có.

**Hậu quả:** nếu đoạn xử lý trong `thenApply` tốn 500 ms, lệnh đăng ký callback trong request thread có thể tốn gần 500 ms. Đừng cho rằng “đã là callback Future thì thao tác đăng ký luôn gần như tức thì”.

### Tình huống C — Future hoàn tất trên worker của executor

```java
ExecutorService worker = Executors.newSingleThreadExecutor();

CompletableFuture<String> source =
        CompletableFuture.supplyAsync(() -> "hello", worker);

CompletableFuture<String> next =
        source.thenApply(String::toUpperCase);
```

Đây là **fragment**, cần khai báo imports và đóng `worker`. Khi task của `supplyAsync` hoàn tất sau lúc callback đã được đăng ký, callback non-async **có thể chạy trên worker vừa hoàn tất upstream**. Nếu upstream hoàn tất quá nhanh trước khi `thenApply` được gọi, callback có thể chạy ngay trên registration thread. Chỉ đọc hai dòng code không đủ để kết luận tên thread.

**Tóm lại:** phương thức non-async **có thể chạy trên luồng hoàn tất**, nhưng **không được hứa luôn chạy trên luồng đó**.

## 3. So sánh phương thức có và không có hậu tố Async

| Non-Async | Async tương ứng | Điểm khác về scheduling |
| --- | --- | --- |
| `thenApply(fn)` | `thenApplyAsync(fn)` | Async yêu cầu thực thi fn qua default asynchronous execution facility |
| `thenApply(fn)` | `thenApplyAsync(fn, executor)` | Cho phép chọn executor rõ ràng cho fn |
| `thenAccept(action)` | `thenAcceptAsync(action, executor)` | Action nhận kết quả, trả `CompletableFuture<Void>` |
| `thenRun(action)` | `thenRunAsync(action, executor)` | Action không nhận kết quả đầu vào |
| `thenCompose(fn)` | `thenComposeAsync(fn, executor)` | Async chuyển bước gọi hàm tạo stage con qua executor |
| `thenCombine(other, fn)` | `thenCombineAsync(other, fn, executor)` | Hàm kết hợp được scheduling qua executor |
| `whenComplete(action)` | `whenCompleteAsync(action, executor)` | Callback quan sát kết quả/lỗi cũng có thể được scheduling |
| `handle(fn)` | `handleAsync(fn, executor)` | Hàm chuyển đổi cả success/error qua executor |

Một số operation như `exceptionally(...)` có phiên bản async trong các Java release hiện đại (`exceptionallyAsync(...)` của Java 12+); cần xem API của phiên bản JDK đang dùng. Các phương thức **không có hậu tố Async** trong bảng không được hứa thread identity.

### 3.1. Default Async dùng ở đâu?

Với `CompletableFuture`, overload `thenApplyAsync(fn)` không truyền `Executor` sử dụng **default asynchronous execution facility**, thường là `ForkJoinPool.commonPool()` trong Java 17. Javadoc còn lưu ý trường hợp common pool không hỗ trợ mức parallelism ít nhất 2 thì implementation có thể dùng thread mới cho từng task. Đây là **chính sách của `CompletableFuture`**, không tự lấy `ThreadPoolTaskExecutor` của Spring.

Vì vậy:

```java
var step = source.thenApplyAsync(this::heavyCalculation);
```

Không đồng nghĩa step dùng bean `applicationTaskExecutor` hoặc bean `reportExecutor` do Spring tạo. Nếu muốn đảm bảo điểm chuyển execution được đưa vào pool đã chọn, truyền explicit executor:

```java
var step = source.thenApplyAsync(this::heavyCalculation, cpuExecutor);
```

Hai đoạn trên là **fragment**: `source`, `heavyCalculation` và `cpuExecutor` phải tồn tại trong class ứng dụng.

### 3.2. Async không nhất thiết “tạo thread mới”

Hậu tố `Async` nghĩa là callback được **lên lịch theo chính sách executor**, không phải “khởi động `new Thread` cho mỗi callback” hay “chắc chắn khác thread”.

Một `Executor` tùy chỉnh kiểu:

```java
Executor direct = Runnable::run;
future.thenApplyAsync(value -> transform(value), direct);
```

có thể thực hiện action **ngay trên thread gọi `execute`**, vì direct executor chạy `Runnable.run()` tại chỗ. `CallerRunsPolicy` của một pool quá tải cũng có thể khiến caller trực tiếp làm việc. Do đó, nếu hệ thống cần **cô lập tài nguyên hoặc tránh block request thread**, phải kiểm soát cả behavior của executor, overload/rejection policy và task duration; không chỉ thêm hậu tố `Async`.

## 4. Java 17 runnable: ba trường hợp thread khác nhau, kết quả xác định

Chương trình sau minh họa ba trường hợp:

1. `thenApply` đăng ký **trước khi** source hoàn tất; worker của producer gọi `complete`.
2. `thenApply` gắn vào `completedFuture` ngay trên registration thread.
3. `thenApplyAsync` dùng **executor được chỉ định rõ** để thực thi callback.

Dùng `CountDownLatch` thay vì `Thread.sleep(...)` để tạo thứ tự sự kiện chắc chắn. Chủ động đặt tên thread để stdout không phụ thuộc tên do hệ điều hành/JVM tự cấp.

Lưu nội dung vào **ContinuationThreadDemo.java**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ContinuationThreadDemo {
    private static ExecutorService oneWorker(String name) {
        return Executors.newSingleThreadExecutor(
                task -> new Thread(task, name));
    }

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("registration-thread");

        ExecutorService producer = oneWorker("completion-worker");
        ExecutorService cpu = oneWorker("cpu-worker");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            CompletableFuture<String> source = new CompletableFuture<>();

            Future<?> producerJob = producer.submit(() -> {
                started.countDown();
                try {
                    release.await();
                    source.complete("payload");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    source.completeExceptionally(e);
                }
            });

            started.await();

            // Source chưa hoàn tất: callback đã được đăng ký.
            CompletableFuture<String> deferred =
                    source.thenApply(value ->
                            Thread.currentThread().getName() + ":" + value);

            release.countDown();
            String a = deferred.join();
            producerJob.get();

            // Source đã hoàn tất trước khi đăng ký callback.
            CompletableFuture<String> ready =
                    CompletableFuture.completedFuture("ready");
            String b = ready.thenApply(value ->
                    Thread.currentThread().getName() + ":" + value).join();

            // Chỉ định rõ nơi thực thi action bất đồng bộ.
            String c = ready.thenApplyAsync(value ->
                    Thread.currentThread().getName() + ":" + value,
                    cpu).join();

            System.out.println("pending thenApply -> " + a);
            System.out.println("completed thenApply -> " + b);
            System.out.println("explicit thenApplyAsync -> " + c);
        } finally {
            release.countDown();
            producer.shutdownNow();
            cpu.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ContinuationThreadDemo.java
java ContinuationThreadDemo
```

**Stdout của chương trình trên khi chạy với Java 17 trong tình huống mô tả:**

```text
pending thenApply -> completion-worker:payload
completed thenApply -> registration-thread:ready
explicit thenApplyAsync -> cpu-worker:ready
```

**Vì sao không phải chỉ tình cờ?**

- `started.await()` đảm bảo producer task đã bắt đầu và đang chờ `release`, nên registration thread có thời gian gắn `thenApply` trước khi producer gọi `complete`.
- Chỉ có producer thread gọi `source.complete(...)` sau khi latch mở, không có thread khác cạnh tranh hoàn tất source. Với cơ chế non-async của JDK 17, dependent action được thực thi trên completion worker trong tình huống này.
- `ready` được tạo qua `completedFuture` ngay trên registration thread; callback ngắn của `thenApply` đã có input ngay lúc đăng ký.
- `thenApplyAsync(..., cpu)` gửi callback vào executor `cpu` chỉ có một thread đặt tên `cpu-worker`.
- Kết quả được lưu thành ba biến rồi **in từ main theo thứ tự cố định**, thay vì print trực tiếp từ ba callback có thể thay nhau ghi stdout.
- `finally` đóng hai executor. `shutdownNow` ở đây chỉ để dọn tài nguyên của demo; ứng dụng cần chính sách graceful shutdown phù hợp.

**Phạm vi kết luận:** demo xác định luồng thực thi trong **điều kiện có latch, thread factory và implementation JDK 17 đang dùng**; không biến những kết quả này thành quy tắc mọi `CompletableFuture` non-async ở mọi hệ thống luôn chạy trên cùng tên thread.

## 5. Chuyển tiếp non-Async: có thể “ăn” thời gian của completion thread

Giả sử một tác vụ I/O hoàn tất trên worker `io-1`, rồi đăng ký:

```java
CompletableFuture<Invoice> parsed =
        fetchInvoiceAsync(id)
                .thenApply(this::parseInvoice)
                .thenApply(this::validate)
                .thenApply(this::calculateLargeReport);
```

Đây là **fragment**, không có implementation `Invoice`/các method. Vấn đề không nằm ở việc gọi `thenApply` nhiều lần mà ở **nội dung callback** và **thread thực thi**.

Nếu `fetchInvoiceAsync` chưa hoàn tất khi các dependent actions được đăng ký, cùng một thread vừa hoàn tất I/O có thể tiếp tục chạy các callback đó. Khi `calculateLargeReport` tốn CPU hàng trăm mili giây, completion thread phải làm thêm CPU thay vì giải phóng công việc khác.

Hệ quả có thể gồm:

- Worker của một `ThreadPoolTaskExecutor` bị chiếm ngoài dự kiến; queue task khác phải chờ.
- Nếu upstream hoàn tất trên thread xử lý HTTP/Netty event loop, callback blocking có thể làm chậm cả event loop.
- Nếu upstream đã hoàn tất trước khi đăng ký và callback non-async được chạy ngay trong caller, request thread có thể bị block ngay tại câu lệnh `thenApply`.
- Khi có nhiều dependent stages, không dự đoán thứ tự chúng được chạy dựa vào thứ tự viết code; tránh side effect yêu cầu ordering ngoài quan hệ phụ thuộc rõ ràng.
- Nếu callback ném exception, dependent future có thể hoàn tất ngoại lệ; lỗi đó không nhất thiết xuất hiện trực tiếp ở thời điểm caller gọi `thenApply` trong mọi tình huống.

### 5.1. Tách CPU work sang executor phù hợp

```java
CompletableFuture<Invoice> result =
        fetchInvoiceAsync(id)
                .thenApply(this::parseSmallPayload)
                .thenApplyAsync(this::calculateLargeReport, cpuExecutor);
```

**Ý nghĩa:** `parseSmallPayload` là bước nhỏ có thể để inline nếu đã đánh giá an toàn; `calculateLargeReport` được giao cho `cpuExecutor` chuyên trách. Trong thực tế, nếu parsing cũng CPU-heavy hoặc có thể block, hãy chuyển cả bước đó vào executor thích hợp.

Đừng dùng mặc định `ForkJoinPool.commonPool()` cho mọi loại công việc trong web service khi cần kiểm soát quota, log, saturation và lifecycle theo bean Spring.

### 5.2. Async ở một bước không “lây” sang mọi bước sau

```java
var b = source.thenApplyAsync(this::cpuStep, cpuExecutor);
var c = b.thenApply(this::nextStep);
```

`cpuStep` được scheduling qua `cpuExecutor`, nhưng `nextStep` **là non-async**. Nếu `b` chưa hoàn tất khi `nextStep` được đăng ký, `nextStep` có thể chạy ngay trên thread hoàn tất `b` — thường là worker đang làm `cpuStep`. Nếu `b` đã hoàn tất rồi mới đăng ký `nextStep`, action có thể chạy ngay trên thread gọi dòng hai.

Không có “executor gắn vĩnh viễn vào chain” chỉ vì một bước từng dùng `thenApplyAsync(..., cpuExecutor)`. **Mỗi điểm nối cần quyết định rõ** có cần dispatch hay không.

## 6. Những phương thức non-Async cần chú ý ngoài thenApply

### 6.1. `thenAccept` và `thenRun`

```java
stage.thenAccept(result -> saveToDatabase(result));
stage.thenRun(() -> refreshCache());
```

`thenAccept` nhận kết quả upstream; `thenRun` không nhận kết quả. Cả hai là non-async trong hai lệnh trên. Nếu các action ghi DB/cache có I/O blocking, chúng có thể **chặn completion thread hoặc caller**.

Nếu workload nặng hoặc phải isolate, dùng `thenAcceptAsync(..., ioExecutor)` hay `thenRunAsync(..., executor)` với executor phù hợp. Đồng thời, không coi thực thi action này là transaction của upstream nếu có thread khác và boundary riêng.

### 6.2. `whenComplete` không chỉ là “observer nhẹ” theo mặc định

```java
CompletableFuture<Order> observed = stage.whenComplete(
        (value, error) -> audit(value, error));
```

Đây là fragment. `whenComplete` tạo một dependent stage và chạy action khi upstream hoàn tất. Nếu `audit` ghi log ngắn, chi phí có thể nhỏ; nhưng nếu `audit` gọi REST hoặc thực hiện nhiều truy vấn, callback có thể chiếm completion thread.

**Semantics lỗi:** `whenComplete` chủ yếu dùng quan sát kết quả/lỗi, không biến một failure thành success như `handle`. Nếu action ném lỗi, dependent stage có thể nhận failure theo quy tắc `whenComplete`; không nên sử dụng `whenComplete` để mặc định “nuốt mọi exception”.

### 6.3. `handle` và `exceptionally` cũng non-Async

```java
CompletableFuture<String> recovered =
        stage.handle((value, error) ->
                error == null ? value : "fallback");
```

`handle` chạy khi stage hoàn tất bình thường hoặc ngoại lệ và **chuyển đổi** result. `exceptionally` chỉ xử lý nhánh ngoại lệ. Cả hai có thể chạy trên completion thread khi gọi các bản non-async.

Một fallback gọi service ngoài có thể chặn worker, nên đừng mặc định code xử lý lỗi là nhẹ. Khi JDK hỗ trợ, `handleAsync(..., executor)` hoặc `exceptionallyAsync(..., executor)` giúp định tuyến các bước fallback đắt đỏ.

### 6.4. `thenCompose` khác `thenApply`

Giả sử hàm `fetchProfileAsync` đã trả về một `CompletionStage<Profile>`. Khi ghép:

```java
CompletableFuture<Receipt> chain =
        lookupUserAsync(id)
                .thenCompose(user -> fetchReceiptAsync(user));
```

`thenCompose` (non-async) **có thể chạy hàm `user -> fetchReceiptAsync(user)` trên completion thread của upstream**. Nhưng nó **không đồng nghĩa toàn bộ việc fetch receipt chạy trên cùng thread**: stage do `fetchReceiptAsync` trả về có thể thực hiện I/O hoặc hoàn tất ở một nơi khác. `thenCompose` chỉ làm phẳng `CompletionStage<CompletionStage<T>>` thành stage kết quả.

Nếu bước **khởi tạo** request con cũng cần chuyển thread, dùng `thenComposeAsync(user -> fetchReceiptAsync(user), executor)`. Hãy tránh `join()` bên trong `thenCompose`; ghép stage bằng cách return chính stage con thường tốt hơn:

```java
// Không nên nếu có khả năng block:
.thenApply(user -> fetchReceiptAsync(user).join())

// Nên dùng composition, không chặn callback để đợi stage con:
.thenCompose(user -> fetchReceiptAsync(user))
```

Hai dòng trên là **fragment chain**, không phải chương trình độc lập.

### 6.5. `thenCombine`, `runAfterBoth`, `allOf`

- `thenCombine(a, b, fn)` chạy `fn` khi cả hai stage đầu vào có kết quả thành công; với bản non-async, callback có thể thực hiện trên thread hoàn tất một trong hai stage hoặc thread tham gia hoàn tất.
- `runAfterBoth` có callback không nhận result; có bản Async tương ứng nếu cần chuyển nơi thực thi.
- `CompletableFuture.allOf(a, b)` trả về future phụ thuộc vào các future đầu vào. Nếu nối `allOf(...).thenRun(this::aggregate)`, bước `aggregate` vẫn là **non-async**, không tự có thread riêng.
- `anyOf` cũng tạo một completion stage theo điều kiện bất kỳ kết quả hoàn tất; nối callback phía sau phải xét tương tự.

**Quy tắc:** việc có **nhiều upstream future** không cung cấp bảo đảm về thread chạy callback cuối. Quan hệ phụ thuộc quyết định **khi nào được phép xử lý**, không cố định **thread nào xử lý**.

## 7. Bối cảnh Spring: @Async executor không tự truyền vào continuation

Trong Spring Framework, `@EnableAsync` và `@Async("ioExecutor")` có thể giao method vào một bean `ThreadPoolTaskExecutor` tên `ioExecutor`. Tuy nhiên, code gọi method có thể nối tiếp `CompletableFuture`:

```java
CompletableFuture<String> loaded = dataService.loadAsync(id);

CompletableFuture<Result> result =
        loaded.thenApply(this::heavyTransform);
```

Câu hỏi **“`heavyTransform` chạy trên `ioExecutor` hay HTTP request thread?”** không thể trả lời bằng một tên cố định:

- Nếu `loaded` chưa hoàn tất khi `thenApply` đăng ký, callback non-async có thể chạy trên thread làm future hoàn tất (thường có liên quan worker của `ioExecutor`).
- Nếu `loaded` hoàn tất trước khi `thenApply` đăng ký, callback có thể chạy inline trên request thread.
- Nếu code khác tham gia hoàn tất dependent stage, có thể phát sinh đường thực thi khác theo completion policy.
- `@Async` xác định interceptor của **method được annotate**; nó **không gắn một executor mặc định vào mọi dependent callback** sau khi method trả Future.

**Không mặc định `thenApplyAsync` không truyền executor sẽ chọn `ioExecutor`.** Nó sử dụng default asynchronous execution facility của `CompletableFuture`, thường là common pool của JDK, không tự kế thừa bean Spring.

### 7.1. Ví dụ Spring Boot 3.x đầy đủ

Ví dụ sau dùng **Java 17+**, **Spring Boot 3.x/Spring Framework 6.x** với dependency `spring-boot-starter-web`. Khai báo hai executor rõ ràng:

- `ioExecutor` nhận việc `@Async` mô phỏng đọc dữ liệu blocking.
- `cpuExecutor` nhận continuation xử lý CPU qua `thenApplyAsync(..., cpuExecutor)`.

Lưu thành **src/main/java/com/example/demo/ContinuationApplication.java**:

```java
package com.example.demo;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
public class ContinuationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContinuationApplication.class, args);
    }

    @Bean("ioExecutor")
    ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(50);
        pool.setThreadNamePrefix("io-");
        pool.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return pool;
    }

    @Bean("cpuExecutor")
    ThreadPoolTaskExecutor cpuExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(30);
        pool.setThreadNamePrefix("cpu-");
        pool.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return pool;
    }
}

@Service
class DataService {
    @Async("ioExecutor")
    public CompletableFuture<String> loadAsync(String id) {
        try {
            Thread.sleep(150); // Mô phỏng đọc dữ liệu blocking
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
        return CompletableFuture.completedFuture("item:" + id);
    }
}

@Service
class WorkflowService {
    private final DataService dataService;
    private final Executor cpuExecutor;

    WorkflowService(
            DataService dataService,
            @Qualifier("cpuExecutor") Executor cpuExecutor) {
        this.dataService = dataService;
        this.cpuExecutor = cpuExecutor;
    }

    public CompletableFuture<String> process(String id) {
        return dataService.loadAsync(id)
                .thenApplyAsync(
                        value -> value.toUpperCase(Locale.ROOT),
                        cpuExecutor);
    }
}

@RestController
class WorkflowController {
    private final WorkflowService workflow;

    WorkflowController(WorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/process/{id}")
    public CompletableFuture<String> process(@PathVariable String id) {
        return workflow.process(id);
    }
}
```

Chạy bằng build Spring Boot có dependencies tương ứng:

```bash
./gradlew bootRun
curl -s http://localhost:8080/process/Z1
```

Trên Windows, dùng `gradlew.bat bootRun`. Khi request được tiếp nhận và hai bước thực thi thành công, response body là:

```text
ITEM:Z1
```

**Giải thích luồng:**

1. Spring tạo các `ThreadPoolTaskExecutor` dưới dạng bean và quản lý initialization/shutdown.
2. Controller gọi `WorkflowService.process`; service gọi bean `DataService` khác nên lời gọi `loadAsync` đi qua Spring `@Async` proxy ở chế độ mặc định.
3. Method `loadAsync` chạy trong worker của `ioExecutor`. `Thread.sleep` chỉ mô phỏng I/O blocking, **không phải non-blocking I/O**.
4. `thenApplyAsync(..., cpuExecutor)` đưa bước chuyển đổi vào executor `cpuExecutor`; không dựa vào việc thread nào hoàn tất `loadAsync`. `toUpperCase` ở đây nhẹ để minh họa, thực tế chỉ cần tách pool khi task đủ lớn hoặc có yêu cầu isolation.
5. Controller trả `CompletableFuture<String>` để Spring MVC xử lý kết quả khi hoàn tất. **HTTP client vẫn chờ response body**; đây không phải API trả job ID 202 ngay.
6. Nếu pool bị bão hòa hoặc một bước ném exception, request sẽ không có response thành công như ví dụ; cần mapping lỗi/overload theo hợp đồng của ứng dụng.

**Điểm quan trọng:** dùng `@Async("ioExecutor")` cho method gốc không buộc callback `thenApply(...)` phía sau dùng `ioExecutor`. Nếu muốn tránh tính toán nặng trên I/O worker hoặc request thread, quyết định tại **chính callback** bằng `thenApplyAsync(..., cpuExecutor)`.

### 7.2. Vì sao không tự tạo một executor mới mỗi request?

`Executors.newFixedThreadPool(...)` được tạo trong controller/service mỗi request sẽ gây bùng số threads, khó quan sát và không tự được Spring quản lý shutdown. Hãy tạo executor **bean dùng chung** có giới hạn rõ ràng và inject theo qualifier.

Nếu custom `TaskExecutor` quá tải, nó có thể từ chối scheduling. Tách pool giúp **cô lập tài nguyên**, nhưng không bảo đảm các tác vụ luôn được nhận; vẫn cần đo queue, active threads và xử lý lỗi từ chối.

## 8. ThreadLocal, MDC, Spring transaction và security context

Một callback non-async có thể chạy ở nhiều thread khác nhau theo timing. Điều này làm những logic dựa vào **thread-local state** trở nên nguy hiểm.

### 8.1. MDC logging

Nếu callback lấy `MDC.get("traceId")` và giả định luôn có ID của HTTP request, kết quả có thể thay đổi:

- Callback chạy inline trên request thread: có thể thấy request MDC.
- Callback chạy trên một pooled worker: MDC **không tự động được truyền** từ request thread.
- Nếu worker không cleanup MDC đúng cách, callback có thể thấy **trace ID cũ** của task khác trên cùng worker.

Khi cần correlation ID, truyền bằng dữ liệu tường minh trong payload hoặc thiết kế context propagation phù hợp. Với Spring `ThreadPoolTaskExecutor`, có thể dùng `TaskDecorator` để copy/restore context ở điểm submit nếu cần, nhưng **không được nghĩ decorator chỉ gắn lên upstream executor sẽ bao trùm mọi callback non-async về sau**. Callback inline trên một thread bất kỳ không chắc đi qua task decorator của executor đã cấu hình.

### 8.2. Transaction context

Spring transaction thông thường gắn với thread. Một callback non-async có thể chạy trên thread hoàn tất hoặc thread đăng ký; **không được dựa vào timing** để đoán transaction của caller có còn hiện diện hay không.

Ví dụ:

```java
@Transactional
public void createOrder() {
    CompletableFuture<String> f = remoteCallAsync();
    f.thenApply(result -> updateOrder(result));
}
```

Đây là fragment có nguy cơ: `thenApply` có thể thực thi ở thread khác ngoài transaction của `createOrder`, hoặc inline trong caller tùy completion timing. Nếu `updateOrder` cần transaction, hãy đưa nó vào một **Spring-managed service method** với boundary transaction rõ ràng và gọi qua proxy; không đặt giả định transaction tự “chảy” sang mọi callback.

Ngay cả khi callback tình cờ chạy inline và thấy transaction, đó không phải thiết kế bền vững. Trong các pipeline async, xác định ranh giới transaction cho từng thao tác dữ liệu, bảo đảm idempotency và consistency khi cần.

### 8.3. Security/request context

`SecurityContextHolder` mặc định thường gắn state với thread. `RequestContextHolder` cũng có các giả định về thread/request lifecycle. Không đưa các object request sống ngắn hoặc mutable request context vào queue/continuation rồi dùng lâu sau khi HTTP request kết thúc.

Nếu cần danh tính để xử lý công việc nền, truyền **dữ liệu danh tính đã kiểm chứng và tối thiểu cần thiết**, hoặc dùng các wrapper/executor hỗ trợ context propagation có kiểm soát. Không tự sao chép toàn bộ context nhạy cảm giữa thread nếu không hiểu boundary.

## 9. Callback blocking có thể tạo starvation và deadlock

Hai anti-pattern đáng tránh:

### 9.1. Chặn completion thread để chờ một task khác cùng pool

```java
CompletableFuture<String> result =
        upstream.thenApply(value -> {
            // Nếu downstream cần worker đang bị callback này chiếm,
            // việc chờ có thể gây starvation.
            return nextAsync(value).join();
        });
```

Đây là fragment minh họa. Nếu pool chỉ có một worker và `nextAsync` cũng phải được lên lịch vào cùng pool, callback chiếm worker rồi `join` chờ task chưa thể bắt đầu: **starvation deadlock**.

Thay bằng composition:

```java
CompletableFuture<String> result =
        upstream.thenCompose(value -> nextAsync(value));
```

`thenCompose` không cần chặn thread chỉ để đợi stage con. Tuy vậy, **hàm khởi tạo stage con vẫn là non-async** và có thể chạy trên completion thread; nếu việc khởi tạo bản thân nặng, cân nhắc `thenComposeAsync(..., executor)`.

### 9.2. Blocking trong event loop

Với WebFlux/Netty và thư viện reactive/non-blocking, completion thread đôi khi là event-loop thread. Một callback `thenApply` gọi JDBC, `Thread.sleep` hoặc xử lý CPU nặng có thể chặn event loop và làm ảnh hưởng nhiều kết nối. Hãy dùng cơ chế scheduling/offloading phù hợp với framework và thư viện đang sử dụng; không suy luận “Future là async nên blocking ở đâu cũng được”.

### 9.3. Callback đồng bộ chạy ngay trên caller

Ngay cả trong servlet MVC truyền thống, `completedFuture(...).thenApply(this::slowStep)` có thể gọi `slowStep` inline lúc đăng ký, giữ request thread. Vì vậy hai request giống nhau nhưng upstream completion timing khác nhau có thể có **latency khác nhau ở điểm nối**.

Không nên dùng phương thức non-async cho work nặng chỉ vì thử nghiệm một lần cho thấy callback chạy trên background worker.

## 10. Exception cũng là completion; xử lý lỗi có thể chạy inline

Một future có thể hoàn tất ngoại lệ:

```java
CompletableFuture<String> source = new CompletableFuture<>();
source.completeExceptionally(new IllegalStateException("service down"));

CompletableFuture<String> recovered =
        source.exceptionally(error -> "fallback");
```

Nếu `source` đã hoàn tất ngoại lệ trước lúc đăng ký, `exceptionally` có thể chạy ngay tại registration thread. Với upstream chưa hoàn tất, fallback non-async có thể chạy trên thread thực hiện exceptional completion.

Một callback lỗi gọi thêm DB/HTTP để dự phòng cũng có nguy cơ block giống callback thành công.

### Những quy tắc quan trọng

- `thenApply` thường chỉ chạy action khi upstream hoàn tất bình thường; nếu upstream exceptional, dependent stage truyền tiếp trạng thái lỗi theo quy tắc CompletionStage.
- `handle` nhận cả result và throwable; phù hợp khi muốn chuyển đổi cả success và failure.
- `whenComplete` thích hợp cho logging/observability nhưng có thể tạo một dependent stage bị lỗi nếu callback cũng lỗi; không phải công cụ “nuốt mọi exception”.
- `exceptionally` có thể khôi phục một upstream failure thành kết quả bình thường nếu trả fallback; **phải xác định** fallback nào chấp nhận được theo nghiệp vụ.
- `join()` ném `CompletionException` cho failure; `get()` có thể ném `ExecutionException`/`InterruptedException`. Quan sát đúng đường lỗi.
- Khi dùng custom executor, task scheduling có thể bị **rejected** nếu pool quá tải hoặc đang shutdown; không hứa `thenApplyAsync` luôn thành công chỉ vì nó có hậu tố Async.

Trong Spring, quản lý riêng đường lỗi của `@Async void` và `CompletableFuture<T>`: handler uncaught async được dùng cho void, còn caller Future cần quan sát exceptional completion bằng `whenComplete`/`handle` hoặc framework error mapping phù hợp.

## 11. Đo lường, kiểm thử và quyết định thiết kế

### 11.1. Kiểm thử thread thực thi bằng điều kiện xác định

Một bài test đáng tin không dựa vào `Thread.sleep(100)` và đoán worker đã hoàn tất. Dùng:

1. `CompletableFuture<String>` chưa hoàn tất.
2. `CountDownLatch` giữ completion worker trước khi gọi `complete`.
3. Đăng ký `thenApply` trước khi mở latch.
4. Trong callback lưu thread name, không đưa nghiệp vụ vào việc so sánh cứng tên thread.
5. Mở latch, dùng `join`/`get` để chờ kết quả.
6. So sánh với một `completedFuture` đã hoàn tất trước khi đăng ký.
7. Đăng ký `thenApplyAsync(fn, executor)` trên executor có tên và kiểm tra bước cần isolate đi qua đó.

Demo `ContinuationThreadDemo.java` bên trên thực hiện đúng ba trường hợp đầu với output có thể kiểm chứng.

### 11.2. Chọn non-Async hay Async như thế nào?

| Tình huống | Gợi ý |
| --- | --- |
| Mapping giá trị rất nhỏ, pure function, không blocking | `thenApply` có thể phù hợp |
| Ghi log/metric rất nhẹ, không phụ thuộc ThreadLocal | `whenComplete` có thể phù hợp |
| Biến đổi CPU nặng | `thenApplyAsync(fn, cpuExecutor)` |
| Gọi blocking I/O trong callback | `thenCompose` với API async thực sự, hoặc `thenApplyAsync`/`thenComposeAsync` trên executor I/O thích hợp |
| Đăng ký callback trong HTTP request, không được block request thread | Tránh callback non-async nặng; tách qua executor |
| Kết hợp hai Future rồi xử lý nặng | `thenCombineAsync(..., executor)` |
| Cần gọi API con trả `CompletionStage<T>` | `thenCompose` để flatten, thêm Async nếu bước tạo stage cần dispatch |
| Nghiệp vụ cần transaction riêng | Gọi Spring bean method với transaction boundary rõ, không dựa vào thread của callback |
| Cần context MDC/security | Propagate context có chủ đích; không tin future tự kế thừa thread-local |
| Dự án cần quota và độ trễ đo lường được | Executor bean có giới hạn, metric, overload policy và lifecycle |
| Một stage trước đã dùng `thenApplyAsync` | Vẫn đánh giá độc lập stage sau, vì executor không được tự động “kế thừa” |

Chọn theo **chi phí thực thi và ranh giới tài nguyên**, không theo quy tắc “luôn Async là tốt” hoặc “luôn non-Async là nhanh”. Dispatch quá nhiều callback rất nhỏ có thể làm tăng scheduling overhead; để callback lớn chạy inline có thể làm nghẽn completion worker.

### 11.3. Theo dõi để phát hiện callback chạy sai nơi

Đối với hệ thống Spring có nhiều executor, quan sát:

- Tên thread khi callback bắt đầu, nhưng **không dùng thread name làm logic nghiệp vụ**.
- Thời gian chạy từng continuation và độ trễ end-to-end.
- `ThreadPoolTaskExecutor#getActiveCount()`, `getPoolSize()`, queue depth.
- Tỷ lệ rejected scheduling, exceptional completion, timeout và cancellation.
- Thread dump khi hệ thống nghẽn, xem completion workers bị chiếm bởi callback hay đang chờ `join`.
- Latency HTTP request và event loop khi upstream đã hoàn tất nhanh, vì callback có thể chạy inline trong caller.

Một callback mất 800 ms không phải “nhẹ” chỉ vì nằm trong `thenApply`. Nhận diện cụ thể callback nào chiếm thread nào giúp quyết định chỗ cần offload.

## 12. Lỗi phổ biến

**Lỗi 1 — Tin rằng mọi `thenApply` đều chạy trên thread tạo future.** Future có thể được hoàn tất ở thread khác, hoặc đã hoàn tất trước khi callback đăng ký.

**Lỗi 2 — Tin rằng non-Async chắc chắn chạy trên completion thread.** Đây là khả năng phổ biến, **không phải bảo đảm duy nhất**; callback có thể chạy ngay trên registration thread.

**Lỗi 3 — Tin rằng `thenApplyAsync` luôn dùng executor của `@Async` Spring.** Nếu không truyền executor, `CompletableFuture` sử dụng default async facility của nó, thường là common pool.

**Lỗi 4 — Tách CPU work bằng `thenApplyAsync` nhưng quên explicit executor.** Có thể đẩy computation vào shared common pool không được giám sát như pool Spring.

**Lỗi 5 — Dùng `thenApply` để gọi blocking API hoặc tính toán dài.** Completion worker/request thread/event loop có thể bị giữ ngoài dự kiến.

**Lỗi 6 — Nối `thenApply` sau một `thenApplyAsync` rồi nghĩ executor vẫn được “kế thừa”.** Non-async continuation vẫn có chính sách không cố định thread.

**Lỗi 7 — `thenCompose` tự được hiểu là “mọi thứ bất đồng bộ”.** Callback tạo stage con vẫn có thể chạy inline; chỉ việc chờ stage con được flatten, không block qua `join`.

**Lỗi 8 — `join()` trong callback chạy trên single-thread executor.** Nếu task được đợi cần cùng worker đó, có thể starvation deadlock.

**Lỗi 9 — Dựa vào `ThreadLocal`/transaction của request trong callback.** Completion thread không ổn định theo timing và thread-local không tự chuyển theo CompletableFuture.

**Lỗi 10 — Cho rằng `@Async` tự hoạt động khi method tự gọi trong cùng bean.** Self-invocation ở chế độ proxy mặc định bỏ qua interceptor.

**Lỗi 11 — Không quan sát exceptional completion.** Future có thể đã kết thúc bằng lỗi, nhưng callback `thenApply` không chạy và endpoint vẫn không biết thất bại nếu bỏ qua Future.

**Lỗi 12 — Tạo executor mới cho mỗi callback.** Dẫn đến rò rỉ thread và thiếu capacity control; ưu tiên shared, managed executor.

**Lỗi 13 — Chuyển mọi callback nhỏ thành Async vô điều kiện.** Tăng scheduling overhead mà không cải thiện throughput. Cần đo và chọn các ranh giới thật sự có giá trị.

**Lỗi 14 — Kiểm thử bằng sleep rồi khẳng định luồng chạy luôn cố định.** Timing biến đổi giữa JVM, máy và tải; dùng latch/executor tên rõ ràng cho thí nghiệm, nhưng ứng dụng production vẫn không ràng business logic vào thread identity.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `CompletableFuture.completedFuture("x").thenApply(fn)` có luôn chạy trên worker thread không?

**Đáp án:** Không. Future đã hoàn tất trước khi đăng ký, nên với implementation thường gặp, `fn` có thể chạy ngay trong thread gọi `thenApply`.

**Câu 2.** Method `@Async("ioExecutor")` trả `CompletableFuture<String>`. Nối `thenApply(this::heavyCpu)` thì heavyCpu chắc chắn chạy trên `ioExecutor`?

**Đáp án:** Không. Non-async callback có thể chạy trên completion thread hoặc registration/completion-related thread tùy thời điểm. Nếu muốn định tuyến CPU work, dùng `thenApplyAsync(this::heavyCpu, cpuExecutor)`.

**Câu 3.** `thenApplyAsync(fn)` không truyền executor sẽ tự dùng `ThreadPoolTaskExecutor` mặc định của Spring?

**Đáp án:** Không. Theo chính sách của `CompletableFuture` trong JDK, thường dùng `ForkJoinPool.commonPool()`, không tự động lấy Spring bean.

**Câu 4.** `thenApplyAsync(fn, directExecutor)` có bảo đảm khác thread không?

**Đáp án:** Không. Một direct executor như `Runnable::run` có thể thực thi action ngay trên caller của `execute`. “Async” là cơ chế scheduling qua executor, không phải bảo đảm tạo mới hay đổi thread.

**Câu 5.** `thenCompose(user -> fetchAsync(user))` có tự block đợi `fetchAsync` không?

**Đáp án:** Không. Nó gộp stage con vào stage kết quả. Tuy nhiên callback khởi tạo `fetchAsync` là non-async và có thể chạy trên completion thread.

**Câu 6.** Vì sao không nên `thenApply(x -> fetchAsync(x).join())` trên một pool nhỏ?

**Đáp án:** Callback có thể chiếm worker và block đợi task con cũng cần worker đó, gây starvation hoặc deadlock. Thường nên dùng `thenCompose(x -> fetchAsync(x))`.

**Câu 7.** Nếu callback `whenComplete` chỉ ghi log thì có an toàn không?

**Đáp án:** Có thể phù hợp nếu thực sự nhẹ, không blocking và không phụ thuộc context không được truyền. Nếu log/telemetry làm I/O chậm hoặc cần context đặc biệt, phải đánh giá lại nơi thực thi.

**Câu 8.** Một callback non-async tình cờ thấy transaction/request MDC của caller trong một test. Có nên dựa vào điều đó?

**Đáp án:** Không. Nếu future hoàn tất ở thời điểm khác, callback có thể chạy trên thread khác và thiếu context. Thiết kế transaction, security và correlation rõ ràng theo ranh giới.

**Câu 9.** Một bước `thenApplyAsync(..., cpuExecutor)` đã hoàn tất. `thenApply(...)` gắn sau đó sẽ chạy ở đâu?

**Đáp án:** Có thể chạy inline trên thread đăng ký vì upstream đã hoàn tất; nếu đăng ký trước khi upstream kết thúc, có thể chạy trên thread hoàn tất stage. Không có quy tắc “luôn CPU executor”.

## Tóm tắt

- **Non-Async continuation không có thread affinity được bảo đảm.** Nó có thể chạy trên completion thread hoặc thread khác tham gia completion; nếu upstream đã sẵn kết quả, có thể chạy ngay tại lúc đăng ký.
- **Hậu tố Async đặt ra yêu cầu lên lịch**, nhưng không đảm bảo “thread mới”; khi cần ranh giới tài nguyên rõ ràng, hãy truyền **executor cụ thể**.
- **Spring `@Async` chỉ điều phối phương thức được proxy intercept**, không gắn bean executor ấy vào mọi continuation của `CompletableFuture`.
- Chỉ để callback nhỏ, pure, không blocking chạy non-async khi phù hợp. **Offload xử lý CPU nặng, blocking I/O hoặc bước cần isolation** sang executor thích hợp.
- Tránh `join()` blocking trong completion callback; dùng `thenCompose` cho stage con. Không dựa vào ThreadLocal, MDC, transaction hay tên thread trùng hợp theo timing.
- Kiểm chứng bằng test có `CountDownLatch`, explicit executor và đo callback latency, thay vì kết luận từ một lần chạy ngẫu nhiên.

## Tài liệu tham khảo

1. Java SE 17 API — CompletableFuture, asynchronous completion policies: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
2. Java SE 17 API — CompletionStage methods: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html
3. Java SE 17 API — Executor: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html
4. Spring Framework Reference — Task Execution and Scheduling, `@Async`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
5. Spring Framework Javadoc — ThreadPoolTaskExecutor: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
6. Spring Framework Reference — AOP proxying and self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-008">supplyAsync() không chỉ định executor và nguy cơ dùng chung common pool</a>

<details>
<summary>Xem chi tiết</summary>

# supplyAsync() không chỉ định executor và nguy cơ dùng chung common pool

## Mục tiêu và phạm vi

Trong ứng dụng Java và Spring, `CompletableFuture.supplyAsync(...)` giúp chạy một `Supplier<T>` ở nơi khác và trả về `CompletableFuture<T>` để caller tiếp tục công việc. Nhưng **không truyền `Executor`** không có nghĩa là Spring tự chọn thread pool của ứng dụng. Trong Java 17, overload một tham số **mặc định gửi task vào `ForkJoinPool.commonPool()`**, một tài nguyên **dùng chung trong JVM**.

Đây là điểm quan trọng khi ghép nhiều `CompletableFuture`: công việc vô tình chia sẻ common pool có thể tranh chấp worker với công việc của thư viện hoặc thành phần khác, đặc biệt khi task **blocking I/O**, chạy lâu hoặc khi lưu lượng tăng đột biến.

Sau bài này, bạn có thể:

- Phân biệt chính xác `supplyAsync(supplier)` và `supplyAsync(supplier, executor)`, kể cả ngoại lệ của JDK khi common pool không có mức song song tối thiểu.
- Giải thích `ForkJoinPool.commonPool()` là pool dùng chung toàn JVM, không phải pool riêng cho mỗi request, bean Spring hay lần gọi `supplyAsync`.
- Phân tích tác động của CPU-bound work, blocking I/O, task dài, quá nhiều future và các bước chaining đối với tài nguyên chung.
- Cấu hình một `ThreadPoolTaskExecutor` Spring rồi **truyền tường minh** vào `supplyAsync` và những stage `...Async` cần điều phối.
- Kiểm tra thread thực thi bằng tên thread/metrics mà không viết bài test phụ thuộc tên worker cố định của common pool.
- Phân biệt lỗi trong `Supplier`, lỗi **rejection tại thời điểm submit**, timeouts, cancellation và context propagation.
- Chọn cách kết hợp `CompletableFuture` an toàn, dễ giám sát, có giới hạn tài nguyên và vòng đời phù hợp production.

**Phiên bản chạy ví dụ:** Java 17+; ví dụ Spring dùng **Spring Framework 6.1.3**, Maven và Java 17+. Tài liệu kỹ thuật được đối chiếu theo Javadoc Java SE 17 và tài liệu Spring. Trọng tâm là cơ chế thực thi và kiểm soát tài nguyên, không phải cách sử dụng các API nội bộ hoặc thay đổi toàn cục của JVM.

## 1. Hai overload có vẻ giống nhau nhưng khác nơi chạy

`CompletableFuture` có hai phương thức tĩnh:

~~~java
// Chữ ký API rút gọn — không phải Java class độc lập.
static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier);

static <U> CompletableFuture<U> supplyAsync(
        Supplier<U> supplier, Executor executor);
~~~

Với overload thứ nhất, `Supplier.get()` được chạy trên cơ chế thực thi bất đồng bộ **mặc định của JDK**. Theo Javadoc Java 17, các async methods không nhận `Executor` được thực thi bởi `ForkJoinPool.commonPool()`, **trừ trường hợp** common pool không hỗ trợ mức song song ít nhất 2: khi đó `CompletableFuture` tạo **một thread mới cho mỗi task** tương ứng. Vì vậy nói “luôn có một worker mang tên `ForkJoinPool.commonPool-worker-*`” là không chính xác cho mọi JVM.

Overload thứ hai dùng chính `Executor` được truyền. Trong Spring, `ThreadPoolTaskExecutor` triển khai `Executor`, nên có thể dùng trực tiếp làm tham số thứ hai. Nó **không được chọn tự động** chỉ vì ứng dụng đã khai báo một `TaskExecutor` bean.

Ví dụ minh họa:

~~~java
// Fragment, giả sử ioExecutor là Executor đã được cấu hình.
CompletableFuture<String> a =
        CompletableFuture.supplyAsync(() -> "A"); // executor mặc định

CompletableFuture<String> b =
        CompletableFuture.supplyAsync(() -> "B", ioExecutor); // chọn rõ
~~~

Hai lời gọi cùng trả `CompletableFuture<String>`, nhưng **tài nguyên thực thi khác nhau**. Việc kiểm soát queue, thread name, số worker, thread context và metrics phụ thuộc executor thực sự nhận task, không phụ thuộc kiểu Future trả về.

| Tiêu chí | `supplyAsync(supplier)` | `supplyAsync(supplier, executor)` |
| --- | --- | --- |
| Nơi chạy mặc định | `ForkJoinPool.commonPool()` của JVM; có ngoại lệ parallelism nhỏ | Executor bạn cung cấp |
| Có dùng Spring `TaskExecutor` bean đã khai báo không? | **Không tự động** | Có nếu bạn **truyền chính bean đó** |
| Giới hạn concurrency | Theo chính sách common pool và tài nguyên JVM | Theo cấu hình executor riêng |
| Tách biệt tải của nghiệp vụ | Không, chia sẻ với code khác dùng common pool | Có thể tách riêng theo loại workload |
| Thread name / metrics / shutdown | Phụ thuộc common pool | Có thể chủ động cấu hình, quan sát và quản lý |
| Nguy cơ tác vụ blocking chiếm worker chung | Có | Có thể khống chế bằng pool riêng và giới hạn downstream |
| Exception của Supplier | Future hoàn tất thất bại | Future hoàn tất thất bại |
| Executor từ chối nộp | Thường không do queue ứng dụng Spring | Có thể ném `RejectedExecutionException`/Spring `TaskRejectedException` ngay |

**Phân biệt:** `CompletableFuture.completedFuture(value)` không nộp task chạy bất đồng bộ; nó tạo future **đã hoàn tất**. `CompletableFuture.supplyAsync(...)` mới lập lịch `Supplier` chạy theo một executor. Một `CompletableFuture` tồn tại không tự chứng minh task được chạy ở thread khác.

## 2. ForkJoinPool.commonPool là gì?

`ForkJoinPool` là executor của JDK được thiết kế để hỗ trợ chia nhỏ và xử lý nhiều công việc song song, có cơ chế work-stealing. `ForkJoinPool.commonPool()` là một **instance dùng chung** trong quá trình chạy JVM. Nhiều thành phần gọi các API bất đồng bộ không chỉ định executor có thể đồng thời đưa công việc vào đây.

~~~text
Module báo cáo                Module thông báo             Thư viện khác
       |                            |                           |
       | supplyAsync(supplier)      | runAsync(runnable)        | thenApplyAsync(fn)
       v                            v                           v
       +----------------------------+---------------------------+
                                    |
                             commonPool (JVM)
                                    |
                  workers thực thi nhiều loại task chung
                                    |
                 một task chậm có thể ảnh hưởng task khác
~~~

Điều này không nghĩa rằng mọi task trong common pool **luôn chạy tuần tự**: pool có nhiều workers và work-stealing. Vấn đề là **chia sẻ giới hạn tài nguyên** và khó cô lập, dự đoán độ trễ khi các module có tải khác nhau.

### Vì sao dùng chung đôi khi vẫn hợp lý?

Với một lượng nhỏ các tính toán **CPU-bound**, ngắn, độc lập, không yêu cầu pool riêng và không chứa I/O chặn lâu, common pool có thể là lựa chọn đơn giản. JDK đã thiết kế `CompletableFuture` để người dùng không nhất thiết tự tạo executor cho mọi tác vụ.

Nhưng ở một dịch vụ production, hãy đánh giá **khối lượng công việc, mức chặn, khả năng cùng tồn tại với các module khác, SLA và yêu cầu điều phối**. Không có quy tắc rằng mọi `supplyAsync` đều nguy hiểm; rủi ro xuất hiện khi mặc định dùng tài nguyên chung **mà không cân nhắc tải và trách nhiệm**.

### Kiểm tra common pool, không đoán từ số lõi

~~~java
int parallelism = ForkJoinPool.getCommonPoolParallelism();
System.out.println("commonParallelism=" + parallelism);
~~~

Đây là fragment cần import `java.util.concurrent.ForkJoinPool`. Kết quả phụ thuộc cấu hình JVM và môi trường, vì vậy không có stdout số cố định. `getCommonPoolParallelism()` trả **mức song song mục tiêu**, không phải số thread đang chạy ở thời điểm đó và không phải con số tối đa mọi task ứng dụng có thể đồng thời hoàn tất.

Một số tham số hệ thống ảnh hưởng common pool, chẳng hạn `java.util.concurrent.ForkJoinPool.common.parallelism`. Nhưng đây là **điều chỉnh phạm vi JVM** và có thể ảnh hưởng các thư viện khác; đừng dùng việc tăng tham số toàn cục làm cách xử lý đầu tiên cho một workload cần cô lập.

## 3. Rủi ro khi không truyền Executor

### 3.1. Blocking I/O có thể chiếm các worker chung

Giả sử `Supplier` gọi HTTP, JDBC hoặc một SDK đồng bộ:

~~~java
// Phản ví dụ kiến trúc: client.fetch(id) là lời gọi chặn.
// Mã chỉ minh họa, không phải chương trình độc lập.
CompletableFuture<String> result =
        CompletableFuture.supplyAsync(() -> client.fetch(id));
~~~

Nếu lời gọi `fetch` chặn lâu, worker thực thi nó bị giữ trong thời gian đó. Khi nhiều task tương tự được nộp vào common pool, việc xử lý các tác vụ CPU-bound ngắn khác có thể bị **trễ**. `ForkJoinPool` có những cơ chế hỗ trợ một số tình huống blocking (ví dụ managed blocking), nên không nên khẳng định nó **không bao giờ bù worker**. Nhưng thư viện HTTP/JDBC thông thường không tự động cung cấp bảo đảm bù tài nguyên theo nhu cầu nghiệp vụ.

**Cách sửa thông dụng:** truyền executor dành cho I/O có giới hạn concurrency và đo tài nguyên downstream:

~~~java
// ioExecutor là Executor/ThreadPoolTaskExecutor được cấu hình.
CompletableFuture<String> result =
        CompletableFuture.supplyAsync(
                () -> client.fetch(id),
                ioExecutor
        );
~~~

Pool riêng cũng có thể quá tải, vì vậy cần quan tâm `maxPoolSize`, `queueCapacity`, HTTP timeout, connection pool của database và cơ chế từ chối task. Tách executor **không làm một API đồng bộ trở thành non-blocking I/O**; nó chỉ chọn nơi giữ các worker chờ I/O.

### 3.2. Hai module có thể ảnh hưởng nhau mà không biết

Một tác vụ gửi email chậm và một tác vụ tính toán ngắn có thể cùng dùng common pool nếu cả hai sử dụng `supplyAsync(...)` mặc định. Module tính toán không nhìn thấy cấu hình của module email, nhưng vẫn chia sẻ năng lực thực thi. Khi đo latency tăng, developer dễ tập trung sai vào thuật toán của module tính toán.

Tách executor giúp đặt giới hạn và chỉ số riêng: tên thread, active workers, queue depth, rejection rate, task waiting time, p95/p99 latency. Tuy nhiên, ngay cả hai executor riêng vẫn **chia sẻ CPU, bộ nhớ và downstream** của máy; đó là cô lập ở tầng lập lịch, không phải phân vùng tài nguyên tuyệt đối.

### 3.3. “Chạy async” không tương đương không bị chậm

`supplyAsync` trả một `CompletableFuture` sớm, nhưng nếu caller gọi ngay `join()`, caller vẫn chờ kết quả:

~~~java
// Fragment minh họa cách vô tình biến async thành blocking.
String value = CompletableFuture
        .supplyAsync(() -> expensiveOperation())
        .join();
~~~

Không chỉ định executor khiến tác vụ vào cơ chế mặc định; `join()` làm caller đợi. Nếu code thực thi trong request thread, latency của request vẫn có thể kéo dài. Cân nhắc giữ Future và tiếp tục công việc độc lập, hoặc trả `CompletableFuture` lên tầng hỗ trợ asynchronous response nếu framework phù hợp.

### 3.4. Quá nhiều Future không tạo thêm năng lực CPU

Gửi 100.000 `supplyAsync` không biến 8 lõi CPU thành 100.000 lõi. Tác vụ có thể chờ lâu, dùng bộ nhớ và gây áp lực lên CPU/GC. Nếu work là I/O blocking, số worker và khả năng phục vụ phụ thuộc vào executor và downstream. Hãy quản lý concurrency theo **khả năng hệ thống**, không chỉ đếm số future.

Không nên tạo một `newFixedThreadPool` mới **mỗi request** để né common pool: cách đó gây tràn thread, mất khả năng dùng chung pool đúng cách và phức tạp hóa shutdown. Thay vì thế, dùng **executor bean dùng chung theo loại workload**, được tạo một lần trong Spring.

## 4. Bước tiếp theo của CompletableFuture có tự kế thừa executor không?

**Không.** Việc bạn nộp bước đầu bằng `supplyAsync(supplier, ioExecutor)` không tạo ra quy tắc rằng **mọi stage sau này tự động chạy trên `ioExecutor`**.

| Stage | Có truyền Executor? | Thread thực hiện theo hợp đồng |
| --- | --- | --- |
| `thenApply(fn)` | Không có overload Executor (non-async) | Có thể chạy ở thread hoàn tất stage trước hoặc thread gọi completion |
| `thenCompose(fn)` | Non-async | Có thể chạy ở thread hoàn tất hoặc gọi completion |
| `thenApplyAsync(fn)` | Không | Dùng default asynchronous execution facility, thường là common pool |
| `thenApplyAsync(fn, ioExecutor)` | Có | Được gửi tới `ioExecutor` |
| `thenComposeAsync(fn, ioExecutor)` | Có | Phần callback composition được gửi tới `ioExecutor`; future do hàm trả về có lifecycle riêng |
| `handleAsync(fn)` | Không | Dùng default async execution facility |
| `handleAsync(fn, ioExecutor)` | Có | Được gửi tới `ioExecutor` |

Ví dụ:

~~~java
// Fragment có biến ioExecutor kiểu Executor.
CompletableFuture<String> first =
        CompletableFuture.supplyAsync(() -> "spring", ioExecutor);

// Không cam kết tiếp tục dùng ioExecutor:
CompletableFuture<String> second =
        first.thenApplyAsync(String::toUpperCase);

// Yêu cầu stage tiếp tục được lên lịch trên ioExecutor:
CompletableFuture<String> third =
        first.thenApplyAsync(String::toUpperCase, ioExecutor);
~~~

**Điểm tinh tế:** `thenApply` **không có chữ Async** có thể chạy ngay tại thread đăng ký callback nếu future đã hoàn tất, hoặc trên thread hoàn tất future, tùy timing. Vì vậy không thiết kế nghiệp vụ dựa vào giả định cố định “`thenApply` chắc chắn ở worker Spring”. Với stage `...Async` mà context, metrics hoặc pool isolation quan trọng, hãy truyền executor tường minh.

Mặt khác, không cần chuyển mọi phép tính nhỏ sang thread khác. Một `thenApply` biến đổi chuỗi rất nhanh thường có chi phí thấp; dùng `thenApplyAsync(..., executor)` khi bạn thật sự muốn **scheduling boundary** và kiểm soát nơi chạy.

## 5. Ví dụ Java 17+ chạy độc lập: mặc định và executor tường minh

Chương trình sau minh họa hai hướng:

- Tác vụ mặc định tạo kết quả `default-ok`; **nơi chạy** được quy định bởi Javadoc `supplyAsync`, không cần gán cứng tên thread.
- Executor riêng cấp tên `business-*`. Cả task đầu và stage `thenApplyAsync(..., businessPool)` đều dùng executor này.
- `Supplier` ném exception; future hoàn tất lỗi và `handle` đọc cause.

Lưu thành `SupplyAsyncExecutorDemo.java`:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SupplyAsyncExecutorDemo {

    public static void main(String[] args) throws Exception {
        AtomicInteger ids = new AtomicInteger();

        ExecutorService businessPool = Executors.newFixedThreadPool(
                2,
                task -> {
                    Thread worker = new Thread(task);
                    worker.setName("business-" + ids.incrementAndGet());
                    return worker;
                }
        );

        try {
            // Không truyền executor: dùng default async facility.
            CompletableFuture<String> implicit =
                    CompletableFuture.supplyAsync(() -> "default-ok");

            // Truyền executor tường minh cho cả task và stage async.
            CompletableFuture<String> explicit =
                    CompletableFuture.supplyAsync(
                            () -> Thread.currentThread().getName(),
                            businessPool
                    ).thenApplyAsync(
                            threadName -> "dedicated="
                                    + threadName.startsWith("business-"),
                            businessPool
                    );

            // Lỗi xảy ra trong Supplier đi vào CompletableFuture.
            CompletableFuture<Integer> failed =
                    CompletableFuture.supplyAsync(() -> {
                        throw new IllegalArgumentException("bad-input");
                    }, businessPool);

            System.out.println("default=" + implicit.join());
            System.out.println(explicit.join());

            String error = failed.handle((value, throwable) -> {
                if (throwable == null) {
                    return "none";
                }
                Throwable root = throwable instanceof CompletionException
                        ? throwable.getCause() : throwable;
                return root.getClass().getSimpleName()
                        + ":" + root.getMessage();
            }).join();

            System.out.println("error=" + error);
        } finally {
            businessPool.shutdown();
            if (!businessPool.awaitTermination(
                    5, TimeUnit.SECONDS)) {
                businessPool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac SupplyAsyncExecutorDemo.java
java SupplyAsyncExecutorDemo
~~~

Các dòng chương trình in (không tính log công cụ ngoài):

~~~text
default=default-ok
dedicated=true
error=IllegalArgumentException:bad-input
~~~

**Vì sao không in tên thread từ task mặc định?** Vì tên thread, số worker và nhánh fallback khi common pool có mức song song thấp **phụ thuộc JVM/môi trường**. Nếu cần debug, có thể cho supplier trả `Thread.currentThread().getName()` và ghi log **như dữ liệu quan sát**, không assert một chuỗi tên cố định.

**Vì sao `dedicated=true` chắc chắn?** `businessPool` dùng thread factory đặt tên `business-1`, `business-2`, và stage đầu trả về tên của chính thread đó. `thenApplyAsync(..., businessPool)` vẫn được gửi tới cùng pool. Giá trị boolean được tính từ tên thread thu được, không phụ thuộc worker nào chạy stage.

**Vì sao `error` có `IllegalArgumentException`?** Exception trong supplier không được ném đồng bộ về dòng tạo future; future hoàn tất **exceptionally**. `handle` đọc throwable, gỡ lớp `CompletionException` khi có, rồi tạo chuỗi kết quả.

`shutdown()` chỉ áp dụng cho **businessPool tự tạo**. Chương trình **không shutdown common pool toàn JVM**; việc tự đóng common pool không phải biện pháp dọn dẹp phù hợp cho một module ứng dụng.

## 6. Minh họa tranh chấp một executor dùng chung (không tác động common pool)

Để hiểu tình huống “task dài làm task ngắn phải chờ”, ta dùng một executor **chỉ có 1 worker** do ví dụ tự tạo. Đây là **mô hình mô phỏng sự chia sẻ tài nguyên**, không phải khẳng định common pool thực tế cũng luôn chỉ có một worker, hay hoàn toàn giống cách `ForkJoinPool` lên lịch.

Lưu thành `SharedExecutorContentionDemo.java`:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class SharedExecutorContentionDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService shared = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            CompletableFuture<String> slow =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return "interrupted";
                        }
                        return "slow-finished";
                    }, shared);

            started.await(); // worker duy nhất đang giữ task slow

            CompletableFuture<String> quick =
                    CompletableFuture.supplyAsync(
                            () -> "quick-finished", shared);

            System.out.println("quickBeforeRelease=" + quick.isDone());
            release.countDown();
            System.out.println("slow=" + slow.join());
            System.out.println("quick=" + quick.join());
        } finally {
            release.countDown();
            shared.shutdown();
            if (!shared.awaitTermination(5, TimeUnit.SECONDS)) {
                shared.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac SharedExecutorContentionDemo.java
java SharedExecutorContentionDemo
~~~

Đầu ra:

~~~text
quickBeforeRelease=false
slow=slow-finished
quick=quick-finished
~~~

Task slow giữ worker theo latch; quick được gửi vào **cùng executor**, nhưng chưa có worker rảnh, nên chưa thể hoàn tất trước khi main mở latch. Sau đó cả hai task hoàn tất. `quick.isDone()` trước `release.countDown()` là `false` bởi pool có đúng một worker và slow còn đang giữ nó.

Thí nghiệm này **không sử dụng hoặc chỉnh sửa common pool**, tránh việc một bài demo làm chậm những thư viện khác trong cùng JVM. Nó giúp hiểu nguyên lý chia sẻ tài nguyên; còn `ForkJoinPool.commonPool()` có nhiều worker và work-stealing nên cần đánh giá bằng metrics và workload thật, không suy luận tốc độ chính xác từ thí nghiệm một worker.

## 7. Tích hợp Spring: truyền ThreadPoolTaskExecutor tường minh

Spring `ThreadPoolTaskExecutor` vừa đóng vai trò `TaskExecutor`, vừa có thể truyền như một `java.util.concurrent.Executor` cho `CompletableFuture.supplyAsync(supplier, executor)`. Khuyến nghị là cấu hình **bean dùng chung** theo loại workload, ví dụ `ioExecutor` cho các công việc I/O đồng bộ có giới hạn.

### 7.1. Dự án Maven Spring Framework 6.1.3

Ví dụ sau dùng Spring Framework **thuần**, không yêu cầu Spring Boot, `@EnableAsync` hay Spring AOP proxy: gọi trực tiếp `CompletableFuture.supplyAsync` với executor đã được inject. Cấu trúc:

~~~text
spring-supply-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                ├── IoExecutorConfig.java
                ├── DocumentService.java
                └── SpringSupplyAsyncDemo.java
~~~

Tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-supply-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.3</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <mainClass>demo.SpringSupplyAsyncDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Tệp `src/main/java/demo/IoExecutorConfig.java`:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class IoExecutorConfig {

    @Bean("ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(20);
        pool.setThreadNamePrefix("io-");
        return pool; // Spring container quản lý init/shutdown
    }

    @Bean
    public DocumentService documentService(
            @Qualifier("ioExecutor") ThreadPoolTaskExecutor executor) {
        return new DocumentService(executor);
    }
}
~~~

Tệp `src/main/java/demo/DocumentService.java`:

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class DocumentService {

    private final Executor executor;

    public DocumentService(Executor executor) {
        this.executor = executor;
    }

    public CompletableFuture<String> load(String documentId) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isolated = Thread.currentThread()
                    .getName().startsWith("io-");
            return "document:" + documentId + "|isolated=" + isolated;
        }, executor);
    }
}
~~~

Tệp `src/main/java/demo/SpringSupplyAsyncDemo.java`:

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class SpringSupplyAsyncDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             IoExecutorConfig.class)) {

            DocumentService service =
                    context.getBean(DocumentService.class);

            System.out.println(service.load("A42").join());
        }
    }
}
~~~

Chạy trong thư mục `spring-supply-demo`:

~~~bash
mvn -q compile exec:java
~~~

Đầu ra do Java in:

~~~text
document:A42|isolated=true
~~~

**Vì sao ví dụ này chạy qua executor Spring?** Method `DocumentService.load` **truyền trường `executor`** vào overload hai tham số của `supplyAsync`; trường đó được nhận từ bean `ioExecutor` do Spring container quản lý. `ThreadPoolTaskExecutor` tạo worker với tiền tố tên `io-`. Khi supplier chạy, nó kiểm tra chính thread hiện hành và trả `isolated=true`.

Không có `@Async` trên `DocumentService.load`, vì bài này chủ động dùng **Java CompletableFuture API** thay vì proxy-based asynchronous method invocation của Spring. Không cần `@EnableAsync` để gọi trực tiếp `supplyAsync(..., executor)`. `try-with-resources` đóng Spring context, từ đó container dọn executor bean.

**Đừng sao chép thông số pool vào production một cách máy móc.** `core=2`, `max=4`, `queueCapacity=20` chỉ làm rõ khả năng giới hạn số worker và hàng đợi. Cần hiệu chỉnh theo tải, độ dài task, số kết nối downstream, timeout và SLA.

### 7.2. Dùng interface TaskExecutor trong service

`org.springframework.core.task.TaskExecutor` **kế thừa `java.util.concurrent.Executor`**, nên có thể truyền trực tiếp làm tham số thứ hai của `CompletableFuture.supplyAsync`, dù interface Spring chỉ cần `execute(Runnable)`:

~~~java
// Fragment khi đã inject taskExecutor kiểu TaskExecutor:
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> "ok", taskExecutor);
~~~

Nếu biến chỉ có kiểu `TaskExecutor`, bạn không thể gọi `taskExecutor.submitCompletable(...)` vì đây là method của `AsyncTaskExecutor`, nhưng **`supplyAsync(..., taskExecutor)` vẫn hợp lệ** vì chỉ cần `Executor`. Đây là cách hữu ích khi tích hợp với Spring 5.x, nơi `submitCompletable` chưa có.

**Cẩn thận:** Implementation `SyncTaskExecutor` chạy task **ngay trên caller thread**; nếu bạn truyền một executor đồng bộ, future có thể hoàn tất ngay nhưng việc `supplyAsync` đã thực thi đồng bộ. Không suy luận “không chặn caller” chỉ từ kiểu `CompletableFuture`. Kiểm tra implementation thực của bean.

## 8. @Async và supplyAsync không tự chọn executor giống nhau

`@Async` của Spring dùng cơ chế interceptor/proxy khi application context bật `@EnableAsync`. Executor của `@Async` có thể được chọn bởi annotation `@Async("name")`, cấu hình `AsyncConfigurer` hoặc Spring bean resolution phù hợp. Trong khi đó, `CompletableFuture.supplyAsync(supplier)` là lời gọi **static của JDK**, không đọc `@Async` hay Spring configuration.

Một phản ví dụ:

~~~java
// Fragment trong Spring service, giả sử đã bật @EnableAsync:
@Async("springExecutor")
public CompletableFuture<String> calculate() {
    return CompletableFuture.supplyAsync(
            () -> blockingLookup());
}
~~~

Đoạn này **không phải chương trình độc lập**; cần `blockingLookup()` và bean `springExecutor`. Method được Spring `@Async` gửi vào `springExecutor`, **nhưng** ngay trong method lại gọi `supplyAsync` **không truyền executor**. Vì vậy **Supplier bên trong** được gửi tiếp đến common pool (hoặc nhánh fallback của JDK). Việc đã có `@Async("springExecutor")` **không khiến inner supplyAsync tự kế thừa pool Spring**.

Thường nên chọn một trong hai chiến lược rõ ràng:

1. **Dùng `@Async("springExecutor")`:** target method tính toán trong worker Spring rồi trả `CompletableFuture.completedFuture(result)`, để proxy đại diện cho việc chạy bất đồng bộ.
2. **Dùng `CompletableFuture.supplyAsync(supplier, springExecutor)`:** method bình thường (không cần `@Async`) trả future được nộp trực tiếp đến executor lựa chọn.

Nếu thật sự cần cả hai tầng scheduling, giải thích lý do bằng kiến trúc và giới hạn tài nguyên; đừng vô tình tạo **double scheduling** sang common pool rồi đoán sai thread nào đang chạy I/O.

## 9. Thread context: transaction, MDC, SecurityContext, ThreadLocal

Spring thường có dữ liệu gắn với **thread hiện tại** như log MDC, `SecurityContext`, request attributes và transaction context. Chuyển công việc sang common pool không tự động mang tất cả dữ liệu đó theo. Một số framework có cơ chế propagation riêng; tùy version/config, không thể chỉ giả định chúng có sẵn.

Các rủi ro:

- **Log mất correlation ID:** MDC được cài trong request thread nhưng không thấy trong worker, khiến trace phân tán.
- **SecurityContext trống hoặc không đúng:** nếu framework áp dụng per-thread security context và bạn không truyền, kiểm tra quyền trong worker có thể thiếu thông tin.
- **Transaction không trải theo async task:** một transaction gắn với caller thread không tự mở rộng sang worker. Task phải có transaction boundary thích hợp nếu truy cập database.
- **ThreadLocal rò rỉ dữ liệu:** nếu tự gắn context lên pool worker mà không dọn `finally`, worker được tái sử dụng có thể giữ context cũ sang task khác.

Cách áp dụng: **truyền thông tin nghiệp vụ bất biến một cách tường minh** khi có thể; nếu cần context propagation, dùng cơ chế được framework hỗ trợ, `TaskDecorator`, hoặc executor wrapper phù hợp. Với `ThreadPoolTaskExecutor.setTaskDecorator(...)`, chú ý decorator có thể nhận một task wrapper do `CompletableFuture` tạo, không nhất thiết nguyên `Supplier`; test end-to-end việc context được đặt **và dọn** ở đúng lúc.

Không điều chỉnh context của **common pool worker** bằng cách lưu một `ThreadLocal` toàn cục như giải pháp nhanh; worker có thể phục vụ những phần code khác.

## 10. Exception, rejection, timeout và cancellation

### 10.1. Lỗi trong Supplier: Future hoàn tất exceptionally

Nếu `Supplier.get()` ném `IllegalArgumentException` hoặc exception runtime khác **sau khi được executor nhận**, `CompletableFuture` hoàn tất thất bại.

~~~java
CompletableFuture<Integer> failed =
        CompletableFuture.supplyAsync(() -> {
            throw new IllegalArgumentException("invalid");
        }, executor);

// Fragment với executor kiểu Executor đã có.
CompletableFuture<Integer> recovered =
        failed.exceptionally(error -> -1);
~~~

Nếu gọi `failed.join()` mà không phục hồi, Java thường ném `CompletionException` chứa cause gốc; `failed.get()` có thể ném `ExecutionException` (checked). Dùng `handle` hoặc `exceptionally` để chuyển sang giá trị dự phòng khi **nghiệp vụ cho phép**, không tự động nuốt mọi lỗi.

`Supplier<T>` khác `Callable<T>` ở chỗ `Supplier.get()` **không khai báo checked exceptions**. Nếu Supplier thực hiện I/O ném checked exception, bạn phải bắt và chuyển đổi hoặc thiết kế API tương thích; không trực tiếp ném checked exception từ lambda không được khai báo.

### 10.2. Rejection tại thời điểm nộp: chưa có Future

Với `CompletableFuture.supplyAsync(supplier, executor)`, executor được truyền có thể **từ chối** task ngay lúc gọi `execute` bên dưới. Ví dụ khi `ThreadPoolTaskExecutor` đầy hoặc đã shutdown, lời gọi có thể ném **`TaskRejectedException`** (subclass của `RejectedExecutionException`) **trước khi trả Future**. Vì thế `future.exceptionally(...)` không xử lý được lời gọi chưa tạo ra Future.

~~~java
// Fragment trong service có biến ioExecutor kiểu ThreadPoolTaskExecutor.
// Method bao quanh có return type CompletableFuture<String>.
try {
    return CompletableFuture.supplyAsync(
            () -> loadFromRemote(), ioExecutor);
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Quyết định phản hồi quá tải / fallback theo hợp đồng nghiệp vụ.
    return CompletableFuture.failedFuture(ex);
}
~~~

`CompletableFuture.failedFuture(ex)` có từ Java 9+, nên hợp lệ trên Java 17. Ở đây biến rejection đồng bộ thành một future lỗi **có chủ đích** để tầng gọi vẫn có cùng kiểu trả về. Không phải trường hợp nào cũng nên làm vậy: một API HTTP đồng bộ có thể cần fail fast, còn một API trả Future có thể mong một failed future. Quan trọng là **không lờ đi rejection**.

### 10.3. Timeout của Future không tự dừng I/O

~~~java
// Fragment khi đã có future và các import TimeUnit:
future.orTimeout(2, TimeUnit.SECONDS);
~~~

`orTimeout` khiến Future hoàn tất exceptionally nếu không có kết quả đúng hạn, **nhưng không bảo đảm dừng Supplier hoặc network request đã khởi chạy**. `completeOnTimeout` cũng chỉ cung cấp kết quả fallback khi quá hạn; I/O thực sự có thể tiếp tục và gây side effect.

`CompletableFuture.cancel(true)` về hợp đồng chủ yếu là **exceptional completion**; `mayInterruptIfRunning` không có tác dụng ngắt worker trực tiếp như nhiều implementation của `FutureTask`. Để kiểm soát lifetime của I/O, thiết lập timeout của HTTP client, database, queue và cancellation hợp tác ở tầng tài nguyên.

### 10.4. Đừng đợi phụ thuộc ngay trong cùng pool nhỏ

Một task A chạy trên executor một worker, lại gửi task B đến **cùng executor** rồi gọi `B.join()`. A giữ worker duy nhất để đợi B, B nằm trong queue không có worker để chạy → **thread-starvation deadlock**. Đừng dùng `supplyAsync` hoặc common pool như phép màu loại bỏ mọi deadlock. Tách các bước thành `thenCompose`/`thenCombine` hoặc thiết kế executor và ranh giới chờ phù hợp.

## 11. Chọn executor theo workload và đo lường

| Workload / yêu cầu | Hướng xử lý |
| --- | --- |
| Tính toán CPU-bound, ít task, độc lập, ngắn | Common pool có thể chấp nhận nếu không cần isolation; đo trước khi quyết định |
| Tính toán CPU-bound quan trọng, có SLA riêng | Executor riêng với concurrency giới hạn, tránh tranh chấp ngoài ý muốn |
| Blocking I/O: HTTP/JDBC/SDK đồng bộ | Executor I/O riêng có giới hạn, timeout thực tế ở downstream |
| Nhiều request/tenant có mức ưu tiên khác nhau | Cân nhắc pool/rate limit/semaphore/queue theo workload và trách nhiệm |
| Công việc rất ngắn, stage chỉ biến đổi giá trị | `thenApply` có thể đủ, không nhất thiết reschedule |
| Stage async cần context hoặc pool cụ thể | `thenApplyAsync(fn, executor)`, `handleAsync(fn, executor)`, v.v. |
| Spring `@Async` dùng executor đã cấu hình | Gọi method qua proxy; đừng mặc định inner `supplyAsync` kế thừa pool |
| Task có thể không được mất | Durable queue/outbox và acknowledgment; Future/ThreadPool không phải hàng đợi bền vững |

### Giám sát

- Với Spring `ThreadPoolTaskExecutor`, theo dõi `getPoolSize()`, `getActiveCount()` và `getQueueSize()` theo thời gian, không chỉ một snapshot.
- Đo **thời gian chờ vào queue**, thời gian chạy task, throughput, rejection rate, timeout và p95/p99 latency.
- Với common pool, có thể tham khảo `ForkJoinPool.commonPool().getPoolSize()`, `getActiveThreadCount()`, `getQueuedSubmissionCount()` và `getStealCount()`. Đây là **số đo ước lượng/quan sát**, không có nghĩa bạn sở hữu pool hoặc có thể quy mọi task về một module riêng.
- Gắn tên executor và correlation ID để trace khi dùng pool riêng; tránh log tên thread làm test bắt buộc vì runtime có thể thay đổi thread.
- Thử nghiệm dưới lưu lượng thực tế, gồm **task dài, I/O chậm, shutdown, rejection**, trước khi thay đổi parallelism của common pool toàn cục.

### Checklist code review

1. Tìm `CompletableFuture.supplyAsync(...)` và `runAsync(...)` không có đối số Executor.
2. Với mỗi call, phân loại CPU-bound / I/O-bound / có chặn / độ dài task / SLA.
3. Xác định rõ **owner** của executor, cách khởi tạo, giới hạn worker/queue và shutdown.
4. Chuyển các tác vụ cần isolation sang overload có `Executor`; trong Spring dùng bean dùng chung đã cấu hình.
5. Rà soát **mọi stage `...Async`**: `thenApplyAsync(fn)` không tự kế thừa pool bước trước. Truyền executor khi cần.
6. Bảo đảm context/transaction/security propagation và cleanup phù hợp; không dựa vào `ThreadLocal` của caller.
7. Xử lý riêng exception **bên trong Supplier** và rejection **tại thời điểm submit**.
8. Đặt timeout cho I/O thật; không dùng `orTimeout`/`cancel` làm bằng chứng ngừng tác vụ bên dưới.
9. Không `join` task con ngay trong một worker thuộc pool nhỏ đang chạy task cha.
10. Dùng monitoring và load test để xác nhận giới hạn của executor; đừng chỉ tăng số worker khi hệ thống đang nghẽn downstream.

## 12. Sai lầm thường gặp

**“Có `@Async` hoặc `ThreadPoolTaskExecutor` bean thì `supplyAsync(supplier)` tự chọn Spring pool.”** Sai: lời gọi static của JDK không tự truy vấn Spring container.

**“Tạo một CompletableFuture là đã có một thread mới.”** Sai: thường là một tác vụ được executor thực thi, worker được tái sử dụng. JDK có nhánh fallback tạo thread mới khi parallelism common pool không đủ 2, nhưng không phải hành vi chung cần dựa vào.

**“Task đầu dùng ioExecutor thì `thenApplyAsync(fn)` cũng dùng ioExecutor.”** Sai: stage Async không executor dùng default async facility, thường common pool.

**“Common pool luôn không thể chạy blocking task.”** Sai: có thể chạy, nhưng dùng chung và nguy cơ ảnh hưởng throughput/latency; cần đánh giá cơ chế blocking và giới hạn tài nguyên.

**“Dùng `join()` không chặn vì CompletableFuture là async.”** Sai: `join()` có thể chặn caller đến khi future hoàn tất.

**“Tăng common pool parallelism là sửa được mọi nghẽn.”** Sai: đây là cấu hình toàn JVM, không giải quyết giới hạn DB, HTTP, memory hay thiết kế task.

**“Bật timeout trên CompletableFuture sẽ ngắt HTTP request.”** Sai: timeout của future không bảo đảm ngắt hoạt động I/O bên dưới.

**“`exceptionally(...)` sẽ bắt mọi lỗi, kể cả executor từ chối trước khi trả future.”** Sai: rejection có thể được ném đồng bộ trước khi có future.

**“Truyền một TaskExecutor bất kỳ luôn khiến công việc chạy background.”** Sai: `SyncTaskExecutor` có thể chạy ngay trên caller thread.

**“Mỗi request tự tạo một thread pool riêng sẽ cô lập tốt hơn.”** Sai trong phần lớn ứng dụng web: quá nhiều thread/pool và thiếu lifecycle còn gây rủi ro tài nguyên cao hơn.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `CompletableFuture.supplyAsync(() -> load())` trong ứng dụng có bean Spring `ioExecutor` sẽ chạy ở đâu?  
**Trả lời:** Theo default async facility của JDK (common pool khi hỗ trợ parallelism ít nhất 2, nếu không dùng thread mới cho từng task); không tự chọn `ioExecutor`.

**Câu 2.** Muốn `load()` chạy trên Spring `ThreadPoolTaskExecutor` thì sửa thế nào?  
**Trả lời:** Truyền chính bean executor làm đối số thứ hai: `CompletableFuture.supplyAsync(() -> load(), ioExecutor)`.

**Câu 3.** Vì sao `thenApplyAsync(fn)` đặt sau `supplyAsync(supplier, ioExecutor)` có thể quay lại common pool?  
**Trả lời:** Stage `thenApplyAsync` không truyền executor sẽ dùng default async facility; executor truyền ở stage trước không tự kế thừa.

**Câu 4.** `thenApply(fn)` có cam kết chạy ở ioExecutor không?  
**Trả lời:** Không. Non-async completion có thể chạy ở thread hoàn tất future hoặc một thread gọi completion, phụ thuộc timing.

**Câu 5.** Vì sao việc đưa hàng nghìn lời gọi HTTP đồng bộ vào common pool có thể gây vấn đề?  
**Trả lời:** Worker chung có thể bị giữ khi I/O chặn, làm những task khác chia sẻ tài nguyên phải chờ lâu; khó cô lập, định hạn và giám sát theo workload.

**Câu 6.** `CompletableFuture.supplyAsync(supplier, ioExecutor)` bị pool từ chối có thể được `future.exceptionally(...)` bắt không?  
**Trả lời:** Không nếu chính lời gọi gửi ném exception trước khi trả future; catch tại điểm gọi rồi quyết định fail fast hoặc tạo failed future.

**Câu 7.** `orTimeout(2, SECONDS)` có bảo đảm ngắt query DB sau 2 giây?  
**Trả lời:** Không. Nó thay trạng thái future theo timeout, còn DB query cần timeout/cancellation ở tầng JDBC/database.

**Câu 8.** Khi nào dùng common pool có thể chấp nhận được?  
**Trả lời:** Với công việc nhỏ, CPU-bound, ngắn, không blocking, không đòi hỏi isolation và đã đo được tương tác với tải khác.

## Tài liệu tham khảo chính thức

- [Java SE 17 — CompletableFuture Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): overload `supplyAsync`, chính sách default async facility, `thenApplyAsync`, lỗi, timeout và cancellation.
- [Java SE 17 — ForkJoinPool Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ForkJoinPool.html): common pool, parallelism, work-stealing, managed blocking và metrics.
- [Java SE 17 — Executor Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html): contract `execute(Runnable)` và sự khác biệt giữa các implementation.
- [Spring Framework 6.1.3 — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): executor bean, core/max/queue, TaskDecorator, quản lý vòng đời và rejection.
- [Spring Framework 6.1.3 — AsyncTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html): `submit`/`submitCompletable` của Spring và interface kế thừa từ `TaskExecutor`.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): các `TaskExecutor` implementation, `@Async`, chọn executor và giới hạn ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-009">Tránh gửi tác vụ hai lần khi vừa dùng @Async vừa dùng supplyAsync()</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh gửi tác vụ hai lần khi vừa dùng `@Async` vừa dùng `supplyAsync()`

**Module:** Spring Framework - concurrency  
**Nhóm kiến thức:** Kết hợp CompletableFuture  
**Mục tiêu:** Phân biệt hai cơ chế lên lịch `@Async` và `CompletableFuture.supplyAsync()`, tránh tạo tác vụ bao ngoài chỉ để chờ tác vụ bên trong, chọn đúng executor và vẫn ghép các bước xử lý bằng `CompletableFuture` một cách có kiểm soát.

## 1. Vì sao dùng hai API bất đồng bộ cùng lúc có thể là phản tác dụng?

Xem một Spring service đã bật `@EnableAsync`:

```java
@Async("businessExecutor")
public CompletableFuture<String> create(String id) {
    return CompletableFuture.supplyAsync(
            () -> createReportBlocking(id));
}
```

Đây là **đoạn minh họa**, chưa phải chương trình độc lập: `createReportBlocking` và executor bean `"businessExecutor"` phải tồn tại. Một lời gọi qua Spring proxy ở đây có **hai lần giao tác vụ**:

1. Spring AOP intercept `@Async("businessExecutor")` và submit **method invocation** vào `businessExecutor`.
2. Khi một worker của pool đó chạy method, `CompletableFuture.supplyAsync(...)` tiếp tục submit **supplier** sang executor mặc định của `CompletableFuture`, thường là `ForkJoinPool.commonPool()`.

```text
Caller / HTTP thread
      |
      | gọi Spring proxy
      v
@Async("businessExecutor")
      |
      | SUBMIT #1
      v
Worker của businessExecutor (OUTER)
      |
      | body gọi supplyAsync
      | SUBMIT #2
      v
Worker của commonPool (INNER)
      |
      | chạy createReportBlocking(id)
      v
Future bên trong hoàn thành
      |
      v
Future do Spring proxy cung cấp hoàn thành
```

**Hai task submissions không có nghĩa nghiệp vụ bị chạy hai lần.** Trong ví dụ, supplier `createReportBlocking(id)` thường vẫn chỉ chạy **một lần cho mỗi invocation được chấp nhận**. Lãng phí nằm ở **hai lần scheduling**, nhiều queue/thread và khả năng có một worker ngoài đang chờ worker trong.

Ở Spring Framework **6.x**, `AsyncExecutionInterceptor` thực thi method đích trong một task của executor ngoài. Nếu method đích trả `Future`, interceptor có thể gọi `Future.get()` để lấy giá trị khi thực hiện task ngoài. Do đó, nếu inner future chưa hoàn tất, **outer worker có thể bị giữ để chờ**. Nó không tương đương với `thenCompose` phi chặn.

Hệ quả:

- Worker ngoài bận nhưng không làm công việc hữu ích, nên throughput có thể giảm.
- Tác vụ blocking I/O vô tình chạy trên `ForkJoinPool.commonPool()` thay vì I/O pool đã giới hạn.
- Trace/MDC và các `ThreadLocal` có thể mất qua ranh giới thứ hai.
- Lỗi/rejection/timeout phải được hiểu ở **hai** tầng.
- Nếu cả hai submit cùng một pool ít thread, có thể xảy ra **starvation hoặc deadlock**.

Trước khi thêm `supplyAsync` vào một method đã `@Async`, hãy hỏi: **“Tầng outer thực sự làm công việc gì, hay chỉ tạo Future khác rồi đợi?”**

## 2. Ba cơ chế thường bị nhầm

| Cách viết | Có submit task mới? | Ai chọn executor? | Tác dụng |
| --- | --- | --- | --- |
| `@Async("pool")` | Có, nếu lời gọi đi qua proxy async | Spring `@Async`/qualifier | Chuyển lời gọi method sang pool |
| `CompletableFuture.completedFuture(x)` | **Không** | Không cần executor | Tạo Future đã có kết quả `x` |
| `CompletableFuture.supplyAsync(fn)` | Có | Thường `ForkJoinPool.commonPool()` của JDK | Giao supplier cho executor mặc định JDK |
| `CompletableFuture.supplyAsync(fn, executor)` | Có | Executor được truyền vào | Giao supplier vào pool chỉ định |
| `thenApply(fn)` | Không mặc định gửi tác vụ riêng | Completion thread hoặc thread đăng ký callback | Biến đổi giá trị đã có |
| `thenApplyAsync(fn, executor)` | Có, cho callback | Executor chỉ định | Dời biến đổi nặng sang pool |
| `thenCompose(fn)` | Không tự thêm worker chỉ để chờ | Theo Future mà `fn` trả về | Nối một công việc đã có async contract |

`@Async` không phải tên khác của `CompletableFuture.supplyAsync`. `@Async` là **Spring proxy advice**; `supplyAsync` là **API JDK**. Việc Spring có bean tên `taskExecutor`, `applicationTaskExecutor` hay implement `AsyncConfigurer` **không** tự động thay executor của lệnh static `CompletableFuture.supplyAsync` không truyền tham số.

`completedFuture` cũng không tự làm công việc “chạy nền”. Nó chỉ phù hợp trong một method **đã được `@Async` proxy chuyển lên worker**, hoặc khi kết quả thực sự đã sẵn sàng.

## 3. Mẫu A — Một lần scheduling do Spring quản lý

Khi method của service thực hiện công việc đồng bộ/blocking, nhưng caller muốn nhận Future, để **Spring làm chủ bước chuyển thread**:

```java
@Service
public class ReportService {

    @Async("reportExecutor")
    public CompletableFuture<String> create(String reportId) {
        String result = createReportBlocking(reportId);
        return CompletableFuture.completedFuture(result);
    }
}
```

Đây là **class minh họa**; cần imports của Spring, `CompletableFuture`, method `createReportBlocking` và bean `"reportExecutor"`. Luồng:

```text
caller -> @Async proxy -> reportExecutor worker
                                   |
                                   | createReportBlocking(reportId)
                                   v
                       completedFuture(result)
                                   |
                                   v
                         caller's Future
```

**Chỉ một lần submit.** `createReportBlocking()` chạy trực tiếp trong worker do Spring chọn, và `completedFuture` không tạo task thứ hai.

Theo Javadoc Spring `@Async`, khi target method khai báo return type là `CompletableFuture<T>`, target có thể trả một **temporary future** đã chứa kết quả tính được (thường `CompletableFuture.completedFuture(result)`). Caller sẽ nhận một **actual asynchronous Future** do proxy Spring cung cấp. Hai đối tượng Future không bắt buộc là cùng một object.

Nếu method ném exception, Future phía caller hoàn thành lỗi. Nếu executor từ chối ngay lúc giao invocation, caller có thể gặp `TaskRejectedException` ở ranh giới submit. Lỗi business sau khi task đã nhận và lỗi không nhận task là **hai tình huống khác nhau**.

**Phù hợp:** service hiện có hàm tính toán, JDBC/HTTP client đồng bộ, thao tác tốn CPU được giao vào executor phù hợp. Các blocking operation vẫn phải có timeout, connection limit và chống overload; `@Async` không làm chúng trở thành non-blocking I/O.

## 4. Mẫu B — Không `@Async`, giao một supplier bằng JDK API

Nếu codebase tổ chức các bước bằng `CompletableFuture` thay vì annotation, để **method bình thường** trả Future:

```java
@Service
public class ReportWorkflow {

    private final Executor reportExecutor;

    public ReportWorkflow(
            @Qualifier("reportExecutor") Executor reportExecutor) {
        this.reportExecutor = reportExecutor;
    }

    public CompletableFuture<String> create(String reportId) {
        return CompletableFuture.supplyAsync(
                () -> createReportBlocking(reportId),
                reportExecutor);
    }
}
```

Đây là class minh họa: `createReportBlocking` chưa định nghĩa; cần imports `@Service`, `@Qualifier`, `Executor`, `CompletableFuture`. Luồng:

```text
caller -> create(...)  [không có @Async]
               |
               | supplyAsync(..., reportExecutor)
               v
        reportExecutor worker
               |
               v
        createReportBlocking(...)
```

**Cũng chỉ một lần submit.** Method `create` được gọi ngay trên thread caller nhưng chỉ tạo một future và gửi supplier, nên thường trả nhanh. Tác vụ business chạy trên worker.

Có thể chọn mẫu A hoặc B tùy kiến trúc. Điểm quan trọng là **không vô tình kết hợp cả hai mà không có công việc hữu ích ở tầng ngoài**.

Nếu một method gọi `CompletableFuture.supplyAsync` **không truyền executor**, việc đó thường gửi sang `ForkJoinPool.commonPool()` với một số fallback tùy điều kiện parallelism; nó **không** tự sử dụng `@Async("reportExecutor")` hay một executor bean được Spring quản lý. Với blocking JDBC, HTTP đồng bộ hoặc tác vụ có yêu cầu isolation, nên chọn executor tường minh, queue hữu hạn và giới hạn downstream.

## 5. Mẫu C — Dữ liệu đã bất đồng bộ: ghép các Future, không tạo outer worker

Giả sử hai client trả `CompletableFuture<Integer>` từ trước:

```java
@Service
public class QuoteService {

    private final PriceClient priceClient;
    private final ShippingClient shippingClient;

    public QuoteService(
            PriceClient priceClient,
            ShippingClient shippingClient) {
        this.priceClient = priceClient;
        this.shippingClient = shippingClient;
    }

    public CompletableFuture<Integer> quote(
            String sku, String address) {
        CompletableFuture<Integer> price =
                priceClient.fetchAsync(sku);
        CompletableFuture<Integer> shipping =
                shippingClient.fetchAsync(address);

        return price.thenCombine(shipping, Integer::sum);
    }
}
```

Đoạn trên là class **minh họa**: `PriceClient`/`ShippingClient` là dependency của ứng dụng. Hai lời gọi client có thể là non-blocking I/O thực, hoặc tự có pool của chúng. `QuoteService.quote` không cần annotation `@Async` chỉ vì return type là `CompletableFuture`: nó đã có async contract từ hai nguồn.

Khi B cần kết quả A:

```java
public CompletableFuture<Receipt> submitOrder(String id) {
    return priceClient.fetchAsync(id)
            .thenCompose(price -> receiptClient.createAsync(id, price));
}
```

Đoạn minh họa cần `Receipt`, `receiptClient` và `priceClient`. `thenCompose` ghép một `CompletionStage` do callback trả về mà không viết `.get()`/`.join()` để chặn thread trong continuation.

**Không nhầm:** `thenCompose` không làm công việc B tự chạy nếu `receiptClient.createAsync` không thực sự bắt đầu nó. Nó chỉ diễn đạt quan hệ phụ thuộc và làm phẳng Future thay vì tạo `CompletableFuture<CompletableFuture<T>>`.

## 6. Vì sao `@Async` và `supplyAsync` cùng một pool có thể gây deadlock?

Ví dụ cố tình sai, không dùng trong production:

```java
@Bean("tinyPool")
public ThreadPoolTaskExecutor tinyPool() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(1);
    e.setMaxPoolSize(1);
    e.setQueueCapacity(10);
    return e;
}

@Async("tinyPool")
public CompletableFuture<String> dangerous() {
    return CompletableFuture.supplyAsync(
            () -> slowBusinessWork(),
            tinyPool);
}
```

Đây là **hai đoạn minh họa** thuộc các class Spring phù hợp, không phải một file biên dịch độc lập; cần bean/field `tinyPool` và method `slowBusinessWork`. Nếu lời gọi đi qua Spring async proxy trong **Spring Framework 6.x**, luồng có thể là:

1. Proxy submit tác vụ **OUTER** vào `tinyPool`.
2. Worker duy nhất chạy method đích và gọi `supplyAsync(..., tinyPool)`.
3. Tác vụ **INNER** vào queue vì worker đang bận.
4. Method đích trả `CompletableFuture<String>` của inner.
5. Async interceptor đợi `Future.get()` ở worker OUTER để lấy giá trị.
6. INNER cần worker để chạy, nhưng worker duy nhất vẫn đang chờ INNER.

```text
tinyPool:
  worker-1: OUTER -> Future.get(INNER) -> chờ...
  queue:    INNER -> đang đợi worker-1
```

Kết quả có thể là **starvation/deadlock** không có tiến triển. Với 2, 4, 8 worker, lỗi có thể chỉ xuất hiện khi số outer task đồng thời đủ lớn chiếm hết worker, còn tất cả inner task nằm ở queue. Vì vậy chạy một request thành công không chứng minh thiết kế an toàn.

Tăng `maxPoolSize` để che lỗi không phải phương án nền tảng: khi burst lớn hơn giới hạn mới, triệu chứng có thể quay lại. `Future.get(timeout)` ở caller cũng không bảo đảm giải phóng worker mà Spring interceptor đang dùng để đợi.

**Sửa:** loại bỏ một tầng. Nếu `slowBusinessWork()` thực sự đồng bộ, dùng `@Async + completedFuture`. Nếu muốn tự lên lịch bằng `supplyAsync`, bỏ `@Async` khỏi method.

## 7. Java 17 chạy độc lập: đếm scheduling và số lần xử lý thật

Chương trình này chỉ dùng JDK, giúp phân biệt “hai lần submit” với “nghiệp vụ chạy hai lần”. Nó dùng **hai executor khác nhau** để tránh deadlock trong ví dụ. Ở đường double, outer worker **chủ động gọi `join()`** để mô phỏng một task ngoài chờ task trong; đây là **mô hình minh họa**, không phải trình giả lập chính xác toàn bộ Spring AOP.

Lưu thành file **`DoubleSchedulingJdkDemo.java`**:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleSchedulingJdkDemo {

    static final class CountingExecutor implements Executor, AutoCloseable {
        private final ExecutorService delegate =
                Executors.newSingleThreadExecutor();
        private final AtomicInteger submissions = new AtomicInteger();

        @Override
        public void execute(Runnable task) {
            submissions.incrementAndGet();
            delegate.execute(task);
        }

        int count() {
            return submissions.get();
        }

        @Override
        public void close() throws InterruptedException {
            delegate.shutdown();
            if (!delegate.awaitTermination(5, TimeUnit.SECONDS)) {
                delegate.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (CountingExecutor outer = new CountingExecutor();
             CountingExecutor inner = new CountingExecutor()) {

            AtomicInteger singleRuns = new AtomicInteger();
            AtomicInteger doubleRuns = new AtomicInteger();

            CompletableFuture<String> single =
                    CompletableFuture.supplyAsync(() -> {
                        singleRuns.incrementAndGet();
                        return "done";
                    }, outer);

            CompletableFuture<String> doubleScheduled =
                    CompletableFuture.supplyAsync(() ->
                            CompletableFuture.supplyAsync(() -> {
                                doubleRuns.incrementAndGet();
                                return "done";
                            }, inner).join(), outer);

            System.out.println("single=" + single.join());
            System.out.println("double=" + doubleScheduled.join());
            System.out.println("singleRuns=" + singleRuns.get());
            System.out.println("doubleRuns=" + doubleRuns.get());
            System.out.println("outerSubmissions=" + outer.count());
            System.out.println("innerSubmissions=" + inner.count());
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac DoubleSchedulingJdkDemo.java
java DoubleSchedulingJdkDemo
```

**Stdout chính xác khi chương trình hoàn tất bình thường:**

```text
single=done
double=done
singleRuns=1
doubleRuns=1
outerSubmissions=2
innerSubmissions=1
```

Giải thích:

- Có **hai logical requests** trong cùng chương trình: `single` và `doubleScheduled`.
- Mỗi request gọi nghiệp vụ đúng một lần (`singleRuns=1`, `doubleRuns=1`).
- Outer executor nhận **hai** task — một cho mỗi logical request.
- Inner executor nhận **một** task chỉ thuộc đường `doubleScheduled`.
- Tổng số task được gửi là **ba**, dù chỉ có hai lần thực thi business work. Double scheduling ở request thứ hai không đồng nghĩa duplicate side effect.

`CompletableFuture.join()` ở `main` chỉ chờ kết quả cuối để in kiểm thử. `join()` **bên trong outer task** thuộc đường double mới là điểm mô phỏng việc chiếm worker để chờ công việc trên pool khác.

Lưu ý `Executors.newSingleThreadExecutor()` có queue bộ nhớ không giới hạn theo công suất xử lý; đây chỉ là một ví dụ ngắn, không phải mẫu executor cho production chịu tải lớn.

## 8. Ví dụ Spring Boot hoàn chỉnh: một tầng, hai tầng, và pipeline thuần Future

Ví dụ dùng **Java 17+**, **Spring Boot 3.5.7** với Spring Framework 6.2.x và Maven, gồm hai `ThreadPoolTaskExecutor` độc lập để quan sát ranh giới:

- `outerExecutor` có thread prefix `outer-`.
- `innerExecutor` có thread prefix `inner-`.
- Method `singleDispatch` dùng `@Async` + `completedFuture`.
- Method `doubleDispatch` cố tình dùng `@Async` + `supplyAsync` để minh họa hai tầng, **không khuyến nghị dùng trong production**.
- Method `composeWithoutOuterAsync` không có `@Async`; nó gửi hai supplier độc lập rồi ghép bằng `thenCombine`.

### 8.1. Cấu trúc dự án

```text
double-dispatch-spring/
├── pom.xml
└── src/main/java/com/example/doubledispatch/
    ├── DoubleDispatchApplication.java
    ├── ExecutorConfiguration.java
    └── JobService.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>double-dispatch-spring</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-boot-starter-aop` mang theo khả năng proxy/AOP cần cho ví dụ. Đây là chương trình CLI, không cần web server.

### 8.3. `src/main/java/com/example/doubledispatch/ExecutorConfiguration.java`

```java
package com.example.doubledispatch;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ExecutorConfiguration {

    @Bean("outerExecutor")
    public ThreadPoolTaskExecutor outerExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(2);
        e.setQueueCapacity(8);
        e.setThreadNamePrefix("outer-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return e;
    }

    @Bean("innerExecutor")
    public ThreadPoolTaskExecutor innerExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(2);
        e.setQueueCapacity(8);
        e.setThreadNamePrefix("inner-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return e;
    }
}
```

Ở đây hai pool được tách để thử nghiệm an toàn; production chỉ tạo pool thứ hai khi có loại workload khác thực sự cần tách. `ThreadPoolTaskExecutor` do Spring quản lý lifecycle qua bean; queue hữu hạn và `AbortPolicy` giúp biết khi quá tải.

### 8.4. `src/main/java/com/example/doubledispatch/JobService.java`

```java
package com.example.doubledispatch;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    private final Executor innerExecutor;

    public JobService(@Qualifier("innerExecutor") Executor innerExecutor) {
        this.innerExecutor = innerExecutor;
    }

    // Một lần submit: business xử lý trong worker của Spring.
    @Async("outerExecutor")
    public CompletableFuture<String> singleDispatch(String id) {
        boolean onOuter =
                Thread.currentThread().getName().startsWith("outer-");
        String value = onOuter ? "outer-" + id : "unexpected";
        return CompletableFuture.completedFuture(value);
    }

    // Cố tình minh họa anti-pattern hai lần submit.
    @Async("outerExecutor")
    public CompletableFuture<String> doubleDispatch(String id) {
        boolean onOuter =
                Thread.currentThread().getName().startsWith("outer-");

        return CompletableFuture.supplyAsync(() -> {
            boolean onInner =
                    Thread.currentThread().getName().startsWith("inner-");
            return onOuter && onInner
                    ? "outer+inner-" + id
                    : "unexpected";
        }, innerExecutor);
    }

    // Không @Async: tự xây pipeline JDK, không có outer task.
    public CompletableFuture<Integer> composeWithoutOuterAsync() {
        CompletableFuture<Integer> price =
                CompletableFuture.supplyAsync(
                        () -> 100, innerExecutor);

        CompletableFuture<Integer> shipping =
                CompletableFuture.supplyAsync(
                        () -> 25, innerExecutor);

        return price.thenCombine(shipping, Integer::sum);
    }
}
```

- Với `singleDispatch`, caller nhận Future từ proxy sau khi Spring đã giao invocation vào pool `outer-`. Trong body không có thêm `supplyAsync`.
- Với `doubleDispatch`, Spring giao invocation vào `outer-`; body lại giao supplier vào `inner-`. Theo Spring Framework 6.x, interceptor có thể chờ inner Future trước khi hoàn thành Future trả cho caller. Đây là ví dụ **để thấy thiết kế có hai tầng**, không phải khuyến nghị.
- Với `composeWithoutOuterAsync`, method bình thường bắt đầu hai tác vụ JDK vào `inner-`, sau đó dùng `thenCombine`. Orchestrator không `join()` và không cần một worker bao ngoài chỉ để chờ.

### 8.5. `src/main/java/com/example/doubledispatch/DoubleDispatchApplication.java`

```java
package com.example.doubledispatch;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DoubleDispatchApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication app =
                new SpringApplication(DoubleDispatchApplication.class);

        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of("logging.level.root", "OFF"));

        try (ConfigurableApplicationContext context = app.run(args)) {
            JobService jobs = context.getBean(JobService.class);

            System.out.println("single=" +
                    jobs.singleDispatch("A").get(5, TimeUnit.SECONDS));

            System.out.println("double=" +
                    jobs.doubleDispatch("B").get(5, TimeUnit.SECONDS));

            System.out.println("composed=" +
                    jobs.composeWithoutOuterAsync().get(
                            5, TimeUnit.SECONDS));
        }
    }
}
```

Chạy tại thư mục có `pom.xml`:

```bash
mvn -q spring-boot:run
```

**Ba dòng do `System.out` chủ động in khi demo chạy bình thường:**

```text
single=outer-A
double=outer+inner-B
composed=125
```

`single=outer-A` cho thấy body `@Async` ở pool ngoài. `double=outer+inner-B` cho thấy body bắt đầu ở pool ngoài, supplier ở pool trong. `composed=125` cho thấy hai Future độc lập được ghép mà không có tầng `@Async` bao ngoài. `main` cố tình chờ bằng `get(timeout)` để có output xác định; cách kiểm thử CLI này không phải mẫu viết controller HTTP không chặn.

Nhận diện pool theo **thread name** chỉ nên dùng ở ví dụ và khi debug, không phải điều kiện nghiệp vụ đáng tin. Giá trị core/max/queue ở đây được chọn để demo; cần load test và giới hạn CPU, JDBC/HTTP connections khi dùng thực tế.

## 9. `@Async`, Spring proxy và self-invocation

`@Async` ở chế độ proxy mặc định chỉ có hiệu lực khi lời gọi đi qua Spring-managed proxy. Hai tình huống cần tách bạch:

**Double dispatch:** lời gọi qua proxy, Spring submit một lần, body `supplyAsync` submit thêm lần nữa.

**Không có dispatch từ Spring:** lời gọi `this.doAsync()` trong cùng bean, đối tượng tự tạo bằng `new`, hoặc chưa bật `@EnableAsync`; annotation có thể không được intercept.

Ví dụ:

```java
@Service
public class ServiceA {

    public CompletableFuture<String> caller() {
        return this.worker(); // self-invocation, bỏ qua proxy @Async
    }

    @Async("businessExecutor")
    public CompletableFuture<String> worker() {
        return CompletableFuture.completedFuture(blockingWork());
    }
}
```

Đoạn class minh họa cần imports và method `blockingWork`. Khi gọi `caller()`, `this.worker()` có thể chạy **trực tiếp trên thread caller**, dù return type là `CompletableFuture`. `completedFuture` không tự tạo worker.

**Chẩn đoán:** kiểm tra có `@EnableAsync` trong đúng application context; method public được gọi từ bean khác qua proxy; bean executor đúng tên; thread dump và metrics có thấy một hay hai lần submit; tránh chỉ dựa trên việc method trả Future.

## 10. Khi nào thêm một stage async nữa là hợp lệ?

Không phải cứ có nhiều Future hoặc nhiều stage là sai. Điều cần loại bỏ là **tác vụ bao ngoài không có giá trị**.

Ví dụ:

```java
CompletableFuture<byte[]> compressed =
        downloadedBytes.thenApplyAsync(
                bytes -> compressCpuHeavy(bytes),
                cpuExecutor);
```

Đoạn minh họa cần `downloadedBytes`, `compressCpuHeavy` và `cpuExecutor`. Đây là **một bước CPU nặng thực sự** cần tách khỏi thread đang hoàn thành I/O; dùng `thenApplyAsync` và CPU pool là có lý do.

Khi A trả dữ liệu và B trả Future:

```java
CompletableFuture<Receipt> receipt =
        quoteFuture.thenCompose(
                quote -> paymentClient.chargeAsync(quote));
```

Đoạn minh họa cần `Receipt`, `quoteFuture`, `paymentClient`. `thenCompose` nối hai stage mà không chặn một worker ở giữa bằng `join()`.

Ngược lại, mã sau tạo thêm một worker chỉ để chờ:

```java
CompletableFuture<Receipt> receipt =
        CompletableFuture.supplyAsync(
                () -> paymentClient.chargeAsync(quote).join(),
                executor);
```

Đây là đoạn minh họa với `paymentClient`, `quote`, `executor`; trong worker sẽ có một `.join()` chặn. Khi dependency đã trả Future, thường tốt hơn là **trả future trực tiếp** hoặc dùng `thenCompose`, không cần bọc nó trong supplier rồi block.

Có thể có ngoại lệ kiến trúc (chuyển từ một boundary blocking cũ sang một pool riêng, chính sách isolation đặc biệt, nhiệm vụ outer làm preprocessing nặng). Nhưng phải xác định **work thật của mỗi stage**, tài nguyên dùng và cách chờ. Trong Spring 6.x, một `@Async` method trả inner Future vẫn có nguy cơ outer worker chờ inner; khi đó chuyển sang orchestrator không `@Async` thường là lời giải tốt hơn.

## 11. Lỗi, từ chối tác vụ và cancellation ở hai tầng

Một pipeline có hai điểm submit phải xử lý **hai điểm có thể thất bại**:

- **Outer rejection:** Spring `@Async` không gửi được invocation vì executor đã shutdown/quá tải; với `ThreadPoolTaskExecutor` và `AbortPolicy`, có thể ném `TaskRejectedException` ngay lúc caller gửi.
- **Inner rejection:** `CompletableFuture.supplyAsync(..., executor)` không gửi được supplier. Với Spring executor, lỗi nhận việc cũng có thể theo contract `TaskRejectedException`; với JDK `ThreadPoolExecutor` thường là `RejectedExecutionException`.
- **Inner execution failure:** supplier đã được nhận nhưng ném exception; inner Future hoàn thành ngoại lệ, rồi lỗi được lan qua cơ chế async proxy / Future phía caller.
- **Timeout:** caller `get(timeout)` chỉ giới hạn thời gian chờ của caller. `orTimeout` hoàn thành Future ngoại lệ khi quá hạn nhưng **không chắc ngắt** một HTTP request/JDBC query hoặc giải phóng outer worker đang chờ inner.
- **Cancellation:** `CompletableFuture.cancel(true)` hoàn thành Future với `CancellationException` theo hợp đồng; `mayInterruptIfRunning` không đảm bảo ngắt supplier. Cần cancellation thực ở HTTP/JDBC client hoặc cơ chế hợp tác riêng.
- **Retry:** nhiều tầng retry không có idempotency có thể lặp side effect dù vấn đề ban đầu chỉ là scheduling. Với email, chuyển tiền hoặc cập nhật DB, cần khóa nghiệp vụ và idempotency phù hợp.

Ví dụ quan sát lỗi phía caller:

```java
CompletableFuture<Report> future = service.createReport(id);

future.whenComplete((value, error) -> {
    if (error != null) {
        metrics.failed(id, error);
    } else {
        metrics.completed(id);
    }
});
```

Đây là đoạn minh họa, cần kiểu `Report`, `service`, `id`, `metrics`. `whenComplete` dùng để quan sát. Muốn phục hồi thành giá trị khác, cần `exceptionally`/`handle` theo hợp đồng; đừng mặc định coi lỗi tạo báo cáo như một báo cáo thành công rỗng.

Với `@Async void`, caller không có Future để quan sát kết quả, nên dùng `AsyncUncaughtExceptionHandler` hoặc cơ chế ghi trạng thái phù hợp. Với `@Async CompletableFuture<T>`, lỗi nghiệp vụ nên được quan sát qua Future/completion stage.

**Không dùng `CallerRunsPolicy` vô thức** để “giải quyết” overload: khi pool đầy, policy này có thể thực thi task trên chính thread gửi, làm một Servlet request thread bị giữ lâu. Dùng queue hữu hạn, admission control và phản hồi overload rõ ràng.

## 12. Context, transaction và việc chuyển thread hai lần

Mỗi lần rời thread gọi có thể ảnh hưởng những giá trị gắn với thread:

- MDC/trace ID (`ThreadLocal`), locale và một số security/request context không tự chuyển qua mọi executor.
- Spring `@Transactional` gắn với transaction manager/resource theo thread; không được tự copy transaction từ caller sang inner task.
- `TaskDecorator` có thể hỗ trợ capture/restore context cho task trong **một executor cụ thể**, nhưng không tự bảo đảm context đi qua `commonPool` nếu inner dùng `supplyAsync` không truyền executor.
- Nếu một transaction DB đang mở trong outer worker rồi inner tự chạy update DB, chúng không mặc nhiên cùng một transaction. Thiết kế transaction riêng cho worker thật và đảm bảo thứ tự commit khi cần.
- Nếu task phải chạy sau khi HTTP request kết thúc, tránh phụ thuộc trực tiếp vào request-scope object sống ngắn; truyền ID/dữ liệu đã validate.

Việc giảm một tầng scheduling thường cũng giúp giảm số bước phải truyền context và đơn giản hóa tracing. Với các giai đoạn thực sự cần đổi pool, truyền context bằng cơ chế được stack hỗ trợ và cleanup trong `finally`.

## 13. Checklist kiểm tra code thật

Trước khi chốt cách viết:

1. **Đánh dấu nơi gửi task:** tìm tất cả `@Async`, `supplyAsync`, `runAsync`, `thenApplyAsync`, `thenComposeAsync`, `Executor.execute` hoặc `submit` trong call graph.
2. **Xác định ai làm work:** outer method có CPU/IO work thật không? Hay chỉ tạo một Future khác?
3. **Xác định từng executor:** named Spring bean, JDK common pool, task scheduler, virtual-thread executor hay client I/O? Đừng suy từ tên method.
4. **Kiểm tra lời gọi qua proxy:** `@EnableAsync`, bean được Spring quản lý, không self-invocation.
5. **Kiểm tra Future target:** `completedFuture` đã có giá trị hay `supplyAsync` còn task phía sau? Worker ngoài có đang chờ Future không?
6. **Kiểm tra pool/queue:** core/max/queue hữu hạn, chính sách rejection, tránh chờ task trong chính pool ít worker.
7. **Kiểm tra lỗi:** lỗi nhận task, lỗi khi chạy, exceptional completion, timeout, cancellation phải được quan sát riêng.
8. **Kiểm tra side effect:** đếm lần business method thực chạy, phân biệt với số callback/task được submit.
9. **Kiểm tra context:** trace/MDC, security, transaction, request lifecycle ở mỗi ranh giới thread.
10. **Kiểm tra hợp đồng với caller:** HTTP đợi Future, trả Future qua MVC async, hay trả `202` với durable job? `CompletableFuture` trong RAM **không** tự bền vững sau restart.

### Một phép thử xác định thay vì đoán timing

Với Java thuần, dùng executor tự đếm `execute`, như `DoubleSchedulingJdkDemo` ở trên. Với Spring integration test, dùng hai bean executor được instrument hoặc `TaskDecorator` để xác nhận nơi business chạy, kết hợp `CountDownLatch` để kiểm soát lúc worker bận. Không cần `Thread.sleep` để hy vọng tạo race.

Test thêm:

- Một request đi qua proxy `@Async`, business chạy một lần.
- Code anti-pattern dùng hai pool thực sự tạo hai submissions.
- Khi outer và inner dùng cùng pool 1 worker + queue hữu hạn, **test có timeout nghiêm ngặt** chứng minh rủi ro kẹt trong Spring/version được dùng; không chủ động tạo deadlock trên production.
- Một inner stage ném exception, Future phía caller lỗi, không bị nuốt.
- Outer hoặc inner bị từ chối khi queue đầy, hệ thống trả lỗi hợp đồng thay vì báo đã nhận job.
- `@Async` self-invocation chạy khác lời gọi qua Spring proxy.

## 14. Dấu hiệu trong production cho thấy bị double scheduling

| Dấu hiệu | Giải thích có thể | Cách kiểm chứng |
| --- | --- | --- |
| `activeCount` outer cao nhưng CPU thấp | Worker ngoài đang chờ inner | Thread dump tìm `Future.get`/`join` |
| Inner queue tăng dù outer queue cũng tăng | Cùng logical request đi qua hai pool | Trace hai lần submit, gắn correlation ID |
| Tăng worker outer không cải thiện throughput | Bottleneck nằm ở inner/downstream | Đo queue wait và execution time từng pool |
| Common pool có nhiều blocking JDBC task | `supplyAsync` không truyền executor | Tìm source callsite và thread names |
| Trace ID có ở outer nhưng mất ở inner | Context không đi qua executor thứ hai | Kiểm tra TaskDecorator và context propagation |
| CPU/GC tăng do nhiều callback/FutureTask | Extra allocations/scheduling | Profile allocation, quan sát queue |
| Thỉnh thoảng treo khi tải cao | Outer đợi inner trên cùng pool | Thread dump và kiểm thử saturation |
| Client timeout nhưng worker vẫn bận | Timeout caller không dừng inner | Theo dõi lifetime task, connection timeout/cancel |

Không khẳng định chỉ từ một metric. Một worker có CPU thấp có thể đang chờ mạng hoặc lock theo thiết kế. Cần đối chiếu thread dump, call graph và timing trước khi kết luận do double scheduling.

**Sửa triệt để** là dời scheduling đến đúng stage thực hiện work, bỏ outer wrapper không có ích. Sau refactor, đo lại throughput, p95/p99, số lần reject, queue depth và độ hoàn thiện của trace.

## 15. Các lỗi thường gặp và cách sửa

| Lỗi | Tại sao sai/nguy hiểm? | Cách sửa |
| --- | --- | --- |
| `@Async` method chỉ trả `supplyAsync` | Hai lần submit, worker ngoài có thể chờ | `@Async + completedFuture` hoặc bỏ `@Async` |
| `@Async` và `supplyAsync` gửi cùng fixed pool | Nguy cơ worker chờ task đang queue cùng pool | Chọn một tầng; không `get/join` trong worker |
| Không truyền executor cho `supplyAsync` | Work chạy ngoài Spring pool mong muốn | Truyền executor hoặc chỉ dùng `@Async` |
| Tin rằng hai lần submit = side effect chạy hai lần | Chẩn đoán sai nguyên nhân | Đếm submissions và business executions riêng |
| Bọc Future của SDK trong một `supplyAsync(() -> future.join())` | Tạo worker chỉ để block | Trả Future hoặc `thenCompose` |
| Gọi `this.doAsync()` trong cùng bean | Bỏ qua Spring proxy | Gọi qua bean khác hoặc cấu trúc lại |
| Controller gọi `.join()` ngay | HTTP thread vẫn chặn | Trả async result, hoặc job API riêng |
| `get(timeout)` rồi nghĩ đã cancel inner task | Worker và I/O có thể vẫn chạy | Timeout/cancel thật ở client/driver |
| Mặc định retry cả hai tầng | Side effect có thể lặp ngoài dự kiến | Idempotency, retry budget, dedupe |
| Queue không giới hạn và nhiều nested Future | Tăng backlog, memory và latency | Bounded queue, backpressure/admission |
| Chỉ thêm thread để chữa deadlock | Che lỗi tạm, không giải quyết vòng chờ | Bỏ wait/scheduling không cần thiết |
| Copy transaction sang worker bằng ThreadLocal | Sai boundary và rò tài nguyên | Transaction độc lập và context propagation đúng |
| Bỏ mặc `CompletableFuture` lỗi | Lỗi không được ghi nhận | Quan sát `whenComplete`, `handle`, job status |
| Trả HTTP `202` ngay sau Future RAM | Crash có thể mất job | Durable job/outbox/broker khi cần |

## 16. Câu hỏi tự kiểm tra

**1.** `@Async("ioPool")` method trả `CompletableFuture.supplyAsync(() -> jdbcCall())`. Có mấy lần gửi task và supplier thường chạy ở đâu?  
**Đáp:** Có hai tầng submit khi Spring proxy hoạt động: outer vào `ioPool`, inner thường vào `ForkJoinPool.commonPool()` nếu không truyền executor.

**2.** Thay bằng `return CompletableFuture.completedFuture(jdbcCall())` có chạy JDBC ở worker không?  
**Đáp:** Có, nếu lời gọi đi qua Spring `@Async` proxy. `completedFuture` không tự tạo task.

**3.** Vì sao hai task submissions không đồng nghĩa `jdbcCall()` chạy hai lần?  
**Đáp:** Outer task có thể chỉ giao và chờ inner; phần nghiệp vụ nằm trong inner supplier, chạy một lần. Retry/side effect là vấn đề riêng.

**4.** Một fixed pool có 1 worker và queue 10. Outer `@Async` gọi `supplyAsync` trên cùng pool, rồi trả Future. Rủi ro gì?  
**Đáp:** Outer giữ worker chờ inner; inner đợi worker còn bận: deadlock/starvation.

**5.** Phải dùng `@Async` để ghép hai client đã trả Future chưa?  
**Đáp:** Không. Orchestrator có thể không `@Async`, khởi động các Future rồi dùng `thenCombine`.

**6.** Khi B trả Future và phụ thuộc A, có nên viết `thenApply(a -> bAsync(a).join())` không?  
**Đáp:** Tránh, vì callback có thể chặn worker. Dùng `thenCompose(a -> bAsync(a))`.

**7.** `@Async` tự quyết định executor cho `CompletableFuture.supplyAsync(fn)` không tham số không?  
**Đáp:** Không. Static API JDK dùng quy tắc executor của JDK; cần truyền executor nếu muốn dùng Spring bean.

**8.** `thenApplyAsync(fn, cpuExecutor)` có luôn là double scheduling sai không?  
**Đáp:** Không. Nếu đó là bước CPU nặng riêng cần pool khác, async boundary đó có công việc thật và có thể hợp lý.

**9.** Caller `future.get(2, SECONDS)` hết hạn có làm ngừng HTTP call trong inner worker không?  
**Đáp:** Không. Đây là timeout chờ của caller; cần timeout/cancel ở client I/O thực.

**10.** `@Async` method được gọi qua `this.method()` có chắc được Spring chuyển thread không?  
**Đáp:** Không. Self-invocation bỏ qua proxy theo `AdviceMode.PROXY` mặc định.

## 17. Ghi nhớ và tài liệu chính thức

**Nguyên tắc:** một tác vụ nghiệp vụ chỉ nên có **một owner của việc gửi task** ở mỗi ranh giới thực sự cần thiết. Dùng nhiều stage để ghép kết quả là bình thường; tạo một **outer worker chỉ để đợi inner worker** là cấu trúc nên tránh.

- **Có `@Async` cho blocking method:** tính toán trong worker Spring, trả `CompletableFuture.completedFuture(result)`.
- **Muốn tự dùng JDK Future:** bỏ `@Async`, gọi `supplyAsync(supplier, executor)` trực tiếp.
- **Nguồn đã là Future:** trả/ghép `thenCombine`, `thenCompose`, `allOf`; tránh thêm outer worker.
- **Tác vụ chuyển pool có chủ đích:** dùng biến thể `Async` với executor tường minh và xác định business work thật, tài nguyên, timeout.
- **Chẩn đoán:** đếm số lần submit và số lần business chạy riêng, kiểm tra Spring proxy, thread dump, queue và exceptional completion.
- **Vận hành:** timeout/cancel thực, queue hữu hạn, context propagation và idempotency. Future trong RAM không thay thế durable job queue.

Tài liệu tham khảo:

- [Spring Framework 6.2 — `@Async` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/annotation/Async.html) — temporary Future từ target và actual Future do proxy cung cấp.
- [Spring Framework 6.2 — `AsyncExecutionInterceptor`](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/interceptor/AsyncExecutionInterceptor.html) — invocation được giao cho executor của Spring.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — annotation, qualifier, exception handling và executor.
- [Spring Framework 6.2 — `ThreadPoolTaskExecutor` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html) — core/max/queue/lifecycle/rejection.
- [Java SE 17 — `CompletableFuture` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html) — `completedFuture`, `supplyAsync`, policies, composition, exception và cancellation.
- [Java SE 17 — `Executor` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html) — hợp đồng gửi task của JDK.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-composition-010">Tránh chờ tác vụ con trên chính nhóm luồng đã cạn</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh chờ tác vụ con trên chính nhóm luồng đã cạn

## 1. Vấn đề: parent giữ worker để chờ child cần chính worker ấy

Một lỗi concurrency khó thấy khi dùng Java `CompletableFuture` cùng Spring `ThreadPoolTaskExecutor` là **tác vụ cha (parent) đang chạy trên worker của pool, submit tác vụ con (child) vào cùng pool rồi gọi `Future.get()` hoặc `CompletableFuture.join()` để đợi con**. Nếu tất cả worker đều bị parent giữ, child nằm trong queue mà không có worker thực thi. Parent không kết thúc trước khi child xong; child không bắt đầu trước khi parent nhường worker.

Đây là **thread-pool starvation deadlock**: không có tiến triển vì tài nguyên thực thi đã bị chiếm hết bởi những công việc đang chờ công việc khác cần chính tài nguyên ấy. Không nhất thiết có `synchronized`, `ReentrantLock`, CPU cao hay lỗi trực tiếp từ JVM.

**Nguyên tắc chính:** khi đang giữ worker của executor, tránh chặn nó để đợi một Future mà việc hoàn tất phụ thuộc vào tác vụ chưa được chạy trong cùng executor. Hãy **diễn tả phụ thuộc bằng completion stages** như `thenApply`, `thenCompose` và `thenCombine`, để worker có thể trở lại nhận việc thay vì đợi đồng bộ.

Bài tập trung vào parent–child dependencies, cơ chế queue/core/max, cách Spring `@Async` có thể thêm tầng chờ ngầm, và biện pháp phòng tránh có thể kiểm chứng bằng Java 17+.

## 2. Cơ chế bế tắc qua sơ đồ một worker

Giả sử executor có một worker và hàng đợi đủ chỗ. Caller gửi parent, parent lại gửi child:

```text
Caller thread
    |
    +--- pool.submit(Parent) -----------+
                                         |
                                    Worker #1 chạy Parent
                                         |
                                         +-- submit(Child)
                                         |       |
                                         |       +--> Queue: [Child]
                                         |
                                         +-- Child.get() / Child.join()
                                                 |
                                                 +--> Worker #1 bị chặn
                                                         |
Child cần Worker #1 <------------------------------------+
    |
    +-- Child không thể bắt đầu, Parent không thể nhường worker
```

Đây là chu trình chờ có tính cấu trúc. Nếu Parent chỉ làm một phép tính dài, worker vẫn có thể tự hoàn tất. Nhưng nếu Parent đòi một Child đang xếp hàng mà chỉ chính worker này có thể thực thi, **tăng thời gian chờ sẽ không làm Child tự chạy**.

Với N worker:

```text
Worker #1 = Parent A  --> chờ Child A
Worker #2 = Parent B  --> chờ Child B
...
Worker #N = Parent N  --> chờ Child N

Queue = [Child A, Child B, ... Child N]
Không còn worker rảnh để xử lý queue.
```

Vì thế một thiết kế có vẻ ổn ở tải nhẹ (còn worker rảnh) có thể kẹt khi **số parent đồng thời đạt N**. Có thể tái hiện với 1, 2 hoặc nhiều worker, không nhất thiết pool thật sự rất nhỏ.

## 3. Chờ bên trong và chờ bên ngoài pool không giống nhau

`Future.get()` đợi kết quả, `CompletableFuture.join()` cũng có thể chặn. Điểm quyết định là **thread đang chờ có phải worker mà child cần hay không**.

| Trường hợp | Đánh giá |
| --- | --- |
| Main/test thread bên ngoài pool gọi `future.get()`, worker của pool rảnh để hoàn tất task | Có thể hợp lệ ở ranh giới chờ có thời hạn; vẫn chặn main/test thread |
| HTTP thread gọi `get()` ngay sau submit | Có thể làm request chờ lâu; không nhất thiết làm cạn pool, nhưng phải kiểm soát SLO |
| Parent trong pool gọi `child.join()`, child chạy trong cùng pool đang đầy | Rủi ro starvation deadlock |
| Parent gọi `join()` trên một Future **đã hoàn tất** | Không phải đang đợi child chưa được chạy; thường trả về ngay |
| Parent đang ở pool A chờ task của pool B | Không tự tạo vòng chờ cùng pool, nhưng vẫn có thể gây cạn tài nguyên hoặc vòng phụ thuộc giữa A và B |
| Một callback đăng ký bằng `thenCompose` mà **không gọi blocking API** | Worker không bị giữ chỉ để đợi stage con |

Không có quy tắc “tất cả `join()` đều sai”. Quy tắc đúng là **không biến một worker cần thiết cho tiến trình hoàn tất thành thread đợi Future của tác vụ phụ thuộc chưa có worker**.

## 4. Ví dụ Java 17+ chạy được và không treo vô hạn

Ví dụ này cố ý dùng executor có **một worker**. Parent submit child rồi chờ child với timeout 100 ms. Vì child chưa thể chạy, parent nhận timeout. Khi parent thoát, child mới được chạy. Cuối chương trình, một workflow `thenCompose` chạy thành công trên **chính pool một worker đó** mà không cần nested blocking.

Tạo file `PoolStarvationDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public final class PoolStarvationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Integer>> childRef = new AtomicReference<>();

        try {
            Future<String> parent = pool.submit(() -> {
                Future<Integer> child = pool.submit(() -> 21 * 2);
                childRef.set(child);

                try {
                    child.get(100, TimeUnit.MILLISECONDS);
                    return "unexpected";
                } catch (TimeoutException ex) {
                    return "timed out";
                }
            });

            System.out.println("parent = " + parent.get());
            System.out.println("child = " + childRef.get().get());

            CompletableFuture<Integer> start =
                    CompletableFuture.supplyAsync(() -> 21, pool);

            CompletableFuture<Integer> composed =
                    start.thenCompose(n ->
                            CompletableFuture.supplyAsync(() -> n * 2, pool));

            System.out.println("composed = " + composed.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac PoolStarvationDemo.java
java PoolStarvationDemo
```

Stdout:

```text
parent = timed out
child = 42
composed = 42
```

Giải thích từng bước:

1. `Executors.newSingleThreadExecutor()` chỉ có một worker. Parent chạy đầu tiên.
2. Parent submit child vào chính pool. Child nằm ở queue vì worker đang bận chạy Parent.
3. Parent gọi `child.get(100, TimeUnit.MILLISECONDS)`; do không có worker nào chạy child, lượt chờ này timeout. Parent kết thúc và trả chuỗi `"timed out"`.
4. Khi worker rảnh, child được lấy khỏi queue, trả `42`. `childRef.get().get()` được gọi ở **main thread**, không giữ worker của pool.
5. Cuối cùng, `start` trả 21. `thenCompose` đăng ký một stage child trong cùng pool và trả quan hệ hoàn tất, **không ép worker đang thực thi continuation phải chặn đợi child**. Child sau đó chạy và cho kết quả 42. Main thread đọc kết quả bằng `composed.get()`.

Ví dụ đã được thực thi qua JShell trên JDK hiện có, xác nhận đúng ba dòng output. Trong chương trình độc lập, `shutdownNow()` dừng executor lúc thoát; trong Spring, không nên tự shutdown bean executor chia sẻ sau mỗi request.

**Phản ví dụ:** nếu bỏ timeout và thay bằng `child.get()` vô thời hạn trong Parent, cả Parent lẫn Child có thể chờ mãi. Không chạy biến thể đó trong CI nếu không có cơ chế kết thúc từ bên ngoài.

## 5. Nhận diện deadlock bằng đồ thị chờ và tài nguyên

Để phân tích một workflow, ghi ra:

```text
Parent P đang sở hữu worker W
P --------- chờ ----------> Future của Child C
C --------- cần ----------> worker thuộc Executor E
E --------- đã cấp -------> W cho P
```

Nếu E đã cấp **toàn bộ** worker cho các parent đang chờ child, không có đường tiến triển. Điều này khác với task đang chạy chậm vì CPU hoặc I/O: với starvation deadlock, child có thể thậm chí chưa **bắt đầu**.

| Hiện tượng | Phân biệt |
| --- | --- |
| **Pool starvation** | Tác vụ đã xếp hàng nhưng không có worker khả dụng |
| **Pool-starvation deadlock** | Worker đang bị chiếm bởi task phải đợi công việc không thể chạy vì chính worker đó đang bị chiếm |
| **CPU saturation** | Worker đang tiến hành tính toán, CPU/quotas bị sử dụng gần hết |
| **Blocking I/O** | Worker có thể đang đợi DB/API; có thể tiến triển khi I/O kết thúc |
| **Lock deadlock** | Threads giữ khóa và chờ khóa của nhau; JVM có thể phát hiện một số monitor/ownable synchronizer cycles |
| **Rejection** | Pool/queue không nhận thêm task và có thể ném exception, không nhất thiết là chờ vô hạn |

Đừng dựa vào mỗi `Thread.State.WAITING`. Phải xác định **thread nào hoàn tất Future đó, task đó đang ở executor nào và điều kiện nào giúp nó có worker**.

## 6. ThreadPoolTaskExecutor: tại sao maxPoolSize không tự cứu được?

Spring `ThreadPoolTaskExecutor` dùng `ThreadPoolExecutor` JDK. Quy tắc nộp task thường là:

1. Khi số worker dưới `corePoolSize`, thêm worker.
2. Khi đạt core mà queue nhận được task, ưu tiên **enqueue**.
3. Chỉ khi queue không nhận được, tạo thêm worker tới `maxPoolSize`.
4. Nếu queue không nhận được và đã đạt max, task bị từ chối theo policy.

Ví dụ **mảnh cấu hình**:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(20);
executor.setQueueCapacity(1000);
```

Nếu hai worker lõi đang chạy hai parent và cả hai submit children rồi chờ, queue còn rất rộng sẽ nhận children, thay vì bắt buộc pool mở lên 20 worker. Vì thế **`maxPoolSize=20` không bảo đảm có 20 worker để giải cứu parent**.

Các cấu hình ảnh hưởng tới triệu chứng:

| Cấu hình | Khi core workers bận | Hệ quả với child |
| --- | --- | --- |
| `core=max=1`, queue có chỗ | Child chờ trong queue | Một parent chờ child có thể kẹt |
| `core=max=N`, queue có chỗ | Tất cả N parent có thể giữ N workers | N child chờ và có thể kẹt |
| `core<max`, queue rất lớn | Hệ thống ưu tiên enqueue | Max có thể hiếm khi được dùng |
| `queueCapacity=0`, max hữu hạn | Cố bàn giao trực tiếp hoặc tạo thêm worker | Child có thể chạy nếu còn worker; nếu đạt max có thể bị từ chối |
| Queue vô hạn/khổng lồ | Rất nhiều task tiếp tục được nhận | Backlog, memory/latency tăng và che giấu việc cạn worker |

Tăng `corePoolSize`, `maxPoolSize` hoặc giảm `queueCapacity` có thể làm vấn đề xuất hiện muộn hơn hoặc đổi thành rejection, nhưng **không sửa cấu trúc parent phải giữ worker để chờ child**. Với N worker, N parent đồng thời vẫn có thể tái tạo vòng chờ.

## 7. CompletableFuture giúp diễn tả phụ thuộc mà không chặn worker

Thay vì tạo child rồi lập tức `get()` trong parent, hãy xây dựng một **completion graph**. Các stage được kích hoạt khi điều kiện hoàn tất đã thỏa, không cần một worker ngồi đợi stage khác.

### thenApply: biến đổi giá trị

```java
CompletableFuture<Integer> doubled =
        CompletableFuture.supplyAsync(() -> 21, executor)
                .thenApply(number -> number * 2);
```

Ở đây `thenApply` biến đổi kết quả 21 thành 42. Đây là **mảnh code** giả sử đã có biến `Executor executor`. Callback có thể chạy trên thread hoàn tất Future trước, hoặc thread gọi hoàn tất khác theo hợp đồng Java; không được mặc định nó chạy trên caller.

Dùng `thenApplyAsync(fn, executor)` khi muốn lập lịch rõ ràng cho callback. **Đừng** nhét một `join()` để chờ child chưa chạy trong callback ấy.

### thenCompose: bước tiếp theo trả một Future

```java
CompletableFuture<Integer> doubled =
        CompletableFuture.supplyAsync(() -> 21, executor)
                .thenCompose(number ->
                        CompletableFuture.supplyAsync(
                                () -> number * 2, executor));
```

`thenCompose` làm phẳng quan hệ Future lồng nhau và tạo stage đại diện cho kết quả child. Stage đầu hoàn tất và có thể giải phóng worker, trong khi stage con được thực thi sau đó. Nó không cần `child.join()` ngay trong callback.

**Sai cách:**

```java
CompletableFuture<Integer> wrong =
        CompletableFuture.supplyAsync(() -> 21, executor)
                .thenApply(number ->
                        CompletableFuture.supplyAsync(
                                () -> number * 2, executor).join());
```

Đây là **phản ví dụ**: `thenApply` chạy callback có `join()`. Nếu callback chạy trên worker của pool một thread thì child được submit vào queue trong khi callback tiếp tục giữ worker để chờ — lặp lại starvation. Đặt `join` sâu trong lambda không làm hành vi blocking biến mất.

### thenCombine: hai tác vụ độc lập

```java
CompletableFuture<Integer> base =
        CompletableFuture.supplyAsync(() -> 40, executor);
CompletableFuture<Integer> extra =
        CompletableFuture.supplyAsync(() -> 2, executor);

CompletableFuture<Integer> total =
        base.thenCombine(extra, Integer::sum);
```

Với một worker, base và extra có thể chạy lần lượt; phép kết hợp được đăng ký và chạy **sau khi cả hai hoàn tất**. Không có parent giữ worker rồi đứng đợi child.

### allOf: lỗi nằm ở nơi gọi join

`CompletableFuture.allOf(a,b,c)` trả Future tổng hợp mà không tự block. Tuy nhiên `allOf(a,b,c).join()` **có thể block** nếu gọi bên trong worker đang cần để a/b/c hoàn tất.

Nếu các Future thành phần đã được tổ chức hợp lý, có thể dùng:

```java
CompletableFuture<Void> all = CompletableFuture.allOf(a, b);
CompletableFuture<Result> combined =
        all.thenApply(ignored -> merge(a.join(), b.join()));
```

Đây là **mảnh minh họa**, giả sử `a`, `b` là các `CompletableFuture`, `Result` và `merge` đã được định nghĩa. Khi `all` hoàn tất bình thường, `a` và `b` đã có kết quả; hai `join()` trong callback chỉ lấy kết quả **đã sẵn sàng**, không chờ task chưa thể chạy. Nếu một stage thất bại thì `thenApply` không chạy; cần xử lý exceptional completion riêng.

**Kết luận:** `thenCompose` và `allOf` không tự giải quyết mọi phụ thuộc. Chúng an toàn theo hướng này khi continuation không tự chiếm worker để chờ một công việc chưa có worker.

## 8. Spring: service điều phối có thể trả CompletableFuture mà không cần @Async

Một cách cấu trúc rõ ràng là để service **xây chuỗi CompletionStage**, còn từng bước tốn thời gian được submit bằng `CompletableFuture.supplyAsync(..., executor)`. Method điều phối không nhất thiết cần `@Async`.

Bean executor (mảnh cấu hình Spring Framework cần import và đăng ký trong ApplicationContext):

```java
@Configuration(proxyBeanMethods = false)
public class WorkerConfig {
    @Bean("workerPool")
    public ThreadPoolTaskExecutor workerPool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(32);
        pool.setThreadNamePrefix("worker-");
        return pool;
    }
}
```

Service (mảnh tích hợp Spring, cần imports `@Service`, `@Qualifier`, `Executor`, `CompletableFuture`, `ThreadPoolTaskExecutor`):

```java
@Service
public class CalculationService {
    private final Executor workerPool;

    public CalculationService(
            @Qualifier("workerPool") ThreadPoolTaskExecutor workerPool) {
        this.workerPool = workerPool;
    }

    // Không gắn @Async lên phương thức chỉ xây completion graph.
    public CompletableFuture<Integer> calculate() {
        return CompletableFuture.supplyAsync(() -> 21, workerPool)
                .thenApplyAsync(number -> number * 2, workerPool);
    }
}
```

`calculate()` lập lịch hai stage và trả Future cho caller. Worker thứ nhất có thể hoàn thành stage đầu rồi nhận stage thứ hai. Không có parent đang chiếm worker chỉ để `join()` kết quả child.

Khi bước tiếp theo vốn là một API async thực sự, chẳng hạn HTTP client trả `CompletionStage<Response>`, sử dụng `thenCompose`:

```java
public CompletableFuture<Report> build(long id) {
    return CompletableFuture.supplyAsync(
                    () -> loadBase(id), workerPool)
            .thenCompose(base ->
                    remote.fetchAsync(base.externalId())
                            .thenApply(extra -> merge(base, extra)));
}
```

Đoạn này là **mảnh minh họa** cần `loadBase`, `remote.fetchAsync`, `merge` và `Report` trong ứng dụng. Điều kiện là `fetchAsync` không giấu một nested blocking trên chính pool cạn worker. Mọi API mang chữ Async đều cần kiểm tra implementation và executor thực tế.

**Lưu ý:** code trên không cần một `@Async` bao ngoài. Nếu phương thức orchestration gọi `join()` trong lambda, lỗi vẫn có thể xuất hiện dù method trả `CompletableFuture`.

## 9. Cạm bẫy Spring @Async: có thể có Future.get() ngầm trong interceptor

Một trường hợp đặc biệt quan trọng: code nghiệp vụ **không hề gọi `join()`**, nhưng cấu trúc vẫn tạo vòng chờ.

Trong Spring Framework **6.2.x** ở chế độ `@Async` proxy, `AsyncExecutionInterceptor` thực hiện lời gọi target trong một `Callable` được gửi tới executor. Nếu lời gọi target **trả một `Future`**, interceptor lấy giá trị của Future đó bằng `get()` để hoàn tất Future phía proxy. Điều này có thể khiến chính worker xử lý `@Async` **chờ một Future con chưa hoàn tất**.

**Mã phản ví dụ** (mảnh Spring-managed bean, không dùng làm thiết kế production):

```java
@Service
public class BadWorkflow {
    private final Executor workerPool;

    public BadWorkflow(@Qualifier("workerPool") Executor workerPool) {
        this.workerPool = workerPool;
    }

    @Async("workerPool")
    public CompletableFuture<Integer> calculateAsync() {
        return CompletableFuture.supplyAsync(() -> 42, workerPool);
    }
}
```

Nếu `workerPool` có một worker, queue nhận được child, caller gọi method qua Spring proxy và cơ chế Spring async tương ứng được bật:

```text
Caller -> @Async proxy -> submit wrapper
                                 |
                            Worker chạy wrapper
                                 |
                         Target.calculateAsync()
                                 |
                          supplyAsync(child)
                                 |
                          child nằm trong queue
                                 |
                     target trả CompletableFuture
                                 |
                    interceptor chờ future.get()
                                 |
                     Worker không rảnh để chạy child
```

Cấu trúc này có thể kẹt dù method chỉ có `return CompletableFuture.supplyAsync(...)`. Đây là hành vi **được đối chiếu với bytecode `AsyncExecutionInterceptor` của Spring 6.2.9**: sau `MethodInvocation.proceed()`, nếu kết quả là `Future`, callable gọi `Future.get()`. Nó không phải quy tắc cho mọi cơ chế async bên ngoài Spring hay mọi phiên bản framework.

Cách sửa:

- **Tốt nhất cho composition:** method điều phối không có `@Async`, tự trả Future được ghép từ các stage và chọn executor tường minh như mục 8.
- **Nếu giữ `@Async`:** thực hiện tính toán ngay trên worker của method, rồi trả một Future **đã hoàn tất**, chẳng hạn `CompletableFuture.completedFuture(result)`. Không tạo Future con pending trên cùng pool rồi trả nó cho interceptor chờ.

```java
@Async("workerPool")
public CompletableFuture<Integer> calculateAsync() {
    int result = doCalculation(); // Đang chạy bên trong worker @Async.
    return CompletableFuture.completedFuture(result);
}
```

Đây là **mảnh method Spring**, cần định nghĩa `doCalculation()` và được gọi qua bean proxy. Khi result đã tính xong trước lúc method trả, interceptor không chờ một child chưa có worker.

**Hai cơ chế cần phân biệt:** `@Async` lập lịch nhờ **Spring proxy/advice**; `CompletableFuture.supplyAsync` lập lịch nhờ **JDK Executor**. Kết hợp cả hai mà không vẽ đường đi có thể vô tình tạo **hai tầng submit và một điểm chờ ẩn**.

## 10. Bảng quyết định khi code hiện có đang gọi get/join

| Đang làm gì? | Rủi ro | Điều chỉnh |
| --- | --- | --- |
| Parent trong pool gọi `child.get()` cùng pool | Rất cao khi mọi worker cùng bị chiếm | Trả stage và nối bằng `thenCompose` |
| Callback `thenApply` gọi `child.join()` | Có thể blocking trên thread hoàn tất stage | Dùng `thenCompose`, không chờ child trong callback |
| N task độc lập rồi gọi `allOf(...).join()` ngay trong worker pool ấy | Có thể giữ worker cần để chạy N task | Trả stage `allOf`, kết hợp sau completion |
| Controller gọi `future.get(timeout)` trên request thread | Không trực tiếp giữ worker pool, nhưng request bị block | Xét async HTTP/SLO; dùng deadline rõ |
| `@Async` trả Future con pending chạy cùng pool | Có thể bị interceptor chờ ngầm | Bỏ `@Async` ở orchestration hoặc trả Future đã hoàn tất trong target |
| Tăng max mà queue rất rộng | Có thể không tạo thêm worker | Kiểm tra core, queue, max theo thứ tự |
| Tách parent/child sang hai pool có giới hạn | Có thể loại vòng chờ cùng pool, nhưng chuyển điểm nghẽn | Xem queue, dependencies, overhead và tải đa tầng |
| Timeout rồi retry vô hạn | Có thể tạo thêm task/backlog | Retry có giới hạn, backoff, idempotency và overload control |

Việc tách executor có thể là giải pháp hợp lý nếu bắt buộc có thao tác chặn độc lập (ví dụ thư viện chỉ có blocking API), nhưng cần cấu hình đồng thời theo toàn bộ dependency: CPU, DB connection, memory, rate limit và network. **Không chọn unbounded threads chỉ để giấu cấu trúc sai**.

## 11. Timeout và cancellation không tự tháo được vòng chờ

`future.get(1, SECONDS)` có thể giải phóng parent sau 1 giây; ví dụ Java ở mục 4 cố ý dùng timeout để chương trình thoát. Nhưng nó chỉ **chặn tình huống treo vô hạn**, không làm mô hình submit child rồi đợi trên cùng pool trở thành tốt.

Với `CompletableFuture`:

- `orTimeout(timeout, unit)` khiến Future hoàn tất exceptional khi chưa xong đúng hạn, nhưng **không tự bảo đảm** worker, I/O hay dependency đã dừng.
- `completeOnTimeout(value, timeout, unit)` dùng giá trị fallback khi quá hạn, **không có nghĩa công việc nền đã ngừng**.
- `cancel(true)` của `CompletableFuture` không sử dụng flag để ngắt worker theo cách một số `FutureTask` có thể làm; hủy stage không nhất thiết hủy mọi tác vụ phụ thuộc.
- `get(timeout)` chỉ giới hạn **lượt chờ của caller**, không phải deadline thực thi toàn workflow.

Mọi giao thức có hủy/cancel nên tính đến tài nguyên đã giữ, side effects, idempotency và deadline của DB/HTTP client. Không nên dùng timeout như cách chính để chờ child khi toàn bộ worker đã cạn.

## 12. ForkJoinPool, commonPool và virtual threads: những ngoại lệ cần hiểu

`CompletableFuture.supplyAsync(supplier)` **không chỉ định executor** thì theo JDK 17 thường dùng `ForkJoinPool.commonPool()`, với ngoại lệ về parallelism và customization của framework/subclass. Việc child chạy trên một pool khác đôi khi khiến mẫu thử “không còn kẹt”, nhưng bạn có thể đã **chuyển tải sang common pool ngoài ý muốn**, làm mất kiểm soát queue/công suất/observability và context.

`ForkJoinPool` có cơ chế đặc thù như work stealing và có thể hỗ trợ một số trường hợp managed blocking; **không được suy ra** mọi kiểu blocking hoặc mọi dependency đều được pool tự mở đủ worker để phá chu trình. Một `ThreadPoolTaskExecutor` cố định thông thường không có cùng chính sách đó.

Virtual threads (JDK 21+ trong môi trường phù hợp) giúp giảm chi phí của nhiều task phải chặn, nhưng không sửa được một chu trình logic phụ thuộc kết quả, quota database hoặc giới hạn semaphore. Cần xác định **phần tài nguyên nào thực sự bị giữ** thay vì hy vọng thread model mới tự bảo đảm tiến triển.

## 13. Chẩn đoán trong Spring production

Quan sát một tập metric **trong cùng khoảng thời gian**:

| Dấu hiệu | Gợi ý | Việc cần làm |
| --- | --- | --- |
| `activeCount ≈ poolSize` lâu | Hầu hết worker đang bận hoặc chờ | Thread dump xem WAITING, TIMED_WAITING, I/O |
| Queue tăng, throughput hoàn tất gần bằng 0 | Task nằm chờ, worker không tiến triển | Tìm parent đang chờ Future được queue thực thi |
| CPU thấp, response timeout | Có thể blocking/lock/pool starvation | Xem stack `FutureTask.get`, `CompletableFuture.join` |
| maxPoolSize cao, pool không tăng beyond core | Queue có chỗ nên không mở worker vượt core | Kiểm tra `queueCapacity` thực tế |
| `TaskRejectedException` tăng | Chạm trần pool/queue hoặc executor shutdown | Phân biệt rejection với task nằm chờ |
| `@Async` method trả Future con pending | Có thể interceptor `get()` Future bên trong worker | Xác định executor của method và child |
| Log có submitted nhưng thiếu started | Child chưa được lấy khỏi queue | Ghép trace parent-child, đo queue wait |

Dùng `ThreadPoolTaskExecutor.getActiveCount()`, `getPoolSize()`, `getQueueSize()` cùng metric executor/Micrometer khi được instrument. Đây là snapshot, không phải chứng minh duy nhất. Kết hợp **thread dump**, trace/correlation ID, và danh sách Future phụ thuộc để biết ai chờ ai.

Cần chú ý: JVM deadlock detector chủ yếu hữu ích với monitor và một số lock; **thread-pool starvation deadlock có thể không hiện là một Java monitor deadlock**. Thread dump vẫn giúp thấy các worker cùng chờ `FutureTask.get` hoặc `CompletableFuture.waitingGet` trong lúc queue còn child chưa bắt đầu.

## 14. Kiểm thử tái hiện mà không để test suite treo

Một bài test tốt nên cố tình tạo điều kiện cạn worker nhưng có deadline:

1. Dùng executor cố định 1 hoặc 2 worker, queue đủ chỗ chứa children.
2. Cho mỗi worker chạy một parent, xác định parent thực sự đã vào task trước khi submit child; dùng `CountDownLatch` nếu cần phối hợp thứ tự.
3. Chờ child với **timeout nhỏ** chỉ để xác nhận điểm kẹt, không để test treo vĩnh viễn. Ghi nhận child đã submit nhưng chưa started.
4. Thoát hoặc hủy parent bằng cơ chế bên ngoài; xác nhận child có thể chạy sau khi worker được nhường.
5. Thay pattern bằng `thenCompose`/`thenApplyAsync`, chạy lại trên **cùng một pool một worker** và xác nhận workflow cuối hoàn tất.
6. Lặp với nhiều parent ngang hoặc vượt số worker, và cả tình huống queue đầy/rejection.
7. Có timeout cho test harness và cleanup trong `finally`. Không dùng `Thread.sleep()` ngẫu nhiên làm giả lập thứ tự thay cho latch/hook có kiểm soát.

Đánh giá đúng phải bao gồm **throughput hoàn tất**, thời gian chờ queue, số worker bận, p95/p99, lỗi và khả năng recovery. Một test chỉ qua khi ít tác vụ chưa đủ chứng minh cấu trúc an toàn.

## 15. Checklist code review

Trước khi merge workflow `CompletableFuture` hoặc `@Async`, hỏi:

1. Hàm nào chạy **trên worker của executor**?
2. Trong hàm ấy có `get`, `join`, `await` hoặc API chặn vì task khác không?
3. Task được đợi sẽ chạy ở **executor nào**? Có phải cùng pool hoặc một chuỗi pool phụ thuộc vòng?
4. Với mọi worker đều bận parent, child còn được cấp worker không?
5. Queue có chỗ khiến `maxPoolSize` không mở rộng hay không?
6. Phương thức `@Async` có trả một Future con **còn pending** chạy trên chính executor mà async interceptor đang dùng?
7. Có thể dùng `thenCompose`/`thenCombine`/`allOf` và trả **Future của toàn workflow** thay cho chờ đồng bộ?
8. Callback có xử lý CPU/I/O nặng hoặc lại submit+join trong cùng pool không?
9. Có đo queue, active workers, task started/completed và exception/rejection không?
10. Timeout/cancellation và resource cleanup có được thiết kế, không chỉ dùng để che starvation?

Đề bài không yêu cầu “không bao giờ chờ bất kỳ Future nào”: chờ ở **ranh giới điều phối bên ngoài** hoặc đọc Future đã hoàn tất có thể hợp lý. Mục tiêu là **không giữ tài nguyên cần thiết cho child tiến triển chỉ để đợi child ấy**.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Pool một worker, parent submit child vào chính pool rồi gọi `child.get()` vô hạn. Vì sao kẹt?  
**Đáp án:** Child xếp hàng nhưng chỉ có worker đang bị parent chiếm. Parent đợi child, child đợi worker.

**Câu 2.** `core=2`, `max=20`, `queueCapacity=1000`; hai parent cùng chờ hai child. Max=20 có đảm bảo cứu được không?  
**Đáp án:** Không. Khi queue còn chỗ, executor ưu tiên enqueue; pool có thể duy trì hai worker đang bị chặn.

**Câu 3.** Đổi `get()` sang `join()` có giải quyết blocking không?  
**Đáp án:** Không. `join` cũng chặn nếu stage chưa hoàn tất; chủ yếu khác loại exception.

**Câu 4.** Bước tiếp theo trả một `CompletableFuture<T>`. Chọn `thenApply` hay `thenCompose`?  
**Đáp án:** `thenCompose` để nối và làm phẳng stage bất đồng bộ, không cần `join` ở callback.

**Câu 5.** `@Async` method trả `CompletableFuture.supplyAsync(..., samePool)` có thể deadlock dù method không gọi `join`?  
**Đáp án:** Có. Với Spring proxy async 6.2.x, interceptor có thể gọi `Future.get()` trên Future target trả về trong worker của pool.

**Câu 6.** `CompletableFuture.allOf(a,b)` có chặn worker không?  
**Đáp án:** Không; nó trả stage tổng hợp. Nhưng gọi `allOf(a,b).join()` trong worker đang cần để a/b chạy thì có thể gây starvation.

**Câu 7.** Khi parent có `get(timeout)`, child chạy sau khi parent timeout, vấn đề đã được sửa chưa?  
**Đáp án:** Chưa. Timeout chỉ giúp parent nhường worker sau khi thất bại; mẫu phụ thuộc tài nguyên vẫn không an toàn.

**Ghi nhớ:** **Worker thực hiện việc; Future biểu diễn phụ thuộc; completion stages phối hợp kết quả.** Đừng để worker biến thành một thread đợi tác vụ con cần chính worker ấy mới bắt đầu. Trong Spring, hãy kiểm tra cả điểm chờ **ngầm trong `@Async` interceptor**, không chỉ `get/join` viết trực tiếp trong code.

### Tài liệu tham khảo

- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — AsyncExecutionInterceptor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/interceptor/AsyncExecutionInterceptor.html)


</details>

- [Quay lại đầu trang](#back-to-top)
