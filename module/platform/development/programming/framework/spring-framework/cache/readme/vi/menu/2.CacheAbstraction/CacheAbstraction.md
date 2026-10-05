<a id="back-to-top"></a>

# Cache, CacheManager và mô hình vùng cache

## Menu
- [Vai trò của Cache interface](#cache-interface)
- [Vai trò của CacheManager](#cache-manager)
- [Tên cache và khái niệm vùng cache](#named-cache-region)
- [Cache hit, cache miss và lookup result](#cache-hit-miss)
- [Giá trị null và ValueWrapper](#cached-null-value)
- [Native cache và ranh giới adapter](#native-cache-access)
- [Programmatic Cache API và declarative caching](#programmatic-vs-declarative)

## <a id="cache-interface">Vai trò của Cache interface</a>

<details>
<summary>Xem chi tiết</summary>

`org.springframework.cache.Cache` là trừu tượng đại diện cho **một cache có tên** trong Spring. Mỗi instance `Cache` cung cấp tập thao tác mà framework cần để tra cứu, ghi và vô hiệu hóa dữ liệu.

Hợp đồng cốt lõi được giữ tương đối nhỏ: lấy giá trị qua `get`, ghi qua `put`, xóa một entry qua `evict`, xóa toàn vùng qua `clear`, và truy cập đối tượng của nhà cung cấp bằng `getNativeCache`. Interface cũng hỗ trợ tải giá trị khi miss bằng `get(key, Callable)`. Spring Framework 6.1 bổ sung các phương thức `retrieve` bất đồng bộ; phần đó được học sâu hơn ở chương xử lý đồng thời/bất đồng bộ.

Mô hình tư duy quan trọng là `Cache` chỉ là **hợp đồng adapter**, không phải cấu trúc dữ liệu phía dưới. Phần triển khai có thể bọc `ConcurrentMap`, Caffeine, JCache hoặc nhà cung cấp khác. Mã ứng dụng nhìn thấy cùng một hợp đồng cơ bản, trong khi khả năng riêng vẫn thuộc nhà cung cấp.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `Cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-manager">Vai trò của CacheManager</a>

<details>
<summary>Xem chi tiết</summary>

`CacheManager` là ranh giới registry/factory dùng để ánh xạ một tên cache logic thành instance Spring `Cache`. Thao tác chính là `getCache(String name)`; manager cũng có thể cung cấp danh sách tên cache hiện có qua `getCacheNames()`.

Nhờ đó, **ứng dụng muốn dùng cache nào** được tách khỏi **cache đó được tạo hoặc lấy bằng cách nào**. Annotation có thể chỉ cần nói `products`, còn `CacheManager` quyết định tên này ánh xạ tới cache in-memory, Caffeine, JCache hay phần triển khai khác.

Một ứng dụng có thể có nhiều `CacheManager`, nhưng lúc đó thao tác cache hoặc `CacheResolver` cần chọn rõ manager/chiến lược phân giải cần dùng. Spring Cache không tự gộp nhiều manager độc lập thành một namespace chung.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `CacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/CacheManager.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="named-cache-region">Tên cache và khái niệm vùng cache</a>

<details>
<summary>Xem chi tiết</summary>

Spring tham chiếu cache bằng **tên logic**. Những tên như `products`, `catalogById` hay `exchangeRates` định danh một vùng cache mà các entry cùng phục vụ một mục đích của ứng dụng và chia sẻ không gian key tương ứng.

Tên cache là một phần của chính sách ứng dụng, không mô tả cách biểu diễn vật lý. Nhà cung cấp này có thể tạo map cục bộ cho tên đó; nhà cung cấp khác có thể ánh xạ nó sang vùng cache từ xa hoặc cấu hình cache gốc.

Vì vậy tên tốt nên mô tả **thứ được cache**, không mô tả công nghệ hiện tại. `productsById` ổn định hơn `redisCache1` vì nhà cung cấp có thể thay đổi mà ý nghĩa cache vẫn giữ nguyên.

Một vùng cache cũng nên có hợp đồng key/value nhất quán. Dùng cùng một vùng cho nhiều loại kết quả không liên quan sẽ làm invalidation và việc gỡ lỗi khó hiểu dù nhà cung cấp vẫn cho phép về mặt kỹ thuật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-hit-miss">Cache hit, cache miss và lookup result</a>

<details>
<summary>Xem chi tiết</summary>

**Cache hit** nghĩa là cache có mapping cho key được yêu cầu. **Cache miss** nghĩa là không có mapping, nên ứng dụng hoặc cache interceptor phải quyết định có tải giá trị hay không.

`Cache.get(key)` cơ bản trả về `Cache.ValueWrapper` khi hit và `null` khi không tìm thấy mapping. Wrapper này cần thiết vì bản thân giá trị đã cache có thể là `null`: framework phải phân biệt được “không có entry” với “có entry nhưng giá trị logic là null”.

`get(key, type)` hỗ trợ bên gọi khi đã biết type mong đợi. `get(key, Callable)` kết hợp tra cứu với việc tải giá trị theo hợp đồng của phần triển khai cache; khả năng đồng bộ hóa khi nhiều bên gọi cùng tải một key vẫn phụ thuộc phần triển khai/nhà cung cấp.

Ở mức này, hit/miss chỉ là kết quả tra cứu. Việc entry có còn đủ mới hay không, miss có nên dẫn tới tính toán hay không, và lỗi có quay về nguồn dữ liệu gốc hay không là các chính sách riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cached-null-value">Giá trị null và ValueWrapper</a>

<details>
<summary>Xem chi tiết</summary>

Caching `null` tạo ra một sự mơ hồ: `null` từ lần tra cứu có thể nghĩa là “không có key” hoặc “key tồn tại và giá trị đã cache là null”. `Cache.ValueWrapper` giúp Spring biểu diễn một mapping tồn tại mà giá trị bên trong vẫn có thể là `null`.

Nhà cung cấp có hỗ trợ null hay không là chuyện của phần triển khai. Một số Spring adapter có thể chuyển `null` ở phía người dùng thành sentinel nội bộ vì nhà cung cấp gốc không lưu trực tiếp null. Nhà cung cấp khác có thể không hỗ trợ hoặc có ràng buộc riêng.

Negative caching—ví dụ cache kết quả “không tìm thấy”—có thể giảm số lần truy vấn database cho cùng một id không tồn tại. Tuy nhiên kết quả “không tồn tại” cũng có thể trở nên cũ nếu entity được tạo ngay sau đó. Vì vậy kết quả null/negative vẫn cần chính sách về độ mới và invalidation rõ ràng.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `Cache.ValueWrapper`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.ValueWrapper.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="native-cache-access">Native cache và ranh giới adapter</a>

<details>
<summary>Xem chi tiết</summary>

`Cache.getNativeCache()` trả về đối tượng của nhà cung cấp nằm phía dưới Spring adapter. Đây là lối truy cập trực tiếp khi ứng dụng cần khả năng mà hợp đồng chung của Spring Cache không mô hình hóa.

Truy cập native có thể hợp lý cho chẩn đoán hoặc khả năng đặc thù. Đổi lại, mã gọi native API bị phụ thuộc trực tiếp vào nhà cung cấp, nên việc đổi nhà cung cấp không còn chỉ là thay cấu hình.

Một cách tổ chức tốt là giữ truy cập native ở ranh giới tích hợp thay vì rải nó trong mã domain/dịch vụ. Nếu một khả năng native là thành phần quan trọng của tính đúng đắn—ví dụ primitive nguyên tử hoặc ngữ nghĩa phân tán cụ thể—thì khả năng đó phải được thiết kế và kiểm thử như hành vi riêng của nhà cung cấp, không nên giả định Spring Cache sẽ chuẩn hóa nó.

Phần này giúp người học nhận ra khi nào ứng dụng đã rời khỏi hợp đồng Spring Cache có tính khả chuyển.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-vs-declarative">Programmatic Cache API và declarative caching</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng có thể gọi `Cache` trực tiếp hoặc để Spring áp dụng chính sách cache theo kiểu khai báo quanh lời gọi phương thức.

Truy cập bằng API trực tiếp thể hiện luồng điều khiển rõ ràng:

```java
Cache cache = cacheManager.getCache("products");
if (cache == null) {
    throw new IllegalStateException("Required cache 'products' is not configured");
}
Cache.ValueWrapper hit = cache.get(productId);
```

`CacheManager.getCache(name)` có thể trả về `null` khi cache đó không tồn tại và không thể được tạo, nên mã dùng API trực tiếp phải xử lý rõ trường hợp phân giải thất bại này. Cách này phù hợp khi caching là một phần của thuật toán mà annotation ở cấp phương thức khó diễn đạt. Đổi lại, logic tra cứu/ghi dữ liệu/invalidation sẽ đi vào mã ứng dụng và tạo thêm mã lặp.

Declarative caching chuyển chính sách đó sang annotation như `@Cacheable`. Phương thức dịch vụ chỉ mô tả cách lấy dữ liệu khi cần; cache interceptor của Spring bao quanh lời gọi để xử lý tra cứu và ghi cache. Cách này giảm mã lặp nhưng yêu cầu hiểu ranh giới interception.

Hai cách tiếp cận vẫn dùng cùng trừu tượng Spring Cache. Declarative caching không phải hệ thống lưu trữ khác; nó là hạ tầng điều phối các thao tác `Cache` quanh việc thực thi phương thức.

</details>

- [Quay lại đầu trang](#back-to-top)
