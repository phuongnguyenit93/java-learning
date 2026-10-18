<a id="back-to-top"></a>

# Spring MVC bất đồng bộ và Streaming

## Menu
- [Vòng đời Servlet async request](#servlet-async-lifecycle)
- [Single-result async với Callable, DeferredResult và WebAsyncTask](#deferred-single-result)
- [Streaming với ResponseBodyEmitter, SseEmitter và StreamingResponseBody](#servlet-streaming)
- [Reactive return value được adapt trong Spring MVC](#reactive-return-values-in-mvc)
- [AsyncTaskExecutor, timeout, redispatch và ASYNC filter dispatch](#async-timeouts-executor-and-dispatch)
- [Ranh giới Async Spring MVC và WebFlux](#async-mvc-vs-webflux)

## <a id="servlet-async-lifecycle">Vòng đời Servlet async request</a>

<details>
<summary>Xem chi tiết</summary>

Servlet async processing cho phép request rời original container thread mà chưa hoàn tất HTTP response. Spring MVC xây async support trên capability đó của Servlet.

```text
REQUEST dispatch ban đầu
→ MVC start async processing
→ original request thread trả về container
→ result xuất hiện về sau
→ ASYNC dispatch quay lại container/MVC
→ MVC tiếp tục return-value processing
```

Request/response object vẫn gắn với async request; đây không phải HTTP request mới từ client.

Async lifecycle tạo thêm failure point: timeout, executor rejection, client disconnect và exception sau khi thread ban đầu đã rời đi. Không nên giả định thread-local context của initial dispatch tự xuất hiện ở thread sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="deferred-single-result">Single-result async với Callable, DeferredResult và WebAsyncTask</a>

<details>
<summary>Xem chi tiết</summary>

`Callable`, `DeferredResult` và `WebAsyncTask` đều biểu diễn **một kết quả cuối cùng**, nhưng khác nhau ở nơi tạo kết quả.

`Callable` yêu cầu Spring MVC chạy computation trên async executor. `DeferredResult` được application/event/operation khác hoàn tất từ bên ngoài. `WebAsyncTask` bọc callable cùng timeout/executor/callback configuration bổ sung.

Sau khi result được đặt, MVC thực hiện async redispatch rồi xử lý value như controller result ở lifecycle tiếp tục.

Dùng các type này khi application cuối cùng có **một** result. Chúng không phải streaming API và không phù hợp để push chuỗi value không giới hạn.

Ownership của timeout/cancel cần rõ: HTTP timeout không đảm bảo mọi external operation tự dừng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-streaming">Streaming với ResponseBodyEmitter, SseEmitter và StreamingResponseBody</a>

<details>
<summary>Xem chi tiết</summary>

Streaming giữ HTTP response mở trong khi dữ liệu được tạo dần.

`ResponseBodyEmitter` cho application gửi nhiều object và MVC ghi từng object qua message converter phù hợp. `SseEmitter` chuyên cho Server-Sent Events. `StreamingResponseBody` cho callback truy cập trực tiếp response `OutputStream` để stream byte.

```text
ResponseBodyEmitter / SseEmitter
→ object/event streaming qua MVC

StreamingResponseBody
→ callback ghi byte trực tiếp
```

Streaming vẫn cần suy nghĩ về slow client dù MVC không dùng Reactive Streams backpressure. Client chậm/disconnect có thể làm write block hoặc fail. Producer nên có giới hạn và release resource khi complete/error.

Khi byte đã commit, error handling không thể an toàn thay toàn bộ response bằng error body bình thường khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-return-values-in-mvc">Reactive return value được adapt trong Spring MVC</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC có thể adapt một số reactive return type qua `ReactiveAdapterRegistry`. Điều này hữu ích khi controller gọi reactive API nhưng application vẫn chạy trên Servlet MVC stack.

Single-value reactive type có thể được adapt thành deferred async completion. Multi-value publisher có thể được stream với streaming media type phù hợp hoặc được MVC adapt theo return-value handling tương ứng.

Boundary quan trọng: reactive **controller value** không biến toàn server runtime thành reactive. MVC vẫn dựa trên Servlet async và Servlet response I/O.

Dùng bridge này để giảm impedance giữa MVC application với reactive dependency. Nếu hệ thống cần end-to-end non-blocking, reactive codec và Reactive Streams semantics, WebFlux là owner đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-timeouts-executor-and-dispatch">AsyncTaskExecutor, timeout, redispatch và ASYNC filter dispatch</a>

<details>
<summary>Xem chi tiết</summary>

Async MVC cần production configuration ngoài việc chọn return type.

`AsyncSupportConfigurer` cấu hình `AsyncTaskExecutor`, default timeout và async interceptor. `Callable`/`StreamingResponseBody` cần executor capacity phù hợp cho blocking work; simple default executor không phải production sizing strategy dưới tải lâu dài.

Servlet/filter registration phải cho phép async dispatch khi cần. Một request có thể đi qua `REQUEST` dispatch, rời thread rồi quay lại bằng `ASYNC` dispatch; filter cần dispatcher-type behavior có chủ đích.

Timeout nên được align giữa MVC, Servlet container, remote client và application work. Web timeout ngắn hơn downstream call không được cancel có thể để lại wasted work sau khi client đã bỏ cuộc.

Thread-local context như logging MDC không tự nhảy qua executor; chỉ propagate context ứng dụng thực sự cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-mvc-vs-webflux">Ranh giới Async Spring MVC và WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

Async Spring MVC và WebFlux đều hỗ trợ application bất đồng bộ nhưng nền tảng khác nhau.

Spring MVC:
- Servlet API;
- blocking I/O là mô hình bình thường;
- async request mở rộng Servlet lifecycle;
- có thể adapt reactive return value.

WebFlux:
- reactive web runtime;
- non-blocking processing là first-class model;
- Reactive Streams/backpressure đi xuyên stack;
- deep mechanics của `WebClient` và reactive codec thuộc module đó.

Không chọn WebFlux chỉ vì một endpoint trả future/publisher; cũng không giả định MVC trở thành non-blocking chỉ vì initial request thread được nhả.

Quyết định phải dựa trên toàn dependency chain và operational model.

</details>

- [Quay lại đầu trang](#back-to-top)
