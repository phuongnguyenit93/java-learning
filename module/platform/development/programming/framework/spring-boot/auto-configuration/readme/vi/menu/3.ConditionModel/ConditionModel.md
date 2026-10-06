<a id="back-to-top"></a>

# Mô hình lựa chọn bằng Condition

## Menu
- [Vì sao Auto-configuration cần Condition?](#condition-model-purpose)
- [Condition theo Classpath](#class-conditions)
- [Bean Condition và thời điểm đánh giá](#bean-conditions-and-timing)
- [Property Condition và đầu vào cấu hình](#property-conditions)
- [Condition theo Resource và loại ứng dụng Web](#resource-and-web-conditions)
- [Class tùy chọn, Annotation Metadata và ranh giới liên kết JVM](#optional-class-linkage)

## <a id="condition-model-purpose">Vì sao Auto-configuration cần Condition?</a>

<details>
<summary>Xem chi tiết</summary>

Cấu hình dùng lại không thể giả định mọi ứng dụng sử dụng thư viện có cùng thư viện, thuộc tính cấu hình, bean, tài nguyên hoặc loại môi trường chạy. Condition giúp auto-configuration phản ứng theo context thực tế.

Nếu không có condition, chỉ cần thêm một JAR tích hợp cũng có thể ép bean không cần thiết vào mọi ứng dụng. Với condition, phần tích hợp khai báo rõ điều kiện áp dụng:

~~~text
thư viện bắt buộc có mặt?
tính năng đang bật?
bean cộng tác tồn tại?
đúng loại ứng dụng?
resource cần thiết tồn tại?
        ↓
chỉ khi đó mới đóng góp cấu hình
~~~

Vì vậy condition không chỉ là tối ưu hiệu năng; nó định nghĩa khi nào cấu hình mặc định là hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-conditions">Condition theo Classpath</a>

<details>
<summary>Xem chi tiết</summary>

Các class condition như ConditionalOnClass và ConditionalOnMissingClass đưa trạng thái classpath vào quyết định. Chúng phù hợp với phần tích hợp chỉ có ý nghĩa khi một công nghệ hoặc thư viện nhất định tồn tại.

~~~java
@AutoConfiguration
@ConditionalOnClass(AcmeClient.class)
class AcmeClientAutoConfiguration {
}
~~~

Nếu AcmeClient không có mặt, candidate không nên cố cấu hình client. Starter thường đưa dependency hữu ích vào classpath; class condition sau đó cho phép cấu hình tương ứng tham gia.

Ở mức configuration class, Boot có thể đọc annotation metadata mà không phải nạp sớm mọi type tùy chọn. Phần sau sẽ phân biệt việc đọc metadata an toàn này với các tham chiếu vẫn buộc JVM liên kết class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-conditions-and-timing">Bean Condition và thời điểm đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

Các bean condition như ConditionalOnBean và ConditionalOnMissingBean lý giải dựa trên bean definition mà ApplicationContext đã biết. Đây là nền tảng của nhiều quy tắc back-off.

Thời điểm đánh giá quan trọng vì condition chỉ thấy những definition đã được xử lý đến lúc đó. Boot áp dụng auto-configuration sau các bean definition do ứng dụng định nghĩa, vì vậy missing-bean condition đặc biệt hữu ích trong auto-configuration.

~~~java
@Bean
@ConditionalOnMissingBean
AcmeClient acmeClient(AcmeProperties properties) {
    return new AcmeClient(properties.getEndpoint());
}
~~~

Type mục tiêu cũng quan trọng. Condition đặt trên Bean method có thể suy ra type mục tiêu từ return type khai báo. Nếu method chỉ công bố một interface quá rộng trong khi condition khác tìm type cụ thể, kết quả có thể khác với mô hình tư duy của tác giả.

Bằng chứng nên kiểm thử cả hai phía: không có bean do ứng dụng cung cấp thì giá trị mặc định xuất hiện; có bean do ứng dụng cung cấp thì giá trị mặc định back off.

Một lưu ý riêng áp dụng cho @ConditionalOnExpression. Khi biểu thức SpEL tham chiếu trực tiếp đến một bean, bean đó sẽ được khởi tạo rất sớm trong quá trình refresh context và không còn đủ điều kiện để nhận các bước hậu xử lý thông thường như binding @ConfigurationProperties. Vì vậy trạng thái bean có thể chưa hoàn chỉnh. Không nên dùng tham chiếu bean trong biểu thức như một cách vòng qua các condition theo classpath, property hoặc bean vốn có quy ước rõ ràng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="property-conditions">Property Condition và đầu vào cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

ConditionalOnProperty cho phép giá trị trong Environment ảnh hưởng tới việc chọn auto-configuration. Đây là điểm nối với module Externalized Configuration, nhưng module hiện tại không sở hữu ngữ nghĩa về thứ tự ưu tiên property source hoặc binding.

~~~java
@ConditionalOnProperty(
    prefix = "acme.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
~~~

Ví dụ này biểu diễn tính năng mặc định bật trừ khi ứng dụng chủ động tắt. Thiết kế khác có thể yêu cầu property phải tồn tại hoặc phải bằng một giá trị cụ thể.

Ranh giới cần giữ rõ: module này giải thích property ảnh hưởng quyết định auto-configuration thế nào. Cách Boot nạp, sắp thứ tự, bind và validate cấu hình thuộc Externalized Configuration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-and-web-conditions">Condition theo Resource và loại ứng dụng Web</a>

<details>
<summary>Xem chi tiết</summary>

Condition có thể quan sát nhiều trạng thái hơn class, bean và property. ConditionalOnResource có thể yêu cầu một tài nguyên; ConditionalOnWebApplication và ConditionalOnNotWebApplication phân biệt ứng dụng web với ứng dụng không phải web.

Nhờ đó có thể tách phần tích hợp rõ ràng hơn:

~~~text
cấu hình Acme client cốt lõi
        +
tích hợp riêng cho servlet
        +
tích hợp riêng cho reactive
~~~

Cấu hình cốt lõi không cần giả định mọi ứng dụng sử dụng thư viện đều chạy web stack. Phần dành riêng cho web chỉ được bật trong context phù hợp.

Module này sở hữu cách lựa chọn dựa trên loại ứng dụng; cơ chế của MVC/WebFlux vẫn thuộc Spring Framework và Spring Boot Web Runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="optional-class-linkage">Class tùy chọn, Annotation Metadata và ranh giới liên kết JVM</a>

<details>
<summary>Xem chi tiết</summary>

Class-level condition thường được đọc từ annotation metadata trước khi class tùy chọn bị nạp. Sự bảo vệ đó không tự động bao phủ mọi tham chiếu trong configuration class.

Nếu Bean method trả về type thuộc dependency tùy chọn, JVM có thể phải phân giải chữ ký method khi nạp class chứa method, trước khi method-level condition kịp bảo vệ nó.

Hãy cô lập type tùy chọn phía sau configuration class có class-level condition:

~~~java
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OptionalLibrary.class)
static class OptionalLibraryConfiguration {

    @Bean
    OptionalLibraryAdapter adapter() {
        return new OptionalLibraryAdapter();
    }
}
~~~

Khi tạo meta-annotation tùy chỉnh bao quanh ConditionalOnClass, đôi lúc cần dùng tên class thay vì class literal vì annotation tổ hợp không nhận mọi xử lý đặc biệt giống annotation gốc.

Kiểm thử bằng FilteredClassLoader cung cấp bằng chứng tốt: loại thư viện tùy chọn khỏi classpath mô phỏng và xác nhận cấu hình liên quan biến mất sạch thay vì phát sinh lỗi liên kết.

Condition quyết định configuration có phù hợp hay không; bản thân chúng chưa nói đầy đủ cách lựa chọn của ứng dụng ghi đè các giá trị mặc định của Boot. Chương tiếp theo biến kết quả condition thành quy ước back-off và quyền kiểm soát rõ ràng cho ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
