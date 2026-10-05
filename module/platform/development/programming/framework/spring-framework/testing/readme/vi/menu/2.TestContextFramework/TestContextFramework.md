<a id="back-to-top"></a>

# Spring TestContext Framework

## Menu
- [Vai trò của TestContext Framework](#testcontext-framework-role)
- [TestContext, TestContextManager và các abstraction cốt lõi](#testcontext-core-abstractions)
- [Bootstrapping TestContext Framework](#testcontext-bootstrap)
- [SmartContextLoader và chiến lược load context](#smart-context-loading)
- [Mô hình TestExecutionListener](#test-execution-listeners)
- [SpringExtension và tích hợp với test framework](#test-framework-integration)

## <a id="testcontext-framework-role">Vai trò của TestContext Framework</a>

<details>
<summary>Xem chi tiết</summary>

TestContext Framework là lớp trung gian giữa test engine tổng quát và hạ tầng do Spring quản lý. Nó không thay thế JUnit hoặc TestNG. Thay vào đó, framework cung cấp các dịch vụ theo vòng đời mà test engine có thể gọi tới: nạp/cache context, dependency injection, xử lý transaction, chạy SQL script, ghi nhận event và các listener mở rộng.

Thiết kế này không phụ thuộc vào một test engine cụ thể. Cùng các abstraction cốt lõi có thể được JUnit Jupiter điều khiển qua `SpringExtension`, hoặc được các lớp tích hợp khác của Spring dùng với JUnit/TestNG.

Có thể xem TestContext như **lớp điều phối** của Spring integration test. Test engine quyết định *khi nào* một giai đoạn vòng đời diễn ra; TestContext quyết định công việc riêng của Spring nào phải chạy trong giai đoạn đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testcontext-core-abstractions">TestContext, TestContextManager và các abstraction cốt lõi</a>

<details>
<summary>Xem chi tiết</summary>

`TestContext` giữ trạng thái kiểm thử hiện tại của Spring: test class, test instance/method hiện tại, exception khi cần, attribute dùng chung giữa listener và quyền truy cập tới test `ApplicationContext`.

`TestContextManager` điều phối vòng đời. Nó giữ `TestContext` đang dùng và gọi các `TestExecutionListener` đã đăng ký quanh những giai đoạn như:

- trước/sau test class;
- chuẩn bị test instance;
- trước/sau test method;
- trước/sau phần thực thi test.

Lớp tích hợp với test engine ủy quyền vào manager này. Nhờ tách lớp như vậy, Spring có thể bổ sung hành vi mà JUnit không cần biết các chi tiết nội bộ của Spring.

`ApplicationContext` thường chỉ được nạp khi cần qua một delegate có nhận biết cache. Vì vậy việc lấy context từ `TestContext` có thể trả về context đang có trong cache thay vì tạo mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testcontext-bootstrap">Bootstrapping TestContext Framework</a>

<details>
<summary>Xem chi tiết</summary>

Quá trình bootstrap quyết định **cách triển khai TestContext, context loader, context customizer và listener nào** tham gia cho một test class.

SPI trung tâm là `TestContextBootstrapper`. `TestContextManager` lấy bootstrapper phù hợp rồi yêu cầu nó tạo `TestContext` và danh sách listener. Có thể chọn chiến lược tùy biến bằng `@BootstrapWith`.

Trong test ứng dụng thông thường, không nên tự triển khai SPI này. Bootstrapper mặc định của Spring đã xử lý các trường hợp phổ biến. Web test dùng bootstrapper có nhận biết web khi có `@WebAppConfiguration`.

Bootstrap tùy biến ở cấp thấp phù hợp hơn với tác giả framework/thư viện cần thay thế một chiến lược lớn của TestContext. Mã ứng dụng thường không cần đi xa như vậy. Trong Spring Framework 6.1, `@ContextCustomizerFactories` cho phép đăng ký thêm `ContextCustomizerFactory` cho test class mà vẫn giữ quy trình bootstrap chuẩn; các factory này có thể kế thừa và hợp nhất với cấu hình mặc định.

Với test ứng dụng thông thường, nên ưu tiên `@ContextConfiguration`, `@ActiveProfiles`, `@TestPropertySource` và `@ContextCustomizerFactories` khi thực sự cần `ContextCustomizerFactory` tùy biến.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `TestContextBootstrapper`, `DefaultBootstrapContext` và `@BootstrapWith`.
- [Spring Framework 6.1.14 API — @ContextCustomizerFactories](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/ContextCustomizerFactories.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="smart-context-loading">SmartContextLoader và chiến lược load context</a>

<details>
<summary>Xem chi tiết</summary>

`SmartContextLoader` là chiến lược biến metadata cấu hình test thành `ApplicationContext`. So với contract `ContextLoader` cũ, nó hiểu cả resource location lẫn component class và làm việc với cấu hình đã hợp nhất mà TestContext sử dụng.

Hai trách nhiệm chính:

1. **xử lý metadata cấu hình** trước khi nạp, bao gồm cơ chế phát hiện mặc định khi không khai báo location/class;
2. **nạp context** từ `MergedContextConfiguration` kết quả.

Delegating loader mặc định chọn nhánh cấu hình annotation hoặc resource XML/Groovy tùy metadata. Web test dùng biến thể tương ứng có hỗ trợ web.

Loader không quyết định vòng đời hay chính sách cache. Nó chỉ là một mắt xích: cấu hình đã hợp nhất tạo nên định danh cache; delegate có nhận biết cache sẽ tái sử dụng context phù hợp hoặc gọi loader để tạo context mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-execution-listeners">Mô hình TestExecutionListener</a>

<details>
<summary>Xem chi tiết</summary>

`TestExecutionListener` là cơ chế mở rộng gắn hành vi Spring vào các giai đoạn của vòng đời test. Spring có listener mặc định cho dependency injection, `@DirtiesContext`, transaction, SQL script, application event và nhiều trách nhiệm khác.

Các listener mặc định được hạ tầng của Spring phát hiện và sắp thứ tự. Thứ tự quan trọng vì listener sau có thể phụ thuộc vào trạng thái mà listener trước đã chuẩn bị. Listener tùy biến nên triển khai `Ordered` hoặc dùng `@Order`, không nên dựa vào thứ tự khai báo.

`@TestExecutionListeners` có thể đăng ký listener cho hệ phân cấp test. Chế độ hợp nhất cần được hiểu rõ:

- `REPLACE_DEFAULTS` thay thế danh sách mặc định trong trường hợp áp dụng;
- `MERGE_WITH_DEFAULTS` gộp listener cục bộ với listener mặc định, loại phần tử trùng rồi sắp xếp lại.

Thay thế listener mặc định tùy tiện có thể làm test đổi ngữ nghĩa một cách khó thấy, ví dụ mất dependency injection hoặc hỗ trợ transaction.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-framework-integration">SpringExtension và tích hợp với test framework</a>

<details>
<summary>Xem chi tiết</summary>

`SpringExtension` là lớp tích hợp của Spring với JUnit Jupiter. Nó nối các callback vòng đời của Jupiter vào `TestContextManager` và tham gia phân giải dependency/parameter.

JUnit Jupiter vẫn chịu trách nhiệm phát hiện test, thực thi nested/parameterized test, assertion và mô hình extension. Spring chỉ bổ sung công việc riêng của Spring quanh các giai đoạn đó.

Ví dụ:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class PricingServiceTests {

    @Autowired
    PricingService pricingService;
}
```

Composed annotation có thể che đi `@ExtendWith` trực tiếp. Cách hiểu vẫn không đổi: `SpringExtension` là cầu nối đi vào TestContext, không phải một test engine khác.

</details>

- [Quay lại đầu trang](#back-to-top)
