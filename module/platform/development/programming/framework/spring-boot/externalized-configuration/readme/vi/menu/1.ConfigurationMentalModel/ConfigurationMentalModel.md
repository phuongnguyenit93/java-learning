<a id="back-to-top"></a>

# Mô hình tư duy về Externalized Configuration

## Menu
- [Externalized Configuration là gì và vì sao cần?](#externalized-configuration-purpose)
- [Giá trị cấu hình và mã ứng dụng khác nhau thế nào?](#configuration-vs-application-code)
- [Environment như góc nhìn đã phân giải của cấu hình](#environment-resolved-view)
- [Các nhóm nguồn cấu hình chính](#configuration-source-categories)
- [Từ đầu vào cấu hình đến giá trị có hiệu lực khi chạy](#configuration-resolution-pipeline)
- [Phạm vi trách nhiệm và điểm bàn giao của module](#module-ownership-boundary)

## <a id="externalized-configuration-purpose">Externalized Configuration là gì và vì sao cần?</a>

<details>
<summary>Xem chi tiết</summary>

Externalized Configuration là cách tách các giá trị phụ thuộc môi trường ra khỏi logic ứng dụng để cùng một bản ứng dụng đã biên dịch có thể chạy với nhiều bộ thiết lập khác nhau. Một dịch vụ có thể cần URL cơ sở dữ liệu, ngưỡng tính năng hoặc cổng HTTP khác nhau giữa môi trường phát triển và môi trường sản xuất, trong khi mã Java thực hiện chức năng của dịch vụ vẫn giữ nguyên.

Spring Boot đưa các đầu vào đó vào mô hình property cấu hình để mã ứng dụng có thể sử dụng. Mô hình tư duy quan trọng không phải là "một tệp cấu hình", mà là **nhiều nguồn đầu vào cùng tạo nên một góc nhìn cấu hình đã được phân giải**. Tệp chỉ là một nguồn; biến môi trường, JVM system property, tham số dòng lệnh và các nguồn thuộc tính khác cũng có thể tham gia.

Việc tách này giúp gói ứng dụng dễ tái sử dụng và triển khai linh hoạt hơn, nhưng đồng thời tạo ra câu hỏi mới: nếu nhiều nguồn cùng khai báo một key thì giá trị nào thắng? Các chương tiếp theo sẽ xây câu trả lời qua precedence, Config Data, profile và binding.

```text
cùng một gói ứng dụng
        +
đầu vào theo từng môi trường
        ↓
cấu hình có hiệu lực
        ↓
hành vi lúc chạy
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-vs-application-code">Giá trị cấu hình và mã ứng dụng khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Cấu hình phù hợp nhất với những giá trị có thể thay đổi hợp lý giữa các môi trường mà không làm thay đổi logic nghiệp vụ của ứng dụng: endpoint, timeout, giới hạn, tham chiếu thông tin xác thực, cờ tính năng hoặc tên phụ thuộc môi trường triển khai. Mã ứng dụng vẫn phải sở hữu các quy tắc diễn giải những giá trị đó và quyết định chương trình sẽ làm gì.

Ví dụ, `payment.retry.max-attempts=4` là cấu hình. Thuật toán retry, loại exception nào được retry và chuyện gì xảy ra sau lần thất bại thứ tư là hành vi ứng dụng. Đưa mọi quyết định sang property sẽ làm chương trình khó hiểu và khó kiểm thử; ghi cứng mọi giá trị triển khai trong Java lại làm gói ứng dụng khó tái sử dụng.

Một câu hỏi thực tế là: **đội vận hành có thể đổi giá trị này giữa các môi trường mà năng lực cốt lõi của ứng dụng vẫn không đổi không?** Nếu có, đó thường là ứng viên tốt cho cấu hình. Nếu thay đổi giá trị đồng nghĩa thay đổi quy tắc nghiệp vụ hoặc cấu trúc chương trình, nó nhiều khả năng nên nằm trong code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="environment-resolved-view">Environment như góc nhìn đã phân giải của cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

`Environment` của Spring là góc nhìn mà ứng dụng sử dụng sau khi các nguồn thuộc tính đã được tập hợp và sắp thứ tự. Thay vì đọc trực tiếp từng nguồn, mã ứng dụng có thể hỏi `Environment` theo key và nhận giá trị có hiệu lực theo precedence.

```java
String region = environment.getProperty("app.region");
```

`Environment` không có nghĩa là mọi giá trị đến từ cùng một nơi. `app.region` có thể bắt nguồn từ Config Data đóng gói trong ứng dụng, tệp bên ngoài, biến môi trường hệ điều hành hoặc tham số dòng lệnh. Điểm này rất quan trọng khi chẩn đoán: đọc giá trị thì đơn giản, nhưng giải thích **vì sao giá trị đó thắng** đòi hỏi hiểu các nguồn thuộc tính đã tham gia.

Module này tập trung vào cách Spring Boot nạp và phân giải cấu hình xung quanh `Environment`. Phần triển khai nội bộ chi tiết của Spring Framework về `Environment` và `PropertySource` nằm ngoài phạm vi sở hữu của module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-source-categories">Các nhóm nguồn cấu hình chính</a>

<details>
<summary>Xem chi tiết</summary>

Các nguồn cấu hình thường gặp trong Spring Boot có thể được chia thành một vài nhóm thực tế:

- **giá trị mặc định của ứng dụng** được cung cấp bằng code;
- **Config Data**, như `application.properties`, `application.yaml`, tệp theo profile và tài nguyên được import;
- **đầu vào từ máy chủ hoặc lúc chạy**, như biến môi trường hệ điều hành và JVM system property;
- **đầu vào nội tuyến hoặc lúc khởi chạy**, như `SPRING_APPLICATION_JSON` và tham số dòng lệnh;
- **nguồn từ framework/container**, như JNDI hoặc tham số khởi tạo servlet khi môi trường chạy sử dụng chúng;
- **nguồn ghi đè chỉ dành cho kiểm thử**, có tồn tại nhưng phần chi tiết thuộc module Testing.

Nhóm nguồn trả lời câu hỏi *giá trị có thể đến từ đâu*. Precedence trả lời câu hỏi *ứng viên nào có hiệu lực khi nhiều nguồn cùng khai báo một key*. Tách hai câu hỏi này giúp tránh ngộ nhận phổ biến rằng tệp bên ngoài luôn thắng mọi nguồn khác chỉ vì nó nằm ngoài jar.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-resolution-pipeline">Từ đầu vào cấu hình đến giá trị có hiệu lực khi chạy</a>

<details>
<summary>Xem chi tiết</summary>

Khi Boot khởi động, có thể hình dung quá trình phân giải cấu hình như một luồng thay vì một lần đọc tệp đơn lẻ:

```text
phát hiện các nguồn cấu hình có thể dùng
        ↓
nạp Config Data và các nguồn thuộc tính khác
        ↓
kích hoạt các tài liệu theo profile phù hợp
        ↓
áp dụng thứ tự ưu tiên cho các nguồn/tài liệu thực sự tham gia
        ↓
phân giải giá trị có hiệu lực cho key được yêu cầu
        ↓
đọc trực tiếp hoặc bind vào đối tượng cấu hình có kiểu
```

Ví dụ, `server.port` có thể là `8080` trong tệp đóng gói, `8081` trong tệp bên ngoài và `9090` từ `--server.port=9090`. Ứng dụng cuối cùng quan sát một giá trị có hiệu lực vì các nguồn thuộc tính có thứ tự. Cùng mô hình đó áp dụng cho các key tự định nghĩa như `orders.timeout`.

Luồng này cũng là danh sách kiểm tra khi chẩn đoán. Khi giá trị không như mong đợi, trước hết kiểm tra nguồn dự kiến có được nạp không, tiếp theo kiểm tra profile/tài liệu dự kiến có đang hoạt động không, rồi mới so sánh các giá trị ứng viên theo thứ tự ưu tiên; chỉ sau đó mới đi sâu vào cách đọc hoặc binding giá trị.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-ownership-boundary">Phạm vi trách nhiệm và điểm bàn giao của module</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu phần Externalized Configuration của Spring Boot: cách nạp nguồn, precedence, vị trí và import Config Data, profile, cách đọc giá trị đã phân giải, `@ConfigurationProperties`, relaxed/complex binding, tích hợp xác thực và metadata cấu hình.

Một số chủ đề lân cận được bàn giao rõ ràng:

- Spring Framework sở hữu phần sâu hơn về `Environment`, chuyển đổi kiểu, bean factory và cơ chế xác thực.
- Spring Boot Testing sở hữu các cơ chế ghi đè dành riêng cho test và hành vi của ngữ cảnh kiểm thử.
- Spring Boot Auto-Configuration dùng giá trị cấu hình để đưa ra quyết định có điều kiện, nhưng việc đánh giá điều kiện thuộc module đó.
- Spring Cloud Config và các sản phẩm quản lý secret sở hữu cấu hình từ xa/phân tán và quy trình lưu trữ secret.
- Các module Runtime và Actuator có thể tiêu thụ giá trị đã phân giải hoặc cung cấp bề mặt chẩn đoán, nhưng không định nghĩa lại precedence của module này.

Giữ ranh giới này rõ giúp toàn bộ module tập trung vào một câu hỏi xuyên suốt: **Spring Boot biến các đầu vào cấu hình thành giá trị đáng tin cậy cho ứng dụng sử dụng như thế nào?**

</details>

- [Quay lại đầu trang](#back-to-top)
