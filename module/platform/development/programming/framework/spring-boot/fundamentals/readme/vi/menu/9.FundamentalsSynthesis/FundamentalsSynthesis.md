<a id="back-to-top"></a>

# Tổng hợp Spring Boot Fundamentals

## Menu
- [Một ứng dụng Boot đơn giản kết nối end-to-end như thế nào?](#end-to-end-boot-flow)
- [Classpath, đầu vào cấu hình và quy ước của Boot liên hệ với nhau ra sao?](#classpath-configuration-conventions)
- [Những hiểu lầm nào về Spring Boot cần tránh?](#common-misconceptions)
- [Module nào chịu trách nhiệm cho từng mảng Spring Boot chuyên sâu?](#ownership-handoff-map)
- [Nên học gì sau Fundamentals?](#next-learning-path)

## <a id="end-to-end-boot-flow">Một ứng dụng Boot đơn giản kết nối end-to-end như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Một ứng dụng Boot đơn giản giờ có thể được giải thích từ source đến tiến trình đang chạy theo một chuỗi thống nhất:

```text
starter/lựa chọn dependency
        ↓ tạo classpath sẵn có
primary @SpringBootApplication class
        ↓ cung cấp cấu hình + điểm vào scan/auto-config
Java main(String[])
        ↓
SpringApplication.run(...)
        ↓ chuẩn bị environment + chọn/tạo context
ApplicationContext refresh
        ↓ tạo ứng dụng do Spring quản lý
dạng ứng dụng chạy
        ↓ công việc non-web hoặc embedded web runtime
orderly shutdown
        ↓ đóng context
```

DevTools có thể rút ngắn vòng phản hồi trong quá trình phát triển quanh ứng dụng đó. Packaging có thể sắp xếp ứng dụng cùng dependencies thành executable artifact. Cả hai không thay đổi điểm cốt lõi: Boot đang hỗ trợ bootstrap và tích hợp một Spring application.

Luồng này là nền tảng cho các module sau. Những module đó đi sâu từng mũi tên thay vì thay thế mô hình tư duy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="classpath-configuration-conventions">Classpath, đầu vào cấu hình và quy ước của Boot liên hệ với nhau ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

Ba loại đầu vào/tín hiệu dễ bị trộn lẫn khi mới học Boot:

- **Classpath:** những thư viện và khả năng ứng dụng nào đang sẵn có.
- **Đầu vào cấu hình:** những giá trị và lựa chọn tường minh nào được cấp cho lần chạy này.
- **Bean/bean definition của ứng dụng:** những thành phần và cấu hình do ứng dụng cung cấp đã tồn tại trong context đang được xây dựng.

Quy ước của Boot và auto-configuration không phải một đầu vào thứ tư. Chúng là cơ chế cấu hình và tích hợp mặc định phản ứng với các tín hiệu đó trong lúc `SpringApplication` bootstrap và `ApplicationContext` được chuẩn bị, refresh. Ví dụ, thêm web starter làm thay đổi classpath; đặt property làm thay đổi đầu vào cấu hình; tự định nghĩa bean có thể khiến auto-configuration đóng góp khác đi hoặc back off.

Mối quan hệ có thể hình dung như sau:

```text
classpath + đầu vào cấu hình + bean/bean definition của ứng dụng
                              ↓
        giai đoạn SpringApplication / ApplicationContext xây dựng context
        ├─ chuẩn bị context từ các đầu vào/tín hiệu đó
        ├─ áp dụng quy ước Boot / auto-configuration như cấu hình
        │  phản ứng với các đầu vào và context đang được xây dựng
        └─ refresh context
                              ↓
             ApplicationContext và các tích hợp kết quả
```

Fundamentals chỉ thiết lập mối quan hệ đó. Thứ tự ưu tiên property chính xác thuộc `externalized-configuration`; phần lập luận về condition, ordering, exclusions và hành vi back-off chi tiết thuộc `auto-configuration`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-misconceptions">Những hiểu lầm nào về Spring Boot cần tránh?</a>

<details>
<summary>Xem chi tiết</summary>

Có một số cách hiểu tắt nên chủ động loại bỏ:

1. **"Spring Boot thay thế Spring Framework."** Boot xây trên Spring Framework và thường khởi động Spring `ApplicationContext`.
2. **"Starter chính là auto-configuration."** Starter chủ yếu định hình dependencies; auto-configuration là cơ chế cấu hình có thể phản ứng với các dependencies đó.
3. **"Mọi Boot application đều là web application."** Boot có thể khởi động non-web, Servlet hoặc reactive application.
4. **"Convention nghĩa là không thể ghi đè hành vi."** Mặc định của Boot được thiết kế để có thể cấu hình hoặc thay thế tại ranh giới được hỗ trợ.
5. **"DevTools là tính năng runtime cho môi trường vận hành thực tế."** Đây là công cụ phản hồi trong quá trình phát triển, và hỗ trợ từ xa có cảnh báo bảo mật rõ ràng cho môi trường vận hành thực tế.
6. **"Executable packaging thay thế luồng bootstrap `main` về mặt logic của ứng dụng."** Nó chỉ thay đổi điểm vào ở cấp archive: executable Boot JAR dùng Boot launcher làm `Main-Class` trong manifest, còn class của ứng dụng được xác định qua `Start-Class`. Launcher thiết lập quyền truy cập tới các class và dependency lồng bên trong rồi gọi class ứng dụng đó. Luồng bootstrap về mặt logic của ứng dụng vẫn là Java `main` gọi `SpringApplication`, đúng với luồng `java -jar` đã giới thiệu ở chương packaging.

Các điểm sửa hiểu lầm này quan trọng vì những chủ đề Boot phía sau giả định bạn phân biệt được lựa chọn dependency, đầu vào cấu hình, hành vi container, hoạt động runtime và build tooling.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ownership-handoff-map">Module nào chịu trách nhiệm cho từng mảng Spring Boot chuyên sâu?</a>

<details>
<summary>Xem chi tiết</summary>

Dùng chính câu hỏi đang gặp để chọn module chuyên trách tiếp theo:

| Mảng chuyên sâu | Module chịu trách nhiệm |
| --- | --- |
| Config Data, property sources/precedence, profiles, binding | `externalized-configuration` |
| Conditions, back-off, exclusions, custom auto-configuration/starters | `auto-configuration` |
| `SpringApplication` lifecycle chi tiết, runners, availability, logging, runtime integrations | `application-runtime` |
| Web application detection, embedded server configuration, TLS, proxy, graceful shutdown | `web-runtime` |
| Gradle/Maven Boot plugins, executable archive internals, layers, OCI images, Buildpacks | `build-tooling-packaging` |
| Endpoint phục vụ vận hành thực tế, health, metrics integration, loggers | `actuator` |
| Boot test bootstrap, slices, Testcontainers service connections | `testing` |
| AOT pipeline và GraalVM native image integration | `native-image` |

Spring Framework vẫn là owner của các cơ chế nằm dưới Boot như bean/DI semantics nói chung, Spring MVC/WebFlux request processing và TestContext Framework.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="next-learning-path">Nên học gì sau Fundamentals?</a>

<details>
<summary>Xem chi tiết</summary>

Bước học tiếp theo được khuyến nghị là làm rõ hai mảng lớn giúp giải thích cách Boot đi đến các quyết định tự động. Học `externalized-configuration` trước để đầu vào cấu hình, configuration source, precedence, profile và binding trở nên cụ thể. Sau đó học `auto-configuration` như cơ chế phản ứng với tín hiệu từ classpath, cấu hình và context thông qua condition, back-off, diagnostics và custom auto-configuration.

Từ đó đi vào mô hình runtime thông thường rồi web runtime khi cần:

```text
fundamentals
   ↓
externalized-configuration
   ↓
auto-configuration
   ↓
application-runtime
   ↓
web-runtime
```

Build/packaging, Actuator và testing tiếp tục đào sâu bàn giao, vận hành và kiểm chứng. Native image nên học sau vì AOT và closed-world constraint dễ hiểu hơn khi mô hình JVM Boot thông thường đã ổn định.

Có thể tự kiểm tra mức sẵn sàng bằng vài câu hỏi: vì sao `main` gọi `SpringApplication`, `@SpringBootApplication` đóng góp gì, vì sao starter thay classpath, vì sao ứng dụng Boot có thể web hoặc không web, và module nào sở hữu câu hỏi chuyên sâu tiếp theo. Nếu giải thích được các điểm đó thì Fundamentals đã hoàn thành vai trò của nó.

</details>

- [Quay lại đầu trang](#back-to-top)
