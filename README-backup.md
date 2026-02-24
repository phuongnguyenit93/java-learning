1. IOC và DI, Bean, Scope, Factory trong Spring
2. Spring AOP, Spring Core
3. Cấu hình tự động của ứng dụng Spring Boot
4. Các anotation căn bản
- @SpringBootApplication
- @Component
- @Controller, @RestController, @ResponseBody
- @Service
- @Repository- @ComponentScan- @Configuration
- @Bean, @Conditional, @Lazy, @Primary, @Qualifier, @DependsOn
- @Autowired
- @Value
- @Transactional, @Query, @Modifying
- @RequestParam, @RequestBody, @PathVariable, @RequestHeader
- @RequestMapping, @PostMapping, @GetMapping, @PutMapping, @DeleteMapping
- Các annotation dùng để kiểm tra dữ liệu @Valid, @NotNull, @Nullable, @Length,...
5. Cấu hình biến môi trường, các thuộc tính trong ứng dụng Spring
- application.properties
- application.yml
6. Cấu hình profiles cho ứng dụng Spring Boot
- Dev, Test, Prod
7. Mô hình lớp trong Spring Boot
- Lớp Controller
- Lớp Service (Business Logic)
- Lớp Repository (Persistence)
8. Xây dựng Interface và mô hình đa kế thừa Service
9. Giao tiếp với cơ sở dữ liệu SQL
- Hibernate
- JPA
- Hiểu về mapping entity với table trong cơ sở dữ liệu
10. Giao tiếp với cơ sở dữ liệu NoSQL
- Sử dụng Template
- Custom Document
11. Các xử lý nâng cao trong giao tiếp cơ sở dữ liệu
- Criteria và Query Builder
- Phân trang
- Sort nhiều điều kiện
- Specification
- Native query và sử dụng template
12. Xử lý lỗi trong ứng dụng và cách trả lỗi về Client
- Exception Handler
- Trả về lỗi theo HttpStatus
- Trả về lỗi theo Custom Status
13. Cấu hình nhiều DataSource
14. Xử lý sự kiện và bất đồng bộ
- EventListener
- Async
15. Chạy lịch schedule và khái niệm executor pool trong Spring
16. Microservices trong Spring
17. Tìm hiểu Maven trong single Service và Microservices
18. Giao tiếp giữa các service qua HTTP
19. Giao tiếp giữa các service qua Eureka
- Server Eureka
- Server Discovery
- Spring Actuator cho việc audit hệ thống và các service con bên trong
20. Tìm hiểu hệ thống Message Queue
21. Giao tiếp giữa các service thông qua Message Queue
22. Tìm hiểu về SSE, WebSocket, polling
23. Tìm hiểu Kafka
- Sau khi có khái niệm về SSE và Kafka chúng ta có thể dựng hệ thống push notification không cần sử dụng đến các bên thứ 3 hỗ trợ như Firebase, Parse Server
24. Tìm hiểu SocketIO
25. Tìm hiểu về WebFlux
26. Tìm hiểu về RxJava
27. Tìm hiểu nâng cao về cơ chế bất đồng bộ và API bất đồng bộ
28. Tìm hiểu Filter, Filterchain, CORS, Request, Response, Session, CSRF
29. Học về Spring Security
30. Hệ thống xác thực căn bản
31. Hệ thống xác thực nâng cao Spring OAuth2 (Token Store + JWT + Third Party)
32. Khái niệm API Gateway
33. Chi tiết API Gateway hỗ trợ sẵn của Spring Boot
34. Tìm hiểu Open Source Kong API Gateway
35. Cấu hình ghi log trong Spring Boot
- Ghi log vào file
- Ghi log vào database
- Tự cấu hình log cho hệ thống, ghi vào service log
36. Thao tác với báo cáo JasperReport trong Spring Boot
37. Thao tác với File, Multipart trong Spring Boot
38. Các anotation nâng cao
- @PostConstruct, @PreDestroy
- @PropertySource
- @CrossOrigin
- @ExceptionHandler
- @InitBinder- @ControllerAdvice
- @EnableDiscoveryClient, @EnableEurekaServer, @EnableConfigServer
- @Cacheable
- @Async
- @BeforeTransaction, @AfterTransaction
- @MockBean, @JsonTest, @TestPropertySource
39. Tự custom anotation trong ứng dụng
- Kết hợp với AOP để xây dựng bộ khung quản lý luồng chạy ứng dụng Spring Boot như trước khi vào Controller kiểm tra RequestBody, sau khi return value xử lý log,...
40. Cache dữ liệu với Memory, Redis
41. Xây dựng Dockerfile cho ứng dụng Spring Boot
42. Xây dựng docker-compose cho ứng dụng Spring Boot
43. Deploy microservices trên Docker
44. CI-CD với Jenkins cho ứng dụng Spring Boot
45. Cấu hình Nginx cho việc gọi API tới Spring Boot
46. Cấu hình Nginx cho việc gọi tới Microservices
47. Cấu hình security cho Nginx
48. Tự động hóa ứng dụng
- Tạo schedule động với việc tạo 1 bean Autowired động và hệ thống quản lý job từ cơ sở dữ liệu
- Khởi động ứng dụng Spring Boot dynamic không cần phải stop và restart gói build war hoặc jar
- ... (đa phần sẽ là kinh nghiệm đúc kết từ quá trình làm việc)
49. Swagger cho ứng dụng
- Spring OpenAPI
- Springfox Swagger
50. Testing cho ứng dụng
51. Load balancer cho ứng dụng
52. Design Pattern cho ứng dụng
53. Code Clean cho ứng dụng

54. Trong Gradle, file settings.gradle chạy ở giai đoạn Initialization, còn các file build.gradle chạy ở giai đoạn Configuration . Theo mặc định, các biến khai báo bình thường trong settings.gradle sẽ không tự động "nhảy" vào Task được.

Tôi đã hoàn thiện phần phân tích về Logging Levels trong Gradle vào file README.md. Việc hiểu rõ các cấp độ log này sẽ giúp bạn quản lý console một cách chuyên nghiệp, tránh bị "ngập" trong thông tin không cần thiết.

Markdown
# 📊 Quản lý Log trong Gradle: Lifecycle vs Info

Việc phân biệt giữa `LIFECYCLE` và `INFO` là chìa khóa để bạn kiểm soát "độ ồn ào" của console mà không làm mất đi các thông tin quan trọng.

---

## 1. Cấp độ ưu tiên (Log Levels)

Gradle sử dụng hệ thống phân cấp log từ ít chi tiết đến rất chi tiết. Dưới đây là thứ tự ưu tiên:

1.  **ERROR**: Chỉ những lỗi nghiêm trọng làm dừng quá trình build.
2.  **QUIET**: Chỉ những tin nhắn cực kỳ quan trọng (sử dụng cờ `-q`).
3.  **LIFECYCLE** (Mặc định): Các thông báo về tiến độ build (Cột mốc).
4.  **INFO**: Các thông tin chi tiết về quá trình thực thi (Mặc định bị ẩn).
5.  **DEBUG**: Mọi thứ diễn ra "dưới nắp ca-pô" (Rất khủng khiếp về số lượng).

---

## 2. So sánh chi tiết: LIFECYCLE vs INFO

| Đặc điểm | LIFECYCLE | INFO |
| :--- | :--- | :--- |
| **Mục đích** | Thông báo các cột mốc quan trọng (VD: "Bắt đầu compile", "Build thành công"). | Thông báo chi tiết kỹ thuật (VD: "Copy file X vào thư mục Y", "Đang dùng cache Z"). |
| **Trạng thái mặc định** | **HIỆN**. Bạn sẽ luôn thấy các log này khi gõ `gradlew build`. | **ẨN**. Bạn sẽ không thấy gì trừ khi yêu cầu cụ thể. |
| **Cách để hiện** | Luôn hiện trừ khi dùng cờ `-q`. | Chỉ hiện khi thêm cờ `-i` hoặc `--info`. |
| **Khi nào nên dùng** | Dùng cho các dòng bạn luôn muốn thấy để biết dự án đang chạy đến đâu. | Dùng cho các dòng debug chỉ muốn thấy khi có lỗi hoặc cần kiểm tra sâu. |

---

## 🛠️ 3. Cách sử dụng trong Script

Để ghi log đúng cấp độ trong file `.gradle`, bạn có thể sử dụng đối tượng `logger`:

    ```groovy
    // Log này sẽ luôn hiện
    logger.lifecycle("--- Đang bắt đầu xử lý cấu hình Kafka ---")

    // Log này chỉ hiện khi chạy với lệnh ./gradlew build -i
    logger.info("Chi tiết: Đang đọc file config tại đường dẫn /config/kafka.yaml")
    ```