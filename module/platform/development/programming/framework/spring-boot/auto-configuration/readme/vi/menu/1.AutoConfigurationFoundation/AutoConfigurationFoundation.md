<a id="back-to-top"></a>

# Nền tảng Auto-configuration

## Menu
- [Auto-configuration là gì và vì sao Spring Boot cần nó?](#auto-configuration-what-why)
- [Spring Framework Configuration và Spring Boot Auto-configuration khác nhau thế nào?](#boot-vs-framework-configuration)
- [Giá trị mặc định, Back-off và quyền kiểm soát của ứng dụng](#defaults-and-back-off-contract)
- [Luồng Auto-configuration từ đầu đến cuối](#auto-configuration-end-to-end-flow)

## <a id="auto-configuration-what-why">Auto-configuration là gì và vì sao Spring Boot cần nó?</a>

<details>
<summary>Xem chi tiết</summary>

Auto-configuration là cơ chế của Spring Boot dùng để bổ sung cấu hình hạ tầng thường cần thiết mà ứng dụng không phải tự khai báo mọi bean. Boot quan sát các dữ kiện đã có quanh ứng dụng như classpath, thuộc tính cấu hình, bean hiện hữu, tài nguyên và loại ứng dụng, sau đó chỉ đóng góp cấu hình khi các condition tương ứng khớp.

Vấn đề thực tế là sự lặp lại. Nếu không có auto-configuration, nhiều ứng dụng dùng cùng một thư viện sẽ lặp lại việc tạo cùng loại bean hạ tầng, sao chép cùng giá trị mặc định và nối cùng nhóm đối tượng cộng tác. Boot đóng gói phần thiết lập lặp lại đó thành cấu hình có điều kiện và có thể tái sử dụng.

~~~text
dependency + cấu hình + bean do ứng dụng định nghĩa
                    ↓
          candidate auto-configuration
                    ↓
             đánh giá condition
                    ↓
          cấu hình mặc định phù hợp
~~~

Auto-configuration không phải là Boot “đoán ngẫu nhiên”. Đây vẫn là cấu hình Spring được chọn bằng các quy tắc rõ ràng. Condition Evaluation Report là một bằng chứng quan trọng vì nó cho biết candidate nào khớp và candidate nào không khớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-vs-framework-configuration">Spring Framework Configuration và Spring Boot Auto-configuration khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework sở hữu các cơ chế nền của container như Configuration class, Bean method, ApplicationContext, bean definition, import và condition. Spring Boot xây trên những cơ chế đó rồi bổ sung tập cấu hình có quy ước cùng cách khám phá chúng.

Ví dụ, một ứng dụng Spring thuần có thể tự khai báo AcmeClient. Một tích hợp của Boot có thể công bố auto-configuration chỉ tạo AcmeClient khi thư viện Acme có mặt, cấu hình phù hợp tồn tại và ứng dụng chưa tự cung cấp client của riêng mình.

~~~text
Spring tạo và quản lý bean như thế nào
→ Spring Framework

Khi nào Boot quyết định một cấu hình mặc định có thể tham gia
→ Spring Boot auto-configuration
~~~

Vì vậy module này tập trung vào cách Boot lựa chọn, back-off, sắp xếp, chẩn đoán và cho phép tác giả thư viện xây auto-configuration; nó không dạy lại toàn bộ Spring container.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="defaults-and-back-off-contract">Giá trị mặc định, Back-off và quyền kiểm soát của ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

Auto-configuration tốt cung cấp giá trị mặc định chứ không khóa ứng dụng. Tích hợp thường đưa ra một lựa chọn tiện dụng khi ứng dụng chưa quyết định cụ thể hơn. Nếu ứng dụng đã định nghĩa bean phù hợp thì auto-configuration thường phải back off.

~~~text
ứng dụng chưa có AcmeClient
→ Boot có thể cung cấp AcmeClient mặc định

ứng dụng đã có AcmeClient
→ Boot nhường quyền quyết định cho ứng dụng
~~~

Back-off rộng hơn một annotation. Công tắc cấu hình, class bị thiếu, loại ứng dụng, tài nguyên hoặc loại trừ tường minh đều có thể ngăn một auto-configuration đóng góp cấu hình. Mục tiêu thiết kế vẫn là: làm đường đi phổ biến trở nên đơn giản nhưng giữ quyền kiểm soát cho ứng dụng.

Do đó “được auto-configure” không bao giờ đồng nghĩa với “luôn được tạo”. Mọi mặc định phải được hiểu cùng với condition bảo vệ nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-configuration-end-to-end-flow">Luồng Auto-configuration từ đầu đến cuối</a>

<details>
<summary>Xem chi tiết</summary>

Có thể hiểu quá trình khởi động auto-configuration như một chuỗi:

~~~text
1. SpringBootApplication bật auto-configuration
        ↓
2. Boot khám phá các candidate auto-configuration
        ↓
3. candidate được sắp xếp
        ↓
4. condition đánh giá trạng thái hiện tại của ứng dụng
        ↓
5. cấu hình khớp đóng góp bean definition
        ↓
6. back-off cho lựa chọn tường minh của ứng dụng quyền ưu tiên
        ↓
7. container tạo bean từ tập definition cuối cùng
~~~

Có hai ranh giới phải nhớ. Khám phá candidate không phải là bước đánh giá condition: một class có thể được tìm thấy nhưng vẫn không áp dụng. Thứ tự auto-configuration cũng không phải thứ tự khởi tạo bean: việc sắp xếp phối hợp lúc xử lý cấu hình, còn việc tạo bean vẫn tuân theo dependency và vòng đời của Spring container.

Các chương sau lần lượt mở rộng từng giai đoạn trong luồng này. Khi gặp một chi tiết khó hiểu, hãy xác định nó thuộc giai đoạn nào trước.

</details>

- [Quay lại đầu trang](#back-to-top)
