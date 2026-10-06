<a id="back-to-top"></a>

# Relaxed Binding, kiểu dữ liệu phức hợp và chuyển đổi kiểu

## Menu
- [Relaxed Binding chuẩn hóa tên thuộc tính như thế nào?](#relaxed-binding)
- [Chuyển tên thuộc tính chuẩn sang biến môi trường](#environment-variable-mapping)
- [Binding đối tượng cấu hình lồng nhau](#nested-object-binding)
- [Binding List và Set](#list-set-binding)
- [Binding Map và giữ nguyên khóa có ký tự đặc biệt](#map-binding)
- [Vì sao List ở nguồn ưu tiên cao thay thế List ở nguồn ưu tiên thấp?](#list-replacement)
- [Chuyển đổi kiểu trong quá trình binding](#type-conversion)
- [Duration, DataSize và các kiểu đích phổ biến](#duration-datasize-types)

## <a id="relaxed-binding">Relaxed Binding chuẩn hóa tên thuộc tính như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Relaxed binding tách tên property về mặt logic khỏi cách viết vật lý mà từng nguồn cấu hình yêu cầu. Ứng dụng có thể có Java property tên firstName trong khi cấu hình được viết ở dạng kebab-case chuẩn như customer.first-name.

Với @ConfigurationProperties, Boot nhận các biến thể phổ biến khi nguồn hỗ trợ: kebab case, camel case và dấu gạch dưới trong properties/YAML, cùng dạng chữ hoa dùng dấu gạch dưới cho biến môi trường hệ điều hành. Sự linh hoạt này thuộc cơ chế binding; không có nghĩa mọi bên tiêu thụ đều tra cứu relaxed name giống hệt nhau. Vì vậy kebab-case chữ thường vẫn là dạng an toàn nhất cho tài liệu, metadata, tiền tố và placeholder.

~~~text
customer.first-name      ← key chuẩn của ứng dụng
customer.firstName       ← được chấp nhận ở nguồn tệp phù hợp
customer.first_name      ← được chấp nhận ở nguồn tệp phù hợp
CUSTOMER_FIRSTNAME       ← dạng biến môi trường
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="environment-variable-mapping">Chuyển tên thuộc tính chuẩn sang biến môi trường</a>

<details>
<summary>Xem chi tiết</summary>

Hệ điều hành thường giới hạn ký tự trong tên biến môi trường, vì vậy Boot định nghĩa cách ánh xạ xác định từ tên property chuẩn. Bắt đầu từ dạng chuẩn: thay dấu chấm bằng dấu gạch dưới, bỏ dấu gạch ngang và chuyển sang chữ hoa.

~~~text
spring.main.log-startup-info
        ↓
SPRING_MAIN_LOGSTARTUPINFO
~~~

Với phần tử List có chỉ số, đặt chỉ số số giữa hai dấu gạch dưới. Ví dụ my.service[0].other ánh xạ thành MY_SERVICE_0_OTHER.

Nên suy ra tên biến môi trường từ khóa chuẩn thay vì tự nghĩ cách viết riêng cho từng môi trường triển khai. Nếu một khóa khó ánh xạ có quy luật, nên cải thiện khóa cấu hình công khai thay vì buộc đội vận hành ghi nhớ ngoại lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-object-binding">Binding đối tượng cấu hình lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

Đối tượng cấu hình có thể chứa đối tượng khác để hình dạng Java phản ánh cấu trúc phân cấp của property. Cách này phù hợp khi một không gian tên có các nhóm con mang ý nghĩa riêng thay vì một danh sách trường phẳng.

~~~properties
mail.host=smtp.example.com
mail.security.enabled=true
mail.security.protocol=tls
~~~

~~~java
@ConfigurationProperties("mail")
public class MailProperties {
    private String host;
    private final Security security = new Security();

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Security getSecurity() { return security; }

    public static class Security {
        private boolean enabled;
        private String protocol;
        // getters/setters
    }
}
~~~

Binding lồng nhau giữ các giá trị liên quan ở gần nhau và tạo vị trí tự nhiên cho xác thực lồng nhau. Cấu trúc nên phản ánh miền cấu hình chứ không phải sao chép mọi package hoặc ranh giới class trong codebase.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-set-binding">Binding List và Set</a>

<details>
<summary>Xem chi tiết</summary>

Boot có thể bind giá trị theo chỉ số hoặc dạng được nguồn hỗ trợ vào property collection. Danh sách YAML dễ đọc với cấu hình có cấu trúc; `.properties` có thể dùng khóa có chỉ số hoặc dạng phân tách bằng dấu phẩy phù hợp.

~~~yaml
app:
  servers:
    - api-a.example
    - api-b.example
~~~

Với biến môi trường, chỉ số của List dùng quy ước gạch dưới, chẳng hạn APP_SERVERS_0 và APP_SERVERS_1 nếu property đích có hình dạng tương ứng. Collection vẫn chịu precedence giữa các nguồn, nhưng List có quy tắc thay thế quan trọng: không được giả định phần tử từ nhiều nguồn sẽ tự gộp theo từng chỉ số.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="map-binding">Binding Map và giữ nguyên khóa có ký tự đặc biệt</a>

<details>
<summary>Xem chi tiết</summary>

Map phù hợp khi tập khóa cấu hình mang tính dữ liệu và không cố định trong kiểu Java. Boot có thể bind một cây con vào Map đồng thời chuyển đổi kiểu của giá trị Map.

~~~properties
labels.region=eu
labels.tier=gold
~~~

Cần chú ý khóa chứa ký tự đặc biệt. Cú pháp ngoặc vuông giữ những ký tự có thể bị hiểu như dấu phân cách đường dẫn hoặc bị loại bỏ trong quá trình binding. Với Map<String,Object>, khóa [a.b] giữ a.b thành một khóa Map; còn a.b không có ngoặc vuông tự nhiên biểu diễn cấu trúc lồng nhau.

Quy tắc khóa Map là một phần của hợp đồng binding, không phải lý do để nhét dữ liệu tùy ý vào tên property. Nếu cấu hình bắt đầu giống một kho tài liệu lớn, nên xem lại properties có còn là cách biểu diễn phù hợp không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="list-replacement">Vì sao List ở nguồn ưu tiên cao thay thế List ở nguồn ưu tiên thấp?</a>

<details>
<summary>Xem chi tiết</summary>

Với binding danh sách phức hợp thông thường qua `@ConfigurationProperties`, khi cùng một thuộc tính List được khai báo ở nhiều nguồn cấu hình, Spring Boot không gộp từng phần tử của danh sách đã bind giữa các nguồn. List từ nguồn tham gia có mức ưu tiên cao hơn thay thế toàn bộ List ở nguồn thấp hơn.

~~~yaml
# precedence thấp
app:
  users:
    - name: alice
      role: reader
    - name: bob
      role: writer

# precedence cao
app:
  users:
    - name: carol
      role: admin
~~~

List `users` có hiệu lực chỉ còn `carol`. Điều này đặc biệt quan trọng với Config Data theo profile và các nguồn ghi đè bên ngoài: ghi đè một phần tử không phải thao tác vá lên List cũ. Map có hành vi khác và có thể kết hợp khóa từ nhiều nguồn, trong đó giá trị từ nguồn ưu tiên cao hơn thắng khi trùng khóa.

Không được khái quát quy tắc binding này cho mọi thuộc tính Boot có dạng danh sách. `spring.profiles.include` là trường hợp đặc biệt đã được tài liệu hóa: Boot xử lý các profile include theo từng nguồn thuộc tính và bổ sung các profile thu được, thay vì coi nó như một List phức hợp thông thường chỉ lấy toàn bộ giá trị từ một nguồn ưu tiên cao nhất. Hãy suy luận theo hợp đồng của từng cơ chế thay vì giả định mọi cấu hình có dạng danh sách đều xử lý giống nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-conversion">Chuyển đổi kiểu trong quá trình binding</a>

<details>
<summary>Xem chi tiết</summary>

Nguồn cấu hình chủ yếu biểu diễn văn bản, còn đối tượng property có kiểu thì không. Trong quá trình @ConfigurationProperties binding, Boot chuyển giá trị đã phân giải sang kiểu Java đích. Các kiểu thường gặp gồm number, boolean, enum, InetAddress, URI, Duration, DataSize và nhiều kiểu Java chuẩn.

~~~properties
client.timeout=2s
client.max-payload=10MB
~~~

~~~java
@ConfigurationProperties("client")
record ClientProperties(Duration timeout, DataSize maxPayload) {}
~~~

Lỗi chuyển đổi kiểu khác với thiếu tệp cấu hình hoặc lỗi xác thực: khóa đã được tìm thấy nhưng giá trị không thể chuyển sang kiểu đích. Có thể cung cấp chuyển đổi tùy chỉnh, nhưng bean chuyển đổi có thể được yêu cầu rất sớm trong vòng đời nên các phụ thuộc của nó cần tối giản. Phần nội bộ của cơ chế chuyển đổi Spring thuộc module khác chịu trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="duration-datasize-types">Duration, DataSize và các kiểu đích phổ biến</a>

<details>
<summary>Xem chi tiết</summary>

Duration và DataSize nên dùng đơn vị tường minh vì số thuần rất dễ gây mơ hồ. Khi bind sang các kiểu này, Boot chấp nhận dạng dễ đọc như 250ms, 2s, 5m, 10KB hoặc 20MB.

~~~properties
cache.ttl=30s
upload.max-size=25MB
~~~

Annotation như @DurationUnit hoặc @DataSizeUnit có thể xác định đơn vị mặc định cho giá trị số không kèm đơn vị khi cần tương thích với property cũ. Với cấu hình mới, nên ghi đơn vị trực tiếp để con người và công cụ đều nhìn thấy ý nghĩa.

Bài học rộng hơn là cấu hình công khai nên dùng kiểu phù hợp với miền. Duration truyền đạt ý nghĩa thời gian an toàn hơn `long` mà đơn vị chỉ tồn tại trong tài liệu.

</details>

- [Quay lại đầu trang](#back-to-top)
