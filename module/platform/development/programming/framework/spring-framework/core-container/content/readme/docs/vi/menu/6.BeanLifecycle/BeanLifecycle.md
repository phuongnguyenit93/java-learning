<a id="back-to-top"></a>

# Bean Lifecycle và thứ tự khởi động

## Menu
- [Toàn cảnh vòng đời của bean](#bean-lifecycle-overview)
- [Khởi tạo bean và nạp dependency](#instantiation-and-population)
- [Aware callbacks](#aware-callbacks)
- [Initialization callbacks](#initialization-callbacks)
- [Destruction callbacks](#destruction-callbacks)
- [Lifecycle và SmartLifecycle](#lifecycle-smartlifecycle)
- [Thứ tự startup và depends-on](#startup-order-depends-on)
- [Ranh giới lỗi trong lifecycle: circular dependency, tạo bean quá sớm và cleanup](#lifecycle-failure-boundaries)

## <a id="bean-lifecycle-overview">Toàn cảnh vòng đời của bean</a>

<details>
<summary>Xem chi tiết</summary>

Một bean chưa sẵn sàng để dùng chỉ vì constructor đã chạy xong. Container còn phải phân giải và inject dependency, gọi các callback liên quan đến container, thực hiện initialization, cho hạ tầng post-process instance và cuối cùng tham gia shutdown khi context đóng.

Có thể hình dung vòng đời chính như sau:

```text
instantiate
    ↓
nạp dependency
    ↓
BeanFactory-level Aware callbacks + BeanPostProcessor.beforeInitialization(...)
(context-specific Aware callbacks và @PostConstruct có thể chạy trong pha này)
    ↓
InitializingBean + custom init method
    ↓
BeanPostProcessor.afterInitialization(...)
    ↓
bean sẵn sàng cho sử dụng bình thường
    ↓
destruction callbacks khi context sở hữu bean shutdown
```

Mô hình này giải thích nhiều hành vi tưởng như khó hiểu. Constructor injection phải được phân giải trước khi instance tồn tại; setter/field injection diễn ra sau khi đã tạo instance; còn proxy có thể chỉ xuất hiện sau khi post-processing hoàn tất. Sơ đồ cũng tách rõ cơ chế callback: Aware callback mức BeanFactory được factory gọi trực tiếp, callback gắn với `ApplicationContext` thường được `ApplicationContextAwareProcessor` xử lý, còn `@PostConstruct` thường được một initialization-aware post-processor gọi trước `InitializingBean` và custom init method. Vì vậy reference mà ứng dụng nhận được có thể là proxy do container tạo ra, không phải object gốc đã chạy các initialization callback.

Lifecycle cũng phụ thuộc scope. Với singleton, container thường tạo một instance cho mỗi bean definition và có thể quản lý destruction của instance đó. Với prototype, Spring tạo và cấu hình object rồi trao cho bên gọi; từ thời điểm đó container không theo dõi toàn bộ vòng đời nên cũng không tự động chạy destruction callback cho prototype.

Hãy dùng lifecycle hook cho công việc gắn trực tiếp với vòng đời container như kiểm tra cấu hình, dựng trạng thái nội bộ hoặc giải phóng resource mà bean sở hữu. Các component có hoạt động chạy nền nên dùng `Lifecycle`/`SmartLifecycle` để biểu diễn start/stop rõ ràng.

### Tài liệu tham khảo

- Spring Framework Reference — Customizing the Nature of a Bean

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="instantiation-and-population">Khởi tạo bean và nạp dependency</a>

<details>
<summary>Xem chi tiết</summary>

Điểm cần tách rõ đầu tiên là **tạo một object** và **biến object đó thành bean đã được cấu hình đầy đủ**.

Với constructor injection, Spring phải resolve các constructor argument trước khi có thể tạo instance:

```java
final class BillingService {
    private final TaxPolicy taxPolicy;

    BillingService(TaxPolicy taxPolicy) {
        this.taxPolicy = taxPolicy;
    }
}
```

Nếu không phân giải được `TaxPolicy`, `BillingService` còn chưa thể được tạo.

Setter và field injection xảy ra sau khi instance đã tồn tại. Về mặt mô hình:

```text
new BillingService(...)
        ↓
nạp các dependency có thể gán
        ↓
tiếp tục initialization
```

Annotation-driven injection như `@Autowired` được hiện thực thông qua post-processing của container. Điều quan trọng đối với người viết bean là thời điểm: các dependency cần thiết đã được inject trước khi callback như `@PostConstruct` chạy.

Không nên thực hiện công việc có tác động ra bên ngoài ngay trong constructor chỉ vì object đã được cấp phát. Lúc đó setter/field dependency có thể chưa có, callback của container chưa chạy và các lớp proxy/hạ tầng chưa đạt trạng thái cuối cùng. Constructor nên bảo đảm invariant nội tại của object; phần thiết lập phụ thuộc container nên diễn ra ở giai đoạn sau.

Điều này cũng cho thấy vì sao circular dependency qua constructor thường thất bại: mỗi bên đều cần bên còn lại được resolve trước khi chính nó có thể được tạo. Một số vòng lặp dùng mutable injection có thể được container xử lý kỹ thuật, nhưng không nên coi đó là kỹ thuật thiết kế; nó làm thứ tự initialization khó đoán và có thể khiến reference xuất hiện khi object chưa hoàn thiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aware-callbacks">Aware callbacks</a>

<details>
<summary>Xem chi tiết</summary>

Các `Aware` interface cho phép bean yêu cầu container cung cấp một phần hạ tầng của chính container. Một số ví dụ là `BeanNameAware`, `BeanFactoryAware`, `ApplicationContextAware`, `EnvironmentAware` và `ResourceLoaderAware`.

Ví dụ:

```java
final class ContextInspector implements ApplicationContextAware {
    private ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }
}
```

Container cung cấp callback này trong quá trình initialization, trước các initialization callback thông thường của bean. Một số callback mức bean factory như `BeanNameAware` và `BeanFactoryAware` được gọi trực tiếp, còn các callback gắn với application context như `ApplicationContextAware` được chuyển qua `ApplicationContextAwareProcessor`, bản thân processor này là một `BeanPostProcessor`. Điều mã ứng dụng có thể dựa vào là giao diện Aware tương ứng đã được cung cấp trước khi các initialization callback thông thường chạy.

Đổi lại, bean bị phụ thuộc trực tiếp vào Spring API. Nếu service chỉ cần `Clock`, `PricingRepository` hoặc một dependency của ứng dụng khác, hãy inject dependency đó trực tiếp qua constructor. Inject toàn bộ `ApplicationContext` chỉ để tự gọi `getBean(...)` biến code ứng dụng thành Service Locator và che giấu dependency thật.

`Aware` phù hợp khi chính hạ tầng là dependency cần thiết. Một framework component có thể hợp lý khi cần biết bean name, dùng resource loader, publish event hoặc đọc environment. Khi đó hãy chọn interface hẹp nhất có thể; nếu chỉ cần nạp resource thì `ResourceLoaderAware` biểu đạt ý định tốt hơn `ApplicationContextAware`.

Điểm cần nhớ về lifecycle là: Aware callback thuộc giai đoạn thiết lập container. Nó không phải event khởi động ứng dụng và cũng không thay thế constructor injection cho đối tượng cộng tác thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="initialization-callbacks">Initialization callbacks</a>

<details>
<summary>Xem chi tiết</summary>

Initialization callback chạy sau dependency population và trước khi bean được xem là đã hoàn tất để dùng bình thường. Spring hỗ trợ ba cơ chế phổ biến:

- `@PostConstruct`
- `InitializingBean.afterPropertiesSet()`
- custom init method được khai báo qua bean metadata, chẳng hạn `@Bean(initMethod = "startCache")`

Nếu một bean dùng cả ba cơ chế với các method khác nhau, thứ tự là:

```text
@PostConstruct
    ↓
InitializingBean.afterPropertiesSet()
    ↓
custom init method
```

Với class ứng dụng, `@PostConstruct` hoặc một method thuần Java thường giúp giảm coupling với Spring hơn so với triển khai `InitializingBean`.

```java
final class RouteTable {
    private final List<Route> routes;
    private Map<String, Route> byCode;

    RouteTable(List<Route> routes) {
        this.routes = routes;
    }

    @PostConstruct
    void indexRoutes() {
        byCode = routes.stream()
                .collect(Collectors.toUnmodifiableMap(Route::code, r -> r));
    }
}
```

Initialization phù hợp để validate cấu hình và dựng dữ liệu nội bộ từ các dependency đã được inject. Không nên biến nó thành hook khởi động tổng quát cho công việc nặng rồi gọi ngược sang nhiều bean khác. Target vẫn đang ở trong vòng đời tạo bean, vì vậy proxy-based interceptor không phải ranh giới đáng tin cậy cho lời gọi xuất phát từ chính init method của target.

Nếu công việc thực sự phải đợi đến sau khi các singleton thông thường đã được tạo xong, hãy dùng cơ chế dành cho thời điểm đó như context refresh event hoặc `SmartInitializingSingleton`, tùy vai trò của component.

### Tài liệu tham khảo

- Spring Framework Reference — Lifecycle Callbacks

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="destruction-callbacks">Destruction callbacks</a>

<details>
<summary>Xem chi tiết</summary>

Destruction callback là phía đối xứng với initialization: nó cho bean do container quản lý cơ hội cuối cùng để giải phóng resource mà bean sở hữu khi context shutdown.

Các cơ chế phổ biến:

- `@PreDestroy`
- `DisposableBean.destroy()`
- custom destroy method, ví dụ `@Bean(destroyMethod = "close")`

Nếu ba cơ chế dùng các method khác nhau, Spring gọi theo thứ tự:

```text
@PreDestroy
    ↓
DisposableBean.destroy()
    ↓
custom destroy method
```

Việc giải phóng tài nguyên nên idempotent và hoàn tất trong thời gian hữu hạn:

```java
final class LocalWorkerPool {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @PreDestroy
    void close() {
        executor.shutdown();
    }
}
```

Destroy callback chỉ có ý nghĩa khi container thực sự được đóng. Với ứng dụng standalone tự tạo `ApplicationContext`, cần gọi `close()` hoặc đăng ký JVM shutdown hook khi phù hợp. Chỉ để Java reference tới context bị mất không đồng nghĩa với việc shutdown context.

Prototype có ranh giới quan trọng: Spring tạo và cấu hình prototype instance nhưng không quản lý toàn bộ lifetime sau khi trả object cho bên yêu cầu. Vì vậy destruction callback của prototype không tự động được gọi. Bên lấy prototype phải chịu trách nhiệm giải phóng tài nguyên sau đó nếu instance giữ resource.

Cũng cần tách bean destruction với `Lifecycle.stop()`. Một component có thể cần được stop có phối hợp trước, rồi destruction callback mới giải phóng resource cuối cùng. Hai cơ chế liên quan nhau nhưng giải quyết hai giai đoạn khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-smartlifecycle">Lifecycle và SmartLifecycle</a>

<details>
<summary>Xem chi tiết</summary>

`Lifecycle` và `SmartLifecycle` dành cho component có trạng thái đang chạy, ví dụ listener, thành phần nhận dữ liệu, adapter kết nối nền hoặc một thành phần xử lý nền. Chúng khác với initialization/destruction chỉ chạy một lần quanh vòng đời bean.

`Lifecycle` có giao diện cơ bản:

```java
interface Lifecycle {
    void start();
    void stop();
    boolean isRunning();
}
```

Bean triển khai `Lifecycle` thông thường tham gia khi `ApplicationContext` nhận tín hiệu start/stop rõ ràng. Chỉ triển khai `Lifecycle` không có nghĩa component tự động được start lúc context refresh.

`SmartLifecycle` bổ sung những gì cần cho startup/shutdown có phối hợp:

- `isAutoStartup()` quyết định component có tự start khi `ApplicationContext` chứa nó được refresh hay không.
- `getPhase()` tham gia sắp thứ tự.
- `stop(Runnable callback)` cho phép shutdown bất đồng bộ báo cho lifecycle processor khi đã stop xong.

Phase chạy theo hai chiều ngược nhau:

```text
startup:   phase thấp → phase cao
shutdown:  phase cao → phase thấp
```

Bean `Lifecycle` thông thường có thể xem như phase `0`. Một lớp triển khai `SmartLifecycle` không ghi đè `getPhase()` dùng `Integer.MAX_VALUE` làm phase mặc định, nên thông thường start muộn hơn component ở phase `0` và stop sớm hơn. Phase âm start trước phase `0` và stop sau phase `0`; phase dương thì ngược lại.

Dùng phase để biểu diễn thứ tự lifecycle thô giữa các component hạ tầng độc lập. Nếu B thực sự có bean dependency vào A thì hãy mô hình dependency đó trực tiếp; Spring cũng tôn trọng dependency relationship để dependent start sau dependency và stop trước dependency.

Với `SmartLifecycle.stop(Runnable)`, lớp triển khai phải gọi `callback.run()` sau khi quá trình stop bất đồng bộ hoàn tất. Nếu quên callback, lifecycle processor phải chờ tới timeout của phase shutdown.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="startup-order-depends-on">Thứ tự startup và depends-on</a>

<details>
<summary>Xem chi tiết</summary>

Spring đã suy ra phần lớn startup order từ chính object graph. Nếu `ReportService` cần `DatabaseClient`, dependency phải được tạo trước khi dependent bean có thể hoàn tất. Dependency injection thông thường không cần thêm annotation chỉ để ép thứ tự.

`depends-on` / `@DependsOn` dành cho quan hệ thứ tự có thật nhưng không được thể hiện bằng một Java reference được inject. Ví dụ một component cần side effect của bean khác hoàn tất trước:

```java
@Bean
CacheIndex cacheIndex() {
    return new CacheIndex();
}

@Bean
@DependsOn("cacheIndex")
QueryGateway queryGateway() {
    return new QueryGateway();
}
```

Với singleton, `depends-on` còn ảnh hưởng shutdown theo chiều ngược: dependent bị destroy trước bean mà nó phụ thuộc. Quan hệ tương tự cũng tham gia thứ tự start/stop của `Lifecycle`.

Chỉ nên dùng `@DependsOn` khi cần thiết. Nếu một component thực sự gọi component khác, dependency trực tiếp mô tả thiết kế rõ hơn, dễ test hơn và để container tự suy ra thứ tự. `@DependsOn` hữu ích hơn cho dependency hạ tầng gián tiếp như registration, static initialization hoặc setup tạo side effect bên ngoài.

Không nên nhầm `@Order` với thứ tự tạo bean tổng quát. `@Order` chỉ được các thành phần sử dụng cụ thể diễn giải khi chúng cần sắp xếp một tập object, chẳng hạn một số extension-point chain hoặc event listener. Nó không có nghĩa "hãy tạo bean này trước".

Lazy bean cũng làm thay đổi thời điểm creation. `@DependsOn` không tự làm bean khai báo quan hệ trở thành eager. Tuy nhiên, khi bean đó thực sự được tạo, bean factory bảo đảm các bean được liệt kê trong `depends-on` phải được khởi tạo trước, kể cả khi một dependency trong số đó vốn được đánh dấu lazy. Nếu chính bean khai báo quan hệ vẫn lazy và không có gì yêu cầu nó, quan hệ này không tự khiến cả graph được tạo trong lúc refresh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-failure-boundaries">Ranh giới lỗi trong lifecycle: circular dependency, tạo bean quá sớm và cleanup</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi lifecycle thường xuất hiện khi code coi "bean đã tồn tại" đồng nghĩa với "bean đã hoàn tất". Có ba ranh giới cần đặc biệt chú ý.

**Circular dependency.** Vòng lặp constructor không thể thỏa mãn vì mỗi bên đều cần bên kia trước khi chính nó được construct. Mutable injection có thể khiến một số vòng lặp được giải quyết về mặt kỹ thuật, nhưng thiết kế vẫn khó theo dõi và có thể buộc container cung cấp early reference trước khi initialization hay post-processing hoàn tất.

**Tạo bean quá sớm.** Code hạ tầng đôi khi vô tình gọi lấy bean trong lúc container vẫn đang đăng ký hoặc tạo các processor. Bean đó có thể được tạo instance trước khi đầy đủ post-processor được áp dụng, dẫn tới bỏ lỡ transformation như proxying. Vì vậy code ở extension point không nên tùy tiện lookup bean ứng dụng trong giai đoạn bootstrap.

**Giải phóng tài nguyên thất bại hoặc bị bỏ quên.** Nếu initialization lấy resource mà không có đường shutdown rõ ràng, ứng dụng có thể rò thread, socket, file hoặc external lease. Hãy làm rõ quyền sở hữu: bean nào tạo resource do nó sở hữu thì thường cũng chịu trách nhiệm giải phóng. Code shutdown cũng nên chịu được initialization dang dở vì startup có thể fail sau khi một số resource đã được tạo.

Một chuỗi chẩn đoán hữu ích:

```text
Bean được request lần đầu ở đâu?
        ↓
Dependency nào buộc phải tồn tại tại thời điểm đó?
        ↓
Các post-processor cần thiết đã được đăng ký chưa?
        ↓
Initialization callback nào thất bại?
        ↓
Nếu startup dừng giữa chừng, resource nào đã tạo vẫn cần được giải phóng?
```

Không nên chữa lỗi lifecycle bằng cách thêm hàng loạt `@DependsOn` tùy ý. Cách đó có thể che triệu chứng nhưng giữ nguyên cycle, lookup ẩn hoặc quyền sở hữu sai. Hãy sửa dependency graph hoặc chuyển công việc sang đúng giai đoạn lifecycle chịu trách nhiệm cho nó.

</details>

- [Quay lại đầu trang](#back-to-top)
