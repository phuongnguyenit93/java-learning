<a id="back-to-top"></a>

# Tổng quan Spring Testing

## Menu
- [Vì sao Spring cần testing support riêng?](#spring-testing-purpose)
- [Khả năng kiểm thử nhờ IoC và object thông thường](#testability-through-ioc)
- [Unit test và Spring integration test khác nhau thế nào?](#unit-vs-integration-tests)
- [Chọn test scope nhỏ nhất nhưng đủ bằng chứng](#test-scope-ladder)
- [Ranh giới của Spring Framework Testing](#testing-boundaries)

## <a id="spring-testing-purpose">Vì sao Spring cần testing support riêng?</a>

<details>
<summary>Xem chi tiết</summary>

Phần lớn mã Java có thể được kiểm thử mà không cần Spring. Hỗ trợ kiểm thử trong `spring-test` chỉ thực sự cần khi hành vi cần chứng minh phụ thuộc vào chính Spring: cách các bean được kết nối, cấu hình `ApplicationContext`, hỗ trợ transaction cho test, quá trình dispatch của MVC, WebFlux handler hoặc dependency của HTTP client.

Ý tưởng quan trọng không phải là “test nào cũng phải khởi động Spring”. Hạ tầng kiểm thử của Spring là một bước **mở rộng phạm vi có chủ đích**. Hãy bắt đầu bằng test ở mức đối tượng Java thông thường; chỉ đưa TestContext Framework hoặc hạ tầng web test vào khi chính hành vi của Spring là thứ test cần chứng minh.

Cách nghĩ này giúp test vừa nhanh vừa dễ xác định nguyên nhân lỗi. Một service chỉ phụ thuộc vào interface thường không cần `ApplicationContext`. Ngược lại, test muốn chứng minh một profile chọn đúng bean, controller được ánh xạ qua cấu hình MVC thật, hoặc transaction được Spring rollback thì phải dùng hạ tầng do Spring quản lý.

Trong toàn bộ module, hãy hỏi trước: **test này cần quan sát hành vi nào của Spring mà một test trên đối tượng thuần không thể chứng minh?**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testability-through-ioc">Khả năng kiểm thử nhờ IoC và object thông thường</a>

<details>
<summary>Xem chi tiết</summary>

Phong cách IoC của Spring đã cải thiện khả năng kiểm thử ngay cả trước khi dùng framework kiểm thử của Spring. Constructor injection làm dependency trở nên tường minh, vì vậy đối tượng ứng dụng thường có thể được tạo trực tiếp bằng fake, stub hoặc mock.

```java
PaymentService service =
        new PaymentService(fakeGateway, fixedClock);

Receipt receipt = service.pay(order);
```

Nếu hành vi cần test hoàn toàn nằm trong `PaymentService` và các dependency được truyền vào, không cần khởi động container. Việc nạp `ApplicationContext` trong trường hợp này chỉ làm tăng chi phí khởi động và mức độ phụ thuộc vào cấu hình mà không tạo thêm bằng chứng có giá trị.

Cách hiểu cần giữ là: **IoC là đặc tính thiết kế; TestContext là hạ tầng kiểm thử tích hợp.** Ứng dụng chạy bằng Spring ở production không có nghĩa mọi test đều phải chạy Spring.

Chỉ dùng test do Spring quản lý khi cần kiểm tra những thứ như component registration, qualifier, profile, post-processor, proxy, scoped bean, transaction listener hoặc hạ tầng web chỉ tồn tại sau khi container được cấu hình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unit-vs-integration-tests">Unit test và Spring integration test khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Unit test thuần tự kiểm soát việc tạo đối tượng. Khi test thất bại, nguyên nhân thường nằm trong mã đang được kiểm thử hoặc dependency đã được cấp rõ ràng. Spring integration test đặt câu hỏi rộng hơn: **mã có hoạt động đúng khi Spring tạo và kết nối các thành phần hạ tầng hay không?**

TestContext Framework bao quanh test engine bên ngoài và quản lý các trách nhiệm riêng của Spring như nạp/cache context, dependency injection vào test instance, test-managed transaction và listener vòng đời. JUnit hoặc TestNG vẫn chịu trách nhiệm phát hiện và thực thi test.

Sự khác nhau nằm ở loại bằng chứng chứ không nằm ở annotation. Một JUnit test tự tạo controller vẫn là test kiểu unit. Một JUnit test dùng `SpringExtension` và `@ContextConfiguration` là Spring integration test vì kết quả của nó phụ thuộc vào context do Spring quản lý.

Không nên hiểu “integration test” đồng nghĩa với “test chậm”. TestContext có cache nên integration test có thể chạy nhanh; ngược lại một unit test thiết kế kém vẫn có thể chậm. Phạm vi phải được chọn từ hành vi cần chứng minh, không phải từ tên gọi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-scope-ladder">Chọn test scope nhỏ nhất nhưng đủ bằng chứng</a>

<details>
<summary>Xem chi tiết</summary>

Một thang phạm vi test hữu ích:

```text
test object thuần
    ↓ khi hành vi container quan trọng
TestContext + ApplicationContext
    ↓ khi web dispatching quan trọng
MockMvc / WebTestClient bind vào mock server
    ↓ khi HTTP stack thật quan trọng
live-server HTTP test
```

Mỗi nấc thêm hạ tầng nên vừa tăng bằng chứng vừa tăng chi phí. Ví dụ, `MockMvc` có thể chứng minh mapping, binding, validation, filter và exception resolution của Spring MVC mà không mở socket. Live-server test còn kiểm chứng ranh giới HTTP/server thật, nhưng tốn tài nguyên hơn và thường khó chẩn đoán hơn.

Hãy chọn **phạm vi nhỏ nhất vẫn quan sát được rủi ro cần kiểm tra**. Nếu lỗi chỉ có thể đến từ cấu hình MVC thì controller unit test là quá nhỏ. Nếu mã chỉ là phép tính không phụ thuộc Spring thì application-context test là quá lớn.

Cách tổ chức này cũng tránh lặp lại bằng chứng: một số integration test rộng kiểm tra cấu hình, còn nhiều test trên đối tượng tập trung có thể bao phủ logic phân nhánh nhanh hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-boundaries">Ranh giới của Spring Framework Testing</a>

<details>
<summary>Xem chi tiết</summary>

Module này chịu trách nhiệm về hạ tầng kiểm thử do Spring Framework cung cấp: TestContext, việc nạp context cho test, context caching, fixture injection do Spring quản lý, hỗ trợ transactional test, `MockMvc`, các binding mode của `WebTestClient` dành cho test, `MockRestServiceServer` và các extension vòng đời liên quan.

Các module lân cận vẫn giữ trách nhiệm riêng:

- vòng đời, assertion, parameterized test và cơ chế engine của JUnit/TestNG thuộc kiểm thử Java tổng quát;
- Spring Boot chịu trách nhiệm về `@SpringBootTest`, test slice, Boot test auto-configuration và context customization dành riêng cho Boot;
- `spring-framework/web` và `reactive` chịu trách nhiệm về ngữ nghĩa của MVC/WebFlux khi chạy production; module này chỉ giải thích cách hạ tầng test thực thi chúng;
- `transaction-management` chịu trách nhiệm về propagation, isolation và ngữ nghĩa transaction ở production; module này chịu trách nhiệm về **hành vi transaction do test quản lý**.

Khi một phần cần các khái niệm đó, nội dung chỉ giải thích đủ để hiểu cơ chế kiểm thử rồi để phần chuyên sâu cho đúng module chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)
