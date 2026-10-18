<a id="back-to-top"></a>

# Bean Scope và chính sách tạo Bean

## Menu
- [Spring singleton scope](#spring-singleton-scope)
- [Prototype scope](#prototype-scope)
- [Request, session, application và websocket scopes](#web-aware-scopes)
- [Eager và lazy initialization](#eager-vs-lazy-initialization)
- [Khi singleton phụ thuộc bean có scope ngắn hơn](#scoped-dependency-mismatch)
- [Tra cứu dependency trì hoãn và động](#dynamic-dependency-lookup)
- [Custom scopes](#custom-scopes)

## <a id="spring-singleton-scope">Spring singleton scope</a>

<details>
<summary>Xem chi tiết</summary>

Scope trả lời một câu hỏi khác với dependency type: **một bean instance được tái sử dụng trong bao lâu, và khi nào container tạo instance mới từ cùng một definition?** Scope mặc định của Spring là `singleton`.

Spring singleton có nghĩa một instance dùng chung **cho mỗi bean definition trong mỗi container**. Nó không phải GoF Singleton pattern và cũng không có nghĩa toàn JVM chỉ được có một instance.

```text
ApplicationContext A
  bean definition "catalogService" → một instance dùng chung

ApplicationContext B
  bean definition "catalogService" → một instance dùng chung khác
```

Trong `ApplicationContext`, các singleton không lazy thường được tạo khi context refresh. Sau khi tạo, bean factory giữ instance đó trong singleton cache và trả lại cùng object cho mọi dependency hoặc lookup tới definition tương ứng.

Singleton scope biểu đạt việc dùng chung vòng đời, nhưng không tự làm trạng thái có thể thay đổi trở nên thread-safe. Một singleton service được nhiều thread gọi đồng thời vẫn phải được thiết kế cho truy cập đồng thời. Service stateless hoặc chỉ giữ trạng thái dùng chung bất biến thường an toàn và dễ hiểu hơn bean chứa field thay đổi theo từng request.

Ưu điểm của singleton là có một instance ổn định và chỉ trả chi phí khởi tạo một lần cho dependency sống cùng container. Nếu trạng thái thật sự thuộc request, session, job hoặc context ngắn hơn, hãy chọn scope phù hợp thay vì đặt trạng thái đó vào singleton.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="prototype-scope">Prototype scope</a>

<details>
<summary>Xem chi tiết</summary>

Prototype scope yêu cầu Spring tạo một bean instance mới mỗi lần definition đó được container yêu cầu. Definition dùng chung, còn object tạo ra thì không.

```java
@Bean
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
Command command() {
    return new Command();
}
```

Mỗi lần `getBean(Command.class)` có thể nhận một `Command` mới. Tương tự, mỗi lần container cần phân giải prototype này cho một dependency, nó sẽ tạo instance khác.

Có một ranh giới trách nhiệm quan trọng: Spring tạo prototype, inject/configure dependency và chạy initialization processing, sau đó giao object cho bên sử dụng. Container **không** theo dõi toàn bộ vòng đời về sau và không tự gọi destruction callback cho prototype instance. Nếu prototype giữ resource cần đóng, bên sử dụng hoặc một cơ chế lifecycle rõ ràng phải chịu trách nhiệm giải phóng resource đó.

Prototype cũng không đồng nghĩa "mỗi method call có object mới". Nếu prototype được inject trực tiếp vào singleton lúc singleton được tạo, singleton nhận đúng một prototype instance rồi giữ reference đó. Muốn lấy instance mới nhiều lần cần deferred lookup, scoped indirection phù hợp hoặc thiết kế resolve dependency tại thời điểm sử dụng.

Hãy dùng prototype khi mỗi lần lấy bean thực sự cần một object có trạng thái riêng. Không nên biến mọi domain object thành prototype bean chỉ để tránh trạng thái dùng chung; nhiều domain object bình thường không cần nằm trong Spring container.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-aware-scopes">Request, session, application và websocket scopes</a>

<details>
<summary>Xem chi tiết</summary>

Các web-aware scope gắn vòng đời của bean với hạ tầng web thay vì toàn application context. Spring Framework định nghĩa `request`, `session`, `application` và `websocket` scope cho web-aware `ApplicationContext`.

- **request** tạo một instance cho mỗi HTTP request.
- **session** tạo một instance cho mỗi HTTP session.
- **application** gắn một instance với `ServletContext` của web application.
- **websocket** gắn trạng thái của bean với một WebSocket session trong hỗ trợ STOMP-over-WebSocket của Spring.

Các scope này chỉ hoạt động khi context tương ứng đang active. Cố resolve request-scoped bean trên thread không có request được bind có thể thất bại vì scope chưa active.

`application` scope có vòng đời gần singleton nhưng ý nghĩa khác. Spring singleton là một instance cho mỗi bean definition trong mỗi Spring container. Application-scoped bean lại gắn với `ServletContext`, nên phạm vi sử dụng đi theo servlet context của web application chứ không phải riêng một Spring container.

Web scope phù hợp với trạng thái thật sự thuộc những vòng đời đó, nhưng không nên trở thành chỗ chứa trạng thái ứng dụng có thể thay đổi một cách tùy tiện. Request/session lifecycle, truy cập đồng thời, serialization và clustering là các vấn đề của kiến trúc web cần được xem xét trước khi đặt nhiều trạng thái vào scoped bean. Ngữ nghĩa STOMP/WebSocket chuyên sâu thuộc ranh giới Spring Messaging/web, không thuộc phần Core Container này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="eager-vs-lazy-initialization">Eager và lazy initialization</a>

<details>
<summary>Xem chi tiết</summary>

Thời điểm tạo bean và scope có liên quan nhưng không phải một khái niệm. Một bean có thể là singleton nhưng được tạo eager hoặc lazy.

Mặc định, `ApplicationContext` pre-instantiate các singleton không lazy trong lúc refresh. Điều này có lợi vì lỗi ở constructor, dependency resolution và initialization được phát hiện ngay lúc startup thay vì tới một request sau này.

Đánh dấu bean bằng `@Lazy` yêu cầu container trì hoãn việc tạo bean tới lúc bean thực sự được cần:

```java
@Bean
@Lazy
ExpensiveIndex expensiveIndex() {
    return new ExpensiveIndex();
}
```

Việc tạo bean theo kiểu lazy có thể giảm công việc lúc startup cho tính năng ít dùng, nhưng đổi lại lỗi cấu hình bị dời sang runtime. Một bean cấu hình sai có thể không ngăn context khởi động và chỉ thất bại ở lần tính năng được dùng đầu tiên.

Bean lazy vẫn có thể bị tạo ngay startup nếu một non-lazy singleton cần nó trực tiếp; dependency đó buộc container lấy bean đích. Ngược lại, `@Lazy` tại injection point có thể tạo lazy-resolution proxy để bean đích chỉ được resolve khi dependency thật sự được gọi.

Nên dùng laziness để biểu đạt nhu cầu trì hoãn thật, không phải cách chung để né startup failure. Với core dependency bắt buộc, fail sớm thường an toàn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scoped-dependency-mismatch">Khi singleton phụ thuộc bean có scope ngắn hơn</a>

<details>
<summary>Xem chi tiết</summary>

Scope mismatch xuất hiện khi object sống lâu giữ trực tiếp một dependency có vòng đời ngắn hơn. Ví dụ quen thuộc là singleton cần prototype hoặc đối tượng cộng tác request-scoped.

Với prototype, constructor injection trực tiếp xảy ra lúc singleton được tạo:

```text
singleton tạo một lần
      ↓
prototype resolve một lần
      ↓
singleton giữ nguyên prototype reference đó
```

Prototype definition có thể tạo nhiều instance, nhưng field của singleton vẫn chỉ là một Java reference. Spring không tự re-inject field đó sau mỗi method call.

Với request/session scope, mismatch còn rõ hơn. Eager singleton có thể được tạo khi chưa có request đang hoạt động, nên việc lấy trực tiếp bean có scope ngắn có thể thất bại. Thứ singleton thường cần là một lớp trung gian để lấy đúng bean của scope hiện tại tại thời điểm sử dụng.

Giải pháp phổ biến là scoped proxy hoặc deferred lookup qua `ObjectProvider`/`ObjectFactory`. Chúng giữ nguyên singleton đang sử dụng dependency nhưng dời việc lấy bean đích tới thời điểm phù hợp. Một lựa chọn khác là thiết kế lại ranh giới và truyền dữ liệu theo request qua method parameter thay vì ẩn nó trong scoped dependency.

Chọn giải pháp theo ý nghĩa của dependency. Nếu dependency thật sự đại diện cho trạng thái theo ngữ cảnh, một lớp trung gian hiểu scope có thể hợp lý. Nếu bean sử dụng chỉ cần vài giá trị của request hiện tại, truyền dữ liệu tường minh thường đơn giản và ít phụ thuộc container hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-dependency-lookup">Tra cứu dependency trì hoãn và động</a>

<details>
<summary>Xem chi tiết</summary>

Deferred lookup giải quyết trường hợp bean không thể hoặc không nên nhận một dependency cố định ngay lúc chính nó được tạo. Spring có nhiều cơ chế, mỗi cơ chế đánh đổi mức độ phụ thuộc và hành vi runtime khác nhau.

`ObjectProvider<T>` là lựa chọn tổng quát và linh hoạt. Nó có thể lấy bean theo yêu cầu, kiểm tra bean có sẵn/duy nhất hay không và stream nhiều candidate:

```java
final class JobRunner {
    private final ObjectProvider<JobContext> contexts;

    JobRunner(ObjectProvider<JobContext> contexts) {
        this.contexts = contexts;
    }

    void run() {
        JobContext context = contexts.getObject();
        // dùng instance được resolve cho lần gọi hiện tại
    }
}
```

`ObjectFactory<T>` là giao diện nhỏ hơn nếu chỉ cần `getObject()`. Cả hai làm việc lookup hiện rõ trong API của bean sử dụng, nhưng đổi lại bean đó phụ thuộc vào abstraction của Spring container.

**Scoped proxy** đi theo hướng khác: bean sử dụng nhận một proxy ổn định, còn mỗi lời gọi được delegate tới target instance của scope hiện tại. Cách này giữ syntax injection bình thường nhưng mang theo đặc tính của proxy, vì vậy cần hiểu type/method nào có thể được proxy.

`@Lookup` cung cấp method injection. Spring ghi đè method được đánh dấu trong subclass do container tạo, để mỗi lần gọi method sẽ thực hiện bean lookup. Vì dựa vào runtime subclassing, class/method phải có khả năng được override; cơ chế này cũng không áp dụng cho instance bất kỳ do `@Bean` factory method trả về, vì Spring không tạo instance đó qua đường subclassing này.

Nên chọn cơ chế làm hành vi vòng đời dễ đọc nhất. `ObjectProvider` tường minh và dễ theo dõi; scoped proxy tiện khi phần lớn đoạn mã gọi không cần biết scope của target; `@Lookup` mang tính chuyên biệt và chỉ nên dùng khi method injection thật sự phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-scopes">Custom scopes</a>

<details>
<summary>Xem chi tiết</summary>

Các scope có sẵn bao phủ những vòng đời phổ biến, nhưng hạ tầng đôi khi cần context riêng như một bean instance cho mỗi tenant, conversation, job hoặc quy trình. `Scope` SPI cho phép thêm chính sách đó vào bean factory.

Custom `Scope` quyết định cách object được lưu và lấy theo key của ngữ cảnh hiện tại. Quy ước của nó gồm lấy object thông qua `ObjectFactory`, remove object, đăng ký destruction callback, resolve contextual object và cung cấp conversation identifier khi có ý nghĩa.

Sau khi scope được đăng ký với configurable bean factory, bean definition có thể dùng scope name đó tương tự built-in scope. `CustomScopeConfigurer` là một cách cấu hình phổ biến để cài custom scope mà không buộc bean ứng dụng tự thao tác bean factory.

Một custom scope tốt phải trả lời rõ các câu hỏi về lifecycle:

- Điều gì xác định context hiện tại?
- Scoped instances được lưu ở đâu?
- Khi nào context kết thúc?
- Ai chạy destruction callback đã đăng ký?
- Context được truyền qua thread hoặc async work thế nào, nếu có?

Không nên tạo custom scope chỉ để có một map chứa object tái sử dụng. Scope thay đổi quy tắc vòng đời xuyên suốt dependency resolution, nên nó thuộc hạ tầng và phải có quy tắc kích hoạt, giải phóng rõ ràng. Nếu vòng đời có thể biểu đạt bằng method parameter hoặc cache do ứng dụng quản lý, những cơ chế đơn giản hơn thường dễ vận hành hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
