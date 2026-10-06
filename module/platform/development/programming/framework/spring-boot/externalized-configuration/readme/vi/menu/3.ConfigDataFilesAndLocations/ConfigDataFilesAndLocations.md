<a id="back-to-top"></a>

# Tệp Config Data và vị trí tìm kiếm

## Menu
- [Config Data đóng góp gì vào cấu hình Spring Boot?](#config-data-role)
- [Các vị trí tìm kiếm mặc định trong và ngoài ứng dụng đã đóng gói](#default-search-locations)
- [Cấu hình đóng gói sẵn và cấu hình bên ngoài](#packaged-vs-external-config)
- [application.properties và application.yaml](#properties-vs-yaml)
- [Đổi tên cơ sở bằng spring.config.name](#spring-config-name)
- [Thay thế vị trí tìm kiếm bằng spring.config.location](#spring-config-location)
- [Bổ sung vị trí tìm kiếm bằng spring.config.additional-location](#spring-config-additional-location)
- [Tệp, thư mục, mẫu wildcard và nhóm vị trí](#location-files-directories-groups)
- [Vì sao đầu vào xác định vị trí cấu hình phải được cung cấp sớm?](#early-config-location-inputs)

## <a id="config-data-role">Config Data đóng góp gì vào cấu hình Spring Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Config Data là mô hình Spring Boot dùng để nạp các tài liệu cấu hình trước khi application context được tạo đầy đủ. Nó bao gồm các tệp quen thuộc như `application.properties`, `application.yaml`, biến thể theo profile, vị trí tìm kiếm tường minh và các tài nguyên được import.

Mối liên hệ quan trọng là: Config Data đóng góp các `PropertySource` vào cùng `Environment` mà ứng dụng sử dụng. Nó không tạo một hệ cấu hình riêng. Giá trị được nạp từ tệp vẫn cạnh tranh với biến môi trường, system property, tham số dòng lệnh và các nguồn khác theo precedence của Boot.

Nhìn theo Config Data giúp giải thích vì sao quá trình tìm tệp, thứ tự vị trí, kích hoạt profile và import đều ảnh hưởng đến tập giá trị ứng viên trước khi bean ứng dụng bắt đầu tiêu thụ cấu hình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="default-search-locations">Các vị trí tìm kiếm mặc định trong và ngoài ứng dụng đã đóng gói</a>

<details>
<summary>Xem chi tiết</summary>

Theo mặc định, Boot tìm `application.properties` và các biến thể YAML tại một tập vị trí xác định. Trên classpath, Boot kiểm tra classpath root và `classpath:/config/`. Bên ngoài ứng dụng đã đóng gói, Boot kiểm tra thư mục hiện tại, thư mục con `config/` và các thư mục con trực tiếp bên trong `config/`.

Các vị trí bên ngoài hữu ích cho việc ghi đè theo môi trường triển khai vì chúng được xét sau các vị trí đóng gói trong thứ tự Config Data. Nhờ vậy jar có thể mang giá trị mặc định an toàn, còn môi trường triển khai cung cấp tệp khác mà không cần tạo lại gói ứng dụng.

Ví dụ:

```text
app.jar
application.properties
config/
  application.properties
  tenant-a/application.properties
```

Vị trí tìm kiếm mặc định `config/*/` của Boot giúp các thư mục con trực tiếp phù hợp khi nền tảng mount nhiều thư mục cấu hình dưới cùng một thư mục cha.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packaged-vs-external-config">Cấu hình đóng gói sẵn và cấu hình bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

Mẫu triển khai phổ biến là giữ các giá trị nền trong jar và chỉ ghi đè phần thay đổi ở bên ngoài. Thứ tự Config Data của Boot hỗ trợ đúng mô hình đó: property ứng dụng đóng gói được xét trước property ứng dụng bên ngoài, còn các biến thể theo profile tham gia cùng hệ thống.

Ví dụ, trong jar có:

```properties
orders.timeout=2s
```

và tệp `application.properties` bên ngoài có:

```properties
orders.timeout=5s
```

Trong phạm vi Config Data, giá trị bên ngoài thắng. Tuy nhiên nó vẫn có thể bị nguồn precedence cao hơn như biến môi trường hoặc tham số dòng lệnh ghi đè. Vì vậy "tệp ngoài ghi đè tệp trong jar" là đúng trong thứ tự Config Data, nhưng chưa phải quy tắc cuối cùng của toàn bộ `Environment`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="properties-vs-yaml">application.properties và application.yaml</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot hỗ trợ cả Java properties lẫn YAML cho cấu hình ứng dụng. YAML thuận tiện với dữ liệu phân cấp; `.properties` làm từng key hiển thị tường minh và thường dễ so sánh precedence. Cả hai cuối cùng đều đóng góp giá trị vào `Environment`.

Quy tắc thực tế là nên nhất quán. Nếu cả tệp `.properties` và YAML cùng tồn tại tại một vị trí, Boot 3.3 cho `.properties` precedence cao hơn YAML. Cố ý dựa vào khác biệt đó làm cấu hình khó suy luận hơn, nên tài liệu chính thức khuyến nghị dùng một định dạng cho toàn ứng dụng khi có thể.

Định dạng tệp không thay đổi mô hình ở mức cao: vị trí, profile, import và precedence của nguồn thuộc tính vẫn quyết định giá trị cuối cùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-config-name">Đổi tên cơ sở bằng spring.config.name</a>

<details>
<summary>Xem chi tiết</summary>

Boot mặc định tìm tệp có tên cơ sở `application`. `spring.config.name` thay đổi tên cơ sở đó. Ví dụ:

```bash
java -jar app.jar --spring.config.name=myservice
```

Boot sẽ tìm `myservice.properties` và các biến thể YAML tại những vị trí được cấu hình, đồng thời xét các biến thể theo profile khi phù hợp.

Thiết lập này thay đổi **Boot sẽ tìm tệp nào**, vì vậy nó phải có sẵn trước giai đoạn tìm Config Data. Do đó `spring.config.name` là một `Environment` property cần được cung cấp sớm, không phải giá trị có thể đáng tin cậy nếu đặt trong chính Config Data mà nó đang điều khiển tên.

Chỉ nên dùng khi tên cơ sở khác là quy ước triển khai có chủ đích; không cần tạo thêm lớp tên tệp nếu `application` kết hợp profile/import đã diễn đạt yêu cầu rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-config-location">Thay thế vị trí tìm kiếm bằng spring.config.location</a>

<details>
<summary>Xem chi tiết</summary>

`spring.config.location` **thay thế** các vị trí tìm Config Data mặc định của Boot bằng danh sách bạn cung cấp. Giá trị có thể là tệp hoặc thư mục; vị trí là thư mục nên kết thúc bằng `/` để Boot có thể ghép tên cơ sở sinh từ `spring.config.name`.

```bash
java -jar app.jar \
  --spring.config.location=optional:classpath:/defaults/,optional:file:./runtime-config/
```

Điểm cần nhớ là hành vi thay thế. Khi đã đặt `spring.config.location`, các vị trí mặc định thông thường không còn nằm trong tập tìm kiếm trừ khi bạn tự đưa chúng vào lại. Điều này hữu ích cho môi trường triển khai được kiểm soát chặt, nhưng cũng là nguyên nhân phổ biến khiến `application.properties` tưởng như "biến mất".

Các vị trí được xử lý theo thứ tự khai báo; vị trí sau có thể ghi đè vị trí trước trong cùng tầng precedence liên quan.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-config-additional-location">Bổ sung vị trí tìm kiếm bằng spring.config.additional-location</a>

<details>
<summary>Xem chi tiết</summary>

`spring.config.additional-location` giữ nguyên các vị trí mặc định của Boot rồi bổ sung vị trí mới phía sau. Vì vậy giá trị từ vị trí bổ sung có thể ghi đè giá trị từ các vị trí mặc định.

```bash
java -jar app.jar \
  --spring.config.additional-location=optional:file:./customer-config/
```

Đây thường là lựa chọn an toàn hơn khi môi trường triển khai chỉ muốn phủ một số giá trị ghi đè lên cấu hình chuẩn của ứng dụng. Ngược lại, `spring.config.location` diễn đạt ý "dùng tập vị trí này thay cho các vị trí mặc định".

Khác biệt này có tác động vận hành rõ ràng: nếu đội ngũ chỉ muốn thêm một thư mục ghi đè nhưng lại dùng `spring.config.location`, họ có thể vô tình loại toàn bộ vị trí tìm kiếm mặc định và làm nhiều property không liên quan biến mất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="location-files-directories-groups">Tệp, thư mục, mẫu wildcard và nhóm vị trí</a>

<details>
<summary>Xem chi tiết</summary>

Vị trí Config Data có thể trỏ đến tệp hoặc thư mục. Với thư mục, Boot tìm tệp theo tên cơ sở đã cấu hình; với tệp, Boot nạp trực tiếp. Boot cũng hỗ trợ mẫu wildcard cho thư mục bên ngoài, ví dụ `file:./config/*/`, hữu ích khi nền tảng gắn nhiều cây cấu hình dưới cùng một thư mục cha.

Wildcard có giới hạn: dùng cho thư mục bên ngoài, chỉ có một `*`, và các vị trí khớp được sắp theo đường dẫn tuyệt đối theo thứ tự chữ cái. Không nên phụ thuộc vào thứ tự liệt kê ngẫu nhiên của hệ thống tệp.

Nhóm vị trí giải quyết vấn đề khác. Dấu chấm phẩy (`;`) nhóm nhiều vị trí ở cùng một mức precedence, còn dấu phẩy tách các nhóm kế tiếp. Điều này đặc biệt quan trọng với tệp theo profile vì "xử lý hết vị trí A rồi đến B" có thể tạo kết quả khác với "A và B là cùng một tầng rồi áp dụng quy tắc giá trị sau cùng thắng của profile trong nhóm".

Nên dùng nhóm khi nhiều vị trí cùng đại diện cho một tầng logic, chẳng hạn nhóm classpath và nhóm bên ngoài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="early-config-location-inputs">Vì sao đầu vào xác định vị trí cấu hình phải được cung cấp sớm?</a>

<details>
<summary>Xem chi tiết</summary>

`spring.config.name`, `spring.config.location` và `spring.config.additional-location` quyết định chính cách Config Data được tìm. Vì vậy Boot đọc chúng rất sớm, trước khi có thể nạp những tệp mà các thiết lập đó điều khiển.

Hãy cung cấp chúng qua một nguồn `Environment` đã có sẵn, như biến môi trường hệ điều hành, JVM system property hoặc tham số dòng lệnh:

```bash
SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./ops/ java -jar app.jar
```

Một cấu hình vòng tròn không hợp lý: đặt `spring.config.location=...` trong `application.properties` rồi mong nó thay đổi nơi mà cùng giai đoạn tìm kiếm đã tìm `application.properties` là quá muộn.

Khi Boot có vẻ bỏ qua vị trí tùy chỉnh, hãy kiểm tra trước hai điểm: thiết lập đã được cung cấp đủ sớm chưa, và có vô tình dùng `spring.config.location` để thay thế các vị trí mặc định thay vì `spring.config.additional-location` để bổ sung không.

</details>

- [Quay lại đầu trang](#back-to-top)
