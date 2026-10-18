<a id="back-to-top"></a>

# Dependency Injection và Bean Resolution

## Menu
- [Constructor, setter và field injection](#injection-styles)
- [Autowiring theo type](#type-based-autowiring)
- [Cách Spring chọn dependency candidate](#candidate-selection)
- [@Primary và @Qualifier](#primary-and-qualifier)
- [Collection, dependency tùy chọn và lazy dependency](#collection-and-optional-dependencies)
- [Dependency Injection và Service Locator](#service-locator-vs-di)
- [Lỗi thiếu bean, nhiều candidate và circular dependency](#dependency-resolution-failures)

## <a id="injection-styles">Constructor, setter và field injection</a>

<details>
<summary>Xem chi tiết</summary>

Kiểu injection quyết định class thể hiện yêu cầu dependency ra sao. Spring hỗ trợ constructor, setter và field injection, nhưng ba cách truyền đạt ý nghĩa thiết kế khác nhau.

**Constructor injection** thường là lựa chọn mặc định cho dependency bắt buộc:

```java
final class CheckoutService {
    private final PaymentGateway gateway;

    CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

Dependency xuất hiện ngay trong yêu cầu khởi tạo, field có thể giữ `final`, và test có thể tạo object mà không cần Spring container. Từ Spring Framework 4.3, class chỉ có một constructor không cần đặt `@Autowired` lên constructor đó.

**Setter injection** phù hợp hơn khi dependency thật sự tùy chọn hoặc việc thay đối tượng cộng tác sau khi object được tạo là một phần hợp lệ của thiết kế object. Vì object có thể tồn tại trước khi setter được gọi, class phải định nghĩa rõ trạng thái chưa được cấu hình đầy đủ có ý nghĩa gì.

**Field injection** ngắn gọn nhưng che dependency khỏi constructor, không thuận tiện cho `final` field và khiến việc tạo object trong unit test kém rõ. Spring vẫn hỗ trợ, nhưng nó thường không phải mặc định tốt cho mã ứng dụng.

Hãy chọn kiểu injection từ ý nghĩa của dependency. Dependency bắt buộc hợp với constructor; dependency optional/thay thế được có thể hợp với setter. Nếu constructor có quá nhiều tham số, đó thường là tín hiệu class đang ôm quá nhiều trách nhiệm chứ không phải lý do để giấu dependency vào field.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-based-autowiring">Autowiring theo type</a>

<details>
<summary>Xem chi tiết</summary>

Quá trình phân giải của `@Autowired` về bản chất bắt đầu từ **type**. Tại một injection point, container trước hết tìm những bean có type assignable cho type được yêu cầu.

```java
final class ReportService {
    ReportService(ReportRepository repository) { ... }
}
```

Nếu chỉ có đúng một candidate `ReportRepository` hợp lệ, lựa chọn rất rõ. Cách triển khai cụ thể có thể thay đổi mà `ReportService` không cần sửa, miễn cách triển khai mới vẫn thỏa type được yêu cầu.

Type-based resolution áp dụng cho class lẫn interface và tuân theo assignability của Java. Generic type information cũng có thể tham gia matching, nên các bean như `Store<String>` và `Store<Order>` có thể được phân biệt nếu container có đủ type metadata.

Điểm cần nhớ là type match tạo ra **tập candidate**, chưa chắc là kết quả cuối. Không có candidate thì dependency bắt buộc sẽ fail. Có nhiều candidate thì Spring cần thêm thông tin như `@Primary`, `@Qualifier` hoặc metadata khác để chọn một bean.

Không nên dùng type quá rộng như `Object` chỉ để làm injection "linh hoạt". Dependency type nên nói đúng khả năng mà bên sử dụng cần; type càng rõ thì đồ thị object và lỗi phân giải càng dễ hiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-selection">Cách Spring chọn dependency candidate</a>

<details>
<summary>Xem chi tiết</summary>

Candidate selection là quá trình thu hẹp dần. Spring bắt đầu từ các bean assignable cho dependency type, loại những bean không đủ điều kiện autowire, rồi áp dụng metadata bổ sung để xem có một candidate được ưu tiên hay injection point thực sự muốn nhận nhiều bean.

Với dependency đơn, có thể hình dung:

```text
required type
   ↓
eligible autowire candidates
   ↓
qualifier constraints, nếu có
   ↓
primary / priority signals
   ↓
unique candidate hoặc resolution failure
```

Bean name có thể tham gia như tiêu chí dự phòng trong những trường hợp name matching phù hợp, nhưng dependency không nên vô tình phụ thuộc vào tên parameter/field nếu sự khác biệt mang ý nghĩa nghiệp vụ. Với Spring Framework 6.1, việc dùng tên constructor hoặc method parameter cho bước dự phòng này đòi hỏi Java parameter metadata, thông thường bằng cách biên dịch với `-parameters`; tên field thì đã có trực tiếp từ field metadata và không phụ thuộc tùy chọn compiler đó. Khi vai trò đó có ý nghĩa, hãy biểu đạt bằng qualifier metadata.

Tập candidate còn chịu ảnh hưởng từ cách bean được đăng ký. Một bean definition có thể bị đánh dấu không tham gia autowiring nhưng vẫn có thể lấy bằng lookup tường minh. Hạ tầng cũng có thể cung cấp một số resolvable dependency trực tiếp từ container mà không nhất thiết là bean definition thông thường.

Khi quá trình phân giải khó hiểu, nên chẩn đoán theo thứ tự: kiểm tra đăng ký, kiểm tra assignable type, xem qualifier/primary metadata, rồi mới xem bean name hoặc custom autowire-candidate rule. Gắn `@Primary` ngay lập tức có thể chỉ che một duplicate registration ngoài ý muốn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="primary-and-qualifier">@Primary và @Qualifier</a>

<details>
<summary>Xem chi tiết</summary>

`@Primary` và `@Qualifier` giải hai bài toán khác nhau khi nhiều bean cùng type.

`@Primary` nói rằng: **nếu không có quy tắc thu hẹp mạnh hơn chọn bean khác, hãy ưu tiên candidate này cho dependency đơn**.

```java
@Bean
@Primary
ExchangeRateProvider liveRates() { ... }

@Bean
ExchangeRateProvider cachedRates() { ... }
```

`@Qualifier` biểu đạt ràng buộc có ý nghĩa tại injection point và trên candidate metadata:

```java
CheckoutService(@Qualifier("offline") PaymentGateway gateway) { ... }
```

Giá trị qualifier nên được hiểu là metadata dùng để thu hẹp các candidate đã match type. Nó không đơn thuần là lệnh lookup bean theo tên dạng chuỗi, dù bean name có thể được xem như cách khớp dự phòng cho qualifier trong trường hợp phù hợp.

Dùng `@Primary` khi một cách triển khai là lựa chọn mặc định hợp lý cho phần lớn bên sử dụng. Dùng qualifier khi các bên sử dụng cần vai trò, khu vực, giao thức hoặc chính sách khác nhau và sự khác biệt đó thuộc mô hình. Custom qualifier annotation thường an toàn và rõ nghĩa hơn việc lặp lại nhiều string value.

Nếu gần như injection point nào cũng cần qualifier khác nhau, nên xem lại liệu interface chung có thật sự biểu diễn một khả năng có thể thay thế lẫn nhau hay đang gom nhiều vai trò không liên quan. Resolution metadata nên mô tả khác biệt thật, không nên bù cho domain model thiếu rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="collection-and-optional-dependencies">Collection, dependency tùy chọn và lazy dependency</a>

<details>
<summary>Xem chi tiết</summary>

Không phải dependency nào cũng có nghĩa "hãy đưa cho tôi đúng một bean ngay bây giờ". Spring hỗ trợ nhiều hình dạng dependency cho collection, đối tượng cộng tác tùy chọn và truy cập trì hoãn.

Khi inject array, `Collection<T>`, `List<T>`, `Set<T>` hoặc dạng map được hỗ trợ, container có thể cung cấp nhiều bean theo element/value type. Cách này phù hợp với pipeline strategy hoặc thiết kế plugin:

```java
final class PriceEngine {
    PriceEngine(List<PriceRule> rules) { ... }
}
```

Các `PriceRule` đủ điều kiện được gom lại thay vì gây mơ hồ. Metadata như `Ordered` hoặc `@Order` có thể ảnh hưởng thứ tự khi injection target là collection có thứ tự.

Nếu dependency được phép vắng mặt một cách hợp lệ, có thể dùng `Optional<T>`, nullable injection point hoặc `@Autowired(required = false)` ở dạng được hỗ trợ. Chỉ nên biểu đạt tính tùy chọn khi class thật sự có hành vi đúng nếu thiếu đối tượng cộng tác; dependency bắt buộc nên fail ngay lúc khởi động.

Với truy cập trì hoãn, `ObjectProvider<T>` là handle gắn với container cho phép lấy bean sau, kiểm tra bean có sẵn/duy nhất hay không, stream nhiều candidate hoặc lấy bean với các argument tường minh trong những trường hợp được hỗ trợ. `@Lazy` tại injection point lại có thể inject một lazy-resolution proxy để trì hoãn việc resolve target tới lần dùng đầu tiên.

Cơ chế trì hoãn hữu ích khi nối hai scope khác nhau, xử lý object đắt tiền hoặc lookup thật sự động, nhưng đổi lại một phần validation bị dời từ lúc khởi động sang runtime. Không nên biến mọi dependency thành lazy chỉ để context khởi động thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="service-locator-vs-di">Dependency Injection và Service Locator</a>

<details>
<summary>Xem chi tiết</summary>

Dependency Injection và Service Locator đều giúp code nhận đối tượng cộng tác, nhưng trách nhiệm nằm ở hai nơi khác nhau.

Với DI, class khai báo thứ nó cần:

```java
final class ShippingService {
    ShippingService(CarrierClient carrier) { ... }
}
```

Với Service Locator, class chủ động hỏi registry hoặc container:

```java
CarrierClient carrier = context.getBean(CarrierClient.class);
```

Cách thứ hai che dependency khỏi constructor và làm mã nghiệp vụ phụ thuộc vào cơ chế lookup. Test phải chuẩn bị locator/context, còn người đọc signature không thể thấy đầy đủ những gì class cần để chạy.

Spring vẫn cung cấp lookup API vì có tình huống thực sự cần: code bootstrap, framework adapter, chẩn đoán, chọn plugin động hoặc code tích hợp chưa biết target cho tới runtime. Việc `ApplicationContext#getBean` tồn tại không biến lookup thành cách nhận dependency ưu tiên bên trong service ứng dụng thông thường.

Quy tắc thực tế là inject những đối tượng cộng tác ổn định và chỉ lookup khi tính động của dependency là yêu cầu thật. Nếu class gọi container chỉ để tránh thêm một constructor parameter, trách nhiệm thường đã bị đặt sai chỗ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-resolution-failures">Lỗi thiếu bean, nhiều candidate và circular dependency</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi phân giải dependency phản ánh trực tiếp hình dạng của đồ thị object. Phân loại theo nguyên nhân giúp đọc lỗi startup nhanh hơn.

**Thiếu dependency:** không có bean đủ điều kiện cho required injection point. Lỗi thường xuất hiện qua `UnsatisfiedDependencyException` với `NoSuchBeanDefinitionException` bên trong. Hãy kiểm tra bean có được register/scan hay không, có bị condition loại ra không, và type/qualifier được yêu cầu có đúng không.

**Nhiều candidate:** nhiều bean vẫn ngang nhau cho một injection point chỉ cần một giá trị. `NoUniqueBeanDefinitionException` thường liệt kê các candidate cạnh tranh. Hãy sửa mô hình bằng lựa chọn mặc định có chủ đích (`@Primary`) hoặc thu hẹp theo ngữ nghĩa (`@Qualifier`) thay vì xóa một bean vẫn hợp lệ.

**Circular dependency:** A cần B trong khi B cuối cùng lại cần A. Constructor cycle về bản chất không thể giải vì không object nào có thể được tạo trước:

```text
A constructor → B
      ↑         ↓
      └──── C ←─┘
```

Với singleton dùng setter/field injection, Spring có thể giải quyết một số cycle bằng early reference, nhưng không nên thiết kế dựa vào khả năng này. Cycle khiến initialization order mong manh, làm proxying khó hơn và thường cho thấy trách nhiệm cần được tách lại. Prototype cycle không thể dựa vào cùng cơ chế vì prototype instance không được container cache để tái sử dụng trong lúc tạo.

Nên xem lỗi startup là tín hiệu hữu ích. Hãy sửa đăng ký và trách nhiệm giữa các component thay vì làm dependency thành optional/lazy cho tới khi context chạy. Cách né lỗi đó chỉ chuyển một lỗi cấu hình xác định từ lúc khởi động sang một request production muộn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
