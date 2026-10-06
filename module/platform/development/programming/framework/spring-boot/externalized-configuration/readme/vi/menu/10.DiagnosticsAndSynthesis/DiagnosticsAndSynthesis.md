<a id="back-to-top"></a>

# Chẩn đoán và tổng hợp luồng cấu hình

## Menu
- [Luồng phân giải cấu hình từ đầu đến cuối](#resolution-pipeline-synthesis)
- [Chẩn đoán lỗi nạp cấu hình](#loading-failures)
- [Chẩn đoán lỗi profile và kích hoạt](#activation-failures)
- [Chẩn đoán lỗi binding và chuyển đổi kiểu](#binding-conversion-failures)
- [Chẩn đoán lỗi xác thực](#validation-failures)
- [Lần theo một khóa đến giá trị có hiệu lực](#effective-value-debugging)
- [Chọn tệp, biến môi trường, tham số dòng lệnh, profile, @Value hay @ConfigurationProperties](#configuration-decision-guide)
- [Bàn giao phạm vi trách nhiệm sang Testing, Application Runtime, Auto-Configuration, Cloud Config và quản lý secret](#externalized-configuration-handoffs)

## <a id="resolution-pipeline-synthesis">Luồng phân giải cấu hình từ đầu đến cuối</a>

<details>
<summary>Xem chi tiết</summary>

Có thể rút gọn toàn bộ module thành một luồng. Trước hết Boot khám phá các đầu vào cấu hình và Config Data. Profile và việc kích hoạt tài liệu quyết định tài liệu cấu hình nào thực sự tham gia. Sau đó Boot áp dụng thứ tự giữa các `PropertySource` và Config Data lên các nguồn đang tham gia để phân giải giá trị có hiệu lực của từng khóa. Cuối cùng bên tiêu thụ đọc giá trị đã phân giải trực tiếp hoặc bind vào đối tượng có kiểu, nơi chuyển đổi kiểu và xác thực có thể thất bại.

~~~text
nguồn + Config Data location/import
              ↓
       kích hoạt tài liệu
              ↓
      precedence PropertySource
              ↓
       Environment có hiệu lực
          ↙           ↘
 Environment/@Value   @ConfigurationProperties
                           ↓
                    conversion + validation
~~~

Khi chẩn đoán, hãy xác định giai đoạn trước khi thay đổi cấu hình. Một khóa không thể bind đúng nếu nguồn chưa từng được nạp; giá trị đúng vẫn có thể thất bại khi chuyển đổi kiểu; kiểu hợp lệ vẫn có thể vi phạm ràng buộc miền. Suy luận theo giai đoạn đáng tin cậy hơn thử ghi đè ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-failures">Chẩn đoán lỗi nạp cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi nạp xảy ra trước binding thông thường. Nguyên nhân phổ biến gồm spring.config.location bắt buộc nhưng không tồn tại, import không tùy chọn bị thiếu, vị trí tài nguyên sai, nội dung cấu hình không đọc được hoặc định dạng không được hỗ trợ.

Spring Boot thường làm quá trình khởi động thất bại bằng exception liên quan đến Config Data khi vị trí bắt buộc không thể nạp. Thêm tiền tố optional: thay đổi hợp đồng đó và chỉ nên dùng khi tài nguyên thực sự tùy chọn, không phải để che giấu lỗi.

Một chuỗi chẩn đoán hữu ích:

~~~text
Vị trí dự kiến có nằm trong tập tìm kiếm/import không?
→ Nó được phân giải là vị trí cố định hay tương đối đúng như dự kiến không?
→ Tài nguyên đó là bắt buộc hay tùy chọn?
→ Boot có đọc và phân tích tài nguyên được không?
~~~

Chỉ sau khi tài nguyên đã được nạp mới nên điều tra property của nó có thắng precedence hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="activation-failures">Chẩn đoán lỗi profile và kích hoạt</a>

<details>
<summary>Xem chi tiết</summary>

Tài liệu cấu hình có thể tồn tại và được phân tích đúng nhưng vẫn không hoạt động. Khi giá trị mong đợi bị thiếu, hãy kiểm tra việc kích hoạt profile tách biệt với bước nạp tệp.

Kiểm tra profile nào đang hoạt động, tệp/tài liệu theo profile mong đợi có khớp không, spring.config.activate.on-profile có nằm trong tài liệu hợp lệ không, và spring.profiles.active/include/group có được khai báo ở nơi Boot cho phép không. Với nhiều profile, nhớ hành vi giá trị sau cùng thắng tại mức nhóm vị trí liên quan.

Đừng “sửa” lỗi kích hoạt bằng cách sao chép cùng khóa vào nhiều tệp. Trước tiên hãy làm rõ mô hình kích hoạt; giá trị dự phòng trùng lặp có thể che giấu việc profile hoặc tài liệu sai đang tham gia.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-conversion-failures">Chẩn đoán lỗi binding và chuyển đổi kiểu</a>

<details>
<summary>Xem chi tiết</summary>

Sau khi khóa đã có trong Environment, @ConfigurationProperties vẫn phải ánh xạ tên khóa và chuyển giá trị sang thành phần đích. Lỗi binding có thể do không gian tên/hình dạng khóa không khớp mô hình Java, đường dẫn collection/map sai hoặc chuyển đổi kiểu không tạo được kiểu đích.

Hãy đọc cùng lúc tên property, giá trị bị từ chối, origin nếu có và kiểu đích. Ví dụ client.timeout=fast có thể phân giải hoàn toàn bình thường dưới dạng văn bản nhưng thất bại khi Boot cố tạo Duration.

~~~text
khóa không có trong Environment
→ vấn đề nạp nguồn, kích hoạt hoặc tên khóa

khóa có nhưng thành phần đích không được điền
→ vấn đề name / shape / binding

khóa có và có exception khi chuyển đổi kiểu
→ vấn đề kiểu đích / định dạng giá trị

khóa có nhưng giá trị hoặc nguồn có hiệu lực không như dự kiến
→ vấn đề thứ tự ưu tiên / thứ tự nguồn
~~~

Tách như vậy tránh trộn lẫn việc chẩn đoán precedence với việc chẩn đoán ánh xạ đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-failures">Chẩn đoán lỗi xác thực</a>

<details>
<summary>Xem chi tiết</summary>

Validation xảy ra sau binding thành công, vì vậy lỗi validation nghĩa là Boot đã tạo hoặc đang tạo mô hình cấu hình có kiểu nhưng giá trị kết quả vi phạm ràng buộc đã khai báo.

Báo cáo validation hữu ích phải dẫn ngược đến property cấu hình, giá trị bị từ chối và ràng buộc. Hãy sửa cấu hình hoặc hợp đồng theo ý định miền; đừng đơn giản xóa @NotBlank, @Positive hoặc ràng buộc tương tự chỉ để khởi động qua.

Với đối tượng lồng nhau, kiểm tra cascading validation đã được bật ở nơi cần thiết chưa. Nếu giá trị lồng nhau rõ ràng sai mà không bị phát hiện, vấn đề có thể nằm ở đồ thị validation chứ không phải quá trình phân giải property.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="effective-value-debugging">Lần theo một khóa đến giá trị có hiệu lực</a>

<details>
<summary>Xem chi tiết</summary>

Khi một giá trị gây bất ngờ, hãy chẩn đoán ngược từ khóa có hiệu lực thay vì đọc các tệp cấu hình theo thứ tự ngẫu nhiên.

1. Ghi khóa chuẩn.
2. Xác định giá trị ứng dụng thực sự quan sát.
3. Liệt kê mọi nguồn có thể định nghĩa khóa.
4. Kiểm tra tệp/tài liệu theo profile nào thực sự đang hoạt động và vì thế được tham gia.
5. Áp dụng thứ tự ưu tiên của PropertySource và Config Data cho các ứng viên đang tham gia.
6. Chỉ kiểm tra placeholder hoặc chuyển đổi kiểu trong binding sau khi biết nguồn thắng.

Endpoint env và configprops của Actuator có thể cung cấp bằng chứng vận hành khi Actuator được bật có chủ ý, nhưng module Actuator sở hữu bề mặt phục vụ môi trường sản xuất đó. Module này sở hữu mô hình suy luận để diễn giải bằng chứng.

Không được làm lộ cấu hình nhạy cảm chỉ để chẩn đoán. Việc che dữ liệu secret và kiểm soát truy cập vận hành thuộc trách nhiệm của security/operations.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-decision-guide">Chọn tệp, biến môi trường, tham số dòng lệnh, profile, @Value hay @ConfigurationProperties</a>

<details>
<summary>Xem chi tiết</summary>

Các cơ chế cấu hình giải quyết những vấn đề khác nhau.

| Tình huống | Lựa chọn khởi đầu phù hợp |
| --- | --- |
| Giá trị mặc định của ứng dụng được quản lý phiên bản | application.properties/yaml đóng gói |
| Giá trị riêng cho môi trường triển khai | tệp bên ngoài hoặc biến môi trường |
| Ghi đè cho một lần chạy | tùy chọn dòng lệnh |
| Chọn một biến thể nhất quán của các tài liệu cấu hình | profile |
| Một giá trị đơn lẻ cục bộ trong thành phần | @Value |
| Nhóm thiết lập ứng dụng có cấu trúc | @ConfigurationProperties |
| Cấu hình mount theo kiểu mỗi khóa một tệp | configtree: import |

Các lựa chọn không loại trừ nhau: đối tượng @ConfigurationProperties có thể nhận giá trị mà nguồn thắng là biến môi trường hoặc tệp bên ngoài. Hãy tách câu hỏi **giá trị đến từ đâu** khỏi câu hỏi **mã ứng dụng tiêu thụ giá trị như thế nào**.

Nên giữ đường ghi đè dễ dự đoán và có tài liệu. Quá nhiều nguồn thay thế lẫn nhau làm hành vi ở môi trường sản xuất khó giải thích dù Boot hỗ trợ về kỹ thuật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="externalized-configuration-handoffs">Bàn giao phạm vi trách nhiệm sang Testing, Application Runtime, Auto-Configuration, Cloud Config và quản lý secret</a>

<details>
<summary>Xem chi tiết</summary>

Externalized Configuration nằm sát nhiều ranh giới. Module này sở hữu các nguồn đầu vào của Boot, Config Data, precedence, profile, cách tiêu thụ, binding, tích hợp validation và metadata. Nó chủ ý dừng lại khi câu hỏi thuộc module lân cận chịu trách nhiệm.

~~~text
property override chỉ dành cho test
→ Spring Boot Testing

hành vi có điều kiện theo property
→ Spring Boot Auto-Configuration

cấu hình logging/task/availability lúc chạy
→ Spring Boot Application Runtime

endpoint env/configprops phục vụ vận hành
→ Spring Boot Actuator

cấu hình tập trung từ xa
→ Spring Cloud Config

vòng đời secret, lưu trữ, rotation, kiểm soát truy cập
→ security / infrastructure chịu trách nhiệm
~~~

Quy tắc bàn giao tránh lỗi chương trình học phổ biến: nhắc tới công nghệ lân cận không có nghĩa module này sở hữu phần nội bộ của nó. Người học cần rời module với hai khả năng: giải thích Boot phân giải cấu hình ứng dụng như thế nào và nhận ra khi câu hỏi đã vượt sang module khác chịu trách nhiệm.

</details>

- [Quay lại đầu trang](#back-to-top)
