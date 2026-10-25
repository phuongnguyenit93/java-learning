<a id="back-to-top"></a>

# Lazy initialization và tối ưu startup

## Menu
- [Lazy initialization thay đổi điều gì trong startup?](#lazy-initialization-purpose)
- [Lazy initialization có thể trì hoãn những lỗi và chi phí nào?](#lazy-initialization-tradeoffs)
- [Vì sao phải đo startup trước khi tối ưu?](#startup-measurement)
- [`ApplicationStartup` cung cấp bằng chứng về startup như thế nào?](#application-startup-tracking)
- [Startup tracking bàn giao sang Actuator và observability ở đâu?](#startup-observability-boundary)

## <a id="lazy-initialization-purpose">Lazy initialization thay đổi điều gì trong startup?</a>

<details>
<summary>Xem chi tiết</summary>

Mặc định, ứng dụng Boot tạo nhiều singleton bean trong quá trình context startup. Lazy initialization đổi thời điểm đó: bean đủ điều kiện chỉ được tạo khi lần đầu cần dùng. Boot cung cấp cơ chế này qua `spring.main.lazy-initialization=true` và API trên `SpringApplication`/builder.

Điều đó có thể giảm lượng công việc nằm trên đường quan trọng của startup, nhất là khi một phần bean graph chưa cần ngay. Nhưng công việc không biến mất; nó được dời sang lần sử dụng đầu tiên.

Mô hình tư duy đúng là *deferred initialization*, không phải "hiệu năng startup miễn phí". Tiến trình sẵn sàng nhanh nhưng request đầu tiên phải tạo nhiều bean có thể chỉ chuyển latency từ lúc triển khai sang lưu lượng runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lazy-initialization-tradeoffs">Lazy initialization có thể trì hoãn những lỗi và chi phí nào?</a>

<details>
<summary>Xem chi tiết</summary>

Đánh đổi lớn nhất của lazy initialization là thời điểm phát hiện lỗi bị trì hoãn. Bean cấu hình sai vốn thất bại ngay khi startup có thể chỉ lỗi khi request hoặc task nền đầu tiên cần bean đó. Điều này thay đổi cả thời điểm và tác động vận hành của lỗi.

Lazy initialization cũng không có nghĩa JVM chỉ cần bộ nhớ cho bean được tạo lúc startup. Khi ứng dụng chạy đủ lâu, toàn bộ graph vẫn có thể được tạo đầy đủ, nên hoạch định năng lực phải nhìn trạng thái chạy ổn định.

Spring Boot vì thế không bật lazy initialization mặc định. Chỉ bật vì lý do đo được, rồi dùng `@Lazy(false)` cho những bean nên thất bại sớm hơn nếu việc kiểm tra sớm có giá trị cao.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Lazy Initialization](https://docs.spring.io/spring-boot/3.3/reference/features/spring-application.html#features.spring-application.lazy-initialization)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="startup-measurement">Vì sao phải đo startup trước khi tối ưu?</a>

<details>
<summary>Xem chi tiết</summary>

Tối ưu startup nên bắt đầu bằng giai đoạn và phép đo, không phải property. "Startup mất 12 giây" mới chỉ là quan sát; câu hỏi hữu ích là công việc nào chiếm thời gian và nó có thật sự cần nằm trên đường quan trọng hay không.

Nguyên nhân có thể thuộc nhiều bên sở hữu: khởi tạo bean, lời gọi mạng trong code startup, cấu hình ứng dụng, thiết lập logging, runner hoặc vấn đề web server. Lazy initialization không thể sửa mọi loại nguyên nhân.

Hãy tạo mốc chuẩn có thể lặp lại, so sánh các lần chạy tương đương rồi thay đổi một cơ chế có chi phí giải thích được. Mục tiêu là thời gian startup chấp nhận được đồng thời giữ khả năng phát hiện lỗi sớm và độ trễ lần sử dụng đầu tiên có thể dự đoán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-startup-tracking">`ApplicationStartup` cung cấp bằng chứng về startup như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`ApplicationStartup` của Spring Framework cho phép code startup ghi các `StartupStep` có cấu trúc. Spring Boot có thể được cấu hình với cách triển khai như `BufferingApplicationStartup` để lưu dữ liệu startup cho việc phân tích sau đó.

```java
SpringApplication application = new SpringApplication(MyApplication.class);
application.setApplicationStartup(new BufferingApplicationStartup(2048));
application.run(args);
```

Cách này biến câu đoán mơ hồ "Spring startup chậm" thành các startup step có tên và thời gian cụ thể. Dữ liệu vẫn cần được giải thích: một step chậm có thể chỉ đến hoạt động của bean/framework, nhưng bản sửa thuộc thành phần thực sự làm công việc.

Instrumentation cũng có chi phí; hãy dùng mức phù hợp và từ bằng chứng đó tạo ra giả thuyết cụ thể trước khi tối ưu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="startup-observability-boundary">Startup tracking bàn giao sang Actuator và observability ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu việc dùng startup tracking như bằng chứng runtime và mô hình quyết định quanh lazy initialization. Việc cung cấp dữ liệu startup đã buffer qua production endpoint thuộc Actuator; pipeline metrics/tracing rộng hơn thuộc observability.

Ranh giới giúp luồng học rõ: trước tiên hiểu phép đo startup nói gì về chi phí lifecycle; sau đó mới học công cụ production cung cấp hoặc vận chuyển dữ liệu đó.

Tương tự, đặc tính startup của AOT/native image thuộc module `native-image`. Không nên dùng quy tắc tinh chỉnh của JVM thông thường như lời giải mặc định cho dạng runtime khác. Hãy xác định runtime và nguồn đo trước khi so sánh con số.

</details>

- [Quay lại đầu trang](#back-to-top)
