<a id="back-to-top"></a>

# TargetSource

## Menu
- [TargetSource và việc lấy đối tượng đích khi proxy xử lý lời gọi](#target-source-001)
- [SingletonTargetSource và đối tượng đích cố định](#target-source-002)
- [PrototypeTargetSource và vòng đời đối tượng theo lần lấy](#target-source-003)
- [HotSwappableTargetSource và thay đối tượng đích](#target-source-004)
- [LazyInitTargetSource và trì hoãn khởi tạo đối tượng đích](#target-source-005)
- [ThreadLocalTargetSource và các hệ quả khi dùng luồng tái sử dụng](#target-source-006)
- [Giải phóng đối tượng đích và trách nhiệm quản lý tài nguyên](#target-source-007)
- [Phân biệt TargetSource với scoped proxy của bean](#target-source-008)
- [Chọn TargetSource nâng cao chỉ khi có nhu cầu và kiểm thử rõ ràng](#target-source-009)

## <a id="target-source-001">TargetSource và việc lấy đối tượng đích khi proxy xử lý lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# TargetSource và việc lấy đối tượng đích khi Spring AOP proxy xử lý lời gọi

## 1. Vấn đề mà TargetSource giải quyết

Trong Spring AOP, đối tượng mà mã ứng dụng gọi thường là **proxy**. Proxy có trách nhiệm áp dụng chuỗi advice/interceptor đã cấu hình rồi chuyển lời gọi tới **target** (đối tượng thực thi phương thức nghiệp vụ). Câu hỏi quan trọng là: **ở thời điểm proxy nhận một lời gọi, target cụ thể được lấy từ đâu và có phải lúc nào cũng là cùng một đối tượng?**

`org.springframework.aop.TargetSource` chính là hợp đồng mà Spring AOP dùng để trả lời câu hỏi đó. Proxy giữ tham chiếu tới một `TargetSource`; khi xử lý một lời gọi cần target, proxy yêu cầu nguồn này cung cấp đối tượng đích. Nguồn có thể luôn trả về một singleton, lấy đối tượng theo scope, tạo một prototype mới, mượn từ pool hoặc đổi đối tượng đang phục vụ mà không thay tham chiếu proxy.

**Phân biệt vai trò trong chính Spring AOP:**

| Thành phần | Trách nhiệm |
| --- | --- |
| AOP proxy (JDK dynamic proxy hoặc class-based proxy) | Nhận lời gọi từ client, điều phối interceptor và gọi target |
| Advice / interceptor | Thực hiện xử lý bổ sung quanh lời gọi, chẳng hạn ghi log hoặc quản lý giao dịch |
| `TargetSource` | Xác định target hiện tại, cung cấp target và nhận lại target khi cần |
| Target object | Chứa phương thức nghiệp vụ thực sự |

`TargetSource` **không phải một advice**: nó không quyết định điều kiện khớp pointcut và không thay thế chuỗi interceptor. Một proxy có thể dùng `TargetSource` đặc biệt ngay cả khi không có advice.

## 2. Hợp đồng API của Spring

Các phương thức cốt lõi của `org.springframework.aop.TargetSource` là:

```java
public interface TargetSource extends TargetClassAware {
    Class<?> getTargetClass();
    boolean isStatic();
    Object getTarget() throws Exception;
    void releaseTarget(Object target) throws Exception;
}
```

Đoạn trên trình bày hình dạng API để đọc hiểu; chữ ký nullability/annotation cụ thể có thể khác theo phiên bản Spring.

- **`getTargetClass()`**: mô tả kiểu của target, giúp Spring cấu hình proxy và xác định metadata phương thức. Kiểu này có thể chưa biết trong một số nguồn đặc biệt; với nguồn tự viết, nên cung cấp kiểu ổn định, tương thích với các giao diện/phương thức được proxy.
- **`isStatic()`**: trả về `true` nếu nguồn luôn dùng cùng một target. Khi đó Spring có thể tối ưu việc truy cập target và **không cần gọi `releaseTarget`**. Trả về `false` khi target có thể thay đổi theo thời điểm/lời gọi hoặc có vòng đời cần hoàn trả. `false` **không có nghĩa** bắt buộc tạo object mới mỗi lần.
- **`getTarget()`**: trả về đối tượng sẽ được dùng cho lời gọi hiện tại. Có thể ném ngoại lệ khi không lấy được target. Trong trường hợp đặc biệt, có thể trả về `null` (ví dụ proxy không có target thực, nơi advice tự cung cấp hành vi).
- **`releaseTarget(target)`**: thông báo rằng proxy đã xử lý xong đối tượng được cấp từ `getTarget()`. Cách giải phóng do nguồn quyết định: trả đối tượng về pool, hủy prototype, hoặc không làm gì. Đây **không phải** lệnh `close()` chung dành cho mọi target.

Hai phương thức `getTarget()` và `releaseTarget()` tạo thành cặp **acquire/release** đối với nguồn động. Một `TargetSource` tự viết phải bảo đảm hai thao tác đó nhất quán khi có nhiều luồng hoặc ngoại lệ.

## 3. Proxy thực sự lấy target vào lúc nào?

Với luồng xử lý lời gọi thông thường qua Spring AOP, có thể hình dung:

```text
Client giữ tham chiếu proxy
    |
    v
proxy.someMethod(args)
    |
    +--> TargetSource.getTarget()   [lấy target cho lần gọi]
    |
    +--> xác định chuỗi interceptor cho method / target class
    |        |
    |        +--> advice trước lời gọi
    |        +--> invocation.proceed()
    |                  |
    |                  +--> target.someMethod(args)
    |        +--> advice sau lời gọi (nếu có)
    |
    +--> finally: releaseTarget(target)
    |              nếu target != null và !isStatic()
    v
trả kết quả / chuyển tiếp ngoại lệ về client
```

Trong triển khai proxy Spring thông dụng (`JdkDynamicAopProxy` và interceptor động của CGLIB), target được lấy **ngay trước lúc xử lý chuỗi interceptor và gọi target**, nhằm giảm thời gian giữ target nếu nó được mượn từ pool. Nhánh không có interceptor có thể gọi target trực tiếp để tránh chi phí tạo invocation, nhưng vẫn dựa vào `TargetSource`.

Hệ quả cần hiểu:

1. **Một proxy có thể sống lâu hơn rất nhiều target** mà nó lần lượt sử dụng.
2. **Lấy được target không đồng nghĩa chắc chắn phương thức đích sẽ chạy**: một around advice có thể không gọi `proceed()` nhưng trong đường xử lý thông thường proxy đã lấy target trước đó.
3. Với `isStatic() == false`, Spring hoàn trả target trong khối `finally` sau khi xử lý lời gọi, kể cả khi advice hoặc target ném ngoại lệ (miễn là đã lấy được target khác `null`).
4. Với `isStatic() == true`, Spring không gọi `releaseTarget()` cho mỗi lần gọi; nguồn phải thực sự đáp ứng cam kết cùng một target.
5. Lời gọi phương thức nội bộ bằng `this.otherMethod()` trong target **không đi qua proxy**; vì vậy cũng không tạo một lượt lấy target mới qua proxy cho lời gọi nội bộ ấy. Đây là giới hạn của Spring AOP theo cơ chế proxy.

Chi tiết tối ưu có thể khác giữa JDK proxy và class-based proxy, nhưng ý nghĩa giao kèo `TargetSource` vẫn giữ nguyên.

## 4. Các TargetSource thường gặp trong Spring

| Triển khai | Cách chọn target | Ngữ cảnh sử dụng và điểm cần chú ý |
| --- | --- | --- |
| `SingletonTargetSource` | Giữ và trả về cùng object (`isStatic() == true`) | Lựa chọn phổ biến/mặc định khi proxy bọc một target cụ thể; không có vòng mượn/trả mỗi lời gọi |
| `HotSwappableTargetSource` | Trả về target hiện tại, có thể đổi bằng `swap(newTarget)` | Cập nhật triển khai phía sau mà client vẫn giữ cùng proxy; đổi target an toàn ở thao tác swap, nhưng phải bảo đảm target mới tương thích |
| `PrototypeTargetSource` | Lấy một bean prototype mới từ `BeanFactory` cho từng lượt | Cần khai báo target bean scope prototype; `releaseTarget` hủy phiên bản prototype sau lần gọi |
| `CommonsPool2TargetSource` | Mượn target từ pool rồi trả về | Phù hợp khi muốn tái sử dụng những target tốn chi phí tạo; cần dependency pooling tương ứng và cấu hình kích thước pool |
| `ThreadLocalTargetSource` | Gắn target với luồng đang thực thi | Đòi hỏi hiểu thread reuse và lifecycle; không nên giả định mô hình này tương đương một object cho mỗi HTTP request |
| `LazyInitTargetSource` | Trì hoãn việc truy cập một bean singleton cho tới khi cần | Hữu ích khi trì hoãn khởi tạo target; sau khi giải quyết singleton, không phải cứ mỗi lời gọi là tạo object |
| `SimpleBeanTargetSource` | Tra cứu target bean theo tên từ `BeanFactory` | Hay gặp trong cơ chế scoped proxy: bean thực được resolve theo scope ở thời điểm gọi |

Không nên lựa chọn nguồn động chỉ vì muốn “bật AOP”. Phần lớn ứng dụng Spring chỉ cần target singleton cùng advice phù hợp. Nguồn động giải quyết **bài toán vòng đời, chọn đối tượng hoặc thay thế target** cụ thể.

### Static và dynamic không đồng nghĩa singleton và prototype

- `SingletonTargetSource` là trường hợp static điển hình: nhiều lời gọi cùng trỏ tới một object.
- `HotSwappableTargetSource` là dynamic nhưng nhiều lời gọi liên tiếp **có thể cùng dùng một object** cho tới lúc swap.
- `PrototypeTargetSource` là dynamic và tạo target mới cho từng lần lấy.
- `ThreadLocalTargetSource` có thể dùng lại object của cùng một thread qua nhiều lời gọi.
- Nguồn dynamic có thể phải trả đối tượng ngay cả khi đối tượng được tái sử dụng; `releaseTarget` là quy tắc riêng của nguồn, không nhất thiết hủy object.

## 5. Ví dụ chạy được: quan sát acquire, advice và release

Ví dụ sau **không cần chạy Spring Boot hay tạo ApplicationContext**: dùng API `ProxyFactory` của Spring AOP để thấy rõ proxy lấy một target khác nhau mỗi lượt gọi, gọi interceptor rồi hoàn trả target. Phần cuối minh họa thay target mà không đổi tham chiếu proxy.

Tạo Maven project đơn giản với Java 17 trở lên:

```xml
<!-- pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-targetsource-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.8</version>
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
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo file `src/main/java/TargetSourceDemo.java`:

```java
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.HotSwappableTargetSource;

public class TargetSourceDemo {
    public interface Greeter {
        String greet();
    }

    static final class NamedGreeter implements Greeter {
        final String name;

        NamedGreeter(String name) {
            this.name = name;
        }

        @Override
        public String greet() {
            return "Hello from " + name;
        }
    }

    // Nguồn động mô phỏng việc cấp một target mới cho mỗi lời gọi.
    static final class PerCallTargetSource implements TargetSource {
        private int nextId;

        @Override
        public Class<?> getTargetClass() {
            return NamedGreeter.class;
        }

        @Override
        public boolean isStatic() {
            return false;
        }

        @Override
        public Object getTarget() {
            NamedGreeter target = new NamedGreeter("target-" + (++nextId));
            System.out.println("acquire " + target.name);
            return target;
        }

        @Override
        public void releaseTarget(Object target) {
            System.out.println("release " + ((NamedGreeter) target).name);
        }
    }

    public static void main(String[] args) {
        ProxyFactory factory = new ProxyFactory();
        factory.setInterfaces(Greeter.class);
        factory.setTargetSource(new PerCallTargetSource());
        factory.addAdvice((MethodInterceptor) invocation -> {
            System.out.println("advice before");
            Object result = invocation.proceed();
            System.out.println("advice after");
            return result;
        });

        Greeter proxy = (Greeter) factory.getProxy();
        System.out.println(proxy.greet());
        System.out.println(proxy.greet());

        HotSwappableTargetSource source =
                new HotSwappableTargetSource(new NamedGreeter("blue"));
        ProxyFactory swapFactory = new ProxyFactory();
        swapFactory.setInterfaces(Greeter.class);
        swapFactory.setTargetSource(source);
        Greeter swappableProxy = (Greeter) swapFactory.getProxy();

        System.out.println(swappableProxy.greet());
        source.swap(new NamedGreeter("green"));
        System.out.println(swappableProxy.greet());
    }
}
```

Chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q -Dexec.mainClass=TargetSourceDemo compile exec:java
```

Đầu ra chuẩn dự kiến (không tính thông báo của Maven):

```text
acquire target-1
advice before
advice after
release target-1
Hello from target-1
acquire target-2
advice before
advice after
release target-2
Hello from target-2
Hello from blue
Hello from green
```

**Giải thích bằng chính thứ tự log:** Spring proxy gọi `getTarget()` trước khi advice chạy. Interceptor gọi `proceed()`, khiến `greet()` thực thi trên target vừa được cấp. Khi kết thúc, proxy gọi `releaseTarget()` cho nguồn động rồi mới trả kết quả cho `println` bên ngoài. Sau hai lượt gọi, `PerCallTargetSource` đã tạo hai object khác nhau. Với `HotSwappableTargetSource`, client tiếp tục gọi cùng `swappableProxy`, nhưng lời gọi sau `swap()` đi tới target `green`.

Ví dụ `PerCallTargetSource` chỉ ghi log lúc release vì các object minh họa không giữ tài nguyên. Nó được viết để quan sát lifecycle; trường `nextId` chưa đồng bộ nên **không dùng nguyên mẫu này trong chương trình đa luồng**. Nếu mở rộng, dùng `AtomicInteger`/cơ chế đồng bộ thích hợp và định nghĩa rõ quyền sở hữu tài nguyên.

## 6. Nguồn target động khi có BeanFactory và scope

Trong ứng dụng Spring thực tế, thường cần quản lý target qua IoC container thay vì tự `new` trong `getTarget()`.

**Một target mới cho mỗi lời gọi:** `PrototypeTargetSource` dựa vào tên bean trong `BeanFactory`. Bean được chỉ định phải có scope `prototype`; mỗi lượt lấy target dẫn tới một instance phù hợp, và Spring hủy target prototype được cấp khi release. Chỉ đặt `@Scope("prototype")` trên class rồi tiêm trực tiếp nó vào một singleton **không tự động** khiến mỗi phương thức của singleton nhận được target mới. Điều cần thiết là một proxy có nguồn tra cứu/tạo target mỗi lượt, hoặc một phương án lazy lookup khác.

**Target phụ thuộc request/session:** scoped proxy duy trì một tham chiếu ổn định cho bên gọi, trong khi target phía sau được container chọn theo scope hiện tại. Cơ chế này thường dựa trên `SimpleBeanTargetSource` để lookup bean tại thời điểm gọi. Nếu chưa có request/scope đang hoạt động, lookup có thể thất bại; proxy không tự mở request scope.

**Pooling:** nguồn target từ pool cần bảo đảm target được hoàn trả khi lời gọi xong, kể cả khi phương thức ném lỗi. Cẩn thận trạng thái còn sót trong target tái sử dụng và giới hạn đồng thời của pool. Việc “có pool” không khiến đối tượng stateful tự nhiên an toàn khi nhiều request dùng chung.

**ThreadLocal:** cùng một thread có thể được tái sử dụng cho các tác vụ/request khác nhau; một luồng có target không đồng nghĩa target thuộc riêng một request. Trong luồng reactive hoặc chuyển thread, không nên suy ra ThreadLocal tự lan truyền. Chọn request/session scope đúng nghĩa nếu đó mới là điều ứng dụng cần.

## 7. Khi target thay đổi, chuyện gì xảy ra với proxy và advice?

- **Tham chiếu client:** client tiếp tục cầm cùng một proxy khi nguồn đổi target. Đây là mục đích rõ nhất của `HotSwappableTargetSource`.
- **Kiểu phương thức:** target mới phải hỗ trợ giao diện/phương thức mà proxy công bố. Đổi sang class không tương thích có thể khiến lời gọi thất bại; việc swap không tự thay đổi danh sách interface của proxy.
- **Advice hiện có:** interceptor vẫn thuộc proxy, nên target được chọn cho lời gọi tiếp theo vẫn đi qua advice tương ứng. Nếu pointcut xét kiểu lớp/method, việc chọn interceptor có thể phụ thuộc metadata target; tránh dùng các kiểu target khác biệt bất ngờ làm thay đổi ý nghĩa pointcut.
- **Lời gọi đang chạy:** swap không biến một phương thức đang thực thi trên target cũ thành lời gọi trên target mới. Target đã được lấy cho lượt hiện tại vẫn được dùng cho lượt đó; lời gọi kế tiếp có thể thấy target mới.
- **Target class và proxy class:** đổi object phía sau proxy không biến JDK dynamic proxy thành class-based proxy, cũng không mở rộng API public đang được proxy.

Trong hệ thống có concurrent requests, `HotSwappableTargetSource` bảo đảm thao tác đổi target của chính nguồn là thread-safe; điều đó **không** tự bảo đảm tính an toàn của trạng thái bên trong target cũ/mới hoặc quá trình dọn dẹp target cũ. Nếu target cũ cần đóng tài nguyên, cần thiết kế để các lời gọi đang sử dụng nó hoàn tất an toàn.

## 8. Lỗi hay gặp và cách chẩn đoán

| Hiện tượng / giả định sai | Cách hiểu và kiểm tra trong Spring AOP |
| --- | --- |
| “Dùng AOP là mỗi lần gọi có một target mới” | Sai. Thông thường proxy bọc singleton qua `SingletonTargetSource`; xem `isStatic()` và loại nguồn target |
| “Proxy singleton thì target phải singleton” | Sai. Một proxy dùng chung vẫn có thể lấy prototype, scoped object hoặc object từ pool ở mỗi lần gọi |
| “`isStatic() == false` chắc chắn tạo object mới” | Sai. Nó chỉ nói nguồn không cam kết cùng target mãi; hot-swap vẫn có thể trả cùng object qua nhiều lượt |
| “`releaseTarget` luôn đóng/hủy target” | Sai. Hành động release tùy nguồn; với pool là trả về pool, với nguồn mô phỏng có thể không làm gì |
| “Bắt exception ở advice là không cần release nữa” | Sai. Với nguồn non-static, proxy có trách nhiệm release trong `finally` sau khi đã lấy target |
| “Inject prototype vào singleton thì mỗi lần gọi tự đổi instance” | Sai. Dependency injection trực tiếp thường resolve lúc tạo singleton; muốn per-call phải có cơ chế lookup/proxy thích hợp |
| “Gọi `this.method()` cũng qua advice và lấy lại target” | Sai. Self-invocation chạy trong target, bỏ qua proxy |
| “Có thể swap sang class bất kỳ” | Sai. Target mới phải phù hợp giao diện và hành vi mà proxy đã công bố |
| “Không thấy advice thì TargetSource không hoạt động” | Sai. Nguồn target vẫn có thể được dùng cho lời gọi qua proxy ngay cả khi không có interceptor phù hợp |

Khi debug, hãy log ở `getTarget()` và `releaseTarget()` (chỉ trong môi trường phù hợp), thêm tên class/identity của target, tên phương thức và request/thread correlation khi cần. Đối chiếu thứ tự `getTarget → advice → target → releaseTarget`; đồng thời xác nhận client đang gọi **proxy bean**, không gọi trực tiếp đối tượng target.

## 9. Chọn thiết kế phù hợp

1. **Ứng dụng nghiệp vụ thông thường, target stateless:** để Spring dùng singleton target và cấu hình advice/pointcut; không cần custom `TargetSource`.
2. **Cần đổi implementation mà client không đổi reference:** xem xét `HotSwappableTargetSource`; chủ động thiết kế tương thích và xử lý target cũ.
3. **Thực sự cần target mới cho từng lần gọi:** xem `PrototypeTargetSource`, chi phí dựng dependency và hủy instance.
4. **Cần target theo request/session:** dùng cơ chế scoped proxy và lifecycle của Spring; tránh thay bằng ThreadLocal tự chế.
5. **Cần quản lý tài nguyên/instance đắt đỏ bằng pool:** chỉ dùng pooling target source khi lợi ích rõ ràng; kiểm tra contention, release khi lỗi và trạng thái object trước khi tái sử dụng.

**Câu hỏi tự kiểm tra:** Với một `TargetSource` có `isStatic() == false` nhưng hai lần `getTarget()` liên tiếp trả cùng object, Spring có sai không? **Không.** Nguồn dynamic không cam kết cố định target, chứ không cam kết hai lần phải khác nhau. Vì vậy vòng release vẫn phải tuân theo hợp đồng của nguồn dù object có trùng tham chiếu.

## 10. Tài liệu Spring chính thức

- Spring Framework Reference — TargetSource implementations: https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html
- Spring Framework Reference — ProxyFactory: https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html
- API của `TargetSource` (6.2.8): https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/TargetSource.html

Trọng tâm cần ghi nhớ khi đọc mã Spring AOP: **proxy là đầu vào ổn định của client; `TargetSource` quyết định target nào thực thi lời gọi hiện tại và target đó được hoàn trả ra sao.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-002">SingletonTargetSource và đối tượng đích cố định</a>

<details>
<summary>Xem chi tiết</summary>

# SingletonTargetSource và đối tượng đích cố định trong Spring AOP

## 1. Mục tiêu và phạm vi

Bài này giải thích cách **Spring AOP** chọn đối tượng đích (target) khi thực thi một lời gọi qua proxy, vì sao **SingletonTargetSource** luôn trả về cùng một đối tượng, cách cấu hình và kiểm chứng hành vi đó. Trọng tâm là API Spring AOP; kiến thức AOP tổng quát được giả định đã học ở chủ đề khác.

Sau bài học, bạn có thể:
- Giải thích quan hệ giữa Spring AOP proxy, TargetSource, advice và target thực tế.
- Dùng SingletonTargetSource trực tiếp hoặc nhận diện nó trong cấu hình ProxyFactory.
- Phân biệt **một target cố định** với **một proxy cố định**, Spring bean scope và trạng thái bất biến.
- Biết khi nào dùng mặc định và khi nào phải cân nhắc TargetSource động.

## 2. TargetSource nằm ở đâu trong Spring AOP?

Trong Spring AOP, ứng dụng thường gọi phương thức trên **proxy**. Proxy áp dụng chuỗi advisor/advice thích hợp; khi chuỗi cho phép thực thi phương thức đích, Spring lấy đối tượng triển khai thực tế từ **org.springframework.aop.TargetSource**.

~~~text
Client
  │ gọi phương thức
  ▼
Spring AOP proxy
  │ advisor / method interceptor
  ▼
TargetSource.getTarget()
  │
  ▼
Đối tượng target cụ thể
  │
  ▼
Phương thức nghiệp vụ
~~~

**SingletonTargetSource** là triển khai tại **org.springframework.aop.target.SingletonTargetSource**. Nó được khởi tạo từ **một đối tượng đã có**; mọi lần yêu cầu target đều nhận chính đối tượng ấy. TargetSource làm nhiệm vụ cấp target cho proxy, còn advice quyết định logic cắt ngang lời gọi.

Trong tình huống thường gặp, **ProxyFactory.setTarget(target)** tự tạo **SingletonTargetSource(target)**. Vì vậy, ứng dụng Spring AOP bình thường không cần khởi tạo lớp này bằng tay.

## 3. Hợp đồng quan trọng của SingletonTargetSource

| API | Hành vi với SingletonTargetSource | Ý nghĩa khi qua Spring AOP proxy |
| --- | --- | --- |
| **new SingletonTargetSource(target)** | Giữ tham chiếu đến một đối tượng target được truyền vào | Phải có target thực tế trước khi tạo nguồn |
| **getTarget()** | Trả lại cùng tham chiếu target ở mọi lần gọi | Các lần thực thi được chuyển tới cùng đối tượng |
| **getTargetClass()** | Trả về kiểu runtime của target | Spring có thông tin về lớp đích để cấu hình proxy/advisor |
| **isStatic()** | Trả về **true** | Danh tính target không thay đổi; framework có thể cache target |
| **releaseTarget(target)** | Không cần giải phóng target sau mỗi lời gọi | Không có vòng đời mượn/trả đối tượng theo lần gọi |

**Static ở đây có nghĩa là danh tính đối tượng cố định, không có nghĩa là dữ liệu của đối tượng bất biến.** Target vẫn có thể thay đổi các field của chính nó. **isStatic()** thuộc giao thức TargetSource, hoàn toàn khác từ khóa **static** trong Java.

Hãy phân biệt:
- **Proxy**: đối tượng client gọi; nó mang hành vi AOP.
- **Target**: đối tượng nghiệp vụ thật mà lời gọi cuối cùng được chuyển đến.
- **SingletonTargetSource**: đối tượng giữ tham chiếu đến target, cung cấp target cho proxy.
- **Advisor/Advice**: logic áp dụng xung quanh hoặc tại thời điểm thực thi lời gọi.

Một proxy có thể được tạo nhiều lần từ cùng cấu hình trong khi các proxy đó vẫn tham chiếu đến **cùng target**, nếu cùng dùng một SingletonTargetSource.

## 4. Ví dụ thực hành: hai lời gọi, một target

Ví dụ dùng **Java 17+** với Spring AOP 6.2.x. Một target có bộ đếm để chứng minh trạng thái được giữ qua các lần gọi. Advice in dấu trước mỗi lần gọi; không tự tạo target mới.

Tạo cấu trúc:

~~~text
spring-aop-singleton-target/
├── pom.xml
└── src/main/java/Main.java
~~~

**pom.xml**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-singleton-target</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/Main.java**

~~~java
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;

public class Main {
    public interface Counter {
        int next();
    }

    public static class CounterService implements Counter {
        private int value;

        @Override
        public int next() {
            return ++value;
        }
    }

    public static void main(String[] args) {
        CounterService target = new CounterService();
        SingletonTargetSource source = new SingletonTargetSource(target);

        ProxyFactory factory = new ProxyFactory(Counter.class, source);
        factory.addAdvice((MethodInterceptor) invocation -> {
            System.out.println("Advice before: " + invocation.getMethod().getName());
            return invocation.proceed();
        });

        Counter proxy = (Counter) factory.getProxy();

        System.out.println("Static target: " + source.isStatic());
        System.out.println("Same instance: " + (source.getTarget() == target));
        System.out.println("Target class: " + source.getTargetClass().getSimpleName());
        System.out.println("Result 1: " + proxy.next());
        System.out.println("Result 2: " + proxy.next());
    }
}
~~~

Chạy tại thư mục chứa **pom.xml**:

~~~bash
mvn -q compile
mvn -q org.codehaus.mojo:exec-maven-plugin:3.5.1:java -Dexec.mainClass=Main
~~~

**Đầu ra stdout của chương trình** (không tính thông điệp riêng của Maven):

~~~text
Static target: true
Same instance: true
Target class: CounterService
Advice before: next
Result 1: 1
Advice before: next
Result 2: 2
~~~

Giải thích từng quan sát:
1. **isStatic() == true**: Spring có thể giữ lại tham chiếu target vì target không luân chuyển qua các lời gọi.
2. Biểu thức dùng **==** chứng minh **getTarget()** trả lại đúng đối tượng đã truyền vào, không phải bản sao.
3. Advice chạy ở cả hai lời gọi vì client đang gọi **proxy.next()**.
4. Kết quả **1** rồi **2** chứng minh field **value** của **cùng CounterService** được duy trì giữa các lời gọi.

Bộ đếm này dùng để minh họa trạng thái, **không phải** một mẫu thiết kế an toàn cho ứng dụng đa luồng: phép **++value** trên một field chia sẻ không tự đảm bảo thread safety.

## 5. Cấu hình ngắn gọn và kiểm tra từ proxy

Nếu đã có đối tượng đích, cách viết thường gặp:

~~~java
CounterService target = new CounterService();
ProxyFactory factory = new ProxyFactory(target);
Counter proxy = (Counter) factory.getProxy();
~~~

Với API **ProxyFactory** của Spring, constructor nhận target hoặc **setTarget(target)** dùng cơ chế bọc target trong SingletonTargetSource. Đoạn trên chỉ minh họa cách cấu hình proxy, chưa khai báo advice; ví dụ đầy đủ ở mục 4 mới cho thấy lời gọi bị advice chặn.

Có thể kiểm tra nguồn target thông qua giao diện quản trị proxy của Spring:

~~~java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.TargetSource;

// Giả sử proxy được tạo bằng ProxyFactory ở ví dụ chính:
TargetSource actualSource = ((Advised) proxy).getTargetSource();
System.out.println(actualSource instanceof SingletonTargetSource);
~~~

Đối với proxy được tạo từ **ProxyFactory** mặc định trong ví dụ, dòng in sẽ là **true**. Đây là mã minh họa nối tiếp ví dụ chính, không phải một chương trình Java độc lập.

Trong hệ thống có **@Aspect**, **@Transactional**, **@Cacheable** hoặc các advisor do Spring quản lý, thường nên để cơ chế tạo proxy của Spring cấu hình target. Chỉ thao tác trực tiếp TargetSource khi cần kiểm soát cách proxy lấy target hoặc khi đang chẩn đoán cấu hình proxy.

## 6. Phân biệt các khái niệm dễ nhầm

| Khái niệm | Câu hỏi trả lời | Điều cần nhớ |
| --- | --- | --- |
| **SingletonTargetSource** | Proxy sẽ lấy đối tượng target nào cho mỗi lần gọi? | Luôn cùng một target instance |
| **Spring bean scope singleton** | BeanFactory quản lý bao nhiêu instance cho một bean definition trong container? | Thường là một instance trên mỗi container/bean definition |
| **Spring AOP proxy instance** | Client đang nắm object proxy nào? | Proxy và target là hai vai trò khác nhau |
| **Target bất biến** | Trạng thái bên trong object có thay đổi được không? | Không suy ra từ SingletonTargetSource |
| **PrototypeTargetSource** | Target có thể đổi theo mỗi method invocation không? | Có, khi được cấu hình đúng với prototype bean |
| **HotSwappableTargetSource** | Có thể thay target đang được proxy sử dụng không? | Có, theo cơ chế hoán đổi target |

Ví dụ: một Spring bean có scope **prototype** nhưng được tạo một lần rồi truyền vào **new SingletonTargetSource(instance)** vẫn trở thành **target cố định của proxy đó**. SingletonTargetSource không tự gọi BeanFactory mỗi lần thực thi.

Ngược lại, **singleton scope** là chính sách của Spring IoC container cho bean; không thể dùng việc một target source trả cùng đối tượng để suy luận rằng mọi proxy hoặc bean trong toàn ứng dụng đều là một instance.

## 7. Lợi ích và giới hạn của target cố định

**Phù hợp khi:**
- Lớp nghiệp vụ được thiết kế để sử dụng chung một instance, nhất là service không lưu trạng thái theo request.
- Muốn AOP advice chạy quanh các thao tác trên một đối tượng có vòng đời ổn định.
- Không cần cấp phát, trả về pool hoặc hoán đổi target theo từng lời gọi.

**Cần thận trọng khi:**
- Target có mutable state dùng chung giữa nhiều request hoặc thread: phải thiết kế thread safety, đồng bộ hóa hoặc tránh trạng thái chia sẻ không cần thiết.
- Yêu cầu mỗi lời gọi có một target riêng: SingletonTargetSource không đáp ứng; cần cân nhắc TargetSource động phù hợp.
- Muốn thay instance đích khi ứng dụng đang chạy: target cố định không có API hoán đổi; chọn giải pháp hotswap theo yêu cầu thực tế.
- Mong thao tác nội bộ trong target cũng đi qua Spring AOP: lời gọi **this.method()** bên trong target thường không đi qua proxy, nên không kích hoạt advice gắn với phương thức đó.

**Lưu ý hiệu năng:** **isStatic()** cho phép Spring AOP tối ưu đường lấy target vì đối tượng không thay đổi. Điều này không có nghĩa mọi lời gọi sẽ nhanh hơn với cùng một tỷ lệ hay advice bị bỏ qua; advice vẫn chạy theo cấu hình.

## 8. Lỗi thường gặp và cách tự kiểm tra

1. **Nhầm isStatic() với bất biến:** object vẫn có thể thay đổi trạng thái. Đừng dùng một instance có mutable state không an toàn giữa nhiều request.
2. **Nhầm proxy là target:** đánh giá advice/proxy dựa trên object thực tế client giữ; dùng **((Advised) proxy).getTargetSource()** để xem nguồn target khi giao diện đó được mở.
3. **Muốn thay target bằng cách đổi biến Java ban đầu:** SingletonTargetSource đã giữ tham chiếu đến đối tượng lúc xây dựng; gán biến **target = new ...** về sau không đổi tham chiếu bên trong nguồn.
4. **Mong một target mới ở mỗi lời gọi chỉ vì bean gốc là prototype:** prototype scope không biến một đối tượng đã bọc thành nguồn động.
5. **Thử gọi thẳng target rồi kết luận advice không chạy:** hãy gọi qua Spring proxy; gọi trực tiếp target sẽ bỏ qua chuỗi advice của proxy.
6. **Cố xử lý việc thay đổi target bằng cách thêm advice:** advice xử lý lời gọi, còn TargetSource quyết định đối tượng thực thi; phải sửa đúng thành phần.

Checklist kiểm chứng một proxy Spring AOP đang dùng target cố định:
- Lấy **TargetSource** từ proxy qua giao diện **Advised** nếu khả dụng.
- Kiểm tra **isStatic()** và loại triển khai thực tế; **true** thể hiện target cố định, không khẳng định target không mutable.
- So sánh hai kết quả **getTarget()** bằng **==** nếu đang debug và được phép truy cập target.
- Gọi qua proxy để xác nhận advice chạy; chỉ chạy thử nghiệm an toàn với đối tượng không làm thay đổi dữ liệu thật.

## 9. Tài liệu Spring tham khảo

- **SingletonTargetSource (Spring AOP Javadoc):** https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/target/SingletonTargetSource.html
- **TargetSource (Spring AOP API):** https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/aop/TargetSource.html
- **Using TargetSource Implementations:** https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html
- **Creating AOP Proxies Programmatically:** https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html

**Ghi nhớ:** Trong Spring AOP, SingletonTargetSource giữ **đúng một target object** và cung cấp lại đối tượng ấy mỗi khi proxy cần thực thi phương thức đích. Sự ổn định về danh tính target độc lập với việc target có mutable state, bao nhiêu proxy được tạo và phạm vi quản lý bean của Spring container.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-003">PrototypeTargetSource và vòng đời đối tượng theo lần lấy</a>

<details>
<summary>Xem chi tiết</summary>

# PrototypeTargetSource trong Spring AOP: vòng đời target theo từng lần gọi

## 1. Khái niệm và mục đích

`org.springframework.aop.target.PrototypeTargetSource` là một triển khai của `org.springframework.aop.TargetSource` trong **Spring AOP**. Khi một lời gọi phương thức đi **qua AOP proxy** tới target, nó cấp một bean **prototype mới**; khi lời gọi kết thúc, target được release và Spring thực hiện các destroy callback đã đăng ký cho instance đó.

Điểm cốt lõi: **tham chiếu proxy có thể giữ nguyên, còn target phía sau proxy thay đổi sau mỗi lần invocation**. Đây là cơ chế chọn và quản lý target của Spring AOP, **không phải** một annotation để khai báo pointcut/advice, nhưng có thể phối hợp với advisor, interceptor và advice trong cùng proxy.

```text
Client giữ proxy P
    P.process() lần 1
        -> getTarget() -> tạo prototype T1 và inject dependency
        -> advice + target method trên T1
        -> releaseTarget(T1) -> destroy T1
    P.process() lần 2
        -> getTarget() -> tạo prototype T2 và inject dependency
        -> advice + target method trên T2
        -> releaseTarget(T2) -> destroy T2
    P vẫn là một proxy; T1 và T2 là hai object khác nhau.
```

"Per invocation" ở đây là **mỗi invocation qua Spring AOP proxy được chuyển đến target**, không có nghĩa mỗi HTTP request, mỗi transaction, mỗi thread hay mỗi lần chạy toàn bộ workflow. Các lời gọi nội bộ qua `this` không đi qua proxy lần nữa.

## 2. Vì sao `@Scope("prototype")` chưa đủ?

| Cơ chế | Cách lấy instance | Khi gọi hai method trên cùng tham chiếu |
| --- | --- | --- |
| Bean `@Scope("prototype")` inject trực tiếp vào singleton | Tạo một bean mới **ở lúc inject** | Singleton vẫn giữ cùng bean đã inject |
| `ObjectProvider.getObject()` / `BeanFactory.getBean()` | Tạo bean mới **khi chủ động gọi hàm lấy bean** | Không tự động tạo theo mỗi method call trên bean đã lấy |
| AOP proxy với target singleton mặc định | Target cố định | Thường cùng target giữa các lần gọi |
| AOP proxy với `PrototypeTargetSource` | Target mới mỗi invocation cần target | Hai lần gọi đến hai target khác nhau và release riêng |

Do vậy, nếu yêu cầu là "client giữ một proxy chung nhưng mỗi lần gọi phương thức cần instance nghiệp vụ riêng", chỉ đặt scope prototype là chưa đạt. Phải cấu hình `PrototypeTargetSource` cho proxy. Bean đích mà `targetBeanName` trỏ tới **bắt buộc là prototype**, tránh nhầm target bean với proxy bean.

## 3. API và thứ tự vòng đời

Những API cần nắm:

- `PrototypeTargetSource.setTargetBeanName(String)`: chỉ định **tên** bean prototype để Spring tạo instance khi cần.
- `setBeanFactory(BeanFactory)`: cung cấp BeanFactory để tìm/khởi tạo target. Khi target source là bean Spring, cơ chế `BeanFactoryAware` có thể thiết lập bước này.
- `getTarget()`: lấy một instance prototype mới từ BeanFactory.
- `releaseTarget(Object)`: hủy chính instance đã lấy cho invocation đó.
- `getTargetClass()`: cung cấp type của target để cấu hình proxy; `isStatic()` thể hiện đây là target source động.
- Lớp cha `AbstractPrototypeBasedTargetSource` triển khai cơ chế `newPrototypeInstance()` và `destroyPrototypeInstance()` dùng BeanFactory.

Chu trình của một method call **đồng bộ đi tới target**:

1. Caller gọi phương thức trên Spring AOP proxy.
2. Proxy yêu cầu `getTarget()`; BeanFactory cấp prototype mới, resolve dependency, inject, xử lý bean post-processor và init callback theo cấu hình.
3. Chuỗi Spring AOP interceptor/advice và phương thức đích làm việc với instance vừa nhận.
4. Proxy hoàn tất lần gọi và thực hiện `releaseTarget(target)` ở phần dọn dẹp, kể cả khi target method ném exception sau khi target đã được cấp.
5. Khi caller gọi tiếp vào proxy, target source lấy một instance khác, không tái sử dụng instance vừa release.

**Khác biệt về destroy:** Bean prototype thông thường không được Spring tự theo dõi để hủy sau lúc đã trả cho người gọi. Riêng `PrototypeTargetSource` chủ động release từng target. Ở BeanFactory hỗ trợ `ConfigurableBeanFactory`, lớp cha sử dụng `destroyBean(beanName, instance)` để kích hoạt `DisposableBean.destroy()`, destroy method và các callback đã đăng ký, bao gồm `@PreDestroy` khi có cấu hình lifecycle phù hợp. Release **không đồng nghĩa** JVM giải phóng bộ nhớ ngay lập tức; garbage collector hoạt động riêng.

Không khẳng định **mọi** lời gọi Java trên proxy đều tạo target: JDK proxy có thể tự xử lý `equals`/`hashCode`; method nội bộ `this.helper()` trên target không phải invocation mới đi qua proxy. Tạo target mới cũng không xảy ra ngay lúc client chỉ lưu một tham chiếu proxy nếu chưa có lời gọi phải lấy target.

## 4. Ví dụ Java 17 chạy được với ProxyFactory

Ví dụ dùng Spring Framework 6.2.14 và Java 17, không cần Spring Boot hoặc AspectJ weaving. Tạo hai file:

```text
prototype-target-source-demo/
  pom.xml
  src/main/java/demo/PrototypeTargetSourceDemo.java
```

Nội dung `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>prototype-target-source-demo</artifactId>
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
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
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
      </plugin>
    </plugins>
  </build>
</project>
```

Nội dung `src/main/java/demo/PrototypeTargetSourceDemo.java`:

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.PrototypeTargetSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

public class PrototypeTargetSourceDemo {
    public interface Job {
        void process();
    }

    public static class JobImpl implements Job, DisposableBean {
        private static final AtomicInteger NEXT_ID = new AtomicInteger();
        private final int id;

        public JobImpl() {
            id = NEXT_ID.incrementAndGet();
            System.out.println("create " + id);
        }

        @Override
        public void process() {
            System.out.println("process " + id);
        }

        @Override
        public void destroy() {
            System.out.println("destroy " + id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Beans {
        @Bean("jobTarget")
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public JobImpl jobTarget() {
            return new JobImpl();
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Beans.class)) {
            var targetSource = new PrototypeTargetSource();
            targetSource.setTargetBeanName("jobTarget");
            targetSource.setBeanFactory(context);

            var factory = new ProxyFactory();
            factory.setInterfaces(Job.class);
            factory.setTargetSource(targetSource);
            factory.addAdvice((MethodInterceptor) invocation -> {
                System.out.println("advice before");
                try {
                    return invocation.proceed();
                } finally {
                    System.out.println("advice after");
                }
            });

            Job proxy = (Job) factory.getProxy();
            System.out.println("first");
            proxy.process();
            System.out.println("second");
            proxy.process();
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml` (máy có JDK 17+ và Maven):

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.PrototypeTargetSourceDemo
```

Những dòng do ứng dụng in ra (`System.out`; không tính thông báo riêng của Maven):

```text
first
create 1
advice before
process 1
advice after
destroy 1
second
create 2
advice before
process 2
advice after
destroy 2
```

Cách đọc output:

- `Job proxy` chỉ được lấy một lần từ `factory.getProxy()`. Hai lần gọi `proxy.process()` vẫn dùng cùng tham chiếu proxy.
- Mỗi lần Spring proxy lấy target, BeanFactory chạy `@Bean("jobTarget")` theo scope prototype; constructor tạo ID 1 rồi ID 2. Target đầu không được tái sử dụng cho lần thứ hai.
- Interceptor `MethodInterceptor` chứng minh advice của Spring AOP có thể chạy trên cả hai target. Trong luồng JDK proxy này, việc lấy target xảy ra trước khi interceptor in `advice before`.
- Sau method call, proxy release target, khiến `DisposableBean.destroy()` chạy. Vì thế `destroy 1` xuất hiện **trước** `second`, không đợi `context.close()` lúc cuối.
- `AtomicInteger` trong ví dụ chỉ cấp ID quan sát, không phải cơ chế bảo đảm trạng thái nghiệp vụ tồn tại qua các lần gọi.

Ta dùng JDK dynamic proxy qua interface `Job` để chương trình đơn giản và type-safe. Class-based proxy vẫn có thể dùng trong Spring AOP nhưng chịu hạn chế với final method/final class, nên không nên coi việc gọi method nào cũng intercept được.

## 5. Cấu hình tương đương bằng ProxyFactoryBean (XML)

Trong ứng dụng khai báo bean bằng Spring XML, cấu hình ba thành phần riêng: target prototype, target source và proxy.

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd">

  <bean id="jobTarget"
        class="demo.PrototypeTargetSourceDemo$JobImpl"
        scope="prototype"/>

  <bean id="jobTargetSource"
        class="org.springframework.aop.target.PrototypeTargetSource">
    <property name="targetBeanName" value="jobTarget"/>
  </bean>

  <bean id="jobProxy"
        class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="proxyInterfaces"
              value="demo.PrototypeTargetSourceDemo$Job"/>
    <property name="targetSource" ref="jobTargetSource"/>
  </bean>
</beans>
```

Trong cấu hình này, consumer sử dụng **`jobProxy`**. `jobTarget` chỉ là bean definition để BeanFactory tạo target theo yêu cầu. XML nhằm minh họa wiring; nếu muốn chạy XML độc lập, cần nạp file bằng một Spring ApplicationContext và có các class nêu trong ví dụ.

**Quan trọng:** `targetBeanName` là một **String tên bean**. Viết `value="jobTarget"`, không viết `ref="jobTarget"`, vì `ref` sẽ resolve và đưa vào setter một object bean, không phải tên để target source gọi BeanFactory mỗi lần. Cách Java tương ứng: `targetSource.setTargetBeanName("jobTarget")`.

## 6. Các tình huống dễ hiểu sai

### State có tồn tại giữa hai invocation không?

Không, nếu state nằm trong field của target prototype. Ví dụ, mỗi `JobImpl` có một field `int counter = 0`, lần đầu gọi tăng thành 1 thì target đó được release; lần sau là instance khác, counter bắt đầu từ 0. Nếu cần trạng thái lâu dài, lưu ở thành phần có scope hợp lý hoặc kho dữ liệu ngoài target.

### Hai thread gọi cùng proxy thì sao?

Mỗi invocation lấy prototype độc lập, nên hai target có thể cùng tồn tại trong hai thread và được release sau từng lời gọi. Tuy nhiên, dependency singleton được inject vào cả hai target vẫn là tài nguyên dùng chung, phải bảo đảm thread-safety cho dependency đó. Cơ chế target prototype không biến toàn bộ object graph thành thread-safe.

### Khi phương thức ném exception?

Với proxy Spring AOP thông thường, sau khi lấy được target thành công, proxy chạy phần release trong quá trình dọn dẹp ngay cả khi target method ném lỗi. Nếu tạo bean đã lỗi trước khi cấp target thì không có instance hoàn chỉnh để release theo luồng này. Đừng gọi `releaseTarget()` một lần nữa trong code nghiệp vụ; để proxy quản lý đúng instance đã lấy.

### Một method gọi `this.helper()`?

`this.helper()` chạy trực tiếp trên target hiện tại và không đi vòng qua AOP proxy. Vì thế nó không tạo thêm prototype mới và các advice Spring AOP áp dụng qua proxy cũng không tự chạy cho lời gọi nội bộ đó. Nếu cần interception riêng, hãy thiết kế lại ranh giới bean/proxy hoặc gọi qua proxy đúng cách, tránh dựa vào self-invocation.

### Với CompletableFuture hoặc luồng bất đồng bộ?

`PrototypeTargetSource` gắn target với **thời gian thực thi lời gọi phương thức thông qua proxy**. Nếu method trả ngay `CompletableFuture`, `Publisher` hay handle khác rồi công việc vẫn chạy phía sau, target có thể bị release **trước khi công việc async hoàn tất**. Không giữ tài nguyên phải dùng về sau trong target trừ khi có cơ chế ownership/lifecycle riêng.

### Destroy có phải thu hồi bộ nhớ ngay?

Không. Callback destroy có thể đóng file/socket, giải phóng resource; JVM thu gom heap khi đủ điều kiện. Nếu method trả `this` hoặc một object giữ reference tới target, caller vẫn có thể tham chiếu object đã chạy destroy callback. Đây là rủi ro về sử dụng sau release, không được giải quyết chỉ bằng GC.

## 7. Chi phí và lựa chọn thay thế trong Spring

Spring khuyến cáo chỉ dùng `PrototypeTargetSource` khi **có lý do rõ**: tạo target mỗi invocation còn kéo theo dependency injection, init/destroy callback và có thể chi phí tạo/đóng resource. Với service stateless bình thường, singleton thường đơn giản và hiệu quả hơn.

| Nhu cầu thực tế | Cách dùng Spring thường cân nhắc |
| --- | --- |
| Service stateless thông thường | Singleton và AOP proxy thông thường |
| Mỗi invocation qua proxy cần target độc lập | `PrototypeTargetSource` |
| Chỉ một lần lấy prototype tại ranh giới một job lớn | `ObjectProvider.getObject()` / `BeanFactory.getBean()` |
| Muốn tái sử dụng các instance thay vì hủy mỗi lần | `CommonsPool2TargetSource` nếu thật sự cần pooling |
| Trạng thái theo request/session web | Spring request/session scope trong đúng môi trường |

Sự khác biệt then chốt với pooling: `PrototypeTargetSource` **tạo mới và hủy sau mỗi lần dùng**; pooling target source có thể trả target về pool để invocation khác tái sử dụng.

## 8. Checklist kiểm tra và lỗi thường gặp

1. **Scope đích:** bean mà `targetBeanName` trỏ tới phải có scope `prototype`; kiểm tra chính xác tên bean và tên proxy.
2. **Wiring:** tạo target source ngoài IoC phải gắn `setBeanFactory`; khi cấu hình qua Spring bean, BeanFactoryAware xử lý. Sai tên bean sẽ gây lỗi khi resolve target.
3. **Đi qua proxy:** gọi trực tiếp `context.getBean("jobTarget")` là gọi prototype thông thường, không kích hoạt lifecycle target source của `jobProxy`.
4. **Đúng interface/type:** JDK proxy nên được inject/cast theo interface; không ép sang implementation. Class proxy không can thiệp được các lời gọi không thể override.
5. **Quan sát create/destroy:** đặt log vào constructor/init và `DisposableBean.destroy()`; gọi proxy hai lần, kiểm tra create/destroy cân bằng tương ứng với hai invocation đồng bộ.
6. **Tài nguyên:** không trả đối tượng đã/bị destroy ra ngoài; cần có thiết kế đặc biệt nếu công việc tiếp tục bất đồng bộ.
7. **Hiệu năng:** đo latency của bean construction, autowiring, initialization và teardown trước khi áp dụng rộng cho endpoint có tần suất cao.

## 9. Tự kiểm tra hiểu bài

**Hỏi:** Singleton inject prototype một lần, gọi dependency hai lần có hai object không?  
**Đáp:** Không; cùng object đã inject được gọi hai lần.

**Hỏi:** Proxy dùng `PrototypeTargetSource` được inject vào singleton thì có nhất thiết target cũng là singleton?  
**Đáp:** Không. Singleton giữ proxy, còn proxy lấy target prototype mới cho mỗi invocation.

**Hỏi:** Ai gọi `destroy()` trong ví dụ Java?  
**Đáp:** `PrototypeTargetSource.releaseTarget()` yêu cầu Spring chạy destroy callback sau mỗi lời gọi, qua phần release của AOP proxy.

**Hỏi:** `this.helper()` bên trong target có tự tạo prototype khác?  
**Đáp:** Không; lời gọi này vẫn chạy trên target đang có, không đi qua proxy.

**Hỏi:** Có thể giữ `CompletableFuture` và tin rằng target còn sống tới khi Future hoàn tất không?  
**Đáp:** Không; method có thể return trước, làm proxy release target sớm hơn công việc async.

## Tài liệu Spring chính thức

- Spring Framework Reference — Using TargetSource Implementations: https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html
- Javadoc — PrototypeTargetSource: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/target/PrototypeTargetSource.html
- Javadoc — AbstractPrototypeBasedTargetSource: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/target/AbstractPrototypeBasedTargetSource.html
- Javadoc — TargetSource: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/TargetSource.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-004">HotSwappableTargetSource và thay đối tượng đích</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: HotSwappableTargetSource và thay đối tượng đích

## Mục tiêu học tập

Trong Spring AOP, object mà caller sử dụng thường là **proxy**; proxy có thể lấy object nghiệp vụ thật từ một `TargetSource`. Với `org.springframework.aop.target.HotSwappableTargetSource`, ta có thể **thay target của proxy ngay khi ứng dụng đang chạy** mà không cần phát lại reference proxy cho mọi client.

Sau bài này, bạn có thể:

- Hiểu trách nhiệm của `TargetSource` và tại sao `HotSwappableTargetSource` hữu ích.
- Cấu hình `HotSwappableTargetSource` qua `ProxyFactory` hoặc `ProxyFactoryBean` của Spring.
- Dùng `swap(newTarget)` đúng cách, biết giá trị trả về là **target cũ**.
- Phân biệt tính thread-safe của thao tác swap với trạng thái và vòng đời của các request đang chạy.
- Giữ hợp đồng interface/type ổn định khi thay target và xác minh advice tiếp tục chạy qua cùng proxy.
- Nhận biết các rủi ro về dependency, target lifecycle, rollback, concurrent calls và tài nguyên khi thay một object có trạng thái.

**Phạm vi:** tập trung vào **Spring AOP TargetSource API**. Đây không phải kỹ thuật hot reload class, hot deployment, sửa bytecode hoặc đổi bean definition của cả Spring container.

## 1. Proxy, TargetSource và target có vai trò khác nhau

Một Spring AOP proxy nhận lời gọi từ client. Khi cần gọi logic nghiệp vụ thật, AOP framework yêu cầu `TargetSource` cung cấp object **target** có phương thức thích hợp.

~~~text
Client giữ một reference
          |
          v
    Spring AOP proxy
          |
          v
 TargetSource.getTarget()  ---> target v1 hoặc v2 đang được dùng
          |
          v
 Chuỗi advisor / interceptor trên invocation hiện tại
          |
          +--> proceed() tới business method, nếu không bị chặn
          |
          v
 releaseTarget(target) nếu source không static và đã lấy target
          |
          v
       Caller nhận kết quả / lỗi
~~~

Sơ đồ này minh họa đường JDK proxy thông thường: Spring lấy target trước khi chạy chuỗi interceptor. Một advice có thể trả về sớm mà không gọi target, nhưng việc release vẫn cần được xử lý cho target đã lấy. Với HotSwappableTargetSource, release không đồng nghĩa tự đóng target cũ; việc thu hồi sau swap thuộc chính sách riêng của ứng dụng.

Ba khái niệm không đồng nhất:

| Thành phần | Vai trò | Điều gì thay đổi khi swap? |
| --- | --- | --- |
| **Proxy** | Đối tượng public mà client giữ, nhận lời gọi qua giao diện hoặc class | Reference proxy không cần đổi |
| **Advisor/advice** | Chính sách áp dụng logic AOP vào invocation đi qua proxy | Thông thường không đổi khi chỉ swap target |
| **TargetSource** | Cấp phát/cung cấp target cho mỗi invocation khi Spring cần | Bên trong `HotSwappableTargetSource`, target hiện tại đổi |
| **Target** | Implementation thực thi business method | Từ object cũ sang object mới |

Giao diện chính của Spring:

~~~java
package org.springframework.aop;

public interface TargetSource extends TargetClassAware {
    Class<?> getTargetClass();
    Object getTarget() throws Exception;
    void releaseTarget(Object target) throws Exception;
    boolean isStatic();
}
~~~

Đây là **mô tả chữ ký API rút gọn** để tìm hiểu, không phải file cần tự triển khai; `getTargetClass()` được kế thừa từ `TargetClassAware` và một số phương thức có thể có implementation mặc định tùy phiên bản Spring. `getTarget()` cung cấp target đang được dùng, `releaseTarget(...)` là bước trả target sau invocation trong những implementation cần cleanup, `isStatic()` cho biết nguồn target có luôn cố định cùng một object hay không.

Khi bạn tạo `new ProxyFactory(target)` bình thường, Spring có thể dùng một target source cố định bao object đó. Khi muốn thay target runtime, hãy chọn implementation phù hợp ngay từ lúc dựng proxy, thay vì thay riêng reference trong application service.

## 2. HotSwappableTargetSource là gì?

`org.springframework.aop.target.HotSwappableTargetSource` là implementation có thể giữ một target hiện tại và **đổi nó bằng target mới** thông qua:

~~~java
HotSwappableTargetSource targetSource =
        new HotSwappableTargetSource(initialTarget);

Object previousTarget = targetSource.swap(newTarget);
~~~

Đây là fragment; `initialTarget` và `newTarget` cần là object thực, khác `null` và phù hợp với contract mà proxy được dựng để phục vụ.

Ý nghĩa API:

- `new HotSwappableTargetSource(initialTarget)`: thiết lập target ban đầu; đối số không được `null`.
- `swap(newTarget)`: thay target hiện tại bằng `newTarget`, trả về **target cũ**. Spring từ chối `null` bằng `IllegalArgumentException`.
- `getTarget()`: trả object hiện được source giữ; gọi trực tiếp method trên object này **không đi qua advice** của proxy.
- `getTargetClass()`: phản ánh class của target hiện tại; Spring khuyên kiểu target **nên ổn định** trong suốt quá trình thay đổi.
- `isStatic()`: target source có thể đổi object, nên không được suy diễn target cố định chỉ vì một proxy giữ nguyên reference.

Spring Framework ghi rõ `HotSwappableTargetSource` **thread-safe** trong việc thay target. Khi client gọi proxy ở lần tiếp theo, proxy lấy target phù hợp từ `TargetSource`. Điều này khác với việc gán biến local trong một service: client cần tiếp tục gọi qua **cùng Spring AOP proxy** để thấy sự chuyển đổi.

### “Hot swap” ở đây không làm gì?

Nó **không**:

1. Thay thế implementation class đã load trong JVM hoặc cập nhật bytecode của class.
2. Sửa mọi reference trực tiếp đến target cũ mà các object khác có thể đang giữ.
3. Tự cập nhật proxy interface, method signatures hoặc tất cả advisor/pointcut.
4. Bảo đảm mọi invocation đang thực thi sẽ dừng hoặc chuyển nửa chừng sang target mới.
5. Tự quản lý migration state, kết nối database, transaction, shutdown thread pool hoặc việc hủy target cũ.

Khả năng cung cấp target mới của `HotSwappableTargetSource` là một **điểm chuyển tiếp có kiểm soát ở ranh giới proxy**. Muốn an toàn trong production phải kiểm soát những phần nói trên bằng thiết kế ứng dụng.

## 3. Khi nào phù hợp?

Những tình huống có thể cân nhắc:

| Nhu cầu | Cách tiếp cận | Điều kiện quan trọng |
| --- | --- | --- |
| Thay implementation service từ phiên bản A sang B | Giữ proxy, swap target đang được delegate | Hai target tương thích contract và cấu hình AOP |
| Chuyển sang adapter mới sau kiểm tra health | Chuẩn bị và kiểm chứng target mới, swap khi sẵn sàng | Cần chính sách rollback và quản lý target cũ |
| Thử nghiệm trong một phần ứng dụng | Một proxy riêng có target source thay được | Chỉ caller giữ proxy đó mới nhận sự thay đổi |
| Thay chiến lược xử lý tại runtime | Hai object implement cùng service interface | Mức trạng thái/shared state phải được kiểm soát |

Không cần `HotSwappableTargetSource` khi chỉ muốn bật/tắt một feature flag đơn giản trong business logic, thay một URL cấu hình hoặc chuyển tuyến qua load balancer. Khi cần đổi kết quả nhưng không muốn quản lý toàn bộ vòng đời target, một strategy/router service cố định hoặc configuration service có thể dễ kiểm soát hơn. Đối với deployment theo nhiều instance, swap trên một JVM **không tự lan sang các instance khác**.

## 4. Ví dụ Java 17+ chạy được: cùng proxy, hai target

Mục tiêu: client giữ đúng một biến `GreetingService proxy`. Trước khi swap, lời gọi tới target `v1`. Sau khi `swap(...)`, **vẫn biến proxy đó** nhưng lời gọi tới target `v2`. Advice trên proxy tiếp tục chạy ở cả hai lượt.

Sử dụng Spring Framework 6.2.x, Maven, JDK 17+.

### 4.1. Cấu trúc

~~~text
spring-swappable-target/
├── pom.xml
└── src/main/java/example/SwappableTargetDemo.java
~~~

### 4.2. Tệp pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-swappable-target</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <version>3.14.1</version>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>example.SwappableTargetDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.3. Tệp src/main/java/example/SwappableTargetDemo.java

~~~java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.target.HotSwappableTargetSource;

public class SwappableTargetDemo {

    public interface GreetingService {
        String greet(String name);
    }

    public static final class PrefixGreeting
            implements GreetingService {

        private final String prefix;

        public PrefixGreeting(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String greet(String name) {
            return prefix + ":" + name;
        }
    }

    public static void main(String[] args) {
        PrefixGreeting first = new PrefixGreeting("v1");
        HotSwappableTargetSource swapper =
                new HotSwappableTargetSource(first);

        MethodInterceptor trace = invocation -> {
            System.out.println("ADVICE " +
                    invocation.getMethod().getName());
            return invocation.proceed();
        };

        ProxyFactory factory = new ProxyFactory();
        factory.setInterfaces(GreetingService.class);
        factory.setProxyTargetClass(false);
        factory.setTargetSource(swapper);
        factory.addAdvice(trace);

        GreetingService proxy =
                (GreetingService) factory.getProxy();
        GreetingService sameReference = proxy;

        System.out.println("JDK proxy: " +
                AopUtils.isJdkDynamicProxy(proxy));
        System.out.println("Before: " + proxy.greet("Lan"));

        Object old = swapper.swap(new PrefixGreeting("v2"));
        System.out.println("Old returned: " + (old == first));
        System.out.println("After: " + sameReference.greet("Lan"));

        System.out.println("Same proxy: " +
                (proxy == sameReference));
        System.out.println("Current target changed: " +
                (swapper.getTarget() != old));

        // rollback: đưa target cũ trở lại
        Object replaced = swapper.swap(old);
        System.out.println("Rollback replaced v2: " +
                (replaced != old));
        System.out.println("Restored: " + proxy.greet("Lan"));
    }
}
~~~

### 4.4. Chạy

Tại thư mục chứa `pom.xml`, với Maven và JDK 17+:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình in ra:

~~~text
JDK proxy: true
ADVICE greet
Before: v1:Lan
Old returned: true
ADVICE greet
After: v2:Lan
Same proxy: true
Current target changed: true
Rollback replaced v2: true
ADVICE greet
Restored: v1:Lan
~~~

Maven hoặc môi trường chạy có thể in thông tin riêng; các dòng trên là phần stdout được tạo trực tiếp từ code.

### 4.5. Giải thích từng bước

1. `first` là target ban đầu: `PrefixGreeting("v1")`. `HotSwappableTargetSource` giữ target này và cung cấp nó khi proxy cần thực thi method.
2. `ProxyFactory` được khai báo với **service interface** `GreetingService`, `setTargetSource(swapper)` và `setProxyTargetClass(false)`. Dù implementation `PrefixGreeting` được khai báo `final`, JDK dynamic proxy vẫn phù hợp vì caller gọi qua interface, không cần tạo subclass của implementation.
3. `factory.addAdvice(trace)` đặt interceptor tại proxy. Nó in `ADVICE greet` trước khi target chạy; interceptor này vẫn là một phần của **proxy không đổi** trong suốt ba lần gọi.
4. `proxy.greet("Lan")` lần đầu nhận kết quả từ `v1`.
5. `swapper.swap(new PrefixGreeting("v2"))` cập nhật source, trả `first` làm old target. Biểu thức `old == first` trả `true` vì `swap` trả cùng object cũ.
6. `sameReference.greet("Lan")` chạy qua **cùng reference proxy** và lấy target hiện tại là `v2`; kết quả đổi thành `v2:Lan` nhưng advice vẫn chạy.
7. `swapper.swap(old)` thực hiện rollback về target `v1`, trả về object `v2` vừa bị thay; proxy không được tạo lại và caller không phải re-inject.
8. `swapper.getTarget()` trong ví dụ chỉ được dùng để xác minh reference. Nếu gọi `((GreetingService) swapper.getTarget()).greet(...)` trực tiếp, lời gọi đó **không đi qua advice** trên proxy.

**Chú ý quan trọng về target final:** ví dụ đặt implementation `final` có chủ đích để khẳng định **proxy interface** được dùng. Nếu bạn bật `setProxyTargetClass(true)`, Spring phải sử dụng subclass proxy (CGLIB), nên class `final` sẽ không thể được proxy theo kiểu đó.

## 5. Cơ chế swap và tính nhất quán khi đang có request

Spring mô tả `HotSwappableTargetSource` là **thread-safe**. Điều này đảm bảo cơ chế lấy/đổi reference target của source an toàn khi các thread cùng truy cập. Nhưng có ba thời điểm phải phân biệt:

~~~text
Thread A (gọi qua proxy)            Thread B (quản trị)
--------------------------------    ----------------------------
getTarget() -> nhận target v1
                                    swap(v2) -> trả target v1
invoke v1.greet(...)
releaseTarget(v1)
                                    Gọi proxy tiếp theo có thể lấy v2
~~~

**Lời gọi đã lấy v1 không bị chuyển giữa chừng sang v2**. Thread A có thể hoàn thành trên v1 sau khi Thread B gọi `swap(v2)`. Đây là đặc tính cần chấp nhận hoặc giải quyết bằng một giao thức điều phối riêng nếu ứng dụng yêu cầu chuyển tiếp hoàn toàn không còn invocation trên target cũ.

### Phạm vi thread-safe cụ thể

- `swap` và `getTarget` phối hợp để không tạo tình trạng đọc reference hỏng.
- Việc swap thành công **không làm business state của target mới tự thread-safe**. Nếu target được gọi đồng thời, target phải có các bảo đảm concurrency riêng.
- Không có bảo đảm `swap` tự chờ các request đang xử lý xong.
- Không có bảo đảm mọi request ở nhiều node ứng dụng đều thấy chuyển đổi cùng thời điểm.
- `swap` không tạo một transaction database nguyên tử gắn với tất cả business operations đang chạy.

Một sơ đồ thời gian có thể có:

~~~text
t0  Request A vào proxy -> lấy target v1
t1  Admin swap(target v2)
t2  Request B vào proxy -> lấy target v2
t3  Request B kết thúc bằng v2
t4  Request A kết thúc bằng v1
~~~

Nếu hệ thống yêu cầu **drain** mọi request ở v1 trước khi hủy v1, bạn cần xây cơ chế đếm inflight requests, routing/quiescing, hoặc phối hợp lifecycle rõ ràng. Chỉ thay target reference là chưa đủ.

## 6. Giữ giao diện và kiểu target tương thích

Proxy không tự phát hiện và công bố một interface mới mỗi lần swap. Với JDK dynamic proxy, tập interface được xác định **khi dựng proxy**.

Ví dụ proxy được tạo với:

~~~java
factory.setInterfaces(GreetingService.class);
~~~

thì object mới truyền vào `swap(...)` nên **implement `GreetingService`** với semantics tương thích. `HotSwappableTargetSource` tập trung vào thay reference và kiểm tra target không null; nó **không phải bộ kiểm định đầy đủ** cho nghiệp vụ hoặc API compatibility của object mới. Nếu truyền object không thực hiện được method mà proxy cần gọi, caller có thể gặp lỗi tại runtime.

Một lưu ý từ API: `getTargetClass()` phản ánh class target hiện tại, nhưng tài liệu Spring khuyên **class target thường nên giữ ổn định**. Thay một object khác class có thể tạo xung đột với assumptions/caching của proxy, advisor, class filter hoặc CGLIB. Cách an toàn nhất là giữ cùng implementation class hoặc ít nhất cùng API đã kiểm chứng; trong ví dụ chúng ta giữ đúng `PrefixGreeting` cho cả v1 và v2.

### Proxy theo class khác proxy theo interface

| Vấn đề | JDK dynamic proxy | CGLIB class proxy |
| --- | --- | --- |
| Cần class target được kế thừa? | Không | Có, class không final |
| Bắt buộc target mới cùng concrete class? | Nên giữ ổn định; interface bắt buộc tương thích | Thực tế cần đặc biệt cẩn thận về type và method dispatch |
| Proxy public type | Các interface được cấu hình | Target class/subclass và interface phù hợp |
| Đổi sang class mới có method bổ sung | Method mới không tự xuất hiện trên interface proxy | Proxy cũ không tự mở rộng surface |
| `this.inner()` trong target | Không tự chạy qua proxy | Không tự chạy qua proxy |

Đừng xem việc swap target là cách thay đổi contract của một bean. Nếu cần contract mới, thường phải tạo proxy/API mới và cập nhật dependency injection/routing ở cấp ứng dụng.

## 7. Cấu hình với Spring container qua ProxyFactoryBean

`ProxyFactory` ở ví dụ mục 4 rất trực tiếp. Nếu sử dụng BeanFactory/ApplicationContext với cấu hình XML, Spring cung cấp `org.springframework.aop.framework.ProxyFactoryBean`, có thuộc tính `targetSource`.

Ví dụ với một interface nghiệp vụ `example.GreetingService` và lớp `example.PrefixGreeting` (giả định class này có constructor nhận `String`):

~~~xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="initialTarget" class="example.PrefixGreeting">
        <constructor-arg value="v1"/>
    </bean>

    <bean id="swapper"
          class="org.springframework.aop.target.HotSwappableTargetSource">
        <constructor-arg ref="initialTarget"/>
    </bean>

    <bean id="greetingService"
          class="org.springframework.aop.framework.ProxyFactoryBean">
        <property name="targetSource" ref="swapper"/>
        <property name="proxyInterfaces" value="example.GreetingService"/>
    </bean>
</beans>
~~~

Đây là **cấu hình XML minh họa**, không chạy độc lập cùng file Java ví dụ trước vì tên package/class `example.GreetingService` và `example.PrefixGreeting` cần được khai báo ở mức top-level đúng với cấu hình. Nó thể hiện nguyên lý:

- `initialTarget` là implementation khởi tạo ban đầu.
- `swapper` là bean `HotSwappableTargetSource` độc lập mà thành phần quản trị có thể lấy từ context.
- `greetingService` là proxy bean mà client sử dụng; Spring dùng target source tùy biến để lấy target mỗi invocation.
- `proxyInterfaces` giúp proxy expose một API ổn định.

Trong một thành phần đã được cấp quyền vận hành:

~~~java
HotSwappableTargetSource swapper =
        context.getBean("swapper", HotSwappableTargetSource.class);
Object previous = swapper.swap(newTarget);
~~~

Đây là fragment, cần `context` có sẵn và `newTarget` là object đã được chuẩn bị. Theo Spring, client giữ reference tới `greetingService` không cần lấy bean lại sau swap.

**Có thể gắn advice cùng một target source** thông qua advisor/interceptor cấu hình trong proxy. `TargetSource` tự nó không phải advisor; nó điều khiển **target nào được gọi**, còn advice/pointcut điều khiển **hành vi bổ sung và method match**.

## 8. Thay target khác với thay bean trong ApplicationContext

Dễ nhầm ba thao tác:

| Thao tác | Phạm vi hiệu lực |
| --- | --- |
| `swapper.swap(newTarget)` | Đổi target mà một `HotSwappableTargetSource` cụ thể sẽ cấp cho proxy đang sử dụng nó |
| Thay một singleton bean trong container bằng cơ chế đăng ký bean riêng | Liên quan bean registry/dependency đã được inject; không tự đồng nghĩa với swap proxy |
| Tạo `new ProxyFactory(newTarget).getProxy()` | Tạo **proxy mới**, caller đang giữ proxy cũ không tự chuyển qua reference mới |

Nếu service A đã inject `GreetingService greetingService` là **proxy**, nó tiếp tục gọi proxy đó và thấy target mới sau swap.

Nếu service B giữ trực tiếp `PrefixGreeting first`, `swap` không đổi field hoặc reference đã inject cho service B. Đó là lý do cần xác định **mọi caller thực sự đi qua proxy** trước khi dựa vào cơ chế hoán đổi.

Với hệ thống nhiều `ApplicationContext`, nhiều `ProxyFactoryBean` hoặc nhiều node JVM, mỗi target source có trạng thái riêng. Việc thay một source không đồng nghĩa thay tất cả.

## 9. Vòng đời của target cũ và mới

`swap(newTarget)` **trả về target cũ**, nhưng không đồng nghĩa target cũ đã được đóng, hủy bean hay giải phóng tài nguyên.

Ví dụ target giữ network connection, cache local, executor hoặc thread nền. Sau khi swap:

1. Target cũ có thể vẫn đang xử lý các invocation bắt đầu trước `swap`.
2. Target cũ có thể còn được object khác tham chiếu trực tiếp.
3. Các callback Spring như `@PreDestroy` hoặc custom destroy method **không được mặc nhiên kích hoạt** chỉ vì gọi `swap`.
4. Target mới cần được chuẩn bị dependency/configuration, health-check và đủ khả năng nhận request trước khi được công bố.
5. Nếu cần rollback, giữ target cũ cho tới khi chính sách chuyển đổi xác nhận an toàn.

### Quy trình thay target có kiểm soát

- **Chuẩn bị:** dựng target mới theo cùng business contract; inject phụ thuộc và validate cấu hình trước.
- **Xác minh:** chạy kiểm tra health/smoke test an toàn theo nghiệp vụ; không tự động chạy business side effect thật chỉ vì check.
- **Chuyển:** gọi `swap(newTarget)` một lần ở bộ phận quản trị được kiểm soát, giữ reference target cũ.
- **Quan sát:** kiểm tra lỗi, độ trễ và tính tương thích trên lời gọi qua proxy sau swap.
- **Rollback nếu cần:** `swap(previousTarget)` để đưa lại target cũ; rollback là một swap mới, không “tua lại” công việc đã được v2 xử lý.
- **Thu hồi:** chỉ đóng/hủy old target khi chắc không còn inflight invocation hoặc reference khác cần dùng nó.

Đây là quy trình ứng dụng đề xuất, **không phải logic Spring tự động thực hiện**. Nếu target thuộc quyền quản lý của Spring container, việc tự gọi `close()` hoặc `destroy()` bừa bãi có thể xung đột với ownership/lifecycle bean, cần có thỏa thuận rõ ràng.

## 10. Advice, pointcut và transaction có còn hoạt động sau swap?

Khi gắn advice vào một proxy Spring AOP rồi dùng `HotSwappableTargetSource`, advice vẫn thuộc proxy, vì vậy những lời gọi đi qua proxy **vẫn đi qua interceptor chain**.

Ví dụ ở mục 4:

~~~java
factory.setTargetSource(swapper);
factory.addAdvice(trace);
~~~

Hai dòng là fragment của chương trình đầy đủ: target source quyết định object v1/v2; `trace` ghi log mọi invocation được advice. Sau swap, các dòng `ADVICE greet` vẫn xuất hiện vì proxy và interceptor không đổi.

Tuy nhiên:

- `PointcutAdvisor` có `ClassFilter`/`MethodMatcher`. Thay target class có thể khiến điều kiện khớp hoặc dữ liệu đã cache trước đó khác với kỳ vọng; giữ target type ổn định.
- Transaction/security/cache/retry advisors có ngữ nghĩa riêng. Swap không biến state của v1 thành state của v2 và không bảo đảm business transaction xuyên hai target tự động nhất quán.
- Proxy chỉ intercept method call đi qua proxy. Self-invocation, gọi trực tiếp target từ `getTarget()` và các giới hạn JDK/CGLIB vẫn tồn tại.
- Nếu target mới tự đưa ra các side effect khác dù cùng interface, advice chung không làm hai implementation tương đương về nghiệp vụ.

**Đừng gỡ advisor rồi dựng proxy mới chỉ vì muốn đổi target.** Nếu ý định là giữ nguyên các chính sách AOP và client references, chính `HotSwappableTargetSource` giải quyết phần thay target.

## 11. Những sai lầm thường gặp

**Sai 1 — “swap đổi reference proxy của mọi client.”** Sai. Proxy là cùng object. `TargetSource` nằm phía sau proxy được thay target.

**Sai 2 — “swap trả target mới.”** Sai. `swap(newTarget)` trả về **target cũ**, hữu ích cho rollback và lifecycle management.

**Sai 3 — “swap target đang chạy thì tất cả request chuyển ngay.”** Không bảo đảm. Invocation đã lấy old target có thể hoàn thành trên old target trong khi invocation mới đã dùng new target.

**Sai 4 — “Thread-safe swap nghĩa là service mới và service cũ thread-safe.”** Sai. Tính an toàn khi đổi reference target không thay thế kiểm soát concurrency trên state nghiệp vụ.

**Sai 5 — “Có thể swap bất kỳ object không tương thích nào.”** Không nên. Constructor/swap không thay cho validation hợp đồng service. Proxy interface/method/class filter đã được cấu hình trước và runtime có thể lỗi khi object mới không đáp ứng contract.

**Sai 6 — “Đưa null vào swap để tạm dừng service.”** Sai. `HotSwappableTargetSource` không chấp nhận `null` và ném `IllegalArgumentException`. Nếu muốn chế độ ngắt dịch vụ, cung cấp một implementation hợp lệ có semantics ngắt rõ ràng hoặc điều khiển request ở lớp routing.

**Sai 7 — “Có target mới thì advisor tự đổi.”** Không. Advisor và pointcut là cấu hình AOP khác với lựa chọn target. Khi cần thay advisor, dùng API cấu hình proxy phù hợp.

**Sai 8 — “Có proxy nên mọi method đều được advice.”** Không. Cần match pointcut và đi qua proxy; method private/final khi dùng CGLIB hay self-invocation vẫn có giới hạn.

**Sai 9 — “Target cũ chắc chắn đã không ai dùng ngay sau swap.”** Sai. Có thể còn invocation đang chạy và reference khác. Phải có điều kiện quiescence/drain trước khi cleanup.

**Sai 10 — “Đổi target tương đương nâng cấp code runtime.”** Sai. Code class mới muốn thực thi phải đã có trên classpath/classloader với contract phù hợp; swap chỉ đổi object mà proxy delegate.

**Sai 11 — “Rollback đưa dữ liệu về đúng trạng thái trước swap.”** Sai. Rollback đổi target reference. Giao dịch, event, cache và side effect mà v2 đã thực thi không bị hoàn tác bởi `swap(previousTarget)`.

**Sai 12 — “getTargetClass luôn thay đổi an toàn bất kể proxy.”** Không nên. Javadoc lưu ý type nên giữ ổn định; hãy thiết kế target replacement theo cùng type hoặc contract tương thích và kiểm chứng thực tế.

## 12. Checklist khi áp dụng trong Spring AOP

1. **Chọn proxy ổn định:** caller phải dùng proxy được tạo với `HotSwappableTargetSource`, không giữ target trực tiếp.
2. **Chọn contract ổn định:** ưu tiên business interface phù hợp hoặc giữ đúng target class để không gây xung đột AOP matching.
3. **Chuẩn bị target mới:** dependency, cấu hình, health, state, thread safety và resource lifecycle sẵn sàng.
4. **Kiểm tra lời gọi qua proxy:** bảo đảm advice, pointcut và kết quả nghiệp vụ đúng cả trước/sau swap.
5. **Đổi target có kiểm soát:** gọi `swap(newTarget)` và lưu old reference nếu cần rollback.
6. **Theo dõi concurrent requests:** biết invocation đã chạy trên v1 có thể tiếp tục khi v2 đã hoạt động.
7. **Thu hồi đúng lúc:** tránh đóng resource của target cũ khi còn request hoặc người giữ reference.
8. **Kiểm tra nhiều instance:** nếu ứng dụng chạy nhiều JVM, từng node cần cơ chế rollout/quản trị riêng.
9. **Chuẩn bị rollback:** swap lại old target chỉ khi old target vẫn hợp lệ; kiểm soát side effect của v2 riêng.
10. **Giữ giới hạn Spring AOP:** self-invocation và những phương thức không được proxy không tự được giải quyết bởi target source.

## 13. Câu hỏi tự kiểm tra

1. `HotSwappableTargetSource` nằm trước hay sau client-facing proxy? Nó chịu trách nhiệm phần nào?
2. `swap(newTarget)` trả về object nào, và vì sao reference đó có ích?
3. Client đã inject một Spring AOP proxy từ ApplicationContext có cần inject lại sau swap không?
4. Vì sao target v1 vẫn có thể hoàn thành một request **sau** khi `swap(v2)` trả về?
5. Nếu v2 không implement interface mà JDK proxy expose, cơ chế swap có tự thích nghi interface không?
6. `HotSwappableTargetSource` có tự chạy `@PreDestroy` cho target cũ không?
7. Vì sao `getTarget()` rồi gọi trực tiếp method không tương đương với gọi qua proxy?
8. Khi sử dụng CGLIB với class `final`, swap có giúp vượt qua hạn chế này không?
9. Nếu v2 ghi dữ liệu ngoài hệ thống rồi bị rollback target về v1, dữ liệu v2 đã ghi có tự hoàn tác không?
10. Bạn sẽ kiểm chứng advice vẫn chạy trước và sau swap thế nào?

## 14. Tài liệu Spring chính thức

- [Using TargetSource Implementations — Spring Framework 6.2](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/targetsource.html): mô hình TargetSource, ví dụ cấu hình HotSwappableTargetSource và thread safety.
- [HotSwappableTargetSource Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/target/HotSwappableTargetSource.html): constructor, swap, getTarget, getTargetClass.
- [TargetSource Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/TargetSource.html): giao diện cung cấp target cho invocation.
- [Creating AOP Proxies with ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html): tạo proxy bằng Spring API.
- [ProxyFactoryBean](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pfb.html): cấu hình proxy trong Spring IoC container.
- [Spring AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): giới hạn JDK/CGLIB và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-005">LazyInitTargetSource và trì hoãn khởi tạo đối tượng đích</a>

<details>
<summary>Xem chi tiết</summary>

# LazyInitTargetSource và trì hoãn khởi tạo đối tượng đích trong Spring AOP

## 1. Mục tiêu

Trong Spring AOP, đôi khi ứng dụng cần **một proxy sẵn sàng để inject hoặc gọi**, nhưng target bean phía sau proxy rất tốn tài nguyên để tạo: constructor nạp cấu hình, kết nối dịch vụ, chuẩn bị mô hình dữ liệu hoặc khởi tạo một thành phần ít dùng. Nếu target được tạo ngay khi `ApplicationContext` khởi động, thời gian startup và lượng tài nguyên chiếm giữ có thể tăng dù không có lời gọi nghiệp vụ nào cần nó.

Spring Framework cung cấp `org.springframework.aop.target.LazyInitTargetSource` để **trì hoãn việc lấy singleton target bean từ `BeanFactory`**. Proxy được cấu hình với một `TargetSource` tham chiếu tên target bean, thay vì chứa một object target đã khởi tạo sẵn. Lần sử dụng đầu tiên cần đến target sẽ buộc `TargetSource` lấy singleton đó từ BeanFactory; những lần sau tái sử dụng cùng target.

Điểm khác biệt quan trọng của Spring AOP:

- **Proxy object** có thể tồn tại và được inject trước.
- **Target object** do BeanFactory quản lý có thể chưa được tạo.
- **Advice/advisor chain** vẫn do Spring AOP xử lý trên proxy, không bị thay bằng cơ chế lazy loading.
- Target vẫn phải được đăng ký với BeanFactory và bảo đảm chính bean definition đó không bị cơ chế eager singleton creation khởi tạo trước.
- Việc trì hoãn target không có nghĩa mọi lỗi đều được phát hiện ở startup: lỗi constructor hoặc dependency có thể chuyển sang **lời gọi đầu tiên qua proxy**.

Bài tập trung vào `LazyInitTargetSource` của **Spring AOP** và cách dùng với `ProxyFactory`/`ProxyFactoryBean`, không phải bài tổng quan về Java lazy initialization hoặc AspectJ bytecode weaving.

## 2. TargetSource nằm ở đâu trong đường gọi?

Một Spring AOP proxy không nhất thiết giữ trực tiếp một instance target cố định. Nó có thể giữ một `TargetSource` cho biết target nào cần dùng tại lúc method invocation.

~~~text
ApplicationContext / BeanFactory
    |
    +-- reportTarget (singleton, lazy-init)
    |       [bean definition đã đăng ký]
    |       [instance CHƯA khởi tạo lúc startup]
    |
    +-- reportService (Spring AOP proxy, sẵn sàng inject)
            |
            +-- Advisors / interceptors
            |
            +-- LazyInitTargetSource
                    |
                    +-- targetBeanName = "reportTarget"
                    +-- BeanFactory reference
                    |
                    +-- lần cần target đầu tiên:
                          BeanFactory.getBean("reportTarget")
                          -> tạo, inject, initialize singleton target
                    |
                    +-- các lần sau:
                          trả về singleton đã được lấy
~~~

Các lớp/API cần phân biệt:

| Thành phần | Nhiệm vụ trong Spring AOP |
| --- | --- |
| `ProxyFactory` hoặc `ProxyFactoryBean` | Tạo/cung cấp Spring proxy với advisor chain và target source |
| `TargetSource` | Contract lấy target dùng cho một invocation: `getTarget()`, `getTargetClass()`, `isStatic()`, `releaseTarget(...)` |
| `LazyInitTargetSource` | Lấy một **singleton bean** từ BeanFactory theo nhu cầu, cache target đã truy xuất |
| `BeanFactory` / `ApplicationContext` | Đăng ký bean definition, tạo/inject/lifecycle của target |
| `Advisor` / `MethodInterceptor` | Quyết định điều kiện advice và xử lý lời gọi qua proxy |
| `reportTarget` | Bean nghiệp vụ thật; không phải chính proxy |

Lưu ý `getTarget()` là điểm framework lấy target trong quá trình xử lý lời gọi. Không coi việc tạo proxy tự động tương đương đã khởi tạo target. Ngược lại, đừng dựa vào một thứ tự tuyệt đối giữa việc lấy target với việc chạy từng advice: đường thực thi cụ thể của proxy có thể phải resolve target **trước khi bắt đầu interceptor chain**. Đảm bảo được cung cấp bởi cách dùng này là target được lấy theo nhu cầu của proxy, không phải thứ tự log giữa constructor và advice cho mọi cấu hình.

## 3. API của LazyInitTargetSource

Lớp chính thức:

~~~java
import org.springframework.aop.target.LazyInitTargetSource;

LazyInitTargetSource targetSource = new LazyInitTargetSource();
targetSource.setTargetBeanName("reportTarget");
targetSource.setBeanFactory(beanFactory);
~~~

Đoạn mã giả định `beanFactory` đã được khai báo là một `org.springframework.beans.factory.BeanFactory` có bean `reportTarget`. Dòng thứ nhất tạo object `TargetSource`, **không** tạo target. `setTargetBeanName` chỉ định bean ID; `setBeanFactory` cung cấp factory mà TargetSource sẽ truy vấn.

Các API quan trọng:

- `setTargetBeanName(String)`: chọn target bean theo tên. Nếu sai ID, lỗi thường lộ ra khi target được request.
- `setBeanFactory(BeanFactory)`: gắn owning factory. Nếu `LazyInitTargetSource` được Spring quản lý như một bean, Spring thường gọi callback `BeanFactoryAware` thay bạn; còn khi cấu hình programmatically thì phải thiết lập.
- `setTargetClass(Class<?>)`: tùy chọn cung cấp thông tin target type tường minh, tránh phải chạm vào bean để suy ra kiểu (đặc biệt hữu ích với một số `FactoryBean`).
- `getTargetClass()`: cho Spring biết loại target. Việc resolve type đôi khi cần tương tác BeanFactory; không giả định mọi bean definition đều suy ra type mà không khởi tạo.
- `getTarget()`: lấy target singleton (tạo theo nhu cầu lần đầu) từ BeanFactory.
- `postProcessTargetObject(Object)`: extension hook dành cho subclass, được gọi khi target được load lần đầu để xử lý thêm nếu có nhu cầu đặc biệt.

### Quan trọng: singleton, không phải prototype mỗi invocation

`LazyInitTargetSource` hướng tới **singleton bean được khởi tạo muộn**. Sau khi lần đầu lấy target, những lần tiếp theo thông thường vẫn dùng **cùng instance**. Nó **không** tạo một đối tượng mới cho mỗi method call và cũng không tự thay target theo request.

Nếu nghiệp vụ cần target mới theo từng invocation, hãy nghiên cứu riêng `PrototypeTargetSource`, pooling/refreshable target sources trong Spring AOP; đó là semantics khác, không nên biến `LazyInitTargetSource` thành giải pháp nhiều instance.

`TargetSource.isStatic()` là một contract tối ưu hóa ở tầng Spring AOP; không suy diễn rằng “singleton được cache” đồng nghĩa chắc chắn mọi subclass/implementation trả `isStatic() == true`. Điều cần kiểm tra trực tiếp với `LazyInitTargetSource` là **identity của target được sử dụng lại sau lần khởi tạo đầu**; không dùng giá trị `isStatic` để đánh giá đã khởi tạo hay chưa.

## 4. Điều kiện tiên quyết: target bean cũng phải lazy

Spring `ApplicationContext` mặc định có thể **pre-instantiate các non-lazy singleton** trong lúc `refresh()`. Vì vậy cấu hình:

~~~xml
<bean id="reportTarget" class="demo.ReportService"/>
~~~

có thể đã tạo `reportTarget` lúc startup, trước khi `LazyInitTargetSource` được dùng. Khi đó việc bọc bằng TargetSource không thể quay ngược thời gian để “chưa tạo” một object đã được container khởi tạo.

Đặt bean definition thành lazy:

~~~xml
<bean id="reportTarget"
      class="demo.ReportService"
      lazy-init="true"/>
~~~

Hoặc dùng `RootBeanDefinition.setLazyInit(true)` nếu đăng ký bean programmatically. Bản thân `lazy-init="true"` nói BeanFactory không cần eagerly pre-instantiate bean ở startup; nó không bảo đảm bean luôn chưa được tạo nếu một bean eager khác yêu cầu dependency đó ngay trong quá trình refresh.

**Phân biệt hai lớp lazy:**

1. `lazy-init` trên **target bean definition**: tránh Spring tạo singleton target khi pre-instantiating.
2. `LazyInitTargetSource` trên **AOP proxy**: để proxy có thể tồn tại/được inject mà không truy xuất target cho đến khi proxy cần nó.

Hai lớp này phối hợp để có kết quả đúng khi ứng dụng muốn injected service **là proxy sẵn dùng** nhưng target vẫn chưa khởi tạo. Chỉ thêm `lazy-init="true"` mà eager bean trực tiếp inject target có thể khiến container phải tạo target để thỏa dependency. Khi eager bean inject **proxy** thay vì raw target, container có thể cấp proxy mà không cần tạo target.

Nếu target singleton được một nơi khác gọi `context.getBean("reportTarget")` hoặc được inject trực tiếp trước đó, target có thể đã được tạo. `LazyInitTargetSource` không cách ly bean khỏi những đường truy cập khác.

## 5. Ví dụ Java 17 chạy được: Spring proxy sẵn, target tạo khi gọi lần đầu

Ví dụ dùng Java 17, Maven, Spring Framework 6.1.15. Nó tạo một `GenericApplicationContext`, đăng ký singleton target với `lazyInit=true`, sau đó cấu hình `LazyInitTargetSource` cho `ProxyFactory`. Một advice ghi log chứng minh các lời gọi nghiệp vụ đi qua Spring AOP proxy, trong khi thời điểm tạo object target được quan sát riêng.

Cấu trúc:

~~~text
spring-aop-lazy-target-demo/
├── pom.xml
└── src/main/java/demo/LazyTargetDemo.java
~~~

### 5.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-lazy-target-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.15</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.1.15</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>17</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.LazyTargetDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Đây là cấu hình Maven với Spring AOP programmatic API; không dùng `@Aspect` expression nên không cần `aspectjweaver` chỉ để chạy ví dụ này.

### 5.2. src/main/java/demo/LazyTargetDemo.java

~~~java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.LazyInitTargetSource;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;

public class LazyTargetDemo {

    public interface ReportApi {
        String render(String reportId);
    }

    public static class ReportService implements ReportApi {
        public ReportService() {
            System.out.println("target: constructor");
        }

        @Override
        public String render(String reportId) {
            System.out.println("target: render " + reportId);
            return "ready " + reportId;
        }
    }

    public static void main(String[] args) {
        try (GenericApplicationContext context =
                     new GenericApplicationContext()) {

            // Đăng ký target bằng bean definition, chưa tạo instance.
            RootBeanDefinition bean = new RootBeanDefinition(ReportService.class);
            bean.setLazyInit(true);
            context.registerBeanDefinition("reportTarget", bean);

            // Refresh context: lazy singleton target chưa pre-instantiate.
            context.refresh();

            System.out.println("startupTargetCreated="
                    + context.getBeanFactory().containsSingleton("reportTarget"));

            // TargetSource dùng lại chính BeanFactory của context.
            LazyInitTargetSource lazy = new LazyInitTargetSource();
            lazy.setTargetBeanName("reportTarget");
            lazy.setBeanFactory(context.getBeanFactory());
            lazy.setTargetClass(ReportService.class);

            // Tạo Spring AOP proxy mà chưa lấy target bean.
            ProxyFactory factory = new ProxyFactory();
            factory.setInterfaces(ReportApi.class);
            factory.setProxyTargetClass(false);
            factory.setTargetSource(lazy);

            MethodInterceptor advice = invocation -> {
                System.out.println("advice: before");
                Object result = invocation.proceed();
                System.out.println("advice: after");
                return result;
            };
            factory.addAdvice(advice);

            ReportApi proxy = (ReportApi) factory.getProxy();
            System.out.println("proxyCreatedTarget="
                    + context.getBeanFactory().containsSingleton("reportTarget"));

            // Proxy invocation đầu tiên cần target.
            System.out.println("result=" + proxy.render("R-1"));
            System.out.println("afterFirstTargetCreated="
                    + context.getBeanFactory().containsSingleton("reportTarget"));

            // Cùng singleton target được dùng lại.
            System.out.println("result=" + proxy.render("R-2"));
        }
    }
}
~~~

Chạy từ thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình tự in trong trường hợp classpath/config như ví dụ, không tính log Maven/Framework:

~~~text
startupTargetCreated=false
proxyCreatedTarget=false
target: constructor
advice: before
target: render R-1
advice: after
result=ready R-1
afterFirstTargetCreated=true
advice: before
target: render R-2
advice: after
result=ready R-2
~~~

**Đọc output:**

1. Sau `context.refresh()`, `containsSingleton("reportTarget")` là `false` vì bean definition đã được đặt lazy.
2. Việc tạo `LazyInitTargetSource` và `ProxyFactory.getProxy()` chưa làm target khởi tạo trong ví dụ; do đó `proxyCreatedTarget=false`.
3. Lần đầu gọi `proxy.render("R-1")`, Spring AOP cần target; BeanFactory tạo `ReportService` và constructor in đúng một lần. Sau đó advice và target method thực thi.
4. Với `R-2`, constructor không in lại, vì `LazyInitTargetSource` tái sử dụng singleton đã khởi tạo.
5. `advice: before/after` xuất hiện ở cả hai lời gọi vì caller gọi qua Spring proxy. Lazy target **không phải** lý do để advice bị bỏ qua.
6. Code quan sát bằng `containsSingleton` thay vì gọi `context.getBean("reportTarget")` trước khi đo. Gọi `getBean` sẽ khởi tạo target và làm sai phép thử tính lazy.

Ví dụ dùng `GenericApplicationContext` để nhìn rõ từng bước tạo proxy. Nếu ứng dụng thật dùng component scan, XML hoặc Spring Boot, cơ chế BeanFactory target và proxy vẫn tương tự nhưng cần kiểm tra thêm auto-proxy configuration và bean wiring thực tế.

### Tại sao target constructor xuất hiện trước `advice: before`?

Đối với đường JDK proxy của ví dụ, Spring có thể cần lấy target từ `TargetSource` trước khi bắt đầu chạy method interceptor chain. Vì vậy câu “advice bao quanh target method” **không** hàm ý advice luôn chạy trước thao tác **khởi tạo object target**. Tối ưu lazy-init nằm ở ranh giới **khi proxy cần một target cho invocation**, không phải cam kết constructor nằm bên trong span của mọi advice.

Nếu target constructor lỗi, exception có thể xảy ra trước khi advice của method thực sự bắt đầu. Vì thế nếu muốn đo chi phí **khởi tạo target**, hãy đo riêng bean creation; nếu chỉ đo thời gian method bên trong around advice, lần đầu có thể không bao gồm toàn bộ chi phí khởi tạo.

## 6. Cấu hình XML với ProxyFactoryBean và LazyInitTargetSource

Spring AOP cũng hỗ trợ cấu hình bean bằng XML. Đây là mẫu wiring theo tài liệu Spring, với một service interface `demo.ReportApi` và implementation `demo.ReportService` có sẵn trong ứng dụng.

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Singleton target thật phải lazy, nếu không context có thể tạo sớm. -->
    <bean id="reportTarget"
          class="demo.ReportService"
          lazy-init="true"/>

    <!-- FactoryBean expose proxy nhưng target vẫn chưa được lấy. -->
    <bean id="reportService"
          class="org.springframework.aop.framework.ProxyFactoryBean">
        <property name="proxyInterfaces"
                  value="demo.ReportApi"/>
        <property name="targetSource">
            <bean class="org.springframework.aop.target.LazyInitTargetSource">
                <property name="targetBeanName" value="reportTarget"/>
                <!-- BeanFactoryAware tự nhận BeanFactory từ Spring. -->
            </bean>
        </property>
    </bean>

</beans>
~~~

Nếu muốn áp dụng advice, tạo một Spring AOP advice bean và khai báo `interceptorNames` hoặc advisor đúng theo `ProxyFactoryBean` contract; ví dụ XML tối thiểu này trình bày **đúng trọng tâm lazy target**. Nó không tự tạo advice chỉ vì có một proxy.

Ví dụ cấu hình thêm một `MethodInterceptor` đã được viết đúng interface:

~~~xml
<bean id="auditInterceptor" class="demo.AuditInterceptor"/>

<bean id="reportService"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="proxyInterfaces" value="demo.ReportApi"/>
    <property name="interceptorNames">
        <list>
            <value>auditInterceptor</value>
        </list>
    </property>
    <property name="targetSource">
        <bean class="org.springframework.aop.target.LazyInitTargetSource">
            <property name="targetBeanName" value="reportTarget"/>
        </bean>
    </property>
</bean>
~~~

Đoạn thứ hai là **biến thể thay cho bean reportService ở cấu hình trước**, không chép đồng thời hai `<bean id="reportService">` vào cùng context. `demo.AuditInterceptor` là class cần có trong dự án, implement `org.aopalliance.intercept.MethodInterceptor`. Với cấu hình đầy đủ, advice được tạo/sẵn sàng còn target được khởi tạo muộn.

**Vì sao `BeanFactoryAware` quan trọng?** Khi `LazyInitTargetSource` là inner bean do Spring tạo, Spring cung cấp owning factory tự động thông qua callback. Nếu người lập trình `new LazyInitTargetSource()` bên ngoài container như ví dụ Java, phải truyền factory bằng `setBeanFactory(...)`. Thiếu factory hoặc ID target sẽ khiến lazy source không thể resolve target.

### Kiểm tra injection bằng proxy chứ không phải raw target

Nếu một eager component muốn dùng report service, inject bean `reportService` là proxy, **không inject bean `reportTarget` trực tiếp**. Đường dependency trực tiếp đến raw target có thể buộc BeanFactory khởi tạo singleton sớm và bỏ qua các advisors của proxy.

Không gọi `context.getBean("reportTarget")` chỉ để “làm nóng” ứng dụng trừ khi chủ đích thay đổi semantics lazy-init; với bean lazy, thao tác đó chính là một trigger khởi tạo.

## 7. Khác nhau giữa LazyInitTargetSource và các dạng @Lazy

| Cơ chế Spring | Đối tượng trì hoãn | Cách khai báo | Khi nào phù hợp |
| --- | --- | --- | --- |
| `lazy-init="true"` / `@Lazy` trên bean definition | Việc Spring pre-instantiate target bean | Bean definition/annotation | Target không cần tạo ở startup |
| `@Lazy` tại injection point | Dependency resolution thông qua lazy-resolution proxy của Spring IoC | Annotation trên injection point | Muốn eager bean inject một tham chiếu lazy đến dependency |
| `LazyInitTargetSource` | Việc **Spring AOP proxy** lấy singleton target từ BeanFactory | `TargetSource` gắn vào `ProxyFactory`/`ProxyFactoryBean` | Cần AOP proxy tồn tại trước, target chỉ được lấy khi có method invocation |
| `PrototypeTargetSource` | Không phải trì hoãn một singleton duy nhất | AOP `TargetSource` khác | Cần target prototype theo invocation |

`@Lazy` tại injection point có thể tạo **lazy-resolution proxy** do container quản lý. Nó không giống mặc định với `LazyInitTargetSource` và không nhất thiết là Spring AOP proxy cùng loại; tránh suy luận mọi proxy liên quan `@Lazy` đều sẽ được `AopUtils.isAopProxy` phân loại như AOP proxy.

Một bean có thể được đánh dấu `@Lazy` nhưng đã khởi tạo vì dependency khác yêu cầu nó; một proxy AOP với `LazyInitTargetSource` cũng có thể đã resolve target khi ai đó trực tiếp gọi method. Tính lazy là hành vi của **quá trình truy cập trong context thực tế**, không phải bảo đảm target sẽ mãi chưa tạo.

## 8. Quan sát đúng thời điểm lazy target được khởi tạo

Khi kiểm thử tính lazy, cần chọn một phép đo **không tự đánh thức target**.

Các phép đo hữu ích:

- `DefaultListableBeanFactory.containsSingleton("reportTarget")`: cho biết singleton instance với tên đó đã được tạo/đăng ký hay chưa, không yêu cầu tạo bean.
- Constructor hoặc `@PostConstruct` gắn một counter/log: xác nhận sự kiện khởi tạo và số lần khởi tạo.
- `ApplicationContext` lifecycle logs khi test integration: giúp biết bean nào bị eager khởi tạo.
- Nếu proxy expose `Advised`, có thể xem `getTargetSource()` để kiểm tra **kiểu source** và tên target bean mà không gọi `getTarget()`.
- Tài liệu cấu hình `lazy-init`, target bean name, class type và dependency graph: chỉ ra mọi đường có thể kích hoạt target.

Phân biệt các thao tác:

~~~java
// Thường dùng để inspect metadata của proxy:
if (proxy instanceof org.springframework.aop.framework.Advised advised) {
    System.out.println(
            advised.getTargetSource().getClass().getSimpleName());
}

// KHÔNG dùng để kiểm tra "chưa khởi tạo":
// advised.getTargetSource().getTarget(); // có thể tạo target ngay.
~~~

Đoạn trên là snippet trong method đã có biến `proxy`. Điều thứ hai **gọi vào chính TargetSource** nên hoàn toàn có thể kích hoạt lazy initialization — sai nếu mục tiêu test là chứng minh target còn chưa được tạo.

`AopUtils.getTargetClass(proxy)` hữu ích để quan sát target class, nhưng tùy loại TargetSource, Spring có thể cần resolve type từ BeanFactory và đôi khi fallback bằng truy xuất bean. Nếu không muốn bất kỳ sự truy cập target nào trong test, khai báo `setTargetClass` tường minh khi phù hợp và quan sát bean instance qua `containsSingleton` hoặc instrumentation của container.

### Test hành vi cần đảm bảo

1. Sau `context.refresh()`: singleton target chưa được tạo nếu không có dependency eager nào yêu cầu.
2. Sau `factory.getProxy()` hoặc `context.getBean("reportService")`: proxy đã sẵn sàng, target vẫn chưa được tạo với cấu hình lazy đúng.
3. Sau method invocation đầu tiên: target được tạo đúng một lần.
4. Sau method invocation thứ hai: cùng singleton target được dùng lại; constructor không chạy lại.
5. Advice dự kiến vẫn chạy cho mỗi invocation qua proxy.
6. Nếu target constructor ném exception: lỗi xuất hiện ở lời gọi đầu tiên cần target; hệ thống không lặng lẽ nuốt lỗi.
7. Nếu có injection trực tiếp `reportTarget`, kết quả test có thể chuyển sang eager — test nên phát hiện thay vì giả định cố định.
8. Khi nhiều thread cùng gọi lần đầu: không xuất hiện nhiều instance singleton target do race; hãy kiểm tra với lifecycle/BeanFactory thật.

## 9. Những trường hợp khiến "lazy" không hoạt động

| Triệu chứng | Nguyên nhân có thể | Cách khắc phục |
| --- | --- | --- |
| Constructor target chạy khi context startup | Bean definition không có `lazy-init=true`; eager dependency yêu cầu trực tiếp target | Đánh dấu lazy target bean, inject proxy vào eager consumer |
| Có proxy rồi nhưng target vẫn được tạo ngay | Ai đó gọi method qua proxy lúc startup; `getTarget()` được dùng để inspect | Tránh invocation/unwrap trước thời điểm cần target |
| Không tìm thấy bean `reportTarget` vào lần gọi đầu | Sai `targetBeanName` hoặc target bean không ở owning BeanFactory | Kiểm tra ID và context chứa bean |
| `LazyInitTargetSource` không resolve được target | Thiếu owning `BeanFactory` khi tạo programmatically | Gọi `setBeanFactory`, hoặc để Spring quản lý target-source bean |
| Advice không chạy sau lazy init | Caller dùng raw target, proxy thiếu advisor, pointcut không match, self-invocation | Kiểm tra `AopUtils`, advisors và đường gọi qua proxy |
| Proxy không tạo được do thiếu target type | BeanFactory không suy ra class, đặc biệt một số `FactoryBean` | Cung cấp `targetClass` tường minh hoặc `proxyInterfaces` hợp lệ |
| Constructor lỗi chỉ xuất hiện khi có request đầu | Lazy đã trì hoãn kiểm tra lỗi | Có health/readiness check phù hợp hoặc chủ động warm-up khi cần |
| Target được tạo nhiều lần ngoài dự kiến | Trộn prototype scope/cách tạo raw `new` với singleton lazy, nhiều bean factories | Dùng singleton bean nhất quán, phân biệt `LazyInitTargetSource` với `PrototypeTargetSource` |
| Startup nhanh nhưng request đầu chậm | Chi phí khởi tạo đã chuyển từ startup sang request đầu | Dự trù latency, prewarm có kiểm soát hoặc chọn eager nếu cần |
| Cố unwrap target từ `Advised` | `getTarget()` kích hoạt lazy và có thể bypass advice nếu gọi raw target | Chỉ inspect metadata, gọi nghiệp vụ qua proxy |
| Thay `@Lazy` ở injection point rồi kỳ vọng hành vi AOP giống hệt | Lazy-resolution proxy của IoC khác AOP TargetSource | Chọn đúng cơ chế cho ranh giới cần trì hoãn |
| Target đã tạo mà output `getTargetClass()` vẫn hợp lệ | Target metadata không chứng minh đã khởi tạo | Kiểm tra singleton instance state riêng |

## 10. Những giới hạn và rủi ro vận hành

### 10.1. First-call latency và khả năng khởi động

Lazy target giúp giảm công việc khởi tạo không cần thiết ở startup, nhưng request đầu tiên sử dụng service có thể chịu chi phí constructor, dependency injection, `@PostConstruct`, truy cập tài nguyên ngoài và logic init. Với target rất nặng, điều này có thể tăng **tail latency**.

Nếu service cần sẵn sàng ngay khi nhận traffic, có thể cân nhắc warm-up có kiểm soát sau khi infrastructure sẵn sàng hoặc để target eager. Khi prewarm bằng lời gọi qua proxy, nhớ nó **cố tình chấm dứt trạng thái lazy** và có thể kích hoạt advice. Không gọi một business method có side effect chỉ để warm-up nếu chưa thiết kế rõ idempotency.

### 10.2. Lỗi cấu hình bị phát hiện muộn

Một target constructor bị thiếu dependency hoặc cấu hình sai có thể không thất bại khi context startup. Lỗi có thể xuất hiện ở lần truy xuất target đầu tiên — tức thời điểm hệ thống đang phục vụ request. Cần test deployment wiring hoặc readiness check cho những target critical. Lazy initialization không thay thế validation cấu hình.

### 10.3. Singleton lifecycle và concurrency

Trong trường hợp đúng hợp đồng, `LazyInitTargetSource` truy cập singleton do Spring BeanFactory quản lý; singleton initialization/concurrency do Spring container xử lý. Điều này không làm **business state của target** tự động thread-safe. Nếu một service giữ mutable field per-request, nhiều requests dùng chung singleton vẫn có race tương tự singleton thường.

Tránh tạo một `LazyInitTargetSource` riêng trỏ vào bean không có singleton semantics rồi dự đoán behavior như thiết kế singleton. TargetSource này được Spring mô tả rõ là lazy singleton target source.

### 10.4. Không loại bỏ overhead của AOP proxy

Dù target singleton được lấy một lần, caller vẫn đi qua Spring proxy/advisor chain khi gọi method. Lazy target là tối ưu **thời điểm khởi tạo target**, không phải tối ưu cache kết quả nghiệp vụ. Nó không làm `@Around` advice chạy một lần hoặc tự bỏ qua logging/transaction/security trên những invocation sau.

### 10.5. Việc khởi tạo target và lời gọi advice có thể khác ranh giới thời gian

Thời điểm lần đầu target được tạo thuộc nhiệm vụ resolve `TargetSource`. Nếu một advice đo thời gian method execution, đừng mặc định nó bao gồm toàn bộ thời gian xây target. Khi phân tích metrics cần phân biệt initialization time, advisor chain time và target method time.

### 10.6. Không áp dụng cho mọi vấn đề lazy trong hệ thống

Nếu cần bean scoped theo HTTP request, tenant hoặc transaction, phải xem semantics target source khác hoặc Spring scopes. Nếu cần object theo từng invocation, `PrototypeTargetSource` khác với `LazyInitTargetSource`. Nếu cần load một service khi thật sự truy cập dependency qua một injection point, `@Lazy` của Spring IoC có thể giải quyết mà không cần cấu hình AOP `TargetSource`.

## 11. Kiểm tra integration test và thông tin chẩn đoán

Một phép kiểm thử thực tế có thể dựa vào Spring context:

~~~java
// Ví dụ snippet trong một Spring integration test:
var beanFactory = context.getBeanFactory();
assertFalse(beanFactory.containsSingleton("reportTarget"));

ReportApi report = context.getBean("reportService", ReportApi.class);
assertTrue(AopUtils.isAopProxy(report));
assertFalse(beanFactory.containsSingleton("reportTarget"));

report.render("R-1");
assertTrue(beanFactory.containsSingleton("reportTarget"));
~~~

Đây là **snippet JUnit minh họa**, giả định `context` là `GenericApplicationContext`, bean `reportService` đã đăng ký qua `ProxyFactoryBean`, có `ReportApi`, imports JUnit `assertFalse/assertTrue` và `org.springframework.aop.support.AopUtils`. Nó không phải source standalone. Không dùng `context.getBean("reportTarget")` trước lần gọi `render` nếu muốn giữ nguyên điều kiện thử nghiệm.

Nếu dùng `Advised` để xác nhận source type:

~~~java
if (report instanceof org.springframework.aop.framework.Advised advised) {
    var source = advised.getTargetSource();
    System.out.println(source instanceof LazyInitTargetSource);
}
~~~

Khi Spring proxy đã cấu hình `opaque` hoặc không expose `Advised`, điều kiện `instanceof` có thể false; không ép cast. Lưu ý `AopUtils.isAopProxy(report)` chỉ xác minh có Spring AOP proxy, không tự đảm bảo source của proxy là `LazyInitTargetSource`.

### Ma trận quan sát đúng

| Mốc | Proxy tồn tại? | Target singleton đã tạo? | Advice chạy? |
| --- | --- | --- | --- |
| Sau đăng ký target definition, trước tạo proxy | Chưa | Chưa | Chưa |
| Sau `getProxy()`, trước `render()` | Rồi | Chưa, nếu đúng cấu hình lazy và không ai lấy target | Chưa |
| Lần đầu `proxy.render("R-1")` | Rồi | Được tạo khi proxy cần target | Có nếu advisor match |
| Lần hai `proxy.render("R-2")` | Rồi | Tái sử dụng singleton | Có nếu advisor match |
| Gọi `getBean("reportTarget")` sớm | Proxy có thể đã/chưa có | Được tạo sớm | Không có advice của proxy nếu gọi trực tiếp target |
| Self-invocation trong target | Proxy có thể là entry point | Target đã được lấy để gọi method ngoài | Method nội bộ không tự đi qua advisor chain riêng |

Ma trận này giả định Spring AOP proxy-based và target singleton. Advice execution còn phụ thuộc pointcut, không phải riêng target-source configuration.

## 12. Quy trình áp dụng vào ứng dụng Spring

1. Xác định bean nào thực sự nặng lúc startup và lý do phải trì hoãn; không lazy tất cả service một cách máy móc.
2. Đăng ký **target bean singleton** dưới ID riêng, đánh dấu `lazy-init=true` hoặc `@Lazy` tương ứng; kiểm tra không có eager dependency trực tiếp buộc nó khởi tạo.
3. Định nghĩa `LazyInitTargetSource` với `targetBeanName` đúng và BeanFactory hợp lệ. Cấp `targetClass` tường minh nếu cần tránh type discovery khởi tạo bean.
4. Gắn source vào `ProxyFactory` hoặc `ProxyFactoryBean`; expose proxy theo **API interface** phù hợp và thêm advisors/interceptors thực sự cần.
5. Để consumers inject **proxy bean**, không truyền raw target ra ngoài hoặc gọi `TargetSource.getTarget()` để chạy nghiệp vụ.
6. Kiểm thử ba mốc: context refresh, proxy created/injected, first method invocation. Đo target creation bằng `containsSingleton` và constructor count.
7. Kiểm chứng second-call reuse, concurrency và first-call latency; có kế hoạch xử lý lỗi tạo target khi workload đến.
8. Nếu service cần ready ngay, cân nhắc eager target hoặc warm-up có kiểm soát thay vì giữ lazy bằng mọi giá.

**Ghi nhớ:** `LazyInitTargetSource` cho Spring AOP proxy một **nguồn truy cập singleton target theo nhu cầu**. Muốn target thật sự tạo muộn trong `ApplicationContext`, target bean cũng phải lazy và các dependency khác không được eager-resolution trực tiếp. Proxy/advice vẫn hoạt động theo Spring AOP proxy semantics; tính lazy chỉ thay đổi thời điểm target được lấy và được tạo.

### Tài liệu Spring chính thức

- [LazyInitTargetSource Javadoc — Spring Framework 6.1](https://www.springframework.org/spring/docs/6.1.3/javadoc-api/org/springframework/aop/target/LazyInitTargetSource.html).
- [AbstractBeanFactoryBasedTargetSource API](https://docs.spring.io/spring-framework/docs/6.1.10/javadoc-api/org/springframework/aop/target/AbstractBeanFactoryBasedTargetSource.html).
- [TargetSource interface — Spring Framework 6.1](https://www.springframework.org/spring/docs/6.1.3/javadoc-api/org/springframework/aop/TargetSource.html).
- [Spring Framework — Using TargetSources](https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html).
- [Spring Framework — Lazy-initialized Beans](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-lazy-init.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-006">ThreadLocalTargetSource và các hệ quả khi dùng luồng tái sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: ThreadLocalTargetSource và các hệ quả khi dùng luồng tái sử dụng

## Mục tiêu

Trong Spring AOP, `ThreadLocalTargetSource` cung cấp **một target object riêng cho mỗi thread** đi qua cùng AOP proxy. Những lần gọi sau trên **chính thread đó** thường lấy lại object đã tạo trước, thay vì tạo target mới. Cơ chế này có thể tiết kiệm khởi tạo target và tránh chia sẻ trạng thái trực tiếp giữa nhiều thread, nhưng nó **không tạo một target riêng cho mỗi request** khi worker thread được tái sử dụng.

Sau bài này, bạn có thể:

- Giải thích cách Spring AOP proxy gọi `TargetSource.getTarget()` và cách `ThreadLocalTargetSource` ghi nhớ target theo thread.
- Cấu hình target **prototype** và một `ThreadLocalTargetSource` trong `ApplicationContext`, sau đó gắn nó vào `ProxyFactory`.
- Phân biệt thread identity với HTTP request, user session, transaction hoặc tác vụ logic.
- Tự chạy ví dụ cho thấy thread pool dùng lại target giữa **hai request giả lập**, khiến trạng thái còn sót lại.
- Hiểu `getInvocationCount()`, `getHitCount()`, `getObjectCount()` và ý nghĩa của việc target tồn tại lâu trong thread pool.
- Nhận biết giới hạn với asynchronous execution, virtual threads và reactive code; chọn cách quản lý state hợp lý hơn theo scope nghiệp vụ.

> Phạm vi: chỉ tập trung vào **Spring AOP TargetSource API**, đặc biệt `org.springframework.aop.target.ThreadLocalTargetSource` và việc proxy/BeanFactory cấp target. Đây không phải bài lý thuyết ThreadLocal tổng quát.

## 1. Tách ba lớp: proxy, target source và target

Khi client giữ Spring AOP proxy, target của từng invocation được chọn dựa trên **thread thực sự đang gọi proxy**. Với đường JDK proxy thông dụng, target được lấy trước chuỗi interceptor; các advisor dùng target đã resolve cho lời gọi đó.

~~~text
Caller trên thread A/B
   |
   v
Spring AOP proxy dùng chung
   |
   v
ThreadLocalTargetSource.getTarget()
   |
   +-- thread A lần đầu --> tạo target A
   +-- thread B lần đầu --> tạo target B
   +-- thread A lần sau --> dùng lại target A
   |
   v
Advisor / MethodInterceptor chain
   |
   v
Business method trên target đã chọn
~~~

TargetSource này giữ target theo thread qua nhiều lời gọi; bước release của proxy không tự reset state sau request.

Cùng một proxy **không đồng nghĩa** cùng một target. `TargetSource` quyết định object nghiệp vụ được sử dụng ở từng invocation. Trong Spring AOP, implementation này dựa trên `ThreadLocal` để gắn một target vào thread hiện tại.

Các thành phần có trách nhiệm khác nhau:

| Thành phần | Vai trò | Có phân biệt thread không? |
| --- | --- | --- |
| `ProxyFactory` / Spring AOP proxy | Expose API và gọi interceptor chain | Một proxy có thể phục vụ nhiều thread |
| `Advisor` / `MethodInterceptor` | Logic advice quanh method | Tùy cấu hình; không tự tạo target theo thread |
| `ThreadLocalTargetSource` | Cấp target, nhớ target theo thread | **Có** |
| Target bean định nghĩa `prototype` | Nguồn tạo object khi thread chưa có target | Tạo instance mới khi được yêu cầu |
| Business target instance | Thực thi method, có thể giữ state | State của instance gắn với thread đã được cấp |

Đừng coi `ThreadLocalTargetSource` là một `Advisor` hay `@Aspect`. Nó nằm ở ranh giới **chọn object đích**, còn pointcut/advice quyết định logic bổ sung quanh lời gọi.

## 2. Cơ chế của ThreadLocalTargetSource

Class `org.springframework.aop.target.ThreadLocalTargetSource` mở rộng `AbstractPrototypeBasedTargetSource`, triển khai `TargetSource` và cung cấp thống kê qua `ThreadLocalTargetSourceStats`.

Mô hình thao tác, diễn giải gần đúng:

~~~java
// MÃ GIẢ: giải thích ý tưởng, không phải mã nguồn Spring đầy đủ
Object getTarget() {
    invocationCount++;
    Object target = targetInThread.get();

    if (target == null) {
        target = beanFactory.getBean(targetBeanName); // prototype
        targetInThread.set(target);
        trackForDestruction(target);
    } else {
        hitCount++;
    }
    return target;
}
~~~

Khi một thread gọi proxy lần đầu, target source chưa có target cho thread đó và tạo **một instance mới** từ prototype bean. Các lần tiếp theo trên **cùng thread**, Spring nhận lại instance đã gắn trước đó. Thread khác có một target riêng.

**Không có bước tự reset trạng thái nghiệp vụ khi kết thúc mỗi invocation.** `releaseTarget(...)` của loại target source này không mang ý nghĩa “xóa state cuối request”. Target được giữ trong thread để tái sử dụng về sau.

### Các phương thức quan trọng

~~~java
import org.springframework.aop.target.ThreadLocalTargetSource;

// Chỉ là minh họa API với biến targetSource đã được cấu hình:
Object target = targetSource.getTarget();
int calls = targetSource.getInvocationCount();
int hits = targetSource.getHitCount();
int objects = targetSource.getObjectCount();
~~~

Đoạn này không chạy độc lập và **không nên gọi `getTarget()` trực tiếp chỉ để xem target** khi bạn đang thử advice. Nó trả object target, còn việc gọi method trên target trực tiếp sẽ **bypass proxy**.

| API | Ý nghĩa |
| --- | --- |
| `getTarget()` | Trả target hiện gắn với thread; tạo target từ bean factory nếu chưa có |
| `getInvocationCount()` | Số lần client invocation khiến target source được hỏi target |
| `getHitCount()` | Số lượt đáp ứng bằng target đã gắn với thread |
| `getObjectCount()` | Số target objects đang được source theo dõi |
| `getStatsMixin()` | Một introduction advisor để expose thống kê qua AOP proxy nếu cấu hình phù hợp |
| `destroy()` | Dọn những target đã được source theo dõi theo hợp đồng lifecycle khi source được hủy |

Thông thường, nếu chỉ có một thread gọi proxy hai lần, lượt đầu tạo target mới, lượt thứ hai được tính là **hit**. Nếu thêm một thread khác gọi lần đầu, source tạo target riêng cho thread thứ hai.

Thống kê là chỉ số hoạt động của target source, **không phải request count** của toàn ứng dụng hoặc số user. Một method có thể được gọi nhiều lần trong cùng request; ngược lại, một request có thể không gọi proxy lần nào.

## 3. Prototype bean là điều kiện thiết kế trọng yếu

Spring cần cách tạo target mới cho mỗi thread chưa có instance. Bởi vậy, target bean được tham chiếu qua `setTargetBeanName(...)` thông thường **phải được khai báo scope prototype**:

~~~java
@Bean("workerTarget")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public WorkerImpl workerTarget() {
    return new WorkerImpl();
}

@Bean
public ThreadLocalTargetSource threadLocalTargetSource() {
    ThreadLocalTargetSource source = new ThreadLocalTargetSource();
    source.setTargetBeanName("workerTarget");
    return source;
}
~~~

Đây là fragment cấu hình cần đặt trong class Spring `@Configuration`, import các annotation/API tương ứng và khai báo `WorkerImpl`. `ThreadLocalTargetSource` là một bean có lifecycle trong context; Spring cung cấp BeanFactory cho nó nhờ interface `BeanFactoryAware`.

**Nếu target bean bị khai báo singleton**, các lần lấy qua BeanFactory có thể trả cùng object, làm hỏng mục tiêu tách instance giữa thread. Không sửa điều này bằng cách đặt scope của chính proxy thành prototype: điểm quyết định là **scope của target bean** mà source lấy ra.

### TargetSource cũng cần gắn vào đúng proxy

~~~java
ProxyFactory factory = new ProxyFactory();
factory.setInterfaces(WorkerApi.class);
factory.setTargetSource(threadLocalTargetSource);
factory.addAdvice(traceInterceptor);
WorkerApi proxy = (WorkerApi) factory.getProxy();
~~~

Đây cũng là fragment, giả định interface `WorkerApi` và interceptor đã được khai báo. `ProxyFactory.setTargetSource(...)` khiến proxy lấy object từ source mỗi khi cần thực thi method. `setInterfaces(...)` bảo đảm JDK dynamic proxy expose một hợp đồng cố định, phù hợp cho nhiều target instances cùng implement interface.

Chỉ khai báo một bean `ThreadLocalTargetSource` trong context **không tự làm mọi service thành per-thread**. Bạn phải cấu hình một proxy hoặc hạ tầng auto-proxy creator sử dụng đúng target source.

## 4. Ví dụ Java 17+ chạy được: request mới vẫn thấy state cũ

Ví dụ sau minh họa **hệ quả khi dùng worker thread tái sử dụng** bằng hai `ExecutorService` đơn luồng có tên khác nhau. Mỗi executor có một thread tồn tại qua nhiều task. Một task mô phỏng request thứ nhất để lại một nhãn trong target; task sau trên **cùng thread** đọc lại target mà không tự reset.

Ví dụ dùng nhãn giả lập `alice` để thể hiện lỗi logic, **không sử dụng thông tin người dùng thật**.

### 4.1. Cấu trúc

~~~text
threadlocal-target-demo/
├── pom.xml
└── src/main/java/example/ThreadLocalTargetDemo.java
~~~

### 4.2. Tệp `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>threadlocal-target-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.19</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>example.ThreadLocalTargetDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.3. Tệp `src/main/java/example/ThreadLocalTargetDemo.java`

~~~java
package example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.target.ThreadLocalTargetSource;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

public class ThreadLocalTargetDemo {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    public interface WorkerApi {
        void setTag(String tag);
        String describe();
    }

    public static final class WorkerImpl implements WorkerApi {
        private final int id = NEXT_ID.incrementAndGet();
        private String tag = "<unset>";

        @Override
        public void setTag(String tag) {
            this.tag = tag;
        }

        @Override
        public String describe() {
            return "id=" + id + " tag=" + tag;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean("workerTarget")
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public WorkerImpl workerTarget() {
            return new WorkerImpl();
        }

        @Bean
        public ThreadLocalTargetSource threadLocalTargetSource() {
            ThreadLocalTargetSource source =
                    new ThreadLocalTargetSource();
            source.setTargetBeanName("workerTarget");
            return source;
        }

        @Bean
        public AtomicInteger adviceCalls() {
            return new AtomicInteger();
        }

        @Bean
        public WorkerApi workerProxy(
                ThreadLocalTargetSource source,
                AtomicInteger adviceCalls) {
            MethodInterceptor countAdvice = invocation -> {
                adviceCalls.incrementAndGet();
                return invocation.proceed();
            };

            ProxyFactory factory = new ProxyFactory();
            factory.setInterfaces(WorkerApi.class);
            factory.setProxyTargetClass(false);
            factory.setTargetSource(source);
            factory.addAdvice(countAdvice);
            return (WorkerApi) factory.getProxy();
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {
            WorkerApi proxy = context.getBean(WorkerApi.class);
            ThreadLocalTargetSource source =
                    context.getBean(ThreadLocalTargetSource.class);
            AtomicInteger adviceCalls =
                    context.getBean(AtomicInteger.class);

            ExecutorService a = Executors.newSingleThreadExecutor(
                    r -> new Thread(r, "worker-A"));
            ExecutorService b = Executors.newSingleThreadExecutor(
                    r -> new Thread(r, "worker-B"));

            try {
                System.out.println("JDK proxy: " +
                        AopUtils.isJdkDynamicProxy(proxy));

                String first = a.submit(() -> {
                    proxy.setTag("alice");
                    return proxy.describe();
                }).get();
                System.out.println("A request 1: " + first);

                // Request logic mới; thread A vẫn là thread cũ.
                String second = a.submit(proxy::describe).get();
                System.out.println("A request 2: " + second);

                // Một thread khác lấy target riêng chưa có nhãn.
                String third = b.submit(proxy::describe).get();
                System.out.println("B request 1: " + third);

                String fourth = a.submit(proxy::describe).get();
                System.out.println("A request 3: " + fourth);

                System.out.println("Objects: " +
                        source.getObjectCount());
                System.out.println("Invocations: " +
                        source.getInvocationCount());
                System.out.println("Hits: " +
                        source.getHitCount());
                System.out.println("Advice calls: " +
                        adviceCalls.get());
            } finally {
                a.shutdown();
                b.shutdown();
            }
        }
    }
}
~~~

### 4.4. Chạy ví dụ

Tại thư mục chứa `pom.xml`, với JDK 17+ và Maven:

~~~bash
mvn -q compile exec:java
~~~

Stdout do chính chương trình tạo ra:

~~~text
JDK proxy: true
A request 1: id=1 tag=alice
A request 2: id=1 tag=alice
B request 1: id=2 tag=<unset>
A request 3: id=1 tag=alice
Objects: 2
Invocations: 5
Hits: 3
Advice calls: 5
~~~

Các dòng Maven/logging khác nếu có là thông tin công cụ, không thuộc stdout từ các lệnh `System.out.println` ở trên. Thứ tự ra kết quả trong ví dụ được cố định bằng `Future.get()` sau từng task và hai executor đơn luồng, vì vậy id và các bộ đếm có thể dự đoán được.

### 4.5. Vì sao có kết quả này?

1. **Proxy là một object chung.** `context.getBean(WorkerApi.class)` trả một JDK dynamic proxy cho mọi task. Không task nào giữ reference target trực tiếp.
2. **Target bean là prototype.** `workerTarget()` chỉ được gọi khi source cần tạo instance mới, không phải một lần cho mỗi `@Bean` hoặc mỗi request.
3. **Thread A lần đầu.** Lời gọi `setTag("alice")` tạo `WorkerImpl(id=1)` rồi gắn instance vào thread A. Lời gọi `describe()` ngay sau đó trên cùng thread là cache hit và đọc `tag=alice`.
4. **Task mới trên thread A.** Executor một luồng tiếp tục sử dụng thread A. `ThreadLocalTargetSource` trả lại `id=1`, nên task request 2 vẫn thấy `tag=alice` dù logic request mới không đặt nhãn đó.
5. **Thread B lần đầu.** Source chưa có target cho thread B, nên tạo `WorkerImpl(id=2)`. Nhãn mặc định vẫn là `<unset>`.
6. **Thread A lần ba.** Instance `id=1` tiếp tục tồn tại; state vẫn là `alice`.
7. **Thống kê.** Có tổng cộng năm method calls qua proxy: `setTag` và `describe` ở task đầu, rồi ba `describe` còn lại. Hai thread tạo hai object; ba lần gọi đã gặp object của thread hiện tại. Advice đếm đúng năm invocations.

Kết quả này là minh chứng trực tiếp: **một request khác không nhất thiết nhận target mới**, bởi vì ranh giới isolation của `ThreadLocalTargetSource` là **thread**, không phải request. Lưu tag hoặc thông tin người dùng vào field của target rồi tin rằng “request khác chắc chắn không thấy” là thiết kế không an toàn khi sử dụng thread pool.

## 5. Luồng tái sử dụng là nguồn rủi ro lớn nhất

Trong một ứng dụng servlet hoặc server xử lý tác vụ qua thread pool, một physical thread thường nhận nhiều request nối tiếp:

~~~text
worker-17:
  10:00:00 Request A -> target T17, set accountId=A
  10:00:01 Request A hoàn tất
  10:00:02 Request B -> target T17, accountId vẫn là A
  10:00:03 Request B hoàn tất

worker-18:
  10:00:02 Request C -> target T18 riêng
~~~

Đây là sơ đồ minh họa theo thời gian, không phải log thực. Việc các request khác nhau được cùng thread xử lý là rất bình thường trong executor và servlet thread pool.

### Những gì bị giữ lại

Một target có thể chứa:

- Giá trị cấu hình tạm thời hoặc biến đếm của invocation trước.
- Cache hoặc buffer tạm do method trước ghi.
- Dữ liệu context mà code vô tình ghi vào instance field, như accountId hoặc tenantId.
- Reference tới object nặng, connection, response, byte array hoặc dữ liệu có thời gian sống ngắn.
- State bị thay đổi khi method ném exception trước lúc tự reset.

Nếu target source tiếp tục giữ target theo thread, những reference đó có thể tồn tại trong khoảng thời gian worker thread còn hoạt động. **Đây vừa là rủi ro trạng thái nghiệp vụ, vừa là rủi ro giữ bộ nhớ/tài nguyên.**

Chú ý: `ThreadLocalTargetSource` giúp hạn chế **đụng độ đồng thời trên cùng một target instance giữa các thread**, nhưng không chứng minh được state đó đúng với từng request. Sự độc lập giữa các thread không phải sự độc lập giữa các công việc chạy lần lượt trên cùng thread.

### Tránh hiểu nhầm “per-thread” là “per-request”

| Khái niệm scope | Đối tượng được gắn với | Có thể trùng qua hai request khác nhau? |
| --- | --- | --- |
| `ThreadLocalTargetSource` | Thread đang gọi proxy | **Có**, khi thread được tái sử dụng |
| Spring `@RequestScope` | HTTP request được Spring web context quản lý | Bình thường request mới có scoped target mới |
| Spring `@SessionScope` | HTTP session | Có, có chủ đích |
| Prototype target source | Mỗi method invocation | Theo từng lời gọi, không mặc định gắn vào request |
| Singleton target source | Một target cố định | Có, qua mọi lời gọi |

Vì vậy, nếu bạn thật sự cần **state riêng cho một HTTP request**, thường nên nghiên cứu `@RequestScope`/scoped proxy hoặc truyền dữ liệu context dưới dạng tham số. `ThreadLocalTargetSource` là lựa chọn riêng cho nhu cầu per-thread thực sự.

## 6. Thống kê hoạt động: hiểu đúng và không làm sai lệch

`ThreadLocalTargetSourceStats` có các giá trị giúp quan sát:

~~~java
System.out.printf("invocations=%d, hits=%d, objects=%d%n",
        source.getInvocationCount(),
        source.getHitCount(),
        source.getObjectCount());
~~~

Đây là fragment cần biến `source` đã được cấu hình trong application context. Không nên gọi `source.getTarget()` ngoài proxy chỉ để kiểm tra vì điều đó có thể **tạo target cho thread hiện tại** và thay đổi bộ đếm.

- **Invocation count:** số lần source được hỏi target, không tự đồng nhất với số request.
- **Hit count:** số lần thread đã có target được gắn nên không cần tạo mới.
- **Object count:** số target instance đang được source theo dõi; trong pool có nhiều thread, con số có thể tăng theo những thread đã từng dùng proxy.
- **Tỷ lệ hit cao:** phản ánh sự tái sử dụng target trên thread, **không tự khẳng định state hợp lệ hoặc ứng dụng an toàn**.

`getStatsMixin()` trả một introduction advisor có thể gắn vào proxy để expose interface `ThreadLocalTargetSourceStats` khi cần quan sát qua proxy. Đây là tiện ích của hạ tầng Spring AOP, không bắt buộc phải dùng; nếu chỉ cần thống kê nội bộ, đọc source từ thành phần vận hành được cấp quyền đã đủ.

Không dùng bộ đếm làm điều kiện duy nhất để giải phóng target. Số object được theo dõi không cho biết chính xác thread nào đã kết thúc, target nào đang xử lý invocation hoặc tài nguyên bên trong target có thể được đóng hay chưa.

## 7. Vòng đời và vấn đề giữ tài nguyên

Javadoc Spring mô tả các target gắn với thread được thu dọn khi bean factory/target source bị hủy, bao gồm gọi cơ chế destroy prototype phù hợp cho target có lifecycle. `ThreadLocalTargetSource` triển khai `DisposableBean`; phương thức `destroy()` quản lý các target nó theo dõi và xóa liên kết ThreadLocal cho thread chạy cleanup.

Điều này **không có nghĩa** target tự hủy sau mỗi invocation hoặc mỗi request. Trên server có pool hoạt động lâu, target có thể tồn tại đến khi application context được đóng.

### Cần hiểu đúng `destroy()`

- `destroy()` liên quan đến **vòng đời target source**, không phải reset per request.
- Spring theo dõi những target được tạo để có thể destroy chúng theo cơ chế dành cho target prototype khi source bị hủy.
- Một cleanup được thực hiện trong một thread **không đồng nghĩa** việc thread-local entry trong mọi worker thread đều được xóa ngay tức thì. Hãy thiết kế shutdown sao cho các task đã hoàn tất và worker threads được dừng/thu hồi phù hợp.
- Không tự gọi `destroy()` giữa lúc proxy vẫn nhận traffic: worker còn sống có thể giữ reference cũ và các invocation mới có thể có hành vi không mong đợi.
- Tương tác với @PreDestroy, `DisposableBean` hoặc custom destroy callback còn phụ thuộc cách prototype được tạo/quản lý và lifecycle mà Spring áp dụng.

Nếu target chứa resource quan trọng như connection, scheduler, thread hoặc object cần close, một `ThreadLocalTargetSource` sống dài có thể làm số tài nguyên tăng theo số thread từng sử dụng proxy. Cần xác định số thread thực tế, giới hạn pool, footprint mỗi target và quy trình shutdown. Những tài nguyên nên quản lý tập trung bằng pool chính thức (connection pool, client pool), thay vì ngẫu nhiên gắn một connection vào mỗi target thread.

## 8. Nên xử lý state bằng cách nào?

Không có một thao tác `ThreadLocalTargetSource.resetForCurrentRequest()` tiêu chuẩn để bạn gọi từ application và khiến Spring tự xóa một target sau request. Một số lựa chọn thiết kế:

### A. Giữ target stateless hoặc luôn ghi đè đầy đủ trạng thái

Nếu cùng target được phép tồn tại qua nhiều invocation, hãy biến business method thành hàm dựa trên **tham số rõ ràng**, không lưu user/request context vào field giữa các lần gọi. Đó thường là hướng xử lý đơn giản nhất.

Ví dụ:

~~~java
public String describe(String accountId) {
    return "account=" + accountId;
}
~~~

Đây là fragment minh họa logic trong target, không phải chương trình độc lập. Mỗi invocation dùng tham số của chính nó; không có field `accountId` phải được xóa.

Nếu target bắt buộc có state tạm, cần xác định contract reset đầy đủ trong mọi đường đi success/failure và tính đúng đắn khi method lồng nhau. **Chỉ gọi reset trên những lời gọi đã “biết là xong” không đủ** nếu exception hay timeout khiến cleanup bị bỏ qua.

### B. Dùng scope thực sự khớp ranh giới nghiệp vụ

Với HTTP request trong Spring web application, cân nhắc `@RequestScope` hoặc scoped proxy nếu cần state sống trong một request. Với dữ liệu phải xuyên request của cùng user, `@SessionScope` có semantics khác và cần đánh giá thread safety/concurrency cho session.

Với một operation độc lập trong message consumer hoặc batch processor, truyền context object tường minh thường rõ ràng hơn việc dùng thread identity. Spring bean scopes không tự suy ra ranh giới cho mọi loại background job, nên cần một chủ sở hữu lifecycle cụ thể.

### C. Dùng TargetSource khác nếu semantics khác

- `PrototypeTargetSource` tạo target **mới cho mỗi method invocation**; chi phí tạo/wiring target cao hơn nhưng tránh giữ state xuyên invocation theo thread.
- Pooling target source dùng pool có lifecycle mượn/trả target; không phải sự bảo đảm state tự reset nếu application không chuẩn hóa.
- `HotSwappableTargetSource` thay target hiện tại cho proxy; nó giải bài toán **chuyển implementation**, không phải cấp target riêng cho mỗi request.
- Singleton target source dùng chung object; phù hợp khi target an toàn khi dùng chung và không cần state theo thread.

Chọn theo thời gian sống của state thực sự cần; tránh chọn `ThreadLocalTargetSource` chỉ vì tên có “ThreadLocal”.

## 9. Async, CompletableFuture, reactive và virtual threads

### 9.1. Lời gọi chuyển sang thread khác

Giả sử một method đang chạy trên worker A rồi dùng `CompletableFuture.supplyAsync(...)` hoặc một executor khác để gọi cùng proxy. Phần công việc chuyển sang worker B có thể nhận **target B**.

~~~text
Thread A: proxy.stage1() -> target A
            |
            +-- dispatch async task
                       |
                       v
Thread B: proxy.stage2() -> target B
~~~

Vì vậy, không nên kỳ vọng target theo thread đại diện cho **một chuỗi tác vụ logic** kéo dài qua nhiều thread. Trạng thái ở target A **không tự được truyền** sang target B. Mặt khác, worker B cũng có thể được reuse và giữ state từ tác vụ trước của một request hoàn toàn khác.

### 9.2. Reactive execution

Trong Reactor/WebFlux, một luồng xử lý có thể chuyển giữa event-loop hoặc worker threads; `ThreadLocalTargetSource` không hiểu subscriber context và không đảm bảo scope per-subscriber. Hãy dùng Reactor Context hoặc cơ chế propagation được framework hỗ trợ cho dữ liệu context nếu thật sự cần, thay vì tin rằng “mỗi request luôn ở một thread”.

### 9.3. Virtual threads trên Java mới

Virtual thread thường được tạo riêng cho một task và có vòng đời ngắn hơn thread pool truyền thống; tuy nhiên **đừng gắn yêu cầu đúng đắn về request scope vào giả định scheduler/runtime**. `ThreadLocalTargetSource` vẫn liên kết target với **thread gọi proxy** chứ không phải request ID. Nếu một virtual thread xử lý nhiều operation logic, target vẫn có thể sống xuyên các operation đó; việc tạo nhiều virtual threads cũng có thể tạo nhiều target objects trong lúc chúng hoạt động.

Khi sử dụng virtual threads, cần kiểm tra chi phí tạo prototype, đặc biệt target sở hữu resource nặng; per-thread caching không phải chiến lược thay thế connection pool.

## 10. Cấu hình XML với ProxyFactoryBean

Spring AOP cũng có thể sử dụng `ThreadLocalTargetSource` qua `ProxyFactoryBean`:

~~~xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="workerTarget"
          class="example.WorkerImpl"
          scope="prototype"/>

    <bean id="threadlocalTargetSource"
          class="org.springframework.aop.target.ThreadLocalTargetSource">
        <property name="targetBeanName" value="workerTarget"/>
    </bean>

    <bean id="workerService"
          class="org.springframework.aop.framework.ProxyFactoryBean">
        <property name="targetSource" ref="threadlocalTargetSource"/>
        <property name="proxyInterfaces" value="example.WorkerApi"/>
    </bean>
</beans>
~~~

Đây là **XML minh họa**, không phải cấu hình chạy độc lập với Java demo trước vì `example.WorkerApi` và `example.WorkerImpl` ở demo là nested types. Để chạy XML này, cần khai báo interface và class top-level tương ứng trong package `example`.

Những điểm chính:

1. `workerTarget` có scope **prototype**.
2. `threadlocalTargetSource` là bean cấp target theo thread, tham chiếu **tên** target bean bằng `targetBeanName`.
3. `workerService` là Spring AOP proxy mà client nên inject; gọi target trực tiếp sẽ bỏ qua advice của proxy.
4. Có thể thêm advice/advisor qua cấu hình proxy; `TargetSource` chọn object, còn advice vẫn ở interceptor chain.

Sự tồn tại của Spring AOP proxy **không làm reset state sau mỗi method**. Dùng XML hoặc Java configuration không làm thay đổi semantics của `ThreadLocalTargetSource`.

## 11. Những nhầm lẫn thường gặp

**“ThreadLocalTargetSource tạo một target mới cho từng request.”** Sai. Nó tạo target **theo thread**, nên nhiều request nối tiếp trên cùng worker dùng chung target.

**“ThreadLocalTargetSource là một bean prototype.”** Không nhất thiết. Target source thường là một bean dùng chung để gắn vào proxy; chính **target bean mà nó tạo** mới phải prototype.

**“Target prototype nghĩa là method nào cũng tạo object mới.”** Không khi dùng `ThreadLocalTargetSource`. Prototype được tạo **khi thread chưa có target**; những invocation tiếp theo trên thread dùng instance đã gắn.

**“Có target riêng theo thread thì không thể lộ state.”** Sai. Nếu thread pool reuse worker, target có thể giữ dữ liệu request trước.

**“Spring dọn ThreadLocal sau mỗi request.”** Sai với cơ chế `ThreadLocalTargetSource`. Source có semantics cache theo thread; việc cleanup lifecycle thường diễn ra khi source bị hủy. Không đồng nhất điều này với cleanup của những ThreadLocal utility khác do Spring web framework quản lý.

**“releaseTarget giải phóng instance ngay sau mỗi lời gọi.”** Không với loại source này. Đừng dựa vào `releaseTarget` để tự xóa trạng thái per request.

**“Target được gắn thread A sẽ được truyền sang async thread B.”** Sai. Target được chọn theo thread thực sự gọi proxy tại thời điểm invocation.

**“ThreadLocalTargetSource tự tạo advisor hoặc thay thế pointcut.”** Sai. Nó chọn target instance; Spring AOP vẫn cần proxy/advice/pointcut phù hợp.

**“Số objects bằng số request.”** Sai. Trong thread pool ổn định, object count có thể gần số worker đã dùng proxy, nhỏ hơn rất nhiều so với số request.

**“Chỉ cần try/finally ở caller là target sẽ biến mất.”** Không. Caller có thể reset dữ liệu nó kiểm soát nhưng target source vẫn cache instance theo thread; cần design rõ resource ownership.

**“Có virtual threads thì luôn tốt hơn.”** Không thể khẳng định. Cần kiểm tra số target được tạo, chi phí prototype và thời gian sống của business state; virtual thread không đổi ngữ nghĩa TargetSource.

## 12. Checklist trước khi áp dụng

1. Xác định **vòng đời business state** cần là method, request, session, transaction hay thread.
2. Chỉ chọn `ThreadLocalTargetSource` nếu **per-thread** thực sự đúng hợp đồng.
3. Khai báo target bean scope **prototype**; xác nhận nguồn target lấy bean mới khi thread chưa có.
4. Tạo một Spring AOP proxy rõ ràng, khai báo business interface/type ổn định.
5. Không lưu dữ liệu request nhạy cảm trong field còn tồn tại xuyên lần gọi; ưu tiên truyền tham số.
6. Kiểm thử bằng thread pool tái sử dụng: nhiều logical requests trên **cùng** thread và nhiều thread khác nhau.
7. Gọi qua proxy để xác nhận advice/pointcut; không kiểm tra chỉ bằng target trực tiếp.
8. Đọc `getInvocationCount()`, `getHitCount()`, `getObjectCount()` để đánh giá tần suất tạo/tái dùng target.
9. Đánh giá shutdown và tài nguyên của các prototype targets; không đóng target trong lúc còn invocation.
10. Với async/reactive/virtual-thread workload, xác minh thread boundaries; đừng suy ra request boundaries.
11. Nếu thật sự cần per-request, dùng cơ chế scope/context tương ứng rồi đo và test theo request ID.
12. Kiểm tra integration test với concurrency và exception, bởi state leak có thể chỉ xuất hiện sau nhiều lượt reuse thread.

## 13. Câu hỏi tự kiểm tra

1. Với một pool 10 worker threads và 1.000 request, `ThreadLocalTargetSource` có nhất thiết tạo 1.000 target không? Vì sao?
2. Tại sao task thứ hai trong ví dụ vẫn thấy `tag=alice` dù nó mô phỏng một request khác?
3. Vì sao instance `id=2` trên worker B không bị ảnh hưởng trực tiếp bởi field của instance `id=1`?
4. `getHitCount()` đo điều gì? Vì sao nó không phải tỷ lệ cache hit theo HTTP request?
5. Khác biệt giữa scope prototype của target bean và cache theo thread trong target source là gì?
6. Vì sao việc gọi thẳng method trên object nhận từ `getTarget()` không kiểm chứng được Spring advice?
7. Vì sao `@RequestScope` phù hợp hơn nếu state chỉ nên tồn tại trong một HTTP request?
8. Một lời gọi `CompletableFuture` chuyển sang thread khác có tự nhìn thấy target cũ của caller không?
9. Khi application context đóng, có thể kết luận mọi worker thread đang chạy đã xóa reference thread-local ngay lập tức không?
10. Dùng virtual threads giải quyết được những rủi ro nào, và những rủi ro nào vẫn tồn tại?

## Tài liệu Spring chính thức

- [Using TargetSource Implementations — Spring Framework 6.2](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/targetsource.html) — nguyên lý các TargetSource và cảnh báo ThreadLocal.
- [ThreadLocalTargetSource Javadoc — Spring Framework 6.2](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/target/ThreadLocalTargetSource.html) — getTarget, destroy, thống kê và sự ràng buộc theo thread.
- [TargetSource Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/TargetSource.html) — getTarget, releaseTarget và hợp đồng cấp target.
- [AbstractPrototypeBasedTargetSource Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/target/AbstractPrototypeBasedTargetSource.html) — tạo prototype target và lifecycle.
- [Creating AOP Proxies Programmatically](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html) — ProxyFactory và advisor/advice.
- [Bean Scopes](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html) — prototype, request, session và scoped proxies.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-007">Giải phóng đối tượng đích và trách nhiệm quản lý tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Giải phóng đối tượng đích và trách nhiệm quản lý tài nguyên với Spring AOP TargetSource

## 1. Mục tiêu và phạm vi

Trong Spring AOP, proxy có thể gọi nhiều **đối tượng đích (target)** khác nhau qua cùng một interface. `TargetSource` chịu trách nhiệm cung cấp target để thực hiện lời gọi phương thức. Khi target được **mượn**, **tạo mới theo lời gọi** hoặc **gắn với thread**, vấn đề quan trọng tiếp theo là: *sau lời gọi, ai phải trả lại hoặc hủy target, ai chịu trách nhiệm đóng tài nguyên mà target sử dụng, và tài nguyên còn sống bao lâu?*

Bài học tập trung vào hợp đồng `org.springframework.aop.TargetSource` trong **Spring Framework**, cơ chế proxy gọi `releaseTarget(...)` và vòng đời thực tế của các implementation như `PrototypeTargetSource`, `CommonsPool2TargetSource`, `ThreadLocalTargetSource`. Những kỹ thuật quản lý tài nguyên ở Java chỉ xuất hiện trong ví dụ để làm rõ cách Spring AOP thực thi hợp đồng này.

Các khái niệm cần phân biệt ngay:

- **Proxy:** đối tượng client giữ và gọi. Proxy có thể sống lâu trong `ApplicationContext` dù target thay đổi sau từng invocation.
- **Target:** đối tượng chứa method thực sự sẽ được gọi, do `TargetSource.getTarget()` cung cấp.
- **Release target:** kết thúc quyền sử dụng target của một invocation. Hành động này có thể **trả vào pool**, **hủy prototype** hoặc **không làm gì**, tùy strategy.
- **Destroy/close:** thực sự giải phóng tài nguyên như kết nối, file hoặc bộ nhớ native. Không phải mọi lần release đều đồng nghĩa destroy/close.
- **Ownership:** thành phần nào đã tạo/mượn resource phải có cơ chế rõ ràng để trả/hủy resource theo đúng vòng đời. Không thể mặc định Spring tự đóng mọi thứ mà một method trả về.

## 2. Hợp đồng cốt lõi của `TargetSource`

Các phương thức quan trọng (chữ ký diễn giải tương thích với Spring Framework 6.2; phần comment được rút gọn):

~~~java
public interface TargetSource {
    Class<?> getTargetClass();          // Có thể null ở một số strategy
    default boolean isStatic() { return false; }
    Object getTarget() throws Exception; // Có thể null nếu không có target thật
    default void releaseTarget(Object target) throws Exception {
        // Mặc định: không làm gì
    }
}
~~~

`TargetSource` còn kế thừa `TargetClassAware`; giao diện thực tế được khai báo trong thư viện Spring, đoạn trên chỉ tóm lược các method để đọc, **không phải mã nguồn cần định nghĩa lại trong dự án**.

### `getTarget()`: nhận quyền dùng một target

- Proxy hỏi `TargetSource` để lấy target cho lời gọi phương thức.
- Với dynamic source, mỗi lần có thể trả một object khác nhau. Với singleton/static source, có thể là cùng object.
- Nếu thất bại trong bước lấy target, Spring không thể release một target chưa được nhận. Chính implementation phải tự xử lý mọi tài nguyên đã tạo *một phần* trước khi ném lỗi.
- `getTarget()` được phép trả `null` theo API, nhưng các lời gọi method instance thường cần target khác null để thực thi.

### `isStatic()`: cam kết có cần vòng lặp mượn–trả không

- `true` có nghĩa mọi lần hỏi đều cùng một target; Spring AOP có thể cache nó và **không gọi `releaseTarget` trên đường đi của static target**.
- `false` là default; proxy xem target là động và xử lý lifecycle mượn–trả.
- Không được dùng `isStatic() = true` chỉ vì muốn tối ưu khi thực tế target thay đổi. Khi ấy cache có thể làm sai hành vi, việc release bị bỏ qua và tài nguyên bị giữ.

### `releaseTarget(Object target)`: trả lại đúng target đã lấy

- Spring chuyển **chính object nhận từ `getTarget()`** cho `releaseTarget` sau khi proxy xử lý xong lời gọi.
- Hàm mặc định **rỗng**, nên một custom source tạo tài nguyên mới mà không override sẽ dễ bị rò rỉ.
- Với source động, proxy thường gọi release trong nhánh tương đương `finally`, kể cả target method ném exception.
- Điều kiện quan trọng: proxy đã nhận target khác `null` qua `getTarget()` và source không static. Trong đường JDK proxy thông dụng, target được acquire trước interceptor chain; ngay cả khi `@Around` trả sớm không gọi `proceed()`, proxy vẫn release target đã lấy. Nếu `getTarget()` ném lỗi trước khi cấp target hoặc client gọi raw object ngoài proxy thì không có lượt release tương ứng.

**Sai lầm dễ gặp:** “Có `releaseTarget` nghĩa là target luôn bị `close()` sau method”. Đây là kết luận sai. Tên gọi chỉ nói đến việc *kết thúc lượt sử dụng*: chính implementation quyết định giữ lại, trả vào pool hay hủy.

## 3. Luồng Spring AOP proxy và thời điểm giải phóng

~~~text
Client gọi AOP proxy
        |
        v
TargetSource.getTarget()  ---> acquire target cho invocation
        |
        v
Xác định chuỗi advisor / interceptor theo method, target class
        |
        +--> advice tiếp tục proceed() -> target.execute(...)
        |                                  |
        +--> advice có thể short-circuit <--+
        |
        v
finally: releaseTarget(target) [đã lấy, target != null, !isStatic()]
        |
        v
return/throw về client
~~~

Với `JdkDynamicAopProxy` của Spring Framework 6.2, Spring lấy target **trước khi chọn/chạy interceptor chain**; callback advice chạy trước thân business method không có nghĩa chạy trước thao tác acquire. Điều này cũng có nghĩa `@Around` có thể ngăn business method chạy nhưng vẫn phát sinh một lần acquire/release. Đây là đường đi của JDK proxy thông thường; một số lời gọi quản trị proxy (chẳng hạn xử lý `equals`/`hashCode`) có nhánh riêng nên không được dùng để đếm business invocations.

Spring thực hiện cleanup trong `finally` khi đã nhận target động khác `null`, cả khi business method hoặc advice ném exception. Nếu acquire thất bại trước lúc có target, nguồn phải tự cleanup mọi resource khởi tạo dở dang. Gọi trực tiếp raw target bên ngoài proxy không tự đi vào vòng lifecycle này.

### Hai thời điểm khác nhau

**Kết thúc invocation**: lúc proxy không còn cần target để hoàn thành lời gọi đồng bộ. Với pool, đây thường là lúc trả object vào pool để invocation khác dùng lại.

**Kết thúc vòng đời target/container**: lúc target thực sự cần bị tiêu hủy. Singleton có thể sống tới khi context đóng; pool có thể giữ nhiều target để tái sử dụng cho tới khi pool loại bỏ hoặc đóng; target gắn thread có thể sống rất lâu dù invocation đã hoàn thành.

Thực tế phải trả lời hai câu hỏi riêng: “Khi nào target được **release**?” và “Khi nào resource bên trong nó thực sự được **dispose**?”

## 4. Ai chịu trách nhiệm: so sánh các TargetSource của Spring

| Strategy | `getTarget()` | `releaseTarget()` và trách nhiệm sau lời gọi | Thời điểm thực sự giải phóng |
| --- | --- | --- | --- |
| `SingletonTargetSource` / static target | Trả cùng target | Không cần release theo invocation; Spring có thể cache | Chủ sở hữu singleton / container quản lý hủy bean nếu bean thuộc container |
| `PrototypeTargetSource` | Lấy một prototype bean mới mỗi invocation | Hủy prototype độc lập ở bước release | Ngay khi lượt dùng kết thúc, qua lifecycle destruction của target do strategy này thực thi |
| `CommonsPool2TargetSource` | Mượn một object từ pool | **Trả object vào pool**, thường không hủy ngay | Theo chính sách eviction/invalidation hoặc khi pool đóng |
| `ThreadLocalTargetSource` | Lấy target riêng cho mỗi thread, tái dùng trong thread đó | Không có nghĩa hủy target sau mỗi method | `destroy()` của strategy khi BeanFactory bị destroy; các target gắn thread có thể sống đến thời điểm đó |
| `LazyInitTargetSource` | Tạo/lấy một singleton khi cần | Không hủy theo từng method | Vòng đời singleton/BeanFactory |
| `HotSwappableTargetSource` | Đưa target hiện tại; `swap(...)` có thể thay | Không tự hiểu là phải đóng target cũ sau mỗi invocation | Ứng dụng quyết định xử lý target cũ sau swap, bảo đảm không có invocation còn dùng nó |
| Custom dynamic `TargetSource` | Hoàn toàn theo implementation | **Phải tự định nghĩa rõ việc return/close/recycle** | Theo policy do tác giả implement |

**Prototype không đồng nghĩa với cơ chế tự hủy mặc định của Spring IoC.** Một bean khai báo scope `prototype` rồi `getBean()` trực tiếp thường **không tự nhận destruction callback** khi container shutdown. `PrototypeTargetSource` bổ sung hành vi hủy đối tượng sau mỗi lần mượn. Vì thế, khi thay bằng custom source tự gọi `beanFactory.getBean(...)`, tác giả cũng phải chịu trách nhiệm phá hủy instance đúng cách, chẳng hạn thông qua `ConfigurableBeanFactory.destroyBean(beanName, bean)`/API tương ứng khi thật sự sở hữu lifecycle; callback destroy cần được cấu hình đúng để giải phóng resource.

**Pool còn sống khác object vừa được release.** Với `CommonsPool2TargetSource`, một lần release hợp lệ chủ yếu đưa object về trạng thái có thể mượn lại. Gọi `close()` tài nguyên của object ngay trong lời gọi nghiệp vụ có thể phá pool. Pool cần lifecycle riêng để đóng lúc shutdown; configuration của pool quyết định giới hạn, timeout, test/eviction và xử lý object hỏng.

**Thread-bound không đồng nghĩa request-scoped.** `ThreadLocalTargetSource` là theo thread, không phải theo HTTP request. Trong thread pool, cùng một thread có thể xử lý nhiều request; target có thể giữ state từ request trước. Không tự dùng ThreadLocal strategy để giữ thông tin nhạy cảm theo request. Spring ghi rõ cleanup của thread-bound objects xảy ra lúc BeanFactory destruction, và nhiều object có thể tồn tại tới lúc ứng dụng dừng.

## 5. Ví dụ chạy được: custom dynamic TargetSource đóng target sau mỗi invocation

Ví dụ sau cố tình quản lý một target dùng một lần để nhìn thấy chính xác thứ tự acquire → invoke → release trong Spring AOP. Có nhánh method thành công và nhánh ném exception để chứng minh cleanup vẫn chạy.

Cấu trúc thư mục:

~~~text
target-source-lifecycle/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/TargetLifecycleDemo.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'target-source-lifecycle'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation platform('org.springframework:spring-framework-bom:6.2.19')
    implementation 'org.springframework:spring-aop'
}

application {
    mainClass = 'demo.TargetLifecycleDemo'
}
~~~

`src/main/java/demo/TargetLifecycleDemo.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;

public class TargetLifecycleDemo {

    public interface Task {
        String execute(String input);
    }

    // Target có tài nguyên giả định; close() biểu diễn cleanup.
    public static final class OneUseTask implements Task, AutoCloseable {
        private final int id;
        private boolean closed;

        public OneUseTask(int id) {
            this.id = id;
        }

        @Override
        public String execute(String input) {
            if (closed) {
                throw new IllegalStateException("target already released");
            }
            System.out.println("invoke #" + id + " (" + input + ")");
            if ("fail".equals(input)) {
                throw new IllegalStateException("simulated failure");
            }
            return "done " + input;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                System.out.println("release #" + id);
            }
        }
    }

    // Dynamic source: mỗi lần proxy cần target, tạo một target mới.
    public static final class OneUseTargetSource implements TargetSource {
        private final AtomicInteger nextId = new AtomicInteger();

        @Override
        public Class<?> getTargetClass() {
            return OneUseTask.class;
        }

        @Override
        public boolean isStatic() {
            return false;
        }

        @Override
        public Object getTarget() {
            int id = nextId.incrementAndGet();
            System.out.println("acquire #" + id);
            return new OneUseTask(id);
        }

        @Override
        public void releaseTarget(Object target) {
            ((OneUseTask) target).close();
        }
    }

    public static void main(String[] args) {
        ProxyFactory factory = new ProxyFactory();
        factory.setInterfaces(Task.class); // Chỉ expose interface qua JDK proxy
        factory.setTargetSource(new OneUseTargetSource());

        Task task = (Task) factory.getProxy();

        System.out.println("result: " + task.execute("ok"));

        try {
            task.execute("fail");
        } catch (IllegalStateException ex) {
            System.out.println("caught: " + ex.getMessage());
        }
    }
}
~~~

Chạy với JDK 17+ và Gradle phù hợp (trong thư mục dự án): `gradle run`. **stdout của ứng dụng** (không tính output build của Gradle):

~~~text
acquire #1
invoke #1 (ok)
release #1
result: done ok
acquire #2
invoke #2 (fail)
release #2
caught: simulated failure
~~~

**Đọc kết quả:**

1. `getProxy()` tạo một **proxy ổn định**; không khởi tạo target dùng một lần trong ví dụ. Khi client gọi `execute`, Spring AOP yêu cầu `TargetSource` cung cấp target.
2. `OneUseTargetSource.isStatic()` trả `false` để Spring biết phải xử lý vòng đời target cho mỗi lời gọi; `getTargetClass()` cung cấp kiểu target để Spring xây proxy.
3. Sau lời gọi thành công, `releaseTarget` đóng đúng `OneUseTask` vừa mượn. Vì vậy dòng `release #1` xuất hiện **trước** `result: done ok` của caller.
4. Với exception, `release #2` vẫn xuất hiện trước `caught...`. Điều này thể hiện cơ chế cleanup trên đường đi của Spring AOP proxy; không cần viết `finally` ở mỗi caller.
5. `close()` trong ví dụ chỉ in log và chuyển cờ. Trong code thật, hãy thay bằng API đóng tài nguyên mà target **thực sự sở hữu**, đồng thời làm cleanup idempotent khi thiết kế yêu cầu.

Ví dụ sử dụng API `ProxyFactory` của **Spring AOP** và không cần chạy `ApplicationContext`. Vì đây là target mới cho từng invocation, mô hình này chỉ thích hợp với lời gọi **đồng bộ, không trả target hoặc resource phụ thuộc target cho caller**. Phần xử lý không đồng bộ được bàn ở mục 8.

### Vì sao không tự gọi `releaseTarget()` từ caller?

Khi client gọi qua Spring AOP proxy, proxy chịu trách nhiệm invoke và release theo contract. Nếu client còn chủ động gọi `source.releaseTarget(target)` trên cùng target, có nguy cơ **release hai lần** (đặc biệt nguy hiểm với pool). Chỉ code nào tự gọi `getTarget()` *bên ngoài proxy* mới phải tự ghép acquisition/release trong `try/finally` theo contract của source.

## 6. Tích hợp với BeanFactory: PrototypeTargetSource và destruction callback

Trong hệ thống thật, target thường cần dependency injection, `@PostConstruct` và destruction callback thay vì `new` thủ công. Khi dùng `PrototypeTargetSource`, hãy khai báo target là **prototype**, còn proxy làm đối tượng được ứng dụng sử dụng thường xuyên.

Ví dụ wiring minh họa (không độc lập chạy vì lược bớt khai báo `WorkerApi` và `Worker`):

~~~java
@Configuration
class TargetSourceConfiguration {
    @Bean("workerTarget")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    Worker workerTarget() {
        return new Worker();
    }

    @Bean
    PrototypeTargetSource workerTargetSource() {
        var source = new PrototypeTargetSource();
        source.setTargetBeanName("workerTarget");
        return source;
    }

    @Bean
    WorkerApi workerProxy(PrototypeTargetSource source) {
        var factory = new ProxyFactory();
        factory.setInterfaces(WorkerApi.class);
        factory.setTargetSource(source);
        return (WorkerApi) factory.getProxy();
    }
}
~~~

Các import chính cho đoạn minh họa:

~~~java
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.PrototypeTargetSource;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
~~~

Spring inject `BeanFactory` vào `PrototypeTargetSource` được quản lý như bean vì class implement `BeanFactoryAware`. Khi proxy invoke, source lấy một instance từ BeanFactory rồi tiêu hủy instance đó tại release. Hủy instance chỉ thực hiện **các destruction callbacks mà container biết**: chẳng hạn implementation `DisposableBean`, `@PreDestroy` hoặc cấu hình destroy method phù hợp. Nếu mở file hoặc kết nối mà không đăng ký cơ chế đóng, không thể trông chờ destruction callback tự đoán ra tài nguyên cần giải phóng.

Nếu muốn tự viết một `TargetSource` dựa trên `BeanFactory`, cần đảm bảo lần `getTarget()` nào tạo một prototype thì lần `releaseTarget()` tương ứng hủy **đúng** instance, tránh dùng mỗi lần một `getBean()` mới để “hủy” (sẽ tạo nhầm object), tránh hủy bean singleton được chia sẻ và tránh hủy object trong pool khi chỉ cần trả lại.

## 7. Tài nguyên cần được quản lý theo đúng tầng sở hữu

Giả sử một target sử dụng database connection. Có ba mô hình khác nhau:

| Mô hình | Ai sở hữu connection? | Hành vi release đúng |
| --- | --- | --- |
| Target tạo riêng một connection trong constructor và giữ độc quyền | Target/target owner | Khi target thực sự bị hủy, phải đóng connection theo lifecycle của target |
| Target mượn connection từ pool cho từng method | Method hoặc tầng DAO/Spring transaction infrastructure | Trả connection đúng pool theo transaction/connection contract; **không đóng cả target** chỉ vì method vừa chạy |
| Spring transaction quản lý connection trong invocation | Transaction manager/infrastructure của Spring | Để hạ tầng transaction quản lý transaction và giải phóng connection; `TargetSource.releaseTarget()` không thay thế transaction cleanup |

**Điểm then chốt:** `releaseTarget()` là hook lifecycle của *đối tượng đích*. Nó không tự hiểu để giải phóng mọi thứ *bên trong* method, không thay thế `@Transactional`, `DataSourceUtils` hay transaction synchronization. Cùng một AOP invocation có thể có cả transaction advice và dynamic target release, nhưng chúng quản lý các tài nguyên ở những tầng khác nhau.

Với pool target: đối tượng target có thể giữ state/cache hoặc kết nối lâu dài để tái sử dụng. Trong `releaseTarget()`, thường cần trả target vào pool **ở trạng thái sạch**; phá kết nối ngay tại đây có thể khiến target lần sau bị lỗi. Việc đóng tài nguyên thuộc chính sách đóng/evict object của pool.

Với singleton target: gọi `releaseTarget` mỗi lần để `close()` một kết nối độc quyền của singleton sẽ phá các lời gọi sau. Một singleton bean thuộc context nên dùng destruction callback đúng lúc context đóng.

## 8. Những tình huống release dễ bị hiểu sai

### 8.1. Method trả về `CompletableFuture`, `Mono`, `Flux` hoặc stream lười

Một dynamic `TargetSource` có thể release target ngay sau khi method **trả về đối tượng future/publisher**, trong khi công việc bất đồng bộ **chưa hoàn tất hoặc chưa bắt đầu**:

~~~java
// Minh họa rủi ro; đây không phải chương trình độc lập.
CompletableFuture<String> startWork() {
    return CompletableFuture.supplyAsync(() -> useResourceOwnedByThisTarget());
}
~~~

Nếu proxy release target và đóng resource lúc `startWork()` trả về, phần thực thi ở background có thể truy cập resource đã đóng. Tương tự, một `Flux` lazy chạy sau thời điểm method tạo ra nó không thể mặc định dựa vào target còn sống.

**Hướng an toàn:** quản lý resource theo lifetime thật của future/subscription (ví dụ cơ chế quản lý resource theo luồng reactive phù hợp), tránh cho target ngắn hạn sở hữu thứ mà work bất đồng bộ còn cần. Đừng kéo dài thời gian mượn target vô hạn bằng cách “quên release”: với pool, điều đó gây cạn pool.

### 8.2. Method trả về resource, target hoặc đối tượng phụ thuộc target

~~~java
// Minh họa sai ở một source có policy close target sau mỗi lời gọi:
InputStream openStream() {
    return streamOwnedByTarget;
}
~~~

Spring AOP chỉ biết đây là một giá trị trả về. Nếu resource thuộc target và `releaseTarget` đóng target trước khi caller đọc stream, stream sẽ trở thành invalid. Cần thiết kế API có ownership minh bạch, chuyển quyền sở hữu resource nếu được phép hoặc cung cấp thao tác sử dụng resource **trọn trong invocation**.

### 8.3. Method target ném exception; release cũng ném exception

Spring thực hiện release trong đường cleanup, nhưng `releaseTarget` bản thân được phép `throws Exception`. Nếu cleanup thất bại, exception ở bước release có thể **che khuất exception nghiệp vụ ban đầu**, tùy đường proxy và ngữ cảnh. Implementation production nên ghi nhận/quan sát lỗi cleanup và xây chính sách xử lý rõ ràng; không được im lặng bỏ qua resource leak. Kiểm thử cần bao gồm cả lỗi acquire, lỗi target và lỗi release.

### 8.4. Around advice kết thúc sớm

`@Around` hoặc `MethodInterceptor` có thể không gọi `proceed()`, vì thế **business method không chạy**. Tuy nhiên, đường JDK proxy chuẩn vẫn có thể **đã lấy target trước khi advice chạy**: nếu source động trả về target khác `null`, proxy phải release target đó dù advice short-circuit.

Khi test, đếm ba sự kiện riêng: số lần `getTarget()` thành công, số lần business method thật sự chạy và số lần `releaseTarget()` hoàn thành. Không dùng số lần business method chạy làm đại diện cho số lần target đã được mượn.

### 8.5. Self-invocation và raw target reference

Lời gọi `this.anotherMethod()` bên trong target không quay lại Spring AOP proxy. Spring không tạo một lượt get/release target độc lập cho self-invocation đó. Nếu ứng dụng giữ trực tiếp raw target rồi gọi ngoài proxy, Spring cũng không tự giải phóng theo lượt gọi ấy. Tránh phát tán raw target và không dùng self-invocation để suy ra các mốc acquire/release.

## 9. Thiết kế custom TargetSource để không rò rỉ tài nguyên

Khi các implementation dựng sẵn không đáp ứng được yêu cầu, hãy ghi rõ một policy lifecycle có thể kiểm thử:

1. **Loại target:** mọi lần cùng object (`isStatic=true`) hay có thể thay (`false`)?
2. **Ai tạo và giữ:** bean factory, pool, thread-bound store hoặc chính custom source?
3. **Điều kiện acquire thành công:** từ thời điểm nào target được xem là đã được mượn; bước khởi tạo dở dang sẽ cleanup ra sao?
4. **Hành động release:** trả về pool, destroy prototype, hoặc no-op; có được gọi lặp không; có thể ném exception không?
5. **Shutdown:** ai đóng pool/cache/target còn giữ; nếu source là Spring bean, cần `DisposableBean`, `@PreDestroy` hay destroy callback rõ ràng?
6. **Đồng thời:** một target có thể đang được nhiều invocation giữ không; khi shutdown/swap phải chờ, từ chối request mới hay đợi in-flight calls?
7. **Quan sát:** bộ đếm acquired/released/active, lỗi acquire/release, tuổi thọ target, idle pool và timeout.

Có thể đặt bất biến cơ bản cho một custom one-use source (khi không có race và mỗi acquire thành công bắt đầu một lượt sở hữu):

~~~text
acquired_successfully = released_successfully + currently_in_use
~~~

Bất biến này không áp dụng máy móc cho một static singleton source (release không theo lượt), và với pool cần hiểu **release** là *return to pool*, không phải destroy. Nếu cleanup thất bại, nên ghi nhận riêng `release_failures` thay vì làm như target đã được trả thành công.

### Các bài kiểm thử quan trọng

| Kịch bản | Kết quả kỳ vọng |
| --- | --- |
| Method return bình thường | Dynamic target đã mượn được release đúng một lần |
| Method ném runtime exception | Vẫn thực hiện release; caller thấy lỗi theo policy |
| `getTarget()` không lấy được target | Không release một reference chưa nhận; resource tạo dở được implementation cleanup |
| Nhiều thread gọi cùng proxy | Không trả nhầm target, không release trùng; pool giữ giới hạn |
| Pool checkout → return → checkout | Có thể tái sử dụng object; không đóng tài nguyên sớm |
| Context shutdown | Pool/target cache nhận destruction callback đúng một lần |
| Aspect trả kết quả sớm không `proceed()` | Kiểm tra acquire/release vẫn cân bằng dù target method có thể không chạy |
| Method trả future/publisher/resource | Thời gian sống của resource đủ dài so với consumer thực tế |
| Hoán đổi target khi request đang chạy | Không hủy old target khi còn lời gọi đang sử dụng |

Với integration test, hãy chạy qua **proxy thực**, không chỉ gọi thẳng implementation của `TargetSource`. Nếu bean được tạo qua `ApplicationContext`, kiểm thử cả bước đóng context và kiểm tra destruction callback.

## 10. Lỗi phổ biến và nguyên tắc chọn giải pháp

- **Lẫn lộn release với destroy:** với `CommonsPool2TargetSource`, release là trả pool; với `PrototypeTargetSource`, release là hủy prototype. Xác định strategy trước khi đóng tài nguyên.
- **Dùng dynamic target nhưng bỏ trống `releaseTarget`:** hàm mặc định no-op; nếu `getTarget` luôn tạo mới một resource cần dispose, đây là nguồn leak.
- **Đánh dấu `isStatic=true` cho nguồn thực chất dynamic:** Spring có thể cache target và bỏ qua release. Chỉ dùng static khi luôn trả cùng instance.
- **Tin rằng scope prototype tự gọi destroy:** thông thường `BeanFactory` không tự gọi destruction callbacks cho prototype đã cấp; dùng lifecycle manager đúng mục đích, chẳng hạn `PrototypeTargetSource`.
- **Để `ThreadLocalTargetSource` giữ request context:** object theo thread có thể đi qua nhiều request trong thread pool và tồn tại đến khi context kết thúc.
- **Trả về lazy resource từ target ngắn hạn:** proxy release theo thời gian method return, sớm hơn lifetime thật của consumer.
- **Tự đóng target trong `@After`/`@AfterReturning` advice:** dễ trùng hoặc sai thời điểm so với cơ chế release của `TargetSource`. Hãy đặt lifecycle ở đúng tầng source.
- **Đóng raw target sau `HotSwappableTargetSource.swap(...)` ngay lập tức:** ứng dụng cần cân nhắc các invocation còn đang dùng old target; `swap` chỉ giúp thay instance hiện tại.
- **Cho rằng dùng Spring AOP nghĩa là tự động quản lý JDBC/network/file:** mỗi loại resource có hợp đồng ownership riêng; Spring AOP quản lý target theo `TargetSource`, không tự quét để đóng tất cả.

**Kết luận:** Hợp đồng đúng của Spring AOP là “proxy nhận target từ `getTarget()` và trả nó theo chính sách `releaseTarget()` khi kết thúc lượt dùng dynamic target”. *Trả target* khác với *hủy target*, và *hủy target* khác với *đóng mọi resource mà một method tạo ra*. Với singleton, prototype, pooling, ThreadLocal và hot swap, các mốc cleanup khác nhau. Thiết kế lifecycle chính xác phải xuất phát từ **ai sở hữu target và resource, lifetime của lời gọi, và cơ chế release/destroy cụ thể**.

## Tài liệu chính thức để tra cứu

- [Spring Framework 6.2 — `TargetSource` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/TargetSource.html).
- [Spring Framework 6.2 — Using `TargetSource` Implementations](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/targetsource.html).
- [Spring Framework 6.2 — `PrototypeTargetSource`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/target/PrototypeTargetSource.html).
- [Spring Framework 6.2 — `CommonsPool2TargetSource`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/target/CommonsPool2TargetSource.html).
- [Spring Framework 6.2 — `ThreadLocalTargetSource`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/target/ThreadLocalTargetSource.html).
- [Spring Framework — Bean Scopes và prototype destruction](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-scopes.html).

Khi nâng Spring Framework, tra Javadoc của đúng minor/patch đang chạy và kiểm thử lại thời điểm acquire/release và destruction callbacks; tránh dựa vào behavior suy đoán từ version khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-008">Phân biệt TargetSource với scoped proxy của bean</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt TargetSource với scoped proxy của bean trong Spring

## 1. Kết luận cốt lõi: hai khái niệm liên quan nhưng khác cấp độ

Trong **Spring AOP**, `TargetSource` là **giao kèo cấp thấp để một AOP proxy lấy đối tượng target** trước khi chuyển lời gọi phương thức tới nó. Ngược lại, **scoped proxy** là **một proxy do Spring IoC tạo ra nhằm cho phép mã giữ một tham chiếu ổn định tới bean có scope thay đổi** (request, session, prototype hoặc custom scope).

Điểm dễ nhầm: đây **không phải hai cách triển khai loại trừ nhau**. Scoped proxy cũng là một loại Spring AOP proxy, và nó **dùng một `TargetSource` bên dưới**. Trong triển khai scoped proxy tiêu chuẩn, `ScopedProxyFactoryBean` sử dụng `SimpleBeanTargetSource`: mỗi lúc proxy cần thực thi phương thức nghiệp vụ, source hỏi `BeanFactory` để lấy bean thực thuộc scope đang hoạt động.

Cần phân biệt ba câu hỏi:

1. **Proxy nằm ở đâu?** Client hoặc singleton service có thể giữ *một proxy* trong thời gian dài.
2. **Ai quyết định target cho một lời gọi?** `TargetSource` của proxy.
3. **Tại sao target thay đổi hay được tái sử dụng?** Với scoped proxy, **scope của bean trong Spring container** (ví dụ một target cho mỗi HTTP request hoặc session) quyết định vòng đời thực tế.

Vì vậy: **`TargetSource` là cơ chế chọn target; scoped proxy là một ứng dụng của cơ chế ấy để đáp ứng quy tắc scope của Spring bean.**

## 2. Bảng so sánh trực tiếp

| Tiêu chí | `TargetSource` | Scoped proxy của bean |
| --- | --- | --- |
| Bản chất | Interface `org.springframework.aop.TargetSource` và các implementation | AOP proxy do Spring cấu hình thông qua `proxyMode`, `@RequestScope`, `@SessionScope`, XML `<aop:scoped-proxy/>`… |
| Mục đích | Trả lời “target của lượt gọi này là object nào?” và xử lý việc trả target nếu cần | Giúp một nơi dùng lâu dài, như singleton, gọi đúng target theo scope hiện hành |
| Ai tạo/chọn | `ProxyFactory`, `ProxyFactoryBean`, auto-proxy infrastructure hoặc cấu hình thấp tầng | Container thường sinh tự động theo bean definition và metadata scope |
| Target lấy từ đâu | Có thể từ object cố định, factory, pool, hot swap, prototype, bean factory… | Thường tra cứu bean thực theo tên từ `BeanFactory`, để scope hiện tại quyết định |
| Gắn với request/session? | Không bắt buộc | Khi bean được khai báo scope request/session thì có |
| API đại diện | `getTargetClass()`, `isStatic()`, `getTarget()`, `releaseTarget()` | `@Scope(proxyMode=...)`, `@RequestScope`, `@SessionScope`, `ScopedProxyFactoryBean` |
| Quyết định hủy target | Tùy implementation; một số source phải release hoặc destroy | Chủ yếu do **vòng đời bean scope** quản lý; scoped proxy không tự hủy target ngay sau mỗi lời gọi |
| Điểm client nhìn thấy | Thường không biết source đang chọn gì; client giữ proxy | Client tiêm hoặc nhận một dependency nhìn giống bean bình thường nhưng thực chất là proxy |
| Trường hợp sử dụng | Hot swap, pooling, lookup, target cố định/động… | Tiêm bean có scope ngắn hơn vào bean có scope dài hơn mà vẫn dùng đúng instance |

**Không đồng nhất** “proxy là singleton” với “target là singleton”, và cũng không đồng nhất “`getTarget()` được gọi mỗi lần” với “tạo một object mới mỗi lần”. Trong scoped proxy, `getTarget()` có thể được gọi nhiều lần nhưng các lời gọi trong **cùng request** vẫn nhận cùng request-scoped target.

## 3. `TargetSource`: hợp đồng phía dưới Spring AOP proxy

Trong Spring AOP, mỗi proxy có thể dùng `TargetSource` để chọn target cho lời gọi phương thức. Giao diện chính:

```java
// Hình dạng đơn giản hóa của API Spring (bỏ các chú thích nullability).
public interface TargetSource extends TargetClassAware {
    Class<?> getTargetClass();
    boolean isStatic();
    Object getTarget() throws Exception;
    void releaseTarget(Object target) throws Exception;
}
```

- `getTargetClass()`: kiểu target được công bố để Spring hiểu metadata phương thức và khả năng proxy.
- `isStatic()`: `true` nếu source luôn giữ cùng target; `false` nếu không cam kết bất biến. Giá trị `false` **không buộc** mỗi lần phải là một object mới.
- `getTarget()`: lấy target hiện tại, thường ở thời điểm proxy sắp xử lý lời gọi; có thể lỗi nếu lookup không thành công.
- `releaseTarget(target)`: hoàn trả/hủy target nếu implementation cần. Với source không static, AOP proxy thông thường gọi release trong `finally` sau lượt gọi.

Các implementation cho thấy interface này rộng hơn phạm vi scoped proxy:

- `SingletonTargetSource`: một object cố định.
- `HotSwappableTargetSource`: thay object phía sau cùng proxy bằng `swap(...)`.
- `PrototypeTargetSource`: tạo một prototype cho mỗi lần gọi và hủy đối tượng khi release.
- `CommonsPool2TargetSource`: mượn/trả object từ pool.
- `SimpleBeanTargetSource`: gọi `BeanFactory.getBean(targetBeanName)` cho lần lookup; chính `BeanFactory` áp dụng scope của bean.

**Một implementation có thể dùng `BeanFactory` nhưng chưa chắc là “scoped proxy” theo cơ chế cấu hình bean scope của Spring.** Ngược lại, scoped proxy tiêu chuẩn phụ thuộc vào một `TargetSource` cụ thể để làm việc.

## 4. Scoped proxy hình thành từ bean definition như thế nào?

Hãy xét một `@RequestScope` bean được inject vào singleton service:

```java
// Đoạn minh họa trong ứng dụng Spring Web/Spring Boot; không phải file chạy độc lập.
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
class RequestIdentity {
    private final String id = java.util.UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}

@Service
class AuditService {
    private final RequestIdentity identity;

    AuditService(RequestIdentity identity) {
        this.identity = identity;
    }

    String currentRequestId() {
        return identity.id();
    }
}
```

`AuditService` mặc định là singleton: constructor được gọi một lần, trường `identity` giữ một **proxy**. `@RequestScope` mặc định dùng class-based scoped proxy. Khi `currentRequestId()` được gọi trong request HTTP có scope hoạt động, proxy mới tra cứu `RequestIdentity` thực qua container.

Hình dung quá trình Spring xây dựng bean:

```text
Bean gốc do lập trình viên khai báo: requestIdentity, scope=request
          |
          | Spring xử lý metadata scoped proxy
          v
Bean tên requestIdentity: một proxy để inject vào AuditService
          |
          +-- ScopedProxyFactoryBean
                   |
                   +-- SimpleBeanTargetSource
                           targetBeanName = "scopedTarget.requestIdentity"
                                       |
                                       v
                           BeanFactory.getBean(...)
                                       |
                                       v
                           request scope -> RequestIdentity thật
```

Tên đích `scopedTarget.<beanName>` là quy ước của cơ chế scoped proxy tiêu chuẩn trong Spring. Khi inspect bean definitions, có thể thấy bean proxy được công bố bằng tên cũ và target gốc được đăng ký dưới tên nội bộ này.

Cần chú ý:

- `@RequestScope` không có nghĩa **một object cho mỗi lời gọi phương thức**. Trong cùng một request, nhiều lời gọi thông thường nhận **cùng một request-scoped bean**.
- Hai request khác nhau nhận hai target khác nhau (nếu scope được kích hoạt và binding đúng).
- Một request-scoped bean chưa tồn tại có thể được tạo **khi lookup lần đầu**, không nhất thiết lúc singleton khởi tạo.
- Nếu lời gọi thực hiện ngoài ngữ cảnh request còn hiệu lực, `BeanFactory` không resolve được request target và có thể ném `ScopeNotActiveException` (hoặc ngoại lệ liên quan đến ngữ cảnh scope).
- Khi request kết thúc, lifecycle của scope xử lý những callback hủy đã đăng ký. Không được giả định proxy gọi `destroy()` trên target sau **mỗi** lời gọi phương thức.

### Proxy ổn định và target theo scope

```text
AuditService singleton
        |
        +--> RequestIdentity proxy [một tham chiếu ổn định]
                 |
                 +--> Request A: target A
                 |      id() -> A
                 |      id() -> A
                 |
                 +--> Request B: target B
                        id() -> B
                        id() -> B
```

Hai lần `id()` trong request A vẫn trả ID của cùng target A. Proxy đó có thể phục vụ tiếp request B với target B mà `AuditService` không cần được tạo lại.

## 5. Đường đi của lời gọi: quan hệ giữa scoped proxy và TargetSource

Trong trường hợp scoped proxy tiêu chuẩn, trình tự khái quát là:

1. Client gọi một phương thức trên **scoped proxy**.
2. Spring AOP proxy gọi `SimpleBeanTargetSource.getTarget()`.
3. `SimpleBeanTargetSource` gọi `BeanFactory.getBean("scopedTarget.<tên-bean>")`.
4. BeanFactory tra cứu `Scope` tương ứng và cấp đúng target theo ngữ cảnh hiện tại.
5. Spring AOP thực thi chuỗi interceptor liên quan (nếu có) và chuyển lời gọi tới target được cấp.
6. Proxy hoàn tất lời gọi. `releaseTarget()` theo source này không có nghĩa hủy scoped target; **scope** tiếp tục giữ object tới khi vòng đời scope kết thúc.

Nếu bean target có advice như `@Transactional`, hệ thống có thể gồm nhiều lớp proxy hoặc các advisor tùy cấu hình. Cần phân biệt **đích nào được resolve theo scope** với **lớp nào chịu trách nhiệm advice**; không nên kết luận luôn có đúng một proxy hay luôn cùng thứ tự interceptor trong mọi cấu hình.

Đặc biệt, lời gọi nội bộ kiểu `this.anotherMethod()` **không đi qua scoped proxy một lần nữa**. Nó gọi trực tiếp phương thức trên object target đã được chọn cho lượt hiện tại. Đây là giới hạn theo cơ chế proxy của Spring AOP.

## 6. Ví dụ chạy được: cùng một scoped proxy, nhưng target được lấy mới

Đây là một ví dụ Java 17+ **chạy được không cần web server**. Dùng `prototype` để quan sát rõ mỗi lượt gọi lấy target mới từ `BeanFactory`, sau đó dùng `HotSwappableTargetSource` để chứng minh interface `TargetSource` còn có mục đích khác hẳn scope.

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>scoped-targetsource-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.8</version>
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
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/ScopedVsTargetSourceDemo.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

public class ScopedVsTargetSourceDemo {

    public interface Worker {
        String name();
    }

    public static class RealWorker implements Worker {
        private final String name;

        public RealWorker(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    public static class Client {
        private final Worker worker;

        public Client(Worker worker) {
            this.worker = worker;
        }

        public void call() {
            System.out.println("scoped call: " + worker.name());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        private final AtomicInteger ids = new AtomicInteger();

        @Bean
        @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE,
               proxyMode = ScopedProxyMode.INTERFACES)
        public Worker worker() {
            return new RealWorker("target-" + ids.incrementAndGet());
        }

        @Bean
        public Client client(Worker worker) {
            return new Client(worker);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            Client client = context.getBean(Client.class);
            Worker scopedProxy = context.getBean(Worker.class);

            System.out.println("proxy reused: "
                    + (scopedProxy == context.getBean(Worker.class)));
            System.out.println("target source: "
                    + ((Advised) scopedProxy).getTargetSource()
                            .getClass().getSimpleName());

            client.call();
            client.call();

            HotSwappableTargetSource switchable =
                    new HotSwappableTargetSource(new RealWorker("blue"));
            ProxyFactory factory = new ProxyFactory();
            factory.setInterfaces(Worker.class);
            factory.setTargetSource(switchable);
            Worker otherProxy = (Worker) factory.getProxy();

            System.out.println("hot swap before: " + otherProxy.name());
            switchable.swap(new RealWorker("green"));
            System.out.println("hot swap after: " + otherProxy.name());
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q -Dexec.mainClass=ScopedVsTargetSourceDemo compile exec:java
```

Stdout dự kiến:

```text
proxy reused: true
target source: SimpleBeanTargetSource
scoped call: target-1
scoped call: target-2
hot swap before: blue
hot swap after: green
```

**Giải thích kết quả:**

- `Worker` trong singleton `Client` là **cùng proxy** được công bố trong context. Vì `proxyMode=INTERFACES`, Spring tạo proxy theo `Worker` interface.
- Source của scoped proxy là `SimpleBeanTargetSource`. Mỗi lần gọi `worker.name()`, BeanFactory resolve `scopedTarget.worker`, vốn có scope `prototype`, nên tạo `target-1` rồi `target-2`.
- Nếu thay `prototype` bằng `request` trong ứng dụng web có request scope hoạt động, hai lời gọi trong cùng request thường dùng **cùng target**.
- `HotSwappableTargetSource` vẫn là `TargetSource` dù ví dụ đó không có scoped bean. Nó giữ target `blue`, rồi đổi thành `green` qua `swap()`; proxy không thay đổi.
- Trong phiên bản prototype **scoped proxy**, BeanFactory tạo bean mới ở mỗi lần lookup nhưng không tự xử lý destruction callback của từng prototype sau lời gọi. Đây là một khác biệt quan trọng so với `PrototypeTargetSource`.

## 7. Scoped proxy so với PrototypeTargetSource: khác biệt về lifecycle

Hai cấu hình đều có thể cho cảm giác “mỗi lời gọi có một target mới”, nhưng vòng đời không giống nhau.

**Cách A — scoped proxy của bean prototype:**

```java
// Trong một @Configuration; minh họa cấu hình, không phải chương trình riêng.
@Bean
@Scope(value = "prototype", proxyMode = ScopedProxyMode.INTERFACES)
Worker worker() {
    return new RealWorker("prototype");
}
```

Cơ chế chính: `ScopedProxyFactoryBean` + `SimpleBeanTargetSource` + BeanFactory lookup prototype. Mỗi lời gọi phương thức nghiệp vụ qua proxy thông thường yêu cầu một prototype mới. Sau lời gọi, `SimpleBeanTargetSource` không chủ động hủy target. Theo quy tắc của prototype bean, container mặc định **không quản lý destruction callback** sau khi bean được trao cho bên dùng.

**Cách B — proxy cấu hình rõ `PrototypeTargetSource`:**

```java
// Pseudocode API Spring: bean "rawWorker" phải được đăng ký scope prototype.
PrototypeTargetSource source = new PrototypeTargetSource();
source.setTargetBeanName("rawWorker");
source.setBeanFactory(beanFactory);

ProxyFactory proxyFactory = new ProxyFactory();
proxyFactory.setInterfaces(Worker.class);
proxyFactory.setTargetSource(source);
Worker proxy = (Worker) proxyFactory.getProxy();
```

Ở cách B, `PrototypeTargetSource` chủ động lấy một instance mới cho lượt gọi và `releaseTarget()` tiến hành **destroy prototype instance** sau khi proxy dùng xong. Vì vậy, nếu target có cleanup quan trọng, không nên cho rằng cách A và B tương đương.

**Lưu ý:** Đoạn B chỉ minh họa cấu hình API, cần `beanFactory` hợp lệ, bean `rawWorker` thuộc scope prototype và các import Spring cần thiết. Một số thiết lập Spring tự động dựng proxy bằng factory/creator thay vì gọi trực tiếp như đoạn minh họa.

## 8. Request/session scoped proxy khác TargetSource động tùy ý ở đâu?

### Request scope

Một request-scoped bean được chia sẻ bởi các lần resolve **trong cùng request HTTP**. Scope cho phép container liên hệ việc lấy bean với request hiện hành. Bản thân `TargetSource` không tự biết request nào đang xử lý; nó chỉ hỏi BeanFactory, nơi scope lookup diễn ra.

### Session scope

Một session-scoped bean có thể tồn tại qua nhiều request **thuộc cùng HTTP session**. Một scoped proxy được singleton giữ vẫn resolve cùng target của session đó và một target khác khi context chuyển sang session khác. Nó không tạo bean mới mỗi HTTP request nếu session vẫn vậy.

### Prototype scope

Mỗi lookup qua `BeanFactory.getBean()` cho một prototype tạo instance mới. Với scoped proxy, lượt gọi proxy dẫn đến lookup tương ứng; nhưng không có vòng quản lý destruction giống request/session. Nếu client gọi hai phương thức để “khởi tạo trạng thái rồi sử dụng” trên cùng tham chiếu proxy prototype, hai lời gọi đó có thể đi tới **hai target khác nhau**. Vì vậy nên tránh ghép trạng thái qua nhiều lời gọi trên một prototype scoped proxy.

### Custom scope

Với custom `Scope`, quan hệ “cùng scope context -> cùng target” do implementation `Scope.get(name, ObjectFactory)` định nghĩa. Scoped proxy vẫn dùng lookup qua BeanFactory. Để sử dụng đúng, context scope phải được binding/active tương ứng; `SimpleBeanTargetSource` không tự triển khai quy tắc custom scope.

## 9. Cấu hình scoped proxy trong Spring

### Annotation-based

```java
// Tùy thuộc module Spring Web của ứng dụng.
@Component
@RequestScope // Tương đương request scope + class-based scoped proxy theo mặc định.
public class RequestCart {
    public int itemCount() { return 0; }
}
```

```java
// Dùng interface-based proxy khi dependency tiêm qua interface.
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.INTERFACES)
public class SessionPreferences implements Preferences {
    @Override
    public String theme() { return "light"; }
}
```

`@Scope(value = "request", proxyMode = ScopedProxyMode.NO)` chỉ khai báo scope, không tạo scoped proxy. Khi một singleton inject trực tiếp loại bean ngắn đời này, có thể không resolve được bean lúc khởi tạo, hoặc không có semantics mong muốn; hãy cân nhắc `ObjectProvider<T>` hay proxy.

### XML

```xml
<bean id="cart" class="example.RequestCart" scope="request">
    <aop:scoped-proxy/>
</bean>
```

Nếu khai báo `proxy-target-class="false"` trong `<aop:scoped-proxy>`, Spring dùng JDK interface proxy và các điểm inject phải tương thích với interface. Mặc định XML scoped proxy là class-based proxy.

**Chọn JDK hay class-based** chỉ ảnh hưởng loại proxy/các method có thể intercept; **không thay** cách `BeanFactory` áp dụng scope. Class-based proxy không chặn được lời gọi private method; final method không thể override để intercept. Với interface-based proxy, chỉ method được công bố qua interface có thể gọi qua proxy đó.

## 10. Cách chẩn đoán trong ứng dụng Spring

Khi gặp hiện tượng bean “đổi instance” hoặc “không lấy đúng request bean”, kiểm tra theo thứ tự:

1. **Bean được inject có phải proxy?** Trong môi trường debug, dùng `AopUtils.isAopProxy(bean)` từ Spring AOP. Một object target nhận được trực tiếp không có semantics của proxy.
2. **Proxy dùng TargetSource nào?** Với proxy implement `Advised`, có thể xem `((Advised) bean).getTargetSource()`. Scoped proxy tiêu chuẩn thường là `SimpleBeanTargetSource`, nhưng cấu hình tùy chỉnh hoặc nhiều lớp proxy có thể làm bạn quan sát một lớp proxy khác.
3. **Bean definition đích có tên và scope gì?** Kiểm tra bean gốc `scopedTarget.<name>` và scope `request`, `session`, `prototype`, v.v. Tên này là quy ước của scoped-proxy infrastructure.
4. **Scope hiện hành có active không?** Với HTTP request/session scope, lời gọi ngoài web request (startup, scheduler, một luồng bất đồng bộ chưa được truyền context) không nhất thiết có scope hoạt động.
5. **Lời gọi có đi qua proxy không?** Self-invocation từ chính target sẽ bỏ qua proxy. Method private/final cũng có giới hạn đặc thù theo kiểu proxy.
6. **Kỳ vọng một hay nhiều target?** Với request/session hãy kiểm tra identity qua các lần gọi trong **cùng** và **khác** request/session; với prototype kiểm tra mỗi lượt gọi; với custom source kiểm tra contract của implementation.
7. **Vòng đời và cleanup ở đâu?** Request/session thông thường do scope quản lý; prototype scoped proxy không tự destroy mỗi target sau mỗi method; `PrototypeTargetSource` có đường `releaseTarget()` hủy prototype.
8. **Có nhiều proxy lồng nhau không?** Các cơ chế `@Transactional`, async hoặc advisor tùy cấu hình có thể thay cách bạn nhìn thấy proxy. Khi debug, phân tách lớp scoped lookup khỏi advice.

Để xác nhận riêng scoped proxy trong dev/test mà không nhầm với target trực tiếp:

```java
Object bean = applicationContext.getBean("worker"); // ví dụ worker scoped-proxy
System.out.println(org.springframework.aop.support.AopUtils.isAopProxy(bean));
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    System.out.println(advised.getTargetSource().getClass().getName());
}
```

Đoạn này là **mã tích hợp trong ứng dụng có `applicationContext` đã được khai báo**, không phải chương trình độc lập. Tránh gọi `getTarget()` thủ công chỉ để debug trong production: với một số source, thao tác ấy tạo/mượn target và đòi hỏi `releaseTarget()` tương ứng.

## 11. Các nhầm lẫn thường gặp

| Nhầm lẫn | Thực tế trong Spring |
| --- | --- |
| “Scoped proxy và TargetSource là hai cơ chế độc lập” | Scoped proxy tiêu chuẩn sử dụng `TargetSource` bên dưới để resolve bean thuộc scope |
| “Có `TargetSource` thì tự động có request scope” | Không. TargetSource có thể chỉ bọc một singleton hoặc hỗ trợ hot swap/pool |
| “`SimpleBeanTargetSource` luôn tạo object mới” | Không. Nó lookup qua BeanFactory; singleton, request, session, prototype có quy tắc tái sử dụng khác nhau |
| “Request proxy là một target mới cho mỗi lời gọi” | Không. Trong cùng request, nhiều lời gọi có thể dùng cùng một target |
| “Proxy được inject vào singleton thì target đã chốt ngay khi inject” | Không với scoped proxy; target được resolve khi gọi trong scope tương ứng |
| “`prototype` với scoped proxy luôn được tự hủy sau method” | Không. Phân biệt cơ chế này với `PrototypeTargetSource` |
| “`@Scope("request")` luôn tự bật scoped proxy” | Không. `@Scope` mặc định thường không tạo proxy; `@RequestScope` mặc định có proxy |
| “Có thể gọi request bean từ bất kỳ thread nào” | Không. Ngữ cảnh scope phải active; chuyển thread có thể làm mất binding của request |
| “`proxyMode=TARGET_CLASS` intercept cả private/final methods” | Không. Class-based proxy có những giới hạn theo khả năng override của Java |
| “Tất cả advice đều ở scoped proxy” | Không bắt buộc; cần xem cấu hình advisor/proxy cụ thể, có thể có lớp proxy khác |

## 12. Khi nào chọn giải pháp nào?

- **Cần tiêm request/session bean vào singleton:** ưu tiên **scoped proxy** (`@RequestScope`, `@SessionScope`, `@Scope(proxyMode=...)`) hoặc inject `ObjectProvider<T>` nếu muốn tự quyết định thời điểm lookup.
- **Cần prototype instance mới ở mỗi business method call:** scoped proxy cho prototype có thể phù hợp nếu không cần cleanup theo lần gọi; tránh lưu trạng thái giữa hai lời gọi. Nếu cần lifecycle acquire/release rõ ràng, nghiên cứu `PrototypeTargetSource` hoặc cơ chế quản lý tài nguyên phù hợp.
- **Cần hoán đổi implementation sau runtime:** `HotSwappableTargetSource` phục vụ việc chuyển target trong cùng proxy; scoped proxy request/session không tự giải quyết hot swapping.
- **Cần pooling:** dùng `TargetSource` hỗ trợ pool khi thật sự có lý do, thay vì gán một bean scope request/session chỉ để tiết kiệm chi phí tạo.
- **Không cần target thay đổi theo scope hay lượt gọi:** singleton thông thường và Spring advice đã đủ; scoped proxy/custom `TargetSource` sẽ làm tăng độ phức tạp không cần thiết.

**Câu hỏi tự kiểm tra:** Singleton `AuditService` giữ một `@RequestScope` proxy, trong request X gọi `identity.id()` hai lần và trong request Y gọi lần nữa. Có bao nhiêu proxy và bao nhiêu target? Thông thường **một proxy dùng chung, một target cho X và một target cho Y**. Kết quả phụ thuộc vào request scope đang hoạt động đúng và mỗi request thực sự là một context khác nhau.

## 13. Tài liệu Spring chính thức

- Spring Framework — Bean Scopes, Scoped Beans as Dependencies: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework — Using TargetSource Implementations: https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html
- Spring API — `SimpleBeanTargetSource`: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/target/SimpleBeanTargetSource.html
- Spring API — `PrototypeTargetSource`: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/target/PrototypeTargetSource.html
- Spring API — `ScopedProxyFactoryBean`: https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/aop/scope/ScopedProxyFactoryBean.html

**Điểm cần nhớ:** Trong Spring, scoped proxy là lời giải ở mức bean wiring và scope; `TargetSource` là hợp đồng mà AOP proxy sử dụng để tìm target khi chuyển lời gọi. Scoped proxy tiêu chuẩn kết nối hai tầng này nhờ `SimpleBeanTargetSource` và `BeanFactory`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-source-009">Chọn TargetSource nâng cao chỉ khi có nhu cầu và kiểm thử rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn TargetSource nâng cao trong Spring AOP: chỉ dùng khi có nhu cầu và kiểm thử rõ ràng

## 1. Mục tiêu và nguyên tắc quyết định

Trong Spring AOP, phần lớn bean nghiệp vụ được quản lý như singleton và được bọc bởi proxy khi có advisor phù hợp. **Không cần một `TargetSource` tùy biến** chỉ để áp dụng `@Aspect`, `@Transactional`, `@Cacheable` hoặc các interceptor thông thường. Việc đổi cách chọn target có thể ảnh hưởng tới trạng thái, tài nguyên, vòng đời, concurrency và ngữ nghĩa của từng lần gọi qua proxy.

`org.springframework.aop.TargetSource` là hợp đồng mà Spring AOP proxy dùng để lấy **đối tượng thực thi method**. Với nguồn target mặc định, các lời gọi thường tới cùng một instance. Với `TargetSource` nâng cao, mỗi lần gọi có thể lấy instance khác, mượn từ pool, gắn với thread hoặc chuyển đổi target đang hoạt động.

Chỉ nên chuyển sang `TargetSource` nâng cao khi đồng thời chứng minh được:

1. **Có yêu cầu định lượng/quan sát được**: cần đổi implementation sau triển khai, tạo đối tượng mới cho mỗi lần gọi, giới hạn tài nguyên đắt đỏ bằng pool, hoặc một đặc tính lifecycle cụ thể.
2. **Đã xét lựa chọn đơn giản hơn**: singleton stateless, Spring scope phù hợp, `ObjectProvider`, dependency injection theo ngữ cảnh, cấu hình động ở tầng service hoặc quản lý tài nguyên tường minh.
3. **Hợp đồng gọi vẫn đúng**: interface/proxy, advisor, exception, transaction và dữ liệu trả về không thay đổi bất ngờ.
4. **Có chiến lược sở hữu tài nguyên**: ai tạo/hủy target, làm gì với lời gọi đang chạy và đối tượng cũ.
5. **Có bộ test tái hiện được yêu cầu**: hành vi, advice, concurrency, lỗi, vòng đời và số đo vận hành.

Đặt câu hỏi trước khi cấu hình: **“Nếu giữ singleton hoặc scope Spring thông thường thì test nào thất bại, và đổi `TargetSource` sẽ làm test đó thành công bằng cách nào?”** Không có câu trả lời kiểm chứng được thì nên giữ cơ chế mặc định.

## 2. TargetSource nằm ở đâu trong một lời gọi Spring AOP?

~~~text
Client giữ tham chiếu Spring AOP proxy
                  |
                  v
      TargetSource.getTarget()
                  |
                  v
       Advisor / advice chain
                  |
                  +--> proceed() -> Gọi method trên target thực
                  |
                  v
  finally: releaseTarget(target) khi target động đã được lấy
                  |
                  v
       Trả kết quả / ném exception
~~~

Sơ đồ minh họa đường JDK proxy xử lý business method: Spring lấy target trước chuỗi interceptor; một advice có thể short-circuit mà không thực thi method đích, nhưng target đã cấp vẫn được release trong nhánh `finally` khi phù hợp. Target source tĩnh (`isStatic() == true`) cho phép framework tái dùng target và bỏ qua release theo lượt. Điều này tách ba trách nhiệm: cấp target, điều phối advice, và xử lý vòng đời target.

Hợp đồng quan trọng:

| Thành phần | Ý nghĩa thực tế |
|---|---|
| `getTargetClass()` | Kiểu target mà cơ chế tạo proxy/advisor có thể dùng để quyết định cách proxy và pointcut |
| `isStatic()` | Có thể giữ nguyên một target cho các invocation hay không |
| `getTarget()` | Cấp target để thực hiện lời gọi; nguồn động có thể chọn instance khác |
| `releaseTarget(target)` | Trả/giải phóng target vừa lấy; tùy implementation có thể là no-op, hủy prototype hoặc trả target về pool |

Điểm dễ nhầm: **proxy và target là hai khái niệm khác nhau**. Hai biến có thể trỏ cùng một proxy nhưng hai lần gọi lại chạy vào hai target khác nhau. Ngược lại, đổi target không đồng nghĩa Spring tự xây dựng lại advisor chain hay tạo một proxy mới.

Với proxy interface (JDK dynamic proxy), mọi replacement target phải triển khai interface mà proxy công khai; với class-based proxy cần xem thêm giới hạn kiểu, method `final` và compatibility của lớp đích. **Không dùng việc thay target để lách các giới hạn của proxy Spring AOP.**

## 3. Bảng lựa chọn: cơ chế nào thực sự cần?

| Giải pháp | Cách lấy target | Khi có thể phù hợp | Rủi ro và điều cần test |
|---|---|---|---|
| **Singleton / `SingletonTargetSource`** (mặc định thường gặp) | Cùng một target | Service stateless hoặc state được đồng bộ rõ ràng | Thread safety ở service; đây là lựa chọn ưu tiên |
| **`HotSwappableTargetSource`** | Cùng proxy nhưng target được thay bởi `swap(...)` | Chuyển implementation hoặc bản dữ liệu đang phục vụ mà không thay tham chiếu client | In-flight call, tương thích interface, cleanup target cũ, đồng bộ state |
| **`PrototypeTargetSource`** | Bean prototype mới cho **mỗi lời gọi method** | Mỗi invocation phải có instance và lifecycle độc lập | Chi phí tạo/wiring/hủy, không chia sẻ state giữa hai method call, tài nguyên trả về |
| **`CommonsPool2TargetSource`** | Mượn/trả target từ pool | Instance đắt để tạo, tái sử dụng được an toàn, có giới hạn dung lượng hợp lý | Exhaustion, timeout/wait, lỗi lúc trả, state leakage, sizing, dependency Commons Pool 2 |
| **`ThreadLocalTargetSource`** | Target gắn với mỗi thread | Trường hợp rất đặc biệt thực sự yêu cầu thread affinity | Thread reuse, thread pool, async handoff, cleanup, memory retention; không đồng nhất với request |
| **`LazyInitTargetSource`** | Khởi tạo target khi lần đầu cần | Muốn trì hoãn khởi tạo một target | Lỗi khởi tạo và độ trễ dồn vào request đầu tiên; thường nên bắt đầu từ lazy bean thông thường |

Các loại trên là công cụ của **Spring AOP**. Đừng dùng `ThreadLocalTargetSource` để mô phỏng request scope, session scope hoặc truyền request context giữa các thread. Đừng dùng pool chỉ vì “nhiều request đồng thời”: singleton stateless thường phục vụ concurrency tốt và đơn giản hơn.

### Quy trình quyết định thực hành

~~~text
Có yêu cầu đặc biệt mà singleton/scope bình thường không đáp ứng?
      |
      +-- Không --> Giữ proxy/target mặc định
      |
      +-- Có --> Yêu cầu nào?
                    |
                    +-- Thay implementation đang chạy --> HotSwappable
                    +-- Instance độc lập mỗi call   --> Prototype
                    +-- Tái sử dụng instance đắt đỏ --> Pooling
                    +-- Gắn state vào từng thread    --> ThreadLocal (cân nhắc rất kỹ)
                    +-- Chỉ trì hoãn tạo instance   --> Lazy initialization
                    |
                    v
        Xác định invariants + test trước khi cấu hình
                    |
                    v
          Chứng minh đúng cả khi lỗi và tải đồng thời
                    |
                    +-- Không đạt --> Thiết kế lại/giữ giải pháp đơn giản
                    +-- Đạt ------> Cho phép triển khai có quan sát
~~~

Đây là cây gợi ý, không phải quy tắc tự động. Ví dụ “cần tạo object mới” có thể giải quyết bằng factory/`ObjectProvider` tại đúng nơi thực sự cần, dễ kiểm soát hơn tạo một target mới **cho mọi method invocation**.

## 4. Hai tình huống dễ chọn sai

### 4.1. “Muốn đổi target theo phiên bản”

`HotSwappableTargetSource` giữ proxy ổn định và thay target được trả về cho những lần `getTarget()` tiếp theo. `swap(newTarget)` trả lại target cũ; thao tác swap an toàn khi nhiều thread gọi vào cơ chế đổi target của nó. Tuy nhiên, **an toàn khi swap không có nghĩa mọi lời gọi đang chạy sẽ dừng hoặc chạy lại trên target mới**.

Ví dụ: request A đã lấy target phiên bản xanh, sau đó admin `swap()` sang phiên bản đỏ, request B bắt đầu sau swap sẽ lấy target mới, nhưng request A vẫn có thể hoàn tất trên target xanh. Nếu có yêu cầu “không còn bất kỳ tác vụ cũ nào sau thời điểm chuyển” thì cần thêm cơ chế drain/quiescence, đếm invocation hoặc phối hợp chuyển pha ngoài `swap()`.

Các điều kiện phải làm rõ:

- Target mới có thực thi đầy đủ contract mà proxy đã công khai, bao gồm exception và ngữ nghĩa method?
- Mọi advisor đang có còn hợp lệ với target mới không? Điểm cắt phụ thuộc kiểu lớp target có thể không tự cập nhật chỉ vì swap.
- Ai dừng và hủy target cũ, vào thời điểm nào an toàn khi có invocation đang chạy?
- Có cần rollback? Nếu có, target dự phòng còn đủ trạng thái/dữ liệu không?
- Metrics cho biết đang phục vụ target nào và có bao nhiêu lỗi trước/sau chuyển?

**Không dựa vào `swap()` như cơ chế chuyển đổi giao dịch phân tán hoặc cấu hình an toàn tuyệt đối.** Nó giải quyết việc chọn local target qua một proxy Spring AOP.

### 4.2. “Muốn đối tượng mới cho mỗi tác vụ”

`PrototypeTargetSource` tạo target prototype mới cho **mỗi lần gọi method qua proxy**, sau đó hủy instance theo hợp đồng Spring BeanFactory khi release. Nếu code thực hiện:

~~~java
service.begin();
service.finish();
~~~

thì `begin()` và `finish()` có thể được thực thi trên **hai target khác nhau**. Cơ chế này sai nếu bạn mong trạng thái do `begin()` tạo được giữ tới `finish()`. Nếu cần một instance cho toàn bộ request/job/transaction, hãy cân nhắc scope phù hợp hoặc truyền một object phiên làm việc do ứng dụng quản lý.

Với prototype, phải đo chi phí tạo bean, autowiring, khởi tạo, callback destroy và áp lực GC. Đặc biệt không được trả một resource còn gắn với target đã hủy sau khi method kết thúc mà không chuyển quyền sở hữu rõ ràng.

## 5. Ví dụ chạy được: thay target và xác minh lifecycle prototype

Ví dụ cố ý tạo **Spring AOP proxy bằng `ProxyFactory`**, không tự động quét `@Aspect`, để cô lập đúng tác động của `TargetSource`. Advice được gắn trực tiếp bằng một `MethodInterceptor`. Khi tích hợp vào ứng dụng thật, proxy cùng advisor phải là bean mà client nhận được từ Spring container; việc chỉ định một `TargetSource` riêng lẻ **không tự động thay `TargetSource` của mọi bean do auto-proxy creator tạo**.

Ví dụ dùng Java 17+ và Spring Framework 6.2.x. Tạo ba tệp trong project Gradle:

`settings.gradle`

~~~groovy
rootProject.name = 'spring-targetsource-choice'
~~~

`build.gradle`

~~~groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.14'
    implementation 'org.springframework:spring-aop:6.2.14'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'example.TargetSourceChoiceDemo'
}
~~~

`src/main/java/example/TargetSourceChoiceDemo.java`

~~~java
package example;

import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.aop.target.PrototypeTargetSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

public class TargetSourceChoiceDemo {

    public interface Greeting {
        String greet();
    }

    public static final class GreetingTarget implements Greeting {
        private final String version;

        public GreetingTarget(String version) {
            this.version = version;
        }

        @Override
        public String greet() {
            return version;
        }
    }

    public interface Worker {
        int identity();
    }

    public static class TrackedWorker implements Worker, DisposableBean {
        private final int id;

        public TrackedWorker(int id) {
            this.id = id;
        }

        @Override
        public int identity() {
            return id;
        }

        @Override
        public void destroy() {
            PrototypeConfig.destroyed.incrementAndGet();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrototypeConfig {
        static final AtomicInteger created = new AtomicInteger();
        static final AtomicInteger destroyed = new AtomicInteger();

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public TrackedWorker prototypeWorker() {
            return new TrackedWorker(created.incrementAndGet());
        }
    }

    private static void hotSwapDemo() {
        var original = new GreetingTarget("v1");
        var source = new HotSwappableTargetSource(original);
        var adviceCalls = new AtomicInteger();

        var factory = new ProxyFactory();
        factory.setInterfaces(Greeting.class); // JDK proxy theo interface
        factory.setTargetSource(source);
        factory.addAdvice((MethodInterceptor) invocation -> {
            adviceCalls.incrementAndGet();
            return invocation.proceed();
        });

        Greeting proxy = (Greeting) factory.getProxy();
        Greeting heldReference = proxy;

        String before = proxy.greet();
        Object old = source.swap(new GreetingTarget("v2"));
        String after = proxy.greet();

        if (!before.equals("v1") || !after.equals("v2")
                || old != original || proxy != heldReference
                || adviceCalls.get() != 2 || !AopUtils.isAopProxy(proxy)) {
            throw new AssertionError("Hot-swap contract failed");
        }

        System.out.println("swap.before=" + before);
        System.out.println("swap.after=" + after);
        System.out.println("swap.oldTarget=" + ((Greeting) old).greet());
        System.out.println("swap.sameProxy=" + (proxy == heldReference));
        System.out.println("swap.adviceCalls=" + adviceCalls.get());
    }

    private static void prototypeDemo() {
        try (var context = new AnnotationConfigApplicationContext(
                PrototypeConfig.class)) {

            var source = new PrototypeTargetSource();
            source.setTargetBeanName("prototypeWorker");
            source.setBeanFactory(context.getBeanFactory());

            var factory = new ProxyFactory();
            factory.setInterfaces(Worker.class);
            factory.setTargetSource(source);

            Worker proxy = (Worker) factory.getProxy();
            int first = proxy.identity();
            int second = proxy.identity();

            int created = PrototypeConfig.created.get();
            int destroyed = PrototypeConfig.destroyed.get();

            if (first != 1 || second != 2
                    || created != 2 || destroyed != 2) {
                throw new AssertionError("Prototype lifecycle contract failed");
            }

            System.out.println("prototype.first=" + first);
            System.out.println("prototype.second=" + second);
            System.out.println("prototype.created=" + created);
            System.out.println("prototype.destroyed=" + destroyed);
        }
    }

    public static void main(String[] args) {
        hotSwapDemo();
        prototypeDemo();
    }
}
~~~

Chạy khi project có Gradle wrapper (Linux/macOS: `./gradlew run --quiet`; Windows: `gradlew.bat run --quiet`). Các dòng mà chương trình chủ động in ra là:

~~~text
swap.before=v1
swap.after=v2
swap.oldTarget=v1
swap.sameProxy=true
swap.adviceCalls=2
prototype.first=1
prototype.second=2
prototype.created=2
prototype.destroyed=2
~~~

**Ý nghĩa kiểm thử của ví dụ:**

- `swap.before` và `swap.after` chứng minh cùng proxy truy cập target khác sau swap; `swap.sameProxy=true` chứng minh client không cần nhận tham chiếu proxy mới.
- `swap.adviceCalls=2` chứng minh interceptor vẫn thực thi ở cả hai lời gọi. Đây là điều cần kiểm tra khi kết hợp `TargetSource` với Spring AOP advice.
- `swap.oldTarget=v1` chứng minh `swap()` trả lại đối tượng cũ để caller tự quyết định lifecycle; ví dụ không giả định Spring tự đóng mọi tài nguyên của target cũ.
- Hai identity khác nhau của prototype chứng minh **mỗi invocation** có instance riêng. Hai callback `destroy()` được gọi ngay sau các invocation; đó là hành vi của `PrototypeTargetSource`, không phải mặc định của mọi bean prototype lấy trực tiếp bằng `getBean()`.

Đoạn `ProxyFactory` là cấu hình AOP **bằng mã** để kiểm thử cơ chế một cách cô lập. Trong ứng dụng dùng `@Aspect`/`@Transactional` và auto-proxy, cần kiểm tra thêm rằng bean client nhận được có đúng advisor và `TargetSource` mong muốn. Không dựa vào kết quả của proxy thử nghiệm để kết luận mọi proxy tự sinh đều đã cấu hình đúng.

## 6. Test cần có trước khi chọn giải pháp nâng cao

Một phép thử “gọi method thành công” là chưa đủ. Tối thiểu phải test các **invariant** sau, tùy cơ chế:

| Tình huống | Hành vi cần xác nhận | Bằng chứng tốt |
|---|---|---|
| Baseline mặc định | Tại sao singleton/scope sẵn có không đáp ứng? | Test nghiệp vụ thất bại có chủ đích hoặc số đo tải/tài nguyên |
| Advice kết hợp với TargetSource | Advice/pointcut/transaction cần thiết vẫn được áp dụng qua proxy | Gọi qua bean/proxy thật, kiểm tra hiệu ứng advice; khi thích hợp xem `Advised.getAdvisors()` |
| Hot-swap bình thường | Proxy identity không đổi, lời gọi **sau swap** tới target mới, target cũ được trả lại | Kiểm tra object identity, phiên bản và old target |
| Hot-swap lúc đang có invocation | Lời gọi cũ có thể hoàn tất trên target cũ; không gọi method của một target đã đóng | Dùng `CountDownLatch`/barrier trong test để giữ invocation cũ mở lúc swap |
| Prototype mỗi method | Mỗi method invocation tạo và release instance đúng một lần | Counter create/destroy và id khác nhau, kể cả khi method ném lỗi |
| Pooling khi còn sức chứa | Borrow/release cân bằng, không rò state giữa hai client | Pool stats, id object, reset/validation sau mỗi lần trả |
| Pooling khi cạn | Thời gian chờ, timeout hoặc lỗi có chủ đích và đo được | Đặt max-size nhỏ, giữ một lease, chạy invocation khác theo kịch bản có kiểm soát |
| ThreadLocal | Mỗi thread nhận đúng target, vòng đời theo thread và cleanup | Thread pool tái sử dụng thread, kiểm tra isolation, memory/lifecycle |
| Lỗi và hủy ứng dụng | Target/resource không bị rò sau exception, cancellation, shutdown | Counter dispose, closed resources, stress/soak test |
| Hiệu năng | Lợi ích vượt overhead proxy/creation/pooling/locks | So sánh tải tương đương p95/p99, allocation, GC, throughput |

### 6.1. Kịch bản concurrency bắt buộc cho HotSwappableTargetSource

Đừng viết test racy bằng `Thread.sleep(...)` rồi mặc định thread nào chạy trước. Hãy tạo hai latch:

1. Target cũ khi vào `greet()` báo hiệu `entered.countDown()` rồi chờ `proceed.await()`.
2. Thread A gọi qua proxy; test chờ chắc chắn `entered` đã đạt zero, tức invocation A đang ở target cũ.
3. Thread B gọi `source.swap(newTarget)`; kiểm tra reference cũ được trả lại.
4. Cho phép A kết thúc; nó phải hoàn thành hợp đồng của **target cũ**. Một lời gọi mới sau swap phải dùng **target mới**.
5. Kiểm tra cả advisor, lỗi và cleanup trước khi đóng target cũ. Nếu target cũ cần close, chỉ đóng sau khi mọi invocation đang dùng nó đã được quản lý xong.

Test này phân biệt lời gọi đang bay (in-flight) và lời gọi bắt đầu sau swap; không đòi một bảo đảm sai rằng `swap()` hủy ngay mọi invocation cũ.

### 6.2. Kịch bản lỗi cho prototype

Cho method của target ném một exception xác định; xác minh exception tới caller đúng như dự kiến **và** `destroy()` vẫn được thực hiện khi target được release. Gọi tiếp một lần mới để chứng minh proxy còn dùng được và instance mới không giữ state lỗi. Nếu target nắm file/socket/connection, dùng đối tượng test có counter close rõ ràng để phát hiện release thiếu.

### 6.3. Kịch bản tải cho pooling

`CommonsPool2TargetSource` sử dụng Apache Commons Pool 2, cần khai báo dependency tương ứng khi áp dụng. Chỉ cân nhắc khi chi phí khởi tạo target đủ lớn và target có thể dùng lại an toàn. Với pool nhỏ:

- Chạy hai lời gọi đồng thời trong khi một target đang bị giữ; xác minh chính xác hành vi khi cạn: đợi, timeout hay lỗi, dựa trên cấu hình thực tế.
- Bắt target ném exception; bảo đảm quy trình trả/loại object lỗi vận hành đúng và không chuyển state bẩn sang invocation tiếp theo.
- Kiểm tra kích thước pool/active/idle, tỷ lệ chờ, thời gian chờ và sử dụng bộ nhớ khi tăng tải.
- So sánh với singleton stateless và factory tạo object trên **cùng workload**; pooling không mặc nhiên nhanh hơn.

Một method trên proxy tương ứng một lần mượn/trả; đừng giữ tham chiếu target/raw bean bên ngoài lease hoặc trả về object còn phụ thuộc tài nguyên chỉ sống trong lease nếu chưa thiết kế quyền sở hữu.

### 6.4. ThreadLocal và ứng dụng có async

`ThreadLocalTargetSource` liên kết target với thread đang gọi, không phải một “phiên nghiệp vụ” có thể đi theo luồng xử lý sang thread khác. Khi `@Async`, `CompletableFuture`, scheduler, pool worker hoặc virtual thread tham gia, ngữ nghĩa dễ khác với request ban đầu. Test phải có cùng một thread xử lý hai request tuần tự và một request chạy qua nhiều thread; kiểm tra state cũ có bị nhìn thấy sai không. Ưu tiên dùng request scope, tham số tường minh hoặc cơ chế truyền context phù hợp nếu dữ liệu theo request.

## 7. Tích hợp vào cấu hình Spring AOP đang có

Khi đã chọn một `TargetSource`, cần xác minh **client lấy đúng Spring bean proxy**:

1. Định nghĩa target (prototype với nguồn prototype/pooling/thread-local), `TargetSource` và proxy ở phạm vi phù hợp. Với Java config, có thể tạo proxy qua `ProxyFactory` và trả nó từ phương thức `@Bean`; với cấu hình truyền thống có thể dùng `ProxyFactoryBean` và property `targetSource`.
2. Gắn các advisor cần thiết vào **cùng proxy**. Nếu hệ thống đang dựa trên auto-proxy của `@EnableAspectJAutoProxy` hoặc hạ tầng transaction, không tự ý tạo một `ProxyFactory` khác rồi mặc định rằng mọi advisor tự động được sao chép. Kiểm tra thực thể proxy cuối cùng bằng `AopUtils.isAopProxy(bean)` và, khi thích hợp, `((Advised) bean).getAdvisors()`/`getTargetSource()`.
3. Không inject trực tiếp raw target bean vào nơi cần advice. Raw target được gọi trực tiếp sẽ bỏ qua proxy; hai object tham chiếu đến cùng target nhưng đi qua đường khác nhau có thể có semantics khác nhau.
4. Chọn interface/class proxy nhất quán. Replacement phải tương thích với kiểu mà caller và proxy đang dùng; target class có thể thay nhưng các method/advisor đã được xây dựng theo cấu hình ban đầu.
5. Kiểm tra các khả năng phụ thuộc vào Spring proxy như `@Transactional`, caching, security hoặc custom interceptors **ở cấu hình ứng dụng thật**, không chỉ ở ví dụ `ProxyFactory` độc lập. Self-invocation vẫn có thể bỏ qua advice dù source được chọn đúng.

Đoạn kiểm tra sau đặt trong code nơi đã có `ApplicationContext context`; đây là snippet tích hợp, **không phải chương trình Java độc lập**:

~~~java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("serviceProxy");

System.out.println("isAopProxy=" + AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    System.out.println("targetSource="
            + advised.getTargetSource().getClass().getSimpleName());

    for (var advisor : advised.getAdvisors()) {
        System.out.println("advisor=" + advisor);
    }
}
~~~

Tên `serviceProxy` chỉ là ví dụ định danh do ứng dụng khai báo; cần thay bằng bean có thật. Kiểm tra metadata proxy **không thay thế** test hành vi của từng advisor qua lời gọi method.

## 8. Quản lý tài nguyên và ngữ nghĩa method

### Target được tạo/hủy lúc nào?

- **HotSwappable**: caller quản lý target mới truyền vào và target cũ nhận về. Nếu target cũ có tài nguyên cần đóng, phải có chính sách chờ invocation cũ hoàn thành rồi đóng đúng lúc.
- **Prototype**: mỗi lời gọi proxy lấy prototype mới; implementation nguồn có xử lý hủy prototype khi release. Một prototype bean lấy trực tiếp bằng `applicationContext.getBean(...)` không được container tự động quản lý toàn bộ vòng đời hủy theo cách giống singleton.
- **Pooling**: resource phải được reset/validate, trả pool hoặc loại khỏi pool khi lỗi; khi container đóng cần bảo đảm pool được hủy.
- **ThreadLocal**: target gắn với thời gian sống của thread; nếu thread sống lâu, state/resource có thể bị giữ ngoài thời gian nghiệp vụ mong muốn.

### Tại sao method stateful là rủi ro?

Xét một interface:

~~~java
interface TransactionalDraft {
    void begin(String key);
    void append(String text);
    String finish();
}
~~~

Nếu dùng `PrototypeTargetSource`, ba lời gọi đó có thể chạy trên **ba đối tượng khác nhau**: `begin` không chuẩn bị state cho `append`/`finish`. Nếu pool chọn các target khác nhau giữa invocation, lỗi tương tự cũng xuất hiện. Thiết kế an toàn hơn thường là một method nghiệp vụ hoàn chỉnh (`processDraft(...)`), một object phiên làm việc có lifecycle tường minh hoặc một kho state riêng có semantics rõ ràng.

Cũng cần xem object trả về: iterator, stream, cursor hay callback giữ tham chiếu target hoặc tài nguyên nội bộ có thể tiếp tục sống **sau khi** target đã được release. Khi đó phải thay đổi hợp đồng trả về hoặc chuyển quyền sở hữu tài nguyên rõ ràng.

## 9. Dấu hiệu đang lạm dụng TargetSource

- Chọn `ThreadLocalTargetSource` vì muốn dữ liệu “riêng từng HTTP request” mà không test thread reuse/async.
- Chọn pooling chỉ vì target có field mutable; pool không tự làm state an toàn hay tự reset mọi dữ liệu nhạy cảm.
- Chọn prototype để giải quyết thread safety của một service thiết kế stateful, nhưng business flow lại cần nhiều method gọi trên cùng object.
- Chọn hot-swap để “deploy code không restart”, trong khi việc hoán đổi classloader, schema, transaction và tài nguyên ngoài JVM không thuộc bảo đảm của `swap()`.
- Chỉ test `isAopProxy` hoặc `getTargetClass`, không test advice, exception, release, instance count và concurrency.
- Dùng `@Lazy`/`@Scope("prototype")` rồi cho rằng Spring đã tự thay chiến lược chọn target của một proxy hiện có.
- Sau khi cấu hình `TargetSource` tùy biến, inject raw target thay vì proxy tại một số đường đi khiến advice chạy không nhất quán.

Khi test chứng minh lựa chọn nâng cao là cần thiết, đó là một quyết định thiết kế hợp lệ. Nếu không, giữ target mặc định giúp giảm số trường hợp cần suy luận và quan sát trong production.

## 10. Mẫu tiêu chí chấp nhận trước khi triển khai

Ghi ngắn gọn ngay cạnh thay đổi cấu hình:

| Trường cần xác nhận | Ví dụ bằng chứng |
|---|---|
| Nhu cầu nghiệp vụ | “Phải đổi bộ thực thi mà client vẫn giữ nguyên proxy”; test v1 → v2 |
| Phương án thông thường không đạt | Test baseline với singleton/scope và lý do thất bại |
| Hợp đồng proxy | Interface, advisor, transaction và exception vẫn đúng |
| Vòng đời | Quy tắc tạo/hủy, counter tài nguyên cân bằng |
| Đồng thời | In-flight swap, pool exhaustion hoặc thread reuse theo cơ chế đã chọn |
| Rollback và quan sát | Quy trình khôi phục target, metrics và log đủ truy vết |
| Ngưỡng chấp nhận hiệu năng | p95/p99, throughput, memory/GC dưới workload tái hiện được |

**Quy tắc chốt:** chỉ cấu hình `TargetSource` nâng cao trong Spring AOP khi có đặc tính mà giải pháp thông thường không cung cấp và bộ test chứng minh được cả **lợi ích lẫn tính đúng đắn**. Quan sát số lượng target, số lần tạo/hủy, độ trễ và hành vi khi có exception quan trọng ngang với việc proxy chạy được ở happy path.

## 11. Tài liệu Spring chính thức

- Spring Framework Reference — Using TargetSource Implementations: https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html
- Spring Framework Reference — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework Javadoc — `TargetSource`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/TargetSource.html
- Spring Framework Javadoc — `HotSwappableTargetSource`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/target/HotSwappableTargetSource.html
- Spring Framework Javadoc — `PrototypeTargetSource`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/target/PrototypeTargetSource.html
- Spring Framework Javadoc — `CommonsPool2TargetSource`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/target/CommonsPool2TargetSource.html

</details>

- [Quay lại đầu trang](#back-to-top)
