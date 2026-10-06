<a id="back-to-top"></a>

# Starter, dependency được quản lý và classpath

## Menu
- [Spring Boot Starter là gì và vì sao nên dùng?](#starter-purpose)
- [Phiên bản dependency được quản lý giải quyết vấn đề gì?](#managed-dependencies)
- [Classpath có thể thay đổi hành vi của Boot như thế nào?](#classpath-driven-behavior)
- [Vì sao starter không phải là auto-configuration?](#starter-vs-auto-configuration)
- [Khi nào nên dùng starter, dependency riêng lẻ hoặc ghi đè phiên bản?](#dependency-choice-boundary)

## <a id="starter-purpose">Spring Boot Starter là gì và vì sao nên dùng?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot starter là một mô tả dependency gom một tập thư viện hữu ích cho một khả năng ứng dụng phổ biến. Thay vì tự tìm và khai báo từng thư viện Spring cùng thư viện bên thứ ba, bạn khai báo starter có tập dependency đại diện cho một điểm bắt đầu được Boot hỗ trợ.

Ví dụ, web starter có thể đưa Spring web stack cùng các thư viện hỗ trợ mà Boot mong đợi cho trường hợp sử dụng đó vào classpath. Bản thân starter không "chạy" web application; vai trò chính của nó là tạo classpath thuận tiện.

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

Điều này quan trọng vì classpath trở thành đầu vào cho các cơ chế Boot khác. Khi những class liên quan xuất hiện, auto-configuration có thể nhận biết khả năng đó và cấu hình hạ tầng phù hợp nếu các condition khác cũng thỏa mãn.

Vì vậy starter chủ yếu thể hiện **ý định về dependency và sự thuận tiện**. Nó giảm công việc chọn thư viện và cung cấp baseline dependency theo quy ước, đã được kiểm thử cùng Boot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="managed-dependencies">Phiên bản dependency được quản lý giải quyết vấn đề gì?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng thực tế thường phụ thuộc nhiều thư viện cần tương thích với nhau. Nếu chọn từng version độc lập, bạn có thể tạo tổ hợp không tương thích: thư viện mới kỳ vọng API mà dependency khác chưa có, hoặc hai cây transitive dependency cùng muốn các version khác nhau.

Mỗi Spring Boot release phát hành một tập version dependency đã được tuyển chọn và hỗ trợ. Khi cấu hình build dùng dependency management của Boot, nhiều dependency phổ biến có thể được khai báo mà không lặp lại version. Khi nâng Boot, cả tập version được phối hợp cũng di chuyển cùng nhau.

```text
Boot version
    ↓
tập dependency được tuyển chọn
    ↓
Spring libraries + các third-party libraries được hỗ trợ
```

Được quản lý không có nghĩa là bất biến. Bạn vẫn có thể ghi đè version khi có lý do rõ ràng, nhưng lúc đó một phần trách nhiệm tương thích quay lại phía nhóm phát triển ứng dụng. Cơ chế BOM và Gradle/Maven plugin chi tiết thuộc `build-tooling-packaging`.

### Tài liệu tham khảo

- [Spring Boot 3.3 — Build Systems](https://docs.spring.io/spring-boot/3.3/reference/using/build-systems.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-driven-behavior">Classpath có thể thay đổi hành vi của Boot như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Classpath không chỉ là danh sách code có thể import. Với Boot, nó còn là bằng chứng cho biết công nghệ nào đang sẵn có. Vì thế thêm hoặc bỏ một thư viện có thể thay đổi những gì Boot có khả năng cấu hình.

Giả sử ứng dụng ban đầu là tiến trình non-web. Khi thêm Servlet web stack thông thường, các class Spring MVC và embedded server implementation trở nên sẵn có. Theo mặc định, `SpringApplication` dùng bằng chứng từ classpath để xác định `WebApplicationType` và tạo application context tương ứng. Sau đó auto-configuration đóng góp và cấu hình hạ tầng phù hợp khi các condition được thỏa mãn, chẳng hạn embedded Servlet web server. Vì vậy, bỏ các khả năng đó khỏi classpath có thể làm thay đổi dạng ứng dụng được suy ra và hạ tầng mà Boot cấu hình.

```text
dependency declaration
        ↓
classpath content thay đổi
        ↓
Boot phát hiện capability khác
        ↓
conditional configuration có thể thay đổi
```

Việc một class có mặt trên classpath không phải toàn bộ quy tắc của auto-configuration. Property, bean hiện có và các condition khác cũng có thể tham gia. Đồng thời cần tách rõ trách nhiệm: `SpringApplication` xác định dạng ứng dụng và context; auto-configuration đóng góp cấu hình và hạ tầng phù hợp bên trong context đó. Ở đây chỉ cần hiểu lựa chọn dependency ảnh hưởng những khả năng runtime nào có thể xuất hiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="starter-vs-auto-configuration">Vì sao starter không phải là auto-configuration?</a>

<details>
<summary>Xem chi tiết</summary>

Starter và auto-configuration giải quyết hai bài toán khác nhau dù thường xuất hiện cùng nhau.

| Starter | Auto-configuration |
| --- | --- |
| tiện ích dependency ở thời điểm build | cơ chế cấu hình trong application context/runtime |
| đưa tập thư viện được tuyển chọn vào classpath | đóng góp cấu hình khi condition match |
| biểu diễn bằng dependency metadata | biểu diễn bằng Boot configuration class và conditions |

Ví dụ, khai báo `spring-boot-starter-web` tạo classpath web theo quy ước. Boot web auto-configuration sau đó có thể quan sát classpath đó và quyết định bean/tích hợp nào phù hợp. Starter không tự đăng ký các bean đó.

Phân biệt này hữu ích khi debug: "thư viện có ở classpath không?" là câu hỏi dependency/classpath; "vì sao Boot tạo hoặc bỏ qua cấu hình này?" là câu hỏi auto-configuration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dependency-choice-boundary">Khi nào nên dùng starter, dependency riêng lẻ hoặc ghi đè phiên bản?</a>

<details>
<summary>Xem chi tiết</summary>

Nên dùng Boot starter khi nó mô tả đúng khả năng ứng dụng cần và bạn muốn tập dependency theo quy ước. Dùng dependency riêng lẻ khi chỉ cần một thư viện hẹp và không muốn kéo cả starter set. Ghi đè managed version khi có yêu cầu cụ thể về tương thích, bảo mật hoặc tính năng và đã kiểm tra tổ hợp kết quả.

Có thể tóm tắt lựa chọn:

```text
trường hợp Boot phổ biến → starter
nhu cầu thư viện hẹp     → dependency riêng lẻ
ngoại lệ với managed baseline → ghi đè version tường minh + kiểm chứng
```

Fundamentals chỉ tập trung vào mô hình lựa chọn. Cách Gradle/Maven import Boot BOM, Boot build plugin thay đổi task thế nào và executable artifact được tạo ra sao thuộc `build-tooling-packaging`.

</details>

- [Quay lại đầu trang](#back-to-top)
