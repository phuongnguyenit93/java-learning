<a id="back-to-top"></a>

# Chính sách lỗi và cập nhật cache theo transaction

## Menu
- [CacheErrorHandler và cơ chế lan truyền lỗi mặc định](#cache-error-handler)
- [Lỗi cache và lỗi của business method](#cache-vs-business-failure)
- [TransactionAwareCacheDecorator và TransactionAwareCacheManagerProxy](#transaction-aware-cache)
- [Hoãn put, evict và clear đến after-commit](#after-commit-cache-mutation)
- [putIfAbsent, evictIfPresent và invalidate phải có hiệu lực ngay](#immediate-cache-operations)
- [Hành vi khi không có transaction đang hoạt động](#no-active-transaction)
- [Ranh giới với Spring Transaction Management](#transaction-module-boundary)

## <a id="cache-error-handler">CacheErrorHandler và cơ chế lan truyền lỗi mặc định</a>

<details>
<summary>Xem chi tiết</summary>

`CacheErrorHandler` là điểm mở rộng chính sách mà cache interceptor của Spring dùng khi chính thao tác cache ném ra runtime exception. Nó có callback riêng cho lỗi get, put, evict và clear, nên ứng dụng có thể quyết định lỗi hạ tầng cache phải làm lời gọi thất bại hay được xử lý theo chính sách khác.

Chính sách mặc định là `SimpleCacheErrorHandler`. Tên “Simple” không có nghĩa nó tự phục hồi; cách triển khai này ném lại ngoại lệ cache cho bên gọi. Hành vi thất bại ngay (fail-fast) giúp lỗi cache hiện rõ thay vì âm thầm giả vờ cache vẫn hoạt động bình thường.

Ứng dụng có thể cung cấp `CacheErrorHandler` khác qua `CachingConfigurer`. Ví dụ, một handler có thể ghi log rồi bỏ qua một số lỗi đọc để phương thức nghiệp vụ tự tính lại giá trị, nhưng vẫn lan truyền lỗi ghi hoặc eviction nếu dữ liệu cũ là rủi ro lớn. Việc bỏ qua lỗi phải có chủ đích vì nó thay đổi cả tính nhất quán lẫn khả năng quan sát.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`CacheErrorHandler`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/CacheErrorHandler.html)
- Spring Framework Javadoc — [`SimpleCacheErrorHandler`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/SimpleCacheErrorHandler.html)
- Spring Framework Reference — [Enabling Caching Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-vs-business-failure">Lỗi cache và lỗi của business method</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi cache và lỗi của phương thức nghiệp vụ nằm ở hai lớp khác nhau.

```text
tra cache lỗi
  -> áp dụng chính sách CacheErrorHandler

cache miss
  -> phương thức nghiệp vụ chạy
       -> phương thức nghiệp vụ lỗi
            -> ngoại lệ nghiệp vụ đi theo logic của ứng dụng
```

`CacheErrorHandler` xử lý exception phát sinh khi Spring gọi thao tác cache. Nó không phải bộ xử lý ngoại lệ chung cho phương thức nghiệp vụ được intercept. Nếu phương thức nghiệp vụ ném exception, Spring không đổi bản chất exception đó thành lỗi của cache provider.

Phân biệt này rất quan trọng khi thiết kế khả năng chịu lỗi. Sự cố đọc cache có thể chỉ là lỗi của lớp tối ưu hóa nếu nguồn dữ liệu chuẩn vẫn truy cập được. Ngoại lệ nghiệp vụ có thể là lỗi validation, lỗi hệ thống từ xa hoặc quy tắc domain cần được giữ nguyên. Gom cả hai vào một đường “cache fallback” dễ che mất nguyên nhân thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-aware-cache">TransactionAwareCacheDecorator và TransactionAwareCacheManagerProxy</a>

<details>
<summary>Xem chi tiết</summary>

`TransactionAwareCacheDecorator` bọc một Spring `Cache` và phối hợp một số thay đổi cache với transaction do Spring quản lý. `TransactionAwareCacheManagerProxy` áp dụng cùng ý tưởng ở cấp manager bằng cách cung cấp các cache object nhận biết transaction từ `CacheManager` đích.

Mục tiêu là tránh công bố thay đổi cache phản ánh trạng thái database trước khi transaction tương ứng commit thành công. Decorator đăng ký synchronization với hạ tầng transaction của Spring và hoãn các thay đổi được hỗ trợ tới giai đoạn after-commit.

```text
transaction bắt đầu
  -> thay đổi database
  -> yêu cầu cache put/evict/clear
       -> thay đổi cache được xếp chờ
  -> commit thành công
       -> thay đổi cache mới thực thi
```

Đây là cơ chế phối hợp, không phải một cache store có transaction. Lần đọc cache không có isolation kiểu database và decorator không biến native cache thành tài nguyên tham gia two-phase commit.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`TransactionAwareCacheDecorator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheDecorator.html)
- Spring Framework Javadoc — [`TransactionAwareCacheManagerProxy`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheManagerProxy.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="after-commit-cache-mutation">Hoãn put, evict và clear đến after-commit</a>

<details>
<summary>Xem chi tiết</summary>

Với transaction-aware cache, `put`, `evict` và `clear` có thể được hoãn tới after-commit của một transaction thành công. Nếu transaction rollback, thay đổi cache đã xếp chờ không được công bố.

Cơ chế này hữu ích khi cache phản chiếu dữ liệu đã commit. Ví dụ một phương thức cập nhật thay đổi một hàng trong database rồi evict cache entry tương ứng. Nếu evict ngay, trạng thái cache thay đổi trước commit; hoãn eviction giúp thay đổi cache đi cùng kết quả commit thành công.

Có một hệ quả về khả năng nhìn thấy dữ liệu ngay bên trong transaction đang chạy: vì thay đổi chưa thực sự xảy ra, lần tra cache sau đó vẫn có thể thấy entry cũ. Vì vậy transaction-aware decoration phải được hiểu là phối hợp thay đổi sau commit, không phải một góc nhìn cache cục bộ theo transaction.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`TransactionAwareCacheDecorator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheDecorator.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="immediate-cache-operations">putIfAbsent, evictIfPresent và invalidate phải có hiệu lực ngay</a>

<details>
<summary>Xem chi tiết</summary>

Một số phương thức của `Cache` cam kết hiệu lực tức thời nên không thể biểu diễn bằng hàng đợi after-commit của decorator.

- `putIfAbsent` có hợp đồng nguyên tử khi key chưa tồn tại và báo mapping trước đó. Các cache manager có sẵn của Spring có thể thực hiện thao tác này một cách nguyên tử, nhưng `Cache` tùy biến kế thừa cách triển khai mặc định hai bước có thể không bảo đảm tính nguyên tử; khi bảo đảm này quan trọng phải kiểm tra provider gốc.
- `evictIfPresent` kỳ vọng key biến mất ngay sau lời gọi và báo key có mapping trước đó hay không.
- `invalidate` kỳ vọng toàn bộ entry trở nên vô hình ngay và báo cache có mapping trước đó hay không.

Vì vậy `TransactionAwareCacheDecorator` không thể hoãn các thao tác này giống `put`, `evict` và `clear` thông thường. Gọi chúng trong transaction có thể làm cache thay đổi trước khi database transaction commit, nên phải thận trọng khi trạng thái cache cần bám theo dữ liệu đã commit.

Sự khác biệt trong API là có chủ đích. `put`/`evict`/`clear` cho phép hiệu lực bất đồng bộ hoặc bị trì hoãn; các phương thức yêu cầu hiệu lực tức thời mang kỳ vọng mạnh hơn về thời điểm quan sát được thay đổi.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`Cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)
- Spring Framework Javadoc — [`TransactionAwareCacheDecorator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheDecorator.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="no-active-transaction">Hành vi khi không có transaction đang hoạt động</a>

<details>
<summary>Xem chi tiết</summary>

Transaction-aware decoration chỉ hoãn thao tác khi có transaction do Spring quản lý đang hoạt động. Nếu transaction synchronization không hoạt động, decorator thực hiện `put`, `evict` và `clear` ngay trên cache đích như bình thường.

Do đó cùng một cache object có thời điểm hiệu lực phụ thuộc ngữ cảnh thực thi:

```text
bên trong Spring transaction đang hoạt động
  -> thay đổi được hỗ trợ chờ tới after-commit

bên ngoài Spring transaction
  -> thay đổi thực thi ngay
```

Hành vi này hữu ích nhưng có thể gây bất ngờ cho code giả định phương thức cache luôn có cùng thời điểm hiệu lực. Khi thời điểm đó ảnh hưởng tính đúng đắn, test nên bao phủ cả đường gọi có transaction và không có transaction.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`TransactionAwareCacheDecorator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/TransactionAwareCacheDecorator.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-module-boundary">Ranh giới với Spring Transaction Management</a>

<details>
<summary>Xem chi tiết</summary>

Spring Cache chịu trách nhiệm cho câu hỏi “thay đổi cache nên nhìn thấy vào thời điểm nào so với một transaction do Spring quản lý đã commit thành công?”. Nó không sở hữu toàn bộ mô hình transaction.

Các chủ đề như propagation, quy tắc rollback, isolation, savepoint, chọn transaction manager, resource synchronization và ngữ nghĩa proxy của `@Transactional` thuộc Spring Transaction Management. Module Cache chỉ cần hiểu đủ để dùng đúng ranh giới after-commit.

Trong thiết kế production, phải giữ nguồn dữ liệu chuẩn rõ ràng. Cache thường là bản sao dẫn xuất hoặc lớp tối ưu hóa. Transaction-aware decoration giúp giảm việc cập nhật cache quá sớm hoặc giữ dữ liệu cũ sau rollback, nhưng không biến cache thành nguồn ghi giao dịch có thẩm quyền và không giải quyết mọi bài toán nhất quán phân tán.

### Tài liệu tham khảo

- Spring Framework Reference — [Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- Spring Framework Javadoc — [`org.springframework.cache.transaction`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/transaction/package-summary.html)

</details>

- [Quay lại đầu trang](#back-to-top)
