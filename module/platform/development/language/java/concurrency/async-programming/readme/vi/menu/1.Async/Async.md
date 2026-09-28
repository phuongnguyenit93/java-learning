<a id="back-to-top"></a>

# Future, CompletableFuture và Asynchronous Programming

## Menu
- [1. Async không đồng nghĩa Non-blocking](#async-mental-model)
- [2. Callable và Future](#future)
- [3. CompletableFuture: completion pipeline](#completable-future-pipeline)
- [4. thenApply và thenApplyAsync](#async-variant)
- [5. allOf, anyOf và semantics của kết quả](#combine-race)
- [6. Exception handling trong pipeline](#exception-handling)
- [7. Common pool và custom executor](#executor-choice)
- [8. Timeout và cancellation](#timeout-cancellation)
- [9. Các experiment của phần Async](#async-experiments)

Phần này học cách phối hợp **kết quả của task**, sau khi đã hiểu Thread Pool/Executor.

Mental model:

```text
Executor
→ thực thi task

Future / CompletableFuture
→ biểu diễn completion/result của task
```

## <a id="async-mental-model">1. Async không đồng nghĩa Non-blocking</a>

<details>
<summary>Click for details</summary>

Asynchronous mô tả quan hệ giữa caller và công việc:

```text
caller submit work
→ không nhất thiết chờ work hoàn thành ngay tại điểm submit
```

Nhưng worker thực thi công việc đó vẫn có thể block.

Ví dụ:

```java
CompletableFuture.supplyAsync(() -> blockingJdbcCall());
```

Caller có thể được giải phóng khỏi JDBC call, nhưng worker chạy lambda vẫn có thể bị block bởi JDBC.

Do đó cần phân biệt:

| Khái niệm | Câu hỏi |
| --- | --- |
| Async | Caller có phải chờ completion ngay không? |
| Parallel | Có nhiều work thực sự chạy đồng thời không? |
| Blocking | Thread thực thi có bị giữ trong lúc chờ không? |
| Non-blocking | Operation có tránh giữ Thread trong lúc chờ external event không? |

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future">2. Callable và Future</a>

<details>
<summary>Click for details</summary>

`Callable<T>` mô tả task có result/exception.

```java
Future<String> future = executor.submit(callable);
```

`Future` cho phép:

- kiểm tra `isDone()`;
- `cancel(...)`;
- lấy result bằng `get()`;
- `get(timeout, unit)`.

Điểm giới hạn: `future.get()` là blocking wait của caller.

Tham khảo:

```text
AsyncController#future()
GET /async/future
```

Demo giữ task chưa hoàn thành, xác nhận `isDone=false`, sau đó cho task chạy xong và gọi `get()`.

**Kết luận:** `Future` tách submit khỏi result retrieval, nhưng chỗ gọi `get()` vẫn có thể block.

### <a id="future-cancellation">Future cancellation và mayInterruptIfRunning</a>

`Future.cancel(...)` thay đổi completion state của Future thành cancelled nếu cancellation được chấp nhận. Tham số:

```java
future.cancel(false);
future.cancel(true);
```

không nên hiểu thành:

```text
false = task chắc chắn dừng nhẹ nhàng
true  = JVM kill task ngay lập tức
```

Mental model đúng hơn:

```text
cancel(false)
→ không yêu cầu interrupt task đang chạy
→ task đã chạy có thể tiếp tục tới completion tự nhiên

cancel(true)
→ implementation có thể interrupt Thread đang chạy task
→ task vẫn phải cooperate với interruption
```

Sau khi Future đã cancelled, `get()` không trả result của task mà ném `CancellationException`, kể cả trường hợp underlying code với `cancel(false)` vẫn tiếp tục chạy đến hết.

Tham khảo:

```text
AsyncController#futureCancellation()
GET /async/future-cancellation
```

Experiment chạy hai task đang block ở `CountDownLatch`:

```text
cancel(false)
→ Future cancelled
→ worker không bị interrupt
→ task vẫn block cho tới khi experiment chủ động release

cancel(true)
→ Future cancelled
→ worker đang await nhận InterruptedException
→ task kết thúc qua cancellation path
```

Điều này nối trực tiếp với chapter Interruption:

```text
Future cancellation
→ có thể request interruption
→ interruption vẫn là cooperative protocol
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-pipeline">3. CompletableFuture: completion pipeline</a>

<details>
<summary>Click for details</summary>

`CompletableFuture` cho phép gắn continuation thay vì buộc caller tự `get()` từng bước.

Các nhóm method quan trọng:

### Transformation

```text
thenApply
thenApplyAsync
```

### Flat composition

```text
thenCompose
thenComposeAsync
```

Dùng khi callback trả về một `CompletionStage` khác và muốn tránh nested future.

### Combine hai nhánh độc lập

```text
thenCombine
thenCombineAsync
```

### Side effect

```text
thenAccept
thenRun
```

Tham khảo:

```text
AsyncController#pipeline()
GET /async/pipeline
```

Pipeline demo:

```text
order task ───────────┐
                     ├─ thenCombine → total
exchange-rate task ──┘
                           ↓
                       thenApply
```

Hai nhánh đầu dùng custom executor cục bộ; executor được shutdown bằng completion callback.

### get() và join() khác nhau ở exception model

Cả `get()` và `join()` đều có thể chờ completion, nhưng API surface khác nhau:

```text
get()
→ checked InterruptedException
→ checked ExecutionException khi computation fail

join()
→ không khai báo checked exception
→ exceptional completion được expose qua CompletionException
→ cancelled future có thể ném CancellationException
```

`join()` không có nghĩa non-blocking. Nếu stage chưa complete, caller vẫn có thể phải chờ.

Trong experiment `allOf()`, code gọi `all.get(...)` với timeout để bounded wait toàn bộ barrier trước, sau đó dùng `join()` trên từng future đã completed để lấy typed result. Đây là lựa chọn API, không phải hai cơ chế execution khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-variant">4. thenApply và thenApplyAsync</a>

<details>
<summary>Click for details</summary>

Không nên học rule:

> `thenApplyAsync` chắc chắn chạy trên một Thread khác stage trước.

Rule hữu ích hơn:

- non-async dependent action có thể được thực thi bởi Thread hoàn thành stage hoặc Thread tham gia completion;
- async variant được submit để thực thi theo default async facility hoặc `Executor` được chỉ định;
- tên worker cụ thể không phải contract rằng nó bắt buộc khác worker stage trước.

Nếu execution policy quan trọng, truyền executor rõ ràng:

```java
future.thenApplyAsync(this::transform, executor);
```

Tham khảo:

```text
AsyncController#executionVariant()
GET /async/execution-variant
```

Experiment dùng một stage đã completed để cho thấy `thenApply` có thể chạy inline trên caller, trong khi `thenApplyAsync(..., executor)` được dispatch qua executor đã chỉ định. Kết luận cần giữ là **execution policy**, không phải giả định rằng mọi async variant luôn chạy trên một Thread khác stage trước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="combine-race">5. allOf, anyOf và semantics của kết quả</a>

<details>
<summary>Click for details</summary>

`allOf(...)` hoàn thành khi tất cả future đầu vào hoàn thành.

`allOf(...)` trả `CompletableFuture<Void>`; nếu cần result typed của từng future, application vẫn phải giữ reference và lấy result của từng stage sau khi barrier completion hoàn tất.

"Hoàn thành" ở đây không đồng nghĩa "thành công". Nếu một input future complete exceptionally, aggregate future của `allOf(...)` cũng complete exceptionally sau khi các input cần thiết đã hoàn tất theo contract.

Điểm dễ nhầm là `allOf()` **không phải fail-fast barrier** theo nghĩa "một child fail là aggregate lập tức hoàn tất". Ví dụ:

```text
A fail trước
B vẫn chưa complete

allOf(A, B)
→ aggregate vẫn chưa complete

B complete sau
→ lúc này aggregate complete exceptionally
```

Nếu business cần fail-fast orchestration và chủ động cancel work còn lại, application phải xây policy riêng thay vì suy ra từ `allOf()`.

`anyOf(...)` hoàn thành khi **một future đầu tiên** hoàn thành.

First completion có thể là success **hoặc failure**. Nếu future đầu tiên complete exceptionally thì aggregate `anyOf(...)` cũng có thể complete exceptionally ngay; nó không bỏ qua failure để chờ một future khác success.

Điều đó có nghĩa:

```text
anyOf = fastest completion
```

không phải:

```text
anyOf = best business result
```

Ví dụ hai server trả giá:

```text
Server A hoàn thành trước: 200 USD
Server B hoàn thành sau: 190 USD
```

`anyOf` có thể trả 200 USD vì A nhanh hơn; nó không tự biết 190 USD là "giá tốt hơn".

Failure race cũng tương tự:

```text
Server A fail trước
Server B success sau

anyOf(A, B)
→ first completion là failure
→ aggregate có thể complete exceptionally
```

Ngoài ra `allOf()` / `anyOf()` là **completion aggregation**, không phải cancellation policy. Không nên mặc định rằng aggregate complete/fail thì các sibling future còn lại sẽ tự bị cancel. Nếu business cần winner-cancels-losers hoặc fail-fast kèm cancellation, application phải thiết kế cancellation/cleanup riêng.

Tham khảo:

```text
AsyncController#fastest()
GET /async/fastest

AsyncController#allOf()
GET /async/all-of
```

Experiment điều khiển thứ tự completion bằng coordination signal thay vì dựa vào `sleep()` timing. Server A được phép hoàn thành trước, sau khi `anyOf` chọn winner thì Server B mới được release và cleanup trước khi experiment kết thúc.

Experiment còn tạo một race nhỏ không cần timing: một future được complete exceptionally trước, future còn lại success sau. Response xác nhận exceptional completion có thể thắng `anyOf` và losing future không bị cancel tự động.

`allOf()` còn chạy một case có controlled completion order: input A fail trước khi sibling B complete. Response xác nhận aggregate chưa complete ngay sau failure đầu tiên, chỉ complete sau khi sibling còn lại hoàn tất, rồi expose exceptional completion mà không tự cancel sibling:

```text
aggregateCompletedImmediatelyAfterFirstFailure = false
aggregateWaitedForRemainingSibling = true
aggregateExceptionalWhenInputFails = true
successfulSiblingCancelledAutomatically = false
```

**Kết luận:** primitive coordination không thay thế business selection rule.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling">6. Exception handling trong pipeline</a>

<details>
<summary>Click for details</summary>

Ba API thường gặp:

### exceptionally

Chỉ chạy khi stage trước thất bại và trả fallback value.

### handle

Nhận cả `(result, exception)` và luôn có thể biến outcome thành value mới.

### whenComplete

Quan sát success/failure nhưng thường không dùng để biến đổi outcome.

Tham khảo:

```text
AsyncController#exceptionHandling()
GET /async/exception
```

Demo tạo failure rồi `handle()` thành một result có kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-choice">7. Common pool và custom executor</a>

<details>
<summary>Click for details</summary>

Các overload không truyền executor của async factory/async continuation thường sử dụng default async facility của `CompletableFuture`, thông thường liên quan `ForkJoinPool.commonPool()`.

Không nên để blocking workload lớn vô tình chiếm common pool mà không hiểu tác động tới các feature khác trong process.

Custom executor hữu ích khi cần:

- resource isolation;
- queue/capacity riêng;
- thread naming;
- observability;
- shutdown policy;
- context propagation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="timeout-cancellation">8. Timeout và cancellation</a>

<details>
<summary>Click for details</summary>

Một production pipeline cần nghĩ đến:

- timeout;
- cancellation;
- propagation của interruption/cancellation tới underlying work;
- partial failure;
- cleanup.

Các API như `orTimeout(...)` và `completeOnTimeout(...)` giúp biểu diễn timeout ở stage level, nhưng không nên mặc định rằng timeout của future sẽ cưỡng bức mọi blocking operation bên dưới dừng ngay.

Phân biệt ba việc:

```text
get(timeout)
→ chỉ giới hạn thời gian caller chờ result

orTimeout / completeOnTimeout
→ thay đổi completion semantics của CompletableFuture

Future.cancel(true)
→ cancellation request có thể đi kèm interruption của running task

CompletableFuture.cancel(true)
→ cancel completion state
→ tham số mayInterruptIfRunning không điều khiển underlying processing của CompletableFuture
```

Không cơ chế nào trong ba dòng trên là một primitive "kill Thread bất kể code đang làm gì".

Tham khảo:

```text
AsyncController#timeoutCancellation()
GET /async/timeout-cancellation
```

Experiment chạy hai case riêng, mỗi case có executor/latch riêng và cleanup bounded:

```text
orTimeout
→ future complete bằng TimeoutException
→ underlying worker không tự nhận interrupt

CompletableFuture.cancel(true)
→ future chuyển sang cancelled state
→ join() ném CancellationException
→ worker đang await vẫn không bị interrupt bởi cancel(true)
→ experiment phải chủ động release underlying work
```

Đây là khác biệt quan trọng với `Future` do `ExecutorService.submit(...)` trả về ở phần trước: không được suy từ chữ `cancel(true)` rằng mọi implementation của `Future` đều có cùng interruption behavior.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-experiments">9. Các experiment của phần Async</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#future` | `AsyncController#future()` | `GET /async/future` |
| `#future-cancellation` | `AsyncController#futureCancellation()` | `GET /async/future-cancellation` |
| `#completable-future-pipeline` | `AsyncController#pipeline()` | `GET /async/pipeline` |
| `#async-variant` | `AsyncController#executionVariant()` | `GET /async/execution-variant` |
| `#combine-race` | `AsyncController#fastest()` | `GET /async/fastest` |
| `#combine-race` | `AsyncController#allOf()` | `GET /async/all-of` |
| `#exception-handling` | `AsyncController#exceptionHandling()` | `GET /async/exception` |
| `#timeout-cancellation` | `AsyncController#timeoutCancellation()` | `GET /async/timeout-cancellation` |

</details>

- [Quay lại đầu trang](#back-to-top)
