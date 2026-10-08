<a id="back-to-top"></a>

# Application arguments và runners

## Menu
- [`ApplicationArguments` diễn giải đầu vào dòng lệnh như thế nào?](#application-arguments-model)
- [`ApplicationRunner` và `CommandLineRunner` chạy ở thời điểm nào?](#runner-timing)
- [`ApplicationRunner` và `CommandLineRunner` khác nhau thế nào?](#applicationrunner-vs-commandlinerunner)
- [Điều khiển thứ tự khi có nhiều runner như thế nào?](#runner-ordering)
- [Loại công việc startup nào phù hợp với runner?](#runner-use-cases)
- [Công việc nào không nên chặn giai đoạn chạy runner?](#runner-antipatterns)

## <a id="application-arguments-model">`ApplicationArguments` diễn giải đầu vào dòng lệnh như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

`ApplicationArguments` là cách Boot biểu diễn có cấu trúc cho `String[]` truyền vào `SpringApplication.run`. Nó giữ các đối số nguồn ban đầu và tách option như `--mode=batch` khỏi non-option argument như tên file ở vị trí tự do. Code có thể hỏi option nào tồn tại và lấy các giá trị tương ứng.

```java
@Component
class ImportRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("dry-run")) {
            // chỉ kiểm tra, chưa ghi dữ liệu
        }
    }
}
```

Command-line option đồng thời có thể tham gia externalized configuration, nhưng thứ tự ưu tiên thuộc module configuration. Ở đây điểm quan trọng là runner có thể dùng đầu vào của lần gọi mà không tự phân tích lại chuỗi thô.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runner-timing">`ApplicationRunner` và `CommandLineRunner` chạy ở thời điểm nào?</a>

<details>
<summary>Xem chi tiết</summary>

Boot gọi `ApplicationRunner` và `CommandLineRunner` sau khi `ApplicationContext` refresh và `ApplicationStartedEvent` đã được phát. Chúng chạy trước `ApplicationReadyEvent` và trước khi Boot chuyển readiness thành `ACCEPTING_TRAFFIC`.

Thời điểm này rất phù hợp cho công việc startup cần bean bình thường và bắt buộc phải hoàn thành trước khi ứng dụng được xem là sẵn sàng: kiểm tra invariant riêng của ứng dụng, làm ấm một cache nhỏ bắt buộc, hoặc thực hiện workload dạng lệnh của ứng dụng non-web.

Thời điểm này cũng tạo ra trách nhiệm. Runner chặn vài phút thì readiness cũng chậm vài phút. Runner ném exception thì startup không hoàn tất bình thường. Hãy xem giai đoạn runner là một phần rõ ràng của startup, không phải thread nền ẩn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="applicationrunner-vs-commandlinerunner">`ApplicationRunner` và `CommandLineRunner` khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Hai interface runner nằm ở cùng giai đoạn lifecycle; khác biệt chính là dạng đối số. `CommandLineRunner.run(String... args)` nhận chuỗi command-line thô, còn `ApplicationRunner.run(ApplicationArguments args)` nhận biểu diễn đã được Boot phân tích với API cho option/non-option.

Chọn `ApplicationRunner` khi code quan tâm command-line options như dữ liệu có cấu trúc. Chọn `CommandLineRunner` khi chính chuỗi đối số thô là hợp đồng cần dùng. Không interface nào bất đồng bộ hơn hay chạy "muộn hơn" interface kia.

Vì thời điểm và thứ tự giống nhau, lựa chọn nên dựa trên ngữ nghĩa của đầu vào. Có thể dùng cả hai, nhưng quá nhiều runner làm thứ tự startup khó hiểu; nên giữ mỗi runner có trách nhiệm rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runner-ordering">Điều khiển thứ tự khi có nhiều runner như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Khi có nhiều runner, hợp đồng sắp thứ tự của Spring được áp dụng. Runner có thể implement `Ordered` hoặc dùng `@Order`; giá trị order nhỏ hơn có độ ưu tiên cao hơn và được gọi sớm hơn tương đối.

Việc sắp thứ tự hữu ích khi có dependency startup thực sự, ví dụ phải nạp dữ liệu tham chiếu trước khi kiểm tra một index phụ thuộc vào dữ liệu đó. Nhưng không nên biến danh sách runner cùng hàng loạt số order thành một công cụ điều phối quy trình ẩn.

Nếu runner B không thể chạy đúng khi thiếu runner A, hãy làm dependency đó rõ trong thiết kế và kiểm thử. Nếu dependency graph ngày càng lớn, nên chuyển phần điều phối thành application service chuyên trách thay vì tiếp tục thêm callback và độ ưu tiên dạng số.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runner-use-cases">Loại công việc startup nào phù hợp với runner?</a>

<details>
<summary>Xem chi tiết</summary>

Runner phù hợp với công việc có ba đặc điểm: cần context đã refresh, thực sự thuộc startup và readiness phải chờ nó hoàn tất. Ví dụ: kiểm tra điều kiện tiên quyết runtime không thể xác minh sớm hơn, thực hiện một migration startup nhỏ do ứng dụng sở hữu, hoặc chạy task ở chế độ lệnh rồi kết thúc tiến trình.

Hãy giữ công việc có giới hạn và quan sát được. Log rõ lúc bắt đầu/lỗi, để exception nghiêm trọng lan truyền và đảm bảo việc chạy lặp lại an toàn nếu môi trường triển khai có thể khởi động lại tiến trình.

Nếu việc kiểm tra có thể làm ngay khi bind configuration, hãy dùng configuration validation. Nếu invariant thuộc bước tạo bean, hãy để bean lifecycle xử lý. Runner có giá trị vì thời điểm của nó; không nên trở thành nơi chứa mọi code "chạy lúc startup".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runner-antipatterns">Công việc nào không nên chặn giai đoạn chạy runner?</a>

<details>
<summary>Xem chi tiết</summary>

Runner không phù hợp với vòng lặp polling vô hạn, message consumer sống suốt tiến trình hoặc workload nền nặng kéo dài. Những công việc đó làm giai đoạn runner không kết thúc, kéo theo `ApplicationReadyEvent` và readiness không tới. Tạo thread không được quản lý bên trong runner còn bỏ qua hạ tầng thực thi do Boot quản lý và làm shutdown khó dự đoán.

Nếu công việc phải tiếp tục sau startup, hãy đưa nó sang executor được quản lý, scheduler hoặc thành phần runtime chuyên biệt. Nếu lưu lượng không cần chờ công việc, đừng chặn readiness khi không có yêu cầu rõ ràng.

Một dấu hiệu thiết kế xấu khác là dùng runner để "sửa" cấu hình thiếu hoặc nuốt lỗi khởi tạo nghiêm trọng. Ứng dụng không đạt điều kiện tiên quyết quan trọng nên thất bại rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)
