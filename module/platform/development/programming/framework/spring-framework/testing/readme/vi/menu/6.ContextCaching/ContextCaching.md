<a id="back-to-top"></a>

# Context cache, isolation và chạy song song

## Menu
- [Vì sao Spring cache ApplicationContext của test?](#context-cache-purpose)
- [Context cache key được tạo như thế nào?](#context-cache-key)
- [Static cache và ranh giới JVM process](#context-cache-process-boundary)
- [Kích thước cache, eviction và chi phí reuse](#cache-size-and-eviction)
- [DirtiesContext và chủ động invalidate context](#dirties-context)
- [Context failure threshold trong Spring Framework 6.1](#context-failure-threshold)
- [Chạy test song song và các ràng buộc an toàn của context](#parallel-test-execution)

## <a id="context-cache-purpose">Vì sao Spring cache ApplicationContext của test?</a>

<details>
<summary>Xem chi tiết</summary>

Việc tải một `ApplicationContext` thường là một trong những phần tốn thời gian nhất của Spring integration test. Spring phải xử lý cấu hình, tạo bean definition, khởi tạo singleton và có thể dựng thêm hạ tầng như `DataSource` hay client kết nối ra ngoài. Nếu mỗi test method đều dựng lại cùng một context thì test suite chậm đi nhưng bằng chứng kiểm thử hầu như không tăng.

Vì vậy TestContext framework cache những context đã tải thành công và tái sử dụng chúng khi test khác yêu cầu cùng một cấu hình test. Việc tái sử dụng chủ yếu diễn ra giữa các test class có cùng cache key; tạo test instance mới không đồng nghĩa với tạo Spring context mới. Nhờ đó một suite có nhiều integration test vẫn chỉ phải trả chi phí khởi động cho một số ít context thực sự khác nhau.

Cache cũng khiến trạng thái của singleton và các đối tượng do context sở hữu có thể tồn tại qua nhiều test method. Nếu test thay đổi trạng thái đó, test phải khôi phục lại, thiết kế component để có thể tái sử dụng an toàn, hoặc chủ động invalidate context. Context cache giúp tối ưu hiệu năng; bản thân nó không tạo sự cô lập cho test.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — Context Caching](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-cache-key">Context cache key được tạo như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring chỉ tái sử dụng context khi merged test configuration tạo ra cùng một cache key. Trong Spring Framework 6.1, key có thể bao gồm configuration location và class, context initializer class, context customizer, `ContextLoader`, parent context, active profile, test property source cùng inline property, và web resource base path từ `@WebAppConfiguration`.

Một số cấu hình đi vào cache key theo đường gián tiếp. `@DynamicPropertySource` tham gia thông qua context customizer có định danh phản ánh các method đăng ký được gắn annotation, chứ không dựa trên từng giá trị runtime mà supplier trả về sau đó. Vì vậy các method dynamic-property hoặc customizer khác nhau có thể tạo cache key khác nhau; ngược lại, các subclass kế thừa cùng một registration vẫn có thể dùng lại một context dù supplier sau đó trả về giá trị khác. Nếu giá trị đó đòi hỏi context khác, hãy dùng `@DirtiesContext` hoặc làm cho định danh cấu hình khác đi bằng một cơ chế phù hợp.

Đây là lý do tính nhất quán của cấu hình test ảnh hưởng trực tiếp đến hiệu năng. Trước khi tăng cache size, hãy kiểm tra suite có vô tình tạo quá nhiều key vì profile, property, customizer hoặc context hierarchy khác nhau hay không. Chia sẻ một cấu hình ổn định hoặc composed annotation thường giúp việc tái sử dụng rõ ràng hơn so với lặp lại nhiều biến thể gần giống nhau.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — Context Caching](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-cache-process-boundary">Static cache và ranh giới JVM process</a>

<details>
<summary>Xem chi tiết</summary>

Context cache của TestContext là static trong JVM process đang chạy test. Thiết kế này cho phép nhiều test class và nhiều test instance trong cùng tiến trình dùng chung cache. Cache không mang tính toàn cục cho cả máy build, một lần chạy Gradle hay CI job.

Nếu công cụ build fork test sang JVM khác, tiến trình mới bắt đầu với cache rỗng. Một cấu hình chạy mỗi test class trong JVM riêng sẽ không nhận được lợi ích tái sử dụng context giữa các class. Tương tự, những test task chạy ở các worker process khác nhau không chia sẻ cache dù cấu hình Spring của chúng giống hệt nhau.

Khi thấy context khởi động nhiều hơn dự kiến, cần xác định mô hình tiến trình trước. Số lần tải cao có thể đến từ nhiều cache key khác nhau, từ `@DirtiesContext`, hoặc đơn giản vì quá trình build đang fork JVM. Ba nguyên nhân này cần ba cách xử lý khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-size-and-eviction">Kích thước cache, eviction và chi phí reuse</a>

<details>
<summary>Xem chi tiết</summary>

TestContext cache mặc định chứa tối đa 32 context. Khi cache đầy, Spring dùng chính sách least-recently-used để eviction. `ApplicationContext` bị eviction sẽ được loại khỏi cache và đóng lại; nếu test sau đó cần lại cùng key thì context phải được tải từ đầu.

Có thể đổi giới hạn bằng system property `spring.test.context.cache.maxSize` hoặc cơ chế Spring properties tương đương. Tăng giới hạn chỉ hợp lý khi suite thực sự có nhiều cấu hình cần được tái sử dụng đồng thời và máy còn đủ bộ nhớ. Nếu nguyên nhân là cấu hình bị phân mảnh ngoài ý muốn, tăng cache size chỉ đổi bộ nhớ lấy thời gian khởi động.

Khi tối ưu, nên quan sát số liệu cache thay vì đoán. Bật DEBUG cho `org.springframework.test.context.cache` để xem hoạt động và thống kê cache. Nhiều cache miss thường là tín hiệu cần rà lại profile/property khác nhau, `@DirtiesContext` quá thường xuyên, hoặc việc fork tiến trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dirties-context">DirtiesContext và chủ động invalidate context</a>

<details>
<summary>Xem chi tiết</summary>

`@DirtiesContext` báo cho TestContext framework rằng một context không còn an toàn để tái sử dụng. Tùy annotation đặt ở đâu và class/method mode được chọn, Spring sẽ loại context tương ứng trước hoặc sau ranh giới test đó. Test tiếp theo cần cùng cấu hình sẽ phải tải một context mới.

Hãy dùng cơ chế này khi test thay đổi trạng thái do context sở hữu mà không thể khôi phục tin cậy, thay đổi hạ tầng gắn với context, hoặc cố ý kiểm thử một hành vi khiến context không còn dùng tiếp được. Đây là biện pháp mạnh hơn việc reset một mock hay dọn một fixture cục bộ vì toàn bộ cached context bị loại bỏ và chi phí khởi động quay lại.

Với context hierarchy, phạm vi invalidation càng cần rõ ràng. `@DirtiesContext` cho phép invalidation rộng theo hierarchy hoặc chỉ ở cấp hiện tại thông qua hierarchy mode. Nên chọn phạm vi nhỏ nhất đúng với trạng thái đã bị làm bẩn. Nếu test có thể tự dọn fixture cục bộ thì cách đó thường rẻ và dễ hiểu hơn việc dựng lại toàn bộ Spring context.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — `@DirtiesContext`](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-failure-threshold">Context failure threshold trong Spring Framework 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 bổ sung context failure threshold để test suite không lặp đi lặp lại việc tải một context đã biết là thất bại với cùng cache key. Giá trị mặc định là `1`: sau lần tải đầu tiên thất bại, lần tiếp theo cho key đó bị chặn sớm bằng `IllegalStateException` thay vì thực hiện lại toàn bộ quá trình tải rồi thất bại lần nữa.

Threshold được cấu hình bằng property số nguyên dương `spring.test.context.failure.threshold`. Có thể đặt một giá trị rất lớn để gần như vô hiệu hóa fail-fast khi cần chẩn đoán qua nhiều lần thử, nhưng đây nên là lựa chọn có chủ đích chứ không phải cấu hình mặc định của suite.

Threshold được theo dõi theo context cache key. Một cấu hình lỗi không khóa mọi Spring test context khác. Nếu nhiều test cùng fail ngay với thông báo threshold, hãy tìm lỗi tải đầu tiên của key đó; các lỗi sau thường chỉ là hệ quả của lỗi gốc.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — Context Failure Threshold](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)
- [Spring Framework 6.1.14 API — ContextCache](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/test/context/cache/ContextCache.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parallel-test-execution">Chạy test song song và các ràng buộc an toàn của context</a>

<details>
<summary>Xem chi tiết</summary>

TestContext framework có thể tham gia parallel test execution trong cùng một JVM, còn test engine hoặc công cụ build mới là thành phần quyết định test có thực sự chạy đồng thời hay không. Spring hỗ trợ sao chép `TestContext` mặc định cho concurrent execution và truy cập context cache an toàn; điều đó không tự làm bean, mutable fixture, database, file hay hệ thống dùng chung trở thành thread-safe.

Parallel execution không phù hợp với test dùng `@DirtiesContext`, phụ thuộc vào thứ tự test method bắt buộc, hoặc cùng thay đổi một tài nguyên bên ngoài. Một thread có thể loại context khỏi cache trong khi test khác vẫn đang dùng nó, tạo ra lỗi tưởng như ngẫu nhiên. Việc context liên tục bị đưa vào và loại khỏi cache do cache quá nhỏ cũng có thể tạo hiệu ứng tương tự.

Cần rà cả hạ tầng gắn với thread. Test-managed transaction gắn với thread mà Spring chuẩn bị cho test. Một tính năng kiểm thử chạy test body trên thread khác, ví dụ preemptive timeout, có thể khiến code thoát khỏi transaction đó. Vì vậy chỉ nên bật chạy song song sau khi đã chắc chắn context, transaction boundary và tài nguyên bên ngoài của từng test độc lập đủ an toàn.

Nếu extension bên thứ ba cung cấp `TestContext` tùy biến, cần xác nhận cách triển khai đó hỗ trợ contract copy-constructor mà việc thực thi TestContext song song yêu cầu.

### Tài liệu tham khảo

- [Spring Framework 6.1 Reference — Parallel Test Execution](https://docs.spring.io/spring-framework/docs/6.1.14/reference/html/testing.html)

</details>

- [Quay lại đầu trang](#back-to-top)
