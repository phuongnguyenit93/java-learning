<a id="back-to-top"></a>

# Các điểm mở rộng của Spring Container

## Menu
- [Khi nào cần mở rộng container?](#extension-point-purpose)
- [BeanFactoryPostProcessor](#bean-factory-post-processor)
- [BeanPostProcessor](#bean-post-processor)
- [Thứ tự của post-processors](#post-processor-ordering)
- [Bean được tạo quá sớm và các hệ quả](#early-bean-instantiation)
- [FactoryBean và product bean](#factory-bean)
- [Annotation processing như hạ tầng của container](#annotation-infrastructure)
- [Giữ mã ứng dụng ít phụ thuộc container](#container-agnostic-design)

## <a id="extension-point-purpose">Khi nào cần mở rộng container?</a>

<details>
<summary>Xem chi tiết</summary>

Phần lớn mã ứng dụng không cần mở rộng container. Các extension point chủ yếu dành cho framework hoặc hạ tầng cần tham gia trực tiếp vào quá trình khởi động container.

Có thể bắt đầu bằng hai câu hỏi:

```text
cần thay đổi metadata cấu hình?
→ làm việc trước khi bean ứng dụng được tạo
→ họ BeanFactoryPostProcessor

cần can thiệp vào bean instance?
→ làm việc quanh giai đoạn initialization
→ họ BeanPostProcessor
```

Cách phân chia này giúp tránh dùng container hook để cài logic nghiệp vụ. Service nghiệp vụ nên là bean bình thường với dependency rõ ràng. Chỉ dùng extension point khi vấn đề thật sự liên quan đến đăng ký, ghép nối, lifecycle, annotation processing hoặc hạ tầng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-factory-post-processor">BeanFactoryPostProcessor</a>

<details>
<summary>Xem chi tiết</summary>

`BeanFactoryPostProcessor` chạy sau khi bean definitions đã được nạp nhưng trước khi các bean thông thường được khởi tạo. Vì vậy đối tượng nó tác động là **mô hình cấu hình của container**, chưa phải các object cuối cùng của ứng dụng.

Trường hợp sử dụng điển hình là điều chỉnh definition metadata, đăng ký hạ tầng hoặc hoàn tất cấu hình trước giai đoạn tạo bean. `BeanDefinitionRegistryPostProcessor` là dạng chuyên biệt cho phép bổ sung bean definitions trước pha factory post-processing thông thường.

Ranh giới quan trọng: không dùng hook này như callback của service. Gọi `getBean(...)` khi đang post-process metadata có thể tạo bean ứng dụng quá sớm và làm sai giả định của những post-processor chạy sau.

```text
nạp bean definitions
→ factory post-processors đọc/thay đổi metadata
→ bắt đầu tạo bean thông thường
```

### Tài liệu tham khảo

- Spring Framework Reference — Container Extension Points

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-post-processor">BeanPostProcessor</a>

<details>
<summary>Xem chi tiết</summary>

`BeanPostProcessor` tham gia vào lifecycle của **bean instance**. Container có thể gọi processor trước và sau initialization callbacks, nhờ đó hạ tầng có thể kiểm tra bean hoặc trả về một instance đã được bọc/thay thế.

Nhiều tính năng tưởng như chỉ "do annotation" thực chất dựa trên hook này: injection processors, lifecycle annotation processors và hạ tầng tạo proxy đều cần một điểm can thiệp có kiểm soát trong quá trình tạo bean.

```text
khởi tạo instance
→ nạp dependency
→ before-initialization processors
→ init callbacks
→ after-initialization processors
→ bean reference được đưa ra sử dụng
```

Một processor có thể tác động rộng tới các bean phù hợp trong cùng factory. Vì vậy custom processor nên nhỏ, xác định rõ mục đích và chỉ xử lý mối quan tâm hạ tầng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="post-processor-ordering">Thứ tự của post-processors</a>

<details>
<summary>Xem chi tiết</summary>

Khi nhiều post-processor cùng tham gia bootstrap, thứ tự có thể thay đổi hành vi quan sát được. Với processor được container tự phát hiện như bean, Spring ưu tiên `PriorityOrdered`, sau đó `Ordered`, rồi đến nhóm không khai báo thứ tự.

Không nên suy ra quy tắc đó cho mọi trường hợp. Khi post-processor được đăng ký trực tiếp bằng API của configurable factory/context, thứ tự đăng ký có ý nghĩa và ordering metadata không nhất thiết được xử lý theo cùng cách.

Điều này quan trọng nếu một processor kỳ vọng processor khác đã đăng ký metadata, inject dependency hoặc bọc bean trước đó. Nếu thật sự cần quan hệ thứ tự, hãy biểu diễn nó rõ ràng và giữ coupling giữa các processor ở mức nhỏ nhất.

Một chuỗi dài custom post-processors mà tính đúng đắn của ứng dụng phụ thuộc chặt vào thứ tự thường là dấu hiệu thiết kế quá ẩn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="early-bean-instantiation">Bean được tạo quá sớm và các hệ quả</a>

<details>
<summary>Xem chi tiết</summary>

Hạ tầng container được tạo sớm hơn bean ứng dụng thông thường. Nếu một post-processor lấy bean ứng dụng trong lúc processor chain vẫn đang được xây dựng, bean đó có thể được tạo trước khi mọi processor cần thiết đã sẵn sàng.

Hệ quả không chỉ là startup sớm. Bean có thể được tạo thành công nhưng bỏ lỡ hạ tầng chạy sau, ví dụ auto-proxying hoặc một số annotation processing. Spring có thể phát cảnh báo rằng bean "not eligible for getting processed by all BeanPostProcessors" trong tình huống kiểu này.

Quy tắc thực hành:

```text
dependency của post-processor
→ ưu tiên dependency hạ tầng hoặc truy cập trì hoãn
→ tránh kéo bean nghiệp vụ vào bootstrap quá sớm
```

Việc tạo bean quá sớm có thể làm thay đổi ngữ nghĩa của bean reference cuối cùng, không chỉ ảnh hưởng hiệu năng khởi động.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="factory-bean">FactoryBean và product bean</a>

<details>
<summary>Xem chi tiết</summary>

`FactoryBean<T>` cho phép một factory object do Spring quản lý cung cấp một object khác dưới cùng bean name. Lookup tên bean thông thường nhận **product**; dùng prefix `&` để yêu cầu chính `FactoryBean`.

Cơ chế này phù hợp khi việc tạo object đủ phức tạp để cần một giao diện factory riêng hoặc khi hạ tầng cần cung cấp object không khớp tự nhiên với một bean definition thông thường.

Hai điểm lifecycle cần nhớ:

- Spring quản lý lifecycle của chính instance `FactoryBean`, không quản lý lifecycle của product do `getObject()` trả về. Vì vậy destroy method trên product không tự động được gọi; nếu product cần giải phóng tài nguyên thì factory phải sở hữu hoặc chủ động chuyển tiếp việc giải phóng đó.
- Cả `getObjectType()` và `getObject()` đều có thể bị gọi sớm trong bootstrap, kể cả trước khi chuỗi post-processor thông thường được thiết lập đầy đủ. `FactoryBean` không nên giả định annotation-driven injection hoặc toàn bộ application graph đã sẵn sàng tại thời điểm đó.

Nếu factory thực sự cần truy cập bean khác trong giai đoạn sớm này, hãy dùng giao diện tường minh như `BeanFactoryAware` thay vì trông chờ vào annotation-driven injection thông thường.

Hãy dùng `FactoryBean` cho ngữ nghĩa tạo object ở mức hạ tầng, không chỉ để che một lời gọi constructor bình thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="annotation-infrastructure">Annotation processing như hạ tầng của container</a>

<details>
<summary>Xem chi tiết</summary>

Annotation của Spring hoạt động vì hạ tầng container đọc và xử lý metadata đó; bản thân annotation không tự thực thi hành vi.

Một số luồng tiêu biểu:

```text
@Configuration / @Bean / @Import
→ configuration-class processing
→ đăng ký bean definitions

@Autowired / @Value
→ injection-oriented BeanPostProcessor
→ áp dependency hoặc value vào instance

@PostConstruct / @PreDestroy
→ lifecycle annotation processor
→ gọi callback ở đúng ranh giới lifecycle
```

Mô hình tư duy này hữu ích khi debug. Nếu object được tạo bằng `new` ngoài container, hoặc processor tương ứng không tồn tại, chỉ gắn annotation lên class không khiến hành vi Spring tự xuất hiện.

Annotation là phần khai báo; extension point của container là cơ chế thực thi khai báo đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="container-agnostic-design">Giữ mã ứng dụng ít phụ thuộc container</a>

<details>
<summary>Xem chi tiết</summary>

Một cách dùng container tốt thường tạo ra mã ứng dụng gần như không cần biết container tồn tại. Constructor injection cho phép service mô tả dependency bằng Java type, còn Spring chịu trách nhiệm ghép nối ở ranh giới ứng dụng.

Nên ưu tiên:

```java
final class CheckoutService {
    private final PricePolicy pricePolicy;

    CheckoutService(PricePolicy pricePolicy) {
        this.pricePolicy = pricePolicy;
    }
}
```

thay vì static context holder hoặc liên tục gọi `applicationContext.getBean(...)` trong logic nghiệp vụ.

Các giao diện `*Aware` và truy cập `BeanFactory` trực tiếp vẫn hợp lệ nếu bean thực sự là hạ tầng. Chúng không nên là mặc định cho service nghiệp vụ/ứng dụng vì sẽ biến dependency graph tường minh thành Service Locator ẩn.

Một câu hỏi thiết kế hữu ích: **class này có thể unit test bằng cách tự tạo nó với các đối tượng cộng tác bình thường không?** Nếu có, container đang được giữ ở đúng ranh giới.

</details>

- [Quay lại đầu trang](#back-to-top)
