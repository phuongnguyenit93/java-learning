<a id="back-to-top"></a>

# Cập nhật, vô hiệu hóa và tính nhất quán

## Menu
- [Đưa dữ liệu vào cache, put và eviction là các thao tác khác nhau](#population-put-eviction)
- [beforeInvocation và thời điểm vô hiệu hóa](#evict-before-invocation)
- [Xóa toàn bộ cache với allEntries](#whole-cache-eviction)
- [Ngoại lệ ảnh hưởng thay đổi cache như thế nào?](#exception-and-cache-mutation)
- [Dữ liệu cũ và khoảng trống nhất quán](#stale-data)
- [Cache-aside và khoảng trống giữa nguồn dữ liệu chuẩn với cache](#cache-aside-consistency)
- [@Cacheable và @CachePut trên cùng đường gọi](#cacheable-cacheput-conflict)

## <a id="population-put-eviction">Đưa dữ liệu vào cache, put và eviction là các thao tác khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

Tính đúng đắn của caching phụ thuộc vào việc phân biệt ba kiểu thay đổi trạng thái khác nhau.

- **Ghi dữ liệu sau cache miss** lưu một giá trị vì tra cứu bị miss và ứng dụng vừa tải hoặc tính được kết quả. `@Cacheable` thường tạo luồng này.
- **Put** ghi một giá trị vì phương thức đã chạy và kết quả cần làm mới trạng thái cache. `@CachePut` mô hình hóa việc này rõ ràng.
- **Eviction** xóa trạng thái cache vì ứng dụng không còn có thể tin giá trị đó. `@CacheEvict` diễn đạt chính sách invalidation.

Ba thao tác trả lời ba câu hỏi khác nhau. Bước ghi sau cache miss hỏi “sau miss thì kết quả nào nên được tái sử dụng?”. Put hỏi “giá trị mới nào nên thay entry hiện có?”. Eviction hỏi “giá trị nào không còn được phép tái sử dụng?”.

Phân biệt rõ các vai trò giúp luồng cập nhật dễ suy luận hơn. Việc ghi vào nguồn dữ liệu chuẩn không đồng nghĩa mọi cache liên quan đều nên nhận cùng một object. Có trường hợp nên cập nhật một entry; có trường hợp an toàn hơn khi evict và để lần đọc kế tiếp dựng lại projection từ dữ liệu có thẩm quyền.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="evict-before-invocation">beforeInvocation và thời điểm vô hiệu hóa</a>

<details>
<summary>Xem chi tiết</summary>

`@CacheEvict` mặc định thực hiện eviction **sau** khi phương thức được áp dụng advice hoàn tất thành công. Vì vậy nếu thao tác nghiệp vụ ném ngoại lệ, cache entry hiện có vẫn được giữ nguyên.

`beforeInvocation=true` đảo thứ tự:

```text
mặc định
method thành công → evict
method throw      → giữ cache entry hiện có

beforeInvocation=true
evict → gọi method
       └─ kể cả method throw, eviction đã xảy ra
```

Lựa chọn nào đúng phụ thuộc ngữ nghĩa nghiệp vụ. Eviction sau khi thành công hợp lý khi trạng thái cache chỉ trở nên không hợp lệ sau thay đổi thành công. Eviction trước lời gọi phù hợp khi giá trị cache không được phép tồn tại sau khi thao tác bắt đầu, nhưng chấp nhận khả năng phương thức thất bại và cache vẫn trống.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `CacheEvict.beforeInvocation`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CacheEvict.html#beforeInvocation())

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="whole-cache-eviction">Xóa toàn bộ cache với allEntries</a>

<details>
<summary>Xem chi tiết</summary>

Có những thao tác nghiệp vụ làm nhiều key mất hiệu lực cùng lúc và việc tính toàn bộ key bị ảnh hưởng không thực tế. `@CacheEvict(allEntries=true)` xóa toàn bộ entry trong cache hoặc các cache đã được phân giải thay vì nhắm tới một key riêng lẻ.

Trường hợp sử dụng thường gặp gồm thay toàn bộ tập dữ liệu tham chiếu, chạy nhập dữ liệu hàng loạt, hoặc thay cấu hình khiến mọi projection đã cache trong một vùng trở nên cũ.

Eviction toàn bộ cache là thao tác cố ý ở mức thô. Nó đánh đổi độ chính xác về phạm vi để lấy bảo đảm đơn giản hơn về tính đúng đắn và có thể tạo một đợt cache miss tạm thời trong lúc các entry được dựng lại.

Khi `allEntries=true`, không được chỉ định `key` vì operation không còn nhắm tới entry riêng lẻ. Câu hỏi thiết kế quan trọng là cache region đã đủ hẹp hay chưa để việc clear không làm mất những dữ liệu không liên quan.

### Tài liệu tham khảo

- [Spring Framework 6.1 Javadoc — `CacheEvict.allEntries`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/CacheEvict.html#allEntries())

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-and-cache-mutation">Ngoại lệ ảnh hưởng thay đổi cache như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Kết quả thực thi của phương thức ảnh hưởng thay đổi cache vì phần lớn thay đổi chỉ hợp lý sau khi có kết quả hoặc tác động nghiệp vụ thành công.

Với ngữ nghĩa annotation thông thường:

- `@Cacheable` miss mà phương thức kết thúc bằng ngoại lệ thì không có kết quả thành công để ghi vào cache;
- phương thức có `@CachePut` mà ném ngoại lệ thì không tạo kết quả bình thường để put;
- `@CacheEvict` với `beforeInvocation=false` mặc định chỉ evict sau khi invocation thành công;
- `@CacheEvict(beforeInvocation=true)` evict trước, nên cache change vẫn tồn tại dù method throw sau đó.

Đây là mối quan hệ giữa **kết quả của phương thức nghiệp vụ** và thay đổi cache. Nó khác với lỗi hạ tầng cache, ví dụ nhà cung cấp ném ngoại lệ trong `get` hoặc `put`; chính sách xử lý lỗi của cache được học ở chương xử lý lỗi sau này.

Không nên suy ra bảo đảm rollback của transaction từ các quy tắc annotation này. Phối hợp thay đổi cache với commit của transaction cần hạ tầng cache nhận biết transaction và thuộc ranh giới tích hợp ở chương sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stale-data">Dữ liệu cũ và khoảng trống nhất quán</a>

<details>
<summary>Xem chi tiết</summary>

Giá trị đã cache trở nên **stale** khi dữ liệu có thẩm quyền đã thay đổi nhưng cache vẫn giữ kết quả cũ. Đây là rủi ro tự nhiên của caching vì cache và nguồn dữ liệu chuẩn là hai nơi giữ trạng thái khác nhau.

Staleness có thể xuất hiện qua nhiều đường:

- write path cập nhật nguồn dữ liệu nhưng quên update/evict cache entry liên quan;
- hai operation concurrent thay đổi source và cache theo thứ tự khác nhau;
- TTL cho phép giá trị cũ tồn tại lâu hơn business có thể chấp nhận;
- một instance ứng dụng vô hiệu hóa cache cục bộ nhưng instance khác vẫn giữ bản sao riêng;
- một cached projection phụ thuộc dữ liệu được thay đổi bởi write path khác.

Vì vậy TTL chỉ là giới hạn về độ mới, không phải hiểu biết về phụ thuộc nghiệp vụ. Nhà cung cấp có thể làm entry hết hạn sau năm phút, nhưng không tự biết một hàng database đã đổi chỉ một giây sau khi entry được cache nếu tầng ứng dụng/tích hợp không truyền tín hiệu đó.

Thiết kế tốt cần xác định khoảng thời gian dữ liệu cũ có thể chấp nhận và những sự kiện nào phải vô hiệu hóa hoặc làm mới từng dạng dữ liệu đã cache.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-aside-consistency">Cache-aside và khoảng trống giữa nguồn dữ liệu chuẩn với cache</a>

<details>
<summary>Xem chi tiết</summary>

Luồng `@Cacheable` phổ biến gần với cache-aside: tra cache trước, load từ nguồn dữ liệu chuẩn khi miss, rồi lưu kết quả đã load vào cache.

```text
đọc dữ liệu
  ↓
cache miss
  ↓
load source of truth
  ↓
put result vào cache
```

Khoảng trống nhất quán xuất hiện vì nguồn dữ liệu và cache không phải một state machine nguyên tử. Ví dụ, một yêu cầu đọc dữ liệu cũ từ nguồn trong lúc yêu cầu khác cập nhật database và evict cache. Nếu yêu cầu đầu tiên put kết quả cũ trở lại cache **sau** eviction, dữ liệu cũ có thể được đưa vào lại.

Spring Cache cung cấp trừu tượng cho tra cứu, put và eviction nhưng không làm database và cache thay đổi nguyên tử như một hệ thống duy nhất. Ứng dụng có thể giảm khoảng trống bằng các chiến lược phù hợp domain: thứ tự invalidation cẩn thận, khoảng dữ liệu cũ ngắn, key có phiên bản, thay đổi cache nhận biết transaction khi phù hợp, hoặc cơ chế phối hợp riêng của nhà cung cấp.

Chiến lược đúng phụ thuộc mức dữ liệu cũ mà nghiệp vụ chấp nhận được. Tính nhất quán mạnh hơn thường cần nhiều phối hợp hơn và có thể làm giảm lợi ích hiệu năng của caching.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cacheable-cacheput-conflict">@Cacheable và @CachePut trên cùng đường gọi</a>

<details>
<summary>Xem chi tiết</summary>

`@Cacheable` và `@CachePut` có mục tiêu thực thi đối lập. `@Cacheable` có thể trả giá trị đã cache **mà không gọi phương thức**. `@CachePut` được thiết kế để **gọi phương thức rồi cập nhật cache bằng kết quả**.

Vì vậy dùng cả hai thao tác trên cùng phương thức cho cùng đường gọi thường là dấu hiệu thiết kế đáng nghi và Spring cũng khuyến cáo mạnh nên tránh. Hành vi trở nên khó dự đoán vì một thao tác muốn bỏ qua việc thực thi trong khi thao tác còn lại cần thực thi để hoàn thành cập nhật cache.

Có một số trường hợp biên khi `condition` của hai thao tác loại trừ lẫn nhau và làm chúng áp dụng cho những lần gọi khác nhau. Tuy nhiên việc loại trừ phải xác định được trước lời gọi; không nên dựa vào `#result` để chứng minh hai thao tác không cùng áp dụng.

Nên tách phương thức hoặc làm chính sách cache rõ hơn khi mục đích là “đọc và tái sử dụng” so với “thực hiện công việc rồi làm mới cache”. Điều này giữ mô hình thực thi dễ nhìn và tránh phụ thuộc vào tương tác giữa các interceptor khó hiểu.

### Tài liệu tham khảo

- [Spring Framework Reference — Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)

</details>

- [Quay lại đầu trang](#back-to-top)
