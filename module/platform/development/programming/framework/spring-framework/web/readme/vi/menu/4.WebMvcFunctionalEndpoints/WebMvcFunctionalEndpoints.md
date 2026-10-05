<a id="back-to-top"></a>

# Endpoint chức năng với WebMvc.fn

## Menu
- [Mental model của WebMvc.fn](#webmvc-fn-mental-model)
- [Router Function và Request Predicate](#router-functions-and-request-predicates)
- [Handler Function với ServerRequest và ServerResponse](#handler-functions-request-response)
- [Handler Filter và composition của route](#handler-filter-functions)
- [Annotated và Functional Endpoint cùng tồn tại](#annotated-and-functional-coexistence)

## <a id="webmvc-fn-mental-model">Mental model của WebMvc.fn</a>

<details>
<summary>Xem chi tiết</summary>

WebMvc.fn là functional programming model của Spring MVC cho Servlet environment. Thay vì đặt mapping annotation lên controller method, route và handler được biểu diễn trực tiếp bằng Java function cùng request/response abstraction.

```text
RequestPredicate
→ RouterFunction
→ HandlerFunction
→ ServerResponse
```

`RouterFunction` quyết định `HandlerFunction` nào xử lý `ServerRequest`; handler trả `ServerResponse`. Spring MVC nối model này với `DispatcherServlet` qua functional handler mapping/adapter.

Đây vẫn là Spring MVC trên Servlet. "Functional" mô tả cách viết route/handler, không có nghĩa chuyển sang WebFlux.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="router-functions-and-request-predicates">Router Function và Request Predicate</a>

<details>
<summary>Xem chi tiết</summary>

`RequestPredicate` là test tái sử dụng trên `ServerRequest`: HTTP method, path, header, accepted content và các thuộc tính request khác có thể tham gia. `RouterFunction` đánh giá predicate rồi trả handler nếu route match.

Route có thể compose và nest, nên path prefix hoặc điều kiện dùng chung được biểu diễn rõ trong code:

```java
RouterFunction<ServerResponse> routes() {
    return route()
        .GET("/users/{id}", this::findUser)
        .POST("/users", this::createUser)
        .build();
}
```

Ordering vẫn quan trọng. Predicate rộng đặt quá sớm có thể che route cụ thể hơn. Route composition nên đủ rõ để maintainer biết handler nào thắng mà không phải "chạy bằng đầu" một biểu thức quá lớn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handler-functions-request-response">Handler Function với ServerRequest và ServerResponse</a>

<details>
<summary>Xem chi tiết</summary>

`HandlerFunction` là application handler của functional model. Nó nhận `ServerRequest`, đọc giá trị cần thiết, gọi application logic rồi trả `ServerResponse`.

`ServerRequest` cho truy cập path variable, query parameter, header, cookie, attribute, session và body extraction. `ServerResponse` dùng builder-style API để mô tả status, header, content type, body hoặc rendering.

```text
annotated controller
→ resolver suy ra parameter từ method signature

HandlerFunction
→ handler đọc rõ ràng từ ServerRequest
```

Explicitness này hữu ích nhưng handler không nên lặp parsing/binding logic ở nhiều nơi; phần dùng chung nên nằm trong helper hoặc application service phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handler-filter-functions">Handler Filter và composition của route</a>

<details>
<summary>Xem chi tiết</summary>

`HandlerFilterFunction` bọc một functional handler và có thể chạy logic trước/sau handler. Nó phù hợp với concern giới hạn trong một route tree chức năng.

Filter có thể compose cùng router function, giúp một nhóm route dùng chung behavior mà không phải dùng Servlet-wide filter hay MVC-wide interceptor.

```text
Servlet Filter
→ toàn Servlet dispatch

HandlerInterceptor
→ lifecycle của mapped MVC handler

HandlerFilterFunction
→ composition của functional route/handler
```

Không xây lại authentication/authorization trong ad-hoc handler filter khi Spring Security là owner của concern đó. Route filter nên dùng cho behavior cục bộ của functional endpoint.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotated-and-functional-coexistence">Annotated và Functional Endpoint cùng tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

Annotated controller và WebMvc.fn có thể cùng chạy vì mỗi programming model có mapping/adapter riêng dưới cùng một `DispatcherServlet`.

Điều này cho phép adoption dần dần: phần lớn API có thể dùng annotated controller, còn một subsystem thích composition có thể dùng functional route.

Tuy nhiên coexistence tạo câu hỏi ownership route. Không nên map cùng HTTP contract bằng cả hai model rồi để handler-mapping order quyết định behavior.

Lựa chọn nằm ở cách biểu diễn và composition, không phải model nào "mạnh hơn". Annotated MVC có method-argument/binding integration rất phong phú; functional MVC explicit hơn trong request extraction và route composition.

</details>

- [Quay lại đầu trang](#back-to-top)
