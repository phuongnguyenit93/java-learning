<a id="back-to-top"></a>

# Flow &amp; Backpressure

## Menu
- [Flow API trong java.util.concurrent](#flow-and-backpressure-001)
- [Publisher, Subscriber, Subscription, Processor](#flow-and-backpressure-002)
- [request(n) và demand-driven consumption](#flow-and-backpressure-003)
- [onSubscribe(), onNext(), onError(), onComplete()](#flow-and-backpressure-004)
- [SubmissionPublisher và executor sử dụng](#flow-and-backpressure-005)
- [Backpressure khác buffering thế nào?](#flow-and-backpressure-006)
- [Giới hạn buffer và chính sách xử lý chậm](#flow-and-backpressure-007)
- [Hủy subscription và giải phóng tài nguyên](#flow-and-backpressure-008)
- [Thread safety trong callback và xử lý lỗi](#flow-and-backpressure-009)
- [Flow API của JDK so với blocking queue](#flow-and-backpressure-010)
- [Khi phù hợp để xây pipeline theo sự kiện](#flow-and-backpressure-011)
- [SubmissionPublisher.submit() so với offer(): xử lý subscriber chậm và buffer đầy](#flow-and-backpressure-012)
- [Đóng SubmissionPublisher, tín hiệu kết thúc và vòng đời executor của publisher](#flow-and-backpressure-013)

## <a id="flow-and-backpressure-001">Flow API trong java.util.concurrent</a>

<details>
<summary>Xem chi tiết</summary>

# Flow API trong java.util.concurrent

## 1. Flow giải quyết vấn đề gì?

Khi một thành phần tạo dữ liệu nhanh hơn thành phần xử lý, việc đẩy dữ liệu vô điều kiện có thể làm hàng đợi phình to, tăng bộ nhớ, làm hệ thống chậm hoặc hết heap. **Backpressure** là cơ chế để bên nhận thông báo khả năng tiếp nhận, giúp bên phát điều chỉnh số lượng phần tử gửi.

Từ **Java 9**, Java cung cấp `java.util.concurrent.Flow`: tập các interface nền tảng cho mô hình **Publisher–Subscriber có điều khiển nhu cầu (demand)**. Flow mô tả hợp đồng truyền các tín hiệu dữ liệu, hoàn tất, lỗi và cơ chế đăng ký/yêu cầu số lượng; nó không tự biến mọi đoạn xử lý thành một hệ thống không chặn, không tự cung cấp hàng trăm toán tử biến đổi và cũng không quyết định cách lưu trữ dữ liệu thay cho ứng dụng.

Flow hữu ích khi xây dựng pipeline bất đồng bộ giữa producer và consumer, xử lý stream sự kiện, kết nối các tầng xử lý nhiều tốc độ và học các nguyên lý Reactive Streams. Nội dung này tập trung vào **Java concurrency nói chung** và các class của JDK, không phụ thuộc framework khác.

### Tư duy cốt lõi

Thay vì producer cứ gọi `consumer.accept(item)` không kiểm soát tốc độ, subscriber có thể nói: **“Tôi sẵn sàng nhận thêm n item.”** Publisher chỉ được phát `onNext` trong hạn mức yêu cầu chưa được sử dụng của subscription đó.

```text
                  subscribe(subscriber)
  Publisher --------------------------------> Subscriber
             onSubscribe(subscription)
            <--------------------------------
                 request(2)
            <--------------------------------
                   onNext(A)
            -------------------------------->
                   onNext(B)
            -------------------------------->
                 request(1)
            <--------------------------------
                   onNext(C)
            -------------------------------->
          onComplete() hoặc onError(error)
            -------------------------------->
```

Các mũi tên thể hiện **luồng tín hiệu logic**, không yêu cầu mọi callback phải chạy trên thread riêng hoặc cùng thread; điều đó tùy triển khai.

## 2. Bốn interface trọng tâm

| Interface | Vai trò | Phương thức quan trọng |
| --- | --- | --- |
| `Flow.Publisher<T>` | Nguồn phát dữ liệu kiểu `T` cho các subscriber | `subscribe(Subscriber<? super T>)` |
| `Flow.Subscriber<T>` | Bên nhận dữ liệu, quyết định nhu cầu và xử lý các sự kiện | `onSubscribe`, `onNext`, `onError`, `onComplete` |
| `Flow.Subscription` | Liên kết giữa một publisher và một subscriber; mang tín hiệu demand/cancel | `request(long n)`, `cancel()` |
| `Flow.Processor<T,R>` | Vừa là subscriber nhận `T`, vừa là publisher phát `R`, có thể biến đổi/lọc dữ liệu | Kế thừa `Subscriber<T>` và `Publisher<R>` |

### Publisher

`Publisher.subscribe(...)` đăng ký một subscriber. Sau khi đăng ký hợp lệ, subscriber được cung cấp một đối tượng `Subscription` qua `onSubscribe`. Publisher có thể có một hoặc nhiều subscriber; chính sách replay, multicast, buffer và bắt đầu phát do implementation quyết định, **không suy ra rằng mọi publisher đều ghi lại lịch sử để người đăng ký muộn đọc lại**.

### Subscriber và bốn tín hiệu

Một subscriber có vòng đời điển hình:

1. **`onSubscribe(subscription)`**: nhận liên kết; thường lưu subscription rồi gọi `request(n)`. Không yêu cầu thì không có quyền nhận item mới.
2. **`onNext(item)`**: xử lý một item đã được yêu cầu; có thể yêu cầu thêm số lượng sau khi hoàn tất xử lý.
3. **`onError(throwable)`**: kết thúc do lỗi; không có thêm tín hiệu thông thường sau terminal signal.
4. **`onComplete()`**: kết thúc bình thường khi nguồn không còn item; không có thêm `onNext` sau đó.

Với cùng một subscription, các lời gọi tín hiệu tới subscriber được tổ chức theo thứ tự và bảo đảm quan hệ *happens-before*, dù triển khai có thể sử dụng nhiều thread khác nhau. Không suy ra thứ tự tổng thể giữa **các subscription khác nhau**.

### Subscription

`request(n)` **cộng thêm** n vào nhu cầu chưa đáp ứng; nó không đặt lại demand bằng n. Với n dương, publisher có thể gọi tối đa thêm n lần `onNext` theo lượng demand đã có; mỗi `onNext` tiêu thụ một đơn vị. Nếu `n <= 0`, subscriber phải nhận tín hiệu `onError` với `IllegalArgumentException`. Giá trị `Long.MAX_VALUE` thường được coi là nhu cầu gần như vô hạn.

`cancel()` yêu cầu dừng nhận dữ liệu theo cách **best effort**: vẫn có thể có tín hiệu đã lên lịch đến sau khi cancel, và subscriber đã cancel không được bảo đảm nhận `onComplete`/`onError`. Vì thế, cancel không đồng nghĩa hủy mọi công việc trong hệ thống ngay lập tức.

### Processor

`Processor<T,R>` vừa nhận tín hiệu đầu vào, vừa phát đầu ra. Một pipeline:

```text
Publisher<Integer>
    |
    v
Processor<Integer, String>  (đổi số thành chuỗi)
    |
    v
Subscriber<String>
```

Một processor đúng phải quản lý **demand ở cả hai phía**: không nhận dữ liệu upstream vô hạn nếu downstream không xử lý kịp, trừ khi chính nó có chiến lược buffer/drop được kiểm soát. `Flow.Processor` là interface, **không tự thực hiện phép map/filter hoặc lan truyền backpressure**.

## 3. Backpressure hoạt động như thế nào?

Giả sử một subscriber bắt đầu với `request(2)`:

| Thời điểm | Hành động | Demand còn lại |
| --- | --- | --- |
| Ban đầu | `onSubscribe`, chưa gọi request | 0 |
| T1 | `request(2)` | 2 |
| T2 | Nhận `onNext(A)` | 1 |
| T3 | Nhận `onNext(B)` | 0 |
| T4 | Producer có item C | C chưa được phát qua `onNext` nếu không có demand mới |
| T5 | `request(3)` | 3 |
| T6 | Nhận `onNext(C)` | 2 |

Ở T4, publisher có thể giữ item trong buffer, trì hoãn nguồn sinh dữ liệu, chặn publisher, áp dụng drop policy hoặc làm gì khác theo hợp đồng triển khai. **Flow quy định giới hạn phát `onNext`, không bảo đảm toàn bộ pipeline dùng bộ nhớ hữu hạn trong mọi implementation.**

Có hai cách yêu cầu phổ biến:

- **Theo từng phần tử:** `request(1)` trong `onSubscribe`, rồi gọi tiếp `request(1)` sau khi xử lý mỗi `onNext`. Dễ hiểu và kiểm soát số phần tử đang được cho phép.
- **Theo lô (batch/prefetch):** yêu cầu ban đầu, chẳng hạn 32 hoặc 64 item, rồi bổ sung khi đã xử lý một phần lô. Thường giảm chi phí điều phối so với yêu cầu từng item, nhưng cho phép nhiều item in-flight hơn.

Kích thước batch phù hợp phụ thuộc độ trễ nguồn, chi phí xử lý, kích thước item và bộ nhớ chấp nhận được. `request(Long.MAX_VALUE)` có thể hợp lý khi dữ liệu nhỏ và bên nhận thật sự xử lý kịp, nhưng làm mất khả năng dùng demand để hạn chế luồng item.

## 4. Ví dụ Java 17+: subscriber tự yêu cầu từng item

JDK cung cấp `SubmissionPublisher<T>`, một publisher có buffer riêng cho từng subscriber và gửi tín hiệu bất đồng bộ qua `Executor`. Ta dùng nó để quan sát chu trình request–onNext–complete.

Lưu tệp **`FlowBasicsDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FlowBasicsDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Flow.Subscriber<Integer> subscriber = new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
                System.out.println("received=" + item);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error);
                done.countDown();
            }

            @Override
            public void onComplete() {
                System.out.println("completed");
                done.countDown();
            }
        };

        try (SubmissionPublisher<Integer> publisher =
                     new SubmissionPublisher<>()) {
            publisher.subscribe(subscriber);
            publisher.submit(10);
            publisher.submit(20);
            publisher.submit(30);
        }

        if (!done.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out");
        }
        if (failure.get() != null) {
            throw new IllegalStateException("Subscriber failed", failure.get());
        }
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac -encoding UTF-8 FlowBasicsDemo.java
java FlowBasicsDemo
```

Stdout khi chạy thành công:

```text
received=10
received=20
received=30
completed
```

**Giải thích:**

- `SubmissionPublisher` cung cấp thực thi sẵn cho `Flow.Publisher`; subscriber không phải tự quản lý queue hoặc đồng bộ callback.
- `onSubscribe` mở demand là 1. Sau mỗi item, `onNext` yêu cầu thêm một item. Do đó subscriber chủ động điều tiết quá trình nhận.
- `submit(...)` có thể trả về trước khi subscriber đã xử lý xong, vì delivery là bất đồng bộ. Không dùng `Thread.sleep(...)` tùy ý để “đợi đủ”; demo dùng `CountDownLatch` và timeout.
- `try-with-resources` đóng publisher và phát `onComplete` sau khi dữ liệu đã được xử lý theo thứ tự cho subscriber đó. `done.await(...)` giữ main thread sống đến khi nhận terminal signal.
- `AtomicReference<Throwable>` giúp truyền lỗi từ callback sang main để không báo thành công giả.

Lưu ý về output: ba dòng `received` có thứ tự xác định với **cùng một subscription**; executor có thể chọn thread nào chạy callback mà không ảnh hưởng thứ tự logic đó.

## 5. Ví dụ Java 17+: chứng minh không có demand thì chưa nhận onNext

Để thấy subscriber là bên cấp quyền nhận item, ví dụ sau **chủ động trì hoãn `request`**. `SubmissionPublisher` có thể giữ các item đã submit trong buffer ngay cả khi subscriber chưa yêu cầu.

Lưu tệp **`FlowDemandDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FlowDemandDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch firstReceived = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Flow.Subscription> control = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                control.set(subscription);
                subscribed.countDown();
            }

            @Override
            public void onNext(String item) {
                System.out.println("onNext: " + item);
                if ("A".equals(item)) firstReceived.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                failure.set(throwable);
                completed.countDown();
            }

            @Override
            public void onComplete() {
                System.out.println("onComplete");
                completed.countDown();
            }
        };

        try (SubmissionPublisher<String> publisher =
                     new SubmissionPublisher<>()) {
            publisher.subscribe(subscriber);
            if (!subscribed.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No onSubscribe");
            }

            publisher.submit("A");
            publisher.submit("B");

            System.out.println("request(1)");
            control.get().request(1);
            if (!firstReceived.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("First item not delivered");
            }

            System.out.println("request(1) again");
            control.get().request(1);
            publisher.close();

            if (!completed.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No completion");
            }
        }

        if (failure.get() != null) {
            throw new IllegalStateException("Flow failed", failure.get());
        }
    }
}
```

```bash
javac -encoding UTF-8 FlowDemandDemo.java
java FlowDemandDemo
```

Stdout khi chạy thành công:

```text
request(1)
onNext: A
request(1) again
onNext: B
onComplete
```

`firstReceived.await` buộc main đợi A được in trước khi gửi demand thứ hai. Nhờ vậy thứ tự output không phụ thuộc lịch chạy của executor. `publisher.close()` báo không còn item mới và gửi tín hiệu kết thúc sau các item đã được xếp hàng cho subscription. Đây là một demo với buffer không bão hòa: **không có demand chỉ ngăn callback `onNext`, không nhất thiết ngăn producer gọi `submit` ngay lập tức**.

## 6. SubmissionPublisher: buffer, executor và chính sách khi consumer chậm

`SubmissionPublisher<T>` là implementation có sẵn của `Flow.Publisher<T>` từ Java 9; lớp này thực hiện phát item **bất đồng bộ** cho các subscriber đang đăng ký.

### Khởi tạo

```java
// Đoạn minh họa, không phải chương trình độc lập:
SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

// Hoặc chỉ định Executor và dung lượng buffer mục tiêu cho mỗi subscriber:
SubmissionPublisher<String> custom =
        new SubmissionPublisher<>(executor, 64);
```

Biến `executor` ở ví dụ thứ hai là một `Executor` do ứng dụng cung cấp và phải được quản lý vòng đời riêng khi cần; không có khai báo nên đoạn trên chỉ minh họa API. Constructor mặc định thường sử dụng `ForkJoinPool.commonPool()` và dung lượng mặc định `Flow.defaultBufferSize()`. Không hard-code giá trị mặc định vì có thể phụ thuộc phiên bản/triển khai.

Mỗi subscriber có **buffer độc lập**. Khi publisher phát nhiều hơn tốc độ subscriber yêu cầu và xử lý, buffer của subscriber đó có thể đầy; dung lượng được thực thi có thể làm tròn so với con số truyền vào constructor. `getMaxBufferCapacity()` trả về dung lượng thực tế của publisher.

### `submit`, `offer` và `close`

| API | Hành vi và lựa chọn |
| --- | --- |
| `submit(item)` | Phát item không-null tới các subscriber hiện tại; có thể **block không ngắt được** khi tài nguyên/buffer chưa sẵn cho subscriber chậm. Kết quả trả về là ước lượng lag tối đa, không phải số item đã xử lý. |
| `offer(item, onDrop)` | Thử phát mà không chờ không giới hạn; khi thiếu chỗ, có thể drop cho một hoặc nhiều subscriber, gọi handler và thử lại một lần nếu handler trả `true`. |
| `offer(item, timeout, unit, onDrop)` | Cho phép đợi có giới hạn; sau đó áp dụng hành vi drop/retry phù hợp. |
| `close()` | Ngăn submit mới và phát `onComplete` cho subscribers hiện tại. |
| `closeExceptionally(error)` | Kết thúc bằng tín hiệu `onError`. |
| `estimateMaximumLag()` | Thông tin **ước lượng để quan sát**, không được dùng như khóa đồng bộ chính xác. |

Với `offer`, kết quả **âm** chỉ báo các lần drop không phát được; kết quả không âm là ước lượng lag, không nên diễn giải như một cam kết không mất dữ liệu tuyệt đối. `onDrop` có thể được gọi trong lúc publisher giữ khóa nội bộ; tránh xử lý phức tạp hoặc block trong handler.

**Trade-off quan trọng:**

- `submit` thường giữ được item theo semantics của publisher nhưng có thể khiến thread sản xuất bị chặn bởi một subscriber chậm.
- `offer` giúp producer phản hồi nhanh hơn khi thiếu chỗ, đổi lại **có thể mất item**. Chỉ dùng drop policy khi sản phẩm hoặc nghiệp vụ cho phép.
- Tăng buffer có thể làm giảm block/drop trong burst ngắn, nhưng làm tăng bộ nhớ; buffer lớn **không xử lý được consumer chậm liên tục**.
- Nhiều subscriber có demand khác nhau, buffer riêng và lịch callback riêng. Một subscriber chậm có thể tác động khả năng gọi `submit` do publisher phải xét tình trạng tài nguyên của từng subscription.

**Lưu ý:** “Publisher hỗ trợ backpressure” không đảm bảo toàn bộ hệ thống không bao giờ bị đầy bộ nhớ. Phải xem producer tạo item trước khi submit thế nào, có queue nào ở phía trước hay không, tốc độ downstream, dung lượng buffer và chính sách overflow.

## 7. Processor: kết nối các giai đoạn xử lý

Một pipeline thường gồm nguồn → xử lý → đích. `Flow.Processor<T,R>` là hợp đồng để một thành phần nhận item kiểu `T` rồi phát item kiểu `R`. Nó có thể chuyển đổi dữ liệu, lọc hoặc tổng hợp, nhưng **phải tự quản lý subscription, trạng thái, demand, lỗi và tín hiệu kết thúc**.

### Khung processor minh họa

```java
// Khung ý tưởng đặt bên trong một lớp; không phải chương trình hoàn chỉnh:
final class DoublingProcessor
        extends SubmissionPublisher<Integer>
        implements Flow.Processor<Integer, Integer> {

    private Flow.Subscription upstream;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        upstream = subscription;
        upstream.request(1);
    }

    @Override
    public void onNext(Integer item) {
        submit(item * 2);
        upstream.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        closeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        close();
    }
}
```

Đây là minh họa một phép biến đổi **một đầu vào → một đầu ra** dùng buffering của `SubmissionPublisher`. Nếu downstream chậm và buffer đầy, `submit` có thể chặn trước khi yêu cầu upstream tiếp. Đó là một cách nối backpressure đơn giản, nhưng việc gọi `request(1)` theo từng item **không thay thế** phân tích buffer, cancellation, vòng đời và xử lý exception.

Để triển khai processor thực tế, còn phải xử lý các trường hợp như nhiều downstream, cancel ở downstream làm ảnh hưởng upstream thế nào, overflow, rejected executor, race khi terminal signal đến và cleanup. Flow chỉ cung cấp interface; không cung cấp sẵn cơ chế truyền toàn bộ nhu cầu ngược qua mọi processor tùy ý.

## 8. Các nguyên tắc an toàn khi tự implement Publisher/Subscriber

**Không gửi onNext vượt demand.** Subscriber `request(3)` không có nghĩa publisher được phát bốn item rồi hứa xử lý sau. Publisher tùy chỉnh phải phối hợp thread và bộ đếm demand; khi cộng demand lớn cần tránh tràn `long`, thường dùng phép cộng bão hòa ở `Long.MAX_VALUE`.

**Đảm bảo callback theo thứ tự cho từng subscription.** Không gọi `onNext` đồng thời hoặc lộn thứ tự vào cùng một subscriber. JavaDoc `Flow.Publisher` yêu cầu thứ tự chặt chẽ và quan hệ *happens-before* giữa các tín hiệu của một subscription.

**Kết thúc đúng một lần.** Sau `onComplete` hoặc `onError` không được gửi thêm `onNext`. Sai `request(0)` hoặc `request(-1)` phải dẫn tới `onError(IllegalArgumentException)`.

**Tôn trọng cancel.** Khi bị hủy, dừng phát trong khả năng cho phép, giải phóng tham chiếu và ngừng công việc thừa. Do cancel là best effort, bên nhận cũng cần chuẩn bị cho những callback đã được lên lịch trước đó.

**Không block tùy tiện trong callback.** `onNext` xử lý quá lâu sẽ làm nghẽn executor, tăng lag và buffer. Nếu cần chuyển xử lý sang queue khác, queue đó cũng cần capacity/giới hạn; không thể né backpressure bằng cách tạo một queue vô hạn nằm bên trong `onNext`.

**Tính tới việc callback ném exception.** Một subscriber phải xử lý lỗi ứng dụng có chủ đích. Không giả định publisher tự khôi phục sau exception trong `onNext` hoặc sẽ tiếp tục các item như bình thường. Chính sách hủy, error handler và đóng tài nguyên phải được xác định.

**Không suy diễn toàn hệ thống từ một subscription.** Thứ tự của item của một subscriber được đảm bảo theo hợp đồng; thời điểm subscriber khác xử lý item có thể khác. Thiết kế dữ liệu chia sẻ phải đảm bảo thread safety độc lập.

## 9. Flow khác Stream và BlockingQueue thế nào?

| Công cụ | Dạng dữ liệu/tương tác | Điểm khác cần nhớ |
| --- | --- | --- |
| `java.util.stream.Stream` | Pipeline thao tác trên nguồn dữ liệu, thường được xử lý khi terminal operation chạy | Không có hợp đồng subscription/demand kiểu `Flow`; không phải một async event stream chỉ vì tên có “stream”. |
| `BlockingQueue` | Producer/consumer trao đổi qua hàng đợi hữu hạn hoặc không hữu hạn | Bên nhận có thể `take`, producer có thể `put`; backpressure qua capacity chứ không phải `request(n)`. |
| `Flow` | Giao diện phát/nhận bất đồng bộ theo subscription và demand | Định nghĩa hợp đồng tín hiệu, không chọn buffer/executor/overflow cho bạn. |
| `SubmissionPublisher` | Implementation publisher có thể dùng ngay | Hỗ trợ buffer, executor, `submit`/`offer`, multicast cho subscriber hiện tại. |
| `CompletableFuture<T>` | Biểu diễn một kết quả hoặc lỗi trong tương lai | Không cung cấp dòng nhiều item cùng hợp đồng demand. |

Nếu chỉ cần một queue công việc nhiều producer/consumer, `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` có thể dễ quản lý hơn. Nếu chỉ cần tính toán trên một danh sách sẵn có, `Stream` thường phù hợp hơn. Flow hữu ích khi cần **đầu ra nhiều item, bên nhận có thể yêu cầu số lượng, và các stage được kết nối bằng subscription**.

## 10. Thiết kế và giám sát pipeline thực tế

Trước khi chọn cấu hình, xác định:

1. **Nguồn:** dữ liệu đến từ đâu, có tạm dừng được không, có cần thứ tự và delivery guarantee nào?
2. **Subscriber:** công việc mỗi item mất bao lâu; có thể xử lý song song không; có cần giữ nguyên thứ tự không?
3. **Demand:** yêu cầu từng item hay theo lô; nên tăng demand ở thời điểm bắt đầu hay hoàn tất xử lý?
4. **Capacity:** publisher và từng stage có những buffer nào; tổng bộ nhớ tệ nhất theo số subscribers là bao nhiêu?
5. **Quá tải:** chặn, đợi có timeout, drop, retry, hay báo lỗi; tác động nghiệp vụ của mất item là gì?
6. **Lifecycle:** ai đóng publisher, ai hủy subscription, ai sở hữu `Executor`, làm gì khi một subscriber lỗi?
7. **Quan sát:** throughput, lag, allocation, thời gian callback, phần trăm drop, thời gian block của producer, `onError`/`cancel`.

Có thể dùng các phương thức ước lượng lag của `SubmissionPublisher` để quan sát và đo bổ sung ở application level, nhưng không dùng chúng làm cơ chế đồng bộ. Với pipeline dài, phải đo từng stage để tìm nơi dữ liệu tích tụ; chỉ nâng buffer ở stage nghẽn có thể chuyển áp lực sang stage tiếp theo.

### Virtual threads có thay thế backpressure không?

Không. Virtual threads giúp triển khai tác vụ blocking với nhiều luồng sống đồng thời ở chi phí thread thấp hơn platform threads, nhưng **không làm tài nguyên downstream vô hạn**. Một triệu tác vụ chờ DB, API hoặc queue vẫn chiếm bộ nhớ và có thể đẩy quá tải vào dịch vụ phía sau. Flow kiểm soát tốc độ phát thông qua demand; khi dùng virtual threads vẫn cần giới hạn kết nối, buffer và những tài nguyên hữu hạn khác.

## 11. Lỗi phổ biến và cách sửa

**Lỗi: gọi `subscribe` nhưng quên `request`.** Subscriber thường sẽ không nhận `onNext`. Sửa bằng cách phát yêu cầu hợp lý trong `onSubscribe` hoặc khi có khả năng tiếp tục nhận.

**Lỗi: coi `request(n)` là đặt lại số lượng.** Request là cộng demand còn thiếu, không xóa demand cũ. Theo dõi tổng yêu cầu, số item đã giao và demand chưa sử dụng.

**Lỗi: gọi `request(0)` để tạm dừng.** Không hợp lệ; hợp đồng yêu cầu `onError(IllegalArgumentException)`. Muốn tạm dừng, đơn giản không yêu cầu thêm sau khi tiêu thụ hết demand hiện tại.

**Lỗi: gọi `request(Long.MAX_VALUE)` rồi tưởng đã có backpressure hữu ích.** Đây gần như vô hạn; nếu consumer chậm, phải dựa vào buffering/overflow của triển khai thay vì demand giới hạn.

**Lỗi: dùng `submit` trong luồng nhạy độ trễ mà quên khả năng block.** Cân nhắc tác vụ phát riêng hoặc `offer` với timeout/drop policy chỉ khi phù hợp yêu cầu dữ liệu.

**Lỗi: nghĩ `close()` hoàn tất tức thì mọi callback.** `close` khởi phát kết thúc; trong xử lý bất đồng bộ, main cần cơ chế chờ terminal signal như `CountDownLatch` hoặc tương đương nếu kết quả còn quan trọng.

**Lỗi: biến một Processor thành queue vô hạn.** Nếu stage trung gian nhận upstream quá nhanh và đẩy xuống queue không giới hạn, backpressure thực chất bị phá vỡ ở stage đó.

**Lỗi: không hủy/đóng tài nguyên.** Chỉ giữ `Subscription` mà không gọi `cancel` khi tác vụ bị bỏ có thể khiến upstream tiếp tục tạo item. Khi producer kết thúc, cần `close` hoặc `closeExceptionally` phù hợp; quản lý vòng đời executor được truyền vào.

## 12. Câu hỏi tự kiểm tra

**Hỏi:** Bốn thành phần của Flow là gì?  
**Đáp:** `Publisher`, `Subscriber`, `Subscription` và `Processor`.

**Hỏi:** Subscriber gọi `request(5)`, sau đó `request(3)` và đã nhận hai item. Demand còn lại là bao nhiêu?  
**Đáp:** Sáu, nếu chưa có thay đổi nào khác: 5 + 3 − 2 = 6.

**Hỏi:** Có thể dùng `request(0)` để dừng tạm thời không?  
**Đáp:** Không; giá trị không dương dẫn tới `onError` với `IllegalArgumentException`. Để dừng cấp item mới, ngừng gọi `request` sau khi demand cũ đã được tiêu thụ.

**Hỏi:** Vì sao `submit` vẫn có thể block dù Flow là API bất đồng bộ?  
**Đáp:** Delivery tới subscriber là bất đồng bộ, nhưng `submit` có thể phải chờ tài nguyên khi buffer của subscriber đầy.

**Hỏi:** Hai subscriber nhận cùng item từ `SubmissionPublisher` có bắt buộc xử lý cùng lúc?  
**Đáp:** Không. Mỗi subscription có buffer/demand riêng và thời gian xử lý độc lập; thứ tự trong mỗi subscription được giữ theo hợp đồng nếu không drop hoặc lỗi.

**Hỏi:** `Flow.Processor` tự có toán tử map/filter và tự bảo đảm propagating demand chưa?  
**Đáp:** Chưa. Đây chỉ là interface; implementation quyết định xử lý và truyền backpressure.

**Hỏi:** Vì sao `CountDownLatch` hữu ích trong chương trình thử `SubmissionPublisher`?  
**Đáp:** Vì callback có thể đến sau khi `submit` trả về; latch cho main chờ tín hiệu hoàn tất hoặc lỗi với timeout rõ ràng.

**Ghi nhớ:** `Flow` cung cấp hợp đồng **Publisher → Subscription → Subscriber** với backpressure bằng `request(n)`; `SubmissionPublisher` là một implementation hữu ích để bắt đầu. Cần hiểu từng tín hiệu, demand, buffer, overflow, cancellation và lifecycle mới xây được pipeline không tích tụ dữ liệu ngoài kiểm soát.

## Tài liệu chính thức

- [Oracle Java 21 API — Flow](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html)
- [Oracle Java 21 API — Flow.Publisher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Publisher.html)
- [Oracle Java 21 API — Flow.Subscriber](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html)
- [Oracle Java 21 API — Flow.Subscription](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html)
- [Oracle Java 21 API — SubmissionPublisher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-002">Publisher, Subscriber, Subscription, Processor</a>

<details>
<summary>Xem chi tiết</summary>

# Publisher, Subscriber, Subscription và Processor trong Java Flow

## 1. Flow giải quyết vấn đề gì?

Khi một bên tạo dữ liệu nhanh hơn bên tiêu thụ, việc **đẩy dữ liệu không giới hạn** có thể làm hàng đợi ngày càng lớn, tăng RAM, độ trễ và cuối cùng gây quá tải. Trong lập trình đồng thời, ta cần một giao thức để bên nhận thông báo mình sẵn sàng xử lý bao nhiêu phần tử. Cơ chế đó gọi là **backpressure**.

Từ Java 9, package `java.util.concurrent` cung cấp lớp `Flow` với bốn interface lồng nhau:

- `Flow.Publisher<T>` — nơi phát các phần tử kiểu `T`.
- `Flow.Subscriber<T>` — nơi nhận và xử lý các phần tử kiểu `T`.
- `Flow.Subscription` — kết nối riêng giữa một publisher và một subscriber, mang tín hiệu **demand** (nhu cầu nhận thêm) và hủy.
- `Flow.Processor<T,R>` — vừa là subscriber nhận `T`, vừa là publisher phát `R`, thường dùng để biến đổi/lọc dữ liệu trong một pipeline.

`Flow` định nghĩa **giao thức và trách nhiệm giữa các thành phần**, không tự cung cấp hệ thống xử lý data stream hoàn chỉnh. Một triển khai sẵn có của JDK là `SubmissionPublisher<T>`. Các interface của Flow tương ứng với mô hình Reactive Streams; bài này chỉ sử dụng Java SE, không cần Spring hoặc thư viện reactive bổ sung.

Hình dung pipeline:

```text
       dữ liệu vào                          dữ liệu đầu ra
   ┌────────────────┐   onNext(T)   ┌───────────────────┐   onNext(R)  ┌────────────────┐
   │ Publisher<T>   │ ────────────► │ Processor<T,R>    │ ───────────►│ Subscriber<R>  │
   └────────────────┘               └───────────────────┘             └────────────────┘
            ▲                               ▲                                  │
            │ request(n)                    │ request(n)                       │
            └── Subscription đầu nguồn ────┘   Subscription đầu ra ◄──────────┘
```

Ở đường bên trái, processor là **subscriber** của publisher nguồn; ở đường bên phải, nó là **publisher** cho subscriber cuối. Hai bên có **subscription riêng**. Processor phải quyết định cách cân đối demand ở hai phía; interface không tự làm công việc đó.

## 2. Bốn vai trò và API quan trọng

| Vai trò | Phương thức | Trách nhiệm chính |
| --- | --- | --- |
| `Publisher<T>` | `subscribe(Subscriber<? super T>)` | Nhận đăng ký và thiết lập một subscription; phát tín hiệu phù hợp giao thức |
| `Subscriber<T>` | `onSubscribe(Subscription)` | Nhận subscription trước mọi callback khác của subscription đó; thường gọi `request(n)` |
| `Subscriber<T>` | `onNext(T)` | Nhận một phần tử đã được yêu cầu |
| `Subscriber<T>` | `onError(Throwable)` | Nhận tín hiệu lỗi kết thúc, không còn callback tiếp theo của subscription |
| `Subscriber<T>` | `onComplete()` | Nhận tín hiệu kết thúc bình thường, không còn callback tiếp theo của subscription |
| `Subscription` | `request(long n)` | Tăng demand còn thiếu thêm `n`; `n` phải dương |
| `Subscription` | `cancel()` | Yêu cầu dừng nhận; mang tính best-effort |
| `Processor<T,R>` | Kế thừa hai nhóm API | Nhận `T` từ upstream, xử lý và phát `R` xuống downstream |

**Chú ý đến kiểu generic:** `Publisher<? extends T>` tạo dữ liệu; `Subscriber<? super T>` có thể nhận `T`. `Processor<Integer,String>` nhận `Integer` từ upstream rồi phát `String` cho downstream. `Subscription` không mang kiểu dữ liệu vì chỉ điều khiển số phần tử được nhận.

### Một subscription gắn với một mối quan hệ phát–nhận

Một publisher có thể có nhiều subscriber. Mỗi subscriber thường có một `Subscription` và lượng demand riêng. Nếu subscriber A đã yêu cầu 10 phần tử, điều đó **không cấp thêm quyền nhận** cho subscriber B. Publisher cần tuân thủ nhu cầu riêng của từng subscriber.

`Publisher.subscribe(...)` có thể thiết lập kết nối bất đồng bộ; không thể mặc định rằng sau khi method trả về thì subscriber đã xử lý `onSubscribe` hoặc sẵn sàng nhận `onNext`. Với `SubmissionPublisher`, quá trình thông báo subscriber được lên lịch bất đồng bộ qua `Executor`.

## 3. Vòng đời và thứ tự tín hiệu

Một subscription điển hình diễn ra như sau:

```text
Publisher                      Subscriber
    │                               │
    │  onSubscribe(subscription)     │
    ├──────────────────────────────►│
    │             request(2)         │
    │◄──────────────────────────────┤
    │                               │
    │  onNext(itemA)                │
    ├──────────────────────────────►│   demand còn 1
    │  onNext(itemB)                │
    ├──────────────────────────────►│   demand còn 0
    │             request(1)         │
    │◄──────────────────────────────┤
    │  onNext(itemC)                │
    ├──────────────────────────────►│
    │  onComplete()                  │
    ├──────────────────────────────►│
    │                               │   kết thúc
```

Những bảo đảm thiết yếu:

1. `onSubscribe` được gửi trước bất kỳ `onNext`, `onError` hoặc `onComplete` nào của subscription tương ứng.
2. `onNext` chỉ được phát khi có demand được yêu cầu trước đó, và mỗi phần tử tiêu thụ **một** đơn vị demand.
3. Với một subscription, callback tới subscriber được gọi **tuần tự, đúng thứ tự**. Chúng có quan hệ *happens-before* theo bảo đảm giao thức; một subscriber nhận **nhiều** subscription hoặc đồng thời có truy cập từ bên ngoài vẫn cần xem lại đồng bộ.
4. Một subscription kết thúc qua `onError` **hoặc** `onComplete`, và sau tín hiệu kết thúc không có callback nào khác từ subscription đó.
5. `cancel()` yêu cầu ngừng nhận nhưng không đồng nghĩa một lời hứa sẽ nhận `onComplete()`; có thể vẫn có ít callback đang trên đường đến sau `cancel`.
6. `onNext` không được truyền `null` như một item hợp lệ trong Flow; `SubmissionPublisher.submit(null)` ném `NullPointerException`.

Với `request(n)`, `n > 0` bổ sung số lượng phần tử được phép gửi; các lần gọi cộng dồn demand. `request(0)` hoặc `request(-1)` là lỗi giao thức và subscriber được gửi `onError(IllegalArgumentException)`. `request(Long.MAX_VALUE)` thường được xem là nhu cầu gần như vô hạn; nó **bỏ ý nghĩa điều tiết qua demand** nên chỉ dùng khi consumer thực sự có thể theo kịp hoặc đã có kiểm soát tải khác.

### Demand là tín hiệu điều khiển, không phải bộ đệm

Nếu consumer gọi `request(3)`, producer **được phép phát tối đa ba phần tử tiếp theo** trước khi có demand mới, nhưng không buộc phải phát đủ ba phần tử. Producer có thể chưa có dữ liệu, phát ít hơn rồi hoàn tất hoặc phát tín hiệu lỗi. Demand không có nghĩa ba phần tử đã được lưu sẵn ở đâu đó.

Backpressure điều tiết **số callback `onNext` được phép phát**, nhưng một publisher vẫn có thể giữ dữ liệu trong buffer nội bộ hoặc nhận dữ liệu từ nguồn ngoài trước khi downstream xử lý. Cần kiểm soát thêm sức chứa buffer, chính sách quá tải và tốc độ đầu nguồn.

## 4. Ví dụ Java 21 chạy được: đủ bốn vai trò trong một pipeline

Ví dụ này tạo chuỗi:

```text
SubmissionPublisher<Integer>          DoublerProcessor<Integer,String>        PrintSubscriber<String>
           1, 2, 3           ───────►        "value=2", "value=4", ...  ───────► output
                ^                                  ^                                |
                | upstream request(1)              | downstream request(1)          |
                └──────────────────────────────────┘◄───────────────────────────────┘
```

`SubmissionPublisher` là `Publisher` thực tế có trong JDK; `DoublerProcessor` tự triển khai `Processor` bằng cách nhận từng `Integer`, chuyển thành `String` và ủy quyền quản lý subscription đầu ra cho một `SubmissionPublisher<String>` khác. `PrintSubscriber` yêu cầu **một** phần tử mỗi lần nhận xong phần tử trước.

Lưu nguyên chương trình vào `FlowFourRolesDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class FlowFourRolesDemo {

    static final class DoublerProcessor
            implements Flow.Processor<Integer, String>, AutoCloseable {

        private final SubmissionPublisher<String> output;
        private Flow.Subscription upstream;

        DoublerProcessor(ExecutorService executor) {
            // Bản thân output sẽ cung cấp Subscription cho downstream.
            this.output = new SubmissionPublisher<>(executor, 4);
        }

        @Override
        public void subscribe(Flow.Subscriber<? super String> subscriber) {
            output.subscribe(subscriber);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (upstream != null) {
                subscription.cancel(); // Ví dụ chỉ hỗ trợ một upstream.
                return;
            }
            upstream = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(Integer item) {
            try {
                output.submit("value=" + (item * 2));
                upstream.request(1);
            } catch (RuntimeException ex) {
                upstream.cancel();
                output.closeExceptionally(ex);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            output.closeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            output.close();
        }

        @Override
        public void close() {
            output.close();
        }
    }

    static final class PrintSubscriber implements Flow.Subscriber<String> {
        private final CountDownLatch terminal = new CountDownLatch(1);
        private Flow.Subscription subscription;
        private volatile Throwable failure;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(String item) {
            System.out.println(item);
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            System.out.println("complete");
            terminal.countDown();
        }

        void awaitResult() throws InterruptedException {
            if (!terminal.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Flow pipeline timed out");
            }
            if (failure != null) {
                throw new IllegalStateException("Flow pipeline failed", failure);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2);
             SubmissionPublisher<Integer> publisher =
                     new SubmissionPublisher<>(executor, 4);
             DoublerProcessor processor = new DoublerProcessor(executor)) {

            PrintSubscriber subscriber = new PrintSubscriber();

            // Đăng ký downstream trước, rồi mới nối nguồn vào processor.
            processor.subscribe(subscriber);
            publisher.subscribe(processor);

            for (int item = 1; item <= 3; item++) {
                publisher.submit(item);
            }

            // Không phát thêm dữ liệu; tín hiệu hoàn tất lan qua processor.
            publisher.close();

            // close() của publisher không bảo đảm subscriber đã xử lý xong.
            subscriber.awaitResult();
        }
    }
}
```

Chạy trên **JDK 21 trở lên** (không cần thư viện ngoài):

```bash
javac FlowFourRolesDemo.java
java FlowFourRolesDemo
```

Output khi không xảy ra lỗi/timeout:

```text
value=2
value=4
value=6
complete
```

Đầu ra này có thứ tự xác định nhờ tín hiệu `onNext` của từng subscription được gửi tuần tự. `CountDownLatch` đợi tín hiệu cuối thay vì đoán thời gian bằng `Thread.sleep(...)`. `try-with-resources` đảm bảo publisher, processor và executor được đóng khi kết thúc.

**Đọc code theo vai trò:**

- **Publisher:** `publisher` phát ba số nguyên, cấp phát subscription khi `processor` đăng ký; không gọi trực tiếp `subscriber.onNext(...)`.
- **Subscriber đầu vào:** `processor.onSubscribe` nhận subscription upstream và xin một phần tử. Sau `onNext`, processor chuyển đổi item rồi xin phần tử tiếp theo.
- **Processor:** biến đổi `Integer → String`; `output` giữ những phần tử đã biến đổi trong giới hạn buffer và quản lý demand downstream.
- **Subscriber cuối:** `PrintSubscriber` nhận subscription downstream, yêu cầu một item, xử lý rồi mới yêu cầu tiếp.
- **Terminal:** `publisher.close()` gửi kết thúc theo thứ tự, `processor.onComplete()` đóng output, và `PrintSubscriber.onComplete()` giải phóng latch.

**Giới hạn có chủ ý của ví dụ:** processor minh họa **một upstream**, một đường biến đổi đơn giản và delegate buffering cho `SubmissionPublisher`. Đây không phải processor tổng quát đã hoàn thiện các tình huống hủy downstream, hỗ trợ nhiều upstream, ghi nhận trạng thái lỗi phức tạp hoặc điều phối demand theo nhiều consumer. `output.submit(...)` vẫn có thể chặn nếu downstream chậm và buffer đầy. Khi xây dựng processor dùng thật, cần xác định rõ chính sách cancel, khả năng đóng tài nguyên, retry/drop và cách chuyển demand giữa hai phía.

## 5. Publisher: sản xuất và phát dữ liệu đúng giao thức

`Flow.Publisher<T>` chỉ có phương thức `subscribe(...)`. Việc thực sự đọc dữ liệu từ file/socket, lấy bản ghi DB, gọi API hoặc tạo event là trách nhiệm implementation.

Một publisher phù hợp cần:

- Cấp một `Subscription` cho mỗi subscriber được chấp nhận.
- Không phát `onNext` quá demand mà subscription đó đã nhận.
- Giữ thứ tự callback đối với mỗi subscription và kết thúc đúng một lần.
- Quản lý nguồn dữ liệu, bộ đệm, lỗi và hủy; không đợi mọi consumer vô hạn mà không có kế hoạch tài nguyên.
- Xác định rõ subscriber mới có nhận dữ liệu trước khi subscribe hay chỉ nhận dữ liệu tương lai. `SubmissionPublisher` gửi các item **được submit sau khi** subscriber đăng ký, không mặc định replay lịch sử.

### SubmissionPublisher đã cung cấp gì?

`java.util.concurrent.SubmissionPublisher<T>` là publisher bất đồng bộ sẵn có, phát các item **khác `null`** đến các subscriber hiện tại, dùng `Executor` để gửi callback. Mỗi subscriber có **buffer riêng**. Constructor có thể chỉ định `Executor` và buffer capacity; capacity thực thi có thể được làm tròn theo implementation.

```java
// Snippet: executor là Executor đã được ứng dụng tạo và quản lý.
SubmissionPublisher<String> publisher =
        new SubmissionPublisher<>(executor, 32);

publisher.subscribe(subscriber); // subscriber: Flow.Subscriber<String>
publisher.submit("event-1");
publisher.close();
```

Đoạn trên là **snippet**, vì cần khai báo/import `executor` và `subscriber` phù hợp. Một publisher chỉ có interface `Flow.Publisher` không nhất thiết có `submit()` hoặc `close()`; đó là API của `SubmissionPublisher` cụ thể.

**Sự khác nhau giữa `submit` và `offer`:**

| Phương thức | Hành vi khi buffer bị bão hòa | Cần lưu ý |
| --- | --- | --- |
| `submit(item)` | Có thể chặn **không ngắt được bằng InterruptedException** khi đợi tài nguyên buffer | Đơn giản, nhưng producer cũng có thể bị kẹt vì consumer chậm |
| `offer(item, onDrop)` | Có thể bỏ item cho một hay nhiều subscriber khi không còn khả năng cấp phát buffer | Giá trị trả về âm cho biết số lần drop, không phải “đã xử lý” |
| `offer(item, timeout, unit, onDrop)` | Chờ có hạn rồi xử lý trường hợp không còn chỗ | Handler có thể yêu cầu thử lại một lần; cần policy drop/retry/error rõ |
| `close()` | Không nhận thêm submit và gửi `onComplete` cho subscriber hiện tại | **Không** bảo đảm callback `onComplete` đã chạy xong ngay khi `close` trả về |
| `closeExceptionally(error)` | Kết thúc với tín hiệu `onError` | Dùng khi nguồn không thể tiếp tục bình thường |

`submit` trả về ước lượng **maximum lag**, không phải số item được consumer xử lý. Các phép đo như `estimateMaximumLag()` và `estimateMinimumDemand()` chủ yếu dành cho quan sát; chúng có thể phản ánh tình trạng cũ, không dùng để quyết định một thao tác check-then-act an toàn.

Điểm dễ bỏ sót: giới hạn 32 buffer là **mỗi subscriber**. Một publisher với 200 subscriber chậm không có nghĩa tổng bộ nhớ luôn bằng 32 item. Các đối tượng item có kích thước khác nhau; nhiều subscriber và nguồn phát có thể tiêu thụ nhiều bộ nhớ hơn dự kiến.

## 6. Subscriber: điều tiết nhu cầu và xử lý tín hiệu

`Flow.Subscriber<T>` gồm bốn callback. Cần thiết kế chính sách **request thêm lúc nào**:

- **`request(1)` trong `onSubscribe`, `request(1)` sau mỗi `onNext`**: dễ hiểu, số item chưa xử lý nhỏ; phù hợp với consumer đơn giản, throughput có thể bị giảm do giao tiếp demand thường xuyên.
- **Yêu cầu theo lô, ví dụ `request(32)`**: giảm số lần gửi demand, phù hợp consumer throughput cao; nhưng số item in-flight và chi phí lưu/đệm tăng, cần đo.
- **`request(Long.MAX_VALUE)`**: không tạo lực cản theo demand; chỉ phù hợp nếu hệ thống khác đã giới hạn tài nguyên và quá trình xử lý luôn theo kịp.

Khi xử lý, nên phân biệt **đã nhận item**, **đã hoàn thành xử lý item** và **đã xử lý thành công**. Nếu `onNext` chỉ đẩy item sang một asynchronous worker khác rồi xin thêm ngay, demand chỉ giới hạn số lần nhận callback, **không** giới hạn số worker đang chạy. Khi đó cần một hạn mức công việc đang xử lý và chỉ yêu cầu thêm sau khi tác vụ thực sự hoàn tất, hoặc áp dụng backpressure qua một queue có sức chứa cố định.

Không tự gọi trực tiếp `onNext`, `onError`, `onComplete` trên subscriber của người khác để “đẩy nhanh” pipeline; giao thức yêu cầu publisher/subscription điều phối callbacks. Đối với subscriber nhận **một** subscription, callbacks từ đúng subscription được bảo đảm tuần tự; nhưng thread thực thi không nhất thiết cố định, và mọi state được chia sẻ với thread khác vẫn cần bảo vệ phù hợp.

**Quản lý lỗi ở consumer:** nếu `onNext` ném exception, giao thức không đảm bảo mọi implementation tự chuyển nó thành `onError` theo cách ứng dụng mong muốn; `SubmissionPublisher` sẽ hủy subscription tương ứng và có cơ chế handler cho một số lỗi. Hãy xử lý lỗi nghiệp vụ một cách chủ động, quyết định fail stream hay giữ/retry item, và đảm bảo tài nguyên được đóng. Đừng ném exception trong `onError`/`onComplete`.

## 7. Subscription: demand và hủy theo từng subscriber

`Flow.Subscription` là **kênh điều khiển** để một subscriber nói với upstream “tôi còn có thể nhận bao nhiêu item”.

Ví dụ subscriber đã gọi `request(5)`. Publisher phát hai `onNext` thì demand chưa đáp ứng còn 3. Nếu subscriber gọi thêm `request(4)`, tổng outstanding demand là 7. Khi nhu cầu rất lớn, implementation thường dùng phép cộng bão hòa để không tràn `long`. `Long.MAX_VALUE` biểu diễn hiệu quả không giới hạn cho nhiều cách triển khai.

- `request(n <= 0)` là lỗi và sẽ dẫn tới `onError(IllegalArgumentException)` cho subscriber tương ứng.
- `cancel()` là yêu cầu ngừng nhận **best-effort**; có thể còn thông báo đang trên đường đến. Một subscription bị cancel **không bắt buộc** phát `onComplete` hay `onError`.
- Chỉ subscriber của subscription tương ứng được thiết kế để gọi `request/cancel`. Gọi chúng từ đối tượng không phải chủ sở hữu là ngoài hợp đồng sử dụng thông thường.
- Subscriber phải lưu subscription sau `onSubscribe` nếu muốn request thêm hoặc cancel sau này.
- Không đồng nhất `request(n)` với “tạo n thread”, “ghi n item vào queue” hay “consumer chắc chắn nhận đủ n item”.

**Ví dụ subscriber dừng sau ba phần tử** (snippet minh họa, không phải lớp chạy độc lập):

```java
private Flow.Subscription subscription;
private int received;

@Override
public void onSubscribe(Flow.Subscription s) {
    subscription = s;
    s.request(1);
}

@Override
public void onNext(String item) {
    use(item); // Hàm do ứng dụng định nghĩa.
    if (++received == 3) {
        subscription.cancel();
        return; // Không chờ onComplete sau cancel.
    }
    subscription.request(1);
}
```

Với ứng dụng thực, cần cân nhắc nếu lời gọi `use(item)` thất bại: có hoàn tác không, hủy hay báo lỗi, và khi nào giải phóng tài nguyên.

## 8. Processor: hai chiều demand, chuyển đổi và lỗi

`Processor<T,R>` kế thừa **`Subscriber<T>` và `Publisher<R>`**. Nó có thể map, filter, batch, aggregate, validate hoặc mã hóa dữ liệu. Điều khó nhất là **không chỉ biến đổi kiểu**, mà còn giữ đúng giao thức ở cả hai mặt:

```text
Source Publisher<T>
      │
      │ onNext(T), onComplete(), onError(...)
      ▼
Processor<T,R> [Subscriber<T>]
      │ transform/filter/buffer
      │ [Publisher<R>]
      ▼
Final Subscriber<R>
```

**Upstream subscription** điều khiển số `T` mà processor được nhận; **downstream subscription** điều khiển số `R` được phát xuống consumer. Nếu một `T` tạo ra ba `R`, không thể truyền thẳng downstream demand theo tỷ lệ 1:1. Nếu processor lọc bỏ nhiều `T`, có thể cần xin nhiều upstream item để đáp ứng một downstream item. Nếu processor gom 100 bản ghi thành một batch, giới hạn bộ nhớ và thời gian chờ của batch phải được thiết kế riêng.

Các trách nhiệm cần thiết trong processor:

1. Bảo đảm mỗi downstream subscription có demand riêng; không phát quá nhu cầu.
2. Truyền lỗi upstream xuống downstream một cách nhất quán khi pipeline không thể tiếp tục; sau lỗi không phát thêm dữ liệu.
3. Khi nguồn kết thúc, xử lý các item/buffer còn hợp lệ rồi phát `onComplete` cuối cùng.
4. Khi downstream cancel hoặc không còn người nhận, quyết định có cancel upstream, tiếp tục cho subscriber khác hay đóng nguồn tài nguyên.
5. Đặt sức chứa buffer và chiến lược quá tải: block producer, drop có chủ đích, reject hay ngừng nguồn; không để queue lớn vô hạn.
6. Xác định cách đồng bộ nếu có nhiều upstream hoặc nhiều downstream, và thứ tự các item phải được bảo tồn.

Ví dụ `DoublerProcessor` ở trên dùng `SubmissionPublisher<String>` để quản lý mặt phát. Phần nhận `Integer` chỉ request tiếp sau khi đã `submit` phần tử biến đổi. Đây là một mẫu học tập phù hợp cho mapping 1:1, nhưng không thay thế thiết kế demand theo đa số đường xử lý phức tạp.

## 9. Backpressure thực tế: xử lý chậm, buffer và executor

Giả sử publisher có tốc độ 1.000 item/giây, subscriber xử lý 100 item/giây. Hệ thống sẽ có chênh lệch trung bình khoảng 900 item/giây nếu nguồn tiếp tục tạo dữ liệu bằng tốc độ cũ. Nếu thiếu cơ chế khống chế, backlog có thể tăng liên tục.

**Flow giúp đặt nhu cầu ở ranh giới callback**, còn việc pipeline có thực sự giảm tải đầu nguồn hay không phụ thuộc cách publisher và processor được triển khai. Các chiến lược thường dùng:

| Tình huống | Hướng xử lý |
| --- | --- |
| Nguồn có thể tạm dừng (đọc từng chunk, iterator) | Chỉ đọc/tạo thêm khi có demand |
| Nguồn có tốc độ không điều khiển được (sự kiện ngoài) | Buffer hữu hạn, drop/reject có quan sát hoặc điều tiết ở cổng nhận |
| I/O chậm với SLA rõ ràng | Bounded in-flight, timeout, cancel khi cần và đo độ trễ |
| Một subscriber quá chậm trong luồng nhiều subscriber | Có policy riêng: buffer theo subscriber, drop, cô lập subscriber hoặc ngắt subscription |
| Processor có xử lý async | Yêu cầu thêm theo lượng việc thực sự hoàn thành, hoặc dùng bộ đếm in-flight/queue có chặn |

### Vai trò của Executor

`SubmissionPublisher` chuyển callbacks đến `Executor`. Hãy chọn executor phù hợp và tránh các trường hợp tự deadlock khi callback có thể chặn vì buffer đầy, trong khi mọi thread trong cùng executor đang chờ vào cùng pipeline. Với xử lý rất chậm, thiết kế cơ chế admission và tài nguyên downstream tách bạch.

Dùng virtual threads (Java 21+) có thể phù hợp cho tác vụ blocking I/O, nhưng **nhiều virtual threads không tự tạo backpressure hoặc giới hạn kết nối ngoài**. Nếu callback đưa việc sang virtual thread khác rồi lập tức request thêm, phải có hạn mức tác vụ và tài nguyên thật; nếu không backlog vẫn tăng dù mỗi virtual thread có chi phí thấp.

Không dùng `Thread.sleep` như một cơ chế backpressure. Sleep không thay đổi outstanding demand, không ràng buộc queue, cũng không bảo đảm nguồn sẽ dừng tạo dữ liệu.

## 10. Các lỗi thường gặp

**Quên gọi `request(n)` sau `onSubscribe`.** Subscriber đã đăng ký nhưng chưa cấp demand nên không nhận `onNext`; pipeline trông như “treo”. Hãy bắt đầu bằng request nhỏ và quan sát.

**Gọi `request(0)` hoặc `request(-n)` để tạm dừng.** Đây là lỗi giao thức, publisher gửi `onError`. Muốn tạm dừng, đơn giản **chưa request thêm** và quản lý outstanding demand đã cấp từ trước.

**Dùng `request(Long.MAX_VALUE)` mọi nơi.** Mất khả năng giới hạn callback theo nhu cầu downstream; dễ che giấu backlog ở các layer khác.

**Hiểu `submit` là consumer đã xử lý xong.** `SubmissionPublisher.submit` có thể chỉ mới đưa item vào buffer; `close` cũng không chờ subscriber hoàn tất. Dùng tín hiệu terminal, `CompletableFuture`, latch hoặc kết quả xử lý để chờ một cách đúng đắn.

**Giữ buffer vô hạn trong processor.** Flow interface không tự cấm một implementation lưu vô hạn item. Giới hạn capacity và xác định hành vi khi tràn.

**Không chuyển lỗi/hủy giữa hai đầu processor.** Downstream có thể chờ mãi khi upstream đã lỗi hoặc hủy; cần quy tắc terminal và cleanup rõ ràng.

**Dùng chung một state mutable cho nhiều subscription.** Callback được tuần tự **theo một subscription**, không bảo đảm đồng bộ giữa mọi subscriber và task khác. Mỗi subscription nên có state riêng hoặc phải bảo vệ shared state.

**Trả một item tới subscriber dù chưa có demand.** Custom publisher phải quản lý outstanding count; vi phạm giao thức có thể làm consumer quá tải hoặc gây lỗi khó đoán.

**Xem `cancel()` như `onComplete()`.** Cancel best-effort, không bắt buộc có terminal callback. Một consumer đợi `onComplete` sau cancel có thể chờ vô hạn.

**Tự tạo custom Publisher/Processor quá sớm.** Tự triển khai giao thức yêu cầu xử lý race giữa request/cancel/terminal, buffer và lỗi. Hãy ưu tiên thành phần JDK `SubmissionPublisher` hoặc thư viện đã kiểm chứng khi chức năng cần phức tạp hơn.

## 11. Kiểm chứng và quan sát một pipeline

Thử các tình huống có chủ đích:

1. Subscriber không request: không được nhận `onNext`.
2. Subscriber request(1): không được nhận quá một `onNext` trước khi request tiếp.
3. Gọi `request(0)`: đúng implementation phải báo lỗi giao thức, không âm thầm phát dữ liệu.
4. Publisher kết thúc bình thường: subscriber nhận `onComplete` đúng một lần, không còn `onNext` sau đó.
5. Publisher kết thúc lỗi: subscriber nhận `onError`; không đồng thời nhận `onComplete`.
6. Cancel giữa chừng: dừng phát theo best-effort, tránh chờ terminal bắt buộc.
7. Producer nhanh, consumer chậm: kiểm tra buffer giới hạn, thời gian `submit` bị chặn hoặc số item bị drop khi `offer`.
8. Processor filter/map/batch: kiểm tra đúng thứ tự, đúng số item, tính nhất quán khi upstream lỗi hoặc downstream cancel.
9. Subscriber ném exception: kiểm tra hành vi implementation, cleanup, và không rò subscription/resource.
10. Nhiều subscriber: demand, lỗi và cancel của từng subscription được quản lý độc lập.

Nên đo: tốc độ phát/nhận item, **outstanding demand** (nếu implementation có thể ghi nhận an toàn), độ trễ xử lý item, dung lượng queue/buffer, số item drop/retry, thời gian chờ `submit`, subscriptions active/cancelled và số lần lỗi terminal. Các API `estimate...` của `SubmissionPublisher` chỉ cho **ước lượng quan sát**, không phải một nguồn đồng bộ chính xác.

## 12. Ghi nhớ

- **Publisher** tạo và phát item; **Subscriber** xử lý và phát tín hiệu demand; **Subscription** là hợp đồng điều khiển cho từng cặp phát–nhận; **Processor** kết hợp hai vai trò để xây pipeline.
- `request(n)` là **quyền nhận tối đa n `onNext` thêm**, không phải cam kết sẽ nhận đủ n hoặc đã có n item trong buffer.
- `onSubscribe` đến trước các callback khác; `onError` và `onComplete` là terminal. `cancel` chỉ yêu cầu dừng, không hứa terminal callback.
- `SubmissionPublisher` cho sẵn cơ chế async delivery và buffering hữu hạn theo từng subscriber; vẫn phải lựa chọn chính sách block/drop/error và chờ kết thúc đúng cách.
- Backpressure có hiệu lực khi **toàn đường pipeline** tôn trọng demand và giới hạn nguồn/buffer/in-flight. Flow interface một mình không giải quyết tất cả bài toán quá tải.

## Tài liệu chính thức

- Oracle, [Flow API — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html): bốn interface, nguyên tắc demand và ví dụ subscriber.
- Oracle, [Publisher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Publisher.html) và [Subscriber](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html): callback, thứ tự và terminal.
- Oracle, [Subscription](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html): `request(long)`, `cancel()`, lỗi với demand không dương.
- Oracle, [Processor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Processor.html): kết hợp Subscriber và Publisher.
- Oracle, [SubmissionPublisher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html): buffer mỗi subscriber, `submit`, `offer` và vòng đời.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-003">request(n) và demand-driven consumption</a>

<details>
<summary>Xem chi tiết</summary>

# `request(n)` và demand-driven consumption trong Java Flow

## 1. Vấn đề cần giải quyết

Giả sử một nguồn phát ra 100.000 sự kiện/giây nhưng thành phần xử lý chỉ theo kịp 2.000 sự kiện/giây. Nếu nguồn cứ đẩy dữ liệu và bên nhận cứ phải nhận, hàng đợi có thể lớn dần, tăng độ trễ, tiêu tốn bộ nhớ hoặc làm hệ thống quá tải. Cơ chế **backpressure** trong mô hình Publisher–Subscriber cho phép phía nhận chủ động cho biết mình sẵn sàng nhận thêm bao nhiêu phần tử.

Trong Java, API chuẩn `java.util.concurrent.Flow` (từ Java 9) mô tả giao thức này. Trọng tâm của bài là **`Flow.Subscription.request(long n)`**: Subscriber cấp thêm quyền gửi (demand) cho Publisher. Publisher chỉ được gọi `onNext` khi subscription còn demand và không được gửi nhiều phần tử hơn lượng đã được yêu cầu. Đây là cơ chế điều phối số lượng phần tử giữa hai phía, không phải cơ chế giới hạn tốc độ theo đơn vị thời gian.

## 2. Bốn vai trò và vòng đời của subscription

| Vai trò | Trách nhiệm |
|---|---|
| `Flow.Publisher<T>` | Nhận đăng ký qua `subscribe(subscriber)`, cung cấp các phần tử và tín hiệu kết thúc/lỗi. |
| `Flow.Subscriber<T>` | Nhận `onSubscribe`, `onNext`, `onError`, `onComplete`; quyết định khi nào yêu cầu thêm. |
| `Flow.Subscription` | Liên kết một Publisher với một Subscriber; cung cấp `request(n)` và `cancel()`. |
| `Flow.Processor<T,R>` | Vừa là Subscriber ở phía upstream, vừa là Publisher ở phía downstream; cần phối hợp demand qua hai phía. |

Một luồng tín hiệu thông thường:

```text
Publisher                   Subscription                  Subscriber
    |                            |                             |
    | -- onSubscribe(s) --------------------------------------> |
    |                            | <-------- request(2) ------- |
    | --------------------- onNext(item 1) ------------------> |
    | --------------------- onNext(item 2) ------------------> |
    |           (demand còn lại = 0; dừng gửi onNext)          |
    |                            | <-------- request(1) ------- |
    | --------------------- onNext(item 3) ------------------> |
    | --------------------- onComplete() --------------------> |
```

`onSubscribe` diễn ra trước các tín hiệu dữ liệu hoặc kết thúc. Một subscription **khởi đầu với demand bằng 0**: chỉ đăng ký chưa có nghĩa đã được phép gửi item. Sau `onError` hoặc `onComplete`, luồng tín hiệu đã kết thúc và không có `onNext` tiếp theo.

## 3. Chính xác `request(n)` có nghĩa gì?

`subscription.request(n)` với `n > 0` **cộng thêm** `n` quyền nhận phần tử vào demand *chưa được đáp ứng* của subscription đó. Mỗi lần Publisher phát thành công một `onNext`, nó tiêu thụ một quyền. Đây không phải lệnh “gửi đúng n phần tử ngay bây giờ”, cũng không phải đặt lại cửa sổ demand thành n.

Ví dụ tuần tự:

| Hành động | Demand chưa đáp ứng | Ý nghĩa |
|---|---:|---|
| `onSubscribe(s)` | 0 | Chưa được phép phát `onNext`. |
| `s.request(2)` | 2 | Tối đa hai item có thể được gửi. |
| Nhận một `onNext` | 1 | Một quyền đã được sử dụng. |
| `s.request(3)` | 4 | Cộng thêm 3 vào lượng còn lại là 1. |
| Nhận bốn `onNext` | 0 | Phải chờ yêu cầu mới để gửi tiếp. |

Các quy tắc cần nhớ:

- **`request(n > 0)` là cộng dồn.** Hai lần `request(3)` liên tiếp có thể cho phép tối đa sáu item, chứ không phải ba. Publisher có thể gửi ít hơn nếu chưa có dữ liệu, đã kết thúc hoặc bị hủy.
- **`request(0)` và `request(n < 0)` là sai giao thức.** Theo hợp đồng `Flow.Subscription`, Subscriber sẽ nhận `onError` với `IllegalArgumentException`. Muốn tạm ngừng cấp thêm demand, hãy *không gọi* `request`; không dùng `request(0)` để pause.
- **`request(Long.MAX_VALUE)` thường được xem như yêu cầu không giới hạn.** Chỉ dùng khi bên nhận thật sự có thể chịu được luồng dữ liệu hoặc có cách kiểm soát riêng.
- **Demand là theo từng subscription.** Hai Subscriber của cùng Publisher có thể có số quyền nhận khác nhau; không được lấy demand của Subscriber A để phát dữ liệu cho B.
- **Tín hiệu kết thúc không phải item.** `onComplete`/`onError` không tiêu thụ demand; một nguồn có thể kết thúc dù không còn phần tử được yêu cầu. Chúng chỉ được phát theo quy tắc vòng đời của Publisher.

Có thể hình dung bất biến an toàn ở một subscription đang hoạt động là **số `onNext` đã gửi không vượt tổng số phần tử đã được yêu cầu** (với quy tắc xử lý bão hòa khi demand rất lớn). Trong implementation thực tế, bộ đếm phải chống tràn số và an toàn trước truy cập đồng thời; không tự cộng `long` theo cách có thể overflow sang số âm.

## 4. Ví dụ 1 — Nhìn thấy demand mở và đóng cửa gửi dữ liệu

Ví dụ sau dùng một Publisher tối giản, chạy **đồng bộ trên một thread**, để quan sát chính xác lúc nào item được phát. Nó dành cho học tập: không dùng implementation này như Publisher production đa luồng, vì chưa xử lý đồng thời/reentrancy khi `request` được gọi lồng nhau.

Lưu với tên `ManualDemandDemo.java`:

```java
import java.util.Objects;
import java.util.concurrent.Flow;

public class ManualDemandDemo {
    static final class FixedPublisher implements Flow.Publisher<Integer> {
        @Override
        public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
            Objects.requireNonNull(subscriber);

            subscriber.onSubscribe(new Flow.Subscription() {
                private int next = 1;
                private boolean stopped;

                @Override
                public void request(long n) {
                    if (stopped) return;

                    if (n <= 0) {
                        stopped = true;
                        subscriber.onError(
                            new IllegalArgumentException("n must be > 0"));
                        return;
                    }

                    long delivered = 0;
                    while (delivered < n && next <= 3 && !stopped) {
                        subscriber.onNext(next++);
                        delivered++;
                    }

                    if (next > 3 && !stopped) {
                        stopped = true;
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    stopped = true;
                }
            });
        }
    }

    public static void main(String[] args) {
        Flow.Subscription[] subscription = new Flow.Subscription[1];

        new FixedPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription[0] = s;
                System.out.println("Đăng ký; demand = 0");
            }

            @Override
            public void onNext(Integer item) {
                System.out.println("onNext " + item);
            }

            @Override
            public void onError(Throwable error) {
                System.out.println("onError " + error.getClass().getSimpleName());
            }

            @Override
            public void onComplete() {
                System.out.println("onComplete");
            }
        });

        System.out.println("request(2)");
        subscription[0].request(2);
        System.out.println("request(1)");
        subscription[0].request(1);
    }
}
```

Chạy với Java 17 trở lên:

```bash
javac ManualDemandDemo.java
java ManualDemandDemo
```

Kết quả chuẩn trong ví dụ đồng bộ:

```text
Đăng ký; demand = 0
request(2)
onNext 1
onNext 2
request(1)
onNext 3
onComplete
```

Sau lần `request(2)`, nguồn chỉ gửi 1 và 2 rồi dừng, dù còn sẵn giá trị 3. Lần `request(1)` tiếp theo mới mở quyền gửi giá trị 3. `onComplete` xuất hiện khi nguồn đã phát hết dữ liệu. Nếu thay `request(1)` cuối bằng `request(0)`, ví dụ phát `onError IllegalArgumentException`, không phải `onComplete`.

**Giới hạn của ví dụ:** vì `request` thực hiện vòng lặp ngay trên thread gọi, đừng cho `onNext` của ví dụ này gọi lại `request` hoặc gọi từ nhiều thread. Publisher dùng thực tế cần điều phối một vòng phát (drain loop), serialize các callback, giữ bộ đếm demand thread-safe và xử lý hủy/kết thúc đúng một lần.

## 5. Ví dụ 2 — Consumer chỉ xin phần tử tiếp theo sau khi xử lý xong

`SubmissionPublisher<T>` là implementation chuẩn có sẵn trong `java.util.concurrent`. Nó chuyển tín hiệu bất đồng bộ tới Subscriber và quản lý buffer theo từng Subscriber. Subscriber có thể bắt đầu bằng `request(1)`, rồi sau mỗi `onNext` xử lý xong mới `request(1)` tiếp.

Lưu với tên `OneByOneDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class OneByOneDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);

        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    subscription = s;
                    s.request(1); // Cấp một quyền nhận ban đầu
                }

                @Override
                public void onNext(Integer item) {
                    System.out.println("Nhận " + item);
                    // Chỉ cấp thêm quyền sau khi xử lý item hiện tại xong
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println("Lỗi: " + error);
                    finished.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("onComplete");
                    finished.countDown();
                }
            });

            publisher.submit(1);
            publisher.submit(2);
            publisher.submit(3);
        } // close() thông báo không còn item mới

        if (!finished.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Subscriber chưa kết thúc");
        }
    }
}
```

Chạy:

```bash
javac OneByOneDemo.java
java OneByOneDemo
```

Khi hoàn tất bình thường, các dòng stdout theo thứ tự:

```text
Nhận 1
Nhận 2
Nhận 3
onComplete
```

Điểm cần phân biệt: `SubmissionPublisher.submit(item)` **có thể đặt item vào buffer ngay khi Subscriber chưa có demand để nhận**. Nó chỉ bảo đảm callback `onNext` tuân theo demand; không có nghĩa `submit` của Producer chỉ chạy sau mỗi `request(1)`. Khi buffer đầy, `submit` có thể block để chờ tài nguyên; `offer` có chính sách xử lý khác (có thể bỏ item). Vì vậy, `request(1)` hạn chế số item đang được trao tới Subscriber, **không tự động bảo đảm toàn bộ upstream không sản xuất trước, không có buffer hoặc không tiêu tốn bộ nhớ**.

Trong code bất đồng bộ, `CountDownLatch` giúp `main` chờ tín hiệu kết thúc. Đừng dùng `Thread.sleep(...)` để “đoán” rằng callback đã chạy xong.

## 6. Chọn chiến lược cấp demand

**Xin từng item:**

```java
@Override
public void onSubscribe(Flow.Subscription s) {
    subscription = s;
    s.request(1);
}

@Override
public void onNext(Event event) {
    process(event);        // xử lý đồng bộ; có thể tốn thời gian
    subscription.request(1); // chỉ xin tiếp sau khi xử lý thành công
}
```

Đoạn trên là **mã minh họa bên trong một Subscriber**, không phải lớp Java hoàn chỉnh. Phù hợp khi mỗi item đắt, muốn kiểm soát chặt, nhưng việc xin từng item có thể làm giảm throughput. Nếu `process` thất bại, phải có chính sách báo lỗi/hủy hoặc xử lý lỗi rõ ràng; không âm thầm làm mất item và cũng không cấp thêm demand vô điều kiện.

**Xin theo lô (window/batch):**

```java
private static final int BATCH_SIZE = 32;
private Flow.Subscription subscription;
private int processedInBatch;

@Override
public void onSubscribe(Flow.Subscription s) {
    subscription = s;
    s.request(BATCH_SIZE);
}

@Override
public void onNext(Event event) {
    process(event);
    if (++processedInBatch == BATCH_SIZE) {
        processedInBatch = 0;
        subscription.request(BATCH_SIZE);
    }
}
```

Đây cũng là đoạn trong một Subscriber, với `Event` và `process` do ứng dụng tự định nghĩa. Nó cho phép tối đa 32 quyền nhận đang mở, rồi bổ sung 32 quyền sau mỗi lô. Trong thực tế có thể bổ sung sớm hơn (ví dụ khi đã xử lý khoảng 75% cửa sổ) để tránh thời gian chờ giữa các lô. Chọn `BATCH_SIZE` theo bộ nhớ, chi phí mỗi item, độ trễ và thông lượng đo được.

**Yêu cầu không giới hạn:**

```java
subscription.request(Long.MAX_VALUE);
```

Cách này đơn giản nhưng không tạo cơ chế giới hạn demand phía Subscriber. Tránh dùng mặc định cho nguồn vô hạn hoặc công việc nặng.

Một Subscriber có thể yêu cầu thêm trong `onSubscribe`, `onNext` hoặc tại thời điểm khác khi tài nguyên sẵn sàng. Với xử lý async trong `onNext`, chỉ gọi `request` tiếp **sau khi thao tác async thật sự hoàn tất** nếu mục tiêu là giới hạn số công việc đang xử lý; gọi ngay sau khi enqueue task sẽ chỉ giới hạn số callback tức thời, không giới hạn số task đã enqueue.

## 7. Backpressure không đồng nghĩa mọi thứ đã an toàn

Cần phân biệt ba đại lượng:

| Đại lượng | Nó đo điều gì? | Do ai kiểm soát? |
|---|---|---|
| **Demand** | Số quyền phát `onNext` chưa dùng của một subscription. | Subscriber cấp qua `request(n)`; Publisher thực thi. |
| **Buffer/queue size** | Số item đã được tạo/nhận nhưng đang chờ phân phối hoặc xử lý. | Thiết kế Publisher/Processor, kích thước buffer, chính sách overflow. |
| **Processing concurrency/rate** | Bao nhiêu tác vụ đang chạy, xử lý nhanh đến đâu. | Executor, worker, giới hạn đồng thời, thuật toán ứng dụng. |

Khi ghép nhiều bước `Publisher -> Processor -> Subscriber`, mỗi liên kết có subscription và demand riêng. Processor phải vừa tôn trọng demand downstream, vừa quyết định lúc nào xin thêm upstream, và có thể cần buffer hữu hạn. Nếu Processor luôn `request(Long.MAX_VALUE)` ở upstream nhưng downstream xin từng item, buffer nội bộ vẫn có thể tăng mạnh.

Với nhiều Subscriber, `SubmissionPublisher` quản lý buffer/demand riêng cho từng người nhận; Subscriber chậm có thể ảnh hưởng tới chính sách submit/offer và tài nguyên tổng. Không coi “có Flow API” là chứng cứ tự động không thể quá tải.

## 8. Lỗi thường gặp và cách sửa

| Lỗi | Hệ quả | Cách đúng |
|---|---|---|
| Quên `request` trong `onSubscribe` và không xin ở đâu khác. | Không nhận `onNext` dù nguồn có item. | Cấp demand dương khi thực sự sẵn sàng. |
| Gọi `request(0)` để tạm dừng. | Vi phạm giao thức, dẫn tới `onError(IllegalArgumentException)`. | Ngừng xin thêm; xử lý những quyền đã cấp trước đó. |
| Hiểu `request(5)` là “phải nhận đúng 5 item ngay”. | Logic chờ sai, dễ treo khi nguồn chỉ có 3 item. | Hiểu là cho phép **tối đa** 5 item nữa; theo dõi tín hiệu terminal. |
| Gọi `request(1)` ngay khi mới enqueue xử lý async. | Công việc chờ xử lý vẫn có thể tích lũy. | Xin tiếp sau completion thực của công việc nếu giới hạn in-flight. |
| Dùng `request(Long.MAX_VALUE)` mặc định rồi nghĩ đã có backpressure hữu hạn. | Mất khả năng hạn chế item qua demand. | Dùng cửa sổ hữu hạn khi cần kiểm soát. |
| Viết Publisher tự chế với `long demand += n` không khóa/atomic. | Race condition hoặc tràn số, phát quá demand. | Dùng implementation tin cậy; nếu tự viết, cần atomic/saturating arithmetic và cơ chế drain serialize. |
| Tưởng `cancel()` dừng tất cả callback tức thì. | Có thể còn item đang in-flight. | Xem cancel là best-effort; xử lý an toàn callback đến sát thời điểm hủy. |

Một sự tinh tế: `onNext` cho cùng một Subscriber cần được phát tuần tự theo hợp đồng `Flow`, nhưng `request` và `cancel` có thể được gọi từ thread khác so với thread đang phát. Người viết Publisher cần đảm bảo an toàn bộ nhớ, đồng bộ hóa và không gửi vượt demand; người viết Subscriber không nên giả định các callback luôn chạy trên thread của `main`.

## 9. Tự kiểm tra hiểu biết

1. Ban đầu demand là 0; gọi `request(4)`, nhận 2 item, rồi gọi `request(3)`. **Còn bao nhiêu quyền?** Trả lời: `4 - 2 + 3 = 5`.
2. Đã gọi `request(10)`, nhưng nguồn chỉ còn 3 item rồi kết thúc. **Có phải lỗi không?** Không. Demand là giới hạn trên, không phải lời hứa sẽ có đủ dữ liệu; `onComplete` là hợp lệ.
3. `request(0)` có phải cách pause? **Không.** Đây là yêu cầu không hợp lệ, gây `onError`.
4. Vì sao một Subscriber `request(1)` vẫn có thể gặp memory pressure? Vì Producer/Processor có thể sản xuất trước, buffer dữ liệu hoặc tạo task async nhanh hơn tốc độ hoàn tất; demand chỉ là một phần của thiết kế.

**Ghi nhớ:** `request(n)` là tín hiệu “tôi cho phép nhận thêm tối đa n phần tử”, và các lần gọi cộng dồn vào demand còn lại. Một hệ thống ổn định cần kết hợp quy tắc demand với giới hạn buffer, chính sách overflow, kiểm soát tác vụ đang xử lý và xử lý đúng terminal/cancel.

## Tham khảo Java SE 17

- [`Flow` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.html)
- [`Flow.Subscription` — request/cancel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.Subscription.html)
- [`SubmissionPublisher` — buffer, submit, offer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-004">onSubscribe(), onNext(), onError(), onComplete()</a>

<details>
<summary>Xem chi tiết</summary>

# onSubscribe(), onNext(), onError(), onComplete() trong Java Flow.Subscriber

## 1. Ngữ cảnh: Flow và backpressure trong Java SE

Từ Java 9, `java.util.concurrent.Flow` cung cấp các interface trao đổi dữ liệu bất đồng bộ theo mô hình Reactive Streams. Khi một nguồn tạo dữ liệu nhanh hơn bên nhận có thể xử lý, cơ chế **backpressure** cho bên nhận quyền điều tiết số lượng phần tử được chuyển tới.

- `Flow.Publisher<T>`: nguồn cung cấp phần tử kiểu `T`, cho phép một hoặc nhiều bên đăng ký.
- `Flow.Subscriber<T>`: bên nhận phần tử và các tín hiệu vòng đời.
- `Flow.Subscription`: quan hệ giữa Publisher và một Subscriber cụ thể; `request(long n)` xin nhận thêm phần tử, `cancel()` yêu cầu ngừng nhận.
- `Flow.Processor<T, R>`: vừa nhận dữ liệu kiểu `T` vừa phát kiểu `R`, nhưng không cần để hiểu Subscriber cơ bản.

Một `Flow.Subscriber<T>` cần hiện thực đầy đủ bốn callback:

```java
void onSubscribe(Flow.Subscription subscription);
void onNext(T item);
void onError(Throwable throwable);
void onComplete();
```

Các callback này **được Publisher/Subscription gọi**. Subscriber bình thường không tự gọi chúng để "đẩy" dữ liệu, mà chủ động gọi `request` hoặc `cancel` trên Subscription được trao. Mọi kiến thức dưới đây thuộc **Java concurrency nói chung**, không sử dụng API Spring.

| Callback | Ý nghĩa | Việc thường làm |
| --- | --- | --- |
| `onSubscribe(subscription)` | Đã thiết lập quan hệ nhận dữ liệu | Lưu subscription, yêu cầu demand ban đầu |
| `onNext(item)` | Đã có một phần tử hợp lệ để chuyển tới | Xử lý item, quyết định thời điểm xin thêm |
| `onError(error)` | Upstream gặp lỗi không thể tiếp tục | Ghi nhận lỗi, giải phóng tài nguyên, kết thúc |
| `onComplete()` | Upstream kết thúc bình thường | Dọn tài nguyên, báo hoàn thành |

## 2. Trật tự lời gọi và trạng thái của subscription

Trên **một subscription**, các callback tuân thủ luồng tín hiệu sau:

```text
subscribe(subscriber)
       |
       v
onSubscribe(subscription)
       |
       +-- request(n > 0) --> onNext(item) --> onNext(item) --> ...
       |                         ^                   |
       |                         +---- request thêm -+
       |
       +-- cancel() -------> ngừng nhận (best-effort)

Nhánh kết thúc bình thường: ... -> onComplete()
Nhánh kết thúc vì lỗi:      ... -> onError(throwable)
```

Những quy tắc quan trọng:

1. **`onSubscribe` được gọi trước** các callback khác của subscription tương ứng. Mỗi subscription có demand và vòng đời riêng.
2. **Không có `onNext` nếu không có demand** từ `request(n > 0)` với Publisher tuân thủ giao thức. Mỗi `onNext` tiêu thụ một đơn vị demand.
3. **Lời gọi callback trên cùng subscription có thứ tự nghiêm ngặt (happens-before)**. Chúng không chồng lấn như những lời gọi callback đồng thời cho cùng subscription; tuy nhiên không có bảo đảm tất cả chạy trên một thread cố định. Nhiều subscription khác nhau vẫn có thể chạy song song.
4. **`onError` và `onComplete` loại trừ nhau**: mỗi subscription nhận tối đa một tín hiệu kết thúc; sau tín hiệu terminal không có thêm callback. Một subscription chưa kết thúc hoặc đã bị hủy có thể không nhận terminal.
5. **`cancel()` là yêu cầu dừng best-effort**. Một số thông điệp đang chuyển vẫn có thể đến sau lệnh hủy, và lệnh hủy không bắt buộc sinh ra `onComplete` hay `onError`.

Bảng trạng thái gợi ý cho logic bên nhận:

| Trạng thái | Tín hiệu / hành động | Kết quả |
| --- | --- | --- |
| Chưa đăng ký | `Publisher.subscribe(...)` | Chờ `onSubscribe` |
| Đang hoạt động | `onSubscribe` → `request(n)` | Có quyền nhận tối đa n item |
| Đang hoạt động | `onNext` | Xử lý item, demand giảm 1 |
| Đang hoạt động | `request(n > 0)` | Demand chưa dùng tăng n |
| Đang hoạt động | `onError` | Kết thúc lỗi |
| Đang hoạt động | `onComplete` | Kết thúc thành công |
| Đang hoạt động | `cancel()` | Yêu cầu ngừng, không chờ terminal bắt buộc |

Publisher tùy biến có trách nhiệm giữ các bất biến này. Nếu Publisher vi phạm hợp đồng, Subscriber không thể mặc định tín hiệu nhận được vẫn hợp lệ.

## 3. onSubscribe(Flow.Subscription): nhận quyền điều khiển

`onSubscribe` được gọi đầu tiên, trao cho Subscriber một `Flow.Subscription`. Subscriber lưu nó để có thể yêu cầu dữ liệu hoặc hủy. Ví dụ là một phần của lớp `implements Flow.Subscriber<String>`:

```java
private Flow.Subscription subscription;

@Override
public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
}
```

`request(1)` cho phép Publisher gọi **tối đa một `onNext`** bổ sung. Nó không yêu cầu Publisher phải tạo ngay một phần tử, cũng không bảo đảm luôn có dữ liệu. Nếu Subscriber không gọi `request` thì Publisher tuân thủ giao thức sẽ không chuyển phần tử bằng `onNext`, kể cả khi nguồn đã có dữ liệu trong bộ đệm.

### request(n) được cộng dồn như thế nào?

`Flow.Subscription.request(long n)` cộng n vào demand chưa được phục vụ. Nếu đang có demand 2 và gọi `request(3)`, bên phát có thể chuyển tối đa 5 phần tử nữa trước khi cần demand mới. Tổng demand lớn có thể được giới hạn ở mức bão hòa, tránh tràn số học.

- `n > 0`: yêu cầu hợp lệ.
- `n == 0` hoặc `n < 0`: **không phải cách pause**. Hợp đồng yêu cầu phát `onError` với `IllegalArgumentException`.
- `n == Long.MAX_VALUE`: thường được xem là demand thực tế không giới hạn; chỉ nên dùng khi consumer chấp nhận cách cấp quyền này.

Không ghi đè field chứa một subscription đang hoạt động bằng một subscription mới nếu implementation có thể phục vụ nhiều đăng ký. Trong thiết kế đơn giản, dùng một Subscriber riêng cho từng Subscription. Nếu nhận subscription không mong muốn, cần chính sách từ chối/hủy phù hợp; không gộp trạng thái demand của hai luồng vào một biến.

## 4. onNext(T): xử lý item và bổ sung demand

`onNext(T item)` được gọi cho từng phần tử đã được cấp quyền bằng `request`. Với xử lý đồng bộ, mẫu dễ hiểu nhất là **xử lý xong rồi xin thêm một item**:

```java
@Override
public void onNext(String item) {
    System.out.println("Đã nhận: " + item);
    subscription.request(1);
}
```

Đoạn trên giả định field `subscription` đã được lưu ở `onSubscribe`; đây là một phương thức trong lớp Subscriber, chưa phải chương trình hoàn chỉnh.

Ví dụ diễn tiến:

| Sự kiện | Demand còn lại |
| --- | ---: |
| `onSubscribe` gọi `request(1)` | 1 |
| Nhận `onNext("A")` | 0 |
| Xử lý A xong, gọi `request(1)` | 1 |
| Nhận `onNext("B")` | 0 |
| Xử lý B xong, gọi `request(1)` | 1 |
| Nguồn hết dữ liệu, gửi `onComplete` | Kết thúc |

`request(1)` sau mỗi item hỗ trợ kiểm soát nhịp nhận nhưng có overhead nếu mỗi item rất nhỏ. Có thể xin theo lô `request(16)` hoặc `request(32)`, theo dõi số phần tử đã tiêu thụ và cấp bù khi đạt một ngưỡng thích hợp.

**Cẩn trọng với công việc bất đồng bộ:** nếu `onNext` chỉ đưa item vào queue hoặc khởi động một `CompletableFuture` rồi xin item mới ngay, demand thể hiện tốc độ **đưa việc vào queue**, không phải tốc độ hoàn thành việc. Queue có thể tăng vô hạn nếu downstream chậm. Cần điều tiết số tác vụ đang chạy, cấp bù khi có năng lực xử lý và đồng bộ trạng thái với luồng hoàn tất công việc.

Không nên block callback quá lâu bởi I/O hoặc chờ khóa lẫn nhau. Việc giữ `onNext` bận có thể chặn tiến độ gửi các item tiếp theo và tín hiệu terminal.

## 5. onError(Throwable): kết thúc do lỗi upstream

Publisher hoặc Subscription gọi `onError` khi không thể tiếp tục một subscription vì lỗi. `Throwable` mang nguyên nhân, sau đó subscription đã kết thúc và không được nhận thêm `onNext` hay `onComplete`.

```java
@Override
public void onError(Throwable throwable) {
    System.err.println("Nguồn dữ liệu thất bại: " + throwable);
    finished.countDown();
}
```

Ở đây `finished` giả định là `CountDownLatch` thuộc phạm vi lớp Subscriber. Việc báo latch giúp luồng điều phối biết subscription đã có terminal. Trong ứng dụng thực tế, có thể hoàn tất một future bằng exception, ghi log có ngữ cảnh và thu hồi tài nguyên.

Phân biệt:

- **Lỗi do Publisher/Subscription báo**: Subscriber nhận `onError`.
- **Exception do code của Subscriber ném trong `onNext`**: Javadoc không bảo đảm exception ấy được chuyển ngược về **chính** `onError` của Subscriber. Ví dụ `SubmissionPublisher` hủy subscription nếu callback ném lỗi; handler lỗi tùy chọn của nó có thể được gọi.
- **Lỗi nghiệp vụ có thể phục hồi của từng item**: có thể xử lý/biểu diễn bằng dữ liệu tùy thiết kế, không nhất thiết phải kết thúc cả luồng.

Không gọi `onComplete` từ `onError` và không tiếp tục `request` sau terminal. Tránh để chính `onError` ném thêm exception vì hành vi khi callback terminal ném lỗi không được xác định.

## 6. onComplete(): kết thúc bình thường

`onComplete()` báo rằng subscription kết thúc bình thường và không còn callback nào nữa. Phương thức không có tham số:

```java
@Override
public void onComplete() {
    System.out.println("Đã nhận hết dữ liệu");
    finished.countDown();
}
```

`onComplete` không đồng nghĩa "mọi công việc ứng dụng đã hoàn tất": nếu `onNext` trước đó đã giao việc cho executor khác, những công việc đó có thể vẫn chạy. Ứng dụng phải tự theo dõi các tác vụ phụ này.

Với `SubmissionPublisher`, `close()` phát tín hiệu hoàn tất theo cách bất đồng bộ. **`close()` trả về không có nghĩa `onComplete` đã chạy xong.** Vì vậy ở ví dụ dưới, `CountDownLatch` được dùng để chờ terminal thực sự.

## 7. Ví dụ Java 17+ độc lập: quan sát toàn bộ vòng đời

Ví dụ chỉ dùng JDK, gồm `SubmissionPublisher<String>`, `Flow.Subscriber<String>` và `CountDownLatch`. `onError` được cài đặt nhưng đường đi bình thường không kích hoạt nó.

Tạo file `FlowLifecycleDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

public class FlowLifecycleDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch finished = new CountDownLatch(1);

        try (SubmissionPublisher<String> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    System.out.println("onSubscribe");
                    subscription.request(1);
                }

                @Override
                public void onNext(String item) {
                    System.out.println("onNext: " + item);
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable throwable) {
                    System.out.println("onError: " + throwable);
                    finished.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("onComplete");
                    finished.countDown();
                }
            });

            publisher.submit("A");
            publisher.submit("B");
            publisher.close();
            finished.await();
        }
    }
}
```

Biên dịch, chạy từ thư mục chứa file:

```shell
javac FlowLifecycleDemo.java
java FlowLifecycleDemo
```

Output đúng của ví dụ (một Subscriber):

```text
onSubscribe
onNext: A
onNext: B
onComplete
```

Đã kiểm thử đoạn mã này bằng JDK 24 (mã sử dụng API chạy được từ Java 17). Những điểm cần nhận ra:

1. `subscribe` thiết lập subscription, callback `onSubscribe` nhận nó và xin 1 item.
2. Hai lệnh `submit` đưa "A" rồi "B" vào publisher. Việc chuyển tới Subscriber là bất đồng bộ; không được coi `submit` đã đợi `onNext` hoàn tất.
3. Demand ban đầu cho phép nhận A; xử lý A xong Subscriber xin thêm 1, do đó được nhận B.
4. `close` thông báo không còn phần tử được phát; `onComplete` xuất hiện sau các item trong cùng subscription.
5. `finished.await()` khiến thread `main` chờ đến khi callback terminal đã chạy.
6. `SubmissionPublisher` có thể dùng thread khác thread gọi `submit`; **trật tự callback của một subscription vẫn được giữ**.

### Thử nhánh onError

Trong chương trình trên, thay **chính ba dòng**:

```java
publisher.submit("A");
publisher.submit("B");
publisher.close();
```

bằng:

```java
publisher.closeExceptionally(new IllegalStateException("upstream failed"));
```

Output kỳ vọng của lần chạy thay thế:

```text
onSubscribe
onError: java.lang.IllegalStateException: upstream failed
```

Ở lần này Publisher chủ động đóng bằng lỗi, vì vậy không có `onNext` và không gọi `onComplete`. Đây là ví dụ lỗi upstream được báo bằng `onError`, không phải chứng minh rằng exception tự ném trong `onNext` sẽ tạo ra cùng hành vi.

## 8. Cấp demand theo lô, hủy và giới hạn backpressure

**Cấp theo từng phần tử:** sử dụng `request(1)` trong `onSubscribe` rồi cấp bù từng item. Dễ lý giải, phù hợp khi xử lý item chậm hoặc cần kiểm soát sát năng lực tiếp nhận.

**Cấp theo lô:** có thể khởi đầu với `request(32)`, rồi mỗi khi xử lý 16 phần tử sẽ cấp bù `request(16)`. Khi đó lượng demand chưa được phục vụ dao động quanh một khoảng thay vì gọi `request` cho từng item. Ví dụ ý tưởng sau là *pseudocode*, không phải chương trình Java đầy đủ:

```text
onSubscribe:
    subscription.request(32)
    remaining = 32

onNext(item):
    process(item)
    remaining--
    if remaining == 16:
        subscription.request(16)
        remaining += 16

onError / onComplete:
    cleanup
    stop requesting
```

Khi dùng batch, cần định nghĩa "xử lý xong" là xử lý đồng bộ hay việc bất đồng bộ đã thực sự hoàn tất, vì đó là thời điểm thích hợp để bổ sung năng lực nhận.

**Hủy giữa chừng:** gọi `subscription.cancel()` khi không cần dữ liệu nữa, ví dụ người dùng hủy tác vụ. `cancel` là best-effort: tín hiệu đang bay tới có thể còn được chuyển và subscription không bắt buộc nhận terminal. Vì vậy logic hủy cần một đường thu hồi tài nguyên riêng.

**Demand không phải kích thước bộ đệm.** `request(n)` chỉ cấp quyền chuyển tối đa n item qua `onNext`; nó không ép mọi Publisher giới hạn nội bộ ở n item. `SubmissionPublisher` dùng bộ đệm riêng cho từng subscriber. Phương thức `submit` có thể chờ tài nguyên khi buffer đầy, còn `offer` có các chính sách từ chối/drop. Thiết kế hệ thống thực tế phải phối hợp demand, buffer, executor, tốc độ upstream và chính sách xử lý lỗi.

## 9. Các lỗi hay gặp

| Lỗi hoặc triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Đăng ký rồi không nhận được item | Chưa gọi `request` | Xin demand dương trong `onSubscribe` |
| Nhận một item rồi "đứng" | Chỉ `request(1)` một lần | Xin tiếp sau khi đủ khả năng xử lý thêm |
| Nhận `IllegalArgumentException` qua `onError` | Gọi `request(0)` hoặc số âm | Chỉ dùng n dương; không dùng request(0) để pause |
| Queue tăng không kiểm soát | Xin quá nhiều hoặc cấp bù trước khi việc async xong | Giới hạn số việc đang xử lý và cấp bù theo năng lực thực |
| Chương trình thoát trước khi thấy callback cuối | Publisher phát tín hiệu bất đồng bộ | Chờ tín hiệu terminal bằng latch/future khi phù hợp |
| Chờ `onComplete` sau `cancel` | Hủy không bảo đảm terminal | Hoàn tất nhánh hủy bằng cơ chế riêng |
| Nghĩ callback luôn chạy trên một thread | Publisher dùng executor | Chỉ dựa vào thứ tự theo subscription; đồng bộ trạng thái chia sẻ với thread khác |
| Ném exception từ `onNext` và chờ `onError` | Không có bảo đảm chuyển tiếp lỗi theo cách đó | Quản lý lỗi downstream và cancellation rõ ràng |
| Vẫn gọi request sau `onComplete` | Không theo dõi terminal | Đánh dấu kết thúc, giải phóng tài nguyên |

## 10. Câu hỏi kiểm tra hiểu biết

**Q1. Vì sao `onSubscribe` thường gọi `request` ngay?** Vì khi chưa có demand, Publisher tuân thủ Flow sẽ không gọi `onNext`; đây là nơi Subscriber lấy quyền điều khiển nhịp nhận.

**Q2. `request(5)` có nghĩa chắc chắn nhận được năm phần tử không?** Không; chỉ cho phép **tối đa năm** `onNext` thêm. Publisher có thể không còn dữ liệu hoặc kết thúc trước đó.

**Q3. Có thể gọi `onComplete` sau `onError` không?** Không, nếu tuân thủ hợp đồng. Một subscription có tối đa một terminal.

**Q4. `onComplete` có đợi mọi `CompletableFuture` được tạo trong `onNext` không?** Không; ứng dụng cần quản lý những công việc bất đồng bộ riêng của mình.

**Q5. Stream chạy vô hạn có bắt buộc gọi `onComplete` không?** Không. Nếu còn hoạt động, có thể không có terminal; ứng dụng có thể dùng `cancel`, timeout hoặc cơ chế dừng khác.

**Q6. API này khác Java `Stream` như thế nào?** `Flow` mô tả giao tiếp bất đồng bộ và demand giữa nguồn và người nhận; `java.util.stream.Stream` chủ yếu cung cấp thao tác xử lý tập dữ liệu qua pipeline và không sử dụng bốn callback này.

## 11. Ghi nhớ và tài liệu tham khảo

- `onSubscribe`: nhận Subscription và yêu cầu demand dương.
- `onNext`: xử lý một item; chỉ cấp bù khi có năng lực xử lý thêm.
- `onError`: terminal vì lỗi; sau đó không có callback khác.
- `onComplete`: terminal thành công; sau đó không có callback khác.
- `request` điều tiết lượng dữ liệu có thể chuyển, `cancel` yêu cầu ngừng theo best-effort.
- Thứ tự callback bảo đảm theo **từng subscription**, không mặc định cùng thread; đừng nhầm demand với buffer hoặc việc hoàn thành callback với hoàn thành công việc async.

Đọc thêm Javadoc chính thức của Java SE:

- [`Flow`: mô hình và ví dụ backpressure](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html)
- [`Flow.Subscriber`: hợp đồng bốn callback](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html)
- [`Flow.Subscription`: `request` và `cancel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html)
- [`SubmissionPublisher`: phát bất đồng bộ và tín hiệu kết thúc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-005">SubmissionPublisher và executor sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# SubmissionPublisher và executor sử dụng trong Java Flow

`SubmissionPublisher<T>` là một triển khai có sẵn của `Flow.Publisher<T>` trong `java.util.concurrent` từ Java 9. Nó nhận phần tử từ producer và **phát bất đồng bộ** các tín hiệu đến subscriber bằng một `Executor`. Đây là điểm then chốt: **luồng gọi `submit()` không nhất thiết là luồng chạy `Subscriber.onNext()`**. Executor quyết định nơi thực thi việc giao tín hiệu; yêu cầu `Subscription.request(n)` và buffer theo từng subscriber quyết định tốc độ có thể tiếp nhận.

Bài này tập trung vào **Java concurrency nói chung**, không phụ thuộc Spring. Ta sẽ học cách chọn executor, phân biệt buffer với demand, nhận biết lúc `submit()` bị chặn, dùng `offer()` có chính sách drop, quản lý lỗi và đóng tài nguyên. Ví dụ chạy được trên JDK 21+ nhưng cơ chế `Flow` và `SubmissionPublisher` đã có từ Java 9.

## 1. Từ Flow đến SubmissionPublisher

Bốn giao diện chính của `java.util.concurrent.Flow`:

| Vai trò | Giao diện | Trách nhiệm |
|---|---|---|
| Nguồn dữ liệu | `Flow.Publisher<T>` | Đăng ký subscriber; phát `onSubscribe`, `onNext`, `onError`, `onComplete` theo protocol |
| Bên nhận | `Flow.Subscriber<T>` | Nhận subscription, xử lý phần tử, nhận tín hiệu kết thúc/lỗi |
| Kênh điều phối | `Flow.Subscription` | `request(n)` yêu cầu thêm `n` item, `cancel()` ngừng subscription |
| Vừa nhận vừa phát | `Flow.Processor<T,R>` | Vừa là subscriber vừa là publisher, dùng khi xây pipeline xử lý |

`SubmissionPublisher<T>` thực thi `Flow.Publisher<T>` và `AutoCloseable`. Nó cung cấp sẵn logic đăng ký, buffer, dispatch bất đồng bộ và xử lý thiếu dung lượng. Khi gọi `submit(item)`, producer giao item cho publisher; executor được dùng để chạy việc **gửi tín hiệu** đến subscribers (ví dụ callback `onNext`), không phải tự động biến công đoạn tạo item của producer thành một task trên executor.

~~~text
                 PRODUCER THREAD
                      |
                submit(item)
                      |
             SubmissionPublisher
                      |
       +--------------+---------------+
       |                              |
    buffer S1                      buffer S2
       |                              |
request(2) của S1              request(1) của S2
       |                              |
       +----------- executor ---------+
                    |
         dispatch onNext(item)
             (async callbacks)
~~~

Mỗi subscriber có **buffer riêng**, được mở rộng khi cần đến mức capacity được cấu hình. Một subscriber có thể chậm hoặc thiếu demand trong khi subscriber khác xử lý nhanh. Nếu không có drop hoặc exception, mỗi subscriber hiện tại nhận các item mới theo đúng thứ tự đã được gửi cho subscription đó; không có cam kết tổng thứ tự thời gian giữa các subscriber.

Những nguyên tắc khác:

- `onSubscribe(subscription)` là tín hiệu thiết lập subscription. Subscriber gọi `subscription.request(n)` với **`n > 0`** để nhận đến `n` phần tử; `request(0)` hoặc âm là yêu cầu không hợp lệ, dẫn đến `onError`.
- `onNext` chỉ được phát khi có demand đã yêu cầu; các item chưa phát được giữ trong buffer nếu còn chỗ.
- `onComplete` là kết thúc bình thường; `onError` là kết thúc thất bại; một subscription không được nhận cả hai tín hiệu terminal.
- `cancel()` của một subscriber không phải `close()` toàn bộ publisher và không tự động hủy subscription của người khác.
- `submit(null)` không hợp lệ và gây `NullPointerException`.

## 2. Executor mặc định và executor tự cấu hình

Các constructor quan trọng:

~~~java
// Executor mặc định + dung lượng mặc định.
SubmissionPublisher<String> a = new SubmissionPublisher<>();

// Tự cấp executor và mức buffer tối đa cho mỗi subscriber.
SubmissionPublisher<String> b =
        new SubmissionPublisher<>(executor, maxBufferCapacity);

// Có thêm callback xử lý lỗi do onNext của subscriber ném ra.
SubmissionPublisher<String> c =
        new SubmissionPublisher<>(executor, maxBufferCapacity, onNextErrorHandler);
~~~

Đây là **đoạn minh họa API**, không phải chương trình độc lập; `executor`, `maxBufferCapacity` và `onNextErrorHandler` là các biến phải khai báo trước.

**Mặc định**, `new SubmissionPublisher<>()` dùng `ForkJoinPool.commonPool()` nếu pool hỗ trợ parallelism ít nhất 2; nếu không, implementation tạo một thread mới cho mỗi task giao tín hiệu. Dung lượng buffer mặc định là `Flow.defaultBufferSize()`. Vì vậy không nên khẳng định rằng mọi máy, mọi cấu hình đều chắc chắn chạy callback trên `commonPool`.

**Tự cấu hình** bằng `new SubmissionPublisher<>(executor, capacity)` có các lợi ích:

- Kiểm soát thread dùng cho subscriber callbacks, tên thread, số platform workers, chính sách shutdown và tài nguyên.
- Cách ly workload giao dữ liệu khỏi các công việc không liên quan trên `commonPool`.
- Lựa chọn `newFixedThreadPool(n)` khi có số lượng subscribers tương đối dự đoán được và công việc giao tín hiệu phù hợp.
- Có thể sử dụng `Executors.newVirtualThreadPerTaskExecutor()` (Java 21+) khi callbacks cần chờ I/O, nhưng vẫn phải giới hạn tải, tài nguyên downstream và số task outstanding. Virtual threads không tự giải quyết buffer saturation hay slow subscriber.

`executor` ở đây nhận các **tác vụ dispatch nội bộ của publisher**; không nên hiểu “mỗi item luôn tạo một thread mới”. Một delivery task có thể xử lý nhiều item, chi tiết lập lịch phụ thuộc implementation. `getExecutor()` trả executor đang dùng; `getMaxBufferCapacity()` trả mức capacity thực tế. Capacity là **theo từng subscriber**, và implementation có thể làm tròn capacity yêu cầu lên lũy thừa 2 hoặc chặn ở giới hạn hỗ trợ: hãy kiểm tra giá trị trả về thay vì giả định luôn đúng literal đưa vào constructor.

## 3. Ví dụ đầy đủ: FixedThreadPool giao tín hiệu

Tạo file **`SubmissionExecutorDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class SubmissionExecutorDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);

        try (ExecutorService delivery = Executors.newFixedThreadPool(2);
             SubmissionPublisher<String> publisher =
                     new SubmissionPublisher<>(delivery, 8)) {

            publisher.subscribe(new Flow.Subscriber<String>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    subscription = s;
                    subscription.request(1);
                }

                @Override
                public void onNext(String item) {
                    System.out.println("received=" + item
                            + " virtual=" + Thread.currentThread().isVirtual());
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable error) {
                    System.out.println("error=" + error.getClass().getSimpleName());
                    completed.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("complete");
                    completed.countDown();
                }
            });

            publisher.submit("A");
            publisher.submit("B");
            publisher.close();

            if (!completed.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Subscriber did not complete");
            }
        }
    }
}
~~~

Chạy với JDK 21+:

~~~bash
javac SubmissionExecutorDemo.java
java SubmissionExecutorDemo
~~~

Stdout thành công:

~~~text
received=A virtual=false
received=B virtual=false
complete
~~~

Giải thích:

1. `newFixedThreadPool(2)` tạo tối đa hai **platform worker threads**; `onNext` chạy trên worker của executor nên `Thread.currentThread().isVirtual()` là `false`.
2. `onSubscribe` lưu `Subscription` và yêu cầu một item. Sau mỗi `onNext`, subscriber yêu cầu thêm một item. Đây là mô hình **request từng phần tử**.
3. `submit("A")` và `submit("B")` đưa item vào publisher; callback được gọi **bất đồng bộ**. Publisher giữ thứ tự hai item cho cùng subscription.
4. `close()` phát yêu cầu kết thúc bình thường và không nhận thêm item mới; nó **không đồng nghĩa subscriber đã xử lý xong callback terminal trước khi `close()` trả về**. Ta dùng `CountDownLatch` để đợi `onComplete`/`onError`.
5. `try-with-resources` đóng publisher và executor theo thứ tự ngược lúc kết thúc. Sau khi đóng publisher, executor vẫn cần được đóng riêng vì **SubmissionPublisher không sở hữu quyền tự shutdown executor mà caller đã cấp**.
6. Trong đường chạy bình thường, `onError` không được gọi. Nếu callback xảy ra lỗi, đường đi khác có thể in thông báo lỗi hoặc phải được xử lý riêng.

Ví dụ dùng hai worker để thấy rõ việc lựa chọn executor; **không có cam kết mỗi subscriber luôn được gán cố định một thread**, cũng không cam kết hai item được xử lý trên hai thread khác nhau.

## 4. Backpressure là demand, buffer không phải giấy phép gửi vô hạn

Trong `Flow`, subscriber điều chỉnh số phần tử được phép phát bằng `subscription.request(n)`. Nếu chỉ request 1 mà chưa xử lý xong, publisher có thể lưu những item tiếp theo trong buffer. Khi buffer đạt giới hạn, producer phải đối mặt với chính sách xử lý của `submit` hoặc `offer`.

~~~text
subscriber: request(1)
producer:   A, B, C, D, E, ...
               |
publisher -> onNext(A)  (đủ demand)
          -> buffer(B,C,... ) khi còn capacity
          -> capacity đầy: submit đợi hoặc offer drop/timeout
subscriber: onNext(A) xong, request(1)
publisher -> onNext(B) ...
~~~

Điểm cần phân biệt:

- **Demand** (`request(n)`): subscriber cấp quyền phát tối đa `n` item chưa được đáp ứng.
- **Buffer capacity**: số item chưa được gửi tới subscriber có thể tạm lưu trong publisher; đây không phải số item subscriber đang được phép nhận.
- **Executor parallelism**: nguồn lực chạy callbacks. Nhiều worker hơn có thể giúp nhiều subscribers tiến triển, nhưng không thay thế demand, không tăng buffer capacity và không bắt subscriber xử lý nhanh hơn.
- **Producer rate**: tốc độ tạo và gửi item. Nếu nhanh hơn lâu dài so với tốc độ xử lý, buffer sẽ đầy, cho dù bắt đầu rất lớn.

Đối với một subscriber, các tín hiệu `onNext` được gửi theo thứ tự và không chạy đồng thời trên cùng subscription; một executor nhiều thread không biến việc gọi `onNext` trên **cùng subscriber** thành xử lý song song tự do. Các subscriber độc lập có thể được dispatch song song nếu executor có khả năng.

Ví dụ: hai subscribers nhận cùng 100 item, subscriber nhanh request tiếp liên tục nhưng subscriber chậm dừng request. Buffer chậm có thể đầy và khiến `submit` từ producer bị giữ lại, mặc dù subscriber nhanh còn demand. Vì thế phải đặt chính sách cho **slowest subscriber** khi hệ thống không chấp nhận producer chờ vô hạn.

## 5. `submit()` và `offer()` có hợp đồng khác nhau

| Phương thức | Khi chưa có chỗ trong buffer của subscriber | Kết quả trả về |
|---|---|---|
| `submit(item)` | Có thể **chờ không thể ngắt** đến khi có tài nguyên | Ước lượng **maximum lag** của các subscriber; ≥1 nếu có subscriber, 0 nếu không |
| `offer(item, onDrop)` | Không chờ dung lượng; có thể drop ở subscriber quá chậm, gọi handler | Số âm: ước lượng số lần drop; số không âm: ước lượng maximum lag |
| `offer(item, timeout, unit, onDrop)` | Có thể đợi đến timeout hoặc bị interrupt; có cơ hội gọi handler và retry một lần nếu trả `true` | Cùng quy ước số âm / không âm |

`submit()` **không chờ subscriber xử lý xong từng item** nếu buffer còn chỗ. Nhưng khi thiếu dung lượng, phương thức này có thể block. Hợp đồng Java 21 mô tả việc chờ tài nguyên của `submit` là *uninterruptible*. Vì vậy nếu cần timeout trong luồng phát item, cân nhắc overload `offer` có thời hạn, giới hạn upstream hoặc kiến trúc tránh vòng chờ vô hạn.

`offer()` có thể drop item **đối với một hoặc nhiều subscriber**; không đồng nghĩa một item bị drop ở mọi nơi. Callback `onDrop` nhận subscriber bị nghẽn và item; trả `true` chỉ yêu cầu publisher **thử lại một lần** tại subscriber đó, không hứa sẽ đưa item thành công. Trả `false` chấp nhận drop. Đừng thực hiện logic phức tạp, gọi đệ quy vào publisher hay chặn lâu trong drop handler; các lời gọi khác vào publisher có thể bị giữ trong khi handler chạy.

Đừng dùng kết quả `submit()` (lag) làm số lượng đã xử lý chính xác. `estimateMaximumLag()`, `estimateMinimumDemand()` là **số ước lượng dành cho monitoring**, có thể chậm so với thực tế và không nên làm điều kiện đồng bộ/chứng minh đã giao đủ item.

## 6. Ví dụ đầy đủ: buffer 1, offer drop, executor virtual thread

Ví dụ này chủ động **chưa request** khi subscriber mới được đăng ký. Buffer chỉ có dung lượng 1: item đầu được giữ lại, item thứ hai không còn chỗ nên bị drop. Sau đó subscriber request 1 để xử lý item đầu. Executor chạy callback trên **virtual thread** (JDK 21+).

Tạo file **`SubmissionOfferDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SubmissionOfferDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

        try (ExecutorService delivery =
                     Executors.newVirtualThreadPerTaskExecutor();
             SubmissionPublisher<Integer> publisher =
                     new SubmissionPublisher<>(delivery, 1)) {

            publisher.subscribe(new Flow.Subscriber<Integer>() {
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    subscription.set(s);
                    subscribed.countDown();
                    // Chủ động chưa request, để minh họa buffer đầy.
                }

                @Override
                public void onNext(Integer item) {
                    System.out.println("received=" + item
                            + " virtual=" + Thread.currentThread().isVirtual());
                }

                @Override
                public void onError(Throwable error) {
                    System.out.println("error=" + error.getClass().getSimpleName());
                    completed.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("complete");
                    completed.countDown();
                }
            });

            if (!subscribed.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Missing onSubscribe");
            }

            int first = publisher.offer(10, (subscriber, item) -> false);
            int second = publisher.offer(20, (subscriber, item) -> false);
            System.out.println("first-accepted=" + (first >= 0));
            System.out.println("second-dropped=" + (second < 0));

            subscription.get().request(1);  // Giờ cho phép nhận item 10.
            publisher.close();

            if (!completed.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Missing terminal signal");
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac SubmissionOfferDemo.java
java SubmissionOfferDemo
~~~

Stdout:

~~~text
first-accepted=true
second-dropped=true
received=10 virtual=true
complete
~~~

Giải thích cơ chế thực tế:

1. `CountDownLatch subscribed` đảm bảo callback `onSubscribe` đã thực thi **trước** hai lời gọi `offer`, tránh phụ thuộc may rủi của lịch thread.
2. Subscriber chưa gọi `request` nên item 10 được xếp vào buffer dung lượng 1. Item 20 hết chỗ và `onDrop` trả `false`, vì thế giá trị `second` âm.
3. `request(1)` sau hai lời `println` mở demand để phát item 10. Do đó stdout có thứ tự nêu trên.
4. `onNext` chạy trên virtual thread của executor đã cấp và `isVirtual()` là `true`. **Điều này không biến mỗi item thành một virtual thread riêng được bảo đảm**; implementation có thể dispatch một nhóm tín hiệu trong cùng task.
5. `publisher.close()` sau request yêu cầu tín hiệu kết thúc; latch giữ chương trình đến lúc subscriber nhận `onComplete`. Item 20 đã drop nên không được phát.

Ở code thực tế, subscriber nên request theo tốc độ có thể xử lý, **không cố tình ngưng request mãi**; ở đây ta cố ý giữ demand bằng 0 để giải thích sự khác nhau giữa demand và buffer.

## 7. Chọn executor: tài nguyên, cách ly và nguy cơ bế tắc

Lựa chọn executor có tác động trực tiếp đến khả năng giải phóng buffer.

### 7.1. `ForkJoinPool.commonPool()` mặc định

Dùng khi workload nhỏ, ngắn, callback không chặn lâu và không cần kiểm soát lifecycle executor riêng. Nhưng `commonPool` là tài nguyên chia sẻ của JVM: các công việc khác có thể chiếm worker và làm delivery chậm. Khi callback gọi API blocking nặng hoặc CPU-bound lâu, cần đo ảnh hưởng đến các tác vụ khác cùng pool.

### 7.2. `Executors.newFixedThreadPool(n)`

Thích hợp để cách ly delivery và đặt một giới hạn hữu hạn về **số worker đồng thời xử lý callbacks**. Khi có vài subscriber chủ yếu xử lý CPU hoặc công việc ngắn, fixed pool thường dễ dự đoán. Khi mỗi `onNext` lại đợi I/O lâu, ít worker có thể tạo nút cổ chai và làm buffer đầy. Khả năng xử lý còn phụ thuộc queue của executor, số subscriber và độ dài callback.

### 7.3. `Executors.newVirtualThreadPerTaskExecutor()` (Java 21+)

Phù hợp nếu callback thực sự phải dùng API blocking I/O tương thích, muốn giữ cách viết tuần tự và có thể có rất nhiều delivery tasks chờ. Tuy vậy, executor này không tự hạn chế số virtual thread được tạo và không thay thế giới hạn DB connection, HTTP rate limit hay timeout. Việc tăng số virtual thread không làm subscriber thiếu demand tự nhận thêm item.

### 7.4. Tránh tự gây starvation/deadlock

Một lỗi điển hình: producer chạy **trên cùng một single-thread executor** dùng làm delivery executor. Producer gọi `submit()` liên tục, buffer đầy và phải chờ; nhưng worker duy nhất đang bị chiếm bởi producer, nên callback `onNext` không có worker để tiến triển, không thể gửi `request` tiếp. Kết quả có thể là deadlock.

~~~text
single-thread executor:
  Worker #1 đang chạy producer:
    submit(A) -> submit(B) -> ... -> buffer đầy -> chờ
  Delivery task onNext(...) đang đợi chạy trên chính Worker #1
  => Không có ai rút buffer hoặc request thêm demand
~~~

Hạn chế nguy cơ bằng cách tách producer khỏi delivery executor, đảm bảo đủ khả năng chạy độc lập cho callback, không chặn worker duy nhất bằng `submit`, dùng `offer` có timeout/drop khi phù hợp và có chính sách backpressure ở upstream. Không có một cấu hình thread count phù hợp cho tất cả; cần kiểm thử bằng số subscriber và tốc độ thực tế.

**Lưu ý:** `newSingleThreadExecutor()` không mặc nhiên sai. Nó phù hợp với quy trình chỉ cần một luồng giao tín hiệu và không đẩy nó vào vòng chờ phụ thuộc chính nó. Vấn đề là dependency cycle giữa producer, callbacks và request.

## 8. Vòng đời và lỗi trong Subscriber

- `publisher.close()`: ngừng chấp nhận phần tử mới, phát tín hiệu `onComplete` khi các item hợp lệ đã được xử lý theo protocol; việc thông báo bất đồng bộ có thể chưa xong khi `close()` trả về.
- `publisher.closeExceptionally(cause)`: chấm dứt bất thường và gửi `onError(cause)`; subscriber phải dừng và xử lý lỗi. `getClosedException()` hỗ trợ truy vấn nguyên nhân đóng bất thường.
- `subscription.cancel()`: hủy một subscription; không đóng publisher hay executor và không phải cơ chế báo thành công.
- `onNext` ném exception: subscription tương ứng bị hủy. Constructor overload có `BiConsumer<Subscriber,Throwable>` xử lý lỗi trong `onNext` **trước khi hủy**, không phải exception handler chung cho mọi callback.
- Nếu `onSubscribe`, `onError` hoặc `onComplete` tự ném exception, handler riêng cho `onNext` không nhận các lỗi đó.
- Executor có thể ném `RejectedExecutionException` (ví dụ bị shutdown quá sớm), khiến `submit`/`offer` ném exception và không phải mọi subscriber đều nhận item; hệ thống nên có chính sách đóng bất thường và thông báo/cảnh báo thích hợp.

Snippet cấu hình handler lỗi `onNext`:

~~~java
// Snippet: delivery là Executor đã được tạo và sẽ đóng riêng.
SubmissionPublisher<String> publisher = new SubmissionPublisher<>(
        delivery,
        32,
        (subscriber, error) -> {
            System.err.println("onNext failed: " + error.getMessage());
        }
);
~~~

Đây là snippet, không có `main`. Không dùng handler để giả định có thể tiếp tục subscription đã bị hủy. Lỗi nghiệp vụ nên được phản ánh qua cơ chế phù hợp: dữ liệu lỗi, retry upstream với giới hạn, hoặc `closeExceptionally` toàn stream.

Nếu cấp `ExecutorService` riêng cho publisher, **cần đóng publisher trước và sau đó đóng executor**. Việc gọi `close()` trên publisher không đồng nghĩa gọi `executor.shutdown()`. Với `commonPool` mặc định, **không tự shutdown shared common pool** chỉ vì publisher của mình hoàn tất. Khi cần đợi công việc hoàn tất, dùng `CountDownLatch`/`CompletableFuture` có timeout, không dựa vào `Thread.sleep` rồi phỏng đoán.

## 9. Đo lường, capacity và thiết kế dữ liệu

Capacity `getMaxBufferCapacity()` tính theo **mỗi subscriber**, không phải hàng đợi chung cho toàn publisher. Có N subscribers, mỗi subscriber có buffer tối đa C phần tử; số item giữ trong các buffer có thể tăng theo N × C, chưa kể bản thân object, queue/task của executor và các reference khác. Nếu item lớn hoặc subscribers chậm, tổng heap có thể đáng kể.

Giả sử có 40 subscribers, mỗi buffer có thể giữ 128 **tham chiếu** item thì có thể có đến 5.120 slot tham chiếu trong các buffer (chưa kể object thực tế và overhead). Đây là ví dụ định cỡ, **không phải đo bộ nhớ cụ thể** hay khẳng định toàn bộ item được sao chép thành N object riêng; nhiều buffer có thể giữ reference đến cùng item.

Quan sát các chỉ số:

- Số subscribers, tốc độ publish, tốc độ `onNext` hoàn thành và tỷ lệ xử lý lỗi.
- `estimateMaximumLag()`, `estimateMinimumDemand()`: chỉ số gần đúng để theo dõi tình trạng demand và lag, **không dùng để chứng minh synchronization**.
- Tỷ lệ `offer` trả giá trị âm, số subscriber bị drop và retry thất bại.
- Executor pool queue/active workers, CPU và thời gian callback (nếu dùng fixed pool).
- Với virtual thread executor: số task đang sống, thời gian chờ I/O, memory, thread-local allocation và giới hạn kết nối.
- Timeout/cancellation ở network và downstream, p95/p99 end-to-end latency, số lần producer bị chặn khi `submit`.

Từ đây lựa chọn capacity dựa trên peak burst thực tế, kích thước item, độ trễ callback và mức tiêu thụ heap chấp nhận được. Đặt capacity lớn hơn có thể hấp thụ burst nhưng **không sửa được mismatch tốc độ kéo dài** giữa producer và subscriber.

## 10. Những sai lầm thường gặp

| Sai lầm | Hậu quả | Cách sửa |
|---|---|---|
| Tưởng `submit` đồng bộ gọi `onNext` ngay | Race và thứ tự output khó dự đoán | Hiểu dispatch bất đồng bộ, đợi terminal đúng cách |
| Tưởng executor chỉ dùng để chạy producer | Cấp thread sai chỗ | Executor dùng cho các tác vụ delivery/callback |
| Gọi `request(0)` vì chưa muốn nhận item | Protocol error | Đừng request khi chưa muốn; khi cần hãy request số dương |
| Gọi `request(Long.MAX_VALUE)` cho subscriber xử lý rất chậm | Có thể tạo áp lực buffer/tài nguyên | Request theo khả năng xử lý, theo dõi lag |
| Dùng `submit` khi không thể chấp nhận blocking | Producer có thể chờ không giới hạn | `offer` có timeout/drop và backpressure trước publisher |
| Coi `offer` âm là lỗi cho mọi subscriber | Đọc sai drop semantics | Drop có thể chỉ xảy ra ở một số subscriptions |
| Tin executor nhiều thread giúp một subscriber `onNext` chạy song song | Hiểu sai contract | Song song ở các subscriptions, không vi phạm thứ tự tín hiệu một subscription |
| Producer và delivery dùng một single-thread executor gây vòng chờ | Starvation/deadlock | Tách executors hoặc thiết kế tránh phụ thuộc vòng |
| Shutdown executor trước khi các callbacks/terminal signal hoàn tất | `RejectedExecutionException`, thiếu signal | Đóng publisher, đợi terminal, sau đó đóng executor |
| Xem `close()` là `onComplete()` đã chạy xong | Program thoát sớm, test flaky | Dùng latch/future chờ callback có timeout |
| Xem lag estimate là số tuyệt đối cho synchronization | Quyết định sai do số liệu có thể stale | Chỉ dùng cho monitoring |

## 11. Câu hỏi tự kiểm tra

**Câu 1:** `new SubmissionPublisher<>()` mặc định có tạo một pool mới riêng không? **Không theo hợp đồng API.** Nó sử dụng common pool nếu parallelism đủ 2, nếu không có cơ chế tạo thread thay thế.

**Câu 2:** Producer gọi `submit(A)` rồi `submit(B)`. Có chắc `onNext(A)` đã chạy xong trước khi `submit(B)` trả về không? **Không.** Publication và delivery là bất đồng bộ; item có thể nằm trong buffer.

**Câu 3:** Hai subscribers, một nhanh và một chậm. Vì sao producer có thể bị `submit` giữ lại? **Vì buffer của subscriber chậm có thể đầy**, dù subscriber nhanh còn demand.

**Câu 4:** `offer(item, (s,i)->false)` trả về `-1` thì có ý nghĩa gì? **Có một drop được báo trong lời gọi này**, không có nghĩa publisher đã đóng hoặc mọi subscriber đều nhận lỗi.

**Câu 5:** Dùng `newVirtualThreadPerTaskExecutor()` có loại bỏ nhu cầu `request(n)` không? **Không.** Executor chỉ quyết định nơi callback chạy, không sửa protocol demand.

**Câu 6:** Dừng `SubmissionPublisher` có tự đóng `ExecutorService` mà caller đã truyền vào không? **Không.** Caller phải quản lý vòng đời executor riêng.

### Bài tập

1. Đổi `SubmissionExecutorDemo` từ fixed pool 2 thành fixed pool 1, quan sát tên thread/cờ `isVirtual()`. Giải thích vì sao thứ tự item trong một subscriber vẫn giữ.
2. Trong `SubmissionOfferDemo`, gọi `request(2)` trước khi offer. Lý giải vì sao hai item có thể được chấp nhận (trong điều kiện subscriber chưa hủy và executor hoạt động); không dựa vào sleep để đồng bộ.
3. Tạo hai subscribers, một request từng item và một cố tình giữ demand. Thử `submit` với buffer nhỏ, sau đó thử `offer` có timeout để quan sát tình huống producer có thể phải chờ và các drop.
4. Thêm `onNext` cố ý ném `IllegalStateException` và cấu hình constructor handler. Quan sát cancellation và cách đóng publisher, nhưng không dùng sự cố này làm tín hiệu thành công trong code sản xuất.

## 12. Ghi nhớ

- `SubmissionPublisher<T>` là publisher bất đồng bộ có sẵn của `Flow`; **executor dùng để giao tín hiệu đến subscriber**, còn producer tự quyết định khi nào tạo/gửi item.
- Mặc định dùng common pool khi đủ parallelism; có thể cấp `Executor` riêng và buffer capacity theo từng subscriber bằng constructor.
- **Backpressure** dựa trên `Subscription.request(n)`; buffer chỉ hấp thụ chênh lệch tốc độ tạm thời.
- `submit()` có thể block khi đầy; `offer()` có thể drop hoặc timeout với handler và chính sách retry một lần.
- Số thread/virtual thread của executor không thay thế hạn mức downstream, backpressure, timeout hay xử lý subscriber chậm.
- `close()` và terminal callback là các sự kiện khác nhau; quản lý executor và chờ completion rõ ràng để tránh dừng sớm hay reject task.

### Tham khảo chính thức

- Java SE 21 `SubmissionPublisher` API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html
- Java SE 21 `Flow` API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html
- Java SE 21 `Flow.Subscription` API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html
- Java SE 21 `Executor` API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executor.html
- Java SE 21 `Executors` API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-006">Backpressure khác buffering thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

# Backpressure khác buffering thế nào? — Java Concurrency

## 1. Câu trả lời cốt lõi

**Buffering (đệm dữ liệu)** là giữ tạm những phần tử chưa được xử lý trong bộ nhớ hoặc kho lưu trữ trung gian. Nó giúp bên tạo dữ liệu (producer) và bên xử lý dữ liệu (consumer) hoạt động lệch nhịp trong thời gian ngắn.

**Backpressure (điều tiết ngược)** là cơ chế để phía chậm hơn tác động trở lại phía gửi: cho biết mình còn có thể nhận bao nhiêu, hoặc buộc phía gửi giảm tốc/chờ khi không còn chỗ tiếp nhận. Mục tiêu là kiểm soát **lượng công việc đang tồn đọng**, đặc biệt khi tốc độ tạo dữ liệu vượt tốc độ xử lý.

Hai khái niệm không loại trừ nhau. Một pipeline Java có thể **vừa có buffer giới hạn vừa có backpressure**: buffer hấp thụ dao động ngắn; tín hiệu nhu cầu hoặc thao tác chặn producer ngăn lượng tồn đọng tăng vô hạn.

Ví dụ: bộ đọc sự kiện tạo 1.000 phần tử/giây, bộ xử lý chỉ hoàn thành 100 phần tử/giây.

- Nếu chỉ đưa sự kiện vào hàng đợi không giới hạn, backlog tăng xấp xỉ 900 phần tử/giây trong thời gian chênh lệch kéo dài. Thêm RAM chỉ trì hoãn sự cố.
- Nếu có buffer tối đa 1.800 phần tử, bắt đầu từ rỗng, không có chính sách điều tiết, bỏ dữ liệu hay thay đổi tốc độ, buffer sẽ đầy sau khoảng 2 giây. Buffer hữu hạn buộc hệ thống phải quyết định làm gì khi đầy.
- Nếu có backpressure và producer **có thể được làm chậm**, tốc độ tạo/đưa vào hệ thống phải giảm tương ứng; buffer chỉ cần đủ cho các đợt biến động ngắn. Nếu nguồn bên ngoài không thể dừng, vẫn phải có chính sách tràn: chặn tại ranh giới thích hợp, bỏ mẫu, lưu ra đĩa, từ chối hoặc báo lỗi.

Công thức trực giác khi tốc độ gần như không đổi:

~~~text
Tốc độ tăng backlog ≈ max(0, tốc độ đến − tốc độ xử lý)
Thời gian tới khi đầy ≈ sức chứa còn trống / tốc độ tăng backlog
~~~

Đây là mô hình ước lượng, không phải bảo đảm thời gian thực: tốc độ xử lý, phân phối sự kiện, bộ nhớ cho mỗi phần tử và việc chặn producer đều có thể thay đổi.

## 2. So sánh theo đúng câu hỏi

| Khía cạnh | Buffering | Backpressure |
|---|---|---|
| Câu hỏi giải quyết | “Dữ liệu chưa xử lý nằm ở đâu?” | “Bên nhận còn cho phép gửi bao nhiêu?” |
| Cơ chế | Queue, ring buffer, batch, bộ đệm nội bộ | Demand/credit, giới hạn in-flight, producer chờ khi đầy, điều tiết tốc độ |
| Vai trò chính | Hấp thụ độ lệch tốc độ tạm thời | Ngăn tốc độ/khối lượng công việc vượt quá khả năng tiếp nhận bền vững |
| Khi consumer chậm | Queue tiếp tục tăng đến khi chạm giới hạn | Producer được yêu cầu giảm gửi hoặc bị chặn tại một ranh giới |
| Rủi ro riêng | Đệm không giới hạn gây áp lực heap, GC và độ trễ | Tắc nghẽn có thể lan ngược; producer cũng có thể phải chờ |
| Khi đầy | Cần quy tắc block/drop/reject/spill/error | Phải có đường truyền tín hiệu hoặc hành động cưỡng chế giới hạn |
| Có đảm bảo không mất dữ liệu? | Không; phụ thuộc sức chứa và chính sách tràn | Không; phụ thuộc khả năng hợp tác của nguồn, chính sách tràn và độ bền lưu trữ |

**Điểm phân biệt:** một queue lớn không tự tạo ra backpressure. Chỉ khi có cơ chế khiến bên gửi **không thể tiếp tục đẩy nhanh hơn khả năng nhận** (hoặc chủ động giảm gửi theo demand) mới có tác dụng điều tiết ngược. Ngược lại, một queue hữu hạn với thao tác chặn khi đầy vừa là buffering vừa tạo backpressure ở ranh giới queue.

## 3. Flow API trong Java: demand là tín hiệu backpressure

Từ Java 9, **java.util.concurrent.Flow** định nghĩa giao thức Publisher–Subscriber tiêu chuẩn trong JDK:

- **Flow.Publisher<T>**: nguồn phát sự kiện, đăng ký người nhận qua subscribe().
- **Flow.Subscriber<T>**: nhận onSubscribe(), onNext(), onError(), onComplete().
- **Flow.Subscription**: kết nối riêng giữa publisher và subscriber; request(n) tăng số phần tử được phép gửi, cancel() dừng đăng ký theo cơ chế best-effort.
- **Flow.Processor<T,R>**: vừa nhận từ upstream như Subscriber vừa xuất cho downstream như Publisher; cần tự quản lý demand cả hai chiều.

Trình tự cơ bản:

~~~text
Publisher                         Subscriber
    |                                 |
    | -------- onSubscribe(s) ------> |
    | <---------- s.request(2) ------ |
    | --------- onNext(A) ----------> |  còn 1 quyền nhận
    | --------- onNext(B) ----------> |  còn 0 quyền nhận
    |           chờ thêm demand       |
    | <---------- s.request(1) ------ |
    | --------- onNext(C) ----------> |
    | -------- onComplete() --------> |  sau khi nguồn kết thúc bình thường
~~~

**request(2)** là cấp thêm tối đa hai lần gọi onNext, không có nghĩa publisher lập tức sinh hai phần tử hay consumer đã hoàn tất hai tác vụ. Demand là quyền **giao sự kiện**, không phải lời hứa về throughput, độ bền lưu trữ hay “exactly once”.

Các quy tắc quan trọng:

1. Subscriber phải nhận onSubscribe trước khi nhận dữ liệu. Publisher đúng chuẩn không gọi onNext vượt quá tổng demand chưa sử dụng của đăng ký đó.
2. Mỗi lần onNext tiêu thụ một đơn vị demand. Các lần request(n) dương cộng dồn; request(Long.MAX_VALUE) thường được xem là nhu cầu gần như không giới hạn, tức subscriber tự bỏ giới hạn cấp phát theo demand.
3. request(0) hoặc request(số âm) là sai giao thức; subscriber nhận onError với IllegalArgumentException.
4. onError và onComplete là tín hiệu kết thúc; sau khi đã kết thúc sẽ không có thêm onNext hợp lệ. cancel() là best-effort nên một ít tín hiệu đang trên đường vẫn có thể xuất hiện.
5. Flow không bắt buộc mọi Publisher chạy bất đồng bộ. Lịch chạy, queue, thread và chính sách khi bộ đệm đầy thuộc về triển khai cụ thể; không được suy diễn từ interface Flow rằng nguồn sẽ tự động chậm lại.

### Ví dụ chạy được: subscriber yêu cầu từng phần tử

Lưu file **FlowDemandDemo.java** (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class FlowDemandDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);

        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(new Flow.Subscriber<Integer>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    subscription = s;
                    subscription.request(1);
                }

                @Override
                public void onNext(Integer value) {
                    System.out.println("Nhan " + value);
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println("Loi: " + error);
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("Hoan tat");
                    done.countDown();
                }
            });

            for (int i = 1; i <= 5; i++) {
                publisher.submit(i);
            }
        }

        if (!done.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Subscriber chua ket thuc");
        }
    }
}
~~~

Chạy:

~~~bash
javac FlowDemandDemo.java
java FlowDemandDemo
~~~

Stdout khi chạy thành công:

~~~text
Nhan 1
Nhan 2
Nhan 3
Nhan 4
Nhan 5
Hoan tat
~~~

Giải thích:

- onSubscribe() gọi request(1): ban đầu chỉ cấp quyền nhận một item.
- onNext() xử lý một item rồi cấp thêm một quyền. Đây là mô hình **request từng phần tử**; trong ứng dụng thật, chỉ yêu cầu thêm sau khi đã hoàn thành phần công việc cần giới hạn.
- SubmissionPublisher phát bất đồng bộ. CountDownLatch giữ main sống đến khi nhận terminal signal; khối try đóng publisher sau khi submit các phần tử. Nếu xảy ra lỗi, nhánh onError in ra stderr nên kết quả trên là **đường chạy bình thường**.
- **Đừng hiểu sai ví dụ:** việc subscriber request(1) **không chứng minh** producer chỉ tạo một item mỗi lần. SubmissionPublisher vẫn có buffer riêng và vòng for có thể submit nhiều item trước khi subscriber xử lý chúng. Demand giới hạn việc **phát onNext**, còn buffer và hành vi submit quyết định backlog ở bên trong.

Ví dụ đơn giản này không dùng Thread.sleep() để “chứng minh” backpressure. Ngủ trong onNext chỉ làm chậm xử lý và kết quả timing dễ khác nhau theo máy; để kiểm tra giới hạn thật cần đo số item đã tạo, đã giao, đang đệm và tốc độ các giai đoạn.

## 4. Buffering độc lập với Flow: ví dụ queue hữu hạn

Lưu file **BufferingDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BufferingDemo {
    public static void main(String[] args) {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);

        System.out.println(queue.offer(10));
        System.out.println(queue.offer(20));
        System.out.println(queue.offer(30));
        System.out.println(queue.poll());
    }
}
~~~

Chạy:

~~~bash
javac BufferingDemo.java
java BufferingDemo
~~~

Stdout:

~~~text
true
true
false
10
~~~

Điều này chứng minh **buffering**: queue có hai vị trí; offer(30) thất bại vì đầy, và poll() lấy phần tử 10. Bản thân offer() trả false **không làm producer chậm lại**; caller phải tự quyết định retry có giới hạn, bỏ, báo lỗi hoặc điều tiết đầu vào.

Thay offer() bằng **put()** sẽ khiến thread producer chờ khi queue đầy cho đến khi có chỗ (trừ khi bị ngắt). Một bounded BlockingQueue kết hợp put()/take() vì thế có cả buffer và backpressure **dạng chặn**. Không được gọi put() trong ví dụ một thread ở trên khi queue đang đầy mà không có consumer: chương trình sẽ chờ vô hạn.

Các biến thể phổ biến:

| Thành phần | Khi đầy | Điều gì cần hiểu |
|---|---|---|
| ArrayBlockingQueue.offer(x) | Trả false ngay | Có giới hạn đệm nhưng caller phải tự xử lý thất bại |
| ArrayBlockingQueue.put(x) | Producer chờ chỗ trống | Tạo điều tiết ngược dạng blocking khi consumer lấy item |
| ArrayBlockingQueue.offer(x, timeout, unit) | Chờ tối đa timeout, rồi false | Có giới hạn thời gian chờ, vẫn cần chính sách khi quá hạn |
| Queue không giới hạn thực tế hoặc không kiểm soát backlog | Có thể tăng đến áp lực tài nguyên | Hấp thụ tăng tải nhưng không bảo đảm an toàn bộ nhớ |
| Flow.Subscription.request(n) | Không cấp thêm quyền onNext khi demand đã hết | Backpressure **theo demand**; không phải một queue |

“Không giới hạn” ở đây chỉ là không đặt một capacity nghiệp vụ hữu hạn; mọi cấu trúc trong JVM vẫn chịu giới hạn heap, số phần tử hoặc tài nguyên thực tế.

## 5. SubmissionPublisher kết hợp cả hai cơ chế ra sao?

**SubmissionPublisher<T>** là triển khai Flow.Publisher có sẵn trong JDK. Nó dùng Executor để giao sự kiện bất đồng bộ và giữ **buffer riêng cho từng subscriber** khi cần. Do đó:

- **Subscriber** điều khiển quyền nhận qua Subscription.request(n); chưa đủ demand thì không được giao thêm onNext vượt quyền.
- **Publisher** có thể đã nhận dữ liệu bằng submit(), mặc dù subscriber chưa xử lý kịp; dữ liệu ở buffer trung gian.
- Buffer của từng subscriber có capacity tối đa. Constructor nhận maxBufferCapacity; sức chứa thực thi có thể được làm tròn đến lũy thừa 2, kiểm tra bằng getMaxBufferCapacity().
- **submit(item)** có thể chặn thread gọi khi bộ đệm subscriber đầy, đến khi có tài nguyên. Đây là backpressure ở ranh giới *bên gửi gọi submit*; method không tạo ra năng lực xử lý mới và việc chặn có thể lan tới upstream.
- **offer(item, onDrop)** hoặc overload có timeout có thể bỏ item khi tài nguyên không đủ và gọi callback xử lý drop. Đó là **chính sách mất dữ liệu có kiểm soát**, không đồng nghĩa với bảo đảm backpressure giữ nguyên mọi item.

Lưu ý nhiều subscriber: mỗi subscriber có bộ đệm và demand riêng. Một subscriber chậm có thể ảnh hưởng đến việc submit() dùng chung; một item có thể giao được cho người nhận này nhưng bị drop ở người nhận khác nếu dùng offer(). Hãy xác định rõ yêu cầu fan-out: có phải mọi subscriber đều cần đủ dữ liệu hay mỗi người có thể bỏ mẫu riêng?

Ví dụ cấu hình có giới hạn (đây là **đoạn minh họa**, không phải chương trình standalone):

~~~java
ExecutorService executor = Executors.newFixedThreadPool(2);
SubmissionPublisher<String> publisher =
        new SubmissionPublisher<>(executor, 8);

// ... subscribe, submit/offer và xử lý các terminal signal ...
// Khi hoàn tất, đóng publisher rồi shutdown executor theo vòng đời ứng dụng.
~~~

Không suy ra rằng request(1) đồng nghĩa “buffer chỉ có đúng một chỗ”. Hai đại lượng này độc lập:

- **Demand** = số lần onNext còn được phép phát.
- **Capacity** = số item có thể chờ trong buffer theo triển khai.

Dùng submit() trong một callback đang nắm tài nguyên mà consumer cần, hoặc dùng cùng một executor quá ít thread cho cả producer và consumer chặn nhau, có thể gây starvation/deadlock. Chọn ranh giới chặn, executor và thứ tự đóng tài nguyên cẩn thận.

## 6. Các chính sách khi consumer không theo kịp

**(a) Hấp thụ burst ngắn.** Dùng bounded queue/buffer. Cho phép tồn đọng nhất thời, đo queue depth và thời gian chờ. Không kỳ vọng buffer giải quyết việc nguồn luôn nhanh hơn consumer.

**(b) Giảm tốc nguồn có thể hợp tác.** Trong Flow, subscriber cấp demand theo khả năng xử lý; một publisher/processor đúng chuẩn tôn trọng demand. Với queue, bounded put() hoặc semaphore giới hạn in-flight có thể ngăn producer tiến quá xa. Phải quyết định việc chờ có phù hợp với thread hiện tại không.

**(c) Nguồn không thể giảm tốc.** Chẳng hạn một luồng nhận dữ liệu từ bên ngoài không có cơ chế pause/ack. Backpressure ở pipeline nội bộ không thể “ép” nguồn ngoài dừng. Cần chính sách rõ: reject, sample, drop-oldest/drop-latest, chuyển sang storage bền vững hoặc báo quá tải. Việc chọn policy phụ thuộc mức độ chấp nhận mất dữ liệu và yêu cầu thứ tự.

**(d) Nhiều bước xử lý.** Nếu producer → parser → aggregator → writer, demand ở downstream phải được quản lý xuyên qua từng bước. Một processor chuyển mọi item vào queue không giới hạn rồi lập tức request(Long.MAX_VALUE) từ upstream có thể phá vỡ mục tiêu giới hạn backlog, dù interface vẫn mang tên Flow.

**(e) Bảo toàn dữ liệu.** Backpressure không thay thế durable queue, transaction, retry, idempotency hay acknowledgment. Nếu ứng dụng phải chịu crash và không được mất item, phải thiết kế cơ chế lưu/khôi phục riêng.

## 7. Chọn chiến lược theo điều kiện thực tế

| Tình huống | Hướng xử lý phù hợp | Vì sao |
|---|---|---|
| Lưu lượng đến tăng trong vài trăm mili-giây rồi giảm | Buffer hữu hạn, đo peak | Hấp thụ dao động tạm thời mà không cần tạo hàng đợi khổng lồ |
| Tải đến luôn cao hơn công suất xử lý | Backpressure/rate limit + buffer hữu hạn | Điều chỉnh tốc độ bền vững; thêm buffer chỉ kéo dài thời gian tới quá tải |
| Producer và consumer là các thread Java độc lập | Bounded BlockingQueue với put/take hoặc offer có timeout | Dễ áp dụng giới hạn bộ nhớ và trạng thái khi đầy |
| Dòng dữ liệu dùng giao thức Flow | request(n) có chủ đích; kiểm tra cách publisher xử lý buffer | Giới hạn giao item theo demand, nhưng cần hiểu thiết kế publisher |
| Không thể trì hoãn nguồn; có thể bỏ mẫu | Bounded buffer + drop/reject policy rõ | Không tích lũy vô hạn, đổi lại có khả năng mất item |
| Không thể mất item và nguồn không thể dừng | Cần giới hạn đầu vào và lưu trữ bền vững/ack phù hợp | Flow hay queue trong heap không tự đảm bảo durability |

Khi thiết kế capacity, tính ít nhất: tốc độ cao điểm, thời gian xử lý trung bình và p95/p99, kích thước trung bình/tối đa mỗi item, số consumer, số subscriber, heap còn lại, nhu cầu giữ thứ tự và chính sách lỗi. **Độ trễ** phải được đo từ lúc sinh/nhận đến lúc xử lý xong; throughput tốt trên giấy vẫn có thể che backlog đang lớn lên.

Các tín hiệu vận hành hữu ích là số item chờ, tuổi item già nhất, demand còn lại (nếu có), thời gian producer bị chặn, số lần offer thất bại, drop count, mức sử dụng executor và latency cuối luồng. Đặt ngưỡng cảnh báo trước khi queue đầy hoặc thời gian xử lý vượt SLO.

## 8. Lỗi hiểu sai phổ biến

1. **“Tăng buffer sẽ hết backpressure.”** Sai. Buffer chỉ hoãn điểm nghẽn; khi chênh lệch tốc độ kéo dài, queue tiếp tục đầy.
2. **“Có queue là đã có backpressure.”** Chưa đủ. Queue không giới hạn, hoặc offer() trả false mà caller vẫn tiếp tục tạo việc, không điều tiết nguồn.
3. **“request(1) nghĩa là publisher chỉ tạo một item.”** Sai. Nó giới hạn delivery đến subscriber; publisher có thể có dữ liệu đã tạo nằm trong buffer.
4. **“request(Long.MAX_VALUE) giúp xử lý nhanh hơn.”** Không. Nó chỉ bỏ giới hạn demand; consumer vẫn có công suất hữu hạn.
5. **“submit() không bao giờ block vì nó bất đồng bộ.”** Sai. Giao onNext là bất đồng bộ nhưng submit() có thể block khi buffer cần thêm chỗ.
6. **“Backpressure đảm bảo không mất item.”** Sai. Hành vi drop, lỗi, crash và nguồn không hợp tác đều cần xử lý riêng.
7. **“Dùng sleep() trong demo là chứng minh được cơ chế.”** Không đáng tin. Cần quan sát demand, queue depth, số item đã gửi/xử lý và trạng thái đầy.
8. **“Cứ block producer là an toàn.”** Không luôn đúng. Block trong event loop, callback cần tiến triển hoặc executor bị chiếm hết có thể gây nghẽn/chết chờ.

### Tự kiểm tra kiến thức

- Với producer 500 item/s và consumer 400 item/s, hàng đợi còn 1.000 chỗ sẽ đầy sau khoảng bao lâu nếu bắt đầu rỗng và không điều tiết? **Khoảng 10 giây**.
- Subscriber đã request(3), nhận hai onNext, rồi request(2): còn tối đa mấy onNext chưa sử dụng? **3** (bỏ qua terminal/cancel và các lần request khác).
- ArrayBlockingQueue.offer() trả false khi đầy có tự tạo backpressure không? **Không** nếu caller bỏ qua false và tiếp tục sinh item; dùng put() hoặc một chiến lược điều tiết mới tạo phản hồi đáng kể lên producer.
- Trong SubmissionPublisher, request(1) có đảm bảo buffer bằng 1 item không? **Không**; demand và maxBufferCapacity là hai giới hạn khác nhau.

## 9. Ghi nhớ

**Buffering quản lý chỗ chờ; backpressure quản lý quyền/tốc độ đi vào chỗ chờ.** Một hệ thống tốt thường cần buffer có giới hạn để chịu burst, cơ chế điều tiết để không quá tải kéo dài, cùng chính sách rõ cho tình huống nguồn không thể hợp tác.

Phạm vi bài này là **Java Concurrency của JDK**; các tích hợp và abstraction riêng của Spring thuộc chương khác.

**Tham khảo JDK chính thức:** [Flow (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html), [Flow.Subscription (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html), [SubmissionPublisher (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html), [ArrayBlockingQueue (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-007">Giới hạn buffer và chính sách xử lý chậm</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn buffer và chính sách xử lý consumer chậm trong Java Flow

Trong một pipeline bất đồng bộ, producer có thể phát dữ liệu nhanh hơn consumer xử lý. Nếu không có giới hạn và quy tắc điều tiết, dữ liệu chờ tích tụ, độ trễ tăng, bộ nhớ cạn hoặc producer bị kẹt. Bài này tập trung vào **Java concurrency nói chung**, đặc biệt là các giao diện chuẩn **java.util.concurrent.Flow** và lớp **SubmissionPublisher** (từ Java 9). Các ví dụ sử dụng Java 17+, không phụ thuộc framework.

## 1. Buffer, demand và backpressure là ba khái niệm khác nhau

- **Buffer** là nơi giữ phần tử đã phát nhưng chưa xử lý xong. Giới hạn buffer (bounded capacity) khống chế số phần tử có thể nằm chờ tại một điểm trong pipeline.
- **Demand** là số phần tử mà subscriber cho phép publisher gửi thêm qua lời gọi **Flow.Subscription.request(n)**. Khi nhận một phần tử qua **onNext**, demand chưa đáp ứng giảm tương ứng. Demand là cơ chế điều tiết theo giao thức, không phải dung lượng bộ đệm.
- **Backpressure** là việc sức xử lý ở phía nhận ảnh hưởng đến tốc độ phát/nhận, thay vì cứ đẩy phần tử không giới hạn. Trong Flow, subscriber chủ động yêu cầu thêm phần tử; publisher và các tầng trung gian còn phải quản lý lượng dữ liệu chờ.
- **Slow consumer** là subscriber xử lý chậm, ngừng yêu cầu thêm, bị nghẽn I/O, hết tài nguyên hoặc mắc lỗi khiến backlog tăng.

Hình dung: publisher tạo 1.000 sự kiện/giây, subscriber chỉ giải quyết 600 sự kiện/giây. Nếu cứ tiếp nhận mà không chặn hay bỏ bớt, backlog tăng xấp xỉ **400 sự kiện/giây** trong giai đoạn đó. Sau 30 giây, khoảng 12.000 sự kiện đang chờ. Nếu mỗi phần tử chiếm trung bình 2 KiB, riêng dữ liệu payload có thể gần 24 MiB, chưa kể đối tượng, node hàng đợi, tham chiếu và overhead JVM. Đây là phép ước lượng để thiết kế, không phải cam kết chính xác về heap.

**Điểm cần nhớ:** tăng buffer chỉ giúp hấp thụ burst ngắn; nếu tốc độ đến trung bình luôn lớn hơn tốc độ xử lý, không có kích thước buffer hữu hạn nào chữa được sự chênh lệch lâu dài.

## 2. Mô hình Flow chuẩn của Java

~~~text
Producer --submit/offer--> Publisher --queue riêng--> Subscriber A
                                 |                 request(n)
                                 +--queue riêng--> Subscriber B (chậm)
                                                   request(n)
~~~

Bốn interface liên quan:

| Thành phần | Trách nhiệm |
| --- | --- |
| **Flow.Publisher<T>** | Cho phép subscribe; phát tín hiệu theo hợp đồng Flow. |
| **Flow.Subscriber<T>** | Nhận **onSubscribe**, các **onNext**, rồi một tín hiệu kết thúc **onError** hoặc **onComplete**. |
| **Flow.Subscription** | Ràng buộc một subscriber với publisher; **request(n)** tăng demand, **cancel()** hủy đăng ký. |
| **Flow.Processor<T,R>** | Vừa là subscriber ở phía upstream, vừa là publisher ở phía downstream; phải điều tiết cả hai chiều nếu tự triển khai. |

Quy tắc quan trọng:

1. Subscriber phải nhận **onSubscribe(subscription)** trước **onNext**. Nó thường giữ subscription và gọi **request(n)** với n dương.
2. Mỗi lệnh **request(n)** *cộng thêm* n vào số lượng được yêu cầu nhưng chưa nhận; không phải đặt lại cửa sổ về n. **request(0)** hoặc số âm là lỗi giao thức và dẫn đến **onError(IllegalArgumentException)** theo hợp đồng Subscription.
3. Publisher không được gửi nhiều **onNext** hơn tổng demand còn hiệu lực. **Long.MAX_VALUE** thường biểu thị demand hiệu quả không giới hạn, nhưng **không đồng nghĩa buffer vô hạn**.
4. Một subscriber có thể **cancel()**; việc ngừng gửi là best effort, có thể vẫn có tín hiệu đang trên đường. Subscriber đã hủy không nhất thiết nhận callback kết thúc.
5. Tín hiệu và các callback cần tuân thủ trật tự của từng subscription. Đừng giả định nhiều subscriber cùng chạy với tốc độ hoặc ở cùng thread.

**SubmissionPublisher<T>** là cách dùng Flow có sẵn trong JDK: nó dùng Executor để chuyển sự kiện bất đồng bộ cho từng subscriber; **mỗi subscriber có buffer độc lập**. Tham số **maxBufferCapacity** trong constructor là mức trần yêu cầu cho *mỗi buffer*, không phải một quota tổng chung cho cả publisher. Dung lượng thực thi có thể được làm tròn lên lũy thừa của 2; dùng **getMaxBufferCapacity()** để biết mức thực tế. **Flow.defaultBufferSize()** cung cấp một giá trị khởi đầu tham khảo, không phải cấu hình phù hợp với mọi tải.

**Cần phân biệt:**
- Subscriber đã request nhiều chưa chắc xử lý nhanh; callback **onNext** có thể rất lâu.
- Producer gọi **submit** nhanh không có nghĩa phần tử đã xử lý xong; phương thức có thể chỉ enqueue phần tử.
- Buffer mỗi subscriber bảo vệ khỏi burst cục bộ, nhưng với **submit**, một subscriber chậm vẫn có thể làm nghẽn đường gửi chung khi buffer của nó đầy.

## 3. Điều gì xảy ra khi buffer đầy?

JDK cung cấp hai nhóm API có ý nghĩa chính sách khác nhau:

| Cách phát | Khi buffer subscriber đầy | Giá trị trả về và ý nghĩa |
| --- | --- | --- |
| **submit(item)** | Chờ đến khi có chỗ; hành vi chờ là *uninterruptible* theo tài liệu API. | Ước lượng lag lớn nhất giữa các subscriber; không phải số phần tử đã xử lý. |
| **offer(item, onDrop)** | Thử phát ngay; nếu không đủ chỗ, có thể bỏ item đối với subscriber bị đầy. | Số âm là âm của số lần drop; số không âm là ước lượng lag lớn nhất. |
| **offer(item, timeout, unit, onDrop)** | Chờ có giới hạn khi thiếu tài nguyên, rồi xử lý theo onDrop; có thể liên quan ngắt thread. | Cùng quy ước: âm biểu thị drop, không âm biểu thị lag ước lượng. |

Trong hai phiên bản **offer**, callback **onDrop(subscriber, item)** chạy khi việc phát gặp đầy buffer. **return false** chấp nhận bỏ phần tử đối với subscriber đó; **return true** cho thử phát lại **một lần**, không bảo đảm thử lại thành công. Callback nên ngắn và tránh công việc tốn thời gian hoặc blocking vì các hoạt động khác của publisher có thể bị chặn trong lúc callback chạy. Với nhiều subscriber, một phần tử có thể đến được người này nhưng bị drop ở người khác; không được suy ra giao hàng nguyên tử cho cả nhóm.

**Cực kỳ quan trọng:** **offer + onDrop** của **SubmissionPublisher** không tự động thực hiện chính sách "bỏ phần tử cũ nhất". Nó xử lý thất bại khi phát **phần tử đang offer**. Muốn **drop-oldest**, coalesce theo key hoặc giữ phần tử mới nhất, cần thiết kế queue/operator riêng; không nên mô tả onDrop như một lệnh xóa đầu hàng đợi.

Một giá trị **offerResult < 0** thể hiện có drop trong lần gọi đó; không thể lấy số không âm để kết luận lag hoặc xử lý hoàn toàn đồng bộ. Các hàm **estimateMaximumLag()** và **estimateMinimumDemand()** dành cho quan sát xu hướng, có thể là ảnh chụp xấp xỉ cũ; không dùng làm điều kiện đồng bộ chính xác.

## 4. Chọn chính sách cho consumer chậm

Không có một chính sách tối ưu chung. Quyết định dựa vào khả năng mất dữ liệu, yêu cầu thứ tự, độ trễ và thời gian producer được phép chờ.

| Chính sách | Hành vi | Phù hợp | Đổi lại / lưu ý |
| --- | --- | --- | --- |
| **Block / backpressure producer** | Producer chờ chỗ trống, ví dụ **submit**. | Tác vụ cần giữ đủ phần tử, producer chịu được chờ. | Có thể tăng độ trễ; tuyệt đối tránh block thread nhạy với latency hoặc thread phải xử lý chính luồng giải phóng buffer. |
| **Bounded wait rồi drop/fail** | Chờ tối đa một khoảng với **offer(timeout, …)**. | Có deadline, ưu tiên không treo producer mãi. | Timeout không hứa tổng độ trễ end-to-end chính xác nếu có nhiều subscription/công đoạn. |
| **Drop newest** | Bỏ sự kiện mới khi đầy. | Telemetry, sampling hoặc cập nhật bỏ qua được. | Mất sự kiện; cần đếm drop và công bố rõ semantics cho người dùng. |
| **Drop oldest / keep latest** | Bỏ giá trị cũ, ưu tiên giá trị mới. | Giá trị trạng thái mới nhất như tiến trình, vị trí đo. | Cần hàng đợi/logic riêng; không phù hợp nhật ký giao dịch cần giữ thứ tự đầy đủ. |
| **Coalesce / deduplicate** | Gộp nhiều cập nhật cùng khóa thành một trạng thái. | UI state, đo lường có thể ghi đè. | Phải định nghĩa phép gộp, đồng bộ và bảo toàn semantics. |
| **Sample / throttle** | Giảm tần suất trước khi đến subscriber. | Dashboard, tín hiệu quan sát. | Chủ động bỏ độ phân giải; phải phân biệt lấy mẫu với bỏ ngẫu nhiên do quá tải. |
| **Fail fast / cancel** | Dừng subscriber hoặc pipeline khi vượt ngưỡng. | Hệ thống coi quá tải là lỗi cần phát hiện ngay. | Cần tuyến báo lỗi, retry và cơ chế khôi phục rõ ràng. |
| **Durable queue / spill to disk** | Ghi nơi lưu bền vững rồi tiêu thụ sau. | Event không được mất, producer và consumer cần tách vòng đời. | Phát sinh latency, giới hạn lưu trữ, đồng bộ, retry, duplicate và quản lý thứ tự. |

Nên viết ra một **hợp đồng overflow** trước khi triển khai: phần tử nào được phép mất, ai được block, block tối đa bao lâu, backlog lớn nhất, hành động khi timeout và cách quan sát. Với dữ liệu bắt buộc giao đủ, không chọn drop âm thầm. Với dữ liệu trạng thái liên tục được cập nhật, giữ "latest" có thể hợp lý hơn cố giao mọi mẫu cũ.

### Ví dụ tình huống

- **Audit event / lệnh thanh toán:** không được âm thầm drop; backpressure với hạn chờ và/hoặc hàng đợi bền vững, cơ chế retry/idempotency.
- **Số liệu CPU cập nhật 100 lần/giây lên giao diện chỉ vẽ 10 lần/giây:** sampling hoặc coalescing phù hợp vì trạng thái cũ nhanh chóng mất giá trị.
- **Pipeline phân tích batch:** bounded queue + giới hạn concurrency upstream; nếu consumer chậm liên tục, giảm tốc producer hoặc tăng năng lực consumer.
- **Nhiều subscriber khác nhau:** một subscriber cần dữ liệu đầy đủ, người khác chỉ cần latest. Đừng ép hai yêu cầu trái nhau vào một chính sách drop duy nhất.

## 5. Ví dụ Java 17+: chứng minh buffer đầy và drop có kiểm soát

Chương trình sau tạo **SubmissionPublisher** với một subscriber, **maxBufferCapacity = 1**, và một Executor một thread. Subscriber dừng có chủ ý tại phần tử **A** bằng CountDownLatch, tạo trạng thái có thể kiểm soát để chứng minh **B** chiếm buffer trong lúc **C** bị drop. Đây là mô phỏng kiểm thử sự chậm trễ, không phải cách nên khóa **onNext** trong ứng dụng thật.

Lưu thành **BoundedFlowDemo.java**:

~~~java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedFlowDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch enteredA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        List<String> received = new CopyOnWriteArrayList<>();
        AtomicInteger drops = new AtomicInteger();

        try {
            try (SubmissionPublisher<String> publisher =
                         new SubmissionPublisher<>(executor, 1)) {

                publisher.subscribe(new Flow.Subscriber<>() {
                    private Flow.Subscription subscription;

                    @Override
                    public void onSubscribe(Flow.Subscription s) {
                        this.subscription = s;
                        s.request(1);
                    }

                    @Override
                    public void onNext(String item) {
                        received.add(item);
                        if (item.equals("A")) {
                            enteredA.countDown();
                            try {
                                releaseA.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                subscription.cancel();
                                completed.countDown();
                                return;
                            }
                        }
                        subscription.request(1);
                    }

                    @Override
                    public void onError(Throwable error) {
                        completed.countDown();
                    }

                    @Override
                    public void onComplete() {
                        completed.countDown();
                    }
                });

                publisher.submit("A");
                if (!enteredA.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Subscriber chua nhan A");
                }

                publisher.submit("B"); // A dang xu ly; B nam trong buffer.
                int offerResult = publisher.offer("C", (subscriber, item) -> {
                    drops.incrementAndGet();
                    return false;      // Chap nhan drop C, khong retry.
                });

                releaseA.countDown();
                publisher.close();
                if (!completed.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Subscriber chua ket thuc");
                }

                System.out.println("received=" + received);
                System.out.println("drops=" + drops.get());
                System.out.println("offerResult=" + offerResult);
            }
        } finally {
            releaseA.countDown();
            executor.shutdownNow();
        }
    }
}
~~~

Biên dịch/chạy bằng Java 17 trở lên:

~~~bash
javac BoundedFlowDemo.java
java BoundedFlowDemo
~~~

Stdout khi chạy bình thường:

~~~text
received=[A, B]
drops=1
offerResult=-1
~~~

**Giải thích từng bước:**

1. **onSubscribe** yêu cầu 1 phần tử, nên **A** được đưa đến **onNext**. Latch **enteredA** xác nhận A đã rời queue và callback đang đợi.
2. Khi A bị giữ trong callback, publisher phát **B** vào buffer còn trống. Vì dung lượng buffer là 1, buffer nay đầy.
3. **offer("C", …)** không chờ vô hạn: callback trả **false**, nên C bị drop và kết quả là **-1** vì có một subscriber bị drop.
4. Main mở latch cho A; subscriber request thêm demand, xử lý B và nhận **onComplete** sau **close()**. **completed.await** bảo đảm chỉ in kết quả sau khi callback kết thúc.
5. **ExecutorService** được đóng trong finally để không rò thread. Latch có timeout khi chờ xác lập/hoàn tất kịch bản; latch cố tình chặn A chỉ nhằm tạo phép thử xác định.

Trong ứng dụng thật, không nên giữ thread giao hàng trong **onNext** chỉ để chờ I/O dài hoặc dựa vào lịch chạy của thread. Khi xử lý chậm, cân nhắc tách task ra một executor có giới hạn, quản lý demand theo mức hoàn thành và định nghĩa nơi chịu backpressure. Nếu callback chuyển việc sang một queue khác rồi request tiếp ngay, chính queue đó có thể phình ra nếu không có giới hạn.

## 6. Điều khiển demand theo cửa sổ

Gọi **request(Long.MAX_VALUE)** là phù hợp khi subscriber thật sự có thể nhận rất nhanh hoặc tác vụ đã được giới hạn ở lớp khác. Đối với consumer chậm, hãy cấp demand theo lô nhỏ rồi bổ sung khi đã xử lý được các phần tử. Một lựa chọn thực dụng:

- Chọn **window = N** theo số việc có thể xử lý an toàn.
- Khi **onSubscribe**, gọi **request(N)**.
- Sau mỗi lô **N/2** phần tử đã xử lý, gọi **request(N/2)** để bổ sung. Tại mọi thời điểm, số demand chưa đáp ứng được giữ có kiểm soát theo thuật toán cửa sổ.
- Nếu xử lý bất đồng bộ, chỉ bổ sung demand **sau khi** công việc thật sự hoàn tất hoặc có capacity ở queue phía sau; đừng coi việc vừa enqueue vào hàng đợi nội bộ là đã hoàn thành.

Ví dụ logic **cửa sổ 8** cho subscriber xử lý đồng bộ, viết như class riêng trong chương trình thực tế (đoạn dưới minh họa cách cấp demand; hàm **process** là nghiệp vụ cần tự cài đặt):

~~~java
final class WindowSubscriber<T> implements Flow.Subscriber<T> {
    private Flow.Subscription subscription;
    private int processedSinceRequest;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(8);
    }

    @Override
    public void onNext(T item) {
        process(item); // Hoan tat dong bo truoc khi cap them demand.
        if (++processedSinceRequest == 4) {
            processedSinceRequest = 0;
            subscription.request(4);
        }
    }

    private void process(T item) {
        // Xu ly nghiep vu dong bo, co gioi han tai nguyen.
    }

    @Override
    public void onError(Throwable error) {
        error.printStackTrace();
    }

    @Override
    public void onComplete() {
        // Ket thuc.
    }
}
~~~

Đoạn minh họa cần **import java.util.concurrent.Flow;** và được sử dụng cùng publisher để trở thành chương trình đầy đủ; nó không có **main** riêng. **window 8** không phải hằng số chuẩn. Điều chỉnh theo burst, thời gian xử lý, nhu cầu concurrency và heap thực tế. Nếu một **onNext** thất bại, cần chính sách lỗi/cancel phù hợp; không được giả định runtime tự retry an toàn.

### Demand không thay thế mọi giới hạn queue

Giả sử Subscriber A **request(8)** nhưng mất 2 giây mới xử lý mỗi item; Subscriber B **request(Long.MAX_VALUE)** nhưng xử lý nhanh. Có thể chọn buffer 16 cho mỗi subscription; đây là hai giới hạn khác mục đích. Nếu producer vượt sức xử lý của A, buffer của A vẫn đầy và **submit** sẽ phải chờ dù B đang rảnh. Hơn nữa, nếu A chuyển công việc từ **onNext** sang một queue phụ không giới hạn, buffer Flow nhỏ không còn bảo vệ tổng bộ nhớ của hệ thống. Cần chặn ở **mọi ranh giới bất đồng bộ**.

## 7. Tính dung lượng và ngưỡng chờ từ workload

Đầu tiên đo các đại lượng:

- **R_in**: tốc độ tới trung bình và peak, tính item/giây.
- **R_out**: throughput xử lý bền vững của consumer.
- **T_burst**: thời lượng burst cần hấp thụ.
- **S_item**: kích thước một phần tử thực tế trong bộ nhớ; nên đo, không chỉ lấy kích thước JSON trên mạng.
- **N_sub**: số subscriber và tính chất fan-out.
- **L_target**: độ trễ tối đa cho phép.

Khi R_in > R_out trong một đợt burst, ước lượng số item tích tụ:

~~~text
backlog_burst ≈ max(0, R_in - R_out) × T_burst
memory_payload ≈ capacity × S_item × N_sub
~~~

Ví dụ burst 1.200 item/giây trong 5 giây, consumer xử lý 900 item/giây thì backlog tăng khoảng **1.500 item**. Buffer 256 không đủ giữ toàn bộ burst nếu không giảm tốc hoặc drop. Chọn 2.048 có thể hấp thụ phần burst này, nhưng chỉ có ý nghĩa nếu consumer xử lý bù được khi đầu vào giảm. Với nhiều subscriber, đừng nhân máy móc mọi peak theo cùng một giả định; hãy tính từng buffer và đo heap, vì item có thể được tham chiếu chung còn cấu trúc queue lại riêng.

**Kiểm tra độ trễ:** một hàng chờ 2.000 item với throughput giải phóng 500 item/giây tương ứng khoảng **4 giây** chỉ riêng thời gian tháo hết queue nếu không có phần tử đến thêm. Dùng định luật Little (L = λW) cho dòng ổn định có điều kiện thích hợp, không áp dụng tùy tiện cho burst ngắn. Buffer lớn giúp giảm drop nhưng có thể kéo dài thời gian chờ và giữ dữ liệu cũ lâu hơn.

Đặt deadline cụ thể, chẳng hạn producer chỉ được chờ 20 ms, rồi báo lỗi/từ chối hoặc drop tùy tính chất dữ liệu. Nếu không có ngưỡng chờ, backlog có thể biến thành latency khó đoán. Với hệ thống yêu cầu đủ dữ liệu và không thể xử lý bù, tăng queue mãi chỉ trì hoãn quá tải.

## 8. Multi-subscriber, Executor và các điểm dễ gây nghẽn

**Fan-out:** mỗi subscriber của **SubmissionPublisher** có buffer và demand riêng. Một consumer chậm có thể dẫn đến drop chỉ trên subscription của nó khi dùng **offer**, nhưng với **submit** thì toàn bộ lời gọi producer có thể chờ vì bất kỳ buffer nào đầy. Cần xác định rõ contract giao hàng: tất cả subscriber bắt buộc đủ dữ liệu, hay mỗi subscriber có thể chọn chính sách mất dữ liệu khác nhau.

**Executor:** SubmissionPublisher giao callback bất đồng bộ qua Executor. Số thread, hàng đợi task của Executor và thời gian callback đều ảnh hưởng đến độ trễ. Constructor cần Executor có khả năng chạy task độc lập; đừng thiết kế executor chỉ có một thread rồi block chính thread ấy bằng task đang đợi task khác trên cùng executor. Thread pool/queue của executor cũng phải được đo và giới hạn theo workload; không coi maxBufferCapacity là trần của toàn bộ tài nguyên được sử dụng.

**Blocking:** **submit** có thể block producer. Nếu producer chạy trên thread nhạy latency hoặc callback nào đó cần đúng thread ấy để giải phóng chỗ, sẽ hình thành nghẽn hoặc deadlock do thiết kế. Dùng timeout hoặc cơ chế handoff thích hợp khi đó. Virtual thread (nếu sử dụng Java phiên bản hỗ trợ) có thể làm blocking rẻ hơn về mặt thread hệ điều hành trong một số trường hợp, nhưng **không tăng throughput consumer, không thay thế capacity và không làm dữ liệu tự hết tồn**.

**Exception và lifecycle:** **close()** báo hoàn tất cho subscriber hiện tại; **closeExceptionally(error)** báo lỗi. Nếu **onNext** ném ngoại lệ hoặc Executor từ chối task, cần đường báo lỗi/giám sát theo hợp đồng API; không được giả định phần tử đã phát thành công cho tất cả subscriber. Sau **close**, lệnh submit/offer mới không hợp lệ. Cần dọn tài nguyên Executor thuộc quyền sở hữu của ứng dụng.

## 9. Quan sát quá tải và kiểm chứng chính sách

Một thiết kế hoàn chỉnh cần tối thiểu:

1. **Đo input/output rate:** item được tạo, item được xử lý thành công mỗi giây.
2. **Đo độ trễ:** thời gian từ lúc tạo đến lúc xử lý xong, gồm cả thời gian chờ trong queue; ghi percentile thay vì chỉ trung bình.
3. **Đo backlog/lag:** dung lượng, lag hoặc số việc đang chờ ở từng tầng; các **estimate...()** của SubmissionPublisher chỉ là xấp xỉ để theo dõi.
4. **Đếm overflow:** số item drop, timeout, retry, cancel, callback lỗi và Executor rejection; gắn policy tương ứng.
5. **Theo dõi bộ nhớ:** heap, GC pause, tốc độ cấp phát và số lượng task/queue phụ.
6. **Kiểm thử tải thực:** consumer chậm hơn producer trong vài phút; burst rồi phục hồi; nhiều subscriber lệch tốc độ; một subscriber dừng request; lỗi giữa luồng; shutdown khi còn backlog.

**Các phép kiểm chứng nên có:** tại thời điểm đầy, producer có bị block quá deadline không? Dữ liệu buộc phải giữ có bị mất không? Nếu drop, đó là item mới hay cũ? Subscriber còn lại có tiếp tục hoạt động không? Khi nguồn giảm tốc, backlog có về mức bình thường? Nếu không trả lời được, chính sách overflow chưa được kiểm thử đủ.

## 10. Những lỗi thường gặp

- **Nhầm request(100) với buffer có capacity 100.** Request chỉ tăng demand, còn buffer do publisher quyết định.
- **Tăng maxBufferCapacity vô hạn khi consumer không theo kịp lâu dài.** Đó là cách trì hoãn overflow và đổi lấy heap/latency cao hơn.
- **Gọi request(Long.MAX_VALUE) ngay rồi đẩy sang queue nội bộ không giới hạn.** Flow có demand nhưng pipeline tổng thể vẫn thiếu backpressure.
- **Cho rằng offer luôn giao tới mọi subscriber.** Kết quả âm cho biết có drop; mỗi subscription có thể thành công/thất bại khác nhau.
- **Cho rằng return true trong onDrop bảo đảm giao lại.** Chỉ có một lượt thử lại, vẫn có thể thất bại.
- **Cho rằng onDrop của SubmissionPublisher là drop-oldest.** Muốn loại phần tử cũ cần tự hiện thực chính sách cụ thể.
- **Block trong onNext hoặc submit trên đường xử lý nhạy latency mà không đo.** Có thể lan nghẽn sang thread khác hoặc deadlock.
- **Dùng estimateMaximumLag làm khóa đồng bộ/điều kiện tuyệt đối.** API chỉ hứa giá trị ước lượng phục vụ quan sát.
- **Drop sự kiện cần bảo toàn thứ tự và độ đầy đủ mà không có hợp đồng dữ liệu.** Đây là sai về nghiệp vụ ngay cả khi chương trình không lỗi.
- **Quên đóng publisher/executor hoặc xử lý onError/cancel.** Gây rò tài nguyên hoặc pipeline treo khi có lỗi.

## 11. Quy trình lựa chọn nhanh

**Bước 1 — phân loại dữ liệu:** cần đúng và đủ hay chỉ cần cập nhật mới nhất? Chỉ chọn drop khi đã biết mất dữ liệu nào là chấp nhận được.

**Bước 2 — tính tốc độ và burst:** đo R_in, R_out, thời gian xử lý, giới hạn heap; ước lượng backlog và thời gian tháo queue. Có thể cân nhắc tăng consumer concurrency, nhưng chỉ khi nghiệp vụ cho phép parallelism và thứ tự vẫn được bảo đảm.

**Bước 3 — đặt giới hạn end-to-end:** buffer cho từng subscriber, queue ở executor, queue tự triển khai, số task đang chạy, số publisher/subscriber. Một queue không giới hạn phá hỏng tổng bảo vệ.

**Bước 4 — chọn phương án khi đầy:** block, bounded wait, drop latest/newest, drop oldest, gộp theo key, hoặc từ chối. Nếu không được mất dữ liệu mà consumer chậm kéo dài, cần điều tiết nguồn hoặc lưu bền vững.

**Bước 5 — công bố và kiểm thử contract:** xác nhận request window, overflow behavior, callback lỗi, hủy, shutdown và các chỉ số cảnh báo. Thử với subscriber chậm thực sự và nhiều subscriber.

## Tài liệu chuẩn

- Java SE 21 — **Flow**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html
- Java SE 21 — **Flow.Subscription**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html
- Java SE 21 — **SubmissionPublisher**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html

**Kết luận:** Trong Java Flow, **request(n)** quản lý nhu cầu nhận, **buffer capacity** giới hạn phần tử chờ, còn chính sách **submit/offer/overflow** quyết định điều gì xảy ra khi người tiêu thụ chậm. Hãy xác định rõ việc được phép chờ hay mất dữ liệu, đo throughput và backlog thực tế, rồi áp dụng giới hạn ở mọi hàng đợi của pipeline.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-008">Hủy subscription và giải phóng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Hủy subscription và giải phóng tài nguyên trong Java Flow

## 1. Vì sao hủy subscription là một phần của backpressure?

Trong Java, `java.util.concurrent.Flow` định nghĩa mô hình Publisher–Subscriber để trao đổi dữ liệu bất đồng bộ có điều tiết lưu lượng (*backpressure*). Một `Flow.Subscriber<T>` nhận đối tượng `Flow.Subscription` trong `onSubscribe`, rồi gọi `request(n)` để xin nhận tối đa `n` phần tử tiếp theo. Khi không còn nhu cầu tiêu thụ, subscriber có thể gọi `cancel()` để ngừng đăng ký.

Hủy subscription giải quyết **quyền tiếp tục nhận dữ liệu của một subscriber**. Giải phóng tài nguyên giải quyết **vòng đời của các đối tượng và công việc phục vụ luồng dữ liệu**: tác vụ đang chạy, bộ đệm, luồng I/O, socket, timer, executor, tệp mở, kết nối và các đối tượng giữ tham chiếu. Hai việc có liên quan nhưng không tự động thay thế cho nhau.

Ví dụ: màn hình theo dõi tiến trình đã đóng thì subscriber không cần nhận thông báo mới. Gọi `subscription.cancel()` là cần thiết; nếu chương trình còn có tác vụ đọc file theo chu kỳ và executor riêng thì thành phần sở hữu chúng vẫn phải dừng và đóng chúng.

**Mục tiêu học tập**

- Biết khi nào và ở đâu gọi `cancel()`, kể cả hủy do người dùng rời màn hình, timeout, đạt đủ số bản ghi, hoặc hệ thống dừng.
- Hiểu tính *best effort* của `cancel()`, sự khác nhau giữa hủy và tín hiệu kết thúc `onComplete`/`onError`.
- Xác định ai sở hữu tài nguyên để đóng đúng nơi, đúng thời điểm và chỉ một lần.
- Viết subscriber Java 17+ có kiểm soát demand, hủy chủ động và không treo khi chờ sự kiện hoàn tất.

## 2. Vai trò và vòng đời trong `Flow`

| Thành phần | Trách nhiệm liên quan đến hủy và tài nguyên |
| --- | --- |
| `Flow.Publisher<T>` | Phát dữ liệu theo demand; quản lý nguồn dữ liệu, hàng đợi hoặc tác vụ do chính publisher tạo; phải phản ứng phù hợp khi subscriber hủy. |
| `Flow.Subscriber<T>` | Nhận `onSubscribe`, giữ `Subscription`, gọi `request`/`cancel` và xử lý `onNext`, `onError`, `onComplete`. |
| `Flow.Subscription` | Liên kết **một** subscriber với publisher; quản lý demand và hủy liên kết đó. |
| `Flow.Processor<T,R>` | Vừa là subscriber phía upstream vừa là publisher phía downstream; phải thiết kế cách truyền tín hiệu hủy và dọn tài nguyên ở cả hai phía. |

Một vòng đời điển hình:

```text
subscribe(subscriber)
    |
    v
onSubscribe(subscription)
    |
    +-- request(n > 0) --> onNext(item) ... --> request(thêm)
    |                                           |
    |                                           +--> onComplete()
    |                                           +--> onError(error)
    |
    +-- cancel() ------> hủy đăng ký; không cần có tín hiệu terminal
```

Điểm cần phân biệt:

1. `request(n)` làm tăng lượng cầu chưa được đáp ứng; `n > 0`. Theo hợp đồng Flow, `request(n <= 0)` dẫn đến `onError(IllegalArgumentException)`.
2. `cancel()` yêu cầu publisher **cuối cùng** dừng gửi thông điệp cho subscriber đó. Nó là hành động *best effort*: khi có cạnh tranh giữa các luồng, vẫn có thể nhận thêm thông điệp sau lời gọi.
3. Sau `cancel()`, **không được chờ `onComplete()` hay `onError()` như một bảo đảm**. Hủy không phải là một tín hiệu kết thúc bình thường.
4. `onComplete()` là nguồn dữ liệu kết thúc bình thường; `onError(Throwable)` là kết thúc do lỗi. Cả hai kết thúc dòng tín hiệu cho subscription; chúng không đồng nghĩa với việc mọi tài nguyên phụ trợ đã được đóng.
5. Một publisher có thể phục vụ nhiều subscriber. Hủy **một** subscription không mặc nhiên đóng publisher hoặc hủy subscription của các consumer khác.

Trong ứng dụng thực tế, nên mô hình hóa tối thiểu các trạng thái: **đang hoạt động → đang hủy/đã hủy** hoặc **đã kết thúc**. Một cờ trạng thái nguyên tử giúp việc dọn dẹp lặp lại và các cuộc đua đồng thời an toàn hơn.

## 3. Khi nào hủy và điều gì không được giả định?

Hãy hủy khi không còn lý do để nhận thêm phần tử: người dùng đóng phiên theo dõi, đạt đủ mẫu, đạt deadline, lỗi trong khâu xử lý khiến tiếp tục nhận dữ liệu không có ý nghĩa, hoặc thành phần sở hữu subscriber chuẩn bị đóng.

Đừng sử dụng `cancel()` như một cơ chế ngắt tức thời tuyệt đối. Publisher có thể đã phát một item; callback `onNext` có thể đang xử lý hoặc đã được xếp lịch. Với luồng concurrent, thứ tự quan sát giữa thao tác hủy và callback đang chạy phụ thuộc vào triển khai và đồng bộ hóa của ứng dụng. Vì vậy:

- Đánh dấu trạng thái hủy trước hoặc khi gọi `cancel()`; callback đến trễ nên kiểm tra trạng thái trước khi tạo tác dụng phụ mới.
- Không gọi `request(1)` tiếp nếu đã quyết định hủy.
- Nếu callback còn sử dụng một file/socket/resource, **đừng đóng resource giữa chừng** mà không có cơ chế phối hợp. Dùng lock, bộ đếm tác vụ đang xử lý, hoặc giao thức chờ xử lý xong rồi mới đóng.
- Nếu thời điểm đóng phải có giới hạn, dùng timeout khi chờ tác vụ thoát và xác định chính sách xử lý phần việc còn dở.
- Cần phân biệt “đã yêu cầu hủy” với “mọi công việc thực sự đã dừng”. Nếu cần chờ điều kiện thứ hai, ứng dụng phải có tín hiệu phối hợp riêng như `CountDownLatch`, `Future` hoặc một completion stage.

Không có API chung của `Flow.Subscription` để “await cancelled”. `cancel()` cũng không có `boolean` trả về cho biết đã giải phóng xong tài nguyên.

## 4. Ví dụ chạy được: subscriber tự hủy sau ba phần tử

Ví dụ sử dụng `SubmissionPublisher<Integer>` có sẵn trong Java, thay vì tự viết một publisher đầy đủ. Subscriber yêu cầu từng phần tử một; sau phần tử thứ ba, nó hủy. `CountDownLatch` là tín hiệu **do ứng dụng chủ động phát ra lúc hủy**, chứ không phải do Flow tự gửi `onComplete`.

Tạo file `CancelAfterThree.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class CancelAfterThree {
    static final class FirstThreeSubscriber implements Flow.Subscriber<Integer> {
        private final CountDownLatch stopped;
        private Flow.Subscription subscription;
        private int received;
        private boolean cancelled;

        FirstThreeSubscriber(CountDownLatch stopped) {
            this.stopped = stopped;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(Integer value) {
            if (cancelled) {
                return; // Bỏ qua tín hiệu đến trễ sau khi đã hủy.
            }

            System.out.println("Nhận: " + value);
            received++;

            if (received == 3) {
                cancelled = true;
                subscription.cancel();
                System.out.println("Đã hủy subscription");
                stopped.countDown();
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable error) {
            System.err.println("Lỗi: " + error);
            stopped.countDown();
        }

        @Override
        public void onComplete() {
            System.out.println("Publisher hoàn tất");
            stopped.countDown();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch stopped = new CountDownLatch(1);

        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(new FirstThreeSubscriber(stopped));

            for (int i = 1; i <= 5; i++) {
                publisher.submit(i);
            }

            if (!stopped.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Chờ subscriber quá lâu");
            }
        } // Đóng publisher tại nơi sở hữu publisher.
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac CancelAfterThree.java
java CancelAfterThree
```

Khi chương trình chạy bình thường, output:

```text
Nhận: 1
Nhận: 2
Nhận: 3
Đã hủy subscription
```

**Giải thích từng điểm quan trọng:**

1. `onSubscribe` nhận subscription rồi xin `request(1)`, cho phép publisher gửi một phần tử. Sau mỗi phần tử được giữ lại, subscriber xin thêm đúng một phần tử; đây là backpressure ở mức đơn giản.
2. Khi `received == 3`, subscriber đặt `cancelled = true` rồi gọi `subscription.cancel()`; nó không xin thêm phần tử. Các item `4` và `5` đã được gửi vào publisher không đồng nghĩa sẽ tiếp tục được giao cho subscription đã hủy.
3. `SubmissionPublisher` giao callback bất đồng bộ và giữ đúng thứ tự phần tử trong ví dụ này. `CountDownLatch` giúp main đợi đến lúc subscriber đã thực hiện yêu cầu hủy, thay vì thoát ngay khi gọi `submit`.
4. Vì callback của **một subscription** được gửi theo thứ tự, cờ `cancelled` trong ví dụ nhỏ này chỉ được truy cập từ chuỗi callback đó. Trong ứng dụng hủy từ một luồng khác, cần `AtomicBoolean` hoặc đồng bộ hóa tương đương.
5. `try-with-resources` đóng `SubmissionPublisher` khi rời khối. Nó là bước dọn dẹp **publisher**, khác với việc subscriber vừa hủy đăng ký. Publisher mặc định dùng `ForkJoinPool.commonPool()`; không được gọi shutdown trên common pool chỉ vì đóng publisher.
6. Sau khi hủy, ví dụ **không** chờ `onComplete`. Nếu bỏ `stopped.countDown()` trong nhánh hủy, chương trình có thể chờ mãi hoặc hết timeout dù `cancel()` đã hoạt động đúng.

Ví dụ trên giả định không có lỗi hệ thống và executor còn nhận nhiệm vụ. `await(5, ...)` bảo vệ việc chờ; thời gian 5 giây là timeout trình diễn, không phải cam kết hiệu năng của `Flow`.

## 5. Phân biệt `cancel()`, đóng publisher và đóng tài nguyên ứng dụng

| Thao tác | Phạm vi và ý nghĩa | Có tự động dọn mọi tài nguyên không? |
| --- | --- | --- |
| `subscription.cancel()` | Yêu cầu dừng gửi dữ liệu cho **subscription cụ thể**. | Không. Không buộc đóng publisher, executor, kết nối hay resource mà caller sở hữu. |
| `publisher.close()` với `SubmissionPublisher` | Ngừng nhận item mới; gửi `onComplete` bất đồng bộ đến subscriber còn hoạt động. | Chỉ theo quy ước của publisher đó. Không bảo đảm callback đã hoàn tất khi `close()` trả về. |
| `publisher.closeExceptionally(error)` | Đóng publisher, phát `onError(error)` đến các subscriber còn hoạt động. | Không thay thế việc đóng resource ngoài publisher. |
| `executor.shutdown()` hoặc `shutdownNow()` | Bắt đầu dừng executor do ứng dụng sở hữu; `shutdownNow` chỉ cố gắng ngắt nhiệm vụ. | Phải phối hợp với task và giới hạn thời gian; không bảo đảm task dừng tức thì. |
| `resource.close()` | Giải phóng tài nguyên `AutoCloseable` như stream/kết nối theo giao ước của resource. | Chỉ dọn chính resource đó; không tự quyết định trạng thái subscription. |

Ví dụ một subscriber có reader và một publisher dùng executor riêng:

```text
Tài nguyên được tạo ở đâu?       Người chịu trách nhiệm đóng
-----------------------------    ----------------------------------------
Subscription nhận trong onSubscribe  Subscriber gọi cancel khi hết nhu cầu
SubmissionPublisher tạo tại main      main/service owner gọi close
ExecutorService tạo tại main          main/service owner shutdown và await
InputStream mở trong source adapter   source adapter đóng sau khi callback/tác vụ an toàn
Timer do component tự tạo             component hủy timer và đóng scheduler
```

**Quy tắc sở hữu:** thành phần tạo resource hoặc nhận trách nhiệm sở hữu rõ ràng mới quyết định khi nào đóng. Nếu một executor được chia sẻ cho nhiều publisher/subscriber, hủy một consumer tuyệt đối không được đóng executor chung.

Nếu một `Flow.Processor` trung gian bị hủy ở phía downstream, việc có hủy upstream ngay hay không tùy thiết kế pipeline: có thể còn downstream khác hoặc xử lý công việc chung. Không suy ra tự động rằng hủy ở một đầu sẽ dọn toàn bộ chuỗi.

## 6. Dọn dẹp một lần, kể cả khi lỗi hoặc hủy đồng thời

Trong hệ thống có callback, timeout và yêu cầu dừng từ người dùng, nhiều đường thực thi có thể cùng cố dọn resource: `cancel()`, `onError()`, `onComplete()`, `close()` của service. Mẫu cần áp dụng là **idempotent cleanup**: dù gọi nhiều lần, một resource chỉ được thực hiện quy trình đóng một lần.

Đoạn sau chỉ **minh họa phương thức dọn dẹp bên trong một component** đã khai báo các trường `subscription`, `task`, `ownedExecutor` và `closed`; đây không phải chương trình độc lập:

```java
private final java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();

private volatile Flow.Subscription subscription;
private java.util.concurrent.Future<?> task;
private java.util.concurrent.ExecutorService ownedExecutor;

@Override
public void close() {
    if (!closed.compareAndSet(false, true)) {
        return; // Thành phần khác đã bắt đầu dọn dẹp.
    }

    Flow.Subscription current = subscription;
    if (current != null) {
        current.cancel();
    }

    if (task != null) {
        task.cancel(true); // Chỉ yêu cầu ngắt; tác vụ phải hợp tác.
    }

    if (ownedExecutor != null) {
        ownedExecutor.shutdown(); // Chỉ khi component sở hữu executor.
    }
}
```

Trong component thật, phải bổ sung cách chờ các tác vụ đang sử dụng resource, chính sách timeout và cách xử lý lỗi khi gọi `close()`. `AtomicBoolean` ngăn dọn lặp nhưng **không tự giải quyết** mọi cuộc đua: chẳng hạn `close()` có thể đến trước `onSubscribe`, khi `subscription` vẫn `null`. Khi đó `onSubscribe` phải kiểm tra trạng thái đã đóng, hủy ngay subscription mới nhận và tuyệt đối không cấp thêm demand. Nếu khởi tạo resource xảy ra sau lúc bắt đầu dọn, cần phối hợp đồng bộ quá trình khởi tạo với đóng.

Đặc biệt, đừng dùng `cancel()` đơn độc để bảo đảm một thread đang thực thi `onNext` đã dừng trước khi socket hay file bị đóng. Thứ tự an toàn thường là: đánh dấu ngừng nhận việc mới → hủy subscription → ngăn task mới → đợi task đang sử dụng resource kết thúc (có giới hạn) → đóng resource → ghi nhận kết quả.

## 7. Thiết kế đường kết thúc cho mọi tình huống

Tùy thuộc ứng dụng, mỗi đường kết thúc cần một cách hoàn thành khác nhau:

| Tình huống | Xử lý subscription | Xử lý hoàn thành/dọn dẹp |
| --- | --- | --- |
| Nguồn phát hết dữ liệu | Subscriber nhận `onComplete`; không cần hủy thêm để dừng dữ liệu. | Dọn resource do subscriber sở hữu và báo hoàn tất cho service nếu cần. |
| Publisher phát lỗi | Subscriber nhận `onError`; kết thúc luồng đó. | Ghi nhận lỗi, dọn resource; quyết định retry là việc ở tầng điều phối. |
| Consumer tự dừng sớm | Subscriber chủ động gọi `cancel()`. | Phát tín hiệu hoàn tất **của ứng dụng**; không chờ `onComplete` từ Flow. |
| Deadline / người dùng đóng màn hình | Thành phần quản lý vòng đời yêu cầu hủy, dừng tác vụ liên quan. | Hợp nhất các đường cleanup bằng trạng thái nguyên tử, quản lý timeout. |
| Publisher/service shutdown | Chủ sở hữu đóng publisher và các resource do mình cấp phát. | Nếu cần chờ callback đã chạy xong, sử dụng công cụ phối hợp riêng. |
| `onNext` thất bại | Chính sách lỗi phụ thuộc publisher và cách xử lý callback. | Tránh ném lỗi vô kiểm soát; ghi nhận nguyên nhân, yêu cầu hủy và dọn resource thích hợp. |

Với `SubmissionPublisher`, nếu callback `onNext` ném ngoại lệ, subscription của subscriber đó bị hủy; nếu muốn ghi nhận lỗi và dọn resource riêng, phải xây dựng đường xử lý của chính ứng dụng. Không thể giả định `onError` của subscriber sẽ được gọi sau mọi ngoại lệ phát sinh trong chính `onNext`.

Nếu cần tự phát hiện timeout, deadline hoặc hết phiên, có thể dùng `ScheduledExecutorService` hay `Future` do service quản lý; hủy timer khi xử lý xong. Không nên tạo một luồng nền vô hạn mà không có đường tắt khi subscriber hủy.

## 8. Những lỗi thường gặp

1. **Nhầm hủy với hoàn tất:** gọi `cancel()` rồi chờ `onComplete` để giải phóng resource. Kết quả có thể bị treo. Hãy tự dọn sau khi yêu cầu hủy hoặc phát tín hiệu hoàn tất riêng.
2. **Nhầm phạm vi:** hủy một subscriber nhưng đóng publisher hoặc executor dùng chung, làm gián đoạn subscriber khác.
3. **Quên hủy tác vụ sinh dữ liệu:** subscription đã dừng nhưng vòng lặp đọc file, socket, timer hoặc job vẫn chạy và vẫn cấp phát dữ liệu. Publisher tùy biến cần nối yêu cầu hủy với cơ chế dừng nguồn đúng cách.
4. **Giải phóng quá sớm:** đóng resource khi `onNext` đang dùng nó. Cần đồng bộ hoặc giao thức đợi các callback/tác vụ đang chạy.
5. **Tin rằng hủy là tức thời:** vẫn có callback đến muộn; callback phải biết trạng thái dừng và tránh tạo tác dụng phụ ngoài ý muốn.
6. **Dọn dẹp không idempotent:** timeout và nhánh lỗi cùng đóng resource, gây race hoặc lỗi thứ cấp. Dùng một đường cleanup chung.
7. **Gọi `request(0)` để “tạm dừng”:** theo hợp đồng Flow, demand không dương là lỗi. Muốn tạm ngừng, đơn giản là chưa xin thêm demand; muốn dừng vĩnh viễn thì `cancel()`.
8. **Yêu cầu quá nhiều để né backpressure:** `request(Long.MAX_VALUE)` tương đương cầu gần như vô hạn; chỉ phù hợp khi consumer thực sự chịu được dữ liệu đến.
9. **Không quản lý trường hợp đóng trước khi đăng ký:** `onSubscribe` đến sau `close()` vẫn xin dữ liệu hoặc giữ subscription không bao giờ hủy.
10. **Đóng executor không thuộc quyền sở hữu:** shutdown một pool chia sẻ chỉ vì một luồng tiêu thụ kết thúc.

## 9. Checklist kiểm thử vòng đời

Để kiểm chứng một luồng Java Flow thực tế, nên có ít nhất các trường hợp sau:

- Subscriber nhận được `onSubscribe` rồi chủ động hủy trước khi xin item đầu tiên; không bắt buộc có `onComplete`/`onError`.
- Subscriber `request(1)`, nhận một item, hủy; kiểm tra không xử lý tác dụng phụ mới sau khi đã đánh dấu hủy, dù callback đến trễ.
- Hủy cùng lúc với `onNext` hoặc đồng thời với shutdown; tài nguyên không bị đóng khi còn callback đang sử dụng.
- Hủy/`close()` lặp nhiều lần; chỉ một lần giải phóng các resource được sở hữu.
- Lỗi xảy ra trong nguồn hoặc xử lý item; không bỏ sót logging, completion của service và cleanup.
- Publisher còn subscriber khác; hủy một subscriber không ảnh hưởng đến luồng còn lại.
- Timeout/đóng trước `onSubscribe`; subscription đến muộn được hủy ngay.
- Sau shutdown, không còn thread riêng, file descriptor, kết nối hoặc scheduled task bị giữ vô hạn.

Các bài test bất đồng bộ nên dùng `CountDownLatch`/`CompletableFuture` hoặc công cụ đồng bộ xác định; tránh kết luận bằng `Thread.sleep(...)` và các giả định timing mong manh.

## 10. Kết luận áp dụng

Trong Java Flow, `Subscription.cancel()` là lệnh **ngừng nhu cầu nhận dữ liệu của một subscriber**, không phải bảo đảm shutdown toàn bộ pipeline. Lệnh hủy có thể trễ, và subscription đã hủy không nhất thiết nhận tín hiệu terminal. Muốn quản lý vòng đời đúng, phải xác định resource owner, tạo đường cleanup một lần cho cả hủy/lỗi/hoàn tất, phối hợp với công việc đang chạy, và dùng tín hiệu riêng nếu service phải chờ dừng thực sự.

Tham khảo API chính thức:

- [`Flow.Subscription` (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html)
- [`Flow.Subscriber` (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html)
- [`SubmissionPublisher` (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-009">Thread safety trong callback và xử lý lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Thread safety trong callback và xử lý lỗi với Java Flow API

> Phạm vi: Java SE (Java 17+), đặc biệt `java.util.concurrent.Flow` và `SubmissionPublisher`. Bài này không bàn về Spring hay các thư viện reactive của Spring.

## 1. Bài toán cần giải quyết

Trong mô hình Publisher–Subscriber, dữ liệu được chuyển qua các callback `onSubscribe`, `onNext`, `onError`, `onComplete`. Với `SubmissionPublisher`, callback thường được gọi bất đồng bộ trên các luồng do `Executor` quản lý. Vì vậy, một bộ tiêu thụ phải giải quyết đồng thời ba vấn đề:

1. **Trạng thái có an toàn giữa các luồng không?** Callback có thể cập nhật bộ đếm, danh sách, cache hoặc kết quả mà luồng khác đang đọc.
2. **Có tuân thủ giao thức tín hiệu không?** Mỗi subscription phải quản lý `request(n)`, `cancel()` và tín hiệu kết thúc đúng cách.
3. **Lỗi xảy ra ở đâu, ai chịu trách nhiệm?** Lỗi dữ liệu của một phần tử, lỗi phát sinh từ upstream và ngoại lệ ném ra khỏi callback có hệ quả khác nhau.

`Flow` giải quyết việc đặt hàng tín hiệu và điều khiển nhu cầu (demand); việc bảo vệ **trạng thái ứng dụng** và xác định **chính sách khôi phục lỗi** vẫn thuộc trách nhiệm lập trình viên.

## 2. Giao thức callback và ý nghĩa lỗi

| Phương thức | Ý nghĩa | Điều cần giữ |
| --- | --- | --- |
| `onSubscribe(Subscription s)` | Nhận quyền điều khiển subscription; gọi `s.request(n)` để yêu cầu phần tử | `n > 0`; lưu subscription theo đúng vòng đời |
| `onNext(T item)` | Xử lý một phần tử đã được yêu cầu | Không chia sẻ mutable state thiếu bảo vệ; xác định cách xử lý lỗi của phần tử |
| `onError(Throwable error)` | Nhận tín hiệu lỗi **kết thúc** từ publisher | Ghi nhận nguyên nhân, giải phóng tài nguyên, thông báo kết quả thất bại |
| `onComplete()` | Nhận tín hiệu **kết thúc thành công** | Hoàn tất kết quả, giải phóng tài nguyên, đánh thức luồng đang đợi |
| `Subscription.cancel()` | Yêu cầu ngừng phát cho subscriber | Việc hủy có thể không tức thì và **không bảo đảm** có thêm `onComplete/onError` |

Một subscription hợp lệ có chuỗi tín hiệu dạng:

```text
onSubscribe
    |
    +-- request(n > 0) --> onNext --> onNext --> ... --> onComplete
    |                                      \
    |                                       +-----> onError (nếu upstream thất bại)
    +-- cancel() --------> ngừng phát (best effort; có thể không có terminal signal)
```

`onError` và `onComplete` là hai kết thúc loại trừ nhau. Sau tín hiệu kết thúc, publisher không gửi thêm tín hiệu đến subscription đó. `request(0)` hoặc `request(n < 0)` vi phạm giao thức: `Subscription` phải dẫn đến `onError` với `IllegalArgumentException`. `cancel()` không phải lệnh đồng nghĩa với `onComplete()`.

## 3. Flow bảo đảm điều gì về thread safety?

### 3.1. Tín hiệu được tuần tự hóa **theo từng subscription**

Giao thức `Flow.Publisher` yêu cầu callback đến **cùng một subscription** được phát theo thứ tự có quan hệ *happens-before*. Bởi vậy, nếu cùng một subscriber chỉ có một subscription hợp lệ, không đưa trạng thái ấy ra luồng khác và không tạo tác vụ phụ cùng sửa trạng thái, các callback của nó có thể dùng biến nội bộ thông thường mà không cần khóa riêng chỉ để chống hai `onNext` đồng thời.

Tuy nhiên, **tuần tự không đồng nghĩa với cố định một thread**. Các callback kế tiếp có thể chạy trên các thread khác nhau; không dựa vào `ThreadLocal`, tên thread hay giả định “callback luôn nằm trên main thread”. Với `SubmissionPublisher`, việc chuyển giao phần tử thường được thực thi bất đồng bộ bởi `Executor`.

### 3.2. Những gì Flow *không* tự bảo vệ

- Hai subscriber/subscription khác nhau có thể chạy callback song song. Nếu cùng ghi vào một `ArrayList`, `HashMap` hoặc bộ đếm chung thì phải phối hợp truy cập.
- Luồng gọi `submit`, luồng giám sát metrics và callback có thể hoạt động đồng thời. Quy tắc tuần tự hóa callback không làm mọi thao tác trên trạng thái bên ngoài trở nên an toàn.
- Nếu `onNext` tạo thêm `CompletableFuture`, task trong pool hay thread mới, tác vụ phụ ấy có thể chạy song song với callback kế tiếp. Khi đó trạng thái chung cần cơ chế đồng bộ độc lập.
- Một thao tác gồm nhiều bước, chẳng hạn “kiểm tra còn quota rồi tăng số lượng”, không trở nên nguyên tử chỉ vì từng biến là `volatile`.

### 3.3. Chọn cơ chế bảo vệ theo loại dữ liệu

| Tình huống | Cách làm phù hợp | Lý do |
| --- | --- | --- |
| Trạng thái chỉ được đọc/ghi bên trong callback của một subscription | Biến thường, không chia sẻ ra ngoài | Tín hiệu đã tuần tự theo giao thức |
| Cờ trạng thái được một thread ghi và thread khác đọc | `volatile` hoặc `AtomicBoolean` | Bảo đảm nhìn thấy cập nhật; atomic cho thao tác như CAS |
| Bộ đếm đồng thời | `AtomicLong`, `LongAdder` tùy yêu cầu | Tránh mất cập nhật kiểu `count++` |
| Hai trường phải thỏa bất biến chung | `synchronized`/lock hoặc một immutable snapshot thay thế nguyên tử | Hai atomic riêng lẻ không tạo transaction |
| Danh sách/tập hợp cùng được sửa bởi nhiều callback | Bộ sưu tập concurrent phù hợp hoặc khóa | Tránh data race và hỏng cấu trúc |
| Luồng ngoài cần đợi kết thúc rồi đọc kết quả | `CountDownLatch`, `CompletableFuture` hoặc cơ chế chờ đồng bộ | Vừa chờ tín hiệu cuối, vừa có bảo đảm visibility |

Ví dụ `volatile long count` vẫn **không** làm `count++` trở thành phép tăng nguyên tử. `AtomicLong.incrementAndGet()` phù hợp cho một bộ đếm; nếu cần nhất quán giữa `accepted` và `rejected` ở thời điểm chụp báo cáo, cần cùng một khóa/snapshot.

## 4. Backpressure và callback phải đi cùng nhau

Subscriber nhận phần tử theo **demand** đã cấp bằng `request(n)`. Một cách dễ hiểu và dễ kiểm chứng là chỉ giữ tối đa một yêu cầu chưa hoàn thành:

1. Trong `onSubscribe`, gọi `request(1)`.
2. Trong `onNext`, xử lý xong phần tử.
3. Nếu tiếp tục thì gọi `request(1)` để xin phần tử kế tiếp.

Cách này đơn giản nhưng có overhead cho mỗi phần tử; pipeline throughput cao thường cấp theo batch và bổ sung demand theo ngưỡng. **Demand không có nghĩa publisher không bao giờ buffer**: `SubmissionPublisher` có bộ đệm riêng cho từng subscriber. `submit(item)` có thể chặn khi không còn dung lượng; `offer(...)` có thể rơi phần tử theo chính sách được cấu hình. Không dùng số liệu ước lượng lag/demand như primitive đồng bộ hóa.

Tránh khóa quá rộng hoặc thực hiện I/O chậm trong `onNext`: điều này làm downstream trả demand chậm và có thể khiến upstream bị backpressure. Đặc biệt, không giữ khóa của trạng thái ứng dụng trong khi gọi hàm bên ngoài có thể gọi ngược/reentrant như `request`, `cancel`, logger tùy biến hoặc callback do người khác cung cấp.

## 5. Phân biệt ba lớp lỗi

| Lỗi | Ví dụ | Cách xử lý |
| --- | --- | --- |
| **Lỗi phần tử, có thể phục hồi** | Một giá trị âm không được phép | `onNext` bắt ngoại lệ dự kiến, ghi nhận/bỏ qua hoặc chuyển sang luồng xử lý lỗi, rồi cấp demand tiếp theo theo chính sách |
| **Lỗi upstream không thể tiếp tục** | Nguồn dữ liệu hỏng, không còn phát đúng được | Publisher phát `onError`; với `SubmissionPublisher`, gọi `closeExceptionally(cause)` |
| **Ngoại lệ thoát khỏi callback** | `onNext` ném `RuntimeException` không bắt | **Không** mặc định coi đây là `onError` của upstream; kiểm tra hợp đồng của publisher cụ thể. `SubmissionPublisher` hủy subscription nếu một phương thức subscriber ném ngoại lệ; handler tùy chọn của publisher có thể nhận ngoại lệ `onNext` trước khi hủy |

`SubmissionPublisher.close()` báo kết thúc bình thường qua `onComplete()`; `closeExceptionally(error)` báo kết thúc lỗi qua `onError(error)`. Nếu đã đóng, lời gọi đóng lại không tạo thêm một kết thúc mới.

Đối với lỗi nghiêm trọng ngay trong việc xử lý item, chọn chính sách rõ ràng: hủy subscription, báo thất bại qua một `CompletableFuture`/kênh trạng thái riêng và dọn tài nguyên. Vì hủy **không bắt buộc** phát terminal signal, không nên chỉ chờ `onError()` sau `cancel()` để kết thúc tác vụ ứng dụng. Với lỗi có thể bỏ qua, bắt **đúng loại ngoại lệ dự kiến**; không `catch(Throwable)` bừa bãi vì có thể nuốt các lỗi nghiêm trọng của JVM.

Trong `onError` và `onComplete`, làm thao tác dọn dẹp/hoàn tất đơn giản, có kiểm soát và tránh ném ngoại lệ. Nếu dọn dẹp có thể thất bại, ghi nhận lỗi riêng nhưng đừng biến đường kết thúc thành điểm phát sinh lỗi không được xử lý.

## 6. Ví dụ Java 17+: subscriber an toàn, backpressure và hai đường kết thúc

Lưu toàn bộ chương trình vào tệp **`SafeFlowCallbacks.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SafeFlowCallbacks {
    static final class RecordingSubscriber implements Flow.Subscriber<Integer> {
        private final AtomicLong total = new AtomicLong();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicReference<Throwable> terminalError = new AtomicReference<>();
        private final CountDownLatch finished = new CountDownLatch(1);
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(Integer item) {
            try {
                if (item < 0) {
                    throw new IllegalArgumentException("negative input: " + item);
                }
                total.addAndGet(item);
            } catch (IllegalArgumentException expected) {
                skipped.incrementAndGet();
            } finally {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable error) {
            terminalError.set(error);
            finished.countDown();
        }

        @Override
        public void onComplete() {
            finished.countDown();
        }

        void awaitTermination() throws InterruptedException {
            if (!finished.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for terminal signal");
            }
        }

        String report() {
            Throwable error = terminalError.get();
            String terminal = error == null ? "complete" : error.getClass().getSimpleName();
            return "total=" + total.get() + ", skipped=" + skipped.get()
                    + ", terminal=" + terminal;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        RecordingSubscriber success = new RecordingSubscriber();
        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(success);
            publisher.submit(10);
            publisher.submit(-7);
            publisher.submit(20);
        }
        success.awaitTermination();
        System.out.println("success: " + success.report());

        RecordingSubscriber failure = new RecordingSubscriber();
        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(failure);
            publisher.closeExceptionally(new IllegalStateException("source unavailable"));
        }
        failure.awaitTermination();
        System.out.println("failure: " + failure.report());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SafeFlowCallbacks.java
java SafeFlowCallbacks
```

Kết quả stdout (đã kiểm tra trên JDK 21, code dùng API có trong Java 17):

```text
success: total=30, skipped=1, terminal=complete
failure: total=0, skipped=0, terminal=IllegalStateException
```

**Giải thích chi tiết:**

- `publisher.subscribe(success)` đăng ký subscriber; `onSubscribe` nhận `Subscription` và yêu cầu đúng một phần tử. Các `submit` có thể xảy ra trước khi callback thực thi nhưng publisher quản lý delivery/demand.
- Với `10` và `20`, `total.addAndGet` cập nhật tổng. `-7` gây `IllegalArgumentException` **do quy tắc nghiệp vụ của ví dụ**, được bắt trong `onNext`; callback không ném lỗi ra publisher, nên stream tiếp tục.
- `finally { subscription.request(1); }` ở đây thể hiện chính sách *tiếp tục sau lỗi dữ liệu đã kiểm soát*. Với lỗi không thể phục hồi, **không** dùng `finally` yêu cầu tiếp vô điều kiện; thay bằng nhánh hủy và tự đánh dấu kết thúc thất bại.
- `try-with-resources` gọi `SubmissionPublisher.close()` ở đường thành công; subscriber được gửi `onComplete` sau các phần tử đã phát hợp lệ.
- Đường thất bại không gửi item: `closeExceptionally` chuyển `IllegalStateException` qua `onError`. `total=0` và `skipped=0`.
- `AtomicLong`, `AtomicInteger`, `AtomicReference` cho phép đọc snapshot an toàn nếu cần quan sát từ thread khác khi stream đang chạy. Ví dụ cũng dùng `CountDownLatch` để chờ terminal signal trước khi in; các ghi xảy ra trước `countDown` được nhìn thấy sau `await` thành công.
- `subscription` là biến thường vì **chỉ callback của một subscription** truy cập nó trong ví dụ. Nếu thread bên ngoài được phép hủy trực tiếp, thiết kế thêm cơ chế công bố subscription an toàn, ví dụ `AtomicReference<Flow.Subscription>`, và phối hợp trạng thái cancel/termination.

Lưu ý giới hạn minh họa: bộ đếm atomic bảo vệ từng phép cập nhật, nhưng không đảm bảo một ảnh chụp nhiều chỉ số lấy cùng một thời điểm nếu đọc **trong lúc** dữ liệu còn đến. Ở ví dụ, đọc báo cáo **sau** terminal signal nên không gặp vấn đề snapshot đang biến đổi.

## 7. Khi trạng thái nhiều trường phải nhất quán

Nếu mỗi callback của nhiều subscription cùng cập nhật metrics tổng hợp, hãy dùng một khóa chung cho cả cập nhật lẫn đọc snapshot. Ví dụ dưới đây là **đoạn lớp hỗ trợ**, có thể đặt vào chương trình khác; không phải chương trình độc lập có `main`:

```java
final class Metrics {
    record Snapshot(long accepted, long rejected) {}

    private long accepted;
    private long rejected;

    synchronized void record(boolean valid) {
        if (valid) {
            accepted++;
        } else {
            rejected++;
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(accepted, rejected);
    }
}
```

Khóa bao quanh cả `record` và `snapshot` giúp người đọc không thấy một trạng thái bị quan sát dở chừng. Nếu cần lưu danh sách sự kiện, `Collections.synchronizedList` chỉ đồng bộ **từng thao tác**; duyệt hoặc kiểm tra–rồi–sửa gồm nhiều thao tác vẫn cần đồng bộ theo tài liệu API. Đừng biến khóa thành chỗ gọi I/O hoặc callback bên ngoài.

## 8. Những race và lỗi vòng đời dễ gặp

### Đọc kết quả quá sớm

`publisher.submit(x)` không có nghĩa `onNext(x)` đã hoàn thành vì việc phát là bất đồng bộ. `Thread.sleep(100)` không phải cơ chế chứng minh hoàn thành. Dùng `CountDownLatch`, `CompletableFuture`, hoặc synchronization đúng vòng đời. Nếu chờ bị timeout, xem đó là lỗi/giới hạn thời gian cần được xử lý, đừng in kết quả như thể đã hoàn tất.

### Hủy subscription rồi chờ mãi

Một `cancel()` có thể còn vài tín hiệu đang trên đường giao và không buộc publisher phải phát `onError` hay `onComplete`. Nếu ứng dụng chủ động hủy, hãy tự hoàn tất promise/latch quản lý vòng đời và dọn tài nguyên idempotent. Không mặc định rằng hủy là thất bại upstream.

### Giữ chung một subscriber cho nhiều subscription

Gán `this.subscription` lần hai có thể làm callback của subscription trước gọi `request` trên subscription sau. Cách dễ hiểu nhất: **tạo một instance subscriber cho mỗi subscription**, hoặc thiết kế riêng một subscriber đa subscription với trạng thái/đồng bộ được chứng minh rõ ràng.

### Nuốt lỗi trong `onNext`

`catch(Exception ignored) {}` làm mất dấu lỗi, trong khi `request(1)` tiếp tục tiêu thụ vô hạn. Cần ghi nhận item lỗi, loại lỗi, policy retry/bỏ qua, giới hạn số lần thất bại và cách thông báo cho phía gọi. Không dùng retry vô hạn bên trong callback đang giữ thread phát.

### Ném ngoại lệ khỏi callback rồi mong `onError` luôn chạy

Đó là giả định sai về kênh lỗi. Với `SubmissionPublisher`, ngoại lệ do phương thức subscriber ném sẽ khiến subscription bị hủy; handler truyền cho constructor có thể quan sát lỗi phát ra từ `onNext`. Đường xử lý ngoại lệ trong một publisher tự viết có thể khác, nên phải đọc hợp đồng của implementation. Muốn báo lỗi nhiệm vụ cho caller, dùng một kênh kết quả riêng có trạng thái thất bại rõ ràng.

### Gọi `request(Long.MAX_VALUE)` ở mọi nơi

Điều này gần như bỏ giới hạn demand của subscriber và có thể làm tăng lượng việc chờ, phụ thuộc cơ chế buffer của publisher. Hãy chọn demand dựa trên tốc độ xử lý, bộ nhớ, độ trễ và chiến lược khi đầy hàng đợi. `request(1)` là phương án giảng dạy an toàn để hiểu vòng đời, không phải tối ưu throughput mặc định.

## 9. Chiến lược kiểm thử

Kiểm tra cả **trật tự tín hiệu** lẫn **trạng thái quan sát được**, tránh test dựa trên thời gian ngủ tùy ý:

1. **Kết thúc thành công:** nhiều item, có item bị loại hợp lệ, tổng và số loại đúng; `onComplete` xảy ra một lần; không có `onError`.
2. **Lỗi upstream:** publisher kết thúc bằng `closeExceptionally`; subscriber lưu đúng nguyên nhân và không coi đó là hoàn thành thành công.
3. **Backpressure:** subscriber chỉ `request(1)`; kiểm thử publisher tuân thủ demand và không gửi vượt số phần tử đã được yêu cầu.
4. **Đồng thời:** nhiều publisher/subscriber cập nhật metrics dùng chung; kết quả không bị mất cập nhật, snapshot tuân thủ bất biến.
5. **Hủy:** khởi phát `cancel()` lúc đang tải, xác nhận ứng dụng tự kết thúc vòng đời mà không chờ một terminal signal không được bảo đảm.
6. **Lỗi callback:** với `SubmissionPublisher`, kiểm tra subscription bị hủy khi callback ném ngoại lệ và đường quan sát lỗi đã cấu hình; không assert rằng publisher phải chuyển lỗi đó thành `onError` trên chính subscriber.
7. **Timeout và cleanup:** nếu nguồn không phát terminal signal, cơ chế đợi phải timeout/cancel hợp lý; executor và tài nguyên sở hữu bởi ứng dụng được đóng đúng lúc.

Một test race có giá trị phải lặp đủ để tìm vấn đề, nhưng kết quả chạy qua một lần không chứng minh không còn race. Nên thiết kế chia sẻ trạng thái qua các primitive đồng bộ có hợp đồng rõ ràng, rồi kiểm tra bất biến thay vì dựa hoàn toàn vào stress test.

## 10. Ghi nhớ khi áp dụng

- **Tuần tự callback theo subscription** là bảo đảm của `Flow`; **an toàn trạng thái chia sẻ** là quyết định thiết kế của ứng dụng.
- **Lỗi dữ liệu phục hồi được**, **lỗi upstream kết thúc stream** và **lỗi ném khỏi callback** cần ba chính sách khác nhau.
- **`onError` và `onComplete` là terminal signals**; **`cancel()` không hứa gửi terminal signal**.
- Dùng demand có chủ đích, tránh callback chặn lâu, và quan sát kết thúc qua cơ chế đồng bộ đáng tin cậy.

Tài liệu tham khảo chính thức:
- [Flow — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html)
- [Flow.Publisher — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Publisher.html)
- [Flow.Subscription — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html)
- [SubmissionPublisher — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-010">Flow API của JDK so với blocking queue</a>

<details>
<summary>Xem chi tiết</summary>

# Flow API của JDK so với BlockingQueue

## 1. Mục tiêu và bối cảnh

Trong Java, cả **Flow API** và **BlockingQueue** đều giúp kết nối nơi tạo dữ liệu với nơi xử lý dữ liệu khi tốc độ hai phía khác nhau. Chúng giải quyết bài toán này bằng những hợp đồng khác nhau:

- **BlockingQueue** là hàng đợi thread-safe theo mô hình **producer–consumer**. Producer chủ động đưa phần tử vào, consumer chủ động lấy phần tử ra. Với hàng đợi có sức chứa hữu hạn, producer có thể bị chặn khi hàng đợi đầy; consumer có thể bị chặn khi hàng đợi rỗng.
- **Flow API** (từ Java 9, gói **java.util.concurrent.Flow**) là bộ giao diện theo mô hình **Publisher–Subscriber**. Publisher đẩy sự kiện tới từng Subscriber, nhưng mỗi Subscriber thông báo trước số phần tử được phép nhận qua **Subscription.request(n)**. Cơ chế này gọi là **demand-driven backpressure**.

Câu hỏi chọn công cụ không đơn thuần là “cái nào nhanh hơn?” mà là: **cần một hàng đợi công việc để nhiều worker lấy việc, hay cần giao thức phát luồng dữ liệu có đăng ký, demand và tín hiệu kết thúc/lỗi?**

Nội dung dưới đây chỉ bàn về các API concurrency của JDK, không đi vào các framework khác.

## 2. BlockingQueue hoạt động thế nào?

**BlockingQueue<E>** là interface thuộc **java.util.concurrent**. Ví dụ triển khai:

- **ArrayBlockingQueue<E>**: dung lượng cố định, thứ tự FIFO; hữu ích khi cần giới hạn bộ nhớ và tạo áp lực ngược lên producer.
- **LinkedBlockingQueue<E>**: có thể đặt capacity; constructor không truyền capacity dùng giới hạn rất lớn (**Integer.MAX_VALUE**), vì thế không nên ngầm coi là queue có giới hạn thực tế.
- **PriorityBlockingQueue<E>**: ưu tiên theo thứ tự so sánh, không bảo toàn FIFO và không giới hạn dung lượng bằng một capacity cố định.
- **SynchronousQueue<E>**: không chứa phần tử tồn đọng; mỗi lần đưa phần tử vào cần ghép với một lần nhận tương ứng.

### 2.1. Các thao tác cần phân biệt

| Tình huống | Thao tác | Hành vi |
|---|---|---|
| Producer chờ chỗ trống | **put(e)** | Chờ nếu queue có giới hạn và đang đầy; có thể bị interrupt. |
| Producer không chờ | **offer(e)** | Trả **false** nếu hiện không thể chèn ngay. |
| Producer chờ có thời hạn | **offer(e, timeout, unit)** | Chờ tối đa theo timeout rồi trả kết quả. |
| Consumer chờ dữ liệu | **take()** | Chờ đến khi có phần tử; có thể bị interrupt. |
| Consumer không chờ | **poll()** | Trả **null** khi không có phần tử. |
| Consumer chờ có thời hạn | **poll(timeout, unit)** | Chờ tối đa theo timeout; có thể trả **null**. |

BlockingQueue không chấp nhận phần tử **null**. Một phần tử được **take()** sẽ bị lấy ra khỏi queue, nên hai consumer thông thường **cạnh tranh để nhận các phần tử khác nhau**. Queue không tự phát cùng một phần tử đến mọi consumer.

### 2.2. Áp lực ngược của hàng đợi có giới hạn

Giả sử queue có capacity = 2:

~~~text
Producer --put(A)--> [A,   ] --take()--> Consumer
Producer --put(B)--> [A, B]
Producer --put(C)--> [A, B]  (đợi vì đầy)
Consumer --take()--> [B,   ] (producer có thể tiếp tục put(C))
~~~

Sức chứa hữu hạn tạo **backpressure ở điểm chèn**: producer gọi **put()** phải đợi hoặc chọn chính sách timeout/drop/reject. Đây là cơ chế kiểm soát bằng **dung lượng buffer**, không phải việc consumer cấp từng “quyền nhận” cho producer. Nếu dùng queue gần như không giới hạn, backlog vẫn có thể tăng mạnh khi tốc độ tạo vượt tốc độ xử lý.

### 2.3. Ví dụ chạy được với Java 17+: hàng đợi có giới hạn

Tạo file **BlockingQueueDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class BlockingQueueDemo {
    private static final int END = -1;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);

        Thread producer = new Thread(() -> {
            try {
                for (int n = 1; n <= 3; n++) {
                    queue.put(n);
                }
                queue.put(END);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");
        producer.start();

        while (true) {
            int item = queue.take();
            if (item == END) break;
            System.out.println("item: " + item);
        }
        producer.join();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 BlockingQueueDemo.java
java BlockingQueueDemo
~~~

Kết quả chuẩn:

~~~text
item: 1
item: 2
item: 3
~~~

**Vì sao đúng?** Producer gửi 1, 2, 3 theo thứ tự rồi gửi marker **END**. **ArrayBlockingQueue** bảo toàn FIFO; consumer in từng giá trị đến khi gặp marker. Capacity = 2 khiến producer phải đợi nếu buffer đầy nhưng không làm mất thứ tự. **producer.join()** đợi producer kết thúc.

**Giới hạn của ví dụ:** Marker **-1** là quy ước riêng của ứng dụng. Trong hệ thống có nhiều consumer, cần chiến lược shutdown phù hợp (chẳng hạn số marker tương ứng với số consumer hoặc một cơ chế dừng tập trung). Nếu producer thất bại trước khi gửi marker, consumer có thể đợi vô hạn; ứng dụng thực tế cần xử lý lỗi, timeout, interrupt và hủy công việc.

## 3. Flow API: Publisher, Subscriber, Subscription và Processor

**Flow** là lớp chứa bốn interface chính, không phải một queue hay một engine xử lý hoàn chỉnh:

| Thành phần | Vai trò |
|---|---|
| **Flow.Publisher<T>** | Nguồn phát; cung cấp **subscribe(subscriber)**. |
| **Flow.Subscriber<T>** | Nhận tín hiệu đăng ký, phần tử, lỗi và hoàn tất. |
| **Flow.Subscription** | Liên kết dành riêng cho một Subscriber; hỗ trợ **request(n)** và **cancel()**. |
| **Flow.Processor<T,R>** | Vừa là Subscriber đầu vào vừa là Publisher đầu ra, dùng xây stage chuyển đổi dữ liệu. |

Một luồng tín hiệu cơ bản:

~~~text
Publisher                   Subscriber
    |---- onSubscribe(s) ------>|
    |<------ request(2) --------|
    |--------- onNext(A) ------>|
    |--------- onNext(B) ------>|
    |<------ request(1) --------|
    |--------- onNext(C) ------>|
    |--------- onComplete() --->|
~~~

### 3.1. Demand là một quyền gửi có đếm được

- Khi gọi **subscription.request(n)** với **n > 0**, Subscriber cộng thêm **n** vào demand chưa được đáp ứng của chính subscription đó.
- Publisher chỉ được gọi **onNext** trong phạm vi demand. **request(3)** cho phép *tối đa* 3 lần **onNext** bổ sung, không buộc Publisher phải tạo đủ 3 phần tử ngay.
- Các yêu cầu là cộng dồn; số demand có thể được xem là gần như vô hạn nếu dùng **request(Long.MAX_VALUE)**.
- **request(0)** hoặc số âm là sai hợp đồng: Subscriber phải nhận **onError(IllegalArgumentException)**.
- **cancel()** yêu cầu hủy subscription theo hướng best-effort; có thể còn tín hiệu đang chạy và không bắt buộc có **onComplete()** sau hủy.

**Điểm cốt lõi:** Demand giới hạn số lần gọi **onNext**; nó **không tự động giới hạn lượng dữ liệu mà một Publisher đã sản xuất, đã giữ trong bộ nhớ hoặc đã đặt vào buffer**. Việc giữ dữ liệu, lịch thực thi, chính sách overload và cách đẩy ngược áp lực lên nguồn do implementation quyết định.

### 3.2. Giao thức tín hiệu và vòng đời

1. **onSubscribe(subscription)** đến trước các tín hiệu còn lại của subscription đó. Subscriber nên lưu subscription rồi yêu cầu demand ban đầu.
2. **onNext(item)** được gửi khi còn demand; các callback của **cùng một subscription** diễn ra tuần tự theo thứ tự, không đồng thời với nhau.
3. **onError(throwable)** là tín hiệu kết thúc do lỗi không thể phục hồi; hoặc **onComplete()** là tín hiệu kết thúc bình thường. Sau tín hiệu kết thúc, không có **onNext** tiếp theo của subscription đó.
4. **cancel()** là hành động từ phía Subscriber; không tương đương một **onComplete()** được bảo đảm.
5. Mỗi Subscriber có Subscription và demand riêng; khi Publisher hỗ trợ nhiều Subscriber, một Subscriber chậm có thể có demand và backlog khác Subscriber nhanh.

Đối với **Flow.Processor**, phải tách demand của upstream và downstream. Một Processor xử lý đúng sẽ giới hạn yêu cầu upstream theo khả năng lưu trữ và nhu cầu downstream, thay vì yêu cầu vô hạn rồi giữ hết dữ liệu trong bộ nhớ.

### 3.3. Flow có luôn bất đồng bộ hay không?

**Không.** Các interface của Flow xác định hợp đồng tín hiệu và demand; tự thân chúng không bắt buộc mọi Publisher dùng thread riêng hoặc tuyệt đối không blocking. Cách callback chạy phụ thuộc implementation. Một Publisher có thể dùng executor để giao sự kiện bất đồng bộ; implementation khác có thể thực thi khác. Vì thế cần đọc tài liệu của **Publisher cụ thể**.

## 4. SubmissionPublisher: triển khai sẵn của JDK

**SubmissionPublisher<T>** là triển khai **Flow.Publisher<T>** dùng cho tình huống phát phần tử đến các Subscriber bằng cơ chế giao bất đồng bộ, với buffer **riêng cho từng Subscriber**.

- **subscribe(subscriber)** đăng ký người nhận và gửi **onSubscribe** theo cơ chế của publisher.
- **submit(item)** đưa item đến các Subscriber hiện tại; **có thể chặn thread gọi** nếu tài nguyên/buffer cho một Subscriber không khả dụng. Nó **không** có nghĩa “fire-and-forget, không bao giờ chờ”.
- **offer(item, onDrop)** và overload có timeout cho phép lựa chọn chính sách khi buffer không nhận được dữ liệu; handler có thể yêu cầu thử lại theo hợp đồng. Khi cần không mất dữ liệu, phải thiết kế cách xử lý trường hợp offer không chấp nhận phần tử.
- **close()** kết thúc bình thường sau khi giao các phần tử tồn đọng theo cơ chế publisher; **closeExceptionally(cause)** truyền lỗi kết thúc.
- Buffer mặc định có thể tham khảo qua **Flow.defaultBufferSize()**, nhưng phải kiểm tra capacity và đặc tính thực của Publisher đang dùng; demand và buffer capacity là hai khái niệm tách biệt.

### 4.1. Ví dụ chạy được với Java 17+: nhận từng phần tử theo demand

Tạo file **FlowDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

public final class FlowDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);

        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(new Flow.Subscriber<Integer>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(1);
                }

                @Override
                public void onNext(Integer item) {
                    System.out.println("item: " + item);
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println("error: " + error);
                    completed.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("done");
                    completed.countDown();
                }
            });

            for (int n = 1; n <= 3; n++) {
                publisher.submit(n);
            }
        }
        completed.await();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 FlowDemo.java
java FlowDemo
~~~

Kết quả chuẩn của lần chạy bình thường:

~~~text
item: 1
item: 2
item: 3
done
~~~

**Giải thích theo trình tự:**

1. Subscriber được cấp một **Subscription** tại **onSubscribe** và gọi **request(1)**.
2. Mỗi lần **onNext(item)** xử lý xong, nó xin tiếp một phần tử bằng **request(1)**. Theo cách này, mỗi lúc chỉ có tối đa một lượt giao **onNext** đang được cấp thêm demand trên subscription đó.
3. **submit()** đưa dữ liệu vào publisher; publisher có thể buffer trước khi callback chạy. Vì vậy, **request(1)** không có nghĩa producer phải đợi xong phần tử đầu mới được gọi **submit()** cho phần tử tiếp theo.
4. **try-with-resources** gọi **close()** trên publisher. **CountDownLatch** cho main thread đợi callback **onComplete** (hoặc **onError**) trước khi thoát, vì callback được giao bất đồng bộ.

Không dùng **Thread.sleep(...)** để “đợi đủ callback”: sleep tạo test chập chờn. Ví dụ sử dụng latch để quan sát việc kết thúc thực sự. Trong ứng dụng production, cần thêm chính sách timeout và hủy nếu nguồn không bao giờ kết thúc.

## 5. So sánh trực tiếp theo tiêu chí thiết kế

| Tiêu chí | Flow API / Publisher–Subscriber | BlockingQueue / Producer–Consumer |
|---|---|---|
| Hình thức giao tiếp | Publisher **đẩy** tín hiệu đến Subscriber có đăng ký. | Consumer **kéo** phần tử bằng **take()/poll()**. |
| Điều tiết tốc độ | Subscriber gửi **request(n)**: demand trên từng subscription. | Producer gặp giới hạn **capacity** khi **put/offer**. |
| Buffer | Không được bảo đảm bởi interface; tùy Publisher. **SubmissionPublisher** dùng buffer trên từng Subscriber. | Queue là buffer chia sẻ; capacity do implementation hoặc constructor quyết định. |
| Nhiều người nhận | Publisher hỗ trợ phát tới các Subscriber; tính broadcast cụ thể phụ thuộc implementation. | Nhiều consumer thường **chia việc**; một phần tử bị lấy ra đúng một lần. |
| Cách chạy | Tùy Publisher/executor; callback có thể bất đồng bộ, hợp đồng Flow không đảm bảo non-blocking. | **put/take** có thể chặn thread gọi. |
| Kết thúc bình thường | Tín hiệu **onComplete()**. | Không có tín hiệu complete chung; tự đặt marker/điều kiện dừng. |
| Kết thúc lỗi | Tín hiệu **onError(Throwable)**. | Không có kênh lỗi tích hợp; tự truyền lỗi qua kết quả, sentinel hoặc cơ chế phối hợp. |
| Hủy | **Subscription.cancel()**, theo best-effort. | Interrupt, cờ dừng, timeout, sentinel hoặc đóng lifecycle bên ngoài queue. |
| Áp dụng phổ biến | Event stream, pipeline theo tín hiệu, phân phối tới Subscriber, xử lý có demand. | Bounded work queue, worker pool, handoff giữa các thread, xử lý task theo từng phần tử. |
| Độ phức tạp | Cần tuân thủ lifecycle, demand, cancellation và terminal signals. | Đơn giản hơn cho một pipeline producer–worker, nhưng cần tự quản shutdown và lỗi. |

**Tránh nhầm lẫn “Flow hiện đại hơn nên luôn tốt hơn”.** Với một worker pool xử lý job theo kiểu mỗi job chỉ có một worker nhận, một **BlockingQueue hữu hạn** thường là cấu trúc trực tiếp và dễ kiểm soát hơn. Với giao diện muốn nhiều Subscriber quan sát dòng phần tử và chủ động cấp demand, **Flow** diễn đạt hợp đồng rõ hơn.

## 6. Hai cơ chế backpressure khác nhau ở đâu?

### 6.1. Ví dụ consumer tạm thời chậm

Giả sử producer có thể sinh 100 phần tử/giây, consumer xử lý 60 phần tử/giây.

**Với ArrayBlockingQueue capacity 200:**

- Khi backlog tăng đến capacity, **put()** bắt đầu chờ.
- Tốc độ thực tế của producer bị ghìm bởi việc chờ queue; nếu thay bằng **offer()**, ứng dụng phải quyết định khi **false** (thử lại, bỏ, log hay chuyển tuyến).
- Queue hữu hạn ngăn việc tồn đọng không giới hạn *trong chính queue*, nhưng không ngăn producer lưu dữ liệu ở một nơi khác trước khi put.

**Với Flow và request theo lô:**

- Subscriber có thể gọi **request(16)**, xử lý dần rồi request thêm khi có năng lực.
- Publisher không được giao **onNext** vượt demand của subscription; tuy nhiên nó vẫn có thể tạo/giữ dữ liệu trong buffer nội bộ trước thời điểm giao.
- Nếu Publisher có buffer hữu hạn, khi buffer đầy nó phải thực thi chính sách của mình: chờ, từ chối, bỏ hoặc điều tiết nguồn. **SubmissionPublisher.submit()** có thể chờ khi một Subscriber chậm làm đầy buffer.
- Một Subscriber khác có thể có demand khác, nên không thể suy ra một capacity hay backlog duy nhất của toàn hệ thống từ **request(n)**.

### 6.2. Backpressure không tự động loại bỏ mọi rủi ro

Cần kiểm soát **cả demand lẫn nơi tích tụ dữ liệu**. Những tình huống dễ gây lỗi:

- Subscriber gọi **request(Long.MAX_VALUE)** nhưng xử lý chậm: nó hầu như đã bỏ giới hạn demand; vẫn phải quản trị buffering.
- Publisher tự sinh hàng triệu item trước khi đọc demand: việc giao **onNext** đúng hợp đồng không cứu được bộ nhớ bị chiếm trước đó.
- Queue không đặt capacity: **put()** gần như không bao giờ chờ do đầy queue cho đến khi tài nguyên hệ thống có vấn đề.
- Subscriber gọi **request(1)** nhưng trong **onNext** lại khởi chạy công việc bất đồng bộ rồi request ngay: callback chỉ xác nhận đã *bàn giao* việc, không có nghĩa công việc thực sự hoàn tất. Demand nên gắn với số công việc đang được phép chạy hoặc giới hạn in-flight riêng.

Một công thức trực giác: nếu tốc độ đến lớn hơn tốc độ xử lý liên tục, backlog có xu hướng tăng. **Backpressure tốt phải truyền áp lực tới nơi tạo dữ liệu hoặc xác định chính sách từ chối/mất dữ liệu**, thay vì chỉ dời backlog sang lớp khác.

## 7. Lifecycle, lỗi, interruption và hủy

### Khi dùng BlockingQueue

- **InterruptedException** từ **put()/take()** là tín hiệu phối hợp dừng. Nếu bắt exception trong worker và không thể ném tiếp, thường cần **Thread.currentThread().interrupt()** và thoát theo chính sách của ứng dụng.
- Không có **queue.close()** chung. Quy ước marker độc lập với dữ liệu thật; nếu có nhiều worker, shutdown phải thức dậy được tất cả worker đang chờ.
- **poll(timeout)** và **offer(timeout)** cho phép tác vụ kiểm tra trạng thái dừng hoặc giới hạn thời gian chờ.
- Một task thất bại không tự báo lỗi tới mọi producer/consumer; cần cơ chế riêng như kết quả lỗi, future, supervisor hoặc shared state.

### Khi dùng Flow

- Nếu **onNext** xử lý đồng bộ lâu, tốc độ xử lý callback của subscription giảm. Nếu xử lý bất đồng bộ, cần quản lý số tác vụ đang chạy.
- Gọi **request(n)** trong **onSubscribe** hoặc sau khi xử lý item theo batch; tránh không request gì khiến luồng đứng yên dù publisher có dữ liệu.
- Với lỗi không thể phục hồi, Publisher/Subscription gửi **onError** rồi dừng; kết thúc bình thường dùng **onComplete**. **onError** và **onComplete** là hai đường kết thúc khác nhau.
- Khi ứng dụng không cần dữ liệu nữa, giữ Subscription và gọi **cancel()**; không chờ **onComplete()** như một lời hứa sau cancel.
- Không nên ném exception ra khỏi callback **onNext** rồi mặc định runtime sẽ retry hoặc xử lý như mong đợi; hành vi lỗi của callback có giới hạn theo hợp đồng và implementation. Cần xử lý hoặc chuyển lỗi có chủ đích.

## 8. Chọn thiết kế cho các trường hợp thực tế

### Trường hợp A: nhiều worker lấy job từ cùng một nguồn

**Chọn BlockingQueue hữu hạn** như **ArrayBlockingQueue** khi mỗi job cần một worker xử lý, số worker cố định và việc chờ hoặc từ chối khi đầy là hợp lý.

~~~text
Producer A --\
Producer B ----> [ bounded BlockingQueue ] ---> Worker 1
                                         \----> Worker 2
~~~

Cần đặt capacity, thời gian chờ, timeout/shutdown và quan sát queue depth. Nếu yêu cầu điều khiển tác vụ phức tạp hơn, cân nhắc các abstraction task execution của JDK, nhưng queue vẫn là thành phần trực tiếp cho handoff.

### Trường hợp B: một nguồn phát sự kiện cho nhiều người nghe

**Chọn Flow Publisher–Subscriber** khi mỗi người nghe cần tín hiệu riêng, muốn điều chỉnh demand độc lập và muốn nhận **onComplete/onError** theo giao thức rõ ràng.

~~~text
                       /--> Subscriber A (request 4)
Publisher --fan-out---<
                       \--> Subscriber B (request 1)
~~~

**SubmissionPublisher** phù hợp để học hoặc xây publisher cục bộ đơn giản. Với throughput lớn, lỗi phức tạp hoặc yêu cầu không mất dữ liệu, cần thiết kế thêm giới hạn buffer, chính sách overload, persistence/retry và kiểm thử thực tế; interface Flow không cung cấp tất cả những chức năng đó.

### Trường hợp C: biến một queue thành nguồn Flow

Có thể viết **Flow.Publisher** lấy dữ liệu từ queue, nhưng adapter đúng phải:

1. Đăng ký một Subscription riêng cho từng Subscriber và theo dõi demand an toàn giữa các thread.
2. Không lấy quá nhiều phần tử khỏi queue khi chưa có chỗ xử lý hoặc demand phù hợp.
3. Xử lý **cancel()**, terminal signals, interrupt, wake-up và shutdown.
4. Quy định rõ chuyển phát dạng **mỗi item một consumer** hay **mọi Subscriber đều nhận cùng item**; một queue dùng chung không tự broadcast.

Chiều ngược lại, nếu đưa **onNext** vào bounded BlockingQueue bằng **put()**, callback có thể chặn thread giao tín hiệu. Khi cần giữ callback ngắn, dùng **offer()** có chính sách overload rõ ràng và chỉ cấp thêm demand khi tài nguyên xử lý thực sự được giải phóng.

## 9. Lỗi nhận thức thường gặp

1. **“Flow là queue với tên khác.”** Sai: Flow định nghĩa giao thức phát luồng, Subscription, demand và terminal events; buffer là chi tiết implementation.
2. **“request(1) nghĩa là producer chỉ được tạo một item.”** Sai: nó hạn chế **onNext** đã giao, không tự hạn chế dữ liệu được tạo hay giữ trước đó.
3. **“SubmissionPublisher.submit không bao giờ chặn.”** Sai: khi không còn buffer cho Subscriber, **submit()** có thể chặn.
4. **“Nhiều consumer đọc BlockingQueue nghĩa là mỗi consumer nhận đủ mọi item.”** Sai: consumer cạnh tranh lấy phần tử.
5. **“onComplete có ngay sau khi gọi close rồi main thoát được.”** Không nên giả định callback bất đồng bộ đã chạy xong; cần đợi điều kiện hoàn tất thực sự khi cần.
6. **“BlockingQueue bất kỳ đều có backpressure nhờ giới hạn dung lượng.”** Sai: phải chọn implementation/capacity phù hợp và phương thức chèn có hành vi mong muốn.
7. **“request(Long.MAX_VALUE) là cách tối ưu mặc định.”** Có thể phù hợp khi không cần flow control, nhưng vô hiệu hóa giới hạn demand hữu ích trong luồng chậm.
8. **“cancel luôn gửi onComplete.”** Sai: cancel là best-effort và không bảo đảm terminal callback.
9. **“Flow tự xử lý mọi race condition.”** Sai: chỉ có những bảo đảm cụ thể về thứ tự callback/demand; trạng thái chia sẻ giữa các thành phần vẫn phải được đồng bộ đúng.
10. **“Chỉ cần cấu hình capacity là đảm bảo không mất dữ liệu.”** Sai: overload, lỗi process, restart và việc dùng **offer** đều cần chính sách đáng tin cậy.

## 10. Checklist khi áp dụng

- **Semantics:** mỗi item được *một worker* nhận, hay *mỗi Subscriber* nhận một tín hiệu?
- **Giới hạn:** capacity queue, capacity buffer từng Subscriber, số job đang xử lý và nguồn tạo dữ liệu đã được giới hạn chưa?
- **Overload:** khi downstream chậm, upstream phải đợi, retry, reject hay drop? Hành vi mất dữ liệu có được chấp nhận không?
- **Lifecycle:** bắt đầu, lỗi, timeout, cancel, interrupt và shutdown có đường đi rõ ràng không?
- **Quan sát:** theo dõi queue depth, thời gian chờ put/take, tốc độ item/giây, độ trễ xử lý, lỗi, dropped item và lag từng Subscriber nếu implementation hỗ trợ.
- **Kiểm thử:** thử consumer rất chậm, không có demand, queue đầy, callback ném lỗi, cancellation giữa chừng và stop khi worker đang chờ.

**Quy tắc chọn nhanh:** dùng **BlockingQueue hữu hạn** cho luồng công việc cần chia giữa các thread; dùng **Flow** khi cần hợp đồng Publisher–Subscriber với **demand, tín hiệu kết thúc/lỗi và khả năng nhiều subscription**. Cả hai đều cần chính sách buffer, tài nguyên và shutdown phù hợp.

## 11. Tài liệu JDK chính thức

- [Flow (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.html)
- [Flow.Subscriber (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html)
- [Flow.Subscription (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.Subscription.html)
- [SubmissionPublisher (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html)
- [BlockingQueue (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-011">Khi phù hợp để xây pipeline theo sự kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Khi phù hợp để xây pipeline theo sự kiện trong Java: Flow & Backpressure

Một **pipeline theo sự kiện** (event-driven pipeline) tổ chức công việc thành các giai đoạn nhận, biến đổi, lọc, tổng hợp hoặc ghi kết quả khi sự kiện xuất hiện. Thay vì một hàm gọi tuần tự mọi bước rồi đợi trả về, các giai đoạn có thể chạy độc lập, truyền thông điệp qua ranh giới bất đồng bộ và phối hợp tốc độ xử lý. Trong Java, `java.util.concurrent.Flow` (từ Java 9) đưa ra giao thức **Publisher–Subscriber** có cơ chế *backpressure* (điều tiết theo nhu cầu), giúp downstream nói rõ nó còn có thể tiếp nhận bao nhiêu phần tử.

Điểm cần quyết định không phải “có event thì dùng Flow”, mà là **pipeline có đủ lý do để chịu thêm độ phức tạp của xử lý bất đồng bộ, bộ đệm, yêu cầu dữ liệu, lỗi và hủy tác vụ hay không**.

Bài học tập trung vào Java concurrency thuần, đặc biệt là `Flow`, `SubmissionPublisher` và công cụ chuẩn `java.util.concurrent`. Không cần framework để hiểu và áp dụng các nguyên tắc bên dưới.

## 1. “Pipeline theo sự kiện” thực sự giải quyết vấn đề gì?

Giả sử ứng dụng nhận liên tục các bản ghi đo đạc:

```text
Nguồn sự kiện
    │
    ▼
Kiểm tra dữ liệu ──► Lọc sự kiện không hợp lệ
    │
    ▼
Chuẩn hóa/biến đổi
    │
    ▼
Tổng hợp theo điều kiện
    │
    ▼
Gửi kết quả đến nơi tiêu thụ
```

Nếu xử lý mọi thứ trên một thread tuần tự, đường đi dễ hiểu nhưng có thể không tận dụng tốt thời gian chờ I/O. Nếu nguồn sinh nhanh hơn nơi ghi kết quả, một hàng đợi không giới hạn có thể làm cạn bộ nhớ. Nếu nhiều giai đoạn chạy độc lập, hệ thống cần biết:

1. **Sự kiện đến từ đâu?** Nguồn kéo dữ liệu khi được hỏi (*pull*) hay phát liên tục dù chưa có người nhận (*push/hot source*)?
2. **Ai quyết định lượng công việc đang chờ?** Có hàng đợi/buffer hữu hạn và quy tắc khi đầy không?
3. **Người tiêu thụ chậm thì sao?** Chờ, giảm nhịp nguồn, bỏ sự kiện, ghi bền vững hay báo lỗi?
4. **Thứ tự và tính đúng đắn cần gì?** Cần giữ thứ tự từng nguồn, thứ tự toàn cục, xử lý đúng một lần hay chỉ cần kết quả cuối?
5. **Lỗi/hủy/trả tài nguyên lan qua các giai đoạn thế nào?** Có đóng subscription, executor, socket và resource đúng vòng đời không?

Pipeline theo sự kiện phù hợp khi những câu hỏi này có câu trả lời rõ ràng và ranh giới bất đồng bộ đem lại lợi ích thực tế.

### Event, command và batch không giống nhau

- **Event** là thông báo “điều đã xảy ra”, chẳng hạn `ReadingReceived` hoặc `FileChanged`. Nó có thể kích hoạt nhiều bộ xử lý.
- **Command** là yêu cầu làm một hành động, có thể cần kết quả hoặc xác nhận, chẳng hạn `RecalculateInvoice`.
- **Batch** là tập dữ liệu có giới hạn được xử lý theo đợt, ví dụ đọc một tệp rồi tính tổng.

Ba kiểu có thể kết hợp, nhưng không nên đổi một phép tính batch ngắn thành nhiều event và callback chỉ vì thư viện cung cấp API bất đồng bộ.

## 2. Những dấu hiệu NÊN cân nhắc event pipeline

| Dấu hiệu trong bài toán | Lợi ích khả dĩ | Cần kiểm chứng |
|---|---|---|
| Nguồn dữ liệu đến liên tục theo thời gian | Xử lý tăng dần, không đợi toàn bộ dữ liệu kết thúc | Cơ chế dừng, ranh giới buffer |
| Nhiều giai đoạn độc lập về thời gian hoặc I/O | Tách trách nhiệm, overlap công việc | Scheduling overhead, quản lý lỗi |
| Tốc độ nguồn và đích biến động | Backpressure có thể hạn chế lượng đang chờ | Nguồn có thực sự giảm nhịp được không? |
| Dữ liệu lớn/không muốn load toàn bộ vào RAM | Xử lý từng phần tử hoặc theo cửa sổ | Tổng hợp/window có giữ state có giới hạn không? |
| Mỗi sự kiện có thể được lọc và biến đổi độc lập | Dễ bố trí các stage và test riêng | Side effects, ordering, idempotency |
| Có nhiều người tiêu thụ với tốc độ khác nhau | Publisher có thể phân phối tới các subscriptions | Buffer và độ trễ riêng từng subscriber |
| Cần hủy luồng xử lý khi người nhận bỏ cuộc | `Subscription.cancel()` tạo kênh thông báo hủy | Các stages có giải phóng tài nguyên thật không? |
| Một bước blocking cần tách khỏi nơi phát sinh event | Không giữ thread nguồn chờ thao tác nặng | Chọn executor/concurrency model phù hợp |

**Ví dụ hợp lý:** xử lý liên tục thay đổi từ cảm biến, đọc và phân loại dòng sự kiện nội bộ, luồng notifications, chuyển đổi bản ghi trong ứng dụng, hoặc streaming dữ liệu với tốc độ tiêu thụ khó dự báo.

“Có nhiều sự kiện” chỉ là dấu hiệu ban đầu. **Giá trị chính của Flow là mô hình hợp đồng nhu cầu** giữa producer và consumer, đặc biệt khi downstream có thể chậm.

## 3. Những trường hợp CHƯA nên xây event pipeline

Đừng ép một quy trình đơn giản thành hệ thống phát/nhận bất đồng bộ nếu:

- Chỉ có vài bước **ngắn, tuần tự, có kết quả ngay**, chẳng hạn kiểm tra input rồi trả giá trị.
- Toàn bộ dữ liệu đã ở trong bộ nhớ và chỉ cần một phép biến đổi batch; `Stream` hoặc vòng lặp thường đơn giản hơn.
- Công việc là CPU-bound thuần và cần tăng tốc chia dữ liệu; cân nhắc thuật toán, `ExecutorService` hoặc parallel stream có kiểm soát thay vì tạo pipeline nhiều buffer.
- Một yêu cầu bắt buộc một giao dịch nguyên tử bao trùm mọi thao tác; tách thành nhiều event bất đồng bộ làm thay đổi ranh giới transaction.
- Cần **lưu bền vững, replay sau restart, retry có đảm bảo, phối hợp nhiều máy hoặc xử lý chính xác một lần**. `Flow` tự nó là giao thức in-process, không tự cung cấp message broker, storage, transaction hay exactly-once.
- Nguồn external *không thể giảm tốc* và nghiệp vụ **không chấp nhận mất dữ liệu**, nhưng chưa có chiến lược buffer/bền vững. Chỉ thêm `request(n)` không làm lượng dữ liệu ngoài hệ thống biến mất.
- Các bên không thể xác định trách nhiệm quản lý lifecycle, cancellation, error và quan sát trạng thái. Độ phức tạp này có thể lớn hơn lợi ích.

Một `BlockingQueue` hữu hạn và một worker có thể đủ cho producer–consumer đơn giản; các lệnh gọi thông thường hoặc `ExecutorService` dễ bảo trì hơn nếu không cần giao thức Publisher–Subscriber. Chọn Flow khi **demand và nhiều stage** là vấn đề thực sự, không phải như một mặc định cho mọi bài toán concurrency.

## 4. Java Flow gồm những thành phần nào?

`Flow` định nghĩa các giao diện tương ứng tinh thần **Reactive Streams**:

| Thành phần | Hợp đồng cốt lõi |
|---|---|
| `Flow.Publisher<T>` | Cho phép một `Subscriber` đăng ký qua `subscribe(subscriber)` và phát phần tử/terminal signals |
| `Flow.Subscriber<T>` | Nhận `onSubscribe`, `onNext`, `onError`, `onComplete` |
| `Flow.Subscription` | Nối Publisher với **một Subscriber**, cho phép `request(long n)` và `cancel()` |
| `Flow.Processor<T,R>` | Vừa là Subscriber nhận `T`, vừa là Publisher phát `R`; thích hợp cho một stage biến đổi |

Luồng tín hiệu cơ bản:

```text
Publisher                          Subscriber
    |                                  |
    | ------- onSubscribe(s) --------> |
    | <------ s.request(2) ----------- |
    | ------- onNext(eventA) --------> |
    | ------- onNext(eventB) --------> |
    |    (không được đẩy thêm nếu       |
    |     chưa có demand mới)          |
    | <------ s.request(1) ----------- |
    | ------- onNext(eventC) --------> |
    | ------- onComplete() ----------> |
    |                                  |
          hoặc onError(error)
```

**`request(n)` nói “cho phép nhận thêm tối đa n sự kiện”**, không phải bắt Publisher phải tạo đủ n phần tử ngay. Demand là số lượng phần tử được phép phát cho subscription đó và được cộng dồn theo quy tắc bão hòa của API. Mỗi `onNext` tiêu thụ một đơn vị demand. `request(Long.MAX_VALUE)` biểu diễn nhu cầu không giới hạn, không có nghĩa hệ thống đã có vô hạn buffer hay vô hạn tốc độ xử lý.

Điều kiện quan trọng:

1. Sau `subscribe`, Subscriber được cấp `Subscription` qua `onSubscribe` trước khi nhận các tín hiệu dữ liệu thông thường.
2. Một subscription chỉ được phát `onNext` khi có demand dương; `request(0)` hoặc giá trị âm là yêu cầu không hợp lệ và phải được phản ánh bằng `onError` theo hợp đồng Flow.
3. Tín hiệu của **cùng một subscription** được gửi theo thứ tự, không gọi chồng nhau đồng thời theo quy tắc Publisher; điều này không đảm bảo nhiều subscribers thấy callback tại cùng thời điểm.
4. `onError` hoặc `onComplete` là tín hiệu kết thúc; sau đó không được gửi thêm `onNext` đến cùng subscription.
5. `cancel()` là yêu cầu ngừng subscription, không phải lời bảo đảm rằng subscriber sẽ nhận thêm `onComplete`. Code upstream cần thực sự giải phóng nguồn tài nguyên.
6. Event `null` không được phép trong các implementations tuân thủ Flow phổ biến; với `SubmissionPublisher`, `submit(null)` ném `NullPointerException`.

`Flow` mô tả hợp đồng trao đổi tín hiệu; **không tự tạo thread, không tự lưu sự kiện, không tự chuyển một phép tính thành non-blocking**. Cách chạy đồng bộ hay bất đồng bộ do implementation và executor quyết định.

## 5. Backpressure khác gì queue, rate limit và load shedding?

**Backpressure** là tín hiệu từ downstream về lượng dữ liệu nó có thể tiếp nhận, truyền ngược để upstream kiểm soát nhịp hoặc chính sách phát. Một buffer có thể hấp thụ burst trong một khoảng ngắn, nhưng buffer **không thể giải quyết chênh lệch tốc độ kéo dài vô hạn**.

Giả sử nguồn tạo 1.000 sự kiện/giây, bộ xử lý chỉ hoàn thành 100 sự kiện/giây:

- Nếu chỉ dùng queue không giới hạn, backlog tăng xấp xỉ 900 sự kiện/giây; cuối cùng có thể hết heap.
- Nếu dùng buffer hữu hạn và nguồn có thể chờ, producer tạm dừng/giảm nhịp, hệ thống ổn định hơn nhưng có thể tăng độ trễ producer.
- Nếu nguồn không thể chờ (ví dụ một thiết bị liên tục gửi), phải có chính sách drop, sampling, lưu bền vững hoặc kiểm soát từ phía nguồn.
- Nếu nghiệp vụ không cho phép mất một sự kiện nào và nguồn vẫn không dừng, **cần một nơi lưu trữ đủ bền vững và có giới hạn vận hành rõ ràng**, không thể giải quyết chỉ bằng RAM và `request(n)`.

**Phân biệt các cơ chế:**

| Cơ chế | Câu hỏi nó giải quyết |
|---|---|
| `request(n)` | Subscriber còn cho phép upstream gửi bao nhiêu phần tử? |
| Bounded queue/buffer | Bao nhiêu phần tử có thể đang đợi trong bộ nhớ? |
| Rate limiting | Được phát bao nhiêu yêu cầu trong một khoảng thời gian? |
| Semaphore/concurrency limit | Có tối đa bao nhiêu thao tác cùng sử dụng tài nguyên? |
| Load shedding/drop | Khi quá tải, phần tử nào được bỏ và có chấp nhận mất không? |
| Durable log/queue | Nếu process restart thì các event đã nhận có thể được phục hồi không? |

Các cơ chế này bổ trợ nhau, không thay thế hoàn toàn nhau. Ví dụ subscriber có thể yêu cầu 20 phần tử nhưng hệ thống chỉ có 5 kết nối ra ngoài; vẫn phải giới hạn số kết nối thực tế.

## 6. Ví dụ runnable: nhu cầu từng phần tử với `SubmissionPublisher`

`SubmissionPublisher<T>` là Publisher chuẩn của JDK: dùng executor để gửi bất đồng bộ đến subscribers hiện có, có **buffer riêng cho mỗi subscriber** và các lựa chọn phát khi buffer đầy. Demo sau tính tổng 5 sự kiện; subscriber chỉ yêu cầu thêm **một phần tử** mỗi khi xử lý xong một phần tử.

Lưu tệp **`FlowDemandDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class FlowDemandDemo {
    static final class CountingSubscriber implements Flow.Subscriber<Integer> {
        private final CountDownLatch done;
        private Flow.Subscription subscription;
        private int sum;

        CountingSubscriber(CountDownLatch done) {
            this.done = done;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(Integer item) {
            sum += item;
            subscription.request(1); // Xử lý xong mới xin thêm một phần tử.
        }

        @Override
        public void onError(Throwable error) {
            System.out.println("error=" + error.getClass().getSimpleName());
            done.countDown();
        }

        @Override
        public void onComplete() {
            System.out.println("sum=" + sum);
            done.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var done = new CountDownLatch(1);

        try (var publisher = new SubmissionPublisher<Integer>(executor, 2)) {
            publisher.subscribe(new CountingSubscriber(done));

            for (int i = 1; i <= 5; i++) {
                publisher.submit(i);
            }
            publisher.close();

            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out");
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch/chạy với **JDK 21**:

```bash
javac FlowDemandDemo.java
java FlowDemandDemo
```

Kết quả khi mọi phần tử xử lý thành công:

```text
sum=15
```

**Giải thích:**

- `publisher.subscribe(...)` đăng ký subscriber, callback `onSubscribe` cấp một `Subscription`.
- `request(1)` cho phép gửi một phần tử. Khi xử lý xong trong `onNext`, subscriber xin thêm một phần tử. Trong ví dụ, `onNext` tuần tự theo từng subscription.
- `SubmissionPublisher<>(executor, 2)` cấu hình buffer tối đa dự kiến cho **mỗi subscriber**; kích thước hiệu lực có thể được làm tròn theo implementation, hãy kiểm tra `getMaxBufferCapacity()` khi cần.
- `submit` có thể chặn thread producer khi buffer của bất kỳ subscriber nào đầy, chờ chỗ trống để phát. Vì vậy đây là **áp lực ngược lên nơi gọi submit**, không phải API luôn non-blocking.
- `close()` kết thúc việc publish và gửi `onComplete` đến subscribers một cách bất đồng bộ sau các phần tử đã phát; `CountDownLatch` giúp main chờ sự kiện kết thúc. Chỉ gọi `close()` rồi thoát ngay không đủ để bảo đảm mọi callback đã hoàn tất.
- Executor là tài nguyên riêng của demo, được `shutdown()` trong `finally`. Với nhiều subscribers chậm, một executor một worker có thể là nút thắt; đây là lựa chọn để ví dụ dễ kiểm chứng, không phải cấu hình hiệu năng được khuyến nghị chung.

Ví dụ không tạo network I/O và không chứng minh mức tăng thông lượng. Nó minh họa **demand và vòng đời tín hiệu**. Trong production, xử lý `onError` phải ghi nhận/đẩy lỗi theo chính sách nghiệp vụ; một callback chỉ in tên lỗi là chưa đủ.

## 7. Ví dụ runnable: dữ liệu có thể chờ trong buffer nhưng chưa được phép giao

Ví dụ tiếp theo tách rõ **đã `submit` vào Publisher** với **đã được giao qua `onNext`**. Subscriber yêu cầu 2 phần tử đầu. Producer phát 3 phần tử; phần tử thứ ba sẽ ở trạng thái đợi cho đến khi Subscriber yêu cầu tiếp.

Lưu thành **`DemandWindowDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class DemandWindowDemo {
    static final class Consumer implements Flow.Subscriber<Integer> {
        private final CountDownLatch firstTwo;
        private final CountDownLatch terminal;
        volatile Flow.Subscription subscription;
        int received;

        Consumer(CountDownLatch firstTwo, CountDownLatch terminal) {
            this.firstTwo = firstTwo;
            this.terminal = terminal;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            s.request(2);
        }

        @Override
        public void onNext(Integer item) {
            System.out.println("item=" + item);
            if (++received == 2) {
                firstTwo.countDown();
            }
        }

        @Override
        public void onError(Throwable failure) {
            System.out.println("error");
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            System.out.println("complete");
            terminal.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var firstTwo = new CountDownLatch(1);
        var terminal = new CountDownLatch(1);
        var consumer = new Consumer(firstTwo, terminal);

        try (var publisher = new SubmissionPublisher<Integer>(executor, 4)) {
            publisher.subscribe(consumer);

            for (int i = 1; i <= 3; i++) {
                publisher.submit(i);
            }

            if (!firstTwo.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Missing first batch");
            }

            System.out.println("request-more");
            consumer.subscription.request(1);
            publisher.close();

            if (!terminal.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Not completed");
            }
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac DemandWindowDemo.java
java DemandWindowDemo
```

Đầu ra:

```text
item=1
item=2
request-more
item=3
complete
```

Ở đây phần tử 3 **đã được `submit`**, nhưng không được gửi qua `onNext` khi demand hai phần tử đã hết. Khi main gọi `request(1)`, publisher mới có quyền giao nốt phần tử đó. `CountDownLatch` bảo đảm phần “request-more” chỉ in sau khi subscriber đã xử lý hai phần tử đầu; không dùng `Thread.sleep` để đoán lịch chạy. `volatile` trên tham chiếu `subscription` thể hiện truy cập giữa callback thread và main thread; đồng thời tín hiệu latch bảo đảm thứ tự ở thời điểm main dùng tham chiếu.

**Điều không được suy diễn:** demand bằng 0 không chứng minh producer đã ngừng tạo dữ liệu, vì `SubmissionPublisher` có thể **đệm** sự kiện đã nộp đến giới hạn. Khi buffer đầy, tùy API gọi và policy, producer mới bị chặn hoặc có khả năng drop. Muốn áp lực ngược đi đến tận nguồn, nguồn đó phải được thiết kế tôn trọng tín hiệu này.

## 8. `SubmissionPublisher` phù hợp và giới hạn đến đâu?

`SubmissionPublisher` rất hữu ích để học hoặc ghép các thành phần nhỏ **trong một JVM**, không phải hạ tầng event-broker sản xuất đầy đủ.

### Hai chính sách khi buffer đầy

- **`submit(item)`:** có thể block **uninterruptibly** đến khi có chỗ trong buffer của mọi subscriber cần nhận; dễ hiểu nhưng nguy cơ giữ thread producer lâu hoặc vô hạn nếu subscriber không tiếp tục request.
- **`offer(item, onDrop)`** hoặc overload có timeout: có thể không gửi được cho một số subscribers khi quá tải. Handler có thể xử lý drop và yêu cầu retry một lần theo hợp đồng API; giá trị trả âm thể hiện các lần drop. Dữ liệu quan trọng không được âm thầm bỏ đi chỉ vì `offer` trả nhanh.

Một publisher có nhiều subscribers có **buffer độc lập** cho từng subscriber. Một consumer chậm có thể khiến `submit` của producer phải chờ, ảnh hưởng tất cả dù các consumer khác nhanh hơn. Việc tạo 100 subscribers với buffer 256 **không** tương đương một buffer toàn cục 256 phần tử; phải lập ngân sách bộ nhớ theo số subscriptions và trạng thái các stages.

`SubmissionPublisher` không replay những phần tử đã phát trước khi subscriber đăng ký như một durable event log; không dùng nó để thay thế lưu trữ lịch sử. Nếu cần khôi phục sau crash, thiết kế storage/broker riêng và adapter điều tiết đầu vào. Nếu event cực quan trọng, cần cơ chế ack/idempotency/retry ở tầng nghiệp vụ và trạng thái bền vững; `onNext` không mặc định là “đã ghi thành công vĩnh viễn”.

### Dấu hiệu thiếu backpressure khi tự viết Processor

Một `Flow.Processor<T,R>` là cầu nối nhận `T` từ upstream và phát `R` downstream. Nó có thể biểu diễn pipeline `Publisher -> Processor -> Subscriber`, nhưng **không có cơ chế chuyển demand tự động nếu bạn tự viết implementation**. Ví dụ processor gọi `upstream.request(Long.MAX_VALUE)`, rồi tự lưu mọi input vào danh sách không giới hạn, sẽ phá vỡ mục tiêu điều tiết dù downstream chỉ gọi `request(1)`.

Processor đúng cần phối hợp **demand downstream, số input đang xử lý, buffer hữu hạn, overflow policy, cancellation, completion và errors**. Với phép lọc bỏ nhiều phần tử, request đầu vào có thể phải cao hơn số output downstream yêu cầu, nhưng vẫn phải có giới hạn và quy tắc rõ ràng. Không suy ra rằng request đầu vào 10 phần tử chắc chắn cho ra 10 phần tử sau filter.

### Vòng đời lỗi và hủy

- `onError(Throwable)` là tín hiệu kết thúc cho subscriber; phải dọn resources và thông báo lỗi đúng phạm vi.
- `onComplete()` chỉ nói Publisher đã phát hết và hoàn tất cho subscription; không có ý nghĩa “mọi tác động ngoài hệ thống đã durable” trừ khi chương trình tự xác định.
- `Subscription.cancel()` yêu cầu ngừng nhận thêm và giải phóng các công việc liên quan; upstream/processor cần hỗ trợ, không mặc định luồng khác tự dừng.
- `SubmissionPublisher.closeExceptionally(error)` dùng để báo kết thúc lỗi đến subscribers hiện có; khác với `close()` thành công.
- Trong production, cần kiểm soát exception phát sinh từ code của `onNext`, tránh callback đốt hết worker, giữ tài nguyên hoặc che lỗi nghiệp vụ. Nếu stage biến đổi lỗi, đưa lỗi theo hợp đồng pipeline hoặc cơ chế giám sát rõ ràng.

## 9. Thiết kế một event pipeline có giới hạn

Ví dụ bài toán: sự kiện cập nhật trạng thái tài liệu phát sinh liên tục, được kiểm tra, làm giàu, gom số lượng và chuyển sang hệ thống ghi kết quả.

```text
Nguồn input
  ├─ giới hạn tốc độ nhận / nguồn có thể chờ?
  ▼
Buffer hữu hạn
  ▼
Validate/Filter
  ▼
Transform/Enrich  ───> Giới hạn số tác vụ I/O đồng thời
  ▼
Aggregate/window  ───> Giới hạn state và thời gian giữ
  ▼
Output
  └─ xác nhận kết quả / xử lý lỗi / hủy
```

Cần trả lời một số câu hỏi **trước khi tạo Publisher/Processor**:

1. **Đặc tính nguồn:** phát có giới hạn, burst, không thể chặn hay hỗ trợ pull? Nếu là hot source, điều gì xảy ra nếu subscriber tụt lại?
2. **Đơn vị sự kiện:** immutable data hay tham chiếu object mutable? Có cần copy để tránh dữ liệu bị thay khi xử lý sau?
3. **Ranh giới xử lý:** stage nào là CPU-bound, stage nào blocking I/O? Số executor/threads và memory budget được cấp riêng ra sao?
4. **Giới hạn bộ đệm:** sức chứa tối đa ở từng ranh giới, không chỉ một buffer tại đầu nguồn. Đầy thì chờ, trả lỗi, drop hay spill ra storage?
5. **Thứ tự:** có cần thứ tự theo key (ví dụ một thiết bị) hay toàn cục? Tăng concurrency có làm sai thứ tự update không?
6. **Tính đúng đắn:** duplicate, retry, timeout hoặc cancel xảy ra thì có xử lý trùng/thiếu sự kiện không? Có idempotent key hay transaction boundary?
7. **Phát tới nhiều subscribers:** một subscriber chậm có được chặn producer và các subscriber khác không? Có cần tách luồng hoặc ưu tiên không?
8. **Kết thúc:** điều kiện onComplete, onError, cancellation và shutdown; ai sở hữu executor và tài nguyên ngoài?
9. **Khả năng quan sát:** đo queue depth/lag, số demand chưa sử dụng (chỉ xem như tín hiệu giám sát), tỉ lệ drop, tốc độ phát/xử lý, latency, errors và GC.

**Một điều kiện quan trọng:** backpressure phải đi qua các stage hoặc chuyển thành một chính sách overflow có chủ đích. Nếu một stage tự sinh vô hạn tasks sang executor khác rồi báo hoàn thành nhận input, toàn bộ luồng có thể lại phình ra trong queue của executor đó.

## 10. Phân biệt việc “phù hợp với event pipeline” và việc “dùng Flow”

Event pipeline là **kiến trúc**; `Flow` là **một tập giao diện Java** để liên kết producer và consumer có demand. Bạn có thể có event pipeline với bounded `BlockingQueue`, actor/message loop hoặc giao diện tự định nghĩa; ngược lại, dùng `Flow` cho một đoạn rất nhỏ không tự làm cả hệ thống trở thành một kiến trúc event-driven.

| Nhu cầu | Lựa chọn khởi đầu nên cân nhắc |
|---|---|
| Một lệnh gọi cần kết quả ngay, logic ngắn | Hàm đồng bộ/`CompletableFuture` khi thực sự cần async |
| Xử lý batch hữu hạn trong bộ nhớ | Vòng lặp hoặc Java `Stream` |
| Producer–consumer một kênh, yêu cầu giới hạn queue rõ | `ArrayBlockingQueue` hoặc `BlockingQueue` hữu hạn |
| Nhiều stage dòng sự kiện, bên nhận cần điều tiết demand | `Flow.Publisher/Subscriber/Processor` |
| Nhiều công việc độc lập với số worker hữu hạn | `ExecutorService` với backlog và timeout kiểm soát |
| Rất nhiều tác vụ blocking I/O độc lập trên Java 21 | Virtual threads với giới hạn kết nối/tài nguyên thực |
| Cần replay, durable delivery, multi-process/multi-host | Storage/broker được thiết kế riêng; có thể adapter sang Flow ở bên trong JVM |

Các cơ chế có thể kết hợp, nhưng càng nhiều tầng concurrency càng khó hiểu thời điểm kết thúc và lỗi. Chọn cấu trúc tối thiểu giải quyết đúng điểm nghẽn đã đo.

## 11. Sai lầm phổ biến

| Sai lầm | Vì sao nguy hiểm | Cách sửa |
|---|---|---|
| Cho rằng dùng `Flow` thì event source tự giảm tốc | Source không biết demand nếu adapter không chuyển tín hiệu | Thiết kế source pull hoặc overflow policy |
| Subscriber gọi `request(Long.MAX_VALUE)` bất kể tải | Mất tín hiệu giới hạn nhu cầu | Request theo batch/công suất xử lý |
| Dùng buffer không giới hạn ở một stage | Có thể OOM khi downstream chậm | Đặt quota rõ từng queue/window |
| Cho rằng `submit` luôn không chặn | Có thể chờ buffer rảnh | Chọn `submit`/`offer` theo yêu cầu mất dữ liệu và timeout |
| Bỏ qua exceptions trong `onNext` | Lỗi khó được quan sát hoặc làm mất subscription | Terminal handling và cleanup rõ ràng |
| Không gửi `onComplete` hoặc `close()` | Subscriber chờ mãi nếu nguồn đã hết | Hoàn tất và chờ terminal signal đúng vòng đời |
| Không xử lý `cancel` | Nguồn/stage vẫn chiếm tài nguyên | Lan cancellation ngược các stages và đóng tài nguyên |
| Gửi vô hạn việc tới pool bên trong `onNext` | Queue executor phình to dù Flow có demand | Limit số tác vụ in-flight và điều tiết upstream |
| Cần replay mà chỉ dùng `SubmissionPublisher` | Dữ liệu có thể mất khi JVM dừng | Durable storage/ack/retry rõ ràng |
| Giả định mọi subscribers xử lý cùng nhịp | Một consumer chậm có thể gây backpressure cho producer | Đo lag từng subscription, chia kênh khi cần |
| Dùng parallelism để sửa nghẽn I/O phía sau | Database/API vẫn có quota cố định | Giới hạn theo resource và tối ưu bottleneck thực |
| Cho rằng backpressure = exactly-once | Demand không bảo đảm durability/idempotency | Thiết kế business acknowledgment và xử lý trùng |

## 12. Phương pháp ra quyết định trong thực tế

Bắt đầu từ **bài toán và tốc độ dữ liệu**, không từ tên thư viện:

1. **Xác định mức độ liên tục:** có dữ liệu streaming hay chỉ là collection hữu hạn?
2. **Đo chênh lệch tốc độ:** producer/consumer events per second, burst peak, average processing latency; nếu tốc độ gần nhau và dữ liệu ít, mô hình đơn giản có thể đủ.
3. **Phân tích backpressure:** có thể chặn upstream không? Nếu không, mất dữ liệu có được phép không, cần buffer lâu bao nhiêu?
4. **Xác định semantic:** ordering, idempotency, retry, cancellation, delivery và persistence.
5. **Chọn mức abstraction:** stream tuần tự, queue hữu hạn, `ExecutorService`, `Flow` hay giao thức external. Chỉ thêm nhiều stage khi có lợi ích về logic hoặc hiệu năng.
6. **Đặt budgets:** buffer capacity, max in-flight tasks, CPU threads/connections, timeout và dung lượng state tổng hợp.
7. **Kiểm thử quá tải trước production:** subscriber nhận chậm, producer burst, lỗi giữa pipeline, cancellation, executor shutdown, một subscriber đứng hẳn, re-subscribe và lỗi logic downstream.
8. **Quan sát:** throughput, end-to-end latency, lag/buffer occupancy, drops, retries, allocation, memory và mức sử dụng executor. Số sự kiện đang chờ phải hữu hạn theo policy.

Một pipeline nên có **hợp đồng xác định khi nhanh và khi quá tải**. Nếu throughput cao trong benchmark bình thường nhưng burst lớn làm backlog tăng không giới hạn, pipeline chưa hoàn chỉnh.

### Tự kiểm tra kiến thức

1. Một dịch vụ chỉ nhận vài lệnh đồng bộ mỗi giây, xử lý 3 phép tính trong RAM: vì sao event pipeline chưa chắc cần thiết?
2. `Subscriber.request(5)` có buộc Publisher phải gửi đủ 5 phần tử ngay không?
3. Subscriber đã `request(2)`, producer `submit` 3 phần tử vào `SubmissionPublisher`. Phần tử 3 có thể tồn tại ở đâu trước khi được request thêm?
4. Nếu upstream tạo 500 events/giây, downstream xử lý 100 events/giây và upstream không thể dừng, tại sao chỉ tăng RAM buffer không giải quyết lâu dài?
5. `Flow` có tự lưu sự kiện để replay sau khi JVM restart không?
6. Vì sao `request(Long.MAX_VALUE)` không nên mặc định dùng cho một consumer có quota I/O hữu hạn?
7. Nếu stage A nhận có demand nhưng nộp toàn bộ input vào một executor có queue vô hạn, liệu backpressure đã được bảo toàn đến stage B?
8. Hai subscribers dùng cùng `SubmissionPublisher` nhưng một bên rất chậm, hành vi `submit` nào cần được tính đến?

**Đáp án định hướng:** (1) overhead và phức tạp vượt lợi ích; (2) không, đó là mức cho phép; (3) buffer của subscription; (4) backlog vẫn tăng, cần slow source/drop/durable queue/công suất xử lý; (5) không; (6) mất giới hạn nhu cầu trong khi tài nguyên vẫn hữu hạn; (7) không; (8) submit có thể bị chặn bởi buffer đầy của subscriber chậm.

### Tài liệu tham khảo chính thức

- [Java SE 21 — `Flow`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html): Publisher–Subscriber interfaces, demand và hướng dẫn cơ bản.
- [Java SE 21 — `Flow.Publisher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Publisher.html): hợp đồng phát, thứ tự tín hiệu, terminal signals.
- [Java SE 21 — `Flow.Subscription`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html): `request`, `cancel`, demand.
- [Java SE 21 — `SubmissionPublisher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html): executor, buffer từng subscription, `submit`, `offer`, `close`.
- [Java SE 21 — `BlockingQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html): lựa chọn nhẹ hơn cho producer–consumer có queue hữu hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-012">SubmissionPublisher.submit() so với offer(): xử lý subscriber chậm và buffer đầy</a>

<details>
<summary>Xem chi tiết</summary>

# SubmissionPublisher.submit() so với offer(): xử lý subscriber chậm và buffer đầy

> Chủ đề: **Flow & Backpressure** trong Java, tập trung vào `java.util.concurrent.Flow` và `SubmissionPublisher<T>` (có từ Java 9). Các ví dụ dùng **JDK 17+** và chỉ phụ thuộc Java SE; không đi vào framework cụ thể.

## 1. Vấn đề: producer nhanh hơn subscriber

`Flow.Publisher<T>` phát phần tử cho các `Flow.Subscriber<T>`. Subscriber nhận `Flow.Subscription` từ `onSubscribe`, sau đó dùng `subscription.request(n)` để thông báo nhu cầu (*demand*). Publisher chỉ được phát `onNext` theo số lượng được yêu cầu; mỗi phần tử đã được chuyển tới `onNext` tiêu thụ một đơn vị demand.

`SubmissionPublisher<T>` là triển khai Publisher có sẵn của JDK. Nó nhận item từ producer và **giao bất đồng bộ** cho các subscriber bằng một `Executor`. Mỗi subscriber có **buffer riêng**, vì vậy nếu một subscriber xử lý chậm hoặc không yêu cầu thêm dữ liệu, buffer của subscriber đó có thể đầy.

```text
producer -- submit/offer(item) --> SubmissionPublisher
                                  |              |
                                  v              v
                             buffer S1      buffer S2
                             [đang đầy]      [còn chỗ]
                                  |              |
                                  v              v
                           subscriber S1  subscriber S2
                           chậm/không xin nhanh/đang xin
```

Bài toán cần quyết định: khi **ít nhất một subscriber không nhận kịp**, producer nên **chờ để giữ dữ liệu** hay **chấp nhận bỏ một item tại subscriber bị quá tải**? Hai API chính hỗ trợ hai lựa chọn đó:

- `submit(item)`: chờ khi thiếu tài nguyên buffer để phát item cho các subscriber hiện tại.
- `offer(item, ...)`: thử phát, có thể drop cho subscriber bị đầy; bản timed offer có thể chờ một khoảng trước khi drop.

**Backpressure ở đây không chỉ là buffer size.** `request(n)` cho biết số lượng callback `onNext` được phép giao theo nhu cầu subscriber. Buffer xử lý chênh lệch tốc độ producer/consumer tạm thời; khi backlog vượt khả năng buffer, producer phải chọn chính sách chờ, bỏ, giới hạn tải hoặc lưu bền vững ở nơi khác.

## 2. So sánh chính xác các overload

| API | Khi buffer của subscriber đầy | Khả năng drop | Khi nào dùng |
|---|---|---|---|
| `int submit(T item)` | **Chờ không ngắt được** (*blocks uninterruptibly*) đến khi có tài nguyên để nhận item | Không drop item chỉ do đầy buffer | Cần giữ dữ liệu trong luồng xử lý, producer được phép chịu backpressure |
| `int offer(T item, BiPredicate onDrop)` | Thử phát ngay, không chờ đầy buffer; chạy drop handler nếu cần | Có; handler có thể thử lại đúng **một lần** khi trả `true` | Không muốn producer bị treo vì subscriber chậm, chấp nhận xử lý mất item |
| `int offer(T item, long timeout, TimeUnit unit, BiPredicate onDrop)` | Chờ buffer rảnh **tối đa theo timeout hoặc tới khi bị interrupt**, sau đó có thể drop | Có; handler có thể thử lại một lần | Muốn một khoảng chờ hữu hạn trước khi áp dụng drop policy |

**Không đồng nghĩa:** gọi `offer` không có nghĩa *chắc chắn* drop, và gọi `submit` không có nghĩa *chắc chắn* block. Khi buffer còn chỗ, cả hai có thể phát thành công và quay lại rất nhanh.

Một subscriber chậm là đủ khiến `submit` của producer bị chờ, dù các subscriber khác vẫn theo kịp. Ngược lại, `offer` có thể phát item cho subscriber nhanh và drop riêng ở subscriber quá tải. Tùy hợp đồng ứng dụng, việc các subscriber thấy tập item khác nhau có thể được chấp nhận hoặc là lỗi nghiêm trọng.

Các phương thức này **đưa item vào đường giao bất đồng bộ**, không có nghĩa `onNext` của mọi subscriber đã thực thi xong khi phương thức trả về.

## 3. Buffer riêng và demand: vì sao subscriber bị đầy?

### 3.1. `request(n)` ảnh hưởng đến luồng giao item

Ví dụ một subscriber:

```java
@Override
public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
}

@Override
public void onNext(String item) {
    process(item);
    subscription.request(1);
}
```

Đây chỉ là **fragment**: giả định class đã có trường `subscription` và phương thức `process`. Nó yêu cầu **từng item**, rồi yêu cầu tiếp khi xử lý xong. Nếu `process` chậm, subscriber cấp lại demand chậm và backlog có thể tăng. Nếu không gọi `request`, `onNext` không được giao, và những item đã được chấp nhận có thể nằm trong buffer cho tới khi đầy.

Nếu subscriber gọi `request(Long.MAX_VALUE)`, đó là demand rất lớn, **không phải** buffer vô hạn. Nếu `onNext` mất 200 ms nhưng producer gửi 1.000 item mỗi giây thì hàng đợi vẫn có thể bị lấp kín.

Không dùng `request(0)` hoặc `request` với số âm: đây là yêu cầu sai giao thức và dẫn tới tín hiệu lỗi theo hợp đồng Flow. Sau khi `cancel()`, subscriber không còn nhận các item mới của subscription đó.

### 3.2. `maxBufferCapacity` là cho **từng subscriber**

```java
ExecutorService delivery = Executors.newFixedThreadPool(4);
SubmissionPublisher<String> publisher =
        new SubmissionPublisher<>(delivery, 16);
```

*Đoạn minh họa trong một chương trình có import `java.util.concurrent.*`; thực tế phải đóng publisher và shutdown executor đúng vòng đời.*

Con số `16` ở constructor là **dung lượng yêu cầu cho mỗi subscriber**, không phải tổng số item trên toàn publisher. Dung lượng thực thi có thể được làm tròn lên lũy thừa 2 hoặc bị chặn bởi giới hạn implementation; gọi `publisher.getMaxBufferCapacity()` để biết dung lượng thực tế.

Nếu có 100 subscriber, có thể có nhiều buffer đang hoạt động đồng thời; cần cân nhắc bộ nhớ, số subscriber và kích thước mỗi item. Buffer giúp hấp thụ burst ngắn, **không chữa được** chênh lệch tốc độ lâu dài. Đặt buffer cực lớn có thể chỉ che một subscriber chậm bằng tăng RAM và latency.

### 3.3. Một mục đang được xử lý và một mục còn trong buffer

Subscriber có thể đang thực hiện `onNext(item1)` khá lâu, trong khi `item2` nằm đợi ở buffer. Cả khi demand chưa hết, tốc độ của executor và thời gian chạy callback vẫn giới hạn khả năng xả buffer. Đừng chỉ đo demand mà bỏ qua **tốc độ hoàn tất `onNext`**.

## 4. `submit(item)`: chờ để giữ item, đánh đổi latency producer

Cú pháp:

```java
int lag = publisher.submit(item);
```

- Item phải **khác `null`**; gửi khi publisher đã đóng gây `IllegalStateException`.
- Khi chưa đủ chỗ trong buffer của bất kỳ subscriber hiện tại nào, `submit` có thể **block uninterruptibly**. `Thread.interrupt()` **không phải** bảo đảm làm lời gọi này kết thúc ngay.
- Giá trị trả về là **ước lượng maximum lag**: số item đã gửi nhưng chưa được consume ở subscriber chậm nhất; có subscriber thì ít nhất 1, không có subscriber thì 0. Không phải số item đã xử lý thành công và không phải số millisecond bị chờ.
- Nếu executor từ chối tác vụ (ví dụ `RejectedExecutionException`), lời gọi có thể ném exception; khi phát sinh lỗi từ executor, không bảo đảm tất cả subscriber đã nhận được item.

**Khi phù hợp:** dữ liệu bắt buộc đi qua từng subscriber trong một pipeline ngắn, producer có thể chờ, đã có giới hạn upstream và cơ chế hủy/shutdown đúng. Việc `submit` bảo toàn giao hàng khi **buffer đầy** không đồng nghĩa bảo đảm bền vững khi JVM crash, subscriber hủy, callback lỗi hoặc publisher bị đóng bất thường.

**Rủi ro:** nếu subscriber không bao giờ gọi `request` tiếp, bị treo trong `onNext` hoặc executor không có thread rảnh, `submit` có thể chờ rất lâu hoặc vô thời hạn. Không gọi `submit` trên luồng phục vụ UI/đường xử lý yêu cầu có deadline nghiêm ngặt nếu không thể kiểm soát backpressure. Đặc biệt tránh gọi `submit` lặp lại từ chính một callback `onNext` khi nó đang chiếm worker mà publisher cần để xả buffer: có thể tạo vòng chờ/deadlock.

`submit` không cho đối số timeout. Nếu cần chính sách latency hữu hạn, xem `offer` có timeout; nếu cần xử lý hủy mạnh hơn, xây dựng cơ chế dừng/cancel trên toàn pipeline thay vì chỉ kỳ vọng interrupt giải phóng producer.

## 5. `offer(item, onDrop)`: không chờ, có khả năng mất item

Cú pháp:

```java
int status = publisher.offer(event, (subscriber, rejectedEvent) -> {
    // Ghi metric / thông báo sự kiện quá tải.
    return false;  // Không thử lại: drop item ở subscriber bị đầy.
});
```

*Đoạn code cần biến `publisher`, `event` và kiểu generic tương thích; không là chương trình độc lập.*

Khi không thể phát cho subscriber do giới hạn buffer, `onDrop` nhận **subscriber cụ thể** và item bị từ chối. Giá trị boolean:

- `false`: không thử lại, item bị drop cho subscriber này.
- `true`: yêu cầu thử offer lại **một lần duy nhất** ở subscriber này; **không** đảm bảo lần thử lại thành công. Nếu vẫn đầy, item có thể bị drop.

Nếu handler là `null`, lời gọi vẫn có thể drop và không gọi handler. Handler nên **rất ngắn, không block, không có thao tác gọi ngược phức tạp vào publisher**: tài liệu API nêu rằng các lời gọi khác vào publisher từ luồng khác sẽ bị chặn trong khi handler chạy. Handler chậm làm lan truyền sự chậm trễ tới cả các producer khác.

Một chiến lược đúng có thể là đếm drop, ghi log có giới hạn, chọn hủy subscriber lỗi hoặc gửi thông báo qua kênh phụ. Không nên giả định `onDrop` là nơi an toàn để retry vô hạn, ngủ dài hoặc chờ cùng buffer mà nó đang xử lý.

## 6. `offer(item, timeout, unit, onDrop)`: chờ có hạn

Ví dụ:

```java
int status = publisher.offer(
        event,
        100, TimeUnit.MILLISECONDS,
        (subscriber, dropped) -> {
            droppedCounter.increment();
            return false;
        }
);
```

*Fragment này cần các biến `publisher`, `event`, `droppedCounter` và import `java.util.concurrent.TimeUnit`.*

Phiên bản timed offer cho phép publisher đợi tài nguyên của subscriber trong khoảng timeout hoặc tới khi thread gọi bị interrupt. Nếu hết thời gian/được ngắt mà subscriber vẫn không nhận được, sẽ áp dụng drop handler; handler vẫn chỉ có quyền yêu cầu **một** lần retry. API nói rõ handler có thể phân biệt interrupt với timeout bằng cờ interrupt của thread hiện tại.

**Lưu ý:** timeout dùng để giới hạn việc đợi tài nguyên trong chính lời gọi offer, không bảo đảm callback `onNext` kết thúc trước deadline; logic nghiệp vụ cần timeout cho processing riêng. Tổng thời gian thực còn chịu scheduler và thời gian handler; không dùng timeout này thay thế deadline end-to-end.

Các chính sách điển hình:

| Mức chịu mất dữ liệu | Chiến lược |
|---|---|
| Không thể bỏ item | Không dùng drop silently; cân nhắc `submit` có backpressure được kiểm soát, hoặc hệ thống lưu bền vững/ack ở tầng khác |
| Có thể bỏ cập nhật trung gian (ví dụ tiến độ UI) | `offer(..., onDrop -> false)`; ghi metric drop |
| Cho phép trễ nhỏ nhưng không vô hạn | Timed `offer` và xử lý drop rõ ràng |
| Chỉ cần trạng thái mới nhất | Thiết kế cơ chế coalescing/latest-state ở ứng dụng; `offer` tự nó **không thay thế item cũ bằng item mới** |

## 7. Đọc `int` trả về cho đúng

Cả `submit` và `offer` đều trả `int`, nhưng **ý nghĩa không giống nhau**.

### `submit`

- Trả **ước lượng lag lớn nhất** của các subscriber tại thời điểm đo.
- Không có subscriber: `0`.
- Có subscriber: giá trị ít nhất `1`, gồm item vừa gửi.
- Không phản ánh trực tiếp độ dài queue chính xác hay số item thật sự đã gọi xong `onNext`.

### `offer` (cả hai overload)

- `status >= 0`: không ghi nhận drop; giá trị là **ước lượng maximum lag** (không có subscriber thì `0`).
- `status < 0`: trị tuyệt đối biểu diễn **số lần không phát được item cho subscriber** (drop/failed attempts theo hợp đồng API), không phải thời gian chờ, không phải subscriber ID và không phải backlog âm.
- Khi handler trả `true` để retry, **vẫn phải xem status cuối**, không được kết luận retry luôn thành công.

```java
int status = publisher.offer(item, (subscriber, rejected) -> false);
if (status < 0) {
    int drops = -status;
    // Ghi nhận số lượt drop; xử lý theo yêu cầu nghiệp vụ.
} else {
    int estimatedMaxLag = status;
    // Chỉ dùng để theo dõi, không làm tín hiệu đồng bộ chính xác.
}
```

*Đây là fragment, không thể biên dịch độc lập nếu chưa khai báo `publisher` và `item`.*

Các API quan sát `estimateMaximumLag()` và `estimateMinimumDemand()` cũng trả **ước lượng**; chúng có thể lỗi thời khi nhiều producer/consumer chạy đồng thời. Dùng để **monitoring/tuning**, không dùng làm giao thức khóa kiểu “nếu lag nhỏ hơn X thì sẽ chắc chắn submit không block”.

## 8. Ví dụ Java 17+ chạy được: chủ động làm đầy buffer

Ví dụ sau làm subscriber chậm bằng `CountDownLatch` để bảo đảm item thứ nhất **đang kẹt trong `onNext`**. Buffer cấu hình 1 chỗ; item thứ hai nằm đợi. Vì thế item thứ ba qua `offer` bị drop, còn item thứ tư gửi bằng `submit` phải chờ tới khi subscriber được mở khóa. Code sử dụng hai executors riêng để tránh tự khóa chính worker giao hàng.

Tạo tệp **`SubmissionPublisherPressureDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class SubmissionPublisherPressureDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService delivery = Executors.newSingleThreadExecutor();
        ExecutorService producer = Executors.newSingleThreadExecutor();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch inFirstOnNext = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        SubmissionPublisher<Integer> publisher =
                new SubmissionPublisher<>(delivery, 1);

        Flow.Subscriber<Integer> slow = new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription = s;
                s.request(Long.MAX_VALUE);
                subscribed.countDown();
            }

            @Override
            public void onNext(Integer value) {
                if (value == 1) {
                    inFirstOnNext.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public void onError(Throwable error) {
                System.err.println("Subscriber failed: " + error);
            }

            @Override
            public void onComplete() {
                // Kết thúc bình thường.
            }
        };

        try {
            publisher.subscribe(slow);
            subscribed.await();

            publisher.submit(1);
            inFirstOnNext.await();  // Đảm bảo onNext(1) đang đợi release.

            publisher.submit(2);    // Một slot buffer đang được dùng.
            int offered = publisher.offer(3, (s, item) -> false);
            System.out.println("offerDropped=" + (offered < 0));

            Future<Integer> pending =
                    producer.submit(() -> publisher.submit(4));
            // Dù tác vụ producer đã bắt đầu hay đang đợi đến lượt,
            // nó không thể hoàn tất submit(4) khi slot còn đầy.
            System.out.println("submitFinishedWhileFull=" + pending.isDone());

            release.countDown();    // Giải phóng callback để xả buffer.
            pending.get(5, TimeUnit.SECONDS);
            System.out.println("submitFinishedAfterRelease=true");
        } finally {
            release.countDown();
            publisher.close();
            producer.shutdown();
            delivery.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SubmissionPublisherPressureDemo.java
java SubmissionPublisherPressureDemo
```

Với cấu hình `maxBufferCapacity=1` và các mốc `CountDownLatch` như chương trình, stdout dự kiến khi chạy bình thường:

```text
offerDropped=true
submitFinishedWhileFull=false
submitFinishedAfterRelease=true
```

Giải thích:

1. Subscriber gọi `request(Long.MAX_VALUE)`, nhưng `onNext(1)` cố tình chờ. Cấp demand vô hạn **không** làm consumer xử lý nhanh.
2. Sau khi đã biết item 1 đang ở trong callback, item 2 có thể chờ ở buffer duy nhất; `offer(3, handler -> false)` phát hiện không còn chỗ và drop.
3. `submit(4)` chạy ở producer thread khác. Khi subscriber còn bị giữ bởi latch, việc phát item 4 không thể hoàn tất, nên producer bị backpressure. Sau khi `release.countDown()`, worker hoàn thành `onNext(1)`, tiếp tục xả buffer, và `submit(4)` có thể trở về.
4. `Future.get(5, SECONDS)` là **giới hạn đợi của chương trình demo**, không biến lời gọi `submit` bên trong thành interruptible hay timed submit. Nếu môi trường bị nghẽn bất thường, `get` có thể ném `TimeoutException` thay vì in dòng cuối.
5. `finally` giải phóng latch và đóng publisher/executors; tránh để một thread demo bị giữ vì callback chờ vô hạn.

Trong thực tế, số worker, lịch chạy và giới hạn tài nguyên làm performance khác nhau. Đừng dùng microdemo này như benchmark thông lượng. Nếu sửa mã (ví dụ bỏ latch), **không** còn bảo đảm mã trả về hoặc trình tự kết quả tương tự.

## 9. Khi có nhiều subscriber: một bên chậm kéo theo điều gì?

Giả sử có hai subscriber `fast` và `slow`, mỗi subscriber có buffer riêng:

```text
       item X
         |
  SubmissionPublisher
       /       \
   fast          slow
   buffer 0/8    buffer 8/8
   nhận X        không còn chỗ
```

- Với `submit(X)`, producer có thể bị chặn vì `slow`. `fast` có thể đã được giao item nhưng lời gọi producer vẫn đang chờ đường còn lại.
- Với `offer(X, handler -> false)`, `fast` có thể nhận X còn `slow` bị drop X. `onDrop` cho biết subscriber cụ thể bị drop để ghi metric/điều chỉnh chính sách.
- Nếu drop là không chấp nhận được, ứng dụng cần định nghĩa việc một subscriber chậm có được quyền làm chậm tất cả subscriber hay phải được tách khỏi pipeline. Một số hệ thống chọn cô lập một subscriber bằng queue/publisher riêng hoặc hủy subscription chậm, nhưng cần quyết định rõ semantics dữ liệu.
- Nếu subscriber mới subscribe sau khi X đã được publish, `SubmissionPublisher` không tự phát lại lịch sử X; nó không phải message broker có replay/durable log.

Không chạy mọi subscriber và producer trên cùng một executor có quá ít thread khi callback có thể block và producer cũng cần chờ, vì rất dễ tạo **thread starvation**. Chọn executor dựa trên workload, số subscriber và hành vi callback; Java API có tùy chọn mặc định dựa trên `ForkJoinPool.commonPool()`, nhưng không có nghĩa mọi workload đều phù hợp với pool mặc định.

## 10. Chọn backpressure policy theo yêu cầu nghiệp vụ

| Workload | Gợi ý | Lưu ý |
|---|---|---|
| Sự kiện phải đủ, không được mất | `submit` trong pipeline có giới hạn upstream hoặc cơ chế lưu/ack bền vững | Nếu subscriber ngừng request, producer có thể chờ vô hạn |
| Telemetry/giá trị có thể lấy lại | `offer` và đếm drop | Chấp nhận mất điểm dữ liệu, không giả vờ delivery guaranteed |
| Cập nhật giá/làm mới UI, chỉ quan tâm bản mới | Thiết kế coalescing/latest-value có chủ đích | `offer` không tự bỏ bản cũ để ưu tiên bản mới |
| Producer có deadline chặt | Timed `offer`, timeout và chính sách fallback | Timeout không khống chế thời gian `onNext` |
| Nhiều subscriber với tốc độ lệch lớn | Tách nhóm/cô lập subscriber, đo per-subscriber lag/drop | Một slow subscriber có thể kìm `submit` của tất cả |
| Mọi item đều cần retry đến khi có ack | Thiết kế queue/durable log + ack/retry ở cấp ứng dụng | `onDrop -> true` chỉ retry một lần, không đảm bảo delivery |

Mục tiêu là **kiểm soát tải**. Dù chọn `submit` hay `offer`, hãy đo tốc độ sản sinh, tốc độ tiêu thụ, tần suất drop, độ trễ `submit`, lag của subscriber, memory và trạng thái thread pool. Không tăng buffer vô hạn để né việc thiết kế flow control.

## 11. Tình huống lỗi phổ biến

### Lỗi A: quên `request(n)`

Subscriber đăng ký thành công nhưng không request, không nhận `onNext`. Producer vẫn gửi nhanh, buffer dần đầy; `submit` có thể treo. **Sửa:** luôn lưu `Subscription` và gọi `request` phù hợp, cấp lại demand theo mức độ xử lý xong. Nếu subscriber cố ý ngừng nhận, cần có cơ chế cancel/timeout/close pipeline ở tầng hệ thống.

### Lỗi B: `request(Long.MAX_VALUE)` rồi tin rằng hết backpressure

Demand lớn chỉ mở quyền giao, không giúp `onNext` chạy nhanh hơn. Nếu callback chậm, buffer vẫn đầy. **Sửa:** giảm tải, mở rộng/đổi cách xử lý callback phù hợp, hoặc chọn drop/backpressure policy rõ ràng.

### Lỗi C: dùng `submit` trong callback phụ thuộc chính publisher

Nếu callback giữ delivery worker mà producer chờ buffer của chính callback, có thể deadlock/starvation. **Sửa:** tránh vòng gọi đồng bộ, tách executor và đường chờ, hoặc đẩy tác vụ sang pipeline không phụ thuộc callback hiện tại.

### Lỗi D: tưởng `onDrop -> true` bảo đảm retry tới thành công

Handler chỉ yêu cầu **một** lần thử lại, vẫn có thể bị drop. **Sửa:** kiểm tra mã trả về, ghi metric, xây cơ chế retry/ack riêng nếu nghiệp vụ yêu cầu.

### Lỗi E: log/block lâu trong `onDrop`

Các producer khác có thể bị chặn trong lúc handler chạy. **Sửa:** chỉ ghi metric/counter nhẹ, tránh network I/O hoặc đợi futures trong handler; ghi log có giới hạn, không spam theo từng item với tốc độ rất cao.

### Lỗi F: dùng `status < 0` như số milliseconds

Đây là chỉ dấu drop; `status >= 0` là estimated max lag. **Sửa:** đo latency bằng clock/metrics riêng, không suy ra từ status.

### Lỗi G: dùng `getMaxBufferCapacity()` như giới hạn tổng RAM

Dung lượng theo subscriber; item có thể lớn và số subscriber biến động. **Sửa:** tính tổng footprint, memory pressure và backlogs. Theo dõi đối tượng được giữ trong buffer cho tới khi tiêu thụ/hủy.

### Lỗi H: dùng `offer` để truyền sự kiện không được phép mất

Offer có thể bỏ item ở subscriber bị đầy; không bảo đảm giao đều cho tất cả. **Sửa:** dùng chính sách không mất kèm downstream limits hoặc lưu bền vững/ack/replay ở cấp phù hợp.

### Lỗi I: quên đóng publisher và executor

`close()` đánh dấu hoàn tất, gửi tín hiệu `onComplete` cho các subscriber hiện tại khi các điều kiện giao tín hiệu được đáp ứng; gửi item mới sau đó bị từ chối. Nếu subscriber hoặc executor bị kẹt, việc giao tín hiệu hoàn tất cũng có thể chậm. **Sửa:** quản lý vòng đời executor, `Subscription.cancel()` và chính sách shutdown rõ ràng. Không gọi `closeExceptionally` nếu thực ra kết thúc bình thường; khi pipeline lỗi nghiêm trọng có thể phát `onError` bằng `closeExceptionally(Throwable)`.

### Lỗi J: dùng metric ước lượng làm điều kiện đồng bộ

`estimateMaximumLag()` và `estimateMinimumDemand()` là thông tin quan sát, có thể lệch khi nhiều thread thao tác. **Sửa:** dùng `Semaphore`/queue có giới hạn hoặc cơ chế điều phối khác nếu cần admission control chính xác.

## 12. Checklist trước khi đưa SubmissionPublisher vào ứng dụng

1. **Xác định yêu cầu dữ liệu**: mỗi item có bắt buộc tới mọi subscriber không, có thể drop hay cần ack/replay?
2. **Định nghĩa demand**: subscriber gọi `request` khi nào, xử lý xong/đang chờ/đã cancel thế nào?
3. **Chọn policy**: `submit` (chờ) hay `offer` (có thể drop), hoặc timed `offer` (chờ hạn chế). Ghi rõ timeout và hành vi `onDrop`.
4. **Đặt giới hạn tài nguyên**: dung lượng buffer theo subscriber, số subscriber, mức gọi đồng thời, executor capacity và tổng memory footprint.
5. **Kiểm tra tương tác khi quá tải**: subscriber không request, `onNext` block, executor bão hòa, interrupt và publisher close giữa lúc producer gửi.
6. **Theo dõi dữ liệu**: đo drops, lag estimates, latency của producer và `onNext`, subscriber count và executor saturation.
7. **Test có kiểm soát**: dùng latches/điều kiện rõ ràng để tái hiện buffer đầy; tránh kết luận từ `Thread.sleep` ngẫu nhiên trong test.
8. **Bảo vệ đúng nghĩa**: nếu cần giao hàng bền vững hoặc retry đến thành công, bổ sung queue/durable log/ack; `SubmissionPublisher` tự thân không làm thay việc đó.

## Tài liệu chính thức

- [Oracle Java 21 — SubmissionPublisher API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html): mô tả chính xác `submit`, hai overload `offer`, `onDrop` và mã trả về.
- [Oracle Java 21 — Flow API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html): quan hệ Publisher–Subscriber–Subscription, demand và tín hiệu.
- [Oracle Java 21 — Flow.Subscription](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html): `request` và `cancel`.
- [Oracle Java 21 — Flow.Subscriber](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html): thứ tự gọi `onSubscribe`, `onNext`, `onError` và `onComplete`.

**Điểm cốt lõi:** `submit()` chuyển sức ép từ buffer đầy sang **thời gian chờ của producer**; `offer()` chuyển nó thành **khả năng drop theo subscriber**. `offer(timeout, ...)` cho phép chờ có hạn trước khi áp dụng drop policy. Không phương thức nào tự giải quyết subscriber lỗi, executor thiếu tài nguyên hoặc yêu cầu bảo đảm giao hàng bền vững.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="flow-and-backpressure-013">Đóng SubmissionPublisher, tín hiệu kết thúc và vòng đời executor của publisher</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng SubmissionPublisher, tín hiệu kết thúc và vòng đời executor của publisher

## 1. Ba vòng đời khác nhau dễ bị nhầm

`SubmissionPublisher<T>` trong `java.util.concurrent` là một implementation của `Flow.Publisher<T>`. Nó phát các phần tử không `null` theo cách **bất đồng bộ** tới những subscriber hiện tại, sử dụng `Executor` để thực hiện các callback. Khi nguồn không còn dữ liệu hoặc thất bại, publisher cần kết thúc subscription đúng cách.

Cần phân biệt **ba sự kiện**:

1. **Publisher ngừng nhận dữ liệu:** `publisher.close()` hoặc `publisher.closeExceptionally(error)` chuyển publisher sang trạng thái đóng, không nhận những lần `submit`/`offer` mới.
2. **Subscriber nhận tín hiệu kết thúc:** callback `onComplete()` hoặc `onError(Throwable)` có thể được gửi qua executor **sau khi lệnh đóng trả về**. Trạng thái “publisher đã đóng” chưa chứng minh “mọi subscriber đã chạy xong”.
3. **Executor ngừng hoạt động:** executor được đưa vào constructor có vòng đời riêng. Đóng `SubmissionPublisher` **không đồng nghĩa gọi `ExecutorService.shutdown()`**. Chủ sở hữu executor phải tự quyết định khi nào được shutdown.

Nhầm ba sự kiện này có thể làm chương trình kết thúc trước khi các callback chạy, shutdown executor quá sớm dẫn tới tác vụ bị từ chối, hoặc để thread pool riêng chạy mãi dù publisher đã đóng.

Bài học tập trung vào **Java concurrency nói chung**: `Flow`, `SubmissionPublisher`, `ExecutorService` và cách dọn tài nguyên. Đây là kiến thức JDK độc lập với Spring.

## 2. Nhắc lại hợp đồng Flow trước khi nói về kết thúc

Bốn interface cốt lõi:

| Interface | Vai trò |
| --- | --- |
| `Flow.Publisher<T>` | Nguồn cho phép đăng ký `Subscriber` và phát tín hiệu dữ liệu. |
| `Flow.Subscriber<T>` | Nhận `onSubscribe`, `onNext`, `onError` và `onComplete`. |
| `Flow.Subscription` | Liên kết riêng của từng subscriber; `request(n)` cấp demand, `cancel()` yêu cầu hủy. |
| `Flow.Processor<T,R>` | Vừa là subscriber upstream vừa là publisher downstream. |

Vòng đời logic của **một subscription**:

```text
publisher.subscribe(subscriber)
        |
        v
  onSubscribe(subscription)
        |
        +-- subscriber.request(n)  -> mở hạn mức nhận item
        |
        +-- onNext(item)           -> 0..n lần theo demand
        |
        +-- ... có thể request tiếp ...
        |
        +-- onComplete()           -> kết thúc bình thường
             HOẶC
            onError(error)         -> kết thúc bất thường
```

`onComplete` và `onError` là **terminal signals**: một subscription không được nhận thêm `onNext` sau khi đã kết thúc. Hai tín hiệu terminal là những kết quả thay thế nhau cho cùng một subscription, không phải hai bước liên tiếp.

`cancel()` là yêu cầu ngừng subscription theo kiểu *best effort*, **không phải tín hiệu `onComplete`**. Một subscriber chủ động hủy không được đảm bảo sẽ nhận thêm `onComplete`/`onError`. Vì vậy, chương trình muốn chờ sự kiện kết thúc phải hiểu có thể có nhánh hủy không có terminal callback.

## 3. close(): kết thúc bình thường

`publisher.close()` được dùng khi publisher **đã sản xuất xong toàn bộ dữ liệu hợp lệ**.

Theo hợp đồng `SubmissionPublisher`:

- Nếu chưa đóng, nó bắt đầu gửi `onComplete` tới các subscriber hiện tại.
- Không nhận các lần publish mới; `isClosed()` trả `true`.
- Lệnh gọi lặp lại được xử lý theo semantics “nếu chưa đóng” (idempotent về trạng thái đóng).
- **Khi `close()` trả về, không có bảo đảm rằng tất cả subscriber đã xử lý xong hoặc đã nhận callback `onComplete`.**
- Các item đã submit được xử lý theo thứ tự cho từng subscriber hiện tại, nếu không có drop, cancellation hoặc exception. Subscriber vẫn cần cấp demand đủ để tiêu thụ item còn trong buffer.

Thứ tự ý tưởng:

```text
Producer thread                  Delivery executor / subscriber
    |                                          |
    | submit(A)                                |
    | submit(B)                                |
    | close() --------------------------------> | onNext(A)
    | close() trả về                           | onNext(B)
    |                                          | onComplete()
    |  (phải chờ terminal riêng nếu cần)       |
    +------------------------------------------+
```

Đường thời gian minh họa **không khẳng định** callback luôn bắt đầu sau `close`: chúng có thể chạy trước, trong hoặc sau lời gọi đóng. Điều chắc chắn là chỉ chờ `close` trả về thì chưa chứng minh subscriber hoàn tất.

Khi gọi `submit(item)` sau khi publisher đóng, `IllegalStateException` được ném. Dù vậy, không dùng `isClosed()` rồi `submit()` như một phép kiểm tra và cập nhật nguyên tử giữa các producer: thread khác có thể đóng publisher sau bước kiểm tra. Cần thiết kế điều phối producer hoặc xử lý race một cách có chủ đích.

## 4. closeExceptionally(error): kết thúc do lỗi

`publisher.closeExceptionally(error)` dùng khi nguồn **không thể tiếp tục hoàn tất bình thường**. Nếu publisher chưa đóng, phương thức gửi `onError(error)` tới các subscriber hiện tại và ngừng nhận item mới. Các subscriber đăng ký **sau khi publisher đã đóng lỗi** cũng nhận `onSubscribe` rồi tín hiệu lỗi tương ứng.

Điểm cần nhớ:

- `error` phải không `null`; truyền `null` ném `NullPointerException`.
- `getClosedException()` trả lỗi đã dùng để đóng bất thường; trả `null` nếu chưa đóng hoặc đóng bình thường.
- Lệnh `closeExceptionally` trả về **không chờ** mọi `onError` callback hoàn tất.
- Không dùng `closeExceptionally` chỉ để diễn tả việc người dùng hết quan tâm dữ liệu; đó thường là nhánh `Subscription.cancel()`.
- Nếu publisher đã được đóng trước đó, một lần đóng khác không biến đổi lại kết quả terminal của những subscription đã kết thúc. Hãy quyết định kết quả kết thúc tại một nơi rõ ràng.

Một số lỗi phát sinh riêng trong **callback subscriber** không đồng nghĩa publisher phải tự động phát `onError` tới toàn bộ subscriber còn lại. Ví dụ, theo `SubmissionPublisher`, khi phương thức của một subscriber ném exception, subscription đó có thể bị hủy. Constructor có callback xử lý exception của `onNext`; nó không tự biến sự cố một subscriber thành lỗi toàn cục cho tất cả subscribers. Chính sách dừng toàn pipeline cần được thiết kế riêng.

## 5. Ví dụ Java 17+: đóng bình thường và chờ subscriber thực sự hoàn tất

`SubmissionPublisher.consume(Consumer)` tạo một subscriber tự yêu cầu và xử lý dữ liệu qua `Consumer`, đồng thời trả về `CompletableFuture<Void>` để quan sát khi subscription kết thúc hoặc thất bại. Đây là cách ngắn gọn để phân biệt `close` với **đợi terminal**.

Lưu tệp `PublisherNormalCloseDemo.java`:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class PublisherNormalCloseDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService delivery = Executors.newSingleThreadExecutor();
        SubmissionPublisher<String> publisher =
                new SubmissionPublisher<>(delivery, 8);

        try {
            CompletableFuture<Void> completed =
                    publisher.consume(item -> System.out.println("onNext: " + item));

            publisher.submit("A");
            publisher.submit("B");
            publisher.close();

            // close() chỉ đánh dấu kết thúc và phát tín hiệu bất đồng bộ.
            // get() chờ cho consumer nhận terminal signal.
            completed.get(5, TimeUnit.SECONDS);

            System.out.println("subscriber completed");
            System.out.println("publisher closed: " + publisher.isClosed());
            System.out.println("executor closed by publisher: " + delivery.isShutdown());
        } finally {
            publisher.close();      // Có thể gọi lại an toàn nếu đã đóng.
            delivery.shutdown();    // Executor thuộc quyền sở hữu của main.
            if (!delivery.awaitTermination(5, TimeUnit.SECONDS)) {
                delivery.shutdownNow();
            }
        }

        System.out.println("executor shutdown: " + delivery.isShutdown());
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 PublisherNormalCloseDemo.java
java PublisherNormalCloseDemo
```

Kết quả khi thực thi thành công:

```text
onNext: A
onNext: B
subscriber completed
publisher closed: true
executor closed by publisher: false
executor shutdown: true
```

Vì `completed.get(...)` chờ xong trước khi main in trạng thái, thứ tự output này không phụ thuộc việc executor khởi động callback nhanh hay chậm. Hai `onNext` có thứ tự A rồi B trong cùng subscription; `CompletableFuture` được hoàn tất khi subscriber nhận `onComplete`.

Lệnh `delivery.isShutdown()` vẫn trả `false` trước khi main tự `shutdown()`, chứng minh việc đóng publisher **không sở hữu và không đóng executor do caller cung cấp**. `shutdown()` cấm nhận task mới và cho phép những task đã gửi tiếp tục; `awaitTermination` đợi executor hoàn tất, còn `shutdownNow()` chỉ là nỗ lực dừng khẩn khi quá thời hạn.

### Vì sao không chỉ viết try-with-resources rồi kết thúc main?

`SubmissionPublisher` triển khai `AutoCloseable` nên có thể viết:

```java
try (SubmissionPublisher<String> publisher =
             new SubmissionPublisher<>()) {
    publisher.submit("item");
}
```

Đây là **mã minh họa**, không phải chương trình độc lập. `try-with-resources` tự gọi `close`, nhưng **không chờ subscriber nhận xong `onComplete`** và không cho phép suy ra rằng mọi callback đã chạy xong khi ra khỏi block. Nếu kết quả subscriber quan trọng, cần một completion signal/future hoặc latch rõ ràng.

## 6. Ví dụ Java 17+: closeExceptionally và onError

Ví dụ sau dùng `CountDownLatch` để chờ callback lỗi, tránh dựa vào `Thread.sleep()`. Không submit item trước khi đóng lỗi, vì thứ tự giữa dữ liệu pending và quyết định đóng bất thường là một vấn đề riêng của quy tắc hủy/terminal; ở đây ta cần quan sát thuần túy nhánh `onError`.

Lưu tệp `PublisherExceptionalCloseDemo.java`:

```java
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PublisherExceptionalCloseDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService delivery = Executors.newSingleThreadExecutor();
        SubmissionPublisher<String> publisher =
                new SubmissionPublisher<>(delivery, 8);

        CountDownLatch terminal = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>();

        try {
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String item) {
                    System.out.println("unexpected item: " + item);
                }

                @Override
                public void onError(Throwable error) {
                    outcome.set("onError: " + error.getClass().getSimpleName()
                            + ": " + error.getMessage());
                    terminal.countDown();
                }

                @Override
                public void onComplete() {
                    outcome.set("onComplete");
                    terminal.countDown();
                }
            });

            publisher.closeExceptionally(new IOException("source failed"));

            if (!terminal.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Terminal signal timed out");
            }

            System.out.println(outcome.get());
            System.out.println("publisher closed: " + publisher.isClosed());
            System.out.println("closed cause: "
                    + publisher.getClosedException().getMessage());
        } finally {
            publisher.close();
            delivery.shutdown();
            if (!delivery.awaitTermination(5, TimeUnit.SECONDS)) {
                delivery.shutdownNow();
            }
        }
    }
}
```

```bash
javac -encoding UTF-8 PublisherExceptionalCloseDemo.java
java PublisherExceptionalCloseDemo
```

Kết quả:

```text
onError: IOException: source failed
publisher closed: true
closed cause: source failed
```

`onSubscribe` đến trước terminal callback, dù cả hai được thực thi bất đồng bộ. Trong ví dụ chỉ có lỗi nguồn, nên subscriber đi vào `onError` thay vì `onComplete`. `CountDownLatch` truyền điểm hoàn tất sang main thread; `AtomicReference` truyền kết quả giữa threads một cách an toàn.

Trong ứng dụng thực tế, `onError` nên ghi nhận và phát tín hiệu failure ra nơi gọi phù hợp, đồng thời giải phóng tài nguyên do subscriber sở hữu. Chỉ in thông báo lỗi không đảm bảo caller xử lý lỗi đúng.

## 7. Đăng ký subscriber sau khi publisher đã đóng

Một subscriber có thể `subscribe(...)` **sau** khi publisher đã đóng. `SubmissionPublisher` xử lý theo trạng thái kết thúc:

- **Đóng bình thường:** subscriber mới vẫn được gọi `onSubscribe` rồi `onComplete`.
- **Đóng bất thường:** subscriber mới được gọi `onSubscribe` rồi `onError` với exception đã ghi.
- Không có cơ chế phát lại toàn bộ item đã được submit trước khi đóng. `SubmissionPublisher` là cơ chế phát cho subscriber hiện tại, không phải event log lịch sử.
- Các callback vẫn đi qua executor của publisher; vì vậy việc muốn có subscriber “late” cần tính đến **executor còn khả năng thực thi task**.

Điều này giải thích vì sao không nên shutdown executor ngay khi publisher đóng nếu còn chủ động cho phép đăng ký/quan sát tín hiệu kết thúc sau đó. Nếu vòng đời chương trình cho phép late subscribers, phải xác định chính sách và thời hạn cho việc này. Một mô hình quản lý hữu ích là **ngừng tạo subscription mới trước**, cho tất cả subscriber hiện hữu đi tới terminal, rồi mới shutdown executor của pipeline.

Không sử dụng `hasSubscribers()` hay `getNumberOfSubscribers()` để tự xác nhận tất cả callback hoàn tất. Các phương thức này phục vụ quan sát trạng thái, không phải cơ chế join tất cả tín hiệu terminal.

## 8. Ai chịu trách nhiệm đóng executor?

`SubmissionPublisher` dùng một `Executor` để gọi các phương thức của subscriber. Có hai cấu hình chính:

### 8.1. Publisher mặc định

```java
SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
```

Thông thường JDK dùng `ForkJoinPool.commonPool()`, hoặc tạo thread cho tác vụ trong cấu hình không hỗ trợ mức song song tối thiểu. **Không được shutdown `ForkJoinPool.commonPool()` của toàn JVM như thể nó là pool riêng của publisher.** Không thể giả định executor mặc định là một thread pool độc quyền chỉ dành cho publisher đó.

### 8.2. Executor do ứng dụng cung cấp

```java
ExecutorService delivery = Executors.newFixedThreadPool(2);
SubmissionPublisher<String> publisher =
        new SubmissionPublisher<>(delivery, 64);
```

Hai dòng này **chỉ minh họa khởi tạo**, chưa có logic cleanup hoàn chỉnh.

`delivery` thuộc nơi tạo/quản lý executor. Nếu ứng dụng tạo pool riêng cho publisher, ứng dụng phải gọi `shutdown()` và có thể `awaitTermination(...)` khi không còn nhiệm vụ cần nhận. Nếu executor dùng chung cho nhiều publisher hoặc thành phần khác, **không một publisher riêng lẻ nào được tự shutdown executor chung**; chủ sở hữu dùng chung quyết định thời điểm đóng sau khi các thành phần đã kết thúc.

### Thứ tự shutdown an toàn

Một chuỗi quản lý vòng đời điển hình:

1. **Ngừng producer:** dừng timer, loop hoặc nguồn gọi `submit`/`offer`; đồng bộ hóa với những producer đang hoạt động để tránh race submit với close.
2. **Quyết định kết quả cuối:** gọi `close()` khi hết dữ liệu bình thường; gọi `closeExceptionally(error)` khi publisher bị lỗi không thể hoàn tất.
3. **Chờ tiêu thụ dữ liệu và terminal callback:** dùng `CompletableFuture` từ `consume`, latch/future tự quản lý ở subscriber hoặc cơ chế đồng bộ tương đương. Với nhiều subscriber, chờ từng subscriber cần thiết.
4. **Shutdown executor do mình sở hữu:** gọi `shutdown()`, sau đó `awaitTermination` với timeout; xử lý timeout và interruption theo chính sách ứng dụng.
5. **Dọn các tài nguyên khác:** đóng nguồn, buffer hoặc connection theo ownership của chúng.

Không áp dụng công thức một cách máy móc trong mọi hệ thống: có trường hợp shutdown khẩn buộc bỏ dữ liệu và không thể đợi hoàn tất. Cần phân biệt **graceful shutdown** (ưu tiên xử lý hết dữ liệu) và **forced shutdown** (ưu tiên kết thúc nhanh, chấp nhận mất việc đang chờ).

### Vì sao shutdown sớm gây lỗi?

Nếu `executor.shutdown()` xảy ra **trước** khi publisher kịp xếp các callback cuối, executor có thể ném `RejectedExecutionException` khi publisher cố gửi task. JavaDoc `SubmissionPublisher` cho phép exception executor bị ném ngược ra từ những đường publish. Khi đó dữ liệu có thể chưa đến mọi subscriber. Không nên suy ra chỉ cần bắt exception rồi coi như mọi subscriber đã nhận `onError`.

Đặc biệt, `shutdownNow()` trả về những tác vụ chưa chạy và nỗ lực ngắt các tác vụ đang chạy, **không có bảo đảm toàn bộ callback đã xong**. Sử dụng nó như biện pháp timeout/forced shutdown có điều kiện, và phải chấp nhận rằng delivery có thể không hoàn chỉnh.

## 9. Cách chờ đúng: future, latch và tránh deadlock

### CompletableFuture của consume

`publisher.consume(consumer)` trả `CompletableFuture<Void>`:

- Hoàn tất bình thường khi subscriber được phát `onComplete`.
- Hoàn tất thất bại nếu publisher phát `onError`, consumer ném exception hoặc future bị hủy.
- `get(timeout, unit)` chờ có giới hạn và cho caller xử lý `TimeoutException` hoặc `ExecutionException`.

Mẫu này phù hợp khi chỉ cần subscriber tiêu thụ mọi item qua một `Consumer`.

### Subscriber tự triển khai

Với nhiều trạng thái kết thúc hoặc xử lý phức tạp, subscriber có thể hoàn tất một `CompletableFuture<Result>` trong `onComplete` hoặc hoàn tất exceptional trong `onError`. `CountDownLatch` đơn giản khi chỉ cần một sự kiện kết thúc. Cần suy nghĩ riêng nhánh `cancel()`, vì cancel thường không mang đến terminal callback.

### Đừng đợi trên chính thread cần chạy callback

Ví dụ: dùng **single-thread executor** vừa để gửi item, vừa để đợi một latch mà chỉ `onComplete` trên chính executor đó mới đếm xuống. Executor đang bị chiếm bởi tác vụ đợi, callback cuối không thể chạy → deadlock. Hãy đợi ở thread điều phối khác hoặc thiết kế bất đồng bộ không chặn executor xử lý tín hiệu.

Không dùng `Thread.sleep(1000)` thay cho completion signal. Trên máy nhanh có thể thừa; trên máy chậm vẫn chưa đủ. Cơ chế latch/future có timeout cho kết quả có thể kiểm chứng.

## 10. Khi có nhiều subscriber hoặc nhiều producer

Mỗi subscriber của `SubmissionPublisher` có buffer/demand riêng. Đóng publisher phát kết thúc tới các **subscriber hiện tại**, nhưng họ có thể kết thúc tại các thời điểm khác nhau.

Nếu có ba subscriber và chương trình cần cả ba hoàn tất, **một latch của subscriber thứ nhất không đủ**. Có thể dùng ba future hoặc một `CountDownLatch(3)`, mỗi subscriber đếm xuống đúng một lần khi nhận terminal. Nếu một subscriber chủ động cancel và không nhận terminal, phải có cơ chế báo hủy riêng để tránh chờ vô hạn.

Khi có nhiều producer cùng dùng publisher, hãy quyết định ai có quyền phát tín hiệu cuối. Một producer gọi `close` trong lúc producer khác còn `submit` có thể khiến bên kia nhận `IllegalStateException`. Điều phối đóng ở một lifecycle owner sau khi đã chặn nguồn phát mới và đợi các producer hoàn thành phần phát cần thiết.

Cũng cần tách biệt lỗi của từng subscriber với lỗi toàn nguồn. Một subscriber xử lý chậm hoặc ném exception không nhất thiết đồng nghĩa nguồn không còn giá trị đối với subscriber khác. Chính sách tiếp tục, hủy riêng, phát lỗi toàn pipeline hoặc retry phải được định nghĩa dựa trên nghiệp vụ.

## 11. Những điểm tinh tế về submit, buffer và backpressure khi đóng

`SubmissionPublisher` sử dụng buffer độc lập theo subscription để điều hòa khác biệt tốc độ giữa bên phát và bên nhận. Các phương thức có hành vi khác nhau:

| API | Hành vi chính |
| --- | --- |
| `submit(item)` | Thường chờ không ngắt được khi buffer của một subscriber không còn chỗ; phát item qua callback bất đồng bộ. |
| `offer(item, onDrop)` | Cho phép drop item khi thiếu chỗ và gọi handler nếu được cung cấp; có thể thử lại một lần theo kết quả handler. |
| `Subscription.request(n)` | Subscriber cấp demand dương, tăng hạn mức `onNext` được phép nhận. |
| `Subscription.cancel()` | Yêu cầu hủy subscription, thường không có terminal callback sau đó. |
| `close()` | Ngừng nhận item mới; phát tín hiệu hoàn tất bình thường. |
| `closeExceptionally(error)` | Ngừng nhận item mới; phát tín hiệu lỗi. |

Một subscriber chưa `request` đủ để nhận những item đã đệm có thể khiến việc chờ `onComplete` kéo dài. Vì thế thiết kế graceful shutdown phải dựa trên việc subscriber tiến triển và phối hợp demand, không chỉ gọi `close` rồi chờ vô hạn.

Nếu ứng dụng cho phép drop trong `offer`, không được tuyên bố rằng subscriber đã nhận “đầy đủ toàn bộ item” chỉ vì nhận `onComplete`. `onComplete` nghĩa là **không còn item mới cho subscription**, không phải một cam kết không mất dữ liệu bất kể chính sách overflow trước đó.

Giá trị `estimateMaximumLag()` và `estimateMinimumDemand()` dùng để theo dõi, không phải hàng rào đồng bộ để quyết định “mọi callback đã xử lý xong”. Khi cần chắc chắn, sử dụng protocol hoàn tất thực sự.

## 12. Xử lý lỗi, interruption và timeout có trách nhiệm

Một quy trình shutdown chất lượng cần xử lý cả nhánh không thành công:

- **Timeout chờ subscriber:** ghi nhận thành phần chưa kết thúc; quyết định có `cancel`, đóng lỗi, hay bỏ chờ tùy ứng dụng. Không báo hoàn tất nếu chưa thấy terminal callback.
- **Interruption:** nếu thread điều phối bị interrupt khi `await` hoặc `get`, trả lại cờ interrupt bằng `Thread.currentThread().interrupt()` khi bắt `InterruptedException` và kết thúc/ủy quyền cleanup theo chính sách. Mã ví dụ `main throws Exception` để đơn giản nhưng logic production cần chủ động.
- **Subscriber ném exception:** `SubmissionPublisher` có thể hủy subscription; handler đặc biệt ở constructor áp dụng khi `onNext` ném exception. Không mặc định caller nhận được exception của subscriber qua `submit`.
- **Executor bị từ chối:** một số đường có thể ném `RejectedExecutionException`; không giả định mọi subscriber đều đã nhận item hoặc terminal signal.
- **Nhiều lần đóng:** dùng kết quả đóng đầu tiên làm kết quả lifecycle; gọi `close` lần nữa không phải kỹ thuật gửi lại terminal để “chắc ăn”.

Không bao giờ giữ khóa ứng dụng trong lúc chờ callback nếu callback cần chính khóa đó để tiến triển. Việc đợi terminal nên xảy ra sau khi đã nhả những synchronization lock có thể gây deadlock.

## 13. Bảng lựa chọn API theo tình huống

| Tình huống | Hành động phù hợp |
| --- | --- |
| Đã phát hết item và mọi producer kết thúc | `close()`, chờ những subscriber quan trọng nhận terminal. |
| Nguồn bị hỏng, không thể tiếp tục | `closeExceptionally(cause)`, ghi nhận lỗi và chờ callback khi cần. |
| Chỉ một subscriber không còn muốn nhận | `Subscription.cancel()`, tự quản lý điểm hoàn tất của tác vụ bị hủy. |
| Muốn xử lý item qua hàm đơn giản | `consume(consumer)` và chờ `CompletableFuture`. |
| Cần biết tất cả subscriber hoàn tất | Latch/future theo từng subscriber và phối hợp ở owner. |
| Có pool riêng cho delivery | Sau khi hoàn tất, owner gọi `shutdown` rồi `awaitTermination`. |
| Executor dùng chung nhiều publisher | Chỉ owner chung shutdown sau khi các thành phần hoàn tất. |
| Hệ thống buộc dừng ngay | Có thể `shutdownNow` hoặc cancel có kiểm soát; chấp nhận và báo cáo công việc có thể chưa hoàn tất. |

## 14. Bài tập tự kiểm tra

**Câu 1:** Gọi `publisher.close()` xong, `publisher.isClosed()` trả `true`. Có thể khẳng định subscriber đã chạy `onComplete()` không?  
**Đáp:** Không. Callback được phát bất đồng bộ, cần completion signal riêng nếu phải đợi.

**Câu 2:** `closeExceptionally(new IOException("failed"))` khác `close()` thế nào?  
**Đáp:** Nó phát `onError` với exception cho subscriber, còn `close()` phát `onComplete` khi hết dữ liệu bình thường.

**Câu 3:** Ứng dụng tạo `ExecutorService` rồi đưa vào constructor `SubmissionPublisher`. Đóng publisher có shutdown executor đó không?  
**Đáp:** Không. Owner executor quản lý shutdown riêng.

**Câu 4:** Chờ `CountDownLatch` trong chính single-thread delivery executor có nguy cơ gì?  
**Đáp:** Deadlock nếu callback cần đếm latch cũng chỉ có thể chạy trên executor đang bị chiếm.

**Câu 5:** Publisher gọi `close` trong lúc một producer khác `submit`. Điều gì có thể xảy ra?  
**Đáp:** Producer kia có thể nhận `IllegalStateException` khi publisher đã đóng; cần điều phối lifecycle giữa producer threads.

**Câu 6:** Subscriber gọi `cancel()` thì có chắc nhận `onComplete` không?  
**Đáp:** Không. Cancel là yêu cầu hủy best effort, không phải terminal signal.

**Câu 7:** `onComplete` có đồng nghĩa không item nào bị drop trước đó không?  
**Đáp:** Không. Chính sách `offer` có thể đã drop item; completion chỉ báo không còn item mới cho subscription đó.

**Câu 8:** Nếu publisher dùng `ForkJoinPool.commonPool()` mặc định, sau khi `close` có nên gọi `commonPool().shutdownNow()` không?  
**Đáp:** Không. Common pool là tài nguyên chia sẻ của JVM, không phải executor riêng của publisher.

**Ghi nhớ:** **Đóng publisher → chờ tín hiệu kết thúc → shutdown executor thuộc quyền sở hữu** là ba trách nhiệm riêng. `close()` và `closeExceptionally()` quyết định cách kết thúc luồng; `onComplete`/`onError` xác nhận kết quả ở subscriber; owner executor bảo đảm tài nguyên delivery được giải phóng đúng thời điểm.

## Tài liệu tham khảo chính thức

- [Oracle Java 21 API — SubmissionPublisher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html)
- [Oracle Java 21 API — Flow.Subscriber](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscriber.html)
- [Oracle Java 21 API — Flow.Subscription](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.Subscription.html)
- [Oracle Java 21 API — ExecutorService](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Oracle Java 21 API — CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)


</details>

- [Quay lại đầu trang](#back-to-top)
