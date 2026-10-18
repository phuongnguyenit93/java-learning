<a id="back-to-top"></a>

# Cache key, điều kiện và lựa chọn vùng cache

## Menu
- [Quy tắc tạo key mặc định và SimpleKey](#default-key-generation)
- [Tùy biến KeyGenerator](#custom-key-generator)
- [SpEL key và ngữ cảnh đánh giá cache](#spel-key-context)
- [condition và unless khác nhau như thế nào?](#condition-vs-unless)
- [Chọn cache qua CacheManager](#cache-manager-resolution)
- [Phân giải cache động với CacheResolver](#cache-resolver)
- [Nhiều tên cache: thứ tự tra cứu và ngữ nghĩa cập nhật](#multiple-cache-names)
- [Các lỗi thiết kế cache key thường gặp](#cache-key-design-pitfalls)

## <a id="default-key-generation">Quy tắc tạo key mặc định và SimpleKey</a>

<details>
<summary>Xem chi tiết</summary>

Khi thao tác không khai báo SpEL `key` và cũng không chọn `KeyGenerator` tùy biến, Spring dùng `SimpleKeyGenerator`. Mục tiêu của nó là tạo cache key ổn định từ các tham số của method để ứng dụng không phải tự viết chính sách key cho mọi thao tác.

Trong Spring 6.1, quy tắc cơ bản là:

```text
không có tham số          → SimpleKey.EMPTY
một tham số khác null     → dùng chính tham số đó
các trường hợp còn lại    → new SimpleKey(parameters)
```

`SimpleKey` triển khai equality và hashing dựa trên các phần tử bên trong, nên không dùng chiến lược “chỉ lấy hash” dễ collision của những phiên bản Spring rất cũ. Key tạo ra dùng an toàn với `ConcurrentMapCache`, nhưng provider khác vẫn có thể yêu cầu cách biểu diễn hoặc serialization riêng.

Cơ chế mặc định này chỉ thật sự phù hợp khi **mọi tham số của method đều thuộc định danh cache**. Nếu có tham số chỉ ảnh hưởng luồng điều khiển, hoặc key cần được chuẩn hóa theo định danh nghiệp vụ, một chiến lược rõ ràng hơn sẽ dễ bảo trì hơn.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `SimpleKeyGenerator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/SimpleKeyGenerator.html)
- [Spring Framework 6.1 Javadoc — `SimpleKey`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/SimpleKey.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-key-generator">Tùy biến KeyGenerator</a>

<details>
<summary>Xem chi tiết</summary>

`KeyGenerator` là điểm mở rộng (extension point) của Spring để tính cache key từ đối tượng đích, `Method` đang được gọi và danh sách đối số:

```java
@FunctionalInterface
public interface KeyGenerator {
    Object generate(Object target, Method method, Object... params);
}
```

Bộ sinh key tùy biến hữu ích khi nhiều thao tác dùng chung một quy ước key—ví dụ chuẩn hóa một định danh nghiệp vụ tổng hợp hoặc chủ động bỏ qua một đối số chỉ phục vụ truyền dữ liệu/logging.

Bộ sinh key có thể được chọn ở nhiều mức: toàn cục qua `CachingConfigurer`, cấp class qua `@CacheConfig(keyGenerator = ...)`, hoặc trực tiếp trên thao tác cache bằng thuộc tính `keyGenerator`.

Một thao tác nên dùng **hoặc** SpEL `key` **hoặc** `keyGenerator`; đây là hai chiến lược tạo key thay thế nhau. Key tùy biến cũng cần có `equals`/`hashCode` ổn định và cách biểu diễn phù hợp với provider đã chọn.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `KeyGenerator`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/KeyGenerator.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spel-key-context">SpEL key và ngữ cảnh đánh giá cache</a>

<details>
<summary>Xem chi tiết</summary>

Các annotation cache có thể dùng Spring Expression Language (SpEL) để tính key. Cách này phù hợp khi định danh cache chỉ dùng một phần các đối số của method hoặc cần rút ra một định danh nhỏ từ chúng.

Ví dụ:

```java
@Cacheable(cacheNames = "books", key = "#isbn.rawNumber + ':' + #includeReviews")
public Book findBook(Isbn isbn, boolean includeReviews) { ... }
```

Cả `isbn` và `includeReviews` đều làm thay đổi `Book` trả về, nên cả hai phải tham gia định danh cache. Nếu bỏ sót một đầu vào, hai lời gọi khác nhau về mặt logic có thể dùng chung một giá trị đã cache.

Ngữ cảnh đánh giá (evaluation context) cung cấp metadata như `#root.method`, `#root.methodName`, `#root.target`, `#root.targetClass`, `#root.args` và `#root.caches`. Đối số của method có thể được tham chiếu bằng tên khi thông tin tên tham số khả dụng, hoặc bằng bí danh theo vị trí như `#p0` và `#a0`.

Một số biểu thức được đánh giá **sau** lời gọi method còn có thể dùng `#result`; `unless` là ví dụ điển hình. Không nên giả định `#result` tồn tại trong biểu thức phải chạy trước method đích.

SpEL giữ chính sách key đơn giản ngay cạnh annotation. Khi biểu thức bắt đầu chứa nhiều logic nghiệp vụ, `KeyGenerator` hoặc một value object riêng thường dễ tái sử dụng và kiểm thử hơn.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `Cacheable.key`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#key())

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="condition-vs-unless">condition và unless khác nhau như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`condition` và `unless` là các điều kiện theo từng thao tác cache. Với `@Cacheable`, chúng được đánh giá ở hai thời điểm khác nhau.

`condition` chạy **trước** lời gọi method. Nếu biểu thức trả về `false`, thao tác cache bị bỏ qua cho lần gọi đó. Vì method chưa chạy nên biểu thức này không thể phụ thuộc vào kết quả.

`unless` chạy **sau** khi method trả về và sẽ ngăn việc lưu kết quả nếu biểu thức trả về `true`. Vì vậy `unless` có thể đọc `#result`.

```java
@Cacheable(
    cacheNames = "products",
    condition = "#id > 0",
    unless = "#result.discontinued"
)
```

Trong ví dụ này, id không hợp lệ khiến caching bị bỏ qua ngay từ đầu; một sản phẩm đã ngừng kinh doanh vẫn được trả cho bên gọi nhưng không được ghi vào cache.

Với `@Cacheable(sync=true)`, `unless` không được hỗ trợ. Đây là giới hạn của cơ chế nạp có đồng bộ và được giải thích cùng các quy tắc `sync=true` trong chương về xử lý đồng thời.

Không nên áp dụng thời điểm đánh giá này cho mọi annotation cache. Riêng `@CachePut.condition` được đánh giá sau lời gọi method và có thể dùng `#result`, vì thao tác put luôn gọi method trước khi quyết định có cập nhật cache hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-manager-resolution">Chọn cache qua CacheManager</a>

<details>
<summary>Xem chi tiết</summary>

Các annotation cache thường bắt đầu từ tên cache logic. Trước khi tra cứu hoặc ghi, những tên đó phải được phân giải thành các đối tượng `Cache` thật.

Trong cấu hình thông thường, Spring dùng bộ phân giải cache dựa trên `CacheManager`. Mỗi tên cache được chuyển cho `CacheManager.getCache(name)` để lấy các `Cache` tham gia thao tác.

`CacheManager.getCache(name)` có thể trả về `null` nếu cache đó không tồn tại và cũng không thể được tạo. Trong luồng phân giải chuẩn, mọi tên cache đã khai báo vì vậy phải được phân giải thành công; tên không phân giải được là lỗi chứ không tự động bị bỏ qua.

Việc chọn manager tuân theo thứ tự ưu tiên đã học ở Chương 3: thao tác có thể chỉ định `cacheManager`, `@CacheConfig` có thể cung cấp mặc định cấp class, và `CachingConfigurer` có thể cung cấp mặc định toàn ứng dụng cho hạ tầng annotation.

Sự tách biệt này cho phép annotation chỉ nói về tên cache logic, còn cấu hình manager chịu trách nhiệm khởi tạo provider và quản lý vòng đời. Nếu việc chọn cache phụ thuộc trực tiếp vào ngữ cảnh lời gọi, `CacheResolver` phù hợp hơn việc mã hóa lựa chọn provider vào tên cache.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-resolver">Phân giải cache động với CacheResolver</a>

<details>
<summary>Xem chi tiết</summary>

`CacheResolver` là interface chiến lược dùng khi việc chọn cache cần nhiều ngữ cảnh hơn một danh sách tên cố định. Nó nhận `CacheOperationInvocationContext`, từ đó có thể đọc thao tác cache, đối tượng đích, method và các đối số, rồi trả về các `Cache` cho lần gọi hiện tại.

Nhờ vậy ứng dụng có thể định tuyến tới cache dựa trên tenant, loại đối số hoặc một quy tắc khác mà vẫn giữ annotation trên method ở dạng khai báo.

Thao tác có thể chọn resolver theo bean name qua `cacheResolver`. `@CacheConfig` có thể đặt resolver ở cấp class và `CachingConfigurer` có thể cung cấp resolver toàn cục. Resolver tùy biến là một chiến lược thay thế cho việc phân giải tên qua `CacheManager`; không nên cấu hình `cacheResolver` và `cacheManager` như hai lựa chọn cạnh tranh trên cùng một thao tác.

Phân giải động nên quyết định **cache nào tham gia**, không nên che giấu ngữ nghĩa nghiệp vụ riêng của provider. Nếu tính đúng đắn phụ thuộc topology hoặc cấu trúc dữ liệu (data structure) đặc thù của provider, đó là vấn đề thuộc ranh giới tích hợp provider.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `CacheResolver`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/interceptor/CacheResolver.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multiple-cache-names">Nhiều tên cache: thứ tự tra cứu và ngữ nghĩa cập nhật</a>

<details>
<summary>Xem chi tiết</summary>

Một thao tác cache có thể khai báo nhiều tên cache. Với `@Cacheable`, Spring tra cứu theo thứ tự khai báo để tìm hit. Nếu một cache đã có giá trị, các cache tham gia còn lại được cập nhật bằng giá trị đó; nếu tất cả đều miss, kết quả mới được tính sẽ được lưu vào các cache tham gia.

Cách này hữu ích khi một method cố ý duy trì cùng một kết quả logic trong nhiều vùng cache, nhưng không nên xem nó như một kiến trúc cache nhiều tầng (tiered cache) độc lập với provider. Spring chỉ định thao tác trên các `Cache` đã phân giải; nó không định nghĩa cơ chế đẩy dữ liệu giữa tầng (promotion), sao chép (replication) hoặc quy tắc nhất quán (consistency) nội tại giữa các sản phẩm cache độc lập.

Spring 6.1 còn có một điểm cần lưu ý với truy cập bất đồng bộ/reactive: provider có thể chỉ xác định cache miss sau khi tra cứu bất đồng bộ hoàn tất. Với trường hợp miss chỉ được xác định muộn như vậy, các cache phía sau có thể không còn được tra cứu. Chương về xử lý đồng thời và bất đồng bộ sẽ giải thích giới hạn này sâu hơn.

Với truy cập đồng bộ thông thường, thứ tự khai báo vẫn cần có chủ đích và tất cả tên cache nên được xem là cùng một chính sách caching cấp ứng dụng.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `Cacheable.cacheNames`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#cacheNames())

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-key-design-pitfalls">Các lỗi thiết kế cache key thường gặp</a>

<details>
<summary>Xem chi tiết</summary>

Provider có thể hoạt động hoàn hảo nhưng cache vẫn trả sai dữ liệu nếu ứng dụng thiết kế key sai.

Các lỗi phổ biến gồm:

- **Thiếu chiều dữ liệu:** locale, tenant, authorization scope, version hoặc đầu vào khác làm kết quả thay đổi nhưng không có trong key.
- **Đối tượng key có thể thay đổi:** field tham gia `equals`/`hashCode` bị thay đổi sau khi ghi vào cache, làm việc tra cứu không ổn định.
- **Cách biểu diễn không ổn định:** key dựa vào `toString()`, định danh object hoặc cách biểu diễn không được thiết kế như một định danh bền vững.
- **Xung đột key giữa các method:** hai method dùng chung vùng cache và sinh key bằng nhau dù hợp đồng giá trị khác nhau. Bộ sinh mặc định dùng các tham số, không tự thêm tên method vào định danh key.
- **Không phù hợp provider:** key dùng tốt trong một Java map cục bộ nhưng không phù hợp với yêu cầu serialization hoặc khả năng tương tác của provider từ xa.

Cache key tốt phải có kết quả xác định, chứa đúng các chiều định nghĩa giá trị được cache, có ngữ nghĩa so sánh bằng (`equals`) ổn định và có thể được provider biểu diễn an toàn.

Khi chính sách key đủ quan trọng để phải giải thích dài, thường nên mô hình hóa nó bằng một value object nhỏ hoặc `KeyGenerator` có thể tái sử dụng thay vì một biểu thức chuỗi khó kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)
