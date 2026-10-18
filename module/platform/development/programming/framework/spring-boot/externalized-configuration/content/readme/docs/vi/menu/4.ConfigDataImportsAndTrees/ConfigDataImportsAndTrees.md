<a id="back-to-top"></a>

# Nhập Config Data và cây cấu hình

## Menu
- [Nhập Config Data bổ sung bằng spring.config.import](#spring-config-import)
- [Giá trị được nhập liên hệ thế nào với tài liệu khai báo import?](#import-precedence)
- [Vị trí cố định và vị trí tương đối theo tài nguyên import](#fixed-vs-relative-imports)
- [Tài nguyên import tùy chọn và hành vi khi tài nguyên không tồn tại](#optional-imports)
- [Nhập tệp cấu hình không có phần mở rộng bằng gợi ý định dạng](#extension-hints)
- [Cây cấu hình và đầu vào kiểu một tệp cho mỗi thuộc tính](#configtree-imports)
- [Chẩn đoán lỗi khi nhập Config Data](#import-failure-model)

## <a id="spring-config-import">Nhập Config Data bổ sung bằng spring.config.import</a>

<details>
<summary>Xem chi tiết</summary>

`spring.config.import` cho phép một tài liệu Config Data nạp thêm cấu hình từ nơi khác. Cơ chế này hữu ích khi cấu hình được tách theo trách nhiệm, được sinh bên ngoài tệp chính hoặc được nền tảng triển khai mount thành tài nguyên riêng.

```properties
spring.config.import=optional:file:./ops.properties
```

Import tham gia trực tiếp vào quá trình xử lý Config Data, không phải một thao tác đọc tệp tùy ý lúc chạy. Dữ liệu được import trở thành một phần của các tài liệu cấu hình đóng góp vào `Environment`, vì vậy vẫn tham gia precedence và cách xử lý profile.

Import được xử lý khi Boot phát hiện tài liệu khai báo nó, và một tài nguyên cụ thể chỉ được nạp một lần dù được khai báo lặp lại. Nên dùng import để làm cấu trúc cấu hình rõ hơn, không tạo chuỗi phụ thuộc dài khiến khó lần theo nơi một giá trị thực sự được sở hữu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="import-precedence">Giá trị được nhập liên hệ thế nào với tài liệu khai báo import?</a>

<details>
<summary>Xem chi tiết</summary>

Tài liệu được import được xem như chèn ngay bên dưới tài liệu khai báo import, và giá trị từ tài liệu được import có precedence cao hơn giá trị trong tài liệu thực hiện import.

```properties
# application.properties
app.name=base
spring.config.import=extra.properties
```

```properties
# extra.properties
app.name=imported
```

Giá trị có hiệu lực giữa hai tài liệu trên là `app.name=imported`. Vị trí vật lý của dòng `spring.config.import` trong cùng một tài liệu không thay đổi quan hệ này. Nếu một import liệt kê nhiều vị trí, chúng được xử lý theo thứ tự khai báo và import sau có thể ghi đè import trước.

Thứ tự cục bộ của import vẫn nằm trong mô hình nguồn thuộc tính rộng hơn, nên biến môi trường hoặc tham số dòng lệnh có precedence cao hơn vẫn có thể ghi đè giá trị đã import.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fixed-vs-relative-imports">Vị trí cố định và vị trí tương đối theo tài nguyên import</a>

<details>
<summary>Xem chi tiết</summary>

Boot phân biệt import **cố định** với import **tương đối theo tài liệu khai báo**. Vị trí bắt đầu bằng `/` hoặc tiền tố dạng URL như `file:` hay `classpath:` là vị trí cố định và được phân giải độc lập với tài liệu gọi nó. Các vị trí còn lại được phân giải tương đối từ tài liệu đang khai báo import.

```properties
# /demo/application.properties
spring.config.import=core/core.properties
```

Vị trí này được tính từ `/demo/`, nên Boot tìm `/demo/core/core.properties`. Nếu tệp đó lại chứa `spring.config.import=extra/extra.properties`, import thứ hai được tính tương đối từ `/demo/core/`.

Tiền tố `optional:` không làm thay đổi việc vị trí là cố định hay tương đối. Import tương đối phù hợp cho gói cấu hình tự chứa; import cố định rõ ràng hơn khi tài nguyên có một địa chỉ triển khai cố định bất kể tài liệu khai báo nằm ở đâu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="optional-imports">Tài nguyên import tùy chọn và hành vi khi tài nguyên không tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

Theo mặc định, vị trí Config Data được cấu hình nhưng không tồn tại sẽ làm quá trình khởi động thất bại với `ConfigDataLocationNotFoundException`. Cách dừng sớm khi lỗi như vậy hữu ích khi tệp thiếu là bắt buộc để ứng dụng vận hành đúng.

Chỉ thêm `optional:` khi việc tài nguyên không tồn tại là trạng thái hợp lệ:

```properties
spring.config.import=optional:file:./local-overrides.properties
```

Khi đó ứng dụng vẫn có thể khởi động nếu tệp không có. Cùng tiền tố này dùng được với `spring.config.location` và `spring.config.additional-location`.

Không nên biến cấu hình môi trường sản xuất bắt buộc thành tùy chọn chỉ để khởi động qua được. Làm vậy thường đẩy lỗi sang giai đoạn sau dưới dạng thiếu property, lỗi binding hoặc hành vi lúc chạy sai, khiến nguyên nhân gốc khó nhìn thấy hơn. `optional:` phù hợp với lớp ghi đè thật sự tùy chọn, tệp cục bộ của lập trình viên hoặc tài nguyên có thể vắng theo thiết kế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="extension-hints">Nhập tệp cấu hình không có phần mở rộng bằng gợi ý định dạng</a>

<details>
<summary>Xem chi tiết</summary>

Một số nền tảng mount tệp mà không có phần mở rộng, khiến Boot không thể suy ra nội dung cần phân tích như properties hay YAML. Gợi ý phần mở rộng của Config Data giải quyết sự mơ hồ đó bằng cách gắn định dạng mong muốn ngay trên vị trí import.

```properties
spring.config.import=file:/etc/config/myconfig[.yaml]
```

Boot sẽ đọc tài nguyên `/etc/config/myconfig` không có phần mở rộng bằng bộ nạp YAML. Với baseline Spring Boot 3.3 của module này, dạng gợi ý phần mở rộng được tài liệu hóa là hậu tố trong ngoặc vuông như ví dụ trên: `[.yaml]`.

Chỉ nên dùng gợi ý khi tài nguyên thật sự không có phần mở rộng vì ràng buộc nền tảng. Đây không phải cách ngụy trang một định dạng thành định dạng khác hay bỏ qua quy ước đặt tên thông thường. Gợi ý ảnh hưởng cách tài nguyên được nạp, không thay đổi precedence của nó so với các nguồn cấu hình khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configtree-imports">Cây cấu hình và đầu vào kiểu một tệp cho mỗi thuộc tính</a>

<details>
<summary>Xem chi tiết</summary>

Cây cấu hình biểu diễn một thư mục trong đó mỗi tệp đóng góp một giá trị property. Mô hình này khớp với kiểu volume được mount phổ biến: nền tảng ghi một tệp cho mỗi giá trị, còn Boot đưa các tệp đó vào mô hình `Environment` thông thường.

Ví dụ:

```text
/etc/config/myapp/
  username
  password
```

với import:

```properties
spring.config.import=optional:configtree:/etc/config/
```

Boot có thể tạo `myapp.username` và `myapp.password`. Tên thư mục/tệp hình thành tên property; tên tệp có dấu chấm cũng ánh xạ tự nhiên sang key dạng chấm. Giá trị có thể bind thành `String` hoặc `byte[]` tùy nơi tiêu thụ.

`configtree:` là cầu nối của Boot từ tệp đã mount sang cấu hình. Nó không biến module này thành nơi sở hữu Kubernetes, Docker secret hay chính sách quản lý secret. Nền tảng quyết định dữ liệu được cấp phát ra sao; Boot quyết định dữ liệu đã mount đi vào `Environment` thế nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="import-failure-model">Chẩn đoán lỗi khi nhập Config Data</a>

<details>
<summary>Xem chi tiết</summary>

Khi chẩn đoán import, nên tách vấn đề thành bốn nhóm: **địa chỉ**, **khả dụng**, **định dạng** và **thứ tự**.

- Nếu Boot báo thiếu vị trí Config Data, kiểm tra đường dẫn và xem tài nguyên có thật sự được phép vắng không.
- Nếu import tương đối trỏ sai nơi, bắt đầu từ thư mục của tài liệu khai báo rồi lần từng bước trong chuỗi import.
- Nếu tài nguyên không có phần mở rộng không phân tích được, kiểm tra gợi ý phần mở rộng có đúng với nội dung thật không.
- Nếu giá trị đã được nạp nhưng không thắng, so sánh precedence giữa các import rồi mới so với các nguồn thuộc tính có precedence cao hơn.

Khi cần bằng chứng về quá trình tìm cấu hình, có thể bật logging tập trung cho `org.springframework.boot.context.config`. Điều quan trọng là phân biệt "tài nguyên chưa được nạp" với "tài nguyên đã nạp nhưng bị ghi đè" vì hai lớp lỗi này cần hướng điều tra khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)
