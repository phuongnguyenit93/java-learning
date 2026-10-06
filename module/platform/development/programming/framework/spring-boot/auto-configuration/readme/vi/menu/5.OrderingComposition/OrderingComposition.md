<a id="back-to-top"></a>

# Thứ tự và cách phối hợp Auto-configuration

## Menu
- [Thứ tự khai báo Configuration và thứ tự tạo Bean](#configuration-order-vs-bean-order)
- [Quan hệ Before và After](#before-after-ordering)
- [Sắp xếp các Auto-configuration độc lập](#auto-configure-order)
- [Phối hợp các Configuration có Condition](#conditional-composition)
- [Cô lập công nghệ tùy chọn](#optional-technology-isolation)

## <a id="configuration-order-vs-bean-order">Thứ tự khai báo Configuration và thứ tự tạo Bean</a>

<details>
<summary>Xem chi tiết</summary>

Auto-configuration ordering phối hợp **thứ tự xử lý configuration definition**. Nó không quyết định thứ tự tạo bean instance.

~~~text
auto-configuration A được xử lý trước B
→ B có thể lý giải definition mà A đã có cơ hội đóng góp

bean X phụ thuộc bean Y
→ dependency/lifecycle rule bình thường của Spring quyết định creation order
~~~

Nhầm hai tầng này tạo thiết kế mong manh. Nếu một bean thật sự phụ thuộc bean khác, hãy biểu diễn dependency qua injection hoặc quan hệ container phù hợp thay vì dựa vào auto-configuration order.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="before-after-ordering">Quan hệ Before và After</a>

<details>
<summary>Xem chi tiết</summary>

Khi một auto-configuration cần được xử lý trước hoặc sau auto-configuration khác, Boot hỗ trợ quan hệ tường minh bằng before/after trên AutoConfiguration hoặc AutoConfigureBefore/AutoConfigureAfter.

Ví dụ về ý định:

~~~text
AcmeCoreAutoConfiguration
        ↓ before
AcmeMetricsAutoConfiguration
~~~

Metrics configuration khi đó biết rằng core configuration đã có cơ hội đăng ký definition trước. Quan hệ theo class name hữu ích khi không muốn biến class phía bên kia thành một dependency cứng trên classpath.

Chỉ dùng ordering khi có quan hệ thật về xử lý definition, không dùng chỉ để làm danh sách “đẹp mắt”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="auto-configure-order">Sắp xếp các Auto-configuration độc lập</a>

<details>
<summary>Xem chi tiết</summary>

AutoConfigureOrder cung cấp giá trị thứ tự cho các auto-configuration cần vị trí tương đối nhưng không có quan hệ before/after trực tiếp.

Nó thuộc riêng luồng xử lý auto-configuration. Không được suy ra rằng giá trị thứ tự trên configuration cũng sắp xếp mọi callback runtime, filter, listener hoặc quá trình tạo bean.

Khi có dependency cụ thể, before/after thường biểu đạt rõ hơn vì nó ghi lại quan hệ trực tiếp. Thứ tự bằng số phù hợp khi các configuration chỉ cần một vị trí tổng quát trong chuỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="conditional-composition">Phối hợp các Configuration có Condition</a>

<details>
<summary>Xem chi tiết</summary>

Phần tích hợp lớn dễ hiểu hơn khi configuration được tách theo ranh giới áp dụng. Một auto-configuration cấp cao có thể import hoặc chứa các configuration có condition hẹp hơn cho tính năng tùy chọn.

~~~text
AcmeClientAutoConfiguration
├── core client configuration
├── metrics configuration      [có thư viện metrics]
└── servlet configuration      [ứng dụng web Servlet]
~~~

Cách này giữ condition gần tính năng mà nó bảo vệ và giảm rủi ro một tính năng tùy chọn làm hỏng cả phần tích hợp.

Cách phối hợp vẫn nên tường minh: dùng import và các configuration class tập trung thay vì component scan quá rộng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="optional-technology-isolation">Cô lập công nghệ tùy chọn</a>

<details>
<summary>Xem chi tiết</summary>

Công nghệ tùy chọn phải được cô lập cả ở ranh giới dependency lẫn ranh giới class loading. Configuration tham chiếu type tùy chọn quá sớm có thể lỗi trước khi condition kịp kết luận “không áp dụng”.

Mẫu thực tế:

~~~text
auto-configuration cấp cao
        ↓ import
nested/separate optional configuration
        ↓ class-level ConditionalOnClass bảo vệ
Bean method mới an toàn khi tham chiếu optional type
~~~

Cấu trúc này cũng dễ kiểm thử hơn: dùng FilteredClassLoader loại thư viện tùy chọn và chứng minh chỉ nhánh tùy chọn biến mất.

Đánh đổi là có thêm configuration class, nhưng đổi lại phần tích hợp có ranh giới lỗi rõ ràng và starter có thể hỗ trợ khả năng tùy chọn mà không ép tất cả bên sử dụng nhận chúng.

Thứ tự và sự cô lập giúp giảm mơ hồ, nhưng khi hành vi thực tế không như mong đợi vẫn cần bằng chứng để biết vì sao candidate khớp hoặc không khớp. Chương tiếp theo tập trung vào exclusion và chẩn đoán condition.

</details>

- [Quay lại đầu trang](#back-to-top)
