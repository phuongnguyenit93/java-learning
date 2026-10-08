<a id="back-to-top"></a>

# Properties dành cho test và `@TestConfiguration`

## Menu
- [Vì sao cần tùy biến Boot test context cho một kịch bản cụ thể?](#test-context-customization-purpose)
- [Properties trên annotation test của Boot ghi đè cấu hình cục bộ như thế nào?](#springboottest-properties)
- [`@TestConfiguration` giải quyết vấn đề gì?](#testconfiguration-purpose)
- [Bổ sung cấu hình chỉ dành cho test mà không thay thế cấu hình chính của ứng dụng như thế nào?](#testconfiguration-import)
- [Khác biệt về property và configuration có thể làm phân mảnh khả năng tái sử dụng context như thế nào?](#test-property-cache-impact)
- [Giá trị ghi đè riêng cho test bàn giao sang externalized configuration và Spring TestContext ở đâu?](#test-configuration-ownership-boundary)

## <a id="test-context-customization-purpose">Vì sao cần tùy biến Boot test context cho một kịch bản cụ thể?</a>

<details>
<summary>Xem chi tiết</summary>
Test đôi khi cần một biến thể có kiểm soát của application: bật feature flag, rút ngắn timeout, thay external endpoint hoặc thêm bean chỉ dành cho test. Boot cung cấp cơ chế tùy biến cục bộ để các thay đổi này nằm trong phạm vi test thay vì rò vào cấu hình application thông thường.

Hãy dùng tùy biến nhỏ nhất đủ diễn đạt kịch bản. Mỗi property hoặc configuration class bổ sung cũng có thể ảnh hưởng khả năng tái sử dụng context, nên tùy biến test là một phần của thiết kế bộ test.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="springboottest-properties">Properties trên annotation test của Boot ghi đè cấu hình cục bộ như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Annotation test của Boot như `@SpringBootTest` có thuộc tính `properties` cho các property riêng của test được khai báo trực tiếp. Những giá trị này được thêm vào test environment của context tương ứng và có thể ghi đè application configuration thông thường trong kịch bản đó.

```java
@SpringBootTest(properties = "feature.checkout.enabled=true")
class CheckoutIntegrationTest {
}
```

Cách này thuận tiện cho các giá trị ghi đè cục bộ nhỏ. Mô hình precedence/binding rộng hơn vẫn thuộc externalized configuration và hỗ trợ property source của Spring TestContext.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testconfiguration-purpose">`@TestConfiguration` giải quyết vấn đề gì?</a>

<details>
<summary>Xem chi tiết</summary>
`@TestConfiguration` đánh dấu configuration chỉ dành cho test. Khác với `@Configuration` lồng nhau có thể trở thành cấu hình chính của test, `@TestConfiguration` lồng nhau được thêm bên cạnh cấu hình chính thông thường của application.

Nó phù hợp cho bean chỉ dành cho test, infrastructure adapter thay thế hoặc phần hỗ trợ có thể tái sử dụng nhưng không bao giờ nên được phát hiện như production configuration thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testconfiguration-import">Bổ sung cấu hình chỉ dành cho test mà không thay thế cấu hình chính của ứng dụng như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
`@TestConfiguration` lồng nhau được kết hợp với cấu hình Boot chính trong cách tổ chức test. `@TestConfiguration` cấp cao nhất có thể được import tường minh bằng `@Import` khi nhiều test cần dùng chung phần hỗ trợ đó.

Điểm quan trọng là mục đích bổ sung: test configuration bổ sung cho mô hình application production thay vì âm thầm trở thành cấu hình chính mới.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-property-cache-impact">Khác biệt về property và configuration có thể làm phân mảnh khả năng tái sử dụng context như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Spring TestContext chỉ tái sử dụng context khi cấu hình test thực tế trùng khớp. Property khai báo trực tiếp, profile, imported configuration, mock và các bộ tùy biến context khác nhau có thể tạo ra cache key khác nhau.

Vì vậy nhiều biến thể configuration riêng lẻ có thể khiến bộ test phải khởi động lại nhiều Boot context gần giống nhau. Hãy ưu tiên test configuration dùng chung và tập property ổn định khi chúng mô tả cùng một ranh giới kịch bản.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-configuration-ownership-boundary">Giá trị ghi đè riêng cho test bàn giao sang externalized configuration và Spring TestContext ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>
Boot sở hữu các thuộc tính tiện ích và phần tích hợp `@TestConfiguration` được annotation test của Boot sử dụng. Externalized configuration sở hữu property source thông thường, precedence, profile, binding và ngữ nghĩa `@ConfigurationProperties`.

Spring TestContext sở hữu test property source tổng quát, đăng ký dynamic property, tùy biến context và cache context. Module này giải thích cách Boot dùng các khả năng đó nhưng không định nghĩa lại cơ chế bên dưới.

</details>

- [Quay lại đầu trang](#back-to-top)
