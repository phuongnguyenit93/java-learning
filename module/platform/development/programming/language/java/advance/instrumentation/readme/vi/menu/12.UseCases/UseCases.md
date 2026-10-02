<a id="back-to-top"></a>

# Các trường hợp sử dụng Instrumentation

## Menu
- [Profiling bằng bytecode instrumentation](#profiling-use-case)
- [Đo độ bao phủ mã](#coverage-use-case)
- [Monitoring và event logging](#monitoring-event-logging-use-case)
- [Tracing và chèn probe](#tracing-probe-use-case)
- [Framework, APM và agent-based tooling](#framework-agent-use-case)
- [Thu thập dữ liệu và ranh giới với Runtime Diagnostics](#diagnostic-boundary)

## <a id="profiling-use-case">Profiling bằng bytecode instrumentation</a>

<details>
<summary>Xem chi tiết</summary>

Profiler cần đo hành vi runtime mà không yêu cầu ứng dụng tự thêm code đo thời gian vào từng method.

Một agent có thể:

~~~text
khớp OrderService.placeOrder
→ chèn timestamp ở điểm vào method
→ ghi thời lượng ở đường thoát bình thường/exception
→ gửi sample/counter tới runtime của profiler
~~~

Điểm cần cân bằng là overhead. Instrument mọi method bằng bộ đếm thời gian độ phân giải cao có thể làm thay đổi chính hành vi ta muốn đo.

Profiler thực tế thường:

- chọn một tập con method/package;
- sampling hoặc tổng hợp thay vì ghi từng event chi tiết;
- tránh cấp phát trên đường thực thi nóng (hot path);
- dùng helper runtime tối ưu;
- cho phép bật/tắt probe động.

Instrumentation giải quyết phần **chèn probe**; cách profiler thống kê, trực quan hóa và phân tích hiệu năng thuộc một miền rộng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="coverage-use-case">Đo độ bao phủ mã</a>

<details>
<summary>Xem chi tiết</summary>

Coverage agent chèn marker/counter để biết code nào đã thực thi.

Ví dụ khái niệm:

~~~java
if (condition) {
    // probe A
    process();
} else {
    // probe B
    fallback();
}
~~~

Transformer có thể đặt probe tại:

- method entry;
- basic block;
- branch;
- điểm ánh xạ dòng.

Sau lần chạy kiểm thử, helper runtime tổng hợp probe ID thành báo cáo coverage.

Khó khăn không chỉ là chèn counter. Agent phải giữ:

- line number/debug attribute đủ chính xác;
- luồng điều khiển hợp lệ;
- các đường thoát bằng exception;
- chính sách cho code generated/synthetic;
- tính duy nhất của vùng lưu probe theo class loader.

Coverage là ví dụ tốt cho “instrumentation bổ sung”: kết quả nghiệp vụ không nên đổi; chỉ thêm dữ liệu quan sát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-event-logging-use-case">Monitoring và event logging</a>

<details>
<summary>Xem chi tiết</summary>

Monitoring/APM agent thường instrument các ranh giới framework như:

~~~text
HTTP handler
client cơ sở dữ liệu
consumer nhận message
task định kỳ
thao tác nghiệp vụ đã chọn
~~~

Probe có thể ghi:

- thời điểm bắt đầu/kết thúc;
- trạng thái/lỗi;
- nhóm endpoint/query;
- metadata tương quan request;
- counter/histogram.

Event logging cũng có thể chèn hook ở những điểm ứng dụng chưa phát event.

Điểm thiết kế quan trọng là **cardinality và quyền riêng tư**. Instrumentation có thể nhìn tham số/giá trị trả về nhưng không có nghĩa nên thu thập tất cả. Agent cần chính sách tránh bí mật/PII và tránh label có cardinality cao.

Monitoring agent tốt làm ứng dụng dễ quan sát hơn với overhead và ranh giới có kiểm soát, không biến mọi method thành một câu lệnh log.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tracing-probe-use-case">Tracing và chèn probe</a>

<details>
<summary>Xem chi tiết</summary>

Tracing agent thường chèn probe ở các ranh giới tạo hoặc truyền span:

~~~text
HTTP đi vào
→ bắt đầu server span

HTTP / DB / messaging đi ra
→ child span + truyền ngữ cảnh

method kết thúc/lỗi
→ kết thúc span
~~~

Instrumentation giúp tracing thư viện tích hợp với framework mà ứng dụng không phải gọi tracing API trực tiếp.

Nhưng việc truyền ngữ cảnh thường liên quan thread-local/object ngữ cảnh, callback bất đồng bộ hoặc luồng reactive. Transformer chỉ chèn điểm hook; ngữ nghĩa truyền ngữ cảnh thuộc thư viện tracing/runtime.

Một lỗi phổ biến là instrument cả ranh giới mức thấp lẫn mức cao rồi tạo span trùng. Việc chọn mục tiêu cần hiểu các lớp của framework và chọn một ranh giới ngữ nghĩa ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="framework-agent-use-case">Framework, APM và agent-based tooling</a>

<details>
<summary>Xem chi tiết</summary>

Nhiều framework/công cụ dùng Java Agent làm điểm khởi tạo cho hành vi cross-cutting ở runtime:

- APM/observability agent;
- công cụ test coverage;
- công cụ mock/profiling runtime;
- hook quét/bảo vệ bảo mật;
- chẩn đoán ORM/framework.

Mẫu chung:

~~~text
Java Agent
→ phát hiện phiên bản runtime/framework
→ chọn các module instrumentation
→ cài đặt transformer
→ chèn lời gọi tới agent runtime/helper
→ thu thập/điều khiển hành vi
~~~

Điều này giải thích vì sao agent dùng trong môi trường production thường có nhiều lớp hơn ví dụ nhỏ:

- matcher registry;
- quy tắc tương thích phiên bản;
- chèn helper;
- cấu hình;
- chuỗi xử lý telemetry;
- vô hiệu hóa/rollback an toàn.

Module này tập trung vào nền tảng Java Instrumentation để người học hiểu các hệ thống đó, không xây lại phần nội bộ của từng framework/vendor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnostic-boundary">Thu thập dữ liệu và ranh giới với Runtime Diagnostics</a>

<details>
<summary>Xem chi tiết</summary>

Instrumentation và Runtime Diagnostics có điểm giao nhau nhưng quyền sở hữu nội dung khác:

~~~text
Instrumentation
→ "làm sao chèn probe để thu dữ liệu?"

Runtime Diagnostics
→ "dữ liệu/thread dump/JFR/bằng chứng heap nói gì về sự cố?"
~~~

Ví dụ agent chèn timing probe và ghi p99 latency tăng. Instrumentation giải thích:

- probe được chèn vào đâu;
- class nào được biến đổi;
- overhead của probe;
- cấu hình runtime/retransform.

Runtime Diagnostics mới tiếp tục:

- latency tăng vì CPU, lock, GC hay I/O phía sau?
- bằng chứng thread/JFR nào xác nhận giả thuyết?

Ranh giới này tránh biến module thành khóa observability tổng hợp, đồng thời vẫn cho người học hiểu vì sao instrumentation là một cơ chế thu thập dữ liệu mạnh.

</details>

- [Quay lại đầu trang](#back-to-top)
