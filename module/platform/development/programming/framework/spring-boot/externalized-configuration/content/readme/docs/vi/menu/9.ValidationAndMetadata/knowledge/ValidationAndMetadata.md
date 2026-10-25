<a id="back-to-top"></a>

# Xác thực cấu hình và metadata

## Menu
- [Vì sao nên xác thực cấu hình ngay khi khởi động?](#configuration-validation-purpose)
- [Xác thực @ConfigurationProperties bằng @Validated](#validated-configuration-properties)
- [Xác thực cấu hình lồng nhau bằng @Valid](#nested-validation)
- [Lỗi binding, chuyển đổi kiểu và xác thực khác nhau thế nào?](#binding-vs-validation-failure)
- [Metadata cấu hình tồn tại để làm gì?](#configuration-metadata-purpose)
- [Tạo metadata bằng configuration processor](#configuration-processor)
- [Metadata, hỗ trợ từ IDE và ranh giới công cụ](#metadata-tooling-boundary)

## <a id="configuration-validation-purpose">Vì sao nên xác thực cấu hình ngay khi khởi động?</a>

<details>
<summary>Xem chi tiết</summary>

Chuyển đổi kiểu có thể chứng minh "8080" là số nguyên, nhưng không thể chứng minh 8080 hợp lệ với một hợp đồng cấu hình cụ thể. Xác thực bổ sung các ràng buộc miền sau binding để cấu hình sai làm ứng dụng thất bại gần thời điểm khởi động thay vì trở thành lỗi lúc chạy khó hiểu về sau.

Các ràng buộc thường gặp gồm máy chủ bắt buộc, kích thước pool dương, định danh không rỗng và timeout trong khoảng cho phép. Mục tiêu là dừng sớm khi lỗi với thông báo gắn với property cấu hình, không phải đưa toàn bộ xác thực nghiệp vụ vào lớp cấu hình.

~~~text
property đã phân giải
        ↓
binding + conversion
        ↓
đối tượng cấu hình có kiểu
        ↓
constraint validation
        ↓
đối tượng hợp lệ HOẶC khởi động thất bại
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validated-configuration-properties">Xác thực @ConfigurationProperties bằng @Validated</a>

<details>
<summary>Xem chi tiết</summary>

Đánh dấu kiểu @ConfigurationProperties bằng @Validated của Spring và đặt ràng buộc Jakarta Validation lên các property tương ứng. Classpath phải có phần triển khai validation phù hợp.

~~~java
@ConfigurationProperties("client")
@Validated
public record ClientProperties(
        @NotBlank String baseUrl,
        @Positive int maxConnections) {
}
~~~

Nếu client.max-connections phân giải thành -1, binding vẫn tạo được int nhưng validation từ chối đối tượng và quá trình khởi động báo lỗi cấu hình. Nhờ vậy ta phân biệt rõ lỗi cú pháp/kiểu với giá trị đúng kiểu nhưng sai hợp đồng miền.

Annotation validation mô tả hợp đồng của kiểu cấu hình. Cơ chế Bean Validation tổng quát và cách xây ràng buộc tùy chỉnh thuộc chương trình học validation chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-validation">Xác thực cấu hình lồng nhau bằng @Valid</a>

<details>
<summary>Xem chi tiết</summary>

Ràng buộc trong đối tượng cấu hình lồng nhau không tự động thay thế cascading validation. Khi đối tượng lồng nhau cũng phải được kiểm tra, đánh dấu liên kết bằng @Valid để bộ xác thực đi sâu vào đối tượng đó.

~~~java
@ConfigurationProperties("mail")
@Validated
public class MailProperties {
    @Valid
    private final Security security = new Security();

    public Security getSecurity() { return security; }

    public static class Security {
        @NotBlank
        private String protocol;
        // getter/setter
    }
}
~~~

Nếu thiếu dấu hiệu cascade, đối tượng bên ngoài có thể được xác thực nhưng ràng buộc trong đối tượng lồng nhau không được đi qua theo hợp đồng mong muốn. Hãy coi xác thực lồng nhau là một phần của hình dạng cấu hình công khai và kiểm thử đường lỗi cho thiết lập lồng nhau bắt buộc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-vs-validation-failure">Lỗi binding, chuyển đổi kiểu và xác thực khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Nhiều lỗi khởi động đều có thể trông như “cấu hình sai” nhưng thực tế xảy ra ở các giai đoạn khác nhau.

| Giai đoạn | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Nạp dữ liệu | tệp import bắt buộc bị thiếu | Boot không lấy được Config Data |
| Binding/chuyển đổi kiểu | timeout=banana cho Duration | giá trị không thể thành kiểu đích |
| Validation | max-connections=-1 với @Positive | kiểu hợp lệ nhưng ràng buộc miền không hợp lệ |

Mô hình theo giai đoạn giúp chẩn đoán nhanh hơn. Đừng sửa lỗi chuyển đổi kiểu bằng cách nới quy tắc validation, và đừng điều tra precedence khi vấn đề thực tế là import bắt buộc bị thiếu. Hãy đọc chuỗi exception cùng báo cáo binding/validation theo đúng giai đoạn của luồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-metadata-purpose">Metadata cấu hình tồn tại để làm gì?</a>

<details>
<summary>Xem chi tiết</summary>

Metadata cấu hình mô tả các khóa được hỗ trợ để công cụ giúp người dùng khám phá và chỉnh cấu hình đúng. Spring Boot jar dùng META-INF/spring-configuration-metadata.json để công bố thông tin như tên property, kiểu đích, mô tả, giá trị mặc định, deprecation và gợi ý giá trị.

Metadata không tạo property, không thay đổi precedence và không thực hiện binding. Nó là dữ liệu mô tả phục vụ công cụ cho hợp đồng cấu hình. Hành vi lúc chạy vẫn do Environment và binding quyết định, còn IDE có thể cung cấp gợi ý hoàn thành/tài liệu mà không cần chạy ứng dụng.

Với thư viện cấu hình do ứng dụng sở hữu, metadata tốt biến mô hình @ConfigurationProperties có kiểu thành trải nghiệm cấu hình dễ dùng hơn nhiều cho bên tiêu thụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-processor">Tạo metadata bằng configuration processor</a>

<details>
<summary>Xem chi tiết</summary>

Annotation processor spring-boot-configuration-processor có thể sinh metadata lúc biên dịch từ các kiểu @ConfigurationProperties. Với Gradle, phụ thuộc này thường nằm trong configuration annotationProcessor thay vì classpath lúc chạy.

~~~groovy
dependencies {
    annotationProcessor "org.springframework.boot:spring-boot-configuration-processor"
}
~~~

Processor đọc các property có thể bind cùng tài liệu có trong mã nguồn rồi ghi metadata dưới META-INF. Vì quá trình sinh xảy ra lúc biên dịch, tệp metadata không phải bằng chứng rằng property đã được cung cấp lúc chạy hoặc validation đã thành công.

Có thể bổ sung metadata thủ công cho trường hợp processor không suy ra được, nhưng metadata phải bám hợp đồng cấu hình thật thay vì tạo khóa mà mã ứng dụng không tiêu thụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="metadata-tooling-boundary">Metadata, hỗ trợ từ IDE và ranh giới công cụ</a>

<details>
<summary>Xem chi tiết</summary>

Gợi ý hoàn thành của IDE và metadata là hỗ trợ lúc phát triển, không phải nguồn thẩm quyền cho cấu hình lúc chạy. Một khóa vẫn có thể bind lúc chạy dù metadata tùy chỉnh bị thiếu; ngược lại một mục metadata không bảo đảm một đường đi lúc chạy cụ thể thực sự dùng khóa đó.

~~~text
nguồn @ConfigurationProperties
        ↓ compile time
configuration processor
        ↓
spring-configuration-metadata.json
        ↓
IDE completion / documentation
~~~

Khi chẩn đoán, hãy tách ba mối quan tâm: phân giải giá trị lúc chạy, binding có kiểu/validation và metadata công cụ khi biên dịch. Nếu gợi ý hoàn thành sai, kiểm tra quá trình sinh metadata. Nếu ứng dụng nhận sai giá trị, kiểm tra `PropertySource` và binding.

</details>

- [Quay lại đầu trang](#back-to-top)
