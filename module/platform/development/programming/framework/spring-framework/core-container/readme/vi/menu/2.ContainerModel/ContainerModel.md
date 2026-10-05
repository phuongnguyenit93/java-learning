<a id="back-to-top"></a>

# Mô hình hoạt động của Spring Container

## Menu
- [BeanDefinition và metadata cấu hình](#bean-definition-metadata)
- [BeanFactory và nền tảng của container](#bean-factory-role)
- [ApplicationContext mở rộng BeanFactory như thế nào?](#application-context-role)
- [Container lắp ráp đồ thị dependency ra sao?](#object-graph-assembly)
- [Khởi động container, đăng ký metadata và context refresh](#container-bootstrap-refresh)

## <a id="bean-definition-metadata">BeanDefinition và metadata cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

Spring không bắt đầu bằng việc tạo object một cách tùy ý. Trước hết container cần metadata mô tả **object nào nên tồn tại** và **nó phải được tạo như thế nào**. Đại diện trung tâm cho metadata đó là `BeanDefinition`.

Có thể xem `BeanDefinition` như một công thức tạo bean, chứ chưa phải bean instance. Tùy cách đăng ký, definition có thể chứa thông tin về bean class, factory method, scope, constructor arguments, property values, init callback, hành vi lazy, dependency metadata và các chỉ dẫn khác cho container. Bean thật có thể vẫn chưa được tạo ở thời điểm definition xuất hiện.

Phân biệt này rất quan trọng:

```text
BeanDefinition = mô tả / công thức
bean instance   = object được tạo từ mô tả đó
```

Các kiểu cấu hình khác nhau cuối cùng đều phải đưa metadata vào cùng container model. `@Bean`, component scanning, XML hay programmatic registration có syntax khác nhau, nhưng Spring vẫn cần một tập definition để xử lý trước khi tạo object.

Mô hình này giải thích vì sao Spring có thể thay đổi cấu hình trước khi bean tồn tại. `BeanFactoryPostProcessor` làm việc với metadata/definition, trong khi `BeanPostProcessor` làm việc với bean instance đã được tạo. Sự tách biệt đó sẽ trở nên rất quan trọng ở các chương về lifecycle và extension point.

`BeanDefinition` chủ yếu là abstraction hạ tầng của container. Mã nghiệp vụ thông thường không nên thao tác trực tiếp với nó; nó hữu ích nhất khi học bootstrap, mở rộng container hoặc debug vấn đề registration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-factory-role">BeanFactory và nền tảng của container</a>

<details>
<summary>Xem chi tiết</summary>

`BeanFactory` là API nền tảng để truy cập và tạo các bean do Spring quản lý. Các cách triển khai container đầy đủ hiện thực API đó trên tập bean-definition metadata, rồi tạo và ghép dependency cho instance khi cần theo metadata và scope.

Ở bề mặt API, ta có thể lấy bean theo tên hoặc type. Bên trong, vai trò của factory rộng hơn: nó quản lý definitions, phân giải dependency, lưu singleton instances, điều phối quá trình tạo bean và tham gia lifecycle thông qua các thành phần hạ tầng được cài vào factory.

Cần phân biệt quy ước API với cách triển khai. `DefaultListableBeanFactory` là một cách triển khai phổ biến nằm bên dưới nhiều `ApplicationContext`, nhưng code ứng dụng thường không tự tạo rồi quản lý bean factory này. Phần lớn ứng dụng làm việc qua `ApplicationContext`.

`BeanFactory` cũng giúp hiểu hành vi lazy. Việc có một bean definition không đồng nghĩa bean instance đã tồn tại. Tùy container và cấu hình, object có thể chỉ được tạo khi có yêu cầu. `ApplicationContext` thay đổi trải nghiệm này vì trong quá trình refresh nó mặc định pre-instantiate các singleton không đánh dấu lazy.

Vì vậy, trong ứng dụng thông thường, `BeanFactory` nên được xem là nền tảng khái niệm và hạ tầng. `ApplicationContext` là abstraction phù hợp hơn ở tầng ứng dụng vì nó giữ toàn bộ khả năng của bean factory đồng thời bổ sung nhiều dịch vụ framework khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-context-role">ApplicationContext mở rộng BeanFactory như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`ApplicationContext` mở rộng bean container thành một context của framework dành cho ứng dụng. Nó vẫn là một `BeanFactory`, nên vẫn phân giải và cung cấp bean, nhưng bổ sung các dịch vụ thường cần quanh object graph của ứng dụng.

Các khả năng nổi bật gồm resource loading, application events, message resolution cho i18n, truy cập `Environment`/properties và parent-child context. Context cũng điều phối quá trình `refresh`: chuẩn bị bean factory, chạy các container post-processor, đăng ký bean post-processor, khởi tạo hạ tầng và tạo các singleton eager.

Đó là lý do ứng dụng thường bắt đầu từ context như `AnnotationConfigApplicationContext` thay vì tự vận hành một `DefaultListableBeanFactory`:

```java
try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
    OrderService service = context.getBean(OrderService.class);
}
```

Điểm cần giữ lại không phải syntax mà là trách nhiệm: context chịu trách nhiệm bootstrap và shutdown; ứng dụng sử dụng object graph đã được chuẩn bị.

Không nên biến `ApplicationContext` thành global service locator mà mã nghiệp vụ gọi ở khắp nơi. Lookup API vẫn tồn tại, nhưng dependency injection mới là cách thông thường để bean ứng dụng nhận đối tượng cộng tác. Truy cập trực tiếp context phù hợp hơn cho bootstrap, framework integration, diagnostics hoặc trường hợp lookup động thực sự cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-graph-assembly">Container lắp ráp đồ thị dependency ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

Nhiệm vụ trung tâm của container là biến các definition độc lập thành một object graph có liên kết. Nó liên tục giải bài toán: **tại dependency point này, object nào đủ điều kiện và phải lấy object đó bằng cách nào?**

Giả sử `CheckoutService` cần `PaymentGateway`, còn `StripeGateway` cần `HttpClient`:

```text
CheckoutService
      ↓
PaymentGateway
      ↓
HttpClient
```

Spring bắt đầu từ các definition đã đăng ký, xác định dependency cần cho từng bean, tìm candidate phù hợp, tạo đối tượng cộng tác còn thiếu rồi inject instance vào nơi cần dùng. Với singleton, instance được container giữ lại và tái sử dụng theo scope.

Object graph không nhất thiết được tạo theo thứ tự file hay thứ tự khai báo. Dependency edge mới là thứ chi phối phần lớn thứ tự tạo bean. Nếu A cần B thì B có thể phải được tạo trước dù A được đăng ký trước. `depends-on` có thể thêm ràng buộc tường minh, nhưng dependency thông thường đã tự tạo ra quan hệ thứ tự cần thiết.

Mô hình graph cũng làm các lỗi dễ hiểu hơn. Không có target phù hợp dẫn tới unsatisfied dependency. Có nhiều target ngang nhau tạo ambiguity. Một vòng dependency có thể khiến quá trình khởi tạo không thể hoàn tất hoặc buộc Spring dùng cơ chế early reference trong một số trường hợp. Hình dung Spring như một bộ lắp ráp graph thường hữu ích hơn việc xem nó đơn thuần là công cụ đọc annotation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-bootstrap-refresh">Khởi động container, đăng ký metadata và context refresh</a>

<details>
<summary>Xem chi tiết</summary>

Khởi động container có thể tách thành hai giai đoạn khái niệm: **đăng ký metadata** và **hiện thực hóa metadata**. Trước hết Spring thu thập cấu hình và đăng ký bean definitions. Sau đó `ApplicationContext` được refresh để biến tập metadata đó thành một container hoạt động đầy đủ.

Với annotation-based context, có thể hình dung luồng rút gọn:

```text
tạo context
   ↓
register configuration classes / scan packages
   ↓
refresh context
   ↓
xử lý bean definitions
   ↓
cài post-processors và context infrastructure
   ↓
tạo non-lazy singleton beans
   ↓
context sẵn sàng sử dụng
```

`refresh()` vì vậy không chỉ có nghĩa "tạo bean". Nó thiết lập trạng thái bean factory, gọi các `BeanFactoryPostProcessor` trước khi phần lớn bean thông thường được tạo, đăng ký `BeanPostProcessor`, khởi tạo các context service và pre-instantiate singleton không lazy. Lỗi ở bất kỳ bước quan trọng nào có thể làm startup thất bại.

Hệ quả thực tế là nhiều lỗi cấu hình được phát hiện ngay lúc startup thay vì chờ tới lần gọi nghiệp vụ đầu tiên. Missing dependency, bean factory method lỗi hay initialization failure thường khiến context refresh fail fast.

Một số cách triển khai context tự refresh trong quá trình tạo, còn một số API cho phép tách bước đăng ký và refresh. Dù cú pháp khác nhau, mô hình tư duy vẫn nên giữ rõ: metadata phải được đăng ký trước khi container có thể xử lý và hiện thực hóa nó thành các instance được quản lý.

</details>

- [Quay lại đầu trang](#back-to-top)
