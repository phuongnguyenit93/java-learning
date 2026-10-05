<a id="back-to-top"></a>

# Caching khai báo với các annotation của Spring

## Menu
- [Bật hạ tầng cache annotation với @EnableCaching](#enable-caching)
- [Tra cứu và ghi cache với @Cacheable](#cacheable)
- [Cập nhật cache với @CachePut](#cache-put)
- [Vô hiệu hóa cache với @CacheEvict](#cache-evict)
- [Kết hợp nhiều thao tác cache với @Caching](#caching-composition)
- [Chia sẻ cấu hình với @CacheConfig](#cache-config)
- [Mặc định toàn cục cho hạ tầng cache với CachingConfigurer](#caching-configurer)
- [Thứ tự ưu tiên cấu hình của thao tác cache](#cache-operation-configuration-precedence)

## <a id="enable-caching">Bật hạ tầng cache annotation với @EnableCaching</a>

<details>
<summary>Xem chi tiết</summary>

`@EnableCaching` bật cơ chế quản lý cache dựa trên annotation trong application context. Nó nhập hạ tầng dùng để phát hiện cache annotation và áp dụng thao tác cache quanh các lời gọi phương thức phù hợp.

Annotation này không tự tạo kho cache. Ứng dụng vẫn cần hạ tầng như `CacheManager` hoặc `CacheResolver` có khả năng phân giải các tên cache logic mà thao tác sử dụng.

Ở proxy mode mặc định, Spring áp dụng caching thông qua interceptor trên proxy bao quanh bean đích. Vì vậy metadata của annotation chỉ tạo hành vi khi lời gọi đi qua đường interception đã cấu hình. Ranh giới self-invocation/proxy được học sâu ở chương riêng; ở đây cần tách rõ hai trách nhiệm: bật hỗ trợ annotation và cung cấp nhà cung cấp/hạ tầng cache là hai việc khác nhau.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `EnableCaching`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cacheable">Tra cứu và ghi cache với @Cacheable</a>

<details>
<summary>Xem chi tiết</summary>

`@Cacheable` mô hình hóa thao tác “tái sử dụng kết quả của phương thức”. Trước khi gọi phương thức đích, Spring tính cache key rồi tra cứu cache đã được phân giải. Nếu tìm thấy giá trị phù hợp, Spring trả giá trị đó và bỏ qua phương thức đích. Nếu miss, phương thức được gọi và kết quả được ghi vào cache theo chính sách của thao tác.

Ví dụ:

```java
@Cacheable(cacheNames = "products", key = "#id")
public Product findProduct(long id) {
    return repository.findById(id).orElseThrow();
}
```

Annotation mô tả chính sách quanh phương thức; phương thức nghiệp vụ vẫn chịu trách nhiệm lấy dữ liệu khi cache không trả được kết quả.

`@Cacheable` hỗ trợ các lựa chọn như `cacheNames`, `key`, `keyGenerator`, `condition`, `unless`, `cacheManager`, `cacheResolver` và `sync`. Mỗi thuộc tính giải quyết một vấn đề khác nhau. Quy tắc về key/phân giải cache được học ở chương kế tiếp; các ràng buộc của `sync=true` thuộc chương xử lý đồng thời.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `Cacheable`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-put">Cập nhật cache với @CachePut</a>

<details>
<summary>Xem chi tiết</summary>

`@CachePut` cập nhật trạng thái cache mà không bỏ qua phương thức đích. Phương thức luôn được thực thi; sau đó kết quả được ghi vào cache theo key đã tính khi thao tác áp dụng.

Nó hữu ích khi phương thức có tác dụng phụ hoặc tác động nghiệp vụ quan trọng—ví dụ cập nhật dữ liệu có thẩm quyền—và kết quả trả về cần làm mới cache entry tương ứng:

```java
@CachePut(cacheNames = "products", key = "#result.id")
public Product update(Product command) {
    return repository.save(command);
}
```

Điểm khác với `@Cacheable` nằm ở ngữ nghĩa thực thi. `@Cacheable` có thể bỏ qua phương thức khi cache hit; `@CachePut` được thiết kế để phương thức chạy rồi mới put kết quả.

`@CachePut` cũng hỗ trợ điều kiện, nhưng thời điểm đánh giá khác `@Cacheable`: vì thao tác put luôn gọi phương thức, cả `condition` và `unless` đều được đánh giá sau lời gọi và có thể tham chiếu `#result`. Việc ghi cache vẫn chỉ là tác dụng phụ của phương thức nghiệp vụ, không thay thế nguồn dữ liệu chuẩn.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `CachePut`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CachePut.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-evict">Vô hiệu hóa cache với @CacheEvict</a>

<details>
<summary>Xem chi tiết</summary>

`@CacheEvict` mô tả invalidation: xóa trạng thái đã cache vì nó có thể không còn đúng. Mặc định, Spring evict entry tương ứng với key sau khi phương thức đích hoàn tất thành công.

Annotation cũng có thể xóa toàn bộ cache bằng `allEntries=true`, hoặc chuyển eviction lên trước lời gọi phương thức bằng `beforeInvocation=true`. Hai lựa chọn này thay đổi cả phạm vi lẫn ngữ nghĩa khi xảy ra lỗi nên không nên xem là tương đương nhau.

Trường hợp sử dụng thường gặp là xóa entity, thay đổi dữ liệu làm một projection đã cache trở nên cũ, hoặc thực hiện cập nhật hàng loạt khiến nhiều entry không còn hợp lệ.

Câu hỏi quan trọng hơn “phương thức này có thể evict không?” là “những dạng dữ liệu đã cache nào trở nên stale khi phương thức thành công hoặc thất bại?”. Chương 5 sẽ phát triển mô hình tư duy về thời điểm và tính nhất quán này.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `CacheEvict`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CacheEvict.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-composition">Kết hợp nhiều thao tác cache với @Caching</a>

<details>
<summary>Xem chi tiết</summary>

`@Caching` là annotation nhóm dùng để khai báo nhiều thao tác cache trên cùng một phương thức. Nó có thể chứa nhiều `@Cacheable`, `@CachePut` hoặc `@CacheEvict` khi một annotation đơn lẻ không diễn đạt đủ chính sách.

Ví dụ, một phương thức cập nhật có thể cần invalidation hai vùng cache dùng key khác nhau. `@Caching` giữ các thao tác đó gắn với phương thức nhưng mỗi thao tác vẫn có tên cache, key và condition riêng.

Việc kết hợp cần có chủ đích. Những thao tác mâu thuẫn trên cùng một lời gọi sẽ làm hành vi thực thi khó hiểu. Đặc biệt, kết hợp `@Cacheable` và `@CachePut` cho cùng một kết quả logic thường đáng nghi vì thao tác đầu có thể bỏ qua phương thức trong khi thao tác sau cần phương thức thực thi. Spring có thể kiểm tra một số tổ hợp không tương thích, nhưng việc giữ ngữ nghĩa rõ ràng vẫn là trách nhiệm của ứng dụng.

Nên dùng `@Caching` khi cùng một sự kiện nghiệp vụ thực sự tác động tới nhiều cache entry/vùng cache, không phải để gom các annotation không liên quan vào một chỗ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-config">Chia sẻ cấu hình với @CacheConfig</a>

<details>
<summary>Xem chi tiết</summary>

`@CacheConfig` cung cấp giá trị mặc định ở cấp class cho các thao tác cache khai báo trong class đó. Nó giúp giảm lặp khi nhiều phương thức dùng chung tên cache hoặc hạ tầng cache.

Annotation có thể khai báo giá trị mặc định cho:

- `cacheNames`;
- `keyGenerator`;
- `cacheManager`;
- `cacheResolver`.

Ví dụ, một service mà hầu hết phương thức đều dùng cache `products` có thể khai báo tên này một lần ở cấp class, rồi chỉ ghi đè ở thao tác nào thực sự khác biệt.

`@CacheConfig` không bật caching và cũng không tự định nghĩa thao tác cache. Nó chỉ cung cấp giá trị mặc định cho `@Cacheable`, `@CachePut`, `@CacheEvict`. Khi cấu hình ở cấp thao tác có giá trị rõ ràng, cấu hình cụ thể hơn đó được ưu tiên.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `CacheConfig`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CacheConfig.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caching-configurer">Mặc định toàn cục cho hạ tầng cache với CachingConfigurer</a>

<details>
<summary>Xem chi tiết</summary>

`CachingConfigurer` là điểm mở rộng toàn cục cho hạ tầng cache dựa trên annotation trong một `@Configuration` có `@EnableCaching`. Nó giải quyết các giá trị mặc định ở phạm vi ứng dụng, rộng hơn một thao tác cache hoặc một class dịch vụ.

Phần triển khai có thể cung cấp:

- `CacheManager` mặc định cho caching dựa trên annotation;
- `CacheResolver` để phân giải cache linh hoạt hơn;
- `KeyGenerator` mặc định;
- `CacheErrorHandler`.

Nếu đồng thời cung cấp `cacheManager()` và `cacheResolver()`, resolver được ưu tiên và cache manager bị bỏ qua trong quá trình phân giải thông thường của caching dựa trên annotation. Khi ghi đè `cacheManager()` hoặc `cacheResolver()`, phần triển khai phải khai báo rõ phương thức đó là `@Bean` để đối tượng trả về tham gia vòng đời của application context. Nếu không khai báo `KeyGenerator` tùy biến, Spring dùng `SimpleKeyGenerator`. Nếu không khai báo `CacheErrorHandler` tùy biến, `SimpleCacheErrorHandler` mặc định sẽ lan truyền ngoại lệ cache cho bên gọi.

`CachingConfigurer` được khởi tạo sớm trong application context, nên các phụ thuộc của nó cần được thiết kế phù hợp. Với Spring 6.1 nên triển khai `CachingConfigurer` trực tiếp; `CachingConfigurerSupport` đã bị đánh dấu deprecated từ Spring 6.0.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `CachingConfigurer`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CachingConfigurer.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-operation-configuration-precedence">Thứ tự ưu tiên cấu hình của thao tác cache</a>

<details>
<summary>Xem chi tiết</summary>

Cấu hình cache tồn tại ở nhiều cấp. Đọc từ cụ thể nhất tới tổng quát nhất giúp hành vi dễ dự đoán:

```text
annotation của thao tác
    ↓ ghi đè khi có cấu hình cụ thể
giá trị mặc định của @CacheConfig ở cấp class
    ↓ nếu không có thì dùng
giá trị mặc định của hạ tầng annotation toàn cục
    ↓ rồi tới
giá trị mặc định của Spring Framework
```

`key`, `keyGenerator`, `cacheManager` hoặc `cacheResolver` ở cấp thao tác có thể chuyên biệt hóa một phương thức. `@CacheConfig` cung cấp giá trị mặc định ở cấp class khi thao tác không đặt giá trị. `CachingConfigurer` cung cấp giá trị mặc định toàn ứng dụng cho việc tạo key, phân giải cache và xử lý lỗi.

Không phải cấu hình nào cũng tồn tại ở mọi cấp. Ví dụ `CachingConfigurer` không khai báo tên cache toàn cục, còn `@CacheConfig` thì có. Tương tự, `key` và `keyGenerator` là hai chiến lược thay thế nhau chứ không phải hai giá trị để kết hợp.

Thứ bậc này cho phép chính sách chung nằm ở nơi tập trung nhưng vẫn hỗ trợ phương thức đặc biệt khi cần. Nên đặt cấu hình ở cấp cao nhất còn mô tả đúng chính sách; quá nhiều ghi đè trên từng phương thức sẽ làm hành vi caching khó rà soát.

</details>

- [Quay lại đầu trang](#back-to-top)
