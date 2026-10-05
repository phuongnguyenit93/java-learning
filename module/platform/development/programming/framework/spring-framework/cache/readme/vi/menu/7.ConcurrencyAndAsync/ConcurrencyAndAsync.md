<a id="back-to-top"></a>

# Xử lý đồng thời, cache stampede và caching bất đồng bộ

## Menu
- [Cache miss đồng thời và việc tính lặp](#concurrent-cache-miss)
- [Ý nghĩa của @Cacheable(sync=true)](#cacheable-sync)
- [Các ràng buộc của sync=true](#cacheable-sync-restrictions)
- [Tra cứu bất đồng bộ trong Cache SPI từ Spring 6.1](#cache-async-retrieve)
- [Cache kết quả CompletableFuture](#completable-future-caching)
- [Cache kết quả Mono và Flux](#reactive-return-types)
- [Khả năng provider cần có cho caching bất đồng bộ](#async-provider-capability)
- [Multi-cache và cache miss được xác định muộn](#multi-cache-late-miss)
- [Ranh giới của caching reactive mức thô](#reactive-cache-boundary)

## <a id="concurrent-cache-miss">Cache miss đồng thời và việc tính lặp</a>

<details>
<summary>Xem chi tiết</summary>

Khi không có cơ chế đồng bộ, nhiều bên gọi có thể cùng cache miss cho một key gần như cùng lúc. Mỗi bên sau đó đều chạy phương thức tốn kém trước khi bất kỳ bên nào kịp ghi kết quả vào cache. Cuối cùng cache vẫn có dữ liệu, nhưng phần công việc đắt đỏ đã bị thực hiện lặp.

```text
T1: miss -> tính -----------------> put
T2:   miss -> tính -----------------> put
T3:     miss -> tính -----------------> put
```

Khi lượng công việc lặp đủ lớn để gây áp lực lên database, dịch vụ từ xa hoặc CPU, hiện tượng này thường được gọi là cache stampede hay thundering herd. Spring Cache abstraction mặc định không thêm một khóa toàn cục. Hành vi đồng thời cuối cùng phụ thuộc vào thao tác cache và provider.

Câu hỏi thiết kế đầu tiên là việc tính lặp chỉ gây lãng phí hay có thể gây quá tải. Với thao tác rẻ và idempotent, một ít tính lặp có thể chấp nhận được. Với thao tác đắt hoặc bị giới hạn tần suất, cần synchronized loading hoặc chiến lược mạnh hơn ở provider.

### Tài liệu tham khảo

- Spring Framework Reference — [Synchronized Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-synchronized)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cacheable-sync">Ý nghĩa của @Cacheable(sync=true)</a>

<details>
<summary>Xem chi tiết</summary>

`@Cacheable(sync=true)` yêu cầu cache provider đồng bộ việc load cho cùng một key. Có thể hiểu là bên gọi đầu tiên thực hiện phép tính, còn các bên gọi cạnh tranh cùng key chờ giá trị xuất hiện.

```java
@Cacheable(cacheNames = "catalog", key = "#id", sync = true)
public Product load(String id) {
    return repository.findRequired(id);
}
```

Spring chuyển hành vi này qua hợp đồng nạp dữ liệu của cache, đáng chú ý là `Cache.get(key, Callable)` với truy cập đồng bộ. Annotation mô tả đây là một gợi ý vì tính nguyên tử và cơ chế khóa thực tế do provider quyết định. Nếu ứng dụng phụ thuộc vào hành vi này, `Cache` tùy biến phải thực thi hợp đồng phù hợp.

`sync=true` phối hợp các bên gọi của một key trong một thao tác cache. Nó không phải khóa phân tán tổng quát và không tự phối hợp các đường gọi khác bỏ qua cùng cache/provider.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`@Cacheable.sync()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#sync())
- Spring Framework Javadoc — [`Cache.get(Object, Callable)`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html#get(java.lang.Object,java.util.concurrent.Callable))

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cacheable-sync-restrictions">Các ràng buộc của sync=true</a>

<details>
<summary>Xem chi tiết</summary>

Synchronized caching cố ý thu hẹp mô hình thao tác. Spring quy định ba ràng buộc quan trọng cho `sync=true`:

- Không hỗ trợ `unless`.
- Chỉ được chỉ định đúng một cache.
- Không được kết hợp thao tác cache khác trên cùng phương thức.

Các giới hạn này giữ một lần nạp đồng bộ gắn với một quyết định cache/key duy nhất. Nếu thiết kế cần nhiều vùng cache, cơ chế từ chối dựa trên kết quả, hoặc đồng thời put/evict, nên mô hình hóa các mối quan tâm đó rõ ràng thay vì ép chúng vào một `@Cacheable(sync=true)`.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`@Cacheable.sync()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#sync())

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-async-retrieve">Tra cứu bất đồng bộ trong Cache SPI từ Spring 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 bổ sung các phương thức tra cứu bất đồng bộ vào `Cache` SPI:

```java
CompletableFuture<?> retrieve(Object key)

<T> CompletableFuture<T> retrieve(
        Object key,
        Supplier<CompletableFuture<T>> valueLoader)
```

Điểm cốt lõi là thao tác tra cứu không được chặn luồng. Provider có thể xác định ngay một key không có mapping, hoặc chỉ biết cache miss sau khi công việc bất đồng bộ hoàn tất. Vì vậy API biểu diễn khác nhau giữa miss được xác định sớm (early miss) và miss được xác định muộn (late-determined miss) thông qua giá trị hoặc future trả về.

Phần mở rộng SPI này cho phép caching dựa trên annotation thích nghi với `CompletableFuture` và kiểu trả về reactive mà không buộc mọi lần tra cache phải dùng `get` theo kiểu chặn luồng. Khả năng của provider vẫn là điều kiện thực tế; phương thức nghiệp vụ trả về future không tự biến một hệ thống cache phía sau vốn chặn luồng thành hệ thống bất đồng bộ.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`Cache.retrieve(...)`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)
- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completable-future-caching">Cache kết quả CompletableFuture</a>

<details>
<summary>Xem chi tiết</summary>

Từ Spring Framework 6.1, annotation cache xử lý được giá trị trả về dạng `CompletableFuture`. Khi miss, phương thức có thể trả future ngay; Spring cache giá trị mà future tạo ra khi hoàn thành thành công. Khi hit, giá trị đã cache được trả lại cho bên gọi thông qua `CompletableFuture`.

```java
@Cacheable("books")
public CompletableFuture<Book> findBook(String isbn) {
    return client.fetchBook(isbn);
}
```

Cache lưu giá trị nghiệp vụ `Book`, thay vì coi chính đối tượng `CompletableFuture` là giá trị cần giữ lâu dài. Các bên gọi sau nhận một future đại diện cho kết quả đã cache.

Cache phải hỗ trợ hợp đồng tra cứu bất đồng bộ để mô hình này thực sự không chặn luồng. `sync=true` cũng có thể kết hợp với caching dựa trên future khi provider hỗ trợ để nhiều lần miss đồng thời chỉ tính giá trị một lần.

### Tài liệu tham khảo

- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-return-types">Cache kết quả Mono và Flux</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 cũng thích nghi annotation-driven caching với kiểu trả về của Reactor. Với `Mono<T>`, Spring cache object được phát ra khi nó xuất hiện; khi cache hit, giá trị đó được thích nghi lại thành `Mono`.

Với `Flux<T>`, Spring thu toàn bộ phần tử được phát ra thành một `List` và cache danh sách đó sau khi publisher hoàn tất. Khi cache hit, danh sách được chuyển lại thành `Flux`.

```java
@Cacheable("authors")
public Flux<Book> findByAuthor(String author) {
    return repository.findByAuthor(author);
}
```

Quy tắc của `Flux` đặc biệt quan trọng: caching dựa trên kết quả đã thu thập đầy đủ, không phải cache độc lập từng phần tử trong stream. Publisher vô hạn, chuỗi dữ liệu rất lớn hoặc pipeline phụ thuộc ngữ cảnh tại thời điểm subscribe thường không phù hợp với annotation-driven caching.

### Tài liệu tham khảo

- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-provider-capability">Khả năng provider cần có cho caching bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

Các annotation hỗ trợ bất đồng bộ cần một cách triển khai cache có cơ chế tra cứu tương thích. `ConcurrentMapCacheManager` của Spring có thể thích nghi với `Cache.retrieve`; `CaffeineCacheManager` có thể dùng chế độ cache bất đồng bộ của Caffeine khi bật `setAsyncCacheMode(true)`.

Khả năng của provider không chỉ nằm ở chữ ký phương thức. Một hệ thống phía sau có thể trả future nhưng vẫn thực hiện I/O theo kiểu chặn luồng, hoặc chỉ xác định cache miss sau một vòng gọi tới hệ thống từ xa. Các chi tiết này tác động đến độ trễ, cách dùng luồng, hành vi multi-cache và ý nghĩa thực tế của `sync=true`.

Khi hành vi bất đồng bộ là yêu cầu quan trọng, phải kiểm tra ngữ nghĩa mà provider công bố cho retrieval, loading, xử lý `null`, cancellation, lan truyền lỗi và synchronization. Spring abstraction định nghĩa hợp đồng tương tác nhưng không xóa đi mô hình xử lý đồng thời của hệ thống phía sau.

### Tài liệu tham khảo

- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)
- Spring Framework Javadoc — [`ConcurrentMapCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/concurrent/ConcurrentMapCacheManager.html)
- Spring Framework Javadoc — [`CaffeineCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/caffeine/CaffeineCacheManager.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="multi-cache-late-miss">Multi-cache và cache miss được xác định muộn</a>

<details>
<summary>Xem chi tiết</summary>

Với truy cập đồng bộ thông thường, nhiều cache name trong `@Cacheable` được kiểm tra theo thứ tự khai báo để tìm hit, và giá trị mới được ghi vào các cache tham gia. Truy cập async/reactive thêm một ranh giới phụ thuộc provider.

Một số provider có thể báo miss ngay. Provider khác chỉ xác định miss sau khi tra cứu bất đồng bộ hoàn tất. Khi miss được xác định muộn, Spring có thể không còn tiếp tục hỏi các cache đứng sau trong danh sách. Vì vậy không nên hiểu danh sách cache như một chuỗi fallback độc lập với provider trong chế độ async.

Điều này đặc biệt quan trọng với ý tưởng “local cache rồi remote cache”. Một mô hình phân tầng như vậy cần ngữ nghĩa rõ ràng về thứ tự tra cứu, thời điểm xác định miss, thao tác ghi, invalidation và xử lý lỗi. Chỉ khai báo nhiều tên trong `@Cacheable` không bảo đảm kiến trúc đó cho provider bất đồng bộ.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`@Cacheable.cacheNames()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/Cacheable.html#cacheNames())
- Spring Framework Javadoc — [`Cache.retrieve(Object)`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html#retrieve(java.lang.Object))

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactive-cache-boundary">Ranh giới của caching reactive mức thô</a>

<details>
<summary>Xem chi tiết</summary>

Spring mô tả rõ annotation-driven reactive caching là cơ chế ở mức thô (coarse-grained). Nó cache object được `Mono` phát ra, hoặc một `List` đã thu thập đầy đủ từ `Flux`. Nó không mô hình hóa operator, subscriber context, demand, vòng đời từng phần tử hay ngữ nghĩa backpressure của reactive pipeline.

Cách này phù hợp khi phương thức reactive về bản chất là một yêu cầu bất đồng bộ để lấy một tập kết quả ổn định. Nó kém phù hợp khi chính hành vi của stream mới là thứ cần bảo toàn, dữ liệu không có điểm kết thúc, hoặc mỗi subscriber cần ngữ cảnh thực thi riêng.

Hãy dùng Spring Cache abstraction khi thứ cần cache vẫn là kết quả của phương thức. Nếu caching phải tham gia sâu vào việc ghép stream, backpressure, invalidation phân tán hoặc chính sách theo từng phần tử, cần một thiết kế reactive-aware ở tầng dữ liệu/provider.

### Tài liệu tham khảo

- Spring Framework Reference — [Caching with CompletableFuture and Reactive Return Types](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-reactive)

</details>

- [Quay lại đầu trang](#back-to-top)
