<a id="back-to-top"></a>

# Kích hoạt và khám phá Candidate

## Menu
- [@SpringBootApplication và @EnableAutoConfiguration](#spring-boot-application-and-enable-auto-configuration)
- [Khám phá Candidate không đồng nghĩa với tạo Bean](#candidate-discovery-vs-bean-creation)
- [AutoConfiguration.imports và ImportCandidates](#auto-configuration-imports)
- [Package Auto-configuration, Component Scanning và Explicit Import](#auto-configuration-package-isolation)
- [Base Package của ứng dụng và ranh giới với cơ chế khám phá Auto-configuration](#application-base-package-boundary)

## <a id="spring-boot-application-and-enable-auto-configuration">@SpringBootApplication và @EnableAutoConfiguration</a>

<details>
<summary>Xem chi tiết</summary>

SpringBootApplication đã bao gồm EnableAutoConfiguration, vì vậy ứng dụng Boot thông thường đã bật cơ chế lựa chọn auto-configuration. Thêm EnableAutoConfiguration một lần nữa không tạo ra cơ chế auto-configuration thứ hai.

SpringBootApplication đồng thời bật component scanning cho ứng dụng, nhưng đó là trách nhiệm khác. Component scanning tìm component của ứng dụng; cơ chế chọn auto-configuration tìm các candidate được công bố bởi dependency.

Điểm phân biệt này quan trọng vì auto-configuration của một thư viện phải được khám phá ngay cả khi package của nó nằm ngoài base package của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="candidate-discovery-vs-bean-creation">Khám phá Candidate không đồng nghĩa với tạo Bean</a>

<details>
<summary>Xem chi tiết</summary>

Boot phải tìm được tập auto-configuration có thể xem xét trước khi đánh giá chúng. Bước khám phá trả lời “candidate nào tồn tại?”. Bước đánh giá condition trả lời “candidate nào phù hợp với ứng dụng này?”.

Một candidate đã được khám phá vẫn có thể không đóng góp bean nào vì class bắt buộc bị thiếu, thuộc tính cấu hình tắt tính năng, bean cộng tác chưa có, bean do ứng dụng cung cấp làm giá trị mặc định back off hoặc candidate bị loại trừ.

Khi chẩn đoán một bean bị thiếu, tách rõ các câu hỏi:

~~~text
Auto-configuration có được khám phá không?
        ↓
Condition ở mức configuration có khớp không?
        ↓
Condition ở mức bean có khớp không?
        ↓
Candidate có bị loại trừ hay bị lựa chọn của ứng dụng thay thế không?
~~~

Tách hai giai đoạn này giúp tránh lỗi suy luận rằng “không áp dụng” đồng nghĩa với “không được khám phá”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-configuration-imports">AutoConfiguration.imports và ImportCandidates</a>

<details>
<summary>Xem chi tiết</summary>

Trong Spring Boot 3.3, auto-configuration được công bố qua file:

~~~text
META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
~~~

Mỗi dòng không phải chú thích khai báo tên đầy đủ của một auto-configuration class. Hạ tầng ImportCandidates của Boot đọc các khai báo này khi auto-configuration được bật.

Ví dụ phần tích hợp Acme có thể khai báo:

~~~text
com.acme.boot.AcmeClientAutoConfiguration
com.acme.boot.AcmeMetricsAutoConfiguration
~~~

File imports chỉ là chỉ mục để khám phá candidate, không bảo đảm các class đó sẽ áp dụng. Condition của từng candidate vẫn phải được đánh giá theo ứng dụng đang chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-configuration-package-isolation">Package Auto-configuration, Component Scanning và Explicit Import</a>

<details>
<summary>Xem chi tiết</summary>

Auto-configuration được công bố nên nằm trong package do thư viện sở hữu và được nạp qua AutoConfiguration.imports. Hướng dẫn chính thức của Boot cố ý không dùng component scanning làm cơ chế khám phá auto-configuration.

Auto-configuration cũng không nên bật component scanning rộng chỉ để tìm thành phần hỗ trợ của chính nó. Hãy import cấu hình hỗ trợ một cách tường minh:

~~~java
@AutoConfiguration
@Import(AcmeClientConfiguration.class)
class AcmeClientAutoConfiguration {
}
~~~

Cách này làm ranh giới tích hợp rõ ràng và tránh quét nhầm class của ứng dụng hoặc nội bộ thư viện không liên quan.

Component scanning trong ứng dụng sử dụng thư viện vẫn hoàn toàn bình thường. Giới hạn ở đây áp dụng cho cách auto-configuration được công bố và cách nó đưa component do chính phần tích hợp sở hữu vào context.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-base-package-boundary">Base Package của ứng dụng và ranh giới với cơ chế khám phá Auto-configuration</a>

<details>
<summary>Xem chi tiết</summary>

Package chứa class mang EnableAutoConfiguration, thường là SpringBootApplication class, có ý nghĩa như package mặc định phía ứng dụng. Một số tính năng Boot khác có thể dùng package này để xác định phạm vi tìm class của ứng dụng.

Việc khám phá auto-configuration bên ngoài là cơ chế khác:

~~~text
base package của ứng dụng
→ quy ước quét phía ứng dụng

AutoConfiguration.imports trong dependency JAR
→ candidate auto-configuration bên ngoài
~~~

Vì vậy com.acme.boot.AcmeClientAutoConfiguration không cần nằm dưới com.example.myapp. Di chuyển class chính của ứng dụng có thể thay đổi ranh giới quét phía ứng dụng, nhưng không nên là yêu cầu để một auto-configuration bên ngoài được công bố đúng cách có thể được khám phá.

Khi đã tách được bước khám phá candidate khỏi việc quét component của ứng dụng, câu hỏi tiếp theo là lựa chọn: candidate nào trong số đã được khám phá thực sự nên áp dụng? Mô hình condition trả lời câu hỏi đó.

</details>

- [Quay lại đầu trang](#back-to-top)
