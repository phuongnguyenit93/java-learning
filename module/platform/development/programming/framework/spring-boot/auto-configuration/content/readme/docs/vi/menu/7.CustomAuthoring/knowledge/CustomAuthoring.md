<a id="back-to-top"></a>

# Tự xây dựng Custom Auto-configuration

## Menu
- [@AutoConfiguration và quy ước dành cho tác giả](#auto-configuration-class-contract)
- [Đăng ký, ranh giới Package và Import tường minh](#registration-and-package-boundaries)
- [Tích hợp @ConfigurationProperties](#configuration-properties-integration)
- [Thiết kế Dependency tùy chọn](#optional-dependency-design)
- [Auto-configuration Processor và Condition Metadata](#auto-configure-metadata)
- [Danh tính class ổn định cho Ordering và Exclusion](#stable-auto-configuration-identities)

## <a id="auto-configuration-class-contract">@AutoConfiguration và quy ước dành cho tác giả</a>

<details>
<summary>Xem chi tiết</summary>

AutoConfiguration đánh dấu một configuration class là candidate của luồng auto-configuration trong Spring Boot. Nó vẫn là Spring configuration, nhưng Boot xử lý nó theo quy ước auto-configuration và luôn dùng proxyBeanMethods=false.

Một auto-configuration tốt nên tập trung: khai báo điều kiện để một phần tích hợp hợp lệ và chỉ đóng góp bean definition thuộc trách nhiệm của phần tích hợp đó.

~~~java
@AutoConfiguration
@ConditionalOnClass(AcmeClient.class)
@EnableConfigurationProperties(AcmeProperties.class)
class AcmeClientAutoConfiguration {
}
~~~

Chỉ có annotation vẫn chưa đủ để công bố class. Candidate còn phải được liệt kê trong AutoConfiguration.imports. Sau đó condition và back-off mới quyết định class có thực sự đóng góp gì trong từng ứng dụng hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="registration-and-package-boundaries">Đăng ký, ranh giới Package và Import tường minh</a>

<details>
<summary>Xem chi tiết</summary>

Auto-configuration của thư viện nên nằm trong package do thư viện sở hữu, được đăng ký trong AutoConfiguration.imports và không dựa vào component scanning. Cấu hình hỗ trợ nên được import tường minh.

~~~text
com.acme.boot.autoconfigure
├── AcmeClientAutoConfiguration
├── AcmeMetricsConfiguration
└── AcmeProperties
~~~

Package này không phải gốc quét component của ứng dụng sử dụng thư viện. Sự độc lập đó là một phần của cam kết của thư viện dùng lại được.

Import tường minh còn làm nhánh tùy chọn dễ kiểm tra hơn: người đọc nhìn thấy cấu hình nào thuộc phần tích hợp thay vì phải suy ra component ẩn từ một vùng quét quá rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-properties-integration">Tích hợp @ConfigurationProperties</a>

<details>
<summary>Xem chi tiết</summary>

ConfigurationProperties là ranh giới tự nhiên giữa externalized configuration của ứng dụng và quyết định trong auto-configuration. Kiểu properties sở hữu tập giá trị có cấu trúc; auto-configuration dùng những giá trị đó để tạo hoặc tùy biến hạ tầng.

~~~java
@ConfigurationProperties("acme.client")
public class AcmeProperties {
    private URI endpoint;
    private Duration timeout = Duration.ofSeconds(2);
    // accessors
}
~~~

Auto-configuration có thể bật kiểu properties và inject nó vào Bean method. Một số property cụ thể cũng có thể được condition dùng để bật/tắt tính năng.

Giữ phạm vi sở hữu rõ: cách nạp property, precedence, relaxed binding và validation thuộc Externalized Configuration. Phần này chỉ tập trung vào việc custom auto-configuration cung cấp và sử dụng một quy ước cấu hình ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="optional-dependency-design">Thiết kế Dependency tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

Autoconfigure artifact dễ dùng lại hơn khi dependency đại diện cho tính năng tùy chọn cũng là tùy chọn. Auto-configuration khi đó kiểm tra sự tồn tại của công nghệ thay vì ép tất cả công nghệ lên mọi bên sử dụng.

Ví dụ:

~~~text
acme-spring-boot
├── Boot/autoconfigure APIs cốt lõi
├── thư viện Acme client tùy chọn
└── phần tích hợp metrics tùy chọn

acme-spring-boot-starter
└── chọn tập dependency phổ biến cho đa số bên sử dụng
~~~

Cách tách này cho phép bên sử dụng nâng cao chỉ dùng artifact autoconfigure rồi tự chọn thư viện tùy chọn; starter cung cấp một lựa chọn dependency thuận tiện cho đường dùng phổ biến.

Thiết kế dependency tùy chọn phải đi cùng condition và sự cô lập class loading. Đánh dấu dependency là optional vẫn chưa đủ nếu configuration liên kết sớm tới type bị thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-configure-metadata">Auto-configuration Processor và Condition Metadata</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot cung cấp auto-configuration annotation processor có thể tạo META-INF/spring-autoconfigure-metadata.properties. Metadata giúp Boot lọc sớm một số candidate chắc chắn không khớp, giảm phần việc khi khởi động.

Đây là tầng tối ưu, không phải một ngôn ngữ condition thứ hai. Các annotation Conditional vẫn biểu diễn ngữ nghĩa áp dụng thật sự của auto-configuration.

~~~text
annotation trong mã nguồn
→ annotation processor
→ spring-autoconfigure-metadata.properties
→ lọc sớm candidate khi metadata đủ thông tin
→ ngữ nghĩa condition bình thường vẫn quyết định kết quả
~~~

Không thiết kế hành vi chỉ “đúng” khi metadata được tạo ra. Metadata cải thiện hiệu quả lựa chọn; nó không nên thay đổi kết quả dự kiến.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stable-auto-configuration-identities">Danh tính class ổn định cho Ordering và Exclusion</a>

<details>
<summary>Xem chi tiết</summary>

Auto-configuration khác và ứng dụng có thể tham chiếu class auto-configuration bằng type hoặc class name để ordering và exclusion. Vì vậy danh tính của class trở thành một phần của quy ước công khai của phần tích hợp dù ứng dụng hiếm khi tự khởi tạo class đó.

Trong Spring Boot 3.3 chưa có cơ chế AutoConfiguration.replacements tổng quát để tự ánh xạ danh tính cũ sang class mới. Vì thế việc đổi tên hoặc di chuyển auto-configuration đã được công bố cần kế hoạch tương thích thay vì giả định Boot sẽ tự sửa mọi tham chiếu.

Quy tắc thực tế: chọn package/class name ổn định, đặc biệt cho starter công khai. Nếu bắt buộc có thay đổi phá vỡ tương thích, hãy đánh giá các tham chiếu before/after, exclude/excludeName, tài liệu và cấu hình của bên sử dụng.

Đây là ranh giới phiên bản: những khả năng ở dòng Boot mới hơn không được suy ngược về baseline 3.3 của repository.

Một auto-configuration tự xây dựng chỉ đáng tin khi các trường hợp khớp, không khớp và back-off đều có thể được kiểm chứng lặp lại bằng thực thi. Chương tiếp theo xây dựng ma trận kiểm chứng đó bằng các context runner tập trung.

</details>

- [Quay lại đầu trang](#back-to-top)
