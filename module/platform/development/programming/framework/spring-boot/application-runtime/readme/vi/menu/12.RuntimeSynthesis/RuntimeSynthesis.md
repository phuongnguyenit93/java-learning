<a id="back-to-top"></a>

# Tổng hợp mô hình Spring Boot runtime

## Menu
- [Luồng Spring Boot runtime từ đầu đến cuối kết nối với nhau như thế nào?](#runtime-end-to-end-flow)
- [Chọn giữa events, runners, executor do Boot quản lý và tùy biến application như thế nào?](#runtime-hook-selection)
- [Availability, tác vụ nền, logging, SSL và dịch vụ lúc phát triển nằm ở đâu trong mô hình runtime?](#runtime-state-and-services)
- [Xác định vấn đề runtime thuộc lớp nào trước khi chọn cách sửa như thế nào?](#runtime-problem-classification)
- [Module lân cận nào sở hữu lớp chi tiết tiếp theo?](#runtime-module-handoffs)

## <a id="runtime-end-to-end-flow">Luồng Spring Boot runtime từ đầu đến cuối kết nối với nhau như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Toàn module có thể ghép thành một dòng thời gian. `SpringApplication` chuẩn bị đầu vào runtime/context, phát lifecycle events khi trạng thái dần hoàn chỉnh, refresh context, đưa liveness sang `CORRECT`, chạy các startup runner theo thứ tự rồi chuyển readiness sang `ACCEPTING_TRAFFIC`. Sau startup, executor/scheduler, tích hợp logging, availability, SSL bundles và các dịch vụ phát triển hỗ trợ trạng thái runtime ổn định. Lỗi và shutdown là các nhánh rời khỏi trạng thái đó.

```text
inputs -> context -> events -> LIVENESS -> runners -> READINESS
   |          |                            |
 logging   auto-config              executors / SSL / dev services
   |                                       |
phân tích lỗi <--- runtime ---> thay đổi availability
                     |
                 shutdown / exit
```

Giá trị của sơ đồ là chẩn đoán: xác định giai đoạn và bên sở hữu trước khi chọn API/property để sửa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-hook-selection">Chọn giữa events, runners, executor do Boot quản lý và tùy biến application như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Nhiều cơ chế đều có thể "chạy code" nhưng mục đích khác nhau. Event listener phản ứng với chuyển trạng thái lifecycle. Runner thực hiện công việc startup có giới hạn sau context refresh và trước readiness. Executor/scheduler được quản lý chạy công việc nền bằng hạ tầng được quản lý. Tùy biến `SpringApplication` thay đổi chính cách Boot khởi động/cấu hình ứng dụng.

Hãy trả lời hai câu: *công việc này cần những gì đã tồn tại?* và *readiness có phải chờ nó không?* Listener cần repository là quá sớm nếu nghe pre-context event. Vòng lặp vô hạn trong runner làm startup không thể xong. Executor tùy biến cũng không phải lời giải cho yêu cầu chỉ cần một Boot property.

Hook đúng ngữ nghĩa giúp thời điểm lỗi, thiết lập kiểm thử và hành vi production dễ giải thích hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-state-and-services">Availability, tác vụ nền, logging, SSL và dịch vụ lúc phát triển nằm ở đâu trong mô hình runtime?</a>

<details>
<summary>Xem chi tiết</summary>

Trạng thái runtime và dịch vụ runtime liên quan nhưng không giống nhau. Availability cho biết trạng thái liveness/readiness của tiến trình. Executors/schedulers cung cấp nơi chạy công việc nền. Logging làm hành vi startup/runtime quan sát được. SSL bundles cung cấp security material dùng lại cho thành phần được hỗ trợ. Tích hợp Docker Compose phối hợp các dependency bên ngoài cục bộ trong môi trường phát triển.

Không khả năng nào một mình định nghĩa tính đúng đắn của ứng dụng. Ứng dụng đã sẵn sàng vẫn có thể có executor bị bão hòa; executor khỏe không sửa được TLS trust sai; database container đang chạy không đồng nghĩa liveness của ứng dụng đã đúng.

Khi sự cố xảy ra, tránh đổi đồng thời mọi thiết lập. Hãy xác định trạng thái hoặc tích hợp đang hỏng trước, rồi dùng chương sở hữu lớp đó để điều tra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-problem-classification">Xác định vấn đề runtime thuộc lớp nào trước khi chọn cách sửa như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Có thể bắt đầu điều tra runtime bằng bảng phân loại đơn giản:

| Triệu chứng | Bên sở hữu đầu tiên nên kiểm tra |
| --- | --- |
| Lỗi trước khi context được tạo | đầu vào SpringApplication / bootstrap sớm |
| Lỗi khi tạo bean | Spring context + configuration/auto-configuration |
| Ứng dụng đã đạt liveness nhưng chưa đạt readiness | runners / chuyển trạng thái readiness |
| Công việc nền bị queue/starve | hạ tầng task của Boot, rồi ngữ nghĩa concurrency |
| Log sớm bỏ qua cấu hình mong muốn | thời điểm khởi tạo logging của Boot |
| Không tìm thấy SSL material có tên | cấu hình/danh mục SSL bundle |
| Service connection cục bộ sai | tích hợp Docker Compose service connection |

Sau khi phân loại, hãy theo exception, chuyển trạng thái hoặc bằng chứng cấu hình. Đừng gom mọi triệu chứng xuất hiện "lúc startup" thành cùng một loại lỗi Boot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-module-handoffs">Module lân cận nào sở hữu lớp chi tiết tiếp theo?</a>

<details>
<summary>Xem chi tiết</summary>

Lớp tiếp theo phụ thuộc câu hỏi. Sang `web-runtime` cho embedded server, server TLS, proxy và graceful HTTP shutdown. Sang Actuator cho health/diagnostic endpoints ở production. Sang `testing` cho Boot test bootstrap và Testcontainers service connection. Sang `native-image` khi ràng buộc AOT/native thay đổi mô hình JVM thông thường.

Quay lại `externalized-configuration` cho property source, thứ tự ưu tiên, profile/binding; quay lại `auto-configuration` cho condition, back-off, ordering và custom auto-config. Dùng Spring Framework concurrency cho `@Async`/`@Scheduled`, Java concurrency cho tính đúng đắn của thread và cơ chế virtual thread.

Cuối cùng, bàn giao vận hành logging, TLS/PKI và cơ chế Docker cho các bên sở hữu hạ tầng/security. Biết điểm bàn giao cũng là một phần của việc hiểu Spring Boot runtime vì Boot tích hợp nhiều công nghệ mà nó không định nghĩa lại.

</details>

- [Quay lại đầu trang](#back-to-top)
