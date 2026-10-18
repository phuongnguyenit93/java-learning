<a id="back-to-top"></a>

# Cấu hình có kiểu với @ConfigurationProperties

## Menu
- [Vì sao nên nhóm cấu hình vào đối tượng có kiểu?](#configuration-properties-purpose)
- [Tiền tố và không gian tên cấu hình](#prefix-namespace)
- [Đăng ký @ConfigurationProperties bằng quét hoặc bật tường minh](#properties-registration)
- [Binding theo JavaBean và setter](#javabean-binding)
- [Binding qua constructor trong Spring Boot 3.x](#constructor-binding)
- [record làm kiểu @ConfigurationProperties](#record-binding)
- [Binding cấu hình vào @Bean của thư viện bên thứ ba](#third-party-bean-binding)
- [Vòng đời @ConfigurationProperties và ranh giới bean](#configuration-properties-lifecycle-boundary)

## <a id="configuration-properties-purpose">Vì sao nên nhóm cấu hình vào đối tượng có kiểu?</a>

<details>
<summary>Xem chi tiết</summary>

Một không gian tên cấu hình thường đại diện cho một khái niệm của ứng dụng như gửi mail, giới hạn lưu trữ, client từ xa hoặc các thiết lập tính năng. Đọc từng khóa riêng lẻ làm hợp đồng đó bị phân tán qua nhiều điểm tiêm. @ConfigurationProperties cho phép Boot bind cả không gian tên một lần vào đối tượng có kiểu để các phần còn lại của ứng dụng sử dụng.

~~~properties
mail.host=smtp.example.com
mail.port=587
mail.retry-count=3
~~~

MailProperties có thể giữ host, port và retryCount cùng nhau. Lợi ích không chỉ là ít annotation hơn: kiểu này trở thành ranh giới cấu hình rõ ràng, có thể xác thực như một đơn vị, sinh metadata và tái cấu trúc an toàn hơn. Environment vẫn chịu trách nhiệm phân giải giá trị; @ConfigurationProperties bắt đầu sau khi giá trị có hiệu lực đã sẵn sàng và biến chúng thành mô hình phía ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="prefix-namespace">Tiền tố và không gian tên cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

Tiền tố của @ConfigurationProperties xác định không gian tên mà Boot sẽ bind. Với @ConfigurationProperties("mail"), các khóa như mail.host và mail.retry-count thuộc đối tượng đó.

Tiền tố phải dùng kebab-case chuẩn. Tiền tố tốt nên ổn định và bám theo miền cấu hình thay vì tên class hoặc môi trường triển khai. Tiền tố trả lời “phần cấu hình nào thuộc hợp đồng này?”, còn các trường trả lời “hợp đồng đó gồm những giá trị nào?”.

Tránh tiền tố quá rộng như app nếu nhiều tính năng không liên quan sẽ cùng dồn thiết lập vào đó. Không gian tên gọn và nhất quán giúp xác thực, metadata, ranh giới trách nhiệm và thông tin deprecation dễ quản lý hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="properties-registration">Đăng ký @ConfigurationProperties bằng quét hoặc bật tường minh</a>

<details>
<summary>Xem chi tiết</summary>

Đánh dấu một kiểu bằng @ConfigurationProperties mô tả cách nó được bind, nhưng Boot vẫn cần đăng ký kiểu đó thành bean. Hai cơ chế phổ biến là @ConfigurationPropertiesScan và @EnableConfigurationProperties tường minh.

~~~java
@SpringBootApplication
@ConfigurationPropertiesScan
class Application { }
~~~

Việc quét tiện cho các lớp cấu hình do ứng dụng sở hữu và đặt dưới ranh giới package có chủ ý. Việc bật tường minh phù hợp khi lớp cấu hình muốn chỉ rõ kiểu nào tham gia hoặc khi auto-configuration của thư viện đăng ký kiểu property của chính nó. Cả hai cơ chế không thay đổi precedence; việc đăng ký chỉ quyết định đối tượng đích có kiểu nào được Boot tạo và bind.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="javabean-binding">Binding theo JavaBean và setter</a>

<details>
<summary>Xem chi tiết</summary>

JavaBean binding dùng đường khởi tạo không tham số rồi ghi vào các property có setter. Boot khớp khóa cấu hình với tên bean property và gọi setter sau khi chuyển giá trị sang kiểu đích đã khai báo.

~~~java
@ConfigurationProperties("mail")
public class MailProperties {
    private String host;
    private int port = 25;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
~~~

Kiểu này phù hợp khi tính khả biến là chấp nhận được hoặc framework/công cụ cần quy ước JavaBean. Có thể đặt giá trị mặc định trong giá trị khởi tạo trường, nhưng cần phân biệt giá trị mặc định của đối tượng với property precedence thấp trong Environment: đây là giá trị sẵn có của đối tượng đích khi không có giá trị nào được bind.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructor-binding">Binding qua constructor trong Spring Boot 3.x</a>

<details>
<summary>Xem chi tiết</summary>

Constructor binding tạo đối tượng cấu hình từ tham số constructor thay vì thay đổi trạng thái qua setter. Trong Spring Boot 3.x, kiểu @ConfigurationProperties có một constructor có tham số sẽ dùng constructor đó để bind mà không cần @ConstructorBinding. Nếu có nhiều constructor, @ConstructorBinding có thể chỉ rõ constructor cần dùng; constructor đánh dấu @Autowired sẽ không dùng constructor binding.

~~~java
@ConfigurationProperties("mail")
public class MailProperties {
    private final String host;
    private final int port;

    public MailProperties(String host, int port) {
        this.host = host;
        this.port = port;
    }
}
~~~

Constructor binding phù hợp với hợp đồng cấu hình bất biến. Nó cũng buộc quyết định về giá trị thiếu/null rõ ngay khi tạo đối tượng. Kiểu `@ConfigurationProperties` dùng constructor binding phải được đăng ký qua `@ConfigurationPropertiesScan` hoặc `@EnableConfigurationProperties`; đây không phải bean được tạo theo cơ chế Spring thông thường và không thể dựa vào các đường tạo như `@Component`, phương thức `@Bean` hay `@Import`. Trường hợp riêng đặt `@ConfigurationProperties` trên phương thức `@Bean` sẽ bind vào đối tượng đã được phương thức tạo ra nên không dùng constructor binding.

Constructor binding cũng cần tên tham số Java còn khả dụng lúc chạy, nên mã phải được biên dịch với `-parameters`. Spring Boot Gradle plugin tự cấu hình tùy chọn này, còn dự án Maven nhận thiết lập đó khi dùng `spring-boot-starter-parent`; Maven build tùy chỉnh hoặc build chỉ áp dụng `spring-boot-maven-plugin` phải tự cấu hình compiler phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="record-binding">record làm kiểu @ConfigurationProperties</a>

<details>
<summary>Xem chi tiết</summary>

Java record là lựa chọn gọn cho cấu hình bất biến vì constructor chuẩn và tên thành phần đã mô tả đối tượng đích của binding.

~~~java
@ConfigurationProperties("client")
public record ClientProperties(
        URI baseUrl,
        Duration timeout) {
}
~~~

Boot bind client.base-url và client.timeout vào thành phần record bằng cùng quy tắc relaxed-name và chuyển đổi kiểu như các configuration properties khác. Record không tự biến cấu hình thành bắt buộc: khả năng nhận null, giá trị mặc định của kiểu nguyên thủy, giá trị mặc định tường minh và xác thực vẫn quyết định điều gì xảy ra khi giá trị thiếu hoặc sai.

Dùng record khi cấu hình thực chất là dữ liệu bất biến. Dùng class khi cần logic khởi tạo, kế thừa, tính khả biến hoặc một cấu trúc mà record làm khó hiểu hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="third-party-bean-binding">Binding cấu hình vào @Bean của thư viện bên thứ ba</a>

<details>
<summary>Xem chi tiết</summary>

Đôi khi đối tượng đích của binding hữu ích là class từ thư viện mà ứng dụng không sở hữu. Có thể đặt @ConfigurationProperties trên phương thức @Bean để Boot bind các giá trị bên ngoài vào đối tượng được trả về.

~~~java
@Bean
@ConfigurationProperties("client.pool")
ConnectionPoolSettings connectionPoolSettings() {
    return new ConnectionPoolSettings();
}
~~~

Cách này tránh phải sao chép mọi thiết lập của thư viện sang lớp bọc chỉ để binding. Nó phù hợp nhất khi đối tượng đích cung cấp các property JavaBean có thể ghi. Vì ứng dụng không kiểm soát kiểu đích, metadata và khả năng xác thực thường hạn chế hơn so với lớp cấu hình do ứng dụng sở hữu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-properties-lifecycle-boundary">Vòng đời @ConfigurationProperties và ranh giới bean</a>

<details>
<summary>Xem chi tiết</summary>

Bean configuration-properties là đối tượng cấu hình hạ tầng. Nên giữ nó tập trung vào dữ liệu và xác thực nhẹ, thay vì biến nó thành dịch vụ nghiệp vụ hoặc nơi thực hiện công việc lúc chạy tốn kém. Boot có thể cần binding và chuyển đổi kiểu từ rất sớm, trước khi phần còn lại của ứng dụng khởi tạo hoàn chỉnh.

Đặc biệt, kiểu property dùng constructor binding phải được phát hiện hoặc bật qua cơ chế configuration-properties, không được khởi tạo như component Spring thông thường. Nó cũng không nên phụ thuộc vào bean ứng dụng thông thường như một đối tượng cộng tác. Nếu giá trị cấu hình quyết định cách tạo client hoặc service, hãy bind dữ liệu trước rồi để @Configuration/@Bean riêng dùng đối tượng cấu hình đó tạo component lúc chạy.

~~~text
giá trị trong Environment
        ↓
binding @ConfigurationProperties
        ↓
đối tượng cấu hình đã được xác thực
        ↓
@Bean / component sử dụng đối tượng đó
~~~

Tách như vậy giữ quá trình phân giải cấu hình xác định và tránh biến đối tượng cấu hình thành service locator ẩn.

</details>

- [Quay lại đầu trang](#back-to-top)
