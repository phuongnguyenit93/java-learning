<a id="back-to-top"></a>

# Tích hợp Quartz

## Menu
- [Khi nào lịch cần lưu trữ, khôi phục và quản lý bên ngoài tiến trình](#quartz-integration-001)
- [Vai trò JobDetail, Trigger và Scheduler trong Quartz](#quartz-integration-002)
- [SchedulerFactoryBean tích hợp vòng đời Quartz với Spring](#quartz-integration-003)
- [JobDetailFactoryBean và các loại trigger được Spring hỗ trợ](#quartz-integration-004)
- [Tiêm phụ thuộc vào job theo cơ chế phù hợp](#quartz-integration-005)
- [Phân biệt CronTrigger của Spring với CronTrigger của Quartz](#quartz-integration-006)
- [Misfire và chính sách chạy bù cần quyết định tường minh](#quartz-integration-007)
- [Giới hạn chạy đồng thời, lưu trữ và cụm phụ thuộc cấu hình Quartz](#quartz-integration-008)

## <a id="quartz-integration-001">Khi nào lịch cần lưu trữ, khôi phục và quản lý bên ngoài tiến trình</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào lịch cần lưu trữ, khôi phục và quản lý bên ngoài tiến trình

Một ứng dụng Spring có thể chạy tác vụ định kỳ chỉ với `@Scheduled` hoặc `TaskScheduler`. Cách đó đơn giản, phù hợp khi lịch cố định trong cấu hình, ứng dụng có thể đăng ký lịch lại mỗi lần khởi động và **không cần lưu lịch do người dùng tạo hoặc các lần kích hoạt bị lỡ**. Nhưng khi lịch là dữ liệu nghiệp vụ — người dùng tạo/sửa/tạm dừng, phải tồn tại sau restart, cần điều phối nhiều instance hoặc có chính sách xử lý một lượt bị lỡ — lịch không nên chỉ là một timer nằm trong bộ nhớ của một JVM.

**Quartz là một scheduling engine** có mô hình `JobDetail`, `Trigger`, `Scheduler` và các loại `JobStore`. Với `RAMJobStore`, Quartz **vẫn lưu lịch trong RAM**. Với **JDBC JobStore**, nó có thể giữ định nghĩa job, trigger và thông tin điều phối trong database để khởi động lại, phục hồi lịch bị lỡ theo chính sách và tổ chức cluster. Tuy nhiên, **lưu lịch bền vững không tự bảo đảm nghiệp vụ thực thi thành công hoặc side effect chỉ xảy ra đúng một lần**.

Bài Knowledge thuộc nhóm **Tích hợp Quartz**, module **Spring Framework - concurrency**, và trả lời đúng vấn đề: **khi nào nên chuyển lịch ra khỏi vòng đời một tiến trình, lưu những gì, khôi phục theo hợp đồng nào, quản trị bên ngoài ra sao và áp dụng với Spring Boot/Spring Framework thế nào**.

## 1. Một lịch có thể có ít nhất bốn loại trạng thái

Trước khi chọn công cụ, hãy phân biệt:

1. **Định nghĩa lịch** (*schedule definition*): job ID, loại công việc, biểu thức cron hoặc mốc thời gian, timezone, dữ liệu đầu vào cần thiết, trạng thái enabled/paused.
2. **Trạng thái kích hoạt** (*trigger state*): lần chạy trước, lần chạy kế tiếp, trigger có bị pause, được acquired, misfired hay không.
3. **Lần thực thi** (*job execution/attempt*): một lần worker bắt đầu, đã chạy tới đâu, lỗi gì, có thể chạy lại hay không.
4. **Kết quả nghiệp vụ** (*business outcome*): báo cáo đã lưu chưa, giao dịch đã commit chưa, email được hệ thống ngoài chấp nhận chưa, có bản ghi idempotency hay audit không.

Hai hệ thống dễ bị nhầm:

~~~text
[Persistent scheduler: Quartz JDBC JobStore]
   ├── JobDetail + Trigger + next fire time
   ├── Cluster coordination and misfire/recovery metadata
   └── Cho phép kích hoạt Job trên worker
                  |
                  v
[Business application / job status store]
   ├── runId / attemptId
   ├── PENDING -> RUNNING -> SUCCEEDED / FAILED
   ├── DB commit / remote request / downstream ACK
   └── idempotency, retries, compensations, audit
~~~

Nếu Quartz lưu được trigger mà job thực thi gọi API payment rồi tiến trình chết sau khi remote đã charge tiền, việc trigger được phục hồi **không chứng minh** request cũ chưa có tác dụng. Lần phục hồi có thể gửi request lần nữa. Phải xử lý idempotency, trạng thái nghiệp vụ và ACK ở tầng business.

### 1.1. Một timer trong JVM khác một lịch là tài sản nghiệp vụ

Ví dụ lịch nội bộ “mỗi 5 phút dọn cache” có thể đăng ký lại từ code khi app restart; mất thông tin về vài lượt dọn cache trong downtime có thể chấp nhận.

Nhưng lịch “khách A tạo yêu cầu xuất báo cáo vào 08:00 ngày cuối tháng”, lưu từ màn hình quản trị, cho phép tạm dừng trong một tuần và sửa múi giờ, lại là **dữ liệu nghiệp vụ**. Nếu chỉ đặt `ScheduledFuture` trong một `ConcurrentHashMap` của process, khi process restart thì không còn lịch để quản trị — trừ khi ứng dụng có cơ chế riêng để lưu và tái dựng chúng.

## 2. Khi nào @Scheduled trong Spring là đủ?

`@Scheduled` hoạt động tốt khi lịch là một phần **cấu hình phần mềm** và yêu cầu vận hành tương đối đơn giản.

Ví dụ tích hợp Spring:

~~~java
package example.local;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheCleanup {
    @Scheduled(fixedDelay = 300000)
    public void run() {
        // Gọi service dọn cache có idempotency và logging thích hợp.
    }
}
~~~

Đây là **đoạn Spring tích hợp**, cần `@EnableScheduling` và Spring ApplicationContext. Khi instance khởi động, Spring đăng ký callback từ annotation. Khi instance tắt, lịch trong memory cũng ngừng; khi khởi động lại, **lịch khai báo trong code được đăng ký lại**. Điều đó khác với việc giữ bền vững các đăng ký động hoặc lịch sử missed executions.

Thường chọn `@Scheduled` nếu:

- Lịch ít thay đổi, được review/deploy qua code hoặc config.
- Không cần người dùng tự tạo hàng nghìn lịch mới ở runtime.
- Chấp nhận downtime khiến một số lượt không được thực thi/bù lại tự động.
- Có một instance điều phối hoặc tác vụ idempotent nếu nhiều instances cùng chạy.
- Không yêu cầu giao diện quản lý pause/resume/reschedule/list lịch đang tồn tại theo user.
- Không yêu cầu retention, audit và recovery của schedule state qua nhiều lần restart.
- Công việc ngắn, có timeout và failure monitoring đủ cho SLA.

**Đừng kết luận “@Scheduled không sống qua restart” theo nghĩa tuyệt đối.** Annotation nằm trong code vẫn được đọc và đăng ký lại khi app trở lên; thứ không tự bền vững là **lịch động được tạo trong RAM, trigger state và lịch sử những lượt bị lỡ**.

### 2.1. Vấn đề nhiều replicas

Nếu ba replicas Spring cùng chạy bean `@Scheduled`, thường **cả ba replicas đều đăng ký lịch local**. Với cron 09:00, cả ba có thể gọi cùng công việc trừ khi có điều phối.

Nếu chỉ muốn chọn một instance chạy, có thể dùng leader election/distributed lock hoặc một scheduler tập trung. Nhưng việc bổ sung một distributed lock cho `@Scheduled` **khác** việc có persistent schedule store và đầy đủ misfire/recovery semantics. Chọn giải pháp theo yêu cầu thật thay vì coi Quartz là bắt buộc cho mọi job.

## 3. Quyết định: bộ nhớ hay lịch bền vững?

| Yêu cầu | Lập lịch local Spring / Quartz RAM | Quartz JDBC JobStore / hệ thống lưu lịch |
| --- | --- | --- |
| Lịch cố định, đăng ký lại từ code sau restart | Thường đủ | Có thể không cần |
| Lịch do user/API tạo và thay đổi trong runtime | Mất khi process restart nếu không tự lưu | Lưu JobDetail/Trigger trong DB |
| Giữ trạng thái pause/resume sau restart | Không mặc định | Có thể duy trì bằng persistent store |
| Cần xử lý trigger đến hạn trong lúc app offline | Không có misfire recovery bền vững mặc định | Có misfire policy, phải chọn và test |
| Một lịch được điều phối giữa nhiều node | Không mặc định | JDBC clustering có cơ chế acquire trigger |
| Phân quyền/audit sửa lịch | Tự xây | Vẫn phải xây API/RBAC/audit trên scheduler |
| Lưu kết quả job và side effect đúng một lần | Không tự có | Quartz cũng **không tự cung cấp** exactly-once business outcome |
| Đơn giản, ít hạ tầng, workload không quan trọng | Lợi thế | Đổi lại có DB schema, migration, ops |
| Chịu được mất node khi đang chạy | Không có takeover persistent mặc định | Cluster/recovery có điều kiện và cần idempotency |
| Khởi động lại nhiều replicas mà không bị reset lịch | Không phù hợp nếu mọi lịch được tạo động trong RAM | Cần JDBC store chung và chính sách startup rõ |

**Câu hỏi cốt lõi:** lịch chỉ là một cơ chế tiện lợi để gọi method hay là một thực thể cần định danh, lưu trữ, truy vấn và khôi phục độc lập với JVM?

## 4. Quartz RAMJobStore và JDBC JobStore là hai hợp đồng khác nhau

Quartz **không tự động bền vững** chỉ vì dùng tên Quartz.

### 4.1. RAMJobStore

Với `RAMJobStore`, các `JobDetail` và `Trigger` được lưu trong bộ nhớ. Khi JVM dừng, trạng thái động mất; restart cần đăng ký lại từ code/config hoặc từ nguồn khác của ứng dụng. Ưu điểm là ít hạ tầng, nhanh để thử và phù hợp tác vụ ngắn hạn không cần persistence.

Ví dụ dùng Quartz API để đăng ký một trigger trong RAM không khiến nó tồn tại qua restart. Một bean Spring `@Scheduled` và Quartz RAM store đều không phải một source-of-truth độc lập bền vững cho lịch do người dùng tạo.

### 4.2. JDBC JobStore

Với JDBC store, Quartz lưu định nghĩa và trạng thái lịch trong các bảng quan hệ (thường có prefix `QRTZ_`), bao gồm job details, triggers, các trạng thái acquire/paused/misfire và metadata điều phối phù hợp.

Điều được lưu là **dữ liệu scheduler**, không tự là application database chứa business results. Tính bền vững phụ thuộc vào DB: backup, transaction isolation, schema migrations, quyền truy cập, connection pool và xử lý lỗi.

Các implementation được Quartz mô tả:

- `JobStoreTX`: Quartz tự quản lý giao dịch DB của mình, thường dùng khi không có global JTA transaction do container quản lý.
- `JobStoreCMT`: tích hợp với transaction manager/JTA theo cấu hình phù hợp.
- Khi Spring quản lý `DataSource` cho Quartz, `SchedulerFactoryBean` có cơ chế `LocalDataSourceJobStore` để tích hợp Spring transaction/data source. **Không tự đặt `org.quartz.jobStore.class=JobStoreTX` khi Spring Boot đang quản lý JDBC store qua DataSource**, trừ khi hiểu và cấu hình đầy đủ lựa chọn thay thế.

Không trộn đồng thời hai đường cấu hình Quartz JDBC store (Spring-managed datasource và Quartz-managed datasource/jobStore class) mà không xác định ai là chủ sở hữu connection và transaction.

### 4.3. “Durable job” có hai nghĩa dễ nhầm

Trong Quartz, `JobBuilder.storeDurably()` có nghĩa **JobDetail vẫn được giữ khi không có Trigger tham chiếu tới nó**. Đây là thuộc tính **durable trong mô hình JobDetail**.

Nó **không** có nghĩa job được lưu xuống đĩa khi đang dùng `RAMJobStore`. Muốn lịch sống qua restart, vẫn phải chọn một **persistent JobStore**, ví dụ JDBC JobStore. Nếu thấy `storeDurably()` mà vẫn cấu hình RAM, đừng hứa người dùng job sống qua crash.

## 5. Minh họa restart: lịch RAM mất, dữ liệu đã lưu có thể nạp lại

Sơ đồ hai trường hợp:

~~~text
CASE A: LỊCH ĐỘNG TRONG MEMORY
 User tạo trigger T1
         |
         v
 App instance #1: Map / RAMJobStore = {T1}
         |
         X  instance crash/restart
         |
         v
 App instance #2: Map mới / RAMJobStore mới = {}
         |
         +-- T1 không được khôi phục từ RAM cũ

CASE B: LỊCH ĐỘNG LƯU TRONG DATABASE
 User tạo trigger T1
         |
         v
 App instance #1 --> DB: save JobDetail + Trigger
         |
         X  instance crash/restart
         |
         v
 App instance #2 --> DB: read persisted trigger state
         |
         +-- T1 vẫn được biết tới
         +-- nếu đã quá hạn: áp dụng MISFIRE POLICY
         +-- sau đó mới xử lý execution/outcome
~~~

Chú ý: việc tìm lại định nghĩa T1 **không tự động đồng nghĩa chạy bù mọi lần T1 đã bỏ lỡ**. Quartz có các misfire instructions khác nhau; outcome của job cũng phải được lưu/kiểm tra ở tầng nghiệp vụ.

### 5.1. Ví dụ Java 17 độc lập về ranh giới persistence

Ví dụ sau **không triển khai Quartz**. Nó chỉ đối chiếu một registry trong RAM với một store dạng file để thấy điều gì được giữ khi **tạo lại đối tượng service đại diện tiến trình**. File được tạo trong thư mục tạm, và ví dụ xóa file khi kết thúc. Trong sản phẩm, Quartz JDBC store cung cấp cơ chế lưu trữ, transaction và concurrency thực sự; không thay nó bằng file `Properties` tự viết.

Tạo tệp **`SchedulePersistenceBoundaryDemo.java`**:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SchedulePersistenceBoundaryDemo {
    static final class LocalRegistry {
        private final Map<String, Instant> schedules = new HashMap<>();
        void register(String id, Instant deadline) {
            schedules.put(id, deadline);
        }
        int size() {
            return schedules.size();
        }
    }

    static final class FileStore {
        private final Path path;
        FileStore(Path path) {
            this.path = path;
        }
        void register(String id, Instant deadline) throws IOException {
            Properties properties = readAll();
            properties.setProperty(id, deadline.toString());
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Schedule definitions only");
            }
        }
        Instant find(String id) throws IOException {
            String value = readAll().getProperty(id);
            return value == null ? null : Instant.parse(value);
        }
        int count() throws IOException {
            return readAll().size();
        }
        private Properties readAll() throws IOException {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            }
            return properties;
        }
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("schedule-boundary-", ".properties");
        try {
            String id = "monthly-report";
            Instant deadline = Instant.parse("2026-03-31T02:00:00Z");

            LocalRegistry firstProcess = new LocalRegistry();
            firstProcess.register(id, deadline);
            System.out.println("1. Local schedules before restart: "
                    + firstProcess.size());

            LocalRegistry restartedProcess = new LocalRegistry();
            System.out.println("2. Local schedules after restart: "
                    + restartedProcess.size());

            new FileStore(file).register(id, deadline);
            FileStore reopened = new FileStore(file);
            System.out.println("3. Persisted definitions after restart: "
                    + reopened.count());
            System.out.println("4. Restored deadline: " + reopened.find(id));

            Instant afterOutage = deadline.plus(Duration.ofHours(2));
            boolean late = afterOutage.isAfter(deadline.plus(Duration.ofMinutes(1)));
            System.out.println("5. More than one minute late: " + late);
            System.out.println("6. Example recovery policy: SKIP_TO_NEXT");
            System.out.println("7. Job execution success stored: false");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 SchedulePersistenceBoundaryDemo.java
java SchedulePersistenceBoundaryDemo
~~~

Stdout **đã được biên dịch và kiểm chứng** với Java 17 API:

~~~text
1. Local schedules before restart: 1
2. Local schedules after restart: 0
3. Persisted definitions after restart: 1
4. Restored deadline: 2026-03-31T02:00:00Z
5. More than one minute late: true
6. Example recovery policy: SKIP_TO_NEXT
7. Job execution success stored: false
~~~

Bài học từ các dòng kết quả:

- Registry mới không còn lịch đã tạo trong registry cũ, nếu không có cơ chế đọc lại.
- File có thể được mở từ đối tượng store mới để nạp định nghĩa đã lưu. Đây là mô phỏng **source-of-truth ngoài memory của một đối tượng**; không kiểm thử database, crash thực hoặc tính nhất quán multi-node.
- Việc một deadline bị trễ hơn một ngưỡng là **sự kiện cần ra chính sách**, không tự suy ra phải chạy lại ngay. Chuỗi `SKIP_TO_NEXT` ở đây là **quyết định minh họa của ứng dụng**, không phải enum Quartz.
- File chỉ lưu định nghĩa lịch; nó **không có bảng kết quả thực thi**. Do đó cuối demo không có bằng chứng job đã thành công.
- File demo không an toàn cho ghi đồng thời, lock, retry hoặc failover. **Không dùng nó thay JDBC JobStore.**

## 6. Hai vấn đề phục hồi khác nhau: misfire và job recovery

Một hệ thống có thể bị offline trong lúc trigger đến hạn, hoặc đang thực thi một job thì process chết. Hai trường hợp này **khác nhau** và Quartz có cơ chế tương ứng khác nhau.

### 6.1. Misfire: trigger đã đến hạn nhưng không được kích hoạt đúng giờ

Ví dụ job chạy mỗi ngày 09:00, nhưng hệ thống offline từ 08:50 đến 10:30:

~~~text
08:50   Scheduler offline
09:00   Trigger expected to fire, but no worker is available
10:30   Scheduler resumes, sees persisted trigger
         |
         +-- apply misfire policy
             +-- Fire once now?
             +-- Skip to next scheduled time?
             +-- Other policy appropriate to trigger type?
~~~

Quartz có `misfireThreshold` để phân biệt một trigger bị trễ đủ mức và được coi là misfired; một giá trị thường gặp/mặc định ở tài liệu Quartz 2.x là 60.000 ms. **Threshold không phải chính sách nghiệp vụ**, chỉ xác định khi nào tình huống trễ được xử lý theo các misfire instructions.

Với `CronTrigger`, có những lựa chọn tiêu biểu:

| Chính sách | Quartz API khi dựng CronTrigger | Hành vi khái quát |
| --- | --- | --- |
| Bỏ lượt đã lỡ, đi tới lịch hợp lệ tiếp theo | `withMisfireHandlingInstructionDoNothing()` | Không tạo một lần chạy bù tức thì chỉ vì missed fire |
| Chạy một lần bù khi trở lại | `withMisfireHandlingInstructionFireAndProceed()` | Kích hoạt một lần sớm rồi tiếp tục lịch |
| Bỏ qua misfire policy | `withMisfireHandlingInstructionIgnoreMisfires()` | Có nguy cơ xử lý backlog dồn dập; chỉ dùng nếu hiểu đầy đủ |
| SMART_POLICY mặc định | `Trigger.MISFIRE_INSTRUCTION_SMART_POLICY` | CronTrigger có quy tắc diễn giải riêng; không mặc định đúng với business |

Nếu nghiệp vụ là “báo cáo cuối ngày không được bỏ”, có thể cần chạy bù và ghi rõ phạm vi ngày cần xử lý. Nếu là “gửi thông báo lúc 09:00, đã trễ thì bỏ”, có thể chọn DoNothing. Không có một policy chung đúng cho mọi lịch.

**Quan trọng:** một misfire instruction kiểu “fire once now” **không bảo đảm phát lại riêng từng lượt bị lỡ trong khoảng downtime**, và không tự xác nhận job đã xử lý thành công. Cần lưu business run IDs/period IDs nếu yêu cầu backfill chính xác từng kỳ.

### 6.2. Recovery: job đang chạy thì node chết

Ví dụ Quartz đã acquire trigger, job bắt đầu xử lý và sau đó process bị kill:

~~~text
Node A:
  09:00 acquire trigger
  09:00 start job
  09:01 side effect A committed
  09:02 process crash, job did not report completion

Node B / restarted scheduler:
  detects failed execution (when supported by JobStore/recovery)
  |
  +-- JobDetail requestsRecovery?
        |
        +-- Yes: scheduler may create recovery execution
        +-- No: no automatic "retry this running job" solely from request
~~~

Quartz có thuộc tính `JobDetail.requestsRecovery()`, có thể được đặt khi build bằng `JobBuilder.requestRecovery(true)`. Khi recovery thực sự diễn ra, `JobExecutionContext.isRecovering()` cho biết job là recovery execution.

**Đừng nhầm requestRecovery với misfire.** Misfire giải quyết một trigger **đã đến hạn nhưng chưa fire đúng giờ**. Recovery nói về **một execution đã chạy trên node hỏng** trong điều kiện Quartz phát hiện và hỗ trợ phục hồi. Cả hai cần kiểm thử với JobStore và cluster cụ thể.

**Đừng nhầm recovery với rollback.** Nếu lần cũ đã commit DB/charge payment trước crash, recovery execution có thể chạy lại side effect. Thiết kế idempotency/reconciliation phải giải quyết điều đó.

## 7. Quartz clustering để lịch được quản lý giữa nhiều JVM

Với JDBC JobStore chia sẻ, Quartz có thể chạy trong cluster. Các node dùng:

- **Cùng một bộ bảng Quartz** trên một database chung.
- Cùng scheduler name/instanceName của cluster.
- **InstanceId khác nhau** trên mỗi node, ví dụ dùng `AUTO`.
- `org.quartz.jobStore.isClustered=true` trên mọi node dùng chung bảng.
- Đồng hồ node được đồng bộ và latency/connection pool phù hợp.

Khi một trigger tới hạn, các node phối hợp qua database để tránh hai node cùng acquire **một firing** trong điều kiện hoạt động chuẩn. Nếu node chết, node còn lại có thể phát hiện theo check-in/recovery và tiếp tục quản lý lịch.

Nhưng:

- **Không phải đúng một lần toàn bộ side effect.** Crash sau khi gửi external request nhưng trước khi ghi nhận kết quả vẫn có thể gây xử lý lặp.
- **Không phải không cần database.** JDBC store làm DB thành dependency trọng yếu; nếu DB outage, trigger acquisition/recovery bị ảnh hưởng.
- **Không phải chạy mọi node với store khác nhau.** Nếu mỗi node dùng DB khác, không có một cluster coordination chung.
- **Không dùng một node non-clustered trên cùng bảng với các node clustered.** Quartz cảnh báo nguy cơ hỏng dữ liệu/hành vi khó dự đoán.
- **Không thay thế synchronization dữ liệu nghiệp vụ.** Job có thể bị retry hoặc chạy lại vì recovery.
- **Không phải leader-only:** Quartz có thể phân công các lần trigger sang các node khác nhau theo tải và locking.

### 7.1. @DisallowConcurrentExecution giải quyết điều gì?

Quartz có annotation `@DisallowConcurrentExecution` để tránh các lần thực thi **đồng thời của cùng một JobDetail/JobKey** khi được scheduler tôn trọng, kể cả khi một JobDetail có nhiều triggers.

Nó **không** cấm hai `JobDetail` khác nhau cùng class được chạy đồng thời; cũng không giải quyết trùng side effect sau crash/recovery. Nếu job có khóa nghiệp vụ như `invoiceId`, vẫn cần idempotency/locking/business transaction phù hợp.

Khi kết hợp `@PersistJobDataAfterExecution` để cập nhật `JobDataMap` sau mỗi lần chạy, cần cẩn trọng concurrency, serialization và tính bền vững. Thường **không lưu toàn bộ trạng thái nghiệp vụ quan trọng trong JobDataMap**; dùng DB domain và lưu job id/references nhỏ gọn.

## 8. Cấu hình Spring Boot 3.x để dùng Quartz bền vững

Spring Boot có starter **`spring-boot-starter-quartz`**. Khi Quartz có trong classpath, Boot có thể tạo `Scheduler` thông qua `SchedulerFactoryBean`. Boot tự nhận các bean `JobDetail` và `Trigger` để đăng ký.

Mặc định Boot thường dùng **memory job store**. Khi cần JDBC storage, thêm DataSource và cấu hình có chủ đích:

Tệp **`application.properties`** cho môi trường dùng database đã được tạo schema Quartz bằng migration:

~~~properties
# Quartz persistent schedule state:
spring.quartz.job-store-type=jdbc
spring.quartz.jdbc.initialize-schema=never

# Shared scheduler identity, unique instance id per node:
spring.quartz.scheduler-name=billingScheduler
spring.quartz.properties.org.quartz.scheduler.instanceId=AUTO
spring.quartz.properties.org.quartz.jobStore.isClustered=true

# Example operational settings, tune for deployment:
spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval=15000
spring.quartz.properties.org.quartz.jobStore.misfireThreshold=60000
spring.quartz.properties.org.quartz.threadPool.threadCount=5

# Preserve previously persisted jobs/triggers by default:
spring.quartz.overwrite-existing-jobs=false
spring.quartz.wait-for-jobs-to-complete-on-shutdown=true
~~~

**Điều kiện bắt buộc của cấu hình này:** database có các bảng Quartz **đúng phiên bản/dialect**, mọi node kết nối cùng DB/bảng và được quyền đọc/ghi cần thiết. Đây là **ví dụ Spring Boot 3.x**, không phải cấu hình độc lập cho một chương trình Java thuần. Chỉ bật clustering khi **thật sự dùng JDBC store chia sẻ**; nếu một node duy nhất cũng có thể dùng JDBC mà không cần cluster flag.

### 8.1. Cảnh báo đặc biệt về initialize-schema

Không bật bừa `spring.quartz.jdbc.initialize-schema=always` trên database production. Tài liệu Spring Boot cảnh báo các script chuẩn có thể **DROP bảng Quartz đang có rồi tạo lại**, làm mất jobs/triggers sau restart — đi ngược mục tiêu persistence.

Phương án an toàn:

1. Chọn Quartz library version và RDBMS dialect được hỗ trợ.
2. Dùng migration có kiểm soát (Flyway/Liquibase hoặc quy trình DBA) để tạo/update schema Quartz.
3. Bật `spring.quartz.jdbc.initialize-schema=never` sau khi đã xác nhận bảng có sẵn.
4. Backup trước các thay đổi schema; chạy migration đúng một lần trên môi trường phù hợp.
5. Test restart thực tế trên bản sao DB, kiểm tra triggers/job details còn nguyên.

Chỉ sử dụng `initialize-schema=always` khi **chủ ý reset schema** trong môi trường demo/test và hiểu rõ tác động.

### 8.2. Khi đã có DataSource Spring-managed

Với Spring Boot JDBC store, `SchedulerFactoryBean` dùng integration với Spring-managed `DataSource`. Không tự chèn `org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX` vào properties cùng lúc rồi cho rằng không ảnh hưởng. Spring Framework lưu ý rằng dùng datasource do Spring cung cấp cần cấu hình JobStore tương ứng, và một số settings trùng lặp giữa Java bean và `quartz.properties` gây mâu thuẫn.

Nếu muốn Quartz dùng database riêng, Spring Boot hỗ trợ `@QuartzDataSource` cho DataSource và `@QuartzTransactionManager` cho transaction manager phù hợp. Đừng trộn nhầm schema Quartz với schema domain khi áp dụng migrations/permissions.

### 8.3. Cẩn trọng overwrite-existing-jobs

Khi có JDBC store, source of truth cho lịch có thể là database, trong khi code vẫn khai báo bean `JobDetail`/`Trigger` để bootstrap.

Mặc định `spring.quartz.overwrite-existing-jobs=false` để các job persisted không bị cấu hình bean mỗi lần restart tự động ghi đè. Khi đổi cron trong code, **không mặc định DB trigger sẽ được thay bằng cron mới**. Cần chính sách:

- Lịch quản lý bằng code: có migration/version cho trigger hoặc chủ động overwrite theo quy trình.
- Lịch quản lý qua admin UI/API: DB là source of truth; startup không ghi đè lịch user đã sửa.
- Lịch hỗn hợp: tách namespace/group và ownership để tránh tranh chấp.
- Khi thay cron: ghi audit old/new schedule và test next-fire-time.

Bật `overwrite-existing-jobs=true` mà không xem ownership có thể làm mất những chỉnh sửa tại runtime của người vận hành.

## 9. Spring/Quartz API: Job, JobDetail và Trigger

Ví dụ sau dành cho **Spring Boot 3.x + Quartz 2.x + JDBC store được cấu hình theo mục 8**. Nó **không phải mã có thể biên dịch bằng javac thuần** nếu thiếu Quartz/Spring dependencies.

### 9.1. Một Quartz Job có thể chạy lại

Tệp **`DailyReportJob.java`**:

~~~java
package example.quartz;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@DisallowConcurrentExecution
public class DailyReportJob implements Job {

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        String runKey = context.getJobDetail().getKey().toString();
        boolean recovery = context.isRecovering();
        System.out.println("Job=" + runKey
                + ", scheduled=" + context.getScheduledFireTime()
                + ", actual=" + context.getFireTime()
                + ", recovery=" + recovery);

        // Production: call a business service with idempotency
        // and persist the actual per-period/run outcome.
    }
}
~~~

Code này ghi log **với giá trị runtime thay đổi**, nên không có một stdout cố định. Nó không thể hiện “báo cáo đã xuất thành công”: log chỉ nói job được Quartz invoke vào thời điểm nào. Với application thật, cần dependency injection/job factory phù hợp để gọi business service, giao dịch và status tracking.

`@DisallowConcurrentExecution` tránh chạy đồng thời **cùng JobDetail**, không tránh việc cùng loại job có nhiều JobDetail khác nhau và không tránh recovery retry sau crash.

### 9.2. Khai báo JobDetail và CronTrigger

Tệp **`QuartzScheduleConfiguration.java`**:

~~~java
package example.quartz;

import java.util.TimeZone;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class QuartzScheduleConfiguration {

    @Bean
    public JobDetail dailyReportJobDetail() {
        return JobBuilder.newJob(DailyReportJob.class)
                .withIdentity("dailyReport", "reports")
                .storeDurably()
                .requestRecovery(true)
                .build();
    }

    @Bean
    public Trigger dailyReportTrigger(JobDetail dailyReportJobDetail) {
        return TriggerBuilder.newTrigger()
                .withIdentity("dailyReportTrigger", "reports")
                .forJob(dailyReportJobDetail)
                .withSchedule(
                    CronScheduleBuilder.cronSchedule("0 0 9 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
~~~

Ý nghĩa:

- `JobDetail` xác định **loại job và định danh ổn định**: `reports.dailyReport`.
- `Trigger` xác định lịch **09:00 hằng ngày tại Asia/Ho_Chi_Minh**.
- `.storeDurably()` cho phép JobDetail tồn tại ngay cả khi không còn Trigger tham chiếu; **không tự chuyển RAM thành JDBC**.
- `.requestRecovery(true)` đánh dấu muốn recovery nếu một execution bị gián đoạn trong hoàn cảnh Quartz hỗ trợ. **Chỉ dùng khi job thực sự an toàn để chạy lại hoặc có idempotency**.
- `.withMisfireHandlingInstructionDoNothing()` minh họa policy **không chạy bù tức thời** khi CronTrigger misfire. Chọn policy theo nghiệp vụ, không tự áp dụng cho mọi job.
- Trigger timezone được đặt tường minh; không dựa vào default JVM timezone của từng node.
- Spring Boot nhận bean `JobDetail` và `Trigger` để đăng ký; quyền sở hữu, overwrite và persistence phải tương thích database hiện có.

Nếu nghiệp vụ là xuất báo cáo quan trọng phải bù ngày 09:00 đã lỡ, `DoNothing` có thể **sai yêu cầu**. Khi đó xét `FireAndProceed` hoặc một quy trình backfill theo `businessDate`, cùng trạng thái idempotent.

### 9.3. JobDataMap nên lưu gì?

Quartz cho phép lưu `JobDataMap` gồm các tham số nhỏ, chẳng hạn `tenantId`, `scheduleId` và version. Với JDBC store, dữ liệu này có thể cần serialization qua database.

**Ưu tiên** lưu identifier và cấu hình primitive/String ổn định; tải dữ liệu nghiệp vụ từ database tại lúc chạy. Tránh đưa trực tiếp Spring bean, connection, `EntityManager`, object nhạy cảm hoặc object lớn không tương thích serialization vào JobDataMap. Tùy cấu hình JDBCJobStore, có thể dùng `org.quartz.jobStore.useProperties=true` để lưu các giá trị dạng String, giúp giảm rủi ro deserialize object từ phiên bản cũ.

Không dùng JobDataMap làm nguồn duy nhất cho kết quả nghiệp vụ quan trọng. Dùng bảng status/outcome riêng.

## 10. Quản lý lịch ngoài vòng đời tiến trình

Đề bài nhấn mạnh **“quản lý bên ngoài tiến trình”**: không chỉ lưu cron vào DB, mà còn cho người vận hành hoặc hệ thống điều phối **quản lý một lịch mà không cần restart/deploy JVM**.

Một API quản trị thường cần:

- **Tạo lịch:** nhận business schedule ID, timezone, cron, start/end, owner, trạng thái, quota.
- **Liệt kê/xem:** tra JobKey, TriggerKey, state, nextFireTime/previousFireTime, mô tả nghiệp vụ và run history.
- **Pause/resume:** tạm ngừng/làm tiếp trigger hoặc job, đồng thời giữ persisted state.
- **Reschedule:** cập nhật cron/timezone/misfire policy, version, next fire time và audit.
- **Delete:** ngừng lịch có kiểm soát, quyết định xử lý các execution đang chạy và retention.
- **Manual run:** trigger một lần theo ý người có quyền, với idempotency.
- **Reconcile:** so sánh điều admin mong muốn với scheduler DB thực tế; phát hiện drift.
- **Audit và phân quyền:** ai thay đổi, khi nào, trước/sau, tại sao.

Quartz cung cấp **Scheduler API**, nhưng không tự bảo đảm một REST admin UI hoàn chỉnh có authentication, authorization, tenant isolation và audit. Cần xây lớp quản lý bên ngoài theo nhu cầu hoặc dùng một hệ thống điều phối có sẵn.

### 10.1. Ví dụ admin service qua Scheduler API

Tệp **`QuartzScheduleAdmin.java`**:

~~~java
package example.quartz;

import java.util.TimeZone;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

@Service
public class QuartzScheduleAdmin {
    private final Scheduler scheduler;

    public QuartzScheduleAdmin(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void pauseReport() throws SchedulerException {
        scheduler.pauseJob(JobKey.jobKey("dailyReport", "reports"));
    }

    public void resumeReport() throws SchedulerException {
        scheduler.resumeJob(JobKey.jobKey("dailyReport", "reports"));
    }

    public void moveToTenAM() throws SchedulerException {
        TriggerKey key = TriggerKey.triggerKey("dailyReportTrigger", "reports");
        Trigger old = scheduler.getTrigger(key);
        if (old == null) {
            throw new IllegalStateException("Report trigger is missing");
        }

        Trigger replacement = TriggerBuilder.newTrigger()
                .withIdentity(key)
                .forJob(old.getJobKey())
                .withSchedule(
                    CronScheduleBuilder.cronSchedule("0 0 10 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();

        if (scheduler.rescheduleJob(key, replacement) == null) {
            throw new IllegalStateException("Reschedule did not find old trigger");
        }
    }
}
~~~

Đây là **mã tích hợp Spring/Quartz**, không có method `main` và cần bean `Scheduler` thực tế. Khi gọi `pauseJob`, `resumeJob` hoặc `rescheduleJob` trên JDBC store, Quartz quản lý trạng thái theo JobStore, không phải chỉ một biến trong controller.

Mẫu này cố ý dùng **key cố định và chính sách cố định**, không mở một endpoint công khai cho người dùng nhập key tùy ý. Production phải thêm RBAC, tenant scope, quota, xác thực caller, optimistic/version conflict handling và audit transaction phù hợp. Nếu hai admin cập nhật cùng một trigger đồng thời, việc lấy old trigger rồi reschedule không tự đảm bảo không bị ghi đè cập nhật của người khác.

### 10.2. Không xem pause là hủy job đang chạy

`scheduler.pauseJob(...)` hoặc pause trigger thường ngăn những lần kích hoạt **tương lai** theo hợp đồng. Nó **không mặc định rollback một execution đã chạy**, không cancel remote request và không có nghĩa task đang chạy dừng ngay. Phải thiết kế cancellation/interrupt, completion monitoring và policy “pause có chờ job hiện tại xong không?” theo nghiệp vụ.

Tương tự, xóa trigger khỏi DB không nhất thiết thu hồi một email đã được gửi đi. Hãy tách trạng thái schedule với trạng thái run.

## 11. Khôi phục lịch không đồng nghĩa “exactly once”

Quartz JDBC clustering làm giảm nguy cơ **nhiều node cùng fire một trigger trong điều kiện bình thường**, nhưng không thể tạo exactly-once business side effect chỉ từ cơ chế scheduler.

Ví dụ cần xử lý:

~~~text
09:00 Node A runs payment batch
09:01 Payment provider accepted charge
09:02 Node A crashes before writing success status

Quartz recovery / operator retry
09:03 Node B starts the same logical business operation

Without idempotency:
    payment may be charged twice
With stable idempotency key:
    provider/domain can detect duplicate logical request
~~~

Các biện pháp thường cần:

1. **Stable business key:** ví dụ `tenantId + billingPeriod + actionType`, không dùng execution attempt ID thay cho định danh nghiệp vụ.
2. **Unique constraint/idempotency:** database/remote API từ chối hoặc trả kết quả cũ khi nhận cùng business key.
3. **Status table:** PENDING/RUNNING/SUCCEEDED/FAILED/NEEDS_RECONCILIATION, có runId/attemptId.
4. **Timeout và lease:** job chết giữa chừng có thể được nhận lại; lease hết hạn không tự chứng minh worker cũ đã chết hoàn toàn.
5. **Replay policy:** phân loại retryable vs non-retryable, backoff, giới hạn retry và manual review khi side effect không rõ.
6. **Outbox/inbox:** khi cần đồng bộ bền vững giữa commit domain và ý định phát event, dùng transactional outbox/idempotent consumer.
7. **Compensation:** nếu side effect đã xảy ra và cần sửa nghiệp vụ, dùng bù trừ có audit, không gọi là rollback đồng bộ xuyên tiến trình.

**Job recovery không phải transaction rollback** và cũng không phải một bảo đảm giữ nguyên trạng thái program counter của thread trước crash. Worker mới phải bắt đầu theo contract ứng dụng và xử lý duplicate safely.

### 11.1. @DisallowConcurrentExecution có đủ không?

Không. Annotation giới hạn overlap của **cùng JobKey** khi Quartz điều phối, nhưng:

- Một logical business operation có thể được khởi động qua hai JobKeys khác nhau.
- API manual run có thể gửi cùng business key ở một workflow khác.
- Node crash/recovery có thể lặp side effect từng hoàn thành một phần.
- Remote system và database không nhìn thấy annotation Java như một idempotency lock.

Vì vậy `@DisallowConcurrentExecution` hữu ích, nhưng **không thay unique key, transaction và job status**.

## 12. Vận hành JDBC scheduling như một hệ thống dữ liệu

Một hệ thống Quartz persistent cần quản trị:

### 12.1. Schema, migrations và backup

- Chọn Quartz version/JDBC delegate tương ứng DB dialect.
- Tạo bảng `QRTZ_*` bằng migration đã được review.
- Không chạy script drop/create lại ở mỗi startup production.
- Backup/restore DB theo RPO/RTO; test restore trên môi trường riêng.
- Khi nâng cấp Quartz, kiểm tra migration compatibility của persistent JobDataMap/trigger state.
- Không dùng `DELETE FROM QRTZ_TRIGGERS` thủ công như một API quản trị; Quartz có quan hệ và locking riêng.

### 12.2. Đồng hồ và timezone

- Dùng IANA timezone cho CronTrigger; ví dụ `Asia/Ho_Chi_Minh`.
- Các node cluster phải đồng bộ clock vì thời điểm acquire/misfire/check-in phụ thuộc thời gian.
- Test DST với zone có daylight-saving, đặc biệt giờ local không tồn tại hoặc bị lặp.
- Không copy trực tiếp số thứ trong cron Spring sang Quartz: hai engine có thể đánh số ngày trong tuần khác nhau.
- Phân biệt `scheduledFireTime`, `actualFireTime` và `businessPeriod` để tránh xử lý sai kỳ.

### 12.3. Database capacity và monitoring

- JDBC JobStore cần DB connections và transactions ngay cả khi job nội dung của bạn chạy ở thread pool riêng.
- Monitor connection pool, slow queries, row locks, deadlocks, acquisition latency và misfire backlog.
- Khi có hàng nghìn triggers tới cùng một thời điểm, xét batch acquisition, pool size, quota và phân tán lịch nếu phù hợp.
- Không mặc định tăng thread count giải quyết DB lock hoặc downstream rate limit.
- Theo dõi các QRTZ tables bằng metrics/logs được hỗ trợ, không dùng truy vấn trực tiếp tự sửa state.

### 12.4. Application shutdown và restart

Spring Boot có property `spring.quartz.wait-for-jobs-to-complete-on-shutdown=true` để yêu cầu Quartz chờ những jobs đang chạy lúc shutdown thông thường. Nó **không thể chống SIGKILL hoặc node crash**, và không bảo đảm mọi job kết thúc trước deadline của container.

Khi deploy rolling:

1. Dừng nhận request tạo/sửa lịch mới nếu cần, hoặc route sang node còn active.
2. Để Quartz job đang chạy kết thúc trong graceful budget theo policy.
3. Chờ nguồn phụ thuộc (DB, remote clients) còn sống đủ lâu.
4. Cho cluster node còn lại tiếp tục acquire trigger theo config.
5. Xác nhận run results và xử lý recovery/idempotency sau startup.

Nếu job chạy dài hơn termination grace period, cần thiết kế checkpoint/cancellation/recovery thay vì hứa chờ vô hạn.

## 13. Chọn đúng phương án trước khi thêm Quartz

Một quyết định có thể đi theo cây sau:

~~~text
Lịch có phải dữ liệu cần lưu/điều khiển ngoài vòng đời JVM?
   |
   +-- Không
   |    |
   |    +-- Một instance / chấp nhận missed runs?
   |          |
   |          +-- Có --> Spring @Scheduled / TaskScheduler
   |          +-- Không --> Bổ sung coordination hoặc job store
   |
   +-- Có
        |
        +-- Chỉ cần lưu input rồi tái dựng lịch?
        |       |
        |       +-- Có --> DB domain + startup registration
        |                  (phải tự viết misfire/reconciliation)
        |
        +-- Cần pause/resume, misfire, cluster & recovery?
                |
                +-- Có --> Quartz JDBC JobStore
                         + admin API/RBAC/audit
                         + business status/idempotency
                         + DB operations/backups
~~~

**Quartz không phải lựa chọn bắt buộc** nếu bạn chỉ cần một cron đơn giản. Một bảng domain schedules + service đăng ký lại khi startup cũng có thể đáp ứng những yêu cầu lưu trữ nhẹ; nhưng phải tự xử lý concurrency, missed runs, edits, ownership và shutdown. Khi các yêu cầu đó tăng, Quartz JDBC thường giảm lượng engine logic bạn phải tự viết.

Ngược lại, **Quartz JDBC không tự trở thành orchestrator đa bước** cho workflow gồm nhiều service, compensation phức tạp hoặc user-facing approval. Khi cần orchestration mạnh, có thể cần workflow engine và trạng thái domain riêng.

## 14. Checklist kiểm thử trước khi khẳng định “đã có lịch bền vững”

### 14.1. Persistence và restart

1. Tạo một lịch bằng API runtime, xác nhận xuất hiện qua `Scheduler.getTrigger(...)`.
2. Tắt process và khởi động lại **với cùng JDBC database**.
3. Xác nhận JobKey, TriggerKey, cron, timezone, trạng thái pause và next-fire-time được giữ theo hợp đồng.
4. Sửa cron qua admin API, restart; xác minh `overwrite-existing-jobs=false` không làm mất thay đổi.
5. Kiểm tra property `initialize-schema` không drop bảng/trigger khi restart.

### 14.2. Misfire khi downtime

1. Tạo một CronTrigger có lần chạy dự kiến sớm và set policy `DoNothing`.
2. Tắt scheduler trước khi tới hạn, khởi động lại sau ngưỡng misfire.
3. Xác nhận trigger đi tới lần chạy kế tiếp thay vì tự chạy bù.
4. Lặp lại với `FireAndProceed`, xác nhận xuất hiện một lần bù phù hợp.
5. Test downtime dài qua nhiều kỳ: không suy diễn “chạy bù tất cả” nếu policy chỉ fire once.
6. Test `misfireThreshold` và cron timezone để không nhầm do khác offset/DST.

### 14.3. Cluster và recovery

1. Chạy hai Spring instances với **cùng JDBC tables**, `isClustered=true` và instanceId riêng.
2. Tạo một trigger, kiểm tra một firing không bị hai node cùng acquire trong điều kiện chuẩn.
3. Kill một node giữa một job có `requestsRecovery=true`; quan sát node còn lại phát hiện, recovery execution và `context.isRecovering()`.
4. Test business idempotency: cùng business key được gọi lại nhưng side effect không nhân đôi.
5. Test DB outage, rolling deploy và đồng hồ lệch; kiểm tra alerts, timeout, retry.
6. Tuyệt đối không chạy node non-clustered trên cùng tables đang được cluster sử dụng.

### 14.4. Quản lý và bảo mật

1. Người không có quyền không thể tạo, sửa, pause hoặc delete lịch của tenant khác.
2. Cron input được validate, có limit tần suất và quota tổng số triggers/jobs.
3. Thao tác reschedule concurrent có version/audit, không làm mất update của người khác.
4. Pause không giả vờ cancel job đang chạy.
5. Hủy lịch có trạng thái và hướng xử lý execution dở dang.
6. Logs và status phân biệt scheduled, started, recovered, succeeded, failed, skipped.
7. Các retry/manual runs đều mang business idempotency key để tránh duplicate.

## 15. Những nhầm lẫn dễ gây lỗi lớn

**“Đã dùng Quartz nên lịch tự tồn tại sau restart.”** Sai với `RAMJobStore`. Persistence cần JDBC JobStore hoặc cơ chế lưu độc lập.

**“`JobBuilder.storeDurably()` nghĩa là Quartz ghi xuống DB.”** Sai. Nó giữ JobDetail khi không có trigger tham chiếu, không đổi loại JobStore.

**“@Scheduled luôn mất lịch khi restart.”** Sai nếu lịch nằm trong annotation/code: nó sẽ được đăng ký lại. Nhưng lịch user tạo ở runtime không tự được lưu và các missed executions không tự có recovery.

**“JDBC JobStore thì mọi lần downtime đều được chạy bù.”** Sai. Cần chọn misfire instruction và có thể chỉ fire một lần hoặc bỏ lượt.

**“Misfire và recovery là một.”** Sai. Misfire xử lý trigger đến hạn bị lỡ; recovery nói về execution đang chạy trên node gặp failure.

**“requestsRecovery=true bảo đảm side effect đúng một lần.”** Sai. Reexecution có thể làm trùng side effect nếu job không idempotent.

**“Một Quartz cluster chỉ có một node chạy mọi jobs.”** Sai. Các node có thể phân chia firing qua DB coordination.

**“Cluster ngăn duplicate mọi loại nghiệp vụ.”** Sai. Chỉ điều phối firing; retry và remote side effect cần idempotency.

**“@DisallowConcurrentExecution ngăn mọi instance cùng class chạy.”** Sai. Nó áp dụng cho cùng JobDetail/JobKey, không phải mọi JobDetail khác nhau của cùng class.

**“Có DB schema là đủ, initialize-schema=always vô hại.”** Sai. Boot cảnh báo script chuẩn có thể xóa tables và tất cả triggers khi khởi động lại.

**“overwrite-existing-jobs=true luôn có lợi.”** Sai. Nó có thể ghi đè lịch đã được operator sửa ở runtime.

**“Quartz tự cung cấp admin UI và RBAC.”** Không mặc định. Quartz cung cấp scheduling APIs, còn user management/security/audit là trách nhiệm ứng dụng hoặc hệ quản trị bổ sung.

**“Pause job nghĩa là worker đang chạy đã rollback.”** Sai. Pause chủ yếu ảnh hưởng những lần kích hoạt tiếp; phải quản lý active execution riêng.

**“Quartz scheduler kết thúc nghĩa là email/payment thành công.”** Sai. Kết quả nghiệp vụ cần ACK/commit/status phù hợp.

## 16. Câu hỏi ôn tập và đáp án

**Câu 1.** Một cron dọn cache cấu hình trong code, có thể bỏ một vài lượt khi downtime. Cần Quartz JDBC không?

**Đáp án:** không nhất thiết. `@Scheduled`/`TaskScheduler` thường đủ nếu app tự đăng ký lại khi restart và chấp nhận hợp đồng đơn giản.

**Câu 2.** Lịch được admin tạo lúc runtime, phải sống qua restart và giữ trạng thái pause. Chọn RAMJobStore hay JDBC JobStore?

**Đáp án:** JDBC JobStore hoặc một nguồn lưu lịch bền vững tương đương. RAMJobStore không tự giữ lịch runtime sau process restart.

**Câu 3.** `storeDurably()` trên JobDetail có đủ để sống qua crash không?

**Đáp án:** không. Nó chỉ giữ JobDetail khi không có trigger; persistence qua crash cần persistent JobStore.

**Câu 4.** Scheduler offline qua mốc 09:00, trở lại lúc 10:30. Có mặc nhiên chạy bù mọi lần đã lỡ không?

**Đáp án:** không. Phải chọn misfire instruction và lưu business periods nếu cần backfill từng kỳ.

**Câu 5.** Khác biệt giữa misfire và recovery của Quartz?

**Đáp án:** misfire xử lý trigger bị trễ khi đến hạn; recovery xử lý job execution bị gián đoạn trong tình huống failover phù hợp.

**Câu 6.** Vì sao `requestsRecovery=true` cần idempotency?

**Đáp án:** job cũ có thể đã tạo side effect trước crash; chạy lại có thể lặp business action.

**Câu 7.** Hai node dùng cùng QRTZ tables nhưng một node có `isClustered=false` có an toàn không?

**Đáp án:** không. Với cluster JDBC, các node cùng chia sẻ tables phải cấu hình clustering nhất quán.

**Câu 8.** `@DisallowConcurrentExecution` có đảm bảo hai job khác JobKey không cùng thực hiện một payment không?

**Đáp án:** không. Nó giới hạn overlap của cùng JobDetail/JobKey, không thay idempotency key ở tầng payment.

**Câu 9.** Vì sao không bật `spring.quartz.jdbc.initialize-schema=always` ở production?

**Đáp án:** script tạo schema chuẩn có thể drop/create lại các bảng Quartz, xóa job/triggers persisted khi restart. Dùng migration có kiểm soát.

**Câu 10.** Người dùng đổi cron từ 09:00 sang 10:00 qua admin API, restart khiến cron trở về 09:00. Cần kiểm tra gì?

**Đáp án:** nguồn sở hữu lịch và startup registration, property `overwrite-existing-jobs`, code/config trùng key, logic migration và audit.

**Câu 11.** Quartz JDBC cluster có bảo đảm remote email được gửi đúng một lần không?

**Đáp án:** không. Cần stable business key, outbox/inbox, status và retry idempotent, vì crash có thể xảy ra giữa side effect và ghi nhận completion.

**Câu 12.** Muốn pause một lịch nhưng để job đang chạy hoàn tất thì cần phân biệt gì?

**Đáp án:** pause trigger/job cho các firing mới; active execution vẫn có lifecycle riêng. Không đồng nhất pause với cancel/rollback.

## 17. Tài liệu tham khảo chính thức

- Spring Framework Reference — Task Execution and Scheduling / Using the Quartz Scheduler: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Boot 3.5 Reference — Quartz Scheduler (starter, JDBC store, schema, overwrite): https://docs.spring.io/spring-boot/3.5/reference/io/quartz.html
- Spring Boot 3.5 — Common application properties (quartz settings): https://docs.spring.io/spring-boot/3.5/appendix/application-properties/
- Quartz Java 2.3 — Configuration Reference (JDBC JobStore, clustering): https://www.quartz-scheduler.org/documentation/2.3.1-SNAPSHOT/configuration.html
- Quartz Java — Configure JDBC JobStoreTX: https://www.quartz-scheduler.org/documentation/quartz-2.3.0/configuration/ConfigJobStoreTX.html
- Quartz Java — JobBuilder API, `storeDurably`, `requestRecovery`: https://www.quartz-scheduler.org/api/2.3.0/org/quartz/JobBuilder.html
- Quartz Java — CronScheduleBuilder API (misfire policies): https://www.quartz-scheduler.org/api/2.3.0/org/quartz/CronScheduleBuilder.html
- Quartz Java — Tutorial Lesson 4: triggers and misfires: https://www.quartz-scheduler.org/documentation/quartz-2.4.x/tutorials/tutorial-lesson-04.html
- Quartz Java — Tutorial Lesson 6: CronTrigger and misfire instructions: https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html
- Java SE 17 — Files and Path: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="quartz-integration-002">Vai trò JobDetail, Trigger và Scheduler trong Quartz</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò JobDetail, Trigger và Scheduler trong Quartz

## 1. Tư duy nền tảng: việc gì, khi nào, và ai điều phối?

Quartz là một thư viện lập lịch công việc cho Java. Khi tích hợp với Spring Framework, Quartz không chỉ cung cấp một annotation để chạy method theo giờ mà còn tách rõ **định nghĩa công việc**, **quy tắc kích hoạt** và **bộ máy điều phối**.

Ba thành phần cần nhớ:

- **`JobDetail`:** định nghĩa **công việc nào** sẽ chạy: lớp `Job`, định danh, mô tả, `JobDataMap`, tính bền (durability) và yêu cầu recovery. Nó là metadata/descriptor, **không phải đối tượng Job đang thực thi**.
- **`Trigger`:** xác định **khi nào** `JobDetail` được kích hoạt, định danh trigger, `JobKey` liên kết, start/end time, priority, timezone, misfire policy và tùy loại trigger có lịch lặp hoặc cron.
- **`Scheduler`:** bộ điều phối sở hữu các đăng ký JobDetail/Trigger trong `JobStore`, tìm fire times, dùng worker threads/JobFactory để tạo và gọi `Job.execute(JobExecutionContext)`, cùng các API start, pause, reschedule và shutdown.

~~~text
                        Quartz Scheduler
                   +-------------------------+
register            |                         |
JobDetail --------->| JobStore: định nghĩa    |
  JobKey            |  JobDetail + Trigger    |
  Job class         |                         |
  JobDataMap        | Thread pool / JobFactory |
                    +-----------+-------------+
                                ^
register                       |
Trigger -----------------------+
  TriggerKey
  forJob(JobKey)
  startTime / repeat / cron
                                |
                         Trigger đến giờ
                                v
                    tạo Job instance cho fire
                                |
                                v
                    Job.execute(context)
                                |
                         cập nhật trạng thái
                    và fire time kế tiếp
~~~

**Phân biệt với Spring:**

- Quartz `org.quartz.Scheduler` **không phải** Spring `org.springframework.scheduling.TaskScheduler`.
- Quartz `org.quartz.Trigger` **khác** Spring `org.springframework.scheduling.Trigger`.
- Spring `SchedulerFactoryBean` là cầu nối tạo và quản lý **Quartz Scheduler** như một bean, không tự biến `@Scheduled` thành Quartz job.
- Một `@Scheduled` method thường đủ cho tác vụ đơn giản trong một JVM. Quartz hữu ích khi cần định danh/điều khiển các jobs và triggers độc lập, cron/misfire phức tạp, hoặc JobStore JDBC được cấu hình để lưu đăng ký bền vững.

Bài nằm trong nhóm **Tích hợp Quartz** của **Spring Framework – concurrency**. Trọng tâm là ba vai trò JobDetail/Trigger/Scheduler, quan hệ khóa định danh, thứ tự chạy, cấu hình Spring, ví dụ Java 17 và những ngộ nhận khi vận hành.

## 2. Job, JobDetail và lần chạy Job là ba khái niệm khác nhau

Đầu tiên phải tách **`Job` interface** và **`JobDetail`**:

~~~java
public final class ReportJob implements Job {

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        String type = context.getMergedJobDataMap()
                .getString("reportType");
        generateReport(type);
    }
}
~~~

Đây là **mảnh code minh họa**: cần import `org.quartz.Job`, `JobExecutionContext`, `JobExecutionException` và định nghĩa `generateReport`. Quartz gọi `execute` khi một trigger fire; method không cần có `@Scheduled`.

`JobDetail` định nghĩa *một job cụ thể*:

~~~java
JobDetail detail = JobBuilder.newJob(ReportJob.class)
        .withIdentity("daily-report", "finance")
        .withDescription("Tạo báo cáo hàng ngày")
        .usingJobData("reportType", "SALES")
        .storeDurably()
        .build();
~~~

Đây là mảnh code minh họa với imports `JobDetail`, `JobBuilder`. Nó **không thực thi** `ReportJob.execute`. Việc gọi `build()` mới tạo descriptor; cần Scheduler đăng ký và một trigger/explicit fire thích hợp để công việc bắt đầu.

### 2.1. Quartz tạo Job instance theo từng execution

Đối với `JobFactory` mặc định của Quartz, khi trigger fire, Quartz tạo **Job instance mới** từ class được ghi trong JobDetail rồi gọi `execute(context)`. Các field mutable đặt trong một instance Job **không nên được dùng như state bền giữa các lần chạy**.

~~~text
JobDetail JobKey=finance.daily-report, class=ReportJob
          |
          +-- fire #1 -> new ReportJob() -> execute(ctx1) -> kết thúc
          |
          +-- fire #2 -> new ReportJob() -> execute(ctx2) -> kết thúc
          |
          +-- fire #3 -> new ReportJob() -> execute(ctx3) -> kết thúc
~~~

Với JobFactory mặc định, job class cần constructor không tham số có thể truy cập. Một `@Service` Spring có constructor injection **không được mặc định Quartz tạo như Spring bean** chỉ vì class có annotation `@Component`. Muốn Spring inject dependencies cho các Job instances, phải cấu hình `JobFactory` phù hợp hoặc dùng `QuartzJobBean`/setter-based configuration/adapter; xem mục 11.

### 2.2. Một job class có thể có nhiều JobDetails

~~~java
JobDetail north = JobBuilder.newJob(ReportJob.class)
        .withIdentity("report-north", "regions")
        .usingJobData("region", "NORTH")
        .build();

JobDetail south = JobBuilder.newJob(ReportJob.class)
        .withIdentity("report-south", "regions")
        .usingJobData("region", "SOUTH")
        .build();
~~~

Đây là mảnh code minh họa. Hai JobDetails dùng cùng `ReportJob.class`, nhưng có **JobKeys khác nhau** và `JobDataMap` riêng. Quartz xem chúng là **hai định nghĩa job độc lập**. Khi cần giới hạn chạy đồng thời theo job identity, sự khác biệt này rất quan trọng.

## 3. JobDetail lưu những gì?

| Thành phần | Thuộc JobDetail? | Ý nghĩa |
| --- | --- | --- |
| `JobKey` | Có | Định danh theo cặp `name` + `group` |
| `jobClass` | Có | Class implement `Job` được Quartz khởi tạo khi fire |
| `description` | Có | Metadata mô tả phục vụ quản trị |
| `JobDataMap` | Có | Tham số/trạng thái gắn với định nghĩa job |
| `durability` | Có | Có giữ JobDetail khi không còn trigger nào tham chiếu không |
| `requestsRecovery` | Có | Có yêu cầu recovery khi thất bại đột ngột theo JobStore/cluster policy không |
| Cron expression | **Không** | Thuộc `CronTrigger` |
| Repeat interval | **Không** | Thuộc `SimpleTrigger` |
| Next fire time | **Không** | Thuộc trạng thái `Trigger` |
| Thread pool size | **Không** | Thuộc cấu hình Scheduler |
| Lỗi của lần chạy hiện tại | Không phải bản thân JobDetail | Quan sát qua `JobExecutionContext`/listeners/job store ứng dụng |

Ví dụ hai định danh:

~~~java
JobKey jobKey = JobKey.jobKey("daily-report", "finance");
TriggerKey triggerKey = TriggerKey.triggerKey("morning", "finance");
~~~

Các `JobKey`/`TriggerKey` độc lập; việc cùng group name là **quy ước quản lý**, không phải yêu cầu. Mỗi key duy nhất trong namespace loại tương ứng của một Scheduler. Trigger liên kết tới job bằng **`Trigger.getJobKey()`**, không phải bằng suy đoán tên group.

### 3.1. Durable và non-durable JobDetail

- **Non-durable job:** nếu không còn trigger nào trỏ tới JobDetail, Quartz có thể tự loại JobDetail khỏi JobStore theo contract.
- **Durable job:** `storeDurably()` giữ định nghĩa job kể cả khi chưa có trigger. Phù hợp khi muốn đăng ký JobDetail trước và gắn/xóa triggers linh hoạt, hoặc kích hoạt ad hoc.

~~~java
JobDetail durable = JobBuilder.newJob(ReportJob.class)
        .withIdentity("report", "ops")
        .storeDurably()
        .build();

scheduler.addJob(durable, false);
~~~

Đây là mảnh code minh họa với `Scheduler scheduler` đã tồn tại; `addJob` đăng ký JobDetail độc lập với trigger. Nếu job không durable mà không có trigger, API đăng ký có thể từ chối hoặc Quartz xóa sau khi orphaned theo contract. **Durable không đồng nghĩa bền qua restart**: nếu vẫn dùng `RAMJobStore`, definitions biến mất khi JVM chết; chỉ JobStore persistent được cấu hình phù hợp mới lưu bền qua process restart.

### 3.2. RequestsRecovery không bảo đảm exactly-once

`JobBuilder.requestRecovery()` đánh dấu yêu cầu Quartz khôi phục một execution bị gián đoạn do scheduler/node thất bại **khi JobStore và cơ chế recovery hỗ trợ**. Đó không phải bảo đảm side effect đã rollback, cũng không tự tạo exactly-once processing. Job phải tự xử lý idempotency, checkpoints hoặc job status khi có thể bị chạy lại sau crash.

## 4. JobDataMap: dữ liệu nào thuộc JobDetail, dữ liệu nào thuộc Trigger?

`JobDataMap` là cấu trúc map mở rộng do Quartz cung cấp. Nó có thể nằm trên **JobDetail** hoặc **Trigger**:

~~~java
JobDetail detail = JobBuilder.newJob(ReportJob.class)
        .withIdentity("report", "finance")
        .usingJobData("tenant", "acme")
        .usingJobData("format", "CSV")
        .build();

Trigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("morning", "finance")
        .forJob(detail)
        .usingJobData("format", "PDF")
        .startNow()
        .build();
~~~

Mảnh code này chỉ mô tả metadata; không nên đăng ký nhiều lần cùng identity khi chạy thật.

Trong `execute`:

~~~java
JobDataMap jobValues =
        context.getJobDetail().getJobDataMap();

JobDataMap triggerValues =
        context.getTrigger().getJobDataMap();

JobDataMap merged =
        context.getMergedJobDataMap();
~~~

Khi cùng một key xuất hiện ở hai nơi, **Trigger JobDataMap có ưu tiên** trong map merged. Ở ví dụ, `merged.getString("format")` sẽ là `"PDF"`, trong khi `jobValues.getString("format")` vẫn là `"CSV"`.

**Khuyến nghị:** dùng JobDetail map cho dữ liệu chung của job, Trigger map cho tham số gắn với lần/lịch kích hoạt cụ thể. Với JobStore JDBC, tránh lưu những object không thể serialize hoặc phụ thuộc version class; ưu tiên String, primitive, ID và tham chiếu tới dữ liệu lớn trong DB/object storage. Đừng đưa connection, Spring ApplicationContext hoặc service object trực tiếp vào persistent JobDataMap.

### 4.1. Cập nhật JobDataMap sau execution

Mặc định, việc sửa một instance JobDataMap trong code không nên được hiểu là cơ chế persist toàn bộ state bền cho lần sau. Quartz có annotation:

~~~java
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
public class CounterJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap map = context.getJobDetail().getJobDataMap();
        int count = map.getInt("count");
        map.put("count", count + 1);
    }
}
~~~

Đây là mảnh Java minh họa, cần khởi tạo `count` và imports. `@PersistJobDataAfterExecution` bảo Quartz cập nhật copy JobDetail map **sau execution thành công**, và `@DisallowConcurrentExecution` ngăn các executions chồng lấp của **cùng một JobDetail/JobKey**, giảm nguy cơ lost update. Với JDBC persistent store, transaction/locking và node configuration vẫn phải đúng. Nếu cần state quan trọng, ứng dụng vẫn nên dùng storage và transaction business chuyên biệt.

## 5. Trigger: khi nào một JobDetail được gọi?

`Trigger` biểu diễn **quy tắc kích hoạt** công việc, không phải công việc. Nó có identity `TriggerKey` và liên kết tới một `JobKey`. Một trigger chỉ trỏ đến **một job**, nhưng **một JobDetail có thể có nhiều triggers**.

~~~text
Trigger "weekday-9am" -----+
                           |
Trigger "end-of-month" ----+--> JobDetail "generate-report"
                           |      |
Trigger "manual-once" -----+      +--> ReportJob.execute(ctx)
~~~

Đây là lợi ích quan trọng của việc tách trigger khỏi job: bạn có thể thay thời gian chạy mà **không phải sửa job class**, hoặc gắn hai lịch độc lập vào cùng công việc.

### 5.1. Các thuộc tính chung của Trigger

- **`TriggerKey`:** định danh trigger qua `name` + `group`.
- **`JobKey`:** liên kết đến JobDetail sẽ thực thi.
- **`startTime`, `endTime`:** thời gian áp dụng lịch.
- **`priority`:** dùng để ưu tiên khi có nhiều triggers cùng cần fire mà pool không đủ threads; không tạo thêm CPU hay đảm bảo deadline tuyệt đối.
- **`JobDataMap`:** tham số riêng của trigger, được merged với JobDetail map khi execute.
- **`misfireInstruction`:** cách ứng xử nếu lịch đã bị lỡ trong điều kiện được Quartz xem là misfire.
- **`Calendar` exclusions:** có thể bỏ qua thời điểm nghỉ lễ/ngày không hợp lệ theo Quartz `Calendar`.

`Trigger` còn có `getNextFireTime()`, `getPreviousFireTime()` và trạng thái qua Scheduler. Đừng nhầm metadata Trigger với kết quả nghiệp vụ của lần chạy.

### 5.2. SimpleTrigger: một lần hoặc lặp theo khoảng thời gian

~~~java
Trigger oneShot = TriggerBuilder.newTrigger()
        .withIdentity("run-now", "ops")
        .forJob(JobKey.jobKey("report", "ops"))
        .startNow()
        .build();
~~~

Mảnh code trên tạo **one-shot Trigger theo cấu hình mặc định**. Nó không chạy nếu chỉ được `build()`; cần đăng ký qua Scheduler.

Ví dụ lặp:

~~~java
Trigger everyMinute = TriggerBuilder.newTrigger()
        .withIdentity("every-minute", "ops")
        .forJob(JobKey.jobKey("report", "ops"))
        .startNow()
        .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(1)
                        .repeatForever())
        .build();
~~~

Mảnh này chỉ tạo Trigger; `repeatForever()` khiến lịch tiếp tục cho tới khi được unschedule/pausing, hết endTime hoặc có thay đổi khác. `SimpleTrigger` phù hợp với "sau 10 giây", "mỗi 1 phút", "lặp N lần"; không diễn tả đầy đủ lịch calendar kiểu thứ Hai 9 giờ.

### 5.3. CronTrigger: các mốc theo lịch

~~~java
Trigger weekdayMorning = TriggerBuilder.newTrigger()
        .withIdentity("weekday-9", "ops")
        .forJob(JobKey.jobKey("report", "ops"))
        .withSchedule(
                CronScheduleBuilder.cronSchedule(
                        "0 0 9 ? * MON-FRI")
                        .inTimeZone(TimeZone.getTimeZone(
                                "Asia/Ho_Chi_Minh")))
        .build();
~~~

Đây là mảnh code minh họa, cần `TimeZone` và Quartz imports. Quartz cron có **sáu trường bắt buộc, trường năm tùy chọn thứ bảy**: giây, phút, giờ, ngày trong tháng, tháng, thứ, [năm]. Dấu `?` nghĩa là không chỉ định cụ thể ngày trong tháng hoặc thứ; nó được Quartz sử dụng để tránh xung đột hai trường lịch.

Lịch `"0 0 9 ? * MON-FRI"` nghĩa là **09:00 các ngày thứ Hai–thứ Sáu** theo timezone được chọn, không phải 09:00 theo timezone server trong mọi trường hợp.

**Không copy nguyên một cron string từ Spring `@Scheduled` sang Quartz mà không kiểm tra.** Dù cả hai thường nhận sáu trường, quy tắc parser và các ký hiệu đặc biệt có khác biệt; ví dụ `?` cần được hiểu trong Quartz. Validation phải dùng **Quartz CronExpression** cho Quartz, không chỉ một parser của Spring.

## 6. Scheduler là "người điều phối" thực sự

`org.quartz.Scheduler` kết nối JobDetail, Trigger, JobStore, ThreadPool và JobFactory. Đây là đối tượng API qua đó ứng dụng thực hiện:

~~~java
scheduler.scheduleJob(jobDetail, trigger);        // Đăng ký
scheduler.start();                                // Bắt đầu xử lý triggers
scheduler.pauseTrigger(triggerKey);               // Tạm ngừng một trigger
scheduler.resumeTrigger(triggerKey);              // Tiếp tục một trigger
scheduler.pauseJob(jobKey);                       // Tạm ngừng triggers của job
scheduler.resumeJob(jobKey);                      // Tiếp tục
scheduler.unscheduleJob(triggerKey);              // Bỏ trigger
scheduler.deleteJob(jobKey);                      // Xóa job cùng triggers liên quan
scheduler.shutdown(true);                         // Đợi job đang thực thi kết thúc
~~~

Đây là **mảnh code minh họa**, các keys và các đối tượng phải tồn tại, methods có thể ném `SchedulerException`. Không chạy cả các lệnh trên liên tiếp trong production; chúng minh họa API có thể dùng để quản trị.

### 6.1. Vì sao phải start Scheduler?

Quartz Scheduler được tạo/đăng ký trước không có nghĩa nó **đã fire**. Với Quartz thuần, bạn thường gọi `scheduler.start()` sau khi tạo Scheduler và đăng ký lịch.

Trong Spring, `SchedulerFactoryBean` quản lý lifecycle của Quartz Scheduler và **mặc định tự start theo lifecycle Spring** nếu `autoStartup` được bật. Nếu ứng dụng tắt autoStartup để kiểm thử hoặc quản trị, phải gọi `start()` ở thời điểm phù hợp.

### 6.2. Một chu trình thực thi

~~~text
1. Ứng dụng tạo JobDetail:
       JobKey=finance.report
       jobClass=ReportJob
       JobDataMap={region=VN}

2. Ứng dụng tạo Trigger:
       TriggerKey=finance.weekday-9
       jobKey=finance.report
       cron="0 0 9 ? * MON-FRI"

3. Scheduler đăng ký cả hai:
       scheduleJob(jobDetail, trigger)

4. Scheduler đã STARTED, đến 09:00:
       JobStore cho phép acquire trigger fire
       ThreadPool cấp worker
       JobFactory tạo ReportJob instance
       execute(JobExecutionContext)

5. Sau execution:
       Quartz xử lý completion/exception
       Cập nhật nextFireTime, trigger state, listeners
       Sau đó xử lý lần fire kế tiếp theo lịch
~~~

**Không có JobDetail hoặc Trigger hợp lệ** thì scheduler không biết cần chạy điều gì/khi nào. **Không có Scheduler active** thì metadata đơn thuần không tự thực thi.

### 6.3. Scheduler không đồng nghĩa thread pool

Một Scheduler phối hợp **JobStore**, **ThreadPool**, **JobFactory**, listeners và trigger acquisition. Thread pool chỉ chạy actual Job instances. Tăng `org.quartz.threadPool.threadCount` không tự sửa database pool exhaustion, cron syntax sai hay misfire policy. Với nhiều triggers cùng fire, giới hạn đồng thời phụ thuộc số worker, `@DisallowConcurrentExecution` theo JobKey, locks và downstream services.

## 7. JobKey và TriggerKey: quản lý đúng đối tượng

`JobKey` và `TriggerKey` là hai namespace định danh riêng. Các ví dụ:

~~~java
JobKey job = JobKey.jobKey("month-close", "accounting");
TriggerKey morning = TriggerKey.triggerKey("morning", "accounting");
TriggerKey evening = TriggerKey.triggerKey("evening", "accounting");
~~~

Có thể đăng ký hai triggers khác nhau cùng trỏ `job`. Nếu chỉ muốn đổi giờ chạy buổi sáng, sử dụng `rescheduleJob(morning, ...)` hoặc `unscheduleJob(morning)`, không `deleteJob(job)`.

### 7.1. Reschedule một Trigger mà giữ JobDetail

~~~java
Trigger replacement = TriggerBuilder.newTrigger()
        .withIdentity("morning", "accounting")
        .forJob(JobKey.jobKey("month-close", "accounting"))
        .withSchedule(
                CronScheduleBuilder.cronSchedule("0 30 9 ? * MON-FRI"))
        .build();

Date next = scheduler.rescheduleJob(
        TriggerKey.triggerKey("morning", "accounting"),
        replacement);
~~~

Đây là **mảnh code minh họa**, giả định trigger cũ tồn tại và job đã đăng ký. `rescheduleJob` thay thế registration trigger tương ứng và trả thời điểm fire tiếp theo theo API (có thể null nếu không thể tạo). Nó **không tự sửa job class hoặc JobDataMap của JobDetail**.

Không tạo một trigger mới với identity mới rồi quên unschedule trigger cũ nếu mục tiêu chỉ là **thay** lịch; khi ấy cả hai triggers có thể chạy cùng job. Hãy giữ `TriggerKey` phù hợp và kiểm tra `Scheduler.checkExists(...)`/`getTrigger(...)`.

### 7.2. Pause, unschedule và delete khác nhau

| Hành động | Tác dụng lên lịch/metadata | Lưu ý |
| --- | --- | --- |
| `pauseTrigger(key)` | Không cho trigger đó fire trong khi pause | Không hủy execution đang chạy |
| `resumeTrigger(key)` | Tiếp tục xử lý trigger | Misfire có thể phát sinh khi resume |
| `unscheduleJob(triggerKey)` | Xóa trigger chỉ định | Non-durable JobDetail có thể bị xóa nếu không còn trigger |
| `deleteJob(jobKey)` | Xóa JobDetail và triggers gắn với nó | Không dùng nếu chỉ muốn đổi một giờ chạy |
| `triggerJob(jobKey)` | Yêu cầu fire job một lần ngay | Không cần đổi cron ban đầu |
| `shutdown(true)` | Đóng Scheduler và chờ running jobs hoàn tất | Không có nghĩa rollback side effects |

### 7.3. Durable JobDetail cho dynamic trigger management

Nếu admin thường xuyên tạo/xóa triggers, `storeDurably()` giúp JobDetail tồn tại khi tạm thời chưa có lịch. Nếu JobDetail non-durable và bạn unschedule trigger cuối, job có thể bị Quartz dọn khỏi JobStore; một lần `scheduler.triggerJob(jobKey)` sau đó sẽ thất bại vì job definition không còn.

## 8. Ví dụ đầy đủ Java 17 + Spring 6.1.14 + Quartz 2.3.2

Ví dụ này minh họa chính xác ba vai trò và tích hợp Spring mà **không lệ thuộc một khoảng sleep mơ hồ**.

- Spring `@Configuration` tạo hai metadata beans: `JobDetail` và `Trigger`.
- Spring `SchedulerFactoryBean` **đăng ký** chúng vào Quartz Scheduler.
- `setAutoStartup(false)` cho phép kiểm tra metadata **trước khi start** Scheduler.
- Sau khi gọi `scheduler.start()`, trigger one-shot fire và Quartz tạo một `Job` object để chạy.
- `CountDownLatch` xác nhận execution đã xảy ra trước khi in kết quả.
- JobDetail được đánh dấu `storeDurably()` để còn tồn tại sau one-shot execution.
- Đây là demo `RAMJobStore` in-memory; dữ liệu không tự tồn tại qua JVM restart.

**Yêu cầu:** JDK 17+, Gradle 8.5+, Spring Framework **6.1.14**, Quartz **2.3.2**.

### 8.1. Cấu trúc project

~~~text
quartz-roles-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/QuartzRolesDemo.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'quartz-roles-demo'
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
    implementation 'org.springframework:spring-context-support:6.1.14'
    implementation 'org.quartz-scheduler:quartz:2.3.2'
}

application {
    mainClass = 'demo.QuartzRolesDemo'
}
~~~

**Chú ý dependency:** Spring Quartz integration classes nằm trong **`spring-context-support`**, không chỉ `spring-context`. Dự án còn cần Quartz artifact để có các kiểu `org.quartz.*`.

### 8.2. src/main/java/demo/QuartzRolesDemo.java

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

public final class QuartzRolesDemo {

    private static final JobKey JOB_KEY =
            JobKey.jobKey("report", "training");

    private static final TriggerKey TRIGGER_KEY =
            TriggerKey.triggerKey("run-once", "training");

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            // SchedulerFactoryBean expose ra org.quartz.Scheduler.
            Scheduler scheduler = context.getBean(Scheduler.class);

            JobDetail detail = scheduler.getJobDetail(JOB_KEY);
            Trigger trigger = scheduler.getTrigger(TRIGGER_KEY);

            boolean detailRegistered = detail != null;
            boolean triggerBound =
                    trigger != null && JOB_KEY.equals(trigger.getJobKey());
            boolean initiallyStopped = !scheduler.isStarted();
            boolean correctJobClass =
                    detail != null && detail.getJobClass() == ReportJob.class;

            // Chỉ START ở đây: metadata trước đó đã tồn tại.
            scheduler.start();

            if (!ReportJob.FINISHED.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Quartz job timeout");
            }

            System.out.println("job detail registered: " +
                    detailRegistered);
            System.out.println("trigger points to job: " +
                    triggerBound);
            System.out.println("scheduler initially stopped: " +
                    initiallyStopped);
            System.out.println("job class configured: " +
                    correctJobClass);
            System.out.println("scheduler started: " +
                    scheduler.isStarted());
            System.out.println("job executed once: " +
                    (ReportJob.RUNS.get() == 1));
            System.out.println("job data delivered: " +
                    ReportJob.DATA_OK.get());
            System.out.println("durable job retained: " +
                    scheduler.checkExists(JOB_KEY));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public JobDetail reportJobDetail() {
            return JobBuilder.newJob(ReportJob.class)
                    .withIdentity(JOB_KEY)
                    .usingJobData("reportType", "SALES")
                    .storeDurably()
                    .build();
        }

        @Bean
        public Trigger runOnceTrigger(JobDetail reportJobDetail) {
            return TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_KEY)
                    .forJob(reportJobDetail)
                    .startNow()
                    .build();
        }

        @Bean
        public SchedulerFactoryBean quartzScheduler(
                JobDetail reportJobDetail, Trigger runOnceTrigger) {

            SchedulerFactoryBean factory =
                    new SchedulerFactoryBean();
            factory.setSchedulerName("KnowledgeQuartzDemo");
            factory.setJobDetails(reportJobDetail);
            factory.setTriggers(runOnceTrigger);
            factory.setAutoStartup(false);
            factory.setWaitForJobsToCompleteOnShutdown(true);
            return factory;
        }
    }

    public static class ReportJob implements Job {
        public static final CountDownLatch FINISHED =
                new CountDownLatch(1);
        public static final AtomicInteger RUNS =
                new AtomicInteger();
        public static final AtomicBoolean DATA_OK =
                new AtomicBoolean(false);

        public ReportJob() {
            // Quartz default JobFactory cần constructor truy cập được.
        }

        @Override
        public void execute(JobExecutionContext context) {
            JobDataMap data = context.getMergedJobDataMap();

            DATA_OK.set(
                    "SALES".equals(data.getString("reportType"))
                    && JOB_KEY.equals(
                            context.getJobDetail().getKey())
                    && TRIGGER_KEY.equals(
                            context.getTrigger().getKey()));

            RUNS.incrementAndGet();
            FINISHED.countDown();
        }
    }
}
~~~

### 8.3. Chạy và stdout

Từ thư mục `quartz-roles-demo`:

~~~bash
gradle run
~~~

Nếu có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

**Tám dòng stdout do chương trình tự in khi Quartz start và job thực thi trước timeout:**

~~~text
job detail registered: true
trigger points to job: true
scheduler initially stopped: true
job class configured: true
scheduler started: true
job executed once: true
job data delivered: true
durable job retained: true
~~~

Quartz/Spring/Gradle có thể in thêm logs. Những boolean này xác minh metadata và lifecycle trên đường chạy thành công; nếu job chưa thực thi trong 10 giây, chương trình ném exception thay vì in thành công giả.

### 8.4. Giải thích từng thành phần trong code

1. `Config.reportJobDetail()` tạo descriptor với `JobKey("report","training")`, `ReportJob.class` và `JobDataMap{"reportType":"SALES"}`. Chưa có execution nào xảy ra.
2. `Config.runOnceTrigger(...)` tạo Trigger có `TriggerKey("run-once","training")` và `.forJob(reportJobDetail)`. Trigger quyết định fire one-shot; bản thân nó không chứa code nghiệp vụ.
3. `SchedulerFactoryBean` có cả JobDetail và Trigger, đăng ký chúng vào Quartz Scheduler dưới sự quản lý của Spring context.
4. `autoStartup=false` khiến Scheduler **không tự bắt đầu** khi context được refresh, nên main có thể query `getJobDetail` và `getTrigger` trước khi fire.
5. `scheduler.start()` cho phép Quartz xử lý trigger; JobFactory tạo một `ReportJob` instance, gọi `execute(context)`.
6. `JobExecutionContext` cung cấp cả JobDetail, Trigger và merged JobDataMap. Worker kiểm tra JobKey/TriggerKey/reportType, đếm lần chạy rồi giảm latch.
7. `CountDownLatch.await(timeout)` đảm bảo kết quả chỉ được in sau khi execution đã tới điểm đồng bộ. Do trigger one-shot, `RUNS==1` là kỳ vọng trên một process sạch.
8. `storeDurably()` khiến JobDetail vẫn tồn tại sau khi one-shot trigger kết thúc và không còn trigger nào tham chiếu.
9. Đóng `AnnotationConfigApplicationContext` khiến Spring quản lý việc shutdown `SchedulerFactoryBean`. Code không tự gọi `Scheduler.shutdown()` trên bean dùng chung.

Lưu ý: demo dùng static latch/counters **chỉ để test**, không phải state persistence hay cách lưu kết quả business job. Nếu bạn muốn lặp lại demo nhiều lần trong cùng một JVM/ApplicationContext, phải quản lý việc reset static test state, key uniqueness và scheduler registration.

## 9. Spring Framework cung cấp gì để tích hợp Quartz?

Spring cung cấp một số lớp trong package `org.springframework.scheduling.quartz`:

| Lớp | Vai trò | Khi nên dùng |
| --- | --- | --- |
| `SchedulerFactoryBean` | Tạo/quản lý Quartz Scheduler thành Spring bean, đăng ký triggers/jobs, lifecycle | Entry point tích hợp Spring |
| `JobDetailFactoryBean` | Tạo `JobDetail` bằng cấu hình bean properties | Khi muốn cấu hình theo bean-style/XML hoặc tạo metadata thuận tiện |
| `SimpleTriggerFactoryBean` | Tạo Quartz SimpleTrigger từ JobDetail/interval | Lịch one-shot/fixed repeat |
| `CronTriggerFactoryBean` | Tạo Quartz CronTrigger từ JobDetail/cron/zone | Lịch calendar |
| `QuartzJobBean` | Base class giúp map JobDataMap/properties vào Job instance | Khi cần pattern setter injection |
| `SpringBeanJobFactory` | Tạo Job instances có hỗ trợ bean property population | Khi cần liên kết Quartz Jobs với Spring bean properties |
| `SchedulerAccessorBean` | Đăng ký JobDetails/Triggers vào một Scheduler đã có | Tích hợp với Scheduler bên ngoài |

Không bắt buộc sử dụng toàn bộ các `FactoryBean` wrappers. Với Java config, bạn có thể tạo `JobDetail` bằng `JobBuilder` và `Trigger` bằng `TriggerBuilder` như demo, rồi đưa chúng vào `SchedulerFactoryBean`.

### 9.1. Cấu hình JobDetailFactoryBean

~~~java
@Bean
public JobDetailFactoryBean settlementJobDetail() {
    JobDetailFactoryBean factory = new JobDetailFactoryBean();
    factory.setJobClass(SettlementJob.class);
    factory.setName("settlement");
    factory.setGroup("finance");
    factory.setDurability(true);
    return factory;
}
~~~

Đây là **mảnh cấu hình**, yêu cầu `SettlementJob` implement Quartz Job/QuartzJobBean và imports phù hợp. Spring `FactoryBean` tạo ra product `JobDetail`; khi inject `JobDetail` theo type, Spring thường sử dụng sản phẩm này. Khi có nhiều JobDetail, dùng `@Qualifier` để tránh chọn nhầm.

### 9.2. Cấu hình CronTriggerFactoryBean

~~~java
@Bean
public CronTriggerFactoryBean settlementTrigger(
        @Qualifier("settlementJobDetail") JobDetail detail) {

    CronTriggerFactoryBean factory =
            new CronTriggerFactoryBean();
    factory.setName("settlement-weekday");
    factory.setGroup("finance");
    factory.setJobDetail(detail);
    factory.setCronExpression("0 0 9 ? * MON-FRI");
    factory.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    return factory;
}
~~~

Đây là **mảnh cấu hình**, không phải chương trình độc lập. Quartz TriggerFactoryBean nhận JobDetail và cron expression. `SchedulerFactoryBean` phải được cấu hình để đăng ký Trigger này hoặc ứng dụng gọi API Scheduler tương ứng.

### 9.3. SchedulerFactoryBean và vòng đời

~~~java
@Bean
public SchedulerFactoryBean schedulerFactory(
        JobDetail detail, Trigger trigger) {
    SchedulerFactoryBean bean = new SchedulerFactoryBean();
    bean.setJobDetails(detail);
    bean.setTriggers(trigger);
    bean.setAutoStartup(true);
    bean.setWaitForJobsToCompleteOnShutdown(true);
    return bean;
}
~~~

Đây là mảnh cấu hình; đừng copy song song vào project demo nếu đã có `quartzScheduler` bean vì có thể tạo thêm scheduler/registrations. Khi Spring context đóng, `SchedulerFactoryBean` quản lý việc shutdown Scheduler. `waitForJobsToCompleteOnShutdown=true` có thể chờ jobs đang thực thi, nhưng không thay thế task timeouts, circuit breaker hay deadline của platform.

**Không suy từ việc đã tạo JobDetailFactoryBean/CronTriggerFactoryBean rằng Quartz tự chạy nếu chưa có Scheduler đăng ký và được start.**

## 10. Quartz JobFactory và tiêm phụ thuộc Spring

Quartz Job class thường do JobFactory tạo ra ở mỗi firing. Một constructor:

~~~java
public class EmailJob implements Job {
    private final EmailService emailService;

    public EmailJob(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        emailService.sendDigest();
    }
}
~~~

Đây là **mảnh class minh họa**, cần Spring bean `EmailService` và code DI tương ứng. Với **Quartz default JobFactory**, class không có constructor không tham số sẽ không được tạo theo kiểu Spring constructor injection chỉ vì nó nằm trong dự án Spring. `@Autowired` cũng không mặc nhiên được áp dụng theo cùng cách Spring tạo một `@Service` instance.

Các lựa chọn:

- Dùng `QuartzJobBean` với setters nhận values từ JobDataMap và một cấu hình phù hợp.
- Cấu hình `SchedulerFactoryBean.setJobFactory(...)` với JobFactory được thiết kế để wire các Spring dependencies; `SpringBeanJobFactory` hỗ trợ bean property injection, không mặc nhiên đồng nghĩa **full constructor autowiring**.
- Viết một adapter Job mỏng, lấy service được Spring quản lý qua JobFactory/DI bridge có kiểm soát rồi ủy quyền nghiệp vụ.

**Không lưu Spring `ApplicationContext` hay một `DataSource` live vào `JobDataMap` để truyền "dependency"**, đặc biệt nếu định dùng JDBC JobStore. Dùng JobFactory/DI thay vì serialize service objects.

### 10.1. Mẫu tách Job orchestration và business service

~~~text
Quartz Trigger
     |
     v
ReportQuartzJob implements Job
     |
     v
ReportService (Spring bean, @Transactional ở method phù hợp)
     |
     v
Repository / HTTP client / storage
~~~

Mẫu này tách thời gian chạy khỏi logic nghiệp vụ. Job class có thể được tạo mới mỗi lần mà service Spring vẫn quản lý transaction, dependency và unit tests bình thường. `@Transactional` chỉ hoạt động khi gọi qua Spring proxy phù hợp, không tự xuất hiện vì Quartz Scheduler gọi `execute`.

## 11. Lưu lịch: RAMJobStore hay JDBC JobStore?

Quartz Scheduler dựa trên `JobStore` để lưu định nghĩa JobDetails/Triggers và trạng thái fire times.

### 11.1. RAMJobStore

- Giữ metadata trong memory JVM.
- Dễ dùng cho demo hoặc những jobs không cần khôi phục.
- Khi process restart, registrations được tạo lại từ Spring startup config hoặc bị mất nếu chỉ được đăng ký runtime.
- Không có distributed coordination qua database.
- `storeDurably()` chỉ có nghĩa job không bị xóa khi hết trigger **trong JobStore hiện tại**, không biến RAM thành persistent storage.

### 11.2. JDBC JobStore

- Lưu metadata và trigger state trong các bảng Quartz.
- Có cơ chế misfire/recovery/clustering khi **cấu hình đúng**.
- Cần schema, datasource, transaction boundaries, thread pool, scheduler identity và cluster settings nhất quán.
- Không nên tự đổi schema hay làm initializer phá bảng trong production.
- JobDataMap nên ưu tiên dữ liệu đơn giản/serializable, tránh class-version compatibility hoặc các object quản lý tài nguyên.

Trong Spring, `SchedulerFactoryBean.setDataSource(...)` có sự phối hợp đặc biệt với `LocalDataSourceJobStore`. Tài liệu Spring cảnh báo không nên vừa cấu hình `org.quartz.jobStore.class` tùy tiện trong quartz properties lại vừa kỳ vọng Spring tự quản lý JobStore thông qua DataSource. Hãy đọc Javadocs và cấu hình theo **một con đường**.

### 11.3. JDBC không tự đảm bảo exactly-once side effects

Quartz có thể giúp nhiều nodes phối hợp trigger acquisition qua JDBC clustered JobStore, nhưng một process có thể crash **sau khi gửi HTTP request** mà **trước khi ghi trạng thái hoàn tất**. Job recovery/retry có thể khiến side effect được thực hiện lại. Muốn kết quả đúng cần idempotency, transaction outbox, deduplication hoặc checkpoint.

"Quartz job đã fire" khác "email đã nhận", "báo cáo đã lưu" hay "payment đã commit".

## 12. Misfire: trách nhiệm của Trigger, Scheduler và JobStore

**Misfire** xảy ra khi một trigger đã quá thời điểm lẽ ra fire theo điều kiện/threshold mà Quartz áp dụng, ví dụ Scheduler bị shutdown lâu hoặc worker pool không đủ capacity. Đây là bài toán **lịch bị lỡ**, không phải exception bên trong business code.

Quartz cung cấp `misfireInstruction` trên Trigger. Mỗi loại có những policy riêng.

Ví dụ CronTrigger:

~~~java
Trigger cron = TriggerBuilder.newTrigger()
        .withIdentity("billing", "finance")
        .forJob(JobKey.jobKey("bill", "finance"))
        .withSchedule(
                CronScheduleBuilder.cronSchedule(
                        "0 0 9 ? * MON-FRI")
                        .withMisfireHandlingInstructionDoNothing())
        .build();
~~~

Đây là mảnh code minh họa. `withMisfireHandlingInstructionDoNothing()` yêu cầu bỏ qua các lần đã lỡ và tính lịch tương lai thay vì fire ngay để bù. Một policy khác là `withMisfireHandlingInstructionFireAndProceed()`: fire một lần sớm rồi tiếp tục cron schedule. Default "smart policy" của CronTrigger được Quartz diễn giải theo loại trigger; không nên tự áp một quy tắc duy nhất cho mọi trigger.

| Thành phần | Vai trò trong misfire |
| --- | --- |
| Trigger | Lưu policy và lịch cụ thể, quyết định cách cập nhật next fire |
| Scheduler | Phát hiện/xử lý triggers tới hạn, điều phối thread pool |
| JobStore | Lưu và quản lý trigger state, đặc biệt với JDBC persistence |
| Job business code | Xử lý idempotency/retry và outcome nếu một lần chạy bị bỏ hoặc chạy bù |

**Không đồng nhất misfire với retry business exception.** Một Quartz Job ném `JobExecutionException` cần xử lý theo `JobExecutionException` flags/listeners và policy riêng; misfire xử lý lịch đến muộn.

## 13. Concurrency: nhiều Trigger có thể gọi cùng JobDetail

Một JobDetail có hai triggers fire gần nhau. Quartz có thể tạo **hai Job instances đang chạy đồng thời** nếu không có ràng buộc khác và thread pool có capacity.

~~~text
Trigger A (09:00:00) --+--> JobDetail K --> Job instance #1
                       |
Trigger B (09:00:01) --+--> JobDetail K --> Job instance #2

Mặc định: có thể overlap
~~~

Khi cần không cho hai executions của **cùng một job definition** overlap:

~~~java
@DisallowConcurrentExecution
public class CriticalJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        // Chỉ một execution cùng JobKey ở một thời điểm
        // khi Quartz thực thi contract này.
    }
}
~~~

Đây là mảnh class minh họa. `@DisallowConcurrentExecution` được đặt trên **job class** nhưng tác động đối với **cùng JobDetail/JobKey**. Nếu cùng class có JobKey A và JobKey B, Quartz vẫn có thể chạy chúng đồng thời. Nó không phải distributed mutex cho mọi business operation có cùng database row.

### 13.1. @PersistJobDataAfterExecution

Nếu Job sửa JobDetail JobDataMap và cần Quartz persist map sau execution thành công, dùng `@PersistJobDataAfterExecution`. Quartz tutorial khuyên kết hợp `@DisallowConcurrentExecution` để tránh race khi nhiều executions cùng job definition cập nhật state.

Tuy vậy JobDataMap không nên là nơi lưu dữ liệu business quan trọng thay database transactions. Nếu hai JobDetails khác nhau ghi cùng tài khoản DB, `@DisallowConcurrentExecution` theo JobKey **không** ngăn xung đột đó.

### 13.2. Giới hạn ThreadPool

`org.quartz.threadPool.threadCount` điều chỉnh số worker Quartz dành cho job executions trong cấu hình thread pool tương ứng. Ví dụ:

~~~properties
org.quartz.threadPool.class=org.quartz.simpl.SimpleThreadPool
org.quartz.threadPool.threadCount=4
org.quartz.threadPool.threadPriority=5
~~~

Đây là mảnh cấu hình Quartz. Bốn threads không có nghĩa tối đa bốn database connections trên toàn ứng dụng, cũng không tự tạo rate limits cho remote APIs. Phối hợp với connection pool, I/O timeout, queue admission và job concurrency constraints.

## 14. Lifecycle và thay đổi lịch tại runtime

Quartz Scheduler cho phép ứng dụng động thay đổi đăng ký:

~~~text
Admin cập nhật cron
        |
        +-- validate expression + timezone
        +-- xác định TriggerKey cụ thể
        +-- xây Trigger replacement
        +-- scheduler.rescheduleJob(oldKey, replacement)
        +-- kiểm tra nextFireTime / state
        +-- ghi applied config version
~~~

Nếu cron chỉ nằm trong Spring XML/`@Bean` và người vận hành sửa file sau startup, `SchedulerFactoryBean` không tự đảm bảo nó sẽ reschedule một trigger đã được Quartz lưu trong JobStore. Dynamic update phải qua API Scheduler hoặc một cơ chế reload/registration rõ.

Với JDBC JobStore, cần còn chú ý **overwriteExistingJobs**, transaction boundary và chính sách giữ dữ liệu runtime giữa các lần restart. Tránh mỗi deployment ghi đè cron người vận hành vừa sửa nếu không có quy tắc nguồn cấu hình nào là authoritative.

### 14.1. Start, standby, pause, shutdown khác nhau

- `start()`: cho Scheduler xử lý triggers.
- `standby()`: tạm ngừng việc fire triggers ở cấp Scheduler, khác shutdown.
- `pauseTrigger`/`pauseJob`: tạm ngừng đối tượng cụ thể, không ngắt job đang chạy.
- `unscheduleJob`: xóa trigger; một JobDetail non-durable có thể bị xóa nếu không còn triggers.
- `shutdown(true)`: đóng Scheduler, yêu cầu chờ jobs đang thực thi hoàn tất. Không còn start lại instance đã shutdown.
- `SchedulerFactoryBean`: thường quản lý lifecycle start/shutdown theo Spring context; đừng gọi shutdown trên shared Scheduler của bean từ một service không sở hữu nó.

### 14.2. Hủy Trigger không đồng nghĩa hủy execution đã chạy

Một trigger có thể đã fire và Job đang chạy trong worker. `unscheduleJob(triggerKey)` ngăn những lần fire tương lai của trigger nhưng không phải "kill" Job instance đang thực thi. Quartz còn có `interrupt(JobKey/FireInstanceId)` ở mức Scheduler khi Job class hỗ trợ `InterruptableJob`, nhưng đó là **cooperative interruption** và không có bảo đảm rollback side effects.

## 15. Quan sát: nhìn được cả định nghĩa và lần chạy

Quartz cung cấp các API đọc:

~~~java
JobDetail job = scheduler.getJobDetail(jobKey);
Trigger trigger = scheduler.getTrigger(triggerKey);

Trigger.TriggerState state =
        scheduler.getTriggerState(triggerKey);

Date next = trigger == null
        ? null
        : trigger.getNextFireTime();
~~~

Đây là mảnh code minh họa cần keys và imports. `TriggerState` bao gồm những trạng thái như `NORMAL`, `PAUSED`, `COMPLETE`, `ERROR`, `BLOCKED`, `NONE` theo API/quá trình. Không suy rằng state NORMAL chứng minh business job vừa hoàn thành thành công.

Nên theo dõi:

- Registered JobKeys/TriggerKeys, group và nguồn cấu hình.
- Trigger state, next/previous fire time, calendar/zone/misfire policy.
- Số lần fire, execution start/end, duration và exceptions qua listeners.
- Pool worker capacity, rejected/blocked jobs, misfire count.
- Nếu JDBC: cluster nodes, DB latency/locks, scheduler check-in, schema health.
- Business result ID, idempotency key, persisted status/side effects.
- Applied schedule version, người sửa và audit trail.

Quartz có `JobListener`, `TriggerListener` và `SchedulerListener` để quan sát sự kiện tương ứng. Logging một lần fire không phải bảo đảm job hoàn tất. Hãy ghi kết quả nghiệp vụ trong service hoặc transaction phù hợp.

## 16. Quartz so với Spring @Scheduled và TaskScheduler

| Nhu cầu | Spring `@Scheduled` / `TaskScheduler` | Quartz |
| --- | --- | --- |
| Một periodic task trong một JVM | Gọn, ít cấu hình | Có thể nhưng nhiều thành phần hơn |
| Tách job definition và nhiều schedules | Cần tự thiết kế registry/handle | `JobDetail` + nhiều `Trigger` là mô hình chính |
| Cron theo calendar | `CronTrigger` Spring | Quartz `CronTrigger`, cron grammar riêng |
| Quản lý pause/resume theo keys | Tự quản lý `ScheduledFuture` hoặc registry | Scheduler có API theo JobKey/TriggerKey |
| Misfire policy chi tiết | Cần xử lý theo implementation/logic | Chính sách chuyên biệt theo Trigger |
| Persist schedules qua restart | Không có sẵn trong in-memory TaskScheduler | JDBC JobStore nếu cấu hình và schema đúng |
| Cluster coordination | Cần giải pháp riêng | Quartz JDBC clustering khi cấu hình đúng |
| Tích hợp với Spring beans | Bean/proxy/@Scheduled trực tiếp | SchedulerFactoryBean và JobFactory/DI bridge |
| Exactly-once business side effects | Không mặc nhiên | Cũng không mặc nhiên |

**Lựa chọn:** dùng `@Scheduled` nếu chỉ có vài công việc chạy định kỳ, dễ cấu hình và không cần persistent job definitions. Dùng Quartz khi bài toán thực sự cần **định danh jobs/triggers**, pause/resume/reschedule, calendar/misfire, persistence hoặc clustering. Không triển khai Quartz chỉ vì thấy annotation scheduling "không đủ chuyên nghiệp"; cân nhắc complexity của JobStore, transaction và vận hành.

## 17. Sai lầm phổ biến và cách sửa

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Tưởng `JobDetail.build()` chạy job | Nó chỉ tạo metadata | Đăng ký với Scheduler và start |
| Tưởng Trigger chứa code business | Trigger chỉ biểu diễn lịch và jobKey | Logic nằm ở Job.execute/service |
| Tưởng Quartz Scheduler là Spring TaskScheduler | Hai hệ API khác nhau | Dùng `SchedulerFactoryBean` để tích hợp |
| Gán cùng Job class rồi nghĩ chỉ có một JobDetail | JobKey định danh definition | Quản lý name/group tường minh |
| Một Trigger trỏ nhiều Jobs | Một Trigger chỉ trỏ một JobKey | Tạo nhiều triggers hoặc workflow khác |
| Nhiều Trigger cùng JobDetail mà không xét overlap | Có thể đồng thời fire | `@DisallowConcurrentExecution` và lock/idempotency phù hợp |
| Đặt mutable state vào field Job instance | Quartz thường tạo Job mới mỗi fire | JobDataMap/business DB theo contract |
| Đưa Spring bean object vào JDBC JobDataMap | Serialization/version/resource risk | Lưu ID, inject service qua JobFactory |
| Nghĩ `storeDurably` là persist qua restart | RAMJobStore vẫn mất | JDBC JobStore phù hợp |
| Nghĩ `requestsRecovery` bảo đảm không duplicate | Recovery có thể chạy lại | Idempotency/checkpoint |
| Đổi TriggerKey rồi gọi reschedule nhầm | Có thể tạo lịch song song | Giữ identity đúng và kiểm tra |
| Unschedule trigger cuối của non-durable job | JobDetail có thể bị dọn | Durable nếu cần giữ definition |
| Không gọi Scheduler.start hoặc tắt autoStartup | Metadata tồn tại nhưng không fire | Hiểu lifecycle |
| Không kiểm tra timezone của cron | Sai giờ khi deploy server vùng khác | Cấu hình TimeZone rõ |
| Copy Spring cron vào Quartz không validate | Grammar có khác biệt | Quartz CronExpression/Builder |
| Coi misfire là business exception | Misfire là lịch bị lỡ | Phân biệt misfire và JobExecutionException |
| Nghĩ JDBC persistence có nghĩa exactly once | Side effects vẫn có race/crash | Job store + idempotency/transaction |
| Tự shutdown bean Scheduler dùng chung | Làm các jobs khác dừng | Spring quản lý lifecycle |
| Tăng Quartz threads để sửa DB pool nghẽn | Downstream limit không đổi | Đo DB/HTTP/locks và bounded concurrency |
| Không ghi next fire time/trigger state | Khó debug job không chạy | Scheduler queries, JFR/logs/metrics |

## 18. Checklist trước khi đưa Quartz vào Spring production

- [ ] Job class implement `org.quartz.Job`, logic tập trung trong `execute` hoặc service được inject đúng.
- [ ] Mỗi JobDetail có JobKey rõ ràng, class, JobDataMap và durability theo nhu cầu.
- [ ] Mỗi Trigger có TriggerKey, liên kết đúng `JobKey`, start/end/timezone/misfire policy.
- [ ] Biết một JobDetail có thể có nhiều triggers và một trigger chỉ trỏ một job.
- [ ] Không tạo nhiều registrations ngoài ý muốn với cùng business purpose.
- [ ] SchedulerFactoryBean tạo/đăng ký và startup/shutdown theo Spring lifecycle.
- [ ] Khi có nhiều Schedulers, xác định bean instance và scheduler name cụ thể.
- [ ] Với dynamic operations, chọn đúng API: pause/resume/unschedule/reschedule/delete.
- [ ] Nếu cần giữ JobDetail không có Trigger, dùng `storeDurably()`.
- [ ] Nếu có state persistence, hiểu `@PersistJobDataAfterExecution` và `@DisallowConcurrentExecution`.
- [ ] Nếu có JDBC JobStore, cấu hình datasource/schema/transactions/cluster đúng.
- [ ] Không lưu Spring-managed resources vào persisted JobDataMap.
- [ ] Có JFR/logs/metrics về trigger fire, errors, misfires, nextFireTime và worker pool.
- [ ] Có idempotency key, business status và recovery policy nếu chạy lại sau crash.
- [ ] Không nhầm Quartz Trigger với Spring Trigger hoặc `@Scheduled`.
- [ ] Có test dùng latches/timeouts, không chỉ `Thread.sleep` để chờ job.
- [ ] Dừng Scheduler đúng owner, chờ jobs đang chạy theo shutdown budget.
- [ ] Tài liệu hóa định nghĩa "đúng một lần" ở business level nếu cần.

## 19. Câu hỏi tự kiểm tra

1. **JobDetail giữ thông tin gì?** JobKey, job class, JobDataMap, durability, recovery và metadata.
2. **JobDetail có tự chạy sau khi gọi build()?** Không; phải được đăng ký và kích hoạt.
3. **Trigger có nhiệm vụ gì?** Tính/biểu diễn các thời điểm fire và trỏ đến JobKey.
4. **Một Trigger có thể trỏ nhiều JobDetails không?** Không; một Trigger gắn một JobKey.
5. **Một JobDetail có thể có nhiều Triggers?** Có.
6. **Scheduler làm gì?** Quản lý đăng ký/JobStore, start/pause/fire, cấp worker và điều phối JobFactory/Job.execute.
7. **Job class và JobDetail khác nhau thế nào?** Class chứa code; JobDetail là một definition cụ thể của class.
8. **Quartz có tạo cùng một Job object mỗi lần fire không?** Mặc định JobFactory tạo instance mới mỗi lần.
9. **JobDataMap của Trigger và JobDetail cùng key thì giá trị nào ưu tiên khi merged?** Trigger map.
10. **Dùng storeDurably có đảm bảo dữ liệu sống qua JVM restart?** Không nếu JobStore là RAM.
11. **@DisallowConcurrentExecution chặn tất cả instances của job class?** Không; chặn overlap theo cùng JobDetail/JobKey.
12. **@PersistJobDataAfterExecution dùng để làm gì?** Cập nhật stored JobDetail map sau execution thành công.
13. **SimpleTrigger phù hợp với lịch gì?** One-shot hoặc lặp theo interval/số lần.
14. **CronTrigger phù hợp với lịch gì?** Calendar schedule dựa trên cron expression, timezone.
15. **Quartz cron có bao nhiêu trường?** Sáu bắt buộc và trường năm thứ bảy tùy chọn.
16. **SchedulerFactoryBean đóng vai trò gì?** Tạo/quản lý Quartz Scheduler như Spring bean và đăng ký Jobs/Triggers.
17. **Tại sao @Autowired constructor không mặc nhiên chạy trong Quartz Job mặc định?** Quartz JobFactory mặc định không tạo Job bằng Spring constructor injection.
18. **Scheduler đã tạo nhưng chưa start sẽ fire không?** Không.
19. **Muốn đổi một cron mà giữ JobDetail thì dùng API nào?** `rescheduleJob(triggerKey,replacement)`.
20. **Unschedule trigger cuối ảnh hưởng non-durable job thế nào?** Quartz có thể xóa JobDetail không còn trigger.
21. **Misfire là gì?** Lịch bị lỡ thời điểm fire theo threshold/policy, khác business exception.
22. **JDBC JobStore có bảo đảm exactly-once side effects?** Không; cần idempotency/transaction design.
23. **Chọn Quartz thay Spring @Scheduled khi nào?** Cần quản lý keys, nhiều triggers, misfire, persistence/clustering.
24. **Ba vai trò phải nhớ?** **JobDetail = việc gì; Trigger = khi nào; Scheduler = quản lý/điều phối.**

**Kết luận:** Quartz tách **định nghĩa công việc** (`JobDetail`), **lịch kích hoạt** (`Trigger`) và **bộ máy quản lý/thực thi** (`Scheduler`). Bằng cách liên kết `TriggerKey` với `JobKey`, ứng dụng có thể giữ một JobDetail nhưng thay nhiều lịch, pause/resume, dùng cron và quản lý persistence nếu chọn JobStore phù hợp. Trong Spring Framework, `SchedulerFactoryBean` quản lý Quartz Scheduler như một bean; còn JobFactory/DI bridge giúp job thực hiện nghiệp vụ qua Spring services. Dù có Quartz, phải thiết kế đúng concurrency, transaction, misfire, cancellation và idempotency — Scheduler không tự chứng minh business side effect đã thành công.

### Tài liệu chính thức

- [Quartz 2.3 — Lesson 2: The Quartz API, Jobs and Triggers](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-02.html)
- [Quartz 2.3 — Lesson 3: More About Jobs and JobDetails](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-03.html)
- [Quartz 2.3 — Lesson 4: More About Triggers](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-04.html)
- [Quartz 2.3 — Lesson 5: SimpleTrigger](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-05.html)
- [Quartz 2.3 — Lesson 6: CronTrigger](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html)
- [Quartz 2.3 — Lesson 9: JobStores](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-09.html)
- [Quartz 2.3 API — JobDetail](https://www.quartz-scheduler.org/api/2.3.0/org/quartz/JobDetail.html)
- [Quartz 2.3 API — Scheduler](https://www.quartz-scheduler.org/api/2.3.0/org/quartz/Scheduler.html)
- [Quartz 2.3 API — Trigger](https://www.quartz-scheduler.org/api/2.3.0/org/quartz/Trigger.html)
- [Spring Framework — Using the Quartz Scheduler](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-quartz)
- [Spring Framework 6.1 — SchedulerFactoryBean](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/quartz/SchedulerFactoryBean.html)
- [Spring Framework 6.1 — JobDetailFactoryBean](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/quartz/JobDetailFactoryBean.html)
- [Spring Framework 6.1 — CronTriggerFactoryBean](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/quartz/CronTriggerFactoryBean.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="quartz-integration-003">SchedulerFactoryBean tích hợp vòng đời Quartz với Spring</a>

<details>
<summary>Xem chi tiết</summary>

# `SchedulerFactoryBean` tích hợp vòng đời Quartz với Spring

**Module:** Spring Framework - concurrency  
**Nhóm kiến thức:** Tích hợp Quartz  
**Bài học:** SchedulerFactoryBean tích hợp vòng đời Quartz với Spring.  
**Phạm vi kỹ thuật:** Spring Framework 6.2.x, Quartz 2.x, Java 17+.

## 1. Tại sao cần cầu nối Quartz–Spring?

Quartz là một Java scheduler độc lập có thể tạo `org.quartz.Scheduler`, đăng ký `JobDetail` và `Trigger`, rồi tự chạy job qua các Quartz worker threads. Trong Java SE thuần, người lập trình thường tự làm:

```java
SchedulerFactory quartzFactory = new StdSchedulerFactory();
Scheduler scheduler = quartzFactory.getScheduler();
scheduler.start();

// Đăng ký job/trigger ...
// Khi dừng process cần chủ động scheduler.shutdown().
```

Đây là đoạn **minh họa**, chưa có imports/exception handling và chưa đăng ký lịch cụ thể.

Nếu mang mô hình tự quản lý này vào một Spring application mà không điều phối, có những vấn đề:

- Quartz có thể bắn lịch trước khi `DataSource`, HTTP client hoặc bean nghiệp vụ sẵn sàng.
- Spring context đã đóng nhưng Quartz vẫn chạy, giữ OS threads hoặc JDBC connections.
- Quartz Job class là đối tượng do Quartz tạo, không tự nhiên trở thành Spring-managed singleton.
- Các job/trigger khai báo bằng configuration không được đăng ký đồng nhất với trạng thái JobStore.
- Tự gọi `new StdSchedulerFactory()` trong khi Spring Boot cũng auto-configure một Quartz Scheduler có thể tạo hai instance không được dự kiến.
- Chưa xác định ai chịu trách nhiệm dừng job, chờ các job đang thực thi và giải phóng tài nguyên.

Spring có:

```java
org.springframework.scheduling.quartz.SchedulerFactoryBean
```

Đây là **FactoryBean của Spring tạo `org.quartz.Scheduler`**, tích hợp cấu hình Quartz với container, đăng ký job và trigger tĩnh, khởi động theo Spring lifecycle và shutdown khi bean bị hủy.

**Bản chất:** `SchedulerFactoryBean` không phải `Job`, không phải `ThreadPoolTaskScheduler`, không phải `org.springframework.scheduling.TaskScheduler`. Nó là **điểm nối ownership, DI và lifecycle** cho Quartz runtime.

## 2. Nhận diện đúng API và trách nhiệm

| Thành phần | Package | Dùng để |
| --- | --- | --- |
| `Job` | `org.quartz` | Định nghĩa `execute(JobExecutionContext)` được Quartz gọi |
| `JobDetail` | `org.quartz` | Danh tính `JobKey`, job class, data và durability |
| `Trigger` | `org.quartz` | Quy định thời điểm/chu kỳ kích hoạt một JobDetail |
| `Scheduler` | `org.quartz` | Runtime quản lý triggers, jobs, worker pool và store |
| `SchedulerFactory` | `org.quartz` | Tạo/lấy Quartz Scheduler, ví dụ `StdSchedulerFactory` |
| `SchedulerFactoryBean` | `org.springframework.scheduling.quartz` | FactoryBean + Spring lifecycle của Quartz Scheduler |
| `JobDetailFactoryBean` | `org.springframework.scheduling.quartz` | Helper tạo `JobDetail` qua bean properties |
| `SimpleTriggerFactoryBean` | `org.springframework.scheduling.quartz` | Helper tạo simple interval trigger |
| `CronTriggerFactoryBean` | `org.springframework.scheduling.quartz` | Helper tạo Quartz cron trigger |
| `SpringBeanJobFactory` | `org.springframework.scheduling.quartz` | Điền job bean properties từ data/context |
| `TaskScheduler` | `org.springframework.scheduling` | Abstraction riêng của Spring, **không phải** Quartz `Scheduler` |
| `ThreadPoolTaskScheduler` | `org.springframework.scheduling.concurrent` | Local Spring scheduler khác Quartz JobStore |

Hai schedulers có thể cùng tồn tại trong một ứng dụng, nhưng **`@Scheduled` của Spring không tự ghi thành Quartz JobDetail/Trigger**. Khi cần persistence, misfire/recovery/cluster có cấu hình, dùng đúng Quartz APIs.

Sơ đồ:

```text
             Spring ApplicationContext
                       |
                       v
          SchedulerFactoryBean (Spring bean)
        +--------------+------------------+
        |              |                  |
        v              v                  v
   JobDetails       Triggers       Quartz properties
        |              |                  |
        +--------------+------------------+
                       |
                       v
             org.quartz.Scheduler
                       |
           +-----------+-------------+
           |                         |
           v                         v
        JobStore                 Worker Threads
                                     |
                                     v
                            Job.execute(context)
                                     |
                                     v
                               Business service
```

Quartz thường tạo **Job instance mới cho mỗi lần thực thi**, còn `JobDetail` descriptor được quản lý với `JobKey`. Vì vậy job class không mặc định là một bean singleton do Spring tạo.

## 3. `FactoryBean` mang ý nghĩa đặc biệt trong Spring

`SchedulerFactoryBean` thực hiện `FactoryBean<org.quartz.Scheduler>` cùng các interface `InitializingBean`, `DisposableBean`, `SmartLifecycle` và `ApplicationContextAware`.

Một bean:

```java
@Bean("quartzFactory")
SchedulerFactoryBean quartzFactory() {
    return new SchedulerFactoryBean();
}
```

Đây chỉ là **minh họa tối giản**; trong ứng dụng thật cần cấu hình các jobs/triggers và yêu cầu vận hành thích hợp.

Khi chạy trong Spring context:

```java
Scheduler scheduler = context.getBean(
        "quartzFactory", Scheduler.class);

SchedulerFactoryBean factory = context.getBean(
        "&quartzFactory", SchedulerFactoryBean.class);
```

- Không có `&`: Spring trả **sản phẩm** của FactoryBean, tức Quartz `Scheduler`.
- Có `&`: Spring trả **chính FactoryBean**, khi cần xem/cấu hình factory ở tầng infrastructure.
- Business service thông thường nên inject `org.quartz.Scheduler` để truy vấn/job động, không cần phụ thuộc vào factory.

Không nhầm `@Bean SchedulerFactoryBean` là một method tạo ra Quartz Job đã chạy; nó tạo **cơ chế** để Spring quản lý Quartz Scheduler.

## 4. Lifecycle từ `afterPropertiesSet()` tới `destroy()`

Spring gọi các hook lifecycle theo những giai đoạn khác nhau:

| Thời điểm | Cơ chế | Kết quả |
| --- | --- | --- |
| Bean construction/config | Spring IoC | Set Quartz properties, DataSource, JobFactory, triggers |
| Bean initialization | `afterPropertiesSet()` | Tạo Quartz `Scheduler`, đăng ký `JobDetail`/`Trigger` qua `SchedulerAccessor` |
| Context refresh | `SmartLifecycle.start()` nếu `autoStartup=true` | Bắt đầu kích hoạt lịch, tôn trọng `startupDelay` |
| Running | `Scheduler` API | Job executes, pause/reschedule/trigger manually, monitoring |
| Context close | Lifecycle stop theo phase | Ngừng hoạt động lập lịch theo quản lý container |
| Destruction | `destroy()` | Gọi Quartz shutdown và giải phóng tài nguyên scheduler |
| Crash/SIGKILL | Không phải graceful shutdown | Không có bảo đảm callback `destroy` chạy |

```text
Spring tạo/configure factory
         |
         v
SchedulerFactoryBean.afterPropertiesSet()
         |-- tạo Quartz Scheduler
         |-- đăng ký JobDetail và Trigger
         v
ApplicationContext refresh hoàn tất
         |
         v
SmartLifecycle.start() [nếu autoStartup]
         |-- startupDelay > 0: khởi động trễ
         v
Quartz chạy jobs theo trigger
         |
         v
ApplicationContext.close()
         |-- lifecycle stop (thứ tự phase)
         |-- bean destruction
         v
SchedulerFactoryBean.destroy()
         |
         v
Quartz Scheduler.shutdown(...)
```

**Phân biệt:**

- *Initialized* nghĩa là scheduler đã được tạo/configure; không đồng nghĩa jobs được kích hoạt.
- `isStarted()` có thể vẫn phản ánh đã từng start, kể cả khi sau đó ở standby.
- `isInStandbyMode()` cho biết không firing triggers tại thời điểm đó.
- `isShutdown()` xác nhận Quartz scheduler đã đóng; scheduler đã shutdown **không thể start lại trên cùng instance**.
- `stop()` của Spring lifecycle không đồng nghĩa đã giải phóng hoàn toàn JobStore/worker; `destroy()` mới là bước Quartz shutdown.
- Khi context refresh thất bại, destroy có thể đến mà không có lifecycle stop trước; không xây logic cleanup phụ thuộc duy nhất vào stop.

## 5. Những setter lifecycle cần nắm

### `setAutoStartup(boolean)`

```java
factory.setAutoStartup(true);
```

Mặc định `true`. Spring tự start scheduler sau context refresh. Đặt `false` khi muốn chủ động mở Quartz sau readiness gate, ví dụ một công việc khởi tạo dữ liệu hoặc xác nhận downstream sẵn sàng.

**Điều quan trọng:** `false` không tự xóa JobDetail/Trigger; chúng vẫn có thể được đăng ký khi factory initialize, nhưng Quartz chưa fire cho đến khi được start.

### `setStartupDelay(int)`

```java
factory.setStartupDelay(10); // giây, không phải mili-giây.
```

Mặc định 0; giá trị dương khiến scheduler start **bất đồng bộ sau khoảng delay**, khi context đã khởi tạo. Nó giúp tránh bắn job quá sớm khi startup. Tuy nhiên delay cố định **không phải** một health check: sau 10 giây, DB vẫn có thể chưa sẵn sàng.

### `setPhase(int)` và `SmartLifecycle`

```java
factory.setPhase(Integer.MAX_VALUE);
```

Default phase `Integer.MAX_VALUE` giúp Quartz được start muộn và stop sớm theo thứ tự phase: startup từ phase thấp đến cao, shutdown ngược lại.

Đổi phase chỉ khi hiểu dependencies của job, DB resources, producer/consumer và thứ tự stop. Không dùng phase để khắc phục tình trạng JobStore/broker không durable.

### `setWaitForJobsToCompleteOnShutdown(boolean)`

```java
factory.setWaitForJobsToCompleteOnShutdown(true);
```

Mặc định là **false**. Với `true`, `SchedulerFactoryBean` yêu cầu Quartz shutdown **đợi những jobs đang thực thi hoàn tất**; với `false`, Quartz không đợi toàn bộ running jobs trước khi shutdown trả về.

**Không nhầm** với `ThreadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true)` — đó là setter thuộc Spring executor khác. `waitForJobs` nói tới Quartz `Scheduler.shutdown(boolean)`.

`waitForJobs=true` không biến các triggers chưa đến hạn thành jobs phải chạy ngay, cũng không persist RAMJobStore. Một job bị treo vô thời hạn có thể giữ quá trình shutdown rất lâu; grace period bên ngoài vẫn có thể kết thúc process.

## 6. Đăng ký `JobDetail` và `Trigger` qua factory

Với Java config, dùng Quartz builders:

```java
JobDetail detail = JobBuilder.newJob(CleanupJob.class)
        .withIdentity("cleanup", "maintenance")
        .storeDurably()
        .build();

Trigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("cleanupTrigger", "maintenance")
        .forJob(detail)
        .startNow()
        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(5)
                .repeatForever())
        .build();

factory.setJobDetails(detail);
factory.setTriggers(trigger);
```

Đây là đoạn **minh họa**, `CleanupJob` phải triển khai Quartz `Job` và có constructor phù hợp; factory phải là một Spring-managed `SchedulerFactoryBean`.

Vai trò:

- **`JobDetail`:** định danh `JobKey`, class và dữ liệu job. `storeDurably()` cho phép giữ `JobDetail` nếu chưa gắn trigger, không có nghĩa persisted qua restart khi dùng RAMJobStore.
- **`Trigger`:** quy tắc thời gian gắn với một `JobDetail`/`JobKey`. Một job có thể có nhiều triggers.
- **`SchedulerFactoryBean`:** khi bean initialize, đăng ký các descriptors/triggers tới Quartz runtime bằng chức năng của `SchedulerAccessor`.

Spring còn có `JobDetailFactoryBean`, `SimpleTriggerFactoryBean`, `CronTriggerFactoryBean` thích hợp cho cấu hình bean-style. Với Java config, Quartz `JobBuilder`/`TriggerBuilder` thường rõ hơn.

### Quartz cron khác Spring cron abstraction

```java
Trigger daily = TriggerBuilder.newTrigger()
        .withIdentity("daily", "report")
        .forJob(detail)
        .withSchedule(CronScheduleBuilder
                .cronSchedule("0 0 8 * * ?")
                .inTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")))
        .build();
```

Đây là đoạn minh họa, cần imports `TimeZone` và Quartz builders. Biểu thức dùng **Quartz CronScheduleBuilder** với cú pháp tương ứng; không suy ra mọi macro/extension của Spring `CronExpression` đều dùng được trong Quartz.

## 7. Ví dụ hoàn chỉnh chạy Java 17: Spring quản lý Quartz thực sự

**Mục tiêu:** kiểm chứng bằng một Spring `ApplicationContext` thật rằng `SchedulerFactoryBean` tạo Quartz Scheduler, tự start, đăng ký job/trigger, cho chạy một job và shutdown khi context đóng.

Dùng **Java 17+**, **Spring Framework 6.2.16**, **Quartz 2.5.2** và Maven. Không có database: demo dùng `RAMJobStore`. Để tránh chờ đồng hồ, trigger tĩnh hẹn năm **2099**; sau đó main chủ động `triggerJob()` và đợi `CountDownLatch`.

### Cấu trúc

```text
quartz-lifecycle-demo/
├── pom.xml
└── src/main/java/com/example/quartzdemo/
    └── QuartzLifecycleDemo.java
```

### `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>quartz-lifecycle-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context-support</artifactId>
            <version>6.2.16</version>
        </dependency>
        <dependency>
            <groupId>org.quartz-scheduler</groupId>
            <artifactId>quartz</artifactId>
            <version>2.5.2</version>
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
                <configuration>
                    <mainClass>com.example.quartzdemo.QuartzLifecycleDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### `src/main/java/com/example/quartzdemo/QuartzLifecycleDemo.java`

```java
package com.example.quartzdemo;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@Configuration(proxyBeanMethods = false)
public class QuartzLifecycleDemo {

    public static final class ProbeState {
        private final CountDownLatch once = new CountDownLatch(1);
        private final AtomicInteger count = new AtomicInteger();

        void record() {
            count.incrementAndGet();
            once.countDown();
        }

        boolean await() throws InterruptedException {
            return once.await(10, TimeUnit.SECONDS);
        }

        int executions() {
            return count.get();
        }
    }

    public static final class ProbeJob implements Job {
        public ProbeJob() {
            // Constructor public để Quartz có thể tạo Job.
        }

        @Override
        public void execute(JobExecutionContext context)
                throws JobExecutionException {
            try {
                Object target = context.getScheduler()
                        .getContext().get("probe");
                if (!(target instanceof ProbeState probe)) {
                    throw new JobExecutionException(
                            "ProbeState missing in SchedulerContext");
                }
                probe.record();
            } catch (SchedulerException ex) {
                throw new JobExecutionException(ex);
            }
        }
    }

    @Bean
    ProbeState probeState() {
        return new ProbeState();
    }

    @Bean
    JobDetail probeJobDetail() {
        return JobBuilder.newJob(ProbeJob.class)
                .withIdentity("probeJob", "lesson")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger futureTrigger(JobDetail probeJobDetail) {
        return TriggerBuilder.newTrigger()
                .withIdentity("futureTick", "lesson")
                .forJob(probeJobDetail)
                .startAt(Date.from(
                        Instant.parse("2099-01-01T00:00:00Z")))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withRepeatCount(0))
                .build();
    }

    @Bean("quartzFactory")
    SchedulerFactoryBean quartzFactory(
            JobDetail probeJobDetail,
            Trigger futureTrigger,
            ProbeState probeState) {

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setSchedulerName("lessonQuartz");
        factory.setJobDetails(probeJobDetail);
        factory.setTriggers(futureTrigger);
        factory.setSchedulerContextAsMap(
                Map.of("probe", probeState));

        Properties properties = new Properties();
        properties.setProperty(
                "org.quartz.threadPool.threadCount", "2");
        properties.setProperty(
                "org.quartz.jobStore.class",
                "org.quartz.simpl.RAMJobStore");
        factory.setQuartzProperties(properties);

        factory.setAutoStartup(true);
        factory.setStartupDelay(0);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        return factory;
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(
                        QuartzLifecycleDemo.class);

        Scheduler scheduler = context.getBean(Scheduler.class);
        ProbeState state = context.getBean(ProbeState.class);

        try {
            boolean started = scheduler.isStarted();
            boolean registeredJob = scheduler.checkExists(
                    JobKey.jobKey("probeJob", "lesson"));
            boolean registeredTrigger = scheduler.checkExists(
                    TriggerKey.triggerKey("futureTick", "lesson"));

            if (!started || !registeredJob || !registeredTrigger) {
                throw new IllegalStateException(
                        "Quartz configuration was not started");
            }

            scheduler.triggerJob(
                    JobKey.jobKey("probeJob", "lesson"));

            if (!state.await()) {
                throw new IllegalStateException(
                        "Quartz Job did not fire within 10 seconds");
            }

            System.out.println("started=" + started);
            System.out.println("jobRegistered=" + registeredJob);
            System.out.println("triggerRegistered=" + registeredTrigger);
            System.out.println("executed=" + state.executions());
        } finally {
            context.close();
        }

        System.out.println("shutdown=" + scheduler.isShutdown());
    }
}
```

### Chạy Maven

```bash
mvn -q compile exec:java
```

**Những dòng do `System.out` phát ra khi chương trình hoàn tất bình thường:**

```text
started=true
jobRegistered=true
triggerRegistered=true
executed=1
shutdown=true
```

Quartz và thư viện logging có thể phát thêm các dòng trạng thái riêng trên console. Vì vậy danh sách trên đại diện **stdout nghiệp vụ**, không khẳng định toàn bộ output của Maven/SLF4J chỉ có đúng năm dòng.

### Giải thích vì sao output có tính xác định

1. `started=true` cho thấy Spring `SmartLifecycle` đã auto-start Quartz khi context refresh.
2. `jobRegistered=true` cho thấy factory đã đăng ký JobDetail theo `JobKey`.
3. `triggerRegistered=true` xác nhận trigger đã được tạo/đăng ký dù lịch bắt đầu tận năm 2099.
4. `scheduler.triggerJob(...)` yêu cầu Quartz thực thi job **ngay**, độc lập thời điểm của future trigger.
5. `CountDownLatch` chỉ trả về sau khi `ProbeJob` truy cập `SchedulerContext` và đếm; `executed=1` được quan sát sau khi job phát tín hiệu.
6. `context.close()` kích hoạt bean destruction và Quartz shutdown; `isShutdown()` là `true` sau khi close thành công.
7. `setWaitForJobsToCompleteOnShutdown(true)` bảo đảm Quartz đợi jobs đang chạy tại shutdown; nếu job kẹt vô hạn, cần deadline ở nghiệp vụ và grace period đủ.

**Ranh giới ví dụ:** dùng RAMJobStore nên không có persistence/cluster. Ngày 2099 là mốc phục vụ demo, không phải hướng dẫn production. Nếu host bị nghẽn và latch hết 10 giây, chương trình ném lỗi thay vì in một kết quả sai. Code được soạn theo các API chính thức; cần chạy thử trên môi trường đích để xác nhận classpath và logging.

## 8. Spring Bean injection vào Quartz Jobs: tránh giả định sai

Trong Spring, một `@Service` thường được Spring tạo và quản lý vòng đời. Quartz lại yêu cầu `JobFactory` tạo **Job instance** lúc fire. Mặc định `SchedulerFactoryBean` dùng `AdaptableJobFactory`; đặt `@Autowired` lên field trong Quartz Job không mặc định làm nó hoạt động hệt một Spring singleton.

Có ba pattern tích hợp thông dụng:

**A. SchedulerContext chứa reference cục bộ đến Spring service.** Demo dùng:

```java
factory.setSchedulerContextAsMap(
        Map.of("probe", probeState));
```

Job đọc từ `context.getScheduler().getContext()`. Thích hợp demo hoặc việc inject dependency rõ ràng. SchedulerContext nằm ở **runtime context** của scheduler, không phải một record phải serialize vào Quartz JobStore.

**B. `SpringBeanJobFactory`.** Config:

```java
factory.setJobFactory(new SpringBeanJobFactory());
```

Nó hỗ trợ điền bean properties từ `JobDataMap` và `SchedulerContext`, nhưng **không tự hứa** mọi field `@Autowired` được xử lý đầy đủ như khi Spring tạo `@Component`; nếu cần autowiring theo annotation, có thể cần custom JobFactory dùng `AutowireCapableBeanFactory`.

**C. QuartzJobBean với setter.** Ví dụ:

```java
public class ReportJob extends QuartzJobBean {
    private ReportService reportService;

    public void setReportService(ReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        reportService.generate();
    }
}
```

Đây là **class minh họa**, cần imports `QuartzJobBean`, Quartz `JobExecutionContext` và service `ReportService`. Khi có context/data và JobFactory hỗ trợ property population, setter được điền; nếu không, field có thể null. Không coi class này là runnable độc lập.

**Cảnh báo persistent store:** không nhét `ApplicationContext`, Spring-managed beans, `DataSource`, HTTP client hoặc object khó serialize vào `JobDataMap` sẽ được lưu DB. Hãy lưu `jobId`, business key, tham số đơn giản; dependency sống của JVM đặt trong SchedulerContext/JobFactory hoặc được DI đúng cách.

## 9. RAMJobStore, JDBC JobStore và Spring transactions

`SchedulerFactoryBean` không mặc định cung cấp durability. JobStore quyết định lịch có sống qua restart hay không:

| JobStore | Persisted sau restart? | Dùng khi |
| --- | --- | --- |
| `RAMJobStore` | Không | Job dọn cache, polling có thể bỏ tick |
| JDBC JobStore | Có, với schema/transactions đúng | Job cần lịch/recovery sau restart |
| JDBC clustered JobStore | Có phối hợp nhiều instances nếu cấu hình đúng | Nhiều replicas chia lịch, failover |

Tự cấu hình Spring-owned DataSource cho Quartz:

```java
@Bean
SchedulerFactoryBean quartzWithDatabase(DataSource dataSource) {
    SchedulerFactoryBean factory = new SchedulerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setWaitForJobsToCompleteOnShutdown(true);
    return factory;
}
```

Đây là **method minh họa**, chưa có schema và các Quartz properties khác; phải ở Spring `@Configuration`, có DataSource bean phù hợp.

Theo Javadoc Spring:

- `setDataSource(dataSource)` cấu hình Spring-specific `LocalDataSourceJobStore`, dựa trên Quartz transaction-capable store.
- **Đừng** đồng thời cấu hình Quartz `org.quartz.jobStore.dataSource` hoặc tự ghi `org.quartz.jobStore.class` khi đã dùng `setDataSource`: có thể double-configure và phá locking.
- Với local non-XA DataSource + Spring transactions, thường chỉ cần một DataSource. Với JTA/XA, có thể cần non-transactional DataSource riêng.
- Các thao tác Scheduler trên JDBC store nên được gọi trong **Spring-managed transaction** phù hợp, nếu không database locking có thể không an toàn.

`JobDetail`/`Trigger` được đăng ký khi startup có thể tương tác với record đã persisted; **không overwrite tùy tiện** khi triển khai phiên bản mới.

### Cluster không mặc định khi bật JDBC

Nhiều replicas dùng JDBC Quartz nhưng muốn phối hợp thực thi phải xác định:

```properties
org.quartz.scheduler.instanceId=AUTO
org.quartz.jobStore.isClustered=true
```

Cần cùng cấu hình schema/table prefix và instance name, unique instance IDs, clock synchronization và transactions. Đây là **hai dòng minh họa**, chưa phải file cấu hình production hoàn chỉnh.

Quartz cluster giúp coordination firing, **không bảo đảm exactly-once business effect** khi có crash giữa side effect và ghi trạng thái hoàn tất. Dùng idempotency key hoặc transaction/outbox/unique constraint cho dữ liệu quan trọng.

## 10. Spring Boot 3.5 đã có `SchedulerFactoryBean` tự động

Khi thêm dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

đây là **đoạn Maven dependency** cho dự án Boot có parent/BOM; không phải POM độc lập.

Spring Boot 3.5 tự cấu hình Quartz `Scheduler` thông qua `SchedulerFactoryBean` khi điều kiện classpath/beans phù hợp. Các bean `JobDetail`, `Trigger`, `Calendar` có thể được thu thập tự động. Vì vậy **đừng tạo một factory trùng mà không có lý do**.

Ví dụ config:

```yaml
spring:
  quartz:
    scheduler-name: businessQuartz
    auto-startup: true
    startup-delay: 5s
    wait-for-jobs-to-complete-on-shutdown: true
    job-store-type: memory
    overwrite-existing-jobs: false
    properties:
      org.quartz.threadPool.threadCount: 4
```

Nếu cần JDBC JobStore:

```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never
    overwrite-existing-jobs: false
    properties:
      org.quartz.scheduler.instanceId: AUTO
      org.quartz.jobStore.isClustered: true
```

Đây **không** phải toàn bộ cấu hình cluster; cần một `DataSource`, Quartz schema DB, instance name, transactions và quyền DB. Với Boot, `spring.quartz.job-store-type=jdbc` chọn persistent store, `spring.quartz.properties.*` chuyển thêm Quartz properties.

**Cảnh báo đặc biệt:** `spring.quartz.jdbc.initialize-schema=always` có thể chạy các scripts Quartz chuẩn **DROP tables rồi tạo lại**, xóa triggers/jobs persisted khi khởi động. Dùng migration được kiểm soát và `initialize-schema: never` nếu schema đã tồn tại, thay vì chạy script destructive ở production.

Với Boot 3.5, có thể custom:

```java
@Bean
SchedulerFactoryBeanCustomizer customizeQuartz() {
    return factory -> {
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setStartupDelay(5);
    };
}
```

Đây là **đoạn minh họa** nằm trong Spring `@Configuration`, cần import Boot 3.5 `org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer`. Package có thể thay đổi ở Boot 4.x; kiểm tra version đích.

**Không nhầm:** `@Scheduled` dùng Spring `TaskScheduler`; Quartz `Scheduler`/`JobDetail` là hệ thống khác. Bật `@EnableScheduling` không phải cách tự động kích hoạt Quartz jobs.

## 11. Misfire, recoverability và concurrent jobs

**Misfire** xảy ra khi trigger không được fire đúng giờ theo ngưỡng của Quartz (scheduler bận, downtime, worker hết chỗ...). Chính sách thuộc trigger type:

- Simple trigger có các lựa chọn fire-now, chuyển mốc hoặc tính lại repeat.
- Quartz cron trigger có thể fire-once-now hoặc bỏ mốc và đợi lần tiếp.
- RAMJobStore không lưu lịch qua JVM restart chỉ vì đã chọn misfire policy.
- JDBC JobStore có thể phục hồi lịch persisted; cách catch-up phụ thuộc misfire, recoverable job và data trạng thái.

Ví dụ cron bỏ mốc đã lỡ:

```java
Trigger hourly = TriggerBuilder.newTrigger()
        .withIdentity("hourly", "maintenance")
        .forJob(detail)
        .withSchedule(CronScheduleBuilder
                .cronSchedule("0 0 * * * ?")
                .withMisfireHandlingInstructionDoNothing())
        .build();
```

Đoạn này minh họa, cần `detail` đã khai báo và các imports Quartz. Việc bỏ mốc hay bù mốc cần dựa trên hợp đồng nghiệp vụ: refresh cache có thể bỏ, còn đối soát tài chính có thể cần xử lý mọi kỳ thiếu.

### Không cho cùng JobDetail chạy chồng nhau

```java
@DisallowConcurrentExecution
public static class SingleFlightJob implements Job {
    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        // Business processing.
    }
}
```

Đây là **class minh họa**, với Quartz imports tương ứng.

`@DisallowConcurrentExecution` áp dụng theo **JobKey/JobDetail**, không đồng nghĩa khóa toàn bộ Job class cho mọi keys. Nếu cùng job có nhiều triggers hoặc có cluster, cần thiết kế concurrency và idempotency theo đúng identity và JobStore.

**Không mặc định exactly-once:** worker có thể ghi side effect rồi crash trước khi cập nhật trạng thái; một lần retry/recovery sẽ lặp lại nếu không bảo vệ bởi idempotency hoặc transaction.

## 12. Dừng ứng dụng an toàn: `stop` không phải `shutdown`

`SchedulerFactoryBean` là `SmartLifecycle` nên được điều phối theo phase. Nhưng **`stop()` chỉ là một giai đoạn lifecycle**, khác `destroy()` thực hiện Quartz shutdown.

Với `waitForJobsToCompleteOnShutdown(true)`, Quartz đợi jobs đang chạy tại shutdown. Điều đó không có nghĩa “phải chạy mọi trigger được đặt cho tháng sau trước khi đóng”, cũng không bảo đảm backlog/JobStore flush thành trạng thái business thành công.

Ví dụ:

```text
T=0  Kubernetes SIGTERM
     |
     v
Spring context begins close
     |
     +-- stop scheduling according to lifecycle phase
     +-- SchedulerFactoryBean.destroy()
             |
             v
       Quartz.shutdown(waitForJobsToComplete=true)
             |
             | một job đang gọi HTTP 90 giây
             |
T=30 orchestrator grace period hết, process có thể bị SIGKILL
```

Nếu chỉ cho 30 giây grace mà job còn 90 giây, không thể chờ đủ. Để shutdown an toàn:

1. Ngừng nhận job mới và pause producer/scheduler theo lifecycle.
2. Đặt deadlines/connection timeouts cho HTTP/JDBC trong job.
3. Chọn chờ running jobs vừa đủ với budget shutdown.
4. Giữ DataSource/client/transaction manager cần thiết sống đến khi job xong hoặc bị dừng đúng cách.
5. Job quan trọng cần persisted state, checkpoint/recovery và idempotency khi node chết.
6. Đo SIGTERM-to-exit và failure/retry sau deploy.

`setWaitForJobsToCompleteOnShutdown` của Quartz khác `setWaitForTasksToCompleteOnShutdown` của Spring `ThreadPoolTaskExecutor`; không dùng nhầm khi viết cấu hình.

## 13. So sánh tự dùng Quartz và Spring-managed Quartz

| Mục tiêu | Tự `StdSchedulerFactory` | `SchedulerFactoryBean` |
| --- | --- | --- |
| Tạo scheduler | Gọi Quartz API trực tiếp | IoC khởi tạo/configure |
| Đăng ký job/trigger tĩnh | Tự gọi `scheduleJob` | `setJobDetails` / `setTriggers` |
| Khởi động cùng Spring context | Tự bắt lifecycle | `autoStartup`, `startupDelay`, `SmartLifecycle` |
| Shutdown khi context đóng | Phải tự gọi `shutdown()` | `destroy()` của bean |
| Inject Quartz scheduler | Publish instance thủ công | `FactoryBean<Scheduler>` expose |
| Truy cập Spring dependencies từ job | Tự xây JobFactory/context | `SchedulerContext` / `SpringBeanJobFactory` |
| Persistence | Tự cấu hình JobStore | Có hỗ trợ `setDataSource`/Spring transactions |
| Cluster, misfire, exactly-once | Vẫn cần thiết kế | **Vẫn cần thiết kế**, không tự miễn phí |

`SchedulerFactoryBean` mang lại **ownership và lifecycle nhất quán**, không làm mất khả năng dùng Quartz API. Khi business có lịch động, inject `Scheduler` và gọi `scheduleJob`, `triggerJob`, `deleteJob`, `rescheduleJob` có kiểm soát.

## 14. Kiểm thử và quan sát

Đối với bài này, những test mang lại giá trị nhất:

| Case | Cách kiểm tra | Kết quả mong muốn |
| --- | --- | --- |
| FactoryBean product | `context.getBean(Scheduler.class)` | Lấy được `org.quartz.Scheduler` |
| FactoryBean instance | `context.getBean("&quartzFactory")` | Lấy `SchedulerFactoryBean` |
| Auto-startup | `scheduler.isStarted()` sau context refresh | True khi autoStartup=true |
| Đăng ký tĩnh | `checkExists(JobKey/TriggerKey)` | Đúng job/trigger được cấu hình |
| Job thực thi | `triggerJob` và `CountDownLatch` | Callback được quan sát |
| Shutdown | `context.close(); scheduler.isShutdown()` | True, không rò threads |
| `autoStartup=false` | Mở context rồi kiểm tra trạng thái | Không tự fire trigger |
| Startup delay | Tính hợp lệ của start timing | Không fire trước startup policy |
| Job DI | Inject từ SchedulerContext/JobFactory | Service không null và đúng bean |
| JDBC store | Restart với DB thật | Job/trigger persisted còn đúng |
| Cluster | Chạy ít nhất hai replicas | Không double-fire ngoài policy và có recovery |
| Misfire | Delay trigger và restore scheduler | Đúng fire-now/do-nothing policy |
| Long-running shutdown | Giữ job bằng latch | Wait flag và grace phù hợp |
| Failure | Ném JobExecutionException | Logging/metrics/retry theo thiết kế |

**Quan sát production:** scheduler name/instanceId, `isStarted`, `isInStandbyMode`, `isShutdown`, JobKeys/TriggerKeys, next fire time, job duration p95/p99, misfire/recovery, thread pool usage, DB locks, transaction errors và shutdown duration.

`SchedulerMetaData` là snapshot; không thay một audit log của các business jobs. Dùng listener/metrics và persisted business status khi tác vụ quan trọng.

## 15. Lỗi thường gặp

| Sai lầm | Hậu quả | Khắc phục |
| --- | --- | --- |
| Nhầm `TaskScheduler` với Quartz `Scheduler` | Dùng nhầm API/không có JobStore | Đúng import và mục đích |
| Nghĩ `FactoryBean` là Job | Không hiểu ai tạo Scheduler | Phân biệt product `Scheduler` với factory |
| Thêm một `StdSchedulerFactory` khác trong Boot | Có thể có hai schedulers | Inject scheduler auto-configured |
| `autoStartup=false` mà quên start | Job có đăng ký nhưng không fire | Kiểm tra lifecycle |
| Dùng `startupDelay` như health-check | Job fire trước khi dependency sẵn sàng | Readiness gate thực tế |
| Job `@Autowired` field nhưng Quartz tự tạo instance | Field null | SchedulerContext/JobFactory phù hợp |
| Lưu service object vào persistent JobDataMap | Serialization/DB errors | Lưu ID và resolve dependencies runtime |
| `setDataSource` lẫn Quartz jobStore.dataSource | Double configuration/locking | Một nguồn cấu hình thống nhất |
| Bật JDBC nhưng quên cluster properties | Failover/double firing không như dự tính | Cluster identity và DB schema đúng |
| `initialize-schema=always` trên DB production | Có thể xóa tables/triggers | Migration và `never` |
| `overwrite-existing-jobs=true` không review | Ghi đè state persisted | Change-control |
| Không đặt misfire instruction | Có thể bù/bỏ kỳ ngoài mong muốn | Chọn theo business |
| Job chạy trùng dưới nhiều triggers | Side effect lặp | `@DisallowConcurrentExecution` và idempotency |
| Wait for jobs khi shutdown nhưng timeout vô hạn | Hết grace, bị SIGKILL | Timeout client và durable recovery |
| Không theo dõi scheduler state | Khó biết job vì sao không chạy | Metrics/health/admin endpoint |

## 16. Tự kiểm tra

**1.** `SchedulerFactoryBean` expose bean loại nào cho application?  
**Đáp:** `org.quartz.Scheduler`, nhờ `FactoryBean<Scheduler>`.

**2.** Dấu `&` trong `context.getBean("&quartzFactory")` có ý nghĩa gì?  
**Đáp:** Lấy **chính factory** thay vì scheduler mà factory tạo.

**3.** `afterPropertiesSet()` đảm nhiệm phần nào?  
**Đáp:** Tạo/configure Quartz scheduler và đăng ký jobs/triggers ở bean initialization.

**4.** `autoStartup=true` khiến Quartz chạy lúc nào?  
**Đáp:** Spring `SmartLifecycle` start sau context refresh, có thể sau startupDelay.

**5.** `setStartupDelay(10)` là 10 ms hay 10 giây?  
**Đáp:** 10 **giây**.

**6.** Ai dừng Quartz khi context đóng bình thường?  
**Đáp:** Lifecycle/destruction của `SchedulerFactoryBean`, đặc biệt `destroy()`.

**7.** `waitForJobsToCompleteOnShutdown=true` có persist RAM jobs không?  
**Đáp:** Không, chỉ quyết định đợi jobs đang chạy khi shutdown.

**8.** Quartz Job có mặc định là Spring singleton có `@Autowired` đầy đủ?  
**Đáp:** Không; job instances do Quartz/JobFactory tạo.

**9.** Vì sao không lưu bean service vào JDBC `JobDataMap`?  
**Đáp:** Không phù hợp serialization/lifecycle; dùng ID/primitive và lấy bean runtime.

**10.** `@Scheduled` của Spring có tự trở thành Quartz JobDetail không?  
**Đáp:** Không, nó thuộc Spring TaskScheduler abstraction khác.

**11.** `job-store-type: jdbc` có tự bật cluster cho nhiều replicas không?  
**Đáp:** Không; cần cluster config và DB schema/identity đúng.

**12.** Quartz cluster có bảo đảm business side effect exactly-once không?  
**Đáp:** Không, vẫn cần idempotency/transactions khi crash/retry.

**13.** Tại sao `initialize-schema=always` nguy hiểm?  
**Đáp:** Script chuẩn có thể drop các Quartz tables và xóa job/trigger persisted.

**14.** Demo hẹn trigger tới 2099 nhưng vẫn in `executed=1` vì sao?  
**Đáp:** Main gọi `scheduler.triggerJob(...)`, không cần đợi trigger thời gian tương lai.

## 17. Kết luận và nguồn chính thức

**Một câu:** `SchedulerFactoryBean` tích hợp **khởi tạo, đăng ký, chạy và dừng Quartz Scheduler** với lifecycle của Spring. Nó quản lý **hạ tầng scheduler**, không tự tạo persistence/cluster/idempotency cho nghiệp vụ.

```text
Spring context creates bean
   -> SchedulerFactoryBean.afterPropertiesSet()
   -> register JobDetail + Trigger
   -> SmartLifecycle.start() if autoStartup
   -> Quartz executes Jobs on trigger
   -> ApplicationContext.close()
   -> SchedulerFactoryBean.destroy()
   -> Quartz Scheduler.shutdown(waitForJobsToComplete)
```

**Tài liệu tham khảo:**

- [Spring Framework 6.2 — `SchedulerFactoryBean` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/quartz/SchedulerFactoryBean.html): lifecycle, FactoryBean, configuration, DataSource, JobFactory.
- [Spring Framework 6.2 — Scheduling và Quartz](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): JobDetail/trigger factory beans và tích hợp Quartz.
- [Spring Boot 3.5 — Quartz Scheduler](https://docs.spring.io/spring-boot/3.5/reference/io/quartz.html): starter, auto-config, JDBC, schema, customization.
- [Quartz 2.5.x — Scheduler API](https://javadoc.io/doc/org.quartz-scheduler/quartz/2.5.2/org/quartz/Scheduler.html): `start`, `standby`, `shutdown`, `triggerJob`, `checkExists`.
- [Quartz 2.5.x — TriggerBuilder API](https://javadoc.io/doc/org.quartz-scheduler/quartz/2.5.2/org/quartz/TriggerBuilder.html): tạo triggers.
- [Spring — FactoryBean extension points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): quy tắc `FactoryBean` và bean product.
- [Spring — Lifecycle và SmartLifecycle](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html): start/stop phases và destruction.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="quartz-integration-004">JobDetailFactoryBean và các loại trigger được Spring hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# JobDetailFactoryBean và các loại trigger được Spring hỗ trợ

## 1. Quartz trong Spring: job là “làm gì”, trigger là “khi nào”

Khi một ứng dụng Spring cần lập lịch công việc, có hai hướng phổ biến: `@Scheduled`/`TaskScheduler` để chạy tác vụ trong một ứng dụng, hoặc **Quartz Scheduler** để định nghĩa Job/Trigger có định danh, chính sách misfire, lưu trữ trong JobStore và khả năng điều phối nhiều node nếu cấu hình JDBC clustering phù hợp.

Khi tích hợp Quartz với Spring Framework 6.1.x, bốn đối tượng cần được phân biệt:

- `org.quartz.Job`: **mã thực thi**; mỗi lần Quartz kích hoạt job, nó tạo instance Job theo `JobFactory` rồi gọi `execute(JobExecutionContext)`.
- `org.quartz.JobDetail`: **định nghĩa một công việc** (job class, `JobKey`, nhóm, mô tả, dữ liệu `JobDataMap`, durability, recovery). Nó **không phải lịch** và **không phải instance Job đang chạy**.
- `org.quartz.Trigger`: **điều kiện và lịch kích hoạt**; trỏ tới một `JobKey`. Nhiều trigger có thể cùng kích hoạt **một JobDetail**.
- `org.quartz.Scheduler`: thành phần **đăng ký** JobDetail/Trigger, chọn fire time, điều phối jobs qua thread pool và JobStore.

Spring cung cấp ba `FactoryBean` quan trọng:

| Spring class | Sản phẩm mà FactoryBean trả về | Vai trò |
| --- | --- | --- |
| `JobDetailFactoryBean` | `org.quartz.JobDetail` | Tạo mô tả job bằng cấu hình bean |
| `SimpleTriggerFactoryBean` | `org.quartz.SimpleTrigger` | Trigger một lần hoặc lặp với khoảng thời gian |
| `CronTriggerFactoryBean` | `org.quartz.CronTrigger` | Trigger theo biểu thức cron và timezone |
| `SchedulerFactoryBean` | `org.quartz.Scheduler` | Khởi tạo và quản lý Quartz Scheduler; đăng ký jobs/triggers |

**Điểm mấu chốt:** `JobDetailFactoryBean` **không** thực thi một job khi được Spring khởi tạo. Và `SimpleTriggerFactoryBean`/`CronTriggerFactoryBean` **không** tự chạy job chỉ vì đã có bean. Các JobDetail/Trigger cần được Quartz Scheduler **đăng ký** qua `SchedulerFactoryBean` hoặc Quartz API.

### Mục tiêu học tập

Sau bài học bạn sẽ:

1. Tạo một `JobDetail` bằng `JobDetailFactoryBean` với tên/nhóm, job class, `JobDataMap`, durability và recovery.
2. Chọn `SimpleTriggerFactoryBean` hoặc `CronTriggerFactoryBean` theo ý nghĩa thời gian.
3. Biết Spring có **hai convenience Trigger FactoryBean chính**, còn Quartz cũng có các loại trigger khác như `CalendarIntervalTrigger` và `DailyTimeIntervalTrigger` có thể tạo bằng Quartz builders.
4. Dùng `SchedulerFactoryBean.setTriggers(...)` để đăng ký hai trigger trỏ cùng một `JobDetail`.
5. Hiểu `FactoryBean` trong Spring: inject bean theo tên thông thường nhận **sản phẩm**; muốn nhận chính factory dùng tiền tố `&`.
6. Chạy ví dụ **Java 17+**, **Spring Framework 6.1.3**, **Quartz 2.3.2** bằng Maven với một simple one-shot trigger và một cron trigger có múi giờ rõ.
7. Phân biệt repeat-count, start-delay, misfire instructions, `JobKey`/`TriggerKey`, `@DisallowConcurrentExecution` và `@PersistJobDataAfterExecution`.
8. Tránh lỗi kết nối Spring bean với Quartz Job, cấu hình RAM/JDBC JobStore và vận hành nhiều replicas.

**Phiên bản cho code độc lập:** Java 17+, Spring Framework **6.1.3** và Quartz **2.3.2**. Project Maven dùng `spring-context-support` vì các lớp `org.springframework.scheduling.quartz` nằm trong module đó, không chỉ trong `spring-context`.

## 2. Bức tranh kiến trúc

~~~text
Spring ApplicationContext
  |
  |-- @Bean JobDetailFactoryBean
  |       +--> JobDetail
  |             key="reports/export"
  |             jobClass=ExportJob.class
  |             JobDataMap={"source": "demo"}
  |
  |-- @Bean SimpleTriggerFactoryBean
  |       +--> SimpleTrigger ----+
  |             startDelay      |
  |             repeatCount     |
  |                             |
  |-- @Bean CronTriggerFactoryBean
  |       +--> CronTrigger -----+---> cùng JobKey của JobDetail
  |             cronExpression  |
  |             timeZone        |
  |                             |
  +-- @Bean SchedulerFactoryBean
          |
          +--> Quartz Scheduler
                 |
                 +--> JobStore (RAM hoặc JDBC)
                 +--> Quartz ThreadPool
                 +--> tính fire times / misfire
                 +--> JobFactory tạo ExportJob instance khi fire
                 +--> ExportJob.execute(context)
~~~

Định nghĩa **JobDetail** là thứ ổn định về danh tính nghiệp vụ. **Trigger** định nghĩa các mốc gọi. Một JobDetail có thể được hai trigger dùng chung; ví dụ “chạy thêm một lần sau khi deploy” và “chạy 02:00 mỗi ngày”. Nếu hai trigger cùng đến hạn và không có chính sách chống concurrency, có thể có **hai lần Job execution đồng thời**.

### 2.1. Không nhầm Quartz với @Scheduled của Spring

~~~java
// Spring @Scheduled truyền thống: phương thức của một Spring bean.
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
public void runEveryNight() {
    // ...
}
~~~

Đoạn trên **không** cần Quartz JobDetail/Trigger. Ngược lại, tạo `CronTriggerFactoryBean` **không** làm Spring tự gọi một method `@Scheduled`; Quartz Job được gọi theo Quartz contract.

Nếu business yêu cầu job persistence, DB-backed misfire, quản lý job keys và clustering thì Quartz thường phù hợp hơn. Nếu chỉ cần một timer nội bộ đơn giản, `@Scheduled` có thể ít phức tạp hơn. Chọn theo yêu cầu vận hành, không theo số class hiện có.

## 3. JobDetailFactoryBean tạo một định nghĩa job

`JobDetailFactoryBean` là một `FactoryBean<JobDetail>`, hỗ trợ cấu hình thuộc tính bằng Spring DI. Khi Spring khởi tạo bean, factory dựng một `JobDetail` thông qua Quartz API tương ứng.

### 3.1. Thuộc tính quan trọng

| Setter | Chức năng | Lưu ý |
| --- | --- | --- |
| `setJobClass(Class<? extends Job>)` | Class Quartz sẽ được tạo để thực thi | Phải tuân hợp đồng `org.quartz.Job` |
| `setName(String)` | Tên job | Nên chọn tên ổn định, mô tả nghiệp vụ |
| `setGroup(String)` | Group của job | `(group, name)` tạo thành `JobKey` |
| `setDescription(String)` | Mô tả | Phù hợp cho quản trị/monitoring |
| `setJobDataAsMap(Map<String, ?>)` | Thêm dữ liệu vào `JobDataMap` | Dùng kiểu đơn giản, serializable khi JDBC JobStore |
| `setJobDataMap(JobDataMap)` | Cung cấp JobDataMap đầy đủ | Không bỏ Spring-managed object vào persistent map |
| `setDurability(boolean)` | Giữ JobDetail ngay cả khi không còn trigger trỏ tới | **Không** đồng nghĩa lưu qua restart |
| `setRequestsRecovery(boolean)` | Đánh dấu job muốn recovery khi có failover phù hợp | Recovery phụ thuộc Quartz/JobStore, không bảo đảm exactly once |
| `setApplicationContextJobDataKey(String)` | Thêm ApplicationContext vào JobDataMap cho một số cách kết nối | **Không phù hợp** với persistent JobDataMap; dùng SchedulerContext |

Tên và group ổn định rất quan trọng với JDBC JobStore: Quartz dùng `JobKey` để nhận diện job. Thay tên mỗi lần startup có thể tạo các định nghĩa mới, không tự sửa định nghĩa cũ.

### 3.2. Định nghĩa job qua @Bean

~~~java
// Fragment trong @Configuration; cần import các class tương ứng.
@Bean("exportJobDetail")
public JobDetailFactoryBean exportJobDetail() {
    JobDetailFactoryBean factory = new JobDetailFactoryBean();
    factory.setJobClass(ExportJob.class);
    factory.setName("nightlyExport");
    factory.setGroup("reporting");
    factory.setDescription("Export báo cáo");
    factory.setDurability(true);
    factory.setRequestsRecovery(false);
    factory.setJobDataAsMap(Map.of("format", "csv"));
    return factory;
}
~~~

Kết quả Spring bean tên `exportJobDetail` thông thường là một **`JobDetail`**. Bản thân `ExportJob` vẫn là class thực thi; Quartz có thể tạo một **instance mới cho từng lần fire**. Không có nghĩa Spring sẽ tự tạo singleton `ExportJob` và gọi `@PostConstruct` trước mỗi execution.

### 3.3. @Bean FactoryBean và cú pháp &beanName

Ở điểm dùng Spring DI:

~~~java
// Fragment trong bean method khác cùng ApplicationContext.
@Bean
public SimpleTriggerFactoryBean exportOnce(
        @Qualifier("exportJobDetail") JobDetail jobDetail) {
    SimpleTriggerFactoryBean factory =
            new SimpleTriggerFactoryBean();
    factory.setJobDetail(jobDetail);
    factory.setRepeatCount(0);
    return factory;
}
~~~

Tham số `@Qualifier("exportJobDetail") JobDetail` nhận **sản phẩm** của `JobDetailFactoryBean`. Nếu thật sự muốn quản trị object factory, Spring dùng cách lookup với tiền tố `&`:

~~~java
// Fragment với ApplicationContext ctx đã có.
JobDetail product = ctx.getBean("exportJobDetail", JobDetail.class);

JobDetailFactoryBean factory =
        ctx.getBean("&exportJobDetail", JobDetailFactoryBean.class);
~~~

Đây là quy tắc **Spring FactoryBean**. Nó giải thích vì sao `@Bean JobDetailFactoryBean` có thể được inject vào method cần `JobDetail` mà không phải tự gọi `afterPropertiesSet()` hay `getObject()` ở tầng business.

### 3.4. Durability không phải persistent storage

`setDurability(true)` chỉ nói Quartz rằng JobDetail **có thể tồn tại khi không có trigger nào còn gắn với nó**. Nếu dùng `RAMJobStore` mặc định, JobDetail vẫn **mất khi JVM/process restart**, dù durability true.

Ngược lại, muốn khôi phục lịch sau restart, phải cấu hình `JDBCJobStore`/Quartz tables, transaction và chính sách khởi tạo dữ liệu phù hợp. Bật `requestsRecovery(true)` cũng không tự tạo database; nó không phải nút bật “tự động chạy lại mọi job bị lỡ và exactly-once”.

### 3.5. JobDataMap nên chứa dữ liệu nào?

`JobDataMap` có thể chứa String, primitive wrappers và các giá trị cấu hình nhỏ, ví dụ `tenantId` đã định danh, `reportType`, `businessPeriod` hoặc resource ID.

~~~java
factory.setJobDataAsMap(Map.of(
    "reportType", "daily",
    "tenantId", "tenant-42",
    "version", 1
));
~~~

Các giá trị ở đây là **fragment minh họa**. Với JDBC JobStore, dữ liệu cần serializable và an toàn qua các lần deploy, upgrade class, thay cấu trúc serialization. Tránh đặt `DataSource`, `ApplicationContext`, Spring singleton service, `EntityManager`, transaction object hoặc thread-local context vào JobDataMap rồi mong Quartz “tiêm DI” đúng.

Nếu Job cần gọi một Spring service, có thể cấu hình `SpringBeanJobFactory` hoặc custom `JobFactory`, lấy bean qua `SchedulerContext` theo cơ chế kiểm soát, hoặc thiết kế job chỉ chứa ID và gọi service qua lớp adapter được quản lý. Đừng đưa raw bean reference vào persistent JobDataMap.

## 4. SimpleTriggerFactoryBean: một lần hoặc lặp theo khoảng

`SimpleTriggerFactoryBean` tạo `org.quartz.SimpleTrigger`, một Quartz trigger dùng cho “chạy một lần sau một khoảng delay” hoặc “chạy nhiều lần với khoảng thời gian xác định”.

### 4.1. Các setter

~~~java
// Fragment: JobDetail đã có.
SimpleTriggerFactoryBean simple = new SimpleTriggerFactoryBean();
simple.setName("warmupOnce");
simple.setGroup("reporting");
simple.setJobDetail(jobDetail);
simple.setStartDelay(2_000L);  // milliseconds kể từ lúc bean khởi tạo
simple.setRepeatCount(0);     // 0 lần LẶP, tổng 1 lần fire
~~~

Một số setter đáng chú ý:

- `setStartDelay(long)`: số mili-giây cộng với thời điểm bean được khởi tạo để xác định start-time. Đây **không phải** delay so với lần request HTTP gần nhất.
- `setStartTime(Date)`: chỉ định thời điểm cụ thể. Nếu dùng `startDelay` dương, thời điểm dynamic tính lúc init có thể ghi đè startTime.
- `setRepeatInterval(long)`: khoảng thời gian giữa các lần fire, **đơn vị milliseconds**.
- `setRepeatCount(int)`: **số lần lặp thêm sau lần đầu**. `0` nghĩa là fire **một lần**; `3` nghĩa là dự kiến **bốn lần** tổng cộng, nếu không có misfire/cancel hay end-time cắt bớt.
- `SimpleTrigger.REPEAT_INDEFINITELY`: lặp không giới hạn đến khi bị hủy hoặc điều kiện dừng phù hợp.
- `setMisfireInstruction(...)` hoặc `setMisfireInstructionName(...)`: chính sách khi bỏ lỡ thời điểm.
- `setJobDetail(JobDetail)`: gắn `JobKey`, đồng thời hỗ trợ SchedulerFactoryBean phát hiện job tương ứng khi đăng ký trigger.

### 4.2. Một bảng đếm minh họa repeat count

| `repeatCount` | Lần fire dự kiến nếu hoạt động bình thường |
| --- | --- |
| `0` | Một lần tại startTime |
| `1` | Hai lần: startTime và 1 lần lặp |
| `3` | Bốn lần: startTime và 3 lần lặp |
| `SimpleTrigger.REPEAT_INDEFINITELY` | Lặp liên tục theo interval đến khi cancel/end-time |

Không viết “repeatCount=3 sẽ chạy đúng ba lần”: nó đếm **số lần lặp**, không đếm tổng số lần fire.

### 4.3. Định nghĩa một trigger lặp từ Spring bean

~~~java
// Fragment trong @Configuration.
@Bean("pollTrigger")
public SimpleTriggerFactoryBean pollTrigger(
        @Qualifier("pollJobDetail") JobDetail detail) {
    SimpleTriggerFactoryBean bean =
            new SimpleTriggerFactoryBean();
    bean.setName("everyThirtySeconds");
    bean.setGroup("polling");
    bean.setJobDetail(detail);
    bean.setStartDelay(5_000L);
    bean.setRepeatInterval(30_000L);
    bean.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
    return bean;
}
~~~

Các class `JobDetail`, `SimpleTrigger`, `SimpleTriggerFactoryBean` và `Qualifier` cần imports; `pollJobDetail` phải có bean thực. Đây là **fragment**, khác project đầy đủ ở phần sau.

### 4.4. Quartz SimpleTrigger không phải Spring @Scheduled(fixedDelay)

`SimpleTrigger` thường dựa vào **các mốc fire-time được tính từ startTime và repeatInterval**. Không tự hiểu nó là “nghỉ interval sau khi toàn bộ Job.execute() đã hoàn tất” giống ý nghĩa fixed-delay trong một số scheduler API.

Nếu job chạy lâu hơn repeat interval và thread pool đủ chỗ, nhiều executions của **cùng JobDetail** có thể chồng lấn khi không có `@DisallowConcurrentExecution`. Nếu cần “xong mới được làm lượt sau”, dùng concurrency policy phù hợp hoặc thiết kế queue/job-state; đừng chỉ đổi số giây.

## 5. CronTriggerFactoryBean: mốc lịch theo cron và timezone

`CronTriggerFactoryBean` tạo `org.quartz.CronTrigger`, thích hợp cho lịch theo giờ/ngày/thứ, ví dụ “02:00 mỗi ngày” hoặc “đầu giờ trong ngày làm việc”.

### 5.1. Các thuộc tính

~~~java
// Fragment trong @Configuration.
@Bean("nightlyTrigger")
public CronTriggerFactoryBean nightlyTrigger(
        @Qualifier("nightlyJobDetail") JobDetail detail) {
    CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
    factory.setName("dailyAtTwo");
    factory.setGroup("reporting");
    factory.setJobDetail(detail);
    factory.setCronExpression("0 0 2 * * ?");
    factory.setTimeZone(
            TimeZone.getTimeZone(ZoneId.of("Asia/Ho_Chi_Minh")));
    return factory;
}
~~~

Giải thích:

- `setCronExpression(String)` nhận **Quartz cron expression**; không copy vô điều kiện expression từ Spring `@Scheduled`, Linux crontab hoặc cron parser khác.
- `setTimeZone(TimeZone)` quy định cách tính giờ theo lịch tường; chọn zone có định danh, tránh phụ thuộc timezone của server.
- `setStartTime(Date)`/`setStartDelay(long)` xác định thời điểm trigger bắt đầu có hiệu lực, không phải lịch hẹn đều đặn độc lập cron.
- `setName`, `setGroup`, `setJobDetail` tạo định danh trigger và link tới `JobKey`.
- `setMisfireInstruction` hoặc `setMisfireInstructionName` chọn cách xử lý khi mốc bị lỡ; không tự đồng nghĩa với recovery sau JVM downtime ở RAMJobStore.

### 5.2. Cú pháp Quartz cron

Quartz 2.3.x thường dùng **6 trường bắt buộc** và **trường năm thứ 7 tùy chọn**:

~~~text
giây  phút  giờ  ngày-trong-tháng  tháng  ngày-trong-tuần  [năm]
 0     0    2          *            *           ?       
~~~

Ví dụ:

| Quartz cron | Ý định |
| --- | --- |
| `0 0 2 * * ?` | 02:00 mỗi ngày |
| `0 0/15 * * * ?` | Mỗi 15 phút, giây 0 |
| `0 0 9 ? * MON-FRI` | 09:00 thứ Hai–Sáu |
| `0 0 12 ? * MON#2` | 12:00 vào thứ Hai thứ hai của tháng |
| `0 0 0 1 1 ? 2099` | Một mốc ở 00:00 ngày 01/01/2099 nếu còn hiệu lực |

Trong Quartz, dấu `?` được dùng trong trường day-of-month/day-of-week để biểu thị “không đặt điều kiện cụ thể tại đây”; một số biểu thức kết hợp hai trường ngày có ràng buộc riêng. Các ký hiệu `L`, `W`, `#` cần đọc theo Quartz cron grammar, không mặc định giống thư viện Spring cron.

**Khác biệt dễ mắc:** Quartz day-of-week hỗ trợ số **1–7 với 1 là Sunday**, trong khi Spring `@Scheduled` dựa trên cách đếm có thể khác. Để tránh nhầm, dùng tên `MON`, `TUE`, … khi liên quan ngày trong tuần và kiểm tra bằng Quartz `CronExpression`.

### 5.3. Timezone và DST

“02:00 hằng ngày” không luôn cách nhau đúng 24 giờ ở những khu vực có daylight saving time (DST). Một giờ có thể không tồn tại hoặc bị lặp lại. `CronTriggerFactoryBean.setTimeZone(...)` giúp thuật toán cron dùng đúng zone, nhưng không thể thay chính sách nghiệp vụ với ngày giờ bị mất hoặc nhân đôi.

Với tác vụ tài chính/đối soát, dùng **business period ID** và idempotency. Đừng tự cộng `Duration.ofDays(1)` vào UTC timestamp rồi khẳng định đó luôn là 02:00 giờ địa phương ngày mai.

## 6. Các loại trigger khác của Quartz và phạm vi hỗ trợ của Spring

Spring Framework có **hai convenience FactoryBean** cho Quartz triggers: `SimpleTriggerFactoryBean` và `CronTriggerFactoryBean`. Điều đó **không có nghĩa** Quartz chỉ có hai loại trigger.

Quartz 2.x còn cung cấp:

| Quartz trigger/interface | Nhu cầu | Tạo như thế nào? |
| --- | --- | --- |
| `SimpleTrigger` | Một lần, khoảng lặp milliseconds | `SimpleTriggerFactoryBean` hoặc `TriggerBuilder` + `SimpleScheduleBuilder` |
| `CronTrigger` | Quy tắc cron theo lịch | `CronTriggerFactoryBean` hoặc `CronScheduleBuilder` |
| `CalendarIntervalTrigger` | Cộng khoảng theo đơn vị lịch: ngày, tuần, tháng, năm | `CalendarIntervalScheduleBuilder` |
| `DailyTimeIntervalTrigger` | Lặp trong một cửa sổ giờ/ngày cụ thể | `DailyTimeIntervalScheduleBuilder` |

Các builder trả về Quartz `Trigger` thông thường; `SchedulerFactoryBean.setTriggers(Trigger...)` có thể nhận chúng. Không có yêu cầu phải viết một Spring `FactoryBean` mới cho mỗi loại trigger, cũng không nên tự suy ra tên class `CalendarIntervalTriggerFactoryBean` đã tồn tại trong Spring 6.1.3.

### 6.1. Tạo trigger bằng Quartz builder

~~~java
// Fragment: JobDetail detail đã tồn tại.
// Các import: org.quartz.Trigger,
// TriggerBuilder, SimpleScheduleBuilder.
Trigger everyTenSeconds = TriggerBuilder.newTrigger()
        .withIdentity("poll10s", "reports")
        .forJob(detail)
        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(10)
                .repeatForever())
        .startNow()
        .build();
~~~

Đối với lịch cron, có thể dùng:

~~~java
// Fragment: Quartz API; dùng Java TimeZone.
Trigger daily = TriggerBuilder.newTrigger()
        .withIdentity("dailyAtTwo", "reports")
        .forJob(detail)
        .withSchedule(CronScheduleBuilder
                .cronSchedule("0 0 2 * * ?")
                .inTimeZone(TimeZone.getTimeZone(
                        ZoneId.of("Asia/Ho_Chi_Minh"))))
        .build();
~~~

Đối với lịch tăng theo ngày lịch, dùng Quartz builder thay vì tự mô phỏng bằng repeatInterval=24h:

~~~java
// Fragment: Quartz CalendarIntervalScheduleBuilder.
Trigger dailyCalendarInterval = TriggerBuilder.newTrigger()
        .withIdentity("dailyCalendar", "reports")
        .forJob(detail)
        .withSchedule(CalendarIntervalScheduleBuilder
                .calendarIntervalSchedule()
                .withIntervalInDays(1))
        .startNow()
        .build();
~~~

Builder trả về interface `Trigger` cho phép Spring `SchedulerFactoryBean.setTriggers(...)` sử dụng. Không nhầm **Quartz CalendarIntervalTrigger** với **Spring CronTrigger** từ `org.springframework.scheduling.support`: chúng có package, API và cách đăng ký khác nhau.

### 6.2. Calendar của Quartz không phải java.util.Calendar đơn thuần

Quartz còn có các **Quartz Calendar** để loại trừ một số ngày/giờ, ví dụ `HolidayCalendar`. Chúng khác `CalendarIntervalTrigger`. Calendar có thể được đăng ký với Scheduler để trigger dùng lịch loại trừ theo tên. Đây là một tầng constraint bên cạnh trigger rule, không phải subclass của Spring `CronTriggerFactoryBean`.

## 7. SchedulerFactoryBean: đăng ký và thực thi

`SchedulerFactoryBean` tạo một Quartz `Scheduler` và quản lý lifecycle của scheduler cùng Spring ApplicationContext. Một số setter thường dùng:

- `setTriggers(Trigger...)`: đăng ký các trigger đã cấu hình; trigger phải liên kết tới `JobDetail`/`JobKey` hợp lệ.
- `setJobDetails(JobDetail...)`: đăng ký JobDetails trực tiếp, hữu ích cho durable jobs chưa có trigger hoặc khi cần tách đăng ký.
- `setAutoStartup(boolean)`: có tự start khi context refresh không; mặc định true.
- `setStartupDelay(int)`: delay theo **giây** trước khi start scheduler.
- `setWaitForJobsToCompleteOnShutdown(boolean)`: yêu cầu chờ jobs hiện đang thực thi lúc scheduler shutdown; không bảo đảm durable completion với job bị kill bởi OS.
- `setOverwriteExistingJobs(boolean)`: chọn có ghi đè job/trigger đã tồn tại trong JobStore khi startup; cần cân nhắc với JDBC persistence.
- `setQuartzProperties(Properties)`: cung cấp Quartz thread pool/job store/scheduler properties.
- `setDataSource(DataSource)`: dùng Spring-integrated JDBC job store; cần hiểu transaction/config theo Javadoc.
- `setJobFactory(JobFactory)`: thay cách Quartz tạo Job instances, có thể hỗ trợ tích hợp Spring bean.
- `setSchedulerContextAsMap(Map<String,?>)`: đặt object dùng chung cho các jobs trong SchedulerContext thay vì persistent JobDataMap.

### 7.1. Đăng ký JobDetail thông qua trigger hay riêng?

Theo Javadoc `SimpleTriggerFactoryBean`/`CronTriggerFactoryBean`, khi gọi `setJobDetail(detail)`, trigger được liên kết tới job và Spring hỗ trợ đăng ký JobDetail tương ứng trong quá trình `SchedulerFactoryBean` xử lý triggers. Vì vậy, với ví dụ một JobDetail được hai triggers dùng chung, có thể cấu hình **chỉ** `setTriggers(simple, cron)` mà không cần đăng ký JobDetail riêng lần nữa.

~~~java
// Fragment: simple và cron là Quartz Trigger sản phẩm của FactoryBeans.
SchedulerFactoryBean factory = new SchedulerFactoryBean();
factory.setTriggers(simple, cron);
~~~

Nếu một job **không có bất kỳ trigger nào** nhưng cần tồn tại để scheduler trigger thủ công sau, hãy dùng `setJobDetails(detail)` và đánh dấu job durable phù hợp. Với JDBC JobStore, tránh tạo cùng JobKey hai lần bằng các startup paths khác nhau mà không định nghĩa overwrite/update policy.

### 7.2. SchedulerFactoryBean khác ThreadPoolTaskScheduler

`ThreadPoolTaskScheduler` là wrapper Spring cho JDK `ScheduledThreadPoolExecutor`. Nó không có Quartz `JobDetail`, `JobKey`, JDBC JobStore hoặc misfire instructions Quartz.

`SchedulerFactoryBean` dùng **Quartz Scheduler** với Quartz thread pool mặc định, trừ khi bạn cấu hình Quartz backend khác. Đừng lấy các setter `setPoolSize`/`queueCapacity` của Spring thread pools rồi áp thẳng lên Quartz SchedulerFactoryBean. Quartz dùng `org.quartz.threadPool.threadCount`, job store và concurrency control tương ứng.

## 8. Maven project Spring Framework 6.1.3 + Quartz 2.3.2

Bài demo thực hiện:

1. Tạo `JobDetailFactoryBean` cho class `DemoJob`, đặt key `knowledge/demoJob`, `durability=true` và JobDataMap với String `source`.
2. Tạo **SimpleTriggerFactoryBean** chỉ chạy một lần sau một khoảng rất ngắn.
3. Tạo **CronTriggerFactoryBean** cho 02:00 hằng ngày tại `Asia/Ho_Chi_Minh`, cùng trỏ về `demoJob`.
4. Cấu hình `SchedulerFactoryBean` với cả hai triggers; lấy Quartz `Scheduler` từ Spring context.
5. Dùng `CountDownLatch` để xác nhận **ít nhất một lần `DemoJob.execute()` đã chạy**, không đoán theo `Thread.sleep`.
6. In các thuộc tính được lấy trực tiếp từ JobDetail/Trigger, không in timestamp hay tên thread phụ thuộc môi trường.

**Cấu trúc:**

~~~text
spring-quartz-knowledge/
├── pom.xml
└── src/main/java/demo/
    ├── DemoJob.java
    ├── QuartzConfig.java
    └── QuartzKnowledgeDemo.java
~~~

### 8.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-quartz-knowledge</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context-support</artifactId>
            <version>6.1.3</version>
        </dependency>
        <dependency>
            <groupId>org.quartz-scheduler</groupId>
            <artifactId>quartz</artifactId>
            <version>2.3.2</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>1.7.36</version>
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
                    <mainClass>demo.QuartzKnowledgeDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context-support` chứa Quartz integration classes của Spring. `quartz` là Quartz runtime. Binding `slf4j-simple` chỉ dùng để xem log trong demo nhỏ; production nên dùng logging stack tập trung của ứng dụng, không thêm hai SLF4J bindings cạnh tranh.

### 8.2. DemoJob.java

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public final class DemoJob implements Job {

    public static final CountDownLatch FIRST_EXECUTION =
            new CountDownLatch(1);

    public static final AtomicInteger RUNS =
            new AtomicInteger();

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {

        String source =
                context.getMergedJobDataMap().getString("source");

        if (!"knowledge-demo".equals(source)) {
            throw new JobExecutionException("Unexpected job source");
        }

        RUNS.incrementAndGet();
        FIRST_EXECUTION.countDown();
    }
}
~~~

`DemoJob` có constructor không tham số mặc định hợp lệ để Quartz tạo Job instance. `getMergedJobDataMap()` đọc dữ liệu của JobDetail cộng với dữ liệu bổ sung của Trigger; nếu trùng key, trigger-specific data có quyền ưu tiên trong merged view.

`FIRST_EXECUTION` chỉ để test nội bộ process, **không** phải cách lưu trạng thái jobs thật trong Quartz cluster. Khi Quartz dùng JDBC JobStore, vẫn phải thiết kế cách đọc/ghi job state bền vững.

### 8.3. QuartzConfig.java

~~~java
package demo;

import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;

import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

@Configuration
public class QuartzConfig {

    @Bean("demoJobDetail")
    public JobDetailFactoryBean demoJobDetail() {
        JobDetailFactoryBean bean = new JobDetailFactoryBean();
        bean.setJobClass(DemoJob.class);
        bean.setName("demoJob");
        bean.setGroup("knowledge");
        bean.setDescription("Job co hai trigger");
        bean.setDurability(true);
        bean.setRequestsRecovery(false);
        bean.setJobDataAsMap(Map.of("source", "knowledge-demo"));
        return bean;
    }

    @Bean("onceTrigger")
    public SimpleTriggerFactoryBean onceTrigger(
            @Qualifier("demoJobDetail") JobDetail detail) {

        SimpleTriggerFactoryBean bean =
                new SimpleTriggerFactoryBean();
        bean.setName("once");
        bean.setGroup("knowledge");
        bean.setJobDetail(detail);
        bean.setStartDelay(250L);
        bean.setRepeatCount(0);
        return bean;
    }

    @Bean("nightlyTrigger")
    public CronTriggerFactoryBean nightlyTrigger(
            @Qualifier("demoJobDetail") JobDetail detail) {

        CronTriggerFactoryBean bean =
                new CronTriggerFactoryBean();
        bean.setName("nightly");
        bean.setGroup("knowledge");
        bean.setJobDetail(detail);
        bean.setCronExpression("0 0 2 * * ?");
        bean.setTimeZone(TimeZone.getTimeZone(
                ZoneId.of("Asia/Ho_Chi_Minh")));
        return bean;
    }

    @Bean("quartzScheduler")
    public SchedulerFactoryBean quartzScheduler(
            @Qualifier("onceTrigger") SimpleTrigger once,
            @Qualifier("nightlyTrigger") CronTrigger nightly) {

        SchedulerFactoryBean bean = new SchedulerFactoryBean();
        bean.setSchedulerName("KnowledgeQuartz");
        bean.setTriggers(once, nightly);
        bean.setAutoStartup(true);
        bean.setWaitForJobsToCompleteOnShutdown(true);
        return bean;
    }
}
~~~

**Điểm cần chú ý:** Các phương thức `@Bean` trả về **FactoryBean** và Spring inject **sản phẩm** `JobDetail`, `SimpleTrigger`, `CronTrigger` vào những tham số khác nhờ tên bean. Bạn không cần tự gọi `afterPropertiesSet()` trên factory hoặc cast `FactoryBean` thành `JobDetail`.

Simple one-shot trigger có `repeatCount=0` (một lần fire). Cron trigger có lịch 02:00 hằng ngày, nhưng demo **không đợi tới 02:00**. Hai trigger cùng trỏ tới `knowledge.demoJob`. Với việc demo chạy vài giây, CronTrigger chủ yếu chứng minh cấu hình, định danh và timezone, không đòi thực thi cron ngay.

### 8.4. QuartzKnowledgeDemo.java

~~~java
package demo;

import java.util.concurrent.TimeUnit;

import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class QuartzKnowledgeDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             QuartzConfig.class)) {

            Scheduler scheduler = context.getBean(Scheduler.class);

            JobKey key = JobKey.jobKey("demoJob", "knowledge");
            JobDetail registered = scheduler.getJobDetail(key);

            if (registered == null) {
                throw new IllegalStateException(
                        "JobDetail not registered");
            }

            SimpleTrigger once =
                    context.getBean("onceTrigger", SimpleTrigger.class);
            CronTrigger nightly =
                    context.getBean("nightlyTrigger", CronTrigger.class);

            System.out.println("jobKey="
                    + registered.getKey().getGroup() + "/"
                    + registered.getKey().getName());
            System.out.println("jobClassCorrect="
                    + (registered.getJobClass() == DemoJob.class));
            System.out.println("durable=" + registered.isDurable());
            System.out.println("sameJob="
                    + (once.getJobKey().equals(nightly.getJobKey())));
            System.out.println("simpleRepeatCount="
                    + once.getRepeatCount());
            System.out.println("cronExpression="
                    + nightly.getCronExpression());
            System.out.println("cronZone="
                    + nightly.getTimeZone().getID());

            if (!DemoJob.FIRST_EXECUTION.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "One-shot trigger did not execute");
            }

            System.out.println("firedAtLeastOnce="
                    + (DemoJob.RUNS.get() >= 1));
        }
    }
}
~~~

### 8.5. Lệnh chạy và stdout

Đặt các file đúng tên, package `demo` và cây thư mục đã nêu. Trong thư mục project:

~~~bash
mvn -q compile exec:java
~~~

**Các dòng do chương trình Java chủ động in nếu Quartz khởi động và one-shot job chạy trước timeout:**

~~~text
jobKey=knowledge/demoJob
jobClassCorrect=true
durable=true
sameJob=true
simpleRepeatCount=0
cronExpression=0 0 2 * * ?
cronZone=Asia/Ho_Chi_Minh
firedAtLeastOnce=true
~~~

Quartz và logging framework có thể in thêm các dòng INFO khác; chúng **không** nằm trong danh sách output chủ động ở trên.

### 8.6. Vì sao ví dụ không dựa vào may rủi của Thread.sleep?

1. Spring context hoàn tất khởi tạo `SchedulerFactoryBean`, đăng ký triggers và start Quartz.
2. JobDetail được tìm theo `JobKey` ổn định: group `knowledge`, name `demoJob`.
3. Code lấy hai Trigger sản phẩm của FactoryBeans, vì vậy vẫn kiểm tra được `once.getJobKey()` ngay cả sau khi one-shot trigger đã bắn và có thể bị Quartz dọn khỏi JobStore.
4. `FIRST_EXECUTION.await(5, SECONDS)` đợi tín hiệu từ chính `DemoJob.execute()`. Khi latch giảm, ít nhất một job execution đã qua phần xác minh data map.
5. Bộ đếm `RUNS` có thể tăng thêm nếu thời gian trùng một mốc cron. Vì vậy bài in **`firedAtLeastOnce=true`**, không khẳng định chính xác một lần trên mọi múi giờ/máy chủ.
6. `try-with-resources` đóng `AnnotationConfigApplicationContext`, Spring shutdown Quartz Scheduler theo lifecycle. Demo không để một scheduler vô hạn sống không quản lý.

**Hạn chế:** Các dòng output ở trên là **kỳ vọng theo semantics và QA đọc mã**. Nếu môi trường không tải được Maven dependencies hoặc scheduler không thể thực thi trong 5 giây, chương trình sẽ báo lỗi/timeout rõ ràng. Đây không phải bằng chứng demo đã được biên dịch và chạy trên mọi máy.

## 9. JobDataMap của JobDetail và từng trigger

Hai trigger cùng trỏ một `JobKey` nhưng vẫn có thể truyền tham số khác nhau vào cùng Job class. `JobDetailFactoryBean.setJobDataAsMap(...)` đặt dữ liệu mặc định của job; mỗi `SimpleTriggerFactoryBean` hoặc `CronTriggerFactoryBean` có thể đặt thêm `JobDataMap` của chính trigger. Quartz cung cấp kết quả qua `JobExecutionContext.getMergedJobDataMap()`, trong đó **giá trị trigger ghi đè giá trị cùng key của JobDetail**.

~~~java
// Fragment trong lớp cấu hình đã tạo các FactoryBean này.
jobDetailFactory.setJobDataAsMap(Map.of("reportType", "general"));
simpleTriggerFactory.setJobDataAsMap(Map.of("reportType", "warmup"));
cronTriggerFactory.setJobDataAsMap(Map.of("reportType", "nightly"));
~~~

Với simple trigger, `reportType` là `warmup`; với cron trigger, giá trị là `nightly`. Không đưa Spring service, `ApplicationContext`, `DataSource` hay transaction vào `JobDataMap` lưu JDBC. Hãy lưu ID/giá trị đơn giản, dùng `JobFactory` hoặc service Spring-managed để tiêm dependency.

## 10. Hai triggers cùng JobDetail và giới hạn concurrency

Mỗi firing có thể tạo Job instance riêng. Hai triggers đến hạn sát nhau **có thể** làm cùng một JobDetail chạy đồng thời nếu Quartz worker pool có khả năng xử lý. Khi không được phép overlap cho **cùng JobKey**, gắn `@DisallowConcurrentExecution` trên Job class. Annotation không khóa tất cả JobDetails cùng class có JobKeys khác nhau, cũng không thay unique key/idempotency của nghiệp vụ.

Nếu Job có `@PersistJobDataAfterExecution` để giữ cập nhật `JobDataMap` giữa các lần fire theo hợp đồng Quartz, cần đánh giá race/lost updates; thường kết hợp với `@DisallowConcurrentExecution`. Các kết quả và giao dịch nghiệp vụ quan trọng vẫn nên lưu ở hệ thống business riêng.

## 11. Misfire phải theo đúng loại trigger

`SimpleTrigger` có chính sách liên quan mốc khởi đầu, interval và số lần lặp còn lại; `CronTrigger` dùng chính sách như `MISFIRE_INSTRUCTION_DO_NOTHING` hoặc `MISFIRE_INSTRUCTION_FIRE_ONCE_NOW`. **Misfire là mốc fire bị lỡ**, không phải Job đã chạy rồi ném exception. Chọn chính sách tường minh theo nghiệp vụ: bỏ một lần refresh cache có thể hợp lệ; đối soát cần xử lý đủ ngày thiếu thường phải có checkpoint/backfill độc lập với một lần firing.

Với JDBC JobStore, trigger còn có thể tồn tại sau restart; RAMJobStore không tự lưu lịch qua process exit. Không coi `FireOnceNow` là cơ chế replay tất cả các kỳ bị lỡ.

## 12. Quyền sở hữu JobStore và đường cấu hình JDBC

`JobDetailFactoryBean` cùng các Trigger FactoryBeans chỉ tạo **metadata**; có lưu qua restart hay không là do `JobStore`. Trong Spring, `SchedulerFactoryBean.setDataSource(...)` dùng cơ chế tích hợp JobStore với Spring transaction/data source. Tránh đồng thời ép `org.quartz.jobStore.class` theo kiểu Quartz thuần mà không xác định ownership của connection và locking.

Nếu cần clustered JDBC, mọi Scheduler phải dùng chung schema, có cấu hình cluster nhất quán, instanceId riêng và clock đồng bộ. `@DisallowConcurrentExecution` giới hạn overlap theo JobKey, còn kết quả nghiệp vụ exactly-once vẫn cần idempotency, transaction/outbox hoặc deduplication. Những lựa chọn misfire, JobStore và cluster được phân tích sâu ở các bài chuyên biệt trong chương.

## 13. Vận hành: xem job/trigger có được đăng ký hay chưa

Một số thao tác Quartz Scheduler API hữu ích:

~~~java
// Fragment: Scheduler scheduler, JobKey jobKey và TriggerKey triggerKey đã có.
boolean jobExists = scheduler.checkExists(jobKey);
JobDetail detail = scheduler.getJobDetail(jobKey);

boolean triggerExists = scheduler.checkExists(triggerKey);
Trigger trigger = scheduler.getTrigger(triggerKey);

Trigger.TriggerState state =
        scheduler.getTriggerState(triggerKey);
~~~

Các câu lệnh trên có thể ném `SchedulerException`, nên cần xử lý hoặc khai báo throws. Tên `JobKey` và `TriggerKey` **độc lập**: một TriggerKey định danh trigger; JobKey định danh job mà nó fire.

### 13.1. Khi một job không chạy

Kiểm tra:

1. `SchedulerFactoryBean` có được tạo trong Spring context? `setAutoStartup(true)` hay app đã gọi `start()`?
2. `JobDetailFactoryBean` đã khai báo `setJobClass(...)` đúng `org.quartz.Job` chưa?
3. FactoryBeans có được Spring khởi tạo, bean products có được inject theo đúng name/qualifier không?
4. Triggers đã được truyền cho `SchedulerFactoryBean.setTriggers(...)` hoặc đăng ký qua Scheduler API chưa?
5. Trigger có đúng `JobKey`? Có dùng nhầm group/name và tưởng hai jobs là một không?
6. SimpleTrigger `startDelay` tính bằng **milliseconds**, `repeatCount=0` chỉ chạy một lần; có đang chờ tới startTime không?
7. CronExpression có dùng **Quartz grammar** và đúng zone, có mốc tiếp theo hay không?
8. Trigger có ở trạng thái `PAUSED`, `ERROR`, `COMPLETE` hoặc bị unschedule do một lần fire cuối không?
9. Quartz thread pool/JobStore có thiếu tài nguyên, database lock hoặc misfire không?
10. Job class có exception, constructor/JobFactory không tạo được instance hoặc dependency Spring chưa được nối không?
11. Trong cluster, job có thể chạy ở node khác; đừng chỉ xem log của một pod.
12. Spring context đã đóng, scheduler đang standby hoặc shutdown chưa?

### 13.2. Khi job chạy hai lần

Xác định có hai TriggerKeys cùng trỏ một JobKey không, có hai JobDetails khác Key nhưng dùng chung class không, có hai SchedulerFactoryBeans tự chạy không và môi trường có nhiều JVM với RAMJobStore không.

Nếu hai trigger cùng fire, đó có thể là **cấu hình đúng** chứ không phải double invocation bug. Tính idempotency theo **business key**, không chỉ đếm `Job.execute()` đã được gọi.

## 14. Chọn giữa FactoryBean và Quartz builders

Spring FactoryBeans thuận lợi khi:

- Job/Trigger cấu hình tĩnh lúc startup, được inject bằng `@Bean` hoặc XML.
- Muốn giữ tên/group và liên kết dependency được quản lý trong ApplicationContext.
- Muốn `SchedulerFactoryBean` tự đăng ký các trigger ở một nơi rõ ràng.

Quartz builders thuận lợi khi:

- Lịch tạo động từ database hoặc request có kiểm soát.
- Cần các trigger types như `CalendarIntervalTrigger` hoặc `DailyTimeIntervalTrigger`.
- Muốn dùng Quartz API trực tiếp để `scheduleJob`, `rescheduleJob`, `pauseTrigger`, `resumeTrigger`, `unscheduleJob`.

Có thể kết hợp cả hai, nhưng phải định nghĩa **owner của mỗi JobKey/TriggerKey**. Không để startup registration ghi đè lịch do người dùng đã thay đổi trong database chỉ vì cùng key, trừ khi đó là chính sách deploy có chủ đích.

## 15. Những sai lầm phổ biến

1. **“`JobDetailFactoryBean` là một Quartz job instance.”** Sai; sản phẩm là `JobDetail`, không phải `Job.execute()`.
2. **“Tạo `CronTriggerFactoryBean` là job tự chạy.”** Sai; phải đăng ký qua Quartz Scheduler.
3. **“Spring 6.1.3 chỉ cần `spring-context` để dùng lớp quartz integration.”** Sai; sử dụng `spring-context-support` và Quartz dependency.
4. **“`repeatCount=3` nghĩa là chạy tổng cộng 3 lần.”** Sai; nó là ba lần lặp **sau lần đầu**.
5. **“`startDelay=5000` là 5000 giây.”** Sai; `SimpleTriggerFactoryBean` nhận milliseconds.
6. **“`durability=true` nghĩa là giữ job qua JVM restart.”** Sai; RAMJobStore vẫn mất state.
7. **“`requestsRecovery=true` tự bảo đảm exactly once.”** Sai; recovery chỉ có ý nghĩa khi job store và failover hỗ trợ, vẫn cần idempotency.
8. **“Quartz CronTrigger và Spring @Scheduled dùng cron grammar y hệt.”** Không nên giả định; kiểm tra parser/package.
9. **“Hai Trigger sử dụng một JobDetail nghĩa là chỉ chạy một lần nếu cùng lúc.”** Sai; có thể có hai executions.
10. **“`@DisallowConcurrentExecution` khóa toàn Job class across mọi JobKeys.”** Sai; phạm vi theo JobDetail/JobKey.
11. **“Spring `@Autowired` luôn có hiệu lực trong Quartz Job class.”** Không mặc định; JobFactory tạo instance.
12. **“`SpringBeanJobFactory` tự làm đầy đủ mọi constructor injection theo Spring Boot mặc định.”** Không nên khẳng định; kiểm tra cách tạo Job và customization.
13. **“Có Quartz JDBC là khỏi cần bảng schema và transaction.”** Sai.
14. **“Đặt bean object vào JobDataMap là cách tốt để serialize service.”** Sai, nguy hiểm với JDBC persistence.
15. **“Misfire nghĩa là retry một Job.execute() đã ném exception.”** Sai; misfire là bỏ lỡ fire time.
16. **“Cron FIRE_ONCE_NOW sẽ bù tất cả mọi kỳ lịch bị bỏ lỡ.”** Sai; đây là một lần fire bù.
17. **“SchedulerFactoryBean.setWaitForJobsToCompleteOnShutdown(true) bảo đảm process không bị kill bởi orchestrator.”** Sai; grace timeout OS/container vẫn có thể kết thúc process.
18. **“Cron trigger 02:00 tương đương cứ cách 24 giờ.”** Không luôn đúng với DST/timezone.
19. **“Quartz có đúng hai trigger types.”** Sai; Spring chỉ cung cấp hai FactoryBeans phổ biến.
20. **“Mọi trigger nên được đăng ký riêng bằng `setJobDetails` lặp lại.”** Không; factory triggers có thể kéo theo JobDetail phù hợp, đừng tạo duplicate registration.
21. **“One-shot trigger phải còn nằm trong JobStore sau khi đã fire.”** Không; Quartz có thể dọn completed trigger, dù JobDetail durable có thể tồn tại.
22. **“`getMergedJobDataMap()` chỉ chứa JobDataMap của JobDetail.”** Sai; nó hợp nhất với trigger map.
23. **“JobDetail có thể lưu Spring transaction ThreadLocal trong DB để dùng lần chạy sau.”** Sai; transaction và resource có lifecycle/thread riêng.
24. **“Mỗi node chạy Quartz với RAMJobStore là thành cluster.”** Không; mỗi RAMJobStore độc lập, có thể chạy trùng.
25. **“Đổi TriggerKey/JobKey ở mỗi startup không có tác dụng phụ.”** Sai; với JDBC có thể tạo lịch mới hoặc để lại job cũ nếu không cleanup.

## 16. Checklist review và triển khai

- [ ] Spring version và Quartz version tương thích, `spring-context-support` được thêm cùng Quartz runtime.
- [ ] `JobDetailFactoryBean.setJobClass` trỏ đúng class implement `org.quartz.Job`, class có cơ chế tạo instance phù hợp JobFactory.
- [ ] Tên/group của `JobKey` ổn định, không tùy tiện đổi ở startup.
- [ ] Xác định durability, requestsRecovery và JobStore RAM/JDBC **là ba khái niệm khác nhau**.
- [ ] JobDataMap dùng scalar IDs hoặc cấu hình có thể persist; không nhét Spring beans/connection/session vào persistent map.
- [ ] Xác định rõ số TriggerKeys trỏ vào mỗi JobKey và có được overlap hay không.
- [ ] Chọn `SimpleTriggerFactoryBean` cho một lần/khoảng thời gian hoặc `CronTriggerFactoryBean` cho mốc lịch theo timezone.
- [ ] Tài liệu hóa repeatCount là **số lần lặp thêm**, startDelay/repeatInterval là **milliseconds**.
- [ ] Với cron, dùng Quartz grammar, timezone tường minh và test DST/mốc giao nhau.
- [ ] Những trigger types khác dùng Quartz builders, không giả định Spring có FactoryBean cho mỗi loại.
- [ ] `SchedulerFactoryBean` đăng ký JobDetails/Triggers một lần với owner/overwrite policy rõ ràng.
- [ ] Cấu hình Quartz thread pool, JobStore, startup/shutdown và metrics; không nhầm với Spring `ThreadPoolTaskScheduler`.
- [ ] Cân nhắc `@DisallowConcurrentExecution` cho cùng JobKey và `@PersistJobDataAfterExecution` khi có lưu state có kiểm soát.
- [ ] Misfire instructions được chọn theo nghiệp vụ: skip hay fire-once-now, không nhầm với retry failed job.
- [ ] Nếu cần durable/cluster, cấu hình JDBC Quartz tables, locks, transactions, IDs, recovery và idempotency.
- [ ] Nếu cần Spring service injection, kiểm tra JobFactory/SchedulerContext rõ, không phụ thuộc @Autowired ngầm.
- [ ] Test một trigger fire bằng latch/observable outcome, không chỉ `Thread.sleep`.
- [ ] Test hai triggers trỏ một JobDetail, và case job xử lý lâu cần giới hạn concurrency.
- [ ] Với nhiều replicas, kiểm tra cùng database JobStore/cluster và business-period dedup.
- [ ] Khi shutdown, xác định có chờ jobs hiện đang chạy không và container grace budget.
- [ ] Theo dõi TriggerState, nextFireTime, previousFireTime, job errors/misfires/retries và missed business periods.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** `JobDetailFactoryBean` trả về sản phẩm gì?  
**Đáp án:** `org.quartz.JobDetail`, bản mô tả JobClass, JobKey, dữ liệu và các thuộc tính của job.

**Câu 2.** `SimpleTriggerFactoryBean` và `CronTriggerFactoryBean` khác nhau ở đâu?  
**Đáp án:** SimpleTrigger dùng start-time + khoảng lặp/số lần lặp; CronTrigger dùng biểu thức lịch cron + timezone.

**Câu 3.** `repeatCount=0` có nghĩa không chạy lần nào không?  
**Đáp án:** Không. Nó nghĩa là không lặp thêm; SimpleTrigger dự kiến fire một lần nếu không bị cancel/misfire cắt bỏ.

**Câu 4.** Hai Trigger có thể dùng một JobDetail không?  
**Đáp án:** Có. Cả hai giữ cùng JobKey, nhưng có thể kích hoạt hai executions khác nhau.

**Câu 5.** Vì sao inject `@Qualifier("exportJobDetail") JobDetail` nhận được object dù @Bean trả `JobDetailFactoryBean`?  
**Đáp án:** Vì Spring `FactoryBean` expose **sản phẩm**. Prefix `&exportJobDetail` dùng khi muốn lấy chính FactoryBean.

**Câu 6.** `setDurability(true)` bảo đảm job sống qua restart không?  
**Đáp án:** Không; còn phụ thuộc JobStore. RAMJobStore vẫn mất toàn bộ state khi process kết thúc.

**Câu 7.** Quartz hỗ trợ những trigger nào ngoài Simple và Cron?  
**Đáp án:** Ví dụ `CalendarIntervalTrigger` và `DailyTimeIntervalTrigger`; có thể cấu hình bằng Quartz builders.

**Câu 8.** Muốn chạy 09:00 từ thứ Hai tới thứ Sáu theo Quartz, biểu thức nào phù hợp?  
**Đáp án:** Ví dụ `0 0 9 ? * MON-FRI`, với timezone được cấu hình riêng.

**Câu 9.** `@DisallowConcurrentExecution` ngăn concurrency theo Job class hay JobKey?  
**Đáp án:** Theo một JobDetail/JobKey, không khóa tất cả JobDetails cùng dùng class.

**Câu 10.** Misfire của Quartz là gì?  
**Đáp án:** Là việc trigger bỏ lỡ mốc scheduled fire theo ngưỡng/quy tắc JobStore, không đồng nghĩa business execution ném lỗi.

**Câu 11.** Nếu dùng JDBC JobStore, có nên lưu `ApplicationContext` trong JobDataMap không?  
**Đáp án:** Không. Dùng SchedulerContext hoặc custom JobFactory, còn JobDataMap nên giữ dữ liệu serializable phù hợp.

**Câu 12.** `SchedulerFactoryBean` có thể nhận trigger được tạo trực tiếp bằng Quartz builder không?  
**Đáp án:** Có. `setTriggers(Trigger...)` nhận Quartz Trigger interface, không chỉ sản phẩm của hai Spring FactoryBeans.

**Câu 13.** Khi đặt `CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW`, Quartz sẽ chạy bù hết mười kỳ thiếu không?  
**Đáp án:** Không. Nó yêu cầu một lần fire gần hiện tại, không phải replay mọi kỳ một cách bền vững.

**Câu 14.** Có cần `@EnableScheduling` cho Quartz SchedulerFactoryBean không?  
**Đáp án:** Không. `@EnableScheduling` bật method `@Scheduled` của Spring; Quartz được đăng ký qua SchedulerFactoryBean/Quartz APIs.

## 18. Tài liệu tham khảo chính thức

- [Spring Framework — JobDetailFactoryBean Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/quartz/JobDetailFactoryBean.html): `setJobClass`, `setDurability`, `setRequestsRecovery`, `setJobDataAsMap` và `FactoryBean<JobDetail>`.
- [Spring Framework 6.1.3 — SimpleTriggerFactoryBean Javadoc](https://www.springframework.org/spring/docs/6.1.3/javadoc-api/org/springframework/scheduling/quartz/SimpleTriggerFactoryBean.html): startDelay, repeatInterval, repeatCount, misfire và link JobDetail.
- [Spring Framework — CronTriggerFactoryBean Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/quartz/CronTriggerFactoryBean.html): cron expression, timezone, start-time, JobDataMap và misfire.
- [Spring Framework 6.1.3 — SchedulerFactoryBean Javadoc](https://www.springframework.org/spring/docs/6.1.3/javadoc-api/org/springframework/scheduling/quartz/SchedulerFactoryBean.html): setTriggers, setJobDetails, JobFactory, SchedulerContext, JDBC JobStore, lifecycle.
- [Spring Framework Reference — Task Execution and Scheduling / Quartz](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): Quartz JobDetail, SimpleTrigger, CronTrigger, SchedulerFactoryBean và scheduling integration.
- [Quartz 2.3.2 — JobDetail API](https://javadoc.io/static/org.quartz-scheduler/quartz/2.3.2/org/quartz/JobDetail.html): JobKey, durability, recovery và JobDataMap.
- [Quartz 2.3.2 — SimpleTrigger API](https://javadoc.io/static/org.quartz-scheduler/quartz/2.3.2/org/quartz/SimpleTrigger.html): repeatCount, interval và misfire constants.
- [Quartz 2.3.2 — CronTrigger API](https://javadoc.io/static/org.quartz-scheduler/quartz/2.3.2/org/quartz/CronTrigger.html): Quartz cron expression, timezone và misfire.
- [Quartz Scheduler — Tutorial 5: SimpleTrigger](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-05.html): một lần và repeating schedule, repeat count, các trường hợp end time.
- [Quartz Scheduler — Tutorial 6: CronTrigger](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html): Quartz cron grammar, dấu ?, L, W, # và ngày trong tuần.
- [Quartz 2.3.2 — DisallowConcurrentExecution](https://javadoc.io/static/org.quartz-scheduler/quartz/2.3.2/org/quartz/DisallowConcurrentExecution.html): ràng buộc concurrency theo JobDetail/JobKey.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="quartz-integration-005">Tiêm phụ thuộc vào job theo cơ chế phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm phụ thuộc vào Job theo cơ chế phù hợp (Spring Framework + Quartz)

## 1. Mục tiêu bài học

Khi một Quartz Job cần gọi service/repository do Spring quản lý, phải xác định **ai tạo Job** và **thông tin nào là dependency, thông tin nào là dữ liệu của lần chạy**. Sau bài này, bạn có thể:

- Giải thích vì sao chỉ thêm `@Autowired`, `@Component` hoặc constructor cho một `Job` chưa đủ nếu Quartz dùng `JobFactory` không tích hợp Spring.
- Sử dụng constructor injection cho service khi `JobFactory` tạo và khởi tạo Job qua Spring; sử dụng `JobDataMap` hoặc `JobExecutionContext` cho tham số của tác vụ.
- Phân biệt cơ chế mặc định của Spring Boot, cấu hình `SchedulerFactoryBean` thủ công, `QuartzJobBean` và cơ chế gọi trực tiếp phương thức của Spring bean.
- Nhận diện vấn đề với JDBC JobStore, vòng đời Job, xử lý lỗi, đồng thời và kiểm thử.

**Phạm vi:** Quartz chạy trong ứng dụng Spring/Spring Boot. Các ví dụ chính dùng Java 17+, Spring Boot 3.5.x và Quartz 2.x. Khi đổi phiên bản hoặc tự thay `JobFactory`, hãy kiểm tra cấu hình thực tế.

## 2. Ai tạo đối tượng Job?

Quartz quản lý lịch chạy qua ba đối tượng khác nhau:

| Thành phần | Trách nhiệm | Có nên chứa service Spring? |
| --- | --- | --- |
| `JobDetail` | Định nghĩa Job class, danh tính `JobKey` và `JobDataMap` | Không lưu trực tiếp service vào dữ liệu Job. |
| `Trigger` | Quy định thời điểm thực thi, có thể mang tham số riêng | Không dùng làm container DI. |
| `JobFactory` | Tạo một đối tượng `Job` cho mỗi lần kích hoạt | Đây là điểm tích hợp với Spring để tạo và inject Job. |
| `JobExecutionContext` | Cấp thông tin thực thi, `JobDataMap` hợp nhất và `Scheduler` | Dùng để đọc dữ liệu/metadata của lần chạy. |
| Spring `ApplicationContext` | Quản lý các service, repository và dependency khác | Nguồn cung cấp dependency cho Job khi factory hỗ trợ. |

Luồng khái niệm:

```text
Trigger phát hỏa
    |
    v
Quartz Scheduler --> JobFactory --> đối tượng Job MỚI cho lần chạy
                         |                   |
                         | (factory tích hợp | constructor nhận
                         |  Spring)          | service do Spring quản lý
                         v                   v
                ApplicationContext        execute(context)
                                             |
                   tham số công việc <-------+-- JobDataMap
                                             |
                                             v
                                        Service/Repository
```

Theo vòng đời Quartz thông thường, mỗi lần thực thi tạo một Job instance mới, còn `JobDetail` định nghĩa công việc và có thể được sử dụng qua nhiều lần chạy. Vì vậy, trường dữ liệu bên trong Job không thích hợp để lưu trạng thái bền vững giữa các lần chạy. Ngược lại, service là một Spring bean có thể được tái sử dụng; singleton service dùng chung giữa các Job cần an toàn khi chạy đồng thời.

**Tách hai loại đầu vào:**

- **Dependency**: `NotificationService`, `OrderRepository`, `Clock`, cấu hình do Spring cung cấp. Ưu tiên constructor injection với factory có khả năng autowire.
- **Execution data**: `recipient`, `orderId`, `tenantId`, `batchId`. Lưu kiểu dữ liệu đơn giản trong `JobDataMap` rồi đọc từ `context.getMergedJobDataMap()`. Không đưa `ApplicationContext`, DataSource, service, entity manager, kết nối hoặc object phức tạp vào `JobDataMap` của Job lưu JDBC.

## 3. Cách khuyến nghị: Spring Boot tạo Job qua SpringBeanJobFactory

Trong cấu hình auto-configuration tiêu chuẩn của Spring Boot 3.5, `spring-boot-starter-quartz` đăng ký một `SchedulerFactoryBean` sử dụng `SpringBeanJobFactory`, có gắn `ApplicationContext`. Phiên bản Spring Framework tương ứng có thể dùng Spring `AutowireCapableBeanFactory` để tạo Job, do đó constructor injection hoạt động. Điều kiện quan trọng là **không có cấu hình khác thay thế factory này bằng factory không biết Spring**. Không cần gắn `@Component` lên Job trong ví dụ dưới đây.

### 3.1. Tạo project mẫu

Cấu trúc tối thiểu:

```text
quartz-di-demo/
├── settings.gradle.kts
├── build.gradle.kts
└── src/main/java/example/quartz/
    ├── QuartzDiApplication.java
    ├── NotificationService.java
    ├── NotifyJob.java
    └── QuartzConfig.java
```

`settings.gradle.kts`:

```kotlin
rootProject.name = "quartz-di-demo"
```

`build.gradle.kts`:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-quartz")
}
```

Ví dụ này giả định bạn có Gradle (hoặc Gradle Wrapper) và JDK 17+; khi triển khai thật, dùng phiên bản Spring Boot tương thích với hệ thống đang vận hành.

### 3.2. Viết service do Spring quản lý

`src/main/java/example/quartz/NotificationService.java`:

```java
package example.quartz;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    public void send(String recipient) {
        // Minh họa nghiệp vụ; không gửi email thật.
        System.out.println("DEMO_SENT recipient=" + recipient);
    }
}
```

Spring tạo bean `NotificationService`; Job chỉ thực hiện điều phối đến service. Trong ứng dụng thật, service có thể gọi repository, client hoặc nghiệp vụ có transaction.

### 3.3. Viết Job sử dụng constructor injection

`src/main/java/example/quartz/NotifyJob.java`:

```java
package example.quartz;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class NotifyJob implements Job {

    private final NotificationService notificationService;

    public NotifyJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String recipient = context.getMergedJobDataMap()
                                  .getString("recipient");

        if (recipient == null || recipient.isBlank()) {
            throw new JobExecutionException("Missing job data: recipient");
        }

        notificationService.send(recipient);
    }
}
```

Điểm cần hiểu: `NotificationService` vào Job qua **constructor**; `recipient` vào Job qua **JobDataMap**. Đối tượng `NotifyJob` do `JobFactory` tạo khi Quartz kích hoạt công việc. Không gọi thủ công `new NotifyJob(...)` ở nơi đăng ký lịch và không lưu Job instance vào `JobDetail`.

### 3.4. Đăng ký JobDetail và Trigger

`src/main/java/example/quartz/QuartzConfig.java`:

```java
package example.quartz;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class QuartzConfig {

    @Bean
    public JobDetail notifyJobDetail() {
        return JobBuilder.newJob(NotifyJob.class)
                .withIdentity("notifyJob", "notification")
                .usingJobData("recipient", "alice@example.test")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger notifyTrigger(JobDetail notifyJobDetail) {
        return TriggerBuilder.newTrigger()
                .withIdentity("notifyTrigger", "notification")
                .forJob(notifyJobDetail)
                .startNow()
                .build();
    }
}
```

`JobBuilder.newJob(NotifyJob.class)` chỉ đưa **class** vào `JobDetail`. Trigger không định nghĩa một service mới. `startNow()` và lịch mặc định ở ví dụ tạo một lần kích hoạt, đủ để quan sát DI; muốn chạy định kỳ thì chọn `SimpleScheduleBuilder` hoặc `CronScheduleBuilder` theo yêu cầu khác.

`src/main/java/example/quartz/QuartzDiApplication.java`:

```java
package example.quartz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QuartzDiApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuartzDiApplication.class, args);
    }
}
```

Chạy từ thư mục project:

```bash
./gradlew bootRun
```

Trên Windows, dùng `gradlew.bat bootRun` nếu project đã có Gradle Wrapper; hoặc `gradle bootRun` khi dùng bản Gradle đã cài. Sau khi scheduler khởi động và trigger chạy, output từ **service mẫu** là:

```text
DEMO_SENT recipient=alice@example.test
```

Spring Boot/Quartz còn in log khởi động và thực thi, nên dòng trên không phải toàn bộ stdout. Với trigger một lần, dự kiến xuất hiện một lần mỗi lần khởi tạo mới trên JobStore memory. Nếu đổi sang JDBC JobStore, định nghĩa và trạng thái Trigger có thể được lưu nên không được giả định sẽ kích hoạt lại mỗi lần ứng dụng khởi động.

### 3.5. Giải thích từng bước

1. Spring Boot quét `@Service` và tạo `NotificationService`.
2. Spring đăng ký các bean `JobDetail`, `Trigger`; auto-configuration chuyển chúng cho `SchedulerFactoryBean`.
3. Quartz phát hiện trigger đến hạn và gọi `JobFactory` tạo `NotifyJob`.
4. Với `SpringBeanJobFactory` được gắn đúng `ApplicationContext`, Spring phân giải `NotificationService` để gọi constructor.
5. Quartz gọi `execute(context)`; Job lấy `recipient` từ `JobDataMap` và gọi service.

Nếu Job báo `NullPointerException` ở dependency hoặc Quartz báo không tạo được Job có constructor tham số, cần kiểm tra **JobFactory đang được cài vào Scheduler**, thay vì thêm `@Component` một cách ngẫu nhiên.

## 4. Nếu tự cấu hình SchedulerFactoryBean: gắn JobFactory có Spring

Khi dùng Spring Framework thủ công hoặc tự định nghĩa `SchedulerFactoryBean`, bạn phải quyết định rõ factory nào sẽ tạo Job. Cấu hình dưới đây là **phương án thay thế** cho auto-configuration ở mục 3, không phải class cần thêm đồng thời vào project mẫu:

```java
package example.quartz;

import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration(proxyBeanMethods = false)
public class ManualSchedulerConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(
            ApplicationContext applicationContext,
            JobDetail notifyJobDetail,
            Trigger notifyTrigger) {

        SpringBeanJobFactory jobFactory = new SpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setJobDetails(notifyJobDetail);
        factory.setTriggers(notifyTrigger);
        return factory;
    }
}
```

Đoạn này sử dụng lại `NotificationService`, `NotifyJob`, `JobDetail` và `Trigger` ở mục 3 nếu đặt trong một project **không còn cấu hình scheduler trùng lặp**. Khi tự cấp `SchedulerFactoryBean` trong Spring Boot, Boot thường nhường quyền cấu hình scheduler cho bean của bạn: bạn phải tự xem lại JobStore, DataSource, transaction manager, scheduler lifecycle, Quartz properties và các JobDetail/Trigger cần đăng ký.

**Phân biệt quan trọng:** `SpringBeanJobFactory` có khả năng ánh xạ các thuộc tính từ SchedulerContext/JobDataMap vào setter, nhưng đó **không đồng nghĩa** với việc một `JobFactory` bất kỳ tự động autowire service. Constructor injection cần cơ chế Spring tạo Job được kết nối chính xác (như `SpringBeanJobFactory` gắn `ApplicationContext` ở phiên bản tương ứng). Nếu dự án dùng factory tùy biến, hãy kiểm tra cách nó triển khai `newJob(...)`/`createJobInstance(...)` và còn giữ ánh xạ JobDataMap hay không.

## 5. Khi nào dùng QuartzJobBean và setter?

`QuartzJobBean` là lớp tiện ích Spring cung cấp để viết `executeInternal` và nhận các **giá trị cấu hình Job** qua setter trùng tên khóa dữ liệu. Ví dụ độc lập về mẫu thiết kế (giả sử đã có Spring bean `ReportService`):

```java
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

public class ReportJob extends QuartzJobBean {

    private final ReportService reportService;
    private String reportId;

    public ReportJob(ReportService reportService) {
        this.reportService = reportService;
    }

    // Được map từ JobDataMap: "reportId" (không phải autowire service).
    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    @Override
    protected void executeInternal(JobExecutionContext context)
            throws JobExecutionException {

        if (reportId == null || reportId.isBlank()) {
            throw new JobExecutionException("Missing reportId");
        }
        reportService.generate(reportId);
    }
}
```

Đây là đoạn minh họa theo kiểu `QuartzJobBean`, **không phải file Java chạy độc lập** vì cần định nghĩa `ReportService` và đăng ký JobDetail/Trigger với `usingJobData("reportId", "...")`. Constructor `ReportService` vẫn chỉ được inject khi JobFactory tích hợp Spring. Ngược lại, setter `setReportId` nhận giá trị từ job data, được xử lý bởi `QuartzJobBean` trong quá trình thực thi.

Nếu cần setter injection **service** qua Spring, phải sử dụng cơ chế DI thật, chẳng hạn setter có `@Autowired` khi Spring tạo Job. Một setter thông thường tên `setReportService` **không tự nhiên nhận bean** nếu không có cấu hình/annotation phù hợp; đặt tên khóa trong `JobDataMap` giống tên bean cũng không phải thiết kế DI an toàn.

## 6. Phương án khác: gọi trực tiếp method của Spring bean

Với tác vụ rất đơn giản, `MethodInvokingJobDetailFactoryBean` có thể biến phương thức của bean đang được Spring quản lý thành một JobDetail, tránh viết Job chỉ có một dòng gọi service. Ví dụ **thay thế** và **chưa phải ứng dụng đầy đủ**:

```java
@Bean
public MethodInvokingJobDetailFactoryBean cleanupDetail(
        CleanupService cleanupService) {

    MethodInvokingJobDetailFactoryBean detail =
            new MethodInvokingJobDetailFactoryBean();
    detail.setTargetObject(cleanupService);
    detail.setTargetMethod("cleanup");
    detail.setConcurrent(false);
    return detail;
}
```

Trong đó `CleanupService` là một Spring bean có phương thức `public void cleanup()`, và bạn vẫn cần Trigger thích hợp. `setConcurrent(false)` yêu cầu không chạy đồng thời các lần gọi theo cơ chế của factory.

**Giới hạn bắt buộc:** JobDetail do `MethodInvokingJobDetailFactoryBean` tạo không serializable nên **không phù hợp với persistent JobStore (JDBC)**. Nếu lịch chạy cần lưu xuống DB, dùng Job class mỏng gọi service như mục 3.

| Tình huống | Cơ chế phù hợp |
| --- | --- |
| Spring Boot 3.5 auto-configuration chuẩn, Job cần service | `Job` + constructor injection qua `SpringBeanJobFactory` tích hợp Spring. |
| Tự tạo `SchedulerFactoryBean` | Cài `SpringBeanJobFactory` có `ApplicationContext` hoặc factory riêng có hợp đồng DI rõ ràng. |
| Job cần tham số thay đổi theo JobDetail/Trigger | `JobDataMap` và `getMergedJobDataMap()`; với `QuartzJobBean` có thể dùng setter. |
| Chỉ gọi một method của Spring bean, JobStore memory | `MethodInvokingJobDetailFactoryBean` có thể đủ. |
| JDBC JobStore / cluster | Job class có dữ liệu đơn giản, tham chiếu service qua DI khi thực thi; tránh object Spring trong `JobDataMap`. |

## 7. Sai lầm thường gặp và cách chẩn đoán

### 7.1. Gắn @Component và nghĩ Quartz dùng bean đó

```java
@Component
class WrongAssumptionJob implements Job {
    @Autowired
    private NotificationService service;

    public void execute(JobExecutionContext context) {
        service.send("someone@example.test");
    }
}
```

**Vấn đề:** `@Component` làm Spring đăng ký một bean, nhưng Quartz có thể tự tạo một đối tượng **khác** bằng JobFactory của nó. Nếu đối tượng đó không qua Spring DI, trường `service` vẫn `null`. Với Boot 3.5 auto-config chuẩn, factory đã hỗ trợ Spring nên injection có thể hoạt động, song `@Component` trên Job không phải yếu tố đảm bảo và thường tạo thêm bean không cần thiết. Để rõ ownership, dùng class Job thông thường + constructor injection + factory đúng.

### 7.2. Nhét service hoặc ApplicationContext vào JobDataMap

```java
// Tránh khi dùng JDBC JobStore:
JobBuilder.newJob(NotifyJob.class)
    .usingJobData("serviceName", "notificationService")
    .build();
```

Đoạn trên thực ra chỉ lưu **tên dưới dạng String**, không tự tiêm service; đừng lẫn String này với bean reference. Càng không nên đưa trực tiếp Spring bean, context hoặc kết nối vào `JobDataMap`: việc serialize/persist và chạy trên node khác có thể thất bại hoặc sai vòng đời. Hãy lưu business ID/cấu hình dạng chuỗi và lấy service qua DI.

### 7.3. Job không có constructor rỗng

Thông báo kiểu `NoSuchMethodException`/`SchedulerException: Problem instantiating class` thường xuất hiện nếu factory đang gọi constructor không tham số nhưng Job chỉ có constructor nhận service. **Đừng giải quyết bằng cách bỏ DI rồi lấy bean tĩnh.** Kiểm tra factory nào đã được gắn qua `SchedulerFactoryBean.setJobFactory(...)`. Với factory tích hợp Spring đúng phiên bản, constructor injection có thể dùng bình thường.

### 7.4. Lẫn setter dữ liệu với setter bean

`public void setCustomerId(String id)` có thể nhận giá trị từ `JobDataMap`. Trong khi đó, `public void setOrderService(OrderService service)` không tự tạo service dependency nếu không có `@Autowired` hoặc cấu hình tương ứng. Khi dùng cả hai, ghi rõ nguồn và vòng đời của từng thuộc tính.

### 7.5. Lưu state mutable trong Job hoặc service

Mỗi lần Quartz thực thi thường có một Job object mới; đừng kỳ vọng bộ đếm instance field được giữ qua các lần chạy. Một Spring singleton service lại có thể phục vụ nhiều Job cùng lúc. Bảo đảm thread safety/idempotency trong service, và lưu checkpoint hoặc trạng thái cần bền vững vào kho dữ liệu phù hợp.

Khi muốn ngăn **cùng một JobDetail** chạy chồng lên nhau, có thể gắn `@DisallowConcurrentExecution` lên Job class. Giới hạn áp dụng theo `JobKey`, không chặn mọi JobDetail khác cùng dùng class đó. Annotation này cũng không thay thế giao dịch, khóa phân tán hoặc thiết kế idempotent nếu có xử lý phân tán.

### 7.6. Giấu lỗi trong execute()

Không nuốt exception rồi coi lần chạy là thành công. Ghi log có `JobKey`, dữ liệu ID cần thiết và nguyên nhân; có thể bọc lỗi nghiệp vụ vào `JobExecutionException` nếu cần điều khiển hành vi retry/refire theo chính sách Quartz. Không bật tự refire tùy tiện khi side effect chưa idempotent.

## 8. Checklist áp dụng và kiểm thử

1. **Xác định nguồn tạo Job:** Spring Boot có tự tạo `SchedulerFactoryBean` không? Có bean custom nào thay thế `JobFactory` không? Phiên bản Framework/Boot dùng cơ chế tạo Job nào?
2. **Kiểm tra DI:** đặt breakpoint ở constructor `NotifyJob(NotificationService)` hoặc log dependency không null. Nếu service thiếu, kiểm tra component scan, loại bean, `@Qualifier` và scope.
3. **Kiểm tra data:** `JobDetail`/`Trigger` có đúng khóa `recipient` không? `getMergedJobDataMap()` ưu tiên giá trị ở Trigger khi trùng khóa với JobDetail.
4. **Chạy smoke test:** chạy project mẫu; xác nhận log có `DEMO_SENT recipient=alice@example.test` một lần với lịch `startNow()`/JobStore memory.
5. **Kiểm thử nghiệp vụ:** mock hoặc fake `NotificationService` ở lớp test để xác minh Job đọc đúng `recipient`; test thiếu khóa, dữ liệu không hợp lệ và exception của service.
6. **Kiểm tra production:** dùng mã định danh đơn giản trong `JobDataMap`, xác nhận behavior khi restart/JDBC, giới hạn chạy đồng thời theo `JobKey`, retry và idempotency. Đặt logic giao dịch ở service/business boundary khi phù hợp.

## 9. Câu hỏi tự kiểm tra

- Vì sao `@Component` trên một class `Job` không tự đảm bảo Quartz dùng đúng object do Spring tạo?
- Constructor injection cho `Job` sẽ lỗi khi dùng factory nào? Cần cấu hình gì để khắc phục?
- `JobDataMap` và Spring DI khác nhau ở mục đích, thời điểm cung cấp dữ liệu và tính bền vững như thế nào?
- Nếu chuyển từ JobStore memory sang JDBC, vì sao một `MethodInvokingJobDetailFactoryBean` hoặc Spring bean trong `JobDataMap` có thể gây lỗi?
- `@DisallowConcurrentExecution` bảo vệ phạm vi nào? Tại sao service vẫn phải thread-safe?

## 10. Tài liệu tham khảo

- [Spring Boot 3.5 – Quartz Scheduler](https://docs.spring.io/spring-boot/3.5/reference/io/quartz.html): auto-configuration `SchedulerFactoryBean`, `JobDetail`/`Trigger` và cấu hình Quartz.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): tích hợp Quartz, `QuartzJobBean` và method-invoking Job.
- [SpringBeanJobFactory API (Spring Framework 6.2)](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/quartz/SpringBeanJobFactory.html): ánh xạ SchedulerContext/JobDataMap và cơ chế tạo Job.
- [Quartz Tutorial 3](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/tutorial-lesson-03.html): vòng đời Job, JobDataMap và xử lý đồng thời.
- [MethodInvokingJobDetailFactoryBean API](https://docs.spring.io/spring-framework/docs/6.2.2/javadoc-api/org/springframework/scheduling/quartz/MethodInvokingJobDetailFactoryBean.html): giới hạn khi dùng persistent JobStore.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="quartz-integration-006">Phân biệt CronTrigger của Spring với CronTrigger của Quartz</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `CronTrigger` của Spring với `CronTrigger` của Quartz

Hai API cùng tên `CronTrigger` và đều tính lịch bằng biểu thức cron, nhưng thuộc **hai cơ chế lập lịch khác nhau**. Trong bài “Tích hợp Quartz”, cần xác định chính xác kiểu trigger trước khi chọn API: Spring Scheduling làm việc với `Runnable`/`TaskScheduler`; Quartz làm việc với `Job`/`JobDetail`/`Scheduler`.

## 1. Nhận diện đúng hai lớp/API

| Tiêu chí | Spring Framework | Quartz Scheduler |
|---|---|---|
| Tên đầy đủ | `org.springframework.scheduling.support.CronTrigger` | `org.quartz.CronTrigger` |
| Kiểu Java | **Class** triển khai `org.springframework.scheduling.Trigger` | **Interface** kế thừa `org.quartz.Trigger`; implementation phổ biến là `CronTriggerImpl` |
| Lịch cho đối tượng | `Runnable` hoặc method của Spring bean | `JobDetail` mô tả một Quartz `Job` |
| Bộ máy chạy | `TaskScheduler`, thường dùng `ThreadPoolTaskScheduler` | `org.quartz.Scheduler` và `JobStore` |
| API điển hình | `taskScheduler.schedule(task, trigger)`; `@Scheduled(cron = "...")` | `TriggerBuilder`, `CronScheduleBuilder`, `scheduler.scheduleJob(...)` |
| Dependency gốc | `spring-context` | `org.quartz-scheduler:quartz` |

Một tên dễ gây nhầm nữa là `org.springframework.scheduling.quartz.CronTriggerFactoryBean`. Tuy ở package Spring, factory này **tạo trigger của Quartz** (`org.quartz.CronTrigger`), để đăng ký vào Quartz `Scheduler` qua tích hợp Spring. Nó **không tạo** `org.springframework.scheduling.support.CronTrigger`.

Không thể hoán đổi đối tượng vì hai interface trigger và hai scheduler khác nhau. Nếu trong một file Java cần cả hai, dùng tên đầy đủ cho ít nhất một kiểu thay vì import nhầm:

```java
import org.springframework.scheduling.support.CronTrigger;

// Trigger của Quartz cần tên đầy đủ nếu cùng scope:
// org.quartz.CronTrigger quartzTrigger = ...;
```

## 2. Cách hoạt động thực tế

### Spring `CronTrigger`

1. `TaskScheduler` nhận `Runnable` và Spring `Trigger`.
2. `CronTrigger.nextExecution(TriggerContext)` tính `Instant` lần chạy kế tiếp từ biểu thức cron, múi giờ và các mốc thực thi trước đó.
3. Scheduler đặt task lên executor và tiếp tục tính lịch sau mỗi lần chạy.
4. Lịch gắn với lifecycle của ứng dụng/scheduler đang chạy. Bản thân `CronTrigger` không lưu JobDetail, không có JDBC JobStore và không điều phối nhiều node.

`@Scheduled(cron = "...")` là cú pháp tiện lợi của **Spring Scheduling**, thường đi cùng `@EnableScheduling`. Chỉ thêm dependency Quartz vào Spring Boot **không chuyển `@Scheduled` thành Quartz job**.

### Quartz `CronTrigger`

1. `Job` chứa logic ở method `execute(JobExecutionContext)`.
2. `JobDetail` định danh job bằng `JobKey` và giữ metadata/job data.
3. `CronTrigger` có `TriggerKey`, liên kết `JobDetail`, chứa cron, time zone, start/end time và chính sách xử lý misfire.
4. `Scheduler` đăng ký trigger, theo dõi next-fire-time thông qua `JobStore`, giao job tới worker thread khi đến hạn.

Quartz hỗ trợ `RAMJobStore` (lịch mất sau khi tiến trình kết thúc) và `JDBCJobStore` (lưu job/trigger trong database). **Persistence, clustering và failover phải được cấu hình**; chúng không tự xuất hiện chỉ vì dùng Quartz `CronTrigger`. Trong cluster JDBC, các node phối hợp giành quyền kích hoạt trigger qua database chung. Cần thiết kế logic nghiệp vụ idempotent để phòng chạy lại khi lỗi/recovery, không mặc định “exactly once” cho side effect.

## 3. So sánh theo nhu cầu thiết kế

| Tiêu chí | Spring `CronTrigger` + `TaskScheduler` | Quartz `CronTrigger` + `Scheduler` |
|---|---|---|
| Độ phức tạp | Đơn giản, phù hợp tác vụ nội bộ app | Nhiều thực thể/API nhưng quản lý job chuyên sâu |
| Đăng ký | `Runnable` và `Trigger`, hoặc `@Scheduled` | `Job` + `JobDetail` + `Trigger` |
| Định danh/lifecycle | Có `ScheduledFuture` để cancel; quản lý theo app | `JobKey`/`TriggerKey`, pause/resume/reschedule và listeners |
| Sau khi restart | Phải đăng ký lại; không tự khôi phục lịch đã bắn | Có thể khôi phục trigger nếu dùng persistent JobStore |
| Nhiều instance | Mỗi instance có lịch riêng và có thể cùng chạy | Có thể phối hợp firing bằng Quartz JDBC clustering |
| Lỡ lịch | Lenient/fixed execution (Spring 6.1.3+) | Misfire instructions riêng của Quartz |
| Chạy đồng thời | Phụ thuộc scheduler/executor và cách đăng ký | Có thể overlap; `@DisallowConcurrentExecution` ngăn cùng `JobKey` chạy đồng thời |
| Time zone | `CronTrigger(expr, ZoneId)` hoặc clock của scheduler | `CronScheduleBuilder.inTimeZone(TimeZone)` |
| Lịch loại trừ/ngày đặc biệt | Chủ yếu tùy biến scheduler/trigger | Quartz Calendars và các tùy chọn trigger |
| Khi dùng Spring Boot | Scheduling qua `@Scheduled`/`TaskScheduler` | `spring-boot-starter-quartz`/`SchedulerFactoryBean` |

## 4. Cron expression: giống sáu trường đầu, khác ngữ nghĩa chi tiết

Thứ tự trường:

```text
giây  phút  giờ  ngày-trong-tháng  tháng  ngày-trong-tuần  [năm]
```

- **Spring** yêu cầu **đúng 6 trường**. Parser Spring hỗ trợ `?`, các toán tử thường gặp (`*`, `,`, `-`, `/`, `L`, `W`, `#`), tên ngày/tháng, cùng macro như `@daily`/`@hourly` ở API Spring.
- **Quartz** yêu cầu **6 trường**, có **năm thứ 7 tùy chọn**. Nó hỗ trợ các ký hiệu cron phong phú và các tùy chọn lịch riêng.
- **Ngày trong tuần bằng số khác nhau:** Spring: `0` hoặc `7` = Chủ nhật, `1` = Thứ hai, `2` = Thứ ba, ... `6` = Thứ bảy. Quartz: `1` = Chủ nhật, `2` = Thứ hai, ... `7` = Thứ bảy.
- **Dấu `?`:** ở Spring có thể dùng thay `*` trong hai trường ngày; ở Quartz mang nghĩa không chỉ định, thường đặt ở trường ngày còn lại khi một trường đã được chọn cụ thể. Quartz không hỗ trợ đầy đủ việc đồng thời ràng buộc cả day-of-month và day-of-week.
- **`W`/`L`/`#`:** nhiều tính năng như ngày làm việc gần ngày N, ngày cuối tháng, thứ N trong tháng có ở cả hai parser hiện đại. Khi chuyển lịch vẫn phải test trên đúng phiên bản/API đích.
- **Time zone:** cùng chuỗi cron có thể phát vào `Instant` khác nếu zone khác. Với zone có DST, một giờ địa phương có thể bị bỏ qua hoặc lặp lại.

| Biểu thức | Spring | Quartz |
|---|---|---|
| `0 0/5 * * * ?` | Mỗi 5 phút | Mỗi 5 phút |
| `0 0 9 ? * MON-FRI` | 09:00 thứ hai đến thứ sáu | Tương tự |
| `0 0 9 ? * 2` | **Thứ ba** 09:00 | **Thứ hai** 09:00 |
| `0 0 9 ? * MON 2027` | **Sai**: có 7 trường | Thứ hai 09:00 trong năm 2027 |
| `@daily` | Macro Spring: 00:00 mỗi ngày | Không phải cron Quartz hợp lệ |

**Quy tắc khi migrate:** đổi số ngày sang tên `MON`/`TUE`/..., xác định rõ zone, kiểm tra số trường, chạy thử nhiều next-fire-time bằng parser thật của hệ thống đích. Không giả định hai cron DSL tương đương 100%.

## 5. Chạy lâu và lỡ lịch: `lenient/fixed` khác Quartz `misfire`

Giả sử lịch **mỗi phút** (`0 * * * * *`). Một lần bắt đầu 10:00 và chỉ hoàn thành lúc 10:02:20.

### Spring 6.1.3 trở lên

- `new CronTrigger(expr)` tương đương `CronTrigger.forLenientExecution(expr)`: lấy **lần hoàn tất trước** làm mốc tính lịch. Trong scheduler thread-pool thông thường, mốc 10:01 và 10:02 có thể bị **bỏ qua**; lần sau là 10:03. Tác động thực tế có phụ thuộc scheduler và cơ chế giao task cho thread.
- `CronTrigger.forFixedExecution(expr)`: tính từ **lần được lên lịch trước đó** và phát bù những mốc chưa kịp chạy (có thể chạy trễ). Chế độ này **không tự lưu lịch qua restart**.
- `resumeLenientExecution(expr, instant)` và `resumeFixedExecution(expr, instant)` cho phép tái tạo trigger từ mốc mà **ứng dụng tự lưu và cung cấp**.

```java
import org.springframework.scheduling.support.CronTrigger;

String expression = "0 * * * * *";
CronTrigger lenient = CronTrigger.forLenientExecution(expression);
CronTrigger fixed = CronTrigger.forFixedExecution(expression);
```

Đây là đoạn minh họa API Spring; cần `TaskScheduler.schedule(Runnable, Trigger)` để thực sự lập lịch. **Không đồng nhất “fixed” ở Spring với Quartz misfire “fire now”**.

### Quartz

Misfire xảy ra khi một firing quá hạn so với ngưỡng cho phép mà Quartz dùng (ví dụ scheduler dừng, thread pool bận, hoặc JobStore không giao trigger kịp). Quartz `CronTrigger` có các chính sách:

- `withMisfireHandlingInstructionDoNothing()`: bỏ những mốc đã lỡ, nhảy tới mốc hợp lệ tiếp theo.
- `withMisfireHandlingInstructionFireAndProceed()`: phát một lần ngay khi xử lý misfire, rồi theo lịch tiếp theo.
- `withMisfireHandlingInstructionIgnoreMisfires()`: bỏ qua cách xử lý misfire thông thường; có thể gây tải do firing dồn.
- `SMART_POLICY` mặc định với Quartz `CronTrigger` được diễn giải là **fire once now**.

```java
import org.quartz.CronScheduleBuilder;

CronScheduleBuilder schedule = CronScheduleBuilder
    .cronSchedule("0 * * * * ?")
    .withMisfireHandlingInstructionDoNothing();
```

Đoạn mã xây dựng lịch Quartz; nó chưa đăng ký một trigger vào `Scheduler`. Quartz job có thể chạy đồng thời nếu có nhiều firing, trừ khi cấu hình/annotation hạn chế phù hợp. `@DisallowConcurrentExecution` áp dụng theo cùng `JobKey`, không khóa toàn bộ mọi job class trên các JobKey khác nhau.

## 6. Ví dụ Spring: `Runnable` với `TaskScheduler`

Đoạn dưới là một class cấu hình **trong ứng dụng Spring Boot** có Spring Scheduling, dùng Java 17+ và Spring Framework 6.x/7.x. `ApplicationRunner` đăng ký một `Runnable` khi ứng dụng đã khởi tạo. Vòng đời của `ThreadPoolTaskScheduler` được Spring quản lý.

```java
package demo;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

@Configuration
public class SpringCronConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("spring-cron-");
        return scheduler;
    }

    @Bean
    public ApplicationRunner registerCron(TaskScheduler taskScheduler) {
        return args -> taskScheduler.schedule(
            () -> System.out.println("Spring: " + Instant.now()),
            new CronTrigger(
                "0 0/5 * * * *",
                ZoneId.of("Asia/Ho_Chi_Minh")
            )
        );
    }
}
```

Lịch chạy vào giây 00 của các phút chia hết cho 5 theo giờ Việt Nam; giá trị log phụ thuộc thời điểm nên không có stdout cố định. `TaskScheduler.schedule` trả về `ScheduledFuture<?>`, có thể gọi `cancel(false)` khi cần dừng. Nếu muốn viết ngắn, dùng `@EnableScheduling` và một method bean đánh dấu:

```java
@Scheduled(cron = "0 0/5 * * * *", zone = "Asia/Ho_Chi_Minh")
public void doWork() {
    // Công việc trong Spring bean.
}
```

Đây là **đoạn mã phương thức**, không phải class Java chạy độc lập; cần import `org.springframework.scheduling.annotation.Scheduled` và cấu hình scheduling. Nó vẫn dùng Spring Scheduling, không phải Quartz.

## 7. Ví dụ Quartz: `JobDetail` + `CronTrigger` + `Scheduler`

Ví dụ độc lập **Java 17, Quartz 2.5.2**, chạy thử cron mỗi hai giây trong khoảng sáu giây. Tạo `src/main/java/demo/QuartzCronDemo.java`:

```java
package demo;

import java.util.TimeZone;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public final class QuartzCronDemo {

    public static class ReportJob implements Job {
        @Override
        public void execute(JobExecutionContext context)
                throws JobExecutionException {
            System.out.println("Quartz: " + context.getFireTime());
        }
    }

    public static void main(String[] args) throws Exception {
        JobDetail job = JobBuilder.newJob(ReportJob.class)
            .withIdentity("reportJob", "demo")
            .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("reportCron", "demo")
            .forJob(job)
            .withSchedule(CronScheduleBuilder
                .cronSchedule("0/2 * * * * ?")
                .inTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
                .withMisfireHandlingInstructionDoNothing())
            .build();

        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.scheduleJob(job, trigger);
        try {
            scheduler.start();
            Thread.sleep(6000);
        } finally {
            scheduler.shutdown(true);
        }
    }
}
```

Maven `pom.xml` để biên dịch/chạy ví dụ trên (cần cài JDK 17 và Maven):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>quartz-cron-demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.quartz-scheduler</groupId>
      <artifactId>quartz</artifactId>
      <version>2.5.2</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.1</version>
      </plugin>
    </plugins>
  </build>
</project>
```

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.QuartzCronDemo
```

Kết quả có khoảng 2–3 dòng `Quartz: <thời gian firing>`; số lượng chính xác phụ thuộc thời điểm bắt đầu, lịch thread và shutdown. Ví dụ dùng JobStore mặc định, **không thể hiện JDBC persistence/cluster**. `scheduler.shutdown(true)` đợi các job đang chạy kết thúc. Trong ứng dụng Spring Boot có `spring-boot-starter-quartz`, có thể đăng ký `JobDetail` và `CronTrigger` thông qua Spring, nhưng kiểu trigger vẫn là Quartz.

## 8. Chọn công cụ phù hợp

**Dùng Spring `CronTrigger` hoặc `@Scheduled`** khi job đơn giản, cục bộ ứng dụng, tự đăng ký lại mỗi lần start; công việc gắn với bean/service; không cần lưu trigger bền vững hoặc điều phối nhiều instance. Nếu một cluster ứng dụng không được phép chạy trùng, phải dùng khóa/phân công bên ngoài.

**Dùng Quartz `CronTrigger`** khi cần định danh job/trigger rõ ràng, thêm/xóa/đổi lịch động, pause/resume, listeners, calendars, misfire policy; đặc biệt khi yêu cầu persistence và clustering với JDBC JobStore cấu hình đúng.

**Dùng Quartz bên trong Spring** khi vừa cần dependency injection và cấu hình Spring, vừa muốn engine Quartz. Các bean như `SchedulerFactoryBean`, `JobDetailFactoryBean`, `CronTriggerFactoryBean` là **cầu nối với Quartz**, không phải cơ chế Spring `TaskScheduler`.

## 9. Lỗi thường gặp và checklist thực hành

1. **Import nhầm package:** `TaskScheduler` yêu cầu Spring `Trigger`; Quartz `Scheduler` yêu cầu Quartz `Trigger`. Dùng full qualified class khi cần.
2. **Nghĩ `@Scheduled` là Quartz:** không đúng; phải đăng ký Quartz `JobDetail`/`Trigger` nếu muốn engine Quartz.
3. **Chuyển cron dùng weekday dạng số:** `2` nghĩa Thứ ba ở Spring nhưng Thứ hai ở Quartz. Dùng `MON`, `TUE`, ... để giảm lỗi.
4. **Dùng cron Quartz có năm thứ 7 cho Spring:** Spring 6-field parser từ chối; cần chuyển đổi.
5. **Trộn khái niệm missed execution/misfire:** kiểm tra `forLenientExecution`/`forFixedExecution` (Spring) hoặc `misfireThreshold`/misfire instruction (Quartz) trên môi trường thực.
6. **Cho rằng Quartz tự lưu lịch:** `RAMJobStore` mất job/trigger khi restart. Persistence yêu cầu JDBCJobStore và database schema/cấu hình.
7. **Cho rằng Quartz tự chống overlap:** dùng `@DisallowConcurrentExecution` nếu cùng `JobKey` không được chạy đồng thời; cần idempotency khi chạy lại vì lỗi.
8. **Nhiều instance:** Spring có thể mỗi instance cùng chạy một lịch; Quartz phối hợp trigger nếu JDBC cluster được thiết lập chuẩn; không tự suy ra exactly-once cho nghiệp vụ.
9. **Timezone/DST:** khai báo zone và test ngày đổi giờ ở nơi có DST; phải phân biệt local time và `Instant`.
10. **Thiếu kiểm thử lifecycle:** thử job chạy lâu, scheduler pause/restart, thread pool bận, thay cron khi chạy, và số next-fire-time thực tế.

Bốn câu hỏi tự kiểm tra: (1) Trigger đến từ package nào? (2) Công việc là `Runnable` hay Quartz `Job`? (3) Lịch cần tồn tại qua restart và nhiều instance không? (4) Khi lỡ giờ, phải bỏ qua, chạy một lần hay bù đủ các mốc? Từ đó quyết định đúng engine và chính sách.

## Tài liệu gốc

- Spring Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring `CronTrigger` Javadoc: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronTrigger.html
- Quartz CronTrigger tutorial: https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/crontrigger.html
- Quartz Misfire Instructions: https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html
- Quartz JobStores: https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/tutorial-lesson-09.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="quartz-integration-007">Misfire và chính sách chạy bù cần quyết định tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Misfire và chính sách chạy bù cần quyết định tường minh

> **Bối cảnh:** Spring Framework tích hợp Quartz. Một lịch chạy đúng giờ trên giấy không đảm bảo `Job` được thực thi đúng thời điểm ngoài thực tế. Khi Scheduler bị dừng, máy chủ mất kết nối, worker hết luồng hoặc Job trước chạy quá lâu, cần quyết định rõ: bỏ lượt đã lỡ, chạy bù một lần hay xử lý đủ từng kỳ nghiệp vụ bị bỏ lỡ.

## 1. Học xong cần nắm được gì?

- Giải thích `misfire` trong Quartz, phân biệt thời điểm dự kiến chạy với thời điểm thực tế chạy.
- Lựa chọn có chủ đích các `misfire instruction` của `CronTrigger` và `SimpleTrigger`.
- Cấu hình chính sách ngay trên `Trigger` trong ứng dụng Spring Boot dùng Quartz, thay vì dựa vào mặc định `SMART_POLICY`.
- Hiểu rằng **“chạy một lần ngay bây giờ” khác hoàn toàn với “chạy bù đủ mọi kỳ đã bỏ lỡ”**.
- Thiết kế chạy bù có checkpoint, giới hạn tải, chống xử lý trùng và kiểm chứng được sau sự cố.

## 2. Misfire là gì và không phải là gì?

Quartz quản lý `JobDetail` (công việc cần thực hiện), `Trigger` (lịch kích hoạt) và `Scheduler` (bộ điều phối). `Trigger` giữ thời điểm kích hoạt kế tiếp, thường quan sát qua `getNextFireTime()`. Nếu thời điểm đó đã đi qua nhưng Scheduler không thể xử lý theo lịch, Quartz có thể xác định trigger bị **misfire** và áp dụng chính sách mà trigger khai báo.

Các nguyên nhân thường gặp:

1. Ứng dụng/Scheduler tắt hoặc tạm dừng qua giờ hẹn; với `JDBCJobStore`, lịch vẫn tồn tại để được đánh giá khi khởi động lại.
2. Quartz không có worker thread rảnh, hệ thống bị nghẽn CPU/DB hoặc công việc chạy lâu.
3. Scheduler phục hồi sau sự cố, lượng trigger quá hạn dồn lại.

`org.quartz.jobStore.misfireThreshold` là ngưỡng độ trễ tính bằng **mili giây** mà JobStore chấp nhận trước khi xem một thời điểm kích hoạt là misfire; giá trị mặc định của Quartz là **60.000 ms (60 giây)**. Ví dụ lịch 09:00, hệ thống mới có thể xử lý lúc 09:00:20 thì chưa chắc bị đưa qua nhánh misfire với ngưỡng mặc định; trễ đến 09:02 có thể bị xem là misfire. Đây là ngưỡng phát hiện, **không phải số lượt được chạy bù, thời gian timeout của Job hay lời hứa về độ chính xác của Scheduler**.

Cần tách ba khái niệm:

| Hiện tượng | Cách hiểu | Cơ chế xử lý chính |
| --- | --- | --- |
| `Misfire` | Thời điểm trigger lẽ ra kích hoạt đã quá hạn | `misfire instruction` gắn với từng trigger |
| Job chạy lỗi | Job đã được gọi nhưng xử lý thất bại | `JobExecutionException`, retry nghiệp vụ, lưu trạng thái, cảnh báo |
| Job đang chạy thì tiến trình chết | Một phiên thực thi bị gián đoạn | Quartz `requestsRecovery` (khi được cấu hình và JobStore hỗ trợ) cùng cơ chế idempotency |

`JobExecutionContext.isRecovering()` phản ánh **job recovery** sau sự cố, không phải cờ tổng quát cho mọi misfire. Cũng không nên suy ra mọi kỳ bị bỏ lỡ chỉ từ `getScheduledFireTime()` của một lần chạy bù: chính sách misfire có thể đã cập nhật thời điểm này.

## 3. Ví dụ thời gian: cùng một outage, ba kết quả rất khác nhau

Giả sử có một `CronTrigger` chạy **09:00 mỗi ngày**, lịch được lưu bền vững trong JDBC. Ứng dụng dừng từ **thứ Hai 08:50** đến **thứ Tư 10:00**. Các mốc 09:00 thứ Hai, thứ Ba và thứ Tư đều bị lỡ, và độ trễ đã vượt `misfireThreshold`.

| Chính sách | Khi khởi động lại thứ Tư 10:00 | Lượt kế tiếp theo lịch |
| --- | --- | --- |
| `DO_NOTHING` | Bỏ các lần quá hạn, không chạy ngay | Thứ Năm 09:00 |
| `FIRE_ONCE_NOW` / `FireAndProceed` | Yêu cầu một lần kích hoạt bù sớm nhất có thể, **không phải ba lần** | Thứ Năm 09:00 |
| `IGNORE_MISFIRE_POLICY` | Bỏ qua cơ chế điều chỉnh misfire, Quartz tiếp tục xử lý các mốc quá hạn theo tiến trình kích hoạt; có thể phát sinh loạt lần chạy sát nhau | Phụ thuộc lịch, trạng thái trigger và năng lực Scheduler |

Đây là ví dụ về hành vi chính sách, không phải cam kết giờ hoàn tất chính xác: còn phụ thuộc thread pool, JobStore, lịch loại trừ, thời gian chạy Job và độ trễ thực tế. Đặc biệt, `IGNORE_MISFIRE_POLICY` **không thay thế** một cơ chế backfill có kiểm soát nếu nghiệp vụ bắt buộc thực hiện đủ từng kỳ.

## 4. Chọn chính sách cho `CronTrigger`

Các phương thức dưới đây nằm trên `CronScheduleBuilder` và được áp dụng khi xây dựng trigger:

| API | Ý nghĩa | Trường hợp phù hợp |
| --- | --- | --- |
| `withMisfireHandlingInstructionDoNothing()` | Bỏ lần đã trễ, nhảy đến mốc theo cron trong tương lai | Làm mới cache, gửi chỉ số tạm thời, công việc chỉ cần trạng thái mới nhất |
| `withMisfireHandlingInstructionFireAndProceed()` | Kích hoạt **một lần** sớm nhất có thể rồi tiếp tục lịch cron | Đồng bộ trạng thái mới nhất; công việc có thể tự quét phần thiếu |
| `withMisfireHandlingInstructionIgnoreMisfires()` | Không để chính sách misfire điều chỉnh lại lịch quá hạn; các lượt cũ có thể được kích hoạt dồn | Chỉ dùng khi đã đánh giá kỹ tải, số lượt và tác động trùng lặp |

Nếu không chỉ định, Quartz dùng `Trigger.MISFIRE_INSTRUCTION_SMART_POLICY`. Với **`CronTrigger`**, smart policy được diễn giải thành `MISFIRE_INSTRUCTION_FIRE_ONCE_NOW`. Do đó, **không khai báo chính sách không có nghĩa là mặc định bỏ qua lượt trễ**.

Ví dụ khai báo trực tiếp:

```java
CronTrigger skipMissed = TriggerBuilder.newTrigger()
    .withIdentity("cache-refresh-trigger", "cache")
    .withSchedule(CronScheduleBuilder
        .cronSchedule("0 0 9 * * ?")
        .inTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        .withMisfireHandlingInstructionDoNothing())
    .build();

CronTrigger catchUpOnce = TriggerBuilder.newTrigger()
    .withIdentity("sync-trigger", "sync")
    .withSchedule(CronScheduleBuilder
        .cronSchedule("0 0 9 * * ?")
        .inTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        .withMisfireHandlingInstructionFireAndProceed())
    .build();
```

Đoạn trên minh họa **cách tạo hai trigger**. Để Scheduler thực thi, mỗi trigger còn phải được gắn với một `JobDetail` khi đăng ký lịch; ví dụ Spring Boot hoàn chỉnh ở mục 6 thực hiện việc này.

## 5. `SimpleTrigger`: phải quyết định cả cách tính số lượt lặp

`SimpleTrigger` dùng mốc bắt đầu, khoảng lặp và số lần lặp. Với `withRepeatCount(9)`, tổng số lần kích hoạt dự kiến ban đầu là **10** (một lần đầu + chín lần lặp), trừ khi có `endTime` hay điều kiện khác làm kết thúc sớm.

Chính sách misfire của `SimpleTrigger` phong phú hơn Cron vì phải xác định cả mốc tiếp theo lẫn số lần lặp còn lại:

| API trên `SimpleScheduleBuilder` | Điều được yêu cầu |
| --- | --- |
| `withMisfireHandlingInstructionFireNow()` | Kích hoạt ngay theo `FIRE_NOW` |
| `withMisfireHandlingInstructionNextWithRemainingCount()` | Đi đến mốc lịch kế tiếp sau hiện tại; điều chỉnh số lần lặp để tính đến các mốc bị lỡ |
| `withMisfireHandlingInstructionNextWithExistingCount()` | Đi đến mốc kế tiếp; giữ cấu hình repeat count hiện có |
| `withMisfireHandlingInstructionNowWithRemainingCount()` | Đặt mốc kích hoạt về hiện tại; tính lại số lần lặp còn lại |
| `withMisfireHandlingInstructionNowWithExistingCount()` | Đặt mốc kích hoạt về hiện tại; giữ repeat count hiện có |
| `withMisfireHandlingInstructionIgnoreMisfires()` | Bỏ qua việc diễn giải lại misfire |

Ví dụ chuỗi tác vụ mỗi phút, chấp nhận bỏ những lượt đã lỡ và giữ số lượt *còn thực sự cần chạy*:

```java
SimpleTrigger trigger = TriggerBuilder.newTrigger()
    .withIdentity("limited-poll-trigger", "poll")
    .startNow()
    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInMinutes(1)
        .withRepeatCount(9)
        .withMisfireHandlingInstructionNextWithRemainingCount())
    .build();
```

**Phân biệt `EXISTING_COUNT` với `REMAINING_COUNT`:** “existing” giữ giá trị đếm lặp trong cấu hình; “remaining” điều chỉnh theo phần lịch đã qua. Hai lựa chọn có thể tạo ra số lượt cuối cùng và thời điểm kết thúc khác nhau. Với trigger có `endTime`, cần kiểm tra riêng: khi cửa sổ đã hết trong lúc downtime, trigger có thể không còn cơ hội kích hoạt. `SMART_POLICY` của `SimpleTrigger` chọn hành vi tùy cấu hình repeat, vì thế càng nên khai báo tường minh và thử đúng trường hợp nghiệp vụ.

Không áp dụng máy móc các hằng số/method của `CronTrigger` cho `SimpleTrigger`: mỗi loại có bộ misfire instruction riêng.

## 6. Cấu hình trong Spring Boot dùng Quartz

Spring Boot có `spring-boot-starter-quartz` và tự nhận các bean `JobDetail`, `Trigger` để đăng ký với Scheduler. Ví dụ dùng **Java 17+**, Spring Boot 3.x, Quartz 2.x; nó khai báo chính sách **chạy bù một lần** cho một Job đồng bộ chạy mỗi năm phút.

Trong dự án Gradle đã dùng plugin Spring Boot, thêm dependency:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-quartz'
}
```

Tạo file `src/main/java/example/QuartzMisfireApplication.java`:

```java
package example;

import java.util.TimeZone;

import org.quartz.CronScheduleBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class QuartzMisfireApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuartzMisfireApplication.class, args);
    }

    @Bean
    public JobDetail reconciliationJob() {
        return JobBuilder.newJob(ReconciliationJob.class)
            .withIdentity("reconcile", "business")
            .storeDurably()
            .build();
    }

    @Bean
    public Trigger reconciliationTrigger(JobDetail reconciliationJob) {
        return TriggerBuilder.newTrigger()
            .withIdentity("reconcile-cron", "business")
            .forJob(reconciliationJob)
            .withSchedule(CronScheduleBuilder
                .cronSchedule("0 0/5 * * * ?")
                .inTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
                .withMisfireHandlingInstructionFireAndProceed())
            .build();
    }

    @DisallowConcurrentExecution
    public static class ReconciliationJob implements Job {
        @Override
        public void execute(JobExecutionContext context)
                throws JobExecutionException {
            System.out.printf(
                "job=%s scheduled=%s actual=%s%n",
                context.getJobDetail().getKey(),
                context.getScheduledFireTime().toInstant(),
                context.getFireTime().toInstant()
            );

            // Đặt việc đồng bộ idempotent thực tế tại đây.
        }
    }
}
```

Chạy ứng dụng bằng lệnh `./gradlew bootRun` (Windows dùng `gradlew.bat bootRun`). Khi trigger được kích hoạt, chương trình in một dòng theo dạng `job=business.reconcile scheduled=<Instant> actual=<Instant>`; các timestamp phụ thuộc thời điểm chạy thực tế, vì vậy không có một stdout thời gian cố định. `scheduled` là thời điểm Quartz ghi nhận cho **lần kích hoạt đó**, `actual` là lúc nó thực sự kích hoạt; độ lệch của một lần thực thi có thể dùng để giám sát, nhưng **không cho biết toàn bộ kỳ nghiệp vụ còn thiếu**.

Annotation `@DisallowConcurrentExecution` ngăn những lần thực thi đồng thời của **cùng một `JobDetail`/`JobKey`**. Nó không thay thế khóa nghiệp vụ/idempotency và không ngăn hai `JobKey` khác nhau đụng tới cùng một dữ liệu.

Muốn thay đổi quyết định trong ví dụ trên:

- Bỏ lượt lỡ: thay bằng `.withMisfireHandlingInstructionDoNothing()`.
- Cân nhắc xử lý lịch quá hạn dồn dập: `.withMisfireHandlingInstructionIgnoreMisfires()`, chỉ sau khi đánh giá kỹ rủi ro tải và tính lặp.
- Nếu yêu cầu xử lý đủ từng cửa sổ dữ liệu, giữ một trigger đánh thức Job rồi thực hiện **backfill bằng trạng thái nghiệp vụ bền vững** như mục 8; chỉ đổi misfire instruction không đủ.

## 7. Cấu hình JobStore, ngưỡng misfire và vòng đời deploy

Với một Spring Boot app dùng Quartz, cấu hình ví dụ trong `application.properties`:

```properties
# Ngưỡng phát hiện trễ: 60 giây, không phải giới hạn số lần chạy bù.
spring.quartz.properties.org.quartz.jobStore.misfireThreshold=60000
```

Mặc định Spring Boot dùng JobStore trong bộ nhớ. `RAMJobStore` thích hợp cho thử nghiệm hoặc các lịch không cần tồn tại sau khi tiến trình chết; **lịch bị mất khi ứng dụng dừng hẳn**. Nếu yêu cầu phát hiện misfire sau restart, cân nhắc `JDBCJobStore` với DataSource và bảng Quartz được thiết lập đúng:

```properties
spring.quartz.job-store-type=jdbc
spring.quartz.jdbc.initialize-schema=never
spring.quartz.properties.org.quartz.jobStore.misfireThreshold=60000
```

Cấu hình JDBC trên chỉ là **phần Quartz**, yêu cầu có `DataSource`, schema Quartz đã tạo bằng migration phù hợp và quyền truy cập DB. Không dùng tùy tiện `spring.quartz.jdbc.initialize-schema=always` trên DB đang có dữ liệu: script mặc định có thể xóa/tạo lại các bảng Quartz và làm mất trigger khi khởi động.

Các điểm vận hành cần hiểu:

- Thay `misfireThreshold` làm thay đổi cách phân loại trễ, **không thay** chính sách riêng của từng trigger. Không nên hạ ngưỡng rất thấp chỉ để “bắt cho hết”: có thể làm các độ trễ ngắn bình thường trở thành misfire.
- Khi sử dụng JobStore bền vững, trigger tồn tại từ bản deploy cũ có thể giữ cấu hình trước đó. Kiểm tra `Scheduler.getTrigger(triggerKey).getMisfireInstruction()` và quản lý việc thay thế lịch như một thay đổi có chủ đích; Spring Boot mặc định không nhất thiết ghi đè job đã đăng ký trong DB.
- Nếu chạy nhiều instance Quartz cùng dùng chung DB, cấu hình clustered JobStore cho đúng. Mọi node phải đồng bộ đồng hồ; tránh đồng thời nhiều Scheduler độc lập dùng cùng bảng mà không cấu hình cluster.
- Với nhiều trigger cùng quá hạn, xử lý misfire có thể tạo backlog và tranh chấp tài nguyên. Tối ưu thread pool/DB chỉ giải quyết năng lực tải, không quyết định đúng sai về mặt nghiệp vụ.

## 8. Khi cần chạy bù **đủ từng kỳ**, hãy dùng checkpoint nghiệp vụ

Ví dụ báo cáo thanh toán mỗi ngày phải tổng hợp **đủ ngày** đã lỡ: nếu máy chủ ngừng ba ngày, `FireAndProceed` chỉ cho một lần kích hoạt bù, không tự truyền vào Job danh sách ba ngày cần xử lý. Cách chắc chắn hơn là thiết kế Job đọc từ một nguồn trạng thái bền vững và tự xác định những **cửa sổ dữ liệu** chưa hoàn tất.

Một thiết kế tối thiểu:

1. Định nghĩa rõ cửa sổ nghiệp vụ, ví dụ `[00:00, 00:00 ngày kế)` theo `Asia/Ho_Chi_Minh`, và chỉ xử lý cửa sổ đã đóng.
2. Lưu checkpoint bền vững, chẳng hạn `job_key` và `last_completed_window_end`; có thể lưu thêm bảng `job_run` với `window_start`, `window_end`, `status`, số lần thử.
3. Mỗi lần Quartz đánh thức Job, truy vấn tất cả cửa sổ đã đóng từ sau checkpoint đến hiện tại, nhưng **giới hạn tối đa N cửa sổ/lượt** để tránh dồn tải.
4. Dùng khóa duy nhất `(job_key, window_start, window_end)`, cơ chế claim/lease hoặc transaction phù hợp để không có hai worker cùng xử lý một kỳ.
5. Chỉ đánh dấu `COMPLETED` và tiến checkpoint **sau khi** dữ liệu kỳ đó đã được xử lý thành công. Nếu có side effect ngoài DB, dùng idempotency key hoặc outbox để khắc phục trường hợp crash giữa hai thao tác.
6. Khi lỗi, để lại trạng thái retry được, tăng backoff, ghi lỗi và cảnh báo; không đánh dấu thành công rồi bỏ mất dữ liệu. Nếu backlog lớn hơn giới hạn một lượt, các lần kích hoạt sau tiếp tục phần còn lại hoặc có cơ chế backfill riêng.

Luồng khái niệm:

```text
Quartz trigger (chạy đúng giờ hoặc misfire)
    -> Job lấy danh sách cửa sổ chưa hoàn tất từ DB
    -> Claim cửa sổ + kiểm tra idempotency
    -> Xử lý có giới hạn tải
    -> Ghi kết quả, đánh dấu thành công, cập nhật checkpoint
    -> Quan sát backlog và cảnh báo khi trễ SLA
```

**Ví dụ cụ thể:** ứng dụng bỏ lỡ các ngày 10, 11, 12. Sau restart ngày 13, Job có thể được gọi một lần nhưng tự đọc checkpoint đến ngày 09, lần lượt xử lý và xác nhận ngày 10, 11, 12. Nếu nó chết sau ngày 11, lần sau chỉ tiếp tục ngày 12. Đây là đảm bảo từ **thiết kế dữ liệu/idempotency**, không phải chỉ từ một tên misfire instruction.

Đừng nhầm “một lần chạy Job” với “một cửa sổ nghiệp vụ”. Một lần chạy có thể xử lý nhiều kỳ; ngược lại, một kỳ có thể phải thử lại nhiều lần trước khi hoàn tất.

## 9. Ma trận quyết định trước khi chọn policy

| Bài toán | Khi bỏ lỡ lịch, nghiệp vụ cần gì? | Lựa chọn gợi ý |
| --- | --- | --- |
| Cập nhật cache mỗi 5 phút | Chỉ cần dữ liệu mới nhất, các snapshot cũ không còn giá trị | `DO_NOTHING` hoặc chạy một lần nếu cần làm mới ngay |
| Đồng bộ trạng thái đối tác | Cần bắt kịp thay đổi tới hiện tại, không cần mỗi bản snapshot trung gian | `FIRE_ONCE_NOW`, Job đọc delta từ checkpoint |
| Lập báo cáo/đối soát theo ngày | Không được mất bất kỳ ngày hạch toán nào | Trigger đánh thức Job + backfill theo từng ngày, checkpoint bền vững |
| Gửi thông báo theo từng deadline | Có thể cần bỏ quá hạn, gộp, hay gửi riêng theo chính sách sản phẩm | Quy tắc nghiệp vụ rõ ràng, lưu deadline/idempotency, không phó mặc Quartz |
| Tác vụ có chi phí cao hoặc không idempotent | Nổ ra nhiều lần sau downtime rất nguy hiểm | Giới hạn chạy bù, xác nhận thủ công khi cần; tránh `IGNORE_MISFIRE_POLICY` không kiểm soát |

Trước khi chốt, trả lời tường minh: **(1)** Có được bỏ lượt không? **(2)** Một lần chạy bù có đủ không? **(3)** Nếu cần mọi kỳ, kỳ được định danh và ghi nhận hoàn tất ở đâu? **(4)** Có giới hạn backlog, retry và chống trùng chưa? **(5)** Người vận hành nhận ra mất kỳ bằng tín hiệu nào?

## 10. Kiểm chứng bằng tình huống hỏng hóc, không chỉ bằng chạy bình thường

Nên dựng bài thử trên môi trường có cấu hình JobStore giống production, đặc biệt nếu production dùng JDBC:

| Kịch bản thử | Điều cần xác minh |
| --- | --- |
| Hệ thống hoạt động bình thường | Job chạy gần lịch, không sinh kỳ trùng |
| Trễ nhỏ hơn `misfireThreshold` | Độ trễ không bị hiểu sai thành yêu cầu chạy bù |
| Dừng Scheduler vượt ngưỡng rồi `start()` lại | `DO_NOTHING` bỏ mốc cũ; `FireAndProceed` kích hoạt bù một lần theo ngữ nghĩa trigger |
| Tắt hẳn ứng dụng qua nhiều mốc rồi restart | Lịch JDBC còn tồn tại; kiểm tra chính sách và số kỳ nghiệp vụ đã xử lý |
| Job mất kết nối DB hoặc crash giữa một cửa sổ | Retry an toàn, checkpoint không nhảy qua kỳ chưa thành công |
| Khởi động nhiều instance/worker | Không chạy trùng cùng kỳ, không vượt giới hạn tải |
| Chuyển múi giờ/DST nếu có lịch địa phương | Cron timezone và định nghĩa cửa sổ nghiệp vụ cho kết quả như yêu cầu |

Có thể dùng `scheduler.standby()` rồi `scheduler.start()` để tạo tình huống tạm dừng trong một integration test, nhưng test dựa vào `Thread.sleep` và đồng hồ thực có thể không ổn định; đo lại `getNextFireTime()`, số lần được gọi và trạng thái DB thay vì khẳng định chính xác mili giây. Để chứng minh hành vi sau **process restart**, phải dùng JobStore bền vững: bài thử chỉ với `RAMJobStore` không đại diện cho trường hợp này.

Quan sát production tối thiểu gồm **số misfire**, độ trễ giữa lịch và kích hoạt, **backlog theo kỳ nghiệp vụ**, độ dài Job, số retry, số kỳ thất bại và số lần bị ngăn trùng. `TriggerListener.triggerMisfired(...)` có thể giúp ghi nhận sự kiện misfire; metric quan trọng nhất cho nghiệp vụ vẫn là *kỳ nào đã hoàn tất, kỳ nào còn thiếu*.

## 11. Những lỗi thường gặp

1. **Không cấu hình policy:** mặc định `SMART_POLICY` của Cron thường dẫn tới một lần chạy bù; điều này có thể gây gửi mail hoặc ghi nhận tiền ngoài dự kiến.
2. **Tưởng `FireAndProceed` chạy đủ mọi mốc cũ:** nó yêu cầu một lần bù; nếu cần đủ từng kỳ phải có logic checkpoint/backfill.
3. **Tưởng `IGNORE_MISFIRE_POLICY` là cơ chế replay chính xác:** nó có thể tạo đợt chạy dồn và vẫn không thay thế định danh cửa sổ/idempotency.
4. **Nhầm `@Scheduled(cron = ...)` của Spring với Quartz `CronTrigger`:** đây là hai cơ chế lập lịch khác nhau; các builder misfire của Quartz không tự áp dụng cho `@Scheduled`.
5. **Dùng RAMJobStore rồi mong khôi phục lịch sau restart:** metadata lịch trong RAM không còn sau khi tiến trình kết thúc.
6. **Chỉ thêm `@DisallowConcurrentExecution` rồi coi như chống trùng:** annotation chỉ giới hạn cùng `JobKey`; job khác, API thủ công hoặc sự cố ghi kết quả vẫn cần idempotency nghiệp vụ.
7. **Tăng worker hoặc sửa threshold để chữa mọi sự cố:** tối ưu tải không trả lời được câu hỏi “bỏ, gộp hay chạy đủ lịch bị lỡ?”.
8. **Không kiểm tra cấu hình trigger tồn tại trong JDBC sau deploy:** code mới chưa chắc đã thay đổi trigger cũ đã được lưu.
9. **Dùng giờ hệ thống tùy tiện:** cron timezone, DST, clock drift và định nghĩa kỳ nghiệp vụ cần nhất quán, nhất là khi dữ liệu phục vụ đối soát hoặc thanh toán.

## 12. Ghi nhớ

**Misfire là tín hiệu lịch kích hoạt đã trễ; chính sách của trigger quyết định Quartz điều chỉnh lịch ra sao.** Với `CronTrigger`, hãy chọn rõ `DO_NOTHING` hay `FIRE_ONCE_NOW` theo nhu cầu. Với `SimpleTrigger`, hãy chọn thêm cách tính repeat count. Nếu sản phẩm yêu cầu **không bỏ sót một kỳ nghiệp vụ**, trách nhiệm đó thuộc về thiết kế checkpoint + xử lý idempotent + backfill có giới hạn, chứ không thể giao hoàn toàn cho Quartz.

### Tài liệu gốc

- [Quartz: More About Triggers / misfire](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/tutorial-lesson-04.html)
- [Quartz: CronTrigger và misfire instructions](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/tutorial-lesson-06.html)
- [Quartz: SimpleTrigger và misfire instructions](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/tutorial-lesson-05.html)
- [Spring Boot 3.3: Quartz Scheduler](https://docs.spring.io/spring-boot/3.3/reference/io/quartz.html)
- [Quartz: JDBC JobStore và misfireThreshold](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/configuration/ConfigJobStoreTX.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="quartz-integration-008">Giới hạn chạy đồng thời, lưu trữ và cụm phụ thuộc cấu hình Quartz</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn chạy đồng thời, lưu trữ và clustering phụ thuộc cấu hình Quartz

Trong Spring Framework, Quartz là bộ lập lịch thực thi công việc (job scheduler). Khi tích hợp Quartz, ba vấn đề dễ bị nhầm thành một: **bao nhiêu job được chạy cùng lúc**, **lịch chạy có tồn tại sau khi ứng dụng khởi động lại không**, và **nhiều instance ứng dụng có phối hợp để thực thi lịch chung hay không**. Ba khả năng này do những cơ chế và cấu hình khác nhau quyết định.

Bài học tập trung vào Quartz 2.x trên Java 17+ và cách cấu hình bằng Spring Boot 3.x hoặc `SchedulerFactoryBean` của Spring Framework. Cần phân biệt bảo đảm ở mức **lập lịch** với bảo đảm ở mức **nghiệp vụ**: ngay cả khi Quartz chỉ phát một trigger trên cụm, một tác dụng phụ bên ngoài vẫn có thể bị lặp khi tiến trình gặp lỗi và chạy lại.

## 1. Mô hình thực thi: Job, JobDetail, Trigger, Scheduler và JobStore

- **`Job`**: lớp cài đặt phương thức `execute(JobExecutionContext)` để thực hiện công việc.
- **`JobDetail`**: định nghĩa một công việc cụ thể, có `JobKey` gồm `name` và `group`, kiểu lớp Job và `JobDataMap`.
- **`Trigger`**: mô tả *khi nào* một JobDetail cần chạy; ví dụ lịch cron hoặc chu kỳ cố định. Một JobDetail có thể được nhiều trigger tham chiếu.
- **`Scheduler`**: điều phối trigger, cấp thread chạy job, ghi trạng thái vào JobStore.
- **`JobStore`**: nơi giữ JobDetail, Trigger và trạng thái lập lịch. `RAMJobStore` lưu trong bộ nhớ; `JDBCJobStore` lưu trong cơ sở dữ liệu.
- **Thread pool của Quartz**: các worker thực thi Job; không phải chính là `TaskExecutor` mặc định của Spring.

Luồng khái niệm:

```text
Trigger đến thời điểm cần kích hoạt
  -> Scheduler/JobStore kiểm tra trigger đủ điều kiện và thread còn khả dụng
  -> một scheduler instance nhận quyền thực thi lần kích hoạt
  -> worker thread gọi Job.execute(context)
  -> Quartz cập nhật trạng thái/next-fire-time và xử lý misfire, recovery khi cần
```

| Mục tiêu | Cơ chế chủ yếu | Phạm vi |
| --- | --- | --- |
| Giới hạn số worker chạy cùng lúc | `org.quartz.threadPool.threadCount` | **Mỗi Scheduler instance** |
| Không chạy chồng cùng một JobDetail | `@DisallowConcurrentExecution` | **Cùng JobKey**; trên cụm JDBC cũng phối hợp theo khóa đó |
| Giữ lịch qua restart | JDBC JobStore và schema Quartz | **Dữ liệu lịch trong DB** |
| Phối hợp nhiều node thực thi lịch chung | JDBC JobStore + `isClustered=true` + định danh node | **Các scheduler cùng cluster** |
| Không xử lý nghiệp vụ hai lần | Khóa/unique key/idempotency ở tầng nghiệp vụ | **Hệ thống nghiệp vụ**, ngoài cam kết của riêng Quartz |

Không có một thuộc tính cấu hình đơn lẻ nào đồng thời cung cấp cả năm hành vi trên.

## 2. Giới hạn chạy đồng thời: thread pool khác với JobKey

### 2.1. `threadCount` giới hạn worker trên từng node

Ví dụ `org.quartz.threadPool.threadCount=4` cho phép tối đa bốn worker Quartz của **scheduler instance đó** chạy Job cùng lúc. Nếu có 20 JobDetail khác nhau sẵn sàng, bốn worker có thể đang thực thi, các lần kích hoạt khác phải chờ được xử lý theo lịch và cơ chế misfire. Cấu hình này **không** có nghĩa là mỗi loại job được chạy tối đa bốn lần, cũng không giới hạn bốn job trên toàn bộ cụm nhiều node.

Với hai node, mỗi node có `threadCount=4`, năng lực worker lý thuyết tối đa là **tám** công việc Quartz đồng thời trên cụm; giới hạn thực tế còn do database, transaction, thời lượng chạy, lock, I/O và tài nguyên của từng node quyết định. Tăng pool không chữa được lỗi truy vấn chậm hoặc tranh chấp khóa DB.

### 2.2. `@DisallowConcurrentExecution` chống chạy chồng theo `JobKey`

Giả sử JobDetail `finance/reconcile` chạy tám giây, trigger yêu cầu kích hoạt mỗi ba giây. Nếu lớp Job có `@DisallowConcurrentExecution`, Quartz sẽ không thực thi chồng **hai lần của cùng JobDetail**. Lần sau chờ đủ điều kiện chạy, và nếu trễ quá ngưỡng, trigger có thể được xử lý theo chính sách misfire. Đừng suy ra số lần gọi hoặc thời điểm thực tế chỉ bằng phép chia tần suất.

Điểm quan trọng: hai JobDetail có `JobKey` khác nhau nhưng cùng sử dụng một Java Job class vẫn có thể chạy đồng thời:

```text
Job class: ReconcileJob
  JobKey finance/reconcile-A -> không chồng với chính reconcile-A
  JobKey finance/reconcile-B -> có thể chạy song song với reconcile-A
```

Vì vậy, nếu hai trigger phải xử lý **cùng một đối tượng nghiệp vụ** theo thứ tự, hãy gán chúng vào cùng JobDetail/JobKey phù hợp, hoặc bổ sung cơ chế khóa nghiệp vụ. `@DisallowConcurrentExecution` không phải global mutex cho toàn bộ Java class, cho mọi lịch, hoặc cho mọi ứng dụng dùng cùng database.

`@PersistJobDataAfterExecution` phục vụ mục đích khác: yêu cầu Quartz lưu thay đổi của JobDataMap khi job hoàn tất. Nếu cập nhật JobDataMap có thể bị ghi đè do các lần chạy song song, thông thường nên kết hợp với `@DisallowConcurrentExecution`. Không dùng JobDataMap làm kho dữ liệu nghiệp vụ lớn hay trạng thái giao dịch quan trọng.

### 2.3. Quartz không tự giới hạn những việc do Job tự phân nhánh

Một Job có thể gọi API ngoài, mở thêm thread hoặc gửi hàng nghìn message tới queue. `threadCount` chỉ giới hạn các worker Quartz đang chạy `execute()`, không giới hạn tổng số tác vụ bất đồng bộ được tạo bên trong. Với API rate limit, hàng đợi, số kết nối DB hoặc tài nguyên của đối tác, cần thiết kế thêm semaphore, connection pool, backpressure hoặc quota chuyên biệt.

Tương tự, job lập lịch bằng Spring `@Scheduled`/`TaskScheduler` và tác vụ chạy bằng `@Async` có executor riêng; không tự chịu sự điều phối của Quartz.

## 3. Lưu trữ: RAMJobStore và JDBCJobStore

| Tiêu chí | `RAMJobStore` | `JDBCJobStore` |
| --- | --- | --- |
| Lịch JobDetail/Trigger | Trong RAM của tiến trình | Trong bảng Quartz của cơ sở dữ liệu |
| Restart tiến trình | Lịch động trong RAM biến mất; chỉ có lịch được ứng dụng đăng ký lại | Lịch đã persist có thể được tải lại |
| Cluster Quartz nhiều node | Không thể đồng bộ trigger xuyên các tiến trình bằng RAM riêng biệt | Hỗ trợ cluster khi cấu hình đúng |
| Độ trễ/độ phức tạp | Đơn giản, không cần DB Quartz | Có JDBC, schema, transaction, lock, vận hành DB |
| Tình huống hợp lý | Demo, bài thử nhỏ, lịch có thể tái tạo | Lịch quan trọng, cần restart/failover và nhiều node |

**JDBC không có nghĩa là lưu kết quả nghiệp vụ.** Quartz lưu định nghĩa job, trigger, lịch lần chạy tới, trạng thái cần thiết và một số metadata thực thi; không mặc định cung cấp lịch sử thành công/thất bại chi tiết, hóa đơn đã xử lý, hay nhật ký nghiệp vụ bất biến. Những dữ liệu đó phải được lưu riêng.

Với JDBC JobStore, cần chuẩn bị schema tương thích với phiên bản Quartz và DB đang sử dụng (thường có các bảng `QRTZ_JOB_DETAILS`, `QRTZ_TRIGGERS`, `QRTZ_FIRED_TRIGGERS`, `QRTZ_SCHEDULER_STATE`, `QRTZ_LOCKS`). Dùng migration có kiểm soát để tạo/nâng cấp schema. Trong production, tránh `spring.quartz.jdbc.initialize-schema=always` với script mặc định: nhiều script khởi tạo có thao tác xóa/tạo lại bảng, làm mất trigger khi restart. Cấu hình `never` sau khi đã triển khai schema bằng migration.

**JobDataMap và phiên bản mã:** JDBC JobStore có thể serialize dữ liệu không phải String tùy cấu hình. Lưu object Java tùy ý dễ gặp lỗi tương thích khi đổi class hoặc triển khai phiên bản mới. Ưu tiên ID/chuỗi/giá trị đơn giản, dữ liệu thực lưu trong DB nghiệp vụ; cân nhắc `org.quartz.jobStore.useProperties=true` nếu chỉ dùng dữ liệu String, sau khi kiểm tra toàn bộ job/trigger tương thích.

**Transaction:** Quartz cần commit/rollback các thay đổi lịch qua JobStore; transaction của nghiệp vụ bên trong `execute()` là trách nhiệm độc lập. Không mặc định coi việc Quartz đánh dấu hoàn thành và việc ghi database/gọi HTTP bên ngoài là một giao dịch nguyên tử. Ví dụ, nếu hệ thống đã gửi tiền nhưng process chết trước khi lưu trạng thái hoàn thành, recovery có thể làm gửi lại. Giải pháp là idempotency key, unique constraint, outbox hoặc trạng thái nghiệp vụ có thể khôi phục.

## 4. Cluster Quartz: điều kiện để nhiều node chia sẻ lịch

Cluster JDBC hoạt động bằng cách để nhiều scheduler instance truy cập **cùng một hệ bảng Quartz** và phối hợp lấy quyền xử lý trigger qua database. Một lần kích hoạt trigger được một node nhận; node nào nhận có thể thay đổi ở những lần tiếp theo. Đây là load balancing và failover của bộ lập lịch, không phải cơ chế bầu leader cố định cho toàn ứng dụng.

Các điều kiện đồng thời phải đúng:

1. **Mọi node dùng JDBC JobStore**, cùng database/schema, cùng tiền tố bảng (`tablePrefix`) và cùng định danh scheduler (`instanceName`/`spring.quartz.scheduler-name`). Không trộn một instance chạy `isClustered=false` với các node cluster dùng chung bảng.
2. **Mọi node đều bật `org.quartz.jobStore.isClustered=true`**; tên scheduler và các cấu hình JobStore cốt lõi phải nhất quán giữa node.
3. **Mỗi node có `org.quartz.scheduler.instanceId` riêng**. Giá trị `AUTO` cho phép Quartz cấp ID instance; không hardcode cùng một ID trên hai process.
4. **Đồng bộ đồng hồ** giữa máy/VM/container, ví dụ dùng NTP; lệch thời gian sẽ phá tính đúng đắn của thời điểm trigger, misfire và kiểm tra node hỏng.
5. **Database đáng tin cậy**: schema đúng, driver/delegate tương thích, pool kết nối đủ, transaction/lock hoạt động, latency/timeout được theo dõi.
6. **Các node triển khai code job tương thích với dữ liệu persist**, giữ `JobKey` ổn định; thay đổi tên class, schema JobDataMap hoặc cấu hình không đồng bộ giữa node phải có kế hoạch migration.

Trong Quartz, `org.quartz.jobStore.clusterCheckinInterval` (millisecond) điều chỉnh tần suất node ghi nhịp sống vào DB. Giá trị nhỏ có thể phát hiện node chết sớm hơn nhưng tăng số lần truy cập DB; phát hiện và phục hồi không xảy ra tức thời, còn chịu ảnh hưởng lỗi mạng, pause JVM và transaction.

### 4.1. Failover khác với chạy đúng một lần

Một node đang thực hiện Job bị kill có thể để lại phần nghiệp vụ đã ghi, đã gửi email hoặc đã gọi dịch vụ ngoài. Node khác nhận diện failure sau check-in và xử lý trigger. Nếu JobDetail được đăng ký `requestRecovery(true)`, Quartz có thể chạy recovery job trên node còn sống cho job đang thực thi khi node lỗi; khi đó `JobExecutionContext.isRecovering()` giúp nhận biết lượt recovery. Nếu không yêu cầu recovery, không nên giả định công việc dở dang sẽ ngay lập tức được chạy lại; một trigger trong tương lai vẫn có thể kích hoạt lần tiếp theo.

Ngay cả khi cluster khóa trigger chính xác, **exactly-once ở mức nghiệp vụ không được Quartz bảo đảm**: hãy dùng khóa idempotency gắn với nghiệp vụ, kiểm tra trạng thái đã hoàn tất, và thiết kế thao tác gọi ra ngoài để có thể retry an toàn.

### 4.2. Ranh giới chống chồng trong cluster

Trong **cùng cluster JDBC**, `@DisallowConcurrentExecution` vẫn áp dụng cho cùng `JobKey`, kể cả khi node A đang giữ execution và node B thấy trigger kế tiếp. Nhưng nếu hai cụm sử dụng database/bảng tách biệt, hoặc lập hai JobKey khác nhau cho cùng đơn hàng, Quartz không thể phối hợp để tránh trùng tác dụng nghiệp vụ; cần tầng khóa/unique nghiệp vụ dùng chung.

## 5. Cấu hình mẫu Spring Boot 3.x: JDBC cluster

Trong ứng dụng Spring Boot đã cấu hình `DataSource` và có dependency `spring-boot-starter-quartz`, ví dụ dưới đây minh họa hai node **dùng chung** database Quartz. Lệnh Gradle dependency trong project dùng Spring Boot:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-quartz'
    runtimeOnly 'org.postgresql:postgresql'
}
```

Hai node có cùng nội dung `application.properties` sau; thông tin kết nối database được cấu hình bằng Spring DataSource của ứng dụng (hoặc một bean `@QuartzDataSource` riêng). Dùng migration để tạo schema Quartz trước khi bật lịch.

```properties
spring.quartz.job-store-type=jdbc
spring.quartz.jdbc.initialize-schema=never
spring.quartz.scheduler-name=financeScheduler
spring.quartz.overwrite-existing-jobs=false
spring.quartz.wait-for-jobs-to-complete-on-shutdown=true

spring.quartz.properties.org.quartz.scheduler.instanceId=AUTO
spring.quartz.properties.org.quartz.threadPool.threadCount=4
spring.quartz.properties.org.quartz.jobStore.isClustered=true
spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval=10000
spring.quartz.properties.org.quartz.jobStore.misfireThreshold=60000
```

**Ý nghĩa của từng nhóm:**

- `job-store-type=jdbc` yêu cầu Spring Boot dùng JDBC cho Quartz; phải có `DataSource` khả dụng. Nếu Quartz dùng DB khác DB chính, dùng bean `@QuartzDataSource`; transaction manager chuyên biệt có thể đánh dấu `@QuartzTransactionManager`.
- `initialize-schema=never` bảo vệ schema production khỏi bị tái tạo khi startup; migration là bước triển khai riêng.
- `scheduler-name=financeScheduler` giống nhau trên mọi node; `instanceId=AUTO` làm định danh từng instance khác nhau.
- `threadCount=4` là giới hạn worker trên **mỗi node**, không phải bốn cho toàn cluster.
- `isClustered=true` bật phối hợp trigger qua JDBC; `clusterCheckinInterval=10000` có nghĩa là chu kỳ check-in 10 giây, **không phải** SLA phục hồi đúng 10 giây.
- `misfireThreshold=60000` là ngưỡng trễ để Quartz xét trigger bị misfire, không có nghĩa rằng mọi trigger đến trễ đều tự động bị bỏ qua.
- `overwrite-existing-jobs=false` giữ định nghĩa job đã tồn tại trong JobStore khi ứng dụng đăng ký cùng khóa, trừ khi chủ động quyết định chiến lược cập nhật.

Khi sử dụng Spring Boot/Spring `SchedulerFactoryBean` với DataSource do Spring cung cấp, **không sao chép máy móc cấu hình Quartz thuần** bằng cách ép `org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX` và tự định nghĩa lại DataSource của Quartz. Spring có cơ chế JobStore tích hợp với DataSource/transaction của container (ví dụ `LocalDataSourceJobStore`). Chọn **một** đường cấu hình phù hợp với cách khởi tạo scheduler, kiểm tra log startup để xác nhận JobStore thực tế.

Nếu chỉ cần **một node và lịch có thể tái tạo** khi restart, có thể dùng profile local với `spring.quartz.job-store-type=memory` và bỏ các thuộc tính clustering. Không sử dụng cấu hình RAM của local để thử nghiệm khả năng failover.

## 6. Ví dụ Java: một JobKey không chạy chồng dù trigger dày

Dưới đây là ví dụ gồm **hai tệp mã trong một ứng dụng Spring Boot 3.x** có starter Quartz. Đây là mã ứng dụng dùng thư viện Spring/Quartz; không phải một file Java độc lập có thể chạy bằng lệnh `javac` thiếu classpath. Ví dụ in log để quan sát giới hạn, không thay thế xử lý nghiệp vụ và cơ chế idempotency.

**Tệp `src/main/java/demo/quartz/ReconcileJob.java`:**

```java
package demo.quartz;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@DisallowConcurrentExecution
public class ReconcileJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String key = context.getJobDetail().getKey().toString();
        System.out.println("START " + key + ", recovery=" + context.isRecovering());

        try {
            Thread.sleep(8_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JobExecutionException("Job interrupted", exception);
        }

        System.out.println("END " + key);
    }
}
```

**Tệp `src/main/java/demo/quartz/QuartzConfiguration.java`:**

```java
package demo.quartz;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class QuartzConfiguration {
    @Bean
    JobDetail reconcileJobDetail() {
        return JobBuilder.newJob(ReconcileJob.class)
                .withIdentity("reconcile", "finance")
                .requestRecovery(true)
                .storeDurably()
                .build();
    }

    @Bean
    Trigger reconcileTrigger(JobDetail reconcileJobDetail) {
        return TriggerBuilder.newTrigger()
                .withIdentity("reconcile-every-3-seconds", "finance")
                .forJob(reconcileJobDetail)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(3)
                        .repeatForever())
                .build();
    }
}
```

Cần có class `@SpringBootApplication` trong package `demo.quartz` hoặc package cha để Spring quét được cấu hình; chạy ứng dụng bằng `./gradlew bootRun` từ project Spring Boot tương ứng. Để thử đơn giản với một JVM, dùng RAMJobStore và `threadCount=2`; để thử cụm, dùng cấu hình JDBC ở mục 5 và khởi chạy **hai tiến trình** kết nối cùng schema Quartz.

**Kết quả quan sát kỳ vọng, không gán timestamp cố định:** với JobKey `finance.reconcile`, mỗi dòng `START` phải có dòng `END` tương ứng trước khi lần `START` tiếp theo của cùng khóa xuất hiện (nếu job không bị ngắt hoặc process chết). Đừng kỳ vọng đúng một dòng START sau mỗi ba giây: job cố tình ngủ tám giây và trigger có thể trễ hoặc misfire. Khi chạy hai node có cùng JobKey và cluster JDBC, đối chiếu log cả hai node để bảo đảm không có hai khoảng thực thi cùng khóa bị chồng. Nếu bỏ annotation, nhiều worker có thể chạy chồng các lần kích hoạt.

**Lưu ý khi học từ ví dụ:** nếu thử chuyển từ memory sang JDBC, hãy có database, schema Quartz và DataSource thật, rồi bảo đảm không còn dữ liệu JobDetail/Trigger cũ xung đột. Trong hệ thống production, không dùng `Thread.sleep` làm xử lý nghiệp vụ; đoạn ngủ chỉ tạo cửa sổ quan sát concurrency.

## 7. Misfire, lịch bị trễ và các lựa chọn có hậu quả khác nhau

Trigger có thời điểm dự kiến (`scheduled fire time`), nhưng lần chạy thực tế có thể bắt đầu muộn vì không có worker, node/DB bận hoặc scheduler bị dừng. `misfireThreshold` là ngưỡng xét trễ; **chính sách của từng loại trigger** quyết định xử lý:

- `CronTrigger` có thể dùng `withMisfireHandlingInstructionDoNothing()` để bỏ các lần cron đã lỡ và chờ lịch tương lai, hoặc `withMisfireHandlingInstructionFireAndProceed()` để chạy một lần sớm nhất có thể rồi theo lịch tiếp. Mặc định smart policy có cách diễn giải riêng; không mặc nhiên coi nó là phát lại mọi lượt đã lỡ.
- `SimpleTrigger` có các lựa chọn như `NextWithRemainingCount`, `NowWithExistingCount`...; hãy đọc semantics về số lần lặp còn lại trước khi chọn.
- Trong ứng dụng xử lý đối soát, quyết định *bỏ qua*, *chạy bù một lần* hay *backfill mọi khoảng dữ liệu* là quyết định **nghiệp vụ**; Quartz không tự suy ra cách backfill đúng. Nhiều hệ thống phù hợp với việc một job khởi chạy quét các bản ghi chưa xử lý theo checkpoint.

Ví dụ khai báo cron cố định múi giờ, chủ động bỏ lần lịch đã lỡ:

```java
CronScheduleBuilder daily = CronScheduleBuilder
        .cronSchedule("0 0 2 * * ?")
        .inTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        .withMisfireHandlingInstructionDoNothing();
```

Đây là **đoạn cấu hình minh họa** đặt bên trong mã Java có dependency Quartz và import `org.quartz.CronScheduleBuilder`; không phải chương trình độc lập. Đừng nhầm timezone của cron với timezone máy chủ, timezone database hoặc thời gian trong log.

## 8. Thử nghiệm có kiểm chứng trên một node và trên hai node

### Thử nghiệm A — Giới hạn worker

Cấu hình RAMJobStore một node với `threadCount=2`, đăng ký ba JobDetail có **ba JobKey khác nhau** cùng đủ điều kiện chạy và mỗi job giữ worker khoảng tám giây. Quan sát số `execute()` thực sự đang chạy cùng thời điểm: không vượt quá hai do thread pool của scheduler đó. Nếu dùng một JobKey duy nhất với `@DisallowConcurrentExecution`, quan sát thêm giới hạn **một** lần thực thi cho khóa đó.

### Thử nghiệm B — Lưu trữ

Đăng ký trigger với JDBC JobStore, xác nhận các bảng Quartz có JobDetail/Trigger và lịch kích hoạt. Dừng ứng dụng rồi khởi động lại bằng cùng schema, cùng scheduler name, không chạy script xóa bảng. Kiểm tra lịch persisted được phục hồi và chiến lược đăng ký lại từ Spring không vô tình ghi đè (`overwrite-existing-jobs`). Lặp lại với RAMJobStore để hiểu rằng lịch chỉ còn nếu ứng dụng tự đăng ký lại.

### Thử nghiệm C — Cluster và recovery

1. Chạy node A, B với **cùng** database/schema, scheduler name, `isClustered=true`; từng node có `instanceId` duy nhất và cùng JobKey.
2. Quan sát log kèm tên node, JobKey, triggerKey, `scheduledFireTime` và `fireInstanceId`. Một lần firing do một node nhận, nhưng các lần khác nhau có thể được chia cho hai node.
3. Cho job chạy lâu rồi dừng cưỡng bức node đang chạy. Với `requestRecovery(true)`, kiểm tra node còn sống nhận recovery sau khi phát hiện failure; kiểm tra `context.isRecovering()` và trạng thái nghiệp vụ trước khi tác động lại.
4. Thử restart node, tăng/giảm `threadCount` và gây chậm database có kiểm soát để quan sát check-in, lock và misfire. Chỉ kết luận sau khi ghép log cả cụm và dữ liệu nghiệp vụ.
5. Kiểm chứng cơ chế idempotency bằng một giao dịch có cùng business key được thử lại: database/đối tác không được nhận hai hiệu ứng độc lập.

Không dùng việc có đúng một log START để kết luận exactly-once nghiệp vụ; phải kiểm tra **đầu ra** (dữ liệu/side effect), retry và các tình huống lỗi giữa chừng.

## 9. Chẩn đoán lỗi theo triệu chứng

| Triệu chứng | Nguyên nhân nên kiểm tra | Hướng xử lý |
| --- | --- | --- |
| Lịch được đăng ký nhưng chạy chậm | Worker bận, `threadCount` thấp, job giữ thread quá lâu, DB/lock chậm | Đo thời lượng job và số worker; tối ưu job, cân nhắc pool sau khi kiểm tra tài nguyên |
| Cùng loại Job chạy song song ngoài ý muốn | Thiếu `@DisallowConcurrentExecution` hoặc tạo nhiều `JobKey` | Xác nhận identity thực tế, dùng cùng JobDetail khi cần, thêm khóa nghiệp vụ nếu cần |
| Hai pod cùng làm một lần việc | Mỗi pod dùng RAMJobStore riêng, chưa bật cluster, hoặc hai cụm tách biệt | Xác nhận `isClustered`, chung DB/schema, scheduler name và JobKey |
| Trigger biến mất sau restart | RAMJobStore hoặc script init đã xóa bảng; registration strategy thay đổi | Chuyển JDBC, dùng migration, kiểm tra `initialize-schema` và `overwrite-existing-jobs` |
| Node khác không phục hồi việc dở dang | Chưa phát hiện node chết, `requestRecovery` chưa bật, DB lỗi | Kiểm tra `QRTZ_SCHEDULER_STATE`, check-in, error log, recovery flag và tính idempotent |
| Chạy muộn, bỏ lượt cron | Thread/DB nghẽn, misfire threshold hoặc misfire policy | Đối chiếu scheduled fire time, chính sách Cron/SimpleTrigger và yêu cầu backfill |
| JDBC lỗi serialize khi triển khai bản mới | Lưu class/object không tương thích trong `JobDataMap` | Dùng ID/chuỗi và migration dữ liệu có kiểm soát |
| Tăng node nhưng hiệu năng không tăng | Lock/connection bottleneck của shared JDBC JobStore hoặc cùng JobKey bị tuần tự hóa | Đo DB wait, tách job độc lập, tối ưu scheduling và worker, tránh tăng node mù quáng |

Để quan sát vận hành, có thể dùng log JobListener/TriggerListener, Quartz `SchedulerMetaData` và trạng thái trong bảng Quartz. `QRTZ_SCHEDULER_STATE` liên quan đến heartbeat cluster; `QRTZ_FIRED_TRIGGERS` là trạng thái firing đang theo dõi, **không phải** nhật ký nghiệp vụ lâu dài. `Scheduler.getCurrentlyExecutingJobs()` chỉ cho ảnh chụp các job đang chạy trên **scheduler instance hiện tại**, không phải số lượng toàn cụm.

## 10. Quy tắc chọn cấu hình và checklist trước khi triển khai

- **Một instance, lịch tái tạo được, không yêu cầu failover:** RAMJobStore có thể đủ; vẫn cần thread pool và xử lý overlap phù hợp.
- **Một instance, lịch phải tồn tại qua restart:** JDBCJobStore; `isClustered` có thể để false nếu chỉ đúng một scheduler sử dụng hệ bảng.
- **Nhiều instance cùng thực thi một tập lịch:** JDBCJobStore cluster, định danh scheduler chung, instance ID riêng, schema/khóa đồng bộ, đồng hồ chính xác.
- **Không chạy chồng cùng công việc:** `@DisallowConcurrentExecution` theo JobKey; thêm khóa/unique nghiệp vụ khi nhiều JobKey hoặc nhiều cụm có thể đụng cùng dữ liệu.
- **Cần phục hồi sau node chết:** cân nhắc `requestRecovery(true)` nhưng bắt buộc thiết kế retry idempotent; xác định misfire policy rõ ràng.
- **Giới hạn throughput bên ngoài:** dùng rate limiter, DB connection/transaction sizing và queue/backpressure; đừng coi `threadCount` là cơ chế rate limit toàn hệ thống.

Trước production, hãy kiểm tra: schema và migration DB; phiên bản Quartz/driver; JobKey ổn định; tên scheduler chung; `instanceId` duy nhất; mọi node `isClustered=true`; pool thread/DB phù hợp; timezone và đồng bộ đồng hồ; chính sách cron/misfire; recovery và idempotency; log/metrics, cảnh báo khi trigger trễ; thử kill node và restart mà không mất trigger.

**Kết luận kỹ thuật:** giới hạn worker là thuộc tính **mỗi scheduler**, chống overlap là thuộc tính **mỗi JobKey**, persistence là khả năng của **JobStore**, còn cluster là sự phối hợp **nhiều scheduler qua cùng JDBC JobStore**. Sự đúng đắn cuối cùng của nghiệp vụ vẫn cần transaction, khóa và idempotency thích hợp.

## Tài liệu tham khảo chính thức

- [Spring Boot — Quartz Scheduler](https://docs.spring.io/spring-boot/3.5/reference/io/quartz.html): auto-configuration, JDBC, khởi tạo schema, `@QuartzDataSource`, `overwrite-existing-jobs`.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): phân biệt `TaskExecutor`/`TaskScheduler` và tích hợp Quartz qua `SchedulerFactoryBean`.
- [Quartz — `@DisallowConcurrentExecution` JavaDoc](https://www.quartz-scheduler.org/api/2.5.x/org/quartz/DisallowConcurrentExecution.html): phạm vi khóa theo JobDetail/JobKey.
- [Quartz — JDBC JobStoreTX configuration](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/configuration/ConfigJobStoreTX.html): `isClustered`, `clusterCheckinInterval`, `misfireThreshold`, `useProperties`.
- [Quartz — Clustering](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/configuration/ConfigJDBCJobStoreClustering.html): chia tải, failover, định danh node và đồng bộ đồng hồ; đối chiếu theo phiên bản Quartz đang dùng.


</details>

- [Quay lại đầu trang](#back-to-top)
