<a id="back-to-top"></a>

# Lịch động và Trigger

## Menu
- [Trigger chọn thời điểm kế tiếp từ ngữ cảnh thực thi](#dynamic-scheduling-001)
- [TriggerContext theo dõi lần dự kiến, thực tế và hoàn tất](#dynamic-scheduling-002)
- [CronTrigger cho lịch theo biểu thức cron](#dynamic-scheduling-003)
- [PeriodicTrigger cho lịch chu kỳ](#dynamic-scheduling-004)
- [SchedulingConfigurer và ScheduledTaskRegistrar](#dynamic-scheduling-005)
- [Đăng ký, giữ ScheduledFuture và hủy lịch bằng mã](#dynamic-scheduling-006)
- [Cập nhật cấu hình không tự thay đổi lịch annotation đã đăng ký](#dynamic-scheduling-007)
- [Tránh đăng ký chồng lịch khi thay đổi cấu hình](#dynamic-scheduling-008)
- [Kiểm soát tính nhất quán khi nhiều luồng cùng cập nhật lịch](#dynamic-scheduling-009)

## <a id="dynamic-scheduling-001">Trigger chọn thời điểm kế tiếp từ ngữ cảnh thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Trigger chọn thời điểm kế tiếp từ ngữ cảnh thực thi trong Spring Framework

Một tác vụ định kỳ không phải lúc nào cũng nên chạy sau một khoảng thời gian cố định tính từ lúc ứng dụng khởi động. Có tác vụ cần **chờ 30 giây sau khi lần trước hoàn thành**, có tác vụ phải bám các mốc giờ dự kiến, có tác vụ muốn **giãn chu kỳ khi xử lý chậm**, hoặc dừng hẳn khi điều kiện nghiệp vụ không còn phù hợp. Trong Spring Framework, giao diện **`Trigger`** cho phép trả lời câu hỏi quan trọng: **“Lần thực thi kế tiếp nên được lên lịch tại `Instant` nào, dựa trên những lần chạy trước?”**

`org.springframework.scheduling.Trigger` là một giao diện có phương thức chính **`Instant nextExecution(TriggerContext context)`** trong Spring Framework 6.x. Scheduler cung cấp **`TriggerContext`** chứa đồng hồ và ba mốc lịch sử: **thời điểm đã lên lịch**, **thời điểm task thực sự bắt đầu**, **thời điểm task hoàn tất**. Trigger đọc thông tin này để trả về `Instant` của lượt tiếp theo hoặc `null` nếu không còn lần chạy nào.

Bài thuộc nhóm **Lịch động và Trigger** trong module **Spring Framework - concurrency**, tập trung vào cách **chọn thời điểm kế tiếp từ ngữ cảnh thực thi**, cách viết custom Trigger, so sánh chiến lược completion-based và schedule-based, tích hợp `TaskScheduler`, kiểm thử và các bẫy vận hành. Không đồng nhất “quyết định lịch tiếp theo” với “đảm bảo một job đã thực thi thành công”.

## 1. Mục tiêu học tập

Sau khi đọc bài này, bạn sẽ có thể:

- Phân biệt `TaskScheduler` (thực hiện registration và dispatch) với `Trigger` (tính `Instant` kế tiếp).
- Hiểu hợp đồng `Trigger.nextExecution(TriggerContext)`, bao gồm **trả `null` để kết thúc lịch**.
- Phân biệt `lastScheduledExecution()`, `lastActualExecution()`, `lastCompletion()` và `getClock()`.
- Xử lý các mốc `null` khi task chưa chạy lần nào, không bị `NullPointerException`.
- Tính lịch tiếp theo dựa trên completion để tránh bám lịch quá gấp, hoặc dựa trên scheduled time để bảo toàn nhịp dự kiến.
- Biết vì sao `CronTrigger` mặc định là **lenient/completion-based** và `CronTrigger.forFixedExecution` là **schedule-based** trong Spring 6.1.3+.
- Tạo custom `Trigger` dạng bất biến, sử dụng `Duration`, `Instant` và `Clock`.
- Đăng ký task bằng `TaskScheduler.schedule(Runnable, Trigger)` hoặc `SchedulingConfigurer`.
- Viết unit test với `SimpleTriggerContext` và đồng hồ cố định thay vì chờ real time.
- Phân biệt lỗi task, timeout, cancellation, thay đổi cấu hình, nhiều replica và sự bền vững của job.

## 2. Trigger và TaskScheduler: mỗi bên làm một việc

`TaskScheduler` là abstraction scheduling của Spring. Ngoài các phương thức như `scheduleAtFixedRate(...)`, `scheduleWithFixedDelay(...)` và `schedule(Runnable, Instant)`, nó có:

~~~java
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);
~~~

Đây là **chữ ký API dạng minh họa** từ Spring; tùy trigger, kết quả có thể là `null` nếu không có thời điểm chạy kế tiếp. Khi sử dụng `Trigger`, scheduler hỏi trigger “khi nào chạy tiếp”, sau đó chịu trách nhiệm đăng ký/điều phối tác vụ qua executor phía dưới.

~~~text
Ứng dụng đăng ký task với TaskScheduler
                 |
                 v
        Trigger.nextExecution(ctx)
                 |
       +---------+----------+
       |                    |
   trả Instant            trả null
       |                    |
       v                    v
Scheduler lên lịch     Không đăng ký thêm
       |
       v
  Đến thời điểm dự kiến
       |
       v
  Worker thật sự bắt đầu Runnable
       |
       v
  Runnable kết thúc
       |
       v
  Cập nhật ngữ cảnh thực thi
       |
       +------> Trigger.nextExecution(ctx) cho lần sau
~~~

Sơ đồ diễn tả nguyên tắc. Chi tiết scheduling phụ thuộc implementation, error handler và cách Trigger được adapter gọi. **Trigger không tự tạo thread**, không gửi HTTP và không thực hiện transaction. Nó chỉ tính thời điểm. Phần Runnable/Callable chứa công việc nghiệp vụ, còn scheduler quản lý thời điểm dispatch.

### Khi nào nên chọn một Trigger?

- Muốn tính lượt chạy kế tiếp dựa trên **lúc lần trước hoàn tất**.
- Muốn điều chỉnh delay khi thời gian chạy biến động.
- Muốn bám một lịch cố định dù worker bắt đầu muộn.
- Muốn dùng cron có lựa chọn **lenient** hoặc **fixed** rõ ràng.
- Muốn dừng chuỗi scheduling bằng cách trả `null`.
- Muốn dùng lịch nâng cao do ứng dụng định nghĩa, không diễn đạt thuận tiện bằng một `@Scheduled(fixedDelay=...)`.

Nếu chỉ cần mỗi 60 giây hoặc sau mỗi lần hoàn thành đợi 30 giây, hãy cân nhắc các API fixed-rate/fixed-delay đơn giản trước. Custom Trigger đem lại linh hoạt nhưng tăng trách nhiệm kiểm thử.

## 3. TriggerContext chứa những thông tin gì?

Trong Spring Framework 6.x, `TriggerContext` cung cấp:

~~~java
Clock getClock();
Instant lastScheduledExecution();
Instant lastActualExecution();
Instant lastCompletion();
~~~

Đây là **những chữ ký API rút gọn**. Ba phương thức lấy lịch sử có thể trả `null` khi chưa có lượt chạy nào trước đó.

| Phương thức | Ý nghĩa | Không nên nhầm với |
| --- | --- | --- |
| `getClock()` | Đồng hồ mà scheduler/trigger dùng để tính thời điểm | Không bắt buộc giống thời gian máy tính test nếu có clock được cấu hình |
| `lastScheduledExecution()` | `Instant` được scheduler lên kế hoạch cho lần thực thi trước | Không bảo đảm worker thực tế bắt đầu đúng mốc này |
| `lastActualExecution()` | `Instant` worker thực tế bắt đầu lần thực thi trước | Không phải thời điểm hoàn thành |
| `lastCompletion()` | `Instant` lần thực thi trước hoàn tất | Không cho biết lượt đó thành công hay thất bại |
| Cả ba giá trị lịch sử bằng `null` | Chưa ghi nhận lần thực thi trước trong context | Không có nghĩa toàn bộ ứng dụng chưa từng chạy job sau restart |

### Ví dụ timeline

Giả sử đơn vị là UTC:

~~~text
09:00:00  lần trước được lên lịch (scheduled)
     |
09:00:04  lần trước thật sự bắt đầu (actual)
     |
09:00:13  lần trước kết thúc (completion)
     |
09:00:14  trigger được hỏi thời điểm mới, theo clock giả định

|-- 4 giây trễ khởi động --|
           |-- 9 giây xử lý --|
~~~

Nếu muốn nhịp **30 giây theo lịch trước**, mốc dự kiến kế tiếp là `09:00:30`.

Nếu muốn **30 giây sau lúc bắt đầu thật**, mốc là `09:00:34`.

Nếu muốn **30 giây sau lúc hoàn tất**, mốc là `09:00:43`.

Ba kết quả đều có thể đúng về mặt toán học nhưng phục vụ các yêu cầu **khác nhau**. Chọn sai mốc khiến job bị dồn, chạy muộn, hoặc không bao giờ bắt kịp lịch mong muốn.

### Vì sao phải phân biệt scheduled và actual?

Executor có thể bận, queue nhiều task hoặc hệ điều hành không cấp CPU đúng lúc. Scheduler yêu cầu chạy tại 09:00:00 nhưng worker bắt đầu 09:00:04, tạo **start lag = 4 giây**. Nếu tính từ `lastActualExecution`, bạn đã đưa trễ scheduler vào mốc thời gian của các lượt sau. Nếu tính từ `lastScheduledExecution`, bạn cố giữ nhịp dự kiến bất chấp một lần bắt đầu muộn.

Tuy nhiên, “giữ nhịp” không đồng nghĩa scheduler có đủ capacity để chạy đúng mốc hoặc tự chạy nhiều lượt song song. Một task có thể được xếp lịch cho một `Instant` đã nằm trong quá khứ; tùy scheduler, nó có thể được dispatch gần như ngay lập tức, tạo hiện tượng **catch-up**.

## 4. Hợp đồng Trigger.nextExecution(TriggerContext)

Giao diện Spring 6.x:

~~~java
package org.springframework.scheduling;

import java.time.Instant;

public interface Trigger {
    Instant nextExecution(TriggerContext triggerContext);
}
~~~

Đoạn này **minh họa chữ ký API**, không phải đề nghị tự khai báo lại class Spring trong dự án. Phương thức `nextExecution(...)` trả về:

- Một `Instant`: yêu cầu scheduler lên lịch lần chạy tiếp theo tại mốc đó.
- `null`: **không có lần chạy tiếp theo** cho lần đăng ký Trigger hiện tại.

Nếu return `Instant` nằm **trước hoặc bằng hiện tại**, tùy scheduler, task có thể được chạy ngay hoặc gần ngay. Nếu Trigger liên tục trả thời điểm trong quá khứ, bạn có thể vô tình tạo luồng công việc dồn dập và quá tải.

### Không phải API “sleep trong Trigger”

Một Trigger phải tính toán nhanh và trả kết quả. Không thực hiện:

~~~java
// Không dùng trong nextExecution():
Thread.sleep(30_000);
return Instant.now();
~~~

Đây là **mã sai ý định** trong một hàm có thể ném `InterruptedException`. Cách này chiếm thread thực hiện scheduling calculation, làm chậm registration và các task khác, trong khi scheduler vốn đã có trách nhiệm đợi đến thời điểm được trả về.

Đúng hơn:

~~~java
Instant base = context.getClock().instant();
return base.plus(Duration.ofSeconds(30));
~~~

Đây là **fragment Spring Trigger** dùng `context` là `TriggerContext`. Chỉ tính toán một `Instant`, không block.

### Đừng phụ thuộc duy nhất vào System.currentTimeMillis()

Vì Spring cung cấp `context.getClock()`, hãy ưu tiên nó thay vì gọi thẳng `Instant.now()` hoặc `System.currentTimeMillis()` ở mọi chỗ. Việc dùng clock được truyền giúp:

1. Unit test có thể cố định thời gian.
2. Tránh mismatch khi scheduler được cấu hình clock/timezone riêng.
3. Giữ các phép cộng/trừ dựa trên `Instant` và `Duration` rõ ràng.
4. Hạn chế race/time coupling khi môi trường chạy và test có khác biệt.

`Clock` không làm đồng hồ hệ thống miễn nhiễm với NTP shift; nó đơn giản giúp sự phụ thuộc vào thời gian được biểu diễn và tiêm vào rõ ràng.

## 5. Ví dụ Java 17+ chạy độc lập: ba cách chọn mốc kế tiếp

Trước khi tích hợp Spring, chương trình Java thuần sau minh họa chính xác phép tính thời điểm. Nó sử dụng `Clock.fixed` nên output **không phụ thuộc thời gian chạy**. `Snapshot` là một mô hình tự tạo gồm bốn trường; nó **không phải `TriggerContext` của Spring**. Khi chuyển vào Spring, ta dùng đúng `TriggerContext` thật.

Tệp **TriggerTimelineDemo.java**:

~~~java
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public final class TriggerTimelineDemo {
    private static final Duration PERIOD = Duration.ofSeconds(30);

    record Snapshot(
            Clock clock,
            Instant lastScheduled,
            Instant lastActual,
            Instant lastCompletion) {
    }

    static Instant nextFromCompletion(Snapshot s) {
        Instant base = s.lastCompletion() != null
                ? s.lastCompletion()
                : s.clock().instant();
        return base.plus(PERIOD);
    }

    static Instant nextFromScheduled(Snapshot s) {
        Instant base = s.lastScheduled() != null
                ? s.lastScheduled()
                : s.clock().instant();
        return base.plus(PERIOD);
    }

    static Instant nextFromActual(Snapshot s) {
        Instant base = s.lastActual() != null
                ? s.lastActual()
                : s.clock().instant();
        return base.plus(PERIOD);
    }

    static Instant adaptiveNext(Snapshot s) {
        if (s.lastActual() == null || s.lastCompletion() == null) {
            return s.clock().instant().plus(PERIOD);
        }
        Duration execution = Duration.between(
                s.lastActual(), s.lastCompletion());
        Duration coolDown = execution.compareTo(Duration.ofSeconds(8)) > 0
                ? Duration.ofMinutes(1)
                : PERIOD;
        return s.lastCompletion().plus(coolDown);
    }

    static Instant nextUntilThreeCompletions(
            Snapshot s, int completedRuns) {
        return completedRuns >= 3 ? null : nextFromCompletion(s);
    }

    public static void main(String[] args) {
        Clock clock = Clock.fixed(
                Instant.parse("2026-01-01T09:00:00Z"), ZoneOffset.UTC);

        Snapshot firstRun = new Snapshot(clock, null, null, null);
        Snapshot finished = new Snapshot(
                clock,
                Instant.parse("2026-01-01T09:00:00Z"),
                Instant.parse("2026-01-01T09:00:04Z"),
                Instant.parse("2026-01-01T09:00:13Z"));

        System.out.println(
                "first=" + nextFromCompletion(firstRun));
        System.out.println(
                "scheduledBased=" + nextFromScheduled(finished));
        System.out.println(
                "actualBased=" + nextFromActual(finished));
        System.out.println(
                "completionBased=" + nextFromCompletion(finished));

        System.out.println("startLagSeconds="
                + Duration.between(
                        finished.lastScheduled(),
                        finished.lastActual()).toSeconds());
        System.out.println("executionSeconds="
                + Duration.between(
                        finished.lastActual(),
                        finished.lastCompletion()).toSeconds());

        System.out.println(
                "adaptiveNext=" + adaptiveNext(finished));
        System.out.println("stopAfterThree="
                + nextUntilThreeCompletions(finished, 3));
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac TriggerTimelineDemo.java
java TriggerTimelineDemo
~~~

Stdout của chương trình:

~~~text
first=2026-01-01T09:00:30Z
scheduledBased=2026-01-01T09:00:30Z
actualBased=2026-01-01T09:00:34Z
completionBased=2026-01-01T09:00:43Z
startLagSeconds=4
executionSeconds=9
adaptiveNext=2026-01-01T09:01:13Z
stopAfterThree=null
~~~

**Vì sao kết quả xác định?** Đồng hồ được đặt cố định tại 09:00:00 UTC. Phép cộng `Duration.ofSeconds(30)` vào từng mốc cho các kết quả khác nhau. Tác vụ chạy 9 giây, vượt ngưỡng 8 giây của mô hình, nên `adaptiveNext` chọn nghỉ 1 phút sau lúc hoàn thành 09:00:13. Hàm `nextUntilThreeCompletions` trả `null` khi bộ đếm do caller cung cấp đạt 3; đây là mô hình minh họa **chính sách kết thúc**, không tự quản lý thread hay định nghĩa một scheduler đầy đủ.

Không cần `Thread.sleep` và không có output thay đổi giữa các máy do scheduler. Trên Spring, unit test có thể mô phỏng cùng các mốc bằng `SimpleTriggerContext` và `Clock.fixed`.

## 6. Custom Trigger đơn giản: chờ sau lần hoàn tất

Một Trigger thông dụng chọn mốc `lastCompletion + delay`. Tệp **CompletionDelayTrigger.java**:

~~~java
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

public final class CompletionDelayTrigger implements Trigger {
    private final Duration delay;

    public CompletionDelayTrigger(Duration delay) {
        this.delay = Objects.requireNonNull(delay, "delay");
        if (delay.isZero() || delay.isNegative()) {
            throw new IllegalArgumentException(
                    "Delay must be positive");
        }
    }

    @Override
    public Instant nextExecution(TriggerContext context) {
        Instant completed = context.lastCompletion();
        Instant base = completed != null
                ? completed
                : context.getClock().instant();
        return base.plus(delay);
    }
}
~~~

Đây là **một class Spring đầy đủ về cấu trúc**, cần `spring-context` trong classpath; không biên dịch độc lập bằng `javac` thuần. Cách hoạt động:

1. **Lần đầu:** `lastCompletion() == null`, nên lấy `context.getClock().instant()` cộng delay. Chúng ta chủ động chọn “lần đầu cũng chờ một delay”, thay vì chạy ngay.
2. **Từ lần hai:** lấy thời điểm hoàn tất thực tế trước đó cộng delay.
3. **Chậm ở lần trước:** nếu job chạy 5 phút thay vì 10 giây, lịch mới vẫn chờ delay sau khi hoàn tất, nên không lập tức cố bắt kịp các mốc đã bỏ.
4. **Lỗi tác vụ:** `TriggerContext` chỉ lưu mốc completion, không lưu flag thành công/thất bại; Trigger này vẫn tính delay bình thường. Muốn backoff dựa trên failure thật cần có nguồn outcome riêng.

Có một đánh đổi về thời gian: delay sau completion và thời gian chạy sẽ **cộng lại** thành chu kỳ giữa các lần bắt đầu. Nếu task chạy 2 phút và delay 30 giây, lần sau có thể bắt đầu khoảng 2 phút 30 giây sau lần trước (chưa kể queue lag). Không nên đặt tên metric `fixedRate30s` cho behavior này.

### Tại sao tính từ completion mà không phải actual?

Dựa trên `lastActualExecution` cộng delay có thể đặt lần sau vào một thời điểm **trước khi task cũ kết thúc** nếu task chạy lâu hơn delay. Scheduler có thể cố chạy tiếp ngay sau khi body cũ kết thúc. Dựa trên completion sẽ giữ được một khoảng nghỉ giữa hai lần làm việc đối với một registration thông thường.

## 7. Schedule-based Trigger: giữ nhịp dự kiến

Đối với tác vụ cần bảo toàn các mốc dự kiến thay vì chờ sau khi hoàn tất, có thể dùng `lastScheduledExecution`:

Tệp **ScheduledCadenceTrigger.java**:

~~~java
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

public final class ScheduledCadenceTrigger implements Trigger {
    private final Duration period;

    public ScheduledCadenceTrigger(Duration period) {
        this.period = Objects.requireNonNull(period, "period");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException(
                    "Period must be positive");
        }
    }

    @Override
    public Instant nextExecution(TriggerContext context) {
        Instant lastScheduled = context.lastScheduledExecution();
        Instant base = lastScheduled != null
                ? lastScheduled
                : context.getClock().instant();
        return base.plus(period);
    }
}
~~~

Đây là **Spring Trigger class** với một chính sách rõ: lần đầu đợi một period, những lần sau cộng period vào thời điểm đã lên lịch. Nếu worker bắt đầu muộn hoặc task kéo dài, `base.plus(period)` có thể nằm trong quá khứ. Lúc đó scheduler có thể lập tức gửi lượt tiếp theo để bắt nhịp.

**Rủi ro:** nếu job kéo dài hơn period trong nhiều lượt, các mốc cũ liên tục bị bỏ lỡ; lịch schedule-based có thể tạo dòng thực thi nối tiếp gần như không nghỉ và đẩy áp lực lên I/O/DB. Tùy nhu cầu, thêm chính sách skip/cap hoặc dùng cron lenient.

### Điểm khác biệt với fixed-rate của JDK

Một custom Trigger dựa trên `lastScheduledExecution` không phải lời cam kết rằng mọi scheduler hoặc app server có semantics giống `ScheduledExecutorService.scheduleAtFixedRate` trong mọi tình huống. Đối với lịch cố định thuần túy, `TaskScheduler.scheduleAtFixedRate(...)` thường đơn giản hơn. Custom Trigger hữu ích khi cần quy tắc riêng về backoff, giờ làm việc hoặc dữ liệu lịch sử.

## 8. Cách dùng lastActualExecution để đo độ trễ hoặc điều chỉnh

`lastActualExecution` có ý nghĩa khi cần biết **scheduler trễ bao lâu**:

~~~java
Instant expected = context.lastScheduledExecution();
Instant actual = context.lastActualExecution();

if (expected != null && actual != null) {
    Duration lag = Duration.between(expected, actual);
    // Ghi metric hoặc tính chính sách điều chỉnh nếu phù hợp.
}
~~~

Đây là **fragment trong Trigger**, cần `Instant`, `Duration` và `context` kiểu `TriggerContext`. `lag` có thể không dương trong các tình huống clock/instrumentation đặc thù; không tự suy ra rằng thời gian phải luôn tăng nếu đồng hồ đã được điều chỉnh.

Nếu cần giảm tải khi worker đã bị trễ:

- Dựa trên `Duration.between(lastScheduled, lastActual)` để phát hiện hiện tượng start lag.
- Kết hợp với `lastCompletion` để biết execution duration.
- Tính delay lớn hơn hoặc skip những mốc cũ khi lag vượt ngưỡng.
- Log/metric lại quyết định để operator hiểu vì sao lịch không chạy đúng mốc.

Tuy nhiên, **TriggerContext không chứa queue size, pool utilization, exception, retry count hay result**. Muốn quyết định dựa vào các thông tin đó, phải có nguồn dữ liệu riêng đáng tin (metrics/atomic state/job store), tránh đọc một biến mutable không đồng bộ giữa threads.

## 9. Adaptive Trigger: điều chỉnh thời gian chờ theo độ dài lần chạy

Giả sử nghiệp vụ muốn:

- Nếu lần chạy trước hoàn thành trong tối đa 20 giây: đợi 1 phút rồi chạy tiếp.
- Nếu kéo dài hơn 20 giây: đợi 5 phút để giảm áp lực.
- Với lần đầu chưa có history: đợi 1 phút.

Tệp **AdaptiveCompletionTrigger.java**:

~~~java
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

public final class AdaptiveCompletionTrigger implements Trigger {
    private static final Duration SLOW_THRESHOLD =
            Duration.ofSeconds(20);
    private static final Duration FAST_DELAY =
            Duration.ofMinutes(1);
    private static final Duration SLOW_DELAY =
            Duration.ofMinutes(5);

    @Override
    public Instant nextExecution(TriggerContext context) {
        Instant actual = context.lastActualExecution();
        Instant completed = context.lastCompletion();

        if (actual == null || completed == null) {
            return context.getClock().instant().plus(FAST_DELAY);
        }

        Duration elapsed = Duration.between(actual, completed);
        Duration delay = elapsed.compareTo(SLOW_THRESHOLD) > 0
                ? SLOW_DELAY
                : FAST_DELAY;

        return completed.plus(delay);
    }
}
~~~

Đây là **một Spring Trigger class**; cần Spring Context dependency và package/import tương ứng. Nó là ví dụ **adaptive theo duration**, không phải circuit breaker hoặc retry-on-failure. Tác vụ chạy nhanh nhưng trả lỗi vẫn được gắn delay 1 phút, bởi `TriggerContext` không nói lượt đó thành công hay thất bại.

### Khi nào không nên dùng chính sách này?

- Nếu job cần đảm bảo tối đa một lượt mỗi ngày theo business date, delay theo duration làm thay đổi lịch nghiệp vụ.
- Nếu SLA đòi chạy tại mốc cố định, kéo dài delay có thể vi phạm SLA.
- Nếu task giữ worker vì I/O treo, việc chỉ điều chỉnh thời điểm **sau khi hoàn thành** không giải quyết được lần hiện tại bị treo; phải dùng timeout.
- Nếu áp lực đến từ nhiều instance, một Trigger adaptive **trong một JVM** không thay thế distributed backpressure/coordination.

### Backoff vì lỗi thì thiết kế ra sao?

Muốn tăng delay sau exception, cần biết trạng thái error **ở ngoài TriggerContext**. Một cách là wrapper task ghi outcome vào state thread-safe rồi Trigger đọc state đó sau completion; có thể dùng `AtomicReference<Result>` hoặc job state trong database. Nhưng phải xác định:

1. Outcome thuộc **lượt chạy nào**, tránh đọc nhầm.
2. Visibility giữa thread body và scheduler callback.
3. Exception đã được Spring error handler xử lý thế nào.
4. Trường hợp JVM restart mất state RAM.
5. Multiple replicas có chia sẻ policy hay không.

Không gọi `context.lastCompletion()==null` là “lần trước bị lỗi”: null thường đơn giản nghĩa là chưa có lịch sử.

## 10. CronTrigger cũng dùng TriggerContext

`CronTrigger` là implementation Spring của `Trigger` dựa trên biểu thức cron. Ví dụ:

~~~java
import org.springframework.scheduling.support.CronTrigger;

CronTrigger cron =
        new CronTrigger("0 */5 * * * *");
~~~

Đây là **fragment Java trong Spring application**, dùng cron **sáu trường**: giây, phút, giờ, ngày trong tháng, tháng, thứ. Nó không phải cron Unix năm trường.

Điểm ít được chú ý: Spring có hai kiểu chọn mốc cơ sở cho cron:

1. **Lenient execution:** tính lịch tiếp theo dựa trên **completion time** của lần trước. Có thể bỏ qua những mốc cron đã xảy ra trong lúc lần trước vẫn đang chạy.
2. **Fixed execution:** tính lịch tiếp theo dựa trên **last scheduled time**. Có thể cố gắng bù những lần cron đã bị lỡ; chúng có thể chạy trễ sau khi task trước kết thúc.

### Lenient — hành vi mặc định của CronTrigger

~~~java
CronTrigger lenient =
        CronTrigger.forLenientExecution("0 */5 * * * *");
~~~

Với Spring **6.1.3+**, factory `forLenientExecution` thể hiện rõ lựa chọn này. Constructor `new CronTrigger(expression)` có semantics tương đương lenient theo Javadoc hiện hành. Nếu một job chạy lâu, các mốc cron nằm trong thời gian ấy có thể **bị skip**.

Ví dụ về ý nghĩa:

~~~text
cron mỗi phút tại giây 0
10:00:00 scheduled -> body chạy đến 10:02:10
lenient lấy completion 10:02:10 làm base
     -> mốc cron kế tiếp 10:03:00
     -> không cố chạy bù 10:01 và 10:02
~~~

Đây là **sơ đồ minh họa** giả định thời điểm start đúng lịch và trigger normal. Nếu scheduler bận, hiện tượng skip/late có thể phụ thuộc loại scheduler và số thread.

### Fixed — bám theo scheduled timestamp

~~~java
CronTrigger fixed =
        CronTrigger.forFixedExecution("0 */5 * * * *");
~~~

Factory này có trong Spring **6.1.3+**. Khi task chạy lâu, trigger dựa trên `lastScheduledExecution` để giữ các mốc cron dự kiến. Theo hợp đồng của Spring, các lượt bị lỡ không bị bỏ qua mà được lên lịch bù, có thể chạy muộn.

~~~text
cron mỗi phút tại giây 0
10:00:00 scheduled -> body chạy đến 10:02:10
fixed lấy mốc scheduled 10:00:00 làm base
     -> lượt 10:01:00 đã trễ -> có thể chạy ngay
     -> sau đó lượt 10:02:00 -> cũng có thể chạy muộn
     -> rồi đến 10:03:00...
~~~

**Đánh đổi:** fixed có thể gây **catch-up burst** sau tác vụ chậm. Với I/O tốn tài nguyên, cần kiểm tra capacity/idempotency. Không suy ra “fixed” đồng nghĩa các lần của **cùng một registration** được chạy đồng thời; cơ chế trigger của `CronTrigger` thường chờ việc trước kết thúc rồi mới tính mốc mới.

### So sánh ngắn

| Câu hỏi | Lenient cron | Fixed cron |
| --- | --- | --- |
| Base để tính mốc cron mới | Completion time | Last scheduled time |
| Mốc bị bỏ qua khi task chạy lâu | Có thể bỏ qua | Có thể chạy bù, muộn |
| Phù hợp với | Polling, cleanup không cần bù mọi tick | Lịch cần ghi nhận mọi tick nếu chấp nhận catch-up |
| Rủi ro | Mất các fire times danh nghĩa | Backlog và các lượt chạy dồn |
| API | `new CronTrigger(expr)` hoặc `forLenientExecution(expr)` | `forFixedExecution(expr)` |
| Spring version | Constructor có từ trước; factory từ 6.1.3 | Factory từ 6.1.3 |

Spring còn có `resumeLenientExecution` / `resumeFixedExecution` để xây trigger với mốc resume cho các trường hợp có **last-known timestamp**. Hai factory này không tự lưu kết quả bền vững; ứng dụng phải có nguồn timestamp đáng tin khi restart.

## 11. PeriodicTrigger: hỗ trợ cố định mà vẫn là Trigger

Spring cung cấp `org.springframework.scheduling.support.PeriodicTrigger` cho lịch theo period có thể được diễn giải dưới dạng fixed-rate hoặc fixed-delay và có thể đặt initial delay. Ví dụ:

~~~java
import java.time.Duration;
import org.springframework.scheduling.support.PeriodicTrigger;

PeriodicTrigger trigger =
        new PeriodicTrigger(Duration.ofSeconds(30));
trigger.setFixedRate(false);
trigger.setInitialDelay(Duration.ofSeconds(10));
~~~

Đây là **fragment Spring 6.x**, có thể dùng với `TaskScheduler.schedule(task, trigger)`. Khi `setFixedRate(false)`, semantics nhắm tới fixed-delay; khi `true`, periodic trigger hướng về fixed-rate. Với lịch tuần hoàn thuần túy, các method `scheduleAtFixedRate`/`scheduleWithFixedDelay` trên `TaskScheduler` thường rõ ràng hơn và giảm số object/cấu hình.

Không nhầm `PeriodicTrigger` với một biểu thức cron; nó định nghĩa khoảng duration, không lập lịch theo ngày trong tuần hoặc giờ địa phương.

## 12. Tích hợp vào Spring bằng TaskScheduler.schedule

Tạo bean scheduler:

Tệp **SchedulerBeans.java**:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerBeans {
    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("trigger-job-");
        return scheduler;
    }
}
~~~

Đây là **Spring configuration class**, cần Spring Context. `ThreadPoolTaskScheduler` do Spring quản lý sẽ được initialize/destroy theo lifecycle. Các con số thread chỉ để minh họa, không phải tuning production. Khi chạy trên Jakarta EE server nghiêm ngặt, có thể cần `DefaultManagedTaskScheduler` thay vì tự tạo pool; `Trigger` vẫn là abstraction lập lịch của Spring.

Một service đăng ký custom Trigger:

Tệp **ReportScheduler.java**:

~~~java
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ReportScheduler {
    private final TaskScheduler taskScheduler;
    private final ReportService reportService;

    public ReportScheduler(
            TaskScheduler taskScheduler,
            ReportService reportService) {
        this.taskScheduler = taskScheduler;
        this.reportService = reportService;
    }

    public ScheduledFuture<?> start() {
        return taskScheduler.schedule(
                reportService::generateReport,
                new CompletionDelayTrigger(Duration.ofMinutes(2)));
    }
}
~~~

Đây là **Spring service** cần một bean `ReportService` có method `void generateReport()` và class `CompletionDelayTrigger` ở phần trước. Lời gọi `start()` đăng ký lịch; **không tự diễn ra chỉ vì bean tồn tại**. Ứng dụng phải chủ động gọi `start` ở lifecycle phase phù hợp, ví dụ khi ApplicationReadyEvent hoặc qua một API quản trị.

Khi `start()` được gọi nhiều lần, mỗi lần có thể tạo một **registration mới**, dẫn đến tác vụ chạy nhiều lần song song. Nếu cần đúng một registration, giữ `ScheduledFuture` và quy tắc start/stop idempotent.

### Không quên kết quả null

`TaskScheduler.schedule(Runnable, Trigger)` có thể trả `null` khi Trigger không tạo một fire time tiếp theo. Một API start/stop phải xử lý trạng thái này; không gọi `future.cancel()` vô điều kiện nếu handle null.

## 13. Đăng ký qua SchedulingConfigurer

Nếu mục tiêu là tự đăng ký một task khi ứng dụng khởi động, Spring có `SchedulingConfigurer` và `ScheduledTaskRegistrar`. Cách này thuận tiện hơn tự viết lifecycle registration nếu chỉ có một lịch cấu hình trước.

Tệp **CustomTriggerSchedulingConfig.java**:

~~~java
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class CustomTriggerSchedulingConfig implements SchedulingConfigurer {
    private final TaskScheduler scheduler;
    private final ReportService reportService;

    public CustomTriggerSchedulingConfig(
            @Qualifier("taskScheduler") TaskScheduler scheduler,
            ReportService reportService) {
        this.scheduler = scheduler;
        this.reportService = reportService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduler);
        registrar.addTriggerTask(
                reportService::generateReport,
                new CompletionDelayTrigger(Duration.ofMinutes(2)));
    }
}
~~~

Đây là **Spring configuration class**, sử dụng scheduler `taskScheduler` từ `SchedulerBeans` và `ReportService` do ứng dụng định nghĩa. Cấu hình giúp scheduler đăng ký task khi context khởi tạo, không cần code bên ngoài gọi `ReportScheduler.start()`.

**Không triển khai cả hai đoạn `ReportScheduler.start()` và `SchedulingConfigurer.addTriggerTask` cho cùng một job rồi gọi start thủ công**, vì chúng sẽ tạo các lịch **độc lập**. Một task có thể chạy hai lần do đăng ký trùng, hoàn toàn không phải “scheduler bắn sai”.

### @Scheduled có nhận custom Trigger trực tiếp không?

`@Scheduled` có các thuộc tính như `cron`, `fixedRate` và `fixedDelay`, **không có thuộc tính lấy một bean `Trigger` tùy ý** trong hợp đồng annotation thông thường. Muốn chọn mốc từ `TriggerContext` bằng logic riêng, hãy dùng `TaskScheduler.schedule(task, trigger)` hoặc `SchedulingConfigurer`/`ScheduledTaskRegistrar`. Đừng gắn đồng thời một `@Scheduled(cron=...)` lên method và đăng ký chính method đó bằng custom Trigger nếu không muốn hai lịch.

## 14. Trả null: dừng lịch theo điều kiện

Một Trigger có thể quyết định kết thúc:

~~~java
@Override
public Instant nextExecution(TriggerContext context) {
    if (jobControl.isDisabled()) {
        return null;
    }

    Instant completed = context.lastCompletion();
    Instant base = completed != null
            ? completed
            : context.getClock().instant();
    return base.plus(Duration.ofMinutes(5));
}
~~~

Đây là **fragment trong một Trigger Spring**; `jobControl` là dependency ứng dụng, cần thread-safe và phải được inject/thiết kế rõ. Trả `null` nghĩa là **không có lần fire tiếp theo từ registration đó**. Nếu sau này `jobControl` được bật lại, **đừng mặc định scheduler sẽ hỏi lại trigger cũ**. Khi đã ngừng scheduling vì `null`, cần chủ động **đăng ký lại** nếu muốn chạy tiếp.

### Dừng sau N lần

Một cách làm là theo dõi số lần **hoàn tất** bằng một `AtomicInteger` nằm trong wrapper task, rồi Trigger đọc số đó. Nhưng cần thiết kế cẩn thận:

1. Trigger được hỏi **trước lần đầu**, khi counter bằng 0.
2. Body tăng counter trong `finally` khi một lượt thật sự hoàn tất.
3. Khi counter đạt N, `nextExecution` trả `null`.
4. Nếu body ném lỗi, callback/error handler và thứ tự cập nhật context phải được kiểm tra.
5. Nếu tạo nhiều registration dùng chung một counter, N là số lần chung hay số lần mỗi lịch?
6. Khi restart, counter RAM mất; job bền vững cần lưu state riêng.

`TriggerContext` không cung cấp “đã chạy N lần” trực tiếp. Không thể nhìn ba timestamp và kết luận số lần đã chạy, đặc biệt khi không có history bền vững.

### Tắt lịch không giống hủy công việc đang chạy

Trả `null` ở lần tính mốc mới **không hủy body hiện đang chạy**. Nếu muốn ngắt task đang chạy, bạn cần một cơ chế cancellation hợp tác, timeout ở I/O, và xử lý cleanup/transaction. Còn `ScheduledFuture.cancel(false)` thường ngăn các lần scheduling tiếp theo nhưng không interrupt body đang chạy; `cancel(true)` chỉ **thử** interrupt khi underlying executor hỗ trợ.

## 15. Unit test Trigger bằng SimpleTriggerContext và Clock.fixed

Spring có class `org.springframework.scheduling.support.SimpleTriggerContext`, phù hợp để tạo ngữ cảnh kiểm thử. Dùng một đồng hồ cố định sẽ cho kết quả thời gian xác định; test không cần tạo thread thật hay đợi cron đến giờ.

Ví dụ JUnit 5 (các method/test phụ thuộc những class Spring ở phần trên):

~~~java
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.SimpleTriggerContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompletionDelayTriggerTest {

    @Test
    void firstRunUsesClock() {
        Instant now = Instant.parse("2026-01-01T09:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        SimpleTriggerContext context = new SimpleTriggerContext(clock);
        CompletionDelayTrigger trigger =
                new CompletionDelayTrigger(Duration.ofSeconds(30));

        assertEquals(
                Instant.parse("2026-01-01T09:00:30Z"),
                trigger.nextExecution(context));
    }

    @Test
    void laterRunUsesLastCompletion() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-01-01T09:00:00Z"),
                ZoneOffset.UTC);

        SimpleTriggerContext context = new SimpleTriggerContext(clock);
        context.update(
                Instant.parse("2026-01-01T09:00:00Z"),
                Instant.parse("2026-01-01T09:00:04Z"),
                Instant.parse("2026-01-01T09:00:13Z"));

        CompletionDelayTrigger trigger =
                new CompletionDelayTrigger(Duration.ofSeconds(30));

        assertEquals(
                Instant.parse("2026-01-01T09:00:43Z"),
                trigger.nextExecution(context));
    }
}
~~~

Đây là **JUnit test class** cần JUnit 5, Spring Context và `CompletionDelayTrigger` ở cùng source set. Không phải Java SE runnable độc lập. `SimpleTriggerContext.update(scheduled, actual, completion)` là phương thức hỗ trợ điền ba mốc lịch sử.

### Vì sao test này tốt hơn Thread.sleep?

- Kiểm tra **hợp đồng tính toán** của Trigger, không phụ thuộc scheduler khởi tạo nhanh/chậm.
- Bao phủ trường hợp **lần đầu có null timestamps** và trường hợp đã có history.
- Dễ tái hiện bug, vì đồng hồ và cả ba mốc đều cố định.
- Có thể tạo thêm case queue lag, duration dài, lịch trong quá khứ, boundary/overflow.
- Không tạo worker treo, không cần chờ thật 30 giây.

### Các test case nên bổ sung

| Test | Dữ liệu | Kết quả mong đợi |
| --- | --- | --- |
| First call | Ba timestamps null | Dùng clock + delay |
| Normal completion | Completion muộn hơn actual | Tính từ completion |
| Queue lag | Actual muộn hơn scheduled | Base theo policy đã định |
| Long task | Duration vượt threshold | Adaptive delay tăng |
| Slow task fixed cadence | Scheduled time cũ | Next có thể nằm trong quá khứ |
| Disable flag | Flag true | `nextExecution` trả `null` |
| Re-enable after null | Flag false trở lại | Cần registration mới |
| Invalid duration | Negative/zero | Constructor từ chối |
| Clock timezone | Clock với ZoneId được chỉ định | `Instant` tính nhất quán, calendaring dựa ZoneId đúng |
| Large timestamp | Instant gần giới hạn | Kiểm soát overflow/cấu hình không hợp lý |
| Error in task | Task ném exception | Không tự suy ra success/failure từ context |
| Concurrent calls | Nhiều registration | State external phải thread-safe và phân biệt lịch |

Nếu custom Trigger dùng business calendar, bổ sung các ngày nghỉ, cuối tuần, DST gap/overlap và timezone thật bằng `Clock.fixed`/`ZoneId`.

## 16. Integration test với ThreadPoolTaskScheduler

Unit test chỉ xác minh phép tính. Khi deploy, cần kiểm tra scheduler **thực sự dùng Trigger** và chạy lại theo policy. Một integration test có thể:

1. Dựng `ThreadPoolTaskScheduler` riêng cho test với một worker, initialize theo Spring bean lifecycle.
2. Đăng ký một task bằng `schedule(Runnable, Trigger)`, không gắn thêm `@Scheduled` trùng.
3. Dùng `CountDownLatch` phát tín hiệu khi task bắt đầu/hoàn thành; mọi await đều có timeout.
4. Chọn delay rất nhỏ trong test hoặc dùng Trigger ngắn; không chạy polling/sleep tùy tiện.
5. Giữ `ScheduledFuture<?>` và cancel trong `finally`.
6. Ghi lại planned time và actual time khi có quyền quan sát để kiểm chứng lag; không chỉ nhìn số lượt.
7. Nếu task lỗi, xác minh behavior của `ErrorHandler` và việc scheduling có tiếp tục không theo chính sách được cấu hình.
8. Nếu test stop condition, xác minh không đăng ký lượt mới sau khi Trigger trả `null`; đừng dựa vào khoảng im lặng ngắn để chứng minh tuyệt đối, ưu tiên instrument/mock registration.
9. Đóng ApplicationContext hoặc shutdown **chỉ executor do test sở hữu**. Không tự shutdown một managed executor thuộc Jakarta EE server.

### Ví dụ integration fragment với latch

~~~java
CountDownLatch received = new CountDownLatch(3);
AtomicInteger runs = new AtomicInteger();

Trigger trigger = context -> {
    if (runs.get() >= 3) {
        return null;
    }
    return context.getClock().instant().plusMillis(20);
};

ScheduledFuture<?> future = scheduler.schedule(() -> {
    runs.incrementAndGet();
    received.countDown();
}, trigger);

try {
    assertTrue(received.await(5, TimeUnit.SECONDS));
    assertEquals(3, runs.get());
} finally {
    if (future != null) {
        future.cancel(false);
    }
}
~~~

Đây là **fragment test** với `scheduler` là `TaskScheduler` đã cấu hình, cùng imports `CountDownLatch`, `AtomicInteger`, `Trigger`, `ScheduledFuture`, `TimeUnit` và assertions JUnit. Vì Trigger được hỏi lại sau mỗi lần hoàn thành, counter được ghi trước khi task kết thúc để xác định điều kiện stop. Tuy nhiên, trong một test production-grade vẫn phải theo dõi **số lần đăng ký** và không dùng khoảng im lặng bất kỳ làm bằng chứng vĩnh viễn. Một scheduler khác có thể dùng policy/threads khác; kiểm thử đúng implementation deploy.

## 17. Thread safety: Trigger dùng chung có nguy hiểm không?

Một `Trigger` thuần tính toán dựa trên `TriggerContext` và các thuộc tính bất biến như `Duration` thường an toàn để tái sử dụng về mặt state. Nhưng Trigger có thể được gọi cho **nhiều registrations** khác nhau; nếu nó lưu mutable field như:

~~~java
private int completedRuns;
private Instant previous;
~~~

thì các giá trị đó có thể bị chia sẻ giữa các lịch hoặc bị đọc/ghi đồng thời không có visibility. Đừng dùng field mutable không đồng bộ làm nguồn quyết định cho một hệ thống nhiều threads.

Nếu cần state riêng:

- Tạo một Trigger instance riêng **cho mỗi registration**, quản lý vòng đời cụ thể.
- Dùng `AtomicInteger`/`AtomicReference`/lock khi mutable state nằm trong một JVM.
- Gắn job ID/version vào state để các registrations không đọc nhầm nhau.
- Nếu multi-instance cần quyết định thống nhất, dùng database/coordination service và quy tắc claim idempotent.
- Đừng làm I/O blocking hay transaction nặng **bên trong `nextExecution`**. Trigger cần nhẹ và dự đoán được.

### Tránh effect ngoài dự kiến trong nextExecution

Một Trigger có thể được gọi lúc đăng ký, sau lượt thực thi hoặc ở thời điểm adapter cần tính toán. Đừng thiết kế kiểu “mỗi lần nextExecution được gọi thì trừ tiền/ghi nhận một job đã chạy”. Cách đúng là tách:

- `Trigger.nextExecution`: **ra quyết định thời gian**.
- `Runnable`: **thực thi nghiệp vụ**.
- Job state/store: **ghi nhận kết quả, retry, idempotency**.

Nhờ đó, unit test chỉ cần gọi Trigger nhiều lần mà không tạo side effect.

## 18. Đồng hồ, múi giờ và thời gian “trong quá khứ”

`Instant` là thời điểm tuyệt đối trên timeline UTC; `ZoneId` diễn tả cách nhìn một mốc theo lịch địa phương. Nếu yêu cầu “09:00 giờ Việt Nam mỗi ngày”, hãy dùng cron/calendar logic theo zone; đừng chỉ cộng `Duration.ofDays(1)` vào một Instant rồi giả định nó luôn giữ cùng giờ địa phương tại những vùng có DST.

Với business hours, có thể cần:

1. Lấy clock hiện tại từ `context.getClock()`.
2. Quy đổi sang `ZonedDateTime` theo zone nghiệp vụ.
3. Tìm slot hợp lệ kế tiếp, bỏ qua cuối tuần/ngày nghỉ.
4. Chuyển lại thành `Instant` và trả về.
5. Kiểm thử ngày DST/holiday boundary và thời điểm không tồn tại/lặp lại.

Nếu nhu cầu chỉ là biểu thức cron, sử dụng `CronTrigger` sẽ giảm rủi ro triển khai tự tạo parser/calendar. Custom Trigger phù hợp khi quy tắc phụ thuộc thêm **lastCompletion** hoặc domain state.

### Phải làm gì với next Instant đã quá hạn?

- **Giữ lịch gốc:** trả timestamp danh nghĩa và chấp nhận catch-up, nhưng theo dõi số task bị dồn.
- **Skip mốc cũ:** tính từ `max(lastCompletion, clock.instant())` nếu yêu cầu chỉ một lượt tương lai.
- **Giãn nhịp:** áp thời gian nghỉ tối thiểu sau completion để tránh burst.
- **Giới hạn backlog:** không cho Trigger liên tục trả mốc quá khứ; đưa business backlog sang persistent job queue.

Không có lựa chọn đúng chung cho mọi hệ thống. Ví dụ báo cáo doanh thu hàng ngày có thể cần xử lý bù các business date chưa được ghi, trong khi cleanup cache có thể bỏ qua tick cũ và chỉ chạy một lượt tại thời điểm mới.

## 19. Exception, error handler và ngữ cảnh hoàn tất

Khi Runnable ném exception, scheduler/error handler quyết định ghi log, truyền lỗi hoặc tiếp tục lịch. `TriggerContext.lastCompletion()` là **thời điểm kết thúc thực thi**, không phải trạng thái thành công/thất bại. Không viết:

~~~java
if (context.lastCompletion() != null) {
    // SAI: coi như tác vụ lần trước chắc chắn thành công.
}
~~~

Đây là **mẫu điều kiện sai ý nghĩa**. Completion chỉ cho biết một execution đã kết thúc theo tracking của scheduler; nó không cung cấp thông tin exception, business outcome hay transaction commit.

### Xử lý lỗi trong Runnable

Một cách dễ quan sát là wrapper ghi outcome riêng:

~~~java
Runnable observedJob = () -> {
    try {
        businessJob.run();
        metrics.recordSuccess();
    } catch (RuntimeException ex) {
        metrics.recordFailure(ex);
        throw ex;
    }
};
~~~

Đây là **mã minh họa** với `businessJob`/`metrics` của ứng dụng. Việc **ném lại** hay nuốt lỗi ảnh hưởng policy của scheduler; cần biết error handler của `ThreadPoolTaskScheduler` và semantics của delegate thực tế. Nếu custom Trigger cần backoff theo lỗi, ghi trạng thái qua cơ chế thread-safe, không dựa vào `lastCompletion()` để phân loại.

### Transaction và thread-bound context

Scheduler dispatch công việc lên worker. Một `@Transactional` ở request thread **không tự propagate** sang worker. Nếu Runnable cần database transaction, hãy gọi một **Spring-managed service** với `@Transactional` qua proxy phù hợp:

~~~java
@Component
public class ScheduledAuditJob {
    private final AuditService auditService;

    public ScheduledAuditJob(AuditService auditService) {
        this.auditService = auditService;
    }

    public void run() {
        auditService.processAuditBatch();
    }
}
~~~

Đây là **class Spring minh họa**, cần import `Component` và `AuditService` có method transactional phù hợp. Đừng tự gọi một method `@Transactional` trong cùng bean bằng `this.method()` rồi mặc định advice được áp dụng trong proxy mode.

MDC, SecurityContext, tracing context và các `ThreadLocal` khác cũng cần xử lý riêng. `TriggerContext` chỉ có metadata thời gian; không phải một object chứa mọi context của request.

## 20. Cancellation, shutdown và thay đổi lịch đang chạy

`TaskScheduler.schedule(task, trigger)` có thể trả `ScheduledFuture<?>`. Hãy giữ handle nếu muốn stop registration:

~~~java
ScheduledFuture<?> handle =
        taskScheduler.schedule(task, trigger);

if (handle != null) {
    handle.cancel(false);
}
~~~

Đây là **fragment Spring integration**. `cancel(false)` thường ngăn các lần chạy sắp tới nếu implementation hỗ trợ và hủy thành công; nó **không interrupt body đã bắt đầu**. `cancel(true)` là yêu cầu interrupt best-effort tùy executor/Future, không đảm bảo rollback side effect.

Nếu Trigger trả `null`, registration có thể không có future hữu ích để cancel; code phải xử lý `null`. Nếu một operator cập nhật cron/config trong lúc job đang chạy, cần phân biệt:

1. Thay **chính sách tính mốc mới**.
2. Hủy registration cũ.
3. Đăng ký registration mới.
4. Xử lý lượt body đang chạy.
5. Giữ trạng thái/job ID để tránh double-processing.

### Thay thuộc tính của một Trigger lúc runtime có tự reschedule không?

Không có bảo đảm chung. Một registration có thể đã có `ScheduledFuture` cho một thời điểm sắp tới; thay field trong Trigger không tự hủy thời điểm đó. Một số Trigger có thể đọc config mới trong lần `nextExecution` kế tiếp, nhưng behavior cần kiểm thử. Nếu phải có đổi lịch ngay, nên sử dụng **cơ chế cancel và re-register rõ ràng**, với version, lock và quan sát trạng thái.

### Shutdown

`ThreadPoolTaskScheduler` do Spring quản lý có lifecycle/shutdown riêng. Nếu bạn tự tạo local `ScheduledExecutorService`, phải quản lý shutdown. Nếu chạy trên Jakarta EE managed scheduled executor, ứng dụng không sở hữu quyền shutdown resource chung của container. Không copy logic `executor.shutdownNow()` từ demo Java SE lên Jakarta managed resource.

## 21. Không nhầm Trigger với một job scheduler bền vững

`TriggerContext` chỉ lưu lịch sử của registration đang hoạt động; nó **không phải một database job table**. Nếu JVM dừng hoặc ứng dụng redeploy, state in-memory có thể bị mất. Khi app khởi động lại, một Trigger thông thường có thể nhận **lastScheduled/Actual/Completion null** như lần đầu, mặc dù hệ thống nghiệp vụ đã xử lý nhiều lần trước đó.

Nếu yêu cầu:

- “Mọi business date phải được xử lý đúng một lần, kể cả downtime”.
- “Giữ chính xác run history qua restart”.
- “Nhiều instance không chạy trùng”.
- “Retry lỗi với backoff bền vững”.
- “Có SLA, dead-letter và khả năng resume”.

...thì cần **job store**/Quartz persistent scheduler, queue bền vững, distributed lock hoặc cơ chế điều phối tương đương. `CronTrigger.resumeFixedExecution(...)` có thể giúp tính mốc từ timestamp đã lưu, nhưng **không tự lưu timestamp**, không ghi kết quả nghiệp vụ và không cung cấp exactly-once toàn cụm.

### Một task trên ba replicas

Nếu cùng một Spring application được deploy trên ba replicas, mỗi ApplicationContext có thể đăng ký lịch riêng. Trigger dựa trên `lastCompletion` của từng instance sẽ có **lịch sử khác nhau**. Vì vậy có thể có ba lượt chạy cho cùng một business window, dù mỗi instance đã dùng Trigger chính xác. Cần xác định idempotency key/lease/leader election hoặc phối hợp qua persistent job store.

### Misfire, skip và catch-up

“Misfire” trong các hệ thống durable scheduler là một khái niệm có chính sách riêng. Đừng gán semantics misfire của Quartz cho Spring `TaskScheduler` chỉ vì đều dùng từ “trigger”. Với Spring cron fixed/lenient, các factory mô tả cách chọn fire time tiếp theo; khôi phục state và xử lý mốc bị lỡ qua downtime là trách nhiệm kiến trúc rộng hơn.

## 22. Observability cho Trigger-based scheduling

Nên đo các thời điểm và chỉ số phù hợp:

| Metric/trường log | Cách tính hoặc ý nghĩa |
| --- | --- |
| `last_scheduled_instant` | Thời điểm lẽ ra task được lên lịch |
| `last_actual_instant` | Thời điểm task thật sự bắt đầu |
| `last_completion_instant` | Thời điểm task kết thúc |
| `start_lag` | `lastActual - lastScheduled`, chậm scheduler/queue |
| `execution_duration` | `lastCompletion - lastActual` |
| `next_fire_instant` | Instant do Trigger quyết định |
| `next_fire_delay` | `nextFire - clock.instant()`, có thể âm nếu catch-up |
| `trigger_policy` | completion-based/schedule-based/cron-lenient/cron-fixed |
| `job_outcome` | Thành công/thất bại/cancelled; lấy từ business job, **không** từ TriggerContext |
| `scheduler_rejections` | Số lần executor không nhận registration/task |
| `missed_business_windows` | Số business window cần xử lý nhưng chưa hoàn thành |
| `active_registrations` | Số lịch thực sự đang đăng ký trong context/node |

`Trigger.nextExecution` không nhất thiết cần ghi metric mỗi lần gọi nếu gây side effect/blocking; có thể instrument ở wrapper hoặc dịch vụ monitoring. Khi log timestamp, sử dụng ISO-8601 `Instant` và kèm job ID/instance ID vừa đủ. Tránh label theo ID duy nhất không giới hạn gây nổ cardinality.

### Phân biệt ba loại trễ

1. **Scheduling lag:** lần chạy thực tế bắt đầu sau mốc đã đặt.
2. **Execution duration:** body thực hiện bao lâu.
3. **Next-fire gap:** thời gian từ completion đến mốc mới.

Ví dụ job bắt đầu trễ 4 giây, xử lý 9 giây và đợi 30 giây sau completion. Tổng thời gian từ **scheduled lần trước** tới **scheduled lần sau** là 43 giây, không phải 30 giây. Nếu operator chỉ nhìn “period=30s” trong config, cần giải thích rõ đó là **delay sau completion**.

## 23. Những lỗi phổ biến

1. **Đồng nhất `Trigger` với `TaskScheduler`.** Trigger tính thời điểm; scheduler dispatch.
2. **Dùng `lastActualExecution` khi nghiệp vụ muốn bám mốc dự kiến.** Làm nhịp chậm thêm do queue lag.
3. **Dùng `lastScheduledExecution` khi nghiệp vụ muốn khoảng nghỉ sau khi xong.** Có thể làm lượt mới chạy ngay khi body lâu hơn period.
4. **Quên kiểm tra null ở lần đầu.** Ba timestamp history đều có thể null, gây `NullPointerException`.
5. **Trả `Instant.now()` liên tục.** Tạo chuỗi gần như không có delay, có nguy cơ loop/burst.
6. **Gọi `Thread.sleep` trong `nextExecution`.** Block thread tính lịch thay vì trả future Instant.
7. **Dùng `System.currentTimeMillis` tùy ý dù context có clock.** Làm test khó và lệch clock configuration.
8. **Tưởng `lastCompletion != null` nghĩa là job thành công.** Chỉ là mốc hoàn tất.
9. **Tưởng TriggerContext có error/retry count/result.** Nó chỉ chứa các mốc thời gian và clock.
10. **Trả null rồi tin rằng đổi flag sẽ làm lịch tự chạy trở lại.** Cần registration mới.
11. **Tưởng trả null giết body đang chạy.** Stop future fire, không tự cancel body.
12. **Tưởng mỗi lần `nextExecution` là một lần job đã chạy.** Có thể gọi lúc đăng ký ban đầu.
13. **Tạo state mutable không đồng bộ trong Trigger dùng chung.** Dẫn tới race giữa registrations.
14. **Dùng fixed cron với job nặng mà không xem catch-up burst.** Task có thể chạy dồn để bù mốc đã lỡ.
15. **Dùng lenient cron khi hợp đồng đòi xử lý đủ mọi time window.** Có thể skip fire times.
16. **Tưởng cron năm trường Unix hợp lệ trong Spring.** Spring cron dùng sáu trường, gồm giây.
17. **Không chỉ định timezone khi job theo giờ địa phương.** Deploy khác zone có thể đổi mốc.
18. **Coi `Duration.ofDays(1)` như một ngày lịch ở mọi zone có DST.** Không đúng ở ngày đổi giờ.
19. **Đăng ký cùng job ở `SchedulingConfigurer` và `TaskScheduler.schedule` thêm lần nữa.** Duplicate registrations.
20. **Đặt `@Scheduled` cùng custom Trigger cho một method mà không quản lý hai lịch.** Dễ chạy trùng.
21. **Thay config field rồi tin thời điểm đã đăng ký được dời tự động.** Có thể phải cancel/re-register.
22. **Chỉ test bằng `Thread.sleep`.** Cần `SimpleTriggerContext`, `Clock.fixed` và assertions.
23. **Không test result `null` từ `TaskScheduler.schedule`.** Có thể gây null dereference khi stop.
24. **Đưa I/O blocking hoặc query nặng vào Trigger.** Làm chậm scheduling calculation và tăng rủi ro lỗi.
25. **Dùng Trigger trong RAM để hứa exactly-once toàn cluster.** Mỗi replica có context riêng.
26. **Cho rằng Spring scheduler khôi phục mọi mốc sau restart.** Không có persistent state mặc định.
27. **Không xử lý lỗi Runnable và error-handler policy.** Có thể bị dừng lịch hoặc log không đủ tùy implementation.
28. **Bỏ qua interruption/shutdown/cancellation.** Job đang chạy vẫn có thể tiếp tục sau khi lịch bị tắt.
29. **Bỏ qua transaction thread boundary.** Worker không tự kế thừa transaction của request.
30. **Coi mọi factory `CronTrigger` đều có ở Spring 5.x.** `forFixedExecution`/`forLenientExecution` là API từ Spring 6.1.3.

## 24. Quy trình lựa chọn đúng Trigger

Trước khi viết custom Trigger, tự hỏi:

1. **Tác vụ thực sự cần gì?** Tick cố định, delay sau hoàn tất, cron theo giờ địa phương, hay quy tắc thích nghi?
2. **Mốc tính lịch mới là gì?** Last scheduled, last actual hay last completion? Tại sao?
3. **Lần đầu khi history null thì sao?** Chạy ngay, đợi một delay hay chờ một cron window?
4. **Nếu worker chạy quá lâu?** Skip các mốc cũ hay bắt kịp theo lịch?
5. **Nếu một lần xử lý thất bại?** Continue, backoff hay stop? Outcome có ở đâu?
6. **Nếu Trigger trả null?** Ai có trách nhiệm re-register khi bật trở lại?
7. **Nếu config thay đổi?** Có cơ chế cancel/re-register và tránh duplicate không?
8. **Nếu nhiều replica?** Ai là owner của một business window?
9. **Nếu JVM restart?** Có cần phục hồi mốc/trạng thái bền vững không?
10. **Test thế nào?** Có thể kiểm chứng bằng clock cố định, context giả và integration test riêng không?

### Bảng chọn nhanh

| Nhu cầu | Lựa chọn khởi đầu |
| --- | --- |
| Mỗi N giây, dựa trên lịch cố định | `TaskScheduler.scheduleAtFixedRate` |
| Đợi N giây sau lần trước hoàn thành | `TaskScheduler.scheduleWithFixedDelay` hoặc completion-based Trigger |
| Cron theo lịch thông thường, có thể skip fire time | `CronTrigger` mặc định / `forLenientExecution` |
| Cần giữ/bù từng mốc cron trong khi task chạy lâu | `CronTrigger.forFixedExecution` |
| Delay phụ thuộc execution duration | Custom Trigger dùng `lastActualExecution` + `lastCompletion` |
| Chọn lần chạy theo business calendar phức tạp | Custom Trigger dùng `Clock`/`ZoneId` hoặc library calendar phù hợp |
| Dừng không còn lần chạy tiếp | Custom Trigger trả `null` |
| Chuyển lịch trong runtime ngay lập tức | Programmatic cancel + re-register, xử lý race |
| Lịch bền vững/cluster/retry có trạng thái | Job store/Quartz/queue; không chỉ Spring Trigger |

## 25. Câu hỏi tự kiểm tra

1. `Trigger` và `TaskScheduler` khác nhau ở trách nhiệm nào?
2. `Trigger.nextExecution(...)` trả kiểu gì? Trả `null` có ý nghĩa gì?
3. Phân biệt `lastScheduledExecution`, `lastActualExecution` và `lastCompletion` bằng một timeline.
4. Vì sao ba timestamp có thể null ngay lần đầu scheduler hỏi?
5. Có thể dùng `lastCompletion != null` để biết job trước thành công không? Vì sao?
6. Với scheduled 09:00:00, actual 09:00:04, completion 09:00:13, cộng 30 giây sẽ cho ba mốc kế tiếp nào?
7. Vì sao chọn `lastScheduledExecution` có thể tạo catch-up khi worker chậm?
8. Vì sao chọn `lastCompletion` giúp có khoảng nghỉ sau body?
9. `context.getClock()` có lợi ích gì so với gọi trực tiếp `Instant.now()` trong mọi phép tính?
10. `CronTrigger` mặc định dùng lenient hay fixed execution? Hai factory riêng được giới thiệu từ Spring version nào?
11. Nếu một task chạy từ 10:00 đến 10:02:10 với cron mỗi phút, lenient và fixed có thể xử lý các mốc bị lỡ khác nhau thế nào?
12. Muốn trigger phụ thuộc kết quả lỗi/thành công, `TriggerContext` có đủ metadata không?
13. Nếu Trigger đã trả null, thay flag enable từ false sang true có bảo đảm nó tự đăng ký lại không?
14. `@Scheduled` có thuộc tính nhận một custom `Trigger` bean trực tiếp không? Cần API nào thay thế?
15. Unit test với `SimpleTriggerContext` và `Clock.fixed` sẽ giúp phát hiện những lỗi gì?
16. Vì sao không nên đăng ký cùng job từ hai đường khác nhau?
17. Khi cron theo giờ địa phương có DST, tại sao `Duration.ofDays(1)` có thể không biểu diễn một ngày lịch?
18. Nếu ứng dụng chạy trên ba replicas, `lastCompletion` trong từng TriggerContext có dùng chung không?
19. Vì sao `CronTrigger.resumeFixedExecution(...)` không tự tạo persistent job store?
20. Khi stop một ScheduledFuture, có thể khẳng định Runnable đang chạy đã bị ngắt/rollback không?

## 26. Tài liệu chính thức

- [Spring Framework Reference — Task Execution and Scheduling: Trigger and TaskScheduler](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Trigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/Trigger.html)
- [Spring Framework — TriggerContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TriggerContext.html)
- [Spring Framework — TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework — SimpleTriggerContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/SimpleTriggerContext.html)
- [Spring Framework — CronTrigger Javadoc: lenient/fixed/resume](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html)
- [Spring Framework — PeriodicTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/PeriodicTrigger.html)
- [Spring Framework — SchedulingConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/SchedulingConfigurer.html)
- [Java SE 17 — Clock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html)
- [Java SE 17 — Instant](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html)
- [Java SE 17 — Duration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html)

**Ghi nhớ:** `Trigger.nextExecution(TriggerContext)` chọn thời điểm chạy kế tiếp dựa trên **lịch sử của lần trước**, không tự chạy job. `lastScheduledExecution` giúp bám nhịp dự kiến; `lastActualExecution` cho biết bắt đầu thực tế; `lastCompletion` thích hợp cho khoảng nghỉ sau body; `getClock` giúp tính thời gian nhất quán và kiểm thử xác định. Trả `null` làm dừng registration hiện tại; với cron, chọn rõ **lenient** hay **fixed** theo chính sách skip/catch-up. Một Trigger tốt tính toán nhanh, xử lý null và không đồng nhất metadata thời gian với business outcome hoặc job durability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-scheduling-002">TriggerContext theo dõi lần dự kiến, thực tế và hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# TriggerContext theo dõi lần dự kiến, thực tế và hoàn tất

## 1. Tại sao cần TriggerContext?

Trong ứng dụng Spring Framework, việc chạy một tác vụ theo lịch đôi khi không thể mô tả đơn giản bằng “mỗi 5 giây” hoặc “02:00 hằng ngày”. Có những trường hợp thời điểm chạy tiếp theo cần phụ thuộc vào **lần chạy trước được hẹn vào lúc nào, thực sự bắt đầu khi nào, và hoàn tất lúc nào**. Ví dụ: tác vụ đồng bộ dữ liệu chỉ được chạy tiếp sau khi phiên trước đã hoàn tất; một lịch fixed-rate cần giữ nhịp theo thời điểm **dự kiến**, còn lịch cron có thể bỏ qua hoặc xử lý bù các mốc đã lỡ.

Spring cung cấp interface `org.springframework.scheduling.TriggerContext` để giữ **ba dấu mốc thời gian của lần thực thi gần nhất** cho một lịch sử dụng `Trigger`. Đây là đầu vào để `Trigger.nextExecution(context)` tính `Instant` cho lần chạy tiếp theo.

**Điểm trọng tâm:** `TriggerContext` không phải danh sách job, không phải trạng thái nghiệp vụ của task, và cũng không phải đồng hồ đếm số lần retry. Nó là một **snapshot của ba mốc thời gian**, cộng với `Clock` dùng cho phép tính lịch.

### Mục tiêu học tập

Sau bài này, bạn sẽ:

1. Phân biệt `lastScheduledExecution()` (lúc **dự kiến**), `lastActualExecution()` (lúc **thực sự bắt đầu**) và `lastCompletion()` (lúc **hoàn tất**).
2. Biết các method trên trả `Instant` hoặc `null`, đặc biệt khi task chưa chạy lần nào.
3. Hiểu quan hệ `TaskScheduler.schedule(Runnable, Trigger)`, `Trigger.nextExecution(TriggerContext)` và `SimpleTriggerContext`.
4. Tính độ trễ khởi động `actual - scheduled`, thời gian chạy `completion - actual`, khoảng cách dự kiến và tác động của task chậm.
5. Đọc `PeriodicTrigger` để phân biệt fixed-rate (theo lần dự kiến) và fixed-delay (theo lần hoàn tất).
6. Đọc `CronTrigger` để phân biệt lenient cron (dựa trên completion) với fixed cron (dựa trên scheduled time), từ Spring Framework 6.1.3.
7. Viết một custom `Trigger` có thể sử dụng `getClock()` và các thời điểm trước đó; không dùng `Thread.sleep` để kiểm thử lịch.
8. Chạy ví dụ Maven **Spring Framework 6.1.3 + Java 17+**, gồm phần tính toán bằng đồng hồ cố định và phần thực thi thật với `ThreadPoolTaskScheduler`.
9. Áp dụng đúng trong production: xử lý task chậm, concurrency, exception, cancellation, shutdown, timezone, metrics và distributed deployment.

**Phiên bản:** Java 17+; project minh họa dùng Spring Framework 6.1.3. Từ Spring Framework 6.0, các thời điểm của `TriggerContext` và API `Trigger.nextExecution` dùng `Instant`; các overload trả `java.util.Date` cũ đã deprecated. Một số factory cron “fixed/lenient” được bổ sung ở Spring 6.1.3, nên khi dùng project Spring cũ hơn phải kiểm tra lại API.

## 2. Ba mốc thời gian: planned, actual và completed

`TriggerContext` cung cấp ba method quan trọng:

~~~java
// Chữ ký rút gọn, KHÔNG phải file Java độc lập.
@Nullable Instant lastScheduledExecution();
@Nullable Instant lastActualExecution();
@Nullable Instant lastCompletion();
~~~

Các giá trị đều biểu diễn một **thời điểm tuyệt đối** bằng `Instant`, không phải `Duration`:

| Method | Ý nghĩa | Khi nào hữu ích |
| --- | --- | --- |
| `lastScheduledExecution()` | Mốc thời gian scheduler **đã lên lịch** cho lượt chạy gần nhất | Giữ nhịp fixed-rate, nhận diện lịch bị chậm, xử lý các kỳ dự kiến |
| `lastActualExecution()` | Thời điểm callback **thực sự bắt đầu** chạy gần nhất | Tính scheduler lag, quan sát việc bị trì hoãn do thiếu worker hoặc scheduler congestion |
| `lastCompletion()` | Thời điểm callback **hoàn thành** gần nhất | Fixed-delay, cron lenient, khoảng nghỉ sau khi tác vụ kết thúc |
| `getClock()` | Đồng hồ tham chiếu cho phép tính lần tiếp theo | Lấy “hiện tại” có thể kiểm soát timezone/test; tránh tự dùng thời gian hệ thống trong mọi custom trigger |

**Ví dụ cụ thể:** Một tác vụ được đặt lịch 10:00:00, nhưng worker bận khiến nó bắt đầu 10:00:03; công việc chạy trong 12 giây và kết thúc 10:00:15.

~~~text
                     10:00:00        10:00:03                   10:00:15
                          |               |                        |
   lịch dự kiến ----------+               |                        |
   worker bắt đầu ------------------------+                        |
   callback hoàn tất -----------------------------------------------+
                          |<--- 3 giây --->|<------ 12 giây ------>|
                          |<---------------- 15 giây -------------->|
~~~

Với snapshot này:

~~~text
lastScheduledExecution = 10:00:00Z
lastActualExecution    = 10:00:03Z
lastCompletion         = 10:00:15Z

Scheduler lag      = actual - scheduled = 3 giây
Callback duration  = completion - actual = 12 giây
End-to-end lateness = completion - scheduled = 15 giây
~~~

**Không đánh đồng:** 3 giây scheduler lag chưa chắc do code của chính job chạy chậm; nó có thể do pool chỉ có một worker, hàng đợi, GC hoặc CPU pressure. Ngược lại, 12 giây duration không nói callback mất bao lâu **chờ ở queue** trước khi được chọn chạy.

### 2.1. “Scheduled” là thời điểm hẹn, không bảo đảm đúng giờ

Spring/JDK scheduling không phải hard real-time scheduling. `TaskScheduler` sẽ cố thực thi khi đến hạn, nhưng tác vụ có thể bắt đầu **sau** mốc đó nếu worker chưa rảnh. `lastScheduledExecution()` giúp phân biệt thời điểm hẹn với thời điểm đã chạy thật.

Giả sử hai job đến hạn cùng lúc trên scheduler có một worker. Job A chạy 20 giây; job B được hẹn 10:00:00 nhưng chỉ được thực thi 10:00:20. Trong `TriggerContext` của lịch B, scheduled là 10:00:00 còn actual khoảng 10:00:20. Không thể chỉ xem `lastActualExecution` rồi kết luận B đã được lên lịch lúc 10:00:20.

### 2.2. “Completion” là kết thúc callback, không tự chứng minh nghiệp vụ hoàn tất

Với một `Runnable` đồng bộ thực hiện thao tác và trở về, `lastCompletion()` tương ứng lúc callback đã hoàn tất. Nhưng nếu callback chỉ **gửi một tác vụ sang executor khác** hoặc gọi một service `@Async` rồi return, Spring scheduler có thể ghi nhận callback hoàn thành **trước khi tác vụ thực sự ở executor kia xong**.

~~~java
// Fragment kiến trúc: reportService là bean khác, method generateAsync()
// nộp việc sang executor và trả Future nhanh.
scheduler.schedule(
        () -> reportService.generateAsync(),
        trigger
);
~~~

Khi đó `TriggerContext.lastCompletion()` theo dõi **lần chạy callback của scheduler**, không tự đợi Future của `generateAsync()`. Nếu business yêu cầu “chạy đợt sau khi việc thật hoàn tất”, phải phối hợp theo Future, job store hoặc queue của việc thật; không dùng một timestamp của callback làm bằng chứng mọi downstream I/O đã kết thúc.

## 3. Interface TriggerContext trong Spring Framework 6.1.3

Các chữ ký đáng chú ý:

~~~java
// Rút gọn contract Spring 6.1.x. Không phải interface để biên dịch độc lập.
public interface TriggerContext {
    default Clock getClock() {
        return Clock.systemDefaultZone();
    }

    @Nullable Instant lastScheduledExecution();
    @Nullable Instant lastActualExecution();
    @Nullable Instant lastCompletion();

    // API Date tương thích cũ đã deprecated từ Spring 6.0.
}
~~~

`getClock()` trả `java.time.Clock`; mặc định là đồng hồ hệ thống với múi giờ mặc định. Một `TaskScheduler` có thể cung cấp clock riêng. Một custom trigger nên dựa trên `context.getClock()` để nhất quán với scheduler và dễ viết unit test bằng `Clock.fixed(...)`.

### 3.1. Trước lần chạy đầu tiên các mốc thường là null

Khi Spring tính lần thực thi đầu tiên của một trigger **chưa từng chạy**, không có “lần trước” để tham chiếu. Các method `lastScheduledExecution()`, `lastActualExecution()` và `lastCompletion()` được phép trả `null`. Vì vậy custom trigger không được làm thế này:

~~~java
// SAI: trong lần scheduling đầu tiên lastCompletion có thể null.
Trigger broken = ctx ->
        ctx.lastCompletion().plusSeconds(30);
~~~

Thay vào đó, xử lý trường hợp ban đầu:

~~~java
// Fragment hợp lệ; ví dụ lịch đầu sau 10 giây,
// các lần sau 30 giây tính từ khi callback hoàn tất.
Trigger safe = ctx -> {
    Instant previousEnd = ctx.lastCompletion();
    if (previousEnd == null) {
        return ctx.getClock().instant().plusSeconds(10);
    }
    return previousEnd.plusSeconds(30);
};
~~~

Một trigger có thể chủ động trả `null` từ `nextExecution` để chỉ ra **không còn lần chạy tiếp**. Khi gọi `TaskScheduler.schedule(task, trigger)`, nếu trigger đã trả `null` ngay lần đầu, API có thể trả **`null` thay vì `ScheduledFuture`**. Hãy kiểm tra handle khi API trả nullable.

### 3.2. Clock không phải là timestamp thứ tư của lần trước

`getClock().instant()` biểu diễn “bây giờ” theo clock được cung cấp. Nó có thể khác `lastCompletion()`, vì clock hiện tại vẫn trôi sau khi task kết thúc. Tại lần đầu, nếu không có lịch sử, custom trigger có thể dựa vào clock; tại các lần tiếp theo, chính sách lịch quyết định dựa vào scheduled, actual hay completion.

Không lấy `Instant.now()` ở khắp nơi khi logic cần cùng nguồn thời gian với scheduler; cách viết dựa vào `context.getClock()` giúp test và tính cron/timezone nhất quán hơn.

### 3.3. Date API cũ: nên tránh trong dự án Spring 6.x

Các tên như `lastScheduledExecutionTime()`, `lastActualExecutionTime()`, `lastCompletionTime()` dùng `java.util.Date` và đã **deprecated từ Spring 6.0**. Với Java 17 và Spring 6.1+, dùng bộ method trả `Instant`. Tránh trộn `Date`/`Instant` và timezone conversions không cần thiết trong custom trigger.

## 4. Trigger.nextExecution(context) chọn thời điểm chạy tiếp

Spring `org.springframework.scheduling.Trigger` là một chiến lược tính lịch:

~~~java
// Chữ ký rút gọn của interface Trigger (Spring Framework 6.x).
@Nullable
Instant nextExecution(TriggerContext triggerContext);
~~~

`TaskScheduler.schedule(Runnable task, Trigger trigger)` nhận một callback và một thuật toán xác định lần tới. Khác với `scheduleAtFixedRate(...)` được tính sẵn bằng chu kỳ JDK, `schedule(task, trigger)` có thể **hỏi lại Trigger sau mỗi lần chạy** để chọn một thời điểm mới.

~~~text
TaskScheduler.schedule(task, trigger)
               |
               v
     Trigger.nextExecution(context)
               |
          tính Instant T1
               |
               v
     Scheduler hẹn callback lúc T1
               |
               v
       Callback thực thi
       - ghi actual start
       - chạy Runnable
       - ghi completion
               |
               v
  cập nhật TriggerContext với:
  scheduled=T1, actual, completion
               |
               v
     Trigger.nextExecution(context)
               |
          tính Instant T2 hoặc null
               |
         T2 -> tiếp tục / null -> dừng
~~~

Sơ đồ phản ánh đường đi thông thường của `ThreadPoolTaskScheduler` với một `Trigger` và một callback đồng bộ. Nếu scheduler shutdown, task bị hủy, handler propagate exception hoặc có lỗi lập lịch, chuỗi có thể kết thúc sớm; không phải mọi callback luôn đi tới một lần tính lịch tiếp theo.

### 4.1. Scheduler giữ ngữ cảnh riêng cho mỗi registration

Một `TriggerContext` thuộc vòng điều phối **một task đã được schedule với một trigger**, không phải một biến toàn cục để tất cả lịch đọc/ghi chung. Nếu dùng cùng một class/method với **hai registrations độc lập**, mỗi registration có thể có lịch sử scheduled/actual/completion riêng.

Không dùng `static SimpleTriggerContext` tự chia sẻ cho tất cả jobs: việc này làm lịch A ảnh hưởng tới lịch B và có thể tạo race. Spring đã quản lý context nội bộ cho các registration mà nó sở hữu.

### 4.2. TriggerContext không tự biết exception hay business outcome

Chỉ có ba thời điểm và clock. Nó không chứa kết quả `T`, exception cause, số lần retry, status database, số bản ghi thành công hoặc cờ “lần trước timeout”. Một trigger muốn thay đổi thời gian theo **thành công/thất bại** cần dữ liệu bên ngoài được đồng bộ an toàn (ví dụ policy state, atomic status, DB/job store), không suy ngược thành công từ việc `lastCompletion()` khác null.

Với Spring `ThreadPoolTaskScheduler`, các repeating tasks mặc định thường được bọc để **log và suppress exception**, cho phép scheduling tiếp tục; một handler tùy chỉnh có thể đổi hành vi. Vì vậy không suy luận “có lastCompletion nghĩa là lần trước thành công nghiệp vụ”.

## 5. SimpleTriggerContext: tạo snapshot trong unit test

Spring có class `org.springframework.scheduling.support.SimpleTriggerContext`, một implementation giữ ba timestamp. Bạn có thể dùng nó để **kiểm thử thuật toán của Trigger** mà không cần chạy worker thread hoặc đợi thời gian thật.

Ví dụ các API:

~~~java
// Fragment Spring 6.x, yêu cầu imports.
Clock fixed = Clock.fixed(
        Instant.parse("2025-04-01T10:00:00Z"),
        ZoneOffset.UTC);

SimpleTriggerContext context = new SimpleTriggerContext(fixed);

// Trước lần đầu: ba trường đều null.
boolean fresh = context.lastScheduledExecution() == null
        && context.lastActualExecution() == null
        && context.lastCompletion() == null;

context.update(
        Instant.parse("2025-04-01T10:00:00Z"), // planned
        Instant.parse("2025-04-01T10:00:03Z"), // actual start
        Instant.parse("2025-04-01T10:00:15Z")  // callback finished
);
~~~

`SimpleTriggerContext.update(Instant, Instant, Instant)` mô phỏng một lần chạy; nó **không thực sự gửi task tới executor**. Vì ba timestamp do test đưa vào, bạn có thể viết assertion chính xác về cách `PeriodicTrigger`/`CronTrigger` tính thời điểm mới.

Lưu ý: `SimpleTriggerContext` dùng được để test/diễn giải; một callback `@Scheduled` thông thường **không thể thêm tham số `TriggerContext` rồi chờ Spring inject**. Spring trao context cho **Trigger.nextExecution** trong đường đăng ký qua `TaskScheduler.schedule(task, trigger)`, không phải mặc định cho method nghiệp vụ.

## 6. Fixed-rate và fixed-delay: khác nhau ở timestamp được chọn

`PeriodicTrigger` cho phép chuyển đổi chính sách fixed-delay và fixed-rate trong một `Trigger`. Với Spring 6.1.3:

- `new PeriodicTrigger(Duration.ofSeconds(30))` mặc định là **fixed-delay**: lần sau dựa trên `lastCompletion()`.
- Gọi `setFixedRate(true)` thì lịch trở thành **fixed-rate**: lần sau dựa trên `lastScheduledExecution()` để giữ nhịp.
- Khi chưa có lần chạy trước, trigger sử dụng clock và initial delay nếu được cấu hình.

### 6.1. Một ví dụ ba timestamp, hai câu trả lời

Lần trước:

~~~text
scheduled   = 10:00:00
actual      = 10:00:03
completion  = 10:00:15
period      = 30 giây
~~~

Fixed-delay:

~~~text
next = lastCompletion + 30 giây
     = 10:00:15 + 00:00:30
     = 10:00:45
~~~

Fixed-rate:

~~~text
next = lastScheduledExecution + 30 giây
     = 10:00:00 + 00:00:30
     = 10:00:30
~~~

| Chính sách | Mốc lịch trước được dùng | Mốc lần tiếp |
| --- | --- | --- |
| `PeriodicTrigger` fixed-delay (mặc định) | `lastCompletion()` = 10:00:15 | **10:00:45** |
| `PeriodicTrigger` fixed-rate | `lastScheduledExecution()` = 10:00:00 | **10:00:30** |

Mốc fixed-rate 10:00:30 **không có nghĩa callback chắc chắn bắt đầu đúng 10:00:30**. Nếu scheduler bận tới 10:00:40, actual có thể trễ; sau đó context vẫn phân biệt scheduled và actual.

**Quan trọng:** `lastActualExecution()` rất hữu ích cho **đo trễ**. Nhưng `PeriodicTrigger` fixed-rate không dùng actual làm chuẩn, vì như vậy nhịp lịch có thể tự trôi theo lần worker bắt đầu muộn.

### 6.2. Khi task chạy lâu hơn period

Giả sử task mất 45 giây, period 30 giây. Fixed-rate theo scheduled time có thể tính mốc lần tiếp đã ở **quá khứ** khi task hiện tại xong, dẫn tới việc scheduler chạy lần kế **sớm nhất có thể**, không tạo ra nhiều worker cho một registration chỉ vì cần “đuổi kịp”.

Fixed-delay lại đợi thêm 30 giây **sau lúc task đã xong**, nên không tự tạo chuỗi callback sát nhau để bắt kịp các mốc trước.

Nếu business yêu cầu mỗi kỳ lịch phải được xử lý đúng một lần, cần thiết kế business-period IDs, idempotency, lưu trạng thái và catch-up phù hợp. Trigger là bộ tính **thời điểm**, không phải job store bền vững.

## 7. CronTrigger: dùng completion hay scheduled làm mốc tính?

`CronTrigger` đọc biểu thức cron theo timezone, sau đó chọn lần hợp lệ tiếp theo dựa trên một mốc tham chiếu. Ở Spring Framework 6.1.3, API hỗ trợ hai chính sách rõ ràng:

- **Lenient execution:** tính từ `lastCompletion()`, có thể **bỏ những mốc cron xảy ra trong lúc callback trước còn chạy**. Constructor `new CronTrigger(expression)` tương đương lenient mặc định; từ Spring 6.1.3 còn có `CronTrigger.forLenientExecution(expression)`.
- **Fixed execution:** tính từ `lastScheduledExecution()`, nhằm **không bỏ các mốc cron dự kiến**; khi callback chậm, các lần tiếp có thể phải chạy trễ để bù. Dùng `CronTrigger.forFixedExecution(expression)` từ **Spring 6.1.3**.

### 7.1. Thử với cron mỗi phút

Biểu thức `0 * * * * *` có nghĩa chạy ở giây **0 của mỗi phút** theo cú pháp sáu trường của Spring. Giả sử:

~~~text
Lần trước được hẹn:        10:01:00
Lần trước thực sự bắt đầu: 10:01:20
Lần trước hoàn tất:        10:02:10
Các mốc cron:              10:01, 10:02, 10:03, 10:04, ...
~~~

Cách tính:

~~~text
Lenient: next cron sau completion 10:02:10 => 10:03:00
Fixed:   next cron sau scheduled  10:01:00 => 10:02:00
~~~

Mốc fixed 10:02:00 **đã qua** khi callback trước kết thúc 10:02:10. Scheduler có thể kích hoạt nó **muộn, sớm nhất khi có worker phù hợp**, để xử lý một kỳ lịch đã bỏ lỡ. Việc thực thi chính xác còn tùy executor và số worker.

Không khẳng định fixed trigger tự làm “replay giao dịch bền vững” sau JVM restart. Nó chỉ xác định mốc theo context **đang tồn tại**. Nếu cần resume sau downtime, Spring 6.1.3 còn có `resumeFixedExecution(...)` và `resumeLenientExecution(...)` với resumption timestamp; bản thân timestamp/resumption state phải được **lưu và quản lý bởi ứng dụng**.

### 7.2. Timezone ảnh hưởng cron, không làm ba Instant biến thành local time

`Instant` là thời điểm độc lập timezone. Cron interpretation lại phụ thuộc zone. Với biểu thức hằng ngày, phải xác định timezone (ví dụ `ZoneId.of("Asia/Ho_Chi_Minh")`) hoặc clock được dùng trong scheduler; nếu để mặc định, môi trường test, staging và production có thể tính giờ khác nhau.

~~~java
// Fragment, không phải chương trình độc lập.
CronTrigger daily = new CronTrigger(
        "0 0 2 * * *",
        ZoneId.of("Asia/Ho_Chi_Minh")
);
~~~

Tại những timezone có DST, một giờ lịch có thể lặp hoặc không tồn tại. Không giả định CronTrigger luôn tương đương cộng thêm đúng 24 giờ theo `Duration`, và không lấy `Instant` đổi qua giờ địa phương rồi cộng 1 ngày mà không xét zone.

## 8. Ví dụ Spring Framework 6.1.3 + Java 17+ chạy độc lập

Project này vừa kiểm tra ba timestamp **mà không chờ thật**, vừa dùng `ThreadPoolTaskScheduler` để xác nhận scheduler **trao context đã cập nhật** cho một custom trigger sau lần callback đầu.

### 8.1. Cấu trúc project

~~~text
spring-triggercontext-demo/
├── pom.xml
└── src/main/java/demo/
    └── TriggerContextDemo.java
~~~

Project dùng Spring Framework thuần; **không cần Spring Boot server** hay `@EnableScheduling` vì demo gọi trực tiếp `TaskScheduler.schedule(task, trigger)`.

### 8.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-triggercontext-demo</artifactId>
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
                    <mainClass>demo.TriggerContextDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 8.3. TriggerContextDemo.java

~~~java
package demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

public final class TriggerContextDemo {

    @Configuration
    public static class SchedulerConfig {
        @Bean("lessonScheduler")
        public ThreadPoolTaskScheduler lessonScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("trigger-lesson-");
            return scheduler;
        }
    }

    private static void demonstrateTimeMath() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2025-04-01T10:00:00Z"),
                ZoneOffset.UTC);

        SimpleTriggerContext context =
                new SimpleTriggerContext(fixedClock);

        boolean initialEmpty = context.lastScheduledExecution() == null
                && context.lastActualExecution() == null
                && context.lastCompletion() == null;
        System.out.println("initialEmpty=" + initialEmpty);

        Instant scheduled = Instant.parse("2025-04-01T10:00:00Z");
        Instant actual = Instant.parse("2025-04-01T10:00:03Z");
        Instant completed = Instant.parse("2025-04-01T10:00:15Z");
        context.update(scheduled, actual, completed);

        System.out.println("scheduled=" + context.lastScheduledExecution());
        System.out.println("actual=" + context.lastActualExecution());
        System.out.println("completed=" + context.lastCompletion());

        System.out.println("lagSeconds="
                + Duration.between(
                        context.lastScheduledExecution(),
                        context.lastActualExecution()).getSeconds());
        System.out.println("runSeconds="
                + Duration.between(
                        context.lastActualExecution(),
                        context.lastCompletion()).getSeconds());

        PeriodicTrigger fixedDelay =
                new PeriodicTrigger(Duration.ofSeconds(30));

        PeriodicTrigger fixedRate =
                new PeriodicTrigger(Duration.ofSeconds(30));
        fixedRate.setFixedRate(true);

        System.out.println("fixedDelayNext="
                + fixedDelay.nextExecution(context));
        System.out.println("fixedRateNext="
                + fixedRate.nextExecution(context));

        // Cron mỗi phút; task đã vượt quá mốc 10:02:00.
        SimpleTriggerContext cronContext =
                new SimpleTriggerContext(fixedClock);

        cronContext.update(
                Instant.parse("2025-04-01T10:01:00Z"),
                Instant.parse("2025-04-01T10:01:20Z"),
                Instant.parse("2025-04-01T10:02:10Z"));

        CronTrigger lenient =
                new CronTrigger("0 * * * * *", ZoneId.of("UTC"));

        CronTrigger fixed =
                CronTrigger.forFixedExecution("0 * * * * *");

        System.out.println("lenientNext="
                + lenient.nextExecution(cronContext));
        System.out.println("fixedCronNext="
                + fixed.nextExecution(cronContext));
    }

    private static final class RecordingTrigger implements Trigger {
        private final AtomicInteger calculations = new AtomicInteger();
        private final CountDownLatch secondCalculation =
                new CountDownLatch(1);
        private final AtomicBoolean historyPresent =
                new AtomicBoolean(false);

        @Override
        public Instant nextExecution(TriggerContext context) {
            int call = calculations.incrementAndGet();

            if (call == 1) {
                // Lần đầu chưa có lịch sử: chạy sớm nhất có thể.
                return context.getClock().instant();
            }

            // Spring gọi lần nữa sau khi callback thứ nhất kết thúc.
            historyPresent.set(
                    context.lastScheduledExecution() != null
                            && context.lastActualExecution() != null
                            && context.lastCompletion() != null);
            secondCalculation.countDown();

            return null; // chủ động kết thúc registration này
        }
    }

    private static void demonstrateLiveScheduler() throws Exception {
        try (AnnotationConfigApplicationContext app =
                     new AnnotationConfigApplicationContext(
                             SchedulerConfig.class)) {

            ThreadPoolTaskScheduler scheduler =
                    app.getBean("lessonScheduler",
                            ThreadPoolTaskScheduler.class);

            AtomicInteger taskRuns = new AtomicInteger();
            RecordingTrigger trigger = new RecordingTrigger();

            ScheduledFuture<?> handle =
                    scheduler.schedule(taskRuns::incrementAndGet, trigger);

            if (handle == null) {
                throw new IllegalStateException("Trigger did not schedule");
            }

            if (!trigger.secondCalculation.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "History was not updated within timeout");
            }

            System.out.println("liveRuns=" + taskRuns.get());
            System.out.println("liveHistoryPresent="
                    + trigger.historyPresent.get());
            System.out.println("triggerCalculations="
                    + trigger.calculations.get());
        }
    }

    public static void main(String[] args) throws Exception {
        demonstrateTimeMath();
        demonstrateLiveScheduler();
    }
}
~~~

### 8.4. Chạy bằng Maven

Trong thư mục `spring-triggercontext-demo`:

~~~bash
mvn -q compile exec:java
~~~

**Stdout do chương trình Java in khi chạy thành công:**

~~~text
initialEmpty=true
scheduled=2025-04-01T10:00:00Z
actual=2025-04-01T10:00:03Z
completed=2025-04-01T10:00:15Z
lagSeconds=3
runSeconds=12
fixedDelayNext=2025-04-01T10:00:45Z
fixedRateNext=2025-04-01T10:00:30Z
lenientNext=2025-04-01T10:03:00Z
fixedCronNext=2025-04-01T10:02:00Z
liveRuns=1
liveHistoryPresent=true
triggerCalculations=2
~~~

### 8.5. Giải thích từng kết quả

- `initialEmpty=true`: `SimpleTriggerContext` mới không có dữ liệu về lần chạy trước.
- `scheduled/actual/completed`: `update` ghi đúng ba `Instant` đã chỉ định; đây là dữ liệu test, **không phải** thời gian lấy từ máy đang chạy.
- `lagSeconds=3`: worker được mô phỏng bắt đầu muộn 3 giây so với mốc hẹn.
- `runSeconds=12`: callback được mô phỏng thực thi từ 10:00:03 tới 10:00:15.
- `fixedDelayNext=10:00:45Z`: 10:00:15 + 30 giây.
- `fixedRateNext=10:00:30Z`: 10:00:00 + 30 giây.
- `lenientNext=10:03:00Z`: cron mỗi phút lấy mốc sau completion 10:02:10.
- `fixedCronNext=10:02:00Z`: fixed cron tính từ scheduled 10:01:00 và xác định kỳ 10:02:00 dù mốc đó đã qua tại lúc task hoàn tất.
- `liveRuns=1`: callback được Spring scheduler gọi đúng một lần, sau đó custom trigger trả `null` để không có lịch tiếp.
- `liveHistoryPresent=true`: ở lần Spring gọi `nextExecution` thứ hai, cả ba timestamp đã có dữ liệu.
- `triggerCalculations=2`: một lần để lập lịch đầu tiên, một lần sau callback hoàn tất để quyết định dừng.

Ví dụ **không in timestamp realtime** trong phần live; chỉ kiểm tra presence và số lần. Vì callback được chạy đúng một lần và có `CountDownLatch` để chờ lần tính thứ hai, stdout không phụ thuộc tên thread, mili giây chính xác hoặc thứ tự log giữa scheduler workers.

**Giới hạn môi trường:** `await(5, TimeUnit.SECONDS)` giúp bài test không treo vô hạn. Nếu scheduler không thể thực thi callback trong 5 giây do môi trường đình trệ, chương trình ném exception rõ ràng thay vì in trạng thái thành công giả. Đóng `AnnotationConfigApplicationContext` giúp Spring dọn scheduler bean.

## 9. Thiết kế một custom Trigger dựa trên completion

Cách đơn giản nhất là hẹn sau một khoảng nghỉ so với `lastCompletion()`, với fallback cho lần đầu:

~~~java
// Fragment trong Spring 6.x, biến task và scheduler đã có.
Trigger afterCompletion = ctx -> {
    Instant completed = ctx.lastCompletion();
    Instant base = completed != null
            ? completed : ctx.getClock().instant();
    return base.plus(Duration.ofSeconds(30));
};

ScheduledFuture<?> future =
        scheduler.schedule(task, afterCompletion);
~~~

Đây là **fragment**, không tự biên dịch: cần có `scheduler` kiểu `TaskScheduler`, `task` kiểu `Runnable` và các imports. Chính sách này tạo một lịch gần giống fixed-delay 30 giây khi có lịch sử; thời điểm đầu tiên cách clock hiện tại 30 giây.

### 9.1. Dựa vào scheduler lag để giảm tải

Giả sử muốn **nới khoảng nghỉ** nếu lần trước bắt đầu muộn hơn 10 giây do scheduler bận:

~~~java
// Fragment: minh họa CHIẾN LƯỢC, không phải ứng dụng độc lập.
Trigger adaptive = ctx -> {
    Instant lastEnd = ctx.lastCompletion();
    if (lastEnd == null) {
        return ctx.getClock().instant().plusSeconds(10);
    }

    Instant scheduled = ctx.lastScheduledExecution();
    Instant actual = ctx.lastActualExecution();

    Duration lag = scheduled != null && actual != null
            ? Duration.between(scheduled, actual)
            : Duration.ZERO;

    Duration pause = lag.compareTo(Duration.ofSeconds(10)) > 0
            ? Duration.ofMinutes(2)
            : Duration.ofSeconds(30);

    return lastEnd.plus(pause);
};
~~~

Khi lag lớn, trigger thử nới khoảng nghỉ, nhưng đây **không phải thuật toán autoscaling hoàn chỉnh**. Trễ có thể do GC, starvation, đồng hồ tường bị điều chỉnh hoặc downstream; việc giảm tần suất đôi khi có ích, đôi khi làm backlog tăng. Đo metrics và xem các constraint nghiệp vụ trước khi áp dụng.

### 9.2. Đừng kết hợp dữ liệu trạng thái mà không quản lý concurrency

Nếu custom trigger cần biết số lần thất bại, timestamp của job hoàn tất ngoài executor, khả năng kết nối hoặc một flag tạm dừng, các dữ liệu đó phải được cung cấp qua **state store có thread-safety và lifecycle thích hợp**. `TriggerContext` không tự đưa tới thông tin này.

Tránh đọc/ghi các biến `static` mutable trong `nextExecution` từ nhiều schedulers hoặc nhiều application contexts mà không đồng bộ; mỗi registration nên có policy/state rõ ràng. Với một job dài, đừng dùng timestamp completion để làm căn cứ “không có task đang chạy” khi callback chỉ đã submit một `@Async` job rồi return.

## 10. Theo dõi độ trễ và thời gian thực thi

Dùng các timestamp của context có thể tính:

~~~java
// Fragment: chỉ chạy khi context có đủ ba timestamp.
Duration scheduleLag =
        Duration.between(context.lastScheduledExecution(),
                         context.lastActualExecution());

Duration callbackDuration =
        Duration.between(context.lastActualExecution(),
                         context.lastCompletion());

Duration totalElapsed =
        Duration.between(context.lastScheduledExecution(),
                         context.lastCompletion());
~~~

| Metric | Công thức | Ý nghĩa |
| --- | --- | --- |
| Schedule lag | `actual - scheduled` | Scheduler trễ bao lâu so với deadline đã lập |
| Callback duration | `completion - actual` | Callback chạy bao lâu |
| Scheduled-to-completion | `completion - scheduled` | Tổng trễ từ mốc dự kiến tới lúc callback hoàn tất |
| Fixed-delay gap | `nextScheduled - previousCompletion` | Khoảng nghỉ hẹn từ lúc hoàn tất |
| Fixed-rate drift | `nextScheduled - previousScheduled` | Nhịp dự kiến giữa hai lần |

**Hai giới hạn của timestamp đo theo `Instant`:**

1. Đây là **wall-clock timestamps**. Nếu đồng hồ hệ thống bị chỉnh NTP hoặc thủ công, chênh lệch có thể âm hoặc méo. Với latency instrumentation cần độ chính xác cao, đo duration bằng `System.nanoTime()` hoặc timer của observability framework ngay trong callback.
2. `TriggerContext` chỉ giữ các mốc **lần gần nhất**, không tự lưu toàn bộ series để tính histogram/p95/p99. Muốn báo cáo theo lịch, ghi metric ở mỗi lần callback và theo dõi ID/label của registration.

Không khẳng định `lastCompletion()` là timestamp commit DB, nhận ACK từ broker hoặc remote API hoàn tất nếu callback chỉ gửi việc qua API async.

## 11. Về concurrency và khả năng chồng lấn

### 11.1. Một đăng ký Trigger, một chuỗi lần chạy

Với `ThreadPoolTaskScheduler.schedule(Runnable, Trigger)` truyền thống, scheduler thường tính mốc lần tới **sau khi callback hiện tại hoàn tất**. Điều này giúp một chuỗi task dựa trên completion không tự đẻ các lần chạy chồng lên nhau khi cùng registration đang còn thực thi.

Nhưng nếu bạn đăng ký **hai triggers khác nhau** cho cùng một callback hoặc đặt **nhiều `@Scheduled` trên một method**, đó là **các lịch độc lập**. Chúng có thể overlap khi scheduler có nhiều workers.

~~~java
// Fragment: đây là HAI registrations độc lập.
ScheduledFuture<?> first = scheduler.schedule(task, triggerA);
ScheduledFuture<?> second = scheduler.schedule(task, triggerB);
~~~

`TriggerContext` của `first` và `second` là lịch sử riêng của từng registration. Đừng mong `lastCompletion()` của A tự là thời điểm completion của B.

### 11.2. Khi callback submit @Async rồi return

~~~java
// Fragment: callback kết thúc ngay sau khi submit.
scheduler.schedule(
        () -> asyncService.processAsync(),
        trigger
);
~~~

Nếu việc thật chạy 10 phút nhưng callback chỉ mất 1 ms để nộp vào worker pool, `lastCompletion()` của scheduler sẽ phản ánh kết thúc callback 1 ms, không phải mốc việc thật hoàn thành. Fixed-delay dựa trên timestamp đó có thể tạo thêm công việc thật đang chồng lấn.

Nếu business đòi hỏi không overlap, hãy đặt concurrency guard/queue/claim ở nơi **việc thật** được xử lý và điều phối theo Future hoàn tất, thay vì chỉ đổi thuật toán Trigger.

### 11.3. Nhiều JVM không chia sẻ TriggerContext

Với ba replicas, mỗi instance có thể tạo một `ThreadPoolTaskScheduler` và context riêng. Các mốc “lần chạy trước” **không được đồng bộ qua JVM** chỉ bằng interface `TriggerContext`. Nếu cần chạy đúng một lần trên toàn cluster, dùng distributed lock, leader election, durable job store hoặc queue có policy rõ ràng, kèm idempotency.

## 12. Lỗi và hủy lịch: context không phải cơ chế khôi phục

### 12.1. Exception trong callback

Ở JDK `ScheduledThreadPoolExecutor`, exception chưa được xử lý trong periodic task có thể làm chuỗi chạy định kỳ **dừng các lần sau**. Spring `ThreadPoolTaskScheduler` thường bọc repeating callbacks bằng một `ErrorHandler` mặc định **log và suppress** lỗi để tiếp tục; handler tùy chỉnh có thể đổi policy.

`TriggerContext` không ghi lại cause của exception hay cờ successful. Sau một lần callback lỗi nhưng được handler suppress, scheduler có thể tiếp tục tính lịch theo thời điểm callback kết thúc. Điều đó không có nghĩa nhiệm vụ đã đạt kết quả nghiệp vụ đúng.

### 12.2. Cancel và shutdown

`TaskScheduler.schedule(task, trigger)` trả `ScheduledFuture<?>` (có thể `null` nếu trigger không có lần fire đầu). Cần giữ handle nếu ứng dụng muốn hủy lịch động.

~~~java
// Fragment: future có thể null nếu trigger không tạo lịch đầu.
if (future != null) {
    future.cancel(false); // dừng các lần dự kiến sau, không yêu cầu interrupt
}
~~~

`cancel(true)` có thể yêu cầu interrupt tùy implementation, nhưng không bảo đảm dừng ngay I/O hoặc rollback side effects. `ThreadPoolTaskScheduler` phải được quản lý lifecycle qua Spring bean; không tạo pool mới cho từng request hoặc quên shutdown khi application context dừng.

### 12.3. Trigger trả null là một cách kết thúc lịch

Khi `nextExecution(context)` trả `null`, Spring hiểu **không có thời điểm tiếp theo** cho registration. Điều này hữu ích cho số lần chạy hữu hạn hoặc điều kiện dừng đã được policy quyết định. Đừng dùng `Instant.MAX` như “không bao giờ nữa”; hãy trả `null` đúng contract.

Trong ví dụ `RecordingTrigger` ở phần 8, việc trả `null` từ lần tính thứ hai cho phép demo dừng gọn sau một lần callback; ta không cần sleep thật lâu hoặc chờ một period lớn.

## 13. Những lỗi thường gặp khi đọc TriggerContext

1. **Nhầm scheduled với actual:** thời điểm đã hẹn không phải thời điểm worker thật bắt đầu.
2. **Nhầm actual với completion:** callback có thể chạy lâu, nên hai timestamp khác nhau.
3. **Nhầm completion với thành công:** Spring error handler có thể log/suppress lỗi mà vẫn hoàn tất callback.
4. **Giả định lần đầu không có null:** trước lần chạy đầu tiên thường thiếu cả ba timestamp.
5. **Dùng `lastActualExecution` để tính fixed-rate:** nhịp theo actual có thể bị trôi nếu worker khởi động muộn; policy fixed-rate thường dùng scheduled.
6. **Dùng `lastScheduledExecution` để tính fixed-delay:** fixed-delay cần tính từ completion để có khoảng nghỉ đúng.
7. **Giả định cron lenient tự chạy bù mọi mốc đã lỡ:** nó có thể bỏ mốc khi task chạy quá lâu.
8. **Giả định fixed cron là job store bền vững:** nếu JVM mất lịch sử, cần state persistence/resume riêng.
9. **Nhầm `getClock()` với “last completion”:** clock là nguồn giờ hiện tại; last completion là dấu mốc đã ghi nhận.
10. **Dùng Date API deprecated trong Spring 6.x:** ưu tiên `Instant`.
11. **Truyền `TriggerContext` như tham số của method `@Scheduled`:** Spring không mặc định inject đối tượng này vào callback nghiệp vụ.
12. **Tự tạo `SimpleTriggerContext` rồi nghĩ đã đăng ký chạy thật:** class đó là state holder, không tự điều phối executor.
13. **Dùng `Thread.sleep` để unit test thuật toán NextExecution:** dùng `Clock.fixed` và `SimpleTriggerContext.update` sẽ có kết quả chính xác hơn.
14. **Cho rằng context theo dõi toàn bộ job history:** chỉ giữ mốc gần nhất, không phải audit trail.
15. **Nhiều registrations dùng chung một context tự chế:** dễ tạo logic sai và data race; tách state theo registration.
16. **Tính duration wall-clock rồi coi đó là đo latency không thể bị ảnh hưởng:** NTP/clock adjustment có thể làm méo `Duration.between`; đo duration bằng monotonic clock.
17. **Callback gửi `@Async` xong thì cho rằng lastCompletion là lúc job thật xong:** sai nếu callback chỉ submit.
18. **Cho rằng scheduler local có state dùng chung giữa replicas:** mỗi JVM có lifecycle/context riêng.
19. **Không xét timezone/DST khi dùng cron:** các mốc lịch theo local time không tương đương mọi lúc với duration cố định.
20. **Làm custom trigger ném exception và trông chờ scheduler tự retry theo nghiệp vụ:** cần xử lý lỗi rõ ràng ở trigger logic và hệ thống scheduling.

## 14. Checklist code review và vận hành

### 14.1. Đúng semantics

- Xác định task được schedule bằng `schedule(Runnable, Trigger)` hay bằng `scheduleAtFixedRate`/`scheduleWithFixedDelay`, vì đường điều phối có thể khác nhau.
- Xác định ba mốc có ý nghĩa với **callback scheduler** hay với **toàn bộ business operation**.
- Định nghĩa kỳ vọng: “giữ nhịp theo lịch”, “đợi sau completion”, “bỏ lỡ được”, hay “không được bỏ kỳ nào”.
- Cài null check cho mọi field được phép chưa có, đặc biệt trước lần chạy đầu.
- Dùng `Clock` của context, timezone tường minh cho cron; test bằng fixed Clock khi tính lịch.
- Xem lại các trigger cố thực thi mốc trong quá khứ: executor có thể phải chạy càng sớm càng tốt, gây nhiều callbacks sát nhau.
- Giữ policy khi callback lỗi, timeout hoặc bị hủy; `TriggerContext` không mang outcome.
- Phân tách metric scheduler lag và callback duration, không đánh đồng với thời gian upstream/downstream operation.

### 14.2. Tính đúng và độ bền

- Với side effects, gắn business-period ID, idempotency key và claim atomic.
- Không giả định một lịch local chỉ chạy một lần trên toàn cụm.
- Nếu cần resume, lưu watermark/last processed period theo nghiệp vụ; không coi `SimpleTriggerContext` đang nằm trong RAM là job store.
- Thiết kế bounded concurrency và timeout của HTTP/DB nếu callback có blocking I/O.
- Khi task đổi sang async executor, theo dõi kết quả thật và policy overlap ở executor/queue, không chỉ trên scheduled callback.
- Dùng monitoring để quan sát fire count, missed fires, late executions, failures và nguyên nhân drift.

### 14.3. Gợi ý kiểm thử

| Test | Kiểm tra |
| --- | --- |
| Context mới | `lastScheduledExecution()`/`lastActualExecution()`/`lastCompletion()` đều `null` |
| Lần chạy có lag 3s, duration 12s | Tính được hai metric chính xác |
| Fixed-delay 30s | Next dựa trên 10:00:15 => 10:00:45 |
| Fixed-rate 30s | Next dựa trên 10:00:00 => 10:00:30 |
| Cron lenient mỗi phút | Completion 10:02:10 => 10:03:00 |
| Cron fixed mỗi phút | Scheduled 10:01:00 => 10:02:00 |
| Custom Trigger lần đầu | Có đường xử lý null với `getClock()` |
| Trigger trả `null` sau một lần | Scheduler dừng registration gọn |
| Callback thực kết thúc | Spring tính next execution với cả ba mốc non-null |
| Callback nộp async work | Context completion không tự đợi work |
| Scheduler shutdown/restart | Có giải pháp khôi phục trạng thái nghiệp vụ khi cần |
| Nhiều registrations/replicas | Không tự dùng chung context hay bảo đảm single execution |

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `lastScheduledExecution()` và `lastActualExecution()` có thể khác nhau như thế nào?  
**Đáp án:** Scheduled là thời điểm được hẹn, actual là thời điểm callback thực bắt đầu. Khi worker bận, actual có thể trễ so với scheduled.

**Câu 2.** Task được hẹn 10:00:00, bắt đầu 10:00:03, hoàn tất 10:00:15. Scheduler lag và callback duration bằng bao nhiêu?  
**Đáp án:** Lag = 3 giây; duration = 12 giây.

**Câu 3.** Vì sao `lastCompletion()` không phải bằng chứng thao tác business đã thành công?  
**Đáp án:** Nó ghi mốc callback hoàn tất, không ghi outcome. Callback có thể lỗi được handler suppress hoặc chỉ submit một việc async rồi return.

**Câu 4.** Custom Trigger được gọi lần đầu, `lastCompletion()` trả `null` thì phải làm gì?  
**Đáp án:** Chọn mốc khởi đầu từ `context.getClock().instant()` hoặc một chính sách initial schedule cụ thể, không gọi method lên `null`.

**Câu 5.** Với PeriodicTrigger period 30s, context có scheduled=10:00:00 và completion=10:00:15, lần tiếp của fixed-rate so với fixed-delay là gì?  
**Đáp án:** Fixed-rate = 10:00:30; fixed-delay = 10:00:45.

**Câu 6.** Một CronTrigger lenient mỗi phút, callback trước hoàn tất 10:02:10; lần tiếp theo là lúc nào?  
**Đáp án:** 10:03:00 (theo cùng timezone, biểu thức ở giây 0 mỗi phút), có thể bỏ mốc 10:02:00.

**Câu 7.** Fixed cron dùng `lastScheduledExecution()` thay vì `lastCompletion()` có ý nghĩa gì?  
**Đáp án:** Nó giữ các mốc dự kiến và có thể tính một kỳ đã qua làm mốc tiếp, khiến scheduler thực thi bù muộn thay vì bỏ kỳ; không đồng nghĩa lưu job bền vững sau restart.

**Câu 8.** `SimpleTriggerContext.update(...)` có tự khiến scheduler chạy task không?  
**Đáp án:** Không. Đây là cập nhật state holder để test/tính trigger; muốn thực thi phải đăng ký với `TaskScheduler`.

**Câu 9.** `Trigger.nextExecution(context)` trả `null` mang ý nghĩa gì?  
**Đáp án:** Không có lần chạy tiếp theo; khi ngay lần đầu, `TaskScheduler.schedule(...)` có thể trả `null` thay vì handle.

**Câu 10.** Tại sao nên dùng `context.getClock()` trong custom Trigger?  
**Đáp án:** Nó dùng đồng hồ của scheduler hoặc clock được cung cấp, giúp thời gian tính nhất quán và test deterministic bằng `Clock.fixed`.

**Câu 11.** Hai registrations cùng dùng một callback có dùng chung TriggerContext không?  
**Đáp án:** Không nên giả định. Mỗi registration có lịch sử tính lịch riêng trong điều phối thông thường; nếu tự chia sẻ state holder, bạn có thể tạo race và ảnh hưởng chéo.

**Câu 12.** `lastCompletion` có tự đồng bộ giữa ba replicas trong một cụm Spring Boot không?  
**Đáp án:** Không. Đây là context cục bộ trong scheduler mỗi instance; cần durable state/distributed coordination khi nghiệp vụ yêu cầu.

## 16. Tài liệu tham khảo chính thức

- [Spring Framework 6.1.3 — TriggerContext Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/TriggerContext.html): `getClock`, `lastScheduledExecution`, `lastActualExecution`, `lastCompletion` và semantics nullable.
- [Spring Framework 6.1.3 — Trigger Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/Trigger.html): `nextExecution(TriggerContext)` và giá trị `null` để kết thúc.
- [Spring Framework 6.1.3 — SimpleTriggerContext Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/support/SimpleTriggerContext.html): constructor với `Clock`, ba dấu mốc `Instant` và `update(...)`.
- [Spring Framework 6.1.3 — PeriodicTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/support/PeriodicTrigger.html): fixed-delay mặc định, `setFixedRate(true)` và `nextExecution`.
- [Spring Framework 6.1.3 — CronTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): cron lenient/fixed execution, factory methods từ 6.1.3 và thời điểm tính tiếp.
- [Spring Framework 6.1.3 — TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/TaskScheduler.html): `schedule(Runnable, Trigger)` và xử lý nullable `ScheduledFuture`.
- [Spring Framework 6.1.x — ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): backend `ScheduledThreadPoolExecutor`, scheduler clock, task lifecycle và policy.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): abstraction `TaskScheduler`, `TriggerContext`, `CronTrigger` và `PeriodicTrigger`.
- [Java SE 17 — Clock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html): `Clock.fixed` để kiểm thử và múi giờ.
- [Java SE 17 — Instant](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html): thời điểm tuyệt đối, xử lý thời gian với `Duration`.
- [Java SE 17 — Duration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html): tính chênh lệch giữa các mốc thời gian.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-scheduling-003">CronTrigger cho lịch theo biểu thức cron</a>

<details>
<summary>Xem chi tiết</summary>

# CronTrigger cho lịch theo biểu thức cron trong Spring Framework

**CronTrigger** là một triển khai của interface **Trigger** trong Spring Framework, dùng biểu thức **cron** để xác định **lần thực thi tiếp theo** của một công việc. Khi kết hợp **CronTrigger** với **TaskScheduler**, ứng dụng có thể lên lịch theo lịch ngày/tháng/thứ và thời điểm cụ thể, thay vì chỉ theo một chu kỳ số giây cố định.

Điểm cần hiểu không chỉ là cách viết `0 0 9 * * MON-FRI` để chạy lúc 09:00 các ngày trong tuần. **CronTrigger là một quy tắc chọn thời điểm**; cách nó xử lý những mốc bị bỏ lỡ còn phụ thuộc **TriggerContext**, **thời điểm hoàn tất lượt trước**, **chế độ lenient/fixed execution**, **múi giờ** và **TaskScheduler thực tế**. Khi scheduled callback chỉ gửi việc vào executor rồi trả về, CronTrigger nhìn thấy **lifecycle của callback**, không tự biết công việc nghiệp vụ trong worker đã xong hay chưa.

Bài Knowledge thuộc nhóm **Lịch động và Trigger** của **Spring Framework - concurrency**. Nội dung sử dụng API Spring Framework **6.2.x** và Java **17+**, gồm giải thích cú pháp, ví dụ chạy được với đồng hồ cố định, cấu hình TaskScheduler, thay đổi lịch khi chạy, timezone/DST, overload, lỗi, tính bền vững và cách kiểm thử.

## 1. Ba lớp trách nhiệm: CronExpression, CronTrigger, TaskScheduler

Có ba thứ dễ bị gộp làm một:

| Thành phần | Trách nhiệm chính | Không tự làm |
| --- | --- | --- |
| **CronExpression** | Phân tích cú pháp cron và tính lần khớp tiếp theo từ một giá trị thời gian | Không tự tạo thread, không chạy Runnable |
| **CronTrigger** | Triển khai `Trigger.nextExecution(TriggerContext)`, chọn thời điểm theo cron và lịch sử chạy | Không tự quản lý thread pool hoặc lưu bền vững job |
| **TaskScheduler** | Nhận Runnable cùng Trigger, giữ đăng ký lịch và thực thi callback theo implementation | Không tự đảm bảo exactly-once, distributed lock hoặc mọi side effect thành công |

Luồng cơ bản:

~~~text
Biểu thức cron: "0 0 9 * * MON-FRI"
               |
               v
       CronExpression.parse(...)
               |
               v
       CronTrigger.nextExecution(TriggerContext)
               |
               v
       Instant lần kích hoạt kế tiếp
               |
               v
       TaskScheduler.schedule(Runnable, Trigger)
               |
               v
       Scheduled callback / Runnable
               |
               +-- xử lý trực tiếp trên scheduler thread
               |
               +-- hoặc chủ động submit vào TaskExecutor khác
~~~

Ví dụ ngắn trong một Spring bean đã có biến `taskScheduler`:

~~~java
import org.springframework.scheduling.support.CronTrigger;

taskScheduler.schedule(
        () -> System.out.println("Tới giờ làm việc"),
        new CronTrigger("0 0 9 * * MON-FRI"));
~~~

Đây là **đoạn minh họa trong method**, không phải chương trình Java độc lập. Nó tạo một đăng ký lịch dựa trên cron. **CronTrigger không chạy phương thức ngay khi được khởi tạo**: scheduler quyết định khi nào invoke Runnable.

## 2. Sáu trường của Spring cron, không phải năm trường Unix cron

Cron của Spring dùng **sáu trường**, theo thứ tự:

~~~text
┌──────────── giây: 0–59
│ ┌────────── phút: 0–59
│ │ ┌──────── giờ: 0–23
│ │ │ ┌────── ngày trong tháng: 1–31
│ │ │ │ ┌──── tháng: 1–12 hoặc JAN–DEC
│ │ │ │ │ ┌── ngày trong tuần: 0–7 hoặc MON–SUN
│ │ │ │ │ │   (0 hoặc 7 = Chủ nhật)
0 0 9 * * MON-FRI
~~~

Biểu thức trên yêu cầu **09:00:00 mỗi ngày từ thứ Hai tới thứ Sáu**, theo múi giờ mà trigger dùng. Đây **không phải biểu thức Unix 5 trường**. Sao chép nguyên một crontab như `0 9 * * 1-5` vào Spring mà không điều chỉnh sẽ gây lỗi hoặc hiểu sai.

| Trường | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Giây | `0`, `*/10` | Vào giây 0 hoặc mỗi 10 giây |
| Phút | `15`, `0/15` | Vào phút 15 hoặc phút 0,15,30,45 |
| Giờ | `9`, `9-17` | 09:00 hoặc các giờ từ 09 đến 17 |
| Ngày trong tháng | `1`, `L`, `1W` | Ngày 1, ngày cuối, ngày làm việc gần nhất ngày 1 |
| Tháng | `*`, `JAN,MAR` | Mọi tháng hoặc tháng 1 và 3 |
| Ngày trong tuần | `MON-FRI`, `MON#1` | Thứ Hai–thứ Sáu, hoặc thứ Hai đầu tiên trong tháng |

**Không có trường năm thứ bảy** trong biểu thức CronExpression chuẩn của Spring. Quartz có dialect và quy tắc riêng; không nên copy một biểu thức Quartz bảy trường rồi tin là Spring hiểu giống hệt.

## 3. Ý nghĩa của các ký hiệu trong biểu thức

### Dấu `*` — mọi giá trị hợp lệ

`0 0 * * * *` nghĩa là vào **đầu mỗi giờ**, không phải mỗi phút.

`*/10 * * * * *` nghĩa là **mỗi 10 giây**.

### Danh sách và khoảng

- `0 0 8,12,18 * * *`: 08:00, 12:00, 18:00 mỗi ngày.
- `0 0 9-17 * * MON-FRI`: mỗi giờ từ 09 tới 17, các ngày thứ Hai–thứ Sáu.
- `0 0/30 8-10 * * *`: 08:00, 08:30, 09:00, 09:30, 10:00 và 10:30 mỗi ngày.

**Lưu ý `9-17` là một dải giờ, không phải mọi phút trong giờ hành chính.** Nếu muốn cứ 15 phút trong khung giờ đó, dùng trường phút `0/15` và xác định ranh giới 17:45 có cần hay không.

### Dấu `/` — bước nhảy

- `*/5` trong trường phút khớp các phút 0,5,10,...,55.
- `10-30/10` khớp 10,20,30.
- `0/15` trong trường phút khớp 0,15,30,45.

Một biểu thức `0 0/15 9-17 * * MON-FRI` bao gồm cả **17:45**, vì trường giờ cho phép 17 và trường phút cho phép 45. Nếu “đến 17:00 là kết thúc” thì phải dùng các điều kiện cron khác hoặc thêm ràng buộc nghiệp vụ đúng.

### Dấu `?` trong ngày của tháng/thứ

Trong cron Spring, `?` có thể dùng **thay `*`** ở trường **day-of-month** hoặc **day-of-week** để biểu diễn không giới hạn trường đó. Ví dụ:

~~~text
0 0 9 ? * MON-FRI
0 0 9 * * MON-FRI
~~~

Hai biểu thức đều có ý nghĩa chạy 09:00 từ thứ Hai đến thứ Sáu trong parser Spring thông thường.

**Đừng áp dụng máy móc quy tắc bắt buộc dùng `?` của Quartz vào Spring.** Với hai trường ngày có điều kiện phức tạp, cần kiểm chứng semantics bằng CronExpression và test ngày cụ thể; thực hành an toàn là chỉ ràng buộc **một** trong hai trường ngày khi bài toán chỉ nói đến một tiêu chí.

### Ký hiệu `L`, `W`, `#`

Spring CronExpression hỗ trợ thêm một số dạng thường thấy ở Quartz:

| Cú pháp | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `L` ở ngày trong tháng | `0 0 0 L * *` | 00:00 ngày cuối mỗi tháng |
| `L-n` | `0 0 0 L-3 * *` | 00:00 ba ngày trước ngày cuối |
| `LW` | `0 0 0 LW * *` | Ngày làm việc cuối tháng |
| `nW` | `0 0 0 1W * *` | Ngày làm việc gần ngày 1 nhất, không vượt ranh giới tháng |
| `dL` | `0 0 0 ? * 5L` | Thứ Sáu cuối tháng (5 theo cách đánh số Spring) |
| `d#n` | `0 0 0 ? * MON#1` | Thứ Hai đầu tiên mỗi tháng |

**Số thứ trong Spring khác Quartz**: Spring dùng quy ước 0 hoặc 7 là Chủ nhật, 1 là thứ Hai; Quartz dùng 1 là Chủ nhật. Dùng tên **MON**, **FRI** thường dễ bảo trì hơn các số nếu source có thể được chia sẻ giữa nhiều loại scheduler.

## 4. Bảng biểu thức cron thực tế

| Biểu thức | Khi nào chạy? |
| --- | --- |
| `0 * * * * *` | Giây 0 của mọi phút |
| `*/15 * * * * *` | Mỗi 15 giây |
| `0 */5 * * * *` | Mỗi 5 phút, vào giây 0 |
| `0 0 * * * *` | Mỗi giờ, ở phút/giây 0 |
| `0 30 8 * * MON-FRI` | 08:30 ngày làm việc thứ Hai–thứ Sáu |
| `0 15 9-17 * * MON-FRI` | Phút 15 mỗi giờ từ 09 tới 17, thứ Hai–thứ Sáu |
| `0 0 2 * * *` | 02:00 hằng ngày theo múi giờ của trigger |
| `0 0 0 1 * *` | 00:00 ngày 1 mỗi tháng |
| `0 0 0 L * *` | 00:00 ngày cuối mỗi tháng |
| `0 0 9 ? * MON#1` | 09:00 thứ Hai đầu tiên mỗi tháng |
| `0 0 9 ? * 5L` | 09:00 thứ Sáu cuối tháng |
| `0 0 0 1 JAN *` | 00:00 ngày 1 tháng 1 |
| `0 0 18 * * FRI` | 18:00 mỗi thứ Sáu |

Bảng mô tả **mốc khớp theo lịch địa phương**. Nó không có nghĩa task sẽ bắt đầu đúng không sai lệch mili giây, cũng không nói job hoàn thành đúng mốc hay thực thi đúng một lần toàn cluster.

### Các macro cron tiện dụng

Spring chấp nhận những macro tương đương biểu thức sáu trường:

| Macro | Tương đương |
| --- | --- |
| `@yearly` hoặc `@annually` | `0 0 0 1 1 *` |
| `@monthly` | `0 0 0 1 * *` |
| `@weekly` | `0 0 0 * * 0` |
| `@daily` hoặc `@midnight` | `0 0 0 * * *` |
| `@hourly` | `0 0 * * * *` |

Macro không thay thế cấu hình **zone**. Ví dụ `@daily` có nghĩa midnight theo **múi giờ mà parser/trigger dùng**; midnight UTC và midnight ở Việt Nam không trùng nhau.

## 5. CronExpression: phân tích lịch nhưng không chạy công việc

`CronExpression` có các API đáng chú ý:

~~~java
CronExpression expression =
        CronExpression.parse("0 0 9 * * MON-FRI");

boolean valid = CronExpression.isValidExpression(
        "0 0 9 * * MON-FRI");

ZonedDateTime next = expression.next(
        ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
~~~

Đây là **đoạn mã minh họa**, cần import các class tương ứng từ Spring và Java Time.

- **parse** tạo đối tượng biểu thức; cú pháp sai có thể dẫn đến **IllegalArgumentException**.
- **isValidExpression** hữu ích để kiểm tra đầu vào cấu hình trước khi sử dụng.
- **next(Temporal)** tính **lần khớp tiếp theo sau** mốc được truyền vào; trả null nếu không xác định được lần khớp phù hợp theo giới hạn parser.
- Dùng **ZonedDateTime** để giữ rõ múi giờ và offset. `LocalDateTime` không chứa múi giờ, nên không đủ nếu cần suy luận về DST hoặc các múi giờ khác nhau.

**CronExpression không phải scheduler**. Nếu gọi `expression.next(...)` 10 lần trong một vòng lặp, ứng dụng chỉ đang tính toán thời điểm, không có task nào được tự động đặt vào thread pool.

### Tại sao không để user nhập tùy ý rồi đăng ký?

Một expression không hợp lệ có thể gây lỗi khi khởi tạo CronTrigger hoặc khi cập nhật lịch. Với cấu hình động, nên:

1. Giới hạn ai được quyền sửa cron expression.
2. Validate sáu trường/macro bằng parser và có phép kiểm tra range business.
3. Chọn và validate múi giờ.
4. Tính vài mốc gần nhất để người quản trị xác nhận intent.
5. Giới hạn tần suất cao nguy hiểm, ví dụ mỗi giây khi công việc thực tế mất vài phút.
6. Chỉ sau khi validate đầy đủ mới thay thế registration cũ.
7. Lưu audit thay đổi lịch, version và người sửa; có rollback khi cần.

**Syntactically valid** không đồng nghĩa **operationally safe** hoặc **có mốc xảy ra trong một cửa sổ hợp lý**.

## 6. Trigger và TriggerContext: yếu tố quyết định lần chạy tiếp theo

Spring định nghĩa interface **Trigger**:

~~~java
public interface Trigger {
    Instant nextExecution(TriggerContext triggerContext);
}
~~~

`TriggerContext` cung cấp:

~~~java
Clock getClock();
Instant lastScheduledExecution();
Instant lastActualExecution();
Instant lastCompletion();
~~~

Đây là **các chữ ký API rút gọn**, không phải một file Java độc lập.

Ý nghĩa:

| Dữ liệu | Ví dụ | Vai trò |
| --- | --- | --- |
| **getClock()** | Clock có zone UTC hoặc Asia/Ho_Chi_Minh | Xác định đồng hồ, múi giờ mặc định khi trigger không chỉ định zone |
| **lastScheduledExecution()** | 09:00 | Mốc **được scheduler hẹn** cho lượt trước |
| **lastActualExecution()** | 09:00:03 | Thời điểm callback **bắt đầu thực tế** |
| **lastCompletion()** | 09:04:20 | Thời điểm callback **thực sự hoàn tất** |

Các mốc này không nhất thiết trùng nhau. Khi scheduler bận, actualStart có thể trễ hơn scheduled. Khi callback chạy lâu, completion có thể rất xa scheduled.

**Trước lần thực thi đầu**, các trường last* có thể null. `SimpleTriggerContext` là một implementation cụ thể, có thể tạo với `Clock` cố định để kiểm thử.

### Lịch sử callback khác lịch sử business job

Nếu callback chỉ gửi công việc tới `TaskExecutor`:

~~~java
Runnable callback =
        () -> jobExecutor.execute(() -> doHeavyWork());
~~~

Đây là **minh họa**. `lastCompletion()` của TriggerContext phản ánh khi **callback đã submit và return**, không phải khi **doHeavyWork** đã hoàn thành trên worker. Đây là nguồn nhiều hiểu nhầm về cron bị “chạy chồng” dù người lập trình nghĩ CronTrigger sẽ chờ job thật.

## 7. CronTrigger mặc định: lenient execution

Trong Spring Framework **6.1.3+**, API thể hiện tường minh hai lựa chọn:

~~~java
CronTrigger lenient =
        CronTrigger.forLenientExecution("0 * * * * *");

CronTrigger fixed =
        CronTrigger.forFixedExecution("0 * * * * *");
~~~

Đây là **đoạn minh họa**. Constructor truyền thống:

~~~java
CronTrigger sameLenient =
        new CronTrigger("0 * * * * *");
~~~

tương đương dạng **lenient**.

Với **lenient execution**, Spring tính lần khớp kế tiếp dựa trên **thời điểm hoàn tất của lượt trước** (theo TriggerContext) trong mô hình scheduler thông thường. Ví dụ lịch mỗi phút:

~~~text
Giờ lý thuyết: 09:00, 09:01, 09:02, 09:03, 09:04, 09:05...
Lượt trước được hẹn:       09:01
Lượt trước bắt đầu:        09:01:05
Lượt trước hoàn tất:       09:04:10

Lenient: tính sau completion 09:04:10
         -> lần tiếp theo 09:05:00

Các mốc 09:02, 09:03, 09:04 không được tự động bù.
~~~

**Đây là cách hợp lý với công việc quét trạng thái hiện tại**: bỏ qua những lần không xử lý kịp có thể chấp nhận hơn tạo hàng trăm tác vụ bị trễ.

**Cẩn thận:** Javadoc Spring nêu rõ tính lenient có thể phụ thuộc scheduler; với kiểu scheduler tạo thread mới cho từng execution, hành vi có thể gần kiểu fixed hơn trong một số trường hợp. Để kết luận chính xác, xác định **TaskScheduler implementation, cách callback thực thi và TriggerContext thực sự được cập nhật**.

## 8. Fixed execution: không bỏ mốc lịch đã dự kiến

`CronTrigger.forFixedExecution(expression)` tính các mốc tiếp theo dựa trên **lastScheduledExecution** thay vì lastCompletion.

Với dữ liệu ví dụ bên trên:

~~~text
Mốc vừa lên lịch: 09:01:00
Callback hoàn tất: 09:04:10
Cron mỗi phút

Fixed: lần tiếp theo cần phục vụ là 09:02:00
       dù thời gian thực tế đã quá 09:02.
       Rồi 09:03, 09:04, 09:05...
~~~

Khi mốc đã qua, scheduler có thể kích hoạt lượt bù **ngay hoặc trễ**, tùy độ sẵn sàng của scheduler và tài nguyên. **Fixed execution** có nghĩa những firing ban đầu không bị tự động bỏ do một lượt chạy lâu, không phải đảm bảo callback chạy đúng thời gian thực hoặc không phát sinh backlog.

### So sánh

| Thuộc tính | Lenient (constructor mặc định) | Fixed execution |
| --- | --- | --- |
| Cơ sở mốc tiếp theo | **lastCompletion** | **lastScheduledExecution** |
| Bỏ mốc bị lỡ vì callback lâu | **Có thể** | **Không tự bỏ** theo chế độ fixed |
| Sau completion 09:04:10 cho lịch mỗi phút | 09:05 | 09:02 (lượt bù đầu tiên) |
| Nếu business logic mất 10 phút, lịch mỗi phút | Có thể skip nhiều lần | Có thể tạo chuỗi chạy bù và backlog |
| Phù hợp với | Refresh trạng thái, best-effort poll | Mỗi tick là một đơn vị xử lý có ý nghĩa cần được phục vụ |
| Vẫn cần idempotency và trạng thái job | Có | Có |

**Không chọn fixed execution chỉ vì “không muốn mất dữ liệu”**. Nếu mỗi mốc quan trọng, vẫn cần **durable job state**, khả năng khôi phục sau restart, idempotency và chính sách overload; một in-memory CronTrigger không lưu lịch sử vào database chỉ nhờ được tạo bằng forFixedExecution.

## 9. Java 17 + Spring chạy độc lập: kiểm chứng cron bằng đồng hồ cố định

Bài này có một ví dụ **Maven đầy đủ và có đầu ra xác định**, không cần web server và không chờ đồng hồ thực. Nó dùng `SimpleTriggerContext` với `Clock.fixed` và chủ động cập nhật ba mốc lịch sử để so sánh lenient/fixed.

Cấu trúc:

~~~text
cron-trigger-demo/
├── pom.xml
└── src/main/java/example/CronRulesDemo.java
~~~

**pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>cron-trigger-demo</artifactId>
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
                    <mainClass>example.CronRulesDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/example/CronRulesDemo.java**:

~~~java
package example;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

public class CronRulesDemo {
    public static void main(String[] args) {
        Clock testClock = Clock.fixed(
                Instant.parse("2024-01-05T08:59:30Z"),
                ZoneOffset.UTC);

        SimpleTriggerContext firstContext =
                new SimpleTriggerContext(testClock);

        CronTrigger weekdays = new CronTrigger(
                "0 0 9 * * MON-FRI", ZoneOffset.UTC);

        Instant nextMorning = weekdays.nextExecution(firstContext);

        SimpleTriggerContext completed =
                new SimpleTriggerContext(testClock);

        completed.update(
                Instant.parse("2024-01-05T09:01:00Z"),
                Instant.parse("2024-01-05T09:01:05Z"),
                Instant.parse("2024-01-05T09:04:10Z"));

        CronTrigger lenient = CronTrigger.forLenientExecution(
                "0 * * * * *");
        CronTrigger fixed = CronTrigger.forFixedExecution(
                "0 * * * * *");

        Instant lenientNext = lenient.nextExecution(completed);
        Instant fixedNext = fixed.nextExecution(completed);

        CronExpression expression = CronExpression.parse(
                "0 0 9 * * MON-FRI");

        ZonedDateTime atFridayNine = ZonedDateTime.of(
                2024, 1, 5, 9, 0, 0, 0, ZoneOffset.UTC);

        ZonedDateTime afterFriday = expression.next(atFridayNine);

        System.out.println("Lịch ngày làm việc: " + nextMorning);
        System.out.println("Lenient sau job dài: " + lenientNext);
        System.out.println("Fixed sau job dài: " + fixedNext);
        System.out.println("Sau thứ Sáu 09:00: " + afterFriday);
    }
}
~~~

Chạy từ thư mục `cron-trigger-demo`:

~~~bash
mvn -q compile exec:java
~~~

**Stdout của chương trình** (không tính log Maven nếu môi trường có):

~~~text
Lịch ngày làm việc: 2024-01-05T09:00:00Z
Lenient sau job dài: 2024-01-05T09:05:00Z
Fixed sau job dài: 2024-01-05T09:02:00Z
Sau thứ Sáu 09:00: 2024-01-08T09:00Z
~~~

**Giải thích tại sao đầu ra xác định:**

1. **Clock.fixed** đóng băng thời điểm ở 08:59:30 UTC ngày thứ Sáu 05/01/2024. Cron 09:00 ngày làm việc khớp lần tiếp theo lúc **09:00:00Z cùng ngày**.
2. `SimpleTriggerContext.update` đặt lượt đã được hẹn **09:01:00**, thực chạy lúc **09:01:05**, hoàn tất lúc **09:04:10**.
3. **Lenient** nhìn completion **09:04:10**, lấy mốc nguyên phút đầu tiên **sau đó**: **09:05:00**.
4. **Fixed** nhìn scheduled **09:01:00**, lấy mốc nguyên phút đầu tiên sau nó: **09:02:00**, dù ở hiện tại đã trễ.
5. CronExpression.next với mốc **09:00 thứ Sáu** tìm lần khớp **sau** mốc đó, nên bỏ qua chính thứ Sáu 09:00 và trả **09:00 thứ Hai 08/01/2024**.
6. Ví dụ **không chạy tác vụ lịch thực**, chỉ tính thời điểm theo API. Vì không phụ thuộc scheduler thread hoặc timeouts, stdout không bị race điều phối.

**Điểm nâng cao:** nếu gọi `nextExecution` liên tiếp mà **không cập nhật TriggerContext**, bạn chưa mô phỏng thêm một lượt thực thi. CronTrigger không “ghi lại” lịch sử vào object context chỉ vì bạn gọi phương thức tính toán; trong test cần chủ động update state theo các lượt giả lập.

## 10. Gắn CronTrigger vào TaskScheduler thật

Trong ứng dụng Spring, ta có thể tạo `ThreadPoolTaskScheduler` và gọi:

~~~java
package example.scheduler;

import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

public class ScheduledJobOwner {
    private final ThreadPoolTaskScheduler scheduler;

    public ScheduledJobOwner(ThreadPoolTaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public ScheduledFuture<?> start(Runnable task) {
        CronTrigger trigger = new CronTrigger(
                "0 0 9 * * MON-FRI",
                ZoneId.of("Asia/Ho_Chi_Minh"));

        return scheduler.schedule(task, trigger);
    }
}
~~~

Đây là **một class Java trong project có Spring dependencies**, chưa tự đăng ký bean nếu không có configuration tương ứng. Phương thức `start` trả về **ScheduledFuture** của đăng ký lịch; caller cần giữ handle nếu muốn hủy lịch sau đó.

Cấu hình một scheduler bean:

~~~java
package example.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("cron-");
        return scheduler;
    }
}
~~~

Đây là **một file cấu hình Spring**. Container quản lý lifecycle cho bean. `ThreadPoolTaskScheduler` chạy scheduled callback trên **scheduler thread(s)**; không tự gửi business logic vào một pool tách biệt.

### Nếu callback tốn nhiều thời gian

Một thiết kế có thể dùng scheduled callback **ngắn** để gửi việc:

~~~java
ScheduledFuture<?> registration = scheduler.schedule(
        () -> jobExecutor.execute(this::processReports),
        new CronTrigger("0 0/5 * * * *",
                ZoneId.of("Asia/Ho_Chi_Minh")));
~~~

Đây là **đoạn minh họa** có scheduler, jobExecutor và processReports được định nghĩa trong class sở hữu chúng.

- Scheduler tính mốc cron và gọi callback gửi việc.
- TaskExecutor nhận việc; callback có thể trả về ngay.
- **lastCompletion trong TriggerContext là lúc callback gửi xong**, không phải lúc processReports hoàn tất.
- Lần cron tiếp theo có thể được tính trong khi report job đang chạy.
- Nếu executor bão hòa, `execute` có thể throw **TaskRejectedException** hoặc có hành vi khác theo policy.

**Không dùng “lenient execution” để khẳng định không overlap giữa các business jobs được delegate.** Nếu cần một job tại một thời điểm, dùng single-flight/locking/queueing hợp đồng riêng.

## 11. @Scheduled(cron = ...) khác CronTrigger lập lịch động thế nào?

Hai cách đều liên quan tới cron, nhưng cách quản lý khác nhau.

**Khai báo annotation cố định tại bean:**

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InvoiceReminder {
    @Scheduled(cron = "0 0 9 * * MON-FRI",
               zone = "Asia/Ho_Chi_Minh")
    public void run() {
        sendDueReminders();
    }

    private void sendDueReminders() {
        System.out.println("Kiểm tra hóa đơn đến hạn");
    }
}
~~~

Đây là **một bean class đầy đủ trong ứng dụng Spring**; cần `@EnableScheduling` tại context và các dependencies Spring phù hợp. Annotation được Spring phát hiện qua hạ tầng scheduling, thông thường khi bean được khởi tạo.

**Lập lịch chương trình bằng CronTrigger:**

~~~java
CronTrigger trigger =
        new CronTrigger("0 0 9 * * MON-FRI",
                ZoneId.of("Asia/Ho_Chi_Minh"));

ScheduledFuture<?> future =
        taskScheduler.schedule(reminderTask, trigger);
~~~

Đây là **đoạn method minh họa** có reminderTask và taskScheduler.

| Tiêu chí | @Scheduled cron | TaskScheduler + CronTrigger |
| --- | --- | --- |
| Nguồn cấu hình | Annotation/placeholder/SpEL trên method | Biểu thức do code tạo, đọc từ config/database nếu cần |
| Ai tạo registration? | Hạ tầng xử lý @Scheduled | Code gọi `schedule` |
| Giữ handle để hủy/thay đổi từng job | Không tự có trên method | Có thể giữ ScheduledFuture trả về |
| Lịch động theo tenant/job | Cần thiết kế bổ sung | Thường linh hoạt hơn với registry riêng |
| Zone | Attribute `zone` | Constructor CronTrigger với ZoneId/TimeZone hoặc scheduler clock |
| Quản lý lifecycle | Gắn với bean/context được xử lý | Code sở hữu handle; container quản lý scheduler nếu là bean |
| Rủi ro đăng ký trùng | Nhiều bean instances/annotations/contexts | Gọi `schedule` nhiều lần mà không cancel handle cũ |

**Không chạy song song cả hai cơ chế cho cùng logical job mà không có chủ đích**, vì có thể tạo hai registrations với cùng business logic.

### Đọc giá trị cron từ cấu hình

Annotation hỗ trợ placeholder:

~~~java
@Scheduled(
    cron = "${jobs.reminder.cron:0 0 9 * * MON-FRI}",
    zone = "${jobs.reminder.zone:Asia/Ho_Chi_Minh}"
)
public void checkReminders() {
    sendReminders();
}
~~~

Đây là **đoạn minh họa**; Spring resolve placeholder ở thời điểm cấu hình scheduling. Việc sửa file cấu hình sau khi application đã khởi động **không tự động bảo đảm** scheduled task đang chạy được đăng ký lại bằng expression mới. Với hot reload, cần một cơ chế thay thế registration/refresh tương ứng.

## 12. Múi giờ: cùng cron, khác Instant

Cron là lịch theo **local calendar time** trong một zone. Ví dụ:

~~~text
Cron: 0 0 9 * * MON-FRI

Zone = UTC                -> 09:00 UTC
Zone = Asia/Ho_Chi_Minh   -> 09:00 ICT (UTC+07:00)
~~~

Hai mốc đó cách nhau **bảy giờ** khi zone Việt Nam có offset +07:00.

### Cách chọn zone

~~~java
CronTrigger trigger = new CronTrigger(
        "0 0 9 * * MON-FRI",
        ZoneId.of("Asia/Ho_Chi_Minh"));
~~~

Trên `@Scheduled`, chỉ định:

~~~java
@Scheduled(cron = "0 0 9 * * MON-FRI",
           zone = "Asia/Ho_Chi_Minh")
public void run() {
    generateDailySummary();
}
~~~

Hai đoạn trên là **minh họa**; generateDailySummary do ứng dụng định nghĩa.

Nếu không truyền ZoneId/TimeZone cho CronTrigger, Spring sử dụng **zone của clock trong TriggerContext** (thường lấy từ TaskScheduler). Một số triển khai dùng **system default zone**. Vì thế **đừng suy ra chắc chắn mọi container đều chạy UTC** hoặc chắc chắn giống timezone máy lập trình.

Đối với ứng dụng triển khai đa khu vực, hãy quyết định rõ:

- Lịch tính theo **UTC cố định** hay theo **timezone địa phương của một business unit**?
- Có cần lưu IANA zone như `Europe/Berlin`, `America/New_York`, `Asia/Ho_Chi_Minh` thay vì chỉ lưu offset?
- Khi user đổi timezone, liệu lịch động sẽ được đăng ký lại hay chỉ hiển thị khác?
- Lịch “ngày kết thúc tháng” tính theo zone nào và thời điểm đóng sổ nào?

**Offset như +07:00 không luôn thay thế được ZoneId đối với nơi có DST**. ZoneId chứa quy tắc thay đổi lịch sử và mùa; offset chỉ là chênh lệch tại một thời điểm.

## 13. Daylight Saving Time: giờ không tồn tại và giờ lặp

Ở những múi giờ có **DST**, local time có thể nhảy:

- **Spring-forward**: một khoảng local time **không tồn tại**.
- **Fall-back**: một khoảng local time có thể **xuất hiện hai lần** với offset khác nhau.

Ví dụ business muốn chạy vào **02:30 mỗi ngày** ở một zone có DST. Vào ngày spring-forward, `02:30` có thể không tồn tại. Vào ngày fall-back, một mốc khác có thể lặp lại.

**Không mặc định** rằng:

- `0 30 2 * * *` luôn chạy đúng một lần mỗi ngày lịch.
- Một thời điểm trùng local date/time tương đương cùng một `Instant`.
- Mọi parser/scheduler đều tự catch up những local times bị thiếu.
- Chỉ cần dùng `Clock.systemUTC` là lịch business theo giờ địa phương sẽ đúng.

Hãy xác định **business policy**:

| Yêu cầu | Chính sách cần quyết định |
| --- | --- |
| Chạy 02:30 mỗi ngày địa phương | Nếu 02:30 không tồn tại thì bỏ, dời, hay tính theo mốc khác? |
| Chỉ tạo một báo cáo cho mỗi local date | Dùng local date + zone làm idempotency key, không chỉ Instant |
| Xử lý dữ liệu theo UTC cutoff | Dùng mốc UTC rõ ràng, tránh đánh đồng local 00:00 với UTC 00:00 |
| Xử lý hai lần khi giờ bị lặp | Cho phép hai job khác nhau hay deduplicate theo logical business key? |

CronExpression và ZoneId giúp tính lịch, nhưng **không tự định nghĩa mọi policy nghiệp vụ về ngày được tính như thế nào**. Test các ngày DST có chuyển đổi thật với version Spring/JDK chạy production.

## 14. Dynamic schedule: tạo, đổi và hủy registration

Khi mỗi tenant có cron riêng hoặc người quản trị có quyền đổi giờ chạy, không thể chỉ đặt một @Scheduled cố định rồi kỳ vọng thay đổi runtime tự xuất hiện.

Một cấu trúc thường gặp:

~~~text
Admin update request
        |
        v
Validate expression + zone + policy
        |
        v
Persist desired schedule/version
        |
        v
Scheduling coordinator
        |
        +-- cancel old ScheduledFuture
        +-- create CronTrigger(newExpression, zone)
        +-- TaskScheduler.schedule(newTask, trigger)
        +-- retain new ScheduledFuture
        |
        v
Observability: schedule id, version, status, next expected fire
~~~

Quan trọng: **CronTrigger là immutable về expression trong cách dùng thông thường**. Sửa một String cấu hình bên ngoài không làm object CronTrigger đã được scheduler giữ tự đổi lịch. Khi muốn cập nhật, tạo trigger/registration mới và quản lý handle.

### Minh họa manager một job trong JVM

~~~java
package example.scheduler;

import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

public class CronJobManager {
    private final TaskScheduler scheduler;
    private final Runnable job;
    private ScheduledFuture<?> registration;

    public CronJobManager(TaskScheduler scheduler, Runnable job) {
        this.scheduler = scheduler;
        this.job = job;
    }

    public synchronized void replace(
            String expression, String zoneName) {

        // Kiểm tra trước khi đụng registration đang có.
        CronExpression.parse(expression);
        ZoneId zone = ZoneId.of(zoneName);
        CronTrigger trigger = new CronTrigger(expression, zone);

        if (registration != null) {
            registration.cancel(false);
        }
        registration = scheduler.schedule(job, trigger);
    }

    public synchronized void stop() {
        if (registration != null) {
            registration.cancel(false);
            registration = null;
        }
    }
}
~~~

Đây là **class minh họa trong project có Spring**, không phải một dịch vụ production hoàn chỉnh. `replace` được synchronized để tránh hai request cập nhật trong **cùng object** làm chồng chéo các handles. Nó validate expression/zone **trước khi hủy** lịch cũ.

**Những giới hạn phải xử lý ở production:**

- `schedule` có thể thất bại hoặc trả null nếu Trigger không xác định được lần chạy tiếp theo; code phải có nhánh theo dõi lỗi/rollback trạng thái.
- Sau khi hủy lịch cũ nhưng trước khi đăng ký lịch mới, có một khoảng gián đoạn. Nếu cần không mất mốc, phải có giao thức thay đổi registration bền vững hơn.
- `cancel(false)` không cưỡng bức dừng callback đang chạy và không tự hủy business job đã gửi vào executor.
- Một callback cũ có thể vừa bắt đầu khi `replace` được gọi. Muốn ngăn xử lý sai version, gắn scheduleVersion vào job và kiểm tra tại boundary.
- Synchronized chỉ bảo vệ **một manager instance**, không đồng bộ giữa nhiều JVM/pods.
- Nếu nhiều jobs theo tenant, cần registry map từ **jobId → scheduled handle/config/version**, với lifecycle và concurrent updates rõ.

### Sửa cron trong database có đủ không?

**Không.** Nếu một service chỉ đọc database vào thời điểm khởi động và sau đó giữ CronTrigger cũ, thay đổi bản ghi database không làm scheduler tự reload. Cần **notification**, **polling cấu hình**, **event-driven refresh** hoặc cơ chế quản lý riêng để thực hiện replace/stop. Đừng đánh đồng “config đã thay đổi” với “schedule đã thay đổi”.

## 15. Pause, resume, recovery và các hàm resume*

Spring 6.1.3+ cung cấp:

~~~java
CronTrigger.resumeLenientExecution(
        expression,
        lastKnownCompletion);

CronTrigger.resumeFixedExecution(
        expression,
        lastKnownScheduled);
~~~

Đây là **đoạn minh họa** với biến expression và các Instant phù hợp.

Những factory này hỗ trợ **tính lịch bắt đầu lại từ timestamp được cung cấp**:

- `resumeLenientExecution`: hiểu timestamp là **last-known completion**; chỉ xét những mốc sau completion, có thể kích hoạt một lần sớm nếu resumption timestamp nằm trong quá khứ rồi chuyển về tính lenient.
- `resumeFixedExecution`: hiểu timestamp là **last-known scheduled**; những mốc giữa timestamp đó và hiện tại **được tính để chạy bù** theo fixed semantics.

**Không nhầm resume timestamp với job success watermark.** Nếu lưu lastScheduled nhưng callback thực tế fail hoặc chưa commit dữ liệu, resumeFixedExecution biết lịch đã lên mốc nào, **không biết** business effect nào thành công.

### Ví dụ suy luận

~~~text
Cron: mỗi phút
Lần được hẹn cuối trước restart: 09:00
Ứng dụng ngừng từ 09:00 tới 09:05

resumeFixedExecution(expression, 09:00)
    -> có thể yêu cầu phục vụ các mốc 09:01, 09:02, ...

resumeLenientExecution(expression, lastCompletion = 09:00)
    -> không cố bù toàn bộ các mốc cũ theo fixed semantics
~~~

**Hệ quả vận hành:**

1. Nếu downtime kéo dài nhiều giờ và cron mỗi giây, fixed catch-up có thể tạo một lượng việc khổng lồ. Cần đánh giá policy **max backlog / catch-up window / skip / coalesce**.
2. Nếu muốn không mất mốc quan trọng, hãy lưu **job keys** và trạng thái per tick một cách durable; CronTrigger trong RAM không đủ.
3. Nếu nhiều pods khởi động lại cùng checkpoint, tất cả có thể cùng chạy bù nếu không có coordination.
4. Mốc truyền vào phải được hiểu đúng **last scheduled** hay **last completion**; dùng sai loại làm lệch lịch.
5. Nếu zone hoặc cron expression đã thay đổi trong downtime, chính sách replay phải giải thích **dùng lịch cũ hay lịch mới**.

## 16. CronTrigger không tự ngăn overlapping business jobs

Một hiểu nhầm quan trọng là: “CronTrigger tính dựa trên lastCompletion, vậy hai công việc không thể chạy chồng.”

Đúng với **một callback đồng bộ** được scheduler theo dõi, trong cấu hình thích hợp, cron tiếp tục sau khi callback trả về. Nhưng nếu callback chỉ submit một task:

~~~java
scheduler.schedule(
        () -> workerPool.execute(this::heavyReport),
        new CronTrigger("0 * * * * *"));
~~~

thì:

~~~text
09:00:00  Cron callback -> submit report A -> callback returns
09:00:01  report A đang chạy trong worker pool
09:01:00  Cron callback -> submit report B -> callback returns
09:01:01  report A và report B có thể cùng chạy
~~~

**lastCompletion là callback completion, không phải completion của report A**. Điều đó đúng cả khi trigger được tạo bằng `forLenientExecution`.

### Single-flight theo logical job

Nếu business rule là **không cho hai reports cùng loại chạy song song trong một process**, bạn có thể đặt guard:

~~~java
private final AtomicBoolean running = new AtomicBoolean();

public void onCronTick() {
    if (!running.compareAndSet(false, true)) {
        return;
    }

    try {
        workerPool.execute(() -> {
            try {
                heavyReport();
            } finally {
                running.set(false);
            }
        });
    } catch (RuntimeException rejected) {
        running.set(false);
        throw rejected;
    }
}
~~~

Đây là **đoạn method minh họa** có workerPool, heavyReport và AtomicBoolean imports. Nó chỉ bảo vệ những lời gọi đi qua **cùng object guard trong cùng JVM**. Nếu dùng DiscardPolicy bỏ task âm thầm, guard có thể bị kẹt true vì worker không chạy finally; cần rejection policy phù hợp. Nếu dùng CallerRunsPolicy, heavyReport có thể chạy **trên scheduler thread** khi pool bão hòa.

**Với nhiều pods**, guard in-memory không đủ. Cần distributed lease/lock, scheduling leader hoặc business idempotency key để bảo vệ hiệu ứng nghiệp vụ.

## 17. ErrorHandler, lỗi callback và việc lịch có tiếp tục không

Một callback cron có thể ném ngoại lệ:

~~~java
scheduler.schedule(
        () -> {
            if (!remoteServiceAvailable()) {
                throw new IllegalStateException("Downstream unavailable");
            }
            runJob();
        },
        new CronTrigger("0 */5 * * * *"));
~~~

Đây là **đoạn minh họa**, các method domain chưa được định nghĩa.

Hành vi sau ngoại lệ **phụ thuộc implementation và ErrorHandler**. Với **JDK ScheduledExecutorService.scheduleAtFixedRate** đơn thuần, một periodic task ném uncaught exception có thể khiến những lượt tiếp theo bị suppress theo hợp đồng JDK. Spring ThreadPoolTaskScheduler có **ErrorHandler** để điều chỉnh cách xử lý exceptions trong scheduled callbacks. Không khẳng định “một callback lỗi thì cron sẽ ngừng mãi” hoặc “mọi lỗi cron đều được tự retry” cho mọi cấu hình.

Phân biệt hai tầng:

| Lỗi | Xảy ra ở đâu | Ai quan sát? |
| --- | --- | --- |
| **Cron expression không hợp lệ** | Khi parse/tạo trigger | Code cấu hình hoặc khởi tạo scheduler |
| **TaskScheduler không nhận thêm** | Khi đăng ký/chạy theo lifecycle | Caller đăng ký, scheduler management |
| **Callback ném exception** | Scheduler thread | ErrorHandler/scheduler |
| **TaskExecutor từ chối việc** | Callback gọi `execute` | Callback hoặc error handling của scheduler |
| **Business job ném exception** | Worker đã được delegate | Worker Future/handler/log/job state |
| **DB/HTTP timeout** | Trong business job | Business error policy, retries, metrics |

Nếu business job đã delegate và callback return, **lỗi business job không tự động xuất hiện trong stack của callback**. Đừng dùng số lần cron được kích hoạt làm bằng chứng số jobs thành công.

## 18. ThreadPoolTaskScheduler, thread model và backlog

`ThreadPoolTaskScheduler` dùng scheduler threads để thực thi những callback được lên lịch. `poolSize` giới hạn số scheduled callbacks có thể chạy đồng thời trên pool đó. Mặc định có thể là một thread; số cụ thể nên được xác định bằng cấu hình/runtime thực tế.

~~~text
TaskScheduler pool=1
    |
    +-- callback cron A đang chặn 30 giây
    |
    +-- callback cron B đã tới hạn, có thể phải đợi
~~~

Khi B chạy trễ, vấn đề không nhất thiết là cron expression sai. Đó có thể là **scheduler lag** do pool bận.

Tách worker:

~~~text
CronTrigger
   |
   v
Short callback on scheduler thread
   |
   +-- TaskExecutor.execute(job)
             |
             v
      worker queue / worker thread
~~~

Cách tách này giúp scheduler rảnh sớm, nhưng chuyển rủi ro sang **worker queue**. Nếu lượng tick nhiều hơn năng lực xử lý, backlog tăng; khi queue đầy có thể reject. Một việc scheduled đã bị trễ có thể không còn giá trị, nên **deadline/expiry** phải được tính cả thời gian queue wait.

**Không dùng CronTrigger fixed catch-up cùng queue vô hạn mà không đo tải.** Một đợt catch-up có thể tạo hàng nghìn callbacks cố submit jobs, làm nhiều hệ thống downstream quá tải cùng lúc.

## 19. Lịch cron trong nhiều ứng dụng/pods

`@Scheduled` và `TaskScheduler.schedule(..., new CronTrigger(...))` thường hoạt động **cục bộ theo ApplicationContext/process**. Nếu triển khai ba replicas, mỗi replica có thể đăng ký cùng expression và chạy quanh cùng mốc.

~~~text
09:00 UTC
    |
    +-- pod A: CronTrigger -> job A
    +-- pod B: CronTrigger -> job B
    +-- pod C: CronTrigger -> job C
~~~

Đó là **ba registrations độc lập**. Không phải vì cron bị parser giải mã sai hoặc ThreadPoolTaskScheduler lặp method trên một thread.

Nếu nghiệp vụ chỉ cho một job global tại mỗi mốc:

- **Chọn một leader** chịu trách nhiệm tạo job theo lịch.
- Hoặc dùng **distributed lease/lock** có timeout và fencing để bảo vệ khỏi worker cũ.
- Hoặc dùng scheduler/job store hỗ trợ cluster như Quartz JDBC clustering hay hệ thống điều phối bên ngoài.
- **Dùng idempotency key** tại storage/domain boundary, ví dụ `reportType + businessDate + zone`.
- Theo dõi trạng thái job `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED`, retry an toàn.
- Định nghĩa rõ chính sách khi pod chết ở giữa lượt và khi leader đổi.

**Một distributed lock chưa tự đảm bảo exactly-once side effect** nếu lock lease hết hạn trong khi worker cũ vẫn thực thi. Bảo vệ database uniqueness và thao tác external idempotent khi cần.

## 20. Không nhầm CronTrigger với Quartz CronTrigger

Cùng tên “CronTrigger”, nhưng:

- `org.springframework.scheduling.support.CronTrigger` thuộc **Spring TaskScheduler**, dùng **CronExpression** của Spring.
- `org.quartz.CronTrigger` thuộc **Quartz Scheduler**, có hệ sinh thái JobDetail, Trigger, JobStore, misfire policies và cấu hình cluster riêng.

Cú pháp hai họ không phải lúc nào hoàn toàn tương đương:

| Điểm | Spring CronTrigger | Quartz CronTrigger |
| --- | --- | --- |
| Tên class | org.springframework.scheduling.support.CronTrigger | org.quartz.CronTrigger |
| Cơ chế lập lịch | TaskScheduler/TriggerContext | Quartz Scheduler/JobDetail |
| Trường cron | 6 trường, bắt đầu giây | Cron của Quartz có quy tắc 6/7 trường và option năm |
| Đánh số thứ | 0/7 là Chủ nhật | 1 là Chủ nhật |
| Misfire | Lenient/fixed/resume theo API Spring; không tự có persistent misfire log | Chính sách misfire của Quartz theo JobStore/trigger |
| Lưu bền vững lịch/job | Cần tự thiết kế hoặc dùng hệ thống ngoài | Quartz hỗ trợ JDBC JobStore khi cấu hình |
| Đảm bảo cluster | Không tự có | Quartz có chế độ clustered JobStore phù hợp |

Nếu tài liệu hướng dẫn “Quartz supports 7 fields with year” thì **không suy ra CronTrigger của Spring hỗ trợ trường năm**. Nếu dùng cả hai thư viện, nên import class tường minh hoặc dùng fully-qualified name nơi dễ nhầm.

## 21. Hủy lịch và dừng ứng dụng đúng lifecycle

Khi lập lịch động:

~~~java
ScheduledFuture<?> handle =
        scheduler.schedule(task, cronTrigger);
~~~

Code cần giữ `handle` và hủy khi job không còn hợp lệ:

~~~java
if (handle != null) {
    handle.cancel(false);
}
~~~

Đây là **đoạn minh họa**. `cancel(false)` yêu cầu hủy những lần lên lịch tiếp theo nhưng **không yêu cầu interrupt callback đang thực thi**. `cancel(true)` có thể đề nghị interrupt tùy Future implementation; việc callback ngừng vẫn cần hợp tác.

Nếu callback đã submit business work vào executor khác, hủy ScheduledFuture **không tự động hủy** Future/handle của business work đó.

Khi shutdown:

1. Ngừng chấp nhận cấu hình lịch mới hoặc đánh dấu coordinator đang đóng.
2. Hủy/đóng đăng ký lịch của job không còn hoạt động.
3. Ngừng scheduler tạo callbacks mới theo lifecycle phù hợp.
4. Cho phép worker jobs đã nhận hoàn tất hoặc thực hiện chính sách cancellation có deadline.
5. Ghi rõ trạng thái job còn lại nếu cần restart recovery.
6. Đảm bảo scheduler và executor Spring-managed có lifecycle phù hợp; không `shutdown()` một singleton shared executor sau mỗi request.

Trong Spring, context close có hỗ trợ shutdown nhiều scheduler/executor beans, nhưng **graceful shutdown settings và trách nhiệm lưu job** vẫn phải được xem xét. Đừng cho rằng đóng context đã commit mọi business job.

## 22. Cron trigger có thể không có lần chạy tiếp theo

Không phải mọi biểu thức parse được đều nhất thiết tạo **một mốc hữu ích trong khoảng thời gian muốn kiểm soát**.

Ví dụ:

- Một cấu hình chỉ chạy ngày 29/02 có khoảng cách nhiều năm.
- Một biểu thức ngày/tháng không bao giờ thỏa ràng buộc theo parser có thể không có `next`.
- Lịch đã bị disable bằng cấu hình của ứng dụng.
- Scheduler/registration đã bị hủy hoặc shutdown.
- Múi giờ và DST thay đổi lịch thực tế so với ngày người vận hành dự đoán.

`CronExpression.next(Temporal)` và `CronTrigger.nextExecution(TriggerContext)` có thể trả **null** nếu không tính được lần khớp tiếp theo. Với `TaskScheduler.schedule(..., trigger)`, kết quả trả về cũng có thể là null nếu Trigger không bao giờ khớp.

**Đừng gọi ngay `future.cancel(...)` nếu `schedule` trả null mà không kiểm tra.** Khi xây UI quản trị lịch, validate và hiển thị một vài upcoming firings trong zone được chọn trước khi lưu, đồng thời có trạng thái explicit như `DISABLED` hoặc `NO_NEXT_FIRE` thay vì để job “im lặng”.

### Vô hiệu hóa cron của @Scheduled

Spring cung cấp hằng **`Scheduled.CRON_DISABLED`**, tương đương chuỗi `"-"`, để cấu hình cron qua placeholder có thể được tắt:

~~~java
@Scheduled(cron = "${jobs.snapshot.cron:-}")
public void snapshot() {
    saveSnapshot();
}
~~~

Đây là **đoạn minh họa trong bean**. Cách này chỉ có ý nghĩa ở **@Scheduled cron attribute**, không phải một cron expression chuẩn để truyền tùy tiện vào `CronExpression.parse("-")` hoặc `new CronTrigger("-")`.

Khi dùng cấu hình động với CronTrigger, bạn nên biểu diễn trạng thái disabled **trước khi tạo trigger**, hủy registration cũ và ghi lại thay đổi.

## 23. Lưu trạng thái lịch: desired state và observed state

Trong hệ thống có cập nhật cron qua API, nên phân biệt:

| Loại trạng thái | Ví dụ | Nguồn sự thật |
| --- | --- | --- |
| **Desired config** | cron, zone, enabled, version, tenant | DB/configuration store |
| **Active registration** | ScheduledFuture handle, trigger instance | Process hiện tại |
| **Last scheduled fire** | Instant mà scheduler định gọi callback | TriggerContext/metrics |
| **Last actual callback start** | Instant callback thật bắt đầu | Scheduler observability |
| **Last callback completion** | Instant callback return | Scheduler observability |
| **Last business job completion** | Job kết thúc trên worker | Job store/worker telemetry |
| **Last successful business watermark** | Đã xử lý dữ liệu tới mốc nào | Durable domain state |
| **Next expected fire** | Mốc cron dự kiến kế tiếp | Tính theo expression/zone và policy |

**Không dùng lastCompletion của CronTrigger làm bằng chứng đã ghi thành công báo cáo** nếu callback chỉ gửi task. Cũng không dùng `Future.isDone()` của ScheduledFuture đại diện callback để chứng minh mọi side effect business đã commit.

Khi restart, ứng dụng có thể cần dựng lại active registrations từ desired configs; nếu yêu cầu replay những mốc đã lỡ, phải dùng checkpoint đã lưu và policy catch-up rõ ràng.

## 24. Metrics và tracing cho CronTrigger

Bộ chỉ số hữu ích:

**Tầng scheduler**

- Tổng số **registrations** theo jobId, version, zone và cron hash/label.
- **next planned fire** và **actual callback start** để tính **scheduler lag**.
- Thời lượng **scheduled callback**.
- Số callback bị lỗi và số registration bị hủy.
- Kích thước/hoạt động scheduler pool; thread bận do blocking callback.

**Tầng worker**

- Số lần **submit attempted/accepted/rejected**.
- **Queue wait** từ accepted tới worker start.
- Thời lượng thực thi job, outcome và deadline exceeded.
- Số lần **overlap prevented**, skip/coalesce và retry.
- Số lần job bị duplicate ở nhiều pods và số business effects thực sự commit.

**Tầng cấu hình**

- Ai đổi cron/zone, từ version nào sang version nào.
- Sau khi đổi, observed active expression có đúng version mong muốn không.
- Lần chạy tiếp theo dự kiến khi đã chọn lenient/fixed/resume.
- Số backlog firings sau downtime nếu bật catch-up.

Một log như `09:00 cron fired` chỉ nói callback bắt đầu; nếu không có jobId, scheduleVersion, processId và outcome, nó không giúp trả lời “báo cáo 09:00 đã được tạo chưa”.

## 25. Kiểm thử biểu thức và hành vi lịch không phụ thuộc thời gian thực

### Test parser và những mốc tiếp theo

- Dùng **Clock.fixed** với ZoneId xác định.
- Dùng `CronExpression.parse` hoặc `CronTrigger.nextExecution(new SimpleTriggerContext(clock))`.
- Assert những mốc dự kiến theo `Instant` hoặc `ZonedDateTime`.
- Test boundary: trước đúng mốc, chính xác tại mốc, sau mốc một giây.
- Test thứ Sáu → thứ Hai, cuối tháng, năm nhuận, `L`, `LW`, `MON#1`.
- Test zone và một hoặc hai ngày DST thực tế của zone đó.
- Test biểu thức sai (thiếu trường, số phút 60, trường năm thừa).

### Test lenient versus fixed

- Cho `SimpleTriggerContext` lastScheduled = 09:01 và lastCompletion = 09:04:10.
- Với cron mỗi phút, assert lenient = 09:05, fixed = 09:02.
- Lặp với một callback giả lập dài hơn nhiều chu kỳ.
- Phân biệt test **tính toán trigger** với test **scheduling thực tế**; `nextExecution` không tự chạy Runnable.

### Integration test TaskScheduler

- Tạo `ThreadPoolTaskScheduler` có prefix thread và pool size rõ.
- Đăng ký một cron ngắn trong môi trường test, dùng `CountDownLatch` để ghi nhận callback đầu tiên.
- Dùng `await(timeout)` và `Future.cancel(false)` khi đã đủ thông tin, không thả lịch chạy vô hạn sau test.
- Test một callback có blocking guard và một callback khác để quan sát scheduler lag.
- Test callback delegate sang TaskExecutor: chứng minh lastCompletion callback khác business job completion.
- Test dynamic replace: trước thay đổi chỉ một active registration, sau thay đổi không còn nhiều registrations trùng.
- Test race khi update job gần thời điểm firing; dùng job version/idempotency để xác minh policy.

**Không dùng `Thread.sleep(100)` rồi kết luận scheduler chắc chắn đã chạy.** Cron tính mốc theo đồng hồ, còn thread scheduler chịu độ trễ thực tế. Clock.fixed giúp kiểm tra parser/trigger nhanh và xác định; latch giúp kiểm tra integration có bounded wait.

## 26. Những nhầm lẫn thường gặp

**“Spring cron chỉ có 5 trường giống crontab.”** Sai. CronExpression của Spring dùng **6 trường**; trường đầu tiên là giây.

**“CronTrigger và CronExpression là cùng một API.”** Sai. CronExpression tính lần khớp từ thời gian, CronTrigger dùng TriggerContext cho lịch sử callback; TaskScheduler mới thực thi Runnable.

**“new CronTrigger(expression) sẽ chạy callback ngay.”** Sai. Bạn phải đăng ký trigger cùng Runnable vào scheduler.

**“@Scheduled(cron=...) sửa config là ngay lập tức hot reload.”** Không được bảo đảm. Đăng ký hiện tại cần được refresh/replace theo lifecycle.

**“CronTrigger mặc định chạy bù mọi mốc bị lỡ.”** Sai. Constructor mặc định tương đương **lenient execution**, có thể skip mốc khi công việc lâu.

**“forFixedExecution luôn chạy chính xác từng giây đã hẹn.”** Sai. Nó không tự bỏ các mốc theo lịch, nhưng callbacks có thể bắt đầu trễ do scheduler/resources.

**“forFixedExecution bảo đảm jobs survive restart.”** Sai. Bản thân CronTrigger không lưu bền vững state/job.

**“resumeFixedExecution hiểu timestamp là lastCompletion.”** Sai. Nó dùng **last-known scheduled timestamp**. resumeLenientExecution dùng last-known completion.

**“lastCompletion của CronTrigger là khi worker job hoàn tất, kể cả callback chỉ submit.”** Sai. Đây là completion của **scheduled callback**, không phải công việc được delegate.

**“Lenient nghĩa là business jobs không bao giờ overlap.”** Sai nếu callback submit và return sớm; nhiều jobs vẫn có thể chạy đồng thời ở worker.

**“Lịch 09:00 ở mọi server là cùng một Instant.”** Sai nếu zone khác. Cần chỉ định ZoneId/Clock.

**“Có zone nên không cần xét DST.”** Sai. DST là hệ quả của lịch địa phương ở một số zones; phải xác định policy cho giờ bị thiếu/lặp.

**“Spring cron và Quartz cron hoàn toàn giống nhau.”** Sai. Số trường, weekday numbering và lifecycle khác.

**“Một cron trong ba pods chỉ chạy một lần toàn cluster.”** Sai. Các schedulers trong các processes thường độc lập.

**“@Scheduled hoặc CronTrigger tự loại duplicate business effect.”** Sai. Cần distributed coordination và idempotency khi nghiệp vụ yêu cầu.

**“Cron expression parse thành công nghĩa là job sẽ chạy ngay.”** Sai. Lần kế tiếp có thể rất xa hoặc không có mốc, và scheduler có thể đã shutdown.

**“Hủy ScheduledFuture cũng hủy mọi worker jobs từng được submit.”** Sai. Worker tasks có lifecycle và handles riêng.

**“Error trong worker chắc chắn làm CronTrigger ngừng.”** Không mặc nhiên. Worker exception không tự ném ngược vào callback đã return.

**“Đặt poolSize thật lớn sẽ sửa cron sai.”** Sai. Pool size xử lý tài nguyên/độ trễ; biểu thức cron, zone và policy vẫn phải được xác minh độc lập.

## 27. Câu hỏi tự kiểm tra

**Câu 1.** Biểu thức `0 0 9 * * MON-FRI` có bao nhiêu trường, và ý nghĩa là gì?

**Đáp án:** Sáu trường: giây, phút, giờ, ngày trong tháng, tháng, thứ. Nó khớp 09:00:00 thứ Hai–thứ Sáu trong zone đã chọn.

**Câu 2.** `CronExpression.parse` có tự gọi một method mỗi ngày không?

**Đáp án:** Không. Nó chỉ phân tích và tính thời điểm. Cần TaskScheduler/@Scheduled để thực thi Runnable.

**Câu 3.** Lịch mỗi phút, callback cuối được hẹn 09:01 và hoàn tất 09:04:10. Lenient tính mốc nào tiếp theo?

**Đáp án:** 09:05, vì dựa trên lastCompletion.

**Câu 4.** Cùng dữ liệu trên, forFixedExecution chọn mốc nào?

**Đáp án:** 09:02, vì dựa trên lastScheduledExecution và không tự bỏ mốc đã hẹn bị trễ.

**Câu 5.** forFixedExecution có bảo đảm task chạy đúng 09:02 nếu đồng hồ thực tế đang là 09:04:10 không?

**Đáp án:** Không. Nó có thể yêu cầu lượt bù; task thực chạy trễ theo khả năng scheduler.

**Câu 6.** Nếu callback cron dùng `executor.execute(job)` rồi return, lastCompletion phản ánh hoàn tất job không?

**Đáp án:** Không. Nó phản ánh khi callback return sau submit; worker job có completion riêng.

**Câu 7.** `new CronTrigger(expression, ZoneId.of("Asia/Ho_Chi_Minh"))` và cùng expression trong UTC có cùng mốc Instant không?

**Đáp án:** Không nhất thiết. 09:00 giờ Việt Nam khác 09:00 UTC bảy giờ.

**Câu 8.** Một method có @Scheduled(cron=...) và code khác cũng gọi scheduler.schedule cùng job; có thể có hai registrations không?

**Đáp án:** Có. Đó là hai đường đăng ký độc lập nếu cả hai cùng hoạt động.

**Câu 9.** Có thể dùng `Scheduled.CRON_DISABLED` với `CronExpression.parse("-")` không?

**Đáp án:** Không. Dấu "-" là cơ chế disabled của attribute cron trong @Scheduled; CronExpression chuẩn không nhận đó như lịch hợp lệ.

**Câu 10.** Khi đổi cron trong database, active registration có tự đổi ngay không?

**Đáp án:** Không được bảo đảm. Coordinator phải validate rồi hủy/thay thế registration hoặc áp dụng cơ chế refresh được thiết kế.

**Câu 11.** Một taskScheduler có poolSize=1, callback A chặn 30 giây. Callback cron B tới hạn khi A chạy; B có thể trễ không?

**Đáp án:** Có. Scheduler thread có thể đang bận. Tách blocking business job sang worker có giới hạn nếu cần.

**Câu 12.** `resumeLenientExecution` và `resumeFixedExecution` nhận cùng loại checkpoint không?

**Đáp án:** Không. Một bên nhận last-known completion, bên kia nhận last-known scheduled timestamp.

**Câu 13.** Nếu chạy cùng cron ở ba replicas, CronTrigger có tự chọn một leader không?

**Đáp án:** Không. Cần coordination/lock/job system và idempotency theo yêu cầu.

**Câu 14.** Vì sao không dùng chỉ `Thread.sleep` để test CronTrigger?

**Đáp án:** Vì kết quả chịu lịch hệ điều hành, thời điểm bắt đầu và tải CI. Dùng Clock.fixed cho tính thời điểm, latch/timeout cho integration scheduling.

**Câu 15.** Khi DST tạo giờ địa phương không tồn tại, điều gì cần quyết định?

**Đáp án:** Có bỏ, chuyển sang giờ khác hay dùng mốc UTC? Business phải quy định và kiểm thử trên zone cụ thể.

## 28. Checklist đưa CronTrigger vào production

- [ ] Biểu thức cron theo **6 trường Spring**, có giây đầu tiên; không copy nguyên crontab/Quartz.
- [ ] Parse expression và validate business cadence trước khi lưu.
- [ ] Chỉ định zone rõ nếu lịch theo giờ địa phương; test DST nếu zone có chuyển đổi.
- [ ] Phân biệt CronExpression, CronTrigger và TaskScheduler.
- [ ] Kiểm tra kiểu TaskScheduler và thread model thực tế.
- [ ] Chọn **lenient hay fixed** có chủ đích; hiểu mốc skip/catch-up.
- [ ] Nếu resume, lưu đúng **lastCompletion hay lastScheduledExecution** theo factory.
- [ ] Không suy ra CronTrigger trong RAM là durable schedule.
- [ ] Nếu thay lịch động, giữ ScheduledFuture handle và thiết kế replace/stop thread-safe.
- [ ] Không để hai registrations trùng cùng logical job, trừ khi có chủ đích.
- [ ] Khi callback delegate, theo dõi business Future/job status riêng.
- [ ] Giới hạn scheduler và worker pools, queue, deadline và overload/rejection policy.
- [ ] Không dùng CallerRunsPolicy mà không đo khả năng callback bị giữ trên scheduler thread.
- [ ] Với nhiều pods, có distributed coordination và domain idempotency.
- [ ] Đặt trạng thái `DISABLED`, `NO_NEXT_FIRE`, `ACTIVE` rõ ràng cho scheduling manager.
- [ ] Khi thay đổi cron, ghi audit config, version, người sửa và next expected fire.
- [ ] Với lỗi/exception, cấu hình ErrorHandler và quan sát worker outcome tách biệt.
- [ ] Hủy lịch trên lifecycle shutdown và quản lý riêng các jobs đã được submit.
- [ ] Có metrics scheduler lag, callback duration, queue wait và business completion.
- [ ] Kiểm thử với Clock.fixed, SimpleTriggerContext và integration latch/timeout.
- [ ] Với job không được mất khi restart, có durable job store/queue và recovery policy.

## Kết luận

**CronTrigger là quy tắc sinh mốc kích hoạt từ biểu thức cron và lịch sử callback; TaskScheduler mới là thành phần nhận trigger để thực thi Runnable.** Trong Spring Framework, cần nắm cú pháp sáu trường, zone/DST và sự khác nhau giữa **lenient execution dựa trên lastCompletion** với **fixed execution dựa trên lastScheduledExecution**. Hai chế độ tạo kết quả rất khác khi callback chậm hoặc hệ thống phải chạy bù.

Đối với **lịch động**, đừng chỉ cập nhật một String trong cấu hình rồi tin scheduler tự thay. Hãy validate biểu thức/zone, quản lý ScheduledFuture của registration, cập nhật version và đo sát lifecycle. Khi callback delegate sang worker, **thời điểm callback hoàn tất và thời điểm business job hoàn tất là hai sự kiện khác nhau**. Trong môi trường đa pod, CronTrigger không tự đảm bảo duy nhất toàn cluster; cần coordinator, idempotency và trạng thái bền vững theo yêu cầu nghiệp vụ.

**Tài liệu chính thức:**

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): TaskScheduler, TriggerContext, CronTrigger, @Scheduled và executor.
- [Spring Framework — CronTrigger Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): lenient/fixed execution, resume factories và zone constructors.
- [Spring Framework — CronExpression Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronExpression.html): cú pháp sáu trường, L/W/#, isValidExpression, next.
- [Spring Framework — SimpleTriggerContext Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/SimpleTriggerContext.html): Clock và các mốc last execution.
- [Spring Framework — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): cron, zone, CRON_DISABLED và cách đăng ký annotation.
- [Java SE 17 — java.time](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/package-summary.html): Instant, ZonedDateTime, ZoneId, Clock và quy tắc thời gian.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-scheduling-004">PeriodicTrigger cho lịch chu kỳ</a>

<details>
<summary>Xem chi tiết</summary>

# PeriodicTrigger cho lịch chu kỳ trong Spring Framework

## Mục tiêu

`PeriodicTrigger` là một implementation của interface `org.springframework.scheduling.Trigger`. Nó dùng để mô tả lịch chạy **theo chu kỳ** bằng một `Duration`, với tùy chọn **initial delay**, **fixed delay** hoặc **fixed rate**. Khi đăng ký bằng `TaskScheduler.schedule(Runnable, Trigger)`, Spring gọi `nextExecution(TriggerContext)` để tìm thời điểm đủ điều kiện thực hiện lần kế tiếp.

Ví dụ cơ bản:

```java
PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofSeconds(5));
trigger.setInitialDelay(Duration.ofSeconds(2));

ScheduledFuture<?> handle = scheduler.schedule(
    () -> pollRemoteState(), trigger
);
```

Đây là snippet có `TaskScheduler scheduler`, method `pollRemoteState`, và import các API tương ứng; ví dụ chạy độc lập xuất hiện ở phần 6. **`PeriodicTrigger` không tự tạo thread hoặc tự gọi Runnable** — phần đó thuộc về `TaskScheduler`.

Bài Knowledge này nằm trong **Lịch động và Trigger** của module **Spring Framework - concurrency**. Sau bài, bạn có thể:

1. Giải thích `PeriodicTrigger` và sự khác nhau giữa `Trigger`, `TriggerContext`, `TaskScheduler`.
2. Hiểu mặc định của trigger là **fixed delay** chứ không phải fixed rate.
3. Tính thời điểm tiếp theo bằng `lastCompletion` hoặc `lastScheduledExecution` và hiểu `lastActualExecution`.
4. Cấu hình `Duration` period, `setInitialDelay(Duration)` và `setFixedRate(true)`.
5. Chạy demo Java 17+ / Spring Framework 6.2.12 với đồng hồ cố định và một lịch lặp thật.
6. Biết lúc nào nên dùng `scheduleAtFixedRate`/`scheduleWithFixedDelay` trực tiếp.
7. Xử lý hủy lịch, lỗi, việc chạy quá hạn, tài nguyên scheduler, transaction, context và nhiều replicas.
8. Phân biệt việc tính mốc chạy với bảo đảm thực thi đúng giờ hoặc exactly-once.

**Câu chốt:** `PeriodicTrigger` mô tả **khi nào** nên chạy, không quyết định **ở thread nào** hay nghiệp vụ chạy thành công ra sao.

## 1. Bức tranh tổng thể

Spring chia scheduling thành các phần độc lập:

```text
Ứng dụng
   |
   +-- Runnable: việc cần làm
   |
   +-- Trigger: mốc chạy tiếp theo
   |       |
   |       +-- PeriodicTrigger: chu kỳ theo Duration
   |       +-- CronTrigger: mốc lịch theo cron expression
   |       +-- custom Trigger: chính sách tùy biến
   |
   +-- TaskScheduler: đăng ký & thực thi
           |
           +-- ThreadPoolTaskScheduler
                   |
                   +-- ScheduledThreadPoolExecutor
                           |
                           +-- scheduler worker gọi task.run()
```

Các API liên quan:

| Thành phần | Vai trò |
| --- | --- |
| `org.springframework.scheduling.Trigger` | Interface trả thời điểm chạy kế tiếp qua `nextExecution(TriggerContext)`. |
| `org.springframework.scheduling.support.PeriodicTrigger` | Trigger có chu kỳ, initial delay và fixed-rate/fixed-delay mode. |
| `org.springframework.scheduling.TriggerContext` | Dữ liệu lịch sử thời gian của task, cùng `Clock`. |
| `org.springframework.scheduling.support.SimpleTriggerContext` | Implementation đơn giản, dùng để test `nextExecution` với lịch sử xác định. |
| `org.springframework.scheduling.TaskScheduler` | Đăng ký Runnable với trigger hoặc các API fixed-rate/delay trực tiếp. |
| `org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler` | Scheduler sử dụng pool scheduler threads và hỗ trợ cấu hình bean/lifecycle. |
| `java.util.concurrent.ScheduledFuture<?>` | Handle để theo dõi/hủy registration; không đại diện cho một result nghiệp vụ của từng lượt. |

Hãy tách ba câu hỏi:

- **Lịch:** Bao lâu lặp? Mốc dựa trên scheduled time hay completion?
- **Thực thi:** Pool nào chạy Runnable? Có worker rảnh tại mốc đến hạn không?
- **Nghiệp vụ:** Một lượt thành công hay thất bại? Có được retry/idempotent và có lưu bền vững không?

`PeriodicTrigger` giải quyết câu thứ nhất. Scheduler xử lý câu thứ hai. Ứng dụng vẫn phải giải quyết câu thứ ba.

## 2. `TriggerContext` lưu gì?

Interface `Trigger` có dạng:

```java
public interface Trigger {
    Instant nextExecution(TriggerContext triggerContext);
}
```

Đây là chữ ký minh họa. Nó trả `Instant` của lần kế tiếp, hoặc `null` với các loại trigger quyết định không chạy nữa. `PeriodicTrigger` hợp lệ thường tiếp tục tạo các mốc mới cho đến khi registration bị hủy hoặc scheduler đóng.

`TriggerContext` có các phương thức:

```java
Clock getClock();
Instant lastScheduledExecution();
Instant lastActualExecution();
Instant lastCompletion();
```

Đây là các chữ ký minh họa, không phải một chương trình độc lập. Ý nghĩa:

1. **`lastScheduledExecution`** là mốc trước đó được **lên lịch**. Nếu worker bận, nó có thể sớm hơn thời điểm thực tế bắt đầu.
2. **`lastActualExecution`** là mốc lần trước thực sự **bắt đầu** chạy.
3. **`lastCompletion`** là mốc lần trước **kết thúc**. Nếu job block HTTP 20 giây, dấu mốc này cách xa actual start.
4. **`getClock()`** cung cấp đồng hồ để trigger tính thời điểm, đặc biệt khi chưa có lượt chạy trước.

Hình dung:

```text
Scheduled:  10:00:00
Actual:     10:00:02  (bắt đầu muộn 2 giây)
Completion: 10:00:09  (task chạy 7 giây)

Period = 5 giây
```

Từ cùng một lịch sử, fixed rate và fixed delay sẽ cho các mốc khác nhau. **Không nên nhầm scheduled time với actual start time**, vì đó là hai khái niệm khác biệt ngay cả khi hệ thống bình thường thường cho giá trị gần nhau.

### Clock và test có thể lặp lại

`SimpleTriggerContext` chấp nhận một `Clock` trong constructor và cho phép cập nhật lịch sử bằng `update(Instant, Instant, Instant)`. Khi dùng `Clock.fixed`, bạn có thể kiểm tra thuật toán thời gian mà không cần sleep hoặc khởi động một thread pool thật.

Trong hệ thống thật, Spring quản lý `TriggerContext` của registration; ứng dụng thông thường **không tự cập nhật context** mỗi khi task chạy. Tự tạo context phù hợp cho unit test và các ví dụ minh họa công thức.

## 3. Mặc định là fixed delay

Constructor dùng `Duration`:

```java
PeriodicTrigger trigger = new PeriodicTrigger(
    Duration.ofSeconds(10)
);
```

Khi không bật fixed rate, `isFixedRate()` là `false`. Javadoc Spring mô tả **fixed delay** là mặc định: nếu đã có lượt trước, tính thời điểm mới bằng:

```text
nextExecution = lastCompletion + period
```

Ví dụ:

```text
Task A bắt đầu:   10:00:00
Task A hoàn tất:  10:00:07
Period:           10 giây
Lượt tiếp đủ hạn: 10:00:17
```

Nếu lượt đầu kết thúc muộn, lượt sau cũng dời theo thời điểm hoàn tất. Đây là cách tránh gửi lượt mới ngay trong lúc task còn đang thực hiện, phù hợp với polling hoặc xử lý cần một khoảng nghỉ sau kết quả trước.

**Không đồng nghĩa:** “mọi lượt bắt đầu cách nhau đúng 10 giây”. Khoảng cách giữa hai lần bắt đầu còn bao gồm thời lượng xử lý trước và mọi sự trì hoãn của scheduler.

### Tình huống nên dùng fixed delay

- Poll một API rồi nghỉ 10 giây sau khi lần poll trước hoàn tất.
- Dọn dẹp database không yêu cầu “đúng giây” mà ưu tiên không ép downstream quá thường xuyên.
- Gọi một service có thời lượng biến động; muốn bảo đảm một khoảng nghỉ từ lúc kết thúc.
- Job đồng bộ có chi phí cao và ít phải bám nhịp thời gian tuyệt đối.

Với các tác vụ khác nhau dùng chung scheduler, fixed delay của task A không tự khóa task B. Một pool có nhiều worker vẫn có thể chạy các registrations khác nhau cùng lúc.

## 4. Bật fixed rate bằng `setFixedRate(true)`

Khi muốn khoảng chu kỳ tính từ **mốc được lên lịch trước**:

```java
PeriodicTrigger trigger = new PeriodicTrigger(
    Duration.ofSeconds(10)
);
trigger.setFixedRate(true);
```

Nếu đã có execution history:

```text
nextExecution = lastScheduledExecution + period
```

Ví dụ period 5 giây:

```text
Scheduled lần trước = 10:00:00
Actual bắt đầu       = 10:00:02
Hoàn tất              = 10:00:09

Fixed rate next       = 10:00:05 (đã quá hạn)
Fixed delay next      = 10:00:14
```

Đừng thay `lastScheduledExecution` bằng `lastActualExecution` khi tính fixed rate. Trong ví dụ trên, dùng actual sẽ cho 10:00:07, **không phải** kết quả theo API của `PeriodicTrigger`.

### Fixed rate có chạy chồng nhiều lượt không?

Một registration thông qua `TaskScheduler.schedule(Runnable, Trigger)` được Spring quản lý bằng cơ chế rescheduling dựa trên lần chạy trước. Việc mốc fixed rate nằm trong quá khứ **không** tự làm chương trình tạo vô hạn bản sao Runnable đang chạy song song cho cùng registration. Sau khi lượt hiện tại hoàn tất, mốc tiếp theo có thể đã quá hạn, vì vậy lượt sau được chạy sớm khi worker có thể phục vụ.

Tuy nhiên:

- Nếu đăng ký hai instances hoặc hai registrations độc lập, chúng có thể chạy song song.
- Nếu nhiều ứng dụng/replicas cùng đăng ký job, mỗi process có scheduler riêng.
- Nếu task thực thi lâu hơn period, scheduler lag có thể tích lũy; mốc scheduled và actual có thể chênh lệch lớn.
- Fixed rate không mang nghĩa hard real-time và không có cam kết tự bù mọi mốc từng bị lỡ nếu hệ thống tắt.

### Khi fixed rate hợp lý

- Heartbeat hoặc metric snapshot cần một nhịp mục tiêu.
- Job nhẹ, p99 duration ngắn hơn period và worker scheduler thường rảnh.
- Cần canh nhịp khoảng cách giữa các thời điểm scheduled hơn là nghỉ trọn period sau khi kết thúc.

Nếu thường xuyên quá hạn, cần đo durations và downstream bottleneck; **không** chỉ tăng worker hoặc giảm period mà không hiểu tải.

## 5. Initial delay chỉ áp dụng cho lần đầu

`PeriodicTrigger` có thể cấu hình:

```java
PeriodicTrigger trigger = new PeriodicTrigger(
    Duration.ofSeconds(10)
);
trigger.setInitialDelay(Duration.ofSeconds(2));
```

Trong lần đầu (chưa có lịch sử), trigger tính từ đồng hồ trong `TriggerContext`:

```text
firstExecution = triggerContext.getClock().instant() + initialDelay
```

Sau khi một lượt đã chạy, initial delay **không tiếp tục được cộng ở mọi lần**; thuật toán sử dụng fixed delay hoặc fixed rate.

Ví dụ:

```text
Clock:         12:00:00
initialDelay:  2 giây
period:        10 giây
first due:     12:00:02

first scheduled:  12:00:02
first actual:     12:00:03
first completion: 12:00:07

next fixed delay: 12:00:17
next fixed rate:  12:00:12
```

Initial delay mặc định của `PeriodicTrigger` là 0, tức có thể bắt đầu ngay khi đã đăng ký và có scheduler worker rảnh. Trong Spring Framework 6.2, nên dùng overload `Duration` thay cho các overload số `long` và `TimeUnit` đã deprecated từ 6.0.

**Không nhầm với one-shot:** Trong Spring Framework 6.1+, một method chỉ có `@Scheduled(initialDelay=...)` mà không có fixedRate/fixedDelay/cron có thể được đăng ký chạy một lần. Trái lại, `PeriodicTrigger` có period thì **tiếp tục lặp lại** sau initial delay.

## 6. Ví dụ Java 17+ và Spring Framework 6.2.12 chạy được

Chương trình dưới đây gồm hai thí nghiệm:

- **A. Tính lịch không phụ thuộc scheduler:** Dùng `Clock.fixed`, `SimpleTriggerContext`, fixed-delay trigger và fixed-rate trigger. Ta biết chính xác mọi `Instant` đầu vào.
- **B. Chạy lịch thật:** Dùng `ThreadPoolTaskScheduler` với pool một worker, đăng ký một `PeriodicTrigger` khác có period 50 ms. `CountDownLatch(3)` báo đã chạy ít nhất ba lần, rồi main hủy registration.

Không dùng `Thread.sleep()` để đo một số lần chạy bằng kỳ vọng; số lượt quan sát của phần B có thể lớn hơn ba vì main không nhất thiết được scheduler ưu tiên ngay sau khi latch mở.

### Cấu trúc project

```text
periodic-trigger-demo/
├── pom.xml
└── src/main/java/example/PeriodicTriggerDemo.java
```

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>periodic-trigger-demo</artifactId>
    <version>1.0.0</version>

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
                <version>3.3.0</version>
                <configuration>
                    <mainClass>example.PeriodicTriggerDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/example/PeriodicTriggerDemo.java`:

```java
package example;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

public class PeriodicTriggerDemo {
    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean("periodicScheduler")
        public ThreadPoolTaskScheduler periodicScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("periodic-");
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        // PHẦN A: thuật toán thời gian xác định.
        Instant origin = Instant.parse("2026-01-01T00:00:00Z");
        Clock clock = Clock.fixed(origin, ZoneOffset.UTC);
        SimpleTriggerContext timing = new SimpleTriggerContext(clock);

        Duration period = Duration.ofSeconds(10);

        PeriodicTrigger delayTrigger = new PeriodicTrigger(period);
        delayTrigger.setInitialDelay(Duration.ofSeconds(2));

        PeriodicTrigger rateTrigger = new PeriodicTrigger(period);
        rateTrigger.setInitialDelay(Duration.ofSeconds(2));
        rateTrigger.setFixedRate(true);

        Instant firstDelay = delayTrigger.nextExecution(timing);
        Instant firstRate = rateTrigger.nextExecution(timing);

        // Scheduled = +2s, Actual = +3s, Completion = +7s.
        timing.update(
            origin.plusSeconds(2),
            origin.plusSeconds(3),
            origin.plusSeconds(7)
        );

        Instant nextDelay = delayTrigger.nextExecution(timing);
        Instant nextRate = rateTrigger.nextExecution(timing);

        System.out.println("defaultFixedDelay="
            + !delayTrigger.isFixedRate());
        System.out.println("firstDelay=" + firstDelay);
        System.out.println("firstRate=" + firstRate);
        System.out.println("nextDelay=" + nextDelay);
        System.out.println("nextRate=" + nextRate);

        // PHẦN B: dùng scheduler thật, ít nhất 3 lần chạy.
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            ThreadPoolTaskScheduler scheduler = context.getBean(
                "periodicScheduler", ThreadPoolTaskScheduler.class
            );

            CountDownLatch threeRuns = new CountDownLatch(3);
            AtomicInteger runCount = new AtomicInteger();
            AtomicReference<String> workerName = new AtomicReference<>();

            PeriodicTrigger live = new PeriodicTrigger(
                Duration.ofMillis(50)
            );
            live.setInitialDelay(Duration.ofMillis(20));

            ScheduledFuture<?> registration = scheduler.schedule(() -> {
                workerName.set(Thread.currentThread().getName());
                runCount.incrementAndGet();
                threeRuns.countDown();
            }, live);

            if (registration == null) {
                throw new IllegalStateException(
                    "Periodic trigger has no first execution"
                );
            }

            boolean reachedThree;
            try {
                reachedThree = threeRuns.await(5, TimeUnit.SECONDS);
            } finally {
                registration.cancel(false);
            }

            if (!reachedThree) {
                throw new IllegalStateException(
                    "Fewer than three runs within five seconds"
                );
            }

            System.out.println("ranAtLeastThreeTimes="
                + (runCount.get() >= 3));
            System.out.println("schedulerWorkerUsed="
                + (workerName.get() != null
                   && workerName.get().startsWith("periodic-")));
        }
    }
}
```

Chạy tại thư mục `periodic-trigger-demo` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

**Bảy dòng do chương trình in ra** khi chạy bình thường:

```text
defaultFixedDelay=true
firstDelay=2026-01-01T00:00:02Z
firstRate=2026-01-01T00:00:02Z
nextDelay=2026-01-01T00:00:17Z
nextRate=2026-01-01T00:00:12Z
ranAtLeastThreeTimes=true
schedulerWorkerUsed=true
```

### Giải thích từng dòng

- `defaultFixedDelay=true`: constructor `PeriodicTrigger(Duration)` mặc định fixed delay (`isFixedRate()` trả false).
- `firstDelay` và `firstRate` giống nhau vì chưa có execution history; cả hai chỉ dùng initial delay 2 giây và `Clock.fixed`.
- Context được cập nhật với lần trước scheduled tại +2 s, actual tại +3 s và hoàn tất tại +7 s.
- Fixed delay lấy completion+period = 7+10=17 giây.
- Fixed rate lấy scheduled+period = 2+10=12 giây.
- Callback thật tăng `runCount` và `countDown()` cho mỗi lượt. Main chỉ tiếp tục sau khi có **ít nhất ba lượt** trong 5 giây. Chương trình không cần tuyên bố “đúng ba lần” khi scheduler vẫn có thể chạy thêm trước khi main hủy.
- `workerName` là tên thread thực thi Runnable; prefix `periodic-` được cấu hình trên `ThreadPoolTaskScheduler`, không phải trên `PeriodicTrigger`.
- Gọi `cancel(false)` ở `finally` để yêu cầu dừng lượt tương lai ngay cả khi test timeout. `AnnotationConfigApplicationContext` đóng scheduler bean do Spring quản lý.

**Giới hạn kiểm chứng:** Phần A kiểm tra toán học thời gian chính xác mà không phụ thuộc tốc độ máy; phần B kiểm tra registration có chạy lặp thật. Nếu dependency không tải được hoặc máy không có đủ tài nguyên để thực hiện ba lượt trong 5 giây, chương trình báo lỗi thay vì in đủ bảy dòng. Đó là điều kiện môi trường, không phải thay đổi công thức trigger.

## 7. Khi nào cần PeriodicTrigger thay vì API trực tiếp?

Spring `TaskScheduler` đã có sẵn:

```java
scheduler.scheduleAtFixedRate(
    () -> refreshCache(), Duration.ofSeconds(5)
);

scheduler.scheduleWithFixedDelay(
    () -> refreshCache(), Duration.ofSeconds(5)
);
```

Đây là hai cách **thay thế nhau** trong một ứng dụng có `scheduler` và method `refreshCache`, không phải một ví dụ cần đăng ký cả hai cùng lúc.

Tài liệu Spring khuyên sử dụng những API chuyên biệt khi chỉ cần fixed-rate/fixed-delay đơn giản, vì chúng rõ ý và không cần thêm object trigger.

`PeriodicTrigger` có giá trị khi một thư viện hoặc component nhận **interface `Trigger` chung**:

```java
public void register(
        TaskScheduler scheduler, Runnable task, Trigger trigger) {
    scheduler.schedule(task, trigger);
}
```

Method trên là snippet, cần imports `TaskScheduler`, `Runnable`, `Trigger`. Caller có thể cung cấp:

```java
Trigger everyFifteenSeconds =
    new PeriodicTrigger(Duration.ofSeconds(15));

Trigger atFiveMinuteMarks =
    new CronTrigger("0 0/5 * * * *");
```

Hai snippet dùng chính `Trigger` interface; `CronTrigger` áp dụng lịch calendar/cron, khác ý nghĩa “nghỉ 15 giây sau completion”.

### Bảng lựa chọn

| Nhu cầu | Lựa chọn ưu tiên | Vì sao |
| --- | --- | --- |
| Fixed rate đơn giản | `scheduleAtFixedRate` | Rõ ràng, trực tiếp. |
| Fixed delay đơn giản | `scheduleWithFixedDelay` | Rõ ràng, trực tiếp. |
| API cần nhận mọi loại `Trigger` | `PeriodicTrigger` hoặc `CronTrigger` | Cùng một hợp đồng đăng ký `Trigger`. |
| Lặp theo ngày/thứ/giờ | `CronTrigger` | Mốc theo lịch, có vấn đề timezone/missed executions riêng. |
| Chu kỳ phụ thuộc dữ liệu hoặc kết quả lỗi | Custom `Trigger`/job orchestrator | Cần state và policy ngoài `PeriodicTrigger`. |
| Chạy đúng một lần tại một `Instant` | `TaskScheduler.schedule(Runnable, Instant)` | Không cần một trigger lặp. |
| Lịch phải tồn tại qua crash/restart | Persistent job store | Scheduler RAM không tự bảo đảm bền vững. |

## 8. Liên hệ với `@Scheduled`

Ví dụ annotation fixed delay:

```java
@Component
class Poller {
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void poll() {
        // Lượt sau đến hạn 5 giây sau khi lượt này hoàn tất.
    }
}
```

Đây là snippet trong Spring bean, cần `@EnableScheduling`, bean registration và imports.

Fixed rate:

```java
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
public void poll() {
    // Tính lịch theo chu kỳ bắt đầu cố định.
}
```

Là một phương án khác. Không nên đặt hai method trùng chữ ký trong cùng class và gọi chúng là cùng một job.

`@Scheduled` không có thuộc tính nhận trực tiếp một object `PeriodicTrigger`. Nếu dùng trigger được quyết định bằng code hoặc tại runtime, gọi programmatic `TaskScheduler.schedule(Runnable, Trigger)`.

**`@EnableScheduling` không cần cho demo phần 6**, vì demo chủ động gọi `schedule(...)`. Annotation support chỉ cần khi Spring phải tìm các method `@Scheduled`.

Nếu job phải có initial delay và lặp: `PeriodicTrigger.setInitialDelay(...)` vẫn lặp theo period. Đừng nhầm với cú pháp `@Scheduled(initialDelay=...)` one-time được hỗ trợ trên Spring Framework 6.1+ khi không khai báo lịch lặp.

## 9. Các vấn đề thời gian khi job mất nhiều thời gian

Nếu period = 1 giây nhưng job chạy 10 giây:

```text
Fixed delay:
  start = 0s
  complete = 10s
  next due = 11s

Fixed rate:
  scheduled = 0s
  complete = 10s
  next calculated due = 1s (đã quá hạn)
  next run có thể bắt đầu sớm khi worker rảnh
```

Một registration được scheduler quản lý không mặc định có mười bản sao đang chạy cùng lúc chỉ vì period đã trôi qua mười lần. Nhưng **nhiều registrations** hoặc **nhiều replicas** có thể chạm cùng dữ liệu đồng thời. Cần phân biệt hai loại concurrency.

Thời điểm trigger trả không phải SLA cứng. Những yếu tố làm actual start muộn:

- Worker scheduler bận do công việc khác.
- JVM pause, GC, CPU contention.
- JDBC/HTTP thiếu timeout, connection pool chật.
- Lock hoặc job trước chờ I/O.
- Scheduler đang pause/shutdown.
- Task bị lỗi/rejection, hoặc cấu hình executor khác chính sách mong đợi.

Theo dõi `scheduled`, `actual`, `completion` và `schedule lag`. Không dùng `Thread.sleep()` để cố “chỉnh cho trùng nhịp” trong Runnable; chính scheduler đã có nhiệm vụ tính deadline.

## 10. Cancellation và ScheduledFuture của lịch lặp

Khi gọi:

```java
ScheduledFuture<?> handle =
    scheduler.schedule(runnable, trigger);
```

Handle này theo dõi **registration lặp**, không phải một `CompletableFuture<T>` hoàn tất với result cho từng invocation.

- `cancel(false)` yêu cầu dừng những lượt chưa chạy mà không chủ động interrupt lượt đang chạy.
- `cancel(true)` có thể yêu cầu interrupt, nhưng không bảo đảm dừng JDBC, HTTP hoặc rollback transaction.
- `isDone()` với recurring schedule không có nghĩa “lượt đầu vừa hoàn tất”.
- `get()` trên future của periodic registration có thể chờ rất lâu hoặc cho tới khi registration bị hủy/hoàn tất bất thường; không gọi `get()` để chờ một lượt cụ thể.
- Khi cần theo dõi một lần chạy, dùng callback/status, metric hoặc `CountDownLatch` trong test.
- Nếu `schedule` trả `null` với trigger không tạo được lần thực thi đầu, không được giả định đã đăng ký thành công. `PeriodicTrigger` hợp lệ thường cho một lần đầu.

Khi ứng dụng shutdown, scheduler có lifecycle riêng. Spring có khả năng ngừng tiếp nhận và hủy pending tasks theo policy; một lịch RAM không sống qua process crash. Với công việc bắt buộc thực hiện, dùng job store hoặc broker bền vững và idempotency.

## 11. Xử lý lỗi, retry và throughput

Một periodic Runnable có thể throw. Spring scheduling infrastructure có `ErrorHandler` cho scheduled tasks; default behavior với repeating tasks thường log/suppress exception để các lượt sau có thể tiếp tục, nhưng cần kiểm tra configuration/framework version.

Ví dụ:

```java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setThreadNamePrefix("periodic-");
scheduler.setErrorHandler(error ->
    System.err.println("Task failed: " + error.getMessage())
);
```

Đây là snippet cấu hình, cần để Spring quản lý lifecycle hoặc tự initialize/shutdown đúng cách.

`ErrorHandler` ở trên chỉ ghi log; nó không tự retry một thất bại bằng exponential backoff. Nếu một task liên tục lỗi nhưng `ErrorHandler` giữ lịch hoạt động, hệ thống có thể log hàng nghìn lỗi mà không chữa được sự cố. Cần alert, backoff hoặc tắt job có chủ đích khi phụ thuộc không khả dụng.

Giới hạn throughput phải được thiết kế theo nguồn lực thật:

- Job CPU-bound: số worker phù hợp CPU quota và các tác vụ cùng máy.
- Job blocking I/O: giới hạn concurrency theo HTTP/JDBC connection pool, timeout, backpressure.
- Job có side effects: idempotency key và retry policy cho lỗi transient.
- Job nhạy cảm độ trễ: tránh để nó dùng chung một pool một thread với batch blocking lâu.
- Job cần trạng thái: lưu số lần thử, trạng thái và dấu mốc xử lý bền vững.

Không tăng `poolSize` vô hạn chỉ để sửa lỗi service chậm. Worker nhiều hơn có thể làm connection pool nghẽn nhanh hơn.

## 12. ThreadPoolTaskScheduler thực thi trực tiếp trên scheduler workers

`ThreadPoolTaskScheduler` bọc `ScheduledThreadPoolExecutor`; thân `Runnable` chạy **trên chính scheduler worker**. Mặc định có một scheduler thread và có thể cấu hình `setPoolSize(n)`.

```text
poolSize = 1

scheduler-worker-1: [long blocking job A .......... ][job B]
scheduled queue:           B due nhưng đợi worker rảnh
```

Tăng pool size hoặc tách nhiều schedulers có thể cô lập các job; tuy nhiên period của `PeriodicTrigger` vẫn là **quy tắc tính thời gian**, không phải pool sizing.

Nếu cần trigger ngắn rồi chạy nghiệp vụ lâu ở executor khác:

```java
scheduler.schedule(
    () -> businessExecutor.execute(() -> runBatch()),
    trigger
);
```

Đây là snippet cần `scheduler`, `businessExecutor`, `runBatch` và imports. Khi offload, scheduler Runnable chỉ **submit** rồi return. Fixed delay lúc ấy tính từ **lúc submission kết thúc**, không phải lúc batch xử lý xong. Nếu batch vẫn đang chạy, tick tiếp theo có thể tiếp tục tạo batch khác; phải quản lý in-flight jobs, queue, rejection và completion riêng.

Với `CallerRunsPolicy` của executor đích, khi bão hòa, chính scheduler thread có thể phải chạy batch. Đừng dùng offload như lời hứa rằng scheduler luôn rảnh.

## 13. ThreadLocal, transaction và nhiều replicas

Scheduler workers khác HTTP caller. Một `ThreadLocal` tự định nghĩa, MDC, `SecurityContext` hoặc transaction gắn caller **không tự theo**. Với một recurring job có thể không hề có HTTP caller, vì nó được scheduler chủ động gọi.

Nếu cần correlation ID, nên truyền một job ID/tenant ID immutable hoặc dùng context propagation có cleanup. Trên worker thread được tái sử dụng, bất kỳ `ThreadLocal.set()` nào cũng nên được dọn trong `finally`, để một job không đọc nhầm context của lượt trước.

Với JDBC/JPA và imperative Spring `@Transactional`, ranh giới giao dịch thường gắn thread. Nếu recurring job cần atomicity:

```java
@Component
class Poller {
    private final WriterService writer;

    Poller(WriterService writer) {
        this.writer = writer;
    }

    public void tick() {
        writer.saveBatch();
    }
}

@Component
class WriterService {
    @Transactional
    public void saveBatch() {
        // Thao tác dữ liệu, transaction bắt đầu trên thread đang chạy.
    }
}
```

Đây là snippet Spring bean không độc lập; `Poller.tick()` còn phải được đăng ký qua scheduler hoặc `@Scheduled`, và cần transaction manager, imports và data access thật. Dùng bean khác giúp lời gọi `saveBatch()` đi qua transaction proxy, tránh self-invocation trong một bean.

Không copy `EntityManager` hoặc transaction-bound JDBC connection từ request thread sang scheduler thread. Với nghiệp vụ nhiều replicas, mỗi replica có registration riêng: `PeriodicTrigger` không tự tạo distributed lock hoặc exactly-once guarantee. Cần job ownership, unique constraint, idempotency và status store khi yêu cầu.

## 14. Checklist review thiết kế

1. Đã dùng `Duration` với period **dương, hợp lệ** chưa?
2. Mode nào? `isFixedRate()` đúng như mong muốn? Mặc định fixed delay có bị hiểu nhầm không?
3. Initial delay chỉ áp dụng lần đầu, có đúng điều kiện startup không?
4. Scheduler bean nào được dùng? Thread prefix, pool size, lifecycle và shutdown?
5. `TaskScheduler.schedule(task, trigger)` có được đăng ký đúng một lần theo job identity?
6. Thời lượng task p95/p99 có vượt period không, có backlog hoặc schedule lag không?
7. Nhiều registrations hay nhiều replicas có thể chạy chồng trên cùng dữ liệu không?
8. Khi task lỗi, ErrorHandler log/suppress/propagate thế nào? Có giám sát failures?
9. Có dùng `ScheduledFuture.get()` nhầm để chờ mỗi lượt không?
10. Có giữ `ScheduledFuture` để cancel khi config thay đổi hoặc ứng dụng đóng không?
11. Có cần runtime reconfiguration? Nếu có, hủy registration cũ rồi tạo trigger mới có kiểm soát.
12. Nếu offload, có theo dõi completion của business job riêng, xử lý rejection và backlog?
13. Có timeout thật ở JDBC/HTTP, không chỉ timeout lúc chờ một Future?
14. Có bảo vệ transaction/resource và cleanup ThreadLocal đúng worker thread?
15. Lịch có cần tồn tại sau restart không? Nếu có, phải dùng durable job store.
16. Có test `nextExecution` với đồng hồ cố định và test actual invocation bằng latch không?

## 15. Những lỗi phổ biến

| Sai lầm | Giải thích đúng |
| --- | --- |
| “PeriodicTrigger tự tạo scheduler thread.” | Chỉ trả `Instant`; TaskScheduler mới thực thi Runnable. |
| “Mặc định fixed rate.” | Mặc định **fixed delay**. |
| “Fixed rate tính từ actual start.” | Tính từ `lastScheduledExecution`. |
| “Fixed delay tính từ scheduled time.” | Tính từ `lastCompletion`. |
| “Initial delay được cộng mọi lượt.” | Chỉ dùng cho lần đầu. |
| “Fixed rate chắc chắn chạy đúng nhịp không trễ.” | Pool load, blocking, CPU, clock và queue có thể làm actual start muộn. |
| “Fixed rate tạo nhiều bản sao cùng registration vô hạn.” | Spring trigger scheduling cập nhật history và reschedule sau lượt trước; nhiều registrations mới cần xét overlap riêng. |
| “@Scheduled nhận PeriodicTrigger object trực tiếp.” | Annotation dùng các thuộc tính lịch, API programmatic mới nhận `Trigger`. |
| “Chưa bật @EnableScheduling thì scheduler.schedule không chạy.” | Programmatic scheduling không cần annotation scanning. |
| “Hủy ScheduledFuture là tự rollback mọi side effects.” | Cancellation chỉ là yêu cầu, I/O/transaction có lifecycle riêng. |
| “Một registration trong mỗi pod tương đương một job toàn cụm.” | Mỗi pod có scheduling riêng; phải thiết kế coordination/idempotency. |
| “Lịch trong RAM đảm bảo chạy sau crash.” | Cần durable state/job orchestration riêng. |
| “Trigger tự đổi period theo tình trạng backend.” | PeriodicTrigger có cấu hình chu kỳ; nếu cần backoff động thì custom Trigger/state. |
| “ThreadLocal của request tự có trong scheduled task.” | Thread-bound context không tự truyền sang scheduler worker. |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `new PeriodicTrigger(Duration.ofSeconds(5))` mặc định fixed rate hay fixed delay?

**Đáp án:** Fixed delay. Muốn fixed rate cần `setFixedRate(true)`.

**Câu 2.** Scheduled = 10:00:00, actual = 10:00:02, completion = 10:00:09, period 5 giây. Mốc tiếp theo là gì?

**Đáp án:** Fixed rate: 10:00:05. Fixed delay: 10:00:14. Actual start không phải mốc để cộng period ở hai mode cơ bản này.

**Câu 3.** Đặt `setInitialDelay(Duration.ofSeconds(2))` có làm mọi lượt nghỉ thêm 2 giây không?

**Đáp án:** Không. Chỉ ảnh hưởng lần đầu khi chưa có execution history.

**Câu 4.** Có thể dùng riêng PeriodicTrigger để khởi động Runnable không?

**Đáp án:** Không. Phải đăng ký với `TaskScheduler.schedule(Runnable, Trigger)` hoặc một component có scheduler.

**Câu 5.** Vì sao `SimpleTriggerContext(Clock.fixed(...))` hữu ích hơn ngủ một khoảng trong unit test?

**Đáp án:** Mọi mốc scheduled/actual/completion và thời gian hiện tại đều xác định, nên toán học thời gian có kết quả tái lập không phụ thuộc CPU scheduling.

**Câu 6.** Trong demo, tại sao `nextDelay=2026-01-01T00:00:17Z` và `nextRate=2026-01-01T00:00:12Z`?

**Đáp án:** Period = 10 s, completion = +7 s, scheduled = +2 s. Fixed delay 7+10=17; fixed rate 2+10=12.

**Câu 7.** Vì sao demo không in `runCount == 3`?

**Đáp án:** Sau khi latch mở, scheduler có thể chạy thêm trước khi main gọi cancel. Demo chỉ chứng minh có **ít nhất ba invocation**.

**Câu 8.** `ScheduledFuture.get()` có phải cách chờ lượt thứ nhất của recurring registration?

**Đáp án:** Không. Future đại diện cho cả registration; nó có thể chờ đến lúc hủy. Theo dõi từng lượt bằng latch/callback/metric riêng.

**Câu 9.** Khi chỉ cần fixed delay 5 giây và không có API `Trigger` chung, nên dùng gì?

**Đáp án:** `scheduler.scheduleWithFixedDelay(task, Duration.ofSeconds(5))` thường rõ hơn tạo `PeriodicTrigger`.

**Câu 10.** Fixed-rate next time nằm trong quá khứ. Scheduler có nhất thiết chạy nhiều bản sao cùng registration đồng thời không?

**Đáp án:** Không. Với trigger-based scheduling, Spring xử lý từng lượt rồi reschedule; task có thể đủ hạn ngay, nhưng không tự sinh nhiều bản sao đồng thời của cùng registration.

**Câu 11.** Hai replicas dùng cùng `PeriodicTrigger` để ghi một bảng có tránh được duplicate không?

**Đáp án:** Không. Cần distributed job coordination/idempotency/unique constraint theo nghiệp vụ.

### Ghi nhớ

`PeriodicTrigger` là một `Trigger` mô tả **chu kỳ bằng `Duration`**. Mặc định **fixed delay**: `lastCompletion + period`. Khi `setFixedRate(true)`, nó dùng **fixed rate**: `lastScheduledExecution + period`. `initialDelay` chỉ ảnh hưởng lượt đầu. Việc thực thi, khả năng chạy trễ, hủy, lỗi và lifecycle thuộc về `TaskScheduler` và code nghiệp vụ. Với lịch đơn giản nên dùng API fixed-rate/fixed-delay trực tiếp; với một thư viện yêu cầu abstraction `Trigger`, `PeriodicTrigger` là cách biểu đạt rõ ràng, dễ test.

### Tài liệu tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): TaskScheduler, TriggerContext và PeriodicTrigger.
- [Spring Framework 6.2 — PeriodicTrigger](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/support/PeriodicTrigger.html): Duration, mặc định fixed delay, setFixedRate và nextExecution.
- [Spring Framework 6.2 — SimpleTriggerContext](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/support/SimpleTriggerContext.html): Clock, constructor và update timestamps.
- [Spring Framework 6.2 — TriggerContext](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/TriggerContext.html): các dấu mốc execution history.
- [Spring Framework 6.2 — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): scheduler worker/lifecycle.
- [Java SE 17 — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): Future/cancellation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-scheduling-005">SchedulingConfigurer và ScheduledTaskRegistrar</a>

<details>
<summary>Xem chi tiết</summary>

# SchedulingConfigurer và ScheduledTaskRegistrar

## 1. Bài toán: khi @Scheduled tĩnh không đủ

Trong Spring Framework, `@Scheduled` là cách gọn để mô tả một lịch cố định:

```java
@Scheduled(fixedDelay = 5_000)
public void refresh() {
    refreshCache();
}
```

Nhưng không phải mọi lịch đều có thể biểu diễn đầy đủ ngay trong annotation. Một ứng dụng có thể cần:

- Chọn một `TaskScheduler` cụ thể trong số nhiều scheduler beans.
- Thêm lịch bằng code Java dựa trên cấu hình lúc khởi tạo.
- Thực thi một `Runnable` không gắn annotation `@Scheduled`.
- Dùng `Trigger` tự tính lần chạy tiếp theo từ thời điểm hoàn tất và trạng thái nghiệp vụ.
- Đăng ký đồng thời cron, fixed rate, fixed delay và one-time task.
- Gắn `ObservationRegistry` để theo dõi scheduled executions khi có hạ tầng Micrometer.
- Đổi một khoảng nghỉ hoặc quy tắc lịch mà không viết cứng trong annotation.
- Quản lý lịch chạy, hủy và shutdown có chủ đích.

Spring cung cấp hai thành phần cho bài toán này:

1. **`SchedulingConfigurer`** là một **callback extension point**; bạn triển khai `configureTasks(ScheduledTaskRegistrar)` để cấu hình quá trình scheduling.
2. **`ScheduledTaskRegistrar`** là **đối tượng đăng ký/quản lý các task definitions** và đưa chúng vào một `TaskScheduler`, gồm các kiểu `TriggerTask`, `CronTask`, fixed-rate, fixed-delay, one-time.

**Mục tiêu học tập:**

1. Biết vai trò của `@EnableScheduling`, `SchedulingConfigurer`, `ScheduledTaskRegistrar`, `TaskScheduler` và `Trigger`.
2. Chọn được đúng API `addTriggerTask`, `addCronTask`, `addFixedRateTask`, `addFixedDelayTask` hoặc `addOneTimeTask`.
3. Hiểu sự khác biệt giữa **đăng ký lịch lúc khởi tạo** và **cập nhật lịch đang hoạt động tại runtime**.
4. Giải thích `TriggerContext.lastScheduledExecution()`, `lastActualExecution()` và `lastCompletion()`.
5. Viết project Spring Framework Java 17+ có một `SchedulingConfigurer` và nhiều task được đăng ký programmatically.
6. Viết một `Trigger` thay đổi khoảng nghỉ giữa những lần chạy theo trạng thái thread-safe, có điểm dừng rõ ràng.
7. Tránh tạo duplicate jobs, deadlock, backlog, mất job khi restart, lỗi self-invocation hay dùng sai executor.
8. Kiểm thử deterministic bằng `CountDownLatch` thay vì đoán scheduler sẽ chạy đúng tại một mili giây.
9. Biết dùng observation, job ID, cancellation, idempotency và distributed coordination khi cần.

**Phạm vi:** nhóm **Lịch động và Trigger** của chủ đề Spring Framework – concurrency. Đây là hướng dẫn cấu hình scheduling và các giới hạn của cơ chế in-process Spring, không phải lời hứa về job persistence hoặc distributed exactly-once.

## 2. Bản đồ các thành phần

```text
@Configuration + @EnableScheduling
                |
                v
ScheduledAnnotationBeanPostProcessor
                |
                | phát hiện @Scheduled
                | tìm SchedulingConfigurer beans
                v
       SchedulingConfigurer
       configureTasks(registrar)
                |
                | setTaskScheduler(...)
                | addTriggerTask(...)
                | addCronTask(...)
                | addFixedRateTask(...)
                | addFixedDelayTask(...)
                | addOneTimeTask(...)
                v
       ScheduledTaskRegistrar
       lưu task definitions và đăng ký
                |
                v
            TaskScheduler
                |
                +-- schedule(Runnable, Trigger)
                +-- scheduleAtFixedRate(...)
                +-- scheduleWithFixedDelay(...)
                +-- schedule(Runnable, Instant)
                |
                v
           Runnable callback
                |
                v
           business service
```

Sơ đồ phân chia **cấu hình**, **đăng ký**, **quyết định thời điểm** và **thực thi**. Một số bước được thực hiện trong lifecycle initialization của Spring; không phải mỗi lần trigger tới thì Spring lại gọi `configureTasks`.

### 2.1. SchedulingConfigurer là gì?

Trong Spring Framework:

```java
public interface SchedulingConfigurer {
    void configureTasks(ScheduledTaskRegistrar taskRegistrar);
}
```

Một class `@Configuration` có thể `implements SchedulingConfigurer`; hoặc ứng dụng có thể đăng ký một **bean triển khai interface** đó. Khi scheduling infrastructure được bật, Spring gọi callback để bạn chọn scheduler và đăng ký task.

`SchedulingConfigurer` **không phải** một scheduler, không tự chứa thread pool và không phải một API tạo job bền vững. Nó chỉ là chỗ để **cấu hình registrar**.

### 2.2. ScheduledTaskRegistrar là gì?

`ScheduledTaskRegistrar` là helper quản lý các task được đăng ký với `TaskScheduler`. Nó cung cấp API cấu hình lúc startup, như:

```java
registrar.setTaskScheduler(taskScheduler);
registrar.addTriggerTask(runnable, trigger);
registrar.addCronTask(runnable, "0 */5 * * * *");
registrar.addFixedRateTask(runnable, Duration.ofSeconds(10));
registrar.addFixedDelayTask(runnable, Duration.ofSeconds(10));
registrar.addOneTimeTask(runnable, Duration.ofSeconds(1));
```

Đây là **fragment API**, cần biến `registrar`, `taskScheduler`, `runnable` và `trigger` đã khai báo, import `Duration`. Những overload nhận `Duration` là lựa chọn hiện đại (tránh overload nhận `long` đã deprecated trong Spring 6.x); `addOneTimeTask` xuất hiện từ Spring Framework **6.1**.

### 2.3. TaskScheduler làm gì?

`TaskScheduler` là giao diện tính thời gian/sắp đặt tác vụ. Ví dụ:

- `ThreadPoolTaskScheduler`: wrapper quanh `ScheduledThreadPoolExecutor`; callback thường chạy trên chính scheduler worker threads.
- `SimpleAsyncTaskScheduler` (Spring 6.1+): một scheduler thread giữ nhịp và bàn giao các task phù hợp sang thread/executor riêng, đặc biệt hữu ích với virtual threads trên Java 21+; **fixed-delay** là ngoại lệ chạy trên scheduler thread.
- `ConcurrentTaskScheduler`: adapter quanh `ScheduledExecutorService` được cung cấp từ bên ngoài.
- Scheduler tùy chỉnh hoặc được cấu hình theo môi trường container.

Chọn loại scheduler ảnh hưởng tới pool size, fixed-delay semantics, overlap, queue wait và lifecycle. `SchedulingConfigurer` giúp chọn nó nhưng **không tự biến** local scheduler thành distributed scheduler.

### 2.4. Trigger làm gì?

`Trigger` chỉ quyết định **lần thực thi tiếp theo** dựa trên `TriggerContext`. Nó không nên tự gửi email, cập nhật DB hoặc chạy business task nặng trong `nextExecution(...)`.

```java
@FunctionalInterface
public interface Trigger {
    Instant nextExecution(TriggerContext context);
}
```

Đây là mô hình interface Spring 6.x; kiểu `Instant` từ Java Time. Trả **`null`** có nghĩa không còn một lần kích hoạt tiếp theo. Không trả `Instant.now()` mãi mãi từ một callback quá nhanh mà không có chính sách giới hạn, vì có thể tạo chuỗi kích hoạt liên tục tiêu thụ tài nguyên.

## 3. Khi nào dùng @Scheduled, khi nào dùng SchedulingConfigurer?

| Nhu cầu | Cách phù hợp |
| --- | --- |
| Chạy mỗi 10 giây theo lịch cố định | `@Scheduled(fixedRate=...)` |
| Chạy 5 giây sau khi lần trước kết thúc | `@Scheduled(fixedDelay=...)` |
| Chạy theo cron cố định trong config | `@Scheduled(cron=...)` |
| Muốn chọn một scheduler riêng cho nhóm task | `SchedulingConfigurer.setTaskScheduler(...)` hoặc qualifier phù hợp ở API hỗ trợ |
| Muốn đăng ký `Runnable` programmatically lúc startup | `ScheduledTaskRegistrar.add...Task(...)` |
| Lần chạy tiếp phụ thuộc vào kết quả/lịch sử task trước | `Trigger` với `TriggerContext` |
| Muốn runtime reschedule ngay khi admin thay cron | Quản lý `ScheduledFuture`/`ScheduledTask`, cancel + reschedule với đồng bộ và lưu cấu hình |
| Muốn một job tồn tại qua restart và nhiều replica không chạy trùng | Durable job store/queue, Quartz với cấu hình phù hợp hoặc coordinator chuyên dụng |
| Muốn task chạy async trên executor riêng | `@Async` hoặc explicit dispatch, nhưng quản lý completion/overlap riêng |

**Đừng chọn code-based scheduling chỉ vì “linh hoạt hơn”.** Nếu lịch đơn giản, `@Scheduled` diễn đạt dễ đọc và ít bookkeeping hơn. Dùng `SchedulingConfigurer` khi bạn thực sự cần **control plane lập lịch**.

## 4. Lifecycle của @EnableScheduling và registrar

`@EnableScheduling` đăng ký Spring infrastructure để phát hiện `@Scheduled`, tạo/resolve registrar và liên kết các tasks.

Thông thường:

1. Spring tạo các bean definitions và Spring-managed instances.
2. Bean post-processor nhận diện các method `@Scheduled`.
3. Hạ tầng scheduling tìm/áp dụng các `SchedulingConfigurer` có trong context.
4. `configureTasks(ScheduledTaskRegistrar)` được gọi khi đăng ký task.
5. Registrar có scheduler và các task definitions, sau đó Spring lên lịch các tasks vào scheduler theo lifecycle phù hợp.
6. Scheduler chạy callback theo trigger, còn context quản lý shutdown theo loại bean.

**`configureTasks` không phải callback per tick.** Không đọc file config trong method này rồi kỳ vọng Spring gọi lại mỗi khi config thay đổi. Nó là nơi **đăng ký và cấu hình**, không phải runtime event loop.

### 4.1. Không gọi lifecycle methods của registrar thủ công

Đừng tạo `new ScheduledTaskRegistrar()` ở business method, gọi `afterPropertiesSet()` hay `scheduleTasks()` thủ công để “kích hoạt” một bean đã được Spring quản lý. Registrar có lifecycle do hạ tầng Spring sở hữu; thao tác thủ công dễ gây hai lần đăng ký, sai ownership và bỏ qua shutdown.

### 4.2. Registrar và TaskScheduler khác nhau ở quyền sở hữu

`SchedulingConfigurer`/registrar tổ chức những task nào được đăng ký. `TaskScheduler` chịu trách nhiệm khi nào thực thi và cung cấp cancellation handles. Một scheduler bean có thể phục vụ **nhiều** task; một registrar cũng có thể đăng ký **nhiều kiểu** task. Chọn scheduler bằng `registrar.setTaskScheduler(...)` không đồng nghĩa bạn tạo một worker pool riêng cho từng task.

### 4.3. Chọn đúng ApplicationContext

`@EnableScheduling` áp dụng cho context nơi cấu hình được nạp. Với ứng dụng web có parent/child contexts, bean `@Scheduled` có thể được tạo trong context khác; hãy xác minh cấu hình scheduling thật sự có hiệu lực ở context phù hợp. Không khai báo hai bản bean chứa cùng method `@Scheduled` trừ khi muốn có hai lịch độc lập.

## 5. Các phương thức đăng ký quan trọng

| API của ScheduledTaskRegistrar | Quy tắc kích hoạt | Dữ liệu đầu vào cần |
| --- | --- | --- |
| `setTaskScheduler(TaskScheduler)` | Chọn scheduler dùng chung | Bean `TaskScheduler` |
| `setScheduler(Object)` | Nhận `TaskScheduler` hoặc `ScheduledExecutorService` được wrap | Object scheduler đúng kiểu |
| `addTriggerTask(Runnable, Trigger)` | Gọi `Trigger.nextExecution` để tính lần tiếp theo | Runnable + Trigger |
| `addCronTask(Runnable, String)` | Theo biểu thức cron | Runnable + cron 6 fields |
| `addFixedRateTask(Runnable, Duration)` | Chu kỳ fixed-rate | Runnable + Duration |
| `addFixedDelayTask(Runnable, Duration)` | Khoảng nghỉ sau completion của invocation | Runnable + Duration |
| `addOneTimeTask(Runnable, Duration)` | Một lần sau initial delay (Spring 6.1+) | Runnable + Duration |
| `setObservationRegistry(ObservationRegistry)` | Gắn Micrometer observation khi phụ thuộc đã được cấu hình | ObservationRegistry |
| `getScheduledTasks()` | Lấy những task handle registrar đã lên lịch | Set các `ScheduledTask` |

Các method `add...Task` thường được dùng **trong `configureTasks` lúc setup**. Những method `schedule...Task` của registrar có thể trả `ScheduledTask` và hỗ trợ registration tại thời điểm phù hợp với trạng thái registrar, nhưng không nên coi việc gọi chúng ở một HTTP controller bất kỳ là giải pháp hot-reload hoàn chỉnh nếu chưa xác minh synchronization, lifecycle và ownership.

### 5.1. Không nhầm add...Task với schedule...Task

- `addTriggerTask` ghi task definition cho registrar, thường trước khi scheduler được khởi động.
- `scheduleTriggerTask(new TriggerTask(...))` có thể lên lịch task tại thời điểm gọi nếu registrar đã initialized, và trả một `ScheduledTask` (tùy lifecycle).
- `TaskScheduler.schedule(runnable, trigger)` là API scheduling trực tiếp, trả một `ScheduledFuture<?>`.

Khi cần **API admin đổi lịch nhiều lần**, dùng một **schedule manager** có state riêng, giữ handle và có cơ chế cancel/reschedule rõ ràng; không dùng registrar như một map mutable không có locking.

### 5.2. @Scheduled vẫn có thể chạy cùng task bạn đăng ký bằng code

Nếu bạn vừa:

```java
@Scheduled(fixedRate = 10_000)
public void refresh() {
    refreshCache();
}
```

vừa làm:

```java
registrar.addFixedRateTask(service::refresh, Duration.ofSeconds(10));
```

thì **có hai đăng ký lịch độc lập** cho cùng một logic, nên hai lần gọi có thể chồng nhau hoặc xuất hiện gần như trùng thời điểm. Không “tự gộp” hai cơ chế này thành một task. Với mỗi job, nên chọn **một owner đăng ký** hoặc cố tình phối hợp có tài liệu rõ.

## 6. Ví dụ hoàn chỉnh Spring Framework Java 17+: SchedulingConfigurer đăng ký Trigger và one-time task

Project này minh họa chính xác API được giao: Spring nhận một bean `SchedulingConfigurer`, gọi `configureTasks`, chọn `ThreadPoolTaskScheduler`, đăng ký `TriggerTask` với `Trigger` chỉ chạy một lần và một `one-time task`.

Ví dụ dùng JDK 17+, Maven và Spring Framework **6.2.14**. Output sử dụng latch/counter, **không dựa vào thứ tự tick** hay giả định scheduler chạy chính xác một mili giây.

### 6.1. Cấu trúc

```text
scheduling-registrar-demo/
├── pom.xml
└── src/main/java/demo/
    ├── RegistrarBasicsDemo.java
    └── AdaptiveTriggerDemo.java
```

### 6.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>scheduling-registrar-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.14</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 6.3. src/main/java/demo/RegistrarBasicsDemo.java

```java
package demo;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class RegistrarBasicsDemo {

    public static class Probe {
        final AtomicInteger triggerRuns = new AtomicInteger();
        final AtomicInteger oneTimeRuns = new AtomicInteger();
        final CountDownLatch triggerDone = new CountDownLatch(1);
        final CountDownLatch oneTimeDone = new CountDownLatch(1);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class AppConfig {
        @Bean
        public Probe probe() {
            return new Probe();
        }

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("schedule-");
            return scheduler;
        }

        @Bean
        public SchedulingConfigurer schedulingConfigurer(
                ThreadPoolTaskScheduler taskScheduler,
                Probe probe) {

            return registrar -> {
                registrar.setTaskScheduler(taskScheduler);

                AtomicBoolean first = new AtomicBoolean(true);
                Trigger once = context -> {
                    if (first.getAndSet(false)) {
                        return context.getClock().instant();
                    }
                    return null;
                };

                registrar.addTriggerTask(() -> {
                    probe.triggerRuns.incrementAndGet();
                    probe.triggerDone.countDown();
                }, once);

                registrar.addOneTimeTask(() -> {
                    probe.oneTimeRuns.incrementAndGet();
                    probe.oneTimeDone.countDown();
                }, Duration.ofMillis(1));
            };
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             AppConfig.class)) {
            Probe probe = context.getBean(Probe.class);

            if (!probe.triggerDone.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("trigger task missing");
            }
            if (!probe.oneTimeDone.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("one-time task missing");
            }

            System.out.println("trigger-runs=" +
                    probe.triggerRuns.get());
            System.out.println("one-time-runs=" +
                    probe.oneTimeRuns.get());
        }
    }
}
```

### 6.4. Chạy và kết quả

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.RegistrarBasicsDemo
```

Hai dòng do chương trình in khi context chạy bình thường:

```text
trigger-runs=1
one-time-runs=1
```

Maven/Spring có thể ghi log riêng.

**Giải thích chi tiết:**

1. `@EnableScheduling` nạp scheduling infrastructure; không cần `@EnableAsync` vì callback không dùng `@Async`.
2. `AppConfig` tạo bean `ThreadPoolTaskScheduler`; Spring quản lý initialization/shutdown của bean.
3. Bean `SchedulingConfigurer` là **một implementation interface** được tạo qua `@Bean`. Đây là cách hợp lệ bên cạnh việc cho `@Configuration` trực tiếp `implements SchedulingConfigurer`.
4. Trong callback `configureTasks`, registrar nhận **đúng scheduler đã inject** bằng `setTaskScheduler`.
5. `Trigger` dựa trên `AtomicBoolean`: lần tính lịch đầu trả `context.getClock().instant()`, các lần sau trả `null`; vì vậy chỉ có **một lần chạy**.
6. `addOneTimeTask` đăng ký một task khác sau 1 ms. Nó có execution riêng, không phải nhánh của trigger task đầu.
7. `CountDownLatch` đợi **callback đã chạy**, không chỉ đợi `configureTasks` được gọi.
8. Cả hai count là 1; thứ tự chúng chạy không được dùng làm assertion.

**Không đổi** `@Bean schedulingConfigurer(...)` thành một object tự `new` rồi quên đăng ký bean; nếu không có SchedulingConfigurer bean trong context, callback sẽ không được Spring khám phá.

### 6.5. Vì sao cách viết tránh lỗi @Configuration(proxyBeanMethods=false)?

Khi dùng `proxyBeanMethods = false`, gọi trực tiếp `taskScheduler()` từ một `@Bean` method khác có thể tạo object Java mới thay vì lấy bean đã đăng ký. Ví dụ đã dùng **method parameters**:

```java
@Bean
SchedulingConfigurer schedulingConfigurer(
        ThreadPoolTaskScheduler taskScheduler,
        Probe probe) {
    return registrar -> {
        registrar.setTaskScheduler(taskScheduler);
        // ...
    };
}
```

Spring inject đúng bean reference. Tránh cách sau trong lite configuration:

```java
@Bean
SchedulingConfigurer incorrectConfigurer() {
    return registrar -> registrar.setTaskScheduler(taskScheduler());
}
```

Đây là **phản mẫu** vì gọi method `taskScheduler()` trực tiếp khi `proxyBeanMethods=false` có thể tạo một scheduler mới không phải bean đã được container quản lý, kéo theo lifecycle và thread leakage.

## 7. Ví dụ hoàn chỉnh Spring Java 17+: Trigger đọc khoảng nghỉ mới

Một `Trigger` tự viết có thể đọc một giá trị thread-safe mỗi khi cần quyết định **lần chạy tiếp theo**. Điều đó hữu ích cho adaptive polling: nếu lần xử lý trước kết thúc, chờ một khoảng nghỉ có thể thay đổi dựa trên tải hoặc cấu hình.

**Giới hạn phải nhớ:** thay giá trị cấu hình **không tự reprogram một future đã được scheduler xếp lịch**. Mô hình dưới đây cập nhật delay **bên trong chính callback**, trước khi `Trigger.nextExecution` được tính cho lần kế tiếp; do đó minh họa chính xác phần dynamic scheduling mà không cần hot-reschedule từ REST.

### 7.1. src/main/java/demo/AdaptiveTriggerDemo.java

```java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class AdaptiveTriggerDemo {
    public static class State {
        final AtomicInteger runs = new AtomicInteger();
        final AtomicReference<Duration> gap =
                new AtomicReference<>(Duration.ofMillis(10));
        final CountDownLatch finished = new CountDownLatch(1);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class AppConfig {
        @Bean
        public State state() {
            return new State();
        }

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler scheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("adaptive-");
            return scheduler;
        }

        @Bean
        public SchedulingConfigurer configure(
                ThreadPoolTaskScheduler scheduler, State state) {

            Trigger trigger = context -> {
                if (state.runs.get() >= 3) {
                    return null; // Không còn lần chạy mới.
                }

                Instant lastDone = context.lastCompletion();
                if (lastDone == null) {
                    return context.getClock().instant();
                }
                return lastDone.plus(state.gap.get());
            };

            return registrar -> {
                registrar.setTaskScheduler(scheduler);
                registrar.addTriggerTask(() -> {
                    int n = state.runs.incrementAndGet();

                    if (n == 1) {
                        state.gap.set(Duration.ofMillis(20));
                    } else if (n == 2) {
                        state.gap.set(Duration.ofMillis(60));
                    } else if (n == 3) {
                        state.finished.countDown();
                    }
                }, trigger);
            };
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             AppConfig.class)) {
            State state = context.getBean(State.class);

            if (!state.finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("job did not finish");
            }

            System.out.println("runs=" + state.runs.get());
            System.out.println("last-gap-ms=" +
                    state.gap.get().toMillis());
        }
    }
}
```

### 7.2. Chạy và output

Dùng cùng `pom.xml`:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.AdaptiveTriggerDemo
```

Output theo logic đã điều phối:

```text
runs=3
last-gap-ms=60
```

Điều quan trọng:

- Lần đầu `lastCompletion() == null` nên trigger trả `Clock.instant()`, kích hoạt gần như ngay.
- Sau lần chạy thứ nhất, task thay `gap` từ 10 ms thành 20 ms.
- Lần kích hoạt tiếp theo được tính từ **completion** của run thứ nhất cộng 20 ms.
- Sau run thứ hai, gap chuyển 60 ms; lần tiếp theo tính từ completion thứ hai.
- Khi run thứ ba kết thúc, `runs >= 3` khiến lần `nextExecution` sau trả `null`, dừng chuỗi.
- Main chỉ xác minh **số run và giá trị gap cuối**, không assert chênh lệch thời gian tuyệt đối trên máy CI.
- `AtomicReference<Duration>` và `AtomicInteger` bảo đảm visibility cho những lần đọc/ghi từ scheduler và main.

Ví dụ này không cho bạn **global mutable cron configuration** trên nhiều pod. State nằm trong một Spring context; khi restart, bộ đếm và delay trở lại giá trị mặc định.

### 7.3. Ngữ nghĩa completion-based trigger

Bằng việc dùng `lastCompletion()`, thời điểm lần tiếp theo phụ thuộc vào lúc **`Runnable` kết thúc**, không phải mốc đến hạn ban đầu. Nếu `Runnable` chỉ submit một `@Async` method rồi trả về ngay, `lastCompletion()` của scheduler **không phải** completion của business worker. Với completion-aware business delay, phải dùng future đúng nghĩa hoặc cơ chế tự schedule lại sau khi business future hoàn tất.

## 8. TriggerContext: ba mốc thời gian không được nhầm

Trong Spring Framework 6.x, `TriggerContext` cung cấp:

```java
Clock getClock();
Instant lastScheduledExecution();
Instant lastActualExecution();
Instant lastCompletion();
```

Đây là danh sách chữ ký **fragment**; cần import `java.time.Clock` và `Instant`, không phải class riêng có `main`.

| Phương thức | Ý nghĩa | Dùng khi |
| --- | --- | --- |
| `getClock()` | Clock do scheduler/context cung cấp | Tính mốc hiện tại nhất quán và thuận tiện test |
| `lastScheduledExecution()` | Thời điểm lịch trước **được dự kiến** kích hoạt | Fixed-rate-like policy và đánh giá trễ |
| `lastActualExecution()` | Thời điểm tác vụ trước **thực sự bắt đầu** | Đo scheduler lag/actual spacing |
| `lastCompletion()` | Thời điểm tác vụ trước **hoàn tất** theo scheduler | Completion-aware delay hoặc adaptive backoff |

Trước lần kích hoạt đầu, các giá trị last* có thể là `null`. Trigger phải xử lý trường hợp này; đừng gọi `lastCompletion().plus(...)` ngay từ lần đầu mà không check.

### 8.1. Lấy Clock từ TriggerContext

Ví dụ trigger:

```java
Trigger trigger = context -> {
    Instant done = context.lastCompletion();
    Duration gap = Duration.ofSeconds(10);
    return done == null
            ? context.getClock().instant().plus(gap)
            : done.plus(gap);
};
```

Đây là **fragment**. `Clock` của context có thể được cấu hình ở `TaskScheduler`, giúp kiểm thử time logic dễ hơn so với rải `Instant.now()` trong nhiều lớp. Nhưng tác vụ vẫn chịu ảnh hưởng OS/GC scheduling khi chạy thật.

### 8.2. Last completion không bằng DB commit

Nếu callback chỉ submit một task hoặc gửi HTTP request async rồi trả về, Spring đánh dấu callback invocation đã xong trước khi tác dụng phụ được xác nhận. `lastCompletion()` chỉ phản ánh completion theo **scheduler callback**; nó không tự đợi những `CompletableFuture` bị bỏ đi.

### 8.3. Trigger null để dừng lịch

Trả `null` từ `nextExecution` nghĩa là không có mốc tiếp theo. Nó hữu ích cho job tạm hoặc bài test chạy một số lần hữu hạn. **Đừng** trả `null` để “tạm pause rồi hy vọng config mới sẽ tự đánh thức” nếu không có code khác chủ động reschedule; thường lịch đã dừng và sẽ không tự xuất hiện thêm tick.

## 9. CronTrigger và cấu hình cron động

Khi chỉ cần cron expression, có thể dùng:

```java
registrar.addCronTask(
        reportWorker::refresh,
        "0 */5 * * * *");
```

Đây là fragment: Spring cron thông thường gồm **sáu trường**: giây, phút, giờ, ngày trong tháng, tháng, thứ trong tuần. Mẫu trên biểu diễn mỗi 5 phút tại giây 0. Việc parse cron có thể ném lỗi nếu expression không hợp lệ, nên validate khi nhận cấu hình thay vì đợi production gặp lỗi.

Nếu cần kiểm soát cách tính lịch hoặc zone, tạo `CronTrigger`:

```java
import java.time.ZoneId;
import org.springframework.scheduling.support.CronTrigger;

CronTrigger cron = new CronTrigger(
        "0 30 8 * * MON-FRI",
        ZoneId.of("Asia/Ho_Chi_Minh"));

registrar.addTriggerTask(
        reportWorker::refresh,
        cron);
```

Đây là **fragment**; cần biến `registrar`/`reportWorker`. Cron ở đây chạy vào 08:30 mỗi ngày thứ Hai đến thứ Sáu theo múi giờ Hồ Chí Minh.

### 9.1. Đổi cron tại runtime: hai chiến lược

**Chiến lược A — trigger đọc cấu hình mỗi lần tính mốc tiếp theo.**

```java
AtomicReference<CronTrigger> current =
        new AtomicReference<>(
                new CronTrigger("0 */5 * * * *"));

Trigger dynamicCron =
        context -> current.get().nextExecution(context);
```

Đây là **fragment**; ngoài imports, còn cần quyền cập nhật `current` và đăng ký `dynamicCron` với scheduler. Khi config thay đổi, bạn có thể thay `CronTrigger` trong `AtomicReference` sau khi validate expression.

**Giới hạn cực quan trọng:** scheduler có thể đã tính **và xếp lịch** lần fire tiếp theo theo cron cũ. Việc đổi `AtomicReference` **không nhất thiết hủy/đặt lại mốc đã pending ngay lập tức**; giá trị mới thường chỉ được dùng khi `nextExecution` được gọi lại. Nếu yêu cầu “từ giây này lập tức chuyển cron” thì cách A không đủ.

**Chiến lược B — cancel và đăng ký lại một handle.** Tạo schedule manager sở hữu `ScheduledFuture<?>`, dùng lock/CAS/generation để hủy handle cũ, tạo trigger mới và lưu handle mới. Cần xử lý cuộc đua giữa cancellation và một callback đang chạy; cancel không đảo ngược side effect đã commit.

### 9.2. Không dùng một cron string mutable không đồng bộ

Các request admin có thể đến đồng thời. Nếu lưu cron trong `String` field không có visibility/lock, thread scheduler có thể thấy giá trị cũ. Dùng immutable config object trong `AtomicReference`, cấu hình bền vững có version hoặc synchronization rõ ràng.

### 9.3. Giá trị cron disabled

Spring có sentinel `"-"` (ví dụ `ScheduledTaskRegistrar.CRON_DISABLED` và `@Scheduled` cron disabled semantics) để biểu diễn lịch bị vô hiệu hóa ở những API có hỗ trợ. Không truyền chuỗi `"-"` trực tiếp vào `new CronTrigger("-")` rồi kỳ vọng constructor chấp nhận; hãy xử lý disabled **trước khi tạo Trigger** hoặc dùng chức năng hỗ trợ sentinel phù hợp. Khi cần runtime disable, cancel handle và lưu trạng thái disabled trong cấu hình bền vững.

### 9.4. Timezone, DST và cron semantics

- Cron có ý nghĩa theo zone; hai pod chạy default timezone khác nhau có thể tính mốc khác.
- Múi giờ có DST có thể làm một số local-time occurrence không tồn tại hoặc lặp lại.
- Clock drift, JVM pauses và downtime có thể làm task chạy trễ hoặc bỏ mất mốc.
- `CronTrigger` có những chính sách lịch cố định/linh hoạt tùy version/API; không tự giả định mọi missed execution được catch up.
- Nếu “đúng mỗi mốc lịch trên toàn cluster” là bắt buộc, cần nguồn schedule state/coordination đáng tin hơn một scheduler in-memory ở từng JVM.

## 10. Thay đổi lịch khi ứng dụng đang chạy: quản lý handle rõ ràng

`SchedulingConfigurer` thường là **hook khởi tạo**. Nếu admin thay cron từ mỗi 5 phút sang mỗi 30 giây tại runtime, bạn phải quyết định **mốc đã được đặt lịch** sẽ ra sao. Đừng chỉ cập nhật một property rồi tin registrar tự đọc lại annotation và mọi future đã tạo sẽ đổi theo.

Một **schedule manager** có thể quản lý:

- `jobKey`/`tenantId` hoặc key ổn định cho từng lịch;
- Cron/zone/interval hiện tại với version;
- `ScheduledFuture<?>` hoặc `ScheduledTask` handle;
- `TaskScheduler` bean và lifecycle owner;
- Lock/mutex để serialize `create/update/delete` cho cùng job key;
- Business state cho “đang chạy”, “đã submit”, “đã hoàn tất” và “đã bị hủy”;
- Idempotency/generation nếu một run cũ không được ghi đè dữ liệu mới.

### 10.1. Phác thảo API của manager

```java
// Fragment minh họa, không phải class tự chạy độc lập.
public final class ScheduleManager {
    private final TaskScheduler scheduler;
    private final Object mutex = new Object();
    private ScheduledFuture<?> current;

    public ScheduleManager(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void replace(Runnable job, Trigger trigger) {
        synchronized (mutex) {
            if (current != null) {
                current.cancel(false);
            }
            current = scheduler.schedule(job, trigger);
        }
    }

    public void stop() {
        synchronized (mutex) {
            if (current != null) {
                current.cancel(false);
                current = null;
            }
        }
    }
}
```

**Không xem fragment này là production-ready:** nó quản lý **một lịch trong một JVM**, chưa có xử lý schedule returning `null`, lỗi từ `schedule`, task đang chạy, race callback với admin, replacement atomicity, persistence/restart, multitenancy, audit và resource cleanup. `cancel(false)` thường ngăn các lần kích hoạt sau nhưng **không cưỡng ép ngừng tác vụ hiện đang chạy**.

Nếu việc tạo lịch mới thất bại sau khi đã cancel lịch cũ, manager có thể rơi vào trạng thái không có lịch; quyết định rollback/compensation và validate cron **trước** khi thực hiện thay thế.

### 10.2. Đừng lẫn lộn hủy lịch với hủy công việc

Một handle thường điều khiển **future trigger**. Nếu callback của lịch đã bàn giao việc sang `@Async` worker, cancel handle không tự hủy worker, DB transaction hoặc HTTP request đã gửi. Muốn ngăn kết quả cũ áp dụng, cần business generation/fencing và kiểm tra ở commit boundary.

### 10.3. Nhiều replica

Nếu cả ba pods đều khởi tạo `SchedulingConfigurer`, mỗi pod có **registrar và scheduler của riêng nó**, do đó có thể phát cùng job ba lần. Không có một bộ đăng ký toàn cụm chỉ nhờ interface này. Chọn leader election, distributed lease với fencing token, Quartz với job store cấu hình đúng hoặc durable scheduler/queue, và đặt idempotency ở nơi thực hiện side effect.

## 11. Fixed rate, fixed delay, Cron và Trigger: chọn chính xác theo ý nghĩa nghiệp vụ

### 11.1. Fixed rate

`registrar.addFixedRateTask(task, Duration.ofSeconds(10))` biểu diễn nhịp kích hoạt định kỳ. Nó không tự nói một bản chạy mới được phép ghi cùng dữ liệu với bản trước, và không bảo đảm chính xác từng thời điểm dưới tải.

### 11.2. Fixed delay

`registrar.addFixedDelayTask(task, Duration.ofSeconds(10))` tính khoảng nghỉ sau khi **Runnable mà scheduler theo dõi kết thúc**. Nếu Runnable chỉ gọi `@Async` rồi return, lần kế tiếp có thể đến **trước khi business worker cũ thực sự xong**.

Với `SimpleAsyncTaskScheduler`, các fixed-delay tasks thực thi trên scheduler thread thay vì handoff theo thread-per-task như các tác vụ cron/fixed-rate thích hợp. Nếu chúng blocking lâu, có thể giữ thread kích hoạt lịch.

### 11.3. Cron

Cron phù hợp mốc giờ lịch. Với một `CronTrigger` thông thường, nhiều lần chạy và thời điểm hoàn tất cần được hiểu theo chính sách cron của version Spring. Kết quả thực thi có thể bị lệch do timezone/DST, downtime và scheduler load.

### 11.4. Custom Trigger

Custom trigger phù hợp khi mốc kế tiếp phụ thuộc vào completion, external configuration, backoff, lịch bảo trì hoặc business calendar. Nhưng `nextExecution` phải:

- Tính nhanh và thread-safe;
- Không chặn database/network lâu;
- Xử lý `null` lần đầu;
- Có khoảng nghỉ tối thiểu hoặc giới hạn số lần fire khi cần;
- Không tạo vòng lặp fire liên tục khi callback nhanh;
- Dùng `Clock`/`Instant` đúng nghĩa;
- Không đưa một mutable entity/session ngoài transaction vào trigger state.

### 11.5. Bảng quyết định

| Yêu cầu | API gợi ý | Rủi ro phải chặn |
| --- | --- | --- |
| Mỗi 10 giây, không quan tâm completion | Fixed rate | Overlap/queue nếu worker async |
| Chờ 10 giây sau callback kết thúc | Fixed delay | Callback chỉ handoff không bằng business completion |
| 08:30 sáng thứ Hai–Sáu | CronTrigger + ZoneId | DST/zone, downtime |
| Delay thay theo lần chạy trước | TriggerContext.lastCompletion + Duration | Trigger null, invalid/zero delay, state visibility |
| One-off 5 giây sau startup | addOneTimeTask(Duration) | Không bền vững qua restart |
| Thay cron tức thì lúc admin cập nhật | Manager cancel+reschedule | Race, rollback khi invalid, version |
| Cần job status/retry qua nhiều replica | Durable job store/orchestrator | Idempotency/fencing/ack |

## 12. Tách @Scheduled/@Async khỏi registration khi cần

`SchedulingConfigurer` kiểm soát scheduling; `@Async` kiểm soát việc gọi method qua executor khác. Hai interface/hạ tầng độc lập:

```java
// Fragment Spring trong một bean chủ lịch.
@Component
class ScheduledBridge {
    private final AsyncWorker worker;

    ScheduledBridge(AsyncWorker worker) {
        this.worker = worker;
    }

    public void startBusinessRun() {
        worker.performAsync(); // Spring proxy gọi sang executor.
    }
}

@Service
class AsyncWorker {
    @Async("workExecutor")
    public CompletableFuture<Void> performAsync() {
        doWork();
        return CompletableFuture.completedFuture(null);
    }
}
```

Bạn có thể đăng ký `ScheduledBridge::startBusinessRun` programmatically bằng registrar. **Nhưng:** invocation theo lịch hoàn thành khi bridge trả về sau submit; `TriggerContext.lastCompletion()` không đợi future của `performAsync()`, nên fixed-delay/backoff sẽ tính từ completion của bridge, không phải work thật.

Nếu muốn delay chỉ sau khi async future hoàn tất, hoặc giữ callback sync/đợi future có timeout phù hợp, hoặc **tự schedule lần kế tiếp trong `whenComplete`** của future thật. Đừng dùng `Thread.sleep(...)` để “giúp scheduler chờ”.

### 12.1. Một ví dụ constructor injection hợp lý

Trong Spring:

```java
@Bean
SchedulingConfigurer registration(
        TaskScheduler scheduler,
        ScheduledBridge bridge) {
    return registrar -> {
        registrar.setTaskScheduler(scheduler);
        registrar.addFixedRateTask(
                bridge::startBusinessRun,
                Duration.ofSeconds(30));
    };
}
```

Đây là **fragment** thuộc một `@Configuration` có `@EnableScheduling`. `bridge` phải là Spring bean thật, không tự `new ScheduledBridge()` tại nơi đăng ký nếu service cần DI/AOP.

### 12.2. Không đăng ký cùng một method hai lần

Nếu `ScheduledBridge.startBusinessRun()` đồng thời có `@Scheduled` và được registrar đăng ký, hai lịch có thể hoạt động. Một nguồn registration duy nhất giúp debugging và idempotency dễ hơn.

## 13. Error handling, cancellation và failure semantics

### 13.1. Lỗi xảy ra ở đâu?

- **Khi parse/đăng ký:** cron sai, Duration không hợp lệ, bean scheduler không tồn tại.
- **Khi tính nextExecution:** custom Trigger ném exception hoặc return mốc không phù hợp.
- **Khi dispatch:** scheduler/target executor shutdown hoặc bị quá tải.
- **Trong business Runnable:** DB, HTTP, serialization hoặc logic ném exception.
- **Sau handoff:** callback async worker thất bại về sau, registrar không mặc nhiên quan sát future bị bỏ.

`ThreadPoolTaskScheduler` cho phép cấu hình `ErrorHandler`. Với periodic tasks, error strategy ảnh hưởng tới việc lần sau còn được lên lịch hay không; đừng dựa vào default mà không kiểm chứng.

### 13.2. Không che lỗi bằng một Runnable rỗng

**Phản mẫu:**

```java
registrar.addTriggerTask(() -> {
    try {
        worker.run();
    } catch (Exception ignored) {
    }
}, trigger);
```

Nó che mất failure, khiến metrics và người dùng tin công việc thành công. Cách tốt hơn là log theo `jobId/runId`, đo failure, áp dụng bounded retry/backoff và quyết định có tiếp tục schedule hay dừng.

### 13.3. Cancellation handles

- `ScheduledTask.cancel()`: hủy task do registrar quản lý; biến thể `cancel(boolean mayInterruptIfRunning)` cho phép chọn yêu cầu interrupt.
- `ScheduledFuture.cancel(...)`: hủy scheduled future theo hợp đồng implementation.
- Hủy lịch **không** rollback side effects đã commit.
- Với source `CompletableFuture`, `cancel(true)` không cưỡng chế interrupt underlying computation trong implementation chuẩn, nên không dùng nó làm business cancellation contract.
- Với task do executor ngoài nhận, phải quản lý cả executor job handle và trạng thái nghiệp vụ.

## 14. Quan sát các scheduled tasks

Spring Framework 6.1+ hỗ trợ `ScheduledTaskRegistrar.setObservationRegistry(...)` để gắn Micrometer observations. Trong một project có Micrometer Observation dependency và `ObservationRegistry` bean, có thể:

```java
@Bean
SchedulingConfigurer observabilityConfigurer(
        TaskScheduler scheduler,
        io.micrometer.observation.ObservationRegistry registry) {
    return registrar -> {
        registrar.setTaskScheduler(scheduler);
        registrar.setObservationRegistry(registry);
    };
}
```

Đây là **fragment**, không tự chạy chỉ bằng `javac`. Nếu ứng dụng đã có một `SchedulingConfigurer` khác đăng ký tasks, cần phối hợp cấu hình trong **một chỗ có ownership rõ** thay vì dựa vào thứ tự giữa nhiều configurers. Với `@Scheduled` tasks, observations thường cần cấu hình registry trên registrar để được ghi; có dependency Micrometer không tự bảo đảm mọi scheduler được instrument.

### 14.1. Những thứ nên đo

| Metric / log event | Ý nghĩa |
| --- | --- |
| scheduled_due_time | Mốc dự kiến |
| trigger_actual_time | Khi callback bắt đầu |
| trigger_lag_ms | Trễ so với due time |
| next_execution | Mốc sắp chạy nếu có |
| runs_started_total | Tổng invocation bắt đầu |
| runs_succeeded_total | Số lần hoàn tất bình thường |
| runs_failed_total | Số lần lỗi |
| executor_rejections_total | Task bị từ chối |
| task_duration_ms | Thời gian callback thật |
| async_inflight | Số business workers vẫn đang chạy |
| reschedules_total | Số lần đổi lịch |
| disabled_schedules | Lịch bị tắt theo policy |
| duplicate_suppressed | Side effects trùng bị chặn |

Tránh metrics labels là `jobId` hoặc `runId` duy nhất (high cardinality). Lưu ID trong structured logs/traces, metrics dùng category/job type/outcome giới hạn.

### 14.2. Runtime inspection

`ScheduledTaskRegistrar.getScheduledTasks()` và `ScheduledAnnotationBeanPostProcessor.getScheduledTasks()` cung cấp danh sách task handles theo phạm vi quản lý. `ScheduledTask.nextExecution()` (Spring 6.2+) có thể trả thời điểm sắp tới hoặc `null` nếu không còn lịch; tùy task/trigger, không nên coi đó là durable source of truth. Dùng các API đó cho diagnostics/admin có kiểm soát, không tự ý sửa/xóa scheduled tasks của Spring ở thread khác.

## 15. Thread pool và khả năng chạy đồng thời

### 15.1. Pool size không phải số task toàn cụm

`ThreadPoolTaskScheduler#setPoolSize(4)` tạo một scheduled thread pool có tối đa nhiều scheduler workers theo implementation, **không** có nghĩa Spring chỉ cho tối đa 4 jobs trên toàn Kubernetes cluster. Nếu có 3 pods, mỗi pod có pool riêng.

### 15.2. Một callback có thể block cả scheduler worker

Nếu `Runnable` chạy 60 giây trong `ThreadPoolTaskScheduler` pool size 1, các lịch khác có thể bị trễ. Tách scheduling với execution, điều chỉnh pool và dùng async worker có thể phù hợp, nhưng phải giám sát completion/overlap/backpressure độc lập.

### 15.3. Fixed rate không có nghĩa có thể chạy song song vô hạn

Với `ScheduledThreadPoolExecutor`, các executions liên tiếp của **cùng một periodic task** có hợp đồng không chồng lấn theo Java executor semantics. Nhưng nếu callback mỗi tick chỉ submit `@Async` work rồi trả về, những **business jobs được submit** có thể overlap hoặc queue. Ngoài ra, nhiều tasks/registrations khác nhau có thể chạy đồng thời.

### 15.4. Virtual threads

Trên Java 21+, `SimpleAsyncTaskScheduler` có thể tạo virtual threads cho các task phù hợp, nhưng không làm DB connections hay remote rate limits vô hạn. `fixedDelay` trên scheduler này có ngoại lệ chạy trên scheduler thread, nên đặc biệt cần kiểm tra nếu callback blocking. Việc chọn scheduler ảnh hưởng semantics, không chỉ tối ưu chi phí thread.

## 16. Transaction, idempotency và multi-replica

Lập lịch là một lần **yêu cầu thực thi**, không phải cam kết business transaction đã thành công.

Ví dụ:

```java
@Transactional
public void processBatch() {
    repository.updateBatch();
    publisher.publishEvent(new BatchChangedEvent());
}
```

Nếu job submit một `@Async` side effect trước commit, worker có thể chạy trước khi DB commit hoặc sau rollback. Với event chỉ được phát khi dữ liệu đã commit, dùng `@TransactionalEventListener(AFTER_COMMIT)` hoặc ghi transactional outbox trong cùng transaction.

**Đừng giữ managed JPA entity trong một Trigger hoặc schedule manager state.** Giữ ID/immutable DTO, worker mở transaction/persistence context phù hợp. Một scheduled task có thể chạy qua giờ làm việc hoặc tới lúc deploy mới; mọi state có thể cũ.

### 16.1. Khi nhiều replica cùng kích hoạt

Nếu ba Spring instances dùng cùng registrar config, thông thường cả ba tạo **cùng lịch cục bộ**, trừ khi application có cơ chế leader/lease. Cần:

- Distributed scheduling/lease khi chỉ một owner được chạy;
- Fencing token nếu owner cũ có thể vẫn tới DB sau lease expiry;
- Idempotency key nếu cùng command được retry;
- Conditional update/optimistic locking để chống stale results;
- Durable queue/outbox nếu công việc không được mất qua restart;
- Retry/backoff có giới hạn, không tạo bão job.

`SchedulingConfigurer` không cung cấp cluster membership hay election.

### 16.2. Job đã chạy không tự bị đảo ngược khi đổi lịch

Admin đổi cron 5 phút thành 1 giờ **không** hủy email đang gửi hoặc hoàn tác DB update đã commit. Hot-reschedule nên có state rõ: “không kích hoạt lần tiếp theo” khác với “hủy business operation đang chạy”.

## 17. Kiểm thử SchedulingConfigurer và registrar

### 17.1. Unit test callback

Với một `SchedulingConfigurer`, có thể tạo `ScheduledTaskRegistrar` test-only và gọi `configureTasks(registrar)` để kiểm tra:

- `getScheduler()` trả đúng bean đã được chỉ định;
- `getTriggerTaskList()`, `getCronTaskList()`, `getFixedRateTaskList()`, `getFixedDelayTaskList()` có số lượng/definitions mong muốn;
- `hasTasks()` phản ánh việc đăng ký;
- Trigger tính được nextExecution khi `TriggerContext` có lịch sử phù hợp.

**Không gọi `afterPropertiesSet()` ở unit test** chỉ để kiểm tra definitions nếu bạn không muốn task thật chạy. Trong integration test, giao lifecycle cho Spring context.

### 17.2. Integration test đúng một lần

`RegistrarBasicsDemo` đợi hai `CountDownLatch` rồi xác nhận hai independent tasks chạy đúng một lần. Nó không dùng `Thread.sleep` hoặc assert “phải chạy đúng sau 1 ms” trên máy CI.

### 17.3. Test dynamic delay

`AdaptiveTriggerDemo` thay `Duration` sau run 1 và run 2, đợi run 3 rồi dừng bằng `null`. Test xác nhận **thứ tự state và số lần chạy**, không cần scheduler time chính xác tuyệt đối.

### 17.4. Test reschedule admin

Nếu có schedule manager runtime, hãy test:

1. Cấu hình cron hợp lệ → giữ handle hiện tại.
2. Cấu hình cron không hợp lệ → từ chối **trước** khi phá lịch đang hoạt động.
3. Đổi lịch khi callback cũ đang chạy → không nhân đôi side effects hoặc trả status đúng policy.
4. Hai request admin đổi cùng job đồng thời → lock/version serialize mutation.
5. Cancel schedule → không phát tick mới sau khi cancellation có hiệu lực; xác định chính xác race với tick in-flight.
6. Shutdown → scheduler và target executors được đóng đúng.
7. Restart → lịch được nạp lại từ nguồn cấu hình bền vững, không chỉ in-memory fields.
8. Hai pods → cơ chế election/fencing/idempotency hoạt động đúng phạm vi.

### 17.5. Test exception và backpressure

- Trigger trả `null`: không có lần kế tiếp.
- Trigger ném exception: capture/monitor lỗi, không để lỗi làm hệ thống khó quan sát.
- Executor reject: không coi “scheduled” là “executed”.
- Business callback ném lỗi: log/metrics đúng và xác định lịch tiếp tục hay dừng.
- Worker async future lỗi: không bỏ future rồi coi registrar đã quan sát.
- Task chậm hơn interval: đo overlap, queue wait và trigger lag.

## 18. Troubleshooting

| Triệu chứng | Nguyên nhân có thể | Hướng kiểm tra |
| --- | --- | --- |
| `configureTasks` không chạy | Chưa bật `@EnableScheduling`, class không là bean, sai context | Xác minh bean `SchedulingConfigurer` trong context |
| Task không chạy | Trigger trả `null`, lịch quá xa, scheduler chưa initialized | Log `nextExecution`, verify registrar/scheduler |
| Chạy hai lần | Vừa `@Scheduled` vừa đăng ký programmatically, hoặc hai bean contexts | Kiểm tra toàn bộ owner đăng ký task |
| Đổi cron string nhưng tick cũ vẫn đến | Next execution đã được đặt trước khi đổi `AtomicReference` | Cancel+reschedule nếu cần hiệu lực ngay |
| Cron chạy lệch giờ | Zone/DST/default clock | Đặt zone rõ ràng, kiểm tra clock |
| Tick rất nhanh/tốn CPU | Trigger luôn trả thời điểm hiện tại | Thêm min delay/rate limit hoặc dừng bằng `null` |
| Fixed delay không chờ async job | Runnable chỉ submit `@Async` rồi return | Completion-aware scheduling hoặc guard |
| Scheduler bị nghẽn | Task blocking, pool nhỏ, SimpleAsyncTaskScheduler fixed-delay | Đo duration, đổi execution model |
| Task bị mất khi restart | In-memory scheduler không bền vững | Durable queue/job store |
| Cluster chạy trùng | Mỗi pod có registrar riêng | Leader election/fencing/idempotency |
| Exception không ai thấy | Error handler/future không được quan sát | Logging, Micrometer observation, job status |
| Tăng pool size không cải thiện | Downstream/DB bottleneck hoặc scheduler implementation khác | Inspect runtime bean, queue/connection metrics |
| Cancel xong vẫn có email | Task đã handoff và external effect đang chạy | Phân biệt schedule cancel với business cancel |

## 19. Lộ trình triển khai thực tế

Một quy trình ổn định cho ứng dụng có lịch động:

1. **Xác định requirement:** fixed calendar, completion-based interval hay adaptive retry?
2. **Quyết định nguồn cấu hình:** YAML khởi tạo, DB config, admin API hoặc external control plane.
3. **Chọn ownership:** Spring `@Scheduled` tĩnh, registrar startup, hay schedule manager runtime; không trộn trùng mà thiếu định danh.
4. **Chọn TaskScheduler:** pool-based, thread-per-task/virtual hoặc managed environment phù hợp.
5. **Định nghĩa Trigger:** first-run semantics, lastCompletion vs lastScheduled, timezone, DST, null stopping rule.
6. **Định nghĩa business completion:** callback sync, async future hoặc job state; không nhầm handoff với hoàn tất.
7. **Đặt quota:** pool, concurrency limit, admission control, downstream timeouts.
8. **Chống overlap:** skip, coalesce, queue, serialize, distributed lease/fencing.
9. **Bảo vệ dữ liệu:** idempotency key, transaction boundaries và optimistic locking.
10. **Quản lý lifecycle:** shutdown/resume, cancel handles, config reload, restart.
11. **Đo/alert:** trigger lag, execution duration, failure, missed tick, duplicate, rejection.
12. **Test nhiều trạng thái:** invalid cron, config update race, rollback, worker failure, shutdown và multi-pod.

### 19.1. Checklist trước khi production

- [ ] Tôi biết `SchedulingConfigurer` là hook cấu hình, không phải executor.
- [ ] Tôi biết `ScheduledTaskRegistrar` đăng ký task definitions với `TaskScheduler`.
- [ ] Tôi dùng `@EnableScheduling` trong context phù hợp.
- [ ] Tôi chọn được `addTriggerTask`, `addCronTask`, `addFixedRateTask`, `addFixedDelayTask`, `addOneTimeTask`.
- [ ] Tôi dùng `Duration` overload thay cho `long` deprecated khi có thể.
- [ ] Tôi biết `Trigger.nextExecution` trả `Instant` hoặc `null`.
- [ ] Tôi hiểu `TriggerContext` phân biệt scheduled, actual và completion timestamps.
- [ ] Tôi không nhầm lần đầu có `lastCompletion() == null` với lỗi.
- [ ] Tôi có min delay/bound cho adaptive trigger.
- [ ] Tôi không nhầm cập nhật AtomicReference với reschedule **ngay lập tức**.
- [ ] Tôi quản lý handle để cancel/re-register khi có hot reload.
- [ ] Tôi không tạo scheduler ngoài Spring context bằng cách tự `new` trong configuration sai cách.
- [ ] Tôi không đăng ký trùng `@Scheduled` method bằng registrar mà không chủ ý.
- [ ] Tôi biết scheduler thread và executor worker khác nhau về completion.
- [ ] Tôi có lifecycle shutdown/metrics/error handling rõ ràng.
- [ ] Tôi có lựa chọn durable job store nếu không được mất task khi JVM chết.
- [ ] Tôi kiểm tra cluster duplicate và idempotency nếu có nhiều replicas.

**Kết luận:** `SchedulingConfigurer` cho bạn quyền **cấu hình cách Spring đăng ký lịch**, còn `ScheduledTaskRegistrar` là **bộ quản lý registrations** và chuyển các task đó cho `TaskScheduler`. Chúng đặc biệt hữu ích khi cần `Trigger` tự tính lịch từ lịch sử thực thi hoặc cấu hình; tuy nhiên **runtime hot-reload**, business completion, cancellation, lỗi và distributed guarantees cần kiến trúc bổ sung. Mỗi lịch nên có một owner, một định nghĩa rõ về “đã hoàn tất” và một chính sách bảo vệ tác dụng phụ.

## 20. Tài liệu chính thức

- Spring Framework 6.2 — [@EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html): bật hạ tầng, lựa chọn scheduler và ví dụ `SchedulingConfigurer`.
- Spring Framework 6.2 — [SchedulingConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/SchedulingConfigurer.html): contract `configureTasks(ScheduledTaskRegistrar)`.
- Spring Framework 6.2 — [ScheduledTaskRegistrar Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/config/ScheduledTaskRegistrar.html): `addTriggerTask`, `addCronTask`, `addFixedRateTask`, `addFixedDelayTask`, `addOneTimeTask`, `getScheduledTasks`.
- Spring Framework 6.2 — [Trigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/Trigger.html): `nextExecution(TriggerContext)` và `null`.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): trigger, cron, task scheduler, annotations và cách dùng.
- Spring Framework — [Observability Support](https://docs.spring.io/spring-framework/reference/integration/observability.html): `setObservationRegistry` cho scheduled tasks.
- Spring Framework 6.2 — [ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): scheduler thread pool, error handler, clock, lifecycle.
- Java SE 17 — [Duration](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html), [AtomicReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): interval và state visibility.

Các ví dụ ghi đầy đủ `pom.xml`, file Java, lệnh Maven và output là **project Java 17+ Spring Framework**; những đoạn được ghi là **fragment**, **mẫu thiết kế** hoặc **phản mẫu** cần context, imports và domain code trước khi tích hợp. Đối chiếu API với đúng phiên bản Spring mà dự án đang sử dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-scheduling-006">Đăng ký, giữ ScheduledFuture và hủy lịch bằng mã</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký, giữ ScheduledFuture và hủy lịch bằng mã

## 1. Khi nào cần lập lịch động thay vì @Scheduled?

Spring Framework hỗ trợ `@Scheduled` cho những công việc có chu kỳ được khai báo trước. Tuy nhiên, không phải lịch nào cũng tồn tại cố định từ khi ứng dụng khởi động. Ví dụ: người dùng bật/tắt báo cáo định kỳ, quản trị viên thay giờ đồng bộ dữ liệu, một tenant yêu cầu tạm dừng polling, hoặc công việc chỉ có hiệu lực trong một khoảng thời gian.

Với các yêu cầu đó, nên cân nhắc **lập lịch bằng mã** thông qua `org.springframework.scheduling.TaskScheduler` và **giữ handle** mà API trả về:

```java
ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay(
        this::checkReports,
        Duration.ofMinutes(5)
);
```

Đây là **mảnh mã trong một Spring service**, không phải chương trình độc lập; `scheduler` là bean `TaskScheduler`, còn `checkReports()` do ứng dụng cung cấp.

`handle` đại diện cho **một lần đăng ký lịch** trong JVM. Khi muốn ngừng các lượt chạy kế tiếp của lịch đó:

```java
boolean accepted = handle.cancel(false);
```

Lệnh trên **không** dừng toàn bộ scheduler, không xóa bản ghi công việc khỏi database và **không chắc làm lượt đang chạy kết thúc ngay**. `cancel(false)` không yêu cầu interrupt worker đang chạy.

Một cơ chế lập lịch động hoàn chỉnh phải làm rõ:

1. **Đăng ký:** chọn kiểu lịch và gọi API `schedule*(...)` thích hợp.
2. **Giữ:** ánh xạ một mã công việc (`jobId`) đến đúng `ScheduledFuture<?>`.
3. **Chống trùng:** hai request cùng tạo một job không được vô tình tạo hai lịch.
4. **Đổi lịch:** xác định thứ tự tạo mới/hủy cũ và rủi ro overlap.
5. **Hủy:** chọn `cancel(false)`/`cancel(true)` có chủ đích.
6. **Quan sát:** tách trạng thái đăng ký lịch khỏi trạng thái từng lượt chạy.
7. **Vòng đời:** cleanup handle, shutdown và khôi phục sau restart khi cần.

**Nguyên tắc:** `ScheduledFuture<?>` là **handle cục bộ của một registration**, không phải một job ID bền vững, không phải distributed lock và không phải chứng nhận nghiệp vụ đã hoàn tất.

## 2. Quan hệ giữa job ID, Runnable, scheduler và Future

```text
Client / Admin API
       |
       | register("report-42", ...)
       v
 ScheduleRegistry (Spring singleton)
       |
       | Map<jobId, ScheduledFuture<?>>
       | "report-42" -> handle A
       | "poll-B"    -> handle B
       v
 TaskScheduler.schedule*(Runnable, ...)
       |
       +-- Trả ScheduledFuture<?> để quản lý
       |
       +-- Chờ mốc thời gian / Trigger
       |
       +-- Worker gọi Runnable từng lượt

Client / Admin API
       |
       | stop("report-42")
       v
 ScheduleRegistry -> handleA.cancel(false)
                     handleB không bị ảnh hưởng
```

| Khái niệm | Vai trò | Vòng đời |
|---|---|---|
| `jobId` | Định danh nghiệp vụ ổn định | Có thể được lưu trong database |
| `Runnable` | Logic của một lần chạy | Chạy khi scheduler kích hoạt |
| `TaskScheduler` | Điều phối thời điểm và worker | Spring quản lý như bean |
| `ScheduledFuture<?>` | Handle của registration cụ thể | Chỉ có ý nghĩa trong JVM đang chạy |
| `JobStatus` riêng | Trạng thái nghiệp vụ/số lần chạy/lỗi | Có thể lưu bền vững |

Một job logic có thể có nhiều `ScheduledFuture` **qua các lần thay lịch**. Khi thay chu kỳ từ 5 phút sang 15 phút, handle cũ bị hủy, handle mới được giữ dưới cùng `jobId`. Nếu bỏ handle cũ mà chưa hủy, lịch cũ có thể tiếp tục kích hoạt dù không ai còn tham chiếu đến nó.

## 3. Các phương thức TaskScheduler quan trọng

Trong Spring Framework 6.x, `TaskScheduler` cung cấp các kiểu lịch sau:

```java
// Chạy một lần tại một thời điểm cụ thể.
ScheduledFuture<?> schedule(Runnable task, Instant startTime);

// Chạy theo Trigger (có thể trả null).
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);

// Fixed rate, khởi chạy sớm nhất có thể.
ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Duration period);

// Fixed rate, chọn thời điểm bắt đầu.
ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period);

// Fixed delay, khởi chạy sớm nhất có thể.
ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Duration delay);

// Fixed delay, chọn thời điểm bắt đầu.
ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay);
```

Đây là **tóm lược chữ ký API**, chưa phải một Java source file độc lập. `Instant` và `Duration` là kiểu trong `java.time`; `ScheduledFuture` thuộc `java.util.concurrent`; `Trigger` thuộc `org.springframework.scheduling`.

| Nhu cầu | API | Ý nghĩa cần chú ý |
|---|---|---|
| Một lần sau 30 phút | `schedule(task, Instant.now().plus(...))` | Nếu thời điểm đã qua, có thể chạy ngay |
| Cố duy trì nhịp bắt đầu | `scheduleAtFixedRate(...)` | Lượt chạy chậm làm việc bắt đầu muộn |
| Nghỉ sau mỗi lượt hoàn tất | `scheduleWithFixedDelay(...)` | Thời gian chạy cộng vào khoảng start-to-start |
| Cron | `schedule(task, new CronTrigger(...))` | Cần biết timezone, DST, khả năng không có lượt kế tiếp |
| Điều kiện động | `schedule(task, Trigger)` | `nextExecution(...)` có thể trả `null` |

Trong code mới, ưu tiên overload `Instant`/`Duration` thay vì các overload `Date`/`long` cũ đã deprecated trong nhiều phiên bản Spring 6.

### 3.1. Fixed rate và fixed delay khác nhau

`scheduleAtFixedRate(task, Duration.ofSeconds(5))` nhắm tới một nhịp bắt đầu 5 giây. `scheduleWithFixedDelay(task, Duration.ofSeconds(5))` chờ 5 giây **sau khi lượt trước kết thúc** mới đủ điều kiện bắt đầu lượt kế tiếp.

Với `ScheduledThreadPoolExecutor` của Java được `ThreadPoolTaskScheduler` sử dụng, các lượt **của cùng một periodic registration** không tự chạy chồng nhau. Nhưng nhiều registrations hoặc nhiều JVM có thể thực thi đồng thời.

### 3.2. Một lưu ý về Trigger và null

`TaskScheduler.schedule(Runnable, Trigger)` **có thể trả `null`** nếu `Trigger.nextExecution(...)` không trả thời điểm nào. Ví dụ:

```java
Trigger never = context -> null;

ScheduledFuture<?> handle = scheduler.schedule(
        () -> System.out.println("Không có lịch"),
        never
);

if (handle == null) {
    System.out.println("Trigger không phát sinh lần chạy");
}
```

Đây là mảnh Java trong class có `scheduler`, cần import `Trigger` và `ScheduledFuture`. Nếu wrapper quản lý lịch không kiểm tra null, một lời gọi `handle.cancel(...)` sau đó sẽ gây `NullPointerException`. Production nên phân biệt `NO_NEXT_EXECUTION` với `DUPLICATE` hoặc `REJECTED` thay vì trả chung một boolean.

## 4. Tạo Spring TaskScheduler bean có vòng đời rõ ràng

Ví dụ cấu hình:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfiguration {

    @Bean("dynamicScheduler")
    public ThreadPoolTaskScheduler dynamicScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("dynamic-");
        scheduler.setRemoveOnCancelPolicy(true);

        return scheduler;
    }
}
```

Đây là **Spring configuration**, không có `main()` độc lập. `ThreadPoolTaskScheduler` là bean do Spring quản lý; `ApplicationContext` khởi tạo và hủy bean theo lifecycle.

**Không cần `@EnableScheduling` nếu ứng dụng chỉ tự gọi `TaskScheduler.schedule*(...)` bằng mã.** `@EnableScheduling` chủ yếu kích hoạt phương thức gắn `@Scheduled`. Có thể dùng đồng thời hai cơ chế nhưng phải phân biệt ai đăng ký, giữ và hủy từng lịch.

`setRemoveOnCancelPolicy(true)` giúp underlying `ScheduledThreadPoolExecutor` loại bỏ những tác vụ đã hủy khỏi delayed queue kịp thời khi có thể. Nó:

- **Không** tương đương giới hạn kích thước queue.
- **Không** tự gọi `cancel(...)` thay cho ứng dụng.
- **Không** kết thúc cưỡng bức một lượt đang chạy.
- **Không** tự dọn map `jobId -> ScheduledFuture` trong service.

Pool 2 thread có thể chạy hai **lịch độc lập** cùng lúc; một registration định kỳ không tự được nhân thành hai lượt song song.

## 5. Mẫu đơn giản: giữ handle trong một Spring service

Khi chỉ có **một lịch**:

```java
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ReportPoller {

    private final TaskScheduler scheduler;
    private ScheduledFuture<?> handle;

    public ReportPoller(
            @Qualifier("dynamicScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public synchronized boolean start() {
        if (handle != null && !handle.isDone()) {
            return false;
        }

        handle = scheduler.scheduleWithFixedDelay(
                this::poll, Duration.ofSeconds(30));
        return true;
    }

    public synchronized boolean stop() {
        ScheduledFuture<?> old = handle;
        handle = null;
        return old != null && old.cancel(false);
    }

    private void poll() {
        System.out.println("Đang lấy trạng thái báo cáo");
    }
}
```

Đây là **Spring component**, không phải chương trình Java SE độc lập. `synchronized` bảo vệ hai thao tác quản lý `handle` khỏi nhiều caller đồng thời trên cùng một instance.

Giới hạn cần biết:

- `stop()` có thể trả trước khi một lượt đang chạy hoàn tất.
- `start()` sau `stop()` có thể tạo lịch mới trong lúc lượt cũ chưa thoát.
- `isDone()` kiểm tra kết thúc **Future của cả registration**, không phản ánh lượt định kỳ gần nhất thành công hay thất bại.
- Một biến `handle` không đủ khi mỗi tenant hoặc mỗi báo cáo có một lịch riêng.
- Nếu một ScheduledFuture bị lỗi/hoàn tất nhưng vẫn nằm trong biến, `start()` có thể ghi đè sau khi `isDone()` thành true; cần theo dõi lý do kết thúc nếu ứng dụng có nghiệp vụ quan trọng.

## 6. Nhiều job: registry theo mã công việc

Một registry có thể ánh xạ:

```text
"report-42" -> ScheduledFuture-A
"tenant-7"  -> ScheduledFuture-B
"sync-3"    -> ScheduledFuture-C
```

Mẫu rút gọn trong Spring service:

```java
private final Map<String, ScheduledFuture<?>> handles =
        new HashMap<>();

public synchronized boolean register(
        String id, Runnable task, Duration delay) {

    if (handles.containsKey(id)) {
        return false;
    }

    ScheduledFuture<?> next =
            scheduler.scheduleWithFixedDelay(task, delay);

    handles.put(id, next);
    return true;
}

public synchronized boolean stop(String id) {
    ScheduledFuture<?> old = handles.remove(id);
    return old != null && old.cancel(false);
}
```

Đây là **mảnh code**, cần một `scheduler`, imports `Map`, `HashMap`, `ScheduledFuture` và `Duration`. Bản có validation, one-shot/replace và ví dụ chạy được nằm ở mục 10.

### 6.1. Vì sao ConcurrentHashMap chưa giải quyết mọi race?

`ConcurrentHashMap` làm các lệnh riêng như `get`, `put` thread-safe, nhưng chuỗi **check → schedule → put** vẫn có thể chạy xen kẽ:

```text
Request A: containsKey(id) == false
Request B: containsKey(id) == false
Request A: scheduler.schedule(...) -> handle A
Request B: scheduler.schedule(...) -> handle B
Request A: put(id, A)
Request B: put(id, B)
```

Map cuối chỉ giữ B, nhưng **A vẫn được scheduler thực thi**. Đây là một *orphan registration*. Dùng `synchronized`, lock per job hoặc state machine atomic. Nếu dùng `putIfAbsent` sau khi đã tạo handle, phải **cancel handle thua cuộc** ngay, đồng thời tính đến khả năng task đã bắt đầu trước lúc bị cancel.

### 6.2. Registry quản lý registration, không làm công việc nghiệp vụ thay worker

Đừng thực hiện I/O dài, gọi `Future.get()` vô hạn hoặc giữ khóa map trong lúc chạy `Runnable`. Một task có thể quay lại gọi API registry; thiết kế lock order và lifecycle để tránh deadlock. Với tải lớn, có thể dùng cấu trúc theo job ID hoặc command/event loop, nhưng phải bảo đảm **không bao giờ có hai registrations mồ côi do race**.

## 7. cancel(false) và cancel(true) không giống nhau

`ScheduledFuture<?>` kế thừa `Future<?>` nên có các API:

```java
boolean accepted = handle.cancel(false);
boolean cancelled = handle.isCancelled();
boolean terminal = handle.isDone();
long remaining = handle.getDelay(TimeUnit.MILLISECONDS);
```

| API | Thông tin thực tế | Không có nghĩa |
|---|---|---|
| `cancel(false)` | Yêu cầu hủy, không xin interrupt lượt đang chạy | Worker đã dừng ngay |
| `cancel(true)` | Yêu cầu hủy, có thể cố gắng interrupt | Bảo đảm kill thread/rollback |
| `isCancelled()` | Future bị hủy | Side effects được đảo ngược |
| `isDone()` | Future ở trạng thái terminal | Lượt gần nhất thành công |
| `getDelay(...)` | Độ trễ ước tính tới hạn | Timestamp tuyệt đối đáng tin cho UI |

**Sơ đồ:**

```text
10:00:00 Worker bắt đầu tải dữ liệu
10:00:01 API gọi future.cancel(false)
10:00:01 Future có thể isCancelled() == true
10:00:10 Worker mới thực sự trả về
          Không có lượt định kỳ kế tiếp
```

Hủy một registration không tự hủy một HTTP request bên ngoài hoặc một transaction đã commit. Nếu API cần biết **đã dừng hoàn toàn**, phải có tín hiệu riêng từ active run.

### 7.1. Interruption là hợp tác

`cancel(true)` có thể gọi interrupt vào thread đang xử lý. Java không có khái niệm “kill an toàn một arbitrary task” chỉ bằng Future. Một đoạn code có thể kiểm tra:

```java
Runnable cancellable = () -> {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            processSmallBatch();
        }
    } finally {
        closeResourcesOwnedByThisRun();
    }
};
```

`processSmallBatch` và `closeResourcesOwnedByThisRun` là logic giả định. Nếu code có `Thread.sleep`, `CountDownLatch.await` hoặc blocking API ném `InterruptedException`, cần xử lý và thường khôi phục cờ interrupt rồi thoát đúng cách. Một thao tác external side effect hoàn thành trước khi interrupt không tự rollback.

### 7.2. Boolean của cancel cần được diễn giải đúng

`cancel(...)` có thể trả false vì Future đã kết thúc, đã bị hủy hoặc không thể hủy theo implementation. Trong service registry, stop có thể trả false khi không còn entry. Khi xây REST API, nên phân biệt `NOT_FOUND`, `CANCEL_ACCEPTED`, `ALREADY_TERMINAL` và trạng thái “còn lượt đang chạy” nếu ứng dụng cần biết.

Không trả “đã hoàn tất nghiệp vụ” chỉ vì `cancel(false)` trả true.

## 8. Thay lịch động: cancel-first hay schedule-first?

Khi muốn đổi job từ 5 phút sang 15 phút, có hai thứ tự:

**A — Hủy trước rồi đăng ký mới:**

```text
old.cancel(false)
new = scheduler.schedule*(...)
handles[id] = new
```

Hạn chế có hai registrations active cùng lúc, nhưng nếu schedule mới bị từ chối, job **mất lịch cũ**. Và `cancel(false)` vẫn cho phép một lượt cũ đang chạy tiếp.

**B — Đăng ký mới trước rồi hủy cũ:**

```text
new = scheduler.schedule*(...)
old = handles.put(id, new)
old.cancel(false)
```

Giữ lịch cũ nếu schedule mới thất bại **trước khi tạo handle**, nhưng tồn tại một khoảng cả hai registrations có thể chạy. Với lịch mới bắt đầu ngay, overlap có thể xảy ra.

**Không có “transaction atomically replace” chung trên `TaskScheduler`**. Hãy chọn một trong các chính sách theo nghiệp vụ, bổ sung trạng thái `REPLACING`/`STOPPING` và chờ active run nếu cần tuyệt đối không overlap.

Với các job có side effect, dùng idempotency key, generation token hoặc fencing token. Trong cluster, một `synchronized` trên registry của một pod không bảo vệ việc chạy cùng job ở pod khác.

## 9. CronTrigger và custom Trigger

Lịch cron bằng mã:

```java
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.support.CronTrigger;

ScheduledFuture<?> cron = scheduler.schedule(
        this::refreshIndexes,
        new CronTrigger("0 */15 * * * *")
);
```

Đây là mảnh code trong service có `scheduler` và `refreshIndexes()`. Spring cron thường có 6 trường, biểu thức trên kích hoạt vào các phút 0,15,30,45. Khi cần theo múi giờ kinh doanh, cấu hình timezone/clock phù hợp và kiểm tra DST.

**Nhớ xử lý `null`** từ `schedule(Runnable, Trigger)` nếu trigger không cung cấp thời điểm nào:

```java
ScheduledFuture<?> candidate =
        scheduler.schedule(task, trigger);

if (candidate == null) {
    return; // Không đăng ký handle trống.
}

handles.put(jobId, candidate);
```

Mảnh code này giả định `task`, `trigger`, `handles` và `jobId` đã được khai báo và được bảo vệ concurrency. Không đồng nhất “trigger không có lượt” với “sai cron”: custom Trigger được phép cố ý trả null để kết thúc lịch.

### 9.1. TriggerContext hỗ trợ lịch tính từ completion

`org.springframework.scheduling.TriggerContext` có các mốc `lastScheduledExecution()`, `lastActualExecution()`, `lastCompletion()` và `getClock()`. Ví dụ custom trigger (mảnh Java):

```java
Trigger afterCompletion = context -> {
    Instant last = context.lastCompletion();
    Instant base = last == null
            ? context.getClock().instant()
            : last;

    return base.plus(Duration.ofMinutes(10));
};
```

`Trigger` thuộc Spring; `Instant`/`Duration` thuộc Java. Một custom trigger có thể quyết định thời gian theo kết quả hoặc trạng thái được giữ ngoài, nhưng nên tránh I/O/blocking nặng trong phương thức tính lần kích hoạt.

## 10. Ví dụ Maven Spring hoàn chỉnh: giữ handle theo job ID

Dự án dưới đây dùng **Java 17, Spring Framework 6.2.7 và Maven**. Nó tự đăng ký hai job, chống trùng, thay one-shot tương lai và hủy lịch. `CountDownLatch` làm kết quả quan sát được mà không dựa vào `Thread.sleep(...)`.

### 10.1. Cấu trúc

```text
dynamic-schedule-demo/
├── pom.xml
└── src/main/java/lesson/DynamicScheduleDemo.java
```

### 10.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lesson</groupId>
    <artifactId>dynamic-schedule-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.7</version>
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
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 10.3. Tệp src/main/java/lesson/DynamicScheduleDemo.java

```java
package lesson;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class DynamicScheduleDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            Registry registry = context.getBean(Registry.class);

            AtomicInteger alphaRuns = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch exited = new CountDownLatch(1);

            try {
                System.out.println("Đăng ký alpha: "
                        + registry.registerFixedDelay(
                            "alpha", () -> {
                                alphaRuns.incrementAndGet();
                                started.countDown();
                                try {
                                    release.await();
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                } finally {
                                    exited.countDown();
                                }
                            }, Duration.ofHours(1)));

                if (!started.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Lượt đầu alpha chưa bắt đầu");
                }

                System.out.println("Alpha đã chạy: "
                        + alphaRuns.get());

                System.out.println("Đăng ký trùng: "
                        + registry.registerFixedDelay(
                            "alpha", () -> {}, Duration.ofHours(1)));

                System.out.println("Hủy alpha: "
                        + registry.cancel("alpha", false));
                System.out.println("Registry còn alpha: "
                        + registry.contains("alpha"));

                release.countDown();
                if (!exited.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Lượt alpha chưa thoát");
                }
                System.out.println("Tổng lượt alpha: "
                        + alphaRuns.get());

                AtomicInteger betaRuns = new AtomicInteger();
                Instant later = Instant.now().plus(
                        Duration.ofHours(1));

                System.out.println("Đăng ký beta: "
                        + registry.registerOnce(
                            "beta", betaRuns::incrementAndGet, later));

                System.out.println("Thay beta: "
                        + registry.replaceOnce(
                            "beta", betaRuns::incrementAndGet, later));

                System.out.println("Hủy beta: "
                        + registry.cancel("beta", false));
                System.out.println("Tổng lượt beta: "
                        + betaRuns.get());

            } finally {
                release.countDown();
                registry.cancelAll(false);
            }
        }
    }

    @Configuration
    public static class Config {

        @Bean("demoScheduler")
        public ThreadPoolTaskScheduler demoScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("demo-clock-");
            scheduler.setRemoveOnCancelPolicy(true);
            return scheduler;
        }

        @Bean
        public Registry registry(
                @Qualifier("demoScheduler") TaskScheduler scheduler) {
            return new Registry(scheduler);
        }
    }

    public static class Registry {

        private final TaskScheduler scheduler;
        private final Map<String, ScheduledFuture<?>> handles =
                new HashMap<>();

        public Registry(TaskScheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler);
        }

        public synchronized boolean registerFixedDelay(
                String id, Runnable work, Duration delay) {

            validate(id);
            Objects.requireNonNull(work, "work");
            Objects.requireNonNull(delay, "delay");

            if (delay.isZero() || delay.isNegative()) {
                throw new IllegalArgumentException(
                        "delay phải dương");
            }
            if (handles.containsKey(id)) {
                return false;
            }

            ScheduledFuture<?> future =
                    scheduler.scheduleWithFixedDelay(work, delay);

            handles.put(id, future);
            return true;
        }

        public synchronized boolean registerOnce(
                String id, Runnable work, Instant time) {

            validate(id);
            Objects.requireNonNull(work, "work");
            Objects.requireNonNull(time, "time");

            if (handles.containsKey(id)) {
                return false;
            }

            ScheduledFuture<?> future = scheduler.schedule(work, time);
            handles.put(id, future);
            return true;
        }

        // Register-first, rồi cancel handle cũ.
        // Có thể chồng tác vụ nếu thời điểm chạy gần nhau.
        public synchronized boolean replaceOnce(
                String id, Runnable work, Instant time) {

            validate(id);
            Objects.requireNonNull(work, "work");
            Objects.requireNonNull(time, "time");

            if (!handles.containsKey(id)) {
                return false;
            }

            ScheduledFuture<?> fresh = scheduler.schedule(work, time);
            ScheduledFuture<?> old = handles.put(id, fresh);
            old.cancel(false);
            return true;
        }

        public synchronized boolean cancel(
                String id, boolean mayInterrupt) {

            ScheduledFuture<?> future = handles.remove(id);
            return future != null && future.cancel(mayInterrupt);
        }

        public synchronized boolean contains(String id) {
            return handles.containsKey(id);
        }

        public synchronized void cancelAll(boolean mayInterrupt) {
            for (ScheduledFuture<?> future : handles.values()) {
                future.cancel(mayInterrupt);
            }
            handles.clear();
        }

        private static void validate(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException(
                        "job id không được trống");
            }
        }
    }
}
```

### 10.4. Chạy

Trong thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.DynamicScheduleDemo
```

Các dòng **do chương trình in ra** trong môi trường scheduler đáp ứng timeout:

```text
Đăng ký alpha: true
Alpha đã chạy: 1
Đăng ký trùng: false
Hủy alpha: true
Registry còn alpha: false
Tổng lượt alpha: 1
Đăng ký beta: true
Thay beta: true
Hủy beta: true
Tổng lượt beta: 0
```

Giải thích:

1. `alpha` được đăng ký theo fixed-delay một giờ. Với overload không có start time, lượt đầu đủ điều kiện ngay, còn lượt sau chỉ đến sau khi lượt trước hoàn tất và chờ một giờ.
2. `started` báo rằng lượt đầu đã thực sự bắt đầu; method đang giữ worker ở `release.await()`.
3. `registerFixedDelay("alpha",...)` lần hai trả `false` vì map đã có ID, không tạo một lịch mới.
4. `cancel("alpha",false)` trả `true` và xóa handle khỏi registry; lượt đầu chưa bị interrupt nên main vẫn cần mở `release`.
5. `exited` xác nhận worker đã tới cuối khối `finally` của lượt đầu; vì chuỗi đã hủy, `alphaRuns` vẫn bằng 1.
6. `beta` được đặt lịch sau một giờ. `replaceOnce` tạo handle mới trước rồi hủy handle cũ; sau đó `cancel` hủy handle mới, nên `betaRuns` bằng 0 trong thời gian demo.
7. `try-with-resources` đóng `ApplicationContext`; Spring quản lý vòng đời `demoScheduler`. `registry.cancelAll(false)` hủy các handle mà registry còn sở hữu.
8. `synchronized` bảo đảm các thao tác registry trên **một instance** không bị xen kẽ, nhưng không chứng minh hai task cũ/mới không overlap hoặc có cơ chế bảo đảm trên nhiều pod.

Ví dụ dùng thời gian **một giờ** để tránh lượt tiếp theo xuất hiện trong chương trình ngắn; đó không phải thiết lập đề xuất cho production. Một timeout 5 giây khiến demo **thất bại rõ ràng nếu worker không kịp chạy**, thay vì treo hoặc in số liệu không kiểm chứng được.

## 11. Java 17 độc lập: cancel(false) khi một lượt vẫn chạy

`ScheduledFuture` là API Java SE; có thể kiểm tra phần cơ chế hủy mà **không cần Spring**.

**Tệp `ScheduledHandleJavaDemo.java` — chạy độc lập:**

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledHandleJavaDemo {

    public static void main(String[] args)
            throws InterruptedException {

        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1);

        executor.setRemoveOnCancelPolicy(true);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();

        ScheduledFuture<?> handle =
                executor.scheduleWithFixedDelay(() -> {
                    runs.incrementAndGet();
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exited.countDown();
                    }
                }, 0, 1, TimeUnit.HOURS);

        try {
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Lượt đầu không bắt đầu");
            }

            System.out.println("Lượt đầu: " + runs.get());

            System.out.println("Hủy được: "
                    + handle.cancel(false));
            System.out.println("Future đã hủy: "
                    + handle.isCancelled());

            release.countDown();

            if (!exited.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Worker chưa thoát");
            }
            System.out.println("Worker đã thoát: true");
            System.out.println("Số lượt: " + runs.get());

        } finally {
            release.countDown();
            handle.cancel(true);
            executor.shutdownNow();
        }
    }
}
```

Lệnh:

```bash
javac ScheduledHandleJavaDemo.java
java ScheduledHandleJavaDemo
```

Kết quả bình thường:

```text
Lượt đầu: 1
Hủy được: true
Future đã hủy: true
Worker đã thoát: true
Số lượt: 1
```

Điểm quan trọng không nằm ở số giây cụ thể mà ở **thứ tự đã được đồng bộ bằng latch**:

- Lượt đầu đã chạy và đang chờ `release`.
- `cancel(false)` thay đổi trạng thái handle mà **không ngắt** lượt đang chờ.
- `release.countDown()` mới cho worker thoát.
- `exited.await(...)` là điểm quan sát worker đã thực sự kết thúc logic của lượt đó.
- Không có lượt hai vì lịch đã bị hủy; delay một giờ tránh cạnh tranh lịch trong thời gian demo.

Không dùng `Thread.sleep(100)` để phỏng đoán thread đã thực thi tới đâu.

## 12. @Scheduled và ScheduledTaskRegistrar khác TaskScheduler trực tiếp

| Cơ chế | Đăng ký | Handle người gọi nhận |
|---|---|---|
| `@Scheduled(fixedDelay=...)` | Spring annotation processor | Không có `ScheduledFuture` trực tiếp từ method |
| `TaskScheduler.schedule*(...)` | Mã nghiệp vụ chủ động | `ScheduledFuture<?>` |
| `ScheduledTaskRegistrar` | API đăng ký nội bộ/chuyên dụng của Spring | Có thể nhận `ScheduledTask` qua các method registrar |

`ScheduledTask` là abstraction khác, có API `cancel(...)` của chính nó. Một phương thức `@Scheduled` bình thường không biến thành API “trả Future” chỉ vì framework chạy nó theo thời gian.

Nếu lịch là hằng số và không cần bật/tắt từng job runtime, annotation đơn giản hơn. Nếu cần CRUD lịch theo mã nghiệp vụ, `TaskScheduler` và registry thường phù hợp hơn.

Một Spring bean có thể dùng cả `@Scheduled` và `TaskScheduler`, nhưng nếu cùng nghiệp vụ được đăng ký theo **cả hai đường**, sẽ có hai lịch độc lập và có thể chạy trùng. Hãy bảo đảm một owner chịu trách nhiệm đăng ký.

## 13. Exception và scheduler từ chối nhận công việc

Các phương thức `TaskScheduler.schedule*(...)` có thể ném `TaskRejectedException` nếu pool đã shutdown hoặc từ chối công việc. Không nên cập nhật registry thành `REGISTERED` trước khi nhận handle hợp lệ.

```java
try {
    ScheduledFuture<?> future = scheduler.schedule(
            runnable,
            Instant.now().plus(Duration.ofMinutes(5)));

    handles.put(id, future);

} catch (TaskRejectedException rejected) {
    recordRejected(id, rejected);
}
```

Đây là **mảnh code**, cần import `TaskRejectedException` từ `org.springframework.core.task` và các biến/phương thức nghiệp vụ. Map phải được đồng bộ theo chính sách registry.

Các lỗi khác:

- `schedule(task, Trigger)` có thể trả **`null`** khi Trigger không sinh lượt tiếp theo; đó không phải một Future hợp lệ.
- Delay/period không hợp lệ cần bị reject trước khi gọi scheduler.
- Một task đăng ký “chạy ngay” có thể bắt đầu **trước khi caller kịp ghi handle vào map**; không dùng thời điểm map.put làm mốc bắt đầu nghiệp vụ.
- Một lần đăng ký thất bại không nên làm mất handle cũ trong thao tác replace-first.

### 13.1. Lỗi trong body task định kỳ

`ThreadPoolTaskScheduler` của Spring thường dùng error-handling wrapper. Lỗi từ một lượt task lặp **thường được log/suppress mặc định**, cho phép chuỗi chạy tiếp. Java `ScheduledThreadPoolExecutor` thuần có thể **chấm dứt periodic registration** khi một lượt ném exception không được xử lý.

Vì thế registry không nên dùng `isDone()` để kết luận lượt gần nhất đã thành công. Nếu cần quan sát lỗi, đo trạng thái **từng lượt**. `ThreadPoolTaskScheduler.setErrorHandler(...)` có thể thay chính sách; handler chỉ log mà không ném tiếp sẽ khác handler propagate.

## 14. Không dùng get() của Future định kỳ để đợi một lượt

Một `ScheduledFuture<?>` của lịch periodic đại diện cho **cả chuỗi thực thi**, không phải một lượt. Nó có thể:

- `isDone() == false` dù 100 lượt đã hoàn tất thành công.
- Bị hủy trong khi một lượt cũ còn chạy.
- Không bao giờ hoàn tất bình thường qua `get()` trong lúc chuỗi vẫn active.

Nếu caller gọi `get()` để đợi “lượt gần nhất”, lời gọi có thể chặn vô hạn. Muốn quan sát completion của một lượt, dùng latch/test signal, callback riêng, active-run counter hoặc lưu trạng thái per-run.

Với **one-shot `Runnable`**, Future hoàn tất khi task kết thúc; `get()` sau thành công thường trả `null`, khi bị hủy ném `CancellationException`, khi lỗi được propagate có thể ném `ExecutionException`. `get(timeout, unit)` có thể chặn tới thời hạn; timeout không tự hủy task.

## 15. Dọn registry và quản lý bộ nhớ

Nếu một service đăng ký nhiều one-shot tasks nhưng không xóa handle đã hoàn tất, `Map<String, ScheduledFuture<?>>` có thể giữ quá nhiều đối tượng, tham chiếu tới Runnable và payload liên quan.

Một cách đơn giản là dọn các handle terminal:

```java
public synchronized int pruneTerminalHandles() {
    int before = handles.size();

    handles.entrySet().removeIf(
            entry -> entry.getValue().isDone());

    return before - handles.size();
}
```

Đây là **mảnh method** trong registry có map `handles`. Nó gỡ handle khỏi memory, **không** xóa lịch sử nghiệp vụ; nếu cần audit, lưu riêng job results và trạng thái.

Với một task one-shot chạy quá nhanh, callback cleanup có thể chạy trước khi handle được đưa vào map. Cần có thiết kế tránh race, ví dụ một lượt prune định kỳ hoặc state machine gắn generation. Không được tùy tiện `handles.remove(id)` trong callback nếu một handle mới của cùng ID đã thay handle cũ; có thể xóa nhầm registration mới.

`ThreadPoolTaskScheduler.setRemoveOnCancelPolicy(true)` giúp scheduler queue dọn những task đã hủy; registry vẫn phải dọn map do nó sở hữu.

## 16. Thay lịch không đồng nghĩa không chồng side effect

Một đoạn replace-first:

```java
ScheduledFuture<?> fresh = scheduler.scheduleWithFixedDelay(
        work, Duration.ofMinutes(15));

ScheduledFuture<?> old = handles.put(jobId, fresh);
if (old != null) {
    old.cancel(false);
}
```

Đây là mảnh code cần khóa registry và validation. **Vấn đề:** `fresh` có thể bắt đầu ngay; `old` cũng có thể đang chạy. `cancel(false)` không buộc `old` dừng. Với nghiệp vụ không được chạy trùng (thanh toán, đối soát, export file), cần quản lý active-run state hoặc generation token.

Ví dụ cơ chế version:

```text
generation 7: RUNNING
Admin yêu cầu đổi lịch
generation 7: CANCEL_REQUESTED
Đợi active-run của generation 7 kết thúc
generation 8: REGISTERED
generation 8: RUNNING
```

Khi cần bảo đảm xuyên nhiều node, generation phải được xác minh trong store chung và thường cần lease/fencing token. Không dùng một biến volatile trên một JVM để giả lập distributed exclusion.

## 17. Shutdown, quyền sở hữu và cancellation khi dừng ứng dụng

Một `ThreadPoolTaskScheduler` được khai báo bằng `@Bean` có vòng đời do Spring điều khiển. Service chỉ nên hủy những handle mà nó sở hữu:

```java
public synchronized void cancelAll(boolean interrupt) {
    for (ScheduledFuture<?> handle : handles.values()) {
        handle.cancel(interrupt);
    }
    handles.clear();
}
```

Đây là mảnh method trong registry. Nó ngăn các lần kích hoạt kế tiếp ở những handles còn giữ, không chờ từng worker thoát. Graceful shutdown có thể cần:

1. ngừng nhận lệnh tạo lịch mới;
2. chuyển registry sang trạng thái `STOPPING`;
3. cancel registrations;
4. đợi/giới hạn thời gian các lượt đang chạy kết thúc;
5. lưu kết quả/cleanup resources;
6. để Spring đóng scheduler bean theo lifecycle.

**Không gọi `scheduler.shutdown()` chỉ để tắt một báo cáo**, vì scheduler có thể phục vụ nhiều job của service khác. Chính sách tiếp tục delayed tasks hoặc periodic tasks sau shutdown phụ thuộc cấu hình của `ThreadPoolTaskScheduler` và Java executor, cần kiểm thử với phiên bản runtime thực tế.

## 18. Cluster, restart và tính bền vững

`ScheduledFuture<?>` là **đối tượng cục bộ JVM**:

```text
Database jobId="report-A"
        |
        +-- Pod A -> ScheduledFuture A
        +-- Pod B -> ScheduledFuture B
        +-- Pod C -> ScheduledFuture C
```

Nếu ba pod đều đặt lịch, cả ba có thể chạy cùng job. Hủy handle A **không hủy** B/C. Restart pod cũng làm mất registry handle ở bộ nhớ.

Nếu nghiệp vụ chỉ cho một worker cluster thực thi, cần thêm leader election, distributed lock/lease, fencing token hoặc job scheduler phân tán. Để chạy lại sau crash, cần lưu metadata và trạng thái job ở database/broker phù hợp; `ScheduledFuture` không tự persist.

**Idempotency quan trọng ngay cả khi có lock**, bởi crash hoặc timeout có thể khiến một side effect đã hoàn tất nhưng caller chưa ghi nhận.

Không lưu trực tiếp `ScheduledFuture` vào database như thể đó là ID có thể phục hồi. Lưu các trường như `jobId`, `scheduleType`, `cronExpression`, `timezone`, `enabled` và version; sau khi chọn owner, dựng handle mới từ cấu hình này.

## 19. Theo dõi lịch và từng lượt chạy

Một registry chỉ trả `isRegistered(id)` chưa đủ cho vận hành. Cần tách:

| Mục tiêu | Trường hoặc metric |
|---|---|
| Lịch được nhận | `registeredAt`, `schedule.registered` |
| Lịch đang được quản lý | `activeRegistrationCount` |
| Lệnh hủy | `cancelRequestedAt`, `cancelAccepted` |
| Lượt bắt đầu | `lastStartedAt`, `run.started` |
| Lượt thành công | `lastSuccessAt`, `run.succeeded` |
| Lượt thất bại | `lastFailureAt`, `run.failed` |
| Thời lượng chạy | `run.duration` |
| Trễ so với thời điểm đủ điều kiện | `scheduler.lateness` |
| Đăng ký bị từ chối | `schedule.rejected` |

`getDelay(...)` chỉ cho thời gian trễ ước tính ở một thời điểm, không thay thế metadata lịch sử hoặc phương pháp xác định lần chạy cron tiếp theo. Một future định kỳ có thể `isDone() == false` dù task liên tục lỗi và bị Spring suppress exception.

Không gắn `jobId` duy nhất làm metric label nếu số lượng job rất lớn, vì cardinality tăng. Job ID thích hợp hơn trong log/trace có kiểm soát.

## 20. Bộ kiểm thử cần có

Một registry chất lượng nên có các kiểm thử:

1. **Start thành công:** tạo đúng một handle.
2. **Start trùng:** không tạo orphan registration.
3. **Hủy lịch tương lai:** one-shot bị hủy trước khi chạy, side effect bằng 0.
4. **Cancel(false) khi đang chạy:** future bị hủy nhưng lượt hiện tại chỉ thoát sau khi mở chốt.
5. **Cancel(true):** task hợp tác với interrupt và cleanup.
6. **Replace:** xác minh chính sách cancel-first/schedule-first và khả năng overlap.
7. **Trigger trả null:** không ghi handle null vào registry.
8. **TaskRejectedException:** không báo đăng ký thành công.
9. **One-shot hoàn tất:** cleanup và trạng thái nghiệp vụ đúng.
10. **Exception periodic:** biết error policy Spring có suppress hay propagate.
11. **Race start/stop:** kết quả không để lại lịch mồ côi.
12. **Shutdown:** từ chối lịch mới sau khi bắt đầu dừng.
13. **Multi-instance:** kiểm tra owner/lease nếu có cluster.

### 20.1. Vì sao dùng CountDownLatch thay Thread.sleep?

`Thread.sleep(100)` không chứng minh task đã bắt đầu, đã tới điểm nào hoặc đã dừng. Trên CI, việc scheduling có thể bị trễ do GC/CPU contention. `CountDownLatch` cho một mốc được báo **bởi chính worker**, còn `await(timeout)` tránh làm test treo vô hạn.

Mảnh test (giả định `scheduler` có sẵn):

```java
CountDownLatch started = new CountDownLatch(1);
CountDownLatch release = new CountDownLatch(1);
CountDownLatch exited = new CountDownLatch(1);

ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
        () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exited.countDown();
            }
        },
        Duration.ofHours(1));

try {
    if (!started.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Chưa bắt đầu");
    }

    boolean accepted = future.cancel(false);
    release.countDown();

    if (!exited.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Chưa thoát");
    }
} finally {
    release.countDown();
    future.cancel(true);
}
```

Ở đây `accepted` nói về yêu cầu hủy; `exited` là mốc worker thật sự thoát. Đừng gọi `get()` của future định kỳ để chờ đúng một lượt.

## 21. Tình huống thực hành

### Bài 1 — Không lưu ScheduledFuture

Một service gọi `scheduleWithFixedDelay(...)` rồi bỏ kết quả. Người dùng muốn ngừng một job nhưng các lịch khác vẫn phải chạy. Vấn đề nằm ở đâu?

**Đáp án:** Không có registry handle theo job ID. Cần giữ `ScheduledFuture<?>` tại nơi sở hữu đăng ký và hủy đúng handle; không shutdown pool chung.

### Bài 2 — Hai yêu cầu start đồng thời

Hai HTTP requests cùng thấy `!map.containsKey(id)` rồi cùng schedule trước khi put. Vì sao map sau đó chỉ có một entry vẫn không đủ?

**Đáp án:** Hai registration đã được tạo; handle bị ghi đè có thể tiếp tục chạy. Cần atomic registration hoặc hủy candidate không được nhận.

### Bài 3 — cancel(false) trả true

Trong khi task đang gửi dữ liệu, cancel trả true. Có thể lập tức kết luận gửi dữ liệu đã dừng không?

**Đáp án:** Không. Future đã hủy registration nhưng lượt đang chạy có thể tiếp tục. Đợi active-run completion hoặc timeout nếu cần.

### Bài 4 — Đổi lịch khi scheduler quá tải

Muốn giữ lịch cũ nếu không tạo được lịch mới, nên chọn kiểu replace nào?

**Đáp án:** Register-first có thể giữ lịch cũ nếu schedule mới thất bại, nhưng phải chấp nhận hoặc quản lý cửa sổ overlap. Nếu cần không overlap, thêm state machine/drain.

### Bài 5 — Trigger không có lần tiếp theo

Custom Trigger trả `null`. `schedule(Runnable, Trigger)` có thể trả gì?

**Đáp án:** Có thể trả `null`; không được lưu như handle hợp lệ hoặc gọi `cancel` trên null. Cần trạng thái `NO_NEXT_EXECUTION`.

### Bài 6 — Một triệu one-shot jobs

Sau khi các job đã hoàn tất, map vẫn có một triệu Future. Cần làm gì?

**Đáp án:** Cleanup terminal handles theo policy, giữ lịch sử ở store riêng và kiểm tra retained heap.

### Bài 7 — Chạy hai demo

Chạy `DynamicScheduleDemo.java` và `ScheduledHandleJavaDemo.java`, giải thích vì sao cả hai đều dùng latch trước và sau cancel.

**Đáp án:** Latch đầu xác nhận worker đã bắt đầu; latch cuối xác nhận lượt đang chạy đã thoát. Cancel không đồng nghĩa completion.

### Bài 8 — Ba pod cùng lập lịch

Pod A hủy `ScheduledFuture` của nó. Có ngừng lịch ở pod B/C không?

**Đáp án:** Không. Cần sở hữu lịch phân tán, lease, command routing và idempotency.

## 22. Checklist triển khai

1. Có **job ID nghiệp vụ** và owner registry được xác định rõ?
2. Dùng đúng overload `TaskScheduler` cho một lần, fixed rate, fixed delay hoặc Trigger?
3. Với Trigger, đã kiểm tra trường hợp trả `null`?
4. Không tạo lịch mồ côi khi start trùng hoặc start/stop race?
5. Có validation chu kỳ, cron, timezone và quyền điều khiển?
6. Xử lý scheduler shutdown/rejection trước khi đánh dấu đăng ký thành công?
7. Khi replace, đã chọn cancel-first hoặc schedule-first có chủ đích?
8. Nếu cần không overlap, đã có active-run drain/idempotency/generation?
9. `cancel(false)`/`cancel(true)` được chọn đúng với interruption và side effects?
10. Không coi `isCancelled()` là worker đã hoàn tất?
11. Không dùng `Future.get()` của chuỗi periodic để chờ một lượt?
12. Có dọn terminal handles, quota số lịch và remove-on-cancel policy?
13. Biết Spring `ErrorHandler` có thể suppress lỗi task lặp?
14. Không shutdown bean scheduler dùng chung để hủy một job?
15. Có logging/metrics cho cả registration và từng lượt chạy?
16. Có chiến lược persistence và distributed ownership nếu chạy cluster?
17. Có integration tests dùng latch/timeout cho register, replace, cancel và shutdown?

## 23. Tổng kết

- **`TaskScheduler.schedule*(...)` trả `ScheduledFuture<?>`**, là handle của một lần đăng ký trong JVM.
- **Giữ handle theo job ID** trong registry được bảo vệ concurrency, không bỏ handle và để lịch trở thành mồ côi.
- **Hủy bằng `cancel(false)` hoặc `cancel(true)`** theo chính sách: cancellation của Future không đồng nghĩa task đang chạy đã dừng ngay.
- **Đổi lịch phải xét race/failure/overlap**, không chỉ `put` một handle mới vào map.
- **`schedule(Runnable, Trigger)` có thể trả `null`**, cần phân biệt với lỗi bị từ chối.
- **Dọn registry, giám sát từng lượt và quản lý lifecycle** là trách nhiệm của ứng dụng.
- **Future là handle cục bộ**, không phải distributed lock, job queue bền vững hoặc đảm bảo thực thi qua restart.

### Tài liệu chính thức

- [Spring Framework Javadoc — TaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework Javadoc — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework Javadoc — Trigger](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/Trigger.html)
- [Spring Framework Javadoc — CronTrigger](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/support/CronTrigger.html)
- [Spring Framework Javadoc — ScheduledTask](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/config/ScheduledTask.html)
- [Java SE 17 Javadoc — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html)
- [Java SE 17 Javadoc — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-scheduling-007">Cập nhật cấu hình không tự thay đổi lịch annotation đã đăng ký</a>

<details>
<summary>Xem chi tiết</summary>

# Cập nhật cấu hình không tự thay đổi lịch annotation đã đăng ký

## 1. Vấn đề: vì sao đổi property nhưng cron/fixed rate vẫn giữ nhịp cũ?

Spring Framework cho phép viết lịch theo cấu hình ngoài bằng `@Scheduled`:

~~~java
@Scheduled(fixedRateString = "${jobs.scan.interval-ms:60000}")
public void scan() {
    service.scan();
}
~~~

Đây là **mảnh code minh họa**, cần Spring-managed bean, `service` và bật `@EnableScheduling`. Nếu lúc khởi tạo `jobs.scan.interval-ms` bằng `60000`, Spring đăng ký một fixed-rate task với period 60 giây.

Sau đó người vận hành sửa property thành `120000` trong file YAML, config server, database hoặc settings bean. **Lịch `@Scheduled` đã được đăng ký không tự đổi thành period 120 giây** chỉ vì một nguồn cấu hình mới tồn tại. Spring không tự re-evaluate các thuộc tính annotation tại mỗi lần job được gọi.

~~~text
Lúc khởi tạo:
    Cấu hình = 60000
         |
         v
    Đọc @Scheduled / resolve ${...}
         |
         v
    Đăng ký task F1: mỗi 60 giây

Lúc runtime:
    Cấu hình được cập nhật = 120000
         |
         +-- Settings/Environment có thể đọc được giá trị mới
         |
         +-- F1 vẫn là task đã đăng ký với period 60 giây
             Không có hành động cancel F1 hoặc tạo F2 tự động
~~~

**Quy tắc chính:** phân biệt **cấu hình mong muốn** (desired configuration) với **registration thực sự đang hoạt động** (active schedule). Muốn đổi thời điểm chạy ở runtime, cần một cơ chế **quản lý đăng ký lịch** tường minh hoặc một `Trigger` động có cách tính lịch mới theo cấu hình.

Bài thuộc nhóm **Lịch động và Trigger** của **Spring Framework – concurrency**. Mục tiêu là hiểu điều gì được cố định lúc xử lý annotation, những gì có thể thay đổi bên trong method, hai phương án reschedule và cách kiểm thử không phụ thuộc timing ngẫu nhiên.

## 2. @Scheduled được xử lý và đăng ký như thế nào?

Khi `@EnableScheduling` có hiệu lực, Spring đăng ký `ScheduledAnnotationBeanPostProcessor` để phát hiện các method có `@Scheduled` trên Spring-managed beans. Hạ tầng đọc metadata của annotation, resolve các biểu thức cần thiết, tạo một task tương ứng rồi giao cho `TaskScheduler`.

~~~text
Spring bean @Scheduled
      |
      v
ScheduledAnnotationBeanPostProcessor
      |
      +-- đọc fixedRate/fixedDelay/cron/initialDelay
      +-- resolve property placeholders và SpEL
      +-- tạo FixedRateTask / FixedDelayTask / CronTask
      +-- đăng ký qua ScheduledTaskRegistrar / TaskScheduler
      |
      v
ScheduledTask / ScheduledFuture đang active
      |
      +--> tick #1
      +--> tick #2
      +--> tick #3
~~~

Sau khi registration đã hình thành, các tick do **scheduler** điều phối, không phải những lần mới gọi vào bộ xử lý annotation để đọc lại `application.yml`. Việc một config object biến đổi không tự mutate `ScheduledFuture` mà scheduler đã tạo.

### 2.1. Cấu hình đọc bên trong body thì khác

~~~java
@Scheduled(fixedDelayString = "${jobs.delay-ms:30000}")
public void processBatch() {
    int size = settings.getBatchSize();
    worker.process(size);
}
~~~

Đây là mảnh code minh họa. `jobs.delay-ms` tham gia tạo lịch, nên không đổi lịch tự động sau khi đã đăng ký. Nhưng `settings.getBatchSize()` nằm trong method body; method được gọi mỗi tick nên có thể thấy một batch size mới nếu settings bean hỗ trợ cập nhật.

Điều này dễ gây hiểu nhầm: **nghiệp vụ thay đổi theo config** không phải bằng chứng **lịch đang chạy đã được reschedule**.

## 3. Placeholder, SpEL và CRON_DISABLED

### 3.1. Các biểu thức trong annotation

~~~java
@Scheduled(fixedRateString = "${jobs.poll.rate:10000}")
public void poll() {}

@Scheduled(fixedDelayString = "${jobs.cleanup.delay:30000}")
public void cleanup() {}

@Scheduled(
    cron = "${jobs.report.cron:0 0 9 * * MON-FRI}",
    zone = "Asia/Ho_Chi_Minh")
public void report() {}
~~~

Các method trên chỉ minh họa chữ ký; cần imports, bean và context. `fixedRateString`, `fixedDelayString` và `cron` hỗ trợ property placeholder. Khi Spring tạo task, chuỗi được resolve để lấy period/cron cụ thể.

SpEL cũng không mặc nhiên là live expression:

~~~java
@Scheduled(fixedRateString = "#{@scheduleSettings.rateMillis}")
public void pollWithSpel() {}
~~~

Mảnh này cần bean `scheduleSettings`. Spring có thể đọc bean property trong lúc xử lý annotation, nhưng **không tự liên tục gọi expression mỗi tick**.

| Nơi lấy giá trị | Thời điểm đọc mặc định | Có làm schedule cũ tự đổi? |
| --- | --- | --- |
| `fixedRate = 10000` | Khi đăng ký annotation | Không |
| `fixedRateString = "${...}"` | Resolve lúc đăng ký | Không |
| `fixedDelayString = "#{...}"` | Đánh giá lúc đăng ký | Không |
| `cron = "${...}"` | Resolve cron lúc đăng ký | Không |
| `settings.getBatchSize()` trong body | Mỗi lần method chạy | Chỉ đổi logic, không đổi lịch |
| `Trigger.nextExecution(context)` đọc config | Khi scheduler tính lần chạy tiếp | Có thể dùng config mới ở **lần tính tiếp** |
| Hủy future cũ và schedule mới | Khi application chủ động yêu cầu | Tạo registration mới rõ ràng |

### 3.2. Dấu "-" của cron

Spring có hằng `Scheduled.CRON_DISABLED` bằng `"-"`:

~~~java
@Scheduled(cron = "${jobs.report.cron:-}")
public void generateReport() {}
~~~

Khi giá trị resolve là `"-"` **lúc registration**, Spring không đăng ký cron này. Nhưng đổi config thành `"-"` **sau khi cron đã được đăng ký** không tự hủy task. Ngược lại, đổi từ `"-"` sang cron hợp lệ sau startup **không tự tạo** registration mới.

Với một API quản trị cần enable/disable runtime, phải thực hiện **cancel/register** có chủ đích thay vì chỉ dựa vào property.

### 3.3. Múi giờ và cron

Một cron có sáu trường trong Spring, khác một số cron UNIX có năm trường. Ví dụ `0 0 9 * * MON-FRI` là 09:00 các ngày làm việc theo zone đã chọn. Khi thay cron hoặc zone đang hoạt động, cần xem giờ địa phương, DST và business rules, không chỉ tính khoảng delay bằng giây.

## 4. Hai câu hỏi độc lập khi hệ thống refresh cấu hình

Nếu người vận hành sửa `application.yml`, cần hỏi **hai câu khác nhau**:

1. **Cấu hình mới đã thực sự vào process chưa?** Không phải mọi Spring app đều tự đọc lại file ngoài sau khi startup. Spring Cloud Config, DB polling hoặc một watcher riêng có thể hỗ trợ reload, nhưng đó là một capability khác.
2. **Schedule đã được tái đăng ký chưa?** Dù `Environment.getProperty(...)` đọc được giá trị mới, `@Scheduled` không mặc nhiên hủy/tạo `ScheduledFuture`.

Đừng suy từ "log thấy config mới" sang "cron đã thay". Ngay cả khi `@RefreshScope` rebuild một settings bean, registration của một method `@Scheduled` khác có thể vẫn dựa trên giá trị cũ.

Một refresh cơ chế thực sự destroy/recreate bean có annotation **có thể** tác động lifecycle và registration; nhưng trình tự cancel/register và overlap tùy cấu hình thực tế. Không có bảo đảm tổng quát rằng `@RefreshScope` làm tất cả cron/fixed rates đổi ngay và an toàn. **Kiểm thử đúng version, đúng bean scope và đúng lifecycle.**

## 5. Fixed rate, fixed delay và cron có semantics khác nhau

| Kiểu lịch | Cách xác định lần sau | Khi đổi cấu hình cần nghĩ tới |
| --- | --- | --- |
| `fixedRate` | Theo các mốc bắt đầu dự kiến cách nhau một period | Overload, delay thực tế, backlog |
| `fixedDelay` | Khoảng chờ sau khi lần trước hoàn thành | Tác vụ dài làm lần sau trễ |
| `cron` | Các mốc calendar theo cron và timezone | Zone, DST, missed executions |
| `initialDelay` | Khoảng trì hoãn trước lần đầu | Khi tạo lịch mới có áp dụng initial delay lại không? |

Nếu job có thể mất 70 giây và fixed-rate period là 60 giây, một lịch định kỳ không tự đảm bảo có hai lần chạy độc lập song song; hành vi còn phụ thuộc scheduler/executor và cách job được dispatch. Nếu fixed-delay 60 giây, lần sau chờ thêm khoảng 60 giây sau lúc lần trước kết thúc.

Đừng chọn `fixedRate` chỉ vì muốn tăng tốc khi job đã bão hòa; phải đo tài nguyên thật và xử lý backlog.

## 6. Phương án A: cancel registration cũ và schedule mới

Khi runtime phải thay ngay **thời điểm tick đang pending**, một phương án rõ ràng là xây service **sở hữu** `ScheduledFuture<?>` rồi thay registration bằng `TaskScheduler`.

~~~java
public synchronized void replaceRate(Duration newRate) {
    if (newRate == null || newRate.isNegative()
            || newRate.isZero()) {
        throw new IllegalArgumentException("rate must be positive");
    }

    ScheduledFuture<?> replacement =
            taskScheduler.scheduleAtFixedRate(
                    this::executeBusinessJob,
                    Instant.now().plus(newRate),
                    newRate);

    ScheduledFuture<?> previous = handle;
    handle = replacement;
    appliedRate = newRate;

    if (previous != null) {
        previous.cancel(false);
    }
}
~~~

Đây là **mảnh method minh họa**, cần khai báo `taskScheduler`, `handle`, `appliedRate`, `executeBusinessJob` và imports. Việc tạo future mới **trước khi** hủy old future giúp giữ lịch cũ nếu registration mới bị từ chối. Tuy nhiên, có cửa sổ **cả hai registrations tồn tại đồng thời**. Nếu làm ngược lại, hủy old trước rồi tạo new, khi registration new thất bại có thể **mất lịch**.

**Không có một thứ tự create/cancel nào loại bỏ mọi race**. Chọn phương án theo SLA, khả năng chấp nhận overlap, transaction đang chạy và cơ chế phục hồi.

### 6.1. ScheduledFuture.cancel không đồng nghĩa hủy side effect

- `cancel(false)` hủy lịch tương lai nhưng **không yêu cầu interrupt** một lần đang chạy.
- `cancel(true)` có thể cố interrupt worker nếu implementation hỗ trợ; không bảo đảm JDBC call, API request hoặc transaction rollback.
- `isCancelled()` chỉ xác nhận trạng thái handle, không chứng minh job đã hoàn thành cleanup.
- Nếu một cron job đã gửi công việc tới executor thứ hai, việc cancel handle scheduler không nhất thiết cancel công việc ở executor phụ.
- Không gọi `shutdown()` trên `ThreadPoolTaskScheduler` dùng chung chỉ để đổi một job; chỉ cancel schedule mình sở hữu.

### 6.2. Khi muốn thay cron

~~~java
public synchronized void replaceCron(String expression, ZoneId zone) {
    CronExpression.parse(expression);
    CronTrigger trigger = new CronTrigger(expression, zone);

    ScheduledFuture<?> replacement =
            taskScheduler.schedule(this::executeBusinessJob, trigger);
    if (replacement == null) {
        throw new IllegalStateException("No future cron execution");
    }

    ScheduledFuture<?> previous = handle;
    handle = replacement;
    if (previous != null) {
        previous.cancel(false);
    }
}
~~~

Mảnh code này cần các fields và method như ví dụ trên. `CronExpression.parse` kiểm tra cú pháp trước khi thay registration, nhưng production còn phải kiểm tra zone hợp lệ, mật độ kích hoạt, quyền người sửa và thời gian hiệu lực. Nếu input là `"-"`, hãy xử lý như yêu cầu **disable/cancel**, không đưa trực tiếp vào `CronTrigger`.

## 7. Phương án B: Trigger động đọc cấu hình khi tính lần chạy tiếp

Một `Trigger` của Spring 6.1 có method:

~~~java
Instant nextExecution(TriggerContext triggerContext);
~~~

`TriggerContext` cho phép đọc `lastScheduledExecution()`, `lastActualExecution()`, `lastCompletion()` và `getClock()`.

Ví dụ fixed-delay-like dynamic trigger:

~~~java
AtomicReference<Duration> delay =
        new AtomicReference<>(Duration.ofSeconds(30));

Trigger dynamic = context -> {
    Duration current = delay.get();
    Instant last = context.lastCompletion();
    Instant anchor = last == null
            ? context.getClock().instant()
            : last;
    return anchor.plus(current);
};

ScheduledFuture<?> scheduled =
        taskScheduler.schedule(this::executeBusinessJob, dynamic);
~~~

Đây là mảnh code minh họa với import và members phù hợp. Nếu cập nhật:

~~~java
delay.set(Duration.ofMinutes(2));
~~~

Thì **lần sau** Spring gọi `nextExecution`, nó có thể đọc Duration mới. Nhưng **một tick đã hẹn trước đó** theo giá trị cũ **không tự bị hủy và tính lại** chỉ vì AtomicReference thay đổi.

### 7.1. Dynamic cron

~~~java
AtomicReference<String> cron =
        new AtomicReference<>("0 */5 * * * *");

ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

Trigger changingCron = context ->
        new CronTrigger(cron.get(), zone).nextExecution(context);

ScheduledFuture<?> scheduled =
        taskScheduler.schedule(this::executeBusinessJob, changingCron);
~~~

Custom Trigger ở đây **thực sự tạo CronTrigger mới mỗi lần nextExecution** từ config hiện tại. Nếu chỉ khởi tạo một `CronTrigger` cố định và đổi biến `cron` bên ngoài, CronTrigger cũ không đổi expression.

Trước khi update:

~~~java
public void updateCron(String expression) {
    CronExpression.parse(expression);
    cron.set(expression);
}
~~~

Mảnh method minh họa này giả định có field `cron`. Validation tránh cron không hợp lệ gây lỗi khi scheduler tính tick kế. Nếu muốn đổi **ngay cả tick đã pending**, vẫn cần explicit reschedule.

### 7.2. Vì sao đổi config không đánh thức pending future?

~~~text
09:00 hôm nay: cron cũ chạy, Trigger tính next = 09:00 ngày mai
10:00 hôm nay: admin đổi cron thành mỗi phút
10:01 hôm nay: nextExecution chưa được gọi lại -> chưa có tick mới
09:00 ngày mai: pending tick tới, sau đó scheduler tính lịch tiếp
~~~

Đây là mô hình rescheduling trigger trên executor in-memory thông thường, không có nghĩa mọi backend scheduling đều giống nhau. Ý chính: **AtomicReference không tự điều khiển ScheduledFuture đang chờ trong queue**.

## 8. Demo Java 17 + Spring 6.1.14: metadata và reschedule

Ví dụ độc lập này không đo latency 60 giây/120 giây. Nó kiểm tra **FixedRateTask metadata** đã đăng ký từ annotation, cập nhật system property mà Spring Environment đọc được, rồi kiểm tra metadata vẫn giữ nguyên. Một service khác chủ động tạo mới/hủy scheduled futures.

**Yêu cầu:** Java 17+, Gradle 8.5+, Spring Framework 6.1.14. Không cần Spring Boot, database hay web server.

### 8.1. Cấu trúc

~~~text
schedule-refresh-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/ScheduleRefreshDemo.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'schedule-refresh-demo'
~~~

**build.gradle**

~~~groovy
plugins {
    id 'application'
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
    implementation 'org.springframework:spring-context:6.1.14'
}

application {
    mainClass = 'demo.ScheduleRefreshDemo'
}
~~~

### 8.2. src/main/java/demo/ScheduleRefreshDemo.java

~~~java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;

public final class ScheduleRefreshDemo {
    private static final String KEY = "demo.rate.ms";

    public static void main(String[] args) {
        String original = System.getProperty(KEY);
        System.setProperty(KEY, "60000");

        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            ScheduledAnnotationBeanPostProcessor processor =
                    context.getBean(ScheduledAnnotationBeanPostProcessor.class);

            FixedRateTask registered =
                    processor.getScheduledTasks().stream()
                            .map(ScheduledTask::getTask)
                            .filter(FixedRateTask.class::isInstance)
                            .map(FixedRateTask.class::cast)
                            .findFirst()
                            .orElseThrow();

            Duration before = registered.getIntervalDuration();

            // Đổi property sau khi @Scheduled đã được đăng ký.
            System.setProperty(KEY, "120000");

            String current =
                    context.getEnvironment().getProperty(KEY);
            Duration after = registered.getIntervalDuration();

            System.out.println("environment changed: " +
                    "120000".equals(current));
            System.out.println("annotation interval unchanged: " +
                    (before.equals(Duration.ofSeconds(60))
                            && after.equals(before)));

            DynamicManager manager = context.getBean(DynamicManager.class);
            manager.replace(Duration.ofSeconds(60));
            ScheduledFuture<?> first = manager.currentHandle();

            manager.replace(Duration.ofSeconds(120));
            ScheduledFuture<?> second = manager.currentHandle();

            System.out.println("old schedule cancelled: " +
                    first.isCancelled());
            System.out.println("new schedule active: " +
                    !second.isCancelled());
            System.out.println("dynamic interval updated: " +
                    manager.currentPeriod().equals(
                            Duration.ofSeconds(120)));

            manager.stop();
        } finally {
            if (original == null) {
                System.clearProperty(KEY);
            } else {
                System.setProperty(KEY, original);
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class Config {

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("refresh-demo-");
            return scheduler;
        }

        @Bean
        public AnnotatedJob annotatedJob() {
            return new AnnotatedJob();
        }

        @Bean
        public DynamicManager dynamicManager(
                @Qualifier("taskScheduler") TaskScheduler scheduler) {
            return new DynamicManager(scheduler);
        }
    }

    public static class AnnotatedJob {
        @Scheduled(
                fixedRateString = "${demo.rate.ms}",
                initialDelay = 600000)
        public void run() {
            // Trong demo không cần quan sát tick thực tế.
        }
    }

    public static class DynamicManager {
        private final TaskScheduler scheduler;
        private ScheduledFuture<?> handle;
        private Duration period;

        public DynamicManager(TaskScheduler scheduler) {
            this.scheduler = scheduler;
        }

        public synchronized void replace(Duration nextPeriod) {
            Objects.requireNonNull(nextPeriod, "period");
            if (nextPeriod.isNegative() || nextPeriod.isZero()) {
                throw new IllegalArgumentException(
                        "period must be positive");
            }

            ScheduledFuture<?> replacement =
                    scheduler.scheduleAtFixedRate(
                            () -> { },
                            Instant.now().plus(nextPeriod),
                            nextPeriod);

            ScheduledFuture<?> previous = handle;
            handle = replacement;
            period = nextPeriod;

            if (previous != null) {
                previous.cancel(false);
            }
        }

        public synchronized ScheduledFuture<?> currentHandle() {
            return handle;
        }

        public synchronized Duration currentPeriod() {
            return period;
        }

        public synchronized void stop() {
            if (handle != null) {
                handle.cancel(false);
            }
        }
    }
}
~~~

### 8.3. Chạy và stdout dự kiến

Trong thư mục `schedule-refresh-demo`:

~~~bash
gradle run
~~~

Nếu có Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

**Năm dòng stdout do chương trình tự in khi Spring đăng ký lịch và executor hoạt động bình thường:**

~~~text
environment changed: true
annotation interval unchanged: true
old schedule cancelled: true
new schedule active: true
dynamic interval updated: true
~~~

Log startup của Spring/Gradle có thể xuất hiện ngoài năm dòng này. Nếu context initialization hoặc scheduler registration thất bại, chương trình ném lỗi thay vì in kết quả thành công giả.

### 8.4. Vì sao demo chứng minh điều cần học?

1. `System.setProperty("demo.rate.ms","60000")` trước context startup là property source mà Standard Spring Environment có thể dùng; annotation resolve ra 60 giây.
2. `@EnableScheduling` bật `ScheduledAnnotationBeanPostProcessor`; `@Scheduled(fixedRateString=...)` tạo `FixedRateTask`. `getScheduledTasks()` cho phép quan sát registration.
3. `FixedRateTask.getIntervalDuration()` trả period đã lưu của task; giá trị này không tự thay đổi sau khi system property bị cập nhật.
4. `Environment.getProperty(KEY)` có thể thấy `120000` từ live system properties, tách biệt với metadata cũ.
5. `DynamicManager` không gắn annotation cho job programmatic; nó dùng `TaskScheduler.scheduleAtFixedRate` để tạo handle mới và cancel handle cũ.
6. `first.isCancelled()` xác nhận old handle đã cancel; `second.isCancelled()==false` chỉ nói new handle chưa cancel lúc quan sát, **không nói business job đã thành công**.
7. `initialDelay=600000` giúp annotation job không chạy trong thời gian demo bình thường; test dựa metadata thay vì đo đếm tick dễ flake.
8. `manager.stop()` chỉ hủy registration do manager sở hữu; đóng Spring context để Spring quản lý shutdown của scheduler bean.

**Điểm quan trọng:** đây là **hai jobs riêng** để chứng minh hai thiết kế. Nếu production dùng cả `@Scheduled` và dynamic manager **cho cùng một business method**, bạn có thể vô tình tạo hai lịch, không phải cập nhật một lịch.

## 9. SchedulingConfigurer và ScheduledTaskRegistrar

Spring cho phép cấu hình tasks thông qua `SchedulingConfigurer` và `ScheduledTaskRegistrar` khi startup. Đây là extension point phù hợp để đăng ký custom Trigger hoặc cung cấp scheduler.

~~~java
@Configuration
@EnableScheduling
public class CustomScheduleConfig implements SchedulingConfigurer {

    private final TaskScheduler scheduler;
    private final Trigger trigger;
    private final Runnable job;

    public CustomScheduleConfig(
            TaskScheduler scheduler, Trigger trigger, Runnable job) {
        this.scheduler = scheduler;
        this.trigger = trigger;
        this.job = job;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduler);
        registrar.addTriggerTask(job, trigger);
    }
}
~~~

Đây là **mảnh code minh họa** với imports/beans cần bổ sung. `SchedulingConfigurer.configureTasks` không mặc nhiên được gọi lại khi một property của Spring Cloud đổi. Muốn dynamic runtime, code quản lý thay đổi/trigger vẫn phải được thiết kế.

`ScheduledAnnotationBeanPostProcessor.getScheduledTasks()` giúp **liệt kê/quan sát** registration; `ScheduledTask.cancel(...)` có thể hủy handle. Nhưng việc hủy annotation task **không tự tạo task mới với property mới**. Không nên dò tất cả tasks bằng tên method rồi cancel ngẫu nhiên trên production; một method có thể có nhiều annotations hoặc nhiều bean instances.

## 10. Quản lý cấu hình có version và quyền sở hữu

Một service vận hành nên giữ **cấu hình mong muốn** và **cấu hình đã áp dụng** riêng:

~~~text
desiredVersion = 12
desiredCron = "0 */10 * * * *"

appliedVersion = 11
activeCron = "0 */5 * * * *"

apply(12):
    validate cron / zone / tần suất
    tạo registration mới
    cancel old theo policy
    ghi appliedVersion=12
    quan sát kết quả
~~~

Nếu code ghi `appliedVersion=12` trước khi scheduler nhận registration mới, dashboard có thể báo đã áp dụng mặc dù executor đã shutdown và reject. Ngược lại, nếu admin gửi hai updates gần nhau, manager cần tránh một event version cũ thay lại lịch mới.

Yêu cầu quan trọng:

- **Một owner/handle cho mỗi job key** trên mỗi JVM.
- **Synchronized/lock** thao tác replace, validate trước khi thay.
- **Idempotent apply:** cùng version đến hai lần không tạo hai schedules.
- **Persistence:** lưu desired config để khôi phục registration sau restart.
- **Authorization:** không để một client bất kỳ sửa cron chạy dày gây quá tải.
- **Audit:** ghi ai thay, cron cũ/mới, zone, thời điểm áp dụng và lỗi.
- **Error policy:** nếu tạo mới thất bại, trạng thái applied phải phản ánh lịch thực tế.

Một API update config trả 200 không đồng nghĩa lịch mới đã active nếu apply chạy bất đồng bộ. Khi cần, trả trạng thái pending/applied/failed với version.

## 11. Race, overload và cancellation khi thay lịch

Một lần job có thể đang chạy transaction hoặc HTTP call khi admin đổi cron. Hủy `ScheduledFuture` là thao tác trên **registration**; nó không hoàn tác side effect.

| Trạng thái | Ý nghĩa |
| --- | --- |
| Config mới được lưu | Desired schedule đã thay |
| ScheduledFuture mới được tạo | Registration mới đã được chấp nhận |
| Old handle cancelled | Các lần kích hoạt cũ được yêu cầu ngừng theo contract |
| Old execution finished | Worker cũ thực sự đã kết thúc |
| Database/remote side effect committed | Kết quả nghiệp vụ đã xảy ra |
| Applied version được ghi | Hệ thống ghi nhận phiên bản lịch đã áp dụng |

Đừng coi các trạng thái trên là một.

Nếu job không được overlap, dùng guard hoặc lease:

~~~java
private final AtomicBoolean running = new AtomicBoolean();

public void runGuarded() {
    if (!running.compareAndSet(false, true)) {
        return; // Skip khi job trước còn chạy.
    }
    try {
        executeBusinessJob();
    } finally {
        running.set(false);
    }
}
~~~

Đây là mảnh class cần import `AtomicBoolean` và method `executeBusinessJob`. Guard trong JVM **không** bảo vệ trên toàn cluster. Với nhiều replicas, dùng DB lease/leader election/distributed locking hoặc idempotency tùy consistency yêu cầu.

**Không tăng scheduler pool size như một cách refresh cron.** Pool size ảnh hưởng capacity nhưng không làm annotation metadata được parse lại.

## 12. Kiểm thử đúng thay vì suy đoán qua log thời gian

Một test chỉ đếm hai tick cách nhau 50 ms hoặc 100 ms dễ flake vì JVM startup, GC, máy CI và queue. Để kiểm chứng **cấu hình thay đổi không làm registration tự thay**, ưu tiên:

1. Tạo context với property ban đầu.
2. Dùng `ScheduledAnnotationBeanPostProcessor.getScheduledTasks()` xác định task được đăng ký.
3. Với fixed-rate task, đọc `FixedRateTask.getIntervalDuration()` hoặc metadata tương ứng.
4. Thay property source hoặc settings bean.
5. Xác nhận `Environment` thấy giá trị mới (nếu đó là cơ chế reload thực).
6. Xác nhận metadata registration cũ **không đổi**.
7. Gọi manager explicit reschedule, kiểm tra old handle cancelled và new handle active.
8. Với Trigger động, test `nextExecution` cùng `TriggerContext` và Clock có kiểm soát.
9. Test invalid cron, period 0/âm, scheduler rejection và shutdown.
10. Test hai admin updates đồng thời, bảo đảm không tạo duplicates không chủ.
11. Test config chuyển disabled/enabled và vòng đời scheduled registration.
12. Test restart/multiple instances nếu job cần durability hoặc cluster exclusivity.

`getScheduledTasks()` hữu ích cho test/observability, nhưng **không tự là API live refresh** của Spring.

### 12.1. Metrics nên có

- Desired config version và applied config version.
- Current applied cron/period/zone.
- Last successful reschedule time và số lần reschedule thất bại.
- Số active registrations cho mỗi job key.
- Planned next execution (nếu được hệ thống tính/quan sát).
- Last actual start/completion, execution duration, error count.
- Số overlap/skipped runs và scheduler rejection.
- Cluster ownership/lease state khi chạy nhiều nodes.

Đừng diễn giải `isDone()` trên periodic ScheduledFuture như "job đã xong thành công" vì một recurring future có thể chưa bao giờ complete bình thường cho tới khi canceled/failed.

## 13. Lịch động và Spring reactive @Scheduled

Từ Spring 6.1, một method `@Scheduled` có thể trả Publisher có deferred semantics để Spring subscribe lại theo lịch. Nhưng thuộc tính `cron`/`fixedRate`/`fixedDelay` vẫn là **metadata của registration**.

~~~java
@Scheduled(cron = "${jobs.reactive.cron:0 */5 * * * *}")
public Mono<Void> refresh() {
    return Mono.defer(() -> service.refreshReactive());
}
~~~

Đây là mảnh code minh họa cần Reactor và các beans. Spring có thể giữ Publisher để subscribe theo tick, nhưng đổi `jobs.reactive.cron` không tự tạo lại registration. Reactive subscription behavior và hot schedule reconfiguration là **hai vấn đề độc lập**.

Với reactive fixed-delay, scheduler có thể chờ subscription hoàn thành để giữ nghĩa fixed delay. Điều đó không khiến một config listener tự cập nhật cron. Nếu pipeline kéo dài hoặc không complete, cần timeout và cancellation phù hợp.

## 14. Các lỗi thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Đổi YAML rồi tin `@Scheduled` đọc lại | Lịch vẫn cũ | Explicit schedule manager |
| SpEL đọc bean rate và tưởng tính lại mỗi tick | Registration đã cố định | Custom Trigger đọc config tại nextExecution |
| Đổi cron property sang `"-"` rồi nghĩ đã disable | Task cũ vẫn pending | Cancel handle |
| Thay String trong một `CronTrigger` đã tạo | Trigger object vẫn giữ expression cũ | Tạo Trigger mới hoặc dynamic Trigger |
| Đổi `AtomicReference` và tin pending tick tự đổi | Next tick đã nằm trong queue | Reschedule nếu cần hiệu lực ngay |
| Mỗi admin request tạo lịch mới mà không cancel | Có nhiều registrations | Một owner + quản lý handle |
| `cancel(true)` và nghĩ đã rollback DB | Task cancellation là best effort | Cancellation protocol + idempotency |
| Tắt shared scheduler pool để đổi một job | Làm ngừng nhiều jobs khác | Cancel đúng ScheduledFuture |
| `@RefreshScope` nhưng không test bean lifecycle | Có thể trùng/gián đoạn hoặc chưa đổi | Quan sát cancel/register/applied state |
| Không validate cron/period | Lịch sai hoặc quá tải | Parse/validate trước khi áp dụng |
| Hai nodes cùng chạy cron không kiểm soát | Duplicate side effect | Leader/lease/job store |
| Chỉ nhìn log thời gian vài tick | Kết luận sai do scheduling noise | Kiểm tra registration metadata |

## 15. Checklist triển khai

- [ ] Đã phân biệt desired config, Environment value và active registration.
- [ ] Không kỳ vọng property placeholder hoặc SpEL trong `@Scheduled` tự refresh.
- [ ] Nếu chỉ đổi logic batch, đọc setting trong body; nếu đổi thời gian, quản lý schedule.
- [ ] Chọn explicit reschedule hay dynamic Trigger theo thời điểm hiệu lực.
- [ ] Biết dynamic Trigger chỉ áp dụng khi scheduler tính next execution.
- [ ] Có owner cho mỗi ScheduledFuture/ScheduledTask.
- [ ] Validate cron/period/zone và giới hạn tần suất.
- [ ] Chọn thứ tự create/cancel, xử lý rejection và overlap.
- [ ] Không đăng ký cùng business job bằng hai cơ chế ngoài ý muốn.
- [ ] Dùng guard/lease nếu job không được chạy trùng.
- [ ] Có desired/applied version và thông báo update failed.
- [ ] Không shutdown pool Spring dùng chung chỉ để thay một lịch.
- [ ] Có cơ chế reconcile sau restart và xử lý event trùng/đến muộn.
- [ ] Nếu cần durable execution, thiết kế job store/claim/retry/idempotency.
- [ ] Test metadata + trạng thái handles, không chỉ timing ngắn.
- [ ] Với reactive `@Scheduled`, hiểu rằng subscribe lại Publisher vẫn không làm cron tự refresh.

## 16. Câu hỏi tự kiểm tra

1. **Vì sao đổi `jobs.poll.rate` không đổi `@Scheduled(fixedRateString=...)` ngay?** Vì annotation đã được resolve và scheduler đăng ký task từ giá trị ban đầu.
2. **Environment thấy property mới nghĩa là lịch đã đổi?** Không. Đây là hai trạng thái độc lập.
3. **SpEL trên annotation có chạy lại theo từng tick?** Không trong cơ chế mặc định.
4. **Dấu `"-"` có tự hủy cron đang chạy khi admin mới update property?** Không; nó vô hiệu hóa registration khi được resolve lúc đăng ký.
5. **Hai phương án cập nhật runtime chính?** Cancel + đăng ký handle mới, hoặc custom Trigger đọc config khi nextExecution.
6. **AtomicReference trong Trigger có đổi tick đã pending ngay?** Không.
7. **Khi nào cần explicit reschedule?** Khi thay đổi phải áp dụng ngay cả cho pending tick.
8. **Vì sao tạo mới trước rồi cancel cũ có rủi ro?** Có cửa sổ hai registrations cùng tồn tại.
9. **Vì sao cancel cũ trước rồi tạo mới có rủi ro?** Nếu tạo mới lỗi thì mất lịch.
10. **`cancel(false)` có buộc ngắt worker đang chạy?** Không.
11. **`@EnableScheduling` có bắt buộc nếu chỉ gọi `TaskScheduler.schedule` programmatically?** Không; nó cần cho annotation processing.
12. **`@RefreshScope` có là giải pháp bảo đảm hot cron?** Không thể khẳng định chung; phải test lifecycle cụ thể.
13. **Demo dùng API nào để đọc period đã đăng ký?** `FixedRateTask.getIntervalDuration()`.
14. **Nếu ba replicas cùng bật lịch thì chỉ một node chạy job?** Không mặc định.
15. **Một scheduler in-memory có khôi phục missed jobs sau crash không?** Không tự động.
16. **Vì sao cần appliedVersion ngoài desiredVersion?** Để biết config đã thật sự đăng ký thành công hay chỉ được lưu.
17. **Reactive @Scheduled có tự update cron do Publisher subscribe lại?** Không; cron thuộc scheduling metadata.
18. **Thông điệp chính?** Đổi config không tự đổi registration `@Scheduled`; cần quản lý timing/lifecycle rõ ràng.

**Kết luận:** `@Scheduled` tạo lịch khi annotation được xử lý. Thay config file, Environment, settings bean hoặc SpEL source không tự thay period/cron của lịch đã đăng ký. Với yêu cầu thay lịch runtime, xây ScheduleManager sở hữu handles hoặc một Trigger động, phân biệt thời điểm **tính lần tiếp theo** với thời điểm **cập nhật ngay**, và kiểm soát cancel, overload, version, cluster và durability.

### Tài liệu chính thức

- [Spring Framework 6.1 — @Scheduled](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.1 — ScheduledAnnotationBeanPostProcessor](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html)
- [Spring Framework 6.1 — FixedRateTask](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/config/FixedRateTask.html)
- [Spring Framework 6.1 — IntervalTask](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/config/IntervalTask.html)
- [Spring Framework 6.1 — ScheduledTask](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/config/ScheduledTask.html)
- [Spring Framework 6.1 — TaskScheduler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework 6.1 — Trigger](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/Trigger.html)
- [Spring Framework 6.1 — TriggerContext](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/TriggerContext.html)
- [Spring Framework 6.1 — CronTrigger](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html)
- [Spring Framework 6.1 — ScheduledTaskRegistrar](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/config/ScheduledTaskRegistrar.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Java 17 — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-scheduling-008">Tránh đăng ký chồng lịch khi thay đổi cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh đăng ký chồng lịch khi thay đổi cấu hình

## 1. Vấn đề: một thay đổi cấu hình có thể tạo ra nhiều lịch cùng chạy

Khi ứng dụng Spring dùng lịch động, lịch thực thi không nhất thiết được xác định một lần bằng `@Scheduled` cố định. Cron expression, múi giờ, tần suất polling hoặc trạng thái bật/tắt có thể đến từ cơ sở dữ liệu, cấu hình từ xa hoặc một API quản trị. Mỗi khi cấu hình đổi, ứng dụng cần thay lịch đang chạy bằng lịch mới.

Lỗi phổ biến là **mỗi sự kiện thay đổi lại gọi `TaskScheduler.schedule(...)` mà không hủy lịch đã đăng ký trước đó**:

```java
// SAI nếu được gọi nhiều lần cho cùng một logical job:
void onConfigChanged(String cron) {
    taskScheduler.schedule(
            () -> refreshReports(),
            new CronTrigger(cron));
}
```

Đây là snippet minh họa; `taskScheduler` và `refreshReports()` phải được định nghĩa trong ứng dụng. Giả sử cấu hình ban đầu là `0 */5 * * * *` (mỗi 5 phút), sau đó chuyển thành `0 */10 * * * *` (mỗi 10 phút). Nếu không giữ lại và hủy lịch cũ, **cả hai lịch** có thể tồn tại trong scheduler và kích hoạt cùng một công việc ở các mốc trùng nhau.

Đây là lỗi **đăng ký chồng lịch** (*duplicate/overlapping schedule registration*) – khác với việc một task chạy lâu khiến lần chạy mới chồng lấn. Hai vấn đề có quan hệ nhưng cần hai lớp bảo vệ khác nhau.

**Mục tiêu sau bài học:**

1. Hiểu vòng đời của `TaskScheduler.schedule(Runnable, Trigger)`, `CronTrigger` và `ScheduledFuture`.
2. Phân biệt “có duy nhất một lịch đăng ký đang hiệu lực” với “có duy nhất một lần chạy nghiệp vụ”.
3. Biết cách **lưu handle, kiểm tra cấu hình có đổi thật, hủy lịch cũ và thay lịch mới**.
4. Thực hiện thay đổi một cách có đồng bộ để nhiều config events không đăng ký hai handle.
5. Hiểu giới hạn của `cancel(false)`, `cancel(true)`, callback đang chạy và cửa sổ race.
6. Áp dụng **generation/version fencing**, single-flight và idempotency tại nơi thích hợp.
7. Test có thể tái hiện bằng Java 17 + Spring, không dựa vào may rủi của đồng hồ cron.
8. Nhận diện thêm vấn đề khi triển khai nhiều pod, restart, reload thuộc tính và cron bị vô hiệu hóa.

**Thông điệp chính:** muốn đổi lịch an toàn, phải quản lý cả **định danh logical job**, **handle của registration**, **phiên bản cấu hình** và **quyền thực thi công việc**, chứ không chỉ gọi thêm `schedule()`.

## 2. Ba khái niệm khác nhau: lịch, lần chạy và công việc nghiệp vụ

Một logical job tên `report-refresh` có thể có nhiều registration do cấu hình thay đổi:

```text
Logical job: "report-refresh"
          |
          +-- Registration R1: cron mỗi 5 phút  ---- future R1
          |
          +-- Registration R2: cron mỗi 10 phút ---- future R2
          |
          +-- Business execution: refreshReports() thật
```

**Registration** là quan hệ giữa một callback và một trigger trong scheduler. **Execution** là một lần callback thực sự được worker chạy. **Business effect** là tác động sau cùng như cập nhật DB, gửi email, ghi cache hoặc gọi API.

Hai registration có thể cùng nhắm tới **một** business method. Nếu chúng đều còn hiệu lực, việc có một bean duy nhất hoặc một method duy nhất **không** ngăn hai nguồn kích hoạt.

Bảng nhận diện:

| Thuộc tính | Nó mô tả | Ví dụ |
| --- | --- | --- |
| Logical job ID | Định danh công việc ổn định qua các lần đổi cấu hình | `report-refresh` |
| Schedule specification | Cron, `ZoneId`, trạng thái bật, kiểu trigger | `0 */10 * * * *` + `Asia/Ho_Chi_Minh` |
| Registration handle | Đối tượng cho phép hủy lịch đã nhận | `ScheduledFuture<?>` |
| Configuration revision | Phiên bản/generation của cấu hình đang có hiệu lực | `revision=12` |
| Execution ID | Một lần chạy cụ thể | `report-refresh/2026-11-02T09:00Z` |
| Business idempotency key | Khóa chống tác động trùng theo nghiệp vụ | `jobId + scheduledFireTime` |

Để tránh đăng ký chồng lịch **trong một JVM**, thông thường ta quản lý một `ScheduledFuture<?>` hiện hành cho mỗi logical job ID. Nhưng chỉ điều đó **chưa bảo đảm** không có tác vụ cũ đã bắt đầu hoặc một registration nằm ở pod khác.

### Hai kiểu overlap cần tách rõ

**Registration overlap:**

```text
Config v1 -> schedule(R1) --- vẫn còn hiệu lực ------> tick
Config v2 -> schedule(R2) --- cũng còn hiệu lực ------> tick
                 Hai lịch cùng gọi một logical job
```

**Execution overlap:**

```text
R1 tick -> run #1 đang xử lý DB (15s) --------->
R2 tick -> run #2 bắt đầu trong lúc run #1 còn chạy
                 Hai executions thật chồng nhau
```

Giải pháp chỉ lưu một registration mới ngăn nguồn kích hoạt trùng trong tương lai; nếu công việc đang chạy lâu hoặc có nhiều worker/pod, vẫn phải giải quyết execution overlap và side effects.

## 3. API Spring để đăng ký và hủy lịch

Với Spring Framework 6.x, cách lập lịch cron động thông thường là:

```java
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

CronTrigger trigger = new CronTrigger(
        "0 */5 * * * *",
        ZoneId.of("Asia/Ho_Chi_Minh"));

ScheduledFuture<?> future =
        taskScheduler.schedule(myRunnable, trigger);
```

Đây là snippet trong một method có biến `taskScheduler` và `myRunnable`. Nó không phải file độc lập.

**Hợp đồng quan trọng:**

- `TaskScheduler.schedule(Runnable, Trigger)` yêu cầu callback được kích hoạt mỗi khi trigger xác định lần chạy kế tiếp.
- Giá trị trả về là `ScheduledFuture<?>` đại diện cho lịch đang chờ. Đối với trigger không còn lần chạy tiếp theo, API có thể trả **`null`**.
- `future.cancel(false)` yêu cầu hủy lịch, **không yêu cầu interrupt** task đang chạy.
- `future.cancel(true)` có thể cố interrupt nếu implementation cho phép; **không bảo đảm** thread/remote I/O/business side effect dừng ngay.
- `schedule(...)` có thể ném `TaskRejectedException` khi scheduler từ chối vì đã shutdown hoặc lý do khác.
- Tạo một `new CronTrigger(...)` cho cùng cron expression **không phải** cơ chế “tự thay thế” registration cũ.
- Một `ScheduledFuture<?>` đã hủy không phải một handle có thể tái dùng để thay cron; hãy tạo registration mới.

### Tại sao phải giữ ScheduledFuture?

Nếu không lưu handle, mã callback cấu hình không có cách trực tiếp để biết **chính xác đăng ký nào cần hủy** khi thay cron. Lưu `String lastCron` thôi không đủ; cron là mô tả lịch, không phải khóa định danh của một handle nội bộ.

Ví dụ quản lý đơn giản cho **một** job:

```java
private ScheduledFuture<?> currentFuture;

public synchronized void updateCron(String expression) {
    if (currentFuture != null) {
        currentFuture.cancel(false);
    }
    currentFuture = taskScheduler.schedule(
            this::runBusinessJob,
            new CronTrigger(expression));
}
```

Đây là snippet minh họa (cần nằm trong class, có `taskScheduler` và `runBusinessJob`). Nó **đỡ hơn code ban đầu** vì có cancel và synchronized, nhưng còn nhiều lỗ hổng cần xử lý: không validate cấu hình trước, đổi sang cron không hợp lệ có thể làm mất lịch cũ, cập nhật không thay đổi vẫn làm hủy/đăng ký lại, task đang chạy cũ vẫn có thể tiếp tục, và không có cách tắt lịch rõ ràng.

Bài sẽ cải tiến thiết kế theo từng rủi ro.

## 4. Những nguyên nhân thường tạo lịch trùng khi cấu hình đổi

Đăng ký chồng lịch không chỉ đến từ một vòng lặp gọi `schedule()`. Những tình huống thực tế:

1. **Listener được gọi nhiều lần cho cùng một version:** config server gửi lại snapshot, event bus có delivery *at-least-once*, hoặc retry API cập nhật.
2. **Hai cập nhật chạy đồng thời:** HTTP admin request và message listener cùng gọi registration code. Nếu chỉ `if (future == null) schedule(...)` mà không lock, cả hai cùng thấy null rồi tạo hai future.
3. **Khởi tạo hai đường:** `@PostConstruct` đăng ký lịch, sau đó `ApplicationReadyEvent` đăng ký lần nữa.
4. **Kết hợp `@Scheduled` với manual schedule:** một method có trigger tĩnh do Spring đăng ký và một trigger động do ứng dụng tạo.
5. **Tạo nhiều bean cùng công việc:** cùng class tồn tại ở hai application contexts hoặc tạo thủ công bean có scheduling infrastructure.
6. **Không hủy khi disable:** cấu hình chuyển từ enabled sang disabled nhưng `ScheduledFuture` cũ vẫn hoạt động.
7. **Hủy sai handle:** lưu một trường `future` chung cho nhiều logical jobs, khi job B update lại hủy handle của A.
8. **Bỏ qua thay đổi timezone:** so sánh chỉ cron string, trong khi `ZoneId` cũng đổi; có thể giữ lịch sai timezone hoặc tạo nhánh đăng ký mới không đồng bộ.
9. **Tạo một scheduler cho mỗi lần đổi config:** mỗi scheduler có registry riêng; việc hủy handle ở instance mới không tác động lịch cũ của instance khác.
10. **Nhiều pod:** cùng một logical job được đăng ký trên nhiều JVM. Mỗi pod “chỉ có một registration” nhưng toàn cluster có nhiều registration.

Các lỗi 1–9 chủ yếu có thể xử lý bằng lifecycle và đồng bộ **trong một JVM**. Lỗi 10 đòi coordination ở cấp cluster và thường cần durable state, lease hoặc leader election.

## 5. Vì sao “hủy cái cũ rồi đăng ký cái mới” chưa hoàn toàn an toàn?

Có hai cách đổi lịch phổ biến. Cả hai đều có trade-off.

### 5.1. Cancel trước, schedule sau

```text
old.cancel(false)
new = scheduler.schedule(task, newTrigger)
publish(new)
```

Ưu điểm: không có khoảng thời gian giữ hai future đăng ký mới và cũ trong cùng registry.

Nhược điểm: nếu cron mới sai, scheduler bị shutdown hoặc `schedule(...)` ném `TaskRejectedException`, lịch cũ **đã bị hủy**; hệ thống có thể không còn lịch chạy nào.

### 5.2. Schedule trước, publish rồi cancel cũ

```text
validate(newConfig)
candidate = scheduler.schedule(guardedTask, newTrigger)
publish(candidate, newVersion)
old.cancel(false)
```

Ưu điểm: nếu candidate bị từ chối **trước khi publication**, registry có thể **giữ nguyên lịch cũ** và báo lỗi thay vì âm thầm mất job.

Nhược điểm: có một khoảng ngắn **hai registrations vật lý cùng tồn tại**. Phải đảm bảo callback của candidate không thực hiện nghiệp vụ trước khi version mới được publish; callback cũ bị kích hoạt sau khi đã publish cũng cần kiểm tra version.

Đây là bài toán **publish/swap có điều kiện**, không phải một transaction nguyên tử mà Spring Scheduler tự cung cấp. Giải pháp khả thi cho nhiều ứng dụng là:

1. Validate expression/zone bên ngoài lock.
2. Dùng một lock trên **logical job** để tuần tự hóa updates.
3. Schedule candidate có **generation token** riêng.
4. Chỉ khi candidate được nhận, cập nhật handle/version hiện hành trong lock.
5. Hủy handle cũ bằng `cancel(false)`.
6. Callback kiểm tra generation trước khi thực thi.
7. Thêm single-flight/idempotency nếu không được phép execution overlap.

Vẫn có một **cửa sổ cực nhỏ**: một callback cũ đã kiểm tra version và bước vào phần nghiệp vụ trước khi update hoàn tất thì có thể tiếp tục chạy. Không thể giải quyết tác dụng phụ đã bắt đầu bằng cách chỉ đổi trạng thái Future.

## 6. Định nghĩa “idempotent update” và version fencing

Một listener cấu hình thường có thể nhận cùng payload nhiều lần. Cần biến việc cập nhật cấu hình thành thao tác **idempotent**: gọi lặp với đúng cùng effective configuration không tạo thêm registration.

Một schedule key tối thiểu:

```text
ScheduleKey = (
    logicalJobId,
    cronExpression,
    zoneId,
    enabled,
    semantics/options
)
```

Nếu tất cả thành phần không đổi, `replace(...)` nên trả kết quả kiểu `NO_CHANGE` mà **không** gọi `schedule` và **không** reset lần kích hoạt kế tiếp.

### Vì sao không chỉ so cron?

```text
cron = "0 0 9 * * *"
zone A = "Asia/Ho_Chi_Minh"
zone B = "Europe/Berlin"
```

Cùng biểu thức nhưng khác thời điểm trên trục UTC; đó là một thay đổi cấu hình thật. Ngược lại, một event có dữ liệu giống hệt hiện hành chỉ cần được ghi nhận, không cần tạo lại lịch.

### Generation token

Mỗi khi một cấu hình mới **được publish thành công**, tăng version:

```text
currentGeneration = 4

callback từ registration v3:
    if (myGeneration != currentGeneration) return;

callback từ registration v4:
    if (myGeneration != currentGeneration) return;
    runBusinessJob();
```

Token là một **fence** giúp callback cũ đến muộn không thực thi như một lịch hiện hành. Một callback đang ở giữa phần nghiệp vụ trước khi version đổi **không thể bị rút lại** chỉ nhờ so version.

Khi triển khai trên nhiều JVM, mỗi JVM có biến generation riêng nên **không đủ**; muốn fencing thật qua nhiều pod phải dựa vào version/lease của kho lưu trữ chung, được kiểm tra tại nơi thực hiện side effect hoặc transaction.

## 7. Ví dụ Spring + Java 17 hoàn chỉnh: cập nhật không tạo lịch trùng

Ví dụ sau dùng Spring Framework 6.2.14 nhưng **không chờ đồng hồ cron thật**. Thay vào đó, một `RecordingScheduler` override đúng API `schedule(Runnable, Trigger)` để ghi nhận handle và cho phép kiểm thử callback một cách thủ công. Cách này khiến đầu ra có thể dự đoán chính xác:

- Đăng ký lần đầu: tạo một handle.
- Nhận lại cùng cấu hình: không tạo handle thứ hai.
- Thay cron: tạo handle mới, hủy handle cũ.
- Một callback cũ **đến muộn sau khi bị cancel**: generation guard bỏ qua.
- Disable: hủy handle hiện hành, callback tới muộn cũng bị bỏ qua.

Đây là **bài kiểm thử state machine của registration**, không phải một phép đo độ chính xác cron hay hiệu năng của scheduler thật. Phần sau sẽ chỉ ra cách cắm registry vào `ThreadPoolTaskScheduler` production.

### 7.1. Cấu trúc dự án

```text
safe-dynamic-cron/
├── pom.xml
└── src/main/java/demo/DynamicCronReplacementDemo.java
```

Điều kiện: **JDK 17+**, Maven 3.x, Spring Framework **6.2.14**.

### 7.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>safe-dynamic-cron</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.14</version>
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
                    <mainClass>demo.DynamicCronReplacementDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 7.3. src/main/java/demo/DynamicCronReplacementDemo.java

```java
package demo;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

public final class DynamicCronReplacementDemo {

    // Một registry dành cho MỘT logical job.
    // Với nhiều jobs, tạo một slot tương tự cho mỗi job ID.
    static final class Registry implements AutoCloseable {
        private final Object monitor = new Object();
        private final TaskScheduler scheduler;
        private final Runnable business;
        private final Semaphore singleFlight = new Semaphore(1);

        private ScheduledFuture<?> active;
        private String activeCron;
        private ZoneId activeZone;
        private long generation;
        private boolean closed;

        Registry(TaskScheduler scheduler, Runnable business) {
            this.scheduler = Objects.requireNonNull(scheduler);
            this.business = Objects.requireNonNull(business);
        }

        boolean replace(String cron, ZoneId zone) {
            Objects.requireNonNull(cron, "cron");
            Objects.requireNonNull(zone, "zone");

            // Validate trước khi thay đổi trạng thái hiện hành.
            CronExpression.parse(cron);

            synchronized (monitor) {
                if (closed) {
                    throw new IllegalStateException("Registry is closed");
                }
                // Event trùng phải là no-op.
                if (active != null
                        && cron.equals(activeCron)
                        && zone.equals(activeZone)) {
                    return false;
                }

                long candidateGeneration = generation + 1;

                Runnable fenced = () -> {
                    // Check sớm trước khi chiếm permit.
                    synchronized (monitor) {
                        if (closed || generation != candidateGeneration) {
                            return;
                        }
                    }

                    // Không cho hai executions của logical job
                    // cùng chạy trong JVM này.
                    if (!singleFlight.tryAcquire()) {
                        return; // Chủ động skip khi đang bận.
                    }
                    try {
                        // Re-check sau khi chiếm permit.
                        synchronized (monitor) {
                            if (closed || generation != candidateGeneration) {
                                return;
                            }
                        }

                        // Callback có thể bắt đầu trước khi config đổi.
                        // Không thể đảo ngược side effect đã bắt đầu.
                        business.run();
                    } finally {
                        singleFlight.release();
                    }
                };

                // Tạo candidate trước để lỗi đăng ký không hủy
                // lịch đang chạy.
                ScheduledFuture<?> candidate = scheduler.schedule(
                        fenced, new CronTrigger(cron, zone));

                if (candidate == null) {
                    throw new IllegalArgumentException(
                            "Trigger has no next execution");
                }

                ScheduledFuture<?> previous = active;
                active = candidate;
                activeCron = cron;
                activeZone = zone;
                generation = candidateGeneration;

                // Cấm lịch cũ tiếp tục kích hoạt, nhưng không
                // ép interrupt business call đã bắt đầu.
                if (previous != null) {
                    previous.cancel(false);
                }
                return true;
            }
        }

        void disable() {
            synchronized (monitor) {
                generation++;
                ScheduledFuture<?> previous = active;
                active = null;
                activeCron = null;
                activeZone = null;
                if (previous != null) {
                    previous.cancel(false);
                }
            }
        }

        @Override
        public void close() {
            synchronized (monitor) {
                closed = true;
                generation++;
                ScheduledFuture<?> previous = active;
                active = null;
                activeCron = null;
                activeZone = null;
                if (previous != null) {
                    previous.cancel(false);
                }
            }
        }
    }

    // Future mô phỏng: không tạo thread hoặc dùng đồng hồ.
    static final class ManualFuture implements ScheduledFuture<Object> {
        private final Runnable task;
        private boolean cancelled;

        ManualFuture(Runnable task) {
            this.task = task;
        }

        // Gọi cố ý ngay cả khi cancelled, mô phỏng callback cũ
        // đã được lấy khỏi queue trước lúc huỷ.
        void fireEvenIfCancelled() {
            task.run();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean interrupt) {
            if (cancelled) {
                return false;
            }
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("Not used in demo");
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("Not used in demo");
        }
    }

    // Đúng loại Spring TaskScheduler, nhưng fake việc giao task
    // để kết quả kiểm thử không phụ thuộc thời gian.
    static final class RecordingScheduler extends ThreadPoolTaskScheduler {
        final List<ManualFuture> created = new ArrayList<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            ManualFuture future = new ManualFuture(task);
            created.add(future);
            return future;
        }
    }

    public static void main(String[] args) {
        RecordingScheduler scheduler = new RecordingScheduler();
        AtomicInteger calls = new AtomicInteger();
        Registry registry = new Registry(
                scheduler,
                () -> calls.incrementAndGet());

        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

        try {
            boolean first = registry.replace("0 0 9 * * *", zone);
            boolean repeated = registry.replace("0 0 9 * * *", zone);
            System.out.println("firstRegistered=" + first);
            System.out.println("sameConfigNoOp=" + !repeated);
            System.out.println(
                    "handlesAfterDuplicate=" + scheduler.created.size());

            ManualFuture oldHandle = scheduler.created.get(0);
            boolean changed = registry.replace("0 0 10 * * *", zone);
            ManualFuture newHandle = scheduler.created.get(1);

            System.out.println("newRegistered=" + changed);
            System.out.println(
                    "oldCancelled=" + oldHandle.isCancelled());

            // Mô phỏng callback cũ đã dispatch dù handle bị cancel.
            oldHandle.fireEvenIfCancelled();
            System.out.println("runsAfterStale=" + calls.get());

            // Callback của cấu hình mới được phép chạy.
            newHandle.fireEvenIfCancelled();
            System.out.println("runsAfterNew=" + calls.get());

            // Disable; callback đã ở hàng đợi không gây chạy mới.
            registry.disable();
            newHandle.fireEvenIfCancelled();

            System.out.println(
                    "newCancelledAfterDisable="
                            + newHandle.isCancelled());
            System.out.println(
                    "runsAfterDisable=" + calls.get());
            System.out.println(
                    "totalRegistrations=" + scheduler.created.size());
        } finally {
            registry.close();
        }
    }
}
```

### 7.4. Chạy ví dụ

```bash
mvn -q compile exec:java
```

**Standard output dự kiến** từ các logic được kiểm soát trong demo:

```text
firstRegistered=true
sameConfigNoOp=true
handlesAfterDuplicate=1
newRegistered=true
oldCancelled=true
runsAfterStale=0
runsAfterNew=1
newCancelledAfterDisable=true
runsAfterDisable=1
totalRegistrations=2
```

**Tại sao kết quả không cần Thread.sleep?**

- `RecordingScheduler` lưu callback chứ không khởi động worker hoặc tính giờ thực.
- Event cập nhật trùng làm `replace(...)` trả `false` và không gọi `schedule(...)` lần nữa. Vì vậy sau hai lời gọi đầu, số handle vẫn là 1.
- Đăng ký cron mới tạo handle thứ hai và gọi `cancel(false)` cho handle đầu.
- `fireEvenIfCancelled()` là công cụ **cố tình mô phỏng race**, gọi callback cũ dù Future đã đánh dấu canceled. Trong executor thật, cancellation thường chặn các trigger tương lai, nhưng callback đã dispatch có thể không dừng chỉ nhờ cancel.
- Vì generation trong callback cũ không còn khớp `generation` được publish, `runsAfterStale=0`.
- Callback hợp lệ của cấu hình mới tăng số lần gọi lên 1.
- Disable tăng generation, hủy handle hiện hành và khiến callback của lịch mới đến muộn cũng bị chặn. Vì vậy số lần gọi vẫn là 1.

**Giới hạn cần nói rõ:** đây là mô phỏng có chủ đích ở lớp quản lý registration; `ManualFuture` không triển khai một scheduler thực, không dùng clock, không đánh giá cron có tick đúng giờ và không chứng minh external side effects được hủy. Trong production, `TaskScheduler.schedule(...)` được thực hiện bởi bean có lifecycle thật. Phần sau chỉ cách dùng registry tương tự với scheduler thật và cách test concurrency.

## 8. Phân tích độ an toàn của registry: tốt hơn ở đâu, còn giới hạn gì?

Thiết kế ở mục 7 có bốn điểm bảo vệ:

**(1) Validation trước khi thay lịch.** `CronExpression.parse(...)` chạy trước thay đổi handle. Một chuỗi năm trường kiểu Unix hoặc cú pháp không hợp lệ bị từ chối mà lịch hiện hành chưa bị hủy.

**(2) Cập nhật tuần tự bằng synchronized.** Hai config events trong cùng JVM không thể cùng lúc đi qua critical section rồi xuất bản hai handle như hai cấu hình hiện hành.

**(3) Đăng ký candidate trước, publish version sau.** Nếu `schedule(...)` từ chối ngay lúc submit, registry giữ handle cũ. Khi candidate được nhận, nó chỉ có thể gọi business sau khi generation mới được publish. Nếu callback chạy khi còn chưa được publish, nó phải đợi lock và kiểm tra version.

**(4) Hủy handle trước đó và fencing callbacks cũ.** Những callbacks đã bị dispatch nhưng tới muộn có cơ hội tự bỏ qua vì không còn khớp generation. `Semaphore.tryAcquire()` bổ sung cơ chế **single-flight trong JVM** khi có hai executions cạnh tranh.

### Những giới hạn còn lại

- Có **khoảng hai registrations vật lý cùng tồn tại** trong lúc candidate được schedule và trước khi old future bị cancel. Điều này được xử lý bằng version guard, không phải bằng một transaction thật ở scheduler.
- `singleFlight.tryAcquire()` **bỏ qua** một tick khi job khác đang chạy; đây là một chính sách nghiệp vụ cụ thể. Nếu không thể bỏ tick, cần queue/buffering/transaction/job store phù hợp.
- Một callback cũ có thể đã bắt đầu `business.run()` ngay trước khi cấu hình đổi; `cancel(false)` không dừng nó. Single-flight ngăn **hai** business executions cùng chạy trong **một registry/JVM**, nhưng không tự rollback công việc đã bắt đầu.
- Có một cửa sổ cực nhỏ giữa lần kiểm tra generation cuối và bước vào `business.run()`. Nếu cần hợp đồng **“sau khi replace trả về không còn business call phiên bản cũ nào có thể bắt đầu hoặc tiếp tục”**, thiết kế này chưa cung cấp; phải dùng rào chắn/lock bao trùm cả việc thực thi hoặc coordination transactional, chấp nhận chi phí.
- `synchronized` trong ví dụ giữ lock cả khi gọi `scheduler.schedule(...)` và `previous.cancel(false)`. Hãy đảm bảo scheduler implementation không chạy callback đồng bộ rồi đợi lock của chính nó theo cách tạo deadlock. Với `ThreadPoolTaskScheduler` điển hình, scheduling là submit bất đồng bộ; với một implementation tự viết, phải kiểm thử.
- `long generation` có giới hạn số học trong thời gian cực dài, dù thực tế ít khi là vấn đề; nhiều hệ thống dùng version từ DB/UUID hoặc counter theo job lifecycle.
- Registry này bảo vệ **một logical job, trong một JVM**. Nó không bảo vệ hai registry instances trên hai pod.
- `ManualFuture` là mock kiểm tra policy, không phải một model mô phỏng mọi trạng thái `ScheduledFuture`/cancellation trên production.

Cần xác định mức bảo đảm từ yêu cầu nghiệp vụ trước khi chọn chiến lược. Đối với refresh cache có thể chạy lại, single-flight và idempotency có thể đủ. Đối với thanh toán/ghi sổ, yêu cầu mạnh hơn và phải gắn với transaction/database.

## 9. Tích hợp registry với ThreadPoolTaskScheduler thật

Trong ứng dụng Spring, nên có một `ThreadPoolTaskScheduler` bean dùng chung, được container quản lý lifecycle:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class DynamicSchedulingConfiguration {

    @Bean("dynamicScheduler")
    public ThreadPoolTaskScheduler dynamicScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("dynamic-cron-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
```

Đây là **class cấu hình tích hợp**, cần Spring context và component scan/import. `setPoolSize(3)` chỉ là tham số minh họa. Với bean managed, Spring thường khởi tạo/dừng scheduler theo lifecycle; không tạo một `new ThreadPoolTaskScheduler()` riêng cho mỗi update.

**Lợi ích của `setRemoveOnCancelPolicy(true)`:** với ScheduledThreadPoolExecutor hỗ trợ tính năng này, một future bị hủy có thể được loại khỏi delayed queue sớm hơn. Điều đó giảm nguy cơ giữ quá nhiều task đã hủy trong queue khi cấu hình thay đổi liên tục. Nó **không** thay thế việc giữ handle, cancel, generation guard hoặc idempotency.

### Cập nhật qua service Spring

Một service có thể inject scheduler và giữ một `Registry` tương tự:

```java
@Service
public class DynamicReportScheduler {
    private final TaskScheduler scheduler;
    private final ReportService reports;

    // Registry là object có lifecycle riêng,
    // khởi tạo một lần sau khi dependencies đã có.
    private final DynamicScheduleRegistry registry;

    public DynamicReportScheduler(
            @Qualifier("dynamicScheduler") TaskScheduler scheduler,
            ReportService reports) {
        this.scheduler = scheduler;
        this.reports = reports;
        this.registry = new DynamicScheduleRegistry(
                scheduler, reports::refresh);
    }

    public boolean update(String cron, ZoneId zone) {
        return registry.replace(cron, zone);
    }

    public void disable() {
        registry.disable();
    }

    @PreDestroy
    public void shutdownRegistry() {
        registry.close();
    }
}
```

**Đây là snippet kiến trúc**, không phải class độc lập có thể biên dịch ngay: `DynamicScheduleRegistry` là một implementation sản phẩm dựa trên `Registry` mẫu ở mục 7 (đã tách thành class public riêng), `ReportService` là business service, và cần import `@Service`, `@Qualifier`, `@PreDestroy`, `ZoneId`, `TaskScheduler`. Đừng copy snippet này như một dự án hoàn chỉnh mà bỏ định nghĩa những type đó.

### Lưu ý @EnableScheduling

**Programmatic `taskScheduler.schedule(...)` không cần `@EnableScheduling` chỉ để hoạt động**, miễn scheduler bean được tạo/khởi tạo. `@EnableScheduling` chủ yếu đăng ký những bean methods dùng `@Scheduled`.

Không gắn `@Scheduled(cron="...")` vào chính `reports.refresh()` **đồng thời** đăng ký manual `taskScheduler.schedule(reports::refresh, trigger)`, trừ khi thực sự muốn hai nguồn trigger. Nếu cần lịch động, chọn **một nơi sở hữu registration**.

Một phương thức có nhiều annotation `@Scheduled` cũng có thể được kích hoạt bởi các trigger độc lập. Đó là một nguồn duplication ngay cả khi không có listener cập nhật cấu hình.

## 10. Khi có nhiều logical jobs: registry theo ID và ownership

Trong thực tế có thể có hàng trăm jobs: `tenant-A/report`, `tenant-B/report`, `billing/reconcile`. Không dùng một biến `currentFuture` toàn cục cho mọi job; như vậy khi thay lịch A có thể hủy lịch B.

Cấu trúc thường gặp:

```text
Map<LogicalJobId, Slot>
    |
    +-- job-A -> Slot(version=5, cron=..., zone=..., future=...)
    |
    +-- job-B -> Slot(version=2, cron=..., zone=..., future=...)
    |
    +-- job-C -> Slot(disabled, no future)

Mỗi Slot có lock + generation + singleFlight riêng.
```

Các yêu cầu quản trị:

1. Tạo `Slot` theo **logical ID đã chuẩn hóa**; không trộn display name hoặc tenant ID không ổn định.
2. Trong một `Slot`, serialize cập nhật cron, zone, enable/disable.
3. Trong các slot khác nhau, cho phép cập nhật độc lập để tránh một lock toàn cục làm nghẽn hệ thống.
4. Dùng một executor/scheduler bean chung hoặc một số scheduler theo chức năng; **không tự tạo một scheduler cho mỗi job** không có giới hạn.
5. Khi xóa logical job, hủy active handle và vô hiệu hóa callbacks stale.
6. Cẩn thận việc **xóa Slot khỏi map**: nếu callback cũ đang chạy mà một Slot mới cùng job ID được tạo lại với semaphore khác, có thể xuất hiện hai executions đồng thời. Có thể cần tombstone/generation theo job ID, lifecycle cleanup an toàn hoặc coordination mạnh hơn.
7. Đặt upper bound cho số job/schedules trong RAM; nếu để các ID tùy ý được đăng ký vô hạn, bộ nhớ và queue sẽ tăng.
8. Nếu cần job tồn tại sau restart, lưu cấu hình ở DB và khôi phục có kiểm soát.

**Tránh vấn đề ABA:** job A bị disable, xóa slot, rồi đăng ký lại đúng job ID với generation khởi đầu từ 1. Callback rất cũ có thể trùng token nếu logic không gắn với slot identity. Dùng token duy nhất cho mỗi lifecycle hoặc giữ state fencing bền vững phù hợp, không chỉ tái sử dụng counter cục bộ.

## 11. Cấu hình thay đổi đồng thời: last-write-wins không phải lúc nào cũng đúng

Giả sử hai sự kiện có thứ tự nghiệp vụ:

```text
t1: version 42 -> cron mỗi 5 phút
t2: version 43 -> cron mỗi 15 phút
```

Do độ trễ mạng, listener có thể nhận version 43 trước rồi **version 42 tới muộn**. Chỉ có synchronized trong process sẽ tuần tự hóa xử lý nhưng vẫn có thể khiến lịch **quay lại cấu hình cũ**.

Cần phân biệt:

- **Mutual exclusion**: chỉ một thread sửa registry state tại một thời điểm.
- **Ordering**: version nào được phép thắng?
- **Durability**: config đã commit vào DB chưa?
- **Idempotency**: sự kiện trùng có tạo thêm lịch không?

Một update API tốt có thể mang `revision` (monotonic version) từ nguồn cấu hình:

```text
if incomingRevision < currentRevision:
    IGNORE_STALE

if incomingRevision == currentRevision:
    compare effective spec; nếu giống, NO_CHANGE;
    nếu khác, CONFLICT / dữ liệu nguồn không nhất quán

if incomingRevision > currentRevision:
    validate spec; attempt replacement; publish revision
```

Đây là **pseudocode**, không phải quy tắc do Spring tự cung cấp. Nếu version được sinh riêng ở từng pod mà không đồng bộ, không thể dùng nó làm thứ tự toàn cluster. Version nên được tạo ở **nguồn sự thật** như DB transaction/config store hoặc từ một revision sequence đáng tin.

Một điểm tinh tế: nếu candidate schedule thất bại, không nên cập nhật `currentRevision` như thể lịch mới đã được áp dụng thành công, trừ khi state machine có trạng thái **PENDING/FAILED** và retry có kiểm soát. Nếu không, sự kiện retry cùng version có thể bị loại nhầm là duplicate trong khi lịch chưa từng đổi.

## 12. Disable, cập nhật lỗi và rollback hành vi

Một hệ thống quản trị cần ít nhất ba thao tác có semantics rõ:

| Thao tác | Trường hợp | Hành vi registry |
| --- | --- | --- |
| `enable(spec, revision)` | Job chưa có lịch | Validate, schedule, publish handle/version |
| `replace(spec, revision)` | Job đã có lịch, spec mới | Tạo candidate, publish, cancel old |
| `disable(revision)` | Job phải dừng các trigger mới | Invalidate generation, cancel active, giữ trạng thái disabled |

### Disable không đồng nghĩa dừng mọi business execution

Nếu task đã bắt đầu, `cancel(false)` cho phép nó tiếp tục. Nếu policy cần dừng work đang chạy, phải có cancellation token/timeout của dependency. Nếu task đã gửi request thanh toán hoặc đã commit DB, không thể “thu hồi” side effect chỉ bằng việc disable lịch.

Vì thế API có thể trả:

```text
scheduleStatus=DISABLED
inFlightExecutions=1
noNewTriggers=true
```

Đây là những trạng thái **có thể đồng thời đúng**. Không nói với người quản trị “đã dừng hoàn toàn” chỉ vì handle `isCancelled()==true`.

### Khi cron mới không hợp lệ

Dùng `CronExpression.parse(expression)` trước khi làm thay đổi registry. Không dựa vào `String.split(" ")` tự viết hoặc kiểm tra chỉ số lượng trường, vì Spring hỗ trợ ký hiệu `L`, `W`, `#` và macro.

### Khi scheduler từ chối registration mới

Có thể giữ lịch cũ vì candidate chưa được publish. Log structured event gồm job ID, incoming revision, cron, zone, nguyên nhân rejection và trạng thái cũ. Không tự động spam retry liên tục nếu scheduler đang shutdown/overloaded; cần backoff, retry budget và alert.

### Khi không còn lần chạy tiếp theo

`TaskScheduler.schedule(Runnable, Trigger)` có thể trả `null` khi Trigger không chỉ ra lần khớp tương lai. Không dùng `null` như một active handle. Định nghĩa policy: từ chối bản cấu hình, đưa job vào trạng thái `NO_NEXT_FIRE` hoặc disable theo nghiệp vụ. Không âm thầm xóa lịch cũ chỉ vì candidate không có lần kế tiếp.

### Trường hợp hủy thất bại hoặc đang chạy

`ScheduledFuture.cancel(...)` trả boolean, nhưng giá trị này không nói hết execution lifecycle. Nếu future đã kết thúc, đang chạy hoặc bị hủy từ trước, kết quả có thể khác nhau. Sau replace, generation guard là lớp bảo vệ callback cũ đến muộn; business idempotency tiếp tục là lớp quan trọng khi side effect có thể xảy ra.

## 13. Thay đổi cron và ZoneId: cùng chuỗi cron vẫn có thể là lịch khác

Cron của Spring có sáu trường (giây, phút, giờ, ngày trong tháng, tháng, thứ trong tuần). Ví dụ:

```text
0 0 9 * * MON-FRI
```

nghĩa là 09:00 thứ Hai–Sáu **theo múi giờ được dùng để diễn giải**, không có nghĩa 09:00 UTC với mọi hệ thống.

So sánh hai cấu hình:

```text
A: cron = "0 0 9 * * MON-FRI"
   zone = "Asia/Ho_Chi_Minh"

B: cron = "0 0 9 * * MON-FRI"
   zone = "Europe/Berlin"
```

Cron text giống nhau, nhưng `Instant` kích hoạt khác nhau và có thể thay đổi theo DST ở Berlin. Nếu registry chỉ `equals(cron)` rồi bỏ qua ZoneId, việc đổi timezone **không được áp dụng**. Đó là lỗi không cập nhật lịch; không giống duplication, nhưng cùng bắt nguồn từ quản lý config thiếu đầy đủ.

**Thực hành tốt:**

- Dùng `ZoneId` rõ ràng, tránh phụ thuộc timezone mặc định của pod.
- Validate `CronExpression.parse(...)` cùng timezone business.
- Kiểm tra vài lần kích hoạt kế tiếp bằng `CronExpression.next(ZonedDateTime)` để xác nhận ý định.
- Với DST gap/overlap, xác định policy khi giờ địa phương không tồn tại hoặc lặp lại.
- Nếu yêu cầu “thứ Hai–Sáu trừ ngày nghỉ lễ”, cron không tự hiểu business calendar; phải kiểm tra trong logic nghiệp vụ.
- Khi cấu hình đổi, ghi nhận cả `oldCron/newCron` và `oldZone/newZone` mà không log dữ liệu nhạy cảm.
- Không đổi chế độ `CronTrigger.forLenientExecution` sang `forFixedExecution` chỉ để “sửa trùng”: nó còn đổi semantics missed ticks/catch-up.

### Lenient vs fixed execution

Spring 6.1.3+ có lựa chọn `CronTrigger.forLenientExecution(...)` và `CronTrigger.forFixedExecution(...)`. Dạng lenient có thể bỏ qua những lịch đã lỡ khi task dài, còn dạng fixed có thể chạy bù các mốc cũ. Đây là quyết định về **trigger execution semantics**, khác với duplicate registration.

Nếu thay cấu hình mà tự tạo một trigger mới, lịch tính từ thời điểm nào (completion gần nhất, scheduled timestamp, current time) sẽ tùy trigger và cách đăng ký. Không tự giả định một “CronTrigger mới” giữ nguyên lịch sử của trigger cũ.

## 14. Đừng để @Scheduled và đăng ký động quản lý cùng một job

Một ví dụ có thể gây duplicate:

```java
@Component
public class ReportJob {
    @Scheduled(cron = "0 */5 * * * *")
    public void refresh() {
        reportService.refresh();
    }
}
```

Trong nơi khác:

```java
taskScheduler.schedule(
        reportJob::refresh,
        new CronTrigger("0 */10 * * * *"));
```

Đây là hai snippet tích hợp. Nếu Spring đã đăng ký `@Scheduled`, manual registration **không hủy** registration từ annotation. Kết quả có thể tồn tại hai triggers cùng gọi `refresh()`.

**Giải pháp:** chọn một owner cho logical job:

- Nếu cron hiếm khi đổi và restart là chấp nhận được: dùng `@Scheduled(cron = "${jobs.report.cron}")` với lifecycle do Spring quản lý.
- Nếu cần cập nhật thật sự trong runtime: chuyển job sang programmatic scheduler/registry, **không đồng thời giữ annotation cho cùng invocation**, trừ khi có ý định điều phối hai lịch độc lập.
- Nếu dùng `ScheduledTaskRegistrar`/`SchedulingConfigurer` để đăng ký custom trigger, quản lý handle `ScheduledTask` và hủy đúng loại handle. `ScheduledTask.cancel(boolean)` là API riêng của registrar-managed task.
- Với context refresh hoặc nhiều bean scopes, tránh khởi tạo bean chứa `@Scheduled` nhiều lần ngoài ý muốn.

**Một property đổi không tự động thay thế registration đã tồn tại.** Với `@Scheduled` và property placeholder, Spring thường resolve ở thời điểm đăng ký; cập nhật config server về sau không mặc nhiên tái đăng ký trigger. Muốn hot reload, ứng dụng phải có explicit rescheduling/lifecycle hoặc một công cụ scheduling có hỗ trợ dynamic reload.

Lưu ý thuộc tính đặc biệt `Scheduled.CRON_DISABLED` có giá trị `"-"` ở hạ tầng `@Scheduled`. Đây không phải một cron expression 6 trường để đưa nguyên vào `CronExpression.parse("-")`; registry programmatic phải xử lý disable như một command riêng.

## 15. Tránh chạy chồng business executions sau khi thay cron

Hủy lịch cũ ngăn nhiều lần kích hoạt trong tương lai, nhưng một execution cũ có thể vẫn đang gọi DB hoặc remote API. Thay đổi config trong lúc có in-flight task cần chính sách thực thi.

### 15.1. Policy SKIP_IF_RUNNING

Dùng `Semaphore.tryAcquire()` hoặc `AtomicBoolean.compareAndSet(...)` để chỉ cho một execution qua business boundary tại một thời điểm:

```java
if (!singleFlight.tryAcquire()) {
    skippedMetric.increment();
    return;
}
try {
    business.run();
} finally {
    singleFlight.release();
}
```

Đây là snippet có `singleFlight` và `skippedMetric` của ứng dụng. Nó bỏ lần kích hoạt mới nếu lần cũ chưa hoàn tất, giảm overlap nhưng có thể **bỏ mất tick**. Chỉ dùng khi phù hợp với nghiệp vụ.

### 15.2. Policy QUEUE_NEXT

Nếu không được bỏ tick, cần queue với capacity hữu hạn, admission control và cách ghi nhận/retry chính xác. Một `Semaphore.acquire()` blocking trong scheduler worker có thể gây deadlock/starvation nếu pool nhỏ; tránh dùng blocking lock vô thời hạn chỉ để “không mất tick”.

### 15.3. Policy SERIALIZE_BY_JOB

Dùng khóa per job, có bounded timeout hoặc tài nguyên worker chuyên biệt. Nếu cập nhật lịch phải chờ job cũ hoàn tất, cần làm rõ SLA của API cấu hình: nó có thể đợi vài giây/phút. Không giữ khóa toàn cục của mọi jobs trong khi một API bên ngoài đang timeout.

### 15.4. Policy INTERRUPT_AND_REPLACE

Có thể thử `cancel(true)` để yêu cầu interrupt task đang chạy. Tuy nhiên:

- Không phải mọi JDBC/HTTP client phản ứng với thread interrupt.
- Spring/JDK future có nhiều lớp adapter; semantics interrupt cụ thể cần kiểm tra.
- Side effects đã commit không bị rollback.
- Callback cần quan sát interrupt/cancellation token và cleanup trong `finally`.
- Nếu nghiệp vụ yêu cầu tính nhất quán, transaction/idempotency vẫn là công cụ chính.

### 15.5. Idempotency bảo vệ sau cùng

Ngay cả khi có một instance, một worker có thể hoàn tất action rồi crash trước khi ghi trạng thái. Khi restart, action có thể chạy lại. Với hệ nhiều pod, duplicate trigger càng dễ xảy ra.

Dùng idempotency key dựa trên **logic nghiệp vụ**, ví dụ `(jobId, scheduledBusinessWindow)` với unique constraint. Không dùng mỗi `UUID.randomUUID()` ở lần chạy làm khóa chống trùng: hai lần chạy duplicate sẽ sinh hai UUID khác nhau và không thể nhận diện là cùng một tác vụ.

Chọn `scheduledBusinessWindow` cần cẩn thận với DST, missed ticks và những lần chạy bị dồn. Nếu side effect là chuyển tiền/gửi invoice, hợp đồng “chỉ một lần theo business event” quan trọng hơn một timestamp tùy ý từ scheduler.

## 16. Multi-instance: một ScheduledFuture trên mỗi JVM vẫn có thể chạy trùng

Giả sử một deployment có ba pod:

```text
                    Shared configuration / DB
                             |
           +-----------------+-----------------+
           |                 |                 |
         Pod A             Pod B             Pod C
      registry A        registry B        registry C
       future A          future B          future C
           |                 |                 |
          tick              tick              tick
           |                 |                 |
           +------ cùng chạy logical job ------+
```

Tất cả các registry đều có thể đúng trong phạm vi JVM riêng: mỗi nơi chỉ giữ một active future. Tuy nhiên **toàn cluster vẫn có ba nguồn kích hoạt**.

Muốn một logical job chỉ được thực thi một lần trên cluster, cần quyết định:

- **Single leader scheduling:** chỉ leader giữ active registration. Nếu leader chết, cơ chế lease/leader election có thể chuyển quyền.
- **All nodes schedule + distributed claim:** mọi pod cùng được tick nhưng chỉ một pod giành quyền xử lý job/window thông qua DB transaction, lease hoặc khóa phân tán.
- **Durable scheduling system:** Quartz với JDBC job store và cluster configuration đúng, hoặc job queue/workflow engine có claim/ack/retry.

### Fencing token ở cấp DB

Một leader cũ có thể mất lease nhưng vẫn đang thực thi. Leader mới tiếp quản, khiến cả hai cùng có công việc in-flight. Vì vậy **chỉ khóa phân tán một thời điểm không đủ** nếu side effects cần loại trừ tuyệt đối; phải có fencing/version tại nơi nhận write, hoặc idempotency/transaction ràng buộc nghiệp vụ.

Ví dụ:

```text
DB job lease:
  owner=pod-B
  leaseGeneration=28

Pod A (generation=27) gọi write:
  kiểm tra generation ở DB -> REJECT_STALE

Pod B (generation=28) gọi write:
  kiểm tra generation ở DB -> ACCEPT nếu còn quyền
```

Đây là ví dụ mô hình, không phải cơ chế được Spring TaskScheduler tự cung cấp. Một call HTTP tới external service không hỗ trợ idempotency/fencing có thể vẫn gây duplicate dù ứng dụng có lease ở DB.

### Điều quan trọng khi restart

`ScheduledFuture<?>` là **handle trong bộ nhớ JVM**. Restart làm mất registry và pending schedules cục bộ. Khi khởi động lại, cần đọc **cấu hình có hiệu lực** từ nguồn sự thật, đăng ký một lần có kiểm soát, và xử lý missed run theo chính sách nghiệp vụ.

Không serialize `ScheduledFuture` vào DB rồi mong khôi phục được cùng một executable task ở process mới. Lưu **job definition + status + revision** mới là hướng phù hợp.

## 17. Observability: làm sao phát hiện có lịch chồng trước khi khách hàng báo lỗi?

Nếu chỉ log “Cron updated to X”, hệ thống có thể che giấu registration đã bị leak. Cần metric theo hai trục: registrations và executions.

| Metric/Log | Giá trị |
| --- | --- |
| `activeRegistrationsByJob` | Số registrations ứng dụng biết cho mỗi logical job; mục tiêu thường là 0 hoặc 1 |
| `scheduleReplaceCount` | Có thay cấu hình thật không? |
| `scheduleDuplicateEventCount` | Bao nhiêu event trùng được bỏ qua? |
| `scheduleRejectedCount` | Bao nhiêu candidate không được scheduler nhận? |
| `oldHandleCancelledCount` | Số handle cũ đã được yêu cầu hủy |
| `staleCallbackSkippedCount` | Bao nhiêu callbacks cũ bị generation fence chặn |
| `concurrentExecutionSkippedCount` | Bao nhiêu tick bị single-flight từ chối |
| `inFlightExecutionsByJob` | Số executions thật đang chạy |
| `executionLatency/Duration` | Độ trễ và thời gian nghiệp vụ |
| `effectiveCron/effectiveZone/revision` | Cấu hình được publish, không phải cấu hình mới chỉ vừa nhận |
| `lastStart/lastSuccess/lastFailure` | Theo dõi sức khỏe job |
| `clusterClaimFailureCount` | Đã có pod khác giành quyền xử lý chưa? |

**Lưu ý:** `activeRegistrationsByJob=1` là trạng thái registry tin rằng mình quản lý, không tự chứng minh underlying scheduler không còn một handle đã leak từ code khác hoặc pod khác. Có thể cần audit nơi gọi `schedule`, kiểm tra bean instances và integration tests.

Với `ThreadPoolTaskScheduler`, `setRemoveOnCancelPolicy(true)` giúp queue cleanup, nhưng một queue có ít phần tử cũng không khẳng định **không có duplicate business effects**. Đo cả end-to-end nghiệp vụ.

Log hữu ích:

```text
jobId=report-refresh
event=CONFIG_REPLACED
oldRevision=41
newRevision=42
oldCron="0 */5 * * * *"
newCron="0 */10 * * * *"
zone="Asia/Ho_Chi_Minh"
oldCancelRequested=true
activeLocalRegistrations=1
```

Không log credential, payload riêng tư hoặc đầy đủ dữ liệu nghiệp vụ nhạy cảm vào tags có cardinality cao.

## 18. Kế hoạch kiểm thử: trùng event, đua cập nhật và task đang chạy

### 18.1. Test idempotency khi cùng spec được gửi lại

Đăng ký cron A, gọi update cron A một lần nữa. Kiểm tra:

- Từ đầu đến cuối chỉ có **một handle** được tạo.
- Không cancel và đăng ký lại khi cấu hình không đổi.
- Version không tăng nếu sự kiện thực sự là no-op.
- Không reset next-run chỉ do listener nhận bản sao cùng nội dung.

Đây là bài test có giá trị hơn việc chỉ so hai chuỗi cron: nó xác nhận **lifecycle registration**.

### 18.2. Test đổi A → B

- Đăng ký A và lấy handle A.
- Đăng ký B khác cron/zone.
- Kiểm tra handle A bị hủy và handle B được publish.
- Chạy callback A đến muộn; verify version guard bỏ qua.
- Chạy callback B; verify business action đúng một lần.

Ví dụ mục 7 làm chính xác nhóm kiểm tra này mà không cần clock thật.

### 18.3. Test concurrent updates

Dùng `CountDownLatch` hoặc barrier để hai threads cùng gọi `replace(A)`/`replace(B)`. Sau khi cả hai hoàn tất:

- Có đúng **một active handle** tại registry.
- Các handle cũ bị cancel.
- Không có callback stale nào vượt generation guard sau publication.
- Với revision sequence, cấu hình có **revision cao nhất** thắng, không phụ thuộc thứ tự scheduler của OS.

Không dùng `Thread.sleep(100)` rồi đoán rằng listener B luôn chạy sau A.

### 18.4. Test scheduler rejection

Dùng scheduler fake có thể ném `TaskRejectedException` khi candidate mới được submit:

- Lịch cũ vẫn tồn tại và chưa bị hủy nếu sử dụng chiến lược schedule-then-publish.
- Registry không công bố revision mới là active.
- Error được báo rõ; retry có chính sách.
- Không có một active handle giả được lưu từ candidate thất bại.

### 18.5. Test task cũ đang chạy

Giữ business callback A bằng latch, gọi replace B, sau đó cho B có tick. Kiểm chứng:

- `cancel(false)` không được coi là đã dừng A.
- Với `singleFlight.tryAcquire()`, tick B bị skip khi A đang giữ permit.
- Khi thả A, một tick B mới có thể chạy.
- Không có hai business executions đồng thời trong **cùng registry**.
- Nếu yêu cầu không được bỏ tick, cần test queue/retry policy khác.

### 18.6. Test disable và restart

Disable khi còn handle; callback đã dispatch bị bỏ qua. Khi restart, đọc cấu hình từ nguồn thật và đăng ký đúng một lần. Với nhiều pod, test duplicate claiming và idempotency riêng.

### 18.7. Test bằng scheduler thật

Sau unit test fake, viết integration test với `ThreadPoolTaskScheduler` thực, cron ngắn chẳng hạn mỗi giây và `CountDownLatch.await(timeout)`. Cần xác nhận callback có thực chạy, cancellation ngăn các tick sau, và shutdown dọn threads. Không dùng một lần timing ngắn để kết luận “không bao giờ trùng”: phải kiểm thử lifecycle, workload và trường hợp race.

## 19. Sai lầm phổ biến và hướng sửa

**Sai lầm 1: gọi `schedule(...)` mỗi khi property thay đổi mà không lưu handle.** Sửa bằng registry theo logical job ID, có active `ScheduledFuture<?>` và thao tác cancel.

**Sai lầm 2: dùng `@Scheduled` và đăng ký manual cho cùng method.** Sửa bằng một owner cho cùng logical job; tránh hai infrastructure triggers độc lập.

**Sai lầm 3: chỉ ghi `currentCron` nhưng không so zone, enabled, semantics.** Sửa bằng effective schedule specification đầy đủ.

**Sai lầm 4: hai listener cùng kiểm tra `future == null` mà không đồng bộ.** Sửa bằng synchronized, per-job lock hoặc một state machine tuần tự hóa cập nhật.

**Sai lầm 5: cancel old trước khi validate new.** Có thể mất lịch khi cấu hình sai hoặc scheduler reject. Validate trước và cân nhắc schedule candidate trước publication.

**Sai lầm 6: cho rằng cancel(false) ngắt business task đang chạy.** Nó không yêu cầu interrupt. Cần single-flight, cancellation-aware dependency hoặc đợi hoàn tất theo policy.

**Sai lầm 7: cho rằng `isCancelled()==true` đảm bảo không có side effect trùng.** Callback đã bắt đầu/đã gửi request vẫn có thể tiếp tục. Cần idempotency, transaction và fencing tại nơi commit.

**Sai lầm 8: xử lý config version theo thứ tự event arrival.** Event cũ có thể đến muộn. Dùng revision có nguồn gốc đáng tin và bỏ stale revisions.

**Sai lầm 9: tắt lịch bằng cron string không hợp lệ.** Với manual scheduler, disable là lifecycle command; đừng đưa `"-"` vào `CronExpression.parse` mà tưởng đó là syntax cron.

**Sai lầm 10: tự tạo scheduler mới cho mỗi lần thay cron.** Handle cũ thuộc scheduler cũ vẫn có thể chạy, còn pool/thread bị leak. Dùng singleton managed scheduler và close đúng lifecycle.

**Sai lầm 11: xóa slot khỏi map ngay khi disable và tái dùng job ID.** Callback từ lifecycle cũ có thể chạy với state cũ. Thiết kế tombstone/generation hoặc job identity đủ mạnh.

**Sai lầm 12: gán `singleFlight` cho toàn bộ ứng dụng.** Một job chậm có thể khiến mọi job khác bị skip. Dùng guard per logical job hoặc phân nhóm phù hợp với yêu cầu isolation.

**Sai lầm 13: chặn scheduler thread bằng `Future.get()` để đợi job cũ dừng.** Có thể làm backlog/timeout, thậm chí deadlock với pool nhỏ. Dùng explicit lifecycle/quiescence policy với timeout, không block vô hạn ở nơi kích hoạt.

**Sai lầm 14: cho rằng một handle/JVM là exactly-once toàn cluster.** Nhiều pod vẫn có registrations riêng. Cần distributed claim, leader election và idempotency.

**Sai lầm 15: dùng fixed cron trigger rồi tưởng change config giữ nguyên mọi missed tick.** Thay `CronTrigger` có thể đổi mốc tính lần kế tiếp. Kiểm tra lenient/fixed semantics, timezone/DST và yêu cầu catch-up.

**Sai lầm 16: ignore việc task cũ đang chạy khi rollback config.** Revert về cron A không làm business action B đã bắt đầu tự biến mất. Track in-flight executions và quan hệ revision.

## 20. Checklist production và tài liệu tham khảo

### Checklist trước khi bật hot reload lịch động

1. **Có logical job ID ổn định?** Một ID có thể ánh xạ tối đa một active registration theo từng owner.
2. **Một nơi sở hữu lịch?** Không cùng lúc dùng `@Scheduled` và manual registration ngoài ý định.
3. **Có đọc đủ effective spec?** Cron, zone, enabled, semantics, revision.
4. **Validate trước khi mutate?** Dùng `CronExpression.parse`, kiểm tra timezone và policy.
5. **Update có idempotent?** Event trùng không tạo thêm handle, không thay đổi next-run.
6. **Có đồng bộ cập nhật?** Per-job lock hoặc state machine serialization.
7. **Khi candidate schedule thất bại?** Lịch cũ còn hiệu lực, error được báo và retry có kiểm soát.
8. **Handle cũ có bị cancel?** Chỉ sau khi candidate mới được chấp nhận và publish.
9. **Có generation fencing?** Callback stale kiểm tra version trước khi đi tới nghiệp vụ.
10. **Có execution overlap policy?** Skip, serialize, queue hoặc parallel có giới hạn.
11. **Có idempotency?** Đặc biệt với side effects, retries và multi-instance.
12. **Có revision ordering?** Event đến muộn không ghi đè config mới.
13. **Disable có semantics rõ?** Không còn trigger mới khác với không còn in-flight task.
14. **Có lifecycle cleanup?** Hủy handles, đóng scheduler bean, không tạo pool mới mỗi event.
15. **Có kiểm thử concurrency và race?** Callback stale, rejection, callback cũ blocked, restart.
16. **Có quan sát active registrations/executions?** Metrics, log versions, rejected updates.
17. **Có bảo vệ khi scale nhiều pod?** Lease/claim/fencing ở shared store khi cần.
18. **Có xử lý DST/zone/missed ticks?** Cron schedule có thể đổi ý nghĩa theo timezone.
19. **Có giới hạn số lịch động?** Queue và registry không tăng vô hạn.
20. **Có rollout/rollback plan?** Không rollback dữ liệu hay công việc đã commit bằng cách chỉ hủy Future.

### Tài liệu chính thức

1. [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskScheduler`, `Trigger`, `CronTrigger`, cảnh báo multiple `@Scheduled` registrations.
2. [Spring Framework 6.2 – TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html): `schedule(Runnable, Trigger)`, `ScheduledFuture`, trường hợp trả `null` và rejection.
3. [Spring Framework 6.2 – CronTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): cron, zone, lenient/fixed execution.
4. [Spring Framework 6.2 – CronExpression Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html): validate expression và tính thời điểm kế tiếp.
5. [Spring Framework 6.2 – ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): pool, remove-on-cancel, lifecycle, cancellation.
6. [Spring Framework 6.2 – ScheduledTask Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/config/ScheduledTask.html): handle của registrar-managed scheduled tasks và `cancel(boolean)`.
7. [Spring Framework 6.2 – Scheduled annotation Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): cron, zone, multiple annotations và giá trị `CRON_DISABLED`.
8. [Java SE 17 – ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): completion/cancellation của future.
9. [Java SE 17 – Semaphore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html): giới hạn một execution in-flight.
10. [Java SE 17 – ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): registry đa job và thao tác theo key.

### Kết luận

Để **tránh đăng ký chồng lịch khi thay đổi cấu hình**, đừng gọi `schedule(...)` rồi quên handle cũ. Cần một owner cho mỗi logical job, lưu `ScheduledFuture<?>`, so sánh cấu hình có hiệu lực, cập nhật tuần tự, validate candidate, publish registration mới rồi hủy registration trước đó. **Generation fencing** giúp chặn callback cũ đến muộn; **single-flight/idempotency** giúp chống chạy chồng và tác động trùng ở tầng nghiệp vụ. Nếu có nhiều pod, phải đưa coordination lên shared store hoặc durable scheduler; một khóa và một Future trong JVM không thể thay thế cơ chế đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-scheduling-009">Kiểm soát tính nhất quán khi nhiều luồng cùng cập nhật lịch</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm soát tính nhất quán khi nhiều luồng cùng cập nhật lịch

> **Module:** Spring Framework – concurrency  
> **Nhóm:** Lịch động và Trigger  
> **Mục tiêu:** Thiết kế một registry lịch động với `TaskScheduler` sao cho hai hoặc nhiều luồng cùng tạo, thay đổi, hủy lịch không gây **lost update**, **ghost schedule**, hủy nhầm lịch mới hoặc callback cũ tác động lên cấu hình đã thay thế. Biết khi nào dùng khóa, phiên bản/CAS, fencing token, state bền vững, và vì sao `ScheduledFuture.cancel()` không phải bảo đảm hoàn tác công việc đang chạy.

## 1. Một lịch động có hai phần trạng thái

Với `@Scheduled`, lịch được khai báo tương đối ổn định khi Spring đăng ký bean. Với **lịch động**, code có thể gọi `TaskScheduler.schedule(...)`, `scheduleAtFixedRate(...)`, `scheduleWithFixedDelay(...)` hay `schedule(Runnable, Trigger)` tại runtime. Sau mỗi lần đăng ký, ứng dụng có thể giữ `ScheduledFuture<?>` để hủy hoặc quan sát trạng thái.

Một job `nightly-report` thực tế có **hai lớp trạng thái**:

- **Trạng thái mong muốn**: business job ID, cron/zone hoặc deadline, enabled/disabled, revision, ai cập nhật cuối cùng, next due, quy tắc retry.
- **Trạng thái thực thi trong JVM**: `ScheduledFuture` hiện tại, timer nằm trong scheduler queue, callback có thể đang chạy và executor có thể đang shutdown.

Sai lầm là coi `Map<String, ScheduledFuture<?>>` vừa là cấu hình nghiệp vụ, vừa là cam kết cuối cùng rằng chỉ có một lần thực thi. Map chỉ giữ **tham chiếu**; nó không tự đồng bộ các bước `read -> cancel -> schedule -> put`, cũng không kiểm soát callback đã bắt đầu.

**Mục tiêu consistency trong một JVM** thường là: một job ID có đúng **một lịch đang được coi là hiện hành**, mọi thay đổi có thứ tự rõ, yêu cầu dùng revision cũ bị từ chối, và callback mang generation cũ không được tiếp tục như một lịch mới.

## 2. Race điển hình: hai admin cập nhật cùng một job

Giả sử `jobs.get("report")` chứa lịch `oldFuture`. Hai request xử lý đồng thời:

~~~text
Luồng A: đổi cron thành 09:00         Luồng B: đổi cron thành 10:00
------------------------------        ------------------------------
old = jobs.get("report")              old = jobs.get("report")
old.cancel(false)                     old.cancel(false)
newA = scheduler.schedule(09:00)      newB = scheduler.schedule(10:00)
jobs.put("report", newA)               jobs.put("report", newB)

Kết quả có thể:
  jobs["report"] trỏ newB
  newA VẪN còn được scheduler giữ và có thể chạy lúc 09:00
  newB cũng có thể chạy lúc 10:00
~~~

Đó là một **ghost schedule**: lịch A không còn handle trong map để service dễ hủy, nhưng timer vẫn tồn tại. Thứ tự `put` không đảm bảo `cancel` của lần trước bao phủ cả những task đã được nộp sau đó.

Còn một race nguy hiểm khi stop:

~~~text
A: đọc handle cũ H1
B: replace H1 bằng H2
A: jobs.remove(id)      // xóa tham chiếu H2 mà không kiểm tra
A: H1.cancel(false)
=> H2 vẫn chạy, registry lại cho rằng job đã dừng
~~~

Vấn đề không nằm ở việc `TaskScheduler` không thread-safe. Nó là **chuỗi thao tác nhiều bước của ứng dụng chưa được đồng bộ và không có định danh phiên bản**. Cách sửa phải được đặt ở registry/job-state layer.

## 3. Những invariant cần ghi ra trước khi viết code

Một thiết kế đúng nên có các bất biến có thể kiểm tra bằng test:

1. **One current configuration per job ID:** tại một thời điểm quan sát được sau một thao tác thành công, registry có một revision hiện hành với tối đa một handle được ghi nhận cho ID.
2. **No lost update:** hai yêu cầu cùng dựa trên revision `r` không thể đều báo thành công rồi âm thầm ghi đè. Chỉ một yêu cầu có quyền chuyển `r -> r+1`; yêu cầu còn lại nhận conflict và có thể đọc trạng thái mới.
3. **Stop is version-aware:** yêu cầu dừng revision `r` không được dừng nhầm lịch đã thay bằng revision `r+1`.
4. **No ABA:** nếu job bị stop rồi tạo lại, revision không quay về giá trị cũ khiến callback lỗi thời tưởng mình là hiện hành.
5. **Scheduling rejection preserves prior state:** nếu scheduler không nhận timer mới, đừng đánh dấu config mới đã áp dụng hoặc hủy vô điều kiện timer cũ.
6. **Callback checks ownership/generation:** timer cũ bắt đầu **sau** khi revision thay đổi phải có cơ chế tự bỏ qua.
7. **Cancel does not imply abort:** callback đã chạy có thể hoàn tất side effect sau khi bị stop; business layer vẫn cần idempotency, transaction/fencing hoặc cơ chế ngăn ghi stale.
8. **Cluster boundary explicit:** `synchronized`/lock trong JVM không đồng bộ với các replica khác.

Cụm từ “một current handle” là bất biến của **registry**, không phải lời hứa rằng không còn bất kỳ callback cũ **in-flight** nào. Phải phân biệt rõ hai mức này ngay từ lúc xác định API.

## 4. Mô hình trạng thái: revision và handle không thể thiếu

Một registry một JVM có thể biểu diễn logic:

~~~text
ScheduleSpec:
  jobId            // ví dụ nightly-report
  cron / zone / at  // mô tả immutable của lịch
  revision          // số phiên bản tăng đơn điệu
  status            // ENABLED | DISABLED
  changedAt         // audit
  changedBy         // audit theo chính sách bảo mật

RuntimeSlot:
  jobId
  revision          // đúng revision đã nộp cho scheduler
  scheduledFuture   // handle cục bộ trong JVM
~~~

**`revision` phải là một phần của hợp đồng update**, không chỉ là counter để in log. Chẳng hạn client đọc revision `7`, gửi `replace(jobId, expectedRevision=7, newSpec)`. Nếu server đã ở revision `8`, không được báo “đã cập nhật thành công” chỉ vì `Map.put` trả về một handle.

Sau khi cancel, **không reset revision về 0**. Có thể giữ tombstone `DISABLED` với revision `9`. Nếu xóa cả counter rồi tạo lại job ID cùng revision cũ, timer lỗi thời có thể qua được bước so sánh đơn giản: đây là biến thể của vấn đề **ABA**.

Với job chạy trên nhiều instance, revision còn phải được lưu ở một nguồn sự thật bền vững, ví dụ một dòng trong DB có unique key theo `job_id`. `ScheduledFuture` chỉ là runtime handle và không thể serialize thành job durable để tái tạo sau restart.

## 5. Vì sao ConcurrentHashMap chưa đủ?

`ConcurrentHashMap` khiến `get`, `put`, `remove`, `compute` và những thao tác riêng lẻ an toàn theo contract của nó. Nó **không tự làm cho chuỗi hành động bên ngoài** trở thành atomic:

~~~java
ScheduledFuture<?> old = handles.get(jobId);
if (old != null) old.cancel(false);
ScheduledFuture<?> next = scheduler.schedule(work, nextRun);
handles.put(jobId, next);
~~~

Đây là **phản ví dụ tích hợp**, không phải chương trình độc lập; cần `jobId`, `handles`, `scheduler`, `work`, `nextRun`. Mỗi operation của map có thể thread-safe, nhưng thread khác có thể chen vào **giữa cancel và put**. `cancel(false)` cũng là side effect lên scheduler, nằm ngoài tính atomic của map.

Giải pháp khả thi trong **một JVM**:

- Dùng `synchronized` hoặc `ReentrantLock` để giữ chung một critical section cho **đọc revision, tạo handle mới, công bố handle/revision, hủy handle cũ**.
- Hoặc đưa toàn bộ lệnh cập nhật registry vào **một luồng điều phối tuần tự (actor/serial executor)**, đảm bảo một thứ tự thay đổi.
- Hoặc dùng khóa **theo job ID** / lock stripes nếu nhiều job độc lập, tránh khóa toàn registry.
- Có thể dùng `ConcurrentHashMap.compute(jobId, ...)` để tuyến tính hóa thay đổi **một key**, nhưng nếu callback/scheduling có side effect hoặc có nhiều map liên quan, cần thiết kế rollback, reentrancy và ordering cẩn thận. Không đặt network call, DB transaction dài hoặc business callback trong `compute` function.

Các lựa chọn là phương pháp khác nhau để thiết lập **một điểm quyết định revision**. Dùng `volatile` cho một biến handle riêng lẻ không đủ để làm nguyên chuỗi `cancel -> replace` atomic.

## 6. Thứ tự thay lịch an toàn hơn: đăng ký mới rồi công bố

Một thuật toán nội bộ cho một JVM:

~~~text
replaceIfVersion(jobId, expectedRevision, newSpec):
  LOCK(jobId hoặc registry)
    state = read(jobId)
    IF state.revision != expectedRevision:
      RETURN CONFLICT

    nextRevision = currentRevision + 1
    newRunnable = guarded(jobId, nextRevision, work)
    newFuture = scheduler.schedule(newRunnable, newSpec)
       - nếu bị REJECT/throw: giữ state và oldFuture cũ, trả lỗi
       - với Trigger không có lần chạy tiếp theo: có thể trả null
    publish state(jobId, nextRevision, newSpec, newFuture)
    cancel(oldFuture, false) nếu có
  UNLOCK
  RETURN UPDATED(nextRevision)
~~~

Có hai chi tiết cần hiểu:

1. **Đăng ký mới trước khi hủy cũ** giảm tình huống lỗi submit khiến hệ thống mất cả lịch cũ. Nhưng giữa lúc đăng ký và lúc hủy, scheduler có thể giữ **hai future**. Registry lock và **generation guard** khiến callback mới/chưa khởi chạy không quan sát state chưa công bố; chúng không làm các side effect đã bắt đầu trở thành atomic.
2. Đừng chạy `work.run()` trong critical section: method nghiệp vụ có thể chậm, chặn HTTP/DB hoặc gọi lại registry, làm mọi request quản trị lịch bị nghẽn.

Với `TaskScheduler.schedule(Runnable, Instant)`, Javadoc mô tả trả `ScheduledFuture` hoặc ném `TaskRejectedException` nếu không nhận task. Với `schedule(Runnable, Trigger)`, một `Trigger` không có lần chạy kế tiếp có thể làm kết quả **`null`**. Nếu dùng API Trigger, hãy định nghĩa rõ null đồng nghĩa DISABLED/no next run hay là lỗi cấu hình, không đưa `null` vào `Map` rồi coi lịch còn active.

Nếu scheduler shutdown sau khi accept một job, hoặc process crash, “đã schedule thành công” cũng không đồng nghĩa task được chạy chắc chắn. Một nguồn state bền vững và reconciliation cần giải quyết tầng đó.

## 7. Java 17 + Spring Framework 6.2.9: ví dụ chạy độc lập

Ví dụ thực hành có hai thread đóng vai admin đồng thời yêu cầu thay **cùng job ID** với **cùng revision dự kiến 0**. Chúng xuất phát qua `CountDownLatch`. Registry dùng `synchronized` để tuyến tính hóa update; chỉ một thread được nâng revision lên 1.

Sau đó caller thử hủy bằng **revision cũ 0** (phải bị từ chối), rồi hủy đúng revision 1 (được chấp nhận). Một `ThreadPoolTaskScheduler` thật dùng để đăng ký one-shot timer 60 giây trong tương lai; bài demo chủ động cancel trước hạn, nên không có side effect nào chạy. Đầu ra là **các assertion có thể quan sát được** của consistency, không lệ thuộc thread nào thắng race.

### Cấu trúc dự án

~~~text
concurrent-schedule-demo/
├── pom.xml
└── src/main/java/demo/ConcurrentScheduleDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>concurrent-schedule-demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.9</version>
    </dependency>
  </dependencies>
  <build><plugins>
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
  </plugins></build>
</project>
~~~

### src/main/java/demo/ConcurrentScheduleDemo.java

~~~java
package demo;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class ConcurrentScheduleDemo {
    static final class Registry {
        private record Slot(long revision, ScheduledFuture<?> future) {}

        private final TaskScheduler scheduler;
        private final Map<String, Long> revisions = new HashMap<>();
        private final Map<String, Slot> active = new HashMap<>();

        Registry(TaskScheduler scheduler) {
            this.scheduler = scheduler;
        }

        private synchronized boolean isCurrent(String id, long revision) {
            Slot slot = active.get(id);
            return slot != null && slot.revision() == revision;
        }

        synchronized boolean replaceIfVersion(
                String id, long expected, Instant at, Runnable work) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(at);
            Objects.requireNonNull(work);
            if (id.isBlank()) throw new IllegalArgumentException("empty id");
            long current = revisions.getOrDefault(id, 0L);
            if (current != expected) return false;

            long next = Math.addExact(current, 1L);
            ScheduledFuture<?> created = scheduler.schedule(() -> {
                if (isCurrent(id, next)) {
                    work.run();
                }
            }, at);
            if (created == null) {
                throw new IllegalStateException("No scheduled handle");
            }

            Slot previous = active.put(id, new Slot(next, created));
            revisions.put(id, next);
            if (previous != null) previous.future().cancel(false);
            return true;
        }

        synchronized boolean cancelIfVersion(String id, long expected) {
            if (revisions.getOrDefault(id, 0L) != expected) return false;
            Slot slot = active.remove(id);
            if (slot == null) return false;
            revisions.put(id, Math.addExact(expected, 1L));
            slot.future().cancel(false);
            return true;
        }

        synchronized long revision(String id) {
            return revisions.getOrDefault(id, 0L);
        }

        synchronized boolean hasPending(String id) {
            Slot slot = active.get(id);
            return slot != null &&
                !slot.future().isDone() &&
                !slot.future().isCancelled();
        }
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("calendar-");
        scheduler.initialize();

        ExecutorService editors = Executors.newFixedThreadPool(2);
        try {
            Registry registry = new Registry(scheduler);
            CountDownLatch begin = new CountDownLatch(1);
            AtomicInteger effects = new AtomicInteger();

            Callable<Boolean> sameVersionUpdate = () -> {
                begin.await();
                return registry.replaceIfVersion(
                    "nightly", 0L, Instant.now().plusSeconds(60),
                    effects::incrementAndGet);
            };

            Future<Boolean> first = editors.submit(sameVersionUpdate);
            Future<Boolean> second = editors.submit(sameVersionUpdate);
            begin.countDown();

            boolean a = first.get(5, TimeUnit.SECONDS);
            boolean b = second.get(5, TimeUnit.SECONDS);
            int success = (a ? 1 : 0) + (b ? 1 : 0);
            System.out.println("Successful updates: " + success);
            System.out.println("Version conflicts: " + (2 - success));
            System.out.println("Revision after update: " +
                registry.revision("nightly"));
            System.out.println("Pending schedule: " +
                registry.hasPending("nightly"));

            System.out.println("Stale cancel rejected: " +
                !registry.cancelIfVersion("nightly", 0L));
            System.out.println("Current cancel applied: " +
                registry.cancelIfVersion("nightly", 1L));
            System.out.println("Revision after cancel: " +
                registry.revision("nightly"));
            System.out.println("Pending after cancel: " +
                registry.hasPending("nightly"));
            System.out.println("Executions: " + effects.get());
        } finally {
            editors.shutdownNow();
            scheduler.shutdown();
        }
    }
}
~~~

Chạy khi có **JDK 17+** và **Maven**:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.ConcurrentScheduleDemo
~~~

**Stdout chính xác đã kiểm tra** với Spring Framework 6.2.9 và mục tiêu biên dịch Java 17:

~~~text
Successful updates: 1
Version conflicts: 1
Revision after update: 1
Pending schedule: true
Stale cancel rejected: true
Current cancel applied: true
Revision after cancel: 2
Pending after cancel: false
Executions: 0
~~~

**Vì sao kết quả ổn định?** Hai thread cùng mang `expected=0` nhưng `replaceIfVersion` được `synchronized`. Thread thắng lock đăng ký một timer, công bố revision 1 rồi trả thành công; thread tiếp theo đọc revision 1 nên trả `false` và không tạo timer thứ hai. Không cần suy đoán thread `first` hay `second` thắng.

`cancelIfVersion("nightly", 0)` thất bại do current revision là 1; lệnh `cancelIfVersion(..., 1)` hủy slot và nâng revision lên 2. Việc giữ revision 2 sau khi stop ngăn caller cũ tái sử dụng expected=0 như chưa có lịch. `effects=0` vì timer được đặt xa hơn thời gian test và đã hủy trước khi chạy.

**Phạm vi của ví dụ:** Đây là registry **trong một JVM**, dùng khóa toàn registry để ưu tiên tính rõ ràng. Nó không persist `ScheduleSpec`, không restore timer sau restart, không phối hợp nhiều server, không tự thu dọn slot one-shot đã hoàn thành và không cấm business action đang chạy hoàn tất sau stop. Các vấn đề production được giải thích ở các mục tiếp theo, không nên sao chép demo như một distributed job scheduler đầy đủ.

## 8. Điểm tuyến tính hóa và việc publish trạng thái

Trong ví dụ, cả `replaceIfVersion`, `cancelIfVersion`, `revision`, `hasPending` và `isCurrent` đều dùng **cùng một monitor** của đối tượng `Registry`.

Vì thế, thao tác đổi lịch có thể được xem như một phép chuyển **có thứ tự**:

~~~text
State before:
  revisions["nightly"] = 0
  active["nightly"] = null

Thread A giữ monitor:
  verify expected == 0
  scheduler.schedule(guarded, futureInstant)
  active["nightly"] = Slot(1, newFuture)
  revisions["nightly"] = 1
  unlock

Thread B tiếp tục:
  verify expected == 0
  actual revision == 1
  return CONFLICT (không đăng ký timer mới)
~~~

Mọi truy cập tới state do registry sở hữu đều đi qua cùng monitor, tạo tính **visibility** và thứ tự thay đổi trong JVM. Callback có thể được executor khởi chạy rất sớm nếu thời điểm đã qua, nhưng wrapper `isCurrent` cần lấy monitor nên **không quan sát một trạng thái đang được công bố dở**.

Các bước ở trong critical section vẫn có side effect trên scheduler bên ngoài registry. “Tuyến tính hóa được state điều khiển” không đồng nghĩa hai future tại scheduler không thể cùng tồn tại thoáng qua, hay hai business task không thể overlap nếu một task cũ đang chạy.

`ThreadPoolTaskScheduler` vốn hỗ trợ schedule từ nhiều thread; vấn đề cần khóa là **hợp đồng state của registry**, không phải bắt buộc bao một lock quanh mọi `schedule()` của tất cả ứng dụng.

## 9. Generation guard: chống callback lỗi thời bắt đầu sau khi thay lịch

Mỗi callback nên mang revision/generation mà nó được tạo ra. Trước khi làm việc, callback kiểm tra “tôi còn là current revision không?”:

~~~java
Runnable guarded = () -> {
    if (!registry.isCurrent(jobId, scheduledRevision)) {
        return; // Timer cũ, không làm công việc mới.
    }
    businessAction.run();
};
~~~

Đây là **mẫu kiến trúc**, không phải mã chạy độc lập: cần cách inject `registry`, `jobId`, `scheduledRevision`, `businessAction`. Việc kiểm tra phải đọc trạng thái đồng bộ (lock/atomic snapshot), không đọc tùy tiện một `HashMap` không có happens-before.

**Giới hạn quan trọng nhất:** Kiểm tra generation tại đầu callback chỉ ngăn callback nào **chưa vượt qua điểm kiểm tra**. Nếu callback cũ đã qua guard, sau đó thread khác đổi lịch và cancel handle, callback cũ vẫn có thể đang gọi DB/HTTP và ghi dữ liệu.

~~~text
old callback:  check current=7  [PASS]
                                               /
new editor:    replace revision 7 -> 8, cancel(old)
               |
old callback:  continues business write  [STILL POSSIBLE]
~~~

Khi business effect phải tuyệt đối không dùng phiên bản cũ, cần **fencing ở đúng nơi commit side effect**, ví dụ `UPDATE ... WHERE current_revision = :capturedRevision` trong transaction DB, hoặc một API downstream kiểm tra fencing token trước khi áp dụng. Nếu chỉ có HTTP API không hỗ trợ token, dùng idempotency key/compensation và thiết kế trạng thái rõ ràng; lock trên registry không thể khóa một server từ xa.

Một `cancel(false)` đã báo thành công cũng không phải bằng chứng không còn in-flight work. Hãy tách `scheduleCancelled`, `inFlightCount`, `lastExecutionStatus` trong quan sát hệ thống nếu sản phẩm cần trạng thái dừng chặt chẽ.

## 10. Stop, replace và complete phải dùng cùng quy tắc revision

Ba đường chuyển trạng thái đều có thể race:

| Hoạt động | Rủi ro | Quy tắc an toàn |
| --- | --- | --- |
| `replace` | Hai writer ghi đè nhau, để lại ghost timer | Expected revision + atomic swap/cancel |
| `stop` | Stop từ UI cũ xóa handle mới | Stop chỉ khi revision vẫn khớp |
| `onComplete` | Callback one-shot cũ hoàn tất muộn và xóa slot mới | Remove chỉ khi slot vẫn có revision/identity của callback |
| `refreshCron` | Config đổi nhưng trigger cũ tiếp tục tick | Tạo immutable spec/trigger mới, cancel/register dưới cơ chế đồng bộ |
| `resume` | Reset revision gây ABA, callback cũ lại được tin | Tăng revision và giữ tombstone |
| `shutdown` | Job accepted trước khi shutdown nhưng không chạy | Trạng thái/reconciliation ở lần startup sau |

Với **one-shot schedule**, có thể cần dọn entry khỏi `active` khi callback xong. Không gọi `active.remove(jobId)` vô điều kiện: giữa lúc callback chạy và lúc nó hoàn tất, một update có thể đã cài lịch khác.

Thay bằng logic trong registry:

~~~java
synchronized void completeIfCurrent(String jobId, long generation) {
    Slot current = active.get(jobId);
    if (current != null && current.revision() == generation) {
        active.remove(jobId);
        // Giữ revisions[jobId] là generation, không reset về 0.
    }
}
~~~

Đây là **snippet tích hợp trong class Registry**, không phải method độc lập; cần `Slot` và các map. Nếu nghiệp vụ muốn phân biệt `SUCCEEDED` và `DISABLED`, nên lưu trạng thái riêng và có thể nâng revision khi chuyển trạng thái; không chỉ dùng việc thiếu handle để đại diện toàn bộ business outcome.

## 11. Optimistic concurrency trong API quản trị lịch

Một UI thường hiển thị “lịch hiện tại: 09:00, revision=12”. Người dùng A và B cùng mở trang:

~~~text
GET /schedules/nightly
-> { cron: "0 0 9 * * *", zone: "UTC", revision: 12 }

A: PUT cron=10:00, expectedRevision=12
-> 200 OK, revision: 13

B: PUT cron=11:00, expectedRevision=12
-> 409 Conflict, currentRevision: 13
~~~

Đây là **ví dụ HTTP contract**, không phải request thật. `409 Conflict` là một lựa chọn phổ biến; API dùng `ETag`/`If-Match` có thể dùng `412 Precondition Failed` theo hợp đồng conditional request.

Điều không nên làm: cứ nhận `PUT` cuối cùng thì âm thầm ghi đè và trả “thành công” cho cả hai, trừ khi sản phẩm **cố ý** chọn last-write-wins và giải thích rõ rủi ro.

Một flow phù hợp:

1. Client gửi **expected revision** cùng `cron`, `ZoneId`, trạng thái và job ID.
2. Server validate cron/zone/deadline.
3. Registry/DB làm conditional update `WHERE revision = :expected`.
4. Nếu không khớp, trả conflict kèm revision mới; không schedule một handle mới.
5. Nếu khớp, chuyển sang revision mới và ghi audit event.
6. Trả response chỉ sau khi đã có một hợp đồng rõ giữa trạng thái mong muốn và lịch thực tế.

Đừng cho frontend tự tăng `revision` rồi gửi đi mà không kiểm tra trạng thái server. Revision là token đối chiếu, **server mới là nơi quyết định** phép chuyển được chấp nhận.

## 12. Synchronized, lock theo key và actor: chọn theo quy mô

Trong demo, `synchronized` trên cả registry tạo một thứ tự toàn cục, đơn giản và dễ hiểu. Với vài chục thay đổi lịch mỗi giờ, nó thường đủ; với nhiều tenant cập nhật hàng nghìn job đồng thời, có thể tạo contention không cần thiết.

Các cách nâng cấp:

- **Per-job lock:** mỗi ID có lock/actor riêng; hai update của `nightly` vẫn được sắp thứ tự, nhưng update `customer-A` không bị `customer-B` chặn.
- **Striped locks:** hash job ID vào một tập lock hữu hạn để tránh tạo vô hạn lock object; chấp nhận contention nhỏ giữa các ID cùng stripe.
- **Serial command queue:** một luồng điều phối nhận `Replace/Stop/Resume`, xử lý tuần tự, có command ID và kết quả. Phải dùng queue **bounded** và xử lý lỗi/shutdown.
- **Atomic immutable snapshot:** `AtomicReference<JobState>` và CAS có thể bảo vệ state thuần; nhưng việc tạo/cancel `ScheduledFuture` vẫn là side effect cần quản lý nếu CAS thất bại.
- **ConcurrentHashMap.compute:** có thể tuyến tính hóa thay đổi **một key** khi lambda được thiết kế cẩn thận. Tránh blocking network/DB, recursive map update và mọi side effect không có rollback.

Không giữ lock khi chạy body công việc lâu. Nếu scheduler callback muốn gọi service nghiệp vụ, lấy thông tin/tokens cần thiết trong lock rồi thả lock trước khi thực hiện I/O. Nếu cần khóa đến thời điểm commit nghiệp vụ, nên dùng **fencing/transaction** ở tầng dữ liệu, không mở rộng một Java monitor xuyên suốt HTTP call.

## 13. Trigger động và tính nhất quán của config

Khi dùng `schedule(Runnable, Trigger)`, có thể có một `CronTrigger` hoặc `Trigger` tùy biến đọc `TriggerContext` để tính lần chạy tiếp theo.

Ví dụ đăng ký theo cron:

~~~java
CronTrigger trigger =
    new CronTrigger("0 0 9 * * MON-FRI",
        ZoneId.of("Asia/Ho_Chi_Minh"));

ScheduledFuture<?> future =
    scheduler.schedule(
        () -> processBusinessDay(),
        trigger);
~~~

Đây là **snippet tích hợp**; `scheduler` là bean `TaskScheduler`, `processBusinessDay` là work và cần imports `CronTrigger`, `ZoneId`, `ScheduledFuture`. `CronTrigger` với zone rõ ràng giúp tránh dựa vào timezone của server.

Nếu có nhiều thread cùng đổi cron hoặc zone:

- **Không** dùng một object mutable có `cron`, `zone`, `period` rồi để trigger đọc từng field riêng lẻ không đồng bộ; nó có thể nhìn thấy **cron mới + zone cũ** trong cùng lần tính.
- Dùng một **immutable schedule specification** (`record ScheduleSpec(String cron, ZoneId zone, long revision)`) và thay bằng một phép swap có kiểm soát.
- Nếu muốn thay trigger ở runtime, validate spec mới, đăng ký trigger mới, công bố revision mới, hủy handle cũ.
- Với trigger tùy biến tính theo kết quả lần chạy trước, cần định nghĩa **ai sở hữu lịch sử execution** và bảo đảm việc thay version không dùng lẫn history của phiên bản cũ theo cách gây sai thời điểm.
- `TaskScheduler.schedule(Runnable, Trigger)` có thể trả `null` khi trigger **không có lần chạy kế tiếp**, nên API registry phải quyết định đây là một trạng thái NO_NEXT_RUN hợp lệ hay lỗi cần báo cho client.

`@Scheduled` là lựa chọn tốt cho lịch định kỳ khai báo ổn định. Thay field trong bean dùng `@Scheduled` **không đảm bảo** trigger đã đăng ký tự động đổi theo; lịch runtime có vòng đời và revision rõ nên dùng registry/direct scheduler khi thực sự cần quyền điều khiển.

## 14. Callback cũ có thể xóa lịch mới: race khi cleanup

Một lỗi không phải lúc thay cấu hình mà xảy ra khi worker hoàn tất:

~~~text
t0: callback của generation 5 bắt đầu
t1: admin đăng ký generation 6, current=6
t2: callback cũ generation 5 hoàn thành
t3: callback cũ gọi registry.remove(jobId) không kiểm tra
t4: registry mất handle generation 6
~~~

Ngay cả khi timer generation 6 thực tế vẫn nằm trong scheduler queue, nó trở thành **orphan/ghost** vì registry không còn tham chiếu để quản trị.

Cần kiểm tra version/identity trước khi clear:

~~~java
synchronized void clearIfCurrent(String id, long revision) {
    Slot slot = active.get(id);
    if (slot != null && slot.revision() == revision) {
        active.remove(id);
    }
}
~~~

Đây là **đoạn code ghép vào Registry** đã định nghĩa. Nếu muốn tránh xóa nhầm khi cùng revision có hai handle do bug, so sánh thêm **identity của handle**. Không bao giờ dùng `Map.remove(key)` vô điều kiện từ callback có thể hoàn tất sau một update mới.

Với trigger recurring, không nên remove slot sau mỗi callback; handle đó còn đại diện cho lịch các lần tiếp theo. Với one-shot, cleanup hoàn tất là hợp lý, nhưng cần phân biệt thành công, lỗi, canceled và trạng thái persisted.

## 15. Cancellation không phải một transaction rollback

`ScheduledFuture.cancel(false)` yêu cầu ngăn thực thi còn chờ của future khi được chấp nhận, **không yêu cầu interrupt task đã chạy**. `cancel(true)` có thể yêu cầu interrupt, nhưng không bảo đảm mọi JDBC driver, HTTP client hay remote service dừng xử lý.

Có ba trạng thái khác nhau:

~~~text
A. Lịch không còn được đăng ký cho lượt tiếp theo
B. Không còn callback của phiên bản cũ đang chạy trong JVM
C. Không còn side effect cũ có thể commit trong DB / hệ thống ngoài
~~~

Đạt A **không tự động kéo theo** B hay C. Một callback đã qua generation check rồi vào DB transaction có thể commit sau khi admin nhấn Stop. Để đảm bảo C, ranh giới ghi dữ liệu phải kiểm tra token hoặc áp dụng giao dịch/unique business key.

Ví dụ ý tưởng điều kiện update trạng thái:

~~~sql
UPDATE job_results
SET last_status = 'SUCCESS'
WHERE job_id = :job_id
  AND current_schedule_revision = :captured_revision;
~~~

Đây là **SQL giả mã minh họa có named parameters**, cần schema thực tế; với thiết kế khác, version authoritative nằm trong `job_schedules` và phải kiểm tra atomically trong transaction. Nếu `UPDATE` ảnh hưởng 0 dòng, callback đã lỗi thời và không được báo business success. Không được gọi SQL này rồi thực hiện một external side effect không có fencing: kiểm tra DB trước một HTTP call vẫn có race khác ngay sau đó.

Nếu job là gửi email/thu tiền, dùng idempotency key theo business occurrence, có thể cần downstream hỗ trợ version token hoặc reconciliation khi response không chắc chắn.

## 16. Schedule bị reject: giữ state cũ hoặc chuyển sang lỗi rõ ràng

Lúc scheduler shutdown, quá tải hoặc có policy từ chối, `TaskScheduler.schedule(...)` có thể ném `TaskRejectedException` trước khi trả handle. Nếu code **đã cancel oldFuture và tăng revision** trước khi nộp lịch mới, thất bại này làm hệ thống mất cả lịch cũ.

Trong thiết kế của mục 6 và demo:

~~~text
under lock:
  check expected revision
  try schedule NEW
  if rejected -> exit, do not mutate old state
  if accepted -> publish new slot/revision; cancel old handle
~~~

Đối với `schedule(Runnable, Trigger)`, giá trị `null` cần được xử lý rõ. Nếu tính next execution phụ thuộc một trigger không có future run, có thể chọn state `INACTIVE` phù hợp; không trả thành công “đã lên lịch” trong khi không có handle.

Những lỗi khác cũng cần policy:

- Scheduler đã shutdown nhưng endpoint vẫn cho phép admin update -> trả lỗi xác định; không quay vòng retry nóng gây quá tải.
- Quá nhiều job một lần -> giới hạn số active schedules, số timer và memory; không lưu hàng triệu `ScheduledFuture` vô hạn.
- Task đã được accept rồi JVM crash -> local handle mất; nếu job phải sống qua restart, lưu schedule spec và next due vào DB để dựng lại.
- Việc hủy handle cũ không thể ngăn một callback đã chạy; ghi metric stale attempts, in-flight và duplicate prevention.
- Nếu update status vào DB sau khi schedule trong memory -> có thể có crash gap giữa hai lần ghi; cần nguồn sự thật và quá trình reconcile, không tuyên bố atomic across DB và scheduler.

**Không nên gọi `schedule()` trong một khối `synchronized` rồi tiếp tục làm network I/O lâu**. Critical section chỉ gồm thao tác state/registration ngắn, không phải toàn bộ xử lý nghiệp vụ.

## 17. Một JVM khác với nhiều instance: giới hạn của khóa Java

Trong Kubernetes với 3 replicas, mỗi replica có một `Registry` và một `ThreadPoolTaskScheduler` riêng. Các monitor Java **không thấy nhau**:

~~~text
Replica A: current version=7, localFuture=A7
Replica B: current version=7, localFuture=B7
Admin update A -> version=8
B không nhận thông báo hoặc không reload -> vẫn chạy B7
~~~

Cho dù `synchronized` trên cả hai JVM đều đúng, hệ thống vẫn có thể chạy callback stale từ replica B.

Để quản lý **state lịch chung**, dùng nguồn sự thật bền vững (DB row, config service có version, job store) và **optimistic locking/conditional update**:

~~~sql
UPDATE job_schedules
SET cron = :new_cron,
    zone_id = :new_zone,
    revision = revision + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE job_id = :job_id
  AND revision = :expected_revision;
~~~

Đây là **SQL với named parameters**, không phải lệnh SQL thuần chạy độc lập. Nếu update đúng **1 row**, writer đã thắng phép CAS tại DB; nếu **0 row**, return conflict và đọc revision hiện hành, không schedule bản ghi stale.

Tuy nhiên DB update thành công **không lập tức hủy timer ở mọi replica**. Mỗi replica cần cơ chế watch/reconcile hoặc chỉ một owner/worker được phép đăng ký lịch, và callback phải kiểm tra token hiệu lực trước khi tạo side effect.

**Thứ tự an toàn ở tầng phân tán** còn yêu cầu định nghĩa ranh giới commit: database là authoritative cho desired schedule, local scheduler là **bản sao thực thi có thể xây dựng lại**, không phải bản ghi chân lý thứ hai có thể ghi tùy ý.

## 18. Fencing token và lease khi có nhiều worker

Một hệ thống job phân tán thường có **owner** và **lease**:

~~~text
job_id = nightly
revision = 23       // phiên bản cấu hình cron
owner = worker-A
owner_epoch = 71    // token tăng khi đổi owner
lease_until = ...
~~~

Hai trục version có thể khác nhau:

- **Configuration revision** bảo vệ việc admin cập nhật cron/zone/deadline và trạng thái enabled.
- **Owner epoch/fencing token** bảo vệ quyền thực thi khi lease đổi từ worker cũ sang worker mới.

Ví dụ worker A bị GC pause, lease hết hạn và worker B nhận owner epoch 72. A tỉnh lại với token 71 và cố ghi dữ liệu: ranh giới DB phải từ chối token cũ, nếu không có thể có **hai worker cùng thực hiện công việc** dù lease service khẳng định đã chuyển ownership.

**Lease expiry không tự dừng CPU/thread cũ.** Cần fencing token được **downstream kiểm tra** hoặc transaction DB có điều kiện, cộng idempotency cho external effects.

Không đồng nhất `cron revision` và `owner epoch`: admin đổi cấu hình không nhất thiết đổi owner, còn owner failover có thể xảy ra khi cron không đổi. Hệ thống có thể dùng một token thống nhất nếu hợp đồng được thiết kế rõ, nhưng phải chứng minh các đường chuyển version đều hợp lệ.

## 19. Job store bền vững và reconciliation sau restart

Nếu một lịch quan trọng phải tiếp tục sau process restart, một `Map<String, ScheduledFuture<?>>` là không đủ. Cần lưu **ScheduleSpec** (job ID, cron/zone hoặc nextAt, revision, status, business key và owner) vào DB/job store, rồi xây dựng lại local runtime handles.

Một vòng reconciliation:

1. Đọc các row enabled mà instance này có quyền sở hữu hoặc claim lease.
2. So sánh revision persisted với revision của runtime slot.
3. Nếu DB mới hơn, validate spec, hủy/đánh dấu stale slot cũ, đăng ký mới theo `Instant` hoặc `Trigger`.
4. Nếu DB disabled, hủy slot local và ngăn callback cũ bằng revision/fence.
5. Nếu runtime có slot nhưng DB không còn sở hữu, ngừng đăng ký lượt mới; callback hiện hành phải bị fence ở data layer.
6. Lưu checkpoint/next due theo chính sách; theo dõi backlog và failed registration.
7. Sau crash giữa DB commit và local schedule, lần reconciliation kế tiếp dựng lại lịch mà không cần dựa vào handle đã mất.

**Quan trọng:** DB row commit và `TaskScheduler.schedule()` trong memory không phải một transaction atomic. Thiết kế **desired state + reconciliation** chấp nhận tạm thời lệch runtime rồi sửa lại theo source of truth, thay vì tuyên bố đã có một phép ghi nguyên tử trên hai hệ thống.

Nếu cần không bỏ sót bất kỳ occurrence nào, lưu occurrence theo job/business date và chọn job queue/outbox/Quartz JDBC JobStore hay worker orchestration phù hợp. Chỉ lưu next cron string mà không có lịch sử completion chưa chắc đủ để chạy bù tất cả lượt đã mất.

## 20. Giữ nhất quán giữa config và TriggerContext

Với `Trigger` tùy biến, `nextExecution(TriggerContext)` có thể dựa vào `lastScheduledExecution`, `lastActualExecution`, `lastCompletion`. Nhiều thread cùng thay cấu hình hoặc cùng ghi thông tin completion có thể tạo ra thời điểm kế tiếp không nhất quán.

Cách thiết kế đáng tin:

- Định nghĩa **immutable trigger spec** gồm cron/zone/delay/policy.
- Mỗi lần thay spec tạo một generation mới. Không đổi từng field của shared object mà không đồng bộ.
- Không dùng `TriggerContext` của generation cũ để tính lại lịch mới trừ khi đó là hợp đồng được chủ động định nghĩa.
- Với trigger theo thời gian địa phương, chọn `ZoneId` tường minh và kiểm thử DST, ngày không tồn tại/trùng.
- Khi một task hoàn tất, chỉ cập nhật metadata của **revision mà nó thuộc về**; callback cũ không được cập nhật `lastRun` của revision mới.
- Với recurring task, không xóa slot sau một lần thực thi. Với one-shot task, dọn slot phải có compare revision/identity.
- Nếu cần update lịch “ngay lập tức”, tính rõ những job đã được queue/in-flight và policy catch-up/reject.

Một `Trigger` có thể tự tính next run dựa trên trạng thái thay đổi, nhưng nếu nhiều writer sửa trạng thái đó, trigger vẫn cần một **snapshot nhất quán** hoặc một cơ chế serialization. Dùng interface `Trigger` không thay thế các nguyên tắc concurrency.

## 21. Kiểm thử cạnh tranh có kiểm soát

Tuyệt đối tránh dựa vào `Thread.sleep(10)` và hy vọng hai thread chen vào đúng lúc. Dùng `CountDownLatch`, `CyclicBarrier`, clock test hoặc scheduler test doubles để tạo race có thể tái hiện.

Một test matrix hữu ích:

| Tình huống | Setup | Assert bắt buộc |
| --- | --- | --- |
| Hai replace dùng cùng version | Barrier cho hai writer bắt đầu cùng lúc | Chính xác 1 update thành công, 1 conflict |
| Replace sau stop | Stop tăng revision, writer dùng token cũ | Không recreate lịch cũ vô tình |
| Stop stale vs replace mới | Writer A giữ token `r`, B cập nhật `r+1` | Stop `r` không xóa slot `r+1` |
| Scheduler reject | Dùng scheduler mock ném `TaskRejectedException` | State và oldFuture giữ nguyên |
| Trigger không có next run | Trigger trả `null` | Không có active slot giả |
| Timer chạy trước registry publish | Dùng scheduler test chạy task ngay khi được accept | Callback không thấy state nửa chừng/được guard đúng |
| Callback cũ qua guard trước khi stop | Latch giữ business action in-flight | Stop không được tuyên bố hủy side effect; DB fence nếu bắt buộc |
| One-shot finish muộn | Callback cũ hoàn thành sau replace | Không xóa entry của version mới |
| ABA | Stop rồi resume cùng ID | Generation không quay về token cũ |
| Nhiều key độc lập | Writers cùng đổi các job khác nhau | Không deadlock, không làm hỏng state |
| Nhiều node | Replica A/B có registry riêng | DB CAS/owner lease/fencing hoạt động |
| Restart | Hủy process sau DB commit trước runtime register | Reconciliation xây lại lịch |
| Repeated attempts | Retry cùng command ID/revision | Không tạo hai timer hoặc hai business effect |
| Shutdown | Context/scheduler đóng khi update | Trả error có thể theo dõi, không báo trạng thái thành công giả |

Có thể chạy stress test nhiều vòng với lịch đặt xa trong tương lai, rồi cancel mọi handle để không chờ. **Test một lần thành công không phải bằng chứng hết race**: phối hợp code review về happens-before, atomics và timeout với test fault injection/integration.

## 22. Quan sát đúng: phân biệt cấu hình, timer và tác vụ đang chạy

Log và metric nên giúp trả lời “ai đổi lịch nào, lúc nào, và phiên bản nào thực sự chạy?”:

| Thuộc tính | Dùng để làm gì |
| --- | --- |
| `jobId` | Định danh công việc, cần kiểm soát PII |
| `scheduleRevision` | So sánh stale/active cấu hình |
| `ownerEpoch` | Fencing và chẩn đoán failover nhiều node |
| `commandId` / `requestId` | Phát hiện lệnh retry trùng |
| `scheduleType` | ONE_SHOT, CRON, FIXED_RATE, FIXED_DELAY |
| `nextDueAtUtc`, `zoneId` | Chẩn đoán lệch giờ, deadline và DST |
| `attemptedAt`, `registeredAt` | Theo dõi độ trễ đăng ký |
| `startedAt`, `finishedAt` | Phân biệt queued, running, completed |
| `cancelRequestedAt`, `cancelAppliedAt` | Phân biệt yêu cầu stop và trạng thái runtime |
| `status`, `errorType` | Thống kê reject, conflict, stale skip, execution fail |

Các metric gợi ý: `schedule_update_conflicts_total`, `schedule_register_rejected_total`, `schedule_stale_callbacks_skipped_total`, `schedule_active_gauge`, `schedule_inflight_gauge`, `schedule_cancel_total`, `schedule_reconcile_lag` và `job_execution_failed_total`.

Tránh đưa raw `jobId` hoặc tenant ID làm label metric có cardinality rất cao. Giữ ID trong log/tracing có phân quyền, dùng tag hữu hạn theo job type, service và loại lỗi. Xác định một alert khi runtime revision khác desired revision quá lâu hoặc số job active bất thường so với job store.

## 23. Các anti-pattern thường gặp

| Anti-pattern | Hậu quả | Phương án thay thế |
| --- | --- | --- |
| `get -> cancel -> schedule -> put` không khóa | Lost update và ghost timer | Critical section + version |
| Chỉ đổi `HashMap` thành `ConcurrentHashMap` | Compound operation vẫn race | Atomic theo key/registry |
| Dùng một `volatile ScheduledFuture` | Không bảo vệ chuỗi side effect | State machine có version |
| Stop không truyền expected revision | Hủy/xóa lịch mới từ client cũ | Conditional stop/CAS |
| Reset revision khi tạo lại cùng job ID | ABA, stale callback qua guard | Monotonic revision + tombstone |
| Xóa `active.remove(id)` sau callback cũ | Orphan handle của version mới | Compare revision/handle identity |
| Gọi `cancel(true)` rồi coi DB rollback | Work in-flight vẫn có thể commit | DB fencing/idempotency |
| Scheduler submit mới thất bại sau khi đã cancel cũ | Job biến mất ngoài ý muốn | Register new trước, publish/cancel sau |
| Chạy network call dưới registry lock | Contention/deadlock và hàng đợi update dài | Chỉ giữ lock khi cập nhật state ngắn |
| Mutable Trigger đọc nhiều field không đồng bộ | Cron và zone có thể đến từ hai revision | Immutable spec/snapshot |
| Chỉ dùng synchronized khi chạy nhiều pod | Không đồng bộ liên JVM | DB CAS, lease và fencing |
| Ghi DB config và timer memory như một phép atomic | Crash gây lệch desired/runtime | DB source of truth + reconciliation |
| Dựa vào stop flag lúc task khởi động | Task cũ đã qua check vẫn có thể ghi | Fencing ngay trước side effect |
| Cho phép retry vô hạn khi scheduler reject | Tăng tải và chồng timer | Bounded retry/backoff, trạng thái ERROR |
| Dùng cron không ghi zone | Lịch có thể thay đổi theo environment | ZoneId tường minh trong spec |

## 24. Checklist khi code review

- Tôi đã định nghĩa trạng thái **desired schedule** khác với **runtime ScheduledFuture**.
- Mọi thay đổi cùng job ID đi qua **một đường atomic** có expected revision/CAS.
- Replace, stop, resume và cleanup đều so sánh revision; không có `remove(id)` mù.
- Revision **không reset** sau stop/recreate, tránh ABA.
- Timer mới chỉ được công bố khi `schedule()` chấp nhận, lỗi reject không âm thầm làm mất lịch cũ.
- Một callback từ generation cũ kiểm tra current token và bỏ qua nếu đã lỗi thời.
- Tôi hiểu check-at-start không ngăn một callback **đã bắt đầu** commit side effect.
- Với tác dụng phụ quan trọng, DB/downstream có idempotency, version/fencing hoặc reconciliation.
- Không có blocking I/O/business work bên trong monitor hoặc `ConcurrentHashMap.compute` của registry.
- Trigger spec là immutable/snapshot, cron và zone được kiểm tra cùng một revision.
- Có policy rõ cho one-shot completed vs recurring active; cleanup không xóa slot mới.
- Có phương án xử lý `TaskRejectedException`, null trigger, scheduler shutdown và quá tải.
- Khi có nhiều instance, DB/JobStore là source of truth, có owner lease và fencing khi cần.
- Có kế hoạch restart/recovery cho desired-vs-runtime drift và job bị lỡ.
- Test có race được kiểm soát bằng latch/barrier, không chỉ một lần chạy may mắn.
- Log/metric phân biệt conflict, reject, skipped stale callback, cancel và in-flight work.

**Kết luận:** Tính nhất quán khi nhiều luồng cùng cập nhật lịch không đến từ `TaskScheduler` hay `ConcurrentHashMap` một cách tự động. Cần **một phép chuyển trạng thái có thứ tự theo job ID**, với expected revision/CAS, quản lý `ScheduledFuture` an toàn và generation guard. Trong một JVM, monitor/actor/per-key lock có thể đủ để tránh lost update; với nhiều instance và side effect cần bảo đảm, phải mở rộng bằng **DB authoritative state, fencing token, idempotency và reconciliation**.

## Tài liệu tham khảo chính thức

- [Spring Framework – TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html): `schedule(Runnable, Instant)`, `schedule(Runnable, Trigger)`, các API lặp, `TaskRejectedException` và nullable Trigger schedule.
- [Spring Framework – ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): thread pool scheduler, `ScheduledFuture`, cancellation và lifecycle.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskScheduler`, `Trigger`, `@Scheduled`, `CronTrigger` và cách cấu hình.
- [Spring Framework – Trigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/Trigger.html): `nextExecution(TriggerContext)` và hợp đồng tính mốc.
- [Java SE 17 – ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): handle cho scheduled task.
- [Java SE 17 – Future.cancel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): cancellation và các giới hạn của interrupt.
- [Java SE 17 – ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): thread-safe operations nhưng không biến side effects bên ngoài thành một transaction.
- [Java SE 17 – synchronized / Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): happens-before, monitor và memory visibility.


</details>

- [Quay lại đầu trang](#back-to-top)
