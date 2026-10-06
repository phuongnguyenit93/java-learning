<a id="back-to-top"></a>

# Environment, placeholder và @Value

## Menu
- [Đọc giá trị đã phân giải qua Environment](#environment-access)
- [Placeholder ${...} được phân giải như thế nào?](#placeholder-resolution)
- [Giá trị mặc định của placeholder và trường hợp thiếu giá trị](#placeholder-defaults)
- [Tiêm giá trị đơn lẻ bằng @Value](#value-injection)
- [Vì sao tên thuộc tính kebab-case chuẩn quan trọng?](#canonical-property-names)
- [@Value và @ConfigurationProperties](#value-vs-configuration-properties)
- [SpEL và ranh giới của @Value](#spel-boundary)

## <a id="environment-access">Đọc giá trị đã phân giải qua Environment</a>

<details>
<summary>Xem chi tiết</summary>

Sau khi Spring Boot đã ghép các nguồn thuộc tính, mã ứng dụng có thể dùng Environment để đọc giá trị có hiệu lực của một khóa. Mô hình quan trọng là Environment cung cấp góc nhìn đã phân giải trên toàn bộ nguồn tham gia; nó không phải một tệp cấu hình khác.

~~~java
@Component
class PricingPolicy {
    PricingPolicy(Environment environment) {
        String currency = environment.getProperty("shop.currency", "USD");
    }
}
~~~

Lời gọi này tuân theo đúng quy tắc precedence đã học ở chương trước. Nếu shop.currency có trong application.properties nhưng tham số dòng lệnh có precedence cao hơn cung cấp --shop.currency=EUR thì Environment trả về EUR. Chỉ nên đọc Environment trực tiếp khi việc tra cứu thực sự mang tính động hoặc hạ tầng; đừng biến nhiều lần tra cứu các khóa liên quan thành cách tự ánh xạ thủ công thay cho @ConfigurationProperties.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="placeholder-resolution">Placeholder ${...} được phân giải như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Placeholder ${...} yêu cầu bộ phân giải thuộc tính thay một khóa bằng giá trị lấy từ Environment. Placeholder có thể xuất hiện trong annotation và ngay trong giá trị cấu hình, vì vậy một property có thể được tạo từ property khác đã phân giải.

~~~properties
app.name=orders
app.description=${app.name} service
~~~

Placeholder được phân giải khi cơ chế tiêu thụ yêu cầu giá trị. Nó không tạo `PropertySource` mới và không thay đổi precedence: Boot xác định tập giá trị có hiệu lực trước, sau đó placeholder đọc từ tập đó. Ranh giới này giúp chẩn đoán đúng chỗ khi giá trị bất ngờ: hoặc khóa được tham chiếu đã phân giải khác dự kiến, hoặc chính biểu thức placeholder không đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="placeholder-defaults">Giá trị mặc định của placeholder và trường hợp thiếu giá trị</a>

<details>
<summary>Xem chi tiết</summary>

Placeholder có thể khai báo giá trị dự phòng sau dấu hai chấm, ví dụ ${region:us-east}. Giá trị này chỉ được dùng khi khóa được tham chiếu không thể phân giải; nó không trở thành property mới có precedence cao hơn và cũng không được công bố như một mục mới trong Environment.

~~~properties
service.region=${REGION_NAME:local}
~~~

Giá trị mặc định phù hợp với phương án dự phòng nhỏ và an toàn, nhưng cũng có thể che giấu cấu hình bắt buộc bị thiếu. Nếu việc thiếu giá trị phải làm quá trình khởi động thất bại, nên dùng binding bắt buộc kết hợp xác thực thay vì âm thầm cung cấp giá trị mặc định. Đây là lựa chọn thiết kế giữa sự tiện dụng và một hợp đồng cấu hình chặt chẽ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-injection">Tiêm giá trị đơn lẻ bằng @Value</a>

<details>
<summary>Xem chi tiết</summary>

@Value phù hợp khi chỉ cần một số ít giá trị đơn lẻ. Annotation này nhờ Spring container phân giải placeholder/biểu thức rồi tiêm kết quả vào tham số constructor, trường hoặc tham số phương thức.

~~~java
@Component
class Banner {
    Banner(@Value("${app.title:Demo}") String title) {
        this.title = title;
    }
}
~~~

Giá trị vẫn xuất phát từ Environment do Boot xây dựng, vì vậy thay đổi `PropertySource` thắng precedence cũng làm thay đổi giá trị được tiêm. @Value phù hợp với cấu hình nhỏ và cục bộ. Khi nhiều giá trị tạo thành một không gian tên có ý nghĩa chung, đối tượng @ConfigurationProperties có kiểu sẽ thể hiện ranh giới trách nhiệm, khả năng xác thực, metadata và khả năng tái cấu trúc rõ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="canonical-property-names">Vì sao tên thuộc tính kebab-case chuẩn quan trọng?</a>

<details>
<summary>Xem chi tiết</summary>

Khi viết placeholder, Spring Boot khuyến nghị dạng kebab-case chữ thường chuẩn như ${demo.item-price}. Dạng này cho phép Boot áp dụng khả năng tra cứu tên linh hoạt nhất trên các `PropertySource` được hỗ trợ.

Nếu dùng ${demo.itemPrice}, placeholder không nhận được cơ chế tra cứu tên linh hoạt mà dạng khóa chuẩn cung cấp, bao gồm khả năng khớp với cách viết tương ứng của biến môi trường. Quy tắc thực hành là thiết kế khóa cấu hình của ứng dụng bằng kebab-case chữ thường và cũng tham chiếu theo dạng chuẩn đó trong placeholder, dù nguồn vật lý như biến môi trường hệ điều hành phải dùng cách viết khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="value-vs-configuration-properties">@Value và @ConfigurationProperties</a>

<details>
<summary>Xem chi tiết</summary>

@Value và @ConfigurationProperties đều tiêu thụ cấu hình đã được phân giải nhưng tối ưu cho hai dạng nhu cầu khác nhau. @Value ngắn gọn với giá trị đơn lẻ và hỗ trợ SpEL. @ConfigurationProperties nhóm một không gian tên thành đối tượng có kiểu, hỗ trợ relaxed binding đầy đủ, binding cấu trúc, xác thực và metadata cấu hình.

| Nhu cầu | Nên ưu tiên |
| --- | --- |
| Một scalar cục bộ hoặc biểu thức SpEL | @Value |
| Nhóm thiết lập liên quan có thể tái sử dụng | @ConfigurationProperties |
| Cấu hình nested/list/map | @ConfigurationProperties |
| Metadata IDE cho khóa tùy chỉnh | @ConfigurationProperties |

Vì vậy lựa chọn không liên quan annotation nào có precedence cao hơn. Precedence đã được quyết định trước khi một trong hai cơ chế nhận giá trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spel-boundary">SpEL và ranh giới của @Value</a>

<details>
<summary>Xem chi tiết</summary>

@Value có thể đánh giá Spring Expression Language (SpEL), còn @ConfigurationProperties chủ ý không làm việc đó. Vì vậy @Value mạnh hơn cho việc tiêm cục bộ dựa trên biểu thức nhưng cũng có hợp đồng khác với data binding thuần túy.

Một chuỗi trông giống SpEL trong application.properties không tự được đánh giá chỉ vì Config Data đã nạp nó. Việc đánh giá biểu thức xảy ra khi bên tiêu thụ như @Value diễn giải giá trị. Ranh giới này tránh hai hiểu nhầm: Config Data không phải bộ máy biểu thức, và @ConfigurationProperties chủ ý tập trung vào binding dữ liệu thay vì thực thi biểu thức tùy ý.

Nếu cấu hình cần giữ dạng dữ liệu dễ quan sát và di chuyển giữa môi trường, nên ưu tiên property thông thường cùng binding có kiểu. Chỉ dùng SpEL khi ý nghĩa của biểu thức thực sự là một phần của mã tiêu thụ.

</details>

- [Quay lại đầu trang](#back-to-top)
