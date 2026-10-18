<a id="back-to-top"></a>

# Chặn lời gọi qua proxy và ranh giới thực thi

## Menu
- [Hạ tầng cache interceptor phía sau @EnableCaching](#cache-interceptor-infrastructure)
- [Proxy mode và lời gọi method từ bên ngoài](#proxy-mode)
- [Self-invocation và trường hợp bỏ qua cache advice](#self-invocation)
- [Ranh giới public method trong proxy mode](#public-method-boundary)
- [Vì sao không nên dựa vào cache trong code khởi tạo?](#initialization-boundary)
- [Thứ tự cache advice với các interceptor khác](#cache-advice-ordering)
- [AspectJ mode và ranh giới với caching dựa trên proxy](#aspectj-mode)

## <a id="cache-interceptor-infrastructure">Hạ tầng cache interceptor phía sau @EnableCaching</a>

<details>
<summary>Xem chi tiết</summary>

Các annotation cache chỉ là metadata. Bản thân chúng không tự thực hiện thao tác cache. `@EnableCaching` nhập cấu hình quản lý cache của Spring, từ đó tạo advisor/interceptor để đọc metadata của cache operation và áp dụng lên các Spring bean phù hợp.

Khi chạy, cache interceptor xác định operation, key và cache đích, rồi quyết định có cần gọi business method hay không. Với `@Cacheable`, cache hit có thể trả thẳng giá trị đã lưu; cache miss mới gọi method và ghi kết quả theo policy đã khai báo.

Vì vậy, chỉ thêm `@Cacheable`, `@CachePut` hoặc `@CacheEvict` lên một object bất kỳ chưa đủ để có caching. Object đó phải đi qua đường interception do Spring quản lý.

### Tài liệu tham khảo

- Spring Framework Reference — [Enabling Caching Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)
- Spring Framework Javadoc — [`@EnableCaching`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-mode">Proxy mode và lời gọi method từ bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

Chế độ mặc định của caching là interception dựa trên proxy. Spring đặt một proxy trước target bean; cache advice chạy khi bên gọi gọi method phù hợp thông qua proxy đó.

Có thể hình dung luồng đơn giản như sau:

```text
bên gọi
  -> Spring proxy
       -> cache advice
            -> cache hit: trả giá trị đã cache
            -> cache miss: gọi target method rồi cache kết quả
```

Ranh giới quan trọng nằm ở điểm đi vào proxy. Một method có annotation hoàn toàn đúng vẫn có thể bỏ qua caching nếu lời gọi đến thẳng target object mà không đi qua Spring proxy. Đây là ranh giới thực thi của AOP, không phải thuộc tính tự động của method Java.

### Tài liệu tham khảo

- Spring Framework Reference — [Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="self-invocation">Self-invocation và trường hợp bỏ qua cache advice</a>

<details>
<summary>Xem chi tiết</summary>

Self-invocation xảy ra khi một method trên target object gọi một method khác của chính object đó, ví dụ qua `this.loadBook(id)`. Trong proxy mode, lời gọi vẫn nằm bên trong target instance, không đi ra ngoài rồi quay lại qua proxy, nên cache interceptor không thấy lời gọi thứ hai.

```java
@Service
class BookService {

    @Cacheable("books")
    public Book find(String id) {
        return load(id);
    }

    @Cacheable("books")
    public Book load(String id) {
        return repository.load(id);
    }
}
```

Lời gọi `find()` có thể được intercept nếu đi qua Spring bean reference, nhưng lời gọi nội bộ từ `find()` sang `load()` không tạo thêm một lần cache interception. Đây là hệ quả trực tiếp của proxy semantics.

Nếu thiết kế thực sự cần interception cho lời gọi nội bộ, có thể tách cộng tác sang một Spring bean khác để lời gọi đi qua bean boundary, hoặc chủ động chọn AspectJ mode. Cơ chế proxy chuyên sâu thuộc module Spring AOP.

### Tài liệu tham khảo

- Spring Framework Reference — [Proxy mode and self-invocation](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="public-method-boundary">Ranh giới public method trong proxy mode</a>

<details>
<summary>Xem chi tiết</summary>

Với caching dựa trên proxy, Spring khuyến nghị đặt cache annotation trên public method. Annotation trên method non-public không tạo ra hành vi caching đã cấu hình trong proxy mode chuẩn, dù việc đặt annotation đó không nhất thiết gây lỗi cấu hình ngay.

Quy tắc này giúp ranh giới cache mang ý nghĩa rõ ràng ở tầng ứng dụng: caching thường gắn với thao tác dịch vụ mà bên gọi truy cập qua hợp đồng của bean do Spring quản lý. Nó cũng tránh việc thiết kế phụ thuộc vào phương thức nội bộ được chặn lời gọi một cách tình cờ.

Đây là ranh giới của proxy mode. AspectJ weaving có mô hình interception khác vì nó thay đổi bytecode của class thay vì yêu cầu lời gọi đi qua proxy.

### Tài liệu tham khảo

- Spring Framework Reference — [Method visibility and cache annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="initialization-boundary">Vì sao không nên dựa vào cache trong code khởi tạo?</a>

<details>
<summary>Xem chi tiết</summary>

Không nên xem caching dựa trên proxy là cơ chế có thể dựa vào trong giai đoạn khởi tạo bean. Spring Reference nêu rõ mã khởi tạo như `@PostConstruct`: tại thời điểm này, kỳ vọng về một đường gọi qua proxy hoàn chỉnh có thể chưa đúng với luồng gọi lúc chạy thông thường.

Một lỗi hay gặp là gọi phương thức có annotation cache từ chính phương thức khởi tạo của bean rồi mong cache được làm nóng. Trường hợp đó vừa gặp ranh giới khởi tạo, vừa là self-invocation.

Hãy khởi tạo trạng thái bắt buộc một cách rõ ràng. Nếu cần làm nóng cache, để một thành phần vòng đời khác giữ tham chiếu tới bean đã được proxy và gọi nó sau khi application context đã sẵn sàng, hoặc dùng cơ chế làm nóng được thiết kế riêng.

### Tài liệu tham khảo

- Spring Framework Reference — [Proxy initialization boundary for caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cache-advice-ordering">Thứ tự cache advice với các interceptor khác</a>

<details>
<summary>Xem chi tiết</summary>

Một phương thức có thể đồng thời chịu nhiều advice như caching, transaction, security hoặc AOP tùy biến. Thứ tự tương đối giữa các advice quyết định mỗi mối quan tâm quan sát điều gì. Ví dụ, thay đổi cache chạy bên trong hay bên ngoài ranh giới transaction có thể ảnh hưởng thời điểm lỗi được nhìn thấy và việc cập nhật cache có đi cùng commit hay không.

`@EnableCaching` có thuộc tính `order` cho cache advisor; giá trị mặc định là `Ordered.LOWEST_PRECEDENCE`. Giá trị này không tạo ra một thứ tự ngữ nghĩa cố định với mọi advisor khác. Khi nhiều advice có cùng mức ưu tiên hiệu lực, cấu hình ứng dụng và quy tắc sắp thứ tự của AOP quyết định chuỗi cuối cùng.

Chỉ nên đặt thứ tự cụ thể khi ứng dụng có yêu cầu hành vi rõ ràng và phải kiểm thử luồng thực tế. Chi tiết về mức ưu tiên advisor thuộc module Spring AOP.

### Tài liệu tham khảo

- Spring Framework Javadoc — [`@EnableCaching.order()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html#order())
- Spring Framework Reference — [Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html#aop-ataspectj-advice-ordering)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-mode">AspectJ mode và ranh giới với caching dựa trên proxy</a>

<details>
<summary>Xem chi tiết</summary>

`AdviceMode.ASPECTJ` thay đổi cơ chế interception. Thay vì phụ thuộc vào lời gọi đi vào qua Spring proxy, caching aspect của Spring được weave vào class, nên lời gọi nội bộ cũng có thể được intercept.

Nhờ vậy AspectJ mode loại bỏ giới hạn cụ thể của proxy entry dẫn đến self-invocation bypass, nhưng đổi lại cần mô hình vận hành khác: phải có `spring-aspects` và cấu hình compile-time hoặc load-time weaving. Đây là một lựa chọn kiến trúc có chủ đích.

Spring Cache chịu trách nhiệm về ngữ nghĩa thao tác cache; weaving, join point, cách triển khai proxy và cách phối hợp advice tổng quát thuộc Spring AOP/AspectJ. Khi gỡ lỗi, nên tách hai câu hỏi: metadata cache có đúng không, và lời gọi có thực sự đi qua cơ chế chặn lời gọi đã cấu hình không.

### Tài liệu tham khảo

- Spring Framework Reference — [Caching annotation mode: proxy versus AspectJ](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html#cache-annotations-enable)
- Spring Framework Javadoc — [`@EnableCaching.mode()`](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/cache/annotation/EnableCaching.html#mode())

</details>

- [Quay lại đầu trang](#back-to-top)
