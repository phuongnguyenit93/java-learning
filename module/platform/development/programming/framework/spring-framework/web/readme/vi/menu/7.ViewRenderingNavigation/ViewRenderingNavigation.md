<a id="back-to-top"></a>

# Model, View Rendering và Navigation

## Menu
- [Model và View](#model-and-view)
- [View Resolution và logical view name](#view-resolution-and-logical-names)
- [Redirect và Flash Attributes](#redirects-and-flash-attributes)
- [Xây dựng URI](#uri-building)
- [Ranh giới View Technology](#view-technology-boundary)

## <a id="model-and-view">Model và View</a>

<details>
<summary>Xem chi tiết</summary>

Trong server-side MVC rendering, controller thường chuẩn bị **data**, còn `View` biến data đó thành HTTP response. `Model` là tập named value được cung cấp cho view.

Handler có thể trả logical view name và ghi dữ liệu vào `Model`, hoặc trả `ModelAndView` khi cần mang cả hai cùng nhau.

```text
controller
→ model attribute + logical view outcome
→ ViewResolver
→ View
→ rendered response
```

Không đặt business computation vào view layer. View nên format/present dữ liệu đã chuẩn bị, không gọi repository hay mutate domain state.

Đường này khác `@ResponseBody`: response-body handling serialize representation trực tiếp và không đi qua normal MVC view.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="view-resolution-and-logical-names">View Resolution và logical view name</a>

<details>
<summary>Xem chi tiết</summary>

Logical view name tách controller khỏi rendering implementation cụ thể. Các `ViewResolver` chuyển tên như `"orders/detail"` thành `View`.

Nhiều resolver có thể tạo ordered chain. Resolver nên resolve view thuộc phạm vi nó hoặc cho chain tiếp tục; resolver quá rộng luôn trả một view có thể chặn resolver phía sau.

`View` đã resolve render model cùng request/response. Rendering technology thực tế có thể là template, JSP integration, feed, document hoặc cơ chế khác.

Controller thường nên trả semantic/logical name thay vì filesystem path tới template, để layout và rendering technology có thể thay đổi ngoài application behavior.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="redirects-and-flash-attributes">Redirect và Flash Attributes</a>

<details>
<summary>Xem chi tiết</summary>

Redirect yêu cầu client gửi request mới tới URI khác. Trong MVC có thể dùng prefix `redirect:` hoặc `RedirectView`.

Vì request tiếp theo là HTTP request mới, model attribute thông thường không tự trở thành state của request mới. `RedirectAttributes` cho phép controller chọn các redirect-model value để `RedirectView` dùng cho URI-template expansion; những simple value phù hợp còn lại có thể được append thành query parameter. Nó đồng thời hỗ trợ **flash attribute** riêng, không cần encode vào redirect URL.

Flash attribute là short-lived server-side value được quản lý qua `FlashMap`/`FlashMapManager` và thường được giữ tạm trong HTTP session. Với redirect, Spring stamp output `FlashMap` bằng target path và query parameter để default manager match incoming request chính xác hơn. Cơ chế này giảm mạnh khả năng request khác lấy nhầm flash state nhưng không tạo bảo đảm tuyệt đối khi có concurrent request.

```text
POST thành công
→ lưu success message ngắn hạn
→ redirect
→ matching GET thông thường nhận flash attribute
```

Flash state dành cho transition data, không phải business state bền vững hay session cache chung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="uri-building">Xây dựng URI</a>

<details>
<summary>Xem chi tiết</summary>

Xây URI an toàn hơn khi code xem path, variable, query parameter và encoding là component có cấu trúc thay vì nối chuỗi thủ công.

`UriComponentsBuilder` cung cấp builder model tổng quát. Các builder Servlet-aware và MVC utility có thể bắt đầu từ current request hoặc controller mapping khi server-side application cần tạo link tới route của chính nó.

```java
URI uri = UriComponentsBuilder
    .fromPath("/orders/{id}")
    .build(42);
```

Structured building giảm lỗi separator, encoding và template expansion, đồng thời giữ link generation gần route semantics.

Khi chạy sau reverse proxy, scheme/host/port bên ngoài có thể khác connection mà container nhìn thấy. Vì vậy forwarded-header processing là trust boundary; không được tin forwarding header do client tự gửi một cách mù quáng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="view-technology-boundary">Ranh giới View Technology</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC định nghĩa `View` và `ViewResolver` contract nhưng không sở hữu mọi template language/rendering engine. JSP hay template engine bên thứ ba có syntax, cache model, escaping behavior và operational constraint riêng.

Knowledge ở tầng Framework là:

```text
controller outcome
→ logical view resolution
→ View contract
→ rendering technology
```

Chọn rendering technology theo nhu cầu ứng dụng và hiểu security/escaping default của nó, nhưng curriculum đặc thù template nên thuộc chính technology đó.

Với API có public contract JSON/XML, view rendering có thể hoàn toàn không tham gia; message conversion là response path phù hợp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
