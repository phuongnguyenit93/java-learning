<a id="back-to-top"></a>

# Tư duy về cache và ranh giới của Spring Cache

## Menu
- [Cache tồn tại để giải quyết vấn đề gì?](#cache-purpose)
- [Cache khác nguồn dữ liệu chuẩn như thế nào?](#cache-vs-source-of-truth)
- [Cache khác buffer và lưu trữ bền vững như thế nào?](#cache-vs-buffer-and-storage)
- [Khi nào một kết quả phù hợp để đưa vào cache?](#cache-candidate-criteria)
- [Spring Cache trừu tượng hóa điều gì?](#spring-cache-abstraction-boundary)
- [Những gì vẫn thuộc trách nhiệm của cache provider?](#provider-owned-concerns)

## <a id="cache-purpose">Cache tồn tại để giải quyết vấn đề gì?</a>

<details>
<summary>Xem chi tiết</summary>

Cache giúp tránh lặp lại công việc khi cùng một kết quả logic được yêu cầu nhiều lần. Thay vì luôn thực hiện lại phép tính tốn kém, truy vấn database, lời gọi dịch vụ từ xa hoặc đọc file, ứng dụng có thể tái sử dụng giá trị đã tính trước đó dựa trên một key.

Luồng cơ bản là:

```text
request
  ↓
tra cứu key trong cache
  ├─ hit  → trả về giá trị đã cache
  └─ miss → tính/tải giá trị → ghi vào cache → trả về
```

Cách này có thể giảm latency và tải lên hệ thống phía sau, nhưng đồng thời tạo thêm một bản sao dữ liệu có khả năng cũ hơn nguồn dữ liệu chuẩn. Vì vậy caching không chỉ là tối ưu hiệu năng; nó còn là quyết định về tính nhất quán. Một thiết kế cache tốt phải trả lời được cả hai câu hỏi: **ta muốn tránh lặp lại công việc nào?** và **kết quả được phép cũ đến mức nào?**

Spring Cache tập trung vào hợp đồng caching ở cấp lời gọi phương thức. Nó cho phép mã ứng dụng mô tả rằng một kết quả có thể được tái sử dụng mà không phải tự viết lại mẫu tra cứu/ghi cache trong từng phương thức dịch vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-vs-source-of-truth">Cache khác nguồn dữ liệu chuẩn như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Nguồn dữ liệu chuẩn (source of truth) là nơi chứa trạng thái có thẩm quyền mà ứng dụng tin cậy khi cần tính đúng đắn. Cache thường chỉ là một bản sao dẫn xuất, có thể thay thế, được tối ưu cho việc truy cập nhanh hơn.

Sự khác biệt này quyết định cách xử lý eviction và lỗi. Nếu một cache entry biến mất, ứng dụng thường phải có khả năng dựng lại nó từ nguồn dữ liệu chuẩn. Nếu dữ liệu chuẩn biến mất, cache không nên được xem như cơ chế phục hồi đáng tin cậy trừ khi hệ thống được thiết kế rõ ràng cho mục đích đó.

Ví dụ, nếu thông tin sản phẩm nằm trong database thì một `ProductView` trong cache có thể bị xóa rồi tạo lại. Database vẫn là nguồn dữ liệu chuẩn ngay cả khi cache đang giữ một bản sao có timestamp mới hơn hoặc chưa hết hạn.

Điều này cũng giải thích vì sao luồng ghi cần chính sách rõ ràng. Nếu cache được cập nhật nhưng việc ghi vào nguồn dữ liệu chuẩn thất bại, ứng dụng có thể trông như đã thành công trong khi trạng thái thực tế vẫn sai. Các chương sau sẽ đi sâu vào invalidation và chiến lược cập nhật; ở đây cần giữ mô hình tư duy: **trạng thái cache thường phụ thuộc vào trạng thái có thẩm quyền**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-vs-buffer-and-storage">Cache khác buffer và lưu trữ bền vững như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Các cơ chế này đều có thể giữ dữ liệu tạm thời, nhưng giải quyết những vấn đề khác nhau.

| Cơ chế | Mục đích chính | Đặc điểm thường gặp |
| --- | --- | --- |
| Cache | Tái sử dụng dữ liệu hoặc kết quả tính toán | Entry có thể bị bỏ và dựng lại |
| Buffer | Làm mượt hoặc tách nhịp luồng dữ liệu | Dữ liệu thường đang chờ được tiêu thụ hoặc truyền đi |
| Lưu trữ bền vững | Giữ trạng thái có thẩm quyền | Dữ liệu được kỳ vọng tồn tại qua restart và eviction thông thường |

Ví dụ, queue buffer có thể tồn tại vì bên tạo dữ liệu nhanh hơn bên tiêu thụ. Cache tồn tại vì việc tải hoặc tính lại cùng một kết quả là lãng phí. Database tồn tại vì bản thân trạng thái cần được lưu giữ.

Một công nghệ có thể đảm nhiệm nhiều vai trò, nhưng ý định thiết kế vẫn quan trọng. Redis có thể được dùng làm cache, kho dữ liệu hoặc hạ tầng messaging. Spring Cache không định nghĩa lại các vai trò gốc đó; nó chỉ mô hình hóa góc nhìn caching thông qua trừu tượng Spring Cache.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-candidate-criteria">Khi nào một kết quả phù hợp để đưa vào cache?</a>

<details>
<summary>Xem chi tiết</summary>

Một kết quả là ứng viên cache tốt khi nó đủ tốn kém để tạo ra, được yêu cầu lặp lại, và đủ ổn định để có thể tái sử dụng trong một khoảng thời gian chấp nhận được.

Các câu hỏi nên kiểm tra gồm:

- **Chi phí:** Phép tính, truy cập database hoặc lời gọi dịch vụ từ xa ban đầu có thực sự tốn kém không?
- **Mức tái sử dụng:** Cùng một key logic có được yêu cầu đủ thường xuyên để tạo cache hit không?
- **Độ đầy đủ của key:** Key đã chứa mọi chiều dữ liệu có thể làm thay đổi kết quả chưa?
- **Độ mới:** Bên gọi có chấp nhận kết quả cho tới lúc refresh hoặc invalidation không?
- **Kích thước:** Giá trị có đủ nhỏ để giữ nhiều entry một cách hợp lý không?
- **Lỗi:** Nếu cache không khả dụng, ứng dụng vẫn có thể tải dữ liệu từ nguồn gốc không?

Caching thường không phù hợp khi mỗi yêu cầu gần như duy nhất, dữ liệu phải luôn phản ánh trạng thái commit mới nhất, hoặc kết quả phụ thuộc vào ngữ cảnh ẩn không có trong key. Với dữ liệu cá nhân hoặc nhạy cảm, key thiếu một chiều định danh còn có thể làm trả dữ liệu của người này cho người khác.

Mục tiêu không phải cache càng nhiều càng tốt. Mục tiêu là chọn nơi mà lợi ích từ tái sử dụng đủ lớn để bù cho chi phí về invalidation và consistency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-cache-abstraction-boundary">Spring Cache trừu tượng hóa điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Cache cung cấp một trừu tượng chung ở phía ứng dụng để lưu và tra cứu giá trị cache. Hai interface trung tâm là `Cache` và `CacheManager`; phía caching khai báo bổ sung chính sách ở cấp phương thức qua các annotation như `@Cacheable`, `@CachePut` và `@CacheEvict`.

Trừu tượng này giúp trả lời các câu hỏi như:

- Thao tác sẽ dùng vùng cache logic nào?
- Key nào định danh kết quả đã cache?
- Phương thức có cần chạy hay có thể trả kết quả từ cache?
- Kết quả cần được ghi mới, cập nhật hay invalidation?

Nó **không** định nghĩa một storage engine chung cho mọi nhà cung cấp. Một Spring `Cache` chỉ là adapter trên phần triển khai cache phía dưới. Nhờ vậy chính sách của ứng dụng có thể giữ ổn định khi đổi nhà cung cấp, đồng thời vẫn thừa nhận khả năng và ngữ nghĩa runtime của từng nhà cung cấp có thể khác nhau.

Đây là giá trị chính của Spring Cache ở ranh giới dịch vụ: mã nghiệp vụ mô tả ý định caching, còn cấu hình nhà cung cấp nằm ngoài phương thức. Các chương tiếp theo sẽ đi vào mô hình `Cache`/`CacheManager` và hạ tầng annotation thực thi chính sách này.

Phần còn lại của module đi theo chuỗi trách nhiệm đó:

```text
Cache / CacheManager
    ↓ cung cấp trừu tượng chung
cache annotations
    ↓ khai báo chính sách ở cấp phương thức
key + cache resolution
    ↓ xác định cache cái gì và ở đâu
invalidation + consistency
    ↓ giữ kết quả tái sử dụng đủ đáng tin cậy
proxy + concurrency mechanics
    ↓ giải thích chính sách được thực thi ở runtime ra sao
provider + transaction boundaries
    ↓ chỉ ra nơi trừu tượng của Spring kết thúc
```

Mục tiêu cuối module không chỉ là nhận biết các cache annotation. Người học cần có thể thiết kế một chính sách cache mà key, invalidation, xử lý đồng thời, lỗi, nhà cung cấp và các giả định về transaction khớp với nhau.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `org.springframework.cache`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/package-summary.html)
- [Spring Framework Reference — Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="provider-owned-concerns">Những gì vẫn thuộc trách nhiệm của cache provider?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Cache chuẩn hóa một hợp đồng caching tương đối nhỏ; nó không làm các sản phẩm cache khác nhau có cùng hành vi runtime. Nhiều chính sách quan trọng vẫn thuộc nhà cung cấp, chẳng hạn:

- time-to-live (TTL), time-to-idle (TTI) và expiry native;
- kích thước tối đa, thuật toán eviction và quản lý bộ nhớ;
- topology local hay distributed;
- định dạng serialization và khả năng tương thích giữa các tiến trình;
- replication, partitioning, durability và distributed consistency;
- locking, loading và async retrieval riêng của provider;
- metrics, công cụ vận hành và hành vi khi cluster lỗi.

Spring có thể cung cấp adapter cho nhà cung cấp, nhưng adapter không xóa những khác biệt này. Một cấu hình an toàn với cache in-memory dựa trên `ConcurrentMap` có thể có độ trễ và tính nhất quán hoàn toàn khác khi cache phía dưới nằm từ xa hoặc phân tán.

Ranh giới này sẽ được giữ xuyên suốt module: Spring Cache sở hữu trừu tượng và chính sách ở cấp phương thức; tài liệu nhà cung cấp sở hữu hành vi lưu trữ gốc. Các chi tiết Redis như data structures, TTL, serialization và topology thuộc curriculum Spring Data Redis/nhà cung cấp, không thuộc module này.

</details>

- [Quay lại đầu trang](#back-to-top)
