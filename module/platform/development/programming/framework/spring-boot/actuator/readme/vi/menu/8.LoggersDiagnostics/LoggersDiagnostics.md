<a id="back-to-top"></a>

# Loggers và các endpoint chẩn đoán runtime

## Menu
- [Loggers endpoint cung cấp thông tin gì?](#loggers-endpoint)
- [Logger level được cấu hình và mức có hiệu lực khác nhau thế nào?](#configured-effective-log-levels)
- [Có thể thay đổi logger level trong lúc chạy như thế nào?](#runtime-log-level-changes)
- [Thread dump endpoint cung cấp dữ liệu gì?](#threaddump-endpoint)
- [Heap dump endpoint cung cấp dữ liệu gì?](#heapdump-endpoint)
- [Việc Actuator cung cấp dữ liệu kết thúc ở đâu và phân tích JVM/logging bắt đầu ở đâu?](#diagnostic-analysis-boundary)
- [Vì sao các endpoint chẩn đoán nhạy cảm tạo rủi ro khi được công khai?](#diagnostic-exposure-risk)

## <a id="loggers-endpoint">Loggers endpoint cung cấp thông tin gì?</a>

<details>
<summary>Xem chi tiết</summary>

Loggers endpoint expose cấu hình logging runtime mà `LoggingSystem` của ứng dụng đang biết. Nó có thể liệt kê tên logger/group, kiểm tra một logger/group và trả các level cần thiết để hiểu cấu hình logging hiện tại được phân giải ra sao.

Endpoint này tiêu thụ tích hợp logging của Boot chứ không thay thế nó. `application-runtime` sở hữu cách Boot khởi tạo `LoggingSystem` và cấu hình logging lúc khởi động; Actuator chỉ thêm operation quản trị trên hệ thống logging đang chạy.

Tên logger có thể tiết lộ cấu trúc package/component, còn write operation có thể làm lượng log tăng mạnh, vì vậy endpoint nên được xem là bề mặt điều khiển vận hành chứ không phải API công khai.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Loggers Endpoint](https://docs.spring.io/spring-boot/3.3/api/rest/actuator/loggers.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configured-effective-log-levels">Logger level được cấu hình và mức có hiệu lực khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Một logger có thể có level cấu hình riêng hoặc kế thừa từ logger cha. Vì vậy loggers endpoint phân biệt `configuredLevel` và `effectiveLevel`. `configuredLevel` cho biết chính logger đó có thiết lập riêng hay không; `effectiveLevel` cho biết level thực sự đang điều khiển logging sau khi cơ chế kế thừa được phân giải.

Ví dụ package logger không có level riêng trong khi root là `INFO`: configured level của package không có giá trị nhưng effective level là `INFO`. Nếu đặt `DEBUG` cho package đó, logger có cấu hình riêng và hành vi thay đổi mà root không cần đổi.

Phân biệt này tránh lỗi chẩn đoán phổ biến: nhìn effective level rồi giả định nó được cấu hình trực tiếp tại logger. Khi truy nguyên quyết định logging ở runtime, nên nhìn cả hai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-log-level-changes">Có thể thay đổi logger level trong lúc chạy như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Khi expose qua web, loggers endpoint có write operation cho phép thay configured level trong lúc ứng dụng chạy. Người vận hành có thể nhắm tới một logger/group cụ thể và tạm đặt `DEBUG` để điều tra; khi bỏ level riêng, logger quay về cơ chế kế thừa bình thường.

Thay đổi ở runtime hữu ích vì không cần khởi động lại nhưng đây là trạng thái vận hành tạm thời. Sau khi khởi động lại, cấu hình logging thường được dựng lại từ các nguồn cấu hình chuẩn, nên thay đổi trong sự cố không nên bị hiểu là cập nhật cấu hình bền vững.

Nên chọn phạm vi logger hẹp và khôi phục sau khi xong. `DEBUG`/`TRACE` quá rộng có thể tạo lượng I/O lớn, tiết lộ dữ liệu nhạy cảm và làm thay đổi hiệu năng của chính ứng dụng đang được debug.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="threaddump-endpoint">Thread dump endpoint cung cấp dữ liệu gì?</a>

<details>
<summary>Xem chi tiết</summary>

Threaddump endpoint chụp snapshot thông tin thread JVM và đưa nó ra qua bề mặt quản trị Actuator. REST API của Boot 3.3 có thể trả JSON có cấu trúc gồm định danh thread, trạng thái, thông tin liên quan đến lock và stack frame.

Endpoint trả lời “hãy lấy thread dump từ tiến trình đang chạy”, không phân tích dump. Việc xác định deadlock, hot lock, trạng thái chờ bình thường hay thread dùng nhiều CPU cần kiến thức JVM/concurrency nằm ngoài Actuator.

Thread dump vẫn có thể tiết lộ tên class, đường đi của mã, tên thread và hành vi runtime nên cần hạn chế exposure. Trong sự cố, nên thu đủ snapshot phục vụ phân tích thay vì gọi endpoint liên tục không mục đích.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Thread Dump REST API](https://docs.spring.io/spring-boot/3.3/api/rest/actuator/threaddump.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="heapdump-endpoint">Heap dump endpoint cung cấp dữ liệu gì?</a>

<details>
<summary>Xem chi tiết</summary>

Heapdump web endpoint trả heap dump của JVM đang chạy dưới dạng binary. Trên HotSpot file là HPROF; trên OpenJ9 là PHD. Heap dump có thể rất lớn và quá trình tạo dump có chi phí runtime, nên đây là công cụ chẩn đoán khi có sự cố chứ không phải endpoint để polling.

Heap dump có thể chứa object graph, chuỗi, dữ liệu cache, credential, nội dung request và dữ liệu nhạy cảm khác đang được giữ trong memory. Artifact tải về cần được bảo vệ ít nhất tương đương dữ liệu production.

Actuator chỉ sở hữu việc tạo/trả dump qua endpoint quản trị của Boot. Phân tích object retention, dominator tree, điều tra memory leak, suy luận GC và công cụ phân tích heap thuộc chẩn đoán JVM/runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-analysis-boundary">Việc Actuator cung cấp dữ liệu kết thúc ở đâu và phân tích JVM/logging bắt đầu ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Loggers, thread dump và heap dump cùng thể hiện một ranh giới lặp lại: Actuator endpoint cung cấp/điều khiển trạng thái chẩn đoán, còn miền chuyên môn giải thích trạng thái đó có ý nghĩa gì. Actuator cho biết effective logger level; phân tích logging mới đánh giá log event có giải thích sự cố hay không.

Actuator trả snapshot thread; công cụ Java concurrency/JVM mới xác định trạng thái chờ bình thường hay bất thường. Actuator trả nội dung heap; công cụ phân tích memory mới tìm object nào giữ memory và vì sao.

Giữ ranh giới này giúp module không biến thành khóa học logging hoặc hiệu năng JVM. Người học cần biết lấy bằng chứng an toàn, hiểu ngữ nghĩa quản trị rồi bàn giao bằng chứng cho đúng miền chuyên môn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-exposure-risk">Vì sao các endpoint chẩn đoán nhạy cảm tạo rủi ro khi được công khai?</a>

<details>
<summary>Xem chi tiết</summary>

Endpoint chẩn đoán tập trung nhiều thông tin đặc quyền. Điều khiển logger có thể tăng độ chi tiết và làm lộ dữ liệu; thread dump expose đường thực thi và trạng thái đồng bộ; heap dump có thể chứa secret/dữ liệu người dùng. Endpoint như env, configprops, mappings hay beans cũng có thể tiết lộ cấu trúc nội bộ.

Không nên expose rộng chỉ vì đã có authentication. Cần kết hợp danh sách exposure tối thiểu, vị trí mạng và authorization phù hợp với đối tượng vận hành. Một số dump endpoint có thể chỉ nên truy cập được qua luồng xử lý sự cố được kiểm soát.

Ngoài tính bí mật còn có tác động tài nguyên. Tải heap dump lớn, tạo dump lặp lại hoặc bật `TRACE` rộng có thể gây áp lực lên chính ứng dụng đang được chẩn đoán.

</details>

- [Quay lại đầu trang](#back-to-top)
