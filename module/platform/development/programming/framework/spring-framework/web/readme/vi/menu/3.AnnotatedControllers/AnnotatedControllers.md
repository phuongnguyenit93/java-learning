<a id="back-to-top"></a>

# Annotated Controller và Handler Method

## Menu
- [Mô hình Annotated Controller](#annotated-controller-model)
- [Request Mapping, điều kiện mapping và Path Pattern](#request-mapping-and-conditions)
- [Input của handler method](#handler-method-inputs)
- [Model và session state](#model-and-session-state)
- [Return value của handler và xử lý response trực tiếp](#handler-return-values-and-response-handling)

## <a id="annotated-controller-model">Mô hình Annotated Controller</a>

<details>
<summary>Xem chi tiết</summary>

Annotated controller cho phép Java method đại diện cho HTTP endpoint của ứng dụng, còn Spring MVC xử lý phần orchestration xung quanh. Class có `@Controller` tham gia handler discovery; `@RestController` kết hợp controller semantics với response-body semantics cho các handler method.

Đơn vị trung tâm là `HandlerMethod`: một bean, một Java method và metadata đi kèm. MVC dùng mapping annotation để chọn method, argument resolver để tạo parameter và return-value handler để diễn giải giá trị trả về.

Programming model này mang tính declarative: method mô tả **request shape nào nó xử lý** và **result nào nó tạo**, thay vì tự đọc mọi thứ từ `HttpServletRequest`.

Đổi lại, behavior được phân bố qua annotation và resolver chain. Nếu signature trở nên khó hiểu vì quá nhiều annotation framework-specific, nên đơn giản hóa endpoint contract thay vì tiếp tục thêm rule binding ẩn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-mapping-and-conditions">Request Mapping, điều kiện mapping và Path Pattern</a>

<details>
<summary>Xem chi tiết</summary>

`@RequestMapping` và các composed annotation như `@GetMapping` mô tả điều kiện của request. Điều kiện có thể gồm path, HTTP method, parameter, header, media type được consume và media type được produce.

Spring MVC kết hợp mapping ở type-level với method-level rồi dùng path-matching infrastructure để tìm handler đủ điều kiện và cụ thể nhất. MVC hiện đại hỗ trợ parsed `PathPattern`, phù hợp với HTTP path và variable như `/orders/{id}`.

Mapping nên không mơ hồ. Nếu hai handler method cùng match một request với độ cụ thể ngang nhau thì đó là lỗi thiết kế, không phải cơ chế load balancing của MVC.

Dùng mapping condition để diễn đạt HTTP contract chứ không nhét business decision vào routing. Version/media-type condition có thể là protocol concern; authorization khách hàng thuộc security/application policy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handler-method-inputs">Input của handler method</a>

<details>
<summary>Xem chi tiết</summary>

Parameter của handler method được tạo bởi một chuỗi argument resolver có thứ tự. Vì vậy controller có thể nhận:

- `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@CookieValue`;
- `@RequestBody`, `@ModelAttribute`;
- Servlet request/response type;
- `Principal`, locale, model và các abstraction MVC khác.

Mỗi parameter contract biểu diễn một nguồn dữ liệu khác nhau. `@RequestBody` dùng message converter để decode body; `@ModelAttribute` tham gia data binding; `@PathVariable` lấy giá trị từ path pattern đã match.

Không nên truyền `HttpServletRequest` vào mọi method chỉ vì nó truy cập được tất cả dữ liệu. Argument có kiểu rõ ràng làm endpoint contract dễ đọc và giữ parsing/conversion trong hạ tầng dùng lại được.

Custom `HandlerMethodArgumentResolver` phù hợp khi ứng dụng có parameter abstraction lặp lại, nhưng resolver nên claim type/annotation hẹp để không che built-in resolver.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="model-and-session-state">Model và session state</a>

<details>
<summary>Xem chi tiết</summary>

`Model` của MVC là trạng thái trong request-processing, chủ yếu phục vụ view rendering. Controller có thể thêm attribute, còn method `@ModelAttribute` có thể đóng góp dữ liệu chung trước khi handler chạy.

`@SessionAttributes` khác với việc dùng `HttpSession` tùy ý. Nó yêu cầu MVC đưa một số model attribute vào session qua nhiều request của một conversational flow rồi kết thúc bằng `SessionStatus`.

Cơ chế này hữu ích cho workflow ngắn nhiều bước, nhưng không nên trở thành application cache chung. Session state làm lifecycle phức tạp hơn, ảnh hưởng horizontal scaling và có thể giữ dữ liệu cũ.

Business state bền vững nên ở persistence/domain layer. Dùng model cho request/view state và session-backed model attribute khi web interaction thực sự kéo dài qua nhiều request.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handler-return-values-and-response-handling">Return value của handler và xử lý response trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

Return value của controller được các return-value handler diễn giải, nên Java return type là một phần của MVC contract.

Các outcome phổ biến:

- logical view name hoặc `ModelAndView`;
- object được ghi qua message converter với `@ResponseBody`/`@RestController`;
- `ResponseEntity` khi cần status, header và body rõ ràng;
- redirect/view navigation;
- async/streaming type;
- truy cập trực tiếp Servlet response khi thật sự cần low-level control.

Nên chọn return type ở mức cao nhất vẫn diễn đạt đủ yêu cầu. `ResponseEntity` phù hợp khi HTTP metadata là một phần của endpoint behavior; tự ghi vào `HttpServletResponse` bypass nhiều bước của MVC return-value pipeline và chỉ nên dùng khi cần quyền kiểm soát đó.

Method trả `void` không tự động nghĩa là "không có response"; semantics còn phụ thuộc metadata khác và việc response đã được xử lý hay chưa.

</details>

- [Quay lại đầu trang](#back-to-top)
