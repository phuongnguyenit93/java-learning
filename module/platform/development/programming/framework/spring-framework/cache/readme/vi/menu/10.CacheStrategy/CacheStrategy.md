<a id="back-to-top"></a>

# Thiết kế chiến lược cache toàn diện

## Menu
- [Chọn dữ liệu và phép tính nên cache](#select-cache-candidate)
- [Thiết kế tên cache và quyền sở hữu](#cache-naming-strategy)
- [Thiết kế chiến lược key](#key-strategy)
- [Thiết kế chiến lược ghi cache và invalidation](#population-invalidation-strategy)
- [Đánh đổi giữa độ mới và tính nhất quán](#freshness-consistency-tradeoff)
- [Xử lý cache stampede ở đúng lớp abstraction](#stampede-strategy)
- [Đánh giá tương tác giữa cache và transaction](#transaction-interaction)
- [Chọn provider dựa trên khả năng thực sự cần thiết](#provider-selection)
- [Danh sách kiểm tra trước khi đưa cache vào production](#cache-strategy-checklist)

## <a id="select-cache-candidate">Chọn dữ liệu và phép tính nên cache</a>

<details>
<summary>Xem chi tiết</summary>

Không phải phương thức tốn kém nào cũng là ứng viên cache tốt. Trước tiên cần hỏi cache giúp loại bỏ chi phí nào và đổi lại tạo ra rủi ro gì cho tính đúng đắn.

Một ứng viên tốt thường có nhiều đặc điểm sau:

- cùng một kết quả logic được yêu cầu lặp lại;
- chi phí tính hoặc lấy dữ liệu cao hơn đáng kể so với một lần tra cache;
- dữ liệu thay đổi ít hơn tần suất đọc;
- bên gọi chấp nhận được một mức dữ liệu cũ đã xác định;
- có thể tạo cache key ổn định cho kết quả;
- invalidation gắn được với sự kiện nghiệp vụ rõ ràng hoặc chính sách hết hạn chấp nhận được.

Ứng viên kém gồm dữ liệu thay đổi quá nhanh, kết quả phụ thuộc ngữ cảnh yêu cầu/phiên ẩn, phép tính chỉ dùng một lần, hoặc giá trị mà phiên bản cũ có thể vi phạm tính đúng đắn hay yêu cầu bảo mật.

Nên xem cache là lớp tối ưu hóa dẫn xuất. Nguồn dữ liệu chuẩn phải luôn rõ ràng, và ứng dụng vẫn cần hành vi xác định khi cache miss, cache không khả dụng hoặc dữ liệu đã cũ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-naming-strategy">Thiết kế tên cache và quyền sở hữu</a>

<details>
<summary>Xem chi tiết</summary>

Tên cache nên mô tả một ranh giới quyền sở hữu ổn định, không phải chi tiết triển khai tình cờ. Tên như `product-by-id` hay `pricing-rules` cho biết loại dữ liệu được lưu và phần nào chịu trách nhiệm invalidation.

Các câu hỏi hữu ích:

- Dịch vụ hoặc bounded component nào sở hữu việc ghi cache?
- Thao tác nào được phép đưa dữ liệu vào cache?
- Sự kiện nghiệp vụ nào phải invalidate hoặc refresh?
- Nhiều phương thức có thể dùng chung một vùng cache và không gian key an toàn không?
- Tên này có nên giữ nguyên khi đổi provider không?

Không nên dùng một tên cache cho các kiểu giá trị không liên quan chỉ vì hiện tại cùng provider. Cách đó trộn thiết kế key, invalidation, thời gian lưu giữ và khả năng quan sát, khiến thay đổi sau này khó kiểm soát.

Spring phân giải tên cache qua `CacheManager`/`CacheResolver`; việc tạo vùng cache hay cấu hình provider nên ánh xạ rõ ràng về mô hình quyền sở hữu ở tầng ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="key-strategy">Thiết kế chiến lược key</a>

<details>
<summary>Xem chi tiết</summary>

Cache key phải xác định duy nhất kết quả nghiệp vụ cần tái sử dụng. Chiến lược key phải thể hiện mọi đầu vào có khả năng làm kết quả thay đổi.

Ví dụ, cache một báo giá sản phẩm chỉ theo `productId` là sai nếu kết quả còn phụ thuộc tiền tệ, hạng khách hàng, locale hoặc ngày hiệu lực. Khi đó các kết quả nghiệp vụ khác nhau bị ép vào cùng một entry.

Key tốt thường:

- có tính xác định với các request tương đương;
- ổn định qua nhiều invocation;
- đủ gọn cho provider;
- dựa trên danh tính nghiệp vụ thay vì danh tính của object mutable;
- tương thích serialization nếu cache ở remote;
- có version/namespace khi nhiều dạng giá trị không tương thích có thể cùng tồn tại lúc triển khai.

Cơ chế tạo key mặc định của Spring tiện cho trường hợp đơn giản, nhưng SpEL key rõ ràng hoặc custom `KeyGenerator` thường phù hợp hơn khi danh tính nghiệp vụ phức tạp.

### Tài liệu tham khảo

- Spring Framework Reference — [Default Key Generation](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-default-key)
- Spring Framework Reference — [Custom Key Generation](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-key)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="population-invalidation-strategy">Thiết kế chiến lược ghi cache và invalidation</a>

<details>
<summary>Xem chi tiết</summary>

Việc đưa dữ liệu vào cache và invalidation phải được thiết kế cùng nhau. Cache rất dễ ghi dữ liệu vào nhưng khó giữ đúng nếu không ai sở hữu các sự kiện làm entry trở nên cũ.

Một luồng kiểu cache-aside, trong đó ứng dụng tự xử lý miss rồi tải và ghi kết quả, thường có dạng:

```text
đọc dữ liệu
  -> cache hit -> trả kết quả
  -> cache miss -> tải từ nguồn dữ liệu chuẩn -> ghi cache -> trả về
```

Thao tác thay đổi dữ liệu cần chính sách tương ứng:

- evict key bị ảnh hưởng sau khi cập nhật thành công;
- dùng `@CachePut` để cập nhật entry khi đã biết giá trị mới;
- chỉ clear một vùng rộng khi invalidation theo key không đáng tin cậy;
- kết hợp invalidation theo sự kiện với cơ chế hết hạn của provider như một giới hạn an toàn khi phù hợp.

Nên dẫn xuất quy tắc invalidation từ cùng danh tính nghiệp vụ đã dùng trong chiến lược key. Nếu luồng ghi không xác định được entry nào đã cũ, mô hình cache có thể quá rộng hoặc key space đang thiếu một chiều dữ liệu quan trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="freshness-consistency-tradeoff">Đánh đổi giữa độ mới và tính nhất quán</a>

<details>
<summary>Xem chi tiết</summary>

Caching luôn tạo ra một khoảng thời gian mà dữ liệu có thể cũ. Thiết kế phải nói rõ giá trị được phép cũ bao lâu và điều gì xảy ra khi thao tác cập nhật cạnh tranh với thao tác đọc.

Mức chấp nhận phụ thuộc domain:

- mô tả sản phẩm có thể chịu dữ liệu cũ vài phút;
- dữ liệu phân quyền thường cần bảo đảm chặt hơn;
- tồn kho hoặc giá trị tài chính có thể cần versioning rõ ràng hoặc đọc thẳng nguồn dữ liệu chuẩn cho quyết định quan trọng.

TTL của provider có thể giới hạn tuổi entry, nhưng cơ chế hết hạn một mình không định nghĩa tính nhất quán của ứng dụng. Hai node dùng cache cục bộ độc lập có thể hết hạn ở hai thời điểm khác nhau. Cache phân tán vẫn có cơ chế replication hoặc hành vi lỗi ảnh hưởng khả năng nhìn thấy dữ liệu.

Hãy chọn mức nhất quán mong muốn trước, sau đó chọn invalidation, expiry, topology và cơ chế phối hợp transaction đủ mạnh để đáp ứng mức đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stampede-strategy">Xử lý cache stampede ở đúng lớp abstraction</a>

<details>
<summary>Xem chi tiết</summary>

Cache stampede nên được xử lý ở lớp nhỏ nhất thực sự cung cấp mức phối hợp cần thiết.

Trong một JVM và một thao tác Spring Cache, `@Cacheable(sync=true)` có thể đủ nếu provider hỗ trợ nạp đồng bộ. Khi có nhiều instance ứng dụng, cơ chế đồng bộ cục bộ không thể ngăn mọi node cùng tính một giá trị đang thiếu.

Hệ thống lớn hơn có thể cần tính năng riêng của provider, phối hợp phân tán, gộp yêu cầu, làm nóng cache trước, làm mới sớm theo xác suất hoặc quy trình làm mới qua queue. Mỗi cơ chế có đánh đổi khác nhau về lỗi và độ trễ.

Không nên thêm khóa phân tán mặc định. Trước tiên hãy ước lượng chi phí của việc tính lặp, mức đồng thời theo key và tác động khi lỗi. Trong nhiều trường hợp, cho phép một ít tính lặp rẻ và an toàn hơn việc thêm một phụ thuộc phối hợp toàn cục.

### Tài liệu tham khảo

- Spring Framework Reference — [Synchronized Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-cacheable-synchronized)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transaction-interaction">Đánh giá tương tác giữa cache và transaction</a>

<details>
<summary>Xem chi tiết</summary>

Nếu cached value phản chiếu dữ liệu transactional, cần quyết định thay đổi cache được phép nhìn thấy lúc nào so với commit.

Các câu hỏi cần trả lời:

- Cache put hoặc eviction có thể xảy ra trước khi database commit không?
- Nếu transaction rollback sau đó thì sao?
- Mã có đọc cache lại bên trong cùng transaction không?
- Có dùng thao tác cần hiệu lực tức thời như `putIfAbsent`, `evictIfPresent` hoặc `invalidate` không?
- Provider có được chia sẻ cho service khác không tham gia cùng transaction không?

Transaction-aware cache decoration của Spring có thể hoãn `put`, `evict` và `clear` thông thường tới sau khi commit thành công. Cơ chế này giảm một khoảng không nhất quán phổ biến nhưng không tạo ngữ nghĩa distributed transaction.

Khi nguồn dữ liệu chuẩn và cache nằm ở hai hệ thống khác nhau, phải thiết kế cho lỗi từng phần. Database commit thành công nhưng cache invalidation thất bại vẫn có thể xảy ra; tùy rủi ro nghiệp vụ mà cần monitoring, retry, expiry hoặc reconciliation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-selection">Chọn provider dựa trên khả năng thực sự cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

Chọn provider theo hành vi thực sự cần, không theo độ phổ biến.

Các khả năng nên đánh giá:

- topology cục bộ hay phân tán;
- mục tiêu độ trễ và throughput;
- TTL/TTI và eviction policy;
- giới hạn kích thước hoặc kiểm soát bộ nhớ;
- atomic loading và hành vi `sync=true`;
- hỗ trợ truy xuất bất đồng bộ cho Spring 6.1;
- serialization và schema evolution;
- replication và hành vi khi lỗi;
- khả năng quan sát và công cụ vận hành;
- yêu cầu tích hợp transaction-aware.

`ConcurrentMapCache` cố ý đơn giản. Caffeine là lựa chọn in-memory cục bộ mạnh với policy phong phú hơn. JCache cung cấp bề mặt tích hợp chuẩn hóa cho provider tương thích. Remote store như Redis thêm ngữ nghĩa trạng thái chia sẻ qua mạng và cần được đánh giá theo khả năng riêng của provider.

Spring abstraction giúp giữ ổn định chính sách của ứng dụng, nhưng việc chọn provider vẫn là quyết định kiến trúc vì hiệu năng và bảo đảm nhất quán đến từ provider.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-strategy-checklist">Danh sách kiểm tra trước khi đưa cache vào production</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi xem thiết kế cache là sẵn sàng cho môi trường production, cần kiểm tra toàn bộ chính sách chứ không chỉ xác nhận `@Cacheable` có cache hit.

Danh sách kiểm tra:

- **Ứng viên** — Giá trị có đủ tốn kém và được tái sử dụng đủ nhiều để đáng cache không?
- **Nguồn dữ liệu chuẩn** — Dữ liệu có thẩm quyền đã rõ chưa?
- **Tên/quyền sở hữu** — Mỗi vùng cache có chủ sở hữu và mục đích ổn định không?
- **Key** — Key có chứa mọi đầu vào làm kết quả thay đổi không?
- **Ghi dữ liệu vào cache** — Đường xử lý cache miss có giới hạn và quan sát được không?
- **Invalidation** — Luồng ghi nào làm key nào trở nên cũ?
- **Độ mới** — Mức dữ liệu cũ tối đa chấp nhận được là bao nhiêu?
- **Xử lý đồng thời** — Nhiều bên gọi cùng miss một key thì điều gì xảy ra?
- **Async/reactive** — Provider có hỗ trợ non-blocking retrieval semantics cần thiết không?
- **Transaction** — Cache mutation có được căn theo commit khi cần không?
- **Chính sách provider** — TTL, size, eviction, serialization và topology đã cấu hình có chủ đích chưa?
- **Lỗi** — Cache read/write/eviction lỗi thì ứng dụng làm gì?
- **Khả năng quan sát** — Có nhìn thấy hit rate, miss rate, độ trễ khi nạp, lỗi, eviction và áp lực dung lượng không?
- **Khôi phục** — Có rebuild hoặc invalidate cache an toàn được không?

Chiến lược cache chỉ hoàn chỉnh khi các câu trả lời trên khớp với nhau. Spring Cache cung cấp abstraction ở tầng ứng dụng; AOP quyết định cơ chế chặn lời gọi, hỗ trợ transaction phối hợp thời điểm commit, và provider quyết định hành vi lưu trữ/xử lý đồng thời/topology.

### Tài liệu tham khảo

- Spring Framework Reference — [Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)

</details>

- [Quay lại đầu trang](#back-to-top)
