<a id="back-to-top"></a>

# Kiểm thử quyết định Auto-configuration

## Menu
- [Vì sao Auto-configuration cần kiểm thử Context tập trung?](#why-focused-context-tests)
- [ApplicationContextRunner](#application-context-runner)
- [Kiểm thử Back-off và các biến thể Property](#back-off-and-property-tests)
- [Kiểm thử khi Classpath thiếu Dependency bằng FilteredClassLoader](#filtered-class-loader)
- [Context Runner cho Servlet và Reactive](#web-context-runners)
- [Bằng chứng từ Condition Evaluation trong Test](#condition-report-in-tests)

## <a id="why-focused-context-tests">Vì sao Auto-configuration cần kiểm thử Context tập trung?</a>

<details>
<summary>Xem chi tiết</summary>

Hành vi auto-configuration là một ma trận quyết định, không chỉ một đường khởi động thuận lợi duy nhất. Kết quả có thể đổi theo bean do người dùng cung cấp, property, classpath, resource và loại ứng dụng.

Một SpringBootTest đầy đủ chứng minh được cả ứng dụng khởi động trong một cấu hình cụ thể, nhưng thường quá rộng để giải thích vì sao một condition khớp. Kiểm thử context tập trung cho phép dựng đúng trạng thái liên quan tới auto-configuration.

Các chiều kiểm thử hữu ích:

- trạng thái mặc định;
- bean thay thế do người dùng cung cấp;
- property bật/tắt;
- thư viện tùy chọn có/không có;
- servlet, reactive và non-web context;
- chẩn đoán cho trường hợp dự kiến không khớp.

Mục tiêu là bằng chứng thực thi cho quy ước của auto-configuration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-context-runner">ApplicationContextRunner</a>

<details>
<summary>Xem chi tiết</summary>

ApplicationContextRunner tạo một ApplicationContext nhỏ, cấu hình được cho từng lần test. AutoConfigurations.of cho phép đăng ký auto-configuration cần test mà không khởi động toàn bộ ứng dụng thực tế.

~~~java
private final ApplicationContextRunner contextRunner =
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(AcmeClientAutoConfiguration.class));
~~~

Mỗi lần chạy tạo context mới, cho phép kiểm tra kết quả trên context rồi đóng context sau test. Có thể bổ sung configuration của người dùng, property và classloader vào runner.

Cách test này làm bằng chứng condition rất rõ: thiết lập mô tả trạng thái đầu vào; phần kiểm tra mô tả chính xác bean nào phải có hoặc không được có.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="back-off-and-property-tests">Kiểm thử Back-off và các biến thể Property</a>

<details>
<summary>Xem chi tiết</summary>

Back-off và property phải được kiểm thử như các biến thể có chủ đích thay vì chỉ tin annotation.

~~~java
contextRunner
    .withUserConfiguration(CustomClientConfiguration.class)
    .run(context ->
        assertThat(context).hasSingleBean(AcmeClient.class));
~~~

Test cần phân biệt bean tùy chỉnh với bean mặc định và xác nhận chỉ bean được mong đợi còn lại.

Với property:

~~~text
acme.client.enabled=true
→ phần tích hợp mặc định xuất hiện

acme.client.enabled=false
→ phần tích hợp không xuất hiện
~~~

Test cả hai phía giúp bắt havingValue bị đảo, hiểu sai matchIfMissing và missing-bean condition nhắm sai type.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="filtered-class-loader">Kiểm thử khi Classpath thiếu Dependency bằng FilteredClassLoader</a>

<details>
<summary>Xem chi tiết</summary>

FilteredClassLoader giúp test mô phỏng classpath thiếu một class hoặc package dù bản build kiểm thử vẫn chứa dependency đó.

~~~java
contextRunner
    .withClassLoader(new FilteredClassLoader(AcmeClient.class))
    .run(context ->
        assertThat(context).doesNotHaveBean(AcmeClient.class));
~~~

Đây là bằng chứng mạnh cho thiết kế dependency tùy chọn. Configuration đúng phải đơn giản ngừng áp dụng khi class bắt buộc vắng mặt, không phát sinh NoClassDefFoundError hoặc lỗi liên kết khác.

Test đồng thời xác minh class condition và cấu trúc cô lập method signature dùng type tùy chọn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-context-runners">Context Runner cho Servlet và Reactive</a>

<details>
<summary>Xem chi tiết</summary>

ApplicationContextRunner mặc định tạo context không phải web. Khi configuration phụ thuộc riêng vào môi trường Servlet hoặc Reactive, dùng WebApplicationContextRunner hoặc ReactiveWebApplicationContextRunner tương ứng.

~~~text
core auto-configuration
→ ApplicationContextRunner

servlet-only auto-configuration
→ WebApplicationContextRunner

reactive-only auto-configuration
→ ReactiveWebApplicationContextRunner
~~~

Chọn đúng runner là một phần của đầu vào kiểm thử. Không thể chứng minh web condition bằng một loại context sai.

Milestone này chỉ sở hữu việc kiểm thử đặc thù cho quyết định auto-configuration. Kiểm thử web và chiến lược test slice rộng hơn thuộc module chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="condition-report-in-tests">Bằng chứng từ Condition Evaluation trong Test</a>

<details>
<summary>Xem chi tiết</summary>

Đôi khi phần kiểm tra hữu ích không chỉ là bean có tồn tại mà còn là vì sao condition dẫn tới kết quả đó. Test có thể gắn listener ghi log Condition Evaluation Report hoặc quan sát report khi chạy context tập trung.

~~~text
bean mong đợi bị thiếu
        ↓
log Condition Evaluation Report
        ↓
xác định condition không khớp
        ↓
sửa quy tắc áp dụng hoặc đầu vào kiểm thử thật sự sai
~~~

Không biến mọi test thành bản chụp toàn bộ report. Sự hiện diện/vắng mặt của bean và hành vi vẫn là quy ước chính; report là bằng chứng chẩn đoán hỗ trợ.

Khi hành vi auto-configuration đã được kiểm chứng, chương cuối đóng gói mô hình đó thành starter: lựa chọn dependency, quy tắc đặt tên, namespace cấu hình và luồng từ lúc thêm dependency đến khi nhận các giá trị mặc định có điều kiện.

</details>

- [Quay lại đầu trang](#back-to-top)
