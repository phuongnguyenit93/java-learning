<a id="back-to-top"></a>

# Cấu hình MVC và Extension Point

## Menu
- [@EnableWebMvc và hạ tầng MVC](#enable-web-mvc)
- [WebMvcConfigurer](#web-mvc-configurer)
- [Cấu hình Path Matching](#path-matching-configuration)
- [Cấu hình conversion và validation](#conversion-validation-configuration)
- [Cấu hình Message Converter và Content Negotiation](#message-converter-content-negotiation-configuration)
- [Cấu hình interceptor, CORS, resource và view](#interceptor-cors-resource-view-configuration)
- [Cấu hình Async Support](#async-support-configuration)
- [Custom MVC SPI](#custom-mvc-spis)
- [Framework default, customization và ranh giới Spring Boot](#framework-defaults-vs-customization)

## <a id="enable-web-mvc">@EnableWebMvc và hạ tầng MVC</a>

<details>
<summary>Xem chi tiết</summary>

`@EnableWebMvc` bật hạ tầng Java configuration của Spring MVC trong một application context. Về mặt mental model, annotation này import cấu hình đăng ký các thành phần MVC trung tâm: handler mapping/adapter, conversion và validation support, message conversion, exception resolver, resource support và những collaborator mà `DispatcherServlet` cần.

Nó không có nghĩa là "bật component scanning cho controller". Việc tìm bean và component scanning thuộc Spring container. `@EnableWebMvc` tạo hạ tầng MVC biết cách diễn giải controller cùng các contract cấu hình web.

Khi dùng Spring Framework trực tiếp, annotation này phù hợp nếu ứng dụng muốn dùng default của MVC rồi tự cấu hình có chủ đích. Trong Spring Boot, thêm `@EnableWebMvc` thường là một lựa chọn mạnh hơn: ứng dụng chuyển sang tự kiểm soát phần lớn MVC configuration thay vì đi theo Boot MVC auto-configuration thông thường, nên không nên thêm chỉ theo thói quen.

```text
Spring container
→ @EnableWebMvc import MVC configuration support
→ các MVC infrastructure bean được tạo
→ WebMvcConfigurer tùy biến hạ tầng đó
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-mvc-configurer">WebMvcConfigurer</a>

<details>
<summary>Xem chi tiết</summary>

`WebMvcConfigurer` là extension contract ưu tiên khi hạ tầng MVC chuẩn đã phù hợp nhưng ứng dụng cần điều chỉnh một số policy. Các default method của interface cho phép configuration class đóng góp cấu hình mà không phải subclass cấu hình trung tâm của Framework.

Các callback bao phủ formatter, validator, path matching, content negotiation, message converter, argument/return-value handler, interceptor, CORS, resource, view, exception resolver và async processing. Có thể có nhiều configurer cùng đóng góp; không nên hình dung đây là một global mutable object duy nhất.

Điểm quan trọng là phân biệt **mở rộng** và **thay thế**. `addFormatters` có semantics bổ sung khá rõ. Một số callback kiểu `configure...` có thể kiểm soát list/policy sâu hơn. Trước khi clear hoặc thay default, cần đọc đúng contract của callback đó.

Nên ưu tiên `WebMvcConfigurer` hơn việc sao chép cấu hình nội bộ của Framework vào application code. Chỉ hạ xuống MVC bean/SPI thấp hơn khi extension contract hiện có thực sự không diễn đạt được yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="path-matching-configuration">Cấu hình Path Matching</a>

<details>
<summary>Xem chi tiết</summary>

Path matching nối request path tới mapping của controller, functional route, resource và các MVC handler khác. Vì vậy thay đổi cấu hình path là thay đổi routing semantics của toàn ứng dụng chứ không chỉ một controller.

`PathMatchConfigurer` là hook trung tâm qua `WebMvcConfigurer.configurePathMatch`. Spring MVC hiện đại có thể dùng parsed `PathPattern` qua `PathPatternParser`; mô hình `PathMatcher` trên chuỗi vẫn tồn tại cho compatibility. Parsed pattern được thiết kế riêng cho web routing và tránh một số điểm mơ hồ của string matching tổng quát.

Các lựa chọn về trailing slash, parser option, path prefix hay matcher strategy có thể làm thay đổi handler thắng hoặc khiến request không còn match. Vì vậy đây là policy cần được quyết định rõ và có routing test.

Không nên nhồi versioning, tenant hay business policy vào pattern ngày càng phức tạp khi một boundary domain rõ ràng sẽ dễ hiểu hơn. "Có thể match được" không đồng nghĩa với API design dễ bảo trì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="conversion-validation-configuration">Cấu hình conversion và validation</a>

<details>
<summary>Xem chi tiết</summary>

MVC dùng conversion và validation như hạ tầng dùng chung khi bind request value vào handler argument và model object. `WebMvcConfigurer.addFormatters` thêm converter/formatter của ứng dụng vào MVC conversion service; cấu hình validation quyết định validator mà controller processing sử dụng khi được yêu cầu.

Đây là integration point, không phải owner chính của `ConversionService`, `Formatter`, `Validator` hay Bean Validation. Các abstraction nền tảng đó thuộc module validation/data-binding. Ở đây câu hỏi quan trọng là MVC nối chúng vào request lifecycle như thế nào.

Global formatter phù hợp cho convention representation cần thống nhất giữa nhiều controller, chẳng hạn domain identifier hoặc date format. Nếu rule chỉ áp dụng cục bộ cho một controller thì `@InitBinder` thường là boundary rõ hơn để tránh làm thay đổi conversion rule toàn ứng dụng.

Tương tự, thay MVC validator có thể tác động rộng tới controller processing. Nên ưu tiên composition và constraint rõ ràng thay vì đưa policy riêng của một endpoint vào global validator.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-converter-content-negotiation-configuration">Cấu hình Message Converter và Content Negotiation</a>

<details>
<summary>Xem chi tiết</summary>

Message converter và content negotiation phối hợp để quyết định Java value gặp HTTP representation như thế nào. Danh sách `HttpMessageConverter` quyết định loại Java/media type nào có thể đọc hoặc ghi; content-negotiation policy ảnh hưởng representation nào được chọn cho request.

`WebMvcConfigurer.configureMessageConverters` là hook mạnh: khi thêm converter qua callback này, việc đăng ký default converter vốn sẽ diễn ra mặc định bị tắt. `extendMessageConverters` thường an toàn hơn khi mục tiêu chỉ là thêm hoặc điều chỉnh danh sách converter đã được cấu hình thay vì thay default.

Content negotiation cần có hành vi dễ dự đoán. Negotiation qua header `Accept` là baseline tự nhiên của HTTP. Nếu bật thêm strategy khác, client phải hiểu được contract đó và ứng dụng phải tránh việc một strategy vô tình chọn representation không mong muốn.

Thứ tự converter cũng quan trọng khi nhiều converter cùng có thể xử lý một giá trị. Custom converter quá rộng có thể che mất JSON, text, form, resource hoặc byte-array converter chuyên biệt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interceptor-cors-resource-view-configuration">Cấu hình interceptor, CORS, resource và view</a>

<details>
<summary>Xem chi tiết</summary>

`WebMvcConfigurer` gom nhiều hook vì chúng cùng mở rộng request-processing graph của MVC:

- `addInterceptors` thêm MVC handler interceptor;
- `addCorsMappings` đóng góp CORS policy theo path;
- `addResourceHandlers` cấu hình resource location và resource chain;
- các callback về view đăng ký hoặc điều chỉnh view resolution.

Chúng không thay thế lẫn nhau. Interceptor chạy quanh mapped MVC handler; CORS mapping điều khiển cross-origin policy; resource handler sở hữu resource delivery; view resolver biến logical view outcome thành rendering implementation.

Nên đặt concern ở layer hẹp nhất nhưng đúng. Ví dụ không tự thêm CORS header bằng interceptor khi MVC đã có CORS contract, và không tạo controller để phục vụ static resource nếu resource handler đã diễn đạt được yêu cầu.

Global configuration phù hợp cho policy thực sự dùng chung. Nếu behavior thay đổi theo từng endpoint, ưu tiên local contract của feature đó để tránh biến cấu hình toàn cục thành dependency ẩn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-support-configuration">Cấu hình Async Support</a>

<details>
<summary>Xem chi tiết</summary>

Async MVC có cấu hình hạ tầng ngoài các return type của controller. `WebMvcConfigurer.configureAsyncSupport` cung cấp `AsyncSupportConfigurer`, nơi ứng dụng có thể chọn `AsyncTaskExecutor`, đặt default timeout và đăng ký interceptor cho callable/deferred-result processing.

Điều này quan trọng vì trả về `Callable` không tự tạo execution capacity. Executor là một phần của hành vi production: queue, concurrency, rejection, thread naming, context propagation và shutdown đều tác động tới request lifecycle.

Timeout cũng là policy chứ không chỉ là một con số. Khi timeout xảy ra, application cần kết thúc công việc nhất quán, đi qua response/error path phù hợp và không bỏ lại external operation không còn owner. Servlet-container async timeout và Spring MVC async processing cần được hiểu cùng nhau.

Đây vẫn là executor cho async work trong Servlet stack, không phải event-loop model của WebFlux.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-mvc-spis">Custom MVC SPI</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC cung cấp các SPI cho trường hợp programming model chuẩn cần một integration point mới. Ví dụ phổ biến gồm custom `HandlerMethodArgumentResolver`, `HandlerMethodReturnValueHandler`, `HandlerExceptionResolver`, `HandlerMapping` hoặc `HandlerAdapter`.

Hãy chọn SPI hẹp nhất đúng với bài toán. Custom argument resolver hợp lý cho một abstraction parameter dùng lại giữa nhiều controller; thay cả handler adapter chỉ để giải quyết bài toán đó sẽ rộng hơn rất nhiều và kéo application gần vào orchestration nội bộ của MVC.

Các extension point thường tham gia vào ordered chain. Thứ tự quyết định custom component có cơ hội xử lý trước hay sau built-in component. Resolver claim quá nhiều parameter type hoặc exception có thể vô tình che behavior mặc định của Framework.

Custom SPI nên giữ các contract của MVC như nullability, thứ tự binding/validation, trạng thái response đã commit, async behavior và exception propagation. Cần test cả path được xử lý lẫn path "không hỗ trợ, chuyển cho component tiếp theo".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="framework-defaults-vs-customization">Framework default, customization và ranh giới Spring Boot</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework cung cấp MVC default và extension contract; Spring Boot quyết định cách auto-configure ứng dụng xung quanh các contract đó. Tách hai vai trò này giúp tránh việc đọc một Boot property rồi nhầm nó thành Spring Framework API.

Ở tầng Framework, `@EnableWebMvc`, `WebMvcConfigurer`, MVC bean và callback contract là cơ chế chính. Boot có thể tạo hoặc customize các thành phần đó có điều kiện, cấu hình embedded server, áp dụng configuration property và đăng ký thêm infrastructure.

Khi ứng dụng Boot chỉ cần **tùy biến** MVC, đóng góp một `WebMvcConfigurer` thường vẫn tương thích với auto-configuration của Boot. Thêm `@EnableWebMvc` là lựa chọn mạnh hơn vì nó biểu thị ứng dụng muốn trực tiếp kiểm soát MVC configuration thay vì đi theo Boot MVC auto-configuration thông thường.

```text
Spring Framework contract
→ MVC có thể làm gì

Spring Boot auto-configuration
→ những Framework component nào được tạo/cấu hình tự động

application configuration
→ policy cục bộ có chủ đích
```

Phân biệt ba layer này giúp việc debug configuration dễ hơn rất nhiều.

</details>

- [Quay lại đầu trang](#back-to-top)
