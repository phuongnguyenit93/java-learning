<a id="back-to-top"></a>

# Cấu hình ApplicationContext cho test

## Menu
- [Mô hình cấu hình test context](#context-configuration-model)
- [Configuration class và resource location](#configuration-classes-and-locations)
- [Chọn context loader và default behavior](#context-loader-selection)
- [Kế thừa cấu hình và nested test](#configuration-inheritance)
- [Context hierarchy](#context-hierarchy)
- [Cấu hình WebApplicationContext](#web-application-context-configuration)

## <a id="context-configuration-model">Mô hình cấu hình test context</a>

<details>
<summary>Xem chi tiết</summary>

`@ContextConfiguration` mô tả cách TestContext Framework phải dựng test `ApplicationContext`. Annotation này có thể chỉ tới class cấu hình, resource location, context initializer và loader cụ thể.

Annotation không tự nạp context ngay lập tức. Metadata của nó được hợp nhất xuyên qua hệ phân cấp test thành `MergedContextConfiguration`. Mô hình đã hợp nhất vừa được dùng để nạp context, vừa góp phần xác định entry có thể tái sử dụng trong context cache.

Luồng cần nhớ:

```text
metadata trên test
        ↓
MergedContextConfiguration
        ↓
cache lookup
        ↓ cache miss
SmartContextLoader tạo ApplicationContext
```

Vì vậy một khác biệt cấu hình tưởng như nhỏ vẫn có thể làm test không tái sử dụng cùng context.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-classes-and-locations">Configuration class và resource location</a>

<details>
<summary>Xem chi tiết</summary>

Test thường cấu hình context bằng class cấu hình Java:

```java
@ContextConfiguration(classes = TestConfig.class)
class PricingIntegrationTests {
}
```

hoặc bằng resource location như XML/Groovy khi ứng dụng dùng kiểu cấu hình đó:

```java
@ContextConfiguration("classpath:/pricing-test.xml")
class PricingIntegrationTests {
}
```

Khi không khai báo cấu hình, loader được chọn có thể thực hiện cơ chế phát hiện mặc định, ví dụ tìm resource theo quy ước hoặc nested configuration class phù hợp. Đây là **cơ chế phát hiện mặc định của TestContext**, không phải Spring Boot auto-configuration.

Không nên giả định loader nào cũng hỗ trợ mọi cách trộn class và location. Chính `SmartContextLoader` được dùng quyết định dạng metadata nào hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-loader-selection">Chọn context loader và default behavior</a>

<details>
<summary>Xem chi tiết</summary>

Thông thường hãy để TestContext bootstrapper chọn loader. Với test không phải web, Spring thường dùng delegating smart loader để chọn nhánh annotation-config hoặc resource-based từ metadata. Web test dùng biến thể tương ứng có hỗ trợ web.

Có thể chỉ định loader qua `@ContextConfiguration(loader = ...)`, nhưng đây là lựa chọn cấp thấp. Chỉ nên làm vậy khi chiến lược mặc định không biểu diễn được mô hình cấu hình cần test.

`SmartContextLoader` nhận cấu hình test đã hợp nhất, bao gồm active profile, property source, initializer, parent configuration và context customizer. Loader chịu trách nhiệm tạo context hoàn chỉnh; delegate có nhận biết cache mới quyết định có thực sự cần nạp hay không.

Loader tùy biến phù hợp với phần mở rộng framework hơn test ứng dụng thông thường. Test ứng dụng thường dễ đọc hơn nếu dùng các annotation cấu hình chuẩn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-inheritance">Kế thừa cấu hình và nested test</a>

<details>
<summary>Xem chi tiết</summary>

Cấu hình context được kế thừa theo mặc định. Subclass có thể mở rộng cấu hình của superclass thay vì lặp lại toàn bộ. Hai cờ chính của `@ContextConfiguration` là `inheritLocations` và `inheritInitializers`, đều mặc định bật.

Đặt một cờ kế thừa thành `false` sẽ chuyển ngữ nghĩa từ “mở rộng” sang “che cấu hình cha” cho khía cạnh tương ứng. Điều này ảnh hưởng cả đồ thị bean lẫn định danh context trong cache.

Nested test class cũng mặc định kế thừa cấu hình TestContext của enclosing class. Có thể dùng `@NestedTestConfiguration(OVERRIDE)` khi nested test cần cấu hình Spring độc lập.

Nên dùng cơ chế kế thừa khi các subclass thực sự test cùng một phần của ứng dụng và chỉ thêm khác biệt nhỏ. Nên ghi đè khi cấu hình kế thừa làm bằng chứng của test trở nên không rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-hierarchy">Context hierarchy</a>

<details>
<summary>Xem chi tiết</summary>

`@ContextHierarchy` mô hình hóa quan hệ parent/child giữa các `ApplicationContext` trong test. Nó hữu ích khi môi trường production cũng có context phân tầng, ví dụ root context dùng chung và web child context.

Mỗi cấp trong hierarchy có thể có `name`. Khi subclass khai báo cấp có cùng tên, cấu hình có thể được hợp nhất đúng cấp; cũng có thể ghi đè cấp đó thay vì mở rộng.

Quan hệ parent/child là một phần của cấu hình đã hợp nhất nên cũng ảnh hưởng context caching. Child context nhìn thấy bean từ parent; parent không nhìn thấy bean chỉ được khai báo trong child.

Không nên tạo hierarchy chỉ để chia file cấu hình cho đẹp. Hierarchy nên phản ánh một quan hệ application-context thật mà test cần chứng minh.

Một giới hạn quan trọng của Spring 6.1: TestContext AOT processing không hỗ trợ `@ContextHierarchy`. Hạn chế này sẽ được nối lại ở phần AOT phía sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-application-context-configuration">Cấu hình WebApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

`@WebAppConfiguration` cho TestContext biết integration test cần `WebApplicationContext`, không chỉ `ApplicationContext` thông thường.

Annotation này phải kết hợp với `@ContextConfiguration` ở đâu đó trong hệ phân cấp test. Web resource base path mặc định là `src/main/webapp`; có thể chỉ định đường dẫn khác nếu dự án dùng bố cục khác.

Spring tạo hạ tầng test servlet thay vì khởi động servlet container thật. Context kết quả được gắn với mock `ServletContext`, đủ cho `MockMvc` và test web-scoped fixture.

Web resource base path cũng tham gia định danh context. Hai cấu hình test giống nhau nhưng dùng resource base path khác nhau không đại diện cho cùng cache entry.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — @WebAppConfiguration](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/web/WebAppConfiguration.html)

</details>

- [Quay lại đầu trang](#back-to-top)
