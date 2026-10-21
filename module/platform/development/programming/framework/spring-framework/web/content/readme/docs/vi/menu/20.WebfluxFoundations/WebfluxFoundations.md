<a id="back-to-top"></a>

# Nền tảng WebFlux

## Menu
- [Reactive Streams và vai trò của Publisher, Subscriber, Subscription](#webflux-foundations-001)
- [Mono, Flux và ý nghĩa của không có, một hoặc nhiều giá trị](#webflux-foundations-002)
- [Tính trì hoãn, đăng ký thực thi và vòng đời một chuỗi reactive](#webflux-foundations-003)
- [Event loop, mô hình luồng và tác hại của thao tác chặn](#webflux-foundations-004)
- [Backpressure, lượng yêu cầu dữ liệu và giới hạn khi đi qua giao thức HTTP](#webflux-foundations-005)
- [map, flatMap, kết hợp kết quả và lựa chọn mức đồng thời](#webflux-foundations-006)
- [Lỗi, hủy đăng ký, timeout và giải phóng tài nguyên](#webflux-foundations-007)
- [Đánh giá WebFlux theo toàn bộ chuỗi I/O; không mặc định nhanh hơn MVC](#webflux-foundations-008)

## <a id="webflux-foundations-001">Reactive Streams và vai trò của Publisher, Subscriber, Subscription</a>

<details>
<summary>Xem chi tiết</summary>

# Reactive Streams: Publisher, Subscriber và Subscription trong Spring WebFlux

## 1. Mục tiêu và phạm vi

Ứng dụng WebFlux có thể nhận, xử lý và trả về các luồng dữ liệu không đồng bộ mà không bắt buộc chiếm một thread riêng chỉ để chờ I/O. Đằng sau `Mono`, `Flux` và phần lớn pipeline phản ứng trong WebFlux là **Reactive Streams**, một giao thức nhỏ để thành phần phát dữ liệu và thành phần tiêu thụ dữ liệu trao đổi tín hiệu với **backpressure** — khả năng bên nhận kiểm soát nhu cầu nhận phần tử.

Bài này giải thích đúng bản chất ba giao diện `Publisher<T>`, `Subscriber<T>` và `Subscription`; đi từ trình tự tín hiệu, `request(n)`, `cancel()` tới ví dụ Java 17, Project Reactor và Spring WebFlux. Nội dung tương thích về mặt khái niệm với **Spring Framework 6.x, Spring Boot 3.x, Java 17+**, tập trung ở nền tảng Reactive Streams JVM và HTTP reactive stack.

Sau khi học, bạn có thể:

- Nêu chính xác vai trò Publisher, Subscriber, Subscription và phân biệt chúng với các HTTP request/controller.
- Vẽ được trình tự `onSubscribe → onNext* → (onError | onComplete)?`; biết khi nào chuỗi kết thúc hay bị hủy.
- Phân tích `request(n)` dương, nhu cầu tích lũy, `Long.MAX_VALUE` và tính chất không phát vượt nhu cầu.
- Giải thích tại sao `request(0)`/`request(-1)` là vi phạm hợp đồng, tại sao `cancel` không bảo đảm dừng ngay.
- Hiểu backpressure **không đồng nghĩa** non-blocking, async, tự sinh thread, queue vô hạn hoặc dừng nguồn nóng.
- Sử dụng Reactor `Flux`/`Mono`, `BaseSubscriber`, `StepVerifier`, các toán tử `map`/`filter`/`flatMap`/`publishOn`/`subscribeOn` đúng kỳ vọng.
- Viết ứng dụng WebFlux ngắn với `Flux`/`Mono` và HTTP streaming; biết không tự `subscribe()` trong controller để tạo response.
- Kiểm thử tín hiệu, demand, cancellation, lỗi, terminal signal và phần HTTP boundary qua `curl`/`WebTestClient`.

**Không nhầm WebFlux với Spring MVC async.** MVC cũng có thể trả một số reactive types nhưng xử lý HTTP qua Servlet stack; WebFlux xây trên reactive contract và non-blocking I/O. Không phải mọi dependency bên trong pipeline tự nhiên trở thành non-blocking: gọi JDBC hay `Thread.sleep` trên event loop vẫn có thể chặn.

## 2. Tại sao Reactive Streams ra đời?

Trong synchronous pull model, bên tiêu thụ gọi `iterator.next()` khi sẵn sàng; producer không đẩy vô hạn. Với asynchronous push, producer có thể nhanh hơn consumer:

```text
Publisher:  100,000 events/second
       |
       v
Unbounded buffer: grow -> GC pressure -> OOM
       |
       v
Subscriber: 100 events/second
```

Reactive Streams định nghĩa một **điểm giao ước** để subscriber gửi nhu cầu `request(n)` và publisher không phát `onNext` vượt tổng demand đã nhận. Một consumer chậm có thể yêu cầu ít phần tử trước, xử lý xong mới yêu cầu tiếp.

```text
Subscriber                            Publisher
    |                                     |
    |  subscribe(...)                     |
    |------------------------------------>|
    |  onSubscribe(subscription)          |
    |<------------------------------------|
    |  subscription.request(2)            |
    |------------------------------------>|
    |  onNext(A)                          |
    |<------------------------------------|
    |  onNext(B)                          |
    |<------------------------------------|
    |  [chưa có demand cho C]             |
    |  subscription.request(1)            |
    |------------------------------------>|
    |  onNext(C)                          |
    |<------------------------------------|
    |  onComplete()                       |
    |<------------------------------------|
```

Mô hình trên là **giao thức logic**, không bảo đảm mỗi request tương ứng một packet mạng, mỗi phần tử chạy thread riêng hoặc mỗi `request(n)` phải phát đủ n phần tử. Publisher có thể phát ít hơn và kết thúc, lỗi, hoặc chờ I/O. Ngược lại, `onError`/`onComplete` có thể đến **không cần demand**.

Reactive Streams tập trung bốn nguyên tắc: xử lý dữ liệu tiềm năng bất đồng bộ, truyền nhu cầu ngược từ consumer, truyền tín hiệu theo thứ tự, và không buộc consumer giữ một hàng đợi vô hạn. Đặc tả không tự tạo cơ chế CPU scheduling, quota hoặc database backpressure thay cho bạn.

## 3. Bốn giao diện lõi trong JVM specification

Reactive Streams JVM định nghĩa `org.reactivestreams` interfaces:

```java
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> subscriber);
}

public interface Subscriber<T> {
    void onSubscribe(Subscription subscription);
    void onNext(T element);
    void onError(Throwable error);
    void onComplete();
}

public interface Subscription {
    void request(long n);
    void cancel();
}

public interface Processor<T, R>
        extends Subscriber<T>, Publisher<R> {
}
```

Đây là **bản chép lại chữ ký API để học**, không phải source file được thêm vào dự án: `org.reactivestreams` đã được thư viện cung cấp. Không tự định nghĩa trùng package/interface trong project.

| Thành phần | Trách nhiệm chính | Ví dụ |
| --- | --- | --- |
| `Publisher<T>` | Cung cấp các phần tử kiểu T cho subscriber đúng protocol | Reactor `Flux`/`Mono` |
| `Subscriber<T>` | Nhận signals, quyết định demand và xử lý dữ liệu/lỗi | `BaseSubscriber<T>` |
| `Subscription` | Liên kết **một quan hệ Publisher–Subscriber**, điều khiển `request`/`cancel` | Đối tượng truyền qua `onSubscribe` |
| `Processor<T,R>` | Đồng thời nhận upstream và phát downstream; có thể biến đổi T thành R | Nền tảng một số operator, không phải mọi operator là Processor object hiển lộ |

**Điểm cốt lõi:** `Publisher.subscribe(...)` trả `void` theo spec; `Subscription` đến với subscriber qua `onSubscribe(subscription)`. Trong Reactor, một số overload tiện ích của `Flux.subscribe(...)` trả `Disposable` để dễ hủy; không nhầm API tiện ích Reactor với chữ ký chuẩn Reactive Streams.

Không phải `Publisher` là nơi biết bạn muốn nhận bao nhiêu phần tử *trước khi đăng ký*. Nhu cầu được truyền qua `Subscription` tương ứng sau `onSubscribe`.

## 4. Publisher: nguồn tín hiệu theo nhu cầu

`Publisher<T>` chịu trách nhiệm chấp nhận subscriber qua `subscribe(Subscriber<? super T>)`, cung cấp `Subscription` và phát các tín hiệu theo protocol.

Các quy tắc quan trọng:

1. Số `onNext` đã phát **không bao giờ lớn hơn** tổng demand đã được chấp nhận trên subscription.
2. Publisher **có thể phát ít hơn** số đã yêu cầu và kết thúc bằng `onComplete` hoặc `onError`.
3. Các signals tới một subscriber phải **serialized**, không đồng thời gọi hai `onNext`/terminal callbacks bất chấp thứ tự.
4. `onSubscribe` luôn được gửi **trước** `onNext`, `onError` hoặc `onComplete` (đối với subscriber không null).
5. Sau terminal signal `onComplete` hoặc `onError`, không được phát tiếp `onNext` hay terminal thứ hai.
6. Sau `cancel`, Publisher phải **cuối cùng ngừng** gửi tín hiệu; dừng có thể trễ do các signals đang bay trong pipeline.
7. Publisher có thể hỗ trợ nhiều subscribers, multicast hoặc unicast; spec **không bắt buộc** mọi publisher là cold/replayable.

Ví dụ Reactor:

```java
Flux<Integer> numbers = Flux.range(1, 3);
```

`Flux.range` là nguồn hữu hạn. **Tạo biến `numbers` không có nghĩa đã chạy một pipeline tiêu thụ**; tín hiệu thường được khởi phát khi có subscriber. Tuy vậy, không được suy “mọi code trong biểu thức tạo Flux đều lazy”, vì phép tính ở arguments của `just(...)` có thể chạy ngay khi tạo object.

### Publisher và dữ liệu không giới hạn

`Flux.interval`, event bus, log tailing, WebSocket hoặc SSE có thể phát dữ liệu lâu dài. Bounded subscriber demand không tự giới hạn nguồn ngoài đời (thiết bị, message broker) nếu nguồn không hỗ trợ pause. Lúc đó adapter phải chọn chính sách buffer hữu hạn, drop/sampling, fail hoặc hạn chế upstream bằng cơ chế riêng. Không thể biến một thiết bị đẩy vô hạn thành backpressure-aware chỉ bằng chữ `Flux`.

## 5. Subscriber: bộ nhận và người quyết định demand

Một `Subscriber<T>` thực thi bốn callbacks:

| Callback | Khi nào được gọi | Nhiệm vụ |
| --- | --- | --- |
| `onSubscribe(Subscription)` | Một lần khi thiết lập subscription | Lưu subscription, quyết định `request(n)` hoặc `cancel()` |
| `onNext(T)` | Khi có phần tử được cấp theo demand | Tiêu thụ phần tử, có thể yêu cầu thêm |
| `onError(Throwable)` | Luồng kết thúc với lỗi | Cleanup, báo lỗi; **terminal** |
| `onComplete()` | Luồng hữu hạn kết thúc thành công | Cleanup/kết thúc; **terminal** |

Một subscriber **phải gửi demand** thông qua `request(n)` để nhận `onNext`. Nhưng `onError` và `onComplete` có thể xảy ra ngay cả khi chưa request. Các signals luôn phải có thứ tự hợp lệ.

**Không nên mặc định tạo nhiều subscriber cho cùng một instance tùy ý**: Reactive Streams yêu cầu Publisher có thể được subscribe nhiều lần với **các Subscriber instance khác nhau**, nhưng publisher được quyền quyết định unicast/multicast và có thể reject các subscriptions không hỗ trợ theo protocol. Không gọi lại `subscribe` cùng một Subscriber instance đang active rồi kỳ vọng framework tự an toàn.

Trong manual implementation, nhận thêm một `onSubscribe` khi đã có subscription phải hủy subscription đến sau theo quy tắc spec. Đối với phần lớn code nghiệp vụ WebFlux, nên dùng Reactor operators và `BaseSubscriber` khi cần custom demand thay vì tự viết raw Subscriber.

## 6. Subscription: cầu nối và quyền kiểm soát

`Subscription` được gắn riêng cho một quan hệ Publisher–Subscriber:

```java
subscription.request(5);
subscription.request(2);
subscription.cancel();
```

- `request(5)` cấp quyền phát **tối đa 5 phần tử**.
- `request(2)` bổ sung thêm **2 phần tử**, không reset số demand thành 2.
- Mỗi `onNext` được phát sử dụng một đơn vị demand.
- `cancel()` yêu cầu ngừng phát và giải phóng resources khi có thể.
- Sau cancel, các `request`/`cancel` tiếp theo trên subscription phải là no-op theo spec.
- `request` và `cancel` cần xử lý thread-safe/serialized đúng hợp đồng; subscriber không tùy tiện gọi hai method này đồng thời không kiểm soát.
- Request/cancel không phải lời hứa sẽ tạo dữ liệu hoặc chấm dứt I/O ngay.

Giả sử:

```text
Initial outstanding demand = 0
request(3) -> outstanding = 3
onNext(A)  -> outstanding = 2
onNext(B)  -> outstanding = 1
request(2) -> outstanding = 3
onNext(C)  -> outstanding = 2
onNext(D)  -> outstanding = 1
onNext(E)  -> outstanding = 0
```

Ở mọi thời điểm `emitted <= total requested`. Một nguồn hữu hạn có thể `onComplete` khi hết dữ liệu; completion signal không tiêu tốn một “phần tử dữ liệu” theo cách `onNext` tiêu tốn demand.

### request(0), request(-1) là sai

Theo Subscription rule 3.9: với subscription chưa bị hủy, `request(n)` có `n <= 0` là vi phạm protocol và **phải signal `onError` với `IllegalArgumentException`**, thay vì xem là “tạm dừng” bình thường. Đừng dùng `request(0)` để chờ; chỉ cần **chưa request thêm**, hoặc chủ động cancel nếu không còn cần luồng.

### Long.MAX_VALUE và cộng dồn nhu cầu

`request(Long.MAX_VALUE)` được phép coi là **nhu cầu thực tế vô hạn**. Implementations phải chịu được nhiều lần request mà không tràn bộ đếm `long` sang số âm; Reactor thường dùng cơ chế cộng demand có giới hạn ở `Long.MAX_VALUE`. Đây không phải “publisher sẽ sinh vô hạn data”: source finite vẫn có thể kết thúc, error hoặc không có data.

Reactor các overload `subscribe()` tiện ích thường request unbounded. Nếu mục tiêu là học backpressure, hãy dùng `BaseSubscriber`/`StepVerifier` với demand được điều khiển thay vì chỉ viết `flux.subscribe(System.out::println)` rồi nói đã kiểm thử request từng phần tử.

## 7. Protocol signals: thứ tự hợp lệ và bất hợp lệ

Hình thức trừu tượng:

```text
onSubscribe → onNext* → (onError | onComplete)?
```

Dấu `*` = 0 hoặc nhiều `onNext`; `?` = 0 hoặc 1 terminal signal. Không có terminal signal là khả dĩ khi stream vô hạn, nguồn chưa xong hoặc subscription bị cancel.

| Chuỗi | Hợp lệ? | Vì sao |
| --- | --- | --- |
| `onSubscribe → onNext(A) → onComplete` | Có, nếu có demand ≥1 | Luồng hữu hạn |
| `onSubscribe → onComplete` | Có | Luồng rỗng có thể hoàn tất không cần request |
| `onSubscribe → onError(ex)` | Có | Lỗi có thể đến trước onNext, không cần request |
| `onSubscribe → onNext(A) → onNext(B)` | Có nếu đủ demand và chưa kết thúc | Stream còn hoạt động |
| `onNext(A) → onSubscribe` | Không | Thiếu onSubscribe trước data |
| `onSubscribe → onNext(A)` khi demand=0 | Không | Vượt nhu cầu |
| `onSubscribe → onComplete → onNext(A)` | Không | Signal sau terminal |
| `onSubscribe → onError(x) → onComplete` | Không | Có hai terminal signals |
| Hai `onNext` chạy đồng thời tới một Subscriber | Không | Signals phải serialized |

“Serialized” **không bắt buộc tất cả callbacks chạy cùng một Thread**. Chúng có thể đến từ các threads khác nhau nếu được đồng bộ để có quan hệ happens-before và không chồng lấn. Đừng dùng shared mutable state giữa callbacks một cách tùy tiện mà không xét thread handoff, dù protocol giữ thứ tự signals tới subscriber.

### Tại sao null không phải một phần tử hợp lệ?

Reactive Streams không cho phép `onNext(null)`; null làm hỏng contract và có thể biểu thị nhầm “không có dữ liệu”. Dùng `Mono.empty()` để biểu diễn hoàn tất không có value, `Optional<T>` được phát như một đối tượng non-null nếu hợp đồng API phù hợp, hoặc xây domain type rõ ràng.

Trong Reactor, `Mono.just(null)` không hợp lệ, `Mono.justOrEmpty(nullable)` có thể tạo Mono rỗng khi giá trị null. Một nguồn không có phần tử vẫn có thể hoàn tất bằng `onComplete`.

## 8. Backpressure là pull-controlled delivery, không phải backpressure ở mọi nơi

Subscriber là phía điều khiển demand, nhưng cần phân biệt ba mức:

1. **Reactive Streams demand:** số `onNext` Publisher được phép gửi cho Subscriber.
2. **Operator demand:** các toán tử có thể request **nhiều hơn** upstream để thực thi `buffer`, `flatMap` hoặc prefetch.
3. **Transport/storage backpressure:** TCP flow control, file/network buffering, database driver, message broker có cơ chế riêng.

Một final subscriber gọi `request(1)` không bảo đảm **chỉ có đúng một database row được đọc**, hoặc chỉ một network packet được nhận. `flatMap` có thể subscribe nhiều inner publishers và prefetch; `buffer(10)` có thể request 10 nguồn để tạo một `List<T>` downstream. Đó không trái spec nếu mỗi boundary gửi không vượt nhu cầu trên boundary của nó.

Ví dụ:

```text
Final Subscriber: request(1) List
          |
          v
Flux.buffer(10): request(10) items upstream
          |
          v
Source: emits 10 items -> buffer returns one List
```

Giải pháp kiểm soát memory không chỉ là “dùng Flux”. Hãy chọn `prefetch`, concurrency, bounded buffer, rate/size limits, timeout và resource ownership đúng.

### Khi nguồn không thể chậm lại

Với event bus nóng không hỗ trợ pause, adapter phải xác định:

- Buffer có giới hạn và xử lý khi đầy.
- Drop/sampling với observability rõ.
- Fail/close connection khi consumer quá chậm.
- Dùng broker có ack/credits/pause-consumer nếu hỗ trợ.

Các lựa chọn Reactor như `onBackpressureBuffer`, `onBackpressureDrop`, `onBackpressureLatest` cần dùng có chủ đích, hiểu mất dữ liệu và memory footprint. **Unbounded buffer không phải backpressure giải pháp an toàn.**

## 9. Cold Publisher và Hot Publisher

Reactive Streams specification **không áp đặt** một Publisher phải cold hay hot. Đây là tính chất cách nguồn được thiết kế:

**Cold:** mỗi lần subscribe có thể bắt đầu một chuỗi riêng; `Flux.range(1,3)` là ví dụ. Nếu nguồn cold gọi HTTP/DB khi subscribe, hai subscriptions có thể kích hoạt công việc hai lần.

**Hot:** dữ liệu được phát theo thời gian và có thể chia sẻ qua subscribers. Subscriber đăng ký muộn có thể bỏ lỡ sự kiện trước đó; backpressure policy tùy source. Reactor `Sinks.Many` là công cụ tạo hot signal source (với các biến thể và overflow policy).

```text
Cold source A:  subscribe1 -> 1 2 3 (riêng)
                subscribe2 -> 1 2 3 (riêng)

Hot broadcast:  time ------  A B C D E F --->
                sub1          ^ B C D E F
                sub2              ^ D E F
```

Sơ đồ mang tính minh họa: replay hot source có thể phát lại lịch sử theo cấu hình; không phải mọi hot publisher đều mất dữ liệu cũ. WebFlux không tự đảm bảo một browser reconnect SSE sẽ nhận lại tất cả events; phải có replay ID, broker retention và chính sách reconnect riêng.

### Tạo Flux chưa chắc đã lazy hết mọi thứ

```java
String value = compute();       // Chạy NGAY.
Mono<String> eager = Mono.just(value);

Mono<String> lazy = Mono.defer(
        () -> Mono.just(compute())); // Chạy khi subscribe.
```

Đây là snippet trong class có method `compute()`. Nếu gọi `Mono.just(expensiveServiceCall())`, phép gọi trong argument chạy ngay lúc dựng pipeline. Dùng `Mono.defer(...)` hoặc `Mono.fromCallable(...)` khi cần hoãn tạo dữ liệu, và phải xét blocking vs non-blocking của công việc.

## 10. Reactor: Flux, Mono và Subscriber tiện ích

Project Reactor là thư viện reactive được Spring WebFlux sử dụng:

| Loại | Số phần tử hợp lệ | Một số factory |
| --- | --- | --- |
| `Mono<T>` | 0 hoặc 1 rồi terminal | `just`, `empty`, `error`, `fromCallable`, `defer` |
| `Flux<T>` | 0..N rồi terminal hoặc luồng chưa kết thúc | `just`, `range`, `fromIterable`, `interval`, `defer` |
| `BaseSubscriber<T>` | Helper để viết Subscriber có kiểm soát `request/cancel` | Hook `hookOnSubscribe`, `hookOnNext` |
| `StepVerifier` | Test signals và demand theo scenario | `expectNext`, `thenRequest`, `verifyComplete` |

`Flux<T>` và `Mono<T>` thực thi các contract reactive cần thiết; khi truyền vào WebFlux handler, framework subscribe và quản lý response lifecycle cho bạn.

Một số `Flux.subscribe(...)` overload là terminal operation tiện ích: **mặc định thường request unbounded**, đặc biệt `subscribe()` và các overload lambda tiêu chuẩn. Đừng nhầm chúng với tự triển khai `Subscriber` nhận `Subscription`.

**Điểm quan trọng:** `Mono` có tối đa một `onNext`, nhưng không có nghĩa Mono không có demand hoặc luôn synchronous. `Mono.error(...)`/`Mono.empty()` có thể gửi terminal mà không phát giá trị. Một Mono cũng có thể chạy network I/O bất đồng bộ.

## 11. Java 17 độc lập: Flow API gần tương ứng Reactive Streams

Java 9+ có `java.util.concurrent.Flow` với `Publisher`, `Subscriber`, `Subscription` và `Processor` **tương đồng về thiết kế** với `org.reactivestreams`. Đây là **hai tập Java interfaces khác package**, không phải cùng object cast trực tiếp. Khi tích hợp, cần adapter phù hợp, ví dụ `reactor.adapter.JdkFlowAdapter` (dependency `reactor-adapter` tùy dự án).

Để quan sát callbacks bằng **Java 17 chuẩn không cần Maven**, dùng `SubmissionPublisher`:

Tệp `FlowDemandDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class FlowDemandDemo {

    public static void main(String[] args) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);

        try (SubmissionPublisher<String> publisher =
                     new SubmissionPublisher<>()) {

            publisher.subscribe(new Flow.Subscriber<String>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription s) {
                    this.subscription = s;
                    System.out.println("onSubscribe");
                    s.request(1);
                }

                @Override
                public void onNext(String item) {
                    System.out.println("onNext=" + item);
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable throwable) {
                    System.out.println("onError=" +
                            throwable.getClass().getSimpleName());
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

            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "No terminal signal before timeout");
            }
        }
    }
}
```

Lệnh:

```bash
javac FlowDemandDemo.java
java FlowDemandDemo
```

Stdout khi chương trình hoàn tất bình thường:

```text
onSubscribe
onNext=A
onNext=B
onComplete
```

`CountDownLatch` đợi terminal signal, nên main thread không kết thúc trước callback. `SubmissionPublisher` có thể **buffer** dữ liệu và chạy callbacks bất đồng bộ qua executor; hai lệnh `submit` không cần đợi từng `request(1)`. Chính `Subscription` mới giới hạn việc **giao onNext cho subscriber**, không nhất thiết chặn producer tạo/buffer dữ liệu ngay lập tức. Dự án thực cần chọn bounded buffer và chính sách khi quá tải.

Lưu ý kỹ: Ví dụ dùng `Flow` để minh họa protocol trên JDK thuần. Với WebFlux, tiếp tục dùng Reactor `Flux`/`Mono`; không được cast `Flow.Publisher` thành `org.reactivestreams.Publisher` mà không adapter.

## 12. Reactor demo: điều khiển từng phần tử bằng BaseSubscriber

Trong Maven project có Reactor Core (Spring Boot WebFlux starter cung cấp), tạo:

Tệp `src/main/java/com/example/demo/DemandDemo.java`:

```java
package com.example.demo;

import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

public class DemandDemo {

    public static void main(String[] args) {
        Flux.range(1, 5)
            .subscribe(new BaseSubscriber<Integer>() {
                private int received;

                @Override
                protected void hookOnSubscribe(
                        Subscription subscription) {
                    System.out.println("subscribed");
                    request(1);
                }

                @Override
                protected void hookOnNext(Integer value) {
                    System.out.println("next=" + value);
                    received++;

                    if (received == 3) {
                        System.out.println("cancel");
                        cancel();
                    } else {
                        request(1);
                    }
                }

                @Override
                protected void hookOnComplete() {
                    System.out.println("complete");
                }
            });
    }
}
```

Để chạy class này trên classpath Maven dependencies, dùng IDE “Run main” hoặc thiết lập Maven exec plugin thích hợp. Nếu chỉ dùng `javac` trực tiếp không có Reactor jars, code **không biên dịch**. Mã này là complete Java class, chạy trong Spring WebFlux Maven project có Reactor dependency.

Stdout cho `Flux.range` synchronous ở ví dụ:

```text
subscribed
next=1
next=2
next=3
cancel
```

Không có `complete` vì subscriber chủ động cancel sau phần tử thứ ba. `BaseSubscriber` quản lý reentrant request một cách phù hợp Reactor contract. Nếu đổi thành subscriber request đủ cả năm phần tử và không cancel, `Flux.range` có thể phát `onComplete` sau phần tử cuối.

**Quan sát:** `request(1)` được gọi ngay trong hook, dữ liệu có thể đến đồng bộ trên thread hiện tại. Reactive Streams không buộc asynchronous execution; “reactive” mô tả giao thức tín hiệu và demand, không tự phát sinh thread.

## 13. StepVerifier: kiểm soát demand trong test

Reactor cung cấp `reactor-test` và `StepVerifier`. Thêm dependency test scope `io.projectreactor:reactor-test` nếu project chưa kéo theo. Ví dụ kiểm thử source hữu hạn:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ReactiveDemandTest {

    @Test
    void doesNotEmitMoreThanInitialDemand() {
        StepVerifier.create(Flux.range(1, 3), 0)
            .expectSubscription()
            .thenRequest(2)
            .expectNext(1, 2)
            .thenRequest(1)
            .expectNext(3)
            .verifyComplete();
    }

    @Test
    void aFluxCanBeCancelledBeforeCompletion() {
        StepVerifier.create(Flux.range(1, 10), 0)
            .expectSubscription()
            .thenRequest(1)
            .expectNext(1)
            .thenCancel()
            .verify();
    }

    @Test
    void emptyMonoCompletesWithoutOnNext() {
        StepVerifier.create(
                reactor.core.publisher.Mono.empty(), 0)
            .expectSubscription()
            .verifyComplete();
    }

    @Test
    void errorIsTerminal() {
        StepVerifier.create(
                Flux.error(new IllegalStateException("boom")))
            .expectErrorMessage("boom")
            .verify();
    }
}
```

Đây là một class JUnit 5 có thể chạy trong Spring Boot project khi `spring-boot-starter-test`/`reactor-test` có trên test classpath. Các test trên không giả định lập lịch scheduler hay thời gian thực; `Flux.range` phát phần tử đồng bộ đúng demand, còn `Mono.empty` có thể hoàn tất dù request=0.

**Không viết** `StepVerifier.create(Flux.range(1,3), 0).expectNext(1)` ngay sau subscription mà chưa `thenRequest(...)`: source đúng contract sẽ không phát value, test có thể timeout/chờ. Đừng lầm kiểm tra “không có value” với `expectNoEvent(...)` khi đang chờ đến thời điểm không có scheduling phù hợp.

Đối với nguồn dùng timer, có thể dùng `StepVerifier.withVirtualTime` khi thích hợp để tránh test chậm/timing flaky. Nhưng khi test chính là protocol request/cancel, nguồn `range` và demand tường minh thường rõ hơn.

## 14. Tự viết Publisher chuẩn có khó không?

Có. Một `Publisher` tuân chuẩn phải xử lý:

- `subscribe(null)` và đường đi báo lỗi đúng.
- `onSubscribe` đúng một lần, trước mọi tín hiệu khác.
- `request(n)` hợp lệ, cộng dồn an toàn, saturation `Long.MAX_VALUE`.
- `request(n<=0)` signal `onError(IllegalArgumentException)` theo spec; không chỉ ném ra từ `request`.
- Serialize signals và tránh reentrant `onNext → request → onNext` đệ quy vô hạn/stack overflow.
- Tôn trọng cancellation eventually và cleanup references.
- Không phát null, không phát sau terminal, không gửi hai terminal signals.
- Race giữa `request`, `cancel`, phát giá trị, error và completion trên nhiều threads.
- Nếu không có dữ liệu, có thể hoàn tất/lỗi ngay; nếu có nguồn chậm, không spin-wait trên event loop.

Đừng tự viết Publisher chỉ bằng:

```java
subscriber.onSubscribe(subscription);
for (T item : items) {
    subscriber.onNext(item); // SAI: không kiểm tra request(n).
}
subscriber.onComplete();
```

Đây là **phản ví dụ**, không phải tệp compile; biến `subscriber`/`subscription`/`items` chỉ mang ý nghĩa minh họa. Nó vi phạm quy tắc demand khi subscriber chưa request.

Thay vì tự xây giao thức, dùng `Flux.fromIterable`, `Flux.generate` (khi phù hợp synchronous stateful generation), `Flux.create`/`Sinks.Many` theo hướng dẫn Reactor, và đọc rõ overflow/backpressure semantics. Khi tự xây integration Publisher mới, dùng **Reactive Streams Technology Compatibility Kit (TCK)** để kiểm tuân thủ protocol.

## 15. Operator có thể thay đổi demand

Ví dụ:

```java
Flux.range(1, 100)
    .filter(n -> n % 2 == 0)
    .buffer(5)
    .take(2);
```

Các toán tử không chỉ chuyển giá trị, mà còn tạo quan hệ Publisher–Subscriber bên trong pipeline.

- `map` thường giữ tỷ lệ một vào/một ra cho mỗi item được phát thành công.
- `filter` có thể cần nhiều upstream items hơn downstream demand vì một số item bị loại.
- `buffer(5)` cần tới 5 phần tử đầu vào để tạo một list đầu ra.
- `flatMap` có thể subscribe nhiều publishers song song, prefetch và interleave kết quả; cần giới hạn `concurrency`/`prefetch`.
- `concatMap` giữ trật tự nhiều inner streams theo cách khác, có chi phí throughput/queue riêng.
- `publishOn` chuyển xử lý sang scheduler khác và có queue/prefetch boundary.
- `limitRate` hỗ trợ điều chỉnh cách request upstream theo đợt, **không** tự giới hạn CPU/memory của mọi downstream.
- `take(n)` thường hủy upstream khi đã lấy đủ n phần tử, nhưng cancellation có thể đến với độ trễ.

**Không kỳ vọng mỗi phép `request(1)` của terminal subscriber sẽ trùng chính xác một `doOnRequest(1)` ở source** khi có các operator trung gian reshape requests. Dùng `doOnRequest` ở các vị trí hợp lý để quan sát demand thực tế từng boundary.

```java
Flux.range(1, 20)
    .doOnRequest(n -> System.out.println("source demand=" + n))
    .buffer(5)
    .doOnRequest(n -> System.out.println("list demand=" + n))
    .take(2);
```

Đây là pipeline minh họa; chưa subscribe nên không có stdout. Nếu subscribe, thứ tự/số lượng request phụ thuộc từng operator và Reactor version. Đừng hardcode log sequence trong tài liệu mà không có test phù hợp.

## 16. Scheduler và threads: subscribeOn, publishOn

Reactive Streams **không chỉ định một thread model cụ thể**. Nguồn như `Flux.range` thường đồng bộ, còn `WebClient`/Reactor Netty tạo tín hiệu từ event loop I/O bất đồng bộ. Reactor cung cấp `Scheduler` để chuyển execution context khi cần.

| Toán tử | Ý nghĩa khái quát |
| --- | --- |
| `subscribeOn(scheduler)` | Ảnh hưởng nơi subscription/request/upstream work bắt đầu; vị trí tương đối có quy tắc riêng |
| `publishOn(scheduler)` | Chuyển phần downstream sau nó sang worker của scheduler, tới khi có boundary khác |
| `Schedulers.parallel()` | Pool tối ưu cho CPU-bound ngắn, không dành cho blocking lâu |
| `Schedulers.boundedElastic()` | Pool bounded dành cho blocking legacy tasks khi buộc phải tích hợp |
| `delayElements(...)` | Đưa asynchronous time boundary và scheduling cho từng phát item |

Ví dụ legacy blocking call:

```java
Mono<String> value = Mono.fromCallable(
        () -> legacyJdbcService.fetchText())
    .subscribeOn(Schedulers.boundedElastic());
```

Đây là snippet trong class có `legacyJdbcService` và imports `reactor.core.publisher.Mono`, `reactor.core.scheduler.Schedulers`. Nó giúp **đẩy blocking call ra khỏi event loop**, nhưng JDBC vẫn blocking, vẫn dùng connection và boundedElastic workers. Không được diễn giải “bọc JDBC vào Mono” thành database reactive driver.

**Không gọi `block()` trong WebFlux handler/event-loop thread**; nó có thể chặn pipeline, và Reactor có thể báo lỗi khi block trong thread non-blocking. Ở boundary ứng dụng đồng bộ (CLI, tests) `block()` có thể hợp lý có kiểm soát, nhưng không dùng để phá vỡ end-to-end reactive chain trong handler.

## 17. WebFlux HTTP boundary liên quan Publisher thế nào?

Trong WebFlux annotated controller:

```java
@GetMapping("/api/numbers")
public Flux<NumberDto> numbers() {
    return Flux.range(1, 3).map(NumberDto::new);
}
```

**Controller trả Publisher**, không tự gọi `subscribe()`. WebFlux HTTP infrastructure trở thành bên tiêu thụ downstream, đăng ký subscription, áp dụng codecs và ghi response khi network I/O cho phép.

```text
Reactive DB / Flux.range / WebClient       HTTP response writer
                Publisher                      Subscriber
                  |                               |
                  | <------ subscribe ------------|
                  | ------ onSubscribe ---------->|
                  | <------ request(n) ------------|
                  | ------ onNext(record) -------->|
                  |                         JSON encoding/network
                  | ------ onComplete ------------>|
```

Sơ đồ rút gọn: WebFlux có nhiều operator, codecs và internal subscribers trung gian; không nhất thiết một `request(n)` bằng một JSON document hoặc một network packet. Reactor Netty/WebFlux xây trên non-blocking I/O và backpressure. Một HTTP client tải chậm có thể tạo áp lực ngược qua response writing, nhưng memory cũng phụ thuộc codecs, buffering, data source và giới hạn ứng dụng.

### Mono/Flux và cách HTTP phản hồi

- `Mono<Dto>`: 0 hoặc 1 object; thường phù hợp GET resource đơn.
- `Flux<Dto>`: stream nhiều giá trị; HTTP JSON array được serialize theo cách có thể cần buffering/format phù hợp; với `text/event-stream` thì mỗi event có thể được gửi theo thời gian.
- `Mono<Void>`: biểu thị xử lý completion không có value, thường kết hợp status thích hợp.
- `Flux<DataBuffer>`: raw bytes, phải quản lý memory/ref-counting và cleanup theo `DataBuffer` API.
- Lỗi trước khi response commit có thể ánh xạ thành HTTP status/body; lỗi sau khi stream commit **không thể luôn đổi** thành JSON error hoặc HTTP 500 mới.

Đừng tự tạo `flux.subscribe(value -> response.getWriter().write(...))` trong handler rồi trả `Mono.empty()`, vì framework không còn quản lý completion/cancellation/backpressure của response đúng cách.

## 18. Ứng dụng Spring Boot WebFlux chạy được

Tạo Maven project bằng Spring Initializr với **Java 17+, Spring Boot 3.x**, dependencies:

- `spring-boot-starter-webflux` (không thêm `spring-boot-starter-web` nếu muốn Boot chọn reactive WebFlux app type một cách rõ ràng).
- `spring-boot-starter-test` (test scope).
- `reactor-test` (test scope, nếu starter-test chưa có).

Ví dụ không cần database. Các file:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  NumbersController.java
src/test/java/com/example/demo/
  NumbersControllerTest.java
  ReactiveDemandTest.java
```

### 18.1. DemoApplication.java

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### 18.2. NumbersController.java

```java
package com.example.demo;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class NumbersController {

    @GetMapping(path = "/numbers",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<NumberDto> numbers(
            @RequestParam(defaultValue = "3") int count) {

        if (count < 1 || count > 20) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "count must be between 1 and 20");
        }

        return Flux.range(1, count)
                .map(NumberDto::new)
                .delayElements(Duration.ofMillis(80));
    }

    @GetMapping(path = "/numbers/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<NumberDto> one(@PathVariable int id) {
        if (id < 1 || id > 20) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "number not found"));
        }
        return Mono.just(new NumberDto(id));
    }

    @PostMapping(path = "/echo",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<EchoResponse> echo(@RequestBody Mono<EchoInput> input) {
        // Không tự subscribe: WebFlux quản lý subscription.
        return input.map(v -> new EchoResponse(v.message()));
    }

    public record NumberDto(int value) {}
    public record EchoInput(String message) {}
    public record EchoResponse(String message) {}
}
```

**Giải thích:**

- `numbers` trả một `Flux` hữu hạn, được render thành Server-Sent Events nhờ `produces=text/event-stream`. `delayElements` tạo độ trễ minh họa; không bảo đảm chính xác 80ms trên mọi hệ thống.
- `one` trả `Mono<NumberDto>` hoặc `Mono.error(...)`; lỗi trước response commit có thể thành 404.
- `echo` nhận request body dưới dạng `Mono<EchoInput>` để minh họa WebFlux reactive request-body argument; response hoàn tất khi input được decode và map xong.
- Controller **không gọi `subscribe()` hoặc `block()`**. HTTP infrastructure sở hữu demand/cancel/completion.
- Endpoint count được giới hạn từ 1..20 để demo không xuất luồng vô hạn và không tiêu thụ tài nguyên không kiểm soát.
- Nếu muốn dùng R2DBC/repository reactive thật, chọn driver cung cấp reactive contract, không tự wrap JDBC rồi tuyên bố non-blocking.

### 18.3. Khởi động

Tại project Maven có Wrapper:

```bash
./mvnw spring-boot:run
```

Trên PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Mặc định app nghe cổng `8080` nếu chưa đổi `server.port`. Đây là Spring Boot source cần classpath Spring WebFlux/Reactor/Jackson; `javac NumbersController.java` đơn độc không đủ dependency.

## 19. curl: quan sát HTTP response của WebFlux

### Server-Sent Events

```bash
curl -N -i 'http://localhost:8080/api/numbers?count=3'
```

Với `Accept` mặc định phù hợp, response thường có `Content-Type: text/event-stream` và ba events được gửi theo thứ tự; phần dữ liệu có dạng:

```text
data:{"value":1}

data:{"value":2}

data:{"value":3}

```

Các header, khoảng thời gian và chi tiết format có thể phụ thuộc codec/server, nhưng số `value` và thứ tự 1–3 là contract của `Flux.range`. `curl -N` tắt output buffering ở curl để quan sát từng event.

Không suy “request count=3 → client gọi Subscription.request(3) đúng một lần”. WebFlux writer và codecs có buffering/prefetch nội bộ; chỉ nên assert response là ba events đúng thứ tự.

### Lấy một phần tử

```bash
curl -i 'http://localhost:8080/api/numbers/2'
```

Kỳ vọng HTTP 200:

```json
{"value":2}
```

`GET /api/numbers/99` trả 404 nếu lỗi được xử lý trước khi response commit. App không có database; rule 1..20 chỉ để minh họa `Mono.error`.

### Đọc JSON body như Mono

```bash
curl -i -X POST 'http://localhost:8080/api/echo' \
  -H 'Content-Type: application/json' \
  -d '{"message":"Xin chào WebFlux"}'
```

Kỳ vọng HTTP 200 và JSON:

```json
{"message":"Xin chào WebFlux"}
```

Không có code nào tự subscribe một lần để “kích hoạt echo”, rồi lại trả một Mono khác; nếu làm vậy sẽ tạo side effects/hai subscriptions ngoài lifecycle HTTP.

### Thử input không hợp lệ

```bash
curl -i 'http://localhost:8080/api/numbers?count=1000'
```

Kỳ vọng HTTP 400 trước khi gửi streaming body vì controller validate ngay. Trong ứng dụng thực, body lỗi tùy Spring Boot error handling và cấu hình Problem Details, không nên hardcode một response JSON chi tiết nếu không cấu hình.

## 20. WebTestClient: kiểm thử HTTP reactive

Tệp `src/test/java/com/example/demo/NumbersControllerTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class NumbersControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void oneNumberReturnsMonoValueAsJson() {
        client.get()
            .uri("/api/numbers/2")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.value").isEqualTo(2);
    }

    @Test
    void rejectsInvalidCount() {
        client.get()
            .uri("/api/numbers?count=1000")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void echoJsonBody() {
        client.post()
            .uri("/api/echo")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"message\":\"hello\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.message").isEqualTo("hello");
    }

    @Test
    void receivesThreeSseItemsThenCompletes() {
        Flux<String> body = client.get()
            .uri("/api/numbers?count=3")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody();

        StepVerifier.create(body)
            .expectNextMatches(s -> s.contains("\"value\":1"))
            .expectNextMatches(s -> s.contains("\"value\":2"))
            .expectNextMatches(s -> s.contains("\"value\":3"))
            .verifyComplete();
    }
}
```

**Lưu ý test SSE:** việc decode `String.class` dưới `text/event-stream` có thể phụ thuộc codec và cách tách events trong Spring version/client. Nếu codec của project map body thành đối tượng khác hoặc cần kiểm event metadata, thay `String.class` bằng `ServerSentEvent<NumberDto>`/DTO với `ParameterizedTypeReference` tương ứng và assert giá trị. Đừng mặc định mỗi TCP chunk là một event độc lập.

Chạy:

```bash
./mvnw test
```

Hoặc PowerShell:

```powershell
.\mvnw.cmd test
```

Đây là **mẫu test có thể chạy trong project Boot 3 với dependencies phù hợp**, không phải kết quả đã thực thi trong repository người dùng. Với endpoints có Spring Security, phải cấp identity/CSRF đúng policy hoặc request có thể nhận 401/403 trước controller.

## 21. Lỗi, cancel, resource cleanup và HTTP disconnect

Khi Publisher phát `onError(error)`, đây là tín hiệu **terminal**: không được sau đó phát `onComplete` hoặc `onNext`. Reactor operators như `onErrorResume` có thể chuyển luồng lỗi thành một Publisher fallback mới, nhưng phải hiểu đây là **chuyển hướng pipeline**, không phải tiếp tục phát tín hiệu sau terminal của cùng subscription gốc.

Ví dụ:

```java
Mono<String> result = remoteCall()
    .timeout(Duration.ofSeconds(2))
    .onErrorResume(java.util.concurrent.TimeoutException.class,
                   e -> Mono.just("fallback"));
```

Đoạn trên là snippet cần method `remoteCall()` và imports; chưa là file chạy độc lập. `timeout` kết thúc/cancel một phần upstream nhưng **không chắc** network/database operation vật lý đã dừng tức thì hoặc side effect đã rollback. Fallback có thể che lỗi quan trọng nếu dùng bừa bãi.

Với HTTP streaming, khi client disconnect, downstream WebFlux có thể cancel subscription. Hãy đảm bảo source giải phóng database cursor, file handle, broker consumer và reference trong `doFinally`/resource-managed operators theo đúng signal type.

```java
Flux<Integer> source = Flux.range(1, 100)
    .doFinally(signal ->
        System.out.println("terminated=" + signal));
```

Snippet trên chỉ quan sát khi source có subscription thực, terminal/cancel phụ thuộc cách subscribe/consume. `doFinally` có thể nhận `ON_COMPLETE`, `ON_ERROR` hoặc `CANCEL`; không coi cancel là thành công trong domain logic.

`Subscription.cancel()` phải thread-safe/idempotent và eventually ngừng phát; subscriber vẫn phải sẵn sàng cho một số `onNext` đang in flight ngay sau khi yêu cầu cancel. Không phụ thuộc cancel như một phép kill thread hoặc rollback transaction.

## 22. Reactive Streams khác Java Stream, Future và Iterator

| Khái niệm | Mô hình tiêu thụ | Điểm khác Reactive Streams |
| --- | --- | --- |
| `java.util.Iterator` | Consumer đồng bộ gọi `next()` | Pull trực tiếp, thường blocking nếu lấy data chậm |
| `java.util.stream.Stream` | Pipeline thường đồng bộ trên collection/spliterator | Không phải Reactive Streams Publisher, không có `Subscription.request(n)` |
| `CompletableFuture<T>` | Một kết quả/lỗi eventual | Không biểu diễn tự nhiên 0..N signals/backpressure |
| `Flow.Publisher<T>` | JDK reactive protocol tương tự | Khác package interface với `org.reactivestreams.Publisher` |
| `Mono<T>` | 0..1 asynchronous/synchronous publisher | Reactor type giàu operators |
| `Flux<T>` | 0..N signals, supports demand | Reactor type giàu operators, không luôn có data vô hạn |

`parallelStream()` không phải đường tắt thành WebFlux non-blocking. `CompletableFuture.supplyAsync` có executor semantics riêng và không mặc nhiên có backpressure từ HTTP subscriber. Reactor không thay thế Java Stream trong mọi tác vụ collection CPU nhỏ.

**Reactor không bảo đảm tối ưu mọi throughput**: nguồn event-driven non-blocking giúp sử dụng ít threads hơn khi có nhiều I/O chờ, nhưng code reactive khó debug hơn, có chi phí scheduler/operators và vẫn có bottleneck dữ liệu.

## 23. Những sai lầm phổ biến

1. **Nghĩ `subscribe()` trả Subscription trong spec:** nó trả `void`; Subscription được truyền qua `onSubscribe`.
2. **Không gọi `request(n)`:** source đúng giao thức không gửi `onNext` khi chưa có demand.
3. **Dùng `request(0)` để pause:** sai theo rule 3.9, phải error khi active.
4. **Nghĩ request(2) phải phát đúng hai giá trị:** source có thể phát ít hơn hoặc complete/error.
5. **Phát `onNext` trước `onSubscribe`:** vi phạm protocol.
6. **Phát tiếp sau `onError`/`onComplete`:** vi phạm terminal contract.
7. **Phát `onNext(null)` để biểu thị không có dữ liệu:** không hợp lệ, dùng empty/completion.
8. **Hai luồng đồng thời gọi onNext trên cùng subscriber:** vi phạm serialized signals.
9. **Không hủy subscription không còn dùng:** tốn resources, có thể leak.
10. **Nghĩ cancel dừng tức thì mọi network I/O:** semantics eventually, không bảo đảm rollback.
11. **Nghĩ Flux đồng nghĩa non-blocking:** nguồn bên trong vẫn có thể gọi JDBC/Thread.sleep.
12. **Gọi `block()` ngay trong WebFlux event-loop handler:** chặn event loop hoặc ném lỗi.
13. **Tự `subscribe()` trong controller và trả `Mono.empty()`:** tách producer khỏi HTTP lifecycle.
14. **Nghĩ lambda `subscribe(...)` demo kiểm được backpressure từng phần tử:** thường request unbounded.
15. **Nghĩ Mono chỉ có một giá trị nên không cần terminal/error handling:** Mono có thể empty/error.
16. **Cho rằng mọi Flux là cold và mỗi subscriber nhận đầy đủ dữ liệu:** hot/unicast/replay khác nhau.
17. **Nghĩ operator map/filter/flatMap giữ request(1) upstream:** có thể reshape/pre-fetch.
18. **Tạo buffer không giới hạn cho nguồn nóng:** có thể OOM khi consumer chậm.
19. **Nghĩ client HTTP đọc chậm chỉ làm Publisher chậm, không có buffer:** network/codecs có giới hạn riêng.
20. **Dùng onErrorResume để nuốt mọi lỗi:** có thể che outage và trả business data sai.
21. **Dùng `Mono.just(blockingCall())` và tưởng call lazy:** argument đã được đánh giá khi tạo Mono.
22. **Bọc JDBC vào Mono mà không chọn scheduler phù hợp:** blocking work có thể lên event loop.
23. **Tự viết Publisher không kiểm TCK:** dễ sai demand, cancellation và concurrency.
24. **Dùng StepVerifier initialRequest=0 rồi expectNext ngay:** source không có quyền phát, test có thể chờ.
25. **Nghĩ WebFlux và Servlet MVC luôn chung request context/thread-local:** stack và context khác.
26. **Không kiểm soát resource cleanup trên cancel/disconnect:** giữ cursors, subscriptions, handles.
27. **Đánh đồng backpressure với rate limit/quota:** demand là per-subscription data flow, khác hạn mức API.
28. **Không phân biệt Flow API với org.reactivestreams package:** cần adapter nếu kết hợp trực tiếp.
29. **Bỏ qua operator prefetch/concurrency:** downstream request nhỏ vẫn có thể khiến upstream work lớn.
30. **Nghĩ streaming HTTP event = một TCP packet:** transport framing và codec khác object signals.

## 24. Checklist thực hành và production

### Protocol

- [ ] Publisher phát `onSubscribe` trước signals khác và tối đa một lần?
- [ ] `onNext` luôn non-null và không vượt total requested?
- [ ] Signals serialized, không có terminal thứ hai hoặc signal sau terminal?
- [ ] `request(n)` dương, demand additive, xử lý `Long.MAX_VALUE`/overflow?
- [ ] `request(0)`/âm phát IllegalArgumentException terminal theo spec?
- [ ] `cancel` an toàn, idempotent và eventual cleanup?
- [ ] Đã dùng Reactor built-ins/TCK thay vì tự triển khai Publisher thủ công khi không cần?

### WebFlux/Reactor

- [ ] Controller trả Flux/Mono cho framework subscribe, không tự subscribe?
- [ ] Không dùng blocking I/O trên Netty event-loop; có boundedElastic hoặc reactive driver phù hợp?
- [ ] Không tự giả định cold/hot khi có nhiều subscribers hoặc reconnect?
- [ ] Operator prefetch/concurrency/buffers có giới hạn?
- [ ] Các nguồn nóng có policy khi downstream chậm?
- [ ] Resource cleanup được kiểm trên complete/error/cancel?
- [ ] Timeout không được coi là tự rollback side effect?
- [ ] Security/authentication/authorization và per-user quotas độc lập backpressure?

### Kiểm thử và vận hành

- [ ] Test `request(1)`, request theo đợt, cancellation, empty, error và completion?
- [ ] Dùng `StepVerifier` với initial request rõ khi học demand?
- [ ] Test HTTP SSE/JSON qua `WebTestClient` và `curl -N`?
- [ ] Quan sát buffer/queue, memory, event-loop blocking, dropped events, timeouts?
- [ ] Không lấy thứ tự TCP chunks làm thứ tự business messages?
- [ ] Có tài liệu giới hạn số events/request, idle timeout và reconnect/replay policy cho streaming?
- [ ] Không lộ dữ liệu nhạy cảm qua event stream, logs, trace hoặc error message?

## 25. Câu hỏi tự kiểm tra

**1.** Ai quyết định số phần tử Publisher được phép giao cho Subscriber?

**Đáp án:** Subscriber gọi `Subscription.request(n)`; Publisher không phát `onNext` vượt demand đã được chấp nhận.

**2.** `Publisher.subscribe(s)` trả về kiểu gì theo đặc tả Reactive Streams JVM?

**Đáp án:** `void`. Subscription được truyền qua `Subscriber.onSubscribe(subscription)`.

**3.** Tại sao cần `onSubscribe` trước `onNext`?

**Đáp án:** Subscriber cần nhận Subscription để chủ động request/cancel, tạo một điểm khởi tạo nhất quán.

**4.** `request(2)` rồi `request(3)` có nghĩa gì?

**Đáp án:** Nhu cầu cộng dồn, tối đa thêm năm phần tử được phép phát trừ số đã dùng; không reset thành ba.

**5.** Có thể gọi `onComplete` khi chưa có `request` không?

**Đáp án:** Có. Terminal signals không đòi hỏi demand như onNext.

**6.** `request(0)` có phải pause không?

**Đáp án:** Không; khi Subscription còn active, đó là request không hợp lệ và phải onError(IllegalArgumentException).

**7.** Có thể có `onError` rồi `onComplete` trên cùng một subscription không?

**Đáp án:** Không. Cả hai là terminal signals và loại trừ nhau.

**8.** Reactive Streams có luôn chạy async trên thread khác không?

**Đáp án:** Không. `Flux.range` có thể phát đồng bộ ngay trong lời gọi request; Scheduler là lựa chọn riêng.

**9.** Vì sao `subscribe(System.out::println)` không chứng minh custom backpressure?

**Đáp án:** Những overload đơn giản trong Reactor thường request unbounded; cần `BaseSubscriber` hoặc StepVerifier điều khiển demand.

**10.** `cancel()` có luôn ngắt network call hoặc rollback transaction ngay không?

**Đáp án:** Không. Nó yêu cầu ngừng và cleanup eventual; downstream I/O/transaction có semantics riêng.

**11.** Một final subscriber chỉ request(1) có bảo đảm nguồn upstream chỉ tính đúng một item không?

**Đáp án:** Không. `buffer`, `flatMap`, prefetch có thể reshape demand, mỗi boundary có quy tắc riêng.

**12.** WebFlux controller nên return Flux hay tự gọi subscribe?

**Đáp án:** Trả Flux/Mono. WebFlux infrastructure sẽ subscribe, demand, xử lý response và cancellation.

**13.** Có phải `Mono.just(service.get())` luôn lazy?

**Đáp án:** Không. `service.get()` được đánh giá trước khi truyền vào `just`; dùng defer/fromCallable và scheduler phù hợp nếu cần.

**14.** Java `Flow.Publisher` có thể cast thẳng thành `org.reactivestreams.Publisher` không?

**Đáp án:** Không. Hai interface khác package, cần adapter có chủ đích.

## 26. Nguồn tham khảo

- Reactive Streams JVM specification, API và các quy tắc Publisher/Subscriber/Subscription: https://github.com/reactive-streams/reactive-streams-jvm
- Reactive Streams official website: https://www.reactive-streams.org/
- Spring Framework — Spring WebFlux overview: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Spring Framework — Reactive Core và HTTP backpressure: https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html
- Spring Framework — WebFlux annotated controllers: https://docs.spring.io/spring-framework/reference/web/webflux/controller.html
- Project Reactor Core reference, `subscribe`, `BaseSubscriber` và demand: https://projectreactor.io/docs/core/release/reference/
- Project Reactor Core reference — backpressure and request reshaping: https://projectreactor.io/docs/core/release/reference/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html
- Project Reactor — testing `StepVerifier`: https://projectreactor.io/docs/core/release/reference/testing.html
- Project Reactor — schedulers and threading: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html
- Java 17 `java.util.concurrent.Flow`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.html
- Java 17 `SubmissionPublisher`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SubmissionPublisher.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-foundations-002">Mono, Flux và ý nghĩa của không có, một hoặc nhiều giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Mono, Flux và ý nghĩa của không có, một hoặc nhiều giá trị trong Spring WebFlux

## 1. Mục tiêu của bài học

Khi bắt đầu học Spring WebFlux, hai kiểu dễ gặp nhất là `Mono<T>` và `Flux<T>`. Chúng có vẻ như “một object” và “một List” nhưng cách hiểu đó **không chính xác**. Cả hai đều là **Publisher** theo Reactive Streams, mô tả quá trình phát tín hiệu theo thời gian. Chúng biểu diễn **khả năng nhận một chuỗi giá trị**, không đơn thuần là một object đã có sẵn trong bộ nhớ.

- `Mono<T>`: phát **0 hoặc 1** giá trị kiểu `T`, sau đó kết thúc thành công, **hoặc** phát tín hiệu lỗi. Mono cũng có thể không kết thúc nếu publisher không bao giờ phát terminal signal.
- `Flux<T>`: phát **0 đến N** giá trị kiểu `T` (N có thể rất lớn hoặc không giới hạn), sau đó kết thúc thành công, **hoặc** phát lỗi. Publisher dài hạn có thể tiếp tục cho tới khi bị hủy.
- **Không có giá trị** trong reactive stream là một trạng thái có nghĩa: `onComplete` mà không có `onNext`, không phải Java `null` được phát như một giá trị.
- **Một giá trị** là một tín hiệu `onNext(value)`; khi sequence kết thúc thành công sẽ có `onComplete`.
- **Nhiều giá trị** là nhiều `onNext(value)` trước một terminal signal; cần quan tâm tới demand, cancellation và backpressure.

Đây là chủ đề nền tảng của WebFlux vì controller, WebClient, reactive repository và streaming API đều phải chọn một kiểu phản ánh **cardinality** thật sự của kết quả.

### Sau bài này bạn sẽ làm được

1. Đọc chuỗi tín hiệu Reactive Streams `onSubscribe → onNext* → (onComplete | onError)` và phân biệt **empty**, **single**, **multiple**, **error**, **never**.
2. Chọn `Mono<T>`, `Mono<Void>`, `Flux<T>` và `Mono<List<T>>` phù hợp với nghiệp vụ HTTP.
3. Hiểu các factory methods: `Mono.just`, `justOrEmpty`, `empty`, `error`, `fromCallable`, `defer`; `Flux.just`, `fromIterable`, `range`, `empty`, `error`.
4. Dùng `map`, `flatMap`, `filter`, `switchIfEmpty`, `defaultIfEmpty`, `collectList`, `next`, `single`, `singleOrEmpty`, `then`, `onErrorResume` mà không đánh mất ý nghĩa số lượng kết quả.
5. Phân biệt **assembly** với **subscription**, cold/hot publisher, backpressure, cancellation, threading và ảnh hưởng của việc `block()`.
6. Tạo Spring Boot WebFlux API thực tế với endpoints trả Mono và Flux, trả 404 khi Mono trống, JSON array và SSE.
7. Dùng `StepVerifier` và `WebTestClient` để kiểm tra giá trị, completion, error, 404 và HTTP streaming.

**Bối cảnh:** Java 17+, Spring Framework 6.x / Spring Boot 3.x, Reactor Core 3.x và Spring WebFlux. API của `reactor.core.publisher.Mono`/`Flux` không phải Java `Optional`, `Stream`, `Future`, hay `java.util.concurrent.Flow.Publisher` dù các abstractions có những ý tưởng liên quan.

## 2. Reactive Streams: không chỉ dữ liệu, mà còn có tín hiệu

`Mono` và `Flux` của Project Reactor triển khai `org.reactivestreams.Publisher<T>`. Một downstream `Subscriber<T>` đăng ký với Publisher, nhận `Subscription` và có thể yêu cầu dữ liệu theo một số lượng `request(n)`.

Giao thức tín hiệu cơ bản:

```text
Publisher<T>
     │
     │ subscriber.subscribe(...)
     ▼
onSubscribe(subscription)
     │
     ├── subscription.request(n)  ← downstream yêu cầu dữ liệu
     │
     ├── onNext(item #1)
     ├── onNext(item #2)
     ├── ...
     │
     └── một trong hai:
           onComplete()   ← thành công và không còn dữ liệu
           onError(err)   ← lỗi terminal
```

Một chuỗi tuân thủ Reactive Streams không được phát thêm `onNext` sau terminal signal; `onComplete` và `onError` loại trừ nhau. Subscriber cũng có thể gọi `cancel()` để hủy; cancellation không nên bị diễn giải như một sự kiện nghiệp vụ thành công.

### 2.1. Mono có bao nhiêu onNext?

**Mono rỗng:**

```text
onSubscribe
onComplete
          → 0 onNext
```

**Mono có một giá trị:**

```text
onSubscribe
onNext("OK")
onComplete
          → 1 onNext
```

**Mono bị lỗi:**

```text
onSubscribe
onError(IllegalStateException)
          → 0 onNext, kết thúc thất bại
```

`Mono<T>` có tối đa một `onNext` thành công. Một `Mono.never()` thì không phát onNext, onComplete hay onError cho tới lúc bị cancel — vì thế “0..1” nói về **số phần tử có thể phát**, không cam kết thời gian kết thúc.

### 2.2. Flux có bao nhiêu onNext?

```text
Flux.empty()        onSubscribe → onComplete

Flux.just("A")      onSubscribe → onNext(A) → onComplete

Flux.just("A","B")  onSubscribe → onNext(A) → onNext(B) → onComplete

Flux.error(ex)      onSubscribe → onError(ex)

Flux.interval(...)  onSubscribe → onNext(0) → onNext(1) → ...
                    (có thể chạy không giới hạn nếu không take/cancel)
```

`Flux` không mặc nhiên có nhiều phần tử. `Flux.empty()` là hoàn toàn hợp lệ, và `Flux.just(1)` cũng là Flux. Khác biệt quan trọng là **contract**: loại này cho phép nhiều phần tử; không phải thực tế mọi subscription đều nhận nhiều giá trị.

### 2.3. Các trạng thái không đồng nghĩa với nhau

| Trạng thái | Có onNext? | Terminal signal? | Ý nghĩa |
| --- | ---: | --- | --- |
| `Mono.empty()` | 0 | `onComplete` | Thành công, không có item |
| `Mono.just(value)` | 1 | `onComplete` | Thành công, có một item |
| `Mono.error(ex)` | 0 | `onError` | Thất bại |
| `Mono.never()` | 0 | Chưa có | Pending vô thời hạn nếu không bị hủy |
| `Flux.empty()` | 0 | `onComplete` | Sequence rỗng |
| `Flux.just(a,b)` | 2 | `onComplete` | Hai phần tử |
| `Flux.error(ex)` | 0 | `onError` | Sequence lỗi |
| `Flux.just(a).concatWith(Mono.error(ex))` | 1 | `onError` | Đã có item rồi mới gặp lỗi |

**Chú ý:** `Flux` có thể phát một số item rồi mới lỗi. `Mono` không phát item rồi mới lỗi ở một subscription tuân thủ hợp đồng: sau một item, Mono thành công và kết thúc. Khi làm HTTP streaming, lỗi giữa chừng có thể xảy ra sau khi headers/body đã bắt đầu gửi; không phải lúc nào cũng đổi được sang HTTP 500 nữa.

## 3. Mono.empty() không phải Mono.just(null)

Đây là điểm cần nắm ngay vì Reactor **không cho phép null như một phần tử `onNext`**.

```java
Mono<String> present = Mono.just("hello");
Mono<String> absent  = Mono.empty();
Mono<String> nullable = Mono.justOrEmpty((String) null);
```

- `present` phát một string và hoàn tất.
- `absent` hoàn tất không phát string nào.
- `nullable` cũng là Mono rỗng.
- `Mono.just(null)` **ném `NullPointerException` ngay khi gọi factory**; không tạo được Mono hợp lệ chứa null.

Nếu nguồn synchronous trả về null và bạn muốn chuyển thành Mono empty, dùng `Mono.justOrEmpty(possiblyNull)` hoặc `Mono.fromCallable(() -> possiblyNull)` theo ngữ nghĩa API. Đừng ghép `map(x -> null)` để loại item: Reactor sẽ báo lỗi vì mapper phải trả non-null.

### 3.1. Mono<Void> mang ý nghĩa gì?

`Mono<Void>` thường đại diện **công việc không có giá trị trả về**, chỉ cần biết thành công hoặc thất bại:

```java
Mono<Void> saveOrDelete = repository.deleteById(42L);
```

Hợp đồng:

```text
thành công: onSubscribe → onComplete
thất bại:   onSubscribe → onError(exception)
```

Nó **không** có một object `Void` để subscriber sử dụng. Vì vậy:

```java
Mono<Void> done = Mono.empty();

done.map(v -> "mapped"); // lambda sẽ KHÔNG chạy
```

Nếu muốn thực hiện bước tiếp theo sau completion, dùng `then(...)` hoặc `thenReturn(value)`:

```java
Mono<String> result =
        repository.deleteById(42L)
                  .thenReturn("deleted");
```

Với API HTTP, `Mono<Void>` là biểu diễn phổ biến của completion không payload; controller cần xác định đúng status (thường `204 No Content` cho DELETE thành công). Không giả định `Mono<Void>` tự mang HTTP 204; status do Spring handler/ResponseEntity và configuration quyết định.

### 3.2. Mono.empty và Optional.empty

Một `Optional.empty()` là **giá trị Java hiện có ngay** cho biết không có object. `Mono.empty()` là một **Publisher**, có thể được subscribe rồi phát completion (có thể asynchronous). Chuyển Optional sang Mono:

```java
Optional<String> maybeName = repository.findNameById(42L);
Mono<String> name = Mono.justOrEmpty(maybeName);
```

Đây là đoạn minh họa từ synchronous repository; nếu repository truy vấn blocking thì việc **gọi `findNameById` đã chạy trước** khi tạo Mono. Bọc nó trong Mono sau đó không khiến DB call tự trở thành non-blocking. Muốn trì hoãn việc truy vấn, xem `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` ở phần threading.

## 4. Chương trình Java 17 độc lập: các trường hợp cơ bản

Dùng Maven Reactor Core nếu chạy trong một project Spring Boot có `spring-boot-starter-webflux`. Chương trình có thể đặt trong `src/main/java/example/ReactorCardinalityDemo.java`. Nó không cần HTTP server.

```java
package example;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactorCardinalityDemo {

    public static void main(String[] args) {
        System.out.println("Mono present:");
        Mono.just("A")
                .subscribe(
                    value -> System.out.println("next=" + value),
                    err -> System.out.println("error=" + err),
                    () -> System.out.println("complete"));

        System.out.println("Mono empty:");
        Mono.<String>empty()
                .subscribe(
                    value -> System.out.println("next=" + value),
                    err -> System.out.println("error=" + err),
                    () -> System.out.println("complete"));

        System.out.println("Flux several:");
        Flux.just(10, 20, 30)
                .subscribe(
                    value -> System.out.println("next=" + value),
                    err -> System.out.println("error=" + err),
                    () -> System.out.println("complete"));
    }
}
```

Vì cả ba publishers là **synchronous cold factories**, không có `delayElement`, `publishOn` hay asynchronous source, stdout theo thứ tự:

```text
Mono present:
next=A
complete
Mono empty:
complete
Flux several:
next=10
next=20
next=30
complete
```

**Cẩn thận:** nếu thay bằng `Flux.interval`, WebClient hoặc producer async, `subscribe()` thường trả quyền điều khiển nhanh và output có thể đến sau; không dùng chương trình in console ngay lập tức rồi nghĩ Publisher “không phát gì”.

### 4.1. Không có subscriber thì sao?

`Mono.just("x").map(...)` tạo một pipeline/description; các operators thường **chưa xử lý dữ liệu** cho tới khi có subscription. `Mono.just(...)` bản thân nhận sẵn object tại lúc assembly, vì vậy **giá trị đưa vào `just` phải được tính trước**; không được nói mọi side effect trong mọi source luôn đợi subscribe.

Ví dụ:

```java
AtomicInteger calls = new AtomicInteger();

Mono<Integer> lazy =
    Mono.fromCallable(calls::incrementAndGet);

System.out.println(calls.get()); // 0
System.out.println(lazy.block()); // 1
System.out.println(lazy.block()); // 2
```

Ở đây `Mono.fromCallable` trì hoãn việc gọi function tới subscription, còn `block()` chủ động subscribe và chờ kết quả. **Chỉ dùng `block()` trong main/test/ranh giới imperative thích hợp, không dùng trong WebFlux request event-loop để gọi chain reactive.**

## 5. Tạo Mono và Flux: chọn đúng factory

### 5.1. Các cách tạo Mono

| Factory | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `Mono.just(value)` | Có sẵn một giá trị non-null | `Mono.just("ok")` |
| `Mono.justOrEmpty(valueOrOptional)` | Một giá trị hoặc không có | `Mono.justOrEmpty(optional)` |
| `Mono.empty()` | Complete ngay, 0 item | `Mono.<String>empty()` |
| `Mono.error(ex)` | Terminal error | `Mono.error(new IllegalStateException("x"))` |
| `Mono.defer(supplierOfMono)` | Tạo Mono mới khi subscribe | `Mono.defer(() -> Mono.just(clockValue()))` |
| `Mono.fromCallable(callable)` | Gọi Callable khi subscribe, null → empty | `Mono.fromCallable(() -> blockingRead())` |
| `Mono.fromSupplier(supplier)` | Gọi Supplier khi subscribe | `Mono.fromSupplier(() -> "hello")` |
| `Mono.never()` | Không tự emit/complete/error | `Mono.<String>never()` |
| `Mono.delay(duration)` | Phát một `Long` sau delay | `Mono.delay(Duration.ofSeconds(1))` |

**Không dùng `Mono.just(expensiveCall())` để trì hoãn `expensiveCall()`**, vì biểu thức argument được tính trước khi factory được gọi. Dùng `Mono.fromCallable(...)` hoặc `Mono.defer(...)` nếu cần lazy semantics.

### 5.2. Các cách tạo Flux

| Factory | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `Flux.just(a,b,c)` | Một dãy giá trị đã có | `Flux.just(1,2,3)` |
| `Flux.fromIterable(iterable)` | Duyệt collection khi subscribe | `Flux.fromIterable(List.of(1,2))` |
| `Flux.range(start,count)` | Dãy số nguyên hữu hạn | `Flux.range(5,3)` phát 5,6,7 |
| `Flux.empty()` | Hoàn tất không có item | `Flux.<String>empty()` |
| `Flux.error(ex)` | Terminal error | `Flux.error(new IllegalStateException())` |
| `Flux.defer(factory)` | Tạo publisher tại subscription | `Flux.defer(() -> fetchCurrentItems())` |
| `Flux.interval(duration)` | Phát số tăng dần theo nhịp scheduler | `Flux.interval(Duration.ofSeconds(1))` |
| `Flux.from(publisher)` | Adapt Publisher | `Flux.from(otherPublisher)` |
| `Flux.generate(...)` | Stateful synchronous sink 1 item mỗi vòng | Cần state/termination rõ |
| `Flux.create(...)` | Bridge callback producer | Cần xử lý demand/backpressure/cancellation |

**Không mặc định dùng `Flux.create` hoặc `Sinks` cho mọi collection.** Với danh sách sẵn có, `Flux.fromIterable` đơn giản và an toàn hơn nhiều.

### 5.3. Flux.range: count, không phải endInclusive

`Flux.range(5, 3)` phát 5, 6, 7. Tham số thứ hai là **số phần tử**, không phải giá trị kết thúc. Với `Flux.range(1, 0)`, Publisher hoàn tất không phát phần tử.

### 5.4. Tạo nguồn khi subscribe để phản ánh dữ liệu mới

`Mono.just(service.readNow())` chạy `readNow()` lúc assembly. Nếu đó là một truy vấn DB blocking, bạn đã block ngay tại điểm tạo Mono.

`Mono.defer(() -> Mono.justOrEmpty(repository.findById(id)))` trì hoãn cuộc gọi đến lúc subscription, nhưng **vẫn blocking** nếu repository là JDBC/JPA synchronous. Muốn ngăn blocking trên event loop, phải dùng driver non-blocking (như R2DBC) hoặc offload tạm thời qua scheduler phù hợp. “Lazy” và “non-blocking” không phải một khái niệm.

## 6. Operators quan trọng theo số lượng giá trị

### 6.1. map: 1 → 1 với mỗi item

```java
Flux<Integer> doubled = Flux.just(1, 2, 3)
        .map(n -> n * 2);
// onNext 2, 4, 6; onComplete
```

Với Mono:

```java
Mono<String> upper =
        Mono.just("spring").map(String::toUpperCase);
// SPRING
```

Nếu nguồn là `Mono.empty()`, lambda của `map` **không chạy**. `map` xử lý item; nó không chạy khi chỉ có onComplete.

### 6.2. filter: item có thể biến mất

```java
Mono<Integer> removed = Mono.just(3)
        .filter(n -> n % 2 == 0);
// Kết quả: Mono.empty() vì 3 không qua predicate.
```

`Flux.just(1,2,3).filter(n -> n > 2)` chỉ còn 3. Một kết quả rỗng sau `filter` **không phải lỗi**.

### 6.3. flatMap: mỗi item mở ra một Publisher

```java
Mono<String> name =
        Mono.just(42L)
            .flatMap(id -> reactiveRepository.findNameById(id));
```

Khi service trả `Mono<String>`, dùng `flatMap` để tránh `Mono<Mono<String>>`. Với Flux, `flatMap` có thể **trộn kết quả, thay đổi thứ tự hoàn thành** của các inner publishers; dùng `concatMap` nếu cần bảo toàn thứ tự và tuần tự hóa inner subscriptions.

Ví dụ:

```java
Flux<String> processed = Flux.just("A", "B")
        .flatMap(code -> webClient.get()
                .uri("/lookup/{code}", code)
                .retrieve()
                .bodyToMono(String.class));
```

Đây là **snippet minh họa cần WebClient được cấu hình**, không phải class standalone. Không dựa vào thứ tự output A rồi B với `flatMap` khi hai HTTP calls có latency khác nhau.

### 6.4. switchIfEmpty và defaultIfEmpty

```java
Mono<String> fallbackValue =
        Mono.<String>empty()
            .defaultIfEmpty("unknown");

Mono<String> fallbackPublisher =
        Mono.<String>empty()
            .switchIfEmpty(Mono.just("from-backup"));
```

Cả hai có thể phát một item fallback rồi complete. Khác biệt:

- `defaultIfEmpty(value)` nhận **một value non-null**, thích hợp với hằng/đối tượng có sẵn.
- `switchIfEmpty(otherMono)` chuyển sang **publisher khác** nếu upstream complete empty; publisher fallback có thể lazy, gọi DB khác, trả empty hoặc error.
- `onErrorResume` xử lý **error**, không xử lý empty.
- `switchIfEmpty` xử lý **empty**, không tự bắt exception của upstream.

Nếu fallback cần đọc tài nguyên tốn kém, dùng `Mono.defer`:

```java
Mono<Item> source = repository.findById(id);

Mono<Item> selected = source.switchIfEmpty(
        Mono.defer(() -> backupRepository.findById(id)));
```

*Các repository là dependency minh họa; code cần được đặt trong method và có kiểu Item thực tế. Chỉ gọi backup khi source rỗng và fallback được subscribe.*

### 6.5. collectList: Flux<T> → Mono<List<T>>

```java
Mono<List<Integer>> numbers =
        Flux.just(1, 2, 3).collectList();
```

Khi subscription kết thúc thành công, Mono phát **một danh sách** chứa 3 phần tử, rồi complete.

Đặc biệt:

```java
Mono<List<Integer>> emptyList =
        Flux.<Integer>empty().collectList();
```

Kết quả **không phải Mono.empty()**. Nó phát **một giá trị List rỗng** (`[]`) rồi complete.

Đây là sự khác biệt cardinality rất quan trọng:

```text
Flux.empty():
  onComplete                  (0 items)

Flux.empty().collectList():
  onNext([]) → onComplete      (1 item: một List rỗng)
```

Khi dùng `collectList` trên Flux vô hạn hoặc chưa complete, nó phải tích lũy tới khi complete: **có nguy cơ giữ toàn bộ dữ liệu trong heap mãi mãi**. Không dùng `collectList()` cho stream không giới hạn nếu chưa có `take`, `timeout` hoặc cửa sổ hữu hạn rõ ràng.

### 6.6. next, single, singleOrEmpty

Đây là các chuyển đổi từ Flux nhiều giá trị sang Mono và có semantics khác nhau.

| Operator | Flux rỗng | Flux có 1 item | Flux có từ 2 item |
| --- | --- | --- | --- |
| `next()` | Mono.empty | Item đầu rồi complete | Chỉ item đầu, cancel/không cần phần còn lại |
| `single()` | Error (không đúng 1 item) | Mono có item | Error (quá 1 item) |
| `singleOrEmpty()` | Mono.empty | Mono có item | Error (quá 1 item) |
| `collectList()` | Mono phát `[]` | Mono phát `[x]` | Mono phát `[x,y,...]` |
| `count()` | Mono phát 0L | Mono phát 1L | Mono phát số phần tử |

**Lỗi thường gặp:** dùng `next()` để xác nhận chỉ có **một** kết quả DB. `next()` chỉ lấy item đầu, không kiểm tra có item thứ hai hay không. Nếu nghiệp vụ yêu cầu duy nhất chính xác, dùng `single()` hoặc repository query có unique constraint và phương thức reactive phù hợp.

### 6.7. then và thenReturn: chỉ quan tâm completion

```java
Mono<Void> saveCompleted =
        repository.save(entity).then();
```

`then()` bỏ qua giá trị trước đó và tạo `Mono<Void>` biểu diễn complete/error.

```java
Mono<String> greetingAfterSave =
        repository.save(entity)
                  .thenReturn("saved");
```

Khi source complete thành công, `thenReturn` phát giá trị cố định; khi source lỗi, lỗi propagate. Không có gì “đã lưu” chỉ vì đã tạo Publisher — vẫn cần subscribe (trong WebFlux, framework thực hiện subscribe cho return value của handler).

### 6.8. zip: cần giá trị từ mỗi nguồn

```java
Mono<Tuple2<String, Integer>> combined =
        Mono.zip(Mono.just("A"), Mono.just(10));
```

Kết quả là **một tuple**, sau đó complete.

Tuy nhiên:

```java
Mono<Tuple2<String, Integer>> noTuple =
        Mono.zip(Mono.just("A"), Mono.<Integer>empty());
```

Result hoàn tất **rỗng**: nếu một bên không phát item thì không đủ giá trị để ghép. Với zipped chains phức tạp, có thể xảy ra short-circuit và không phải tất cả nguồn đều được subscribe; không dùng zip như “cơ chế chắc chắn chạy side effects của mọi nguồn”. Chọn `defaultIfEmpty` hoặc redesign pipeline nếu empty là trạng thái chấp nhận được.

## 7. Empty không đồng nghĩa HTTP 404 trong WebFlux

Một method của `@RestController`:

```java
@GetMapping("/api/items/{id}")
Mono<Item> get(@PathVariable long id) {
    return repository.findById(id);
}
```

Nếu `repository.findById(id)` trả `Mono.empty()`, framework không có item để serialize. **Không mặc nhiên được bảo đảm 404** chỉ vì Publisher rỗng. Nếu HTTP API contract yêu cầu “không tìm thấy → 404”, trả `Mono<ResponseEntity<Item>>` và xử lý rõ:

```java
@GetMapping("/api/items/{id}")
Mono<ResponseEntity<Item>> get(@PathVariable long id) {
    return repository.findById(id)
            .map(item -> ResponseEntity.ok(item))
            .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

*Đoạn minh họa có repository/Item được inject; code hoàn chỉnh ở mục 12.*

Phân biệt các kết quả:

| Reactive value | Ý nghĩa ở tầng dữ liệu | Contract HTTP cần quyết định |
| --- | --- | --- |
| `Mono.empty()` | Không có item | Có thể là body rỗng, 404, 204... tùy endpoint |
| `Mono.just(item)` | Có một item | Thường 200 + JSON item |
| `Flux.empty()` | Tập hợp rỗng | Thường 200 + `[]` với JSON array |
| `Mono.just(List.of())` | Có **một danh sách rỗng** | Thường 200 + `[]` |
| `Mono.error(ex)` | Truy xuất thất bại | Error handling quyết định 4xx/5xx |
| `Mono<Void>` complete | Tác vụ không có payload | Có thể 204 khi API định nghĩa no-content |

**Không dùng empty để che giấu lỗi database.** “Không tìm thấy bản ghi” có thể là empty; “không kết nối được DB” là error và cần xử lý đúng. Nếu biến mọi error thành empty, client sẽ tưởng dữ liệu không tồn tại dù sự cố hạ tầng đang xảy ra.

### 7.1. Mono<ResponseEntity<T>> khác ResponseEntity<Mono<T>>

WebFlux hỗ trợ các kiểu:

```java
Mono<ResponseEntity<Item>>
ResponseEntity<Mono<Item>>
ResponseEntity<Flux<Item>>
```

Điểm khác:

- `Mono<ResponseEntity<Item>>`: **status, headers và body** có thể được quyết định **sau** khi một Mono resolve. Hợp với trường hợp cần `404` nếu empty.
- `ResponseEntity<Mono<Item>>`: status/headers được chọn ngay, body sẽ đến async; không thuận tiện nếu status phải đổi theo việc body có item hay không.
- `ResponseEntity<Flux<Item>>`: status/headers đã chọn, body nhiều items đến theo thời gian. Khi response đã commit rồi gặp error, không thể tùy ý đổi status.

Chọn type theo **thời điểm biết status**, không chỉ do coder thích Mono bọc ở ngoài hay bên trong.

## 8. Mono và Flux trong annotated WebFlux controllers

Các ví dụ thường gặp:

### 8.1. Mono<Item> cho detail

```java
@GetMapping("/api/items/{id}")
Mono<ResponseEntity<Item>> item(@PathVariable long id) {
    return service.find(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

### 8.2. Flux<Item> cho danh sách

```java
@GetMapping("/api/items")
Flux<Item> items() {
    return service.all();
}
```

Với `application/json`, một finite Flux nhiều giá trị thường được serialize như JSON array; client có thể nhận sau khi encoding/buffering tùy writer/streaming media type. Không khẳng định “Flux là streaming từng item ngay lập tức” trong mọi content type.

### 8.3. Flux<ServerSentEvent<Item>> cho streaming

```java
@GetMapping(
        value = "/api/items/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<ServerSentEvent<Item>> events() {
    return service.all()
            .map(item -> ServerSentEvent.<Item>builder()
                    .event("item")
                    .data(item)
                    .build());
}
```

Với SSE (`text/event-stream`), mỗi item có thể được gửi/flush như một event; tránh `collectList` nếu muốn giữ semantics streaming và backpressure. Client disconnect có thể phát cancellation; dùng `doFinally` để cleanup resources chứ không đợi `onComplete` mãi.

### 8.4. Mono<Void> cho operation không payload

```java
@DeleteMapping("/api/items/{id}")
Mono<ResponseEntity<Void>> delete(@PathVariable long id) {
    return service.delete(id)
            .thenReturn(ResponseEntity.noContent().build());
}
```

*Snippet minh họa, cần service.delete(id) có contract rõ (không tồn tại ID thì 204 hay 404?)*. Nếu method delete là `Mono<Void>`, `thenReturn` phát ResponseEntity sau khi complete. Nhưng nếu muốn status 404 cho ID không tồn tại, phải dùng business decision riêng; `Mono<Void>` không tự mang thông tin đó.

### 8.5. Body publisher từ request

Với WebFlux:

```java
@PostMapping("/api/items")
Mono<ResponseEntity<Item>> create(
        @RequestBody Mono<CreateItem> requestBody) {

    return requestBody
            .flatMap(service::create)
            .map(saved -> ResponseEntity
                    .created(URI.create("/api/items/" + saved.id()))
                    .body(saved));
}
```

Đây là snippet minh họa, cần classes/service/URI imports. Nếu request body empty, `flatMap` không chạy và Mono ngoài cũng empty trừ khi thêm `switchIfEmpty` để quy định 400. Với input validation phức tạp, dùng validation support và trả lỗi có schema ổn định; không mong `flatMap` tự xử lý null/empty.

## 9. Assembly, subscription, cold và hot

### 9.1. Phân biệt assembly time với execution time

```java
AtomicInteger counter = new AtomicInteger();

Mono<Integer> lazy = Mono.fromCallable(counter::incrementAndGet);

System.out.println(counter.get()); // 0
lazy.subscribe();
System.out.println(counter.get()); // 1
lazy.subscribe();
System.out.println(counter.get()); // 2
```

Với synchronous `fromCallable`, hai subscriptions dẫn tới hai lần gọi Callable. Trong WebFlux, **không tự gọi `subscribe()` bên trong controller cho Publisher sẽ trả về**, nếu muốn kết quả đó trở thành HTTP response. Trả Publisher và để WebFlux subscribe; tự subscribe có thể tạo thêm execution song song, double DB writes, mất error handling và cancellation.

### 9.2. Cold publisher

Phần lớn factories `Mono.just`, `Mono.defer`, `Flux.fromIterable` biểu diễn nguồn có thể phát lại cho từng subscriber; mỗi subscription có own lifecycle. `Mono.just(value)` đã capture value lúc assembly, nên không chạy lại expression đã tính để tạo value. Ngược lại `Mono.defer` tạo source mới khi subscribe.

`Flux.range(1,3)` là cold:
 
```text
Subscriber A: 1 → 2 → 3 → complete
Subscriber B: 1 → 2 → 3 → complete
```

### 9.3. Hot publisher

Hot source có thể phát khi đang có subscribers, không nhất thiết replay quá khứ cho người đến sau. Các ví dụ gồm message/event bus được bridge vào Reactor qua `Sinks.Many<T>`. Khi dùng `Sinks.many().multicast()`, cần hiểu behavior của nhiều subscribers, buffer và overflow; backpressure của external producer có thể không tự nhiên khớp tốc độ consumer.

Một đoạn minh họa, không phải service hoàn chỉnh:

```java
Sinks.Many<String> sink = Sinks.many()
        .multicast()
        .onBackpressureBuffer();

Flux<String> events = sink.asFlux();
// Publisher có thể chia sẻ emissions cho subscribers.
// Cần xử lý EmitResult, buffer policy và cancellation.
```

Dùng `tryEmitNext(value)` và kiểm tra `Sinks.EmitResult` thay vì giả định mọi emit sẽ thành công. Hot streams có nguy cơ bỏ lỡ sự kiện, tràn buffer hoặc khiến client chậm kéo ảnh hưởng các subscribers. Thiết kế replay/durability tách biệt với việc chọn Flux.

### 9.4. cache, share, replay và sự thật về số lần gọi DB

Các operators có thể đổi cách chia sẻ:

- `Mono.cache()` cache terminal result (giá trị, empty hoặc error theo API semantics) cho subscriptions sau, có TTL overloads.
- `Flux.share()` tạo sharing behavior phù hợp hot subscription cho nhiều subscribers.
- `Flux.replay(n)`/`ConnectableFlux` hỗ trợ replay theo số phần tử (và biến thể).
- Các lựa chọn này có memory/TTL/error semantics cần kiểm soát kỹ.

**Đừng bật cache() vô hạn cho response cá nhân hoặc dữ liệu biến động** nếu chưa xét invalidation và privacy; reactive cache không thay authentication, transaction hoặc distributed cache design.

## 10. Backpressure: Flux 0..N phải tôn trọng tốc độ tiêu thụ

Reactive Streams có cơ chế **demand**. Subscriber nhận `Subscription` rồi gọi `request(n)` để yêu cầu số item tối đa tiếp theo. Nếu producer tôn trọng demand, nó không flood consumer không giới hạn.

Ví dụ:

```java
Flux.range(1, 5)
    .subscribe(new BaseSubscriber<Integer>() {
        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            request(2);
        }

        @Override
        protected void hookOnNext(Integer value) {
            System.out.println(value);
            request(1);
        }
    });
```

*Snippet cần imports `BaseSubscriber` và `org.reactivestreams.Subscription`. Mục đích là mô tả demand theo API; với cơ chế này eventual output là 1..5 rồi complete, nếu không bị cancel.*

### 10.1. subscribe() thường request vô hạn

Các overloads như `subscribe(value -> ...)` và `block()` phổ biến thường request **unbounded** (`Long.MAX_VALUE`). Không dùng việc “đã gọi subscribe” làm bằng chứng rằng client tiêu thụ có giới hạn.

Trong WebFlux, reactive HTTP integration sẽ quản lý subscription và flow control theo WebFlux/Netty và từng writer; ứng dụng vẫn phải kiểm soát upstream DB/WebClient và consumer làm việc đắt đỏ.

### 10.2. Backpressure không tự bảo vệ mọi producer

- Nguồn synchronous `Flux.range`, `Flux.fromIterable` thường tôn trọng request demand.
- Nguồn push kiểu callback/hot events có thể phát nhanh hơn, cần buffering/dropping/latest/error strategy hoặc upstream flow control.
- `onBackpressureBuffer()` có thể tốn heap nếu producer nhanh hơn consumer trong thời gian dài.
- `limitRate(n)` điều chỉnh demand downstream-upstream theo batch, không biến database/remote broker thành zero-cost.
- `buffer(n)` biến nhiều items thành một `List<T>` mỗi batch; không biến mỗi batch thành complete stream.
- `take(n)` giới hạn số items và thường hủy upstream sau khi đủ số item.

### 10.3. Cancellation

Trong HTTP streaming, khi client ngắt kết nối, subscription có thể bị cancel. Thiết kế cleanup:

```java
Flux<Item> stream = repository.streamItems()
    .doFinally(type -> {
        // cleanup/metrics nhẹ khi complete, error hoặc cancel;
        // đừng block event loop.
    });
```

*Ví dụ minh họa cần repository thực. `doFinally` nhận `SignalType`; tránh lập luận rằng mọi network disconnection có thể được phát hiện ngay lập tức ở mọi proxy.*

Không đặt logic release resource chỉ trong `doOnComplete`: nếu pipeline lỗi hoặc cancel, callback đó không chạy. Dùng `usingWhen`/`doFinally` phù hợp lifecycle và bản chất resource.

## 11. Threading: reactive không đồng nghĩa chạy nhiều thread

`Mono.just(1).map(i -> i + 1)` thường chạy synchronous trên thread thực hiện subscribe. Việc dùng Mono/Flux **không tự làm task chạy nền**. Sự thay đổi thread chỉ xảy ra nếu source đã async, framework/scheduler/driver quyết định, hoặc bạn đặt `subscribeOn`/`publishOn`.

### 11.1. subscribeOn và publishOn

- `subscribeOn(scheduler)` tác động tới quá trình subscription/upstream execution (vị trí tương đối trong chain có semantics riêng).
- `publishOn(scheduler)` chuyển execution context cho các operators **sau nó**.
- Không đặt `publishOn` bừa bãi để “chữa blocking” nếu trước đó đã block event-loop.

Ví dụ:

```java
Mono<String> value =
    Mono.fromCallable(() -> legacyJdbc.queryOne())
        .subscribeOn(Schedulers.boundedElastic());
```

Đây là cầu nối tạm cho call synchronous **có giới hạn**. `boundedElastic` thiết kế cho blocking work tương đối phù hợp hơn event loop, nhưng không biến JDBC thành non-blocking I/O và có giới hạn/queue. Cần set timeout, connection pool và tránh quá tải. Nếu cả data stack đã reactive, ưu tiên driver non-blocking (ví dụ R2DBC) và không dùng wrapper blocking không cần thiết.

### 11.2. Vì sao .block() nguy hiểm trong WebFlux handler?

Trong request xử lý bởi Reactor Netty event-loop, `block()`/`blockFirst()`/`blockLast()` có thể gây chặn event-loop hoặc Reactor báo `IllegalStateException` trên các non-blocking threads. Một ít request chặn có thể khiến nhiều kết nối bị ảnh hưởng.

**Sai:**

```java
@GetMapping("/wrong")
String wrong() {
    return webClient.get().uri("/remote")
        .retrieve().bodyToMono(String.class).block();
}
```

**Đúng hơn:**

```java
@GetMapping("/right")
Mono<String> right() {
    return webClient.get().uri("/remote")
        .retrieve().bodyToMono(String.class);
}
```

Đây là hai snippets minh họa, cần `WebClient` instance được inject. Thực tế production còn cần deadlines, status handling, circuit breaker/backoff và connection pooling. Trong unit test hoặc command-line boundary, `block()` có thể chấp nhận khi mục đích chủ động chuyển về synchronous world.

### 11.3. Reactor Context thay vì ThreadLocal mù quáng

Reactive chain có thể chuyển thread; `ThreadLocal`/MDC không tự được bảo đảm có cùng giá trị ở mọi operator. Reactor cung cấp **Context** per subscriber:

```java
Mono<String> trace =
    Mono.deferContextual(ctx ->
            Mono.just(ctx.getOrDefault("traceId", "absent")))
        .contextWrite(Context.of("traceId", "req-123"));
```

Trong real app, để log MDC, tracing và security context xuyên thread phải dùng Spring/Reactor context propagation tích hợp phù hợp. Không truyền principal/credential bí mật bằng request header tự do rồi tin đó là identity.

## 12. Ứng dụng Spring Boot WebFlux + Java 17 có thể chạy

Tạo project Maven sử dụng **Spring Boot 3.3.13**, JDK 17+, dependency `spring-boot-starter-webflux`. **Không** thêm `spring-boot-starter-web` nếu muốn Boot mặc định chọn reactive web application; khi cả MVC và WebFlux starters cùng có mặt, Boot thường ưu tiên MVC auto-configuration trừ khi cấu hình khác.

Ứng dụng minh họa dữ liệu cố định và reactive repository tối giản trong memory:

- `GET /api/items` trả `Flux<Item>` → JSON array có 2 items.
- `GET /api/items/1` trả `Mono<ResponseEntity<Item>>` → 200 + item.
- `GET /api/items/999` → empty source được chuyển thành 404.
- `GET /api/items?contains=boot` → Flux.filter, có thể 0..N.
- `GET /api/items/first` → `Flux.next()`, chuyển sang Mono.
- `GET /api/items/summary` → `collectList()`, tạo một Summary object.
- `GET /api/items/events` → finite SSE Flux với hai event.
- `GET /api/items/none` → `Mono<Void>`/204, minh họa completion không body.

### 12.1. Cấu trúc

```text
mono-flux-cardinality/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── ReactiveCardinalityApplication.java
    │   ├── Item.java
    │   ├── CatalogService.java
    │   └── ItemController.java
    └── test/java/example/
        ├── ReactorCardinalityTest.java
        └── ItemControllerTest.java
```

### 12.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>mono-flux-cardinality</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
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

`spring-boot-starter-webflux` cung cấp Spring WebFlux, Reactor Core và Reactor Netty runtime mặc định; `reactor-test` cung cấp `StepVerifier` cho tests. Không cần chỉ định Reactor version bằng tay vì Spring Boot dependency management chọn bộ versions tương thích.

### 12.3. ReactiveCardinalityApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactiveCardinalityApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactiveCardinalityApplication.class, args);
    }
}
```

### 12.4. Item.java

```java
package example;

public record Item(long id, String name) {}
```

Java record có constructor và accessors `id()`, `name()`; Jackson được Boot cấu hình để encode JSON object.

### 12.5. CatalogService.java

```java
package example;

import java.util.List;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CatalogService {
    private final List<Item> items = List.of(
        new Item(1L, "Spring WebFlux"),
        new Item(2L, "Spring Boot")
    );

    public Flux<Item> all() {
        // Cold source: mỗi subscriber duyệt cùng snapshot.
        return Flux.defer(() -> Flux.fromIterable(items));
    }

    public Mono<Item> find(long id) {
        return Mono.defer(() ->
            Mono.justOrEmpty(
                items.stream()
                    .filter(item -> item.id() == id)
                    .findFirst()
            )
        );
    }
}
```

Đây là data store cố định trong memory, **không phải reactive DB driver**. Vì dữ liệu List quá nhỏ, synchronous lookup là đủ cho mục đích demo; trong hệ thống thật, không dùng cách này để bọc một JPA query blocking lớn trên event-loop. Với R2DBC/reactive repository, các methods trả Mono/Flux mà I/O không cần giữ thread trong lúc chờ như JDBC.

Hai methods dùng `defer` để tạo Publisher/lookup vào lúc subscription. `all()` phát hai items rồi complete. `find(999)` hoàn tất rỗng mà không báo lỗi.

### 12.6. ItemController.java

```java
package example;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ItemController {

    public record Summary(int count, List<String> names) {}

    private final CatalogService service;

    public ItemController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/items")
    public Flux<Item> list(
            @RequestParam(required = false) String contains) {

        Flux<Item> source = service.all();

        if (contains == null || contains.isBlank()) {
            return source;
        }

        String wanted = contains.trim().toLowerCase(Locale.ROOT);
        return source.filter(item ->
            item.name().toLowerCase(Locale.ROOT).contains(wanted));
    }

    @GetMapping("/api/items/first")
    public Mono<ResponseEntity<Item>> first() {
        return service.all()
            .next()
            .map(item -> ResponseEntity.ok(item))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/items/summary")
    public Mono<Summary> summary() {
        return service.all()
            .collectList()
            .map(items -> new Summary(
                items.size(),
                items.stream().map(Item::name).toList()
            ));
    }

    @GetMapping("/api/items/none")
    public Mono<ResponseEntity<Void>> none() {
        return Mono.<Void>empty()
            .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping(
        value = "/api/items/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Item>> events() {
        return service.all()
            .delayElements(Duration.ofMillis(20))
            .map(item -> ServerSentEvent.<Item>builder()
                .event("item")
                .data(item)
                .build());
    }

    @GetMapping("/api/items/{id}")
    public Mono<ResponseEntity<Item>> get(@PathVariable long id) {
        return service.find(id)
            .map(item -> ResponseEntity.ok(item))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
```

**Vì sao chọn các return types như vậy?**

- `list`: có 0..N item, trả `Flux<Item>` thay vì `Mono<Item>`; với `application/json` trên kết quả finite, browser thường nhận JSON array.
- `first`: `Flux.next()` lấy một item đầu; nếu source không có item, controller chủ động trả 404 thay vì mặc nhiên 200 body rỗng.
- `summary`: `collectList` tạo một List rồi map sang **một Summary**; nếu items rỗng, summary vẫn có thể là `{count:0,names:[]}` chứ không phải 404.
- `none`: `Mono<Void>` hoàn tất thành công, `thenReturn` cung cấp ResponseEntity 204 để HTTP status được xác định tường minh.
- `events`: `Flux<ServerSentEvent<Item>>` cùng media type SSE biểu diễn nhiều events được gửi theo thời gian; demo chỉ có hai events rồi complete để tránh stream vô hạn trong test.
- `get`: `Mono.empty()` từ repository được biến thành `ResponseEntity.notFound()`. Đây là lựa chọn API contract, không phải behavior mặc định của Mono.
- Annotated route `/api/items/first` và `/api/items/{id}` cùng tồn tại: Spring path matching chọn route literal cụ thể hơn; thứ tự khai báo methods trong class không phải yếu tố chính.

### 12.7. Lệnh chạy và response mẫu

Ở thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Gọi GET danh sách:

```bash
curl -i http://localhost:8080/api/items
```

HTTP 200, JSON body:

```json
[
  {"id":1,"name":"Spring WebFlux"},
  {"id":2,"name":"Spring Boot"}
]
```

Whitespace thực tế thường compact, nhưng JSON structure như trên.

GET một item:

```bash
curl -i http://localhost:8080/api/items/1
```

HTTP 200, body:

```json
{"id":1,"name":"Spring WebFlux"}
```

Không tồn tại:

```bash
curl -i http://localhost:8080/api/items/999
```

HTTP **404 Not Found**, do controller xử lý `Mono.empty()` thành `ResponseEntity.notFound()`. Response body lỗi chính xác có thể do Boot default error handler quyết định; đừng assert một JSON error text không có trong controller.

Lọc thành công:

```bash
curl -i "http://localhost:8080/api/items?contains=boot"
```

HTTP 200:

```json
[{"id":2,"name":"Spring Boot"}]
```

Lọc không có kết quả:

```bash
curl -i "http://localhost:8080/api/items?contains=unknown"
```

HTTP 200 với JSON array rỗng:

```json
[]
```

Tóm tắt:

```bash
curl -i http://localhost:8080/api/items/summary
```

HTTP 200:

```json
{"count":2,"names":["Spring WebFlux","Spring Boot"]}
```

Không có body:

```bash
curl -i http://localhost:8080/api/items/none
```

HTTP **204 No Content**, không có JSON body.

SSE:

```bash
curl -N -i -H "Accept: text/event-stream" \
  http://localhost:8080/api/items/events
```

Phần body minh họa (dạng encoding cụ thể, khoảng trắng và các headers có thể thay đổi):

```text
event:item
data:{"id":1,"name":"Spring WebFlux"}

event:item
data:{"id":2,"name":"Spring Boot"}

```

`curl -N` tắt output buffering phía curl để dễ xem từng event. Khi response stream dài, client cần xử lý reconnect, ID, cancellation, timeouts và backpressure; đừng cho rằng sự tồn tại của Flux bảo đảm delivery durable.

## 13. Unit tests Reactor: StepVerifier kiểm tra signals, không chỉ data

`reactor-test` cung cấp `StepVerifier` để kiểm tra những gì Publisher phát:

- `expectNext(...)`: dự kiến một/một số onNext values.
- `expectNextCount(n)`: dự kiến n phần tử mà không quan tâm giá trị.
- `verifyComplete()`: xác nhận completion thành công.
- `expectError(...)`/`expectErrorMatches(...)`: xác nhận terminal error.
- `thenCancel()`: hủy stream dài hạn.
- `withVirtualTime(...)`: kiểm tra publisher có delay mà không đợi thời gian thực quá dài, nếu publisher được tạo sau khi virtual scheduler được thiết lập.

### 13.1. ReactorCardinalityTest.java

```java
package example;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactorCardinalityTest {

    @Test
    void monoJustEmitsOneItemThenCompletes() {
        StepVerifier.create(Mono.just("A"))
            .expectNext("A")
            .verifyComplete();
    }

    @Test
    void monoEmptyCompletesWithoutNext() {
        StepVerifier.create(Mono.<String>empty())
            .verifyComplete();
    }

    @Test
    void monoJustNullIsNotAllowed() {
        assertThrows(NullPointerException.class,
            () -> Mono.just((String) null));
    }

    @Test
    void monoJustOrEmptyNullIsEmpty() {
        StepVerifier.create(Mono.justOrEmpty((String) null))
            .verifyComplete();
    }

    @Test
    void monoErrorIsNotSuccessfulCompletion() {
        StepVerifier.create(
                Mono.<String>error(
                    new IllegalStateException("boom")))
            .expectErrorMatches(ex ->
                ex instanceof IllegalStateException &&
                "boom".equals(ex.getMessage()))
            .verify();
    }

    @Test
    void fluxCanEmitMoreThanOneItem() {
        StepVerifier.create(Flux.just(1, 2, 3))
            .expectNext(1, 2, 3)
            .verifyComplete();
    }

    @Test
    void fluxCanBeEmpty() {
        StepVerifier.create(Flux.<Integer>empty())
            .verifyComplete();
    }

    @Test
    void collectListTurnsEmptyFluxIntoOneEmptyList() {
        StepVerifier.create(
                Flux.<Integer>empty().collectList())
            .expectNext(List.of())
            .verifyComplete();
    }

    @Test
    void nextTakesOnlyFirst() {
        StepVerifier.create(Flux.just(9, 8, 7).next())
            .expectNext(9)
            .verifyComplete();
    }

    @Test
    void singleRequiresExactlyOneValue() {
        StepVerifier.create(Flux.just(9).single())
            .expectNext(9)
            .verifyComplete();

        StepVerifier.create(Flux.<Integer>empty().single())
            .expectError(NoSuchElementException.class)
            .verify();

        StepVerifier.create(Flux.just(1, 2).single())
            .expectError(IndexOutOfBoundsException.class)
            .verify();
    }

    @Test
    void singleOrEmptyAllowsZeroButNotTwoValues() {
        StepVerifier.create(Flux.<Integer>empty().singleOrEmpty())
            .verifyComplete();

        StepVerifier.create(Flux.just(1, 2).singleOrEmpty())
            .expectError(IndexOutOfBoundsException.class)
            .verify();
    }

    @Test
    void fallbackForEmptyIsNotFallbackForError() {
        StepVerifier.create(
                Mono.<String>empty().defaultIfEmpty("fallback"))
            .expectNext("fallback")
            .verifyComplete();

        StepVerifier.create(
                Mono.<String>error(new IllegalStateException())
                    .defaultIfEmpty("fallback"))
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void collectListCountsAllFiniteItems() {
        StepVerifier.create(
                Flux.range(5, 3).collectList())
            .expectNext(List.of(5, 6, 7))
            .verifyComplete();
    }

    @Test
    void thenReturnWaitsForCompletion() {
        StepVerifier.create(
                Mono.just("ignored").thenReturn("done"))
            .expectNext("done")
            .verifyComplete();
    }

    @Test
    void monoZipWithEmptyCannotEmitTuple() {
        StepVerifier.create(Mono.zip(
                Mono.just("A"), Mono.<Integer>empty()))
            .verifyComplete();
    }
}
```

Các tests này có scope **signal/cardinality** và không cần HTTP server. `single()` với quá nhiều items được Reactor 3.6 báo lỗi `IndexOutOfBoundsException`; với source empty, `single()` báo `NoSuchElementException`. Kiểm thử không chỉ dùng `block()` rồi so giá trị, vì `block()` trên Mono rỗng có thể trả null và dễ làm người đọc lẫn lộn empty với null.

### 13.2. Test virtual time cho stream có thời gian

Ví dụ bổ sung:

```java
@Test
void timedFluxCanBeTestedWithoutRealDelay() {
    StepVerifier.withVirtualTime(() ->
            Flux.interval(Duration.ofHours(1)).take(2))
        .expectSubscription()
        .thenAwait(Duration.ofHours(2))
        .expectNext(0L, 1L)
        .verifyComplete();
}
```

Đặt method này vào class trên và thêm `import java.time.Duration;`. **Publisher phải được tạo trong Supplier của `withVirtualTime`**, nếu tạo trước, timer có thể đã gắn vào scheduler thực và test không điều khiển được. Virtual-time tests hữu ích cho `delayElement`, `interval`, `timeout` và retries; tránh `Thread.sleep` làm test chậm/flaky.

## 14. WebTestClient: kiểm tra Mono/Flux qua HTTP

Bài kiểm thử dưới đây khởi động reactive Spring Boot context qua `@SpringBootTest(webEnvironment = RANDOM_PORT)` và dùng `WebTestClient`; không ép phải dùng Servlet MockMvc. `@AutoConfigureWebTestClient` cấu hình client test Spring WebFlux.

### 14.1. ItemControllerTest.java

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ItemControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void listFluxBecomesJsonArray() {
        client.get().uri("/api/items")
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
                .jsonPath("$[0].name").isEqualTo("Spring WebFlux")
                .jsonPath("$[1].name").isEqualTo("Spring Boot");
    }

    @Test
    void existingItemMonoReturns200() {
        client.get().uri("/api/items/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.name").isEqualTo("Spring WebFlux");
    }

    @Test
    void emptyMonoIsExplicitlyMappedTo404() {
        client.get().uri("/api/items/999")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void filterFluxCanReturnZeroItems() {
        client.get()
            .uri("/api/items?contains=missing")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]");
    }

    @Test
    void firstConvertsFluxToMono() {
        client.get().uri("/api/items/first")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void summaryCollectsFiniteFluxIntoSingleObject() {
        client.get().uri("/api/items/summary")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.count").isEqualTo(2)
                .jsonPath("$.names[0]")
                    .isEqualTo("Spring WebFlux");
    }

    @Test
    void completionWithoutBodyReturns204() {
        client.get().uri("/api/items/none")
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();
    }

    @Test
    void sseEndpointProducesEventStream() {
        client.get().uri("/api/items/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }
}
```

**Tại sao test dùng RANDOM_PORT?** Nó kiểm tra WebFlux HTTP server stack (Reactor Netty mặc định), Spring Boot routing và encoding thật hơn test chỉ unit controller. Với kiểm thử streaming/cancellation có yêu cầu mạnh, cần consume body qua `returnResult(...)` và dùng StepVerifier, không chỉ assert headers. Test này không thay thế load test network backpressure hoặc reliability khi client disconnect.

### 14.2. Kiểm thử lỗi không phải không có item

Nếu service trả `Mono.error(new IllegalStateException("db unavailable"))`, đó là lỗi hạ tầng; không được tự chuyển thành 404. Với Spring WebFlux có thể cấu hình `@RestControllerAdvice`, `ErrorWebExceptionHandler` hoặc `onErrorResume` theo contract. Nhưng đừng áp `onErrorResume(ex -> Mono.empty())` cho mọi exception vì sẽ biến sự cố thành “không tìm thấy”, che mất alert và thông tin cần debug.

## 15. Lựa chọn Mono/Flux trong WebClient, repository và business API

### 15.1. WebClient trả một object

```java
Mono<Item> item = webClient.get()
        .uri("/api/items/{id}", 42L)
        .retrieve()
        .bodyToMono(Item.class);
```

Nếu server HTTP 404, `retrieve()` mặc định có thể sinh `WebClientResponseException.NotFound`, **không tự biến status 404 thành Mono.empty()**. Muốn phân biệt 404 với lỗi khác, viết status handler hoặc dùng `exchangeToMono(...)`:

```java
Mono<Item> selected = webClient.get()
    .uri("/api/items/{id}", 42L)
    .exchangeToMono(response -> {
        if (response.statusCode().value() == 404) {
            return Mono.empty();
        }
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(Item.class);
        }
        return response.createError();
    });
```

*Đoạn minh họa, cần WebClient/Item imports và xử lý các status nghiệp vụ khác nếu cần. Không dùng code mẫu để nuốt mọi lỗi 5xx thành empty.*

### 15.2. WebClient trả nhiều items

```java
Flux<Item> items = webClient.get()
        .uri("/api/items")
        .retrieve()
        .bodyToFlux(Item.class);
```

Nếu endpoint trả JSON array, WebClient có thể decode thành nhiều Item signals. Nhưng server phát JSON array hữu hạn và server phát SSE/infinite stream có lifecycle khác; consumer phải hiểu content type, buffering và cancellation.

### 15.3. ReactiveCrudRepository

Các repository reactive phổ biến đưa về cardinality như:

| Operation | Kiểu thường gặp |
| --- | --- |
| `findById(id)` | `Mono<T>`: 0..1 |
| `findAll()` | `Flux<T>`: 0..N |
| `save(entity)` | `Mono<T>`: saved object hoặc lỗi |
| `deleteById(id)` | `Mono<Void>`: completion/error |
| `count()` | `Mono<Long>` |
| `existsById(id)` | `Mono<Boolean>` |

Đây là contract kiểu phổ biến trong Spring Data reactive repositories. Một empty result của `findById` không nhất thiết tương ứng với HTTP 404 cho mọi use case; HTTP policy thuộc service/controller.

### 15.4. Business cardinality so với technical cardinality

Nếu entity phải duy nhất theo email, `Mono<User>` là API tốt, nhưng **không bảo đảm DB unique constraint**. Cần database uniqueness/transaction. Nếu endpoint findAll trả `Flux<User>` nhưng yêu cầu page size 20, đừng stream toàn bộ hàng triệu users không giới hạn; dùng pagination/limits và query database phù hợp.

## 16. Lỗi phổ biến: 20 bẫy cần tránh

| Nhầm lẫn | Hậu quả | Cách hiểu đúng |
| --- | --- | --- |
| “Mono luôn có đúng 1 item” | Không xử lý empty | Mono phát 0 hoặc 1 |
| “Flux luôn có nhiều items” | Không biết Flux.empty | Flux phát 0..N |
| “Mono.empty() là Mono.just(null)” | NPE hoặc logic sai | Null không phải onNext hợp lệ |
| “Mono.error là Mono.empty” | Che mất sự cố | Error terminal khác complete empty |
| “Mono<Void> chứa object Void” | map không chạy | Chỉ completion hoặc error |
| “Mono<List<T>> giống Flux<T>” | Sai cardinality và streaming | Một List là một item |
| “Flux.empty().collectList() vẫn empty Mono” | Sai response/test | Nó phát `[]` |
| “Flux.next kiểm tra đúng một item” | Dữ liệu trùng bị bỏ qua | next chỉ lấy item đầu |
| “Flux.single có thể bỏ qua item thừa” | Lỗi cardinality | single yêu cầu đúng một item |
| “Flux.singleOrEmpty chấp nhận mọi số items” | Error khi >1 | Nó chỉ cho 0 hoặc 1 |
| “switchIfEmpty bắt exception” | Lỗi không được xử lý | Dùng onErrorResume khi thật sự muốn fallback lỗi |
| “onErrorResume dùng cho not found” | Đổi mọi lỗi DB thành 404 | Empty và error phải tách |
| “zip với Mono.empty luôn chạy side effects bên kia” | Side effects bị short-circuit | Không dùng zip để kích hoạt tác vụ bắt buộc |
| “Mono.just(blockingCall()) là lazy” | Blocking chạy lúc assembly | defer/fromCallable và scheduler phù hợp |
| “Mono.fromCallable làm JDBC non-blocking” | Event loop vẫn bị block | Reactive driver hoặc boundedElastic bridge |
| “subscribe bên trong controller để nhanh hơn” | Double execution, mất cancellation | Return Publisher để WebFlux subscribe |
| “block() trong WebFlux handler là vô hại” | Event-loop starvation/lỗi | Compose reactive operators |
| “Flux là streaming tức browser thấy item ngay” | Mất kỳ vọng về media type | JSON array vs SSE/NDJSON khác nhau |
| “Flux hot luôn replay data cho người mới” | Mất events | Chọn replay/buffer policy rõ |
| “Backpressure tự tồn tại trong mọi callback source” | Overflow/OOM | Design demand/backpressure |
| “collectList trên Flux vô hạn cho kết quả” | Không complete, heap tăng | Giới hạn take/window hoặc stream |
| “Cancellation là onComplete” | Cleanup thiếu | doFinally/usingWhen đúng semantics |
| “Mỗi operator tạo thread mới” | Sai performance model | Scheduler chỉ thay khi được chỉ định |
| “HTTP 404 tự đến khi Mono rỗng” | Sai API contract | Explicit ResponseEntity.notFound |
| “WebClient retrieve 404 trả Mono.empty” | 404 thành error | onStatus/exchangeToMono theo policy |
| “Một ETag/security token là reactive context” | Sai trust boundary | Xác thực và context riêng |
| “Reactive nghĩa mọi thư viện đều non-blocking” | JDBC/file I/O vẫn block | Kiểm tra toàn call chain |
| “map xử lý completion empty” | Side effect không chạy | then/doOnSuccess/doFinally theo semantics |
| “Flux flatMap luôn giữ thứ tự” | Output không ổn định | concatMap/flatMapSequential nếu cần |
| “Producer cancel thì data đã được lưu chắc rollback” | Side effect mơ hồ | Transaction/idempotency rõ |

## 17. Checklist thiết kế Mono hay Flux

Trước khi định nghĩa return type cho method, trả lời:

1. Nghiệp vụ trả **không**, **một** hay **nhiều** object?
2. “Không có object” là thành công hợp lệ, HTTP 404, hay lỗi business?
3. “Không có body” nghĩa thao tác thành công không payload (`Mono<Void>`/204) hay entity không tồn tại?
4. Nếu nhiều objects, cần JSON array hữu hạn hay SSE/NDJSON streaming?
5. Source hữu hạn hay có thể phát vô hạn? Ai và khi nào cancel?
6. Cần giữ thứ tự items? `flatMap` có thể reorder không?
7. Lỗi xuất hiện **trước** hay **sau** khi HTTP response committed?
8. Driver repository/WebClient có thực sự non-blocking không?
9. Có upstream demand/backpressure khi producer nhanh hơn consumer không?
10. Có giữ toàn data bằng `collectList()` và cần giới hạn memory không?
11. Có accidental double subscription gây DB queries hoặc duplicate writes không?
12. Có retry mà operation không idempotent không?
13. Đã phân biệt `switchIfEmpty` với `onErrorResume` rõ chưa?
14. HTTP 404, 204, 400, 500 có được mapping tường minh đúng contract không?
15. Tests đã assert completion, error, empty, một giá trị và nhiều giá trị chưa?
16. Test async có dùng virtual time thay `Thread.sleep` khi cần không?
17. Có log/metrics cancellation, demand, latency và retries mà không leak dữ liệu không?
18. Context/security đi qua reactive chain đúng cách, không phụ thuộc ThreadLocal tùy tiện?
19. Khi trộn MVC và WebFlux starters, ứng dụng thực chạy stack nào?
20. Có cần pagination hoặc rate limit thay vì để client subscribe vào stream vô hạn?

## 18. Câu hỏi tự kiểm tra

**1.** `Mono<T>` có nhất thiết phát một giá trị không?  
**Đáp án:** Không; nó có 0 hoặc 1 item, kết thúc thành công hoặc lỗi, và có thể không kết thúc nếu source never.

**2.** `Flux<T>` có thể rỗng không?  
**Đáp án:** Có; `Flux.empty()` hoàn tất không phát item.

**3.** Vì sao `Mono.just(null)` không hợp lệ?  
**Đáp án:** Reactive Streams không cho phát null qua onNext; Reactor factory ném NullPointerException.

**4.** `Mono.justOrEmpty((String)null)` biểu diễn gì?  
**Đáp án:** Mono empty, hoàn tất không có onNext.

**5.** `Mono<Void>` có onNext(Void) không?  
**Đáp án:** Không; chủ yếu biểu diễn completion/error không payload.

**6.** `Flux.empty().collectList()` phát bao nhiêu item?  
**Đáp án:** Một item: empty List.

**7.** `Flux.just(1,2).next()` phát gì?  
**Đáp án:** Phát 1 rồi hoàn tất; không kiểm tra item 2.

**8.** `Flux.just(1,2).single()` thì sao?  
**Đáp án:** Error vì có hơn một item.

**9.** `Flux.empty().single()` thì sao?  
**Đáp án:** Error vì yêu cầu đúng một item.

**10.** `Flux.empty().singleOrEmpty()` thì sao?  
**Đáp án:** Complete empty, hợp lệ.

**11.** `switchIfEmpty` xử lý trường hợp error không?  
**Đáp án:** Không, cần error handling riêng.

**12.** `Mono.error(ex).defaultIfEmpty(v)` phát v không?  
**Đáp án:** Không, error propagate.

**13.** Mono.zip với một nguồn Mono.empty tạo kết quả không?  
**Đáp án:** Không, tuple không có đủ thành phần.

**14.** Tại sao `Mono.just(expensive())` không trì hoãn expensive?  
**Đáp án:** Java đánh giá argument trước khi gọi just.

**15.** Gọi `subscribe()` ngay trong WebFlux controller thường có nên không?  
**Đáp án:** Không; return Publisher cho Framework quản lý subscription/lifecycle.

**16.** Khi client disconnect từ SSE, điều gì có thể xảy ra?  
**Đáp án:** Subscription bị cancel, cần cleanup; không nhất thiết onComplete.

**17.** `flatMap` trên Flux có bảo toàn thứ tự của async inner publishers không?  
**Đáp án:** Không bảo đảm; dùng concatMap hoặc lựa chọn giữ thứ tự.

**18.** `publishOn` và `subscribeOn` cùng tác động đến execution context không?  
**Đáp án:** Có, nhưng ở phạm vi downstream và subscription/upstream khác nhau.

**19.** Dùng `.block()` bên trong WebFlux request event-loop có nguy hiểm không?  
**Đáp án:** Có thể block hoặc bị Reactor từ chối, giảm khả năng xử lý concurrent connections.

**20.** HTTP GET tìm ID không có, service trả Mono.empty, có tự 404 không?  
**Đáp án:** Không bảo đảm; dùng explicit ResponseEntity.notFound nếu contract cần 404.

**21.** Flux trả JSON array có luôn flush mỗi item không?  
**Đáp án:** Không; phụ thuộc writer/media type. SSE/NDJSON dùng streaming semantics rõ hơn.

**22.** WebClient.retrieve() với 404 có mặc định trả Mono.empty không?  
**Đáp án:** Không; thường sinh WebClientResponseException tương ứng.

**23.** Mono.zip dùng để chắc chắn chạy side effects của mọi nguồn có tốt không?  
**Đáp án:** Không; empty có thể short-circuit subscriptions.

**24.** `Mono.cache()` có phù hợp lưu mãi thông tin riêng tư mọi user không?  
**Đáp án:** Không nếu chưa xét key/TTL/invalidation/security.

**25.** Backpressure giải quyết vấn đề gì?  
**Đáp án:** Cho downstream thông báo demand để upstream phù hợp tốc độ tiêu thụ, khi nguồn có thể tôn trọng demand.

## 19. Nguồn đọc chính thức

- [Project Reactor — Reactor 3 Reference Guide](https://projectreactor.io/docs/core/3.6.12/reference/index.html): Mono 0..1, Flux 0..N, backpressure, cold/hot, assembly/subscription, operators.
- [Reactor Core 3.6 — Mono Javadoc](https://projectreactor.io/docs/core/3.6.15/api/reactor/core/publisher/Mono.html): `justOrEmpty`, `defer`, `switchIfEmpty`, `defaultIfEmpty`, `thenReturn`, subscribe.
- [Project Reactor — Flux Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html): `fromIterable`, `next`, `single`, `singleOrEmpty`, `collectList`.
- [Project Reactor — Testing with StepVerifier](https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html): kiểm thử onNext/onComplete/onError/virtual time.
- [Spring Framework 6.2 — WebFlux Annotated Controllers](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller.html): Mono/Flux return types trong REST controllers.
- [Spring Framework 6.2 — WebFlux Controller Return Values](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/return-types.html): HTTP encoding theo media type, Flux stream vs response buffering.
- [Spring Framework 6.2 — ResponseEntity](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/responseentity.html): `Mono<ResponseEntity<T>>` khác `ResponseEntity<Mono<T>>`/`ResponseEntity<Flux<T>>`.
- [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient.html): `bodyToMono`/`bodyToFlux`, error handling.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html): HTTP tests của WebFlux.
- [Reactive Streams specification](https://www.reactive-streams.org/): Publisher, Subscriber, Subscription, demand, terminal signals.

**Ghi nhớ cốt lõi:** `Mono<T>` là **0 hoặc 1 item**; `Flux<T>` là **0 đến nhiều items**. Cả hai còn phát **completion/error/cancellation semantics** và chịu subscription/backpressure. **Empty không phải null, error không phải empty, Flux.empty().collectList() không phải Mono.empty().** Đọc đúng những khác biệt này giúp thiết kế API WebFlux có HTTP status, memory footprint, streaming và error handling đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-foundations-003">Tính trì hoãn, đăng ký thực thi và vòng đời một chuỗi reactive</a>

<details>
<summary>Xem chi tiết</summary>

# Tính trì hoãn, đăng ký thực thi và vòng đời một chuỗi reactive trong Spring WebFlux

## 1. Bài toán: khai báo một pipeline có phải là chạy nó?

Trong lập trình mệnh lệnh, lời gọi `service.findById(id)` thường thực thi ngay lúc chương trình đi qua dòng lệnh. Trong Project Reactor, một biểu thức như `Mono.defer(() -> service.findReactive(id))` thường chỉ **mô tả cách tạo nguồn dữ liệu khi có subscription**. Dựng một pipeline, đăng ký một subscriber, yêu cầu phần tử, phát các tín hiệu và dọn tài nguyên là các pha khác nhau.

Đây là kiến thức nền tảng của **Spring WebFlux**, nơi controller thường trả `Mono<T>` hoặc `Flux<T>` để HTTP infrastructure đăng ký và xử lý response. Không phân biệt các pha sẽ dẫn đến lỗi: gọi API quá sớm, gửi email/thanh toán hai lần khi có retry, tưởng source đã được thực thi dù không ai subscribe, tự `subscribe()` rồi mất quản lý lỗi và cancellation, hoặc quên đóng tài nguyên khi client ngắt kết nối.

Mục tiêu:

- Phân biệt **assembly time** (dựng chuỗi), **subscription time** (đăng ký), **request/demand**, **signal processing**, **termination/cancellation** và **resource cleanup**.
- Biết `Mono.just(...)` khác `Mono.defer(...)`, `Mono.fromSupplier(...)` và `Mono.fromCallable(...)` ở đâu; không nói “mọi thứ trong Mono đều lazy”.
- Hiểu đường truyền `subscribe` từ downstream lên upstream, còn `onNext/onError/onComplete` truyền từ upstream xuống downstream.
- Giải thích vì sao một chuỗi cold được subscribe hai lần có thể kích hoạt hai lần truy vấn/side effects; so sánh với hot/shared/cached publisher.
- Quan sát `doFirst`, `doOnSubscribe`, `doOnRequest`, `doOnNext`, `doOnError`, `doOnComplete`, `doFinally`.
- Quản lý `Disposable`, `cancel()`, timeout, retry, resource acquisition/release và client disconnect.
- Tích hợp đúng với WebFlux annotated controller, WebClient và Reactive Repository; không tự đăng ký trong controller.
- Thực hành **Java 17+, Spring Framework 6.x, Spring Boot 3.x, Reactor Core** bằng mã runnable và kiểm thử `StepVerifier`/`WebTestClient`.

**Phạm vi:** Spring WebFlux/Reactor, không phải chỉ Spring MVC `Callable`/Servlet async. Reactor xây trên Reactive Streams; một nguồn có thể phát **đồng bộ ngay trong `subscribe`/`request`**, nên reactive không đồng nghĩa tự tạo thread mới. Ví dụ không đòi framework chạy trên một phiên bản Reactor cụ thể hơn bản Spring Boot 3.x quản lý tương thích.

## 2. Sơ đồ vòng đời một chuỗi

```text
T0: ASSEMBLY — tạo Flux/Mono, nối operators
    Flux.range(1, 3).map(...).filter(...)
             |
             | (chưa có terminal consumer)
             v
T1: SUBSCRIBE — downstream Subscriber đăng ký
    subscriber -> filter -> map -> source
             |
             v
T2: onSubscribe(Subscription), demand request(n)
    downstream -------------> upstream
             |
             v
T3: SIGNALS
    upstream -> onNext(value)* -> downstream
    upstream -> onComplete()/onError() -> downstream
             |
             v
T4: FINALLY/CLEANUP
    complete | error | cancel
    release file/connection/registration
```

Các mốc không nhất thiết cách nhau theo thời gian: với `Flux.range(1,3)`, subscribe/request/onNext/complete có thể diễn ra **cùng thread và cùng call stack**. Với `WebClient` hoặc database reactive driver, phát tín hiệu có thể đến từ network I/O callbacks sau đó. Một publisher dài vô hạn có thể không bao giờ onComplete; nó cần cancellation hoặc chính sách kết thúc riêng.

**Quy tắc trách nhiệm:** Dựng pipeline chưa có nghĩa HTTP response đã gửi. Đăng ký cũng không có nghĩa đã nhận phần tử: demand `request(n)` và cách source phát tín hiệu quyết định. Completion/error/cancel cần kiểm soát tài nguyên, không xem việc method controller return là hoàn tất của operation.

## 3. Reactive Streams: Publisher, Subscriber, Subscription

Ba giao diện chính trong `org.reactivestreams`:

```java
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> subscriber);
}

public interface Subscriber<T> {
    void onSubscribe(Subscription subscription);
    void onNext(T item);
    void onError(Throwable failure);
    void onComplete();
}

public interface Subscription {
    void request(long n);
    void cancel();
}
```

Đây là **chữ ký API minh họa**, không phải source file cần tự tạo lại vì thư viện cung cấp sẵn.

- **Publisher:** nguồn có thể cung cấp 0..N phần tử; `Mono` đại diện 0..1, `Flux` đại diện 0..N.
- **Subscriber:** người nhận callbacks và quyết định demand; phần lớn code WebFlux không phải tự viết Subscriber.
- **Subscription:** hợp đồng gắn một upstream-downstream relationship, nhận `request(n)` và `cancel()`.
- **Processor:** vừa Subscriber vừa Publisher; nhiều operators bên trong Reactor tạo các boundary tương đương.
- **Disposable:** kiểu tiện ích Reactor trả về từ các overload `subscribe` để hủy đăng ký, **không đồng nhất với API `Subscription` chuẩn**.

Giao thức có dạng:

```text
onSubscribe → onNext* → (onError | onComplete)?
```

`onError` và `onComplete` là terminal signals loại trừ nhau; chúng không cần có demand mới phát. `onNext` không được vượt số `request(n)` đã được chấp nhận, không gửi null và không gửi tiếp sau terminal. `cancel()` làm publisher cuối cùng ngừng phát, nhưng không nhất thiết cắt ngay công việc đang chạy hoặc rollback transaction.

## 4. Assembly time: khi nào Java đã chạy code?

Ví dụ:

```java
System.out.println("A");
Mono<String> mono = Mono.just(expensiveCall());
System.out.println("B");
```

Ở đây **`expensiveCall()` được đánh giá ngay** khi chương trình dựng argument truyền cho `Mono.just`, trước khi `mono` được gán. `Mono.just` giữ giá trị đã có. Nếu `expensiveCall()` gọi network hoặc JDBC blocking, việc này không được “biến thành lazy” nhờ bọc kết quả trong Mono.

Trái lại:

```java
Mono<String> mono = Mono.fromSupplier(() -> expensiveCall());
```

Lambda mô tả công việc sẽ được gọi khi có subscription (và logic source thực thi theo semantics của operator). Tạo biến `mono` chưa gọi `expensiveCall`.

**Cạm bẫy thứ hai:**

```java
Mono<String> mono = Mono.just("hello")
    .map(value -> value.toUpperCase());
```

Lambda `map` chưa chạy ở assembly; tuy nhiên biểu thức `Mono.just("hello")` và việc tạo operator objects đã thực thi. Không nên nói mọi dòng bên trong một biểu thức reactive đều deferred. **Dựng pipeline là thao tác Java bình thường; deferred là phần công việc được đặt vào các publisher/operator để chạy khi có subscription.**

### Khác biệt Mono.just, fromSupplier, fromCallable, defer

| Cách tạo | Khi tính value/source? | Ghi chú |
| --- | --- | --- |
| `Mono.just(value)` | `value` đã được tính **trước** lời gọi | Từ value non-null, phát lại giá trị này với subscriber |
| `Mono.justOrEmpty(nullable)` | `nullable` đã được tính trước | Null → complete empty, không onNext(null) |
| `Mono.fromSupplier(supplier)` | Supplier gọi trên subscription | Supplier trả null → empty theo Reactor contract |
| `Mono.fromCallable(callable)` | Callable gọi trên subscription | Checked exception được chuyển thành onError; null → empty |
| `Mono.defer(supplierOfMono)` | Factory tạo **một Publisher mới mỗi subscription** | Hữu ích khi cần đọc state/tạo pipeline tại subscribe time |
| `Flux.defer(supplierOfFlux)` | Tương tự nhưng 0..N | Không tự thay đổi thread model |
| `Mono.empty()` | Nguồn không có onNext | Vẫn có subscription/completion |
| `Mono.error(exception)` | Nguồn phát onError khi subscribe | Exception object có thể tạo tại assembly |
| `Mono.error(exceptionSupplier)` | Tạo exception theo factory khi subscribe | Thích hợp exception chứa thông tin subscription-local |

`Mono.defer` không chỉ “trì hoãn một phép tính”: nó hoãn **việc tạo cả Publisher** dựa trên trạng thái mới tại mỗi subscription. `fromSupplier`/`fromCallable` thường phù hợp tính đúng **một giá trị**. Nếu source phải được tái cấu hình theo Reactor Context của từng subscriber, dùng `Mono.deferContextual(...)`.

## 5. Ví dụ rõ nhất: giá trị eager và lazy khi subscribe hai lần

Tệp Java đầy đủ dưới đây yêu cầu Reactor Core (ví dụ trong Spring Boot WebFlux Maven project). Lớp `LazinessDemo.java`:

```java
package com.example.demo;

import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

public class LazinessDemo {

    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger();

        int eagerValue = counter.incrementAndGet();
        Mono<Integer> eager = Mono.just(eagerValue);

        Mono<Integer> lazy = Mono.fromSupplier(
                counter::incrementAndGet);

        System.out.println("assembled=" + counter.get());
        System.out.println("eager1=" + eager.block());
        System.out.println("eager2=" + eager.block());
        System.out.println("lazy1=" + lazy.block());
        System.out.println("lazy2=" + lazy.block());
        System.out.println("final=" + counter.get());
    }
}
```

Trong một Maven project đã có `spring-boot-starter-webflux`, chạy main từ IDE hoặc `mvn exec:java` khi đã cấu hình exec plugin; **không** dùng `javac LazinessDemo.java` trần khi thiếu Reactor jars. `block()` chỉ được dùng ở **ranh giới main demo đồng bộ**, không dùng trong WebFlux handler/event-loop.

Stdout chính xác với nguồn đồng bộ:

```text
assembled=1
eager1=1
eager2=1
lazy1=2
lazy2=3
final=3
```

Giải thích:
1. `counter.incrementAndGet()` trước `just` chạy ngay, dù chưa subscriber: counter=1.
2. Hai `block()` trên `Mono.just(1)` tạo hai subscriptions nhưng vẫn phát **cùng giá trị đã chụp** là 1.
3. Hai `block()` trên `Mono.fromSupplier(counter::incrementAndGet)` tạo hai subscriptions khác nhau, gọi supplier thêm hai lần → 2 rồi 3.
4. `block()` bản thân là **một terminal operation khởi tạo subscription mới** cho Mono mỗi lần gọi.

Điều này dẫn tới câu hỏi quan trọng: nếu supplier **ghi database/gửi email/thanh toán**, việc subscribe lại có thể chạy side effect thêm một lần. Vì vậy không đặt non-idempotent side effects vào nguồn cold và cho `retry`/nhiều subscribers tùy ý mà không có bảo vệ nghiệp vụ.

## 6. Đăng ký: subscribe() thực sự làm gì?

Lời gọi `publisher.subscribe(subscriber)` tạo mối quan hệ giữa Subscriber và Publisher, thiết lập operator chain và truyền `Subscription` đến downstream.

Với một pipeline:

```java
Flux<Integer> stream = Flux.range(1, 3)
    .map(n -> n * 10)
    .filter(n -> n >= 20);

stream.subscribe(System.out::println);
```

Phần tạo `stream` chưa tiêu thụ phần tử. `subscribe(...)` khởi tạo quá trình; với `Flux.range` synchronous có thể thấy:

```text
20
30
```

Nhưng đây là trường hợp synchronous: `subscribe()` có thể không chờ network I/O hoàn tất trong nguồn async, và không đảm bảo mã sau `subscribe()` chạy sau `onComplete`.

### Subscription đi từ downstream về upstream

```text
downstream subscribe
     |
     v
filter.subscribe -> map.subscribe -> source.subscribe
                                        |
onSubscribe(subscription) <-------------|
      |
request(n) -----------------------------> upstream
                                        |
onNext / onComplete --------------------> downstream
```

Operator có thể bọc Subscription và biến đổi demand nội bộ. Một terminal Subscriber gọi `request(1)` không bảo đảm upstream nhận đúng một `request(1)` khi có `buffer`/`flatMap`/prefetch.

Các overload Reactor `subscribe()`/`subscribe(Consumer)` thông dụng thường yêu cầu unbounded demand; nếu muốn **quan sát request từng phần tử**, dùng `BaseSubscriber` hoặc `StepVerifier.create(publisher, 0)` với `thenRequest(n)`.

## 7. Demand: subscribe chưa chắc được nhận onNext

Reactive Streams yêu cầu Publisher **không được gửi nhiều hơn số onNext đã request**. Mới subscribe mà demand bằng 0, một source tuân thủ protocol không được phát dữ liệu `onNext`. Tuy nhiên nó có thể gửi terminal `onComplete`/`onError` không cần demand.

Ví dụ Reactor Test:

```java
StepVerifier.create(Flux.range(1, 3), 0)
    .expectSubscription()
    .thenRequest(1)
    .expectNext(1)
    .thenRequest(2)
    .expectNext(2, 3)
    .verifyComplete();
```

Đoạn này nằm trong Java test method đã có imports. Demand cộng dồn, không reset: `request(1)` rồi `request(2)` cấp tổng 3 quyền onNext.

Các lưu ý:

- `request(0)` và giá trị âm không phải “pause”; vi phạm spec, active Subscription phải báo `IllegalArgumentException` qua `onError`.
- `request(Long.MAX_VALUE)` được coi là unbounded demand, không có nghĩa source chắc chắn phát vô hạn.
- `cancel()` yêu cầu ngừng subscription; một số signals đang in-flight có thể đến ngay sau cancel.
- Các nhà vận chuyển hot dữ liệu không thể tự chậm lại có thể phải buffer/drop/fail theo chính sách; `Flux` không biến tất cả upstream thành backpressure-capable.
- Khi dùng `publishOn`, `flatMap`, `buffer`, `limitRate`, upstream demand và final subscriber demand có thể khác.

## 8. Lifecycle hooks: doFirst, doOnSubscribe, doOnRequest, doOnNext

Reactor có nhiều operator chỉ quan sát lifecycle mà không biến đổi dữ liệu.

| Hook | Mốc quan sát | Ghi chú |
| --- | --- | --- |
| `doFirst(Runnable)` | Trước khi subscription bắt đầu đi vào nguồn | Được gọi từ luồng subscribe, các doFirst nối tiếp chạy theo thứ tự ngược khai báo |
| `doOnSubscribe(Consumer<Subscription>)` | Subscription đã được tạo/đi qua operator | Hữu ích metrics; không dùng để chiếm quyền quản lý Subscription tùy tiện |
| `doOnRequest(LongConsumer)` | Một request(n) đi qua boundary | Quan sát demand tại vị trí operator, không phải request HTTP |
| `doOnNext(Consumer<T>)` | Nguồn phát một value qua operator | Có thể lặp với mỗi subscription, không tự giới hạn đến một lần |
| `doOnComplete(Runnable)` | Flux hoàn tất thành công | Chỉ Flux; không được gọi khi error/cancel |
| `doOnSuccess(Consumer<T>)` | Mono hoàn tất thành công | Nhận **null** khi Mono complete empty |
| `doOnError(Consumer<Throwable>)` | Lỗi đi qua operator | Không phục hồi lỗi; muốn fallback dùng onErrorResume |
| `doOnCancel(Runnable)` | Downstream cancel operator | Không nhất thiết giống thời điểm hoàn tất I/O vật lý |
| `doFinally(Consumer<SignalType>)` | Sau complete, error **hoặc cancel** | Quan trọng để release/log cuối cùng |
| `doOnDiscard(Class, Consumer)` | Cleanup các phần tử bị một số operator discard | Chỉ áp với operators hỗ trợ discard; không phải hook bắt mọi object |

**Thứ tự hook** còn phụ thuộc vị trí operator, tính synchronous/async của source và khả năng reentrant request. Không giả định log `doOnNext` luôn xuất hiện trước mọi `doOnSubscribe` trong pipeline; nhưng protocol vẫn đảm bảo subscriber phải nhận onSubscribe trước onNext.

### doFirst theo hướng subscription (ngược thứ tự khai báo)

```java
Mono.just("x")
    .doFirst(() -> System.out.println("one"))
    .doFirst(() -> System.out.println("two"))
    .subscribe();
```

Stdout đồng bộ:

```text
two
one
```

Vì `doFirst` có ý nghĩa “làm trước khi subscribe vào phần upstream”, hook được nối sau chạy trước. Đừng dùng `doFirst` cho non-idempotent mutation rồi dựa vào một subscription duy nhất, bởi subscribe lại/`retry` có thể chạy hook lại.

### doFinally xảy ra sau tín hiệu downstream

Một đoạn:

```java
Mono.just("x")
    .doFinally(signal -> cleanup())
    .subscribe(System.out::println);
```

Đây là snippet cần method `cleanup()`. `doFinally` được gọi khi sequence kết thúc vì complete/error/cancel, nhưng callback này có thể **chạy sau khi tín hiệu đã truyền xuống downstream**. Vì vậy test đọc biến cleanup ngay sau `block()` hoặc `verifyComplete()` đôi khi gặp race tùy scheduling; hãy đồng bộ đúng hoặc dùng công cụ test quan sát signal.

**Phân biệt `doFinally` và `doOnTerminate`:** `doOnTerminate` chỉ quan sát complete/error, không bảo đảm được gọi khi cancel; `doFinally` bao phủ cả cancellation. Với resource mang lifecycle riêng, `using`/`usingWhen` thường an toàn và diễn đạt ownership tốt hơn gắn cleanup thủ công vào mọi nhánh.

## 9. Mono empty, onSuccess(null), lỗi và cancel

`Mono<T>` có ba kiểu completion cơ bản:

```text
success with value: onSubscribe -> onNext(value) -> onComplete
success empty:      onSubscribe -> onComplete
error:              onSubscribe -> onError(ex)
cancel:             onSubscribe -> ...cancel (không nhất thiết onComplete)
```

`Mono#doOnSuccess(Consumer<T>)` khác `doOnNext`: doOnSuccess được gọi cả khi Mono hoàn tất rỗng, với argument `null`; `doOnNext` **không** được gọi cho empty.

Ví dụ:

```java
Mono<String> noValue = Mono.<String>empty()
    .doOnNext(v -> System.out.println("next=" + v))
    .doOnSuccess(v -> System.out.println("success=" + v));

noValue.subscribe();
```

Output:

```text
success=null
```

Đây là **null argument cho hook**, không phải `onNext(null)`. Reactive Streams **không cho phép** publisher gửi null value bằng onNext. Muốn diễn tả không có bản ghi, thường dùng `Mono.empty()` và map tới response status 404/204 phù hợp thay vì `Mono.just(null)`.

Lỗi `onError` là terminal: pipeline upstream không tiếp tục onNext sau lỗi. Operator `onErrorResume` có thể chuyển qua **publisher dự phòng**, không phải “phát tiếp sau terminal” từ subscription cũ. `retry(n)` làm **subscribe lại** vào upstream source, có thể lặp lại side effects.

## 10. Cold và hot: subscribe lại có thể chạy lại hay chia sẻ

### Cold publisher

`Flux.range(1,3)` là cold: mỗi subscription thường tạo một quá trình phát 1,2,3 riêng.

`Mono.defer(() -> repository.findById(id))` là lazy factory, nếu mỗi subscription dẫn tới một truy vấn reactive repository mới, subscribe hai lần có thể chạy truy vấn hai lần.

```java
Mono<String> cold = Mono.fromSupplier(
        () -> expensiveOperation());

cold.subscribe(v -> useFirst(v));
cold.subscribe(v -> useSecond(v));
```

Đây là **snippet** có ba methods được cung cấp bởi ứng dụng. Hai subscriptions là hai cuộc tiêu thụ, và supplier thông thường chạy hai lần. Không gọi `subscribe()` lần một để “warm up” rồi assume lần hai chỉ đọc response có sẵn.

### Hot/shared publisher

Hot sources như một `Sinks.Many` nhận sự kiện kể cả khi subscribers khác nhau hoặc đăng ký muộn. Một số share/replay operators tạo dạng multicast/caching khác nhau:

| Operator/cách dùng | Mục tiêu | Cảnh báo |
| --- | --- | --- |
| `Flux.share()` | Chia sẻ một subscription upstream trong lúc có subscribers | Late subscribers có thể bỏ lỡ dữ liệu cũ |
| `publish().refCount(n)` | Kết nối upstream khi đủ n subscribers, có thể disconnect khi giảm | Ngưỡng/scheduling/reconnect cần test |
| `Mono.cache()` | Cache terminal signal/value cho subscriptions về sau | Giữ lỗi hoặc kết quả lâu, cần cân nhắc TTL và tài nguyên |
| `Flux.cache(history)` | Replay số lượng item đã lưu | Giới hạn history/memory quan trọng |
| `Sinks.Many` | Phát các sự kiện từ producer thủ công | Buffer/overflow/backpressure policy phải rõ |

**Không mặc định cache là an toàn:** cache một response theo user/tenant trên bean singleton có thể làm rò dữ liệu giữa requests; cần cache key, TTL, invalidation và authorization. `Mono.cache()` mặc định có thể giữ kết quả/lỗi vô thời hạn theo lifecycle Mono instance; không dùng tùy tiện cho dữ liệu thay đổi hoặc chứa secrets.

### Hotness không phải đặc tính của Flux type

Hai biến cùng kiểu `Flux<String>` có thể một cold và một hot. Tính hot/cold do **source và sharing strategy**, không do tên lớp.

## 11. retry và repeat đều là re-subscription

`retry` không phải “chạy lại đoạn xử lý đã thất bại trong một subscription đang tiếp tục”. Thông thường nó **subscribe lại upstream** sau onError, chịu ảnh hưởng của `retry(n)`, backoff, loại lỗi và trạng thái nguồn.

```java
AtomicInteger attempts = new AtomicInteger();

Mono<String> result = Mono.fromCallable(() -> {
    int attempt = attempts.incrementAndGet();
    if (attempt < 3) {
        throw new IllegalStateException("temporary");
    }
    return "ok";
}).retry(2);
```

Đây là đoạn trong method có import `java.util.concurrent.atomic.AtomicInteger`. Khi subscribe một lần với source đồng bộ, attempt #1 và #2 lỗi, #3 thành công; `retry(2)` cho phép hai lần thử lại sau lần đầu. Nếu gọi `result.block()` lần thứ hai, đó lại là một subscription khác, biến `attempts` vẫn được giữ bên ngoài nên kết quả có thể khác.

`repeat` tái đăng ký sau complete (theo semantics/operator cụ thể), không chỉ sau error. `retryWhen` có thể dùng retry budget/backoff nhưng phải phân loại lỗi có thể retry; không retry tất cả HTTP 4xx hoặc các write không idempotent.

**Production:** Với payment/order creation, dùng idempotency key và DB uniqueness/transaction để ngăn side effects trùng nếu client retry hoặc Reactor retry; chỉ dựa vào operator là không đủ.

## 12. Thread và scheduler không do subscribe tự sinh

Reactive Streams không bắt buộc callback chạy thread riêng. `Flux.range`/`Mono.just` có thể tạo tín hiệu đồng bộ ngay trong call stack subscription. Reactor `Schedulers` cho phép chuyển execution context:

- `subscribeOn(scheduler)`: ảnh hưởng tới nơi subscription/upstream work được thực thi; thường dùng để chuyển blocking nguồn legacy ra worker.
- `publishOn(scheduler)`: chuyển xử lý downstream kể từ vị trí đó sang worker scheduler.
- `Schedulers.parallel()`: phù hợp CPU tasks ngắn, không dành cho blocking I/O lâu.
- `Schedulers.boundedElastic()`: bounded scheduler dành cho bridge tới blocking legacy code, vẫn tốn threads/queue.
- `WebClient` và reactive database drivers: thường có I/O non-blocking, không cần tự bọc bằng `fromCallable` khi client đã trả Mono/Flux.

Ví dụ tích hợp legacy API:

```java
Mono<String> callLegacy() {
    return Mono.fromCallable(
            () -> legacyJdbcService.queryOne())
        .subscribeOn(Schedulers.boundedElastic());
}
```

Đây là **snippet** cần bean `legacyJdbcService`. Source deferred đến subscription và chạy trên boundedElastic worker; **JDBC vẫn blocking**. Không gọi `Mono.just(legacyJdbcService.queryOne())` trong WebFlux controller, vì `queryOne()` chạy **ngay trên thread gọi method**, có thể là Netty event loop.

### block() cũng tạo subscription

`mono.block()` là một terminal consumer tạo subscription **mới** rồi chờ; không dùng trong WebFlux handler hoặc reactor-netty event-loop. Ở Java console/demo/tests khi ở thread blocking cho phép, `block()` có thể là công cụ minh họa nhưng không phải giải pháp production cho reactive pipeline.

`blockOptional()`/`block(Duration)` cũng khởi tạo subscription; timeout của blocking caller không đồng nghĩa nguồn I/O đã rollback side effects.

## 13. doOnNext và side effects: quan sát khác xử lý nghiệp vụ

`doOnNext` được thiết kế cho logging, metrics, tracing hoặc một side effect kiểm soát được:

```java
Flux<Order> monitored = orders
    .doOnSubscribe(s -> metrics.started())
    .doOnNext(order -> metrics.itemReceived())
    .doOnError(e -> metrics.failed())
    .doFinally(signal -> metrics.finished(signal));
```

Đây là snippet phụ thuộc `orders` và `metrics`. Nếu được subscribe hai lần, các hooks có thể chạy hai lần. Nếu `metrics.finished` ném exception hoặc block, nó làm khó lifecycle; hooks nên nhanh, không làm thay đổi business semantics.

**Không dùng `doOnNext(order -> paymentService.charge(order))` làm cơ chế đảm bảo charge exactly once**. Retry/repeat/multiple subscriptions có thể gọi lại. Nếu side effect là một phần nghiệp vụ, diễn tả nó qua một reactive operator trả Publisher (`flatMap`/`then`) với idempotency và transaction phù hợp; không tạo fire-and-forget subscribe bên trong hook.

### Sai anti-pattern

```java
// Sai: chạy background subscription ngoài quản lý của caller.
return repository.save(order)
    .doOnNext(saved -> sendEmail(saved).subscribe());
```

Đây là **snippet phản ví dụ**, không phải method độc lập. Nếu WebFlux HTTP caller cancel sau khi `repository.save` phát value, email đã được subscribe riêng và có thể tiếp tục độc lập; error của email cũng có thể mất. Một lựa chọn hợp lý hơn:

```java
return repository.save(order)
    .flatMap(saved ->
        sendEmail(saved).thenReturn(saved));
```

Ở đây `sendEmail(saved)` trả `Mono<Void>`; chuỗi cha quản lý completion/error/cancel của email. Cách này vẫn không cung cấp exactly-once transactional email, nên production có thể dùng outbox/message broker/idempotency.

## 14. Quản lý tài nguyên qua complete, error và cancel

Một nguồn có thể mở tệp, database cursor, websocket session hoặc lease. Cleanup chỉ trong `doOnComplete` sẽ bỏ lỡ `onError` và `cancel`.

### Dùng resource scope

Reactor có `Flux.using`/`Mono.using` và `usingWhen`:

- `using(resourceSupplier, sourceFactory, resourceCleanup)`: phù hợp resource xử lý đồng bộ/`AutoCloseable`, liên kết resource lifecycle với subscription.
- `usingWhen(resourcePublisher, resourceClosure, asyncCompleteCleanup, asyncErrorCleanup, asyncCancelCleanup)`: phù hợp resource acquire/cleanup **bất đồng bộ** (ví dụ reactive connection/transaction), với callback riêng cho terminal paths.

Ví dụ một source từ file resource (minh họa):

```java
Flux<String> lines = Flux.using(
    () -> java.nio.file.Files.lines(path),
    stream -> Flux.fromStream(stream),
    java.util.stream.Stream::close);
```

Đây là snippet method đã có `java.nio.file.Path path`, Reactor dependency và import `Flux`. `Files.lines` là blocking file I/O; trên WebFlux không nên chạy nó trực tiếp trên Netty event-loop. Nếu buộc đọc file theo kiểu này, cân nhắc `subscribeOn(Schedulers.boundedElastic())` hoặc API I/O phù hợp, đồng thời giới hạn số file, size và tài nguyên.

Đừng tự giả định `doFinally` luôn hoàn tất cleanup **trước khi** downstream nhận complete. Với `usingWhen`, thứ tự cleanup và truyền terminal signal có semantics riêng, cần kiểm tra tài liệu/phiên bản. Khi có transaction, dùng Reactor-aware transaction management thay vì trộn ThreadLocal JDBC transaction manager.

### Cleanup trên cancel và HTTP disconnect

Khi WebFlux subscriber HTTP bị cancel (client disconnect/timeout downstream), upstream có thể nhận `cancel`. Các callbacks `doFinally(SignalType.CANCEL)`, `usingWhen` asyncCancel handler giúp dọn resources. Tuy nhiên `cancel` **không bảo đảm** network/database request vật lý dừng ngay hoặc rollback side effects đã commit.

Nếu pipeline giữ message broker consumer hoặc event listener, cleanup phải unsubscribe/remove listener; không lưu references trong static map vô hạn. Cancel có thể đến từ downstream operator `take`/`timeout`, không chỉ browser đóng tab.

## 15. onErrorResume, timeout, take và doFinally

Một số operator làm subscription chấm dứt theo các con đường khác nhau:

| Operator/tình huống | Điều có thể xảy ra |
| --- | --- |
| `take(3)` với nguồn còn dữ liệu | Lấy đủ ba phần tử, cancel upstream, downstream có thể complete |
| `timeout(Duration)` | Khi không có tín hiệu đúng hạn, cancel upstream và phát TimeoutException downstream |
| `onErrorResume(e -> fallback)` | Chuyển sang publisher dự phòng khi upstream lỗi |
| `switchIfEmpty(fallback)` | Khi upstream complete empty, subscribe fallback |
| `retry(2)` | Sau onError, subscribe upstream lại tối đa hai lần |
| HTTP client disconnect | Downstream HTTP subscriber có thể cancel |
| `doFinally` | Nhận `ON_COMPLETE`/`ON_ERROR`/`CANCEL` trên boundary tương ứng |

**Quan trọng:** Một pipeline có thể có **nhiều `doFinally` ở các vị trí khác nhau**. Ví dụ source nằm trước `take(3)` nhận cancel, còn downstream sau `take(3)` nhận complete. Vì thế không khẳng định “toàn bộ chuỗi có đúng một signal type duy nhất” khi các operators biến đổi terminal behavior.

Một `Flux` kết thúc rỗng (no values) vẫn có subscription/completion; `switchIfEmpty` có thể kích hoạt một publisher khác. Nếu fallback là `Mono.just(expensiveCall())`, expensive call **đã chạy** khi tạo fallback tại assembly; dùng `Mono.defer(...)` nếu chỉ muốn gọi khi thật sự rỗng.

## 16. Reactor Context: dữ liệu gắn subscription, không phải ThreadLocal

Trong WebFlux, request processing và signals có thể đi qua nhiều threads/schedulers. Do đó không nên dựa vào một `ThreadLocal` thường để giữ request ID, locale, user context hoặc tracing data xuyên suốt pipeline. Reactor có `Context`/`ContextView` gắn với **Subscriber chain**, phù hợp metadata bất biến thuộc subscription.

Ví dụ:

```java
Mono<String> message = Mono.deferContextual(ctx ->
    Mono.just("request=" + ctx.get("requestId")));

String result = message
    .contextWrite(ctx -> ctx.put("requestId", "r-42"))
    .block();
```

Đây là snippet cho main/test thread với Reactor dependency. Giá trị `result` là:

```text
request=r-42
```

Lưu ý `block()` dùng ở ranh giới demo, không trong WebFlux event-loop. Reactor Context không phải một bản sao toàn bộ Servlet request; không tự chứa user/token trừ khi ứng dụng/framework đặt vào đúng context. Security reactive context có APIs riêng của Spring Security.

### Context và subscription boundaries

Mỗi lần subscribe tạo subscription context tương ứng. `contextWrite` ảnh hưởng các operators upstream theo quy tắc Reactor; không phải một global ThreadLocal được update cho mọi task. Nếu bạn tự `subscribe()` bên trong controller/service, bạn có thể tạo **subscription mới không mang cùng Context** của HTTP request, làm mất trace/security metadata.

Muốn truyền metadata qua service, có thể dùng method params explicit hoặc `Mono.deferContextual` có chủ đích, không giấu business inputs trong context khi chúng nên là tham số rõ ràng.

## 17. WebFlux: framework subscribe ở HTTP boundary

Trong controller WebFlux:

```java
@GetMapping("/products/{id}")
public Mono<ProductDto> one(@PathVariable String id) {
    return productService.find(id);
}
```

Controller chỉ trả một Publisher. **Spring WebFlux HTTP infrastructure là downstream subscriber**, đăng ký để đọc kết quả và ghi response. Nó sở hữu phần quan trọng của lifecycle: demand, response encoding, cancellation và xử lý errors trước khi response commit.

Không cần (và thường không được) làm:

```java
@GetMapping("/bad")
public Mono<Void> bad() {
    productService.saveSomething().subscribe();
    return Mono.empty();
}
```

Đây là **anti-pattern minh họa**, không phải file có sẵn. Server có thể gửi HTTP thành công trước khi `saveSomething` hoàn tất, lỗi bị tách khỏi HTTP, cancellation không lan truyền và context có thể thất lạc.

Thay bằng:

```java
@GetMapping("/good")
public Mono<Void> good() {
    return productService.saveSomething();
}
```

Đây cũng là snippet cần bean. Khi method trả Publisher, framework quản lý subscription. Nếu operation đã là background workflow độc lập, hãy dùng queue/broker/task management có giám sát, không giấu nó trong `subscribe()` của một request handler.

### Controller method gọi lúc nào?

**Sự kiện gọi method controller để tạo Mono/Flux là phần xử lý HTTP request; nó có thể chạy trước khi publisher được subscribe.** Nếu code gọi hàm blocking trước khi return, ví dụ `Mono.just(repository.find())`, hàm đó chạy ngay khi handler được gọi. **Lazy là logic được hoãn trong Publisher, không phải toàn bộ method controller.**

## 18. Ví dụ Spring Boot WebFlux có thể chạy: eager, lazy, request lifecycle

Tạo một Maven project từ Spring Initializr:

- Java 17+ và Spring Boot 3.x.
- `spring-boot-starter-webflux`.
- `spring-boot-starter-test` (test scope) và `io.projectreactor:reactor-test` (test scope nếu chưa có).
- **Không thêm `spring-boot-starter-web`** nếu bạn muốn Boot tự chọn WebFlux reactive application type khi cả hai starters có mặt.

Cấu trúc:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  SequenceService.java
  SequenceController.java
src/test/java/com/example/demo/
  SequenceControllerTest.java
  LifecycleTest.java
```

App không cần database. `AtomicInteger` tạo số đếm per service singleton, giúp quan sát hành vi khi có các HTTP requests; đây là teaching demo, không phải nguồn sequence/version phân tán an toàn.

### 18.1. DemoApplication.java

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### 18.2. SequenceService.java

```java
package com.example.demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SequenceService {

    private final AtomicInteger sequence = new AtomicInteger();

    public Mono<ValueResponse> nextValue() {
        // Tạo một publisher tại invocation time; chưa tăng counter.
        return Mono.fromSupplier(() ->
            new ValueResponse(sequence.incrementAndGet()));
    }

    public Mono<ValueResponse> echoLazy(String value) {
        // Mono.defer tạo Publisher tại subscription time.
        return Mono.defer(() -> Mono.just(
            new ValueResponse(value.length())));
    }

    public record ValueResponse(int value) {}
}
```

`sequence.incrementAndGet()` chỉ chạy khi có subscription tới `nextValue()`, không phải ngay khi method service return Mono. `echoLazy` dùng `defer` nhằm minh họa factory theo subscription; với chuỗi immutable đã biết `value.length()` không tốn đáng kể, nên trong production không nhất thiết phải dùng `defer` cho việc nhỏ như vậy.

### 18.3. SequenceController.java

```java
package com.example.demo;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/lifecycle")
public class SequenceController {

    private final SequenceService service;

    public SequenceController(SequenceService service) {
        this.service = service;
    }

    @GetMapping(path = "/next",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SequenceService.ValueResponse> next() {
        // WebFlux subscribe, controller KHÔNG gọi subscribe/block.
        return service.nextValue();
    }

    @GetMapping(path = "/double",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SequenceService.ValueResponse> doubleNext() {
        // zip hai Mono khác nhau tạo hai subscriptions tới source.
        return Mono.zip(service.nextValue(), service.nextValue())
            .map(tuple -> new SequenceService.ValueResponse(
                tuple.getT1().value() + tuple.getT2().value()));
    }

    @GetMapping(path = "/stream/{count}",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SequenceService.ValueResponse> stream(
            @PathVariable int count) {

        if (count < 1 || count > 5) {
            return Flux.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "count must be 1..5"));
        }

        return Flux.range(1, count)
            .delayElements(Duration.ofMillis(80))
            .map(SequenceService.ValueResponse::new);
    }

    @GetMapping(path = "/empty",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SequenceService.ValueResponse> empty() {
        return Mono.empty();
    }
}
```

**Giải thích endpoint:**

- `GET /next` trả một `Mono.fromSupplier`; mỗi HTTP request thông thường có subscription riêng, làm counter tăng một lần. Nếu proxy/client retry HTTP, counter có thể tăng thêm; không dùng GET có side effects quan trọng như demo này trong production.
- `GET /double` tạo **hai Mono khác nhau** và `zip` subscribe cả hai; khi có một subscription vào zip, counter thường tăng hai lần và output là tổng hai số liên tiếp, nhưng concurrency/scheduling giữa nhiều HTTP requests khiến tổng không deterministic. Dùng bài test riêng/isolated để kiểm.
- `GET /stream/{count}` phát một `Flux` finite dưới dạng server-sent events; `delayElements` dùng scheduler theo thời gian, không bảo đảm đúng từng millisecond.
- `GET /empty` complete không có item; WebFlux có thể tạo một response rỗng với status mặc định tùy handling. Nếu muốn **404** rõ ràng khi không có dữ liệu, dùng `switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))` hoặc `ResponseEntity` phù hợp.
- Controller không tự `subscribe()`. Nếu client dừng đọc stream, WebFlux có thể cancel subscription và Reactor dọn timers/operators theo semantics.

**Lưu ý API design:** Demo `GET /next` cố ý có side effect tăng counter để dễ quan sát subscription, **không phù hợp HTTP safe-method semantics trong dịch vụ thực**. Với state mutation, dùng POST/PUT đúng business contract, authorization/idempotency và transaction. Nếu không muốn client caching/prefetch làm thay đổi counter, đừng sử dụng GET cho side effects ngoài demo.

### 18.4. Chạy demo

Với Maven wrapper của project:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Mặc định server nhận ở port 8080 khi chưa tùy chỉnh `server.port`. Mã trên cần classpath Spring Boot/Reactor/Jackson; `javac SequenceController.java` riêng lẻ **không** đủ dependencies.

## 19. curl: quan sát subscription và response

### Mỗi GET /next sinh một kết quả

```bash
curl -i 'http://localhost:8080/api/lifecycle/next'
curl -i 'http://localhost:8080/api/lifecycle/next'
```

Nếu mới khởi động app và không có request cạnh tranh đến `/next` hoặc `/double`, JSON lần lượt:

```json
{"value":1}
```

và:

```json
{"value":2}
```

Counter nằm trong một singleton service và reset khi restart; **không** kết luận mọi môi trường/tất cả concurrent requests đều cho đúng dãy 1,2. Nếu HTTP client tự retry, một request người dùng có thể có nhiều subscription ở server.

### Hai subscriptions trong một request /double

```bash
curl -i 'http://localhost:8080/api/lifecycle/double'
```

Nếu counter hiện là 2 và không có concurrent calls, hai `nextValue()` suppliers trả 3 và 4; response:

```json
{"value":7}
```

`Mono.zip` chỉ hoàn tất khi cả hai sources có value; nếu một source `Mono.empty()`, zip có thể complete empty và các source khác có thể bị cancel. Đừng dùng zip cho việc cần bảo đảm cả hai side effects được thực thi dù một source empty.

### Streaming events

```bash
curl -N -i 'http://localhost:8080/api/lifecycle/stream/3'
```

Dữ liệu SSE (minh họa phần body):

```text
data:{"value":1}

data:{"value":2}

data:{"value":3}

```

Lệnh `-N` giảm buffering từ curl. Thời điểm các events phụ thuộc scheduler/network, không lấy thời gian 80ms làm hard guarantee. Một event không nhất thiết trùng một TCP packet.

### Empty Mono và lỗi validation

```bash
curl -i 'http://localhost:8080/api/lifecycle/empty'
curl -i 'http://localhost:8080/api/lifecycle/stream/100'
```

`/empty` có HTTP response không có JSON value; `/stream/100` phát lỗi 400 nếu chưa commit response. Cách trình bày body lỗi phụ thuộc Spring Boot/Problem Details configuration; không hardcode JSON nếu chưa định nghĩa error handler riêng.

## 20. StepVerifier: test chính xác vòng đời trong Reactor

Với `reactor-test`, kiểm thử subscription được trigger tại `verify...()`, không phải ngay khi tạo biến StepVerifier. Tệp `src/test/java/com/example/demo/LifecycleTest.java`:

```java
package com.example.demo;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleTest {

    @Test
    void justCapturesBeforeSubscription() {
        AtomicInteger counter = new AtomicInteger();

        Mono<Integer> eager = Mono.just(counter.incrementAndGet());

        assertEquals(1, counter.get());
        StepVerifier.create(eager)
            .expectNext(1)
            .verifyComplete();
        StepVerifier.create(eager)
            .expectNext(1)
            .verifyComplete();
        assertEquals(1, counter.get());
    }

    @Test
    void deferFactoryRunsOnEverySubscription() {
        AtomicInteger counter = new AtomicInteger();

        Mono<Integer> lazy = Mono.defer(() ->
            Mono.just(counter.incrementAndGet()));

        assertEquals(0, counter.get());

        StepVerifier.create(lazy)
            .expectNext(1)
            .verifyComplete();
        StepVerifier.create(lazy)
            .expectNext(2)
            .verifyComplete();

        assertEquals(2, counter.get());
    }

    @Test
    void backpressureRequiresRequestForOnNext() {
        StepVerifier.create(Flux.range(1, 3), 0)
            .expectSubscription()
            .thenRequest(1)
            .expectNext(1)
            .thenRequest(2)
            .expectNext(2, 3)
            .verifyComplete();
    }

    @Test
    void emptyMonoCompletesWithoutValue() {
        StepVerifier.create(Mono.empty(), 0)
            .expectSubscription()
            .verifyComplete();
    }

    @Test
    void retryTriggersNewSourceSubscriptions() {
        AtomicInteger count = new AtomicInteger();

        Mono<String> eventual = Mono.defer(() -> {
            int attempt = count.incrementAndGet();
            if (attempt < 3) {
                return Mono.error(
                    new IllegalStateException("temporary"));
            }
            return Mono.just("ok");
        }).retry(2);

        StepVerifier.create(eventual)
            .expectNext("ok")
            .verifyComplete();

        assertEquals(3, count.get());
    }

    @Test
    void cancellationStopsFiniteSequence() {
        StepVerifier.create(Flux.range(1, 100), 0)
            .expectSubscription()
            .thenRequest(2)
            .expectNext(1, 2)
            .thenCancel()
            .verify();
    }

    @Test
    void timeoutPropagatesErrorWithVirtualTime() {
        StepVerifier.withVirtualTime(() ->
                Mono.never().timeout(Duration.ofSeconds(5)))
            .expectSubscription()
            .thenAwait(Duration.ofSeconds(5))
            .expectError(java.util.concurrent.TimeoutException.class)
            .verify();
    }
}
```

**Kiểm thử này không dùng network** và không giả định một executor bất kỳ. `StepVerifier.withVirtualTime` yêu cầu publisher với time operators được tạo **bên trong lambda supplier** như ở ví dụ; tạo `Mono.delay` trước lambda có thể làm virtual-time test sai. Khi cần biết `doFinally` đã chạy, hãy xét callback có thể được thực thi sau downstream terminal, dùng đồng bộ test đúng thay vì đọc một boolean ở thời điểm chưa bảo đảm.

Đặt `StepVerifier.create(publisher, 0)` rồi `expectNext(...)` ngay **không** `thenRequest(...)` sẽ khiến source đúng protocol không phát item; test không phải chứng minh source lỗi. Cần có `expectSubscription` hoặc request step phù hợp.

## 21. WebTestClient: test HTTP WebFlux từ controller

Tệp `src/test/java/com/example/demo/SequenceControllerTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SequenceControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void nextProducesOneValue() {
        client.get().uri("/api/lifecycle/next")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.value").isNumber();
    }

    @Test
    void invalidStreamingCountReturns400() {
        client.get().uri("/api/lifecycle/stream/100")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void streamCompletesAfterThreeEvents() {
        Flux<String> response = client.get()
            .uri("/api/lifecycle/stream/3")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody();

        StepVerifier.create(response)
            .expectNextMatches(s -> s.contains("\"value\":1"))
            .expectNextMatches(s -> s.contains("\"value\":2"))
            .expectNextMatches(s -> s.contains("\"value\":3"))
            .verifyComplete();
    }
}
```

**SSE codec caveat:** String decoding với `text/event-stream` phụ thuộc message readers và cấu hình WebTestClient/Reactor Netty; trong dự án thực có thể decode thành `ServerSentEvent<ValueResponse>` hoặc object DTO bằng `ParameterizedTypeReference` phù hợp nếu codec không trả mỗi event thành một String. Đừng coi mỗi TCP chunk là một Reactor element, và đừng khẳng định streaming luôn flush đúng từng khoảng thời gian.

**Độ ổn định của test:** Không assert counter chính xác giữa nhiều test HTTP vì singleton state có thể được chia sẻ với test khác và JUnit chạy theo thứ tự không cố định. Với Spring Security, test cần user/authorization/CSRF theo endpoint. Mock WebTestClient và HTTP server thực có khác biệt về transport/disconnect, nên cần thêm integration tests để xác minh cancellation từ client.

Chạy:

```bash
./mvnw test
```

PowerShell:

```powershell
.\mvnw.cmd test
```

Đây là ví dụ có thể chạy trong Boot 3 project đúng dependencies; không phải tuyên bố đã chạy trên workspace người dùng.

## 22. Side effects, HTTP, WebClient và retry đúng ranh giới

### WebClient Mono thường không gửi HTTP khi chỉ được dựng

Ví dụ:

```java
Mono<ProductDto> product = webClient.get()
    .uri("/products/{id}", id)
    .retrieve()
    .bodyToMono(ProductDto.class);
```

Đây là snippet trong class có `webClient`, `id` và `ProductDto`. Việc dựng `Mono` **thường chưa gửi HTTP request**; network exchange diễn ra khi subscriber subscribe. Nếu gọi `product.block()` hai lần hoặc có hai independent subscribers tới nguồn cold, có thể có **hai outbound HTTP requests**, trừ khi source có sharing/cache semantics khác.

Với WebFlux controller, **trả Mono này cho framework**, không `subscribe()` để “gửi request trước” rồi return Mono khác.

### Khi side effect phải chạy chính xác một lần

Reactive Streams không cung cấp exactly-once mutation semantics. Nếu một `POST`/payment pipeline có:
- client retry HTTP,
- `retryWhen`,
- duplicate subscription,
- network timeout sau server commit,

thì side effects có thể chạy lại. Phải có idempotency keys, transaction/outbox, uniqueness constraints, authorized ownership và retry policy. Cache một Mono trong memory **không thay thế** cơ chế lưu durable cho exactly-once trên nhiều pods/restarts.

### Không trộn blocking service call vào assembly

Sai:

```java
@GetMapping("/bad")
public Mono<String> bad() {
    return Mono.just(jdbcService.fetch());
}
```

Ở đây `jdbcService.fetch()` block ngay khi handler method chạy. Nếu bắt buộc tích hợp legacy JDBC:

```java
@GetMapping("/bridge")
public Mono<String> bridge() {
    return Mono.fromCallable(() -> jdbcService.fetch())
        .subscribeOn(Schedulers.boundedElastic());
}
```

Đây là **snippets** cần bean `jdbcService` và imports; muốn code compile trong sample controller phải thêm dependency/data service tương ứng. BoundedElastic chỉ **di chuyển** blocking work sang bounded workers; không biến JDBC thành reactive driver hoặc tự giải quyết connection pool, timeout, cancellation.

## 23. Những sai lầm cần tránh

1. **“Khai báo Flux tức là chạy dữ liệu”**: với cold source, chưa subscribe thường chưa phát data.
2. **“Mọi thứ trong Mono đều lazy”**: argument `Mono.just(expensiveCall())` đã chạy tại assembly.
3. **“Subscribe luôn chạy trên thread khác”**: nguồn đồng bộ như `Flux.range` có thể phát ngay trên cùng thread.
4. **“subscribe không có request thì vẫn onNext”**: publisher đúng protocol phải đợi demand.
5. **“block() không ảnh hưởng”**: nó tạo subscription mới, chặn thread và có thể lặp side effects.
6. **Tự subscribe trong WebFlux controller**: đánh mất response lifecycle/error/cancellation control.
7. **Đọc resource trong controller trước khi return Mono**: có thể block event loop.
8. **Dùng `Mono.just(null)`**: onNext(null) không hợp lệ; dùng Mono.empty/justOrEmpty.
9. **Dùng `doOnNext` để gửi email/thanh toán**: có thể chạy nhiều lần khi retry hoặc multi-subscribe.
10. **Tin cold source được cache tự động**: mỗi subscription có thể query lần nữa.
11. **Tin `Mono.cache()` phù hợp dữ liệu user nhạy cảm**: cần key, TTL, auth, invalidation.
12. **Dùng `retry` với non-idempotent POST**: có thể tạo duplicate records/charges.
13. **Tin `onErrorResume` tiếp tục upstream sau onError**: thực chất chuyển sang fallback publisher.
14. **Tin `take(3)` luôn gửi complete tới upstream**: thường cancel upstream sau đủ dữ liệu.
15. **Chỉ cleanup trong `doOnComplete`**: bỏ sót lỗi và cancel.
16. **Cho rằng doFinally đã chạy trước khi downstream thấy completion**: callback thường sau terminal propagation.
17. **Gọi Subscription.request(0) để tạm dừng**: sai protocol.
18. **Kỳ vọng request(1) cuối pipeline = request(1) database**: operator prefetch/reshaping.
19. **Dùng ThreadLocal như request context trên mọi scheduler**: Reactor Context có semantics riêng.
20. **Cho rằng cancel rollback side effects**: resource/task có thể đã commit.
21. **Đặt một hot source vào pipeline rồi giả định mọi subscriber thấy lịch sử**: replay phải cấu hình.
22. **Không giới hạn buffers từ hot source**: nguy cơ OOM.
23. **Dùng StepVerifier với initialRequest=0 và expectNext ngay**: test bị treo vì thiếu demand.
24. **Đặt source timed ngoài withVirtualTime lambda**: không điều khiển được scheduler ảo đúng.
25. **Hardcode thứ tự/tên thread khi có publishOn**: scheduler semantics cần test có kiểm soát.
26. **Dùng block trong WebFlux event loop**: thread starvation hoặc Reactor NonBlockingThread exception.
27. **Không giải phóng file/connection khi client disconnect**: leak.
28. **Không test no-value/error/cancel, chỉ test happy path**: bỏ sót terminal lifecycle.
29. **Dùng GET có side effect mutation để biểu diễn API thật**: vi phạm kỳ vọng safe method/retry/cache.
30. **Nghĩ HTTP response hoàn tất ngay khi controller return Mono**: nó hoàn tất khi publisher xử lý terminal và writer flush.
31. **Lấy một Reactive Repository Mono rồi subscribe trong service**: ngắt transaction/retry/error tracing khỏi caller.
32. **Gộp mọi lỗi thành Mono.empty**: đánh mất lý do failure; phân biệt 404/no value với outage.
33. **Không xét cancellation khi kết nối SSE dài**: giữ timer/broker subscription không cần thiết.
34. **Tin Mono.fromCallable không block**: Callable vẫn có thể block thread nó chạy.
35. **Mỗi lần subscribe một publisher hot cũng tạo HTTP request mới**: hot/shared semantics phải được xem theo implementation.

## 24. Checklist dùng trong code review và production

### Dựng pipeline và subscribe

- [ ] Mọi lời gọi có side effects tại assembly time được xác định rõ?
- [ ] `Mono.just(...)` chỉ nhận value đã có, không bao một blocking call ngoài ý muốn?
- [ ] Khi cần value per subscription, dùng `defer/fromSupplier/fromCallable` đúng?
- [ ] Biết publisher cold/hot/shared/cache hay không trước khi tạo nhiều subscriptions?
- [ ] Các nguồn có state mutable per subscription được tạo đúng chỗ (defer nếu cần)?
- [ ] WebFlux controller trả Mono/Flux, không `subscribe/block`?
- [ ] Background job thật sự độc lập có executor/queue/monitoring riêng?

### Signals, error, cancel, cleanup

- [ ] Các callbacks terminal không bị phát hai lần?
- [ ] `request(n)` dương và demand không bị vượt?
- [ ] Các operators `flatMap`/`buffer`/`publishOn` có resource/concurrency/prefetch budget?
- [ ] `retry/repeat` có phân loại lỗi, backoff và idempotency?
- [ ] Cleanup có đường hoàn tất, lỗi **và cancellation**?
- [ ] `using/usingWhen` được dùng khi sở hữu connection/file resource?
- [ ] Event/broker subscriptions được dọn khi client disconnect?
- [ ] Không coi cancellation là transaction rollback?
- [ ] Reactor Context/security/trace được bảo toàn qua publisher chain?

### Kiểm thử

- [ ] Test phân biệt eager `just` và lazy `fromSupplier/defer`?
- [ ] Test hai subscriptions kích hoạt bao nhiêu lần supplier?
- [ ] Test demand ban đầu 0, `thenRequest` và `thenCancel`?
- [ ] Test `Mono.empty`, onError, fallback và retry resubscription?
- [ ] Test timeout bằng `withVirtualTime` và lazy publisher supplier?
- [ ] Test SSE/JSON HTTP qua WebTestClient/curl đúng lifecycle?
- [ ] Không assert timer hoặc scheduler thread name cứng trong test async?
- [ ] HTTP client disconnect được integration-test nếu API streaming dài?
- [ ] Không cache/replay dữ liệu riêng tư giữa users/tenants?

## 25. Câu hỏi tự kiểm tra

**1.** Khi `Mono.just(service.load())` được tạo, `service.load()` chạy lúc nào?

**Đáp án:** Ngay tại assembly khi đánh giá argument trước lời gọi `just`; không đợi subscribe.

**2.** Khi `Mono.fromSupplier(service::load)` được tạo, method chạy khi nào?

**Đáp án:** Khi có subscription và source được thực thi; mỗi subscription thông thường gọi supplier lại.

**3.** `Mono.defer(...)` khác `fromSupplier(...)` ra sao?

**Đáp án:** `defer` nhận factory tạo Publisher mới theo subscription; `fromSupplier` nhận factory cho một value.

**4.** `publisher.subscribe(subscriber)` trả kiểu gì theo Reactive Streams JVM spec?

**Đáp án:** `void`; Subscription đi qua `subscriber.onSubscribe(subscription)`. Một số Reactor overload subscribe trả `Disposable`.

**5.** Chỉ subscribe mà không request có bảo đảm nhận onNext không?

**Đáp án:** Không; publisher chỉ được phát onNext khi có demand, nhưng có thể onComplete/onError không cần demand.

**6.** Vì sao hai lần `mono.block()` có thể gửi hai outbound HTTP requests?

**Đáp án:** Mỗi block là subscription mới. Nếu nguồn cold như WebClient body Mono chưa cache/share, mỗi subscription có thể tạo một exchange mới.

**7.** `doOnSuccess(null)` có phải là Reactor phát onNext(null) không?

**Đáp án:** Không; hook dùng null để báo Mono complete empty. `onNext(null)` bị cấm.

**8.** Khi `Flux.take(3)` đủ ba item, upstream thường nhận gì?

**Đáp án:** Cancel upstream; downstream nhận completion, hai boundary có thể có `doFinally` signal type khác nhau.

**9.** `doFinally` xử lý những con đường kết thúc nào?

**Đáp án:** Complete, error và cancel. Cleanup có thể chạy sau khi terminal đã tới downstream; để quản lý resource hãy cân nhắc using/usingWhen.

**10.** Có nên `repository.save(...).subscribe()` trong controller rồi return `Mono.empty()`?

**Đáp án:** Không. Nó tạo fire-and-forget ngoài HTTP lifecycle, mất propagated error/cancel/trace. Trả publisher cho framework quản lý.

**11.** `retry(2)` có chỉ “tiếp tục” một Subscription thất bại?

**Đáp án:** Không; thường subscribe lại upstream tối đa hai lần nữa, có thể chạy lại side effects.

**12.** Một `Flux` có chắc cold và phát lại toàn bộ cho late subscriber?

**Đáp án:** Không. Hot/shared sources có thể không replay; cache/replay có policy riêng.

**13.** `subscribeOn(boundedElastic)` biến JDBC thành non-blocking driver không?

**Đáp án:** Không. Nó di chuyển blocking call sang worker pool, JDBC vẫn blocking.

**14.** Khi WebFlux handler trả Mono nhưng không tự subscribe, ai subscribe?

**Đáp án:** Spring WebFlux HTTP infrastructure, để quản lý viết response và cancellation.

**15.** Reactor Context có phải một global ThreadLocal không?

**Đáp án:** Không. Nó gắn với Subscriber chain và được truyền theo protocol/scheduling rules, khác biến thread-local thông thường.

## 26. Tài liệu tham khảo

- Project Reactor reference — Reactor Core, building and subscribing: https://projectreactor.io/docs/core/release/reference/
- Reactor — Simple ways to create a Flux/Mono and subscribe: https://projectreactor.io/docs/core/release/reference/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html
- Reactor — Which operator do I need? `defer`, `fromCallable`, `just`: https://projectreactor.io/docs/core/release/reference/apdx-operatorChoice.html
- Reactor Core API — `Mono`, `doFirst`/`doFinally`/`doOnSuccess`: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html
- Reactor Core API — `Flux`, `using`/`usingWhen` and lifecycle: https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html
- Reactor — Testing with `StepVerifier`: https://projectreactor.io/docs/core/release/reference/testing.html
- Reactor Test API — `StepVerifier.withVirtualTime`: https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html
- Reactor — Schedulers and threading: https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html
- Reactor — Adding a Context: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html
- Spring Framework — WebFlux overview/non-blocking principles: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Spring Framework — WebFlux annotated controllers: https://docs.spring.io/spring-framework/reference/web/webflux/controller.html
- Spring Framework — WebFlux return values: https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html
- Spring Framework — WebTestClient: https://docs.spring.io/spring-framework/reference/testing/webtestclient.html
- Reactive Streams JVM specification and TCK: https://github.com/reactive-streams/reactive-streams-jvm
- Java 17 — Flow API and reactive streams: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Flow.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-foundations-004">Event loop, mô hình luồng và tác hại của thao tác chặn</a>

<details>
<summary>Xem chi tiết</summary>

# Event loop, mô hình luồng và tác hại của thao tác chặn trong Spring WebFlux

## 1. Bài toán: vì sao một lệnh `sleep` có thể làm nghẽn cả WebFlux?

Giả sử một server WebFlux dựa trên Reactor Netty tiếp nhận đồng thời nhiều request. Mỗi request cần chờ một dịch vụ ngoài 300 ms. Nếu xử lý bằng I/O bất đồng bộ đúng cách, event-loop thread **không phải nằm chờ** response; nó có thể tiếp tục phục vụ các kết nối khác và được đánh thức khi có sự kiện I/O hoàn tất.

Ngược lại, nếu trong handler bạn thực hiện:

~~~java
@GetMapping("/bad")
public Mono<String> bad() throws InterruptedException {
    Thread.sleep(300);              // CHẶN request/event-loop thread!
    return Mono.just("done");
}
~~~

thì `Thread.sleep(300)` chạy **ngay lúc Spring gọi controller method**, trước khi `Mono.just` được tạo. Trên mô hình Netty event-loop thông thường, điều này có thể chiếm một event-loop thread trong 300 ms. Các kết nối khác được gán tới loop đó phải chờ; kết quả có thể là latency tăng đột biến, throughput giảm, timeout và "cascading failure" khi tải cao.

**`Mono<T>` không tự biến một phép tính chặn thành non-blocking.** Một API trả kiểu reactive nhưng vẫn gọi `Thread.sleep`, `Future.get`, `RestTemplate`, JDBC đồng bộ hoặc `block()` trong sai ngữ cảnh vẫn có thể làm tắc luồng xử lý.

Bài này giải thích chính xác:
1. Event loop xử lý sự kiện như thế nào và vì sao cần giữ mỗi callback ngắn.
2. Reactor Netty dùng bao nhiêu nhóm thread về mặt mô hình, không nhầm một request với một thread.
3. `Mono`/`Flux`, subscription, operators và `Scheduler` liên quan tới thread thế nào.
4. Phân biệt `publishOn`, `subscribeOn` và quy tắc cô lập blocking code.
5. Vì sao `.block()`, `Thread.sleep`, blocking JDBC/HTTP/file I/O và CPU-heavy work gây hại ở các mức độ khác nhau.
6. Cách dùng `Schedulers.boundedElastic()` hoặc một **bounded scheduler riêng** cho thư viện legacy.
7. Cách dùng WebClient/R2DBC để non-blocking end-to-end.
8. Timeout, cancellation, overload, backpressure, context propagation, monitoring và test.

Ví dụ runnable dùng Java 17, Spring Boot **3.3.5**, Spring WebFlux và Reactor Netty. Một vài giá trị implementation (số event-loop threads, tên threads, scheduler defaults) thay đổi theo CPU, container, Reactor release và configuration: **không hard-code chúng thành cam kết API**.

## 2. Hiểu ba thuật ngữ trước khi đọc code

### 2.1. Concurrency (đồng thời) khác parallelism (song song)

**Concurrency** là quản lý tiến độ nhiều tác vụ chồng lấn về thời gian, có thể chỉ sử dụng một CPU/thread nếu các tác vụ dành nhiều thời gian chờ I/O.

**Parallelism** là thực thi nhiều công việc **đồng thời ở mức vật lý** trên nhiều CPU cores.

Một event loop trên một thread có thể phục vụ hàng nghìn connections nhờ hoạt động event-driven, nhưng nó không thể thực thi đồng thời hai đoạn CPU code trên chính thread đó.

Ví dụ:
- 1.000 HTTP connections đang đợi network response không đồng nghĩa cần 1.000 threads.
- 1.000 phép tính CPU nặng cũng không thể tự chạy nhanh chỉ vì được trả về dưới dạng `Flux`.
- Một blocking JDBC query vẫn cần một thread bị chiếm trong khoảng thời gian query chờ nếu dùng driver JDBC truyền thống.

### 2.2. Blocking và non-blocking I/O

**Blocking I/O** thường khiến thread gọi phải chờ tới khi có dữ liệu hoặc kết thúc timeout, ví dụ:

~~~java
String body = legacyHttpClient.executeSynchronousRequest();
~~~

Đây là fragment giả định có biến `legacyHttpClient`, không phải Java SDK cụ thể. Khi network chậm, thread gọi bị chiếm mặc dù CPU phần lớn rảnh.

**Non-blocking I/O** đăng ký quan tâm tới sự kiện đọc/ghi hoặc completion. Thread có thể quay lại xử lý việc khác; khi socket sẵn sàng, event loop nhận notification và tiếp tục pipeline:

~~~java
Mono<String> body =
        webClient.get()
                .uri("/resource")
                .retrieve()
                .bodyToMono(String.class);
~~~

Đây cũng là fragment; `webClient` phải được cấu hình bằng `WebClient` và connector reactive. Đoạn này **không gửi request ngay tại thời điểm tạo pipeline**; thông thường HTTP exchange bắt đầu khi có subscription. Không gọi `body.block()` trong handler WebFlux để biến code trở lại đồng bộ.

### 2.3. "Không bị chặn" khác "không tốn thời gian"

Non-blocking không làm network round-trip từ 300 ms thành 3 ms. Nó giúp thread không bị giữ trong thời gian chờ, nên một số lượng thread nhỏ có thể phục vụ nhiều yêu cầu đang chờ I/O. Tổng latency của request vẫn bao gồm network, server downstream, data decoding, retry và queuing.

## 3. Event loop hoạt động thế nào?

Event loop là cơ chế liên tục chờ và xử lý các sự kiện như socket readable/writable, connection accepted, scheduled timer hoặc task callback. Về khái niệm:

~~~text
while (serverIsRunning) {
    events = waitForReadyEvents();
    for (event : events) {
        handleSmallNonBlockingStep(event);
    }
}
~~~

Đây là **pseudocode**, không phải mã nguồn thực của Netty. Điểm quyết định: `handleSmallNonBlockingStep` phải nhanh và tránh chờ dữ liệu hoặc lock dài. Khi công việc chưa sẵn sàng, đăng ký callback/continuation rồi trao quyền cho event loop xử lý sự kiện khác.

### 3.1. Sơ đồ hai requests

~~~text
                    Một event-loop thread
                           |
time -->  [read A] [read B] [schedule I/O A]
                           |
          [handle B] [write B] [read C]
                           |
             ...network response A arrives...
                           |
          [resume A] [serialize A] [write A]
~~~

Đây là mô hình logic, không khẳng định chỉ có duy nhất một loop của server. Reactor Netty thường quản lý một nhóm worker event loops; một connection có thể gắn với loop nhất định, và một loop phục vụ nhiều connections.

Khi A phải đợi dịch vụ khác, event loop không nên nằm yên trong A; nó tiếp tục B/C. Nếu A chạy `Thread.sleep(1000)` ngay trên loop, B/C trong cùng loop có thể bị trì hoãn đáng kể.

### 3.2. Những gì event loop thực sự làm

- Nhận/đọc/ghi dữ liệu qua socket và phối hợp Netty channel pipeline.
- Kích hoạt downstream reactive callbacks/operators.
- Thực hiện code user mà framework gọi **nếu chưa có scheduler switch**.
- Xử lý timer/scheduled tasks và lifecycle callbacks.
- Phối hợp backpressure và write readiness theo implementation.

Event loop **không** phải một thread riêng cho mỗi HTTP request. Cũng không có lời hứa rằng một request luôn chạy trên cùng thread từ đầu đến cuối nếu pipeline có `publishOn`, `subscribeOn`, thư viện bất đồng bộ hay các scheduler khác.

### 3.3. Reactor Netty và client/server loops

Trong một ứng dụng Boot WebFlux chạy Reactor Netty, bạn thường thấy thread names mang tiền tố như:

~~~text
reactor-http-nio-1
reactor-http-nio-2
...
~~~

Đây là **ví dụ tên threads thường gặp**, không phải giá trị cố định, API contract hoặc căn cứ cho production authorization. Reactor Netty `WebClient` và HTTP server **mặc định có thể chia sẻ event-loop resources** khi cùng dùng Reactor Netty.

Hệ quả: một handler server vô tình chặn event loop có thể đồng thời làm trì hoãn I/O progress của các outbound WebClient requests trên shared resources. Trong production có thể cấu hình `LoopResources`, connection pool hoặc client/server resources riêng, nhưng **tăng số loop không phải phương pháp chữa blocking code**.

Không khẳng định "Netty luôn có đúng 4 threads" hay "mỗi core có 2 event loops": tài liệu, CPU count, cgroup/container limits và configuration thay đổi defaults.

## 4. Mô hình luồng: Spring MVC và Spring WebFlux

| Đặc điểm | Servlet Spring MVC đồng bộ | Spring WebFlux trên Reactor Netty |
| --- | --- | --- |
| Thread cho request | Thường một servlet worker bị giữ khi controller blocking | Số event-loop threads nhỏ phục vụ nhiều connections |
| Giả định thiết kế mặc định | Blocking API được chấp nhận trong giới hạn container thread pool | User code trên event loop phải non-blocking |
| Xử lý I/O chậm | Thread thường chờ | Đăng ký completion và tiếp tục khi socket sẵn sàng |
| Return type | DTO/ResponseEntity hoặc async types | `Mono<T>`, `Flux<T>`, các publishers/adapters |
| Gọi JDBC blocking | Thường trực tiếp trên worker pool | Không gọi trên event loop; cần isolate hoặc đổi stack |
| HTTP client non-blocking | Có thể dùng `WebClient` | `WebClient` là lựa chọn tự nhiên |
| Khi tải vượt năng lực | Queue/worker saturation | Event-loop starvation, scheduler/connection pool saturation |
| Phù hợp | CRUD, JDBC và ecosystem blocking rộng | High concurrency I/O, reactive stack end-to-end |

**Không biến so sánh thành khẩu hiệu “WebFlux luôn nhanh hơn MVC”.** Spring Framework giải thích lợi ích chính của reactive stack là **scalability/predictable resource usage** với workload chờ I/O, chứ không bảo đảm latency thấp hơn trong mọi tác vụ. Với ứng dụng chủ yếu dùng JDBC và các thư viện blocking, MVC có thể đơn giản hơn và hoạt động rất tốt.

### 4.1. Spring WebFlux không chỉ chạy trên Netty

Spring WebFlux có thể chạy trên Reactor Netty hoặc qua adapters cho Servlet non-blocking servers như Tomcat/Jetty. Khi đó thread names/chi tiết scheduling khác nhau. Bài dùng Reactor Netty để giải thích event loop cụ thể, **không suy diễn** toàn bộ WebFlux deployments có cùng số threads với Netty.

### 4.2. Boot chọn MVC hay WebFlux khi có hai starters?

Spring Boot thường ưu tiên **Spring MVC application type** khi cả `spring-boot-starter-web` và `spring-boot-starter-webflux` cùng hiện diện, trừ khi application type được cấu hình chủ đích. Để chạy đúng demo WebFlux/Netty dưới đây, chỉ thêm `spring-boot-starter-webflux`, **không thêm `spring-boot-starter-web`**. Một ứng dụng MVC vẫn có thể dùng reactive `WebClient` mà không cần chuyển server sang WebFlux.

## 5. Reactive Streams, `Mono`, `Flux` và subscription

Reactor cung cấp:
- `Mono<T>`: publisher phát **0 hoặc 1** item, hoặc kết thúc bằng error.
- `Flux<T>`: publisher phát **0 đến N** items, hoặc kết thúc bằng error.

Cả hai biểu diễn một **pipeline**: chuỗi operators, signals và side effects được thực hiện khi subscription và dữ liệu tương ứng xuất hiện.

~~~java
Mono<String> pipeline = Mono.just("spring")
        .map(String::toUpperCase);
~~~

Ở đây `"spring"` được tạo sẵn; toán tử `map` thường chạy khi có subscription. Chỉ tạo biến `pipeline` **không có nghĩa HTTP response đã gửi hoặc network request đã chạy**.

### 5.1. Signals

Luồng reactive có các signals như:
- `onSubscribe`: thiết lập subscription.
- `onNext`: item được phát.
- `onComplete`: hoàn thành bình thường.
- `onError`: kết thúc bằng lỗi.
- `request(n)`: subscriber yêu cầu số lượng items, là cơ chế backpressure.
- `cancel()`: subscriber hủy subscription.

Backpressure giúp mô tả nhu cầu tiêu thụ, **không bảo đảm mọi producer bên ngoài có thể giảm tốc vô hạn**. Nếu upstream không thể chậm lại, hệ thống cần buffering giới hạn, dropping, rejection hoặc control ở ingress. Reactive type không biến memory thành vô hạn.

### 5.2. Tránh gọi `subscribe()` trong controller chỉ để "khởi động"

Ví dụ sai:

~~~java
@GetMapping("/bad-start")
public Mono<String> badStart() {
    remoteCall().subscribe();       // side effect tách khỏi HTTP lifecycle
    return Mono.just("accepted");
}
~~~

`remoteCall()` chỉ là fragment chưa định nghĩa. Khi tự subscribe, bạn tạo một tác vụ độc lập khó liên kết với request cancellation, errors, retries, trace và HTTP response. Nếu mục tiêu là trả kết quả trong cùng request, hãy **return publisher** để WebFlux quản lý subscription:

~~~java
@GetMapping("/good")
public Mono<String> good() {
    return remoteCall();
}
~~~

Nếu mục tiêu thực sự là background job, thiết kế job queue và `202 Accepted` với job ID/status endpoint; không chỉ `subscribe()` rồi hy vọng background operation sẽ bền vững.

## 6. Vì sao blocking call làm hại cả hệ thống?

### 6.1. Event-loop starvation

Giả sử mỗi event-loop worker có nhiều connections đang chờ callback. Một handler chạy:

~~~java
Thread.sleep(1_000);
~~~

Trên event loop, thread đó không xử lý được read/write events hoặc callbacks khác trong khoảng sleep. Nếu nhiều requests cùng mắc lỗi này, event loops lần lượt bị chiếm. Trong logs, CPU usage có thể không cao nhưng latency p99 và timeouts tăng; đây là một dấu hiệu của **blocking wait**, không phải thiếu CPU.

Một loạt sự cố có thể xảy ra:
1. HTTP server nhận request.
2. Handler chặn event loop.
3. Các sockets khác không được phục vụ kịp.
4. Client/gateway timeout, retry tự động gửi thêm request.
5. Hàng đợi và số connections tăng.
6. Retry tiếp tục làm tình hình xấu đi dù downstream đã phục hồi.

Điều này là **mẫu failure**, không phải định luật mọi thread sleep đều gây outage; phụ thuộc concurrency, số loop, thời gian blocking, retry policy và tải.

### 6.2. Blocking API phổ biến

| API/operation | Có thể chặn? | Cách ứng xử |
| --- | --- | --- |
| `Thread.sleep` | Có | Không dùng trong request event loop |
| `Mono.block()`, `Flux.blockLast()` | Có | Không dùng trong reactive chain WebFlux |
| `Future.get()`, `CompletableFuture.join()` | Có nếu chưa hoàn thành | Compose futures/publishers không chờ thread |
| `RestTemplate`, HTTP clients sync | Có | Dùng `WebClient` non-blocking hoặc isolate boundedElastic |
| JDBC `DataSource`/`JdbcTemplate`/JPA | Có | Dùng R2DBC hoặc offload bounded pool |
| File I/O đồng bộ | Có thể | Streaming/async file API khi phù hợp hoặc offload |
| `InputStream.read()` | Có thể | Không đọc blocking stream lâu trên event loop |
| `synchronized`/lock contention | Có thể | Giảm lock/critical sections, tránh khóa dài |
| BCrypt/crypto/hash/compression lớn | CPU-heavy | Giữ callback ngắn, phân bổ CPU rõ; không lạm dụng event loop |
| JSON serialization rất lớn | CPU/memory nặng | Giới hạn payload, streaming/codec strategy |
| `System.out.println` hoặc logging synchronous quá nhiều | Có thể | Structured async logging, metrics và sampling |
| `WebClient.retrieve().bodyToMono(...)` | Non-blocking nếu connector phù hợp | Return/compose publisher, không thêm `block()` |
| R2DBC driver reactive | Thiết kế non-blocking | Chọn stack/driver đúng, kiểm actual behavior |

**Phân biệt I/O-blocking và CPU-bound:** Chuyển phép tính CPU nặng sang `boundedElastic` có thể giảm ảnh hưởng trực tiếp lên event loop nhưng không làm CPU work rẻ hơn; có thể làm quá tải scheduler và tăng context switches. Với CPU workload, dùng `Schedulers.parallel()` hoặc compute worker pool được đo/giới hạn, tùy độ dài/nhiệm vụ. Reactor `parallel()` không phải phép màu tăng thông lượng cho tác vụ vượt số CPU cores.

### 6.3. `.block()` có thể fail-fast hoặc âm thầm làm chậm

Project Reactor biết một số threads được đánh dấu **NonBlocking**. `block()`/`blockFirst()`/`blockLast()` trên các threads này có thể ném `IllegalStateException`, ví dụ thông điệp:

~~~text
block()/blockFirst()/blockLast() are blocking, which is not supported in thread ...
~~~

Không dựa vào thông điệp chính xác theo version. Nếu chạy trên một thread không được đánh dấu NonBlocking, `block()` **có thể không ném** nhưng vẫn chiếm thread và gây chậm. Vì vậy “không có IllegalStateException” **không chứng minh pipeline non-blocking**.

Trong Spring WebFlux handler, đừng chữa lỗi bằng cách đổi exception handler cho `IllegalStateException` và tiếp tục `block()`. Phải sửa nguyên nhân: compose publisher đúng hoặc isolate legacy blocking call trên worker phù hợp.

## 7. Phản ví dụ quan trọng: `Mono.just(blockingCall())` vẫn blocking

Giả sử `legacyService.find(id)` là JDBC/HTTP call đồng bộ.

**Sai:**

~~~java
@GetMapping("/bad/{id}")
public Mono<Product> bad(@PathVariable long id) {
    return Mono.just(legacyService.find(id));
}
~~~

`legacyService.find(id)` được gọi **trước** khi `Mono.just(...)` chạy, có thể ngay trên event-loop thread đang invoke controller. Mặc dù return type là Mono, block đã xảy ra.

**Cũng sai nếu chỉ thêm subscribeOn phía sau:**

~~~java
Mono.just(legacyService.find(id))
    .subscribeOn(Schedulers.boundedElastic());
~~~

`legacyService.find(id)` vẫn được thực thi eager ở thời điểm biểu thức đánh giá; subscribeOn không thể quay ngược thời gian để sửa lời gọi đã chạy.

**Đúng khi bắt buộc dùng blocking library:**

~~~java
Mono<Product> product = Mono.fromCallable(
        () -> legacyService.find(id))
    .subscribeOn(Schedulers.boundedElastic());
~~~

`Mono.fromCallable` hoãn việc thực hiện lambda đến subscription; `subscribeOn` bố trí việc subscribe/source trên bounded elastic worker. Vậy blocking call chiếm **worker phù hợp**, không chiếm event loop.

Ví dụ trên là fragment với `Product` và `legacyService` chưa được định nghĩa; app hoàn chỉnh ở mục 12. Việc offload không loại bỏ số threads cần cho blocking work; nó chỉ **cô lập** phần blocking khỏi I/O loops để server không bị "nghẹt thở" sớm.

### 7.1. `Mono.defer` và `Mono.fromCallable`

`Mono.defer(() -> legacyWrapper())` trì hoãn **việc dựng publisher** theo từng subscription. `Mono.fromCallable(() -> legacyService.find(id))` phù hợp với **một phép tính đồng bộ trả một kết quả**, có thể ném checked exception và trở thành onError.

Chỉ dùng `defer` mà không có scheduling vẫn có thể thực hiện blocking code trên thread subscription (có thể là event loop). Để isolate, vẫn cần `subscribeOn(Schedulers.boundedElastic())` hoặc scheduler tương đương.

### 7.2. Nếu Callable trả `null` thì sao?

`Mono.fromCallable` có thể complete **empty** khi callable trả null. Không nhầm điều đó với JSON null hay HTTP 404 tự động. Nếu không tìm thấy resource, nên ném typed exception/domain error hoặc dùng `switchIfEmpty(Mono.error(...))` với fallback rõ. Ứng dụng demo ném `ProductNotFoundException` để controller/advice biến thành 404.

## 8. Scheduler: không phải mọi operator tạo thread

Reactor `Scheduler` quản lý nơi thực thi việc đã được schedule. Nhiều operators như `map`, `filter`, `flatMap`, `doOnNext` **không mặc nhiên mở thread mới**. Chúng thường chạy trên thread phát signal tại thời điểm đó, trừ khi có asynchronous boundary/scheduler switch.

### 8.1. Những scheduler thường gặp

| Scheduler | Thiết kế chính | Điều cần tránh |
| --- | --- | --- |
| `Schedulers.immediate()` | Thực thi trên thread hiện tại | Không dùng để offload blocking |
| `Schedulers.single()` | Một worker dùng chung | Một task chặn làm trì hoãn các task khác |
| `Schedulers.parallel()` | Thread pool cố định cho tác vụ CPU-bound ngắn | Không gửi JDBC/sleep vào pool này |
| `Schedulers.boundedElastic()` | Worker capacity giới hạn cho blocking I/O legacy | Không coi là pool vô hạn, vẫn có queue/rejection |
| `Schedulers.newBoundedElastic(...)` | Tạo bounded pool riêng, dễ isolate dependency | Phải đặt giới hạn hợp lý và dispose khi ngừng |
| Event-loop threads của Reactor Netty | Network I/O/callbacks | Không chạy blocking code hoặc phép tính kéo dài |

Default boundedElastic trong Reactor 3.6.x có giới hạn thread/queue, nhưng **đừng copy một con số default từ một version khác làm contract**; có thể tùy chỉnh system properties và môi trường. Một số Reactor releases/JDKs hỗ trợ chiến lược virtual-thread cho bounded elastic khi bật cấu hình tương ứng. Java 17 trong demo này sử dụng platform threads truyền thống; **không tự giả định virtual threads đang bật**.

### 8.2. `subscribeOn`: điều phối subscription/source

~~~java
Mono<String> p = Mono.fromCallable(() -> {
            // Blocking operation cần worker riêng.
            return legacyRead();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(String::toUpperCase);
~~~

`legacyRead()` là hàm giả định chưa định nghĩa. `subscribeOn` tác động tới quá trình subscription/source upstream. Thường đặt **ngay sau `fromCallable`** để ý đồ rõ ràng. Điều đó không có nghĩa nó tự subscribe; publisher chỉ thực thi khi client/WebFlux subscribe.

Một pipeline có thể chứa nhiều `subscribeOn` nhưng việc thêm liên tiếp nhiều subscribeOn không phải cách "chuyển thread qua lại" dễ hiểu: một `subscribeOn` gần source thường quyết định scheduling của source, tùy structure/operators. Tránh sử dụng chồng chéo khi không cần thiết.

### 8.3. `publishOn`: thay thread cho các bước phía sau

~~~java
Mono<String> p = Mono.just("a")
        .publishOn(Schedulers.parallel())
        .map(value -> value.toUpperCase());
~~~

`publishOn` dùng scheduler cho phần downstream sau operator cho tới khi có scheduler boundary khác. Nó thích hợp để chuyển một phần pipeline sang compute worker.

**Cẩn thận với blocking source:**

~~~java
Mono.fromCallable(() -> legacyRead())
    .publishOn(Schedulers.boundedElastic())
    .map(String::toUpperCase);
~~~

Nếu không có upstream scheduling khác, `fromCallable` có thể **vẫn thực thi trên subscription thread** trước khi publishOn chuyển tín hiệu downstream. Do đó mẫu offload blocking source chuẩn là **`Mono.fromCallable(...).subscribeOn(boundedElastic)`**.

### 8.4. Thread switch là chi phí, không phải thành tựu

Mỗi scheduler boundary có chi phí queueing, context switches, propagation context và latency. Không thêm `publishOn(Schedulers.parallel())` ở mọi toán tử "cho an toàn". Hãy xác định bước nào thực sự blocking/CPU-heavy, sau đó bố trí một boundary có chủ đích, đo hiệu năng và giám sát queue.

## 9. Vì sao `boundedElastic` có giới hạn, và tại sao điều đó tốt?

Một bounded scheduler giúp cô lập những calls không thể chuyển sang reactive ngay, nhưng những calls ấy vẫn sử dụng workers:
- Nếu dependency thường phản hồi sau 50 ms, một số workers có thể phục vụ nhiều requests theo thời gian.
- Nếu dependency treo 30 giây, workers ở trạng thái block lâu, request tiếp theo có thể phải đợi queue.
- Khi queue/pool chạm ngưỡng, schedule có thể bị từ chối (ví dụ Reactor `RejectedExecutionException`), không phải tự tạo vô hạn threads.

**Giới hạn là cơ chế bảo vệ tài nguyên**, không phải lỗi thiết kế. Khi overload, có thể trả status phù hợp (thường 503 với application policy), áp dụng admission control, circuit breaker, rate limit và retry có backoff/jitter giới hạn.

### 9.1. Dedicated bounded scheduler

Thay vì mọi thư viện legacy chia sẻ global boundedElastic, ứng dụng có thể tạo scheduler riêng:

~~~java
@Bean(name = "legacyScheduler", destroyMethod = "dispose")
Scheduler legacyScheduler() {
    return Schedulers.newBoundedElastic(4, 32, "legacy-io");
}
~~~

Đây là fragment thuộc class `@Configuration`, có imports ở ví dụ runnable bên dưới. Ý nghĩa:
- Tối đa 4 workers (theo cài đặt helper này).
- Queue bound là 32 **mỗi backing thread** theo Reactor semantics của factory; đừng coi đây chắc chắn là 32 requests tổng toàn app.
- Prefix thread name `legacy-io` hỗ trợ diagnostics; tên cụ thể không phải security hoặc business contract.
- Khi Spring context shutdown, gọi `dispose` để dọn scheduler resources.

Số `4` và `32` **chỉ dành cho demo**, không phải recommendation production. Trong thực tế cần dựa trên connection pool, service latency, concurrency, CPU, memory, expected load và timeout của upstream.

### 9.2. Không vô tình vượt connection pool

Nếu scheduler cho phép 100 blocking operations nhưng JDBC connection pool chỉ có 10 connections, nhiều workers sẽ **ngồi chờ connection** và lấp queue. Điều chỉnh thread/connection/request admission cùng nhau. Với R2DBC, giới hạn connection pool và số in-flight operations vẫn quan trọng — không có nghĩa non-blocking là unlimited concurrency.

## 10. WebClient: tận dụng non-blocking HTTP thay vì offload mọi call

Nếu downstream hỗ trợ HTTP, `WebClient` có thể xây reactive request mà không cần worker block:

~~~java
public Mono<Product> fetch(long id) {
    return webClient.get()
            .uri("/products/{id}", id)
            .retrieve()
            .bodyToMono(Product.class)
            .timeout(Duration.ofSeconds(2));
}
~~~

Đây là fragment của một class có `WebClient webClient` và record/class `Product` được cấu hình sẵn; nó **không phải project hoàn chỉnh** vì không cung cấp downstream server. Với Reactor Netty connector, HTTP I/O được xử lý non-blocking, và publisher được WebFlux subscribe khi controller return.

**Không làm thế này:**

~~~java
Product product = webClient.get()
        .uri("/products/{id}", id)
        .retrieve()
        .bodyToMono(Product.class)
        .block();
~~~

`block()` biến lời gọi thành đồng bộ, có thể throw trên non-blocking thread hoặc gây starvation. Khi client là WebFlux, hãy return `Mono<Product>` hoặc compose bằng `flatMap`/`zipWhen`.

### 10.1. Chạy nhiều request HTTP I/O độc lập

Nếu cần hai remote responses:

~~~java
Mono<Product> a = productClient.fetch(1);
Mono<Product> b = productClient.fetch(2);

return Mono.zip(a, b)
        .map(tuple -> new ProductPair(tuple.getT1(), tuple.getT2()));
~~~

Đây là fragment, `ProductPair` và `productClient` cần khai báo riêng. Hai publishers có thể có I/O in flight đồng thời; không cần gọi `block()` để chờ từng item theo thứ tự. Tuy nhiên `zip` hoàn thành empty nếu một nguồn empty theo Reactor semantics; muốn xử lý trường hợp không tìm thấy cần `switchIfEmpty`/domain exception cho từng nguồn trước khi zip.

### 10.2. Timeout không tự dừng mọi tác vụ legacy

`timeout(Duration.ofSeconds(2))` phát signal lỗi và cancel upstream khi hết thời gian. Với WebClient non-blocking, cancellation có thể huỷ request/giải phóng resources theo connector. Với `fromCallable` đang chạy code blocking không hỗ trợ interruption, timeout **không bảo đảm** tác vụ/SQL/network call đó dừng ngay. Vì vậy timeout ở từng driver/HTTP client/database vẫn cần thiết.

### 10.3. Reactive data access: R2DBC khác JDBC

R2DBC thiết kế cho reactive database access; JDBC/JPA là các API đồng bộ trên caller thread trong trường hợp truyền thống. Nếu toàn hệ thống chủ yếu là JDBC, có thể:
- Giữ Spring MVC đồng bộ vì đó là một kiến trúc phù hợp.
- Với WebFlux bắt buộc, offload blocking JDBC qua bounded worker và đặt concurrency/connection limits.
- Nếu có yêu cầu và driver hỗ trợ, cân nhắc chuyển riêng data access sang R2DBC theo kế hoạch migration.

Đừng gắn `@Transactional` kiểu imperative/JPA rồi giả định transaction context tự di chuyển qua mọi Scheduler: imperative transaction thường thread-bound; reactive transaction dùng `Reactor Context` và `ReactiveTransactionManager` theo stack phù hợp.

## 11. Phân biệt `Thread.sleep`, blocking I/O và CPU-heavy work

### 11.1. Blocking wait

~~~java
Thread.sleep(2_000);
~~~

Tiêu tốn rất ít CPU nhưng giữ thread. Đặt trên event loop là đặc biệt xấu: loop không quay lại phục vụ sockets/callbacks. Đặt trên bounded worker vẫn sử dụng một worker, gây queue khi nhiều request cùng chờ.

Nếu mục đích chỉ là **đợi một khoảng thời gian** trong pipeline, dùng non-blocking timer:

~~~java
Mono<String> delayed = Mono.delay(Duration.ofSeconds(2))
        .map(ignored -> "done");
~~~

`Mono.delay` không dùng `Thread.sleep` trên event loop; nó dùng scheduler/timer tương ứng. Nhưng đây chỉ là mô phỏng delay, **không thay** một mạng/DB request có side effects.

### 11.2. CPU-bound work

~~~java
long value = expensiveHash(payload);
~~~

Đây là fragment với hai biến chưa khai báo. Dù không gọi bất kỳ I/O nào, nếu `expensiveHash` mất 500 ms trên event loop, nó vẫn khiến loop bận và chậm xử lý sockets. Có thể tách sang compute pool hoặc đưa sang job queue nếu việc nặng; bảo đảm không tạo hàng trăm operations lớn tranh CPU. Dùng scheduler đúng workload: `parallel`/dedicated bounded compute worker thường khác với `boundedElastic` vốn hướng tới blocking I/O.

### 11.3. Synchronous locks

Một callback lấy monitor/lock bị các threads khác giữ lâu cũng là blocking. Một synchronized block rất ngắn không mặc định là lỗi, nhưng lock cạnh tranh dưới tải cao và các call chặn trong lock có thể tạo tail latency nghiêm trọng. Ưu tiên immutable data, concurrent structures và giảm critical section; không chuyển mọi lock sang Reactor context mà không có thiết kế thread-safety.

### 11.4. Logging cũng có thể gây chặn

Ghi debug log quá dày, formatter nặng, synchronous file/network appenders hoặc ghi stdout trong vòng lặp lớn có thể làm chậm event loop. Với production dùng structured logs, bounded asynchronous appenders phù hợp, sampling, redaction và metrics. Tránh log raw Authorization/cookie/session IDs hoặc dữ liệu riêng tư chỉ để đo thread.

## 12. Ứng dụng hoàn chỉnh: Java 17 + Spring Boot 3.3.5 + scheduler cô lập

Ví dụ có:
- `GET /api/health`: trả `Mono` nhanh, không blocking.
- `GET /api/products/{id}`: giả lập thư viện legacy blocking bằng `Thread.sleep(30)` trong service. Controller dùng `Mono.fromCallable(...).subscribeOn(legacyScheduler)` để không chặn Reactor Netty loop.
- Nếu không có product: typed exception được `@RestControllerAdvice` chuyển thành 404.
- Nếu timeout: `TimeoutException` được advice chuyển thành 503.
- Mock HTTP integration dùng `WebTestClient` với embedded Reactor Netty, kiểm cả success/404.
- Test scheduler xác minh công việc chạy trên worker có prefix khác event loop.

`Thread.sleep(30)` **chỉ để mô phỏng blocking code trong demo**; đừng đưa sleep vào production và cũng đừng cho rằng offload giải quyết mọi vấn đề tải.

~~~text
webflux-eventloop-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/loopdemo/
    │   ├── LoopDemoApplication.java
    │   ├── config/SchedulerConfig.java
    │   ├── domain/Product.java
    │   ├── domain/ProductNotFoundException.java
    │   ├── domain/LegacyProductGateway.java
    │   └── web/
    │       ├── ProductController.java
    │       └── ApiErrorHandler.java
    └── test/java/com/example/loopdemo/
        └── ProductControllerIntegrationTest.java
~~~

### 12.1. Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'webflux-eventloop-demo'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

**Không thêm `spring-boot-starter-web`**. `starter-webflux` có Reactor/Spring WebFlux/Reactor Netty, `starter-test` có JUnit và Spring Boot testing, `reactor-test` cung cấp StepVerifier. Boot BOM quản lý phiên bản Reactor thống nhất. Khi build thực tế nên dùng Gradle wrapper tương thích Boot 3.3/Java 17.

### 12.2. Main application

`src/main/java/com/example/loopdemo/LoopDemoApplication.java`:

~~~java
package com.example.loopdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoopDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoopDemoApplication.class, args);
    }
}
~~~

### 12.3. Bounded scheduler riêng cho legacy blocking

`src/main/java/com/example/loopdemo/config/SchedulerConfig.java`:

~~~java
package com.example.loopdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class SchedulerConfig {

    @Bean(name = "legacyScheduler", destroyMethod = "dispose")
    public Scheduler legacyScheduler() {
        return Schedulers.newBoundedElastic(4, 32, "legacy-io");
    }
}
~~~

Spring quản lý scheduler bean và gọi `dispose` khi shutdown. Đặt scheduler riêng làm rõ boundary và dễ đo cô lập thư viện legacy. Số workers và queue chỉ mang tính minh họa; ứng dụng có thể dùng `Schedulers.boundedElastic()` chia sẻ nếu không cần isolation.

### 12.4. Domain types

`src/main/java/com/example/loopdemo/domain/Product.java`:

~~~java
package com.example.loopdemo.domain;

public record Product(long id, String title) {}
~~~

`src/main/java/com/example/loopdemo/domain/ProductNotFoundException.java`:

~~~java
package com.example.loopdemo.domain;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long id) {
        super("Product not found: " + id);
    }
}
~~~

### 12.5. Giả lập thư viện legacy blocking

`src/main/java/com/example/loopdemo/domain/LegacyProductGateway.java`:

~~~java
package com.example.loopdemo.domain;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LegacyProductGateway {

    private final Map<Long, Product> products = Map.of(
            1L, new Product(1L, "Project Reactor"),
            2L, new Product(2L, "Spring WebFlux"));

    public Product find(long id) throws InterruptedException {
        // Demo: thay cho một JDBC/HTTP client synchronous call.
        Thread.sleep(30L);

        Product product = products.get(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }
}
~~~

`find` là **blocking** do sleep. Nếu gọi thẳng từ Netty event loop sẽ giữ loop ít nhất khoảng 30 ms cộng phần lookup. Đưa nó vào dedicated scheduler chỉ giúp **không chiếm Netty loop**, vẫn tốn một worker. `Map.of` dùng fixture; không chứa dữ liệu người dùng, không phụ thuộc external network và kết quả test deterministic về body.

### 12.6. WebFlux controller đúng cơ chế

`src/main/java/com/example/loopdemo/web/ProductController.java`:

~~~java
package com.example.loopdemo.web;

import com.example.loopdemo.domain.LegacyProductGateway;
import com.example.loopdemo.domain.Product;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final LegacyProductGateway legacy;
    private final Scheduler scheduler;

    public ProductController(
            LegacyProductGateway legacy,
            @Qualifier("legacyScheduler") Scheduler scheduler) {
        this.legacy = legacy;
        this.scheduler = scheduler;
    }

    @GetMapping("/health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP"));
    }

    @GetMapping("/products/{id}")
    public Mono<Product> product(@PathVariable("id") long id) {
        return Mono.fromCallable(() -> legacy.find(id))
                .subscribeOn(scheduler)
                .timeout(Duration.ofSeconds(2));
    }
}
~~~

**Giải thích theo thứ tự:**
1. Khi GET tới `/api/products/{id}`, controller trả **một Mono publisher** nhanh chóng, chưa gọi `legacy.find` tại chỗ.
2. `Mono.fromCallable` giữ công việc legacy ở trạng thái lazy tới subscription.
3. `subscribeOn(scheduler)` chọn bounded worker cho subscription/source; `Thread.sleep` sẽ chạy trên worker đó, không phải request event loop.
4. `timeout(2s)` giới hạn thời gian chờ của publisher; một tác vụ blocking không support interruption vẫn có thể tiếp tục sau khi timeout.
5. Khi worker trả `Product`, WebFlux serialize JSON như response HTTP.
6. Nếu `legacy.find` ném `ProductNotFoundException`, lỗi đi vào reactive `onError` signal; WebFlux error handling chọn advice bên dưới.

Không cần viết `subscribe()` thủ công trong controller. Không có blocking trong `health()`, nên route đó không cần dedicated scheduler. Khi có CPU-heavy JSON serialization hoặc complex response transforms, cần đánh giá riêng thay vì đưa cả controller vào boundedElastic mặc định.

### 12.7. API error handling

`src/main/java/com/example/loopdemo/web/ApiErrorHandler.java`:

~~~java
package com.example.loopdemo.web;

import com.example.loopdemo.domain.ProductNotFoundException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> missing(
            ProductNotFoundException exception) {

        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Product not found");
        problem.setDetail("No product has that id.");
        problem.setProperty("code", "PRODUCT_NOT_FOUND");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ProblemDetail> timeout(
            TimeoutException exception) {

        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Product service timed out");
        problem.setDetail("The product could not be fetched in time.");
        problem.setProperty("code", "PRODUCT_TIMEOUT");

        return ResponseEntity.status(
                HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
~~~

`ProblemDetail` có semantics RFC 9457; Spring WebFlux + Jackson có thể serialize extension field `code`. Advice không trả raw exception messages; đó là lựa chọn an toàn vì thực tế exception có thể chứa SQL, URLs nội bộ hoặc secrets.

**Giới hạn:** Demo chỉ mapping hai error types; executor rejection, malformed path ID, client disconnect, unexpected runtime error và HTTP gateway timeout cần được xử lý/test theo chính sách production. Không gom mọi `Throwable` thành 200/503 và che bug nội bộ. HTTP 504 chỉ phù hợp khi thành phần đóng vai gateway/proxy theo semantics; route application demo cố ý chọn 503 khi dependency timeout.

## 13. WebTestClient và StepVerifier: kiểm đúng WebFlux stack

Trong một ứng dụng WebFlux, `WebTestClient` là cách test HTTP reactive tự nhiên. Demo dùng embedded server với port ngẫu nhiên để kiểm thêm network/Netty; nó không phải MockMvc dành cho Servlet MVC.

`src/test/java/com/example/loopdemo/ProductControllerIntegrationTest.java`:

~~~java
package com.example.loopdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ProductControllerIntegrationTest {

    @Autowired
    WebTestClient client;

    @Autowired
    @Qualifier("legacyScheduler")
    Scheduler scheduler;

    @Test
    void healthIsReactiveAndAvailable() {
        client.get().uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void legacyProductStillReturnsJson() {
        client.get().uri("/api/products/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.title").isEqualTo("Project Reactor");
    }

    @Test
    void missingProductBecomes404ProblemDetail() {
        client.get().uri("/api/products/999")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND")
                .jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void dedicatedSchedulerActuallyOffloadsItsSource() {
        Mono<String> workThread =
                Mono.fromCallable(() ->
                        Thread.currentThread().getName())
                    .subscribeOn(scheduler);

        StepVerifier.create(workThread)
                .expectNextMatches(name -> name.startsWith("legacy-io-"))
                .verifyComplete();
    }
}
~~~

**Phân biệt điều test chứng minh:**
- Ba `WebTestClient` tests gửi HTTP đến application và kiểm status/body.
- Test StepVerifier chứng minh **nguồn Mono khi subscribeOn dedicated scheduler** chạy trên worker có prefix do ta đặt. Nó không đo request concurrency dưới tải, nhưng xác minh scheduler hoạt động.
- Demo cố ý không assert tên Reactor Netty event-loop threads vì naming có thể đổi theo runtime.
- Test không dựa vào timing 30 ms chính xác vì `Thread.sleep`/CI scheduler có thể lệch; response test chờ body thay vì tự ước lượng thời gian cố định.
- Không khẳng định chỉ bốn tests có thể chứng minh system chịu 10.000 concurrent requests. Cần load test và scheduler/Netty metrics khi production.

Chạy trong project có Gradle wrapper và Java 17:

~~~bash
./gradlew test
./gradlew bootRun
~~~

Windows PowerShell:

~~~powershell
.\gradlew.bat test
.\gradlew.bat bootRun
~~~

Không chạy `bootRun` cùng terminal sau khi `test` chưa hoàn tất. Trong server đang chạy, gọi:

~~~bash
curl -i 'http://localhost:8080/api/health'
curl -i 'http://localhost:8080/api/products/1'
curl -i 'http://localhost:8080/api/products/999'
~~~

Kết quả chính:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"status":"UP"}
~~~

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"title":"Project Reactor"}
~~~

~~~http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{"type":"about:blank","title":"Product not found","status":404,"detail":"No product has that id.","code":"PRODUCT_NOT_FOUND"}
~~~

Headers bổ sung, thứ tự header, JSON field order và `instance` có thể khác theo serializer/framework, nhưng status/essential fields phải khớp. Cả ba calls là những HTTP requests độc lập; không phải một request tự động chuyển threads theo số lượng headers.

## 14. Anti-pattern đối chiếu trực tiếp với ví dụ đúng

### 14.1. Sai: tạo Mono sau khi blocking đã chạy

~~~java
return Mono.just(legacy.find(id));
~~~

Lời gọi blocking đã chạy trước khi Mono được dựng. Sửa:

~~~java
return Mono.fromCallable(() -> legacy.find(id))
        .subscribeOn(legacyScheduler);
~~~

### 14.2. Sai: gọi `block()` trên WebClient trong handler

~~~java
return Mono.just(
        webClient.get().uri("/x").retrieve()
                .bodyToMono(String.class).block());
~~~

Nếu API có thể hoàn toàn non-blocking:

~~~java
return webClient.get().uri("/x")
        .retrieve().bodyToMono(String.class);
~~~

### 14.3. Sai: dùng `publishOn` sai vị trí

~~~java
return Mono.fromCallable(() -> legacy.find(id))
        .publishOn(legacyScheduler);
~~~

Source vẫn có thể chạy ngay trên subscriber/event-loop thread. Sửa bằng `subscribeOn` ngay sau source, như code runnable.

### 14.4. Sai: `flatMap` có lambda đồng bộ chậm

~~~java
return ids.flatMap(id -> {
    Product p = legacy.find(id);  // chạy đồng bộ trong lambda
    return Mono.just(p);
});
~~~

`ids` ở đây là Flux/Publisher fragment. Dù tên operator là flatMap, lambda đã block ngay khi upstream phát. Cần:

~~~java
return ids.flatMap(
        id -> Mono.fromCallable(() -> legacy.find(id))
                .subscribeOn(legacyScheduler),
        8);
~~~

`8` giới hạn concurrency ở tầng flatMap trong ví dụ. Khi cần giữ thứ tự, xét `flatMapSequential` hoặc `concatMap` với trade-offs. **Không để số in-flight requests vượt năng lực database/legacy client**; limit concurrency và scheduler queue phải tương thích.

### 14.5. Sai: dùng `doOnNext` cho blocking side-effect

~~~java
return products.doOnNext(product ->
        legacyAuditRepository.save(product));
~~~

`doOnNext` callback chạy đồng bộ trên thread phát signal, không offload. Nếu audit cần tham gia kết quả:

~~~java
return products.concatMap(product ->
        Mono.fromCallable(() -> {
            legacyAuditRepository.save(product);
            return product;
        }).subscribeOn(legacyScheduler));
~~~

`products`, `legacyAuditRepository` là fragments chưa khai báo. Nếu audit là event bền vững/tách khỏi HTTP response, dùng outbox/queue và thiết kế reliability riêng. Không chỉ `subscribe()` ở `doOnNext` vì sẽ mất retry/error ownership.

## 15. Backpressure và overload không giống scheduling

### 15.1. `request(n)` không giới hạn mọi nguồn bên ngoài

Reactive Streams cho phép Subscriber yêu cầu số item tối đa một thời điểm. Với cold Flux phát từ một nguồn do framework điều khiển, upstream có thể tôn trọng demand.

Nhưng HTTP client connections, file watchers, Kafka brokers, GUI/events hay một external service không phải lúc nào dừng ngay được khi downstream giảm demand. Nếu event rate vượt tốc độ xử lý, cần bounded buffers, concurrency limits, backpressure strategy phù hợp, dropping/error policy hoặc admission control. Không dùng buffer vô hạn trong pipeline để "giữ đủ dữ liệu".

### 15.2. `flatMap` có concurrency và prefetch

Một pipeline:

~~~java
Flux.fromIterable(ids)
    .flatMap(id -> productClient.fetch(id), 8);
~~~

giới hạn số inner publishers được subscribed đồng thời theo tham số concurrency. `ids` và `productClient` là fragments. Điều này khác với `Schedulers.boundedElastic`: flatMap giới hạn **lượng công việc đang in flight**, scheduler giới hạn **tài nguyên thực thi**. Khi dùng legacy DB, cả hai có thể cần.

### 15.3. Reactive không loại bỏ connection pools

Non-blocking WebClient vẫn cần connection pool, DNS/TLS resources, limits, read/write timeouts và memory budgets. R2DBC vẫn có pool/concurrent transactions. Nếu downstream chỉ cho phép 20 concurrent calls, application phải kiểm giới hạn đó ngay cả khi event loop có thể quản lý hàng nghìn sockets.

### 15.4. Retry gây snowball nếu không giới hạn

Không viết `retry()` vô hạn cho endpoint đã chậm vì saturation. Retry with exponential backoff/jitter, deadline, idempotency và circuit breakers chỉ nên áp dụng cho transient failures có định nghĩa rõ. Không retry một HTTP POST có side effects mù quáng. Một thread model tốt không cứu được retry storm do architecture sai.

## 16. Timeout và cancellation trong reactive pipeline

### 16.1. `timeout` là deadline cho publisher, không phải global cleanup

~~~java
return legacyPublisher
        .timeout(Duration.ofSeconds(2));
~~~

Khi quá thời gian, `timeout` có thể phát `TimeoutException` và cancel source. Nhưng nếu source đang chặn trong native/JDBC driver không phản hồi interruption, thread vẫn có thể bị giữ. Cần driver-side timeout, database statement timeout, HTTP response timeout và cleanup ở từng tầng.

### 16.2. Hủy request không đồng nghĩa undo transaction

Client đóng tab hoặc hủy subscription có thể khiến HTTP exchange không cần response nữa. Nhưng:
- Một business side effect ở DB đã commit không tự rollback vì subscriber cancel.
- Blocking call đang chạy có thể tiếp tục đến khi driver trả hoặc hết timeout.
- Retry/cancel có thể tạo trạng thái "không biết operation đã thành công chưa".
- Với commands/transactions, cần idempotency keys, transaction boundaries và audit chứ không chỉ rely vào Reactor cancellation.

### 16.3. Error handling operator

~~~java
return productClient.fetch(id)
        .timeout(Duration.ofSeconds(2))
        .onErrorResume(TimeoutException.class,
                ex -> Mono.error(
                        new UpstreamUnavailableException()));
~~~

Fragment có `UpstreamUnavailableException` tự định nghĩa. `onErrorResume` quyết định fallback/error ở reactive chain, nhưng response HTTP 503/504/500 phụ thuộc global WebFlux exception handler, `@RestControllerAdvice` hoặc custom error mapping. Đừng bắt mọi lỗi và trả một default "empty product" vì có thể che dữ liệu sai và lỗi bảo mật.

## 17. WebFlux threading, SecurityContext và Reactor Context

### 17.1. ThreadLocal không an toàn làm định danh logical request

Ở Servlet MVC đồng bộ, nhiều code quen dùng `ThreadLocal` hoặc MDC để giữ user/trace trên thread request. Trong reactive pipeline, callbacks có thể chạy trên event loop, boundedElastic, parallel scheduler hoặc trên thread của asynchronous source. Một logical request **không gắn vĩnh viễn với một Java thread**.

Ví dụ anti-pattern:

~~~java
String user = currentUserThreadLocal.get();
return asyncCall.map(result -> useForUser(user, result));
~~~

Biến được đọc lúc dựng pipeline có thể đúng/sai tùy security integration và timing; nếu ThreadLocal đọc trong asynchronous callback, càng không thể assume nó chứa context cũ. Đừng tự dùng ThreadLocal để phân quyền ở mọi reactive callback.

### 17.2. Reactor `Context`

Reactor cung cấp `Context` để gắn metadata theo subscription, không phụ thuộc identity của worker thread. Ví dụ runnable độc lập trong Java fragment:

~~~java
Mono<String> greeting =
        Mono.deferContextual(ctx ->
                Mono.just("hello " + ctx.get("tenant")))
            .contextWrite(context ->
                context.put("tenant", "demo"));

greeting.subscribe(System.out::println);
~~~

Đây là fragment có imports Reactor `Mono`; stdout khi subscribe:

~~~text
hello demo
~~~

Trong production, tenant phải được lấy từ **authentication/authorization đã xác minh**, không từ request header tùy ý. Context keys/string demo không phải schema mặc định của Spring Security; khi dùng Spring Security reactive, `ReactiveSecurityContextHolder` khai thác Reactor Context để truy cập security identity.

### 17.3. MDC/tracing propagation

MDC của SLF4J thường dựa trên ThreadLocal, nên khi `publishOn` chuyển threads hoặc callback I/O chạy thread khác, MDC fields có thể mất hoặc leak giữa connections nếu quản lý sai. Sử dụng integration với Micrometer Context Propagation, Reactor context-propagation hooks hoặc tracing libraries phù hợp version; tránh tự copy raw ThreadLocal vào global static fields. Luôn clear khi phục vụ trên pooled thread.

### 17.4. Reactive transaction boundaries

Với R2DBC, `ReactiveTransactionManager` và `TransactionalOperator` gắn transactional state theo Reactor Context phù hợp. Với JDBC/JPA `PlatformTransactionManager`, transaction thường thread-bound; việc chuyển scheduler qua subscribeOn/publishOn **không tự giữ nguyên** transaction của một imperative controller/service method. Offload blocking repository calls cần ranh giới transactional thích hợp ngay trong worker được Spring proxy quản lý.

## 18. Debugging event-loop starvation bằng dữ liệu

### 18.1. Những triệu chứng đáng chú ý

- p95/p99 latency tăng trong khi CPU không nhất thiết tăng tương ứng.
- Nhiều request nhỏ và health checks cũng chậm dù DB route riêng đang lỗi.
- Reactor Netty I/O loop có stack traces đứng ở `Thread.sleep`, socket read blocking, JDBC calls hoặc lock.
- WebClient timeouts xuất hiện đồng thời với HTTP server latency.
- Bounded scheduler active workers/queue tăng tới giới hạn; task scheduling rejection.
- Retries từ client/gateway tăng và connections bị giữ lâu.
- Một phần requests rất nhanh, phần khác bị đợi lâu theo phân phối callback/event loop.

Những dấu hiệu trên cần được đối chiếu bằng tracing/profiling; không kết luận một cách chắc chắn chỉ từ metric duy nhất.

### 18.2. Thread dump

Một thread dump ở thời điểm incident có thể chỉ ra:
- Thread `reactor-http-nio-*` nằm trong `Thread.sleep` hoặc JDBC call.
- Thread `legacy-io-*` đang đợi DB theo thiết kế isolation.
- Threads đang tranh lock hoặc ở trạng thái WAITING/BLOCKED.
- Các Netty event loops nhàn rỗi, nhưng connection pool bottleneck có thể nằm nơi khác.

Đọc nhiều dumps trong một khoảng có ý nghĩa thay vì một snapshot; ghi chú stack traces có thể chứa internal paths/identifiers và xử lý theo chính sách bảo mật.

### 18.3. Reactor `Schedulers.isInNonBlockingThread()`

Reactor có API kiểm thread hiện tại có được đánh dấu NonBlocking không:

~~~java
boolean markedNonBlocking =
        Schedulers.isInNonBlockingThread();
~~~

Một callback chạy trên Reactor Netty event loop thường cho kết quả true; nhưng API này **không xác định mọi chỗ blocking về mặt nghiệp vụ**. False chỉ nói thread không mang marker phù hợp, không phải "được phép block vô hạn". Không dùng kết quả boolean để thay latency/throughput measurements.

### 18.4. BlockHound khi phù hợp

**BlockHound** là công cụ bên ngoài có thể instrument JVM để phát hiện một số lời gọi blocking trên threads không được phép blocking. Có thể tích hợp trong testing/diagnostics khi runtime và Java version tương thích. Nó không phát hiện tất cả loại blocking hoặc làm chuẩn đoán production miễn phí; một số JDK/release cần JVM flags và integration cụ thể. Không thêm `BlockHound.install()` trực tiếp trong demo Boot 3 ở trên khi chưa xác minh compatibility và startup behavior.

BlockHound phát hiện `Thread.sleep` trên Reactor nonblocking threads là tình huống minh họa, nhưng **nó không thay load test, source review, thread dumps hoặc observability**.

### 18.5. Micrometer / Actuator metrics

Nếu triển khai Spring Boot Actuator/Micrometer phù hợp có thể quan sát:
- HTTP server request latency/status distribution.
- Reactor Netty connection pools và event-loop behaviors qua metrics được bật.
- Scheduler active/queued/rejected tasks nếu được instrument.
- WebClient client latency, downstream timeouts, connection pending acquires.
- CPU usage, heap, GC, thread states.
- Business-specific deadlines/circuit breaker states.

Tên metric chính xác phụ thuộc phiên bản Boot/Micrometer/Reactor instrumentation và những binders được cấu hình; đừng bịa một metric name rồi giả định nó luôn có. Mục tiêu là **liên hệ event-loop stalls với loại code hoặc dependency**, không phải lập dashboard chỉ đo tổng QPS.

## 19. Tính toán năng lực và chọn kiến trúc

### 19.1. Khi nào WebFlux có lợi?

WebFlux thường đáng cân nhắc nếu:
- Có rất nhiều concurrent I/O requests, thời gian đợi remote service lớn.
- Các dependencies quan trọng có **non-blocking drivers/clients**.
- Đội ngũ hiểu reactive operators, backpressure, cancellation và context propagation.
- Cần streams, SSE/WebSocket hoặc reactive composition.
- Có khả năng test/monitor scheduling, queues và event-loop health.

### 19.2. Khi nào Spring MVC là lựa chọn tốt?

Spring MVC vẫn phù hợp khi:
- Dùng JPA/Hibernate/JDBC và synchronous integrations là chính.
- Hầu hết business logic là CRUD đơn giản, throughput trong khả năng thread pools.
- Team có kinh nghiệm imperative programming; chi phí debugging reactive lớn hơn lợi ích.
- Bottleneck thực sự nằm ở DB/index/network chứ không phải số request threads.
- Không muốn xây pipeline reactive một nửa rồi offload gần như mọi lời gọi blocking.

Một reactive wrapper bên ngoài một lõi blocking lớn không có cùng lợi ích với non-blocking end-to-end.

### 19.3. Ước lượng số blocking workers

Trong steady state, Little's Law cho một hệ ổn định nói rằng **số yêu cầu đang ở hệ thống ≈ arrival rate × thời gian nằm trong hệ thống**. Ví dụ workload blocking có 100 requests/giây, trung bình mỗi operation giữ worker 0,2 giây, số concurrent operations trung bình cần ~20 nếu giả định tất cả đều thành công và không có queueing bất thường.

Đây là phép ước lượng năng lực, **không phải số thread khuyến nghị cố định**; p99, burst, pool limits, memory và retries cần headroom và load test. Không đặt scheduler 20 workers chỉ vì trung bình tính được 20 mà bỏ qua tail latency.

### 19.4. Reactive pipeline cũng cần chính sách bảo mật

Return type `Mono` không thay:
- Authentication và authorization trên mỗi resource/tenant.
- CSRF nếu dùng cookie-based browser credentials.
- CORS khi cross-origin.
- Input validation, limits, JSON deserialization safeguards.
- Timeouts, retries, idempotency.
- Log redaction, SSRF protection trong outbound requests và dependency allowlists.

Không đọc raw `SecurityContextHolder` ThreadLocal từ scheduler callback để quyết định quyền dữ liệu; Spring Security reactive có các API context phù hợp.

## 20. Những nhầm lẫn nguy hiểm và cách sửa

| Nhầm lẫn / triệu chứng | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| "Trả `Mono` là non-blocking" | `Mono.just(blockingCall())` vẫn thực thi eager | `fromCallable` + `subscribeOn` hoặc đổi non-blocking client |
| "`Thread.sleep` chỉ ảnh hưởng request đó" | Event loop phục vụ nhiều connections | Dùng non-blocking timer hoặc offload legacy call |
| "`publishOn` sau fromCallable offload source" | Source có thể chạy trước publishOn | Dùng `subscribeOn` ngay sau source |
| "`block()` không ném nên an toàn" | Có thể chỉ không chạy trên NonBlocking-marked thread | Không block trong reactive request chain |
| "Event loop = 1 thread/HTTP request" | Một loop multiplex nhiều connections | Hiểu event-driven callback scheduling |
| "WebFlux = WebClient" | WebClient có thể dùng ở Spring MVC | Kiểm Spring Boot application type/starter |
| "WebFlux luôn nhanh hơn MVC" | Reactive không rút ngắn tự động DB/network latency | Benchmark workload, chọn theo dependencies |
| "boundedElastic là pool vô hạn" | Có giới hạn workers/queues | Thiết lập capacity, monitor rejection |
| "Tăng Netty worker count chữa JDBC block" | Vẫn giữ mỗi loop khi JDBC chờ | Offload/change driver, giới hạn đồng thời |
| "`parallel()` xử lý JDBC tốt" | CPU pool có số threads nhỏ, blocking gây starvation | boundedElastic/dedicated blocking scheduler |
| "`timeout` dừng mọi SQL call" | Blocking driver có thể không hủy ngay | Statement/socket timeouts, cancellation-aware driver |
| "`flatMap` luôn chạy mỗi item trong thread mới" | flatMap là composition, không tự tạo scheduler | Explicit scheduler/concurrency as needed |
| "`Flux` ngăn RAM vô hạn" | Backpressure phụ thuộc producer/queues | Bounded buffers, admission, drop/error policy |
| "`subscribe()` trong controller là background job đáng tin" | Tách khỏi request lifecycle, lỗi khó quản lý | Return publisher hoặc durable queue |
| "ThreadLocal luôn đúng cho một request" | Callbacks đổi threads | Reactor Context, reactive security/tracing |
| "SecurityContext tự đi qua mọi worker" | Context propagation phụ thuộc tích hợp | Dùng ReactiveSecurityContextHolder/validated context |
| "`@Transactional` JDBC đi qua publishOn" | Imperative transaction thường thread-bound | Ranh giới transactional đúng hoặc R2DBC |
| "CPU không cao nghĩa WebFlux không bị nghẽn" | Blocking waits giữ loop mà ít CPU | Thread dumps, latency/queue metrics |
| "Một test HTTP 200 là đủ chứng minh concurrency" | Không đo starvation dưới tải | Load tests, thread dumps, scheduler metrics |

## 21. Quy trình khắc phục starvation trong production

1. **Đo hiện trạng**: p95/p99, error/timeout rates, connections, Netty event loops, scheduler queues, DB/HTTP client pools.
2. **Thu stack traces** ở thời điểm incident; tìm `sleep`, JDBC, blocking HTTP, Future.get, locks, excessive serialization.
3. **Xác định nơi code chạy**: event loop, boundedElastic, parallel, dedicated executor hay custom library worker.
4. **Phân loại workload**: I/O cần non-blocking; blocking unavoidable; CPU-bound; streaming/buffering.
5. **Loại bỏ blocking ngay trong handler** và các `map`/`flatMap`/`doOnNext` callbacks; chuyển non-blocking HTTP sang WebClient, DB sang R2DBC nếu khả thi.
6. **Cô lập legacy calls** bằng `fromCallable().subscribeOn(dedicatedBoundedScheduler)`, giữ concurrency hợp lý.
7. **Gắn timeout ở dependency** và end-to-end; phân biệt cancellation signal với driver thực sự dừng.
8. **Giới hạn tải** bằng flatMap concurrency, semaphore/connection pool, rate limit, circuit breaker và bounded queues.
9. **Tránh retry storm**; dùng retry có backoff, idempotency và giới hạn số lần.
10. **Giữ context bảo mật/tracing** qua Reactor Context và approved propagation mechanism.
11. **Kiểm bằng WebTestClient/StepVerifier** các trường hợp success, not found, timeout, cancellation nếu test được deterministic.
12. **Load-test** trên staging với concurrency và payload thực tế; quan sát scheduler rejection và event-loop latency.
13. **Đánh giá lại chọn MVC hay WebFlux** nếu phần lớn dependencies vẫn blocking và reactive maintenance cost lớn.
14. **Theo dõi sau thay đổi**: giảm p99 không làm tăng error/queue time ở tầng khác.
15. **Giữ tài liệu vận hành** về scheduler ownership, dependency budgets, thread naming và alert thresholds.

### Câu hỏi tự kiểm tra

**1.** Vì sao WebFlux có thể dùng ít threads mà quản lý nhiều kết nối?

**Trả lời:** Non-blocking/event-driven I/O cho phép thread quay lại phục vụ các sockets khác trong khi chờ sự kiện I/O, thay vì một thread phải đứng chờ mỗi request.

**2.** `Mono.just(jdbc.find(id))` đã non-blocking chưa?

**Trả lời:** Chưa. `jdbc.find(id)` được gọi ngay khi expression đánh giá, có thể block event loop trước khi Mono tồn tại.

**3.** Cách bọc blocking method chuẩn là gì?

**Trả lời:** `Mono.fromCallable(() -> legacy.find(id)).subscribeOn(Schedulers.boundedElastic())` hoặc một bounded scheduler riêng phù hợp.

**4.** `publishOn(boundedElastic)` phía sau `fromCallable` có bảo đảm source offload không?

**Trả lời:** Không. `publishOn` chủ yếu chuyển downstream signals; source có thể đã chạy trên subscription thread. Dùng `subscribeOn` cho nguồn blocking.

**5.** Vì sao `.block()` có thể ném IllegalStateException trên event loop?

**Trả lời:** Reactor đánh dấu một số threads là non-blocking và ngăn các APIs block vốn không phù hợp với event-loop model. Không ném trên thread khác không chứng minh an toàn.

**6.** `timeout(2s)` có chắc hủy SQL query đang block không?

**Trả lời:** Không. Nó cancel reactive subscription/phát lỗi deadline, nhưng blocking driver có thể tiếp tục; cần driver/network statement timeouts.

**7.** Khi `WebClient` và server cùng dùng Reactor Netty, có gì cần lưu ý?

**Trả lời:** Chúng có thể share event-loop resources theo mặc định, nên block server loop có thể ảnh hưởng outbound HTTP I/O callbacks.

**8.** `Schedulers.parallel()` và `boundedElastic()` dùng giống nhau không?

**Trả lời:** Không. Parallel phù hợp CPU tasks; boundedElastic dành cho các calls blocking legacy cần worker capacity giới hạn.

**9.** `Flux` với backpressure có tự ngăn mọi queue vô hạn không?

**Trả lời:** Không. Phải xét producer có thể tôn trọng demand không, bộ đệm/retry/pool và overload policy của từng tầng.

**10.** Cần kiểm gì khi chuyển từ blocking JDBC sang WebFlux?

**Trả lời:** Chọn một reactive database driver như R2DBC hoặc offload bounded worker, kiểm transaction/context, connection pool, request concurrency, timeouts và production observability.

## 22. Tài liệu chính thức và đọc thêm

- [Spring Framework — WebFlux Overview / Concurrency Model](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html): vì sao WebFlux dùng non-blocking I/O và small event-loop worker pool, khác MVC.
- [Spring Framework — WebFlux Reference](https://docs.spring.io/spring-framework/reference/web/webflux.html): annotated controllers, functional endpoints, Reactive Streams và runtime.
- [Project Reactor — FAQ: Wrap a Synchronous Blocking Call](https://projectreactor.io/docs/core/release/reference/faq.html#faq.wrap-blocking): mẫu `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.
- [Project Reactor — Threading and Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html): `subscribeOn`, `publishOn`, `parallel`, `boundedElastic`, queue/bounds.
- [Project Reactor — Reactor Context](https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html): reactive context thay vì dựa vào ThreadLocal.
- [Project Reactor — Reactor 3.6 Reference](https://projectreactor.io/docs/core/3.6.11/reference/index.html): APIs tương ứng với thế hệ Boot 3.3/Reactor 3.6.
- [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html): reactive HTTP client và I/O connectors.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): HTTP-level tests cho reactive server.
- [Spring Framework — WebFlux Error Handling](https://docs.spring.io/spring-framework/reference/web/webflux/ann-controller/ann-exceptions.html): `@ExceptionHandler`, `ProblemDetail` và exception resolution.
- [Spring Boot — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html): cấu hình WebFlux, server và tests trong Boot 3.3.
- [Reactive Streams Specification](https://www.reactive-streams.org/): demand, backpressure, publisher/subscriber protocols.
- [Project Reactor — Handling Errors](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html): `timeout`, `onErrorResume` và error propagation.

**Kết luận:** WebFlux xử lý tốt nhiều kết nối nhờ event loops **không phải chờ blocking I/O**. Chỉ đặt kiểu trả về `Mono`/`Flux` lên code đồng bộ không đem lại lợi ích reactive. Với API non-blocking, hãy compose publishers; với thư viện blocking không thể thay ngay, dùng `Mono.fromCallable(...).subscribeOn(boundedElastic/dedicatedScheduler)` và giới hạn tải. Giữ event-loop callbacks ngắn, kiểm monitoring, timeout, cancellation, bảo mật context và kiểm thử từng dependency để tránh biến một lời gọi chặn thành nghẽn toàn hệ thống.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-foundations-005">Backpressure, lượng yêu cầu dữ liệu và giới hạn khi đi qua giao thức HTTP</a>

<details>
<summary>Xem chi tiết</summary>

# Backpressure, lượng yêu cầu dữ liệu và giới hạn khi đi qua giao thức HTTP trong Spring WebFlux

## 1. Vì sao WebFlux cần backpressure?

Một server phản hồi API hoặc phát luồng dữ liệu có thể tạo dữ liệu nhanh hơn tốc độ phía nhận xử lý:

```text
Nguồn dữ liệu                   Người tiêu thụ
20.000 events/giây               300 events/giây
         |                              ^
         v                              |
     [ HÀNG ĐỢI ? ] --------------------+
         |
  Bộ nhớ tăng, trễ tăng, có thể OOM
```

Nếu producer cứ tiếp tục tạo không giới hạn, dữ liệu có thể tích tụ trong heap, buffers của codec, network stack hoặc queue nội bộ; nếu giữ mỗi message trong RAM, ứng dụng có thể hết bộ nhớ. **Backpressure** là khả năng phía nhận bày tỏ **nhu cầu (demand)** để phía gửi sản xuất/phát tiếp một lượng phù hợp.

Trong **Reactive Streams**, tín hiệu trung tâm là `Subscription.request(n)`, trong đó `n` là **số phần tử** downstream sẵn sàng nhận; upstream không được gửi `onNext` vượt quá nhu cầu đã cấp cho subscriber đó. Đây là hợp đồng ở **lớp publisher/subscriber**, không phải lệnh HTTP mà browser tự gửi.

**Spring WebFlux** xây trên reactive programming và I/O không blocking để kết nối request/response bodies với các nguồn `Publisher`. Reactor cung cấp `Flux<T>` (0..N giá trị) và `Mono<T>` (0..1 giá trị) cùng operator điều tiết nhu cầu. Nhưng khả năng backpressure từ đầu tới cuối **chỉ có hiệu lực nếu mọi đoạn của pipeline thực sự tôn trọng tín hiệu hoặc xử lý overload có giới hạn**. Một nguồn dữ liệu ngoài hệ thống liên tục đẩy dữ liệu, một API blocking, hoặc một proxy buffer toàn bộ response có thể phá vỡ kỳ vọng “ít tốn RAM”.

Bài học thuộc **Nền tảng WebFlux**, giới thiệu:

- `Publisher`, `Subscriber`, `Subscription` và `request(n)`.
- Cách demand di chuyển **ngược chiều** với dữ liệu.
- `StepVerifier`/`BaseSubscriber` để điều khiển request và quan sát `doOnRequest`.
- Các operator `limitRate`, `buffer`, `flatMap`, `publishOn`, `onBackpressureBuffer/Drop/Latest/Error`.
- Khác biệt giữa cold source và hot source, xử lý slow consumer và nguồn không hỗ trợ demand.
- **Ranh giới quan trọng:** Reactive Streams demand, HTTP response streaming, TCP receive windows, HTTP/2 flow-control windows, proxy buffering và browser đều là những cơ chế khác nhau.
- Thiết kế API WebFlux NDJSON/SSE, cancel/timeout, tests theo layers và production observability.

Baseline ví dụ: **Java 17+, Spring Boot 3.5.x, Spring Framework 6.2.x, Project Reactor 3.7.x, Reactor Netty**. Code không sử dụng `spring-boot-starter-web`, không lẫn Servlet MVC và WebFlux.

## 2. Mô hình Reactive Streams: hai hướng thông tin

Reactive Streams định nghĩa những interface chính (thuộc `org.reactivestreams`):

```java
interface Publisher<T> {
    void subscribe(Subscriber<? super T> subscriber);
}

interface Subscriber<T> {
    void onSubscribe(Subscription subscription);
    void onNext(T value);
    void onError(Throwable error);
    void onComplete();
}

interface Subscription {
    void request(long n);
    void cancel();
}
```

Đoạn trên là **rút gọn chữ ký để học**, không phải file Java cần tự khai báo. Những quy tắc quan trọng:

1. Subscriber nhận `onSubscribe(subscription)` trước các signal dữ liệu.
2. Subscriber gọi `request(n)` để cấp **số lượng phần tử** có thể nhận thêm; `n` phải **dương**. `request(0)` hoặc số âm vi phạm hợp đồng và thường gây `onError`.
3. Tổng demand có thể được cộng dồn; không phải mỗi lần `request(n)` tạo một subscription mới.
4. Upstream chỉ gửi `onNext` trong giới hạn demand còn lại; `onError` hoặc `onComplete` kết thúc stream.
5. `cancel()` thông báo không cần thêm dữ liệu; cancel không phải lời hứa hủy ngay một HTTP request/database job đã khởi chạy ngoài hệ thống.
6. Tín hiệu cần được **serialize theo hợp đồng**; thiết kế publisher tùy tiện gọi `onNext` song song từ nhiều thread có thể sai.

Đường đi của dữ liệu và demand đối nghịch:

```text
Publisher / Flux                     Subscriber
      |                                  |
      |<---- onSubscribe/subscription ---| (khái niệm gắn Subscription)
      |<----------- request(3) ---------|
      |------------ onNext(A) --------->|  còn 2
      |------------ onNext(B) --------->|  còn 1
      |------------ onNext(C) --------->|  còn 0
      |                                  | không request thêm:
      |       phải chờ demand mới       | không được gửi onNext D
      |<----------- request(2) ---------|
      |------------ onNext(D) --------->|
      |------------ onNext(E) --------->|
      |----------- onComplete() ------->|
```

**Lưu ý:** `onSubscribe` là signal từ Publisher tới Subscriber; subscriber nhận được `Subscription` rồi mới gọi request. Hình chỉ nhằm diễn tả hai chiều điều khiển/dữ liệu.

### 2.1. “n” là số phần tử, không phải số byte

Với `Flux<Integer>`, `request(3)` cho phép ba số nguyên. Với `Flux<Record>`, mỗi record là một item có thể dài 100 byte hoặc 10 MB. Với `Flux<DataBuffer>`, mỗi item là một bộ đệm bytes, kích thước khác nhau.

Vì vậy:

- `request(10)` **không** cam kết cấp đúng 10 KB.
- `request(1)` **không** đồng nghĩa “chỉ đọc 1 byte từ TCP”.
- Nếu mỗi item rất lớn, backpressure theo item count vẫn có thể gây memory pressure.
- Nếu operator `buffer(1000)` tạo `List<T>`, **một item đầu ra** có thể chứa 1000 item nguồn.
- TCP window tính theo bytes và buffer capacity, không theo số Java objects.

Thiết kế phải đo **element size, queue size, bytes in-flight** và throughput, không chỉ xem demand count.

### 2.2. `Long.MAX_VALUE` là unbounded demand

Nhiều cách subscribe tiện lợi dùng demand gần như không giới hạn:

```java
Flux.range(1, 10).subscribe(System.out::println);
```

Đoạn này là fragment trong Java class có Reactor dependency; nó không phải cách test backpressure từng phần tử vì các overload lambda `subscribe` thường request `Long.MAX_VALUE`. Điều đó thích hợp cho những luồng nhỏ và nhanh nhưng **không chứng minh một consumer production có giới hạn demand**.

`block()`, `blockLast()` cũng tiêu thụ eager/unbounded ở nhiều đường xử lý. Trên Netty event-loop của WebFlux, **không gọi `block()`** để “đợi” kết quả reactive, vì nó có thể chặn luồng I/O và giảm nghiêm trọng throughput hoặc ném exception.

## 3. Ví dụ Java: quan sát demand mà không có HTTP

Muốn học backpressure, trước hết tách HTTP khỏi bài toán để nhìn đúng `request(n)`. Phương pháp ổn định là `StepVerifier` với **initial request bằng 0**.

### 3.1. StepVerifier cấp phát từng lô

```java
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

AtomicInteger produced = new AtomicInteger();

Flux<Integer> source = Flux.range(1, 5)
        .doOnNext(value -> produced.incrementAndGet());

StepVerifier.create(source, 0)
        .expectSubscription()
        .then(() -> {
            if (produced.get() != 0) {
                throw new AssertionError(
                        "Source emitted without demand");
            }
        })
        .thenRequest(2)
        .expectNext(1, 2)
        .thenRequest(3)
        .expectNext(3, 4, 5)
        .verifyComplete();
```

Đây là **fragment Java trong một test method**, cần `reactor-test` trong test dependencies. `StepVerifier.create(source, 0)` có nghĩa **không cấp nhu cầu ban đầu**, *không phải* gọi `Subscription.request(0)` (một thao tác không hợp lệ theo Reactive Streams). Sau `thenRequest(2)`, subscriber nhận đúng hai item; sau `thenRequest(3)` nhận ba item còn lại.

Nếu nguồn tôn trọng demand, trước lần request đầu không có `onNext`. `Flux.range` là cold publisher và tôn trọng pull demand, phù hợp quan sát cơ chế.

### 3.2. BaseSubscriber điều khiển thủ công

```java
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

Flux.range(1, 6).subscribe(
    new BaseSubscriber<Integer>() {
        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            request(2);
        }

        @Override
        protected void hookOnNext(Integer value) {
            System.out.println("got=" + value);
            if (value == 2) {
                request(2);
            }
            if (value == 4) {
                cancel();
            }
        }

        @Override
        protected void hookOnCancel() {
            System.out.println("cancelled");
        }
    });
```

Đây là fragment trong method Java có Reactor Core. Với `Flux.range` đồng bộ và không thêm scheduler, các giá trị mong đợi là 1,2,3,4 rồi cancel; các giá trị 5,6 **không được yêu cầu**. Ví dụ có output định hướng:

```text
got=1
got=2
got=3
got=4
cancelled
```

Trong các pipeline dùng nhiều schedulers, đừng dựa vào thứ tự log giữa các threads để suy ra request boundaries khi chưa xác định scheduling và concurrency.

**`cancel()` là tín hiệu hủy đối với Subscription**, không tương đương rollback một giao dịch đã commit hoặc thu hồi một message đã gửi sang Kafka.

## 4. Demand không nhất thiết đi nguyên xi qua operator

Trong thực tế, downstream `request(3)` có thể khiến upstream thấy request **khác 3** vì nhiều operator phải lấy trước dữ liệu, gom nhóm hoặc xử lý nhiều inner publishers.

```text
HTTP writer requests 1 record
       |
       v
map          -> thường theo demand 1:1
       |
buffer(10)   -> cần tới 10 upstream records cho 1 List<Record>
       |
flatMap(...) -> có concurrency và prefetch riêng
       |
publishOn   -> hàng đợi + prefetch, replenishment
       |
Data source receives transformed demand
```

### 4.1. `map` / `filter`

- `map` thường biến đổi một input thành một output nên dễ hiểu demand.
- `filter` có thể bỏ qua item. Để cung cấp đủ **n output hợp lệ** cho downstream, nó có thể cần request **nhiều hơn n source items**.
- `take(n)` giới hạn **tổng item downstream được nhận** và cancel upstream khi đạt số lượng mong muốn (xem overload và version semantics).
- `buffer(k)` gom k source items thành một `List<T>`; demand downstream theo **số list**, không phải số phần tử chứa bên trong.

Ví dụ:

```java
Flux<List<Integer>> windows =
        Flux.range(1, 10).buffer(5);
```

Nếu subscriber request **1**, pipeline có thể cần đọc **5 số** để phát một list `[1,2,3,4,5]`. Đây không phải lỗi backpressure; nó là biến đổi đơn vị demand.

### 4.2. `flatMap` và concurrency

```java
Flux<Result> results = keys
        .flatMap(
            key -> repository.findByKey(key),
            8,   // concurrency: tối đa các inner publishers đồng thời
            16); // prefetch cho inner publishers
```

Đây là **fragment**, giả định `keys` là Flux và repository reactive trả Publisher/Mono phù hợp. `flatMap` có thể yêu cầu dữ liệu từ nhiều inner sequences song song; `concurrency=8` và `prefetch=16` giới hạn những thông số operator, **không** phải giới hạn tổng heap có thể dùng trong cả request.

Không suy ra memory upper bound chính xác chỉ bằng phép nhân 8×16: mỗi item có kích thước khác nhau và còn có queues, codecs, outbound buffers và nguồn ngoài operator. Nếu thứ tự kết quả quan trọng, `concatMap` hoặc `flatMapSequential` với trade-offs tương ứng có thể phù hợp.

### 4.3. `publishOn` tạo ranh giới scheduler và queue

```java
Flux<Data> processed = source
        .publishOn(Schedulers.parallel(), 32)
        .map(this::transform);
```

Fragment giả định `source`, `Data`, `transform` đã có. `publishOn` chuyển downstream processing sang scheduler phù hợp và có `prefetch` nội bộ. Nó có thể phát upstream `request(32)` ban đầu ngay cả khi consumer cuối cùng chỉ đang quan sát một vài items.

**Đừng dùng `publishOn(Schedulers.parallel())` cho blocking JDBC, `Thread.sleep`, file I/O blocking hoặc HTTP client blocking.** Những tác vụ này cần hệ thống non-blocking thật hoặc cô lập bằng `boundedElastic` theo thiết kế.

## 5. `limitRate`: chia nhu cầu lớn thành đợt, không chặn tổng

Một subscriber (bao gồm nhiều thành phần do framework quản lý) có thể request lượng lớn hoặc unbounded. `limitRate(int)` giúp upstream nhìn nhu cầu theo **batches**.

```java
Flux<Integer> limited = Flux.range(1, 100)
        .doOnRequest(n ->
                System.out.println("upstream requested=" + n))
        .limitRate(10);
```

Nếu downstream request 100, upstream sẽ nhận các request theo batch với mức high-tide 10 và cơ chế **75% replenishment** mặc định. Tức **không** nhất thiết logs là mười dòng `request(10)` liên tiếp: khi 75% buffer đã tiêu thụ, operator có thể xin thêm sớm.

Để minh họa strict batch theo low-tide=0:

```java
Flux<Integer> strictBatches = Flux.range(1, 100)
        .doOnRequest(n ->
                System.out.println("requested upstream=" + n))
        .limitRate(10, 0);
```

`limitRate(10, 0)` gửi nhu cầu theo từng lô tối đa 10 trước khi cấp tiếp; vẫn có thể truyền **toàn bộ 100 item** khi downstream tiếp tục request. Nó **không** tương đương `take(10)`, không phải time-based rate limiting kiểu “10 requests per second”, và không phải HTTP rate limiter chặn spam.

### 5.1. Ba khái niệm rất dễ nhầm

| API | Tác dụng chính | Không có nghĩa |
| --- | --- | --- |
| `limitRate(10)` | Chia downstream demand trước khi upstream thấy | Không giới hạn tổng output còn 10 |
| `take(10)` | Lấy tối đa 10 items rồi kết thúc/cancel | Không bảo đảm HTTP 10 bytes/chunks |
| `delayElements(Duration.ofMillis(100))` | Hoãn phát phần tử theo thời gian với scheduler | Không đảm bảo rate limit đối với mọi upstream nóng |
| `timeout(Duration.ofSeconds(5))` | Báo lỗi hoặc fallback nếu thiếu signal đúng hạn | Không khống chế số connection đồng thời |
| `flatMap(mapper,8,16)` | Điều chỉnh inner concurrency/prefetch | Không phải application-wide request quota |

Khi cần rate limiting **requests per client/tenant**, dùng gateway, security/rate limiter hoặc cơ chế server-side có shared state. `limitRate` là operator quản lý demand của reactive sequence, không bảo vệ endpoint công khai khỏi DDoS.

## 6. Cold publisher và hot publisher

### 6.1. Cold source

Một publisher như `Flux.range`, `Flux.defer` hoặc `Mono.fromCallable` thường sản xuất riêng theo mỗi subscription. Cold source có khả năng tuân theo request demand tự nhiên nếu implementation đúng.

```java
Flux<Integer> cold = Flux.range(1, 5)
        .doOnSubscribe(s -> System.out.println("new subscriber"));
```

Hai subscribers thường nhận hai lần phát dữ liệu độc lập. Nếu source là truy vấn R2DBC hoặc reactive HTTP client, subscribe mới có thể khởi tạo I/O mới; cần quan tâm cancellation và connection/resource cleanup.

### 6.2. Hot source

Một sensor, WebSocket incoming stream, Kafka consumer hoặc callback listener có thể có dữ liệu **không phụ thuộc** vào subscriber hiện tại. Nếu không còn downstream demand, producer ngoài hệ thống có thể vẫn phát.

```text
Hot source:  event event event event event ...
Subscriber: request 1 rồi chậm 5 giây
              |
              v
  Phải chọn: buffer có giới hạn, drop, latest,
             báo overflow, giảm tốc upstream ngoài hệ thống,
             hoặc disconnect subscriber
```

`Flux.interval` cũng là ví dụ một nguồn theo clock; nếu subscriber không theo kịp, nó **có thể phát lỗi overflow** thay vì cứ âm thầm xếp hàng vô hạn. Đừng giả định mỗi hot publisher tự buffer tất cả messages.

Một API nguồn ngoài có **pause/resume** hoặc hỗ trợ backpressure (ví dụ demand-aware cursor) thuận tiện hơn nguồn callback chỉ có thể push. Với nguồn không thể làm chậm, cần **chiến lược overflow rõ ràng**.

## 7. Các chiến lược `onBackpressure...`

Reactor cung cấp operators để xử lý những nguồn có áp lực không tương thích với downstream.

| Operator | Khi downstream thiếu demand | Trường hợp phù hợp | Rủi ro |
| --- | --- | --- | --- |
| `onBackpressureBuffer()` | Xếp backlog | Chậm tạm thời | Bản unbounded có thể gây OOM |
| `onBackpressureBuffer(maxSize,...)` | Giữ đến giới hạn, rồi overflow theo overload/strategy | Chấp nhận độ trễ có giới hạn | Cần policy, metrics và error handling |
| `onBackpressureDrop()` | Bỏ giá trị không thể giao ngay | Telemetry có thể mất mẫu | Mất dữ liệu; không dùng cho giao dịch |
| `onBackpressureLatest()` | Giữ giá trị mới nhất thay vì mọi giá trị | Tiến độ %, gauge live | Bỏ qua các trạng thái trung gian |
| `onBackpressureError()` | Báo overflow error | Luồng cần integrity, fail-fast | Client cần retry/recovery |
| `limitRate` | Chia nhu cầu đi ngược lên source có demand support | Cursor/DB paging | Không xử lý nguồn ngoài cứ push bất chấp demand |

### 7.1. Bounded buffer — phải có kế hoạch khi đầy

```java
Flux<Event> buffered = hotEvents
        .onBackpressureBuffer(
            256,
            event -> metrics.recordOverflow());
```

Đây là fragment, `Event`, `hotEvents` và `metrics` do ứng dụng cung cấp. Overload trên có capacity hữu hạn và callback overflow; khi đầy có thể kết thúc với overflow error theo API semantics. **Không dùng buffer unbounded** cho bất kỳ topic hoặc user không giới hạn, nhất là dữ liệu nhạy cảm.

Cần xác định:

- Max backlog theo **items và bytes**.
- Khi đầy thì trả lỗi/disconnect, drop oldest/newest hay coalesce.
- Deadline/TTL cho event quá cũ.
- Ai dọn dữ liệu buffered khi client cancel.
- Metrics về backlog size/age/overflow count.
- Sự khác nhau giữa **global buffer** và buffer **mỗi subscriber**, vì 10.000 subscribers × 256 item có thể rất lớn.

### 7.2. Drop/latest không dùng tùy tiện

```java
Flux<Progress> latestProgress = updates
        .onBackpressureLatest();
```

Thích hợp cho dashboard chỉ quan tâm **% mới nhất**, nhưng không thích hợp cho balance changes, payment events, security audit hoặc dữ liệu mà việc bỏ một message làm thay đổi kết quả.

```java
Flux<Telemetry> sampled = telemetry
        .onBackpressureDrop(value ->
                metrics.incrementDropped());
```

Ví dụ fragment này chấp nhận mất mẫu telemetry. Nếu `Telemetry` phải lưu chính xác mọi điểm, không được drop; thay bằng broker/durable log, giảm nguồn hoặc phân tách ingest/serving.

### 7.3. `onBackpressureError` và HTTP response đã commit

Một `Flux` đang được WebFlux dùng để ghi response có thể gặp `onError` vì overflow. Nếu HTTP headers/body đã bắt đầu được gửi, server **không thể chắc chắn đổi status 200 thành 500** hoặc gửi một JSON error envelope nguyên vẹn. Client có thể thấy stream bị ngắt.

Với SSE cần một protocol/reconnect/replay policy; với NDJSON cần xác định incomplete stream/cancel semantics. Không giả định operator lỗi sẽ tự tạo `HTTP 429`. **429 Too Many Requests** là HTTP rate limiting/application policy khác, cần thiết kế tường minh trước khi commit response.

## 8. Spring WebFlux ghép Reactive Streams với HTTP như thế nào?

Spring WebFlux dùng `HttpHandler`, `WebHandler`, HTTP codecs (`HttpMessageReader`, `HttpMessageWriter`), reactive server adapter và `DataBuffer` để xử lý body theo mô hình non-blocking với backpressure.

```text
Business Flux<Item>
    |
    | demand điều phối qua Reactor Publisher/Subscriber
    v
JSON/SSE encoder
    |
    v
Flux<DataBuffer>
    |
    v
ServerHttpResponse / HttpHandler adapter
    |
    v
Reactor Netty / TCP socket output
    |
    v
HTTP/1.1 chunks hoặc HTTP/2 DATA frames
    |
    v
Browser / WebClient / Gateway
```

Khi mạng chậm, server adapter có thể dùng non-blocking write readiness và buffer watermarks để điều tiết số buffers đọc từ pipeline. Nhưng phải phân biệt:

- **Reactor demand:** số `T` hoặc `DataBuffer` theo `request(n)` trong JVM.
- **Netty buffer/write readiness:** trạng thái event loop, output queue và writable thresholds.
- **TCP flow control:** receive window theo **bytes**; endpoint TCP điều phối stream bytes qua ACK/window updates.
- **HTTP/2 flow control:** connection-level và stream-level windows cho **DATA bytes**, không phải `Flux<Item>` request(n).
- **HTTP/1.1 chunked transfer:** phân đoạn body khi không có Content-Length; chunk boundaries không tương ứng 1:1 với `onNext`.
- **HTTP client application:** trình duyệt đọc/parse payload với tốc độ riêng; JavaScript không nhận `Subscription` từ server qua giao thức HTTP tiêu chuẩn.

Điểm cốt lõi: **không có một HTTP header tiêu chuẩn tương đương “request(3) Java objects”** được trình duyệt gửi sau mỗi ba SSE messages. WebFlux xây cầu nối giữa reactive demand và năng lực ghi/đọc tại HTTP adapter, nhưng mapping có buffer/codec và không phải “một item bằng một TCP packet”.

### 8.1. Ví dụ tốc độ bị giới hạn bởi tầng nào?

Giả sử `Flux` sinh 1000 records/s, browser xử lý 100 records/s:

1. Nếu browser/client không kịp đọc bytes, receive buffers và TCP/HTTP2 windows có thể hạn chế tốc độ truyền.
2. Server write queue có thể không writable, HTTP adapter giảm demand hoặc trì hoãn writes.
3. Một vài items vẫn nằm trong codec/operator prefetch/network buffers.
4. Nếu source cold và demand-aware, cuối cùng nó có thể sản xuất chậm lại.
5. Nếu source hot từ một broker vẫn đẩy không giới hạn, **tầng này vẫn cần queue/drop/pause strategy**.
6. Nếu CDN đọc toàn bộ response thật nhanh rồi buffer cho browser, origin **không còn thấy browser chậm theo cùng cách**.

Do đó phải test **end-to-end** trên topology thật, không chỉ nhìn `doOnRequest` trong controller.

### 8.2. Streaming media types quyết định framing

| Media type | Tổ chức body | Streaming thường kỳ vọng |
| --- | --- | --- |
| `application/json` | Một JSON value/array hoàn chỉnh | Một `Flux<T>` có thể được kết thành array, có thể buffering tùy encoder |
| `application/x-ndjson` | Mỗi dòng một JSON object | Có thể encode từng phần tử và truyền dần |
| `text/event-stream` | Các SSE frames `id/event/data` + dòng trống | Truyền sự kiện theo thời gian, browser EventSource |
| `application/octet-stream` | Raw bytes | Dùng `Flux<DataBuffer>`/Resource ở layer phù hợp |
| `text/plain` | Text bytes; không tự biết message delimiter | Cần định nghĩa framing khi nhiều items |

**Một Flux không tự bảo đảm HTTP response stream hiển thị từng event ngay.** Media type, codec, flush semantics, proxy buffering và client parser đều ảnh hưởng.

Với `application/json`, WebFlux có thể cần tổng hợp dữ liệu cho JSON array; với `application/x-ndjson` hoặc SSE, message writer thường xử lý streaming theo item/event. Nếu cần low latency quan sát được, test actual first byte/first frame arrival.

## 9. NDJSON và SSE: cùng Flux nhưng client khác nhau

### 9.1. NDJSON

```http
HTTP/1.1 200 OK
Content-Type: application/x-ndjson

{"id":1,"name":"item-1"}
{"id":2,"name":"item-2"}
{"id":3,"name":"item-3"}
```

Mỗi dòng là một JSON value hợp lệ. Client có thể parse từng line. Nếu request bị cancel sau dòng thứ hai, stream có thể chỉ chứa hai dòng — protocol cần quyết định **partial result** có được coi là hợp lệ hay không.

### 9.2. Server-Sent Events

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream

id: 1
event: progress
data: {"seq":1,"state":"running"}

id: 2
event: done
data: {"seq":2,"state":"complete"}

```

SSE là giao thức **server → browser** qua HTTP GET. Browser `EventSource` hiểu `data`, `id`, `event`, tự reconnect trong nhiều trường hợp. Nhưng `Last-Event-ID` **không tự tạo durable replay ở server**; khi cần không mất events, phải lưu event log, gắn IDs và xử lý resumption.

**SSE không đồng nghĩa Reactive Streams end-to-end qua browser.** `EventSource` không có API `request(5)` điều khiển trực tiếp upstream Reactor. Browser có thể hủy kết nối (`source.close()`) và network stack cung cấp áp lực theo bytes, nhưng application-level demand không truyền thành Reactive Streams request signal một-một.

### 9.3. Heartbeat

WebFlux docs khuyến nghị gửi dữ liệu/heartbeat định kỳ để phát hiện client đã disconnect sớm hơn, vì một kết nối im lặng không luôn báo cancel tức thì. SSE có thể gửi comment:

```text
: keep-alive

```

Heartbeat cần nhỏ hơn idle timeout của reverse proxy/gateway phù hợp và không được tạo vòng lặp/queue không giới hạn cho mỗi subscriber.

## 10. Ví dụ chạy được: Spring Boot 3.5 WebFlux + Java 17

Demo gồm:

1. `GET /api/items` trả năm items dạng một JSON array.
2. `GET /api/items.ndjson` trả năm items NDJSON và log upstream demand.
3. `GET /api/events` gửi ba SSE events có thứ tự, sau đó complete; client có thể thấy từng event.
4. Không dùng DB, network broker hoặc blocking I/O nên ví dụ giữ scope để quan sát Reactive Streams và HTTP framing.
5. Các tests gồm `StepVerifier` kiểm tra demand không có HTTP và `WebTestClient` qua server thật kiểm tra NDJSON/SSE.

### 10.1. Tạo project

Chọn **Spring Boot 3.5.x**, Java 17, Gradle hoặc Maven, dependency **Spring Reactive Web (WebFlux)**. Với project Boot Gradle plugin/BOM/Java toolchain đã cấu hình, dùng:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **fragment của `build.gradle`**, không phải toàn bộ file build. `spring-boot-starter-webflux` dùng Reactor Netty mặc định khi ứng dụng chạy reactive stack. **Không thêm `spring-boot-starter-web` chỉ vì muốn dùng `@RestController`**: khi cả MVC và WebFlux starters có mặt, Boot 3.x thường chọn MVC làm web application type mặc định; điều này làm demo khác hoàn toàn mục tiêu.

### 10.2. Toàn bộ Java application

Tạo `src/main/java/demo/backpressure/BackpressureApplication.java`:

```java
package demo.backpressure;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@SpringBootApplication
public class BackpressureApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackpressureApplication.class, args);
    }
}

record Item(int id, String name) {}

record Progress(long seq, String state) {}

@RestController
@RequestMapping("/api")
class BackpressureController {
    private static final Logger log =
            LoggerFactory.getLogger(BackpressureController.class);

    private Flux<Item> items() {
        return Flux.range(1, 5)
                .map(id -> new Item(id, "item-" + id));
    }

    @GetMapping(
            path = "/items",
            produces = MediaType.APPLICATION_JSON_VALUE)
    Flux<Item> jsonItems() {
        return items();
    }

    @GetMapping(
            path = "/items.ndjson",
            produces = MediaType.APPLICATION_NDJSON_VALUE)
    Flux<Item> ndjsonItems() {
        return items()
                .doOnRequest(n ->
                        log.info("items_upstream_demand={}", n))
                .limitRate(2)
                .doOnCancel(() ->
                        log.info("items_stream_cancelled"));
    }

    @GetMapping(
            path = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Progress>> events() {
        return Flux.range(1, 3)
                .delayElements(Duration.ofMillis(150))
                .map(seq ->
                        ServerSentEvent.<Progress>builder()
                            .id(Integer.toString(seq))
                            .event(seq == 3
                                    ? "done" : "progress")
                            .data(new Progress(seq,
                                    seq == 3
                                        ? "complete" : "running"))
                            .build())
                .doOnCancel(() ->
                        log.info("sse_stream_cancelled"));
    }
}
```

**Mã phù hợp Java 17 + Boot 3.5 WebFlux:** file có một `public` class `BackpressureApplication`; records/controller package-private cùng package, được component scan. `Flux.range` là cold, finite và backpressure-aware. Không có `Thread.sleep` hoặc `block()` trong handler, cũng không tự `subscribe()` bên trong controller; **WebFlux sở hữu subscription** khi ghi HTTP response.

**`limitRate(2)` được áp sau `doOnRequest`**, nên logger phía upstream có thể quan sát requests bị chia lô, thay vì nghĩ một HTTP client đã gửi trực tiếp `request(2)`. Exact request log sequence không nên hardcode, vì low-tide replenishment và codec/server có thể khác.

**SSE demo chỉ ba events** để tests chạy hữu hạn. `delayElements` tạo khoảng cách quan sát được giữa events nhưng không phải rate limiter production cho source hot không hợp tác. Nếu client đóng sớm, `doOnCancel` có thể ghi nhận khi tín hiệu cancel tới subscription. Đừng khẳng định TCP đã đóng thì callback chắc xuất hiện ngay tức khắc khi server không còn ghi thêm.

### 10.3. Chạy curl

```bash
./gradlew bootRun
```

**JSON array:**

```bash
curl -i -H 'Accept: application/json' \
  'http://localhost:8080/api/items'
```

Body dự kiến (không phụ thuộc whitespace):

```json
[
  {"id":1,"name":"item-1"},
  {"id":2,"name":"item-2"},
  {"id":3,"name":"item-3"},
  {"id":4,"name":"item-4"},
  {"id":5,"name":"item-5"}
]
```

**NDJSON:**

```bash
curl -N -i -H 'Accept: application/x-ndjson' \
  'http://localhost:8080/api/items.ndjson'
```

Body dự kiến:

```text
{"id":1,"name":"item-1"}
{"id":2,"name":"item-2"}
{"id":3,"name":"item-3"}
{"id":4,"name":"item-4"}
{"id":5,"name":"item-5"}
```

**SSE:**

```bash
curl -N -i -H 'Accept: text/event-stream' \
  'http://localhost:8080/api/events'
```

Body có dạng:

```text
id:1
event:progress
data:{"seq":1,"state":"running"}

id:2
event:progress
data:{"seq":2,"state":"running"}

id:3
event:done
data:{"seq":3,"state":"complete"}

```

Có thể có dấu cách sau `:` hoặc thứ tự field khác theo encoder. Điều quan trọng là event ID, type, data và dòng trống phân cách. Ví dụ finite nên kết nối đóng sau event thứ ba. Browser EventSource có thể **tự kết nối lại**; client demo muốn dừng phải xử lý event `done` rồi `source.close()`.

`curl -N` tắt buffering của **curl output**, không bắt origin, proxy và CDN bắt buộc flush mỗi phần tử.

## 11. Kiểm thử đúng hai tầng: Reactive Streams và HTTP

### 11.1. Unit test demand bằng StepVerifier

Đoạn test này cô lập Reactor core, xác minh nguồn cold **không phát phần tử khi chưa được cấp demand**, rồi phát đủ từng đợt.

Tạo file `src/test/java/demo/backpressure/BackpressureApplicationTests.java` với class test phía dưới.

### 11.2. HTTP integration test bằng WebTestClient

`WebTestClient` có thể chạy cùng server thực khi `@SpringBootTest(webEnvironment=RANDOM_PORT)`. Nó decode response body theo media type; tests sau xác minh status và item/event contents, **không suy ra trực tiếp request(n) qua TCP**.

```java
package demo.backpressure;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackpressureApplicationTests {

    @Autowired
    WebTestClient client;

    @Test
    void coldSourceWaitsForRequests() {
        AtomicInteger produced = new AtomicInteger();

        Flux<Integer> source = Flux.range(1, 5)
                .doOnNext(x -> produced.incrementAndGet());

        StepVerifier.create(source, 0)
                .expectSubscription()
                .then(() -> assertEquals(0, produced.get()))
                .thenRequest(2)
                .expectNext(1, 2)
                .thenRequest(3)
                .expectNext(3, 4, 5)
                .verifyComplete();

        assertEquals(5, produced.get());
    }

    @Test
    void ndjsonStreamsFiveItemsThroughHttp() {
        Flux<Item> body = client.get()
                .uri("/api/items.ndjson")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.APPLICATION_NDJSON)
                .returnResult(Item.class)
                .getResponseBody();

        StepVerifier.create(body)
                .expectNextMatches(item ->
                        item.id() == 1
                                && "item-1".equals(item.name()))
                .expectNextCount(3)
                .expectNextMatches(item ->
                        item.id() == 5
                                && "item-5".equals(item.name()))
                .verifyComplete();
    }

    @Test
    void sseCarriesTypedEventsAndCompletes() {
        Flux<ServerSentEvent<Progress>> body =
                client.get()
                    .uri("/api/events")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(
                            MediaType.TEXT_EVENT_STREAM)
                    .returnResult(
                        new ParameterizedTypeReference<
                            ServerSentEvent<Progress>>() {})
                    .getResponseBody();

        StepVerifier.create(body)
                .expectNextMatches(event ->
                        "progress".equals(event.event())
                            && "1".equals(event.id())
                            && event.data() != null
                            && event.data().seq() == 1)
                .expectNextMatches(event ->
                        "progress".equals(event.event())
                            && "2".equals(event.id())
                            && event.data() != null
                            && event.data().seq() == 2)
                .expectNextMatches(event ->
                        "done".equals(event.event())
                            && "3".equals(event.id())
                            && event.data() != null
                            && "complete".equals(
                                    event.data().state()))
                .verifyComplete();
    }
}
```

Chạy:

```bash
./gradlew test
```

Ba tests được **thiết kế để pass** trên Boot 3.5 WebFlux/Java17 với source ở phần 10. Đây là **kỳ vọng qua kiểm tra mã và tài liệu**, **không phải log thực thi Gradle**. Test dùng WebTestClient qua **RANDOM_PORT** để HTTP codec và Reactor Netty cùng tham gia; khác với mock bindToController tests.

**Điểm cần nhớ:** test `coldSourceWaitsForRequests` trực tiếp chứng minh demand của Reactive Streams. Hai HTTP tests chứng minh serialization/framing/event contents. Chúng **không thể xác nhận “HTTP client gửi `request(5)` đến server”**, vì wire protocol không có signal đó.

## 12. Quan sát request(n) trong pipeline, đúng chỗ và đúng mục tiêu

`doOnRequest` ghi lại demand đi qua một vị trí cụ thể:

```java
Flux<Integer> observable = Flux.range(1, 100)
        .doOnRequest(n ->
                log.debug("upstream_request_count={}", n))
        .publishOn(Schedulers.parallel(), 16)
        .filter(n -> n % 2 == 0)
        .limitRate(4);
```

Một client cuối cùng request 4 giá trị **không bắt buộc** log upstream `request(4)`. `publishOn` có prefetch 16; `filter` cần bỏ item lẻ; `limitRate` bổ sung batch logic.

Nếu chuyển `doOnRequest` sang **sau** `publishOn`, số request được log có thể khác. Vì thế khi debug:

- Đặt `doOnRequest` ở hai đầu của operator nghi ngờ, gắn tên stage cụ thể.
- Dùng `doOnNext`, `doOnCancel`, `doFinally` để quan sát terminal/cleanup signals.
- Nếu cần debug tín hiệu ở mức chi tiết, dùng Reactor `log()` có kiểm soát trong dev/test; không bật log mọi item ở production traffic cao.
- Đừng ghi raw payload, auth tokens hoặc PII vào telemetry.
- Theo dõi **queue size và bytes**, không chỉ `request(n)` count.
- Dùng Reactor metrics/Micrometer hoặc OpenTelemetry theo cấu hình Boot đang dùng; operator labels phải có cardinality thấp.

### `doOnRequest` có thể nhận `Long.MAX_VALUE` mà hệ thống vẫn vận hành hợp lý

Downstream của nhiều operator/framework có thể request unbounded và nội bộ dùng write readiness/queues. Điều đó không đủ chứng minh cả ứng dụng không có backpressure; cần xem toàn chuỗi, khả năng buffering và protocol adapter. Ngược lại, log request(1) cũng không chứng minh client thật đã xử lý xong item trước đó.

## 13. Backpressure và request body uploads

Backpressure không chỉ dành cho **response**. Khi WebFlux nhận HTTP request body, adapter/`HttpMessageReader` có thể cung cấp `Flux<DataBuffer>` hoặc decode thành publisher:

```text
Client POST streamed body
    |
    v
TCP receive buffer / HTTP/2 flow-control
    |
    v
Reactor Netty inbound byte buffers
    |
    v
Flux<DataBuffer> -> HttpMessageReader/Decoder
    |
    v
Application processing & bounded storage
```

Nhưng có ranh giới:

- Nếu controller gọi `bodyToMono(String.class)` hoặc `@RequestBody Mono<LargeObject>`, decoder có thể **tích lũy dữ liệu** để tạo một object.
- `spring.codec.max-in-memory-size` giới hạn bộ nhớ cho nhiều codec khi phải buffer/aggregate; **không phải total HTTP upload cap** cho mọi dạng stream/file.
- `multipart/form-data` có rules/limits riêng (số parts, disk usage, file handling), xem docs chính xác theo Spring version.
- Trực tiếp tiêu thụ `DataBuffer` cần quản lý lifecycle; với pooled Netty buffers, phải **release** đúng khi tự giữ/discard chúng. Nếu dùng standard codecs, framework thường xử lý việc quản lý buffers.
- Đừng chuyển mọi incoming body thành `collectList()` hoặc `DataBufferUtils.join(...)` không giới hạn; có thể mất lợi ích streaming và gây OOM.
- Kiểm tra kích thước request tại **gateway, web server, decoder và nghiệp vụ**, áp quota/rate limits và auth.
- `Flux<DataBuffer>` chỉ thể hiện buffer chunks; một multipart upload có thể chứa file rất lớn dù chỉ từng chunk nhỏ.

Ví dụ property giới hạn các codec phải aggregate:

```properties
spring.codec.max-in-memory-size=256KB
```

**Không kết luận request 10 GB đã an toàn** chỉ vì mỗi codec limit 256 KB: nếu pipeline stream file tới disk/object store, total bytes/quota/cancellation/storage limits vẫn cần kiểm soát riêng.

## 14. Blocking I/O trong WebFlux phá vỡ lợi ích của demand-aware pipeline

Một handler WebFlux sai kiểu:

```java
@GetMapping("/report")
Mono<Report> report() {
    Report report = blockingJdbcDao.load(); // Chạy ngay trên event loop.
    return Mono.just(report);
}
```

Đây là **anti-pattern fragment**, giả định `blockingJdbcDao` trả Report. Spring WebFlux event-loop có ít threads; blocking JDBC hoặc `Thread.sleep` có thể giữ thread quá lâu, ảnh hưởng mọi request khác.

Nếu vẫn buộc dùng thư viện blocking trong giai đoạn migration:

```java
@GetMapping("/report")
Mono<Report> report() {
    return Mono.fromCallable(() -> blockingJdbcDao.load())
            .subscribeOn(Schedulers.boundedElastic());
}
```

Đây là **fragment** cần Reactor `Mono`, `Schedulers`, và service/record `Report`. `boundedElastic` cô lập blocking operation khỏi Netty event loop theo một pool có giới hạn; **không biến JDBC thành non-blocking** và không giải quyết không giới hạn connection pool, timeout, queue hoặc thread count.

Khuyến nghị:

- Dùng R2DBC/reactive database driver nếu cần backpressure ở database read path.
- Dùng `WebClient` thay blocking HTTP client khi pipeline cần async/non-blocking.
- Với cursor/query, kiểm tra driver có thật sự tôn trọng demand và có resource lifecycle.
- Cấu hình DB pool, HTTP outbound concurrency và global rate limits bên cạnh Reactor prefetch.
- Tránh `block()`, `toIterable()`, đồng bộ locks hoặc CPU-heavy loops trên Netty event loop.
- Với CPU-heavy work, scheduler `parallel` hoặc dedicated executor theo measurements; không tùy tiện `subscribeOn`/`publishOn` khắp nơi.

## 15. HTTP/1.1, HTTP/2 và HTTP/3: giới hạn nào là vật lý, giới hạn nào là API?

### 15.1. HTTP/1.1

- Một response stream có thể đi qua chunked transfer khi không có Content-Length; container quyết định framing.
- TCP điều khiển truyền **bytes**, không biết `Flux<Item>` hoặc `ServerSentEvent<T>`.
- Một HTTP/1.1 connection thường có hạn chế multiplexing so với HTTP/2; nhiều SSE connections có thể tốn browser/network resources.
- HTTP clients/proxies có thể buffer, decompress và parse theo cách riêng.
- “`onNext(item)` được gọi” không khẳng định ngay lúc đó client đã nhận đầy đủ item.

### 15.2. HTTP/2

HTTP/2 có **stream-level và connection-level flow control** bằng windows và `WINDOW_UPDATE` cho DATA frames. Đây là cơ chế byte-oriented, độc lập với `Subscription.request(n)` của Reactor. HTTP/2 còn có multiplexing: một connection có nhiều streams; một stream chậm không đồng nghĩa publisher khác có cùng demand pattern.

Mối liên hệ có thể gián tiếp: HTTP/2 send window nhỏ → outbound write bị hạn chế → adapter cần hạn chế đọc thêm data buffers → demand trong reactive pipeline được điều tiết. Nhưng không phải công thức `request(n) = HTTP2 WINDOW_UPDATE(n)`.

### 15.3. HTTP/3/QUIC

HTTP/3 dựa trên QUIC, có stream/connection flow control theo bytes và cơ chế truyền khác TCP, nhưng nguyên lý “giao thức mạng không gửi Reactor `request(n)`” vẫn không đổi. Với WebFlux chạy sau HTTP/3 terminating reverse proxy, HTTP/3 có thể chỉ tồn tại giữa browser và gateway, còn origin dùng HTTP/1.1 hoặc HTTP/2; hãy đo actual deployment.

### 15.4. Các loại giới hạn khác

- **Content-Length**: kích thước body được tuyên bố/biết trước, không phải Reactive Streams demand.
- **Max request size/codec max buffer**: ngưỡng bảo vệ payload/memory, không phải số item consumer sẽ request.
- **Rate limit**: requests hoặc tokens/bytes theo thời gian/user, không phải operator `limitRate`.
- **Timeout**: thời gian phản hồi/idle, không trực tiếp kiểm soát queue bytes.
- **Concurrency cap**: số tasks/connections/subscribers được phép, khác với item prefetch.
- **Backpressure**: nhu cầu flow-controlled giữa các reactive stages, có thể làm chậm upstream phù hợp.

## 16. Proxy/CDN có thể che đi slow consumer

Giả sử client thực đọc chậm nhưng NGINX có response buffering:

```text
WebFlux origin  -->  Proxy buffer RAM/disk  -->  Browser slow
    nhanh                   tích tụ                  chậm

Origin đã gửi xong rất nhanh
=> demand ở origin KHÔNG nhất thiết phản ánh browser đang xử lý chậm
```

Điều này có thể đúng ngay cả khi application Reactor pipeline viết rất “đúng backpressure”. Những tầng cần kiểm tra:

- Reverse proxy `proxy_buffering` / `proxy_request_buffering`, response compression.
- CDN edge caching, transformation, WAF buffering.
- GZIP/Brotli chunk flush policy.
- HTTP/2 → HTTP/1.1 gateway translation.
- Keep-alive, read/write/idle timeout và maximum connections.
- Browser EventSource/client buffering/console output.
- Network latency, packet loss và TCP flow control.

Với streaming latency-sensitive routes, cấu hình proxy phù hợp, ví dụ NGINX có thể tôn trọng:

```http
X-Accel-Buffering: no
Cache-Control: no-cache
Content-Type: text/event-stream
```

`X-Accel-Buffering` là NGINX-specific hint, **không phải bảo đảm tiêu chuẩn HTTP**; proxy phải được cấu hình để honor. Không tắt buffering toàn bộ website vô điều kiện.

Test cả **localhost**, **embedded Reactor Netty**, **gateway staging** và nếu cần **browser thật**; đo time-to-first-event, queue growth và disconnect detection.

## 17. Cancellation, timeout, errors và việc client đóng kết nối

Một consumer có thể dừng bằng `cancel()` (như `take(3)` hoặc StepVerifier cancel). Client HTTP có thể disconnect giữa stream. Nhưng cancel propagation qua codec, adapter, scheduler và driver **không luôn tức thời**.

```java
Flux<Data> stream = source
        .doOnCancel(() ->
                metrics.incrementCancelled())
        .doFinally(signal ->
                metrics.recordTerminal(signal));
```

Đây là fragment. `doFinally` nhận `SignalType` (gồm complete, error hoặc cancel) và phù hợp để theo dõi terminal lifecycle; callback không thay transaction cancellation ở nguồn ngoài.

Khi nguồn là database cursor, file reader hoặc broker subscription, cancellation nên dẫn tới cleanup:

- Đóng/release DB cursor, reactive connection hoặc pooled resource.
- Hủy timer/subscription/producer work không cần thiết.
- Dọn per-client buffers và queue.
- Giới hạn thời gian network requests và external service calls.
- Gắn cancellation với user disconnect ở adapter đúng chỗ.
- Không giữ `DataBuffer` pooled vượt lifecycle nếu chưa retain/release đúng.

### 17.1. Lỗi sau khi response đã bắt đầu

Nếu WebFlux đã gửi header `HTTP 200` và vài NDJSON/SSE items, sau đó upstream `onError`, server có thể **không đổi** response thành một `500 application/problem+json` hợp lệ. Client có thể nhận incomplete stream hoặc connection reset.

Giải pháp phụ thuộc protocol: phát SSE event `error` trong phạm vi stream còn writable, cung cấp event IDs/replay, đặt checksum/end marker cho batch data, hoặc cho client retry/idempotency.

**`onErrorResume` không tự biến mọi stream error thành HTTP 500**; nó chỉ thay Publisher bằng một fallback theo logic Reactor. Nếu fallback object không đúng schema/framing, client có thể đọc sai; nếu lỗi bảo mật, không trả stack trace.

### 17.2. Timeout không thay được overload control

`timeout(Duration)` có thể chấm dứt flow khi thiếu tín hiệu. Nhưng request concurrency, pending upstream calls và buffer memory vẫn cần điều chỉnh. Khi trả timeout/retry, cần tránh tạo **retry storm**.

Không dùng `retry()` vô hạn cho database, gateway hoặc nguồn hot mà không có backoff, jitter, attempt limit và idempotency policy.

## 18. Backpressure phía client dùng WebClient

WebClient trong WebFlux cũng là reactive HTTP client; nếu response body là NDJSON/SSE có thể decode thành `Flux<T>`:

```java
WebClient client = WebClient.create(
        "https://api.example.org");

Flux<Item> items = client.get()
        .uri("/api/items.ndjson")
        .accept(MediaType.APPLICATION_NDJSON)
        .retrieve()
        .bodyToFlux(Item.class)
        .limitRate(8);
```

Đây là **fragment**, cần imports WebClient/Reactor/MediaType và `Item` DTO. Chưa subscribe nên phần lớn pipeline chưa phát HTTP request. `limitRate(8)` điều chỉnh demand Reactor phía client; nó **không gửi HTTP message `request(8)` tới origin**. Network/backpressure bridge có thể giảm lượng bytes được đọc/đệm, nhưng không có contract “đúng tám network objects in-flight”.

Nếu client gọi:

```java
List<Item> all = client.get()
        .uri("/api/items.ndjson")
        .retrieve()
        .bodyToFlux(Item.class)
        .collectList()
        .block();
```

thì sẽ **thu toàn bộ kết quả** vào list (và block thread). Với nguồn dài hoặc vô hạn, đây là anti-pattern gây memory pressure và có thể không bao giờ hoàn tất. Trong WebFlux request thread/event-loop, dùng reactive composition thay vì `block()`.

### WebClient filters, timeouts và connection pooling

- Giới hạn connection pool, pending acquires, response timeout và max in-memory codec buffering.
- Dùng `flatMap(..., concurrency)` để giới hạn số HTTP requests đồng thời.
- Có circuit breaker/bulkhead phù hợp, không chỉ `limitRate`.
- Với retry, kiểm soát idempotency và thundering herd.
- Nếu nhận stream rất dài, đừng dùng `bodyToMono(byte[].class)` để aggregate toàn bộ.
- Đảm bảo response body bị consume hoặc hủy đúng để connection có thể được giải phóng/reuse.

## 19. Đo backpressure đúng trong production

Các chỉ số cần quan sát:

| Metric | Tại sao hữu ích |
| --- | --- |
| Active connections/streams | Phát hiện kết nối kéo dài hoặc leak |
| Inbound/outbound bytes/s | Tốc độ thực của HTTP/network |
| Queue depth **và bytes** | Đo backlog; bytes quan trọng vì item sizes khác nhau |
| Oldest queued item age | Phát hiện dữ liệu đến client đã quá cũ |
| Time-to-first-byte / first-event | Trực tiếp đo streaming UX |
| End-to-end event latency | So sánh lúc tạo và lúc client nhận |
| HTTP write/flush stalls | Phát hiện chậm từ socket/gateway |
| Dropped/latest/overflow count | Biết loss policy đang xảy ra |
| Subscription cancel/error/complete | Phân biệt dừng hợp lệ và sự cố |
| Reactor scheduler pending tasks | Phát hiện scheduling bottleneck |
| DB/outbound pool utilization | Đo upstream có thực sự giảm tải hay không |
| Gateway/edge buffer occupancy | Xác định chỗ nguồn và browser bị tách rời |
| HTTP client consumption latency | Không suy ra UX chỉ từ producer send timestamps |

`doOnRequest` hữu ích cho debug trong giới hạn nhưng **request count không trực tiếp là throughput network**. Tránh gắn từng event payload/PII vào metrics tags; cardinality cao làm observability system bùng nổ.

### 19.1. Thiết kế load test slow consumers

Một test nghiêm túc nên:

1. Tạo 100/1000 concurrent clients trong môi trường kiểm soát.
2. Dùng HTTP client đọc **từng phần**, có client đọc chậm hoặc tạm ngưng đọc.
3. Quan sát heap, direct memory, Netty pending writes, queue depth, GC và CPU.
4. Đo origin so với gateway và client timestamps.
5. Test source cold và hot riêng.
6. Thử buffer policy, drop/latest/error, subscriber limits.
7. Test client disconnect sớm, server cancel và cleanup.
8. Thử cấu hình HTTP/1.1 vs HTTP/2 qua cùng topology.
9. Tăng payload size từng item, không chỉ số item.
10. Kiểm tra retry storm khi overload/circuit breaker hoạt động.

Không dùng `Thread.sleep` trên WebFlux event loop để giả lập slow consumers. Test harness phía client có thể đọc chậm với thread có giới hạn hoặc dùng virtual-time tests cho riêng Reactor logic.

## 20. Những hiểu lầm thường gặp

| Nhận định sai | Điều đúng |
| --- | --- |
| “`Flux` nghĩa là mọi response tự stream ngay.” | Phụ thuộc encoder, media type, flush và proxy |
| “`request(3)` gửi một HTTP command tới browser.” | Signal nội bộ Reactive Streams, không phải wire HTTP |
| “`request(3)` tương ứng 3 KB.” | Theo số **items**, kích thước mỗi item khác nhau |
| “`limitRate(10)` giới hạn 10 request/giây.” | Chia demand thành batches, không phải rate limiter |
| “`take(10)` và `limitRate(10)` giống nhau.” | Take giới hạn tổng items, limitRate chia nhu cầu |
| “Downstream request 1 nên upstream cũng luôn request 1.” | Buffer/filter/flatMap/publishOn/prefetch thay đổi demand |
| “`onBackpressureBuffer()` giải quyết overload mãi mãi.” | Buffer unbounded có thể gây OOM |
| “`onBackpressureDrop` luôn vô hại.” | Không hợp dữ liệu phải giao đủ như thanh toán |
| “Source hot tự chậm lại khi subscriber không request.” | Nguồn ngoài có thể tiếp tục phát; cần policy |
| “SSE EventSource có `request(n)`.” | Browser EventSource không implement Reactive Streams |
| “HTTP/2 WINDOW_UPDATE là request(n).” | Windows theo bytes, không theo objects |
| “TCP chậm sẽ làm origin chậm ngay.” | Proxy/CDN có thể buffer và decouple |
| “`subscribe()` lúc nào cũng đòi 1 item.” | Nhiều overload request `Long.MAX_VALUE` |
| “Reactive = tự động non-blocking dù gọi JDBC.” | Blocking library vẫn block thread |
| “`collectList()` cho stream vô hạn là an toàn.” | Có thể tăng RAM vô hạn và không hoàn tất |
| “Controller trả Flux sẽ chống DDoS.” | Cần auth, quotas, concurrency/request-rate limits |
| “Client đóng tab thì doOnCancel ngay.” | Phụ thuộc write/heartbeat/network detection |
| “onError lúc stream đã bắt đầu sẽ thay status 200.” | Response có thể committed, cần protocol-level handling |

## 21. Checklist khi phát triển WebFlux streaming

- [ ] Xác định chính xác đơn vị demand: `T`, `List<T>` hay `DataBuffer`.
- [ ] Pipeline nguồn thật sự demand-aware; nếu hot source, có overflow policy.
- [ ] Không có buffer/collectList unbounded trên stream dài.
- [ ] Các operator `flatMap/publishOn` có concurrency/prefetch phù hợp.
- [ ] Không dùng `limitRate` như API request rate limiter.
- [ ] Không có blocking I/O/`block()` trên Reactor Netty event loops.
- [ ] Media type `application/x-ndjson` hoặc `text/event-stream` được chọn đúng contract.
- [ ] Client parse đúng framing và hỗ trợ partial stream/cancel.
- [ ] HTTP codec buffer limits, request sizes, queue bytes được kiểm soát.
- [ ] Netty connection pools và outbound HTTP concurrency có upper bounds.
- [ ] Hot SSE có heartbeat, Last-Event-ID replay nếu cần, connection/tenant limits.
- [ ] `doOnCancel`/`doFinally` cleanup DB cursors, subscriptions, timers, pooled buffers.
- [ ] Lỗi sau committed response có xử lý retry/protocol rõ.
- [ ] Proxy/CDN/ingress được kiểm tra về buffering và timeouts.
- [ ] Unit StepVerifier kiểm tra demand từ 0; HTTP WebTestClient kiểm tra framing.
- [ ] Có load test slow consumers cùng direct-memory/queue metrics.
- [ ] Không đưa tokens, payloads hoặc user IDs tùy ý vào logs/metrics tags.
- [ ] Khi nâng Spring/Boot/Reactor, kiểm tra operator prefetch defaults, codecs và server adapter behavior.

## 22. Bài tập tự đánh giá

1. `request(5)` có nghĩa yêu cầu 5 bytes, 5 HTTP chunks hay 5 Reactor elements? Nếu element là `List<Order>` thì sao?
2. Tạo `StepVerifier.create(Flux.range(1,5),0)` rồi `thenRequest(2)`. Vì sao initial request 0 trong test **khác** gọi `Subscription.request(0)`?
3. Đặt `doOnRequest` trước và sau `limitRate(4)`. Dự đoán logs khác nhau khi downstream request 100.
4. So sánh `limitRate(10)` với `limitRate(10,0)`, đặc biệt low-tide replenishment.
5. Với `Flux.range(1,100).buffer(10)`, subscriber request 2 có thể làm source nhận demand bao nhiêu?
6. Vì sao `flatMap(mapper,8,16)` không đặt được hard byte memory limit cho mọi request?
7. Trình bày cách `onBackpressureLatest` làm mất intermediate progress events nhưng vẫn có thể đúng với dashboard percent.
8. Tại sao không dùng `onBackpressureDrop` cho order payments hoặc security audit events?
9. Giả sử upstream publish từ Kafka liên tục 50k msg/s, client SSE nhận 100/s. Bạn chọn pause, bounded buffer, durable broker hay disconnect? Giải thích semantics mất dữ liệu.
10. Cùng `Flux<Item>` nhưng `application/json` và `application/x-ndjson` có thể khác hành vi phát dữ liệu như thế nào?
11. Browser `EventSource` có thể gửi `request(2)` không? Nếu không, backpressure HTTP còn hoạt động bằng các cơ chế nào?
12. HTTP/2 stream window và TCP receive window dùng đơn vị gì? Vì sao không tương đương Reactor element demand?
13. NGINX đọc origin nhanh nhưng buffer response cho browser chậm. Producer `doOnRequest` ở origin còn phản ánh chính xác browser latency không?
14. Nếu `Flux<DataBuffer>` có 1 item 50 MB, `request(1)` có an toàn về memory không? Thiết kế giới hạn kích thước chunk/buffer.
15. Thêm `onBackpressureBuffer(100)` cho hot events. Khi buffer đầy thì policy nào có thể áp? Cần quan sát metric nào?
16. Thay DB access trong WebFlux bằng blocking JDBC trực tiếp. Nó ảnh hưởng Reactor Netty event loops ra sao? `boundedElastic` khắc phục được những gì và không khắc phục được gì?
17. Với `WebClient.bodyToFlux(Item.class)`, nếu đổi thành `collectList().block()` cho infinite stream, lỗi thiết kế là gì?
18. Client ngắt sau 2/5 NDJSON records; server đã trả status 200. Làm sao client phát hiện partial result và retry an toàn?
19. Thiết kế test `StepVerifier` đo demand trực tiếp, test `WebTestClient` kiểm tra SSE fields và load test qua proxy đo first-event latency; phân biệt ba mục tiêu.
20. Trong rolling deployment, browser reconnect SSE với `Last-Event-ID` sang node khác. Vì sao cần shared durable event log nếu không được mất events?
21. Đưa một source hot vào `publishOn` với prefetch 32; nếu client không request thì buffers/overflow có thể hình thành ở những đâu?
22. Xây rate-limit HTTP theo tenant và overflow policy cho stream data; giải thích vì sao không thể chỉ dùng một `limitRate(10)`.

## 23. Tài liệu chính thức

- [Reactive Streams Specification — JVM](https://www.reactive-streams.org/): nguyên tắc `Publisher`/`Subscriber`/`Subscription` và `request(n)` contract.
- [Project Reactor Reference — Backpressure and Ways to Reshape Requests](https://projectreactor.io/docs/core/release/reference/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html#_on_backpressure_and_ways_to_reshape_requests): demand, BaseSubscriber, prefetch, limitRate.
- [Project Reactor — Flux Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html): `limitRate`, `flatMap`, `publishOn`, `onBackpressureBuffer/Drop/Latest/Error`.
- [Project Reactor — StepVerifier](https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html): initial request, thenRequest, deterministic signal testing.
- [Spring Framework — WebFlux Reactive Core](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html): `HttpHandler`, codecs, non-blocking I/O, Reactive Streams backpressure và `DataBuffer`.
- [Spring Framework — WebFlux Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods.html): reactive controller return values.
- [Spring Framework — WebFlux Reactive Libraries](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-libraries.html): reactive adaptation, cancellation và request signals.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): test HTTP streaming/response bodies.
- [Spring Boot 3.5 — Embedded Web Servers](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html): reactive server mặc định Reactor Netty.
- [Spring Boot 3.5 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html): RANDOM_PORT, WebTestClient, WebFlux test configuration.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html): `spring.codec.max-in-memory-size`.
- [RFC 9113 — HTTP/2](https://www.rfc-editor.org/rfc/rfc9113.html): stream/connection byte-oriented flow control.
- [RFC 9293 — Transmission Control Protocol](https://www.rfc-editor.org/rfc/rfc9293.html): TCP receiver windows và byte stream semantics.
- [WHATWG HTML — Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html): SSE/EventSource protocol/reconnect.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-foundations-006">map, flatMap, kết hợp kết quả và lựa chọn mức đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# map, flatMap, kết hợp kết quả và lựa chọn mức đồng thời trong Spring WebFlux

> **Phạm vi:** Reactor Core (`Mono`, `Flux`, Reactive Streams), Spring WebFlux trên Spring Framework 6.2.x / Spring Boot 3.5.x với Java 17+. Trọng tâm gồm: **biến đổi dữ liệu bằng map/flatMap**, **ghép nhiều publishers bằng zip/merge/concat/combineLatest**, và **chọn concurrency/prefetch/backpressure/order phù hợp** khi gọi WebClient hoặc reactive repository. Ví dụ chạy với WebFlux server không block HTTP event loop, có cURL và WebTestClient/StepVerifier. Nếu đang dùng Spring Boot 4.x/Spring Framework 7.x, phải đối chiếu starter/test package tương ứng trước khi sao chép dependency.

## 1. Tình huống thực tế và sơ đồ

Một hệ thống nhận danh sách product IDs, cần gọi dịch vụ catalog rồi trả JSON. Khi tải một profile tổng hợp, cần kết hợp account và settings từ hai services độc lập.

```text
GET /api/items
    |
Flux<Integer> ids
    |
    + map(id -> key): giá trị -> giá trị mới ngay
    |
    + flatMap(id -> Mono<Item>, concurrency):
    |                 |
    |           nhiều I/O in-flight
    |                 |
    |         Flux<Item> (có thể đảo thứ tự)
    |
    + flatMapSequential(id -> Mono<Item>, n):
    |                 |
    |         Flux<Item> giữ thứ tự đầu vào
    |
    + concatMap(id -> Mono<Item>):
                      |
             mỗi inner chạy tuần tự

GET /api/profile/{id}
    |
    +--> Mono<Account> -------+
    |                         +--> Mono.zip(...) --> Mono<Profile>
    +--> Mono<Settings> ------+
```

**Câu hỏi phải giải quyết trước khi chọn operator:** mapper trả giá trị thuần hay Publisher? Các requests độc lập hay phụ thuộc nhau? Có phải giữ thứ tự **output**, hay cả thứ tự **side effects**? Downstream chịu được bao nhiêu request đồng thời? Có thể mất một item hay mọi item đều bắt buộc?

## 2. Nền tảng: Mono, Flux và tín hiệu

`Mono<T>` phát 0 hoặc 1 phần tử rồi hoàn tất, hoặc kết thúc bằng lỗi. `Flux<T>` phát 0 đến N phần tử (có thể vô hạn), hoàn tất hoặc lỗi. Một reactive pipeline thường **lazy**; operators như `map` và `flatMap` xây dựng mô tả tính toán, chưa thực thi cho đến khi có subscription. Spring WebFlux subscribe Publisher do controller trả trong response lifecycle.

Reactive Streams có các tín hiệu/khái niệm:
- `onSubscribe`: trao Subscription;
- `request(n)`: consumer yêu cầu số phần tử;
- `onNext`: một phần tử, không được null;
- `onComplete`: hoàn tất thành công;
- `onError`: lỗi terminal;
- `cancel`: hủy subscription.

Sau terminal `onComplete` hoặc `onError`, publisher không tiếp tục phát các phần tử bình thường. Cancellation từ browser/disconnected client có thể lan ngược upstream, tùy operator/source/HTTP connector.

```java
Flux<Integer> source = Flux.just(1, 2, 3);
Flux<Integer> output = source.map(x -> x * 10);
// Chưa gọi subscribe: các tác vụ thường chưa chạy.
```

Trong HTTP controller WebFlux: **return `Flux` hoặc `Mono`** để Spring điều phối subscription, không tự gọi `.subscribe()` ở handler và trả `null`. Trong unit tests có thể gọi `StepVerifier.create(output)` để kiểm tra tín hiệu mà không viết `block()`.

### 2.1. Cold vs hot source

`Flux.range(...)` và `Mono.defer(...)` thường là cold: mỗi subscription có thể phát lại hoặc thực hiện lại logic. Một hot stream có thể chia sẻ nguồn sự kiện đã và đang chạy. Concurrency trên `flatMap` giới hạn **số inner subscriptions của instance operator**, không tự hạn chế toàn bộ event rate của hot source dùng chung, connection pool của service hoặc tổng inbound HTTP requests.

## 3. map: một phần tử đầu vào thành một giá trị

`map(Function<T,R>)` thực hiện chuyển đổi **đồng bộ** khi một `onNext` tới:

```java
Flux<String> labels = Flux.just(1, 2, 3)
        .map(id -> "item-" + (id * 10));
```

Giá trị của `labels.collectList()` khi được subscribe tương đương:

```text
[item-10, item-20, item-30]
```

`map` thích hợp cho DTO mapping, chuẩn hóa string, tính toán giá trị thuần. Nó không tự mở thread hay biến logic blocking thành non-blocking. Mapper không được trả `null`; trả null là lỗi reactive contract.

**Sai trường hợp mapper trả một Mono:**

```java
Mono<Mono<User>> nested =
        Mono.just(42).map(id -> fetchUserReactive(id));
```

Nếu `fetchUserReactive(int)` trả `Mono<User>`, bên ngoài vẫn là Mono chứa một Mono. Muốn nhận `Mono<User>`, dùng `flatMap`:

```java
Mono<User> flattened =
        Mono.just(42).flatMap(id -> fetchUserReactive(id));
```

Hai đoạn này là snippets, cần method `fetchUserReactive` và class `User` được định nghĩa trước.

## 4. flatMap: tạo Publisher mới rồi làm phẳng

`Mono.flatMap(Function<T,Mono<R>>)` hợp với một request cần kết quả bất đồng bộ từ một giá trị. `Flux.flatMap(Function<T,Publisher<R>>)` hợp với nhiều items, có thể tạo nhiều inner publishers đang active.

```java
Mono<User> user = userIdMono
        .flatMap(id -> userRepository.findById(id));

Flux<Product> products = Flux.fromIterable(productIds)
        .flatMap(id -> webClient.get()
                .uri("/products/{id}", id)
                .retrieve()
                .bodyToMono(Product.class), 4);
```

Snippets cần `userIdMono`, `userRepository`, `productIds`, `webClient`, DTO. Ưu điểm là nhiều network calls non-blocking có thể **overlap latency**; nhược điểm: **không bảo đảm thứ tự output theo IDs**.

### 4.1. flatMap không tự tạo thread

Ví dụ:

```java
Flux<Integer> a = Flux.range(1, 4)
        .flatMap(i -> Mono.just(i * 10));

Flux<Integer> b = Flux.range(1, 4)
        .flatMap(i -> Mono.delay(Duration.ofMillis(100))
                .map(ignore -> i * 10), 2);
```

Ví dụ A phần lớn vẫn chạy đồng bộ trong subscription; B có timer async. `flatMap` là toán tử **flatten/merge**, không phải lệnh `new Thread(...)`.

Với nguồn blocking JDBC:

```java
// Anti-pattern trên WebFlux event loop.
return Flux.fromIterable(ids)
        .flatMap(id -> Mono.fromCallable(
                () -> jdbcRepository.findById(id)));
```

Nếu subscribe vào blocking Callable trên luồng hiện tại, event loop có thể vẫn bị block. Bridge tạm thời:

```java
return Flux.fromIterable(ids)
        .flatMap(id -> Mono.fromCallable(
                        () -> jdbcRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic()), 4);
```

Đoạn sau chuyển blocking call sang boundedElastic, **không biến JDBC thành reactive database driver**. Cần giới hạn số tasks, thread/queue, DB pool, và cân nhắc Spring MVC hoặc R2DBC tùy kiến trúc.

### 4.2. flatMapMany cho Mono đầu vào

Nếu từ một account ta lấy nhiều orders:

```java
Flux<Order> orders = accountMono
        .flatMapMany(account -> orderRepository.findByAccount(account.id()));
```

Ở đây repository trả `Flux<Order>`; `Mono.flatMapMany` chuyển từ 0..1 sang 0..N. Nếu chỉ `map(account -> Flux<Order>)`, kết quả là `Mono<Flux<Order>>`, không phải chuỗi orders.

### 4.3. flatMap khác map khi dữ liệu trống

Với `Mono.empty()`, không có `onNext` để gọi mapper của `map` hay `flatMap`. Muốn xử lý absence có chủ đích, dùng `switchIfEmpty(...)` hoặc `defaultIfEmpty(...)` nếu value hợp lệ, không mặc định kết luận “mapper bị lỗi”.

## 5. flatMap, flatMapSequential, concatMap: chọn theo thứ tự

```java
Flux<Item> fastest = ids.flatMap(this::fetch, 3);
Flux<Item> ordered = ids.flatMapSequential(this::fetch, 3);
Flux<Item> serial = ids.concatMap(this::fetch);
```

| Tiêu chí | flatMap(mapper,3) | flatMapSequential(mapper,3) | concatMap(mapper) |
| --- | --- | --- | --- |
| Có thể subscribe nhiều inner sớm | Có | Có | Không, các inner nối tuần tự |
| Tối đa inner active theo ví dụ | 3 | 3 | 1 |
| Output theo thứ tự input | Không bảo đảm | **Có** | **Có** |
| Interleave giữa giá trị các inners | Có | Không trong output theo source order | Không |
| Buffer khi kết quả sau đến trước | Không cần reorder theo source | Có thể phải queue để chờ inner trước | Không overlap async inner |
| Khi nào dùng | I/O độc lập, ưu tiên throughput | Giữ thứ tự output và overlap I/O | Side effects phải lần lượt |

**Điểm dễ nhầm:** `flatMapSequential` đảm bảo thứ tự **phát ra kết quả**, chứ các inner operations/side effects vẫn có thể diễn ra **đồng thời**. Nếu thao tác write A phải hoàn thành trước khi bắt đầu write B trên cùng aggregate, hãy dùng `concatMap` hoặc reactive sequencing thích hợp; vẫn cần DB transaction/versioning để bảo vệ tranh chấp từ **nhiều HTTP requests khác nhau**.

### 5.1. Ví dụ head-of-line

Input IDs [1,2,3], inner ID=1 tốn 300ms, ID=2 80ms, ID=3 40ms:
- `flatMap` có thể phát 3,2,1.
- `flatMapSequential` có thể hoàn thành 3,2 trước nhưng **giữ trong queue** rồi phát 1,2,3.
- `concatMap` đợi 1 hoàn tất mới đăng ký 2, sau đó mới 3.

Thời điểm/phân phối cụ thể còn phụ thuộc scheduler và nguồn. Test không nên assert một thứ tự fixed cho `flatMap`. Với `flatMapSequential` ở concurrency cao và item lớn, reorder queue làm tăng memory và time-to-first-byte khi inner đầu chậm.

### 5.2. Vì sao không sort sau flatMap?

`flatMap(...).sort(comparator)` hoặc `collectList().map(list -> sort(...))` phải gom dữ liệu đến khi source hoàn tất, không thích hợp với stream vô hạn và tạo memory theo kích thước dữ liệu. `flatMapSequential` dùng source-order semantics thay vì sort toàn bộ, dù vẫn có buffer cho inner đến sớm.

### 5.3. map giữ order không?

`Flux.map` thực hiện chuyển đổi lần lượt các `onNext` do upstream phát, không tự reorder nguồn. Nếu upstream đã bị reorder do `flatMap` trước đó, `map` **không khôi phục** thứ tự ban đầu.

## 6. concurrency: số inner đang được subscribe, không phải số threads

`Flux.flatMap(mapper, concurrency)` giới hạn số inner publishers đang active của pipeline đó. Nó không tự cấp thread, không giới hạn mọi callers trên server, và không tương đương HTTP connection pool max size.

Ví dụ:

```java
Flux<Product> result = Flux.fromIterable(ids)
        .flatMap(id -> client.get()
                .uri("/products/{id}", id)
                .retrieve()
                .bodyToMono(Product.class), 8);
```

Khi mỗi inner là `Mono<Product>` của WebClient, có thể có tối đa khoảng 8 HTTP exchanges đang in-flight do operator tại một subscription, tùy connector/pooling/response/retry. Nhưng 100 HTTP requests cùng lúc, mỗi request flatMap concurrency 8, có thể làm phát sinh **tới khoảng 800 inner operations** đang active (upper-bound trực giác, không phải 800 connections bảo đảm).

**Cần hai lớp hạn chế:**
- local per-pipeline concurrency (flatMap argument);
- global controls (client pool/bulkhead/rate limit, per-tenant quotas, ingress concurrency, downstream capacity).

### 6.1. Chọn mức concurrency như thế nào?

Không có một con số tốt cho mọi workload. Với 1 request fan-out 30 IDs, cần khảo sát:
- mỗi remote call latency trung bình/p95/p99;
- downstream RPS/concurrent request quota;
- HTTP client max connections/pending acquire;
- memory per in-flight request, payload size, JSON decoding;
- retries/429/503/timeout và bão hòa DB/CPU;
- số inbound requests hoạt động đồng thời.

Bắt đầu nhỏ (ví dụ 2–8 **chỉ là giá trị thí nghiệm**, không phải chuẩn) rồi tăng dần dưới load test, quan sát throughput, tail latency, memory và error rate. Khi downstream hoặc pool đã đạt ngưỡng, tăng concurrency thường chỉ tăng queue, timeout, retry storm.

### 6.2. prefetch khác concurrency

Một overload:

```java
Flux<R> output = source.flatMap(
        value -> mapperReturningFlux(value),
        4,   // số inner publishers đang active
        16   // prefetch: số item request-ahead cho mỗi inner
);
```

Snippet chưa độc lập; `source` và `mapperReturningFlux` là placeholders có ý nghĩa minh họa. `prefetch` điều chỉnh demand/queueing, **không** cấp 16 threads, và không có nghĩa luôn lưu đúng 16 items trong RAM.

Ước lượng kiểm tra áp lực trên inner multi-value streams: `concurrency × prefetch × average item size`. Đây chỉ là xấp xỉ, chưa gồm outer buffers, HTTP response encoder, Reactor/Netty buffers, object overhead và caches. Với inner chỉ có một giá trị như Mono, prefetch kém quan trọng hơn trường hợp mỗi inner là Flux dài.

### 6.3. Bảo vệ stream vô hạn và inner treo

Nếu một inner không bao giờ complete và concurrency slots đã kín, phần tử đầu vào tiếp theo có thể không được xử lý. Cần `timeout(Duration)`, client timeout, circuit-breaker/bulkhead khi phù hợp; với infinite/hot publishers phải xem semantics cancellation/buffering, không dùng `flatMap(concurrency=1000)` tùy hứng.

## 7. Kết hợp kết quả độc lập bằng Mono.zip

Một endpoint profile cần account và settings:

```java
Mono<Account> account = accountService.find(id);
Mono<Settings> settings = settingsService.find(id);

Mono<Profile> profile = Mono.zip(
        account, settings,
        (a, s) -> new Profile(a.id(), a.name(), s.theme()));
```

`Mono.zip` chỉ phát một kết quả khi **mỗi source đều có một value**. Nếu hai sources là asynchronous và độc lập, chúng có thể tiến triển chồng lấp latency. Nếu một nguồn lỗi, zip thường phát lỗi rồi hủy các nguồn khác còn chạy. Nếu một nguồn hoàn tất rỗng, zip không thể áp dụng combinator.

**Không hiểu sai:** zip hai remote calls không tạo distributed transaction, không bảo đảm snapshot consistency giữa hai databases và cũng không chứng minh người dùng có quyền đọc kết quả. Authorization trên từng nguồn/data scope vẫn cần được đảm bảo.

### 7.1. zipWith và tuple

```java
Mono<Tuple2<Account, Settings>> pair = accountMono.zipWith(settingsMono);

Mono<Profile> combined = accountMono.zipWith(
        settingsMono,
        (a, s) -> new Profile(a.id(), a.name(), s.theme()));
```

Đây là snippets, cần import `reactor.util.function.Tuple2` cùng DTO. Dùng `zipWith` khi một Mono đã là vế trái; semantics empty/error giống zip.

### 7.2. zipWhen cho dependent request

```java
Mono<Profile> profile = accountService.find(id)
        .zipWhen(account ->
                settingsService.findByAccount(account.id()))
        .map(pair -> new Profile(
                pair.getT1().id(),
                pair.getT1().name(),
                pair.getT2().theme()));
```

`zipWhen` tạo Publisher bên phải **sau khi có giá trị account**, do đó nó không tương đương “hai nguồn độc lập bắt đầu cùng lúc”. Với dependency chain đơn giản, cũng có thể dùng:

```java
Mono<Profile> profile = accountService.find(id)
        .flatMap(account -> settingsService.findByAccount(account.id())
                .map(settings -> new Profile(
                        account.id(), account.name(), settings.theme())));
```

Nếu không cần tuple, `flatMap`/`map` lồng nhau thường rõ ràng.

### 7.3. Zip với Mono.empty hoặc Mono<Void>

```java
Mono<User> user = userRepository.findById(id);
Mono<Settings> absent = Mono.empty();

Mono<Tuple2<User, Settings>> zipped = Mono.zip(user, absent);
```

`zipped` thường hoàn tất rỗng, không phát tuple. `Mono<Void>` cũng là “không có onNext” cho zip; không được dùng `Mono.zip(writeAuditReturningVoid, getUser)` và kỳ vọng luôn nhận cặp. Với trường hợp **chờ hoàn tất các tác vụ không trả giá trị**, dùng `Mono.when(publisherA,publisherB)` hoặc sequencing `then(...)`, đúng theo yêu cầu lỗi/transaction.

Nếu không tìm thấy user thì cần 404:

```java
Mono<User> user = userRepository.findById(id)
        .switchIfEmpty(Mono.error(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found")));
```

Nếu settings là tùy chọn và có default hợp lệ:

```java
Mono<Settings> settings = settingsService.find(id)
        .defaultIfEmpty(new Settings("light"));
```

Không gán default mở rộng quyền truy cập khi permission service rỗng/lỗi. Reactor FAQ cũng cảnh báo với zip và nguồn rỗng, một vài nguồn có thể **không được subscribe** trong các trường hợp cụ thể; không gắn side effects bắt buộc vào giả định “zip subscribe hết tất cả” mà không có protocol đảm bảo.

## 8. Flux.zip, merge, concat và combineLatest

### 8.1. Flux.zip: ghép theo chỉ số

```java
Flux<String> zipped = Flux.zip(
        Flux.just("A", "B", "C"),
        Flux.just(1, 2),
        (letter, number) -> letter + number);
```

Output khi subscribe:

```text
A1
B2
```

Với hai Flux hữu hạn, zip ghép 1:1 từng vị trí và kết thúc khi không thể tạo thêm cặp vì một nguồn đã cạn. Phần `C` không có số để ghép nên không xuất hiện. Nếu một nguồn phát nhanh hơn, zip có thể queue/buffer để chờ nguồn còn lại.

### 8.2. Flux.merge: trộn theo thời điểm phát

```java
Flux<String> first = Flux.just("A1", "A2")
        .delayElements(Duration.ofMillis(100));
Flux<String> second = Flux.just("B1", "B2")
        .delayElements(Duration.ofMillis(60));
Flux<String> merged = Flux.merge(first, second);
```

Cả hai sources được subscribe sớm; output có thể interleave. Thứ tự ví dụ `B1,A1,B2,A2` chỉ là **một khả năng**. Không assert một thứ tự duy nhất trên những nguồn async có timing không được kiểm soát.

Dùng merge cho event streams độc lập khi thứ tự giữa sources không quan trọng, chẳng hạn hợp nhất notification topics.

### 8.3. Flux.concat: source A rồi source B

```java
Flux<String> out = Flux.concat(
        Flux.just("A1", "A2"),
        Flux.just("B1", "B2"));
```

Output deterministic:

```text
A1
A2
B1
B2
```

Concat đợi A hoàn tất trước khi xử lý B. Nếu A **vô hạn**, B không bao giờ được subscribe theo concat thông thường.

### 8.4. mergeSequential: subscribe sớm nhưng giữ source order

`Flux.mergeSequential(...)` có thể đăng ký nhiều sources sớm rồi phát theo thứ tự source; thích hợp khi muốn overlap async công việc nhưng thứ tự nhóm A trước B. Tương tự `flatMapSequential`, sẽ phải queue output từ source B khi A chậm. Nó **không** đảm bảo các side effects A/B xảy ra tuần tự.

### 8.5. combineLatest: trạng thái mới nhất

```java
Flux<String> dashboard = Flux.combineLatest(
        temperatureFlux,
        humidityFlux,
        (temperature, humidity) ->
                "temperature=" + temperature + ",humidity=" + humidity);
```

Sau khi mỗi source phát ít nhất một giá trị, combineLatest phát tổ hợp giá trị mới nhất mỗi khi nguồn bất kỳ cập nhật. Thích hợp dashboard/sensor state, không phải pairing theo index. Các values có thể đến từ thời điểm khác nhau, không bảo đảm DB transaction-consistent snapshot. Một source chưa từng phát thì chưa có đủ “latest values” để combine.

### 8.6. Bảng quyết định

| Mục tiêu | Operator |
| --- | --- |
| Ghép hai Mono độc lập, đủ cả hai value | `Mono.zip` |
| Nguồn phải biết output của nguồn trước | `flatMap` / `zipWhen` |
| Ghép từng phần tử theo index | `Flux.zip` |
| Trộn events giữa sources theo lúc đến | `merge` |
| Phát hết A rồi mới B | `concat` |
| Subscribe sớm nhưng giữ output theo source | `mergeSequential` |
| Ghép giá trị mới nhất của từng stream | `combineLatest` |
| Fan-out nhiều inner publishers ưu tiên throughput | `flatMap(fn,n)` |
| Fan-out nhưng giữ thứ tự output | `flatMapSequential(fn,n)` |
| Xử lý inner lần lượt, có execution order | `concatMap(fn)` |

## 9. Error handling khi compose

### 9.1. flatMap mặc định fail-fast

```java
Flux<Product> products = Flux.fromIterable(ids)
        .flatMap(id -> fetchProduct(id), 4);
```

Nếu một inner phát `onError`, composition thường phát lỗi terminal, cancel các inner còn lại. Nếu API yêu cầu **đủ mọi sản phẩm**, đây có thể là lựa chọn đúng.

Nếu một lỗi 404 được phép bỏ qua:

```java
Flux<Product> bestEffort = Flux.fromIterable(ids)
        .flatMap(id -> fetchProduct(id)
                .onErrorResume(ProductNotFoundException.class,
                        ex -> Mono.empty()), 4);
```

Đây là fragment: `ProductNotFoundException` cần định nghĩa. Không dùng fallback rỗng với mọi exception nếu ứng dụng cần biết sự cố 500/timeouts. Để ghi nhận partial failures, trả DTO từng item có success/error thay vì âm thầm drop.

### 9.2. Partial result DTO

```java
record ItemResult(int id, String value, String error) {}

Flux<ItemResult> result = Flux.fromIterable(ids)
        .flatMap(id -> fetchProduct(id)
                .map(p -> new ItemResult(id, p.name(), null))
                .onErrorResume(ex -> Mono.just(
                        new ItemResult(id, null, "remote_failed"))), 4);
```

Đây là snippet; `ids`, `fetchProduct`, `Product` phải tồn tại. Không gửi nguyên exception message chứa credentials/IP/SQL sang client. Nếu cần giữ thứ tự, dùng `flatMapSequential`; lựa chọn HTTP status cho partial success phải thuộc API contract.

### 9.3. Empty và error của zip

Nếu một source rỗng, zip có thể hoàn tất rỗng. Nếu một source lỗi, zip thường fail-fast/cancel những sources còn lại. Với permission service bị lỗi, **không** mặc định `onErrorReturn(new Permissions("ADMIN"))`; fail closed để không mở rộng quyền.

### 9.4. Retry + timeout + không tạo retry storm

```java
Mono<Product> call = fetchProduct(id)
        .timeout(Duration.ofSeconds(2))
        .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                .filter(ex -> ex instanceof TransientFailure));
```

Snippet cần import `reactor.util.retry.Retry` và class `TransientFailure`. `retryWhen` ở đây cho tối đa 2 retries bổ sung theo filter; backoff và deadline vẫn phải gắn với SLO và per-request budget. Với 100 inbound calls × 5 fan-out × 3 attempts, tải có thể tăng lớn khi dependency lỗi. Không retry mù 4xx/permission/side-effecting POST.

### 9.5. flatMapDelayError

`flatMapDelayError(mapper, concurrency, prefetch)` trì hoãn lỗi cho đến khi những hoạt động/backlog phù hợp được xử lý. Nó **không tự chuyển thành partial success**: cuối cùng vẫn có terminal error. Khi muốn báo cáo item-wise, per-inner error DTO thường rõ ràng hơn.

### 9.6. onErrorContinue là lựa chọn nhạy cảm

`onErrorContinue` có semantics phụ thuộc operators và có thể gây hành vi khó dự đoán ở upstream. Đừng dùng mặc định để “bỏ qua bất kỳ item lỗi nào”; prefer `onErrorResume` đặt **bên trong mapper** với contract rõ và metrics.

## 10. Schedulers và tránh blocking event loop

`flatMap` không tự tạo threads. Reactor có:
- `subscribeOn(scheduler)`: ảnh hưởng nơi nguồn/subscription và upstream chạy.
- `publishOn(scheduler)`: dịch execution context của operators phía sau.
- `Schedulers.boundedElastic()`: phù hợp để bridge một số blocking tasks có giới hạn.
- `Schedulers.parallel()`: cho một số công việc CPU có giới hạn; không dùng để chạy JDBC blocking.

```java
Mono<LegacyRecord> record = Mono.fromCallable(
                () -> jdbcService.findRecord(id))
        .subscribeOn(Schedulers.boundedElastic());
```

Không biến JDBC thành nonblocking; phải giới hạn pool/queue và cân nhắc Spring MVC cho kiến trúc chủ yếu blocking.

**Anti-pattern trong WebFlux controller:**

```java
@GetMapping("/bad")
Product bad() {
    return webClient.get().uri("/products/1").retrieve()
            .bodyToMono(Product.class).block();
}
```

Lệnh block terminal có thể block hoặc bị cấm trên Reactor nonblocking event-loop. Controller nên:

```java
@GetMapping("/good")
Mono<Product> good() {
    return webClient.get().uri("/products/1").retrieve()
            .bodyToMono(Product.class);
}
```

Hai snippets yêu cầu `webClient`, `Product` và `@RestController`; trong app thật trả Publisher cho Spring WebFlux để framework subscribe và điều khiển response. Không tạo detached `subscribe()` trong handler.

## 11. Demo runnable Spring Boot 3.5 + Java 17

Ví dụ sử dụng `Mono.delay` để mô phỏng async I/O, không có database hoặc external service. Các endpoints:
- `GET /api/demo/map`: map deterministic;
- `GET /api/demo/flat?concurrency=2`: flatMap có thể đảo order;
- `GET /api/demo/ordered`: flatMapSequential giữ ID order;
- `GET /api/demo/serial`: concatMap tuần tự;
- `GET /api/demo/profile/{id}`: zip account và settings độc lập;
- `GET /api/demo/zip`, `/concat`, `/merge`: các semantics ghép.

### 11.1. POM fragment

Tạo project tại [Spring Initializr](https://start.spring.io/), Maven/Java 17/Spring Boot 3.5.x, chọn **Spring Reactive Web (WebFlux)**. Dependency chính:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

Đây là fragment của POM do Initializr tạo, không phải toàn bộ POM. Để giữ server reactive, không thêm `spring-boot-starter-web` vào demo này; khi cả hai cùng có mặt trong Boot 3.x, Servlet MVC thường được ưu tiên.

### 11.2. Mã Java đầy đủ

Đặt toàn bộ nội dung vào `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

record ItemView(int id, String label) {}
record AccountView(int id, String name) {}
record SettingsView(String theme) {}
record ProfileView(int id, String name, String theme) {}

@Service
class AsyncDemoService {

    // Với ID 1..4, delay lần lượt 320, 240, 160, 80 ms.
    Mono<ItemView> loadItem(int id) {
        long millis = 80L * (5 - id);
        return Mono.delay(Duration.ofMillis(millis))
                .map(tick -> new ItemView(id, "item-" + id));
    }

    Mono<AccountView> loadAccount(int id) {
        return Mono.delay(Duration.ofMillis(100))
                .map(tick -> new AccountView(id, "user-" + id));
    }

    Mono<SettingsView> loadSettings(int id) {
        return Mono.delay(Duration.ofMillis(70))
                .map(tick -> new SettingsView("dark"));
    }
}

@RestController
@RequestMapping("/api/demo")
class DemoController {

    private final AsyncDemoService service;

    DemoController(AsyncDemoService service) {
        this.service = service;
    }

    @GetMapping("/map")
    Flux<Integer> mapped() {
        return Flux.range(1, 4).map(i -> i * 10);
    }

    @GetMapping("/flat")
    Flux<ItemView> flattened(
            @RequestParam(defaultValue = "2") int concurrency) {
        if (concurrency < 1 || concurrency > 4) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "concurrency must be between 1 and 4");
        }
        return Flux.range(1, 4)
                .flatMap(service::loadItem, concurrency);
    }

    @GetMapping("/ordered")
    Flux<ItemView> ordered() {
        return Flux.range(1, 4)
                .flatMapSequential(service::loadItem, 3);
    }

    @GetMapping("/serial")
    Flux<ItemView> serial() {
        return Flux.range(1, 4)
                .concatMap(service::loadItem);
    }

    @GetMapping("/profile/{id}")
    Mono<ProfileView> profile(@PathVariable int id) {
        if (id < 1) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "id must be positive"));
        }
        return Mono.zip(
                service.loadAccount(id),
                service.loadSettings(id),
                (account, settings) -> new ProfileView(
                        account.id(), account.name(), settings.theme()));
    }

    @GetMapping("/zip")
    Flux<String> zipped() {
        return Flux.zip(
                Flux.just("A", "B", "C"),
                Flux.just(1, 2),
                (letter, number) -> letter + number);
    }

    @GetMapping("/concat")
    Flux<String> concatenated() {
        return Flux.concat(
                Flux.just("A1", "A2"),
                Flux.just("B1", "B2"));
    }

    @GetMapping("/merge")
    Flux<String> merged() {
        Flux<String> a = Flux.just("A1", "A2")
                .delayElements(Duration.ofMillis(100));
        Flux<String> b = Flux.just("B1", "B2")
                .delayElements(Duration.ofMillis(60));
        return Flux.merge(a, b);
    }
}
```

### 11.3. Giải thích code

File chỉ có một public top-level class và các records/classes package-private: hợp lệ trong Java 17. Spring Boot WebFlux subscribe publishers cho HTTP response, không có `.block()`/`.subscribe()` thủ công. `Mono.delay` tạo async timer giả lập, không chứng minh hệ thống có HTTP network traffic thực. `flatMap(concurrency)` chỉ giới hạn inner subscriptions cho mỗi request. `flatMapSequential`/`concatMap` phát ID 1..4, nhưng concatMap không tạo các inner cùng lúc. `Mono.zip` cần cả Account và Settings. `Flux.zip` bỏ chữ C vì không có số thứ ba. `Flux.merge` có output order không định trước.

Đây là demo không có auth/DB/CSRF/rate limiting/metrics; không public deploy làm API sản xuất.

### 11.4. Chạy

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell / Command Prompt
mvnw.cmd spring-boot:run
```

Nếu không đổi server port, thử `http://localhost:8080`.

### 11.5. Đối chiếu các kết quả

**Map:**

```bash
curl -i http://localhost:8080/api/demo/map
```

Status 200, JSON body deterministic:

```json
[10,20,30,40]
```

**FlatMap (thứ tự tùy scheduling):**

```bash
curl -i 'http://localhost:8080/api/demo/flat?concurrency=3'
```

Response chứa đúng bốn objects ID=1..4, nhưng thứ tự có thể là [3,2,1,4] hoặc kiểu khác. **Không assert cứng một thứ tự** trong test. Nếu `concurrency=0` hoặc `concurrency=5`, endpoint trả 400 do kiểm tra range 1..4.

**FlatMapSequential:**

```bash
curl -i http://localhost:8080/api/demo/ordered
```

JSON array theo ID:

```json
[
 {"id":1,"label":"item-1"},
 {"id":2,"label":"item-2"},
 {"id":3,"label":"item-3"},
 {"id":4,"label":"item-4"}
]
```

**ConcatMap:**

```bash
curl -i http://localhost:8080/api/demo/serial
```

Cũng có ID 1,2,3,4, nhưng subscribe mỗi inner sau khi inner trước hoàn tất. Trong demo, tổng thời gian thường dài hơn việc gọi các inner overlapping; không cam kết latency chính xác.

**Mono.zip profile:**

```bash
curl -i http://localhost:8080/api/demo/profile/7
```

Body:

```json
{"id":7,"name":"user-7","theme":"dark"}
```

Hai nguồn trễ khoảng 100ms và 70ms; nếu overlap thực, tổng thời gian gần max latency cộng overhead, không mặc định bằng 170ms hay đúng 100ms. `id=0` trả 400.

**Flux.zip vs Flux.concat:**

```bash
curl -i http://localhost:8080/api/demo/zip
curl -i http://localhost:8080/api/demo/concat
```

Bodies lần lượt:

```json
["A1","B2"]
```

```json
["A1","A2","B1","B2"]
```

**Flux.merge:**

```bash
curl -i http://localhost:8080/api/demo/merge
```

Kết quả gồm A1,A2,B1,B2, nhưng thứ tự giữa hai nguồn phụ thuộc timing. A1 trước A2 trong nguồn A và B1 trước B2 trong nguồn B.

**PowerShell:** dùng `curl.exe` thay cho alias `curl` của một số phiên bản:

```powershell
curl.exe -i "http://localhost:8080/api/demo/map"
curl.exe -i "http://localhost:8080/api/demo/flat?concurrency=3"
curl.exe -i "http://localhost:8080/api/demo/ordered"
curl.exe -i "http://localhost:8080/api/demo/profile/7"
```

## 12. Test với WebTestClient và StepVerifier

### 12.1. Test dependencies

Thêm vào POM Spring Boot 3.5:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
```

Boot BOM quản lý versions. `reactor-test` cung cấp `StepVerifier`, Spring Test và WebFlux stack hỗ trợ `WebTestClient`.

### 12.2. HTTP integration test

Lưu `src/test/java/com/example/demo/ReactiveCombinationTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ReactiveCombinationTest {

    @Autowired
    WebTestClient client;

    @Test
    void mapProducesValues() {
        client.get().uri("/api/demo/map")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[10,20,30,40]");
    }

    @Test
    void orderedMaintainsInputOrder() {
        client.get().uri("/api/demo/ordered")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(1)
                .jsonPath("$[1].id").isEqualTo(2)
                .jsonPath("$[2].id").isEqualTo(3)
                .jsonPath("$[3].id").isEqualTo(4);
    }

    @Test
    void zipCombinesProfile() {
        client.get().uri("/api/demo/profile/7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7)
                .jsonPath("$.name").isEqualTo("user-7")
                .jsonPath("$.theme").isEqualTo("dark");
    }

    @Test
    void invalidConcurrencyIsRejected() {
        client.get().uri("/api/demo/flat?concurrency=0")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
```

Test không assert thứ tự `/flat` vì operator không hứa điều đó. Với môi trường CI quá chậm, cần xem timeout của WebTestClient/timeouts của app, không thay tests bằng `Thread.sleep()`.

### 12.3. Unit test operator

Lưu `src/test/java/com/example/demo/OperatorTest.java`:

```java
package com.example.demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OperatorTest {

    @Test
    void mapIsDeterministic() {
        StepVerifier.create(Flux.just(1, 2, 3)
                        .map(i -> i * 10))
                .expectNext(10, 20, 30)
                .verifyComplete();
    }

    @Test
    void zipStopsWithShortest() {
        StepVerifier.create(Flux.zip(
                        Flux.just("A", "B", "C"),
                        Flux.just(1, 2),
                        (a, b) -> a + b))
                .expectNext("A1", "B2")
                .verifyComplete();
    }

    @Test
    void emptyMonoMakesZipEmpty() {
        StepVerifier.create(
                        Mono.zip(Mono.just(1),
                                Mono.<String>empty()))
                .verifyComplete();
    }

    @Test
    void concatKeepsBothSourcesInOrder() {
        StepVerifier.create(
                        Flux.concat(
                                Flux.just("A1", "A2"),
                                Flux.just("B1", "B2")))
                .expectNext("A1", "A2", "B1", "B2")
                .verifyComplete();
    }

    @Test
    void collectListProducesList() {
        StepVerifier.create(
                        Flux.just("A", "B").collectList())
                .expectNext(List.of("A", "B"))
                .verifyComplete();
    }
}
```

Đây là unit tests về **semantics** không dựa vào thời gian. Nếu cần test concurrency thực:
- instrument trong `Mono.defer(...)` để đếm active subscriptions;
- tăng `AtomicInteger` khi subscribe, giảm trong `doFinally(...)`;
- phát controlled completion bằng `Sinks.One`/virtual scheduler;
- assert peak active ≤ concurrency;
- không đo counter ở thời điểm **assembly**, vì assembly không phải subscription.

### 12.4. Chạy test

```bash
# macOS/Linux
./mvnw test

# Windows
mvnw.cmd test
```

## 13. Thực hành WebClient: tránh serial HTTP calls

Tổng hợp product và inventory độc lập:

```java
Mono<Product> product = catalogClient.get()
        .uri("/products/{id}", id).retrieve()
        .bodyToMono(Product.class);

Mono<Stock> stock = inventoryClient.get()
        .uri("/stock/{id}", id).retrieve()
        .bodyToMono(Stock.class);

Mono<ProductPage> page = Mono.zip(
        product, stock, ProductPage::new);
```

Snippet cần WebClient beans/DTO và id. Nếu recommendation phụ thuộc product category:

```java
Mono<RecommendationPage> page =
        catalogClient.get()
                .uri("/products/{id}", id)
                .retrieve()
                .bodyToMono(Product.class)
                .flatMap(product ->
                        recommendationClient.get()
                                .uri("/categories/{cat}/recommendations",
                                        product.category())
                                .retrieve()
                                .bodyToFlux(Product.class)
                                .take(10)
                                .collectList()
                                .map(items -> new RecommendationPage(
                                        product, items)));
```

Cần lấy product trước vì recommendation URL phụ thuộc category; `flatMap` biểu diễn dependency. Nếu muốn giữ order kết quả từ một danh sách IDs theo UI rank, dùng `flatMapSequential(this::fetchProduct, concurrency)`. Với updates cần strict side-effect order, dùng `concatMap` và transaction/version policy của datastore.

### 13.1. Các giới hạn cùng lúc

```java
Flux<Product> products = Flux.fromIterable(ids)
        .take(50)
        .flatMap(id -> fetchProduct(id)
                .timeout(Duration.ofSeconds(2)), 5);
```

`take(50)` cap số IDs từ Flux nhưng nếu `ids` đã materialize thành List rất lớn trước đó, memory của List vẫn tồn tại. Nên giới hạn ngay request parser/API boundary. `flatMap(...,5)` chỉ điều chỉnh concurrency của một subscription; HTTP client pool/global rate limit vẫn cần cài riêng.

## 14. Backpressure, bộ nhớ và response HTTP

Nguồn tăng memory thường gặp:
- `flatMapSequential` buffer inner đến sau hoàn tất trước;
- `zip` buffer từ source nhanh đợi source chậm;
- `collectList()` gom toàn source cho đến complete;
- `buffer(n)` gom batches;
- `cache()` trên hot/unbounded Flux không giới hạn lịch sử;
- `onBackpressureBuffer()` không maxSize;
- response conversion, decoder buffer, Netty ByteBuf, downstream client pool pending queue.

**Backpressure không có nghĩa heap luôn hữu hạn.** Mọi collection/queue và concurrency đều phải có business/storage limits.

Trong WebFlux, `Flux<T>` trả `application/json` có thể được encode/stream theo cơ chế non-blocking writer thay vì nhất thiết gom toàn bộ như MVC. Tuy nhiên nếu handler tự `collectList()`, phần dữ liệu đó đã được gom trước khi writer có thể stream; với stream vô hạn sẽ không có complete.

Với `text/event-stream` hoặc NDJSON, từng item có thể được flush/stream riêng; khi lỗi xảy ra **sau khi đã commit HTTP headers/body**, không thể bảo đảm đổi HTTP 200 thành 500. Thiết kế application event-error/connection-close và retry/cancellation cho streams dài. Với một Mono.zip trả DTO sau khi các dependencies xong, lỗi thường xuất hiện trước khi viết body, dễ mapping thành 4xx/5xx hơn.

### 14.1. combineLatest không tạo snapshot transaction

Nếu dashboard ghép giá A (lúc t=1) với số lượng B (lúc t=2), combineLatest có thể phát bản ghép từ những dữ liệu **không cùng transaction timestamp**. Với tài chính hoặc invariants quan trọng, cần API/DB snapshot/version chung, không chỉ combineLatest.

### 14.2. Cancellation không hoàn tác DB commit

Khi browser ngắt, upstream Publisher có thể bị cancel, nhưng DB side effect đã commit không rollback tự động. Đừng dùng HTTP cancellation như thay transaction hoặc distributed saga compensation. Dùng idempotency keys, transaction/outbox và operation status khi nghiệp vụ yêu cầu.

### 14.3. Context, auth và secrets

Reactor Context khác Java ThreadLocal. Đừng giả định MDC/SecurityContextHolder sẽ tự có trên mọi scheduler thread. Dùng Spring Security reactive context/context propagation phù hợp, truyền identity/tenant rõ ràng vào downstream, và giới hạn mọi truy vấn theo scope đã kiểm tra. Với concurrent fan-out, tránh lộ kết quả tenant khác hoặc ghi raw Authorization token trong lỗi/logs.

## 15. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `Mono<Mono<T>>` | Dùng map cho hàm trả Mono | flatMap |
| `Flux<Flux<T>>` | Dùng map cho hàm trả Flux | flatMap/concatMap |
| Item đảo thứ tự | flatMap merge theo completion time | flatMapSequential hoặc concatMap |
| Side effects ghi lộn thứ tự | Nhiều inner chạy đồng thời | concatMap, DB lock/version |
| Chậm vì chỉ xử lý từng item | concatMap với I/O độc lập | flatMap với concurrency đo được |
| Dữ liệu chờ lâu và memory tăng | flatMapSequential queue chờ inner đầu | Giảm concurrency/prefetch, bỏ ordering nếu được |
| Tăng concurrency nhưng throughput không tăng | Downstream/pool bão hòa hoặc nguồn blocking | Đo latency, pool, scheduler, RPS |
| Zip không có output | Một source empty/Mono<Void> | switchIfEmpty/when/then theo contract |
| Zip bên phụ không subscribe | Source rỗng khiến zip complete sớm | Không buộc side effect vào zip subscription |
| Zip bỏ phần dư | Một Flux ngắn hơn | Đúng semantics; chọn merge/combineLatest nếu khác mục tiêu |
| Concat không chạy B | A vô hạn | Thay merge hoặc ràng buộc A |
| merge không giữ global order | Giá trị interleave | concat |
| combineLatest chưa emit | Chưa nhận một value từ mỗi source | Thiết kế defaults/khởi tạo |
| WebFlux controller block event loop | Dùng block()/JDBC/Thread.sleep | Reactive I/O hoặc boundedElastic bridge |
| Queue cao, 429/503 tăng | Fan-out nhân concurrency inbound | Bulkhead/rate limit/pool limits |
| Một lỗi làm dừng cả batch | flatMap/zip fail-fast | Per-item onErrorResume/DTO result |
| Timeout/retry làm tải nặng hơn | Retry nhiều không có giới hạn | Deadline/backoff/filter/jitter |
| Partial errors bị che | onErrorResume trả empty mọi lỗi | Phân loại và báo thiếu item rõ |
| Concurrency test sai | Đếm mapper assembly thay subscriptions | Mono.defer + doFinally |
| HTTP lỗi không đổi được status | Response đã committed | Error event/close và pre-validation |
| collectList OOM | Nguồn quá lớn/vô hạn | Limit/pagination/stream |
| Context tenant bị mất | ThreadLocal không được propagate | Reactor Context/auth-scope propagation |
| Unit test order không ổn định | Assert fixed order của flatMap/merge | Assert tập items, không assume order |

## 16. Checklist review

1. Function trong map trả value hay Publisher?
2. Source là Mono (0..1), Flux (0..N), cold hay hot?
3. Có thể empty/Mono<Void>? Zip sẽ làm gì?
4. Có cần thứ tự output hoặc thứ tự side effects? Đó là hai yêu cầu khác.
5. Khi nào dùng flatMapSequential thay concatMap và chấp nhận queue?
6. Concurrency thực tế per request và trên toàn hệ thống có được giới hạn?
7. Prefetch/inner cardinality/item size có nguy cơ heap growth?
8. Retry, timeout, cancellation và downstream connection pool được cấu hình?
9. Với concurrent writes, DB transaction/optimistic locking có được áp?
10. Errors fail-fast hay per-item, client có nhận biết partial success?
11. Có blocking operations trên Netty/event-loop?
12. Có CORS/auth/tenant ACL/rate limits khi fan-out WebClient?
13. HTTP writer stream hay collectList? Lỗi sau committed response xử lý thế nào?
14. Có tests deterministic cho map/zip/concat và thứ tự flatMapSequential?
15. Có tests concurrency dựa vào subscription, không dựa vào thời gian?
16. Có đo p95/p99 latency, active connections, queue, 429/503 và memory với slow downstream?

## 17. Tự kiểm tra

**Câu 1.** Dùng `Mono<UserId>.map(id -> fetchUser(id))`, nếu `fetchUser` trả Mono<User> thì type kết quả là gì?

**Đáp án:** `Mono<Mono<User>>`. Dùng `flatMap` cho `Mono<User>`.

**Câu 2.** `flatMap(mapper, 3)` có nghĩa ứng dụng dùng đúng ba threads không?

**Đáp án:** Không. Nó giới hạn inner subscriptions/in-flight của operator, không phải số threads toàn ứng dụng.

**Câu 3.** Cần giữ thứ tự dữ liệu đầu ra nhưng vẫn overlap calls thì chọn gì?

**Đáp án:** `flatMapSequential`, chấp nhận buffer khi inner đến sau xong sớm.

**Câu 4.** Cần A commit trước khi B bắt đầu thì `flatMapSequential` đủ không?

**Đáp án:** Không. Dùng `concatMap`/sequencing và bảo vệ DB transaction/concurrency.

**Câu 5.** Một Mono trong `Mono.zip` rỗng thì sao?

**Đáp án:** Không có đủ dữ liệu để combine; zip thường hoàn tất rỗng.

**Câu 6.** Flux.zip("A","B","C" và 1,2) trả gì?

**Đáp án:** Các cặp A1, B2; C không có phần tử ở source kia nên không phát.

**Câu 7.** Dashboard cần giá trị mới nhất khi sensor bất kỳ cập nhật?

**Đáp án:** `combineLatest`, sau khi từng source có ít nhất một value.

**Câu 8.** Một trong hai source của Flux.concat không hoàn tất thì source sau thế nào?

**Đáp án:** Nguồn sau chờ mãi (nếu không có timeout/cancellation).

**Câu 9.** Tại sao concurrency 100 có thể chậm hơn 10?

**Đáp án:** Có thể làm downstream pool/CPU/queue/429/503 bão hòa; phải benchmark theo tải thực.

**Câu 10.** Dùng `.block()` trong WebFlux HTTP controller có hợp lý không?

**Đáp án:** Tránh; return Mono/Flux và compose async work thay vì chặn event loop.

## 18. Nguồn tham khảo chính thức

- [Spring Framework — WebFlux Overview](https://docs.spring.io/spring-framework/reference/web/webflux.html): Reactor/WebFlux non-blocking architecture.
- [Spring Framework — Reactive Core](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html): Reactive Streams, Mono, Flux và backpressure.
- [Spring Framework — WebFlux Controller Return Values](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html): Publisher response encoding, stream semantics.
- [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html): compose non-blocking HTTP calls.
- [Spring Framework — WebClient retrieve](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html): bodyToMono/bodyToFlux.
- [Project Reactor Core — Reference Guide](https://projectreactor.io/docs/core/release/reference/): transforming/combining streams, schedulers, error handling, testing.
- [Project Reactor — Flux Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html): flatMap, flatMapSequential, concatMap, merge, zip, combineLatest, prefetch.
- [Project Reactor — Mono Javadoc](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html): Mono.zip/zipWhen/flatMap.
- [Project Reactor FAQ — zipWith/zipWhen empty sources](https://projectreactor.io/docs/core/release/reference/faq.html#faq.zip.empty): nguyên nhân không emit với Mono<Void>/empty.
- [Spring Boot 3.5 — Reactive Web](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html): WebFlux starter, reactive server.
- [Spring Boot 3.5 — Application Testing](https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html): WebTestClient.
- [Reactive Streams Specification](https://www.reactive-streams.org/): Publisher/Subscriber/Subscription demand, cancellation.

**Kết luận:** `map` tạo giá trị đồng bộ, `flatMap` nối kết quả Publisher và cho phép inner async tiến triển đồng thời nhưng không bảo đảm thứ tự; `flatMapSequential` giữ thứ tự **output**, `concatMap` tuần tự hóa **thực hiện inners**. Ghép kết quả độc lập bằng `Mono.zip`, phụ thuộc nhau bằng `flatMap`/`zipWhen`, từng cặp bằng `Flux.zip`, trộn theo thời gian bằng `merge`, nối theo thứ tự bằng `concat`, ghép trạng thái mới nhất bằng `combineLatest`. Chọn concurrency theo hạn mức downstream, total inbound load, backpressure/prefetch/memory, timeouts, error semantics và benchmarks thay vì “càng cao càng nhanh”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-foundations-007">Lỗi, hủy đăng ký, timeout và giải phóng tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Spring WebFlux — Lỗi, hủy đăng ký, timeout và giải phóng tài nguyên

## 1. Mục tiêu và bối cảnh

Spring WebFlux dựa trên Reactive Streams và Reactor (`Mono`/`Flux`). Trong ứng dụng reactive, một HTTP request không nhất thiết đồng nghĩa một Servlet thread đang chờ đến khi controller return. Một request có thể là một **chuỗi tín hiệu bất đồng bộ** được subscription kích hoạt, xử lý qua operators rồi kết thúc bằng complete, error hoặc cancellation.

Tình huống thực tế:

- Client gọi API phụ thuộc upstream chậm, sau 2 giây cần timeout và trả thông báo an toàn.
- Browser mở Server-Sent Events (SSE), sau đó đóng tab: ứng dụng phải giải phóng subscription, tài nguyên stream và giảm số active connections.
- WebClient đã nhận response HTTP nhưng code không đọc body: pooled connection/DataBuffer có thể không được giải phóng nếu dùng API thấp không đúng cách.
- Service dùng resource bất đồng bộ (connection, session, cursor) và bị hủy giữa stream: cleanup phải chạy cả khi cancel, chứ không chỉ khi complete.
- Stream đã gửi status 200 cùng một số bytes rồi mới lỗi: HTTP response đã committed, không thể “đổi sang 500 JSON” trên cùng response.

Sau bài học, bạn có thể:

1. Phân biệt `onNext`, `onComplete`, `onError` và `cancel()` theo Reactive Streams; hiểu vì sao **cancel không phải onError**.
2. Sử dụng `onErrorResume`, `onErrorMap`, `onErrorReturn`, `doOnError`, `doFinally`, `doOnCancel` và `retryWhen` có kiểm soát.
3. Phân biệt `timeout(Duration)` của Reactor, network timeouts ở Reactor Netty/WebClient và gateway/client timeouts.
4. Dùng `Flux.using`/`Mono.using` cho resource synchronous, `usingWhen` cho asynchronous resource cleanup trên **complete/error/cancel**.
5. Biết khi nào phải release pooled `DataBuffer` và response body, đặc biệt khi tự làm việc với streaming/raw buffers.
6. Dùng WebFlux `@RestControllerAdvice`, `ProblemDetail`, `ResponseStatusException` và `WebExceptionHandler` đúng lớp, không lộ stacktrace cho client.
7. Thiết kế streaming SSE/WebClient resource lifecycle, backpressure, cancellation propagation, retry idempotency và observability.
8. Chạy demo **Java 17 / Spring Boot 3.5.0 / Spring Framework 6.2**, kiểm thử `WebTestClient` và `StepVerifier`.

**Phạm vi:** Spring WebFlux reactive stack. Spring MVC dùng Servlet async `SseEmitter`/`DeferredResult` và có lifecycle khác. Không dùng MVC `ResponseBodyEmitter` hoặc `AsyncRequestNotUsableException` như giải pháp chính cho WebFlux.

## 2. Reactive Streams: ba tín hiệu kết thúc và một hành động hủy

Một `Publisher<T>` có thể phát:

~~~text
onSubscribe(Subscription)
      |
      +-- request(n) -------------------------+
      |                                       |
      v                                       v
onNext(value) ... onNext(value) ...    onComplete()
      |                                       ^
      +-------------------------------> onError(error)
      |
      +-- cancel() [subscriber -> upstream]
~~~

Đồ thị trên mô tả các khả năng, không phải một chuỗi mà terminal signals cùng xuất hiện: theo protocol, sau **onComplete hoặc onError**, publisher không được tiếp tục phát onNext/terminal khác.

- `onNext(T)`: dữ liệu, có thể bằng 0 hoặc nhiều lần với `Flux`, tối đa một lần với `Mono`.
- `onComplete()`: kết thúc thành công, không còn dữ liệu.
- `onError(Throwable)`: kết thúc có lỗi, không tiếp tục onNext bình thường trong publisher đó.
- `Subscription.cancel()`: tín hiệu **từ downstream/subscriber lên upstream** để thông báo không cần dữ liệu nữa; **không phải onError** và không nhất thiết dẫn tới onComplete cho subscriber đã hủy.

**Hủy không đồng nghĩa lỗi.** Ví dụ `take(1)` chủ động cancel upstream sau khi nhận đủ một phần tử; đó là kết thúc đúng kỳ vọng, không phải lỗi hệ thống.

### 2.1. Tại sao phải hiểu cancel?

Nếu bạn chỉ có:

~~~java
.doOnError(error -> releaseResource())
~~~

thì resource có thể bị giữ khi browser đóng kết nối hoặc consumer gọi `take(1)` và cancel. Nếu chỉ dùng:

~~~java
.doOnComplete(() -> releaseResource())
~~~

thì cleanup cũng không chạy trên error hay cancel.

Phù hợp hơn khi chỉ cần side effect quan sát terminal:

~~~java
.doFinally(signal -> recordTermination(signal))
~~~

`SignalType` nhận giá trị như `ON_COMPLETE`, `ON_ERROR`, `CANCEL` tùy lifecycle. `doFinally` chạy khi sequence kết thúc vì bất kỳ nguyên nhân terminal/cancel theo Reactor semantics. Callback cần **không blocking** và xử lý thread-safety.

Đối với resource sở hữu cụ thể, ưu tiên **scope resource** bằng `using`/`usingWhen` thay vì chỉ ghép những callbacks thủ công.

### 2.2. Cold và hot publisher

| Loại | Ví dụ | Hệ quả khi cancel |
| --- | --- | --- |
| Cold | `Mono.fromCallable(...)`, repository reactive query | Subscription thường khởi phát riêng hoạt động; cancel request hủy upstream ở mức publisher |
| Hot/shared | Shared `Sinks.Many`, Kafka consumer dùng chung, `publish().refCount()` | Cancel một subscriber không nhất thiết dừng producer chung |
| Finite | `Flux.range(1, 10)` | Có thể complete rất nhanh trước khi quan sát cancel |
| Infinite | `Flux.interval(...)`, SSE/live updates | Phải quản lý cancellation, backpressure, request lifetime |

Một subscription bị cancel **không đảm bảo** toàn bộ công việc ngoài hệ thống đã dừng. Ví dụ một JDBC driver blocking, một HTTP request đã gửi tới upstream hoặc một job bên thứ ba có thể vẫn đang làm việc. Reactive cancellation là **cooperative/best-effort** tùy nguồn dữ liệu và adapter.

## 3. Operators xử lý lỗi: chọn đúng ngữ nghĩa

Trong Reactor, error là **terminal signal**. Các recovery operators không “quay lại chạy tiếp các phần tử còn lại của nguồn cũ”; thường chúng thay thế phần thất bại bằng một publisher/fallback mới.

### 3.1. Bảng lựa chọn

| Operator | Ý nghĩa | Khi dùng |
| --- | --- | --- |
| `doOnError(consumer)` | Side effect khi nhìn thấy lỗi; **không nuốt lỗi** | Logging/metrics/tracing |
| `onErrorReturn(value)` | Trả một giá trị fallback, rồi hoàn thành | Default rõ và an toàn |
| `onErrorResume(function)` | Chuyển sang publisher fallback | Cache fallback, dự phòng có chính sách |
| `onErrorMap(function)` | Đổi loại exception, vẫn là error | Mapping domain/transport exception |
| `onErrorComplete()` | Biến lỗi thành complete rỗng | Chỉ khi bỏ qua lỗi có chủ đích |
| `retryWhen(Retry...)` | Subscribe lại **nguồn** theo policy | Tạm lỗi, có idempotency và giới hạn |
| `doFinally(consumer)` | Quan sát complete/error/cancel | Metrics và terminal cleanup side effects |
| `doOnCancel(action)` | Chạy khi cancel | Dừng producer riêng hoặc ghi metric cancellation |
| `onErrorContinue(...)` | Tiếp tục xử lý một số phần tử theo semantics operator | Tránh làm giải pháp mặc định do scope/hành vi phức tạp |

### 3.2. doOnError không sửa lỗi

~~~java
Mono<Item> mono = service.load(id)
        .doOnError(error -> log.warn(
                "item_lookup_failed id={} kind={}",
                id, error.getClass().getSimpleName()));
~~~

Nếu upstream phát error, subscriber vẫn nhận error trừ khi phía dưới có operator phục hồi.

Không dùng `doOnError` làm nơi duy nhất quản lý resource cancellation. Và không log raw exception message cho mọi lỗi nghiệp vụ nếu message có SQL/password/PII.

### 3.3. onErrorResume có điều kiện

~~~java
Mono<Product> result = remoteCatalog.find(id)
        .onErrorResume(RemoteServiceUnavailable.class,
                ex -> cachedCatalog.find(id));
~~~

Đây là fragment: cần service/types của ứng dụng. Chỉ fallback cho loại lỗi có thể phục hồi. Đừng biến lỗi truy cập 403, validation 400 hoặc bug NullPointerException thành dữ liệu cache như thể đó là một success.

### 3.4. onErrorMap giữ nguyên ý nghĩa lỗi

~~~java
return upstream.fetch(id)
        .onErrorMap(TimeoutException.class,
                ex -> new CatalogTimeoutException(ex));
~~~

Mapping lỗi low-level thành domain-level giúp tầng WebFlux trả status ổn định. Bản demo phía dưới cũng map `TimeoutException` thành `ApiTimeoutException` rồi trả HTTP 504.

### 3.5. onErrorComplete có thể che lỗi

~~~java
Flux<AuditEvent> events = auditRepository.read()
        .onErrorComplete();
~~~

Nếu audit là dữ liệu quan trọng, việc biến lỗi thành complete có thể che mất mất mát dữ liệu. Chỉ dùng cho các hoạt động **tùy chọn**, đã có metrics và contract “best effort”.

### 3.6. onErrorResume sau timeout

~~~java
Mono<Item> result = remote.fetch(id)
        .timeout(Duration.ofMillis(500))
        .onErrorResume(TimeoutException.class,
                ex -> cache.find(id));
~~~

Ở đây operator timeout có thể phát `TimeoutException` xuống dưới và cancel upstream. Nếu cache fallback trả kết quả, downstream nhận success. Quan trọng: **đừng trả dữ liệu lỗi thời khi hợp đồng API đòi tính nhất quán tuyệt đối**.

## 4. Cancellation và hooks trong Reactor

### 4.1. doOnCancel so với doFinally

~~~java
Flux<Long> source = Flux.interval(Duration.ofSeconds(1))
        .doOnCancel(() -> metrics.incrementCancelled())
        .doFinally(signal -> metrics.incrementFinished(signal));
~~~

Đoạn trên minh họa với biến `metrics` tự định nghĩa. `doOnCancel` chỉ chạy khi cancellation; `doFinally` bao gồm cancel, complete và error.

Cần hiểu **operator placement**. Đặt `doFinally` **trước** `timeout` có thể quan sát CANCEL khi timeout hủy upstream; đặt **sau** `timeout` có thể quan sát ON_ERROR khi downstream nhận TimeoutException.

~~~java
Mono<String> result = remote()
        .doFinally(signal -> log.info("upstream={}", signal))
        .timeout(Duration.ofSeconds(2))
        .doFinally(signal -> log.info("downstream={}", signal));
~~~

Trong trường hợp `remote()` chưa kịp phát và timeout xảy ra, thường:

~~~text
upstream=CANCEL
downstream=ON_ERROR
~~~

Thứ tự log có thể không giống thứ tự dòng minh họa do propagation và scheduling; hãy test với `StepVerifier`. Đây là phân biệt quan trọng khi đặt metrics và không được đếm cả CANCEL+ERROR thành hai HTTP requests thất bại.

### 4.2. take(n) chủ động hủy nguồn

~~~java
Flux<Integer> firstTwo = Flux.range(1, 1_000_000)
        .doFinally(signal -> log.info("source={}", signal))
        .take(2);
~~~

Khi downstream đã nhận hai phần tử và complete, operator `take(2)` có thể cancel nguồn phía trước (trừ khi nguồn đã terminal trước). Source-side `doFinally` vì vậy có thể nhận `CANCEL`; subscriber-side thấy `ON_COMPLETE`. Đây là hành vi hợp lệ, không phải lỗi dữ liệu.

### 4.3. Cancellation do client disconnect

Trong WebFlux, khi connection/response consumer bị hủy, subscription phản hồi thường nhận cancel. Nhưng:

- Không có cam kết người dùng đóng tab sẽ được server nhận biết **ngay lập tức** trong mọi topology.
- Dữ liệu có thể bị buffer bởi socket, Netty hoặc reverse proxy.
- Nếu publisher là hot source dùng chung, hủy một subscription không tự đóng nguồn toàn cục.
- Nếu gọi thư viện blocking từ source, cancel có thể không dừng operation ngay.

Do đó, thiết kế cleanup theo **subscription lifecycle** và bảo vệ per-client resource scopes, không chỉ trông chờ exception “broken pipe”.

### 4.4. Không gọi subscribe() tùy tiện trong controller

Sai về ownership:

~~~java
@GetMapping("/start")
Mono<String> start() {
    remoteJob().subscribe(value -> log.info("job={}", value));
    return Mono.just("started");
}
~~~

Với cách này, subscription được tạo **ngoài lifecycle response**; client cancel không được truyền tự nhiên vào job ấy và errors có thể bị mất. Nếu đó là tác vụ phải sống lâu bất kể request, hãy dùng service/job queue được quản lý rõ. Nếu kết quả thuộc request, **return publisher** cho WebFlux subscribe/cancel.

Tương tự, tránh `block()` hoặc `Thread.sleep()` trên Netty event-loop; làm vậy giảm khả năng phục vụ nhiều requests. Với legacy blocking calls, dùng `Schedulers.boundedElastic()` có kiểm soát, ưu tiên native reactive drivers.

## 5. Timeout: Reactor operator, network, server và gateway

### 5.1. Reactor timeout

~~~java
Mono<String> response = remoteCall()
        .timeout(Duration.ofSeconds(2));
~~~

Nếu nguồn không phát tín hiệu theo yêu cầu trước deadline, Reactor phát `TimeoutException`, hủy subscription upstream và kết thúc downstream bằng error.

Biến thể fallback:

~~~java
Mono<String> response = remoteCall()
        .timeout(Duration.ofSeconds(2), Mono.just("cached"));
~~~

Downstream nhận fallback nếu timeout. Tuy nhiên operation remote ngoài hệ thống có thể vẫn chạy dù local Publisher đã nhận cancel; cancellation không thể “undo” một HTTP/SQL write đã thực thi.

### 5.2. Timeout vs deadline

Nếu API request trải qua A → B → C, mỗi tầng dùng timeout 2 giây riêng có thể dẫn tới tổng vượt SLA của client. Với deadline end-to-end, cần truyền remaining budget, cancel downstream khi hết hạn và đo thời gian từ request bắt đầu. Reactor `timeout` là operator-level policy, không tự tạo distributed deadline protocol.

### 5.3. Những loại timeout khác nhau

| Timeout | Tầng | Khi nào |
| --- | --- | --- |
| Reactor `timeout(Duration)` | Chuỗi Publisher | Bảo vệ logic và thời gian chờ tín hiệu |
| HTTP client response timeout | Reactor Netty HttpClient/WebClient | Chờ response read phù hợp với transport |
| Connect timeout | TCP/socket client | Mất quá lâu khi thiết lập kết nối |
| Read/write timeout | Network handlers | Socket không có tiến triển I/O |
| Server idle/request timeout | Netty/server config | Giới hạn connection/request tùy setup |
| Gateway/ingress timeout | Nginx/Envoy/CDN | Proxy tự đóng upstream/downstream |
| Caller/client deadline | Browser/mobile/other service | Caller ngừng chờ, có thể cancel |

Một request có thể bị client cancel **trước** timeout của server. Hoặc gateway timeout trước khi Reactor timeout và client chỉ thấy 504 do gateway tạo. Phải quan sát hai phía, không suy mọi `TimeoutException` có một nguyên nhân duy nhất.

### 5.4. Timeout của WebClient

Một fragment dùng Reactor Netty:

~~~java
HttpClient nettyClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(3));

WebClient client = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(nettyClient))
        .build();
~~~

Imports: `reactor.netty.http.client.HttpClient`, `org.springframework.http.client.reactive.ReactorClientHttpConnector`, `org.springframework.web.reactive.function.client.WebClient`. Đây là fragment cấu hình, không phải application đầy đủ. Connect timeout cần cấu hình channel option riêng nếu cần; không nhầm `responseTimeout` với tổng deadline toàn pipeline sau decoding/business processing.

### 5.5. Timeout và cleanup

Khi timeout hủy upstream Publisher, resource cleanup phụ thuộc source có hỗ trợ cancellation hay không. Với một resource bất đồng bộ, dùng `usingWhen(..., asyncCancel)` để thực hiện release khi cancel. Không chỉ dùng `doOnError` vì upstream có thể nhìn thấy `CANCEL` chứ không phải `ON_ERROR`.

## 6. Giải phóng resource bằng using và usingWhen

### 6.1. Flux.using — resource synchronous

Ví dụ tạo một `Lease` tăng active count khi subscribe và giảm khi complete/error/cancel:

~~~java
Flux<String> events = Flux.using(
        () -> new Lease(),
        lease -> Flux.interval(Duration.ofSeconds(1))
                .map(n -> "tick-" + n),
        Lease::close
);
~~~

`Lease` cần cài đặt `close()`; đây là fragment. Resource supplier chạy **khi subscribe** chứ không phải ngay lúc assembly. `Flux.using` đảm nhiệm cleanup đúng lifecycle của publisher.

So với tạo lease ngoài publisher:

~~~java
Lease lease = new Lease();
Flux<String> events = Flux.interval(Duration.ofSeconds(1))
        .doFinally(signal -> lease.close());
~~~

cách thứ hai tạo resource **ngay khi method được gọi**. Nếu publisher không được subscribe, lease có thể rò. `using` scope resource theo subscription đúng hơn.

### 6.2. Mono.using

Với tài nguyên có close đồng bộ:

~~~java
Mono<String> result = Mono.using(
        () -> new LocalHandle(),
        handle -> Mono.fromSupplier(handle::read),
        LocalHandle::close
);
~~~

Fragment này giả định `LocalHandle` có `read()` và `close()` không ném checked exception hoặc được xử lý đúng. Nếu read là blocking I/O, bản thân `Mono.fromSupplier` **không tự biến nó thành non-blocking**. Cần offload phù hợp hoặc dùng API reactive.

### 6.3. usingWhen — asynchronous resource

Với R2DBC transaction, reactive session hoặc resource cần close publisher:

~~~java
Mono<Result> result = Mono.usingWhen(
        connectionFactory.create(),
        connection -> executeQuery(connection),
        connection -> connection.close(),
        (connection, error) -> connection.close(),
        connection -> connection.close()
);
~~~

Đây là fragment tổng quát, trong đó `executeQuery(connection)` trả publisher phù hợp và `Result` do ứng dụng định nghĩa. Các cleanup functions lần lượt là:

1. **asyncComplete:** release khi source hoàn thành thành công.
2. **asyncError:** release khi source error (có cả Throwable).
3. **asyncCancel:** release khi subscription bị cancel.

Nếu resource close cũng có thể error, cần xác định cách xử lý lỗi cleanup. Đừng coi close error là “không quan trọng” nếu nó khiến pool cạn kiệt; log/metrics và recovery policy phải rõ, đồng thời tránh tiết lộ secrets.

### 6.4. Sự khác nhau giữa doFinally và usingWhen

- `doFinally`: side-effect callback đồng bộ, hữu ích cho logs/metrics/small release action.
- `using`: resource lifecycle có cleanup đồng bộ, thiết kế theo subscriber.
- `usingWhen`: cleanup **reactive/asynchronous**, có thể theo dõi Publisher đóng resource, phân nhánh complete/error/cancel.
- `doOnCancel`: bổ sung tín hiệu hủy, không đủ để giải phóng resource trên success/error.

Nếu cleanup là một network operation bất đồng bộ, một `doFinally(() -> resource.close().subscribe())` là **anti-pattern** phổ biến: bạn tự tạo fire-and-forget subscription, lỗi close không được gắn với lifecycle và khó test. Dùng `usingWhen` hoặc transactional reactive API.

### 6.5. Cleanup đúng một lần

Ở một pipeline, operator `using`/`usingWhen` quản lý resource theo subscription. Tuy nhiên nếu tài nguyên còn được chia sẻ bởi external callback, scheduler hay shutdown hook, bản thân chúng vẫn có thể gọi close cạnh tranh. Dùng state guard (ví dụ `AtomicBoolean.compareAndSet(false,true)`) nếu close không idempotent.

Đừng double-close bằng cách vừa `Flux.using(..., Lease::close)` vừa thêm `doFinally(sig -> lease.close())` không được bảo vệ, và đừng giữ một global singleton resource trong using rồi đóng nó mỗi subscriber nếu resource đó dùng chung toàn ứng dụng.

## 7. Backpressure, discard và pooled DataBuffer

### 7.1. Backpressure là phần của lifecycle

Reactive Streams cho phép subscriber yêu cầu số lượng phần tử `request(n)`. Nếu client chậm hoặc subscriber cancel, những phần tử đã được buffer/prefetch có thể bị discard. Các operators như `flatMap`, `publishOn`, `buffer`, `onBackpressureBuffer` có semantics và chi phí lưu trữ khác nhau.

Một `Flux.interval` nếu downstream không yêu cầu đủ có thể phát lỗi overflow thay vì tự chờ vô hạn. Không coi interval làm nguồn backpressure-safe cho mọi tải.

Chọn chính sách:

- Bounded queues, giới hạn per client.
- `onBackpressureLatest()` hoặc sample/throttle khi bài toán chỉ cần “giá trị mới nhất”.
- `limitRate`/prefetch tuning khi phù hợp.
- Durable storage/replay nếu mọi event cần được bảo toàn.
- Cancellation propagation tới producer thật, không chỉ dừng phần encode response.

### 7.2. DataBuffer có thể giữ pooled native memory

Trong Spring WebFlux, `DataBuffer` abstraction có implementations dùng pooled/reference-counted buffers như Netty `ByteBuf`. Khi ứng dụng tự giữ, cắt, bỏ hoặc chuyển buffers ngoài contract của codec, bạn có thể phải **release** những buffers mình sở hữu.

Ví dụ:

~~~java
DataBufferUtils.release(buffer);
~~~

Không áp dụng mù quáng khi framework vẫn sở hữu buffer và sẽ tự release; double release có thể gây lỗi. Chỉ release khi lifecycle ownership rõ.

### 7.3. DoOnDiscard với buffered elements

Một số operators có thể discard phần tử khi cancel/filter/overflow. Nếu ứng dụng đang xử lý pooled DataBuffer do mình sở hữu:

~~~java
Flux<DataBuffer> data = source
        .filter(this::shouldKeep)
        .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
~~~

Đây là fragment: `source` là Flux<DataBuffer>, `shouldKeep` do ứng dụng định nghĩa; sự đúng đắn phụ thuộc operator placement và ownership. `doOnDiscard` chỉ giúp ở những operators hỗ trợ discard hooks, **không giải quyết tất cả đường rò bộ nhớ**. Đọc Javadoc từng operator và lập kế hoạch release cho retry, split, cancel, buffer/error paths.

### 7.4. Không tự decode DataBuffer bằng cách giữ raw ByteBuf vô hạn

Nếu có thể, dùng `bodyToMono(Dto.class)`, `bodyToFlux(Dto.class)`, codecs hoặc `DataBufferUtils.write/read` với lifecycle rõ. Chỉ thao tác raw DataBuffer khi thực sự cần streaming binary/protocol và đã có tests memory/resource cleanup.

Một `OutOfMemoryError: Direct buffer memory` có thể đến từ buffer leaks, backpressure không phù hợp, request body quá lớn hoặc network pipeline không được giới hạn. Đừng kết luận một nguyên nhân khi chưa có metrics/profiling.

## 8. WebClient: tiêu thụ và release HTTP response body

### 8.1. retrieve thường là điểm bắt đầu tốt

~~~java
Mono<ProductDto> product = webClient.get()
        .uri("/products/{id}", id)
        .retrieve()
        .bodyToMono(ProductDto.class);
~~~

Mặc định `retrieve()` biến 4xx/5xx thành `WebClientResponseException` theo WebClient semantics (trừ khi bạn tùy chỉnh). Với DTO decoding, framework quản lý phần lớn lifecycle của response body.

Đừng gọi `block()` trên WebFlux event loop; return `Mono<ProductDto>` hoặc compose tiếp.

### 8.2. exchangeToMono/exchangeToFlux

Cần điều khiển status theo response:

~~~java
Mono<ProductDto> product = webClient.get()
        .uri("/products/{id}", id)
        .exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(ProductDto.class);
            }
            if (response.statusCode().value() == 404) {
                return Mono.empty();
            }
            return response.createError();
        });
~~~

Theo tài liệu Spring Framework 6.2, sau khi Publisher mà `exchangeToMono`/`exchangeToFlux` trả về hoàn tất, response body **nếu chưa được tiêu thụ** sẽ được framework kiểm tra/release, giúp tránh leaks. Trong fragment trên, status 404 bỏ qua body có chủ đích; method `exchangeToMono` hỗ trợ release phần còn lại theo contract.

**Không decode body ở downstream sau khi exchangeToMono callback đã kết thúc**: body có thể đã được release. Nếu cần error content, hãy decode nó **bên trong response function**.

### 8.3. Tại sao exchange() cũ nguy hiểm?

API thấp `exchange()` đã bị deprecate vì code dễ quên tiêu thụ/release body ở một nhánh, khiến connection/memory leak. Tránh viết:

~~~java
// Anti-pattern với API exchange() legacy:
return webClient.get().uri("/resource")
        .exchange()
        .flatMap(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return Mono.just("ok"); // Bỏ body chưa xử lý!
            }
            return Mono.just("error");
        });
~~~

Ưu tiên `retrieve()` hoặc `exchangeToMono`/`exchangeToFlux`. Nếu dùng `ClientResponse` thủ công ở API thấp hơn, phải hiểu `bodyToMono`/`bodyToFlux`, `releaseBody()` và decoding pathways.

### 8.4. Timeout và WebClient errors

Các nguồn lỗi khác nhau:

| Lỗi | Gợi ý kiểm |
| --- | --- |
| DNS/connect failure | Host, proxy, connect timeout, network |
| HTTP 404/429/500/503 | WebClientResponseException/status policy |
| Reactor timeout | Operator timeout và cancellation propagation |
| Response decode error | Content-Type/body shape/max-in-memory-size |
| Client cancel | Downstream không cần response; không nhất thiết onError |
| Pool exhaustion | Connection leak, pool size, slow upstream, unconsumed body |
| OutOfMemoryError/DirectMemory | Pooled buffer ownership, unbounded buffering |

Đừng biến tất cả thành HTTP 500 "unknown" không phân loại. Cũng không đưa raw upstream URL với query tokens và response body chứa secrets vào public error payload.

## 9. HTTP exception handling trong WebFlux

### 9.1. Controller-level và global advice

WebFlux hỗ trợ `@ExceptionHandler` tại controller hoặc `@RestControllerAdvice`. Với HTTP APIs, dùng `ProblemDetail`/`ErrorResponse` để chuẩn hóa lỗi.

Ví dụ fragment:

~~~java
@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(UpstreamTimeoutException.class)
    ResponseEntity<ProblemDetail> timeout(
            UpstreamTimeoutException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.GATEWAY_TIMEOUT,
                "Upstream service did not respond in time");
        body.setTitle("Gateway timeout");
        body.setProperty("code", "UPSTREAM_TIMEOUT");
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(body);
    }
}
~~~

Đây là fragment; class exception/imports và application config phải tồn tại. `ProblemDetail` cung cấp HTTP status/title/detail/type/instance; extension properties có thể được serialize cùng JSON theo hỗ trợ Spring/Jackson.

### 9.2. WebExceptionHandler là lớp thấp hơn

`WebExceptionHandler` thuộc WebFlux processing chain, xử lý exceptions từ `WebFilter`/target `WebHandler`. Với API controller thông thường, ưu tiên `@RestControllerAdvice` và WebFlux default exception handling trước khi tự cài handler cấp thấp.

Một `WebExceptionHandler` tùy chỉnh có thể tạo thứ tự xử lý phức tạp và rủi ro khi response đã committed. Nếu đã committed, không thể tự reset status/headers như chưa có bytes nào được gửi.

### 9.3. 400/404/409/429/503/504/500

Đừng map mọi lỗi reactive thành 500. Một gợi ý:

| Điều kiện | HTTP status thường phù hợp | Lưu ý |
| --- | --- | --- |
| Request input sai | 400 | Validation, unsupported/missing parameters |
| Resource không tồn tại | 404 | Không lộ sự tồn tại tài nguyên forbidden |
| Business conflict/version mismatch | 409 hoặc 412 | Tùy contract |
| Rate limit | 429 | Có thể kèm Retry-After nếu phù hợp |
| Service tạm ngừng/overload | 503 | Có thể kèm retry policy |
| Gateway/upstream timeout | 504 | Khi ứng dụng đóng vai gateway tới upstream |
| Internal bug không xác định | 500 | Generic message, log correlation ID nội bộ |
| Subscriber cancel/client disconnected | **Không phải một HTTP status tự phát** | Không giả vờ đã trả 499/500 nếu client đã rời đi |

Một app gọi remote dependency mà timeout không nhất thiết luôn có status 504; đó là lựa chọn API contract. Cần phân biệt upstream timeout với **client** ngừng chờ. Lưu ý `499` trong logs Nginx là mã riêng proxy, không phải HTTP status chuẩn để WebFlux “trả sau cancel”.

### 9.4. Không lộ thông tin nhạy cảm

Error payload **không** bao gồm:

- Stack trace, class/method names nội bộ.
- Database URL, SQL, credentials, tokens, cookies.
- Raw upstream error body có thể chứa PII hoặc secret.
- File system paths, hostnames nội bộ, provider access keys.
- Arbitrary exception message có input người dùng không được sanitize.

Dùng mã lỗi ổn định và request/trace ID trong logs. Nếu client cần mã đối chiếu, expose một header được server tạo và hỗ trợ CORS nếu cross-origin. Không tin unvalidated request correlation ID để xây log fields vô hạn.

### 9.5. Khi HTTP response đã committed

Trong streaming SSE hoặc response body chunked:

~~~text
HTTP/1.1 200 OK
Content-Type: text/event-stream

event: update
data: {"progress":20}
~~~

nếu publisher gặp error sau khi headers/body bắt đầu, application **không thể đổi 200 thành 500 JSON** trong cùng HTTP response. Error handler chỉ có thể thay status nếu response chưa committed. Khi stream đã bắt đầu:

- Nếu kênh còn usable, có thể map business failure thành **SSE application error event** ngay trong stream protocol.
- Nếu transport/write đã lỗi, chỉ stop/cancel/release resources, log/metrics; không cố write một HTTP response thứ hai.
- Error publisher trong streaming không tự trở thành JSON ProblemDetail sau commit; connection có thể bị đóng và client xử lý reconnect/error.

## 10. Retry đúng cách và hủy subscription

### 10.1. retryWhen chỉ cho transient error

~~~java
return webClient.get()
        .uri("/catalog/{id}", id)
        .retrieve()
        .bodyToMono(CatalogDto.class)
        .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                .filter(this::isTransientFailure));
~~~

Fragment với `isTransientFailure` do ứng dụng định nghĩa. `retryWhen` **resubscribe** nguồn. Nếu nguồn có side effect như POST payment, retry có thể tạo giao dịch trùng nếu không có idempotency key/transaction deduplication.

Không retry:

- 400 validation error.
- 401/403 không có logic refresh token đã được thiết kế.
- 404 khi resource thật sự không tồn tại.
- Business conflict không thể tự sửa.
- Non-idempotent commands không có idempotency safeguards.

Có thể retry một số network resets/429/503 sau khi xét rate limit, jitter, caps và retry budget. Backoff tránh retry storm khi upstream đang quá tải.

### 10.2. Retry và timeout order

Hai cách sau **khác nhau**:

~~~java
remoteCall()
    .timeout(Duration.ofSeconds(1))
    .retryWhen(Retry.max(2));
~~~

Mỗi subscription attempt có timeout 1 giây. Trong khi:

~~~java
remoteCall()
    .retryWhen(Retry.max(2))
    .timeout(Duration.ofSeconds(1));
~~~

Một deadline 1 giây bao quanh cả sequence retries. Cần thiết kế **per-attempt timeout và overall budget** có chủ đích; không chỉ đổi thứ tự operators cho code “đẹp”.

### 10.3. Retry không phải tiếp tục nguồn cũ

Nếu một `Flux` đã phát 5 records rồi error, retry subscription có thể phát lại 5 records đó. Consumer phải hỗ trợ idempotency/deduplication hoặc dùng checkpoint/cursor để resume. Đặc biệt nguy hiểm với transactional writes hoặc event-stream processing.

### 10.4. Hủy sau retry/backoff

Khi client cancel trong lúc chờ backoff, subscription quản lý bởi Reactor sẽ dừng việc phát thêm kết quả/retry scheduling theo semantics operator. Tuy nhiên không được giả định mọi side effect ngoài tiến trình đã được rollback. Với các tasks tự tạo ở service, phải tự gắn ownership/cancellation vào publisher lifecycle.

## 11. SSE và client disconnect trong WebFlux

Một WebFlux endpoint có thể trả:

~~~java
@GetMapping(path = "/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<ServerSentEvent<String>> events() {
    return Flux.interval(Duration.ofSeconds(1))
            .map(n -> ServerSentEvent.<String>builder()
                    .id(Long.toString(n))
                    .event("tick")
                    .data("tick-" + n)
                    .build())
            .doFinally(signal -> log.info(
                    "sse_terminated signal={}", signal));
}
~~~

Đây là fragment có `log`, imports cần thiết. `Flux.interval` là nguồn phát theo scheduler; khi downstream cancel, subscription có thể dừng timer. Nếu browser closes SSE, WebFlux network handler thường propagates cancel, nhưng detection timing còn phụ thuộc network và proxy.

### 11.1. SSE heartbeat

SSE comment có thể dùng làm heartbeat:

~~~text
: keepalive

~~~

Trong Spring WebFlux, builder:

~~~java
ServerSentEvent.<String>builder()
        .comment("keepalive")
        .build();
~~~

Comment-only SSE thường không trigger application message event trong browser, nhưng tạo bytes giúp proxy không xem connection idle. Nếu gateway buffer response, heartbeat có thể không tới client kịp. Test thật qua proxy với buffering disabled nếu cần.

### 11.2. Last-Event-ID và replay

Browser EventSource có reconnect, và có thể gửi `Last-Event-ID` nếu stream dùng event ID. Muốn resume không mất events, server cần lưu durable event IDs/retention và đọc lại từ cursor. Nếu stream chỉ tạo `Flux.interval` in-memory, **không được hứa exactly-once delivery**. Reconnect có thể tạo subscription mới và mất/nhận lại events tùy thiết kế.

### 11.3. Hot publisher chia sẻ nhiều clients

Ví dụ dùng `Sinks.Many<T>` làm producer toàn ứng dụng. Khi client A cancel, không được tự đóng Sink làm client B mất dữ liệu. Dọn **per-client subscription** hoặc resource riêng; shared producer có lifecycle ứng dụng độc lập. Trên nguồn hot, need to distinguish subscriber cancellation vs producer shutdown.

## 12. Dự án chạy được: Spring Boot 3.5, Java 17

Demo tập trung vào:

- `GET /api/items/ok`: trả JSON bình thường.
- `GET /api/items/slow`: Mono chậm, Reactor timeout 150ms, map thành custom exception rồi trả ProblemDetail HTTP 504.
- `GET /api/items/missing`: trả HTTP 404 qua ResponseStatusException.
- `GET /api/items/boom`: lỗi internal được advice map thành 500 generic, không lộ exception message.
- `GET /api/events`: SSE streaming dùng `Flux.using` với per-subscription lease và `doFinally`, dọn cả complete/error/cancel.
- `GET /api/active`: demo chỉ đếm active SSE subscriptions.
- Tests dùng WebTestClient và StepVerifier để kiểm HTTP error body, timeout, cancel/cleanup, virtual time.

### 12.1. Cấu trúc

~~~text
webflux-lifecycle-demo/
├── pom.xml
└── src/
    ├── main/
    │   └── java/demo/WebFluxLifecycleApplication.java
    └── test/
        └── java/demo/WebFluxLifecycleTest.java
~~~

### 12.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>webflux-lifecycle-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
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

**Không thêm `spring-boot-starter-web` vào demo.** Nếu cả MVC và WebFlux starters tồn tại, Spring Boot thường chọn MVC application type mặc định, khiến bài kiểm thử reactive stack sai mục tiêu.

### 12.3. WebFluxLifecycleApplication.java

Lưu toàn bộ Java sau vào `src/main/java/demo/WebFluxLifecycleApplication.java`. Chỉ có một public top-level class, những class/records còn lại package-private. Demo không gọi dịch vụ ngoài Internet, chạy độc lập khi có dependencies Maven.

~~~java
package demo;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class WebFluxLifecycleApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebFluxLifecycleApplication.class, args);
    }
}

record Item(String id, String title) {
}

class ApiTimeoutException extends RuntimeException {
    ApiTimeoutException(Throwable cause) {
        super("The dependency timed out", cause);
    }
}

@Service
class ItemService {
    Mono<Item> find(String id) {
        return Mono.defer(() -> {
            if ("missing".equals(id)) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not found"));
            }
            if ("boom".equals(id)) {
                return Mono.error(new IllegalStateException(
                        "internal database password=secret"));
            }

            Mono<Item> source = "slow".equals(id)
                    ? Mono.delay(Duration.ofSeconds(3))
                            .map(n -> new Item(id, "Late"))
                    : Mono.just(new Item(id, "Ready"));

            return source
                    .timeout(Duration.ofMillis(150))
                    .onErrorMap(TimeoutException.class,
                            ApiTimeoutException::new);
        });
    }
}

@Service
class EventService {
    private static final Logger LOG =
            LoggerFactory.getLogger(EventService.class);

    private final AtomicInteger active = new AtomicInteger();

    Flux<ServerSentEvent<String>> events() {
        return Flux.using(
                () -> new Lease(active),
                lease -> Flux.interval(Duration.ofMillis(100))
                        .map(n -> ServerSentEvent.<String>builder()
                                .id(Long.toString(n))
                                .event("tick")
                                .data("tick-" + n)
                                .build()),
                Lease::close
        ).doFinally(signal ->
                LOG.info("sse_terminated reason={}", signal));
    }

    int activeConnections() {
        return active.get();
    }

    private static final class Lease implements AutoCloseable {
        private final AtomicInteger active;
        private final AtomicBoolean closed = new AtomicBoolean();

        Lease(AtomicInteger active) {
            this.active = active;
            active.incrementAndGet();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                active.decrementAndGet();
            }
        }
    }
}

@RestController
class ApiController {
    private final ItemService items;
    private final EventService events;

    ApiController(ItemService items, EventService events) {
        this.items = items;
        this.events = events;
    }

    @GetMapping("/api/items/{id}")
    Mono<Item> item(@PathVariable String id) {
        return items.find(id);
    }

    @GetMapping(path = "/api/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> stream() {
        return events.events();
    }

    @GetMapping("/api/active")
    Mono<Integer> active() {
        return Mono.fromSupplier(events::activeConnections);
    }
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiTimeoutException.class)
    ResponseEntity<ProblemDetail> timeout(ApiTimeoutException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.GATEWAY_TIMEOUT,
                "Upstream did not respond in time");
        body.setTitle("Gateway timeout");
        body.setProperty("code", "UPSTREAM_TIMEOUT");
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> internal(IllegalStateException ex) {
        // Không gửi ex.getMessage() cho người dùng.
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        body.setTitle("Internal server error");
        body.setProperty("code", "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
}
~~~

### 12.4. Những chi tiết thiết kế quan trọng

1. `Mono.defer` chỉ tạo publisher theo từng subscription, không khởi động tác vụ ngay ở thời điểm assembly.
2. `slow` dùng một source phát sau 3 giây, nhưng `timeout(150ms)` phát error; `onErrorMap` chuyển sang exception rõ nghiệp vụ.
3. HTTP 504 là **policy cho tình huống demo khi API đóng vai upstream gateway**. Trong ứng dụng khác có thể chọn 503 hoặc lỗi phù hợp hơn.
4. `boom` chứa message nội bộ để chứng minh advice trả message chung, không xuất secret.
5. `events()` tạo `Lease` bằng `Flux.using` **khi subscribe**; cleanup chạy khi downstream complete/error/cancel.
6. `AtomicBoolean` trong Lease bảo vệ double-close nếu ownership mở rộng hoặc external callback gọi close thêm.
7. `Flux.interval` có nguồn vô hạn; browser disconnect/cancel sẽ dừng per-subscription timer theo Reactor semantics thông thường.
8. `doFinally` chỉ log reason; cleanup thật nằm trong `using` để bảo đảm resource ownership.
9. `activeConnections` không phải metric security-grade; endpoint demo phải được bảo vệ/loại bỏ trong production.
10. Demo không có auth/CSRF, không tích hợp upstream thật, không có streaming buffers; ưu tiên dạy đúng lifecycle trước.

### 12.5. Chạy

~~~bash
mvn spring-boot:run
~~~

Trong terminal khác:

~~~bash
curl -i http://localhost:8080/api/items/ok
~~~

Response tương đương:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":"ok","title":"Ready"}
~~~

Thử timeout:

~~~bash
curl -i http://localhost:8080/api/items/slow
~~~

Status 504 và JSON ProblemDetail tương đương:

~~~json
{
  "type":"about:blank",
  "title":"Gateway timeout",
  "status":504,
  "detail":"Upstream did not respond in time",
  "code":"UPSTREAM_TIMEOUT"
}
~~~

Response thực tế có thể bổ sung `instance` và headers; JSON field order không cố định.

Thử lỗi nội bộ:

~~~bash
curl -i http://localhost:8080/api/items/boom
~~~

Kỳ vọng 500, body chung không có chuỗi `password=secret`. Thử `missing` nhận HTTP 404.

### 12.6. Mở SSE và hủy client

~~~bash
curl -N -i http://localhost:8080/api/events
~~~

Ví dụ đầu stream:

~~~text
id:0
event:tick
data:tick-0

id:1
event:tick
data:tick-1

~~~

SSE formatter thực tế có thể khác thứ tự fields. Ở terminal khác:

~~~bash
curl -s http://localhost:8080/api/active
~~~

Nếu có một subscription, JSON có thể là:

~~~json
1
~~~

Sau Ctrl+C ở curl, cancel có thể lan về WebFlux Publisher và `Flux.using` đóng lease; active trở về 0 khi server **quan sát được** connection close/cancellation. Thời điểm không tuyệt đối vì TCP/proxy buffering.

## 13. Kiểm thử: WebTestClient và StepVerifier

Lưu `src/test/java/demo/WebFluxLifecycleTest.java`:

~~~java
package demo;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WebFluxTest(controllers = ApiController.class)
@Import({ItemService.class, EventService.class, ApiErrors.class})
class WebFluxLifecycleTest {
    @Autowired
    WebTestClient http;

    @Autowired
    EventService events;

    @Test
    void normalItemIs200() {
        http.get().uri("/api/items/ok").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo("ok")
            .jsonPath("$.title").isEqualTo("Ready");
    }

    @Test
    void missingItemIs404() {
        http.get().uri("/api/items/missing").exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void slowItemIsMappedTo504WithSafeBody() {
        http.get().uri("/api/items/slow").exchange()
            .expectStatus().isEqualTo(504)
            .expectBody()
            .jsonPath("$.code").isEqualTo("UPSTREAM_TIMEOUT")
            .jsonPath("$.detail").isEqualTo(
                    "Upstream did not respond in time");
    }

    @Test
    void internalErrorDoesNotExposeSecret() {
        http.get().uri("/api/items/boom").exchange()
            .expectStatus().is5xxServerError()
            .expectBody()
            .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
            .jsonPath("$.detail").isEqualTo(
                    "An unexpected error occurred")
            .jsonPath("$.detail").value(value ->
                    assertTrue(!String.valueOf(value)
                            .contains("password=secret")));
    }

    @Test
    void takeOneCancelsUpstreamAndCleansLease() {
        int before = events.activeConnections();

        StepVerifier.create(events.events().take(1))
            .expectNextMatches(event ->
                    "tick".equals(event.event())
                    && "tick-0".equals(event.data()))
            .verifyComplete();

        assertEquals(before, events.activeConnections());
    }

    @Test
    void timeoutCancelsUpstreamSource() {
        AtomicBoolean cancelled = new AtomicBoolean();

        StepVerifier.withVirtualTime(() ->
                Mono.<String>never()
                    .doOnCancel(() -> cancelled.set(true))
                    .timeout(Duration.ofSeconds(2)))
            .expectSubscription()
            .thenAwait(Duration.ofSeconds(2))
            .expectError(TimeoutException.class)
            .verify();

        assertTrue(cancelled.get());
    }

    @Test
    void onErrorResumeProvidesExplicitFallback() {
        StepVerifier.create(
                Mono.<String>error(
                        new IllegalStateException("temporary"))
                    .onErrorResume(IllegalStateException.class,
                            e -> Mono.just("fallback")))
            .expectNext("fallback")
            .verifyComplete();
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

**Ý nghĩa từng nhóm test:**

- WebTestClient kiểm HTTP response status/body của WebFlux controller và advice.
- StepVerifier kiểm cancellation/lease cleanup khi downstream `take(1)`, không dựa vào TCP socket giả.
- StepVerifier virtual time kiểm timeout/cancellation không chờ hai giây thật.
- OnErrorResume test chứng minh fallback là một **publisher thay thế**.
- Một test real-client-disconnect phải chạy qua embedded Netty/staging và đóng socket khi server đang ghi; `WebTestClient` slice tests không thay thế kiểm thử network/proxy.

**Hạn chế:** Test cleanup chịu điều kiện implementation của `Flux.using`. Nếu refactor sang shared/hot publisher, phải sửa ownership test. Test thời gian thực 150ms phụ thuộc scheduler của môi trường; có thể tăng test-specific timeout nếu CI cực chậm, nhưng không bỏ phân biệt error/cancel.

## 14. Kiểm thử tài nguyên bất đồng bộ và release

### 14.1. Test resource cleanup trên cả ba đường

Với `usingWhen`, thiết kế test:

~~~text
Case A: source emits value + completes
  -> asyncComplete runs
Case B: source emits error
  -> asyncError runs
Case C: downstream cancels subscription
  -> asyncCancel runs
~~~

Dùng counters hoặc mocked reactive connection và StepVerifier/PublisherProbe. Nếu cleanup Publisher của `usingWhen` cũng lỗi, phải quyết định liệu primary error bị thay thế/suppressed theo Reactor API và ghi log/metric chính xác.

### 14.2. Không dùng doFinally để subscribe fire-and-forget close

Sai:

~~~java
.doFinally(signal -> connection.close().subscribe())
~~~

Nếu `close()` trả Mono<Void>, đoạn này tự tạo một subscription ngoài ownership, khó xử lý close error và có thể không được test. Thay bằng `usingWhen` để close Publisher được framework đợi/quan sát theo contract.

### 14.3. Kiểm DataBuffer release

Khi code sở hữu pooled buffers, tests nên có path:

- Request complete bình thường, tất cả DataBuffers đã consumed/released.
- Cancel ở giữa stream.
- Downstream codec error.
- Filtering/drop/skip/prefetch discard.
- Concurrent subscribers (nếu source được chia sẻ).
- Bounded memory under slow reader.

Tránh tự tạo unit test “release hai lần cũng không sao”. Reference-counted buffers có thể báo lỗi khi double-release hoặc access after release. Không tái sử dụng DataBuffer đã được downstream release.

## 15. Phân biệt WebFlux cancellation với MVC Servlet disconnect

| Chủ đề | Spring WebFlux | Spring MVC |
| --- | --- | --- |
| Main model | Reactive Streams, non-blocking pipeline | Servlet stack; async processing dựa Servlet |
| Streaming return | `Flux<ServerSentEvent<T>>`, `Flux<DataBuffer>` | `SseEmitter`, `ResponseBodyEmitter`, `StreamingResponseBody` |
| Client cancel | Cancellation thường propagate vào subscription | Container async error/write failure callbacks |
| Cleanup | `using/usingWhen/doFinally/doOnCancel` | `onCompletion/onTimeout/onError` và resource owner |
| Timeout | Reactor `timeout`, Netty/HTTP/gateway timeouts | Servlet async timeout, emitter timeout, proxy |
| Error status sau commit | Không thể đổi HTTP status đã gửi | Cùng giới hạn HTTP |
| Blocking I/O | Tránh trên event loop | Có thể chạy ở worker nhưng vẫn tiêu tốn threads |
| Backpressure | Reactive Streams request/cancel | Phụ thuộc async write/callback và I/O behavior |

**Không dùng `SseEmitter.completeWithError` trong WebFlux** vì đó là Servlet MVC API. Tương tự, không coi việc WebFlux subscriber cancel giống hệt MVC `onCompletion` callback.

## 16. Các lỗi phổ biến cần tránh

1. **Coi CANCEL là onError.** `onErrorResume` không xử lý subscriber cancel; đặt cleanup trong `using/usingWhen/doFinally` theo ownership.
2. **Dùng doOnError để đóng resource.** Bỏ sót normal completion và cancellation.
3. **Fire-and-forget subscribe() để close resource.** Mất liên kết lifecycle/close errors.
4. **Tạo resource ngoài Mono.defer/using.** Nếu Publisher không được subscribe có thể leak.
5. **Đóng shared producer khi một client unsubscribe.** Ảnh hưởng các clients khác.
6. **retryWhen cho POST payment không idempotent.** Gây thao tác trùng.
7. **retry mọi lỗi 4xx và bug logic.** Làm lỗi nặng hơn và tăng tải.
8. **Dùng timeout mà không biết cancel propagation.** Background/side effects có thể vẫn chạy.
9. **Giữ DataBuffer pooled sau khi framework release.** Access-after-release/memory safety.
10. **Quên release body của WebClient exchange() cũ.** Connection pool/direct memory leak.
11. **Đưa response body raw vào error log/public error.** Lộ thông tin.
12. **Bắt Throwable rồi onErrorComplete vô điều kiện.** Che bug và tạo dữ liệu thiếu.
13. **Cố đổi status/headers khi stream committed.** Không thể phát response 500 mới.
14. **Dùng block() hoặc Thread.sleep() trên event loop.** Nghẽn toàn bộ reactive server.
15. **Lưu unbounded queue cho SSE clients chậm.** OOM khi mạng yếu.
16. **Dùng userId/requestId làm metric labels.** Cardinality bùng nổ.
17. **Không giới hạn concurrent connections và per-tenant subscriptions.** Cạn tài nguyên.
18. **Cho rằng cancel giúp rollback remote side effect.** Không có đảm bảo.
19. **Suy từ HTTP 499 là response chuẩn server phát.** Đây là ký hiệu gateway riêng.
20. **Chỉ kiểm HTTP status mà không test cleanup.** Dễ leak khi timeout/cancel xảy ra.
21. **Cho rằng successful send luôn tới client.** Proxy/TCP buffers có thể làm observation chậm.
22. **Chỉ dùng MockWebServer/slice tests để khẳng định socket disconnect semantics.** Cần integration test thật nếu high stakes.

## 17. Logging và observability

Nên phân biệt terminal signals:

~~~text
request_completed   status=200 result=success
request_failed      status=504 errorCategory=UPSTREAM_TIMEOUT
stream_cancelled    endpoint=/api/events reason=client-or-downstream-cancel
stream_terminated   endpoint=/api/events signal=CANCEL lifetimeMs=...
resource_released   type=reactive-lease outcome=closed
~~~

Các logs trên là **ví dụ**; không suy chính xác client đóng tab chỉ từ `SignalType.CANCEL`. Một `take(1)` hợp lệ cũng có thể cancel upstream.

Metrics có ích:

- HTTP status và latency theo route template.
- Upstream timeout/circuit-breaker rates.
- Active reactive subscriptions/SSE sessions.
- Cancellation count theo endpoint và finite reason groups.
- Resource lease active/acquired/released, cleanup failures.
- Netty connection pool active/idle/pending acquire.
- DataBuffer/direct memory và allocator metrics.
- Retry attempts, fallback rates, backpressure overflow.
- Event-loop saturation/blocked operations.
- Gateway client resets/read timeouts.

Không đưa token, cookie, raw body, stacktrace và PII vào response hoặc high-cardinality metrics. Correlation ID/trace ID phục vụ tìm logs, nhưng không tự là identity/authentication proof.

## 18. Checklist production

- [ ] Đã phân loại complete, error, cancel riêng trong lifecycle và metrics.
- [ ] Lỗi được map theo domain và HTTP status phù hợp, không rò exception internals.
- [ ] onErrorResume/onErrorComplete chỉ dùng khi fallback/suppression đúng contract.
- [ ] Retry có giới hạn, idempotency, jitter/backoff và overall deadline budget.
- [ ] Timeout operator được đặt đúng vị trí so với retry và recovery.
- [ ] Cancellation được propagate tới nguồn; external blocking side effects có cancellation riêng hoặc có chủ đích.
- [ ] Synchronous resources dùng using/try-with-resources đúng ownership; async resources dùng usingWhen hoặc reactive transaction API.
- [ ] Cleanup complete/error/cancel có tests và không double-close shared resource.
- [ ] WebClient response body được consume/release, không còn exchange() legacy dễ rò.
- [ ] Raw DataBuffers pooled được release khi thuộc quyền sở hữu app, kể cả discard/cancel.
- [ ] Streams dùng bounded queues/backpressure/quota, không tạo publisher vô hạn thiếu kiểm soát.
- [ ] Sau response committed không cố gửi HTTP status/ProblemDetail mới.
- [ ] SSE có reconnect/Last-Event-ID/replay contract và proxy buffering/timeout được kiểm thử.
- [ ] Không chạy blocking JDBC/filesystem calls trên Reactor Netty event-loop.
- [ ] Có WebTestClient, StepVerifier và embedded-network integration tests cho timeout/cancel/cleanup.
- [ ] Active resources/connection pools/cleanup failures được đo và alert.
- [ ] TLS/CORS/authz/cookie/CSRF được thiết kế riêng, không coi cancellation/timeout như biện pháp bảo mật.

## 19. Bài tập tự luyện

**Bài 1 — Ba tín hiệu:** Một `Flux.range(1,10).take(1)` kết thúc sau phần tử đầu. Upstream thấy signal nào? Downstream thấy gì? Vì sao không nên ghi error metric?

**Bài 2 — doFinally:** Viết `Mono.never().timeout(Duration.ofSeconds(2))` với doFinally trước và sau timeout. Dùng StepVerifier virtual time để ghi lại upstream CANCEL và downstream ON_ERROR.

**Bài 3 — Error mapping:** API gọi upstream trả TimeoutException. Dùng onErrorMap thành domain exception, advice trả ProblemDetail 504 với code ổn định và không lộ raw exception message.

**Bài 4 — Cancellation:** Client đóng EventSource đang đọc Flux.interval. Phân tích điều kiện cancel lan upstream, cleanup per subscription và tại sao producer hot dùng chung không nên bị shutdown.

**Bài 5 — Resource:** Viết Flux.using cho resource AutoCloseable. Test complete, error và cancel bằng StepVerifier; xác minh counter acquired == released.

**Bài 6 — Async close:** Một reactive DB session có close() trả Mono<Void>. Hãy dùng usingWhen với asyncComplete, asyncError và asyncCancel thay vì close().subscribe() trong doFinally.

**Bài 7 — WebClient:** Chuyển WebClient .exchange() cũ sang exchangeToMono, giải thích release body 404 và path lỗi. Nếu cần body lỗi, decode ở đâu?

**Bài 8 — DataBuffer:** Một Flux<DataBuffer> bị filter và discard khi cancel. Ai sở hữu buffer, khi nào dùng DataBufferUtils.release/doOnDiscard, và vì sao double release nguy hiểm?

**Bài 9 — Retry:** Thiết kế retry cho GET idempotent với backoff+jitter+deadline. Vì sao copy nguyên chính sách sang POST thanh toán là nguy hiểm?

**Bài 10 — Streaming commit:** SSE đã phát HTTP 200 và ba events rồi lỗi business. Vì sao không thể đổi status thành 500? Thiết kế in-band app-error event nếu stream còn usable.

**Bài 11 — Timeout layers:** Một browser thấy 504 sau 30s dù server Reactor timeout đặt 60s. Những tầng proxy/WebClient cần kiểm?

**Bài 12 — Backpressure:** Producer tạo 1000 events/giây, client đọc 10 events/giây. Chọn bounded queue/drop/coalesce hoặc durable replay cho dữ liệu progress và giao dịch.

**Bài 13 — Test:** Viết StepVerifier xác minh onErrorResume fallback không chạy khi source được cancel, và dùng PublisherProbe hoặc AtomicBoolean kiểm cleanup.

**Bài 14 — Blocking source:** Chuyển một legacy blocking SDK call thành Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic()), giải thích vì sao cancellation không bảo đảm lệnh SDK dừng tức thời.

## 20. Ghi nhớ

- Trong Reactor, **complete, error và cancel không giống nhau**; cancellation là tín hiệu downstream lên upstream, không phải một onError.
- `doFinally` có thể quan sát `ON_COMPLETE`, `ON_ERROR`, `CANCEL`; resource lifecycle nên dùng `using/usingWhen`.
- `timeout` phát TimeoutException downstream và cancel nguồn khi hết hạn, nhưng external operations có thể vẫn thực thi.
- `onErrorResume` tạo fallback Publisher, không phục hồi vị trí đã lỗi trong nguồn ban đầu.
- `retryWhen` resubscribe, có thể phát lại side effects/records; cần idempotency và deadline.
- WebClient `retrieve/exchangeToMono/exchangeToFlux` an toàn hơn `exchange()` cũ nếu không quản lý body release tường minh.
- Pooled `DataBuffer` đòi **ownership rõ** để tránh leak hoặc double release.
- Khi response đã committed, lỗi stream không thể được biến thành HTTP 500 JSON mới; dùng in-band event khi có thể hoặc cancel/cleanup.
- Hủy một subscription không có nghĩa dừng mọi shared producer hay remote side effect.
- Tests cần kiểm **cả dữ liệu/status lẫn release trên cancel/error/timeout**.

## 21. Tài liệu chính thức

- [Spring Framework 6.2 — WebFlux](https://docs.spring.io/spring-framework/reference/6.2/web/webflux.html): reactive stack, backpressure và tổng quan.
- [Spring Framework 6.2 — WebFlux Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/ann-rest-exceptions.html): ProblemDetail/ErrorResponse và controller advice.
- [Spring Framework 6.2 — WebFlux Exceptions](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-exceptions.html): @ExceptionHandler.
- [Spring Framework 6.2 — WebClient exchange](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient/client-exchange.html): exchangeToMono/exchangeToFlux, response body release.
- [Spring Framework 6.2 — Data Buffers and Codecs](https://docs.spring.io/spring-framework/reference/6.2/core/databuffer-codec.html): DataBuffer pooled lifecycle và release.
- [Project Reactor — Error Handling](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html): onErrorResume, onErrorMap, retry, doFinally, using.
- [Project Reactor — Dealing with Objects that Need Cleanup](https://projectreactor.io/docs/core/release/reference/advancedFeatures/cleanup.html): reference counting, discard, cleanup.
- [Reactor Flux API](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html): using, usingWhen, doFinally, timeout, doOnCancel.
- [Reactor StepVerifier](https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html): virtual time, cancellation/termination tests.
- [Reactive Streams 1.0.4 Specification](https://www.reactive-streams.org/reactive-streams-1.0.4-spec-javadoc/org/reactivestreams/Subscription.html): request(n), cancel contract.
- [HTTP Semantics — RFC 9110](https://www.rfc-editor.org/rfc/rfc9110): status, committed HTTP response semantics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-foundations-008">Đánh giá WebFlux theo toàn bộ chuỗi I/O; không mặc định nhanh hơn MVC</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá WebFlux theo toàn bộ chuỗi I/O; không mặc định nhanh hơn MVC

## 1. Mục tiêu bài học

**Spring WebFlux** là web stack reactive của Spring Framework, hỗ trợ mô hình **non-blocking I/O**, `Mono`/`Flux`, Reactive Streams và backpressure. Tuy nhiên, đổi từ Spring MVC sang WebFlux **không mặc định khiến API phản hồi nhanh hơn**. Lợi ích tiềm năng lớn nhất thường là sử dụng ít threads và memory hơn khi phục vụ **nhiều request đồng thời có thời gian chờ I/O**, với điều kiện các đoạn I/O quan trọng trong hệ thống cũng **không chặn event loop**.

Điểm cốt lõi: phải đánh giá **toàn bộ chuỗi xử lý từ HTTP vào đến HTTP ra**, không chỉ nhìn controller trả `Mono<T>`.

Sau bài này, bạn có thể:

1. Phân biệt **latency**, **throughput**, **concurrency**, **resource efficiency**, **p95/p99**, saturation, backpressure và tốc độ tính toán CPU.
2. Hiểu mô hình Servlet MVC (thường thread-per-active-request với blocking I/O) và WebFlux (event-loop + reactive signals), không đơn giản hóa rằng MVC “không hỗ trợ async”.
3. Vẽ và kiểm toán một request end-to-end: **client → gateway → WebFlux handler → HTTP client → database → cache → messaging → serializer → HTTP response**.
4. Nhận diện JDBC/JPA, `RestTemplate`, `Thread.sleep`, `Files.readAllBytes`, `block()`, đồng bộ hóa và client libraries blocking làm suy yếu WebFlux.
5. Giải thích cách dùng **`WebClient`**, **R2DBC**, `Schedulers.boundedElastic()`, Reactor Context, `timeout`, `flatMap(..., concurrency)`, pool/queue bounds và cancellation đúng chỗ.
6. Chạy một ví dụ **Java 17+, Spring Boot 3.x, Spring WebFlux + R2DBC/H2** không dùng JDBC, có HTTP endpoint trả `Mono` từ reactive database và kiểm thử bằng `WebTestClient`.
7. Lập thử nghiệm MVC-vs-WebFlux công bằng, đo concurrency/latency/CPU/memory/threads/GC/errors theo workload, đọc kết quả không thiên lệch.
8. Quyết định **giữ MVC**, **đưa WebClient vào MVC**, hay **chuyển sang WebFlux** dựa trên bằng chứng và khả năng vận hành.

**Phạm vi:** Spring Framework **6.x**, Spring Boot **3.x**, Project Reactor trên Java 17+. Nếu dùng Spring Framework 7/Spring Boot 4 hoặc thư viện driver đời khác, đối chiếu API/version tương ứng. Bài tập trung web server/reactive data I/O; không coi nó là một benchmark đã được chạy trên máy của bạn.

## 2. Kết luận kỹ thuật cần ghi nhớ trước

> **Reactive/non-blocking không tự làm code chạy nhanh hơn.** Nó giúp ứng dụng có khả năng quản lý nhiều thao tác chờ I/O hơn với ít threads hơn, trong đúng loại workload và kiến trúc.

Tài liệu Spring Framework nêu rõ: WebFlux thường **không** làm tăng tốc một request riêng lẻ; đôi khi còn thêm chi phí lập trình reactive và scheduling. WebFlux có thể có lợi khi số request đồng thời cao, remote/database I/O có độ trễ biến động, và toàn bộ các tầng quan trọng đều dùng API phù hợp.

Một REST endpoint trả dữ liệu cố định trong memory và hoàn thành trong 1 ms có thể không cải thiện đáng kể khi đổi sang WebFlux; đổi lại bạn thêm learning curve và khả năng debug phức tạp hơn. Ngược lại, endpoint phải chờ nhiều upstream services 100–500 ms và có hàng nghìn kết nối đồng thời có thể là ứng viên tốt để đo lợi ích non-blocking.

**Không có thứ hạng tuyệt đối:** không kết luận “MVC luôn nhanh hơn” hay “WebFlux luôn nhanh hơn” từ một biểu đồ benchmark đơn lẻ. Hardware, Java version, GC, server connector, database, SSL/TLS, compression, payload size, concurrency, error budget và test tooling có thể đảo ngược kết quả.

## 3. Hiểu đúng các đại lượng hiệu năng

| Khái niệm | Ý nghĩa | Điều dễ nhầm |
| --- | --- | --- |
| Latency | Thời gian từ khi client gửi tới khi có phản hồi hoàn chỉnh hoặc dấu mốc được định nghĩa | Không đồng nghĩa số request mỗi giây |
| p50 | Trung vị latency | Có thể che giấu slow tail |
| p95 / p99 | 95% / 99% mẫu nằm trong hoặc dưới mức tương ứng | Cần đo đủ mẫu và báo cả timeout |
| Throughput | Requests/transactions hoàn thành mỗi giây (RPS/TPS) | Không đánh đồng “request đã gửi” với thành công |
| Concurrency | Số operations/requests đang in-flight | Không đồng nghĩa số threads |
| CPU utilization | Mức sử dụng CPU | Cao có thể là bão hòa, không phải hiệu quả |
| Memory / RSS | Heap, native memory, direct buffers, process resident set | WebFlux vẫn có buffer/netty/native overhead |
| Thread count | Số JVM/worker/event-loop threads | Ít threads không bảo đảm ít latency |
| Saturation | CPU, DB pool, network, queue, downstream đã đạt giới hạn | Không giải quyết bằng tăng concurrency vô hạn |
| Error / timeout rate | Tỷ lệ request thất bại hoặc vượt deadline | Bỏ lỗi khỏi kết quả làm benchmark lệch |
| Backpressure | Cơ chế điều tiết tốc độ dữ liệu theo nhu cầu tiêu thụ | Không tự giới hạn mọi nguồn bên ngoài |

Khi nói “nhanh hơn”, cần xác định **mục tiêu tối ưu**:
- Giảm p99 từ lúc request tới **byte cuối**, dưới tải 200 concurrent clients?
- Tăng **successful RPS** với cùng vCPU/RAM, và p99 không vượt SLA?
- Giảm threads/RSS khi duy trì 5.000 concurrent sockets?
- Giảm CPU cost/request ở cùng tải?
- Giảm chi phí vận hành trên một lượng traffic thực?

Một hệ thống có p50 đẹp nhưng p99 cao, nhiều timeout hoặc memory phình to **không** thể xem đã thắng.

### 3.1. Little's Law là trực giác, không phải phép màu

Ở trạng thái tương đối ổn định, số request đang ở hệ thống xấp xỉ throughput × thời gian ở hệ thống:

~~~text
L ≈ λ × W

L: số requests in-flight (bình quân)
λ: request completion rate (requests/second)
W: response time (seconds)
~~~

Nếu backend có latency I/O dài, số yêu cầu đồng thời tăng dù mỗi request tiêu thụ ít CPU. WebFlux có thể tránh giữ một thread blocking trên toàn bộ thời gian chờ đó — **nếu** I/O thực sự là non-blocking. Nhưng database connections, socket pools, memory và remote service rate limit vẫn là các nguồn lực hữu hạn.

## 4. Mô hình thực thi Spring MVC và WebFlux

### 4.1. Spring MVC / Servlet

Luồng MVC điển hình:

~~~text
Client -> Tomcat/Jetty Servlet connector
       -> Servlet filter / DispatcherServlet
       -> Controller -> Service
       -> JDBC/JPA/HTTP blocking call
       -> Result -> HTTP response
~~~

Với handler đồng bộ, request thread thường **chờ** khi gọi JDBC/HTTP blocking. Servlet container do đó cấu hình pool threads đủ lớn để phục vụ lượng request active.

Tuy nhiên, **MVC không đồng nghĩa mọi I/O đều blocking trong mọi tình huống**:
- Servlet API hiện đại hỗ trợ async request processing.
- MVC có `Callable`, `DeferredResult`, `StreamingResponseBody` và hỗ trợ reactive return types ở mức tích hợp.
- Một service MVC có thể dùng `WebClient` để gọi upstream non-blocking rồi trả reactive type.
- MVC có thể vận hành hiệu quả và đơn giản hơn nếu hệ thống phụ thuộc JDBC/JPA hoặc xử lý nghiệp vụ imperative.

Không nên lấy một MVC controller viết `Thread.sleep(500)`, so với WebFlux `Mono.delay(Duration.ofMillis(500))`, rồi kết luận WebFlux nhanh hơn dưới tải: hai đoạn có **ngữ nghĩa I/O khác nhau**, một bên block thread thật, bên kia tạo timer không block, không mô phỏng cùng downstream resource.

### 4.2. Spring WebFlux / Reactor Netty

Luồng WebFlux điển hình khi dùng Netty:

~~~text
Client -> Reactor Netty event loop (HTTP non-blocking)
       -> DispatcherHandler / WebFlux controller
       -> Publisher (Mono/Flux)
       -> WebClient / R2DBC reactive driver
       -> signals onNext / onError / onComplete
       -> reactive HTTP response
~~~

Event loop threads xử lý nhiều kết nối và callback mà **không nên bị chặn** bởi công việc dài. Một small fixed number of event loop workers có thể xử lý nhiều connection I/O-bound vì mỗi operation nhường thread lúc chờ I/O.

WebFlux cũng có thể chạy trên Servlet containers hỗ trợ non-blocking I/O. “WebFlux” là **framework programming model**, không đồng nghĩa tuyệt đối “Netty” ở mọi deployment.

**Cảnh báo cốt lõi:** nếu bạn gọi một blocking database client trên event loop, toàn bộ lợi thế ít threads dễ trở thành điểm yếu: một số ít event-loop workers bị giữ lại, ảnh hưởng **nhiều connections khác**.

## 5. Kiểm toán toàn bộ chuỗi I/O end-to-end

Đây là điểm trọng tâm của bài.

~~~text
Browser / Mobile / API client
         |
         v
CDN / WAF / load balancer / reverse proxy
         |
         v
TLS / Netty receive / WebFlux handler
         |
         +----> Auth / token check / remote identity provider
         |
         +----> Cache (in-process / Redis)
         |
         +----> Database (JDBC/JPA  vs  R2DBC)
         |
         +----> HTTP upstream (RestTemplate/block  vs  WebClient)
         |
         +----> Filesystem / object storage
         |
         +----> Message broker / async callback
         |
         +----> Serialization / compression / crypto / CPU
         |
         v
Netty send / reverse proxy / client reads response
~~~

Với **mỗi bước**, ghi rõ nó là: non-blocking thực sự, blocking được cô lập trong bounded worker, CPU-bound, hoặc phụ thuộc service khác. Một reactive endpoint có thể **chứa nhiều điểm blocking ẩn**.

### 5.1. Ma trận kiểm toán thư viện

| Tầng | Ví dụ phù hợp reactive I/O | Cảnh báo blocking / giới hạn |
| --- | --- | --- |
| HTTP server | Spring WebFlux với Reactor Netty | Controller gọi blocking operation trên event loop |
| HTTP client | Spring `WebClient` với connector non-blocking | `RestTemplate`, `HttpURLConnection`, `WebClient(...).block()` |
| RDBMS | R2DBC driver phù hợp database | JDBC, JPA/Hibernate ORM truyền thống |
| Redis | Reactive Redis client/API khi dùng reactive driver | Sync Redis API/blocking wrapper |
| Filesystem | NIO async API nếu stack hỗ trợ thực sự | `Files.readAllBytes`, `Files.newInputStream` sync read |
| Object storage | Async SDK/client phù hợp, end-to-end stream | SDK sync client, `getObject` blocking |
| Messaging | Non-blocking/reactive receiver nếu khả dụng | Consumer callback block hoặc bridge không có limit |
| JSON serialization | Jackson codec trong WebFlux | Serialize JSON nặng vẫn tốn CPU, có thể block event loop |
| Security | Reactive security chain | Remote auth introspection blocking, BCrypt/crypto CPU nặng |
| Template/rendering | Pipeline phù hợp | Block template lookup/dynamic rendering; CPU cost |
| Tracing/logging | Reactor Context/Micrometer đúng cách | Blocking appender, network log sink sync |
| Legacy integration | Bounded scheduler wrapper nếu bắt buộc | Unbounded thread/queue hoặc không timeout |
| Reverse proxy | Async/non-blocking proxy theo cấu hình | Connection limits, buffering, idle timeout vẫn ảnh hưởng |

**Nếu database chính là JPA/JDBC**, WebFlux không tự biến việc truy cập DB thành non-blocking. Dùng `Mono.fromCallable(() -> jpaRepo.findById(...))` trên bounded worker có thể giúp tránh block event loop, nhưng vẫn **tiêu thụ thread blocking và DB connection**. Đây là một migration bridge, không phải lợi ích full reactive.

### 5.2. “Trả Mono” không chứng minh non-blocking

Anti-pattern:

~~~java
@GetMapping("/unsafe/{id}")
public Mono<BookDto> unsafe(@PathVariable long id) {
    BookEntity entity = jdbcRepository.findById(id);
    return Mono.just(mapToDto(entity));
}
~~~

Đây là **mẫu phản diện minh họa**, không biên dịch độc lập vì `jdbcRepository`, `BookEntity`, `BookDto`, `mapToDto` chưa được định nghĩa. Lệnh JDBC đã chạy đồng bộ **trước khi Mono được trả**, và rất có thể trên event-loop thread. Dù return type là `Mono<BookDto>`, đoạn code không phải pipeline non-blocking end-to-end.

Tương tự:

~~~java
@GetMapping("/also-unsafe")
public String alsoUnsafe() {
    return webClient.get().uri("/upstream")
            .retrieve()
            .bodyToMono(String.class)
            .block();
}
~~~

Đây là anti-pattern. `WebClient` không mất tính non-blocking, nhưng `block()` bắt caller chờ kết quả; trên event loop, Reactor có thể phát hiện và ném `IllegalStateException` vì `block()` không được phép trên non-blocking threads. Đừng dùng `block()` trong WebFlux request path để chuyển reactive sang imperative cho tiện.

## 6. Reactor Mono, Flux và Reactive Streams backpressure

- **`Mono<T>`** phát tối đa 0 hoặc 1 phần tử rồi complete/error.
- **`Flux<T>`** phát 0 đến nhiều phần tử; có thể hữu hạn hoặc vô hạn.
- **Publisher** được subscribe và có signals `onSubscribe`, `onNext`, `onError`, `onComplete`. Backpressure qua **demand (`request(n)`)**.
- **Lazy execution**: nhiều reactive operations chỉ được thực thi khi có subscription. Spring WebFlux sẽ subscribe vào Publisher được controller trả.
- **Không gọi `subscribe()` tùy tiện trong handler** chỉ để kích hoạt tác vụ rồi trả `Mono.empty()`; bạn có thể mất lifecycle, error propagation, cancellation, tracing và backpressure.

Ví dụ pipeline không blocking:

~~~java
@GetMapping("/hello")
public Mono<String> hello() {
    return Mono.just("hello")
            .map(String::toUpperCase);
}
~~~

Mã minh họa trong `@RestController`. Kết quả là `HELLO`, nhưng đây là tác vụ memory/CPU cực nhỏ; **không có I/O chờ**, nên chưa phải chứng minh WebFlux scale tốt hơn MVC.

### 6.1. Backpressure không phải quota toàn hệ thống

Backpressure biểu diễn nhu cầu tiêu thụ các phần tử của một reactive stream. Nó **không** tự giới hạn:
- HTTP requests mới đổ vào server;
- số TCP connections;
- tất cả queries đồng thời;
- Redis/object storage client không hỗ trợ backpressure;
- remote service nhận request trước khi client chậm;
- memory của `collectList()` trên một Flux cực lớn;
- số retry/timeouts làm tăng tải.

Nếu producer không thể giảm tốc, bạn vẫn cần chính sách **buffer/drop/error** và capacity limits. Các điểm **network boundary, JDBC adapter, distributed messaging** có thể không tôn trọng demand giống một Flux thuần.

### 6.2. Các operator dễ gây bùng nổ tài nguyên

~~~java
Flux.range(1, 100_000)
    .flatMap(id -> remoteCall(id));
~~~

Đây là snippet minh họa trong scope có `remoteCall`; không chạy độc lập. `flatMap` có concurrency và prefetch semantics theo overload/Reactor version, **không nên mặc định vô hạn hay mặc định an toàn**. Nếu downstream có khả năng quá tải, đặt limit rõ:

~~~java
Flux.range(1, 1_000)
    .flatMap(id -> remoteCall(id), 8);
~~~

Tối đa khoảng 8 inner publishers active cùng lúc tại operator này, nhưng remote APIs vẫn cần connection-pool bounds, timeouts và rate limits. Kết quả `flatMap` **có thể không giữ thứ tự**; nếu cần thứ tự, cân nhắc `concatMap` hoặc `flatMapSequential` và đánh giá latency.

Một anti-pattern khác:

~~~java
Mono<List<Item>> all = hugeFlux.collectList();
~~~

Nếu `hugeFlux` rất lớn hoặc không kết thúc, `collectList()` có thể tiêu tốn heap hoặc không hoàn tất. Với download/streaming, chọn media type và response body semantics phù hợp thay vì materialize toàn bộ stream.

## 7. Blocking I/O trong reactive pipeline: xử lý đúng ranh giới

Khi chưa thể thay một thư viện synchronous/blocking (JDBC, SDK cũ, filesystem API), tài liệu Reactor khuyến nghị dạng:

~~~java
public Mono<LegacyResult> legacy(long id) {
    return Mono.fromCallable(() -> blockingLegacyClient.fetch(id))
            .subscribeOn(Schedulers.boundedElastic());
}
~~~

Đây là method minh họa, cần `LegacyResult` và `blockingLegacyClient` thực tế. Điều quan trọng:

1. `Mono.fromCallable` **trì hoãn** việc gọi blocking API tới lúc subscribe.
2. `subscribeOn(Schedulers.boundedElastic())` dời source execution sang worker scheduler dành cho blocking operations.
3. Event loop không bị chặn vì tác vụ được offload.
4. **Tác vụ vẫn blocking** trên một thread khác; DB connections, heap, queue và scheduler capacity không biến mất.
5. `boundedElastic` có bounds (thread/queued tasks) nhưng default của nó có thể không phù hợp workload/ngưỡng SLO của bạn. Giới hạn concurrency/downstream và test overload vẫn cần thiết.
6. Nên đặt `subscribeOn` ngay sau blocking source theo pattern Reactor docs để người đọc không nhầm phạm vi scheduler.

### 7.1. `publishOn` khác `subscribeOn`

- `subscribeOn(scheduler)` tác động đến quá trình subscribe/source execution upstream theo Reactor semantics, phù hợp để wrap **nguồn** blocking.
- `publishOn(scheduler)` đổi thread dùng để xử lý những downstream signals/operators từ vị trí đặt operator.

Ví dụ:

~~~java
Mono.fromCallable(() -> blockingRead())
    .subscribeOn(Schedulers.boundedElastic())
    .map(this::transform);
~~~

Đây là snippet minh họa với `blockingRead` và `transform` chưa định nghĩa. Source được offload ngay từ đầu; `map` có thể chạy trên worker tương ứng. Nếu muốn chuyển CPU-heavy transform sang pool khác, cần xem CPU scheduling và tránh tốn chi phí thread-switch không cần thiết.

**Đừng đặt** `publishOn` **sau** một `map` đang gọi blocking API rồi nghĩ đã sửa:

~~~java
Mono.just(id)
    .map(x -> blockingLegacyClient.fetch(x))
    .publishOn(Schedulers.boundedElastic());
~~~

Đây là **phản ví dụ**: `map` blocking xảy ra **trước** `publishOn`, nên có thể block thread gốc. Tốt hơn dùng `fromCallable(...).subscribeOn(boundedElastic())` hoặc đặt `publishOn` trước đoạn blocking nếu hiểu rõ pipeline.

### 7.2. Không dùng `Thread.sleep` giả lập latency của I/O

~~~java
return Mono.just("done")
        .map(v -> {
            Thread.sleep(200);  // minh họa phản diện
            return v;
        });
~~~

Snippet này còn cần xử lý `InterruptedException` để compile. Nó minh họa một ý: **đặt sleep bên trong map** là blocking, không phải reactive wait. Để tạo một asynchronous timer **cho demo/test**, dùng:

~~~java
return Mono.delay(Duration.ofMillis(200))
        .map(tick -> "done");
~~~

Nhưng `Mono.delay` **không mô phỏng database lock contention, connection pool, network bandwidth hay payload serialization**. Không lấy benchmark timer giả để kết luận throughput production.

## 8. WebClient: HTTP I/O không chặn nhưng cũng có giới hạn

`WebClient` là non-blocking reactive HTTP client, có thể chạy trong WebFlux **hoặc Spring MVC**. Khi ứng dụng gọi remote service, sử dụng:

~~~java
@Service
public class InventoryGateway {
    private final WebClient client;

    public InventoryGateway(WebClient.Builder builder) {
        this.client = builder
                .baseUrl("https://inventory.example.com")
                .build();
    }

    public Mono<Stock> getStock(long id) {
        return client.get()
                .uri("/stock/{id}", id)
                .retrieve()
                .bodyToMono(Stock.class)
                .timeout(Duration.ofSeconds(2));
    }

    public record Stock(long productId, int available) {}
}
~~~

Đây là **class minh họa tích hợp một upstream giả định**; cần imports `@Service`, `WebClient`, `Mono`, `Duration`, và upstream thực trả JSON khớp `Stock`. `https://inventory.example.com` ở đây **chỉ là domain ví dụ**, không phải service thật để ứng dụng demo mục 11 gọi. `WebClient.Builder` được Boot auto-configure khi có WebFlux infrastructure phù hợp.

Các điểm cần nhớ:

- `bodyToMono` không tự gọi `block()`; bạn **return Mono** hoặc compose trong pipeline.
- `timeout(Duration)` đặt deadline cho Publisher; đồng thời cấu hình connect/response/read timeout ở HTTP client connector khi cần.
- Giới hạn connection pool, pending acquire, DNS, TLS handshake, remote rate limit, max in-memory buffer size và error status handling.
- `retrieve()` mặc định coi response 4xx/5xx thành error signal; thiết kế `onStatus`, retries và fallback có chủ đích.
- `retryWhen` có thể làm tăng tải lên service vốn đã quá tải; retry phải có backoff, jitter, limit và chỉ với nghiệp vụ/idempotency phù hợp.
- Nếu gọi nhiều upstream độc lập, `Mono.zip(a, b)` có thể cho phép overlap các I/O chờ (khi sources non-blocking), nhưng không khẳng định mọi case nhanh hơn hoặc total request time luôn bằng max(latencies).
- Với 10.000 requests đồng thời, WebClient vẫn cần đủ connections/FDs và upstream capacity. Non-blocking không phải “vô hạn connections”.

### 8.1. Một chuỗi đủ reactive và một chuỗi giả reactive

~~~text
A: WebFlux -> Mono -> WebClient(non-blocking) -> R2DBC(non-blocking)
   -> response codecs -> Netty
   => Có cơ sở kỳ vọng tiết kiệm blocked threads nếu latency I/O cao.

B: WebFlux -> Mono.just(jpaRepository.findById(...))
   -> JDBC blocking trên event loop -> Netty
   => Return type reactive nhưng I/O cốt lõi vẫn blocking.

C: WebFlux -> Mono.fromCallable(JPA).subscribeOn(boundedElastic)
   -> JDBC blocking trên worker -> Netty
   => Event loop được bảo vệ; vẫn cần threads/queue/JDBC pool.
~~~

Lợi ích cần đo trên **toàn đường A**, không chỉ vì có chữ `Mono` trong signature.

## 9. Database: R2DBC so với JDBC/JPA

JDBC truyền thống sử dụng blocking APIs. JPA/Hibernate ORM thường dựa trên JDBC và transaction context thread-bound. R2DBC cung cấp API reactive cho relational database drivers, nhưng **driver và database thực** phải hỗ trợ và ổn định ở workload của bạn.

| Khía cạnh | MVC + JDBC/JPA | WebFlux + R2DBC |
| --- | --- | --- |
| Lối viết | Imperative, transaction/service quen thuộc | Publisher, composition, reactive transactions |
| I/O DB | Blocking caller thread | Reactive API/driver khi đúng implementation |
| ORM/ecosystem | JPA/Hibernate mature và nhiều integration | R2DBC hỗ trợ theo dialect/driver, không tương đương mọi JPA feature |
| Lazy loading | JPA proxy/lazy entity theo session | Không sử dụng lazy-loading ORM JPA theo cùng cách |
| Transactions | `PlatformTransactionManager`, thread-bound | `ReactiveTransactionManager`, Reactor context |
| Backpressure | Không phải Reactive Streams | Có reactive API, nhưng DB protocol/pool vẫn có giới hạn |
| Pool | JDBC pool (vd HikariCP) | R2DBC connection pool khi cần |
| Failure modes | Thread/connection exhaustion | Connection pool/queue/event-loop/retry pressure |
| Migration cost | Giữ code hiện tại | Thay đổi repositories, transactions, mappings, tests |

**R2DBC không tự động nhanh hơn JDBC**. Nó thay đổi execution model; lợi thế là tránh giữ thread trong lúc đợi DB I/O khi concurrency lớn. Một truy vấn SQL tối ưu hóa kém hoặc DB đã bão hòa sẽ vẫn chậm, bất kể reactive client. Các phép join, index, row scans, locks, network và connection count mới thường là yếu tố quyết định.

### Reactive transaction không đi theo ThreadLocal

Với R2DBC, `@Transactional` có thể dùng **ReactiveTransactionManager** với return type reactive. Transaction context được liên kết với subscriber/Reactor Context, không giống mô hình thread-bound JDBC. Tránh dùng một `@Transactional` imperative method gọi nội dung async rồi mong transaction bao bọc toàn bộ subscription.

Không tự gọi `subscribe()` bên trong service rồi rời khỏi transaction scope. Trả Publisher để Spring quản lý lifecycle/transaction. Kiểm tra reactive transaction manager được cấu hình khi dự án có đồng thời JDBC/JPA và R2DBC.

## 10. Một benchmark công bằng phải kiểm soát điều gì?

Nếu bạn benchmark MVC và WebFlux, **hai phiên bản phải giải cùng một bài toán** với cùng contract và điều kiện:

1. **Cùng endpoint semantics:** status codes, JSON schema, số records, validation, authentication, error handling, serialization.
2. **Cùng dữ liệu:** database size/indexes/query plans, cache hit/miss, network latency, SQL result set size.
3. **Cùng downstream:** cùng DB cluster, upstream HTTP service, rate limits, connection pools được tối ưu hợp lý theo từng stack.
4. **Cùng hạ tầng:** số vCPU, RAM, JVM version, GC, heap, containers, network, TLS, proxies và bandwidth.
5. **Warm-up tương đương:** JIT, driver pools, DB caches và connection establishment.
6. **Tool tạo tải độc lập:** máy load generator không bão hòa CPU/network, giữ đúng connection reuse/keep-alive và payload profile.
7. **Tỷ lệ lỗi:** không bỏ 429/503/timeout, client abort khỏi số liệu.
8. **Same arrival pattern:** thử cả closed-loop concurrency cố định và open-loop request arrival rate để giảm coordinated omission.
9. **Cùng time budget:** thử nhiều lần, độc lập, dùng confidence intervals/distributions.
10. **Quan sát mỗi tầng:** HTTP server, event loops/worker threads, DB pool, GC, remote services, OS sockets/FDs và gateway.

Không nên benchmark bằng một lệnh `curl` đơn lẻ rồi khẳng định stack nào “nhanh hơn”. Một response 5 ms trong dev environment không phản ánh p99 khi 500 client đồng thời.

### 10.1. Các kịch bản nên có

| Kịch bản | Mục tiêu kiểm chứng | WebFlux có lợi? |
| --- | --- | --- |
| K1: Echo JSON rất nhỏ | Baseline framework/codec CPU overhead | Không nên mặc định |
| K2: CPU-bound mapping/crypto | Sức tính toán CPU, context switching | Reactive không tạo thêm CPU |
| K3: JDBC/JPA blocking query | Hệ quả blocking DB/client library | Thường không thấy lợi thế full reactive |
| K4: R2DBC + high-latency DB | Concurrent I/O và thread/memory footprint | Có thể; phải đo |
| K5: Hai WebClient upstream độc lập | Composed concurrent waits | Có thể; MVC + WebClient cũng là ứng viên |
| K6: Streaming lớn | Buffering, backpressure, client chậm | Phụ thuộc end-to-end support |
| K7: 500 concurrent clients | Saturation, queue, pool, p99 | Có thể; cần DB/service đủ capacity |
| K8: Upstream lỗi/chậm | Timeouts, retries, circuit breakers | Reactive không tự tránh cascading failure |
| K9: 90% cache hits | CPU/cache path efficiency | Chưa chắc khác biệt đáng kể |
| K10: TLS+compression+auth thật | Overheads production thực tế | Không kết luận từ demo nhỏ |

### 10.2. Metric set bắt buộc

**Client/HTTP:** successful RPS, p50/p95/p99 latency, max latency, total errors, 4xx/5xx distribution, timeouts, pending/inflight, bytes transferred.

**JVM:** CPU utilization, heap used, GC pauses/allocation rate, native/direct memory, JVM thread count, event-loop busy time.

**Pools/queues:** Netty connection provider active/pending, R2DBC/JDBC pool active/idle/acquire wait, scheduler queue, executor threads và rejections.

**Downstream:** DB query latency/pool/locks, upstream HTTP latency/errors/rate limits, Redis/streaming throughput, filesystem/object storage time.

**Business:** đúng dữ liệu, số bản ghi, thứ tự results, duplicate retries, p99 trên các endpoint quan trọng. Khả năng trả lỗi nhanh khi quá tải đôi khi **tốt hơn** hàng đợi dài vô hạn, nhưng phải đo error budget.

Không chỉ báo một số RPS; các metrics phải được xem **cùng nhau**.

## 11. Ví dụ chạy được: WebFlux → R2DBC/H2 → HTTP response

Để minh họa **chuỗi HTTP → database** không cần JDBC, ứng dụng mẫu dùng:
- Spring Boot 3.x, Java 17+, **chỉ `spring-boot-starter-webflux`** cho HTTP server.
- **`spring-boot-starter-data-r2dbc`** và **`io.r2dbc:r2dbc-h2`** cho kết nối reactive H2.
- `DatabaseClient` đọc một row từ H2 trong một Publisher.
- Controller trả `Mono<ResponseEntity<CatalogItem>>` và `Flux<ServerSentEvent<String>>`.
- **`spring-boot-starter-test`** và `WebTestClient` thực hiện HTTP integration test.

Ví dụ này chứng minh **đường dẫn non-blocking và correctness**, **không** có ý nghĩa thay thế benchmark production. H2 memory thường quá nhanh, không đại diện DB thực có network và tranh chấp locks. Đừng dùng số liệu thử demo này để tuyên bố WebFlux có throughput tốt hơn MVC.

### 11.1. Maven dependencies

Tạo project Maven bằng Spring Initializr với Java 17+, Spring Boot 3.x. Chọn WebFlux, Spring Data R2DBC và Test; bổ sung driver R2DBC H2 nếu chưa được chọn. Các dependency trong `pom.xml`:

~~~xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-r2dbc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.r2dbc</groupId>
        <artifactId>r2dbc-h2</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
~~~

Đây là **đoạn dependencies** đặt trong project `pom.xml` do Spring Initializr tạo; không phải toàn bộ file Maven độc lập. **Không** thêm `spring-boot-starter-web`/Spring MVC ở đây. Trong Spring Boot 3.x, nếu cả Web MVC và WebFlux starters cùng trên classpath, **Spring MVC thường được chọn mặc định**, trừ khi chọn rõ reactive application type; chỉ dùng WebFlux starter giúp ví dụ rõ ràng.

### 11.2. Cấu trúc project

~~~text
src/
  main/
    java/com/example/demo/
      DemoApplication.java
      CatalogItem.java
      CatalogService.java
      CatalogController.java
    resources/
      application.properties
      schema.sql
      data.sql
  test/
    java/com/example/demo/
      CatalogHttpTest.java
~~~

### 11.3. Cấu hình R2DBC database và SQL scripts

`src/main/resources/application.properties`:

~~~properties
spring.application.name=webflux-io-chain-demo
spring.r2dbc.url=r2dbc:h2:mem:///catalogdb
spring.sql.init.mode=always
~~~

H2 là in-memory database cho demo. `spring.sql.init.mode=always` cho phép Boot khởi tạo scripts `schema.sql` và `data.sql` trên ConnectionFactory reactive; đây **không** phải JDBC URL. Project sử dụng version Boot/R2DBC H2 do BOM của Boot quản lý, tránh ép version driver không tương thích.

`src/main/resources/schema.sql`:

~~~sql
CREATE TABLE catalog_item (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    stock INTEGER NOT NULL
);
~~~

`src/main/resources/data.sql`:

~~~sql
INSERT INTO catalog_item (id, name, stock)
VALUES (7, 'spring', 9);

INSERT INTO catalog_item (id, name, stock)
VALUES (8, 'reactor', 12);
~~~

Vì H2 memory ở đây được khởi tạo trong app, demo có dữ liệu tất định. Khi chạy đồng thời nhiều application contexts vào cùng H2 memory database, tránh reuse schema không mong muốn; các test cần kiểm soát tạo/xóa dữ liệu hoặc database isolation. Trong production, dùng migration tool/phương thức deployment nhất quán thay cho khởi tạo schema theo demo trên mọi lần startup.

### 11.4. Java application

`src/main/java/com/example/demo/DemoApplication.java`:

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

### 11.5. Immutable DTO

`src/main/java/com/example/demo/CatalogItem.java`:

~~~java
package com.example.demo;

public record CatalogItem(
        Long id,
        String name,
        Integer stock
) {}
~~~

`CatalogItem` là immutable Java record, không chứa shared mutable state. Việc record immutable **không** tự chứng minh toàn pipeline thread-safe, nhưng giảm một lớp rủi ro chia sẻ object.

### 11.6. Service dùng `DatabaseClient` reactive

`src/main/java/com/example/demo/CatalogService.java`:

~~~java
package com.example.demo;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CatalogService {

    private final DatabaseClient db;

    public CatalogService(DatabaseClient db) {
        this.db = db;
    }

    public Mono<CatalogItem> findById(long id) {
        return db.sql("""
                SELECT id, name, stock
                FROM catalog_item
                WHERE id = :id
                """)
                .bind("id", id)
                .map((row, metadata) ->
                        new CatalogItem(
                                row.get(0, Long.class),
                                row.get(1, String.class),
                                row.get(2, Integer.class)))
                .one();
    }
}
~~~

**Giải thích:**

1. `DatabaseClient` được Spring Boot auto-configure với R2DBC `ConnectionFactory`.
2. `:id` là **bind marker**, không phải nối SQL thô từ input. Nó tránh SQL injection khi dùng đúng cơ chế binding.
3. `.one()` trả `Mono<CatalogItem>`, complete empty nếu không có row.
4. `row.get(0, Long.class)`/theo index tương ứng cột SELECT. Với schema H2 này, kiểu `BIGINT` và `INTEGER` ánh xạ sang `Long`/`Integer`.
5. Không gọi `.block()`, `.subscribe()` hoặc JDBC client bên trong service.
6. Pipeline truy vấn thực tế chỉ chạy khi Publisher được subscribe bởi WebFlux; DB driver và pool vẫn phải được quan sát.
7. SQL table và scripts phải tồn tại khi app startup. Nếu đổi thứ tự cột SELECT, code mapping theo vị trí phải được cập nhật; trong service production có thể map theo tên/row-mapper rõ ràng.

### 11.7. Reactive HTTP controller

`src/main/java/com/example/demo/CatalogController.java`:

~~~java
package com.example.demo;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<CatalogItem>> one(
            @PathVariable("id") long id) {

        return catalogService.findById(id)
                .map(item -> ResponseEntity.ok(item))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(
            path = "/ticks",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ticks() {
        return Flux.interval(Duration.ofMillis(200))
                .take(3)
                .map(number -> ServerSentEvent
                        .builder("tick-" + number)
                        .build());
    }
}
~~~

Với record H2 ban đầu:
- `GET /api/catalog/7` trả **200** JSON `{"id":7,"name":"spring","stock":9}`.
- `GET /api/catalog/999` trả **404**, vì `Mono.empty()` được ánh xạ qua `defaultIfEmpty`.
- `GET /api/catalog/ticks` là demo **SSE** phát `tick-0`, `tick-1`, `tick-2` rồi kết thúc; nó dùng timer không blocking, **không** thay thế việc đo streaming với client chậm.

Không dùng `Thread.sleep` để tạo interval. `Flux.interval` phát tín hiệu thông qua scheduler phù hợp; lượng subscribers/timers cũng tiêu tốn tài nguyên, nên SSE production cần heartbeat/connection limits/cleanup.

### 11.8. Kiểm thử HTTP thật với WebTestClient

`src/test/java/com/example/demo/CatalogHttpTest.java`:

~~~java
package com.example.demo;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogHttpTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    void readsOneRowThroughReactiveDatabase() {
        client.get()
                .uri("/api/catalog/7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7)
                .jsonPath("$.name").isEqualTo("spring")
                .jsonPath("$.stock").isEqualTo(9);
    }

    @Test
    void notFoundWhenDatabasePublisherIsEmpty() {
        client.get()
                .uri("/api/catalog/999")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty();
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

hoặc trên Windows:

~~~powershell
.\mvnw.cmd test
~~~

Nếu project không có Maven Wrapper, dùng `mvn test` khi máy đã cài Maven. Test khởi động **embedded HTTP server trên một random port**, thực hiện **HTTP network request** và truy xuất **R2DBC H2** rồi assert JSON/status. Đây là một integration test hữu ích hơn so với chỉ test một `Mono.just` giả lập.

Tuy nhiên test này **không đo hiệu năng**: nó không đo meaningful high concurrency, TLS, reverse proxy, packet loss, remote database latency hay real network contention. Đừng trình bày hai test pass như một chứng minh “WebFlux nhanh hơn”.

### 11.9. Thử bằng curl

Khởi chạy:

~~~bash
./mvnw spring-boot:run
~~~

Gọi API:

~~~bash
curl -i http://localhost:8080/api/catalog/7
~~~

Kết quả kỳ vọng: status **200 OK**, `Content-Type: application/json` và body:

~~~json
{"id":7,"name":"spring","stock":9}
~~~

Không tồn tại:

~~~bash
curl -i http://localhost:8080/api/catalog/999
~~~

Status kỳ vọng **404 Not Found**. Body của default WebFlux error response có thể phụ thuộc cấu hình/negotiation; trong controller này `ResponseEntity.notFound().build()` không cung cấp business payload.

SSE timer:

~~~bash
curl -N http://localhost:8080/api/catalog/ticks
~~~

Body có ba events với dữ liệu kiểu:

~~~text
data:tick-0

data:tick-1

data:tick-2
~~~

`-N` tránh buffering stdout của curl; thứ tự các event là 0, 1, 2 nhưng **thời gian phát thực tế** phụ thuộc scheduler, server, proxy và client, không bảo đảm đúng từng 200 ms trên mạng.

## 12. Mở rộng chuỗi I/O với WebClient bên ngoài

Ví dụ mục 11 chỉ đi qua một database reactive. Nếu nghiệp vụ thực gọi thêm inventory service, pipeline có thể là:

~~~java
public Mono<ItemDetail> detail(long id) {
    Mono<CatalogItem> item = catalogService.findById(id);
    Mono<InventoryGateway.Stock> stock = inventoryGateway.getStock(id);

    return Mono.zip(item, stock)
            .map(pair -> new ItemDetail(
                    pair.getT1(), pair.getT2()));
}
~~~

Đây là **method minh họa**, chưa runnable độc lập: cần khai báo `catalogService`, `inventoryGateway`, `ItemDetail`; gateway ở mục 8 dùng domain ví dụ. `Mono.zip` chỉ emit khi cả hai sources có giá trị; nếu một source complete empty, zip có thể complete empty, cần xử lý missing data theo business semantics. Nếu hai calls độc lập, khi subscribe chúng có thể overlap I/O. Không được **gọi `.block()`** để gom results:

~~~java
// Anti-pattern: đồng bộ hóa hai request bất đồng bộ
CatalogItem a = item.block();
InventoryGateway.Stock b = stock.block();
~~~

Dù upstream dùng WebClient, đoạn anti-pattern làm caller chờ và có thể phá event loop. Trả `Mono<ItemDetail>` để framework subscribe, propagate error/cancellation một cách có kiểm soát.

### Khi nào MVC + WebClient đủ tốt?

Nếu hệ thống đã dùng MVC/JPA ổn định và chỉ có một vài HTTP fan-outs, bạn có thể:
- Giữ Spring MVC và blocking DB calls ở thread Servlet/worker được cấu hình phù hợp.
- Dùng `WebClient` ở một số integration calls.
- Dùng reactive return type từ MVC handler khi API phù hợp hoặc tập trung tối ưu connection pools và timeouts.
- Đo lại SLO trước khi cân nhắc rewrite toàn bộ service sang WebFlux/R2DBC.

Đây là một chiến lược **tối ưu theo bottleneck**, không phải thỏa hiệp kém về mặt kiến trúc.

## 13. Lỗi khi chạy reactive nhưng blocking ẩn: cách phát hiện

Các điểm cần tìm trong code review:

~~~text
.block()
.blockOptional()
Thread.sleep(...)
Future.get()
CompletableFuture.join()
RestTemplate.exchange(...)
JdbcTemplate.query(...)
jpaRepository.find...
Files.readAllBytes(...)
InputStream.read(...) trên nguồn blocking
ObjectMapper.writeValueAsBytes(hugeObject)
synchronized / locks giữ lâu
collectList() trên Flux không có giới hạn
flatMap(...) không có concurrency budget rõ
subscribe() bên trong controller/service
Schedulers.boundedElastic() dùng như lời giải mọi vấn đề
~~~

Không phải mọi method trong danh sách luôn sai **ở mọi thread/hoàn cảnh**: ví dụ `Files.readAllBytes` trong startup config hoặc một tool command line có thể hoàn toàn hợp lý. Vấn đề là dùng nó **trên event-loop request path** hoặc trong một publisher mà chưa offload/bound resource.

### 13.1. BlockHound

**BlockHound** là công cụ hỗ trợ phát hiện một số blocking calls trên threads được đánh dấu non-blocking trong tests. Nó hữu ích để tìm `Thread.sleep` hoặc API blocking khi lỡ chạy trên Reactor scheduler/event loop.

Tuy nhiên:
- Không chứng minh mọi I/O trong toàn hệ thống đều non-blocking.
- Có thể có false positives/whitelisting; cần test profile riêng.
- Kiểm tra **tính tương thích với JVM và Reactor/BlockHound version**; Java 17+ có yêu cầu instrumentation/JVM arguments tùy version.
- Không bật tự động trong production nếu chưa đánh giá overhead.

### 13.2. Java Flight Recorder, traces và thread dumps

Quan sát:
- Reactor Netty event loop threads đang làm gì? Có stack traces JDBC, JSON cực lớn, file IO, locks, crypto kéo dài?
- Reactive scheduler/boundedElastic có đầy queue/threads không?
- Connection pool acquires có chờ lâu?
- DB query latency và counts/locks có tăng?
- `WebClient` có đủ channels, DNS/connect/response timeouts?
- Reactor Context/tracing có mất khi chuyển thread?
- p99 bị giữ bởi network/proxy/client speed hay chính application?

Tìm **điểm nghẽn cụ thể** trước khi chọn đổi framework.

## 14. Reactive Context, ThreadLocal và transactions

Reactor có **`Context`** gắn với subscription, hỗ trợ mang metadata qua async execution mà không mặc định dựa vào một `ThreadLocal` duy nhất:

~~~java
Mono<String> observed = Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("requestId", "missing")))
        .contextWrite(context -> context.put("requestId", "req-42"));
~~~

Đoạn minh họa thuần Reactor này tạo Publisher chứa `req-42` khi được subscribe. **Không** dùng `ThreadLocal` hoặc tự chia sẻ mutable map cho mọi request. Ngược lại, nếu thư viện logging/tracing dựa MDC thì cần cơ chế bridge/context propagation thích hợp, không chắc MDC đi theo mọi operator qua mọi thread chỉ vì return type là Mono.

Với **reactive transaction**, Spring dùng transaction context gắn với reactive pipeline khi `ReactiveTransactionManager` được cấu hình, thay vì giả định giao dịch JDBC `ThreadLocal` đi theo thread xuyên `publishOn`/`subscribeOn`. Trả Publisher cho transaction interceptor để lifecycle được quản lý; không tự `subscribe()` rồi cho rằng transaction hiện hành bao bọc mọi thứ.

**Bảo mật:** Spring Security cho WebFlux dùng reactive security model/`SecurityWebFilterChain` và reactive context handling. Không sao chép cấu hình Servlet `SecurityFilterChain`/`HttpServletRequest` và cho rằng nó xử lý đúng WebFlux. Mỗi endpoint vẫn phải authorize dù reactive chain non-blocking.

## 15. Cancellation, timeout, retries và tài nguyên hữu hạn

Một client ngắt kết nối có thể khiến subscription bị cancel; không phải mọi thư viện legacy/downstream operation tự dừng ngay. Với R2DBC/WebClient, cancellation có thể giải phóng resources theo driver/client semantics, nhưng cần kiểm chứng resource lifecycle thực tế.

Đặt timeout phù hợp:

~~~java
return inventoryGateway.getStock(id)
        .timeout(Duration.ofSeconds(2))
        .onErrorResume(TimeoutException.class,
                ex -> Mono.just(new StockFallback(id, 0)));
~~~

Đây là snippet nghiệp vụ minh họa, chưa runnable độc lập vì `StockFallback` và return type phải khớp, cần import `TimeoutException`. **Fallback stock=0 có thể không hợp lý trong thương mại**: chỉ dùng giá trị fallback khi domain cho phép; còn không thì trả lỗi HTTP 503/504 hoặc kết quả partial có định nghĩa rõ.

Không đặt retry vô hạn:

~~~java
return remoteMono.retry();
~~~

Đoạn này là anti-pattern đối với remote service: task có thể gửi request liên tục khi upstream lỗi. Hãy giới hạn số lần, backoff + jitter, chỉ retry idempotent operations khi hợp lý, và áp tổng deadline. Mỗi retry có chi phí capacity thật.

### 15.1. BoundedElastic không thay admission control

Dù scheduler có giới hạn thread/queue, vẫn cần:
- limit số requests/traces đang active;
- limit `flatMap` concurrency;
- HTTP client connection pool limits;
- DB connection pool size và acquire timeout;
- per-tenant/per-user quotas;
- circuit breaker/bulkheads và rate limiting;
- bounded buffers cho streaming/event processing;
- metrics cho cancel, timeout, OOM và scheduler saturation.

Nếu upstream max concurrency là 50 mà bạn mở 5.000 in-flight requests, WebFlux không khiến upstream có thêm 4.950 slots.

### 15.2. Tài nguyên gửi HTTP response

Client đọc chậm có thể giữ TCP connection và buffers lâu. Backpressure trong reactive chain giúp quản lý dòng dữ liệu tại các ranh giới hỗ trợ nó, nhưng `collectList` và các gateway buffering có thể vẫn gom hàng MB/GB. Đánh giá response size, streaming content type, codec `maxInMemorySize`, slow-reader rate và proxy caching/buffering.

## 16. Khi nào chọn MVC, WebFlux hoặc kết hợp?

| Điều kiện hiện tại | Hướng đề xuất để thử trước | Vì sao |
| --- | --- | --- |
| MVC ổn định, JPA/JDBC là lõi | Giữ MVC, tối ưu DB/SQL/pool | Đổi WebFlux không loại bỏ blocking driver |
| Đa số endpoints CPU-bound, response nhanh | Giữ stack phù hợp, tối ưu thuật toán/CPU | Reactive không tự tạo CPU |
| Nhiều HTTP upstream calls có độ trễ | Thử `WebClient` trên MVC trước | Có thể nhận lợi ích I/O concurrency mà không rewrite |
| Xây mới service I/O-bound với R2DBC + async HTTP | Đo WebFlux thực tế | Toàn chuỗi có điều kiện non-blocking |
| Streaming/SSE rất nhiều kết nối chờ | So sánh WebFlux với MVC async | Mô hình kết nối/threads có thể khác đáng kể |
| Legacy SDK bắt buộc blocking | MVC hoặc WebFlux với bounded adapter có giám sát | Không che mất blocking chỉ bằng Mono |
| Team ít kinh nghiệm Reactor, debugging khó | Giữ MVC nếu SLO đáp ứng | Chi phí vận hành/learning curve là chi phí thật |
| API có tail latency do DB lock | Fix DB, pool, query plan trước | Web stack không sửa lock contention |
| Hệ thống cần throughput cao nhất trên CPU-bound work | Benchmark stack/GC/serialization thực | Không kết luận theo marketing |
| Migrate một phần sang reactive | Phân tách boundary rõ, thử POC nhỏ | Giảm blast radius và tăng khả năng rollback |

### 16.1. Một cây quyết định ngắn

~~~text
Dịch vụ đã đạt SLO và chi phí chấp nhận được?
   |
   +-- Có -> Không nhất thiết chuyển WebFlux
   |
   +-- Không:
        |
        +-- Bottleneck ở DB/SQL/JDBC locks?
        |       -> Tối ưu dữ liệu/query trước
        |
        +-- Bottleneck là nhiều remote I/O chờ?
        |       -> Thử WebClient, concurrency/timeout/pool
        |       -> Đánh giá WebFlux+reactive drivers nếu lợi ích đo được
        |
        +-- CPU-bound / serialization?
        |       -> Tối ưu CPU/algorithm/codec/GC
        |
        +-- Connection/streaming pressure?
                -> Benchmark WebFlux vs MVC async end-to-end
~~~

WebFlux là **một lựa chọn kiến trúc**, không phải mặc định tốt hơn cho mọi service.

## 17. Anti-patterns trong benchmark

### 17.1. So sánh hai đường I/O khác nhau

Không công bằng nếu:
- MVC dùng JDBC đến DB thật còn WebFlux trả `Mono.just` trong memory.
- MVC dùng `Thread.sleep` còn WebFlux dùng `Mono.delay`.
- MVC test đi qua TLS/gateway còn WebFlux gọi trực tiếp local Netty.
- WebFlux dùng R2DBC pool 100 connections còn MVC JDBC pool 10.
- MVC gzip JSON còn WebFlux không gzip hoặc trả payload nhỏ hơn.

Hai phiên bản phải có **cùng nghĩa vụ nghiệp vụ và chất lượng service**.

### 17.2. Closed-loop benchmark che slow tail

Client chỉ gửi request mới sau khi request trước hoàn tất có thể giảm load thực khi server chậm, dẫn đến **coordinated omission** trong một số công cụ đo. Cần cân nhắc open-loop arrival scenarios hoặc công cụ hỗ trợ latency correction thích hợp, đồng thời không để load generator là bottleneck.

### 17.3. Thay đổi concurrency rồi kết luận latency

Khi tăng concurrency, throughput có thể tăng đến điểm saturation nhưng p99 cũng tăng mạnh. Không so đơn thuần RPS lớn nhất rồi bỏ qua SLA hoặc 503. Chọn ngưỡng p99/tỷ lệ lỗi trước, xem stack nào có **tổng chi phí thấp hơn** tại SLA ấy.

### 17.4. Chỉ đo một lần

JIT compilation, heap warm-up, database caches, background GC, noisy neighbors và OS scheduling làm số đo biến động. Thực hiện nhiều trial độc lập, lấy distribution/confidence interval, ghi rõ môi trường và cấu hình. Không tự bịa chênh lệch phần trăm WebFlux/MVC nếu chưa có số liệu thật.

## 18. Những lỗi thường gặp và cách khắc phục

| Triệu chứng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| WebFlux vẫn chậm hơn MVC | Workload CPU-bound, overhead reactive, DB/serialization bottleneck | Xem profile, đo đúng workload |
| Chỉ một vài request block làm toàn server chậm | JDBC/SDK/Thread.sleep trên event loop | Tìm stack traces, offload hoặc thay reactive client |
| `block()` gây exception trong Reactor Netty | Blocking trên non-blocking thread | Trả Mono/Flux, không gọi block trong handler |
| Scheduler boundedElastic đầy queue | Legacy blocking tasks vượt capacity | Giới hạn requests, DB calls, timeout, admission control |
| Memory tăng khi download nhiều | `collectList`/`readAllBytes`, buffering gateway | Stream có giới hạn, backpressure, quotas |
| p99 cao dù threads ít | DB/upstream saturated, event-loop blocked, network, GC | Thu thập end-to-end traces |
| Response 200 nhưng data sai | Pipeline combine/zip empty, retry, mutable state | Kiểm tra semantics, unit/integration tests |
| SSE clients ngắt nhưng resources còn | Cancellation/cleanup chưa triển khai | `doFinally`, lifecycle hooks, limit subscribers |
| Debug mất request ID | ThreadLocal không tự theo reactive operator | Reactor Context/Micrometer propagation |
| `@Transactional` không bao quát stream | Trộn reactive và imperative transaction managers | ReactiveTransactionManager, return Publisher |
| Boot chạy MVC dù đã thêm WebFlux | Cả `spring-boot-starter-web` lẫn webflux | Chỉ chọn WebFlux starter hoặc config explicit |
| WebTestClient pass nhưng production fail | H2 in-memory/test không đại diện I/O thật | Integration tests với downstream/proxy thực |
| Retry làm upstream sập nặng hơn | Retry không bounded, không backoff | Timeout, backoff, jitter, circuit breaker |
| DB pool hết connections | Quá nhiều in-flight queries | Điều chỉnh pool/concurrency + fix slow SQL |
| Chỉ số RPS đẹp nhưng timeout tăng | Bỏ errors khỏi mẫu hoặc overload | Đo successful RPS và error/timeout cùng lúc |
| Dùng blocking logger | Async appender không có hoặc sync network sink | Quan sát log sink, giới hạn và queue |

## 19. Câu hỏi ôn tập

**1. Vì sao `Mono<T>` không chứng minh endpoint non-blocking?**

Vì JDBC, file read, `Thread.sleep` hoặc `block()` có thể đã chạy trước khi tạo Mono hoặc bên trong pipeline. Return type không xác định implementation I/O của từng tầng.

**2. WebFlux có luôn cho p99 tốt hơn MVC không?**

Không. Điều đó phụ thuộc workload, concurrency, backpressure, blocking boundaries, CPU/GC, DB pool và nhiều yếu tố khác. Phải benchmark end-to-end.

**3. Khi service đang dùng JPA/JDBC, chuyển controller sang WebFlux đã đủ chưa?**

Chưa. JDBC/JPA truyền thống vẫn blocking. Có thể offload tạm trên bounded scheduler, nhưng vẫn dùng threads/connections và chưa thành full reactive database path.

**4. `WebClient` có chỉ chạy được trong WebFlux không?**

Không. Spring MVC cũng có thể dùng WebClient. Đây thường là một cách thử tối ưu I/O từ từ.

**5. Vì sao `Mono.delay` không phải benchmark tương đương với JDBC query mất 200 ms?**

Vì timer reactive không chặn thread/DB connection, không mô phỏng SQL locks, networking, pool wait và downstream resource consumption của query thật.

**6. `flatMap(fn, 8)` có giải quyết mọi overload không?**

Không. Nó giới hạn inner concurrency ở operator cụ thể; vẫn cần giới hạn HTTP connections, DB pool, request arrival rate, retries và memory.

**7. Khi nào nên dùng `Mono.fromCallable(...).subscribeOn(boundedElastic())`?**

Khi bắt buộc wrap một synchronous blocking call trong reactive app để tránh block event loop; đây là phương án chuyển tiếp, không biến I/O đó thành non-blocking thật.

**8. Test WebFlux + R2DBC/H2 của bài chứng minh điều gì?**

Nó xác minh HTTP response và truy vấn DB theo reactive API hoạt động đúng qua embedded server; **không** chứng minh WebFlux nhanh hơn MVC trên DB production.

**9. Nếu benchmark có RPS tăng nhưng p99 và errors đều tăng, có thể kết luận chiến thắng không?**

Không nếu SLA yêu cầu p99 và error budget. Phải so sánh successful RPS tại cùng ngưỡng chất lượng service.

**10. Bottleneck nằm ở database lock, đổi Netty/WebFlux có khắc phục trực tiếp không?**

Không. Nên tối ưu query/transaction/index/pool và concurrency trước.

## 20. Checklist đánh giá trước quyết định kiến trúc

1. Phát biểu rõ tiêu chí thành công: **successful RPS, p95/p99, error budget, CPU/RAM, cost**.
2. Vẽ và ghi chú toàn chuỗi I/O: ingress/proxy → handler → auth → DB/cache/upstream → serializer → response.
3. Xác minh từng **driver/client** blocking hay non-blocking; xem cả callbacks/schedulers, không chỉ return types.
4. Kiểm tra `.block()`, `Thread.sleep`, JDBC/JPA, synchronous filesystem, unbounded queues, `collectList`.
5. Nếu có blocking code trong WebFlux, dùng bounded adapter có timeout/concurrency budgets, theo dõi saturation; lên kế hoạch thay driver nếu lợi ích tương xứng.
6. Dùng R2DBC/WebClient/reactive clients chỉ khi phù hợp với DB, driver, team, operational tooling và production capabilities.
7. Benchmark MVC, MVC+WebClient/MVC async và WebFlux theo **cùng nghĩa vụ end-to-end**; không so sánh `Thread.sleep` với `Mono.delay`.
8. Đo nhiều workload: CPU-bound, remote high latency, DB contention, streaming, clients chậm và overload.
9. Theo dõi p50/p95/p99, successful RPS, timeouts/errors, CPU, heap/RSS, event-loop, threads, pool waits, queues, GC và downstream.
10. Kiểm thử security, transactions, cancellation, retries, backpressure, tracing/Reactor Context và resource cleanup.
11. Giữ giới hạn connections/queue/concurrency, không coi reactive backpressure là quota toàn hệ thống.
12. Nếu MVC đang đạt SLO ổn định với chi phí hợp lý, **không có nghĩa vụ** phải chuyển WebFlux. Chọn giải pháp đơn giản nhất đáp ứng yêu cầu được đo đạc.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — WebFlux overview, performance và concurrency model](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/new-framework.html)
- [Spring Framework 6.2 — WebFlux reference](https://docs.spring.io/spring-framework/reference/6.2/web/webflux.html)
- [Spring Framework 6.2 — Reactive libraries, Mono/Flux](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-reactive-libraries.html)
- [Spring Framework 6.2 — WebClient](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient.html)
- [Spring Framework 6.2 — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html)
- [Spring Boot 3.5 — SQL databases, R2DBC và DatabaseClient](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html)
- [Spring Boot — SQL script database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Project Reactor — FAQ: wrapping a synchronous/blocking call](https://projectreactor.io/docs/core/release/reference/faq.html#faq.wrap-blocking)
- [Project Reactor — Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html)
- [Project Reactor — Backpressure](https://projectreactor.io/docs/core/release/reference/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html)
- [R2DBC specification](https://r2dbc.io/spec/1.0.0.RELEASE/spec/html/)


</details>

- [Quay lại đầu trang](#back-to-top)
