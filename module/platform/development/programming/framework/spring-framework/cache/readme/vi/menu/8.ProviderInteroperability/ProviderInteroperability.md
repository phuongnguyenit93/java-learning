<a id="back-to-top"></a>

# Cache provider và ranh giới tương thích

## Menu
- [Cache dựa trên ConcurrentMap](#concurrent-map-provider)
- [Tích hợp Caffeine ở lớp Spring Cache](#caffeine-provider)
- [Tích hợp JCache](#jcache-integration)
- [Annotation của Spring và JCache](#spring-vs-jcache-annotations)
- [Tùy biến Cache và CacheManager adapter](#custom-cache-adapter)
- [TTL, TTI, eviction và chính sách kích thước thuộc provider](#provider-policy-boundary)
- [Serialization, xử lý đồng thời và tính nhất quán không được abstraction đảm bảo](#serialization-and-consistency)
- [Cache cục bộ và cache phân tán ở ranh giới provider](#local-vs-distributed-cache)

## <a id="concurrent-map-provider">Cache dựa trên ConcurrentMap</a>

<details>
<summary>Xem chi tiết</summary>

`ConcurrentMapCache` là cách triển khai `Cache` đơn giản, chạy trong cùng tiến trình và dựa trên collection đồng thời của JDK. `ConcurrentMapCacheManager` có thể tạo cache theo tên khi cần hoặc quản lý một tập tên cache cố định.

Điểm mạnh của nó là đơn giản: không cần dịch vụ bên ngoài, thao tác tra cứu nằm ngay trong JVM và rất phù hợp cho kiểm thử hoặc caching cơ bản. Giới hạn cũng đến từ chính sự đơn giản đó. Cache manager này không cung cấp cơ chế cấu hình phong phú cho expiration, eviction theo kích thước, persistence hay phối hợp giữa nhiều node.

`ConcurrentMapCache` có thể thích nghi giá trị `null` vì `ConcurrentHashMap` mặc định không lưu được Java `null` trực tiếp. Từ Spring 6.1, nó cũng hỗ trợ `Cache.retrieve` theo kiểu best-effort cho truy cập bất đồng bộ. Các tiện ích này không biến nó thành distributed cache hoặc cache có policy phức tạp.

Nên dùng khi ngữ nghĩa cache mong muốn thực sự chỉ ở phạm vi tiến trình và đơn giản, hoặc khi cần cách triển khai nhẹ cho kiểm thử.

### Tài liệu tham khảo

- Spring Framework Reference — [JDK `ConcurrentMap`-based Cache](https://docs.spring.io/spring-framework/reference/integration/cache/store-configuration.html#cache-store-configuration-jdk-concurrentmap)
- Spring Framework Javadoc — [`ConcurrentMapCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/concurrent/ConcurrentMapCacheManager.html)
- Spring Framework Javadoc — [`ConcurrentMapCache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/concurrent/ConcurrentMapCache.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="caffeine-provider">Tích hợp Caffeine ở lớp Spring Cache</a>

<details>
<summary>Xem chi tiết</summary>

Spring tích hợp Caffeine thông qua `CaffeineCache` và `CaffeineCacheManager`. Các type của Spring chuyển Caffeine vào contract `Cache`/`CacheManager` chung, còn việc tuning đặc thù Caffeine vẫn ở phía provider.

`CaffeineCacheManager` có thể tạo cache động và nhận cấu hình qua Caffeine builder hoặc specification. Các chính sách như maximum size, expiration, refresh, statistics và hành vi loader thuộc Caffeine, không phải ngữ nghĩa dùng chung của annotation Spring Cache.

Với asynchronous caching của Spring Framework 6.1, `CaffeineCacheManager` có thể expose asynchronous cache của Caffeine khi bật `setAsyncCacheMode(true)`. Khả năng này quan trọng khi method trả `CompletableFuture`, `Mono` hoặc `Flux`.

Vì vậy nên tách hai quyết định: dùng Spring Cache annotation, key và invalidation policy để thể hiện caching intent ở tầng ứng dụng; dùng Caffeine configuration để quyết định cơ chế của local cache.

### Tài liệu tham khảo

- Spring Framework Reference — [Caffeine Cache](https://docs.spring.io/spring-framework/reference/integration/cache/store-configuration.html#cache-store-configuration-caffeine)
- Spring Framework Javadoc — [`CaffeineCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/caffeine/CaffeineCacheManager.html)
- Spring Framework Javadoc — [`CaffeineCache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/caffeine/CaffeineCache.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jcache-integration">Tích hợp JCache</a>

<details>
<summary>Xem chi tiết</summary>

JCache, chuẩn hóa dưới tên JSR-107, định nghĩa một Java caching API mà nhiều nhà cung cấp cache có thể triển khai. Spring có thể bọc một `javax.cache.CacheManager` bằng `JCacheCacheManager`, rồi cung cấp các cache đó qua chính abstraction `CacheManager` của Spring.

```text
chính sách cache của ứng dụng
        |
Spring Cache abstraction
        |
JCacheCacheManager / JCacheCache
        |
JSR-107 provider
```

Vì vậy JCache là một lựa chọn cho khả năng tương tác. Mã do Spring quản lý vẫn có thể dùng Spring Cache abstraction trong khi cách triển khai phía dưới tuân theo JSR-107. Cấu hình provider vẫn quyết định expiry, serialization, topology và các khả năng riêng.

Tích hợp JCache không có nghĩa mọi JCache provider có hành vi giống hệt nhau. Chuẩn chỉ tạo một bề mặt API chung; đặc tính vận hành vẫn phụ thuộc cách triển khai cụ thể.

### Tài liệu tham khảo

- Spring Framework Reference — [JSR-107 Cache](https://docs.spring.io/spring-framework/reference/integration/cache/store-configuration.html#cache-store-configuration-jsr107)
- Spring Framework Javadoc — [`JCacheCacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/jcache/JCacheCacheManager.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-vs-jcache-annotations">Annotation của Spring và JCache</a>

<details>
<summary>Xem chi tiết</summary>

Spring hỗ trợ cả cache annotation riêng của Spring và mô hình annotation của JCache. Chúng giải quyết các bài toán tương tự nhưng không có ngữ nghĩa hoàn toàn đồng nhất.

Ví dụ:

- Spring `@Cacheable` gần với JCache `@CacheResult`, nhưng JCache có thêm cơ chế riêng để cache exception và điều khiển việc phương thức có buộc phải chạy hay không.
- Spring `@CachePut` cache kết quả của phương thức; JCache `@CachePut` xác định giá trị cần cache qua parameter được đánh dấu `@CacheValue`.
- Spring `@CacheEvict` gần với JCache `@CacheRemove`; evict cả cache tương ứng với `@CacheRemoveAll`.
- Hai mô hình có type riêng cho resolver/key generation và annotation cấu hình khác nhau.

Spring xử lý JCache annotation bằng hạ tầng của Spring, nên dùng annotation chuẩn không buộc ứng dụng rời khỏi cơ chế caching do Spring quản lý. Nên chọn một mô hình annotation nhất quán dựa trên nhu cầu portability và tính năng, thay vì trộn chúng tùy ý trên cùng thao tác.

### Tài liệu tham khảo

- Spring Framework Reference — [JCache (JSR-107) Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/jsr-107.html)
- Spring Framework Javadoc — [JCache declarative configuration package](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/jcache/config/package-summary.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-cache-adapter">Tùy biến Cache và CacheManager adapter</a>

<details>
<summary>Xem chi tiết</summary>

Ranh giới provider của Spring được giữ nhỏ một cách có chủ đích. Một custom provider có thể tích hợp bằng cách implement `Cache` và cung cấp các instance đó qua `CacheManager`. Cache interceptor tiếp tục làm việc với contract chung của Spring, còn adapter chuyển các thao tác sang native store.

Custom `Cache` phải mô tả đúng các ngữ nghĩa quan trọng: miss và nullable value được biểu diễn ra sao, `get(key, Callable)` có atomic loading không, `putIfAbsent` có atomic không, thao tác yêu cầu hiệu lực tức thời có thật sự thấy hiệu lực ngay không, và các phương thức `retrieve` của Spring 6.1 có thật sự non-blocking không.

Custom `CacheManager` quyết định cache name ánh xạ tới cache instance nào và có tạo động cache name chưa biết hay không. Nếu adapter che giấu giới hạn của provider hoặc hứa bảo đảm mạnh hơn native backend, các annotation ở tầng trên sẽ trở nên gây hiểu nhầm.

Adapter nên chuyển khả năng của provider sang Spring contract, không tự tạo ra khả năng mà backend không có.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`Cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)
- Spring Framework Javadoc — [`CacheManager`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/CacheManager.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-policy-boundary">TTL, TTI, eviction và chính sách kích thước thuộc provider</a>

<details>
<summary>Xem chi tiết</summary>

Spring Cache định nghĩa khi nào ứng dụng muốn tra cứu, đưa dữ liệu vào cache hoặc evict một giá trị. Nó không cung cấp annotation portable cho mọi chính sách lưu giữ dữ liệu.

Các chính sách sau thuộc cấu hình provider:

- time to live (TTL) hoặc expiry theo thời gian;
- time to idle (TTI), nếu provider hỗ trợ;
- giới hạn số lượng entry hoặc weight;
- size/weight-based eviction;
- refresh policy;
- persistence hoặc disk tier;
- replication hay distributed topology.

Sự phân tách này có lợi: code ứng dụng có thể giữ ổn định cache name, key và quy tắc invalidation trong khi cấu hình provider thay đổi theo môi trường triển khai. Đồng thời, đổi provider phải đi kèm việc đánh giá lại tính tương đương của chính sách; giữ nguyên `@Cacheable` không chứng minh hành vi expiration hay eviction còn tương đương.

Với Caffeine, cấu hình các khía cạnh này bằng Caffeine builder/specification. Với JCache provider hoặc distributed store, dùng mô hình cấu hình của provider đó.

### Tài liệu tham khảo

- Spring Framework Reference — [Configuring the Cache Storage](https://docs.spring.io/spring-framework/reference/integration/cache/store-configuration.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="serialization-and-consistency">Serialization, xử lý đồng thời và tính nhất quán không được abstraction đảm bảo</a>

<details>
<summary>Xem chi tiết</summary>

Spring `Cache` abstraction chuẩn hóa một tập thao tác hữu ích, không chuẩn hóa toàn bộ mô hình nhất quán của mọi backing store. Nó không bảo đảm hai provider có cùng định dạng serialization, mức độ khóa, tính atomic, độ trễ replication, chế độ lỗi hay bảo đảm về khả năng nhìn thấy dữ liệu giữa các node.

Những câu hỏi sau phụ thuộc provider:

- Key/value được giữ bằng object reference trong JVM cục bộ hay phải serialize?
- Sau một `put`, instance ứng dụng khác có nhìn thấy ngay không?
- Việc load một key có atomic giữa các thread, giữa các process, hay chỉ trong một cache instance?
- Invalidation được truyền đồng bộ, bất đồng bộ hay theo mô hình nhất quán cuối cùng (eventual consistency)?
- Network partition hoặc sự cố một phần của provider được xử lý ra sao?

Hợp đồng Spring có yêu cầu cụ thể cho một số thao tác, ví dụ ý định nguyên tử của `putIfAbsent` và kỳ vọng hiệu lực tức thời của `evictIfPresent`/`invalidate`. Tuy vậy cách triển khai vẫn có chi tiết riêng theo provider, nên thiết kế cho môi trường production phải đọc tài liệu của provider gốc.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`Cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/Cache.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="local-vs-distributed-cache">Cache cục bộ và cache phân tán ở ranh giới provider</a>

<details>
<summary>Xem chi tiết</summary>

Local cache giữ dữ liệu trong một process của ứng dụng. Nó có độ trễ rất thấp và tránh một lần đi qua mạng, nhưng mỗi process sở hữu entry riêng. Khi có nhiều instance ứng dụng, cùng một key có thể tồn tại thành nhiều bản cache độc lập và invalidation phải tính đến topology đó.

Distributed cache đặt trạng thái cache dùng chung phía sau một provider qua mạng. Nhiều instance ứng dụng có thể dùng chung một tập dữ liệu, nhưng mỗi lần truy cập mang thêm độ trễ mạng, chi phí serialization, chế độ lỗi từ xa và mô hình nhất quán của provider.

Không có lựa chọn nào luôn tốt hơn. Local cache phù hợp với dữ liệu tham chiếu hoặc phép tính chấp nhận mức dữ liệu cũ khác nhau giữa các instance. Distributed cache hữu ích khi việc chia sẻ trạng thái cache giữa các instance đủ quan trọng để chấp nhận chi phí vận hành cao hơn.

Spring Cache cho phép cả hai implement cùng `Cache` contract ở tầng ứng dụng, nhưng kiến trúc vẫn phải suy luận topology một cách rõ ràng. Serialization, TTL, data structure, clustering và vận hành đặc thù Redis thuộc module Spring Data Redis/provider, không thuộc chapter về abstraction này.

### Tài liệu tham khảo

- Spring Framework Reference — [Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)

</details>

- [Quay lại đầu trang](#back-to-top)
