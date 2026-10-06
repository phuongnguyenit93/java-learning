<a id="back-to-top"></a>

# Thiết kế Starter và tổng hợp

## Menu
- [Starter bổ sung gì cho Auto-configuration?](#starter-purpose)
- [Tách Autoconfigure và Starter hay dùng một Starter kết hợp?](#starter-split-or-combine)
- [Quy tắc đặt tên Starter và quyền sở hữu Package](#starter-naming)
- [Namespace của Configuration Key và Metadata](#configuration-key-namespace)
- [Lựa chọn Dependency có chủ đích và tính năng tùy chọn](#starter-dependency-opinion)
- [Từ Starter Dependency đến ứng dụng đã được cấu hình](#starter-end-to-end-flow)
- [Ranh giới và hướng học tiếp theo](#auto-configuration-boundaries-next)

## <a id="starter-purpose">Starter bổ sung gì cho Auto-configuration?</a>

<details>
<summary>Xem chi tiết</summary>

Auto-configuration chứa logic cấu hình có điều kiện. Starter chủ yếu làm lựa chọn dependency trở nên thuận tiện: thêm một dependency là có những thành phần điển hình cần để bắt đầu dùng phần tích hợp.

Hướng dẫn Spring Boot mô tả starter như một lựa chọn có chủ đích về các dependency cần thiết để bắt đầu. Starter không cần chứa logic nghiệp vụ chỉ để chứng minh nó “có việc để làm”.

~~~text
starter dependency
→ đưa Boot core + Acme dependencies vào project
→ cung cấp Acme auto-configuration candidate
→ Boot đánh giá condition
→ ứng dụng nhận thiết lập mặc định phù hợp
~~~

Đây là lý do thiết kế starter nằm cuối module: starter có ý nghĩa vì nó đóng gói dependency quanh condition, back-off và giá trị mặc định mà người học đã hiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="starter-split-or-combine">Tách Autoconfigure và Starter hay dùng một Starter kết hợp?</a>

<details>
<summary>Xem chi tiết</summary>

Bố cục phổ biến tách code auto-configuration khỏi starter:

~~~text
acme-spring-boot
→ auto-configuration + configuration properties + extension API

acme-spring-boot-starter
→ lựa chọn dependency có chủ đích cho thiết lập phổ biến
~~~

Việc tách này không bắt buộc. Spring Boot cho phép phần tích hợp đơn giản, không có tính năng tùy chọn đáng kể, gộp cả hai vai trò vào một starter module.

Tách module hữu ích hơn khi có nhiều dependency tùy chọn hoặc nhiều lựa chọn dependency có chủ đích. Bên sử dụng khi đó có thể dùng artifact autoconfigure mà không phải nhận toàn bộ lựa chọn mặc định của starter.

Hãy chọn cấu trúc từ nhu cầu mở rộng và dependency, không từ quy tắc cứng rằng mọi starter đều phải chia thành hai module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="starter-naming">Quy tắc đặt tên Starter và quyền sở hữu Package</a>

<details>
<summary>Xem chi tiết</summary>

Starter bên thứ ba nên dùng namespace do thư viện sở hữu thay vì tạo cảm giác đó là module chính thức của Spring Boot. Hướng dẫn Boot dành không gian tên `spring-boot` cho hỗ trợ chính thức.

Mẫu thường gặp:

~~~text
acme-spring-boot
acme-spring-boot-starter
~~~

Nếu phần tích hợp gộp thành một artifact, tên theo starter giúp truyền đạt rằng đây là điểm vào dependency được khuyến nghị.

Quyền sở hữu package cũng phải rõ. Class auto-configuration nằm dưới package của thư viện, không nằm dưới package của ứng dụng sử dụng thư viện. Tên ổn định còn giảm rủi ro cho các tham chiếu ordering/exclusion.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="configuration-key-namespace">Namespace của Configuration Key và Metadata</a>

<details>
<summary>Xem chi tiết</summary>

Configuration key mà starter cung cấp nên dùng namespace do thư viện sở hữu, ví dụ acme.client. Phần tích hợp bên thứ ba không nên đặt key vào namespace Boot quản lý như server, management hoặc spring.

~~~text
acme.client.endpoint
acme.client.timeout
acme.client.enabled
~~~

Properties cần được mô tả để configuration metadata tạo hỗ trợ IDE hữu ích. Tác giả nên kiểm tra metadata đã tạo để xác nhận mô tả và type khớp quy ước cấu hình công khai.

Cơ chế binding vẫn thuộc Externalized Configuration; thiết kế starter chịu trách nhiệm cung cấp namespace mạch lạc và tránh xung đột tên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="starter-dependency-opinion">Lựa chọn Dependency có chủ đích và tính năng tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

Starter biểu diễn một lựa chọn dependency có chủ đích: tập thư viện mà đa số người dùng nên nhận mặc định. Nó nên đưa những dependency thường cần vào project nhưng tránh ép công nghệ tùy chọn không cần thiết.

Nếu phần tích hợp có nhiều tính năng tùy chọn, tách auto-configuration khỏi starter giúp bên sử dụng chọn tập dependency khác. Nhiều starter thậm chí có thể phục vụ các tổ hợp phổ biến khác nhau trong khi tái sử dụng cùng lớp auto-configuration.

Điểm phân biệt cần giữ:

~~~text
auto-configuration
→ hành vi có điều kiện

starter
→ dependency thuận tiện với lựa chọn có chủ đích
~~~

Nhầm hai khái niệm dễ dẫn tới thiết kế ép mọi thư viện tùy chọn lên mọi bên sử dụng chỉ vì auto-configuration có condition cho chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="starter-end-to-end-flow">Từ Starter Dependency đến ứng dụng đã được cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình tư duy hoàn chỉnh của module:

~~~text
ứng dụng thêm starter
        ↓
starter cung cấp dependency phổ biến
        ↓
dependency JAR cung cấp AutoConfiguration.imports
        ↓
Boot khám phá auto-configuration candidate
        ↓
ordering phối hợp xử lý configuration
        ↓
condition đánh giá classpath/property/bean/context
        ↓
mặc định phù hợp đóng góp bean definition
        ↓
lựa chọn của người dùng làm mặc định back off theo quy ước
        ↓
Condition Evaluation Report giải thích quyết định
        ↓
kiểm thử context tập trung chứng minh quy ước
~~~

Luồng này nối thiết kế dependency với hành vi runtime mà không nhầm starter là cơ chế trực tiếp tạo bean.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-configuration-boundaries-next">Ranh giới và hướng học tiếp theo</a>

<details>
<summary>Xem chi tiết</summary>

Module này kết thúc ở ranh giới Spring Boot auto-configuration.

Chuyển sang module khác khi câu hỏi thay đổi:

- “Spring container tạo và quản lý các bean này thế nào?” → Spring Framework core container.
- “Property được load, sắp thứ tự, bind và validate thế nào?” → Spring Boot Externalized Configuration.
- “Nên kiểm thử toàn bộ Boot ứng dụng thế nào?” → Spring Boot Testing.
- “Gradle/Maven plugin, BOM, executable archive hoặc container image hoạt động thế nào?” → Build Tooling and Packaging.
- “AOT/native image ảnh hưởng configuration này thế nào?” → Native Image.

Mô hình tư duy cần giữ lại là: **starter dependency làm khả năng tích hợp xuất hiện; cơ chế khám phá candidate tìm phần tích hợp; condition chọn phần phù hợp; back-off giữ quyền kiểm soát cho ứng dụng; chẩn đoán và kiểm thử context tập trung biến quyết định đó thành bằng chứng quan sát được.**

</details>

- [Quay lại đầu trang](#back-to-top)
