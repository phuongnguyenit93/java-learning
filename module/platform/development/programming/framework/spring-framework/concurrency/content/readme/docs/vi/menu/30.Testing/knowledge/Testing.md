<a id="back-to-top"></a>

# Kiểm thử

## Menu
- [Phân biệt kiểm thử logic tác vụ với kiểm thử thực thi bất đồng bộ](#testing-001)
- [Dùng SyncTaskExecutor cho kiểm thử phù hợp và nhận biết điều nó không chứng minh](#testing-002)
- [CountDownLatch, Semaphore hoặc hàng đợi để phối hợp kiểm thử xác định](#testing-003)
- [Chờ điều kiện với giới hạn thời gian thay cho ngủ cố định](#testing-004)
- [Kiểm tra lời gọi thật sự đi qua proxy @Async](#testing-005)
- [Kiểm thử nhận kết quả và cả đường lỗi của phương thức void hoặc Future](#testing-006)
- [Kiểm thử quá tải, từ chối, timeout và hủy](#testing-007)
- [Kiểm tra truyền rồi dọn ngữ cảnh qua nhiều tác vụ](#testing-008)
- [Kiểm thử cron, múi giờ và lịch chạy với nguồn thời gian có thể kiểm soát](#testing-009)
- [Kiểm tra dừng context và giải phóng executor trong kiểm thử](#testing-010)
- [Kiểm tra tranh chấp bằng phối hợp luồng thay vì chỉ tăng số lần chạy](#testing-011)

## <a id="testing-001">Phân biệt kiểm thử logic tác vụ với kiểm thử thực thi bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểm thử logic tác vụ với kiểm thử thực thi bất đồng bộ trong Spring Framework

## 1. Vì sao cần tách hai loại kiểm thử?

Khi một chức năng chạy qua `@Async`, `TaskExecutor` hoặc `CompletableFuture`, có **hai hợp đồng độc lập** cần chứng minh:

1. **Logic tác vụ (task logic):** Với đầu vào xác định, tác vụ xử lý đúng dữ liệu, áp dụng quy tắc nghiệp vụ, tạo kết quả/side effect đúng và xử lý dữ liệu lỗi theo hợp đồng.
2. **Thực thi bất đồng bộ (asynchronous execution):** Lời gọi được chuyển giao cho executor/proxy đúng cách; bên gọi có thể tiếp tục trước khi công việc hoàn tất; công việc được thực thi, hoàn thành hoặc báo lỗi đúng cách mà không tạo race/deadlock. Khi hợp đồng yêu cầu, phải kiểm tra cả thread, context, thứ tự hoặc chính sách từ chối tác vụ.

Ví dụ: `formatAsync("  spring ")` trả về `CompletableFuture<String>`. Kết quả `"SPRING"` đúng chỉ chứng minh giá trị cuối cùng. Một hàm chạy **đồng bộ** rồi trả `CompletableFuture.completedFuture("SPRING")` vẫn vượt qua phép thử đó. Ngược lại, việc tác vụ chạy trên một thread khác không bảo đảm nó xử lý đúng chuỗi.

Đường đi khi dùng Spring `@Async` theo proxy mặc định:

```text
Caller / HTTP request
       |
       v
Spring-managed proxy có @Async  ---->  TaskExecutor / hàng đợi / worker thread
                                               |
                                               v
                                    Task logic / dependency / I/O
                                               |
                                               v
                                    Future: kết quả hoặc lỗi
```

**Phân chia trách nhiệm:** Unit test đặt ở `Task logic`; integration test đặt ở ranh giới `Spring proxy → executor → task`. Không nhất thiết dùng toàn bộ Spring context trong bài kiểm thử quy tắc nghiệp vụ.

| Góc nhìn | Kiểm thử logic tác vụ | Kiểm thử thực thi bất đồng bộ |
| --- | --- | --- |
| Câu hỏi chính | Tính toán/quy tắc có đúng? | Tác vụ được dispatch, chờ và hoàn tất đúng? |
| Đối tượng | Hàm/lớp nghiệp vụ thuần Java | Proxy `@Async`, executor, future và phối hợp các thread |
| Thiết lập | Input, dependency giả, không cần thread nền | Spring context tối thiểu và executor thật hoặc bộ điều phối có kiểm soát |
| Assertion | Giá trị, exception, thay đổi trạng thái | Thời điểm hoàn tất, thread thực thi, kết quả/lỗi bất đồng bộ |
| Tính ổn định | Thường hoàn toàn xác định | Cần latch, timeout hữu hạn, kiểm soát trạng thái đồng thời |
| Lỗi phát hiện | Sai validation, phép tính, nhánh nghiệp vụ | Quên `@EnableAsync`, self-invocation, executor sai, test không chờ, race |

## 2. Thiết kế để kiểm thử hai lớp độc lập

Một cách dễ kiểm thử là giữ quy tắc nghiệp vụ trong đối tượng thuần Java; bean bất đồng bộ chỉ đóng vai trò chuyển việc cho nó. Ví dụ sau dùng **Java 17+, Spring Framework 6.2.x và JUnit Jupiter 5**.

Cấu trúc tệp tối thiểu:

```text
src/main/java/example/TaskLogic.java
src/main/java/example/DefaultTaskLogic.java
src/main/java/example/AsyncTaskService.java
src/test/java/example/TaskLogicTest.java
src/test/java/example/AsyncTaskServiceTest.java
```

Với dự án Gradle Java độc lập, có thể dùng `build.gradle` như sau (nếu dự án đã quản lý phiên bản bằng BOM thì dùng phiên bản do BOM quản lý):

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.13'
    testImplementation 'org.springframework:spring-test:6.2.13'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.11.4'
}

test {
    useJUnitPlatform()
}
```

**`src/main/java/example/TaskLogic.java`**

```java
package example;

public interface TaskLogic {
    String format(String raw);
}
```

**`src/main/java/example/DefaultTaskLogic.java`**

```java
package example;

import java.util.Locale;

public final class DefaultTaskLogic implements TaskLogic {
    @Override
    public String format(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        return raw.strip().toUpperCase(Locale.ROOT);
    }
}
```

**`src/main/java/example/AsyncTaskService.java`**

```java
package example;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

public class AsyncTaskService {
    private final TaskLogic logic;

    public AsyncTaskService(TaskLogic logic) {
        this.logic = logic;
    }

    @Async("lessonExecutor")
    public CompletableFuture<String> formatAsync(String raw) {
        return CompletableFuture.completedFuture(logic.format(raw));
    }
}
```

Ở đây, `format` không biết tới executor, Spring hoặc thời gian chờ. Phương thức `formatAsync` cũng không tự tạo thread. Khi bean được gọi **qua Spring proxy đã bật `@EnableAsync`**, Spring nộp lời gọi vào `lessonExecutor`. `completedFuture(...)` là future tạm được tạo **bên trong lần gọi trên worker**; Spring đưa ra future bất đồng bộ cho bên gọi. Nếu khởi tạo `new AsyncTaskService(...)` rồi gọi trực tiếp, annotation không được áp dụng.

## 3. Kiểm thử logic: nhanh, quyết định được kết quả

**`src/test/java/example/TaskLogicTest.java`**

```java
package example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskLogicTest {
    private final TaskLogic logic = new DefaultTaskLogic();

    @Test
    void formatsValidInput() {
        assertEquals("SPRING", logic.format("  spring  "));
        assertEquals("JAVA 17", logic.format("java 17"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> logic.format("   "));
        assertThrows(IllegalArgumentException.class,
                () -> logic.format(null));
    }
}
```

Những test này kiểm tra trực tiếp hành vi nghiệp vụ: cắt khoảng trắng hai đầu, chuyển chữ hoa bằng `Locale.ROOT`, chặn `null`/chuỗi trắng. Chúng **không chứng minh** executor hoặc proxy hoạt động, và đó là chủ đích. Không cần `Thread.sleep`, Spring context, hàng đợi hoặc biến dùng chung. Khi business rule thay đổi, lỗi được cô lập ngay tại tầng logic.

Với tác vụ thực tế, unit test nên bao phủ các nhánh quan trọng (đầu vào hợp lệ/biên/lỗi, tương tác dependency, idempotency nếu được yêu cầu) mà không ép thứ tự thread vào logic nghiệp vụ.

## 4. Kiểm thử bất đồng bộ: chứng minh điều mà unit test bỏ qua

Integration test phải lấy `AsyncTaskService` từ **Spring ApplicationContext**, khai báo `@EnableAsync` và executor có thread thật. Để loại bỏ phụ thuộc lịch chạy CPU, fake `ControlledLogic` dùng hai `CountDownLatch`:

- `started` báo worker đã **đi vào** logic.
- `release` giữ worker ở đó cho tới khi test cho phép hoàn tất.

Trong lúc worker còn chờ `release`, bên gọi phải nhận được future và có thể chạy assertion. Điều này chứng minh rõ hơn nhiều so với đo vài mili giây.

**`src/test/java/example/AsyncTaskServiceTest.java`**

```java
package example;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AsyncTaskServiceTest.Config.class)
class AsyncTaskServiceTest {
    @Autowired
    private AsyncTaskService service;

    @Autowired
    private ControlledLogic controlledLogic;

    @Test
    void dispatchesToWorkerAndReturnsBeforeCompletion() throws Exception {
        CompletableFuture<String> future;
        try {
            future = service.formatAsync("  spring  ");

            assertTrue(controlledLogic.started.await(10, SECONDS),
                    "worker never entered the task");
            assertNotSame(Thread.currentThread(),
                    controlledLogic.worker.get(),
                    "task must execute on a different thread");
            assertFalse(future.isDone(),
                    "task should still be blocked on release");
        } finally {
            // Luôn giải phóng worker, kể cả khi assertion thất bại.
            controlledLogic.release.countDown();
        }

        assertEquals("SPRING", future.get(10, SECONDS));
    }

    @Test
    void reportsWorkerFailureThroughFuture() {
        CompletableFuture<String> future = service.formatAsync(" ");

        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> future.get(10, SECONDS));

        assertInstanceOf(IllegalArgumentException.class,
                failure.getCause());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class Config {
        @Bean
        ControlledLogic controlledLogic() {
            return new ControlledLogic();
        }

        @Bean
        AsyncTaskService asyncTaskService(ControlledLogic logic) {
            return new AsyncTaskService(logic);
        }

        @Bean("lessonExecutor")
        ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("lesson-");
            return executor;
        }
    }

    static class ControlledLogic implements TaskLogic {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Thread> worker = new AtomicReference<>();

        @Override
        public String format(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("input must not be blank");
            }

            worker.set(Thread.currentThread());
            started.countDown();
            try {
                if (!release.await(10, SECONDS)) {
                    throw new IllegalStateException("task was never released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("worker interrupted", e);
            }

            return raw.strip().toUpperCase(Locale.ROOT);
        }
    }
}
```

Giải thích từng bằng chứng:

1. `started.await(10, SECONDS)` xác nhận worker đã vào tác vụ, tránh kiểm tra `isDone()` trước khi tác vụ thật sự bắt đầu.
2. `assertNotSame(...)` chứng minh lần gọi này chạy ở thread khác thread test. Assertion dựa vào **Thread object thực**, không phụ thuộc prefix tên thread.
3. `assertFalse(future.isDone())` chỉ đáng tin **vì worker đang bị giữ bằng latch**. Dùng assertion này với tác vụ cực ngắn mà không có latch sẽ gây flaky test.
4. Khối `finally` luôn nhả worker; `get(10, SECONDS)` sau đó chứng minh kết quả cuối cùng. Timeout hữu hạn bảo vệ CI khi có lỗi đồng bộ hóa.
5. Test lỗi gọi đầu vào trắng để fake ném `IllegalArgumentException` trên worker. `Future.get` đưa lỗi về dưới dạng `ExecutionException`; `getCause()` là lỗi nghiệp vụ. Với `join()`, wrapper thường là `CompletionException`.

Đây là **integration test cho cơ chế Spring**: nếu bỏ `@EnableAsync` hoặc gọi trực tiếp object thay vì proxy, phương thức sẽ chạy đồng bộ và bài thử dispatch sẽ thất bại (worker chờ `release` ngay trong lời gọi). Nếu lỡ thay executor bằng kiểu thực thi đồng bộ như `SyncTaskExecutor`, test cũng không còn xác nhận được đặc tính bất đồng bộ thật.

Chạy bộ kiểm thử trong dự án Gradle:

```bash
./gradlew test
```

Trên Windows dùng `gradlew.bat test`. Kỳ vọng: cả hai lớp kiểm thử vượt qua; không cần dựa vào nội dung stdout hay tên thread cụ thể. Tùy cấu hình dự án, có thể chạy riêng `--tests example.TaskLogicTest` hoặc `--tests example.AsyncTaskServiceTest`.

## 5. Các kiểm tra nâng cao: chọn theo hợp đồng, không kiểm thử mọi chi tiết

| Tình huống | Chứng minh bằng gì? | Chú ý |
| --- | --- | --- |
| Logic tính sai/validation sai | Unit test dữ liệu và exception | Không cần Spring hay executor |
| Lời gọi phải trả trước khi worker hoàn tất | Latch giữ worker; caller vẫn nhận future | Tránh đo elapsed time bằng ngưỡng mili giây |
| `@Async` phải hoạt động | Lấy bean qua context, `@EnableAsync`, executor thread thật; quan sát worker | Self-invocation trong cùng bean có thể bỏ qua proxy |
| Kết quả bất đồng bộ | `future.get(timeout, unit)` và assertion kết quả | Không assert ngay trạng thái dùng chung khi chưa có đồng bộ |
| Lỗi từ worker | `ExecutionException#getCause()` hoặc `CompletionException#getCause()` | `@Async void` không có future cho caller; cấu hình/test `AsyncUncaughtExceptionHandler` nếu cần |
| Side effect (DB, gửi message, lưu cache) | Chờ tín hiệu hoàn tất hoặc polling có deadline rồi kiểm tra hiệu ứng | Xác định mức bảo đảm: eventual, exactly once hay at-least-once |
| Task bị hủy/ngắt | Một test riêng dùng fake có thể quan sát `interrupt`/cleanup | `cancel(true)` không mặc nhiên đảm bảo đã dừng hoặc rollback nghiệp vụ |
| Executor đầy hoặc từ chối task | Test cấu hình pool/queue/rejection ở mức integration | Chính sách `CallerRunsPolicy` có thể chạy task trên thread gọi |
| Context thread (security, MDC, transaction) | Test riêng trên worker với cơ chế truyền context đã cấu hình | `ThreadLocal` không tự động đi từ caller sang worker |
| Nhiều task và thứ tự | Điều phối bằng latch/phaser rồi kiểm tra invariant | Đừng giả định thứ tự hoàn tất nếu hợp đồng không quy định |

**Quy tắc thực dụng:** mỗi test cần nêu rõ điều kiện nào khiến nó thất bại. Kiểm thử logic thất bại vì quy tắc sai; kiểm thử bất đồng bộ thất bại vì dispatch/completion/exception propagation sai. Nếu một test lẫn cả hai loại assertion mà không thể chẩn đoán, hãy chia nhỏ theo ranh giới phù hợp.

## 6. Các sai lầm phổ biến và cách sửa

**Sai lầm 1 — Chỉ kiểm tra giá trị của future.**

```java
// Test này KHÔNG chứng minh có chạy bất đồng bộ.
assertEquals("SPRING", service.formatAsync("spring").join());
```

`join()` có thể chờ kết quả từ executor hoặc lấy ngay kết quả đã tính đồng bộ. Giữ test này để xác minh kết quả cuối cùng, rồi thêm test điều phối bằng latch nếu cần chứng minh non-blocking execution.

**Sai lầm 2 — Dùng `Thread.sleep(100)` để “chờ đủ lâu”.** Máy CI chậm, GC và scheduling khiến thời gian ngủ không bảo đảm task hoàn tất; sleep dài lại tăng thời gian suite. Dùng `CountDownLatch`/`CompletableFuture.get(timeout, unit)` hoặc polling có thời hạn với điều kiện thực tế; luôn giải phóng tài nguyên trong `finally`.

**Sai lầm 3 — Gọi `new AsyncTaskService(...)` rồi cho rằng `@Async` đã kích hoạt.** Với proxy mode mặc định, Spring chỉ intercept lời gọi **đi qua proxy**. Một lời gọi `this.formatAsync(...)` từ phương thức khác trong cùng bean (self-invocation) cũng không đi qua proxy. Kiểm thử `@Async` cần bean lấy từ context; nếu tách một bean riêng để gọi, kiểm tra ranh giới giữa hai bean.

**Sai lầm 4 — Cho mọi test chạy bằng executor đồng bộ rồi tuyên bố đã kiểm tra async.** `SyncTaskExecutor`, `Runnable::run` hoặc fake executor chạy ngay rất hữu ích khi unit test logic điều phối thuần, vì chúng làm execution deterministic. Nhưng loại executor đó không chứng minh tác vụ chạy tách khỏi caller. Cần ít nhất một integration test với executor thật cho hợp đồng async.

**Sai lầm 5 — Test kết thúc khi tác vụ chưa xong.** Ví dụ gọi một phương thức `@Async void` rồi assert side effect ngay; thread khác có thể chưa ghi gì. Hãy tạo tín hiệu completion quan sát được, ưu tiên `Future`/`CompletableFuture` khi API cho phép. Với `void`, thử riêng cấu hình lỗi thông qua `AsyncUncaughtExceptionHandler`; exception của worker không tự bật lên ở test thread.

**Sai lầm 6 — Giả định transaction của test tự bao phủ worker.** Spring bind thông tin transaction vào thread; tác vụ `@Async` chạy thread khác thường không tham gia transaction do test thread mở. Vì vậy `@Transactional` trên test không phải bảo đảm rollback các ghi nhận từ worker. Cẩn thận khi dùng JUnit `assertTimeoutPreemptively`: assertion này chạy phần thân được đo trong một thread khác, dễ gây khác biệt về `ThreadLocal` và quản lý transaction. Ưu tiên `get(timeout, unit)` để chờ tác vụ và cleanup dữ liệu test rõ ràng.

**Sai lầm 7 — Kiểm thử chi tiết triển khai quá mức.** Không phải mọi tác vụ đều phải chạy thread tên `"lesson-"`, hoàn thành theo thứ tự gửi hoặc có `isDone() == false` ngay sau lệnh gọi. Chỉ khẳng định những thuộc tính mà contract yêu cầu; test thời gian thực, throughput và độ bền khi tải nặng thường cần bài đo/kiểm thử riêng.

## 7. Chiến lược áp dụng trong dự án Spring

1. Xác định **đầu vào, kết quả, exception và side effect** của tác vụ. Viết unit test cho chúng trên đối tượng nghiệp vụ không phụ thuộc `@Async`.
2. Xác định **hợp đồng bất đồng bộ quan sát được**: trả control cho caller, chạy executor nào, cách nhận lỗi, deadline, cancellation, context hoặc chính sách từ chối. Chỉ viết integration test cho những thuộc tính thật sự cam kết.
3. Tạo Spring context tối thiểu với `@EnableAsync` và executor được đặt tên; đi qua bean proxy. Dùng latch để kiểm soát một điểm bắt đầu/hoàn tất thay vì ngủ chờ.
4. Chờ kết quả hoặc hiệu ứng qua API có timeout hữu hạn; xác minh cả đường thành công và đường lỗi. Giải phóng latch, đóng executor/context và dọn tài nguyên ngay cả khi test thất bại.
5. Khi một test thỉnh thoảng lỗi, kiểm tra assumption về scheduling, trạng thái dùng chung, context và thứ tự; **không** tăng `sleep` tùy tiện để che race.

Một hệ kiểm thử tốt có **nhiều unit test logic gọn, ổn định** và **một số integration test bất đồng bộ có điều phối rõ ràng**. Hai nhóm bổ sung cho nhau: tính đúng nghiệp vụ không suy ra thực thi bất đồng bộ đúng, và việc chạy trên worker không suy ra dữ liệu được xử lý đúng.

## Tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `@Async`, proxy mode, executor qualification và exception handling.
- [JUnit 5 User Guide — Timeouts](https://docs.junit.org/5.13.1/user-guide/index.html): timeout, `assertTimeoutPreemptively` và khác biệt khi thực thi trong thread riêng.
- [Java SE 17 — `CompletableFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html) và [`CountDownLatch`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-002">Dùng SyncTaskExecutor cho kiểm thử phù hợp và nhận biết điều nó không chứng minh</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng SyncTaskExecutor để kiểm thử phù hợp và nhận biết điều nó không chứng minh

## Mục tiêu

`org.springframework.core.task.SyncTaskExecutor` là một implementation của `TaskExecutor` chạy công việc ngay trên luồng gọi `execute(Runnable)`. Nó đặc biệt hữu ích cho kiểm thử logic gửi task: chạy nhanh, có thứ tự có thể dự đoán và assertion được thực hiện sau khi task hoàn tất. Tuy nhiên, **bài test pass với executor đồng bộ không chứng minh việc chạy bất đồng bộ hoặc an toàn khi nhiều luồng**.

Mục tiêu bài học: biết dùng `SyncTaskExecutor` khi cần kiểm tra nội dung/side effect của task; mô tả chính xác điều test đã chứng minh; chọn thêm loại test phù hợp cho thread pool, context, lỗi bất đồng bộ và tương tranh.

## 1. Cơ chế và giới hạn cơ bản

`TaskExecutor` của Spring có một thao tác cốt lõi `execute(Runnable)`. Với `SyncTaskExecutor`, trình tự điển hình là:

1. Caller gọi `execute(task)`.
2. Executor gọi trực tiếp `task.run()` trong chính caller thread.
3. Task hoàn tất (hoặc ném exception) rồi `execute` mới trả về.

Hệ quả kiểm thử:

- Không sinh worker thread, không tạo queue riêng và không mang nghĩa “submit rồi trả về ngay”. Caller bận cho đến khi task hoàn tất.
- Task nhận cùng `ThreadLocal`, thread context class loader và ngữ cảnh transaction đang gắn với caller. Điều này có thể thuận tiện khi test nhưng có thể **che giấu lỗi ở ranh giới thread**.
- Với lệnh `execute` trực tiếp, unchecked exception từ `Runnable.run()` truyền về caller; đường gọi `@Async` có cơ chế xử lý exception riêng.
- **Một executor đồng bộ không phải một mutex toàn cục.** Nếu hai caller khác nhau cùng gọi `execute`, từng task chạy trên caller tương ứng; chúng vẫn có thể overlap. Test gọi hai lần tuần tự chỉ kiểm tra trường hợp tuần tự.

Trong Spring Framework 6.x, `SyncTaskExecutor` implements `TaskExecutor`, không implements `AsyncTaskExecutor`. Không giả định các phương thức như `submit` có ngay trên đối tượng này; khi Spring xử lý `@Async` nó có thể sử dụng adapter để submit task. `SyncTaskExecutor` không thay thế `ThreadPoolTaskExecutor` nếu mục tiêu là xác minh đa luồng, queue, saturation hoặc lifecycle.

## 2. Khi nào nên dùng để test?

| Kiểm tra | Lý do phù hợp |
| --- | --- |
| Service có gửi task khi điều kiện nghiệp vụ thỏa? | Side effect xảy ra trong chính test, dễ khẳng định nhánh đúng/sai. |
| Dữ liệu task xử lý có đúng, callback có được gọi, repository/mock có nhận đúng tham số? | Assertion chạy sau khi `execute` đã hoàn tất. |
| Task ghi kết quả hoặc phát ra domain event theo quy tắc đơn luồng? | Test logic độc lập với lịch chạy của scheduler. |
| Trong cấu hình test, đoạn code gọi executor có chạy đúng trước/sau một hành động khác? | Có thể assert thứ tự riêng cho executor đồng bộ. |
| Một Spring bean `@Async` có được gọi qua proxy trong cấu hình test xác định? | Dùng context thật với executor test để kiểm tra wiring và side effect; phải kiểm tra riêng proxy nếu muốn kết luận có advice. |

Ví dụ, service lập audit command rồi gọi `executor.execute(() -> auditRepository.save(command))` có thể được unit test với `SyncTaskExecutor`: assert command đúng, đúng một lần lưu và lỗi logic được xử lý. Test ấy **không** chứng minh transaction của worker, tốc độ trả về caller, hoặc thread safety khi production chạy nhiều task.

## 3. Ví dụ chạy được: Java 17+, Spring 6.2 và JUnit 5

Ba tệp sau tạo một project Gradle nhỏ, độc lập Spring Boot. Nếu đã có project Spring, có thể đưa code vào module tương ứng và dùng các dependency tương đương.

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.12'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

application {
    mainClass = 'SyncExecutorDemo'
}

test {
    useJUnitPlatform()
}
```

**`src/main/java/SyncExecutorDemo.java`** kiểm tra thread, thứ tự và unchecked exception của `execute` trực tiếp:

```java
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

public class SyncExecutorDemo {
    public static void main(String[] args) {
        TaskExecutor executor = new SyncTaskExecutor();
        Thread caller = Thread.currentThread();
        List<String> events = new ArrayList<>();

        events.add("before");
        executor.execute(() -> {
            events.add("work");
            System.out.println("sameThread=" +
                    (Thread.currentThread() == caller));
        });
        events.add("after");

        System.out.println("events=" + String.join(",", events));

        try {
            executor.execute(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ex) {
            System.out.println("error=" + ex.getMessage());
        }
    }
}
```

Chạy `./gradlew --quiet run`, hoặc trên Windows `gradlew.bat --quiet run`. Stdout do chính chương trình in ra:

```text
sameThread=true
events=before,work,after
error=boom
```

Giải thích: lambda chạy trước khi `execute` trả về, nên `work` nằm giữa `before` và `after`. Thread của lambda là caller thread; unchecked exception từ `execute` trực tiếp được `catch` của caller nhận. Không có worker hay song song nào được kiểm tra trong ví dụ này.

**`src/test/java/SyncExecutorTest.java`** chứa ba phép thử với ba mục tiêu khác nhau:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.*;

class SyncExecutorTest {

    @Test
    void taskIsFinishedBeforeExecuteReturns() {
        List<String> events = new ArrayList<>();
        Executor executor = new SyncTaskExecutor();

        events.add("before");
        executor.execute(() -> events.add("work"));
        events.add("after");

        assertEquals(List.of("before", "work", "after"), events);
    }

    @Test
    void asyncProxyCanUseSynchronousTestExecutor() {
        try (var context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            SampleService service = context.getBean(SampleService.class);
            Thread caller = Thread.currentThread();

            service.work();

            assertSame(caller, service.executedOn());
        }
    }

    @Test
    void realPoolRunsTaskOnAnotherThread() throws Exception {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(1);
        pool.initialize();
        try {
            CompletableFuture<Thread> observed = new CompletableFuture<>();
            pool.execute(() -> observed.complete(Thread.currentThread()));

            Thread worker = observed.get(2, TimeUnit.SECONDS);
            assertNotSame(Thread.currentThread(), worker);
        } finally {
            pool.shutdown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync(proxyTargetClass = true)
    public static class TestConfig {
        @Bean("testExecutor")
        public Executor testExecutor() {
            return new SyncTaskExecutor();
        }

        @Bean
        public SampleService sampleService() {
            return new SampleService();
        }
    }

    public static class SampleService {
        private Thread executedOn;

        @Async("testExecutor")
        public void work() {
            executedOn = Thread.currentThread();
        }

        public Thread executedOn() {
            return executedOn;
        }
    }
}
```

Chạy `./gradlew test` (Windows: `gradlew.bat test`). Không dự đoán stdout của Gradle/JUnit vì log phụ thuộc runner.

- `taskIsFinishedBeforeExecuteReturns` chỉ xác nhận thứ tự và completion khi đang dùng executor đồng bộ.
- `asyncProxyCanUseSynchronousTestExecutor` lấy bean từ Spring context, bật `@EnableAsync`, khai báo `@Async("testExecutor")` và cung cấp bean đúng tên. `work()` được gọi qua Spring proxy và chạy cùng thread **vì chọn executor đồng bộ cho test**. Test không chứng minh khả năng return sớm ở production. Nếu method được gọi bằng `this.work()` trong cùng bean, `@Async` proxy mặc định thường không intercept.
- `realPoolRunsTaskOnAnotherThread` dùng executor bất đồng bộ thật, lấy thread trong task qua future có timeout và shutdown trong `finally`. Assertion chỉ chứng minh task riêng này chạy trên thread khác, chưa chứng minh các task chạy đồng thời, đủ throughput hay an toàn trước race.

Chỉ tiêm executor test trong context test thích hợp; không thay toàn bộ cấu hình runtime chỉ để assertion dễ hơn. Nếu `@Async` có qualifier hoặc ứng dụng dùng nhiều executor, đảm bảo bean name thực sự được chọn và không bị cấu hình khác lấn át.

## 4. SyncTaskExecutor không chứng minh những điều gì?

### 4.1. Không chặn caller, khả năng overlap và tốc độ

Khi task chạy lâu với `SyncTaskExecutor`, caller sẽ đợi task. Vì vậy test đồng bộ không đo được việc caller có thể làm việc khác ngay, thời điểm completion của các task trong pool, throughput hay lợi ích song song. Muốn thử overlap, tạo ít nhất hai task trên executor thật và dùng `CountDownLatch`/`CyclicBarrier` để buộc cả hai tới điểm đồng bộ trước khi giải phóng. Dùng timeout hữu hạn để tránh treo test; giải phóng latch trong `finally`.

Một test gọi task A rồi task B trên cùng thread bằng executor đồng bộ luôn quan sát A xong trước B; kết quả ấy không nói gì về thứ tự hai task cùng được submit vào pool nhiều worker.

### 4.2. Race condition, visibility và thread safety

Đơn luồng có thể che lỗi lost update, data race, visibility của field không được đồng bộ hoặc collection không thread-safe. Để kiểm tra invariant của state dùng chung, tạo công việc có thể thực sự chồng lấn trên pool thật; xác định điều kiện đúng khi interleaving xảy ra, kiểm tra bằng assertion và lặp/stress test khi cần. Dù stress test nhiều lần pass cũng không phải chứng minh hình thức rằng không còn race.

Đừng lẫn việc `SyncTaskExecutor` dùng một caller trong **một test** với cam kết serialize mọi caller trong **toàn bộ ứng dụng**.

### 4.3. Queue, cấu hình pool, rejection, backpressure và lifecycle

`SyncTaskExecutor` không kiểm tra `corePoolSize`/`maxPoolSize`, `queueCapacity`, chính sách từ chối khi đầy, thời gian chờ, thread naming, shutdown hay xử lý task đang chờ. Những yêu cầu này phải được thử riêng bằng executor cấu hình tương đương production, đủ task để tạo tải và assertion rõ về completion, rejection, hoặc cleanup. Không dùng thời gian mili-giây đo trên CI làm ngưỡng tuyệt đối khi không kiểm soát môi trường.

### 4.4. Truyền context giữa các thread và transaction

Task đồng bộ dùng chung thread nên có thể đọc được `ThreadLocal`, MDC, `SecurityContext` và transaction liên kết với test thread. Đây là một nguồn **false positive**: worker thread thật không tự động mang theo toàn bộ context đó. Với test Spring `@Transactional`, transaction do test quản lý thường gắn với thread và rollback không tự bao trùm công việc trên thread khác. Muốn chứng minh propagation hoặc transaction boundary đúng, kiểm tra riêng trên thread thật, tạo context propagation theo thiết kế, quan sát commit/rollback và cleanup thực tế.

### 4.5. Spring @Async proxy và kiểu lỗi

`@Async` phụ thuộc Spring-managed bean, `@EnableAsync`, proxy interception và executor được chọn. Tính đúng của business logic không tự chứng minh advice đã chạy; self-invocation trong cùng bean qua `this.method()` có thể bypass proxy mặc định. Trong test wiring, nên kiểm tra đã gọi qua bean proxy và đúng executor (nếu đó là yêu cầu); sau đó thêm test với executor thật cho hành vi bất đồng bộ.

Phân biệt lỗi:

- `TaskExecutor.execute(Runnable)` **trực tiếp** trên `SyncTaskExecutor` thường ném unchecked exception trở về caller.
- `@Async` với method trả về `void` dùng `AsyncUncaughtExceptionHandler` cho lỗi không bắt; không trông chờ `assertThrows` ở caller là hợp đồng chung.
- `@Async` với `Future`/`CompletableFuture` cần quan sát completion và failure từ `get()`/`join()` (chú ý exception wrapping và timeout). Khi chuyển sang pool thật, không assert side effect ngay lập tức sau submit.

## 5. Chọn tầng test theo câu hỏi

| Tầng | Phương pháp | Chứng minh được |
| --- | --- | --- |
| Unit test nghiệp vụ | Logic độc lập hoặc `TaskExecutor` inject `SyncTaskExecutor`; assert ngay | Tham số, nhánh xử lý, side effect, call count và lỗi logic khi task đã chạy. |
| Test Spring wiring | Context nhỏ có `@EnableAsync`, bean `@Async`/qualifier, kiểm tra proxy và executor được chọn | Đường gọi qua infrastructure trong cấu hình test. |
| Integration test bất đồng bộ | `ThreadPoolTaskExecutor`/executor production, `Future`/`CompletableFuture` hoặc latch, timeout hữu hạn | Task thực thi trên thread khác và kết quả sau completion. |
| Concurrency và operational test | Nhiều task overlap có kiểm soát, queue đầy, rejection, shutdown, cleanup | Invariant dưới tranh chấp và hành vi vận hành khi có tải. |

Nếu invariant chỉ là “một audit command hợp lệ được xử lý đúng”, dùng `SyncTaskExecutor` là hợp lý. Nếu invariant là “caller không bị chặn, hai yêu cầu chạy song song mà không mất cập nhật”, cần test riêng với executor thật.

## 6. Những lỗi thường gặp

1. Dùng `Thread.sleep(...)` để đợi task: test dễ flaky; với executor đồng bộ assert ngay, với executor thật dùng `Future.get(timeout)`, latch `await(timeout)` hoặc điều kiện chờ rõ ràng.
2. Dùng test pass với `SyncTaskExecutor` làm bằng chứng không chặn hoặc thread-safe: kết luận vượt dữ kiện thực thi.
3. Gọi `new Service()` hoặc `this.asyncMethod()` rồi nghĩ đã kiểm tra `@Async`: gọi Spring bean qua proxy khi test annotation wiring.
4. Trộn lỗi nghiệp vụ và lỗi lịch chạy trong một test: tách unit test deterministic khỏi integration test về executor.
5. Tin rằng task của worker sẽ nhìn thấy transaction/security context vì test đồng bộ có thấy: kiểm tra qua thread boundary thật.
6. Đánh đồng lỗi trực tiếp từ `Runnable` với cách `@Async void` xử lý exception: kiểm tra handler/future theo hợp đồng.
7. Dùng pool thật mà không timeout, không release latch và không shutdown: dễ làm test suite treo/rò rỉ tài nguyên.
8. Cho rằng tất cả task dùng `SyncTaskExecutor` phải chạy trên duy nhất một thread: executor chạy trên **mỗi caller thread**, không áp đặt một thread chung.

## 7. Checklist trước khi chốt kết luận

- Tôi có ghi rõ executor đang dùng và phương thức gọi qua `execute`, adapter hay `@Async` không?
- Assertion chỉ nói về logic và side effect trong executor đồng bộ, hay đang suy diễn sang tính bất đồng bộ?
- Nếu claim thread khác, đã quan sát bằng executor thật và chờ kết quả có timeout?
- Nếu claim an toàn khi đồng thời, đã chủ động tạo overlap và kiểm tra invariant?
- Nếu claim context/transaction đúng, đã kiểm tra đường chạy qua thread boundary?
- Nếu claim `@Async` wiring đúng, đã đi qua proxy và dùng đúng qualifier?
- Với test bất đồng bộ, đã bảo đảm timeout, release primitive và shutdown executor?

`SyncTaskExecutor` giúp kiểm thử logic task nhanh, đơn giản và có thể dự đoán. Giá trị của nó nằm ở **phạm vi bằng chứng hẹp, rõ ràng**; đa luồng, context propagation, non-blocking và operational behavior phải được kiểm thử bằng một cơ chế thực thi phù hợp khác.

## Tài liệu chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): TaskExecutor, SyncTaskExecutor, @Async và các loại executor.
- [SyncTaskExecutor Javadoc (Spring Framework 6.2.12)](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/SyncTaskExecutor.html): synchronous execution trên caller thread, thread context.
- [Spring Test-managed Transactions](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html): test transaction gắn với thread và hạn chế khi đổi thread.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-003">CountDownLatch, Semaphore hoặc hàng đợi để phối hợp kiểm thử xác định</a>

<details>
<summary>Xem chi tiết</summary>

# Phối hợp kiểm thử concurrency xác định bằng CountDownLatch, Semaphore và BlockingQueue

> **Phạm vi:** kiểm thử hành vi chạy đồng thời trong Java và Spring Framework (đặc biệt `@Async`, `TaskExecutor`, listener hoặc tác vụ nền). Mục tiêu là chủ động tạo điểm đồng bộ, quan sát trạng thái đúng thời điểm và xác nhận kết quả mà không đoán lịch chạy của hệ điều hành.

## 1. Thế nào là một kiểm thử đồng thời xác định?

Một kiểm thử concurrency thường có hai yêu cầu khác nhau:

1. **Điều khiển thứ tự tại điểm cần kiểm tra:** ví dụ, worker đã vào phương thức nhưng phải dừng trước khi cập nhật dữ liệu; test kiểm tra trạng thái cũ rồi mới cho worker tiếp tục.
2. **Chờ một sự kiện có ý nghĩa:** ví dụ, hai worker đều đã vào vùng tới hạn, một bản ghi đã được xử lý, hoặc listener đã phát sự kiện kết quả.

`Thread.sleep(500)` không đảm bảo hai yêu cầu này: 500 ms có thể thừa trên máy nhanh và thiếu trên CI. Các primitive đồng bộ tạo quan hệ thứ tự dựa trên sự kiện thực tế, còn **timeout chỉ là hạn trên để phát hiện lỗi/treo**, không phải cơ chế điều phối lịch chạy.

Một kịch bản tốt có dạng:

```text
Test tạo worker → worker phát tín hiệu "đã tới điểm X"
                → worker chờ cổng cho phép
Test chờ "đã tới X" → kiểm tra tiền điều kiện
                     → mở cổng
Test chờ Future/kết quả/sự kiện → kiểm tra hậu điều kiện
```

Không thể đảm bảo chính xác thứ tự CPU chạy tất cả instruction. Ta chỉ ép thứ tự ở những điểm đồng bộ quan trọng, sau đó kiểm tra thuộc tính cần chứng minh.

### Bảng chọn công cụ

| Công cụ | Tín hiệu cốt lõi | Hợp với kiểm thử nào? | Không nên kỳ vọng |
| --- | --- | --- | --- |
| `CountDownLatch` | Bộ đếm một lần về 0; `countDown()`/`await()` | Đợi N worker sẵn sàng/hoàn thành; giữ worker trước một bước xử lý; phát lệnh bắt đầu đồng loạt | Không tự đặt lại để dùng cho nhiều vòng; không truyền payload |
| `Semaphore` | Số permit; `acquire()`/`release()` | Chứng minh giới hạn số công việc đồng thời, chặn tài nguyên, điều tiết admission | Không biểu diễn thứ tự hoàn thành hay dữ liệu của công việc |
| `BlockingQueue<T>` | Truyền phần tử giữa producer và consumer | Đợi sự kiện/kết quả cụ thể, quan sát thứ tự FIFO với loại queue phù hợp, tạo backpressure khi queue hữu hạn | `take()` có thể treo vô hạn; phần tử được đưa vào queue chưa chắc mọi công việc khác đã hoàn tất |

Các công cụ có thể phối hợp: latch xác nhận worker đang ở đúng pha; semaphore giới hạn số worker trong pha; queue chuyển kết quả từ worker về test.

## 2. CountDownLatch: kiểm soát các pha sẵn sàng, bắt đầu và hoàn thành

Khởi tạo `new CountDownLatch(n)` với số tín hiệu phải nhận. Mỗi `countDown()` giảm bộ đếm (không âm); `await(timeout, unit)` trả `true` khi đã về 0 và `false` nếu quá hạn. Sau khi về 0, latch luôn mở; cần vòng thử mới thì tạo latch mới.

Đặt tên latch theo **điểm sự kiện**, không dùng một latch mơ hồ cho mọi trạng thái:

- `ready = new CountDownLatch(n)`: mỗi worker xác nhận đã đến điểm bắt đầu.
- `start = new CountDownLatch(1)`: test mở cổng cho worker được xử lý.
- `done = new CountDownLatch(n)`: ghi nhận hoàn thành nếu không có `Future` đại diện cho tác vụ.

**Không đánh đồng "ready" và "done".** `ready.countDown()` chỉ chứng tỏ worker tới nơi đặt tín hiệu; nó không chứng tỏ logic sau đó đã chạy.

### Ví dụ chạy độc lập với JDK 17+

Tệp `LatchCoordinationDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LatchCoordinationDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> job = executor.submit(() -> {
                entered.countDown(); // Đã tới điểm kiểm thử
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("release timeout");
                    }
                    processed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", e);
                }
            });

            if (!entered.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("worker did not enter");
            }
            if (processed.get() != 0) {
                throw new AssertionError("processed too early");
            }
            System.out.println("before=0");

            release.countDown();
            job.get(3, TimeUnit.SECONDS); // Chờ xong và lan truyền lỗi worker

            if (processed.get() != 1) {
                throw new AssertionError("unexpected result");
            }
            System.out.println("after=1");
        } finally {
            release.countDown(); // Không bỏ mặc worker nếu assertion thất bại
            executor.shutdownNow();
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                throw new AssertionError("executor did not terminate");
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 LatchCoordinationDemo.java
java LatchCoordinationDemo
```

Stdout khi chạy thành công, đúng thứ tự:

```text
before=0
after=1
```

**Vì sao xác định?** Sau khi `entered.await()` thành công, test biết worker đã gọi `entered.countDown()`; nhưng worker phải đi qua `release.await()` trước khi tăng `processed`. Bởi vậy giá trị 0 tại checkpoint được bảo đảm bởi thiết kế cổng, không bởi tốc độ máy. Sau `release.countDown()`, `job.get()` xác nhận worker đã kết thúc hoặc ném lỗi; giá trị cuối phải là 1.

Với nhiều worker muốn tạo cạnh tranh tại cùng một điểm, dùng `ready(n)` + `start(1)`; mỗi worker `ready.countDown()` rồi `start.await()`. Test `ready.await(timeout)` trước khi mở `start`. Nếu executor chỉ có 1 thread nhưng `n=3` và ba task đều chặn trước `ready.countDown()` của task tiếp theo, test sẽ deadlock/timeout: **kích thước pool phải đủ để đạt barrier**. Nếu muốn quan sát kết quả từng worker, vẫn nên giữ `Future<?>` và gọi `get(timeout)`.

## 3. Semaphore: kiểm tra giới hạn số tác vụ đồng thời

`new Semaphore(2)` cho phép tối đa hai lần giữ permit cùng lúc **nếu tất cả đường vào đều tuân thủ acquire/release**. `tryAcquire(timeout, unit)` cho phép fail-fast; `release()` phải ở `finally` và chỉ chạy sau khi thật sự acquire thành công. Semaphore không tự gắn permit với một thread; code phải tự cân bằng số permit, tránh release thừa.

### Ví dụ: đúng hai worker giữ permit, worker thứ ba không được vào

Tệp `SemaphoreLimitDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SemaphoreLimitDemo {
    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(2);
        CountDownLatch inside = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable work = () -> {
            boolean acquired = false;
            try {
                acquired = permits.tryAcquire(3, TimeUnit.SECONDS);
                if (!acquired) {
                    throw new IllegalStateException("no permit");
                }

                int now = active.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                inside.countDown();

                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            } finally {
                if (acquired) {
                    active.decrementAndGet();
                    permits.release();
                }
            }
        };

        try {
            Future<?> first = executor.submit(work);
            Future<?> second = executor.submit(work);

            if (!inside.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("both workers did not enter");
            }

            // Cả hai permit đang được giữ: không thể lấy permit thứ ba.
            if (permits.tryAcquire()) {
                permits.release();
                throw new AssertionError("concurrency limit was broken");
            }

            release.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);

            if (peak.get() != 2 || active.get() != 0
                    || permits.availablePermits() != 2) {
                throw new AssertionError("invalid permit accounting");
            }
            System.out.println("peak=2, permits=2");
        } finally {
            release.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                throw new AssertionError("executor did not terminate");
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 SemaphoreLimitDemo.java
java SemaphoreLimitDemo
```

Stdout khi thành công:

```text
peak=2, permits=2
```

`inside.await()` đảm bảo **cả hai permit đang được giữ** ở thời điểm assertion; `tryAcquire()` lập tức kiểm tra permit thứ ba. `peak` xác nhận tối thiểu hai worker từng ở trong vùng có permit; mã đảm bảo không có quá hai permit. `availablePermits()` ở cuối là phép kiểm tra kế toán sau khi mọi `Future` đã xong, không phải tín hiệu dùng để chờ trong lúc hệ thống còn chạy.

**Fairness:** `new Semaphore(2, true)` có chính sách xếp hàng công bằng hơn ở các điểm acquire chặn, nhưng không có nghĩa test được phép khẳng định chính xác thứ tự mọi thread thức dậy. Không viết assertion phụ thuộc vào thread A luôn nhận permit trước B nếu bài toán chỉ yêu cầu giới hạn số tác vụ.

## 4. BlockingQueue: đợi kết quả hoặc sự kiện thực, không poll trạng thái bằng sleep

`BlockingQueue` vừa chứa payload vừa đồng bộ giữa người gửi và người nhận. Với kiểm thử, thường dùng một queue hữu hạn như `ArrayBlockingQueue<>(capacity)` để nhận sự kiện kết thúc hoặc kết quả từ callback/worker:

- Producer: `offer(event, timeout, unit)` trả boolean; kiểm tra `false` khi queue đầy, hoặc `put(event)` khi thật sự chấp nhận chờ không thời hạn.
- Consumer/test: `poll(timeout, unit)` trả giá trị hoặc `null` khi quá hạn; tránh `take()` vô hạn trong test.
- `ArrayBlockingQueue` giữ thứ tự FIFO của những phần tử đã vào queue; thứ tự các producer **đạt đến thao tác enqueue** vẫn do lịch chạy quyết định nếu không có cơ chế khác phối hợp.

### Ví dụ Java 17 nhận đúng sự kiện

Tệp `QueueEventDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class QueueEventDemo {
    public static void main(String[] args) throws Exception {
        BlockingQueue<String> events = new ArrayBlockingQueue<>(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> producer = executor.submit(() -> {
                try {
                    if (!events.offer("saved:42", 3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("queue is full");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", e);
                }
            });

            String event = events.poll(3, TimeUnit.SECONDS);
            if (!"saved:42".equals(event)) {
                throw new AssertionError("missing or wrong event: " + event);
            }
            producer.get(3, TimeUnit.SECONDS);
            System.out.println("event=saved:42");
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                throw new AssertionError("executor did not terminate");
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 QueueEventDemo.java
java QueueEventDemo
```

Stdout khi thành công:

```text
event=saved:42
```

Test không cần đoán producer phải xử lý trong bao nhiêu mili-giây. `poll` đợi **chính sự kiện `saved:42`** với hạn trên 3 giây. `producer.get()` quan trọng vì một sự kiện đã được gửi chưa chứng minh mọi câu lệnh còn lại trong task đều thành công. Với nhiều loại thông báo, dùng kiểu event rõ ràng (record/sealed hierarchy hoặc DTO), tránh chuỗi khó phân biệt.

Trong Spring, có thể truyền một `BlockingQueue<DomainEvent>` vào một fake event publisher/test listener đặt trong test context. Service phát event qua interface của ứng dụng; fake đưa event vào queue; test `poll(timeout)` rồi assert payload. Cách này thường rõ hơn việc liên tục đọc `repository.count()` trong một vòng ngủ.

## 5. Kiểm thử Spring `@Async` có kiểm soát

Spring `@Async` thực thi lời gọi **qua proxy** trên một `TaskExecutor`. Test cần gọi bean đã được Spring quản lý và dùng executor biết trước; gọi `this.asyncMethod()` trong cùng bean sẽ không đi qua proxy ở chế độ proxy mặc định. Với tác vụ có kết quả, ưu tiên `CompletableFuture<T>` để lỗi từ worker đến được test qua `get(timeout)`. Phương thức `@Async void` cần cơ chế quan sát exception riêng, ví dụ `AsyncUncaughtExceptionHandler` hoặc event báo lỗi.

Ví dụ integration test với Spring Test + JUnit Jupiter (tệp `AsyncCoordinationTest.java`). Đây là **test phụ thuộc Spring/JUnit**, không phải chương trình JDK thuần để chạy bằng `javac` một mình.

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(AsyncCoordinationTest.Config.class)
class AsyncCoordinationTest {
    @Autowired
    WorkService service;

    @Test
    void workerCannotProcessBeforeRelease() throws Exception {
        CompletableFuture<Integer> result = service.process();
        try {
            assertTrue(service.entered.await(3, TimeUnit.SECONDS),
                    "worker never entered");
            assertEquals(0, service.processed.get());

            service.release.countDown();
            assertEquals(1, result.get(3, TimeUnit.SECONDS));
            assertEquals(1, service.processed.get());
        } finally {
            service.release.countDown();
            result.cancel(true);
        }
    }

    @Configuration
    @EnableAsync
    static class Config {
        @Bean("testExecutor")
        ThreadPoolTaskExecutor testExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(1);
            executor.setThreadNamePrefix("test-async-");
            return executor; // Spring quản lý init/shutdown của bean
        }

        @Bean
        WorkService workService() {
            return new WorkService();
        }
    }

    static class WorkService {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger processed = new AtomicInteger();

        @Async("testExecutor")
        public CompletableFuture<Integer> process() {
            entered.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("release timeout"));
                }
                return CompletableFuture.completedFuture(
                        processed.incrementAndGet());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(e);
            }
        }
    }
}
```

Điều kiện chạy: test runtime có `spring-context`, `spring-test` và JUnit Jupiter; bộ chạy test của Maven/Gradle được cấu hình cho JUnit 5. Dùng dependency versions tương thích với Spring Framework của dự án. Khi test thành công, JUnit báo test **passed**; ví dụ này không in stdout cố định.

Điểm quyết định: test gọi `service.process()` **qua bean proxy**, chờ `entered`, xác minh chưa tăng bộ đếm, mở `release`, rồi `result.get(3, SECONDS)` để vừa chờ hoàn tất vừa thấy exception. Không dùng `CompletableFuture.isDone()` được đọc ở thời điểm tùy ý để suy luận thứ tự. Executor riêng có tên giúp hạn chế phụ thuộc vào pool mặc định/đang dùng của ứng dụng.

**Thiết kế test hook:** Trong ví dụ nhỏ, latch nằm trên bean để minh họa. Với ứng dụng thật, tránh đưa latch dùng riêng cho test vào public API production. Ưu tiên truyền một collaborator/test double, callback ở ranh giới domain, hoặc một cấu hình test riêng có checkpoint rõ ràng. Mỗi lần chạy/test case phải có latch, queue và dữ liệu trạng thái độc lập để không dính tín hiệu cũ. Nếu Spring test context được cache hoặc test chạy song song, bean mutable singleton như ví dụ phải được cô lập theo test hoặc reset bằng thiết kế riêng.

## 6. Các kịch bản kiểm thử và cách phối hợp

### 6.1 Bắt một interleaving quan trọng để tái hiện race

Giả sử hai yêu cầu đọc cùng trạng thái rồi ghi cập nhật. Để thử optimistic locking hoặc một cơ chế loại trừ:

1. Đặt checkpoint trong test double **sau khi cả hai worker đã đọc** nhưng **trước khi ghi**.
2. Mỗi worker gọi `bothRead.countDown()` rồi `allowWrite.await(timeout)`.
3. Test chờ `bothRead.await(timeout)`; xác nhận hai lần đọc cùng version/trạng thái nếu đó là tiền điều kiện của kịch bản.
4. Mở `allowWrite.countDown()`; chờ hai `Future.get(timeout)`.
5. Assert thuộc tính domain: chẳng hạn đúng một cập nhật được chấp nhận và một thao tác nhận lỗi conflict; không assert "thread A chắc chắn thắng".

Thiết kế như vậy lặp lại được cạnh tranh tại checkpoint nhưng vẫn cho phép các lịch chạy hợp lệ bên trong pha ghi. Nếu cần kiểm tra một thứ tự cụ thể, thêm handshake riêng cho từng bước thay vì mặc định scheduler tạo thứ tự đó.

### 6.2 Chứng minh giới hạn tải

Đặt `Semaphore(k)` ở nơi ứng dụng giới hạn tài nguyên (hoặc trong fake mô phỏng). Test cho `k` worker đồng loạt lấy permit, chặn lại ở một latch, xác nhận đã vào đủ `k` worker; kiểm tra một lần `tryAcquire()` kế tiếp thất bại khi tất cả permit đang bị giữ. Sau khi mở gate, chờ mọi future và xác nhận permit đã được trả. **Không chỉ đọc `availablePermits()` giữa lúc các thread tự do chạy** rồi coi đó là bằng chứng lịch sử.

### 6.3 Chờ kết quả của listener/consumer

Với event listener, callback sau khi lưu dữ liệu, hoặc consumer xử lý message, fake sink đưa kết quả vào `BlockingQueue<Result>`. Test dùng `poll(timeout)` để nhận kết quả cụ thể, assert ID/payload/status, sau đó (nếu có) `Future`, ack hoặc trạng thái lưu trữ để xác nhận hoàn thành đầy đủ. Với nhiều event, định danh theo correlation ID; không kiểm tra "phần tử đầu tiên bất kỳ" nếu hệ thống có nhiễu từ các luồng khác. Với queue hữu hạn, xác định rõ cách xử lý khi đầy; không để worker chặn vĩnh viễn trên `put` mà test không thể kết thúc.

### 6.4 Khi chạy trong Spring Test

- `@Async`: bật `@EnableAsync`, chọn đúng executor, gọi qua proxy. `Future.get(timeout)` quan sát lỗi từ task; với phương thức `void` phải thu thập lỗi qua handler/test hook.
- `@Scheduled`: scheduler khác với executor xử lý `@Async`. Nếu mục tiêu là kiểm tra logic xử lý, gọi logic qua collaborator trực tiếp; nếu kiểm tra scheduling, quan sát số lần/event bằng latch hoặc queue và cấu hình scheduler riêng để không có tác vụ nền từ test khác xen vào.
- `@Transactional`: transaction do Spring Test quản lý thường gắn với thread của test; thread `@Async` không tự kế thừa transaction đó. Chuẩn bị dữ liệu đã commit hoặc kiểm soát ranh giới transaction riêng; không giả định rollback của thread test bao phủ thao tác của worker.
- Test chạy song song: sử dụng executor, latch, queue và dữ liệu riêng cho mỗi test; không tái sử dụng một `CountDownLatch` đã về 0 hoặc một queue còn event từ lần trước. Tránh tăng kích thước thread pool chỉ để che deadlock do hàng đợi bị chặn không đúng thiết kế.

## 7. Happens-before và giới hạn bằng chứng

Các lớp trong `java.util.concurrent` cung cấp quan hệ visibility theo Java Memory Model:

- Với `CountDownLatch`, hành động xảy ra **trước `countDown()`** của thread phát tín hiệu happens-before hành động xảy ra **sau `await()` trả về thành công** ở thread chờ.
- Với `Semaphore`, hành động trước `release()` happens-before hành động sau một `acquire()` thành công tương ứng ở thread khác.
- Với `BlockingQueue`, hành động trước khi đưa phần tử vào queue happens-before hành động sau khi thread khác lấy phần tử đó ra.
- Với `Future`, hành động của tác vụ trước khi hoàn tất happens-before hành động sau khi thread khác lấy kết quả bằng `get()`.

Điều này giúp test đọc trạng thái đã công bố đúng cách mà không cần thêm `sleep`. Nhưng **một tín hiệu chỉ chứng minh những gì code đặt trước tín hiệu**. Nếu gọi `entered.countDown()` trước khi ghi database thì `entered.await()` không chứng minh đã ghi database. Nếu `queue.offer(event)` diễn ra giữa một quy trình dài, việc nhận event không chứng minh các bước sau đó đã hoàn thành. Chọn điểm đặt tín hiệu khớp điều kiện muốn assert.

`CountDownLatch` không thay thế cơ chế bảo vệ trạng thái chia sẻ: `HashMap` vẫn không an toàn cho ghi đồng thời chỉ vì test dùng latch; production phải dùng đồng bộ/collection phù hợp. Tương tự, một test đã ép được một interleaving không chứng minh mọi interleaving đều an toàn; kết hợp unit test, test cạnh tranh nhiều lần và phân tích invariant.

## 8. Lỗi phổ biến và cách khắc phục

| Lỗi | Vì sao gây sai/flaky/hang | Cách làm |
| --- | --- | --- |
| `Thread.sleep(...)` rồi assert | Dựa vào tốc độ máy và tải CI | Chờ sự kiện/latch/queue/future với timeout |
| `latch.await()` không timeout | Worker lỗi trước khi `countDown` khiến test treo | Dùng `await(n, SECONDS)` và assert kết quả boolean |
| Đếm `ready` sai hoặc pool nhỏ hơn số worker đang chặn | Không đủ task đạt barrier | Kiểm lại số tín hiệu và khả năng chạy đồng thời của executor |
| `countDown()` đặt trước thao tác muốn xác nhận | Tín hiệu đến quá sớm | Phát `done` sau khi hoàn tất thao tác; nếu cần lỗi thì dùng `Future.get` |
| `release()` semaphore dù chưa acquire | Số permit tăng sai, test có thể "đậu" giả | Theo dõi `acquired` và release đúng một lần trong `finally` |
| Quên mở cổng trong `finally` | Assertion thất bại để lại worker treo | `finally` mở gate, shutdown executor và chờ kết thúc có hạn |
| Chỉ assert queue size/`isEmpty()` | Snapshot tức thời, không xác nhận event cụ thể | `poll(timeout)`, assert payload/correlation ID và completion |
| Gửi event rồi mặc định worker đã xong | Event có thể phát trước bước cuối | Chờ thêm `Future.get`/ack hoặc phát completion event đúng cuối pipeline |
| Bỏ qua exception của task | Test chính có thể pass dù worker throw | Giữ `Future` hoặc `CompletableFuture`; `get(timeout)` |
| Gọi `@Async` bằng `this.method()` | Không qua Spring proxy, chạy đồng bộ | Gọi qua bean được Spring inject và kiểm tra executor |
| Dùng chung latch/queue giữa các test | Tín hiệu cũ gây false positive, race xuyên test | Tạo trạng thái mới theo từng kịch bản, dọn sạch trong teardown |
| Assertion phụ thuộc thread ID/thứ tự thắng | Scheduler không bảo đảm thứ tự | Assert invariant và event ordering do test chủ động tạo |

**Timeout thực dụng:** chọn hạn đủ rộng cho CI (ví dụ vài giây), nhất quán với thời gian chờ của worker và test, có thông báo chỉ rõ pha bị kẹt. Tăng timeout chỉ giúp tránh báo giả do máy quá tải; timeout không sửa logic đồng bộ sai. Khi worker bị interrupt, khôi phục cờ interrupt bằng `Thread.currentThread().interrupt()`, và bảo đảm cleanup không bỏ lại thread.

## 9. Checklist thiết kế một test concurrency

1. **Invariant:** định nghĩa điều phải đúng (đã đến checkpoint, tối đa `k` job đồng thời, event đúng ID, chỉ một giao dịch thành công...).
2. **Tín hiệu:** chọn latch cho checkpoint, semaphore cho giới hạn, queue cho payload; có thể ghép nhiều cơ chế.
3. **Pha:** phân biệt `ready` / `release` / `done`; mọi checkpoint đặt ở nơi thật sự mang ý nghĩa domain.
4. **Khả năng chạy:** thread pool đủ chỗ cho các task phải cùng đứng tại barrier; không tạo vòng chờ vô tận.
5. **Giới hạn chờ:** tất cả `await`, `tryAcquire`, `poll`, `Future.get` trong test có timeout và kiểm tra giá trị trả về.
6. **Sai hỏng:** exception worker phải đến được test; báo lỗi phải giúp xác định pha nào không đạt.
7. **Dọn dẹp:** mọi gate cuối cùng được mở; permit được trả chính xác; executor được shutdown; trạng thái dùng cho test độc lập.
8. **Spring integration:** proxy/executor đúng, thread-bound transaction được hiểu chính xác, callback/event đã được quan sát ở đúng điểm.
9. **Khả năng lặp lại:** chạy test độc lập và trong suite song song; chỉ assert thứ tự được bảo đảm bởi cơ chế đồng bộ.

## Tài liệu chính thống

- [Java 17 CountDownLatch API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)
- [Java 17 Semaphore API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html)
- [Java 17 BlockingQueue API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)
- [Spring Framework: Task Execution and Scheduling, `@Async`](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-004">Chờ điều kiện với giới hạn thời gian thay cho ngủ cố định</a>

<details>
<summary>Xem chi tiết</summary>

# Chờ điều kiện với giới hạn thời gian thay cho ngủ cố định

## 1. Vấn đề trong kiểm thử đồng thời và bất đồng bộ

Khi kiểm thử một tác vụ chạy trên Spring TaskExecutor, phương thức gắn @Async, một callback, hoặc thao tác được xử lý bởi worker khác, hành động kích hoạt có thể trả về **trước khi kết quả xuất hiện**. Kiểm thử cần chờ đúng sự kiện mà nó quan tâm: bản ghi đã được xử lý, trạng thái chuyển sang READY, Future đã hoàn tất, hoặc một tín hiệu đã phát ra.

Cách viết dễ gặp nhưng thiếu tin cậy:

~~~java
service.startProcessing("job-42");
Thread.sleep(1000);                  // Đoán rằng 1 giây là đủ.
assertEquals("DONE", store.status("job-42"));
~~~

Đây chỉ là **đoạn mã minh họa anti-pattern**, không phải chương trình chạy độc lập. Nó có hai lỗi đối xứng:

- Nếu xử lý chỉ mất 20 ms, test vẫn mất thêm gần một giây.
- Nếu máy CI bận, thread pool hết worker, hoặc phụ thuộc ngoài cần 1,5 giây, test thất bại ngẫu nhiên dù hành vi nghiệp vụ vẫn đúng.

Ngoài ra, Thread.sleep không chứng minh tác vụ đã bắt đầu, đã hoàn tất hay kết quả đã được công bố an toàn giữa các thread. Nó chỉ làm thread gọi tạm dừng trong một khoảng thời gian được yêu cầu. Tăng thời gian ngủ từ 1 giây lên 5 giây thường chỉ làm bộ kiểm thử chậm hơn mà không sửa được lỗi đồng bộ hóa.

**Nguyên tắc:** kích hoạt thao tác **một lần**, rồi chờ **điều kiện quan sát được** với một **hạn chót hữu hạn**. Nếu điều kiện sớm đúng, test tiếp tục ngay; nếu hết hạn, test thất bại với thông báo hữu ích.

## 2. Ba thành phần của một phép chờ đúng

Một phép chờ điều kiện gồm:

1. **Điều kiện (condition):** biểu thức có thể kiểm tra, ví dụ trạng thái là DONE hoặc latch đã được mở. Điều kiện phải phản ánh hành vi cần kiểm thử.
2. **Giới hạn thời gian (timeout):** thời lượng tối đa được phép chờ, ví dụ 3 giây. Timeout là hàng rào để test không treo vô hạn, không phải thời gian cần ngủ đủ.
3. **Cách nhận biết thay đổi:** nhận tín hiệu từ cơ chế đồng bộ hóa (CountDownLatch/Future) hoặc kiểm tra lại định kỳ (polling như Awaitility).

Minh họa:

~~~text
Test thread                 Worker thread
    |                            |
    | -- submit task ----------> |
    |                            | -- processing ...
    | -- check condition: false  |
    | -- wait for signal / poll  |
    |                            | -- state = DONE
    | <-- condition becomes true |
    | -- assert and continue     |
    |
    +-- nếu hết timeout khi chưa đạt điều kiện -> FAIL
~~~

**Timeout không bảo đảm thao tác được hủy.** Nó giới hạn thời gian bên test chờ; công việc nền có thể vẫn tiếp tục. Nếu test sở hữu executor, future hoặc tài nguyên của tác vụ, phải có chiến lược hủy và dọn dẹp sau khi thất bại.

Với polling, khoảng kiểm tra (poll interval) là nhịp *quan sát*, không phải một lần ngủ cố định trước assertion. Polling 25 ms với atMost 3 giây nghĩa là test có nhiều cơ hội kiểm tra trước hạn chót, và kết thúc ở lần kiểm tra đầu tiên thỏa điều kiện. Điều kiện xuất hiện giữa hai lần poll có thể được nhận biết trễ đến lần kiểm tra tiếp theo.

## 3. Chọn cơ chế chờ theo loại tín hiệu

| Bạn có thể quan sát gì? | Cách chờ phù hợp | Điều chứng minh được |
| --- | --- | --- |
| Một trạng thái cuối trong repository, cache, projection hoặc bean | Awaitility: until / untilAsserted | Điều kiện cuối cùng đã trở thành đúng trước hạn chót |
| Một worker đã đến một mốc do test kiểm soát | CountDownLatch.await(timeout, unit) | Tín hiệu mốc đó đã được phát |
| Một tác vụ trả kết quả hoặc lỗi bằng Future | Future.get(timeout, unit) | Future hoàn tất và kết quả/lỗi được trả về trước hạn chót |
| Một CompletableFuture có sẵn | CompletableFuture.get(timeout, unit) hoặc kiểm tra completion stage | Stage tương ứng đã hoàn tất |
| Nhiều thread cần gặp nhau tại một pha | CountDownLatch, CyclicBarrier hoặc Phaser với timeout | Các bên đã đến pha đồng bộ được định nghĩa |
| Mục tiêu thực sự là kiểm thử thời gian, retry delay hay backoff | Clock/Scheduler giả lập hoặc virtual time khi framework hỗ trợ | Hành vi theo thời gian đã được điều khiển, không phụ thuộc tốc độ máy thật |

Không có một công cụ tối ưu cho mọi trường hợp. Nếu đã có **tín hiệu sự kiện**, đợi tín hiệu thường hiệu quả và rõ nghĩa hơn polling. Nếu chỉ có **trạng thái nghiệp vụ**, polling với timeout phù hợp. Nếu chỉ kiểm tra return value của Future, dùng get(timeout) sẽ vừa đợi vừa nhận lỗi từ tác vụ.

Chẳng hạn, việc Future hoàn tất không tự động chứng minh dữ liệu đã hiện trong một search index *khác* đang cập nhật eventually consistent. Khi đó vẫn phải chờ đúng điều kiện ở search index.

## 4. Awaitility: chờ assertion đạt yêu cầu

Awaitility là thư viện DSL cho kiểm thử bất đồng bộ. Thay đoạn sleep cố định bằng:

~~~java
service.startProcessing("job-42");

await().alias("job-42 reaches DONE")
       .atMost(Duration.ofSeconds(3))
       .pollInterval(Duration.ofMillis(50))
       .untilAsserted(() ->
           assertEquals("DONE", store.status("job-42")));
~~~

Đây là **mẫu trích đoạn** cần các biến service/store, JUnit Assertions, java.time.Duration và static import org.awaitility.Awaitility.await trong một test thực tế.

- **untilAsserted** thực hiện lại assertion cho đến khi assertion thành công hoặc hết timeout; có lợi khi cần kiểm tra nhiều điều kiện cùng lúc.
- **until** nhận một điều kiện Boolean, ví dụ until(() -> repository.contains(id)).
- **alias** đặt tên cho phép chờ để khi thất bại biết chính xác sự kiện nào không xảy ra.
- **atMost** ghi rõ ngân sách thời gian của thao tác chờ. Nếu không thỏa mãn, Awaitility ném ConditionTimeoutException.
- **pollInterval** phải cân bằng giữa phản hồi nhanh và tải lên hệ thống. Với trạng thái trong RAM có thể chọn vài chục mili giây; với DB/HTTP, nhịp dày đặc có thể làm test và dependency quá tải.
- Có thể đặt **pollDelay(Duration.ZERO)** nếu muốn kiểm tra ngay thay vì chờ lần poll đầu tiên. Với polling interval cố định, nếu không chỉ định pollDelay, Awaitility thường dùng poll delay tương ứng interval.

Ví dụ kiểm tra hai điều kiện liên quan đến cùng job:

~~~java
await().atMost(Duration.ofSeconds(3))
       .untilAsserted(() -> {
           assertEquals("DONE", store.status(jobId));
           assertEquals(1, store.completedEvents(jobId));
       });
~~~

Đặt **hành động kích hoạt ngoài lambda chờ**. Nếu đặt service.startProcessing(jobId) bên trong untilAsserted, mỗi lần poll có thể gửi thêm một tác vụ, tạo nhiều side effect và che khuất lỗi thật.

Cũng không nên biến ConditionTimeoutException thành một trạng thái “thành công” bằng cách catch rồi bỏ qua. Mục đích timeout chính là làm test thất bại khi điều kiện không được thỏa mãn.

## 5. Ví dụ kiểm thử Spring @Async đầy đủ với Awaitility

Tình huống: bean Spring gửi công việc lập chỉ mục sang một ThreadPoolTaskExecutor. Test kiểm tra trạng thái **đã được lập chỉ mục** mà không cần đoán thời gian thread nền sẽ chạy.

Trong dự án có Spring Context, Spring Test và JUnit Jupiter, bảo đảm có thư viện Awaitility trong test classpath. Nếu chưa được bộ dependency quản lý cung cấp, ví dụ Gradle Groovy DSL:

~~~groovy
dependencies {
    testImplementation 'org.awaitility:awaitility:4.3.1'
}
~~~

Phiên bản chỉ là ví dụ dependency cụ thể; nên đồng bộ với version catalog/BOM đang sử dụng. Spring Boot starter-test ở một số phiên bản đã có Awaitility nên không nhất thiết phải khai báo thêm.

Tệp **AsyncConditionTest.java** trong source set test của dự án Spring:

~~~java
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AsyncConditionTest.TestConfig.class)
class AsyncConditionTest {

    @Autowired
    private IndexService service;

    @Test
    void eventuallyIndexesSubmittedRecord() {
        String recordId = "record-42";

        service.index(recordId); // Gửi đúng một lần.

        await().alias("index record-42")
                .atMost(Duration.ofSeconds(3))
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(25))
                .untilAsserted(() ->
                        assertTrue(service.isIndexed(recordId),
                                "Record has not been indexed"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class TestConfig {

        @Bean
        IndexService indexService() {
            return new IndexService();
        }

        @Bean("testExecutor")
        ThreadPoolTaskExecutor testExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("index-test-");
            return executor;
        }
    }

    public static class IndexService {
        private final Set<String> indexed =
                ConcurrentHashMap.newKeySet();

        @Async("testExecutor")
        public void index(String recordId) {
            indexed.add(recordId);
        }

        public boolean isIndexed(String recordId) {
            return indexed.contains(recordId);
        }
    }
}
~~~

**Giải thích:**

1. @EnableAsync kích hoạt cơ chế xử lý @Async của Spring. Tên "testExecutor" bảo đảm lời gọi index() sử dụng executor trong cấu hình thử nghiệm.
2. Test gọi index() từ một đối tượng Spring được inject, tức lời gọi đi qua proxy. Gọi một phương thức @Async từ chính một phương thức khác trên cùng instance (self-invocation) thường sẽ bỏ qua proxy và không tạo bất đồng bộ như mong đợi.
3. ConcurrentHashMap.newKeySet() bảo đảm việc công bố và đọc trạng thái giữa worker và test thread là an toàn; không dùng một HashSet thường được truy cập đồng thời mà thiếu đồng bộ hóa.
4. Awaitility quan sát điều kiện isIndexed(recordId) cho đến khi thấy true hoặc hết 3 giây. Vì không có Thread.sleep cố định, test hoàn tất ngay sau lần quan sát thành công.
5. Spring quản lý vòng đời bean executor của context. Trong ứng dụng thật, cũng cần xử lý ngoại lệ, shutdown và chính sách rejection cho tác vụ.

**Ranh giới của test:** ví dụ này chứng minh *trạng thái cuối được quan sát trong thời hạn*, chứ không tự chứng minh rằng index() đã chạy trên thread khác. Nếu vô tình bỏ @EnableAsync và lời gọi trở thành đồng bộ, assertion trạng thái vẫn có thể pass. Muốn kiểm thử riêng khả năng thực thi bất đồng bộ hoặc thứ tự các interleaving, hãy dùng latch/barrier điều khiển các mốc và/hoặc ghi nhận thread thực thi.

Khi test chạy qua Gradle, sử dụng lệnh test phù hợp với module và cấu hình dự án; đây là test tích hợp với Spring, không biên dịch bằng lệnh javac thuần nếu thiếu dependency classpath.

## 6. Java 17 không cần thư viện: CountDownLatch với timeout

Khi test **chủ động kiểm soát** thời điểm một worker bắt đầu và được phép hoàn tất, CountDownLatch giúp đồng bộ sự kiện mà không cần sleep. Một latch mang tín hiệu “đã bắt đầu”, latch thứ hai đóng vai trò “cổng cho phép tiếp tục”, latch thứ ba mang tín hiệu “đã kết thúc”.

Tệp **BoundedSignalDemo.java** (Java 17+, độc lập):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BoundedSignalDemo {

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                allowFinish.await();
                completed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        }, "sample-worker");

        worker.start();

        try {
            require(started.await(3, TimeUnit.SECONDS),
                    "Worker did not start before timeout");

            require(!completed.get(),
                    "Worker completed before the gate opened");

            allowFinish.countDown();

            require(finished.await(3, TimeUnit.SECONDS),
                    "Worker did not finish before timeout");

            require(completed.get(), "Missing completed state");
            System.out.println("PASS: condition satisfied");
        } finally {
            allowFinish.countDown();
            worker.interrupt();
            worker.join(3_000L);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BoundedSignalDemo.java
java BoundedSignalDemo
~~~

**stdout khi các tín hiệu đến trước timeout:**

~~~text
PASS: condition satisfied
~~~

Nếu máy không cấp CPU cho worker trong thời hạn hoặc xảy ra lỗi, chương trình sẽ thất bại bằng AssertionError thay vì in PASS. Đó là hành vi có chủ đích: **không được coi timeout là success**.

Điểm đáng chú ý:

- await(3, TimeUnit.SECONDS) trả về boolean: true nếu latch chạm 0 trước timeout, false nếu hết hạn. Vì thế phải kiểm tra kết quả; chỉ gọi await rồi bỏ giá trị trả về sẽ tạo test sai.
- countDown() là tín hiệu được phát bởi worker tại thời điểm cụ thể; điều này mạnh hơn việc test đoán rằng “sau 200 ms chắc worker đã đến đó”.
- started và allowFinish thiết lập thứ tự rõ ràng: trước khi mở cổng, worker không thể đánh dấu completed.
- finished được phát trong finally để test không kẹt chờ nếu worker bị ngắt. Tuy nhiên **finished không đồng nghĩa với thành công**; test còn kiểm tra completed.
- AtomicBoolean bảo đảm việc đọc/ghi completed an toàn giữa các thread. CountDownLatch cũng thiết lập quan hệ happens-before cho hành động trước countDown() và phần sau await() thành công.
- Khối finally giải phóng cổng, gửi interrupt và join có giới hạn để không để lại worker đang chờ vô hạn nếu assertion thất bại.

CountDownLatch chỉ sử dụng một lần; nếu test có nhiều vòng hoặc pha, hãy tạo latch mới cho mỗi vòng hoặc dùng CyclicBarrier/Phaser thích hợp. Không dùng await() không timeout trong test có khả năng deadlock.

## 7. Khi đã có Future hoặc CompletableFuture

Một phép gọi trả Future thường đã có tín hiệu hoàn tất. Sử dụng get(timeout) thay vì sleep rồi xem isDone():

~~~java
// Trích đoạn bên trong JUnit test.
Future<String> future = executor.submit(() -> "READY");

assertEquals("READY", future.get(2, TimeUnit.SECONDS));
~~~

Với CompletableFuture:

~~~java
CompletableFuture<String> result = service.processAsync(jobId);

assertEquals("READY", result.get(2, TimeUnit.SECONDS));
~~~

Hai đoạn trên là **trích đoạn**, cần các biến, import và executor/service của dự án. get(timeout) kết thúc khi Future hoàn thành hoặc ném:

- TimeoutException: chưa hoàn tất trước hạn chót.
- ExecutionException: công việc đã thất bại; nguyên nhân thật nằm trong getCause().
- InterruptedException: thread đang chờ bị ngắt; khi xử lý ngoài test framework, cần cân nhắc phục hồi interrupt status và kết thúc đúng cách.

Việc Future bị timeout **không mặc nhiên hủy** công việc. Có thể gọi cancel(true) nếu test sở hữu future và tác vụ hỗ trợ interrupt, nhưng không bảo đảm dừng được mọi thao tác I/O hay mã không hợp tác.

Với @Async trả CompletableFuture<T>, test nhận được lỗi từ future nếu tác vụ hoàn tất thất bại. Ngược lại, ngoại lệ trong phương thức @Async trả void không được ném trực tiếp trên test thread; cần giám sát AsyncUncaughtExceptionHandler, trạng thái kết quả hoặc thiết kế API trả Future khi cần kiểm chứng outcome. Một test chỉ đợi trạng thái mà không quan sát lỗi nền có thể cho thông báo timeout khó hiểu.

Cần phân biệt **Future đã hoàn tất** với **hệ thống nghiệp vụ đã hội tụ**: phương thức có thể kết thúc ngay sau khi gửi một message, trong khi consumer của message còn đang chạy. Khi đó phải chờ trạng thái cuối của consumer/repository thay vì chỉ chờ future của bước gửi.

## 8. Viết điều kiện tốt và đặt timeout hợp lý

### 8.1. Kiểm tra kết quả thật, tránh các tín hiệu yếu

Điều kiện “queue rỗng” chưa chứng minh mọi task đã thực thi thành công: task có thể đang chạy hoặc đã bị reject. Điều kiện “thread vẫn sống” cũng không bảo đảm nghiệp vụ hoàn tất.

Nên dùng tín hiệu rõ nghĩa như:

- trạng thái của đúng jobId là DONE;
- đúng một event hoàn tất đã được lưu;
- một CompletableFuture chứa giá trị hợp lệ hoặc lỗi mong đợi;
- CountDownLatch được hạ tại đúng mốc mà test muốn kiểm chứng.

Cần bảo đảm điều kiện kiểm tra đúng lần chạy hiện tại. Dùng ID riêng và trạng thái được reset/cô lập theo test; nếu đọc dữ liệu còn lại từ một lần chạy trước, Awaitility có thể pass tức thì dù tác vụ mới chưa làm gì.

### 8.2. Điều kiện polling cần an toàn và ít tác dụng phụ

Nếu trạng thái được chia sẻ qua thread, sử dụng volatile, AtomicInteger/AtomicBoolean, concurrent collection, lock hoặc cơ chế lưu trữ có hợp đồng nhất quán phù hợp. Một trường boolean thường được worker ghi mà test thread đọc không có đồng bộ hóa có thể dẫn đến vấn đề visibility, khiến polling không nhìn thấy thay đổi đúng lúc.

Lambda kiểm tra điều kiện nên **chỉ quan sát**. Tránh:

- gửi lại request hoặc tạo thêm task mỗi lần poll;
- làm thay đổi trạng thái cần kiểm tra;
- thực hiện truy vấn rất nặng hàng chục lần mỗi giây;
- giữ lock lâu làm worker không thể hoàn thành;
- chờ lồng nhau vô hạn trong một callback polling.

Một assertion có nhiều thao tác đọc cũng cần xem xét snapshot: nếu dữ liệu thay đổi giữa các lần đọc, test có thể quan sát trạng thái không cùng thời điểm. Với yêu cầu tính nhất quán cao, nên cung cấp một API đọc snapshot nhất quán.

### 8.3. Timeout phải có lý do

Chọn timeout dựa trên môi trường và SLO của test, không suy ra từ kết quả của một lần chạy trên máy cá nhân. Khoảng thời gian quá ngắn gây flakiness khi CI chịu tải; khoảng thời gian quá dài làm phát hiện lỗi chậm và giấu deadlock.

Ví dụ, test tác vụ trong-memory có thể dùng atMost 2–3 giây để chấp nhận biến thiên lập lịch; kiểm thử consumer đi qua broker hoặc DB cần timeout được cân nhắc theo môi trường và dependency. Con số không mang tính mặc định cho mọi hệ thống.

Đặt **một timeout bên trong phép chờ** và có thể dùng thêm **timeout tổng cho cả test** (chẳng hạn JUnit @Timeout) để bắt trường hợp kẹt trước hoặc sau câu lệnh chờ. Hai giới hạn phục vụ hai mục đích khác nhau. Không lấy atMost 3 giây làm phép đo performance “tác vụ phải chạy trong chính xác 3 giây”: Awaitility có độ trễ kiểm tra theo polling và scheduling; muốn benchmark latency, dùng công cụ đo hiệu năng riêng.

### 8.4. Khi cần chứng minh điều kiện *giữ đúng* trong một khoảng

Một lần quan sát false không chứng minh sự kiện “sẽ không bao giờ xảy ra”. Với yêu cầu không được có sự kiện trong một cửa sổ xác định, có thể dùng API kiểu during(...) của Awaitility để xác nhận một điều kiện tiếp tục đúng trong một khoảng, hoặc đồng bộ bằng barrier để khống chế đúng mốc phát sinh event.

Đặc biệt, không viết:

~~~java
assertFalse(service.isDone(jobId)); // Có thể pass chỉ vì worker chưa kịp chạy.
~~~

rồi coi đó là bằng chứng một quy trình đã bị chặn. Test cần biết worker đã đến mốc liên quan trước khi đưa ra kết luận; latch/barrier thường phù hợp hơn ngủ 100 ms và đoán.

## 9. Những bẫy riêng khi kiểm thử Spring concurrency

**@Async và proxy.** @Async hoạt động khi lời gọi đi qua Spring-managed proxy trong cấu hình phù hợp. Self-invocation có thể chạy đồng bộ; test chờ trạng thái vẫn pass, khiến ta tưởng đã kiểm thử đường bất đồng bộ. Nếu cần xác nhận thread khác được sử dụng, hãy quan sát một tín hiệu hoặc thread name/thread identity theo cách có kiểm soát.

**@Transactional trong test.** Transaction của Spring Test thường ràng buộc với test thread. Worker @Async chạy thread riêng, không tự kế thừa transaction đó; dữ liệu test chưa commit có thể không hiển thị với worker. Điều này dễ tạo timeout “ngẫu nhiên”. Hãy chuẩn bị fixture theo ranh giới transaction phù hợp rồi mới submit tác vụ; đừng kỳ vọng sleep giải quyết được dữ liệu chưa commit.

**Executor chung với test.** Nếu test chạy phép polling hoặc chờ một mốc trên đúng executor một worker đang bị một task khác chiếm, có thể tạo starvation/deadlock. Awaitility thường poll điều kiện từ thread kiểm tra riêng; đừng tự submit thêm tác vụ blocking vào executor đang cần giải phóng.

**Race giữa đăng ký listener và phát event.** Nếu đăng ký listener *sau* khi kích hoạt công việc, event có thể phát và mất trước khi test bắt đầu chờ. Chuẩn bị latch/listener trước khi kích hoạt, hoặc kiểm tra một trạng thái bền vững có thể đọc lại.

**Lỗi bị ẩn trong tác vụ void.** Nếu worker ném exception trước khi thay đổi trạng thái, Awaitility chỉ thấy điều kiện mãi false và cuối cùng timeout. Hãy thu thập kết quả/lỗi của tác vụ, cấu hình handler hoặc ghi tín hiệu lỗi rõ ràng. Không cài ignoreExceptions() toàn cục tùy tiện, vì có thể che lỗi lập trình thật.

**Test dữ liệu chia sẻ.** Cache, singleton bean, database và hàng đợi có thể giữ trạng thái của test trước; khi chạy song song, hai test cùng dùng "job-42" sẽ ảnh hưởng nhau. Tạo ID riêng, làm sạch hoặc cô lập tài nguyên, và dọn những task còn sống sau timeout.

**assertTimeoutPreemptively.** JUnit có thể chạy phần kiểm thử trong thread khác để ép timeout; việc đó có thể thay đổi ngữ cảnh ThreadLocal và transaction mà Spring Test thiết lập. Đối với kiểm thử gắn @Transactional, cần đặc biệt thận trọng; timeout hữu hạn ngay tại latch/Future/Awaitility thường diễn đạt mục đích cụ thể và tránh đổi test thread ngoài ý muốn.

**Lạm dụng Thread.sleep để “test concurrency”.** Sleep có thể vẫn xuất hiện trong code nghiệp vụ mô phỏng độ trễ hoặc test một hành vi thời gian thật, nhưng nó không phải cơ chế đồng bộ hóa đáng tin cậy. Nếu đang kiểm thử retry delay, scheduled timeout hay backoff, nên kiểm soát Clock/Scheduler hoặc virtual time khi framework hỗ trợ; không kéo test bằng đồng hồ thật nếu có thể điều khiển thời gian.

## 10. Quy trình chuyển một test từ sleep sang condition wait

Giả sử có test cũ:

~~~java
jobService.submit(jobId);
Thread.sleep(2000);
assertEquals(Status.DONE, jobRepository.findStatus(jobId));
~~~

Đây là trích đoạn phụ thuộc vào API của ứng dụng.

Thay đổi theo thứ tự:

1. **Xác định điều kiện hoàn tất đúng:** Status.DONE của chính jobId, và cân nhắc cả trạng thái FAILED nếu hệ thống có trạng thái lỗi.
2. **Chọn cách chờ:** nếu có Future trả từ submit, dùng get(timeout) để nhận kết quả/lỗi; nếu cần kiểm tra trạng thái được cập nhật sau đó, dùng Awaitility; nếu test kiểm soát event tại worker, dùng latch.
3. **Đặt timeout hữu hạn:** chọn theo môi trường test; đưa việc báo lỗi và tình trạng cuối vào thông báo assertion.
4. **Kích hoạt đúng một lần:** không gửi thêm job từ trong lambda của Awaitility.
5. **Dọn dẹp:** hủy hoặc giải phóng nguồn lực khi test thất bại; không để worker, latch, message hoặc transaction ảnh hưởng test kế tiếp.

Ví dụ với Awaitility:

~~~java
jobService.submit(jobId);

await().alias("job " + jobId + " completed")
       .atMost(Duration.ofSeconds(3))
       .pollInterval(Duration.ofMillis(50))
       .untilAsserted(() ->
           assertEquals(Status.DONE,
                   jobRepository.findStatus(jobId)));
~~~

**Khác biệt cốt lõi:** test cũ *ngủ 2 giây rồi hy vọng*; test mới *chờ đến khi đúng, nhưng không quá 3 giây*. Thời gian chờ thực tế phụ thuộc lúc điều kiện trở thành đúng và lúc test quan sát được điều đó, không phụ thuộc vào một thời lượng sleep cố định.

Nếu có khả năng job chuyển sang FAILED một cách không thể phục hồi, nên cân nhắc đưa nhánh lỗi vào điều kiện hoặc dùng cơ chế fail-fast/quan sát outcome phù hợp để test thất bại sớm với nguyên nhân chính xác, thay vì chờ hết 3 giây mà không có thông tin.

## 11. Câu hỏi tự kiểm tra

**Tình huống A:** một thao tác thường hoàn tất trong 30 ms nhưng đôi khi cần 600 ms khi CI bận. Vì sao sleep 200 ms không phù hợp? Vì nó vừa lãng phí thời gian ở phần lớn lần chạy, vừa khiến các lần trên 200 ms thất bại. Thay bằng một điều kiện quan sát được với timeout có dư địa hợp lý.

**Tình huống B:** test gọi future.get(2, SECONDS) thành công, nhưng truy vấn search index ngay sau đó vẫn không thấy dữ liệu. Có mâu thuẫn không? Không nhất thiết. Future có thể chỉ đánh dấu bước gửi/đăng ký công việc đã xong; search index có thể cập nhật sau. Hãy chờ trạng thái ở thành phần mà test thực sự muốn xác nhận.

**Tình huống C:** test dùng latch nhưng gọi await(2, SECONDS) mà không kiểm tra boolean trả về. Điều gì xảy ra? Nếu timeout, chương trình có thể tiếp tục như thể sự kiện đã đến. Luôn assert true hoặc chuyển false thành lỗi.

**Tình huống D:** test chờ một boolean thường được worker ghi mà không có đồng bộ hóa. Thêm pollInterval 1 ms có chắc giải quyết không? Không. Vấn đề có thể là visibility hoặc data race; cần dùng cơ chế đồng bộ hóa và công bố trạng thái phù hợp.

**Tình huống E:** một test với @Async trả void không cập nhật trạng thái và Awaitility timeout. Trước khi tăng timeout cần kiểm tra gì? Xem @EnableAsync/proxy, executor có chạy không, task có bị reject không, exception trên worker, transaction visibility và điều kiện có đang đọc đúng ID hay không.

## 12. Checklist áp dụng

- [ ] Chờ **điều kiện nghiệp vụ hoặc tín hiệu xác định**, không suy luận từ thời gian ngủ.
- [ ] Mọi phép chờ có **timeout hữu hạn** và kiểm tra kết quả timeout.
- [ ] Điều kiện polling **thread-safe, ít side effect**, gắn với đúng lần chạy test.
- [ ] Async task được kích hoạt **một lần bên ngoài** phần kiểm tra điều kiện.
- [ ] Test phân biệt **hoàn tất thành công, hoàn tất lỗi, timeout và cancellation**.
- [ ] Với Spring, xác nhận **@EnableAsync, proxy, executor và transaction boundary**.
- [ ] Khi failure, test **giải phóng latch, xử lý future/executor và dữ liệu dùng chung**.
- [ ] Timeout và poll interval được chọn phù hợp môi trường CI; không dùng chúng làm benchmark latency.

**Ghi nhớ:** trong kiểm thử đồng thời, hãy *đợi điều kiện đúng với hạn chót rõ ràng*. CountDownLatch/Future phù hợp với tín hiệu sẵn có; Awaitility phù hợp với trạng thái xuất hiện về sau. Một phép sleep cố định chỉ trì hoãn assertion, không tạo ra bằng chứng rằng hệ thống đã sẵn sàng.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): cách @Async hoạt động, executor và proxy.
- [Spring Framework — Spring JUnit Jupiter Testing Annotations](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-junit-jupiter.html): @SpringJUnitConfig.
- [Awaitility — Usage Guide](https://github.com/awaitility/awaitility/wiki/Usage): until, untilAsserted, atMost, pollDelay, pollInterval, during, exceptions.
- [Java 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): await(timeout), countDown() và memory-consistency guarantees.
- [Java 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): get(timeout) và cancellation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-005">Kiểm tra lời gọi thật sự đi qua proxy @Async</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra lời gọi thật sự đi qua proxy `@Async`

## 1. Mục tiêu kiểm thử: chứng minh có **lời gọi qua proxy**, không chỉ có annotation

Trong Spring Framework, gắn `@Async` lên một phương thức không tự bảo đảm phương thức ấy chạy ở luồng khác. Với cấu hình thông dụng `@EnableAsync` ở chế độ `AdviceMode.PROXY`, Spring tạo một **proxy** bao quanh Spring bean. Chỉ lời gọi đến phương thức phù hợp **qua proxy** mới được advice của `@Async` chuyển sang `Executor`.

Một kiểm thử đáng tin cần chứng minh những điều khác nhau:

1. Đối tượng mà caller nhận được là một **Spring-managed proxy** (`AopUtils.isAopProxy(bean)`).
2. Caller **gọi chính đối tượng proxy đó**, không dùng `new`, không gọi trực tiếp đối tượng target và không gọi qua `this` bên trong cùng instance.
3. Lời gọi thực sự được **chuyển tới executor**: thân phương thức chạy trên một `Thread` khác với thread gọi, và nếu kiểm soát executor thì có thể xác nhận đúng thread của pool dự kiến.
4. Hợp đồng bất đồng bộ đúng: caller nhận một `CompletableFuture` khi tác vụ vẫn đang chạy; dữ liệu/kết quả chỉ sẵn sàng sau khi tác vụ hoàn tất.
5. Một ví dụ **self-invocation** được kiểm chứng để phát hiện bẫy “bean có proxy nhưng riêng lời gọi này không qua proxy”.

**Không thể kết luận “`@Async` hoạt động” chỉ từ:** có annotation trong source, bean có proxy, log có tên thread lạ, một test chạy nhanh, `CompletableFuture.completedFuture(...)` hoặc Mockito xác nhận method được gọi.

Bài này tập trung vào cách **kiểm thử** cơ chế proxy `@Async` trong Spring; phần ví dụ dùng Java 17+, Spring Framework 6.x và JUnit Jupiter 5. Với Spring Boot 3.x, các API và nguyên tắc tương ứng được dùng trực tiếp.

## 2. Hiểu đường đi của lời gọi

Đường đi thành công khi caller thuộc bean khác hoặc mã kiểm thử nhận bean từ `ApplicationContext`:

```text
JUnit test / Controller / Service A
          |
          | gọi dependency được Spring inject
          v
    Spring AOP proxy
          |
          | @Async advice submit Runnable/Callable
          v
    TaskExecutor (worker thread)
          |
          v
     Service B thực thi method
          |
          v
  Future/CompletableFuture hoàn tất
```

Nếu một method cùng class gọi method `@Async` bằng `this.doAsync()` hoặc gọi ngầm `doAsync()`:

```text
Caller -> proxy -> target.outer()
                      |
                      +-> this.doAsync()  (gọi trực tiếp target)
                             |
                             +-> chạy đồng bộ trên caller thread
```

Lời gọi nội bộ không quay lại proxy nên `AsyncAnnotationBeanPostProcessor`/async interceptor không có cơ hội can thiệp. Điều này đúng cả khi bean thực sự là proxy. Với `@EnableAsync(mode = AdviceMode.PROXY)`, đây là đặc tính của thiết kế Spring AOP, không phải lỗi của JUnit.

### Những điều kiện tối thiểu để proxy được tạo và advice có hiệu lực

- Có cấu hình `@EnableAsync` hoặc hạ tầng tương đương; đừng mặc định rằng chỉ dùng Spring Boot là mọi `@Async` đều được kích hoạt.
- Đối tượng được Spring tạo/quản lý (`@Bean`, `@Service`/`@Component` qua component scan); một instance tạo bằng `new` không có Spring proxy.
- Phương thức cần gọi qua proxy, ưu tiên `public` và **không `final`**. Lời gọi tới `private` method, constructor hay nội bộ cùng instance không được proxy advice xử lý như bạn mong muốn.
- Executor được chọn hợp lệ, qua `@Async("beanName")` hoặc cấu hình executor mặc định. Nếu dùng executor đồng bộ như `SyncTaskExecutor` trong test, proxy vẫn có thể tồn tại nhưng không chuyển công việc sang thread khác.
- Kiểu trả về của method `@Async` phù hợp: `void`, `Future<T>` hoặc `CompletableFuture<T>`; `String`/`int` trực tiếp không phải hợp đồng return hỗ trợ cho phương thức async.

Proxy có thể là JDK dynamic proxy cho interface hoặc proxy dựa trên subclass cho class, tùy cấu hình. Khi JDK proxy được dùng, caller phải truy cập qua interface do proxy phơi bày. Ví dụ kiểm thử dưới đây cấu hình `proxyTargetClass = true` **chỉ để kiểu bean và cơ chế proxy được nhất quán**, không phải yêu cầu mọi ứng dụng production đều phải bật CGLIB.

## 3. Vì sao các cách “test async” phổ biến vẫn cho dương tính giả?

| Kiểm tra | Nó chứng minh gì? | Vì sao chưa đủ? |
| --- | --- | --- |
| Kiểm tra `@Async` bằng reflection | Source có annotation | Không chứng minh bean đi qua interceptor |
| `AopUtils.isAopProxy(service)` | Object được Spring proxy hóa | Proxy có thể đến từ advice khác; self-invocation vẫn bypass |
| `assertNotNull(service.doAsync())` | Có kết quả/handle | Method đồng bộ cũng có thể trả `completedFuture` |
| Mockito `verify(service).doAsync()` | Lời gọi được quan sát trên mock/spy | Mock có thể thay thế proxy, không chứng minh executor chạy |
| So thời gian với `Thread.sleep` | Ước lượng độ nhanh/chậm | Flaky do scheduler, CI, CPU; không khẳng định giao thức |
| Chỉ assert tên thread | Có chuỗi tên như kỳ vọng | Có thể log nhầm caller hoặc đổi cấu hình thread factory |
| Kiểm tra **proxy + thread identity + latch chặn worker** | Bean có proxy, caller không bị chặn, method chạy ở worker | Bằng chứng tích hợp mạnh, ít phụ thuộc tốc độ máy |

Mấu chốt là tạo điều kiện để **worker đã bắt đầu nhưng cố ý chưa thể kết thúc**; lúc đó caller vẫn phải có được future. Dùng `CountDownLatch` thay cho sleep giúp test có quan hệ đồng bộ rõ ràng.

## 4. Kiểm thử tích hợp hoàn chỉnh: có proxy, chạy trên worker, caller nhận future sớm

Ví dụ dưới đây chạy trong **Spring TestContext thực** qua `@SpringJUnitConfig`, không cần khởi động cả HTTP server. Nó dùng một `ThreadPoolTaskExecutor` riêng để xác nhận đúng nguồn thực thi.

Trong dự án Spring Boot đã dùng Gradle/JUnit Platform, đảm bảo có các dependency phù hợp:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
```

Đây là phần dependency minh họa trong build đã có plugin Spring Boot và quản lý phiên bản dependency; không phải một `build.gradle` đầy đủ tự chạy độc lập.

Tạo file **`src/test/java/example/async/AsyncProxyVerificationTest.java`** với toàn bộ mã sau:

```java
package example.async;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AsyncProxyVerificationTest.TestConfig.class)
class AsyncProxyVerificationTest {

    @Autowired
    private ProbeService service;

    @Test
    void externalCallMustGoThroughAsyncProxy() throws Exception {
        // Bằng chứng 1: bean tiêm vào test là Spring proxy.
        assertTrue(AopUtils.isAopProxy(service));

        Thread callerThread = Thread.currentThread();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        // Test gọi bean do Spring inject: đây là external call qua proxy.
        CompletableFuture<Thread> future =
            service.executeAsync(started, releaseWorker);

        try {
            // Bằng chứng 2: thân method đã bắt đầu trên worker.
            assertTrue(started.await(5, TimeUnit.SECONDS),
                "Executor không khởi chạy method");

            // Bằng chứng 3: method vẫn bị chặn nhưng caller đã có future.
            assertFalse(future.isDone(),
                "Future hoàn tất dù worker chưa được cho phép tiếp tục");
        } finally {
            // Luôn mở latch, kể cả khi assertion thất bại.
            releaseWorker.countDown();
        }

        Thread actualExecutionThread = future.get(5, TimeUnit.SECONDS);

        // Bằng chứng 4: thân @Async chạy trên một thread khác caller.
        assertNotSame(callerThread, actualExecutionThread);

        // Bằng chứng bổ sung: đúng executor đã khai báo trong test.
        assertTrue(actualExecutionThread.getName()
            .startsWith("probe-async-"));
    }

    @Test
    void internalCallBypassesAsyncAdviceEvenOnProxiedBean()
            throws Exception {
        assertTrue(AopUtils.isAopProxy(service));

        Thread callerThread = Thread.currentThread();
        CompletableFuture<Thread> future = service.callInternally();

        // Cố ý chứng minh ca âm: callInternally() tự gọi executeAsync()
        // trên target, do đó không submit sang executor.
        assertSame(callerThread, future.get(5, TimeUnit.SECONDS));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync(proxyTargetClass = true)
    static class TestConfig {

        @Bean(name = "probeExecutor")
        ThreadPoolTaskExecutor probeExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("probe-async-");
            return executor;
        }

        @Bean
        ProbeService probeService() {
            return new ProbeService();
        }
    }

    // Non-final class, public async method: phù hợp class-based proxy.
    public static class ProbeService {

        @Async("probeExecutor")
        public CompletableFuture<Thread> executeAsync(
                CountDownLatch started,
                CountDownLatch releaseWorker) {
            started.countDown();

            try {
                // Không dùng Thread.sleep: chỉ kết thúc khi test mở latch.
                if (!releaseWorker.await(10, TimeUnit.SECONDS)) {
                    return CompletableFuture.failedFuture(
                        new IllegalStateException("Worker gate timed out"));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(ex);
            }

            return CompletableFuture.completedFuture(
                Thread.currentThread());
        }

        public CompletableFuture<Thread> callInternally() {
            // Gọi trực tiếp method cùng instance: bypass proxy @Async.
            return executeAsync(
                new CountDownLatch(0),
                new CountDownLatch(0));
        }
    }
}
```

Chạy từ gốc module Gradle có task `test`:

```bash
./gradlew test --tests "example.async.AsyncProxyVerificationTest"
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat test --tests "example.async.AsyncProxyVerificationTest"
```

**Kết quả mong đợi:** cả hai test thành công. Test thứ nhất xác nhận external call được chuyển sang worker. Test thứ hai cố tình xác nhận self-invocation chạy đồng bộ trên caller. Không có stdout cố định cho ví dụ này; JUnit báo pass/fail bằng assertions và Gradle báo kết quả task test theo môi trường.

### Giải thích chi tiết từng bằng chứng

- `AopUtils.isAopProxy(service)` đảm bảo test dùng bean có proxy. Đây là điều kiện hữu ích nhưng **chưa đủ** để chứng minh lời gọi `executeAsync` đã được advice xử lý.
- `started.countDown()` nằm **trong thân `executeAsync`**, nên `started.await(...)` trả `true` chỉ khi method đã bước vào thực thi.
- `releaseWorker` giữ method ở trạng thái đang chạy. Sau khi `started` mở mà `releaseWorker` vẫn đóng, `future.isDone()` phải là `false`; chứng minh caller đã nhận handle trước khi worker hoàn tất.
- Method trả về `CompletableFuture.completedFuture(Thread.currentThread())` **bên trong worker**. Spring async interceptor chuyển kết quả method đó thành future quan sát được ở phía caller; vì vậy `future.get(...)` cho biết **thread thực thi method**, không chỉ thread đang chạy assertion.
- So sánh bằng `assertNotSame(callerThread, actualExecutionThread)` ổn định hơn việc suy đoán từ tên thread. Kiểm tra prefix `probe-async-` chỉ là bằng chứng thêm rằng đúng pool đã chọn.
- `finally` giải phóng latch ngay cả khi assertion hỏng, tránh để một test thất bại khiến worker tiếp tục bị giữ lại và làm bẩn các test khác.

Với lời gọi đồng bộ trực tiếp (không có advice `@Async`), `service.executeAsync(...)` sẽ chờ trên latch trước khi trả kết quả, hoặc trả future hoàn tất/exceptional; test thứ nhất sẽ thất bại ở điều kiện về proxy, chưa hoàn tất, hoặc thread khác nhau. Dùng timeout ở latch và `future.get` tránh treo vô hạn khi cấu hình lỗi.

### Vì sao test thứ hai rất quan trọng?

`service` là proxy thật nên `AopUtils.isAopProxy(service)` vẫn `true`. Nhưng khi proxy gọi `callInternally()`, code trong target gọi `executeAsync(...)` bằng `this`; call không qua proxy, dù method đích có `@Async`. Kết quả `Thread.currentThread()` lúc này chính là thread của JUnit caller. Test thứ hai chỉ ra rằng **có proxy trên bean ≠ mọi lời gọi tới method có annotation đều đã đi qua proxy**.

Trong code ứng dụng, kết quả test ca âm này là dấu hiệu cần sửa thiết kế caller, không phải lý do để chấp nhận chạy đồng bộ.

## 5. Sửa self-invocation bằng cách tách bean và gọi qua dependency được inject

**Mẫu lỗi** (minh họa, không phải chương trình Java độc lập):

```java
@Service
public class ReportService {

    public void startReport() {
        generateReportAsync(); // this.generateReportAsync() => bypass proxy
    }

    @Async("reportExecutor")
    public CompletableFuture<Void> generateReportAsync() {
        // làm công việc...
        return CompletableFuture.completedFuture(null);
    }
}
```

**Mẫu đúng**: tách method `@Async` sang một bean khác để caller luôn gọi thông qua reference đã được Spring inject:

```java
@Service
public class ReportFacade {
    private final ReportWorker worker;

    public ReportFacade(ReportWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<Void> startReport() {
        return worker.generateReportAsync(); // lời gọi qua Spring bean proxy
    }
}

@Service
public class ReportWorker {

    @Async("reportExecutor")
    public CompletableFuture<Void> generateReportAsync() {
        // chạy tác vụ và trả kết quả (hoặc exceptional future)
        return CompletableFuture.completedFuture(null);
    }
}
```

Hai đoạn ví dụ `ReportService`/`ReportFacade`/`ReportWorker` là minh họa cấu trúc; cần imports, khai báo bean `reportExecutor` và cấu hình `@EnableAsync` trong ứng dụng thực tế. Cách tách bean giúp tránh phụ thuộc vào `AopContext.currentProxy()` và tránh self-injection khó quản lý. Có thể dùng AspectJ weaving để can thiệp cả self-invocation nhưng đó là cơ chế riêng, cần cấu hình và kiểm thử khác; ví dụ của bài này cố ý kiểm tra chế độ **proxy**.

## 6. Viết bài kiểm thử cho luồng nghiệp vụ thật, không dừng ở thread

Một hệ thống thực có thể chạy đúng worker thread nhưng vẫn sai nghiệp vụ. Sau khi test cấu trúc proxy như mục 4, cần thêm **integration test ở ranh giới caller thực tế**:

1. Gọi `ReportFacade.startReport()` (hoặc controller/event listener thực) bằng Spring bean thật.
2. Tạo dữ liệu đầu vào riêng cho test và một tín hiệu xác định worker đã nhận công việc.
3. Dùng barrier/latch hoặc trạng thái DB có điều kiện, thay vì `Thread.sleep(1000)` rồi đoán tác vụ xong.
4. Đợi kết quả bằng `CompletableFuture.get(timeout, unit)`, hoặc đợi tín hiệu có timeout nếu method trả `void`.
5. Kiểm tra side effect đã commit, kết quả đúng và trường hợp thất bại phát ra lỗi theo hợp đồng.

Nếu method trả `CompletableFuture<Result>`, `get()` ném `ExecutionException` khi worker thất bại; `join()` ném `CompletionException`. Test phải assert lỗi thực sự thay vì bỏ qua future:

```java
// Minh họa assertion cho một future thất bại, không phải lớp test độc lập.
ExecutionException failure =
    assertThrows(ExecutionException.class,
        () -> future.get(5, TimeUnit.SECONDS));
assertInstanceOf(IllegalStateException.class, failure.getCause());
```

Với `@Async void`, caller không nhận future để quan sát lỗi; cấu hình `AsyncUncaughtExceptionHandler` hoặc ghi nhận kết quả vào nơi quan sát được rồi kiểm thử. Dùng `CompletableFuture<T>` khi caller phải biết thành công/thất bại và kiểm tra được kết quả thường thuận tiện hơn.

Đối với tác vụ có I/O, luồng khác nhau **không đồng nghĩa** đã commit database: test nên xác minh kết quả lưu trữ sau khi future hoàn tất. Tránh trông đợi transaction của thread kiểm thử tự lan truyền sang worker: transaction và nhiều context được gắn theo thread; việc truyền security context, MDC/trace hay tenant context cần cơ chế riêng nếu nghiệp vụ đòi hỏi.

## 7. Bộ tình huống kiểm thử cần có

| Tình huống | Cần chứng minh | Tín hiệu/kiểm tra hữu ích |
| --- | --- | --- |
| Caller nhận Spring bean thật và gọi method `@Async` | Proxy intercept + chuyển sang executor | `AopUtils.isAopProxy`, thread identity, latch + future chưa done |
| Cùng bean gọi `this.asyncMethod()` | Proxy bị bypass | Thread thực thi trùng caller, dù bean ban đầu là proxy |
| Thiếu `@EnableAsync` | Annotation một mình không kích hoạt cơ chế proxy | Proxy/async evidence không đạt; test tích hợp thất bại |
| Dùng `new AsyncWorker()` trong code caller | Instance nằm ngoài IoC | `AopUtils.isAopProxy` false, thực thi trực tiếp |
| Dùng executor đồng bộ trong test | Có interceptor chưa chắc đã bất đồng bộ | Assert thread khác + caller nhận future khi worker còn block |
| Phương thức `private` hoặc `final` không thể được class proxy advice xử lý theo cách dự tính | Method không được intercept | Test hành vi thật, tránh chỉ soi annotation |
| Chọn sai tên executor trong `@Async("...")` | Không chạy đúng executor yêu cầu | Xác minh executor tồn tại và thread factory/prefix trong môi trường test |
| Worker ném exception | Lỗi được truyền/ghi nhận đúng | `CompletableFuture` exceptional + `assertThrows` |
| Executor đầy, reject task | Caller nhận tín hiệu lỗi/rejection phù hợp | Test giới hạn queue/pool, xử lý `TaskRejectedException` nếu nghiệp vụ cần |
| Caller gọi qua interface (JDK proxy) | Signature và injection tương thích loại proxy | Inject interface, test đúng object lấy từ `ApplicationContext` |

Không nhất thiết phải đưa tất cả các case vào một lớp test. Tối thiểu cần một ca **đường đi thành công**, một ca **self-invocation không qua proxy**, và test nghiệp vụ dùng bean thực. Những case về executor reject, transaction hoặc security context chỉ thêm khi hệ thống thực sự phụ thuộc vào chúng.

## 8. Chẩn đoán khi test thất bại

### 8.1 `AopUtils.isAopProxy(service) == false`

Kiểm tra theo thứ tự: cấu hình `@EnableAsync` có được nạp vào context test không; `ProbeService` có phải bean do Spring quản lý không; có vô tình dùng `new` hoặc object được tạo trước khi Spring xử lý không; method có đủ điều kiện để được advice intercept không. Với cấu hình Spring Boot, chỉ có dependency về task execution chưa thay thế được `@EnableAsync`.

Một Spring unit test tạo class bằng `new` vẫn có thể kiểm thử **logic bên trong** method, nhưng không thể kiểm thử **cơ chế proxy**.

### 8.2 Proxy có nhưng `assertNotSame` thất bại

Kiểm tra caller đi vào **đúng method có `@Async` qua reference proxy** hay đi vào wrapper trong chính class rồi gọi `this.method()`. Xem executor có phải `SyncTaskExecutor` hoặc task chạy inline do cấu hình test không. Đối với JDK proxy, kiểm tra interface mà caller sử dụng và annotation trên method thực tế. Đừng chữa test bằng `Thread.sleep` dài hơn.

### 8.3 `started.await` hết hạn hoặc `future.get` timeout

Có thể executor không nhận được task (saturation/queue), đang bị khóa bởi task khác, job mắc kẹt, hoặc method không được gọi. Dùng executor riêng cho test, thread pool đủ dung lượng, một signal ở đầu method, timeout hữu hạn và log có context. Một test timeout **không tự chứng minh** lý do là proxy bị bypass; đối chiếu nó với các assertion và thông tin từ executor.

### 8.4 Test xanh nhưng hệ thống vẫn chạy đồng bộ

Xem đúng đường gọi ở production chưa: `Controller -> Facade -> Worker` có thể khác với đường gọi trực tiếp trong test. Có thể test dùng một bean async riêng nhưng production vẫn self-invoke. Hãy test chính entrypoint sản phẩm, đặc biệt khi method bị gọi từ event listener, startup hook, một bean khác hoặc scheduler. Không đưa lời gọi `@Async` vào `@PostConstruct` rồi mặc định rằng proxy đã hoàn chỉnh.

### 8.5 Test với Mockito, spy và transaction

Mock đối tượng mang `@Async` có thể xác nhận interaction nhưng đã thay thế hành vi proxy. Nếu mục tiêu là kiểm tra lời gọi thật qua async advice, dùng Spring bean thật; mock các dependency bên trong worker (gateway, repository) khi cần. Với `@Transactional` trong test, chú ý dữ liệu chưa commit ở caller có thể không nhìn thấy từ worker thread; kiểm thử visibility và commit theo thiết kế thật.

## 9. Phân biệt bằng chứng kỹ thuật và kết luận nghiệp vụ

Một kiểm thử proxy tốt có ba tầng:

| Tầng | Câu hỏi trả lời | Bằng chứng |
| --- | --- | --- |
| Cấu trúc | Caller đang có Spring bean proxy? | Bean lấy từ context, `AopUtils.isAopProxy` |
| Runtime | Method đi qua `@Async` advice và được thực thi ở executor thread? | Caller nhận future khi worker còn chặn; actual thread khác caller |
| Nghiệp vụ | Kết quả, lỗi và dữ liệu được xử lý đúng trong luồng bất đồng bộ? | Await completion, assert side effect, exception và trạng thái cuối |

Một test ở tầng cấu trúc không thay cho tầng runtime; một test ở tầng runtime không thay cho việc kiểm tra kết quả nghiệp vụ. Việc gộp ba tầng theo một trục bằng chứng làm test rõ mục đích và dễ chẩn đoán hơn.

Có thể thêm `AopUtils.isJdkDynamicProxy(bean)` hoặc `AopUtils.isCglibProxy(bean)` để tìm hiểu dạng proxy, nhưng đây không phải điều kiện business requirement. Đặc biệt, **không phụ thuộc vào tên class chứa `$$SpringCGLIB$$`** vì đó là chi tiết triển khai dễ thay đổi theo phiên bản.

## 10. Lỗi phổ biến và cách sửa ngắn gọn

1. **Chỉ kiểm tra `@Async` có tồn tại:** thay bằng test tích hợp gọi Spring bean thật và quan sát thread thực thi.
2. **Chỉ kiểm tra bean là proxy:** thêm test qua entrypoint được dùng trong production; self-invocation vẫn có thể chạy đồng bộ.
3. **Tự gọi `this.asyncMethod()`:** tách async operation sang bean khác và inject vào caller.
4. **Dùng `new`, mock hoặc constructor tự cấp đối tượng cho test proxy:** để Spring TestContext tạo và inject bean thật.
5. **Dùng sleep để “đợi đủ lâu”:** dùng `CountDownLatch` và `future.get(timeout, unit)`, luôn mở latch trong `finally`.
6. **Chỉ so thread name:** so `Thread` identity; dùng prefix executor như bằng chứng phụ.
7. **Quên cấu hình `@EnableAsync` hoặc chọn nhầm executor:** test startup context + assert behavior; khai báo `@Async("probeExecutor")` nhất quán với tên bean.
8. **Tin rằng future trả về đồng nghĩa code bất đồng bộ:** `completedFuture` trả từ lời gọi trực tiếp vẫn chạy đồng bộ; kiểm tra trạng thái chưa hoàn thành khi worker đang giữ latch.
9. **Bỏ qua lỗi bên trong `void @Async`:** thêm `AsyncUncaughtExceptionHandler` hoặc trả `CompletableFuture` và assert exceptional completion.
10. **Test không đi đúng ranh giới caller:** gọi entrypoint thật (controller/facade/event consumer) qua Spring bean để phát hiện việc một wrapper bất ngờ bypass proxy.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Bean được inject báo `AopUtils.isAopProxy(...) == true`. Có thể kết luận mọi lời gọi `@Async` đều chạy ở thread khác chưa?

**Trả lời:** Chưa. Lời gọi nội bộ trong cùng target qua `this` vẫn bypass proxy; mặt khác proxy đó có thể chứa advice không liên quan. Cần kiểm tra đường gọi thật và thread thực thi.

**Câu 2.** Tại sao test `CompletableFuture` không nên chỉ xác nhận `result != null`?

**Trả lời:** Method đồng bộ có thể trả ngay một `completedFuture`. Cần dựng tín hiệu cho thấy worker vẫn đang chạy trong lúc caller đã nhận future.

**Câu 3.** Tại sao `CountDownLatch` được ưu tiên so với `Thread.sleep(200)`?

**Trả lời:** Latch diễn đạt chính xác “method đã bắt đầu”, “cho phép kết thúc” mà không phụ thuộc vào tốc độ CPU/CI; timeout chỉ để ngăn test treo.

**Câu 4.** Nếu test chạy async đúng nhưng dữ liệu DB chưa đúng, nên điều tra gì?

**Trả lời:** Đợi future hoàn tất, kiểm tra exception, transaction boundaries/commit, dữ liệu worker nhìn thấy, side effect/idempotency; việc đổi thread mới chỉ chứng minh cơ chế thực thi.

## 12. Kết luận

Để chứng minh **lời gọi thật sự đi qua proxy `@Async`**, hãy lấy bean từ Spring context, kiểm tra proxy, gọi method từ **bên ngoài bean**, dùng latch giữ worker chưa hoàn tất, rồi assert caller có future trước khi worker được mở và kết quả được tạo trên **thread khác caller**. Thêm một ca self-invocation cho thấy cùng bean vẫn có thể chạy đồng bộ khi bypass proxy. Sau đó kiểm thử entrypoint nghiệp vụ thực để chắc chắn production dùng đúng đường gọi đã được xác minh.

### Tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling, @Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — @EnableAsync (Spring 6.1 API)](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework — Testing với JUnit Jupiter](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-junit-jupiter.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-006">Kiểm thử nhận kết quả và cả đường lỗi của phương thức void hoặc Future</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử nhận kết quả và đường lỗi của phương thức `void` hoặc `Future` với Spring `@Async`

## 1. Mục tiêu và phạm vi

Với một phương thức Spring được đánh dấu `@Async`, lời gọi của đối tượng bên ngoài thường trả quyền điều khiển cho caller trước khi công việc trên executor kết thúc. Vì vậy, **gọi được phương thức mà không thấy exception tại dòng gọi chưa chứng minh công việc đã hoàn tất hay thành công**. Bài kiểm thử đúng phải quan sát cả **kết quả sau khi hoàn tất** và **đường lỗi** theo hợp đồng return type.

Sau bài này, bạn có thể:

1. Phân biệt cách kiểm thử `@Async void`, `Future<T>` và `CompletableFuture<T>`.
2. Viết test chờ completion bằng tín hiệu có đồng bộ và **timeout hữu hạn**, không dùng `Thread.sleep(...)` để đoán công việc đã xong.
3. Xác minh giá trị trả về và nguyên nhân lỗi thực qua `Future.get(...)`, `CompletableFuture.join()`.
4. Bắt lỗi của phương thức `void` qua `AsyncUncaughtExceptionHandler`, đồng thời kiểm thử side effect/đầu ra thành công.
5. Bảo đảm test thật sự đi qua **Spring proxy** và executor, đồng thời nhận biết lỗi bị che bởi self-invocation, cấu hình test, thread khác hoặc exception của lúc submit.

**Ngữ cảnh:** Spring Framework 6.x, Java 17+, JUnit Jupiter 5, cơ chế `@EnableAsync` + `@Async` theo chế độ proxy mặc định. Ví dụ có thể dùng trong ứng dụng Spring Boot 3.x, nhưng test mẫu dùng Spring TestContext trực tiếp để chạy nhanh và tập trung vào cơ chế bất đồng bộ.

## 2. Hiểu đúng hợp đồng của lời gọi bất đồng bộ

### 2.1. Hai lớp thời điểm: gọi và hoàn tất

```text
Test thread                      Spring async proxy                 Executor worker
    |                                     |                                |
    | ---- gọi service.calculate() ---->  |                                |
    |                                     | ---- submit task ------------> |
    | <--- Future hoặc return void ------ |                                |
    |                                     |                chạy method body|
    |                                     |                thành công/lỗi  |
    | --- chờ tín hiệu / future.get() ------------------------------------> |
    | <------------ nhận result, exception hoặc thông báo --------------- |
```

Trong luồng này, cần kiểm tra hai hợp đồng độc lập:

- **Submit/invocation:** proxy có chấp nhận lịch thực thi không? Nếu thread pool đầy hoặc executor đóng, có thể xảy ra lỗi ở thời điểm **submit** (ví dụ `TaskRejectedException`), ngay trên thread gọi.
- **Execution:** task đã được nhận và bắt đầu chạy thì cuối cùng có trả kết quả, phát tín hiệu hoàn tất hoặc ném exception không? Lỗi worker được diễn giải theo return type của method.

Cấu hình `@EnableAsync` tạo cơ chế xử lý `@Async`. Mặc định Spring dựa trên proxy: khi bean khác gọi qua proxy, invocation được chuyển sang executor. Gọi trực tiếp bằng `new`, gọi nội bộ qua `this.someAsyncMethod()` hoặc gọi method target trong unit test thuần có thể **bỏ qua proxy** và trở thành synchronous. Vì vậy test cơ chế `@Async` cần lấy bean từ Spring context.

### 2.2. Bảng kiểm thử cho từng kiểu trả về

| Kiểu method | Cách quan sát thành công | Cách quan sát lỗi trong worker | Điểm không nên hiểu sai |
| --- | --- | --- | --- |
| `@Async void send(...)` | Side effect có thể quan sát, event, queue, latch hoặc mock collaborator | `AsyncUncaughtExceptionHandler` và bằng chứng lỗi nghiệp vụ | Return về từ `send()` không chứng minh đã gửi xong |
| `@Async Future<T> load(...)` | `future.get(timeout, unit)` rồi assert giá trị | `get(...)` ném `ExecutionException`, xem `getCause()` | `Future.isDone()` đúng cả khi hoàn tất **thất bại** hoặc bị hủy |
| `@Async CompletableFuture<T> load(...)` | `get(timeout, unit)`, `join()` hoặc stage tiếp theo | `get` → `ExecutionException`; `join` → `CompletionException`; xem cause | `join()` tự nó không có timeout; nên dùng `orTimeout` hoặc `get(timeout, ...)` trong test |
| Submit bị từ chối trước khi chạy | Xác nhận executor hoạt động/nhận task | Có thể ném lỗi đồng bộ tại dòng gọi | Lỗi submit khác lỗi bên trong method body |

Với `Future<T>`, exception xảy ra trong execution sẽ được lưu trên future do proxy trả về, rồi xuất hiện khi test gọi `get()`. Với `void`, không có handle kết quả để truyền exception trở về caller; Spring mặc định log lỗi, hoặc gọi `AsyncUncaughtExceptionHandler` do ứng dụng cung cấp. **Handler này không phải nơi mặc định nhận lỗi của các method trả `Future`.**

## 3. Quy tắc thiết kế test ít flaky

Một test bất đồng bộ ổn định cần có:

- **Tín hiệu xác định:** một `Future` hoàn tất, một giá trị được đưa vào `BlockingQueue`, một `CountDownLatch`, hoặc một event ghi vào dữ liệu test. Chọn tín hiệu gắn với **điều kiện mà bạn thật sự muốn chứng minh**.
- **Thời hạn chờ:** `future.get(3, SECONDS)`, `queue.poll(3, SECONDS)` hoặc `latch.await(3, SECONDS)`. Timeout phải đủ cho CI thông thường, nhưng không để test kẹt vô hạn khi implementation bị lỗi.
- **Tính đồng bộ giữa thread:** dùng `BlockingQueue`, `CountDownLatch`, `CompletableFuture`, `AtomicReference` kết hợp latch, hoặc một thư viện polling có hỗ trợ. Không đọc một `ArrayList` được worker ghi vào từ thread khác mà không có synchronization.
- **Kiểm tra nguyên nhân:** đối chiếu **kiểu exception và message/code nghiệp vụ**, không chỉ khẳng định “có exception”.
- **Độc lập giữa test:** reset trạng thái/queue trước mỗi test và đảm bảo task từ test trước đã thực sự hoàn tất. Không dùng một executor đang có công việc nền không liên quan cho cả bộ kiểm thử.
- **Kiểm tra proxy:** một bài test có thể xác nhận tên thread chạy method mang tiền tố của executor thử nghiệm; cách này giúp phát hiện lời gọi chạy sync do quên `@EnableAsync` hoặc không dùng Spring bean.

**Không dùng `Thread.sleep(100)` như phép kiểm chứng thành công.** Sau 100 ms, task có thể vẫn chưa được schedule trên máy CI; hoặc đã kết thúc trước khi test quan sát. `Thread.sleep` để tạo delay trong demo concurrency có thể hữu ích, nhưng không phải công cụ chờ kết quả có điều kiện cho test nghiệp vụ.

## 4. Ví dụ hoàn chỉnh: Spring TestContext + JUnit 5

Ví dụ gồm `build.gradle` và **một tệp test Java**, tự khai báo async bean, executor và handler để có thể sao chép vào project Gradle nhỏ. Không cần ứng dụng web hoặc chạy Spring Boot server. Các phương thức async trong ví dụ mô phỏng thao tác tải dữ liệu, gửi sự kiện và báo lỗi.

### 4.1. Phụ thuộc Gradle

Tệp `build.gradle`:

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    testImplementation 'org.springframework:spring-context:6.2.16'
    testImplementation 'org.springframework:spring-test:6.2.16'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.12.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.12.2'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Spring Context cung cấp `@EnableAsync`, `@Async`, `ThreadPoolTaskExecutor`; Spring Test cung cấp `@SpringJUnitConfig`; JUnit Jupiter cung cấp `@Test` và assertions. Trong ứng dụng Spring Boot đã có dependency management, sử dụng phiên bản do BOM của Boot quản lý thay vì trộn version bằng tay.

### 4.2. Toàn bộ tệp kiểm thử

Tệp `src/test/java/demo/async/AsyncOperationsTest.java`:

```java
package demo.async;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(AsyncOperationsTest.TestConfiguration.class)
class AsyncOperationsTest {

    @Autowired
    AsyncOperations operations;

    @Autowired
    Signals signals;

    @BeforeEach
    void clearSignals() {
        signals.completed.clear();
        signals.failures.clear();
    }

    @Test
    void futureReturnsExpectedValue() throws Exception {
        Future<String> future = operations.load("A1");

        assertEquals("loaded:A1", future.get(3, SECONDS));
    }

    @Test
    void futureErrorKeepsOriginalCause() {
        Future<String> future = operations.loadBroken("A2");

        ExecutionException error = assertThrows(
                ExecutionException.class,
                () -> future.get(3, SECONDS));

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals("load-failed:A2", error.getCause().getMessage());
    }

    @Test
    void completableFutureJoinExposesFailure() {
        CompletableFuture<String> future = operations.composeBroken("A3");

        CompletionException error = assertThrows(
                CompletionException.class,
                () -> future.orTimeout(3, SECONDS).join());

        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals("compose-failed:A3", error.getCause().getMessage());
    }

    @Test
    void voidSuccessMustBeObservedBySignal() throws Exception {
        operations.send("B1");

        assertEquals("sent:B1", signals.completed.poll(3, SECONDS));
    }

    @Test
    void voidErrorIsDeliveredToAsyncHandler() throws Exception {
        operations.sendBroken("B2");

        Failure failure = signals.failures.poll(3, SECONDS);

        assertNotNull(failure, "AsyncUncaughtExceptionHandler was not called");
        assertEquals("sendBroken", failure.methodName());
        assertEquals("B2", failure.firstArgument());
        assertInstanceOf(IllegalArgumentException.class, failure.cause());
        assertEquals("send-failed:B2", failure.cause().getMessage());
    }

    @Test
    void invocationReallyRunsOnConfiguredExecutor() throws Exception {
        String workerName = operations.workerThreadName().get(3, SECONDS);

        assertTrue(workerName.startsWith("learn-async-"), workerName);
    }

    @Configuration
    @EnableAsync
    static class TestConfiguration implements AsyncConfigurer {
        private final Signals signals = new Signals();

        @Bean
        Signals signals() {
            return signals;
        }

        @Bean
        AsyncOperations asyncOperations() {
            return new AsyncOperations(signals);
        }

        @Bean("learningAsyncExecutor")
        ThreadPoolTaskExecutor learningAsyncExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(16);
            executor.setThreadNamePrefix("learn-async-");
            return executor;
        }

        @Override
        public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
            return (cause, method, arguments) ->
                    signals.failures.add(new Failure(
                            cause,
                            method.getName(),
                            arguments.length == 0 ? null : arguments[0]));
        }
    }

    static class AsyncOperations {
        private final Signals signals;

        AsyncOperations(Signals signals) {
            this.signals = signals;
        }

        @Async("learningAsyncExecutor")
        public Future<String> load(String id) {
            return CompletableFuture.completedFuture("loaded:" + id);
        }

        @Async("learningAsyncExecutor")
        public Future<String> loadBroken(String id) {
            throw new IllegalStateException("load-failed:" + id);
        }

        @Async("learningAsyncExecutor")
        public CompletableFuture<String> composeBroken(String id) {
            throw new IllegalArgumentException("compose-failed:" + id);
        }

        @Async("learningAsyncExecutor")
        public void send(String id) {
            signals.completed.add("sent:" + id);
        }

        @Async("learningAsyncExecutor")
        public void sendBroken(String id) {
            throw new IllegalArgumentException("send-failed:" + id);
        }

        @Async("learningAsyncExecutor")
        public CompletableFuture<String> workerThreadName() {
            return CompletableFuture.completedFuture(
                    Thread.currentThread().getName());
        }
    }

    static class Signals {
        final BlockingQueue<String> completed = new LinkedBlockingQueue<>();
        final BlockingQueue<Failure> failures = new LinkedBlockingQueue<>();
    }

    record Failure(Throwable cause, String methodName, Object firstArgument) {}
}
```

**Chạy từ project Gradle đã có wrapper:**

```bash
./gradlew test --tests demo.async.AsyncOperationsTest
```

Trên Windows PowerShell, chạy tương đương `.\gradlew.bat test --tests demo.async.AsyncOperationsTest` nếu project có `gradlew.bat`. Kỳ vọng: **sáu test đều thành công**. Không khẳng định thứ tự log, ID thread hoặc chuỗi stdout cố định vì executor và test runner có thể thay đổi lịch chạy. Nếu build đang sử dụng Spring Boot, đặt test trong source set `test` và dùng dependency/JUnit tương thích của project.

### 4.3. Vì sao cấu hình trong test thực sự kiểm tra Spring `@Async`?

- `@SpringJUnitConfig` tải một `ApplicationContext` cho test, kích hoạt Spring TestContext và injection. Test gọi `operations` đã được lấy từ context, không phải đối tượng tự `new` trong test.
- `@EnableAsync` kích hoạt interception cho `@Async`; executor `learningAsyncExecutor` là một Spring bean, có tên được annotation tham chiếu trực tiếp.
- `ThreadPoolTaskExecutor` được Spring quản lý lifecycle/khởi tạo/đóng cùng context. Đặt tên thread cố định để xác nhận bài test dùng worker thực.
- `AsyncConfigurer#getAsyncUncaughtExceptionHandler()` cung cấp handler lưu exception, method name và tham số đầu tiên vào queue dùng cho test. `Signals` được tạo nội bộ trong configuration, tránh phụ thuộc bean được inject quá sớm trong lúc tạo async config.
- `BlockingQueue` làm tín hiệu giữa worker và test thread, cung cấp phối hợp thread an toàn. `poll(timeout)` trả `null` khi hết hạn; test có assertion hiển thị lỗi rõ ràng.

### 4.4. Đọc đúng từng bài test

**`futureReturnsExpectedValue()`:** ghi nhận `Future<String>` từ Spring proxy; `get(3, SECONDS)` chờ task hoàn tất và thu được `loaded:A1`. Nếu method ném exception, trả sai hoặc không kết thúc, bài test thất bại. Chỉ gọi `operations.load("A1")` mà không `get` sẽ để lọt lỗi worker.

**`futureErrorKeepsOriginalCause()`:** thân method ném `IllegalStateException` trên executor. Test mong `ExecutionException` tại `get` rồi kiểm tra `getCause()`. Nếu chỉ kiểm tra `assertThrows(Exception.class, ...)`, test có thể “xanh” do cả lỗi timeout hay cấu hình khác, không chứng minh đúng đường lỗi nghiệp vụ.

**`completableFutureJoinExposesFailure()`:** với `CompletableFuture`, `join()` thường bọc lỗi execution trong `CompletionException`; test xem `getCause()` để nhận lỗi gốc. `orTimeout(3, SECONDS)` bảo đảm test không chờ vô hạn. Nếu muốn thống nhất với `Future` hoặc dễ xử lý checked exception, gọi `future.get(3, SECONDS)` thay cho `join()`.

**`voidSuccessMustBeObservedBySignal()`:** `send()` trả về `void` ngay sau khi proxy nhận task; không có future để chờ. Test chờ `sent:B1` được đưa vào queue **từ trong body** của method. Đây là bằng chứng cụ thể rằng method đã chạm mốc cần kiểm tra. Trong hệ thống thật, thay queue bằng kết quả lưu DB, message outbound được capture, trạng thái domain, hoặc mock collaborator được kiểm chứng sau khi hoàn thành.

**`voidErrorIsDeliveredToAsyncHandler()`:** body của `sendBroken("B2")` ném exception ở worker. Test đợi handler báo `Failure` và đối chiếu method, tham số và exception. Việc gọi trực tiếp `assertThrows(IllegalArgumentException.class, () -> operations.sendBroken("B2"))` là **sai mục đích** khi async đã được proxy, vì exception của body không quay về tại dòng invocation.

**`invocationReallyRunsOnConfiguredExecutor()`:** phương thức trả tên thread của body. Test đối chiếu tiền tố `learn-async-`. Đây là dấu hiệu có giá trị rằng lời gọi đi qua async advice; nếu mất proxy hoặc `@EnableAsync`, method thường chạy trên thread gọi test và bài kiểm tra sẽ phát hiện.

## 5. Kiểm thử `void`: thành công và lỗi đều cần kênh quan sát riêng

### 5.1. Thành công không phải “không ném exception”

Sai:

```java
@Test
void misleadingTest() {
    operations.send("B1"); // Chỉ chứng minh lời gọi đã được thực hiện.
}
```

Bài test có thể hoàn tất trước khi worker chạy dòng đầu tiên, và lỗi ném trong body vẫn được handler log/ghi nhận mà test không biết. Muốn kiểm tra “gửi xong”, tín hiệu phải đặt **sau bước gửi thành công**; nếu đặt trước bước gửi, test có thể xanh dù bước gửi thất bại.

Với `void` có side effect, những cách kiểm chứng phù hợp gồm:

- Đối chiếu dữ liệu sau commit hoặc message thực sự được gửi đến fake/test broker.
- Dùng một collaborator `@Mock`/spy và cơ chế đợi interaction bằng timeout có giới hạn (hoặc Awaitility), rồi kiểm tra arguments/outcome.
- Phát một event/queue entry cuối hàm cho test. Nếu có nhiều giai đoạn, biểu diễn các trạng thái `STARTED`, `SUCCEEDED`, `FAILED` riêng để tránh nhầm.
- Dùng `CountDownLatch` trong test fixture khi chỉ cần biết công việc đã qua một mốc; latch phải được `countDown()` ở đúng nhánh cần chứng minh.

Nếu cần bắt cả success lẫn failure cho một thao tác nghiệp vụ quan trọng, cân nhắc đổi API thành `CompletableFuture<Void>` để caller có hợp đồng completion rõ ràng hơn, thay vì dựa hoàn toàn vào một method `void` không thể quan sát kết quả từ caller.

### 5.2. Handler chỉ nhận những exception thuộc phạm vi của nó

`AsyncUncaughtExceptionHandler` bắt exception **thoát khỏi body** của method `@Async` trả `void` khi Spring async interceptor nhận biết lỗi đó. Nó không thay thế tất cả cơ chế xử lý lỗi:

- Nếu method tự `try/catch` rồi nuốt exception, handler sẽ không thấy gì. Test phải kiểm tra hành vi sau `catch`.
- Nếu `void` tự khởi chạy một `CompletableFuture.runAsync(...)` khác rồi return, exception của nested future xảy ra **sau** body `void`; handler của Spring không tự nhận exception đó. Phải kiểm tra stage/callback hoặc thu thập lỗi từ nested future.
- Nếu lỗi xuất hiện lúc executor **từ chối submit**, caller có thể nhận exception đồng bộ; test phân biệt nó với lỗi ở worker.
- Handler có thể log, phát metric hoặc chuyển thành event; **nó không truyền lại exception cho thread đã gọi `void`**. Một handler production nên xử lý không blocking, không tự gây lỗi và không chứa dữ liệu bí mật trong log.

### 5.3. Dùng `CompletableFuture<Void>` khi cần hợp đồng hoàn thành

Nếu có thể thiết kế API mới, một phương thức trả `CompletableFuture<Void>` cho phép test chỉ đợi completion mà không cần hook riêng:

```java
@Async("learningAsyncExecutor")
public CompletableFuture<Void> sendWithCompletion(String id) {
    // Thực thi side effect đồng bộ trong worker.
    sendToRemoteService(id);
    return CompletableFuture.completedFuture(null);
}
```

Đây là **đoạn minh họa đặt trong service thật** có sẵn phương thức `sendToRemoteService(String)`, không phải chương trình Java độc lập. Test có thể làm:

```java
CompletableFuture<Void> completion = service.sendWithCompletion("B3");
assertNull(completion.get(3, SECONDS));
```

Nếu side effect ném exception, future hoàn thành lỗi và `get` ném `ExecutionException`. Chỉ chuyển sang `CompletableFuture<Void>` khi bạn muốn thay đổi hợp đồng public API; không giả định `@Async void` ngầm cung cấp một future.

## 6. Kiểm thử `Future`: kết quả, lỗi, hủy và timeout

### 6.1. `get`, `join` và `isDone` có ý nghĩa khác nhau

- `future.get()` chờ không giới hạn; trong test nên tránh vì có thể treo CI. `future.get(3, SECONDS)` giới hạn thời gian chờ và có thể ném `TimeoutException`.
- `future.get(...)` ném `ExecutionException` khi task hoàn thành bằng exception; nguyên nhân thực ở `getCause()`.
- `CompletableFuture.join()` không ném checked exception nhưng dùng `CompletionException` cho lỗi completion (trừ trường hợp hủy); `join` không nhận timeout trực tiếp.
- `future.isDone()` chỉ trả lời “đã kết thúc theo một cách nào đó” — bao gồm thành công, lỗi, hủy; không phải assertion về success.
- `future.isCancelled()` xác nhận yêu cầu hủy, nhưng không chứng minh side effect chưa xảy ra. Hủy có thể đua với execution.

Nên kiểm tra giá trị và exception theo **thông tin mà API cam kết**, không assert cứng vào thread name, thời gian từng millisecond hay thứ tự scheduler nếu nghiệp vụ không yêu cầu.

### 6.2. Khi return type là `CompletableFuture`, có thể quan sát ở stage

```java
CompletableFuture<String> future = service.fetchAsync("42");

String result = future
        .thenApply(String::trim)
        .orTimeout(3, SECONDS)
        .join();
```

Đoạn trên là **mã minh họa trong một test có bean `service`** trả `CompletableFuture<String>`. `thenApply` chạy khi stage trước có kết quả thành công; nếu stage trước lỗi thì chain hoàn thành exceptional, `join()` ném `CompletionException`. `exceptionally` hoặc `handle` có thể chuyển lỗi thành kết quả fallback: kiểm thử cần khẳng định **chính sách fallback** chứ không trông chờ future vẫn ném khi production code đã chủ động recover.

Ví dụ kiểm tra lỗi không bị nuốt:

```java
CompletableFuture<String> result = service.fetchAsync("missing");
CompletionException failure = assertThrows(
        CompletionException.class,
        () -> result.orTimeout(3, SECONDS).join());

assertInstanceOf(IllegalArgumentException.class, failure.getCause());
```

Đây là đoạn test minh họa; muốn nó chạy cần có service/fetchAsync được khai báo và ném loại exception như mong đợi.

### 6.3. `Future` đơn thuần không có `thenApply`, và implementation không nhất thiết trả `CompletableFuture`

Nếu hợp đồng method là `Future<String>`, chỉ dùng các API thuộc `Future` như `get`, `cancel`, `isDone`. Đừng ép kiểu future do proxy trả thành `CompletableFuture`. Muốn composition, thiết kế signature trả `CompletableFuture<T>` thực sự, rồi sử dụng `thenApply`, `thenCompose` hoặc `handle`. Trong method target trả `CompletableFuture.completedFuture(value)` là cách hợp lệ để đưa kết quả đã tính trong worker về cho Spring proxy; **future caller nhận được vẫn đại diện execution của proxy**.

## 7. Kiểm thử hành vi đang chạy: tránh race condition bằng latch

Đôi khi cần chứng minh rằng method **chưa hoàn tất** trước khi cho một dependency tiếp tục. Khi đó sử dụng hai latch `entered` và `release` để test điều khiển tiến trình một cách xác định.

Sơ đồ:

```text
Worker:       entered.countDown() -> release.await() -> trả kết quả
Test thread:  entered.await(timeout) -> assert !future.isDone()
              -> release.countDown() -> future.get(timeout)
```

Một đoạn ý tưởng cho test:

```java
CountDownLatch entered = new CountDownLatch(1);
CountDownLatch release = new CountDownLatch(1);

try {
    CompletableFuture<String> future = service.computeWithGate(entered, release);
    assertTrue(entered.await(3, SECONDS), "worker never started");
    assertFalse(future.isDone(), "completed before release");

    release.countDown();
    assertEquals("done", future.get(3, SECONDS));
} finally {
    release.countDown(); // Không bỏ kẹt worker nếu assertion thất bại.
}
```

Đây là **đoạn minh họa test**, yêu cầu method `computeWithGate(CountDownLatch, CountDownLatch)` được định nghĩa riêng trong fixture. Cần xử lý `InterruptedException` trong worker một cách tường minh. Hãy đặt latch trong test fixture hoặc dependency giả, tránh cấy công cụ kiểm thử vào code production. Nếu nhiều concurrent task, dùng `Phaser`/`CyclicBarrier` theo nhu cầu; tránh dàn dựng chỉ bằng `sleep`.

**Lưu ý:** timeout chứng minh task không hoàn tất trong giới hạn thử nghiệm khi synchronization đang chặn nó, chứ không chứng minh một độ trễ cố định của scheduler. Hãy assert điều kiện nghiệp vụ và mối quan hệ trước/sau, không assert từng mili giây trôi qua.

## 8. Unit test logic và integration test proxy là hai cấp độ khác nhau

**Unit test thuần:** tạo service bằng `new`/Mockito, gọi method body trực tiếp để kiểm tra dữ liệu, tính toán, phân nhánh exception. Nó nhanh và hữu ích, nhưng **không chứng minh Spring `@Async` chạy bất đồng bộ**. Nếu test trực tiếp gọi một method `void` rồi dùng `assertThrows` để bắt lỗi, bạn chỉ đang kiểm tra exception của Java method trực tiếp.

**Integration test cho `@Async`:** mở Spring context tối thiểu như ví dụ, bật `@EnableAsync`, inject bean proxy, dùng executor thật có tên thread riêng và handler chuyên dụng. Test cả đường thành công lẫn đường lỗi bằng signal/future. Cách này xác minh wiring, interception và executor/handler mà unit test bỏ qua.

**Integration test nghiệp vụ:** nếu job thật cập nhật DB, gửi message hoặc gọi API, dùng fake server, test broker, test DB hoặc Testcontainers theo phạm vi cần thiết. Chờ bằng trạng thái quan sát được với timeout; kiểm tra dữ liệu sau commit và cleanup để test không ảnh hưởng nhau.

| Test | Dùng real Spring context? | Kiểm tra điều gì? |
| --- | --- | --- |
| Hàm thuần tính toán/validation | Không bắt buộc | Kết quả và exception của logic |
| `@Async` chạy trên worker | Có | Proxy/annotation/executor |
| Lỗi của `@Async void` vào handler | Có (hoặc kiểm thử riêng handler) | Cấu hình handler và đường exception |
| `Future.get` có result/cause đúng | Có nếu muốn test cơ chế proxy; không nếu chỉ test future thuần | Kết quả execution và cách truyền lỗi |
| Tác động DB/network thật | Tuỳ chiến lược integration | Side effect sau bất đồng bộ và consistency |

## 9. Bẫy thường gặp và cách phát hiện

### Bẫy 1 — Không chờ completion

```java
@Test
void doesNotProveCompletion() {
    operations.send("B1");
    // Test kết thúc trước khi async side effect hoàn tất.
}
```

**Sửa:** chờ queue/latch, quan sát collaborator hoặc dữ liệu thật, và đặt timeout.

### Bẫy 2 — `assertThrows` bọc trực tiếp lời gọi `void`

```java
assertThrows(IllegalArgumentException.class,
        () -> operations.sendBroken("B2"));
```

**Sai với exception worker khi `@Async` hoạt động:** `sendBroken()` có thể return ngay; exception được handler nhận. **Sửa:** assert event ghi bởi `AsyncUncaughtExceptionHandler`. Riêng lỗi submit như executor reject có thể ném đồng bộ; cần test riêng đường này nếu contract yêu cầu.

### Bẫy 3 — Không kiểm tra root cause của `Future`

```java
assertThrows(Exception.class,
        () -> operations.loadBroken("A2").get(3, SECONDS));
```

**Thiếu chính xác:** timeout, cancel, cấu hình sai hay lỗi nghiệp vụ đều có thể làm assertion thỏa. **Sửa:** assert `ExecutionException` rồi kiểm tra `getCause()` và message/mã lỗi mong đợi.

### Bẫy 4 — Self-invocation bỏ qua `@Async`

```java
public void start() {
    this.sendBroken("B2"); // Trong cùng instance, không đi qua proxy mặc định.
}
```

Nếu test gọi `start()` rồi mong handler được gọi, kết quả có thể khác với khi gọi bean proxy từ bên ngoài. **Sửa:** tách lời gọi sang một bean khác được inject qua Spring hoặc kiểm chứng trực tiếp ở đường gọi chính thức. Không kết luận method thực sự chạy async chỉ nhờ annotation tồn tại trong source.

### Bẫy 5 — Sai executor, race hoặc worker từ test khác

Có nhiều executor bean mà không chỉ rõ `@Async("beanName")` thì job có thể chạy trên executor không dự tính; test dựa trên thời gian trở nên không ổn định. **Sửa:** cấu hình executor cụ thể, có tiền tố thread, giới hạn pool, cleanup context và tránh chia sẻ signal/counter giữa những test chạy song song. Nếu test vẫn chạy song song theo cấu hình JUnit, mỗi invocation cần correlation ID riêng để đón đúng tín hiệu.

### Bẫy 6 — Lầm tưởng transaction cùng thread

Một test có `@Transactional` trong Spring TestContext thường quản lý transaction trên **test thread**; tác vụ `@Async` chạy trên thread khác và không mặc nhiên thừa hưởng transaction/rollback đó. Không đặt giả định “mọi dữ liệu được async ghi sẽ rollback cùng test” nếu không kiểm tra lifecycle thật. Với DB integration test, dùng transaction độc lập, cleanup dữ liệu hoặc test container có isolation.

### Bẫy 7 — Biến handler thành nơi nuốt lỗi và khiến test giả xanh

Nếu handler chỉ ghi log mà test không thu thập exception, bài test `void` thất bại trong worker nhưng runner vẫn báo thành công. **Sửa:** trong test fixture, đổi handler thành kênh tín hiệu có thể assert. Trong production, log có correlation ID, metric/alert, retry và dead-letter theo tính chất nghiệp vụ; tránh tự ý retry vô hạn trong handler.

### Bẫy 8 — Lỗi bên trong nested future không đến handler của `void`

```java
@Async
public void fireAndForget() {
    CompletableFuture.runAsync(() -> {
        throw new IllegalStateException("nested failure");
    });
}
```

Lỗi trên nằm trong task do `CompletableFuture.runAsync` khởi chạy, ngoài thân `void` sau khi return; Spring `AsyncUncaughtExceptionHandler` của method này **không tự thấy** exception của task nested. **Sửa:** quản lý stage đó bằng `whenComplete`/`handle`, hoặc đổi API trả `CompletableFuture` và compose chuỗi rõ ràng.

### Bẫy 9 — Dùng assertion “không có tín hiệu lỗi ngay bây giờ”

`assertTrue(signals.failures.isEmpty())` ngay sau `send()` không bảo đảm worker chưa kịp chạy lỗi. Để kiểm thử điều kiện “không có lỗi”, trước hết chờ **tín hiệu hoàn tất công việc** và chỉ khi đó mới kiểm tra kênh lỗi ứng với correlation ID; nếu hệ thống có delayed callbacks, cần xác định thời điểm kết thúc vòng đời của nghiệp vụ trước khi khẳng định.

## 10. Mở rộng bộ kiểm thử theo contract nghiệp vụ

Sau các case cơ bản, xem xét những tình huống có ý nghĩa cho ứng dụng thật:

1. **Lỗi từ dependency:** fake repository/HTTP client ném exception trong worker; `Future` giữ cause, `void` đi qua handler; xác nhận không có side effect thành công giả.
2. **Timeout:** dependency không trả lời; test phải kết thúc hữu hạn và cleanup worker. Timeout ở `Future.get` chỉ chặn **test chờ**, không mặc nhiên hủy execution; nếu muốn hủy cần hành động `cancel(...)` riêng và code có hỗ trợ interruption/cancellation.
3. **Executor quá tải:** dùng executor dung lượng nhỏ và gate để giữ worker/queue, submit thêm để chủ động kích hoạt rejection; assert đúng exception submit, không nhầm với lỗi method body.
4. **Nhiều invocation cùng lúc:** gắn request ID cho mỗi việc, nhận đúng result/failure; không dùng một queue chung không lọc nếu test chạy song song.
5. **Retry và idempotency:** nếu ứng dụng retry, assert số lần gọi dependency, thời điểm cho phép và kết quả cuối; khi lỗi lặp, không tạo hai side effect không mong muốn.
6. **Dọn dẹp:** shutdown executor do test context quản lý, tránh thread sống sót làm bộ test bị treo. Latch trong test phải được nhả trong `finally`, ngay cả khi assertion thất bại.
7. **Observability:** log/metric handler nên ghi loại lỗi và method; không dùng kiểm tra log đơn thuần thay cho assertion có cấu trúc nếu test cần kết luận nhân quả.

## 11. Checklist kết thúc bài kiểm thử

- [ ] Test gọi **Spring bean proxy** (không phải target tự khởi tạo bằng `new`); `@EnableAsync` đang bật.
- [ ] Executor được xác định rõ; có thể kiểm chứng task đang chạy ở worker thread khi điều đó là yêu cầu.
- [ ] Với `Future<T>`/`CompletableFuture<T>`: có test nhận **đúng giá trị**, test nhận **đúng exception/cause**.
- [ ] Với `void`: có tín hiệu chứng minh **side effect thành công**, và một `AsyncUncaughtExceptionHandler` hoặc kênh lỗi tương đương để xác minh **nhánh thất bại**.
- [ ] Các chờ đều có timeout hữu hạn; không phụ thuộc `Thread.sleep` hay thứ tự chạy ngẫu nhiên.
- [ ] Dữ liệu đa thread dùng primitive có synchronization; test độc lập, không có worker bỏ kẹt.
- [ ] Phân biệt exception khi **submit**, exception khi **thực thi** và exception của **task nested**.
- [ ] Với DB/network, xác nhận trạng thái nghiệp vụ thực, transaction boundary, retry và cleanup.

**Điểm cần nhớ:** `void` không có future cho caller chờ nên muốn kiểm thử đầy đủ phải chủ động thiết kế **kênh quan sát success và error**. `Future`/`CompletableFuture` cung cấp completion handle để khẳng định kết quả hoặc nguyên nhân exception. Cả hai chỉ có ý nghĩa kiểm thử cơ chế Spring `@Async` khi lời gọi thực sự đi qua proxy và executor.

## Tài liệu chính thức

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `@EnableAsync`, `@Async`, proxy, `Future`, `CompletableFuture` và xử lý exception của `void`.
- [Spring Framework — `AsyncConfigurer` JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/annotation/AsyncConfigurer.html): hook tùy chỉnh executor và `AsyncUncaughtExceptionHandler`.
- [Spring Framework — `AsyncExecutionInterceptor` JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/interceptor/AsyncExecutionInterceptor.html): cách interceptor quản lý Future/result/error.
- [Spring Framework — `@SpringJUnitConfig`](https://docs.spring.io/spring-framework/reference/6.2/testing/annotations/integration-junit-jupiter.html): khởi chạy Spring TestContext với JUnit Jupiter.
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/): assertions, timeout và kiểm thử với JUnit Jupiter.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-007">Kiểm thử quá tải, từ chối, timeout và hủy</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử quá tải, từ chối, timeout và hủy trong Spring Framework

Ứng dụng Spring có thể nhận tác vụ nhanh hơn tốc độ xử lý của executor. Khi đó, tác vụ phải chờ trong hàng đợi, bị từ chối hoặc được thực thi theo một chính sách khác. Một tác vụ được chấp nhận cũng có thể chạy quá thời hạn của caller; việc caller hết thời gian chờ hay hủy một `Future` chưa chắc đã dừng công việc bên dưới.

Bài này hướng dẫn **kiểm thử hành vi**, không chỉ kiểm tra cấu hình: tạo quá tải có kiểm soát, chủ động lấp đầy pool và queue, xác nhận chính sách từ chối, phân biệt timeout với cancellation, quan sát interrupt và dọn sạch tài nguyên sau test. Ngữ cảnh là **Spring Framework concurrency**, đặc biệt `ThreadPoolTaskExecutor` và các tác vụ `@Async`, với Java 17+ và JUnit 5.

## 1. Phân biệt bốn tình huống cần kiểm thử

| Tình huống | Điều xảy ra | Điều cần khẳng định trong test |
| --- | --- | --- |
| **Quá tải (overload/saturation)** | Số tác vụ đang chạy và đang chờ chạm giới hạn cho phép | Pool và hàng đợi đã đạt trạng thái định trước; test không phụ thuộc may rủi timing |
| **Từ chối (rejection)** | Executor không nhận thêm việc theo chính sách hiện tại | `AbortPolicy` gây lỗi đúng kiểu, hoặc `CallerRunsPolicy` chạy ở luồng gửi việc |
| **Timeout** | Người đợi kết quả hoặc một deadline không thể chờ thêm | Caller nhận timeout; kiểm tra riêng tác vụ bên dưới còn chạy hay đã kết thúc |
| **Hủy (cancellation)** | Caller yêu cầu bỏ kết quả hoặc dừng thực thi | `Future` báo cancelled; nếu muốn dừng thực sự, phải quan sát worker nhận và xử lý interrupt |

Một trường hợp có thể dẫn đến trường hợp khác: pool bận khiến job nằm chờ lâu; caller timeout dù job vẫn nằm trong queue; caller hủy Future nhưng worker có thể đã bắt đầu ghi dữ liệu. Vì vậy, **“timeout đã trả về” không đồng nghĩa “job đã dừng”**, và **“cancel trả về true” không bảo đảm thao tác I/O đã bị ngắt**.

### Trục thời gian của một tác vụ

```text
Caller                      Executor                          Worker
  | submit(task)                |                                |
  |---------------------------->| nhận / xếp hàng                |
  |                             |----> queue (có thể chờ) ------>|
  |                             |                                | chạy task
  | get(timeout)                |                                |
  |---- đợi kết quả ------------|--------------------------------|
  | <-- TimeoutException ------|                                | vẫn có thể chạy
  | cancel(...)                 |                                |
  |---------------------------->| yêu cầu hủy (tùy Future)       | có thể nhận interrupt
```

Cần đo riêng **thời gian chờ trong queue**, **thời gian thực thi**, **thời gian caller đợi**, và **tổng thời gian end-to-end**; các đồng hồ đo đó có điểm bắt đầu/kết thúc khác nhau.

## 2. Mô hình pool có thể làm đầy một cách xác định

`ThreadPoolTaskExecutor` của Spring bao một `ThreadPoolExecutor` từ JDK. Để kiểm thử overload, dùng cấu hình **nhỏ, hữu hạn** và chủ động khóa worker:

```java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setThreadNamePrefix("boundary-test-");
executor.setCorePoolSize(1);
executor.setMaxPoolSize(1);
executor.setQueueCapacity(1);
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
executor.initialize();
```

Trong trường hợp này:

1. **A** được nộp trước, bắt đầu chạy và chờ trên `CountDownLatch` → một worker bận.
2. **B** được nộp tiếp → queue có đúng một phần tử.
3. **C** được nộp khi A vẫn đang chờ → worker đã đạt `maxPoolSize=1` và queue đã đầy.
4. Với `AbortPolicy`, C bị từ chối ngay lúc submit. `ThreadPoolTaskExecutor` bọc lỗi thành `org.springframework.core.task.TaskRejectedException`.

Đây là điều kiện kiểm thử được **điều khiển bằng tín hiệu đồng bộ**, không dựa vào việc “gửi thật nhiều job và hy vọng máy đủ chậm”.

**Vì sao đặt `queueCapacity` rõ ràng?** Nếu dùng queue rất lớn, tác vụ có thể tích tụ mà chưa có rejection; tăng `maxPoolSize` cũng không nhất thiết tạo thêm worker ngay khi queue vẫn còn chỗ. Với `queueCapacity=0`, Spring sử dụng cơ chế hand-off trực tiếp (`SynchronousQueue`), nên hành vi tăng worker và từ chối khác. Các giá trị `1/1/1` chỉ dành cho fixture test; không áp dụng máy móc cho production.

## 3. Nguyên tắc viết concurrency test ổn định

- **Đợi tín hiệu “đã bắt đầu”** bằng `CountDownLatch.await(timeout)` trước khi xác nhận worker đang bận. Thời hạn vài giây ở đây là *giới hạn an toàn để test không treo*, không phải điều kiện phân định hiệu năng.
- **Giữ worker bận có chủ đích** cho tới khi đã nộp đủ tác vụ và quan sát trạng thái queue. Tránh `Thread.sleep(...)` để đoán khi nào pool sẽ đầy.
- **Chủ động nhả latch trong `finally`** và `shutdown()` executor, kể cả khi assertion thất bại; không để thread của test treo mãi.
- **Kiểm tra kết quả quan sát được**: loại exception, `Future` state, thread thực thi, dấu hiệu nhận interrupt và tác vụ nào đã chạy. Không chỉ so sánh số mili-giây.
- **Tách test chức năng với load test**: một fixture nhỏ chứng minh chính sách hoạt động; đo throughput/latency dưới hàng nghìn request là bài stress/performance riêng, cần môi trường, tải và tiêu chí thống kê.
- **Không dùng `assertTimeoutPreemptively` tùy tiện với mã dùng `ThreadLocal`, transaction hoặc security context**: JUnit có thể chạy code được test ở thread khác, làm sai ngữ cảnh. Khi cần deadline cho Future, ưu tiên `get(timeout, unit)` rồi tự cleanup.

## 4. Bộ kiểm thử JUnit 5 chạy trên `ThreadPoolTaskExecutor`

Ví dụ dưới đây là một **file test hoàn chỉnh** dành cho project đã có `spring-context` (Spring Framework 6.x), JUnit Jupiter 5 và Java 17+. Nó kiểm thử trực tiếp executor để cô lập semantics của pool; phần `@Async` qua Spring proxy được kiểm tra riêng ở mục sau.

**Tệp `ExecutorFailureModesTest.java`**:

```java
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ExecutorFailureModesTest {

    private static ThreadPoolTaskExecutor newExecutor(
            RejectedExecutionHandler policy) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("boundary-test-");
        executor.setRejectedExecutionHandler(policy);
        executor.initialize();
        return executor;
    }

    private static Runnable blockingTask(
            CountDownLatch started, CountDownLatch release) {
        return () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    @Test
    void rejectsThirdSubmissionWhenWorkerAndQueueAreFull() throws Exception {
        ThreadPoolTaskExecutor executor =
                newExecutor(new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();

        try {
            executor.execute(blockingTask(started, release)); // A đang chạy
            assertTrue(started.await(5, SECONDS));

            executor.execute(() -> { });                    // B nằm trong queue
            assertEquals(1, executor.getQueueSize());

            assertThrows(TaskRejectedException.class,
                    () -> executor.execute(
                            () -> rejectedTaskRan.set(true))); // C bị từ chối
            assertFalse(rejectedTaskRan.get());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void callerRunsPolicyExecutesOverflowOnSubmittingThread() throws Exception {
        ThreadPoolTaskExecutor executor =
                newExecutor(new ThreadPoolExecutor.CallerRunsPolicy());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> actualThread = new AtomicReference<>();

        try {
            executor.execute(blockingTask(started, release)); // A
            assertTrue(started.await(5, SECONDS));
            executor.execute(() -> { });                    // B trong queue
            assertEquals(1, executor.getQueueSize());

            Thread submittingThread = Thread.currentThread();
            executor.execute(
                    () -> actualThread.set(Thread.currentThread())); // C
            assertSame(submittingThread, actualThread.get());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void getTimeoutDoesNotCancelRunningTask() throws Exception {
        ThreadPoolTaskExecutor executor =
                newExecutor(new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> result = executor.submit(() -> {
                started.countDown();
                release.await();
                return "OK";
            });
            assertTrue(started.await(5, SECONDS));

            assertThrows(TimeoutException.class,
                    () -> result.get(100, MILLISECONDS));
            assertFalse(result.isDone());
            assertFalse(result.isCancelled());

            release.countDown();
            assertEquals("OK", result.get(5, SECONDS));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void futureCancellationInterruptsCooperativeWorker() throws Exception {
        ThreadPoolTaskExecutor executor =
                newExecutor(new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        try {
            Future<?> result = executor.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(started.await(5, SECONDS));
            assertTrue(result.cancel(true));
            assertTrue(result.isCancelled());
            assertThrows(CancellationException.class, () -> result.get());
            assertTrue(interrupted.await(5, SECONDS));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void cancellingCompletableFutureDoesNotInterruptSupplier()
            throws Exception {
        ThreadPoolTaskExecutor executor =
                newExecutor(new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean();

        try {
            CompletableFuture<String> result =
                    CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        try {
                            release.await();
                            return "OK";
                        } catch (InterruptedException e) {
                            sawInterrupt.set(true);
                            Thread.currentThread().interrupt();
                            return "INTERRUPTED";
                        } finally {
                            finished.countDown();
                        }
                    }, executor);

            assertTrue(started.await(5, SECONDS));
            assertTrue(result.cancel(true));
            assertTrue(result.isCancelled());

            // Future đã canceled, nhưng supplier còn bị giữ trên release.
            assertEquals(1L, finished.getCount());
            release.countDown();
            assertTrue(finished.await(5, SECONDS));
            assertFalse(sawInterrupt.get());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
```

### Vì sao từng assertion có ý nghĩa?

**Test 1 — overload và rejection:** A được xác nhận đã vào worker trước khi submit B, nên B chiếm queue. C không có đường nào để được chấp nhận với `AbortPolicy`; assertion kiểm tra `TaskRejectedException` ngay tại lời gọi `execute`, đồng thời xác minh thân C chưa chạy. `TaskRejectedException` là kiểu exception của Spring; khi dùng trực tiếp `java.util.concurrent.ThreadPoolExecutor` thì lỗi AbortPolicy là `RejectedExecutionException`.

**Test 2 — `CallerRunsPolicy`:** khi executor *chưa shutdown* và đã bão hòa, C chạy đồng bộ trong thread gọi `execute`. Điều này tạo backpressure nhưng khiến luồng HTTP/request có thể phải gánh công việc nặng, làm tăng latency. Một test kỳ vọng rejection sẽ sai nếu chính sách đang là `CallerRunsPolicy`; phải khẳng định thread thực thi thay thế.

**Test 3 — timeout:** `result.get(100, MILLISECONDS)` chỉ giới hạn thời gian **caller chờ**. Sau `TimeoutException`, job vẫn đang bị giữ trên `release`, vì vậy `isDone()` và `isCancelled()` đều false. Khi giải phóng latch, cùng `Future` vẫn trả `"OK"`.

**Test 4 — `Future.cancel(true)`:** trạng thái cancelled được xác nhận riêng, sau đó test dùng `interrupted` để chứng minh **thân job thật sự quan sát `InterruptedException`**. Nếu bỏ assertion thứ hai, `isCancelled()` đơn độc không đủ bằng chứng worker đã dừng.

**Test 5 — `CompletableFuture.cancel(true)`:** JDK định nghĩa cancellation của `CompletableFuture` là một kiểu hoàn thành ngoại lệ; tham số `mayInterruptIfRunning` **không điều khiển interrupt** của supplier. Stage có thể cancelled trong lúc supplier vẫn chờ latch. Test đợi supplier thoát sau khi nhả latch và xác nhận không có interrupt do lệnh cancel này.

**Chạy test:** trong dự án đã cài Spring 6.x và JUnit Jupiter, đặt tệp vào test source set tương ứng, rồi chạy:

```bash
./gradlew test --tests "*ExecutorFailureModesTest"
```

Trên Windows có thể dùng `gradlew.bat test --tests "*ExecutorFailureModesTest"`. Các test trong file không yêu cầu HTTP server hoặc database. Nếu dự án không dùng Gradle, chạy lớp test bằng runner JUnit Jupiter của build tool tương ứng. Các code fence Spring/JUnit yêu cầu thư viện trên classpath nên **không thể chạy chỉ bằng `javac` thuần**.

## 5. Kiểm tra đúng ranh giới `@Async` của Spring

Các test trực tiếp executor chứng minh hành vi bên dưới, nhưng không chứng minh một phương thức `@Async` **đã đi qua Spring proxy**. Nếu tự gọi `this.method()` trong cùng bean, lời gọi thường bỏ qua proxy và chạy ngay trên caller. Để bổ sung kiểm thử tích hợp, tạo một Spring context nhỏ với executor đặt tên rõ ràng.

**Tệp `AsyncProxyTest.java`** (Spring Framework 6.x, Spring AOP, JUnit 5):

```java
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncProxyTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class Config {
        @Bean("limitedExecutor")
        ThreadPoolTaskExecutor limitedExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(1);
            executor.setThreadNamePrefix("async-integration-");
            return executor;
        }

        @Bean
        WorkerService workerService() {
            return new WorkerService();
        }
    }

    public static class WorkerService {
        @Async("limitedExecutor")
        public CompletableFuture<String> currentThreadName() {
            return CompletableFuture.completedFuture(
                    Thread.currentThread().getName());
        }
    }

    @Test
    void goesThroughSpringProxyAndChosenExecutor() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            String name = context.getBean(WorkerService.class)
                    .currentThreadName().get(5, SECONDS);
            assertTrue(name.startsWith("async-integration-"));
        }
    }
}
```

Test này xác nhận bean được Spring tạo/proxy và qualifier `@Async("limitedExecutor")` được áp dụng. Để kiểm tra rejection qua **API nghiệp vụ**, làm đầy chính executor đó bằng A/B, rồi gọi service từ **bean khác** (hoặc gọi qua proxy lấy từ context); kiểm tra đường truyền exception và chính sách lỗi của ứng dụng. Đừng tự động cho rằng `@Async` chuyển một rejection thành HTTP 503: Spring không quyết định mã HTTP nghiệp vụ thay bạn.

Với `@Async` trả `Future`/`CompletableFuture`, lỗi thực thi sau khi task được nhận có thể xuất hiện khi caller lấy kết quả. Với `@Async void`, caller không có Future để đợi; Spring sử dụng cơ chế `AsyncUncaughtExceptionHandler` cho ngoại lệ không bắt. **Lỗi từ chối ngay lúc submit** cần được xem xét riêng với **lỗi bên trong worker**.

## 6. Timeout có ba mức khác nhau

### 6.1 Timeout của người đợi: `Future.get(timeout, unit)`

```java
try {
    String value = result.get(250, TimeUnit.MILLISECONDS);
    // Dùng kết quả nếu hoàn tất đúng hạn chờ.
} catch (TimeoutException timeout) {
    // Caller hết thời gian chờ; result có thể vẫn chạy.
    // Ứng dụng quyết định có thử cancel hay tiếp tục xử lý nền.
}
```

`get(timeout)` không tự gọi `cancel`, không đặt deadline cho database và không hủy HTTP call từ worker. Test phải xác minh cả **lỗi của caller** lẫn **trạng thái tác vụ**. Đừng suy ra có interrupt chỉ vì `get` đã hết thời hạn.

### 6.2 Timeout của stage: `CompletableFuture.orTimeout(...)`

```java
CompletableFuture<String> stage = new CompletableFuture<>();
stage.orTimeout(200, TimeUnit.MILLISECONDS);
```

Nếu chưa hoàn tất đúng hạn, **chính stage này** hoàn thành ngoại lệ với nguyên nhân `TimeoutException`; `orTimeout` trả lại cùng instance. `completeOnTimeout(fallback, ...)` cũng tác động lên stage, nhưng hoàn thành bằng giá trị fallback thay vì lỗi. Cả hai không bảo đảm dừng supplier hoặc request I/O đang chạy ở nơi khác.

Một test kiểm tra contract này có thể tạo stage chưa complete, gọi `orTimeout`, rồi dùng `get(5, SECONDS)` để nhận `ExecutionException` với `getCause()` là `TimeoutException`. Không nên chỉ khẳng định rằng test “chạy dưới 200ms” vì scheduling CI có thể dao động.

### 6.3 Deadline toàn tác vụ hoặc request

Một request có thể đợi queue 300ms, chạy nghiệp vụ 300ms, chờ DB 1s; timeout 500ms ở phía HTTP có thể xảy ra trong khi worker vẫn đang thao tác với DB. Muốn thực sự tôn trọng **deadline end-to-end** cần phối hợp:

- Ghi nhận deadline hoặc remaining budget ở entrypoint.
- Chặn submit nếu budget không đủ, hoặc tính cả thời gian xếp hàng.
- Đặt timeout riêng cho network/DB theo API đang dùng; các I/O timeout có cơ chế riêng.
- Sau timeout hoặc hủy, có hành vi cleanup/rollback/compensation phù hợp. Transaction rollback không tự đến chỉ vì client mất kết nối.
- Tránh ghi kết quả muộn vào một response đã đóng; nhưng nếu công việc nền được chủ đích cho phép tiếp tục thì phải bảo đảm idempotency.

## 7. Hủy đúng: trạng thái Future khác với việc đã dừng công việc

Với `Future` do `ExecutorService.submit` tạo, `cancel(false)` cố hủy **nếu chưa bắt đầu**, không yêu cầu interrupt một job đang chạy. `cancel(true)` có thể interrupt thread thực thi nếu implementation biết thread đó. Đây là **nỗ lực hợp tác**, không phải lệnh cưỡng bức hủy CPU, socket, database hoặc side effect.

Ví dụ bên dưới là mẫu thân job có thể đáp ứng cancellation khi đang chờ:

```java
try {
    // Đợi tín hiệu hoặc thao tác blocking có hỗ trợ interrupt.
    signal.await();
    processNextStep();
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt(); // bảo toàn tín hiệu hủy
    // Thoát khỏi tác vụ; không tiếp tục bước gây side effect.
    return;
} finally {
    releaseOwnedResources();
}
```

Đoạn này là **mã minh họa bên trong một phương thức**, phụ thuộc `signal`, `processNextStep()` và `releaseOwnedResources()` của ứng dụng. Nếu API ném `InterruptedException`, một hướng khác là cho ngoại lệ lan ra; điều quan trọng là **không nuốt mất tín hiệu rồi tiếp tục chạy như chưa có gì xảy ra**.

### Hủy tác vụ đang nằm trong queue

Kịch bản riêng để kiểm thử:

1. A chiếm worker và bị giữ bằng latch.
2. B được `submit(...)` vào queue, nhận `Future<?>`.
3. Gọi `B.cancel(false)` **trước khi nhả A**; xác nhận `isCancelled()`.
4. Nhả A, đợi executor đi qua B rồi chạy một tác vụ **sentinel** được đặt sau B (fixture cần đủ queue capacity hoặc nộp sentinel đúng lúc).
5. Khi sentinel đã chạy, xác nhận thân B **không thực hiện side effect**.

Không dùng phép kiểm tra `queue.size() == 0` ngay sau `cancel(false)` như yêu cầu bắt buộc: một `FutureTask` đã hủy vẫn có thể còn nằm trong queue vật lý cho đến khi được lấy ra hoặc dọn dẹp. Mục tiêu là **không chạy thân tác vụ đã hủy**.

### `CompletableFuture` không tự dừng producer

`CompletableFuture.cancel(true)` đánh dấu kết quả bị hủy nhưng theo đặc tả JDK, đối số `true` **không điều khiển interrupt**. Nếu muốn hủy công việc bên dưới, cần một cơ chế riêng: giữ `Future` của producer để yêu cầu cancel, truyền cancellation token và để worker kiểm tra, hoặc sử dụng API I/O có hỗ trợ cancel. Cơ chế đó cũng cần test riêng.

Không đồng nhất các khái niệm sau:

| Kiểm tra | Chứng minh được | Chưa chứng minh được |
| --- | --- | --- |
| `future.isCancelled()` | Future ghi nhận trạng thái hủy | Worker thực sự dừng hoặc rollback |
| `future.isDone()` | Future đã kết thúc ở góc nhìn API | Kết thúc thành công |
| `future.get()` ném `CancellationException` | Không thể lấy kết quả bình thường từ Future đã hủy | Remote I/O đã dừng |
| Latch trong worker báo nhận interrupt | Code worker đã quan sát tín hiệu interrupt | Mọi tài nguyên/side effect đã hoàn tất cleanup |
| Log “cleanup complete” hoặc hook cleanup được xác nhận | Cleanup theo contract đã chạy | Tự động hoàn tác dữ liệu đã commit trước đó |

## 8. Ví dụ Java 17 độc lập: timeout không hủy công việc

Ví dụ này dùng **chỉ JDK**, không cần Spring hoặc JUnit. Nó khóa job cho đến sau khi caller nhận `TimeoutException`, nên stdout xác định và không phụ thuộc may rủi thread scheduling.

**Tệp `TimeoutDoesNotCancel.java`:**

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimeoutDoesNotCancel {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);

        Future<String> result = executor.submit(() -> {
            release.await();
            return "DONE";
        });

        try {
            try {
                result.get(50, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                System.out.println("Waiting timed out");
                System.out.println("Cancelled: " + result.isCancelled());
            }
            release.countDown();
            System.out.println("Result: " + result.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac TimeoutDoesNotCancel.java
java TimeoutDoesNotCancel
```

Stdout **chính xác**:

```text
Waiting timed out
Cancelled: false
Result: DONE
```

Vì `release.countDown()` chỉ xảy ra sau khi `get(50ms)` đã ném `TimeoutException`, không có đường nào để tác vụ trả `"DONE"` trước timeout trong luồng điều khiển này. Sau khi nhả latch, Future vẫn trả kết quả bình thường.

## 9. Thử nghiệm end-to-end qua API: dự kiến đáp ứng khi quá tải

Sau khi bộ unit test chứng minh executor hoạt động đúng, kiểm tra thêm hành vi mà người dùng API thật sự thấy. Ví dụ một endpoint nhận công việc bất đồng bộ:

```text
Client -> Controller -> Application service -> @Async("limitedExecutor")
                                             -> worker / queue
```

Tùy yêu cầu nghiệp vụ, hãy **định nghĩa contract** rõ ràng trước khi viết test. Ví dụ:

| Điều kiện | Chính sách minh họa của ứng dụng | Cần kiểm tra |
| --- | --- | --- |
| Còn khả năng nhận | HTTP 202 với jobId | Job được tiếp nhận đúng một lần, có trạng thái truy vấn |
| Executor từ chối | HTTP 503 hoặc phản hồi giới hạn tải theo quy ước API | Không trả nhầm “accepted”; không tạo job treo; ghi metric rejection |
| Người đợi kết quả timeout | HTTP 504 nếu request phụ thuộc upstream, hoặc mã phù hợp contract của ứng dụng | Công việc nền được xử lý tiếp hay được hủy theo chính sách đã định |
| Client đóng kết nối | Policy tiếp tục hoặc hủy job | Không tự suy luận disconnect đồng nghĩa worker dừng |
| Client yêu cầu hủy | Phản hồi hủy theo trạng thái job | Cancellation đã được tiếp nhận, cleanup/compensation được kiểm tra |

**Mã HTTP trên là ví dụ thiết kế API**, không phải hành vi mặc định của `@Async` hay `ThreadPoolTaskExecutor`. `503 Service Unavailable` thường phù hợp khi service quá tải tạm thời; `429 Too Many Requests` có thể phù hợp cho giới hạn theo client; `504 Gateway Timeout` là ngữ nghĩa gateway/proxy. Chọn mã theo vị trí timeout và contract của ứng dụng.

Ở integration test, hãy kích hoạt đủ A/B bằng latch (hoặc một double được kiểm soát), sau đó gửi request C. Kiểm tra response, status của job, số lần gọi tác vụ thật và metadata lỗi. Không cần một vòng lặp gửi hàng chục nghìn request để chứng minh rejection.

## 10. Shutdown, race condition và dọn dẹp test

**Tác vụ có thể hoàn thành ngay lúc caller yêu cầu hủy.** Nếu không chặn thời điểm bắt đầu bằng latch, `cancel(true)` có thể trả false vì job đã xong. Đó có thể là hành vi hợp lệ, không hẳn lỗi executor. Các test cần đặt trạng thái “đã bắt đầu nhưng đang chờ” trước khi gửi cancel, hoặc chấp nhận cả hai nhánh nếu chủ đích là kiểm tra race.

**Không đồng nhất `shutdown()` với `shutdownNow()`:** trong JDK, `shutdown()` ngừng nhận việc mới và cho công việc đã nộp hoàn tất theo chính sách executor; `shutdownNow()` cố interrupt các task đang chạy và trả về danh sách task chưa được bắt đầu, nhưng không bảo đảm tất cả task sẽ dừng. `ThreadPoolTaskExecutor` có thêm lựa chọn lifecycle/shutdown của Spring, vì vậy test shutdown phải xác nhận cấu hình tương ứng thay vì mặc định mọi task queued đều hoàn tất.

Test lifecycle nên kiểm tra:

1. Nhả mọi latch do chính test tạo trước khi chờ executor kết thúc.
2. `shutdown()` trong `finally` hoặc đóng Spring context bằng `try-with-resources`.
3. Chỉ dùng `awaitTermination(timeout, unit)` khi cần chứng minh worker đã dừng; luôn đặt timeout hữu hạn.
4. Nếu test thực sự cần dừng tác vụ vô hạn, kiểm tra cooperation với interrupt và cleanup, tránh tạo thread không thể dừng.

Với các thao tác có side effect, một job bị timeout/hủy vẫn có thể đã gửi email, ghi dữ liệu hoặc gọi dịch vụ ngoài. Test nên xác minh tính idempotent/rollback/compensation riêng, không giả định Future quyết định số phận các side effect đã phát sinh.

## 11. Metrics và log để chứng minh sự cố

Đối với kiểm thử tích hợp, kết hợp assertion trong code với tín hiệu quan sát:

| Tín hiệu | Tác dụng |
| --- | --- |
| `executor.getActiveCount()` | Số worker đang active tại thời điểm đo |
| `executor.getPoolSize()` | Số worker hiện được tạo |
| `executor.getQueueSize()` | Số tác vụ đang chờ trong queue ở thời điểm đo |
| Counter `task_rejected_total` | Phát hiện đường submit bị từ chối |
| Timer `queue_wait`, `execution_time`, `end_to_end` | Phân biệt độ trễ vì xếp hàng với độ trễ thực thi |
| Log `jobId`, `traceId`, `thread`, `outcome` | Theo dõi accept → start → complete/reject/timeout/cancel |

Các số đo về `activeCount`, `queueSize` là **ảnh chụp tức thời**, có thể thay đổi ngay sau khi đọc; đừng xây test bất biến tuyệt đối trên giá trị tại thời điểm không bị khóa. Trong fixture ở mục 4, latch giữ A để cho phép assertion `queueSize==1` một cách xác định.

Khi test cancellation, nếu có metric “cancel requested” và “cancel acknowledged”, nên tách chúng; số lần yêu cầu hủy không nói được bao nhiêu worker thực sự dừng. Với caller timeout, phân biệt **số timeout** và **số job nền hoàn thành sau timeout**.

## 12. Những lỗi phổ biến và cách tránh

| Lỗi | Hệ quả | Cách kiểm thử đúng |
| --- | --- | --- |
| Submit nhiều việc rồi `sleep(100)` | Test pass/fail tùy tốc độ máy CI | Dùng latch để khóa worker và xác nhận queue đầy |
| Test overload với queue không giới hạn | Có thể không đạt rejection | Giới hạn `core`, `max`, `queueCapacity` rõ ràng |
| Dùng `assertThrows(RejectedExecutionException)` nhưng quên lớp bọc Spring | Bỏ sót contract public của `TaskExecutor` | Khẳng định `TaskRejectedException` ở biên Spring |
| Giả định `CallerRunsPolicy` sẽ throw | Test sai chính sách, bỏ qua latency caller | Kiểm tra tác vụ chạy trên thread nộp |
| Gọi `get(timeout)` và mặc định task đã bị hủy | Job vẫn có thể chạy và tạo side effect | Kiểm tra `isDone/isCancelled`, cleanup và chính sách hủy |
| Gọi `CompletableFuture.cancel(true)` rồi chờ interrupt | Stage bị hủy nhưng supplier có thể vẫn chạy | Dùng handle producer hoặc cancellation token nếu cần |
| Chỉ test `isCancelled()` | Không biết worker có xử lý interrupt | Thêm latch báo đã quan sát interrupt |
| Không nhả latch khi assertion thất bại | Test treo, làm bẩn suite | Dùng `finally` và timeout hữu hạn |
| Test `@Async` bằng `this.method()` | Đường proxy bị bỏ qua, không kiểm đúng executor | Gọi qua bean lấy từ Spring context |
| Đếm `getQueueSize()` như số liệu giao dịch chính xác | Có race khi task được dequeue | Chỉ khẳng định trong trạng thái được latch khóa |
| Coi timeout API là rollback nghiệp vụ | Có thể đã ghi DB hoặc gửi sự kiện | Test side effect, idempotency và compensation |

## 13. Checklist tự đánh giá

Trước khi kết luận tính năng xử lý đúng khi quá tải, tự trả lời:

- Có test nộp **A đang chạy, B đang chờ, C bị từ chối** bằng latch không?
- Với `AbortPolicy`, exception có đúng ở biên Spring không? Với `CallerRunsPolicy`, ai thực thi C?
- Timeout của `Future.get(...)` có để job chạy tiếp không? `orTimeout` có khiến stage hoàn thành ngoại lệ đúng như mong đợi không?
- `cancel(true)` trên `Future` có khiến thân job quan sát interrupt không? `CompletableFuture.cancel(true)` có bị hiểu nhầm là kill thread không?
- Có thử hủy B *khi còn trong queue* và chứng minh B không tạo side effect không?
- Có xác minh lời gọi `@Async` qua Spring proxy đúng executor không?
- Có cleanup chắc chắn khi test fail, không để thread hoặc Spring context rò rỉ không?
- Có contract riêng cho HTTP response, telemetry, deadline và tác vụ hoàn thành muộn không?

**Đạt mục tiêu bài học** khi bạn giải thích và chứng minh bằng test được ba tầng khác nhau: **executor có nhận việc hay không**, **caller có còn chờ kết quả hay không**, và **worker đã thật sự kết thúc công việc hay chưa**.

## Tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — TaskRejectedException API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/TaskRejectedException.html)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-008">Kiểm tra truyền rồi dọn ngữ cảnh qua nhiều tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra truyền rồi dọn ngữ cảnh qua nhiều tác vụ trong Spring Framework

## 1. Mục tiêu kiểm thử

Trong ứng dụng Spring, một yêu cầu có thể đặt `traceId`, `requestId`, ngữ cảnh ghi log (MDC) hoặc thông tin xác thực vào biến gắn với luồng (`ThreadLocal`). Khi chuyển xử lý sang `ThreadPoolTaskExecutor` hay phương thức `@Async`, tác vụ chạy trên **luồng worker khác** và các luồng trong pool được **tái sử dụng**. Vì vậy, kiểm thử phải chứng minh được **hai thuộc tính độc lập**:

1. **Truyền đúng ngữ cảnh (propagation):** tác vụ A nhận ngữ cảnh A, tác vụ B nhận ngữ cảnh B, tác vụ không có ngữ cảnh phải nhận trạng thái rỗng. Giá trị phải được chụp **khi gửi tác vụ**, không lấy từ một yêu cầu đến sau.
2. **Dọn/khôi phục đúng ngữ cảnh (cleanup/restoration):** sau khi tác vụ kết thúc, kể cả khi ném exception hoặc sửa ngữ cảnh trong lúc chạy, worker không còn giữ dữ liệu của tác vụ vừa xong. Nếu worker vốn đang có ngữ cảnh hợp lệ bên ngoài phạm vi đang chạy, phải khôi phục trạng thái cũ thay vì xóa tùy tiện.

Đề bài không chỉ là kiểm tra log có in đúng ID. Bài test cần chạy **nhiều tác vụ liên tiếp trên cùng một worker**, có tác vụ không mang ngữ cảnh, trường hợp lỗi, và thêm tình huống nhiều bên gửi tác vụ đồng thời.

Ví dụ trạng thái mong đợi:

| Bước | Ngữ cảnh trên luồng gửi | Worker nhìn thấy **trong** tác vụ | Worker sau khi tác vụ hoàn tất |
| --- | --- | --- | --- |
| Gửi A | `traceId=A` | `A` | Rỗng (nếu ban đầu rỗng) |
| Gửi B trên cùng worker | `traceId=B` | `B` | Rỗng |
| Gửi tác vụ không có ID | `null` | `null` | Rỗng |
| Gửi C, tác vụ đổi ID rồi ném lỗi | `traceId=C` | `C` tại lúc vào tác vụ | Rỗng, dù có exception |
| Gửi D tiếp theo | `traceId=D` | `D` | Rỗng |

**Vì sao cần kiểm thử cleanup trực tiếp?** Nếu tác vụ tiếp theo luôn được decorator đặt ID mới trước khi chạy, bài test chỉ đọc ID **bên trong tác vụ kế tiếp** có thể vượt qua mặc dù worker vẫn lưu ID của tác vụ trước. Muốn phát hiện lỗi này, cần có phép kiểm tra trạng thái worker **sau** khi decorator kết thúc; phần JUnit bên dưới có một phép “raw probe” dành riêng cho kiểm thử.

## 2. Cơ chế: chụp ở lúc gửi, đặt và khôi phục ở lúc chạy

`ThreadLocal` không tự truyền giá trị giữa hai luồng độc lập. `InheritableThreadLocal` cũng không phải giải pháp đáng tin cho thread pool: giá trị được kế thừa ở thời điểm tạo thread con, trong khi worker đã tạo sẵn sẽ được dùng cho nhiều request khác nhau. Dùng nó để mang user/trace/request context có thể dẫn tới thiếu hoặc sai dữ liệu.

Spring cho phép cấu hình `ThreadPoolTaskExecutor.setTaskDecorator(TaskDecorator)`. Một decorator đúng phải:

1. Đọc và chụp ngữ cảnh ở **luồng gọi `execute/submit`**, ngay lúc `decorate` được gọi.
2. Trả về một `Runnable` mới; chỉ khi worker thực thi `run()` mới áp dụng ngữ cảnh đã chụp.
3. Ghi nhớ trạng thái trước đó của **worker**, đặt ngữ cảnh của tác vụ rồi gọi code gốc.
4. Trong `finally`, khôi phục trạng thái worker trước đó; với giá trị trước đó là `null` thì gọi `remove()`.
5. Xử lý được đầu vào “không có context”, tác vụ tự sửa context, và tác vụ lỗi.

Sơ đồ thời điểm:

~~~text
Luồng gửi:    set(A) -- submit(A) -- set(B) -- submit(B) -- clear()
                          |                        |
                      chụp A                  chụp B
                          v                        v
Worker 1:         đặt A -> chạy A -> dọn -> đặt B -> chạy B -> dọn
Worker 1:         [rỗng]              [rỗng]              [rỗng]
~~~

Nếu chỉ đặt context mà quên `finally`, worker có thể tiếp tục giữ ID cũ sau khi công việc xong. Nếu chỉ gọi `remove()` mà không giữ giá trị trước đó, mã chạy lồng nhau hoặc chính sách `CallerRunsPolicy` có thể làm mất context của luồng gọi. Quy tắc tổng quát là **đóng phạm vi và khôi phục ngữ cảnh trước đó**.

`TaskDecorator` bao quanh callback thực thi; callback đó đôi khi là `FutureTask`, không nhất thiết là `Runnable` do người dùng gửi. Vì vậy, với `submit`, exception bên trong công việc thường được chứa trong `Future` và kiểm thử phải dùng `Future.get()` để thấy `ExecutionException`. Khối `finally` của decorator vẫn chạy khi callback đã xử lý exception nội bộ.

## 3. Ví dụ Spring: context riêng và một TaskDecorator có cleanup

Các tệp dưới đây là **mã của một ứng dụng có Spring Framework**, yêu cầu Spring Context và, ở phần test, JUnit Jupiter. Các mã này không phải chương trình Java SE chạy độc lập. Sử dụng Java 17+.

Tệp `src/main/java/demo/context/TraceContext.java`:

~~~java
package demo.context;

public final class TraceContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() { }

    public static String get() {
        return CURRENT.get();
    }

    public static void set(String value) {
        if (value == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(value);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
~~~

`TraceContext` chỉ minh họa một chuỗi ID không đổi. Đối với dữ liệu phức tạp, snapshot phải cân nhắc sao chép/immutable; chụp một tham chiếu đến đối tượng mutable rồi chia sẻ cho nhiều worker **không bảo đảm cách ly**.

Tệp `src/main/java/demo/context/TraceTaskDecorator.java`:

~~~java
package demo.context;

import org.springframework.core.task.TaskDecorator;

public final class TraceTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable delegate) {
        // Đọc tại luồng gửi tác vụ; giá trị không thay đổi theo request sau.
        String submittedTrace = TraceContext.get();

        return () -> {
            // Đọc ở luồng thực thi, có thể khác luồng gửi.
            String previousOnWorker = TraceContext.get();
            try {
                TraceContext.set(submittedTrace);  // null -> remove()
                delegate.run();
            } finally {
                // Chạy cả khi delegate ném exception hoặc sửa TraceContext.
                TraceContext.set(previousOnWorker);
            }
        };
    }
}
~~~

Điểm cốt lõi: `TraceContext.set(null)` thực hiện `ThreadLocal.remove()`, và `finally` luôn khôi phục trạng thái worker. Decorator này chỉ truyền **một** `traceId` minh họa; nó không tự động truyền MDC, `SecurityContextHolder` hay toàn bộ `RequestContextHolder`.

Tệp `src/main/java/demo/context/AsyncConfig.java` để dùng executor trong Spring:

~~~java
package demo.context;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("traceExecutor")
    public ThreadPoolTaskExecutor traceExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix("trace-");
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(50);
        pool.setTaskDecorator(new TraceTaskDecorator());
        return pool; // Spring khởi tạo và dừng bean theo vòng đời context.
    }
}
~~~

Trong một Spring bean khác, một phương thức `@Async("traceExecutor")` sẽ dùng pool đã cấu hình. Khi áp dụng thực tế, request filter/interceptor hoặc caller phải đặt `TraceContext` ở luồng gửi và tự `clear()` tại ranh giới request bằng `try/finally`; worker cleanup **không thay thế** cleanup ở luồng gửi.

Lưu ý: `@Async` hoạt động qua Spring proxy. Việc gọi một phương thức `@Async` từ phương thức khác **trên chính cùng instance** (self-invocation) có thể không đi qua proxy nên tác vụ không chạy bất đồng bộ. Kiểm thử integration cần gọi qua bean do Spring quản lý và xác minh thread thực thi khác thread gọi.

## 4. JUnit 5: kiểm tra tái sử dụng worker, exception và dọn thực sự

Tệp `src/test/java/demo/context/TraceTaskDecoratorTest.java`:

~~~java
package demo.context;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TraceTaskDecoratorTest {
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("context-test-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setTaskDecorator(new TraceTaskDecorator());
        executor.initialize(); // Cần khi tự tạo executor, không qua Spring bean.
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear(); // Dọn ThreadLocal của luồng chạy JUnit.
        executor.shutdown();
    }

    @Test
    void reusesSameWorkerAndRemovesContextEvenAfterFailure() throws Exception {
        ThreadPoolExecutor rawWorkerPool = executor.getThreadPoolExecutor();
        String callerThread = Thread.currentThread().getName();

        TraceContext.set("request-A");
        Future<String> first = executor.submit(() -> {
            assertEquals("request-A", TraceContext.get());
            TraceContext.set("dirty-inside-A");
            return Thread.currentThread().getName();
        });
        String workerThread = first.get(5, SECONDS);
        assertNotEquals(callerThread, workerThread);

        // Bỏ qua TaskDecorator CHỈ TRONG TEST để nhìn trạng thái thực
        // của worker sau khi A đã kết thúc.
        assertNull(rawWorkerPool.submit(
                (Callable<String>) TraceContext::get).get(5, SECONDS));

        TraceContext.set("request-B");
        Future<String> second = executor.submit(() -> {
            assertEquals("request-B", TraceContext.get());
            return Thread.currentThread().getName();
        });
        assertEquals(workerThread, second.get(5, SECONDS));
        assertEquals("request-B", TraceContext.get()); // Caller không bị đổi.

        TraceContext.clear();
        assertNull(executor.submit(
                (Callable<String>) TraceContext::get).get(5, SECONDS));

        TraceContext.set("request-C");
        Future<?> failed = executor.submit((Runnable) () -> {
            assertEquals("request-C", TraceContext.get());
            TraceContext.set("dirty-before-failure");
            throw new IllegalStateException("simulated failure");
        });

        ExecutionException exception = assertThrows(
                ExecutionException.class, () -> failed.get(5, SECONDS));
        assertInstanceOf(IllegalStateException.class, exception.getCause());

        // Đây mới là phép đo trực tiếp xác nhận finally đã dọn.
        assertNull(rawWorkerPool.submit(
                (Callable<String>) TraceContext::get).get(5, SECONDS));
        TraceContext.clear();
        assertNull(executor.submit(
                (Callable<String>) TraceContext::get).get(5, SECONDS));
    }

    @Test
    void capturesContextWhenTaskIsSubmittedNotWhenItRuns() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> blocker = executor.submit((Runnable) () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            assertTrue(started.await(5, SECONDS));
            TraceContext.set("at-submission");
            Future<String> queued = executor.submit(
                    (Callable<String>) TraceContext::get);
            TraceContext.set("changed-while-queued");

            release.countDown();
            assertEquals("at-submission", queued.get(5, SECONDS));
            assertEquals("changed-while-queued", TraceContext.get());
            blocker.get(5, SECONDS);
        } finally {
            release.countDown(); // Tránh kẹt pool nếu assertion thất bại.
        }
    }

    @Test
    void cleansAfterManySequentialTasks() throws Exception {
        ThreadPoolExecutor rawWorkerPool = executor.getThreadPoolExecutor();
        for (int i = 0; i < 50; i++) {
            String expected = i % 5 == 0 ? null : "job-" + i;
            TraceContext.set(expected);

            Future<String> observed = executor.submit(
                    (Callable<String>) TraceContext::get);
            assertEquals(expected, observed.get(5, SECONDS));

            // Không chỉ kiểm tra tác vụ tiếp theo đã được decorator đặt lại.
            assertNull(rawWorkerPool.submit(
                    (Callable<String>) TraceContext::get).get(5, SECONDS),
                    "Worker retained a context after job " + i);
        }
    }

    @Test
    void isolatesTwoIndependentSubmittingThreads() throws Exception {
        ExecutorService submitters = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        try {
            Future<String> alice = submitters.submit(() -> {
                TraceContext.set("alice");
                try {
                    ready.countDown();
                    go.await();
                    return executor.submit(
                            (Callable<String>) TraceContext::get)
                            .get(5, SECONDS);
                } finally {
                    TraceContext.clear();
                }
            });

            Future<String> bob = submitters.submit(() -> {
                TraceContext.set("bob");
                try {
                    ready.countDown();
                    go.await();
                    return executor.submit(
                            (Callable<String>) TraceContext::get)
                            .get(5, SECONDS);
                } finally {
                    TraceContext.clear();
                }
            });

            assertTrue(ready.await(5, SECONDS));
            go.countDown();
            assertEquals("alice", alice.get(10, SECONDS));
            assertEquals("bob", bob.get(10, SECONDS));
            assertNull(executor.getThreadPoolExecutor().submit(
                    (Callable<String>) TraceContext::get).get(5, SECONDS));
        } finally {
            go.countDown();
            submitters.shutdownNow();
        }
    }
}
~~~

### Vì sao các phép thử này bắt được lỗi thật?

- `corePoolSize=maxPoolSize=1` ép các tác vụ liên tiếp dùng lại **một** worker. Nếu mỗi tác vụ chạy trên thread mới, một lỗi rò rỉ `ThreadLocal` có thể bị che khuất.
- Các phép `rawWorkerPool.submit(...)` chủ ý gọi thẳng `ThreadPoolExecutor` bên dưới để **bỏ qua decorator trong môi trường test**. Nhờ vậy, chúng phát hiện được giá trị còn sót sau tác vụ A hoặc tác vụ lỗi. Đừng dùng lối gọi thẳng này trong nghiệp vụ production nếu cần propagation.
- `Future.get(timeout)` buộc test đợi việc hoàn tất và kiểm tra exception. Tránh `Thread.sleep(...)` với thời gian ước đoán, vì làm test chập chờn.
- `CountDownLatch` giữ một task trong worker để test rằng task thứ hai đã chụp context **lúc submit**, ngay cả khi giá trị trên luồng gửi bị sửa trước lúc worker bắt đầu chạy task đó.
- 50 lượt giúp phát hiện contamination qua chuỗi việc A/B/rỗng thay đổi liên tục; test 2 luồng gửi kiểm tra hai caller không làm lẫn giá trị với nhau.
- `@AfterEach` dọn context của chính luồng chạy JUnit và dừng executor, ngăn test này ảnh hưởng test khác.

Nếu muốn kiểm tra **hai worker chạy thật sự đồng thời**, khởi tạo pool với `corePoolSize=maxPoolSize=2`, dùng `CyclicBarrier(2)` bên trong hai task để buộc chúng gặp nhau trước khi so sánh từng ID, rồi thực hiện hai raw probe ở cả hai worker. Không dùng barrier chờ hai task trên pool chỉ có một worker, vì sẽ gây deadlock.

Để chạy các test trong dự án Maven đã có Spring Context và JUnit Jupiter:

~~~bash
mvn -Dtest=TraceTaskDecoratorTest test
~~~

JUnit thành công thường không in giá trị context; tiêu chí là các assertion vượt qua. Lệnh trên phụ thuộc cấu hình Maven Surefire/JUnit hiện có của dự án, nên không thể thay cho kiểm thử integration `@Async` trong ứng dụng thực.

## 5. Bài tập Java 17 độc lập, có stdout xác định

Đây là mô phỏng thuần Java `ExecutorService` của nguyên tắc chụp–đặt–khôi phục. Không có dependency Spring hay JUnit. Mục đích là có thể tự chạy và thấy kết quả trước khi đưa cùng cơ chế vào `TaskDecorator`.

Lưu tệp `ContextIsolationDemo.java`:

~~~java
import java.util.concurrent.*;

public class ContextIsolationDemo {
    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    private static <T> Callable<T> captured(Callable<T> action) {
        String submitted = TRACE.get();
        return () -> {
            String previous = TRACE.get();
            try {
                if (submitted == null) TRACE.remove();
                else TRACE.set(submitted);
                return action.call();
            } finally {
                if (previous == null) TRACE.remove();
                else TRACE.set(previous);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            TRACE.set("A");
            String a = pool.submit(captured(() -> {
                String seen = TRACE.get();
                TRACE.set("changed-inside-A");
                return seen;
            })).get();

            TRACE.set("B");
            String b = pool.submit(captured(TRACE::get)).get();

            TRACE.remove();
            String empty = pool.submit(captured(TRACE::get)).get();

            TRACE.set("C");
            Future<?> failed = pool.submit(captured(() -> {
                TRACE.set("changed-before-error");
                throw new IllegalStateException("boom");
            }));

            String errorType = "none";
            try {
                failed.get();
            } catch (ExecutionException e) {
                errorType = e.getCause().getClass().getSimpleName();
            }

            TRACE.remove();
            String afterError = pool.submit(captured(TRACE::get)).get();
            System.out.println(a + " | " + b + " | " + empty
                    + " | " + errorType + " | " + afterError);
        } finally {
            TRACE.remove();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy với Java 17+:

~~~bash
javac ContextIsolationDemo.java
java ContextIsolationDemo
~~~

Đầu ra:

~~~text
A | B | null | IllegalStateException | null
~~~

Một worker duy nhất nhận A rồi B rồi tác vụ không có context, sau đó chạy tác vụ lỗi và nhận tác vụ cuối. Mỗi task nhận snapshot của chính nó; `finally` khôi phục ThreadLocal ở worker. Đây là ví dụ kiểm chứng **nguyên tắc**; hãy dùng test JUnit phía trên để kiểm tra chính `ThreadPoolTaskExecutor` của Spring.

## 6. Khi ngữ cảnh là MDC, SecurityContext hoặc Micrometer

### 6.1. MDC để nối log theo request

`org.slf4j.MDC` lưu bản đồ chuỗi theo ngữ cảnh của hệ thống logging. Nếu phải tự viết một decorator MDC, cần **sao chép map ở luồng gửi**, đặt trên worker và khôi phục trong `finally`. Đoạn sau là một **phương thức minh họa** trong lớp cấu hình (yêu cầu SLF4J và `TaskDecorator`), không phải file Java chạy độc lập:

~~~java
static TaskDecorator mdcDecorator() {
    return task -> {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (captured == null) MDC.clear();
                else MDC.setContextMap(captured);
                task.run();
            } finally {
                if (previous == null) MDC.clear();
                else MDC.setContextMap(previous);
            }
        };
    };
}
~~~

Cần import `java.util.Map`, `org.slf4j.MDC` và `org.springframework.core.task.TaskDecorator`. Không giả định backend logging luôn hỗ trợ MDC; kiểm tra cấu hình Logback/Log4j hoặc adapter thực tế. Trong bài test, chụp một map có `requestId=A`, chạy tác vụ A; lặp lại với `B`, thử một task không có MDC, rồi task ném exception. Sau mỗi task dùng raw probe đọc `MDC.getCopyOfContextMap()` ngay trên worker để xác nhận map đã trở về trạng thái ban đầu. Dùng ID thử nghiệm, không đưa thông tin nhạy cảm vào log.

### 6.2. Spring Security

`SecurityContextHolder` thường dựa trên ngữ cảnh gắn với thread. Nếu tác vụ async cần danh tính người dùng, Spring Security cung cấp `DelegatingSecurityContextAsyncTaskExecutor` (và các wrapper tương tự) để gói công việc, đặt rồi dọn `SecurityContext`. Không tự giả định decorator chỉ truyền `traceId` đã truyền cả thông tin xác thực.

Bài test security cần tạo hai `Authentication` khác nhau (ví dụ `alice`, `bob`), submit lần lượt qua executor bảo mật, xác minh principal trong từng task, rồi gửi task không có authentication và kiểm tra không nhìn thấy principal cũ. Dùng `SecurityContextHolder.clearContext()` trong `finally` ở luồng gửi và `Future.get()` để đợi. Nếu muốn đảm bảo worker trống sau wrapper, kiểm tra thêm bằng raw probe dành riêng cho test. Không chia sẻ và sửa cùng một đối tượng `SecurityContext` mutable ở nhiều worker; điều đó có thể tạo ra data race dù wrapper có cleanup.

Với tác vụ chạy sau khi HTTP request kết thúc, nên truyền ID hoặc dữ liệu nghiệp vụ cần thiết một cách tường minh; tránh giữ trực tiếp toàn bộ servlet request, session hoặc principal có vòng đời không còn phù hợp.

### 6.3. Micrometer Context Propagation và decorator có sẵn

Từ Spring Framework **6.1**, `ContextPropagatingTaskDecorator` hỗ trợ chụp và khôi phục context qua Micrometer `ContextSnapshot`. Nó hữu ích cho observation/tracing và các ngữ cảnh có `ThreadLocalAccessor` được đăng ký. **Không có nghĩa mọi `ThreadLocal` tự động được chụp.** Với `TraceContext` riêng trong ví dụ, cần đăng ký accessor, như đoạn cấu hình minh họa:

~~~java
ContextRegistry registry = new ContextRegistry();
registry.registerThreadLocalAccessor(new ThreadLocalAccessor<String>() {
    @Override public Object key() { return "traceId"; }
    @Override public String getValue() { return TraceContext.get(); }
    @Override public void setValue(String value) { TraceContext.set(value); }
    @Override public void setValue() { TraceContext.clear(); }
});
ContextSnapshotFactory factory =
        ContextSnapshotFactory.builder().contextRegistry(registry).build();

executor.setTaskDecorator(new ContextPropagatingTaskDecorator(factory));
~~~

Đoạn trên giả định có biến `executor` kiểu `ThreadPoolTaskExecutor` đang cấu hình, có dependency `micrometer-context-propagation` phù hợp phiên bản Spring, và các import cho `ContextRegistry`, `ThreadLocalAccessor`, `ContextSnapshotFactory`, `ContextPropagatingTaskDecorator`, `TraceContext`. Đây là **phương án thay thế** cho decorator tự viết đối với cùng `traceId`; không chồng hai cơ chế truyền cùng một trường mà chưa xác định thứ tự, phạm vi và cách dọn.

Kiểm thử Micrometer vẫn cần các trường hợp A/B/rỗng/exception/worker reuse. Khi `ContextSnapshot.Scope` được đóng, giá trị trước khi vào scope được khôi phục. Quan sát kết quả trong task thôi chưa đủ để phát hiện một accessor không dọn đúng. Đối với code Reactor, phải xét cả `Reactor Context`, vì ngữ cảnh reactive không đồng nhất với `ThreadLocal` của executor.

## 7. Những trường hợp dễ khiến test “xanh giả”

| Sai sót | Tại sao chưa chứng minh đúng | Cách kiểm thử tốt hơn |
| --- | --- | --- |
| Dùng `SyncTaskExecutor` hoặc gọi thẳng phương thức thay vì đi qua proxy `@Async` | Tác vụ có thể chạy luôn trên luồng gửi, context vốn đã có sẵn. | Dùng `ThreadPoolTaskExecutor` thật; kiểm tra thread của worker khác caller. |
| Chỉ chạy một tác vụ A | Không quan sát việc tái sử dụng và contamination. | Chạy A → B → rỗng → lỗi → rỗng trên worker cố định. |
| Chỉ assert giá trị ở đầu task B | Decorator có thể ghi đè ID cũ, che lỗi quên cleanup. | Sau mỗi task, dùng raw probe đọc trực tiếp ThreadLocal ở worker. |
| Đo bằng `Thread.sleep` | Phụ thuộc tốc độ máy và lịch chạy thread. | Dùng `CountDownLatch`, `Future.get(timeout)` và các assertion. |
| Bỏ `Future.get()` khi test `submit` lỗi | Exception bị giữ trong `FutureTask`. | Dùng `assertThrows(ExecutionException.class, future::get)` và kiểm tra cause. |
| Kiểm tra theo thứ tự xuất hiện của log khi có nhiều worker | Thứ tự thực thi không bảo đảm theo thứ tự submit. | Đối chiếu bằng ID từng future; dùng barrier nếu cần đồng thời. |
| Chỉ dọn ở luồng worker, quên dọn trong HTTP filter hoặc test JUnit | Context của luồng gửi còn sót và bị capture vào request kế tiếp. | `try/finally` ở mọi ranh giới tạo context, gồm caller. |
| Copy một đối tượng mutable thay vì snapshot giá trị | Hai task có thể chia sẻ tham chiếu đang thay đổi. | Dùng giá trị immutable hoặc bản sao độc lập, kiểm thử thay đổi giữa lúc submit và run. |
| Dùng `InheritableThreadLocal` trong pool | Worker tồn tại lâu, giá trị có thể thừa hưởng ở lúc tạo thread và lỗi thời. | Propagation theo từng task bằng decorator/snapshot. |
| Trộn pool có decorator và lời gọi trực tiếp vào underlying executor trong production | Những task bypass decorator có thể để lại context trên cùng worker. | Bắt mọi đường submit đi qua executor được quản lý; raw probe chỉ dùng trong test. |

Một phương án kiểm thử tối thiểu đáng tin phải có ít nhất: **truyền đúng A/B**, **tác vụ rỗng không nhận dữ liệu cũ**, **cleanup sau thành công và exception**, **snapshot tại lúc submit**, **tái sử dụng cùng worker**, và **cô lập các luồng gửi khác nhau**.

## 8. Checklist khi áp dụng vào ứng dụng thực

1. Xác định rõ những context nào **được phép** truyền: correlation ID, MDC, observation, authentication; không gom toàn bộ request context một cách tùy tiện.
2. Đặt context ở điểm vào (filter/interceptor/caller); dọn ở điểm ra bằng `finally`.
3. Chọn đúng executor và bảo đảm `@Async("...")` dùng bean có decorator; xác minh proxy được kích hoạt.
4. Với mỗi phần tử context, xác định chính sách snapshot, dọn khi `null`, khôi phục previous, và cách xử lý exception. Nếu dùng Micrometer, kiểm tra accessor đã đăng ký.
5. Tạo unit test có pool **một worker** để bắt leak trên cùng thread; thêm test nhiều worker để kiểm tra isolation đồng thời nếu ứng dụng có nhiều luồng.
6. Test both success/failure, no-context, mutation inside task, 50+ lượt liên tiếp, task đang xếp hàng, và cleanup ở luồng gọi.
7. Phân biệt task **đã chạy** với task bị reject/cancel **trước lúc chạy**. Với task chưa chạy, wrapper `run()` và `finally` không được gọi; test không được kỳ vọng cleanup trên worker chưa thực thi. Đừng giữ snapshot tham chiếu đến cả request object trong queue kéo dài.
8. Đảm bảo tài nguyên test (`ThreadPoolTaskExecutor`, latch, producer pool) được đóng; không để test dở dang làm ảnh hưởng các test sau.
9. Với production, quan sát `traceId` trong log/traces cho từng tác vụ, nhưng không coi log đúng là bằng chứng đã cleanup. Dùng test thực thi để xác minh trạng thái worker sau nhiệm vụ.

## Tài liệu đối chiếu

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring `ThreadPoolTaskExecutor` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring `ContextPropagatingTaskDecorator` Javadoc (6.2.x)](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html)
- [Spring Security — Concurrency Support](https://docs.spring.io/spring-security/reference/servlet/integrations/concurrency.html)
- [Micrometer Context Propagation — Examples](https://docs.micrometer.io/context-propagation/reference/usage.html)
- [SLF4J `MDC` Javadoc](https://www.slf4j.org/apidocs/org/slf4j/MDC.html)
- [JDK 17 — `ThreadLocal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-009">Kiểm thử cron, múi giờ và lịch chạy với nguồn thời gian có thể kiểm soát</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử cron, múi giờ và lịch chạy với nguồn thời gian có thể kiểm soát

## 1. Mục tiêu và ranh giới bài toán

Một tác vụ trong Spring Framework có thể được kích hoạt bởi `@Scheduled(cron = ...)`, `TaskScheduler.schedule(Runnable, Trigger)` hoặc một lịch do ứng dụng lưu trong cơ sở dữ liệu. Những câu hỏi kiểm thử quan trọng là:

1. Biểu thức cron có hợp lệ và tạo **đúng những thời điểm chạy tiếp theo** không?
2. “09:00 mỗi ngày làm việc” là 09:00 ở **múi giờ nào**? Khi triển khai trên máy chủ dùng UTC, lịch có còn đúng không?
3. Ngày, tháng, cuối tuần, năm nhuận và các lần đổi giờ mùa hè (DST) có làm lịch chạy thiếu/thừa không?
4. Khi job chạy lâu hơn chu kỳ, scheduler có bỏ qua lượt, chạy bù hay chạy trùng?
5. Logic bên trong job dùng “hôm nay”, “đến hạn” hoặc “quá hạn” có được kiểm thử **không phải chờ đồng hồ thật** không?

Mục tiêu là kiểm thử bằng **thời điểm cố định và kết quả xác định**, không dùng `Thread.sleep(...)` để chờ đến giờ thật. Phải phân biệt hai phần:

- **Quyết định lúc nào chạy:** biểu thức cron + múi giờ + `Trigger`/`TaskScheduler` và trạng thái các lượt trước.
- **Xử lý khi được gọi:** nghiệp vụ dùng `Clock` được tiêm vào để đọc “hiện tại”; có thể kiểm thử trực tiếp bằng `Clock.fixed(...)` hoặc `Clock.offset(...)`.

Tiêm `Clock` vào service **không tự động thay thế đồng hồ của `@Scheduled`**. Khi kiểm thử thời điểm phát lịch, gọi `CronExpression.next(...)` hoặc `CronTrigger.nextExecution(...)` với thời gian đầu vào kiểm soát được. Với kiểm thử đăng ký scheduler thực tế, cần cấu hình `TaskScheduler`/harness riêng và kiểm tra ranh giới của framework.

Ví dụ bên dưới hướng tới **Java 17+, Spring Framework 6.2.x, Spring Boot 3.x tương thích, JUnit Jupiter 5**. Một số factory của `CronTrigger` như `forFixedExecution` có từ Spring Framework **6.1.3**; nếu dự án dùng phiên bản cũ hơn, cần chọn API tương ứng.

## 2. Hiểu đúng cú pháp cron và khái niệm múi giờ

Spring dùng **sáu trường**, khác cú pháp crontab Unix phổ biến có năm trường:

```text
giây  phút  giờ  ngày-trong-tháng  tháng  ngày-trong-tuần
  0    0    9         *            *       MON-FRI
```

Một số lịch hợp lệ:

| Spring cron | Ý nghĩa |
| --- | --- |
| `0 0 9 * * MON-FRI` | 09:00:00 vào thứ Hai–thứ Sáu |
| `0 */15 * * * *` | Giây 0 mỗi 15 phút |
| `0 30 2 * * *` | 02:30:00 hằng ngày ở vùng giờ được chọn |
| `0 0 0 1 * *` | 00:00:00 ngày đầu mỗi tháng |
| `@daily` | Macro cho `0 0 0 * * *` |

`CronExpression.parse(...)` kiểm tra cú pháp và `next(Temporal)` tính mốc **sau** thời điểm đầu vào. Với trường thứ trong tuần, Spring theo quy ước cron **0 hoặc 7 là Chủ nhật**; đừng sao chép máy móc mã số ngày trong tuần của Quartz. Phân biệt “cron hợp lệ về cú pháp” và “cron đạt kỳ vọng nghiệp vụ”: `0 0 9 * * *` hợp lệ nhưng vẫn chạy cuối tuần.

Cron biểu diễn các mốc theo **lịch địa phương**, không phải một khoảng `Duration` cố định giữa hai lần thực thi. Cùng biểu thức `0 0 9 * * *` có thể ứng với các `Instant` khác nhau tùy `ZoneId`:

- `Asia/Ho_Chi_Minh`: 09:00 ngày 29/10/2026 là **2026-10-29T02:00:00Z**.
- `UTC`: 09:00 ngày 29/10/2026 là **2026-10-29T09:00:00Z**.

Cấu hình múi giờ rõ ràng cho từng job:

```java
@Scheduled(cron = "${report.cron}", zone = "${report.zone}")
public void fire() {
    reportService.runForCurrentBusinessDate();
}
```

```properties
report.cron=0 0 9 * * MON-FRI
report.zone=Asia/Ho_Chi_Minh
```

`zone` xác định cách diễn giải cron. Nếu bỏ trống, lịch theo vùng giờ mặc định của scheduler; tùy cấu hình, thường chịu ảnh hưởng của môi trường. Ưu tiên **ID IANA** như `Asia/Ho_Chi_Minh`, `America/New_York`, `Europe/Berlin` thay vì tự cộng trừ một offset UTC cố định ở nơi có DST.

### Ba kiểu thời gian không thể hoán đổi

- `Instant` biểu diễn một thời điểm duy nhất trên trục UTC. Dùng để lưu mốc đã xảy ra, đối chiếu sự kiện và so sánh trước/sau.
- `LocalDateTime` là ngày/giờ trên lịch, **chưa có vùng giờ**. Nó không xác định duy nhất một `Instant`; khi DST đổi giờ, một `LocalDateTime` có thể không tồn tại hoặc xuất hiện hai lần.
- `ZonedDateTime` là giờ địa phương gắn với `ZoneId` và quy tắc chuyển đổi vùng giờ. Dùng để tính “09:00 theo giờ New York” và `CronExpression.next(...)`.

Trong kiểm thử, lưu giá trị mong đợi ở dạng `Instant` **kèm lý do tương ứng với giờ địa phương**, tránh so sánh chuỗi giờ phụ thuộc múi giờ của máy chạy CI.

## 3. Tách đầu kích hoạt lịch khỏi nghiệp vụ

Đặt `@Scheduled` ở lớp mỏng, đưa quyết định dựa vào thời gian vào service có thể tạo trực tiếp trong unit test. Cấu hình `Clock` thành bean ở môi trường thật; test tự truyền `Clock.fixed` hay `Clock.offset`.

Giả sử dự án Spring Boot có Maven và dependency `spring-boot-starter`, `spring-boot-starter-test` (test scope). Tạo tệp `src/main/java/example/scheduling/SchedulingApplication.java`:

```java
package example.scheduling;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@SpringBootApplication
@EnableScheduling
public class SchedulingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulingApplication.class, args);
    }

    @Bean
    Clock businessClock() {
        return Clock.systemUTC();
    }
}

@Component
class DailyReportSchedule {
    private final DailyReportService reportService;

    DailyReportSchedule(DailyReportService reportService) {
        this.reportService = reportService;
    }

    @Scheduled(cron = "${report.cron}", zone = "${report.zone}")
    public void fire() {
        reportService.runForCurrentBusinessDate();
    }
}

@Service
class DailyReportService {
    private final Clock clock;
    private final ZoneId businessZone;

    DailyReportService(Clock clock, @Value("${report.zone}") String zoneId) {
        this.clock = clock;
        this.businessZone = ZoneId.of(zoneId);
    }

    LocalDate currentBusinessDate() {
        return LocalDate.ofInstant(clock.instant(), businessZone);
    }

    void runForCurrentBusinessDate() {
        LocalDate businessDate = currentBusinessDate();
        System.out.println("report-date=" + businessDate);
    }
}
```

Và `src/main/resources/application.properties`:

```properties
report.cron=0 0 9 * * MON-FRI
report.zone=Asia/Ho_Chi_Minh
```

Chạy trong một dự án có Maven Wrapper:

```bash
./mvnw spring-boot:run
```

Trên Windows PowerShell dùng `./mvnw.cmd spring-boot:run`. Lệnh khởi động ứng dụng để lịch hoạt động; **không có cam kết một dòng stdout tức thì**, vì job chỉ được gọi khi đến giờ cron. Ví dụ in ra một ngày nghiệp vụ để minh họa `Clock`, chưa chứa I/O hay logic báo cáo thực tế.

Cách tách này cho phép unit test gọi `new DailyReportService(clock, "Asia/Ho_Chi_Minh")` trực tiếp mà không khởi tạo ứng dụng và không đợi scheduler. `Clock.systemUTC()` là nguồn thời gian thật cho nghiệp vụ; vùng giờ của lịch được khai báo riêng trong `@Scheduled`. Khi test bằng `Clock.fixed`, **chỉ logic trong service được đóng băng**; scheduler đang chạy trong Spring context vẫn sử dụng nguồn thời gian của nó.

## 4. Kiểm thử các thời điểm chạy bằng CronExpression và CronTrigger

Hai API hỗ trợ hai cấp độ test khác nhau:

- `CronExpression.parse(cron).next(zonedDateTime)`: kiểm tra **quy tắc lịch thuần túy** từ một điểm bắt đầu và vùng giờ. Dễ lập bảng kỳ vọng cho ngày trong tuần, cuối tháng, DST.
- `new CronTrigger(cron, zone).nextExecution(triggerContext)`: kiểm tra **quy tắc kích hoạt dựa trên trạng thái lịch**. `TriggerContext` cung cấp `getClock()`, `lastScheduledExecution()`, `lastActualExecution()`, `lastCompletion()`. `SimpleTriggerContext` cho phép đặt trạng thái mà không cần thread chạy thật.

Không dùng `Instant.now()` ngay trong assertion: hai lần gọi sẽ lấy hai thời điểm khác nhau và có thể vượt qua ranh giới phút/ngày. Hãy đặt một mốc cụ thể, ví dụ `2026-10-29T00:00:00Z`, rồi tính mốc tiếp theo.

### Bộ test chạy không cần sleep

Tạo `src/test/java/example/scheduling/ScheduleContractTest.java`, cùng package với các lớp ví dụ:

```java
package example.scheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleContractTest {
    private static final ZoneId HCM =
            ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void cronRequiresSixFieldsAndHonorsWeekdays() {
        assertTrue(CronExpression.isValidExpression(
                "0 0 9 * * MON-FRI"));
        assertFalse(CronExpression.isValidExpression(
                "0 9 * * MON-FRI")); // Chỉ có năm trường.

        ZonedDateTime seed = ZonedDateTime.of(
                2026, 10, 29, 8, 59, 59, 0, HCM);
        ZonedDateTime next = CronExpression
                .parse("0 0 9 * * MON-FRI")
                .next(seed);

        assertNotNull(next);
        assertEquals(Instant.parse("2026-10-29T02:00:00Z"),
                next.toInstant());
    }

    @Test
    void explicitZoneChangesTheNextInstant() {
        Clock frozen = Clock.fixed(
                Instant.parse("2026-10-29T00:00:00Z"),
                ZoneOffset.UTC);
        SimpleTriggerContext context =
                new SimpleTriggerContext(frozen);

        Instant hcmRun = new CronTrigger(
                "0 0 9 * * *", HCM).nextExecution(context);
        Instant utcRun = new CronTrigger(
                "0 0 9 * * *", ZoneOffset.UTC)
                .nextExecution(context);

        assertEquals(Instant.parse("2026-10-29T02:00:00Z"),
                hcmRun);
        assertEquals(Instant.parse("2026-10-29T09:00:00Z"),
                utcRun);
    }

    @Test
    void businessDateMovesAcrossMidnightWithoutSleep() {
        Clock before = Clock.fixed(
                Instant.parse("2026-10-29T16:59:59Z"),
                ZoneOffset.UTC);
        Clock after = Clock.offset(before, Duration.ofSeconds(2));

        DailyReportService serviceBefore =
                new DailyReportService(before, HCM.getId());
        DailyReportService serviceAfter =
                new DailyReportService(after, HCM.getId());

        assertEquals(LocalDate.of(2026, 10, 29),
                serviceBefore.currentBusinessDate());
        assertEquals(LocalDate.of(2026, 10, 30),
                serviceAfter.currentBusinessDate());
    }

    @Test
    void dstHasMissingAndRepeatedLocalTimes() {
        ZoneId newYork = ZoneId.of("America/New_York");

        LocalDateTime missing = LocalDateTime.of(
                2026, 3, 8, 2, 30);
        LocalDateTime repeated = LocalDateTime.of(
                2026, 11, 1, 1, 30);

        assertEquals(0, newYork.getRules()
                .getValidOffsets(missing).size());
        assertEquals(2, newYork.getRules()
                .getValidOffsets(repeated).size());
    }

    @Test
    void dailyWallClockScheduleIsNotAlways24HoursApart() {
        ZoneId newYork = ZoneId.of("America/New_York");
        ZonedDateTime march7 = ZonedDateTime.of(
                2026, 3, 7, 9, 0, 0, 0, newYork);

        ZonedDateTime march8 = CronExpression
                .parse("0 0 9 * * *")
                .next(march7);

        assertNotNull(march8);
        assertEquals(LocalDateTime.of(2026, 3, 8, 9, 0),
                march8.toLocalDateTime());
        assertEquals(Duration.ofHours(23),
                Duration.between(march7.toInstant(),
                        march8.toInstant()));
    }

    @Test
    void lenientCanSkipButFixedKeepsMissedSlots() {
        Clock frozen = Clock.fixed(
                Instant.parse("2026-10-29T10:00:00Z"),
                ZoneOffset.UTC);
        SimpleTriggerContext context =
                new SimpleTriggerContext(frozen);

        context.update(
                Instant.parse("2026-10-29T09:00:00Z"),
                Instant.parse("2026-10-29T09:00:03Z"),
                Instant.parse("2026-10-29T09:03:00Z"));

        Instant lenient = CronTrigger
                .forLenientExecution("0 * * * * *")
                .nextExecution(context);
        Instant fixed = CronTrigger
                .forFixedExecution("0 * * * * *")
                .nextExecution(context);

        assertEquals(Instant.parse("2026-10-29T09:04:00Z"),
                lenient);
        assertEquals(Instant.parse("2026-10-29T09:01:00Z"),
                fixed);
    }
}
```

Chạy:

```bash
./mvnw test
```

Trên Windows dùng `./mvnw.cmd test`. Kết quả mong đợi: các assertion đều thành công với dependency và phiên bản Spring đã nêu. Ví dụ kiểm tra ở ranh giới `16:59:59Z` và hai giây sau bởi lúc đó ngày tại Việt Nam đổi từ 29 sang 30/10/2026. Các test kiểm tra logic `java.time`, `CronExpression` và `CronTrigger`; **không** kiểm chứng rằng bộ lập lịch thực tế đã đăng ký annotation `@Scheduled` trong một Spring context.

### Đọc đúng test lenient và fixed execution

Ở tình huống cuối, cron chạy vào giây 0 mỗi phút. Lượt trước **dự kiến** lúc 09:00 nhưng hoàn thành lúc 09:03:

- `CronTrigger.forLenientExecution(...)` tính từ **lúc hoàn thành**, nên mốc kế tiếp là 09:04; các slot 09:01–09:03 không được bù.
- `CronTrigger.forFixedExecution(...)` tính từ **lúc đã lên lịch**, nên mốc kế tiếp là 09:01, có thể được chạy bù trễ rồi tiếp tục 09:02, 09:03.

Đây là hai ngữ nghĩa lập lịch khác nhau; quyết định dựa trên yêu cầu nghiệp vụ. Chạy bù chỉ hợp lý khi việc xử lý cũ còn ý nghĩa và có cơ chế chống trùng/kiểm soát tải. Lưu ý: `CronTrigger.forFixedExecution` không tự bảo đảm lưu trạng thái bền vững qua tiến trình bị dừng hay sự cố. Nếu cần chạy bù sau downtime, phải có cơ chế lưu mốc chạy, phục hồi và idempotency riêng.

## 5. Múi giờ và DST: kiểm thử điều gì?

`Asia/Ho_Chi_Minh` hiện không có chuyển giờ mùa hè, nhưng ứng dụng có thể chạy cho khách hàng ở khu vực có DST. **Không** suy luận từ việc test qua một ngày thường rằng cron xử lý mọi ngày trong năm đúng như ý muốn.

Ví dụ ở `America/New_York` trong năm 2026:

| Tình huống | Thời điểm địa phương | Kết quả từ quy tắc vùng giờ |
| --- | --- | --- |
| Chuyển sang DST, 08/03/2026 | 02:30 | Không tồn tại vì đồng hồ nhảy từ 02:00 lên 03:00 |
| Kết thúc DST, 01/11/2026 | 01:30 | Xuất hiện hai lần với hai offset UTC |
| Lịch 09:00 ngày 07/03 đến 09:00 ngày 08/03 | Hai mốc cùng giờ địa phương | Chỉ cách nhau **23 giờ** thực |
| Lịch 09:00 ngày 31/10 đến 09:00 ngày 01/11 | Hai mốc cùng giờ địa phương | Cách nhau **25 giờ** thực |

Test `ZoneRules.getValidOffsets(...)` ở trên xác minh **tính tồn tại/đa nghĩa của giờ địa phương**, không tự khẳng định cron sẽ gọi job bao nhiêu lần. Chính sách với giờ bị mất hoặc lặp còn phụ thuộc phiên bản và cách trigger lấy mốc tiếp theo; phải kiểm tra trực tiếp các mốc `next(...)` bằng đúng `ZoneId` và phiên bản framework dùng trong dự án.

### Quy trình kiểm thử DST có ý nghĩa

1. Chọn một `ZoneId` có chuyển đổi DST, ví dụ `America/New_York`, thay vì cộng cứng `-05:00` hoặc `-04:00`.
2. Với lịch **02:30 mỗi ngày**, lấy một seed trước ngày 08/03/2026 rồi tính liên tiếp nhiều mốc bằng `CronExpression.next(ZonedDateTime)`. Ghi lại kết quả mà phiên bản đang dùng chọn cho ngày có gap.
3. Với lịch **01:30 mỗi ngày**, làm tương tự quanh 01/11/2026. So sánh theo `Instant` và cả offset được áp dụng để phát hiện mốc lặp.
4. Xác định chính sách nghiệp vụ: “bỏ qua nếu giờ không tồn tại”, “chạy bù một lần”, “mỗi ngày địa phương tối đa một lượt” hay “mỗi mốc cron hợp lệ một lượt”. Sau đó viết assertion cho **chính sách đã thống nhất**.
5. Nếu yêu cầu là “mỗi 24 giờ chính xác” thì `Duration.ofHours(24)` cùng cơ chế periodic có thể phù hợp hơn một cron theo giờ địa phương; nếu yêu cầu “09:00 giờ địa phương”, cron có zone rõ ràng là diễn đạt đúng hơn.

Một test đáng tin phải cố định **ngày, múi giờ, biểu thức, phiên bản library và expected `Instant`**; nếu thay phiên bản JDK/tzdb hoặc Spring, cần xem lại test ở các vùng có thay đổi quy định giờ địa phương.

## 6. Phân biệt kiểm thử logic lịch và kiểm thử tích hợp scheduler

| Lớp kiểm thử | Đầu vào có thể kiểm soát | Đảm bảo chính | Không đảm bảo |
| --- | --- | --- | --- |
| Unit test `Clock.fixed`/`Clock.offset` | `Instant`, `ZoneId`, khoảng thời gian | Quyết định nghiệp vụ khi “bây giờ” thay đổi | Tự động kích hoạt `@Scheduled` |
| Unit test `CronExpression.next` | Cron, `ZonedDateTime` seed | Các mốc thời gian theo lịch | Thread pool, đăng ký annotation, xử lý miss |
| Unit test `CronTrigger.nextExecution` | `TriggerContext`, `Clock`, completion | Mốc tiếp theo dựa trên trạng thái lịch | Job có thực sự được chạy hay không |
| Integration test Spring context | Properties, bean scheduler, task registration | `@EnableScheduling`, giải tham số `@Scheduled`, wiring | Độ chính xác của vận hành nhiều ngày nếu chỉ ngủ vài giây |
| Test end-to-end/tải | Hệ thống scheduler thật, lưu trạng thái, nhiều instance | Retry, idempotency, tranh chấp, giám sát | Nên giới hạn phạm vi, tránh chờ giờ thật trong unit suite |

**Tích hợp nên kiểm tra riêng:** một Spring context test với cấu hình `report.cron` và `report.zone` thực tế, bean `DailyReportSchedule` được tạo, lịch được đăng ký, ứng dụng dừng thì task được hủy. Có thể dùng `ScheduledTaskHolder` hoặc một scheduler thu thập lời gọi để kiểm tra việc đăng ký; thiết kế harness để việc gọi task là sự kiện do test chủ động kích hoạt. Tránh lấy một `Thread.sleep(1_000)` rồi đếm số lần gọi, vì CI chậm, GC pause hoặc thread pool bận có thể làm test sai ngẫu nhiên.

`ThreadPoolTaskScheduler` có `setClock(Clock)` và Spring `TaskScheduler` có `getClock()`, nhưng **đặt `Clock.fixed` vào một scheduler thực đang dùng timer hệ điều hành không tạo ra mô phỏng thời gian toàn diện**. Chỉ mock/điều khiển tính toán `Trigger` khi unit test; nếu cần virtual time cho toàn bộ hệ lập lịch, dùng harness/scheduler giả được thiết kế để nhận các sự kiện “advance time” và phát task theo thứ tự xác định. `VirtualTimeScheduler` của Reactor không tự động làm `@Scheduled` của Spring trở thành virtual time.

### Khi job chạy chậm hoặc có nhiều worker

- `@Scheduled(cron = ...)` của Spring và `CronTrigger` thông thường có cách tính mốc dựa trên hoàn thành, không nên giả định mọi slot đã định đều được thực thi khi task dài.
- `fixedRate` đo chu kỳ theo mốc bắt đầu dự kiến; `fixedDelay` đo khoảng cách từ lúc hoàn tất lượt trước; cron theo lịch/ngày/giờ, không tương đương hai cơ chế này.
- Khi một tác vụ được đăng ký lặp lại, hoặc có nhiều phiên bản ứng dụng đều bật `@EnableScheduling`, có thể nhận nhiều lời gọi cho cùng ngày nghiệp vụ. Với tác vụ quan trọng, nên kiểm tra idempotency qua khóa nghiệp vụ (ví dụ `jobName + businessDate`) lưu bền vững và/hoặc distributed lock.
- Test idempotency nên gửi **hai lần cùng khóa**, xác minh hiệu ứng nghiệp vụ chỉ xảy ra một lần; test recovery nên mô phỏng dừng và khởi động lại với lịch đã lưu, không giả định scheduler trong bộ nhớ tự biết lần đã bỏ qua.
- Test sát ranh giới timeout và exception: lịch có tiếp tục sau khi job lỗi không, kết quả có bị retry vô hạn không, có làm trễ job khác trong pool nhỏ không? Các khía cạnh này nằm ngoài việc chỉ kiểm tra cú pháp cron.

## 7. Thiết kế ma trận test theo yêu cầu nghiệp vụ

Thay vì chỉ thử “có chạy hay không”, hãy chốt tập ca kiểm thử nhỏ nhưng bao phủ ranh giới:

| Nhóm | Seed và kiểm tra cụ thể | Lỗi thường phát hiện |
| --- | --- | --- |
| Cú pháp | Năm trường so với sáu trường; giá trị phút 60; macro đúng | Copy cron Linux/Quartz sai |
| Lịch làm việc | Thứ Sáu 16:00, thứ Bảy, Chủ nhật, thứ Hai 08:59 tại zone nghiệp vụ | Job chạy cuối tuần hoặc bỏ sáng thứ Hai |
| Múi giờ triển khai | Cùng `Instant`, thử `UTC` và `Asia/Ho_Chi_Minh` | Dính vùng giờ máy chủ |
| Qua ngày/tháng | 23:59:59 → 00:00:01; 31/12 → 01/01 | Lỗi xác định ngày báo cáo |
| Năm nhuận | 28/02 → 29/02 → 01/03 trong năm nhuận | Tính ngày thủ công |
| DST | Gap tháng 3, overlap tháng 11 ở `America/New_York` | Chạy thiếu/trùng, sai offset |
| Job chạy lâu | Hoàn thành sau 2–3 slot cron | Nhầm lenient với fixed/catch-up |
| Khởi động lại | Mốc trước/sau downtime, trạng thái last-run đã lưu | Mất job hoặc chạy bù trùng |
| Concurrency | Hai instance cùng nhận một khóa ngày nghiệp vụ | Trùng báo cáo, đúp thanh toán |

Mỗi case ghi rõ **input**, **zone**, **mốc `Instant` kỳ vọng**, **hành động được gọi** và **quy tắc idempotency**. Với lịch ngày làm việc, đừng chỉ test thứ Hai bình thường; hãy test đoạn chuyển từ thứ Sáu sang thứ Hai và thời điểm sát 09:00:00. Với lịch tháng, luôn có ca ngày 28/29/30/31 và leap year.

Có thể tổ chức các ca dưới dạng parameterized tests JUnit 5 hoặc bảng trường hợp, nhưng dùng một `Clock.fixed`/seed riêng cho mỗi case để tránh phụ thuộc thứ tự chạy test.

## 8. Những anti-pattern khiến test dễ sai

**Lỗi 1: dùng thời gian thật trong nghiệp vụ.**

```java
// Dễ phụ thuộc vùng giờ và thời điểm chạy CI.
LocalDate businessDate = LocalDate.now();

// Có thể kiểm thử deterministically với Clock được tiêm.
LocalDate businessDate = LocalDate.ofInstant(clock.instant(), businessZone);
```

**Lỗi 2: “sleep cho qua phút rồi kiểm tra số lần gọi”.** Test có thể xanh trên máy cá nhân nhưng đỏ trên CI vì scheduling, GC, CPU và độ trễ không có giới hạn chính xác. Unit test nên gọi trực tiếp phương thức nghiệp vụ/đường tính trigger với `Instant` đã biết.

**Lỗi 3: thay `TimeZone.setDefault(...)` toàn JVM trong test chạy song song.** Đây là trạng thái toàn cục, dễ gây ảnh hưởng test khác. Truyền `ZoneId` tường minh và so sánh `Instant` sẽ an toàn hơn. Nếu bắt buộc test default zone, cô lập context hoặc process.

**Lỗi 4: dùng `LocalDateTime` để lưu “lần chạy trước” ở vùng có DST.** Một thời điểm 01:30 có thể có hai offset khác nhau. Lưu mốc thực thi dưới dạng `Instant`; chỉ chuyển sang `ZonedDateTime` để đối chiếu nghiệp vụ.

**Lỗi 5: một assertion `CronExpression.isValidExpression(...)` rồi kết luận lịch đúng.** Cú pháp hợp lệ vẫn có thể sai thứ, sai giờ, sai zone hoặc xử lý không phù hợp qua DST.

**Lỗi 6: đặt clock cố định trong service rồi chờ `@Scheduled` chạy theo giờ giả.** Clock của service chỉ ảnh hưởng `clock.instant()` nơi nó được gọi. Kiểm thử trigger với `SimpleTriggerContext`, hoặc dùng scheduler giả/recording scheduler đúng mục đích.

**Lỗi 7: mặc định lịch quan trọng là “exactly once”.** `@Scheduled` đơn lẻ không thay thế giao dịch, khóa phân tán, hàng đợi bền vững hay kiểm soát chạy bù. Unit test đúng lịch chỉ là một phần của bảo đảm nghiệp vụ.

## 9. Quy trình QA trước khi merge hoặc triển khai

1. Xác nhận cron **sáu trường**, trường giây ở đầu; đọc giá trị hiệu lực của properties theo từng môi trường.
2. Ghi rõ `ZoneId` trong cấu hình và test; so sánh `Instant` cho thời điểm, `LocalDate` cho ngày nghiệp vụ.
3. Tạo `Clock` dưới dạng dependency cho logic đọc “hiện tại”; test dùng `Clock.fixed` và `Clock.offset` để đi qua ranh giới mà không sleep.
4. Test `CronExpression.next` với nhiều seed: giữa chu kỳ, đúng một giây trước mốc chạy, sát cuối tuần/tháng/năm.
5. Test `CronTrigger.nextExecution` với trạng thái lượt chạy trước hoàn thành nhanh, chậm, lỗi hoặc bị gián đoạn; chọn lenient/fixed có chủ đích.
6. Nếu zone có DST, test gap/overlap với phiên bản JDK/tzdb và Spring đang phát hành; viết chính sách nghiệp vụ cho giờ không tồn tại hoặc bị lặp.
7. Có ít nhất một integration test để kiểm chứng wiring `@EnableScheduling`/`@Scheduled`/properties; tách nó khỏi các test logic ngắn, xác định.
8. Với multi-instance, retry hoặc tác vụ tài chính, kiểm tra khóa idempotency và phục hồi lịch bền vững.
9. Quan sát trong vận hành: thời gian thực tế bắt đầu, độ trễ so với mốc dự kiến, thời gian xử lý, lần bị bỏ qua/chạy bù, số lỗi, backlog và số lần thực thi trùng.

**Tiêu chí hoàn thành bài học:** có thể chỉ ra chính xác **khi nào** cron phát mốc dưới một múi giờ xác định; có thể điều khiển “hiện tại” trong unit test; chứng minh được DST và lịch chạy lâu làm thay đổi ý nghĩa thời gian; đồng thời phân biệt rõ tính đúng của phép tính lịch với bảo đảm thực thi thực tế của hệ thống.

### Tài liệu tham khảo

- Spring Framework — Task Execution and Scheduling, `@Scheduled`, cron và `TaskScheduler`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework 6.2 — `CronExpression`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html
- Spring Framework 6.2 — `CronTrigger`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html
- Spring Framework 6.2 — `SimpleTriggerContext`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/SimpleTriggerContext.html
- Java SE — `java.time.Clock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html
- Java SE — `ZoneRules.getValidOffsets`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/zone/ZoneRules.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-010">Kiểm tra dừng context và giải phóng executor trong kiểm thử</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra dừng context và giải phóng executor trong kiểm thử Spring Framework

## 1. Bài kiểm thử cần chứng minh điều gì?

Trong một hệ thống Spring có `@Async`, `TaskExecutor` hoặc tác vụ định kỳ, kiểm thử có thể kết thúc nhưng các thread nền vẫn chạy. Hậu quả thường thấy là JUnit/Gradle không thoát, tác vụ từ test trước tác động sang test sau, kết nối/tài nguyên không được trả về, hoặc kết quả kiểm thử phụ thuộc thứ tự chạy.

Bài này tập trung vào **vòng đời của `ApplicationContext` và executor trong kiểm thử**. Một bài kiểm thử shutdown đáng tin cần xác minh những hợp đồng riêng biệt:

1. **Context đã đóng:** `ConfigurableApplicationContext.close()` được gọi, `isActive()` trở thành `false`; callback dừng/hủy của bean được thực thi.
2. **Executor đã nhận tín hiệu dừng:** pool không còn nhận task mới theo chính sách shutdown đã chọn; `ThreadPoolExecutor.isShutdown()` phản ánh trạng thái này.
3. **Task đang chạy hoặc đang xếp hàng được xử lý theo chính sách:** hoàn thành trong graceful shutdown, hoặc có thể bị hủy/ngắt khi dùng immediate shutdown; test không được mặc định mọi chính sách giống nhau.
4. **Worker thật sự kết thúc:** `isTerminated()` chỉ đúng khi executor đã shutdown và tất cả worker đã kết thúc. `isShutdown() == true` **chưa chứng minh** giải phóng thread.
5. **Bài kiểm thử luôn dọn dẹp:** kể cả khi assertion ném lỗi, timeout hoặc task ném exception, executor/context do test sở hữu vẫn được đóng.

Có hai phạm vi tài nguyên khác nhau: executor là **Spring-managed bean** (Spring quản lý lifecycle) và executor do **mã kiểm thử tự tạo** bằng `Executors.newFixedThreadPool(...)` (mã kiểm thử phải tự shutdown). Gọi `context.close()` không bảo đảm giải phóng mọi executor được tạo thủ công nằm ngoài container.

## 2. Vòng đời khi đóng Spring context

Một luồng tổng quát khi dùng `AnnotationConfigApplicationContext`:

```text
Test tạo ApplicationContext
           |
           v
Spring tạo bean và khởi tạo ThreadPoolTaskExecutor
           |
           v
Test nộp task --> worker đang chạy / task đang đợi
           |
           v
Test gọi context.close()
           |
           +--> ContextClosedEvent và bước stop/lifecycle thích hợp
           |
           +--> destroy singleton beans
                      |
                      v
          ExecutorConfigurationSupport.shutdown()
                      |
                      v
        ThreadPoolExecutor: shutdown / shutdownNow
                      |
                      v
         chờ termination nếu được cấu hình
           |
           v
context.close() trả về
```

Các bước cụ thể phụ thuộc cấu hình executor và phiên bản Spring; đặc biệt, thứ tự từ chối task mới và thời điểm worker bị ngắt không nên suy ra chỉ từ tên phương thức `close()`.

### Ba trạng thái phải phân biệt

| Quan sát | Ý nghĩa | Có đủ chứng minh không còn thread? |
| --- | --- | --- |
| `context.isActive() == false` | Container không còn ở trạng thái active | **Không**: tài nguyên ngoài container có thể còn chạy |
| `pool.isShutdown() == true` | Executor đã bắt đầu shutdown và không nhận task mới qua JDK pool | **Không**: worker có thể đang hoàn tất task |
| `pool.isTerminated() == true` | Pool đã shutdown và worker đã kết thúc | **Có**, đối với *pool cụ thể đó*, nhưng không nói về những executor khác |

`getActiveCount() == 0` cũng không thay thế `isTerminated()`: pool có thể vẫn giữ worker rảnh đang chờ tác vụ.

### Hai chính sách shutdown thường dùng

**Graceful shutdown:** Để những task đã được chấp nhận hoàn thành, bao gồm task trong hàng đợi. Với `ThreadPoolTaskExecutor` có thể cấu hình:

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(10);
```

`setWaitForTasksToCompleteOnShutdown(true)` hướng Spring đến việc không ngắt task đang chạy và cho phép xử lý các task đã xếp hàng trong giai đoạn shutdown. `setAwaitTerminationSeconds(10)` yêu cầu bước hủy executor chờ **tối đa 10 giây**; đó là giới hạn chờ, **không phải cam kết** rằng tác vụ chắc chắn hoàn thành trong 10 giây. Nếu task treo hoặc không kết thúc, context có thể đóng xong trong khi pool chưa terminated.

**Immediate shutdown:** `shutdownNow()` của JDK cố ngắt các worker đang chạy và trả về danh sách task chưa bắt đầu. Việc ngắt là yêu cầu hợp tác; task bỏ qua interruption, chờ I/O không ngắt được hoặc vòng lặp vô hạn vẫn có thể tiếp tục. Với Spring, giá trị mặc định `waitForTasksToCompleteOnShutdown=false` còn tương tác với coordinated lifecycle stop; **không** nên suy diễn rằng mọi lần `context.close()` đều ngay lập tức ngắt task.

Một tùy chọn khác, `ThreadPoolTaskExecutor.setStrictEarlyShutdown(true)` (Spring 6.1.4+), điều khiển việc phát tín hiệu shutdown sớm ngay khi đóng context và từ chối submission sau thời điểm đó. Tương tác của nó với `waitForTasksToCompleteOnShutdown` và `acceptTasksAfterContextClose` cần được đọc theo đúng phiên bản Spring dùng trong dự án. Trong bài kiểm thử dưới đây, ta chỉ dựa vào chính sách graceful được khai báo tường minh.

## 3. Ví dụ hoàn chỉnh: context sở hữu ThreadPoolTaskExecutor

Ví dụ dùng **Java 17+, Spring Framework 6.2.x, JUnit Jupiter 5**. Mỗi test tự tạo một `AnnotationConfigApplicationContext` nên sở hữu rõ vòng đời; không cần `@SpringBootTest` hoặc cơ chế cache context của Spring Test.

Cấu trúc:

```text
build.gradle
src/test/java/example/ExecutorShutdownTest.java
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    testImplementation 'org.springframework:spring-context:6.2.13'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.11.4'
}

test {
    useJUnitPlatform()
}
```

Ở đây không có mã sản phẩm: toàn bộ cấu hình `@Configuration` và test đặt trong một tệp test. `ThreadPoolTaskExecutor` được khai báo bằng `@Bean`; Spring khởi tạo và gọi lifecycle destruction của nó khi context đóng. Không cần gọi thủ công `executor.initialize()` hoặc thêm `@PreDestroy` để gọi `shutdown()` lần nữa.

**`src/test/java/example/ExecutorShutdownTest.java`**

```java
package example;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class ExecutorShutdownTest {

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean("lessonExecutor")
        ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(2);
            executor.setThreadNamePrefix("test-lesson-");

            // Chính sách mà test sẽ xác minh.
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(10);
            return executor;
        }
    }

    @Test
    void closeWaitsForRunningTaskAndTerminatesExecutor() throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TestConfig.class)) {

            ThreadPoolTaskExecutor executor =
                    context.getBean("lessonExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();

            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch mayFinish = new CountDownLatch(1);
            CountDownLatch closeReturned = new CountDownLatch(1);
            AtomicReference<Throwable> closeError = new AtomicReference<>();

            Future<String> result = executor.submit(() -> {
                taskStarted.countDown();
                if (!mayFinish.await(8, SECONDS)) {
                    throw new IllegalStateException("worker was not released");
                }
                return "completed";
            });

            Thread closer = new Thread(() -> {
                try {
                    context.close();
                } catch (Throwable failure) {
                    closeError.set(failure);
                } finally {
                    closeReturned.countDown();
                }
            }, "context-closer");

            try {
                assertTrue(taskStarted.await(3, SECONDS),
                        "worker must start before closing context");
                closer.start();

                // Chờ tín hiệu shutdown thật, không suy đoán từ sleep.
                awaitPoolShutdown(pool);
                assertEquals(1L, closeReturned.getCount(),
                        "close() should wait while accepted work is blocked");
                assertFalse(result.isDone(),
                        "worker should still be waiting for mayFinish");
            } finally {
                // Luôn nhả worker, kể cả khi một assertion thất bại.
                mayFinish.countDown();
                if (closer.isAlive()) {
                    closer.join(12_000);
                }
            }

            assertFalse(closer.isAlive(), "context close must finish");
            assertEquals(0L, closeReturned.getCount());
            assertNull(closeError.get(), "close() must not fail");
            assertEquals("completed", result.get(2, SECONDS));
            assertFalse(context.isActive());
            assertTrue(pool.isShutdown());
            assertTrue(pool.isTerminated());

            // Reference executor vẫn tồn tại trong test nhưng pool đã đóng.
            assertThrows(TaskRejectedException.class,
                    () -> executor.execute(() -> {}));
        }
    }

    @Test
    void closeTerminatesIdleExecutorToo() throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TestConfig.class);
        ThreadPoolTaskExecutor executor =
                context.getBean("lessonExecutor", ThreadPoolTaskExecutor.class);
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();

        try {
            Future<?> completed = executor.submit(() -> {});
            completed.get(3, SECONDS);
        } finally {
            context.close();
        }

        assertFalse(context.isActive());
        assertTrue(pool.isShutdown());
        assertTrue(pool.isTerminated());
    }

    private static void awaitPoolShutdown(ThreadPoolExecutor pool) {
        long deadline = System.nanoTime() + SECONDS.toNanos(3);

        while (!pool.isShutdown() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(MILLISECONDS.toNanos(2));
        }

        assertTrue(pool.isShutdown(),
                "executor did not start shutdown within the deadline");
    }
}
```

Chạy bằng Gradle wrapper của dự án:

```bash
./gradlew test --tests example.ExecutorShutdownTest
```

Trên Windows: `gradlew.bat test --tests example.ExecutorShutdownTest`. Kết quả mong đợi: **2 test pass**; ví dụ không dựa vào stdout, thời gian chạy chính xác hay tên thread do hệ điều hành cấp.

### Diễn giải từng assertion quan trọng

- `taskStarted.await(3, SECONDS)` xác nhận công việc đã bắt đầu, rồi `mayFinish` giữ worker ở trạng thái đang thực thi. Test kiểm soát được trạng thái mà không phải đo thời gian xử lý nghiệp vụ.
- `closer` là một thread **khác** để gọi `context.close()`. Nếu test thread tự gọi `close()` trong khi worker vẫn chờ `mayFinish`, chính test sẽ bị khóa và không thể nhả latch.
- `awaitPoolShutdown(pool)` đợi **tín hiệu shutdown của pool** với deadline hữu hạn. Sau khi tín hiệu đó xuất hiện, `closeReturned.getCount() == 1` chứng minh lời gọi đóng context chưa trả về khi task còn bị giữ; `result.isDone() == false` xác minh task chưa hoàn thành. Đây là bằng chứng của việc chờ task khi shutdown, không phải một phép đo mili giây mong manh.
- Khối `finally` nhả latch ngay cả khi assertion trước đó lỗi. `closer.join(12_000)` tránh chờ vô hạn; sau join kiểm tra chính xác thread đóng context đã kết thúc.
- `result.get(2, SECONDS)` khẳng định tác vụ đã tạo ra kết quả; `pool.isTerminated()` khẳng định worker đã được giải phóng. Hai sự kiện này khác nhau: hoàn thành task không tự động đồng nghĩa pool đã đóng.
- `TaskRejectedException` trên submission sau khi `close()` hoàn tất khẳng định executor không chấp nhận tác vụ mới. Không dùng thử nghiệm này để suy ra **thời điểm chính xác** bắt đầu từ chối task trong quá trình context đang đóng.

**Vì sao test chọn timeout khác nhau?** `3 giây` là deadline bắt đầu/quan sát shutdown; `8 giây` là bảo vệ chống treo bên trong worker; `10 giây` là giới hạn Spring đợi khi đóng executor; `12 giây` dành cho join của thread đóng context. Những con số được chọn làm giới hạn an toàn của test; không phải SLA hiệu năng. Trên môi trường CI rất chậm có thể dùng deadline phù hợp, nhưng không giải quyết race bằng `Thread.sleep(...)` ngẫu nhiên.

Nếu chủ ý thay `setWaitForTasksToCompleteOnShutdown(true)` thành chính sách có thể ngắt task, bài test graceful này phải được thiết kế lại để assert **interrupt/cancellation policy**. Không nên sửa assertion cho pass khi hành vi không còn đúng với chính sách đã công bố.

## 4. Context nào thực sự được đóng trong Spring Test?

Có sự khác nhau rõ rệt giữa **context tự tạo** và **context của TestContext framework**.

| Cách viết test | Ai tạo context? | Khi nào context đóng? | Cách xác minh shutdown |
| --- | --- | --- | --- |
| `new AnnotationConfigApplicationContext(...)` | Chính test | `close()` hoặc `try-with-resources` trong test | Có thể quan sát trực tiếp sau lời gọi `close()` |
| `@SpringJUnitConfig` / `@SpringBootTest` | Spring TestContext | Thường được **cache và tái sử dụng** giữa các test cùng cấu hình; không mặc nhiên đóng sau mỗi `@Test` | Dùng fixture/context riêng hoặc cơ chế yêu cầu đóng rõ ràng |
| `@DirtiesContext` trên test/class | Spring TestContext | Đóng và loại khỏi cache theo `methodMode` / `classMode` được khai báo | Hữu ích cho test tác động đến trạng thái context; cẩn thận với thứ tự lifecycle |
| `ExecutorService` do test tự tạo | Mã test | Khi mã test gọi `shutdown()` / `shutdownNow()` | `awaitTermination(...)` và xác minh kết thúc |

Ví dụ, với `@SpringBootTest`, đặt logic kiểm tra `executor.isTerminated()` trong `@AfterEach` thường **sai thời điểm**, vì context có thể vẫn nằm trong cache cho các test tiếp theo. Nếu cần đóng context phục vụ test, cân nhắc `@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)` hoặc `classMode = AFTER_CLASS` tùy phạm vi. Lifecycle listener của Spring đánh dấu dirty và đóng context theo thời điểm của nó, nên assertion trong `@AfterEach` không mặc nhiên chạy **sau** close. Để kiểm tra chính xác hành vi shutdown ngay tại method, cách tự tạo context như ví dụ ở mục 3 thường rõ ràng hơn.

**Đừng gọi `close()` tùy tiện trên context được injection bởi Spring TestContext** trong một test thông thường; làm vậy có thể phá giả định cache của các test khác. Thay vào đó, sử dụng cơ chế do framework quản lý hoặc dựng context riêng và tự quản lý lifecycle.

## 5. Executor thuộc quyền sở hữu của ai?

### Executor là Spring bean

`ThreadPoolTaskExecutor` đã được đăng ký như `@Bean` và được Spring quản lý lifecycle. `context.close()` sẽ thực hiện destroy callback liên quan; cấu hình shutdown nên được đặt ở chính bean đó. Nếu callback tạo hoặc đăng ký thêm tài nguyên riêng, bean cũng phải có cơ chế đóng tài nguyên tương ứng.

Khi cần dùng JDK `ExecutorService` như bean, nên khai báo hủy tường minh:

```java
// Đoạn minh họa cho một lớp @Configuration:
@Bean(destroyMethod = "shutdown")
ExecutorService applicationPool() {
    return Executors.newFixedThreadPool(2);
}
```

Cần import `java.util.concurrent.ExecutorService` và `java.util.concurrent.Executors` trong lớp cấu hình tương ứng. `destroyMethod = "shutdown"` chỉ gửi lệnh shutdown; nếu yêu cầu đợi mọi task xong trước khi kết thúc, cần bổ sung chính sách await/cleanup phù hợp. Không mặc định mọi executor, scheduler, listener container hoặc client tự tuân theo cùng một quy tắc.

### Executor được test tạo độc lập

Khi test dùng `Executors.newFixedThreadPool` hoặc `Executors.newSingleThreadScheduledExecutor` bên ngoài context, chính test phải đóng nó. Một mẫu shutdown an toàn:

```java
// Mã minh họa bên trong finally hoặc phương thức cleanup của test:
pool.shutdown();
try {
    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new AssertionError("executor did not terminate");
        }
    }
} catch (InterruptedException e) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
    throw new AssertionError("interrupted while cleaning up", e);
}
```

Trong đoạn trên, `pool` là `ExecutorService` do test sở hữu, với import `java.util.concurrent.TimeUnit`. `shutdownNow()` cố gửi interrupt; không thể cưỡng ép một tác vụ không hợp tác dừng lại. Luôn release các latch, đóng socket/stream, hủy subscription hoặc dọn dẹp các điều kiện khiến worker chờ trước khi trông đợi termination.

Java 19+ có `ExecutorService.close()`, nhưng khi dự án dùng Java 17 thì mẫu `shutdown` + `awaitTermination` là đường đi tương thích rõ ràng. Không dựa vào `AutoCloseable` của `ExecutorService` khi còn hỗ trợ Java 17.

## 6. Những trường hợp dễ bỏ sót trong kiểm thử

### A. Test chỉ thấy future hoàn thành

```java
// Không đủ để chứng minh executor đã giải phóng worker:
future.get(5, TimeUnit.SECONDS);
```

Tác vụ hoàn tất nhưng pool vẫn có thể đang chờ task mới. Sau khi đóng context, quan sát thêm `isShutdown()` và `isTerminated()` của đúng pool đã thực thi.

### B. Chỉ assert `context.isActive() == false`

Context đã đóng nhưng một `ExecutorService` tạo thủ công, một thread chạy vòng lặp, một subscription hoặc timer bên ngoài bean lifecycle có thể vẫn sống. Xác định **ownership** của từng nguồn tạo thread và đóng đúng chủ.

### C. Dùng `Thread.sleep(...)` rồi kiểm tra số thread

Tên và số lượng thread có thể thay đổi theo thời điểm, môi trường CI, các framework hoặc thread pool tái sử dụng. Khi kiểm tra lifecycle của một executor cụ thể, ưu tiên API `ThreadPoolExecutor`, `CountDownLatch`, `Future` và deadline hữu hạn. Thread dump là công cụ điều tra leak, không phải assertion mặc định cho mọi bài test.

### D. Test treo vì gọi `close()` từ sai thread

Với graceful shutdown có chờ, nếu test gọi `close()` trong khi worker đang đợi một latch chỉ test mới có thể nhả, test tự tạo deadlock. Cho worker một cách thoát trong `finally`; khi cần chứng minh `close()` đang đợi, chạy lời gọi đóng context trên thread khác như ví dụ.

### E. Cấu hình chờ không bảo đảm kết thúc

`setAwaitTerminationSeconds(10)` chỉ chờ tối đa 10 giây. Nếu task mất 20 giây, context có thể hết thời gian chờ và vẫn còn worker. Bài test phải assert `isTerminated()` sau close nếu yêu cầu thật sự là dọn sạch; đồng thời sửa task/cleanup để có thể hoàn thành hoặc đáp ứng interruption.

### F. Đánh đồng graceful với interruption

Với graceful shutdown, task đang chạy và task đã vào queue có cơ hội hoàn thành. Với immediate shutdown, task có thể nhận interrupt hoặc bị bỏ khỏi queue. Không viết một test mong cả “task chắc chắn hoàn tất” lẫn “task chắc chắn bị hủy” nếu không định nghĩa chính sách. Nếu cần xác minh interruption, viết test riêng với worker chờ interrupt và dùng latch để ghi nhận tín hiệu đã quan sát được.

### G. Cho task mới vào trong lúc context đang đóng

Các setting như `acceptTasksAfterContextClose` và `strictEarlyShutdown` ảnh hưởng thời điểm ngừng nhận task trong giai đoạn close. Test shutdown không nên dựa vào ranh giới thời gian mơ hồ. Chỉ assert submission **sau khi close trả về**, hoặc thiết kế rõ bài test cho chính sách reject ngay tại `ContextClosedEvent`.

### H. Kiểm thử `@Scheduled`/`ThreadPoolTaskScheduler`

Scheduler vừa điều phối trigger, vừa có thể sở hữu worker; hủy lịch lặp và xử lý tác vụ đang chạy là hai việc khác nhau. Nếu bài test dùng `@Scheduled`, cần kiểm tra chính sách shutdown của `ThreadPoolTaskScheduler`, hủy `ScheduledFuture` hoặc cleanup theo lifecycle. Tránh để fixed-rate task chạy mãi trong lúc context đang shutdown. Những điều này là trường hợp mở rộng; bài test `ThreadPoolTaskExecutor` ở trên chỉ kiểm chứng executor đã khai báo.

### I. Test chạy song song dùng chung executor

Nếu hai test cùng sử dụng context cache với pool singleton, một test đóng pool có thể khiến test kia nhận `TaskRejectedException`. Với test shutdown có tác động lifecycle, dùng context riêng hoặc cô lập test, tránh phụ thuộc thứ tự thực thi. Nếu dùng `@DirtiesContext` thì hiểu chính xác thời điểm cache bị loại bỏ.

### J. Gọi `shutdownNow()` nhưng task bỏ qua interrupt

Worker trong tác vụ thực tế cần xử lý `InterruptedException` đúng cách (thường khôi phục cờ interrupt khi chuyển sang exception khác), có deadline cho I/O, và không nuốt interruption trong vòng lặp. Nếu mã không hợp tác, `shutdownNow()` không đồng nghĩa `isTerminated()` sẽ đúng.

## 7. Quy trình áp dụng vào module Spring thực tế

1. Liệt kê đúng executor/scheduler/listener đang được dùng và **ai sở hữu chúng**: Spring bean, test, hay thư viện ngoài. Tách bài test lifecycle khỏi unit test logic nghiệp vụ.
2. Chọn chính sách shutdown cần kiểm tra: cho task đang chạy hoàn thành, ngắt task, xử lý queue, hủy lịch định kỳ, từ chối submission mới và giới hạn thời gian đóng.
3. Tạo context tối thiểu bằng cấu hình thật sự quản lý executor, ghi nhận handle của chính pool đó và đặt một task có `started`/`release` latch để test chủ động kiểm soát thời điểm.
4. Gọi `context.close()` có kiểm soát. Nếu muốn chứng minh close chờ task, gọi close ở thread khác, đợi `isShutdown()`, xác minh close chưa trả về, rồi nhả task.
5. Sau close, xác minh **đủ** `context.isActive() == false`, `pool.isShutdown()`, `pool.isTerminated()` và hành vi từ chối task theo chính sách; đồng thời xác minh future/task đã kết thúc đúng mong đợi.
6. Dọn dẹp bằng `try-with-resources`/`finally` cho context và executor do test tạo; luôn mở latch, đóng I/O, giải phóng fixture dù assertion thất bại.
7. Khi suite treo, dùng thread dump/JFR để xác định worker nào vẫn sống và nó đang chờ ở đâu; kiểm tra pool bị bỏ quên, task không hợp tác với interrupt, timeout quá ngắn, transaction/context thread-local hoặc dependency bên ngoài chưa đóng.

Với test tạo context thủ công, một lần `close()` thành công và `isTerminated()` đúng cho executor được quản lý là bằng chứng trực tiếp của cleanup. Với `@SpringBootTest` sử dụng context cache, việc test method kết thúc **không chứng minh** context đã được đóng; phải kiểm tra theo vòng đời của TestContext framework.

## Tài liệu tham khảo

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `TaskExecutor`, `ThreadPoolTaskExecutor`, lifecycle và scheduling.
- [Spring Framework — ExecutorConfigurationSupport Javadoc](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html): `setWaitForTasksToCompleteOnShutdown`, `setAwaitTerminationSeconds`, shutdown và dừng lifecycle.
- [Spring Framework — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): `setStrictEarlyShutdown` và quan sát pool.
- [Spring TestContext — Context Caching](https://docs.spring.io/spring-framework/reference/6.2/testing/testcontext-framework/ctx-management/caching.html) và [`@DirtiesContext`](https://docs.spring.io/spring-framework/reference/6.2/testing/annotations/integration-spring/annotation-dirtiescontext.html).
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html), [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html) và [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-011">Kiểm tra tranh chấp bằng phối hợp luồng thay vì chỉ tăng số lần chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra tranh chấp bằng phối hợp luồng thay vì chỉ tăng số lần chạy

## 1. Mục tiêu của bài kiểm thử

Khi kiểm thử code đồng thời trong Spring Framework, việc gọi cùng một phương thức 1.000 hoặc 100.000 lần **không bảo đảm** hai luồng thực sự đi qua đoạn mã nguy hiểm vào cùng một thời điểm. Một bài kiểm thử có thể luôn xanh chỉ vì máy chạy thử vô tình thực thi các lời gọi gần như tuần tự.

Cách có sức thuyết phục hơn là **phối hợp các luồng tại đúng điểm có thể tranh chấp**: để cả hai đọc cùng trạng thái cũ, giữ chúng ở một điểm chờ, sau đó cho chúng tiếp tục và kiểm tra bất biến nghiệp vụ. Các công cụ tiêu biểu trong Java là `CountDownLatch`, `CyclicBarrier`, `Phaser`, `ExecutorService` và `Future`; Spring Test giúp đặt chúng vào ngữ cảnh các bean thực tế.

Sau bài này, bạn cần làm được ba việc:

1. Chỉ ra **cửa sổ tranh chấp** (race window) trong thao tác đọc–kiểm tra–ghi và giải thích vì sao lặp lại nhiều lần không thay thế việc kiểm soát xen kẽ luồng.
2. Viết bài kiểm thử có **pha sẵn sàng, tín hiệu bắt đầu, điểm hội tụ, thời hạn chờ và thu thập kết quả/lỗi**, sao cho tái hiện được một lỗi tranh chấp cụ thể.
3. Kiểm tra sau khi sửa bằng `compareAndSet`, khóa, ràng buộc database hoặc transaction thích hợp; hiểu những điều kiện bổ sung khi dùng Spring `@Async`, `@Scheduled` và `@Transactional`.

## 2. Điều cần bắt được là một thứ tự xen kẽ cụ thể

Giả sử chỉ còn **một** vé và có hai lời gọi `reserve()` đến gần nhau:

```java
// Sai: ba thao tác riêng lẻ không tạo thành một giao dịch nguyên tử.
if (remaining.get() > 0) {
    remaining.decrementAndGet();
    return true;
}
return false;
```

`AtomicInteger` khiến từng thao tác `get()`, `decrementAndGet()` có ngữ nghĩa nguyên tử/hiển thị dữ liệu thích hợp, nhưng **cặp “kiểm tra còn vé” và “trừ vé” không nguyên tử**. Hai luồng vẫn có thể cùng đọc số 1.

| Mốc | Luồng A | Luồng B | remaining |
| --- | --- | --- | --- |
| T0 | Đọc 1, kiểm tra hợp lệ | — | 1 |
| T1 | Tạm dừng trước khi trừ | Đọc 1, kiểm tra hợp lệ | 1 |
| T2 | Trừ 1, cho phép đặt vé | — | 0 |
| T3 | — | Trừ 1, cũng cho phép đặt vé | -1 |
| T4 | Trả về `true` | Trả về `true` | **-1: vi phạm bất biến** |

Bất biến nghiệp vụ là: với tồn kho ban đầu bằng 1, **tối đa một lượt đặt vé thành công** và số vé còn lại không âm.

Đây là **race condition ở mức logic** (check-then-act/TOCTOU). Ví dụ này dùng biến nguyên tử, nên không cần có “data race” theo nghĩa truy cập cùng một biến thường thiếu đồng bộ mà vẫn sai. Ngược lại, một `synchronized` hay transaction sai phạm vi cũng có thể để lộ race condition nếu ranh giới nguyên tử không bao trùm toàn bộ quyết định nghiệp vụ.

### Vì sao tăng số lượt chạy không đủ?

- Bộ lập lịch hệ điều hành có thể luôn chạy hết luồng A trước khi B bắt đầu. 10.000 lần lặp vẫn không tái hiện T0–T3.
- Một executor chỉ có một worker thực thi tác vụ tuần tự dù test tạo hàng nghìn `Future`.
- `Thread.sleep(100)` chỉ trì hoãn, không xác nhận luồng còn lại đã đọc dữ liệu hay đến một dòng cụ thể.
- JIT, tốc độ CPU, tải CI, GC và cấu hình máy khiến race khó hoặc dễ thấy khác nhau. Test xanh trên laptop không chứng minh không có tranh chấp.
- Nếu không gọi `Future.get()` hoặc chờ tất cả worker hoàn tất, lỗi phát sinh trong worker có thể bị bỏ qua và assertion chạy quá sớm.

Lặp nhiều lần vẫn hữu ích để **stress testing** sau khi đã có kiểm thử mục tiêu, nhưng không thay cho việc buộc chương trình đi qua cửa sổ tranh chấp cần chứng minh.

## 3. Thiết kế đúng một phép thử phối hợp luồng

Một kịch bản cơ bản dùng hai tầng tín hiệu:

```text
Luồng test
  | tạo pool 2 workers, ready=2, start=1
  |--------------------------------------|
  |                                      |
  |                              Worker A / Worker B
  |                              ready.countDown()
  |                              start.await()
  |
  | ready.await(timeout): cả hai đã sẵn sàng
  | start.countDown(): giải phóng cả hai
  |                                      |
  |                             Cả hai đọc remaining=1
  |                             checkpoint.await()  <--- CyclicBarrier(2)
  |                                      |
  |                             Cả hai tiếp tục cập nhật
  |
  | Future.get(timeout) cho từng worker
  | assert kết quả + trạng thái cuối
  | shutdown executor
```

Mỗi thành phần giải quyết một vấn đề khác nhau:

| Công cụ | Vai trò | Điều không được suy ra |
| --- | --- | --- |
| `CountDownLatch ready` | Đợi cả hai worker được khởi chạy và tới cổng bắt đầu | Chưa chứng minh cả hai đã đi vào đoạn nghiệp vụ |
| `CountDownLatch start` | Cho hai worker cùng cơ hội bắt đầu | Không bảo đảm hai CPU chạy chính xác đồng thời |
| `CyclicBarrier(2)` đặt **sau thao tác đọc** | Buộc cả hai thấy trạng thái ban đầu trước khi ai ghi | Chỉ kiểm chứng điểm đã instrument, không tự bao phủ mọi race khác |
| `Future.get(timeout)` | Chờ kết thúc và chuyển lỗi worker về luồng test | Không biến thao tác nghiệp vụ thành thread-safe |
| Assertion trên kết quả và trạng thái | Kiểm tra bất biến, ví dụ có đúng một người đặt vé thành công | Không tương đương bằng chứng hình thức cho mọi lịch thực thi |
| `shutdownNow()` trong `finally` | Giải phóng worker khi thành công hay thất bại | Không thay cho hủy/chờ hợp lý đối với tác vụ chặn I/O |

**Điểm quan trọng nhất:** `start` chỉ đồng bộ lúc bắt đầu. Với ví dụ kiểm tra–ghi, cần **checkpoint ngay sau lần đọc hợp lệ và trước cập nhật** thì mới tái hiện chắc chắn lỗi mong muốn.

Không đặt checkpoint “hai luồng phải cùng chạm tới” bên trong critical section vốn được khóa chỉ cho một luồng vào: luồng thứ hai không thể tới barrier, làm test chờ đến timeout. Đối với thuật toán dùng lock đúng, có thể phối hợp ở cổng vào hoặc giữ luồng thứ nhất trong critical section bằng latch riêng rồi xác nhận luồng thứ hai bị chặn.

## 4. Ví dụ Java 17 chạy được: tái hiện lỗi và kiểm chứng bản sửa

Ví dụ dưới đây chạy độc lập bằng JDK 17+, không phụ thuộc Spring. Hai implementation đều bắt đầu với `remaining=1`. Bản sai dùng `get()` rồi `decrementAndGet()`; bản đúng dùng vòng lặp `compareAndSet()` (CAS). Cả hai có barrier thử nghiệm sau lần đọc hợp lệ đầu tiên để bảo đảm cùng đi qua cửa sổ tranh chấp.

Tạo tệp `CoordinatedRaceDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class CoordinatedRaceDemo {
    private static final long WAIT_SECONDS = 10;

    private static void meetAfterRead(CyclicBarrier barrier) {
        try {
            barrier.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted at race checkpoint", e);
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new IllegalStateException("Race checkpoint was not reached", e);
        }
    }

    static final class UnsafeInventory {
        private final AtomicInteger remaining = new AtomicInteger(1);
        private final CyclicBarrier bothReadOne = new CyclicBarrier(2);

        boolean reserve() {
            int observed = remaining.get();
            if (observed <= 0) {
                return false;
            }

            // Test checkpoint: cả hai đã thấy số dương trước khi ai cập nhật.
            meetAfterRead(bothReadOne);
            remaining.decrementAndGet(); // Sai: không ràng buộc với observed.
            return true;
        }

        int remaining() {
            return remaining.get();
        }
    }

    static final class SafeInventory {
        private final AtomicInteger remaining = new AtomicInteger(1);
        private final CyclicBarrier bothReadOne = new CyclicBarrier(2);

        boolean reserve() {
            boolean checkpointReached = false;

            for (;;) {
                int observed = remaining.get();
                if (observed <= 0) {
                    return false;
                }

                // Chỉ chờ ở lần đọc hợp lệ đầu tiên. CAS thất bại được retry.
                if (!checkpointReached) {
                    checkpointReached = true;
                    meetAfterRead(bothReadOne);
                }

                if (remaining.compareAndSet(observed, observed - 1)) {
                    return true;
                }
            }
        }

        int remaining() {
            return remaining.get();
        }
    }

    private static int runTwoWorkers(Callable<Boolean> operation)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Start gate timed out");
                    }

                    return operation.call();
                }));
            }

            if (!ready.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers were not ready");
            }

            start.countDown();

            int approvals = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    approvals++;
                }
            }
            return approvals;
        } finally {
            start.countDown(); // Nếu setup lỗi, không để worker kẹt ở cổng.
            executor.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        UnsafeInventory unsafe = new UnsafeInventory();
        int unsafeApprovals = runTwoWorkers(unsafe::reserve);
        System.out.println("unsafe: approved=" + unsafeApprovals
                + ", remaining=" + unsafe.remaining());

        SafeInventory safe = new SafeInventory();
        int safeApprovals = runTwoWorkers(safe::reserve);
        System.out.println("safe: approved=" + safeApprovals
                + ", remaining=" + safe.remaining());

        if (unsafeApprovals != 2 || unsafe.remaining() != -1
                || safeApprovals != 1 || safe.remaining() != 0) {
            throw new AssertionError("Unexpected concurrency result");
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac CoordinatedRaceDemo.java
java CoordinatedRaceDemo
```

Khi cả hai worker qua checkpoint trước timeout, stdout được xác định:

```text
unsafe: approved=2, remaining=-1
safe: approved=1, remaining=0
```

### Tại sao kết quả không phụ thuộc may rủi?

- **Bản sai:** Worker đầu tiên đọc 1 rồi dừng ở `CyclicBarrier(2)`. Worker thứ hai bắt buộc cũng phải đọc 1 và tới barrier thì cả hai mới được trừ. Vì vậy cả hai đều trả `true`, tồn kho cuối `-1`.
- **Bản đúng:** Hai worker vẫn cùng đọc 1, nhưng chỉ một thao tác `compareAndSet(1, 0)` có thể thành công. Worker còn lại CAS thất bại, đọc lại giá trị 0 và trả `false`. Tồn kho cuối 0.
- `AtomicInteger` **không tự sửa** thiết kế check-then-act; chỉ CAS gộp điều kiện “giá trị vẫn là 1” với cập nhật thành một thao tác nguyên tử.
- `Future.get()` ném `ExecutionException` khi worker ném lỗi và `TimeoutException` nếu không hoàn tất đúng hạn, nên lỗi không bị giấu. Timeout ở đây phản ánh lỗi phối hợp/chạy chậm, không được lặng lẽ coi là “không có race”.

Checkpoint trong ví dụ là **thiết bị kiểm thử**. Không giữ một barrier có `parties=2` trong phiên bản production: một lời gọi đơn lẻ sẽ bị chặn. Trong production, bản CAS chỉ cần logic đọc–CAS–thử lại.

## 5. Đưa cách kiểm thử vào Spring Framework và JUnit 5

Spring không làm biến mất race condition của một bean singleton. Nhiều request, `@Async` invocation hoặc các listener khác nhau có thể gọi đồng thời cùng một bean. Phép thử có ý nghĩa phải gọi **bean thực sự được Spring quản lý**, đặt điều kiện cạnh tranh tại tầng nghiệp vụ đang cần bảo vệ, rồi kiểm tra bất biến.

Ví dụ dưới đây dùng `@SpringJUnitConfig` (JUnit Jupiter + Spring TestContext) tạo bean `BookingService`, tiêm một `RaceCheckpoint` chỉ dùng trong fixture test, và kiểm chứng CAS giữ đúng một lượt thành công. Nó chạy mà không cần server HTTP hoặc task scheduler, giúp test cô lập chính xác race window.

### Dự án Maven tối thiểu

```text
spring-race-test/
├── pom.xml
└── src/test/java/com/example/race/BookingConcurrencyTest.java
```

`pom.xml` (JDK 17+, bản Spring Boot 3.5.16 được dùng để quản lý phiên bản thư viện kiểm thử):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>spring-race-test</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

`src/test/java/com/example/race/BookingConcurrencyTest.java`:

```java
package com.example.race;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(BookingConcurrencyTest.TestConfig.class)
class BookingConcurrencyTest {
    @Autowired
    private BookingService service;

    @Test
    void onlyOneCallerCanReserveTheLastSeat() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Start timeout");
                    }
                    return service.reserve();
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS),
                    "Both workers must be ready");
            start.countDown();

            int approvals = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    approvals++;
                }
            }

            assertEquals(1, approvals, "Only one seat may be reserved");
            assertEquals(0, service.remaining(), "Stock must not be negative");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        RaceCheckpoint raceCheckpoint() {
            return new RaceCheckpoint();
        }

        @Bean
        BookingService bookingService(RaceCheckpoint checkpoint) {
            return new BookingService(checkpoint);
        }
    }

    static class RaceCheckpoint {
        private final CyclicBarrier bothReadOne = new CyclicBarrier(2);

        void afterPositiveRead() {
            try {
                bothReadOne.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted", e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("Race checkpoint failed", e);
            }
        }
    }

    static class BookingService {
        private final AtomicInteger seats = new AtomicInteger(1);
        private final RaceCheckpoint checkpoint;

        BookingService(RaceCheckpoint checkpoint) {
            this.checkpoint = checkpoint;
        }

        boolean reserve() {
            boolean reached = false;

            for (;;) {
                int observed = seats.get();
                if (observed <= 0) {
                    return false;
                }

                if (!reached) {
                    reached = true;
                    checkpoint.afterPositiveRead();
                }

                if (seats.compareAndSet(observed, observed - 1)) {
                    return true;
                }
            }
        }

        int remaining() {
            return seats.get();
        }
    }
}
```

Chạy ở thư mục dự án:

```bash
mvn test
```

Kết quả mong đợi: **một test thành công, không failure/error** (mã thoát 0). Output chi tiết tùy Maven/Surefire và môi trường; không phụ thuộc vào thứ tự hai worker. Để chứng minh test phát hiện bản lỗi, chỉ trong bản sao bài tập có thể thay khối CAS của `BookingService.reserve()` bằng:

```java
// Bản cố tình sai: sau barrier, cả hai trừ vô điều kiện.
seats.decrementAndGet();
return true;
```

Khi giữ nguyên checkpoint và assertions, cả hai worker đều đọc 1 rồi cùng trừ. Assertion `assertEquals(1, approvals)` sẽ thất bại vì thực tế `approvals=2`; biến `seats` xuống `-1`. Trả lại logic CAS thì test xanh.

**Chú ý về mã kiểm thử:** `RaceCheckpoint` là một hook của fixture, không phải quy tắc nghiệp vụ và không nên bắt production đợi hai request. Khi đưa sang dự án thật, có thể tiêm một interface `Checkpoint` mặc định no-op và cấu hình một implementation barrier chỉ trong test, hoặc dùng instrumentation/package-private hook để dừng đúng tại điểm đọc; mọi hook phải bị loại khỏi đường vận hành bình thường.

## 6. Chọn primitive theo đúng cửa sổ tranh chấp

Các API CountDownLatch, Semaphore và BlockingQueue đã được giải thích theo cơ chế ở bài “CountDownLatch, Semaphore hoặc hàng đợi để phối hợp kiểm thử xác định”. **Ở đây tiêu chí lựa chọn là có tạo được đúng interleaving nguy hiểm hay không**.

| Cơ chế | Vai trò riêng trong test tranh chấp | Cạm bẫy |
| --- | --- | --- |
| `CountDownLatch` | Cổng `ready/start` để biết hai worker sẵn sàng rồi cùng được phép bắt đầu | `ready` không chứng minh cả hai đã đọc trạng thái cần kiểm tra |
| `CyclicBarrier(2)` | Đặt **sau lần đọc, trước cập nhật** để buộc hai worker cùng thấy trạng thái cũ | Đặt bên trong lock chỉ cho một thread vào sẽ tự gây timeout |
| `Phaser` | Điều phối nhiều pha hoặc số bên tham gia thay đổi | Tăng độ phức tạp không cần thiết cho bài toán chỉ hai worker |
| `Semaphore` hoặc lock | Kiểm tra giới hạn truy cập vùng tới hạn | Không dùng barrier cần hai người bên trong vùng đã được khóa loại trừ |
| `Future.get(timeout)` | Đợi đủ tất cả participant và nhận lỗi worker | Không chỉ assert trạng thái trong lúc task còn dang dở |

**Khi kiểm thử fix dựa trên lock**, không yêu cầu hai thread đồng thời đi vào vùng khóa; thay vào đó, giữ worker A trong critical section ở một latch và chứng minh worker B chưa vào, rồi nhả A, chờ B hoàn tất. **Khi kiểm thử CAS**, hai worker có thể cùng đọc trước cập nhật nên barrier sau lần đọc là phù hợp.

Với mọi trường hợp, thread pool cần đủ worker để các participant đi tới checkpoint, mọi chờ phải có timeout và cleanup trong `finally`. Không suy ra từ một kịch bản đã kiểm tra rằng tất cả interleaving khác đều an toàn.

## 7. Các tình huống đặc thù của Spring

### Gọi bean thực, kể cả proxy

Nếu muốn kiểm thử `@Async`, `@Transactional` hay AOP, phải gọi qua **Spring bean/proxy** tương ứng. Tự gọi `this.someAsyncMethod()` từ cùng bean thường bỏ qua proxy `@Async` theo cấu hình proxy mặc định; test có thể đang chạy đồng bộ mà không nhận ra. `@SpringJUnitConfig`, `@SpringBootTest` hoặc test slice thích hợp cho phép lấy bean qua `@Autowired`.

Với `@Async`, cấu hình `TaskExecutor` có **ít nhất số worker cần chạm barrier**. Nếu chỉ có một luồng xử lý, worker đầu tiên tới barrier và chờ người thứ hai đang nằm trong queue: test timeout. Dùng kết quả `Future`/`CompletableFuture` thực sự để chờ kết thúc và nhận lỗi; không đánh giá thành công chỉ từ việc method đã trả về trước khi công việc bất đồng bộ hoàn tất.

### `@Scheduled` không đồng nghĩa đang chạy song song

Một `@Scheduled` phương thức có thể được thực thi tuần tự nếu scheduler chỉ có một thread; gọi lại lịch 100 lần vẫn chưa tạo contention. Với lỗi tranh chấp trong logic nghiệp vụ, hãy kiểm thử service bên dưới bằng các worker độc lập như ví dụ. Nếu yêu cầu kiểm tra scheduler thật, kiểm soát cấu hình `TaskScheduler`, số worker, cách trigger, `@Async` và overlap, rồi kiểm tra số lần thực thi/ranh giới hoàn tất. Nhiều replica ứng dụng có thể kích hoạt cùng một job ở các JVM khác nhau; bài test một JVM không chứng minh khóa phân tán hoạt động.

### Transaction và database phải được đo đúng ranh giới

Test dùng database có thể gặp race giữa **hai transaction và hai connection**. Khi đó:

1. Tạo hai worker, mỗi worker đi qua proxy service có `@Transactional` hoặc tự mở `TransactionTemplate` **trong thread của worker**.
2. Chuẩn bị dữ liệu sạch (ví dụ tồn kho 1), dùng checkpoint nếu an toàn để hai transaction cùng đi tới trạng thái đáng kiểm tra, rồi cho chúng hoàn tất.
3. Chờ kết quả đầy đủ (kể cả exception), đọc lại trạng thái đã commit bằng một transaction riêng và kiểm tra bất biến: tồn kho không âm, không hai lượt đặt cùng khóa duy nhất, số bản ghi đúng.
4. Dọn dữ liệu theo test case/transaction riêng. Không giả định `@Transactional` trên **method test** sẽ tự bao trùm các thread do `ExecutorService` tạo: transaction Spring thường gắn với thread hiện tại; rollback của test thread không tự rollback công việc đã commit ở worker.

Đặc biệt, **không đặt barrier sau khi một transaction đã giữ lock độc quyền ngăn transaction kia tiến tới cùng điểm**, ví dụ sau một `SELECT ... FOR UPDATE` theo cấu hình DB phù hợp. Khi đó chỉ một worker tới checkpoint, test tự tạo deadlock/time-out; hãy phối hợp trước bước lấy lock hoặc kiểm chứng hành vi chờ khóa theo cách riêng.

Giải pháp đúng ở mức database có thể là câu `UPDATE inventory SET remaining = remaining - 1 WHERE id = ? AND remaining > 0`, rồi thành công khi số hàng được cập nhật bằng 1; optimistic locking với cột version và retry được kiểm soát; pessimistic locking; hoặc unique constraint đảm bảo không bán cùng một vé. `synchronized` trong một Spring bean **không đủ** bảo vệ dữ liệu trên nhiều instance/JVM.

### Spring test context và trạng thái chia sẻ

`@SpringJUnitConfig` có thể cache context, và bean singleton có thể giữ trạng thái giữa các test. Không để test sau kế thừa stock đã bị trừ, barrier đã hỏng hoặc executor chưa đóng. Tạo fixture mới, reset đúng chỗ hoặc dùng context riêng khi thật sự cần; tránh phụ thuộc thứ tự test. EntityManager/JPA persistence context thông thường không được chia sẻ tùy tiện giữa các worker vì không thread-safe.

## 8. Mẫu thiết kế một bài test tốt

Một bài kiểm thử contention có thể được đặc tả thành bảy câu hỏi rõ ràng:

| Câu hỏi | Ví dụ câu trả lời |
| --- | --- |
| Bất biến nào phải đúng? | Chỉ một lời gọi được lấy vé cuối cùng, remaining ≥ 0 |
| Ai là participant? | Hai lời gọi độc lập qua cùng bean/proxy hoặc hai transaction |
| Cửa sổ nguy hiểm ở đâu? | Sau đọc `remaining=1`, trước khi ghi giảm |
| Làm sao chứng minh cả hai đã ở cửa sổ đó? | Barrier với hai participant sau lần đọc hợp lệ |
| Làm sao giải phóng và chờ? | Ready/start latch; `Future.get(10, SECONDS)` |
| Assertion kiểm tra gì? | Số lượt thành công, state cuối, bản ghi DB nếu có |
| Dọn và timeout ra sao? | `finally` release latch, shutdown pool, fail rõ khi không tới gate |

Một test phải **thất bại đáng tin khi đưa lại lỗi cũ** (red), và **thành công khi áp dụng bản sửa** (green). Đây là kiểm chứng quan trọng hơn việc test chỉ tình cờ xanh 1.000 lần.

Không chỉ assert số cuối: với thao tác tạo đơn hàng, đôi khi số tồn kho bằng 0 nhưng lại có hai đơn đặt thành công, hoặc một exception bị nuốt. Hãy kiểm tra đồng thời **kết quả trả về, số side-effect đã commit và bất biến dữ liệu**.

## 9. Lỗi phổ biến và cách tránh

1. **Chỉ tăng `@RepeatedTest` hoặc lặp một vòng `for` lớn:** xác suất gặp race tăng nhưng không điều khiển được interleaving; dùng checkpoint ở điểm có ý nghĩa rồi mới thêm stress test.
2. **Dùng `Thread.sleep()` làm điều kiện đồng bộ:** máy chậm/nhanh khiến test lúc đỏ lúc xanh; dùng latch/barrier với timeout và tín hiệu xác nhận chính xác.
3. **Pool nhỏ hơn số người của barrier:** executor một luồng không thể phục vụ checkpoint cần hai người cùng đến; kiểm tra pool size và queue.
4. **Assertion chạy khi worker chưa kết thúc:** phải chờ toàn bộ `Future` và xử lý exception; đừng chỉ chờ `start.countDown()`.
5. **Nuốt exception trong worker:** test không thấy nguyên nhân; để `Future.get()` chuyển lỗi ra test hoặc thu thập lỗi một cách có assertion.
6. **Không timeout:** test và CI có thể treo mãi; mỗi chờ đồng bộ quan trọng nên có hạn chờ và thông báo.
7. **Barrier đặt bên trong lock đúng:** người thứ hai không thể vào, tạo timeout giả; đặt barrier ở điểm hai người thật sự có thể đồng thời tới.
8. **Gọi trực tiếp đối tượng thay vì Spring proxy:** `@Async` và `@Transactional` có thể không được áp dụng; tiêm và gọi bean được quản lý.
9. **Chỉ khóa trong một JVM rồi tin an toàn toàn cluster:** áp dụng constraint, transaction hoặc distributed coordination theo phạm vi thật.
10. **Dùng chung trạng thái giữa test methods:** context singleton và dữ liệu DB cũ làm test không lặp lại; cô lập fixture, có cleanup và assertion về trạng thái đầu.
11. **Không đo trạng thái sau commit:** phép đọc trong cache/persistence context có thể chưa phản ánh transaction khác; kiểm tra từ transaction đọc mới khi cần.
12. **Gọi một test đơn lẻ là “chứng minh thread-safe tuyệt đối”:** test tái hiện và chặn regression của interleaving đã chọn; cần thêm kiểm tra thuật toán, invariants khác, stress và quan sát production.

## 10. Bài tập thực hành

1. **Red–green:** chạy `CoordinatedRaceDemo`, xác nhận tồn kho `-1` ở bản sai; thay CAS bằng logic tách rời và quan sát assertion của test Spring bị đỏ; sửa lại CAS để test xanh.
2. **Thêm ba người tranh hai vé:** điều chỉnh barrier cho ba participant và tồn kho 2; kiểm tra số winner chính xác và giải thích vì sao bản CAS cần vòng lặp retry khi mất CAS nhưng vẫn còn vé.
3. **Kiểm tra lock:** viết biến thể dùng `ReentrantLock` và một cặp latch `entered/release` để giữ worker đầu tiên trong vùng khóa. Đảm bảo worker thứ hai không cùng vào critical section; không đặt barrier cần hai người bên trong khóa.
4. **Kiểm tra transaction:** tạo bảng tồn kho với số lượng 1, khởi chạy hai transaction độc lập và so sánh `SELECT` rồi `UPDATE` sai với một câu `UPDATE ... WHERE remaining > 0` nguyên tử. Assert cả số lượt thành công lẫn hàng dữ liệu sau commit.
5. **Mở rộng observability:** khi một worker chờ quá timeout, ghi rõ nó đang ở cổng ready, barrier hay DB lock; dùng thông tin đó để phân biệt lỗi fixture với lỗi ứng dụng.

## Tài liệu chính thức

- Java SE 17 — `CountDownLatch`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- Java SE 17 — `CyclicBarrier`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html
- Java SE 17 — `Future` và `ExecutorService`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- Spring Framework — Testing, JUnit Jupiter và `@SpringJUnitConfig`: https://docs.spring.io/spring-framework/reference/testing/annotations/integration-junit-jupiter.html
- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework — Transaction Management: https://docs.spring.io/spring-framework/reference/data-access/transaction.html


</details>

- [Quay lại đầu trang](#back-to-top)
