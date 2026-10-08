<a id="back-to-top"></a>

# Graceful shutdown

## Menu
- [Hai chế độ shutdown của server: immediate và graceful](#server-shutdown-modes)
- [Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?](#graceful-shutdown-lifecycle)
- [Cấu hình timeout cho giai đoạn shutdown](#shutdown-phase-timeout)
- [Các server được hỗ trợ ngừng nhận request mới như thế nào?](#server-specific-shutdown-behavior)
- [Khoảng thời gian gia hạn cho request đang xử lý](#in-flight-request-grace-period)
- [Bàn giao sang application-runtime và trạng thái sẵn sàng](#shutdown-runtime-handoff)

## <a id="server-shutdown-modes">Hai chế độ shutdown của server: immediate và graceful</a>

<details>
<summary>Xem chi tiết</summary>
Boot 3.3 hỗ trợ web-server shutdown kiểu `immediate` và `graceful`. Mặc định của dòng 3.3 là `immediate`. Muốn có khoảng thời gian gia hạn phải bật rõ:

```properties
server.shutdown=graceful
```

Graceful shutdown được hỗ trợ trên bốn họ embedded server của Boot 3.3 và cho cả ứng dụng Servlet lẫn Reactive. Nó thay cách server từ chối công việc mới trong khi context ứng dụng đang đóng.

### Tài liệu tham khảo

- [Spring Boot 3.3 Reference — Graceful Shutdown](https://docs.spring.io/spring-boot/3.3/reference/web/graceful-shutdown.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="graceful-shutdown-lifecycle">Graceful shutdown nằm ở đâu trong lúc ApplicationContext đóng?</a>

<details>
<summary>Xem chi tiết</summary>
Graceful server shutdown diễn ra như một phần của việc đóng `ApplicationContext`. Boot thực hiện nó ở giai đoạn sớm nhất khi dừng các `SmartLifecycle` bean, nhờ đó điểm vào web bắt đầu từ chối công việc mới trong khi phần còn lại của ứng dụng tiếp tục shutdown có điều phối.

Đây là lý do graceful shutdown thuộc web-runtime nhưng có điểm bàn giao sang application-runtime. Server tham gia cùng quá trình đóng context chứ không chạy một tiến trình shutdown độc lập bên ngoài Spring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shutdown-phase-timeout">Cấu hình timeout cho giai đoạn shutdown</a>

<details>
<summary>Xem chi tiết</summary>
`spring.lifecycle.timeout-per-shutdown-phase` điều khiển ngân sách thời gian của một giai đoạn shutdown. Trong graceful web-server shutdown, ngân sách đó là khoảng thời gian gia hạn để các request đang xử lý có cơ hội hoàn tất.

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```

Property này thuộc vòng đời ứng dụng rộng hơn nên có thể ảnh hưởng các thành phần `SmartLifecycle` khác trong cùng giai đoạn. Hãy chọn nó như ngân sách thời gian shutdown của ứng dụng, không xem nó như một HTTP timeout tách biệt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="server-specific-shutdown-behavior">Các server được hỗ trợ ngừng nhận request mới như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>
Hợp đồng chung của Boot là “cho công việc đang xử lý hoàn tất trong khoảng thời gian gia hạn và không nhận công việc mới”, nhưng cơ chế khác nhau theo server. Trong Boot 3.3, Jetty, Reactor Netty và Tomcat ngừng nhận request mới ở tầng mạng khi graceful shutdown.

Undertow khác ở chỗ vẫn có thể chấp nhận kết nối nhưng trả HTTP `503 Service Unavailable` ngay cho request mới. Kết nối duy trì cũng có thể làm máy khách quan sát hành vi khác nhau, vì vậy không nên lấy hành vi trên đường truyền của một server làm định nghĩa chung cho graceful shutdown.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="in-flight-request-grace-period">Khoảng thời gian gia hạn cho request đang xử lý</a>

<details>
<summary>Xem chi tiết</summary>
Request đã bắt đầu xử lý được một khoảng thời gian để hoàn tất trong giai đoạn shutdown đã cấu hình. Graceful shutdown vì vậy giúp giảm lỗi không cần thiết khi kết thúc có kế hoạch, triển khai cuốn chiếu hoặc thay thế instance.

Đây vẫn là shutdown có giới hạn, không phải chờ vô hạn. Công việc của ứng dụng phải phù hợp ngân sách thời gian của vòng đời, và orchestrator/load balancer bên ngoài cũng phải cho tiến trình đủ thời gian để sử dụng khoảng thời gian gia hạn đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="shutdown-runtime-handoff">Bàn giao sang application-runtime và trạng thái sẵn sàng</a>

<details>
<summary>Xem chi tiết</summary>
Trách nhiệm web-runtime kết thúc khi hành vi dừng có kiểm soát của server và thời điểm trong vòng đời đã rõ. Trạng thái sẵn sàng, tín hiệu tiến trình, các bean vòng đời khác, công việc nền và chính sách kết thúc của orchestrator thuộc application-runtime cùng phần hạ tầng chịu trách nhiệm.

Một ranh giới thực tế là tín hiệu kết thúc. Tài liệu Boot lưu ý rằng nút stop trong IDE có thể dẫn tới shutdown ngay nếu IDE không gửi `SIGTERM` phù hợp. Graceful shutdown chỉ tham gia khi tiến trình thực sự đi vào đường đóng context thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)
