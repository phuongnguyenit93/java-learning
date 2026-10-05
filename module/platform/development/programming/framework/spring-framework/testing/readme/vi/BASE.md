# Spring Framework Testing

Module này giải thích cách Spring Framework hỗ trợ kiểm thử khi test ở mức object thuần không còn đủ để chứng minh hành vi của ứng dụng.

Luồng học bắt đầu từ ranh giới giữa unit test thông thường và integration test do Spring quản lý, sau đó đi qua TestContext Framework, cấu hình ApplicationContext và dependency injection cho test fixture, profile/property dành riêng cho test, context caching và isolation, transactional/database testing, Servlet/reactive web testing, rồi kết thúc ở lifecycle và khả năng mở rộng của hạ tầng kiểm thử.

Kiến thức nên có trước:

- kiến thức kiểm thử Java cơ bản và một test framework tổng quát như JUnit;
- nền tảng Spring IoC / ApplicationContext từ `spring-framework/core-container`;
- kiến thức Spring MVC trước khi đi sâu vào MockMvc;
- kiến thức Spring WebFlux trước khi đi sâu vào WebTestClient;
- nền tảng Spring transaction trước khi phân tích sâu hành vi của transactional test.

Luồng học chính:

```text
testability và lựa chọn test scope
→ TestContext runtime model
→ cấu hình context và test fixture
→ profile và property cho test
→ context caching / isolation / parallelism
→ transactional và database testing
→ MockMvc
→ WebTestClient và client-side HTTP testing
→ lifecycle, AOT support và tổng hợp testing strategy
```

Module này sở hữu hạ tầng kiểm thử của Spring Framework như TestContext, MockMvc, WebTestClient, MockRestServiceServer, context caching, transactional test support và các lifecycle hook liên quan. Module không sở hữu kiến thức nền JUnit, Spring Boot test slice hay `@SpringBootTest`, cơ chế production bên trong Spring MVC/WebFlux hoặc semantics tổng quát của Spring transaction management.

Mục tiêu cuối cùng là biết chọn test scope nhỏ nhất đủ chứng minh hành vi cần kiểm tra và hiểu chính xác Spring infrastructure nào đang tham gia vào bằng chứng đó.
