<a id="back-to-top"></a>

# DispatcherServlet và vòng đời request MVC

## Menu
- [DispatcherServlet là Front Controller](#dispatcher-servlet-front-controller)
- [Bootstrap DispatcherServlet và phân cấp WebApplicationContext](#dispatcher-bootstrap-and-context-hierarchy)
- [HandlerMapping và HandlerExecutionChain](#handler-mapping-and-execution-chain)
- [HandlerAdapter, handler invocation và xử lý return value](#handler-invocation-and-return-value-processing)
- [Vị trí exception resolution trong dispatch flow](#exception-resolution-position)
- [Mô hình strategy của DispatcherServlet](#dispatcher-strategies-overview)

## <a id="dispatcher-servlet-front-controller">DispatcherServlet là Front Controller</a>

<details>
<summary>Xem chi tiết</summary>

`DispatcherServlet` là front controller của Spring MVC. Thay vì mỗi feature có một servlet riêng, Servlet container chuyển request MVC tới servlet trung tâm này rồi nó delegate từng stage cho strategy chuyên trách.

Vai trò của nó là orchestration, không phải business logic. Mỗi dispatch cần tìm handler, chọn adapter biết cách gọi handler đó, chuẩn bị request attribute của MVC, xử lý kết quả, resolve exception và render/ghi response.

```text
request
→ DispatcherServlet
→ mapping
→ invocation
→ xử lý result/error
→ response
```

Controller không gọi `DispatcherServlet`; controller được MVC infrastructure gọi sau khi dispatch đã chọn đúng handler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dispatcher-bootstrap-and-context-hierarchy">Bootstrap DispatcherServlet và phân cấp WebApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

`DispatcherServlet` chạy với một `WebApplicationContext`. Trong Servlet deployment truyền thống, servlet có thể tạo child web context riêng. Ứng dụng cũng có thể có root `WebApplicationContext` tùy chọn làm parent.

```text
root context (tùy chọn)
    ↓ visible to
DispatcherServlet child context
```

Bean trong child có thể nhìn bean ở parent; parent không nhìn thấy bean chỉ tồn tại ở child. Nhờ đó shared service có thể nằm ở root context còn MVC infrastructure/controller dành riêng cho servlet nằm trong child context.

Spring Boot thường ẩn phần registration ceremony, nhưng visibility rule vẫn quan trọng khi debug duplicate bean, thiếu MVC infrastructure hoặc application có nhiều servlet.

Không nên giả định root context là bắt buộc. `DispatcherServlet` có thể vận hành với context riêng tùy cấu trúc ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handler-mapping-and-execution-chain">HandlerMapping và HandlerExecutionChain</a>

<details>
<summary>Xem chi tiết</summary>

`HandlerMapping` trả lời câu hỏi routing đầu tiên của MVC: **request này do gì xử lý?** Có thể có nhiều mapping, mỗi mapping sở hữu một programming model hoặc handler family.

Với annotated controller, `RequestMappingHandlerMapping` match request mapping và trả về `HandlerMethod`. Functional MVC dùng router-function mapping. Resource và handler type khác có mapping riêng.

Kết quả được đặt trong `HandlerExecutionChain`, gồm handler và các MVC interceptor áp dụng cho request đó. `preHandle` chạy trước handler; các callback sau tham gia theo MVC lifecycle.

Ordering có ý nghĩa khi nhiều mapping có khả năng nhận cùng request. Custom mapping nên chỉ claim request nó thật sự hiểu; mapping quá rộng có thể che behavior mặc định của MVC.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handler-invocation-and-return-value-processing">HandlerAdapter, handler invocation và xử lý return value</a>

<details>
<summary>Xem chi tiết</summary>

Tìm được handler chưa đủ vì MVC hỗ trợ nhiều handler shape. `HandlerAdapter` tách `DispatcherServlet` khỏi chi tiết gọi từng loại handler.

`RequestMappingHandlerAdapter` gọi annotated `HandlerMethod`. Nó phối hợp argument resolver, data binding, validation, controller invocation và return-value handler. Functional endpoint có handler-function adapter riêng.

```text
HandlerMethod
→ resolve method argument
→ tạo/bind/validate model value khi cần
→ gọi controller method
→ chọn return-value handler
→ tạo ModelAndView hoặc response-body outcome
```

Vì vậy custom argument resolver hay return-value handler có thể thay cách MVC gọi controller mà không cần custom `DispatcherServlet`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-resolution-position">Vị trí exception resolution trong dispatch flow</a>

<details>
<summary>Xem chi tiết</summary>

Exception có thể xảy ra trong handler mapping và xuyên suốt handler execution, bao gồm argument resolution, binding, controller invocation và return-value processing. Với failure trong phần request processing này, `DispatcherServlet` cho các `HandlerExceptionResolver` đã cấu hình cơ hội theo thứ tự để chuyển exception thành MVC response outcome.

Exception resolution là một phần của dispatch lifecycle, không phải một global `try/catch` tách rời. `@ExceptionHandler`, status mapping và Framework default exception translation đều tham gia thông qua resolver tương ứng.

Nếu exception được resolve, MVC có thể render model/view hoặc ghi error response. Nếu không resolver nào xử lý, exception tiếp tục quay về Servlet container. Exception phát sinh muộn hơn trong chính quá trình view rendering nằm ngoài handler-exception-resolution step này và tiếp tục theo Servlet error path thay vì được đưa ngược lại resolver chain.

Khi debug cần hỏi failure xảy ra trước khi chọn handler, trong controller processing hay sau khi response đã bắt đầu. Stage đó quyết định resolver còn có thể thay đổi gì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dispatcher-strategies-overview">Mô hình strategy của DispatcherServlet</a>

<details>
<summary>Xem chi tiết</summary>

`DispatcherServlet` dựa trên strategy interface thay vì một pipeline hard-code. Các nhóm strategy cốt lõi gồm handler mapping, handler adapter, exception resolver, view resolver, locale resolution, multipart resolution, flash-map management và các collaborator liên quan.

Servlet khởi tạo các bean strategy đã cấu hình và dùng Framework default khi phù hợp. Nhờ đó có thể thay/mở rộng một stage mà không viết lại dispatch.

Hai quy tắc quan trọng:

1. mỗi strategy nên sở hữu một stage rõ ràng;
2. với strategy family dạng chain, ordering là một phần của behavior.

Không nên thay low-level strategy khi `WebMvcConfigurer`, argument resolver hoặc interceptor đã giải quyết được yêu cầu. Extension hẹp giữ được nhiều default của Framework hơn và dễ nâng cấp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
