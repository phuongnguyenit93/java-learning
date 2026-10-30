<a id="back-to-top"></a>

# Giao dịch và sự kiện

## Menu
- [Giao dịch thông thường gắn với luồng không tự truyền vào tác vụ @Async](#transactions-events-001)
- [Tạo ranh giới giao dịch mới trong bean xử lý khi nghiệp vụ yêu cầu](#transactions-events-002)
- [Thời điểm commit của bên gửi và nguy cơ bên nhận đọc dữ liệu chưa hoàn tất](#transactions-events-003)
- [Không truyền EntityManager hoặc phiên ORM sang luồng xử lý khác](#transactions-events-004)
- [Truyền mã định danh hoặc dữ liệu bất biến thay cho thực thể đang gắn phiên](#transactions-events-005)
- [@TransactionalEventListener cho xử lý gắn với kết quả giao dịch](#transactions-events-006)
- [Sự kiện nội bộ bất đồng bộ không thay thế hàng đợi bền vững](#transactions-events-007)
- [Không giả định hai tác vụ trên hai luồng có thể rollback cùng nhau](#transactions-events-008)

## <a id="transactions-events-001">Giao dịch thông thường gắn với luồng không tự truyền vào tác vụ @Async</a>

<details>
<summary>Xem chi tiết</summary>

# Giao dịch thông thường gắn với luồng không tự truyền vào tác vụ @Async

## 1. Hiện tượng: caller có @Transactional nhưng worker không cùng transaction

Trong một ứng dụng Spring, ta có thể tạo dữ liệu trong một phương thức `@Transactional` rồi gửi một công việc khác bằng `@Async`:

```java
@Transactional
public void registerOrder(long orderId) {
    orderRepository.save(new Order(orderId));
    mailWorker.sendConfirmation(orderId); // Method có @Async ở bean khác.
}
```

Đây là **fragment minh họa**: `Order`, `orderRepository` và `mailWorker` là thành phần giả định của ứng dụng. Nhiều người nghĩ rằng vì `sendConfirmation()` được gọi *bên trong* một method `@Transactional`, nó sẽ tham gia vào transaction đang mở của `registerOrder()`. Trong mô hình giao dịch imperative thường gặp của Spring, suy luận đó **sai**.

Khi gọi qua Spring async proxy, `@Async` chuyển công việc sang một worker thread. Transaction của caller được gắn với **thread thực thi lời gọi giao dịch**; nó không được sao chép nguyên vẹn sang worker mới. Kết quả có thể là:

- Caller đang có transaction, nhưng trong worker `TransactionSynchronizationManager.isActualTransactionActive()` trả `false` nếu worker không tự mở transaction.
- Worker dùng một kết nối JDBC khác, không nhìn thấy bản ghi caller vừa insert nhưng **chưa commit**, ở mức isolation như `READ_COMMITTED`.
- Nếu worker có `@Transactional` riêng, nó có thể mở **một transaction độc lập** trên worker; đó **không phải** transaction của caller.
- Worker có thể gửi email hoặc ghi dữ liệu của chính nó thành công trong khi transaction caller **sau đó rollback**.
- Nếu caller đợi Future của worker trước khi commit, có thể tạo phụ thuộc thời điểm hoặc thậm chí bế tắc nghiệp vụ khi worker cố chờ dữ liệu chưa được commit.

**Quy tắc nền tảng:** `@Transactional` trong imperative Spring là ngữ cảnh giao dịch **thread-bound**. `@Async` thường đổi execution thread. Việc đổi thread **không tự mang theo transaction** của caller.

## 2. Giao dịch imperative của Spring gắn với thread như thế nào?

### 2.1. Transaction manager và resource binding

Trong JDBC, Spring thường dùng `PlatformTransactionManager` như `DataSourceTransactionManager`. Khi một lời gọi đi qua transactional interceptor:

1. Transaction manager lấy/bắt đầu transaction theo propagation và tình trạng hiện tại.
2. Spring gắn thông tin giao dịch và tài nguyên phù hợp — thường gồm `ConnectionHolder` cho `DataSource` — vào thread hiện tại thông qua cơ chế của `TransactionSynchronizationManager`.
3. Các lời gọi `JdbcTemplate`/DAO tham gia transaction khi chạy trên thread đó và dùng đúng resource/transaction manager.
4. Khi lời gọi transactional hoàn tất, Spring commit hoặc rollback theo quy tắc, sau đó dọn những resource bindings tương ứng.

Tính chất thread-bound giúp các repository/DAO khác nhau tham gia cùng một transaction mà không phải truyền `Connection` qua mọi tham số Java. Nhưng nó cũng tạo ra **ranh giới thread**: một task chạy ở thread khác không có các binding đó theo mặc định.

```text
Thread HTTP / caller                         Worker thread (executor)
--------------------                         ------------------------
@Transactional proxy begins T1
  |
  +-- bind T1 connection to caller thread
  |
  +-- JDBC INSERT row #1 (uncommitted)
  |
  +-- invoke @Async proxy ----------------> submit task to executor
  |                                         |
  |                                         +-- worker begins
  |                                         +-- no caller T1 binding
  |                                         +-- JDBC query via own connection
  |                                         +-- might NOT see uncommitted #1
  |
  +-- continue and commit/rollback T1
  |
  +-- unbind caller transaction
```

Quan sát chính xác: worker có thể nhận được các giá trị thông thường như `orderId` vì chúng được truyền như arguments, nhưng **không nhận được quyền sở hữu transaction/connection đang mở** chỉ nhờ việc truyền arguments.

### 2.2. TransactionSynchronizationManager kiểm tra được gì?

Hai method chẩn đoán thường gặp:

- `TransactionSynchronizationManager.isActualTransactionActive()`: báo một actual transaction đã được đánh dấu active trên thread hiện tại.
- `TransactionSynchronizationManager.isSynchronizationActive()`: báo transaction synchronization đang được bật cho thread; không hoàn toàn đồng nghĩa với một transaction vật lý đang active.

Trong ví dụ, dùng `isActualTransactionActive()` vì muốn so sánh **caller đang trong transaction** với **worker không kế thừa transaction**. Đây là tín hiệu hữu ích, nhưng không thay thế kiểm tra transaction manager, connection, isolation và dữ liệu thực tế.

**Đừng truyền hoặc ép gắn trực tiếp Connection/TransactionSynchronizationManager state từ caller vào worker** để “chia sẻ transaction”. Một JDBC `Connection`/Hibernate `Session` thường không được thiết kế để hai thread thao tác đồng thời; việc cưỡng ép chia sẻ tài nguyên có thể gây race, vi phạm lifecycle và consistency.

### 2.3. Ngoại lệ: reactive transaction không dùng cùng cơ chế

Với `ReactiveTransactionManager`, Spring quản lý transaction bằng **Reactor Context**, không theo `ThreadLocal` của mô hình imperative `PlatformTransactionManager`. Cách suy luận và propagation khi đó theo reactive pipeline/context, không thể lấy quy tắc “mọi giao dịch trong Spring đều thread-bound” để áp cho reactive.

Bài này tập trung vào **Spring imperative transaction + @Async + JDBC/JPA**. Khi đọc tài liệu reactive, cần phân biệt hai mô hình giao dịch.

## 3. @Async thực sự tạo ra ranh giới nào?

Spring hỗ trợ `@Async` thông qua async advice và executor. Ở cấu hình proxy mặc định:

- Có `@EnableAsync` (hoặc `<task:annotation-driven>`) thì Spring đăng ký hạ tầng xử lý annotation.
- Phương thức phải nằm trên bean do Spring quản lý, với lời gọi phù hợp đi **qua proxy**.
- Async advice submit invocation vào executor, chẳng hạn `ThreadPoolTaskExecutor`.
- Phần thân method đích chạy trên worker được executor cung cấp. Khi đó, transaction binding của **thread gọi ban đầu không tự xuất hiện** trên worker.

Cần đặc biệt phân biệt ba tình huống:

| Tình huống | Có đổi thread không? | Transaction của caller có tự được kế thừa không? |
| --- | --- | --- |
| Caller gọi method `@Async` trên **bean khác qua proxy** và executor thực sự bất đồng bộ | Có | Không |
| Caller tự gọi `this.asyncMethod()` trong cùng target ở proxy mode | Thường không: bypass async advice | Code chạy đồng bộ trên chính caller, nên có thể *thấy transaction caller*; không chứng minh propagation hoạt động |
| Executor thực tế là `SyncTaskExecutor`, thực thi ngay tại thread gọi | Không | Có thể nhìn thấy thread-bound transaction đang tồn tại, vì không có thread switch; đó không phải async worker độc lập |

Đây là lý do không nên chẩn đoán transaction propagation chỉ từ việc có hay không có annotation `@Async`. Phải xác nhận **call path**, **executor thực tế**, **thread name** và **transaction state**.

## 4. Thời điểm commit là phần quyết định dữ liệu worker nhìn thấy

Giả sử caller đang làm:

```text
T1 begins
  INSERT order id=1  (chưa commit)
  call worker.inspect(1) via @Async
  worker queries by id=1
  T1 commits after @Transactional method returns
```

Nếu DB dùng isolation `READ_COMMITTED`, worker đọc qua một transaction/connection độc lập thường **không nhìn thấy** row vừa được insert bởi T1 trước commit.

Hai điều cần hiểu:

1. `@Async` không tạo một lời hứa “chạy **sau commit**”. Worker có thể bắt đầu trước commit, sau commit hoặc khi caller đang chuẩn bị rollback — tùy scheduling.
2. Kết quả đọc dữ liệu chưa commit phụ thuộc isolation, DB engine và loại operation. Không suy rằng **mọi cơ sở dữ liệu, mọi isolation** đều cho cùng kết quả; ví dụ này cố ý dùng H2 `READ_COMMITTED` mặc định để minh họa không thấy row chưa commit.

Một race rất hay gặp: công việc async dựa vào bản ghi vừa được lưu trong caller. Trên máy phát triển, worker thỉnh thoảng chạy sau commit và mọi thứ “có vẻ đúng”; ở production, worker nhanh hơn và query trả rỗng. Việc thêm `Thread.sleep(100)` trong worker chỉ che race, không làm giao dịch có ràng buộc chính xác.

### 4.1. Thời gian chạy không đồng nghĩa thứ tự commit

Ngay cả khi method caller gọi `asyncService.run()` *sau* `repository.save()` trong cùng mã nguồn, transaction của caller có thể vẫn chưa commit cho tới khi method transactional **trả về qua proxy**. Bởi vậy thứ tự câu lệnh Java không bảo đảm worker nhìn được dữ liệu từ transaction đó.

### 4.2. Wait Future trong transaction có thể làm tình huống tệ hơn

Nếu caller đang giữ transaction mở và lập tức gọi `future.get()` để đợi worker mà worker lại chờ dữ liệu caller commit, hệ thống có thể tiến triển rất chậm hoặc bế tắc:

```text
Caller: holds T1 -> waits worker Future -> only after method returns can T1 commit
Worker: waits for data/lock held by T1 -> cannot finish Future
```

Không nên xử lý vấn đề này bằng cách tăng thread pool vô hạn. Cần sửa **ranh giới transaction**, thứ tự phát sự kiện/đẩy việc và cách task nhận dữ liệu.

## 5. Worker không có transaction khác với worker có transaction riêng

Có hai khả năng hoàn toàn khác:

### 5.1. Worker @Async không khai báo @Transactional

```java
@Async("workerExecutor")
public CompletableFuture<Integer> countRows(int id) {
    int count = jdbc.queryForObject(
            "select count(*) from items where id = ?",
            Integer.class, id);
    return CompletableFuture.completedFuture(count);
}
```

Đây là fragment trên Spring-managed bean, đã có `JdbcTemplate jdbc` và executor. Trong worker, `isActualTransactionActive()` có thể là false. JDBC query vẫn thực thi được bằng connection riêng, thường ở auto-commit behavior của JDBC/driver. Không có nghĩa mọi thao tác đều tự động “an toàn” hoặc đang cùng transaction caller.

### 5.2. Worker @Async cũng có @Transactional

```java
@Async("workerExecutor")
@Transactional(readOnly = true)
public CompletableFuture<Integer> countRowsInOwnTransaction(int id) {
    int count = jdbc.queryForObject(
            "select count(*) from items where id = ?",
            Integer.class, id);
    return CompletableFuture.completedFuture(count);
}
```

Khi lời gọi đi qua advice đúng cách, task chạy trên worker và transaction advice có thể mở **transaction mới trên worker đó**. Nó có transaction state active, nhưng **vẫn không tham gia transaction chưa commit của caller**.

Đừng diễn giải `Propagation.REQUIRED` là “tìm đến transaction của bất kỳ thread nào gọi mình”. `REQUIRED` chỉ tham gia một transaction phù hợp **đang tồn tại trong ngữ cảnh thực thi hiện tại**; nếu worker không có transaction thread-bound, nó sẽ bắt đầu transaction mới.

Với `Propagation.REQUIRES_NEW` trong worker, nó cũng bắt đầu transaction độc lập; không cần dùng `REQUIRES_NEW` chỉ để ép tạo transaction cho mọi phương thức async trong trường hợp worker không có transaction từ trước. Ý nghĩa suspend một transaction hiện hữu chỉ liên quan khi **thread hiện tại** thực sự có transaction mà propagation sẽ xử lý.

### 5.3. Hai transaction độc lập có thể cho kết quả khác nhau

Nếu worker tự lưu một row và commit transaction của worker, còn caller sau đó rollback, row của worker có thể **vẫn tồn tại**. Ngược lại, worker đọc dữ liệu caller chưa commit vẫn có thể không thấy dù chính worker đang ở trong transaction mới.

Vì vậy, thêm `@Transactional` vào worker không phải cách “truyền” transaction ban đầu. Nó chỉ quyết định ranh giới transaction cho công việc của worker.

## 6. Ví dụ runnable: Spring @Transactional + @Async + H2 kiểm chứng sự khác nhau

Ví dụ dùng **Java 21, Spring Framework 6.2.1, Spring JDBC, H2 2.3.232 và Maven**. Không cần Spring Boot.

Nó minh họa đồng thời:

1. Method caller `@Transactional` insert một hàng rồi **chưa commit**.
2. `@Async` worker không khai báo transaction đọc hàng đó: worker không có transaction của caller và không nhìn thấy dữ liệu chưa commit.
3. Worker `@Async @Transactional(readOnly = true)` có **transaction riêng**, nhưng cũng không thấy row chưa commit.
4. Sau khi caller commit, một listener `@TransactionalEventListener(phase=AFTER_COMMIT)` kết hợp `@Async` chạy và nhìn thấy row đã commit.
5. Khi caller ném exception và rollback, row đó không tồn tại, listener `AFTER_COMMIT` cho sự kiện trong transaction rollback không được gọi.

Để tránh đoán bằng `Thread.sleep()`, chương trình gọi `Future.get(timeout)` cho hai truy vấn của worker và dùng `CountDownLatch` để đợi listener sau commit.

### 6.1. Cấu trúc

```text
tx-async-demo/
├── pom.xml
└── src/main/java/demo/TxAsyncDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>tx-async-demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.1</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-jdbc</artifactId>
      <version>6.2.1</version>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <version>2.3.232</version>
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
          <mainClass>demo.TxAsyncDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

**Tệp `src/main/java/demo/TxAsyncDemo.java`:**

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TxAsyncDemo {
    public record Observation(boolean txActive, int visibleRows, String thread) {}
    public record Comparison(boolean callerTxActive, Observation asyncNoTx,
                             Observation asyncOwnTx) {}
    public record ItemCreated(int id) {}

    public static class Reader {
        private final JdbcTemplate jdbc;

        public Reader(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Async("workerExecutor")
        public CompletableFuture<Observation> withoutTransaction(int id) {
            return CompletableFuture.completedFuture(sample(id));
        }

        @Async("workerExecutor")
        @Transactional(readOnly = true)
        public CompletableFuture<Observation> withOwnTransaction(int id) {
            return CompletableFuture.completedFuture(sample(id));
        }

        private Observation sample(int id) {
            int count = jdbc.queryForObject(
                    "select count(*) from items where id = ?",
                    Integer.class, id);
            return new Observation(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    count,
                    Thread.currentThread().getName());
        }
    }

    public static class Writer {
        private final JdbcTemplate jdbc;
        private final Reader reader;
        private final ApplicationEventPublisher publisher;

        public Writer(JdbcTemplate jdbc, Reader reader,
                      ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.reader = reader;
            this.publisher = publisher;
        }

        @Transactional
        public Comparison insertAndObserve() throws Exception {
            jdbc.update("insert into items(id, label) values (?, ?)", 1, "one");
            boolean callerTx =
                    TransactionSynchronizationManager.isActualTransactionActive();

            Observation noTx = reader.withoutTransaction(1)
                    .get(5, TimeUnit.SECONDS);
            Observation ownTx = reader.withOwnTransaction(1)
                    .get(5, TimeUnit.SECONDS);

            publisher.publishEvent(new ItemCreated(1));
            return new Comparison(callerTx, noTx, ownTx);
        }

        @Transactional
        public void insertAndRollback() {
            jdbc.update("insert into items(id, label) values (?, ?)", 2, "two");
            publisher.publishEvent(new ItemCreated(2));
            throw new IllegalStateException("demo rollback");
        }
    }

    public static class AfterCommitListener {
        private final JdbcTemplate jdbc;
        private final CountDownLatch committedEvent = new CountDownLatch(1);
        private final AtomicInteger observedAfterCommit = new AtomicInteger(-1);
        private final AtomicInteger unexpectedRollbackEvent = new AtomicInteger();
        private final AtomicReference<String> listenerThread =
                new AtomicReference<>("");

        public AfterCommitListener(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Async("workerExecutor")
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onCreated(ItemCreated event) {
            if (event.id() == 1) {
                observedAfterCommit.set(jdbc.queryForObject(
                        "select count(*) from items where id = ?",
                        Integer.class, event.id()));
                listenerThread.set(Thread.currentThread().getName());
                committedEvent.countDown();
            } else if (event.id() == 2) {
                unexpectedRollbackEvent.incrementAndGet();
            }
        }

        public boolean awaitCommitted() throws InterruptedException {
            return committedEvent.await(5, TimeUnit.SECONDS);
        }

        public int observedCount() {
            return observedAfterCommit.get();
        }

        public int rollbackEventCount() {
            return unexpectedRollbackEvent.get();
        }

        public String listenerThread() {
            return listenerThread.get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    public static class Config {
        @Bean
        public DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:tx_async_demo;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean("workerExecutor")
        public ThreadPoolTaskExecutor workerExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(2);
            pool.setMaxPoolSize(2);
            pool.setQueueCapacity(8);
            pool.setThreadNamePrefix("tx-worker-");
            return pool;
        }

        @Bean
        public Reader reader(JdbcTemplate jdbc) {
            return new Reader(jdbc);
        }

        @Bean
        public Writer writer(JdbcTemplate jdbc, Reader reader,
                             ApplicationEventPublisher publisher) {
            return new Writer(jdbc, reader, publisher);
        }

        @Bean
        public AfterCommitListener afterCommitListener(JdbcTemplate jdbc) {
            return new AfterCommitListener(jdbc);
        }
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Transaction/thread behavior not as expected");
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Writer writer = context.getBean(Writer.class);
            AfterCommitListener listener =
                    context.getBean(AfterCommitListener.class);

            jdbc.execute("create table items(id int primary key, label varchar(50))");
            Comparison c = writer.insertAndObserve();

            int committed = jdbc.queryForObject(
                    "select count(*) from items where id = 1", Integer.class);
            require(listener.awaitCommitted());

            boolean rolledBack = false;
            try {
                writer.insertAndRollback();
            } catch (IllegalStateException expected) {
                rolledBack = true;
            }

            int afterRollback = jdbc.queryForObject(
                    "select count(*) from items where id = 2", Integer.class);

            boolean onWorker = c.asyncNoTx().thread().startsWith("tx-worker-")
                    && c.asyncOwnTx().thread().startsWith("tx-worker-");
            boolean eventOnWorker = listener.listenerThread().startsWith("tx-worker-");

            require(c.callerTxActive());
            require(!c.asyncNoTx().txActive());
            require(c.asyncNoTx().visibleRows() == 0);
            require(c.asyncOwnTx().txActive());
            require(c.asyncOwnTx().visibleRows() == 0);
            require(committed == 1 && listener.observedCount() == 1);
            require(rolledBack && afterRollback == 0);
            require(listener.rollbackEventCount() == 0);
            require(onWorker && eventOnWorker);

            System.out.println("callerTxActive=" + c.callerTxActive());
            System.out.println("asyncTxActive=" + c.asyncNoTx().txActive());
            System.out.println("asyncSeesUncommitted=" +
                    (c.asyncNoTx().visibleRows() > 0));
            System.out.println("ownAsyncTxActive=" + c.asyncOwnTx().txActive());
            System.out.println("ownAsyncSeesUncommitted=" +
                    (c.asyncOwnTx().visibleRows() > 0));
            System.out.println("committedRowVisible=" + (committed == 1));
            System.out.println("afterCommitEventVisible=" +
                    (listener.observedCount() == 1));
            System.out.println("rollbackRowVisible=" + (afterRollback > 0));
            System.out.println("rollbackEventDelivered=" +
                    (listener.rollbackEventCount() > 0));
            System.out.println("workerThreadUsed=" + onWorker);
            System.out.println("listenerWorkerThreadUsed=" + eventOnWorker);
        }
    }
}
```

**Vì sao dùng `DriverManagerDataSource`?** Đây là lựa chọn **chỉ phục vụ demo**: mỗi lần `getConnection()` có thể tạo một connection mới. Production thường dùng connection pool như HikariCP và cấu hình timeout/size phù hợp. H2 in-memory trong ví dụ dùng URL `DB_CLOSE_DELAY=-1` để giữ database khi các connection lần lượt được đóng trong lifetime của JVM.

### 6.2. Lệnh chạy và stdout đã kiểm chứng

Cần Maven và JDK 21 trở lên. Từ thư mục `tx-async-demo`:

```bash
mvn -q -DskipTests compile exec:java
```

**Stdout đã biên dịch/chạy thực tế:**

```text
callerTxActive=true
asyncTxActive=false
asyncSeesUncommitted=false
ownAsyncTxActive=true
ownAsyncSeesUncommitted=false
committedRowVisible=true
afterCommitEventVisible=true
rollbackRowVisible=false
rollbackEventDelivered=false
workerThreadUsed=true
listenerWorkerThreadUsed=true
```

Chương trình in các boolean thay vì in thứ tự tên thread cố định; thứ tự thực tế của worker có thể thay đổi theo scheduling. Test có các assertions; nếu hành vi lệch khỏi kỳ vọng, chương trình báo lỗi thay vì chỉ in kết quả gây hiểu nhầm.

### 6.3. Diễn giải từng phần của output

| Dòng output | Ý nghĩa |
| --- | --- |
| `callerTxActive=true` | Method `Writer.insertAndObserve()` thực sự chạy trong transaction caller. |
| `asyncTxActive=false` | Method `Reader.withoutTransaction()` chạy trên worker, không có transaction của caller. |
| `asyncSeesUncommitted=false` | Query của worker chưa thấy row 1 do transaction caller chưa commit ở isolation demo. |
| `ownAsyncTxActive=true` | Method `Reader.withOwnTransaction()` đã mở transaction trên worker. |
| `ownAsyncSeesUncommitted=false` | Transaction riêng vẫn không nhìn thấy bản ghi chưa commit của transaction caller. |
| `committedRowVisible=true` | Sau khi method transactional hoàn tất và commit, row 1 thấy được từ query mới. |
| `afterCommitEventVisible=true` | Listener phase AFTER_COMMIT, chạy async trên pool, quan sát được row đã commit. |
| `rollbackRowVisible=false` | Transaction insert row 2 ném exception và rollback nên row 2 không tồn tại. |
| `rollbackEventDelivered=false` | Event row 2 gắn với transaction đã rollback không kích hoạt listener AFTER_COMMIT. |
| `workerThreadUsed=true` | Hai lần đọc worker đều chạy trên thread pool prefix `tx-worker-`. |
| `listenerWorkerThreadUsed=true` | Listener phase AFTER_COMMIT cũng được xử lý trên worker thread. |

**Giới hạn của ví dụ:** H2 default transaction isolation trong kịch bản là `READ_COMMITTED`; các truy vấn được điều phối để chạy trước caller commit, nên hai dòng về “không thấy uncommitted” là kết quả ổn định của demo, không phải lời khẳng định độc lập với mọi database/isolation.

## 7. Cơ chế @Transactional và @Async trong ví dụ: hai proxy boundary

Bản demo khai báo:

```java
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableTransactionManagement
public static class Config {
    // Beans: DataSource, transactionManager, workerExecutor,
    // Reader, Writer và AfterCommitListener.
}
```

Cùng một ApplicationContext đã đăng ký xử lý cả `@Transactional` và `@Async`. Nhưng **không có nghĩa** một transaction xuyên qua các threads.

- `Writer.insertAndObserve()` được gọi từ `main` qua Spring-managed bean `Writer`. Transaction interceptor mở transaction T1 trên caller thread.
- `Writer` gọi `Reader.withoutTransaction()` và `Reader.withOwnTransaction()` thông qua dependency injected từ container. Async interceptor submit task sang `workerExecutor`.
- Method `withoutTransaction()` chạy trên worker, không có T1. Nó query DB bằng connection khác.
- Method `withOwnTransaction()` chạy trên worker; transaction interceptor mở T2 riêng trong ngữ cảnh thread worker. Nó query DB theo mức isolation của T2.
- Sau khi hai Future trả về và `insertAndObserve()` kết thúc, T1 mới commit. Bởi vậy hai worker queries được bảo đảm xảy ra trước commit trong demo.

**Tách bean là quan trọng.** Nếu `Writer` tự chứa một method `@Async` và gọi bằng `this.workerMethod()`, lời gọi có thể bypass proxy ở chế độ mặc định và chạy đồng bộ trên caller. Khi đó test có thể báo transaction active=true trong “worker method” chỉ vì **không hề chuyển thread**. Đừng coi đó là transaction propagation.

## 8. Điều gì xảy ra nếu worker cần ghi dữ liệu?

Nếu một công việc bất đồng bộ cần cập nhật database, có hai mô hình khác nhau về ý nghĩa nghiệp vụ.

### 8.1. Worker tự mở transaction

Dùng một Spring-managed bean khác với method:

```java
@Async("workerExecutor")
@Transactional
public CompletableFuture<Void> updateProjection(long orderId) {
    jdbc.update("update projections set status = ? where order_id = ?",
                "READY", orderId);
    return CompletableFuture.completedFuture(null);
}
```

Đây là fragment, đòi hỏi bean thực tế có `JdbcTemplate jdbc` và schema tương ứng. `@Transactional` ở worker có thể tạo transaction riêng. Giao dịch này commit/rollback theo kết quả thực thi của worker, **không phụ thuộc một cách tự động** vào transaction đang mở của caller.

Nếu caller transaction sau đó rollback mà worker đã commit một side effect khác, database có thể tồn tại một trạng thái mà nghiệp vụ không muốn. Việc đặt cả hai annotation trên một method không làm chúng cùng chia sẻ T1.

### 8.2. Cần cùng một transaction nguyên tử

Nếu hai thao tác DB buộc phải commit/rollback **cùng nhau**, hãy thực thi chúng trên **cùng transaction boundary** theo mô hình imperative đồng bộ, ví dụ đưa chúng vào một application-service method transactional hoặc gọi service transactional khác đồng bộ trong cùng thread và resource context.

Không dùng `@Async` rồi kỳ vọng có một transaction ACID trải qua nhiều threads. Nếu cần phân phối công việc, hãy chọn semantics eventual consistency, outbox, saga hoặc cơ chế distributed coordination phù hợp — đây là thiết kế khác với một local JDBC transaction.

### 8.3. Sử dụng REQUIRES_NEW không phải “kết nối lại” T1

Một method worker có `@Transactional(propagation = Propagation.REQUIRES_NEW)` mở transaction độc lập tại **worker thread**. Nó không thể nhìn thấy dữ liệu uncommitted của caller bằng cách “đặt propagation mạnh hơn”. Nếu thread worker không có transaction đang active, `REQUIRED` cũng sẽ tạo transaction mới; `REQUIRES_NEW` không cần thiết chỉ để tránh kế thừa T1 vốn không có.

## 9. Tránh đọc trước commit bằng sự kiện gắn với transaction

Một nhu cầu phổ biến: “Sau khi tạo order thành công, gửi email/refresh cache/cập nhật search index”. Thay vì gọi worker ngay khi transaction đang mở, có thể **phát sự kiện trong transaction** rồi **đợi đến phase sau commit** mới xử lý.

Spring có `@TransactionalEventListener`. Ví dụ:

```java
@Transactional
public void createOrder(long id) {
    jdbc.update("insert into orders(id) values (?)", id);
    applicationEventPublisher.publishEvent(new OrderCreated(id));
}
```

Đây là fragment, giả sử `applicationEventPublisher` được inject và `OrderCreated` là một record/event type có thật. Khi có listener:

```java
@Async("workerExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCreated(OrderCreated event) {
    // Có thể tra cứu row đã commit rồi thực hiện hậu xử lý.
}
```

Đây là fragment trên Spring-managed listener bean; cần `@EnableAsync`, proper transaction management và executor. Với `AFTER_COMMIT`, listener được kích hoạt sau khi transaction phát sự kiện **commit thành công**. Khi transaction rollback, listener `AFTER_COMMIT` không xử lý event đó.

**Điểm mạnh:** tránh race “async đọc dữ liệu trước commit” và tránh phát side effect sau một transaction đã rollback trong đường thực thi bình thường.

**Điểm cần cẩn trọng:** `@TransactionalEventListener` không phải một **message broker bền vững**. Nếu JVM chết sau commit nhưng trước khi async listener xử lý xong, event trong bộ nhớ có thể bị mất.

### 9.1. phase và fallbackExecution

Các phase gồm:

- `BEFORE_COMMIT`: trước commit, thích hợp cho một số logic cần chạy khi transaction chuẩn bị hoàn tất, nhưng không dùng làm mốc dữ liệu đã commit.
- `AFTER_COMMIT`: sau commit thành công (mặc định).
- `AFTER_ROLLBACK`: khi transaction rollback.
- `AFTER_COMPLETION`: khi transaction hoàn tất, bất kể kết quả.

Nếu phát event **không nằm trong transaction đang active**, listener transactional mặc định **không được gọi**, trừ khi bật `fallbackExecution = true`. Vì vậy không giả định mọi `publishEvent(...)` đều kích hoạt một `@TransactionalEventListener` trong mọi hoàn cảnh.

### 9.2. Đừng viết DB ở afterCommit callback cũ mà không nghĩ về transaction mới

Một callback `AFTER_COMMIT` đồng bộ có thể chạy trong giai đoạn transaction completion khi resource gắn với caller thread chưa được cleanup hết. Nếu callback tự ghi DB, cần thận trọng với resource/transaction state; nếu buộc ghi một transaction mới, hãy mở ranh giới phù hợp như `REQUIRES_NEW` trong đúng execution context.

Trong demo, listener có `@Async` để chạy **trên worker khác** sau phase commit và chỉ đọc row đã commit. Nếu listener async cũng cần ghi DB, nên cấu hình transaction mới cho công việc ghi khi cần. Không dựa vào connection cũ của caller sau commit.

## 10. Khi yêu cầu là chắc chắn xử lý hậu kỳ: Transactional Outbox

`@TransactionalEventListener(AFTER_COMMIT)` giải bài toán **đúng thứ tự commit** trong một tiến trình đang sống. Nhưng nó không giải quyết yêu cầu “DB đã commit thì việc gửi email/event bắt buộc phải được giao, kể cả khi process chết ngay sau commit”.

Đối với business task quan trọng, cân nhắc **transactional outbox**:

1. Trong **cùng transaction T1**, ghi entity nghiệp vụ và một bản ghi outbox về sự kiện cần xử lý.
2. T1 commit **nguyên tử** cả hai bản ghi.
3. Một relay/worker khác đọc các outbox records đã commit và gửi đến message broker/destination.
4. Sau khi nhận acknowledgement phù hợp, đánh dấu outbox record đã xử lý; nếu thất bại, retry có kiểm soát.
5. Consumer dùng idempotency/deduplication vì hệ thống có thể gửi lại ít nhất một lần.

```text
Caller @Transactional
  |
  +-- INSERT orders
  +-- INSERT outbox_event
  +-- COMMIT (atomic)
             |
             v
       Outbox relay
             |
             +-- publish to broker / durable job system
             |
             v
       Consumer / background worker
             |
             +-- dedup + business processing + status
```

Trong mô hình này, worker không cần thừa hưởng T1. Nó làm việc sau khi dữ liệu và thông điệp outbox đã commit; tính nhất quán được đảm bảo bằng **điều phối nghiệp vụ và lưu trữ**, không bằng cách kéo một JDBC transaction qua thread boundary.

Không tự tuyên bố “exactly once” chỉ nhờ outbox; cần phân tích retry, dedup, idempotency và giao thức nhận/ghi nhận kết quả.

## 11. Exception, rollback và Future trong tác vụ async

### 11.1. Lỗi caller transaction

Nếu method `@Transactional` đồng bộ ném một unchecked exception và exception đi qua transactional proxy, Spring thường đánh dấu rollback/rollback theo quy tắc mặc định. Trong ví dụ `insertAndRollback()` ném `IllegalStateException`; row 2 bị rollback.

Không suy rằng **mọi exception** đều mặc định rollback: checked exceptions có quy tắc khác tùy cấu hình (ví dụ `rollbackFor`), transaction manager và chính sách ứng dụng.

### 11.2. Lỗi trong worker không tự rollback transaction caller

Nếu worker bất đồng bộ ném exception **sau khi caller đã commit**, lỗi đó không thể tự quay lại và rollback transaction T1 đã kết thúc. Kể cả khi caller còn giữ T1 mở, một lỗi được worker lưu vào `Future` không tự động bị ném trên caller nếu caller không `get()` và xử lý.

Phân biệt:

- Lỗi **lúc submit**: pool/queue đầy có thể ném `TaskRejectedException` ngay tại caller.
- Lỗi **trong worker**: với method `@Async` trả `Future`/`CompletableFuture`, caller có thể quan sát bằng `get()`/completion handler.
- Method async trả `void`: caller không có Future để lấy lỗi; cần `AsyncUncaughtExceptionHandler`/logging/monitoring phù hợp.
- Lỗi **trong listener AFTER_COMMIT**: commit caller đã xong; xử lý thất bại và retry cần cơ chế riêng.

Nếu công việc quan trọng, phải có nơi theo dõi task ID, trạng thái cuối, retry, alert và nguồn dữ liệu khôi phục khi restart; không chỉ gọi `@Async` rồi bỏ qua.

### 11.3. Không để caller chờ worker trong cùng transaction chỉ để nhận lỗi

Gọi `future.get()` trong T1 có thể giúp caller thấy lỗi task, nhưng tăng thời gian giữ connection/locks và không biến T1 với worker transaction thành một transaction chung. Đặc biệt nguy hiểm nếu worker cần nhìn thấy dữ liệu chỉ được commit **sau khi T1 kết thúc**.

Hãy xác định rõ orchestration: việc nào thuộc transaction T1 đồng bộ, việc nào được giao xử lý sau commit với trạng thái độc lập.

## 12. Thread-local, MDC, SecurityContext và JPA

Câu chuyện transaction là một ví dụ của **thread context**. Khi đổi thread, nhiều loại context khác cũng có thể không tự truyền:

- `TransactionSynchronizationManager`: transaction/resource binding của imperative Spring.
- Logging `MDC`: correlation ID/log fields dùng `ThreadLocal`.
- `SecurityContextHolder`: tùy chiến lược lưu security context; không mặc nhiên được copy an toàn sang worker.
- Request-scoped context/HTTP request attributes: có thể không còn hợp lệ khi worker chạy sau response.
- JPA `EntityManager`/Hibernate `Session`: persistence context gắn với transaction/thread; không chia sẻ mutable entity/session tùy tiện giữa threads.

Một `TaskDecorator` hoặc context-propagation library có thể sao chép **metadata phù hợp** như correlation ID hoặc một security context đã được xử lý an toàn. Tuy nhiên **không dùng TaskDecorator để copy raw transaction connection/Session sang worker**. Truyền ID/immutable DTO, mở transaction độc lập đúng cách trong worker, rồi đo trạng thái kết quả.

Khi sửa self-invocation để thực sự chạy async, một method trước đây vô tình dùng được transaction/MDC của caller sẽ không còn thấy chúng. Đây không phải bug của worker mà là sự xuất hiện của thread boundary vốn trước đó chưa có.

## 13. Kiểm thử không phụ thuộc may rủi về scheduling

Trường hợp “worker không thấy bản ghi mới” có thể không tái hiện nếu worker chạy quá muộn và caller đã commit. Vì vậy, **test phải kiểm soát thời điểm**.

Trong ví dụ hoàn chỉnh:

- `Writer.insertAndObserve()` giữ transaction caller đang active.
- Caller **đợi hai Future** của worker hoàn tất trước khi method transactional return.
- Vì commit diễn ra khi `insertAndObserve()` hoàn tất qua proxy, hai worker queries chắc chắn được thực hiện **trước T1 commit**.
- Assertions đo cả trạng thái transaction của worker và kết quả JDBC query, không chỉ xem tên thread.
- Sau khi method trả, query mới thấy row đã commit.
- Listener `AFTER_COMMIT` có `CountDownLatch` để main đợi completion thay vì ngủ với thời lượng đoán trước.
- Một method khác phát event rồi ném unchecked exception để kiểm chứng rollback: row và listener tương ứng không xuất hiện.

**Lưu ý về test harness:** Việc chờ worker bên trong T1 là thiết kế phục vụ **chứng minh isolation**, không phải lời khuyên thiết kế workflow nghiệp vụ như vậy. Production nên tránh giữ transaction mở để chờ các tác vụ async cần đọc lại dữ liệu chưa commit.

### 13.1. Integration test cần có transaction manager thực

Một unit test chỉ mock `JdbcTemplate` hoặc gọi method trực tiếp bằng `new Reader(...)` không chứng minh transaction binding qua Spring proxy. Muốn kiểm tra thread/commit/isolation, phải chạy với Spring context, transaction manager và database/driver thực phù hợp.

### 13.2. Đừng dùng test mặc định @Transactional rồi suy ra production

Nhiều test framework chạy test method trong transaction và rollback cuối test; nếu test spawn worker riêng, worker không tự tham gia test transaction. Kết quả đọc dữ liệu của worker có thể bất ngờ rỗng. Cần biết test transaction boundary, thiết lập dữ liệu đã commit khi cần và kiểm soát rollback/cleanup đúng mục đích.

### 13.3. Tính nhất quán giữa database và side effects

Hãy kiểm thử cả ba trường hợp:

1. Commit thành công rồi worker chạy.
2. Transaction rollback trước khi worker được phát.
3. Worker gặp lỗi sau commit, hệ thống có retry/alert và trạng thái durable hay không.

Ca (3) thường quan trọng hơn một demo đơn giản vì nó quyết định độ tin cậy của nghiệp vụ.

## 14. Cấu hình executor và transaction trong production

Mẫu `ThreadPoolTaskExecutor` trong ví dụ dùng:

```java
pool.setCorePoolSize(2);
pool.setMaxPoolSize(2);
pool.setQueueCapacity(8);
pool.setThreadNamePrefix("tx-worker-");
```

Đây là thông số của demo, không phải mặc định khuyến nghị. Trong production:

- Queue/burst cần được giới hạn rõ ràng, vì delay trước khi worker đọc dữ liệu sẽ ảnh hưởng timing của tác vụ.
- Connection pool phải đủ theo số DB operations có thể chạy đồng thời, nhưng không vượt quá khả năng database.
- Worker có transaction riêng cần timeout, rollback rule và isolation theo nhu cầu.
- DB transaction cần ngắn; không giữ locks/connection qua việc chờ network/mail service nếu không bắt buộc.
- Theo dõi active threads, queue depth, rejection, DB connection wait, transaction duration, deadlocks, slow queries và retry rate.
- Nếu cần việc bền vững, phải có outbox/broker/job store chứ không chỉ tăng executor capacity.

**Không đồng nhất @Async với @Transactional.** Chúng là hai loại advice: một loại quyết định **thread thực thi**, một loại quyết định **ranh giới giao dịch trên thread đang thực thi**. Cách kết hợp hai annotation phải được kiểm thử qua proxy và transaction manager thực tế.

## 15. Những lỗi phổ biến

| Hiểu sai hoặc thiết kế sai | Hậu quả | Cách xử lý |
| --- | --- | --- |
| “@Transactional ở caller bao phủ luôn @Async worker” | Worker không nhìn thấy uncommitted data hoặc không có transaction | Xác định thread boundary; mở transaction riêng ở worker nếu cần |
| Thêm `@Transactional` vào worker để “kế thừa” T1 | Worker có T2 độc lập, không phải T1 | Thiết kế commit order/event/outbox rõ ràng |
| Dùng `REQUIRES_NEW` để mong đọc uncommitted row | T2 vẫn chịu isolation, thường không thấy row của T1 | Chờ commit đúng cách, không ép isolation bừa |
| Gửi @Async ngay sau `save()` rồi nghĩ row đã commit | Worker có thể đọc quá sớm | `@TransactionalEventListener(AFTER_COMMIT)` hoặc outbox |
| Caller đợi worker trong T1 nhưng worker chờ dữ liệu T1 | Có thể deadlock/timeout | Sửa workflow và điểm phát việc sau commit |
| Worker commit side effect, caller rollback | Trạng thái nghiệp vụ phân kỳ | Chỉ phát công việc sau commit, hoặc thiết kế saga/compensation |
| `this.asyncMethod()` lại thấy transaction caller | Self-invocation bypass proxy, chưa thực sự đổi thread | Tách async bean hoặc submit tường minh |
| Cho rằng đổi thành class-based proxy chữa self-invocation | Vẫn bypass lời gọi nội bộ | Kiểm tra proxy boundary, không chỉ proxy type |
| Dùng `TaskDecorator` copy JDBC Connection sang thread khác | Sai resource lifecycle/concurrency assumptions | Chỉ propagate metadata an toàn, không kéo transaction raw |
| Nghĩ `@TransactionalEventListener` là durable queue | Event có thể mất khi process chết | Transactional outbox nếu cần recovery |
| Phát event ngoài transaction rồi chờ listener AFTER_COMMIT | Mặc định listener không chạy | Xem `fallbackExecution` hoặc thiết kế rõ boundary |
| Bỏ qua lỗi worker vì caller không thấy exception | Task lỗi âm thầm hoặc mất outcome | Future/error handler, metrics, retry/status |
| Tin test pass vì worker chạy sau commit | Race không xuất hiện ở test nhẹ | Điều phối/kiểm tra trước commit bằng Future/latch |
| Dùng H2 demo mà kết luận cho mọi database | Isolation và locking khác nhau | Test DB engine/isolation thực tế |
| Cho rằng reactive transaction cũng gắn ThreadLocal | Sai mô hình Reactor Context | Phân biệt imperative với reactive transaction |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Method `register()` được gọi qua `@Transactional` proxy và đang insert order. Nó gọi `notificationWorker.send()` trên bean khác qua `@Async`. Worker có tự dùng T1 không?

Không. T1 là thread-bound imperative transaction. Worker chạy trên thread khác, không tự nhận tài nguyên hay connection của T1.

**Câu 2.** Đặt thêm `@Transactional` lên `send()` có làm hai method cùng transaction không?

Không. Khi @Async advice chuyển lời gọi sang worker và @Transactional advice được áp dụng ở worker, nó có thể mở **T2 độc lập**. T2 không nhìn thấy uncommitted T1 ở isolation như READ_COMMITTED.

**Câu 3.** Nếu worker truy vấn thấy row chưa commit trong test nhẹ, có thể kết luận transaction đã propagate không?

Không. Có thể caller đã commit trước khi worker query, isolation khác kỳ vọng, hoặc method `@Async` chưa thực sự chuyển thread vì self-invocation/SyncTaskExecutor. Phải đo thread name, transaction state và kiểm soát commit timing.

**Câu 4.** Listener `@TransactionalEventListener(AFTER_COMMIT)` có tự bảo đảm email được gửi sau commit ngay cả khi JVM crash?

Không. Nó giúp chọn phase xử lý trong tiến trình còn chạy, nhưng không đảm bảo durability. Với công việc không được phép mất, dùng outbox/durable queue và retry.

**Câu 5.** Vì sao listener AFTER_COMMIT trong demo thấy row 1, nhưng listener cùng loại không nhận event row 2?

Row 1 được commit thành công nên listener phase AFTER_COMMIT kích hoạt; transaction chứa row 2 rollback do exception nên không có commit phase thành công.

**Câu 6.** Khi nào nên giữ hai thao tác DB trong cùng một thread giao dịch thay vì đẩy một thao tác sang @Async?

Khi chúng phải commit/rollback nguyên tử trong **cùng local transaction**. Nếu tách async, cần chấp nhận và thiết kế semantics nhất quán cuối cùng hoặc orchestration khác.

## 17. Checklist áp dụng trước khi đưa vào production

- Có biết transaction manager thực tế là `PlatformTransactionManager` imperative hay reactive `ReactiveTransactionManager`?
- `@EnableTransactionManagement` và `@EnableAsync` có nằm đúng ApplicationContext?
- Các method `@Transactional`/`@Async` có thực sự được gọi qua Spring proxy, không phải `this.method()`?
- Executor thật có chạy ở worker thread khác không, hay đang dùng `SyncTaskExecutor`?
- Worker có cần transaction độc lập không, và propagation/isolation/rollback rule đã rõ chưa?
- Worker có phụ thuộc bản ghi caller vừa ghi nhưng chưa commit không?
- Nếu cần chạy sau commit, đã dùng phase AFTER_COMMIT hoặc transactional outbox chưa?
- Có chính sách khi transaction rollback và khi worker thất bại **sau commit**?
- Có thiết kế idempotency/deduplication và lưu trạng thái task durable khi cần?
- Có tránh giữ DB transaction mở để chờ Future/network không?
- Có đo thread, transaction state, commit visibility và DB connection pool dưới tải?
- Có test rollback/commit trên DB và isolation thực tế, thay vì chỉ dùng mock?
- Có tránh truyền raw JDBC Connection/EntityManager/transaction ThreadLocal sang worker?
- Có cơ chế timeout/retry/alert cho công việc nền quan trọng?

## 18. Kết luận

Trong Spring Framework imperative, **giao dịch thông thường gắn với thread thực thi**, thông qua transaction manager và resource binding. Khi một lời gọi `@Async` thực sự đi qua proxy và được chạy trên worker khác, **transaction của caller không tự chuyển theo**.

Worker không có `@Transactional` thường không có transaction caller. Worker có `@Transactional` có thể mở transaction **riêng**, nhưng không vì thế mà được đọc dữ liệu chưa commit hoặc cùng rollback với caller. Muốn công việc bắt đầu sau commit, hãy sử dụng ranh giới transaction/event rõ ràng; muốn không mất việc qua restart, cần một cơ chế bền vững như **transactional outbox**.

### Tài liệu tham khảo

- [Spring Framework — Understanding Declarative Transaction Implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html)
- [Spring Framework — Task Execution and @Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Spring Framework 6.2.1 — @Transactional](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/transaction/annotation/Transactional.html)
- [Spring Framework 6.2.1 — TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html)
- [Spring Framework 6.2.1 — @TransactionalEventListener](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactions-events-002">Tạo ranh giới giao dịch mới trong bean xử lý khi nghiệp vụ yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo ranh giới giao dịch mới trong bean xử lý khi nghiệp vụ yêu cầu

## Mục tiêu

Trong Spring Framework, một giao dịch được quản lý theo mô hình imperative với `PlatformTransactionManager` thường gắn với **thread đang thực thi**. Khi nghiệp vụ gửi công việc sang một executor bằng `@Async`, **giao dịch của caller không tự theo sang worker**. Nếu worker cần ghi dữ liệu một cách nguyên tử, phải thiết kế **ranh giới giao dịch của chính phần xử lý**.

Vấn đề không chỉ là thêm annotation `@Transactional` ở bất kỳ đâu. Cần quyết định **nghiệp vụ có thật sự cho phép một transaction độc lập hay không**, đặt transaction quanh đúng logic, gọi thông qua Spring proxy, chọn propagation phù hợp và hiểu tác động khi caller commit/rollback, worker thành công/thất bại, hoặc sự kiện được xử lý sau commit.

Bài học giúp bạn:

1. Hiểu transaction gắn thread và vì sao `@Async` không tự kế thừa transaction của caller.
2. Biết cách tách bean nhận việc async và bean xử lý `@Transactional` để mở transaction ở worker.
3. Phân biệt `Propagation.REQUIRED`, `REQUIRES_NEW` và `NESTED`; không mặc định `REQUIRES_NEW` luôn cần thiết khi chuyển thread.
4. Nhận diện các trường hợp **nên** và **không nên** cho worker commit độc lập với caller.
5. Dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` và transactional outbox đúng mục đích khi công việc phụ thuộc commit của giao dịch nguồn.
6. Chạy ví dụ Java 17+ và Spring Framework với H2: caller rollback nhưng giao dịch do worker tự mở vẫn commit.
7. Xử lý self-invocation, failure, timeout, connection-pool contention, retry và idempotency.

**Thông điệp cốt lõi:** `@Async` quyết định **nơi chạy công việc**; `@Transactional` quyết định **ranh giới nguyên tử của thao tác dữ liệu**. Đó là hai hợp đồng khác nhau. Một transaction mới ở worker chỉ đúng khi nghiệp vụ thực sự cần và chấp nhận tính độc lập này.

## 1. Một giao dịch Spring imperative sống trên thread nào?

Với JDBC/JPA thông thường, `@Transactional` được thực thi bởi Spring transaction advice/proxy. Một `PlatformTransactionManager` phù hợp liên kết connection, synchronization và trạng thái transaction với **thread hiện tại** thông qua cơ chế như `TransactionSynchronizationManager`.

```text
HTTP / caller thread
    |
    +-- @Transactional method
    |      Spring mở transaction A
    |      bind tài nguyên giao dịch vào caller thread
    |      thao tác SQL trong transaction A
    |
    +-- gọi @Async("workExecutor") ----> worker thread
    |                                    |
    |                                    +-- không tự có transaction A
    |                                    +-- nếu cần, mở transaction B riêng
    |                                    +-- commit/rollback transaction B
    |
    +-- caller commit/rollback A
```

Khi method `@Transactional` trên caller gọi `@Async`, worker không thể “nhìn thấy” transaction A chỉ vì caller vẫn đang mở transaction. Đây là đặc điểm của **thread-bound imperative transactions**; trong lập trình reactive, Spring dùng Reactor context và hợp đồng riêng, không nên áp dụng nguyên xi logic thread-bound.

Ba điều cần phân biệt:

- **Giao dịch được mở:** Transaction advice có bắt đầu hoặc tham gia một transaction ở thread đang chạy hay không?
- **Tác vụ được gửi đi:** Async proxy đã submit lời gọi vào executor hay chưa? Task có thể còn nằm trong queue.
- **Dữ liệu đã commit:** Người khác/worker có thấy bản ghi hay chưa? Điều này còn phụ thuộc isolation level, khóa và thời điểm commit.

Nếu caller vừa `INSERT order` nhưng chưa commit, worker dùng connection riêng thông thường không nhìn thấy order ấy ở mức isolation như `READ_COMMITTED`. Một số database có thể cho worker đợi lock; việc submit async **không bảo đảm** dữ liệu caller đã sẵn sàng.

## 2. Khi nào nên mở transaction mới tại bean xử lý?

Ví dụ **hợp lý** khi có một unit of work cần nguyên tử trong worker:

- Lưu kết quả của một job nền **đã được phép chạy độc lập**, gồm cập nhật trạng thái job và ghi các bản ghi liên quan trong cùng transaction của worker.
- Ghi nhận một nỗ lực xử lý (attempt log) độc lập, nếu nghiệp vụ cố tình muốn lưu cả khi caller thất bại.
- Xử lý một message đã được lấy từ broker; transaction dữ liệu của consumer phải bao phủ các thao tác cập nhật trong chính handler.
- Sau khi transaction tạo đơn hàng **đã commit**, thực hiện cập nhật một projection hay notification status trong một transaction mới.
- Chia một pipeline thành nhiều bước có trạng thái rõ, mỗi bước là transaction riêng và được phối hợp qua job record/event, không giả vờ tất cả là một transaction xuyên thread.

Ví dụ **không nên** cho worker commit độc lập chỉ vì có `@Async`:

- Caller tạo order trong transaction A, worker lập tức ghi shipment/payment rồi commit B; sau đó A rollback. Khi đó shipment/payment có thể còn tồn tại dù order chưa từng được commit.
- Nghiệp vụ đòi **tất cả hoặc không có gì** giữa thao tác caller và worker; transaction riêng phá tính nguyên tử toàn cục.
- Worker cần đọc dữ liệu vừa tạo nhưng caller chưa commit; việc worker chạy sớm có thể thấy thiếu dữ liệu, bị lock hoặc retry sai.
- Không có hệ thống lưu công việc bền vững nhưng vẫn yêu cầu mọi task phải sống qua crash/restart.

**Hỏi trước khi viết annotation:** Nếu transaction của caller rollback, bản ghi worker có được phép tồn tại không? Nếu câu trả lời là “không”, đừng chọn giao dịch độc lập và hy vọng việc dùng `@Transactional` trên cả hai phía sẽ tự phối hợp commit.

## 3. Tách trách nhiệm thành hai Spring bean

Một cấu trúc dễ hiểu và giảm nhầm lẫn AOP:

```text
Caller bean / controller
  |
  +-- gọi asyncGateway.dispatch(...)
                 |
                 v
       Spring async proxy
                 |
                 v
            workExecutor
                 |
                 v
          worker thread
                 |
                 +-- asyncGateway target method
                              |
                              +-- transactionalWorker.save(...)
                                        |
                                        v
                                  Spring tx proxy
                                        |
                                  BEGIN transaction B
                                        |
                                  JDBC / JPA writes
                                        |
                                  COMMIT / ROLLBACK B
```

Nên tách:

- **`AsyncGateway`** nhận yêu cầu, chứa phương thức `@Async` hoặc chủ động submit vào executor. Nó quyết định ranh giới thread và có thể trả `CompletableFuture<T>` để caller theo dõi.
- **`TransactionalWorker`** chứa thao tác dữ liệu nhất quán cần commit/rollback cùng nhau, được gắn `@Transactional` và **được gọi qua proxy Spring**.
- **Repository/JdbcTemplate** dùng đúng `DataSource`/`EntityManagerFactory` do `PlatformTransactionManager` của worker quản lý. Nếu dùng hai DataSource khác nhau, phải chọn đúng transaction manager; một transaction manager không tự bao phủ tất cả database.

Ví dụ cấu trúc:

```java
@Service
class AsyncGateway {
    private final TransactionalWorker worker;

    AsyncGateway(TransactionalWorker worker) {
        this.worker = worker;
    }

    @Async("workExecutor")
    public CompletableFuture<String> dispatch(long id) {
        String result = worker.save(id); // gọi bean giao dịch khác
        return CompletableFuture.completedFuture(result);
    }
}

@Service
class TransactionalWorker {
    @Transactional(propagation = Propagation.REQUIRED)
    public String save(long id) {
        // Những thao tác JDBC/JPA cùng ranh giới commit/rollback.
        return "saved:" + id;
    }
}
```

**Đây là snippet minh họa**, cần import annotation, cấu hình `@EnableAsync`, `@EnableTransactionManagement`, executor và các dependency phù hợp; phần `save` phải có thao tác dữ liệu thật mới có ý nghĩa transaction. Chương trình đầy đủ ở phần 6 có tất cả thành phần cần thiết.

Ưu điểm của hai bean: method `AsyncGateway.dispatch` thực thi trên worker, còn lời gọi `worker.save` đi qua **một transaction proxy khác** và transaction bắt đầu ngay trong worker. Không phải dựa vào phỏng đoán thứ tự advice khi gắn cả `@Async` và `@Transactional` lên một method duy nhất.

## 4. `REQUIRED` hay `REQUIRES_NEW`?

Đây là điểm quan trọng nhất của chủ đề. Hai propagation thường bị hiểu nhầm là “mặc định” và “có thread mới”, nhưng **cả hai đều là quy tắc transaction, không phải chính sách tạo thread**.

| Tình huống trên thread gọi method `@Transactional` | `REQUIRED` | `REQUIRES_NEW` |
| --- | --- | --- |
| **Chưa có transaction** trên thread đó | **Bắt đầu một transaction mới**. | Cũng bắt đầu một transaction mới. |
| **Đã có transaction** trên cùng thread, cùng manager/tài nguyên | **Tham gia transaction hiện có**; logic scope khác nhau có thể chia một physical transaction. | **Tạm đình chỉ** transaction đang có và tạo một physical transaction **độc lập**. |
| Khi method nội bộ rollback | Nếu tham gia physical transaction ngoài, rollback-only có thể khiến toàn transaction không commit. | Transaction mới rollback riêng; transaction ngoài có thể tiếp tục nếu nghiệp vụ và exception handling cho phép. |
| Tài nguyên | Có thể chia sẻ connection đang bound trên thread. | Thường cần connection/resource riêng trong khi resource ngoài vẫn bị giữ. |
| Có tạo thread mới không? | **Không**. | **Không**. |
| Khi dùng trong một `@Async` worker **chưa có transaction riêng** | Mở transaction mới độc lập với caller thread. | Cũng mở transaction mới; không cần thiết chỉ để khác caller transaction. |

Nếu caller đang có transaction A trên thread 1, còn `@Async` thực sự chạy trên thread 2 **không có transaction nào**, `@Transactional(propagation = REQUIRED)` ở `TransactionalWorker` sẽ mở B. Không cần cố gắng dùng `REQUIRES_NEW` chỉ để “thoát khỏi transaction caller”; transaction A vốn **không được truyền qua thread**.

Nhưng nếu trên **cùng worker thread** đã có transaction B và một bean khác cần luôn commit audit record độc lập với B, `REQUIRES_NEW` có thể được cân nhắc:

```java
@Service
class AuditWriter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(AuditRecord record) {
        auditRepository.insert(record);
    }
}
```

Đây là **snippet cấu trúc**, cần `AuditRecord`, `auditRepository` và import. Phải gọi từ **bean/proxy khác**; `this.recordAttempt(...)` trong cùng class không kích hoạt transaction advice ở proxy mode.

### Cẩn thận với connection pool và deadlock

`REQUIRES_NEW` trên cùng thread đang giữ một transaction khác có thể cần **connection bổ sung**, trong khi connection của outer transaction vẫn được giữ. Nếu có N thread đều giữ N connection ngoài rồi mỗi thread chờ thêm connection cho `REQUIRES_NEW`, một pool chỉ có N connection có thể dẫn tới starvation hoặc deadlock. Spring khuyến cáo sizing connection pool phù hợp, thường lớn hơn số thread transaction đồng thời ít nhất một connection cho kịch bản này.

Đối với async, còn một cạm bẫy tương tự: caller giữ một connection mở, **chặn bằng `future.get()`**, trong khi worker cần connection nhưng pool đã hết chỗ. Không chọn “đợi async trong một transaction còn mở” làm mô hình mặc định production.

### `NESTED` không phải transaction độc lập

`Propagation.NESTED` thường dùng **savepoint** bên trong cùng physical transaction (khi transaction manager và resource hỗ trợ, điển hình là JDBC savepoints). Một phần có thể rollback về savepoint, nhưng việc commit thật phụ thuộc transaction ngoài. Nó không có nghĩa inner transaction commit độc lập như `REQUIRES_NEW`, và cũng không chuyển transaction sang worker thread.

## 5. Các đặc điểm của transaction mới ở worker

Một phương thức:

```java
@Transactional(propagation = Propagation.REQUIRED)
public void process(JobId id) {
    repository.markRunning(id);
    repository.writeResult(id);
    repository.markSucceeded(id);
}
```

Đây là **mã cấu trúc**, cần các kiểu/repository cụ thể. Nếu phương thức được gọi qua transaction proxy trên worker thread chưa có transaction, cả ba thao tác dùng cùng transaction; nếu bước 2 ném `RuntimeException` chưa được xử lý thì transaction thường rollback, kể cả bước 1 đã chạy SQL.

Một số nguyên tắc:

- Transaction bắt đầu **khi transaction interceptor thực sự được gọi** trên worker, không bắt đầu lúc caller submit công việc vào queue.
- Nếu worker còn chờ trong executor queue, caller transaction có thể đã commit, rollback hoặc kết thúc trước khi worker bắt đầu.
- Transaction commit sau khi target method `@Transactional` return bình thường qua transaction proxy. Nếu exception khiến rollback theo rule, các write cùng transaction không được commit.
- Mặc định Spring rollback với `RuntimeException` và `Error`, không tự rollback với mọi checked exception. Có thể dùng `rollbackFor` (hoặc quy tắc dự án tương ứng) khi nghiệp vụ yêu cầu.
- Nếu method catch exception rồi **nuốt** lỗi và return bình thường, transaction advice có thể commit vì không thấy exception. Nếu cần rollback, ném lại lỗi hoặc đánh dấu rollback-only đúng ranh giới.
- Một async result `CompletableFuture` hoàn tất thành công không tự chứng minh các hệ thống khác đã xử lý message/email; nó chỉ biểu diễn completion theo hợp đồng method.
- Giữ transaction nhỏ, tránh remote HTTP call dài hoặc CPU dài không cần thiết **bên trong transaction**, vì connection và lock có thể bị giữ quá lâu.

Các nguyên tắc này áp dụng cho Spring imperative transactions. Với R2DBC/reactive programming, hãy học riêng `ReactiveTransactionManager` và Reactor context; không copy transaction-bound `ThreadLocal` sang thread khác như một workaround.

## 6. Ví dụ chạy được: caller rollback, worker vẫn commit transaction riêng

Ví dụ dùng **Java 17+**, Maven, **Spring Framework 6.2.12**, Spring JDBC và H2 in-memory. Nó chứng minh sự độc lập trong trường hợp có chủ đích:

1. Main tạo transaction A qua `TransactionTemplate`, ghi một bản ghi vào `caller_records`.
2. Khi transaction A **chưa commit**, main gọi `AsyncGateway.enqueue(...)` qua Spring proxy.
3. Trên thread của `workExecutor`, gateway xác nhận **không có transaction A bound**.
4. Gateway gọi bean `TransactionalWorker.save(...)`, nơi `@Transactional(REQUIRED)` tự mở transaction B và ghi vào **bảng khác** `worker_records`.
5. Main chờ worker hoàn tất B, sau đó đánh dấu transaction A rollback.
6. Khi đọc database từ ngoài cả hai transaction, `caller_records` không còn bản ghi, còn `worker_records` có một bản ghi đã commit.

**Vì sao hai bảng?** Demo muốn chỉ ra **ranh giới transaction**, không tạo thêm tình huống khóa row do hai thread cùng sửa một bản ghi. Chọn hai bảng làm ví dụ ít phụ thuộc vào chiến lược lock của H2; trong production vẫn cần đánh giá lock, isolation và connection pool.

### Cấu trúc thư mục

```text
async-worker-transaction-demo/
├── pom.xml
└── src/main/java/example/AsyncWorkerTransactionDemo.java
```

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>async-worker-transaction-demo</artifactId>
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
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
            <version>6.2.12</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.3.232</version>
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
                    <mainClass>example.AsyncWorkerTransactionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/example/AsyncWorkerTransactionDemo.java`:

```java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

public class AsyncWorkerTransactionDemo {

    public record Receipt(
        boolean txAtAsyncStart,
        boolean txInsideWriter,
        String writerThread
    ) {
    }

    public static class TransactionalWorker {
        private final JdbcTemplate jdbc;

        public TransactionalWorker(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public Receipt save(String value, boolean txAtAsyncStart) {
            boolean active =
                TransactionSynchronizationManager.isActualTransactionActive();

            jdbc.update(
                "INSERT INTO worker_records(id, payload) VALUES (?, ?)",
                2, value
            );

            return new Receipt(
                txAtAsyncStart,
                active,
                Thread.currentThread().getName()
            );
        }
    }

    public static class AsyncGateway {
        private final TransactionalWorker writer;

        public AsyncGateway(TransactionalWorker writer) {
            this.writer = writer;
        }

        @Async("workExecutor")
        public CompletableFuture<Receipt> enqueue(String payload) {
            // Trước khi gọi writer, worker này chưa có transaction.
            boolean activeBeforeWriter =
                TransactionSynchronizationManager.isActualTransactionActive();

            // Lời gọi tới bean/proxy khác để @Transactional có hiệu lực.
            Receipt saved = writer.save(payload, activeBeforeWriter);

            return CompletableFuture.completedFuture(saved);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    public static class Config {

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:tx_boundary_demo;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean("workExecutor")
        public ThreadPoolTaskExecutor workExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(4);
            pool.setThreadNamePrefix("worker-");
            return pool;
        }

        @Bean
        public TransactionalWorker transactionalWorker(JdbcTemplate jdbc) {
            return new TransactionalWorker(jdbc);
        }

        @Bean
        public AsyncGateway asyncGateway(TransactionalWorker writer) {
            return new AsyncGateway(writer);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            // Khởi tạo schema ngoài transaction thử nghiệm.
            jdbc.execute(
                "CREATE TABLE caller_records(" +
                "id INT PRIMARY KEY, payload VARCHAR(100))"
            );
            jdbc.execute(
                "CREATE TABLE worker_records(" +
                "id INT PRIMARY KEY, payload VARCHAR(100))"
            );

            PlatformTransactionManager manager =
                context.getBean(PlatformTransactionManager.class);
            TransactionTemplate tx = new TransactionTemplate(manager);
            AsyncGateway gateway = context.getBean(AsyncGateway.class);

            AtomicBoolean callerTransactionActive = new AtomicBoolean();
            AtomicReference<Receipt> receipt = new AtomicReference<>();

            tx.executeWithoutResult(status -> {
                callerTransactionActive.set(
                    TransactionSynchronizationManager
                        .isActualTransactionActive()
                );

                jdbc.update(
                    "INSERT INTO caller_records(id, payload) VALUES (?, ?)",
                    1, "will-rollback"
                );

                try {
                    // Chờ worker chỉ để chứng minh B commit trước A rollback.
                    receipt.set(
                        gateway.enqueue("worker-committed")
                            .get(10, TimeUnit.SECONDS)
                    );
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "Interrupted while waiting for worker", ex
                    );
                } catch (ExecutionException | TimeoutException ex) {
                    throw new IllegalStateException(
                        "Worker failed or timed out", ex
                    );
                }

                // Rollback A một cách có chủ đích.
                status.setRollbackOnly();
            });

            Receipt saved = receipt.get();
            int callerRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM caller_records", Integer.class
            );
            int workerRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM worker_records", Integer.class
            );

            System.out.println("callerTransactionActive="
                + callerTransactionActive.get());
            System.out.println("txAtAsyncStart=" + saved.txAtAsyncStart());
            System.out.println("txInsideWriter=" + saved.txInsideWriter());
            System.out.println("ranOnWorkerThread="
                + saved.writerThread().startsWith("worker-"));
            System.out.println("callerRowsAfterRollback=" + callerRows);
            System.out.println("workerRowsAfterCallerRollback=" + workerRows);
        }
    }
}
```

Chạy từ thư mục `async-worker-transaction-demo` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

Khi ứng dụng chạy thành công, **sáu dòng do chương trình in ra** theo thứ tự:

```text
callerTransactionActive=true
txAtAsyncStart=false
txInsideWriter=true
ranOnWorkerThread=true
callerRowsAfterRollback=0
workerRowsAfterCallerRollback=1
```

### Giải thích từng phép kiểm tra

1. `TransactionTemplate` mở transaction A trên main thread; `TransactionSynchronizationManager.isActualTransactionActive()` trả `true`.
2. Main ghi một dòng `caller_records` nhưng chưa commit. `gateway.enqueue(...)` đi qua Spring async proxy tới `workExecutor` tên worker.
3. Trong `AsyncGateway` **trước** khi gọi `TransactionalWorker`, không có transaction A gắn với worker; `txAtAsyncStart=false`.
4. `writer.save(...)` được gọi qua **transaction proxy khác**. Vì worker chưa có transaction, `Propagation.REQUIRED` bắt đầu B, nên `txInsideWriter=true`.
5. `writer.save` ghi một dòng trong `worker_records`. Khi method kết thúc bình thường qua proxy, transaction B commit; sau đó `AsyncGateway` hoàn tất future.
6. Main đã chờ future bằng `get(10, SECONDS)` (chỉ cho thí nghiệm), nên biết B hoàn tất trước khi đánh dấu A rollback.
7. `status.setRollbackOnly()` khiến transaction A rollback khi `TransactionTemplate` kết thúc. Dòng trong `caller_records` biến mất; dòng do B commit vẫn tồn tại.
8. Vì tên pool được đặt `worker-`, `ranOnWorkerThread=true` và worker thực sự khác main thread theo cấu hình này.

**Cảnh báo:** Ví dụ cố ý cho worker commit trước khi caller rollback để chứng minh tính độc lập. Đây là điều **không nên** làm nếu dữ liệu worker phải phụ thuộc vào một đơn hàng mà transaction caller chưa commit. Việc chờ `Future.get()` khi đang giữ transaction trên caller cũng có thể gây hết connection hoặc deadlock ở hệ thống thật; ví dụ dùng hai bảng và `DriverManagerDataSource` (mỗi lần lấy connection là connection mới) để cô lập bài học khỏi giới hạn pool.

`DriverManagerDataSource` phù hợp để minh họa đơn giản, **không phải connection pool production**. Ứng dụng thật thường dùng một pool có giới hạn, cần sizing theo số caller transaction đang mở và số worker đồng thời. Với nhiều service, phải xác định transaction manager nào điều khiển từng DataSource.

## 7. Điều gì xảy ra nếu worker lỗi?

Khi `TransactionalWorker.save(...)` ném `RuntimeException` chưa được nuốt:

```text
AsyncGateway worker thread
     |
     +-- call TransactionalWorker proxy
               |
               +-- BEGIN B
               +-- update SQL
               +-- RuntimeException
               +-- ROLLBACK B
     |
     +-- future caller completed exceptionally
```

Một method có thể dùng:

```java
@Transactional
public void writeTwoRows() {
    repository.insertFirst();
    repository.insertSecond(); // có thể ném exception
}
```

Đây là snippet, cần repository thật. Nếu second insert ném unchecked exception, Spring transaction advice thường rollback cả hai thao tác trong cùng physical transaction. Nếu exception được **bắt và nuốt** trong method, transaction advice thấy return bình thường và có thể commit; không vì log lỗi mà rollback tự động.

Mặc định `@Transactional` rollback với unchecked exception (`RuntimeException`) và `Error`, không mặc định với mọi checked exception. Nếu chính sách cần rollback cả checked exception nghiệp vụ, có thể cấu hình:

```java
@Transactional(rollbackFor = Exception.class)
public void process(...) throws Exception {
    // ...
}
```

Mẫu này là **minh họa chữ ký** cần thay tham số và thân method theo nghiệp vụ; không phải code độc lập. Quy tắc rollback cần đủ hẹp để không biến mọi lỗi điều kiện thành rollback ngoài ý muốn.

Với `@Async` trả `CompletableFuture<T>`, caller có thể quan sát lỗi bằng `get()` (thường `ExecutionException`), `join()` (thường `CompletionException`) hoặc `whenComplete`/`handle`. Đừng bỏ Future nếu công việc cần theo dõi hoàn tất.

Nếu executor **từ chối submit** vì queue đầy hoặc đã shutdown, lỗi có thể xuất hiện trước khi có future hợp lệ; điều đó không tương đương rollback của transaction B, vì B có thể chưa từng được mở.

## 8. Cạm bẫy của proxy: cùng bean tự gọi không mở transaction mới

`@Transactional` và `@Async` đều dựa vào AOP proxy ở mode thông thường. Nếu trong một bean có:

```java
@Service
class SameBean {
    public void outer() {
        this.inner(); // self-invocation: không qua transaction proxy
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inner() {
        // Dự định mở transaction độc lập.
    }
}
```

Đây là đoạn minh họa class, không có đầy đủ import/implementation. Trong proxy mode, lời gọi `this.inner()` là **lời gọi Java trực tiếp trên target object**, không qua proxy; `REQUIRES_NEW` không tự được áp dụng cho invocation ấy. Nếu đang có transaction ngoài, code bên trong có thể vẫn chạy trong transaction cũ; nếu không có transaction, nó có thể không được giao dịch nào bao phủ.

Giải pháp rõ ràng: tách `inner` vào một Spring bean khác và inject reference của bean đó; gọi qua reference do container cung cấp. Với code rất nhỏ, có thể dùng `TransactionTemplate` trong method để kiểm soát phạm vi bằng API tường minh.

Không tự gọi một method `@Async` qua `this` rồi kỳ vọng nó chuyển thread; nếu nó chạy đồng bộ trên caller thì cả nhận định “transaction không được truyền” lẫn số liệu latency đều bị sai bối cảnh.

### Gắn cả `@Async` và `@Transactional` lên một method thì sao?

Có thể hoạt động tùy advice/proxy order và cấu hình ứng dụng. Nhưng **khó quan sát** đoạn nào đã chuyển sang worker, transaction mở ở thread nào và kết quả commit khi nào, đặc biệt khi có `@Retryable`, security, `@Cacheable` hoặc proxy nhiều tầng.

Khi mục tiêu là làm rõ transaction mới ở worker, hai bean như `AsyncGateway` và `TransactionalWorker` khiến trách nhiệm rõ hơn: async advice thuộc gateway, transaction advice thuộc worker. Đây là khuyến nghị thiết kế để kiểm chứng dễ, không phải tuyên bố Spring cấm hoàn toàn hai annotation trên cùng method.

## 9. Giao dịch và sự kiện: đừng xử lý trước khi caller commit

Một lỗi phổ biến:

```java
@Transactional
public void createOrder(Command command) {
    repository.insertOrder(command);
    asyncGateway.startShipping(command.orderId());
}
```

Snippet giả định repository, command và gateway phù hợp. `startShipping` có thể chạy **trước khi transaction tạo order commit**. Nếu caller rollback, worker vẫn có thể tạo shipping record; nếu worker đọc database trên connection riêng, nó có thể không thấy order chưa commit.

**Giải pháp khi chỉ muốn xử lý sau commit:** phát sự kiện nghiệp vụ trong transaction caller và dùng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` để chỉ kích hoạt sau commit thành công.

Ví dụ kiến trúc với các bean riêng:

```java
public record OrderCreated(long orderId) {
}

@Service
public class OrderService {
    private final ApplicationEventPublisher publisher;
    private final OrderRepository repository;

    public OrderService(ApplicationEventPublisher publisher,
                        OrderRepository repository) {
        this.publisher = publisher;
        this.repository = repository;
    }

    @Transactional
    public void createOrder(long id) {
        repository.insert(id);
        publisher.publishEvent(new OrderCreated(id));
    }
}

@Component
public class OrderCommittedListener {
    private final AsyncOrderProcessor asyncProcessor;

    public OrderCommittedListener(AsyncOrderProcessor asyncProcessor) {
        this.asyncProcessor = asyncProcessor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OrderCreated event) {
        asyncProcessor.processAsync(event.orderId());
    }
}

@Service
public class AsyncOrderProcessor {
    private final OrderWorker worker;

    public AsyncOrderProcessor(OrderWorker worker) {
        this.worker = worker;
    }

    @Async("orderExecutor")
    public CompletableFuture<Void> processAsync(long orderId) {
        worker.processCommittedOrder(orderId);
        return CompletableFuture.completedFuture(null);
    }
}

@Service
public class OrderWorker {
    @Transactional(propagation = Propagation.REQUIRED)
    public void processCommittedOrder(long orderId) {
        // Đọc đơn hàng đã commit và cập nhật dữ liệu worker.
    }
}
```

**Đây là sơ đồ code cho ứng dụng**, không phải một file Java độc lập. Cần khai báo đầy đủ repository, import, executor, bật `@EnableAsync` và `@EnableTransactionManagement`. Mục đích chính: listener sau commit **giao task sang bean async khác**, còn method dữ liệu trong worker **mở transaction riêng** khi chạy.

`@TransactionalEventListener` mặc định ở phase `AFTER_COMMIT`. Khi phát event **không có transaction** đang hoạt động, listener mặc định không được gọi (trừ khi cấu hình `fallbackExecution = true`); điều này cần kiểm chứng nếu có những đường phát event ngoài transaction.

### Vì sao cần chú ý giao dịch ở listener AFTER_COMMIT?

Spring lưu ý rằng sau khi transaction đã hoàn tất, một số tài nguyên transactional có thể **vẫn còn bound/accessible** trong giai đoạn callback. Nếu một listener chạy đồng bộ trên cùng thread **tự thao tác database trực tiếp** sau commit, thao tác đó có thể tham gia ngữ cảnh resource cũ nhưng không có một commit tiếp theo như mong đợi. Với write cần chắc chắn được commit ở listener đồng bộ, thường cần chuyển sang một bean `@Transactional(REQUIRES_NEW)` được gọi qua proxy.

Trong mẫu ở trên, listener **không** làm database write trực tiếp: nó giao việc qua executor sang một worker thread khác, nơi `@Transactional(REQUIRED)` mở transaction mới. Vẫn phải hiểu async dispatch có thể thất bại và không đồng nghĩa work chắc chắn được xử lý.

### AFTER_COMMIT không tự bảo đảm độ bền

`@TransactionalEventListener(AFTER_COMMIT)` là cách chọn **thời điểm tương đối với transaction hiện tại**, không phải một hàng đợi bền vững. Process có thể crash **sau khi order commit nhưng trước khi task được submit**, hoặc task đã ở RAM queue nhưng crash trước khi chạy xong. Nếu bắt buộc “mỗi order đã commit cuối cùng sẽ được xử lý”, cần transactional outbox/queue durable hoặc cơ chế lưu job có retry và idempotency.

## 10. Transactional outbox khi cần cả atomicity nguồn và xử lý nền

Với nghiệp vụ quan trọng, transaction của caller nên ghi cả dữ liệu chính và một **outbox record** trong cùng physical transaction:

```text
Caller transaction A
   |
   +-- INSERT order
   +-- INSERT outbox_event(order-created, orderId, eventId)
   |
   +-- COMMIT A
         |
         v
Outbox dispatcher (quy trình độc lập)
   |
   +-- đọc outbox record đã commit
   +-- publish hoặc giao xử lý
   +-- retry/ack theo chính sách
         |
         v
Worker nhận event
   |
   +-- BEGIN transaction B
   +-- xử lý idempotent theo eventId
   +-- update worker state
   +-- COMMIT B
```

Nếu A rollback, cả order và outbox event đều rollback. Nếu A commit, outbox event còn trong database để dispatcher phục hồi sau crash. Hệ thống có thể phát trùng khi retry, vì vậy worker cần idempotency theo `eventId`/unique constraint hoặc protocol tương đương.

Outbox **không** tạo distributed ACID transaction giữa database và broker; nó chuyển bài toán sang delivery có thể retry và xử lý trùng. Đây thường là đánh đổi hợp lý hơn việc giữ một transaction qua nhiều thread, remote systems hoặc hàng chờ trong RAM.

## 11. Chờ Future trong transaction: dễ hết connection và khóa

Trong ví dụ chính, caller cố ý gọi `future.get(...)` **khi transaction A còn mở** để làm rõ B commit rồi A rollback. Đừng chuyển nguyên mẫu ấy thành production pattern.

Tình huống nguy hiểm:

```text
Caller thread 1..N:
  BEGIN A; giữ DB connection 1..N
  submit @Async work
  Future.get() đợi worker kết thúc

Worker thread:
  @Transactional cần connection mới
  DB pool chỉ có N connection, tất cả đang bị caller giữ
  -> worker chờ connection
  -> caller chờ worker
  -> starvation / deadlock
```

Ngay cả với pool có đủ connection, worker có thể chờ **row lock** mà caller giữ; caller lại chờ worker, tạo deadlock ở mức giao dịch. Tách thread không tách contention dữ liệu.

Các cách giảm rủi ro:

- Không chờ `Future` bên trong transaction nếu không bắt buộc; nên commit trước khi giao việc phụ thuộc vào dữ liệu đó.
- Với việc sau commit, dùng listener `AFTER_COMMIT` hoặc outbox bền vững tùy yêu cầu.
- Giới hạn executor concurrency/queue, timeout DB/HTTP, lock timeout và ngân sách retry.
- Theo dõi số DB connections đang bận/chờ, số worker, queue length, thời gian transaction và lock waits.
- Với `REQUIRES_NEW`, dự trù connection dư khi outer transaction giữ một connection; không coi việc tăng max pool thread là giải pháp nếu DB đã bão hòa.

## 12. Timeout, cancellation và rollback không phải một thứ

Nếu caller nhận một `CompletableFuture<Result>` từ async gateway:

```java
Result value = gateway.runAsync(id).get(2, TimeUnit.SECONDS);
```

Đây là snippet trong method có xử lý checked exceptions. `get(timeout)` chỉ giới hạn **thời gian caller đợi kết quả**. Nếu nó ném `TimeoutException`, worker có thể vẫn đang chạy, transaction B có thể chưa bắt đầu, đang mở hoặc đã commit.

Các kết luận **không được suy ra** sau timeout:

- “Worker chắc chắn đã rollback”: sai, worker có thể đang chạy bình thường.
- “Caller rollback thì worker cũng rollback”: sai, nếu B độc lập.
- “`Future.cancel(true)` chắc chắn ngắt JDBC và rollback”: sai, cancellation/interruption phụ thuộc future, JDBC driver và code hợp tác.
- “Retry an toàn”: sai, task cũ có thể đã commit nhưng result đến trễ.

Với operation tạo side effect, phải có **idempotency key** hoặc dữ liệu trạng thái kiểm chứng được. Nếu caller timeout rồi retry `INSERT` không có unique constraint, worker có thể tạo bản ghi trùng. Một `CompletableFuture` chỉ là handle completion, không phải bằng chứng exactly-once.

Đặt timeout ở nơi chờ thật sự: connection acquisition timeout, query timeout, HTTP connect/read timeout, lock timeout, deadline job. Hạn chế transaction dài và đừng giữ lock khi thực hiện network I/O ngoài ý muốn.

## 13. Exception và rollback giữa nhiều transaction

| Tình huống | Transaction A ở caller | Transaction B ở worker |
| --- | --- | --- |
| A rollback **trước** khi worker bắt đầu | A rollback | B vẫn có thể bắt đầu sau đó nếu task đã được submit, trừ khi có cơ chế không cho chạy. |
| A rollback **sau** khi B commit | A rollback | B giữ commit nếu nghiệp vụ và database không có ràng buộc phá vỡ. |
| B rollback do exception, A vẫn đang chạy | A có thể commit hoặc rollback theo logic riêng. | B rollback các write trong B. |
| A commit, B thất bại | A vẫn đã commit. | B rollback; cần retry/compensation nếu nghiệp vụ yêu cầu. |
| Task bị từ chối trước khi worker chạy | Tùy code caller có ném lỗi/rollback hay xử lý tiếp. | B chưa được mở. |
| Caller timeout khi đang chờ | A có thể tiếp tục hoặc rollback theo handler. | B có thể đang chạy và sau đó commit/rollback. |

Trong cùng một thread khi **REQUIRED tham gia transaction A**, rollback-only của inner logical scope có thể làm A rollback và dẫn tới `UnexpectedRollbackException` nếu caller tưởng sẽ commit. Nhưng với hai thread độc lập như ví dụ, không có quan hệ rollback-only tự động xuyên transaction; cần phối hợp bằng domain workflow chứ không coi hai proxy là một global transaction.

Nếu muốn nhiều bước có trạng thái có thể hoàn tác, hãy cân nhắc saga/compensation ở cấp nghiệp vụ. Compensation là **một hành động mới** để sửa tác dụng đã commit, không phải rollback vật lý của transaction cũ.

## 14. Cấu hình và điều cần nhớ với JPA

Ví dụ dùng `JdbcTemplate` để thấy transaction manager rõ. Với JPA, có thể dùng `JpaTransactionManager`, repository hoặc `EntityManager` được container quản lý. Tuy nhiên:

- `EntityManager` gắn với persistence context/transaction thường **không thread-safe** để chuyển nguyên instance sang async worker.
- Lazy-loaded entity từ caller có thể không còn session hợp lệ khi worker truy cập; đừng gửi entity object chưa được materialize như một transport payload tự do.
- Truyền ID hoặc DTO bất biến sang worker, rồi **load lại** entity trong transaction B của worker. Có thể cần kiểm tra record đã commit và xử lý optimistic locking/version conflicts.
- `@Transactional(readOnly = true)` là hint/configuration cho framework, không phải cơ chế bảo đảm không có write ở mọi database.
- Nếu có nhiều transaction managers (JDBC/JPA/đa DataSource), chọn đúng manager, ví dụ `@Transactional(transactionManager = "ordersTransactionManager")`, và đảm bảo repository thực sự tham gia manager đó.
- Không mở transaction kéo dài xuyên HTTP calls chỉ vì JPA lazy loading: xác định rõ ranh giới truy cập dữ liệu và thiết kế fetch graph/DTO phù hợp.

Một method worker xử lý entity sau commit có thể cần `@Transactional(REQUIRED)` để load và update trạng thái. Chỉ khi **trong cùng worker thread đã có transaction khác** và nghiệp vụ yêu cầu commit độc lập, mới cân nhắc `REQUIRES_NEW` với toàn bộ hệ quả về connection và lỗi.

## 15. Một phương án dùng TransactionTemplate

Không phải mọi ranh giới phải được khai báo bằng annotation. Khi logic bắt đầu/kết thúc transaction cần điều khiển tại một block code trong worker, `TransactionTemplate` có thể diễn đạt tường minh:

```java
public class TransactionalJobRunner {
    private final TransactionTemplate txTemplate;
    private final JdbcTemplate jdbc;

    public TransactionalJobRunner(PlatformTransactionManager txManager,
                                  JdbcTemplate jdbc) {
        this.txTemplate = new TransactionTemplate(txManager);
        this.jdbc = jdbc;
    }

    public String runJob(long jobId) {
        return txTemplate.execute(status -> {
            jdbc.update("UPDATE jobs SET status = ? WHERE id = ?",
                        "DONE", jobId);
            return "done:" + jobId;
        });
    }
}
```

Đây là **mẫu lớp minh họa**, không phải chương trình độc lập vì cần schema `jobs`, imports và integration vào Spring bean/executor. Khi gọi nó từ worker thread, block `txTemplate.execute(...)` tạo/tham gia transaction theo propagation mặc định của `TransactionTemplate` (REQUIRED). Nó không cần `@Transactional` proxy ở method `runJob`, vì chính template quản lý transaction.

Tuy vậy, `TransactionTemplate` **không** làm cho code tự chạy async. Bạn vẫn cần executor/`@Async` nếu muốn chuyển thread. Việc chọn programmatic hay declarative transaction tùy nhu cầu rõ ràng và quy ước dự án, không phải cơ chế “sửa” transaction tự truyền giữa thread.

## 16. Các sai lầm thường gặp

| Nhận định sai | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| “Transaction của caller tự theo `@Async` worker.” | Imperative transaction gắn với caller thread. | Mở transaction của worker nếu cần và được phép độc lập. |
| “Cứ có `@Async` là worker tự có transaction.” | Executor chỉ chạy task; không tự tạo JDBC transaction. | Gọi bean `@Transactional` qua proxy hoặc dùng template. |
| “Phải dùng `REQUIRES_NEW` vì worker là thread khác.” | Worker không có transaction A để suspend; REQUIRED cũng mở transaction mới. | Chọn propagation theo transaction **hiện có trên worker**. |
| “`REQUIRES_NEW` mở thread mới.” | Nó thay physical transaction, không thay executor. | Tách khái niệm transaction boundary và execution thread. |
| “Có `@Transactional` trên `this.inner()` là đã mở tx mới.” | Self-invocation bỏ qua proxy mode. | Gọi bean khác hoặc dùng TransactionTemplate. |
| “Caller rollback thì mọi worker write cũng rollback.” | Independent transaction B có commit riêng. | Chỉ chọn độc lập khi nghiệp vụ cho phép; cân nhắc after-commit/outbox. |
| “Submit worker ngay sau insert thì worker thấy bản ghi.” | Caller có thể chưa commit; isolation và lock quyết định. | Dispatch sau commit hoặc dùng outbox. |
| “Chờ Future ngay trong transaction là an toàn.” | Có thể giữ connection/lock rồi chờ worker cần chính resource. | Tránh block; commit trước, sizing/timeout đúng. |
| “`REQUIRES_NEW` không có chi phí tài nguyên.” | Outer transaction thường giữ connection khi inner cần connection khác. | Sizing pool và đánh giá contention/deadlock. |
| “`NESTED` commit độc lập.” | Thường là savepoint trong cùng physical transaction. | Dùng REQUIRES_NEW chỉ khi cần physical transaction độc lập. |
| “`AFTER_COMMIT` event tự bền vững qua crash.” | Listener/queue trong RAM có thể mất việc. | Transactional outbox/broker durable khi cần. |
| “Bắt exception và return bình thường vẫn tự rollback.” | Transaction advice có thể thấy success và commit. | Rethrow hoặc setRollbackOnly theo rule. |
| “Timeout/cancel future là rollback DB.” | Completion/cancellation không tự chứng minh transaction dừng. | Timeout I/O thật, idempotency và trạng thái job. |
| “Có thể share EntityManager từ caller sang worker.” | Persistence context không an toàn khi chia thread. | Truyền ID/DTO rồi load trong transaction worker. |

## 17. Checklist khi thiết kế một async transactional workflow

1. **Nghiệp vụ có cho phép độc lập không?** Nếu A rollback, B có được commit không? Nếu không, không tạo B sớm.
2. **Khi nào dữ liệu nguồn có thể đọc?** Đã commit chưa? Có lock/isolation hoặc version conflict cần xử lý?
3. **Worker dùng thread/executor nào?** Gọi qua Spring proxy chưa, có self-invocation không?
4. **Bean transaction nằm đâu?** Có một `TransactionalWorker` được gọi qua proxy trên worker, hoặc `TransactionTemplate` bao đúng block dữ liệu?
5. **Propagation nào?** Worker chưa có tx thì REQUIRED tạo mới; đang có tx mới xem cần tham gia hay REQUIRES_NEW độc lập.
6. **Manager/Resource nào?** TransactionManager đang quản lý đúng DataSource/JPA context không?
7. **Timeout/retry/cancellation?** Future timeout không tự rollback; task có thể commit trễ, cần idempotency.
8. **Có chờ worker trong outer transaction không?** Kiểm tra connection pool/lock deadlock, thường nên tránh.
9. **Task có thể bị mất không?** Nếu phụ thuộc commit của A và phải xử lý cuối cùng, dùng outbox hoặc job store durable.
10. **Có theo dõi trạng thái không?** Queue wait, tx duration, DB connections, rollback rate, retry/dead-letter, domain correlation ID.
11. **Exception/rollback rule đã kiểm thử chưa?** Runtime vs checked, caught exception, rollback-only và async completion.
12. **Security/context có được mang đi an toàn không?** Không copy transaction ThreadLocal; truyền metadata immutable cần thiết và xác thực lại khi phù hợp.

## 18. Câu hỏi tự kiểm tra

**1.** Caller có transaction A rồi gọi method `@Async`. Worker có tự tham gia transaction A không?

**Đáp án:** Không đối với imperative transaction gắn với thread. Nếu worker cần transaction, transaction advice được gọi trên worker phải tạo/tham gia một transaction ở chính worker.

**2.** Trên worker chưa có transaction, `@Transactional(propagation=REQUIRED)` có mở transaction mới không?

**Đáp án:** Có. REQUIRED tạo mới khi chưa có transaction trên thread phù hợp, vì vậy không cần REQUIRES_NEW chỉ để khác caller transaction đang ở thread khác.

**3.** Khi nào `REQUIRES_NEW` khác REQUIRED rõ nhất?

**Đáp án:** Khi đã có transaction trên **cùng thread**. REQUIRED thường tham gia physical transaction hiện có; REQUIRES_NEW suspend transaction đó rồi mở một physical transaction riêng, tốn tài nguyên khác và commit/rollback độc lập.

**4.** Trong demo, tại sao `callerRowsAfterRollback=0` nhưng `workerRowsAfterCallerRollback=1`?

**Đáp án:** Caller ghi ở A nhưng đánh dấu rollback-only. Worker mở B trên thread khác, ghi bảng worker và commit trước khi A rollback; A không tự rollback B.

**5.** Tại sao demo gọi `AsyncGateway` và `TransactionalWorker` qua hai bean riêng?

**Đáp án:** Để `@Async` advice chuyển invocation sang worker trước, rồi `@Transactional` proxy của bean khác mở transaction trên worker; tránh self-invocation và giảm nhầm thứ tự advice.

**6.** Caller insert order chưa commit rồi submit worker ship order. Worker có chắc nhìn thấy order không?

**Đáp án:** Không. Worker dùng connection/transaction riêng và phụ thuộc isolation/lock; nên chỉ dispatch sau commit hoặc dùng outbox khi công việc gắn với order đã commit.

**7.** `@TransactionalEventListener(AFTER_COMMIT)` đã đủ bảo đảm task không bị mất qua crash chưa?

**Đáp án:** Chưa. Nó chọn thời điểm callback theo transaction, không phải durable broker. Để bảo đảm phục hồi sau crash, cần lưu công việc/outbox trong transaction nguồn và retry/idempotency.

**8.** Một listener đồng bộ ở phase AFTER_COMMIT tự ghi database trong method listener; có thể cần gì?

**Đáp án:** Tài nguyên của transaction vừa commit có thể vẫn bound ở callback. Nếu cần một write có commit riêng, gọi bean khác `@Transactional(REQUIRES_NEW)` qua proxy; hoặc chuyển việc sang async worker có transaction mới phù hợp.

**9.** `Future.get(2, SECONDS)` timeout. Điều đó có chứng minh worker transaction đã rollback không?

**Đáp án:** Không. Caller chỉ hết ngân sách chờ; worker có thể đang chạy và commit sau đó. Cần idempotency và timeout I/O/transaction phù hợp.

**10.** Worker có thể dùng trực tiếp `EntityManager` hoặc JDBC connection từ caller không?

**Đáp án:** Không nên. Resource gắn transaction/thread không được thiết kế để chia sẻ qua worker; truyền ID/DTO và lấy resource riêng trong transaction worker.

### Ghi nhớ

Khi **nghiệp vụ cho phép một unit of work độc lập**, hãy để bean xử lý mở transaction trên **thread thực sự chạy công việc**: `@Async` qua proxy đến worker, rồi gọi một bean khác có `@Transactional(REQUIRED)` hoặc dùng `TransactionTemplate`. Nếu trên worker đã có transaction và cần **physical transaction độc lập**, mới xét `REQUIRES_NEW` cùng chi phí connection. Nếu công việc chỉ hợp lệ sau khi caller commit, hãy dùng cơ chế **sau commit**; nếu không được mất việc qua crash, hãy cân nhắc **transactional outbox**. Đừng dùng transaction annotation như một cách “ghép” ACID qua các thread.

### Tài liệu tham khảo chính thức

- [Spring Framework — Declarative Transaction Implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): transaction gắn thread trong imperative model và cơ chế proxy.
- [Spring Framework — Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html): REQUIRED, REQUIRES_NEW, NESTED và tài nguyên/connection.
- [Spring Framework — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html): `@TransactionalEventListener`, AFTER_COMMIT và ranh giới callback.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableAsync`, `@Async`, executor, proxy mode.
- [Spring Framework — TransactionTemplate](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/transaction/support/TransactionTemplate.html): quản lý transaction bằng code.
- [Spring Framework — TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html): trạng thái và resource bound theo thread.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactions-events-003">Thời điểm commit của bên gửi và nguy cơ bên nhận đọc dữ liệu chưa hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# Thời điểm commit của bên gửi và nguy cơ bên nhận đọc dữ liệu chưa hoàn tất

> **Module:** Spring Framework – concurrency  
> **Nhóm:** Giao dịch và sự kiện  
> **Mục tiêu:** Hiểu tại sao một service công bố sự kiện **trước khi giao dịch ghi dữ liệu commit** có thể khiến consumer không tìm thấy hoặc nhìn thấy trạng thái chưa đầy đủ; biết dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` đúng chỗ, phân biệt **thời điểm hiển thị dữ liệu** với **đảm bảo phát sự kiện**, và lựa chọn transactional outbox khi cần độ tin cậy cao.

## 1. Vấn đề cốt lõi

Giả sử service A tạo đơn hàng trong giao dịch DB và gửi sự kiện `OrderCreated(42)` cho service B. Service B nhận event rồi đọc bảng `orders` bằng **một kết nối hoặc một cơ sở dữ liệu khác**.

Nếu A gửi event khi transaction vẫn mở:

~~~text
Service A / Transaction T1                 Service B / Connection T2
--------------------------                 -------------------------
BEGIN
INSERT orders(id=42, status=NEW)
publish OrderCreated(42) ----------------> nhận event
                                          SELECT ... WHERE id=42
                                          -> không thấy row, hoặc thấy bản cũ
COMMIT
                                          event đã được xử lý quá sớm
~~~

Dưới isolation thông dụng như `READ_COMMITTED`, một transaction độc lập **không được thấy dữ liệu chưa commit** của transaction khác. Kết quả là B có thể báo `order not found` dù vài mili giây sau A commit thành công. Nếu A rollback, event đã phát còn có thể mô tả một đơn hàng **chưa bao giờ tồn tại ở trạng thái đã commit**.

Điểm quan trọng không phải message broker nhanh hay chậm. Đây là **lỗi thứ tự và ranh giới transaction**: công bố sự kiện ra ngoài trước khi trạng thái DB trở thành durable/visible đối với reader độc lập.

**Quy tắc:** Khi consumer cần truy vấn trạng thái đã commit của producer, hãy bảo đảm việc xử lý sự kiện chỉ bắt đầu sau khi transaction producer **commit thành công**. Nhưng đừng kết luận rằng “chuyển gửi sự kiện sang after-commit” tự động bảo đảm event sẽ không bao giờ bị mất: đó là một vấn đề thứ hai.

## 2. Phân biệt ba thời điểm

Có ba mốc khác nhau thường bị gộp làm một:

1. **Ghi trong transaction:** `INSERT`/`UPDATE` đã thực hiện qua connection của producer, nhưng chưa hoàn tất commit. Code trong **cùng transaction** thường đọc lại được thay đổi của chính nó; reader transaction khác thường chưa thấy.
2. **Commit thành công:** DB transaction manager yêu cầu commit, DB chấp nhận trạng thái mới. Sau đó, một read mới vào primary với isolation thích hợp thông thường thấy các thay đổi. Điều này không có nghĩa mọi read replica/cache đã cập nhật tức thì.
3. **Event đã được bên nhận xử lý:** message thực sự được broker/consumer nhận và xử lý; phụ thuộc queue, thread pool, retry, mạng, acknowledgement và trạng thái consumer.

`publishEvent(...)` trong Spring là **yêu cầu công bố event trong ứng dụng**, không phải chứng chỉ DB đã commit; `KafkaTemplate.send(...)` cũng không ngầm thực hiện commit của giao dịch SQL trước đó.

Cũng cần phân biệt “producer đã gọi API gửi” với “broker đã lưu bền”, “consumer đã nhận” và “consumer đã xử lý thành công”. Chúng có những mốc xác nhận khác nhau; trả về từ một API gửi asynchronous không chứng minh toàn bộ pipeline thành công.

## 3. Vì sao một consumer có thể đọc thiếu dữ liệu?

Tình huống thường gặp nhất: producer mở transaction, ghi nhiều bảng, gửi event ở giữa hoặc ngay sau câu SQL cuối cùng **nhưng trước commit**. Consumer bắt đầu làm việc sớm hơn producer hoàn tất commit.

Có những kết quả khác nhau theo thiết kế hệ thống:

| Điều kiện consumer | Điều consumer có thể thấy |
| --- | --- |
| Đọc trên connection khác với `READ_COMMITTED` trước commit | Không thấy bản ghi mới; dữ liệu cập nhật cũ còn hiển thị |
| Đọc trong cùng transaction/connection của producer | Có thể thấy thay đổi chưa commit của chính mình; **không đại diện** cho consumer độc lập |
| Đọc sau commit trên primary | Thông thường thấy phiên bản đã commit, nếu query đúng ID/isolation và không bị điều kiện lọc khác |
| Đọc sau commit nhưng transaction reader đang ở snapshot cũ (`REPEATABLE_READ`/MVCC) | Có thể tiếp tục thấy snapshot trước đó cho đến khi transaction reader kết thúc |
| Đọc replica/caches/materialized view sau commit | Có thể thấy trạng thái cũ do replication lag, cache invalidation hoặc projection lag |
| DB isolation cho phép dirty read (một số engine/cấu hình) | Có thể thấy dữ liệu chưa commit, nhưng kết quả có rủi ro rollback/đọc bẩn |

Do đó, câu “chỉ cần publish sau commit thì consumer chắc chắn đọc thấy ngay” **đúng trong một phạm vi hẹp**, không phải mọi database topology. Phải xét primary/replica, transaction reader, cache và độ trễ eventual consistency.

**Dữ liệu chưa hoàn tất** còn có nghĩa nghiệp vụ chưa ổn định: đơn hàng có thể được ghi qua nhiều bảng, một bên chưa cập nhật inventory hoặc transaction sắp rollback. Nếu producer mở **một transaction** bao trùm tất cả thay đổi liên quan, commit giúp chúng trở thành một trạng thái nhất quán với phạm vi atomicity của DB; nhưng thay đổi ở hệ thống bên ngoài **không tự tham gia** transaction đó.

## 4. @EventListener thông thường không tự chờ commit

Trong Spring Framework, `ApplicationEventPublisher.publishEvent(event)` công bố một application event cho listener. Với `SimpleApplicationEventMulticaster` mặc định không cấu hình executor, listener `@EventListener` thông thường chạy **đồng bộ trong thread đang phát sự kiện**.

~~~java
@Transactional
public void createOrder(Command command) {
    orderRepository.save(toOrder(command));
    publisher.publishEvent(new OrderCreated(command.orderId()));
    // Giao dịch có thể vẫn CHƯA commit tại đây.
}

@EventListener
public void onOrderCreated(OrderCreated event) {
    // Có thể được gọi trước khi transaction bên gửi commit.
}
~~~

Đây là **snippet tích hợp**; `Command`, `OrderCreated`, repository và injection thuộc ứng dụng. Sau khi `publishEvent` trả về, method transactional vẫn có thể tiếp tục chạy và chỉ commit **khi rời transaction interceptor**. `@EventListener` không phải cơ chế chờ DB commit.

Có một bẫy khi tự kiểm thử: listener đồng bộ có thể truy cập DB bằng `JdbcTemplate` trên **cùng thread** và tham gia connection đã bind vào transaction của producer. Nó có thể đọc thấy chính row chưa commit, tạo ấn tượng rằng mọi consumer đều thấy được. Để mô phỏng consumer thật, cần **một connection/transaction khác**, như ví dụ mục 6.

Nếu listener được `@Async` hóa, hoặc multicaster được cấu hình gửi listener chạy trên executor, lúc thực thi trên thread khác sẽ có cuộc đua thời gian rõ ràng hơn: consumer có thể chạy **trước hoặc sau commit** tùy scheduling. `@Async` chỉ tạo tính bất đồng bộ, **không** tạo ràng buộc “sau commit”.

## 5. Một trục thời gian gây lỗi trong thực tế

~~~text
Thời điểm      Producer trong @Transactional             Consumer / broker
t0             BEGIN
t1             INSERT order 42
t2             UPDATE order_items...
t3             publish event OrderCreated(42) ----------> enqueue
t4                                                        receive
t5                                                        SELECT order 42
t6                                                        -> not found
t7             COMMIT thành công
t8                                                        đã báo lỗi hoặc retry
~~~

Nguyên nhân gốc ở t3: producer công bố trước khi DB commit. Khi consumer nhận thông điệp nhanh, việc tìm thấy row phụ thuộc tốc độ commit, không còn là một hợp đồng đảm bảo.

**Vòng retry tức thời không phải lời giải đúng.** Nó có thể may mắn chạy lại sau commit trong một số lần, nhưng sẽ thất bại dai dẳng nếu producer rollback, replica chậm, ID sai hoặc transaction reader giữ snapshot cũ. Nó cũng làm tăng tải DB/broker lúc hệ thống đang bận.

Có thể gặp cùng lỗi với email gửi link đến hóa đơn chưa commit, webhook cho bên thứ ba, cập nhật search index từ event, consumer Kafka đọc bảng orders, hoặc một `@Async` task nội bộ chạy truy vấn ngay sau `save()`.

## 6. Ví dụ chạy được: Spring Framework + H2 chứng minh khả năng hiển thị

Ví dụ dùng **Spring Framework 6.2.9**, **H2 2.3.232** và **Java 17+**. Producer chạy trong transaction của `DataSourceTransactionManager`, ghi một order rồi `publishEvent`. Hai listener cùng nhận `OrderCreated` nhưng ở **hai thời điểm** khác nhau:

- `@EventListener`: chạy ngay khi publish; kiểm tra row bằng connection JDBC **độc lập** và thấy `false` vì producer chưa commit.
- `@TransactionalEventListener(phase = AFTER_COMMIT)`: chỉ chạy sau commit thành công; connection JDBC độc lập thấy `true`.

Ví dụ cố ý dùng `DriverManagerDataSource` để mỗi `getConnection()` trả connection mới, **không dùng pool** trong demo. Vì đây là DB H2 in-memory với isolation mặc định `READ_COMMITTED`, kiểm chứng trực tiếp tính visible-to-other-connection.

### Cấu trúc project

~~~text
commit-visibility-demo/
├── pom.xml
└── src/main/java/demo/CommitVisibilityDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>commit-visibility-demo</artifactId>
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
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-jdbc</artifactId>
      <version>6.2.9</version>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <version>2.3.232</version>
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

### src/main/java/demo/CommitVisibilityDemo.java

~~~java
package demo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class CommitVisibilityDemo {
    public record OrderCreated(long id) {}

    @Bean
    DataSource dataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
            "jdbc:h2:mem:commit_visibility;DB_CLOSE_DELAY=-1", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        return ds;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    DataSourceTransactionManager transactionManager(DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean
    TransactionTemplate transactionTemplate(
            DataSourceTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    Observer observer(DataSource ds) {
        return new Observer(ds);
    }

    public static class Observer {
        private final DataSource ds;

        public Observer(DataSource ds) {
            this.ds = ds;
        }

        private boolean committedRowVisible(long id) {
            try (Connection connection = ds.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                     "select count(*) from orders where id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getInt(1) == 1;
                }
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @EventListener
        public void onImmediate(OrderCreated event) {
            System.out.println(
                "Immediate listener sees order: " +
                    committedRowVisible(event.id()));
        }

        @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT)
        public void onCommitted(OrderCreated event) {
            System.out.println(
                "After-commit listener sees order: " +
                    committedRowVisible(event.id()));
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(
                     CommitVisibilityDemo.class)) {

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            TransactionTemplate tx =
                context.getBean(TransactionTemplate.class);
            ApplicationEventPublisher publisher = context;

            jdbc.execute(
                "create table orders (id bigint primary key, " +
                "status varchar(32))");

            tx.executeWithoutResult(status -> {
                jdbc.update(
                    "insert into orders (id, status) values (?, ?)",
                    42L, "NEW");
                publisher.publishEvent(new OrderCreated(42L));
            });

            Integer count = jdbc.queryForObject(
                "select count(*) from orders where id = 42",
                Integer.class);
            System.out.println(
                "After transaction sees order: " + (count == 1));
        }
    }
}
~~~

**Lưu ý quan trọng:** `@EnableTransactionManagement` đăng ký infrastructure transaction và support cho `@TransactionalEventListener` trong ví dụ Spring Framework thuần. Nếu không bật cơ chế transaction management hoặc không có `TransactionalEventListenerFactory`, không được kỳ vọng annotation tự nhiên có tác dụng đúng phase. Một Spring Boot app có transaction auto-configuration thường đã chuẩn bị infrastructure này, nhưng vẫn phải xác minh context thực tế.

Chạy trong thư mục gốc project:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.CommitVisibilityDemo
~~~

**Stdout của chương trình:**

~~~text
Immediate listener sees order: false
After-commit listener sees order: true
After transaction sees order: true
~~~

Mã đã được **biên dịch target Java 17 và chạy thực tế** với Spring Framework 6.2.9 + H2 2.3.232 trên JDK mới hơn; ba dòng stdout trên được đối chiếu. `@EventListener` chạy trước commit, trong khi `@TransactionalEventListener` được gọi sau commit. `Observer` dùng một connection mới để **không** đọc qua connection đang bind vào transaction của producer.

Nếu đổi test thành đọc qua `JdbcTemplate` trên cùng thread của listener immediate, kết quả có thể khác vì nó tham gia transaction đang mở. Điều đó **không bác bỏ** nguy cơ consumer bên ngoài không thấy row.

Chương trình không dùng Kafka/RabbitMQ. Nó mô phỏng **thời điểm và isolation DB** trong cùng tiến trình để giải thích race. Với broker thật, callback dispatch có thêm độ trễ và các lỗi giao nhận riêng; không thể dựa vào tốc độ broker để “tình cờ sau commit”.

## 7. @TransactionalEventListener và các giai đoạn transaction

Spring cung cấp `@TransactionalEventListener` để liên kết listener với **phase** của giao dịch chứa lời gọi publish event.

~~~java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT)
public void afterCommit(OrderCreated event) {
    // Chỉ xử lý khi transaction đã commit thành công.
}
~~~

Đây là **đoạn tích hợp**, phải có `OrderCreated` và transaction-event infrastructure đúng context. Giá trị `AFTER_COMMIT` là mặc định nếu không truyền `phase`.

| Phase | Thời điểm | Phù hợp với |
| --- | --- | --- |
| `BEFORE_COMMIT` | Trước khi transaction commit | Kiểm tra/thao tác gắn với transaction trước commit; **không** phải tín hiệu DB đã durable |
| `AFTER_COMMIT` | Sau commit thành công | Gửi thông báo hoặc đánh dấu bước tiếp theo cần dựa trên state đã commit |
| `AFTER_ROLLBACK` | Sau khi transaction rollback | Quan sát rollback/ghi nhận lỗi theo cơ chế tách biệt |
| `AFTER_COMPLETION` | Sau completion, commit hoặc rollback | Cleanup/telemetry không phụ thuộc kết quả cụ thể, phải tự phân biệt nếu cần |

Khi **không có active transaction** tại thời điểm event được publish, mặc định listener `@TransactionalEventListener` **không chạy**. Có thể đặt `fallbackExecution = true` để chạy ngoài transaction, nhưng lựa chọn đó thay đổi hợp đồng nghiệp vụ; không dùng chỉ để “chữa lỗi không thấy listener chạy”.

Trong transaction rollback, listener `AFTER_COMMIT` không chạy. Vì vậy nó tránh được một lớp **sự kiện sai do rollback** mà `@EventListener` thông thường có thể mắc.

**Đừng nhầm @TransactionalEventListener với @Async.** Listener theo phase, nếu không được cấu hình async riêng, không đương nhiên chuyển sang worker khác. Nếu thêm `@Async`, listener có thể được nộp để chạy sau commit nhưng xảy ra lỗi nộp, shutdown hoặc thiếu concurrency; cần chính sách riêng.

## 8. Cảnh báo khó thấy: tài nguyên transaction sau commit có thể còn được bind

Spring Javadoc nêu một lưu ý quan trọng: tại `AFTER_COMMIT`/`AFTER_ROLLBACK`/`AFTER_COMPLETION`, DB transaction đã kết thúc, **nhưng resource gắn với transaction có thể vẫn còn active/bound với thread** trong giai đoạn callback.

Hệ quả: nếu listener gọi code DB tham gia lại resource cũ, thao tác ghi có thể tưởng vẫn trong transaction và **không được commit như bạn mong** vì commit chính đã xảy ra. Không nên suy luận rằng `AFTER_COMMIT` mặc nhiên tạo một transaction mới cho mọi lời gọi repository.

Nếu callback cần **ghi trạng thái bền vững mới sau commit**, hãy gọi một service bean khác với transaction mới thật sự:

~~~java
@Service
public class AuditWriter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSent(long orderId) {
        auditRepository.insert(orderId);
    }
}
~~~

Đây là **đoạn tích hợp**, cần `@Service`, `@Transactional`, `Propagation`, `auditRepository` và transaction manager thích hợp. Caller phải gọi **qua Spring-managed proxy của service khác**; gọi `this.recordSent(...)` trong cùng bean thường bỏ qua advice trong proxy mode.

Tuy nhiên `REQUIRES_NEW` chỉ tạo transaction DB mới, **không** giải quyết toàn bộ vấn đề gửi message ra broker đúng một lần. Nếu crash giữa commit order và lần ghi audit/event mới, vẫn có cửa sổ mất thao tác. Những workflow quan trọng nên cân nhắc **transactional outbox**.

Trong demo mục 6, listener hậu commit **chỉ đọc qua connection độc lập** và không ghi trong resource cũ; điều đó cố ý tránh cạm bẫy trên.

## 9. Phân biệt tính đúng thời điểm với tính chắc chắn của giao sự kiện

Có hai câu hỏi hoàn toàn khác:

1. **Consumer có bị chạy trước khi DB commit không?** Sử dụng `AFTER_COMMIT` có thể đảm bảo một **callback trong cùng ứng dụng** được gọi sau commit thành công, giải quyết race nội bộ này.
2. **Event có chắc được lưu/gửi đến consumer sau commit không?** Chưa chắc. Sau khi DB commit nhưng trước khi gửi message bền vững thành công, JVM có thể crash, broker có thể từ chối hoặc network timeout. DB đã có order nhưng không có event tương ứng.

Timeline khác:

~~~text
BEGIN DB transaction
INSERT order 42
COMMIT thành công     <-- dữ liệu đã bền vững
    |
    +-- JVM crash / broker unavailable
    |
    +-- publish event never completed
        --> consumer không bao giờ được thông báo nếu không có recovery
~~~

Do đó, `@TransactionalEventListener(AFTER_COMMIT)` là lựa chọn hữu ích cho **event phụ trợ, xử lý cục bộ hoặc notification chấp nhận mất có thể phục hồi**, nhưng **không phải cơ chế bảo đảm event delivery bền vững giữa DB và broker**.

Ngay cả khi lời gọi send trong callback thành công, vẫn còn các trạng thái “broker đã nhận nhưng ACK bị mất” hoặc “consumer xử lý rồi crash trước khi xác nhận”; thiết kế đúng cần xử lý **trùng lặp** thay vì mặc định exactly-once từ đầu đến cuối.

## 10. Transactional outbox: ghi dữ liệu và ý định phát sự kiện trong một commit

Với nghiệp vụ yêu cầu **khi tạo order thành công phải có event tương ứng**, mẫu phổ biến là **transactional outbox**:

- Trong **cùng một DB transaction**, ghi record nghiệp vụ vào `orders` và ghi một record event vào bảng `outbox_events`.
- **Chỉ sau commit**, một poller hoặc CDC connector lấy outbox record đã commit để gửi đến broker.
- Nếu transaction rollback, cả order và event record đều rollback.
- Nếu app crash sau commit nhưng trước khi gửi broker, record outbox vẫn tồn tại và có thể được gửi lại.
- Vì poller có thể gửi lại khi mất ACK, consumer phải **idempotent**.

Luồng điển hình:

~~~text
Producer transaction T1
   |
   +-- INSERT orders(id=42,...)
   +-- INSERT outbox_events(event_id=E-1, aggregate_id=42,...)
   |
   +-- COMMIT atomically
             |
             v
  Poller/CDC chỉ nhìn thấy outbox đã commit
             |
             v
       Message broker
             |
             v
  Consumer nhận event và đọc state/order
             |
             +-- Dedupe event_id + áp dụng idempotent
~~~

**Outbox giải quyết khoảng trống dual-write**, vì intent phát sự kiện tồn tại cùng transaction với dữ liệu. Nó không bắt Kafka, RabbitMQ hay DB tham gia cùng một global transaction. Tuy nhiên nó **không loại bỏ duplicate**, không tự bảo đảm strict global ordering giữa nhiều aggregate và không giải quyết replication lag ở consumer nếu họ đọc bản sao chậm.

## 11. Ví dụ thiết kế outbox trong Spring

Ví dụ schema **minh họa theo SQL PostgreSQL** (không phải schema di động cho mọi engine):

~~~sql
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ NULL
);
~~~

Một phương thức Spring `@Transactional` có thể ghi cả hai bảng:

~~~java
@Service
public class OrderCommandService {
    private final JdbcTemplate jdbc;

    public OrderCommandService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void create(long orderId, String eventJson, UUID eventId) {
        jdbc.update(
            "insert into orders(id, status) values (?, ?)",
            orderId, "NEW");

        jdbc.update(
            "insert into outbox_events" +
            "(event_id, aggregate_id, event_type, payload) " +
            "values (?, ?, ?, ?)",
            eventId, orderId, "OrderCreated", eventJson);
    }
}
~~~

Đây là **snippet tích hợp**: cần imports `JdbcTemplate`, `UUID`, `@Service`, `@Transactional`; datasource, migration và JSON serializer do ứng dụng cung cấp. Không phải một tệp Java chạy độc lập.

Cả hai `jdbc.update` phải dùng **cùng transaction manager và cùng DB transaction thực sự**, gọi method qua Spring transaction proxy. Nếu statement outbox thất bại rồi exception được truyền cho interceptor theo rollback rule, order cũng rollback. Nếu commit thành công, poller có thể đọc cả order lẫn outbox row.

Một schema production còn cần index cho polling, trạng thái lease/retry, số lần giao, last error, tenant/correlation ID, schema version, chính sách retention và bảo mật payload. Không lưu bí mật/PII nhạy cảm vào broker một cách vô kiểm soát.

**Đừng gửi broker bằng cách trực tiếp gọi `broker.send` giữa hai lệnh INSERT trong transaction** rồi coi đó là cùng một atomic commit: broker không tự tham gia DB transaction.

## 12. Poller, CDC và giới hạn của outbox

Có hai cách phổ biến đưa event outbox đi:

**Poller:** worker định kỳ lấy các row chưa gửi, claim/lock có giới hạn, publish message, rồi cập nhật trạng thái. Với nhiều instance, cần một cơ chế lease/lock đúng DB (ví dụ `FOR UPDATE SKIP LOCKED` trong PostgreSQL ở transaction ngắn), tránh các worker đồng thời chọn cùng một row theo cách không kiểm soát.

**CDC (change data capture):** một connector đọc thay đổi đã commit từ transaction log của DB, biến row outbox thành message. Debezium Outbox Event Router là một giải pháp ví dụ.

Cả hai vẫn phải xử lý:

- Broker không phản hồi, poller bị restart, transaction cập nhật `published_at` thất bại.
- Broker **đã nhận** message nhưng ACK mất, poller gửi lại cùng `event_id`.
- Message bị gửi/lưu trữ sai partition; các event cho cùng aggregate có thể bị nhận không theo thứ tự nếu không dùng khóa partition/version đúng.
- Backpressure: consumer xử lý chậm, backlog outbox tăng; cần metric lag, số event pending, retry và dead-letter strategy.
- Purge/retention: không xóa outbox row trước khi bảo đảm nó không còn cần recovery; xác nhận hợp đồng lưu trữ, kiểm soát archive.

Nếu production thực sự cần *exactly-once effect* lên business state, điều này thường phải đạt bằng **idempotent consumer, transaction ở bên nhận và deduplication**, không chỉ bằng kỳ vọng broker sẽ không gửi trùng.

## 13. Consumer sau commit vẫn phải chấp nhận eventual consistency

Thậm chí khi producer ghi outbox và commit trước khi message được gửi, consumer có thể đọc một **read replica chậm**, một cache chưa invalidated hoặc một search projection chưa cập nhật. Sự kiện đã hợp lệ nhưng phần dữ liệu consumer đọc chưa đuổi kịp.

Các chiến lược tùy nhu cầu:

1. **Event chứa đủ snapshot tối thiểu:** nếu consumer chỉ cần `orderId`, `status`, `total` tại thời điểm commit, có thể đặt các field đó (đã chuẩn hóa và kiểm soát privacy) trong payload; không bắt consumer luôn đi đọc lại DB producer.
2. **Read-your-writes/primary read:** với dữ liệu cần mới nhất, consumer có thể gọi API đọc primary (nếu được phép), thay vì read replica có lag.
3. **Version/sequence:** gắn `aggregateVersion` để consumer biết mình đang xử lý trạng thái nào; tránh event cũ ghi đè projection mới.
4. **Bounded retry có jitter:** nếu API đọc chỉ tạm thời chưa thấy row do replication lag, retry có giới hạn và deadline có thể hợp lý. Không biến “not found” thành vòng retry vô hạn.
5. **Reconciliation:** có job đối chiếu dữ liệu producer–consumer để sửa những sự kiện bị lỗi, thất lạc hoặc projection lệch.

Sự kiện “order được tạo” là một **sự thật nghiệp vụ có thời điểm**, không bảo đảm đối tượng sẽ mãi ở trạng thái `NEW`. Đến lúc consumer đọc, order có thể đã chuyển `PAID` hoặc `CANCELLED`. Nếu cần trạng thái “lúc phát event”, hãy gửi payload/version đúng thay vì coi mọi read hiện tại là dữ liệu của thời điểm event.

## 14. Idempotency và thứ tự sự kiện bên nhận

**Idempotency:** với mỗi event, producer nên tạo `eventId` ổn định. Consumer lưu event đã xử lý hoặc dùng unique constraint/business key để hai lần giao cùng event không tạo tác dụng phụ hai lần.

Một flow consumer dựa trên DB transaction:

~~~text
BEGIN
  INSERT processed_events(consumer_id, event_id)
      -- unique(consumer_id, event_id)
  IF duplicate:
      -- bỏ qua business effect trùng
  ELSE:
      UPDATE projection / apply side effect nội bộ
COMMIT
ACK message sau khi DB commit
~~~

Đây là **sơ đồ logic**, không phải SQL chạy nguyên xi. Việc insert dedupe marker và cập nhật business state nên nằm trong **cùng transaction của consumer**. Nếu ACK gửi trước commit, crash giữa hai bước có thể làm consumer bỏ mất effect. Nếu commit xong nhưng ACK mất, broker có thể gửi lại; dedupe xử lý được duplicate.

Với email hoặc thanh toán ở **hệ thống ngoài DB consumer**, một transaction nội bộ không bao trùm được side effect bên ngoài. Cần idempotency key của API bên ngoài, outbox/saga và reconciliation khi kết quả không rõ.

**Thứ tự:** `OrderCreated`, `OrderPaid` và `OrderCancelled` có thể đến lệch do nhiều partition/retry. Nếu trạng thái cần đúng trình tự, đưa event của cùng aggregate vào một key partition phù hợp và kiểm tra `aggregateVersion`, hoặc xây dựng quy tắc xử lý out-of-order. “Publish after commit” không tự bảo đảm order trên một hệ phân tán.

## 15. @Async và transaction context: hai thứ không tự truyền qua thread

Một anti-pattern dễ thấy trong Spring:

~~~java
@Transactional
public void createOrder(Order order) {
    repository.save(order);
    asyncNotifier.notifyCreated(order.getId());
    // Transaction trong caller vẫn có thể chưa commit.
}

@Async
public void notifyCreated(Long orderId) {
    // Đang chạy ở thread khác.
    externalConsumerOrDbRead(orderId);
}
~~~

Đây là **đoạn minh họa tích hợp**, không phải Java class độc lập. Nếu method `@Async` được gọi qua proxy, executor có thể chạy ngay khi được submit, **trước khi transaction trong caller commit**.

Transaction imperative của Spring gắn với thread: worker không tự được chia sẻ transaction đang mở của producer. Đây là lý do việc `@Async` không làm event được “an toàn hơn”; nó thường làm race rõ hơn.

Cũng đừng gửi một Hibernate/JPA entity đang managed hoặc một lazy-loaded proxy vào async event rồi tin rằng consumer sẽ truy cập được mọi property. Session/transaction có thể đóng; trạng thái object có thể mutable, lười tải hoặc không còn nhất quán. Dùng **event payload immutable** chứa ID, version, các field cần thiết, hoặc một DTO snapshot đúng contract.

Nếu muốn xử lý **sau commit nhưng không chặn thread commit**, có thể dùng một listener `@TransactionalEventListener(AFTER_COMMIT)` kích hoạt async task đã giới hạn concurrency. Cách này giải quyết thứ tự bắt đầu task trong nội bộ, nhưng vẫn có các rủi ro **sau commit nhưng chưa enqueue/persist**; với event bắt buộc phải giao, outbox thường phù hợp hơn.

## 16. Exception và rollback: đừng lấy kết quả publish làm kết quả commit

Một method `@Transactional` có thể publish event rồi cuối cùng ném exception:

~~~java
@Transactional
public void createOrder(long id) {
    repository.insert(id);
    publisher.publishEvent(new OrderCreated(id));
    if (businessRuleRejected(id)) {
        throw new IllegalStateException("Rejected");
    }
}
~~~

Đây là snippet mô phỏng, không có class chạy độc lập. Với rollback rule mặc định cho unchecked exception và một transaction manager phù hợp, database insert có thể rollback. Nhưng event thông thường được publish trước đó **không thể thu hồi** nếu consumer đã nhận hoặc message đã được broker ghi.

Khi dùng `@TransactionalEventListener(AFTER_COMMIT)`, callback không chạy nếu producer transaction rollback đúng cách. Tuy nhiên phải hiểu rollback rule thực tế:

- Checked exception không mặc định kích hoạt rollback theo cùng cách với unchecked exception nếu không cấu hình.
- Nếu code bắt exception rồi kết thúc bình thường, transaction có thể commit trừ khi đã `setRollbackOnly()` hoặc có cấu hình khác.
- Việc tham gia transaction sẵn có và `Propagation.REQUIRES_NEW` thay đổi ranh giới commit.
- Self-invocation của `@Transactional` trong proxy mode có thể bỏ qua transaction advice.
- Chỉ có **một transaction thực sự bao trùm** các write thì commit mới cho visibility mong muốn.

Nói “method đã chạy xong” chưa đủ; cần xác minh transaction nào thực sự commit và khi nào. Với `TransactionTemplate`, callback transaction chỉ trả về sau khi commit/rollback của transaction mà template quản lý hoàn tất.

## 17. Test đúng: quan sát từ transaction/connection khác

Các test chỉ assert `repository.save()` hoặc gọi `findById()` trong cùng transaction không chứng minh consumer bên ngoài nhìn thấy dữ liệu đã commit. Một integration test đúng cần:

1. **Primary DB và transaction thật:** test dưới `READ_COMMITTED` hoặc isolation tương ứng, không dựa trên mock `EntityManager`.
2. **Connection độc lập:** producer giữ transaction chưa commit, consumer query qua connection khác hoặc thread khác, xác nhận không thấy row trước commit.
3. **After commit:** commit producer rồi query lại bằng transaction đọc mới, xác nhận row hiển thị.
4. **Rollback:** bắt transaction rollback; listener `AFTER_COMMIT` không chạy và consumer không thấy row chưa commit.
5. **Race có kiểm soát:** dùng `CountDownLatch` để giữ producer trước commit; kích hoạt consumer trước/sau qua latch. Không dựa vào `Thread.sleep(10)` và hy vọng timing đúng.
6. **Spring proxy:** test `@Transactional` và `@Async` qua các Spring-managed beans; `this.method()` có thể vô hiệu hóa advice.
7. **Transaction event factory:** bật `@EnableTransactionManagement` trong Framework test hoặc cấu hình tương đương; xác nhận listener có chạy đúng phase.
8. **Mất event sau commit:** giả lập broker/poller unavailable sau commit; nếu dùng outbox, record vẫn tồn tại và có thể redeliver.
9. **Duplicate:** gửi cùng `eventId` hai lần; consumer không ghi business effect trùng.
10. **Replica lag và out-of-order:** cho consumer nhìn snapshot cũ hoặc nhận version sau trước; xác nhận logic retry/version không ghi đè trạng thái đúng.

Khi dùng `@Transactional` trên **phương thức test** (ví dụ `@Transactional` của Spring Test) và framework tự rollback cuối test, listener `AFTER_COMMIT` có thể **không chạy**. Đây là lý do cần một test điều khiển commit thật bằng `TransactionTemplate` hoặc test transaction API phù hợp.

Trong bài demo mục 6, commit được điều khiển tường minh nên không bị che bởi transaction rollback mặc định của test framework.

## 18. Chẩn đoán production bằng log và metric

Nên có correlation xuyên suốt producer → outbox/broker → consumer:

| Trường | Ý nghĩa |
| --- | --- |
| `eventId` | Khóa duy nhất phục vụ dedupe, truy vết retry |
| `aggregateId` | Order/customer/tài nguyên nghiệp vụ liên quan |
| `aggregateVersion` | Dùng kiểm tra ordering và projection stale |
| `producedAt` | Thời gian logic event được tạo; **không** thay thế commit timestamp |
| `outboxCommittedAt` hoặc `createdAt` | Hỗ trợ ước lượng lag, cần hiểu cách DB/clock ghi nhận |
| `publishedAt` | Thời điểm broker send/ACK được ghi nhận theo policy |
| `consumedAt` | Thời điểm consumer bắt đầu/hoàn tất xử lý |
| `traceId` | Liên kết request và background processing mà không dùng raw PII |

Các metric thiết yếu: event pending trong outbox, **oldest pending age**, publish retries, broker acknowledgement failures, duplicate events, consumer processing latency, row-not-found sau event, replication lag và dead-letter count. Tránh dùng raw eventId/orderId làm metric label có cardinality cao; dùng log hoặc tracing có phân quyền.

Nếu consumer hay gặp `not found` ngay sau event, kiểm tra **thời điểm publish so với commit**, isolation, read replica routing và transaction snapshot của consumer. Tăng số retry có thể chỉ che giấu race và làm DB nóng hơn.

## 19. Ma trận lựa chọn theo độ tin cậy

| Nhu cầu | Cách tiếp cận | Hợp đồng và giới hạn |
| --- | --- | --- |
| Listener nội bộ chỉ cần quan sát event sau commit | `@TransactionalEventListener(AFTER_COMMIT)` | Đúng phase; không tự đảm bảo event surviving crash |
| Async notification không quá quan trọng, có thể tái tạo | `AFTER_COMMIT` rồi submit task tới executor có giới hạn | Có rủi ro crash/rejection sau commit; cần monitor |
| Bắt buộc broker nhận event cho mọi order đã commit | Transactional outbox + poller/CDC | Atomic DB state+intent; consumer idempotent, monitoring retry |
| Consumer đọc replica/projection có lag | Payload/version + read policy + bounded retry/reconciliation | After commit vẫn có eventual consistency |
| Nhiều producer hoặc message broker ordering phức tạp | Aggregate version/partitioning + idempotency | Không suy luận global order từ local commit |
| Cross-service state cập nhật theo nhiều bước | Saga/workflow và compensation, outbox mỗi chặng nếu cần | Không có một local DB transaction cho toàn hệ thống |

Không có giải pháp đơn nhất cho mọi workload. **Chọn trước hợp đồng nghiệp vụ**: chấp nhận event mất hay không, consumer cần payload gì, phải trả lời trong bao lâu, làm sao khôi phục khi crash, và xử lý được duplicate/out-of-order ở mức nào.

## 20. Những hiểu nhầm phổ biến

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “`repository.save()` đã trả về thì DB chắc chắn commit.” | Thao tác có thể nằm trong transaction chưa commit. |
| “`publishEvent()` mặc nhiên chờ giao dịch kết thúc.” | Listener thông thường có thể chạy ngay trong transaction đang mở. |
| “Listener đọc được row nghĩa là consumer khác cũng đọc được.” | Listener có thể đang dùng **cùng connection/transaction** của producer. |
| “Đánh dấu `@Async` cho listener sẽ tránh race.” | Async làm scheduling độc lập; có thể chạy trước commit. |
| “`@TransactionalEventListener` chạy ngay cả khi không có transaction.” | Mặc định không chạy; `fallbackExecution=true` mới cho phép. |
| “`AFTER_COMMIT` là một transaction mới.” | Resource cũ có thể còn bound; ghi dữ liệu mới có thể cần `REQUIRES_NEW`. |
| “`AFTER_COMMIT` bảo đảm Kafka luôn có message.” | Có khoảng trống crash giữa DB commit và broker publish. |
| “Outbox nghĩa là consumer không bao giờ nhận trùng.” | Poller/CDC/broker có thể redeliver; cần idempotency. |
| “Sau commit thì replica ngay lập tức cập nhật.” | Replica, cache và projection có thể có lag. |
| “Một event luôn mô tả state hiện tại khi consumer đọc.” | State có thể đã tiến lên version mới. |
| “Transaction của producer truyền qua worker `@Async`.” | Imperative transaction gắn với thread/context, không tự truyền sang worker. |
| “Retry mãi lỗi `not found` là đủ an toàn.” | Có thể rollback, sai ID, thiếu outbox hoặc trùng tác dụng phụ; retry phải hữu hạn. |

## 21. Checklist thiết kế trước khi triển khai

- Tôi biết **giao dịch producer mở ở đâu, commit lúc nào**, có proxy `@Transactional` thực sự và rollback rule đúng.
- Tôi không phát sự kiện ra consumer độc lập trước commit nếu consumer cần state đã commit.
- Tôi phân biệt `@EventListener`, `@TransactionalEventListener(AFTER_COMMIT)` và `@Async`.
- Tôi kiểm tra listener transactional chỉ chạy khi có transaction (hoặc fallback được chọn có chủ đích).
- Tôi hiểu `AFTER_COMMIT` không tự tạo transaction mới; write từ callback cần ranh giới phù hợp.
- Tôi đã xác định **event mất sau DB commit** có chấp nhận được không; nếu không, có outbox/CDC hoặc cơ chế bền vững tương đương.
- Consumer có `eventId`/business key chống duplicate, đủ thông tin để xử lý out-of-order.
- Tôi xác nhận consumer đọc primary, replica hay cache và có phương án xử lý replication lag.
- Tôi giới hạn retry, backpressure, queue concurrency và có dead-letter/reconciliation.
- Tôi kiểm tra event chứa dữ liệu immutable theo contract, không phụ thuộc vào JPA managed entity/lazy session.
- Tôi có integration test trên hai connection với commit/rollback thật, không chỉ mock.
- Tôi theo dõi outbox lag, broker publish failure, consumer processing, duplicate và “row not found” bằng metric/log phù hợp.

**Kết luận:** Khi producer phát event trước khi giao dịch SQL commit, consumer độc lập có thể đọc thấy dữ liệu cũ hoặc chưa tồn tại; event còn có thể mô tả trạng thái bị rollback. `@TransactionalEventListener(AFTER_COMMIT)` giải quyết **thứ tự callback so với commit** trong Spring. Nếu nghiệp vụ còn cần **không mất sự kiện sau commit**, hãy ghi **business state và event intent cùng một DB transaction** (transactional outbox), rồi gửi qua poller/CDC và xử lý duplicate ở consumer. Kể cả sau commit, read replica/cache vẫn cần chính sách eventual consistency.

## Tài liệu tham khảo

- [Spring Framework: Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) — event gắn với transaction phase, fallbackExecution, imperative/reactive context.
- [Spring Framework 6.2: TransactionalEventListener Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html) — AFTER_COMMIT mặc định, AFTER_ROLLBACK, resource còn bound sau completion.
- [Spring Framework 6.2: TransactionPhase Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/transaction/event/TransactionPhase.html) — ý nghĩa BEFORE_COMMIT, AFTER_COMMIT, AFTER_ROLLBACK và AFTER_COMPLETION.
- [Spring Framework: Declarative Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html) — proxy, rollback, transaction boundaries.
- [Spring Framework: Application Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html) — `ApplicationEventPublisher` và event listener trong Spring context.
- [Spring Framework: TransactionSynchronization Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html) — afterCommit/afterCompletion, cảnh báo dữ liệu mới cần transaction riêng.
- [Debezium: Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html) — một cách chuyển outbox rows thành message qua CDC.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactions-events-004">Không truyền EntityManager hoặc phiên ORM sang luồng xử lý khác</a>

<details>
<summary>Xem chi tiết</summary>

# Không truyền EntityManager hoặc phiên ORM sang luồng xử lý khác

## 1. Ý chính: ranh giới thread cũng là ranh giới persistence context

Trong ứng dụng Spring có xử lý đồng thời, một luồng có thể đang thực thi `@Transactional`, giữ **persistence context** và dùng JPA `EntityManager` hoặc Hibernate `Session`. Nếu method này gửi công việc sang `@Async`/`TaskExecutor`, **không chuyển nguyên instance EntityManager/Session đang hoạt động sang worker mới** để tiếp tục truy vấn hoặc cập nhật dữ liệu.

Lý do: một `EntityManager` thực do provider tạo, hoặc một Hibernate `Session` thực, là thành phần **có trạng thái (stateful), không thread-safe**. Nó theo dõi entity được quản lý, trạng thái dirty checking, flush, lazy loading và transaction. Spring quản lý transaction kiểu imperative thông qua tài nguyên gắn với **thread hiện tại**; khi chuyển việc sang worker, transaction và persistence context của caller **không tự đi theo**.

**Thiết kế khuyến nghị:** chỉ chuyển **ID, giá trị bất biến hoặc DTO đã được materialize** qua ranh giới async. Tại worker, gọi **service/repository bean do Spring quản lý** với transaction riêng; để Spring lấy EntityManager tương ứng từ persistence context **của worker**, đọc/ghi dữ liệu rồi **trả một DTO**, không trả EntityManager, Session hay lazy collection.

~~~text
KHÔNG NÊN

request thread                          async worker thread
  @Transactional
  EntityManager thực / Session thực ────────► em.find(), em.flush()
  persistence context A                         dùng chéo A: không an toàn
  transaction A                                  transaction A không tự truyền


NÊN

request thread                          async worker thread
  lưu entity và commit
  lấy orderId (Long) ─────────────────────────► @Transactional(readOnly = true)
                                               EntityManager của worker
                                               em.find(Order.class, orderId)
                                               DTO snapshot
                  ◄──────────────────────────── CompletableFuture<DTO>
~~~

Ở đây "EntityManager thực" là object quản lý persistence context do provider tạo. Có một **ngoại lệ quan trọng về kiểu reference**: `@PersistenceContext` mặc định của Spring thường inject một **shared EntityManager proxy** vào repository singleton. Proxy **có thể được dùng từ nhiều thread** vì nó chuyển các lời gọi tới EntityManager thích hợp cho transaction/thread đang hoạt động. Điều đó **không** khiến **underlying EntityManager/Session** trở thành thread-safe; cũng **không** phải lý do để truyền reference EM/Session qua API công việc. Phần 3 sẽ giải thích tường minh để không hiểu nhầm lời khuyên của bài.

## 2. EntityManager, Session, EntityManagerFactory và persistence context

| Thành phần | Vai trò | Quy tắc với concurrency |
| --- | --- | --- |
| `EntityManagerFactory` (JPA) | Factory và metadata của persistence unit | Được thiết kế để chia sẻ, thường là bean singleton; tạo EntityManager theo từng unit of work |
| `SessionFactory` (Hibernate) | Factory, cấu hình/mapping Hibernate | Thường chia sẻ qua các thread; mở Session riêng cho unit of work |
| `EntityManager` thực | API thao tác với một persistence context cụ thể | **Không thread-safe**, không dùng đồng thời hoặc chuyển tiếp giữa worker threads |
| `Session` thực | API Hibernate có trạng thái, thường là implementation của EntityManager | **Không thread-safe**, không chia sẻ giữa thread/unit of work |
| `@PersistenceContext` kiểu TRANSACTION của Spring | Reference proxy chuyển lời gọi tới EntityManager của transaction hiện tại | Proxy được inject vào singleton DAO có thể dùng ở các thread khác nhau; **không chia sẻ underlying context** |
| Entity JPA đang managed | Dữ liệu theo dõi bởi một persistence context | Không tự động thread-safe; không để nhiều thread dùng cùng entity đang managed |
| DTO/record bất biến | Bản chụp dữ liệu không gắn ORM persistence context | Phù hợp để chuyển giữa threads nếu các thành phần thực sự immutable/thread-safe |

`EntityManagerFactory` và `EntityManager` không thể đánh đồng: factory dùng chung là bình thường, còn context chứa first-level cache và trạng thái thay đổi entity thuộc về một unit of work. Tương tự, `SessionFactory` chia sẻ được không có nghĩa `Session` do factory mở được chia sẻ.

**Persistence context** quản lý lifecycle của entity: `new` → managed → detached/removed, cùng identity map, tracking thay đổi và flush. Đây không phải cấu trúc được thiết kế để nhiều worker cùng sửa một lúc. Nếu hai task cần thao tác trên cùng bản ghi DB, chúng có thể **có EntityManager riêng** và dùng transaction/locking/versioning để đồng bộ ở database, thay vì cố "chia sẻ một Session cho tiết kiệm".

### 2.1. Vì sao "không thread-safe" là giới hạn nghiêm túc?

Một Session/EntityManager có thể giữ:

- Identity map / first-level cache: theo dõi instance entity tương ứng với database identifier.
- Tập thay đổi cần flush và action queue.
- Trạng thái transaction, kết nối hoặc phối hợp với transaction manager.
- Lazy-loading proxies, collection wrappers và internal persistence state.
- Query context, trạng thái flush mode và cache hints.
- Tài nguyên cần đóng khi transaction/unit of work kết thúc.

Nếu hai thread cùng mutate hoặc flush trên một context, hành vi không được bảo đảm: race, dirty tracking sai, dữ liệu không nhất quán, exception ngẫu nhiên hoặc connection lifecycle sai. Ngay cả khi hai tác vụ chỉ gọi `find()`, việc thực thi cũng có thể thay đổi cache và trạng thái bên trong. **Không coi read-only access là bằng chứng có thể dùng một Session thực đồng thời.**

Một Hibernate `Session` thường cần phạm vi tương đối ngắn theo unit of work. Đừng đặt một Session thực vào singleton field, HTTP session, static field hoặc một executor task rồi giữ nó sống qua nhiều transactions.

## 3. Điểm tinh tế: @PersistenceContext trong Spring là proxy dùng chung, không phải Session thực

Trong một Spring singleton repository:

~~~java
@Repository
public class OrderRepository {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public OrderSummary findSummary(Long id) {
        Order order = em.find(Order.class, id);
        return new OrderSummary(order.getId(), order.getStatus());
    }
}
~~~

Đây là **mảnh code minh họa**, cần các kiểu `Order`, `OrderSummary` và import. `OrderSummary` nên là immutable DTO. Với `PersistenceContextType.TRANSACTION` mặc định, Spring thường inject một **shared transactional EntityManager proxy** vào `em`, không phải một provider Session cố định dành cho mọi lời gọi.

Proxy này xác định **EntityManager gắn với transaction đang hoạt động**; nếu không có transaction, trong một số trường hợp nó tạo EntityManager cho operation thích hợp. Khi hai thread khác nhau gọi vào cùng repository bean, proxy chọn context theo transaction/thread của từng lời gọi, thay vì chia sẻ một persistence context thật. Vì vậy mẫu repository singleton có `@PersistenceContext` là **mẫu được Spring hỗ trợ**, không trái với tiêu đề.

**Điều không được làm:**

- Lấy `em.unwrap(org.hibernate.Session.class)` từ một transaction rồi giữ/đưa **Session thực** sang executor.
- Tạo `emf.createEntityManager()` thủ công rồi chia sẻ instance đó giữa request thread và worker.
- Chuyển persistence context dưới dạng tham số `worker.doWork(em, ...)` với giả định transaction sẽ đi theo.
- Dùng `@PersistenceContext(type = PersistenceContextType.EXTENDED)` trong một singleton bean được truy cập đồng thời; extended context không phải shared transactional proxy an toàn kiểu trên.

**Điều được phép:** inject repository có `@PersistenceContext` proxy vào các Spring-managed services, để mỗi method được gọi với **transaction riêng phù hợp**. Mẫu này dùng **cùng reference proxy** nhưng **không dùng chung một underlying EntityManager thực**.

### 3.1. Shared proxy không phải "tấm vé" để bỏ qua transaction

Dù shared proxy tự chọn EntityManager, nó **không sao chép transaction của caller** sang worker. Tại worker không có active transaction, một số thao tác đọc có thể được thực hiện bằng một EntityManager tạo theo operation; thao tác ghi hoặc lazy loading ngoài phạm vi quản lý thường không có hợp đồng đúng. Thiết kế an toàn hơn là gọi một service/repository `@Transactional` ở thread thực thi công việc.

Sự khác biệt cần nhớ:

~~~text
Field trong singleton repository:
  @PersistenceContext EntityManager em
             |
             +-- reference proxy (có thể dùng từ nhiều thread)
                      |
                      +-- thread A / transaction A -> EntityManager A
                      |
                      +-- thread B / transaction B -> EntityManager B

Không phải:
  singleton repo -> EntityManager thực duy nhất -> mọi thread
~~~

Tài liệu Spring nhấn mạnh `@PersistenceContext` mặc định kiểu `TRANSACTION` và lưu ý `EXTENDED` là cơ chế khác, không thích hợp cho singleton truy cập đồng thời. Điều phân biệt đó quan trọng hơn việc chỉ đọc type `EntityManager` của field.

## 4. @Transactional không được truyền sang luồng mới

Một `@Transactional` imperative do `PlatformTransactionManager` quản lý thường gắn transaction/resources với thread bằng các cơ chế như `TransactionSynchronizationManager`. Khi gọi `@Async`, `Executor.execute(...)`, `CompletableFuture.supplyAsync(...)` hoặc tạo `Thread` mới, code chạy ở **thread khác**, nên **không tự kế thừa**:

- Transaction JPA và persistence context của caller.
- JDBC connection được bind bởi transaction manager.
- Hibernate `Session` của transaction caller.
- Các `ThreadLocal` khác như MDC/security/request context, trừ khi chủ động cấu hình propagation phù hợp.

Ví dụ anti-pattern:

~~~java
@Service
public class OrderCoordinator {
    private final EntityManagerFactory emf;
    private final Executor executor;

    // Constructor injection không hiển thị trong đoạn minh họa.

    @Transactional
    public void unsafe(Long id) {
        EntityManager em = emf.createEntityManager();
        executor.execute(() -> em.find(Order.class, id));
        // Không có protocol ownership, close, transaction hay
        // đồng bộ truy cập phù hợp cho EntityManager này.
    }
}
~~~

Đây là **đoạn sai có chủ đích**, không phải code runnable; còn thiếu constructor, kiểu `Order` và import. `createEntityManager()` trả về application-managed EntityManager thực. Tác vụ được gửi sang thread khác nhưng resource vẫn có lifecycle riêng không rõ ràng; nếu caller dùng/đóng `em` trong lúc worker chạy, còn có concurrent access/lifecycle race. **Đừng xem instance này là một payload hợp lệ cho executor.**

Một ví dụ sai còn rõ hơn:

~~~java
@Transactional
public void wrong(Long id) {
    Session session = entityManager.unwrap(Session.class);
    executor.execute(() -> session.find(Order.class, id));
}
~~~

Mảnh này cần `entityManager`, `executor`, `Order` và Hibernate imports. `unwrap(Session.class)` có thể làm lộ **Session thực gắn với transaction caller**. Khi lambda chạy, transaction caller có thể đã commit/close, hoặc hai thread cùng chạm tới Session không thread-safe. Không giải quyết lỗi bằng cách gọi `session.flush()` trên worker hay tăng kích thước pool.

### 4.1. Lời gọi @Async có thể bắt đầu trước commit

~~~text
request thread                                worker thread
  @Transactional save(order)
  submit async(order.id) --------------------> findById(id)
  chưa commit                                  có thể không thấy row
  commit
~~~

Khi worker có transaction riêng, nó không mặc nhiên nhìn thấy dữ liệu **chưa commit** từ transaction caller. Kết quả phụ thuộc isolation, thời điểm commit, database, lock và operation. Có thể gặp "record not found" hoặc block vì lock ngay cả khi repository và EntityManager đã được quản lý đúng cách. Đây là vấn đề **thời điểm dữ liệu được công bố**, khác với vấn đề thread-safety của EntityManager.

Với việc **bắt buộc chạy sau commit**, xem phần 10 về `@TransactionalEventListener(phase = AFTER_COMMIT)` và transactional outbox.

## 5. Không chỉ EntityManager: managed entity và lazy proxy cũng nguy hiểm khi chuyển thread

Một cách "lách" thường gặp là không truyền `EntityManager`, nhưng chuyển nguyên **entity đang managed** hoặc một **Hibernate lazy collection/proxy** sang worker:

~~~java
@Transactional
public void start(Long orderId) {
    PurchaseOrder order = em.find(PurchaseOrder.class, orderId);
    asyncWorker.process(order); // Không khuyến nghị dùng managed entity làm payload
}
~~~

Đây là mảnh minh họa với bean/typings chưa khai báo. Entity có thể chứa mutable fields, `@OneToMany(fetch = LAZY)` và reference tới trạng thái provider. Khi transaction caller kết thúc, worker có thể nhận entity **detached**, gặp `LazyInitializationException` nếu truy cập lazy association chưa được load, hoặc đọc dữ liệu không nhất quán do chia sẻ mutable object.

**Detached không đồng nghĩa immutable hoặc thread-safe.** Một detached POJO có thể vẫn được hai thread thay đổi đồng thời. Hibernate lazy proxy cũng có thể cần Session đã đóng, nên việc "đợi đến khi worker dùng" không làm nó tự attach lại.

Một số dữ liệu không nên mang qua boundary:

| Payload | Rủi ro |
| --- | --- |
| Provider `EntityManager` hoặc Hibernate `Session` | Non-thread-safe, lifecycle/transaction không theo worker |
| Managed entity có state biến đổi | Gắn với persistence context, mutable và có thể bị dirty checking ở caller |
| Lazy proxy / `PersistentCollection` | Cần session để initialize; session có thể đã đóng hoặc nằm ở thread khác |
| `Query`, `TypedQuery`, `StoredProcedureQuery` | Thường gắn với EntityManager/persistence context, không phải payload |
| JPA `Stream`/cursor đang mở | Tài nguyên DB/connection có lifecycle theo context gốc |
| `EntityTransaction`, JDBC `Connection`, Hibernate transaction | Stateful resource, không làm transaction có thể "di chuyển" giữa threads |
| Primitive/ID/immutable record chứa dữ liệu đã materialize | Phù hợp hơn khi không giữ reference ORM và có semantics rõ ràng |

Nếu cần các field từ association, truy vấn chúng **trong transaction của worker** hoặc **materialize thành DTO** trong một transaction có quyền truy cập phù hợp trước khi truyền; không gửi một collection proxy chưa được initialize để mong worker tự load.

### 5.1. Dùng DTO bất biến làm ranh giới dữ liệu

Java 17 record:

~~~java
public record OrderEvent(Long orderId, String requestedBy, long version) {}
~~~

Đây là một kiểu value object, không phải ORM entity. Nó có các thành phần final và không mang persistence context. **Lưu ý:** record chỉ immutable nông; nếu component là `List`, array hoặc object mutable thì dữ liệu bên trong vẫn có thể thay đổi. Với `Long`, `String` và `long` ở ví dụ này, payload là một snapshot dễ truyền.

Đừng dựa vào `EntityManager.detach(entity)` như giải pháp toàn diện: detach chỉ thay đổi trạng thái quản lý trong persistence context, không biến object mutable thành immutable hay giải quyết lazy association còn chưa load.

## 6. Mẫu an toàn: truyền ID rồi load bằng transaction ở worker

**Quy tắc ba bước:**

1. Caller lưu hoặc quyết định ID **theo contract về commit**.
2. Caller gửi **ID/immutable command** qua `@Async` hoặc executor, không gửi EM/Session/entity đang managed.
3. Worker **mở transaction riêng** qua Spring service/repository, load bằng `@PersistenceContext` proxy của bean worker và trả snapshot DTO.

Mẫu phân lớp:

~~~java
@Service
public class OrderWriter {
    @Transactional
    public Long create(OrderRequest request) {
        // Persist entity rồi trả ID; transaction commit khi
        // lời gọi qua Spring proxy hoàn tất bình thường.
        return orderRepository.saveAndGetId(request);
    }
}

@Service
public class AsyncLookup {
    private final OrderReader orderReader;

    public AsyncLookup(OrderReader orderReader) {
        this.orderReader = orderReader;
    }

    @Async("dbExecutor")
    public CompletableFuture<OrderDto> loadAsync(Long id) {
        OrderDto dto = orderReader.readDto(id);
        return CompletableFuture.completedFuture(dto);
    }
}

@Service
public class OrderReader {
    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public OrderDto readDto(Long id) {
        Order entity = em.find(Order.class, id);
        return OrderDto.of(entity); // copy dữ liệu, không trả entity
    }
}
~~~

Đây là **mã minh họa**; `OrderRequest`, `OrderDto`, `Order`, `orderRepository` và cấu hình bean chưa được định nghĩa. Điều quan trọng là worker `AsyncLookup` gọi **bean OrderReader khác qua Spring proxy**, nhờ đó `@Transactional(readOnly = true)` mở transaction **trên worker thread**. `@Async` cũng được gọi qua bean proxy từ caller, không phải `this.loadAsync(...)`.

Một phương án khác là đặt `@Async` và `@Transactional` trên method worker được gọi qua proxy phù hợp, nhưng việc tách thành **dispatcher async + reader transactional** làm hai ranh giới rõ ràng và ít phụ thuộc vào thứ tự advice.

### 6.1. readOnly không có nghĩa entity/thread tự an toàn

`@Transactional(readOnly = true)` là hint/thuộc tính transaction mà Spring/provider có thể tối ưu theo cấu hình; nó **không** làm underlying EntityManager thread-safe. Vẫn phải giữ context của worker, không trả entity đang managed sang thread khác.

### 6.2. Nếu phải cập nhật dữ liệu tại worker

Worker có thể gọi:

~~~java
@Transactional
public void markProcessed(Long id) {
    Order entity = em.find(Order.class, id);
    if (entity == null) {
        throw new IllegalArgumentException("Order not found: " + id);
    }
    entity.setProcessed(true);
}
~~~

Đây là method minh họa trong repository/service có EM được quản lý. Transaction riêng của worker thực hiện dirty checking/flush; caller transaction không tự bao bọc lần cập nhật này. Nếu nhiều workers cùng cập nhật một row, dùng `@Version` optimistic locking, database constraints hoặc locking phù hợp để tránh lost update. Không chia sẻ object entity giữa worker threads để "đồng bộ" database.

## 7. Ví dụ hoàn chỉnh: Java 17 + Spring Framework 6.1 + Hibernate + H2

Ví dụ sau là một **ứng dụng command-line Spring Framework thuần**, không cần Spring Boot, server web hay database bên ngoài. Nó cho thấy:

- `OrderWriter.create(...)` thực thi `@Transactional` trên caller và **hoàn tất transaction trước khi trả về**.
- Caller chỉ chuyển một `Long id` sang worker.
- `AsyncOrderLookup.loadAsync(id)` chạy thông qua Spring `@Async("dbExecutor")`.
- Worker gọi `OrderReader.read(id)`, một **bean khác** có `@Transactional(readOnly = true)`; transaction này hình thành trên worker.
- `@PersistenceContext` inject **shared proxy** vào singleton `OrderReader`, nhưng không chia sẻ provider EntityManager thực giữa hai transactions.
- Kết quả là `OrderView` bất biến (record), không phải managed entity hay Session.

**Môi trường:** JDK 17+, Gradle 8.5+, Spring Framework 6.1.14, Hibernate ORM 6.4.10.Final, Jakarta Persistence 3.1, H2 2.2.224.

### 7.1. Cấu trúc tệp

~~~text
orm-thread-boundary/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/
    ├── JpaThreadDemo.java
    ├── PurchaseOrder.java
    ├── OrderView.java
    ├── OrderWriter.java
    ├── OrderReader.java
    └── AsyncOrderLookup.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'orm-thread-boundary'
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
    implementation 'org.springframework:spring-orm:6.1.14'
    implementation 'jakarta.persistence:jakarta.persistence-api:3.1.0'
    implementation 'org.hibernate.orm:hibernate-core:6.4.10.Final'
    runtimeOnly 'com.h2database:h2:2.2.224'
    runtimeOnly 'org.slf4j:slf4j-simple:2.0.16'
}

application {
    mainClass = 'demo.JpaThreadDemo'
}
~~~

Đây là demo khóa version cụ thể cho mục đích học; dự án thực cần kiểm tra **BOM/version compatibility** và bản vá bảo mật theo quy trình dependency management của mình.

### 7.2. src/main/java/demo/PurchaseOrder.java

~~~java
package demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reference;

    protected PurchaseOrder() {
        // Constructor cho JPA.
    }

    public PurchaseOrder(String reference) {
        this.reference = reference;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }
}
~~~

Entity được Hibernate quản lý **trong transaction của từng thread**. Nó không được gửi trực tiếp vào `CompletableFuture`. `@GeneratedValue(IDENTITY)` giúp demo có khóa chính từ H2. Các mapping phức tạp có quan hệ lazy được bỏ qua ở đây để tập trung vào ranh giới concurrency.

### 7.3. src/main/java/demo/OrderView.java

~~~java
package demo;

public record OrderView(
        Long id,
        String reference,
        String workerThread,
        boolean transactionActive) {
}
~~~

DTO này là một Java record gồm các giá trị có thể chia sẻ phù hợp. `workerThread` chỉ để minh họa thread chạy truy vấn; production code thường trả data/metadata cần thiết chứ không dùng thread name làm khóa nghiệp vụ.

### 7.4. src/main/java/demo/OrderWriter.java

~~~java
package demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

public class OrderWriter {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public Long create(String reference) {
        PurchaseOrder order = new PurchaseOrder(reference);
        em.persist(order);
        em.flush();
        return order.getId();
    }
}
~~~

Trong demo, `OrderWriter` là Spring bean được tạo từ cấu hình bên dưới. Method `create` được gọi **qua proxy**, Spring transaction interceptor hoàn tất commit trước khi caller tiếp tục sau lời gọi trả về bình thường. `em` là shared injected proxy, không phải provider Session có thể đem sang thread khác.

`em.flush()` trong demo bảo đảm JPA thực hiện flush trước khi trả ID, nhưng **flush không đồng nghĩa commit**. Transaction được hoàn tất bởi Spring khi lời gọi method qua proxy kết thúc thành công.

### 7.5. src/main/java/demo/OrderReader.java

~~~java
package demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class OrderReader {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public OrderView read(Long id) {
        PurchaseOrder order = em.find(PurchaseOrder.class, id);
        if (order == null) {
            throw new IllegalArgumentException("Missing order: " + id);
        }

        // Tạo snapshot khi vẫn ở trong transaction của worker.
        return new OrderView(
                order.getId(),
                order.getReference(),
                Thread.currentThread().getName(),
                TransactionSynchronizationManager
                        .isActualTransactionActive());
    }
}
~~~

`OrderReader.read` được gọi từ một bean khác, nên transaction interceptor tạo một ranh giới read-only **ngay ở worker thread**. Nó truy vấn entity và copy các field vào DTO; entity không rời persistence context qua ranh giới thread.

### 7.6. src/main/java/demo/AsyncOrderLookup.java

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

public class AsyncOrderLookup {
    private final OrderReader reader;

    public AsyncOrderLookup(OrderReader reader) {
        this.reader = reader;
    }

    @Async("dbExecutor")
    public CompletableFuture<OrderView> loadAsync(Long id) {
        // Chỉ có ID đi từ caller sang worker.
        OrderView view = reader.read(id);

        // Phần thân @Async đang chạy trên worker;
        // Spring proxy cung cấp future kết quả cho caller.
        return CompletableFuture.completedFuture(view);
    }
}
~~~

`AsyncOrderLookup` chỉ nhận `Long id` và reference Spring-managed `OrderReader`. Không có `EntityManager`, `Session`, `Query` hay JPA entity trong tham số async. Phương thức `loadAsync` trả một `CompletableFuture` đã hoàn tất từ **bên trong worker**; proxy của Spring trả handle để caller theo dõi công việc nền.

### 7.7. src/main/java/demo/JpaThreadDemo.java

~~~java
package demo;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

public final class JpaThreadDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            // Lời gọi Spring proxy: transaction caller commit trước khi về.
            Long id = context.getBean(OrderWriter.class)
                    .create("ORDER-42");

            // Chỉ chuyển ID; worker tạo transaction riêng qua OrderReader.
            OrderView view = context.getBean(AsyncOrderLookup.class)
                    .loadAsync(id)
                    .get(5, TimeUnit.SECONDS);

            System.out.println("id preserved: " +
                    id.equals(view.id()));
            System.out.println("field loaded: " +
                    "ORDER-42".equals(view.reference()));
            System.out.println("worker pool used: " +
                    view.workerThread().startsWith("db-worker-"));
            System.out.println("worker transaction active: " +
                    view.transactionActive());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAsync
    public static class Config {

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:orm_thread_demo;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean
                entityManagerFactory(DataSource dataSource) {

            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("demo");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

            Properties props = new Properties();
            props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            props.setProperty("hibernate.show_sql", "false");
            factory.setJpaProperties(props);
            return factory;
        }

        @Bean
        public PlatformTransactionManager
                transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean("dbExecutor")
        public ThreadPoolTaskExecutor dbExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("db-worker-");
            return executor;
        }

        @Bean
        public OrderWriter orderWriter() {
            return new OrderWriter();
        }

        @Bean
        public OrderReader orderReader() {
            return new OrderReader();
        }

        @Bean
        public AsyncOrderLookup asyncOrderLookup(OrderReader reader) {
            return new AsyncOrderLookup(reader);
        }
    }
}
~~~

**Giải thích cấu hình:** `LocalContainerEntityManagerFactoryBean` xây `EntityManagerFactory` cho Hibernate, `JpaTransactionManager` cung cấp giao dịch JPA theo thread, và `@EnableTransactionManagement` kích hoạt transaction proxies. `@EnableAsync` kích hoạt async proxy. H2 là database trong bộ nhớ; `create-drop` **chỉ dành cho demo**, không áp dụng cho production vì có thể phá schema/dữ liệu.

`OrderWriter`, `OrderReader`, `AsyncOrderLookup` được đăng ký qua `@Bean`. Đối tượng do phương thức `@Bean` trả về được Spring post-process như bean bình thường, không phải object tự `new` ngoài container. Khi `OrderReader` được inject vào `AsyncOrderLookup`, reference có transaction proxy để intercept `read`.

### 7.8. Chạy và kết quả

Trong thư mục `orm-thread-boundary`, khi có Java 17+ và Gradle tương thích:

~~~bash
gradle run
~~~

Nếu dự án đã có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

**Bốn dòng do chương trình tự in, với điều kiện khởi tạo H2/Hibernate thành công và tác vụ hoàn tất trước timeout:**

~~~text
id preserved: true
field loaded: true
worker pool used: true
worker transaction active: true
~~~

Các thư viện Hibernate/SLF4J/Gradle có thể in thêm INFO/WARN trong quá trình khởi động/đóng context; đó không thuộc bốn dòng ở trên. Demo kiểm tra boolean thay vì in ID cụ thể hoặc số thứ tự worker để không phụ thuộc thứ tự thread và khóa chính sinh từ database.

**Tại sao thiết kế đúng?**

1. Caller lấy `OrderWriter` qua context, gọi `create` qua transaction proxy; khi method trả về bình thường, transaction được Spring commit.
2. Chỉ `Long id` được gửi sang `AsyncOrderLookup`, không phải EntityManager, Hibernate Session hay entity đang managed.
3. `@Async` được áp dụng qua Spring proxy, dispatcher chạy trên `db-worker-`.
4. Dispatcher gọi `OrderReader.read(id)` qua bean reference được inject; `@Transactional(readOnly = true)` được kích hoạt trên worker.
5. Shared EM proxy bên trong `OrderReader` delegate tới underlying EntityManager cho worker transaction.
6. `OrderView` là snapshot được tạo **trong** transaction; Future của caller mang dữ liệu DTO thuần.
7. Context được đóng sau khi future đã hoàn tất, để Spring quản lý lifecycle của entity manager factory và executor.

**Bài học quan trọng:** ví dụ không cố tạo một EntityManager thủ công ở caller rồi chuyển nó tới thread pool. Nó chuyển **dữ liệu** sang thread khác và để hạ tầng Spring quản lý persistence context mới tại chỗ công việc chạy.

## 8. Khi phải tự dùng EntityManagerFactory trong worker

Không phải mọi chương trình đều dùng declarative `@Transactional`. Một worker chạy ngoài Spring có thể tự lấy **factory dùng chung** rồi tạo **EntityManager riêng tại chính worker**, cùng transaction/lifecycle riêng.

Mẫu JPA `RESOURCE_LOCAL` minh họa:

~~~java
CompletableFuture<Summary> future = CompletableFuture.supplyAsync(
        () -> {
            EntityManager workerEm = emf.createEntityManager();
            EntityTransaction tx = workerEm.getTransaction();
            try {
                tx.begin();

                Order entity = workerEm.find(Order.class, orderId);
                Summary result = new Summary(
                        entity.getId(), entity.getStatus());

                tx.commit();
                return result;
            } catch (RuntimeException ex) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw ex;
            } finally {
                workerEm.close();
            }
        },
        executor);
~~~

Đây là **đoạn minh họa**, cần `emf`, `executor`, `orderId`, `Summary` và `Order`. Nó chỉ hợp lệ khi dùng **application-managed resource-local transactions**; với JTA/container-managed transactions cần giao thức và quản lý tương ứng, không tự gọi `getTransaction()` tùy tiện.

Điểm đúng: **factory chia sẻ được**; worker gọi `createEntityManager()`, bắt đầu transaction, copy DTO, commit/rollback và close trong **cùng một unit of work**. Không đưa `workerEm` ra ngoài lambda, không giữ nó vào singleton field và không trả nó trong Future.

Trong một ứng dụng Spring đã có `JpaTransactionManager`/`@PersistenceContext`, ưu tiên cách **Spring-managed repository + @Transactional** ở mục 6–7; tự quản lý EntityManager/transaction dễ mắc lỗi cleanup và làm lệch transaction manager của ứng dụng.

## 9. Trả DTO, không trả lazy object hoặc query stream

Một method async có thể trông an toàn vì không truyền `EntityManager`, nhưng **trả entity hoặc lazy collection qua Future** vẫn gây rủi ro:

~~~java
@Async("dbExecutor")
public CompletableFuture<PurchaseOrder> findAsync(Long id) {
    PurchaseOrder entity = em.find(PurchaseOrder.class, id);
    return CompletableFuture.completedFuture(entity);
}
~~~

Đây là mảnh code **không khuyến nghị**, cần bean/transaction/import. Nếu entity được load trong worker, Future đưa entity ra caller **sau khi worker transaction có thể đã kết thúc**, khiến lazy fields không load được hoặc object bị sửa ở nơi không kiểm soát. Bản thân entity không trở thành immutable khi detached.

**Cách tốt hơn:**

~~~java
@Transactional(readOnly = true)
public OrderDetailsDto getDetails(Long id) {
    PurchaseOrder order = em.find(PurchaseOrder.class, id);

    // Lấy các giá trị cần thiết khi persistence context còn hiệu lực.
    return new OrderDetailsDto(
            order.getId(),
            order.getReference(),
            resolveItemNames(order));
}
~~~

Đây là mảnh code cần DTO và `resolveItemNames` riêng. Nếu `resolveItemNames` đọc một association lazy, hãy chủ động fetch/mapping trong transaction. Record chứa `List<String>` nên copy bằng `List.copyOf(...)` nếu cần immutable snapshot, thay vì trả nguyên `PersistentCollection`.

Có thể dùng **JPQL constructor projection**, Spring Data projection, entity graph hoặc `JOIN FETCH` hợp lý để lấy đúng field cần dùng. Nhưng câu hỏi gốc không phải "làm sao fetch mọi quan hệ"; nó là **không chuyển persistence context hoặc Session qua thread**.

## 10. Khi cần làm việc sau commit: TransactionalEventListener và outbox

Với các nhiệm vụ được phát sinh trong transaction caller, chỉ dùng `@Async` không đảm bảo worker chạy sau commit.

### 10.1. Phát sự kiện chứa ID và xử lý sau commit

Một mẫu phù hợp cho việc **có thể xử lý trong bộ nhớ**:

~~~java
public record OrderCommittedEvent(Long orderId) {}

// Trong service đang được Spring quản lý:
@Transactional
public void createAndNotify(OrderRequest request) {
    Long id = saveOrder(request);
    applicationEventPublisher.publishEvent(new OrderCommittedEvent(id));
}

// Trong listener bean khác:
@Component
public class OrderCommittedListener {
    private final AsyncOrderLookup worker;

    public OrderCommittedListener(AsyncOrderLookup worker) {
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(OrderCommittedEvent event) {
        worker.loadAsync(event.orderId());
    }
}
~~~

Đây là các **mảnh code minh họa**, chưa phải chương trình độc lập: cần khai báo request, persistence method, imports và xử lý kết quả Future. `@TransactionalEventListener(AFTER_COMMIT)` gắn việc nhận sự kiện với **commit thành công** của transaction gốc; listener sau commit gửi ID tới async worker. Worker khởi tạo transaction riêng khi đọc.

Cần hiểu các giới hạn:

- Một transactional event listener thông thường cần event được publish khi có transaction đang hoạt động; nếu không có transaction, mặc định listener có thể **không được gọi** trừ khi `fallbackExecution` được cấu hình.
- Với listener đồng bộ chạy ngay sau commit, **tài nguyên transaction cũ có thể còn tạm thời bound** khi callback đang diễn ra. Nếu listener định ghi dữ liệu trong DB, cần mở một ranh giới transaction độc lập phù hợp (thường `REQUIRES_NEW` trong cùng thread sau commit) hoặc dispatch sang worker có transaction riêng.
- Không mặc định chuyển toàn bộ việc đăng ký transactional event sang một **async event multicaster** tùy ý: việc đăng ký listener với transaction cần xảy ra trong context transaction gốc theo hợp đồng của Spring.
- **Sự kiện in-memory có thể mất** nếu process crash ngay sau commit trước khi worker chạy. Nó không tự bảo đảm at-least-once delivery.

### 10.2. Transactional outbox cho công việc phải bền vững

Nếu việc gửi email, cập nhật hệ thống ngoài hoặc xử lý giao dịch tài chính **không được phép mất vì crash/restart**, nên xem xét **transactional outbox**:

~~~text
Caller transaction:
  INSERT purchase_order
  INSERT outbox_event (orderId, eventType, status=PENDING)
  COMMIT cả hai

Background worker:
  CLAIM outbox_event
  READ order by ID trong transaction riêng
  THỰC HIỆN side effect với idempotency key
  UPDATE outbox status / retry metadata
~~~

Outbox giữ metadata công việc **trong cùng transaction** với thay đổi nghiệp vụ. Nó giải quyết rủi ro mất notification giữa commit và in-memory dispatch, nhưng **không tự mang lại exactly-once side effects**; worker cần retry budget, leasing/claiming, idempotency/deduplication và theo dõi trạng thái.

Không đặt EntityManager hoặc managed entity vào outbox payload; ghi **ID và dữ liệu cần thiết có thể serialize**. Nếu cần snapshot đúng ở thời điểm commit, ghi các giá trị đó vào event payload/bảng outbox theo hợp đồng, thay vì kỳ vọng một async worker đọc giá trị ở thời điểm cũ.

## 11. Dữ liệu thay đổi giữa lúc submit và lúc worker đọc

Ngay cả khi dùng ID/DTO và transaction riêng đúng cách, worker **không mặc nhiên đọc cùng snapshot** mà caller vừa thấy.

Ví dụ:

~~~text
T1: Order status = NEW; caller submit async(id=7)
T2: Một transaction khác đổi status thành APPROVED và commit
T3: Worker bắt đầu, mở transaction, find(id=7) -> APPROVED
~~~

Đây không phải race của EntityManager, mà là **race nghiệp vụ giữa các transactions độc lập**. Nếu cần biết chính xác trạng thái tại lúc tạo event, phải mang **immutable snapshot/version** qua event hoặc lưu trong outbox.

Một số chiến lược:

- **Optimistic locking với `@Version`** cho cập nhật đồng thời; xử lý `OptimisticLockException`/Spring exception tương ứng và retry có giới hạn nếu operation idempotent.
- **Conditional update** tại DB khi trạng thái/version vẫn đúng.
- **Idempotency key** để ngăn side effects lặp khi retry.
- **Định nghĩa thứ tự xử lý** (theo aggregate ID) nếu thông điệp có phụ thuộc.
- **READ COMMITTED/SNAPSHOT/REPEATABLE READ** theo nhu cầu database; đừng giả định transaction riêng luôn thấy một snapshot giống caller.
- **Record các thông tin đủ dùng** trong payload khi nghiệp vụ cần tính nhất quán theo thời điểm event.

Tránh cố giải vấn đề version bằng cách để hai thread dùng chung persistence context. Nó chỉ làm phát sinh rủi ro concurrency mới và vẫn không thay thế database concurrency control.

## 12. Vì sao Open EntityManager in View không sửa được ranh giới async?

Trong một số Spring MVC apps, Open EntityManager in View (OEIV) gắn EntityManager vào **thời gian xử lý request** để hỗ trợ lazy access ở tầng view. Nhưng OEIV **không cấp quyền** dùng underlying EM/Session từ worker khác và không biến context thành thread-safe.

Dùng OEIV để "giữ Session mở lâu hơn" còn có những rủi ro:

- Tăng thời gian giữ tài nguyên gắn với request.
- Dễ phát sinh lazy query ở view/serialization, khó kiểm soát N+1.
- Không đảm bảo async worker có cùng transaction, isolation hoặc persistence context.
- Có thể tạo hiểu nhầm vì một endpoint trả entity hoạt động trong trường hợp này nhưng vỡ khi chạy ngoài request.

**Không bật OEIV như bản vá cho `LazyInitializationException` do chuyển entity/lazy proxy sang worker.** Giải pháp là truy vấn/materialize trong transaction thuộc về worker hoặc trả DTO đã chuẩn bị đúng lúc.

## 13. Spring @Async, @Transactional và proxy boundaries

Để method có annotation được xử lý, cần:

1. `@EnableAsync` và `@EnableTransactionManagement` có hiệu lực trong **đúng ApplicationContext**.
2. Bean được Spring quản lý (qua `@Component`/`@Service`/`@Repository` hoặc `@Bean`).
3. Với proxy mode mặc định, lời gọi đi **qua proxy**, không dùng `this.method()` cho method cần advice.
4. Executor được chọn đúng và còn nhận task.
5. Transaction manager đúng cho JPA datasource/persistence unit.

Một anti-pattern:

~~~java
@Service
public class BadService {
    @Transactional
    public void process(Long id) {
        this.doAsync(id); // Không đi qua async proxy.
    }

    @Async
    public void doAsync(Long id) {
        // Có thể chạy đồng bộ ngay trong transaction caller,
        // làm người đọc hiểu nhầm về an toàn thread.
    }
}
~~~

Mảnh code trên thiếu imports; nó cố ý minh họa lỗi self-invocation. Trường hợp này không phải EM được chia sẻ qua hai thread; **vấn đề là @Async không được áp dụng**. Điều nguy hiểm là sau khi refactor để lời gọi thật sự chạy async, một thiết kế vốn phụ thuộc transaction caller có thể lập tức lỗi vì worker không kế thừa nó. Phải sửa **cả data handoff lẫn transaction boundary**.

Trong demo ở phần 7, hai proxy boundaries được tách:

~~~text
Caller -> OrderWriter.create()      [@Transactional trong caller]
        | trả Long id sau commit
        v
Caller -> AsyncOrderLookup.loadAsync(id)  [@Async -> executor]
        |
        v
Worker -> OrderReader.read(id)       [@Transactional trong worker]
        |
        v
Worker EntityManager -> OrderView
~~~

Đây là kiến trúc dễ giải thích, test và quan sát. Một method có thể đồng thời `@Async` và `@Transactional` khi interceptor setup đúng, nhưng không nên coi hai annotation là công cụ truyền transaction cũ sang thread mới.

### 13.1. Không tự propagate TransactionSynchronizationManager

Đừng tự sao chép dữ liệu từ `TransactionSynchronizationManager.getResourceMap()` hoặc các thread-local transaction internals rồi bind vào worker để "chia sẻ" cùng transaction/context. Những tài nguyên như Connection/EntityManager không được thiết kế để phân phối qua worker tùy ý; có thể phá lifecycle, locking và cleanup của transaction manager.

Nếu có thư viện context propagation hỗ trợ MDC/tracing, **điều đó không đồng nghĩa** nên propagate **transaction**. Trace/correlation ID có thể là dữ liệu có ngữ nghĩa request; EntityManager/JDBC connection là stateful resources gắn unit of work.

## 14. Quá tải, exception và cancellation vẫn cần thiết kế

Thiết kế đúng về thread confinement không đảm bảo async job luôn thành công.

### 14.1. Bounded executor và connection pool

Một `ThreadPoolTaskExecutor` có:

- `corePoolSize` / `maxPoolSize`: giới hạn số worker.
- `queueCapacity`: số task chờ, ảnh hưởng latency.
- `RejectedExecutionHandler`: phản ứng khi bão hòa hoặc shutdown.
- `threadNamePrefix`: giúp đọc log/thread dump.

Nếu DB connection pool chỉ có vài connections nhưng executor cho phép quá nhiều workers truy vấn, chúng sẽ tranh connection, queue wait tăng và có thể timeout. Tuning executor phải đi cùng **DataSource pool, DB capacity, query latency và service-level objective**, không chỉ tăng max threads.

Ví dụ `DiscardPolicy` hoặc `DiscardOldestPolicy` có thể âm thầm bỏ task; Future không hoàn thành khiến người dùng chờ mãi. Với công việc bắt buộc, nên dùng **rejection có tín hiệu**, backpressure/rate limit hoặc lưu job bền vững. Không ghi trạng thái "đã xử lý" chỉ vì gọi `@Async` trả một Future.

### 14.2. Exception không làm persistence context có thể tái sử dụng

Khi Hibernate Session gặp lỗi nghiêm trọng, transaction cần rollback và Session/context thường phải bỏ đi, không được xem việc catch exception là đủ để tiếp tục mọi thao tác trên cùng Session. Để Spring transaction interceptor kết thúc/rollback theo hợp đồng hoặc quản lý rollback/close trong code tự quản lý.

Lỗi `LazyInitializationException`, `TransactionRequiredException`, `OptimisticLockException`, `DataAccessException` và `ExecutionException` có ngữ nghĩa khác nhau. Với Future, `get()` có thể ném `ExecutionException` và `join()` có thể ném `CompletionException`; đọc cause để thấy lỗi gốc thay vì đổ lỗi ngay cho executor.

### 14.3. Cancellation và database side effects

`Future.cancel(true)` chỉ **cố yêu cầu interrupt worker** nếu implementation hỗ trợ. Nó không đảm bảo JDBC query dừng lập tức, DB transaction rollback, email bị thu hồi hoặc external payment bị đảo ngược. `CompletableFuture.cancel(true)` theo JDK không dùng tham số `mayInterruptIfRunning` để điều khiển computation nền.

Nên dùng query timeout, transaction timeout và policy cancellation rõ ràng; kiểm thử driver/database thực tế. Không đóng EntityManager **từ caller thread** để cố ngắt một worker đang dùng nó. Worker/transaction manager sở hữu cleanup của context trong worker.

## 15. Kiểm thử ranh giới ORM + async có kiểm soát

Một integration test đáng tin cậy nên chạy **Spring ApplicationContext**, datasource và transaction manager thật hoặc test profile có DB gần môi trường production. Không nên chỉ tạo object bằng `new` rồi mong `@Async`/`@Transactional` có hiệu lực.

Các phép kiểm tra gợi ý:

1. **Thread:** method async chạy trên thread có prefix executor, khác caller.
2. **Transaction tại worker:** `TransactionSynchronizationManager.isActualTransactionActive()` là true trong method transactional được gọi qua proxy.
3. **Không truyền EM/Session:** signature async chỉ có ID/immutable DTO/command, không có `EntityManager`, `Session`, `Query` hoặc managed entity.
4. **Commit visibility:** gửi việc sau khi writer transaction commit có thể đọc row; với gửi trước commit, test tình huống fail/race theo isolation thực.
5. **Lazy associations:** DTO được materialize trong transaction; truy cập DTO bên ngoài không cần Session.
6. **Rollback:** writer transaction rollback thì after-commit listener không được gửi job kiểu đó.
7. **Multiple workers:** chạy đồng thời, xác nhận mỗi worker có ranh giới transaction riêng, không dùng chung EM thực.
8. **Optimistic locking:** update đồng thời có thể báo version conflict, không âm thầm ghi đè.
9. **Timeout/rejection:** Future/HTTP/job status báo lỗi đúng và cleanup; không treo vô hạn.
10. **Crash/restart:** nếu cần durability, outbox/job store còn tác vụ pending và worker có thể retry.
11. **Context propagation:** trace ID được truyền bằng cơ chế an toàn, nhưng không sao chép transaction resource.
12. **Connection pool:** load test với concurrency/queue phù hợp, không bị starvation vì pool DB quá nhỏ.

### 15.1. Test có thể dùng latch để kiểm tra isolation

Nếu cần chứng minh hai worker có hoạt động chồng lấp, dùng `CountDownLatch` để chắc rằng chúng đã vào transaction và đang chờ ở điểm kiểm tra trước khi giải phóng. Khi đó đo active thread/transaction tại từng worker; **đừng chia sẻ một Session duy nhất chỉ để "test concurrent read"**. Hai EntityManager riêng mới là hành vi đúng cần kiểm chứng.

Tránh test chỉ đo thời gian ngắn hơn một ngưỡng bất kỳ: JVM warmup, H2 memory mode, executor scheduling và CI load khiến kết quả dao động. Kiểm tra đúng thread, transaction và DTO output là tín hiệu ổn định hơn.

## 16. Các lỗi thường gặp và cách sửa

| Anti-pattern | Nguy cơ | Thay bằng |
| --- | --- | --- |
| Truyền `EntityManager` thực vào `@Async` | Shared non-thread-safe context, sai lifecycle | Truyền ID; load trong transaction worker |
| Truyền `session = em.unwrap(Session.class)` | Worker chạm Hibernate Session của caller | Không unwrap để handoff; dùng repository tại worker |
| Tạo `EntityManager` ngoài rồi để nhiều tasks dùng chung | Race, flush/cache/lifecycle không an toàn | Tạo EM riêng per worker/unit of work và close |
| Singleton giữ `@PersistenceContext(EXTENDED)` | Extended context không thread-safe | Dùng transaction-scoped proxy mặc định |
| Hiểu field `@PersistenceContext` singleton là Session thật duy nhất | Nhầm shared proxy với underlying context | Hiểu cơ chế delegation theo transaction |
| Truyền managed entity vào async task | Data race và lazy access sai | ID/immutable DTO, materialize đúng transaction |
| Trả `PersistentCollection`/JPA Stream qua Future | Session/connection phụ thuộc lifecycle cũ | Map sang DTO/list snapshot trước khi trả |
| `@Async` đọc row trước writer commit | Không thấy dữ liệu hoặc block vì lock | After-commit dispatch / outbox tùy durability |
| Dùng `@Transactional` tại caller rồi kỳ vọng worker kế thừa | Transaction thread-bound | Worker tự mở transaction qua proxy |
| Dùng `this.asyncMethod()` trong cùng bean | Bỏ qua async proxy mặc định | Tách bean cộng tác hoặc gọi qua proxy đúng |
| Copy transaction thread-local sang worker | Chia sẻ stateful JDBC/JPA resources sai | Propagate dữ liệu thuần, không propagate tx |
| Bật OEIV để tránh lỗi lazy ở worker | Không giải quyết thread confinement | Fetch/materialize trong đúng transaction |
| `readOnly=true` rồi chia sẻ EM thực | Read-only không biến EM thành thread-safe | Context riêng per worker |
| Future.cancel(true) và tin DB đã rollback | Cancel là best-effort | Transaction/query timeout, verify outcome |
| Gửi async rồi trả 202 Accepted như bảo đảm durable | Job có thể mất khi crash/rejection | Job store/outbox với status rõ |
| Quá nhiều worker so với DB connections | Pool exhaustion/latency | Bounded concurrency và DB pool sizing |

## 17. Checklist code review

- [ ] Không truyền `EntityManager` thực, `Session` thực, `Query`, `EntityTransaction` hoặc Hibernate collection proxy sang worker khác.
- [ ] Phân biệt **Spring `@PersistenceContext` shared proxy** với **provider EntityManager thực**.
- [ ] Không đặt `PersistenceContextType.EXTENDED` vào singleton được gọi đồng thời.
- [ ] Không đưa managed JPA entity qua async boundary; dùng ID/immutable DTO/command với semantic rõ.
- [ ] Mỗi database operation tại worker có transaction riêng được quản lý qua Spring service/repository proxy.
- [ ] `@EnableAsync` và `@EnableTransactionManagement` ở đúng context, không self-invoke method cần advice.
- [ ] Không dựa vào transaction/ThreadLocal của caller sau khi chuyển thread.
- [ ] Có quy tắc **sau commit** khi worker cần đọc dữ liệu vừa ghi.
- [ ] Với task quan trọng, lưu outbox/job để chịu được crash/restart.
- [ ] DTO được materialize hoàn toàn; không lộ lazy association, JPA Stream hoặc provider handles.
- [ ] Có optimistic locking/idempotency khi các worker cập nhật cùng bản ghi.
- [ ] Executor và DB connection pool được sizing phù hợp; rejection có tín hiệu khi cần.
- [ ] Future/CompletableFuture được giám sát lỗi, timeout và cancellation; không nuốt lỗi nền.
- [ ] Có integration test thật cho thread, transaction, commit visibility, rollback và reload DTO.
- [ ] Không sử dụng `em.close()` lên shared proxy do Spring quản lý; để framework quản lý lifecycle.
- [ ] Manual EntityManager (nếu thật sự cần) chỉ tạo/sử dụng/đóng trong unit of work thuộc thread sở hữu.
- [ ] Tracing có correlation ID, nhưng transaction resources không được copy qua threads.

## 18. Câu hỏi tự kiểm tra

1. **Vì sao không nên gửi Hibernate `Session` vào `CompletableFuture.supplyAsync`?** Vì Session thực có trạng thái và không thread-safe; unit of work/transaction không tự di chuyển.
2. **`EntityManagerFactory` có thể là singleton được nhiều worker dùng không?** Có. Factory dùng chung, từng worker cần context/transaction thích hợp.
3. **Field `@PersistenceContext` trong singleton Spring repository có luôn là một provider EntityManager duy nhất không?** Không. Mặc định nó thường là shared proxy delegate theo transaction.
4. **Dùng chung reference shared proxy có đồng nghĩa dùng chung persistence context không?** Không; chính proxy chọn underlying EM theo transaction/operation.
5. **Vì sao `@PersistenceContext(type=EXTENDED)` khác?** Nó tạo extended persistence context có vòng đời dài, không phù hợp cho singleton concurrent access.
6. **Có thể truyền một managed entity sang worker thay EM không?** Không nên. Entity có thể mutable/lazy, gắn với context cũ và không tự thread-safe.
7. **Worker cần dữ liệu order thì nên nhận gì?** ID hoặc immutable command/DTO, rồi load bằng repository/transaction worker.
8. **`@Transactional` tại caller có đi theo `@Async` worker không?** Không với imperative thread-bound transactions.
9. **Vì sao gửi async trước commit có thể thấy "not found"?** Worker có transaction riêng và không thấy dữ liệu chưa commit theo isolation của DB.
10. **Khi nào dùng `@TransactionalEventListener(AFTER_COMMIT)`?** Khi muốn chỉ dispatch công việc sau khi transaction gốc commit thành công và chấp nhận hạn chế in-memory delivery.
11. **Khi nào cần outbox?** Khi task/event bắt buộc sống qua crash, phải có retry và ghi nhận trạng thái bền vững.
12. **`@Transactional(readOnly=true)` có làm EntityManager thực thread-safe không?** Không.
13. **Nếu caller và worker cùng update một row bằng EM riêng, xử lý xung đột ra sao?** Locking/versioning, constraints, retries có kiểm soát và idempotency.
14. **OEIV có cho phép worker sử dụng session của request không?** Không; nó không biến Session thành thread-safe.
15. **`Future.cancel(true)` có chắc đóng Session và rollback transaction không?** Không. Hủy là best-effort; transaction/driver/lifecycle quyết định.
16. **Nếu cần database access trong worker ngoài Spring, làm sao quản lý EM?** Dùng shared EntityManagerFactory, tạo EntityManager **tại worker**, quản lý transaction và close ở cùng unit of work.
17. **Cách test hiệu quả hơn đo thời gian thuần túy là gì?** Kiểm tra thread name, worker transaction active, DTO đúng, visibility sau commit và concurrency bằng latch.
18. **Điều gì được "chia sẻ" một cách an toàn trong Spring repository dùng @PersistenceContext?** Shared proxy reference, **không phải một underlying persistence context thực**.

**Kết luận:** Đừng chuyển **EntityManager/Session thực hoặc managed ORM objects** sang thread khác để tái sử dụng transaction/persistence context. Trong Spring, hãy chuyển **ID hoặc dữ liệu bất biến** và để **worker có transaction/persistence context riêng**. `@PersistenceContext` mặc định trong singleton repository là shared proxy có khả năng điều phối theo transaction; nó **không** phải ngoại lệ cho phép chia sẻ một Hibernate Session thực. Đồng thời kiểm soát thời điểm commit, lazy data, overload, durability và các cập nhật cạnh tranh.

### Tài liệu chính thức

- [Jakarta Persistence 3.1 — Persistence Context, EntityManager và thread safety](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)
- [Spring Framework — JPA: EntityManagerFactory, @PersistenceContext và EXTENDED](https://docs.spring.io/spring-framework/reference/data-access/orm/jpa.html)
- [Spring Framework 6.1 — SharedEntityManagerCreator](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/orm/jpa/SharedEntityManagerCreator.html)
- [Spring Framework — Declarative Transactions and Thread-bound Context](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Hibernate ORM — Session Javadoc](https://docs.jboss.org/hibernate/orm/6.4/javadocs/org/hibernate/Session.html)
- [Hibernate ORM — Transactions and Session-per-application Anti-pattern](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#transactions-basics)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactions-events-005">Truyền mã định danh hoặc dữ liệu bất biến thay cho thực thể đang gắn phiên</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền mã định danh hoặc dữ liệu bất biến thay cho thực thể đang gắn phiên

## 1. Bài toán: một entity JPA không phải thông điệp an toàn giữa các thread

Trong một ứng dụng Spring, transaction thường mở một persistence context để truy xuất entity JPA qua `EntityManager`/Hibernate `Session`. Ở thread xử lý request, code lấy `OrderEntity` từ repository, sửa trạng thái, rồi muốn phát sự kiện hoặc gửi entity đó cho method `@Async`. Cách làm tưởng tiện:

```java
// Phản ví dụ, không phải một chương trình Java độc lập.
@Transactional
public void approve(long orderId) {
    OrderEntity order = repository.findById(orderId).orElseThrow();
    order.approve();
    backgroundWorker.notifyCustomer(order); // Truyền entity đang managed.
}
```

Nhưng `backgroundWorker` có thể chạy trên thread khác khi transaction của caller chưa commit, đã rollback hoặc persistence context đã đóng. Entity có thể chứa lazy proxy, collection chưa khởi tạo, trạng thái mutable và tham chiếu đến đồ thị đối tượng lớn. **Một entity đang managed không phải hợp đồng dữ liệu được thiết kế để chuyển giữa transaction, thread, process hay message broker.**

Mục tiêu bài học thuộc nhóm **Giao dịch và sự kiện** của chủ đề **Spring Framework - concurrency**: **truyền mã định danh (ID) hoặc dữ liệu bất biến (immutable snapshot/DTO) thay cho entity đang gắn persistence context** ở những ranh giới bất đồng bộ.

Sau khi học xong, bạn cần:

1. Giải thích managed, detached, lazy association, dirty checking và lý do `Session`/persistence context không thread-safe.
2. Hiểu vì sao `@Async` không tự chuyển transaction, `EntityManager` hay Hibernate session của caller sang worker.
3. Chọn giữa **truyền ID rồi load lại**, **truyền immutable snapshot**, và **truyền ID kèm version** theo yêu cầu nhất quán.
4. Biết tạo snapshot **khi context còn hợp lệ**, không vô tình đụng lazy association trong worker.
5. Áp dụng Spring `@TransactionalEventListener(phase = AFTER_COMMIT)` đúng phạm vi; phân biệt sự kiện sau commit với hàng đợi bền vững.
6. Dùng `@Version`/optimistic locking hoặc conditional update khi công việc cũ không được ghi đè trạng thái mới.
7. Nhận diện giới hạn của `List.copyOf` và `record`: immutable reference không tự làm cho cả object graph sâu bên trong bất biến.
8. Có ví dụ Java 17+ chạy được, cấu trúc Spring/JPA minh họa và kế hoạch test lỗi concurrency.

**Thông điệp chính:** truyền một entity reference sang thread khác chỉ truyền **tham chiếu**, không truyền một transaction còn sống, không clone trạng thái và không trao quyền sử dụng persistence context từ thread khác.

## 2. Persistence context thực sự quản lý điều gì?

Một JPA persistence context theo dõi các entity và quan hệ của chúng trong một đơn vị công việc. Hibernate `Session` cung cấp persistence context, thường đóng vai trò:

- **Identity map / first-level cache:** trong một context, một hàng entity theo định danh có một managed representation tương ứng.
- **Dirty checking:** Hibernate có thể nhận diện thay đổi trên managed entity và đồng bộ xuống database khi flush/commit thích hợp.
- **Lazy loading:** một association có thể được đại diện bằng proxy/collection chưa được nạp; việc truy cập có thể cần một context/session còn hiệu lực.
- **Unit of work:** các thao tác được phối hợp trong phạm vi transaction/flush theo cấu hình.
- **Lifecycle:** context có thể là transaction-scoped, extended hoặc application-managed; không phải mặc định tồn tại suốt vòng đời JVM.

Trong cấu hình Spring Data JPA thông thường, transaction-bound `EntityManager` được quản lý theo phạm vi transaction và thread context phù hợp. Entity lấy ra bên trong transaction có thể là **managed** ở thời điểm đó. Sau khi context đóng, entity có thể trở thành **detached**; việc truy cập trường đã nạp có thể vẫn hoạt động, còn lazy association chưa nạp có thể gây `LazyInitializationException` khi dùng Hibernate.

### 2.1. Bốn trạng thái entity JPA cơ bản

| Trạng thái | Diễn giải | Rủi ro khi chuyển sang background worker |
| --- | --- | --- |
| `new/transient` | Object mới, chưa gắn persistence context | Không có dirty checking; ID có thể chưa được cấp |
| `managed` | Object gắn với persistence context | Chia sẻ graph/session không thread-safe, dữ liệu có thể chưa commit |
| `detached` | Entity từng được quản lý nhưng không còn gắn context đó | Lazy chưa load có thể lỗi, dữ liệu cũ, thay đổi không tự flush |
| `removed` | Entity đã được đánh dấu xóa trong context | Worker có thể quan sát trạng thái không còn phù hợp sau commit/rollback |

**Cần phân biệt:** một reference entity được **truyền** sang worker không tự động thành detached ngay lập tức chỉ vì đổi thread. Entity vẫn có thể đang managed trong **context của caller**. Vấn đề là context/Session không được thiết kế để sử dụng đồng thời từ nhiều thread; khi context kết thúc, reference có thể detached.

### 2.2. Session không thread-safe

Hibernate User Guide ghi rõ `Session`/`EntityManager` và persistence context **không thread-safe**. Không đưa cùng một `EntityManager`/`Session` thật sang nhiều worker, và không cho các worker đồng thời thao tác cùng một managed entity graph trong một context.

Một nuance quan trọng: bean `EntityManager` được Spring inject qua `@PersistenceContext` trong nhiều cấu hình thực tế có thể là **shared transactional proxy** an toàn để *inject* vào nhiều Spring services, vì proxy định tuyến tới target `EntityManager` phù hợp với thread/transaction. Điều đó **không** làm cho **target EntityManager**, persistence context, hoặc entity lấy từ context trở thành đối tượng dùng chung an toàn giữa thread.

### 2.3. OSIV có thể che lấp lỗi thiết kế

Open Session in View (OSIV) có thể giữ persistence context trong suốt vòng đời HTTP request ở một số cấu hình Spring web, khiến lazy properties vô tình được nạp khi serialize response. Nó không bảo đảm session còn sống trong mọi `@Async` worker, không biến JPA entity thành thread-safe và dễ dẫn đến truy vấn N+1/khó quan sát số query.

Không bật OSIV hoặc giữ transaction mở dài chỉ để “chữa” việc chuyển entity sang worker. Hãy **xác định dữ liệu cần dùng** và xây boundary rõ ràng.

## 3. Vì sao truyền trực tiếp entity sang @Async dễ sai?

```text
Request thread                          Worker thread
-------------------------               ------------------------
@Transactional
   |
   | findById(42)
   v
managed OrderEntity ----- reference ---> @Async process(OrderEntity)
   |                                        |
   | mutate/flush/commit                    | getCustomer().getEmail()
   |                                        | getLines().size()
   | close persistence context              | mutate order.setStatus(...)
   v                                        v
transaction ended                    lazy failure / stale data /
                                     concurrent modification /
                                     no predictable flush
```

Một số tình huống thực tế:

- **Lazy association:** `order.getLines()` chưa khởi tạo. Worker đụng vào sau khi context đóng có thể gặp `LazyInitializationException`.
- **Race khi caller còn chạy:** worker đọc `order.status` đúng lúc caller đang sửa; không có snapshot độc lập.
- **Dirty checking sai kỳ vọng:** worker thay đổi entity detached không tự làm database được update; worker thay đổi managed entity từ thread khác lại là sử dụng context không an toàn.
- **Đọc trước commit:** worker có thể chạy trước transaction tạo/sửa order commit. Một query trong transaction mới thậm chí chưa thấy hàng mới, hoặc đọc phiên bản cũ.
- **Rollback:** caller phát event/submit task trước commit rồi transaction rollback; worker vẫn có thể gửi email về một order chưa từng được commit.
- **Serialize message:** JPA proxy/bi-directional relations gây payload rất lớn, recursion, dữ liệu không chủ đích hoặc lỗi serializer.
- **Gắn transaction lâu:** giữ entity manager/connection mở trong suốt tác vụ chậm gây tăng contention và giữ first-level cache lớn.

Không phải lần nào truyền entity cũng lập tức ném lỗi. Một entity đã nạp toàn bộ trường và chỉ đọc có thể trông như chạy “ổn” trong test. Điều đó **không phải bảo đảm** về tính nhất quán, thread-safety hay vòng đời.

## 4. Nguyên tắc: chuyển quyền truy cập dữ liệu thành một hợp đồng rõ ràng

Thay vì mang theo **managed entity reference**, hãy mang theo một **message/command** gồm dữ liệu tối thiểu, ổn định và có ý nghĩa nghiệp vụ:

1. **ID:** worker nhận định danh rồi tự mở transaction/persistence context phù hợp để load dữ liệu.
2. **Immutable snapshot:** caller tạo DTO bất biến từ những trường cần thiết khi context hợp lệ, rồi chuyển qua worker.
3. **ID + version/revision:** worker dùng ID để tải lại hoặc kiểm tra version nhằm phát hiện job đã lỗi thời.
4. **Hybrid:** ID + version + vài trường snapshot để tối ưu, nhưng vẫn định nghĩa dữ liệu nào là authoritative.

| Tiêu chí | ID rồi re-fetch | Immutable snapshot | ID + expected version |
| --- | --- | --- | --- |
| Payload | Nhỏ | Theo số trường đã chọn | Nhỏ |
| Thời điểm dữ liệu được đọc | Lúc worker load | Lúc caller tạo snapshot | Lúc worker load/so version |
| Thấy dữ liệu cập nhật sau event? | Có thể, tùy transaction/isolation | Không, snapshot giữ giá trị cũ | Có thể phát hiện thay đổi |
| Lazy loading | Worker load trong context mới | Caller phải materialize trường cần thiết trước khi gửi | Worker load trong context mới |
| Dễ dùng qua queue/broker | Có | Có nếu DTO serializable theo protocol | Có |
| Chi phí query ở worker | Có | Có thể không | Thường có |
| Rủi ro stale | Đọc state mới nhưng có thể lệch event | Snapshot cũ theo chủ đích | Có luật từ chối stale |
| Sử dụng phổ biến | “Xử lý trạng thái hiện tại của order X” | “Gửi thông điệp phản ánh event tại thời điểm T” | “Chỉ xử lý nếu revision X còn hợp lệ” |

Không có lựa chọn “luôn tốt nhất”. Phải hỏi **ngữ nghĩa thời điểm**: worker nên xử lý **trạng thái mới nhất hiện tại** hay **sự kiện đã xảy ra tại một thời điểm cụ thể**?

## 5. Mẫu 1 — Chỉ truyền ID, worker load lại trong transaction của nó

Đây thường là điểm khởi đầu đơn giản nhất:

```java
// Phản mẫu: OrderEntity đang managed được đưa qua async boundary.
@Async("orderExecutor")
public CompletableFuture<Void> sendNotice(OrderEntity order) {
    String email = order.getCustomer().getEmail();
    gateway.send(email, order.getId());
    return CompletableFuture.completedFuture(null);
}
```

Đổi sang:

```java
// Fragment Spring: chỉ truyền giá trị định danh.
@Async("orderExecutor")
public CompletableFuture<Void> sendNotice(long orderId) {
    NoticeSnapshot notice = snapshotLoader.loadById(orderId);
    gateway.send(notice);
    return CompletableFuture.completedFuture(null);
}
```

`snapshotLoader` là một **Spring bean khác** có method `@Transactional(readOnly = true)`. Nó load entity trong transaction mới của worker, lấy đúng dữ liệu và trả snapshot. Worker gửi email **sau khi** transaction đọc đã đóng, không giữ transaction qua network I/O kéo dài.

Lợi ích:

- Không chia sẻ entity/Session giữa caller và worker.
- Mỗi transaction có persistence context độc lập.
- Payload có thể truyền qua message broker: chỉ ID, version, tenant ID nếu cần.
- Dễ thay implementation lưu trữ và tối ưu query mà không thay event schema phức tạp.
- Có thể kiểm soát trường hợp record đã bị xóa hoặc không nhìn thấy do commit chưa xảy ra.

### 5.1. Re-fetch có nghĩa đọc state vào thời điểm worker chạy

Giả sử tại thời điểm event, order có status `CREATED`, nhưng trước khi worker chạy, người khác cập nhật thành `CANCELLED`. Worker load bằng ID có thể thấy `CANCELLED`. Đó là hành vi **đúng** nếu mục tiêu là “chỉ gửi nếu order hiện còn được phép gửi”; nhưng có thể **sai** nếu người nhận cần một bản ghi audit chính xác về sự kiện `CREATED` đã xảy ra.

Để tránh nhầm lẫn, ghi rõ:

- **Fresh-state command:** “Đọc trạng thái hiện tại của order X và quyết định có gửi không”.
- **Historical event:** “Order X đã được xác nhận lúc T với dữ liệu Y”. Event cần snapshot hoặc version/historical storage để diễn tả sự kiện.
- **Guarded command:** “Chỉ áp dụng lệnh nếu order X vẫn ở version V”.

### 5.2. Đợi commit trước khi worker tìm record mới tạo

Nếu caller tạo order và submit worker **trước commit**, worker có thể chạy trước khi row được commit, rồi `findById` trả empty hoặc thấy trạng thái cũ theo isolation. Không giải quyết bằng `Thread.sleep(500)` vì thứ tự scheduling không được bảo đảm.

Giải pháp đúng tùy yêu cầu:

- Gắn listener với `AFTER_COMMIT` qua `@TransactionalEventListener` khi có transaction thật.
- Lưu durable outbox/job record **cùng transaction** nếu sự kiện bắt buộc không được mất.
- Nếu workflow phụ thuộc vào transaction thành công, đừng trực tiếp gọi `@Async` để phát side effect **trước commit**.

## 6. Mẫu 2 — Snapshot bất biến giữ dữ liệu tại thời điểm phát sự kiện

Khi worker cần **giá trị tại thời điểm caller quyết định**, một immutable DTO giúp mô tả contract chính xác.

Ví dụ dùng Java 17 `record`:

```java
public record OrderNoticeSnapshot(
        long orderId,
        String customerEmail,
        String status,
        java.util.List<String> productCodes) {

    public OrderNoticeSnapshot {
        java.util.Objects.requireNonNull(customerEmail);
        java.util.Objects.requireNonNull(status);
        productCodes = java.util.List.copyOf(productCodes);
    }
}
```

Đây là **fragment định nghĩa DTO**. `record` cung cấp các final components; `String` bất biến; `List.copyOf` chụp cấu trúc list và trả unmodifiable list, từ chối null. Vì mỗi element là `String` bất biến nên đồ thị trong ví dụ có tính chất snapshot tốt.

**Cảnh báo sâu:** `List.copyOf` chỉ **shallow copy** cấu trúc list, không copy sâu từng phần tử. Nếu `productCodes` là `List<MutableLine>`, mỗi `MutableLine` vẫn có thể bị sửa sau khi gửi. Tương tự, một record chứa `Date`, mảng, entity, `List<MutableEntity>` hoặc object có setter **không tự thành deep immutable** chỉ vì outer type là record.

### 6.1. Chọn đúng dữ liệu

Snapshot tốt:

- Chỉ có trường worker thực sự cần.
- Có ý nghĩa rõ: ID, operation ID, timestamp, status tại thời điểm phát event, version nếu cần.
- Không chứa `EntityManager`, `Session`, `PersistenceContext`, lazy proxy hoặc live `PersistentCollection`.
- Không chứa object có lifecycle request hay thông tin nhạy cảm không cần thiết.
- Có schema ổn định nếu được serialize qua message broker.
- Dùng immutable scalars, nested records/DTOs cũng bất biến, và immutable collections.

Nếu một quan hệ được lazy load, hãy chủ động fetch/khởi tạo **trong transaction đang mở** và chuyển từng trường thành DTO; không truyền lazy collection để worker “tự đọc sau”.

### 6.2. Transaction boundary của snapshot

Khi tạo snapshot trong method `@Transactional`, bạn đang chụp **trạng thái code nhìn thấy** trong transaction đó. Nếu sau đó transaction rollback, một snapshot được phát đi ngay từ method có thể mô tả thay đổi chưa bao giờ commit.

Vì vậy:

- Nếu event chỉ hợp lệ khi commit thành công, publish event theo contract phù hợp và xử lý bằng `@TransactionalEventListener(AFTER_COMMIT)`, hoặc ghi outbox trong cùng transaction.
- Có thể **tạo snapshot trước commit** khi entity còn được truy cập hợp lệ, rồi listener chỉ xử lý nó **sau commit**.
- Snapshot không bảo đảm dữ liệu không đổi trong DB sau thời điểm chụp; nó bảo đảm **payload đã chụp không thay đổi** nếu thiết kế thật sự bất biến.

## 7. Ví dụ Java 17+ — reference mutable và snapshot bất biến

Ví dụ Java thuần sau mô phỏng việc một thread giữ reference mutable, trong khi một object `record` chứa giá trị đã chụp. Không cần JPA để thấy rằng **truyền reference không copy giá trị**. Dùng latch để tạo thứ tự xác định, không dựa vào `Thread.sleep`.

Tệp `MutableVsSnapshotDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MutableVsSnapshotDemo {
    static final class MutableOrder {
        private volatile String status;

        MutableOrder(String status) {
            this.status = status;
        }

        String status() {
            return status;
        }

        void changeStatus(String newStatus) {
            status = newStatus;
        }
    }

    record OrderSnapshot(String status) {}

    public static void main(String[] args) throws Exception {
        MutableOrder entity = new MutableOrder("CREATED");
        OrderSnapshot snapshot = new OrderSnapshot(entity.status());
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> byReference = executor.submit(() -> {
                release.await();
                return entity.status();
            });

            Future<String> bySnapshot = executor.submit(() -> {
                release.await();
                return snapshot.status();
            });

            entity.changeStatus("CANCELLED");
            release.countDown();

            System.out.println("reference=" +
                    byReference.get(5, TimeUnit.SECONDS));
            System.out.println("snapshot=" +
                    bySnapshot.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac MutableVsSnapshotDemo.java
java MutableVsSnapshotDemo
```

Kết quả:

```text
reference=CANCELLED
snapshot=CREATED
```

**Giải thích:**

- `byReference` giữ cùng reference `entity`, nên sau khi main đổi status, worker thấy giá trị mới.
- `bySnapshot` giữ record chứa String đã được chụp lúc `CREATED`.
- `volatile` trong mẫu chỉ làm việc đọc/ghi status rõ ràng về visibility; nó không biến một JPA entity và Hibernate Session thành thread-safe.
- Latch đặt quan hệ *happens-before* cần thiết cho thứ tự quan sát. Đây là mô phỏng **tính mutable của Java reference**, không phải khẳng định về nội bộ JPA.
- Nếu nghiệp vụ muốn worker xử lý **trạng thái mới nhất**, việc re-fetch ID là một chiến lược; nếu muốn xử lý đúng event `CREATED`, snapshot là một chiến lược khác.

## 8. Ví dụ Java 17+ — đóng băng nested data bằng copy phù hợp

Một snapshot có `List` cần tránh “bất biến ở vỏ, mutable ở ruột”.

Tệp `DeepEnoughSnapshotDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class DeepEnoughSnapshotDemo {
    record Line(String productCode, int quantity) {}

    record OrderSnapshot(long id, List<Line> lines) {
        OrderSnapshot {
            lines = List.copyOf(lines);
        }
    }

    public static void main(String[] args) {
        List<Line> liveLines = new ArrayList<>();
        liveLines.add(new Line("SKU-A", 2));

        OrderSnapshot snapshot =
                new OrderSnapshot(101L, liveLines);

        liveLines.add(new Line("SKU-B", 1));

        System.out.println("live-size=" + liveLines.size());
        System.out.println("snapshot-size=" + snapshot.lines().size());
        System.out.println("first=" +
                snapshot.lines().get(0).productCode());

        try {
            snapshot.lines().add(new Line("SKU-C", 3));
        } catch (UnsupportedOperationException ex) {
            System.out.println("list-is-unmodifiable=true");
        }
    }
}
```

```bash
javac DeepEnoughSnapshotDemo.java
java DeepEnoughSnapshotDemo
```

Output:

```text
live-size=2
snapshot-size=1
first=SKU-A
list-is-unmodifiable=true
```

Ở đây `Line` là record chứa `String` và `int`; list được copy thành unmodifiable list nên snapshot đủ bất biến theo contract đơn giản này.

**Không tổng quát hóa sai:** nếu phần tử list là `MutableLine` với setter, `List.copyOf` vẫn giữ reference tới phần tử đó. Lúc ấy hãy map thành `Line` bất biến **từng phần tử** trước khi tạo list snapshot.

Một ví dụ map trong transaction JPA:

```java
List<OrderLineSnapshot> lines =
        order.getLines().stream()
                .map(line -> new OrderLineSnapshot(
                        line.getSku(),
                        line.getQuantity()))
                .toList();
```

Đây là fragment Spring/JPA, giả định `getLines()` được fetch/đọc khi persistence context hợp lệ và `OrderLineSnapshot` là immutable DTO. `Stream.toList()` trả unmodifiable list, nhưng tính bất biến sâu vẫn phụ thuộc phần tử.

## 9. Ví dụ Java 17+ — chọn fresh state hoặc snapshot theo hợp đồng

Tệp `IdVsSnapshotDemo.java`:

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IdVsSnapshotDemo {
    record Order(long id, String status) {}
    record OrderEvent(long id, String statusAtEvent) {}

    public static void main(String[] args) {
        Map<Long, Order> store = new ConcurrentHashMap<>();
        store.put(7L, new Order(7L, "CREATED"));

        Order atEvent = store.get(7L);
        long idOnly = atEvent.id();
        OrderEvent snapshot =
                new OrderEvent(atEvent.id(), atEvent.status());

        store.put(7L, new Order(7L, "CANCELLED"));

        Order current = store.get(idOnly);

        System.out.println("loaded-now=" + current.status());
        System.out.println("event-snapshot="
                + snapshot.statusAtEvent());
    }
}
```

```bash
javac IdVsSnapshotDemo.java
java IdVsSnapshotDemo
```

Output:

```text
loaded-now=CANCELLED
event-snapshot=CREATED
```

Đây là mô phỏng bằng `ConcurrentHashMap`, không phải database/JPA. Nó cố ý cho thấy hai câu hỏi khác nhau:

- **“Trạng thái của order 7 là gì ở thời điểm xử lý?”** → re-fetch theo ID.
- **“Trạng thái của order 7 khi event được tạo là gì?”** → dùng snapshot.

Trong database thật, isolation level, replication lag và transaction timing còn ảnh hưởng tới dữ liệu mà worker nhìn thấy. Vì vậy “re-fetch” không đồng nghĩa với “luôn nhìn thấy trạng thái mới nhất theo thời gian thực trên mọi replica”.

## 10. Cấu trúc Spring/JPA nên dùng: event chỉ mang ID, worker load snapshot trong transaction riêng

Các đoạn sau là **mẫu kiến trúc trong project Spring Boot + Spring Data JPA**, không phải từng file chạy chỉ với `javac`. Chúng cần imports đúng của Spring/JPA, bean database, entity/repository/service/notification gateway thực tế.

### 10.1. Entity và repository

Ví dụ domain có `OrderEntity` và `OrderLineEntity`. Trong snippet này, entity có ID và các fields; collection `lines` có thể được lazy-load.

```java
// File: OrderEntity.java (fragment)
@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    private Long id;

    @Version
    private long version;

    private String status;
    private String customerEmail;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderLineEntity> lines = new ArrayList<>();

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public String getStatus() { return status; }
    public String getCustomerEmail() { return customerEmail; }
    public List<OrderLineEntity> getLines() { return lines; }

    public void markApproved() {
        status = "APPROVED";
    }
}
```

`OrderLineEntity` là một entity riêng có quan hệ `order` và field `sku`/`quantity`; cần khai báo nó trong dự án. `@Version` giúp optimistic locking phát hiện các ghi cạnh tranh theo semantics provider, nhưng không tự biến entity thành object có thể chia sẻ giữa thread.

Repository đọc order cùng lines bằng query có chủ đích:

```java
// File: OrderRepository.java (fragment)
public interface OrderRepository
        extends JpaRepository<OrderEntity, Long> {

    @Query("""
           select distinct o from OrderEntity o
           left join fetch o.lines
           where o.id = :id
           """)
    Optional<OrderEntity> findWithLines(@Param("id") Long id);
}
```

Query này yêu cầu quan hệ/mapping hợp lệ trong project. Có thể thay bằng `@EntityGraph`, DTO projection hoặc query chuyên biệt. **Không** trả về entity này cho worker khác sau khi repository gọi xong chỉ vì query đã fetch eager; làm vậy vẫn ràng buộc code vào managed entity/mutable graph.

### 10.2. Command và snapshot tách khỏi JPA

```java
// File: OrderCommitted.java
public record OrderCommitted(long orderId) {}

// File: OrderNoticeSnapshot.java
public record OrderNoticeSnapshot(
        long orderId,
        long version,
        String customerEmail,
        java.util.List<String> productCodes) {

    public OrderNoticeSnapshot {
        productCodes = java.util.List.copyOf(productCodes);
    }
}
```

Hai record đều là **value carriers**; không chứa entity, Hibernate proxy hay `EntityManager`. Nếu `customerEmail` có dữ liệu cá nhân, hãy áp dụng tối thiểu hóa payload và chính sách xử lý dữ liệu của hệ thống, nhất là khi gửi qua broker/log.

### 10.3. Loader sở hữu transaction đọc trong worker thread

```java
// File: OrderSnapshotLoader.java (fragment)
@Service
public class OrderSnapshotLoader {
    private final OrderRepository repository;

    public OrderSnapshotLoader(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public OrderNoticeSnapshot load(long id) {
        OrderEntity order =
                repository.findWithLines(id).orElseThrow();

        List<String> codes =
                order.getLines().stream()
                        .map(OrderLineEntity::getSku)
                        .toList();

        return new OrderNoticeSnapshot(
                order.getId(),
                order.getVersion(),
                order.getCustomerEmail(),
                codes);
    }
}
```

Đây là một **Spring bean riêng**. Worker gọi `loader.load(id)` qua Spring-managed reference nên `@Transactional(readOnly = true)` được intercept khi cấu hình transaction hoạt động. Loader tạo snapshot **bên trong context**, rồi trả ra DTO. Sau khi method kết thúc, worker không cần truy cập lazy fields của entity.

**Đừng coi `readOnly = true` là cơ chế cấm ghi tuyệt đối ở mọi database:** đó là chỉ dẫn transaction/provider để tối ưu hoặc hạn chế flush theo cấu hình; correctness nghiệp vụ vẫn cần thiết kế riêng.

### 10.4. Worker @Async nhận ID, không nhận entity

```java
// File: OrderNotificationWorker.java (fragment)
@Service
public class OrderNotificationWorker {
    private final OrderSnapshotLoader loader;
    private final NotificationGateway gateway;

    public OrderNotificationWorker(
            OrderSnapshotLoader loader,
            NotificationGateway gateway) {
        this.loader = loader;
        this.gateway = gateway;
    }

    @Async("notificationExecutor")
    public CompletableFuture<Void> deliver(long orderId) {
        OrderNoticeSnapshot payload = loader.load(orderId);
        gateway.send(payload);
        return CompletableFuture.completedFuture(null);
    }
}
```

Worker mở transaction đọc **qua loader khác bean**, sau đó gọi gateway bằng DTO. Cách tách giúp tránh giữ một transaction DB mở trong lúc chờ HTTP/email I/O. Lỗi gateway làm future của lời gọi `@Async` thất bại theo cơ chế của Spring; caller/event listener cần quan sát và xử lý thất bại.

Nếu thông báo bắt buộc phải được gửi dù JVM restart, worker chỉ chạy trong RAM không đủ; xem phần **outbox** dưới đây.

### 10.5. Cấu hình executor có giới hạn

```java
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
    @Bean("notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notice-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(40);
        return executor;
    }
}
```

Fragment cần imports tương ứng. Số thread/queue chỉ là ví dụ. `@EnableAsync` kích hoạt async annotation processing; bean executor quyết định tài nguyên. Nếu `notificationExecutor` bị reject khi quá tải, application phải có chính sách xử lý, không bỏ lặng exception hoặc chỉ tin rằng “đã publish event là xong”.

## 11. Sự kiện gắn transaction: chỉ xử lý sau commit

Một luồng tiêu biểu là service transactional cập nhật order và phát **event nhẹ**:

```java
// File: OrderApplicationService.java (fragment)
@Service
public class OrderApplicationService {
    private final OrderRepository repository;
    private final ApplicationEventPublisher publisher;

    public OrderApplicationService(
            OrderRepository repository,
            ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Transactional
    public void approve(long orderId) {
        OrderEntity order =
                repository.findById(orderId).orElseThrow();
        order.markApproved();

        // Event mang ID, không mang OrderEntity.
        publisher.publishEvent(new OrderCommitted(orderId));
    }
}
```

Nếu listener thông thường được gọi ngay trong transaction, việc gọi worker trực tiếp có thể chạy **trước khi commit**. Ta cần listener theo pha transaction:

```java
// File: OrderCommittedListener.java (fragment)
@Component
public class OrderCommittedListener {
    private final OrderNotificationWorker worker;

    public OrderCommittedListener(
            OrderNotificationWorker worker) {
        this.worker = worker;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCommitted event) {
        worker.deliver(event.orderId())
              .whenComplete((ignored, error) -> {
                  if (error != null) {
                      // Ghi nhận lỗi theo logging/monitoring thực tế.
                  }
              });
    }
}
```

Các snippet này cần imports, transaction manager thực sự và event được publish **trong transaction**. `@TransactionalEventListener` mặc định gắn với pha `AFTER_COMMIT`. Khi transaction không tồn tại, listener mặc định **không được gọi**, trừ khi bật `fallbackExecution=true` theo yêu cầu rõ ràng.

**Quan hệ thời gian dự kiến:**

```text
Request thread:
  @Transactional approve()
     -> find managed OrderEntity
     -> mutate entity
     -> publish OrderCommitted(orderId)
     -> commit transaction
     -> listener AFTER_COMMIT gọi worker.deliver(orderId)

Worker executor thread:
  @Async deliver(orderId)
     -> @Transactional(readOnly=true) loader.load(orderId)
     -> mở context mới trong worker
     -> map entity sang immutable snapshot
     -> đóng transaction đọc
     -> gateway.send(snapshot)
```

Listener sau commit **không tự động bất đồng bộ**. `@TransactionalEventListener` gắn với lifecycle của transaction; bước gọi `worker.deliver(...)` mới đi qua `@Async` proxy để submit task cho executor.

### 11.1. Hai cảnh báo quan trọng về AFTER_COMMIT

**Cảnh báo 1 — Sự kiện in-memory không bền vững.** Transaction DB có thể commit thành công rồi JVM chết **trước khi** listener kịp submit task, hoặc task đã nằm trong queue RAM nhưng process bị kill. `AFTER_COMMIT` ngăn chạy trước commit theo cấu hình, **không** cam kết at-least-once delivery qua restart. Nếu không được mất tác vụ, dùng transactional outbox/durable job queue.

**Cảnh báo 2 — Resources sau commit có thể vẫn còn bound.** Theo Javadoc Spring, trong callback `AFTER_COMMIT`/`AFTER_COMPLETION`, transactional resources đôi khi vẫn accessible mặc dù transaction đã commit; một thao tác DB ghi trong cùng transaction resource có thể **không được commit tiếp**. Nếu listener phải thực hiện ghi transactional đồng bộ, dùng transaction mới phù hợp (ví dụ `REQUIRES_NEW` qua Spring proxy); trong mẫu trên, ta chỉ submit sang worker, còn loader mở transaction ở thread worker.

Đừng coi “đã sau commit” là đồng nghĩa mọi resource đã bị remove ngay tại thời điểm callback bắt đầu.

### 11.2. Nếu cần snapshot của trạng thái trước/sau cụ thể

Listener chỉ nhận ID sẽ load **trạng thái hiện tại khi worker chạy**, có thể là bản được cập nhật *sau* event. Nếu email phải phản ánh chính xác “đã duyệt lúc T, tổng tiền Y” thì event nên chứa immutable snapshot của những trường đó, được xây **trong transaction** khi trạng thái đã chuẩn bị đúng và chỉ được xử lý sau commit, hoặc lưu dữ liệu trong outbox theo transaction.

Không lấy entity `OrderEntity` rồi trì hoãn việc map DTO đến listener sau commit; điều đó đưa rủi ro lazy/context trở lại. **Materialize snapshot khi context còn hợp lệ.**

## 12. Khi cần tránh dữ liệu cũ: ID kèm version và optimistic locking

Truyền ID bảo đảm có thể re-fetch trong context riêng, nhưng không tự chống xử lý thông tin cũ. Dùng `@Version` hoặc revision/generation nếu cần xác nhận công việc tương ứng trạng thái nào.

Ví dụ event:

```java
public record OrderNotificationRequested(
        long orderId,
        long expectedVersion) {}
```

Worker đọc version hiện tại rồi quyết định:

```java
OrderNoticeSnapshot current = loader.load(event.orderId());

if (current.version() != event.expectedVersion()) {
    // Event đã cũ; skip, reconcile hoặc theo policy.
}
```

Đây chỉ là **fragment kiểm tra trước xử lý**. Nếu dữ liệu có thể đổi **giữa lúc kiểm tra và lúc commit side effect**, phép so sánh trong Java có race kiểu check-then-act. Cần thực thi điều kiện **nguyên tử tại ranh giới cập nhật**: JPA optimistic lock khi flush/commit, conditional SQL UPDATE theo version, hoặc fencing ở downstream.

Cũng cần lưu ý `@Version` thường được cập nhật khi flush. Nếu publish event trong method transactional mà đọc `getVersion()` **trước flush**, giá trị có thể vẫn là version cũ. Nếu cần **committed version** trong event, thiết kế cách lấy version sau khi flush và chỉ xử lý event sau commit; kiểm tra behavior của JPA provider và transaction thực tế.

### 12.1. Conditional update ở database

Ví dụ SQL minh họa:

```sql
UPDATE orders
SET notification_state = 'SENT'
WHERE id = :order_id
  AND version = :expected_version
  AND notification_state = 'PENDING';
```

Số hàng update là tín hiệu quan trọng: 1 nghĩa là điều kiện phù hợp tại thời điểm database áp dụng câu lệnh; 0 nghĩa là record/version/state đã khác hoặc không tồn tại. Cần xử lý transaction, isolation và version increments đúng schema. **Đây chưa phải một fencing guarantee cho lệnh gửi email bên ngoài**, vì gửi email xảy ra ở hệ thống khác.

Đối với giao dịch cập nhật nhiều bảng, thiết kế transaction để check + thay đổi **cùng phạm vi nguyên tử**, không làm một `findById` rồi ghi tự do sau thời gian dài.

### 12.2. Ví dụ Java 17+: bảo vệ cập nhật bằng version

Tệp `VersionedUpdateDemo.java`:

```java
import java.util.concurrent.atomic.AtomicReference;

public class VersionedUpdateDemo {
    record State(long version, String status) {}

    static boolean updateIfVersion(
            AtomicReference<State> store,
            long expectedVersion,
            String newStatus) {

        while (true) {
            State current = store.get();

            if (current.version() != expectedVersion) {
                return false;
            }

            State next = new State(
                    current.version() + 1,
                    newStatus);

            if (store.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    public static void main(String[] args) {
        AtomicReference<State> state =
                new AtomicReference<>(new State(1, "CREATED"));

        long oldVersion = state.get().version();

        state.set(new State(2, "APPROVED"));

        System.out.println("stale-applied=" +
                updateIfVersion(state, oldVersion, "SENT"));
        System.out.println("current-applied=" +
                updateIfVersion(state, 2, "SENT"));
        System.out.println("final=" +
                state.get().version() + ":" +
                state.get().status());
    }
}
```

```bash
javac VersionedUpdateDemo.java
java VersionedUpdateDemo
```

Stdout:

```text
stale-applied=false
current-applied=true
final=3:SENT
```

Đây là mô hình một JVM dùng `AtomicReference.compareAndSet`. So sánh version + cập nhật state được thực hiện nguyên tử bằng CAS trên **reference hiện tại**. Trong production có nhiều replica, dùng **database conditional update/optimistic locking**, không dựa vào một `AtomicReference` cục bộ.

Nếu tác dụng phụ là email/HTTP call, không thể đặt network I/O vào CAS để tạo transaction phân tán. Sử dụng outbox/idempotency/reconciliation theo hợp đồng hệ nhận.

## 13. Immutable snapshot còn phải an toàn về ownership và serialization

Một DTO tốt không nên dùng lại **JPA entity** bên trong:

**Không an toàn:**

```java
record OrderEvent(long id, OrderEntity entity) {}
record CustomerEvent(long id, List<CustomerEntity> customers) {}
```

Outer record bất biến về reference, nhưng `entity` và phần tử list vẫn mutable và có thể giữ lazy proxies.

**Tốt hơn:**

```java
record CustomerSnapshot(long id, String email) {}

record OrderEvent(
        long id,
        long revision,
        List<CustomerSnapshot> customers) {

    OrderEvent {
        customers = List.copyOf(customers);
    }
}
```

Ví dụ này có thể cần import `List`; là fragment định nghĩa DTO. Phải tạo `CustomerSnapshot` từ các scalar fields đã load trong transaction, không tạo một record chỉ để tiếp tục giữ entity reference.

### 13.1. Đừng lạm dụng serialize entity

Tránh phát entity toàn bộ qua JSON/Kafka/RabbitMQ vì:

- Trường lazy có thể bị truy cập ngoài session.
- Mối quan hệ hai chiều dẫn tới recursion hoặc payload lớn.
- Schema persistence khác schema event; đổi JPA mapping có thể phá consumer.
- Có thể vô tình rò dữ liệu cá nhân hoặc thông tin nội bộ.
- Entity có thể gắn behavior và invariant không phù hợp serialized contract.
- “Deserialized entity” không vì vậy tự là một **managed entity** trong transaction mới.

Message nên dùng event DTO có schema riêng, version khi cần, trường tối thiểu và ID hợp lệ. Các consumer phải có cách xử lý message cũ trong quá trình schema evolution.

### 13.2. ID không phải đủ nếu có nhiều tenant

Trong ứng dụng multi-tenant, chỉ truyền `orderId` có thể không đủ để xác định đúng phạm vi truy cập. Event nên có `tenantId`/scope cần thiết (không phải toàn bộ `SecurityContext` mutable), và worker phải kiểm tra quyền truy cập/tenant isolation tại nguồn dữ liệu authoritative. Đừng tin rằng vì controller đã được authenticate nên worker bất đồng bộ tự mang đầy đủ security context.

### 13.3. Version và event time không giống nhau

- `id` xác định tài nguyên.
- `version` xác định revision và hỗ trợ phát hiện stale update.
- `eventId` định danh một lần phát event/operation để deduplicate.
- `occurredAt` mô tả thời điểm sự kiện; không phải khóa concurrency đủ mạnh.
- `tenantId` xác định scope.
- `correlationId` hỗ trợ tracing và audit.

Chọn metadata có mục đích, tránh nhồi mọi trường vào một record.

## 14. @Async và transaction context là hai ranh giới khác nhau

Khi một method được gọi qua Spring `@Async` proxy, logic thường được submit lên executor và chạy trên thread khác. Spring transaction được quản lý dựa trên thread-bound context trong mô hình imperative phổ biến; transaction ở caller **không tự di chuyển sang worker**.

Các điều này cần phân biệt:

1. `@Async` bật chuyển cách thực thi qua proxy khi có `@EnableAsync`; không tự mở transaction.
2. `@Transactional` bật transaction advice khi method được intercept đúng; không tự tạo executor.
3. Một method có cả hai annotation có thể hoạt động theo thứ tự advice/config cụ thể; cách dễ kiểm soát hơn là **tách bean async worker và bean transactional loader** để boundary rõ ràng.
4. `@Transactional(readOnly = true)` trên loader mở context phù hợp với thread worker khi method được gọi qua proxy.
5. `this.loadById(id)` trong cùng bean có thể bypass transaction advice ở proxy mode; tách loader sang bean riêng hoặc dùng transaction mechanism phù hợp.
6. Context của caller có thể chứa object chưa flush/commit, nên ID re-fetch trong worker **trước commit** có thể không thấy thay đổi.

### 14.1. OpenEntityManagerInView không phải công cụ truyền context

OSIV có thể mở context trong thread request, nhưng **không** đưa cái context đó vào mọi `@Async`/`CompletableFuture` callback. Tránh dùng OSIV như giải pháp chính cho việc giữ graph sống. Kỹ thuật thiết kế đúng là query/projection/DTO và transaction scope ngắn rõ ràng.

### 14.2. Reactive context là mô hình khác

Với Spring WebFlux/R2DBC, transaction context có thể được truyền qua Reactor context thay vì `ThreadLocal`. Dù vậy, nguyên tắc message boundary vẫn hữu ích: không kéo tài nguyên/session gắn execution context qua thread hoặc callback không phù hợp. `@TransactionalEventListener` trong Spring 6.1+ cũng có hỗ trợ reactive transaction theo quy tắc riêng; cần dùng đúng event source/context. Không suy từ ví dụ JPA imperative ra cách xử lý reactive.

## 15. Khi nào truyền ID tốt hơn snapshot?

**Truyền ID** thường phù hợp khi:

- Worker phải đánh giá **trạng thái hiện tại** của entity.
- Quyền gửi/ghi phụ thuộc vào status, consent, subscription hoặc thông tin mới nhất.
- Object graph lớn, chỉ vài trường cần thiết và có repository query tối ưu.
- Job tồn tại lâu, qua nhiều retry/replica/restart.
- Event schema cần đơn giản, còn logic worker tự quyết định bằng domain state mới.

Ví dụ: “sau khi order được xác nhận, nếu hiện chưa gửi thông báo và chưa bị hủy, hãy gửi đúng một lần” → ID + kiểm tra state/version/idempotency trong worker.

**Bất lợi:** worker có thêm DB query, có thể thấy record đã thay đổi hoặc bị xóa; cần xử lý `not found` và consistency lag.

## 16. Khi nào snapshot bất biến tốt hơn ID?

**Immutable snapshot** thường phù hợp khi:

- Nội dung phải phản ánh **giá trị tại thời điểm event**.
- Consumer ở hệ khác không có quyền truy cập database nguồn.
- Không muốn một worker chậm đọc state mới và làm sai ngữ nghĩa lịch sử.
- Event là fact nghiệp vụ (“đơn đã được xác nhận với tổng tiền X”), không phải command “hãy đọc state mới nhất”.
- Muốn giảm coupling tới JPA mapping và database schema.

Ví dụ: audit/billing event cần amount, currency, approvedAt và revision **đúng lúc commit**. Snapshot phải được tạo từ dữ liệu hợp lệ và chỉ công bố sau commit; nếu cần độ bền qua crash, lưu outbox record cùng transaction.

**Bất lợi:** payload lớn hơn, cần version schema, bất biến sâu và chính sách xử lý thông tin nhạy cảm. Snapshot có thể **cố ý cũ** so với trạng thái hiện tại; consumer không nên dùng nó để ghi đè một aggregate mới hơn khi chưa có version/fencing.

## 17. Mẫu hybrid: ID + version + snapshot tối thiểu

Một event domain có thể dùng:

```java
public record OrderApprovedEvent(
        long orderId,
        long aggregateVersion,
        String approvedCurrency,
        long approvedAmountMinor,
        String eventId) {}
```

Đây là fragment value type Java 17. Consumer có thể:

- Dùng amount/currency snapshot để ghi audit sự kiện.
- Dùng `orderId` truy vấn thêm dữ liệu mới khi cần.
- Dùng `aggregateVersion` để phát hiện event cũ nếu làm state projection latest-wins.
- Dùng `eventId` để deduplicate retry/event redelivery.

Không có một field nào tự bảo đảm exactly-once qua mọi dịch vụ. Phải có unique constraint, conditional update hoặc downstream idempotency mechanism phù hợp.

### 17.1. Ví dụ SQL kiểm tra version khi áp dụng projection

```sql
UPDATE order_views
SET status = :status,
    source_version = :incoming_version
WHERE order_id = :order_id
  AND source_version < :incoming_version;
```

Đây là **SQL mẫu**, yêu cầu schema/transaction thích hợp và cơ chế xử lý hàng chưa tồn tại (UPSERT hợp lệ). Nó cho phép tránh một event version nhỏ hơn ghi đè projection mới hơn, nhưng chưa tự giải quyết insert race, duplicate event hay atomicity giữa nhiều bảng.

## 18. Transactional outbox cho event không được mất

`@TransactionalEventListener(AFTER_COMMIT)` tốt để nối logic với commit **trong một process đang sống**, nhưng **không** bảo đảm job tồn tại khi process chết ở khoảng giữa DB commit và listener/async queue.

Với yêu cầu at-least-once delivery:

```text
Trong transaction tạo/sửa order:
    UPDATE/INSERT order...
    INSERT outbox(event_id, order_id, version, immutable_payload)
COMMIT

Worker outbox:
    đọc event chưa giao
    publish/dispatch bằng retry có giới hạn
    consumer deduplicate theo event_id
    ghi trạng thái giao nhận
```

Đây là pseudocode kiến trúc, không phải SQL chạy độc lập. Khi ghi **order + outbox cùng DB transaction**, hoặc cả hai commit hoặc cả hai rollback. Consumer có thể nhận lại message, nên phải thiết kế **idempotency**. Event chứa ID hoặc immutable DTO/projection, **không chứa managed JPA entity**.

Nếu cần chống lệnh cũ đến sau lệnh mới, thêm `version`/fencing tại consumer; outbox không tự làm dữ liệu của event luôn là bản mới nhất.

### 18.1. Sau commit vẫn có thể có cạnh tranh sự kiện

Hai order events có thể được publish cùng lúc và worker xử lý không đúng thứ tự publish. Một event ID chỉ giúp dedup, không giúp sắp thứ tự. Với projection latest-wins, dùng aggregate version và conditional update. Với quy trình cần đúng một thứ tự toàn cục, phải thiết kế partitioning/order guarantee riêng.

## 19. Kiểm thử: chứng minh không chuyển managed entity qua boundary

Một test tốt phải nắm được transaction, thread, entity state và dữ liệu thực tế — không chỉ kiểm tra future đã complete.

### 19.1. Unit tests cho DTO

- Khởi tạo `OrderNoticeSnapshot` với `List` mutable, sửa list gốc sau đó và xác nhận snapshot không đổi.
- Thử sửa `snapshot.productCodes()` và xác nhận list không hỗ trợ `add`.
- Kiểm tra nested elements là immutable, không chỉ outer record.
- Kiểm tra null, missing fields, schema version và dữ liệu nhạy cảm bị loại bỏ.

Các ví dụ Java 17+ trong bài cung cấp dữ liệu mẫu để test deterministic.

### 19.2. Integration tests với JPA/Hibernate

1. Tạo order và lazy association trong transaction.
2. Load entity, tạo DTO **trong transaction**, đóng transaction.
3. Truy cập DTO trong worker thread và xác nhận không còn phụ thuộc vào Hibernate session.
4. Kiểm tra trường hợp cố tình mang một lazy proxy chưa nạp ra ngoài context và xác nhận hành vi provider tương ứng — thường là `LazyInitializationException` với Hibernate khi truy cập cần session. Không giả định tất cả entity reference luôn ném lỗi.
5. Đo query count để phát hiện N+1 từ projection; dùng fetch join/entity graph/DTO projection thích hợp.
6. Cho transaction rollback sau khi publish event, xác nhận `AFTER_COMMIT` listener không thực hiện side effect.
7. Publish event trong transaction thành công và xác nhận worker đọc dữ liệu **đã commit**.
8. Thay đổi entity trước khi worker load, kiểm tra ID path thấy state theo isolation, trong khi snapshot giữ state đã chụp.
9. Gây optimistic locking conflict và kiểm tra stale version bị từ chối.
10. Dừng ứng dụng sau DB commit nhưng trước khi dispatch, so sánh hành vi `@TransactionalEventListener` với outbox bền vững.

### 19.3. Không dùng sleep để dự đoán thứ tự

Dùng `CountDownLatch`/`CyclicBarrier`/`CompletableFuture` có timeout để điều phối *event published*, *transaction committed*, *worker started*, *worker allowed to query*. `Thread.sleep(100)` không bảo đảm transaction sẽ kịp commit hoặc worker sẽ chạy lúc mong muốn.

Ví dụ với integration test có thể giữ worker tại barrier, cho caller commit và thay đổi state, sau đó release. Kiểm tra ID path và snapshot path khác nhau. Với nhiều replica, cần kiểm tra isolation/concurrency trên DB thật thay vì chỉ in-memory map.

### 19.4. Proxy và transaction đúng thread

- `@EnableAsync` thực sự được nạp vào context?
- Method `@Async` có được gọi qua Spring-managed bean, không phải `this.method()`?
- `@Transactional` loader có được intercept qua bean khác?
- Thread executor được chọn có tên/bounds đúng?
- Method listener `AFTER_COMMIT` có được gọi khi transaction commit, bỏ qua khi rollback?
- Lỗi từ `CompletableFuture<Void>` được quan sát, không bị bỏ qua?
- Loader đóng transaction trước khi gửi external I/O kéo dài?

## 20. Sai lầm thường gặp và cách sửa

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Truyền entity đang managed trực tiếp vào `@Async` | Dùng object graph/session qua thread; race/lazy failure | Truyền ID hoặc immutable DTO |
| Nghĩ chuyển thread sẽ tự detach entity | Entity vẫn có thể thuộc context của caller | Không chia sẻ context/entity giữa thread |
| Đọc lazy association ở worker sau khi transaction caller đóng | `LazyInitializationException` khi chưa load | Fetch/materialize DTO trong transaction hợp lệ |
| Bật OSIV để “chữa” async lazy loading | Che boundary, không đảm bảo thread-safe | Transaction riêng của worker + DTO |
| Truyền `List.copyOf(entities)` | Copy list nhưng phần tử vẫn là entity mutable | Map từng phần tử thành immutable snapshot |
| Đọc entity vào event trước commit rồi xử lý ngay | Worker thấy dữ liệu chưa commit/rollback | AFTER_COMMIT hoặc outbox |
| Nghĩ `@TransactionalEventListener` luôn chạy async | Mặc định listener theo pha, không phải executor riêng | Gọi `@Async` worker qua proxy hoặc cấu hình rõ |
| Nghĩ AFTER_COMMIT bảo đảm durable delivery | JVM chết có thể mất event in-memory | Transactional outbox/queue |
| Truyền ID rồi tin worker sẽ thấy snapshot tại event | Re-fetch có thể thấy state mới | Truyền snapshot nếu cần historical fact |
| Truyền snapshot rồi dùng để cập nhật state mới nhất | Snapshot có thể cũ | Version/conditional update, domain invariant |
| Đọc `@Version` trước flush rồi tưởng committed revision | Version có thể chưa được tăng | Kiểm chứng flush/commit và payload version |
| Dùng `@Transactional` trên self-invoked helper | Advice có thể bị bypass | Tách transactional loader sang bean khác |
| Giữ transaction DB trong lúc gọi email/HTTP lâu | Giữ tài nguyên và tăng contention | Copy DTO, đóng transaction rồi gọi gateway |
| Mang `SecurityContext`/`HttpServletRequest` sang worker | Context request/thread không còn hợp lệ | Truyền ID/scope an toàn, xác thực lại ở boundary |
| Serialize whole JPA entity ra broker | Lazy recursion, schema coupling, rò dữ liệu | Message schema riêng |
| Dùng ID không kèm tenant scope | Truy cập sai dữ liệu trong multi-tenant | Tenant isolation và authorization authoritative |
| Bỏ lỗi future của worker | Job thất bại âm thầm | Theo dõi completion, metrics, retry/durable job |

## 21. Bài tập thực hành

### Bài 1 — Refactor async service

Cho code:

```java
@Transactional
public void createOrder(OrderEntity entity) {
    repository.save(entity);
    worker.sendEmail(entity);
}
```

Thiết kế lại thành: transactional service persist order và publish `OrderCommitted(orderId)`; listener `AFTER_COMMIT` dispatch `worker.sendEmail(orderId)`; worker gọi một `@Transactional(readOnly=true)` loader riêng để lấy immutable payload. Giải thích vì sao không dùng `entity` làm tham số async.

### Bài 2 — Snapshot các lazy associations

Cho `OrderEntity` có `List<OrderLineEntity> lines` lazy. Viết query fetch phù hợp, tạo `OrderNoticeSnapshot` với `List<LineSnapshot>` mà mọi element đều immutable, thử đóng persistence context và dùng snapshot ở worker thread.

### Bài 3 — Trạng thái mới nhất hay lịch sử?

Order ở phiên bản 8 khi phát event nhưng chuyển sang phiên bản 9 trước khi worker chạy. Hãy xác định behavior mong muốn cho (a) email hóa đơn lịch sử, (b) gửi thông báo nếu order chưa hủy, (c) cập nhật search projection. Chọn ID, snapshot hoặc ID+version và giải thích.

### Bài 4 — Optimistic locking

Viết test có hai worker dùng cùng `expectedVersion`. Worker đầu update thành công; worker sau phải nhận `OptimisticLockException`/số hàng conditional UPDATE bằng 0 theo implementation. Không cho một dữ liệu cũ ghi đè dữ liệu mới.

### Bài 5 — Ranh giới event và retry

Thử rollback transaction sau khi publish event; kiểm tra listener `AFTER_COMMIT` không chạy. Sau đó giả lập process chết sau commit trước listener; giải thích vì sao outbox mới có thể khôi phục công việc. Thêm `eventId` để consumer deduplicate retry.

### Bài 6 — Multi-tenant và bảo vệ dữ liệu

Thiết kế event chỉ có `tenantId`, `orderId`, `eventId` và các trường snapshot thật cần thiết. Worker query theo cả tenant và ID, không dựa vào SecurityContext request, tránh log email/full payload. Test một event tenant A không thể đọc order tenant B.

### Bài 7 — Deep immutability

Thay `List<String>` bằng `List<MutableLine>` trong record và thấy `List.copyOf` không chặn thay đổi field phần tử. Sửa bằng mapping từng line thành `record LineSnapshot` bất biến. Viết assertion sau khi mutate source.

## 22. Checklist trước khi đưa vào production

- [ ] Tôi biết entity managed và persistence context là hai khái niệm gắn với một unit of work, không phải message payload.
- [ ] Tôi không chia sẻ target `EntityManager`/Hibernate Session hoặc managed entity graph đồng thời giữa thread.
- [ ] Tôi chọn ID khi worker cần fresh state; snapshot khi worker cần state tại event; ID+version khi cần stale guard.
- [ ] Tôi không truy cập lazy association của entity trong background thread ngoài context phù hợp.
- [ ] Snapshot được tạo trong transaction hợp lệ và immutable **cả nested elements cần thiết**.
- [ ] Tôi không nghĩ `@Async` chuyển transaction hoặc request context sang worker.
- [ ] Tôi dùng bean transactional loader riêng và gọi nó qua Spring proxy.
- [ ] Tôi hiểu `@TransactionalEventListener(AFTER_COMMIT)` không đồng nghĩa với async/durability.
- [ ] Tôi không giữ transaction DB lâu khi gọi network/email bên ngoài.
- [ ] Tôi có optimistic locking/fencing nếu bản cập nhật cũ không được ghi đè bản mới.
- [ ] Tôi có outbox/idempotency khi event bắt buộc được giao và có thể retry.
- [ ] Tôi kiểm soát tenant scope, quyền truy cập và nội dung nhạy cảm trong payload.
- [ ] Tôi có test lazy loading, rollback, commit, re-fetch, snapshot, stale version và restart.

**Kết luận:** trong Spring concurrency, cách an toàn là **đi qua async/event boundary bằng những giá trị có hợp đồng rõ ràng** — ID, immutable snapshot hoặc ID+version — thay vì chuyển entity còn gắn persistence context. Worker tự mở transaction cần thiết, materialize DTO bên trong boundary đó và kiểm soát side effect sau commit. Kết quả là code có vòng đời minh bạch, ít phụ thuộc Hibernate session, dễ test cạnh tranh và dễ mở rộng qua queue/replica.

## 23. Tài liệu tham khảo chính thức

- Hibernate ORM — [User Guide, Persistence Context](https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html): managed/detached, session lifecycle, dirty checking, lazy loading và thread-safety.
- Hibernate ORM — [An Introduction to Hibernate 6](https://docs.hibernate.org/orm/6.6/introduction/html_single/): vì sao Session/EntityManager không được chia sẻ giữa thread.
- Spring Framework — [Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html): các pha `@TransactionalEventListener` và `fallbackExecution`.
- Spring Framework Javadoc — [TransactionalEventListener](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html): lưu ý nguồn lực sau `AFTER_COMMIT`.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`, executor và lifecycle.
- Spring Framework — [Declarative Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html): Spring transaction proxy và ranh giới `@Transactional`.
- Jakarta Persistence — [Jakarta Persistence Specification](https://jakarta.ee/specifications/persistence/): entity lifecycle, persistence context và `@Version`.

Hãy đối chiếu phiên bản Spring/JPA/Hibernate trong project. Các snippet Spring/JPA và SQL ở trên là **mẫu tích hợp cần có domain, imports và dependencies thực tế**; các ví dụ ghi rõ `javac`/`java` là chương trình Java 17+ độc lập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactions-events-006">@TransactionalEventListener cho xử lý gắn với kết quả giao dịch</a>

<details>
<summary>Xem chi tiết</summary>

# @TransactionalEventListener cho xử lý gắn với kết quả giao dịch

Trong Spring Framework, một thao tác nghiệp vụ có thể **ghi dữ liệu trong transaction** và đồng thời cần phát thông báo, cập nhật cache, tạo audit record hoặc gửi một công việc sang hệ thống khác. Thời điểm xử lý sự kiện rất quan trọng: nếu consumer thực hiện việc phụ **trước khi transaction commit**, hệ thống bên ngoài có thể nhận thông báo về một bản ghi sau đó bị rollback. Nếu chỉ muốn xử lý khi giao dịch đã thành công, Spring cung cấp **`@TransactionalEventListener`**, cho phép gắn listener vào **giai đoạn của transaction** thay vì thực thi listener ngay lúc sự kiện được phát.

Mặc định, `@TransactionalEventListener` chạy với `TransactionPhase.AFTER_COMMIT`. Nó cũng hỗ trợ `BEFORE_COMMIT`, `AFTER_ROLLBACK` và `AFTER_COMPLETION`. Sự khác biệt giữa các pha, hành vi khi không tồn tại transaction, và tương tác với `@Async` là nền tảng để xây dựng xử lý đồng thời đúng.

**Cảnh báo cốt lõi:** “chạy sau commit” **không đồng nghĩa** “chạy trong một transaction mới” và **không bảo đảm giao việc bền vững qua JVM crash**. Sau commit, một số tài nguyên transaction cũ vẫn có thể còn được gắn với thread; nếu cần ghi database từ callback, thường phải mở **transaction mới** với `REQUIRES_NEW`. Với công việc không được phép mất, dùng **transactional outbox** hoặc một thiết kế lưu job bền vững.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt `@EventListener` với `@TransactionalEventListener` và mô tả cơ chế đăng ký callback theo transaction hiện hành.
- Giải thích bốn giá trị của `TransactionPhase`, điều kiện gọi listener và giới hạn của từng pha.
- Hiểu `fallbackExecution=false` mặc định: sự kiện xuất bản **ngoài transaction** không được xử lý bởi transactional listener.
- Đặt `ApplicationEventPublisher.publishEvent(...)` ở đúng transaction/thread để listener gắn với outcome mong muốn.
- Giải thích vì sao `AFTER_COMMIT` không đồng nghĩa với một transaction mới, và khi nào cần `@Transactional(propagation = REQUIRES_NEW)`.
- Kết hợp `@TransactionalEventListener` với `@Async` một cách có chủ đích mà không giả định context giao dịch tự truyền sang worker.
- Phân biệt độ bền của một callback nội bộ trong JVM với bảo đảm giao việc qua shutdown, crash hoặc nhiều replica.
- Kiểm thử commit, rollback, no-transaction, thứ tự callback và rejection mà không dùng `Thread.sleep` để đoán.
- Nhận diện trường hợp transaction reactive trong Spring 6.1+ và nhu cầu truyền context khác với transaction dựa trên thread.
- Chọn đúng phương án cho gửi email, phát message, cập nhật audit/cache và tác vụ cần retry/idempotency.

## 2. Tình huống vấn đề: gửi thông báo quá sớm

Giả sử chức năng tạo đơn hàng:

~~~java
@Transactional
public void createOrder(CreateOrderRequest request) {
    orderRepository.save(new Order(request));
    emailGateway.sendConfirmation(request.email());
}
~~~

Đây là **mã minh họa trong Spring service**, cần `@Transactional`, các type và dependency ứng dụng. Khi gửi email ngay bên trong method, email có thể được gửi thành công **trước khi database transaction thực sự commit**. Sau đó commit có thể thất bại hoặc một exception dẫn đến rollback: khách hàng nhận thông báo về đơn hàng không được lưu.

Thay vì gửi trực tiếp, ta có thể phát một **domain event** trong transaction:

~~~java
@Transactional
public void createOrder(CreateOrderRequest request) {
    Order order = orderRepository.save(new Order(request));
    publisher.publishEvent(new OrderCreatedEvent(order.getId()));
}
~~~

Và xử lý khi transaction commit thành công:

~~~java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void afterOrderCommitted(OrderCreatedEvent event) {
    notifications.notifyOrderCreated(event.orderId());
}
~~~

Đây cũng là **mã minh họa**, `publisher` là `ApplicationEventPublisher`, `notifications` là service/gateway do ứng dụng cung cấp. `@TransactionalEventListener` ở đây giúp **không chạy listener nếu transaction bị rollback** trong đường xử lý chuẩn. Tuy vậy, gửi email ngay trong after-commit callback vẫn có thể lỗi; nếu JVM chết sau commit nhưng trước khi gửi, email có thể không bao giờ đi. **Không dùng after-commit callback đơn lẻ để cam kết exactly-once hoặc durable delivery.**

## 3. @EventListener và @TransactionalEventListener khác nhau thế nào?

| Tiêu chí | `@EventListener` thông thường | `@TransactionalEventListener` |
| --- | --- | --- |
| Thời điểm xử lý mặc định | Khi event được multicast tới listener, theo cấu hình multicaster (mặc định thường đồng bộ) | Khi transaction đạt pha `AFTER_COMMIT` |
| Phụ thuộc transaction hiện hành? | Không yêu cầu | Có theo mặc định |
| Nếu publisher phát trong transaction rồi rollback | Listener thường đã được gọi nếu multicaster đồng bộ | `AFTER_COMMIT` không chạy |
| Có thể chọn trước commit/sau rollback? | Không tự có ràng buộc pha giao dịch | Có `BEFORE_COMMIT`/`AFTER_ROLLBACK`/`AFTER_COMPLETION` |
| Phát event khi không có transaction | Listener thông thường vẫn có thể nhận | Mặc định **không xử lý**; `fallbackExecution=true` thay đổi điều đó |
| Có đảm bảo message/job không mất qua JVM crash? | Không | Không |
| Thread/context | Phụ thuộc multicaster và `@Async` | Đăng ký giao dịch cần đúng context; xử lý sau pha có thể đồng bộ hoặc dispatch async |

`@TransactionalEventListener` được khai báo như một event listener với thêm ràng buộc theo transaction. **Không cần chồng `@EventListener` lên cùng method** chỉ để nhận event. Việc gắn hai annotation theo cách tùy tiện có thể gây nhầm logic đọc cấu hình, còn tên method không quyết định pha.

### Hình dung luồng điển hình

~~~text
Request thread
   |
   v
Spring @Transactional proxy mở transaction
   |
   +-- INSERT order
   |
   +-- publishEvent(OrderCreated)
   |       |
   |       +-- transactional listener nhận event
   |           và đăng ký synchronization cho TX hiện tại
   |
   +-- service method trở về
   |
   +-- transaction manager quyết định commit / rollback
           |
           +-- COMMIT thành công --> AFTER_COMMIT listener
           |
           +-- ROLLBACK ---------> AFTER_ROLLBACK listener
           |
           +-- kết thúc ---------> AFTER_COMPLETION listener
~~~

**Thời điểm method `publishEvent` trả về không chứng minh listener `AFTER_COMMIT` đã chạy.** Thông thường lúc publish, listener mới được đăng ký gắn với transaction. Spring gọi listener theo pha khi transaction manager tiến hành synchronization.

## 4. Bốn pha TransactionPhase

`org.springframework.transaction.event.TransactionPhase` cung cấp bốn giá trị:

| Pha | Khi nào? | Mục tiêu thường gặp | Cần nhớ |
| --- | --- | --- | --- |
| `BEFORE_COMMIT` | Trước khi commit transaction | Kiểm tra hoặc chuẩn bị một thao tác cần xảy ra trước commit | **Chưa chắc commit thành công**; commit có thể thất bại sau callback |
| `AFTER_COMMIT` | Sau khi transaction commit thành công | Phát tín hiệu, kích hoạt việc phụ sau khi dữ liệu đã commit | Không còn lượt commit cho transaction cũ; I/O/DB phụ cần kế hoạch riêng |
| `AFTER_ROLLBACK` | Sau khi transaction bị rollback | Ghi nhận rollback, giải phóng/cập nhật trạng thái thất bại theo nhu cầu | Ghi DB mới cần transaction độc lập |
| `AFTER_COMPLETION` | Khi transaction đã kết thúc, bất kể commit hoặc rollback | Cleanup, metric, audit về completion | Không chỉ có thành công; cần biết kết quả nếu logic phân biệt |

### 4.1 BEFORE_COMMIT

~~~java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void beforeCommit(OrderCreatedEvent event) {
    verifyRequiredMetadata(event.orderId());
}
~~~

Đây là **mã tích hợp**, `verifyRequiredMetadata` do ứng dụng định nghĩa. `BEFORE_COMMIT` chạy ở giai đoạn chuẩn bị commit; transaction vẫn đang trong quá trình hoàn tất. Với synchronous callback, exception runtime có thể ảnh hưởng quá trình commit và khiến giao dịch rollback theo cơ chế transaction manager. Nhưng **callback thành công không bảo đảm database commit thành công**: lỗi ràng buộc, mạng hoặc lỗi commit vẫn có thể xảy ra sau đó.

Không gửi thông báo khẳng định đơn hàng đã thành công trong `BEFORE_COMMIT`. Nếu callback gửi email trước commit và commit sau đó thất bại, vấn đề ban đầu vẫn tồn tại.

### 4.2 AFTER_COMMIT — pha mặc định

~~~java
@TransactionalEventListener
public void onCommitted(OrderCreatedEvent event) {
    queueNonCriticalNotification(event.orderId());
}
~~~

Tương đương về pha với `@TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)`. Điều kiện cần là event được publish trong một transaction được Spring hỗ trợ và transaction đó **commit thành công**.

Mặc định listener không nhất thiết chạy trong một thread khác: transactional phase là **điều kiện về transaction**, không phải cấu hình concurrency. Nếu listener làm việc blocking trong callback đồng bộ, nó có thể kéo dài thời gian trả về của luồng đã commit transaction và thậm chí gây timeout ở request, dù transaction chính đã commit.

### 4.3 AFTER_ROLLBACK

~~~java
@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
public void onRolledBack(OrderCreatedEvent event) {
    recordRollbackMetric(event.orderId());
}
~~~

Đây là **mã minh họa**. `AFTER_ROLLBACK` chỉ có ý nghĩa khi transaction bị rollback. Nếu DB commit thành công, listener này không được gọi cho event đã đăng ký trong transaction đó.

Cần phân biệt rollback của transaction thực tế với việc một hàm nghiệp vụ trả mã lỗi **nhưng transaction vẫn commit**; listener không suy ra “thất bại nghiệp vụ” từ HTTP status hay giá trị return. Mốc quyết định là outcome của transaction manager.

### 4.4 AFTER_COMPLETION

~~~java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
public void onCompleted(OrderCreatedEvent event) {
    cleanupTemporaryState(event.orderId());
}
~~~

Callback sẽ được gọi khi giao dịch kết thúc với commit hoặc rollback theo contract của Spring. Vì nhận cùng payload `OrderCreatedEvent` mà không có status trong method signature mặc định, **không tự suy ra outcome từ việc listener chạy**. Nếu phải phân biệt commit và rollback, dùng hai listener theo pha tương ứng hoặc một transaction synchronization có thông tin trạng thái phù hợp.

## 5. Ví dụ Java 17+ độc lập mô phỏng timeline của hai kết quả

Chương trình bên dưới **không sử dụng Spring và không thực hiện transaction DB**. Nó chỉ minh họa chuỗi callback theo bốn pha để người học tự chạy được bằng JDK 17. Kết quả này không thay thế integration test `@TransactionalEventListener` trên một transaction manager thật.

Tệp **TransactionPhaseTimelineDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class TransactionPhaseTimelineDemo {
    enum Phase {
        BEFORE_COMMIT, AFTER_COMMIT, AFTER_ROLLBACK, AFTER_COMPLETION
    }

    static final class Timeline {
        private final Map<Phase, List<Runnable>> callbacks =
                new EnumMap<>(Phase.class);
        private boolean active = true;

        Timeline() {
            for (Phase phase : Phase.values()) {
                callbacks.put(phase, new ArrayList<>());
            }
        }

        void register(Phase phase, Runnable callback) {
            if (!active) {
                throw new IllegalStateException("Transaction already finished");
            }
            callbacks.get(phase).add(callback);
        }

        private void fire(Phase phase) {
            for (Runnable callback : callbacks.get(phase)) {
                callback.run();
            }
        }

        void complete(boolean commit) {
            if (!active) {
                throw new IllegalStateException("Already completed");
            }
            if (commit) {
                fire(Phase.BEFORE_COMMIT);
            }
            active = false;
            fire(commit ? Phase.AFTER_COMMIT : Phase.AFTER_ROLLBACK);
            fire(Phase.AFTER_COMPLETION);
        }
    }

    private static String simulate(boolean commit) {
        Timeline tx = new Timeline();
        List<String> observations = new ArrayList<>();

        tx.register(Phase.BEFORE_COMMIT,
                () -> observations.add("before"));
        tx.register(Phase.AFTER_COMMIT,
                () -> observations.add("committed"));
        tx.register(Phase.AFTER_ROLLBACK,
                () -> observations.add("rolledBack"));
        tx.register(Phase.AFTER_COMPLETION,
                () -> observations.add("completed"));

        tx.complete(commit);
        return String.join(">", observations);
    }

    public static void main(String[] args) {
        System.out.println("commit=" + simulate(true));
        System.out.println("rollback=" + simulate(false));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac TransactionPhaseTimelineDemo.java
java TransactionPhaseTimelineDemo
~~~

Stdout mong đợi:

~~~text
commit=before>committed>completed
rollback=rolledBack>completed
~~~

**Ý nghĩa:** khi mô hình chọn commit, đường `BEFORE_COMMIT` và `AFTER_COMMIT` được kích hoạt, không có `AFTER_ROLLBACK`. Khi mô hình chọn rollback, chỉ callback `AFTER_ROLLBACK` và `AFTER_COMPLETION` liên quan. `AFTER_COMPLETION` xuất hiện ở cả hai đường. Đây là mô hình **timeline khái niệm**, không phản ánh mọi nuance về exception, JTA, nested transaction, timeout hoặc thứ tự tất cả synchronization trong Spring.

## 6. Viết Spring event và publisher đúng cách

Ví dụ dưới đây là một cấu trúc Spring ứng dụng điển hình, gồm event bất biến, service phát event và bean listener theo commit. Các đoạn là **Spring classes**; cần dependency của Spring, transaction manager và repository/gateway của dự án, không thể chạy độc lập chỉ bằng `javac`.

### Event bất biến mang ID

Tệp **OrderCreatedEvent.java**:

~~~java
public record OrderCreatedEvent(String orderId) {
}
~~~

Với Java 17, `record` phù hợp để mang metadata bất biến. Event chứa **orderId**, không giữ reference đến entity JPA có lazy relation, EntityManager hoặc transaction-bound object. Vì listener chạy sau thời điểm ghi dữ liệu, dùng ID giúp tách rõ vòng đời của entity và consumer.

### Service phát event trong transaction

Tệp **OrderCommandService.java**:

~~~java
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {
    private final OrderRepository orders;
    private final ApplicationEventPublisher publisher;

    public OrderCommandService(
            OrderRepository orders,
            ApplicationEventPublisher publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public void createOrder(String orderId) {
        orders.insert(orderId);
        publisher.publishEvent(new OrderCreatedEvent(orderId));
    }
}
~~~

`OrderRepository` ở đây là interface ứng dụng với method `insert(String)`; cần có bean repository thực sự kết nối DB. Để callback gắn với transaction, **một bean khác phải gọi `createOrder(...)` qua Spring transactional proxy** hoặc code phải có transaction đang hoạt động theo cơ chế khác. Nếu bạn tự gọi `this.createOrder(...)` từ cùng class trong proxy mode, `@Transactional` có thể bị bỏ qua, khiến `publishEvent` xảy ra **ngoài transaction**.

Để bảo đảm event phản ánh đúng dữ liệu commit, phép `orders.insert` và event publication phải nằm trong cùng transaction do manager hỗ trợ. Đừng xuất bản event trên một worker khác ngay tại đây rồi kỳ vọng worker tự thấy transaction gắn với request thread.

### Listener sau commit

Tệp **OrderNotificationListener.java**:

~~~java
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderNotificationListener {
    private final OrderNotificationGateway notificationGateway;

    public OrderNotificationListener(
            OrderNotificationGateway notificationGateway) {
        this.notificationGateway = notificationGateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OrderCreatedEvent event) {
        notificationGateway.notifyCreated(event.orderId());
    }
}
~~~

`OrderNotificationGateway` là interface/service ứng dụng đảm nhiệm gửi thông báo. Listener này chỉ có pha commit; nếu transaction rollback nó không chạy. **Đây không phải một cơ chế retry/durable queue**: nếu gửi thông báo thất bại trong callback, dữ liệu đơn hàng đã commit vẫn tồn tại, và caller có thể gặp lỗi hoặc không tùy cơ chế gọi/logging. Phải xử lý thông báo theo hợp đồng nghiệp vụ, không cố rollback một transaction đã chốt.

## 7. Không có transaction: fallbackExecution

Thuộc tính của annotation:

~~~java
@TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = false)
public void listener(OrderCreatedEvent event) {
    // ...
}
~~~

`fallbackExecution=false` là mặc định. Nếu code thực thi `publisher.publishEvent(...)` mà **không có transaction tương thích đang hoạt động**, transactional listener thông thường **không được gọi**. Đây là hành vi có chủ đích: không thể đăng ký pha “after commit” khi không có transaction để commit.

Ví dụ ngoài transaction:

~~~java
public void publishOutsideTransaction(String id) {
    publisher.publishEvent(new OrderCreatedEvent(id));
}
~~~

Đây là **mã minh họa** trong một Spring bean có `publisher` đã inject. Nếu không có transaction và listener chỉ có `@TransactionalEventListener` mặc định, sự kiện này bị bỏ qua đối với listener đó, dù một `@EventListener` thông thường khác vẫn có thể nhận event.

### Khi bật fallbackExecution=true

~~~java
@TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true)
public void onCommitOrNoTransaction(OrderCreatedEvent event) {
    // ...
}
~~~

Nếu transaction tồn tại, listener vẫn gắn với `AFTER_COMMIT`. Nếu không có transaction, Spring cho phép **xử lý ngay theo đường fallback**, không có sự kiện commit nào để chờ. Vì thế tên `onCommitOrNoTransaction` thể hiện hợp đồng chính xác hơn so với tên `afterCommit`.

**Không dùng `fallbackExecution=true` để chữa “@Transactional bị vô hiệu” mà chưa điều tra.** Nó có thể làm listener chạy ngoài transaction và bỏ qua quy tắc “chỉ gửi khi dữ liệu đã commit”. Khi nghi ngờ code self-invocation hoặc transaction manager cấu hình sai, cần sửa nguyên nhân trước.

### Khi nào bật fallback hợp lý?

Một event mang tính thông báo có thể xuất hiện ở cả code transactional lẫn nontransactional, và việc xử lý ngay ngoài transaction được sản phẩm cho phép. Ngược lại, nếu listener gửi message về bản ghi cần tồn tại chắc chắn, thường **giữ mặc định false** và bảo đảm publish trong transaction. Việc listener không được gọi trong trường hợp no-tx nên có test và metric phù hợp, thay vì tự động bật fallback.

## 8. Sau commit: tài nguyên cũ có thể còn tồn tại

Đây là điểm rất dễ hiểu sai khi học `@TransactionalEventListener`.

Ở pha `AFTER_COMMIT`, transaction chính đã được commit. Tuy nhiên, Spring transaction synchronization có thể còn giữ connection/session hoặc resource liên kết thread cho đến khi hoàn tất dọn dẹp. Theo Javadoc Spring, nếu data access code chạy ở thời điểm này, nó **có thể trông như đang tham gia transaction cũ**, nhưng **không còn lần commit tiếp theo** cho transaction đó.

Vì thế code dạng:

~~~java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCommitted(OrderCreatedEvent event) {
    auditRepository.save(new AuditRecord(event.orderId()));
}
~~~

là **mã minh họa có rủi ro**. `AuditRecord`, `auditRepository` được dự án định nghĩa. Việc gọi `save` sau commit **không bảo đảm record audit mới được commit vào DB** nếu nó tham gia vào resource của transaction cũ. Đây là lý do Spring khuyến nghị dùng **`PROPAGATION_REQUIRES_NEW`** cho những thao tác transactional phát sinh ở callback sau commit/completion.

### Cách an toàn hơn: service riêng mở transaction mới

Tệp **AuditWriteService.java**:

~~~java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditWriteService {
    private final AuditRepository auditRepository;

    public AuditWriteService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeCommittedOrderAudit(String orderId) {
        auditRepository.insertCommittedOrder(orderId);
    }
}
~~~

`AuditRepository` là interface ứng dụng với `insertCommittedOrder(String)`. Listener inject service khác:

~~~java
@Component
public class OrderAuditListener {
    private final AuditWriteService auditWriteService;

    public OrderAuditListener(AuditWriteService auditWriteService) {
        this.auditWriteService = auditWriteService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OrderCreatedEvent event) {
        auditWriteService.writeCommittedOrderAudit(event.orderId());
    }
}
~~~

Đây là **đoạn Spring cần imports `Component`, `TransactionalEventListener` và `TransactionPhase`** như phần trước. Vì lời gọi đi qua bean `AuditWriteService` khác, `@Transactional(REQUIRES_NEW)` có cơ hội được Spring proxy intercept để mở một transaction độc lập. Nếu bạn đặt `@Transactional(REQUIRES_NEW)` trên method rồi tự gọi `this.method()` trong cùng bean, proxy mode có thể không tạo transaction mới.

**Ghi nhớ:** REQUIRES_NEW giúp có lượt commit riêng cho ghi audit, nhưng **không tự biến việc gửi thông báo thành thao tác atomic với transaction chính**. Sau khi main transaction commit nhưng trước khi audit transaction commit, tiến trình có thể crash. Nếu yêu cầu “order commit thì luôn có audit/message”, cần thiết kế **outbox** trong cùng transaction gốc thay vì chỉ thêm REQUIRES_NEW.

### Với AFTER_ROLLBACK và AFTER_COMPLETION cũng cần lưu ý

Ở `AFTER_ROLLBACK`/`AFTER_COMPLETION`, resource của transaction cũ cũng có thể còn gắn với thread. Một thao tác **ghi database mới** từ listener ở những pha này nên dùng transaction mới độc lập và test thật. Không giả định chỉ do đã rollback xong là mọi DAO call tự lập transaction mới.

## 9. BEFORE_COMMIT và exception: không nên nhầm với validation service

Có thể dùng listener `BEFORE_COMMIT` cho logic phải xảy ra trong commit sequence. Tuy nhiên callback là một phần gián tiếp của cơ chế transaction synchronization, khiến dòng lỗi và thứ tự khó đọc hơn việc gọi validation trực tiếp trước khi kết thúc service method.

~~~java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
public void verify(OrderCreatedEvent event) {
    if (!businessRuleIsSatisfied(event.orderId())) {
        throw new IllegalStateException("Order rule violation");
    }
}
~~~

Đây là **ví dụ minh họa**. Trong đường synchronization đồng bộ chuẩn, exception runtime tại `beforeCommit` thường có thể làm commit fail/rollback. Nhưng **không dùng nó thay cho kiểm tra bắt buộc ở nơi tạo đơn** nếu nghiệp vụ cần phản hồi rõ ràng, hoặc nếu event publication/ordering có thể thay đổi. Một `BEFORE_COMMIT` listener chạy xong vẫn không chứng minh database đã commit.

**Không gửi side effect không rollback được tại BEFORE_COMMIT**, vì event đã “chuẩn bị” không bằng event đã “được commit”.

## 10. Kết hợp @TransactionalEventListener và @Async

Một listener theo pha transaction **không mặc nhiên bất đồng bộ**. Nếu listener phải gọi network hoặc xử lý lâu và không muốn block đường completion, có thể xét sử dụng `@Async`:

~~~java
@Component
public class AsyncOrderNoticeListener {
    private final NoticeSender sender;

    public AsyncOrderNoticeListener(NoticeSender sender) {
        this.sender = sender;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        sender.send(event.orderId());
    }
}
~~~

Đây là **Spring class minh họa**; cần imports `Component`, `Async`, `TransactionalEventListener`, `TransactionPhase`, đồng thời có bean `NoticeSender`, executor tên `notificationExecutor` và `@EnableAsync`. Khi được cấu hình đúng, Spring đăng ký listener theo transaction ở thread phát event; **đến lúc pha AFTER_COMMIT được kích hoạt**, method target có thể được dispatch qua executor bởi `@Async`.

Tác động:

- **Pha transaction** quyết định **khi nào listener đủ điều kiện chạy**.
- **Executor** quyết định **thread nào thực thi body và khi nào body thật sự hoàn tất**.
- Thread worker **không tự thừa hưởng** transaction/thread-local của publisher.
- Nếu queue bị từ chối, `TaskRejectedException` có thể xảy ra tại thời điểm dispatch; logic xử lý failure và observability phải được thiết kế.
- Nếu executor dùng `CallerRunsPolicy` khi bão hòa, body có thể chạy inline trên thread gọi mặc dù có `@Async`, nên delay của đường commit vẫn có thể tăng.
- Vì job ở queue JVM, task đã được submit vẫn có thể mất khi process chết.

### Phân biệt với ApplicationEventMulticaster có executor

Trong các phiên bản Spring mới, transactional listener có yêu cầu đăng ký transaction synchronization trong context của event publication; `TransactionalApplicationListener` không cho phép bộ multicaster tùy ý dịch chuyển chính phần đăng ký synchronization sang thread khác. Có thể làm **target listener** xử lý async theo cơ chế phù hợp (ví dụ `@Async`), nhưng đừng chỉ cấu hình `SimpleApplicationEventMulticaster.setTaskExecutor(...)` rồi mặc định transactional listener sẽ hoạt động/nhận đúng transaction ở worker.

Điểm kỹ thuật cần kiểm tra ở integration test là: **event đã được đăng ký với transaction nào, trên thread nào** và **body listener thực thi sau outcome nào**. Trong Spring 6.1+, `TransactionalApplicationListener.supportsAsyncExecution()` thể hiện khác biệt này: synchronization listener phải xử lý theo context nguồn, còn target listener có thể có đường thực thi bất đồng bộ riêng.

### Ví dụ cấu hình executor

~~~java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class NotificationAsyncConfig {
    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("notice-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Đây là **Spring configuration class**, không phải chương trình `javac` độc lập. Các thông số minh họa, phải sizing theo tải; với `AbortPolicy`, cần xử lý việc không nhận task khi pool đầy hoặc shutdown. `@EnableAsync` là điều kiện của phương thức `@Async`; chỉ thêm annotation vào một bean không được Spring quản lý không kích hoạt proxy.

## 11. Khi nào async listener làm sai kỳ vọng giao dịch?

### Đọc ThreadLocal hoặc transaction của caller

Trong Spring dùng `PlatformTransactionManager` truyền thống, transaction được liên kết với thread. Khi `@Async` chuyển body sang worker, thread worker không tự mang theo transaction context, MDC hoặc SecurityContext của publisher. Dùng `TransactionSynchronizationManager.isActualTransactionActive()` trong body async để suy ra “vẫn đang ở giao dịch gốc” là một giả định sai. Ngay cả ở callback đồng bộ sau commit, tài nguyên transaction có thể còn khả dụng trong một thời gian nhưng không còn commit mới.

**Giải pháp:** truyền ID, correlation ID và dữ liệu tối thiểu cần thiết trong payload bất biến; xác định transaction riêng trong service của listener khi cần ghi DB; dùng context propagation có chủ đích cho logging/security. Không truyền EntityManager, connection, JPA managed entity hoặc transaction status qua event.

### Chạy ngoài transaction vì publish từ worker khác

~~~java
@Async
public void publishLater(OrderCreatedEvent event) {
    publisher.publishEvent(event);
}
~~~

Đây là đoạn minh họa trong bean có `publisher`. Nếu transaction chính đang ở thread A còn `publishLater` chạy trên thread B, event thường được phát **không có transaction bound trên B**. `@TransactionalEventListener` mặc định có thể bỏ qua event ấy. Nên publish event **ở thread đang có transaction**, rồi để listener chọn giai đoạn và có thể thực thi body async **sau khi đến pha**.

### Lỗi listener sau commit không đảo ngược commit gốc

Nếu giao dịch chính đã commit, exception xảy ra trong `AFTER_COMMIT` không thể làm nó “uncommit”. Hành vi exception có lan tới caller hay chỉ bị log còn phụ thuộc giai đoạn synchronization, executor và cách Spring gọi listener. Đừng xây dựng hợp đồng “request lỗi tức transaction đã rollback” nếu có listener post-commit đang ném exception.

Nếu side effect cần nhiều lần retry, cần mã job và trạng thái rõ ràng. Nếu cần business consistency, dùng thiết kế dựa trên transaction/outbox hoặc saga/compensation, không chờ callback sau commit để cố rollback dữ liệu đã chốt.

## 12. Transaction reactive trong Spring 6.1+

Đối với transaction truyền thống quản lý bằng `PlatformTransactionManager`, Spring theo dõi state bằng thread-bound context. Với `ReactiveTransactionManager`, transaction context nằm trong **Reactor Context**, không phải ThreadLocal.

Từ Spring Framework **6.1**, `@TransactionalEventListener` có thể làm việc với cả hai mô hình, nhưng với **reactive transaction**, sự kiện phải mang context cần thiết trong **event source**; Spring giới thiệu `TransactionalEventPublisher` để hỗ trợ publication đúng cách trong reactive pipeline.

Điểm cần nhớ:

1. **Đừng** gọi `ApplicationEventPublisher.publishEvent(...)` tùy tiện trong một Reactor operator rồi mặc định thread hiện tại mang transaction reactive.
2. Xem tài liệu `TransactionalEventPublisher` của đúng phiên bản Spring và dùng đường publish gắn Reactor transaction context.
3. Không copy nguyên ví dụ servlet/JPA/`@Transactional` thread-bound sang R2DBC mà bỏ qua khác biệt context.
4. Khi xử lý async/reactive, đánh giá executor/scheduler, error propagation và backpressure riêng; transaction event không tự biến mọi side effect thành non-blocking.

Đây là phần nhận diện khác biệt, không phải hướng dẫn đầy đủ của Reactor/R2DBC. Việc bật `fallbackExecution=true` không giải quyết sai transaction context: nó chỉ cho phép listener chạy khi không tìm thấy transaction, đồng thời phá mất yêu cầu “chỉ sau commit” nếu dùng không đúng.

## 13. Nhiều transaction và lời gọi lồng nhau

### REQUIRES_NEW trong producer

Giả sử transaction ngoài A đang chạy; một service khác được gọi qua Spring proxy với `@Transactional(propagation=REQUIRES_NEW)` tạo transaction B, rồi **publish event trong B**. Event được gắn với transaction B; listener `AFTER_COMMIT` của event ấy chạy sau khi **B** commit, không đợi A commit. Sau đó A vẫn có thể rollback.

Điều này không trái với contract: listener ràng buộc theo **transaction thực tế đang hoạt động lúc publish**, không theo toàn bộ yêu cầu HTTP hoặc “transaction lớn nhất theo trực giác”. Vì thế nếu nghiệp vụ cần kết quả của transaction ngoài, event phải được publish ở đúng boundary hoặc lưu trong outbox của transaction phù hợp.

### NESTED/savepoint

Một thao tác `NESTED` có thể dùng savepoint trong cùng physical transaction (tùy manager/driver), không phải một physical commit độc lập như `REQUIRES_NEW`. Không mặc định `@TransactionalEventListener(AFTER_COMMIT)` chạy khi chỉ release một savepoint nested. Cần test kỹ điều kiện rollback-to-savepoint và sự kiện đã đăng ký: một event đã đăng ký tại synchronization của physical transaction có thể không tương ứng với outcome bạn muốn ở cấp nested step.

### Transaction rollback-only

Nếu transaction đã bị đánh dấu rollback-only, method `@Transactional` vẫn có thể chạy đến cuối và `publishEvent` vẫn đã đăng ký listener. Khi transaction manager kết thúc bằng rollback, `AFTER_COMMIT` không chạy; listener `AFTER_ROLLBACK` chạy theo phase thích hợp. Đừng suy từ “method return không ném exception ngay” sang “commit chắc chắn thành công”.

## 14. @Order, condition và lọc event

`@TransactionalEventListener` kế thừa nhiều khả năng event listener thông thường:

### Sắp xếp callback

~~~java
@Order(10)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void notifyAfterCommit(OrderCreatedEvent event) {
    // ...
}
~~~

Đây là mã minh họa, cần import `org.springframework.core.annotation.Order`. `@Order` có thể sắp xếp mức ưu tiên giữa những listener/callback liên quan, nhưng **không** tạo một thứ tự delivery toàn cục giữa các JVM, executor, message broker hay những listener async. Không dựa vào ordering để bảo đảm tác vụ có side effect thực thi đúng một lần.

### Điều kiện bằng SpEL

~~~java
@TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        condition = "#event.orderId() != null")
public void onOrderCreated(OrderCreatedEvent event) {
    // ...
}
~~~

Đây là ví dụ Spring SpEL minh họa ý định chỉ xử lý event có ID. Tên tham số trong SpEL phụ thuộc metadata parameter name và cấu hình Spring; khi cần cấu hình portable, hãy dùng hình thức truy cập đối số được Spring hỗ trợ và test. Một lựa chọn dễ bảo trì hơn là kiểm tra dữ liệu ngay trong listener hoặc xây dựng event constructor đảm bảo non-null.

### Sự kiện cùng kiểu nhưng nhiều pha

Một event có thể được nhiều listener khác nhau đăng ký với `BEFORE_COMMIT`, `AFTER_COMMIT`, `AFTER_ROLLBACK`, `AFTER_COMPLETION`. Mỗi listener chạy theo pha của nó nếu transaction phù hợp. Đừng kết luận một event sẽ chỉ kích hoạt đúng một method trong toàn ứng dụng.

## 15. Kiểm thử transaction outcome đúng cách

Một test chỉ gọi service và kiểm tra log có thể bỏ sót transaction thật. Nên test trực tiếp những trường hợp:

| Kịch bản | Kỳ vọng |
| --- | --- |
| Publish trong transaction rồi commit | `AFTER_COMMIT` chạy, `AFTER_ROLLBACK` không chạy |
| Publish trong transaction rồi rollback | `AFTER_ROLLBACK` chạy, `AFTER_COMMIT` không chạy |
| Commit hoặc rollback | `AFTER_COMPLETION` chạy ở cả hai đường |
| Publish ngoài transaction, fallback mặc định | Transactional listener không xử lý |
| Publish ngoài transaction, `fallbackExecution=true` | Listener được xử lý theo fallback, không có transaction commit thực |
| Publish sau khi transaction đã kết thúc | Không gắn vào transaction cũ; cần xem no-tx/fallback |
| Listener thực hiện ghi DB sau commit | Ghi qua service `REQUIRES_NEW` tồn tại sau commit |
| Listener có `@Async` | Xác nhận body chạy sau outcome và có lối quan sát completion |
| Main transaction dùng `REQUIRES_NEW` ở nơi phát event | Callback gắn với transaction trong cùng nơi phát event |
| Transaction rollback-only | `AFTER_COMMIT` không chạy |
| Event được publish trong worker thread khác | Nếu không có transaction bound trên worker, listener mặc định bị bỏ qua |
| Executor từ chối task async | Xử lý rejection rõ ràng, không nhầm với rollback của transaction chính |

### Dùng TransactionTemplate để kiểm soát commit/rollback trong integration test

Một test có Spring context thật và `PlatformTransactionManager` phù hợp có thể điều khiển transaction rõ ràng:

~~~java
import org.springframework.transaction.support.TransactionTemplate;

// Trong một test method với variables đã inject từ Spring:
TransactionTemplate template =
        new TransactionTemplate(transactionManager);

template.executeWithoutResult(status -> {
    publisher.publishEvent(new OrderCreatedEvent("commit-1"));
});
// TransactionTemplate hoàn tất transaction trước khi dòng sau chạy.
// Assert listener AFTER_COMMIT theo cơ chế sync.

template.executeWithoutResult(status -> {
    publisher.publishEvent(new OrderCreatedEvent("rollback-1"));
    status.setRollbackOnly();
});
// Assert listener AFTER_ROLLBACK, không có AFTER_COMMIT.
~~~

Đây là **test fragment**, cần `transactionManager`, `publisher`, listener/recorder và assertion framework. Chỉ dùng khi hai lời gọi tạo **transaction mới thích hợp**: nếu test method đang có một transaction bên ngoài, `TransactionTemplate` với propagation mặc định có thể tham gia transaction đó, và “commit” chỉ xảy ra ở boundary ngoài. Trong Spring Test với `@Transactional` trên test method, transaction thường rollback ở cuối test; do đó **đừng để test đang được bọc bởi transaction làm sai kỳ vọng after commit**.

Tốt hơn, có thể dùng test không annotated `@Transactional`, một transaction manager thật, và các repository đã cấu hình. Nếu test cần thao tác sau commit, đợi callback completion (nếu listener async) bằng latch hoặc Future/recorder thread-safe, không dùng `Thread.sleep(500)` tùy ý.

### Dùng latch cho listener async

Trong test integration, event listener async có thể ghi nhận:

~~~java
CountDownLatch received = new CountDownLatch(1);

// Trong listener callback khi chạy thành công:
received.countDown();

// Trong test (sau lúc transaction đã commit):
assertTrue(received.await(3, TimeUnit.SECONDS));
~~~

Đây là **các fragment minh họa ở hai vị trí khác nhau**, không phải một chương trình hoàn chỉnh. Thay đổi được bảo vệ bởi `CountDownLatch` giúp xác nhận listener đã vào điểm kiểm tra, không giả định timing ngẫu nhiên của pool. Nếu cần xác minh đã **gửi email thành công**, latch phải được thả sau khi gateway/mock được gọi và trả về thành công, không phải ngay khi listener vừa bắt đầu.

## 16. Khi không được phép mất sự kiện: transactional outbox

`@TransactionalEventListener(AFTER_COMMIT)` chỉ đảm bảo **điểm gọi listener theo kết quả transaction trong tiến trình đang chạy**, không đảm bảo sự kiện được lưu bền sau khi transaction commit. Có một **khoảng trống thất bại**:

~~~text
TX chính:
  INSERT order
  COMMIT thành công
             |
             | JVM crash trước khi listener gửi / enqueue job
             v
  order đã tồn tại nhưng notification không được gửi
~~~

Tương tự, listener đã được gọi nhưng executor queue đầy, shutdown hoặc worker lỗi trước khi gửi message. `@Async` chỉ đổi thread, không giải quyết độ bền.

**Transactional outbox** xử lý bằng cách ghi dữ liệu nghiệp vụ và bản ghi outbox trong **cùng transaction**:

~~~text
BEGIN TRANSACTION
  INSERT orders (...)
  INSERT outbox (event_id, event_type, payload, status=NEW)
COMMIT
        |
        v
Outbox worker / CDC
        |
        +-- publish message / invoke downstream
        +-- retry với backoff nếu lỗi
        +-- ghi trạng thái và kiểm soát duplicate
~~~

Nếu transaction rollback, cả đơn và outbox record đều rollback. Nếu commit, outbox row tồn tại bền vững để một worker khác hoặc tiến trình phục hồi gửi sau restart.

### Ví dụ ý tưởng producer

~~~java
@Transactional
public void createOrder(CreateOrderRequest request) {
    Order order = orderRepository.save(new Order(request));
    outboxRepository.insert(new OutboxEvent(
            UUID.randomUUID().toString(),
            "OrderCreated",
            order.getId().toString()));
}
~~~

Đây là **mã minh họa cần class/repository của ứng dụng**, không phải Spring file runnable độc lập. Một worker độc lập quét/publish những record NEW và cập nhật trạng thái có chiến lược retry. Listener `AFTER_COMMIT` có thể dùng như **tín hiệu đánh thức worker sớm**, nhưng outbox row mới là **nguồn sự thật**; nếu tín hiệu bị mất, worker định kỳ vẫn quét record chưa gửi.

### At-least-once và idempotency

Ngay cả với outbox, vẫn có thể xảy ra tình huống publisher gửi message thành công rồi crash **trước khi đánh dấu outbox SENT**. Sau restart, message được gửi lại. Vì thế giải pháp thực tế thường là **at-least-once + idempotent consumer**, không tự hứa exactly-once end-to-end. Dùng event ID làm idempotency key, trạng thái đã xử lý ở receiver hoặc transactional inbox khi cần.

## 17. Khi nào chọn phase nào?

| Yêu cầu thực tế | Pha/giải pháp phù hợp | Vì sao |
| --- | --- | --- |
| Chỉ xử lý sự kiện khi transaction đã commit | `AFTER_COMMIT` mặc định | Bỏ qua đường rollback |
| Thực hiện bước chuẩn bị/kiểm tra trong sequence commit | `BEFORE_COMMIT` nếu phù hợp | Callback còn trước outcome commit |
| Quan sát việc rollback | `AFTER_ROLLBACK` | Chỉ chạy khi transaction bị rollback |
| Dọn state/ghi metric sau bất kỳ kết quả | `AFTER_COMPLETION` | Cả commit và rollback |
| Ghi audit DB sau khi commit | `AFTER_COMMIT` + service `REQUIRES_NEW` | Có transaction mới để commit ghi audit |
| Gửi email best-effort sau tạo đơn | `AFTER_COMMIT`, có thể `@Async` | Tránh gửi trước commit nhưng vẫn có rủi ro mất |
| Bảo đảm event không mất qua crash | Transactional outbox / durable job store | Event được lưu trong transaction chính |
| Xử lý event cả ngoài transaction | Xem xét `fallbackExecution=true` | Fallback có semantics không chờ commit |
| Reactive transaction | `TransactionalEventPublisher` theo Spring 6.1+ | Context nằm trong Reactor Context |
| Một event từ inner `REQUIRES_NEW` | Gắn listener vào transaction inner | Outcome inner khác outcome outer |
| Event không được listener nhận do no-tx | Sửa transaction/publish context trước | Tránh “chữa” bằng bật fallback sai ý nghĩa |

**Mẹo ra quyết định:** trả lời ba câu hỏi riêng: (1) Sự kiện **có cần chờ commit thành công?** (2) Listener **cần thread/transaction riêng không?** (3) Tác vụ **có bắt buộc hoàn thành qua crash/restart không?** `@TransactionalEventListener` chủ yếu trả lời câu thứ nhất; `@Async`/`REQUIRES_NEW` liên quan câu thứ hai; outbox/queue bền vững giải quyết câu thứ ba.

## 18. Các lỗi thường gặp

1. **Dùng `@EventListener` cho việc chỉ được gửi sau commit** rồi giả định listener tự đợi transaction kết thúc.
2. **Nhầm `AFTER_COMMIT` với “có transaction mới”.** Tài nguyên cũ có thể còn gắn thread nhưng không còn commit tiếp.
3. **Ghi DB trực tiếp trong `AFTER_COMMIT`** mà không có `REQUIRES_NEW` và tưởng thay đổi chắc chắn được commit.
4. **Đưa cả entity JPA vào event** rồi gọi lazy relation sau commit; tài nguyên và trạng thái entity có thể không còn như kỳ vọng.
5. **Publish event ngoài transaction** rồi nghĩ transactional listener mặc định luôn nhận.
6. **Bật `fallbackExecution=true` để tránh event bị bỏ** mà không xem hậu quả chạy trước/không có commit.
7. **Self-invocation bỏ qua `@Transactional` proxy**, khiến publisher không có transaction thực sự.
8. **Publish event từ `@Async` worker** rồi nghĩ worker kế thừa transaction thread của request.
9. **Gắn `@Async` lên listener và nghĩ nó tự tạo hàng đợi durable.** Executor queue trong RAM không chịu được crash.
10. **Dùng `SimpleApplicationEventMulticaster.setTaskExecutor` tùy tiện** và chuyển việc đăng ký synchronization ra khỏi thread transaction.
11. **Coi `BEFORE_COMMIT` như bằng chứng commit đã thành công.** Có thể commit lỗi hoặc rollback sau callback.
12. **Coi `AFTER_COMPLETION` luôn đồng nghĩa commit.** Nó cũng dùng cho rollback.
13. **Dùng `AFTER_ROLLBACK` nhưng listener lại mở default transaction gắn resource cũ**, khiến audit ghi không được commit.
14. **Giả định exception của after-commit listener có thể rollback transaction chính.** Outcome đã được chốt.
15. **Không có đường ghi nhận lỗi listener**, khiến notification thất bại nhưng trạng thái nghiệp vụ vẫn success mà không có reconciliation.
16. **Dùng `@Order` để đảm bảo thứ tự tuyệt đối giữa nhiều thread/JVM.** Annotation chỉ giúp ordering trong phạm vi Spring callback liên quan.
17. **Cho rằng event từ transaction inner `REQUIRES_NEW` chờ transaction outer.** Event gắn transaction thực tại thời điểm publish.
18. **Bỏ qua reactive transaction context**, dù R2DBC không dựa trên ThreadLocal như JDBC transaction manager.
19. **Dùng `@Transactional` trên test method rồi assert AFTER_COMMIT trước lúc test transaction kết thúc**; test thường rollback theo mặc định.
20. **Dùng `Thread.sleep` trong test để đoán listener async đã chạy.** Dùng latch/recorder và deadline.
21. **Cho rằng outbox loại bỏ mọi duplicate.** Producer có thể gửi lặp nếu crash sau publish trước khi ack; consumer phải idempotent.
22. **Gửi email ngay trong `BEFORE_COMMIT`** dù email không thể rollback theo DB transaction.
23. **Không đo latency callback đồng bộ**, khiến request chậm dù dữ liệu đã commit.
24. **Đọc `TransactionSynchronizationManager` ở after-completion rồi kết luận chắc chắn đang có transaction còn commit được.** Bound resource không đồng nghĩa một commit tương lai.
25. **Dùng event publisher như message broker liên JVM.** `ApplicationEventPublisher` mặc định chỉ phát event trong Spring ApplicationContext/JVM, không có durable replication.

## 19. Câu hỏi tự kiểm tra

1. `@TransactionalEventListener` mặc định dùng pha nào? Có cần khai báo `phase` mới chờ commit không?
2. Khác biệt lớn nhất giữa `@EventListener` và `@TransactionalEventListener` khi transaction rollback là gì?
3. Nếu một event được publish mà không có transaction, listener mặc định có xử lý không? `fallbackExecution=true` thay đổi điều gì?
4. Listener `BEFORE_COMMIT` chạy thành công có bảo đảm DB commit thành công không?
5. Vì sao `AFTER_COMPLETION` không thể tự suy ra transaction commit?
6. Gọi `auditRepository.save(...)` trong `AFTER_COMMIT` mà không mở transaction mới có nguy cơ gì?
7. Vì sao nên gọi một **bean khác** có `@Transactional(REQUIRES_NEW)` để ghi audit?
8. Nếu một worker `@Async` publish event trong khi transaction JDBC gốc nằm trên request thread, listener gắn với transaction nào?
9. `@Async` trên transactional listener có nghĩa transaction outcome được chờ ở thread nào và body được gửi tới thread nào?
10. Nếu transaction chính đã commit nhưng async listener lỗi, có thể rollback commit cũ không?
11. Nếu inner method được gọi qua `REQUIRES_NEW` publish event rồi commit, outer transaction sau đó rollback, listener của event inner sẽ làm gì?
12. Trong Spring 6.1+, reactive transaction sử dụng context gì và tại sao cần `TransactionalEventPublisher`?
13. Vì sao `@TransactionalEventListener` không thay thế transactional outbox khi message không được phép mất?
14. Nếu outbox worker crash sau khi gửi message nhưng trước lúc đánh dấu đã gửi, hệ thống phải xử lý duplicate thế nào?
15. Tại sao không nên để `@Transactional` trên test method rồi mong assertion after-commit chạy ngay?
16. Làm sao test việc listener không chạy trên rollback mà không dùng chờ thời gian tùy ý?
17. Phân biệt trạng thái “transaction đã commit” và “notification đã gửi thành công” bằng những nguồn dữ liệu nào?
18. Trong tình huống publisher chạy ngoài transaction một cách hợp lệ, tiêu chí nào cho phép bật `fallbackExecution` mà không phá nghiệp vụ?

## 20. Tài liệu tham khảo chính thức

- [Spring Framework Reference — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Spring Framework — TransactionalEventListener Javadoc](https://docs.spring.io/spring-framework/docs/6.1.12/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html)
- [Spring Framework — TransactionPhase Javadoc](https://docs.spring.io/spring-framework/docs/6.1.12/javadoc-api/org/springframework/transaction/event/TransactionPhase.html)
- [Spring Framework — TransactionSynchronization Javadoc](https://docs.spring.io/spring-framework/docs/6.1.12/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html)
- [Spring Framework — TransactionTemplate Javadoc](https://docs.spring.io/spring-framework/docs/6.1.12/javadoc-api/org/springframework/transaction/support/TransactionTemplate.html)
- [Spring Framework — TransactionalApplicationListener Javadoc](https://docs.spring.io/spring-framework/docs/6.2.6/javadoc-api/org/springframework/transaction/event/TransactionalApplicationListener.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring Framework — TransactionalEventPublisher (Reactive)](https://docs.spring.io/spring-framework/docs/6.1.12/javadoc-api/org/springframework/transaction/reactive/TransactionalEventPublisher.html)

**Ghi nhớ:** `@TransactionalEventListener` ràng buộc **thời điểm gọi listener với kết quả transaction**: mặc định `AFTER_COMMIT`, ngoài ra có `BEFORE_COMMIT`, `AFTER_ROLLBACK`, `AFTER_COMPLETION`. Nó không tự tạo transaction mới hoặc hàng đợi bền vững. Khi cần ghi DB sau commit, hãy chủ động mở **`REQUIRES_NEW`**; khi sự kiện không được phép mất, ghi **outbox cùng transaction gốc**; khi kết hợp `@Async`, kiểm tra thread/context, lỗi submit và lifecycle của executor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactions-events-007">Sự kiện nội bộ bất đồng bộ không thay thế hàng đợi bền vững</a>

<details>
<summary>Xem chi tiết</summary>

# Sự kiện nội bộ bất đồng bộ không thay thế hàng đợi bền vững

## 1. Vấn đề cần phân biệt: xử lý lệch thời điểm và bảo đảm không mất việc

Trong Spring Framework, `ApplicationEventPublisher.publishEvent(...)` cho phép một bean phát sự kiện để các listener bên trong `ApplicationContext` phản ứng. Có thể dùng `@EventListener` cùng `@Async` hoặc cấu hình `ApplicationEventMulticaster` để chuyển xử lý sang thread pool. Cách làm này phù hợp với thông báo nội bộ, giảm coupling và đưa công việc không cấp thiết ra khỏi request thread.

Nhưng **“đã giao việc sang thread khác” không đồng nghĩa “đã lưu việc vào hàng đợi bền vững”**. Một `ThreadPoolTaskExecutor` thường giữ các `Runnable` đang chờ trong bộ nhớ của JVM. Khi JVM chết đột ngột, container bị kill, máy hết điện hoặc tiến trình được thay thế mà chưa kịp xử lý, **những task chỉ nằm trong bộ nhớ không được bảo đảm tự phục hồi**. Nếu listener ném exception, Spring event infrastructure thông thường cũng không tự cung cấp cơ chế lưu trạng thái, retry sau restart, dead-letter hay audit bền vững cho từng sự kiện.

Tương tự, `@TransactionalEventListener(phase = AFTER_COMMIT)` giúp xử lý listener *sau khi transaction của publisher commit thành công*, nhưng **không tự biến event thành bản ghi bền vững**. Có khoảng rủi ro: dữ liệu nghiệp vụ đã commit, trong khi JVM chết trước khi xử lý listener hoặc chuyển được tác vụ tới nơi lưu an toàn.

Đặt câu hỏi theo hai lớp:

1. **Asynchronous execution:** caller có cần chờ listener thực thi xong không? Có muốn giảm thời gian request và điều phối các module trong cùng ứng dụng không?
2. **Durable delivery:** nếu tiến trình chết ở bất kỳ thời điểm nào, có thể tìm lại những event chưa xử lý, retry có kiểm soát, tránh xử lý trùng và quan sát tiến độ không?

`@Async` giải quyết chủ yếu lớp thứ nhất. Lớp thứ hai cần **thiết kế lưu bền và giao nhận**: transactional outbox, một event publication registry bền vững hoặc broker/queue được cấu hình độ bền phù hợp, cùng consumer idempotent.

## 2. Bốn khái niệm không được đánh đồng

| Khái niệm | Có nghĩa là gì? | Không tự bảo đảm |
| --- | --- | --- |
| Spring application event | Gửi thông báo tới listener trong `ApplicationContext` | Lưu bền, phát lại sau restart, giao giữa nhiều process |
| `@Async` hoặc executor bất đồng bộ | Giao callback cho executor để xử lý theo lịch của nó | Task đã hoàn tất, dữ liệu đã commit, task tồn tại qua crash |
| `@TransactionalEventListener(AFTER_COMMIT)` | Gắn callback với giai đoạn transaction commit thành công | Một record event được lưu để recovery sau sự cố |
| Durable broker/queue/event log | Lưu thông điệp ngoài bộ nhớ của ứng dụng theo hợp đồng hạ tầng | “Exactly once” ở mức side effect nghiệp vụ, hoặc tự đồng bộ atomically với DB nguồn |
| Transactional outbox / publication registry | Lưu ý định phát sự kiện trong một kho bền vững, thường cùng giao dịch dữ liệu nghiệp vụ | Bên nhận không bao giờ thấy bản phát trùng; phải có relay/retry/idempotency |

**Độ bền không phải một thuộc tính nhị phân chỉ nhìn tên sản phẩm.** Một broker có thể lưu message bền vững hay không tùy cấu hình message persistence, replication/quorum, producer acknowledgement, consumer acknowledgement, retention, TTL và chính sách vận hành. Hàng đợi bền vững không mặc nhiên bảo đảm mọi consumer side effect được thực hiện đúng một lần.

## 3. Mặc định Spring events là đồng bộ, @Async mới thay đổi lịch thực thi

Trong Spring Framework thông thường, `ApplicationEventMulticaster` mặc định sử dụng cách phát sự kiện đồng bộ. Khi publisher gọi:

```java
events.publishEvent(new OrderPlaced(orderId));
```

listener có `@EventListener` thường được gọi ngay trên **thread của publisher** trước khi `publishEvent` trả về. Nếu publisher đang ở trong một transaction imperative, listener đồng bộ có thể tham gia chính thread và transaction context ấy theo cấu hình tương ứng. Điều này có thể hữu ích với một hành vi nội bộ phải hoàn tất cùng đường xử lý nhưng cũng có thể kéo dài thời gian transaction/request.

Nếu listener được đánh dấu `@Async("eventPool")` và bật `@EnableAsync`, lời gọi listener phù hợp đi qua Spring async advice và được gửi vào executor được chọn. Caller có thể tiếp tục trước khi listener thực thi xong.

Hai tình huống cần phân biệt:

```text
A. @EventListener bình thường (mặc định)

publisher --publishEvent--> listener chạy trên publisher thread
                                |
                                +-- xong/lỗi --> publishEvent trở về

B. @Async @EventListener (có @EnableAsync, proxy, executor phù hợp)

publisher --publishEvent--> async advice --> submit vào executor
           |                                      |
           +-- có thể tiếp tục                    +--> worker thực thi listener sau đó
                                                       (task có thể đang chờ trong RAM)
```

Lưu ý: `publishEvent(...)` được định nghĩa là thao tác **bàn giao thông báo cho event multicaster**, không phải biên nhận “toàn bộ listener đã xử lý và commit side effect”. Thực thi cụ thể đồng bộ/bất đồng bộ còn phụ thuộc multicaster, listener và framework configuration.

Spring cho phép cấu hình `SimpleApplicationEventMulticaster.setTaskExecutor(...)` để chạy nhiều listener một cách async; đây là cách khác với việc gắn `@Async` cho một listener cụ thể. Với Spring 6.2.x, các listener tuyên bố không hỗ trợ async execution, chẳng hạn transaction-synchronized listener, vẫn cần xử lý trên thread phát event để đăng ký transaction synchronization đúng. Vì vậy **đừng kết luận rằng chỉ cấu hình một multicaster async là mọi listener đều chạy thread khác**.

## 4. Ví dụ Spring Framework 6.2.9 + Java 17+ chạy thật

Ví dụ minh họa event nội bộ được giao cho một worker của `ThreadPoolTaskExecutor`. Chúng ta dùng `CountDownLatch` để chờ có thời hạn, vì nếu chỉ gọi `publishEvent` rồi lập tức kiểm tra biến, worker có thể chưa bắt đầu. Dữ liệu quan sát được đặt trong một bean `Probe` riêng để tránh nhầm state nội bộ của AOP proxy với state trong target bean.

Cấu trúc Maven:

```text
local-event-demo/
├── pom.xml
└── src/main/java/demo/LocalEventDemo.java
```

File `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>local-event-demo</artifactId>
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
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
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

File `src/main/java/demo/LocalEventDemo.java`:

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class LocalEventDemo {

    @Bean("eventPool")
    public ThreadPoolTaskExecutor eventPool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("event-worker-");
        return pool;
    }

    public record OrderPlaced(long id) {}

    // Bean riêng để kiểm thử, không phải outbox hoặc event store.
    public static class Probe {
        final CountDownLatch done = new CountDownLatch(1);
        volatile long seen;
        volatile String worker;
    }

    @Bean
    public Probe probe() {
        return new Probe();
    }

    @Bean
    public Receiver receiver(Probe probe) {
        return new Receiver(probe);
    }

    public static class Receiver {
        private final Probe probe;

        public Receiver(Probe probe) {
            this.probe = probe;
        }

        @Async("eventPool")
        @EventListener
        public void on(OrderPlaced event) {
            probe.seen = event.id();
            probe.worker = Thread.currentThread().getName();
            probe.done.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(LocalEventDemo.class)) {
            Probe probe = context.getBean(Probe.class);
            String caller = Thread.currentThread().getName();

            context.publishEvent(new OrderPlaced(7L));

            if (!probe.done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Listener timed out");
            }

            System.out.println("order id = " + probe.seen);
            System.out.println("pool worker = "
                    + probe.worker.startsWith("event-worker-"));
            System.out.println("different thread = "
                    + !caller.equals(probe.worker));
        }
    }
}
```

Chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.LocalEventDemo
```

Stdout do chương trình in:

```text
order id = 7
pool worker = true
different thread = true
```

**Giải thích:**

1. `@EnableAsync` bật hạ tầng async proxy. `@Async("eventPool")` chọn executor bean cụ thể cho listener.
2. `context.publishEvent(new OrderPlaced(7L))` giao sự kiện nội bộ cho application context; callback được lên lịch trên `eventPool`.
3. `CountDownLatch` cho phép main chờ tối đa 5 giây để xác nhận callback đã chạy. Nếu không chờ, phép đo kết quả có thể chạy đua với worker.
4. `Receiver` ghi ID và tên thread vào `Probe`; hai kiểm tra xác nhận target listener chạy trên worker của pool thay vì caller.
5. Sau `try`, application context được đóng, executor bean được quản lý theo lifecycle của Spring.

Đã kiểm chứng đường chạy tương đương với Spring 6.2.9/JDK trong JShell: ba dòng kết quả khớp. Ví dụ **không có database, broker, outbox hay cơ chế recovery**, nên chỉ chứng minh event được xử lý trên worker **khi ứng dụng vẫn hoạt động**, tuyệt đối không chứng minh giao nhận bền vững. Nếu đặt một event khác trong queue rồi kill process trước khi worker nhận, queue trong RAM không có hợp đồng tự phát lại sau restart.

## 5. Các cửa sổ lỗi: vì sao callback trong RAM có thể bị mất?

Xét yêu cầu tạo đơn hàng:

```text
HTTP request
    |
    +-- ghi order vào database
    |
    +-- publishEvent(OrderPlaced)
    |
    +-- worker gửi email / cập nhật hệ thống phụ
```

Nếu dùng `@Async @EventListener` thông thường, có các rủi ro khác nhau:

| Thời điểm hỏng | Điều có thể xảy ra | Spring event nội bộ mặc định có tự sửa không? |
| --- | --- | --- |
| Sau khi gửi event nhưng trước khi listener được chạy | Task chỉ còn trong queue RAM; crash làm mất task chưa xử lý | Không có replay bền vững |
| Sau khi listener bắt đầu nhưng trước side effect | Worker mất tiến trình; không có record công việc đang dở | Không có cơ chế retry sau restart mặc định |
| Listener gọi external API thành công nhưng JVM chết trước khi ghi trạng thái hoàn tất | Trạng thái không được lưu; nếu tự retry có thể làm side effect lần hai | Không có idempotency tự động |
| Executor đầy queue và pool không nhận thêm task | Có thể phát sinh rejection ở đường dispatch, tùy cách cấu hình | Không biến rejection thành backlog bền vững |
| Listener ném exception khi chạy async | Lỗi không tự quay lại caller để rollback business transaction | Cần handler, log, retry và kiểm soát nghiệp vụ |
| Deploy/scale down với xử lý dài | Graceful shutdown có thể chờ một phần tác vụ theo cấu hình nhưng bị giới hạn thời gian | Không bảo đảm qua `kill -9`/OOM/power loss |

Không nên suy diễn rằng **mọi restart đều làm mất mọi task**. Nếu shutdown có grace period đủ dài, một số task có thể hoàn tất. Điểm thiết kế là **không có cam kết khôi phục những task chưa hoàn tất khi process bị chấm dứt không kiểm soát**.

Một HTTP response thành công hoặc một lời gọi `publishEvent` đã trả về chỉ chứng minh những bước mà API đó cam kết; **không phải biên nhận của bên nhận** và càng không phải bằng chứng dữ liệu đã được ghi bền vững ở một nơi độc lập.

## 6. Giao dịch: @EventListener không phải @TransactionalEventListener

Khi đang ở một phương thức `@Transactional`:

```java
@Transactional
public void placeOrder(CreateOrder command) {
    Order order = repository.save(new Order(command.customerId()));
    events.publishEvent(new OrderPlaced(order.getId()));
}
```

Đây là **mảnh code tích hợp Spring Data/transaction**, không phải Java tự chạy. Phương thức `placeOrder` phải thật sự được gọi qua Spring transactional proxy và có transaction manager. Nếu listener đồng bộ thông thường, listener có thể chạy **trước khi transaction commit**. Nếu listener gửi email ngay, sau đó transaction rollback, email đã ra ngoài nhưng order không tồn tại: một lỗi tính nhất quán.

Thêm `@Async` không tự chữa điều này. Một listener chạy trên thread khác có thể bắt đầu **trước commit**, không có transaction context của publisher, và khi truy vấn database có thể chưa thấy order theo isolation level của DB.

Spring cung cấp:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCommitted(OrderPlaced event) {
    // Chạy khi transaction publisher commit thành công.
}
```

Đây là **mảnh method trong Spring-managed bean**, cần import `@TransactionalEventListener` và `TransactionPhase`. Phase mặc định của annotation là `AFTER_COMMIT`, nên khai báo tường minh giúp người đọc nhìn rõ ý định.

Các phase khác gồm `BEFORE_COMMIT`, `AFTER_ROLLBACK`, `AFTER_COMPLETION`. Nếu **không có transaction đang chạy**, listener transaction-bound **không được gọi** trừ khi cấu hình `fallbackExecution = true`; khi bật fallback, semantics phụ thuộc tình huống và cần quy định rõ.

Với Spring Framework 6.2.x, listener transaction-bound dùng cơ chế synchronization đăng ký trên thread có transaction. Một `@TransactionalEventListener` không nên bị suy diễn là tự chạy trên worker chỉ vì multicaster toàn cục được cấu hình async. Nếu muốn listener business chạy async sau commit, có thể kết hợp `@Async`/executor đúng cách, nhưng **phase after-commit không biến queue RAM thành durable queue**.

## 7. AFTER_COMMIT sửa thứ tự nhưng không đóng khoảng cách mất sự kiện

So sánh hai bước:

```text
T0   Bắt đầu transaction DB
T1   Ghi order
T2   Đăng ký event / listener AFTER_COMMIT
T3   COMMIT dữ liệu order thành công
T4   Spring bắt đầu gọi callback sau commit / submit async task
T5   Worker thực hiện side effect
```

Nếu tiến trình chết **giữa T3 và T4**, dữ liệu order đã được commit, nhưng callback chưa được giao tới một nơi lưu bền vững. Nếu chết **giữa T4 và T5**, task có thể đã nằm trong executor RAM nhưng chưa hoàn tất. Hai trường hợp đều không có cam kết tự phục hồi chỉ nhờ `@TransactionalEventListener`.

Điều này có nghĩa:

- `AFTER_COMMIT` rất có ích để **không phản ứng với transaction bị rollback** và tránh đọc dữ liệu chưa commit.
- Nó **không lưu một thông điệp vào DB/broker** như một phần của transaction nguồn.
- Nếu callback sau commit ghi dữ liệu mới vào DB, cần hiểu resource/transaction boundary. Trong một số ngữ cảnh thread-bound, resource của transaction cũ có thể còn được gắn ngắn hạn; cần một giao dịch mới rõ ràng, thường `@Transactional(propagation = REQUIRES_NEW)` trên một Spring-managed service/worker phù hợp, thay vì mong các write sau commit tự được commit.
- Nếu event chạy trong worker khác, transaction của publisher **không tự truyền** sang worker. Worker cần transactional boundary riêng nếu nghiệp vụ đòi.

**Đừng hiểu “sau commit” thành “đã ghi vào queue”.** Nó chỉ xác định thời điểm xử lý callback trong lifecycle giao dịch.

## 8. Mẫu bền vững: transactional outbox

Khi một hành động phụ bắt buộc được xử lý sau khi đơn hàng đã tồn tại — chẳng hạn phát thông báo đến kho hàng, thanh toán hoặc hệ thống tích hợp — cần ghi lại **ý định phát sự kiện** ở nơi khôi phục được.

Mẫu phổ biến:

```text
                     MỘT DB TRANSACTION
+----------------------------------------------------+
| UPDATE orders SET status='PLACED' ...              |
| INSERT INTO outbox(event_id, type, payload, ...)  |
| COMMIT                                             |
+----------------------------------------------------+
                       |
                       v
              Outbox rows đã bền vững
                       |
                       v
              Background relay / CDC
                       |
                       v
             Broker lưu message (có ack)
                       |
                       v
             Consumer nhận, xử lý, ack
                       |
                       +--> Dedup by event_id
                       +--> Retry / DLQ / monitoring
```

Điều kiện then chốt: **ghi state nghiệp vụ và outbox record trong cùng một transaction của cùng hệ thống lưu trữ có atomic commit**. Khi transaction rollback, cả order lẫn outbox record rollback; khi commit, cả hai cùng tồn tại bền vững theo cam kết của DB. Relay có thể chạy sau, kể cả sau một restart.

Ví dụ dữ liệu outbox nên có ít nhất: `event_id` duy nhất, `aggregate_type`, `aggregate_id`, `event_type`, `schema_version`, `payload` hoặc reference, `created_at` và trạng thái/attempt metadata khi dùng polling. Nên tạo `event_id` ổn định cho **một ý định nghiệp vụ**, không phát ID mới vô hạn trên mỗi lần retry.

Để mô hình dễ hiểu, đây là **pseudo-SQL cho PostgreSQL**, tên bảng/columns chỉ minh họa:

```sql
BEGIN;

UPDATE orders
SET status = 'PLACED'
WHERE id = 42;

INSERT INTO outbox_events (
    event_id, aggregate_id, event_type, payload, status
) VALUES (
    'c44a7b4e-1fd0-4a4e-85c2-f11fa35a8368',
    42,
    'OrderPlaced',
    '{"orderId":42,"version":1}',
    'PENDING'
);

COMMIT;
```

Hai câu lệnh cùng commit mới tạo tính chất atomic business+outbox. Không dùng riêng hai DB transaction độc lập rồi gọi chúng là outbox nhất quán; thiếu distributed transaction phù hợp thì vẫn có khoảng commit một bên mà bên kia không.

### Relay phải xác nhận rồi mới đánh dấu đã gửi

Một relay polling thường:

1. Claim một nhóm record `PENDING` có khóa/lease, bảo đảm nhiều relay không lấy trùng vô kiểm soát.
2. Publish từng message tới broker với persistence/confirmation phù hợp.
3. **Chỉ sau khi có producer confirm/ack đủ mạnh**, đánh dấu record đã gửi (hoặc chuyển sang trạng thái đã được broker nhận).
4. Nếu publish/confirm thất bại, không đánh dấu thành công; retry với backoff.
5. Thu thập metric backlog, tuổi event lâu nhất, số lần retry và trạng thái lỗi lâu ngày.

Một relay có thể chết **sau khi broker đã nhận message nhưng trước khi đánh dấu outbox SENT**. Sau restart, relay sẽ gửi lại record. Đây là một trong các lý do **at-least-once delivery thường phải đi cùng consumer idempotent**. Không được đánh dấu SENT trước broker confirm, vì nếu broker không nhận thì mất event.

Hàng đợi bền vững không loại bỏ chuyện duplicates; nó cho cơ hội replay an toàn nếu thiết kế đầy đủ.

## 9. Consumer idempotent: chấp nhận có duplicate

Ngay cả broker bền vững cũng có thể redeliver:

```text
Consumer nhận message E
    |
    +--> Ghi DB side effect thành công
    |
    +--> Consumer chết TRƯỚC khi ack broker
    |
    +--> Broker gửi lại E
```

Không có dedup thì hệ thống có thể tính tiền hai lần, gửi email hai lần hoặc cộng tồn kho hai lần. Vì vậy nên thiết kế **idempotency** dựa trên `event_id`/business key theo loại side effect:

- Với DB, dùng bảng `processed_events(consumer_name, event_id)` có **unique constraint**. Trong cùng DB transaction với side effect, insert marker có điều kiện; nếu marker đã tồn tại, coi là đã xử lý và không áp dụng side effect lần nữa.
- Với external API, sử dụng **idempotency key được API hỗ trợ** hoặc một business operation ID ổn định. Chỉ check trong DB của mình **không tự ngăn** external API thực hiện hai lần nếu trạng thái phía mình và phía ngoài không commit atomically.
- Với thông báo/email, cân nhắc dedup ở bước dispatch hoặc nhà cung cấp, và xác định chấp nhận được duplicate đến mức nào.
- Ack broker **sau khi** phần xử lý cần đảm bảo đã bền vững, không ack ngay khi vừa nhận thông điệp.

Ví dụ ý tưởng PostgreSQL cho một consumer (mảnh minh họa, phải chạy trong cùng transaction với side effect):

```sql
BEGIN;

INSERT INTO processed_events (consumer_name, event_id)
VALUES ('inventory', 'c44a7b4e-1fd0-4a4e-85c2-f11fa35a8368')
ON CONFLICT DO NOTHING;

-- Chỉ thực hiện UPDATE stock nếu lần INSERT phía trên
-- thực sự thêm một record mới; nếu conflict thì bỏ qua side effect.

COMMIT;
```

Đoạn này **chưa phải consumer hoàn chỉnh**, bởi ứng dụng phải kiểm tra row count của INSERT và chỉ cập nhật tồn kho trong nhánh insert thành công. Cần bảo đảm transaction marker và transaction side effect atomic, xử lý rollback khi side effect thất bại. Nếu broker/driver hỗ trợ transaction riêng, vẫn phải làm rõ phạm vi atomicity giữa broker và DB.

Kết luận: mục tiêu thực tế thường là **at-least-once + idempotent processing**, không tuyên bố “exactly once” ở cấp ứng dụng chỉ vì broker có một tính năng giao dịch.

## 10. Ví dụ Spring service: ghi đơn hàng và outbox trong một transaction

Trong Spring Data JPA hoặc JDBC, điểm gửi event đáng tin không phải `ApplicationEventPublisher` mà là **ghi outbox record** cùng transaction nghiệp vụ. Mảnh kiến trúc sau dùng lớp/repository giả định của ứng dụng, **không phải file Java chạy độc lập**:

```java
@Service
public class OrderCommandService {
    private final OrderRepository orders;
    private final OutboxRepository outbox;

    public OrderCommandService(
            OrderRepository orders,
            OutboxRepository outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @Transactional
    public long placeOrder(NewOrderCommand command) {
        Order order = orders.save(Order.newFrom(command));

        OutboxEvent event = OutboxEvent.pending(
                UUID.randomUUID(),
                "OrderPlaced",
                order.getId(),
                1,
                "{\"orderId\":" + order.getId() + ",\"version\":1}"
        );

        outbox.save(event);
        return order.getId();
    }
}
```

Các kiểu `OrderRepository`, `OutboxRepository`, `Order`, `OutboxEvent` và `NewOrderCommand` là **domain abstractions cần tự triển khai**; đoạn này chỉ cho thấy **một transactional boundary** chứa cả business data và outbox. Với JPA, ID sinh tự động phải được cấp/flush đúng lúc trước khi dùng trong payload; ở dự án thật nên serialize có schema/version rõ, dùng Jackson hoặc thư viện tương ứng thay vì nối JSON thủ công.

Nếu `orders.save` thành công nhưng `outbox.save` làm transaction fail, **toàn bộ transaction rollback**, không tạo trạng thái “order đã lưu nhưng event không có” khi transaction manager và DB đúng như cấu hình. Caller trả thành công chỉ khi transaction commit đúng; API cũng cần phân biệt commit với việc consumer downstream đã xử lý.

Sau đó có một **outbox relay** độc lập, không đòi handler bên trong request transaction gửi thông điệp tới broker. Có thể dùng job theo lịch, worker riêng hoặc CDC, tùy khối lượng và yêu cầu latency. Nếu muốn đánh thức relay nhanh, một Spring event nội bộ vẫn có thể đóng vai **tín hiệu tối ưu độ trễ**, nhưng **outbox record mới là nguồn sự thật**: mất tín hiệu RAM không được làm mất công việc đã commit.

```text
DB transaction lưu order + outbox
              |
              +-- tín hiệu local event (tùy chọn, có thể mất)
              |
              +-- scheduled relay vẫn quét outbox bền vững
```

Đừng biến tín hiệu local thành điều kiện duy nhất để relay hoạt động.

## 11. Khi nào cần broker, khi nào đủ event registry của ứng dụng?

Không phải mọi hệ thống cần dựng Kafka/RabbitMQ ngay. Sự lựa chọn phụ thuộc ranh giới tiến trình, khối lượng, SLA khôi phục và yêu cầu replay.

| Nhu cầu | Phương án thường phù hợp | Cảnh báo |
| --- | --- | --- |
| Cập nhật UI nội bộ không quan trọng, có thể tính lại | `@EventListener` hoặc `@Async @EventListener` | Chấp nhận mất một notification khi crash |
| Cache invalidation chỉ mang tính tối ưu | Local event có cache TTL/rebuild | Không coi cache event là source of truth |
| Gửi email đơn giản nhưng mất email là sự cố nghiệp vụ | Job record/outbox bền vững và retry | Email gửi thành công nhưng ack thất bại có thể gây duplicate |
| Giao tiếp giữa nhiều process/service | Broker/queue có cấu hình durable | Cần outbox hoặc cơ chế atomic publish phía nguồn |
| Workflow quan trọng trong modular monolith | Event publication registry bền vững hoặc outbox | Kiểm tra lưu state, recovery, idempotency của từng module |
| Cần replay, fan-out, retention dài | Event log/broker và consumer state | Cần schema evolution, offset/ack, quotas và quyền |
| Tác vụ nền có thể mất mà không ảnh hưởng dữ liệu | Executor in-memory là đủ nếu được chấp nhận | Ghi rõ SLA và cách tính lại |

**Spring Modulith Event Publication Registry** là một lựa chọn thuộc hệ sinh thái Spring cho modular monolith: registry có thể lưu bản ghi publication cho listener transaction-bound cùng transaction nguồn và đánh dấu hoàn tất sau khi listener thành công. Các phiên bản/tùy chọn cung cấp API resubmit; cấu hình recovery trên restart và monitor trạng thái phải được kiểm tra theo **phiên bản đang dùng**, không mặc định mọi starter bật auto-republication. Đây là **thành phần bổ sung cần cài và cấu hình**, không có sẵn chỉ bởi Spring Framework core hoặc một annotation `@EventListener`.

Broker bên ngoài cũng không tự giải bài toán dual-write. Ví dụ “DB commit order thành công; gửi Kafka thất bại” vẫn khiến event mất nếu không có outbox, transaction-coordination hoặc cơ chế bù/phục hồi khác. Hãy xem ranh giới transaction của hệ thống nguồn.

## 12. Phân biệt sự kiện nội bộ và thông điệp công khai giữa các service

Một local event có thể mang đối tượng Java trực tiếp:

```java
public record OrderPlaced(long orderId) {}
```

Trong cùng process, event này không cần serializer hay schema registry. Khi chuyển sang broker, một event thường phải:

- Có `eventId` độc nhất, `eventType`, `schemaVersion`, `occurredAt` hoặc metadata thời gian, `aggregateId`/business key.
- Mang **ID hoặc snapshot dữ liệu bất biến**, không mang JPA entity đang gắn `EntityManager`/`Session`, stream hoặc object phụ thuộc vòng đời request.
- Có schema tương thích khi producer/consumer nâng cấp độc lập; thay đổi field cần chính sách versioning rõ.
- Không chứa secret, token hay dữ liệu cá nhân dư thừa, vì broker có retention, replication và nhiều consumer.
- Định nghĩa ordering: ordering theo aggregate/partition key khi nghiệp vụ cần, nhưng **không giả định global ordering giữa nhiều partition**.
- Có timeout, retry và quyền truy cập cho publisher/consumer.

Một event payload chỉ chứa order ID cho consumer đọc lại DB sẽ cho phép lấy trạng thái mới nhất, **không nhất thiết trạng thái tại thời điểm event xảy ra**. Nếu nghiệp vụ cần snapshot lịch sử, hãy mang version hoặc các giá trị đã cố định trong event; nếu consumer fetch dữ liệu mới nhất, phải nhận thức tính **eventual consistency**.

## 13. Rejection và backpressure: không phải chỉ chọn queue dài hơn

Với `ThreadPoolTaskExecutor`, ba tham số quan trọng:

- `corePoolSize`: số worker lõi.
- `maxPoolSize`: trần thread mà pool có thể mở rộng.
- `queueCapacity`: dung lượng hàng đợi tác vụ.

Khi core bận, executor thường **ưu tiên enqueue** nếu queue còn chỗ. Khi queue đầy, pool mới thử tăng worker tới max; khi không nhận được tác vụ, có thể ném `TaskRejectedException` theo hợp đồng Spring.

Tăng queue chỉ giúp hấp thụ burst có giới hạn; **không tạo độ bền**. Một queue RAM chứa hàng nghìn event vẫn biến mất khi process chết đột ngột. Ngoài ra queue quá dài tăng latency, giữ memory và có thể làm thông báo quá hạn.

Khi dùng broker, backpressure cũng cần thiết:

1. Giới hạn in-flight messages, prefetch/concurrency và memory của consumer.
2. Nếu downstream quá tải, giảm tốc, retry với backoff/jitter và trần lần thử.
3. Dùng DLQ/quarantine cho poison message sau chính sách retries, với công cụ replay có kiểm soát.
4. Theo dõi `oldest_unprocessed_age`, queue depth, delivery lag, failure/retry count và thời gian thực hiện side effect.
5. Cảnh báo khi outbox backlog tăng dù HTTP requests vẫn thành công.

Nếu `publishEvent` nội bộ bị rejection, việc catch rồi “bỏ qua tạm” có thể là đúng **chỉ khi** nghiệp vụ đã tuyên bố event thuộc loại best-effort hoặc có outbox bền vững để khôi phục. Không biến mất event quan trọng thành “log warning” vô nghĩa.

## 14. Lỗi trong @Async listener không quay về caller như exception đồng bộ

Với listener:

```java
@Component
public class EmailListener {
    @Async("eventPool")
    @EventListener
    public void on(OrderPlaced event) {
        // Giả sử có IOException hoặc lỗi từ nhà cung cấp email.
        sendEmail(event.id());
    }
}
```

Đây là **mảnh minh họa cần `@Component`, imports, bean `eventPool` và method `sendEmail`**, không phải chương trình độc lập. Khi listener đã được giao cho worker, exception phát sinh trong xử lý **không tự ném trở lại** phương thức `publishEvent` của caller. Với `@Async void`, cần `AsyncUncaughtExceptionHandler` hoặc log/metrics thích hợp.

Một số điều cần tránh:

- **Chỉ dựa vào `try/catch` xung quanh `publishEvent`:** nó có thể bắt lỗi ở đường phát/gửi task, không bắt được mọi lỗi worker sau này.
- **Dùng `@Async` như retry framework:** Spring không tự persist delivery attempts và tự replay event sau restart.
- **Dùng return value của async `@EventListener` để publish event tiếp:** Spring docs nêu tính năng return-value publishing không hỗ trợ async listener; nếu cần phát event mới, inject `ApplicationEventPublisher` và publish rõ ràng, đồng thời xác định reliability riêng.
- **Tưởng `@Order` áp dụng đồng bộ thứ tự hoàn tất giữa các worker:** một số listener có thể được gọi theo thứ tự dispatch, nhưng thực thi async hoàn tất không bảo đảm cùng thứ tự.
- **Tưởng transaction ThreadLocal của publisher tự có trên worker:** không có bảo đảm; cần transaction mới ở worker nếu nghiệp vụ yêu cầu.

Đối với công việc quan trọng, việc quan sát lỗi qua `AsyncUncaughtExceptionHandler` chưa đủ nếu không có **persistent record + retry/recovery policy**.

## 15. Một phép thử sự cố thực tế nên bao phủ những gì?

Một kiểm thử “listener chạy khi mọi thứ đều tốt” chưa chứng minh delivery guarantee. Các tình huống bắt buộc cân nhắc:

| Kịch bản thử | Với local event + @Async | Với outbox + durable delivery đúng thiết kế |
| --- | --- | --- |
| Listener xử lý thành công khi JVM sống | Thường chạy được | Phải chạy được |
| Transaction business rollback | Listener thường có thể chạy sớm nếu không gắn phase | Outbox record cũng rollback cùng business state |
| Process chết sau DB commit nhưng trước listener chạy | Có nguy cơ mất event | Outbox record tồn tại, relay khôi phục sau restart |
| Process chết sau broker confirm trước outbox marked-sent | Không có trạng thái durable để suy luận | Relay có thể phát lại; consumer phải dedup |
| Consumer ghi DB thành công rồi chết trước broker ack | Không có broker ack tương ứng | Message có thể redeliver; consumer xử lý idempotent |
| Broker tạm thời down | In-memory listener không tự lưu retry qua restart | Relay giữ outbox PENDING và retry theo policy |
| Queue bị đầy | Có thể bị reject ngay | Có backlog/flow control nhưng vẫn cần giới hạn |
| Có nhiều instance và restart cuốn chiếu | Local event chỉ tồn tại trong context instance hiện tại | Kho event/broker chung có cơ chế claim/ack và durable storage |

Tạo fault-injection test trên môi trường được phép: dừng process ngay sau commit nhưng trước relay; ngắt mạng broker; kill consumer sau side effect nhưng trước ack; phát trùng cùng `eventId`; tạo event cũ với schema version trước; khởi động lại và so sánh **tất cả event đã commit** với tập event đã xử lý.

Một test bền vững nên có ít nhất các invariant:

- Mỗi business transaction thành công sinh **đúng một ý định event** trong outbox theo business key đã định nghĩa.
- Không có intent phát khi transaction rollback.
- Mọi intent chưa hoàn tất vẫn truy vấn được sau restart.
- Khi chạy retry/redelivery, external side effect không bị nhân đôi trái hợp đồng.
- Chỉ đánh dấu SENT/ACKED sau khi có bằng chứng phù hợp.
- Backlog cuối cùng giảm về trạng thái bình thường khi dependency được phục hồi.

Đừng thử crash trên production hoặc gửi tải không kiểm soát chỉ để chứng minh một pattern; cần isolation và teardown đúng.

## 16. Monitoring: nhìn delivery end-to-end, không chỉ log “event published”

Đối với event nội bộ, metric hữu ích gồm:

- Số event được publish và số event listener nhận; tách theo loại event.
- Executor active threads, queue size/capacity, rejection.
- Thời gian từ publish đến listener start và đến listener finish.
- Tỷ lệ listener failed/timeouts; mất/khôi phục context khi chạy async.

Đối với durable pipeline cần thêm:

- Số outbox records `PENDING`, `PROCESSING`, `FAILED`; tuổi record cũ nhất.
- Producer confirmations, lỗi publish, lần thử và thời gian retry.
- Consumer lag/prefetch/in-flight, ack/nack, DLQ volume.
- Số event deduplicated, schema-deserialization failures.
- Độ trễ từ **business commit đến side effect hoàn tất**, không chỉ HTTP response.
- Dữ liệu đối soát giữa business rows, outbox records và processed-events marker.

Nếu biết event đã commit nhưng chưa có trạng thái handled, một hệ thống bền vững phải có **cách replay và cảnh báo**. Một hệ thống chỉ có log “publish successful” mà không biết listener đã xử lý chưa là **best effort**, không thể cam kết delivery deadline.

Vì asynchronous processing cho phép sau commit mới làm tác vụ phụ, hệ thống có thể **eventually consistent**: order đã PLACED trong DB trong khi email hoặc inventory projection chưa cập nhật. API/UI cần phản ánh trạng thái `PENDING`/`IN_PROGRESS`/`FAILED` khi sự chậm trễ quan trọng, không chỉ trả một cờ “đã xong” dựa trên business commit.

## 17. Những sai lầm phổ biến

1. **Nói `@Async` là queue:** nó lập lịch công việc; queue của executor thường là cấu trúc bộ nhớ không tự replay.
2. **Nói `@EventListener` mặc định bất đồng bộ:** mặc định Spring application event thường được dispatch đồng bộ.
3. **Cho rằng `publishEvent()` trả về là listener đã xử lý thành công:** sai với async, và ngay cả synchronous dispatch cũng không phải durable acknowledgement.
4. **Nói `@TransactionalEventListener(AFTER_COMMIT)` bảo đảm message không thể mất:** chỉ gắn phase; JVM vẫn có thể crash trước khi handler được ghi nhận.
5. **Gọi API ngoài trong transaction trước commit rồi tưởng rollback hoàn tác API ấy:** side effects bên ngoài DB không tự rollback.
6. **Ghi DB rồi publish broker bằng hai bước độc lập, không xử lý dual-write gap:** một bước có thể thành công, bước khác thất bại.
7. **Đánh dấu outbox SENT trước producer confirm:** nếu publish fail có thể mất event mà DB nói đã gửi.
8. **Cho rằng “at least once” đồng nghĩa không bao giờ có duplicate:** resend sau crash là bình thường, cần idempotency.
9. **Đặt dedup marker khác transaction với DB side effect:** có thể có marker thành công nhưng side effect thất bại, hoặc ngược lại.
10. **Giữ entity JPA đang attached làm payload event gửi sang worker:** transaction/persistence context không tự đi theo thread, getter LAZY có thể lỗi.
11. **Tăng `queueCapacity` thật lớn để “bền vững hơn”:** chỉ tăng lượng công việc RAM, rủi ro memory/latency và mất khi crash vẫn còn.
12. **Không giám sát outbox lag và DLQ:** durable storage mà không relay/recovery runbook vẫn không đạt thời gian hoàn tất.
13. **Đồng nhất event publication registry với tính năng của Spring core:** registry bền vững cần dependency, storage và cấu hình riêng.
14. **Tưởng broker tự đảm bảo exactly once cho side effect HTTP/DB:** cần thiết kế transaction boundary, idempotency và recovery.
15. **Cho rằng restart êm sẽ giống crash:** graceful shutdown có thể drain, nhưng không phải bảo đảm cho sự cố bất ngờ.
16. **Không định nghĩa hợp đồng business về mất/trễ/trùng:** kỹ thuật chỉ đúng khi đáp ứng mức độ tin cậy nghiệp vụ chấp nhận.

## 18. Bài tập tình huống và câu hỏi tự kiểm tra

**Câu 1.** Một hệ thống dùng `@Async @EventListener` để gửi email sau khi nhận đơn hàng. Khi JVM bị kill trước khi worker lấy task từ queue, Spring có tự gửi email lại không?  
**Đáp án:** Không có cam kết mặc định. Queue in-memory không tồn tại qua crash; cần lưu intent bền vững và recovery.

**Câu 2.** Đổi listener thành `@TransactionalEventListener(AFTER_COMMIT)` có giải quyết hoàn toàn mất event không?  
**Đáp án:** Không. Nó giải quyết thứ tự với business commit và tránh xử lý khi rollback, nhưng không tự lưu publication. Crash ngay sau commit có thể làm mất callback.

**Câu 3.** Có thể làm sao để transaction lưu order và intent phát event đồng thời thành công/thất bại?  
**Đáp án:** Ghi order và outbox record trong **cùng DB transaction** có atomic commit.

**Câu 4.** Relay gửi thành công tới broker nhưng chết trước khi đánh dấu SENT. Điều gì xảy ra?  
**Đáp án:** Khi khởi động lại, relay có thể gửi lại cùng event. Consumer phải dùng event ID ổn định và idempotency/dedup.

**Câu 5.** Consumer đã ghi side effect trong DB rồi chết trước ack. Broker có thể phát lại không?  
**Đáp án:** Có theo at-least-once semantics. Dedup marker và DB side effect nên được commit trong cùng transaction phù hợp.

**Câu 6.** Dùng `@EventListener` bình thường trong `@Transactional` rồi gửi external HTTP call ngay, có an toàn khi business rollback không?  
**Đáp án:** Không. External side effect có thể đã thực hiện dù DB transaction rollback.

**Câu 7.** Spring Modulith Event Publication Registry có sẵn tự động mỗi khi bật `@EnableAsync` không?  
**Đáp án:** Không. Đây là subsystem bổ sung cần dependency và cấu hình persistent store/republication phù hợp.

**Câu 8.** Khi nào local async event vẫn đủ tốt?  
**Đáp án:** Khi nghiệp vụ **chấp nhận mất tín hiệu hoặc có cơ chế tính lại khác**, không cần replay qua restart, và đã biết giới hạn latency/rejection của executor.

**Câu 9.** Vì sao không gọi `publishEvent` là một thao tác “publish vào Kafka” nếu không có adapter riêng?  
**Đáp án:** `ApplicationEventPublisher` gốc giao event cho Spring ApplicationContext. Nó không có hợp đồng tự ghi message vào broker; adapter/outbox/externalization cần được thiết kế riêng.

**Câu 10.** Khi cần chắc chắn email được gửi cuối cùng, chỉ cần retry listener vô hạn trong RAM đúng không?  
**Đáp án:** Không. RAM không cứu được crash; retry không giới hạn còn gây áp lực và duplicate. Cần persistent intent, retry có giới hạn/backoff, idempotency và quan sát trạng thái.

**Ghi nhớ:** **Event nội bộ tách rời các bean; `@Async` tách thời điểm thực thi; `AFTER_COMMIT` gắn với thời điểm commit; outbox/registry/broker được cấu hình đúng mới tạo nền tảng lưu bền và recovery.** Luôn tính cả khoảng crash, duplicate và side effect khi nói tới độ tin cậy của sự kiện.

### Tài liệu tham khảo

- [Spring Framework — ApplicationContext events và @EventListener](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html)
- [Spring Framework — ApplicationEventPublisher JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/ApplicationEventPublisher.html)
- [Spring Framework — Transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Spring Framework — TransactionalEventListener JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html)
- [Spring Framework — SimpleApplicationEventMulticaster JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/event/SimpleApplicationEventMulticaster.html)
- [Spring Modulith — Working with Application Events / Publication Registry](https://docs.spring.io/spring-modulith/reference/events.html)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transactions-events-008">Không giả định hai tác vụ trên hai luồng có thể rollback cùng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Không giả định hai tác vụ trên hai luồng có thể rollback cùng nhau

Trong Spring Framework, `@Transactional` giúp quản lý ranh giới giao dịch, còn `@Async` và `TaskExecutor` giúp chuyển công việc sang một luồng khác. Kết hợp hai cơ chế này **không tự tạo ra một giao dịch duy nhất bao trùm nhiều luồng**.

Với mô hình giao dịch imperative thông thường của Spring (`PlatformTransactionManager`, JDBC/JPA), transaction được gắn với **luồng thực thi hiện tại**. Khi method A đang trong transaction và gửi method B tới một worker của thread pool, worker **không tự thừa hưởng transaction của A**. Nếu B có một transaction riêng, B có thể commit độc lập trước khi A rollback; ngược lại, A có thể commit khi B thất bại.

**Quy tắc cốt lõi: rollback của một transaction không phải lệnh “hoàn tác mọi công việc đồng thời”**. Muốn hai thao tác thật sự có tính nguyên tử, chúng cần nằm trong một ranh giới giao dịch được transaction manager và tài nguyên hỗ trợ. Muốn xử lý bất đồng bộ bền vững và nhất quán giữa nhiều bước, cần thiết kế giao thức rõ như event sau commit, transactional outbox hoặc saga, không chỉ gắn `@Transactional` lên hai method.

Bài Knowledge thuộc nhóm **Giao dịch và sự kiện** trong **Spring Framework - concurrency**. Ví dụ dựa trên Java 17+, Spring Framework 6.x / Spring Boot 3.x; phần Java thuần có thể biên dịch độc lập, còn code Spring yêu cầu database và Spring dependencies.

## 1. Giao dịch thực sự là gì trong trường hợp này?

Một database transaction thường có những thao tác: bắt đầu, đọc/ghi, commit hoặc rollback. Với JDBC, các câu lệnh nằm trên cùng connection và cùng transaction có thể được commit hoặc rollback **như một đơn vị** theo khả năng của database.

Trong Spring imperative, `@Transactional` được triển khai thường qua Spring AOP proxy và `TransactionInterceptor`. Transaction manager quản lý connection, session và trạng thái giao dịch gắn với thread. Các thao tác truy cập dữ liệu **tham gia cùng ranh giới transaction khi chúng dùng transaction manager/tài nguyên thích hợp và được thực thi trong context đó**.

Ví dụ cùng thread:

~~~text
Thread HTTP #1
    |
    | --> Transaction T1 bắt đầu
    |     INSERT orders
    |     INSERT audit_events
    |     throw RuntimeException
    | --> T1 rollback
    |
    +--> cả hai INSERT của T1 được rollback
         (nếu cùng tài nguyên transactional và không có commit độc lập)
~~~

Trường hợp này khác hẳn hai worker:

~~~text
Thread HTTP #1 / Transaction T1             Worker #2 / Transaction T2
    |                                               |
    | BEGIN T1                                      |
    | INSERT orders                                 |
    |---- submit async audit ---------------------->|
    |                                               | BEGIN T2
    |                                               | INSERT audit_events
    |                                               | COMMIT T2
    | throw RuntimeException                        |
    | ROLLBACK T1                                   |
    |                                               |
    v                                               v
  orders bị rollback                        audit_events của T2
                                            vẫn đã commit
~~~

Đây là **một kết quả có thể xảy ra**, không khẳng định mọi lần chạy đều theo đúng lịch đó. Nếu worker được xếp queue lâu, nó có thể bắt đầu sau khi T1 rollback. Nếu worker cần đọc dữ liệu chưa commit của T1, nó còn có thể bị chặn, không nhìn thấy dữ liệu, hoặc vướng foreign-key constraint. Điều quan trọng là **hai giao dịch không được đồng bộ commit/rollback chỉ vì được gọi từ cùng một request**.

### 1.1. Phạm vi của nhận định

Bài này nói về transaction imperative do `PlatformTransactionManager` quản lý trong cấu hình Spring thông dụng, chẳng hạn `DataSourceTransactionManager` / `JdbcTransactionManager` hoặc `JpaTransactionManager`. Các transaction này thường **thread-bound**, không tự lan sang luồng khác.

Mô hình reactive của Spring có thể dùng `ReactiveTransactionManager` và Reactor Context thay vì ThreadLocal. Đây là một kiến trúc truyền context khác, **không phải bằng chứng rằng @Transactional tự động rollback đồng bộ mọi task trên nhiều thread**. Không trộn ngữ nghĩa reactive với JDBC/JPA imperative khi đánh giá bài toán này.

## 2. Tại sao @Transactional không tự “đi theo” @Async?

Cơ chế phổ biến:

1. Caller đi qua Spring proxy của method `@Transactional`; interceptor mở/đăng ký transaction **cho thread caller**.
2. Bên trong method, code gọi bean có `@Async` qua một Spring proxy khác.
3. Interceptor `@Async` submit callback vào `TaskExecutor`. Với thread pool bất đồng bộ, worker khác sẽ thực thi callback.
4. Transaction context đang gắn với thread caller **không tự chuyển qua worker**.
5. Nếu worker gọi một method transactional qua proxy, Spring có thể mở **một transaction riêng** trên worker; nếu không có transactional advice ở worker, các câu lệnh có thể chạy autocommit hoặc theo cấu hình khác.
6. Caller kết thúc hoặc ném exception; transaction của caller commit/rollback theo quy tắc riêng. Trạng thái đó không tự undo transaction worker đã commit.

~~~text
HTTP thread                     @Async interceptor                Pool worker
    |                                  |                               |
    | BEGIN parent TX                  |                               |
    | INSERT parent                    |                               |
    |---- asyncMethod() -------------->|-- queue task ---------------->|
    |<--- return Future ---------------|                               |
    |                                  |                         BEGIN worker TX?
    |                                  |                         INSERT child
    |                                  |                         COMMIT/ROLLBACK
    | ROLLBACK parent TX               |                               |
    |                                  |                               |
    | Không có implicit shared transaction giữa hai thread             |
~~~

Ký hiệu dấu hỏi ở `BEGIN worker TX?` có chủ đích: worker chỉ nhận một transaction riêng khi cấu hình, annotation/proxy và resource manager thực sự tạo nó; **không phải mọi @Async task đều mặc nhiên có transaction**.

### 2.1. REQUIRED, REQUIRES_NEW và sự nhầm lẫn thường gặp

- `Propagation.REQUIRED`: trong cùng thread và cùng transaction manager, method transactional có thể tham gia transaction đang có; nếu không có transaction tương ứng, Spring tạo mới.
- Khi B chạy trên **một worker khác**, B thường không thấy transaction A trên caller. `REQUIRED` ở B do đó có thể mở một transaction mới **cho worker**, không nối vào A.
- `Propagation.REQUIRES_NEW` tạo một physical transaction độc lập khi có transaction đang chạy trong context đó; nó **không phải công cụ gộp hai thread**. Transaction mới có thể commit/rollback độc lập.
- `Propagation.NESTED` dùng savepoint của một physical transaction theo khả năng transaction manager/database; không biến hai database connection khác luồng thành một unit rollback chung.
- Nếu dùng cùng một `DataSource` thì vẫn **không đồng nghĩa** cùng một JDBC connection/transaction. Mỗi thread thường có tài nguyên riêng được transaction manager bind.

Trong code imperative Spring, propagation mô tả cách xử lý **các lời gọi trong context transaction đang thấy**, không phải cơ chế tự truyền một transaction qua `ExecutorService.submit`.

## 3. Java 17: mô phỏng hai transaction theo ThreadLocal

Chương trình này mô phỏng đơn giản **việc transaction context gắn với thread** và việc commit/rollback riêng. Nó **không kết nối database và không phải implementation giao dịch JDBC**; mục đích là làm rõ tại sao một worker không tự thừa hưởng trạng thái từ caller.

Tạo tệp **`CrossThreadTransactionDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CrossThreadTransactionDemo {
    record Tx(String name, List<String> staged) {}

    private static final ThreadLocal<Tx> CURRENT = new ThreadLocal<>();
    private static final List<String> COMMITTED = new CopyOnWriteArrayList<>();

    static void begin(String name) {
        CURRENT.set(new Tx(name, new ArrayList<>()));
    }

    static void stage(String change) {
        Tx tx = CURRENT.get();
        if (tx == null) {
            throw new IllegalStateException("No local transaction");
        }
        tx.staged().add(change);
    }

    static void commit() {
        Tx tx = CURRENT.get();
        if (tx == null) {
            throw new IllegalStateException("No local transaction");
        }
        COMMITTED.addAll(tx.staged());
        CURRENT.remove();
    }

    static void rollback() {
        CURRENT.remove();
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        begin("caller");
        try {
            stage("order");
            Future<?> child = executor.submit(() -> {
                System.out.println("1. Child inherited transaction: "
                        + (CURRENT.get() != null));
                begin("child");
                try {
                    stage("audit");
                    commit();
                    System.out.println("2. Child committed audit");
                } finally {
                    CURRENT.remove();
                }
            });
            child.get();
            rollback();
            System.out.println("3. Caller rolled back order");
            System.out.println("4. Committed changes: " + COMMITTED);
        } finally {
            CURRENT.remove();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 CrossThreadTransactionDemo.java
java CrossThreadTransactionDemo
~~~

Stdout đã được xác minh khi chạy với JDK biên dịch ở mức `--release 17`:

~~~text
1. Child inherited transaction: false
2. Child committed audit
3. Caller rolled back order
4. Committed changes: [audit]
~~~

Giải thích:

1. `ThreadLocal<Tx>` thông thường không được copy sang worker thread. `CURRENT.get()` trên worker là `null`, nên in `false`.
2. Worker tự gọi `begin("child")`, stage thay đổi `audit` rồi `commit()`. Danh sách mô phỏng dữ liệu đã commit nhận `audit`.
3. `Future.get()` khiến caller **đợi worker hoàn tất**, nhưng **không nhập transaction của worker vào transaction của caller**.
4. Caller rollback thao tác `order` đã stage trong context riêng; thay đổi `audit` đã commit vẫn ở danh sách.
5. Thứ tự output xác định nhờ `Future.get()` và việc worker không in từ nhiều luồng không đồng bộ cùng lúc trong các bước cần kiểm tra.

Điểm mấu chốt: **chờ worker chạy xong không đồng nghĩa có chung transaction**. `CompletableFuture.allOf(...).join()` cũng chỉ là cơ chế đợi hoàn tất nhiều future; không có tính chất tự rollback mọi transaction đã commit.

### 3.1. Điều mô phỏng này không chứng minh

- Không thực hiện JDBC commit hoặc rollback thực.
- Không kiểm tra isolation level, lock, deadlock hay constraint.
- Không chứng minh rằng cứ dùng Spring là kết quả luôn giống đúng thứ tự stdout trên.
- Không có distributed transaction coordinator.

Để đánh giá chính xác ứng dụng, cần integration test với database và transaction manager thật ở các mục sau.

## 4. Một anti-pattern Spring: parent rollback nhưng worker commit riêng

Giả sử ứng dụng quản lý đơn hàng và audit bằng Spring JDBC. Sơ đồ dữ liệu chỉ nhằm học: bảng `audit_events` trong ví dụ **không có foreign key** đến bảng `orders` để quan sát trường hợp audit độc lập vẫn commit dù order bị rollback.

~~~sql
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE audit_events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL
);
~~~

DDL trên là ví dụ cho database hỗ trợ cú pháp identity tương ứng (ví dụ H2); trong PostgreSQL hoặc hệ quản trị khác cần kiểm tra dialect và migration phù hợp. **Không bỏ foreign key trong thiết kế thật chỉ để tránh lỗi**; ở đây việc không có FK cố ý để làm lộ rủi ro ghi “audit mồ côi”.

### 4.1. Bean executor và quản lý transaction

Tệp `ConcurrentTxConfig.java`:

~~~java
package example.concurrency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableAsync
@EnableTransactionManagement
public class ConcurrentTxConfig {

    @Bean("auditExecutor")
    public ThreadPoolTaskExecutor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("audit-");
        return executor;
    }
}
~~~

Đây là **mã tích hợp Spring Framework 6.x / Java 17+**, không phải chương trình có thể chạy bằng `javac` thiếu Spring dependencies. Cần một `PlatformTransactionManager` được cấu hình đúng cho JDBC/JPA, datasource, schema và component scanning. Trong Spring Boot tương thích, những thành phần này thường do cấu hình/data-access starter cung cấp; với Spring Framework thuần phải đăng ký phù hợp.

### 4.2. AuditWriter mở transaction riêng khi được worker gọi

Tệp `AuditWriter.java`:

~~~java
package example.concurrency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditWriter {
    private final JdbcTemplate jdbc;

    public AuditWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void saveRequested(long orderId) {
        jdbc.update(
                "INSERT INTO audit_events(order_id, event_type) VALUES (?, ?)",
                orderId, "ORDER_REQUESTED");
    }
}
~~~

Tệp `AsyncAuditDispatcher.java`:

~~~java
package example.concurrency;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncAuditDispatcher {
    private final AuditWriter writer;

    public AsyncAuditDispatcher(AuditWriter writer) {
        this.writer = writer;
    }

    @Async("auditExecutor")
    public CompletableFuture<Void> dispatch(long orderId) {
        writer.saveRequested(orderId);
        return CompletableFuture.completedFuture(null);
    }
}
~~~

Lời gọi đi qua **hai bean/proxy khác nhau**: Spring gửi `AsyncAuditDispatcher.dispatch(...)` tới worker; trên worker, lời gọi `AuditWriter.saveRequested(...)` đi qua transactional proxy và mở giao dịch của worker. Việc tách này giúp tránh nhầm lẫn về thứ tự các advice khi đặt `@Async` và `@Transactional` trên cùng một method và tránh self-invocation.

### 4.3. Parent transaction gửi công việc rồi rollback

Tệp `OrderCreationService.java`:

~~~java
package example.concurrency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreationService {
    private final JdbcTemplate jdbc;
    private final AsyncAuditDispatcher audit;

    public OrderCreationService(
            JdbcTemplate jdbc, AsyncAuditDispatcher audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void createThenFail(long orderId) {
        jdbc.update(
                "INSERT INTO orders(id, status) VALUES (?, ?)",
                orderId, "PENDING");

        audit.dispatch(orderId);

        throw new IllegalStateException("rollback order transaction");
    }
}
~~~

Ví dụ trên cố ý minh họa **thiết kế không an toàn**:

- Parent insert `orders` trong transaction T1 chưa commit.
- Parent submit audit; caller **không đợi** worker và cũng không xử lý future trả về.
- Worker có thể bắt đầu T2, insert `audit_events` và commit.
- Parent ném `IllegalStateException`, dẫn tới T1 rollback theo quy tắc mặc định của `@Transactional` cho `RuntimeException`.
- Nếu T2 đã commit, hàng audit có thể vẫn tồn tại dù order đã rollback. Hoặc T2 có thể chạy sau khi T1 rollback và vẫn insert audit.

Không có stdout xác định cho ví dụ tích hợp Spring vì thứ tự scheduler, database, queue và cấu hình connection pool thay đổi kết quả quan sát. Nếu thêm foreign key từ audit đến order, worker có thể bị chặn hoặc gặp lỗi ràng buộc do order chưa commit/đã rollback; đó là **một dạng hỏng khác**, không phải cách sửa ranh giới giao dịch.

Với `@Async` trả `CompletableFuture`, exception phát sinh trong worker có thể làm Future hoàn tất thất bại. Nhưng vì parent không giữ/quan sát Future, lỗi dễ bị bỏ sót; cho dù parent gọi `get()` rồi throw để rollback T1, T2 **đã commit sẽ không tự rollback**.

### 4.4. Bốn kết cục khác nhau cần phân biệt

| Thứ tự thực tế | Kết quả có thể xảy ra |
| --- | --- |
| T2 commit, sau đó T1 rollback | Audit tồn tại, order không tồn tại |
| T1 rollback trước, T2 chạy và commit | Audit vẫn có thể tồn tại nếu không có constraint |
| T1 commit, T2 lỗi/rollback | Order tồn tại nhưng audit thiếu |
| T1 commit, T2 commit | Cả hai tồn tại, nhưng vẫn **không phải một atomic transaction** |

Cơ chế chạy song song tạo ra nhiều interleaving, không có cam kết all-or-nothing. Nếu cùng cần DB connection và pool hết chỗ, còn có thể phát sinh blocking hoặc deadlock khi caller giữ transaction trong lúc đợi worker.

## 5. Khi cần atomic rollback: giữ các ghi liên quan trong một transaction

Nếu hai thao tác là các bản ghi cùng một database và nghiệp vụ đòi hỏi **cùng thành công hoặc cùng thất bại**, giải pháp đơn giản thường là thực hiện chúng **trong một method transaction cùng thread**.

Tệp `AtomicOrderService.java` (một phương án **thay thế**, không dùng cùng lúc với anti-pattern):

~~~java
package example.concurrency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtomicOrderService {
    private final JdbcTemplate jdbc;

    public AtomicOrderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void createOrderWithAudit(long orderId) {
        jdbc.update(
                "INSERT INTO orders(id, status) VALUES (?, ?)",
                orderId, "PENDING");
        jdbc.update(
                "INSERT INTO audit_events(order_id, event_type) VALUES (?, ?)",
                orderId, "ORDER_CREATED");
    }
}
~~~

Khi `@Transactional` thật sự hoạt động và cả hai câu SQL cùng tham gia transaction của cùng datasource/transaction manager, nếu một thao tác ném `RuntimeException` không bị xử lý nuốt, transaction có thể rollback cả hai. Nếu method commit thành công, cả hai ghi cùng được xác nhận theo hợp đồng của database.

Lưu ý: không tự động mở rộng khẳng định này sang HTTP request gửi cho dịch vụ ngoài, gửi email, message broker không tham gia cùng transaction hay các tài nguyên không được enlist. Một giao dịch DB **không có nút Undo cho side effect bên ngoài**.

### 5.1. Nếu vẫn muốn xử lý song song?

Đặt câu hỏi trước: liệu yêu cầu thật sự là xử lý parallel và atomic? Một local DB transaction dựa vào một connection không được thiết kế để chia cho nhiều worker tùy ý. Không chuyển cùng một `Connection` hoặc JPA `EntityManager` cho các thread rồi cố dùng chung: các đối tượng truy cập dữ liệu này thường không được thiết kế thread-safe, và Spring không cung cấp cơ chế tự động làm điều đó an toàn.

Nếu cần throughput, có thể chuẩn bị/tính toán dữ liệu song song **không thực hiện side effect**, sau đó tập hợp kết quả và thực hiện các ghi cần atomic trong một transaction trên một thread. Mô hình này vẫn phải xem xét bộ nhớ, timeout và lỗi của các phép tính song song, nhưng tránh chia sẻ transaction DB xuyên thread.

## 6. Sau commit mới phát sự kiện: @TransactionalEventListener

Một vấn đề riêng là **không nên chạy task phụ nếu transaction chính rollback**. Với sự kiện chỉ có ý nghĩa sau khi dữ liệu đã commit, Spring hỗ trợ `@TransactionalEventListener`, mặc định liên kết listener với phase `AFTER_COMMIT`.

Ví dụ, service phát event bên trong transaction:

~~~java
package example.concurrency;

public record OrderCreatedEvent(long orderId) {}
~~~

Tệp `OrderRegistrationService.java`:

~~~java
package example.concurrency;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderRegistrationService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public OrderRegistrationService(
            JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Transactional
    public void create(long orderId) {
        jdbc.update(
                "INSERT INTO orders(id, status) VALUES (?, ?)",
                orderId, "CREATED");
        events.publishEvent(new OrderCreatedEvent(orderId));
    }
}
~~~

Tệp `OrderCreatedListener.java`:

~~~java
package example.concurrency;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderCreatedListener {
    private final AuditWriter writer;

    public OrderCreatedListener(AuditWriter writer) {
        this.writer = writer;
    }

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        writer.saveRequested(event.orderId());
    }
}
~~~

Các đoạn trên là **mẫu tích hợp thay thế** cho anti-pattern, dùng `ConcurrentTxConfig`, `AuditWriter`, schema và transaction manager đã nêu. Không có output cố định. Ý nghĩa:

- Event được publish trong transaction T1.
- Nếu T1 rollback, listener `AFTER_COMMIT` **không thực hiện logic ứng với commit thành công**.
- Sau khi T1 commit, callback của listener mới được kích hoạt; `@Async` chuyển phần xử lý sang executor đã chọn.
- `AuditWriter.saveRequested(...)` chạy trên worker qua proxy transactional của mình, vì vậy có transaction ghi audit **riêng**, không phải một phần của T1.

**Giới hạn quan trọng:** `AFTER_COMMIT` chỉ xác định **thời điểm bắt đầu xử lý event liên quan đến commit**, không bảo đảm audit chắc chắn được xử lý. Ứng dụng có thể crash ngay sau DB commit nhưng trước khi async job được lưu/chạy; executor có thể từ chối, worker có thể fail, event in-memory có thể mất. Và nếu audit thất bại sau commit, **không rollback lại order đã commit**.

`@TransactionalEventListener` mặc định **không chạy khi không có transaction** để gắn phase, trừ khi chủ động dùng `fallbackExecution=true`. Đừng bật fallback chỉ để “cho chạy bằng mọi giá” nếu nghiệp vụ yêu cầu chỉ phát sau commit.

### 6.1. Các phase sự kiện không có nghĩa rollback xuyên thread

| Phase | Khi nào listener được kích hoạt? | Điều không được suy ra |
| --- | --- | --- |
| `BEFORE_COMMIT` | Trước khi transaction chuẩn bị commit | Không có bảo đảm callback async là một phần của commit DB |
| `AFTER_COMMIT` | Sau khi transaction commit thành công | Không biến tác vụ chạy sau đó thành atomic với transaction trước |
| `AFTER_ROLLBACK` | Sau khi transaction rollback | Không tự rollback mọi side effect đã xảy ra ở worker khác |
| `AFTER_COMPLETION` | Sau khi transaction kết thúc dù commit hoặc rollback | Không cung cấp durable retry hoặc sự kiện đúng một lần |

Nếu xử lý **đồng bộ ngay trong AFTER_COMMIT** và tiếp tục ghi DB bằng tài nguyên transactional cũ, cần cẩn trọng: tài nguyên có thể còn bound trong giai đoạn callback nhưng transaction chính đã hoàn tất; những ghi mới có thể không được commit theo cách bạn mong đợi. Có thể dùng transaction mới (ví dụ `REQUIRES_NEW` theo trường hợp) hoặc chuyển sang worker có transaction riêng. Điều này vẫn không làm hai transaction atomic.

## 7. Khi cần phát việc chắc chắn sau commit: transactional outbox

Nếu yêu cầu là **order commit thì chắc chắn có bản ghi để về sau phát event**, một listener in-memory `AFTER_COMMIT` chưa đủ độ bền. Mẫu **transactional outbox** lưu **domain data và bản ghi sự kiện** trong cùng một local DB transaction.

~~~text
Transaction T1 (một DB)
     |
     +-- INSERT orders
     +-- INSERT outbox_events
     +-- COMMIT cả hai
             |
             v
      Worker / relay độc lập
             |
             +-- đọc bản ghi outbox đã commit
             +-- publish event / gửi broker
             +-- ghi nhận đã gửi hoặc retry
             |
             v
      Consumer xử lý idempotent
~~~

Nếu T1 rollback, cả `orders` và `outbox_events` cùng rollback. Nếu T1 commit, ít nhất có bản ghi outbox bền vững để relay tiếp tục sau restart. **Đây là ranh giới atomic thật cần thiết** giữa dữ liệu đơn hàng và *ý định phát sự kiện*, không phải giữa transaction của DB và quá trình xử lý remote.

Ví dụ schema bổ sung (SQL minh họa; cần điều chỉnh dialect):

~~~sql
CREATE TABLE outbox_events (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload VARCHAR(2048) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE
);
~~~

Tệp `OutboxOrderService.java`:

~~~java
package example.concurrency;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxOrderService {
    private final JdbcTemplate jdbc;

    public OutboxOrderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void createOrder(long orderId) {
        jdbc.update(
                "INSERT INTO orders(id, status) VALUES (?, ?)",
                orderId, "CREATED");

        String eventId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO outbox_events(" +
                "event_id, aggregate_id, event_type, payload, published) " +
                "VALUES (?, ?, ?, ?, ?)",
                eventId, orderId, "ORDER_CREATED",
                "{\"orderId\":" + orderId + "}", false);
    }
}
~~~

Đây là **mã tích hợp Spring/JDBC**, không có relay trong đoạn code. Relay riêng phải đọc các hàng chưa publish, quản lý concurrency/claim/lease, gửi broker, retry/backoff và cập nhật trạng thái với cơ chế chống mất/đúp phù hợp.

### 7.1. Tại sao outbox không có nghĩa exactly-once toàn hệ thống?

Một relay có thể gửi event thành công tới broker rồi crash **trước khi** đánh dấu outbox đã publish. Sau restart, event được gửi lại. Vì vậy cách triển khai thực tế thường có **at-least-once delivery** và người nhận phải xử lý **idempotent** theo `event_id` hoặc idempotency key.

Outbox giải quyết khoảng trống giữa commit domain data và **đảm bảo lưu ý định gửi event**, không khiến toàn bộ hệ thống từ DB đến email/payment có một transaction rollback chung. Cần thiết kế ordering, deduplication, poison-message handling và quan sát backlog theo yêu cầu.

## 8. Saga và compensation khi nhiều giao dịch độc lập

Khi một nghiệp vụ trải qua nhiều service/database, thường không thể gom mọi bước thành một local transaction. **Saga** xem mỗi bước là một local transaction riêng, đồng thời định nghĩa **hành động bù trừ** nếu các bước sau thất bại.

Ví dụ luồng đặt hàng:

~~~text
T1: tạo order PENDING        -> commit
T2: reserve inventory       -> commit
T3: charge payment          -> fail

Compensation C2: release inventory (transaction mới)
Compensation C1: mark order FAILED (transaction mới)
~~~

Điều quan trọng:

- Compensation **không phải rollback vật lý**. Nó là hành động nghiệp vụ mới, có thể thất bại, retry, hoặc để lại dấu vết lịch sử.
- Nếu email đã gửi hoặc dịch vụ thứ ba đã thấy sự kiện, không có “undo” hoàn hảo.
- Cần state machine, correlation id, idempotency, timeout, retry có giới hạn, cơ chế phát hiện thao tác bị kẹt và người vận hành có thể xử lý ngoại lệ.
- Nếu compensation không thành công, workflow phải giữ trạng thái cần xử lý chứ không báo sai “đã rollback toàn bộ”.
- Saga không được thực hiện bằng cách đơn giản catch exception từ `CompletableFuture.allOf` rồi gọi rollback cho mọi database khác nếu các transaction đã commit.

### 8.1. Distributed transaction / XA có tồn tại không?

Có những hệ thống có thể triển khai distributed transaction bằng cơ chế JTA/XA và transaction coordinator với các resource hỗ trợ. Tuy nhiên **đây là một kiến trúc được cấu hình chuyên biệt**, có chi phí, giới hạn và yêu cầu resource enlistment; không tự xuất hiện khi ta gắn `@Transactional` lên các method trên nhiều worker.

Vì vậy tiêu đề bài không nói “hai luồng không bao giờ có thể tham gia một protocol transaction phân tán”, mà nói **không được mặc định coi hai tác vụ trên hai luồng của Spring có thể rollback cùng nhau**.

## 9. Future, join và ngoại lệ không tạo transaction chung

Một lỗi thiết kế phổ biến:

~~~java
@Transactional
public void process() {
    CompletableFuture<Void> f1 = worker.updateA();
    CompletableFuture<Void> f2 = worker.updateB();

    CompletableFuture.allOf(f1, f2).join();
    // Nếu bất kỳ Future lỗi, ném exception để rollback parent.
}
~~~

Đây là **pseudocode Java/Spring**: cần các bean `worker` và code method khác. Nếu `worker.updateA()/updateB()` chạy trên các thread khác với transaction riêng:

- `allOf(...).join()` chỉ chờ các Future hoàn tất. Nó **không biết transaction DB nào đã commit**, không có nút Undo các commit đó.
- Nếu f1 commit T1, f2 rollback T2, rồi `join()` ném `CompletionException`, transaction của caller (nếu có) có thể rollback; **T1 đã commit vẫn độc lập**.
- Khi caller còn giữ DB connection trong transaction và đợi worker lấy connection khác, có thể gặp thiếu connection, block hoặc deadlock. Thời gian chờ kéo dài có thể khiến outer transaction giữ lock lâu.
- `Future.cancel(true)` cũng không bảo đảm ngắt task đang chạy; với `CompletableFuture.cancel()`, tín hiệu cancel của Future càng không có nghĩa rollback transaction worker đã commit.

Nếu thật sự cần một transaction atomic, không dùng `allOf` để mô phỏng transaction manager. Nếu cần concurrency, tách tính toán song song khỏi phần commit atomic, hoặc chấp nhận mô hình eventual consistency có bù trừ.

## 10. Transaction rollback mặc định cần được hiểu đúng

Trong Spring `@Transactional` với transaction manager imperative thông thường:

- `RuntimeException` hoặc `Error` không được bắt ở biên method transactional thường khiến Spring đánh dấu rollback theo quy tắc mặc định.
- Checked exception (như một số `Exception` không phải `RuntimeException`) **không tự kích hoạt rollback** mặc định; có thể dùng `rollbackFor` khi cần.
- Nếu method bắt exception rồi trả về bình thường mà không đánh dấu rollback-only, transaction có thể commit dù một bước logic đã thất bại.
- Lời gọi method cùng bean như `this.transactionalMethod()` có thể bỏ qua Spring proxy, làm `@Transactional` không có hiệu lực trong proxy mode mặc định.
- `@Async` cũng phụ thuộc interceptor/proxy. Không có bảo đảm về ranh giới nếu method không đi qua proxy như dự kiến.

Đây là lý do các ví dụ Spring trong bài dùng **nhiều bean khác nhau**, không để một method vừa submit async vừa gọi trực tiếp một method `@Transactional` trong chính cùng object.

### 10.1. Transaction của parent đã commit thì lỗi worker làm gì?

Giả sử parent gọi async worker rồi commit thành công. Vài giây sau, worker phát hiện lỗi. Parent transaction **không còn active** để có thể rollback bằng exception worker. Cần cập nhật trạng thái job, retry/idempotency hoặc compensation theo hợp đồng nghiệp vụ.

Với `@Async void`, exception chưa xử lý đi qua `AsyncUncaughtExceptionHandler`. Với `@Async CompletableFuture<T>`, exception execution được phản ánh ở Future. **Cả hai không tự rollback một transaction khác đã commit**.

## 11. Quan sát và xử lý lỗi trong hệ thống thực

Các trạng thái nên theo dõi cho mỗi workflow:

| Sự kiện / trạng thái | Thông tin quan trọng |
| --- | --- |
| `PARENT_TX_STARTED` | Correlation id, transaction/workflow id |
| `PARENT_COMMITTED` hoặc `PARENT_ROLLED_BACK` | Ranh giới kết thúc của parent |
| `CHILD_SUBMITTED` và `CHILD_REJECTED` | Executor nhận được task hay từ chối |
| `CHILD_STARTED` | Worker thực sự bắt đầu |
| `CHILD_COMMITTED` hoặc `CHILD_ROLLED_BACK` | Kết quả của transaction child |
| `EVENT_PERSISTED` | Outbox event được ghi trong local transaction |
| `EVENT_PUBLISHED` và `EVENT_RETRIED` | Relay gửi event, có thể gửi lại |
| `COMPENSATION_REQUESTED` / `COMPENSATION_DONE` | Quy trình bù trừ và kết quả |
| `FAILED_NEEDS_ATTENTION` | Workflow không thể tự phục hồi |

Tên trên là **gợi ý log/metric cho ứng dụng**, không phải state Spring tự cung cấp sẵn. Đừng ghi toàn bộ payload nhạy cảm; dùng correlation/job/event id để nối các bước và hạn chế cardinality khi đẩy vào metrics.

### 11.1. Phân biệt ba loại lỗi

**Lỗi submit:** executor bão hòa/shutdown nên không nhận task; ví dụ Spring `TaskRejectedException`. Nếu parent transaction vẫn đang chạy và exception không bị bắt, nó có thể khiến parent rollback. Điều đó **không phải rollback một child transaction đã commit**, vì child có thể chưa được nhận.

**Lỗi thực thi child:** worker đã chạy, DB operation thất bại, child transaction rollback; parent có thể đã commit hoặc đang chạy. Lỗi Future/handler chỉ giúp quan sát, không đồng bộ rollback.

**Lỗi truyền event / gửi message:** giao dịch DB đã commit nhưng relay/broker/network lỗi. Nếu dùng outbox, event còn để retry; nếu chỉ gửi async từ memory, công việc có thể mất.

Đặt chính sách retry khác nhau cho từng nhóm. Không retry vô hạn một lỗi cố định, không retry payment/side effect mà không có idempotency.

## 12. Kiểm thử ranh giới transaction bằng chứng cứ thực

Với Java demo trong mục 3, `ThreadLocal` và danh sách COMMITTED cho phép thấy cơ chế **ngữ cảnh hai thread tách nhau**. Nhưng muốn xác minh Spring/JDBC cần integration test với transaction manager và DB.

Một checklist:

1. **Kiểm tra proxy:** `@EnableTransactionManagement` / auto-configuration, `@EnableAsync`, bean injection, lời gọi từ bean khác. Không gọi `new Service()` trực tiếp.
2. **Kiểm tra connection/transaction:** trong worker, `TransactionSynchronizationManager.isActualTransactionActive()` có thể giúp quan sát có transaction đang active **trên thread hiện tại**, nhưng không chứng minh shared transaction với caller. Có thể đo tên thread/connection id theo công cụ datasource để hỗ trợ.
3. **Dùng latch thay sleep ngẫu nhiên:** chủ động giữ worker hoặc parent tại ranh giới cụ thể để ép các thứ tự commit/rollback; tránh test chập chờn do lịch OS.
4. **Test parent rollback, child commit:** nếu schema cho phép, parent insert rồi throw; child chạy riêng và commit; sau cùng truy vấn DB ngoài test transaction để thấy chỉ dữ liệu child tồn tại.
5. **Test parent commit, child fail:** parent commit rồi worker ném lỗi; xác minh dữ liệu parent còn, Future/job status ghi lỗi child.
6. **Test AFTER_COMMIT:** phát event bên trong transaction. Khi rollback, listener không phát nhiệm vụ commit; khi commit, listener được kích hoạt. Nếu dùng `@Async`, test cần đợi callback bằng latch/eventually assertion.
7. **Test outbox:** khi transaction rollback, domain và outbox đều mất; khi commit, cả hai còn. Giả lập relay gửi thành công nhưng chết trước ACK để kiểm tra deduplication.
8. **Test connection pressure:** kiểm tra trường hợp caller giữ transaction và đợi worker cũng muốn lấy connection. Tránh cấu hình pool mà không đủ connections.
9. **Test failure trong compensation:** đảm bảo state machine không tự báo workflow đã rollback thành công khi bước bù trừ thất bại.

**Cẩn trọng với test mặc định có `@Transactional` trên chính test method**: framework test có thể rollback transaction của test sau mỗi case, trong khi worker async dùng transaction khác. Điều này có thể che giấu hoặc làm khó đọc kết quả. Đối với test độ bền commit/rollback, chủ động quản lý transaction boundary và kiểm tra DB từ bên ngoài transaction test.

## 13. Những hiểu nhầm cần tránh

**“Cả A và B đều có @Transactional nên rollback của A cũng rollback B.”** Sai khi A/B có transaction riêng trên các worker khác. Annotation không tự tạo global transaction.

**“Dùng cùng một DataSource thì chắc chắn cùng transaction.”** Sai. Thread khác thường lấy connection và transaction context khác.

**“Propagation.REQUIRED tự tham gia transaction caller dù gọi @Async.”** Sai trong mô hình PlatformTransactionManager thread-bound; worker không tự thấy context của caller.

**“Dùng REQUIRES_NEW để khiến hai tác vụ rollback cùng nhau.”** Sai. `REQUIRES_NEW` cố ý tạo physical transaction độc lập.

**“Gọi allOf().join() trước khi method return sẽ làm mọi child atomic.”** Sai. Đợi Future không hợp nhất các commit đã thực hiện.

**“Parent rollback thì @Async đã gửi chắc chắn không chạy.”** Sai. Task có thể đã được nhận và xử lý; nó không biết parent rollback nếu không có giao thức.

**“@TransactionalEventListener(AFTER_COMMIT) bảo đảm event không mất.”** Sai. Nó tránh xử lý khi parent rollback, nhưng một callback async in-memory vẫn có thể mất sau commit; outbox giải quyết độ bền của ý định gửi.

**“Nếu audit có foreign key đến order thì hai transaction tự atomic.”** Sai. FK có thể làm worker bị chặn hoặc fail khi parent chưa commit/rollback, không tạo chung transaction.

**“Saga rollback tất cả các transaction đã commit.”** Sai. Saga chạy các **compensating actions** mới; không thực hiện DB rollback vật lý xuyên service.

**“Exception của child Future ném muộn vẫn rollback parent đã commit.”** Sai. Khi parent transaction đã kết thúc, không có ranh giới active để quay lại.

## 14. Bài tập và lời giải

**Câu 1.** Method A có `@Transactional` trên thread HTTP, gọi B có `@Async`. B có `@Transactional(propagation=REQUIRED)`. B có chắc tham gia transaction A không?

**Đáp án:** không. Với Spring imperative, transaction A gắn với thread HTTP; B trên worker thường không thấy transaction đó và có thể mở transaction riêng.

**Câu 2.** B đã commit thành công trên worker; sau đó A ném `RuntimeException`. Spring có rollback luôn commit của B không?

**Đáp án:** không. A có thể rollback giao dịch riêng, không tự hoàn tác transaction B đã commit.

**Câu 3.** Nếu A gọi `CompletableFuture.allOf(f1, f2).join()` trước khi commit, có làm hai child transaction atomic không?

**Đáp án:** không. `join()` chỉ đợi/truyền trạng thái Future, không hợp nhất ranh giới commit của nhiều database transaction.

**Câu 4.** Khi nào có thể dùng một `@Transactional` để rollback cả ghi order và ghi audit?

**Đáp án:** khi cả hai thao tác DB tham gia **cùng một transaction thật** trong mô hình/tài nguyên/manager hỗ trợ, thường cùng thread với JDBC/JPA và không mở transaction độc lập khác.

**Câu 5.** `@TransactionalEventListener(phase=AFTER_COMMIT)` giúp điều gì, và chưa giải quyết điều gì?

**Đáp án:** chỉ gọi listener ở phase sau khi parent commit thành công; chưa bảo đảm delivery bền vững, retry hoặc rollback parent nếu xử lý async sau đó thất bại.

**Câu 6.** Vì sao outbox cần consumer idempotent?

**Đáp án:** relay có thể publish thành công rồi crash trước khi xác nhận đã gửi; sau restart sẽ publish lại, dẫn đến at-least-once delivery.

**Câu 7.** Saga compensation khác rollback DB ở đâu?

**Đáp án:** compensation là một hành động nghiệp vụ mới để bù tác động, không phải đảo ngược vật lý mọi giao dịch đã commit và có thể thất bại.

**Câu 8.** Nếu A đang giữ DB connection rồi `join()` đợi B cũng cần connection, rủi ro gì?

**Đáp án:** cạn connection, lock wait, timeout hoặc deadlock tùy workload; cần tránh chờ các worker cần tài nguyên bị giữ bởi transaction parent.

**Câu 9.** Một task ở worker ném exception sau khi HTTP đã trả 202. Có thể sửa response trước đó thành 500 hoặc rollback transaction parent đã commit không?

**Đáp án:** không. Cần job status/alert/retry/compensation phù hợp; 202 phải được hiểu là accepted theo hợp đồng được thiết kế, không phải success của toàn workflow.

## 15. Ghi nhớ để thiết kế đúng

- **Thread khác không mặc định chung transaction.** Với Spring imperative, transaction context thường thread-bound.
- **@Transactional không tạo rollback toàn cục.** Cần cùng transaction manager/resource và thực tế cùng ranh giới transaction được quản lý.
- **Future/join chỉ đồng bộ kết quả, không đồng bộ commit.**
- **Nếu cùng DB và cần atomic, ghi trong một local transaction.**
- **Nếu cần xử lý sau commit nhưng chấp nhận best-effort, cân nhắc @TransactionalEventListener(AFTER_COMMIT) và quan sát lỗi.**
- **Nếu cần lưu ý định gửi event bền vững cùng domain data, dùng transactional outbox.**
- **Nếu nhiều hệ thống có side effect đã commit, cân nhắc saga với bù trừ/idempotency, không hứa rollback chung.**

## 16. Tài liệu tham khảo chính thức

- Spring Framework 6.2 — Declarative Transaction Implementation (thread-bound transactions): https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-decl-explained.html
- Spring Framework 6.2 — Using @Transactional và rollback settings: https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html
- Spring Framework 6.2 — Transaction Propagation (REQUIRED, REQUIRES_NEW, NESTED): https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html
- Spring Framework 6.2 — Transaction-bound Events (@TransactionalEventListener): https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html
- Spring Framework — Task Execution and Scheduling (@Async): https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Java SE 17 — ThreadLocal: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html
- Java SE 17 — ExecutorService/Future: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html


</details>

- [Quay lại đầu trang](#back-to-top)
