<a id="back-to-top"></a>

# Lifecycle, extension point và chiến lược kiểm thử

## Menu
- [TestContext lifecycle callback](#test-lifecycle-callbacks)
- [Đăng ký và sắp xếp TestExecutionListener](#testexecutionlistener-ordering)
- [Test execution event](#test-execution-events)
- [Ghi nhận ApplicationContext event trong test](#application-event-recording)
- [Support class và composed testing annotation](#support-classes-and-composed-annotations)
- [Ahead-of-time support cho integration test](#aot-test-support)
- [Ràng buộc execution mode và rà soát an toàn khi chạy song song](#execution-mode-constraints)
- [Chọn giữa unit, context, mock-web và live-server test](#test-scope-decision)
- [Tổng hợp chiến lược Spring Framework Testing](#testing-strategy-synthesis)

## <a id="test-lifecycle-callbacks">TestContext lifecycle callback</a>

<details>
<summary>Xem chi tiết</summary>

TestContext Framework quan sát vòng đời test thông qua `TestContextManager`. Test engine vẫn sở hữu vòng đời thật; Spring chỉ nhận callback quanh các giai đoạn quan trọng và cho phép `TestExecutionListener` phản ứng tại đúng thời điểm.

Các giai đoạn chính gồm:

- trước/sau test class;
- chuẩn bị test instance;
- trước/sau từng test method;
- trước/sau phần thực thi thật của test method.

Các giai đoạn này cố ý chi tiết hơn một cặp “before/after test”. Dependency injection thuộc lúc chuẩn bị test instance, trong khi việc thiết lập transaction, chạy SQL script, publish event hay invalidate context cần những vị trí khác nhau trong vòng đời.

Khi debug integration test, nên hỏi **trạng thái bị thiếu lẽ ra phải được chuẩn bị ở giai đoạn nào của lifecycle?** Câu hỏi đó thường dẫn thẳng tới listener chịu trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testexecutionlistener-ordering">Đăng ký và sắp xếp TestExecutionListener</a>

<details>
<summary>Xem chi tiết</summary>

Các `TestExecutionListener` mặc định của Spring bao phủ servlet setup, xử lý dirty context, application event, dependency injection, observation support, transaction, SQL script và việc publish lifecycle event.

Thứ tự là một phần của contract. Listener phía sau có thể cần trạng thái mà listener phía trước đã chuẩn bị, vì vậy custom listener nên dùng `Ordered` hoặc `@Order` thay vì dựa vào thứ tự khai báo.

Với `@TestExecutionListeners`, cần nhớ chính sách merge:

- `REPLACE_DEFAULTS` có thể loại toàn bộ listener mặc định;
- `MERGE_WITH_DEFAULTS` gộp listener cục bộ với listener mặc định, loại phần trùng lặp rồi sắp xếp lại.

Vô tình thay thế listener mặc định có thể làm dependency injection, transaction, SQL script hoặc event support biến mất dù test method không thay đổi.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — TestExecutionListeners](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/TestExecutionListeners.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-execution-events">Test execution event</a>

<details>
<summary>Xem chi tiết</summary>

Spring có thể publish các giai đoạn trong TestContext lifecycle thành application event thông qua `EventPublishingTestExecutionListener`. Hạ tầng hoặc component hỗ trợ kiểm thử bên trong `ApplicationContext` có thể phản ứng với quá trình thực thi test mà không cần định nghĩa thêm custom listener.

Các event tương ứng với những giai đoạn như before/after test class, before/after test method và before/after test execution.

Có một ràng buộc về thời điểm quan trọng: listener publish event thông qua test `ApplicationContext`. Nếu context chưa được tải thì event ở giai đoạn sớm không thể được publish qua context đó. Test class đầu tiên làm một context mới được tải vì vậy có thể không quan sát class-level “before” event giống một test class tái sử dụng context đã có.

Tương tự, nếu context bị dirty trước lifecycle event ở giai đoạn muộn thì có thể không còn context đang active để publish event.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-event-recording">Ghi nhận ApplicationContext event trong test</a>

<details>
<summary>Xem chi tiết</summary>

`@RecordApplicationEvents` bật việc ghi nhận application event được publish bởi test `ApplicationContext`. Test có thể inject `ApplicationEvents` rồi assert các domain/application event phát sinh trong kịch bản.

```java
@RecordApplicationEvents
class OrderEventTests {

    @Autowired
    ApplicationEvents events;
}
```

Việc ghi nhận event gắn với từng lần thực thi test, không phải một nhật ký event toàn cục tăng mãi. Trong Spring Framework 6.1.14, contract ghi nhận bao phủ event được publish từ test thread hoặc các thread hậu duệ của nó; không nên hiểu thành bảo đảm rằng event từ mọi worker thread không liên quan đều được ghi nhận. Điểm này quan trọng khi xử lý application event đi qua ranh giới executor.

Lỗi của listener đồng bộ lan truyền bình thường vì việc publish diễn ra trên thread gọi. Lỗi trong listener bất đồng bộ không tự lan truyền ngược vào vòng đời TestContext, vì vậy việc event đã được publish chưa đủ chứng minh xử lý bất đồng bộ đã thành công.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — RecordApplicationEvents](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/event/RecordApplicationEvents.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="support-classes-and-composed-annotations">Support class và composed testing annotation</a>

<details>
<summary>Xem chi tiết</summary>

Spring testing annotation có thể được compose. Project có thể đóng gói cấu hình TestContext lặp lại thành annotation mang ý nghĩa domain thay vì chép cùng một annotation stack sang nhiều test class.

Ví dụ, một composed annotation có thể gom `@ContextConfiguration`, `@ActiveProfiles`, `@TestPropertySource` và annotation tích hợp với test engine trong khi vẫn giữ semantics của từng annotation gốc.

Spring cũng có một số base class hỗ trợ, nhưng inheritance không nên trở thành cách mặc định để chia sẻ cấu hình test. Composed annotation và fixture helper tập trung thường tạo mức độ phụ thuộc thấp hơn một hệ phân cấp abstract test quá sâu.

Tên composed annotation nên có ý nghĩa. `@RepositoryIntegrationTest` nên nói rõ loại môi trường nó thiết lập, không chỉ che một nhóm thiết lập tiện ích rời rạc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aot-test-support">Ahead-of-time support cho integration test</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 hỗ trợ ahead-of-time processing cho TestContext integration test. Ở build time, Spring phát hiện các cấu hình test context duy nhất và chuẩn bị metadata để khởi tạo context tối ưu. Khi chạy ở AOT mode, các context đã xử lý đó vẫn được quản lý qua TestContext cache.

Hạ tầng tùy chỉnh có extension point riêng cho AOT. Custom context loader cần tham gia AOT nên implement `AotContextLoader`; custom listener có công việc AOT cũng cần dùng SPI tương ứng của TestContext AOT.

`@DisabledInAotMode` đánh dấu test không nên chạy trong AOT mode khi giả định của test phụ thuộc vào hành vi runtime không thể tái hiện trong chế độ đó.

Không phải mọi tính năng TestContext đều hỗ trợ AOT. Trong Spring Framework 6.1, `@ContextHierarchy` không được hỗ trợ trong TestContext AOT processing. Đây là ranh giới khả năng, không phải lý do để thiết kế lại JVM test thông thường đang dùng hierarchy hợp lệ.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 API — AotContextLoader](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/aot/AotContextLoader.html)
- [Spring Framework 6.1.14 API — DisabledInAotMode](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/aot/DisabledInAotMode.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-mode-constraints">Ràng buộc execution mode và rà soát an toàn khi chạy song song</a>

<details>
<summary>Xem chi tiết</summary>

Parallel test execution do test engine/công cụ build điều khiển chứ không phải Spring. TestContext được thiết kế để hỗ trợ concurrent execution, nhưng phần còn lại của test fixture cũng phải an toàn.

Parallel execution không phù hợp khi test:

- cùng thay đổi một database/file/message resource bên ngoài mà không có isolation;
- phụ thuộc method order;
- thường xuyên dùng `@DirtiesContext`;
- phụ thuộc trạng thái gắn với thread nhưng không được tái tạo trên worker thread.

Một test có thể invalidate hoặc evict context mà test chạy đồng thời khác vẫn đang cần. Cách triển khai `TestContext` tùy biến cũng phải hỗ trợ quy tắc sao chép cần cho việc thực thi TestContext đồng thời.

Test-managed transaction còn nhạy hơn vì transaction được gắn với test thread mà Spring chuẩn bị. Tính năng kiểm thử chạy test body trên thread khác, ví dụ preemptive timeout, có thể thực hiện thao tác database bên ngoài transaction đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="test-scope-decision">Chọn giữa unit, context, mock-web và live-server test</a>

<details>
<summary>Xem chi tiết</summary>

Hãy chọn phạm vi test dựa trên điều mạnh nhất mà test phải chứng minh:

```text
chỉ business logic
→ test object thuần

Spring bean graph / profile / lifecycle
→ TestContext + ApplicationContext

Spring MVC hoặc WebFlux request handling
→ MockMvc hoặc WebTestClient bind vào mock target

transport / deployed server thật
→ live-server HTTP test
```

Phạm vi lớn hơn không tự động tốt hơn. Nó làm tăng lượng cấu hình, trạng thái dùng chung, chi phí khởi động và số nguyên nhân có thể gây lỗi.

Một test suite tốt thường kết hợp nhiều phạm vi: nhiều object test tập trung, đủ context test để chứng minh Spring wiring, các mock-web test cho request handling và một số ít live-server test cho ranh giới không thể mô phỏng đáng tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-strategy-synthesis">Tổng hợp chiến lược Spring Framework Testing</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework Testing gồm nhiều **lớp tạo bằng chứng**, không phải một kiểu test duy nhất.

Mô hình tổng thể từ đầu đến cuối:

```text
test engine thông thường
        ↓
SpringExtension / engine adapter
        ↓
TestContextManager
        ↓
TestExecutionListeners
        ↓
MergedContextConfiguration
        ↓
context cache + ApplicationContext
        ↓
transaction / web / HTTP-client test infrastructure khi cần
```

Luồng học của module kết nối các lớp này như sau:

- cấu hình context quyết định môi trường Spring nào tồn tại;
- fixture đưa môi trường đó vào test;
- profile/property ảnh hưởng đến định danh context;
- caching cân bằng chi phí khởi động với isolation;
- transactional support quản lý transaction bên ngoài của test;
- MockMvc và WebTestClient bổ sung bằng chứng ở bề mặt web;
- lifecycle, event, AOT và parallel execution giải thích hạ tầng vận hành quanh test như thế nào.

Quy tắc cuối cùng cần giữ: **chỉ dùng hạ tầng kiểm thử Spring cần thiết để chứng minh hành vi đang có rủi ro, đồng thời luôn biết ranh giới production nào test đó vẫn chưa đi qua.**

</details>

- [Quay lại đầu trang](#back-to-top)
