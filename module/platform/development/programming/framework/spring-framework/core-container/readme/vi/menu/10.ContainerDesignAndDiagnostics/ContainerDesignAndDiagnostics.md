<a id="back-to-top"></a>

# Thiết kế và chẩn đoán Container End-to-End

## Menu
- [Luồng end-to-end từ metadata đến ApplicationContext sẵn sàng sử dụng](#end-to-end-container-flow)
- [Chọn chiến lược đăng ký bean](#registration-strategy)
- [Chọn chiến lược dependency injection](#injection-strategy)
- [Thiết kế scope và lifecycle](#scope-lifecycle-design)
- [Thiết kế Environment và các dịch vụ ApplicationContext](#environment-context-design)
- [Chẩn đoán lỗi startup và dependency](#startup-failure-diagnosis)
- [Điểm chuyển sang các Spring Framework modules lân cận](#neighboring-module-handoffs)

## <a id="end-to-end-container-flow">Luồng end-to-end từ metadata đến ApplicationContext sẵn sàng sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

Cách dễ nhớ Spring Container nhất là nhìn nó như một luồng thay vì một tập annotation rời rạc:

```text
nguồn cấu hình
→ BeanDefinition metadata
→ factory post-processing
→ phân giải dependency
→ tạo instance + nạp dependency
→ initialization/post-processing
→ đồ thị bean sẵn sàng trong ApplicationContext
→ shutdown/destruction
```

Mỗi chương trước giải thích một đoạn của luồng này. Khi ghép lại, Spring Container là **bộ máy ghép nối và quản lý lifecycle**: cấu hình mô tả những gì cần tồn tại, quá trình phân giải nối các đối tượng cộng tác, lifecycle làm chúng sẵn sàng sử dụng, còn `ApplicationContext` bổ sung các dịch vụ nền quanh đồ thị đó.

Khi debug, hãy xác định pha lỗi trước. Thiếu bean definition khác với nhiều candidate; cả hai lại khác bean ném exception trong initialization.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="registration-strategy">Chọn chiến lược đăng ký bean</a>

<details>
<summary>Xem chi tiết</summary>

Cách đăng ký bean là lựa chọn thiết kế, không có một cơ chế luôn "đúng hơn" mọi cơ chế khác.

**Component scanning** gọn khi class thật sự là component ứng dụng và ranh giới package rõ. **`@Bean` tường minh** phù hợp khi cách khởi tạo có tham số đặc biệt, dùng type từ thư viện bên ngoài hoặc cần cách ghép nối dễ nhìn. Đăng ký bằng API hữu ích cho hạ tầng khám phá/sinh definition động. XML vẫn là nguồn metadata hợp lệ và còn xuất hiện trong hệ thống cũ hoặc tích hợp đặc thù.

Mục tiêu thực tế là giữ **đồ thị đối tượng dễ khám phá**. Scan mọi package chỉ vì tiện có thể làm quyền sở hữu mơ hồ; khai báo mọi service đơn giản bằng `@Bean` lại tạo quá nhiều nhiễu cấu hình.

Hãy chọn cơ chế nhỏ nhất nhưng vẫn khiến ý định đăng ký rõ với người đọc code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="injection-strategy">Chọn chiến lược dependency injection</a>

<details>
<summary>Xem chi tiết</summary>

Constructor injection là lựa chọn mặc định cho dependency bắt buộc vì object không thể tồn tại ở trạng thái thiếu đối tượng cộng tác và yêu cầu dependency hiện rõ trong Java.

Setter hoặc method injection phù hợp hơn với đối tượng cộng tác tùy chọn/có thể cấu hình lại. Field injection ngắn nhưng che giấu yêu cầu khởi tạo và khiến việc kiểm thử object thuần ngoài container khó hơn.

Nếu dependency thực sự tùy chọn hoặc cần lấy muộn, hãy mô hình hóa điều đó bằng `Optional`, `ObjectProvider`, collection hoặc abstraction phù hợp thay vì bắt exception từ lookup.

Khi có nhiều candidate, giải quyết sự mơ hồ ở ranh giới ghép nối bằng qualifier có nghĩa hoặc một primary rõ ràng. Không nên đẩy bean-name string xuống mã nghiệp vụ chỉ để quá trình phân giải thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scope-lifecycle-design">Thiết kế scope và lifecycle</a>

<details>
<summary>Xem chi tiết</summary>

Scope trả lời **một bean identity được tái sử dụng trong bao lâu**; lifecycle trả lời **điều gì phải xảy ra khi identity đó sẵn sàng rồi dừng hoạt động**. Hai quyết định này nên được thiết kế cùng nhau.

Singleton là mặc định hợp lý cho service stateless và hạ tầng dùng chung. Không nên nhét dữ liệu mutable theo request/operation vào singleton. Khi singleton cần đối tượng cộng tác có scope ngắn hơn, scoped proxy hoặc deferred lookup giúp quá trình phân giải xảy ra tại đúng ranh giới scope.

Initialization nên thiết lập resource/invariant không thể biểu diễn chỉ bằng constructor injection. Destruction nên giải phóng resource do bean sở hữu. Riêng prototype bean không được container quản lý đầy đủ giai đoạn hủy sau khi instance đã được giao cho bên gọi.

Đừng biến lifecycle callback thành bộ máy điều phối thứ hai. Nó phục vụ việc làm hạ tầng sẵn sàng, không nên che giấu luồng điều phối nghiệp vụ thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="environment-context-design">Thiết kế Environment và các dịch vụ ApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

`Environment` và `ApplicationContext` giải quyết hai phần khác nhau của việc ghép nối. `Environment` cung cấp profiles và property sources để quyết định/tham số hóa cấu hình; `ApplicationContext` sở hữu đồ thị bean kết quả cùng các dịch vụ như resource loading, events và message resolution.

Nên giữ quyết định cấu hình gần giai đoạn bootstrap. Profile có thể quyết định một bean hạ tầng có tồn tại hay không; không nên dùng profile thay cho rẽ nhánh nghiệp vụ lúc runtime.

Tương tự, hãy inject abstraction hẹp mà class thật sự cần (`ResourceLoader`, `MessageSource`, `ApplicationEventPublisher`) thay vì cả `ApplicationContext`. Dependency hẹp làm ý định rõ hơn và giảm mức phụ thuộc vào container.

Spring Boot bổ sung externalized configuration và auto-configuration ở tầng cao hơn, nhưng các cơ chế đó sử dụng nền tảng Framework này chứ không thay thế nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="startup-failure-diagnosis">Chẩn đoán lỗi startup và dependency</a>

<details>
<summary>Xem chi tiết</summary>

Khi startup lỗi, hãy phân loại **pha của container** trước khi thử thay annotation ngẫu nhiên.

```text
không có bean definition / scan sai phạm vi
→ lỗi registration

nhiều bean cùng hợp lệ
→ lỗi candidate selection

constructor cycle hoặc scope mismatch
→ lỗi thiết kế dependency graph

exception từ init callback
→ lỗi lifecycle/initialization

bean bị tạo quá sớm và bỏ lỡ proxy/hạ tầng
→ lỗi extension-point/bootstrap

sai profile/property value
→ lỗi Environment/cấu hình
```

Đọc nguyên nhân sâu nhất có liên quan và bean name/type trong chuỗi exception, rồi lần ngược về metadata hoặc dependency edge đã tạo ra lỗi.

Mục tiêu không phải thuộc mọi exception class mà là biết pha nào của container đang chịu trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="neighboring-module-handoffs">Điểm chuyển sang các Spring Framework modules lân cận</a>

<details>
<summary>Xem chi tiết</summary>

Core Container sở hữu việc ghép nối object và hạ tầng context chung. Nhiều khả năng quan trọng của Spring xây trên nền đó nhưng có mô hình tư duy riêng:

- **validation-data-binding**: `Validator`, `DataBinder`, conversion, formatting và Bean Validation integration.
- **aspect**: ngữ nghĩa proxy/AOP vượt quá phần nhỏ cần để giải thích hạ tầng container.
- **transaction-management**: transaction policy, propagation, rollback và synchronization.
- **cache**: Spring Cache abstraction và ngữ nghĩa declarative caching.
- **web/reactive**: lifecycle xử lý request của MVC/WebFlux.
- **messaging/jms**: message-oriented flow và broker-facing abstractions.
- **testing**: Spring TestContext và framework-level test integration.

Một quy tắc đơn giản: nếu câu hỏi chủ yếu là **object vào container thế nào, được ghép nối ra sao và sống trong container như thế nào**, nó thuộc module này. Nếu câu hỏi là ngữ nghĩa của một subsystem Spring cấp cao hơn, hãy chuyển sang module sở hữu subsystem đó.

</details>

- [Quay lại đầu trang](#back-to-top)
