<a id="back-to-top"></a>

# Hỗ trợ Web Servlet và phân phối resource

## Menu
- [Multipart Request Resolution](#multipart-request-resolution)
- [Locale Resolution](#locale-resolution)
- [Static resource, ResourceHandler và resource chain](#static-resources-and-resource-chain)
- [Conditional request, Cache-Control, ETag và Last-Modified](#conditional-requests-and-http-caching)
- [Ranh giới Default Servlet](#default-servlet-boundary)

## <a id="multipart-request-resolution">Multipart Request Resolution</a>

<details>
<summary>Xem chi tiết</summary>

Multipart giải quyết một nhu cầu truyền tải cụ thể: một HTTP request có thể chứa đồng thời các field dạng form và một hay nhiều phần dữ liệu nhị phân. Controller không nên tự parse MIME boundary hay tự quản lý file tạm, nên Spring MVC dựa vào Servlet container để parse request rồi cung cấp các abstraction thuận tiện hơn như `MultipartFile` và `Part`.

Trên Servlet stack chuẩn, `StandardServletMultipartResolver` là cầu nối phổ biến của Spring Framework. Để `DispatcherServlet` discovery được resolver, hãy khai báo nó trong servlet application context với bean name theo convention là `multipartResolver`. Resolver nhận biết multipart request và bọc `HttpServletRequest` để argument resolution và data binding của MVC nhìn thấy các part đã được container parse.

Resolver **không** cấu hình giới hạn upload hay chính sách lưu file tạm của Servlet container. Các giới hạn như kích thước file tối đa, kích thước request tối đa và vị trí file tạm vẫn thuộc multipart configuration của Servlet. Vì resolver delegate cho multipart parser của Servlet container nên behavior parse chi tiết cũng có thể khác giữa các container; cần test behavior mà deployment thực sự yêu cầu.

Mental model nên là:

```text
Servlet container
→ parse multipart/form-data theo cấu hình Servlet
→ StandardServletMultipartResolver đưa part vào Spring MVC
→ controller nhận MultipartFile / Part / dữ liệu form thông thường
```

Controller vẫn phải kiểm tra kích thước, media type, tên part mong đợi và các ràng buộc nghiệp vụ. Tên file do client gửi là input không đáng tin cậy; không được ghép trực tiếp nó vào filesystem path. Upload lớn cũng cần giới hạn vận hành rõ ràng vì buffering hoặc file tạm có thể gây áp lực bộ nhớ, đĩa hoặc denial-of-service.

Multipart phù hợp khi một request thực sự cần kết hợp dữ liệu có cấu trúc với binary. Với upload rất lớn hoặc cần resumable upload, một thiết kế storage/upload chuyên biệt thường phù hợp hơn việc đưa toàn bộ luồng qua MVC controller.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locale-resolution">Locale Resolution</a>

<details>
<summary>Xem chi tiết</summary>

Locale resolution trả lời câu hỏi trình bày: request hiện tại nên dùng `Locale` nào để format số, ngày tháng, message và các output đã bản địa hóa? Spring MVC biểu diễn quyết định này qua `LocaleResolver` hoặc `LocaleContextResolver` khi cần context phong phú hơn.

Mỗi strategy thể hiện một ownership khác nhau. Resolver dựa trên header lấy locale từ request; resolver dựa trên cookie hoặc session giữ một preference do ứng dụng đã chọn trước đó. Locale suy ra từ request không đồng nghĩa với một user preference có thể thay đổi và lưu lại.

Locale được chọn đi vào request context của MVC, sau đó có thể được formatter, message lookup, view và controller sử dụng. `LocaleChangeInterceptor` có thể đọc một request parameter như yêu cầu đổi locale, nhưng thao tác đổi chỉ có ý nghĩa khi resolver hiện tại hỗ trợ cập nhật trạng thái locale. Không nên giả định resolver nào cũng có thể ghi.

Locale thông thường là preference phục vụ rendering. Không dùng nó như bằng chứng về quốc gia của người dùng, jurisdiction pháp lý hay dữ liệu profile đã được xác thực.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="static-resources-and-resource-chain">Static resource, ResourceHandler và resource chain</a>

<details>
<summary>Xem chi tiết</summary>

Các static resource như JavaScript, CSS, ảnh hay font không cần semantics của controller method, nhưng vẫn cần routing, resource lookup, cache header và đôi khi cả versioned URL. Spring MVC phục vụ nhóm request này qua resource handler thay vì gọi annotated controller.

`WebMvcConfigurer.addResourceHandlers` đăng ký URL pattern cùng một hay nhiều resource location. Request được xử lý bởi `ResourceHttpRequestHandler`: component này resolve Spring `Resource`, kiểm tra điều kiện request, xác định media type và ghi resource vào response.

Resource chain tùy chọn cho phép ghép các `ResourceResolver` và `ResourceTransformer` theo thứ tự. Nó hữu ích khi cần content-versioned filename, biến thể đã encode, WebJar resolution hoặc transform reference. Ví dụ, `VersionResourceResolver` giúp đưa URL có version vào contract phân phối resource thay vì bắt application controller tự tính hash.

Nên tách rõ **resource delivery** khỏi application request handling:

```text
/api/orders/**  → application handler
/assets/**      → resource handler
```

Không nên tạo controller chỉ để stream resource từ classpath. Cũng không nên buộc mọi request static đi qua logic ứng dụng nặng nếu không có yêu cầu thực sự; static delivery hiệu quả nhất khi request path, cache policy và resource chain có hành vi dự đoán được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="conditional-requests-and-http-caching">Conditional request, Cache-Control, ETag và Last-Modified</a>

<details>
<summary>Xem chi tiết</summary>

HTTP caching hiệu quả khi server truyền được cả hai thông tin: **representation có thể được tái sử dụng trong bao lâu** và **bản client đang giữ còn mới hay không**. Spring MVC cung cấp helper cho cả freshness lẫn conditional validation thay vì buộc controller tự ghép chuỗi header.

`CacheControl` dùng để xây các directive của `Cache-Control`. Các validator như `ETag` và `Last-Modified` hỗ trợ conditional request. Với conditional `GET` hoặc `HEAD`, validator khớp có thể dẫn tới `304 Not Modified` và không cần gửi response body. Với request thay đổi trạng thái như `POST`, `PUT` hoặc `DELETE`, conditional header mang semantics precondition và có thể dẫn tới `412 Precondition Failed` khi điều kiện không còn đúng.

Mental model:

```text
validator / precondition từ client
→ server so sánh trạng thái representation hiện tại
→ GET/HEAD không đổi: 304, không gửi representation body
→ precondition của request thay đổi trạng thái thất bại: 412
→ trường hợp khác: tiếp tục request processing bình thường
```

`Cache-Control` freshness và conditional validation liên quan nhưng không giống nhau. Freshness có thể giúp không cần gửi request; validator làm revalidation rẻ hơn khi request vẫn phải xảy ra. Không đặt public cache lifetime dài cho response chứa dữ liệu nhạy cảm hoặc theo từng người dùng, và không xem ETag như token xác thực.

Với response động, cache policy là một phần của HTTP representation contract. Nó khác với Spring Cache abstraction dùng để cache dữ liệu hay computation của ứng dụng; phần đó thuộc module cache riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-servlet-boundary">Ranh giới Default Servlet</a>

<details>
<summary>Xem chi tiết</summary>

Servlet container thường có một "default" servlet để phục vụ resource mà ứng dụng không tự xử lý. Khi `DispatcherServlet` được map vào `/`, nó có thể trở thành điểm nhận đầu tiên cho các path đó, nên Spring MVC cung cấp cơ chế handoff có kiểm soát qua `DefaultServletHttpRequestHandler`.

`WebMvcConfigurer.configureDefaultServletHandling` có thể bật mô hình forwarding này. Khi không có MVC handler ưu tiên cao hơn khớp request, Spring có thể forward request sang default servlet của container. Đây là ranh giới tương thích với container, không phải một controller mechanism thứ hai.

Chỉ nên dùng handoff khi deployment thực sự dựa vào default resource handling của container. Nếu Spring resource handler đã sở hữu static resource thì bật thêm default-servlet forwarding có thể khiến routing khó hiểu hơn.

```text
Spring MVC handler/resource
→ Framework sở hữu rõ ràng

default servlet
→ Servlet container sở hữu
```

Spring Boot có thể cung cấp các convention về static resource và servlet registration, nhưng những default đó thuộc Boot chứ không thuộc contract Framework ở đây.

</details>

- [Quay lại đầu trang](#back-to-top)
