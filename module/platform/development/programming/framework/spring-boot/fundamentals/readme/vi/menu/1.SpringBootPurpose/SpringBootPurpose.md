<a id="back-to-top"></a>

# Mục đích và mô hình tư duy về Spring Boot

## Menu
- [Spring Boot là gì và vì sao cần nó?](#spring-boot-purpose)
- [Spring Framework và Spring Boot chịu trách nhiệm khác nhau thế nào?](#boot-vs-framework)
- [Vì sao convention over configuration giúp giảm công việc thiết lập?](#convention-over-configuration)
- [Đầu vào cấu hình thay đổi ứng dụng Boot như thế nào?](#externalized-configuration-orientation)
- [Các khả năng chính của Spring Boot phối hợp với nhau ra sao?](#boot-capability-map)
- [Fundamentals chịu trách nhiệm đến đâu và học gì tiếp theo?](#fundamentals-learning-boundary)

## <a id="spring-boot-purpose">Spring Boot là gì và vì sao cần nó?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot là một lớp nằm quanh hệ sinh thái Spring, giúp ứng dụng đi từ trạng thái "đã có mã Spring và các dependency" đến trạng thái "có một ứng dụng chạy được" với ít công việc thiết lập lặp lại hơn. Boot làm việc đó bằng quy ước, giá trị mặc định hợp lý, điều phối dependency, hỗ trợ khởi động ứng dụng và các tích hợp cho những dạng runtime phổ biến.

Mô hình tư duy quan trọng là Boot không thay thế Spring Framework. Spring Framework vẫn cung cấp container, dependency injection, mô hình cấu hình, các web framework, abstraction cho data access và nhiều cơ chế nền tảng khác. Boot giúp những cơ chế đó dễ ghép lại và khởi động hơn trong các trường hợp phổ biến.

Không có Boot, một nhóm vẫn có thể xây ứng dụng Spring, nhưng thường phải đưa ra nhiều quyết định thiết lập một cách tường minh hơn: chọn các phiên bản dependency tương thích, khởi động ứng dụng thế nào, cần những bean hạ tầng nào, đóng gói/chạy kết quả ra sao và kết nối các thư viện phổ biến như thế nào. Boot giảm phần công việc lặp lại đó nhưng vẫn để các quyết định có thể quan sát và ghi đè.

Sau Fundamentals, bạn nên giải thích được luồng đơn giản sau:

```text
mã ứng dụng + dependencies
        ↓
SpringApplication khởi động
        ↓
Spring ApplicationContext
        ↓
quy ước và tích hợp của Boot định hình ứng dụng đang chạy
```

### Tài liệu tham khảo

- [Spring Boot 3.3 — Developing with Spring Boot](https://docs.spring.io/spring-boot/3.3/reference/using/index.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-vs-framework">Spring Framework và Spring Boot chịu trách nhiệm khác nhau thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework và Spring Boot giải quyết các lớp trách nhiệm khác nhau trong cùng bài toán ứng dụng. Spring Framework cung cấp các cơ chế nền tảng như `ApplicationContext`, đăng ký bean, dependency injection, transaction, Spring MVC và Spring WebFlux. Spring Boot cung cấp quy ước và tích hợp để việc lắp ghép, khởi động, cấu hình, quan sát trong lúc phát triển và đóng gói ứng dụng Spring trở nên thuận tiện hơn.

Có thể dùng một câu hỏi để phân biệt: khái niệm này có còn tồn tại trong một ứng dụng Spring không dùng Boot hay không? Bean, `ApplicationContext`, `@Configuration` hay Spring MVC thuộc Spring Framework. `SpringApplication`, `@SpringBootApplication`, starter, Boot auto-configuration, DevTools và cách đóng gói thực thi được của Boot là các khái niệm phía Boot.

Mối quan hệ là bổ sung cho nhau:

```text
Spring Framework = các cơ chế ứng dụng cốt lõi
Spring Boot      = quy ước + bootstrap + tích hợp quanh các cơ chế đó
```

Phân biệt này tránh một hiểu lầm phổ biến: Boot không chạy một container tách biệt với Spring. Ứng dụng Boot thường chạy một `ApplicationContext` của Spring Framework; Boot giúp tạo và cấu hình context đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="convention-over-configuration">Vì sao convention over configuration giúp giảm công việc thiết lập?</a>

<details>
<summary>Xem chi tiết</summary>

Convention over configuration nghĩa là bắt đầu từ những mặc định phù hợp với trường hợp phổ biến, rồi chỉ ghi đè các quyết định khác với nhu cầu của ứng dụng. Đây là cách giảm thiết lập lặp lại, không phải quy tắc lấy quyền kiểm soát khỏi lập trình viên.

Ví dụ, khi classpath có các thư viện web phù hợp, Boot có thể chuẩn bị web application context và tích hợp embedded server mà người học không phải tự nối từng đối tượng hạ tầng. Nếu ứng dụng cần lựa chọn khác, Boot thường cung cấp thuộc tính cấu hình hoặc điểm mở rộng bằng mã để thay đổi quyết định đó.

Có thể học theo mô hình:

```text
trường hợp phổ biến → dùng mặc định
trường hợp đặc biệt → ghi đè đúng quyết định cần thay đổi
kiến trúc khác thường → tùy biến hoặc thay thế quy ước
```

Bằng chứng cho thấy Boot dựa trên quy ước chứ không phải "phép thuật" là các quyết định của nó có thể quan sát qua đầu ra lúc khởi động, configuration metadata, bean và hành vi runtime. Các chương sau sẽ dùng bằng chứng lúc khởi động để làm những mặc định này cụ thể hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="externalized-configuration-orientation">Đầu vào cấu hình thay đổi ứng dụng Boot như thế nào?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng Boot được thiết kế để nhận cấu hình từ bên ngoài mã Java. Database URL, feature setting, application name, port hay profile có thể thay đổi giữa các môi trường dù artifact đã biên dịch vẫn giống nhau.

Ở mức Fundamentals, cần giữ một ý chính: **đầu vào cấu hình là một trong các yếu tố định hình ứng dụng mà Boot tạo ra**. Giá trị có thể đến từ các file như `application.properties` hoặc `application.yml`, biến môi trường, command-line argument và những nguồn được hỗ trợ khác. Boot đưa các giá trị đó vào môi trường ứng dụng và các tích hợp của Boot có thể sử dụng chúng.

Mô hình Config Data chi tiết, thứ tự ưu tiên chính xác giữa property source, profiles, binding, validation và `@ConfigurationProperties` thuộc module `externalized-configuration`. Ở đây chỉ cần hiểu rằng cùng một mã ứng dụng có thể cho hành vi khác nhau vì đầu vào cấu hình khác nhau.

Mối quan hệ lớn hơn là:

```text
cùng code + cùng dependencies + đầu vào cấu hình khác
                         ↓
                 lựa chọn runtime có thể khác
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="boot-capability-map">Các khả năng chính của Spring Boot phối hợp với nhau ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

Spring Boot dễ hiểu hơn khi xem các khả năng chính là những phần phối hợp với nhau, thay vì một cơ chế tự động khổng lồ.

```text
starter / managed dependencies
        ↓ tạo classpath sẵn có

classpath -------------------------┐
đầu vào cấu hình ------------------┼─→ SpringApplication bootstrap
bean hiện có của ứng dụng ---------┘      + chuẩn bị/refresh ApplicationContext
                                             ↓
                                  auto-configuration được xử lý
                                  như một phần cấu hình trong lifecycle này
                                  và phản ứng với các đầu vào/context đó
                                             ↓
                                  ApplicationContext kết quả
                                             ↓
                                  tích hợp runtime / web
```

Điểm quan trọng là đây là các đầu vào phối hợp trong quá trình bootstrap và refresh context, không phải một pipeline nơi starter "chạy" cấu hình hoặc auto-configuration chạy trước `SpringApplication`. Starter chủ yếu định hình classpath; đầu vào cấu hình cung cấp giá trị và lựa chọn tường minh; bean và bean definition của ứng dụng có thể ảnh hưởng phần cấu hình tự động mà Boot nên đóng góp hoặc nên back off. Trong quá trình `SpringApplication` bootstrap và `ApplicationContext` refresh, Boot xử lý auto-configuration như cấu hình Spring bên cạnh cấu hình của chính ứng dụng, từ đó tạo ra context kết quả cùng các tích hợp runtime.

Packaging, Actuator, testing và hỗ trợ native image nằm ngoài chuỗi bootstrap này. Chúng hỗ trợ bàn giao, vận hành, kiểm thử hoặc dạng runtime khác sau khi mô hình ứng dụng cốt lõi đã rõ ràng.

Fundamentals chỉ giới thiệu mối quan hệ này. Condition evaluation, ordering, exclusions và quy tắc back-off chi tiết thuộc `auto-configuration`; các sự kiện bootstrap và vòng đời runtime chi tiết thuộc `application-runtime`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fundamentals-learning-boundary">Fundamentals chịu trách nhiệm đến đâu và học gì tiếp theo?</a>

<details>
<summary>Xem chi tiết</summary>

Fundamentals sở hữu các thuật ngữ nền tảng và mô hình end-to-end cần có trước khi học các module Spring Boot sâu hơn. Sau module này, bạn nên biết Boot bổ sung gì quanh Spring, cách `SpringApplication` khởi động context, vai trò của `@SpringBootApplication`, vì sao starter quan trọng, classpath ảnh hưởng khả năng sẵn có thế nào, có những dạng ứng dụng lớn nào, và DevTools cùng cách đóng gói thực thi nằm ở đâu trong bức tranh.

Khi câu hỏi đi vào cơ chế chi tiết, chuyển sang module chuyên trách tương ứng:

| Câu hỏi chuyển sang... | Học tiếp ở... |
| --- | --- |
| Config Data, thứ tự ưu tiên property, profiles, binding | `externalized-configuration` |
| Conditions, back-off, exclusions, custom auto-configuration | `auto-configuration` |
| Lifecycle events, runners, availability, logging, runtime services | `application-runtime` |
| Embedded-server configuration, TLS, proxy handling, graceful shutdown | `web-runtime` |
| Gradle/Maven plugin, `bootJar`, layers, images, Buildpacks | `build-tooling-packaging` |
| Production endpoints và operational diagnostics | `actuator` |
| Boot test bootstrap và slices | `testing` |
| AOT và native image execution | `native-image` |

Ranh giới này giúp Fundamentals giải thích cách các mảnh ghép kết nối với nhau mà không lặp lại nội dung học chi tiết của các module chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)
