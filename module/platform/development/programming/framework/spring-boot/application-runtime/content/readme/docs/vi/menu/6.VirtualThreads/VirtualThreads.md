<a id="back-to-top"></a>

# Virtual threads trong Spring Boot

## Menu
- [`spring.threads.virtual.enabled` thay đổi điều gì?](#virtual-thread-switch)
- [Chiến lược executor và scheduler của Boot thay đổi như thế nào?](#virtual-executor-scheduler)
- [Vì sao các property kích thước pool không còn mô tả cùng một mô hình?](#virtual-thread-pool-properties)
- [Vì sao daemon virtual thread có thể cần `spring.main.keep-alive`?](#virtual-thread-daemon-keepalive)
- [Phần nào vẫn là ngữ nghĩa của Java virtual thread thay vì trách nhiệm của Boot?](#virtual-thread-java-boundary)

## <a id="virtual-thread-switch">`spring.threads.virtual.enabled` thay đổi điều gì?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot 3.3 tích hợp Java 21 virtual threads qua một công tắc ở cấp ứng dụng: `spring.threads.virtual.enabled=true`. Property này không thay đổi ngữ nghĩa của JVM; nó yêu cầu Boot chọn cách triển khai dùng virtual thread cho những hạ tầng runtime được hỗ trợ.

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Với task execution, Boot chuyển từ `ThreadPoolTaskExecutor` sang `SimpleAsyncTaskExecutor` cấu hình virtual thread. Với scheduling, Boot chuyển sang `SimpleAsyncTaskScheduler` dùng virtual threads.

Lợi ích của công tắc là tạo cách tích hợp thống nhất cho hạ tầng do Boot quản lý. Workload có thực sự hưởng lợi hay không vẫn phụ thuộc hành vi ở cấp Java và đặc điểm workload.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-executor-scheduler">Chiến lược executor và scheduler của Boot thay đổi như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Bật virtual thread làm thay đổi *chiến lược triển khai* phía sau hạ tầng task của Boot. Ứng dụng vẫn dùng abstraction executor/scheduler, nhưng cách triển khai không còn mô hình một pool worker cố định/có giới hạn giống đường platform thread.

`SimpleAsyncTaskExecutor` có thể tạo virtual thread cho task được gửi vào, còn `SimpleAsyncTaskScheduler` sử dụng virtual-thread execution cho công việc theo lịch. Các builder tương ứng do Boot cung cấp cũng được cấu hình theo lựa chọn virtual thread.

Vì vậy nên đánh giá việc chuyển đổi ở ranh giới abstraction thay vì tìm từng chỗ tạo virtual thread. Nếu ứng dụng tự tạo executor ngoài Boot, `spring.threads.virtual.enabled` không tự động quản lý hạ tầng do ứng dụng sở hữu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-pool-properties">Vì sao các property kích thước pool không còn mô tả cùng một mô hình?</a>

<details>
<summary>Xem chi tiết</summary>

Các property về kích thước pool mô tả cách quản lý một tập platform worker threads. Mô hình đó không ánh xạ trực tiếp sang `SimpleAsyncTaskExecutor`/`SimpleAsyncTaskScheduler` dùng virtual thread. Spring Boot 3.3 ghi rõ các scheduler property liên quan pooling bị bỏ qua khi virtual threads được bật.

Do đó quy tắc tinh chỉnh cũ như "tăng `spring.task.scheduling.pool.size`" có thể không còn mô tả runtime đang chạy. Không nên mang nguyên cách tính của pool platform thread sang đường virtual thread rồi giả định nó vẫn điều khiển mức song song giống trước.

Virtual threads vẫn tiêu thụ CPU, bộ nhớ, connection, rate limit và năng lực của hệ thống phía sau. Việc giới hạn concurrency có thể vẫn cần, nhưng đó là thiết kế workload/concurrency chứ không phải lý do giả lập lại các thiết lập pool cũ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-daemon-keepalive">Vì sao daemon virtual thread có thể cần `spring.main.keep-alive`?</a>

<details>
<summary>Xem chi tiết</summary>

Virtual thread là daemon thread. JVM có thể thoát khi chỉ còn daemon threads, nên ứng dụng dựa vào công việc nền trên virtual thread có thể không còn non-daemon thread nào giữ tiến trình sống.

Spring Boot cung cấp `spring.main.keep-alive=true` cho trường hợp JVM cần tiếp tục sống ngay cả khi toàn bộ công việc đang chạy trên daemon virtual threads. Điều này đáng chú ý với ứng dụng non-web hoặc thiên về scheduling vì chúng có thể không có thành phần runtime khác giữ non-daemon thread.

```yaml
spring:
  main:
    keep-alive: true
```

Đây là cấu hình lifecycle của tiến trình, không phải tinh chỉnh hiệu năng. Nếu tiến trình thoát sau khi bật virtual threads, hãy kiểm tra thread nào đang giữ lifecycle trước khi chỉnh executor property không liên quan.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-java-boundary">Phần nào vẫn là ngữ nghĩa của Java virtual thread thay vì trách nhiệm của Boot?</a>

<details>
<summary>Xem chi tiết</summary>

Boot sở hữu công tắc và những tích hợp được hỗ trợ phản ứng với công tắc đó. Java sở hữu ngữ nghĩa bên dưới: cách virtual thread được schedule, blocking/pinning, carrier thread, daemon status, interruption, hành vi thread-local và quyết định workload nào phù hợp.

Ranh giới này quan trọng vì `spring.threads.virtual.enabled=true` không phải lời hứa rằng mọi workload nhanh hơn. Workload CPU-bound vẫn tranh CPU; thư viện hoặc code có thể tạo điều kiện làm khả năng mở rộng khác kỳ vọng.

Dùng tài liệu Boot để biết thành phần do Boot quản lý thay đổi ra sao. Dùng phần kiến thức Java concurrency và hướng dẫn JDK để hiểu *vì sao* virtual thread hành xử như vậy và cách chẩn đoán concurrency ở cấp JVM.

</details>

- [Quay lại đầu trang](#back-to-top)
