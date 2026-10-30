<a id="back-to-top"></a>

# Trạng thái bean

## Menu
- [Bean singleton được nhiều luồng sử dụng và không tự trở nên an toàn luồng](#bean-state-001)
- [Biến cục bộ, trường đối tượng và dữ liệu dùng chung](#bean-state-002)
- [Đối tượng bất biến và công bố dữ liệu an toàn](#bean-state-003)
- [Giữ dữ liệu của từng yêu cầu tách khỏi trạng thái bean](#bean-state-004)
- [Phép kiểm tra rồi cập nhật và thao tác nhiều bước cần bảo vệ](#bean-state-005)
- [Chọn synchronized, Lock hoặc atomic theo phạm vi bất biến](#bean-state-006)
- [Không giữ khóa khi chờ nhập xuất hoặc chờ tác vụ phụ thuộc](#bean-state-007)
- [Phân biệt phạm vi bean với cơ chế đồng bộ](#bean-state-008)
- [Theo dõi trạng thái bị chia sẻ qua đối số và giá trị trả về](#bean-state-009)

## <a id="bean-state-001">Bean singleton được nhiều luồng sử dụng và không tự trở nên an toàn luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Bean singleton được nhiều luồng sử dụng và không tự trở nên an toàn luồng

## 1. Vấn đề cần hiểu

Trong Spring Framework, một bean có scope **singleton** thường được tạo một lần cho một bean definition trong một Spring IoC container. Khi nhiều thành phần cùng inject bean đó, chúng thường nhận **cùng tham chiếu** đến đối tượng. Nếu nhiều luồng gọi phương thức trên bean cùng lúc, chúng có thể cùng đọc hoặc thay đổi các field của đối tượng ấy.

**Singleton chỉ nói về số lượng và vòng đời instance do container quản lý; singleton không phải cam kết thread-safe.** Spring không tự động khóa mọi lời gọi, chuyển field thành atomic hoặc tuần tự hóa mọi request vào singleton bean. An toàn luồng là trách nhiệm của thiết kế và những cơ chế đồng bộ phù hợp.

Ví dụ thường gặp:

- Hai HTTP request vào cùng một singleton **@RestController** hoặc **@Service** trong ứng dụng Spring MVC có thể chạy trên hai request threads khác nhau.
- Một method của singleton bean được gọi từ executor qua **@Async**, đồng thời bị một request khác gọi.
- Một singleton service được gọi bởi **@Scheduled**, listener hoặc nhiều worker threads.
- Nhiều phần code inject cùng singleton và cùng tác động vào cache, counter hay cấu hình mutable bên trong bean.

Không phải ứng dụng Spring nào cũng có nhiều luồng hoạt động, và không phải mọi method trên singleton đều bị gọi đồng thời. Nhưng **mặc định của scope singleton không ngăn sự đồng thời** khi môi trường thực thi tạo ra nó.

> Quy tắc thiết kế: ưu tiên singleton service **không giữ trạng thái biến đổi theo request**. Khi buộc phải có shared mutable state, phải định nghĩa bất biến (invariant), mức độ nhất quán cần thiết, ranh giới đồng bộ và cách kiểm chứng.

## 2. Singleton của Spring thực sự có nghĩa gì?

### 2.1. Một instance được chia sẻ trong phạm vi container

Scope mặc định của bean trong Spring là singleton. Hai lần lấy **cùng bean definition** từ cùng ApplicationContext sẽ cùng trỏ tới một object, ví dụ:

~~~java
// Minh họa bên trong ứng dụng đã có ApplicationContext.
Object first = context.getBean("orderService");
Object second = context.getBean("orderService");
System.out.println(first == second); // true
~~~

Cần phân biệt:

| Nhận định | Đúng hay sai? | Lý do |
| --- | --- | --- |
| Một bean singleton có thể được inject vào nhiều consumer | Đúng | Consumer dùng chung target instance do container cung cấp |
| Spring singleton là một object duy nhất trong cả JVM | Sai | Singleton scope được quản lý trong từng IoC container và theo bean definition; nhiều context/definition có thể tạo instance khác |
| Mọi instance của cùng một class đều là một bean singleton duy nhất | Sai | Hai bean definitions khác nhau có thể tạo hai đối tượng cùng class |
| Singleton sẽ tự khóa các lời gọi method | Sai | Scope không có chức năng đồng bộ thread |
| Không có mutable shared state thì singleton thường dễ dùng đồng thời | Đúng, với điều kiện | Cần kiểm tra cả các dependency và đối tượng mutable được chia sẻ |
| Một bean là singleton thì luôn bị gọi bởi nhiều thread | Sai | Phụ thuộc runtime và đường đi của request/task |

Lưu ý về **proxy**: trong Spring AOP, caller có thể cầm một proxy thay cho target. Dù proxy can thiệp vào lời gọi, không được suy ra target tự trở nên thread-safe. Những cơ chế như **@Transactional**, **@Async** hay **@Cacheable** cũng không phải khóa chung bảo vệ toàn bộ field của bean.

### 2.2. Phân biệt state theo từng lời gọi và state được chia sẻ

Quan sát hai method sau:

~~~java
@Service
public class InvoiceService {
    // Phần này không lưu dữ liệu của từng lời gọi vào field.
    public long calculate(long quantity, long unitPrice) {
        long subtotal = quantity * unitPrice;
        return subtotal;
    }
}
~~~

Đây là đoạn minh họa (chưa kèm import và cấu hình Spring độc lập). Biến **subtotal** là local variable, mỗi lần gọi có biến riêng. Nếu tham số là primitive hoặc dữ liệu immutable và không có dependency mutable bị sử dụng thiếu an toàn, các lời gọi đồng thời không tranh chấp field trong ví dụ này.

Ngược lại:

~~~java
@Service
public class OrderService {
    private String currentOrderId;

    public void process(String orderId) {
        currentOrderId = orderId;
        // Các bước tiếp theo đọc currentOrderId.
    }
}
~~~

Hai request A và B dùng chung **currentOrderId**. A có thể ghi "A", B ghi "B", rồi A đọc lại "B". Chương trình đã làm lẫn dữ liệu giữa các request dù mỗi method có tham số riêng.

Những dữ liệu thường **không nên** giữ trong field của singleton: request id hiện tại, user hiện tại, thông tin xác thực theo request, dữ liệu form đang xử lý, biến tạm tính toán, một request-scoped object lấy sai cách hoặc một buffer mutable tái sử dụng không có bảo vệ.

Giữ dữ liệu request trong tham số, local variable, giá trị trả về, hoặc chuyển sang cơ chế lưu trữ đúng scope và vòng đời. Local variable chỉ bảo vệ **tham chiếu cục bộ**: nếu nó trỏ đến một object mutable mà các luồng khác cũng dùng chung thì vẫn có race trên object đó.

## 3. Vì sao shared mutable state tạo race condition?

### 3.1. Lost update với phép tăng biến đếm

Giả sử singleton có field:

~~~java
private int count = 0;

public int next() {
    return ++count;
}
~~~

**++count** không phải một thao tác nguyên tử duy nhất. Về mặt logic, nó gồm đọc giá trị hiện tại, tính giá trị mới và ghi lại. Hai luồng có thể xen kẽ:

| Thời điểm | Thread A | Thread B | count lưu trong bean |
| --- | --- | --- | --- |
| 1 | Đọc 0 | | 0 |
| 2 | | Đọc 0 | 0 |
| 3 | Tính 1, ghi 1 | | 1 |
| 4 | | Tính 1, ghi 1 | 1 |
| 5 | Hoàn tất | Hoàn tất | **1 thay vì 2** |

Đây là **lost update**. Đồng thời còn có thể gặp **stale read** (đọc giá trị cũ), **check-then-act race** (kiểm tra điều kiện rồi hành động khi điều kiện đã đổi) và **vi phạm invariant** khi một thao tác cần cập nhật nhiều field nhất quán.

### 3.2. Visibility, atomicity và ordering là ba khái niệm riêng

- **Visibility:** khi thread A cập nhật, thread B có đảm bảo nhìn thấy cập nhật ấy đúng lúc không? Java Memory Model và quan hệ *happens-before* quyết định việc này.
- **Atomicity:** một thao tác ghép gồm nhiều bước có bị các thread khác xen vào giữa không?
- **Ordering:** compiler, CPU và runtime có thể tái sắp xếp các thao tác trong phạm vi được Java Memory Model cho phép; cơ chế đồng bộ đặt ra những ràng buộc quan trọng.

Đánh dấu field **volatile** tăng bảo đảm về visibility và ordering cho các truy cập tương ứng, nhưng **không biến count++ thành một phép tăng nguyên tử**. Hai thread vẫn có thể cùng đọc số cũ rồi cùng ghi số mới.

Tương tự, khai báo object là **final** giữ cố định tham chiếu field sau khởi tạo nhưng **không tự động làm cho nội dung của object được tham chiếu bất biến**. Ví dụ final List không đồng nghĩa mọi lần add/remove đều an toàn luồng.

## 4. Ví dụ chạy được: cùng một singleton bị hai luồng gọi

Ví dụ dưới đây dùng **Java 17+, Spring Framework 6.1.14 và Gradle 8.5+**. Không cần Spring Boot hay máy chủ web; một AnnotationConfigApplicationContext tạo hai bean singleton, rồi executor có hai worker gọi cùng instance. **CyclicBarrier** được đặt trong ví dụ lỗi chỉ để *buộc hai thread đều đọc giá trị cũ trước khi ghi*, qua đó tái hiện lost update một cách có kiểm soát. Trong nghiệp vụ thật không thêm barrier để tăng counter.

Cấu trúc project:

~~~text
singleton-race-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/SingletonRaceDemo.java
~~~

**settings.gradle**:

~~~groovy
rootProject.name = 'singleton-race-demo'
~~~

**build.gradle**:

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
    mainClass = 'demo.SingletonRaceDemo'
}
~~~

**src/main/java/demo/SingletonRaceDemo.java** (một file Java hoàn chỉnh):

~~~java
package demo;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class SingletonRaceDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(BeanConfig.class)) {

            UnsafeCounter unsafe = context.getBean(UnsafeCounter.class);
            SafeCounter safe = context.getBean(SafeCounter.class);

            System.out.println("same bean instance = " +
                    (unsafe == context.getBean(UnsafeCounter.class)));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                // Cả hai task phải đọc cùng giá trị ban đầu rồi mới ghi.
                CyclicBarrier bothRead = new CyclicBarrier(2);
                Callable<Void> unsafeCall = () -> {
                    unsafe.incrementForDemo(bothRead);
                    return null;
                };

                Future<Void> first = pool.submit(unsafeCall);
                Future<Void> second = pool.submit(unsafeCall);
                first.get();
                second.get();

                System.out.println("unsafe updates = " + unsafe.value());

                // AtomicInteger thực hiện increment nguyên tử.
                Callable<Void> safeCall = () -> {
                    safe.increment();
                    return null;
                };

                Future<Void> third = pool.submit(safeCall);
                Future<Void> fourth = pool.submit(safeCall);
                third.get();
                fourth.get();

                System.out.println("atomic updates = " + safe.value());
            } finally {
                pool.shutdown();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BeanConfig {
        @Bean
        UnsafeCounter unsafeCounter() {
            return new UnsafeCounter();
        }

        @Bean
        SafeCounter safeCounter() {
            return new SafeCounter();
        }
    }

    static final class UnsafeCounter {
        // volatile KHÔNG khiến đọc + tăng + ghi trở thành atomic.
        private volatile int count;

        void incrementForDemo(CyclicBarrier bothRead)
                throws InterruptedException, BrokenBarrierException {
            int before = count;
            bothRead.await(); // Dùng riêng cho minh họa race.
            count = before + 1;
        }

        int value() {
            return count;
        }
    }

    static final class SafeCounter {
        private final AtomicInteger count = new AtomicInteger();

        void increment() {
            count.incrementAndGet();
        }

        int value() {
            return count.get();
        }
    }
}
~~~

Tại thư mục **singleton-race-demo**, chạy:

~~~bash
gradle run
~~~

Nếu project có Gradle Wrapper, thay lệnh bằng **./gradlew run** (Linux/macOS) hoặc **gradlew.bat run** (Windows). Ba dòng do chương trình tự in sẽ là:

~~~text
same bean instance = true
unsafe updates = 1
atomic updates = 2
~~~

Gradle có thể in thêm thông báo build và logging phụ thuộc môi trường. Tại sao ba giá trị trên có thể dự đoán? **@Bean** mặc định dùng singleton scope; hai lần getBean trả cùng bean. Trong UnsafeCounter, barrier buộc hai tác vụ cùng giữ bản sao **0**, rồi cả hai đều ghi **1**. Trong SafeCounter, AtomicInteger.incrementAndGet() áp dụng hai lần tăng nguyên tử, nên kết quả cuối là **2**. Việc đợi Future.get() bảo đảm main chỉ đọc kết quả sau khi các task đã hoàn tất.

Ví dụ không nhằm đo throughput hay chứng minh một thuật toán có thể luôn tái hiện race ngoài production. Nó cố tình điều khiển thời điểm xen kẽ để cho thấy **scope singleton không ngăn mất cập nhật**.

## 5. Cách thiết kế singleton an toàn trong thực tế

### 5.1. Ưu tiên stateless service

Tốt nhất là để method nhận đủ đầu vào, tính toán và trả kết quả; field chỉ giữ những dependency được inject hoặc configuration cố định. Ví dụ minh họa:

~~~java
@Service
public class PriceService {
    public long total(long unitPrice, int quantity) {
        return Math.multiplyExact(unitPrice, quantity);
    }
}
~~~

Dữ liệu mỗi request đi qua tham số và giá trị trả về. Nếu có dependency được inject, vẫn phải xét tính thread-safe của dependency đó; một singleton stateless gọi một object mutable dùng chung thiếu đồng bộ **vẫn có thể không an toàn**. Đừng chuyển state từ field này sang một static field hoặc một shared cache chỉ để trông giống stateless.

### 5.2. Dùng immutable state khi cần chia sẻ cấu hình

Các đối tượng immutable (nội dung không thể thay đổi sau khi xây dựng) rất phù hợp để dùng chung, ví dụ String, record chứa thành phần immutable, hoặc một danh sách được sao chép phòng vệ rồi chỉ đọc. Hãy bảo đảm cả đối tượng lồng nhau cũng không bị thay đổi bởi nơi khác nếu invariant dựa vào chúng.

~~~java
public record FeePolicy(int percent, String currency) { }

// Cùng một FeePolicy bất biến có thể được nhiều request cùng đọc.
~~~

Nếu cần thay cả bộ cấu hình trong runtime, một cách là tạo **immutable snapshot** mới rồi thay tham chiếu bằng AtomicReference hoặc volatile, thay vì cập nhật lẻ tẻ nhiều field. Cần phân biệt atomic replacement của một reference với tính nguyên tử của nhiều tài nguyên độc lập.

### 5.3. Dùng nguyên tử và cấu trúc concurrent đúng thao tác

Một counter dùng chung có thể khai báo:

~~~java
private final AtomicLong requestCount = new AtomicLong();

public long nextNumber() {
    return requestCount.incrementAndGet();
}
~~~

AtomicLong phù hợp cho tăng, giảm, compare-and-set của **một giá trị** trong một JVM. Với thống kê tần suất ghi rất cao, LongAdder có thể giảm contention, nhưng **sum() không phải một snapshot nguyên tử** khi các cập nhật còn diễn ra. Không dùng nó cho số thứ tự cần duy nhất và tăng tuần tự từng lần gọi.

Với map được nhiều thread cập nhật, cân nhắc **ConcurrentHashMap** và các thao tác như compute, merge, putIfAbsent khi cần cập nhật một key một cách nguyên tử:

~~~java
private final ConcurrentHashMap<String, Long> counts =
        new ConcurrentHashMap<>();

public void record(String type) {
    counts.merge(type, 1L, Long::sum);
}
~~~

Đoạn trên là minh họa nằm trong class có import java.util.concurrent.ConcurrentHashMap. **ConcurrentHashMap** bảo vệ những thao tác được đặc tả của map; nó không làm cho một object mutable đặt trong value tự nhiên thread-safe, cũng không khiến nhiều thao tác trên nhiều key trở thành một transaction chung.

### 5.4. Dùng synchronized hoặc Lock khi có invariant nhiều bước

Khi cần kiểm tra điều kiện và cập nhật nhiều field theo một đơn vị logic, bảo vệ **cả vùng thao tác** bằng cùng một lock:

~~~java
public class BoundedInventory {
    private int available = 3;

    public synchronized boolean reserve() {
        if (available <= 0) {
            return false;
        }
        available--;
        return true;
    }

    public synchronized int remaining() {
        return available;
    }
}
~~~

Nếu mọi đường đọc/ghi **available** đều tuân thủ cùng monitor, invariant không bị hai thread tranh giành trong một instance. Có thể dùng ReentrantLock với try/finally và các tùy chọn lock khác khi thực sự cần. Khóa giúp bảo đảm tính đúng nhưng có chi phí về contention và có thể gây deadlock nếu khóa nhiều tài nguyên không nhất quán. Tránh giữ lock khi thực hiện I/O, gọi mạng hoặc chờ task khác nếu chưa đánh giá cẩn thận.

**@Transactional** là cơ chế transaction, không thay thế monitor lock trên field Java. Ngược lại, **synchronized** chỉ khóa một JVM/object, không tự cung cấp giao dịch hay điều phối dữ liệu trên nhiều bản sao ứng dụng.

### 5.5. Dữ liệu dùng chung giữa nhiều application instances phải đặt ở ranh giới phù hợp

Nếu chạy nhiều pod/instance, mỗi ApplicationContext có các singleton của riêng mình. AtomicInteger hay synchronized trong một instance **không làm bộ đếm chung của cluster** nhất quán. Với ví dụ số lượng hàng tồn, số thứ tự nghiệp vụ, hạn mức hay chống xử lý trùng, hãy dùng cơ chế thống nhất trên nguồn dữ liệu thực: transaction và cập nhật có điều kiện, optimistic/pessimistic locking của database, unique constraint, atomic operation của hệ lưu trữ phù hợp hoặc dịch vụ điều phối được thiết kế cho việc đó.

Đừng dùng static field hoặc Singleton pattern thuần Java như một "distributed lock": chúng cũng không vượt qua ranh giới tiến trình.

## 6. Scope khác có giải quyết thread-safety không?

| Scope/cơ chế | Thay đổi vòng đời hoặc việc cấp phát | Có tự bảo đảm thread-safe? |
| --- | --- | --- |
| **singleton** | Một bean instance cho một bean definition trong container | **Không** |
| **prototype** | Tạo instance mới mỗi lần container nhận yêu cầu lấy bean | **Không**; cùng prototype vẫn có thể bị chia sẻ sang nhiều thread |
| **request** | Một bean cho phạm vi HTTP request đang hoạt động (trong web context hỗ trợ) | **Không**; logic có thể có concurrent/asynchronous access trong request |
| **session** | Một bean theo HTTP session | **Không**; hai request của cùng session có thể đồng thời truy cập |
| **ObjectProvider<T>** | Cho phép yêu cầu bean theo nhu cầu, ví dụ lấy prototype mỗi lần | Chỉ giúp quản lý việc lấy instance; không tự đồng bộ state |

Một bẫy quan trọng: **inject prototype trực tiếp vào constructor của singleton không có nghĩa mỗi lần singleton gọi method sẽ tự được cấp prototype mới**. Dependency thường được resolve ở thời điểm xây dựng singleton và sau đó cùng tham chiếu được dùng lại; nếu cần mỗi lần lấy instance mới, dùng ObjectProvider, lookup method hoặc scoped proxy theo ngữ cảnh thích hợp.

Scope cũng không phải công cụ thay thế thiết kế dữ liệu: ngay cả một request-scoped bean vẫn có thể ghi vào một static map chung, gọi singleton mutable hoặc chia sẻ một object cho nhiều worker threads.

## 7. Các tương tác thường gây nhầm lẫn trong Spring Concurrency

**Spring MVC:** trong mô hình Servlet truyền thống, những request đồng thời có thể do những worker threads khác nhau xử lý. Singleton controller và service là những object được chia sẻ. Việc controller không tạo new service ở mỗi request là đặc tính của DI/scope, không phải bảo đảm serialization.

**@Async:** đưa lời gọi hợp lệ qua Spring proxy sang executor có thể tạo thêm luồng cùng dùng bean hoặc dependency. Nếu method @Async đọc/ghi field mutable của singleton, việc đổi sang async **làm tăng tình huống tranh chấp**, chứ không sửa race. Nếu cần dữ liệu của một request, truyền rõ dữ liệu cần thiết hoặc quản lý context propagation đúng cách.

**@Scheduled:** scheduler kích hoạt một method theo thời gian; cùng singleton cũng có thể được gọi từ nơi khác hoặc từ nhiều registrations. Không suy ra mỗi scheduled bean có "thread riêng" hoặc mọi lời gọi được khóa. Tính chồng lấp phụ thuộc scheduler, cách đăng ký, thời gian thực thi và việc có tách @Async hay không.

**@Transactional:** transaction thường gắn với luồng thực thi/cơ chế transaction manager và dữ liệu lưu trữ; nó không bảo vệ một biến int trong singleton khỏi hai luồng sửa đồng thời. Nếu hai transaction thay đổi cùng record, cần chiến lược concurrency control của data layer.

**ThreadLocal:** chứa dữ liệu theo thread, không phải theo Spring singleton hay tự động theo HTTP request. Với thread pool, thread có thể được tái sử dụng; cần quản lý set/remove và việc truyền context đúng cách. ThreadLocal không giải quyết race trên field shared, không thay thế việc truyền tham số, và không tự an toàn khi đổi thread bằng @Async.

**Reactive/WebFlux:** đừng mặc định xử lý luôn thuộc một thread cố định; callback có thể chạy trên các scheduler khác nhau. Một singleton với shared mutable state vẫn cần đồng bộ thích hợp, trong khi blocking lock/I/O trên event-loop có thể ảnh hưởng nghiêm trọng khả năng đáp ứng. Stateless và thiết kế không chặn thường là hướng ưu tiên.

## 8. Những lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân đáng kiểm tra | Hướng khắc phục |
| --- | --- | --- |
| Counter đôi khi tăng thiếu | **count++** trong singleton gặp lost update | AtomicLong/AtomicInteger hoặc lock bao toàn bộ cập nhật |
| Dữ liệu user A xuất hiện trong xử lý user B | Lưu currentUser/currentRequest vào field singleton | Truyền request data qua tham số hoặc dùng scope/context đúng vòng đời |
| HashMap/ArrayList lỗi hoặc thiếu phần tử khi truy cập đồng thời | Collection mutable được chia sẻ không có bảo vệ | Chọn concurrent collection và đúng atomic operations; hoặc lock |
| Tính tổng sai dù field đã dùng volatile | Volatile không làm thao tác ghép nguyên tử | Chuyển sang Atomic*, lock, hoặc thuật toán không có shared update |
| Tạo prototype nhưng vẫn bị race | Prototype bị inject một lần vào singleton hoặc reference được chia sẻ | Kiểm tra thời điểm lấy instance, ownership và giới hạn chia sẻ |
| Đồng bộ đã đúng trong một instance nhưng nhiều pod vẫn mất cập nhật | Khóa/atomic chỉ bảo vệ bộ nhớ một JVM | Đưa invariant sang transaction, unique constraint hoặc atomic operation ở shared store |
| Thỉnh thoảng đọc cấu hình chắp vá | Ghi nhiều field riêng lẻ khi thread khác đọc | Dùng một immutable snapshot thay nguyên tử hoặc bảo vệ nhiều field cùng lock |

Lỗi khác dễ bỏ qua là gọi dependency *không* thread-safe từ singleton. Ví dụ chia sẻ một SimpleDateFormat mutable hoặc một buffer StringBuilder giữa các request mà không bảo vệ. Không phải mọi dependency đều cần khóa: trước hết xem tài liệu thread-safety và cách sử dụng thực tế của nó.

## 9. Cách kiểm tra một singleton trước khi đưa vào môi trường đồng thời

1. **Xác định instance và callers.** Bean thuộc context nào? Có bao nhiêu nơi inject? Có request, scheduler, listener, async task hoặc callback nào gọi đồng thời không?
2. **Liệt kê shared state.** Field mutable của chính bean, object mutable qua dependency, static state, cache, collection, biến cấu hình có thể reload.
3. **Phân tích thao tác.** Chỉ đọc? Một giá trị cần atomic update? Một invariant trải trên nhiều field? Có cơ chế publication/happens-before giữa writer và reader không?
4. **Chọn chiến lược phù hợp.** Ưu tiên stateless/immutable, tiếp theo Atomic* hoặc concurrent collection; khi invariant nhiều bước thì dùng cùng lock hoặc transaction của tầng dữ liệu tương ứng.
5. **Kiểm tra bằng tình huống đồng thời thật.** Dùng CountDownLatch, CyclicBarrier hay lệnh gọi đồng thời để ép interleaving và assert invariant; bổ sung stress test nếu cần. Một test chạy qua một lần **không chứng minh** không có race; test ngẫu nhiên nhiều lần cũng không thay thế phân tích bộ nhớ và khóa.
6. **Quan sát production.** Đo tỉ lệ lỗi, số lần retry, queue, độ trễ và các invariant bị vi phạm. Phân biệt lỗi concurrency trong JVM với xung đột dữ liệu ở database hoặc nhiều instance.

Khi một method chỉ trả về kết quả tính toán từ input độc lập, không giữ state theo request, đó thường là trường hợp singleton dễ dùng an toàn nhất. Khi nó phải lưu shared state, hãy làm rành mạch **ai sở hữu dữ liệu, thao tác nào nguyên tử và phạm vi bảo vệ có bao trùm tất cả callers hay không**.

## 10. Câu hỏi tự kiểm tra

1. Hai controller khác nhau inject cùng một singleton service. Chúng có dùng hai đối tượng service độc lập không? **Không**, nếu cả hai resolve cùng bean definition trong cùng container.
2. Đánh dấu **volatile int count** rồi gọi **count++** từ hai threads có bảo đảm kết quả tăng đúng hai lần không? **Không**, vì read-modify-write không nguyên tử.
3. Thay singleton bằng prototype có tự động sửa lỗi nếu cùng một object được chia sẻ cho nhiều worker không? **Không**. Scope điều khiển cách container tạo/cấp phát bean, không tự khóa thao tác.
4. Một singleton hoàn toàn stateless nhưng gọi chung một HashMap mutable thiếu bảo vệ: có chắc thread-safe không? **Không**. Phải xét cả dependency và shared objects.
5. Thêm **@Transactional** quanh method cập nhật field có chặn hai threads thay đổi field ấy cùng lúc không? **Không**. Transaction không phải monitor lock cho bộ nhớ Java.
6. AtomicInteger trong một instance có bảo đảm một business counter tăng đúng trên ba pod không? **Không**. Cần đặt quy tắc nhất quán tại hệ lưu trữ/điều phối chung.

**Ghi nhớ:** singleton quyết định **chia sẻ instance**, còn thread-safety quyết định **chia sẻ có đúng đắn khi thao tác xen kẽ hay không**. Spring cung cấp container, proxy và các tích hợp concurrency, nhưng thiết kế về mutable state, atomicity và invariant vẫn thuộc về mã ứng dụng.

Tài liệu tham khảo chính thức:
- [Spring Framework — Bean Scopes, Singleton Scope](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html#beans-factory-scopes-singleton)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Java SE — AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-state-002">Biến cục bộ, trường đối tượng và dữ liệu dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Biến cục bộ, trường đối tượng và dữ liệu dùng chung trong Spring bean

## 1. Vì sao trạng thái bean quan trọng khi có nhiều luồng?

Một ứng dụng Spring MVC thường nhận nhiều HTTP request cùng lúc. Mỗi request được servlet container phục vụ bởi một luồng; các luồng có thể đồng thời gọi **cùng một đối tượng** `@RestController`, `@Service` hoặc `@Repository`. Theo mặc định, bean Spring có scope `singleton`: Spring tạo **một instance cho mỗi bean definition trong mỗi `ApplicationContext`** và đưa cùng instance đó cho các thành phần cần dùng.

Điều này **không khiến mọi biến trong bean đều dùng chung**. Phải phân biệt:

- **Biến cục bộ (local variable):** được khai báo trong phương thức/khối; mỗi lần gọi phương thức có bộ biến cục bộ riêng. Giá trị nguyên thủy cục bộ không bị request khác sửa trực tiếp.
- **Trường đối tượng (instance field):** thuộc về một instance. Nếu đó là singleton bean, mọi luồng gọi bean đều nhìn thấy cùng trường ấy; trường có thể an toàn khi bất biến, hoặc có rủi ro khi bị cập nhật đồng thời.
- **Dữ liệu dùng chung (shared data):** bất kỳ đối tượng/trạng thái nào mà nhiều luồng cùng truy cập: có thể là field, đối tượng được tham chiếu từ local variable, static field, cache, collection, database, Redis hoặc file. Nơi khai báo **không quyết định toàn bộ** việc dữ liệu có được chia sẻ hay không.

Ví dụ: hai request cùng gọi `reportService.generate(id)`. Mỗi lời gọi có biến cục bộ `id` riêng, nhưng cả hai cùng gọi trên instance `reportService`. Nếu service giữ `private String currentReportId` và sửa field mỗi request, hai request có thể ghi đè nhau. Nếu service chỉ đọc một cấu hình bất biến và truyền `id` qua tham số xuống repository thì không có trạng thái nghiệp vụ dùng chung trong field.

### Bảng nhận diện trạng thái

| Vị trí dữ liệu | Thuộc về ai? | Có thể bị luồng khác chạm tới? | Thiết kế thường dùng |
| --- | --- | --- | --- |
| `int total` bên trong phương thức | Một lần gọi phương thức | Không đối với chính biến cục bộ ấy | Tính toán tạm thời |
| `List<Item> items` cục bộ | Biến tham chiếu của một lần gọi | **Có**, nếu list được lấy từ field/cache hoặc truyền ra ngoài | Xác định nguồn và ownership của đối tượng được tham chiếu |
| `private final Config config` trong singleton | Một instance bean | Cùng đối tượng được mọi lời gọi tham chiếu | Cấu hình bất biến, xây xong trước khi phục vụ request |
| `private long count` trong singleton | Một instance bean | **Có**, khi các request đọc/ghi | Không dùng `++` không đồng bộ; dùng atomic/lock khi phù hợp |
| `private static Map<...> cache` | Class trong classloader | Có thể chia sẻ qua nhiều bean instance | Quản lý an toàn như mọi trạng thái dùng chung khác |
| Hàng dữ liệu trong DB/Redis | Kho ngoài tiến trình | Có thể chia sẻ giữa **nhiều instance ứng dụng** | Transaction, khóa/điều kiện version, idempotency |

**Lưu ý về JVM:** cách nói “local ở stack, object ở heap” có ích để hình dung nhưng không nên dùng làm tiêu chuẩn an toàn luồng. Một local reference nằm trong lần gọi riêng nhưng vẫn trỏ đến *cùng object* trên heap; đối tượng ấy có thể thoát khỏi phương thức và được nhiều luồng dùng. JVM cũng có các tối ưu hóa nội bộ, nên phân tích theo **ai sở hữu và ai có thể truy cập object** mới chính xác.

## 2. Ví dụ sai: lưu trạng thái từng request vào field singleton

~~~java
import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    // Demo anti-pattern: request này có thể ghi đè request khác.
    private String currentUser;

    public String greet(String user) {
        currentUser = user;
        // Giữa hai dòng, luồng khác có thể sửa currentUser.
        return "Xin chào " + currentUser;
    }
}
~~~

Nếu request A gọi `greet("Mai")` và tạm dừng sau phép gán, request B gọi `greet("Linh")` rồi gán vào `currentUser`, khi A chạy tiếp nó **có thể** trả “Xin chào Linh”. Không cần có hai controller instance; chính việc chia sẻ một `GreetingService` singleton là đủ để tạo race. Đoạn trên minh họa một service class, chưa phải chương trình Java độc lập.

Thay bằng thiết kế **không giữ dữ liệu request trong field**:

~~~java
import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public String greet(String user) {
        String normalized = user.strip(); // Cục bộ cho mỗi invocation.
        return "Xin chào " + normalized;
    }
}
~~~

Các lời gọi không ghi vào trạng thái chung. Nhưng nếu `user` được thay bằng một `List` hoặc DTO mutable lấy từ cache, local reference **không sao chép** đối tượng. Ví dụ sau vẫn sửa list chung:

~~~java
// Trích đoạn minh họa trong một phương thức (không độc lập).
List<String> localAlias = sharedList; // sharedList là field.
localAlias.add("report");             // Thực chất sửa sharedList.
~~~

Với dữ liệu cần chỉnh sửa, chọn rõ một trong các hướng: tạo bản sao mỗi request, dùng giá trị bất biến, đồng bộ hóa truy cập, hoặc thực hiện cập nhật nguyên tử tại kho dữ liệu.

## 3. Race condition và Java Memory Model

### 3.1. `count++` gồm nhiều bước

Lệnh `count++` tương đương về logic với đọc giá trị, cộng một, rồi ghi lại. Hai luồng có thể cùng đọc 0 và cùng ghi 1; đáng lẽ sau hai lần tăng là 2 nhưng thực tế chỉ còn 1 (**lost update**). `private` không ngăn hai phương thức của cùng singleton truy cập field; `final` chỉ cấm gán lại field tham chiếu chứ không tự bảo vệ nội dung của object; `volatile` bảo đảm một số quan hệ *visibility/ordering*, **không biến `count++` thành phép tăng nguyên tử**.

Ví dụ Java 17 độc lập sau chủ động dùng hai latch để ép hai luồng cùng đọc giá trị ban đầu, nhờ đó kết quả rõ ràng chứ không dựa vào may rủi lập lịch. Lưu thành `LostUpdateDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public class LostUpdateDemo {
    static class Counter {
        private int value;
        final CountDownLatch bothRead = new CountDownLatch(2);
        final CountDownLatch continueWriting = new CountDownLatch(1);

        void increment() {
            int snapshot = value;  // Cả hai đọc 0 trước khi ghi.
            bothRead.countDown();
            try {
                continueWriting.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            value = snapshot + 1;
        }

        int get() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        Thread first = new Thread(counter::increment);
        Thread second = new Thread(counter::increment);

        first.start();
        second.start();
        counter.bothRead.await();
        counter.continueWriting.countDown();
        first.join();
        second.join();

        System.out.println("Expected = 2");
        System.out.println("Actual   = " + counter.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LostUpdateDemo.java
java LostUpdateDemo
~~~

Stdout chính xác:

~~~text
Expected = 2
Actual   = 1
~~~

Trong ứng dụng thật, thứ tự chạy không được ép như ví dụ, vì vậy một phép thử tuần tự có thể **không lộ ra lỗi**. Hai `join()` trong demo bảo đảm main đọc kết quả sau khi các luồng kết thúc. Hai latch chỉ giúp tái hiện lost update; chúng không phải cách xây counter production.

### 3.2. Phân biệt ba vấn đề

- **Atomicity (nguyên tử):** một thao tác gồm nhiều bước không bị xen vào theo cách tạo trạng thái sai. `AtomicLong.incrementAndGet()` thực hiện phép tăng nguyên tử cho *một* biến.
- **Visibility (khả năng nhìn thấy):** thay đổi do luồng A thực hiện trở nên quan sát được ở luồng B theo quy tắc *happens-before* của Java Memory Model (JMM); `volatile`, monitor lock, atomics và một số cơ chế đồng bộ tạo ra quan hệ cần thiết.
- **Ordering (thứ tự):** compiler và CPU có thể tối ưu sắp xếp thao tác miễn giữ ngữ nghĩa được JMM cho phép; đồng bộ đúng tạo các ràng buộc thứ tự cần thiết.

`synchronized` cung cấp loại trừ tương hỗ và quan hệ visibility khi dùng **cùng một monitor**. Atomic classes phù hợp với cập nhật độc lập như bộ đếm. `volatile boolean shutdownRequested` phù hợp với tín hiệu một luồng ghi/luồng khác đọc, nhưng không giải quyết phép cập nhật ghép như “kiểm tra rồi thêm” trên nhiều field.

## 4. Ví dụ Spring Boot 3: đặt local, field và request-scoped state đúng chỗ

Ví dụ có thể chạy bằng **Java 17+ và Maven**, dùng Spring Boot 3.3.5 để khởi động web server. Chỉ định nghĩa field unsafe ở đây **để quan sát lỗi**, không dùng endpoint đó cho số liệu production.

Cấu trúc:

~~~text
spring-bean-state/
├── pom.xml
└── src/main/java/com/example/beanstate/
    ├── BeanStateApplication.java
    ├── CounterService.java
    ├── RequestStamp.java
    └── VisitController.java
~~~

### 4.1. `pom.xml`

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
    <artifactId>spring-bean-state</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
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

### 4.2. Boot application

`src/main/java/com/example/beanstate/BeanStateApplication.java`:

~~~java
package com.example.beanstate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BeanStateApplication {
    public static void main(String[] args) {
        SpringApplication.run(BeanStateApplication.class, args);
    }
}
~~~

### 4.3. Một singleton chứa cả field an toàn và không an toàn

`src/main/java/com/example/beanstate/CounterService.java`:

~~~java
package com.example.beanstate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class CounterService {
    // Chỉ để minh họa race: đọc/cộng/ghi không nguyên tử.
    private long unsafeTotal;

    // Cùng là field singleton, nhưng phép tăng là nguyên tử.
    private final AtomicLong safeTotal = new AtomicLong();

    // Dữ liệu chỉ đọc, giá trị String bất biến.
    private final Map<String, String> labels = Map.of(
            "report", "Báo cáo",
            "sync", "Đồng bộ"
    );

    public long unsafeIncrement() {
        return ++unsafeTotal;
    }

    public long safeIncrement() {
        return safeTotal.incrementAndGet();
    }

    public String labelFor(String type) {
        return labels.getOrDefault(type, "Khác");
    }
}
~~~

Việc cùng singleton chia sẻ `safeTotal` là **có chủ đích**: mọi request tăng cùng bộ đếm và `AtomicLong` bảo vệ phép tăng. `final` bảo đảm không gán lại tham chiếu `safeTotal`; chính `AtomicLong` mới bảo vệ cập nhật. `labels` không bị sửa sau khi tạo và dùng `Map.of` với các `String` bất biến, nên có thể đọc đồng thời an toàn khi bean được Spring khởi tạo và công bố bình thường.

### 4.4. Một bean mới theo mỗi HTTP request

`src/main/java/com/example/beanstate/RequestStamp.java`:

~~~java
package com.example.beanstate;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestStamp {
    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}
~~~

`@RequestScope` tạo đối tượng `RequestStamp` mới cho mỗi HTTP request. Annotation này mặc định sử dụng scoped proxy, vì thế singleton controller có thể inject `RequestStamp`: thực chất nó giữ **proxy**, còn proxy tìm đối tượng thuộc request hiện tại khi được gọi. Hai lần gọi `id()` trong cùng request cho cùng giá trị; hai request mới gần như chắc chắn có ID khác nhau.

`@RequestScope` không mặc nhiên khả dụng ở luồng chạy `@Async`, `@Scheduled` hoặc trong một test không có HTTP request context. Trước khi rời luồng HTTP, hãy trích xuất dữ liệu cụ thể (ví dụ request ID) rồi truyền bằng tham số cho worker.

### 4.5. Controller singleton nhưng local thuộc từng lần gọi

`src/main/java/com/example/beanstate/VisitController.java`:

~~~java
package com.example.beanstate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisitController {
    private final CounterService counters;
    private final RequestStamp requestStamp;

    public VisitController(CounterService counters, RequestStamp requestStamp) {
        this.counters = counters;
        this.requestStamp = requestStamp;
    }

    @GetMapping("/visit")
    public Map<String, Object> visit(
            @RequestParam(defaultValue = "Mai") String name) {
        String normalized = name.strip().toLowerCase(Locale.ROOT);
        int length = normalized.length();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", normalized);
        result.put("length", length);
        result.put("requestId", requestStamp.id());
        result.put("label", counters.labelFor("report"));
        return result;
    }

    @GetMapping("/counter/unsafe")
    public Map<String, Long> unsafe() {
        return Map.of("value", counters.unsafeIncrement());
    }

    @GetMapping("/counter/safe")
    public Map<String, Long> safe() {
        return Map.of("value", counters.safeIncrement());
    }
}
~~~

`normalized`, `length` và `result` là local của từng lần gọi `visit()`; mỗi request tạo map mới. `counters` là field của controller, nhưng nó tham chiếu cùng service singleton; `requestStamp` cũng là field nhưng tham chiếu **scoped proxy** phục vụ dữ liệu riêng của mỗi request. Không thể nhìn mỗi chữ `private` hoặc `final` mà kết luận object được tham chiếu luôn thread-safe.

### 4.6. Chạy và quan sát

Trong thư mục chứa `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Trong terminal khác:

~~~bash
curl "http://localhost:8080/visit?name=%20Mai%20"
curl "http://localhost:8080/visit?name=%20Mai%20"
curl http://localhost:8080/counter/safe
curl http://localhost:8080/counter/safe
curl http://localhost:8080/counter/unsafe
~~~

Phản hồi của `/visit` có dạng:

~~~json
{
  "name": "mai",
  "length": 3,
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "label": "Báo cáo"
}
~~~

UUID ở đây chỉ là **ví dụ**; mỗi request nhận ID mới. Trong một ứng dụng mới khởi động, không có request nào khác, hai lần gọi tuần tự đầu tiên đến `/counter/safe` sẽ trả `{"value":1}` rồi `{"value":2}`. Bộ đếm unsafe cũng *có thể* cho kết quả đúng khi gọi tuần tự; lỗi mất cập nhật chỉ xuất hiện dưới các lịch xen kẽ nhất định. Hai bộ đếm bắt đầu độc lập từ 0 và mất trạng thái khi khởi động lại.

Nếu chạy nhiều request song song, `unsafeTotal` có thể bỏ sót lần tăng, trong khi `safeTotal.incrementAndGet()` không mất lần tăng **trong cùng instance**. Không dùng giá trị trả về của endpoint unsafe như một metric có độ tin cậy. Thử nghiệm hai latch ở mục 3 cung cấp bằng chứng lost update có kết quả xác định.

## 5. Chọn chiến lược phù hợp với dạng trạng thái

### 5.1. Ưu tiên singleton stateless và immutable input/output

Thiết kế dễ bảo trì nhất là bean giữ những dependency/cấu hình dùng chung, còn dữ liệu thay đổi theo request nằm trong tham số, local variable, DTO bất biến hoặc DB. Với Java 17, có thể dùng `record` cho kết quả, ví dụ `record ReportResult(String id, long total) {}`. Record chỉ bảo đảm các thành phần tham chiếu là `final`; nếu chứa `List` mutable thì **không tự trở thành deeply immutable**. Dùng `List.copyOf`, `Map.copyOf` và các phần tử bất biến khi cần snapshot thực sự.

### 5.2. Khi cần counter hoặc cờ trạng thái trong một instance

| Nhu cầu | Công cụ thường dùng | Giới hạn |
| --- | --- | --- |
| Tăng ID/counter của **một instance** | `AtomicLong.incrementAndGet()` | Không chia sẻ qua các pod, mất khi restart |
| Thu thập metric đếm tần suất ghi lớn | `LongAdder.increment()` | `sum()` dưới tải đồng thời không phải snapshot nguyên tử |
| Cờ bật/tắt một luồng quan sát | `volatile boolean` hoặc `AtomicBoolean` | Không bảo vệ đồng thời nhiều trường |
| Cập nhật nhiều trường phải nhất quán | `synchronized`, `ReentrantLock` hoặc immutable snapshot thay nguyên tử | Khóa chỉ trong tiến trình/instance |
| Map cùng sửa từ nhiều luồng | `ConcurrentHashMap`, `compute`, `putIfAbsent` | Các thao tác nhiều bước ngoài map vẫn cần phối hợp |

Ví dụ bộ đếm theo loại công việc trong cùng một service (đoạn phương thức không độc lập):

~~~java
private final java.util.concurrent.ConcurrentMap<String,
        java.util.concurrent.atomic.AtomicLong> byType =
        new java.util.concurrent.ConcurrentHashMap<>();

public long record(String type) {
    return byType.computeIfAbsent(type,
            ignored -> new java.util.concurrent.atomic.AtomicLong())
            .incrementAndGet();
}
~~~

`ConcurrentHashMap` bảo vệ cấu trúc map và `AtomicLong` bảo vệ biến đếm của từng key. Đừng thay bằng mẫu `if (!map.containsKey(k)) map.put(k, new Counter())` nếu nhiều luồng có thể cùng khởi tạo một key. Khi một cập nhật phải sửa *nhiều thuộc tính liên quan*, các atomic riêng lẻ không bảo đảm một transaction tổng thể: cân nhắc lock hoặc cấu trúc trạng thái bất biến được thay nguyên tử.

### 5.3. Snapshot dữ liệu dùng chung kiểu đọc nhiều/ghi ít

Để công bố một bộ cấu hình đã xây dựng đầy đủ cho nhiều luồng đọc, có thể thay **cả tham chiếu**:

~~~java
// Trích đoạn nằm trong class quản lý cấu hình.
private volatile Map<String, String> snapshot = Map.of();

public void replaceSnapshot(Map<String, String> next) {
    snapshot = Map.copyOf(next);
}

public String find(String key) {
    return snapshot.get(key);
}
~~~

Với `String` key/value bất biến, từng lần `find` thấy một snapshot nhất quán. `volatile` hỗ trợ visibility khi thay tham chiếu; `Map.copyOf` tạo bản sao không thể sửa qua API của map. Nhưng hai writer cùng dựa vào snapshot cũ có thể ghi đè lần thay của nhau; cần version, CAS hoặc lock nếu phép thay phải kiểm tra điều kiện. Nếu giá trị là object mutable, sao chép map **không tự sao chép sâu** các giá trị.

### 5.4. Scope không thay thế bảo đảm an toàn luồng

| Spring scope | Vòng đời | Cần chú ý |
| --- | --- | --- |
| `singleton` (mặc định) | Một instance mỗi bean definition trong context | Có thể bị nhiều request gọi đồng thời; tốt nhất stateless |
| `prototype` | Spring tạo bean khi được yêu cầu | Inject trực tiếp vào singleton thường **chỉ tạo một lần tại lúc inject**; cần `ObjectProvider<T>` hoặc provider khi muốn lấy instance mới mỗi lần |
| `request` | Một instance mỗi HTTP request có scope hoạt động | Phù hợp request state; có thể không tồn tại trên luồng async/scheduled |
| `session` | Một instance mỗi HTTP session | Hai tab hoặc hai request cùng session vẫn có thể chạy đồng thời; mutable session state vẫn cần chú ý |

Scope kiểm soát **vòng đời và cách tra cứu bean**, không mang lại tự động các bảo đảm atomicity, visibility, transaction hay đồng bộ giữa nhiều instance. `@Scope("prototype")` cũng không có nghĩa Spring tự dọn mọi tài nguyên của prototype; thành phần sử dụng phải quản lý vòng đời thích hợp.

### 5.5. `ThreadLocal` là trạng thái gắn với luồng, không đồng nghĩa request

`ThreadLocal<T>` giữ giá trị riêng theo **Java thread**. Servlet container và `TaskExecutor` thường tái sử dụng luồng cho nhiều request/công việc, nên một giá trị quên xóa có thể rò sang lần xử lý sau. Nếu thật sự dùng, cần `remove()` trong `finally`:

~~~java
// Trích đoạn minh họa, không phải chương trình độc lập.
private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

void withTrace(String traceId, Runnable work) {
    TRACE_ID.set(traceId);
    try {
        work.run();
    } finally {
        TRACE_ID.remove();
    }
}
~~~

Không dùng `ThreadLocal` để thay database cho trạng thái nghiệp vụ; cũng không giả định giá trị `ThreadLocal` tự đi theo sang luồng `@Async` hoặc `CompletableFuture`. Logging MDC, SecurityContext và transaction context thường gắn với luồng và cần cơ chế propagation được cấu hình phù hợp; cách đơn giản nhất cho job nền là truyền dữ liệu thiết yếu như ID qua tham số.

## 6. Dữ liệu dùng chung vượt khỏi một bean và một JVM

Dữ liệu trong Java field, `AtomicLong` hay `synchronized` chỉ bảo vệ tình huống **trong một instance/proccess** như đã thiết kế. Nếu triển khai 3 pod, mỗi pod có `CounterService` và `safeTotal` riêng; client được load balancer chuyển qua pod khác có thể thấy giá trị đếm khác. `static` cũng không làm cho biến trở thành trạng thái phân tán.

Khi cần một tổng số đơn hàng nhất quán trên toàn hệ thống, lưu tổng trong DB hoặc kho phù hợp và bảo đảm cập nhật nguyên tử ở đó. Ví dụ SQL đơn giản cho một dòng:

~~~sql
UPDATE report_stats
SET processed_count = processed_count + 1
WHERE id = 1;
~~~

Trong một DB giao dịch phù hợp, câu UPDATE được thực thi với cơ chế đồng thời của DB, thay vì đọc số lên Java rồi ghi đè bằng giá trị cũ. Với nghiệp vụ kiểm tra số dư, tồn kho, hoặc thay đổi nhiều dòng, cần transaction, điều kiện cập nhật, isolation/locking hoặc optimistic version tùy quy tắc dữ liệu; `synchronized` của một bean không khóa được các process khác.

Với JPA, entity managed và `EntityManager` gắn với persistence context/transaction; **không lưu entity mutable đang managed hoặc `EntityManager` như trạng thái tùy ý chia sẻ xuyên request**. Spring Data repository có thể là singleton proxy nhưng operations phải dùng đúng transaction/persistence context, khác với việc tự giữ một object JPA dùng chung. Cần phân biệt cơ chế đồng bộ trong Java với giao dịch và isolation ở database.

## 7. Những hiểu lầm phổ biến và cách kiểm tra

| Hiểu lầm/triệu chứng | Điều cần kiểm tra | Cách xử lý |
| --- | --- | --- |
| “Controller là singleton nên local bị request khác sửa” | Local thuộc từng invocation; có tham chiếu đến object shared hay không? | Giữ dữ liệu request trong local/DTO, không lưu `currentUser` vào field |
| “Field `private final List` là thread-safe” | `final` chỉ cố định tham chiếu, `List` có còn bị mutate? | Dùng collection bất biến hoặc cơ chế đồng bộ thích hợp |
| “Đặt `volatile int counter` là đủ” | Có phép `++` hoặc check-then-act không? | `AtomicInteger`, `synchronized` hoặc update DB nguyên tử |
| “`ConcurrentHashMap` giúp mọi thao tác an toàn” | Có quy trình `get → sửa object → put` gồm nhiều bước? | Dùng `compute`, immutable value, lock/transaction phù hợp |
| “`@RequestScope` chạy trong mọi worker” | Worker có request context đang active không? | Truyền dữ liệu request cần thiết vào tác vụ |
| “`prototype` được inject mỗi lời gọi” | Bean prototype được inject trực tiếp vào singleton khi nào? | Dùng provider mỗi khi cần bean mới |
| “Một counter đúng trên dev sẽ đúng trên cluster” | Có nhiều process/pod hoặc restart không? | Dùng kho chia sẻ, transaction/version, idempotency |
| “Test 10 request đều qua nên không có race” | Test có chạy thực sự đồng thời và có xác minh invariant không? | Thử concurrency có kiểm soát/latches, stress test, theo dõi lỗi |

### Cách tự đánh giá một bean

1. Liệt kê từng **instance/static field**: ai gán, khi nào, có sửa sau khi startup không, object được tham chiếu có mutable không?
2. Với mỗi public method, phân biệt **dữ liệu riêng của lời gọi** với những tham chiếu thoát ra service/cache/entity bên ngoài.
3. Đánh dấu mọi thao tác “đọc → kiểm tra → ghi”, “nếu chưa có thì thêm”, “tăng bộ đếm”: chúng có cần nguyên tử không?
4. Xác định phạm vi chia sẻ: **một lời gọi**, **một request**, **một singleton trong một context**, **một JVM**, hay **toàn cụm**.
5. Chọn biện pháp nhỏ nhất đáp ứng invariant: local/immutable trước; sau đó atomic, lock hoặc DB transaction khi thực sự cần. Đo hiệu năng nếu dùng lock trên đường xử lý nóng.
6. Kiểm thử nhiều request cùng lúc; xác minh trạng thái cuối, không chỉ mã HTTP. Thử chạy nhiều instance nếu dữ liệu cần đồng bộ toàn cụm.

### Tự kiểm tra kiến thức

- Một singleton `@Service` chỉ có các dependency `final` và phương thức dùng local variable đã chắc chắn an toàn luồng chưa? Những dependency đó có thể tự giữ mutable state không?
- Biến local `List<String> copied = cache.get(key)` có chắc là danh sách riêng? Phải làm gì nếu cần thay đổi danh sách?
- Vì sao `volatile long total; total++;` vẫn có thể mất lần tăng, còn `AtomicLong.incrementAndGet()` thì không?
- `@RequestScope` và `@SessionScope` khác nhau thế nào khi hai request cùng đến? Scope nào bảo đảm tự động thread-safe?
- Khi chuyển từ một instance sang ba pod, vì sao counter trong bean không còn là bộ đếm toàn hệ thống? Thiết kế DB/Redis cần thêm điều kiện gì để retry không tính trùng?

**Quy tắc thiết kế:** giữ Spring singleton bean càng *stateless* càng tốt; phân biệt biến cục bộ với **object mà biến đó trỏ tới**; bất kỳ mutable state nào mà nhiều luồng có thể chạm tới đều phải có ownership và cơ chế phối hợp rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-state-003">Đối tượng bất biến và công bố dữ liệu an toàn</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng bất biến và công bố dữ liệu an toàn trong Spring Framework

Một bean `@Service` có thể chỉ có một instance trong Spring container nhưng được nhiều request, tác vụ `@Async` hoặc job `@Scheduled` gọi đồng thời. Câu hỏi then chốt không chỉ là “bean có `singleton` không?”, mà còn là: **trạng thái nào được chia sẻ, trạng thái đó có thể thay đổi không, và thread đọc có chắc nhìn thấy dữ liệu đã khởi tạo hoặc cập nhật đúng cách không?**

Bài học này tập trung vào **đối tượng bất biến (immutable object)** và **công bố dữ liệu an toàn (safe publication)** khi thiết kế trạng thái bean trong Spring. Nền tảng lý thuyết là **Java Memory Model (JMM)**, đặc biệt các quy tắc `happens-before` và ngữ nghĩa của trường `final`. Mục tiêu thực hành là xây dựng bean có state dễ suy luận, cập nhật an toàn và tránh phụ thuộc vào may rủi của việc thread chạy theo thứ tự nào.

## 1. Bean scope không đồng nghĩa thread safety

Spring mặc định dùng scope `singleton`: **một đối tượng cho một bean definition trong một IoC container**. Nhiều controller, request và worker có thể tham chiếu cùng bean đó. Đây không phải “một instance duy nhất trong toàn bộ JVM” và càng không bảo đảm trạng thái thay đổi bên trong bean tự động thread-safe.

Ví dụ một service sai:

```java
import org.springframework.stereotype.Service;

@Service
public class UnsafeGreetingService {
    private String lastName;

    public String greet(String name) {
        lastName = name;
        return "Hello, " + lastName;
    }
}
```

Giả sử request A gọi `greet("An")`, request B gọi `greet("Binh")`. Nếu A gán `lastName = "An"`, B gán `lastName = "Binh"` rồi A mới đọc, A có thể trả lời `Hello, Binh`. Đây là **race condition** trên instance field chia sẻ. Không nên dựa vào việc một test đơn luồng “chạy đúng” để suy ra ứng dụng web an toàn.

Phiên bản tốt hơn cho dữ liệu chỉ phục vụ một lời gọi:

```java
@Service
public class GreetingService {
    public String greet(String name) {
        String currentName = name; // Biến local, riêng cho mỗi lời gọi
        return "Hello, " + currentName;
    }
}
```

Không có lý do nghiệp vụ để giữ tên người dùng ở instance field. **Giữ request state trong biến local** là cách đơn giản nhất giảm shared mutable state. Nếu phải giữ trạng thái lâu dài, lựa chọn cấu trúc dữ liệu và cơ chế công bố/cập nhật là phần trách nhiệm của ứng dụng.

## 2. Immutable object là gì?

Một đối tượng **bất biến** có trạng thái quan sát được không thay đổi sau khi khởi tạo hợp lệ. Khi nhiều thread cùng đọc một đối tượng thực sự bất biến, chúng không cần khóa chỉ để bảo vệ các thao tác đọc.

Trong Java, một thiết kế immutable điển hình có các đặc điểm:

1. Toàn bộ trạng thái logic được xác lập khi xây dựng đối tượng; sau đó không có phương thức nào sửa nó.
2. Dùng trường `private final` khi thích hợp và không để tham chiếu `this` thoát ra ngoài trong quá trình constructor chạy.
3. Không giữ trực tiếp tham chiếu đến dữ liệu mutable do caller sở hữu nếu caller còn sửa được; dùng **defensive copy**.
4. Không trả trực tiếp cấu trúc mutable bên trong cho caller sửa; dùng bản sao hoặc cấu trúc immutable.
5. Kiểm soát tính kế thừa nếu subclass có thể thêm hành vi khiến hợp đồng bất biến bị phá vỡ, chẳng hạn dùng `final class` hoặc `record` cho value object phù hợp.

Ví dụ **shallow immutability** không đủ:

```java
final class WeakPolicy {
    private final java.util.Map<String, String> values;

    WeakPolicy(java.util.Map<String, String> values) {
        this.values = values; // Giữ tham chiếu mutable của caller
    }

    java.util.Map<String, String> values() {
        return values; // Trả lại cùng Map mutable
    }
}
```

`final` ở đây chỉ ngăn **gán lại trường `values`**; nó không ngăn `values.put(...)` thay đổi nội dung của Map. Một thread đang đọc có thể đụng race với thread khác sửa Map, và `HashMap` không được thiết kế cho truy cập đọc/ghi đồng thời thiếu phối hợp.

Thiết kế tốt hơn khi key/value đều immutable:

```java
import java.util.Map;
import java.util.Objects;

public record Policy(String name, Map<String, String> values) {
    public Policy {
        Objects.requireNonNull(name, "name");
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }
}
```

Khi tạo `new Policy("standard", originalMap)`, constructor chụp một bản sao bất biến của các entry; thay đổi `originalMap` sau đó không thay đổi `Policy`. Accessor `values()` trả Map không cho sửa nội dung bằng `put` hoặc `remove`.

**Lưu ý về sao chép nông:** `Map.copyOf` ngăn sửa cấu trúc Map và tách trạng thái Map khỏi các thao tác thay đổi trên Map gốc, nhưng **không tự biến các object value thành immutable**. Với `Map<String, List<String>>`, một `List` mutable bên trong vẫn có thể bị sửa từ tham chiếu khác; phải sao chép từng value hoặc dùng mô hình bất biến đến tận các lớp dữ liệu cần bảo vệ. `List.copyOf` và `Set.copyOf` cũng có giới hạn tương tự. Các phương thức `copyOf` từ chối phần tử null không hợp lệ theo hợp đồng của chúng.

### Record và final không phải “bùa thread-safe”

- `record Person(String name, List<String> tags)` tự sinh các field `final`, nhưng `tags` có thể vẫn là List mutable; record **không tự động deep copy**.
- `final List<String> tags = new ArrayList<>()` là tham chiếu ổn định tới một danh sách có thể thay đổi.
- `Collections.unmodifiableList(source)` tạo **view không sửa được qua view**, nhưng nếu thread khác sửa `source`, nội dung view vẫn thay đổi. Muốn snapshot độc lập, ưu tiên `List.copyOf(source)` khi các phần tử đáp ứng yêu cầu.
- `String`, `LocalDate`, `BigDecimal` là những ví dụ đối tượng bất biến hữu ích cho value object. Với mảng `byte[]`, `Date` cũ hoặc DTO mutable, cần cách ly dữ liệu.

Hợp đồng quan trọng là **không có đường nào cho thread khác thay đổi trạng thái quan sát được**, chứ không chỉ kiểm tra số lần xuất hiện của từ khóa `final`.

## 3. Công bố một đối tượng là gì?

**Publication (công bố)** là lúc một tham chiếu tới object trở nên truy cập được từ thread khác. Ví dụ: đưa vào field của singleton bean, cache dùng chung, registry static, queue hoặc Future.

Hai câu hỏi khác nhau cần phân biệt:

- **Đối tượng có được khởi tạo hoàn chỉnh không?** Constructor đã thiết lập đúng state và bảo vệ phần dữ liệu mutable hay chưa?
- **Thread khác nhìn thấy những ghi dữ liệu nào?** Java Memory Model có một quan hệ đồng bộ hóa phù hợp giữa bên ghi/bên công bố và bên đọc hay không?

Nếu một thread ghi các field rồi gán object vào một biến chia sẻ thông thường, thread khác đọc biến đó **không tự động có bảo đảm visibility** đối với mọi lần ghi trước đó. Khi thiếu quan hệ `happens-before`, chương trình có thể xuất hiện stale read, giá trị mặc định hoặc quan sát không nhất quán; kết quả không ổn định, nên không thể dựa vào “máy tôi luôn ra đúng”.

### 3.1. Happens-before là bảo đảm gì?

Quan hệ `happens-before` (viết tắt HB) xác định thứ tự quan sát mà JMM bảo đảm. Nếu thao tác ghi W `happens-before` thao tác đọc R, các tác động của W được bảo đảm có hiệu lực theo các quy tắc memory model tại R, trừ khi có các ghi khác hợp lệ xen vào.

Một số cơ chế tạo quan hệ HB quen thuộc:

- **Cùng thread:** các thao tác đứng trước trong thứ tự chương trình xảy ra HB các thao tác sau.
- **`synchronized`:** hành động unlock một monitor xảy ra HB hành động lock tiếp theo của *cùng monitor*.
- **`volatile`:** ghi vào một biến volatile xảy ra HB các lần đọc tiếp theo của chính biến đó theo thứ tự đồng bộ hóa.
- **`Thread.start()`:** hành động trước lúc gọi `start()` xảy ra HB các hành động của thread được start; hành động của thread kết thúc HB việc thread khác hoàn thành `join()` trên nó.
- **java.util.concurrent:** các cấu trúc như `BlockingQueue`, `ConcurrentHashMap`, `ExecutorService` và `Future.get()` có các memory-consistency effects được mô tả trong hợp đồng API.

HB **không yêu cầu mỗi thread chạy lần lượt**. Nó là ràng buộc về khả năng quan sát dữ liệu, không phải một lời hứa về thời gian chạy nhanh/chậm hay thứ tự log toàn hệ thống.

### 3.2. Vai trò đặc biệt của `final`

JMM có quy tắc riêng cho **final-field semantics**. Với đối tượng được xây dựng đúng cách, khi constructor kết thúc sẽ có “freeze” cho các final field được thiết lập trong quá trình khởi tạo. Thread khác nắm được tham chiếu đến object đã xây dựng đúng có bảo đảm mạnh hơn khi quan sát giá trị các final field, dù cách truyền tham chiếu chưa được đồng bộ hóa theo cách thông thường.

Ví dụ:

```java
final class HostInfo {
    final String host;
    final int port;

    HostInfo(String host, int port) {
        this.host = host;
        this.port = port;
    }
}
```

Các final field của `HostInfo` có ngữ nghĩa khởi tạo được JMM bảo vệ nếu object được xây dựng đúng và `this` không thoát ra trong constructor. Nhưng cần nhớ:

1. Điều này **không bảo đảm thread khác sẽ nhìn thấy ngay tham chiếu mới** nếu biến dùng để công bố tham chiếu bị đọc/ghi racy.
2. Nếu final field trỏ tới object mutable, các **sửa đổi về sau** của object đó không được final-field semantics tự động bảo vệ.
3. Final field không thay thế synchronization cho protocol cập nhật, phối hợp nhiều field hoặc shared mutable state khác.
4. Không được công bố `this` trong constructor, ví dụ ghi nó vào registry static, đăng ký listener gọi ngược ngay hoặc start task có thể truy cập object trước khi constructor hoàn tất.

**Kết luận:** final field giúp bảo đảm trạng thái khởi tạo của object immutable được đọc đúng; safe publication vẫn là cách rõ ràng để chuyển giao tham chiếu, bảo đảm thời điểm nhìn thấy cập nhật và xử lý các object không bất biến hoàn toàn.

## 4. Những cách công bố dữ liệu an toàn

### 4.1. Khởi tạo static

Class initialization có cơ chế đồng bộ hóa do JVM quản lý. Dữ liệu tạo khi khởi tạo class được công bố an toàn theo quy tắc class initialization.

```java
final class Defaults {
    static final Policy POLICY =
            new Policy("default", java.util.Map.of("mode", "safe"));

    private Defaults() {}
}
```

Mẫu này hợp với cấu hình cố định dùng trong class. Không giải quyết nhu cầu thay đổi snapshot lúc runtime.

### 4.2. Truyền tham chiếu qua `volatile`

`volatile` phù hợp khi một biến đại diện **một giá trị/snapshot độc lập**, writer thay cả tham chiếu và reader đọc một lần:

```java
import java.util.Map;

final class PolicyStore {
    private volatile Policy current = new Policy("v1", Map.of("level", "basic"));

    public Policy snapshot() {
        return current;            // volatile read
    }

    public void replace(Policy next) {
        current = java.util.Objects.requireNonNull(next); // volatile write
    }
}
```

Giả sử một thread gọi `replace(new Policy(...))`, thread khác đọc tham chiếu mới qua `snapshot()`, nó nhìn thấy state được khởi tạo trước phép ghi volatile. Mỗi reader có thể tiếp tục sử dụng snapshot cũ mà không bị thay đổi bên dưới; một reader đến trước hoặc sau một lượt thay thế có thể thấy **phiên bản khác nhau**, nhưng không thấy “nửa Map cũ, nửa Map mới” bên trong một snapshot bất biến.

**Giới hạn:** `volatile` chỉ bảo đảm visibility và tính nguyên tử cho **một lần đọc/ghi tham chiếu**, không biến chuỗi thao tác “đọc → sửa → ghi” thành một thao tác atomic. Nếu hai writer đồng thời làm `current = modify(current)`, một cập nhật có thể bị ghi đè.

### 4.3. `synchronized` với cùng lock

`synchronized` thiết lập cả loại trừ đồng thời và visibility qua lock/unlock:

```java
final class LockedCounter {
    private long count;

    public synchronized void increment() {
        count++;
    }

    public synchronized long get() {
        return count;
    }
}
```

Hai method phải dùng cùng monitor (ở đây là `this`) để bảo vệ chung state. `synchronized` là lựa chọn đơn giản cho invariant có nhiều bước hoặc nhiều field. Đừng trả ra ngoài một object mutable nội bộ rồi tiếp tục tin rằng khóa ở getter đã bảo vệ mọi thay đổi sau đó.

### 4.4. Handoff qua cấu trúc concurrent

Đưa dữ liệu vào `BlockingQueue` và lấy từ queue, hoàn thành `Future` rồi tiêu thụ qua `get()`, hay thực hiện các thao tác thích hợp với concurrent collection đều là những cách **chuyển giao/công bố an toàn** theo hợp đồng API cụ thể. Ví dụ producer xây dựng job object xong rồi `queue.put(job)`; consumer `queue.take()` nhận job với các ghi trước lúc đưa vào queue đã được công bố phù hợp.

Cần tách **hành vi thread-safe của container** khỏi hành vi của element: `ConcurrentHashMap<String, MutableReport>` không khiến việc sửa từng `MutableReport` trở nên an toàn. Dữ liệu lấy từ map vẫn phải immutable hoặc có cơ chế đồng bộ hóa riêng nếu còn thay đổi.

### 4.5. `AtomicReference` và thay thế snapshot bằng CAS

Khi có nhiều writer cùng cập nhật từ state cũ, dùng `AtomicReference<Snapshot>` với `updateAndGet` hoặc vòng lặp `compareAndSet`. Cơ chế CAS có thể thử lại khi một writer khác cập nhật trước; nó tránh việc hai writer độc lập đọc cùng state cũ rồi cùng ghi đè một kết quả.

```text
State v4
  ├── Writer A muốn thêm cờ A ──┐
  └── Writer B muốn thêm cờ B ──┼── CAS: một writer thắng
                                └── Writer còn lại đọc snapshot mới, tính lại và CAS
State cuối: chứa cả hai cập nhật (nếu quy tắc merge đúng)
```

Điều kiện: hàm cập nhật phải **không có side effect không lặp lại được**, vì `updateAndGet` có thể gọi lại hàm khi tranh chấp. Không gửi email, ghi database hay gọi dịch vụ bên ngoài ngay trong lambda cập nhật.

## 5. Java 17+ runnable: immutable snapshot + safe publication + nhiều writer

Ví dụ này xây dựng snapshot với `record`, sao chép phòng vệ bằng `Map.copyOf` và dùng `AtomicReference.updateAndGet` để 4 worker cùng tăng bộ đếm mà không làm mất cập nhật. Snapshot đã đọc trước đó không bị thay đổi.

Lưu là **ImmutablePublicationDemo.java**:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ImmutablePublicationDemo {
    public record Snapshot(int version, Map<String, Integer> counts) {
        public Snapshot {
            counts = Map.copyOf(counts);
        }

        public int count(String key) {
            return counts.getOrDefault(key, 0);
        }
    }

    private static final class Store {
        private final AtomicReference<Snapshot> current =
                new AtomicReference<>(new Snapshot(0, Map.of("views", 0)));

        Snapshot read() {
            return current.get();
        }

        void increment() {
            current.updateAndGet(old -> {
                Map<String, Integer> copy = new HashMap<>(old.counts());
                copy.merge("views", 1, Integer::sum);
                return new Snapshot(old.version() + 1, copy);
            });
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, Integer> input = new HashMap<>();
        input.put("views", 7);
        Snapshot copy = new Snapshot(5, input);
        input.put("views", 999);
        System.out.println("defensive copy: " + copy.count("views"));

        Store store = new Store();
        Snapshot before = store.read();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();

            for (int worker = 0; worker < 4; worker++) {
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        store.increment();
                    }
                }));
            }

            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            pool.shutdown();
        }

        Snapshot after = store.read();
        System.out.println("before: " + before.version()
                + "/" + before.count("views"));
        System.out.println("after: " + after.version()
                + "/" + after.count("views"));
        System.out.println("old snapshot remains: " + before.count("views"));
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac ImmutablePublicationDemo.java
java ImmutablePublicationDemo
```

**Stdout xác định:**

```text
defensive copy: 7
before: 0/0
after: 400/400
old snapshot remains: 0
```

Vì sao kết quả đúng?

1. `Snapshot` là record chứa số `int` và Map có key `String`, value `Integer`, đều bất biến; compact constructor dùng `Map.copyOf` để ngăn bên ngoài thay đổi Map snapshot. Bản `copy` vẫn giữ 7 dù `input` bị đổi thành 999.
2. `Store.read()` đọc một tham chiếu hiện tại qua `AtomicReference.get()`; mỗi snapshot là **một trạng thái hoàn chỉnh**.
3. Mỗi `increment()` dựng Map mới, tạo Snapshot mới rồi `updateAndGet` thực hiện cập nhật atomic với CAS/retry. Không chia sẻ Map đang bị sửa giữa các thread.
4. Bốn worker thực hiện tổng cộng 400 lần tăng. `Future.get()` bảo đảm task đã hoàn tất, đồng thời cung cấp quan hệ memory consistency thích hợp trước khi main đọc kết quả.
5. Snapshot `before` vẫn có version 0 và `views=0`: object cũ không bị sửa. Các writer chỉ thay tham chiếu hiện tại tới snapshot mới.

Lưu ý: **CAS có thể gọi lại lambda** nhiều lần; mọi thao tác bên trong lambda ở đây chỉ tính toán bộ nhớ cục bộ và tạo object mới. Cách này không tự loại bỏ các yêu cầu nghiệp vụ về persistence, transaction hoặc độ bền dữ liệu sau restart.

## 6. Đưa mô hình snapshot vào Spring singleton bean

### 6.1. Bean đọc nhiều, cập nhật cấu hình trong runtime

Ví dụ service quản lý feature flags. Đọc một snapshot immutable, ghi bằng CAS, giữ phiên bản để quan sát thay đổi. Đây là fragment cho dự án Spring Framework 6+/Spring Boot 3+, cần dependency Spring và nằm trong **src/main/java/com/example/demo/FeatureFlagService.java**; không chạy bằng `javac` độc lập nếu thiếu Spring trên classpath.

```java
package com.example.demo;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService {
    private final AtomicReference<Flags> current =
            new AtomicReference<>(new Flags(0, Map.of()));

    public boolean enabled(String name) {
        Objects.requireNonNull(name, "name");
        Flags snapshot = current.get();
        return snapshot.values().getOrDefault(name, false);
    }

    public Flags snapshot() {
        return current.get();
    }

    public void setFlag(String name, boolean enabled) {
        Objects.requireNonNull(name, "name");
        current.updateAndGet(old -> {
            Map<String, Boolean> next = new HashMap<>(old.values());
            next.put(name, enabled);
            return new Flags(old.version() + 1, next);
        });
    }

    public record Flags(long version, Map<String, Boolean> values) {
        public Flags {
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
        }
    }
}
```

Một controller/request thread có thể gọi `enabled("newCheckout")` trong khi scheduler/admin thread gọi `setFlag(...)`. Mỗi lần đọc lấy **một snapshot nhất quán**; không có Map đang bị sửa tại chỗ để gây race. Hai writer cập nhật **hai tên cờ khác nhau** không đánh rơi cập nhật do CAS phải tính lại từ state mới khi tranh chấp. Tuy nhiên, hai writer cùng đặt **một cờ** sang giá trị khác nhau vẫn có kết quả “lần cập nhật nào linearize sau cùng thắng”; đó là **quy tắc nghiệp vụ**, không phải lỗi JMM.

Điểm cần chú ý:

- Có thể tiêm `FeatureFlagService` vào nhiều controller và job vì một instance singleton phục vụ truy cập đồng thời; thread safety đến từ immutable snapshots và AtomicReference, không đến từ `@Service`.
- Mỗi lần cập nhật copy toàn bộ Map, có chi phí bộ nhớ và CPU tỷ lệ với kích thước Map. Phù hợp **read-heavy/write-rarely** như feature flags/cấu hình; nếu ghi liên tục với dữ liệu rất lớn, cần đánh giá mô hình khác như `ConcurrentHashMap`, phân vùng dữ liệu, event stream hoặc lock có phạm vi hợp lý.
- Nếu cặp `values + version` phải luôn cùng snapshot, cho chúng vào **cùng một object Flags** như trên. Hai field volatile độc lập không bảo đảm các lần đọc tạo thành một cặp nhất quán.
- Expose `snapshot()` ở đây vẫn an toàn vì bản thân `Flags` immutable với `String/Boolean`. Nếu value mutable, phải sửa chiến lược copy.
- Khi update gắn với database, event hoặc network, không thực hiện side effect trong lambda CAS; thực hiện sau khi cập nhật thành công và phải thiết kế riêng semantics khi bị thất bại.

### 6.2. Constructor injection + final dependency

Một Spring singleton bean khác có thể không có mutable state:

```java
import org.springframework.stereotype.Service;

@Service
public class PriceService {
    private final TaxPolicy taxPolicy;

    public PriceService(TaxPolicy taxPolicy) {
        this.taxPolicy = taxPolicy;
    }

    public long totalWithTax(long amount) {
        return taxPolicy.apply(amount);
    }
}
```

Đây là **fragment** minh họa DI; `TaxPolicy` là một abstraction giả định đã được định nghĩa ở nơi khác. Field `final` bảo đảm `PriceService` không thay tham chiếu dependency sau constructor; việc `PriceService` có thread-safe hay không còn phụ thuộc **`TaxPolicy` có trạng thái mutable hay không** và cách method `apply` hoạt động. Nếu dependency là immutable hoặc thread-safe và method không giữ state chia sẻ khác, mô hình này rất dễ dùng đồng thời.

**Đừng coi “field chứa dependency là final” đồng nghĩa “toàn bộ object graph immutable”.** Một field final trỏ tới một cache mutable vẫn phải có quy tắc đồng bộ hóa riêng cho cache đó.

## 7. Công bố bean trong Spring lifecycle

Trong vòng đời chuẩn, Spring tạo bean, xử lý dependency injection, gọi initialization callbacks (như `@PostConstruct`) rồi công bố bean đã sẵn sàng sử dụng qua container. Tài liệu Spring cho biết state cấu hình chỉ được thay đổi **trong giai đoạn khởi tạo** có bảo đảm visibility thích hợp khi container công bố singleton hoàn tất; các field cấu hình kiểu thường không nhất thiết phải đánh dấu volatile chỉ vì setter injection diễn ra lúc khởi tạo.

Tuy nhiên, phải phân biệt rõ:

| Tình huống | Đánh giá |
| --- | --- |
| Bean constructor nhận dependency vào field `final`, không sửa về sau | Thiết kế tốt cho cấu hình/dependency cố định |
| Setter hoặc `@PostConstruct` thiết lập state rồi không sửa tiếp; bean được Spring công bố bình thường sau khi init | Container công bố trạng thái khởi tạo của bean theo lifecycle của nó |
| Một thread bất kỳ sửa field thường **sau** khi bean đã khởi tạo | Không có bảo đảm visibility/thread safety chỉ nhờ bean là singleton |
| Field runtime thay đổi được và có nhiều reader | Cần `volatile`, lock, atomic, concurrent structure hoặc cơ chế phù hợp |
| Bean gửi `this` sang static registry/thread khác khi đang constructor/init | Có nguy cơ công bố sớm trước khi khởi tạo hoàn chỉnh |
| Circular dependency buộc Spring expose bean sớm trước khi hoàn tất initialization | Có thể tạo ngoại lệ với quy tắc công bố thông thường; tránh vòng phụ thuộc |

### 7.1. Vì sao `@PostConstruct` không thay thế concurrency control?

`@PostConstruct` chạy trong lifecycle initialization. Phương thức này thích hợp để kiểm tra cấu hình hoặc tạo cấu trúc dữ liệu ban đầu. Sau khi hoàn tất, container mới xem bean là initialized trong quy trình chuẩn. Nhưng nếu method nào đó tiếp tục sửa Map hoặc field thường khi nhiều request đang dùng bean, **visibility cho các cập nhật runtime không tự phát sinh**. Cần thiết kế synchronization tương ứng.

Không nên tự start worker, đăng ký listener callback ngay hoặc trao `this` cho thành phần chưa được kiểm soát khi constructor/init chưa xong. Nếu cần thực hiện công việc tốn thời gian sau khi các singleton đã hoàn thành khởi tạo, cân nhắc lifecycle hook phù hợp như `SmartInitializingSingleton.afterSingletonsInstantiated()`, `ContextRefreshedEvent` hoặc `SmartLifecycle`; chúng giải quyết **thời điểm khởi động**, còn việc chia sẻ state runtime vẫn phải bảo đảm theo JMM.

### 7.2. Scope prototype/request có giải quyết được không?

`prototype` tạo instance mới mỗi lần container trực tiếp resolve bean, nhưng nếu tiêm một prototype bean một lần vào singleton, singleton thường giữ đúng instance đã được tiêm đó. Scope `request` có một instance theo request trong web context phù hợp, nhưng vẫn có thể chia sẻ dữ liệu mutable qua dependency, cache hoặc async handoff. Vì vậy, scope là công cụ **định nghĩa vòng đời và phạm vi chia sẻ**, không phải một cơ chế synchronization tổng quát.

## 8. Chọn cơ chế theo kiểu trạng thái bean

| Loại state | Hướng thiết kế nên ưu tiên | Điều cần tránh |
| --- | --- | --- |
| Tham số gắn với một request/lời gọi | Biến local, immutable DTO | Lưu user/request hiện tại vào instance field của singleton |
| Cấu hình không thay đổi sau bootstrap | Constructor injection, `final`, immutable value object | Giữ tham chiếu tới input Map/List mutable |
| Cấu hình thay toàn bộ theo phiên bản, đọc nhiều | Immutable snapshot trong `volatile` / `AtomicReference` | Sửa Map của snapshot cũ tại chỗ |
| Nhiều writer sửa từ state trước đó | `AtomicReference.updateAndGet` / CAS hoặc lock | `volatile` cộng `state = derive(state)` khi có cạnh tranh |
| Bộ đếm/giá trị nguyên tử đơn lẻ | `AtomicLong`, `LongAdder` tùy yêu cầu đọc chính xác | `count++` trên `volatile long`, vì đây không phải phép tăng atomic |
| Map cần cập nhật độc lập theo key, tần suất cao | `ConcurrentHashMap.compute` hoặc `merge` | `get` rồi `put` và giả định tổ hợp đó atomic |
| Nhiều field có invariant chung | Lock bảo vệ toàn bộ thao tác hoặc snapshot gộp | Mỗi field volatile riêng rồi đọc ghép thành state “hỗn hợp” |
| Giao việc giữa thread | Executor, queue, Future với hợp đồng HB rõ ràng | Chia sẻ một object mutable rồi bỏ mặc đồng bộ hóa |

**Ví dụ invariant:** nếu `min <= max` phải luôn đúng, việc đặt `min` và `max` là hai biến volatile không bảo đảm mỗi reader nhìn thấy một cặp hợp lệ cùng phiên bản. Có thể dùng record `Range(int min, int max)`, kiểm tra invariant trong constructor và thay toàn bộ `volatile Range`/`AtomicReference<Range>` trong một bước, hoặc khóa chung khi đọc/ghi hai field.

### Lưu ý hiệu năng

Immutable snapshot thường đổi chi phí **mỗi lần cập nhật** lấy đường đọc nhanh và đơn giản, rất tốt cho cấu hình ít ghi. Với state thay đổi liên tục và kích thước lớn, copy cả Map mỗi lần sẽ không tối ưu. Việc chọn atomic, lock, concurrent collection hay snapshot phải xuất phát từ tỉ lệ đọc/ghi, độ lớn state, invariant cần bảo vệ và yêu cầu latency; không có một cơ chế luôn tốt nhất.

## 9. Những lỗi phổ biến

**Lỗi 1 — Nghĩ rằng singleton = thread-safe.** Singleton chỉ nói về số instance do container quản lý. Nếu các request sửa cùng field, vẫn có race.

**Lỗi 2 — Dùng `final HashMap` rồi sửa bằng `put`.** `final` giữ tham chiếu, không đóng băng nội dung HashMap. Dùng immutable Map, đồng bộ hóa hoặc collection concurrent theo nhu cầu.

**Lỗi 3 — Dùng `Collections.unmodifiableMap` trên source Map còn bị sửa.** View không sửa được qua API của nó nhưng vẫn phản ánh thay đổi của source. Nếu cần snapshot tách biệt, copy dữ liệu.

**Lỗi 4 — Đưa object ra ngoài trước khi constructor hoàn thành.** Việc đăng ký listener, lưu `this` vào static hoặc chạy task từ constructor có thể cho thread khác nhìn thấy object chưa xây dựng xong, phá điều kiện của final-field guarantee.

**Lỗi 5 — Cho rằng `@PostConstruct` làm mọi cập nhật sau này visible.** Lợi ích công bố state ban đầu của container không bao trùm các mutation runtime. Cần memory barrier/synchronization phù hợp.

**Lỗi 6 — Chỉ đánh dấu `volatile` cho Map rồi tiếp tục `map.put`.** Volatile reference không làm operations bên trong `HashMap` atomic hay an toàn. Thay toàn bộ immutable snapshot hoặc dùng cấu trúc/thread coordination thích hợp.

**Lỗi 7 — Dùng `volatile count++`.** Phép tăng gồm đọc, cộng, ghi nên có thể mất update. `AtomicLong.incrementAndGet()` hoặc synchronized bảo đảm atomicity cho một counter.

**Lỗi 8 — Không phân biệt safe publication và safe mutation.** Công bố an toàn cho phép consumer thấy trạng thái *tại thời điểm bàn giao*; nếu object còn mutable và bị writer sửa về sau, việc truy cập các sửa đổi đó vẫn đòi hỏi thread safety.

**Lỗi 9 — Nghe “concurrent collection” rồi đặt dữ liệu mutable bên trong.** Map thread-safe không tự bảo vệ trạng thái của mỗi value. Cần immutable values hoặc synchronization cụ thể.

**Lỗi 10 — Gọi DB/HTTP bên trong `AtomicReference.updateAndGet`.** Lambda có thể bị thực thi lại trong quá trình CAS retry, tạo side effect lặp. Chỉ tính object mới trong lambda; phối hợp với I/O theo giao thức rõ ràng.

**Lỗi 11 — Đọc hai snapshot khác nhau trong cùng một quyết định.** Nếu hai lời gọi `store.snapshot()` có thể trả hai phiên bản, business logic dễ ghép dữ liệu không nhất quán. Lấy snapshot **một lần** ở đầu một quyết định cần tính nhất quán và dùng nó xuyên suốt.

**Lỗi 12 — Hiểu nhầm visibility thành persistence.** Volatile và CAS bảo đảm trật tự bộ nhớ giữa thread trong cùng JVM; chúng không tự lưu state sau khi restart và không đồng bộ hóa trạng thái giữa nhiều Spring Boot replica. Những vấn đề này cần database, message broker hoặc cơ chế phân tán.

## 10. Tự kiểm tra hiểu bài

**Câu 1.** Một Spring `@Service` có `private final Map<String, Integer> totals = new HashMap<>();`. Các request cùng gọi `totals.merge(...)`. Có thread-safe không?

**Đáp án:** Không. Field final chỉ giữ nguyên tham chiếu Map; HashMap mutable không an toàn cho cập nhật đồng thời. Tùy yêu cầu, dùng concurrent map với `merge/compute`, lock hoặc immutable snapshot thay thế bằng CAS.

**Câu 2.** `record Config(List<String> servers)` có luôn immutable sâu không?

**Đáp án:** Không. Record không tự copy danh sách. Constructor có thể dùng `servers = List.copyOf(servers)` cho một danh sách phần tử String immutable và tránh trả ra dữ liệu mutable nội bộ.

**Câu 3.** Khi một thread gán `volatile Config current` bằng object mới được xây dựng xong, reader lấy object qua chính biến volatile đó có nhìn thấy state đã khởi tạo không?

**Đáp án:** Có, với các ghi xảy ra trước volatile write và volatile read tương ứng theo quan hệ HB. Nếu object sau đó bị sửa qua đường khác thiếu đồng bộ hóa, bảo đảm này không tự bảo vệ mọi mutation sau đó.

**Câu 4.** Vì sao `volatile long counter; counter++;` có thể mất số lần tăng?

**Đáp án:** Vì volatile không gộp read-modify-write thành một hành động atomic. Hai thread có thể đọc cùng giá trị cũ rồi ghi cùng giá trị mới; dùng atomic counter hoặc khóa.

**Câu 5.** Vì sao một immutable snapshot cũ vẫn dùng được khi bean đổi sang snapshot mới?

**Đáp án:** Writer tạo object mới và chỉ thay tham chiếu hiện tại. Reader đang giữ object cũ vẫn thấy trạng thái cũ nhất quán, miễn graph trạng thái thực sự immutable và object được khởi tạo/công bố đúng.

**Câu 6.** Spring có bảo đảm singleton được công bố sau khi hoàn tất quy trình khởi tạo chuẩn; như vậy field thường có thể bị nhiều request sửa không đồng bộ hóa không?

**Đáp án:** Không. Công bố ban đầu và các cập nhật runtime là hai vấn đề khác nhau. Sau khởi tạo, shared mutable state vẫn phải có cơ chế thread safety/visibility thích hợp.

## 11. Checklist trước khi đưa bean vào production

1. Xác định chính xác field nào là **per-request**, field nào là **shared**, và có writer nào ngoài request thread (scheduler, async, listener) không.
2. Nếu chỉ cần đọc, thiết kế **immutable value objects**, defensive copy cho cấu trúc lồng nhau và constructor injection cho dependency bắt buộc.
3. Tránh cho `this` hoặc reference mutable nội bộ thoát ra trước khi object được khởi tạo hoàn chỉnh.
4. Nếu cập nhật động, chọn **volatile snapshot** cho thay thế đơn giản hoặc **CAS/lock** cho cập nhật phụ thuộc state trước đó; giữ invariant trong cùng một snapshot.
5. Bảo đảm object và các phần tử được chia sẻ thực sự immutable hoặc có protocol synchronization rõ ràng; đừng tin vào từ khóa `final` một cách máy móc.
6. Kiểm tra hành vi khi nhiều writer cạnh tranh, khi async job chạy muộn, khi khởi động lại instance và khi nhiều replica cùng hoạt động.
7. Tách yêu cầu **thread safety trong JVM** khỏi **độ bền dữ liệu và nhất quán phân tán**.

**Ghi nhớ:** *Immutability* loại bỏ việc sửa trạng thái sau khi xây dựng; *safe publication* tạo bảo đảm rằng thread khác nhận được trạng thái khởi tạo/cập nhật theo đúng quan hệ bộ nhớ; *Spring bean scope* quy định đối tượng được chia sẻ trong phạm vi nào. Kết hợp ba góc nhìn này giúp lập luận đúng về concurrency trong ứng dụng Spring.

## Tài liệu đối chiếu

- Java Language Specification, Chapter 17 — Threads and Locks; `17.4 (Memory Model)`, `17.5 (Final Field Semantics)`: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- Spring Framework — Bean Scopes: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework — Dependency Injection (constructor injection, circular references): https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
- Spring Framework — Customizing the Nature of a Bean (init/publication and runtime state): https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html
- Java SE API — `java.util.concurrent`, `AtomicReference`, `Future`, `ConcurrentHashMap` và `BlockingQueue`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-state-004">Giữ dữ liệu của từng yêu cầu tách khỏi trạng thái bean</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ dữ liệu của từng yêu cầu tách khỏi trạng thái bean trong Spring

Khi nhiều HTTP request đến cùng lúc, Spring có thể cho các luồng khác nhau gọi **cùng một đối tượng controller hoặc service**. Nếu bean đó lưu tên người dùng, tham số request, mã truy vết hay dữ liệu xử lý tạm thời vào **field dùng chung**, request này có thể ghi đè dữ liệu của request khác. Hậu quả không chỉ là kết quả sai: với dữ liệu gắn với người dùng, đó còn có thể là **rò rỉ dữ liệu giữa các yêu cầu**.

Nguyên tắc chính: **dữ liệu chỉ thuộc một request nên đi theo lời gọi, đối tượng dữ liệu của request hoặc một phạm vi request được quản lý rõ ràng; không được âm thầm trở thành trạng thái dùng chung của singleton bean**. Bài này giải thích cơ chế, các lựa chọn thiết kế an toàn, cách xử lý khi có bất đồng bộ và những giới hạn của từng cách.

## 1. Vì sao trạng thái bean liên quan trực tiếp đến concurrency?

Trong Spring, các stereotype thông dụng như **@Component, @Service, @Repository, @Controller, @RestController** mặc định có **singleton scope**: trong một Spring container, một bean definition singleton thường có một instance được chia sẻ cho mọi nơi sử dụng bean đó. Singleton là phạm vi vòng đời của đối tượng, **không phải bảo đảm thread-safe**.

Hãy hình dung hai request cùng gọi một bean:

~~~text
Request A (user=An)    Request B (user=Bình)
        |                       |
        +----------+------------+
                   |
             Singleton bean
             field currentUser
                   |
        An ghi vào | Bình ghi đè
                   |
        An đọc lại -> "Bình"  (sai ngữ cảnh)
~~~

Mỗi request có dữ liệu và luồng điều khiển riêng, nhưng **field của bean singleton thuộc về cùng một object trên heap**, nên mọi invocation đều nhìn thấy *cùng vị trí trạng thái*. Spring không tạo thêm bản sao controller/service singleton cho từng request.

Phân biệt rõ:

| Thành phần | Vòng đời và chia sẻ | Có phù hợp để giữ dữ liệu từng request? |
| --- | --- | --- |
| Tham số phương thức và biến cục bộ | Mỗi lần gọi có các biến/tham chiếu riêng | **Có**, khi không trỏ đến đối tượng mutable dùng chung |
| DTO/record tạo mới cho request | Một đối tượng cụ thể được cấp/phân phối cho request | **Có**, nếu không bị tái sử dụng, publish hoặc mutate sai cách |
| Field của singleton controller/service | Một field được chia sẻ giữa các lần gọi | **Không**, đối với dữ liệu chỉ thuộc một request |
| Field của request-scoped bean | Một target bean gắn với một HTTP request đang hoạt động | **Có điều kiện**, khi cần chia sẻ ngữ cảnh trong cùng request |
| Field của session-scoped bean | Gắn với HTTP session, có thể tồn tại qua nhiều request | **Không thay thế request scope**; nhiều request cùng session có thể chạy đồng thời |
| ThreadLocal | Giá trị gắn với thread, không phải trực tiếp với request | **Chỉ cho cơ chế hạ tầng có quản lý và cleanup**, không là giải pháp mặc định |
| Field static | Chung ở cấp class/classloader | **Không**, cho dữ liệu riêng từng request |

**Không phải mọi field singleton đều nguy hiểm.** Một dependency bất biến qua constructor, configuration đã được đóng băng, service stateless hoặc cache được thiết kế thread-safe có thể được chia sẻ hợp lý. Điều cần tránh là đặt **trạng thái thay đổi riêng theo request** vào chỗ dùng chung. Ngay cả field có từ khóa **final** cũng chỉ khóa *tham chiếu*, không làm đối tượng được tham chiếu tự động bất biến hoặc thread-safe.

## 2. Anti-pattern: lưu dữ liệu request trong field của controller

Ví dụ sau là **đoạn mã Spring MVC để nhận diện lỗi**, không phải một chương trình Java độc lập:

~~~java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnsafeGreetingController {
    private String currentName; // Dùng chung giữa mọi request!

    @GetMapping("/unsafe/greet")
    public String greet(@RequestParam String name) {
        currentName = name;                 // Request A có thể vừa ghi "An".
        return "Xin chào " + currentName;   // Request B có thể đã ghi "Bình".
    }
}
~~~

Nếu A và B chạy xen kẽ, A có thể trả “Xin chào Bình” dù tham số của A là “An”. Không thể kết luận lỗi này sẽ xuất hiện ở *mọi* lần gọi, vì lịch CPU không cố định. Đây còn là **data race**: các luồng cùng đọc/ghi field không được đồng bộ, nên khả năng quan sát giá trị phụ thuộc vào Java Memory Model và quan hệ happens-before.

Một cách sửa sai thường thấy là thêm **synchronized** quanh phương thức. Điều đó có thể loại bỏ một số race trên field, nhưng biến xử lý request độc lập thành công việc phải xếp hàng và vẫn giữ mô hình dữ liệu đặt sai phạm vi. Tương tự, đổi sang **volatile** hoặc **AtomicReference<String>** có thể thay đổi tính hiển thị/thao tác nguyên tử, nhưng **không biến một ô nhớ chung thành ô riêng của từng request**. Đúng về cơ chế đồng bộ chưa chắc đúng về ngữ nghĩa dữ liệu.

## 3. Tái hiện lỗi có kiểm soát bằng Java 17+

Ví dụ Java độc lập sau mô phỏng hai request truy cập một object dùng chung. **CountDownLatch** chủ động buộc thứ tự xen kẽ, vì vậy không phải dựa vào Thread.sleep hay sự may rủi của scheduler.

Lưu thành **RequestLeakDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RequestLeakDemo {
    static final class SharedBean {
        private String currentUser;

        String handleAn(CountDownLatch anStored,
                        CountDownLatch binhStored) throws InterruptedException {
            currentUser = "An";
            anStored.countDown();
            binhStored.await(); // Chờ B ghi đè rồi mới đọc.
            return currentUser;
        }

        String handleBinh(CountDownLatch anStored,
                          CountDownLatch binhStored) throws InterruptedException {
            anStored.await();
            currentUser = "Bình";
            binhStored.countDown();
            return currentUser;
        }
    }

    public static void main(String[] args) throws Exception {
        SharedBean bean = new SharedBean();
        CountDownLatch anStored = new CountDownLatch(1);
        CountDownLatch binhStored = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> an = executor.submit(
                    () -> bean.handleAn(anStored, binhStored));
            Future<String> binh = executor.submit(
                    () -> bean.handleBinh(anStored, binhStored));

            System.out.println("Request An đọc: " + an.get());
            System.out.println("Request Bình đọc: " + binh.get());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 RequestLeakDemo.java
java RequestLeakDemo
~~~

Kết quả:

~~~text
Request An đọc: Bình
Request Bình đọc: Bình
~~~

**Vì sao kết quả xác định?** A ghi “An”, báo hiệu cho B bằng anStored, rồi chờ. B chỉ chạy phần ghi “Bình” sau khi nhận tín hiệu; B báo binhStored và A mới đọc lại. Các thao tác countDown/await tạo các quan hệ happens-before cần thiết để A quan sát lần ghi của B. Ví dụ cố ý đặt state vào field chung để làm rõ lỗi; không khuyến khích viết chương trình thực tế theo cấu trúc đó.

**Một request không phải một thread cố định.** Trong Servlet MVC, xử lý đồng bộ thường diễn ra trên request thread; nhưng khi dùng async dispatch, @Async, CompletableFuture hoặc thư viện I/O, phần việc có thể sang thread khác. Vì thế, luôn xác định **ai sở hữu dữ liệu** thay vì dựa vào giả định thread nào đang chạy.

## 4. Giải pháp mặc định: singleton stateless, tham số và biến cục bộ

Cách đơn giản nhất là **truyền dữ liệu vào phương thức**, chỉ tạo biến cục bộ hoặc đối tượng kết quả mới. Bean có thể giữ dependency ổn định, nhưng không lưu “request hiện tại” vào field.

Lưu thành **StatelessRequestDemo.java**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class StatelessRequestDemo {
    static final class GreetingService {
        String greet(String user) {
            String result = "Xin chào " + user;
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        GreetingService service = new GreetingService();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> an =
                    executor.submit(() -> service.greet("An"));
            Future<String> binh =
                    executor.submit(() -> service.greet("Bình"));

            System.out.println(an.get());
            System.out.println(binh.get());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 StatelessRequestDemo.java
java StatelessRequestDemo
~~~

Kết quả:

~~~text
Xin chào An
Xin chào Bình
~~~

Hai task dùng **cùng một service** nhưng mỗi invocation có tham số user và biến result của chính nó. Dù executor chạy chúng theo thứ tự nào, một task không thể ghi đè biến cục bộ của task kia.

Một lưu ý quan trọng: **biến cục bộ là tham chiếu riêng, không tự làm đối tượng được trỏ tới an toàn**. Nếu hai request cùng giữ tham chiếu tới một List hoặc Map mutable được chia sẻ, chỉnh sửa collection đó vẫn có thể gây race. Kiểm tra cả object graph, không chỉ vị trí khai báo biến.

Một quy tắc thiết kế dễ áp dụng:

~~~text
HTTP request
    -> Controller đọc input và thông tin request
    -> DTO/record riêng cho lời gọi
    -> Service stateless nhận DTO qua tham số
    -> Repository / external client theo hợp đồng thread-safe
    -> Response tạo mới cho caller
~~~

Đây là kiến trúc mặc định tốt cho phần lớn Spring MVC REST endpoints.

## 5. Ví dụ Spring MVC: controller và service không mang request state

Các mã sau là **các lớp/tệp trong một ứng dụng Spring MVC có dependency Spring Web**, không chạy bằng lệnh javac trần nếu chưa thêm classpath Spring. Mỗi khối thể hiện một tệp Java riêng.

**GreetingResponse.java** — dữ liệu kết quả bất biến dạng record:

~~~java
package example.web;

public record GreetingResponse(String requestId, String message) {
}
~~~

**GreetingService.java** — service có thể là singleton vì không có field request:

~~~java
package example.web;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public GreetingResponse build(String requestId, String name) {
        String message = "Xin chào " + name.trim();
        return new GreetingResponse(requestId, message);
    }
}
~~~

**GreetingController.java** — tạo thông tin riêng từng request rồi truyền qua tham số:

~~~java
package example.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/greet")
    public GreetingResponse greet(@RequestParam String name) {
        String requestId = UUID.randomUUID().toString();
        return greetingService.build(requestId, name);
    }
}
~~~

Khi hai HTTP request đi vào **/greet?name=An** và **/greet?name=Bình**, mỗi lần gọi tạo một requestId mới rồi xây dựng đối tượng response riêng. **Không có field currentName/currentRequestId** trong singleton controller hay service. Field final greetingService là dependency được chia sẻ có chủ đích, và implementation ví dụ này không giữ trạng thái request.

UUID ở đây chỉ minh họa tạo mã truy vết; trong hệ thống thật, một tracing/request ID có thể đến từ middleware đáng tin hoặc cơ chế quan sát đã chuẩn hóa. **Request ID không phải thông tin xác thực hoặc bằng chứng phân quyền**.

Tách biệt cả khi ứng dụng có nhiều tầng: controller có thể chuyển tiếp **request DTO** sang service rồi repository; không cần service giữ “người dùng đang xử lý” trong field. Khi logic cần người dùng đã xác thực, lấy từ security principal thích hợp ở ranh giới rồi truyền một đối tượng context đủ thông tin, đã được kiểm tra quyền.

## 6. Đối tượng riêng cho request: DTO và immutable snapshot

Khi một request có nhiều thông tin cần truyền cùng nhau, thay vì lặp nhiều tham số, hãy tạo một **request context thuộc về lời gọi**:

~~~java
package example.web;

public record RequestSnapshot(
        String requestId,
        String userId,
        String localeTag
) {
}
~~~

Có thể tạo record này trong controller từ dữ liệu đã xác thực/chuẩn hóa, rồi truyền nó qua các tầng service. Record giữ các field final, và các String của ví dụ là immutable. Nếu record chứa List, Map hoặc đối tượng mutable, người tạo vẫn phải copy phòng vệ khi cần; chỉ từ khóa **record** không bảo đảm toàn bộ cấu trúc con bất biến.

Với Spring service:

~~~java
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {
    public String describe(RequestSnapshot snapshot, String invoiceId) {
        return "user=" + snapshot.userId() + ", invoice=" + invoiceId;
    }
}
~~~

Đây là **đoạn minh họa** giả sử InvoiceService và RequestSnapshot nằm trong package/import phù hợp. Những giá trị userId, invoiceId thuộc về invocation nên không lưu trong field. Một singleton service có thể thực hiện hàng nghìn lời gọi độc lập mà không cần khóa riêng chỉ để giữ các biến này.

Nếu dữ liệu có bí mật (token, thông tin định danh nhạy cảm), chỉ giữ phần tối thiểu cần cho tác vụ; tránh log hoặc truyền snapshot dư thừa sang những hệ thống không cần dùng.

## 7. Khi nào dùng @RequestScope?

Đôi khi nhiều thành phần trong cùng request cần dùng **một đối tượng ngữ cảnh có vòng đời request**, ví dụ mã correlation, thông tin localization đã tính hoặc một bộ nhớ tạm ngắn hạn. Spring Web cung cấp **request scope**: bean target được tạo/duy trì trong phạm vi một HTTP request đang hoạt động.

**RequestTrace.java** (một bean riêng):

~~~java
package example.web;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestTrace {
    private final String requestId = UUID.randomUUID().toString();

    public String id() {
        return requestId;
    }
}
~~~

**TraceController.java** (singleton controller phụ thuộc request-scoped bean):

~~~java
package example.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TraceController {
    private final RequestTrace trace;

    public TraceController(RequestTrace trace) {
        this.trace = trace;
    }

    @GetMapping("/trace")
    public String trace() {
        return trace.id();
    }
}
~~~

Trong Spring, **@RequestScope** mặc định dùng **class-based scoped proxy**. Controller singleton giữ **proxy**, và khi phương thức được gọi trong HTTP request hợp lệ, proxy giải quyết **request-scoped target** của request hiện tại. Hai request khác nhau có target RequestTrace khác nhau; nhiều lần truy cập trong *cùng* request sử dụng cùng target.

Điểm cần hiểu chính xác:

- **@RequestScope không có nghĩa một object riêng cho mỗi thread.** Nếu cùng request điều phối nhiều công việc đồng thời và các công việc ấy thực sự truy cập cùng target, field mutable bên trong target vẫn cần xét concurrency.
- Scoped bean cần **request context đang hoạt động**; gọi proxy ở ngoài request, hoặc trong executor thread không có context tương ứng, có thể dẫn tới lỗi scope không active.
- Đừng mang request-scoped proxy hoặc target vào một background task chạy sau khi request kết thúc rồi mong nó còn hoạt động. Hãy trích xuất dữ liệu cần thiết thành immutable snapshot trước khi chuyển ranh giới bất đồng bộ.
- Request scope không cần thiết khi vài biến cục bộ và một DTO đã giải quyết được yêu cầu. Thêm scope tạo phụ thuộc ngầm vào vòng đời HTTP, tăng khó khăn cho test và async.

**Request scope khác prototype scope.** Một prototype bean được tạo khi Spring giải quyết yêu cầu lấy bean mới; nếu prototype được inject trực tiếp vào constructor của một singleton, tham chiếu được inject **không tự đổi mỗi HTTP request**. Cũng không dùng **@SessionScope** để giả lập request scope: nhiều request song song của cùng session vẫn có thể dùng chung đối tượng session.

## 8. ThreadLocal không đồng nghĩa với request scope

ThreadLocal lưu một giá trị **theo thread**, ví dụ một request ID được middleware gắn vào thread khi xử lý request. Nó không tạo quyền sở hữu theo HTTP request.

Ở server dùng thread pool:

1. Worker thread T1 phục vụ request A.
2. Nếu A đặt ThreadLocal rồi quên xóa, T1 có thể được tái sử dụng để phục vụ request B.
3. B đọc nhầm giá trị còn lại của A: rò rỉ ngữ cảnh giữa các request.

Nếu một cơ chế tầng hạ tầng thật sự dùng ThreadLocal, tối thiểu phải **dọn trong finally** trên mọi nhánh lỗi và thành công:

~~~java
public final class TraceHolder {
    private static final ThreadLocal<String> CURRENT =
            new ThreadLocal<>();

    private TraceHolder() {
    }

    public static void runWithTrace(String id, Runnable action) {
        CURRENT.set(id);
        try {
            action.run();
        } finally {
            CURRENT.remove();
        }
    }

    public static String currentId() {
        return CURRENT.get();
    }
}
~~~

Đây là **lớp Java độc lập có thể biên dịch** nhưng chỉ minh họa cleanup cho một ngữ cảnh không lồng nhau. Nếu cho phép lồng context, cần lưu và phục hồi giá trị trước đó một cách có chủ đích. Tránh tự xây kho request-state toàn cục bằng ThreadLocal khi tham số/DTO đủ dùng.

Spring MVC có **RequestContextHolder** và một số cơ chế sử dụng thread-bound request attributes trong quá trình xử lý web. Đây là một phần hạ tầng có vòng đời bind/unbind, **không có nghĩa tùy ý tạo @Async task là request attributes sẽ tự đi theo task**. Với executor tái sử dụng thread, dựa vào **InheritableThreadLocal** cho dữ liệu request đặc biệt nguy hiểm vì thời điểm tạo thread và thời điểm gán request không trùng nhau, đồng thời dễ mang theo context cũ.

Trong reactive WebFlux, quá trình xử lý có thể đổi thread qua nhiều operator; ThreadLocal càng không phải phương tiện đáng tin để định danh một logical request. Với Reactor, **Context** là nơi phù hợp cho dữ liệu gắn với reactive subscriber khi không tiện truyền trực tiếp qua biến/hàm.

## 9. Qua ranh giới @Async / CompletableFuture: truyền snapshot, không mượn field

Giả sử HTTP controller đã xác thực người dùng và chuẩn hóa thông tin request. Sau đó cần chạy việc lâu trên executor:

~~~text
HTTP request trên thread xử lý
    |
    | lấy requestId, userId; kiểm tra quyền
    v
RequestSnapshot (giá trị riêng)
    |
    | truyền qua tham số
    v
@Async / CompletableFuture / worker
    |
    | chỉ dùng snapshot được truyền vào
    v
Kết quả/response hoặc xử lý nền có vòng đời rõ ràng
~~~

Ví dụ trong Spring (cần **@EnableAsync**, bean executor tên **appExecutor**, và gọi phương thức thông qua Spring proxy):

**RequestSnapshot.java**:

~~~java
package example.async;

public record RequestSnapshot(String requestId, String userId) {
}
~~~

**ReportWorker.java**:

~~~java
package example.async;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportWorker {
    @Async("appExecutor")
    public CompletableFuture<String> build(RequestSnapshot snapshot) {
        String result = "request=" + snapshot.requestId()
                + ", user=" + snapshot.userId();
        return CompletableFuture.completedFuture(result);
    }
}
~~~

Điểm chính: **snapshot là tham số đã được tạo trước khi rời request thread**. Spring proxy đưa phương thức @Async tới executor; mã bên trong chỉ đọc snapshot. Việc trả completedFuture trong phương thức này hợp lý vì phương thức đã được interceptor @Async chuyển sang luồng thực thi phù hợp, không cần bọc thêm supplyAsync chỉ để chuyển luồng một lần nữa.

Nếu dùng Java thuần, cũng truyền snapshot như vậy:

~~~java
CompletableFuture<String> result =
        CompletableFuture.supplyAsync(
                () -> generateFor(snapshot), executor);
~~~

Đây là **đoạn minh họa** với snapshot, generateFor và executor được khai báo sẵn. Lambda capture một *tham chiếu*; nếu snapshot hoặc object bên trong bị thread khác mutate thì vẫn có race. Dùng immutable DTO hoặc defensive copy khi crossing boundary.

**Những cảnh báo cần nhớ:**

- @Async **không tự mang theo mọi ThreadLocal**, ví dụ MDC log hoặc SecurityContext trong mọi cấu hình. Nếu thực sự cần propagation, dùng cơ chế được framework hỗ trợ, chẳng hạn TaskDecorator và wrapper tương ứng cho Spring Security; không tự sao chép bừa bãi các trạng thái bảo mật.
- @Async trong cấu hình proxy thông thường **không có tác dụng khi self-invocation** (một phương thức cùng bean gọi trực tiếp phương thức @Async của chính nó). Điều này liên quan cách Spring chuyển luồng, không phải giải pháp để cô lập state.
- Nếu tác vụ chạy **sau khi HTTP request kết thúc**, không đọc tùy tiện HttpServletRequest, request-scoped proxy hoặc RequestContextHolder ở worker. Tạo snapshot đủ dùng trước, xác định deadline và trách nhiệm dọn tài nguyên.
- Khi dùng callback **thenApply/whenComplete**, callback có thể chạy trên thread hoàn tất tương lai hoặc luồng đăng ký. Không giả định callback vẫn nằm trên “request thread”.
- Không đưa cả request body lớn, session, principal mutable hoặc đối tượng servlet request vào queue chỉ để giữ context; giữ dữ liệu nhỏ, an toàn, đúng nhu cầu.

## 10. Với WebFlux: cô lập theo subscription, không theo thread

Trong Spring WebFlux, request được biểu diễn bằng reactive pipeline. Nhiều subscription có thể dùng chung thread và một subscription có thể chuyển thread. Cách truyền dữ liệu qua tham số và tạo DTO riêng vẫn tốt; với metadata xuyên nhiều operator, **Reactor Context** giúp giữ thông tin theo chuỗi subscriber:

~~~java
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

Mono<String> response = Mono.deferContextual(view ->
        Mono.just("requestId=" + view.<String>get("requestId")))
    .contextWrite(Context.of("requestId", "req-42"));
~~~

Đây là **đoạn code minh họa trong phạm vi phương thức có Reactor dependency**. Khi subscribe, dữ liệu req-42 được truy cập từ context tương ứng; nó không đến từ một static/field currentRequest. Reactor Context không tự động biến mọi request thành có ID trên: application/filter cần cung cấp giá trị theo đúng pipeline.

Nếu bài toán chỉ cần truyền một DTO cho một hàm, hãy truyền DTO trực tiếp; không cần đẩy mọi tham số nghiệp vụ vào Reactor Context. Reactor Context phù hợp hơn với ngữ cảnh xuyên nhiều operator như tracing và các thông tin kỹ thuật cần thiết. Lưu ý trách nhiệm dọn/giới hạn dữ liệu khi pipeline hoàn tất và việc không đưa thông tin bí mật vào log.

## 11. Chọn cách giữ trạng thái đúng phạm vi

| Nhu cầu | Lựa chọn nên ưu tiên | Điều cần tránh |
| --- | --- | --- |
| Tên, filter, pagination của một HTTP request | Tham số phương thức, request DTO | Field mutable currentFilter trong singleton |
| Giá trị trung gian tính toán trong service | Biến cục bộ, object mới | Cache chung không có key và vòng đời rõ ràng |
| Response được tạo riêng cho caller | Immutable DTO/record | Tái sử dụng cùng response builder mutable |
| Mã request cần dùng ở vài tầng MVC | Truyền context rõ ràng; @RequestScope khi thực sự có ích | Biến static hoặc ThreadLocal thủ công kéo dài |
| Công việc @Async / executor | Snapshot bất biến truyền như tham số | Đọc request attributes từ worker sau request |
| Trace/logging xuyên task | Context propagation do hạ tầng quản lý | Tin rằng MDC tự chuyển thread |
| Dòng xử lý reactive | DTO qua operator; Reactor Context cho cross-cutting context | Gắn thông tin request vào ThreadLocal đơn thuần |
| Bộ đếm/caches dùng chung thật sự | Primitive thread-safe, ConcurrentHashMap, cơ chế đồng bộ đúng | Dùng field chung kiểu “current user” |
| State duy trì trong session | Cơ chế session có kiểm soát và đồng bộ khi cần | Xem session bean là chỉ một request tại một thời điểm |

**State dùng chung hợp lệ khác với request state.** Một metrics counter toàn hệ thống có thể là AtomicLong; một cache dùng chung có thể dùng concurrent collection; một client bất biến/thread-safe có thể inject vào singleton. Nhưng **AtomicReference<UserContext> currentUser** vẫn là một khe duy nhất cho mọi caller, nên giữ đúng tính nguyên tử không chữa sai lầm về quyền sở hữu request.

**Request scope cũng không thay thế authorization.** Mỗi request có context riêng không bảo đảm dữ liệu trong đó được xác thực, hoặc caller có quyền truy cập tài nguyên. Phải kiểm tra quyền và kiểm soát việc lộ dữ liệu riêng.

## 12. Kiểm thử tách biệt request

Đừng chỉ chạy tuần tự hai request rồi kết luận an toàn: bug chỉ bộc lộ khi có xen kẽ. Một chiến lược kiểm thử:

1. **Unit test đồng thời có kiểm soát** bằng CountDownLatch/CyclicBarrier để hai lời gọi thật sự chen vào đoạn ghi/đọc trạng thái, thay vì Thread.sleep và mong scheduler tạo race.
2. **Kiểm tra hai request có dữ liệu phân biệt**, chẳng hạn userId=An, userId=Bình, requestId khác nhau. Assert mỗi kết quả chỉ chứa dữ liệu của request tương ứng.
3. **Kiểm tra lỗi và hủy**: nếu xử lý thất bại, request context/ThreadLocal do ứng dụng quản lý có được giải phóng không? Pool có tái sử dụng worker mang context cũ không?
4. **Kiểm tra async boundary**: task hoạt động sau khi request thread kết thúc có thể xử lý từ snapshot, không truy vấn scope đã đóng.
5. **Kiểm tra shared dependencies**: service có thể stateless nhưng cache, mapper, builder hoặc collection được inject có mutable state. Đảm bảo loại object đó thật sự thread-safe hoặc được tạo riêng theo invocation.

Với test ứng dụng Spring, có thể kết hợp unit test với MockMvc/WebTestClient hoặc HTTP integration test phù hợp stack. Nếu muốn tái hiện race, nên kiểm soát điểm xen kẽ bằng latch trong fixture/test seam; một phép test đồng thời có lịch chạy không kiểm soát có thể pass rất nhiều lần rồi bất chợt thất bại.

## 13. Những hiểu nhầm thường gặp

**“Spring singleton có một instance nên chỉ một thread dùng nó tại một thời điểm.”** Sai. Nhiều request threads có thể gọi một singleton cùng lúc.

**“Mỗi lần vào method, field sẽ là bản riêng.”** Sai. Biến cục bộ thuộc invocation; field thuộc object. Nếu object là singleton, field được chia sẻ.

**“Thêm volatile/synchronized/AtomicReference vào currentRequest sẽ cô lập các request.”** Sai về mô hình dữ liệu. Đồng bộ có thể quản lý truy cập, nhưng không tạo ngữ cảnh riêng theo request.

**“Chỉ cần prototype là mỗi request có instance mới.”** Không nhất thiết. Prototype được phân phối khi lấy bean; inject một lần vào singleton không tự làm mới ở mọi request. Request scope có ngữ nghĩa riêng.

**“@RequestScope có một bean cho mỗi thread.”** Sai. Scope dựa trên HTTP request đang hoạt động, không dựa trên danh tính thread.

**“ThreadLocal tồn tại đúng bằng một HTTP request.”** Sai. Thread thường được tái sử dụng; task async có thể chạy ở thread khác. Phải có cơ chế bind/cleanup và propagation được quản lý.

**“record luôn deep immutable.”** Sai. Record ngăn thay đổi tham chiếu thành phần sau khi tạo, nhưng thành phần trỏ đến mutable collection/object vẫn có thể bị sửa.

**“Không có field mutable trong service thì ứng dụng chắc chắn thread-safe.”** Chưa đủ. Dependencies, static state, mutable input được chia sẻ, I/O clients và transaction/session boundaries đều cần xem xét.

**“Dùng @Async rồi mới đọc request-scoped bean cũng giống request thread.”** Không được bảo đảm. Cần truyền snapshot hoặc sử dụng propagation được hỗ trợ với vòng đời hợp lệ; không đánh đồng request scope với executor task scope.

## 14. Quy trình rà soát trước khi đưa lên production

Khi xem một controller/service xử lý concurrent request, hãy tự hỏi:

1. **Dữ liệu này thuộc ai?** Một HTTP request, một user session, một workflow hay toàn ứng dụng?
2. **Nó đang nằm ở đâu?** Tham số/local, DTO mới, field bean, static, ThreadLocal, request scope hay shared cache?
3. **Ai có thể đọc/ghi đồng thời?** Nhiều HTTP request, cùng session mở nhiều tab, worker @Async, CompletableFuture callbacks?
4. **Có cần chia sẻ không?** Nếu không, truyền qua tham số hoặc tạo object mới; nếu có, xác định scope và cơ chế đồng bộ đúng.
5. **Ranh giới async nằm ở đâu?** Snapshot nào cần đi theo task; thông tin nào có thể biến mất khi request kết thúc?
6. **Cleanup và bảo mật ra sao?** Giá trị ThreadLocal, dữ liệu nhạy cảm, lifecycle của worker và request-scoped object có được xử lý phù hợp?
7. **Có test xen kẽ thật sự chưa?** Dùng công cụ điều phối thread trong test để kiểm tra dữ liệu của từng request không nhiễm nhau.

Ví dụ sửa anti-pattern ngắn:

~~~text
SAI:
SingletonService.currentUser = request.user
SingletonService.process() đọc currentUser

ĐÚNG:
RequestSnapshot snapshot = lấy dữ liệu đúng của request
SingletonService.process(snapshot) chỉ đọc dữ liệu được truyền
~~~

Nếu một dependency chung cần giữ dữ liệu nghiệp vụ dài hạn, hãy dùng kho lưu trữ có định danh cụ thể và cơ chế concurrency thích hợp (ví dụ DB transaction, optimistic locking, cache theo key); đừng dùng field currentUser làm nơi lưu tạm.

## 15. Tự kiểm tra

**Câu 1.** Một bean @Service không khai báo scope, hai request gọi đồng thời. Mỗi request có service riêng không?

**Đáp án:** Không. Theo mặc định, bean là singleton trong Spring container. Hai invocation có tham số/local riêng nhưng cùng thấy các instance field.

**Câu 2.** Thay currentUser bằng AtomicReference<String> có bảo đảm A không đọc nhầm user của B?

**Đáp án:** Không. AtomicReference quản lý thao tác trên *một tham chiếu chia sẻ*, nhưng A và B vẫn dùng chung ô currentUser.

**Câu 3.** Nếu một method chỉ có String userId làm tham số, tạo String result từ userId và trả về, tại sao thường an toàn khi gọi đồng thời?

**Đáp án:** Các binding tham số/local tách theo invocation; miễn không truy cập hay mutate object chung không an toàn ở sâu bên dưới.

**Câu 4.** Request-scoped bean được inject vào singleton hoạt động ra sao?

**Đáp án:** Với @RequestScope mặc định, singleton thường giữ scoped proxy; proxy giải quyết target thuộc request đang active khi được gọi. Không được giả định proxy hoạt động ở bất kỳ executor thread nào sau đó.

**Câu 5.** Một @Async task cần requestId sau khi HTTP response đã hoàn tất; nên lấy dữ liệu bằng RequestContextHolder ở worker không?

**Đáp án:** Không nên phụ thuộc vào request context của thread HTTP. Tạo immutable snapshot trước ranh giới và truyền cho task; tracing/security propagation cần cơ chế chuyên dụng.

**Câu 6.** Có phải mọi field trong singleton đều bị cấm?

**Đáp án:** Không. Dependencies stateless/thread-safe, cấu hình bất biến, metrics và cache được đồng bộ đúng vẫn phù hợp. Cái sai là lưu state vốn thuộc một request vào ô nhớ chia sẻ không có định danh sở hữu.

## Kết luận

**Scope của Spring quyết định vòng đời object; lời gọi concurrent quyết định ai có thể truy cập nó; mô hình dữ liệu quyết định trạng thái thuộc về ai.** Với request-specific data, lựa chọn mặc định nên là **singleton stateless + tham số + DTO/record riêng**. Chỉ dùng **@RequestScope** khi thực sự cần đối tượng tồn tại xuyên các thành phần trong cùng request, và hiểu cơ chế proxy/lifecycle. Không sử dụng field singleton, ThreadLocal hay prototype như giải pháp ngầm để giữ “request hiện tại”. Khi chuyển sang **@Async, CompletableFuture hoặc Reactor**, tiếp tục giữ quyền sở hữu dữ liệu rõ ràng bằng snapshot hoặc context propagation được thiết kế đúng cho môi trường thực thi.

**Tra cứu thêm khi triển khai:** tài liệu Spring Framework về Bean Scopes, Request Scope, Task Execution and Scheduling/@Async, Spring MVC/WebFlux; JDK về Java Memory Model, CountDownLatch và ThreadLocal; Project Reactor về Context.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-state-005">Phép kiểm tra rồi cập nhật và thao tác nhiều bước cần bảo vệ</a>

<details>
<summary>Xem chi tiết</summary>

# Phép kiểm tra rồi cập nhật và thao tác nhiều bước cần bảo vệ

## 1. Vấn đề: một quyết định nghiệp vụ thường gồm nhiều bước

Trong ứng dụng Spring, một bean `@Service` thường có scope `singleton`: nhiều request HTTP, callback, tác vụ `@Async` hoặc job có thể sử dụng **cùng một đối tượng**. Nếu bean giữ trạng thái có thể thay đổi, các luồng có thể cùng đọc và ghi trạng thái đó.

Ví dụ, nghiệp vụ chỉ còn một suất đăng ký:

```java
// SAI khi nhiều thread cùng gọi và cùng dùng biến available
if (available > 0) {
    available--;
    return true;
}
return false;
```

Đoạn này gồm ít nhất hai hành động: **kiểm tra** điều kiện rồi **cập nhật** dữ liệu. Chúng không trở thành một thao tác nguyên tử chỉ vì nằm liền nhau trong mã nguồn. Hai luồng cùng thấy `available == 1`, đều kết luận “còn suất” rồi đều cấp thành công: hệ thống bán hai suất dù chỉ có một suất.

Đây là lỗi **check-then-act** (kiểm tra rồi hành động), một dạng **TOCTOU** (*time of check to time of use*). Vấn đề cốt lõi không phải thread nào nhanh hơn; đó là dữ liệu có thể thay đổi **giữa thời điểm kiểm tra và thời điểm hành động**.

Các biến thể trong thực tế:

- **Check-then-act:** “Nếu còn hàng thì giảm tồn kho”; “Nếu chưa có bản ghi thì tạo”; “Nếu chưa xử lý request này thì xử lý”.
- **Read-modify-write:** `counter++`, `balance = balance - amount`, `visits.put(key, visits.get(key) + 1)`.
- **Compound action (thao tác nhiều bước):** chuyển tiền phải giảm một tài khoản và tăng tài khoản khác; đặt vé phải giảm `available`, tăng `reserved` rồi tạo phiếu; cập nhật cấu hình phải đổi nhiều trường trong một trạng thái hợp lệ.
- **Check-then-use tài nguyên bên ngoài:** kiểm tra file tồn tại rồi mở file, kiểm tra quyền rồi thực hiện thao tác, kiểm tra DB chưa có bản ghi rồi chèn. Các bước ở những hệ thống khác nhau thường không thể bảo vệ chỉ bằng một lock Java.

**Mục tiêu học tập:** nhận diện ranh giới nguyên tử của quy tắc nghiệp vụ, chọn một cơ chế bảo vệ nhất quán, hiểu phạm vi bảo vệ của từng cơ chế, và kiểm chứng tính đúng đắn khi có nhiều request đồng thời.

## 2. Nhìn thấy race condition bằng lịch xen kẽ

Ban đầu `available = 1`. Hai request A và B chạy trên hai luồng:

| Thời điểm | Thread A | Thread B | Kết quả |
| --- | --- | --- | --- |
| T1 | Đọc `available == 1` | | A thấy còn suất |
| T2 | | Đọc `available == 1` | B cũng thấy còn suất |
| T3 | Cập nhật, trả thành công | | A được cấp |
| T4 | | Cập nhật, trả thành công | B cũng được cấp |

Ngay cả khi hai lệnh giảm dùng thao tác atomic riêng lẻ, **phần kiểm tra và phần giảm vẫn có thể bị xen vào giữa**. Với `AtomicInteger`, mẫu sau **vẫn sai**:

```java
private final AtomicInteger available = new AtomicInteger(1);

public boolean reserveWrong() {
    if (available.get() <= 0) {
        return false;
    }
    available.getAndDecrement(); // Bản thân phép giảm atomic, cả quy trình thì không.
    return true;
}
```

Khi A và B đều vượt qua `get()` trước khi một bên giảm, cả hai có thể gọi `getAndDecrement()`. Kết quả có thể là `available == -1` và cả hai request đều được xác nhận thành công.

Cần phân biệt ba thuộc tính:

1. **Atomicity (tính nguyên tử):** các bước liên quan đến một quy tắc nghiệp vụ được nhìn nhận như một quyết định không bị chen ngang bởi thao tác cạnh tranh.
2. **Visibility (tính nhìn thấy):** một thread có nhìn thấy cập nhật của thread khác theo quy tắc Java Memory Model hay không.
3. **Consistency/invariant (bất biến nghiệp vụ):** những điều luôn phải đúng, chẳng hạn `available >= 0` và `available + reserved == capacity`.

`volatile` hỗ trợ visibility và quy tắc thứ tự nhất định, nhưng **không làm nguyên tử** chuỗi “đọc → kiểm tra → ghi”, cũng không khiến `counter++` trở thành atomic. `AtomicInteger` bảo vệ thao tác riêng lẻ và cung cấp CAS; cần **CAS loop** hoặc một lock bao trọn cả quyết định để giải quyết check-then-act.

## 3. Xác định ranh giới cần bảo vệ

Một giải pháp đúng bắt đầu từ **bất biến**, không bắt đầu từ tên annotation.

Giả sử tổng số suất ban đầu là 5; bean lưu hai trường `available` và `reserved`. Bất biến cần giữ là:

```text
available >= 0
reserved >= 0
available + reserved == 5
```

Một lượt đặt chỗ phải làm **trọn** các bước “kiểm tra còn suất → giảm available → tăng reserved” trong cùng một vùng bảo vệ. Nếu chỉ đồng bộ lệnh giảm, lệnh kiểm tra vẫn có race. Nếu đồng bộ cập nhật nhưng cho phép thread khác đọc hai trường rời rạc, người đọc có thể quan sát snapshot thiếu nhất quán.

Cần xác định:

- **Trạng thái nào được chia sẻ?** Field của bean singleton, cache, static field, map, database row, hay tài nguyên hệ thống khác?
- **Ai có thể truy cập?** Request threads, executor workers, scheduled tasks, listener, các bean khác, hoặc nhiều instance ứng dụng?
- **Bất biến nào phải giữ?** Không âm, không cấp trùng, tổng phải bảo toàn, không thể chuyển từ trạng thái cuối trở lại trạng thái đầu, v.v.
- **Đơn vị nguyên tử là gì?** Một biến, một khóa trong map, nhiều trường của một object, nhiều DB rows, hay nhiều hệ thống bên ngoài?
- **Phạm vi bảo vệ ở đâu?** Trong một object/JVM, một transaction DB, hoặc một hệ phân tán?

Một phần mã được gọi là “thread-safe” chưa đủ để suy ra **cả workflow** thread-safe. Ví dụ, `ConcurrentHashMap` an toàn cho từng thao tác được bảo đảm, nhưng đoạn `containsKey(key)` rồi `put(key, value)` là hai thao tác riêng và vẫn có cửa sổ race.

## 4. Ví dụ Java 17 chạy độc lập: tái hiện lỗi và sửa

Ví dụ sau **cố ý** sử dụng `CountDownLatch` để hai worker cùng đi qua bước kiểm tra trước khi thực hiện bước giảm. Nhờ thế kết quả lỗi có thể tái hiện xác định, không dựa vào may rủi của scheduler. Sau đó so sánh hai cách sửa: CAS cho một biến và lock cho bất biến nhiều trường.

Tạo tệp **`CheckThenActDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class CheckThenActDemo {
    private static final class BrokenInventory {
        private final AtomicInteger remaining = new AtomicInteger(1);

        boolean reserve(CountDownLatch bothChecked, CountDownLatch proceed)
                throws InterruptedException {
            if (remaining.get() <= 0) {
                return false;
            }

            // Demo: buộc cả hai luồng vượt qua check trước khi ai được giảm.
            bothChecked.countDown();
            proceed.await();

            remaining.getAndDecrement();
            return true;
        }

        int remaining() {
            return remaining.get();
        }
    }

    private static final class CasInventory {
        private final AtomicInteger remaining = new AtomicInteger(1);

        boolean reserve() {
            while (true) {
                int observed = remaining.get();
                if (observed <= 0) {
                    return false;
                }
                if (remaining.compareAndSet(observed, observed - 1)) {
                    return true;
                }
                // Có thread khác thay đổi giá trị: đọc lại và thử tiếp.
            }
        }

        int remaining() {
            return remaining.get();
        }
    }

    private static final class ProtectedLedger {
        private final Object lock = new Object();
        private int available = 1;
        private int reserved = 0;

        boolean reserve() {
            synchronized (lock) {
                if (available <= 0) {
                    return false;
                }
                available--;
                reserved++;
                return true;
            }
        }

        Snapshot snapshot() {
            synchronized (lock) {
                return new Snapshot(available, reserved);
            }
        }
    }

    private record Snapshot(int available, int reserved) {}

    private static int accepted(Future<Boolean> first, Future<Boolean> second)
            throws Exception {
        return (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            BrokenInventory broken = new BrokenInventory();
            CountDownLatch checked = new CountDownLatch(2);
            CountDownLatch proceed = new CountDownLatch(1);

            Future<Boolean> b1 = pool.submit(() -> broken.reserve(checked, proceed));
            Future<Boolean> b2 = pool.submit(() -> broken.reserve(checked, proceed));
            checked.await();
            proceed.countDown();

            int brokenAccepted = accepted(b1, b2);
            System.out.println("Broken accepted: " + brokenAccepted);
            System.out.println("Broken remaining: " + broken.remaining());

            CasInventory cas = new CasInventory();
            int casAccepted = accepted(pool.submit(cas::reserve),
                                       pool.submit(cas::reserve));
            System.out.println("CAS accepted: " + casAccepted);
            System.out.println("CAS remaining: " + cas.remaining());

            ProtectedLedger ledger = new ProtectedLedger();
            int lockAccepted = accepted(pool.submit(ledger::reserve),
                                        pool.submit(ledger::reserve));
            Snapshot current = ledger.snapshot();
            System.out.println("Lock accepted: " + lockAccepted);
            System.out.println("Lock available: " + current.available());
            System.out.println("Lock reserved: " + current.reserved());
        } finally {
            pool.shutdown();
        }
    }
}
```

Trong thư mục chứa tệp, dùng **JDK 17+**:

```bash
javac CheckThenActDemo.java
java CheckThenActDemo
```

Kết quả stdout:

```text
Broken accepted: 2
Broken remaining: -1
CAS accepted: 1
CAS remaining: 0
Lock accepted: 1
Lock available: 0
Lock reserved: 1
```

**Giải thích kết quả:**

- **Broken:** cả hai luồng đã kiểm tra giá trị dương trước khi `proceed` mở. Hai phép `getAndDecrement()` đều nguyên tử, nhưng quyết định cấp suất không nguyên tử, nên hai request được chấp nhận và tồn kho thành -1.
- **CAS:** khi còn 1 suất, chỉ một thread có thể đổi thành 0 bằng `compareAndSet(1, 0)`. Thread khác thử CAS sẽ thất bại, đọc lại 0 và trả `false`. Không có overselling.
- **Lock:** cả kiểm tra và hai cập nhật `available--`, `reserved++` nằm trong cùng monitor. `snapshot()` cũng lấy monitor đó nên không nhìn thấy nửa chừng một lần cập nhật. Tổng `available + reserved` luôn là 1 tại các điểm quan sát được bảo vệ.

Lưu ý: `CountDownLatch` trong nhánh Broken chỉ phục vụ tái hiện race có chủ đích; đó **không phải** cơ chế bảo vệ nghiệp vụ. Ví dụ không nói rằng một lịch thực thi luôn gây lỗi trong chương trình không dùng latch. Khi đánh giá một hệ thống thật, cần chứng minh bất biến đúng với mọi lịch xen kẽ có thể xảy ra.

## 5. Chọn đúng công cụ cho ranh giới nguyên tử

### 5.1. `synchronized` hoặc `ReentrantLock` khi bảo vệ một nhóm trạng thái

Cách trực quan cho invariant nhiều trường trong một JVM là cùng dùng một lock:

```java
private final Object stateLock = new Object();
private int available = 5;
private int reserved = 0;

public boolean tryReserve() {
    synchronized (stateLock) {
        if (available <= 0) {
            return false;
        }
        available--;
        reserved++;
        return true;
    }
}
```

Đây là snippet nằm trong một class có các field như khai báo, không phải chương trình standalone. Mọi phương thức đọc/sửa **các trường cùng bất biến** phải tuân thủ cùng một khóa. Lock chỉ có hiệu lực giữa những thread cùng truy cập **đúng đối tượng lock**. Tránh dùng string intern, class object hoặc đối tượng công khai làm monitor nếu code khác có thể khóa ngoài ý muốn. Dùng `private final Object` thể hiện quyền sở hữu rõ ràng.

`synchronized` vừa cung cấp mutual exclusion, vừa thiết lập quan hệ happens-before khi một thread mở monitor và thread khác sau đó lấy cùng monitor. Một snapshot nhất quán cần đọc tất cả trường liên quan khi đang giữ cùng lock.

`ReentrantLock` hữu ích khi cần `tryLock()`, timeout, khả năng interrupt trong lúc đợi lock hoặc các condition riêng. Phải mở khóa trong `finally`:

```java
lock.lock();
try {
    // Kiểm tra và cập nhật các trường cùng invariant.
} finally {
    lock.unlock();
}
```

Đây là snippet minh họa với biến `lock` là `ReentrantLock` được khởi tạo ở lớp. Không nên mở nhiều lock theo thứ tự tùy ý. Ví dụ chuyển tiền A→B khóa A rồi B, còn B→A khóa B rồi A có thể gây **deadlock**. Cần một thứ tự khóa nhất quán theo ID hoặc cơ chế dữ liệu phù hợp khác.

Không giữ lock qua lời gọi HTTP, truy cập mạng, `Thread.sleep`, callback không kiểm soát, hay xử lý dài nếu có thể tránh. Những công việc ấy giữ các request khác chờ và có thể gây deadlock. Nhưng cũng không được thả lock giữa “check” và “update” nếu invariant phụ thuộc cả hai bước: hãy thiết kế dữ liệu và ranh giới transaction để thu hẹp critical section **mà vẫn nguyên tử**.

### 5.2. `AtomicInteger` và CAS loop khi chỉ cần cập nhật một giá trị

Với counter hoặc quota lưu trong **một biến**, mẫu CAS trong ví dụ Java độc lập phù hợp:

```java
public boolean reserveOne(AtomicInteger remaining) {
    while (true) {
        int current = remaining.get();
        if (current <= 0) {
            return false;
        }
        if (remaining.compareAndSet(current, current - 1)) {
            return true;
        }
    }
}
```

Đây là snippet nhận một `AtomicInteger` chia sẻ giữa các thread. CAS làm thao tác “chỉ đổi nếu giá trị vẫn như vừa đọc” trở thành một quyết định nguyên tử trên **biến đó**. Nếu thất bại vì có cạnh tranh, vòng lặp phải **đọc lại** thay vì cố ghi theo giá trị cũ. Trong tải cạnh tranh, có thể phải thử nhiều lần; tránh đặt I/O hay side effect trong vòng retry.

Không dùng hai `AtomicInteger` riêng và mặc định chúng tạo ra transaction trên hai trường. Nếu invariant liên quan cả `available` và `reserved`, một thread khác vẫn có thể đọc trạng thái ở giữa hai phép cập nhật. Có thể gói các trường vào **một state immutable** rồi CAS cả reference, hoặc dùng lock để giải thích và duy trì tính đúng đắn đơn giản hơn.

### 5.3. Concurrent collections: atomic operation khác với chuỗi nhiều thao tác

Chẳng hạn, đánh dấu một khóa “đã xử lý” trong memory:

```java
private final java.util.Set<String> processed =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

public boolean markOnce(String requestId) {
    return processed.add(requestId); // true chỉ cho lần thêm thành công.
}
```

Đây là snippet trong class được chia sẻ. `add` trên concurrent key set hỗ trợ đánh dấu nguyên tử cho **một khóa**. Trái lại, `if (!processed.contains(id)) processed.add(id)` tách check và act, nên không thể dựa vào `contains` để kết luận ai có quyền xử lý.

`ConcurrentHashMap.putIfAbsent`, `compute` và `computeIfAbsent` cũng có thao tác atomic **theo key** theo hợp đồng API. Callback của `compute` cần ngắn, đơn giản, không sửa đệ quy cùng map và không gửi email/trừ tiền/gọi mạng bên trong: callback map không tạo transaction với hệ ngoài. Đối với điều kiện trải qua nhiều key, cần cơ chế phối hợp cao hơn.

`LongAdder` có thể tối ưu thống kê đếm có cạnh tranh cao, nhưng kết quả `sum()` khi đang cập nhật không phải snapshot nguyên tử của cả hệ thống. Đừng dùng `sum() >= limit` rồi `increment()` như cách giữ quota tuyệt đối.

## 6. Đặt cơ chế bảo vệ vào Spring bean đúng cách

Ví dụ sau là một **Spring-managed service** trong dự án Spring Boot/Spring Framework đã có dependencies tương ứng. Đây là class hoàn chỉnh để đưa vào mã nguồn dự án ở package phù hợp; không phải chương trình `javac` độc lập vì dùng annotation Spring.

**`LocalSeatService.java`:**

```java
import org.springframework.stereotype.Service;

@Service
public class LocalSeatService {
    private final Object stateLock = new Object();
    private int remaining = 3;
    private int booked = 0;

    public boolean bookOne() {
        synchronized (stateLock) {
            if (remaining <= 0) {
                return false;
            }
            remaining--;
            booked++;
            return true;
        }
    }

    public State state() {
        synchronized (stateLock) {
            return new State(remaining, booked);
        }
    }

    public record State(int remaining, int booked) {}
}
```

Một controller có thể gọi `bookOne()`; khi ba request cùng đến, tối đa ba lượt sẽ thành công **trong một instance bean**. Phương thức `state()` trả record immutable chứa hai giá trị được đọc đồng thời dưới cùng lock.

Các giả định phải được ghi rõ khi dùng cách này:

- **Mặc định `@Service` singleton trong một `ApplicationContext`.** Đây không có nghĩa là singleton cho toàn bộ cluster. Hai JVM/pod có hai `LocalSeatService` và hai lock riêng.
- **State đang ở RAM:** khởi động lại làm mất số đếm. Nếu nghiệp vụ là giữ chỗ thật, dữ liệu cần sống trong database; bean RAM không phải source of truth phù hợp.
- **Request scope hoặc prototype không phải thuốc chữa race:** object khác nhau có thể vẫn trỏ tới tài nguyên chung, hoặc một object vẫn bị dùng từ nhiều thread. Phân tích ownership thực tế quan trọng hơn đổi scope.
- **`@Async` không tự đảm bảo thread safety:** nó chuyển công việc qua executor và còn khiến việc truy cập đồng thời dễ xảy ra hơn nếu state dùng chung.
- **Bất biến phải được bảo vệ từ mọi đường vào:** HTTP endpoint, scheduled task, listener và các service khác không được cập nhật `remaining`/`booked` qua lối tắt.

Khi logic nghiệp vụ có cả thao tác DB, không nên giữ lock Java và gọi DB trong đó như một cách mặc định. Lock này chỉ bao phủ các thread trong một JVM và có thể giữ lâu do I/O; dùng cơ chế transaction/constraint/atomic update ở database cho state bền vững.

## 7. Multi-step trong database: `@Transactional` cần đi cùng cơ chế chống race

Một ví dụ sai thường gặp:

```java
@Transactional
public boolean reserveWrong(long eventId) {
    int available = jdbc.queryForObject(
            "SELECT available FROM event_capacity WHERE event_id = ?",
            Integer.class, eventId);

    if (available <= 0) {
        return false;
    }

    jdbc.update(
            "UPDATE event_capacity SET available = available - 1 WHERE event_id = ?",
            eventId);
    return true;
}
```

Đây là snippet minh họa với một `JdbcTemplate jdbc` đã được inject, không phải class standalone. **Chỉ thêm `@Transactional` không đủ để loại race** giữa hai transaction đồng thời trong cấu hình isolation thông dụng như READ COMMITTED: cả hai có thể đọc giá trị cũ trước khi update. Còn hành vi cụ thể khi lock/isolation xung đột tùy database. Không suy luận “transaction = tự động tuần tự hóa tất cả request”.

Khi quy tắc chỉ là “giảm 1 nếu còn suất”, có thể chuyển cả check và decrement thành **một câu UPDATE có điều kiện**:

```sql
UPDATE event_capacity
SET available = available - 1
WHERE event_id = :event_id
  AND available > 0;
```

Trong một RDBMS hỗ trợ transaction và row locking đúng chuẩn, câu lệnh này xét điều kiện và cập nhật nhất quán với các cập nhật cạnh tranh của hàng liên quan. **Số hàng bị ảnh hưởng** là căn cứ xác định thành công: `1` là giữ được suất; `0` là không còn suất hoặc ID không tồn tại. Phải xác minh hành vi với database/driver đang dùng.

Nếu sau đó còn tạo bản ghi đăng ký, hai thao tác DB cần thuộc cùng **một transaction**. Ví dụ Spring JDBC dưới đây có thể dùng trong ứng dụng đã cấu hình `JdbcTemplate`, datasource và transaction manager:

**`DatabaseReservationService.java` (snippet tích hợp Spring):**

```java
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseReservationService {
    private final JdbcTemplate jdbc;

    public DatabaseReservationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean reserve(long eventId, String requestId) {
        int updated = jdbc.update(
                "UPDATE event_capacity " +
                "SET available = available - 1 " +
                "WHERE event_id = ? AND available > 0",
                eventId);

        if (updated != 1) {
            return false;
        }

        jdbc.update(
                "INSERT INTO event_registration (request_id, event_id) VALUES (?, ?)",
                requestId, eventId);

        return true;
    }
}
```

Ví dụ giả định schema tối thiểu có `event_capacity(event_id PRIMARY KEY, available)`, `event_registration(request_id UNIQUE, event_id)`, ràng buộc `CHECK (available >= 0)` nếu database hỗ trợ, và dùng database transaction thực sự. `request_id` duy nhất ngăn tạo hai đăng ký có cùng định danh. Nếu INSERT vi phạm unique constraint và ném exception runtime từ Spring JDBC, transaction mặc định rollback cả phần giảm suất. Cần xử lý lỗi trùng `requestId` ở **biên gọi ngoài transaction** và tra lại kết quả cũ nếu nghiệp vụ yêu cầu retry idempotent; class minh họa trên chưa tự triển khai toàn bộ quy trình idempotency.

**Phân biệt rõ các cơ chế khác trong DB:**

| Cơ chế | Phù hợp khi | Lưu ý |
| --- | --- | --- |
| `UPDATE ... WHERE condition` + kiểm tra số hàng | Quy tắc có thể biểu diễn thành một cập nhật điều kiện | Thường đơn giản cho stock/quota; kiểm tra hàng bị ảnh hưởng và lỗi |
| Unique constraint | Không cho hai bản ghi có cùng khóa nghiệp vụ | `SELECT` trước khi `INSERT` không thay thế unique constraint |
| Optimistic locking với `@Version` | Nhiều request sửa cùng entity, xung đột tương đối hiếm | Transaction thất bại do version cũ cần chiến lược retry hoặc báo conflict |
| Pessimistic row lock, ví dụ `SELECT ... FOR UPDATE` hoặc JPA `PESSIMISTIC_WRITE` | Cần đọc và cập nhật nhiều bước trên các hàng có liên quan | Lock có thể chặn, deadlock, timeout; thứ tự khóa cần nhất quán |
| Isolation mạnh hơn, như SERIALIZABLE | Quy tắc phức tạp cần bảo đảm nhất quán theo transaction | Chi phí cạnh tranh và khả năng transaction bị abort/retry; tùy RDBMS |
| Queue/outbox hoặc workflow coordinator | Một nghiệp vụ liên quan DB cùng dịch vụ ngoài, phải chịu restart/retry | Không thể có atomicity cross-system chỉ bằng `synchronized` hay transaction DB thông thường |

Không gói gửi email hoặc gọi REST từ dịch vụ ngoài vào giữa lock/transaction DB và giả định chúng sẽ rollback cùng database. Nếu phải phát sự kiện sau khi ghi DB với bảo đảm không mất, cân nhắc transactional outbox và xử lý downstream có idempotency.

## 8. Những vấn đề thường làm lời giải “đã có lock” vẫn sai

### Lock sai phạm vi

`synchronized` trên một instance chỉ bảo vệ **instance ấy**. Nhiều instance ứng dụng dùng chung DB không chia sẻ monitor. Với dữ liệu bền vững, dùng DB atomic constraint/transaction hoặc cơ chế điều phối phân tán được thiết kế phù hợp. Distributed lock cần tính đến lease hết hạn, fencing token và failure; không nên dùng chỉ để thay cho một câu SQL có điều kiện.

### Đọc không cùng cơ chế bảo vệ

Nếu writer cập nhật `available` và `reserved` trong lock nhưng reader đọc chúng độc lập ngoài lock, reader có thể thấy snapshot “nửa cũ nửa mới”. Đọc nhiều trường liên quan cần cùng monitor, immutable snapshot được công bố đúng cách, hoặc transaction/snapshot đúng mức ở DB.

### “Mỗi lệnh đều thread-safe” nhưng workflow không thread-safe

`AtomicInteger.get()` và `getAndDecrement()` đều có tính atomic riêng nhưng ghép lại không bảo vệ điều kiện. Tương tự `ConcurrentHashMap.containsKey()` + `put()`, hoặc DB `SELECT` + `UPDATE` khi không có guard/lock phù hợp. Phải kiểm tra **toàn bộ quyết định**.

### Nhầm ranh giới của Spring proxy và transaction

Spring mặc định xử lý `@Transactional` bằng AOP proxy; lời gọi `this.methodAnnotated()` bên trong cùng bean thường không đi qua proxy, nên không tự mở transaction như mong đợi. Các transaction imperative gắn với thread; công việc được chuyển sang thread khác bằng `@Async` không tự động kế thừa transaction của thread gọi. Khi cần transaction trong task async, đặt ranh giới transaction tại chính service/method xử lý ở thread đó, thông qua cách gọi Spring quản lý phù hợp.

### Khóa quá rộng hoặc lồng khóa không nhất quán

Một lock toàn cục có thể giảm throughput mạnh, nhất là khi chứa I/O. Nhiều lock lấy theo thứ tự khác nhau có thể deadlock. Cần chọn đúng hạt khóa (per-key/per-account nếu phù hợp), thứ tự khóa cố định, timeout và metric về thời gian chờ. Tối ưu lock chỉ sau khi đã giữ được invariant.

### Lỗi, retry và tác vụ không lặp an toàn

Nếu cập nhật state rồi exception xảy ra trước khi ghi nhận kết quả, caller có thể retry và thực hiện lần hai. In-memory lock không giải quyết được crash/retry qua JVM. Cần request ID/idempotency key, unique constraint, transaction rollback, outbox hoặc các kỹ thuật nghiệp vụ tương ứng. Tính đúng đắn bao gồm cả **điều xảy ra khi lỗi**, không chỉ lúc hai luồng chạy bình thường.

## 9. Cách kiểm thử và đánh giá giải pháp

Bài toán concurrency không thể chứng minh bằng một lần chạy “thấy đúng”. Hãy kiểm tra bất biến sau nhiều lịch xen kẽ và thiết kế test có điểm đồng bộ chủ động:

1. **Xác định invariant:** số lượt cấp thành công không vượt quota; `remaining >= 0`; tổng các thành phần không đổi; một request ID chỉ được chấp nhận một lần.
2. **Tạo đối thủ đồng thời có kiểm soát:** dùng `CountDownLatch` hoặc `CyclicBarrier` để nhiều worker tiến gần điểm kiểm tra cùng lúc; test không nên dựa duy nhất vào `Thread.sleep`.
3. **Thu kết quả từng task:** đợi `Future.get()`/`join()` để không kiểm tra khi task chưa hoàn thành; xử lý exception; không giả định thứ tự thread hoàn thành.
4. **Đo invariant, không đo thứ tự log:** ví dụ với 1 suất và 2 đối thủ, phải có đúng một thành công, tồn kho 0; không quan trọng request A hay B thắng.
5. **Chạy cả điều kiện lỗi:** timeout, exception trong bước hai, retry cùng request ID, transaction rollback, restart và nhiều JVM nếu nghiệp vụ có.
6. **Quan sát giới hạn tài nguyên:** số request tranh lock, thời gian chờ, deadlock/timeout, throughput, lỗi DB và số lần optimistic-lock retry.

Test Java ở mục 4 chứng minh một lịch lỗi cụ thể của cách sai và kết quả đúng của hai cách sửa cho tình huống nhỏ. Nó **không phải chứng minh hình thức** rằng mọi triển khai ứng dụng đều đúng. Với state trong DB hoặc nhiều instance, test tích hợp thật phải dùng đúng database, mức isolation, cấu hình pool và topology triển khai mục tiêu.

## 10. Chọn hướng giải quyết cho từng tình huống

| Bài toán | Ranh giới nguyên tử cần thiết | Hướng ưu tiên |
| --- | --- | --- |
| Quota tạm trong RAM của một singleton bean trên một JVM | Một số nguyên | `AtomicInteger` + CAS loop hoặc synchronized |
| Hai hay nhiều field của cùng bean phải thỏa invariant | Toàn bộ nhóm trường | Một lock nhất quán / immutable state CAS; snapshot dưới cùng cơ chế |
| Dedupe một key trong concurrent cache | Một key | `putIfAbsent`/`newKeySet().add()`/`compute` thích hợp |
| Trừ tồn kho bền vững, nhiều pod cùng truy cập | Một hàng/điều kiện trong database | Conditional UPDATE + affected row + DB constraint |
| Chuyển số dư giữa hai tài khoản | Nhiều hàng và một transaction | Database transaction, locking/isolation, thứ tự cập nhật và retry |
| Nhận request lặp do mạng/queue | Request ID và kết quả lâu dài | Idempotency key, unique constraint, record trạng thái |
| Ghi DB rồi phát event đáng tin cậy | DB commit + giao event cuối cùng | Outbox/queue và consumer idempotent |

Có thể tự rà soát một phương thức bằng năm câu hỏi: **(1)** Quy tắc “chỉ khi...” đang kiểm tra điều gì? **(2)** Dữ liệu nào có thể đổi ngay sau khi kiểm tra? **(3)** Cơ chế nào làm toàn bộ quyết định nguyên tử? **(4)** Cơ chế đó bao phủ một object, một JVM, một database hay cả cụm? **(5)** Nếu crash hoặc retry ở giữa các bước thì bất biến và kết quả nghiệp vụ còn đúng không?

## 11. Tài liệu đối chiếu

- [Java SE 17 – AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): `get()`, `compareAndSet()`, `getAndDecrement()`.
- [Java SE 17 – ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): `putIfAbsent`, `compute` và bảo đảm atomic theo key.
- [Java SE 17 – Lock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Lock.html): acquire/release, interruptibility và `tryLock`.
- [Spring Framework – Understanding Declarative Transaction Implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): transaction proxy, thread-bound transaction và khác biệt với reactive.
- [Spring Framework – Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): quy tắc gọi qua proxy và self-invocation.

**Ghi nhớ:** Thread-safe của một field, collection hay statement không tự đảm bảo một **quy trình kiểm tra rồi cập nhật** là đúng. Bắt đầu bằng invariant, bao trọn điểm ra quyết định trong một cơ chế nguyên tử có phạm vi đủ rộng, rồi kiểm tra cả chạy đồng thời, lỗi, retry và nhiều instance.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-state-006">Chọn synchronized, Lock hoặc atomic theo phạm vi bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `synchronized`, `Lock` hoặc atomic theo phạm vi bất biến

**Module:** Spring Framework - concurrency  
**Nhóm:** Trạng thái bean  
**Mục tiêu:** Khi một bean Spring được nhiều luồng truy cập, xác định chính xác **bất biến cần bảo vệ**, những thao tác nào phải xảy ra như một đơn vị và ranh giới đồng bộ hóa cần rộng đến đâu; từ đó chọn `synchronized`, `Lock` hay các lớp `Atomic*` đúng ngữ cảnh.

## 1. Bắt đầu từ bất biến, không bắt đầu từ tên công cụ

**Bất biến (invariant)** là điều kiện phải luôn đúng đối với trạng thái được chia sẻ tại những điểm quan sát hợp lệ. Ví dụ:

- Một bộ đếm có thể tăng độc lập: mỗi lần gọi `increment()` tăng đúng một đơn vị, không bị mất cập nhật.
- Số lượng hàng còn có thể đặt không âm: `available >= 0`. Một lần đặt hàng là hành động **kiểm tra đủ hàng và trừ hàng**.
- Hai trường cùng mô tả một kho: `available >= 0`, `reserved >= 0`, `available + reserved == initialTotal`. Một lần đặt hàng phải cập nhật cả `available` và `reserved` trong **cùng một lần chuyển trạng thái logic**.
- Hai tài khoản ở cùng một sổ cái: tổng số dư không đổi khi chuyển tiền; khoản chuyển phải bảo vệ đồng thời cả hai tài khoản. Nếu dữ liệu ở cơ sở dữ liệu/nhiều dịch vụ, một khóa trong JVM không đủ.

Câu hỏi không phải “biến này đã được khóa chưa?” mà là:

1. **Đâu là trạng thái được chia sẻ?** Trường của singleton bean, đối tượng cache, collection, bản ghi DB hay nhiều tài nguyên?
2. **Điều kiện nào bắt buộc đúng?** Viết tiền điều kiện và hậu điều kiện rõ ràng.
3. **Một thao tác nghiệp vụ gồm những bước nào?** Có cần giữ chúng nguyên tử cùng nhau?
4. **Ai có thể đọc/ghi?** Nhiều HTTP request, `@Async`, `@Scheduled`, callback, nhiều instance ứng dụng?
5. **Ranh giới quan sát nằm ở đâu?** Một giá trị, một nhóm trường, nhiều đối tượng, một bản ghi DB hay nhiều hệ thống?
6. **Cần gì ngoài bảo vệ dữ liệu?** Thử lấy khóa không chặn, timeout, hủy khi ngắt, chờ điều kiện, đo độ trễ?

Từ đó mới lựa chọn cơ chế.

### Ví dụ race condition

Giả sử bean giữ `available = 1`. Cách viết sau **không an toàn** khi có hai luồng gọi cùng lúc:

```java
private int available = 1;
private int reserved = 0;

public boolean reserveOne() {
    if (available == 0) {
        return false;
    }
    available--;
    reserved++;
    return true;
}
```

Cả hai luồng có thể cùng quan sát `available > 0`, cùng đi qua nhánh kiểm tra và cùng báo đặt thành công. `available--` và `reserved++` đều là thao tác đọc–tính toán–ghi; bản thân chúng không phải một giao dịch nguyên tử. Có thể xảy ra bán vượt số lượng, mất cập nhật hoặc snapshot giữa chừng. Đặt `volatile` cho hai trường **không** biến ba bước kiểm tra/cập nhật thành một hành động nguyên tử.

## 2. Vì sao trạng thái trong Spring bean cần chú ý?

`@Component`, `@Service` và `@Repository` mặc định có **singleton scope**: một bean instance cho mỗi bean definition trong một Spring IoC container. Nhiều request thread hoặc worker có thể gọi cùng instance. Singleton chỉ mô tả **số lượng instance và vòng đời**, không tự bảo đảm an toàn luồng.

```java
@Service
public class VisitService {
    private int visits;

    public int registerVisit() {
        return ++visits;  // race condition khi nhiều luồng cùng gọi
    }
}
```

Đây là **đoạn minh họa nằm trong ứng dụng Spring** (cần `import org.springframework.stereotype.Service;`). Nhiều lời gọi có thể đọc cùng giá trị cũ và ghi đè kết quả nhau.

Một service **stateless** thường dễ dùng chung: dữ liệu của mỗi request nằm trong local variable, còn dependency là các đối tượng được dùng theo hợp đồng thread-safety của chúng. “Không có trường mutable” là điểm khởi đầu tốt nhưng không đủ nếu service vẫn sửa đổi đối tượng mutable do dependency chia sẻ.

Các scope khác cũng không chữa mọi race:

- `prototype` tạo instance theo lần yêu cầu bean, nhưng nếu một prototype được inject trực tiếp một lần vào singleton thì singleton giữ cùng instance đó.
- `request` tạo trạng thái riêng theo request, nhưng một request có thể gửi công việc sang thread khác; việc truyền context và vòng đời cần xử lý riêng.
- `session` vẫn có thể bị nhiều request đồng thời của **cùng session** truy cập.
- Mỗi replica ứng dụng có singleton bean của **riêng replica đó**; `synchronized` không khóa được một replica khác.

Vì vậy cần xác định trạng thái sống trong **một JVM** hay là dữ liệu nghiệp vụ **dùng chung giữa nhiều JVM và DB** trước khi chọn khóa.

## 3. Phân biệt atomicity, visibility và ordering

Ba thuộc tính liên quan nhưng khác nhau:

| Thuộc tính | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Atomicity** | Thao tác có thể bị luồng khác xen vào giữa các bước logic không? | `if (stock > 0) stock--` phải là một bước nghiệp vụ |
| **Visibility** | Luồng khác có thấy giá trị đã ghi hay vẫn thấy bản cũ? | Cờ dừng được một worker quan sát |
| **Ordering / happens-before** | Một ghi có được công bố đúng thứ tự trước một đọc liên quan? | Trạng thái được cập nhật dưới cùng khóa rồi đọc dưới khóa đó |

Trong Java Memory Model:

- Thoát `synchronized` trên **cùng monitor** có quan hệ happens-before với một lần vào monitor kế tiếp; thao tác dưới cùng monitor có tính loại trừ lẫn nhau.
- `Lock` chuẩn (chẳng hạn `ReentrantLock`) cung cấp hiệu ứng bộ nhớ tương ứng khi `unlock()` rồi `lock()` trên cùng lock.
- `AtomicInteger`, `AtomicLong`, `AtomicReference` cung cấp các thao tác nguyên tử và ngữ nghĩa bộ nhớ đã được tài liệu API định nghĩa; `compareAndSet` có hiệu ứng như truy cập volatile phù hợp.
- `volatile` phù hợp cho việc công bố/đọc **một giá trị** hoặc cờ mà không có thao tác phức hợp; `volatile int count; count++;` vẫn mất cập nhật.
- `final` giúp thiết kế đối tượng bất biến và công bố an toàn khi xây dựng đúng, nhưng không làm một object mutable bên trong trở thành bất biến.

**Nguyên tắc:** mọi đường **đọc và ghi** liên quan đến cùng bất biến phải tuân theo **cùng một giao thức đồng bộ**. Chỉ khóa các phương thức ghi mà để getter đọc nhiều trường trần trụi sẽ tạo snapshot không nhất quán.

## 4. Bảng quyết định nhanh theo ranh giới bất biến

| Bất biến và nhu cầu | Thường chọn | Vì sao | Dễ sai ở đâu? |
| --- | --- | --- | --- |
| Một bộ đếm độc lập, tăng/giảm/get | `AtomicInteger`/`AtomicLong` | Một thao tác cập nhật nguyên tử; không cần monitor | Tách `get()` và `incrementAndGet()` rồi tưởng là nguyên tử |
| Một số có điều kiện, ví dụ quota không âm | `AtomicInteger` + vòng CAS | Điều kiện và phép trừ được thực hiện như **một** cập nhật của **một giá trị** | `if (get() > 0) decrementAndGet()` không an toàn |
| Vài trường cần thay đổi cùng nhau | `synchronized` trên monitor chung | Dễ đọc, khó quên mở khóa; bảo vệ cả invariant và snapshot | Khóa từng field riêng, hoặc getter không khóa |
| Vài trường nhưng cần `tryLock`, timeout, xử lý interrupt, `Condition` | `ReentrantLock` | Điều khiển cách chờ/lấy khóa, cùng bảo vệ vùng tới hạn | Quên `unlock()` trong `finally`; dùng khác lock cho reader/writer |
| Nhiều trường nhưng có thể biểu diễn thành **một snapshot immutable** | `AtomicReference<State>` + CAS | Thay thế nguyên trạng thái logic bằng một thao tác | Mutable object bên trong state; side effect trong vòng CAS |
| Nhiều tác vụ đọc, ít ghi và đã đo contention | Có thể xem xét `ReentrantReadWriteLock` | Đọc đồng thời với nhau khi không có writer | Nhiều cơ chế khóa làm phức tạp; hiệu năng chưa chắc tốt |
| Nhiều JVM/replica hoặc dữ liệu DB phải nhất quán | DB constraint, transaction + conditional update/locking/optimistic version | Phạm vi bất biến vượt quá một JVM | Nhầm `synchronized` của một bean là khóa toàn hệ thống |

**Quy tắc ưu tiên:** Nếu cần khóa thông thường trong một JVM, bắt đầu với `synchronized`. Chọn `ReentrantLock` khi thật sự cần hành vi bổ sung. Dùng atomic khi toàn bộ phép cập nhật có thể biểu diễn đúng bằng **một trạng thái nguyên tử**; atomic không tự động tốt hơn khóa cho mọi bất biến.

## 5. `AtomicInteger`: một biến, có thể có điều kiện

Với thống kê độc lập:

```java
private final java.util.concurrent.atomic.AtomicLong totalRequests =
        new java.util.concurrent.atomic.AtomicLong();

public long onRequest() {
    return totalRequests.incrementAndGet();
}
```

Mỗi lần gọi có một tăng nguyên tử. Tuy vậy, nếu yêu cầu “chỉ tăng khi nhỏ hơn giới hạn”, mã sau vẫn sai:

```java
if (stock.get() > 0) {
    stock.decrementAndGet(); // kiểm tra và trừ là HAI thao tác
}
```

Hai luồng có thể cùng đọc `1` rồi cả hai trừ, làm `stock = -1`. Sửa bằng **compare-and-set (CAS)**:

```java
public boolean takeOne() {
    for (;;) {
        int current = stock.get();
        if (current == 0) {
            return false;
        }
        if (stock.compareAndSet(current, current - 1)) {
            return true;
        }
        // Có luồng khác cập nhật trước: đọc lại và thử tiếp.
    }
}
```

`compareAndSet(expected, update)` chỉ ghi khi giá trị hiện tại vẫn bằng `expected`. Nếu tranh chấp làm CAS thất bại, vòng lặp đọc lại; vì vậy không thể có hai lần trừ thành công từ cùng một đơn vị hàng. Với quota có thể âm do dữ liệu lỗi, nên kiểm tra `current <= 0`, không chỉ `== 0`; đồng thời validate dữ liệu khởi tạo.

**Giới hạn của atomic:** cập nhật một `AtomicInteger` không bảo vệ đồng thời `reserved`, một `Map`, một giao dịch DB hay thao tác gửi email. Khi bất biến kéo dài qua nhiều trường, có thể dùng một khóa chung hoặc gom các trường thành **một giá trị trạng thái bất biến**.

Các hàm `updateAndGet`/`getAndUpdate` có thể thử lại hàm cập nhật khi cạnh tranh CAS. Hàm cung cấp cho chúng phải **không có side effect** (không ghi log nghiệp vụ, gửi email, trừ tiền, gọi HTTP) vì nó có thể được gọi nhiều lần. `LongAdder` phù hợp bộ đếm thống kê nhiều ghi, nhưng `sum()` không đại diện một snapshot nguyên tử đối với các cập nhật đồng thời; tránh dùng nó khi cần điều kiện quota chính xác.

## 6. `synchronized`: bảo vệ nhiều trường bằng **một** monitor

Khi `available` và `reserved` phải giữ `available + reserved == total`, bao toàn bộ thao tác trong cùng một vùng tới hạn:

```java
private final Object mutex = new Object();
private int available = 10;
private int reserved;

public boolean reserveOne() {
    synchronized (mutex) {
        if (available == 0) {
            return false;
        }
        available--;
        reserved++;
        return true;
    }
}

public StockSnapshot snapshot() {
    synchronized (mutex) {
        return new StockSnapshot(available, reserved);
    }
}

public record StockSnapshot(int available, int reserved) {}
```

Đây là đoạn thành viên minh họa cho một class/bean; phần `record` cũng thuộc class đó. Khi cả hai số bắt đầu bằng `10` và `0`, sau mỗi `reserveOne()` thành công, tổng luôn là `10`. Getter phải đồng bộ theo **cùng** `mutex` để nhận ảnh chụp nhất quán. Đối tượng `StockSnapshot` bất biến nên có thể trả ra ngoài khóa.

**Đặc tính cần nhớ:**

- `synchronized (mutex)` dùng monitor của **chính đối tượng `mutex`**; mỗi monitor là một vùng loại trừ độc lập. Hai object `new Object()` khác nhau **không** phối hợp với nhau.
- Khi ra khỏi block, kể cả do exception, JVM tự nhả monitor. Đây là lý do `synchronized` thường là lựa chọn dễ bảo trì.
- `synchronized` có tính reentrant: luồng đang giữ monitor có thể vào lại monitor đó; không có API trực tiếp cho `tryLock(timeout)` hoặc chờ lấy khóa có thể bị interrupt.
- `synchronized` trên `this` hoặc method `synchronized` cũng hợp lệ về mặt ngôn ngữ, nhưng `private final Object mutex` giúp tránh việc code ngoài tình cờ sử dụng cùng monitor. Không khóa trên String literal, boxed integer hay đối tượng dùng chung không do mình sở hữu.
- Giữ vùng khóa ngắn: tính toán/chuyển trạng thái trong RAM; tránh giữ khóa khi chờ mạng, cơ sở dữ liệu, sleep hoặc gọi callback không kiểm soát. Khóa quá rộng làm tăng độ trễ và nguy cơ deadlock.
- Nếu bảo vệ hai đối tượng bằng hai khóa khác nhau, cần thứ tự lấy khóa thống nhất để tránh thread A giữ khóa 1 đợi khóa 2 và thread B giữ khóa 2 đợi khóa 1.

Không cần thêm `volatile` cho `available`/`reserved` nếu **tất cả** truy cập liên quan đều diễn ra dưới cùng monitor; giao thức khóa cung cấp cả visibility lẫn atomicity cần thiết.

## 7. `ReentrantLock`: cùng bài toán, thêm khả năng điều khiển chờ

`java.util.concurrent.locks.Lock` là interface; `ReentrantLock` là implementation phổ biến. Với nhiều trường cùng bất biến, cách bảo vệ giống `synchronized`, chỉ khác phải quản lý vòng đời khóa tường minh:

```java
private final java.util.concurrent.locks.ReentrantLock lock =
        new java.util.concurrent.locks.ReentrantLock();

private int available = 10;
private int reserved;

public boolean reserveOne() {
    lock.lock();
    try {
        if (available == 0) {
            return false;
        }
        available--;
        reserved++;
        return true;
    } finally {
        lock.unlock();
    }
}
```

Đây là một đoạn thành viên Java minh họa, không phải chương trình độc lập. **Luôn** gọi `unlock()` trong `finally` sau khi chắc chắn đã lấy khóa; nếu có lỗi giữa chừng và quên nhả khóa, các yêu cầu khác có thể bị treo.

`Lock` đáng chọn khi cần:

- `tryLock()`: có thể trả về ngay khi hệ thống bận thay vì chờ.
- `tryLock(timeout, unit)`: chờ tối đa một khoảng thời gian và hỗ trợ interrupt trong thời gian đợi.
- `lockInterruptibly()`: cho phép hủy khi luồng đang chờ lấy khóa.
- `Condition`: nhiều hàng chờ điều kiện gắn với cùng `Lock`, dùng `await()`/`signal()` theo giao thức khóa. `await()` nhả khóa trong lúc chờ và lấy lại trước khi tiếp tục.

Ví dụ riêng cho chính sách “không chờ lâu”:

```java
public boolean reserveWithTimeout(
        int quantity, long timeoutMillis) throws InterruptedException {
    if (quantity <= 0) {
        throw new IllegalArgumentException("quantity must be positive");
    }
    if (!lock.tryLock(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
        return false; // Hết thời gian đợi lấy khóa.
    }
    try {
        if (available < quantity) {
            return false; // Không đủ hàng.
        }
        available -= quantity;
        reserved += quantity;
        return true;
    } finally {
        lock.unlock();
    }
}
```

Ví dụ trên **giả định** có các field `lock`, `available`, `reserved` như đoạn trước; đây không phải class độc lập. Trong API thật, nên phân biệt “hết thời gian đợi khóa” với “không đủ hàng” bằng enum/result type hoặc lỗi có mã, thay vì gộp vào một `false`.

`new ReentrantLock(true)` yêu cầu chính sách fairness khi có tranh chấp, nhưng có thể giảm throughput và không có nghĩa mọi lệnh lấy khóa đều theo thứ tự tuyệt đối (đặc biệt bản `tryLock()` không timeout). **Không** chọn `ReentrantLock` chỉ vì nghĩ nó luôn nhanh hơn `synchronized`; hãy đo đặc tính tải và chọn theo hành vi cần thiết.

## 8. `AtomicReference` khi nhiều field tạo thành một trạng thái immutable

Một bất biến gồm nhiều trường không bắt buộc phải dùng monitor nếu có thể **gói tất cả trường thành một object immutable** và chỉ thay nguyên trạng thái bằng CAS:

```java
import java.util.concurrent.atomic.AtomicReference;

record StockState(int available, int reserved) {}

final class StockCas {
    private final AtomicReference<StockState> current =
            new AtomicReference<>(new StockState(10, 0));

    boolean reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        for (;;) {
            StockState before = current.get();
            if (before.available() < quantity) {
                return false;
            }
            StockState after = new StockState(
                    before.available() - quantity,
                    before.reserved() + quantity);
            if (current.compareAndSet(before, after)) {
                return true;
            }
        }
    }

    StockState snapshot() {
        return current.get();
    }
}
```

Đây là ví dụ Java 17+ độc lập về cú pháp, gồm top-level package-private `StockState` và `StockCas`; để chạy thử cần thêm một class có `main` hoặc đặt vào test. `StockState` là `record` chỉ có hai số nguyên nên bản thân nó bất biến. Cùng một lần CAS thay cả cặp giá trị `available`/`reserved`, bảo vệ tổng và cung cấp snapshot nhất quán.

**Đánh đổi:** khi tranh chấp cao, CAS có thể phải thử lại và cấp phát nhiều snapshot; nên đo trước khi tối ưu. Vòng CAS không được chứa side effect vì có thể chạy lặp. `AtomicReference` chỉ bảo đảm thao tác trên **tham chiếu** là nguyên tử; nếu tham chiếu trỏ tới `List` hoặc object mutable rồi sửa nó tại chỗ, bạn vẫn có race.

## 9. Chương trình Java 17 chạy được: cùng một tải, ba cách bảo vệ

Ví dụ hoàn chỉnh này cố tình chạy 100 lần “lấy một đơn vị” trên một pool có 8 thread, nhưng mỗi kho chỉ có 10 đơn vị. Nó cho thấy kết quả ổn định dù lịch chạy thread không xác định. `AtomicQuota` bảo vệ **một** số lượng; `MonitorLedger` và `LockLedger` bảo vệ **cặp** `available`/`reserved`.

Lưu **nguyên văn** vào `ConcurrencyChoiceDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

public class ConcurrencyChoiceDemo {

    record Snapshot(int available, int reserved) {}

    static final class AtomicQuota {
        private final AtomicInteger available;

        AtomicQuota(int initial) {
            if (initial < 0) {
                throw new IllegalArgumentException("initial < 0");
            }
            available = new AtomicInteger(initial);
        }

        boolean takeOne() {
            for (;;) {
                int before = available.get();
                if (before <= 0) {
                    return false;
                }
                if (available.compareAndSet(before, before - 1)) {
                    return true;
                }
            }
        }

        int remaining() {
            return available.get();
        }
    }

    static final class MonitorLedger {
        private final Object mutex = new Object();
        private int available;
        private int reserved;

        MonitorLedger(int initial) {
            if (initial < 0) {
                throw new IllegalArgumentException("initial < 0");
            }
            available = initial;
        }

        boolean reserveOne() {
            synchronized (mutex) {
                if (available == 0) {
                    return false;
                }
                available--;
                reserved++;
                return true;
            }
        }

        Snapshot snapshot() {
            synchronized (mutex) {
                return new Snapshot(available, reserved);
            }
        }
    }

    static final class LockLedger {
        private final ReentrantLock lock = new ReentrantLock();
        private int available;
        private int reserved;

        LockLedger(int initial) {
            if (initial < 0) {
                throw new IllegalArgumentException("initial < 0");
            }
            available = initial;
        }

        boolean reserveOne() {
            lock.lock();
            try {
                if (available == 0) {
                    return false;
                }
                available--;
                reserved++;
                return true;
            } finally {
                lock.unlock();
            }
        }

        Snapshot snapshot() {
            lock.lock();
            try {
                return new Snapshot(available, reserved);
            } finally {
                lock.unlock();
            }
        }
    }

    static int compete(int attempts, BooleanSupplier operation) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return operation.getAsBoolean();
                }));
            }

            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            return accepted;
        } finally {
            start.countDown(); // Tránh để các task đang chờ kẹt nếu có lỗi.
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicQuota atomic = new AtomicQuota(10);
        int atomicAccepted = compete(100, atomic::takeOne);
        System.out.printf(
                "atomic: accepted=%d remaining=%d%n",
                atomicAccepted, atomic.remaining());

        MonitorLedger monitor = new MonitorLedger(10);
        int monitorAccepted = compete(100, monitor::reserveOne);
        Snapshot monitorState = monitor.snapshot();
        System.out.printf(
                "synchronized: accepted=%d available=%d reserved=%d%n",
                monitorAccepted, monitorState.available(), monitorState.reserved());

        LockLedger locking = new LockLedger(10);
        int lockAccepted = compete(100, locking::reserveOne);
        Snapshot lockState = locking.snapshot();
        System.out.printf(
                "lock: accepted=%d available=%d reserved=%d%n",
                lockAccepted, lockState.available(), lockState.reserved());
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac ConcurrencyChoiceDemo.java
java ConcurrencyChoiceDemo
```

**Stdout chính xác khi chương trình hoàn tất bình thường:**

```text
atomic: accepted=10 remaining=0
synchronized: accepted=10 available=0 reserved=10
lock: accepted=10 available=0 reserved=10
```

Vì mỗi phương án có 10 đơn vị và không nạp lại, **đúng 10** trong 100 lần gọi thành công. Lịch chạy 8 worker và thứ tự request thắng quyền đặt hàng không được xác định, nhưng số lần thành công và trạng thái cuối đều xác định. `CountDownLatch` chỉ giúp nhiều task cùng bắt đầu tranh chấp; chính CAS/monitor/lock mới bảo vệ invariant. Dùng `Future.get()` tại chương trình **thử nghiệm** để chờ tất cả task kết thúc trước khi in, không coi nó là kỹ thuật cần đưa vào request thread của API thực.

Điểm học được từ chương trình:

- `AtomicQuota` chỉ cần bảo vệ **một giá trị** `available`.
- `MonitorLedger` cập nhật hai trường cùng một monitor, và `snapshot()` đọc chúng cùng một monitor.
- `LockLedger` đạt cùng tính đúng đắn với khóa tường minh; lợi ích bổ sung sẽ đến khi cần `tryLock`/timeout/interrupt/condition, không phải vì ví dụ này nhất thiết chạy nhanh hơn.

## 10. Đưa quyết định vào một singleton `@Service`

Đây là phiên bản Spring của bài toán hai trường, dùng monitor private. Đặt trong `src/main/java/com/example/inventory/InventoryService.java` của một ứng dụng Spring Boot có dependency Spring Context (chẳng hạn `spring-boot-starter-web`):

```java
package com.example.inventory;

import org.springframework.stereotype.Service;

@Service
public class InventoryService {
    private final Object mutex = new Object();
    private int available = 10;
    private int reserved;

    public boolean reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        synchronized (mutex) {
            if (available < quantity) {
                return false;
            }
            available -= quantity;
            reserved += quantity;
            return true;
        }
    }

    public Snapshot snapshot() {
        synchronized (mutex) {
            return new Snapshot(available, reserved);
        }
    }

    public record Snapshot(int available, int reserved) {}
}
```

Đây là **file class hoàn chỉnh để đặt trong dự án Spring**, không phải chương trình có `main` tự chạy bằng `javac` không classpath. Với một instance bean trong một JVM, tất cả request thread đi qua bean này và chỉ truy cập trạng thái bằng hai method trên sẽ không thể làm `available` âm hoặc tạo snapshot có tổng khác 10.

Một controller có thể gọi `reserve(quantity)` rồi trả mã lỗi khi không đủ hàng; controller không cần tự khóa nữa vì service giữ ranh giới nghiệp vụ. Nếu một method khác sửa thẳng `available`/`reserved`, dùng khóa khác, hoặc bean có các bản sao không chia sẻ cùng object, cam kết đó bị phá.

Lựa chọn **tốt hơn nếu không cần state trong RAM** là thiết kế singleton service không giữ số hàng trong field mà giao invariant cho repository/DB. Một singleton stateless cùng transaction/SQL đúng thường dễ vận hành hơn khi có nhiều replica.

### Một vài trường hợp Spring dễ nhầm

- `@Async`, `@Scheduled` và request handler có thể cùng chạm singleton; thread được tạo bởi Spring không tự đồng bộ trạng thái của bean.
- `@Transactional` quản lý transaction của cơ sở dữ liệu theo transaction manager; nó **không** biến phép `++` trên field Java thành nguyên tử, cũng không tạo một mutex JVM cho bean.
- `@Transactional` trên một method cập nhật DB không tự giải quyết mọi race kiểm tra-rồi-ghi; cần mức isolation/locking/version/SQL phù hợp với bất biến.
- Nếu một method thực hiện I/O khi đang giữ `synchronized`/`Lock`, nhiều request khác có thể phải chờ lâu; cân nhắc chia nhỏ hoặc chuyển invariant về nơi dữ liệu thực sự sống.

## 11. Ranh giới của khóa trong JVM: database và nhiều instance

Giả sử chạy **ba replica** Spring Boot, mỗi replica có `InventoryService` riêng. `synchronized (mutex)` ở replica A **không biết** đến lock ở B/C. Nếu cả ba cùng đọc số tồn kho trong DB rồi giảm, một monitor local không ngăn được bán vượt kho.

Với **một bản ghi tồn kho trong DB**, có thể dùng cập nhật có điều kiện và kiểm tra số hàng ảnh hưởng (cú pháp SQL minh họa; áp dụng theo dialect/schema thực tế):

```sql
UPDATE inventory
SET available = available - :quantity,
    reserved = reserved + :quantity
WHERE id = :id
  AND available >= :quantity;
```

Nếu `quantity > 0` đã được kiểm tra và đúng **một** hàng bị cập nhật, giao dịch DB đã làm chuyển trạng thái của cùng bản ghi theo điều kiện. Nếu **0** hàng, có thể không đủ hàng hoặc không tìm thấy bản ghi; API phải phân biệt theo nghiệp vụ. Bổ sung DB constraints, transaction, isolation hoặc optimistic locking (`@Version`) tùy schema và điều kiện cạnh tranh; xử lý đúng cách khi version conflict hoặc deadlock phát sinh.

Nếu invariant trải qua **nhiều bảng, nhiều bản ghi hoặc nhiều dịch vụ**, cần lựa chọn ranh giới giao dịch, thứ tự khóa DB, cơ chế đồng bộ liên dịch vụ hoặc quy trình bù trừ phù hợp. Không thể giải quyết invariant phân tán chỉ bằng `AtomicInteger`, `ReentrantLock` hay một annotation trên bean.

## 12. Dấu hiệu chọn sai và cách kiểm thử

| Lỗi | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| `volatile int count; count++` | Visibility không bao hàm phép tăng nguyên tử | `AtomicInteger.incrementAndGet()` hoặc khóa |
| `if (atomic.get() > 0) atomic.decrementAndGet()` | Kiểm tra và trừ là hai hành động có thể xen kẽ | Vòng CAS hoặc `updateAndGet` thuần |
| Hai `AtomicInteger` riêng cho `available` và `reserved` | Từng trường atomic nhưng tổng có thể bị quan sát sai và chuyển trạng thái bị tách | Một monitor/lock chung hoặc `AtomicReference` chứa một state immutable |
| Ghi trong `synchronized`, đọc getter không khóa | Getter có thể thấy tổ hợp field không nhất quán | Cùng monitor cho mọi đường đọc/ghi thuộc invariant |
| `Lock.lock()` rồi chỉ `unlock()` cuối nhánh bình thường | Exception làm rò khóa, các thread sau bị treo | `try/finally` |
| Khóa trên `this`, chuỗi literal hoặc object người khác sở hữu | Code ngoài có thể tranh cùng monitor bất ngờ | `private final Object mutex` |
| Vòng CAS gọi HTTP, gửi email hoặc ghi DB | CAS có thể thử lại gây side effect lặp | Chỉ tính toán trạng thái thuần trong vòng CAS; side effect có hợp đồng riêng |
| Mỗi bean/replica có lock riêng cho dữ liệu chung | Không có loại trừ lẫn nhau trên toàn nguồn dữ liệu | Đưa invariant về cùng owner/DB |
| Thêm `@Transactional` để chữa race của field Java | Transaction DB không khóa object trong heap | Chọn primitive JVM đúng, hoặc chuyển state vào DB |
| Khóa bao một request mạng dài | Tăng contention, timeout và nguy cơ deadlock | Thu nhỏ critical section; tránh I/O khi giữ khóa |

### Checklist trước khi đưa vào production

1. **Viết invariant:** ví dụ `available >= 0` và `available + reserved = initialTotal`.
2. **Liệt kê tất cả reader/writer:** request, background jobs, scheduler, listeners, test helpers; không được có đường vòng bỏ khóa.
3. **Chọn đúng owner:** một giá trị → atomic/CAS; một nhóm field một JVM → monitor/lock; nhiều replica → nguồn dữ liệu dùng chung và giao dịch phù hợp.
4. **Kiểm tra thời điểm quan sát:** getter cần snapshot đồng bộ, hoặc đọc một immutable state được công bố nguyên tử.
5. **Xử lý lỗi:** validate số lượng dương, overflow của bộ đếm, từ chối khi thiếu tài nguyên, interrupt khi chờ lock, retry CAS có thể tranh chấp.
6. **Đo đạc:** throughput, tail latency, thời gian chờ khóa, số lần CAS retry; không tối ưu bằng trực giác.
7. **Kiểm thử đồng thời:** cho nhiều thread xuất phát qua `CountDownLatch`/`CyclicBarrier`, đợi mọi `Future`, kiểm tra invariant cuối, lặp thử nhiều lần. Test phát hiện race nhưng không thay thế chứng minh mọi đường truy cập đi theo giao thức khóa.

Với dữ liệu quan trọng, còn cần kiểm thử khi **restart, chạy nhiều instance và giao dịch DB thất bại**. Unit test trong một JVM không chứng minh tính đúng đắn phân tán.

## 13. Tự kiểm tra

1. Một singleton chỉ lưu `AtomicLong requestCount`. Có cần thêm `synchronized` cho `incrementAndGet()` không?  
   **Đáp:** Không, nếu invariant chỉ là từng phép tăng của một biến.
2. Vì sao hai `AtomicInteger available/reserved` không đủ để bảo vệ `available + reserved == total` khi có getter đọc cả hai?  
   **Đáp:** Không có một điểm chuyển trạng thái nguyên tử trên **cặp** giá trị; getter có thể xen vào giữa hai cập nhật.
3. Cần thử lấy khóa tối đa 50 ms và hủy khi interrupt. Chọn gì?  
   **Đáp:** `ReentrantLock.tryLock(50, TimeUnit.MILLISECONDS)` và `unlock()` trong `finally` sau khi lấy khóa thành công.
4. Tại sao `@Service` singleton có thể bị race?  
   **Đáp:** Nhiều request/worker cùng dùng một instance; scope không cung cấp mutex.
5. Có hai replica cùng cập nhật một bản ghi tồn kho DB. `synchronized` trên mỗi replica có đủ không?  
   **Đáp:** Không. Ranh giới bất biến nằm tại nguồn dữ liệu chung; cần thao tác DB bảo đảm đồng thời.
6. Khi nào `AtomicReference<StockState>` hợp lý hơn hai atomic độc lập?  
   **Đáp:** Khi cả hai trường thuộc một immutable snapshot và có thể chuyển toàn trạng thái bằng một CAS thuần, chấp nhận chi phí thử lại.

## 14. Điểm cốt lõi và tài liệu

**Chọn công cụ theo phạm vi bất biến cần bảo vệ:**

- **Một giá trị độc lập:** atomic; nếu cập nhật có điều kiện, dùng CAS đúng cách.
- **Một nhóm trường phải nhất quán trong một JVM:** `synchronized` là lựa chọn mặc định đơn giản; `ReentrantLock` khi cần timeout, interrupt, thử lấy khóa hoặc `Condition`.
- **Một nhóm trường có thể thay bằng một snapshot immutable:** có thể cân nhắc `AtomicReference<State>` với CAS.
- **Dữ liệu nhiều JVM/DB:** dùng cơ chế nhất quán ở nơi dữ liệu sống; khóa của singleton không trải qua nhiều tiến trình.

Tài liệu tham khảo chính thức:

- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton, prototype và các web scope.
- [Oracle Java 17 — Intrinsic Locks and Synchronization](https://docs.oracle.com/javase/tutorial/essential/concurrency/locksync.html): monitor, lock và visibility.
- [Java SE 17 — `AtomicInteger`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): `compareAndSet`, `getAndUpdate`, ngữ nghĩa atomic.
- [Java SE 17 — `Lock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Lock.html): `lock`, `tryLock`, `lockInterruptibly`, `unlock`.
- [Java SE 17 — `ReentrantLock`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): điều khiển khóa và fairness.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-state-007">Không giữ khóa khi chờ nhập xuất hoặc chờ tác vụ phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Không giữ khóa khi chờ nhập/xuất hoặc chờ tác vụ phụ thuộc

## 1. Vấn đề trong bean có trạng thái

Một bean Spring có phạm vi mặc định là `singleton`: trong cùng một `ApplicationContext` thường chỉ có một đối tượng bean được chia sẻ cho nhiều luồng xử lý yêu cầu. **Singleton không có nghĩa là an toàn luồng.** Nếu bean có trường dữ liệu thay đổi được (`mutable state`), nhiều luồng có thể cùng đọc hoặc ghi trường đó.

Để bảo vệ dữ liệu chung, ta có thể dùng `synchronized`, `ReentrantLock`, các kiểu atomic hoặc cấu trúc dữ liệu concurrent. Tuy nhiên, bảo vệ đúng một biến chưa đủ: **thời gian giữ khóa cũng quyết định khả năng đáp ứng của ứng dụng.**

Giả sử một bean vừa quản lý trạng thái, vừa gọi HTTP, đọc tệp, truy vấn cơ sở dữ liệu hoặc đợi tác vụ khác hoàn tất:

```text
Luồng A: lấy khóa ---- gọi HTTP / đợi Future ............. cập nhật ---- nhả khóa
Luồng B:               đợi khóa .......................... cập nhật nhanh
Luồng C:               đợi khóa .......................... đọc trạng thái

Thời gian giữ khóa của A ≈ thời gian chờ bên ngoài + thời gian cập nhật.
```

Khi A giữ khóa trong thời gian chờ, B và C có thể bị chặn dù phần thao tác với trạng thái chỉ tốn rất ít thời gian. Thời gian chờ mạng, I/O hay lịch chạy của một task khác thường dài và khó dự đoán hơn nhiều so với thao tác cập nhật bộ nhớ.

**Nguyên tắc thiết kế:** Chỉ giữ khóa trong đoạn thực sự cần bảo vệ trạng thái hoặc bất biến chung (`critical section`). Thực hiện thao tác có thể chặn ở bên ngoài vùng khóa; sau đó lấy lại khóa trong thời gian ngắn để kiểm tra và công bố kết quả.

Bài học tập trung vào **khóa trong tiến trình Java** khi bean có trạng thái. Khóa hàng dữ liệu của cơ sở dữ liệu, transaction và khóa phân tán là các cơ chế khác; chúng có quan hệ với hiệu năng nhưng không được thay thế lẫn nhau.

## 2. Vì sao chờ khi đang giữ khóa là nguy hiểm?

### 2.1. Nghẽn khóa và tăng độ trễ

Với `synchronized (guard)`, chỉ một luồng được sở hữu monitor của `guard` tại một thời điểm. Với `ReentrantLock`, chỉ luồng sở hữu khóa mới có thể giữ vùng cần bảo vệ. Luồng khác cần cùng khóa phải chờ.

Một truy vấn HTTP mất 800 ms trong vùng `synchronized` đồng nghĩa những thao tác chỉ cần 1 ms với **cùng khóa** có thể phải đợi phần lớn 800 ms đó. Đây là hiện tượng tranh chấp khóa (`lock contention`), có thể tạo hàng đợi kéo dài và làm thời gian đáp ứng tăng mạnh khi số request lớn.

Không có một con số timeout chung nào đảm bảo an toàn: ngay cả I/O thường nhanh vẫn có thể chậm bất thường khi bị nghẽn mạng, hết connection, tải tăng hoặc dịch vụ bên ngoài lỗi.

### 2.2. Lan truyền lỗi và thiếu công bằng

Một thao tác chậm có thể trì hoãn các tác vụ không liên quan nếu tất cả dùng một khóa toàn cục. Những luồng đứng chờ khóa cũng sử dụng tài nguyên (ví dụ worker thread của server). `synchronized` không cam kết phục vụ các luồng đang chờ theo thứ tự đến.

Khi hệ thống bận, có thể hình thành chuỗi phụ thuộc: nhiều request đợi monitor, worker bị chiếm, tác vụ khác không được chạy đúng lúc, còn request ban đầu tiếp tục đợi. Vì vậy giữ khóa khi chờ cũng có thể khuếch đại một sự cố nhỏ thành nghẽn toàn ứng dụng.

### 2.3. Bế tắc khi chờ một tác vụ cần chính khóa đó

Trường hợp nguy hiểm hơn là A **giữ khóa** rồi gọi `Future.get()` hoặc `CompletableFuture.join()` để chờ B, trong khi B phải lấy **cùng khóa** mới có thể hoàn tất.

```text
Luồng A:
  đã sở hữu monitor M
  |
  +--> chờ kết quả Future của B
                ^
                |
Luồng B: cần monitor M để hoàn tất
                |
                +---- M vẫn do A giữ

A chờ B; B chờ A nhả M => chờ vòng (deadlock).
```

Rủi ro không chỉ có với `Future`. Một callback, tác vụ nền hoặc đoạn logic trong bean khác cũng có thể cần quay về cập nhật trạng thái đang được khóa. Chỉ cần xuất hiện chu trình chờ tài nguyên là có khả năng bế tắc.

Bế tắc khác với nghẽn khóa thông thường: nghẽn có thể giải tỏa khi chủ khóa kết thúc; bế tắc do vòng phụ thuộc không thể tự tiến triển nếu không có timeout, ngắt, hủy hay tác động bên ngoài.

## 3. Những thao tác không nên thực hiện khi sở hữu khóa

| Thao tác | Ví dụ | Tác hại nếu giữ khóa |
|---|---|---|
| Gọi HTTP hoặc RPC đồng bộ | `HttpClient.send(...)`, gọi API từ xa | Độ trễ mạng và lỗi phía ngoài kéo dài critical section |
| Thao tác I/O có thể chặn | Đọc/ghi tệp, socket, database call | Chờ thiết bị, kết nối, pool hoặc dịch vụ |
| Chờ tác vụ phụ thuộc | `Future.get()`, `CompletableFuture.join()` | Tác vụ được chờ có thể cần khóa đang giữ |
| Chờ thread hay tín hiệu | `Thread.join()`, `CountDownLatch.await()` | Luồng báo tín hiệu có thể cần chính trạng thái đang khóa |
| Đợi hàng đợi hoặc tài nguyên | `BlockingQueue.take()/put()` | Thời gian chờ khó kiểm soát |
| Ngủ trong vùng khóa | `Thread.sleep(...)` | Ngủ không nhả monitor hay `ReentrantLock` đang giữ |
| Gọi mã tùy biến/callback | Listener, plugin, hook chưa rõ nội bộ | Có thể tái nhập, chặn, gọi mạng hoặc tạo phụ thuộc bất ngờ |

Đây là các dấu hiệu cần xem lại **ranh giới giữ khóa**, không có nghĩa tất cả API trên luôn chặn. Ví dụ `Future.get()` có thể trả về ngay nếu tác vụ đã xong; nhưng thiết kế an toàn không nên phụ thuộc vào trường hợp thuận lợi đó.

**Phân biệt quan trọng:** `Object.wait()` khi được gọi đúng cách bên trong `synchronized` sẽ **tạm nhả monitor của đối tượng được wait** trong lúc chờ rồi lấy lại monitor trước khi trả về. `Condition.await()` cũng nhả khóa `ReentrantLock` tương ứng trong thời gian đợi. Chúng là cơ chế phối hợp trạng thái có giao thức kiểm tra điều kiện cụ thể; đừng đánh đồng với `Thread.sleep()`, `Future.get()` hoặc HTTP I/O, vốn **không tự giải phóng khóa bạn đang giữ**. Nếu vẫn sở hữu những khóa *khác*, các khóa đó không tự được nhả.

## 4. Ví dụ sai: giữ monitor trong lúc gọi HTTP

Ví dụ bean Spring sau dùng một monitor để bảo vệ số ký tự đã nhận được từ các yêu cầu. Vì gọi HTTP nằm trong `synchronized`, một lần kết nối chậm sẽ giữ khóa lâu hơn cần thiết.

**Mã minh họa dùng Spring và Java 17**, chưa phải chương trình chạy độc lập; cần Spring trong classpath và URI đến máy chủ có thật:

```java
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.stereotype.Service;

@Service
public class SlowLockedBean {
    private final Object guard = new Object();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private long totalCharacters;

    public long fetchAndRecord(URI uri)
            throws IOException, InterruptedException {
        synchronized (guard) {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            totalCharacters += response.body().length();
            return totalCharacters;
        }
    }
}
```

`totalCharacters += ...` là thao tác cần được bảo vệ vì nhiều request cùng sửa biến. Tuy nhiên, `httpClient.send(...)` không cần monitor để tải dữ liệu: đưa nó vào vùng khóa chỉ khiến những request khác phải chờ trong thời gian mạng.

Đoạn `synchronized (guard)` trong ví dụ cũng có nghĩa mọi lời gọi cùng dùng **một khóa của cùng bean**. Nếu bean còn phương thức khác cùng dùng `guard`, phương thức ấy cũng bị ảnh hưởng. Lệnh `httpClient.send` có thể ném `IOException` hoặc `InterruptedException`; Java vẫn tự nhả monitor khi rời khỏi khối `synchronized` do ngoại lệ, nhưng các request khác đã phải đợi cho tới thời điểm đó.

## 5. Cách sửa: I/O ở ngoài, cập nhật trạng thái trong vùng khóa ngắn

Với trường hợp chỉ cộng dồn số ký tự, có thể tải HTTP trước rồi dùng monitor trong vài thao tác cập nhật:

```java
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.stereotype.Service;

@Service
public class ShortLockedBean {
    private final Object guard = new Object();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private long totalCharacters;

    public long fetchAndRecord(URI uri)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .build();

        // Có thể mất thời gian lâu, nhưng không sở hữu guard.
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
        long delta = response.body().length();

        // Chỉ khóa phần cần cập nhật cùng một biến.
        synchronized (guard) {
            totalCharacters += delta;
            return totalCharacters;
        }
    }
}
```

Vì sao phiên bản này tốt hơn?

1. Nhiều request có thể thực hiện I/O độc lập mà không phải chờ cùng một monitor.
2. Các thay đổi của `totalCharacters` vẫn tuần tự theo khóa, nên không bị mất cập nhật do phép cộng chồng lấn.
3. Nếu HTTP lỗi, biến tổng không bị thay đổi. Monitor không bị giữ trong giai đoạn chờ bên ngoài.
4. Phần trả về là giá trị tổng **ngay khi request đó cập nhật xong**; thứ tự hoàn tất mạng và thứ tự tăng tổng có thể khác thứ tự request đến.

Ví dụ này đúng vì kết quả I/O **không phụ thuộc một phiên bản trạng thái đã đọc trước đó**. Không thể áp dụng máy móc kiểu “đưa mọi thứ ra khỏi khóa” nếu việc đọc trạng thái, gọi bên ngoài và ghi kết quả cần giữ bất biến xuyên suốt. Trường hợp đó cần cơ chế kiểm tra phiên bản ở phần sau.

Một mẫu thiết kế thường gặp là:

```text
Giai đoạn A — dưới khóa:
    Đọc snapshot cần thiết, cấp ID, kiểm tra điều kiện tại thời điểm đọc
          |
          v
Nhả khóa — chờ bên ngoài:
    HTTP / database / file / Future / tính toán tốn thời gian
          |
          v
Giai đoạn B — lấy khóa lại:
    Kiểm tra trạng thái còn hợp lệ, hợp nhất hoặc từ chối kết quả,
    cập nhật bất biến chung
          |
          v
Nhả khóa — trả lời/gửi thông báo
```

Trong một số bài toán, bước A không cần khóa nếu trạng thái chỉ đọc và được công bố an toàn; trong bài toán khác, bước B có thể dùng `AtomicLong` hoặc cấu trúc concurrent thay monitor. Điều cốt lõi là chỉ giữ cơ chế bảo vệ tại đoạn cần nó.

## 6. Thí nghiệm Java 17: thao tác nhanh vẫn chạy khi tác vụ chậm đang chờ

Ví dụ chạy được dưới đây dùng `CountDownLatch` thay cho mạng thật. Latch cho phép giữ tác vụ chậm ở một điểm chờ **bên ngoài monitor** và chứng minh tác vụ cập nhật nhanh vẫn có thể lấy khóa. Khác với kiểm thử dựa vào `Thread.sleep`, thứ tự kiểm tra được điều phối bằng tín hiệu nên kết quả không phụ thuộc vào tốc độ máy.

**Tệp `LockBoundaryDemo.java`**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LockBoundaryDemo {
    static final class StateBean {
        private final Object guard = new Object();
        private final List<String> history = new ArrayList<>();

        void slowOperation(CountDownLatch started,
                           CountDownLatch allowFinish)
                throws InterruptedException {

            // Đại diện cho thời điểm bắt đầu chờ I/O bên ngoài.
            started.countDown();
            allowFinish.await();  // Không sở hữu guard ở đây.

            synchronized (guard) {
                history.add("slow");
            }
        }

        void quickUpdate() {
            synchronized (guard) {
                history.add("quick");
            }
        }

        List<String> snapshot() {
            synchronized (guard) {
                return List.copyOf(history);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        StateBean bean = new StateBean();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> slow = executor.submit(() -> {
                try {
                    bean.slowOperation(started, allowFinish);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Tác vụ chậm bị ngắt", interrupted);
                }
            });

            started.await();

            Future<?> quick = executor.submit(bean::quickUpdate);
            quick.get();
            System.out.println(bean.snapshot());

            allowFinish.countDown();
            slow.get();
            System.out.println(bean.snapshot());
        } finally {
            allowFinish.countDown();
            executor.shutdown();
        }
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac LockBoundaryDemo.java
java LockBoundaryDemo
```

Kết quả chuẩn:

```text
[quick]
[quick, slow]
```

Giải thích:

- `started.await()` bảo đảm main biết tác vụ chậm đã đến thời điểm báo hiệu chờ. `allowFinish` vẫn chưa được mở nên tác vụ chậm **chưa thể** thêm `slow` vào danh sách.
- `quick.get()` chỉ hoàn tất sau khi tác vụ nhanh đã lấy `guard`, thêm `quick` rồi nhả khóa.
- Main in snapshot đầu tiên khi tác vụ chậm chưa được mở chốt, nên chỉ có `[quick]`.
- Khi `allowFinish.countDown()`, tác vụ chậm tiếp tục, lấy monitor trong thời gian ngắn rồi thêm `slow`. `slow.get()` bảo đảm cập nhật đó xong trước khi in dòng cuối.
- `List.copyOf(history)` được tạo **bên trong vùng khóa** để nhận một bản sao ổn định. Người gọi không cầm tham chiếu đến `ArrayList` nội bộ và không phải giữ khóa khi dùng bản sao.

Điều quan trọng không phải hai thread nhất thiết chạy song song mọi lúc, mà là **tác vụ đang chờ không chiếm monitor** nên tác vụ khác có thể cập nhật trạng thái được bảo vệ.

## 7. Chờ Future trong vùng synchronized có thể tự tạo deadlock

Đây là một **phản ví dụ chỉ để phân tích, không chạy trực tiếp**, vì lời gọi `get()` có thể chờ mãi:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

final class CircularWaitBean {
    private final Object guard = new Object();
    private String state = "ready";

    String dangerous(ExecutorService executor)
            throws InterruptedException, ExecutionException {
        synchronized (guard) {
            Future<String> dependent = executor.submit(() -> {
                synchronized (guard) {
                    return state;
                }
            });
            return dependent.get(); // Chờ B trong lúc giữ guard.
        }
    }
}
```

Giả sử `executor` có worker đang sẵn sàng: A đang chạy `dangerous` và giữ `guard`; B được gửi vào pool, nhưng B muốn đọc `state` nên phải lấy `guard`. A lại gọi `dependent.get()` để chờ B. Vì A không rời khỏi `synchronized` trước khi B hoàn tất, hai luồng tạo một vòng chờ.

Cách sửa là tách **chờ kết quả** khỏi **sở hữu khóa**:

```java
// Mã minh họa phương thức trong CircularWaitBean:
String safer(ExecutorService executor)
        throws InterruptedException, ExecutionException {
    Future<String> dependent = executor.submit(() -> {
        synchronized (guard) {
            return state;
        }
    });

    // Không giữ guard khi đợi kết quả.
    return dependent.get();
}
```

Mẫu sửa này loại bỏ vòng chờ **trên guard** của ví dụ. Tuy nhiên, `get()` vẫn có thể chờ dài hoặc vô hạn nếu chính tác vụ B bị treo. Với yêu cầu thời hạn rõ ràng, dùng `get(timeout, TimeUnit.SECONDS)` và xử lý `TimeoutException`, đồng thời cân nhắc `cancel(true)` khi thật sự cần hủy. Hết hạn `get(timeout, ...)` **không tự hủy** tác vụ; `cancel(true)` cũng chỉ yêu cầu ngắt theo cơ chế hợp tác.

Một biến thể khác là **executor starvation deadlock**: tác vụ A đang chạy trong pool chỉ có một worker, gửi B vào **chính pool đó** rồi gọi `B.get()`. B chưa thể chạy vì A đang chiếm worker duy nhất; sự cố này có thể xảy ra **ngay cả khi không có monitor**. Vì vậy cần kiểm tra cả phụ thuộc khóa và phụ thuộc tài nguyên worker.

## 8. Đừng đánh mất tính đúng đắn khi tách I/O khỏi khóa

Rút ngắn critical section giúp giảm nghẽn, nhưng có thể tạo lỗi **ghi đè kết quả cũ** nếu trạng thái đã thay đổi trong khoảng không giữ khóa.

Xem quy trình:

```text
A: [khóa] đọc cấu hình phiên bản 7 [nhả]
A:       gọi dịch vụ chậm dựa vào phiên bản 7 ...
B: [khóa] cập nhật cấu hình lên phiên bản 8 [nhả]
A:                                        [khóa] ghi kết quả từ phiên bản 7?
```

Nếu A ghi thẳng kết quả cũ, dữ liệu mới của B có thể bị ghi đè hoặc một bất biến nghiệp vụ bị phá vỡ. Ta cần **snapshot + kiểm tra phiên bản + chỉ commit nếu vẫn hợp lệ**. Đây là kiểu kiểm soát đồng thời lạc quan (`optimistic concurrency control`) trong bộ nhớ.

**Tệp `VersionGuardDemo.java` — ví dụ Java 17 độc lập**

```java
public class VersionGuardDemo {
    static final class StateBean {
        private final Object guard = new Object();
        private String value = "ban đầu";
        private long version = 0;

        record Snapshot(long version, String value) {}

        Snapshot readSnapshot() {
            synchronized (guard) {
                return new Snapshot(version, value);
            }
        }

        void updateFromAnotherRequest(String newValue) {
            synchronized (guard) {
                value = newValue;
                version++;
            }
        }

        boolean publishIfUnchanged(Snapshot snapshot, String result) {
            synchronized (guard) {
                if (version != snapshot.version()) {
                    return false;
                }

                value = result;
                version++;
                return true;
            }
        }
    }

    public static void main(String[] args) {
        StateBean bean = new StateBean();

        StateBean.Snapshot before = bean.readSnapshot();
        String resultOfSlowCall = "kết quả từ dữ liệu cũ";

        // Mô phỏng một request khác cập nhật khi lời gọi ngoài đang chạy.
        bean.updateFromAnotherRequest("dữ liệu mới");

        boolean accepted = bean.publishIfUnchanged(
                before, resultOfSlowCall);

        System.out.println("Đã chấp nhận kết quả cũ: " + accepted);
        System.out.println("Trạng thái cuối: "
                + bean.readSnapshot().value());
    }
}
```

```bash
javac VersionGuardDemo.java
java VersionGuardDemo
```

Kết quả:

```text
Đã chấp nhận kết quả cũ: false
Trạng thái cuối: dữ liệu mới
```

Ví dụ mô phỏng sự thay đổi theo thứ tự cố định để quan sát kết quả; trong ứng dụng thật, hai request có thể chạy trên các luồng khác nhau. `readSnapshot()` chụp cả `version` và `value` dưới cùng monitor. Sau khi làm việc bên ngoài, `publishIfUnchanged(...)` kiểm tra phiên bản và ghi trong **một vùng khóa duy nhất**. Nếu phiên bản đã đổi, kết quả cũ bị từ chối.

Tùy nghiệp vụ, khi kiểm tra phiên bản thất bại có thể:

- Đọc snapshot mới rồi **thử lại bên ngoài khóa**, nhưng cần giới hạn số lần thử và đảm bảo thao tác có thể chạy lại.
- Hợp nhất kết quả nếu phép hợp nhất là hợp lệ, ví dụ cộng thêm một lượng độc lập thay vì thay thế toàn bộ giá trị.
- Trả về trạng thái xung đột để caller hoặc người dùng quyết định.
- Thiết kế lại dữ liệu để hai request không phải cập nhật chung một trường.

**Không thể tạo giao dịch nguyên tử xuyên hệ thống chỉ bằng `synchronized`.** Nếu gọi bên ngoài đã gửi email, chuyển tiền hoặc ghi dữ liệu ở một hệ thống khác, việc không chấp nhận kết quả vào bean **không tự hoàn tác** tác động bên ngoài. Khi có side effect, cần phân biệt giai đoạn tính toán, giai đoạn gọi hệ thống ngoài và giai đoạn ghi local; có thể cần idempotency key, transaction, outbox hoặc quy trình bù trừ theo nghiệp vụ. Đây là một bài toán khác với mục tiêu giảm thời gian giữ monitor.

## 9. Dùng ReentrantLock mà không kéo dài vùng khóa

`ReentrantLock` có hành vi sở hữu tương tự ở điểm cốt lõi: **một luồng giữ khóa chặn luồng khác cần cùng khóa**. Khác với `synchronized`, mã sử dụng `ReentrantLock` phải giải phóng thủ công bằng `unlock()`, thường đặt trong `finally`.

**Mã minh họa bên trong một lớp**, giả định có các trường `lock` và `count`:

```java
private final ReentrantLock lock = new ReentrantLock();
private long count;

long recordAfterSlowOperation(long amount)
        throws InterruptedException {
    // Mô phỏng việc chờ bên ngoài. Không giữ lock.
    Thread.sleep(20);

    lock.lock();
    try {
        count += amount;
        return count;
    } finally {
        lock.unlock();
    }
}
```

Để đưa vào một lớp đầy đủ cần import `java.util.concurrent.locks.ReentrantLock`. Đây là đoạn phương thức minh họa; không có `main` và thời gian ngủ chỉ thay thế thao tác chậm trong ví dụ.

Đừng viết kiểu sau:

```java
lock.lock();
try {
    Thread.sleep(20); // Sai vị trí: ngủ khi vẫn giữ lock.
    count++;
} finally {
    lock.unlock();
}
```

Cơ chế `try/finally` giải phóng khóa khi thành công hoặc khi có ngoại lệ, nhưng **không làm cho việc giữ khóa trong lúc chờ trở nên tốt hơn**.

Khi cần đọc nhiều trường phải nhất quán với nhau, hãy đọc các trường đó trong một đoạn khóa ngắn rồi tạo một snapshot bất biến. Khi cần ghi nhiều trường cùng lúc, hãy cập nhật tất cả chúng trong cùng một đoạn khóa để giữ bất biến. Không nên chia nhỏ critical section đến mức phá vỡ tính nguyên tử của trạng thái.

## 10. Bean Spring: chọn đúng nơi đặt trạng thái và cơ chế đồng bộ

### 10.1. Ưu tiên bean không giữ trạng thái theo request

Ví dụ bean sau chỉ dùng biến cục bộ:

```java
import org.springframework.stereotype.Service;

@Service
public class PriceService {
    public long calculateTotal(long unitPrice, int quantity) {
        return Math.multiplyExact(unitPrice, quantity);
    }
}
```

Các lần gọi có tham số và biến cục bộ riêng, không tranh chấp một biến `total` trong bean. Đoạn này là ví dụ thành phần Spring, cần dependency Spring và không có `main`; `Math.multiplyExact` giúp báo `ArithmeticException` khi kết quả `long` tràn số.

Một singleton stateless thường dễ mở rộng hơn singleton chứa dữ liệu tạm của từng request. Nếu cần trạng thái chung, hãy xác định rõ **owner**, phạm vi chia sẻ, bất biến cần bảo vệ và giới hạn tải.

### 10.2. Khi phải có trạng thái chung

| Nhu cầu | Cách tiếp cận có thể phù hợp | Lưu ý |
|---|---|---|
| Đếm độc lập | `AtomicLong` | Giảm nhu cầu dùng monitor chung; nhiều trường liên quan vẫn cần phối hợp |
| Tra cứu/ghi từng khóa | `ConcurrentHashMap` | Các thao tác liên hợp cần API nguyên tử thích hợp, chẳng hạn `compute`; không suy ra mọi chuỗi thao tác đều nguyên tử |
| Nhiều trường có bất biến chung | `synchronized` hoặc `ReentrantLock` ngắn | Duy trì một quy tắc khóa nhất quán, ghi/đọc nhất quán |
| Đọc ảnh chụp ổn định | Snapshot bất biến được tạo dưới khóa | Không trả trực tiếp collection mutable nội bộ |
| Gọi API, database hoặc tệp | I/O ngoài khóa; commit dữ liệu trong khóa | Xử lý timeout, lỗi, idempotency và xung đột phiên bản |
| Xử lý bất đồng bộ trên pool | `ExecutorService` hoặc `TaskExecutor` phù hợp | Không chặn worker chờ vòng; cấu hình năng lực và hàng đợi |

**Đối tượng khóa nên là chi tiết nội bộ** (`private final Object guard = new Object()`) để code ngoài bean không thể tùy tiện khóa cùng monitor. Việc dùng `synchronized` trên toàn phương thức instance sẽ dùng monitor của `this`; điều này dễ tạo vùng khóa lớn và tác động tới những lời gọi khác trên cùng instance. Một khóa riêng giúp biểu đạt phạm vi đồng bộ rõ hơn, nhưng vẫn phải giữ critical section ngắn.

### 10.3. @Async và tác vụ phụ thuộc

`@Async` có thể chuyển việc thực thi sang bộ thực thi do Spring cấu hình khi lời gọi đi qua cơ chế proxy thích hợp. Điều này **không** làm biến instance tự động an toàn luồng. Nếu bean singleton gửi một task nền rồi đứng dưới `synchronized` để gọi `Future.get()`, task có thể bị kẹt vì cần cập nhật chính bean đó.

Ngược lại, với `CompletableFuture`, một số continuation không có hậu tố `Async` (như `thenApply`, `thenAccept`) có thể chạy ngay trên luồng hoàn tất giai đoạn trước hoặc luồng đăng ký continuation nếu giai đoạn đã xong. Không nên cho rằng callback luôn chạy ở một worker độc lập hoặc luôn an toàn khi gọi từ trong vùng khóa. Hãy xác định ranh giới tác vụ, cập nhật kết quả bằng đoạn đồng bộ nhỏ và chỉ chờ khi đã rời vùng khóa.

`@Transactional` xử lý ranh giới transaction của các tài nguyên hỗ trợ, **không** biến các trường Java trong bean thành an toàn luồng. Hơn nữa, nếu một transaction đang giữ khóa dữ liệu ở cơ sở dữ liệu, gọi dịch vụ mạng chậm bên trong transaction cũng có thể làm kéo dài thời gian giữ các khóa cơ sở dữ liệu đó. Đây là hai loại khóa khác nhau nhưng cùng dẫn tới nghẽn khi giữ tài nguyên lâu.

## 11. Timeout, interrupt và cleanup

Chuyển I/O ra ngoài monitor giúp các thao tác khác không bị khóa oan, nhưng **không có nghĩa chính tác vụ I/O sẽ hoàn tất**. Với tác vụ có thể chặn, cần chính sách thời hạn và hủy rõ ràng:

1. **Đặt timeout ở nơi phát sinh độ trễ.** Ví dụ HTTP client/request timeout, database query timeout hoặc `Future.get(timeout, unit)`. Đặt timeout vẫn cần cách xử lý khi hết hạn.
2. **Đảm bảo vùng finally nhả ReentrantLock.** Với `synchronized`, monitor được nhả tự động khi thoát khối; `ReentrantLock` bắt buộc `unlock()`.
3. **Bảo toàn tín hiệu ngắt khi bắt mà không ném tiếp.** `InterruptedException` là tín hiệu để phối hợp dừng tác vụ; không nên nuốt ngoại lệ rồi tiếp tục chờ vô hạn.
4. **Không coi cancel là lệnh dừng cưỡng bức.** `Future.cancel(true)` có thể yêu cầu ngắt task, nhưng task cần hợp tác để dừng. Nếu một tác vụ bỏ qua ngắt hoặc một thao tác không hỗ trợ ngắt, nó có thể tiếp tục.
5. **Không đóng pool do bên khác sở hữu.** Trong Spring, pool dùng chung do container quản lý thường được đóng theo vòng đời application context; code nghiệp vụ không tự `shutdown()` sau mỗi request.

Ví dụ một mẫu chờ `Future` **bên ngoài mọi khóa của bean**, nằm trong phương thức có quyền ném `InterruptedException` và `ExecutionException`:

```java
try {
    return future.get(2, TimeUnit.SECONDS);
} catch (TimeoutException timeout) {
    future.cancel(true);
    throw new IllegalStateException("Tác vụ vượt thời hạn", timeout);
}
```

Ở đây `future` là `Future<T>`, phương thức trả về `T`, và đã import các kiểu tương ứng từ `java.util.concurrent`. Đây là mảnh mã minh họa, không phải một chương trình độc lập. Nếu luồng gọi bị ngắt, hãy để `InterruptedException` lan lên tầng có trách nhiệm xử lý; chỉ khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()` khi quyết định bắt rồi không ném tiếp.

**Cảnh báo:** Nếu mã này vẫn nằm **bên trong** `synchronized`, tối đa 2 giây chờ của `get` vẫn là tối đa 2 giây mà những luồng khác có thể phải đợi khóa. Timeout là chính sách cho thao tác chờ; **không phải** biện pháp thay thế việc thiết kế ranh giới khóa.

## 12. Phân biệt khóa, trạng thái luồng và khả năng quan sát

Khi sự cố xảy ra trong môi trường chạy, thread dump và số liệu về pool giúp chỉ ra một luồng đang chờ gì và có giữ khóa nào không.

- Luồng có trạng thái `BLOCKED` thường đang chờ lấy một monitor `synchronized`; nhìn thêm vị trí lock owner thay vì chỉ đếm trạng thái.
- Luồng có trạng thái `WAITING` hoặc `TIMED_WAITING` có thể đang gọi `Future.get()`, `join()`, `sleep()` hay `await()`. Trạng thái WAITING **không chứng minh** luồng đã nhả tất cả monitor; cần xem stack và các monitor nó đang sở hữu.
- Với `ReentrantLock`, chi tiết chờ khóa có thể xuất hiện qua các cơ chế park/AQS; thread state đơn lẻ chưa đủ để kết luận.
- Quan sát thời gian chờ, tỷ lệ timeout, độ sâu queue, số worker bận và p95/p99 request latency. Việc tăng số worker đôi khi chỉ che khuất một khóa chung bị giữ quá lâu.

Một dấu hiệu thiết kế cần kiểm tra là bean có cùng một `synchronized` method vừa gọi HTTP, vừa ghi cache, vừa xử lý callback. Câu hỏi cần đặt ra là: **“Đoạn mã nào thực sự cần khóa để duy trì bất biến?”** Từ đó đưa các bước không cần khóa ra ngoài, kiểm tra lại điều kiện thay đổi trạng thái và xác minh bằng thử nghiệm đồng thời.

## 13. Lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả có thể gặp | Cách sửa |
|---|---|---|
| Đánh dấu cả method `synchronized` dù phần lớn method là I/O | Mọi request dùng cùng bean phải nối hàng dài | Khóa riêng quanh đọc/ghi trạng thái; I/O ra ngoài |
| Gọi `Future.get()` / `join()` trong monitor | Có thể deadlock với task cần cùng monitor | Gửi việc và chờ ngoài monitor; kiểm tra phụ thuộc worker |
| Dùng `Thread.sleep()` trong critical section để “chờ an toàn” | Không nhả khóa, tăng độ trễ | Thiết kế cơ chế chờ ngoài khóa; nếu cần điều kiện phối hợp, dùng API phù hợp |
| Đưa I/O ra ngoài khóa rồi ghi kết quả cũ không kiểm tra | Lost update hoặc ghi đè trạng thái mới | Snapshot + version check / hợp nhất / retry |
| Chỉ đồng bộ hàm ghi, đọc trực tiếp các trường mutable | Có thể thấy dữ liệu không nhất quán hoặc cũ | Dùng cùng quy tắc đồng bộ khi đọc; snapshot bất biến |
| Trả `ArrayList`/`HashMap` mutable nội bộ cho caller | Caller sửa dữ liệu mà không đi qua khóa | Trả bản sao hoặc chế độ truy cập an toàn |
| Nghĩ `@Async` làm bean singleton thread-safe | Race condition vẫn tồn tại | Xác định rõ ownership và cơ chế đồng bộ |
| Nghĩ `@Transactional` bảo vệ trường của bean | Đồng bộ dữ liệu Java không được bảo đảm | Phân biệt transaction DB với monitor JVM |
| Quên `unlock()` khi có exception | Luồng khác có thể chờ không thời hạn | `lock.lock(); try { ... } finally { lock.unlock(); }` |
| Dựa vào timeout để giữ khóa khi chờ | Contention kéo dài hết timeout | Tách chờ ra ngoài critical section |
| Dùng quá nhiều khóa hoặc nhiều thứ tự lấy khóa | Nguy cơ khóa chéo vòng phụ thuộc | Quy tắc thứ tự khóa thống nhất; giảm phạm vi khóa |

## 14. Bài tập thực hành

### Bài 1 — Xác định ranh giới giữ khóa

Cho một singleton bean có các bước: đọc `version`, gọi HTTP theo `version`, parse JSON, cập nhật `cachedData` và `version`. Hãy chỉ rõ bước nào cần khóa; nếu HTTP đang chạy mà request khác đổi `version`, phải làm gì với kết quả cũ?

**Gợi ý:** Chụp snapshot dưới khóa; HTTP và parse ở ngoài; lấy lại khóa kiểm tra snapshot còn đúng trước khi commit. Nếu không đúng, quyết định retry hoặc trả xung đột. Đừng giả định kết quả cũ luôn dùng được.

### Bài 2 — Đọc và chạy ví dụ LockBoundaryDemo

Chạy chương trình rồi giải thích tại sao `quick` luôn được in trước `slow`. Liệu chương trình còn bảo đảm tiến triển nếu chuyển `allowFinish.await()` vào trong `synchronized (guard)` và vẫn gọi `quick.get()` trước khi mở chốt?

**Gợi ý:** Khi slow nắm monitor và chờ chốt, quick không thể lấy guard để hoàn tất; main lại chờ quick trước khi mở chốt. Đó là vòng chờ. Chỉ phân tích biến thể sai, không chạy không có timeout.

### Bài 3 — Phân biệt hai kiểu bế tắc

So sánh trường hợp A giữ monitor M rồi chờ B cần M, với trường hợp A chạy trong pool một worker, gửi B vào cùng pool rồi chờ B. Vì sao loại thứ hai không cần monitor vẫn bế tắc?

**Gợi ý:** Tìm tài nguyên mà B phải đợi: monitor ở trường hợp một, worker trống ở trường hợp hai.

### Bài 4 — Chứng minh không ghi đè dữ liệu mới

Chạy `VersionGuardDemo`. Nếu thay `publishIfUnchanged` bằng lệnh `value = result` không kiểm tra version, kết quả sẽ khác thế nào? Khi nào có thể hợp nhất thay vì từ chối?

**Gợi ý:** Trạng thái cuối có thể bị ghi đè bằng kết quả cũ. Phép cộng dồn độc lập có thể hợp nhất, nhưng thay một cấu hình dựa trên phiên bản cũ thường cần kiểm tra xung đột.

### Bài 5 — Rà soát bean thực tế

Với một bean đang có `synchronized` hoặc `ReentrantLock`, hãy liệt kê thao tác bên trong vùng khóa và phân loại thành: đọc/ghi shared mutable state, I/O hoặc chờ, tính toán không cần trạng thái, gọi code bên ngoài. Chỉ giữ phần cần bảo toàn bất biến; kiểm tra bằng test đồng thời và quan sát thread dump nếu xuất hiện nghẽn.

## 15. Điều cần nhớ

- **Không giữ monitor hoặc ReentrantLock trong lúc đợi I/O, Future hoặc tác vụ phụ thuộc**, trừ khi có lý do đồng bộ cụ thể và đã chứng minh không tạo chờ vòng hay contention không chấp nhận được.
- **Đọc snapshot ngắn → nhả khóa → thực hiện thao tác chậm → khóa ngắn để kiểm tra và cập nhật** là mẫu nền tảng. Khi có thay đổi đồng thời, cần version check, hợp nhất hoặc retry phù hợp.
- **`synchronized` bảo vệ dữ liệu trong một tiến trình, không điều khiển mạng, transaction DB hay hệ thống ngoài.** Không dùng nó thay cho idempotency hoặc giao dịch xuyên dịch vụ.
- **`Future.get()` và `Thread.sleep()` không tự nhả khóa đang sở hữu.** `Object.wait()` và `Condition.await()` có cơ chế nhả khóa tương ứng riêng khi được dùng đúng, không làm mọi thao tác chờ trở nên an toàn.
- **Bean singleton và `@Async` không tự tạo thread safety.** Giảm shared mutable state, dùng primitive đồng bộ phù hợp, giới hạn tài nguyên và quản lý vòng đời tác vụ rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-state-008">Phân biệt phạm vi bean với cơ chế đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt phạm vi bean với cơ chế đồng bộ trong Spring Framework

## Mục tiêu

Sau bài này, bạn sẽ phân biệt được **bean scope** (phạm vi tồn tại và chia sẻ *instance* do Spring quản lý) với **cơ chế đồng bộ** (cách bảo vệ trạng thái khi nhiều luồng truy cập cùng lúc). Bạn cũng sẽ biết tại sao `singleton` không đồng nghĩa với “an toàn luồng”, `prototype` không bảo đảm loại bỏ race condition, `request` và `session` không phải khóa, và cách lựa chọn thiết kế đúng cho bean có trạng thái.

Bối cảnh là Spring Framework chạy trong ứng dụng nhiều luồng: yêu cầu HTTP, tác vụ `@Async`, scheduler hoặc executor đều có thể gọi một bean. Không cần dùng đa luồng trong mọi chương trình để hiểu scope, nhưng khi nhiều luồng cùng chạm tới dữ liệu mutable, phải đánh giá thread safety riêng.

## 1. Hai câu hỏi khác nhau

**Bean scope trả lời:** “Spring tạo, lưu và cung cấp *instance bean nào* cho lần yêu cầu này, trong phạm vi và vòng đời nào?”

**Đồng bộ hóa trả lời:** “Nếu nhiều thread đọc/ghi *cùng trạng thái có thể thay đổi*, thao tác nào phải có tính nguyên tử, thứ tự và khả năng nhìn thấy giữa các thread?”

Hãy hình dung:

- **Scope** quyết định ai được cấp chìa khóa vào cùng một phòng (cùng object) và phòng được tạo/hủy khi nào.
- **Synchronization** quyết định cách người trong phòng dùng chung sổ ghi chép mà không mất dòng, đọc sai hoặc ghi đè nhau.

Hai chiều này độc lập. Một singleton hoàn toàn có thể thread-safe nếu không giữ mutable state hoặc bảo vệ state đúng cách. Một prototype vẫn có thể không thread-safe nếu tham chiếu của nó được chia cho hai worker hoặc nó sửa một biến `static`, collection, cache hay tài nguyên dùng chung.

```text
Spring container / scope          Các thread và truy cập trạng thái
        |                                       |
        | lấy hoặc inject instance              | gọi method trên instance
        v                                       v
  Bean instance ------------------------> field / object / storage
        |                                       |
 quyết định chia sẻ instance             cần phân tích race, visibility,
 và vòng đời                              atomicity, invariants
```

**Đừng suy ra** từ `@Scope`, `@Component`, `@Service`, `@Async` hay `@Transactional` rằng framework đã tự động đặt khóa quanh toàn bộ phương thức nghiệp vụ. Mỗi annotation có hợp đồng riêng.

## 2. Các scope phổ biến và mức độ chia sẻ

| Scope | Khi nào Spring cung cấp instance? | Điều cần chú ý về concurrency |
| --- | --- | --- |
| `singleton` (mặc định) | Một object cho một bean definition trong **một Spring IoC container**. Các lần lấy cùng bean definition trả về cùng instance. | Nhiều thread có thể gọi đồng thời và dùng chung các instance field; cần thiết kế an toàn nếu state mutable. |
| `prototype` | Tạo object mới **mỗi lần container được yêu cầu cung cấp bean**. | Không tự tạo một instance cho mỗi method call hay thread; object prototype vẫn có thể được chia sẻ sau khi cấp phát. |
| `request` | Một object cho một HTTP request trong web-aware context. | Các request khác nhau được tách trạng thái bean, nhưng cùng request vẫn có thể có truy cập đa luồng; phải chú ý ngữ cảnh request khi chuyển thread. |
| `session` | Một object theo HTTP session. | Nhiều request song song từ cùng session có thể dùng chung bean; session scope **không** tuần tự hóa thao tác. |
| `application` | Một object cho một `ServletContext`. | Có thể được nhiều request/bean dùng chung; `ServletContext` không phải ranh giới khóa. |
| `websocket` | Gắn với một WebSocket session trong môi trường hỗ trợ. | Nhiều callback/tác vụ liên quan cùng session vẫn cần thiết kế concurrency phù hợp. |

Spring còn hỗ trợ **custom scope**. `SimpleThreadScope` có sẵn nhưng **không được đăng ký mặc định**: nó gắn instance theo thread nếu bạn tự đăng ký. Trong thread pool, một worker có thể chạy nhiều task của nhiều người dùng, nên “mỗi thread một bean” **không tương đương** “mỗi request một bean”. Tránh dùng thread scope như cách chữa race condition chung.

Lưu ý ranh giới:

1. Spring `singleton` là **per container, per bean definition**, không phải một object duy nhất cho toàn JVM, mọi application context hay toàn cụm server. Hai bean definition cùng class vẫn có thể tạo hai singleton khác nhau.
2. Web scopes chỉ hoạt động khi có ngữ cảnh web thích hợp; một `AnnotationConfigApplicationContext` thông thường không tự cấp request/session scope.
3. Scope chỉ kiểm soát **bean được container quản lý**. `new SomeClass()` tự tạo bên ngoài Spring và static field không tự đổi vòng đời chỉ vì class có annotation.
4. Khi nhiều instance dùng chung database, Redis, file, shared cache hoặc tài nguyên khác, việc tách instance cũng không tách tài nguyên bên dưới.

## 3. Nhận diện state nằm trong bean hay bên ngoài bean

Một biến local thuộc từng lần gọi, nhưng **đối tượng được biến local trỏ tới có thể vẫn được chia sẻ**; ngược lại, field của singleton thường được nhiều caller thấy cùng lúc. Trước khi chọn scope hay lock, hãy theo các tham chiếu thực tế:

| Nơi đặt trạng thái | Quan hệ với scope | Câu hỏi cần trả lời |
| --- | --- | --- |
| Tham số, biến cục bộ | Ô biến riêng cho mỗi invocation | Tham chiếu có trỏ tới List/DTO mutable được chia sẻ không? |
| Instance field của singleton | Field thuộc một bean instance được chia sẻ | Field có bị sửa sau khi khởi tạo; mọi reader/writer có phối hợp không? |
| Field của prototype/request/session | Phụ thuộc cách bean được cấp và reference được giữ | Có cùng target bị nhiều thread truy cập hay bị giữ quá vòng đời không? |
| Static, cache, DB, dependency khác | Không được cô lập chỉ vì đổi scope của bean caller | Chủ sở hữu và cơ chế đồng bộ tại tài nguyên dùng chung là gì? |

Ví dụ, `final List<String> items` không khiến list bất biến; `AtomicInteger` bảo vệ phép tăng một giá trị trong một JVM, nhưng không bảo vệ database hoặc một workflow nhiều bước.

## 4. Scope khác gì với synchronization?

Ba thuộc tính concurrency quan trọng:

- **Atomicity (tính nguyên tử):** một thao tác hoặc một nhóm thao tác phải được quan sát như một đơn vị phù hợp. Ví dụ “đọc tồn kho, kiểm tra đủ, trừ tồn” là một invariant nhiều bước.
- **Visibility (khả năng nhìn thấy):** ghi của thread A phải được thread B quan sát theo quan hệ happens-before thích hợp; nếu thiếu, B có thể đọc giá trị cũ.
- **Ordering (thứ tự):** compiler, CPU và runtime có thể tối ưu/sắp xếp lại theo Java Memory Model khi không có quan hệ đồng bộ; phải thiết kế quan hệ happens-before khi logic phụ thuộc thứ tự.

| Công cụ | Giải quyết tốt việc gì? | Không tự giải quyết |
| --- | --- | --- |
| Dữ liệu bất biến, thiết kế stateless | Loại bỏ phần lớn tranh chấp trên state riêng của service. | Race trong các dependency mutable hay storage bên ngoài. |
| Biến cục bộ, object không chia sẻ giữa thread | Giảm chia sẻ trạng thái do mỗi lần gọi giữ state riêng. | Object được trao tiếp cho thread khác hoặc global state vẫn có thể bị tranh chấp. |
| `synchronized` / `ReentrantLock` | Bảo vệ đoạn tới hạn, tạo quan hệ happens-before với cùng khóa, bảo vệ invariant nhiều bước. | Không đồng bộ với code dùng khóa khác hoặc truy cập không tuân thủ cùng quy tắc; không khóa qua nhiều JVM. |
| `AtomicInteger`, `AtomicLong`, CAS | Các thao tác nguyên tử đơn lẻ như tăng bộ đếm. | Invariant phức tạp trải trên nhiều field hoặc nhiều dòng lệnh nếu không thiết kế phối hợp. |
| `volatile` | Visibility và ordering cho field phù hợp, ví dụ cờ dừng. | Biến `count++`, “kiểm tra rồi cập nhật”, hay invariant nhiều bước. |
| `ConcurrentHashMap`, concurrent collections | Các hoạt động đồng thời theo hợp đồng collection; một số thao tác atomic như `computeIfAbsent` theo đặc tả. | Toàn bộ quy trình gồm nhiều thao tác độc lập nếu cần atomicity xuyên suốt. |
| Database transaction / lock / optimistic version | Tính nhất quán tại tầng dữ liệu khi có giao dịch phù hợp. | Thread safety tự động cho instance field trong JVM. |

Với `synchronized`, điều quan trọng là **các thread cùng đồng bộ trên *đúng một lock*** và các truy cập liên quan đều tuân thủ quy tắc đó:

```java
private int count;

public synchronized int increment() {
    return ++count;
}

public synchronized int current() {
    return count;
}
```

Hai method trên dùng monitor của **cùng một instance**. Nếu có hai object khác nhau, mỗi object có khóa riêng; chúng không bảo vệ một tài nguyên dùng chung bên ngoài. Nếu cần khóa cho cùng database record ở nhiều instance ứng dụng, phải dùng chiến lược đồng bộ ở storage / transaction / concurrency control, không chỉ `synchronized` của một bean.

**Không khóa toàn bộ một service theo thói quen.** Việc serialize các lời gọi có thể làm giảm throughput, tăng độ trễ và tạo rủi ro deadlock. Chỉ khóa phần invariant cần thiết, giữ critical section ngắn và ưu tiên immutable/atomic data khi phù hợp.

## 5. Ví dụ chạy được: bean scope và một race condition

Ví dụ sau dùng **Java 17+** và Spring Framework `spring-context` **6.2.0**. Nó cho thấy bốn điều:

1. Hai lần lấy singleton theo cùng bean definition nhận cùng object.
2. Mỗi yêu cầu prototype trực tiếp hoặc qua `ObjectProvider` tạo object khác.
3. Một prototype được **inject một lần vào singleton** không tự làm mới theo mỗi lời gọi singleton.
4. Hai thread gọi cùng singleton có thể mất một lần tăng; dùng `AtomicInteger` thì hai lần tăng được giữ lại.

Để kết quả của race minh họa **ổn định**, `CyclicBarrier` chỉ dùng trong class cố ý viết sai nhằm buộc cả hai thread đọc cùng số `0` trước khi ghi. Đây là dụng cụ tái hiện lỗi, **không phải cơ chế sửa lỗi**.

Tạo cấu trúc:

```text
scope-sync-demo/
├── pom.xml
└── src/main/java/example/ScopeSyncDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>scope-sync-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.0</version>
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
                    <mainClass>example.ScopeSyncDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/example/ScopeSyncDemo.java`:

```java
package example;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

public class ScopeSyncDemo {

    // Cố ý không thread-safe để chứng minh mất cập nhật.
    public static class UnsafeCounter {
        private int value;
        private final CyclicBarrier bothHaveRead = new CyclicBarrier(2);

        public void incrementTogether() {
            int snapshot = value; // Cả hai thread phải đọc trước khi ghi.
            try {
                bothHaveRead.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted", ex);
            } catch (BrokenBarrierException | TimeoutException ex) {
                throw new IllegalStateException("Barrier failed", ex);
            }
            value = snapshot + 1;
        }

        public int current() {
            return value;
        }
    }

    // Mỗi incrementAndGet là một thao tác nguyên tử.
    public static class SafeCounter {
        private final AtomicInteger value = new AtomicInteger();

        public int increment() {
            return value.incrementAndGet();
        }

        public int current() {
            return value.get();
        }
    }

    public static class ScratchPad {
        private int notes;

        public void addNote() {
            notes++;
        }

        public int notes() {
            return notes;
        }
    }

    // Singleton giữ một prototype inject lúc khởi tạo,
    // đồng thời có provider để yêu cầu prototype mới.
    public static class Client {
        private final ScratchPad injectedOnce;
        private final ObjectProvider<ScratchPad> provider;

        public Client(ScratchPad injectedOnce,
                      ObjectProvider<ScratchPad> provider) {
            this.injectedOnce = injectedOnce;
            this.provider = provider;
        }

        public ScratchPad injected() {
            return injectedOnce;
        }

        public ScratchPad newPad() {
            return provider.getObject();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        public UnsafeCounter unsafeCounter() {
            return new UnsafeCounter(); // singleton mặc định
        }

        @Bean
        public SafeCounter safeCounter() {
            return new SafeCounter(); // cũng là singleton
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public ScratchPad scratchPad() {
            return new ScratchPad();
        }

        @Bean
        public Client client(ScratchPad pad,
                             ObjectProvider<ScratchPad> provider) {
            return new Client(pad, provider);
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            UnsafeCounter unsafe = context.getBean(UnsafeCounter.class);
            SafeCounter safe = context.getBean(SafeCounter.class);
            Client client = context.getBean(Client.class);

            System.out.println("singletonSame="
                + (unsafe == context.getBean(UnsafeCounter.class)));

            ScratchPad direct1 = context.getBean(ScratchPad.class);
            ScratchPad direct2 = context.getBean(ScratchPad.class);
            System.out.println("prototypeDirectDistinct="
                + (direct1 != direct2));

            System.out.println("prototypeInjectedSame="
                + (client.injected() == client.injected()));

            System.out.println("prototypeViaProviderDistinct="
                + (client.newPad() != client.newPad()));

            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<?> u1 = workers.submit(
                    () -> { unsafe.incrementTogether(); });
                Future<?> u2 = workers.submit(
                    () -> { unsafe.incrementTogether(); });
                u1.get(); // chờ và quan sát lỗi trước khi in kết quả
                u2.get();

                Future<?> s1 = workers.submit(
                    () -> { safe.increment(); });
                Future<?> s2 = workers.submit(
                    () -> { safe.increment(); });
                s1.get();
                s2.get();
            } finally {
                workers.shutdownNow();
            }

            System.out.println("unsafeAfterTwoCalls=" + unsafe.current());
            System.out.println("safeAfterTwoCalls=" + safe.current());
        }
    }
}
```

Chạy tại thư mục `scope-sync-demo` (đã cài JDK 17+ và Maven):

```bash
mvn -q compile exec:java
```

Các dòng do chương trình in ra, theo thứ tự:

```text
singletonSame=true
prototypeDirectDistinct=true
prototypeInjectedSame=true
prototypeViaProviderDistinct=true
unsafeAfterTwoCalls=1
safeAfterTwoCalls=2
```

Phân tích:

- `unsafe` là một singleton, và hai `Future` đều gọi **cùng instance**; cả hai lấy snapshot `0`, chờ nhau rồi ghi `1`. Lẽ ra hai lần tăng phải cho `2`. `Future.get()` giúp main đợi hai tác vụ và quan sát kết quả sau khi chúng hoàn tất; nó không sửa thao tác tăng bị mất.
- `safe` cũng là **singleton** nhưng dùng `AtomicInteger.incrementAndGet()`: cả hai lần tăng được tính, cho `2`. **Cùng scope, khác tính an toàn luồng**.
- `direct1 != direct2` xác nhận hai yêu cầu lấy prototype tạo hai object. `client.injected()` vẫn trả một object cố định, vì singleton `Client` đã được tạo với dependency đó từ trước.
- `client.newPad()` gọi `ObjectProvider.getObject()` mỗi lần nên tạo prototype mới. Việc có nhiều instance chỉ giảm nguy cơ chia sẻ state khi bạn thực sự không đưa các instance đó cho nhiều thread sử dụng chung.
- `ScratchPad.addNote()` không có đồng bộ; nó chỉ an toàn nếu mỗi instance thực sự được giữ riêng cho một thread/đơn vị công việc hoặc có biện pháp bảo vệ phù hợp.

Trong sản phẩm thật, **không** tạo `CyclicBarrier` bên trong counter như vậy; nó chỉ là thiết bị dựng tình huống lost update trong bài học. Dùng công cụ kiểm thử concurrency hoặc stress test để khảo sát hành vi thực tế. Không kiểm thử bằng giả định “chạy 1.000 lần chắc sẽ phát hiện race”: lỗi phụ thuộc lịch trình thực thi.

## 6. Tại sao prototype không phải cách sửa race mặc định?

Một lời gọi `getBean(PrototypeBean.class)` tạo instance mới, nhưng không có cam kết rằng *mỗi thread* sẽ lấy bean khác nhau. Hãy xét các trường hợp:

**Trường hợp A — prototype inject vào singleton một lần:**

```java
@Service
class CheckoutService {
    private final MutableBasket basket;

    CheckoutService(MutableBasket basket) {
        this.basket = basket; // chỉ inject một lần vào singleton
    }
}
```

Giả sử `MutableBasket` là prototype: sau khi `CheckoutService` khởi tạo, mọi request gọi cùng singleton có thể truy cập **chính basket đã inject**. Đây là ví dụ minh họa một phần, cần khai báo bean/configuration để chạy. Muốn instance mới theo mỗi lần xử lý, dùng `ObjectProvider<MutableBasket>`, method injection hoặc tạo object local thích hợp.

**Trường hợp B — prototype được chia sẻ qua tham chiếu:** một object prototype truyền vào hai task của một executor vẫn là **một object được hai thread chạm tới**. Annotation scope không ngăn Java chia sẻ reference.

**Trường hợp C — state nằm ngoài prototype:** hai prototype ghi chung `static Map`, chung singleton repository hoặc cùng database record. Tách instance prototype không tạo atomicity cho tài nguyên này.

Một nguyên tắc thiết kế thực dụng: chỉ chọn prototype khi thực sự cần vòng đời “cấp một object mới theo yêu cầu”. Nếu công việc chỉ cần state tạm thời, tạo local variable hoặc dùng value object bất biến thường đơn giản hơn. Nếu cần `ObjectProvider`, gọi `getObject()` tại đúng ranh giới công việc; không lưu kết quả đó vào field của singleton rồi kỳ vọng nó tự thay đổi ở lần kế tiếp.

## 7. `request`, `session`, `@Async`: scope không tự đi theo thread

### Request scope

`@RequestScope` gắn bean với HTTP request. Trong một request MVC thông thường, Spring Web thiết lập request context cho đường xử lý request. Hai request khác nhau nhận hai instance request-scoped khác nhau, nhưng điều đó **không chứng minh** rằng cùng request chỉ có một thread có thể dùng instance hoặc rằng mọi thao tác trên nó là atomic.

Ví dụ minh họa riêng cho ứng dụng Spring MVC có web context:

```java
@RequestScope
@Component
public class RequestCart {
    private final java.util.List<String> items = new java.util.ArrayList<>();

    public void add(String item) {
        items.add(item); // không tự thread-safe khi cùng instance bị truy cập đồng thời
    }
}
```

Đây chỉ là snippet cho một ứng dụng MVC, **không** chạy độc lập bằng `AnnotationConfigApplicationContext` của ví dụ trước. Nếu một xử lý HTTP chủ động chạy nhiều tác vụ song song dùng cùng `RequestCart`, cần thiết kế chia sẻ và đồng bộ đúng cách.

Đặc biệt, **request context không mặc nhiên hiện diện trong thread của `@Async`**. Việc gọi request-scoped proxy từ worker không có request attributes hợp lệ có thể thất bại; chuyển ngữ cảnh phải có cơ chế hỗ trợ cụ thể, giữ đúng vòng đời và tránh dùng dữ liệu request sau khi đã kết thúc. Không nên “sửa” bằng `InheritableThreadLocal` tùy tiện với thread pool vì thread được tái sử dụng.

### Session scope

`@SessionScope` gắn object với một HTTP session. Trình duyệt có thể gửi nhiều request cùng session gần như đồng thời (nhiều tab, fetch song song), nên hai request có thể sửa cùng bean session. **Một session không tương đương một thread.** Nếu state session mutable, cân nhắc thiết kế bất biến, thao tác nguyên tử, đồng bộ trên cùng object hoặc chuyển state sang store với concurrency control phù hợp.

### Scoped proxy và dependency có vòng đời ngắn

Singleton cần dùng request/session-scoped bean thường inject **scoped proxy** hoặc `ObjectProvider`. Proxy giữ một điểm tham chiếu ổn định ở singleton và tìm *target bean hiện hành* khi được gọi trong context hợp lệ. Proxy **không tự khóa** phương thức của target. `@RequestScope`/`@SessionScope` cung cấp cấu hình proxy thông dụng để hỗ trợ kiểu inject này.

Với prototype, scoped proxy cũng có ngữ nghĩa riêng: proxy cho prototype có thể lấy target mới cho từng method call; nếu cần nhiều thao tác trên **cùng một prototype** trong một quy trình, nên lấy một object từ provider rồi giữ tham chiếu trong phạm vi quy trình đó. Không trông chờ proxy tự quản lý một “transaction nghiệp vụ” nhiều lời gọi.

### `@Async` và executor

`@Async` khi được kích hoạt đúng qua Spring proxy chuyển *việc thực thi* phương thức sang executor, nhưng **không đổi scope** của service:

```text
HTTP request thread                       Worker trong executor
    | gọi @Async trên singleton                  |
    +------------- submit --------------------->| gọi method cùng singleton
    | nhận Future/CompletableFuture              | đọc/ghi field singleton
    | xử lý việc khác                            |
```

Nếu nhiều HTTP request gọi cùng service singleton có `@Async`, có thể nhiều worker cùng sửa field của cùng instance. `@Async`, `@EnableAsync` và kích thước thread pool **không thay thế** cơ chế đồng bộ. Thread pool size 1 chỉ làm giảm một dạng song song của một executor; request thread hoặc executor khác vẫn có thể truy cập cùng bean. Không thiết kế an toàn dựa vào giả định lúc nào cũng chỉ có một worker.

Tương tự, `@Scheduled` gọi method trên bean theo lịch, không biến field thành thread-safe. Cần xem cách scheduler cấu hình, các đường gọi khác, và liệu nhiều lịch hoặc nhiều instance ứng dụng có thể cùng đụng state.

## 8. Cách thiết kế bean có trạng thái an toàn

### Ưu tiên singleton stateless

Một singleton không cần khóa chỉ vì có nhiều caller: nếu phương thức tính `"Xin chào " + name` từ tham số `String name`, không giữ tên request trong field và không sửa dependency mutable, mỗi invocation có dữ liệu độc lập. Ngược lại, field `currentName` trong singleton là **một ô chung** dù được gắn `volatile` hoặc `AtomicReference`; những cơ chế đó không tách dữ liệu theo request.

Khi cần state theo request, tạo local/DTO riêng hoặc dùng request scope đúng vòng đời. Khi thật sự cần shared mutable state, xác định invariant và bảo vệ các thao tác liên quan.

### Chọn công cụ theo invariant

- Bộ đếm riêng lẻ: `AtomicLong` / `AtomicInteger`.
- Nhiều field phải thay đổi nhất quán: lock chung hoặc lưu snapshot immutable rồi thay thế nguyên tử với cấu trúc phù hợp.
- Cache đa luồng: collection concurrent và thao tác atomic đúng hợp đồng, chú ý bất biến của các **value** đặt trong map.
- Dữ liệu nhiều JVM/replica: transaction database, optimistic locking (`@Version` khi phù hợp với ORM), unique constraint, hoặc concurrency control tại storage.
- Công việc nền quan trọng: cân nhắc hàng đợi bền vững, idempotency và quản lý vòng đời thay vì chỉ tăng số prototype/singleton.

Không đồng nhất `@Transactional` với `synchronized`: transaction bảo vệ tính nhất quán theo cơ chế transaction manager/tầng lưu trữ và isolation đang dùng, không đặt mutex quanh mọi field Java; nhiều transaction vẫn có thể cùng chạy.

### Kiểm tra hệ quả khi có nhiều container và instance

Spring singleton trong ứng dụng A và singleton cùng tên trong ứng dụng B là hai object khác nhau. Khóa nội bộ một bean không phân phối qua các replica. Ngược lại, hai bean prototype trong **cùng JVM** có thể cùng truy cập static state hoặc shared cache. Luôn xác định đúng tài nguyên cần bảo vệ và ranh giới chạy thật.

### Vòng đời

Spring chủ động quản lý singleton từ khởi tạo đến đóng context theo lifecycle tương ứng. Với prototype, Spring tạo/configure và bàn giao instance nhưng **không tự gọi các destruction callbacks của prototype theo cách quản lý trọn đời như singleton**. Nếu prototype giữ file handle, socket hoặc tài nguyên đắt, phía sử dụng phải có trách nhiệm dọn dẹp hợp lý. `request`/`session` có vòng đời theo request/session khi được web context hỗ trợ.

## 9. Những kết luận sai cần tránh

| Nhận định sai | Thực tế |
| --- | --- |
| “Singleton không thread-safe, nên đổi hết sang prototype.” | Stateless singleton hoàn toàn có thể an toàn. Prototype có thể bị chia sẻ hoặc sửa chung storage. |
| “Mỗi task / mỗi thread sẽ tự có prototype riêng.” | Prototype được cấp khi *container được yêu cầu bean*, không dựa trực tiếp vào thread. |
| “Inject prototype vào singleton thì lần gọi service sau nhận prototype mới.” | Dependency được resolve lúc tạo singleton; cần provider hoặc method injection nếu muốn cấp lại. |
| “Request scope bảo đảm không có race condition.” | Nó cô lập giữa HTTP requests; cùng request hoặc object reference vẫn có thể bị truy cập đồng thời. |
| “Session scope khóa mọi request của cùng user.” | Các request cùng session có thể xử lý song song. |
| “`@Async` / `@Scheduled` sẽ tạo bean mới hoặc tự đồng bộ.” | Chúng điều phối thực thi, không định nghĩa scope hoặc mutex cho state. |
| “Đã dùng `volatile`, `count++` sẽ chính xác.” | Volatile bảo đảm visibility theo quy tắc của nó, không làm chuỗi read-modify-write thành atomic. |
| “Đã dùng `@Transactional`, field trong singleton được khóa.” | Transaction không thay thế cơ chế đồng bộ JVM. |
| “Dùng `synchronized` trong một singleton là an toàn cả cụm server.” | Monitor chỉ nằm trên object trong một JVM. |

## 10. Quy trình phân tích một bean trong dự án thật

1. **Xác định instance:** bean được tạo ở đâu, scope gì, số application context / replica, có scoped proxy hoặc `ObjectProvider` không?
2. **Liệt kê state:** field mutable, object được inject, collection, `static` và dữ liệu ở storage; biến nào thực sự dùng chung?
3. **Liệt kê đường gọi:** HTTP request, `@Async`, scheduler, listener, callback, executor; có thể có hai thread cùng đi vào đường ghi không?
4. **Nêu invariant:** ví dụ “đơn hàng chỉ trừ kho một lần”, “số tăng không được mất”, “một session giữ cấu hình hợp lệ”; đơn vị cần atomic là một phép toán hay nhiều bước?
5. **Chọn chiến lược:** bỏ state khỏi singleton, immutable, atomic class, lock cùng monitor, concurrent collection hoặc transaction/lock tại storage.
6. **Kiểm chứng:** viết bài kiểm thử cạnh tranh có điều kiện phối hợp (`CountDownLatch`, `CyclicBarrier`) và điều kiện hậu kiểm rõ; đo race, deadlock và contention khi cần. Không dùng `Thread.sleep()` để giả vờ chứng minh đúng thứ tự.

Ví dụ đọc mã: `@Service` singleton có `Map<String, Cart>` và một controller có nhiều request song song. **Câu hỏi cần trả lời không chỉ là scope**: `Map` có an toàn khi cập nhật đồng thời không, từng `Cart` có bị hai request chung user sửa cùng lúc không, và invariant tổng tiền được bảo vệ ở đâu? Đổi `@Service` sang prototype có thể khiến cache phân mảnh mà vẫn chưa bảo vệ `Cart` dùng chung trong database.

## 11. Tự kiểm tra

**Câu 1.** Một singleton không có field mutable, mọi dữ liệu request đi qua tham số và biến local. Có cần `synchronized` toàn bộ method chỉ vì nó là singleton?

**Đáp án:** Không. Phải xét những dependency/tài nguyên dùng chung, nhưng singleton tự nó không tạo race nếu không có mutable state bị chia sẻ.

**Câu 2.** Một singleton inject `@Scope("prototype") PaymentDraft` qua constructor. Mỗi request gọi singleton có chắc nhận `PaymentDraft` mới không?

**Đáp án:** Không. Prototype chỉ được tạo khi Spring resolve dependency lúc khởi tạo singleton. Muốn tạo tại mỗi quy trình, dùng `ObjectProvider.getObject()` hoặc giải pháp scope phù hợp.

**Câu 3.** Hai request cùng HTTP session cập nhật bean `@SessionScope` vào đúng một thời điểm. Scope có tự bảo vệ `balance = balance - amount` không?

**Đáp án:** Không. Cùng session có thể có xử lý song song. Thêm nữa, balance ở database phải được bảo vệ tại storage; lock JVM không đủ khi có nhiều replica.

**Câu 4.** Một bean singleton dùng `AtomicInteger.incrementAndGet()` cho counter. Có nhất thiết phải đổi sang prototype để hai thread tăng đúng không?

**Đáp án:** Không. Atomic increment đã giải quyết thao tác đơn lẻ đó trên cùng atomic object; singleton hoàn toàn phù hợp.

**Câu 5.** Worker `@Async` được gọi từ request thread và cố dùng request-scoped bean. Điều gì phải kiểm tra?

**Đáp án:** Kiểm tra có request context hợp lệ trên thread thực thi hay không, vòng đời context, cách truyền dữ liệu và khả năng nhiều thread cùng dùng state. Không giả định request context tự động đi theo worker.

### Điều cần nhớ

**Scope quản lý việc cấp phát/chia sẻ bean và vòng đời; đồng bộ hóa quản lý truy cập đồng thời vào trạng thái.** Chỉ khi xác định đúng *object nào*, *thread nào*, *state nào* và *invariant nào* đang bị chia sẻ mới chọn được thiết kế an toàn.

### Tài liệu tham khảo chính thức

- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton, prototype, web scopes, inject prototype vào singleton, scoped proxy, `ObjectProvider` và custom scope.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): executor, `@Async`, scheduler và ranh giới luồng thực thi.
- [Java SE — AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): ý nghĩa của phép tăng nguyên tử.
- [Java SE — Java Language Specification, Chapter 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model, synchronization và happens-before.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-state-009">Theo dõi trạng thái bị chia sẻ qua đối số và giá trị trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Theo dõi trạng thái bị chia sẻ qua đối số và giá trị trả về

## 1. Mục tiêu: nhìn thấy trạng thái thực sự được chia sẻ ở đâu

Trong Spring Framework, một bean `singleton` thường được nhiều request và nhiều thread gọi đồng thời. Rủi ro không chỉ đến từ các trường (field) nằm trong bean: trạng thái mutable có thể đi **vào bean qua đối số**, đi qua nhiều lớp service/repository/callback, rồi **thoát ra ngoài qua giá trị trả về**. Hai lời gọi tưởng như độc lập vẫn có thể cùng giữ tham chiếu đến một đối tượng và sửa dữ liệu của nhau.

Bài này hướng dẫn lần theo **đường đi của tham chiếu, quyền sửa và thời điểm truy cập**. Sau khi học, bạn cần trả lời được năm câu hỏi cho một lời gọi:

1. Đối tượng được tạo ở đâu, do ai sở hữu, có thể thay đổi hay không?
2. Tham số nhận **giá trị riêng** hay **tham chiếu tới cùng đối tượng mutable**?
3. Phương thức có sửa đối tượng tham số, lưu nó vào field/cache, hoặc chuyển tiếp sang thread khác không?
4. Giá trị trả về là giá trị mới, snapshot, view hay chính đối tượng nội bộ?
5. Có bao nhiêu luồng có thể đọc/ghi cùng đối tượng tại một thời điểm, và cơ chế bảo vệ là gì?

**Phạm vi:** theo dõi trạng thái bean qua luồng dữ liệu Java và Spring; hiểu vấn đề correctness và phương án thiết kế. Đây không phải bài tổng quan tất cả primitive concurrency.

## 2. Từ vựng và mô hình tham chiếu phải nắm

- **State (trạng thái):** dữ liệu mô tả một đối tượng tại một thời điểm, ví dụ `Cart.items`, `Order.status` hay `Service.lastRequest`.
- **Mutable object:** đối tượng có thể thay đổi sau khi tạo, ví dụ `ArrayList`, `HashMap`, DTO có setter, mảng, `StringBuilder`.
- **Immutable value:** đối tượng có trạng thái quan sát được không đổi sau khi tạo, ví dụ `String`, `Integer`, hoặc record được thiết kế với toàn bộ dữ liệu bên trong cũng bất biến.
- **Reference/alias:** hai biến khác nhau cùng trỏ tới một đối tượng. `a == b` đúng với hai biến tham chiếu nghĩa là cùng identity; `a.equals(b)` có thể đúng dù là hai đối tượng khác nhau.
- **Ownership:** quy ước thành phần nào được phép sửa và quản lý vòng đời đối tượng. Java không tự áp đặt ownership qua kiểu tham chiếu thông thường.
- **Escape:** tham chiếu vốn được xem là nội bộ thoát ra ngoài, chẳng hạn lưu đối số vào singleton field, đưa vào callback giữ lâu, hoặc trả về collection nội bộ.
- **Snapshot:** bản chụp giá trị tại một thời điểm. Snapshot nông (*shallow*) chỉ sao chép cấu trúc ngoài; các phần tử mutable bên trong có thể vẫn dùng chung.
- **Thread confinement:** trạng thái chỉ được một thread truy cập trong vòng đời liên quan. Một biến local chỉ có ô tham chiếu riêng trên stack; đối tượng nó trỏ tới vẫn có thể được chia sẻ.

Java **truyền đối số theo giá trị** (*pass-by-value*). Với kiểu nguyên thủy, giá trị số/boolean được sao chép; với kiểu tham chiếu, **giá trị tham chiếu được sao chép**, còn đối tượng phía sau không được sao chép tự động. Vì vậy trong phương thức, gán lại biến tham số không làm biến của người gọi trỏ sang đối tượng mới, nhưng sửa trạng thái trên đối tượng mà cả hai cùng tham chiếu thì người gọi có thể quan sát thay đổi.

```text
Caller                         Bean method                      Return
  request ── reference ───────► parameter
     │                              │
     └────────► Mutable DTO ◄────────┘
                    │
                    ├──── bean.field = parameter  (escape/retention)
                    ├──── executor.submit(() -> use(parameter))  (cross-thread)
                    └──── return parameter  (escape/alias)
                              │
callerResult ─────────────────┘ cùng identity, không tự tạo bản sao
```

Sơ đồ mô tả trường hợp có thể xảy ra, không có nghĩa mọi phương thức đều lưu hay trả về chính đối tượng đầu vào. Tại từng mũi tên phải xác minh điều gì thực sự được truyền và có thao tác copy hay không.

## 3. Theo dõi một đối số từ caller vào bean

Giả sử controller gọi `service.process(command)`. Đừng chỉ nhìn tên biến `command`; cần theo một **đối tượng cụ thể** đi qua lời gọi:

| Điểm kiểm tra | Câu hỏi quan trọng | Tín hiệu cần quan sát |
| --- | --- | --- |
| Controller / caller | `command` được tạo mới cho lần gọi này hay lấy từ field/cache/session? | Vị trí `new`, factory, cache lookup, injection |
| Lúc tạo DTO | Trường bên trong có được copy không? | Constructor, setter, `List.copyOf`, copy constructor |
| Vào service | Phương thức chỉ đọc, sửa trực tiếp hay giữ lại tham chiếu? | `setX`, `add`, `put`, `this.current = command` |
| Qua lời gọi tiếp | Có truyền chính DTO hoặc các phần tử mutable của nó sang phương thức khác? | Parameter forwarding, lambda capture, callback, event |
| Sang async | Tác vụ bắt đầu trước hay sau khi caller tiếp tục sửa DTO? | `@Async`, executor, future, queue, latch |
| Kết thúc | Caller nhận giá trị mới hay reference đang được nhiều nơi nắm giữ? | `return command`, `return this.items`, copy/snapshot |

Một đối tượng được tạo riêng cho mỗi HTTP request **thường có thể an toàn** nếu không ai chia sẻ nó đồng thời và nó không thoát vào trạng thái sống lâu hơn request. Tuy nhiên, nhận DTO ở một tham số không chứng minh DTO đó là riêng tư: caller có thể truyền cùng một object cho hai request công việc, đặt nó trong cache, hoặc đưa nó sang task async.

Các cách trạng thái có thể được chia sẻ mà không dễ thấy:

- Lưu đối số vào field của singleton bean, đối tượng `static`, registry, cache hoặc collection dùng chung.
- Trả lại nguyên object, mảng, `List` hay `Map` được quản lý nội bộ.
- Truyền một phần tử mutable nằm trong collection cho thành phần khác.
- Bắt tham chiếu trong lambda, callback, listener hoặc `Runnable` để chạy về sau.
- Dùng cùng bean phụ thuộc có field mutable mặc dù bean hiện tại không có field.
- Dùng `ThreadLocal` hoặc context chứa đối tượng mutable rồi lại đưa chính đối tượng đó cho thread khác. ThreadLocal không làm đối tượng trở thành immutable.

Một field khai báo `final` chỉ ngăn **gán lại tham chiếu field**, không ngăn `list.add(...)` trên danh sách mà field đang giữ.

## 4. Ví dụ Java: cùng object đi qua tham số và được trả lại

Tệp `AliasFlowDemo.java` chạy với Java 17+:

```java
import java.util.ArrayList;
import java.util.List;

public class AliasFlowDemo {
    static final class RequestData {
        private final List<String> tags;

        RequestData(List<String> tags) {
            this.tags = tags; // Cố tình giữ nguyên tham chiếu
        }

        List<String> tags() {
            return tags; // Cố tình lộ collection nội bộ
        }
    }

    static List<String> process(RequestData request) {
        request.tags().add("server");
        return request.tags();
    }

    public static void main(String[] args) {
        List<String> callerTags = new ArrayList<>(List.of("caller"));
        RequestData request = new RequestData(callerTags);

        List<String> returned = process(request);

        System.out.println(callerTags);
        System.out.println(callerTags == returned);

        returned.add("outside");
        System.out.println(request.tags());
    }
}
```

```bash
javac AliasFlowDemo.java
java AliasFlowDemo
```

Kết quả:

```text
[caller, server]
true
[caller, server, outside]
```

Phân tích đường tham chiếu:

1. `callerTags` là một `ArrayList` mutable do caller tạo.
2. Constructor `RequestData` gán `this.tags = tags`; đây là cùng một list, dù field được khai báo `final`.
3. `process` sửa list qua tham số `request`, nên caller thấy phần tử `server`.
4. `process` trả về chính list đó; `returned == callerTags` là `true`.
5. Caller sửa `returned` thì `request.tags()` cũng thay đổi. Không có đồng thời trong ví dụ, nhưng khi nhiều thread cùng thực hiện các bước này, sẽ có nguy cơ data race và vi phạm quy tắc nghiệp vụ.

**Điều cần ghi nhớ:** hai biến có tên khác nhau, nằm ở hai lớp khác nhau, không có nghĩa hai vùng trạng thái độc lập. Theo dõi identity của đối tượng chứ không chỉ tên biến.

## 5. Phân biệt tham số local với đối tượng bên trong tham số

Phương thức sau chỉ gán lại biến tham số cục bộ nên không làm caller đổi tham chiếu:

```java
static void replace(List<String> values) {
    values = new ArrayList<>(); // Chỉ biến local 'values' trỏ nơi khác
    values.add("new");
}
```

Đây là fragment minh họa; giả định `List` và `ArrayList` đã import. Ngược lại, `values.clear()` hoặc `values.add(...)` tác động lên chính đối tượng mà caller truyền vào.

Với tham số primitive như `int count`, tăng `count++` trong phương thức không sửa biến primitive của caller. Nhưng nếu primitive là **field trong một object** và phương thức dùng `request.setCount(...)`, object chung vẫn bị sửa.

Đừng đánh đồng hai phát biểu:

- “Method không có mutable local variable dùng chung” là nhận định về **ô biến local**.
- “Method không thao tác shared mutable state” là nhận định mạnh hơn về **toàn bộ object graph có thể truy cập**: đối tượng tham số, phần tử của chúng, các bean được inject và đối tượng được trả về.

Một service không có field mutable vẫn có thể thực hiện thao tác không thread-safe nếu caller truyền cùng một `ArrayList` cho các thread khác nhau để cùng sửa. Ngược lại, một service stateless chỉ đọc immutable input và tạo immutable output thường dễ dùng an toàn giữa nhiều request.

## 6. Theo dõi trạng thái đi ra qua return value

Giá trị trả về là một điểm thoát (escape) quan trọng. Xem cách phương thức tạo giá trị trước khi đánh giá an toàn.

| Dạng trả về | Thực chất | Rủi ro/khả năng |
| --- | --- | --- |
| `return internalList;` | Cùng list nội bộ | Caller có thể sửa dữ liệu bean; nhiều caller có thể cùng sửa |
| `return Collections.unmodifiableList(internalList);` | View không cho sửa qua biến view | Nếu nguồn `internalList` đổi, view vẫn phản ánh thay đổi |
| `return List.copyOf(internalList);` | Collection không sửa được, có nội dung snapshot của list lúc copy | Copy **nông**; phần tử mutable vẫn có thể dùng chung |
| `return new ArrayList<>(internalList);` | List ngoài mới, có thể sửa | Caller sửa cấu trúc list riêng nhưng phần tử mutable vẫn chia sẻ |
| `return new Result(total, status);` | Đối tượng kết quả mới | An toàn hơn nếu các field và object lồng nhau bất biến |
| `return this;` | Cùng instance chứa trạng thái | Có thể làm lộ toàn bộ API mutable của đối tượng |
| `return CompletableFuture<T>;` | Handle kết quả tương lai | Khi hoàn tất, `T` vẫn có thể là mutable object dùng chung |

Không có một loại copy tự động giải quyết mọi vấn đề. Cần quyết định **snapshot ở mức nào**: chỉ sao chép list ngoài, sao chép DTO từng phần tử, hay chuyển thành immutable value object hoàn toàn. `List.copyOf` yêu cầu phần tử khác `null`; nếu dữ liệu có null, chọn chiến lược khác và ghi rõ hợp đồng.

### 6.1. Ví dụ: read-only view khác snapshot

Tệp `ViewVsSnapshotDemo.java` chạy với Java 17+:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ViewVsSnapshotDemo {
    public static void main(String[] args) {
        List<String> original = new ArrayList<>(List.of("A"));
        List<String> view = Collections.unmodifiableList(original);
        List<String> snapshot = List.copyOf(original);

        original.add("B");

        System.out.println("view=" + view);
        System.out.println("snapshot=" + snapshot);

        try {
            snapshot.add("C");
        } catch (UnsupportedOperationException e) {
            System.out.println("snapshot blocks add");
        }
    }
}
```

```bash
javac ViewVsSnapshotDemo.java
java ViewVsSnapshotDemo
```

Kết quả:

```text
view=[A, B]
snapshot=[A]
snapshot blocks add
```

`view` cấm sửa thông qua chính view, nhưng vẫn quan sát thay đổi từ `original`; `snapshot` giữ cấu trúc danh sách tại thời điểm tạo. Ví dụ dùng `String` (immutable). Nếu phần tử là `MutableAddress` hoặc `ArrayList`, dù có snapshot outer list, nội dung từng phần tử vẫn có thể bị sửa từ nơi khác.

### 6.2. Thiết kế DTO immutable với defensive copy

Tệp `ImmutableBoundaryDemo.java` chạy với Java 17+:

```java
import java.util.ArrayList;
import java.util.List;

public class ImmutableBoundaryDemo {
    record Command(String id, List<String> tags) {
        Command {
            tags = List.copyOf(tags);
        }
    }

    record Result(String id, int count) {}

    static Result handle(Command command) {
        return new Result(command.id(), command.tags().size());
    }

    public static void main(String[] args) {
        List<String> callerTags = new ArrayList<>(List.of("first"));
        Command command = new Command("R-1", callerTags);

        callerTags.add("later");
        Result result = handle(command);

        System.out.println(command.tags());
        System.out.println(result.id() + ":" + result.count());
    }
}
```

```bash
javac ImmutableBoundaryDemo.java
java ImmutableBoundaryDemo
```

Kết quả:

```text
[first]
R-1:1
```

Record không tự biến các thành phần mutable thành bất biến sâu. Constructor chuẩn hóa bằng `List.copyOf` để ngăn alias với list gốc. Vì phần tử ở đây là `String`, snapshot này đủ cho ví dụ. Nếu record chứa `List<MutableItem>`, cần immutable element/copy tương ứng. Thời điểm copy cũng phải được bảo vệ nếu một thread khác đang đồng thời sửa list nguồn.

Trong API thực tế, tạo *result DTO* mô tả dữ liệu caller cần thay vì trả cả entity đang được cache/quản lý bởi bean. Tách **hợp đồng dữ liệu** khỏi **object identity nội bộ** giúp dễ theo dõi và test hơn.

## 7. Bean scope không thay thế việc kiểm tra chia sẻ tham chiếu

Trong Spring, các scope ảnh hưởng vòng đời instance; chúng không tự biến đối tượng mutable thành thread-safe.

| Phạm vi/tình huống | Điều thường đúng | Điều vẫn phải kiểm tra |
| --- | --- | --- |
| `singleton` (mặc định) | Một instance bean cho mỗi bean definition trong một Spring container | Các request/thread cùng gọi và có thể truy cập field hoặc dữ liệu được giữ lại |
| `prototype` | Spring tạo instance khi container được yêu cầu cấp bean | Nếu inject prototype vào singleton một lần, singleton thường giữ instance nhận lúc inject; không tự tạo mới theo từng lời gọi |
| `request` | Instance gắn với một HTTP request đang hoạt động | Reference có bị chuyển sang async/lưu vào singleton; request scope có còn active lúc truy cập không? |
| Method local | Mỗi invocation có biến local riêng | Object do biến local trỏ tới có bị chia sẻ không? |
| Bean dependency `final` | Tham chiếu dependency không bị gán lại | Dependency có field mutable hay cache thiếu đồng bộ không? |

Ví dụ **mẫu Spring không an toàn** (fragment, cần project Spring và định nghĩa `Cart`):

```java
@Service
class CartService {
    private Cart lastCart;

    public Cart inspect(Cart argument) {
        lastCart = argument; // Giữ reference từ caller trong singleton
        return lastCart;     // Đưa reference đang giữ ra caller
    }
}
```

Giả định đã import `org.springframework.stereotype.Service`. Trong thiết kế này, `argument` được chuyển vào field sống lâu và trả về, nên request sau có thể thay `lastCart`; hai thread cũng có thể truy cập `Cart` mutable cùng lúc. Thêm `volatile` vào `lastCart` chỉ cải thiện visibility của **tham chiếu field**, không làm thao tác trên `Cart` trở nên atomic, không tách ownership và cũng không ngăn request này đọc trạng thái request khác.

Một phiên bản an toàn hơn thường không cần field `lastCart`: đọc dữ liệu cần thiết từ command, tính toán bằng local immutable value và trả `CartResult` mới. Nếu thực sự phải lưu shared state (ví dụ cache), hãy xác định rõ dữ liệu được lưu, cách cập nhật atomic, thời gian sống, chính sách đồng bộ và kiểu snapshot trả ra.

## 8. Khi đi qua thread boundary: đối số vẫn có thể cùng identity

Với executor, `CompletableFuture` hoặc `@Async`, caller có thể tiếp tục chạy sau khi nộp tác vụ. Nếu task giữ cùng tham chiếu tới DTO mutable thì trạng thái có thể đổi **trước khi task đọc**. Điều này khác với trường hợp truyền giá trị immutable đã chụp trước khi submit.

Quy tắc quan trọng của `ExecutorService`: các hành động trong thread trước khi submit task có quan hệ *happens-before* với hành động của task; và hành động của task có quan hệ *happens-before* với việc caller truy xuất kết quả thành công qua `Future.get()`. Quy tắc đó **không** tạo một snapshot bất biến của đối số và không tự sắp thứ tự cho mọi thao tác sửa xảy ra sau khi submit.

### 8.1. Ví dụ hoàn chỉnh: task đọc thay đổi được thực hiện sau khi submit

Tệp `AsyncAliasDemo.java` chạy với Java 17+:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AsyncAliasDemo {
    static final class Payload {
        private String message;

        Payload(String message) {
            this.message = message;
        }

        void setMessage(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }

    public static void main(String[] args) throws Exception {
        Payload payload = new Payload("before");
        CountDownLatch allowRead = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> result = executor.submit(() -> {
                allowRead.await();
                return payload.message();
            });

            payload.setMessage("after");
            allowRead.countDown();
            System.out.println(result.get());
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac AsyncAliasDemo.java
java AsyncAliasDemo
```

Kết quả:

```text
after
```

Điều gì xảy ra?

- Task được submit khi `payload.message` vẫn là `"before"`, nhưng không nhận bản copy của `Payload`.
- Caller sửa cùng object thành `"after"` rồi mới gọi `countDown()`.
- Worker chờ `await()`; sau khi latch mở, nó đọc object đã sửa. Quan hệ *happens-before* do latch bảo đảm kết quả xác định trong ví dụ.
- `result.get()` đợi task hoàn thành. Nếu bỏ latch, thời điểm task đọc so với lần caller sửa sẽ phụ thuộc scheduling; không dựa vào `sleep` để đoán kết quả.

**Cách sửa hợp đồng:** tạo immutable `PayloadSnapshot` trước khi submit, rồi truyền snapshot vào task; hoặc chuyển quyền sở hữu sang task, và caller cam kết không sửa nữa. Nếu cả hai bên cần sửa, cần thiết kế cơ chế đồng bộ/queue phù hợp. Không giải quyết bằng cách tùy tiện thêm `volatile` vào tất cả field.

### 8.2. `@Async` trong Spring

Đoạn minh họa Spring với input immutable (cần dependency Spring, đã bật `@EnableAsync` và phương thức được gọi **qua proxy Spring**):

```java
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

record InspectCommand(String id, List<String> codes) {
    InspectCommand {
        codes = List.copyOf(codes);
    }
}

record InspectResult(String id, int codeCount) {}

@Service
public class InspectService {
    @Async
    public CompletableFuture<InspectResult> inspect(InspectCommand command) {
        InspectResult result =
                new InspectResult(command.id(), command.codes().size());
        return CompletableFuture.completedFuture(result);
    }
}
```

Đây là **mẫu tích hợp Spring**, chưa phải chương trình tự chạy bằng `javac` vì cần file, package và cấu hình Spring phù hợp. Một file Java công khai đặt tên `InspectService.java`. `@Async` chuyển công việc sang executor do Spring quản lý khi đi qua cơ chế interception phù hợp; nó **không tự sao chép tham số**. Self-invocation, tức gọi `this.inspect(...)` trong chính bean, thường bỏ qua proxy `@Async` ở chế độ proxy mặc định. Tùy cấu hình, một executor có thể cần khai báo rõ để kiểm soát tài nguyên.

Trong ví dụ, `InspectCommand` tạo snapshot danh sách lúc khởi tạo và phần tử là `String` immutable; dữ liệu task đọc không bị caller làm đổi thông qua list ban đầu. Return value cũng là `InspectResult` mới chỉ chứa giá trị immutable. Lưu ý thread chạy `@Async` thường không tự kế thừa transaction hoặc mọi thread-local context của thread caller; truyền context được cho phép bằng cơ chế rõ ràng, không giữ toàn bộ request object chỉ để lấy một thuộc tính sau này.

## 9. Trường hợp trạng thái sai qua field và return: lỗi xen kẽ giữa hai request

Xét singleton bean có field `currentUser`. Mỗi request gọi `begin(user)` rồi `finish()` để nhận user tương ứng. Hai lời gọi riêng lẻ có thể được bảo vệ bằng `synchronized` nhưng **chuỗi nghiệp vụ** vẫn có thể bị xen kẽ nếu không có thiết kế trạng thái riêng cho request.

Ví dụ runnable `CrossRequestLeakDemo.java` (Java 17+), dùng latch để tạo thứ tự xác định mà không dùng `sleep`:

```java
import java.util.concurrent.CountDownLatch;

public class CrossRequestLeakDemo {
    static final class SingletonLikeService {
        private String currentUser;

        void begin(String user) {
            currentUser = user;
        }

        String finish() {
            return currentUser;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        SingletonLikeService bean = new SingletonLikeService();
        CountDownLatch firstBegun = new CountDownLatch(1);
        CountDownLatch secondBegun = new CountDownLatch(1);

        Thread first = new Thread(() -> {
            bean.begin("Lan");
            firstBegun.countDown();
            try {
                secondBegun.await();
                System.out.println("Lan sees " + bean.finish());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread second = new Thread(() -> {
            try {
                firstBegun.await();
                bean.begin("Minh");
                secondBegun.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        first.start();
        second.start();
        first.join();
        second.join();
    }
}
```

```bash
javac CrossRequestLeakDemo.java
java CrossRequestLeakDemo
```

Kết quả khi các thread không bị ngắt từ ngoài:

```text
Lan sees Minh
```

`firstBegun` và `secondBegun` tạo quan hệ thứ tự và visibility rõ ràng. Vấn đề là **state được lưu sai chỗ**, trong field của một instance dùng chung, nên request của Lan nhận dữ liệu của Minh. Cách sửa đơn giản: không lưu `currentUser` ở field singleton; truyền user thành đối số cho hàm xử lý và trả kết quả của chính lần gọi, hoặc lưu theo request/transaction có định danh và vòng đời tường minh.

Nếu chỉ thêm `synchronized` lên `begin()` và `finish()` riêng lẻ, lỗi thiết kế vẫn có thể xảy ra giữa hai lời gọi. Muốn dùng lock phải bảo vệ **toàn bộ invariant** trong một vùng tới hạn thích hợp; tuy nhiên kiến trúc không giữ trạng thái request trong singleton thường tốt hơn.

## 10. Quy trình lần vết một luồng dữ liệu trong dự án thực tế

Với một endpoint hoặc một lời gọi service, hãy bắt đầu tại **giá trị đầu vào đang nghi vấn** rồi đi theo *reference graph*, không bắt đầu bằng việc đổi toàn bộ bean scope.

### Bước 1 — Xác định object gốc và ownership

Ghi lại: loại dữ liệu, nơi tạo, bean/request nào sở hữu, ai được phép mutate, thời điểm hết vòng đời. Ví dụ `List<OrderItem> items` lấy từ cache khác với list do JSON request body được deserialize riêng cho từng request.

### Bước 2 — Vẽ đường đi qua đối số

Theo dõi các method call, cả field trên DTO và phần tử collection. Nếu `serviceA(A)` gọi `serviceB(A.getItems())`, chính `List` có thể đang được chia sẻ dù serviceB không nhận toàn bộ DTO. Những lời gọi setter, `clear`, `sort`, `removeIf`, `computeIfAbsent`, hoặc thao tác trên entity đang managed đều đáng đánh dấu.

### Bước 3 — Tìm điểm giữ lại hoặc phát tán tham chiếu

Các điểm thường cần xem: gán vào `this.field`/`static`, cache, listener, event chứa payload mutable, callback/lambda, tạo future, đưa vào queue, đặt vào request/session attribute. Một method có vẻ chỉ “chuyển tiếp dữ liệu” nhưng lại khiến đối tượng sống lâu hơn caller.

### Bước 4 — Tìm đường trả về và biến đổi trung gian

Dùng bảng theo dõi ngắn:

| Mốc | Đối tượng cụ thể | Hành động | Ai có thể sửa tiếp? |
| --- | --- | --- | --- |
| Controller | `request.tags` | Tạo list từ input | Controller/các method được truyền |
| Service | `command.tags` | Trỏ vào cùng list nếu không copy | Service và caller |
| Executor | `capturedCommand` | Lambda giữ tham chiếu | Worker; caller nếu còn alias |
| Bean field | `lastTags` | Giữ lại reference | Mọi invocation thấy field |
| Return | `result.tags` | Trả list gốc hoặc bản copy | Caller và các chủ sở hữu còn lại |
| Boundary an toàn | `List.copyOf(tags)` của immutable elements | Snapshot trước khi chạy async | Caller chỉ đọc snapshot; input gốc do caller quản lý |

Điền tên thật của object và thời điểm mutation; không kết luận chỉ từ tên kiểu dữ liệu. Với đối tượng nested, tiếp tục theo tới phần tử có thể thay đổi.

### Bước 5 — Kiểm tra concurrency và happens-before

Xác định thread nào tạo, sửa, đọc; có cùng executor không; task có thể chạy sau khi phương thức caller trả về không; có sử dụng lock/atomic/queue/future đúng cách không. Cần biết **cả trật tự thực thi lẫn quyền sửa**. `Future.get()` có thể cho bạn thấy kết quả đã hoàn tất, nhưng không ngăn một thread khác tiếp tục sửa đối tượng kết quả mutable sau đó.

### Bước 6 — Quan sát bằng công cụ

- Debugger: đặt breakpoint tại constructor, chỗ sửa field, điểm submit async và return; so sánh identity tham chiếu bằng `==` khi có hai biến cụ thể.
- Logging/tracing: ghi `traceId`/`requestId` tường minh, thread name, loại sự kiện (created/copied/mutated/returned) và một mã object có mục đích chẩn đoán; tránh log toàn bộ dữ liệu nhạy cảm.
- `System.identityHashCode(object)` có thể hỗ trợ nhận diện trong log điều tra, nhưng **không bảo đảm duy nhất** và không phải object ID ổn định xuyên các lần chạy.
- Thread dump/JFR: dùng khi cần xác định thread đang chờ/giữ monitor hoặc thời gian truy cập; chúng không tự chỉ ra semantic ownership của DTO.
- Test: dùng `assertSame` để chứng minh alias, `assertNotSame` để kiểm tra object khác identity và các assertion về snapshot; dùng latch để tái tạo thời điểm đọc/ghi.

Không dùng log “cùng content” để kết luận “cùng object”: hai object độc lập vẫn có thể cùng `equals`. Cũng đừng dùng `System.identityHashCode` làm khóa dữ liệu trong production.

## 11. Chiến lược thiết kế để cắt đường chia sẻ không mong muốn

**A. Stateless bean, immutable input và output.** Lý tưởng cho nghiệp vụ thuần tính toán: caller chuyển command immutable vào; service tạo result mới; không đặt `lastRequest`, `tempResult` hay `currentUser` trong field singleton.

**B. Chuyển ownership rõ ràng.** Nếu cần hiệu suất và cho phép mutable object, quy định sau khi submit thì caller không được sửa/đọc đồng thời đối tượng cho đến khi task hoàn thành; áp dụng bằng thiết kế API và test. Cam kết bằng lời mà không có ranh giới kỹ thuật thường dễ bị phá vỡ.

**C. Defensive copy tại boundary.** Sao chép trước khi bean lưu giữ/callback chạy, và sao chép khi trả ra nội bộ mutable. Chọn shallow hoặc deep copy theo kiểu các phần tử. Không đọc một collection đang bị thread khác sửa mà không có đồng bộ chỉ vì dự định “copy ngay”.

**D. Shared mutable state có chủ đích.** Nếu phải chia sẻ, chọn cấu trúc/primitive phù hợp: `ConcurrentHashMap.compute` cho cập nhật atomic một key, lock cho invariant nhiều trường, atomic type cho một giá trị độc lập, queue cho trao đổi message. Không giả định một collection thread-safe khiến chuỗi nhiều thao tác thành atomic.

**E. Context dữ liệu riêng với context thread.** Truyền trace ID, tenant ID hoặc quyền cần dùng theo hợp đồng của phương thức/cơ chế propagation được hỗ trợ. `@Async` đổi thread; `ThreadLocal` trong worker không mặc nhiên có cùng giá trị với caller. Không đưa nguyên `HttpServletRequest` hoặc bean request-scoped qua async chỉ để truy xuất về sau.

**F. API return có ý nghĩa.** Trả ID, immutable DTO, immutable snapshot hoặc operation result; tránh trả raw entity/cache object mutable nếu caller không được phép thay đổi trạng thái nội bộ. Với event payload và cache result cũng áp dụng cùng nguyên tắc.

Một contract hữu ích có thể ghi rõ: “Method không mutate đầu vào; không giữ tham chiếu đầu vào sau khi trả về; output không chia sẻ mutable state với repository cache”. Đó là điều có thể test, rõ hơn “service thread-safe”.

## 12. Kiểm thử tập trung vào alias và trạng thái bị rò rỉ

Thực hiện kiểm thử theo ba tầng, từ đơn giản đến concurrency:

1. **Alias/identity test:** caller tạo mutable object, gọi service, rồi sửa object gốc và object trả về; xác định bên nào thay đổi. Kiểm tra `assertSame`/`assertNotSame` chỉ khi identity thật sự là hợp đồng.
2. **Mutation isolation test:** tạo command/result theo API đã định, sửa nguồn ban đầu sau đó và kiểm tra snapshot không thay đổi; với nested mutable, sửa cả phần tử.
3. **Concurrent scheduling test:** cho hai lời gọi dùng chung bean, cùng hoặc khác đối tượng; dùng `CountDownLatch` để tạo thứ tự xác định, ghi nhận read/write và verify result đúng request. Đặt timeout để test không treo.
4. **Async completion test:** xác minh input được chụp trước submit, kết quả sau `Future.get()` đúng, lỗi/timeout/hủy được xử lý, và service không giữ reference khi tác vụ hoàn tất.
5. **Load test và quan sát:** với cache/shared state thực tế, tăng concurrency rồi đo race, queue, latency, error và ảnh hưởng bộ nhớ; test stress bổ sung nhưng không thay thế lập luận thread-safety.

Ví dụ assertion JUnit 5 dạng **fragment** (giả định có các lớp/constructor của `ImmutableBoundaryDemo` và đã import JUnit 5):

```java
var source = new ArrayList<>(List.of("one"));
var command = new ImmutableBoundaryDemo.Command("id-1", source);

source.add("two");

assertEquals(List.of("one"), command.tags());
assertNotSame(source, command.tags());
assertThrows(UnsupportedOperationException.class,
        () -> command.tags().add("three"));
```

Những assertion này kiểm tra hợp đồng snapshot của collection ngoài; chúng không chứng minh tất cả object nested là immutable hoặc mọi lời gọi đồng thời là an toàn. Có thể cần thêm test cho từng kiểu mutable ở tầng sâu.

## 13. Lỗi phổ biến và cách giải thích chính xác

| Nhận định/lỗi | Vì sao sai | Cách kiểm tra/sửa |
| --- | --- | --- |
| “Các tham số là local nên luôn an toàn.” | Biến local riêng, object phía sau có thể được nhiều thread dùng chung | Trace identity, nested references và mutation |
| “Java truyền object bằng tham chiếu.” | Java truyền **bản sao của giá trị tham chiếu**; đổi biến tham số không đổi biến caller | Thử cả gán lại tham số và sửa object |
| “Field `final` bảo đảm object immutable.” | Chỉ cố định field reference; collection bên trong vẫn sửa được | Immutable elements + defensive copy |
| “`@Async` clone request DTO trước khi chạy.” | Nó chuyển execution, không tự deep-copy input | Snapshot/ownership trước khi submit |
| “Trả `unmodifiableList` là snapshot.” | Wrapper có thể phản ánh thay đổi của backing list | Dùng `List.copyOf` hoặc deep snapshot khi cần |
| “`List.copyOf` đã copy mọi object lồng nhau.” | Chỉ tạo snapshot cấu trúc ngoài | Sao chép phần tử mutable hoặc dùng value object |
| “Bean singleton có method synchronized là đủ.” | Khóa từng method có thể không bảo vệ chuỗi nghiệp vụ và ownership bị rò | Bảo vệ invariant hoặc bỏ per-request field |
| “Bean prototype luôn mới cho mỗi lời gọi.” | Inject vào singleton thông thường được resolve lúc inject | Xem cách lấy bean/lifecycle thực tế |
| “`volatile` sửa mọi race.” | Visibility khác atomicity và invariant nhiều bước | Atomic, lock, immutable data hoặc message passing |
| “`Future.get()` làm object kết quả bất biến.” | Nó đồng bộ việc hoàn thành/quan sát kết quả, không cấm sửa về sau | Tạo immutable result, không giữ mutable alias |
| “Hai object bằng `equals` là cùng instance.” | Equality theo giá trị khác identity theo `==` | Quan sát từng identity; tránh nhầm trong log |

## 14. Bài thực hành tích hợp: truy tìm lỗi qua ba tầng Spring

Giả sử một `@RestController` nhận danh sách sản phẩm; một `@Service` nhận list, lưu vào field `lastItems`, tính tổng; một service khác trả `lastItems` để debug. Đồng thời một `@Async` method cũng dùng list này.

Hãy thực hiện các bước sau:

1. Vẽ object graph bắt đầu từ request body: DTO, list, từng item, field singleton, async lambda, result. Phân biệt **same reference**, **shallow copy**, **deep copy** tại mỗi cạnh.
2. Chỉ ra hai cách rò rỉ: input bị giữ ở singleton và return đưa backing list ra ngoài. Giải thích vì sao caller có thể sửa dữ liệu của request khác.
3. Viết phiên bản stateless: DTO immutable hoặc defensive copy, xử lý bằng local variables, result DTO mới; nếu cần audit thì lưu event bất biến trong store có đồng bộ và chỉ trả snapshot.
4. Tạo test làm đổi backing list sau khi gọi constructor để kiểm chứng input snapshot; tạo test làm đổi result (nếu được thiết kế mutable) để kiểm chứng không ảnh hưởng nội bộ.
5. Thêm một task `@Async`, sửa list gốc sau khi submit và trước khi task đọc bằng latch. Quan sát lỗi cũ, rồi thay bằng immutable snapshot để bảo đảm kết quả; không dùng `Thread.sleep` để tạo timing giả.
6. Viết bảng ownership cho từng object: owner, nơi sinh ra, ai mutate, nơi được trả ra, thời điểm hết vòng đời, có đồng bộ hay không. Giải thích tại sao chỉ đổi bean scope hoặc thêm `volatile` không đủ.

### Tiêu chí hoàn thành

- [ ] Giải thích đúng Java pass-by-value và nhận ra hai tham chiếu `==` trỏ cùng object.
- [ ] Truy được object mutable từ caller → tham số → field/callback/async → return.
- [ ] Phân biệt view, shallow snapshot, deep copy và immutable result.
- [ ] Chứng minh local variable không đủ kết luận về thread-safety của trạng thái reachable.
- [ ] Phát hiện per-request state bị giữ nhầm trong singleton bean.
- [ ] Biết vì sao `@Async` và `Future` không tự ngăn caller/task cùng sửa một DTO.
- [ ] Đề xuất API có ownership rõ, copy đúng boundary, xử lý context và concurrency có chủ đích.
- [ ] Dùng test có assertion và phối hợp bằng latch để tái tạo lỗi mà không đoán timing.

**Kết luận học tập:** để đánh giá một bean có chia sẻ trạng thái an toàn hay không, phải theo dõi **đối tượng và quyền sửa qua mọi đối số, giá trị trả về và ranh giới thread**, thay vì chỉ đếm field trong bean hoặc nhìn tên biến. Khi owner, lifetime và mutation được biểu diễn rõ, race condition và rò rỉ dữ liệu giữa request trở nên dễ giải thích, kiểm thử và phòng tránh.


</details>

- [Quay lại đầu trang](#back-to-top)
